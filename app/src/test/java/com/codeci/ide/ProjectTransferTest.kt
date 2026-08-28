package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectTransfer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
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
