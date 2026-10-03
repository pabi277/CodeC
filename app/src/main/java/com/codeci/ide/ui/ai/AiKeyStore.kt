package com.codeci.ide.ui.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Per-user BYOK (D3): independent AES-256-GCM Keystore slots in noBackupFilesDir/ai/.
 * Phase 82B retains Gemini's original alias/file/properties (no re-entry migration).
 * NVIDIA requires its own current dev/test terms acceptance, never Google's flag.
 * No plaintext fallback/logging; decrypt failure removes only the affected slot.
 * Only credentials, model/terms and the existing layout preferences persist here.
 * The selected provider, chat, timeline and retry countdown are never stored.
 */
class AiKeyStore(context: Context) {
    private val dir = File(context.applicationContext.noBackupFilesDir, "ai")
    private val settingsFile = File(dir, "ai_settings.properties")

    private fun keyFile(provider: AiProviderId) = File(dir, "${provider.slot}_key.bin")
    private fun alias(provider: AiProviderId): String = when (provider) {
        AiProviderId.GEMINI -> ALIAS
        AiProviderId.NVIDIA -> NVIDIA_ALIAS
    }
    private fun modelProp(provider: AiProviderId) = if (provider == AiProviderId.GEMINI) PROP_MODEL else PROP_NVIDIA_MODEL
    private fun termsProp(provider: AiProviderId) = if (provider == AiProviderId.GEMINI) PROP_TERMS else PROP_NVIDIA_TERMS
    private fun acceptedAtProp(provider: AiProviderId) = if (provider == AiProviderId.GEMINI) PROP_ACCEPTED_AT else PROP_NVIDIA_ACCEPTED_AT

    /** A saved key is usable only with this provider's CURRENT acceptance. */
    fun isReady(provider: AiProviderId = AiProviderId.GEMINI): Boolean = synchronized(STORE_LOCK) {
        if (!keyFile(provider).isFile) return@synchronized false
        if (acceptedTermsVersion(provider) != AiProviders.termsVersion(provider)) {
            deleteKey(provider)
            return@synchronized false
        }
        return@synchronized true
    }

    fun model(provider: AiProviderId = AiProviderId.GEMINI): String =
        settings().getProperty(modelProp(provider))?.takeIf { AiProviders.isValidModel(provider, it) }
            ?: AiProviders.defaultModel(provider)

    fun setModel(model: String, provider: AiProviderId = AiProviderId.GEMINI): Boolean = synchronized(STORE_LOCK) {
        val m = model.trim()
        if (!AiProviders.isValidModel(provider, m)) return@synchronized false
        val p = settings()
        p.setProperty(modelProp(provider), m)
        return@synchronized writeSettings(p)
    }

    // Existing Phase 77 layout choices, not conversation state. Deleting either key keeps them.
    fun bubble(): AiBubblePosition = AiBubblePolicy.decode(settings().getProperty(PROP_BUBBLE_POS))
    fun setBubble(p: AiBubblePosition): Boolean = synchronized(STORE_LOCK) {
        val props = settings()
        props.setProperty(PROP_BUBBLE_POS, AiBubblePolicy.encode(p))
        return@synchronized writeSettings(props)
    }
    fun showBubble(): Boolean = settings().getProperty(PROP_BUBBLE_SHOW) != "false"
    fun setShowBubble(show: Boolean): Boolean = synchronized(STORE_LOCK) {
        val props = settings()
        props.setProperty(PROP_BUBBLE_SHOW, show.toString())
        return@synchronized writeSettings(props)
    }
    fun outputConflict(): AiOutputConflict = AiSheetPolicy.decodeConflict(settings().getProperty(PROP_SHEET_OUTPUT))
    fun setOutputConflict(c: AiOutputConflict): Boolean = synchronized(STORE_LOCK) {
        val props = settings()
        props.setProperty(PROP_SHEET_OUTPUT, AiSheetPolicy.encodeConflict(c))
        return@synchronized writeSettings(props)
    }

    fun acceptedTermsVersion(provider: AiProviderId = AiProviderId.GEMINI): Int? =
        settings().getProperty(termsProp(provider))?.toIntOrNull()

    /** Consent checked here AND in VM/UI. No other provider's consent may authorize a new key. */
    fun saveKey(
        rawKey: String,
        model: String,
        provider: AiProviderId = AiProviderId.GEMINI,
        confirmed: Boolean = false
    ): Boolean = synchronized(STORE_LOCK) {
        if (!AiProviders.canSaveKey(provider, rawKey, model, confirmed)) return@synchronized false
        val key = AiKeySetup.normalize(rawKey)
        val file = keyFile(provider)
        val tmp = File(dir, "${provider.slot}_key.tmp")
        return@synchronized try {
            dir.mkdirs()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(provider))
            val blob = AiKeyBlob.encode(cipher.iv, cipher.doFinal(key.toByteArray(Charsets.UTF_8)))
            tmp.writeBytes(blob)
            if (!tmp.renameTo(file)) {
                tmp.delete()
                return@synchronized false
            }
            val p = settings()
            p.setProperty(termsProp(provider), AiProviders.termsVersion(provider).toString())
            p.setProperty(acceptedAtProp(provider), System.currentTimeMillis().toString())
            p.setProperty(modelProp(provider), model.trim())
            if (!writeSettings(p)) {
                file.delete()
                return@synchronized false
            }
            true
        } catch (_: Exception) {
            tmp.delete()
            file.delete()
            false
        }
    }

    /** Decrypted only for the selected provider/request; never exposed as UI state. */
    fun loadKey(provider: AiProviderId = AiProviderId.GEMINI): String? = synchronized(STORE_LOCK) {
        if (!isReady(provider)) return@synchronized null
        return@synchronized try {
            val (iv, ct) = AiKeyBlob.decode(keyFile(provider).readBytes()) ?: throw IllegalStateException()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey(provider) ?: throw IllegalStateException(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            deleteKey(provider)
            null
        }
    }

    /** Deletes only this credential/acceptance; both key deletions keep the Level 3 journal cleanup. */
    fun deleteKey(provider: AiProviderId = AiProviderId.GEMINI): Unit = synchronized(STORE_LOCK) {
        keyFile(provider).delete()
        runCatching { keyStore().deleteEntry(alias(provider)) }
        runCatching {
            dir.parentFile?.let {
                com.codeci.ide.ui.projects.AiEditApplier.clearAllJournals(it)
                com.codeci.ide.ui.projects.AiTaskMemoryStore.clearAll(it)
            }
        }
        val p = settings()
        p.remove(termsProp(provider))
        p.remove(acceptedAtProp(provider))
        writeSettings(p)
        Unit
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private fun existingKey(provider: AiProviderId): SecretKey? =
        (keyStore().getEntry(alias(provider), null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun secretKey(provider: AiProviderId): SecretKey = existingKey(provider) ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(alias(provider), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }
        .generateKey()

    private fun settings(): Properties = Properties().apply {
        if (settingsFile.isFile) runCatching { settingsFile.inputStream().use { load(it) } }
    }
    /** Atomic non-secret metadata update; readers never observe a truncated consent file. */
    private fun writeSettings(p: Properties): Boolean {
        val tmp = File(dir, "ai_settings.tmp")
        return try {
            dir.mkdirs()
            tmp.outputStream().use { p.store(it, null) }
            tmp.renameTo(settingsFile)
        } catch (_: Exception) {
            false
        } finally {
            tmp.delete()
        }
    }

    companion object {
        /** Shared across store instances: provider/model/layout writers must not lose other slots. */
        private val STORE_LOCK = Any()
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "codec_ai_gemini_key_v1"
        private const val NVIDIA_ALIAS = "codec_ai_nvidia_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PROP_MODEL = "model"
        private const val PROP_TERMS = "terms_version"
        private const val PROP_ACCEPTED_AT = "terms_accepted_at"
        private const val PROP_NVIDIA_MODEL = "nvidia_model"
        private const val PROP_NVIDIA_TERMS = "nvidia_terms_version"
        private const val PROP_NVIDIA_ACCEPTED_AT = "nvidia_terms_accepted_at"
        private const val PROP_BUBBLE_POS = "bubble_pos"
        private const val PROP_BUBBLE_SHOW = "bubble_show"
        private const val PROP_SHEET_OUTPUT = "sheet_with_output"
    }
}
