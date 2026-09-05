package com.codeci.ide

import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.editor.EditorKeyDef
import com.codeci.ide.ui.editor.KeyStripStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 26.1 — key strip persistence host test.
 */
class KeyStripStorageTest {

    @Test
    fun `serialize roundtrip preserves label and key`() {
        val defs = listOf(
            EditorKeyDef("TAB", EditorKey.Tab, wide = true),
            EditorKeyDef("()", EditorKey.Pair("(", ")"), swipeUp = EditorKey.Insert("("), swipeDown = EditorKey.Insert(")")),
            EditorKeyDef(";", EditorKey.Insert(";"), popup = EditorKey.Insert(":")),
            EditorKeyDef("/", EditorKey.Insert("/"), popup = EditorKey.CommentToggle),
            EditorKeyDef("←", EditorKey.Caret(EditorKey.Caret.Move.LEFT), popup = EditorKey.Caret(EditorKey.Caret.Move.LINE_START))
        )
        val json = KeyStripStorage.serialize(defs)
        val back = KeyStripStorage.deserialize(json)
        assertNotNull(back)
        assertEquals(defs.size, back!!.size)
        assertEquals(defs[0].label, back[0].label)
        assertEquals(defs[1].popup, null) // primary has no popup, swipe only
        assertEquals(defs[2].popup, back[2].popup)
        assertEquals(defs[3].popup, back[3].popup)
        assertEquals(EditorKey.CommentToggle, back[3].popup)
    }

    @Test
    fun `deserialize invalid json returns null for fallback`() {
        val bad = "{ not json"
        val res = KeyStripStorage.deserialize(bad)
        assertNull(res)
    }

    @Test
    fun `deserialize empty array returns empty list`() {
        val res = KeyStripStorage.deserialize("[]")
        assertNotNull(res)
        assertEquals(0, res!!.size)
    }

    @Test
    fun `serialize handles wide and swipe fields`() {
        val defs = listOf(EditorKeyDef("TAB", EditorKey.Tab, wide = true))
        val json = KeyStripStorage.serialize(defs)
        assertEquals(true, json.contains("\"wide\":true"))
    }
}
