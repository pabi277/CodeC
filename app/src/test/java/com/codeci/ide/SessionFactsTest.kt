package com.codeci.ide

import com.codeci.ide.ui.terminal.SessionFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 104.3 — session facts (pure). */
class SessionFactsTest {

    @Test
    fun `exit notice names the code and asks for Enter`() {
        assertEquals("Process completed (code 0) \u2014 press Enter", SessionFacts.exitNotice(0))
        assertEquals("Process completed (code 137) \u2014 press Enter", SessionFacts.exitNotice(137))
        assertEquals("Process completed \u2014 press Enter", SessionFacts.exitNotice(null))
    }

    @Test
    fun `Enter restarts only a dead session`() {
        assertTrue(SessionFacts.restartsOnInput(dead = true, input = "\r"))
        assertTrue(SessionFacts.restartsOnInput(dead = true, input = "\n"))
        assertFalse(SessionFacts.restartsOnInput(dead = false, input = "\r"))
        assertFalse(SessionFacts.restartsOnInput(dead = true, input = "x"))
        assertFalse(SessionFacts.restartsOnInput(dead = true, input = ""))
    }

    @Test
    fun `a new tab inherits only an absolute directory`() {
        assertEquals("/data/user/0/app/files/projects", SessionFacts.inheritedDirectory("/data/user/0/app/files/projects"))
        assertNull(SessionFacts.inheritedDirectory(null))
        assertNull(SessionFacts.inheritedDirectory("relative/dir"))
        assertNull(SessionFacts.inheritedDirectory(""))
    }
}
