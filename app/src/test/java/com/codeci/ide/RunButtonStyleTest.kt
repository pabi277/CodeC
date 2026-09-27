package com.codeci.ide

import com.codeci.ide.ui.editor.RunButtonRole
import com.codeci.ide.ui.editor.RunButtonState
import com.codeci.ide.ui.editor.RunButtonStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunButtonStyleTest {
    @Test
    fun `an open file has an enabled primary run action`() {
        val state = RunButtonState()
        assertEquals(RunButtonRole.HERO, RunButtonStyle.roleFor(state))
        assertTrue(RunButtonStyle.isEnabled(state))
        assertTrue(RunButtonStyle.isPrimary(state))
        assertFalse(RunButtonStyle.showsRunning(state))
    }

    @Test
    fun `a busy job keeps its real progress visible`() {
        val state = RunButtonState(running = true)
        assertTrue(RunButtonStyle.showsRunning(state))
        assertTrue(RunButtonStyle.isEnabled(state))
        assertEquals(RunButtonRole.HERO, RunButtonStyle.roleFor(state))
    }

    @Test
    fun `no open file is quiet and inert`() {
        val state = RunButtonState(hasOpenFile = false)
        assertEquals(RunButtonRole.QUIET, RunButtonStyle.roleFor(state))
        assertFalse(RunButtonStyle.isEnabled(state))
        assertFalse(RunButtonStyle.isPrimary(state))
    }

    @Test
    fun `every state depends on file availability and actual job progress only`() {
        for (running in listOf(false, true)) for (hasFile in listOf(false, true)) {
            val state = RunButtonState(running, hasFile)
            assertEquals(running, RunButtonStyle.showsRunning(state))
            assertEquals(hasFile, RunButtonStyle.isEnabled(state))
            assertEquals(hasFile, RunButtonStyle.isPrimary(state))
        }
    }
}
