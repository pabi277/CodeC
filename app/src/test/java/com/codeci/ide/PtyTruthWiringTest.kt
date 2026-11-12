package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 104.2 — source pins. The pixel math is pure and tested in PtyWinSizeTest;
 * these pins prove the native PTY really turns flow control off, sets IUTF8, and
 * carries pixel extents from the view to the kernel winsize.
 */
class PtyTruthWiringTest {

    private fun source(path: String): String = RepoFiles.mainSource(path).readText()

    private val ptyC = source("app/src/main/cpp/pty.c")
    private val native = source("app/src/main/java/com/codeci/ide/ui/terminal/PtyNative.kt")
    private val session = source("app/src/main/java/com/codeci/ide/ui/terminal/PtySession.kt")
    private val termSession = source("app/src/main/java/com/codeci/ide/ui/terminal/TerminalSession.kt")
    private val vm = source("app/src/main/java/com/codeci/ide/ui/viewmodels/TerminalViewModel.kt")
    private val view = source("app/src/main/java/com/codeci/ide/ui/components/TerminalEmulatorView.kt")
    private val screen = source("app/src/main/java/com/codeci/ide/ui/screens/TerminalScreen.kt")

    @Test
    fun `slave clears IXON and IXOFF and sets IUTF8`() {
        assertTrue(ptyC.contains("~(INLCR | IGNCR | IXON | IXOFF)"))
        assertTrue(ptyC.contains("IUTF8"))
        assertTrue(!ptyC.contains("(ICRNL | IXON)"))
    }

    @Test
    fun `native winsize writes the pixel fields`() {
        assertTrue(ptyC.contains("ws.ws_xpixel"))
        assertTrue(ptyC.contains("ws.ws_ypixel"))
        assertTrue(native.contains("nativeSetWindowSize(fd: Int, rows: Int, cols: Int, widthPx: Int, heightPx: Int)"))
        assertTrue(native.contains("nativeSetWindowSize(fd, rows, cols, widthPx, heightPx)"))
    }

    @Test
    fun `pixel extent travels view to viewmodel to session to pty`() {
        assertTrue(view.contains("PtyWinSize.pixelExtent(ptyCols, settledCellW)"))
        assertTrue(view.contains("PtyWinSize.pixelExtent(ptyRows, settledCellH)"))
        assertTrue(screen.contains("viewModel.resize(cols, rows, widthPx, heightPx)"))
        assertTrue(vm.contains("item.session.resize(cols, rows, widthPx, heightPx)"))
        assertTrue(vm.contains("item.session.resize(terminalCols, terminalRows, terminalWidthPx, terminalHeightPx)"))
        assertTrue(termSession.contains("pty?.setWindowSize(rows, cols, widthPx, heightPx)"))
        assertTrue(session.contains("PtyNative.setWindowSize(masterFd, rows, cols, pid, widthPx, heightPx)"))
    }

    @Test
    fun `a pixel-only resize never signals the shell`() {
        // Phase 104.2 fix: the PTY (and so SIGWINCH) is touched only on a grid change.
        val body = termSession.substringAfter("fun resize(cols: Int, rows: Int, widthPx: Int = 0, heightPx: Int = 0)")
            .substringBefore("private var lastWidthPx")
        assertTrue(body.indexOf("if (!gridChanged) return") in 0 until body.indexOf("pty?.setWindowSize(rows, cols, widthPx, heightPx)"))
    }

    @Test
    fun `a new shell starts with the last known pixel extent, not zero`() {
        assertTrue(termSession.contains("session.setWindowSize(emulator.rows, emulator.cols, lastWidthPx, lastHeightPx)"))
    }
}
