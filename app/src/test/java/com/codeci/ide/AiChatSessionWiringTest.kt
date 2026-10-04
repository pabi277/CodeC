package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 90 (the conversation surface, 90.2) — the wiring, pinned as source.
 *
 * These read the real repo tree (the house style of `RepoFiles`), so a later
 * edit cannot quietly add a second network road, persist the conversation, skip
 * the New-chat confirmation or stop labelling a turn with who answered it.
 */
class AiChatSessionWiringTest {

    private val aiDir = "app/src/main/java/com/codeci/ide/ui/ai"

    private fun src(name: String): String = RepoFiles.mainSource("$aiDir/$name").readText()

    private fun aiSources(): List<File> =
        File(RepoFiles.root(), aiDir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `Send is still the only network road`() {
        val streams = aiSources().sumOf { f -> Regex("client\\.stream\\(").findAll(f.readText()).count() }
        assertEquals("exactly three stream sites", 3, streams)
        val openUri = aiSources().sumOf { f -> Regex("openUri\\(").findAll(f.readText()).count() }
        assertEquals("exactly two openUri sites in ui/ai", 2, openUri)
    }

    @Test
    fun `the transcript model is pure, and no AI file persists it`() {
        val model = src("AiChatSession.kt")
        assertFalse("no android imports", model.contains("import android"))
        assertFalse("no java.io imports", model.contains("import java.io"))
        assertFalse("no java.nio imports (minSdk 24 keeps the model on java.io at most)", model.contains("import java.nio"))
        assertFalse("no file handles", model.contains("File(") || model.contains("FileOutputStream") || model.contains("FileWriter"))
        assertFalse("no logging", model.contains("Log."))
        assertFalse("no clock", model.contains("currentTimeMillis"))
        assertFalse("no store", model.contains("AiKeyStore") || model.contains("DataStore"))
        assertFalse("the key store never learns about chat", src("AiKeyStore.kt").contains("AiChatSession"))
        assertFalse("the Level 9 task store never learns about chat", src("AiTaskMemory.kt").contains("AiChatSession"))
    }

    @Test
    fun `the prompt packs the transcript inside the user text`() {
        val context = src("AiContext.kt")
        assertTrue(
            "the block is built from the prompt's own session",
            context.contains("val transcript = session.render()")
        )
        assertTrue(
            "and it is what userText returns for a helper request",
            context.contains("if (transcript.isEmpty()) body else transcript + body")
        )
        assertTrue(
            "an agent task carries it inside the packed text instead, so preview and send stay one string",
            context.contains("transcript = session.render()")
        )
        assertTrue("the prompt carries it", context.contains("val session: AiChatSession = AiChatSession.EMPTY"))
        val model = src("AiViewModel.kt")
        assertTrue("the preview freezes it into the prompt", model.contains("session = session"))
    }

    @Test
    fun `an agent task carries the conversation on its first turn only`() {
        val loop = src("AiAgentLoop.kt")
        assertTrue("pack takes it, defaulted for every existing caller", loop.contains("transcript: String = \"\""))
        assertTrue("and it opens the packed head", loop.contains("if (transcript.isNotEmpty()) append(transcript)"))
        val model = src("AiViewModel.kt")
        assertTrue("the task is born with it", model.contains("val transcript: String = \"\","))
        assertTrue("and it rides the first turn", model.contains("transcript = if (steps.isEmpty()) session.transcript else \"\""))
        assertTrue("frozen before the task starts", model.contains("val transcript = commitFinishedTask().render()"))
    }

    @Test
    fun `a committed task can never be committed twice`() {
        val model = src("AiViewModel.kt")
        assertTrue("the flag exists", model.contains("val taskCommitted: Boolean = false"))
        assertTrue("and the commit checks it", model.contains("if (s.taskCommitted) return s.session"))
        assertTrue("a new task resets it", model.contains("taskCommitted = false"))
    }

    @Test
    fun `a finished task is committed once, and only when settled`() {
        val model = src("AiViewModel.kt")
        assertTrue(model.contains("private fun commitFinishedTask()"))
        assertTrue(
            "only DONE or FAILED tasks are settled",
            model.contains("if (s.phase != AiPhase.DONE && s.phase != AiPhase.FAILED) return s.session")
        )
        assertTrue(
            "a continuation and a review never commit the current task",
            model.contains("if (prompt.continuation != null || prompt.source == AiSource.REVIEW) return current")
        )
        assertTrue(
            "the reviewer's own task is never a turn",
            model.contains("if (prompt.source == AiSource.REVIEW) return s.session")
        )
    }

    @Test
    fun `New chat clears the conversation, and clear keeps it`() {
        val model = src("AiViewModel.kt")
        assertTrue("newChat exists", model.contains("fun newChat()"))
        assertTrue(
            "newChat empties the session after clearing the task",
            model.contains("fun newChat() {") &&
                model.contains("it.copy(session = AiChatSession.EMPTY, taskCommitted = false)")
        )
        assertTrue(
            "clear commits the settled task instead of dropping it",
            model.contains("fun clear() {")
        )
        val sheet = src("AiChatSheet.kt")
        assertTrue("the sheet asks first", sheet.contains("AiCopy.NEW_CHAT_CONFIRM"))
        assertTrue("and says nothing is saved", sheet.contains("AiCopy.NEW_CHAT_BODY"))
        assertTrue("the action is wired", sheet.contains("onNewChat"))
    }

    @Test
    fun `a project switch drops the transcript`() {
        val model = src("AiViewModel.kt")
        assertTrue(
            "another project's conversation is a leak surface",
            Regex("session = AiChatSession\\.EMPTY, taskCommitted = false").findAll(model).count() >= 2
        )
    }

    @Test
    fun `every drawn turn says who answered it`() {
        val sheet = src("AiChatSheet.kt")
        assertTrue("the turns are drawn in order", sheet.contains("state.session.turns.forEach"))
        assertTrue("with the role", sheet.contains("AiChatRole.YOU -> YouBubble(turn.text)"))
        assertTrue(
            "and a label naming the real recipient",
            sheet.contains("AiCopy.turnLabel(")
        )
        val copy = src("AiCopy.kt")
        assertTrue(copy.contains("fun turnLabel(provider: AiProviderId, model: String, stopped: Boolean)"))
    }

    @Test
    fun `the follow-up disclosure shows the exact block that will be sent`() {
        val sheet = src("AiChatSheet.kt")
        assertTrue("the preview reads the prompt's own session", sheet.contains("val earlier = it.session.render()"))
        assertTrue("collapsed by default", sheet.contains("AiCopy.EARLIER_TURNS_SHOW"))
        assertTrue("and it shows the rendered block", sheet.contains("Body(earlier.trimEnd())"))
    }
}
