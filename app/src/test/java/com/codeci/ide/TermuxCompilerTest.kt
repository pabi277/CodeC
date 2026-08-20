package com.codeci.ide

import com.codeci.ide.ui.services.TermuxCompiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxCompilerTest {

    @Test
    fun `compile script writes stdin source and invokes clang with settings`() {
        val script = TermuxCompiler.buildCompileScript("c11", warnings = true, optimization = 2)

        assertTrue(script.startsWith("set -e; "))
        assertTrue(script.contains("cat > \"\$HOME/.codec/source.c\""))
        assertTrue(
            script.contains(
                "clang \"\$HOME/.codec/source.c\" -o \"\$HOME/.codec/program\" -std=c11 -Wall -Wextra -O2"
            )
        )
    }

    @Test
    fun `compile script honours no-warnings and O0 and normalizes the standard`() {
        val script = TermuxCompiler.buildCompileScript("C17", warnings = false, optimization = 0)

        assertTrue(script.contains("-std=c17"))
        assertFalse(script.contains("-Wall"))
        assertTrue(script.contains("-O0"))
    }

    @Test
    fun `compile script embeds base64 source when provided`() {
        val script = TermuxCompiler.buildCompileScript(
            "c11", warnings = false, optimization = 0,
            sourceBase64 = "aW50IG1haW4oKSB7IHJldHVybiAwOyB9"
        )

        assertTrue(
            script.contains(
                "printf '%s' 'aW50IG1haW4oKSB7IHJldHVybiAwOyB9' | base64 -d > \"\$HOME/.codec/source.c\""
            )
        )
        assertFalse(script.contains("cat >"))
    }

    @Test
    fun `optimization level is clamped into 0..3`() {
        val high = TermuxCompiler.buildCompileScript("c11", warnings = false, optimization = 9)
        val low = TermuxCompiler.buildCompileScript("c11", warnings = false, optimization = -2)

        assertTrue(high.contains("-O3"))
        assertTrue(low.contains("-O0"))
    }

    @Test
    fun `run cleanup and kill scripts target the codec program path`() {
        assertEquals("exec \"\$HOME/.codec/program\"", TermuxCompiler.buildRunScript())
        assertTrue(TermuxCompiler.buildCleanupScript().contains("\$HOME/.codec/source.c"))
        assertTrue(TermuxCompiler.buildCleanupScript().contains("\$HOME/.codec/program"))
        assertTrue(TermuxCompiler.buildKillScript().contains("pkill -f"))
    }

    @Test
    fun `program path lives in Termux home`() {
        assertEquals(
            "/data/data/com.termux/files/home/.codec/program",
            TermuxCompiler.absoluteProgramPath()
        )
    }

    @Test
    fun `internalFailure is null when err is RESULT_OK`() {
        val ok = TermuxCompiler.TermuxResult(
            stdout = "out",
            stderr = "err",
            exitCode = 0,
            internalErr = -1,
            internalErrMsg = null,
            timedOut = false
        )
        assertNull(ok.internalFailure)

        val failed = TermuxCompiler.TermuxResult(
            stdout = "",
            stderr = "",
            exitCode = null,
            internalErr = 3,
            internalErrMsg = "working directory not found",
            timedOut = false
        )
        assertTrue(failed.internalFailure!!.contains("working directory not found"))
    }
}
