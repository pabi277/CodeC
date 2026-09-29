package com.codeci.ide

import com.codeci.ide.ui.terminal.SessionLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 71.1 — owner's choice: "Name the session — the toast and the top bar". */
class SessionLabelTest {

    @Test
    fun `a default title already names the session`() {
        assertEquals("Session 2", SessionLabel.titled(2, "Session 2"))
        assertEquals("Session 2", SessionLabel.titled(2, "session 2"))
    }

    @Test
    fun `a renamed or shell-titled session keeps its number`() {
        assertEquals("3 · build", SessionLabel.titled(3, "build"))
        assertEquals("1 · ~/proj", SessionLabel.titled(1, "  ~/proj "))
    }

    @Test
    fun `a blank title falls back to the number`() {
        assertEquals("Session 4", SessionLabel.titled(4, "  "))
    }

    @Test
    fun `the toast sentence names the place, and degrades without one`() {
        assertEquals("Installing git in Session 2…", SessionLabel.sentence("Installing git", 2, "Session 2"))
        assertEquals("Running ls in 2 · build…", SessionLabel.sentence("Running ls", 2, "build"))
        assertEquals("Installing git…", SessionLabel.sentence("Installing git", null, null))
    }
}
