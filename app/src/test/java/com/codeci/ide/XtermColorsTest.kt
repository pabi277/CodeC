package com.codeci.ide

import com.codeci.ide.ui.terminal.XtermColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XtermColorsTest {

    @Test
    fun `palette has 256 entries and known ansi colors`() {
        assertEquals(256, XtermColors.PALETTE.size)
        assertEquals(0x000000, XtermColors.PALETTE[0])
        assertEquals(0xCD0000, XtermColors.PALETTE[1])
        assertEquals(0xFFFFFF, XtermColors.PALETTE[15])
    }

    @Test
    fun `rgb encoding sets the flag`() {
        val color = XtermColors.rgb(10, 20, 30)
        assertTrue(XtermColors.isRgb(color))
        assertEquals(10, XtermColors.red(color))
        assertEquals(20, XtermColors.green(color))
        assertEquals(30, XtermColors.blue(color))
        assertEquals(0x000A141E, XtermColors.toRgb(color, 0))
    }

    @Test
    fun `bold promotes the 0 to 7 palette`() {
        val normal = XtermColors.toRgb(1, 0, bold = false)
        val bright = XtermColors.toRgb(1, 0, bold = true)
        assertEquals(XtermColors.PALETTE[1], normal)
        assertEquals(XtermColors.PALETTE[9], bright)
    }

    @Test
    fun `defaults resolve to the supplied fallback`() {
        assertEquals(0xABCDEF, XtermColors.toRgb(XtermColors.COLOR_DEFAULT_FG, 0xABCDEF))
        assertTrue(XtermColors.isDefault(XtermColors.COLOR_DEFAULT_BG))
        assertFalse(XtermColors.isDefault(3))
    }

    @Test
    fun `cube and gray ramps are populated`() {
        // index 16 is the first cube cell: 0,0,0
        assertEquals(0x000000, XtermColors.PALETTE[16])
        // last cube cell 231: 255,255,255
        assertEquals(0xFFFFFF, XtermColors.PALETTE[231])
        // first gray 232: 8,8,8
        assertEquals(0x080808, XtermColors.PALETTE[232])
    }
}
