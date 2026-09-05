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
import com.codeci.ide.ui.editor.AcceptGranularity
import com.codeci.ide.ui.editor.GhostState
import com.codeci.ide.ui.viewmodels.CompletionModel
import com.codeci.ide.ui.viewmodels.EditorViewModel
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.event.InlayHintClickEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.text.batchEdit
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion

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
    modifier: Modifier = Modifier,
    // ---- Phase 27 wiring (ghost + gated panel) ----
    completionModel: CompletionModel = CompletionModel.EMPTY,
    completionMasterOn: Boolean = true,
    ghostEnabled: Boolean = true,
    ghostPanelEnabled: Boolean = true,
    /** Ghost text ARGB (comment color @ 38% — G5) from the active theme. */
    ghostColorArgb: Int = 0x5575715E,
    onBrowseVisibilityChanged: (Boolean) -> Unit = {}
) {
    val codeText by viewModel.codeText.collectAsState()
    val cursorPos by viewModel.cursorPos.collectAsState()

    // One-time editor configuration: ONLY what has no reactive effect below.
    // Everything keyed (language/scheme/size/font/tab/wrap/line numbers) is
    // applied by exactly ONE LaunchedEffect — setting them twice makes sora
    // destroy + rebuild the analyzer/scheme at startup for nothing.
    val completionBits = remember(editor) {
        editor.apply {
            setUndoEnabled(false) // VM EditorUndoManager is canonical
            // Phase 27.2/27.3 — swap the completion window for the gated
            // browse-mode one BEFORE any completion could auto-fire, and
            // register the ghost renderer (type "codec.ghost").
            replaceComponent(
                EditorAutoCompletion::class.java,
                CodeCCompletionComponent(editor)
            )
            registerInlayHintRenderer(GhostHintRenderer(ghostColorArgb))
        }
        editor.getComponent(EditorAutoCompletion::class.java) as CodeCCompletionComponent to
            (editor.getInlayHintRendererForType(GhostInlayHint.TYPE_NAME) as GhostHintRenderer)
    }
    val completionComponent = completionBits.first
    val ghostHintRenderer = completionBits.second

    // Phase 27 — master switch: the WHOLE completion chrome off means the
    // component is disabled as well (27.3 invariant 4: zero residual chrome).
    LaunchedEffect(completionMasterOn) {
        completionComponent.masterEnabled = completionMasterOn
        completionComponent.setEnabled(completionMasterOn)
        if (!completionMasterOn) editor.setInlayHints(null)
    }
    LaunchedEffect(ghostColorArgb) {
        ghostHintRenderer.ghostColorArgb = ghostColorArgb
        editor.invalidate()
    }
    // Compose-facing mirror of the real panel visibility (drives the policy
    // surface; never claims PANEL while nothing is on screen).
    val browseVisibilityCallback = androidx.compose.runtime.rememberUpdatedState(onBrowseVisibilityChanged)
    // Host-local mirror of panel browse: while the panel floats, the ghost
    // does NOT paint behind it (one owning surface at a time — 27.3).
    var browsingActive by remember { mutableStateOf(false) }
    LaunchedEffect(completionComponent) {
        completionComponent.onBrowseVisibility = { visible ->
            browsingActive = visible
            browseVisibilityCallback.value(visible)
        }
    }
    // "⌄ more" → browse mode (screen button → VM request flow → here).
    LaunchedEffect(completionComponent, ghostPanelEnabled, completionMasterOn) {
        if (ghostPanelEnabled && completionMasterOn) {
            viewModel.completionPanelRequests.collect {
                completionComponent.browseNow()
            }
        }
    }

    // Phase 27.1 — the ghost: an inlay hint at the caret (sora line/column
    // are 0-based; cursorPos is 1-based). Cleared on every Hidden state —
    // including the instant shrink/clear path. (Composing is NOT a clear
    // trigger — soft-IME word composition would keep the ghost permanently
    // hidden on phones; see the device-round note below.)
    // Deduped: repeated null-application per caret move stays free.
    val lastAppliedGhost = remember(editor) { arrayOf<String?>(null) }
    LaunchedEffect(
        completionModel.ghost, cursorPos.line, cursorPos.column,
        ghostEnabled, completionMasterOn, browsingActive
    ) {
        val ghost = completionModel.ghost
        if (ghost is GhostState.Visible && ghostEnabled && completionMasterOn &&
            !browsingActive
        ) {
            runCatching {
                val lineCount = editor.text.lineCount
                val line = (cursorPos.line - 1).coerceIn(0, (lineCount - 1).coerceAtLeast(0))
                val column = (cursorPos.column - 1).coerceIn(0, editor.text.getColumnCount(line))
                val key = "$line:$column:${ghost.suffix}"
                if (key != lastAppliedGhost[0]) {
                    val container = InlayHintsContainer()
                    container.add(GhostInlayHint(line, column, ghost.suffix))
                    editor.setInlayHints(container)
                    lastAppliedGhost[0] = key
                }
            }.onFailure { editor.setInlayHints(null); lastAppliedGhost[0] = null }
        } else if (lastAppliedGhost[0] != null) {
            editor.setInlayHints(null)
            lastAppliedGhost[0] = null
        }
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
                // NOTE (device round 2026-09-05): deliberately NO
                // `hasComposingText()` gate here or at apply time. On a soft
                // keyboard (Gboard with suggestions) the IME holds a
                // composing span around the current word for autocorrect, so
                // composing is true during almost ALL normal phone typing and
                // the gate kept the ghost permanently invisible while every
                // other affordance (TAB ▸, pill, chips) still worked. The
                // point-anchored inlay auto-shifts on replace, so real CJK
                // composition cannot corrupt it either; G7's selection /
                // find-dialog / run / scroll suppressions still hold.
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
        // Phase 27.1 G3 — tapping the ghost text accepts it (in full):
        // sora dispatches InlayHintClickEvent from its touch handler.
        val ghostClickReceipt = editor.subscribeEvent(
            InlayHintClickEvent::class.java,
            EventReceiver { event, _ ->
                if (event.inlayHint.type == GhostInlayHint.TYPE_NAME) {
                    event.intercept()
                    viewModel.acceptGhost(AcceptGranularity.FULL)
                }
            }
        )
        // Phase 27.1 G4 — the ghost clears on scroll (re-arms on next edit).
        val scrollReceipt = editor.subscribeEvent(
            ScrollEvent::class.java,
            EventReceiver { _, _ -> viewModel.onCompletionScroll() }
        )
        onDispose {
            runCatching { selectionReceipt.unsubscribe() }
            runCatching { ghostClickReceipt.unsubscribe() }
            runCatching { scrollReceipt.unsubscribe() }
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
                    // Phase 27.1 — a full replay invalidates ghost anchors;
                    // the effect above repaints from the fresh VM state.
                    ed.setInlayHints(null)
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
