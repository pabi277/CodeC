package com.codeci.bench.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28.1 — the scripted bursts: exact press counts, strictly advancing
 * timeline, all labels resolvable against the grid, and expected-text derived
 * from the SAME pure model the presses run through (the device must end the
 * burst with exactly this text tail — the oracle is the model, never a
 * hand-typed string that could drift).
 */
class KeysSpikeScriptsTest {

    @Test fun `type burst64 presses exactly 64 keys on real caps`() {
        val s = KeysSpikeScripts.typeBurst64()
        assertEquals("type_burst64", s.name)
        assertEquals(64, s.events.size)
        for (e in s.events) {
            assertTrue("cap '${e.label}' must exist in the grid", CodecKeyGrid.find(e.label) != null)
        }
    }

    @Test fun `type burst64 timeline is monotone at 40ms cadence`() {
        val s = KeysSpikeScripts.typeBurst64()
        var prev = 0L
        for (e in s.events) {
            assertTrue("events must advance", e.atMs > prev)
            prev = e.atMs
        }
        assertEquals(40L, s.events[1].atMs - s.events[0].atMs)
    }

    @Test fun `type burst64 expected text is the exact model fold`() {
        val s = KeysSpikeScripts.typeBurst64()
        // Spelled out from the script by hand: 64 presses, the "xyz" typo is
        // erased by 3 DELs, "value" is retyped — TAB inserts 4 spaces.
        val expected = "include cstdlib\n\nint main\n\n    return value\n    value valuevalue"
        assertEquals(expected, s.expectedText())
        assertEquals(64, expected.length)
    }

    @Test fun `hold repeat30 presses 30 groups and commits 40 times`() {
        val s = KeysSpikeScripts.holdRepeat30()
        assertEquals(40, s.events.size)
        assertEquals(6, s.events.count { it.label == "DEL" })
        assertEquals(11, s.events.count { it.label == "space" })
        assertEquals(30, s.expectedPresses())
    }

    /**
     * 40 commits in 30 presses: the two held caps carry 5 extra commits each.
     * Press groups = maximal runs of identical labels (no legitimate tap in
     * this script repeats a label back-to-back — that is precisely why only
     * a HOLD produces label runs).
     */
    private fun GridScript.expectedPresses(): Int {
        var presses = 0
        var i = 0
        while (i < events.size) {
            presses++
            val label = events[i].label
            i++
            while (i < events.size && events[i].label == label) i++
        }
        return presses
    }

    @Test fun `hold repeat30 DEL burst deletes exactly its six chars`() {
        val s = KeysSpikeScripts.holdRepeat30()
        val full = CodecKeyGrid.expectedText(s.events.map { CodecKeyGrid.find(it.label)!! })
        // After the typo trio "xyz" the tail is "...for xyz"; six DELs erase
        // "z y x ␣ r o" leaving "...sum      f"; then "for " is retyped.
        assertTrue(full.endsWith("ffor "))
        assertEquals("int main void sum      ffor ", full)
        val withoutDel = CodecKeyGrid.expectedText(s.events.filterNot { it.label == "DEL" }.map {
            CodecKeyGrid.find(it.label)!!
        })
        assertEquals(full.length + 6, withoutDel.length)
    }

    @Test fun `run row check presses twelve keys and the expected buffer is exact`() {
        val s = KeysSpikeScripts.runRowCheck()
        assertEquals(12, s.events.size)
        // "run stdin x" + one DEL → "run stdin "
        assertEquals("run stdin ", s.expectedRunRowText())
    }

    private fun GridScript.expectedRunRowText(): String {
        val buffer = StringBuilder()
        for (e in events) {
            val cap = CodecKeyGrid.find(e.label) ?: error("unknown cap ${e.label}")
            RunRowEdit.apply(buffer, cap)
        }
        return buffer.toString()
    }
}
