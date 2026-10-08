package com.codeci.ide

import com.codeci.ide.ui.services.ApkSignaturePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 102 — the authenticity gate. The pin in code must stay the pin in
 * `docs/guides/UPLOAD_KEY_SETUP.md` (the two spellings are pinned together
 * here), the digest helper must match the known SHA-256 test vector, and the
 * verdict table must never install what it cannot attribute.
 */
class ApkSignaturePolicyTest {

    @Test
    fun `the pinned constant is the documented fingerprint, colon-free`() {
        val documented =
            "D5:8F:ED:4B:79:31:3E:CB:43:2F:CD:C1:6B:3C:EE:17:A6:E9:24:80:62:18:0D:18:F1:EB:75:D2:20:5A:46:38"
        assertEquals(
            documented.replace(":", "").lowercase(),
            ApkSignaturePolicy.PINNED_RELEASE_CERT_SHA256
        )
        assertEquals(64, ApkSignaturePolicy.PINNED_RELEASE_CERT_SHA256.length)
    }

    @Test
    fun `sha256Hex matches the known test vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ApkSignaturePolicy.sha256Hex("abc".toByteArray())
        )
    }

    @Test
    fun `an unreadable archive signature is never installed`() {
        assertEquals(
            ApkSignaturePolicy.Verdict.Unverifiable,
            ApkSignaturePolicy.verdict(apkCertDer = null, installedCertDer = null)
        )
        assertEquals(
            "even when the installed cert is readable",
            ApkSignaturePolicy.Verdict.Unverifiable,
            ApkSignaturePolicy.verdict(apkCertDer = null, installedCertDer = "own".toByteArray())
        )
    }

    @Test
    fun `the same key as the running install is a self-update`() {
        val key = "the upload key bytes".toByteArray()
        assertEquals(
            ApkSignaturePolicy.Verdict.SameKeyAsInstalled,
            ApkSignaturePolicy.verdict(apkCertDer = key, installedCertDer = key)
        )
    }

    @Test
    fun `a third key is refused with its digest named`() {
        val verdict = ApkSignaturePolicy.verdict(
            apkCertDer = "attacker key".toByteArray(),
            installedCertDer = "own key".toByteArray()
        )
        assertTrue(verdict is ApkSignaturePolicy.Verdict.WrongKey)
        assertEquals(
            ApkSignaturePolicy.sha256Hex("attacker key".toByteArray()),
            (verdict as ApkSignaturePolicy.Verdict.WrongKey).gotSha256
        )
    }

    @Test
    fun `the installed cert being unreadable only disables the self-key branch`() {
        val verdict = ApkSignaturePolicy.verdict(
            apkCertDer = "some archive cert".toByteArray(),
            installedCertDer = null
        )
        assertTrue(
            "not the pin and no self key: WrongKey, never a silent install",
            verdict is ApkSignaturePolicy.Verdict.WrongKey
        )
    }
}
