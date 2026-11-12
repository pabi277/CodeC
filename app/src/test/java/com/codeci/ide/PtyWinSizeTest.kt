package com.codeci.ide

import com.codeci.ide.ui.terminal.PtyWinSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 104.2 — pixel extent of the PTY winsize (pure). */
class PtyWinSizeTest {

    @Test
    fun `extent is cells times cell size`() {
        assertEquals(80 * 9, PtyWinSize.pixelExtent(80, 9))
        assertEquals(24 * 18, PtyWinSize.pixelExtent(24, 18))
    }

    @Test
    fun `non-positive inputs report unknown as zero`() {
        assertEquals(0, PtyWinSize.pixelExtent(0, 9))
        assertEquals(0, PtyWinSize.pixelExtent(80, 0))
        assertEquals(0, PtyWinSize.pixelExtent(-3, 9))
        assertEquals(0, PtyWinSize.pixelExtent(80, -9))
    }

    @Test
    fun `extent clamps to the 16-bit winsize field`() {
        assertEquals(65_535, PtyWinSize.pixelExtent(4_000, 100))
        assertEquals(65_535, PtyWinSize.pixelExtent(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(65_535, PtyWinSize.pixelExtent(65_535, 1))
    }
}
