package com.codeci.ide

import com.codeci.ide.ui.terminal.InstallProgress
import com.codeci.ide.ui.terminal.SetupFacts
import com.codeci.ide.ui.terminal.SetupStage
import com.codeci.ide.ui.terminal.TerminalIntro
import com.codeci.ide.ui.terminal.TerminalIntroFacts
import com.codeci.ide.ui.terminal.TerminalIntroPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.3 — the terminal's first frame, decided from the SAME SetupFacts the
 * setup bar and Packages render, so the three surfaces cannot disagree about
 * "is it ready?".
 */
class TerminalIntroTest {

    private fun facts(
        progress: InstallProgress = InstallProgress(SetupStage.CHECKING),
        runnable: Boolean = false,
        pkg: Boolean = false,
        swapping: Boolean = false,
        hasOutput: Boolean = false,
    ) = TerminalIntroFacts(
        setup = SetupFacts(
            progress = progress,
            userlandRunnable = runnable,
            packageManager = pkg,
            swapping = swapping,
        ),
        hasOutput = hasOutput,
    )

    private val ready = InstallProgress(SetupStage.READY)

    @Test
    fun `a shell that has spoken silences the intro`() {
        assertEquals(
            TerminalIntro.NONE,
            TerminalIntroPolicy.introFor(facts(progress = ready, runnable = true, pkg = true, hasOutput = true)),
        )
    }

    @Test
    fun `a downloading setup says so`() {
        assertEquals(
            TerminalIntro.INSTALLING,
            TerminalIntroPolicy.introFor(facts(progress = InstallProgress(SetupStage.DOWNLOADING, 42))),
        )
    }

    @Test
    fun `extracting and verifying are installations too`() {
        assertEquals(
            TerminalIntro.INSTALLING,
            TerminalIntroPolicy.introFor(facts(progress = InstallProgress(SetupStage.VERIFYING))),
        )
        assertEquals(
            TerminalIntro.INSTALLING,
            TerminalIntroPolicy.introFor(facts(progress = InstallProgress(SetupStage.EXTRACTING))),
        )
    }

    @Test
    fun `usable tools with nothing printed are READY`() {
        assertEquals(
            TerminalIntro.READY,
            TerminalIntroPolicy.introFor(facts(progress = ready, runnable = true, pkg = true)),
        )
    }

    @Test
    fun `a prefix without pkg is not ready`() {
        assertEquals(
            TerminalIntro.NEEDS_SETUP,
            TerminalIntroPolicy.introFor(facts(progress = ready, runnable = true, pkg = false)),
        )
    }

    @Test
    fun `an interrupted swap is not ready`() {
        assertEquals(
            TerminalIntro.NEEDS_SETUP,
            TerminalIntroPolicy.introFor(
                facts(progress = ready, runnable = true, pkg = true, swapping = true)
            ),
        )
    }

    @Test
    fun `a failed setup keeps the honest sentence`() {
        assertEquals(
            TerminalIntro.NEEDS_SETUP,
            TerminalIntroPolicy.introFor(facts(progress = InstallProgress(SetupStage.FAILED))),
        )
    }

    @Test
    fun `output always wins, whatever the setup stage is`() {
        for (progress in listOf(
            InstallProgress(SetupStage.CHECKING),
            InstallProgress(SetupStage.DOWNLOADING, 10),
            InstallProgress(SetupStage.EXTRACTING),
            InstallProgress(SetupStage.READY),
            InstallProgress(SetupStage.FAILED),
            InstallProgress(SetupStage.UNSUPPORTED),
        )) {
            assertEquals(
                "output must win over $progress",
                TerminalIntro.NONE,
                TerminalIntroPolicy.introFor(facts(progress = progress, hasOutput = true)),
            )
        }
    }

    @Test
    fun `only NONE is silent`() {
        assertTrue(TerminalIntroPolicy.isSilent(TerminalIntro.NONE))
        assertFalse(TerminalIntroPolicy.isSilent(TerminalIntro.READY))
        assertFalse(TerminalIntroPolicy.isSilent(TerminalIntro.INSTALLING))
        assertFalse(TerminalIntroPolicy.isSilent(TerminalIntro.NEEDS_SETUP))
    }
}
