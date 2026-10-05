package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 93 — the wiring pins for the owner's five items and the three defects
 * this phase fixed.
 *
 * The pure rules live in `StorageAccessTest` (permissions), `AiChatSessionTest`
 * (the transcript budget) and `AiSelfCheckTest` (the verdicts). These cases pin
 * where the wiring can silently drift back:
 *
 *  1. the arrow is the last control and the only send — [Send] is not a button;
 *  2. an AI edit that cannot write names the permission and the switch;
 *  3. one storage answer for the whole app, and the manifest capped at API 32;
 *  4. the Projects home greets instead of reporting an absence;
 *  5. one conversation per project, in memory, and D6 still holds;
 *  6. the run-request latch, so the fifth self-check can actually pass;
 *  7. the refusal that used to be invisible while an answer was arriving.
 */
class Phase93WiringTest {

    private fun raw(name: String) =
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai/$name").readText()

    private fun src(name: String) = RepoFiles.codeOnly(raw(name))

    private val vm = src("AiViewModel.kt")
    private val sheet = src("AiChatSheet.kt")
    private val copy = raw("AiCopy.kt")
    private val bottomBar = sheet.substringAfter("private fun BottomBar(").substringBefore("private fun Composer(")
    private val conversation = sheet.substringAfter("private fun Conversation(").substringBefore("private fun progressInput(")

    // ---- item 1: one arrow, and it is the last button -----------------------

    @Test
    fun `the preview bar sends through the arrow, not a Send button`() {
        assertFalse("no [Send] button in the bar", bottomBar.contains("Button(onClick = onSend)"))
        assertFalse("no Send label in the bar", bottomBar.contains("Text(AiCopy.SEND)"))
        val preview = bottomBar.substringAfter("AiPhase.PREVIEW ->").substringBefore("AiPhase.STREAMING ->")
        assertTrue("the arrow takes the preview's own action", preview.contains("onSend()"))
        assertTrue("and the exact text stays one tap away", raw("AiChatSheet.kt").contains("it.systemInstruction + \"\\n\\n\" + it.userText"))
    }

    @Test
    fun `the arrow is drawn in every phase and is always the last control in its row`() {
        assertTrue("one arrow composable, so the control cannot drift", sheet.contains("private fun ArrowButton("))
        val arrow = sheet.substringAfter("private fun ArrowButton(").substringBefore("private fun storageFixRow(")
        assertTrue("it is an IconButton", arrow.contains("IconButton("))
        assertTrue("with the send icon", arrow.contains("Icons.AutoMirrored.Filled.Send"))
        // Every ArrowButton call closes its row: no control may follow it.
        val calls = Regex("ArrowButton\\(").findAll(sheet).count() - 1 // minus the declaration
        assertTrue("the arrow is used where the flow needs it", calls >= 3)
        assertTrue(
            "and each call is the last child of its Row",
            Regex("ArrowButton\\([\\s\\S]{0,700}?\\n\\s*\\)\\n\\s*\\}\\n\\s*\\}").findAll(sheet).count() >= 3
        )
        assertTrue("it says what the tap does", copy.contains("const val SEND_ARROW_HINT = "))
        assertTrue("and the preview's own words", copy.contains("const val SEND_PREVIEW = "))
    }

    @Test
    fun `the arrow still means the phase-78 rule when nothing is previewed`() {
        val arrow = sheet.substringAfter("private fun ArrowButton(").substringBefore("private fun storageFixRow(")
        assertTrue("the arrow forwards the caller's tap", arrow.contains("IconButton(onClick = onTap, enabled = enabled)"))
        assertTrue(
            "selection wins, in the composer and mid-stream",
            Regex("if \\(hasSelection\\) onExplainSelection\\(question\\) else onAskProject\\(question\\)")
                .findAll(sheet).count() >= 2
        )
        assertTrue("and the preview never re-asks", sheet.contains("onSend()"))
    }

    // ---- items 2 + 3: the permission is named, once, before the failure ----

    @Test
    fun `an AI edit that cannot write names the permission, not a mystery`() {
        val applier = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/projects/AiEditApplier.kt").readText()
        assertTrue("apply checks the folder", applier.contains("if (!root.canRead() || !root.canWrite())"))
        assertTrue("undo too", applier.contains("STORAGE_PERMISSION_MESSAGE"))
        assertTrue(
            "pointing at the screen that fixes it",
            applier.contains("Android Settings (Settings → Apps → CodeC → Permissions)")
        )
        // The version-exact switch is named where the Android version is known:
        assertTrue("the preflight names the switch", vm.contains("StorageAccessPolicy.fixSteps(facts)"))
        assertTrue("and the statement is public for the other doors", applier.contains("val STORAGE_PERMISSION_MESSAGE: String"))
    }

    @Test
    fun `the preflight refuses before a request and offers the fix`() {
        assertTrue("the door check exists", vm.contains("private fun storageProblemFor(root: File, source: AiSource): String?"))
        assertTrue("it runs in the agent door", vm.contains("storageProblemFor(root, source)?.let { problem ->"))
        assertTrue("and before an apply", vm.contains("storageProblemFor(root, AiSource.PROPOSE_EDITS)?.let { problem ->"))
        assertTrue("a proposal needs the write side", vm.contains("if (source == AiSource.PROPOSE_EDITS) facts.canWrite else facts.canRead"))
        assertTrue("CodeC's own storage is never gated", vm.contains("StorageAccessPolicy.needsExternalAccess(root.absolutePath, privateRoots)"))
        assertTrue("the notice carries its own kind", vm.contains("storageProblem = true"))
        assertTrue("and the flag clears with every new preview", vm.contains("storageProblem = false"))
        assertTrue("the sheet offers the one tap", sheet.contains("private fun storageFixRow(") && sheet.contains("AiCopy.GRANT_ACCESS"))
        val editor = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
        assertTrue("which is a real request, on the editor", editor.contains("onGrantAccess = { (context as? MainActivity)?.requestStoragePermissions() }"))
    }

    @Test
    fun `every storage reader asks the same object`() {
        val shell = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/terminal/ShellEnvironment.kt").readText()
        val activity = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/MainActivity.kt").readText()
        val android = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/projects/StorageAccessAndroid.kt").readText()
        val settings = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt").readText()

        // Phase 93 — ONE Android adapter, so the reads cannot drift apart …
        assertTrue("the adapter owns the reads", android.contains("fun read(context: Context): StorageFacts = StorageAccessPolicy.facts("))
        assertTrue("and the modern switch's page", android.contains("fun openAllFilesSettings(context: Context)"))
        // … and every caller goes through it.
        assertTrue("the terminal gate", shell.contains("StorageAccessAndroid.granted(context)"))
        assertTrue("the runtime request", activity.contains("val facts = StorageAccessAndroid.read(this)"))
        assertTrue("the AI", vm.contains("private fun storageFacts(app: Application): StorageFacts = StorageAccessAndroid.read(app)"))
        assertTrue("the storage screen", settings.contains("val facts = StorageAccessAndroid.read(context)"))
        // No caller keeps its own copy of the intent, the old duplicated block.
        assertFalse(
            "no second all-files intent outside the adapter",
            activity.contains("ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION") ||
                settings.contains("ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION")
        )
        // The self-check verdict is built from the same facts object.
        assertTrue("built from the facts", src("AiSelfCheck.kt").contains("val facts = observed.storageFacts()"))
        assertTrue("which the snapshot exposes", src("AiSelfCheck.kt").contains("fun storageFacts(): StorageFacts = StorageAccessPolicy.facts("))
    }

    @Test
    fun `a folder that vanished is not reported as a permission problem`() {
        assertTrue(
            "apply says what happened",
            vm.contains("notice = AiCopy.PROJECT_FOLDER_UNREACHABLE")
        )
        assertFalse(
            "and never blames a missing project on an open project",
            Regex("applying = false, notice = AiCopy[.]NEEDS_PROJECT").containsMatchIn(vm)
        )
        assertTrue("the sentence is honest", copy.contains("const val PROJECT_FOLDER_UNREACHABLE ="))
    }

    // ---- item 4: the Projects home greets --------------------------------

    @Test
    fun `the Projects home welcomes instead of reporting an absence`() {
        val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()
        assertTrue("a greeting", strings.contains("<string name=\"no_projects\">Welcome to CodeC<"))
        assertTrue("and a line that says what to do", strings.contains("Pick a starter below"))
        assertFalse("the old status report is gone", strings.contains("<string name=\"no_projects\">No projects yet<"))
    }

    // ---- item 5: per-project history, still memory-only -------------------

    @Test
    fun `one conversation per project, kept only in memory`() {
        assertTrue("the map is the store", vm.contains("private val chatsByProject = LinkedHashMap<String, ProjectChat>()"))
        assertTrue("saved on the way out", vm.contains("rememberChatForCurrentProject()"))
        assertTrue("restored on the way in", vm.contains("val remembered = chatForProject(name)"))
        assertTrue("New chat clears one project", vm.contains("project?.let { chatsByProject.remove(it) }"))
        assertFalse("D6: nothing is written", vm.contains("chatsByProject") && vm.contains("SharedPreferences"))
        assertTrue(
            "and the dialog says what New chat really does",
            copy.contains("Each project keeps its own") && copy.contains("nothing here is saved to a file")
        )
    }

    // ---- fix 1: the run-request latch ------------------------------------

    @Test
    fun `a run that reached the card is still a run after the task settles`() {
        assertTrue("the latch reads the timeline", vm.contains("private fun runRequested(): Boolean ="))
        assertTrue("including the row", vm.contains("it.kind == AiAgentStepKind.RUN_REQUEST"))
        assertFalse("and never only the pausable card", vm.contains("runRequested = s.agentRun != null"))
        assertTrue("the row is written when the card appears", vm.contains("kind = AiAgentStepKind.RUN_REQUEST"))
        assertTrue("with its own words", copy.contains("const val AGENT_RUN_REQUESTED = "))
        assertTrue("and the snapshot asks the latch", vm.contains("runRequested = runRequested(),"))
    }

    // ---- fix 2: the transcript budget includes its closing line ----------

    @Test
    fun `the transcript budget counts the closing line`() {
        val session = src("AiChatSession.kt")
        assertTrue("the frame is its own function", session.contains("private fun frameChars(keptSize: Int, totalSize: Int): Int"))
        assertTrue("and it counts TURNS_END", session.contains("DATA_NOTE.length + 1 + TURNS_END.length + 1 + 1"))
        assertTrue("the trim loop uses it", session.contains("var total = frameChars(list.size, turns.size)"))
    }

    // ---- fix 3: the refusal is visible while it is happening -------------

    @Test
    fun `a refusal is drawn in the phases that can refuse`() {
        assertTrue(
            "the bar owns the notice exactly while an answer is arriving",
            bottomBar.contains("if (state.notice != null && state.phase == AiPhase.STREAMING) {")
        )
        assertTrue("and draws it as an error line", bottomBar.contains("ErrorLine(state.notice)"))
        assertFalse(
            "so no sentence is drawn over the body's own copy",
            bottomBar.contains("if (state.notice != null && state.phase != AiPhase.DONE)")
        )
        // The notice's own place in every phase the body owns: IDLE, PREVIEW,
        // DONE and FAILED draw it here, STREAMING is the bar's.
        val failed = conversation.substringAfter("AiPhase.FAILED ->")
        assertTrue("FAILED is not left silent", failed.contains("state.notice?.let { Body(it) }"))
        assertTrue("IDLE keeps its copy", conversation.contains("state.notice?.let { ErrorLine(it) }"))
        assertTrue("and DONE keeps its own", conversation.contains("state.notice?.let { Body(it) }"))
        assertTrue("the busy sentence still exists", copy.contains("const val SELF_CHECK_BUSY = "))
    }

    @Test
    fun `a self-check preview keeps its card, so Skip is always reachable`() {
        assertTrue("the sheet knows it is a check", sheet.contains("val checking = state.selfCheck != null && state.phase == AiPhase.PREVIEW"))
        assertTrue("and keeps the card company", sheet.contains("if (checking) {"))
        assertTrue("drawing the exact text behind a disclosure", sheet.contains("AiCopy.SENT_TEXT_SHOW"))
        assertTrue("while the ordinary preview stands down", sheet.contains("AiPhase.PREVIEW -> if (checking) Unit"))
    }

    @Test
    fun `a finished run is not labelled as one still in progress`() {
        val check = raw("AiSelfCheck.kt")
        assertTrue(
            "the done card asks for no count",
            check.contains("if (isFinished(run)) return \"all \${STEPS.size} checks\"")
        )
    }
}
