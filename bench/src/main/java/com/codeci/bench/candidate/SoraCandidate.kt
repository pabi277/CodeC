package com.codeci.bench.candidate

import android.graphics.Typeface
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.codeci.bench.harness.HarnessState
import com.codeci.bench.harness.TypingTarget
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula

/**
 * Phase 25.1 candidate C-sora — sora-editor's `CodeEditor` behind the
 * standard Compose↔View interop (`AndroidView`), the same shape a Phase 25.2
 * integration would use.
 *
 * LGPL-2.1 discipline: sora enters as a BINARY Maven dependency
 * (`io.github.rosemoe:editor` + `language-java`, 0.24.6 — see the catalog
 * research note); no sora source is vendored anywhere.
 *
 * Language: `JavaLanguage` — the module's lexer-based incremental analyzer
 * with identifier auto-completion (the ready-made "trivial lexer" the spike
 * spec asks for). C code highlighted with Java token rules looks slightly off
 * (no preprocessor scopes) but exercises exactly the machinery the decision
 * table measures: incremental spans, cached indexing, completion.
 *
 * APIs used are the officially documented surface: `setText`,
 * `setTypefaceText`, `setEditorLanguage`, `setColorScheme` (fresh scheme per
 * editor — sora enforces single-owner schemes), plus `Content.insert` for the
 * DIRECT input mode and `Cursor.leftLine` for the drag-traversal probe.
 */
@Composable
fun SoraCandidate(text: String, harness: HarnessState) {
    val context = LocalContext.current
    val editor = remember {
        CodeEditor(context).apply {
            setTypefaceText(Typeface.MONOSPACE)
            setColorScheme(SchemeDarcula())
            setEditorLanguage(JavaLanguage())
            isHorizontalScrollBarEnabled = false
        }
    }

    AndroidView(factory = { editor }, modifier = Modifier.fillMaxSize())

    DisposableEffect(editor) {
        editor.setText(text)
        editor.post { editor.requestFocus() }
        harness.attach(object : TypingTarget {
            override val view: View
                get() = editor

            override fun insertAtCaret(t: String) {
                editor.text.insert(editor.cursor.left, t)
            }

            override fun length(): Int = editor.text.length

            override fun scrollToTop() {
                editor.setSelection(0, 0)
            }

            override fun firstVisibleLine(): Int = editor.cursor.leftLine
        }, "C-sora")
        onDispose { harness.detach() }
    }
}
