package com.codeci.ide

import com.codeci.ide.ui.performance.FrameBudget
import com.codeci.ide.ui.performance.FrameVerdict
import com.codeci.ide.ui.performance.PaintVerdict
import com.codeci.ide.ui.performance.SpeedVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameBudgetTest {
    @Test fun `frame thresholds are explicit`() {
        assertEquals(FrameVerdict.SMOOTH, FrameBudget.verdictFor(15))
        assertEquals(FrameVerdict.SLOW, FrameBudget.verdictFor(16))
        assertEquals(FrameVerdict.SLOW, FrameBudget.verdictFor(31))
        assertEquals(FrameVerdict.JANK, FrameBudget.verdictFor(32))
    }

    @Test fun `jank percentage handles empty and mixed samples`() {
        assertEquals(0, FrameBudget.jankPercent(emptyList()))
        assertEquals(0, FrameBudget.jankPercent(listOf(1, 15, 31)))
        assertEquals(50, FrameBudget.jankPercent(listOf(16, 32, 40, 10)))
    }

    @Test fun `paint verdict has useful boundary`() {
        assertEquals(PaintVerdict.INSTANT, SpeedVerdict.firstPaintVerdict(0))
        assertEquals(PaintVerdict.INSTANT, SpeedVerdict.firstPaintVerdict(499))
        assertEquals(PaintVerdict.OK, SpeedVerdict.firstPaintVerdict(500))
        assertEquals(PaintVerdict.OK, SpeedVerdict.firstPaintVerdict(1499))
        assertEquals(PaintVerdict.SLOW, SpeedVerdict.firstPaintVerdict(1500))
        assertEquals(PaintVerdict.SLOW, SpeedVerdict.firstPaintVerdict(-1))
    }
}
