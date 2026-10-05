package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 92 — the self-check wiring (the owner: *"give some command and I will run
 * and share what is wrong"*).
 *
 * The pure script and its verdicts are tested in `AiSelfCheckTest`. These are the
 * pins around it: the one command must never become a way to send anything the
 * owner did not approve, it must judge the app honestly, and the report it
 * produces must stay redacted.
 */
class AiSelfCheckWiringTest {

    private fun raw(name: String) =
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai/$name").readText()

    private fun src(name: String) = RepoFiles.codeOnly(raw(name))

    private val vm = src("AiViewModel.kt")
    private val sheet = src("AiChatSheet.kt")

    /** The self-check block of the view model, from its start to the report. */
    private val block = vm.substringAfter("fun startSelfCheck()").substringBefore("private fun settleSelfCheckStep()")

    /** Phase 92.1 — the card itself, so a "never does X" check is scoped to it. */
    private val card = sheet.substringAfter("private fun SelfCheckCard(").substringBefore("private fun TurnCopy(")

    @Test
    fun `the self-check never sends anything by itself`() {
        assertFalse("no Send call in the block", block.contains("send()"))
        assertFalse("no stream call in the block", block.contains("client.stream("))
        assertFalse("and no auto-approval of a run", block.contains("approveRun"))
    }

    @Test
    fun `Send is still the only road, and the check walks through the ordinary doors`() {
        assertEquals("exactly three stream sites", 3, Regex("client[.]stream[(]").findAll(vm).count())
        assertTrue("the ask door is the ordinary one", block.contains("AiSelfCheckDoor.ASK -> agentAsk("))
        assertTrue("the diff door is the ordinary one", block.contains("AiSelfCheckDoor.PROPOSE -> agentPropose("))
        // Every one of those doors ends at a PREVIEW, which is what D4 requires.
        assertTrue("the check previews through preview()", vm.contains("fun preview(result: AiContextResult)"))
    }

    @Test
    fun `a step is judged the moment its own task settles`() {
        val commit = vm.substringAfter("private fun commitFinishedTask()").substringBefore("val asked =")
        assertTrue("settled tasks are judged", commit.contains("settleSelfCheckStep()"))
        assertTrue(
            "and before the commit short-circuit, so clear cannot lose a verdict",
            commit.indexOf("settleSelfCheckStep()") < commit.indexOf("if (s.taskCommitted) return s.session")
        )
        assertTrue("the judge is only reached for a settled task", commit.contains("AiPhase.FAILED) return s.session"))
        assertTrue("it is idempotent", vm.contains("if (live.stepIndex != run.stepIndex) return@update state"))
    }

    @Test
    fun `the snapshot every verdict is built from carries numbers and labels, never an answer`() {
        val observed = src("AiSelfCheck.kt")
            .substringAfter("data class AiSelfCheckObserved(")
            .substringBefore(")\ndata class AiSelfCheckVerdict")
        assertFalse("no answer text field", observed.contains("val answer:"))
        assertFalse("no answer text field under another name", observed.contains("val answerText"))
        assertFalse("no prompt text field", observed.contains("val prompt:"))
        assertTrue("only the count of an answer", observed.contains("val answerChars: Int"))
        assertTrue("and a boolean about the code word", observed.contains("val usedCodeWord: Boolean"))
        assertTrue(
            "the code word is checked where the answer lives",
            vm.contains("usedCodeWord = AiSelfCheck.usedCodeWord(s.answer)")
        )
    }

    @Test
    fun `the report is built by the pure model, from the app version and the snapshot`() {
        assertTrue(vm.contains("AiSelfCheck.report(BuildConfig.VERSION_NAME, selfCheckObserved(), run)"))
        assertFalse("the sheet never builds a report itself", sheet.contains("Result: "))
        assertTrue("the sheet asks the view model for it", sheet.contains("onReport = onSelfCheckReport"))
        // S6: a report leaves through the clipboard, like every other copy in ui/ai.
        assertTrue("the report copies", sheet.contains("copyAnswer(context, onReport())"))
        assertFalse("and never through a share sheet", card.contains("Intent("))
        assertFalse("never through a file", card.contains("File("))
    }

    @Test
    fun `the check reports permissions, it never asks for one`() {
        val observed = vm.substringAfter("private fun selfCheckObserved()").substringBefore("Phase 90 — a settled task")
        // Phase 93 — the reads moved into one adapter (StorageAccessAndroid), so
        // the snapshot asks for the facts rather than repeating the two calls.
        assertTrue("the storage facts are read", observed.contains("storageFacts(app)"))
        assertTrue("the adapter is the one that reads them", vm.contains("StorageAccessAndroid.read(app)"))
        assertTrue("and the API level is part of the verdict", observed.contains("sdkInt = Build.VERSION.SDK_INT"))
        // The rule Phase 92 wrote still holds: the check REPORTS, it never asks.
        assertFalse("no dialog is opened", observed.contains("requestPermissions"))
        assertFalse("nothing is launched", observed.contains("startActivity"))
        assertFalse("and no permission action is called from the check", observed.contains("onGrantAccess"))
    }

    @Test
    fun `the card is drawn in both faces, with one line per check`() {
        assertTrue("the card has a definition", sheet.contains("private fun SelfCheckCard("))
        assertEquals(
            "and exactly one call site (plus the definition)",
            2, Regex("SelfCheckCard[(]").findAll(sheet).count()
        )
        val idx = sheet.indexOf("SelfCheckCard(\n                run = run,")
        assertTrue("the call passes the run", idx > 0)
        assertTrue("from the run in the state", sheet.contains("state.selfCheck?.let { run ->"))
        assertFalse("it is never hidden by the simple face", sheet.substring(idx - 300, idx).contains("if (simple) {"))
        assertTrue("it lists every step", sheet.contains("AiSelfCheck.STEPS.forEachIndexed"))
        assertTrue("and each line comes from the pure model", sheet.contains("AiSelfCheck.verdictAt(run, index, live)"))
    }

    @Test
    fun `the entry is one button in the idle bar that starts nothing but a preview`() {
        assertTrue("the button exists", sheet.contains("TextButton(onClick = onSelfCheckStart)"))
        assertTrue("it is wired in the screen", RepoFiles.codeOnly(
            RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
        ).contains("onSelfCheckStart = aiViewModel::startSelfCheck"))
        assertTrue(
            "and so are its controls",
            sheet.contains("onSelfCheckNext") && sheet.contains("onSelfCheckSkip") && sheet.contains("onSelfCheckStop")
        )
        assertTrue("skip is wired in the screen too", RepoFiles.codeOnly(
            RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
        ).contains("onSelfCheckSkip = aiViewModel::selfCheckSkip"))
    }

    @Test
    fun `every AiCopy name the AI surface uses is declared`() {
        // The SELF_CHECK_TITLE lesson: a missing constant is invisible to a
        // single-file syntax check and to every string pin, and cost a CI round.
        // This pins the whole surface at once -- strings blanked, comments
        // stripped, so only real references are seen.
        val decl = RepoFiles.codeOnly(
            RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai/AiCopy.kt").readText()
        )
        val declared = Regex("""\b(?:val|fun|object|class|interface)\s+([A-Za-z0-9_]+)""")
            .findAll(decl).map { it.groupValues[1] }.toSet()
        val users = RepoFiles.mainKotlinSources().filter {
            it.path.contains("/ui/ai/") || it.name == "EditorScreen.kt" || it.name == "OutputPanelView.kt"
        }
        val dangling = users.flatMap { file ->
            Regex("""\bAiCopy\.([A-Za-z0-9_]+)""")
                .findAll(RepoFiles.codeOnly(file.readText()))
                .map { it.groupValues[1] + " (" + file.name + ")" }
        }.filter { it.substringBefore(' ') !in declared }
        assertTrue("unresolved AiCopy references: $dangling", dangling.isEmpty())
    }

    @Test
    fun `a check waiting for its Send can always be skipped, and the card says so`() {
        // The owner's own round on 2026-10-05: "Not all test run" -- the card sat
        // on "waiting for Send" with no control on it at all. The way on is now
        // unconditional: one button, labelled for what it will actually do.
        assertTrue("the skip label exists", sheet.contains("AiCopy.SELF_CHECK_SKIP"))
        assertTrue(
            "the button is shown for every step that is left, not only a settled one",
            card.contains("if (current != null) {") && card.contains("OutlinedButton(onClick = if (waiting) onSkip else onNext)")
        )
        assertTrue(
            "and what it does is what it says",
            card.contains("val waiting = current != null && current.door != null && live == null") &&
                card.contains("Text(if (waiting) AiCopy.SELF_CHECK_SKIP else AiCopy.SELF_CHECK_NEXT)")
        )
        assertTrue("the hint offers it", raw("AiCopy.kt").contains("or tap Skip check"))
    }

    @Test
    fun `a skipped check is recorded as not run, never as a pass`() {
        val pure = src("AiSelfCheck.kt")
        assertTrue("the pure model has a skip verdict", pure.contains("fun skipped(step: AiSelfCheckStep): AiSelfCheckVerdict"))
        assertTrue(
            "and it is PENDING, which the report counts as not run",
            pure.substringAfter("fun skipped(").substringBefore("/**").contains("AiSelfCheckOutcome.PENDING")
        )
        assertTrue("the view model records it", vm.contains("verdicts = run.verdicts + AiSelfCheck.skipped(step)"))
        assertTrue("the card never calls a finished run all-run", sheet.contains("AiCopy.SELF_CHECK_DONE"))
        assertTrue(
            "and the label itself promises nothing",
            raw("AiCopy.kt").contains("const val SELF_CHECK_DONE = \"finished")
        )
    }

    @Test
    fun `the run moves itself to the next question, so the only tap left is the Send`() {
        assertTrue(
            "the card advances when a verdict lands",
            card.contains("LaunchedEffect(run.stepIndex, live?.detail)")
        )
        assertTrue("after a beat, so the line can be read", card.contains("delay(AUTO_ADVANCE_MS)"))
        assertTrue("through the ordinary Next (which previews, never sends)", card.contains("onNext()"))
        assertTrue("and the constant is a real one", sheet.contains("private const val AUTO_ADVANCE_MS = "))
        // The CI lesson of 57bd267: the const was inserted between a @Composable
        // and its function, so the annotation landed on a property and the debug
        // Kotlin compile failed. The card must stay annotated and the const must
        // stay above the doc comment.
        assertTrue(
            "the card stays @Composable",
            sheet.contains("@Composable\nprivate fun SelfCheckCard(")
        )
        assertFalse(
            "and no annotation lands on a property",
            Regex("@Composable\\n\\s*(private |internal |public )?(const val|val|var)").containsMatchIn(sheet)
        )
        assertTrue(
            "and a finished run is never advanced",
            card.contains("if (live != null && !AiSelfCheck.isFinished(run)) {")
        )
    }

    @Test
    fun `a door that refuses says so instead of going silent`() {
        // The other half of the owner's round: the preview of a step could be
        // refused (an answer still arriving) and the tap vanished without a word.
        assertTrue("busy has a name", vm.contains("private fun selfCheckBusy(s: AiUiState): Boolean ="))
        assertTrue(
            "it is the three states that refuse a door",
            vm.contains("s.phase == AiPhase.STREAMING || s.gathering || s.applying")
        )
        assertTrue(
            "Next and Skip both say it instead of returning in silence",
            Regex("AiCopy[.]SELF_CHECK_BUSY").findAll(vm).count() == 2
        )
        assertTrue("the sentence exists", raw("AiCopy.kt").contains("const val SELF_CHECK_BUSY = "))
    }

    @Test
    fun `the run is memory-only, and a new conversation means a new check`() {
        assertTrue("it lives in the state", vm.contains("val selfCheck: AiSelfCheck.Run? = null = null") ||
            vm.contains("val selfCheck: AiSelfCheck.Run? = null"))
        assertFalse("the key store never learns about it", src("AiKeyStore.kt").contains("selfCheck"))
        assertFalse("nor does the task store", src("AiTaskMemory.kt").contains("selfCheck"))
        assertTrue("New chat resets it (the follow-up step reads the conversation)",
            vm.contains("taskCommitted = false, selfCheck = null"))
        assertTrue("and so does a project switch", Regex("selfCheck = null").findAll(vm).count() >= 2)
    }
}
