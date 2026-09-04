package com.codeci.bench.candidate

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.bench.core.DocumentBuffer
import com.codeci.bench.core.LineSpanCache
import com.codeci.bench.core.VisibleWindow
import com.codeci.bench.harness.HarnessState
import com.codeci.bench.harness.TypingTarget
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.getEditorTheme
import com.codeci.ide.ui.utils.LanguageType
import kotlinx.coroutines.launch

/**
 * Phase 25.1 candidate C-compose2 — the visible-window Compose sketch.
 *
 * The Phase 22-deferred rewrite idea, at spike fidelity: a line-partitioned
 * [DocumentBuffer] with a maintained offset index, ONLY the visible lines
 * (plus overscan) laid out in a LazyColumn, and per-line span caches so an
 * edit re-tokenizes ONE line instead of rebuilding a whole-document
 * AnnotatedString (the CMP-4023 cost that caps the current core).
 *
 * Editing model at spike scope: the caret's line renders as a focused
 * single-line BasicTextField; other lines are static Text. No IME
 * composing-region handling across lines and no newline insertion — that is
 * exactly the "decade of IME edge cases" debt the research dossier names; the
 * burst/churn scenarios never type newlines. A selection drag maps y→line
 * via the offset index and auto-scrolls near the bottom edge (the traversal
 * the caret-drag scenario records).
 */
class Compose2State(corpus: String, val language: LanguageType) {

    val buffer = DocumentBuffer(corpus)

    /** Bumped on every edit so visible items recompose (plain lists are not snapshot state). */
    var revision by mutableIntStateOf(0)
        private set

    var caretLine by mutableIntStateOf(0)
        private set

    var caretCol by mutableIntStateOf(0)
        private set

    /** Selection anchor LINE for the drag scenario; -1 = no selection. */
    var selectionAnchorLine by mutableIntStateOf(-1)

    /** Furthest line the drag reached (for traversal reporting). */
    var dragMaxLine by mutableIntStateOf(-1)

    val spans = LineSpanCache(language, EditorThemeType.DRACULA)

    fun caretOffset(): Int = buffer.lineStart(caretLine) + caretCol

    fun insertAtCaret(text: String) {
        val (first, untilLine) = buffer.insert(caretOffset(), text)
        spans.invalidateLines(first, untilLine)
        val (line, col) = buffer.locate(caretOffset() + text.length)
        caretLine = line
        caretCol = col
        revision++
    }

    fun scrollToTop() {
        caretLine = 0
        caretCol = 0
        selectionAnchorLine = -1
        revision++
    }
}

@Composable
fun Compose2Candidate(text: String, language: LanguageType, harness: HarnessState) {
    val state = remember(text) { Compose2State(text, language) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val editorColors = getEditorTheme(EditorThemeType.DRACULA)

    val windowRange by remember(state) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo.size
            VisibleWindow.range(
                firstVisible = listState.firstVisibleItemIndex,
                visibleCount = visible,
                lineCount = state.buffer.lineCount
            )
        }
    }

    val caretLine = state.caretLine
    val selectionAnchorLine = state.selectionAnchorLine
    val dragMaxLine = state.dragMaxLine
    // Read at this level so any edit (DIRECT-mode inserts included, which may
    // keep the caret line unchanged) recomposes the visible items.
    val rev = state.revision

    val focusRequester = remember(caretLine) { FocusRequester() }
    LaunchedEffect(caretLine) {
        if (caretLine < listState.firstVisibleItemIndex ||
            caretLine > listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size - 1
        ) {
            listState.scrollToItem(caretLine.coerceAtLeast(0))
        }
        withFrameNanos { }
        focusRequester.requestFocus()
    }

    fun lineAtPointer(y: Float): Int {
        val info = listState.layoutInfo
        var line = info.visibleItemsInfo.firstOrNull()?.index ?: 0
        for (item in info.visibleItemsInfo) {
            if (y >= item.offset && y <= item.offset + item.size) {
                line = item.index
                break
            }
            if (y > item.offset + item.size) line = item.index
        }
        return line
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val line = lineAtPointer(offset.y)
                            state.selectionAnchorLine = line
                            state.dragMaxLine = line
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val line = lineAtPointer(change.position.y)
                            if (line > state.dragMaxLine) state.dragMaxLine = line
                            // Auto-scroll while holding near the bottom edge —
                            // the behavior that lets a selection drag traverse
                            // the 500-line region.
                            if (change.position.y > listState.layoutInfo.viewportEndOffset - 40) {
                                scope.launch { listState.scrollBy(14f) }
                            }
                        },
                        onDragEnd = { state.selectionAnchorLine = -1 },
                        onDragCancel = { state.selectionAnchorLine = -1 }
                    )
                }
        ) {
            items(windowRange.toList(), key = { it }) { line ->
                val lineText = state.buffer.lineAt(line)
                val textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = editorColors.text
                )
                if (line == caretLine && selectionAnchorLine < 0) {
                    var fieldValue by remember(line, rev) {
                        mutableStateOf(TextFieldValue(lineText, selection = TextRange(state.caretCol)))
                    }
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { value ->
                            state.caretCol = value.selection.end.coerceIn(0, value.text.length)
                            if (value.text != lineText) {
                                // Rewrite the whole line region (spike scope:
                                // per-line edits only).
                                val start = state.buffer.lineStart(line)
                                state.buffer.delete(start, start + state.buffer.lineLength(line))
                                val (first, untilLine) = state.buffer.insert(start, value.text)
                                state.spans.invalidateLines(first, untilLine)
                                state.revision++
                            }
                            fieldValue = value
                        },
                        textStyle = textStyle,
                        cursorBrush = SolidColor(editorColors.text),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .drawBehind {
                                drawRect(Color.White.copy(alpha = 0.05f))
                            }
                    )
                } else {
                    val annotated = state.spans.get(state.buffer, line)
                    Text(
                        text = annotated,
                        style = textStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                if (selectionAnchorLine >= 0) {
                                    val lo = minOf(selectionAnchorLine, dragMaxLine)
                                    val hi = maxOf(selectionAnchorLine, dragMaxLine)
                                    if (line in lo..hi) {
                                        drawRect(Color(0x3380CBC4))
                                    }
                                }
                            }
                    )
                }
            }
        }
    }

    DisposableEffect(state) {
        harness.attach(object : TypingTarget {
            override val view: android.view.View
                get() = harness.rootView ?: error("root view not ready")

            override fun insertAtCaret(t: String) = state.insertAtCaret(t)

            override fun length(): Int = state.buffer.length

            override fun scrollToTop() {
                state.scrollToTop()
                scope.launch { listState.scrollToItem(0) }
            }

            override fun firstVisibleLine(): Int = listState.firstVisibleItemIndex
        }, "C-compose2")
        onDispose { harness.detach() }
    }
}
