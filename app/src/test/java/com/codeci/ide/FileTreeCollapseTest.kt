package com.codeci.ide

import com.codeci.ide.ui.editor.FileTreeCollapse
import com.codeci.ide.ui.viewmodels.EditorFileEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 16 — the drawer's collapse filter over the flat tree entries the
 * ViewModel publishes: a collapsed folder keeps its own row (so the chevron
 * stays tappable) while everything under it disappears; nested collapse sets
 * interact correctly.
 */
class FileTreeCollapseTest {

    private fun dir(path: String, depth: Int) =
        EditorFileEntry("proj", path, path.substringAfterLast('/'), depth, true)

    private fun file(path: String, depth: Int) =
        EditorFileEntry("proj", path, path.substringAfterLast('/'), depth, false)

    private val tree = listOf(
        dir("src", 0),
        file("src/main.c", 1),
        dir("src/img", 1),
        file("src/img/a.png", 2),
        file("README.md", 0)
    )

    @Test
    fun `empty collapse set shows everything`() {
        assertEquals(tree, FileTreeCollapse.visible(tree, emptySet()))
    }

    @Test
    fun `collapsed folder keeps its row and hides contents`() {
        val shown = FileTreeCollapse.visible(tree, setOf("src"))
        assertEquals(listOf("src", "README.md"), shown.map { it.relativePath })
    }

    @Test
    fun `nested collapse hides deeper rows but keeps both folder rows`() {
        val shown = FileTreeCollapse.visible(tree, setOf("src", "src/img"))
        assertEquals(listOf("src", "README.md"), shown.map { it.relativePath })
    }

    @Test
    fun `only the inner folder collapsed hides nothing above it`() {
        val shown = FileTreeCollapse.visible(tree, setOf("src/img"))
        assertEquals(listOf("src", "src/main.c", "src/img", "README.md"), shown.map { it.relativePath })
    }

    @Test
    fun `allDirs collects exactly the directory rows`() {
        assertEquals(setOf("src", "src/img"), FileTreeCollapse.allDirs(tree))
        assertTrue(FileTreeCollapse.allDirs(emptyList()).isEmpty())
    }
}
