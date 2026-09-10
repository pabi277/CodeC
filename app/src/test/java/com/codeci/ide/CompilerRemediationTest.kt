package com.codeci.ide

import com.codeci.ide.ui.editor.CompilerRemediation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 38.2 — the error-path home of the Termux-fallback remedy:
 * the exec "Permission denied" signature yields the four steps, an
 * ordinary compiler diagnostic (or a non-exec permission problem)
 * yields nothing. The four steps are pinned verbatim so the doc copy
 * (`docs/TROUBLESHOOTING.md` §27) and this string cannot drift apart.
 */
class CompilerRemediationTest {

    private val shellExecDenied =
        "sh: /data/user/0/com.codeci.ide/files/CodeC/modules/clang-compiler/bin/clang: Permission denied"
    private val wrapperExecDenied =
        "compiler-wrapper.sh[11]: /data/user/0/com.codeci.ide/files/CodeC/modules/clang-compiler/bin/clang: Permission denied"

    @Test
    fun `shell exec permission denied yields the remedy`() {
        val text = CompilerRemediation.textFor(shellExecDenied)
        assertNotNull(text)
        assertTrue(text!!.contains("Termux"))
    }

    @Test
    fun `the wrapper script signature yields the remedy`() {
        assertNotNull(CompilerRemediation.textFor(wrapperExecDenied))
    }

    @Test
    fun `the signature anywhere in multi-line build output yields the remedy`() {
        val output = "cc -std=c11 main.c -o bin/main\n" +
            "warning: unused variable\n" +
            shellExecDenied + "\n"
        assertNotNull(CompilerRemediation.textFor(output))
    }

    @Test
    fun `an ordinary compile error yields nothing`() {
        assertNull(CompilerRemediation.textFor("main.c:6: error: unknown type name 'foo'"))
        assertNull(CompilerRemediation.textFor("main.c:3: error: ';' expected (got \"}\")"))
    }

    @Test
    fun `a non-exec permission problem yields nothing`() {
        // A compiler diagnostic about opening a FILE is not the exec
        // signature, even though it says "Permission denied".
        assertNull(CompilerRemediation.textFor("cc: error: cannot open output file bin/menu: Permission denied"))
        // Git's SSH denial is not ours to remedy.
        assertNull(CompilerRemediation.textFor("git@github.com: Permission denied (publickey)."))
    }

    @Test
    fun `blank and null output yield nothing`() {
        assertNull(CompilerRemediation.textFor(null))
        assertNull(CompilerRemediation.textFor(""))
        assertNull(CompilerRemediation.textFor("   \n  "))
    }

    @Test
    fun `the remedy carries all four steps and the lead line`() {
        val text = CompilerRemediation.textFor(shellExecDenied)!!
        assertTrue("lead must name the real cause", text.contains("Android is blocking execution"))
        for (step in listOf(
            "Install Termux 0.109+",
            "allow-external-apps=true",
            "termux-reload-settings",
            "Run commands in Termux environment",
            "pkg update && pkg install clang"
        )) {
            assertTrue("remedy is missing step: $step", text.contains(step))
        }
    }

    @Test
    fun `STEPS is the pinned four-step text`() {
        // The doc quotes this wording; keep them in sync deliberately.
        assertEquals(
            "1) Install Termux 0.109+ from F-Droid or GitHub (termux.dev). " +
                "2) In Termux run: echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && " +
                "termux-reload-settings. 3) Grant CodeC the \"Run commands in Termux environment\" " +
                "permission (Android Settings \u2192 Apps \u2192 CodeC IDE \u2192 Permissions \u2192 Additional " +
                "permissions). 4) In Termux run: pkg update && pkg install clang",
            CompilerRemediation.STEPS
        )
    }

    @Test
    fun `detector is line-based not whole-output`() {
        // A diagnostic mentioning permission denied in one line and a
        // binary in ANOTHER line is not the exec signature.
        assertFalse(
            CompilerRemediation.isExecPermissionDenied(
                "cc: error: something else\nrandom note about clang\nPermission denied on read"
            )
        )
        assertTrue(
            CompilerRemediation.isExecPermissionDenied("ok line\nsh: 1: cc: Permission denied")
        )
    }
}
