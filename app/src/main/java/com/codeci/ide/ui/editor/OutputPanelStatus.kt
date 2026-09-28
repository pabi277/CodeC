package com.codeci.ide.ui.editor

import com.codeci.ide.ui.viewmodels.OutputPhase
import com.codeci.ide.ui.viewmodels.OutputRunState

/**
 * Phase 70.1 (2026-09-28) — **what the Output Panel's header says, and the
 * error count beside it**, from facts only.
 *
 * The owner's answers that this file implements, verbatim:
 * *“A — State word + icon, and the exit code on done/failed.”* Before this,
 * the header said `Output`/`Tests`, printed the free-text `summary` and
 * coloured it busy-blue or grey — so *done*, *failed*, *stopped* and a
 * finished install all rendered the same grey, and a failed build was stored
 * as `DONE` with its exit code buried in a line. The state a user reads must
 * not depend on which terminal path happened to write the last line, so it is
 * derived here, in one place, in a fixed order:
 *
 * 1. waiting for input wins (the program is stopped *at the user*, Q3);
 * 2. then busy — building / installing / running (Q2's “Installing” is its
 *    own word);
 * 3. then a live server, which is not “done” in any useful sense;
 * 4. then the terminal phases, where **a build that failed is a failure**
 *    even though the run pipeline stores it as `DONE`
 *    (`finishFailedBuild`), and a non-zero run exit is a failure too;
 * 5. everything else is idle.
 *
 * [counts] is the same law for the message area: the parseable diagnostics in
 * the visible lines, so a failed build can say *“3 errors · 1 warning”*
 * instead of leaving the owner to count red rows — the second half of his
 * report: *“terminal output is good enough but is hard to understand the
 * error line from terminal.”*
 */
sealed class OutputHead {
    /** Nothing has run, or the panel was cleared. Not drawn as a state word. */
    object Idle : OutputHead()

    /** Build, install or run in flight; [installing] is the install-gate word. */
    data class Working(val phase: OutputPhase, val installing: Boolean) : OutputHead()

    /** A program is running and waiting for stdin (the inline field is live). */
    object Waiting : OutputHead()

    /** Finished; [exitCode] null for a build-only success. */
    data class Done(val exitCode: Int?) : OutputHead()

    /** Non-zero somewhere; [build] says whether it was the build or the run. */
    data class Failed(val exitCode: Int?, val build: Boolean) : OutputHead()

    /** Stopped by the user (the Stop button or Clear). */
    object Stopped : OutputHead()

    /** A live server project is serving. */
    object Serving : OutputHead()
}

object OutputPanelStatus {

    /** Counts of parseable diagnostics; the banner's numbers. */
    data class Counts(val errors: Int, val warnings: Int) {
        val total: Int get() = errors + warnings
        val hasErrors: Boolean get() = errors > 0
    }

    fun head(state: OutputRunState): OutputHead = when {
        state.waitingForInput -> OutputHead.Waiting
        state.busy -> OutputHead.Working(state.phase, state.installing)
        state.serverRun && state.serverUrl != null -> OutputHead.Serving
        state.phase == OutputPhase.FAILED -> OutputHead.Failed(state.runExitCode, build = false)
        state.phase == OutputPhase.CANCELLED -> OutputHead.Stopped
        state.phase == OutputPhase.DONE -> {
            val buildExit = state.buildExitCode
            val runExit = state.runExitCode
            when {
                buildExit != null && buildExit != 0 -> OutputHead.Failed(buildExit, build = true)
                runExit != null && runExit != 0 -> OutputHead.Failed(runExit, build = false)
                runExit != null -> OutputHead.Done(runExit)
                else -> OutputHead.Done(null)
            }
        }
        else -> OutputHead.Idle
    }

    /** The visible diagnostics, counted. Unparseable lines are not errors. */
    fun counts(lines: List<String>): Counts {
        var errors = 0
        var warnings = 0
        for (line in lines) {
            val diagnostic = OutputLineParser.parseLine(line) ?: continue
            if (diagnostic.isError) errors++ else warnings++
        }
        return Counts(errors, warnings)
    }
}
