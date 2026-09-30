package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 77 — the phone AI surface, asked of the source (house style of
 * `AiHelperWiringTest`): the policies are really used (a pure policy nobody
 * calls is decoration), the no-nag and read-only laws hold on the new
 * surfaces, and the ✨ slot is AI home only.
 */
class AiSurfaceWiringTest {

    private val aiDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private fun ai(name: String) = RepoFiles.codeOnly(File(aiDir, name).readText())
    private val editor = RepoFiles.codeOnly(
        RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
    )
    private val surfaceFiles = listOf("AiHome.kt", "AiChatSheet.kt", "AiFloatingButton.kt", "AiParts.kt")

    @Test
    fun `the bubble shows only through the policy and only when the helper is READY`() {
        assertTrue(editor.contains("val aiReady = aiAvailability == AiAvailability.READY"))
        assertTrue(editor.contains("AiBubblePolicy.visible(aiAvailability, aiState.showBubble, aiSheetOpen)"))
        // the sheet itself can only be "open" while READY — a deleted key closes it
        assertTrue(editor.contains("val aiSheetOpen = aiReady && aiState.sheet != AiSheetState.HIDDEN"))
        assertTrue(editor.contains("LaunchedEffect(aiReady) { if (!aiReady) aiViewModel.closeSheet() }"))
        // the policy's own rule, pinned where it is written
        assertTrue(ai("AiBubblePolicy.kt").contains("availability == AiAvailability.READY && showSetting && !sheetOpen"))
    }

    @Test
    fun `the bubble is drawn inside the code area - after the editor host, before the coding row`() {
        val host = editor.indexOf("SoraEditorHost(")
        val bubble = editor.indexOf("AiFloatingButton(")
        val firstStrip = editor.indexOf("BottomStrip(")
        assertTrue(host in 0 until bubble)
        assertTrue("the bubble must come before the coding row in the column", bubble < firstStrip)
        assertEquals(1, Regex("AiFloatingButton\\(").findAll(editor).count())
    }

    @Test
    fun `chat and bubble share the one AiViewModel`() {
        assertEquals(1, Regex("AiViewModel = viewModel\\(\\)").findAll(editor).count())
        for (f in surfaceFiles) {
            val code = ai(f)
            assertFalse("$f must not create its own view model", code.contains("viewModel(") || code.contains("AiViewModel("))
        }
        // both sheet sizes are the same composable fed the same state
        assertEquals(1, Regex("AiChatSheet\\(").findAll(editor).count())
        assertTrue(editor.contains("aiSheet(false, Modifier)"))
        assertTrue(editor.contains("aiSheet(true, Modifier.imePadding())"))
    }

    @Test
    fun `the rail slot is AI home only - no question, answer or send`() {
        val home = ai("AiHome.kt")
        for (banned in listOf("EXPLAIN_SELECTION", "EXPLAIN_ERROR", "onSend", "onExplain", "Answer(", "QUESTION", "SentText(")) {
            assertFalse("AI home must not contain the chat ($banned)", home.contains(banned))
        }
        assertTrue(home.contains("AiCopy.SHOW_BUBBLE"))
        assertTrue(home.contains("AiCopy.OPEN_CHAT"))
        assertTrue(home.contains("AiCopy.TEST"))
        assertTrue(home.contains("AiCopy.DELETE_KEY"))
        assertFalse("the Phase 76 panel file is gone", File(aiDir, "AiPanel.kt").exists())
    }

    @Test
    fun `Explain with AI is offered only after a failed run and only when READY`() {
        assertTrue(editor.contains("val aiExplainWithAi: (() -> Unit)? = if (aiReady && aiRunFailed)"))
        assertTrue(editor.contains("val aiRunFailed = AiGate.runFailed("))
        assertEquals(1, Regex("onExplainWithAi = aiExplainWithAi").findAll(editor).count())
        val panel = RepoFiles.codeOnly(
            RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/components/OutputPanelView.kt").readText()
        )
        assertTrue(panel.contains("onExplainWithAi: (() -> Unit)? = null"))
        assertTrue(panel.contains("if (onExplainWithAi != null)"))
        // it only opens the sheet on a run-output PREVIEW — never a request
        val rawEditor = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText()
        assertTrue(rawEditor.contains("aiExplainError(\"\")"))
    }

    @Test
    fun `the new surfaces add no network path - one stream call pair, in the view model only`() {
        assertEquals(2, Regex("client\\.stream\\(").findAll(ai("AiViewModel.kt")).count())
        for (f in surfaceFiles) {
            val code = ai(f)
            for (banned in listOf("GeminiClient", "GeminiRequest", "HttpURLConnection", "client.")) {
                assertFalse("$f must not reach the network ($banned)", code.contains(banned))
            }
        }
        // Send still requires a preview
        assertTrue(ai("AiViewModel.kt").contains("if (_state.value.phase != AiPhase.PREVIEW"))
    }

    @Test
    fun `the no-nag law - no pulse, tooltip, coach mark or animation on the bubble`() {
        val bubble = ai("AiFloatingButton.kt")
        for (banned in listOf("rememberInfiniteTransition", "animate", "Tooltip", "Popup(", "coach", "pulse", "AnimatedVisibility")) {
            assertFalse("the bubble must not use $banned", bubble.contains(banned))
        }
        // the only menu is the owner's own long-press: Hide / Move
        assertTrue(bubble.contains("AiCopy.HIDE_BUBBLE") && bubble.contains("AiCopy.MOVE_BUBBLE"))
    }

    @Test
    fun `Back and the Output panel go through the existing seams, not new ones`() {
        assertTrue(editor.contains("BackAction.CollapseAiSheet -> aiViewModel.sheetEvent(AiSheetEvent.BACK)"))
        assertTrue(editor.contains("aiSheetOpen = aiSheetOpen"))
        val router = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/navigation/BackRouter.kt").readText()
        assertTrue(router.contains("s.aiSheetOpen && !s.editorDrawerOpen && !s.keyboardVisible -> BackAction.CollapseAiSheet"))
        assertFalse("the sheet must not add a second BackHandler", ai("AiChatSheet.kt").contains("BackHandler"))
        // HALF is the Output panel's own height rule, not a second model
        val policy = ai("AiSheetPolicy.kt")
        assertTrue(policy.contains("OutputPanelHeight.resolve(OutputPanelHeight.defaultFor(available), available, imeVisible)"))
        assertTrue(editor.contains("visible = AiSheetPolicy.outputVisible(outputExpanded, aiState.sheet)"))
    }

    @Test
    fun `while the sheet is up the coding row, status bar and CodeC Keys step aside`() {
        assertTrue(editor.contains("if (stripVisible && !imeVisible && !aiSheetOpen)"))
        assertTrue(editor.contains("if (stripVisible && imeVisible && !aiSheetOpen)"))
        assertTrue(editor.contains("if (caretPlaced && !imeVisible && !codecKeysUp && !aiSheetOpen)"))
        assertTrue(editor.contains("if (codecKeysUp && !aiSheetOpen)"))
    }

    @Test
    fun `only the button's spot and visibility are saved, in the existing AI properties file`() {
        val store = ai("AiKeyStore.kt")
        assertTrue(store.contains("PROP_BUBBLE_POS") && store.contains("PROP_BUBBLE_SHOW"))
        assertTrue(File(aiDir, "AiKeyStore.kt").readText().contains("\"bubble_pos\""))
        assertTrue(File(aiDir, "AiKeyStore.kt").readText().contains("\"bubble_show\""))
        for (banned in listOf("dataStore", "DataStore", "SharedPreferences")) {
            for (f in aiDir.listFiles { x -> x.extension == "kt" }!!) {
                assertFalse("${f.name} must not use $banned", RepoFiles.codeOnly(f.readText()).contains(banned))
            }
        }
        // the sheet state and the A/B choice are memory only
        assertFalse(store.contains("sheet") || store.contains("outputConflict") || store.contains("OutputConflict"))
    }

    @Test
    fun `code text uses the code family and no surface hard-codes a colour`() {
        assertTrue(ai("AiParts.kt").contains("CodecType.codeFamily"))
        for (f in surfaceFiles) {
            val code = ai(f)
            assertFalse("$f: no Color(0x…)", code.contains("Color(0x"))
            assertFalse("$f: TypeAdoptionTest forbids FontFamily.Monospace", code.contains("FontFamily.Monospace"))
        }
    }

    @Test
    fun `Ask AI in the selection menu stays unbuilt - Sora's action window has no extension point`() {
        // PART_77_3 records why: fixed five-button inflated layout, final button
        // fields, no item registry. No fork, no view injection — so no reference.
        for (f in RepoFiles.mainKotlinSources()) {
            assertFalse("${f.name} must not touch Sora's text-action window", f.readText().contains("EditorTextActionWindow"))
        }
    }
}
