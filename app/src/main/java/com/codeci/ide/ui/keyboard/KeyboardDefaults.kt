package com.codeci.ide.ui.keyboard

import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.editor.EditorKeyDef

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

    /**
     * ← → ↑ ↓: tap moves, hold repeats (26.1), and the navigation the strip
     * gives as POPUPS rides FLICKS here — on this grid the 150 ms repeat
     * always beats a 300 ms popup, so a popup-only Home would be unreachable
     * (the strip has the same conflict; the keyboard resolves it with data).
     */
    private val UTILITY_ROW: List<EditorKeyDef> = listOf(
        EditorKeyDef(KeyboardRouter.TO_SYMBOLS_CAP, EditorKey.Insert("")),
        EditorKeyDef(
            "←", EditorKey.Caret(EditorKey.Caret.Move.LEFT),
            swipeUp = EditorKey.Caret(EditorKey.Caret.Move.LINE_START)
        ),
        EditorKeyDef(
            "→", EditorKey.Caret(EditorKey.Caret.Move.RIGHT),
            swipeUp = EditorKey.Caret(EditorKey.Caret.Move.LINE_END)
        ),
        EditorKeyDef(
            "↑", EditorKey.Caret(EditorKey.Caret.Move.UP),
            swipeUp = EditorKey.Caret(EditorKey.Caret.Move.PAGE_UP)
        ),
        EditorKeyDef(
            "↓", EditorKey.Caret(EditorKey.Caret.Move.DOWN),
            swipeUp = EditorKey.Caret(EditorKey.Caret.Move.PAGE_DOWN)
        )
    )

    private fun one(c: Char) = EditorKeyDef(c.toString(), EditorKey.Insert(c.toString()))

    /**
     * Symbols layer — ONE KEY PER BUTTON (owner, round 3: "many keys in one
     * touch … make one key per button"). The multi-char caps (`()`, `->`,
     * `==`, `<=`…) are GONE: every cap inserts exactly one character; `->`
     * is `-` then `>`, `==` is `=` twice, and the brackets/quotes live as
     * their own keys so nothing hides behind a pair. Three full 10-wide
     * rows + the specials row; the letters layer's flick set stays as-is.
     *
     * (The 22.5 pair-with-caret behavior stays where it always was — the
     * extra-keys strip when CodeC Keys is off, and the dev layout JSON can
     * still express `pair` caps for anyone who wants them.)
     */
    private val SYMBOL_ROWS: List<List<EditorKeyDef>> = listOf(
        "!@#$%^&*~`".map { one(it) },
        "-=+_|\\/<>?".map { one(it) },
        "[]{}()'\".,".map { one(it) },
        listOf(
            EditorKeyDef(KeyboardRouter.TO_LETTERS_CAP, EditorKey.Insert("")),
            EditorKeyDef(":", EditorKey.Insert(":")),
            EditorKeyDef(";", EditorKey.Insert(";")),
            EditorKeyDef("TAB", EditorKey.Tab, wide = true),
            EditorKeyDef("space", EditorKey.Insert(" "), wide = true),
            DEL,
            EditorKeyDef("⏎", EditorKey.Insert("\n"), wide = true)
        )
    )

    /**
     * The letters layer — five rows, language-INDEPENDENT since round 3
     * (owner: one key per button; the multi-char tail caps — C's `->`,
     * Python's `_(self)` — are exactly what round 3 deleted; every one of
     * their characters is a single tap away on the SYM layer).
     */
    fun codeQwerty(
        rowTransform: (List<EditorKeyDef>) -> List<EditorKeyDef> = { it }
    ): KeyboardLayout {
        val rows = listOf(TOP_ROW, HOME_ROW, BOTTOM_ROW, SPECIAL_ROW, UTILITY_ROW)
            .map(rowTransform)
        return KeyboardLayout.of(rows)
    }

    /** The dense symbols layer. */
    fun symbols(
        rowTransform: (List<EditorKeyDef>) -> List<EditorKeyDef> = { it }
    ): KeyboardLayout =
        KeyboardLayout.of(SYMBOL_ROWS.map(rowTransform), heightScale = 0.85f)
}
