package com.codeci.ide

import com.codeci.ide.ui.services.PreviewConsolePolicy
import com.codeci.ide.ui.services.PreviewLevel
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 72.1 — the console's command line, exercised end to end on the host:
 * a typed line becomes a script, the script's answer becomes a console line.
 * Everything between the keyboard and the WebView is pinned here, including
 * the escaping — the one place a typed line could break out of its script.
 */
class PreviewConsolePolicyTest {

    @Test fun `a blank line runs nothing`() {
        assertNull(PreviewConsolePolicy.command(""))
        assertNull(PreviewConsolePolicy.command("   "))
        assertNull(PreviewConsolePolicy.command("\n\t "))
    }

    @Test fun `quote escapes what a script literal cannot carry`() {
        assertEquals("\"a\\\"b\"", PreviewConsolePolicy.quote("a\"b"))
        assertEquals("\"line\\nnext\"", PreviewConsolePolicy.quote("line\nnext"))
        assertEquals("\"back\\\\slash\"", PreviewConsolePolicy.quote("back\\slash"))
        assertEquals("\"\\u2028\"", PreviewConsolePolicy.quote("\u2028"))
        assertEquals("\"plain\"", PreviewConsolePolicy.quote("plain"))
    }

    @Test fun `the typed line is carried as data, never as script`() {
        val script = PreviewConsolePolicy.command("alert(\"x\"); document.title")!!
        // The command is inside one quoted literal: its own quotes are escaped,
        // so the wrapper's structure cannot be closed early.
        assertTrue(script.contains("eval(\"alert(\\\"x\\\"); document.title\")"))
        assertTrue(script.startsWith("(function(){try{var v=eval("))
        assertTrue(script.endsWith("})()"))
    }

    @Test fun `a command longer than the limit is cut, not dropped`() {
        val script = PreviewConsolePolicy.command("x".repeat(9000))!!
        assertTrue(script.contains("x".repeat(PreviewConsolePolicy.COMMAND_LIMIT)))
        assertFalse(script.contains("x".repeat(PreviewConsolePolicy.COMMAND_LIMIT + 1)))
    }

    @Test fun `unquote reads what evaluateJavascript answers`() {
        assertEquals("hello", PreviewConsolePolicy.unquote("\"hello\""))
        assertEquals("a\"b", PreviewConsolePolicy.unquote("\"a\\\"b\""))
        assertEquals("line\nnext", PreviewConsolePolicy.unquote("\"line\\nnext\""))
        assertEquals("ok\u0001value", PreviewConsolePolicy.unquote("\"ok\\u0001value\""))
        assertEquals("paired \ud83d\ude00", PreviewConsolePolicy.unquote("\"paired \\ud83d\\ude00\""))
        assertEquals("plain", PreviewConsolePolicy.unquote("  plain  "))
        assertEquals("", PreviewConsolePolicy.unquote(null))
    }

    @Test fun `results carry the level the page's own answer deserves`() {
        val ok = PreviewConsolePolicy.result("\"ok\\u0001document.title\"")
        assertEquals(PreviewLevel.LOG, ok.level)
        assertEquals("document.title", ok.message)

        val failed = PreviewConsolePolicy.result("\"err\\u0001Unexpected token ')'\"")
        assertEquals(PreviewLevel.ERROR, failed.level)
        assertEquals("Unexpected token ')'", failed.message)

        // A string result that really was empty is not an error.
        assertEquals("\"\"", PreviewConsolePolicy.result("\"ok\\u0001\"").message)

        // A refused evaluation (the view was gone) must not read as a success.
        assertEquals(PreviewLevel.ERROR, PreviewConsolePolicy.result(null).level)
        assertEquals(PreviewLevel.ERROR, PreviewConsolePolicy.result("\"\"").level)
    }

    @Test fun `the echo is the typed line, bounded and never the command itself`() {
        val echo = PreviewConsolePolicy.echo("  document.title  ")
        assertEquals(PreviewLevel.INFO, echo.level)
        assertEquals("› document.title", echo.message)
        assertEquals(
            PreviewConsolePolicy.COMMAND_LIMIT + 2,
            PreviewConsolePolicy.echo("x".repeat(9000)).message.length,
        )
    }
}
