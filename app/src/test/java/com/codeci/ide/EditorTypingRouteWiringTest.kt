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

    private val completionEngine: String
        get() = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/editor/CodeCompletionEngine.kt"
        ).readText()

    private val keySet: String
        get() = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/editor/EditorKeySet.kt"
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
    fun `the python block rule has exactly one owner and both openers answer a level`() {
        // Comments are blanked first (Phase 50 hygiene): a sentence that says
        // "the colon rule lives in one place" must not satisfy the pin. The
        // adapter may keep its brace test and nothing else, and both branches
        // answer the same clamped `level` (Phase 75.2 device round: "Int
        // main(){ not auto indenting", "{} are not indenting i also tryed java
        // same").
        val analyzerCode = RepoFiles.codeOnly(analyzer)
        assertEquals(
            "the sora adapter owns exactly one textual rule, and it is the brace",
            1,
            Regex("""endsWith\(""").findAll(analyzerCode).count()
        )
        assertTrue(analyzer.contains("val level = indentStep.coerceIn(2, 8)"))
        assertTrue(analyzer.contains("trimmed.endsWith('{') -> level"))
        assertTrue(
            analyzer.contains(
                "language == LanguageType.PYTHON && SmartTyping.opensPythonBlock(trimmed) -> level"
            )
        )
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

    // ---- Phase 75.2 + 75.3 device rounds: suggestion word, Tab unit, indent marks, live strip commit ---

    @Test
    fun `clicking a suggestion on either surface writes only suggestionInsertText`() {
        assertEquals(
            "one table of Python block keywords",
            1,
            Regex("""val pythonBlockKeywords = setOf\(""").findAll(smartTyping).count()
        )
        assertTrue(smartTyping.contains("word.lowercase() in pythonBlockKeywords"))
        assertTrue(completionEngine.contains("fun suggestionInsertText(item: CompletionItem): String"))
        assertTrue(
            "the strip chip accept path must resolve through suggestionInsertText",
            viewModel.contains("val insert = CodeCompletionEngine.suggestionInsertText(item)")
        )
        assertTrue(
            "the sora panel accept path must resolve through suggestionInsertText",
            analyzer.contains("val commit = CodeCompletionEngine.suggestionInsertText(item)")
        )
    }

    @Test
    fun `the keys row commits against the live buffer and refreshes pointerInput lambdas`() {
        val keysRow = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/components/EditorKeysRow.kt"
        ).readText()
        val screen = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
        ).readText()
        assertTrue(keysRow.contains("val currentOnKey by rememberUpdatedState(onKey)"))
        assertTrue(keysRow.contains("commitKey: ((EditorKey) -> Unit)? = null"))
        assertEquals(
            "both BottomStrip call sites must pass the live-buffer commitEditorKey",
            2,
            Regex(
                Regex.escape(
                    "commitEditorKey = { key -> viewModel.applyEditorKey(key, autoIndent = autoIndent, tabSize = tabSize, suppressAutoPair = true) }"
                )
            ).findAll(screen).count()
        )
    }

    @Test
    fun `selection events during text modification or pending VM edits cannot clobber the buffer`() {
        assertTrue(host.contains("if (event.cause == SelectionChangeEvent.CAUSE_TEXT_MODIFICATION)"))
        assertTrue(host.contains("if (base != viewModel.codeText.value.text)"))
        assertTrue(host.contains("val cursorDrifted = runCatching"))
        assertTrue(host.contains("if (caretPlaced && (target.selection != syncedSelection || cursorDrifted))"))
    }

    @Test
    fun `both keyboards speak one tab unit through indentRun`() {
        assertTrue(smartTyping.contains("handleTabAsIndent(old, newValue, tabSize)?.let { return it }"))
        assertTrue(smartTyping.contains("val run = indentRun(old.text, caret, step)"))
        assertTrue(keySet.contains("SmartTyping.indentRun(text, start, tabSize.coerceIn(2, 8))"))
    }

    @Test
    fun `leading indentation is painted at the source and survives theme switches`() {
        val oneTime = host.substringAfter("val completionBits = remember(editor) {")
            .substringBefore("val completionComponent = completionBits.first")
        assertTrue(oneTime.contains("CodeEditor.FLAG_DRAW_WHITESPACE_LEADING"))
        assertTrue(oneTime.contains("CodeEditor.FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE"))
        val themeEffect = host.substringAfter("LaunchedEffect(theme) {")
            .substringBefore("LaunchedEffect(fontSizeSp)")
        assertTrue(
            themeEffect.contains(
                "editor.colorScheme.setColor(EditorColorScheme.NON_PRINTABLE_CHAR, CodecPalette.INDENT_MARK)"
            )
        )
    }
}
