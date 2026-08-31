package com.codeci.ide.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.utils.LanguageType
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 16 — the snippet / extra-keys row (Spck's signature), data-driven and
 * pure so the exact insert/caret math is host-tested. Rendering lives in
 * `SymbolBar`; the language hook mirrors Spck's per-language keyboard modes,
 * and the custom-snippet parsing (Spck "Custom Snippets", public docs) ships
 * here as the data model — the Settings editor UI is a recorded follow-up.
 */
sealed class EditorKey {
    /** Text inserted at the caret, replacing any selection. */
    data class Insert(val text: String) : EditorKey()

    /** Move the caret; selection collapses toward the movement. */
    enum class Move { LEFT, RIGHT, UP, DOWN }
    data class Caret(val move: Move) : EditorKey()

    /** Insert the configured indent run (tabSize spaces). */
    object Tab : EditorKey()
}

/** One rendered keycap. */
data class EditorKeyDef(
    val label: String,
    val key: EditorKey,
    val wide: Boolean = false
)

/**
 * Key-set + application logic. `TextFieldValue` is a plain JVM Compose data
 * class (no Android runtime), so CI unit-tests [apply] directly; the Compose
 * row iterates [keysFor] and forwards taps to [apply].
 */
object EditorKeySet {

    /** The shared keycap list: Spck's set — TAB { } ( ) ; < > / = " ' arrows. */
    private val GENERAL: List<EditorKeyDef> = listOf(
        EditorKeyDef("TAB", EditorKey.Tab, wide = true),
        EditorKeyDef("{", EditorKey.Insert("{")),
        EditorKeyDef("}", EditorKey.Insert("}")),
        EditorKeyDef("(", EditorKey.Insert("(")),
        EditorKeyDef(")", EditorKey.Insert(")")),
        EditorKeyDef(";", EditorKey.Insert(";")),
        EditorKeyDef("<", EditorKey.Insert("<")),
        EditorKeyDef(">", EditorKey.Insert(">")),
        EditorKeyDef("/", EditorKey.Insert("/")),
        EditorKeyDef("=", EditorKey.Insert("=")),
        EditorKeyDef("\"", EditorKey.Insert("\"")),
        EditorKeyDef("'", EditorKey.Insert("'")),
        EditorKeyDef("←", EditorKey.Caret(EditorKey.Caret.Move.LEFT)),
        EditorKeyDef("→", EditorKey.Caret(EditorKey.Caret.Move.RIGHT)),
        EditorKeyDef("↑", EditorKey.Caret(EditorKey.Caret.Move.UP)),
        EditorKeyDef("↓", EditorKey.Caret(EditorKey.Caret.Move.DOWN))
    )

    /** Small per-language tails (data-driven, per spec; one good general set first). */
    private fun languageTail(language: LanguageType?): List<EditorKeyDef> = when (language) {
        LanguageType.C -> listOf(EditorKeyDef("->", EditorKey.Insert("->")))
        LanguageType.CPP -> listOf(
            EditorKeyDef("->", EditorKey.Insert("->")),
            EditorKeyDef("::", EditorKey.Insert("::"))
        )
        LanguageType.PYTHON -> listOf(
            EditorKeyDef(":", EditorKey.Insert(":")),
            EditorKeyDef("_(self)", EditorKey.Insert("self "))
        )
        LanguageType.HTML_CSS -> listOf(
            EditorKeyDef("</>", EditorKey.Insert("</>"))
        )
        LanguageType.JAVASCRIPT -> listOf(
            EditorKeyDef("`", EditorKey.Insert("`")),
            EditorKeyDef("=>", EditorKey.Insert("=>"))
        )
        LanguageType.SHELL -> listOf(
            EditorKeyDef("$", EditorKey.Insert("$"))
        )
        else -> emptyList()
    }

    /**
     * Keys for [language]: the general set, the language tail, then any
     * [customSnippets] (name ⇒ body) parsed from the Settings string. The
     * row is horizontally scrollable so growth is safe.
     */
    fun keysFor(language: LanguageType?, customSnippets: String? = null): List<EditorKeyDef> =
        GENERAL + languageTail(language) + parseCustomSnippets(customSnippets)

    /**
     * Custom-snippet data model (Spck's "Custom Snippets"): one `label=text`
     * per line; `#` starts a comment; blank lines are skipped. The Settings
     * editing UI is a recorded follow-up — parsing + the keycap are this
     * phase's deliverable.
     */
    fun parseCustomSnippets(raw: String?): List<EditorKeyDef> =
        raw?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?.mapNotNull { line ->
                val label = line.substringBefore('=', "").trim()
                val body = line.substringAfter('=', "")
                if (label.isEmpty() || body.isEmpty()) null
                else EditorKeyDef(label, EditorKey.Insert(body))
            }
            ?.toList()
            .orEmpty()

    /**
     * Apply [key] to the buffer at [value]'s selection. Insert replaces the
     * selection and lands the caret after the text; TAB inserts exactly
     * [tabSize] spaces (2..8 clamped, the editor's indentation law); caret
     * moves collapse a selection first, and UP/DOWN travel by visual line via
     * the text itself (same column, clamped at both ends).
     */
    fun apply(key: EditorKey, value: TextFieldValue, tabSize: Int = 4): TextFieldValue {
        val text = value.text
        val sel = value.selection
        val start = min(sel.start, sel.end).coerceIn(0, text.length)
        val end = max(sel.start, sel.end).coerceIn(0, text.length)
        return when (key) {
            is EditorKey.Insert -> replaced(text, start, end, key.text, key.text.length)
            EditorKey.Tab -> {
                val spaces = " ".repeat(tabSize.coerceIn(2, 8))
                replaced(text, start, end, spaces, spaces.length)
            }
            is EditorKey.Caret -> when (key.move) {
                EditorKey.Caret.Move.LEFT -> caret(text, if (start != end) start else (start - 1).coerceAtLeast(0))
                EditorKey.Caret.Move.RIGHT -> caret(text, if (start != end) end else (start + 1).coerceAtMost(text.length))
                EditorKey.Caret.Move.UP -> caret(text, columnShift(text, start, -1))
                EditorKey.Caret.Move.DOWN -> caret(text, columnShift(text, end, +1))
            }
        }
    }

    private fun replaced(text: String, start: Int, end: Int, insert: String, caretShift: Int): TextFieldValue {
        val next = text.substring(0, start) + insert + text.substring(end)
        return TextFieldValue(next, TextRange(start + caretShift))
    }

    private fun caret(text: String, position: Int): TextFieldValue =
        TextFieldValue(text, TextRange(position.coerceIn(0, text.length)))

    /** Same column one line up/down; clamped at the buffer edges. */
    private fun columnShift(text: String, position: Int, direction: Int): Int {
        if (text.isEmpty()) return position
        val lineStart = text.lastIndexOf('\n', (position - 1).coerceAtLeast(0)) + 1
        val column = (position - lineStart).coerceAtLeast(0)
        val targetLineStart = when {
            direction < 0 -> if (lineStart == 0) return 0 else {
                text.lastIndexOf('\n', lineStart - 2) + 1
            }
            else -> {
                val lineEnd = text.indexOf('\n', position).let { if (it == -1) text.length else it }
                if (lineEnd >= text.length) return text.length
                lineEnd + 1
            }
        }
        val targetLineEnd = text.indexOf('\n', targetLineStart).let {
            if (it == -1) text.length else it
        }
        return min(targetLineStart + column, targetLineEnd)
    }
}
