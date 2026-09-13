package com.codeci.ide

import com.codeci.ide.ui.editor.CaretVisibilityPolicy
import com.codeci.ide.ui.editor.EditorViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 48 — the rescroll decision (PART_48_1 §Tests). The policy is pure,
 * so these walk the real decision surface: which of the five chrome triggers
 * owes a rescroll (each in both directions), what must never trigger (the
 * narrowness pin — the owner's exit 6: scrolled away from the caret, the
 * keyboard opens, the view must NOT yank back), and the debounce split.
 */
class CaretVisibilityPolicyTest {

    private fun viewport(
        heightPx: Int = 1000,
        imeVisible: Boolean = false,
        codecKeysVisible: Boolean = false,
        stripVisible: Boolean = true,
        outputExpanded: Boolean = false,
        statusVisible: Boolean = true,
        fontSizeSp: Float = 14f
    ) = EditorViewport(
        heightPx = heightPx,
        imeVisible = imeVisible,
        codecKeysVisible = codecKeysVisible,
        stripVisible = stripVisible,
        outputExpanded = outputExpanded,
        statusVisible = statusVisible,
        fontSizeSp = fontSizeSp
    )

    // ---- the first observation ----

    @Test
    fun `null previous never owes - sora placed the caret on open`() {
        // Phase 35.4's quiet-on-open state: a forced scroll on open is the
        // exact behaviour this policy must not reintroduce.
        assertFalse(CaretVisibilityPolicy.owesRescroll(null, viewport(heightPx = 100)))
        assertFalse(CaretVisibilityPolicy.owesRescroll(null, viewport(heightPx = 1)))
    }

    // ---- the five triggers, both directions ----

    @Test
    fun `each of the five chrome triggers owes a rescroll via its height change`() {
        // Trigger 1 — IME appears (the owner's "last line go under the
        // keyboard"): the imePadding()'d box shrinks.
        val settled = viewport(heightPx = 1000, imeVisible = false)
        val imeUp = viewport(heightPx = 560, imeVisible = true)
        assertTrue(CaretVisibilityPolicy.owesRescroll(settled, imeUp))
        // …and disappears again.
        assertTrue(CaretVisibilityPolicy.owesRescroll(imeUp, settled))

        // Trigger 2/3 — CodeC Keys appears and the keys/strip row toggles.
        val keysUp = viewport(heightPx = 620, codecKeysVisible = true)
        assertTrue(CaretVisibilityPolicy.owesRescroll(settled, keysUp))

        // Trigger 4 — the output panel expands (draggable height).
        val panelUp = viewport(heightPx = 500, outputExpanded = true)
        assertTrue(CaretVisibilityPolicy.owesRescroll(settled, panelUp))

        // Trigger 5 — the status bar yields/returns (one whole row).
        val statusGone = viewport(heightPx = 978, statusVisible = false)
        assertTrue(CaretVisibilityPolicy.owesRescroll(settled, statusGone))

        // A 1 px change is still a change (the IME animation's last frame).
        assertTrue(
            CaretVisibilityPolicy.owesRescroll(
                viewport(heightPx = 1000),
                viewport(heightPx = 999)
            )
        )
        assertTrue(
            CaretVisibilityPolicy.owesRescroll(
                viewport(heightPx = 1000),
                viewport(heightPx = 1001)
            )
        )
    }

    @Test
    fun `same height and font owe nothing - narrowness pin`() {
        // The owner's exit 6: every chrome boolean flips while the box
        // height stays constant (a row swapped for a row) — the caret is
        // where it was, and a rescroll here is a scroll fight with the
        // user's hand.
        val from = viewport(
            imeVisible = false, codecKeysVisible = false, stripVisible = true,
            outputExpanded = false, statusVisible = true
        )
        val to = viewport(
            imeVisible = true, codecKeysVisible = false, stripVisible = false,
            outputExpanded = false, statusVisible = false
        )
        assertEquals(from.heightPx, to.heightPx)
        assertFalse(CaretVisibilityPolicy.owesRescroll(from, to))
    }

    @Test
    fun `font size change at constant height owes a rescroll`() {
        // Reflow moves every line under a constant box.
        assertTrue(
            CaretVisibilityPolicy.owesRescroll(
                viewport(fontSizeSp = 14f),
                viewport(fontSizeSp = 18f)
            )
        )
    }

    @Test
    fun `identical viewports owe nothing`() {
        val v = viewport()
        assertFalse(CaretVisibilityPolicy.owesRescroll(v, v))
    }

    // ---- the debounce split ----

    @Test
    fun `a shrinking box is urgent - zero debounce`() {
        // The keyboard is rising over the caret: perform the rescroll on
        // the next layout pass, no coalescing window.
        assertEquals(
            0L,
            CaretVisibilityPolicy.debounceMs(
                viewport(heightPx = 1000),
                viewport(heightPx = 560)
            )
        )
    }

    @Test
    fun `a growing box waits the coalescing window`() {
        // The keyboard is closing: the animation reports many intermediate
        // sizes; the edge collapses them into one call.
        assertEquals(
            CaretVisibilityPolicy.RESCROLL_DEBOUNCE_MS,
            CaretVisibilityPolicy.debounceMs(
                viewport(heightPx = 560),
                viewport(heightPx = 1000)
            )
        )
    }

    @Test
    fun `debounce is total - constant box and null previous`() {
        assertEquals(
            CaretVisibilityPolicy.RESCROLL_DEBOUNCE_MS,
            CaretVisibilityPolicy.debounceMs(viewport(), viewport())
        )
        assertEquals(
            CaretVisibilityPolicy.RESCROLL_DEBOUNCE_MS,
            CaretVisibilityPolicy.debounceMs(null, viewport())
        )
    }

    @Test
    fun `the coalescing window is a small constant`() {
        // One IME animation gets ONE coalesced call, not 30 — but not a
        // wait a user could ever perceive as lag for the urgent direction
        // (that one is 0 ms anyway). Pinned so a "tweak" cannot make the
        // growing direction seasick.
        assertEquals(90L, CaretVisibilityPolicy.RESCROLL_DEBOUNCE_MS)
    }

    @Test
    fun `one line of air is the margin`() {
        assertEquals(1, CaretVisibilityPolicy.KEEP_LINES_BELOW)
    }
}
