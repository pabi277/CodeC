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
        val long = "a".repeat(AiChatHistoryLimits.MAX_TITLE_CHARS + 40)
        val t = AiChatHistory.titleOf(turn(long))
        assertEquals("New chat", AiChatHistory.titleOf(AiChatSession.EMPTY))
        assertTrue("clipped: $t", t.endsWith("…"))
        assertTrue("length bounded ${t.length}", t.length <= AiChatHistoryLimits.MAX_TITLE_CHARS)
    }

    // ---- Phase 96: what the drawer draws ----------------------------------

    @Test fun `summaries mark exactly one row current, and none after a new chat`() {
        // The mark is how a drawer of four rows answers "where am I". It is a
        // property of the history, never a stored flag on the row, so a switch
        // moves it and nothing can leave two rows marked.
        val alpha = AiChatHistory.EMPTY.withCurrent(turn("alpha"), false)
        assertEquals(listOf(true), alpha.summaries().map { it.current })
        val both = alpha.beginNew().withCurrent(turn("beta"), false)
        assertEquals("newest first, and only it is current", listOf(true, false), both.summaries().map { it.current })
        val back = both.switchTo(alpha.currentId!!)
        assertEquals("the mark follows the switch", listOf(false, true), back.summaries().map { it.current })
        assertTrue("a fresh chat marks nothing", both.beginNew().summaries().none { it.current })
    }

    @Test fun `archiving one chat never edits another`() {
        // The ordering bug Phase 96 fixed showed up here as a legal-looking call:
        // `withCurrent` may only touch the entry the history points at. If a
        // settle ever ran with a different `currentId`, the previous
        // conversation's last exchange would be appended to the chat being
        // switched TO — the merge the drawer promises never to do.
        val alpha = AiChatHistory.EMPTY.withCurrent(turn("alpha"), false)
        val alphaId = alpha.currentId!!
        val beta = alpha.beginNew().withCurrent(turn("beta"), false)
        val betaId = beta.currentId!!
        val settled = beta.withCurrent(turn("beta").addAssistant("answered", AiProviderId.GEMINI, "m"), true)
        assertEquals("beta grew, alpha did not", 2, settled.entries.first { it.id == betaId }.session.turns.size)
        assertEquals(1, settled.entries.first { it.id == alphaId }.session.turns.size)
        assertEquals("alpha", settled.entries.first { it.id == alphaId }.title())
    }

    @Test fun `the drawer has a row as soon as the first exchange lands`() {
        // Phase 96 round 2, the owner: *"when I started chat. It doesn't
        // automatically create the chat history instantly. After opening a new
        // chat, it creates the history so fix it."* The row is the settle's own
        // doing — no New chat, no switch and no second question has to happen
        // first. An empty conversation still draws nothing, so a project whose
        // chat was never used keeps an empty drawer.
        assertTrue("nothing asked, nothing to show", AiChatHistory.EMPTY.withCurrent(AiChatSession.EMPTY, false).summaries().isEmpty())
        val asked = AiChatHistory.EMPTY.withCurrent(turn("what is onCreate"), taskCommitted = true)
        assertEquals(1, asked.summaries().size)
        assertEquals("what is onCreate", asked.summaries().first().title)
        assertTrue("and it is the one on screen", asked.summaries().first().current)
    }
}
