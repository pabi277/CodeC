package com.codeci.ide.ui.keyboard

import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.editor.EditorKeyDef
import com.codeci.ide.ui.editor.EditorKeySet
import com.codeci.ide.ui.utils.LanguageType

/**
 * Phase 28.2 — the SHIPPED default layouts, pure code (host-tested). The
 * JSON codec [KeyboardLayoutCodec] overrides these in dev builds ("edit the
 * JSON → edit the keyboard"); the built-ins are the corruption fallback and
 * the single testable source of truth for the exit conditions, so the asset
 * files and the code can never disagree — the code IS the default.
 *
 * Letter law (spec §1.2, "Unexpected-density, gentle"): the three letter
 * rows carry the digit/symbol layer as FLICK-UPS only — `1234567890` over
 * the top row, the promised symbol set `_-=;:/.\"'(){}[]<>` distributed over
 * rows 2–3 (pairs as flick targets, closers/opener per swipe). Uppercase is
 * shift, not a layout (see [KeyboardRouter]). `;` sits on the bottom row
 * WITH its `:` popup because the exit condition names it; everything rarer
 * lives one tap away on the symbols layer.
 */
object KeyboardDefaults {

    private fun letter(c: Char, swipeUp: Char) = EditorKeyDef(
        c.toString(), EditorKey.Insert(c.toString()),
        swipeUp = EditorKey.Insert(swipeUp.toString())
    )

    private val TOP_ROW: List<EditorKeyDef> =
        "qwertyuiop".zip("1234567890") { c, d -> letter(c, d) }

    // The promised gentle set `_-=;:/."'(){}[]<>` distributed exactly:
    // row 2 gets `_ - = ; : " ' / .`, row 3 gets the pairs `< >`, the `/`
    // cap simply has no flick (zip stops at the shorter side on purpose).
    private val HOME_ROW: List<EditorKeyDef> =
        "asdfghjkl".zip("_-=;:\"'/.") { c, d -> letter(c, d) }

    private val BOTTOM_ROW: List<EditorKeyDef> =
        "zxcvbnm,./".zip("(){}[]<>") { c, d -> letter(c, d) }

    /** ⌫ is the 28.2 `EditorKey.Delete` home: tap deletes, hold repeats
     * (26.1 timers), flick-up = word delete (26.2). No popup — the hold
     * belongs to repeat, matching the strip's arrows. */
    private val DEL = EditorKeyDef(
        "⌫", EditorKey.Delete, swipeUp = EditorKey.DeleteWord
    )

    private val SPECIAL_ROW: List<EditorKeyDef> = listOf(
        EditorKeyDef(KeyboardRouter.SHIFT_CAP, EditorKey.Insert("")),
        EditorKeyDef("TAB", EditorKey.Tab, wide = true),
        EditorKeyDef("space", EditorKey.Insert(" "), wide = true),
        EditorKeyDef(";", EditorKey.Insert(";"), popup = EditorKey.Insert(":")),
        DEL,
        EditorKeyDef("⏎", EditorKey.Insert("\n"), wide = true)
    )

    /** ← → ↑ ↓ with the strip's exact 26.1 popups (Home/End/PgUp/PgDn). */
    private val UTILITY_ROW: List<EditorKeyDef> = listOf(
        EditorKeyDef(KeyboardRouter.TO_SYMBOLS_CAP, EditorKey.Insert("")),
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

    /** Dense symbol pairs with the 26.1 swipe law: flick-up = opener only,
     * flick-down = closer only, tap = pair with caret between (22.5). */
    private fun pairDef(label: String, open: String, close: String) = EditorKeyDef(
        label, EditorKey.Pair(open, close),
        swipeUp = EditorKey.Insert(open), swipeDown = EditorKey.Insert(close)
    )

    /** Symbols layer, 5×(up to 10). One tap in from letters, one tap back. */
    private val SYMBOL_ROWS: List<List<EditorKeyDef>> = listOf(
        listOf("!@#$%^&*~`").map { EditorKeyDef(it.toString(), EditorKey.Insert(it.toString())) },
        listOf(
            pairDef("()", "(", ")"), pairDef("{}", "{", "}"),
            pairDef("[]", "[", "]"), pairDef("<>", "<", ">"),
            pairDef("\"\"", "\"", "\""), pairDef("''", "'", "'"),
            pairDef("``", "`", "`"),
            EditorKeyDef("->", EditorKey.Insert("->")),
            EditorKeyDef("::", EditorKey.Insert("::")),
            EditorKeyDef("=>", EditorKey.Insert("=>"))
        ),
        listOf("+-*/=%?\\").map { EditorKeyDef(it.toString(), EditorKey.Insert(it.toString())) } +
            listOf(EditorKeyDef("==", EditorKey.Insert("=="))),
        listOf(";:.,_|").map { EditorKeyDef(it.toString(), EditorKey.Insert(it.toString())) } +
            listOf(
                EditorKeyDef("<=", EditorKey.Insert("<=")),
                EditorKeyDef(">=", EditorKey.Insert(">=")),
                EditorKeyDef("!=", EditorKey.Insert("!=")),
                EditorKeyDef("&&", EditorKey.Insert("&&"))
            ),
        listOf(
            EditorKeyDef(KeyboardRouter.TO_LETTERS_CAP, EditorKey.Insert("")),
            EditorKeyDef("TAB", EditorKey.Tab, wide = true),
            EditorKeyDef("space", EditorKey.Insert(" "), wide = true),
            DEL,
            EditorKeyDef("⏎", EditorKey.Insert("\n"), wide = true)
        )
    )

    /** The letters layer for [language]: 5 base rows + the Phase 16
     * language hook as a macro row when the language has one (C `->`,
     * Python `:` `self`, …). */
    fun codeQwerty(
        language: LanguageType?,
        rowTransform: (List<EditorKeyDef>) -> List<EditorKeyDef> = { it }
    ): KeyboardLayout {
        val macroRow = EditorKeySet.languageMacroRow(language)
        val rows = listOf(TOP_ROW, HOME_ROW, BOTTOM_ROW, SPECIAL_ROW, UTILITY_ROW)
            .map(rowTransform) +
            if (macroRow.isNotEmpty()) listOf(rowTransform(macroRow)) else emptyList()
        return KeyboardLayout.of(rows)
    }

    /** The dense symbols layer. */
    fun symbols(
        rowTransform: (List<EditorKeyDef>) -> List<EditorKeyDef> = { it }
    ): KeyboardLayout =
        KeyboardLayout.of(SYMBOL_ROWS.map(rowTransform), heightScale = 0.85f)
}
