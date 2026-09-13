package com.codeci.ide

import com.codeci.ide.ui.editor.DrawerCloseReason
import com.codeci.ide.ui.editor.DrawerPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 47.1 — the close-affordance matrix (PART_47_1 §Tests, amended by the
 * device round 2): every close path the app controls answers through
 * [DrawerPolicy]; a project pick is the ONE event that does not close —
 * the switch happens behind the drawer, list dropped-down.
 */
class DrawerPolicyTest {

    private val reasons = DrawerCloseReason.values()

    @Test
    fun `every reason except a project pick closes an open drawer`() {
        for (reason in reasons) {
            assertEquals(
                "$reason vs an open drawer",
                reason != DrawerCloseReason.PROJECT_SWITCHED,
                DrawerPolicy.shouldClose(reason, drawerOpen = true)
            )
        }
    }

    @Test
    fun `a project pick never closes the drawer - open or closed`() {
        // Device round 2 (owner: the pick "closes the pop up of the file
        // selection option [but] it should drop down all the available
        // projects"). The switch happens behind the drawer; the guided tour's
        // drawer beats are unreachable-to-break by construction.
        assertFalse(
            DrawerPolicy.shouldClose(DrawerCloseReason.PROJECT_SWITCHED, drawerOpen = true)
        )
        assertFalse(
            DrawerPolicy.shouldClose(DrawerCloseReason.PROJECT_SWITCHED, drawerOpen = false)
        )
    }

    @Test
    fun `a close when already closed is a no-op - no double animation`() {
        for (reason in reasons) {
            assertFalse(
                "$reason must do nothing to a closed drawer",
                DrawerPolicy.shouldClose(reason, drawerOpen = false)
            )
        }
    }

    @Test
    fun `the close button is a pure close - no dialog, no navigation, no switch`() {
        // PART_47_1 exit 1: ✕ closes it "and does nothing else".
        assertTrue(DrawerPolicy.shouldClose(DrawerCloseReason.CLOSE_BUTTON, drawerOpen = true))
        assertFalse(DrawerPolicy.shouldClose(DrawerCloseReason.CLOSE_BUTTON, drawerOpen = false))
    }
}
