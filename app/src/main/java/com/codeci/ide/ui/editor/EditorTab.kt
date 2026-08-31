package com.codeci.ide.ui.editor

import androidx.compose.ui.text.input.TextFieldValue

/**
 * Phase 9 — one open file tab in the project editor.
 *
 * [buffer] holds the last persisted buffer state of the tab (the ACTIVE tab's
 * live buffer lives in the ViewModel and is stashed into this record on
 * switch/close); [savedText] is what is on disk, so dirtiness is derived
 * rather than separately tracked.
 */
data class EditorTab(
    val relativePath: String,
    val buffer: TextFieldValue,
    val savedText: String,
    /** Phase 16 — the file's native ending ([LineEndings.LF]/[LineEndings.CRLF]); the buffer is always LF. */
    val lineEnding: String = LineEndings.LF
) {
    val displayName: String get() = relativePath.substringAfterLast('/')
}
