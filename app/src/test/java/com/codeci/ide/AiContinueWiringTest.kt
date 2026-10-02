package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 81 — wiring and source-scan pins for **Continue after cut off**.
 *
 * The feature's contract, in three lines: the button only exists on a cut-off
 * prose answer with room left; tapping it builds a PREVIEW of the same request
 * plus the tail (it never reaches the network itself); and the follow-up text
 * is what the preview renders, so D4 ("preview every request") is unchanged.
 *
 * Source pins run through [RepoFiles.codeOnly]; runtime values are asserted on
 * the real objects where possible.
 */
class AiContinueWiringTest {

    private val aiDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private fun ai(name: String) = RepoFiles.codeOnly(File(aiDir, name).readText())
    private fun aiRaw(name: String) = File(aiDir, name).readText()
    private val editor = RepoFiles.codeOnly(
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
    )

    @Test
    fun `the continue button is offered only on a cut-off prose answer with room left`() {
        val sheet = ai("AiChatSheet.kt")
        assertTrue(sheet.contains("AiCopy.CONTINUE"))
        assertTrue("the offer must be tied to cutShort", sheet.contains("state.cutShort"))
        assertTrue(
            "an edit proposal and an agent task must not offer it",
            sheet.contains("!p.agent") && sheet.contains("AiSource.PROPOSE_EDITS")
        )
        assertTrue(
            "the offer must respect the shared budget",
            sheet.contains("AiContinuation.canContinue(state.continuations, state.answer.length)")
        )
        // A missing button always has a sentence: no dead ends.
        assertTrue(sheet.contains("AiCopy.CONTINUE_AGENT_NOTE"))
        assertTrue(sheet.contains("AiCopy.CONTINUE_PROPOSAL_NOTE"))
        assertTrue(sheet.contains("AiCopy.CONTINUE_HINT"))
        assertTrue(sheet.contains("AiContinuation.limitNote("))
    }

    @Test
    fun `continue builds a preview and sends nothing - the network still has three doors`() {
        val vm = ai("AiViewModel.kt")
        val fn = vm.substringAfter("fun continueAnswer()").substringBefore("fun clear()")
        assertTrue("the continuation must land on the preview gate", fn.contains("phase = AiPhase.PREVIEW"))
        assertFalse("continueAnswer must not touch the network", fn.contains("client.stream("))
        assertFalse("continueAnswer must not launch a request job", fn.contains("viewModelScope.launch"))
        assertEquals(
            "still exactly three stream sites: send, agentTurn, test connection",
            3,
            Regex("client[.]stream[(]").findAll(vm).count()
        )
    }

    @Test
    fun `the continuation rides inside the one user string the preview renders`() {
        val ctx = ai("AiContext.kt")
        assertTrue("AiPrompt carries the continuation", ctx.contains("val continuation: AiContinuationRequest?"))
        val getter = ctx.substringAfter("val userText: String").substringBefore("val sentChars")
        assertTrue(
            "the sent body must be the rendered text plus the block",
            getter.contains("AiPromptText.userText(this) + (continuation?.let { AiContinuation.block(it) }")
        )
        // And the preview prints that same string (Phase 76 D4 rule).
        assertTrue(ai("AiChatSheet.kt").contains("SentText(it.systemInstruction"))
        assertTrue("the preview must disclose the continuation", ai("AiChatSheet.kt").contains("AiCopy.continuePreviewNote(c.index)"))
    }

    @Test
    fun `the answer so far stays on screen and the shared budget is enforced at the client`() {
        val vm = ai("AiViewModel.kt")
        assertTrue(
            "a continuation keeps the earlier text",
            vm.contains("val base = if (prompt.continuation != null) _state.value.answer else")
        )
        assertTrue("the new text streams under it", vm.contains("s.copy(answer = base + text)"))
        assertTrue("the finished text is the whole answer", vm.contains("answer = base + outcome.text"))
        assertTrue("the request carries the remaining budget", vm.contains("AiContinuation.requestBudget(base.length)"))
        val client = ai("GeminiClient.kt")
        assertTrue("the client takes a per-reply budget", client.contains("maxChars: Int = AiLimits.MAX_REPLY_CHARS"))
        assertTrue("the accumulator is built with it", client.contains("AiAnswerAccumulator(maxChars = maxChars)"))
    }

    @Test
    fun `an agent task and a new question clear the continuation state`() {
        val vm = ai("AiViewModel.kt")
        assertTrue("state carries the count", vm.contains("val continuations: Int = 0"))
        val preview = vm.substringAfter("fun preview(result: AiContextResult)").substringBefore("fun cancelPreview()")
        assertTrue("a fresh preview is a fresh answer", preview.contains("continuations = 0"))
        val clear = vm.substringAfter("fun clear()").substringBefore("fun dismissNotice()")
        assertTrue("clear resets it", clear.contains("continuations = 0"))
        assertTrue("the counter only grows from the prompt", vm.contains("continuations = prompt.continuation?.index ?: 0"))
    }

    @Test
    fun `the editor and the sheet are wired, and the button sends nothing by itself`() {
        assertTrue(editor.contains("onContinue = aiViewModel::continueAnswer"))
        val sheet = ai("AiChatSheet.kt")
        assertTrue(sheet.contains("onContinue: () -> Unit = {}"))
        assertTrue(sheet.contains("onContinue: () -> Unit,"))
        // The handler is passed straight to the button: no send inside the sheet.
        val bar = sheet.substringAfter("AiPhase.DONE -> Row(").substringBefore("AiPhase.FAILED ->")
        assertTrue(bar.contains("OutlinedButton(onClick = onContinue)"))
        assertFalse("the sheet must not send", bar.contains("onSend"))
    }

    @Test
    fun `the new surface is pure and brings no new dependency or key`() {
        val code = RepoFiles.codeOnly(aiRaw("AiContinuation.kt"))
        for (banned in listOf("java.io", "android.", "ProcessBuilder", "Runtime.getRuntime")) {
            assertFalse("AiContinuation.kt must stay pure (found $banned)", code.contains(banned))
        }
        val all = aiDir.listFiles { f -> f.extension == "kt" }!!.joinToString("\n") { it.readText() }
        assertFalse("no new store key", all.contains("\"ai_continuation"))
    }
}
