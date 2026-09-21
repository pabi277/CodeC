package com.codeci.ide

import com.codeci.ide.ui.projects.HubListPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.3 — the hub's surfaces: a designed empty state, a skeleton instead
 * of a blank frame, and the card actions the owner device-tested left exactly
 * as they were.
 */
class HubSurfaceTest {

    private val hub = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt"
    ).readText()

    @Test
    fun `the hub has three branches, not two`() {
        assertTrue(hub.contains("HubListPolicy.branchFor("))
        assertTrue(hub.contains("HubListBranch.LOADING ->"))
        assertTrue(hub.contains("HubListBranch.EMPTY ->"))
        assertTrue(hub.contains("HubListBranch.LIST ->"))
    }

    @Test
    fun `the loading branch renders skeleton cards`() {
        val loading = hub.substringAfter("HubListBranch.LOADING ->").substringBefore("HubListBranch.EMPTY ->")
        assertTrue("the loading branch must draw the list's own shape", loading.contains("SkeletonHubCard("))
        assertTrue(
            "and it must declare the same count the policy does",
            loading.contains("HubListPolicy.rowCount("),
        )
    }

    @Test
    fun `the skeleton template looks like a hub card`() {
        val skeleton = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/components/Skeleton.kt"
        ).readText()
        assertTrue(skeleton.contains("fun SkeletonBox("))
        assertTrue(skeleton.contains("CodecMotion.shimmer"))
        assertTrue(
            "tokens only: the placeholder is chrome",
            skeleton.contains("CodecTokens.radius(") && skeleton.contains("CodecTokens.space("),
        )
    }

    @Test
    fun `the empty hub is a designed state`() {
        val empty = hub.substringAfter("private fun EmptyProjectsState(")
        assertTrue("the empty state carries the app's mark", empty.contains("R.drawable.app_mark"))
        assertTrue("and the three starter tiles", empty.contains("StarterTile("))
        assertTrue("and the create door", empty.contains("R.string.hub_create_first"))
    }

    @Test
    fun `the hub's own read feeds the policy, not the generic busy flag`() {
        assertTrue(hub.contains("viewModel.hubListFacts.collectAsState()"))
        assertTrue(hub.contains("viewModel.lastKnownHubRowCount()"))
    }

    @Test
    fun `the skeleton count is the list's count for the same input`() {
        val loading = com.codeci.ide.ui.projects.HubListFacts(entryCount = 0, loadedOnce = false)
        val loaded = com.codeci.ide.ui.projects.HubListFacts(entryCount = 4, loadedOnce = true)
        assertEquals(4, HubListPolicy.rowCount(loaded, lastKnownCount = 0))
        assertEquals(4, HubListPolicy.rowCount(loading, lastKnownCount = 4))
    }

    @Test
    fun `every hub card action the owner tested still exists`() {
        val expected = listOf(
            "OPEN",
            "OPEN_IN_EDITOR",
            "RENAME",
            "EXPORT",
            "SHARE_ZIP",
            "DELETE",
            "PUSH",
            "PULL",
            "COPY_REMOTE_URL",
            "SWITCH_BRANCH",
            "SOURCE_CONTROL",
        )
        for (action in expected) {
            assertTrue("HubCardAction.$action disappeared", hub.contains("HubCardAction.$action"))
        }
    }

    @Test
    fun `the hub chrome is on tokens`() {
        assertFalse(
            "no raw radius may come back to the hub",
            Regex("""RoundedCornerShape\(\s*\d+\s*\.dp""").containsMatchIn(hub),
        )
    }

    @Test
    fun `the hub is interactive through the policy's own branch`() {
        assertTrue(HubListPolicy.isPlaceholder(com.codeci.ide.ui.projects.HubListBranch.LOADING))
    }
}
