package com.codeci.ide

import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.editor.EditorTab
import com.codeci.ide.ui.editor.TabSort
import com.codeci.ide.ui.editor.TabSortPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 60 — the tab menu's three sorts, pure over `EditorTab`.
 *
 * What a device round could only see as "the tabs moved" is pinned here: the
 * order each sort produces, the case-insensitive comparison, the tiebreaks
 * (name → path), where a file with no extension lands, and the two invariants
 * the menu depends on — the input list is never mutated, and the sort is
 * total, so the same tab set always produces the same order.
 */
class TabSortPolicyTest {

    private fun tab(path: String, saved: String = "x", buffer: String = "x") = EditorTab(
        relativePath = path,
        buffer = TextFieldValue(buffer),
        savedText = saved,
    )

    @Test
    fun `the menu asks for exactly the three sorts the spec names`() {
        assertEquals(
            listOf(TabSort.NAME, TabSort.EXTENSION, TabSort.PATH),
            TabSortPolicy.MENU_ORDER,
        )
        assertEquals(3, TabSort.entries.size)
    }

    @Test
    fun `an extension is the text after the last dot`() {
        assertEquals("kt", TabSortPolicy.extensionOf("Main.kt"))
        assertEquals("gz", TabSortPolicy.extensionOf("archive.tar.gz"))
        assertEquals("json", TabSortPolicy.extensionOf("app/src/.codec.json"))
    }

    @Test
    fun `no dot and a leading dot both mean no extension`() {
        assertEquals("", TabSortPolicy.extensionOf("Makefile"))
        assertEquals("", TabSortPolicy.extensionOf(".gitignore"))
        assertEquals("", TabSortPolicy.extensionOf(""))
        // A trailing dot is an empty extension, not the file's own name.
        assertEquals("", TabSortPolicy.extensionOf("odd."))
    }

    @Test
    fun `name sorts alphabetically, ignoring case`() {
        val tabs = listOf(tab("src/zeta.py"), tab("Beta.kt"), tab("alpha.kt"), tab("gamma.js"))
        assertEquals(
            listOf("alpha.kt", "Beta.kt", "gamma.js", "src/zeta.py"),
            TabSortPolicy.sort(tabs, TabSort.NAME).map { it.relativePath },
        )
    }

    @Test
    fun `name breaks a same-name tie by path`() {
        val tabs = listOf(tab("b/util.kt"), tab("a/util.kt"), tab("A/util.kt"))
        assertEquals(
            listOf("A/util.kt", "a/util.kt", "b/util.kt"),
            TabSortPolicy.sort(tabs, TabSort.NAME).map { it.relativePath },
        )
    }

    @Test
    fun `extension groups by type, extensionless files first`() {
        val tabs = listOf(
            tab("main.py"),
            tab("index.html"),
            tab("Makefile"),
            tab("app.kt"),
            tab("b.js"),
            tab("a.js"),
        )
        assertEquals(
            listOf("Makefile", "index.html", "a.js", "b.js", "app.kt", "main.py"),
            TabSortPolicy.sort(tabs, TabSort.EXTENSION).map { it.relativePath },
        )
    }

    @Test
    fun `extension compares case-insensitively too`() {
        val tabs = listOf(tab("B.KT"), tab("a.kt"), tab("c.java"))
        assertEquals(
            listOf("c.java", "a.kt", "B.KT"),
            TabSortPolicy.sort(tabs, TabSort.EXTENSION).map { it.relativePath },
        )
    }

    @Test
    fun `path sorts whole paths, so folders group their files`() {
        val tabs = listOf(tab("src/ui/View.kt"), tab("app/build.gradle"), tab("src/Main.kt"))
        assertEquals(
            listOf("app/build.gradle", "src/Main.kt", "src/ui/View.kt"),
            TabSortPolicy.sort(tabs, TabSort.PATH).map { it.relativePath },
        )
    }

    @Test
    fun `sorting never mutates the list it was given`() {
        val tabs = listOf(tab("z.py"), tab("a.kt"))
        TabSortPolicy.sort(tabs, TabSort.NAME)
        assertEquals(listOf("z.py", "a.kt"), tabs.map { it.relativePath })
    }

    @Test
    fun `the buffers ride along with their tabs`() {
        val tabs = listOf(tab("z.py", saved = "old", buffer = "new"), tab("a.kt"))
        val sorted = TabSortPolicy.sort(tabs, TabSort.NAME)
        val z = sorted.first { it.relativePath == "z.py" }
        assertEquals("new", z.buffer.text)
        assertEquals("old", z.savedText)
    }

    @Test
    fun `every sort is total - the same set always lands in the same order`() {
        val tabs = listOf(tab("b/x.kt"), tab("B/x.KT"), tab("a/y"), tab("a/Y.kt"), tab("same.kt"))
        for (sort in TabSortPolicy.MENU_ORDER) {
            val once = TabSortPolicy.sort(tabs, sort).map { it.relativePath }
            val shuffled = TabSortPolicy.sort(tabs.reversed(), sort).map { it.relativePath }
            assertEquals("$sort is not deterministic", once, shuffled)
            assertEquals("$sort lost or duplicated a tab", tabs.size, once.size)
            assertTrue("$sort left the active set empty", once.isNotEmpty())
        }
    }
}
