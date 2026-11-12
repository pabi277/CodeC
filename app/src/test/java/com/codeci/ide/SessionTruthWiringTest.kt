package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 104.3 — source pins. The decisions are pure and tested in AltScrollTest and
 * SessionFactsTest; these pins prove the terminal really uses them.
 */
class SessionTruthWiringTest {

    private fun source(path: String): String = RepoFiles.mainSource(path).readText()

    private val view = source("app/src/main/java/com/codeci/ide/ui/components/TerminalEmulatorView.kt")
    private val session = source("app/src/main/java/com/codeci/ide/ui/terminal/TerminalSession.kt")
    private val ptySession = source("app/src/main/java/com/codeci/ide/ui/terminal/PtySession.kt")
    private val vm = source("app/src/main/java/com/codeci/ide/ui/viewmodels/TerminalViewModel.kt")
    private val screen = source("app/src/main/java/com/codeci/ide/ui/screens/TerminalScreen.kt")

    @Test
    fun `the wheel and the fling become arrow keys in the alternate screen`() {
        assertTrue(view.contains("AltScroll.active(snap.altScreen, snap.mouseMode)"))
        assertTrue(view.contains("AltScroll.steps(dy, altScrollRemainder, cellH.toFloat())"))
        assertTrue(view.contains("onInput(cursorSequence(if (steps.rows > 0) 'B' else 'A'))"))
    }

    @Test
    fun `the fling loop checks alt-scroll before local scrollback`() {
        val loop = view.substringAfter("LaunchedEffect(fling) {").substringBefore("val rested = ScrollMath.rest")
        assertTrue(loop.contains("AltScroll.active(snap.altScreen, snap.mouseMode)"))
        assertTrue(loop.indexOf("AltScroll.active") < loop.indexOf("ScrollMath.step("))
    }

    @Test
    fun `dead sessions print the spec notice and restart on Enter`() {
        assertTrue(session.contains("SessionFacts.exitNotice("))
        assertTrue(vm.contains("SessionFacts.restartsOnInput(dead = s.isDead(), input = text)"))
        assertTrue(session.contains("fun isDead(): Boolean"))
    }

    @Test
    fun `a new tab opens in the previous tab's directory`() {
        assertTrue(vm.contains("SessionFacts.inheritedDirectory(activeSession()?.currentDirectory())"))
        assertTrue(vm.contains("startItem(item, cwd = inherited)"))
        assertTrue(vm.contains("base.copy(cwd = File(cwd))"))
        assertTrue(ptySession.contains("File(\"/proc/\$pid/cwd\").canonicalPath"))
    }

    @Test
    fun `the terminal menu holds keep-screen-on and kill-process`() {
        assertTrue(screen.contains("WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON"))
        assertTrue(screen.contains("viewModel.setKeepScreenOn(!keepScreenOn)"))
        assertTrue(screen.contains("viewModel.killActiveProcess()"))
        assertTrue(vm.contains("fun setKeepScreenOn("))
        assertTrue(ptySession.contains("PtyNative.kill(pid, PtyNative.SIGKILL)"))
    }
}
