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
 * Phase 76 — the user's Gemini key, encrypted with a non-exportable Android
 * Keystore AES-256-GCM key (decision D3; no `security-crypto`, which is
 * deprecated upstream; API 23+, CodeC's minSdk is 24).
 *
 * Where: `noBackupFilesDir/ai/` — Android's own never-backed-up directory,
 * and outside `files/CodeC/projects` (the ONLY backup include,
 * `res/xml/backup_rules.xml`). A restore or reinstall asks for the key again,
 * by design.
 *
 * Laws:
 *  - any decrypt failure (Keystore reset, OEM bug, corrupt file) deletes the
 *    blob and reports "no key" — never a plaintext fallback, never a crash;
 *  - deleting the key also deletes the Keystore entry and the terms
 *    acceptance (O1: a new key means a new confirmation);
 *  - nothing here logs.
 */
class AiKeyStore(context: Context) {

    private val dir = File(context.applicationContext.noBackupFilesDir, "ai")
    private val keyFile = File(dir, "gemini_key.bin")
    private val settingsFile = File(dir, "ai_settings.properties")

    /** A key is usable only with a current terms acceptance. */
    fun isReady(): Boolean {
        if (!keyFile.isFile) return false
        if (!AiKeySetup.acceptanceValid(acceptedTermsVersion())) {
            deleteKey()
            return false
        }
        return true
    }

    fun model(): String = settings().getProperty(PROP_MODEL)?.takeIf { AiModel.isValid(it) } ?: AiModel.DEFAULT

    fun setModel(model: String): Boolean {
        val m = AiModel.normalize(model)
        if (!AiModel.isValid(m)) return false
        val p = settings()
        p.setProperty(PROP_MODEL, m)
        return writeSettings(p)
    }

    fun acceptedTermsVersion(): Int? = settings().getProperty(PROP_TERMS)?.toIntOrNull()

    /** Encrypts and stores [rawKey] with the terms acceptance. False on any failure (nothing half-written). */
    fun saveKey(rawKey: String, model: String): Boolean {
        val key = AiKeySetup.normalize(rawKey)
        if (!AiKeySetup.looksLikeKey(key)) return false
        return try {
            dir.mkdirs()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val blob = AiKeyBlob.encode(cipher.iv, cipher.doFinal(key.toByteArray(Charsets.UTF_8)))
            val tmp = File(dir, "gemini_key.tmp")
            tmp.writeBytes(blob)
            if (!tmp.renameTo(keyFile)) {
                tmp.delete()
                return false
            }
            val p = settings()
            p.setProperty(PROP_TERMS, AiKeySetup.TERMS_VERSION.toString())
            p.setProperty(PROP_ACCEPTED_AT, System.currentTimeMillis().toString())
            if (AiModel.isValid(AiModel.normalize(model))) p.setProperty(PROP_MODEL, AiModel.normalize(model))
            // A key without its acceptance must not exist, even for a moment longer.
            if (!writeSettings(p)) {
                keyFile.delete()
                return false
            }
            true
        } catch (_: Exception) {
            keyFile.delete()
            false
        }
    }

    /** The decrypted key, held by the caller only for one request. Null = no usable key. */
    fun loadKey(): String? {
        if (!isReady()) return null
        return try {
            val (iv, ct) = AiKeyBlob.decode(keyFile.readBytes()) ?: throw IllegalStateException()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: throw IllegalStateException(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            deleteKey()
            null
        }
    }

    /** Removes the key blob, the Keystore entry and the acceptance. The model choice stays. */
    fun deleteKey() {
        keyFile.delete()
        runCatching { keyStore().deleteEntry(ALIAS) }
        val p = settings()
        p.remove(PROP_TERMS)
        p.remove(PROP_ACCEPTED_AT)
        writeSettings(p)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun secretKey(): SecretKey = existingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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

    private fun writeSettings(p: Properties): Boolean = runCatching {
        dir.mkdirs()
        settingsFile.outputStream().use { p.store(it, null) }
    }.isSuccess

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "codec_ai_gemini_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PROP_MODEL = "model"
        private const val PROP_TERMS = "terms_version"
        private const val PROP_ACCEPTED_AT = "terms_accepted_at"
    }
}
