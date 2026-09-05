package com.codeci.bench.keys

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.codeci.bench.candidate.NowState
import com.codeci.bench.harness.HarnessState
import com.codeci.bench.harness.ImeInset
import com.codeci.bench.harness.TypingTarget
import com.codeci.ide.ui.editor.EditorKey
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.utils.LanguageType
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Phase 28.1 — the two spike surfaces. Each is minimal on purpose: an editor
 * fed ONLY through [CodeKeysGrid] with the system IME suppressed, a live
 * tap-budget line, and three owner toggles (haptics, stdin route, IME test).
 *
 * Suppression mechanism (measured, not assumed — spec budget 3): while a K
 * screen is open the window flips to `SOFT_INPUT_STATE_ALWAYS_HIDDEN`, a 50 ms
 * poll hides the IME on the decor window, and every pointer-down anywhere on
 * the screen hides it in the Initial pass. The IME-inset probe samples at the
 * same cadence, so a visible flash would LAND in the results sheet — the
 * "IME test" toggle is the control: flipping it must make the probe go > 0,
 * proving the detector is real.
 *
 *  - K1 (spike S1) — Compose core: the 25.1 C-now [NowState] (VM-shape pure
 *    document + undo + debounced highlight/decoration pipeline) fed by
 *    `EditorKeySet.apply` through [CodecKeyGrid.commit] — the exact path the
 *    shipping keys strip uses, minus the IME.
 *  - K2 (spike S2) — sora `CodeEditor` in an `AndroidView`, every cap a
 *    programmatic `Content` edit — the route sora's own `SymbolInputView`
 *    takes, so no IME character ever transits.
 */
@Composable
fun ComposeCoreSpike(text: String, harness: HarnessState, session: SpikeSession) {
    val context = LocalContext.current
    val state = remember(text) { NowState(text, LanguageType.C, EditorThemeType.DRACULA) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) { state.start(scope) }
    val codeText by state.codeText.collectAsState()

    var imeAllowed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(session, state) {
        session.commit = { cap -> state.updateCode(CodecKeyGrid.commit(cap, state.codeText.value)) }
    }
    SuppressSoftInput(session, imeAllowed, context)

    DisposableEffect(state) {
        harness.attach(
            object : TypingTarget {
                override val view: View get() = harness.rootView ?: error("root view not ready")
                override fun insertAtCaret(t: String) = state.insertAtCaret(t)
                override fun length(): Int = state.length()
                override fun scrollToTop() = state.scrollToTop()
                override fun firstVisibleLine(): Int = -1
            },
            "K1-codecgrid"
        )
        onDispose { harness.detach() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imeAllowed) {
                if (imeAllowed) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    hideIme(context, view)
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.changes.none { it.pressed }) break
                    }
                    hideIme(context, view)
                }
            }
    ) {
        SpikeToolbar(
            session = session,
            imeAllowed = imeAllowed,
            onImeToggle = {
                imeAllowed = !imeAllowed
                if (imeAllowed) {
                    val activity = context as? Activity
                    val focused = activity?.currentFocus
                    if (focused != null) {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(focused, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            BasicTextField(
                value = codeText,
                onValueChange = { state.updateCode(it) },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFFF8F8F2)
                ),
                cursorBrush = SolidColor(Color(0xFFF8F8F2)),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
            )
        }
        CodeKeysGrid(session = session, modifier = Modifier.fillMaxWidth())
        RunRowView(session)
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
}

@Composable
fun SoraCoreSpike(text: String, harness: HarnessState, session: SpikeSession) {
    val context = LocalContext.current
    val editor = remember {
        CodeEditor(context).apply {
            setTypefaceText(Typeface.MONOSPACE)
            setColorScheme(SchemeDarcula())
            setEditorLanguage(JavaLanguage())
            isHorizontalScrollBarEnabled = false
        }
    }
    var imeAllowed by remember { mutableStateOf(false) }

    LaunchedEffect(session, editor) {
        session.commit = { cap -> runCatching { soraCommit(editor, cap) } }
    }
    SuppressSoftInput(session, imeAllowed, context)

    DisposableEffect(editor) {
        editor.setText(text)
        editor.post { runCatching { editor.requestFocus() } }
        harness.attach(
            object : TypingTarget {
                override val view: View get() = editor
                override fun insertAtCaret(t: String) {
                    editor.text.insert(editor.cursor.leftLine, editor.cursor.leftColumn, t)
                }

                override fun length(): Int = editor.text.length
                override fun scrollToTop() = editor.setSelection(0, 0)
                override fun firstVisibleLine(): Int = editor.cursor.leftLine
            },
            "K2-codecgrid"
        )
        onDispose { harness.detach() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imeAllowed) {
                if (imeAllowed) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    hideIme(context, view)
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.changes.none { it.pressed }) break
                    }
                    hideIme(context, view)
                }
            }
    ) {
        SpikeToolbar(
            session = session,
            imeAllowed = imeAllowed,
            onImeToggle = {
                imeAllowed = !imeAllowed
                if (imeAllowed) {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { editor }, modifier = Modifier.fillMaxSize())
        }
        CodeKeysGrid(session = session, modifier = Modifier.fillMaxWidth())
        RunRowView(session)
    }
}

/**
 * The ONE sora commit path: programmatic edits on `Content` (the line/column
 * API sora's own symbol-input view uses — no KeyEvent, no IME), mirroring the
 * pure [CodecKeyGrid.commit] math the K1 side runs through: selection
 * collapse first, then insert / backspace.
 */
private fun soraCommit(editor: CodeEditor, cap: GridKeycap) {
    val content = editor.text
    // Selection collapse — offsets are the proven `Cursor.left/right` pair;
    // the line/column form goes through `indexer.getCharPosition` (the same
    // API the shipping SoraEditorHost replay uses), avoiding any unverified
    // accessor in the spike.
    val cur = content.cursor
    if (cur.left != cur.right) {
        val indexer = editor.text.indexer
        val a = indexer.getCharPosition(minOf(cur.left, cur.right))
        val b = indexer.getCharPosition(maxOf(cur.left, cur.right))
        content.delete(a.line, a.column, b.line, b.column)
    }
    if (cap.backspace) {
        val line = editor.cursor.leftLine
        val col = editor.cursor.leftColumn
        if (line == 0 && col == 0) return
        if (col > 0) content.delete(line, col - 1, line, col)
        else content.delete(line - 1, content.getColumnCount(line - 1), line, 0)
        return
    }
    val key = cap.key ?: return
    val text = when (key) {
        is EditorKey.Insert -> key.text
        EditorKey.Tab -> " ".repeat(CodecKeyGrid.TAB_SIZE)
        else -> return
    }
    content.insert(editor.cursor.leftLine, editor.cursor.leftColumn, text)
}

/** Haptics + stdin-route + IME-test toggles and the live budget line. */
@Composable
private fun SpikeToolbar(
    session: SpikeSession,
    imeAllowed: Boolean,
    onImeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(150); tick++ } }
    val liveLine = remember(tick) { sessionLine(session) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { session.haptics = !session.haptics }) {
            Text("haptics: ${if (session.haptics) "on" else "off"}", fontSize = 10.sp)
        }
        TextButton(onClick = { session.routeToRunRow = !session.routeToRunRow }) {
            Text("stdin: ${if (session.routeToRunRow) "route ON" else "editor"}", fontSize = 10.sp)
        }
        TextButton(onClick = onImeToggle) {
            Text("IME: ${if (imeAllowed) "allowed" else "suppressed"}", fontSize = 10.sp)
        }
        Text(
            text = liveLine,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

private fun sessionLine(session: SpikeSession): String {
    val snap = session.ledger.snapshot()
    var imeMax = 0
    for (px in session.snapshotIme()) if (px > imeMax) imeMax = px
    return "taps=%d p50=%.2f p95=%.2f max=%.2fms ime=%dpx".format(
        snap.count, snap.p50Ms, snap.p95Ms, snap.maxMs, imeMax
    )
}

@Composable
private fun RunRowView(session: SpikeSession) {
    if (!session.routeToRunRow) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("stdin › ", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(
            text = synchronized(session.runRowText) { session.runRowText.toString() },
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Window-level IME suppression + the flicker probe sampler (see the file
 * header). Restores the window policy when the screen leaves or the test
 * toggle flips.
 */
@Composable
private fun SuppressSoftInput(session: SpikeSession, imeAllowed: Boolean, context: Context) {
    LaunchedEffect(imeAllowed) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val decor = window.decorView
        if (imeAllowed) {
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            )
            return@LaunchedEffect
        }
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        try {
            var sampleDivider = 0
            while (isActive) {
                if (imm != null) hideImeDecor(imm, decor)
                if (sampleDivider % 2 == 0) session.addImeSample(ImeInset.bottomPx(decor))
                sampleDivider++
                delay(60)
            }
        } finally {
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            )
        }
    }
}

private fun hideIme(context: Context, view: View?) {
    if (view == null) return
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
    hideImeDecor(imm, view)
}

private fun hideImeDecor(imm: InputMethodManager, view: View?) {
    if (view == null) return
    runCatching { imm.hideSoftInputFromWindow(view.windowToken ?: return, InputMethodManager.HIDE_NOT_ALWAYS) }
}
