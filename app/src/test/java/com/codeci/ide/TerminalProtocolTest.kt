package com.codeci.ide

import com.codeci.ide.ui.terminal.Base64Codec
import com.codeci.ide.ui.terminal.MouseModes
import com.codeci.ide.ui.terminal.TerminalEmulator
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 19.5 — terminal identity & capability protocol parity:
 *  * Primary/Secondary Device Attributes responses (programs probe these
 *    before enabling advanced modes; silence made some TUI apps degrade),
 *  * OSC 52 clipboard write (write-only; read queries refused),
 *  * xterm mouse-reporting mode tracking.
 */
class TerminalProtocolTest {

    @Test
    fun `primary DA answers VT102 class`() {
        val replies = mutableListOf<String>()
        val emu = TerminalEmulator(cols = 20, rows = 5, responder = { replies.add(it) })
        emu.feed("\u001b[c")
        emu.feed("\u001b[0c")
        assertEquals(listOf("\u001b[?6c", "\u001b[?6c"), replies)
    }

    @Test
    fun `secondary DA self-identifies CodeC`() {
        val replies = mutableListOf<String>()
        val emu = TerminalEmulator(cols = 20, rows = 5, responder = { replies.add(it) })
        emu.feed("\u001b[>c")
        emu.feed("\u001b[>0;1;0c")
        assertEquals(listOf("\u001b[>0;100;0c", "\u001b[>0;100;0c"), replies)
    }

    @Test
    fun `osc 52 writes the clipboard`() {
        val clips = mutableListOf<String>()
        val emu = TerminalEmulator(cols = 20, rows = 5, onClipboardWrite = { clips.add(it) })
        emu.feed("\u001b]52;c;aGVsbG8=\u0007")            // BEL terminator
        assertEquals(listOf("hello"), clips)
    }

    @Test
    fun `osc 52 with ST terminator writes the clipboard`() {
        val clips = mutableListOf<String>()
        val emu = TerminalEmulator(cols = 20, rows = 5, onClipboardWrite = { clips.add(it) })
        emu.feed("\u001b]52;c;" + Base64.getEncoder().encodeToString("hi\nthere".toByteArray()) + "\u001b\\")
        assertEquals(listOf("hi\nthere"), clips)
    }

    @Test
    fun `osc 52 carries utf-8`() {
        val clips = mutableListOf<String>()
        val emu = TerminalEmulator(cols = 20, rows = 5, onClipboardWrite = { clips.add(it) })
        emu.feed("\u001b]52;c;" + Base64.getEncoder().encodeToString("কি বাংলা".toByteArray()) + "\u0007")
        assertEquals(listOf("কি বাংলা"), clips)
    }

    @Test
    fun `osc 52 read query and invalid data are refused`() {
        val clips = mutableListOf<String>()
        val emu = TerminalEmulator(cols = 20, rows = 5, onClipboardWrite = { clips.add(it) })
        emu.feed("\u001b]52;c;?\u0007")                   // read query — refused
        emu.feed("\u001b]52;c;!!!\u0007")                 // not base64 — refused
        emu.feed("\u001b]52;p;aGVsbG8=\u0007")            // primary selection only — refused
        assertTrue(clips.isEmpty())
    }

    @Test
    fun `mouse modes track set reset and replace`() {
        val emu = TerminalEmulator(cols = 20, rows = 5)
        emu.feed("\u001b[?1002;1006h")
        assertEquals(MouseModes.BUTTON or MouseModes.SGR_EXT, emu.mouseMode)

        emu.feed("\u001b[?1006l")
        assertEquals(MouseModes.BUTTON, emu.mouseMode)

        emu.feed("\u001b[?1000h")                         // replaces 1002
        assertEquals(MouseModes.NORMAL, emu.mouseMode)

        emu.feed("\u001b[?1000l")
        assertEquals(0, emu.mouseMode)

        emu.feed("\u001b[?1003;1006h")
        assertEquals(MouseModes.ANY or MouseModes.SGR_EXT, emu.mouseMode)
        assertEquals(MouseModes.ANY or MouseModes.SGR_EXT, emu.buffer.snapshot().mouseMode)

        emu.feed("\u001bc")                               // RIS clears modes
        assertEquals(0, emu.mouseMode)
    }

    @Test
    fun `base64 codec decodes padding whitespace and rejects garbage`() {
        assertEquals("hello", Base64Codec.decode("aGVsbG8="))
        assertEquals("hello", Base64Codec.decode("aGVsbG8"))
        assertEquals("hi there", Base64Codec.decode("aGkgdGhl\ncmU="))
        assertNull(Base64Codec.decode("aGV sbG8*"))
    }
}
