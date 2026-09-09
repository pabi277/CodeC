package com.codeci.ide.ui.editor

import androidx.compose.ui.text.TextRange

/** Android-free result of the current-line and bracket decoration pass. */
data class EditorDecorationSnapshot(
    val line: Int,
    val column: Int,
    val selectionLength: Int,
    val currentLineRange: IntRange?,
    val bracketRanges: List<IntRange>
) {
    companion object {
        fun calculate(text: String, selection: TextRange): EditorDecorationSnapshot {
            val cursor = selection.min.coerceIn(0, text.length)
            var line = 1
            var lineStart = 0
            for (i in 0 until cursor) {
                if (text[i] == '\n') {
                    line++
                    lineStart = i + 1
                }
            }
            val brackets = if (
                text.length <= BracketMatcher.MAX_SCAN_LENGTH &&
                (cursor in text.indices || cursor - 1 in text.indices)
            ) {
                runCatching { BracketMatcher.findPair(text, cursor) }.getOrNull()
                    ?.let { (open, close) -> listOf(open..open, close..close) }
                    ?: emptyList()
            } else {
                emptyList()
            }
            return EditorDecorationSnapshot(
                line = line,
                column = cursor - lineStart + 1,
                selectionLength = selection.length,
                currentLineRange = CodeFormatter.lineBounds(text, line)?.takeIf { !it.isEmpty() },
                bracketRanges = brackets
            )
        }
    }
}
