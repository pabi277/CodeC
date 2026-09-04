package com.codeci.bench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputScriptsTest {

    @Test
    fun `burst is exactly sixty keystrokes at forty ms`() {
        val script = InputScripts.burst60()
        val keys = script.events.filterIsInstance<ScriptEvent.TypeChar>()
        assertEquals(60, keys.size)
        assertTrue(keys.zipWithNext().all { (a, b) -> b.atMs - a.atMs == InputScripts.BURST_INTERVAL_MS })
        assertTrue(keys.all { !it.c.isWhitespace() || it.c == ' ' })
        assertTrue(keys.none { it.c == '\n' })
    }

    @Test
    fun `completion churn is slower than the completion debounce`() {
        val script = InputScripts.completionChurn()
        val keys = script.events.filterIsInstance<ScriptEvent.TypeChar>()
        assertTrue(keys.size in 8..32)
        assertTrue(keys.zipWithNext().all { (a, b) -> b.atMs - a.atMs >= InputScripts.CHURN_INTERVAL_MS })
        // 220 ms cadence is well past the app's 120 ms completion debounce.
        assertTrue(InputScripts.CHURN_INTERVAL_MS > 120L)
    }

    @Test
    fun `fling path accelerates and then lifts`() {
        val script = InputScripts.fling()
        val touches = script.events.filterIsInstance<ScriptEvent.Touch>()
        assertEquals(TouchAction.DOWN, touches.first().action)
        assertEquals(TouchAction.UP, touches.last().action)
        val moves = touches.drop(1).dropLast(1)
        assertTrue(moves.size >= 10)
        // Path goes UP the screen (y decreases) — content scrolls down.
        assertTrue(touches.last().point.yFrac < touches.first().point.yFrac)
        // Inter-move gaps grow (acceleration) so the velocity tracker fires.
        val gaps = moves.zipWithNext { a, b -> b.atMs - a.atMs }
        assertTrue(gaps.last() >= gaps.first())
    }

    @Test
    fun `caret drag holds the long press then wiggles at the bottom`() {
        val script = InputScripts.caretDrag()
        val touches = script.events.filterIsInstance<ScriptEvent.Touch>()
        val down = touches.first()
        val firstMove = touches.first { it.action == TouchAction.MOVE }
        // Long-press first: the first MOVE comes after LONG_PRESS_MS.
        assertTrue(firstMove.atMs - down.atMs >= InputScripts.LONG_PRESS_MS)
        // Final position is near the bottom edge (the auto-scroll driver).
        assertTrue(touches.last().point.yFrac >= 0.8f)
        // The wiggle phase exists (several near-identical positions at 0.85).
        val wiggle = touches.filter { it.point.yFrac >= 0.85f }
        assertTrue(wiggle.size >= 10)
    }

    @Test
    fun `all scripts are non-empty and timed`() {
        for (script in listOf(InputScripts.burst60(), InputScripts.fling(), InputScripts.caretDrag(), InputScripts.completionChurn())) {
            assertTrue(script.events.isNotEmpty())
            assertTrue(script.durationMs > script.events.last().atMs)
            assertNotNull(script.name)
        }
    }
}
