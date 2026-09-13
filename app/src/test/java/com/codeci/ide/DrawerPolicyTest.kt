package com.codeci.ide

import com.codeci.ide.ui.editor.DrawerCloseReason
import com.codeci.ide.ui.editor.DrawerPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 47.1 — the close-affordance matrix (PART_47_1 §Tests). Every close
 * path the app controls answers through [DrawerPolicy]; the matrix pins that
 * they all agree and that a project switch is the ONLY close that also
 * changes context.
 */
class DrawerPolicyTest {

    private val reasons = DrawerCloseReason.values()

    @Test
    fun `every reason closes an open drawer`() {
        for (reason in reasons) {
            assertTrue(
                "$reason must close an open drawer",
                DrawerPolicy.shouldClose(reason, drawerOpen = true)
            )
        }
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
    fun `only PROJECT_SWITCHED closes and switches context`() {
        for (reason in reasons) {
            assertEquals(
                "$reason closesAndSwitches",
                reason == DrawerCloseReason.PROJECT_SWITCHED,
                DrawerPolicy.closesAndSwitches(reason)
            )
        }
    }

    @Test
    fun `the close button is a pure close - no dialog, no navigation, no switch`() {
        // PART_47_1 exit 1: ✕ closes it "and does nothing else".
        assertTrue(DrawerPolicy.shouldClose(DrawerCloseReason.CLOSE_BUTTON, drawerOpen = true))
        assertFalse(DrawerPolicy.closesAndSwitches(DrawerCloseReason.CLOSE_BUTTON))
    }
}
