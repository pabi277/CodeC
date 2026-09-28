package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 70.1 — the Output Panel's rebuild, as wiring pins.
 *
 * The owner's six answers (2026-09-28) all landed in the panel or the screen
 * around it: the state word and exit code (Q2), the waiting strip and the run
 * keys (Q3), one labelled 48 dp Stop at the leading edge (Q4), the IME cap
 * (Q5), the collapse on an error-line jump (Q6) — and his console answer, that
 * the panel itself takes commands. The pure halves live in
 * `OutputPanelStatusTest`; this file is the thing only the source can witness.
 */
class OutputPanelWiringTest {

    private val panel = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/components/OutputPanelView.kt"
    ).readText()
    private val code = RepoFiles.codeOnly(panel)

    private val editor = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
    ).readText()

    private val editorCode = RepoFiles.codeOnly(editor)

    private val vm = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt"
    ).readText()

    @Test
    fun `the header says the state, the exit code and one labelled Stop`() {
        assertTrue(code.contains("OutputPanelStatus.head(state)"))
        assertTrue(code.contains("R.string.output_state_failed"))
        assertTrue(code.contains("R.string.output_state_waiting"))
        assertTrue(code.contains("R.string.output_exit_short"))
        // Q4 — a labelled Stop, at the leading edge of a scrollable 48 dp row.
        assertTrue(code.contains("R.string.output_stop"))
        assertTrue(code.contains("horizontalScroll(rememberScrollState())"))
    }

    @Test
    fun `no header action is under the 48 dp floor any more`() {
        assertFalse("a 36 dp action survived 70.1", code.contains(".size(36.dp)"))
        assertFalse("a 32 dp action survived 70.1", code.contains(".size(32.dp)"))
        assertTrue("the send glyph rides a 48 dp IconButton now", code.contains("IconButton(onClick = onSubmit)"))
    }

    @Test
    fun `the panel's own line is the console, and it routes through the run's state`() {
        assertTrue(code.contains("R.string.output_console_hint"))
        assertTrue(code.contains("R.string.output_console_stdin_hint"))
        assertTrue(code.contains("keyboardActions = KeyboardActions(onSend = { onSubmit() })"))
        assertTrue(vm.contains("fun submitInput(context: Context)"))
        assertTrue(vm.contains("if (_outputState.value.waitingForInput)"))
        assertTrue(vm.contains("runConsoleCommand(context, line)"))
        // The brief's rule — no second job on an occupied runner.
        assertTrue(vm.contains("R.string.output_console_busy"))
        // Same shell, same environment as a run, so ls/python/pkg behave.
        assertTrue(vm.contains("fun runConsoleCommand(context: Context, command: String)"))
        assertTrue(vm.contains("InteractiveRunSession.start("))
        assertTrue(vm.contains("CONSOLE_COMMAND_LIMIT"))
    }

    @Test
    fun `an error line is a location row and a message row, counted above them`() {
        assertTrue(code.contains("private fun diagnosticLocation(diagnostic: OutputDiagnostic)"))
        assertTrue(code.contains("diagnostic.message"))
        assertTrue(code.contains("pluralStringResource(R.plurals.output_error_count"))
        assertTrue(code.contains("R.string.output_waiting_tap"))
        assertTrue(code.contains("R.string.output_empty_hint"))
    }

    @Test
    fun `the screen asks the height law, caps it for the IME and collapses on a jump`() {
        assertTrue(editorCode.contains("OutputPanelHeight.resolve("))
        assertTrue(editorCode.contains("imeVisible = imeVisible"))
        assertTrue(editorCode.contains("OutputPanelHeight.defaultFor(panelScreen)"))
        assertTrue(editorCode.contains("viewModel.submitInput(context)"))
        // Q6 — the jump puts the panel away, twice (expanded panel and strip).
        assertTrue(editorCode.contains("if (outputExpanded) viewModel.toggleOutput()"))
    }
}
