package com.codeci.ide

import com.codeci.ide.ui.editor.ProjectPathGuard
import com.codeci.ide.ui.editor.ProjectSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Phase 55.3 — the Search slot's engine, on the host JVM.
 *
 * The panel is Compose (CI-only), so the engine is where the honesty lives:
 * nothing outside the project, nothing huge, nothing binary, no hang, and the
 * shot's five glyphs as real options.
 */
class ProjectSearchTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun project(): File = folder.newFolder("1st semester")

    private fun write(root: File, relative: String, text: String): File {
        val file = File(root, relative)
        file.parentFile?.mkdirs()
        file.writeText(text)
        return file
    }

    // ---- matching -----------------------------------------------------------

    @Test
    fun `an empty query returns nothing - the shot's empty RESULTS`() {
        assertTrue(ProjectSearch.matchesIn("hello world", "").isEmpty())
        assertTrue(ProjectSearch.matchesIn("hello world", "   ").isEmpty())
        assertTrue(ProjectSearch.search(project(), "").isEmpty())
    }

    @Test
    fun `hits carry the line and the 1-based column`() {
        val text = "alpha\nbeta gamma\nbeta"
        val hits = ProjectSearch.matchesIn(text, "beta")
        assertEquals(2, hits.size)
        assertEquals(2, hits[0].line)
        assertEquals(1, hits[0].column)
        assertEquals(3, hits[1].line)
        assertEquals("alpha", ProjectSearch.matchesIn(text, "alpha").first().text)
    }

    @Test
    fun `case is honoured only when the Aa glyph is on`() {
        val text = "Snake\nsnake"
        assertEquals(2, ProjectSearch.matchesIn(text, "snake").size)
        assertEquals(2, ProjectSearch.matchesIn(text, "snake", ProjectSearch.Options(caseSensitive = false)).size)
        assertEquals(1, ProjectSearch.matchesIn(text, "snake", ProjectSearch.Options(caseSensitive = true)).size)
        assertEquals(1, ProjectSearch.matchesIn(text, "Snake", ProjectSearch.Options(caseSensitive = true)).size)
    }

    @Test
    fun `the regex glyph switches between literal and pattern`() {
        val text = "<div class=\"a\">\nplain text"
        assertTrue(ProjectSearch.matchesIn(text, "\\bd").isEmpty())
        val regexHits = ProjectSearch.matchesIn(text, "\\bdiv\\b", ProjectSearch.Options(regex = true))
        assertEquals(1, regexHits.size)
        assertEquals(1, regexHits.first().line)
        // A literal "." is not a wildcard unless the user asked for regex.
        assertEquals(1, ProjectSearch.matchesIn("a.b", ".").size)
        assertEquals(3, ProjectSearch.matchesIn("a.b", ".", ProjectSearch.Options(regex = true)).size)
    }

    @Test
    fun `whole word does not match inside a longer word`() {
        val options = ProjectSearch.Options(wholeWord = true)
        assertTrue(ProjectSearch.matchesIn("snakes alive", "snake", options).isEmpty())
        assertEquals(1, ProjectSearch.matchesIn("a snake here", "snake", options).size)
    }

    @Test
    fun `a half-typed regex is silence, not a crash`() {
        assertNull(ProjectSearch.patternFor("([unclosed", ProjectSearch.Options(regex = true)))
        assertTrue(ProjectSearch.matchesIn("anything", "([unclosed", ProjectSearch.Options(regex = true)).isEmpty())
        assertNotNull(ProjectSearch.patternFor("ok", ProjectSearch.Options(regex = true)))
    }

    @Test
    fun `the hit list is capped`() {
        val text = (1..500).joinToString("\n") { "hit" }
        assertEquals(3, ProjectSearch.matchesIn(text, "hit", limit = 3).size)
        assertEquals(ProjectSearch.MAX_HITS, ProjectSearch.matchesIn(text, "hit").size)
    }

    // ---- walking the project ------------------------------------------------

    @Test
    fun `the whole project is searched, in a stable order, inside the root`() {
        val root = project()
        write(root, "index.html", "<h1>snake</h1>")
        write(root, "js/app.js", "// snake\nconst snake = 1")
        write(root, "notes.txt", "no match here")
        val hits = ProjectSearch.search(root, "snake")
        assertEquals(3, hits.size)
        assertEquals(listOf("index.html", "js/app.js", "js/app.js"), hits.map { it.relativePath })
        assertTrue(hits.all { it.line >= 1 })
    }

    @Test
    fun `skipped dirs and hidden dirs are never walked`() {
        val root = project()
        write(root, "index.html", "snake")
        write(root, ".git/config", "snake")
        write(root, "node_modules/dep/index.js", "snake")
        write(root, "build/out.txt", "snake")
        val hits = ProjectSearch.search(root, "snake")
        assertEquals(1, hits.size)
        assertEquals("index.html", hits.single().relativePath)
    }

    @Test
    fun `non-text extensions are not searched`() {
        val root = project()
        write(root, "logo.png", "snake")
        write(root, "app.kt", "snake")
        assertFalse(ProjectSearch.isSearchable("logo.png"))
        assertFalse(ProjectSearch.isSearchable("archive.zip"))
        assertTrue(ProjectSearch.isSearchable("index.html"))
        assertTrue(ProjectSearch.isSearchable("main.c"))
        assertEquals(1, ProjectSearch.search(root, "snake").size)
    }

    @Test
    fun `a huge file is skipped rather than read`() {
        val root = project()
        write(root, "big.txt", "snake\n" + "x".repeat((ProjectSearch.MAX_FILE_BYTES + 16).toInt()))
        write(root, "small.txt", "snake")
        // The cap is the whole point: the big file is skipped, the small one is not.
        assertTrue(ProjectSearch.searchFile(root, "big.txt", "snake").isEmpty())
        assertTrue(File(root, "big.txt").length() > ProjectSearch.MAX_FILE_BYTES)
        assertEquals(1, ProjectSearch.searchFile(root, "small.txt", "snake").size)
    }

    @Test
    fun `a binary-looking file is skipped`() {
        val root = project()
        write(root, "weird.txt", "snake\u0000more")
        assertTrue(ProjectSearch.searchFile(root, "weird.txt", "snake").isEmpty())
    }

    @Test
    fun `a missing file is an empty result, not an exception`() {
        assertTrue(ProjectSearch.searchFile(project(), "nope.txt", "snake").isEmpty())
    }

    // ---- the path guard -----------------------------------------------------

    @Test
    fun `a hit can never be read from outside the project`() {
        val root = project()
        assertNull(ProjectPathGuard.childOf(root, "../secret.txt"))
        assertNull(ProjectPathGuard.childOf(root, "a/../../secret.txt"))
        assertNull(ProjectPathGuard.childOf(root, "/etc/passwd"))
        assertNull(ProjectPathGuard.childOf(root, ""))
        assertNotNull(ProjectPathGuard.childOf(root, "js/app.js"))
        assertNotNull(ProjectPathGuard.childOf(root, "./js/app.js"))
        val outside = File(folder.root, "outside.txt")
        outside.writeText("snake")
        assertTrue(ProjectSearch.searchFile(root, "../outside.txt", "snake").isEmpty())
    }
}
