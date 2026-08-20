package com.codeci.ide

import com.codeci.ide.ui.services.CompilerService
import com.codeci.ide.ui.services.ErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompilerServiceTest {

    @Test
    fun `parseDiagnostics extracts clang errors and warnings`() {
        val raw = """
            /data/user/0/com.codeci.ide/files/CodeC/temp/source_1.c:5:9: error: expected ';' after expression
            /data/user/0/com.codeci.ide/files/CodeC/temp/source_1.c:8:3: warning: unused variable 'x' [-Wunused-variable]
        """.trimIndent()

        val errors = CompilerService.parseDiagnostics(raw)

        assertEquals(2, errors.size)
        assertEquals(5, errors[0].line)
        assertEquals(9, errors[0].column)
        assertEquals(ErrorType.ERROR, errors[0].type)
        assertTrue(errors[0].message.contains("expected"))
        assertEquals(ErrorType.WARNING, errors[1].type)
        assertEquals("unused variable 'x' [-Wunused-variable]", errors[1].message)
    }

    @Test
    fun `parseDiagnostics returns empty for unrelated output`() {
        assertTrue(CompilerService.parseDiagnostics("Compilation succeeded\n").isEmpty())
        assertTrue(CompilerService.parseDiagnostics("").isEmpty())
    }

    @Test
    fun `parseDiagnostics handles TCC line-only format`() {
        // TCC prints "file.c:3: error: ..." (no column).
        val raw = """
            bad.c:3: error: identifier expected
            bad.c:7: warning: implicit declaration of function 'printf'
        """.trimIndent()

        val errors = CompilerService.parseDiagnostics(raw)

        assertEquals(2, errors.size)
        assertEquals(3, errors[0].line)
        assertEquals(0, errors[0].column)
        assertEquals(ErrorType.ERROR, errors[0].type)
        assertEquals("identifier expected", errors[0].message)
        assertEquals(7, errors[1].line)
        assertEquals(ErrorType.WARNING, errors[1].type)
        assertEquals("implicit declaration of function 'printf'", errors[1].message)
    }

    @Test
    fun `detectEnvironmentError maps permission denied to device-blocked message`() {
        val message = CompilerService.detectEnvironmentError(
            "/data/user/0/com.codeci.ide/files/CodeC/modules/clang-compiler/bin/" +
                "compiler-wrapper.sh[11]: /data/.../bin/clang: Permission denied"
        )
        assertEquals(CompilerService.DEVICE_EXEC_BLOCKED, message)
    }

    @Test
    fun `detectEnvironmentError maps format and library failures`() {
        assertEquals(
            CompilerService.ARCH_MISMATCH,
            CompilerService.detectEnvironmentError("clang: Exec format error")
        )
        assertEquals(
            CompilerService.TOOLCHAIN_INCOMPLETE,
            CompilerService.detectEnvironmentError(
                "error while loading shared libraries: libLLVM-21.so: cannot open shared object file"
            )
        )
    }

    @Test
    fun `detectEnvironmentError ignores normal diagnostics and blanks`() {
        assertNull(CompilerService.detectEnvironmentError("warning: unused variable 'y'"))
        assertNull(CompilerService.detectEnvironmentError(""))
        assertNull(CompilerService.detectEnvironmentError(null))
    }
}
