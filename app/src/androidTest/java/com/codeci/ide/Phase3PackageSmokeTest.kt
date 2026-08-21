package com.codeci.ide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codeci.ide.ui.terminal.ShellEnvironment
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Clean-install contract test for the Phase 3 frontend. A device without the
 * Phase 3 bootstrap must still be able to ask for help and must not cause the
 * frontend to fall back to an ambient Termux repository.
 */
@RunWith(AndroidJUnit4::class)
class Phase3PackageSmokeTest {
    @Test
    fun pkgHelpIsOfflineAndCodeCOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val script = File(context.cacheDir, "codec-pkg-smoke.sh")
        script.writeText(ShellEnvironment.pkgScript())
        script.setExecutable(true, false)

        val process = ProcessBuilder("/system/bin/sh", script.absolutePath, "help")
            .redirectErrorStream(true)
            .apply {
                environment()["PREFIX"] = File(context.filesDir, "usr").absolutePath
                environment()["PATH"] = "/system/bin"
            }
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, process.waitFor())
        assertTrue(output.contains("pkg update"))
        assertTrue(output.contains("pkg install"))
        assertTrue(output.contains("CodeC apt/dpkg bootstrap"))
        assertFalse(output.contains("com.termux/files/usr"))
    }

    /**
     * The shell environment contract for Phase 3: LD_PRELOAD may only point
     * at an existing termux-exec library, and the profile stays POSIX sh.
     */
    @Test
    fun ldPreloadContract() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = File(context.filesDir, "usr")
        val lib = File(prefix, "lib")

        // No library -> no LD_PRELOAD.
        assertNull(ShellEnvironment.termuxExecPreload(prefix))

        // Primary variant wins.
        lib.mkdirs()
        val primary = File(lib, "libtermux-exec-ld-preload.so")
        primary.writeBytes(byteArrayOf(1))
        assertEquals(primary, ShellEnvironment.termuxExecPreload(prefix))
        primary.delete()

        // Compatibility name still works.
        val compat = File(lib, "libtermux-exec.so")
        compat.writeBytes(byteArrayOf(1))
        assertEquals(compat, ShellEnvironment.termuxExecPreload(prefix))
        compat.delete()

        // The profile is POSIX sh (sourced by /system/bin/sh before exec).
        val profile = File(context.cacheDir, "codec-profile-smoke.sh")
        profile.writeText(ShellEnvironment.profileScript(prefix, File(context.filesDir, "home"), File(context.filesDir, "CodeC/projects")))
        profile.setExecutable(true, false)
        val run = ProcessBuilder("/system/bin/sh", profile.absolutePath)
            .redirectErrorStream(true)
            .start()
        assertEquals(0, run.waitFor())

        // The pkg frontend must stay free of official Termux repository URLs.
        val pkg = ShellEnvironment.pkgScript()
        assertFalse(pkg.contains("packages.termux.dev"))
        assertFalse(pkg.contains("packages-cf.termux.dev"))
    }
}
