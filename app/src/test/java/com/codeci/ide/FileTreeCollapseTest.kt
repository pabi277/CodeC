package com.codeci.ide

import com.codeci.ide.ui.editor.FileTreeCollapse
import com.codeci.ide.ui.viewmodels.EditorFileEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    @Test fun `file name search includes ancestors and does not read contents`() {
        val found = FileTreeCollapse.search(tree, "A.PNG")
        assertEquals(listOf("src", "src/img", "src/img/a.png"), found.map { it.relativePath })
        assertEquals(tree, FileTreeCollapse.search(tree, "  "))
        assertTrue(FileTreeCollapse.search(tree, "not-found").isEmpty())
        assertEquals(listOf("src", "src/img", "src/img/a.png"),
            FileTreeCollapse.search(tree, "img/a").map { it.relativePath })
    }

    // ---- Phase 69.4 — the tree remembers its shape, per project -----------

    @Test
    fun `the remembered shape survives a round trip through storage`() {
        // Owner: *"remember what open by the use … in the same position no all
        // expend or collapse"*. Encode/decode is the whole storage format, so a
        // set must come back identical — including the empty one, which means
        // "the user opened every folder" and is NOT the same as never stored.
        for (shape in listOf(emptySet(), setOf("src"), setOf("src", "src/img"))) {
            assertEquals(shape, FileTreeCollapse.decode(FileTreeCollapse.encode(shape)))
        }
        // Sorted, so the same tree always writes the same bytes.
        assertEquals(
            FileTreeCollapse.encode(setOf("src", "src/img")),
            FileTreeCollapse.encode(setOf("src/img", "src"))
        )
    }

    @Test
    fun `never stored means the first open, and is not the same as all expanded`() {
        assertNull(FileTreeCollapse.decode(null))
        assertEquals(emptySet<String>(), FileTreeCollapse.decode(""))
    }

    @Test
    fun `a project opened for the first time starts with every folder closed`() {
        // Owner: *"When i import a zip or repository and open in editor it will
        // in collapse state"* — today a fresh project opens fully expanded.
        val dirs = FileTreeCollapse.allDirs(tree)
        assertEquals(dirs, FileTreeCollapse.initialTree(dirs, remembered = null))
        // …and a remembered shape always wins, even when it is "all expanded".
        assertEquals(emptySet<String>(), FileTreeCollapse.initialTree(dirs, remembered = emptySet()))
        assertEquals(setOf("src"), FileTreeCollapse.initialTree(dirs, remembered = setOf("src")))
    }

    @Test
    fun `folders that no longer exist drop out of what is remembered`() {
        // Only the folder that is GONE drops out: the two that still exist
        // keep the shape the user chose, whether that is open or closed.
        assertEquals(
            setOf("src", "src/img"),
            FileTreeCollapse.prune(setOf("src", "src/img", "src/gone"), setOf("src", "src/img"))
        )
        assertTrue(FileTreeCollapse.prune(setOf("src"), emptySet()).isEmpty())
    }
}
