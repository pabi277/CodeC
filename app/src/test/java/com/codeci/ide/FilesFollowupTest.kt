package com.codeci.ide

import com.codeci.ide.ui.editor.NewFilePath
import com.codeci.ide.ui.editor.SingleFileTransfer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilesFollowupTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `new relative file creates missing parents without changing the typed name`() {
        val root = temp.newFolder()
        assertEquals("css/subjects.css", NewFilePath.create(root, null, "css/subjects.css").getOrThrow())
        assertTrue(File(root, "css/subjects.css").isFile)
        assertEquals(0L, File(root, "css/subjects.css").length())
        assertEquals("website/css/subjects.css", NewFilePath.create(root, "website", "css/subjects.css").getOrThrow())
        assertTrue(File(root, "website/css/subjects.css").isFile)
    }

    @Test fun `new file rejects blank absolute traversal and malformed paths`() {
        listOf("", " ", "/x.css", "../x.css", "css/../../x.css", "css/", "css//x.css", "css\\x.css", "./x.css").forEach {
            assertNull(it, NewFilePath.resolve(null, it))
        }
        assertNull(NewFilePath.resolve("../outside", "x.css"))
    }

    @Test fun `existing files and blocking parents are never replaced`() {
        val root = temp.newFolder()
        File(root, "css").mkdir()
        val file = File(root, "css/a.css").apply { writeText("keep") }
        assertTrue(NewFilePath.create(root, null, "css/a.css").isFailure)
        assertTrue(NewFilePath.create(root, null, "css/a.css/child.txt").isFailure)
        assertEquals("keep", file.readText())
        assertTrue(NewFilePath.create(root, null, "css").isFailure)
    }

    @Test fun `creation refuses symlink escape`() {
        val root = temp.newFolder()
        val outside = temp.newFolder()
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        assertTrue(NewFilePath.create(root, null, "link/stolen.css").isFailure)
        assertFalse(File(outside, "stolen.css").exists())
    }

    @Test fun `export is a named binary snapshot independent of subsequent source changes`() {
        val cache = temp.newFolder()
        val source = temp.newFile("image.png").apply { writeBytes(byteArrayOf(0, -1, 12, 13, 10)) }
        val snapshot = SingleFileTransfer.snapshot(source, cache)
        source.writeText("changed afterwards")
        val output = ByteArrayOutputStream()
        SingleFileTransfer.copy(snapshot, output)
        assertArrayEquals(byteArrayOf(0, -1, 12, 13, 10), output.toByteArray())
        assertEquals("image.png", snapshot.name)
        assertEquals(snapshot, SingleFileTransfer.pending(cache, snapshot.path))
        assertNull(SingleFileTransfer.pending(cache, source.path))
        SingleFileTransfer.discard(cache, snapshot)
        assertFalse(snapshot.exists())
        assertTrue(source.exists())
    }

    @Test fun `same file name exports cannot replace an already shared snapshot`() {
        val cache = temp.newFolder()
        val source = temp.newFile("a.txt").apply { writeText("first") }
        val first = SingleFileTransfer.snapshot(source, cache)
        source.writeText("second")
        val second = SingleFileTransfer.snapshot(source, cache)
        assertNotEquals(first, second)
        assertEquals("first", first.readText())
        assertEquals("second", second.readText())
    }
}
