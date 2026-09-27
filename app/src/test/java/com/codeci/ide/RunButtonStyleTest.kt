package com.codeci.ide

import com.codeci.ide.ui.editor.RunButtonRole
import com.codeci.ide.ui.editor.RunButtonState
import com.codeci.ide.ui.editor.RunButtonStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.2, re-scoped by Phase 57.1 — RUN ▶'s role is decided by the pure
 * policy, so "the lock still wins", "a running job still owns the control" and
 * "nothing to run is quiet but present" are tests instead of screenshots.
 *
 * What changed with the reference: the control is the shots' bare green ▶, so
 * the container *tone* pair is gone (dead policy is worse than none) and the
 * vocabulary is the glyph's: HERO / LOCKED / QUIET. What did not change is
 * every rule that keeps the tap honest — the lock's precedence, the one inert
 * case, and the running state's single owner.
 */
class RunButtonStyleTest {

    private val idle = RunButtonState(running = false, locked = false, hasOpenFile = true)

    @Test
    fun `idle and runnable is the hero glyph`() {
        assertEquals(RunButtonRole.HERO, RunButtonStyle.roleFor(idle))
        assertTrue(RunButtonStyle.isPrimary(idle))
    }

    @Test
    fun `a running job keeps the hero role and says so`() {
        val running = idle.copy(running = true)
        assertEquals(RunButtonRole.HERO, RunButtonStyle.roleFor(running))
        assertTrue(RunButtonStyle.showsRunning(running))
        assertTrue("the state must answer 'did my tap work?'", RunButtonStyle.showsRunning(running))
    }

    @Test
    fun `the chrome lock wins over a running job`() {
        val locked = idle.copy(running = true, locked = true)
        assertEquals(RunButtonRole.LOCKED, RunButtonStyle.roleFor(locked))
        assertFalse("a paused control must not claim to be running", RunButtonStyle.showsRunning(locked))
    }

    @Test
    fun `the chrome lock wins even with nothing open`() {
        val locked = idle.copy(locked = true, hasOpenFile = false)
        assertEquals(RunButtonRole.LOCKED, RunButtonStyle.roleFor(locked))
    }

    @Test
    fun `nothing to run is quiet, never hidden`() {
        val empty = idle.copy(hasOpenFile = false)
        assertEquals(RunButtonRole.QUIET, RunButtonStyle.roleFor(empty))
        assertFalse(RunButtonStyle.isPrimary(empty))
    }

    @Test
    fun `only the nothing-open case is inert`() {
        // Phase 44's law: a locked tap still runs `showChromeLock`, so it is
        // enabled — the control has something to say.
        assertTrue(RunButtonStyle.isEnabled(idle.copy(locked = true)))
        assertTrue(RunButtonStyle.isEnabled(idle))
        assertFalse(RunButtonStyle.isEnabled(idle.copy(hasOpenFile = false)))
    }

    @Test
    fun `the lock is never the primary action in any combination`() {
        for (running in listOf(false, true)) {
            for (hasFile in listOf(false, true)) {
                val s = RunButtonState(running = running, locked = true, hasOpenFile = hasFile)
                assertEquals(RunButtonRole.LOCKED, RunButtonStyle.roleFor(s))
                assertFalse(RunButtonStyle.isPrimary(s))
                assertFalse(RunButtonStyle.showsRunning(s))
            }
        }
    }

    @Test
    fun `every combination maps to exactly one role`() {
        val seen = mutableMapOf<Pair<Boolean, Boolean>, RunButtonRole>()
        for (running in listOf(false, true)) {
            for (hasFile in listOf(false, true)) {
                val s = RunButtonState(running = running, locked = false, hasOpenFile = hasFile)
                val role = RunButtonStyle.roleFor(s)
                seen[running to hasFile] = role
                // The role is total: never null, never a fourth value.
                assertTrue(role == RunButtonRole.HERO || role == RunButtonRole.QUIET)
            }
        }
        assertEquals(RunButtonRole.HERO, seen[false to true])
        assertEquals(RunButtonRole.QUIET, seen[false to false])
        assertEquals(RunButtonRole.HERO, seen[true to true])
    }

    @Test
    fun `the green is the hero's alone`() {
        // The reference paints ONE control the run green; a locked or quiet
        // control must fall back to the theme's on-surface variant.
        assertTrue(RunButtonStyle.isPrimary(idle))
        assertFalse(RunButtonStyle.isPrimary(idle.copy(locked = true)))
        assertFalse(RunButtonStyle.isPrimary(idle.copy(hasOpenFile = false)))
    }
}
