package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 102 — source pins for the install-path hardening: the pure policies
 * have their own tests; this file pins that the ANDROID side actually calls
 * them (the same pin-the-prose discipline the AI wiring tests apply). If a
 * future change deletes the session path, un-scopes the fallback intent,
 * drops the signature gate or the consent dialog, a pin here fails.
 */
class UpdateHardeningWiringTest {

    private val manager =
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/services/ApkUpdateManager.kt").readText()
    private val screen =
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt").readText()
    private val manifest = RepoFiles.mainSource("app/src/main/AndroidManifest.xml").readText()
    private val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `the session path is primary and the fallback is scoped`() {
        assertTrue("session install exists", manager.contains("private fun installViaSession"))
        assertTrue("session is tried first", manager.contains("if (installViaSession(file)) return"))
        assertTrue(manager.contains("installer.createSession(params)"))
        assertTrue(
            "fallback picks its target through the policy",
            manager.contains("InstallHandoffPolicy.pickInstallerPackage(candidates)")
        )
        assertTrue("the picked target scopes the intent", manager.contains("target?.let { setPackage(it) }"))
    }

    @Test
    fun `the signature gate sits between download and install`() {
        assertTrue(manager.contains("fun signatureVerdict(file: File): ApkSignaturePolicy.Verdict"))
        assertTrue(manager.contains("ApkSignaturePolicy.verdict("))
        assertTrue("the UI consults the gate", screen.contains("updater.signatureVerdict(result.file)"))
        assertTrue(
            "a wrong key never reaches installApk",
            screen.contains("ApkSignaturePolicy.Verdict.WrongKey")
        )
        assertTrue(
            "an unreadable signature never reaches installApk",
            screen.contains("ApkSignaturePolicy.Verdict.Unverifiable")
        )
    }

    @Test
    fun `the consent dialog with the Play Protect heads-up exists`() {
        assertTrue("consent state exists", screen.contains("var pendingUpdate by remember"))
        assertTrue("one tap no longer auto-installs", screen.contains("pendingUpdate = check"))
        assertTrue(
            "the heads-up names the expected Play Protect notice",
            screen.contains("R.string.update_play_protect_notice")
        )
        assertTrue(strings.contains("update_confirm_title"))
        assertTrue(strings.contains("update_play_protect_notice"))
        assertTrue(strings.contains("update_sig_bad"))
        assertTrue(strings.contains("update_sig_unreadable"))
    }

    @Test
    fun `the session status receiver is declared and not exported`() {
        assertTrue(manifest.contains("android:name=\".ui.services.InstallStatusReceiver\""))
        assertTrue(
            manifest.substringAfter("InstallStatusReceiver").contains("android:exported=\"false\"")
        )
        assertTrue(manager.contains("InstallStatusReceiver::class.java"))
    }

    @Test
    fun `the host pin lives in the policy and in preflight`() {
        val policy =
            RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/services/UpdatePolicy.kt").readText()
        assertTrue(policy.contains("fun isTrustedDownloadUrl"))
        assertTrue("preflight applies the pin", policy.contains("!isTrustedDownloadUrl(asset.downloadUrl)"))
    }
}
