package com.codeci.bench.candidate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.bench.harness.HarnessState
import com.codeci.bench.harness.TypingTarget
import com.codeci.ide.ui.editor.BracketMatcher
import com.codeci.ide.ui.editor.CodeCompletionEngine
import com.codeci.ide.ui.editor.CodeFormatter
import com.codeci.ide.ui.editor.CompletionItem
import com.codeci.ide.ui.editor.EditorUndoManager
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.getEditorTheme
import com.codeci.ide.ui.utils.EditorDecorations
import com.codeci.ide.ui.utils.HighlightedCode
import com.codeci.ide.ui.utils.LanguageType
import com.codeci.ide.ui.utils.SyntaxVisualTransformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Debounces — the SAME values the production editor uses. */
private const val HIGHLIGHT_DEBOUNCE_MS = 80L
private const val COMPLETION_DEBOUNCE_MS = 120L
private const val DECORATION_DEBOUNCE_MS = 20L

/** Bench font size (sp) — the app default. */
private const val BENCH_FONT_SP = 14

/**
 * Phase 25.1 candidate C-now — the CURRENT editor core, measured.
 *
 * Faithful mirror of the `EditorScreen`/`EditorViewModel` widget stack
 * (Phase 22.1 shape): `BasicTextField(TextFieldValue)` +
 * `SyntaxVisualTransformation` fed by the ±3 000-char windowed, 80 ms
 * debounced, off-thread highlight snapshot; 20 ms decorations (current line +
 * bracket pair); the 120 ms debounced completion scan; the gutter
 * `remember(lineCount)` string. Project/tab/save chrome is deliberately
 * absent — it is not part of the keystroke path being measured.
 */
class NowState(initial: String, language: LanguageType, theme: EditorThemeType) {

    private val _codeText = MutableStateFlow(TextFieldValue(initial))
    val codeText: StateFlow<TextFieldValue> = _codeText.asStateFlow()

    private val undo = EditorUndoManager()

    private val _highlightContext = MutableStateFlow(theme to language)
    private val _highlighted = MutableStateFlow<HighlightedCode?>(null)
    val highlighted: StateFlow<HighlightedCode?> = _highlighted.asStateFlow()

    val currentLineRange = MutableStateFlow<IntRange?>(null)
    val bracketRanges = MutableStateFlow<List<IntRange>>(emptyList())

    private var decorationJob: Job? = null
    internal var decorationScope: CoroutineScope? = null

    /** The 80 ms debounced off-thread highlight pipeline — the VM's collector, mirrored. */
    fun start(scope: CoroutineScope) {
        decorationScope = scope
        scope.launch {
            combine(_codeText, _highlightContext) { value, context ->
                val caret = value.selection.min.coerceAtLeast(0)
                Triple(value.text, context, caret / (HighlightedCode.WINDOW / 4))
            }
                .distinctUntilChanged()
                .debounce(HIGHLIGHT_DEBOUNCE_MS)
                .collect { (text, context, _) ->
                    val caret = _codeText.value.selection.min.coerceAtLeast(0)
                    val built = withContext(Dispatchers.Default) {
                        HighlightedCode.of(text, context.first, context.second, caret)
                    }
                    if (built.text == _codeText.value.text) _highlighted.value = built
                }
        }
    }

    fun updateCode(newValue: TextFieldValue) {
        val old = _codeText.value
        if (newValue.text != old.text) {
            undo.recordChange(old, newValue, System.currentTimeMillis())
            _codeText.value = newValue
        } else if (newValue.selection != old.selection) {
            _codeText.value = newValue
        } else {
            return
        }
        scheduleDecorationRefresh()
    }

    private fun scheduleDecorationRefresh() {
        val scope = decorationScope ?: return
        decorationJob?.cancel()
        decorationJob = scope.launch {
            delay(DECORATION_DEBOUNCE_MS)
            refreshDecorationsNow()
        }
    }

    private fun refreshDecorationsNow() {
        val current = _codeText.value
        val cursor = current.selection.min.coerceIn(0, current.text.length)
        var line = 1
        var lineStart = 0
        for (i in 0 until cursor) {
            if (current.text[i] == '\n') {
                line++
                lineStart = i + 1
            }
        }
        currentLineRange.value = CodeFormatter.lineBounds(current.text, line)?.takeIf { !it.isEmpty() }
        bracketRanges.value = if (
            current.text.length <= BracketMatcher.MAX_SCAN_LENGTH &&
            (cursor in current.text.indices || cursor - 1 in current.text.indices)
        ) {
            runCatching { BracketMatcher.findPair(current.text, cursor) }.getOrNull()
                ?.let { (open, close) -> listOf(open..open, close..close) }
                ?: emptyList()
        } else {
            emptyList()
        }
    }

    // ---- TypingTarget (DIRECT input mode) --------------------------------

    fun insertAtCaret(text: String) {
        val current = _codeText.value
        val caret = current.selection.end.coerceAtLeast(current.selection.start)
            .coerceIn(0, current.text.length)
        val next = current.text.substring(0, caret) + text + current.text.substring(caret)
        updateCode(TextFieldValue(next, selection = TextRange(caret + text.length)))
    }

    fun length(): Int = _codeText.value.text.length

    fun scrollToTop() {
        _codeText.value = _codeText.value.copy(selection = TextRange(0))
    }

    fun firstVisibleLine(): Int = -1 // BasicTextField does not expose its scroll position.
}

@Composable
fun NowCandidate(text: String, language: LanguageType, harness: HarnessState) {
    val theme = EditorThemeType.DRACULA
    val state = remember(text) { NowState(text, language, theme) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) { state.start(scope) }

    val editorColors = getEditorTheme(theme)
    val codeText by state.codeText.collectAsState()
    val highlighted by state.highlighted.collectAsState()
    val currentLineRange by state.currentLineRange.collectAsState()
    val bracketRanges by state.bracketRanges.collectAsState()

    val decorations = remember(currentLineRange, bracketRanges) {
        EditorDecorations(currentLineRange = currentLineRange, bracketRanges = bracketRanges)
    }
    // Phase 22.7 — the caret feeds only the INLINE FALLBACK's window, bucketed
    // exactly as the app buckets it.
    val caretBucket = codeText.selection.min.coerceAtLeast(0) / (HighlightedCode.WINDOW / 4)
    val transformation = remember(theme, decorations, language, highlighted, caretBucket) {
        SyntaxVisualTransformation(
            theme, decorations, language, highlighted, caretBucket * (HighlightedCode.WINDOW / 4)
        )
    }

    // Gutter — the same derivedStateOf/remember(lineCount) shape as the app.
    val lineCount by remember { derivedStateOf { codeText.text.count { it == '\n' } + 1 } }
    val lineNumbers = remember(lineCount) { (1..lineCount).joinToString("\n") }

    // Completion scan — the app's 120 ms debounced produceState, rendered as a
    // minimal preview so item changes recompose something (as the strip would).
    val completionItems by produceState(
        initialValue = emptyList<CompletionItem>(),
        key1 = codeText.text,
        key2 = codeText.selection,
        key3 = language
    ) {
        delay(COMPLETION_DEBOUNCE_MS)
        val t = codeText.text
        val sel = codeText.selection
        value = withContext(Dispatchers.Default) {
            CodeCompletionEngine.completions(t, sel.end.coerceAtLeast(sel.start), language)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(48.dp)
                .fillMaxSize()
                .drawBehind {
                    drawLine(
                        color = editorColors.text.copy(alpha = 0.14f),
                        start = Offset(size.width - 0.5f, 0f),
                        end = Offset(size.width - 0.5f, size.height),
                        strokeWidth = 1f
                    )
                }
        ) {
            Text(
                text = lineNumbers,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = BENCH_FONT_SP.sp,
                    color = Color(0xFF858585),
                    textAlign = TextAlign.End
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            BasicTextField(
                value = codeText,
                onValueChange = { state.updateCode(it) },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = BENCH_FONT_SP.sp,
                    color = editorColors.text
                ),
                visualTransformation = transformation,
                cursorBrush = SolidColor(editorColors.text),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
            if (completionItems.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().height(88.dp)) {
                    completionItems.take(4).forEach { item ->
                        Text(
                            text = item.label,
                            style = TextStyle(fontSize = 12.sp, color = Color(0xFFBFBFBF)),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(state) {
        harness.attach(object : TypingTarget {
            override val view: android.view.View
                get() = harness.rootView ?: error("root view not ready")

            override fun insertAtCaret(t: String) = state.insertAtCaret(t)
            override fun length(): Int = state.length()
            override fun scrollToTop() = state.scrollToTop()
            override fun firstVisibleLine(): Int = state.firstVisibleLine()
        }, "C-now")
        onDispose { harness.detach() }
    }
}
