package com.codeci.ide

import com.codeci.ide.ui.projects.HubListBranch
import com.codeci.ide.ui.projects.HubListFacts
import com.codeci.ide.ui.projects.HubListPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.3 — a shimmer that changes the list's length while the user is
 * scrolling breaks the scroll position (the Phase 36 bug class). The skeleton
 * and the loaded list therefore answer their row count from ONE function.
 */
class SkeletonStabilityTest {

    @Test
    fun `the skeleton never declares zero rows`() {
        for (lastKnown in 0..8) {
            assertTrue(
                "a LOADING branch with $lastKnown previous rows drew nothing",
                HubListPolicy.rowCount(HubListFacts(0, loadedOnce = false), lastKnown) > 0,
            )
        }
    }

    @Test
    fun `the skeleton honours a longer previous list`() {
        val count = HubListPolicy.rowCount(HubListFacts(0, loadedOnce = false), lastKnownCount = 11)
        assertEquals(11, count)
    }

    @Test
    fun `the fallback is the declared constant`() {
        assertEquals(
            HubListPolicy.SKELETON_ROWS,
            HubListPolicy.rowCount(HubListFacts(0, loadedOnce = false), lastKnownCount = 0),
        )
    }

    @Test
    fun `an empty result declares no rows and a loaded list declares its own`() {
        assertEquals(0, HubListPolicy.rowCount(HubListFacts(0, loadedOnce = true), 9))
        assertEquals(9, HubListPolicy.rowCount(HubListFacts(9, loadedOnce = true), 0))
        assertEquals(
            HubListBranch.LIST,
            HubListPolicy.branchFor(HubListFacts(1, loadedOnce = false)),
        )
    }
}
