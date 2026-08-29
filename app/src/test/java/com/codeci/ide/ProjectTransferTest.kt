package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.projects.ProjectTransfer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectTransferTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `zip export and import preserve nested project files`() {
        val source = tmp.newFolder("source")
        File(source, ".codec/project.json").apply { parentFile?.mkdirs(); writeText("{\"version\":1}") }
        File(source, "src/main.c").apply { parentFile?.mkdirs(); writeText("main") }
        File(source, "include/calc.h").apply { parentFile?.mkdirs(); writeText("header") }

        val archive = ByteArrayOutputStream()
        ProjectTransfer.exportZip(source, archive)

        val destination = tmp.newFolder("destination")
        val entries = ProjectTransfer.importZip(ByteArrayInputStream(archive.toByteArray()), destination)
        assertTrue(entries >= 4)
        assertEquals("main", File(destination, "src/main.c").readText())
        assertEquals("header", File(destination, "include/calc.h").readText())
        assertTrue(File(destination, ".codec/project.json").isFile)
    }

    @Test
    fun `zip import preserves every normal filename and nested structure`() {
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            listOf(
                "src/main file.c" to "main",
                "include/calc (public).h" to "header",
                "assets/data.bin" to "binary",
                "LICENSE" to "license",
                "config/app settings.yaml" to "settings"
            ).forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }

        val destination = tmp.newFolder("all-files")
        ProjectTransfer.importZip(ByteArrayInputStream(archive.toByteArray()), destination)

        assertEquals("main", File(destination, "src/main file.c").readText())
        assertEquals("header", File(destination, "include/calc (public).h").readText())
        assertEquals("binary", File(destination, "assets/data.bin").readText())
        assertEquals("license", File(destination, "LICENSE").readText())
        assertEquals("settings", File(destination, "config/app settings.yaml").readText())
    }

    @Test
    fun `archive relative paths accept normal names but reject traversal`() {
        assertEquals(
            "src/main file.c",
            ProjectPathUtils.sanitizeArchiveRelativePath("src/main file.c")
        )
        assertNull(ProjectPathUtils.sanitizeArchiveRelativePath("../outside.c"))
        assertNull(ProjectPathUtils.sanitizeArchiveRelativePath("/absolute.c"))
    }

    @Test(expected = SecurityException::class)
    fun `zip import rejects traversal`() {
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            zip.putNextEntry(ZipEntry("../outside.txt"))
            zip.write("bad".toByteArray())
            zip.closeEntry()
        }
        ProjectTransfer.importZip(
            ByteArrayInputStream(archive.toByteArray()),
            tmp.newFolder("destination")
        )
    }
}
