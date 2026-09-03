package com.codeci.ide

import com.codeci.ide.ui.viewmodels.OutputLine
import com.codeci.ide.ui.viewmodels.OutputLineKind
import com.codeci.ide.ui.viewmodels.OutputPhase
import com.codeci.ide.ui.viewmodels.OutputRunState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 22.4 — the collapsed Output Panel strip must not reserve 64dp of a
 * phone screen before it has anything to show. `hasContent()` is the pure
 * predicate the editor uses to decide that, so the rule is host-tested here
 * rather than left to the composable.
 */
class OutputPanelVisibilityTest {

    @Test
    fun `a fresh editor session shows no strip`() {
        assertFalse(OutputRunState().hasContent())
    }

    @Test
    fun `a run that has started shows the strip even before any output`() {
        // RUN ▶ flips the phase to BUILDING before the first line arrives —
        // the strip must appear immediately so the user sees the run happen.
        assertTrue(OutputRunState(phase = OutputPhase.BUILDING).hasContent())
        assertTrue(OutputRunState(phase = OutputPhase.RUNNING).hasContent())
    }

    @Test
    fun `a finished run keeps its strip so the result stays readable`() {
        assertTrue(
            OutputRunState(
                phase = OutputPhase.DONE,
                lines = listOf(OutputLine("Hello", OutputLineKind.OUTPUT))
            ).hasContent()
        )
        assertTrue(OutputRunState(phase = OutputPhase.FAILED).hasContent())
        assertTrue(OutputRunState(phase = OutputPhase.CANCELLED).hasContent())
    }

    @Test
    fun `lines alone are enough even if the phase is idle`() {
        assertTrue(
            OutputRunState(
                phase = OutputPhase.IDLE,
                lines = listOf(OutputLine("\$ gcc main.c", OutputLineKind.COMMAND))
            ).hasContent()
        )
    }

    @Test
    fun `clearing back to a bare idle state hides the strip again`() {
        // This is exactly the value `clearOutput()` assigns.
        assertFalse(OutputRunState(phase = OutputPhase.IDLE).hasContent())
    }
}
