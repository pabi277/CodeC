package com.codeci.ide

import com.codeci.ide.ui.editor.RunButtonRole
import com.codeci.ide.ui.editor.RunButtonState
import com.codeci.ide.ui.editor.RunButtonStyle
import com.codeci.ide.ui.editor.RunButtonTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.2 — RUN ▶'s role is decided by the pure policy, so "the lock still
 * wins", "a running job still owns the button" and "nothing to run is quiet
 * but present" are tests instead of screenshots.
 */
class RunButtonStyleTest {

    private val idle = RunButtonState(running = false, locked = false, hasOpenFile = true)

    @Test
    fun `idle and runnable is the contained hero`() {
        assertEquals(RunButtonRole.CONTAINED, RunButtonStyle.roleFor(idle))
        assertTrue(RunButtonStyle.isPrimary(idle))
        assertEquals(RunButtonTone.PRIMARY_CONTAINER, RunButtonStyle.toneFor(idle))
    }

    @Test
    fun `a running job keeps the contained role and says so`() {
        val running = idle.copy(running = true)
        assertEquals(RunButtonRole.CONTAINED, RunButtonStyle.roleFor(running))
        assertTrue(RunButtonStyle.showsRunning(running))
        assertTrue("the label must answer 'did my tap work?'", RunButtonStyle.showsRunning(running))
    }

    @Test
    fun `the chrome lock wins over a running job`() {
        val locked = idle.copy(running = true, locked = true)
        assertEquals(RunButtonRole.TONAL_LOCKED, RunButtonStyle.roleFor(locked))
        assertFalse("a paused control must not claim to be running", RunButtonStyle.showsRunning(locked))
    }

    @Test
    fun `the chrome lock wins even with nothing open`() {
        val locked = idle.copy(locked = true, hasOpenFile = false)
        assertEquals(RunButtonRole.TONAL_LOCKED, RunButtonStyle.roleFor(locked))
    }

    @Test
    fun `nothing to run is tonally quiet, never hidden`() {
        val empty = idle.copy(hasOpenFile = false)
        assertEquals(RunButtonRole.TONAL_DISABLED, RunButtonStyle.roleFor(empty))
        assertEquals(RunButtonTone.SURFACE_VARIANT, RunButtonStyle.toneFor(empty))
        assertFalse(RunButtonStyle.isPrimary(empty))
    }

    @Test
    fun `only the nothing-open case is inert`() {
        // Phase 44's law: a locked tap still runs `showChromeLock`, so it is
        // enabled — the button has something to say.
        assertTrue(RunButtonStyle.isEnabled(idle.copy(locked = true)))
        assertTrue(RunButtonStyle.isEnabled(idle))
        assertFalse(RunButtonStyle.isEnabled(idle.copy(hasOpenFile = false)))
    }

    @Test
    fun `the lock is never the primary action in any combination`() {
        for (running in listOf(false, true)) {
            for (hasFile in listOf(false, true)) {
                val s = RunButtonState(running = running, locked = true, hasOpenFile = hasFile)
                assertEquals(RunButtonRole.TONAL_LOCKED, RunButtonStyle.roleFor(s))
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
                assertTrue(role == RunButtonRole.CONTAINED || role == RunButtonRole.TONAL_DISABLED)
            }
        }
        assertEquals(RunButtonRole.CONTAINED, seen[false to true])
        assertEquals(RunButtonRole.TONAL_DISABLED, seen[false to false])
        assertEquals(RunButtonRole.CONTAINED, seen[true to true])
    }

    @Test
    fun `the visual is the one call the screen makes`() {
        for (running in listOf(false, true)) {
            for (locked in listOf(false, true)) {
                for (hasFile in listOf(false, true)) {
                    val s = RunButtonState(running, locked, hasFile)
                    val v = RunButtonStyle.visualFor(s)
                    assertEquals(RunButtonStyle.roleFor(s), v.role)
                    assertEquals(RunButtonStyle.toneFor(s), v.tone)
                    assertEquals(RunButtonStyle.showsRunning(s), v.showsRunning)
                    assertEquals(RunButtonStyle.isPrimary(s), v.primary)
                }
            }
        }
    }
}
