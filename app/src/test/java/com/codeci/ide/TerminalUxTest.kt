package com.codeci.ide

import com.codeci.ide.ui.components.GridSelection
import com.codeci.ide.ui.components.extractUrls
import com.codeci.ide.ui.components.findUrlAt
import com.codeci.ide.ui.components.findWordBoundaries
import com.codeci.ide.ui.components.parseExtraKeysMacros
import com.codeci.ide.ui.components.selectedText
import com.codeci.ide.ui.terminal.TerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalUxTest {

    @Test
    fun `findUrlAt detects http and https URLs at character column`() {
        val line = "Visit https://github.com/pabi277/CodeC for docs or http://example.com/test."
        
        // Before URL
        assertNull(findUrlAt(line, 2))
        
        // Inside first URL
        val url1 = findUrlAt(line, 10)
        assertNotNull(url1)
        assertTrue(url1!!.startsWith("https://github.com/pabi277/CodeC"))
        
        // Between URLs
        assertNull(findUrlAt(line, 45))
        
        // Inside second URL
        val url2 = findUrlAt(line, 55)
        assertNotNull(url2)
        assertTrue(url2!!.startsWith("http://example.com/test"))
    }

    @Test
    fun `extractUrls extracts all valid URLs from terminal text`() {
        val text = "Check https://codec.dev and http://localhost:8080/api now"
        val urls = extractUrls(text)
        assertEquals(2, urls.size)
        assertEquals("https://codec.dev", urls[0])
        assertEquals("http://localhost:8080/api", urls[1])
    }

    @Test
    fun `parseExtraKeysMacros parses single and multi-row macro definitions`() {
        val raw = "pkg install nano, git:git status, make"
        val macros = parseExtraKeysMacros(raw)
        assertEquals(3, macros.size)
        
        assertEquals("pkg install nano", macros[0].first)
        assertEquals("pkg install nano\n", macros[0].second)
        
        assertEquals("git", macros[1].first)
        assertEquals("git status\n", macros[1].second)
        
        assertEquals("make", macros[2].first)
        assertEquals("make\n", macros[2].second)
    }

    @Test
    fun `selectionText extracts selected portion accurately`() {
        val emu = TerminalEmulator(cols = 40, rows = 10)
        emu.feed("Hello World!\r\nSecond Line.")
        val snapshot = emu.snapshot()
        
        // Select "World" in line 0 (cols 6..10)
        val sel = GridSelection(x1 = 6, y1 = 0, x2 = 10, y2 = 0)
        val text = snapshot.selectedText(sel)
        assertEquals("World", text)
    }

    @Test
    fun `findWordBoundaries selects full word at column`() {
        val line = "echo \"hello_world-123 foo\""
        val (start, end) = findWordBoundaries(line, 10)
        assertEquals("hello_world-123", line.substring(start, end + 1))
    }

    @Test
    fun `dynamic title updates on OSC 0 and OSC 2 escapes`() {
        val emu = TerminalEmulator()
        assertEquals("Terminal", emu.title)
        
        emu.feed("\u001b]0;project/test\u0007")
        assertEquals("project/test", emu.title)
        assertEquals("project/test", emu.snapshot().title)
        
        emu.feed("\u001b]2;build-job\u0007")
        assertEquals("build-job", emu.title)
        assertEquals("build-job", emu.snapshot().title)
    }
}
