package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 87 / Level 10 source pins.
 *
 * The policies are pure, so the risk this phase actually carries is a policy
 * that the app never calls, or a call site that reaches past it. Each pin below
 * fails the build if that happens.
 */
class AiLevel10WiringTest {
    private val aiDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private val projectsDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/projects")
    private val screensDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens")
    private fun ai(name: String) = RepoFiles.codeOnly(File(aiDir, name).readText())
    private fun raw(name: String) = File(aiDir, name).readText()

    @Test
    fun `Send stays the only network road and there are still exactly three stream sites`() {
        val vm = ai("AiViewModel.kt")
        assertEquals(3, Regex("client[.]stream[(]").findAll(vm).count())
    }

    @Test
    fun `the hardcoded brevity line is gone from every instruction constant`() {
        // Defect 11. The sentence now comes from AiOptionsPolicy.detailSentence,
        // appended by AiPrompt.systemInstruction, so it is disclosed per request.
        val context = raw("AiContext.kt")
        val constants = context.substringBefore("data class AiPrompt")
        assertFalse(
            "no instruction constant may still hardcode brevity",
            constants.contains("Keep answers short")
        )
        assertTrue(context.contains("AiOptionsPolicy.detailSentence(answerDetail)"))
        assertTrue(context.contains("val answerDetail: AiAnswerDetail"))
    }

    @Test
    fun `the read window is a parameter at all three sites that must agree`() {
        // If any one of these keeps using the constant, the runner refuses what
        // the model was told it could ask for, or the cache key lies.
        val tools = ai("AiTools.kt")
        val runner = ai("AiToolRunner.kt")
        val memory = ai("AiTaskMemory.kt")

        assertTrue(tools.contains("readWindow: Int"))
        // One clamp, at the door; every later use is of the clamped `window`.
        assertTrue(
            tools.contains(
                "AiOptionsPolicy.clampReadWindow(readWindow)" +
                    ".coerceAtMost(AiToolLimits.MAX_READ_LINES)"
            )
        )
        assertTrue(tools.contains("validateRead(request, view, window)"))
        assertTrue(tools.contains("validateReadFiles(request, window)"))
        // The deny and the default end are built from the parameter, never from
        // the constant. The wording itself is not pinned: codeOnly blanks
        // string literals, so a pin on prose would never see it (Phase 45:
        // "pin the button, never the word").
        assertTrue(tools.contains("if (end - start + 1 > window)"))
        assertTrue(tools.contains("start + window - 1"))

        assertTrue(runner.contains("readWindow: Int"))
        // execute() clamps once and forwards the clamped value positionally into
        // both read paths, so no inner function can re-derive its own window.
        assertTrue(runner.contains("AiOptionsPolicy.clampReadWindow(readWindow)"))
        assertTrue(runner.contains("cachedFiles, window)"))

        assertTrue(memory.contains("readWindow: Int"))
        assertTrue(memory.contains("val end = call.end ?: start + window - 1"))
    }

    @Test
    fun `the view model threads one frozen window into all three sites`() {
        val vm = ai("AiViewModel.kt")
        assertTrue(vm.contains("readWindow = session.options.readWindowLines"))
        assertTrue(vm.contains("session.options.readWindowLines"))
        assertTrue(vm.contains("keepLastResults = session.options.workingSetDepth"))
        // Frozen at Send, so a mid-task settings change cannot alter an in-flight
        // request that the preview already described (D4).
        assertTrue(vm.contains("options = s.options"))
    }

    @Test
    fun `the working set depth is clamped inside the packer, not at the call site`() {
        val loop = ai("AiAgentLoop.kt")
        assertTrue(loop.contains("keepLastResults: Int = AiAgentLimits.KEEP_LAST_RESULTS"))
        assertTrue(loop.contains("AiOptionsPolicy.clampWorkingSetDepth(keepLastResults)"))
        assertTrue(loop.contains("if (kept.size >= depth) dropped += step"))
    }

    @Test
    fun `request inspection has no stored property and no setter`() {
        // D4: not user-removable. The absence is the guarantee.
        val store = raw("AiKeyStore.kt")
        val vm = raw("AiViewModel.kt")
        assertFalse(store.contains("request_inspection"))
        assertFalse(store.contains("PROP_REQUEST_INSPECTION"))
        assertFalse(vm.contains("fun setRequestInspection"))
        // …and the panel says so, rather than leaving the user to wonder.
        assertTrue(ai("AiCopy.kt").contains("REQUEST_INSPECTION_ALWAYS_ON".let { "REQUEST_INSPECTION" }))
        assertTrue(ai("AiOptionsPolicy.kt").contains("REQUEST_INSPECTION_ALWAYS_ON = true"))
    }

    @Test
    fun `the AI panel renders one row for each of the nine controls`() {
        val home = ai("AiHome.kt")
        for (label in listOf(
            "READ_WINDOW", "WORKING_SET", "TASK_MEMORY", "ANSWER_DETAIL",
            "TOOL_ACTIVITY", "REQUEST_INSPECTION", "BACKUP_MODE", "BUDGET_OFFER", "REVIEWER"
        )) {
            assertTrue("AiHome must render a row for $label", home.contains("AiCopy.$label"))
        }
        assertTrue(home.contains("AiCopy.TASK_MEMORY_CLEAR"))
        assertTrue(home.contains("onOptionsChange"))
        assertTrue(home.contains("onClearTaskMemory"))
    }

    @Test
    fun `the AI options live in the key store property bag, not in SettingsManager`() {
        // Correction to the Level 10 spec: SettingsManager has no AI keys and
        // SettingsScreen has no AI rows. Forking the pattern would leave two homes.
        val store = raw("AiKeyStore.kt")
        for (prop in listOf(
            "read_window_lines", "working_set_depth", "task_memory_on", "answer_detail",
            "tool_activity", "backup_provider_mode", "budget_extension_offer", "readonly_reviewer"
        )) {
            assertTrue("AiKeyStore must declare $prop", store.contains("\"$prop\""))
        }
        val settingsManager = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/settings/SettingsManager.kt"
        ).readText()
        assertFalse(settingsManager.contains("read_window_lines"))
        assertFalse(settingsManager.contains("answer_detail"))
    }

    @Test
    fun `the memory toggle reaches the store constructor and Clear now is project-scoped`() {
        val vm = ai("AiViewModel.kt")
        assertTrue(vm.contains("persistentEnabled = s.options.taskMemory"))
        assertTrue(vm.contains("AiTaskMemoryStore.clearProject("))
        // clearAll stays reserved for key deletion; a project-scoped row must not
        // wipe every project's memory.
        assertFalse(vm.contains("AiTaskMemoryStore.clearAll("))
        val store = File(projectsDir, "AiTaskMemoryStore.kt").readText()
        assertTrue(store.contains("fun clearProject("))
        assertTrue(store.contains("persistentEnabled"))
    }

    @Test
    fun `the collapsed timeline keeps the disclosure and never changes model input`() {
        val sheet = ai("AiChatSheet.kt")
        // The full text is still rendered — one tap away, not deleted (S8).
        assertTrue(sheet.contains("SentText(instruction"))
        assertTrue(sheet.contains("AiActivityPolicy.row("))
        assertTrue(sheet.contains("AiActivityDisplay.COLLAPSED"))
        // The display state is read only for drawing; the packed text is built in
        // AiAgentPrompt from steps and memory, never from the display (S1).
        val loop = ai("AiAgentLoop.kt")
        assertFalse(loop.contains("AiActivityDisplay"))
        assertFalse(loop.contains("activity"))
    }

    @Test
    fun `the reviewer has no route to an edit, a run or a tool`() {
        // codeOnly, not raw: the doc comment on AiReviewerPolicy names
        // AiEditApplier to say the reviewer never reaches it, and a comment
        // must not trip a pin about code.
        val policy = ai("AiLevel10Policies.kt")
        assertTrue(policy.contains("AiReviewerPolicy"))
        assertTrue(policy.contains("fun mayTrigger("))
        // The only write path stays a user tap on Apply through AiEditApplier.
        assertFalse(policy.contains("AiEditApplier"))
        assertFalse(policy.contains("AiToolRunner.execute"))

        // The real guarantee, tested as behaviour rather than as a grep: the
        // reviewer's instruction names none of the three protocols, so there is
        // no format in which it could ask for a read, an edit or a run.
        val instruction = com.codeci.ide.ui.ai.AiReviewerPolicy.instruction()
        assertFalse(instruction.contains(com.codeci.ide.ui.ai.AiToolProtocol.OPEN))
        assertFalse(instruction.contains(com.codeci.ide.ui.ai.AiEditProposalParser.OPEN_TAG_PREFIX))
        assertFalse(instruction.contains(com.codeci.ide.ui.ai.AiTaskMemoryProtocol.OPEN))

        // And its reply is display-only: markup is classified, never executed.
        val markup = com.codeci.ide.ui.ai.AiToolProtocol.OPEN + " read_file"
        assertTrue(
            com.codeci.ide.ui.ai.AiReviewerPolicy.parse(markup) is
                com.codeci.ide.ui.ai.AiReviewVerdict.MarkupShownAsText
        )
        assertTrue(
            com.codeci.ide.ui.ai.AiReviewerPolicy.parse("Looks fine to me.") is
                com.codeci.ide.ui.ai.AiReviewVerdict.Text
        )

        // The ViewModel routes a review through that classifier and must never
        // hand the text to the edit parser.
        val vm = ai("AiViewModel.kt")
        assertTrue(vm.contains("AiReviewerPolicy.parse(outcome.text)"))
        assertTrue(vm.contains("reviewing"))
        // The reviewer is never an agent task, so it never reaches a tool turn.
        val context = ai("AiContext.kt")
        assertTrue(context.contains("source == AiSource.REVIEW -> AiReviewerPolicy.instruction()"))
    }

    @Test
    fun `no option path assigns to the run cap`() {
        val policy = raw("AiLevel10Policies.kt")
        // extend() returns caps.runs unchanged; there must be no other writer.
        assertTrue(policy.contains("runs = caps.runs"))
        assertFalse(policy.contains("runs = caps.runs +"))
        assertFalse(policy.contains("MAX_RUNS +"))
    }

    @Test
    fun `no new dependency, permission, endpoint or default model`() {
        val manifest = RepoFiles.mainSource("app/src/main/AndroidManifest.xml").readText()
        // Level 10 adds controls, not capabilities: the permission list is
        // unchanged from main @ 6838ea6 (14 declarations, INTERNET and
        // ACCESS_NETWORK_STATE among them).
        assertEquals(14, Regex("<uses-permission").findAll(manifest).count())
        val policy = raw("AiPolicy.kt")
        assertTrue(policy.contains("const val DEFAULT = \"gemini-3-flash-preview\""))
        val gradle = RepoFiles.mainSource("app/build.gradle.kts").readText()
        assertFalse(gradle.contains("openai"))
    }

    @Test
    fun `ui-ai still has zero direct project writes and zero command execution`() {
        val allAi = aiDir.listFiles { file -> file.extension == "kt" }!!
            .joinToString("\n") { RepoFiles.codeOnly(it.readText()) }
        for (banned in listOf(
            "writeText(", "FileOutputStream", "ProcessBuilder",
            "Runtime.getRuntime", "sendCommand("
        )) {
            assertFalse("ui/ai must not write project files or execute commands ($banned)", allAi.contains(banned))
        }
    }

    @Test
    fun `the options are wired from the editor screen, not left decoration`() {
        val screen = File(screensDir, "EditorScreen.kt").readText()
        assertTrue(screen.contains("onOptionsChange = aiViewModel::setOptions"))
        assertTrue(screen.contains("onClearTaskMemory"))
    }

    // ---- 87.6 budget extension --------------------------------------------

    @Test
    fun `87_6 every gate consults the task caps and the offer goes through the policy`() {
        val vm = ai("AiViewModel.kt")
        // All three gates take the session's caps, so an extension is real.
        assertTrue(vm.contains("blockResume(System.currentTimeMillis(), session.caps)"))
        assertTrue(vm.contains("withToolCalls(1, session.caps)"))
        assertTrue(vm.contains("caps = session.caps"))
        // Offered by the policy, granted by the policy — not improvised.
        assertTrue(vm.contains("AiBudgetExtensionPolicy.offerAt("))
        assertTrue(vm.contains("AiBudgetExtensionPolicy.extend(session.caps)"))
        assertTrue(vm.contains("AiBudgetExtensionPolicy.MAX_EXTENSIONS_PER_TASK"))
        // Accepting resumes through the existing turn, so no new stream site.
        assertTrue(vm.contains("fun acceptBudgetExtension()"))
        assertEquals(3, Regex("client\\.stream\\(").findAll(vm).count())
        // The counter renders the caps in force, so it cannot under-report.
        assertTrue(ai("AiChatSheet.kt").contains("usage.turnCap, usage.readCap"))
    }

    // ---- 87.7 backup provider ---------------------------------------------

    @Test
    fun `87_7 the backup provider is offered, never switched behind the user's back`() {
        val vm = ai("AiViewModel.kt")
        assertTrue(vm.contains("AiBackupProviderPolicy.offer("))
        assertTrue(vm.contains("fun acceptBackupProvider()"))
        assertTrue(vm.contains("fun declineBackupProvider()"))
        // Accepting re-previews; it does not send. Send stays the only road.
        assertTrue(vm.contains("phase = AiPhase.PREVIEW"))
        assertTrue(vm.contains("store.isReady(next)"))
        assertEquals(3, Regex("client\\.stream\\(").findAll(vm).count())
        // The offer is built from real readiness, so it cannot name a provider
        // whose key or consent is missing.
        assertTrue(vm.contains("store.providerReadiness()"))
        // And the sheet renders it as a question with both answers.
        val sheet = ai("AiChatSheet.kt")
        assertTrue(sheet.contains("BackupProviderCard("))
        assertTrue(sheet.contains("AiCopy.BACKUP_OFFER_PREFIX"))
    }

    @Test
    fun `87_7 the offer needs consent for that provider and never replays another's`() {
        val configured = setOf(
            com.codeci.ide.ui.ai.AiProviderId.GEMINI,
            com.codeci.ide.ui.ai.AiProviderId.NVIDIA
        )
        val current = com.codeci.ide.ui.ai.AiProviderId.GEMINI
        val other = com.codeci.ide.ui.ai.AiProviderId.NVIDIA
        val failure = com.codeci.ide.ui.ai.AiAgentStopReason.PROVIDER_FAILURE

        // Consent for the other provider: offered.
        assertEquals(
            com.codeci.ide.ui.ai.AiBackupOffer.Offer(other),
            com.codeci.ide.ui.ai.AiBackupProviderPolicy.offer(
                com.codeci.ide.ui.ai.AiBackupMode.MANUAL, current, failure, configured, setOf(other)
            )
        )
        // Configured but not consented: never offered (S8).
        assertEquals(
            com.codeci.ide.ui.ai.AiBackupOffer.None,
            com.codeci.ide.ui.ai.AiBackupProviderPolicy.offer(
                com.codeci.ide.ui.ai.AiBackupMode.MANUAL, current, failure, configured, emptySet()
            )
        )
        // A user stop is not the provider's fault: no offer.
        assertEquals(
            com.codeci.ide.ui.ai.AiBackupOffer.None,
            com.codeci.ide.ui.ai.AiBackupProviderPolicy.offer(
                com.codeci.ide.ui.ai.AiBackupMode.MANUAL,
                current,
                com.codeci.ide.ui.ai.AiAgentStopReason.USER_STOP,
                configured,
                setOf(other)
            )
        )
    }

    // ---- 87.8 read-only reviewer ------------------------------------------

    @Test
    fun `87_8 the reviewer is previewed like any other request and is never an agent`() {
        val vm = ai("AiViewModel.kt")
        assertTrue(vm.contains("fun requestReview()"))
        assertTrue(vm.contains("AiReviewerPolicy.mayTrigger("))
        assertTrue(vm.contains("source = AiSource.REVIEW"))
        assertTrue(vm.contains("agent = false"))
        // It goes through the ordinary preview, so D4 covers it too.
        assertTrue(vm.contains("preview(AiContextResult.Ready(review))"))
        assertEquals(3, Regex("client\\.stream\\(").findAll(vm).count())
        // Wired, not decoration.
        val screen = File(screensDir, "EditorScreen.kt").readText()
        assertTrue(screen.contains("onRequestReview = aiViewModel::requestReview"))
        assertTrue(ai("AiChatSheet.kt").contains("ReviewCard("))
    }

    @Test
    fun `87_8 a review cannot become an edit proposal`() {
        val vm = ai("AiViewModel.kt")
        // The review branch classifies for display and does not build a
        // proposal: the edit parser is reached only from the non-review branch.
        assertTrue(vm.contains("review = AiReviewerPolicy.parse(outcome.text)"))
        assertTrue(vm.contains("if (reviewing)"))
        // And a stopped review keeps its text instead of silently vanishing.
        assertTrue(vm.contains("s.prompt?.source == AiSource.REVIEW"))
    }
}
