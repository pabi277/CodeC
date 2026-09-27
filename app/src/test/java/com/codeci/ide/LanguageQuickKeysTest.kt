package com.codeci.ide

import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.editor.EditorKeyDef
import com.codeci.ide.ui.editor.EditorKeySet
import com.codeci.ide.ui.editor.LanguageQuickKeys
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 69.2 — the quick-key row answers to the file's language.
 *
 * Owner (2026-09-28), verbatim:
 *  - *"What language i am using don't matter it always give me same fixed
 *    quick keys"*;
 *  - *"Can it be auto detection my file extension and set the the quick keys
 *    order as per requirement and if it is not a standard file than a default
 *    quick key option"*;
 *  - *"B — Give each language a few real caps (recommended, Spck parity)"*.
 *
 * Every case here is host-side and pure: the table, the extension mapper, and
 * the assembled row — no Android, no device.
 */
class LanguageQuickKeysTest {

    private fun caps(language: LanguageType?) = LanguageQuickKeys.forLanguage(language)

    @Test
    fun `detection is the file extension through the one mapper`() {
        assertEquals(LanguageType.C, LanguageType.fromFileName("main.c"))
        assertEquals(LanguageType.PYTHON, LanguageType.fromFileName("src/app.py"))
        assertEquals(LanguageType.HTML, LanguageType.fromFileName("index.html"))
        assertEquals(LanguageType.TEXT, LanguageType.fromFileName("notes.zzz"))
        assertEquals(LanguageType.TEXT, LanguageType.fromFileName("README"))
    }

    @Test
    fun `the row shows the detected language's caps first`() {
        val row = EditorKeySet.keysFor(LanguageType.fromFileName("main.c")).map { it.label }
        assertEquals(listOf("#include", "printf", "int", "->"), row.take(4))
        // The general set is intact behind them — nothing was removed.
        assertTrue(row.containsAll(listOf("TAB", "()", "{}", "[]", "<>", "''", "←", "→", "↑", "↓")))
    }

    @Test
    fun `a non-standard file falls back to the default row`() {
        val fallback = EditorKeySet.keysFor(LanguageType.fromFileName("notes.zzz")).map { it.label }
        assertEquals("TAB", fallback.first())
        assertEquals(EditorKeySet.keysFor(null).map { it.label }, fallback)
    }

    @Test
    fun `every standard language has caps of its own`() {
        // The law this part exists for: the language always changes the row.
        for (language in LanguageType.entries - LanguageType.TEXT) {
            val defs = caps(language)
            assertTrue("$language has no quick keys", defs.size >= 3)
            assertTrue("$language has duplicate labels", defs.map { it.label }.distinct().size == defs.size)
        }
        assertEquals(emptyList<EditorKeyDef>(), caps(LanguageType.TEXT))
        assertEquals(emptyList<EditorKeyDef>(), caps(null))
    }

    @Test
    fun `caps are plain inserts or pairs that insert what they say`() {
        for (language in LanguageType.entries) {
            for (def in caps(language)) {
                assertTrue("${language}: empty label", def.label.isNotBlank())
                val key = def.key
                assertTrue("${language}/${def.label}: unexpected key kind", key is EditorKey.Insert || key is EditorKey.Pair)
                val rendered = when (key) {
                    is EditorKey.Insert -> key.text
                    is EditorKey.Pair -> key.open + key.close
                    else -> ""
                }
                assertTrue("${language}/${def.label}: empty insert", rendered.isNotEmpty())
                // A keycap inserts LITERALLY (EditorKeySet applies the text as it
                // is), so no snippet-syntax may leak into a cap.
                assertTrue("${language}/${def.label}: snippet syntax in a cap", !rendered.contains("\${"))
                if (key is EditorKey.Pair) {
                    assertTrue("${language}/${def.label}: pair does not name its cap",
                        rendered.contains(def.label))
                }
            }
        }
    }

    @Test
    fun `the pre-69_2 popup and flick layers survive the move`() {
        // 69.2 moved and grew these caps; it did not simplify them.
        val json = caps(LanguageType.JSON)
        assertEquals(EditorKey.Insert(": "), json.first { it.label == ":" }.popup)
        val literals = json.first { it.label == "null" }
        assertEquals(EditorKey.Insert("true"), literals.swipeUp)
        assertEquals(EditorKey.Insert("false"), literals.swipeDown)
    }

    @Test
    fun `the language's own symbols are carried by the caps that name them`() {
        // A few pinned examples, one per family, so a silent edit to the table
        // cannot quietly change what a tap types.
        fun insertOf(language: LanguageType, label: String): String {
            val def = caps(language).first { it.label == label }
            val key = def.key
            return when (key) {
                is EditorKey.Insert -> key.text
                is EditorKey.Pair -> key.open + key.close
                else -> ""
            }
        }
        assertEquals("#include <stdio.h>\n", insertOf(LanguageType.C, "#include"))
        assertEquals("printf(\"\");", insertOf(LanguageType.C, "printf"))
        assertEquals("def ", insertOf(LanguageType.PYTHON, "def"))
        assertEquals("<tag></tag>", insertOf(LanguageType.HTML, "<tag>"))
        assertEquals("console.log()", insertOf(LanguageType.JAVASCRIPT, "log"))
        assertEquals(" := ", insertOf(LanguageType.GO, ":="))
        assertEquals("#!/bin/sh\n", insertOf(LanguageType.SHELL, "#!"))
    }
}
