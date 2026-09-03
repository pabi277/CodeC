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
    fun `exportZipToCache writes a zip under the cache shares dir`() {
        val source = tmp.newFolder("share-source")
        File(source, "main.c").writeText("int main(){}")
        val cache = tmp.newFolder("share-cache")
        val zip = ProjectTransfer.exportZipToCache(source, cache, "share-source.zip")
        assertTrue(zip.isFile)
        assertEquals("share-source.zip", zip.name)
        assertEquals(File(cache, "shares"), zip.parentFile)

        // The cached ZIP must contain the same files exportZip produces.
        val extracted = tmp.newFolder("share-extracted")
        val entries = ProjectTransfer.importZip(zip.inputStream(), extracted)
        assertTrue(entries >= 2)
        assertEquals("int main(){}", File(extracted, "main.c").readText())
    }

    @Test
    fun `exportZipToCache sanitises the zip file name`() {
        val source = tmp.newFolder("share-unsafe")
        File(source, "a.c").writeText("a")
        val cache = tmp.newFolder("unsafe-cache")
        val zip = ProjectTransfer.exportZipToCache(source, cache, "my/project?name.zip")
        assertTrue(zip.name.matches(Regex("[A-Za-z0-9._-]+")))
        assertTrue(zip.isFile)
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
