package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 69.3 — the wiring pins for the owner's second report (2026-09-28,
 * verbatim): *"The quick keys are sensitive even i want to drag for other
 * keys it's types which ever i am scrolling"*.
 *
 * The arithmetic lives in `KeyGestureDetectorTest` (`isScrollDx`); this file
 * pins the part a unit test cannot see — that every cap row that SCROLLS
 * actually asks that rule, that the distance is the row's own touch slop
 * rather than a number invented per row, that the arrows' hold-repeat cannot
 * fire into a scroll, and that the finger's last position (the up, which a
 * flick may deliver without any move at all) is measured too.
 *
 * Everything here is read from the real sources, so a later phase cannot
 * quietly restore a "20 dp" of its own and bring the report back.
 */
class KeysScrollCancelWiringTest {

    private val keysRow = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/components/EditorKeysRow.kt"
    ).readText()

    private val strip = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/components/SuggestionStrip.kt"
    ).readText()

    private fun code(source: String): String = RepoFiles.codeOnly(source)

    @Test
    fun `the row's own touch slop decides, never a number of our own`() {
        val slop = "with(density) { LocalViewConfiguration.current.touchSlop.toPx() }"
        assertTrue(
            "the keys row must read the slop the row scrolls with",
            code(keysRow).contains(slop)
        )
        assertTrue(
            "the suggestion chips scroll for the same reason and get the same rule",
            code(strip).contains(slop)
        )
        assertFalse(
            "the 20 dp scroll threshold is the bug itself — a drag between the " +
                "slop and 20 dp scrolled the row AND typed the cap",
            code(keysRow).contains("20.dp.toPx()")
        )
        assertFalse(
            "the chips row must not keep a threshold of its own either",
            code(strip).contains("20.dp.toPx()")
        )
    }

    @Test
    fun `every scrolling cap asks the one pure rule`() {
        // Two caps in the keys row (a keycap and a run key) and one chip in
        // the suggestion strip: three rows that scroll, three callers, one
        // rule — so the distance can never drift between them.
        assertEquals(
            "EditorKeysRow must ask isScrollDx for both of its caps",
            2,
            Regex(Regex.escape("KeyGestureDetector.isScrollDx(")).findAll(code(keysRow)).count()
        )
        assertEquals(
            "the suggestion chip must ask the same rule",
            1,
            Regex(Regex.escape("KeyGestureDetector.isScrollDx(")).findAll(code(strip)).count()
        )
    }

    @Test
    fun `the arrows never repeat into a scroll`() {
        // The arrows are the caps a thumb drags FROM: hold-repeat starts at
        // 150 ms, so a slow scroll used to move the caret before the drag had
        // travelled far enough to cancel anything.
        assertTrue(
            "the hold-repeat step must be guarded by the scroll decision",
            code(keysRow).contains("if (!isScroll) onKey(def.key)")
        )
        assertTrue(
            "the arrows are still the caps that repeat (the guard is on the step)",
            code(keysRow).contains("while (true) {")
        )
    }

    @Test
    fun `the up is part of the gesture - a flick is measured where it ends`() {
        // A flick can lift the finger without a move event ever landing, and
        // the old loop threw that change away: the tap was decided on the last
        // MOVE, not on where the touch really ended.
        for ((name, source) in listOf("EditorKeysRow" to keysRow, "SuggestionStrip" to strip)) {
            val body = code(source)
            val dx = body.indexOf("val dx = change.position.x - startX")
            val up = body.indexOf("if (!change.pressed) finished = true")
            assertTrue("$name must measure the change", dx > 0)
            assertTrue("$name must end the gesture on the up", up > 0)
            assertTrue(
                "$name must measure the up BEFORE deciding the touch is finished",
                dx < up
            )
        }
    }
}
