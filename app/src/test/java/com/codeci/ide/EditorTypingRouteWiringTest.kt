package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 75.1 — the typing-route pins. Both reported defects live in the seam
 * between Sora and the VM, where a pure test cannot reach: what Sora is allowed
 * to do to a Backspace, and which arguments the adapter hands Sora's indent
 * rule. Source-scanned, the same role as `ReplayPathWiringTest` /
 * `BackHandlerWiringTest` — they pin the WIRING, the other suites pin the
 * decision.
 */
class EditorTypingRouteWiringTest {

    private val analyzer: String
        get() = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/editor/sora/CodeCAnalyzer.kt"
        ).readText()

    private val host: String
        get() = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/editor/sora/SoraEditorHost.kt"
        ).readText()

    private val viewModel: String
        get() = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt"
        ).readText()

    private val smartTyping: String
        get() = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/editor/SmartTyping.kt"
        ).readText()

    // ---- the two Enter routes ------------------------------------------------

    @Test
    fun `the sora adapter gives its indent rule the file language and the step`() {
        // The one-line root cause of "def indents, for does not": the call ran
        // `indentAdvanceFor(...)` on its C default, so the Python branch was
        // dead and the IME route never asked the question the VM route answers.
        assertTrue(
            "getIndentAdvance must pass the language AND the indent step",
            analyzer.contains("indentAdvanceFor(head, language, indentStepSpaces)")
        )
    }

    @Test
    fun `the python block rule has exactly one owner`() {
        // Comments are blanked first (Phase 50 hygiene): a sentence that says
        // "the colon rule lives in one place" must not satisfy the pin. The
        // adapter may keep its brace test and nothing else.
        val analyzerCode = RepoFiles.codeOnly(analyzer)
        assertEquals(
            "the sora adapter owns exactly one textual rule, and it is the brace",
            1,
            Regex("""endsWith\(""").findAll(analyzerCode).count()
        )
        assertTrue(analyzer.contains("trimmed.endsWith('{') -> 1"))
        assertTrue(analyzer.contains("SmartTyping.opensPythonBlock(trimmed)"))
        assertTrue(
            "and the VM route asks the very same function",
            smartTyping.contains("language == LanguageType.PYTHON && opensPythonBlock(trimmedPrev)")
        )
        assertEquals(
            "one definition of the rule",
            1,
            Regex("""fun opensPythonBlock\(""").findAll(smartTyping).count()
        )
    }

    @Test
    fun `the language step follows the editor tab width in both directions`() {
        assertTrue(host.contains("lang.indentStepSpaces = editor.tabWidth"))
        val effect = host.substringAfter("LaunchedEffect(tabSize) {")
            .substringBefore("LaunchedEffect(wordWrap)")
        assertTrue(effect.contains("editor.setTabWidth(tabSize)"))
        assertTrue(effect.contains("attached.indentStepSpaces = tabSize"))
    }

    // ---- the Backspace contract ---------------------------------------------

    @Test
    fun `sora is told at the source that one press deletes one space`() {
        // `deleteEmptyLineFast` (sora's own default) is what turned a single
        // Backspace on an auto-indented line into "the whole indent, plus the
        // line above" — the owner's report 2.
        assertEquals(1, Regex("""props\.deleteEmptyLineFast = false""").findAll(host).count())
        assertEquals(1, Regex("""props\.deleteMultiSpaces = 1""").findAll(host).count())
        // Both belong to the ONE-time configuration block (the `remember(editor)`
        // that already owns `setUndoEnabled(false)`), never a per-frame effect.
        val oneTime = host.substringAfter("val completionBits = remember(editor) {")
            .substringBefore("val completionComponent = completionBits.first")
        assertTrue(oneTime.contains("props.deleteEmptyLineFast = false"))
        assertTrue(oneTime.contains("props.deleteMultiSpaces = 1"))
    }

    @Test
    fun `the buffer guard is threaded through the shared change entry`() {
        val signature = viewModel.substringAfter("fun updateCode(").substringBefore(") {")
        assertTrue(signature.contains("indentBackspaceGuard: Boolean = true"))
        val call = viewModel.substringAfter("SmartTyping.transform(").substringBefore(")")
        assertTrue(call.contains("suppressAutoPair"))
        assertTrue(call.contains("indentBackspaceGuard"))
    }

    @Test
    fun `the ime push keeps the guard on by default`() {
        // The sora→VM push is the surface the owner types on. It calls the
        // shared entry with its defaults, so the guard applies there; only the
        // CodeC Keys word-delete cap opts out, in `applyEditorKey`.
        assertTrue(host.contains("viewModel.updateCode(TextFieldValue(newText, range))"))
        assertTrue(
            viewModel.contains(
                "indentBackspaceGuard = key !is com.codeci.ide.ui.editor.EditorKey.DeleteWord"
            )
        )
    }
}
