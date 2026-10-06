package com.codeci.ide

import com.codeci.ide.ui.ai.AiAgentLimits
import com.codeci.ide.ui.ai.AiProjectFiles
import com.codeci.ide.ui.ai.AiToolLimits
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 88 / Level 11 source pins.
 *
 * Level 11 is presentation only. The pure parts are tested directly; the risk
 * left is an edge that reaches past them — a link opened without the dialog, an
 * answer parsed on every chunk, a result row that shows something other than
 * what was packed, a second progress builder. Each pin reads code only
 * (`RepoFiles.codeOnly`), so a comment or a string can never satisfy it.
 */
class AiLevel11WiringTest {
    private val aiDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private fun ai(name: String) = RepoFiles.codeOnly(File(aiDir, name).readText())
    private fun aiFiles(): List<File> = aiDir.listFiles { f -> f.isFile && f.extension == "kt" }!!.sortedBy { it.name }

    // ---- 88.3 the answer surface ------------------------------------------------

    @Test
    fun `Answer renders through the Markdown model and the verbatim body is kept for reviewer markup`() {
        val parts = ai("AiParts.kt")
        val answer = parts.substringAfter("internal fun Answer(").substringBefore("internal fun VerbatimText(")
        assertTrue(answer.contains("AiMarkdownAnswer(text, streaming)"))
        val view = ai("AiMarkdownView.kt")
        assertTrue(view.contains("remember(text) { AiMarkdown.parse(text) }"))
        assertTrue(view.contains("AiMarkdown.parse(current)"))
        // :787 stays verbatim; the streaming site is the only one that streams.
        val sheet = ai("AiChatSheet.kt")
        assertTrue(sheet.contains("is AiReviewVerdict.MarkupShownAsText -> VerbatimText(review.text)"))
        assertTrue(sheet.contains("Answer(state.answer, streaming = true)"))
        assertEquals(1, Regex("streaming = true").findAll(sheet).count())
        // Phase 90 added one more Markdown-drawn surface (each earlier turn in the
        // conversation reads through the same model, AiChatSheet `Answer(turn.text)`);
        // the verbatim exception is still exactly one, so the census moves 6 -> 7.
        assertTrue(sheet.contains("Answer(turn.text)"))
        assertEquals(7, Regex("\\bAnswer[(]").findAll(sheet).count() + Regex("\\bVerbatimText[(]").findAll(sheet).count())
    }

    @Test
    fun `the streaming parse is throttled, conflated and off the main thread`() {
        val view = ai("AiMarkdownView.kt")
        val streaming = view.substringAfter("private fun rememberAnswerBlocks(").substringBefore("private fun MdBlocks(")
        assertTrue(streaming.contains("snapshotFlow"))
        assertTrue(streaming.contains(".conflate()"))
        assertTrue(streaming.contains("withContext(Dispatchers.Default) { AiMarkdown.parse(current) }"))
        assertTrue(streaming.contains("delay(AiMarkdown.STREAM_REPARSE_MS)"))
    }

    @Test
    fun `no HTML interpreter, web view, deprecated clickable text or image loader anywhere in ui ai`() {
        for (file in aiFiles()) {
            val code = RepoFiles.codeOnly(file.readText())
            for (banned in listOf("fromHtml", "WebView", "ClickableText", "AsyncImage", "rememberAsyncImagePainter", "ImageRequest", "Html.")) {
                assertFalse("${file.name} must not use $banned", code.contains(banned))
            }
        }
    }

    @Test
    fun `exactly two openUri sites, and the new one is the dialog's Open button`() {
        val counts = aiFiles().associate { it.name to Regex("openUri[(]").findAll(RepoFiles.codeOnly(it.readText())).count() }
        assertEquals(2, counts.values.sum())
        assertEquals(1, counts["AiParts.kt"])
        assertEquals(1, counts["AiMarkdownView.kt"])
        val view = ai("AiMarkdownView.kt")
        val confirm = view.substringAfter("confirmButton = {").substringBefore("dismissButton = {")
        assertTrue(confirm.contains("uriHandler.openUri(link.url)"))
        // The dialog shows the same Openable that Open passes on.
        val dialog = view.substringAfter("private fun AiLinkConfirmDialog(link: AiLink.Openable")
        assertTrue(dialog.contains("Text(link.host"))
        assertTrue(dialog.contains("Text(link.url"))
    }

    @Test
    fun `every LinkAnnotation is an Openable built with a listener`() {
        var total = 0
        for (file in aiFiles()) {
            val code = RepoFiles.codeOnly(file.readText())
            val urls = Regex("LinkAnnotation[.]Url[(]").findAll(code).count()
            val withListener = Regex("LinkAnnotation[.]Url[(][^()]*[)]\\s*[{]").findAll(code).count()
            assertEquals("${file.name}: every LinkAnnotation.Url needs a listener", urls, withListener)
            assertFalse(file.name, code.contains("LinkAnnotation.Clickable("))
            total += urls
        }
        assertEquals(1, total)
        val view = ai("AiMarkdownView.kt")
        val builder = view.substringAfter("private fun AnnotatedString.Builder.appendInlines(").substringBefore("private fun tableGrid(")
        assertTrue(builder.contains("when (val link = AiLinkPolicy.classify(span.target))"))
        assertTrue(builder.contains("is AiLink.Openable -> {"))
        assertTrue(builder.contains("LinkAnnotation.Url(link.url, linkStyles) { onLink(link) }"))
        // Images keep only their alt text.
        assertTrue(builder.contains("AiCopy.answerImage(span.alt)"))
    }

    @Test
    fun `the renderer never truncates, never caps height and uses theme colours only`() {
        val view = ai("AiMarkdownView.kt")
        assertFalse(view.contains("maxLines"))
        assertFalse(view.contains("heightIn"))
        assertFalse(view.contains("Color(0x"))
        assertFalse(view.contains("TextOverflow"))
    }

    @Test
    fun `code blocks copy through copyAnswer and nothing in the answer writes, applies or runs`() {
        val view = ai("AiMarkdownView.kt")
        assertTrue(view.contains("onCopy = { copyAnswer(context, it) }"))
        assertTrue(view.contains("onClick = { onCopy(block.text) }"))
        assertTrue(view.contains("DisableSelection {"))
        for (name in listOf("AiMarkdownView.kt", "AiMarkdown.kt", "AiLevel11Policies.kt")) {
            val code = ai(name)
            for (banned in listOf(
                "writeText(", "writeBytes(", "FileOutputStream", "AiEditApplier", "ProjectManager", "insert(",
                "ProcessBuilder", "Runtime.getRuntime", "DataStore", "SharedPreferences", "File("
            )) {
                assertFalse("$name must not use $banned", code.contains(banned))
            }
        }
        // The DONE bar still copies the raw Markdown the model wrote.
        assertTrue(ai("AiChatSheet.kt").contains("copyAnswer(context, state.answer)"))
    }

    @Test
    fun `the presentation layer never reaches request building, the network, the key store or the tools`() {
        for (name in listOf("AiMarkdownView.kt", "AiMarkdown.kt", "AiLevel11Policies.kt", "AiAgentState.kt")) {
            val code = ai(name)
            for (banned in listOf(
                "AiViewModel", "client.", ".stream(", "AiKeyStore", "AiToolRunner", "AiAgentPrompt",
                "HttpURLConnection", "openConnection", "URL(", "AiHttpStream", "AiTaskMemory"
            )) {
                assertFalse("$name must not reach $banned", code.contains(banned))
            }
        }
    }

    @Test
    fun `the new surface file obeys the AI surface laws`() {
        // The same laws AiSurfaceWiringTest / AiHelperWiringTest apply to the older surface files.
        val view = ai("AiMarkdownView.kt")
        assertFalse(view.contains("viewModel(") || view.contains("AiViewModel("))
        for (banned in listOf("GeminiClient", "GeminiRequest", "HttpURLConnection", "client.")) {
            assertFalse("AiMarkdownView.kt must not reach the network ($banned)", view.contains(banned))
        }
        assertFalse(view.contains("FontFamily.Monospace"))
        assertTrue(view.contains("CodecType.codeFamily"))
    }

    // ---- 88.4 one truthful progress line ------------------------------------------------

    @Test
    fun `one progress builder - the old working line is gone and both sites ask the policy`() {
        for (file in RepoFiles.mainKotlinSources()) {
            assertFalse(file.name, file.readText().contains("agentWorkingLine"))
        }
        val sheet = ai("AiChatSheet.kt")
        assertEquals(2, Regex("AiProgressPolicy[.]line[(]").findAll(sheet).count())
        assertTrue(sheet.contains("== AiProgressPolicy.Placement.CARD"))
        assertTrue(sheet.contains("== AiProgressPolicy.Placement.BOTTOM_BAR"))
        assertTrue(Regex("AiProgressPolicy[.]placement[(]").findAll(sheet).count() >= 2)
        // The card no longer builds its own counter line.
        val card = sheet.substringAfter("private fun AgentActivityCard(").substringBefore("val openRequests")
        assertFalse(card.contains("agentUsageLine("))
        // The stage is read from state that already exists: no new UI-state field.
        val input = sheet.substringAfter("private fun progressInput(state: AiUiState)").substringBefore("@Composable")
        for (field in listOf("state.phase", "state.agentSteps.lastOrNull()?.kind", "state.answer.isEmpty()",
            "state.agentRun != null", "state.agentRunRunning", "state.retryCountdown != null")) {
            assertTrue(field, input.contains(field))
        }
        // Counters render against the task's own caps (Phase 87.6), inside the one builder.
        val policies = ai("AiLevel11Policies.kt")
        assertTrue(policies.contains("turnCap = usage.turnCap"))
        assertTrue(policies.contains("readCap = usage.readCap"))
        assertTrue(policies.contains("AiCopy.agentUsageLine("))
    }

    // ---- 88.5 activity rows and disclosure ----------------------------------------------

    @Test
    fun `result rows open to exactly the packed text and never through the Markdown renderer`() {
        val sheet = ai("AiChatSheet.kt")
        val row = sheet.substringAfter("if (AiResultRowPolicy.hasFullResult(step)) {").substringBefore("} else if (step.detail.isNotBlank()) {")
        assertTrue(row.contains("val full = AiResultRowPolicy.fullResult(step)"))
        assertTrue(row.contains("SentText(full)"))
        assertTrue(row.contains("AiResultRowPolicy.startsOpen(state.options.activity)"))
        assertTrue(row.contains("openResults[index]"))
        assertFalse(row.contains("Answer("))
        assertFalse(row.contains("AiMarkdown"))
        // The policy reads exactly what renderStep packs.
        assertTrue(ai("AiAgentLoop.kt").contains("append(step.modelResult.take(AiAgentLimits.MAX_RESULT_CHARS))"))
        assertTrue(ai("AiLevel11Policies.kt").contains("step.modelResult.take(AiAgentLimits.MAX_RESULT_CHARS)"))
    }

    @Test
    fun `row state never leaves the composable (S1)`() {
        for (name in listOf("AiViewModel.kt", "AiAgentLoop.kt")) {
            val code = ai(name)
            assertFalse(name, code.contains("openResults"))
            assertFalse(name, code.contains("AiResultRowPolicy"))
            assertFalse(name, code.contains("AiProgressPolicy"))
            assertFalse(name, code.contains("AiMarkdown"))
        }
        assertTrue(ai("AiChatSheet.kt").contains("val openResults = remember { mutableStateMapOf<Int, Boolean>() }"))
    }

    @Test
    fun `request disclosure stays one tap away and the pre-Send preview stays whole (S8 and D4)`() {
        val sheet = ai("AiChatSheet.kt")
        assertTrue(sheet.contains("val openRequests = remember { mutableStateListOf<Int>() }"))
        assertTrue(sheet.contains("AiActivityPolicy.row("))
        assertTrue(sheet.contains("SentText(instruction + "))
        assertTrue(sheet.contains(".clickable { openRequests.add(index) }"))
        assertTrue(sheet.contains("SentText(it.systemInstruction + "))
    }

    // ---- the budget does not move ---------------------------------------------------------

    @Test
    fun `no ceiling moved and Send is still the only network road`() {
        assertEquals(12, AiAgentLimits.MAX_TURNS)
        assertEquals(24, AiAgentLimits.MAX_TOOL_CALLS)
        assertEquals(2, AiAgentLimits.MAX_RUNS)
        assertEquals(8, AiToolLimits.MAX_BATCH_READS)
        assertEquals(8_000, AiToolLimits.MAX_RESULT_CHARS)
        assertEquals(24_000, AiProjectFiles.MAX_READ_CHARS)
        assertEquals(3, Regex("client[.]stream[(]").findAll(ai("AiViewModel.kt")).count())
    }

    @Test
    fun `the pure Level 11 files stay host-testable`() {
        for (name in listOf("AiMarkdown.kt", "AiLevel11Policies.kt", "AiAgentState.kt")) {
            val source = File(aiDir, name).readText()
            assertFalse(name, source.contains("import android"))
            assertFalse(name, source.contains("import androidx"))
            assertFalse(name, RepoFiles.codeOnly(source).contains("java.nio"))
        }
        // The state types moved, unchanged, out of the Android-bound ViewModel.
        val vm = ai("AiViewModel.kt")
        assertFalse(vm.contains("enum class AiPhase"))
        assertFalse(vm.contains("data class AiAgentUsage"))
        val state = ai("AiAgentState.kt")
        assertTrue(state.contains("enum class AiPhase { IDLE, PREVIEW, STREAMING, DONE, FAILED }"))
        assertTrue(state.contains("val turnCap: Int = AiAgentLimits.MAX_TURNS"))
        assertTrue(state.contains("val readCap: Int = AiAgentLimits.MAX_TOOL_CALLS"))
    }
}
