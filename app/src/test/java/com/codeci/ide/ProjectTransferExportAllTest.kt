package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectTransfer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 42.3 §3 — "Export all projects": the everything-backup, pinned
 * against the spec's test plan (7 rows: round-trip over two roots; both
 * roots exported; shared entry cap errors; import side keeps its guards;
 * name collisions reported; empty-file parity).
 */
class ProjectTransferExportAllTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun recursiveBytes(root: File): Map<String, String> =
        root.walkTopDown()
            .filter { it.isFile && it.absoluteFile.path == it.canonicalFile.path }
            .associate {
                val rel = it.relativeTo(root).path.replace(File.separatorChar, '/')
                rel to it.readText()
            }

    private fun project(parent: File, name: String, vararg files: Pair<String, String>): File =
        File(parent, name).apply {
            files.forEach { (rel, content) ->
                File(this, rel).apply { parentFile?.mkdirs(); writeText(content) }
            }
        }

    @Test
    fun `two project roots export into one zip and re-import byte-exact`() {
        val internal = tmp.newFolder("internal")
        val external = tmp.newFolder("external")
        val p1 = project(internal, "Calc", "main.c" to "int main(){return 0;}", "lib/calc.h" to "h")
        val p2 = project(external, "Notes", "notes.txt" to "hello world")
        val out = ByteArrayOutputStream()

        val result = ProjectTransfer.exportAllZip(out, listOf(internal, external))

        assertEquals(2, result.projects)
        assertEquals(2, result.rootsWithProjects)
        assertEquals(emptyMap<String, String>(), result.renames)
        assertEquals(emptyList<String>(), result.skipped)

        // Round-trip into a CLEAN root: every file, byte-identical.
        val restored = tmp.newFolder("restored")
        ProjectTransfer.importAllZip(ByteArrayInputStream(out.toByteArray()), restored)
        assertEquals(recursiveBytes(p1), recursiveBytes(File(restored, "Calc")))
        assertEquals(recursiveBytes(p2), recursiveBytes(File(restored, "Notes")))
    }

    @Test
    fun `folders land in folders and files in files under their project name`() {
        val internal = tmp.newFolder("internal")
        project(internal, "App", "src/ui/screen.c" to "screen", "src/util.c" to "util")
        val out = ByteArrayOutputStream()
        ProjectTransfer.exportAllZip(out, listOf(internal))

        val restored = tmp.newFolder("restored")
        ProjectTransfer.importAllZip(ByteArrayInputStream(out.toByteArray()), restored)
        assertTrue(File(restored, "App/src/ui").isDirectory)
        assertEquals("screen", File(restored, "App/src/ui/screen.c").readText())
    }

    @Test
    fun `an empty file survives the round trip - the parity found late in earlier rounds`() {
        val internal = tmp.newFolder("internal")
        project(internal, "Empty", "blank.c" to "", "full.c" to "x")
        val out = ByteArrayOutputStream()
        ProjectTransfer.exportAllZip(out, listOf(internal))

        val restored = tmp.newFolder("restored")
        ProjectTransfer.importAllZip(ByteArrayInputStream(out.toByteArray()), restored)
        assertTrue(File(restored, "Empty/blank.c").isFile)
        assertEquals(0L, File(restored, "Empty/blank.c").length())
    }

    @Test
    fun `same-named projects from two roots are flattened visibly, never merged`() {
        // The 42.3 trap: "a/b vs a_b flattening" — a name collision in the
        // flat layout must not silently merge two projects; it gets a
        // deterministic suffix AND a line in the result's renames report.
        val rootA = tmp.newFolder("rootA")
        val rootB = tmp.newFolder("rootB")
        project(rootA, "Demo", "a.c" to "A")
        project(rootB, "Demo", "b.c" to "B")
        val out = ByteArrayOutputStream()

        val result = ProjectTransfer.exportAllZip(out, listOf(rootA, rootB))

        assertEquals(2, result.projects)
        assertEquals(mapOf("Demo" to "Demo-2"), result.renames)

        val restored = tmp.newFolder("restored")
        ProjectTransfer.importAllZip(ByteArrayInputStream(out.toByteArray()), restored)
        assertEquals("A", File(restored, "Demo/a.c").readText())
        assertEquals("B", File(restored, "Demo-2/b.c").readText())
    }

    @Test
    fun `an unsanitizable project name is skipped and reported, not exported silently`() {
        val internal = tmp.newFolder("internal")
        // A control character fails the import side's segment rules — the
        // export must not place bytes in an archive the import would refuse.
        val odd = File(internal, "badname").apply { mkdirs() }
        File(odd, "main.c").writeText("x")
        project(internal, "Good", "main.c" to "g")
        val out = ByteArrayOutputStream()

        val result = ProjectTransfer.exportAllZip(out, listOf(internal))

        assertEquals(1, result.projects)
        assertEquals(listOf("badname"), result.skipped)
    }

    @Test
    fun `the shared entry cap errors in the open rather than half-writing projects`() {
        // 12 quiet projects at 9 999 entries each is how a per-project cap
        // becomes a backup trap: the ALL backup shares ONE entry budget.
        val internal = tmp.newFolder("internal")
        val big = File(internal, "Big").apply { mkdirs() }
        repeat(10_002) { File(big, "file-%05d.c".format(it)).writeText("") }
        val out = ByteArrayOutputStream()

        try {
            ProjectTransfer.exportAllZip(out, listOf(internal))
            fail("expected the shared entry budget to reject the backup")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("too many files"))
        }
    }

    @Test
    fun `the import side of the everything-backup keeps its path guard`() {
        val evil = ByteArrayOutputStream()
        ZipOutputStream(evil).use { zip ->
            zip.putNextEntry(ZipEntry("Proj/../../../escape.txt"))
            zip.write("x".toByteArray())
            zip.closeEntry()
        }
        val restored = tmp.newFolder("restored")
        try {
            ProjectTransfer.importAllZip(ByteArrayInputStream(evil.toByteArray()), restored)
            fail("expected the traversal entry to be refused")
        } catch (e: SecurityException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("unsafe path"))
        }
    }
}
