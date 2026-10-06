package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.codeci.ide.ui.ai.AiChatHistory
import com.codeci.ide.ui.ai.AiChatHistoryLimits
import com.codeci.ide.ui.ai.AiChatRole
import com.codeci.ide.ui.ai.AiChatSession
import com.codeci.ide.ui.ai.AiProviderId
import com.codeci.ide.ui.ai.AiProviders

class AiChatHistoryTest {

    private fun turn(you: String) =
        AiChatSession.EMPTY.addYou(you, AiProviderId.GEMINI, AiProviders.defaultModel(AiProviderId.GEMINI))

    @Test fun `empty history has no current, no entries`() {
        val h = AiChatHistory.EMPTY
        assertNull(h.current())
        assertTrue(h.entries.isEmpty())
        assertTrue(h.session().isEmpty())
        assertFalse(h.taskCommitted())
    }

    @Test fun `withCurrent archives a non-empty session and keeps its id on update`() {
        val s = turn("first question")
        val h1 = AiChatHistory.EMPTY.withCurrent(s, taskCommitted = true)
        assertEquals(1, h1.entries.size)
        val id = h1.currentId
        assertNotNull(id)
        assertEquals("first question", h1.current()!!.title())
        val h2 = h1.withCurrent(s, taskCommitted = true)
        assertEquals(id, h2.currentId)
        assertEquals(1, h2.entries.size)
    }

    @Test fun `beginNew archives and leaves an empty current`() {
        val s = turn("first")
        val h = AiChatHistory.EMPTY.withCurrent(s, false).beginNew()
        assertNull("current is empty -> no currentId", h.currentId)
        assertTrue(h.session().isEmpty())
        assertEquals("the first chat is still in the drawer", 1, h.recents().size)
        assertEquals("first", h.recents().single().title())
    }

    @Test fun `switchTo replaces the session whole`() {
        val s1 = turn("alpha")
        val h1 = AiChatHistory.EMPTY.withCurrent(s1, false).beginNew()
        val sid = h1.recents().single().id
        val s2 = turn("beta")
        val h2 = h1.withCurrent(s2, false).switchTo(sid)
        assertEquals("alpha", h2.session().turns.first().text)
        assertEquals(sid, h2.currentId)
        assertEquals("beta still archived", 2, h2.entries.size)
    }

    @Test fun `togglePin moves the row between pinned and recents`() {
        val h = AiChatHistory.EMPTY.withCurrent(turn("p"), false)
        val id = h.currentId!!
        assertTrue(h.pinned().isEmpty())
        val h2 = h.togglePin(id)
        assertEquals(1, h2.pinned().size)
        assertTrue(h2.recents().isEmpty())
        val h3 = h2.togglePin(id)
        assertTrue(h3.pinned().isEmpty())
    }

    @Test fun `bounded eviction drops oldest unpinned, never pinned, never current`() {
        var h = AiChatHistory.EMPTY
        for (i in 1..AiChatHistoryLimits.MAX_CHATS + 3) {
            h = h.withCurrent(turn("q$i"), false).beginNew()
        }
        assertEquals(AiChatHistoryLimits.MAX_CHATS, h.entries.size)
        val oldestId = h.recents().last().id
        h = h.togglePin(oldestId)
        for (i in 1..3) h = h.withCurrent(turn("extra $i"), false).beginNew()
        assertTrue("pinned oldest survived", h.pinned().any { it.id == oldestId })
    }

    @Test fun `titleOf clips long questions and uses New-chat for an empty session`() {
        assertEquals("New chat", AiChatHistory.titleOf(AiChatSession.EMPTY))
        val long = "a".repeat(AiChatHistoryLimits.MAX_TITLE_CHARS + 40)
        val t = AiChatHistory.titleOf(turn(long))
        assertTrue("clipped: $t", t.endsWith("…"))
        assertTrue("length bounded ${t.length}", t.length <= AiChatHistoryLimits.MAX_TITLE_CHARS)
    }
}
