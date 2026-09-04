package com.codeci.ide.ui.editor.sora

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import com.codeci.ide.ui.viewmodels.EditorViewModel
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.text.batchEdit
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Phase 25.2 — the two-way bridge between sora's [CodeEditor] and the
 * `EditorViewModel` (whose `codeText: TextFieldValue` stays THE source of
 * truth for tabs, dirty flags, autosave, undo, find/replace, keys strip,
 * completions and the status readout).
 *
 * Sora → VM: a [ContentListener] pushes the new text + selection after every
 * sora edit (`viewModel.updateCode` — the SAME entry point the old
 * `BasicTextField.onValueChange` used, so undo recording, dirty computation,
 * autosave scheduling and the decoration pipeline behave exactly as before).
 * A [SelectionChangeEvent] subscription keeps the caret synced for
 * selection-only moves (taps, handles — no content listener fires there).
 * Both paths run through `syncedText`, the string snapshot we know sora
 * already holds, so selection pushes never re-copy the buffer.
 *
 * VM → Sora: on every recomposition the host compares the VM text with the
 * snapshot; a difference means the change came from OUTSIDE the surface
 * (tab switch, undo/redo, find/replace, formatter, keys strip, quick fix,
 * completion insert) and is replayed into sora as ONE batch edit. Typing
 * echoes back from the VM with the SAME string instance the listener pushed,
 * which the reference-equality fast path skips — zero work per keystroke.
 *
 * Sora's own undo stack is DISABLED: the VM's per-tab `EditorUndoManager`
 * stays canonical (strip buttons + hardware Ctrl+Z, handled by the screen),
 * which is also what keeps "undo survives tab switch" true.
 */
@Composable
fun SoraEditorHost(
    editor: CodeEditor,
    viewModel: EditorViewModel,
    language: com.codeci.ide.ui.utils.LanguageType,
    theme: com.codeci.ide.ui.theme.EditorThemeType,
    fontSizeSp: Float,
    fontFamily: FontFamily,
    tabSize: Int,
    wordWrap: Boolean,
    showLineNumbers: Boolean,
    modifier: Modifier = Modifier
) {
    val codeText by viewModel.codeText.collectAsState()

    // One-time editor configuration: ONLY what has no reactive effect below.
    // Everything keyed (language/scheme/size/font/tab/wrap/line numbers) is
    // applied by exactly ONE LaunchedEffect — setting them twice makes sora
    // destroy + rebuild the analyzer/scheme at startup for nothing.
    remember(editor) {
        editor.apply {
            setUndoEnabled(false) // VM EditorUndoManager is canonical
        }
        Unit
    }

    // Reactive settings/context.
    LaunchedEffect(language) {
        // Fresh Language per editor (sora: one language instance serves one editor).
        editor.setEditorLanguage(CodeCLanguage(language))
    }
    LaunchedEffect(theme) {
        // Fresh scheme object per application (sora enforces single ownership);
        // colors applied post-construction (see CodeCScheme's construction-
        // order note — reading `type` inside applyDefault was the crash).
        editor.setColorScheme(CodeCScheme.of(theme))
    }
    LaunchedEffect(fontSizeSp) { editor.setTextSize(fontSizeSp) }
    LaunchedEffect(fontFamily) {
        editor.setTypefaceText(
            when (fontFamily) {
                FontFamily.SansSerif -> Typeface.SANS_SERIF
                FontFamily.Serif -> Typeface.SERIF
                else -> Typeface.MONOSPACE
            }
        )
    }
    LaunchedEffect(tabSize) { editor.setTabWidth(tabSize) }
    LaunchedEffect(wordWrap) { editor.setWordwrap(wordWrap) }
    LaunchedEffect(showLineNumbers) { editor.setLineNumberEnabled(showLineNumbers) }

    // The string sora's Content is known to hold (reference-compared first).
    var soraHasText by remember { mutableStateOf<String?>(null) }
    // The selection sora is known to hold (kept by BOTH directions so the
    // VM-driven caret moves — find-next navigation, quick fixes — replay).
    var syncedSelection by remember { mutableStateOf(TextRange.Zero) }
    // The last full-text snapshot pushed to the VM (cheap selection pushes).
    var syncedText by remember { mutableStateOf("") }
    // Suppresses the sora→VM echo while WE replay a VM-driven change into sora.
    val pushing = remember { arrayOf(false) }

    DisposableEffect(editor) {
        fun pushToVm(content: Content) {
            if (pushing[0]) return // our own replay; the VM already holds this text
            val newText = content.toString()
            syncedText = newText
            soraHasText = newText
            val cursor = content.cursor
            val range = TextRange(cursor.left, cursor.right)
            syncedSelection = range
            viewModel.updateCode(TextFieldValue(newText, range))
        }

        val contentListener = object : ContentListener {
            override fun beforeReplace(content: Content) = Unit

            override fun afterInsert(
                content: Content, startLine: Int, startColumn: Int,
                endLine: Int, endColumn: Int, insertedContent: CharSequence
            ) = pushToVm(content)

            override fun afterDelete(
                content: Content, startLine: Int, startColumn: Int,
                endLine: Int, endColumn: Int, deletedContent: CharSequence
            ) = pushToVm(content)
        }
        editor.text.addContentListener(contentListener)

        val selectionReceipt = editor.subscribeEvent(
            SelectionChangeEvent::class.java,
            EventReceiver { event, _ ->
                // Our own replays move the caret too; the VM already holds
                // this text, and `syncedText` is STALE mid-replay (it still
                // holds the pre-replay string). Letting this through rolled
                // the VM back and ping-ponged replays — the 25.2 device
                // crash. Same guard as pushToVm.
                if (pushing[0]) return@EventReceiver
                if (soraHasText == null) {
                    // First replay hasn't run: sora holds nothing meaningful
                    // yet. Pushing now would overwrite the VM's real text
                    // with the empty synced snapshot.
                    return@EventReceiver
                }
                // Selection-only move: reuse the synced snapshot — no O(n)
                // copy. Clamp against the snapshot: an event racing the
                // first replay (or arriving between replays) can carry
                // indices for text the VM has never seen — a TextFieldValue
                // whose selection exceeds its text is poison downstream.
                val base = syncedText
                val left = event.left
                val right = event.right
                val startIdx = left.index.coerceIn(0, base.length)
                val endIdx = right.index.coerceIn(0, base.length)
                syncedSelection = TextRange(startIdx, endIdx)
                viewModel.updateCode(
                    TextFieldValue(base, TextRange(startIdx, endIdx))
                )
            }
        )
        onDispose {
            runCatching { selectionReceipt.unsubscribe() }
            runCatching { editor.text.removeContentListener(contentListener) }
        }
    }

    // VM → Sora replay.
    AndroidView(
        factory = { editor },
        update = { ed ->
            val target = codeText
            val known = soraHasText
            if (target.text !== known && target.text != known) {
                pushing[0] = true
                try {
                    ed.text.batchEdit { content ->
                        val lastLine = content.lineCount - 1
                        content.delete(0, 0, lastLine, content.getColumnCount(lastLine))
                        content.insert(0, 0, target.text)
                    }
                    val start = target.selection.start.coerceIn(0, target.text.length)
                    val end = target.selection.end.coerceIn(0, target.text.length)
                    val indexer = ed.text.indexer
                    val startPos = indexer.getCharPosition(start)
                    val endPos = indexer.getCharPosition(end)
                    if (start == end) {
                        ed.setSelection(startPos.line, startPos.column)
                    } else {
                        ed.setSelectionRegion(
                            startPos.line, startPos.column,
                            endPos.line, endPos.column
                        )
                    }
                    syncedText = target.text
                    soraHasText = target.text
                    syncedSelection = target.selection
                } finally {
                    pushing[0] = false
                }
            } else if (target.selection != syncedSelection) {
                // VM-driven caret move (find-next, quick fix): text unchanged.
                val start = target.selection.start.coerceIn(0, target.text.length)
                val end = target.selection.end.coerceIn(0, target.text.length)
                val indexer = ed.text.indexer
                val startPos = indexer.getCharPosition(start)
                val endPos = indexer.getCharPosition(end)
                if (start == end) {
                    ed.setSelection(startPos.line, startPos.column)
                } else {
                    ed.setSelectionRegion(
                        startPos.line, startPos.column,
                        endPos.line, endPos.column
                    )
                }
                syncedSelection = target.selection
            }
        },
        modifier = modifier
    )
}
