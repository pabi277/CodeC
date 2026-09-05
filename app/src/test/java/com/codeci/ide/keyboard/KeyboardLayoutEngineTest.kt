package com.codeci.ide.keyboard

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.editor.EditorKeyDef
import com.codeci.ide.ui.editor.EditorKeySet
import com.codeci.ide.ui.editor.KeyStripStorage
import com.codeci.ide.ui.keyboard.CapAction
import com.codeci.ide.ui.keyboard.KeyboardDefaults
import com.codeci.ide.ui.keyboard.KeyboardLayout
import com.codeci.ide.ui.keyboard.KeyboardLayers
import com.codeci.ide.ui.keyboard.KeyboardLayoutCodec
import com.codeci.ide.ui.keyboard.KeyboardRouter
import com.codeci.ide.ui.keyboard.ShiftState
import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28.2 — the layout engine's laws, pinned on the host: JSON codec
 * (round-trip, corruption fallback, per-row drop, weight/repeat derivation),
 * the router (shift law, special caps, layer cycling, popup/swipe routing),
 * the shipped defaults (the exit conditions' claims about what exists), and
 * `EditorKey.Delete` — the backspace home the 28.1 spike promised.
 */
class KeyboardLayoutEngineTest {

    // ---- codec ------------------------------------------------------------

    @Test
    fun builtInLayoutsRoundTripThroughTheCodec() {
        for (layout in listOf(KeyboardDefaults.codeQwerty(LanguageType.C), KeyboardDefaults.symbols())) {
            val json = KeyboardLayoutCodec.serialize(layout)
            val back = KeyboardLayoutCodec.deserialize(json)
            assertNotNull("round trip must parse: $json", back)
            assertEquals(layout.rows.map { it.size }, back!!.rows.map { it.size })
            assertEquals(layout.heightScale, back.heightScale, 0.0001f)
            val a = layout.allCaps(); val b = back.allCaps()
            for (i in a.indices) {
                assertEquals(a[i].def.label, b[i].def.label)
                assertEquals(a[i].def.key, b[i].def.key)
                assertEquals(a[i].def.popup, b[i].def.popup)
                assertEquals(a[i].def.swipeUp, b[i].def.swipeUp)
                assertEquals(a[i].def.swipeDown, b[i].def.swipeDown)
                assertEquals(a[i].widthWeight, b[i].widthWeight, 0.0001f)
                assertEquals(a[i].repeat, b[i].repeat)
            }
        }
    }

    @Test
    fun corruptFilesFallBackAndGoodOnesDoNot() {
        assertNull(KeyboardLayoutCodec.deserialize(null))
        assertNull(KeyboardLayoutCodec.deserialize(""))
        assertNull(KeyboardLayoutCodec.deserialize("nonsense"))
        assertNull(KeyboardLayoutCodec.deserialize("{\"rows\":[[ ]]}"))
        assertNull(KeyboardLayoutCodec.deserialize("{\"heightScale\":1.0}"))
        assertNotNull(KeyboardLayoutCodec.deserialize(KeyboardLayoutCodec.serialize(KeyboardDefaults.symbols())))
    }

    @Test
    fun aBadRowDropsItselfNotTheKeyboard() {
        val good = "[{\"label\":\"q\",\"type\":\"insert\",\"text\":\"q\"}]"
        val json = "{\"heightScale\":1.0,\"rows\":[[{\"label\":\"x\",\"type\":\"wat\"}],$good]}"
        val layout = KeyboardLayoutCodec.deserialize(json)
        assertNotNull(layout)
        assertEquals(1, layout!!.rows.size)
        assertEquals("q", layout.rows[0].single().def.label)
    }

    @Test
    fun missingWeightAndRepeatDeriveFromTheModelRules() {
        val space = "[{\"label\":\"space\",\"type\":\"insert\",\"text\":\" \",\"wide\":true}]"
        val layout = KeyboardLayoutCodec.deserialize("{\"heightScale\":1.0,\"rows\":[$space]}")!!
        val cap = layout.rows.single().single()
        assertEquals(5f, cap.widthWeight, 0.0001f)
        assertFalse(cap.repeat)

        val del = "[{\"label\":\"⌫\",\"type\":\"delete\"}]"
        val d = KeyboardLayoutCodec.deserialize("{\"heightScale\":1.0,\"rows\":[$del]}")!!
            .rows.single().single()
        assertEquals(1.6f, d.widthWeight, 0.0001f)
        assertTrue("⌫ carries the 26.1 hold-repeat", d.repeat)
    }

    @Test
    fun heightScaleIsClampedAndRowBudgetRespected() {
        val row = "[{\"label\":\"a\",\"type\":\"insert\",\"text\":\"a\"}]"
        val huge = KeyboardLayoutCodec.deserialize("{\"heightScale\":9,\"rows\":[$row]}")!!
        assertEquals(KeyboardLayout.HEIGHT_SCALE_MAX, huge.heightScaleClamped, 0.0001f)
        val tooManyRows = (1..9).joinToString(",") { row }
        assertNull(KeyboardLayoutCodec.deserialize("{\"rows\":[$tooManyRows]}"))
    }

    // ---- router -----------------------------------------------------------

    @Test
    fun shiftUppercasesSingleAsciiLettersOnly() {
        val r = KeyboardRouter
        assertEquals(EditorKey.Insert("A"), r.resolveEdit(EditorKey.Insert("a"), ShiftState.ONCE))
        assertEquals(EditorKey.Insert("Z"), r.resolveEdit(EditorKey.Insert("z"), ShiftState.LOCKED))
        // untouched:
        assertEquals(EditorKey.Insert("("), r.resolveEdit(EditorKey.Insert("("), ShiftState.ONCE))
        assertEquals(EditorKey.Insert("ab"), r.resolveEdit(EditorKey.Insert("ab"), ShiftState.ONCE))
        assertEquals(EditorKey.Tab, r.resolveEdit(EditorKey.Tab, ShiftState.ONCE))
        assertEquals(EditorKey.Delete, r.resolveEdit(EditorKey.Delete, ShiftState.LOCKED))
        assertEquals(EditorKey.Insert("1"), r.resolveEdit(EditorKey.Insert("1"), ShiftState.ONCE))
    }

    @Test
    fun displayLabelFollowsTheShiftLaw() {
        val a = EditorKeyDef("a", EditorKey.Insert("a"))
        assertEquals("A", KeyboardRouter.displayLabel(a, ShiftState.LOCKED))
        assertEquals("a", KeyboardRouter.displayLabel(a, ShiftState.OFF))
        assertEquals(
            "TAB ▸",
            KeyboardRouter.displayLabel(EditorKeyDef("TAB ▸", EditorKey.GhostAccept), ShiftState.ONCE)
        )
    }

    @Test
    fun specialCapsRouteBeforeEdits() {
        fun cap(label: String) = EditorKeyDef(label, EditorKey.Insert(""))
        assertEquals(CapAction.ToggleShift, KeyboardRouter.tapAction(cap("⬆"), ShiftState.OFF))
        assertEquals(CapAction.ToggleLock, KeyboardRouter.popupAction(cap("⬆")))
        assertEquals(CapAction.SetLayer(KeyboardLayers.SYMBOLS), KeyboardRouter.tapAction(cap("SYM"), ShiftState.OFF))
        assertEquals(CapAction.SetLayer(KeyboardLayers.LETTERS), KeyboardRouter.tapAction(cap("ABC"), ShiftState.LOCKED))
        // no-ops keep a special from ever writing to the doc — and a stray
        // long-press on SYM must NOT re-fire the layer switch
        assertEquals(CapAction.Noop, KeyboardRouter.popupAction(cap("SYM")))
        assertEquals(CapAction.Noop, KeyboardRouter.swipeAction(cap("SYM"), up = true))
        // an ordinary cap without a popup long-presses its own key (strip law)
        val plain = EditorKeyDef("k", EditorKey.Insert("k"))
        assertEquals(CapAction.Edit(EditorKey.Insert("k")), KeyboardRouter.popupAction(plain))
    }

    @Test
    fun onceShiftDiesOnEditAndOnlyOnEdits() {
        assertEquals(ShiftState.OFF, KeyboardRouter.shiftAfterAction(ShiftState.ONCE, CapAction.Edit(EditorKey.Tab)))
        assertEquals(ShiftState.LOCKED, KeyboardRouter.shiftAfterAction(ShiftState.LOCKED, CapAction.Edit(EditorKey.Tab)))
        assertEquals(ShiftState.ONCE, KeyboardRouter.shiftAfterAction(ShiftState.ONCE, CapAction.SetLayer(0)))
        assertEquals(ShiftState.ONCE, KeyboardRouter.toggleShift(ShiftState.OFF))
        assertEquals(ShiftState.OFF, KeyboardRouter.toggleShift(ShiftState.ONCE))
        assertEquals(ShiftState.LOCKED, KeyboardRouter.toggleLock(ShiftState.OFF))
        assertEquals(ShiftState.OFF, KeyboardRouter.toggleLock(ShiftState.LOCKED))
    }

    @Test
    fun popupAndSwipeCarryTheirOwnKeysVerbatim() {
        val semis = EditorKeyDef(";", EditorKey.Insert(";"), popup = EditorKey.Insert(":"))
        assertEquals(CapAction.Edit(EditorKey.Insert(":")), KeyboardRouter.popupAction(semis))
        val pair = EditorKeyDef(
            "()", EditorKey.Pair("(", ")"),
            swipeUp = EditorKey.Insert("("), swipeDown = EditorKey.Insert(")")
        )
        assertEquals(CapAction.Edit(EditorKey.Insert("(")), KeyboardRouter.swipeAction(pair, up = true))
        assertEquals(CapAction.Edit(EditorKey.Insert(")")), KeyboardRouter.swipeAction(pair, up = false))
        assertEquals(CapAction.Noop, KeyboardRouter.swipeAction(semis, up = false))
        // exit condition 2 (device): flick-down on '()' inserts the CLOSER only
        val applied = EditorKeySet.apply(
            (KeyboardRouter.swipeAction(pair, up = false) as CapAction.Edit).key,
            TextFieldValue("x", TextRange(1))
        )
        assertEquals("x)", applied.text)
    }

    @Test
    fun popupLabelsAreStableForTheBubble() {
        assertEquals(":", KeyboardRouter.popupLabel(EditorKey.Insert(":")))
        assertEquals("()", KeyboardRouter.popupLabel(EditorKey.Pair("(", ")")))
        assertEquals("Home", KeyboardRouter.popupLabel(EditorKey.Caret(EditorKey.Caret.Move.LINE_START)))
        assertEquals("⌫W", KeyboardRouter.popupLabel(EditorKey.DeleteWord))
        assertEquals("TAB", KeyboardRouter.popupLabel(EditorKey.Tab))
    }

    // ---- shipped defaults (the layout engine's own exit claims) ----------

    @Test
    fun codeQwertyCoversEveryLetterOnceWithDigitAndSymbolFlicks() {
        val caps = KeyboardDefaults.codeQwerty(null).allCaps()
        for (c in 'a'..'z') {
            val hits = caps.filter { it.def.label == c.toString() }
            assertEquals("letter $c exactly once", 1, hits.size)
        }
        // digits via flick-up of the top row, in order (exit condition 2:
        // swipe-up on 'p' yields '0'-style digit per JSON)
        val top = KeyboardDefaults.codeQwerty(null).rows[0]
        assertEquals("qwertyuiop".map { EditorKey.Insert(it.toString()) }, top.map { it.def.key })
        assertEquals("1234567890".map { EditorKey.Insert(it.toString()) }, top.map { it.def.swipeUp })
        // the promised gentle symbol set distributed over rows 2–3
        val flicks = KeyboardDefaults.codeQwerty(null).rows[1].mapNotNull { it.def.swipeUp } +
            KeyboardDefaults.codeQwerty(null).rows[2].mapNotNull { it.def.swipeUp }
        val texts = flicks.filterIsInstance<EditorKey.Insert>().map { it.text }.toSet()
        for (symbol in "_-=;:.\"'(){}<>") {
            assertTrue("gentle-set symbol $symbol reachable by flick", texts.contains(symbol.toString()))
        }
        // bottom specials exist
        val labels = KeyboardDefaults.codeQwerty(null).rows[3].map { it.def.label }
        assertTrue(labels.containsAll(listOf("⬆", "TAB", "space", ";", "⌫", "⏎")))
    }

    @Test
    fun theSemiColonPairLawHoldsSomewhereReachable() {
        // exit condition 2's `:` must be ONE GESTURE from `;` wherever it
        // lives: today the strip and the grid share the model, and the
        // home row's flick carries `;`→…; the dedicated `;`+popup cap is a
        // layout edit away (JSON), which is the point of 28.2.
        val flicks = KeyboardDefaults.codeQwerty(null).allCaps()
            .mapNotNull { it.def.swipeUp }.filterIsInstance<EditorKey.Insert>().map { it.text }
        assertTrue(flicks.contains(";"))
        assertTrue(flicks.contains(":"))
    }

    @Test
    fun delHoldsRepeatsAndFlicksToWordDelete() {
        val del = KeyboardDefaults.codeQwerty(null).allCaps().first { it.def.label == "⌫" }
        assertTrue("⌫ hold-repeats (26.1 timers)", del.repeat)
        assertEquals(EditorKey.DeleteWord, del.def.swipeUp)
        assertEquals(null, del.def.popup)
    }

    @Test
    fun languageMacroRowIsThePhase16Hook() {
        val c = KeyboardDefaults.codeQwerty(LanguageType.C)
        val py = KeyboardDefaults.codeQwerty(LanguageType.PYTHON)
        assertEquals("C ships the -> row", "->", c.rows.last().first().def.label)
        assertEquals(
            "Python ships the Phase 16 tail verbatim (: and _(self))",
            listOf(":", "_(self)"),
            py.rows.last().map { it.def.label }
        )
        // languages without a tail keep 5 rows
        assertEquals(5, KeyboardDefaults.codeQwerty(LanguageType.MARKDOWN).rows.size)
    }

    @Test
    fun symbolsLayerTogglesBackAndCarriesPairLaws() {
        val sym = KeyboardDefaults.symbols()
        assertEquals(5, sym.rows.size)
        assertTrue(sym.allCaps().any { it.def.label == "ABC" })
        val parens = sym.allCaps().first { it.def.label == "()" }
        assertEquals(EditorKey.Pair("(", ")"), parens.def.key)
        assertEquals(EditorKey.Insert("("), parens.def.swipeUp)
        assertEquals(EditorKey.Insert(")"), parens.def.swipeDown)
        // one-tap back: the ABC cap routes to the letters layer
        assertEquals(
            CapAction.SetLayer(KeyboardLayers.LETTERS),
            KeyboardRouter.tapAction(EditorKeyDef("ABC", EditorKey.Insert("")), ShiftState.OFF)
        )
    }

    // ---- the Delete model home (28.1's promised follow-up) ---------------

    @Test
    fun deleteKeyIsBackspaceMath() {
        val noSel = EditorKeySet.apply(EditorKey.Delete, TextFieldValue("abc", TextRange(2)))
        assertEquals("ac", noSel.text)
        assertEquals(1, noSel.selection.start)
        val sel = EditorKeySet.apply(EditorKey.Delete, TextFieldValue("abcd", TextRange(1, 3)))
        assertEquals("ad", sel.text)
        assertEquals(1, sel.selection.start)
        val empty = EditorKeySet.apply(EditorKey.Delete, TextFieldValue("", TextRange(0)))
        assertEquals("", empty.text)
    }

    @Test
    fun deleteRoundTripsThroughTheStripStorageSchema() {
        val def = EditorKeyDef("⌫", EditorKey.Delete)
        val json = KeyStripStorage.serialize(listOf(def))
        val back = KeyStripStorage.deserialize(json)
        assertEquals(listOf(def), back)
    }

    @Test
    fun ghostMoodTransformReachesTheKeyboardRows() {
        // 27.1 law: while the ghost is visible, TAB caps read "TAB ▸" and
        // first-refuse the press. The keyboard applies the SAME transform.
        val layout = KeyboardDefaults.codeQwerty(LanguageType.C) { row ->
            EditorKeySet.keysWithGhostMood(row, com.codeci.ide.ui.editor.CompletionSurface.STRIP)
        }
        val tab = layout.rows[3].first { it.def.label.startsWith("TAB") }
        assertEquals(EditorKey.GhostAccept, tab.def.key)
        assertEquals(EditorKey.Tab, tab.def.popup) // long-press = raw indent
    }
}
