package com.codeci.ide

import com.codeci.ide.ui.services.InstallHandoffPolicy
import com.codeci.ide.ui.services.InstallHandoffPolicy.Resolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 102 — the fallback APK handoff may only be scoped to a package the
 * policy trusts; a third-party app that registered an APK intent filter must
 * NEVER be picked, however many candidates it adds (that registration is
 * exactly the attack the pre-102 implicit intent exposed).
 */
class InstallHandoffPolicyTest {

    private val aosp = Resolver("com.android.packageinstaller", isSystemApp = true)
    private val google = Resolver("com.google.android.packageinstaller", isSystemApp = true)
    private val oemSystem = Resolver("com.oem.packageinstaller", isSystemApp = true)
    private val malicious = Resolver("com.evil.apkgrabber", isSystemApp = false)

    @Test
    fun `a known system installer wins`() {
        assertEquals(
            "com.android.packageinstaller",
            InstallHandoffPolicy.pickInstallerPackage(listOf(malicious, aosp, oemSystem))
        )
        assertEquals(
            "com.google.android.packageinstaller",
            InstallHandoffPolicy.pickInstallerPackage(listOf(google, oemSystem))
        )
    }

    @Test
    fun `a known installer name beats an unknown system app`() {
        assertEquals(
            "com.android.packageinstaller",
            InstallHandoffPolicy.pickInstallerPackage(listOf(oemSystem, aosp))
        )
    }

    @Test
    fun `an unknown SYSTEM app is acceptable when no known installer exists`() {
        assertEquals(
            "com.oem.packageinstaller",
            InstallHandoffPolicy.pickInstallerPackage(listOf(malicious, oemSystem))
        )
    }

    @Test
    fun `a non-system unknown app is never picked`() {
        assertNull(InstallHandoffPolicy.pickInstallerPackage(listOf(malicious)))
        assertNull(
            "even listed first and alone among non-system apps",
            InstallHandoffPolicy.pickInstallerPackage(
                listOf(malicious, Resolver("com.other.thirdparty", isSystemApp = false))
            )
        )
    }

    @Test
    fun `no candidates means no scoped target`() {
        assertNull(InstallHandoffPolicy.pickInstallerPackage(emptyList()))
    }
}
