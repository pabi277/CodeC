package com.codeci.ide.ui.editor

/**
 * Phase 9 — built-in C indentation formatter used when `clang-format` is not
 * installed under `$PREFIX/bin`.
 *
 * Design contract (also pinned by unit tests):
 *  - **Line-count-preserving**: only leading whitespace of each line may
 *    change, so `lineCount(before) == lineCount(after)` and the caret can be
 *    mapped by (line, column) without a real diff.
 *  - String/char literals and comments are not analyzed for brace counting
 *    (a `{` inside `"..."` is ignored).
 *  - Lines inside a multi-line block comment are left byte-identical.
 *  - Preprocessor lines (`#include`, `#define`, …) are forced to column 0.
 *  - `case` / `default` labels indent one level less than their switch body.
 */
object CodeFormatter {

    const val MAX_LINES = 8_000
    private const val INDENT_MIN = 2
    private const val INDENT_MAX = 8
    private const val MAX_INDENT_DEPTH = 64

    fun format(source: String, tabSize: Int): String {
        val unit = " ".repeat(tabSize.coerceIn(INDENT_MIN, INDENT_MAX))
        val lines = source.split('\n')
        if (lines.size > MAX_LINES) return source
        val out = ArrayList<String>(lines.size)
        // Open blocks stack; `case x:` / `default:` push a pseudo-entry ':' so
        // the case body indents one level deeper while consecutive labels stay
        // aligned. A leading closer pops the open case body, then the block.
        val stack = ArrayDeque<Char>()
        var inBlockComment = false
        for (raw in lines) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                out += ""
                continue
            }
            if (inBlockComment) {
                out += raw
                if (trimmed.contains("*/")) inBlockComment = false
                continue
            }
            if (trimmed.startsWith("/*") && !containsBlockCommentEnd(trimmed)) {
                out += raw
                inBlockComment = true
                continue
            }
            if (trimmed.startsWith("*")) {
                out += raw
                continue
            }
            if (trimmed.startsWith("#")) {
                out += trimmed
                continue
            }
            var code = stripStringsAndComments(trimmed)
            if (code.isEmpty()) {
                out += raw
                continue
            }
            val isLabelStart = code.startsWith("case ") || code == "case" ||
                code.startsWith("case:") || code.startsWith("default:") || code == "default"
            if (isLabelStart && stack.lastOrNull() == ':') stack.removeLast()
            if (code.startsWith("}") || code.startsWith(")") || code.startsWith("]")) {
                if (stack.lastOrNull() == ':') stack.removeLast()
                if (stack.isNotEmpty()) stack.removeLast()
                code = code.substring(1)
            }
            out += unit.repeat(stack.size) + trimmed
            var i = 0
            while (i < code.length) {
                when (code[i]) {
                    '{', '(', '[' -> stack.addLast(code[i])
                    '}', ')', ']' -> if (stack.isNotEmpty()) stack.removeLast()
                    ':' -> if (i == code.length - 1 && (isLabelStart || code.startsWith("default"))) {
                        stack.addLast(':')
                    }
                }
                i++
            }
            if (stack.size > MAX_INDENT_DEPTH) {
                while (stack.size > MAX_INDENT_DEPTH) stack.removeFirst()
            }
        }
        return out.joinToString("\n")
    }

    /**
     * Maps a caret offset from [before] to [after] assuming only leading
     * whitespace changed: same line index, column clamped into the new line.
     */
    fun mapCursor(before: String, after: String, cursor: Int): Int {
        val safeCursor = cursor.coerceIn(0, before.length)
        val line = before.take(safeCursor).count { it == '\n' }
        val lineStart = before.lineStartOffset(safeCursor, line)
        val column = safeCursor - lineStart
        val afterLines = after.split('\n')
        val targetLine = afterLines.getOrNull(line) ?: return after.length.coerceAtLeast(0)
        val targetStart = afterLines.take(line).sumOf { it.length + 1 }
        return targetStart + column.coerceAtMost(targetLine.length)
    }

    /**
     * Inclusive offset range of the *content* of the 1-based [line]
     * (excluding its line break), or null when the line does not exist.
     * May return an empty range (`start > start - 1` style) for an empty line.
     */
    fun lineBounds(text: String, line: Int): IntRange? {
        if (line < 1) return null
        if (line == 1) {
            val e = text.indexOf('\n')
            return 0 until (if (e < 0) text.length else e)
        }
        var seen = 0
        for (i in text.indices) {
            if (text[i] == '\n') {
                seen++
                if (seen == line - 1) {
                    val start = i + 1
                    val e = text.indexOf('\n', startIndex = start)
                    return start until (if (e < 0) text.length else e)
                }
            }
        }
        return null
    }

    /** Offset of the first character of the 1-based [line] (0 when line 1). */
    fun lineStartOffset(text: String, line: Int): Int = lineBounds(text, line)?.first ?: 0

    private fun String.lineStartOffset(cursor: Int, line: Int): Int {
        if (line == 0) return 0
        var seen = 0
        for (i in 0 until cursor.coerceAtMost(length)) {
            if (this[i] == '\n') {
                seen++
                if (seen == line) return i + 1
            }
        }
        return 0
    }

    private fun containsBlockCommentEnd(code: String): Boolean {
        val idx = code.indexOf("*/", startIndex = 2)
        return idx >= 0
    }

    /** Removes string/char literal contents and comment tails from a line. */
    private fun stripStringsAndComments(line: String): String {
        val sb = StringBuilder(line.length)
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' || c == '\'' -> {
                    val quote = c
                    i++
                    while (i < line.length) {
                        if (line[i] == '\\' && i + 1 < line.length) { i += 2; continue }
                        if (line[i] == quote) { i++; break }
                        i++
                    }
                }
                c == '/' && i + 1 < line.length && line[i + 1] == '/' -> break
                c == '/' && i + 1 < line.length && line[i + 1] == '*' -> {
                    val end = line.indexOf("*/", startIndex = i + 2)
                    i = if (end < 0) line.length else end + 2
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }
}
