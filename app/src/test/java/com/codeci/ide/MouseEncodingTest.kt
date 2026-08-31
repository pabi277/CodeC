package com.codeci.ide

import com.codeci.ide.ui.terminal.MouseEncoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 19.5 — xterm mouse encodings (SGR 1006 and legacy X10), written
 * from the xterm control-sequence spec: Cb = button | 4 shift | 8 meta |
 * 16 ctrl | 32 motion; wheel = 64/65; 1-based coordinates; release is the
 * `m` final (SGR) or button 3 (legacy).
 */
class MouseEncodingTest {

    @Test
    fun `sgr press and release`() {
        assertEquals("\u001b[<0;5;3M", MouseEncoding.press(0, 5, 3, sgr = true))
        assertEquals("\u001b[<0;5;3m", MouseEncoding.release(0, 5, 3, sgr = true))
    }

    @Test
    fun `sgr coordinates are one-based`() {
        assertEquals("\u001b[<0;1;1M", MouseEncoding.press(0, 1, 1, sgr = true))
    }

    @Test
    fun `sgr wheel uses buttons 64 and 65 press-only`() {
        assertEquals("\u001b[<64;10;2M", MouseEncoding.wheel(MouseEncoding.WHEEL_UP, 10, 2, sgr = true))
        assertEquals("\u001b[<65;10;2M", MouseEncoding.wheel(MouseEncoding.WHEEL_DOWN, 10, 2, sgr = true))
    }

    @Test
    fun `sgr motion sets the motion bit`() {
        assertEquals("\u001b[<32;4;4M", MouseEncoding.motion(0, 4, 4, sgr = true))
        assertEquals("\u001b[<35;4;4M", MouseEncoding.freeMotion(4, 4, sgr = true))
    }

    @Test
    fun `sgr modifiers shift meta ctrl`() {
        assertEquals("\u001b[<4;1;1M", MouseEncoding.press(0, 1, 1, sgr = true, shift = true))
        assertEquals("\u001b[<8;1;1M", MouseEncoding.press(0, 1, 1, sgr = true, alt = true))
        assertEquals("\u001b[<16;1;1M", MouseEncoding.press(0, 1, 1, sgr = true, ctrl = true))
    }

    @Test
    fun `legacy encoding offsets by 32`() {
        assertEquals(
            "\u001b[M\u0020\u0025\u0023",
            MouseEncoding.press(0, 5, 3, sgr = false)
        )
    }

    @Test
    fun `legacy release is button 3`() {
        assertEquals(
            "\u001b[M\u0023\u0021\u0021",
            MouseEncoding.release(0, 1, 1, sgr = false)
        )
    }

    @Test
    fun `legacy wheel and out-of-range coordinates`() {
        assertEquals(
            "\u001b[Mp\u0021\u0021",
            MouseEncoding.wheel(MouseEncoding.WHEEL_UP, 1, 1, sgr = false)
        )
        assertNull(MouseEncoding.press(0, 300, 1, sgr = false))
        assertEquals("\u001b[<0;300;1M", MouseEncoding.press(0, 300, 1, sgr = true))
    }
}
