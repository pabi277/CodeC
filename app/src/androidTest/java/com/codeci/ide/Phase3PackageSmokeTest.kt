package com.codeci.ide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codeci.ide.ui.terminal.ShellEnvironment
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
