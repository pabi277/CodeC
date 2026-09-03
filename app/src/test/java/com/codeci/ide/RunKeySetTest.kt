package com.codeci.ide

import com.codeci.ide.ui.editor.KeysContext
import com.codeci.ide.ui.editor.KeysForContext
import com.codeci.ide.ui.editor.RunKey
import com.codeci.ide.ui.editor.RunKeySet
import com.codeci.ide.ui.editor.keysForContext
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 23.2 — the interactive-run key set and the context → keys mapping.
 * The editor key set itself stays covered by [EditorKeySetTest]; here we pin
 * the run keys (Enter/Ctrl+C/Tab + history arrows) and the three-way
 * `keysForContext` decision the screen's strip renders from.
 */
class RunKeySetTest {

    @Test
    fun `interactive run keys contain Enter and Ctrl+C`() {
        val actions = RunKeySet.KEYS.map { it.action }
        assertTrue(actions.containsAll(listOf(RunKey.SUBMIT, RunKey.INTERRUPT)))
    }

    @Test
    fun `interactive run keys include Tab`() {
        assertTrue(RunKeySet.KEYS.any { it.action == RunKey.TAB })
    }

    @Test
    fun `run keys are Enter, Ctrl+C, Tab, then history arrows`() {
        assertEquals(
            listOf(
                RunKey.SUBMIT,
                RunKey.INTERRUPT,
                RunKey.TAB,
                RunKey.HISTORY_UP,
                RunKey.HISTORY_DOWN
            ),
            RunKeySet.KEYS.map { it.action }
        )
    }

    @Test
    fun `run key labels read naturally`() {
        assertEquals("↵ Enter", RunKeySet.KEYS[0].label)
        assertEquals("Ctrl+C", RunKeySet.KEYS[1].label)
    }

    @Test
    fun `keysForContext maps an interactive run to the run set`() {
        val resolved = keysForContext(KeysContext.InteractiveRun)
        assertTrue(resolved is KeysForContext.RunKeys)
        assertEquals(RunKeySet.KEYS, (resolved as KeysForContext.RunKeys).defs)
    }

    @Test
    fun `keysForContext maps an editor context to the C key set with pairs`() {
        val resolved = keysForContext(KeysContext.Editor(LanguageType.C))
        assertTrue(resolved is KeysForContext.EditorKeys)
        val labels = (resolved as KeysForContext.EditorKeys).defs.map { it.label }
        assertTrue(labels.contains("{}"))
    }

    @Test
    fun `keysForContext keeps the per-language tail for C`() {
        val resolved = keysForContext(KeysContext.Editor(LanguageType.C)) as KeysForContext.EditorKeys
        assertEquals("->", resolved.defs.last().label)
    }

    @Test
    fun `keysForContext maps idle to no keys`() {
        assertTrue(keysForContext(KeysContext.Idle) is KeysForContext.None)
    }
}
