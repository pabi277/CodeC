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
        val card = sheet.substringAfter("private fun SelfCheckCard(").substringBefore("private fun TurnCopy(")
        assertFalse("and never through a share sheet", card.contains("Intent("))
        assertFalse("never through a file", card.contains("File("))
    }

    @Test
    fun `the check reports permissions, it never asks for one`() {
        val observed = vm.substringAfter("private fun selfCheckObserved()").substringBefore("Phase 90 — a settled task")
        assertTrue("the all-files state is read", observed.contains("Environment.isExternalStorageManager()"))
        assertTrue("the legacy permission is read", observed.contains("checkSelfPermission("))
        assertFalse("no dialog is opened", observed.contains("requestPermissions"))
        assertFalse("nothing is launched", observed.contains("startActivity"))
        assertTrue("and the API level is part of the verdict", observed.contains("sdkInt = Build.VERSION.SDK_INT"))
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
        assertTrue("and so are its three controls", sheet.contains("onSelfCheckNext") && sheet.contains("onSelfCheckStop"))
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
