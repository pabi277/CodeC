package com.codeci.ide.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.utils.LanguageType

/**
 * Phase 24.3 — hardware-shortcut text operations that stay Android-free so
 * CI can unit-test the exact edit/caret math (the same contract as
 * [EditorKeySet.apply]): [EditorViewModel] applies the resulting
 * [TextFieldValue] through its undo-recording path.
 */
object EditorLineOps {

    /** The toggle prefix for a language (C/Go/JS -> `//`, Python/Shell -> `#`). */
    fun commentPrefixFor(language: LanguageType?): String = when (language) {
        LanguageType.PYTHON, LanguageType.SHELL -> "#"
        LanguageType.C, LanguageType.CPP, LanguageType.JAVASCRIPT, LanguageType.JSON -> "//"
        LanguageType.HTML_CSS -> "<!--"
        else -> "//"
    }

    /**
     * Toggle line-comment on every line the selection touches. `//` lines are
     * un-commented when ALL non-blank selected lines already carry the prefix,
     * otherwise the prefix is prepended. Blank lines are left alone (so
     * repeatedly toggling an empty region is a no-op). Returns null when the
     * buffer does not change.
     */
    fun toggleLineComment(value: TextFieldValue, prefix: String): TextFieldValue? {
        val text = value.text
        if (text.isEmpty()) return null
        val totalLines = text.count { it == '\n' } + 1
        val selection = value.selection
        val firstLine = lineAt(text, selection.min)
        val lastLine = lineAt(text, selection.max)
        val startLine = firstLine.coerceIn(1, totalLines)
        val endLine = lastLine.coerceIn(startLine, totalLines)

        val startOffset = lineStartOffset(text, startLine)
        val endOffset = lineEndOffset(text, endLine)
        val region = text.substring(startOffset, endOffset)
        val lines = region.split('\n')
        val nonBlank = lines.filter { it.isNotBlank() }
        val allCommented = nonBlank.isNotEmpty() && nonBlank.all { it.trimStart().startsWith(prefix) }

        val transformed: List<String> = if (allCommented) {
            lines.map { line -> if (line.isBlank()) line else dropFirstPrefix(line, prefix) }
        } else {
            lines.map { line -> if (line.isBlank()) line else prefix + line }
        }

        val newRegion = transformed.joinToString("\n")
        val newText = text.substring(0, startOffset) + newRegion + text.substring(endOffset)
        if (newText == text) return null

        // Add/remove exactly one prefix per line from the first selected line
        // through the caret's line (blank lines are never commented, so a
        // blank line between two selected lines does not shift the caret).
        val caretLine = lineAt(text, selection.min)
        val caretDelta = if (allCommented) {
            var removed = 0
            for (i in startLine..caretLine) {
                val bounds = lineBounds(text, i) ?: continue
                val l = text.substring(bounds.first, bounds.last + 1)
                if (l.isNotBlank() && l.trimStart().startsWith(prefix)) removed += prefix.length
            }
            -removed
        } else {
            var added = 0
            for (i in startLine..caretLine) {
                val bounds = lineBounds(text, i) ?: continue
                val l = text.substring(bounds.first, bounds.last + 1)
                if (l.isNotBlank()) added += prefix.length
            }
            added
        }
        val caret = (selection.min + caretDelta).coerceIn(0, newText.length)
        return TextFieldValue(newText, TextRange(caret))
    }

    private fun dropFirstPrefix(line: String, prefix: String): String {
        val indentation = line.takeWhile { it == ' ' || it == '\t' }
        val body = line.removePrefix(indentation)
        return indentation + body.removePrefix(prefix)
    }

    /** Duplicate the line(s) the selection touches, placed below the last selected line. */
    fun duplicateLine(value: TextFieldValue): TextFieldValue {
        val text = value.text
        if (text.isEmpty()) return value
        val totalLines = text.count { it == '\n' } + 1
        val selection = value.selection
        val firstLine = lineAt(text, selection.min)
        val lastLine = lineAt(text, selection.max)
        val startLine = firstLine.coerceIn(1, totalLines)
        val endLine = lastLine.coerceIn(startLine, totalLines)
        val startOffset = lineStartOffset(text, startLine)
        val endOffset = lineEndOffset(text, endLine)
        val duplicated = text.substring(startOffset, endOffset)
        val newText = text.substring(0, endOffset) + "\n" + duplicated + text.substring(endOffset)
        val caret = (endOffset + 1).coerceIn(0, newText.length)
        return TextFieldValue(newText, TextRange(caret))
    }

    private fun lineAt(text: String, offset: Int): Int =
        text.take(offset.coerceIn(0, text.length)).count { it == '\n' } + 1

    private fun lineStartOffset(text: String, line: Int): Int {
        if (line <= 1) return 0
        var count = 1
        for (i in text.indices) {
            if (text[i] == '\n') {
                count++
                if (count == line) return i + 1
            }
        }
        return text.length
    }

    /** Offset just past the last character of [line] (before its newline). */
    private fun lineEndOffset(text: String, line: Int): Int {
        val start = lineStartOffset(text, line)
        if (start >= text.length) return text.length
        val newline = text.indexOf('\n', start)
        return if (newline < 0) text.length else newline
    }

    private fun lineBounds(text: String, line: Int): IntRange? {
        val start = lineStartOffset(text, line)
        if (start > text.length) return null
        val end = lineEndOffset(text, line)
        return start..end
    }
}
