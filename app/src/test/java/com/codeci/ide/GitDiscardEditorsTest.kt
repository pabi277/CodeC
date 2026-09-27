package com.codeci.ide

import com.codeci.ide.ui.projects.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class GitDiscardEditorsTest {
    private val root = File(System.getProperty("java.io.tmpdir"), "discard-coordinator-test")
    private class Editor : GitDiscardEditor {
        var clean = true
        var failReload = false
        val reloaded = mutableListOf<String>()
        override fun canDiscardFile(root: File, path: String) = clean
        override fun reloadDiscardedFile(root: File, path: String) {
            check(!failReload) { "read failed" }
            reloaded += path
        }
    }
    @Test fun `dirty buffer refuses before a write guard is acquired`() {
        val editor = Editor().apply { clean = false }
        GitDiscardEditors.register(editor)
        try {
            assertTrue(runCatching { GitDiscardEditors.begin(root, "a.txt") }.isFailure)
            assertFalse(GitDiscardEditors.blocks(root, "a.txt"))
        } finally { GitDiscardEditors.unregister(editor) }
    }
    @Test fun `only the selected root and path are blocked then reloaded`() {
        val editor = Editor()
        GitDiscardEditors.register(editor)
        val ticket = GitDiscardEditors.begin(root, "a.txt")
        try {
            assertTrue(GitDiscardEditors.blocks(root, "a.txt"))
            assertFalse(GitDiscardEditors.blocks(root, "b.txt"))
            assertFalse(GitDiscardEditors.blocks(File(root, "other"), "a.txt"))
            assertTrue(runCatching { GitDiscardEditors.begin(root, "b.txt") }.isFailure)
        } finally {
            GitDiscardEditors.finish(ticket)
            GitDiscardEditors.unregister(editor)
        }
        assertEquals(listOf("a.txt"), editor.reloaded)
        assertFalse(GitDiscardEditors.blocks(root, "a.txt"))
    }
    @Test fun `failed reload releases the guard and still reconciles other editors`() {
        val failed = Editor().apply { failReload = true }
        val good = Editor()
        GitDiscardEditors.register(failed); GitDiscardEditors.register(good)
        val ticket = GitDiscardEditors.begin(root, "a.txt")
        try {
            assertTrue(runCatching { GitDiscardEditors.finish(ticket) }.isFailure)
            assertFalse(GitDiscardEditors.blocks(root, "a.txt"))
            assertEquals(listOf("a.txt"), good.reloaded)
        } finally {
            GitDiscardEditors.unregister(failed); GitDiscardEditors.unregister(good)
        }
    }
}
