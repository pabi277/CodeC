package com.codeci.ide

import com.codeci.ide.ui.projects.HubListBranch
import com.codeci.ide.ui.projects.HubListFacts
import com.codeci.ide.ui.projects.HubListPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.3 — the hub's three states. The bug being closed: before the first
 * read lands, the hub rendered the EMPTY state, so "still reading" and "your
 * projects are gone" were the same screen.
 */
class HubListPolicyTest {

    @Test
    fun `nothing read yet is loading, never empty`() {
        assertEquals(
            HubListBranch.LOADING,
            HubListPolicy.branchFor(HubListFacts(entryCount = 0, loadedOnce = false)),
        )
    }

    @Test
    fun `a finished read with no projects is the empty state`() {
        assertEquals(
            HubListBranch.EMPTY,
            HubListPolicy.branchFor(HubListFacts(entryCount = 0, loadedOnce = true)),
        )
    }

    @Test
    fun `entries in hand are the list, even before the read finishes`() {
        assertEquals(
            HubListBranch.LIST,
            HubListPolicy.branchFor(HubListFacts(entryCount = 4, loadedOnce = false)),
        )
    }

    @Test
    fun `the list count is the data's own count`() {
        val facts = HubListFacts(entryCount = 7, loadedOnce = true)
        assertEquals(7, HubListPolicy.rowCount(facts, lastKnownCount = 99))
    }

    @Test
    fun `the skeleton honours the last known count so the shape does not jump`() {
        val loading = HubListFacts(entryCount = 0, loadedOnce = false)
        assertEquals(6, HubListPolicy.rowCount(loading, lastKnownCount = 6))
    }

    @Test
    fun `the skeleton has a floor when there is no previous count`() {
        val loading = HubListFacts(entryCount = 0, loadedOnce = false)
        assertEquals(HubListPolicy.SKELETON_ROWS, HubListPolicy.rowCount(loading, lastKnownCount = 0))
        assertTrue(HubListPolicy.SKELETON_ROWS > 0)
    }

    @Test
    fun `the empty state declares no rows at all`() {
        assertEquals(
            0,
            HubListPolicy.rowCount(HubListFacts(entryCount = 0, loadedOnce = true), lastKnownCount = 5),
        )
    }

    @Test
    fun `only the loading branch is a placeholder`() {
        assertTrue(HubListPolicy.isPlaceholder(HubListBranch.LOADING))
        assertFalse(HubListPolicy.isPlaceholder(HubListBranch.EMPTY))
        assertFalse(HubListPolicy.isPlaceholder(HubListBranch.LIST))
    }

    @Test
    fun `the loaded list and the skeleton can never disagree about a non-empty count`() {
        // Same input, same answer: the transition from skeleton to list changes
        // the shape only by the data itself (the Phase 36 scroll-position bug
        // class).
        val loading = HubListFacts(entryCount = 0, loadedOnce = false)
        val loaded = HubListFacts(entryCount = 3, loadedOnce = true)
        assertEquals(HubListPolicy.rowCount(loading, 3), HubListPolicy.rowCount(loaded, 3))
    }
}
