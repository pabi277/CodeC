package com.codeci.ide

import com.codeci.ide.ui.utils.migrateCSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `migrate copies c files from shared storage into the executable dir`() {
        val shared = tmp.newFolder("external", "CodeC", "projects")
        val internal = tmp.newFolder("files", "CodeC", "projects")
        File(shared, "main.c").writeText("int main(){return 0;}")
        File(shared, "notes.txt").writeText("ignored")

        val copied = migrateCSources(listOf(shared, internal), internal)

        assertEquals(1, copied)
        assertTrue(File(internal, "main.c").exists())
        assertEquals("int main(){return 0;}", File(internal, "main.c").readText())
        assertFalse(File(internal, "notes.txt").exists())
    }

    @Test
    fun `migrate overwrites when the source is newer`() {
        val shared = tmp.newFolder("sdcard", "CodeC", "projects")
        val internal = tmp.newFolder("data", "CodeC", "projects")
        val dest = File(internal, "main.c")
        dest.writeText("old")
        dest.setLastModified(1_000L)
        val src = File(shared, "main.c")
        src.writeText("new")
        src.setLastModified(2_000L)

        migrateCSources(listOf(shared), internal)

        assertEquals("new", dest.readText())
    }

    @Test
    fun `migrate skips the destination directory itself`() {
        val internal = tmp.newFolder("files", "CodeC", "projects")
        File(internal, "main.c").writeText("keep")

        val copied = migrateCSources(listOf(internal), internal)

        assertEquals(0, copied)
        assertEquals("keep", File(internal, "main.c").readText())
    }
}
