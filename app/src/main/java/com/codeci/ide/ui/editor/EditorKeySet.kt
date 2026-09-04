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
 *
 * Phase 26.1 — key strip 2.0: long-press popup keys, swipe layers, hold-repeat,
 * and user-editable sets. The strip gains popup + swipe dimensions without
 * gaining a row — Termux extra-keys popup density + Unexpected/FlorisBoard
 * swipe layers, host-tested as a pure state machine.
 */
sealed class EditorKey {
    /** Text inserted at the caret, replacing any selection. */
    data class Insert(val text: String) : EditorKey()

    /** Move the caret; selection collapses toward the movement. */
    data class Caret(val move: Move) : EditorKey() {
        enum class Move {
            LEFT, RIGHT, UP, DOWN,
            LINE_START, LINE_END,
            PAGE_UP, PAGE_DOWN,
            DOC_START, DOC_END
        }
    }

    /** Insert the configured indent run (tabSize spaces). */
    object Tab : EditorKey()

    /**
     * Phase 22.5 — a bracket/quote PAIR on one keycap. Typing `(` on a phone
     * almost always means "I want `()`\", and the closer is the character that
     * is most awkward to reach on a soft keyboard. Tapping the cap inserts
     * [open] + [close] and lands the caret BETWEEN them; with a selection it
     * wraps the selection instead (and keeps it selected), which is the
     * standard editor behavior for surround.
     */
    data class Pair(val open: String, val close: String) : EditorKey()

    /** Phase 26.2 — delete the previous word (hold-repeat / swipe-on-DEL). */
    object DeleteWord : EditorKey()

    /** Phase 26.2 — toggle line comment (popup on /). */
    object CommentToggle : EditorKey()
}

/** One rendered keycap — Phase 26.1 adds popup + swipe layers. */
data class EditorKeyDef(
    val label: String,
    val key: EditorKey,
    val wide: Boolean = false,
    val popup: EditorKey? = null,
    val swipeUp: EditorKey? = null,
    val swipeDown: EditorKey? = null
)

/**
 * Key-set + application logic. `TextFieldValue` is a plain JVM Compose data
 * class (no Android runtime), so CI unit-tests [apply] directly; the Compose
 * row iterates [keysFor] and forwards taps to [apply].
 */
object EditorKeySet {

    // Phase 26.1 hold-repeat constants — the same as 25.4's arrow item.
    const val HOLD_INITIAL_DELAY_MS = 150L
    const val HOLD_REPEAT_INTERVAL_MS = 40L
    const val LONG_PRESS_MS = 300L

    /** The shared keycap list: Spck's set — TAB { } ( ) ; < > / = " ' arrows. */
    private val GENERAL: List<EditorKeyDef> = listOf(
        EditorKeyDef("TAB", EditorKey.Tab, wide = true),
        // Phase 22.5 — pairs on one cap + Phase 26.1 swipe layers: flick-up = opener only, flick-down = closer only.
        EditorKeyDef(
            "()", EditorKey.Pair("(", ")"),
            swipeUp = EditorKey.Insert("("), swipeDown = EditorKey.Insert(")")
        ),
        EditorKeyDef(
            "{}", EditorKey.Pair("{", "}"),
            swipeUp = EditorKey.Insert("{"), swipeDown = EditorKey.Insert("}")
        ),
        EditorKeyDef(
            "[]", EditorKey.Pair("[", "]"),
            swipeUp = EditorKey.Insert("["), swipeDown = EditorKey.Insert("]")
        ),
        EditorKeyDef(
            "<>", EditorKey.Pair("<", ">"),
            swipeUp = EditorKey.Insert("<"), swipeDown = EditorKey.Insert(">")
        ),
        EditorKeyDef(
            "\"\"", EditorKey.Pair("\"", "\""),
            popup = EditorKey.Insert("`"),
            swipeUp = EditorKey.Insert("\""), swipeDown = EditorKey.Insert("\"")
        ),
        EditorKeyDef(
            "''", EditorKey.Pair("'", "'"),
            swipeUp = EditorKey.Insert("'"), swipeDown = EditorKey.Insert("'")
        ),
        EditorKeyDef(";", EditorKey.Insert(";"), popup = EditorKey.Insert(":")),
        EditorKeyDef("/", EditorKey.Insert("/"), popup = EditorKey.CommentToggle),
        EditorKeyDef("=", EditorKey.Insert("="), popup = EditorKey.Insert("==")),
        EditorKeyDef(
            "←", EditorKey.Caret(EditorKey.Caret.Move.LEFT),
            popup = EditorKey.Caret(EditorKey.Caret.Move.LINE_START)
        ),
        EditorKeyDef(
            "→", EditorKey.Caret(EditorKey.Caret.Move.RIGHT),
            popup = EditorKey.Caret(EditorKey.Caret.Move.LINE_END)
        ),
        EditorKeyDef(
            "↑", EditorKey.Caret(EditorKey.Caret.Move.UP),
            popup = EditorKey.Caret(EditorKey.Caret.Move.PAGE_UP)
        ),
        EditorKeyDef(
            "↓", EditorKey.Caret(EditorKey.Caret.Move.DOWN),
            popup = EditorKey.Caret(EditorKey.Caret.Move.PAGE_DOWN)
        )
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
            // Backticks are a pair too — template literals.
            EditorKeyDef("``", EditorKey.Pair("`", "`")),
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
     *
     * Phase 26.1: when [storedJson] (from DataStore) is valid, it replaces
     * GENERAL as the base — user-editable ordering. Invalid JSON falls back
     * silently to defaults (the single log line is in KeyStripStorage).
     */
    fun keysFor(
        language: LanguageType?,
        customSnippets: String? = null,
        storedJson: String? = null
    ): List<EditorKeyDef> {
        val base = if (!storedJson.isNullOrBlank()) {
            KeyStripStorage.deserialize(storedJson) ?: GENERAL
        } else {
            GENERAL
        }
        return base + languageTail(language) + parseCustomSnippets(customSnippets)
    }

    /** Access to the default general set for Settings reset + tests. */
    fun defaultGeneral(): List<EditorKeyDef> = GENERAL

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
            is EditorKey.Pair -> {
                val selected = text.substring(start, end)
                val body = key.open + selected + key.close
                val next = text.substring(0, start) + body + text.substring(end)
                if (selected.isEmpty()) {
                    // Empty caret: land it between the two characters.
                    TextFieldValue(next, TextRange(start + key.open.length))
                } else {
                    // Surround: keep the original text selected inside the pair.
                    TextFieldValue(
                        next,
                        TextRange(start + key.open.length, start + key.open.length + selected.length)
                    )
                }
            }
            EditorKey.Tab -> {
                val spaces = " ".repeat(tabSize.coerceIn(2, 8))
                replaced(text, start, end, spaces, spaces.length)
            }
            EditorKey.DeleteWord -> SmartTyping.deletePrevWord(value)
            EditorKey.CommentToggle -> value
            is EditorKey.Caret -> when (key.move) {
                EditorKey.Caret.Move.LEFT -> caret(text, if (start != end) start else (start - 1).coerceAtLeast(0))
                EditorKey.Caret.Move.RIGHT -> caret(text, if (start != end) end else (start + 1).coerceAtMost(text.length))
                EditorKey.Caret.Move.UP -> caret(text, columnShift(text, start, -1))
                EditorKey.Caret.Move.DOWN -> caret(text, columnShift(text, end, +1))
                EditorKey.Caret.Move.LINE_START -> caret(text, lineStart(text, start))
                EditorKey.Caret.Move.LINE_END -> caret(text, lineEnd(text, start))
                EditorKey.Caret.Move.PAGE_UP -> caret(text, pageShift(text, start, -10))
                EditorKey.Caret.Move.PAGE_DOWN -> caret(text, pageShift(text, end, 10))
                EditorKey.Caret.Move.DOC_START -> caret(text, 0)
                EditorKey.Caret.Move.DOC_END -> caret(text, text.length)
            }
        }
    }

    private fun replaced(text: String, start: Int, end: Int, insert: String, caretShift: Int): TextFieldValue {
        val next = text.substring(0, start) + insert + text.substring(end)
        return TextFieldValue(next, TextRange(start + caretShift))
    }

    private fun caret(text: String, position: Int): TextFieldValue =
        TextFieldValue(text, TextRange(position.coerceIn(0, text.length)))

    private fun lineStart(text: String, position: Int): Int {
        if (text.isEmpty()) return 0
        val pos = position.coerceIn(0, text.length)
        val idx = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0))
        return if (idx < 0) 0 else idx + 1
    }

    private fun lineEnd(text: String, position: Int): Int {
        if (text.isEmpty()) return text.length
        val pos = position.coerceIn(0, text.length)
        val idx = text.indexOf('\n', pos)
        return if (idx < 0) text.length else idx
    }

    private fun pageShift(text: String, position: Int, lines: Int): Int {
        var pos = position.coerceIn(0, text.length)
        repeat(kotlin.math.abs(lines)) {
            pos = if (lines < 0) columnShift(text, pos, -1) else columnShift(text, pos, 1)
        }
        return pos
    }

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
