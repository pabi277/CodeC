package com.codeci.ide

import com.codeci.ide.ui.stats.StreakFacts
import com.codeci.ide.ui.stats.StreakLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakLineTest {
    @Test fun `about formats the stored numbers`() {
        assertEquals(
            "3 days in a row · 41 runs · 12 files created",
            StreakLine.forAbout(StreakFacts(streak = 3, runs = 41, files = 12)),
        )
    }

    @Test fun `about pluralises singular values`() {
        assertEquals(
            "1 day in a row · 1 run · 1 file created",
            StreakLine.forAbout(StreakFacts(streak = 1, runs = 1, files = 1)),
        )
    }

    @Test fun `zero progress is absent`() {
        assertNull(StreakLine.forAbout(StreakFacts(streak = 0, runs = 0, files = 0)))
    }

    @Test fun `hub line is only for a continuing streak`() {
        assertNull(StreakLine.forHub(StreakFacts(1, 1, 1)))
        assertNull(StreakLine.forHub(StreakFacts(3, 3, 1, streakBroken = true)))
        assertEquals("Day 3 with CodeC.", StreakLine.forHub(StreakFacts(3, 3, 1)))
    }

    @Test fun `broken streak is never named`() {
        val about = StreakLine.forAbout(StreakFacts(1, 7, 2, streakBroken = true))
        assertFalse(about.orEmpty().contains("broken", ignoreCase = true))
        assertTrue(StreakLine.forHub(StreakFacts(1, 7, 2, streakBroken = true)) == null)
    }
}
