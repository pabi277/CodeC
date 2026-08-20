package com.codeci.ide

import com.codeci.ide.ui.terminal.TerminalHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalHandoffTest {

    @Test
    fun `safe identifiers stay unquoted`() {
        assertEquals("main.c", TerminalHandoff.shellEscape("main.c"))
        assertEquals("/data/foo/bar.c", TerminalHandoff.shellEscape("/data/foo/bar.c"))
    }

    @Test
    fun `spaces and quotes are wrapped`() {
        assertEquals("'hello world.c'", TerminalHandoff.shellEscape("hello world.c"))
        assertEquals("'it'\\''s.c'", TerminalHandoff.shellEscape("it's.c"))
        assertEquals("''", TerminalHandoff.shellEscape(""))
    }

    @Test
    fun `compile and run uses cc then a out`() {
        val cmd = TerminalHandoff.compileAndRunCommand("/data/data/com.codeci.ide/files/CodeC/projects/main.c")
        assertEquals(
            "cd /data/data/com.codeci.ide/files/CodeC/projects && " +
                "cc /data/data/com.codeci.ide/files/CodeC/projects/main.c -o a.out && " +
                "./a.out",
            cmd
        )
    }

    @Test
    fun `open in directory just cds`() {
        val cmd = TerminalHandoff.openInDirectoryCommand("/tmp/work dir")
        assertEquals("cd '/tmp/work dir'", cmd)
    }

    @Test
    fun `custom output name is escaped`() {
        val cmd = TerminalHandoff.compileAndRunCommand("/tmp/x.c", "my prog")
        assertTrue(cmd.contains("-o 'my prog'"))
        assertTrue(cmd.endsWith("./'my prog'"))
    }
}
