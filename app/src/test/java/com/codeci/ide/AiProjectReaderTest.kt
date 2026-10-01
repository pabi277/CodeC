package com.codeci.ide

import com.codeci.ide.ui.ai.AiProjectFiles
import com.codeci.ide.ui.ai.AiProjectReader
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 78 (AI Level 2) — the project walk, against a real temporary tree on
 * the host JVM. `AiProjectReader` is `java.io` only, so none of this needs
 * Robolectric.
 *
 * What these cases exist to prove: a credential-shaped file never reaches the
 * candidate list, nothing outside the project root does either (by symlink or
 * otherwise), the walk is bounded, and a dirty editor buffer is never replaced
 * by stale disk bytes.
 */
class AiProjectReaderTest {

    private fun tempDir(tag: String): File =
        Files.createTempDirectory("codec78-$tag").toFile()

    private fun File.write(body: String) = apply { parentFile?.mkdirs(); writeText(body) }

    /** A small project with everything Level 2 must get right in it. */
    private fun buildProject(): File {
        val root = tempDir("proj")
        File(root, "main.c").write("int main(){ return area(2); }\n".repeat(20))
        File(root, ".env").write("API_KEY=supersecret\n")
        File(root, ".env.production").write("TOKEN=abc\n")
        File(root, ".npmrc").write("//registry:_authToken=abc\n")
        File(root, "photo.png").write("not text at all")
        File(root, "notes.txt").write("area helper notes\n".repeat(20))
        File(root, "node_modules/lib.js").write("module.exports = 1\n".repeat(20))
        File(root, ".git/config").write("[core]\n")
        File(root, "src/area.py").write("def area(r):\n    return 3.14*r*r\n".repeat(20))
        File(root, "blob.c").writeText("int x = 0;\u0000\u0000binary here\n")
        return root
    }

    @Test
    fun `code files are found and the rest is not`() {
        val root = buildProject()
        try {
            val paths = AiProjectReader.scan(root, "where is area defined", null, null, false)
                .candidates.map { it.relativePath }
            assertTrue("main.c", paths.contains("main.c"))
            assertTrue("src/area.py", paths.contains("src/area.py"))
            assertTrue("notes.txt", paths.contains("notes.txt"))
            assertFalse("a png is not code", paths.contains("photo.png"))
            assertFalse("a NUL file is not text", paths.contains("blob.c"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `credential-shaped files are never offered - only counted`() {
        val root = buildProject()
        try {
            val scan = AiProjectReader.scan(root, "area", null, null, false)
            val paths = scan.candidates.map { it.relativePath }
            for (secret in listOf(".env", ".env.production", ".npmrc")) {
                assertFalse("$secret must never be a candidate", paths.contains(secret))
            }
            assertTrue("and they must be counted, not silently ignored", scan.skippedSecret >= 3)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `the secret text itself never appears anywhere in the result`() {
        val root = buildProject()
        try {
            val scan = AiProjectReader.scan(root, "area", null, null, false)
            for (c in scan.candidates) {
                assertFalse("${c.relativePath} must not carry the .env secret", c.text.contains("supersecret"))
                assertFalse("${c.relativePath} must not carry the npm token", c.text.contains("_authToken"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `build output and vcs directories are pruned`() {
        val root = buildProject()
        try {
            val scan = AiProjectReader.scan(root, "area", null, null, false)
            val paths = scan.candidates.map { it.relativePath }
            assertTrue("nothing from node_modules", paths.none { it.startsWith("node_modules") })
            assertTrue("nothing from .git", paths.none { it.startsWith(".git/") })
            assertTrue("pruned directories are counted", scan.skippedDirectories >= 2)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `symlinks are skipped - both inside the project and escaping it`() {
        val root = buildProject()
        val outside = tempDir("outside")
        try {
            File(outside, "other_project.py").write("print('nope')\n".repeat(20))
            Files.createSymbolicLink(File(root, "link.py").toPath(), File(outside, "other_project.py").toPath())
            Files.createSymbolicLink(File(root, "esc").toPath(), outside.toPath())

            val paths = AiProjectReader.scan(root, "area", null, null, false)
                .candidates.map { it.relativePath }
            assertFalse("a file symlink is not followed", paths.contains("link.py"))
            assertTrue("a directory symlink is not entered", paths.none { it.startsWith("esc/") })
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun `no candidate path is absolute - the device layout never leaves`() {
        val root = buildProject()
        try {
            for (c in AiProjectReader.scan(root, "area", null, null, false).candidates) {
                assertFalse("${c.relativePath} must be project-relative", c.relativePath.startsWith("/"))
                assertFalse("${c.relativePath} must not contain the root", c.relativePath.contains(root.absolutePath))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a dirty buffer is used instead of the disk copy, and says so`() {
        val root = buildProject()
        try {
            val scan = AiProjectReader.scan(root, "area", "main.c", "int main(){ return UNSAVED_EDIT; }\n", true)
            val open = scan.candidates.firstOrNull { it.relativePath == "main.c" }
            assertTrue("the open file must be a candidate", open != null)
            assertTrue("the buffer text wins", open!!.text.contains("UNSAVED_EDIT"))
            assertTrue("and it is labelled as the buffer", open.fromBuffer)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a clean buffer falls back to the disk copy`() {
        val root = buildProject()
        try {
            val scan = AiProjectReader.scan(root, "area", "main.c", "int main(){ return UNSAVED_EDIT; }\n", false)
            val open = scan.candidates.firstOrNull { it.relativePath == "main.c" }
            assertTrue("the open file must be a candidate", open != null)
            assertFalse("the disk copy is used", open!!.text.contains("UNSAVED_EDIT"))
            assertFalse(open.fromBuffer)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a dirty buffer for another file does not leak into this one`() {
        val root = buildProject()
        try {
            val scan = AiProjectReader.scan(root, "area", "src/area.py", "UNSAVED = 1\n", true)
            val main = scan.candidates.firstOrNull { it.relativePath == "main.c" }
            assertTrue(main != null)
            assertFalse("main.c must still be the disk copy", main!!.text.contains("UNSAVED"))
            assertFalse(main.fromBuffer)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a missing or unreadable root answers empty instead of throwing`() {
        val gone = File(tempDir("gone"), "does-not-exist")
        try {
            val scan = AiProjectReader.scan(gone, "x", null, null, false)
            assertEquals(0, scan.candidates.size)
            assertFalse(scan.hitEntryCap)
            assertEquals(0, scan.filesSeen)
        } finally {
            gone.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun `the walk stops at its entry cap and reports that it did`() {
        val big = tempDir("big")
        try {
            for (i in 0 until (AiProjectFiles.MAX_ENTRIES + 5)) {
                File(big, "f$i.c").writeText("int x$i = 0;\n")
            }
            val scan = AiProjectReader.scan(big, "x", null, null, false)
            assertTrue("the cap must trip on a huge tree", scan.hitEntryCap)
            assertTrue(
                "and it must not read the whole tree",
                scan.candidates.size <= AiProjectFiles.READ_SHORTLIST
            )
        } finally {
            big.deleteRecursively()
        }
    }

    @Test
    fun `a file larger than the read ceiling is not opened at all`() {
        val root = tempDir("huge")
        try {
            File(root, "huge.c").writeText("x".repeat((AiProjectReader.MAX_FILE_BYTES + 1024).toInt()))
            File(root, "small.c").writeText("int main(){ return 0; }\n".repeat(20))
            val paths = AiProjectReader.scan(root, "main", null, null, false)
                .candidates.map { it.relativePath }
            assertFalse("the oversized file is skipped", paths.contains("huge.c"))
            assertTrue("the normal one is kept", paths.contains("small.c"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `an empty project scans to nothing without failing`() {
        val root = tempDir("empty")
        try {
            val scan = AiProjectReader.scan(root, "anything", null, null, false)
            assertEquals(0, scan.candidates.size)
            assertEquals(0, scan.filesSeen)
            assertEquals(0, scan.totalLeftOut)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `the binary and line helpers behave on their own`() {
        assertTrue(AiProjectReader.looksBinary("abc\u0000def"))
        assertFalse(AiProjectReader.looksBinary("abcdef"))
        assertEquals(3, AiProjectReader.countLines("a\nb\nc"))
        // A trailing newline still opens a (empty) third line — the count is
        // "lines the caret can be on", which is what the preview reports.
        assertEquals(3, AiProjectReader.countLines("a\nb\n"))
        assertEquals(0, AiProjectReader.countLines(""))
    }
}
