package com.codeci.ide

import com.codeci.ide.ui.ai.AiCopy
import com.codeci.ide.ui.ai.AiToolName
import com.codeci.ide.ui.ai.AiToolProtocol
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 96 — the bug sweep after the owner's Phase 95 round on his phone.
 *
 * Nothing here opens a feature. Every case pins a behaviour the Phase 95 device
 * list (D1–D11) and the Phase 94 list (D1–D8) claim, and which the code had
 * quietly stopped keeping in one of four places: the ORDER of two statements
 * (a project switch, a chat switch), a control drawn in one of two copies (the
 * drawer's New-chat pill), a flag that only flowed one way (the drawer's open
 * state), or a guard applied on one route and not its twin (the value-level
 * secret scan on the run-output chip).
 *
 * Source pins, in the house style — these files are Compose/Android and only
 * compile under Gradle, so what a host JVM can prove is the shape of the code
 * that runs. The pure half of the same claims lives in `AiChatHistoryTest`.
 */
class Phase96WiringTest {

    private val aiDir = "app/src/main/java/com/codeci/ide/ui/ai"

    private fun src(name: String): String = RepoFiles.mainSource("$aiDir/$name").readText()

    /** Slices one function's own text, so a pin reads the code it names and not the file. */
    private fun between(source: String, start: String, end: String, label: String = "the slice"): String {
        val from = source.indexOf(start)
        val to = source.indexOf(end, from + start.length)
        assertTrue("$label is where it was promised", from >= 0)
        assertTrue("$label ends before $end", to > from)
        return source.substring(from, to)
    }

    // ---- D9: the project switch, and who a settled task belongs to --------

    @Test
    fun `a project switch files the conversation under the project being left`() {
        val fn = between(src("AiViewModel.kt"), "fun onProjectChanged(name: String?) {", "// ---- Phase 95: the history drawer")
        // `clear()` settles the finished task and files it under `project`; the
        // pointer must still name the LEAVING project while it runs. Round 1 of
        // Phase 95 reassigned `project` first, so an answer that had landed but
        // not yet been committed was written into the ARRIVING project's slot —
        // on top of the leaving project's list when the arriving one had none.
        // D9 ("B never shows A's chats") and A's own last row both lived here.
        val clearAt = fn.indexOf("clear()")
        val movedAt = fn.indexOf("project = name")
        assertTrue("the task is settled before the pointer moves", clearAt in 0 until movedAt)
        assertTrue("and the archive happens under the old name",
            fn.indexOf("rememberChatForCurrentProject()") in clearAt until movedAt)
        assertTrue("the arriving history is read only after both", fn.indexOf("historyForProject(name)") > movedAt)
        assertTrue("a switch closes the drawer", fn.contains("historyOpen = false"))
    }

    @Test
    fun `switching chats settles before it moves the current id`() {
        val fn = between(src("AiViewModel.kt"), "fun switchChat(id: Long) {", "fun toggleChatPin(id: Long)")
        // The same ordering law as above, one level down: `commitFinishedTask`
        // writes into the entry named by `currentId`, so switching first made the
        // tapped chat receive the PREVIOUS chat's last exchange — the merge the
        // drawer promises never to happen, and it reached the stored history.
        assertTrue("the settle happens first", fn.indexOf("clear()") < fn.indexOf(".switchTo(id)"))
        assertFalse("no archive before the settle", fn.contains("withCurrent") && fn.indexOf("withCurrent") < fn.indexOf("clear()"))
        assertTrue("and the tapped row is restored whole", fn.contains("session = saved.session()"))
    }

    // ---- D8: one New chat, one question, drawn where the tap was ------------

    @Test
    fun `the header plus and the drawer pill ask the same question once`() {
        val sheet = src("AiChatSheet.kt")
        // One confirm, shared: the header `+` asked and the drawer's pill asked
        // nothing, so the same sentence was a gate in one place and a silent
        // archive in the other. The count is the pin — a second dialog would let
        // the two copies drift apart.
        assertEquals("exactly one New-chat question in the sheet", 1, Regex("AiCopy\\.NEW_CHAT_TITLE").findAll(sheet).count())
        assertTrue("the header asks through it", sheet.contains("onExpand, onMinimize, requestNewChat, onToggleMode,"))
        assertTrue("so does the pill", sheet.contains("onNewChat = requestNewChat,"))
        assertTrue("with the honest body", sheet.contains("text = { Text(AiCopy.NEW_CHAT_BODY) }"))
        assertTrue("and the answer is the VM's archive", sheet.contains("newChatConfirming = false; onNewChat()"))
        assertEquals("no second confirm dialog in the header", 1, Regex("AlertDialog\\(").findAll(sheet).count())
    }

    @Test
    fun `the pill leaves the drawer open so the owner sees where the chat went`() {
        val drawer = between(src("AiChatSheet.kt"), "ModalNavigationDrawer(", "@Composable\nprivate fun Header(")
        // D8: "archives the current chat, opens an empty composer, and shows the
        // drawer so you can see where it went". The pill closed the drawer it had
        // just filled — the one moment the list was worth looking at.
        val pill = drawer.substringAfter("onNewChat = requestNewChat,").substringBefore("onClose =")
        assertFalse("the pill does not close the drawer", pill.contains("drawerState.close()"))
        assertFalse("and does not clear the flag", pill.contains("onCloseHistory()"))
        assertTrue("the switch DOES close it, after landing the chat",
            drawer.substringAfter("onSwitch = { id ->").substringBefore("onTogglePin").contains("drawerState.close()"))
        assertTrue("the new chat opens it from the model side",
            src("AiViewModel.kt").substringAfter("fun newChat()").substringBefore("fun switchChat")
                .contains("historyOpen = true"))
    }

    // ---- D5: the drawer's flag is a fact, not a wish ------------------------

    @Test
    fun `the drawer's open flag follows the drawer back`() {
        val sheet = src("AiChatSheet.kt")
        // A scrim tap or a swipe closes a ModalNavigationDrawer without asking the
        // app. `historyOpen` stayed true, and ☰ (which only sets it) had nothing
        // left to act on — the drawer stopped opening until something else closed
        // it. The settled value is synced back, in one direction only, because
        // the model owns "open" and the gesture owns "closed".
        assertTrue("the sheet watches the settled value", sheet.contains("LaunchedEffect(drawerState.currentValue) {"))
        assertTrue("and reports a close", sheet.contains("if (drawerState.currentValue == DrawerValue.Closed) onCloseHistory()"))
        assertFalse("never an open (the header keeps that door)",
            Regex("currentValue == DrawerValue.Open\\) onClose").containsMatchIn(sheet))
        assertTrue("the model still drives the open", sheet.contains("if (state.historyOpen && drawerState.isClosed) drawerState.open()"))
    }

    // ---- Phase 94 round 3: the refused write's one-tap fix, in every phase ----

    @Test
    fun `the grant row is drawn once in every phase that can refuse a write`() {
        val sheet = src("AiChatSheet.kt")
        assertEquals("one fix row per phase, exactly two draw sites", 2,
            Regex("storageFixRow\\(state, onGrantAccess\\)").findAll(sheet).count())
        // The bar owns every phase but DONE; DONE owns it in the body, beside the
        // proposal and the undo card it refused. Neither copy is a duplicate: the
        // bar skips exactly where the body draws.
        val bar = between(sheet, "private fun BottomBar(", "// ---- Phase 95: Welcome + agreement")
        assertTrue("the bar skips DONE on purpose", bar.contains("if (state.phase != AiPhase.DONE) storageFixRow(state, onGrantAccess)"))
        val done = between(sheet, "private fun Conversation(", "private fun ProposalReviewCard(")
        val doneBranch = done.substringAfter("AiPhase.DONE ->")
        assertTrue("DONE draws it beside its own notice",
            doneBranch.contains("state.notice?.let { Body(it) }") && doneBranch.contains("storageFixRow(state, onGrantAccess)"))
        assertTrue("and the notice comes first, so the row answers it",
            doneBranch.indexOf("state.notice?.let { Body(it) }") < doneBranch.indexOf("storageFixRow(state, onGrantAccess)"))
        val failed = doneBranch.substringAfter("AiPhase.FAILED ->")
        assertFalse("FAILED is the bar's, not a third copy", failed.contains("storageFixRow("))
        // Both setters still ask the policy, never a sentence (Phase 94 round 3).
        val vm = src("AiViewModel.kt")
        assertEquals("exactly two places set the flag from the policy", 2,
            Regex("storageProblem = problem != null").findAll(vm).count())
        assertTrue("the tap is a real door", RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt")
            .readText().contains("onGrantAccess = { (context as? MainActivity)?.requestStoragePermissions() }"))
    }

    @Test
    fun `the welcome card gates the surface and nothing else`() {
        val sheet = src("AiChatSheet.kt")
        // D1–D3: before the tap there is no composer, no chip, no arrow and no
        // drawer gesture. And it must NOT become a second permission gate — the
        // refused-write row and the AI home (where a key is saved) live outside
        // it, so accepting nothing still leaves every other door open.
        assertTrue("one card, drawn instead of the surface", sheet.contains("if (!state.welcomeAccepted) {"))
        assertTrue("with one accept tap", sheet.contains("WelcomeCard(onAccept = onAcceptWelcome)"))
        assertTrue("and no drawer swipe behind it", sheet.contains("gesturesEnabled = state.welcomeAccepted"))
        val welcome = between(sheet, "private fun WelcomeCard(", "// ---- Phase 95: the history drawer")
        assertFalse("the card draws no composer", welcome.contains("Composer("))
        assertFalse("no arrow", welcome.contains("ArrowButton("))
        assertFalse("and no send of its own", welcome.contains("onSend()"))
        assertTrue("the accept is the only control", welcome.contains("onClick = onAccept"))
        // The flag lands with `keySaved` in one update, so the sheet cannot be
        // drawn at all before it is known: no flash of the card on a reopen (D2).
        val init = between(src("AiViewModel.kt"), "init {", "// ---- Phase 77: the chat sheet")
        assertTrue("keySaved and welcomeAccepted arrive together",
            init.contains("keySaved = ready") && init.contains("welcomeAccepted = welcomed"))
        assertEquals("in exactly one state update", 1, Regex("_state\\.update").findAll(init).count())
    }

    // ---- Phase 95 item 3: the composer, on every road to Send --------------

    @Test
    fun `every route that can reach Send clears the composer`() {
        val editor = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
        // The clear belongs to Send, not to a chip: an explain-preview and an
        // error-preview land on the same arrow, so they clear the same way. One
        // `send()` call site is the whole claim — a second door to the network
        // that forgot the clear would put the bug back.
        assertEquals("Send is called once, from the arrow", 1, Regex("aiViewModel\\.send\\(\\)").findAll(editor).count())
        assertTrue("and it clears on the way", editor.contains("onSend = { aiQuestion = \"\"; aiViewModel.send() }"))
        // The two preview chips the owner named, plus the two agent chips: all of
        // them end at a preview, and a preview is the only thing that can show the
        // arrow.
        assertEquals("both chip previews go through preview()", 2, Regex("aiViewModel\\.preview\\(").findAll(editor).count())
        assertTrue("the agent doors too", editor.contains("aiViewModel.agentAsk(") && editor.contains("aiViewModel.agentPropose("))
        val sheet = src("AiChatSheet.kt")
        assertTrue("the composer's arrow never sends", !sheet.substringAfter("private fun Composer(").substringBefore("@Composable\nprivate fun ArrowButton").contains("onSend()"))
        assertTrue("the preview's arrow always does", sheet.substringAfter("AiPhase.PREVIEW ->").contains("onSend()"))
    }

    // ---- D6/D7: the drawer's rows ------------------------------------------

    @Test
    fun `the drawer says which row is on screen`() {
        val sheet = src("AiChatSheet.kt")
        // HistoryRow is the file's last function, so the tail IS the function.
        val row = sheet.substringAfter("private fun HistoryRow(")
        assertTrue("the current row is marked", row.contains("if (row.current) {"))
        assertTrue("and the mark is named for a screen reader", row.contains("contentDescription = AiCopy.CURRENT_CHAT"))
        assertTrue("an unmarked row keeps the indent", row.contains("Spacer(Modifier.width("))
        assertTrue("the copy exists", src("AiCopy.kt").contains("const val CURRENT_CHAT = \"Current chat\""))
        assertEquals("a row never draws a message body", 1, Regex("row\\.title").findAll(row).count())
    }

    @Test
    fun `pinned and recents are still two sections built from the flag`() {
        val sheet = src("AiChatSheet.kt")
        val drawer = sheet.substringAfter("private fun HistoryDrawer(")
        assertTrue("pinned first", drawer.contains("val pinned = summaries.filter { it.pinned }"))
        assertTrue("recents after", drawer.contains("val recents = summaries.filterNot { it.pinned }"))
        assertTrue("and a row's own id is the key", drawer.contains("items(pinned, key = { it.id })"))
        assertFalse("no body text in a row", drawer.contains("session"))
    }

    // ---- Phase 94's guard on the route Phase 95 left raw --------------------

    @Test
    fun `the run-output chip is scrubbed the way the run-output tool is`() {
        val context = src("AiContext.kt")
        val fn = between(context, "fun fromRunOutput(", "fun fromProject")
        // Same bytes, two doors: `read_run_output` redacts a credential-shaped
        // value and says so, while the *Explain last error* card sent the panel
        // tail raw. The scan now runs on that route too, after the refusal and
        // before the budget, so D4's "this is exactly what leaves" still holds.
        assertTrue("the guard runs here", fn.contains("AiSecretScan.redact(cleaned.joinToString(\"\\n\")).text.split('\\n')"))
        assertTrue("after the honest refusal", fn.indexOf("NO_FAILED_RUN") < fn.indexOf("AiSecretScan.redact"))
        assertTrue("and before the ceiling is measured",
            fn.indexOf("AiSecretScan.redact") < fn.indexOf("MAX_OUTPUT_LINES"))
        assertTrue("the kept lines are the scrubbed ones", fn.contains("ArrayDeque(scrubbed.takeLast("))
        // The selection route is deliberately NOT rewritten: the owner chose that
        // text and reads it word-for-word in the preview.
        val selection = between(context, "fun fromSelection(", "fun fromRunOutput")
        assertFalse("a selection is not rewritten", selection.contains("AiSecretScan"))
    }

    @Test
    fun `no new door to the network, and nothing new written`() {
        val files = File(RepoFiles.root(), aiDir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val streams = files.sumOf { Regex("client\\.stream\\(").findAll(it.readText()).count() }
        assertEquals("still exactly three stream sites", 3, streams)
        // Comments are blanked first: a sentence promising "no DataStore" must not
        // be read as a use of one (and must not hide a real use either).
        val vm = RepoFiles.codeOnly(src("AiViewModel.kt"))
        listOf("writeText(", "dataStore", "DataStore", "SharedPreferences", "Log.").forEach { banned ->
            assertFalse("the view model never touches $banned", vm.contains(banned))
        }
        val history = src("AiChatHistory.kt")
        assertFalse("the history model stays pure: no java.io", history.contains("import java.io"))
        assertFalse("no android", history.contains("import android"))
        assertFalse("no clock", history.contains("currentTimeMillis"))
    }

    // ---- the read surface, widened and taught ------------------------------

    @Test
    fun `search_project scopes like list_files and the timeline says so`() {
        val tools = src("AiTools.kt")
        assertTrue(
            "both scoping keys are allowed for a search",
            tools.contains("AiToolName.SEARCH_PROJECT -> setOf(\"query\", \"max\", \"path\", \"ext\")")
        )
        assertTrue("the model is taught the scope", tools.contains("search_project(query, max?, path?, ext?)"))
        // The two sentences a search row is made of live in the source text, and
        // `$` is a template marker in Kotlin, so the needles are assembled here.
        val dollar = "\u0024"
        val searchRow = between(
            between(tools, "fun describeCall", "fun describe("), "SEARCH_PROJECT ->", "LIST_FILES ->"
        )
        assertTrue("the reviewed row names the folder scope", searchRow.contains("in " + dollar + "it/"))
        assertTrue("the reviewed row names the extension scope", searchRow.contains("call.ext"))
        val runner = src("AiToolRunner.kt")
        val search = between(runner, "private fun search(", "// ---- read_file")
        assertTrue(
            "the filter runs over the admitted list only",
            search.contains("for (relative in scoped.sorted())")
        )
        assertTrue("the header names the scope it applied", search.contains(dollar + "scope —"))
    }

    // ---- round 2: the room the first screens need ---------------------------

    @Test
    fun `the welcome card pins its button outside the scrolling copy`() {
        val card = between(src("AiChatSheet.kt"), "private fun WelcomeCard", "// ---- Phase 95: the history drawer")
        assertEquals("one scroll area, and it holds only the copy", 1, card.split("verticalScroll(").size - 1)
        assertTrue("the copy column gives way to the button", card.contains(".weight(1f)"))
        assertTrue(
            "the accept button is drawn after the scroll area closes",
            card.indexOf("Button(") > card.indexOf("verticalScroll(")
        )
    }

    @Test
    fun `the sheet asks the policy for its room, in the ViewModel and on accept`() {
        val vm = src("AiViewModel.kt")
        val open = between(vm, "fun openSheet(", "fun sheetEvent(")
        assertTrue(
            "opening hands the policy both facts it decides on",
            open.contains("s.welcomeAccepted, s.session.turns.size")
        )
        assertTrue(
            "accepting the agreement does not drop him back into a strip",
            vm.contains("AiSheetPolicy.needsFullRoom(true, it.session.turns.size)")
        )
        val policy = src("AiSheetPolicy.kt")
        assertTrue("one rule, in the policy — not a second model", policy.contains("fun needsFullRoom(welcomeAccepted: Boolean, turns: Int)"))
        assertTrue(
            "a conversation with turns still answers the Phase 77 variants",
            policy.contains("conflict == AiOutputConflict.REPLACE_OUTPUT -> AiSheetState.HALF to true")
        )
    }

    /**
     * The drawer row used to appear only when something *looked* for the settled
     * task (✕, New chat, a switch, the next Send). Now every terminal phase write
     * files it, so the pins are about the shape of that set: five bare calls, and
     * every measured end of a task is also a filing.
     */
    @Test
    fun `a settled task is filed where it settles, not when the next question needs it`() {
        val lines = src("AiViewModel.kt").lines()
        assertEquals(
            "finishAgent, finalizeStop, send, stop and clear each file the task",
            5,
            lines.count { it.trim() == "commitFinishedTask()" }
        )
        var stamped = 0
        for (i in lines.indices) {
            if (lines[i].trim() != "recordFinish()") continue
            stamped++
            // The NEXT STATEMENT, not the next few lines: this pin was first
            // written as a four-line window and walked into its own comment, so
            // CI read it as a failure. A comment must never be able to break a
            // guard by getting longer.
            val next = lines.drop(i + 1).firstOrNull {
                val t = it.trim(); t.isNotEmpty() && !t.startsWith("//") && !t.startsWith("*")
            }?.trim()
            assertTrue("every terminal task is filed as well as measured", next == "commitFinishedTask()")
        }
        assertEquals("three terminal writes in the streaming and agent paths", 3, stamped)
    }

    /**
     * The tool list is written twice — the sentence the model is taught and the
     * sentence the owner previews — and validated in a third place. A tool that
     * exists in only one of them is a bug the device round cannot see, so the
     * three are pinned to one set here, against the runtime strings rather than
     * the source text.
     */
    @Test
    fun `the taught tool list, the preview note and the validated tools agree`() {
        val validated = AiToolName.entries.mapTo(mutableSetOf()) { it.wire }
        val toolsLine = AiToolProtocol.INSTRUCTIONS.lines().first { it.startsWith("Tools:") }
        val taught = Regex("([a-z_]+)\\(").findAll(toolsLine).map { it.groupValues[1] }.toSet()
        assertEquals("every tool is taught once, and nothing else is taught", validated, taught)
        val note = AiCopy.agentPreviewNote()
        for (n in AiToolName.entries) {
            assertEquals(
                "the preview names ${'$'}{n.wire} exactly when it is a read",
                n != AiToolName.REQUEST_RUN,
                note.contains(n.wire)
            )
        }
    }
}
