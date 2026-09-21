package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.3 — the terminal's chrome only. The first-run frame quotes the SAME
 * setup truth the setup bar and Packages render, and the emulator view (Phase
 * 36's measured rendering work) is untouched.
 */
class TerminalChromeTest {

    private val terminal = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/TerminalScreen.kt"
    ).readText()

    @Test
    fun `the first frame comes from the shared setup facts`() {
        assertTrue(terminal.contains("viewModel.setupFacts.collectAsState()"))
        assertTrue(terminal.contains("TerminalIntroPolicy.introFor("))
        assertTrue(terminal.contains("TerminalIntroFacts("))
    }

    @Test
    fun `the first frame has copy for each state it can be in`() {
        assertTrue(terminal.contains("R.string.terminal_intro_installing"))
        assertTrue(terminal.contains("R.string.terminal_intro_ready"))
        assertTrue(terminal.contains("R.string.terminal_intro_needs_setup"))
    }

    @Test
    fun `the line disappears as soon as the shell has said something`() {
        assertTrue(terminal.contains("hasOutput = snapshot.lines.any"))
        assertTrue(terminal.contains("TerminalIntro.NONE"))
    }

    @Test
    fun `the terminal chrome is on tokens`() {
        assertTrue(terminal.contains("CodecTokens"))
        assertFalse(
            "no raw corner radius may come back",
            Regex("""RoundedCornerShape\(\s*\d+\s*\.dp""").containsMatchIn(terminal),
        )
    }

    @Test
    fun `the emulator view is untouched by this phase`() {
        val emulator = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/components/TerminalEmulatorView.kt"
        ).readText()
        for (token in listOf("CodecTokens", "CodecMotion", "HapticMoment", "surfaceContainerHigh")) {
            assertFalse(
                "TerminalEmulatorView.kt must not gain $token (Phase 36 owns its rendering)",
                emulator.contains(token),
            )
        }
    }

    @Test
    fun `the sora editor host is the same as the merged phase-50 file`() {
        val host = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/editor/sora/SoraEditorHost.kt"
        ).readText()
        for (token in listOf("CodecTokens", "CodecMotion", "HapticMoment", "surfaceContainerHigh")) {
            assertFalse(
                "SoraEditorHost.kt must not gain $token (Phase 48 owns the caret box)",
                host.contains(token),
            )
        }
    }

    @Test
    fun `the terminal keeps its phase 44 honesty`() {
        assertTrue(terminal.contains("SetupGatePolicy.dontCloseText("))
        assertTrue(terminal.contains("TerminalStatusLabel.label("))
        assertTrue(terminal.contains("ShellEnvironment.prefixDir(") || terminal.contains("setupProgress"))
    }
}
