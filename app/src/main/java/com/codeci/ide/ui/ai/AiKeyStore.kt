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

    // ---- Phase 87 (Level 10): the nine bounded agent controls ---------------
    // Non-secret tuning only. Eight properties for nine controls: *request
    // inspection* is deliberately not stored, because a persisted "on" implies a
    // persisted "off" and D4 says it is not user-removable. Nothing here can
    // raise a permission (S9) — every value is clamped by AiOptionsPolicy.

    fun options(): AiOptions {
        val p = settings()
        return AiOptionsPolicy.decode(
            readWindow = p.getProperty(PROP_READ_WINDOW),
            workingSet = p.getProperty(PROP_WORKING_SET),
            taskMemory = p.getProperty(PROP_TASK_MEMORY),
            answerDetail = p.getProperty(PROP_ANSWER_DETAIL),
            activity = p.getProperty(PROP_TOOL_ACTIVITY),
            backup = p.getProperty(PROP_BACKUP_MODE),
            budgetOffer = p.getProperty(PROP_BUDGET_OFFER),
            reviewer = p.getProperty(PROP_REVIEWER)
        )
    }

    private fun setOption(key: String, value: String): Boolean = synchronized(STORE_LOCK) {
        val props = settings()
        props.setProperty(key, value)
        return@synchronized writeSettings(props)
    }

    fun setReadWindow(lines: Int): Boolean =
        setOption(PROP_READ_WINDOW, AiOptionsPolicy.clampReadWindow(lines).toString())

    fun setWorkingSetDepth(depth: Int): Boolean =
        setOption(PROP_WORKING_SET, AiOptionsPolicy.clampWorkingSetDepth(depth).toString())

    /** Off means "forget", not "pause": the store clears the retained copy (Level 9). */
    fun setTaskMemory(on: Boolean): Boolean = setOption(PROP_TASK_MEMORY, on.toString())

    fun setAnswerDetail(detail: AiAnswerDetail): Boolean =
        setOption(PROP_ANSWER_DETAIL, detail.name)

    fun setActivity(display: AiActivityDisplay): Boolean =
        setOption(PROP_TOOL_ACTIVITY, display.name)

    fun setBackupMode(mode: AiBackupMode): Boolean = setOption(PROP_BACKUP_MODE, mode.name)

    fun setBudgetOffer(offer: AiBudgetOffer): Boolean = setOption(PROP_BUDGET_OFFER, offer.name)

    fun setReviewer(reviewer: AiReviewer): Boolean = setOption(PROP_REVIEWER, reviewer.name)

    fun acceptedTermsVersion(provider: AiProviderId = AiProviderId.GEMINI): Int? =
        settings().getProperty(termsProp(provider))?.toIntOrNull()

    /**
     * Level 10 (87.7): one IO pass over every provider, reporting which have a
     * usable key and which have their **own** current terms accepted.
     *
     * The backup-provider offer is built from this, so it can never name a
     * provider the user has not consented to for that provider. Consent is per
     * provider and is never replayed (**S8**).
     */
    fun providerReadiness(): Map<AiProviderId, AiProviderReadiness> = synchronized(STORE_LOCK) {
        AiProviderId.entries.associateWith { p ->
            val configured = if (keyFile(p).isFile) {
                if (acceptedTermsVersion(p) == AiProviders.termsVersion(p)) true else false
            } else {
                false
            }
            AiProviderReadiness(configured = configured, termsAccepted = configured)
        }
    }

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

        // Phase 87 (Level 10) — bounded agent controls. Non-secret, clamped by
        // AiOptionsPolicy, and never able to raise a permission (S9).
        private const val PROP_READ_WINDOW = "read_window_lines"
        private const val PROP_WORKING_SET = "working_set_depth"
        private const val PROP_TASK_MEMORY = "task_memory_on"
        private const val PROP_ANSWER_DETAIL = "answer_detail"
        private const val PROP_TOOL_ACTIVITY = "tool_activity"
        private const val PROP_BACKUP_MODE = "backup_provider_mode"
        private const val PROP_BUDGET_OFFER = "budget_extension_offer"
        private const val PROP_REVIEWER = "readonly_reviewer"

        // Phase 95 — the welcome + agreement screen. Bumped whenever the copy
        // materially changes, so the owner sees the new text rather than being
        // silently grandfathered into wording he has not read.
        private const val PROP_WELCOME_VERSION = "welcome_version"
        internal const val WELCOME_VERSION = 1
    }

    /** Phase 95 — the welcome/agreement is shown once per version, on first open. */
    fun welcomeAccepted(): Boolean = synchronized(STORE_LOCK) {
        settings().getProperty(PROP_WELCOME_VERSION)?.toIntOrNull() == WELCOME_VERSION
    }

    fun acceptWelcome(): Boolean = synchronized(STORE_LOCK) {
        val p = settings()
        p.setProperty(PROP_WELCOME_VERSION, WELCOME_VERSION.toString())
        writeSettings(p)
    }
}
