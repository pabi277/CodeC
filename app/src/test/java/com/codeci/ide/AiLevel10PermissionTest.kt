package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 87 / Level 10 — **S9** and **S6** as a permission test.
 *
 * `AiLevel10CeilingTest` proves no control can raise a *number*. This proves no
 * control can reach a *capability*: a project write, a command, or a path
 * outside the project tree. The two together are what makes Level 10 an options
 * phase rather than a permissions phase.
 */
class AiLevel10PermissionTest {
    private val aiDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private fun ai(name: String) = RepoFiles.codeOnly(File(aiDir, name).readText())

    /** The files Level 10 added. */
    private val newFiles = listOf("AiOptionsPolicy.kt", "AiLevel10Policies.kt")

    @Test
    fun `no Level 10 file can write to the project or run a command`() {
        // The same banned list the Level 4 and Level 9 pins use, applied to the
        // files this phase added. `codeOnly` first, so a comment explaining why
        // something is absent cannot trip the pin.
        for (name in newFiles) {
            val src = ai(name)
            assertFalse("$name must not write files", src.contains("writeText("))
            assertFalse("$name must not open output streams", src.contains("FileOutputStream"))
            assertFalse("$name must not run commands", src.contains("ProcessBuilder"))
            assertFalse("$name must not run commands", src.contains("Runtime.getRuntime"))
            assertFalse("$name must not send editor commands", src.contains("sendCommand("))
            assertFalse("$name must not apply edits", src.contains("AiEditApplier"))
        }
    }

    @Test
    fun `no Level 10 file reaches the network`() {
        // Send is the only network road (S8). Neither new file may hold a client,
        // a stream call or a URL.
        for (name in newFiles) {
            val src = ai(name)
            assertFalse("$name must not stream", src.contains("client.stream("))
            assertFalse("$name must not open sockets", src.contains("HttpURLConnection"))
            assertFalse("$name must not name an endpoint", src.contains("https://"))
        }
        // And the phase added no fourth stream site anywhere in the ViewModel.
        assertEquals(3, Regex("client\\.stream\\(").findAll(ai("AiViewModel.kt")).count())
    }

    @Test
    fun `the options never carry a path, so no control can widen the project boundary`() {
        // AiOptions holds two ints, a few enums and a boolean. If a path ever
        // entered it, a control could aim a read outside the project tree (D5).
        val src = ai("AiOptionsPolicy.kt")
        assertTrue(src.contains("data class AiOptions("))
        assertFalse(src.contains("val path"))
        assertFalse(src.contains("File("))
        assertFalse(src.contains("rootDir"))
        // The window it does control is bounded on both sides by pure policy.
        assertTrue(src.contains("fun clampReadWindow("))
        assertTrue(src.contains("fun clampWorkingSetDepth("))
    }

    @Test
    fun `the whole ai package still has no direct project write or command execution`() {
        // S6, re-checked over every file in ui/ai after Level 10 landed — not
        // only over the new ones, because this phase edited ten existing files.
        var scanned = 0
        for (file in aiDir.listFiles().orEmpty().sortedBy { it.name }) {
            if (!file.name.endsWith(".kt")) continue
            scanned++
            val src = RepoFiles.codeOnly(file.readText())
            assertFalse("${file.name} writes to the project", src.contains("FileOutputStream"))
            assertFalse("${file.name} runs a command", src.contains("ProcessBuilder"))
            assertFalse("${file.name} runs a command", src.contains("Runtime.getRuntime"))
        }
        assertTrue("expected the whole ui/ai package to be scanned", scanned >= 30)
    }
}
