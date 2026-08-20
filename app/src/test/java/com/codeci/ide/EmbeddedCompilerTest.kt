package com.codeci.ide

import com.codeci.ide.ui.services.EmbeddedCompiler
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedCompilerTest {

    @Test
    fun `compile command uses static musl flags and bundle-relative paths`() {
        val source = File("/data/user/0/com.codeci.ide/files/CodeC/temp/source_1.c")
        val output = File("/data/user/0/com.codeci.ide/files/CodeC/temp/program_1")

        val cmd = EmbeddedCompiler.buildCompileCommand("c11", warnings = true, optimization = 2, source, output)

        assertEquals(
            listOf(
                "-nostdlib", "-static", "-std=c11", "-O2",
                "-Wall", "-Wextra",
                "-I", "include-tcc",
                "-I", "include",
                "-B", ".",
                "-L", ".",
                "crt1.o", "crti.o",
                source.absolutePath,
                "libtcc1.a", "libc.a", "libtcc1.a", "libc.a", "crtn.o",
                "-o", output.absolutePath
            ),
            cmd
        )
    }

    @Test
    fun `compile command honours no-warnings, O0 and normalizes the standard`() {
        val source = File("s.c")
        val output = File("p")

        val cmd = EmbeddedCompiler.buildCompileCommand("C17", warnings = false, optimization = 0, source, output)

        assertEquals("-std=c17", cmd[2])
        assertEquals("-O0", cmd[3])
        assertFalse(cmd.contains("-Wall"))
        assertFalse(cmd.contains("-Wextra"))
    }

    @Test
    fun `optimization is clamped into the 0 to 3 range`() {
        val source = File("s.c")
        val output = File("p")

        val high = EmbeddedCompiler.buildCompileCommand("c11", false, 9, source, output)
        val low = EmbeddedCompiler.buildCompileCommand("c11", false, -3, source, output)

        assertTrue(high.contains("-O3"))
        assertTrue(low.contains("-O0"))
    }

    @Test
    fun `abi dirs cover every ABI we ship a jniLibs binary for`() {
        assertEquals(listOf("arm64-v8a", "x86_64"), EmbeddedCompiler.ABI_DIRS)
    }
}
