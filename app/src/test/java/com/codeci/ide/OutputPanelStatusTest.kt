package com.codeci.ide

import com.codeci.ide.ui.editor.OutputHead
import com.codeci.ide.ui.editor.OutputPanelHeight
import com.codeci.ide.ui.editor.OutputPanelStatus
import com.codeci.ide.ui.viewmodels.OutputLine
import com.codeci.ide.ui.viewmodels.OutputLineKind
import com.codeci.ide.ui.viewmodels.OutputPhase
import com.codeci.ide.ui.viewmodels.OutputRunState
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 70.1 — the Output Panel's state word and its error count, pinned.
 *
 * The owner's answers whose consequences live here: *“A — State word + icon,
 * and the exit code on done/failed”* (Q2) and *“A — Cap the panel lower while
 * the keyboard is up”* (Q5); his report that the panel's error lines are hard
 * to understand is what [OutputPanelStatus.counts] answers.
 */
class OutputPanelStatusTest {

    private fun lines(vararg text: String) =
        text.map { OutputLine(it, OutputLineKind.BUILD) }

    @Test
    fun `a fresh panel has no state word`() {
        assertEquals(OutputHead.Idle, OutputPanelStatus.head(OutputRunState()))
    }

    @Test
    fun `a waiting program outranks everything else`() {
        val state = OutputRunState(
            phase = OutputPhase.RUNNING,
            busy = true,
            waitingForInput = true,
        )
        assertEquals(OutputHead.Waiting, OutputPanelStatus.head(state))
    }

    @Test
    fun `busy says what it is busy with, install included`() {
        val building = OutputPanelStatus.head(OutputRunState(phase = OutputPhase.BUILDING, busy = true))
        assertEquals(OutputHead.Working(OutputPhase.BUILDING, installing = false), building)

        val installing = OutputPanelStatus.head(
            OutputRunState(phase = OutputPhase.BUILDING, busy = true, installing = true)
        )
        assertEquals(OutputHead.Working(OutputPhase.BUILDING, installing = true), installing)

        val running = OutputPanelStatus.head(OutputRunState(phase = OutputPhase.RUNNING, busy = true))
        assertEquals(OutputHead.Working(OutputPhase.RUNNING, installing = false), running)
    }

    @Test
    fun `a live server is serving, not done`() {
        val state = OutputRunState(
            phase = OutputPhase.RUNNING,
            serverRun = true,
            serverUrl = "http://127.0.0.1:8000",
        )
        assertEquals(OutputHead.Serving, OutputPanelStatus.head(state))
    }

    @Test
    fun `a failed build reads as failed even though the phase says done`() {
        // finishFailedBuild stores DONE with the exit code in buildExitCode;
        // the header must not colour that the same grey as a success.
        val state = OutputRunState(
            phase = OutputPhase.DONE,
            buildExitCode = 1,
            lines = lines("main.c:3:5: error: expected ';'"),
        )
        assertEquals(OutputHead.Failed(1, build = true), OutputPanelStatus.head(state))
    }

    @Test
    fun `a non-zero run exit is a failure and a zero one is done, with the code`() {
        assertEquals(
            OutputHead.Failed(2, build = false),
            OutputPanelStatus.head(OutputRunState(phase = OutputPhase.DONE, runExitCode = 2)),
        )
        assertEquals(
            OutputHead.Done(0),
            OutputPanelStatus.head(OutputRunState(phase = OutputPhase.DONE, runExitCode = 0)),
        )
        // A build-only run has no run code and is still a success.
        assertEquals(
            OutputHead.Done(null),
            OutputPanelStatus.head(OutputRunState(phase = OutputPhase.DONE, buildExitCode = 0)),
        )
        assertEquals(
            OutputHead.Failed(null, build = false),
            OutputPanelStatus.head(OutputRunState(phase = OutputPhase.FAILED)),
        )
        assertEquals(
            OutputHead.Stopped,
            OutputPanelStatus.head(OutputRunState(phase = OutputPhase.CANCELLED)),
        )
    }

    @Test
    fun `diagnostics are counted, and non-diagnostics are not errors`() {
        val counts = OutputPanelStatus.counts(
            listOf(
                "main.c:3:5: error: expected ';' before '}' token",
                "main.c:9: error: use of undeclared identifier 'x'",
                "util.c:2:1: warning: unused variable 'y' [-Wunused-variable]",
                "cc: fatal error: no such file: missing.h",
                "Build failed with exit code 1",
                "1 + 1",
                "",
            )
        )
        assertEquals(3, counts.errors)
        assertEquals(1, counts.warnings)
        assertEquals(4, counts.total)
        assertTrue(counts.hasErrors)
    }

    @Test
    fun `a clean run has nothing to announce`() {
        val counts = OutputPanelStatus.counts(lines("Build OK (220ms)", "Process finished with exit code 0 (12ms)"))
        assertEquals(0, counts.errors)
        assertEquals(0, counts.warnings)
        assertFalse(counts.hasErrors)
    }

    @Test
    fun `the panel is capped lower while the keyboard is up`() {
        // 800 dp screen: 440 normally, 304 with the keyboard.
        assertEquals(440f, OutputPanelHeight.resolve(400f, 800f, imeVisible = false), .01f)
        assertEquals(304f, OutputPanelHeight.resolve(400f, 800f, imeVisible = true), .01f)
        // A request below the floor comes back at the floor, not at zero.
        assertEquals(OutputPanelHeight.MIN, OutputPanelHeight.resolve(1f, 800f, imeVisible = false), .01f)
        // A tiny screen keeps its own maximum (the IME cap can be the smaller one).
        assertEquals(79.8f, OutputPanelHeight.resolve(400f, 210f, imeVisible = true), .01f)
        // Nonsense requests become the default.
        assertEquals(OutputPanelHeight.DEFAULT, OutputPanelHeight.resolve(Float.NaN, 800f, false), .01f)
    }
}
