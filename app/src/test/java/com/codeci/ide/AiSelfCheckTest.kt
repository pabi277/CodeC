package com.codeci.ide

import com.codeci.ide.ui.ai.AiProviderId
import com.codeci.ide.ui.ai.AiSelfCheck
import com.codeci.ide.ui.ai.AiSelfCheckObserved
import com.codeci.ide.ui.ai.AiSelfCheckOutcome
import com.codeci.ide.ui.ai.AiSelfCheckDoor
import com.codeci.ide.ui.ai.AiSelfCheckVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 92 — the self-check script and its verdicts, host-tested.
 *
 * The owner asked for a command he can run instead of judging rows by eye, so
 * the judgements themselves are the thing under test here: a verdict must be
 * mechanical, honest and pending-until-proven, and the report must never carry a
 * prompt, an answer or a key.
 */
class AiSelfCheckTest {

    private val steps = AiSelfCheck.STEPS
    private fun step(id: String) = steps.first { it.id == id }

    private fun observed(
        question: String? = null,
        settled: Boolean = true,
        answerChars: Int = 20,
        usedCodeWord: Boolean = false,
        cutShort: Boolean = false,
        errorLine: String? = null,
        proposalFiles: Int = 0,
        proposalInvalidReason: String? = null,
        runRequested: Boolean = false,
        runRefusedReason: String? = null,
        sentChars: Int = 2_500,
        transcriptChars: Int = 1_800,
        keySaved: Boolean = true,
        sdkInt: Int = 34,
        allFilesAccess: Boolean = true,
        storageGranted: Boolean = true
    ) = AiSelfCheckObserved(
        question = question, settled = settled, answerChars = answerChars, usedCodeWord = usedCodeWord,
        cutShort = cutShort, errorLine = errorLine, proposalFiles = proposalFiles,
        proposalInvalidReason = proposalInvalidReason, runRequested = runRequested,
        runRefusedReason = runRefusedReason, sentChars = sentChars,
        transcriptChars = transcriptChars, provider = AiProviderId.NVIDIA,
        model = "nvidia/nemotron-3-super-120b-a12b", keySaved = keySaved, sdkInt = sdkInt,
        allFilesAccess = allFilesAccess, storageGranted = storageGranted
    )

    // ---- the script ---------------------------------------------------------

    @Test
    fun `the script is five checks and only the ones that need a request have a question`() {
        assertEquals(5, steps.size)
        assertEquals(listOf("access", "remember", "recall", "proposal", "run"), steps.map { it.id })
        assertEquals("the access check needs no request", null, step("access").door)
        assertEquals(null, step("access").prompt)
        for (s in steps.filter { it.door != null }) {
            assertNotNull("${s.id} needs a question", s.prompt)
            assertTrue("${s.id} asks something", (s.prompt ?: "").length > 20)
        }
    }

    @Test
    fun `the follow-up check is a real follow-up and the code word is in the first question only`() {
        assertTrue(step("remember").prompt!!.contains(AiSelfCheck.CODE_WORD))
        assertFalse("the follow-up must not repeat the code word", step("recall").prompt!!.contains(AiSelfCheck.CODE_WORD))
        assertTrue("it refers to the previous message", step("recall").prompt!!.contains("previous message"))
        assertEquals("remember is asked first, recall second", 1, steps.indexOf(step("remember")))
        assertEquals(2, steps.indexOf(step("recall")))
    }

    @Test
    fun `both follow-up checks go through the ordinary ask door, the diff check through the propose door`() {
        assertEquals(AiSelfCheckDoor.ASK, step("remember").door)
        assertEquals(AiSelfCheckDoor.ASK, step("recall").door)
        assertEquals(AiSelfCheckDoor.PROPOSE, step("proposal").door)
        assertEquals(AiSelfCheckDoor.ASK, step("run").door)
    }

    // ---- the access check ---------------------------------------------------

    @Test
    fun `access passes with all-files access and names the switch when it is missing`() {
        val pass = AiSelfCheck.judge(step("access"), observed(sdkInt = 34, allFilesAccess = true))
        assertEquals(AiSelfCheckOutcome.PASS, pass.outcome)
        assertTrue("the API is named", pass.detail.contains("API 34"))

        val fail = AiSelfCheck.judge(step("access"), observed(sdkInt = 34, allFilesAccess = false))
        assertEquals(AiSelfCheckOutcome.FAIL, fail.outcome)
        // Phase 93: the sentence is StorageAccessPolicy.fixSteps — the same words
        // the preflight and the terminal gate show, and a real path on Android 11+.
        assertTrue("the fix is named", fail.detail.contains("Settings → Apps → CodeC → Permissions"))
        assertTrue("and the switch itself", fail.detail.contains("Allow management of all files"))
    }

    @Test
    fun `below API 30 the legacy storage permission is the one that counts`() {
        val pass = AiSelfCheck.judge(step("access"), observed(sdkInt = 29, storageGranted = true, allFilesAccess = false))
        assertEquals(AiSelfCheckOutcome.PASS, pass.outcome)
        val fail = AiSelfCheck.judge(step("access"), observed(sdkInt = 29, storageGranted = false))
        assertEquals(AiSelfCheckOutcome.FAIL, fail.outcome)
    }

    // ---- pending is never a guess -------------------------------------------

    @Test
    fun `nothing is judged before its own task settles`() {
        for (id in listOf("remember", "recall", "proposal", "run")) {
            val v = AiSelfCheck.judge(step(id), observed(question = step(id).prompt, settled = false))
            assertEquals("$id must wait", AiSelfCheckOutcome.PENDING, v.outcome)
        }
    }

    @Test
    fun `another question of the owner's is never read as the step's task`() {
        val v = AiSelfCheck.judge(step("recall"), observed(question = "what does main.js do?"))
        assertEquals(AiSelfCheckOutcome.PENDING, v.outcome)
    }

    @Test
    fun `a provider error fails the step with the app's own line`() {
        val v = AiSelfCheck.judge(
            step("remember"),
            observed(question = step("remember").prompt, errorLine = "Limit hit; retrying in 30s")
        )
        assertEquals(AiSelfCheckOutcome.FAIL, v.outcome)
        assertTrue(v.detail.contains("Limit hit"))
    }

    // ---- the four checks ----------------------------------------------------

    @Test
    fun `remember passes on an answer and fails on an empty or cut-off one`() {
        val q = step("remember")
        assertEquals(AiSelfCheckOutcome.PASS, AiSelfCheck.judge(q, observed(question = q.prompt)).outcome)
        assertEquals(
            AiSelfCheckOutcome.FAIL,
            AiSelfCheck.judge(q, observed(question = q.prompt, answerChars = 0)).outcome
        )
        assertEquals(
            AiSelfCheckOutcome.FAIL,
            AiSelfCheck.judge(q, observed(question = q.prompt, cutShort = true)).outcome
        )
    }

    @Test
    fun `recall fails when the app carried no conversation at all`() {
        val v = AiSelfCheck.judge(
            step("recall"),
            observed(question = step("recall").prompt, transcriptChars = 0, usedCodeWord = false)
        )
        assertEquals(AiSelfCheckOutcome.FAIL, v.outcome)
        assertTrue("it must point at the app, not the model", v.detail.contains("the app, not the model"))
    }

    @Test
    fun `recall passes when the model used the code word, in any spacing or case`() {
        for (answer in listOf("KIWI-42", "kiwi 42", "K I W I - 4 2", "The code word is KIWI-42.")) {
            assertTrue("\"$answer\" must count", AiSelfCheck.usedCodeWord(answer))
        }
        for (answer in listOf("I do not know", "there was no code word", "kiwi", "42")) {
            assertFalse("\"$answer\" must not count", AiSelfCheck.usedCodeWord(answer))
        }
        val v = AiSelfCheck.judge(step("recall"), observed(question = step("recall").prompt, usedCodeWord = true))
        assertEquals(AiSelfCheckOutcome.PASS, v.outcome)
        assertTrue("the carried size is in the line", v.detail.contains("1800"))
    }

    @Test
    fun `recall fails when the conversation was carried and ignored, with both numbers`() {
        val v = AiSelfCheck.judge(
            step("recall"),
            observed(question = step("recall").prompt, transcriptChars = 1_800, answerChars = 41, usedCodeWord = false)
        )
        assertEquals(AiSelfCheckOutcome.FAIL, v.outcome)
        assertTrue(v.detail.contains("1800"))
        assertTrue(v.detail.contains("41"))
    }

    @Test
    fun `the proposal check separates parsed, rejected and absent`() {
        val q = step("proposal")
        val parsed = AiSelfCheck.judge(q, observed(question = q.prompt, proposalFiles = 1))
        assertEquals(AiSelfCheckOutcome.PASS, parsed.outcome)
        assertTrue(parsed.detail.contains("1 file(s)"))

        val rejected = AiSelfCheck.judge(
            q, observed(question = q.prompt, proposalInvalidReason = "Unclosed <<<SEARCH>>> block in 'app.py'")
        )
        assertEquals(AiSelfCheckOutcome.FAIL, rejected.outcome)
        assertTrue("the rejection is quoted", rejected.detail.contains("Unclosed"))

        val absent = AiSelfCheck.judge(q, observed(question = q.prompt, answerChars = 300))
        assertEquals(AiSelfCheckOutcome.FAIL, absent.outcome)
        assertTrue(absent.detail.contains("no edit block"))
    }

    @Test
    fun `the run check passes only when the approval card appeared`() {
        val q = step("run")
        assertEquals(AiSelfCheckOutcome.PASS, AiSelfCheck.judge(q, observed(question = q.prompt, runRequested = true)).outcome)
        val v = AiSelfCheck.judge(q, observed(question = q.prompt, runRequested = false, answerChars = 200))
        assertEquals(AiSelfCheckOutcome.FAIL, v.outcome)
        assertTrue(v.detail.contains("never asked to run"))
    }

    /**
     * Phase 93b — the owner's round-2 [5/5]: the model asked with an argument
     * the run tool does not take, the app's policy refused it, and the check
     * blamed the model ("never asked"). The refusal is a fact the app owns, and
     * the line must carry it.
     */
    @Test
    fun `a refused run request fails with the app's refusal, not with silence`() {
        val q = step("run")
        val v = AiSelfCheck.judge(
            q,
            observed(
                question = q.prompt,
                runRequested = false,
                answerChars = 0,
                runRefusedReason = "request_run does not take command"
            )
        )
        assertEquals(AiSelfCheckOutcome.FAIL, v.outcome)
        assertTrue("the refusal is quoted", v.detail.contains("request_run does not take command"))
        assertFalse("and the model is not blamed for it", v.detail.contains("never asked"))
    }

    /**
     * Phase 93b — the scripted question must match the tool that exists:
     * `request_run(target?)`, a request to run the project. Asking for a shell
     * command was the round-2 trap — the allow-list has no `command` key, so the
     * only request the model could build was refused before the card.
     */
    @Test
    fun `the run check asks for the request the run tool can actually deliver`() {
        val prompt = step("run").prompt!!
        assertTrue("the tool is named", prompt.contains("request_run"))
        assertTrue("it asks for a project run", prompt.contains("run this project"))
        assertFalse("it must not ask for a shell command", prompt.contains("shell"))
        assertFalse("nor invent a command key", prompt.contains("command"))
    }

    // ---- the run, the live verdict and the report ---------------------------

    @Test
    fun `the live verdict is the step being run, and nothing once the run is over`() {
        val run = AiSelfCheck.Run(stepIndex = 1)
        assertNull("waiting for Send", AiSelfCheck.liveVerdict(run, observed(question = null, settled = false)))
        val settled = AiSelfCheck.liveVerdict(run, observed(question = step("remember").prompt))
        assertNotNull(settled)
        assertEquals(AiSelfCheckOutcome.PASS, settled!!.outcome)
        assertNull("a finished run has no live step", AiSelfCheck.liveVerdict(AiSelfCheck.Run(stepIndex = 5), observed()))
    }

    @Test
    fun `progress and finish are read from the step index`() {
        assertEquals("Check 1 of 5", AiSelfCheck.progressLabel(AiSelfCheck.Run(stepIndex = 0)))
        assertEquals("Check 5 of 5", AiSelfCheck.progressLabel(AiSelfCheck.Run(stepIndex = 4)))
        assertFalse(AiSelfCheck.isFinished(AiSelfCheck.Run(stepIndex = 4)))
        assertTrue(AiSelfCheck.isFinished(AiSelfCheck.Run(stepIndex = 5)))
        assertNull(AiSelfCheck.stepAt(AiSelfCheck.Run(stepIndex = 5)))
    }

    @Test
    fun `the report counts what the checks found, and says which failed`() {
        val run = AiSelfCheck.Run(
            stepIndex = 4,
            verdicts = listOf(
                AiSelfCheckVerdict("access", AiSelfCheckOutcome.PASS, "all-files access granted (API 34)"),
                AiSelfCheckVerdict("remember", AiSelfCheckOutcome.PASS, "answered (2 chars, 2400 sent)"),
                AiSelfCheckVerdict("recall", AiSelfCheckOutcome.FAIL, "carried 0 chars of conversation — the app, not the model"),
                AiSelfCheckVerdict("proposal", AiSelfCheckOutcome.FAIL, "no edit block came back (answer 300 chars)")
            )
        )
        val report = AiSelfCheck.report("1.3.17", observed(question = step("run").prompt, runRequested = true), run)
        assertTrue("the app version is named", report.contains("1.3.17"))
        assertTrue("the device line is there", report.contains("Android API 34"))
        assertTrue("with both storage facts", report.contains("all-files access: granted") && report.contains("storage permission: "))
        assertTrue("the recipient is there", report.contains("nvidia/nemotron-3-super-120b-a12b"))
        assertTrue("every check is listed", report.contains("[5/5]"))
        assertTrue("the failures keep their reasons", report.contains("the app, not the model"))
        assertTrue("the live step is included", report.contains("run approval card appeared"))
        assertTrue("the totals are honest", report.contains("Result: 3 passed, 2 failed, 0 not run."))
    }

    @Test
    fun `the report never carries a prompt, the code word, an answer or a key`() {
        val run = AiSelfCheck.Run(
            stepIndex = 3,
            verdicts = listOf(
                AiSelfCheckVerdict("access", AiSelfCheckOutcome.PASS, "all-files access granted (API 34)"),
                AiSelfCheckVerdict("remember", AiSelfCheckOutcome.PASS, "answered (2 chars, 2400 sent)"),
                AiSelfCheckVerdict("recall", AiSelfCheckOutcome.FAIL, "carried 1800 chars of conversation; the model did not use it (answer 41 chars)")
            )
        )
        val report = AiSelfCheck.report("1.3.17", observed(question = step("proposal").prompt, answerChars = 512), run)
        for (s in steps) {
            s.prompt?.let { assertFalse("the report must not quote a check's question", report.contains(it)) }
        }
        assertFalse("and not the code word", report.contains(AiSelfCheck.CODE_WORD))
        assertFalse("nor anything about a key but whether one is saved", report.contains("nvapi-"))
        assertTrue("while still saying a key is saved", report.contains("API key saved: yes"))
    }

    @Test
    fun `a skipped check is PENDING and the report counts it as not run`() {
        val step = step("recall")
        val v = AiSelfCheck.skipped(step)
        assertEquals("the step's own id", "recall", v.stepId)
        assertEquals("never a pass, never a failure", AiSelfCheckOutcome.PENDING, v.outcome)
        assertTrue("and the line says why", v.detail.contains("skipped"))
        assertFalse("the word 'pass' never appears", v.detail.contains("pass"))
        val run = AiSelfCheck.Run(stepIndex = 3, verdicts = listOf(AiSelfCheck.skipped(step)))
        val report = AiSelfCheck.report("1.3.17", observed(), run)
        assertTrue("the report counts it out loud", report.contains("0 passed, 0 failed, 5 not run."))
        assertTrue("and its own line is not a PASS", report.contains("[3/5] Follow-up uses it"))
        assertFalse(
            "no PASS mark anywhere in a run that never ran",
            report.lines().any { it.startsWith("[") && it.contains("PASS") }
        )
    }

    @Test
    fun `checks that were never reached are counted as not run`() {
        val run = AiSelfCheck.Run(
            stepIndex = 1,
            verdicts = listOf(AiSelfCheckVerdict("access", AiSelfCheckOutcome.PASS, "all-files access granted (API 34)"))
        )
        val report = AiSelfCheck.report("1.3.17", observed(question = null, settled = false), run)
        assertTrue("the reached check is counted", report.contains("1 passed"))
        assertTrue("and the rest are honestly not run", report.contains("0 failed, 4 not run."))
        assertTrue("every line still names its check", report.contains("[5/5]"))
    }
}
