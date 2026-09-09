package com.codeci.ide

import com.codeci.ide.ui.editor.OutputDiagnosticTarget
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 32.3 — the tap-to-line resolution behind the Output Panel's clickable
 * diagnostic lines. A tap must land on the USER's file; a compiler temp name
 * (`source_<stamp>.c`) or a name that is not a real file in the folder falls
 * back to the active file instead of silently doing nothing.
 */
class OutputDiagnosticTargetTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun rootWithFile(name: String): Pair<File, File> {
        val root = tmp.newFolder("proj")
        val file = File(root, name).apply {
            parentFile?.mkdirs()
            writeText("x")
        }
        return root to file
    }

    @Test
    fun `a real project file resolves to its relative path`() {
        val (root, file) = rootWithFile("src/main.c")
        val resolved = OutputDiagnosticTarget.resolve(root, "src/main.c")
        assertEquals(file.canonicalFile, resolved?.canonicalFile)
        assertEquals(
            "src/main.c",
            OutputDiagnosticTarget.targetOrActive(root, "src/main.c", "main.c")
        )
    }

    @Test
    fun `an absolute path inside the root resolves`() {
        val (root, file) = rootWithFile("main.c")
        val resolved = OutputDiagnosticTarget.resolve(root, file.absolutePath)
        assertEquals(file.canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun `a path escaping the root is refused`() {
        val (root, _) = rootWithFile("main.c")
        val outside = tmp.newFolder("other").apply { File(this, "x.c").writeText("x") }
        assertNull(OutputDiagnosticTarget.resolve(root, File(outside, "x.c").absolutePath))
    }

    @Test
    fun `a compiler temp name falls back to the active file`() {
        val (root, _) = rootWithFile("main.c")
        assertEquals(
            "main.c",
            OutputDiagnosticTarget.targetOrActive(root, "source_12345.c", "main.c")
        )
        assertEquals(
            "main.c",
            OutputDiagnosticTarget.targetOrActive(root, "/tmp/source_9.c", "main.c")
        )
    }

    @Test
    fun `a name that is not a real file in the folder falls back too`() {
        val (root, _) = rootWithFile("main.c")
        assertEquals(
            "main.c",
            OutputDiagnosticTarget.targetOrActive(root, "deleted.c", "main.c")
        )
    }

    @Test
    fun `temp source names are classified`() {
        assertTrue(OutputDiagnosticTarget.isTempSource("source_12345.c"))
        assertTrue(OutputDiagnosticTarget.isTempSource("/tmp/source_1.c"))
        assertTrue(OutputDiagnosticTarget.isTempSource("/data/tmp/source_9.c"))
        assertFalse(OutputDiagnosticTarget.isTempSource("main.c"))
        assertFalse(OutputDiagnosticTarget.isTempSource("src/util.c"))
    }
}
