package com.codeci.ide.ui.services

import java.security.MessageDigest

/**
 * Phase 102 — authenticity, not just integrity, for the verified updater.
 *
 * The pre-102 gate compared the downloaded bytes against the `sha256:` lines
 * of the release notes ([UpdatePolicy.verifyDownload]). That proves the APK
 * matches what the release *claims* — but the notes ride the same channel as
 * the APK, so a compromised release could publish a matching pair. A signing
 * certificate answers the question the digest cannot: WHO signed these bytes.
 *
 * The pin is the public certificate fingerprint the owner already publishes
 * in `docs/guides/UPLOAD_KEY_SETUP.md` (safe to share by design; the private
 * key never enters the repository). The second accepted key is the running
 * install's own certificate — the classic self-update anchor — so a
 * debug-signed install can still verify a debug-signed build without ever
 * accepting a third key.
 *
 * Pure host-testable code: `java.security.MessageDigest` only, no Android.
 */
object ApkSignaturePolicy {

    /**
     * SHA-256 fingerprint (over the DER-encoded certificate, colon-free
     * lowercase) of the CodeC release upload key — mirrors
     * `docs/guides/UPLOAD_KEY_SETUP.md` verbatim; a test pins the two
     * spellings together so a transcription error fails CI.
     */
    const val PINNED_RELEASE_CERT_SHA256 =
        "d58fed4b79313ecb432fcdc16b3cee17a6e9248062180d18f1eb75d2205a4638"

    /** SHA-256 of a DER-encoded certificate, the way fingerprints are printed. */
    fun sha256Hex(der: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(der)
            .joinToString("") { "%02x".format(it) }

    sealed class Verdict {
        /** Signed by the pinned release upload key. Install. */
        data object PinnedKey : Verdict()

        /** Signed by the same key as the running install (self-update). Install. */
        data object SameKeyAsInstalled : Verdict()

        /** The platform could not read any certificate: never auto-install. */
        data object Unverifiable : Verdict()

        /** A readable certificate that is neither the pin nor our own key. */
        data class WrongKey(val gotSha256: String) : Verdict()
    }

    /**
     * [apkCertDer] / [installedCertDer] are the DER bytes of the first signing
     * certificate of the downloaded archive / the running app; null when the
     * platform could not read one. An unreadable archive signature is never
     * an install; an unreadable installed signature only disables the
     * self-key branch, the pin still works.
     */
    fun verdict(apkCertDer: ByteArray?, installedCertDer: ByteArray?): Verdict {
        val apk = apkCertDer ?: return Verdict.Unverifiable
        val got = sha256Hex(apk)
        return when {
            got == PINNED_RELEASE_CERT_SHA256 -> Verdict.PinnedKey
            installedCertDer != null && got == sha256Hex(installedCertDer) ->
                Verdict.SameKeyAsInstalled
            else -> Verdict.WrongKey(got)
        }
    }
}
