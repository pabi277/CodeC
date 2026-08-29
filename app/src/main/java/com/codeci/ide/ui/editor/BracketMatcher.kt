package com.codeci.ide.ui.editor

/**
 * Phase 9 — bracket pair matching for `()`, `{}`, `[]`.
 *
 * The scan skips string literals, character literals, and both comment
 * styles, so a `)` inside `"...)"` or `// )` never matches. The cursor may sit
 * either just before or just after the bracket to highlight.
 */
object BracketMatcher {

    /** Guard so an O(n) scan never runs on absurd buffers; caller may also gate. */
    const val MAX_SCAN_LENGTH = 300_000

    /** Returns the matched `(openIndex, closeIndex)` pair, or null. */
    fun findPair(text: String, cursor: Int): Pair<Int, Int>? {
        if (text.isEmpty() || text.length > MAX_SCAN_LENGTH) return null
        val pos = listOf(cursor, cursor - 1)
            .firstOrNull { it in text.indices && isBracket(text[it]) }
            ?: return null
        return matchAt(text, codeMask(text), pos)
    }

    fun isBracket(c: Char): Boolean =
        c == '(' || c == ')' || c == '{' || c == '}' || c == '[' || c == ']'

    private fun isOpener(c: Char): Boolean = c == '(' || c == '{' || c == '['

    private fun closerFor(opener: Char): Char = when (opener) {
        '(' -> ')'
        '{' -> '}'
        '[' -> ']'
        else -> ' '
    }

    private fun openerFor(closer: Char): Char = when (closer) {
        ')' -> '('
        '}' -> '{'
        ']' -> '['
        else -> ' '
    }

    private fun matchAt(text: String, code: BooleanArray, pos: Int): Pair<Int, Int>? {
        val c = text[pos]
        var depth = 0
        if (isOpener(c)) {
            val close = closerFor(c)
            for (i in pos until text.length) {
                if (!code[i]) continue
                when (text[i]) {
                    c -> depth++
                    close -> {
                        depth--
                        if (depth == 0) return pos to i
                    }
                }
            }
        } else {
            val open = openerFor(c)
            // Walking backwards, the starting closer itself makes depth -1;
            // the matching opener is where depth returns to 0.
            for (i in pos downTo 0) {
                if (!code[i]) continue
                when (text[i]) {
                    c -> depth--
                    open -> {
                        depth++
                        if (depth == 0) return i to pos
                    }
                }
            }
        }
        return null
    }

    /** Marks positions that are real code (not inside strings/chars/comments). */
    private fun codeMask(text: String): BooleanArray {
        val mask = BooleanArray(text.length) { true }
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '"' -> i = skipQuoted(text, mask, i, '"')
                '\'' -> i = skipQuoted(text, mask, i, '\'')
                '/' -> {
                    if (i + 1 < text.length && text[i + 1] == '/') {
                        var j = i
                        while (j < text.length && text[j] != '\n') { mask[j] = false; j++ }
                        i = j
                    } else if (i + 1 < text.length && text[i + 1] == '*') {
                        var j = i
                        while (j < text.length && !(text[j] == '*' && j + 1 < text.length && text[j + 1] == '/')) {
                            mask[j] = false
                            j++
                        }
                        if (j < text.length) {
                            mask[j] = false
                            if (j + 1 < text.length) mask[j + 1] = false
                            j += 2
                        }
                        i = j
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
        return mask
    }

    private fun skipQuoted(text: String, mask: BooleanArray, start: Int, quote: Char): Int {
        var j = start + 1
        mask[start] = false
        while (j < text.length && text[j] != '\n') {
            if (text[j] == '\\' && j + 1 < text.length) {
                mask[j] = false
                mask[j + 1] = false
                j += 2
                continue
            }
            if (text[j] == quote) {
                mask[j] = false
                return j + 1
            }
            mask[j] = false
            j++
        }
        // Unterminated literal: treat rest of the line as non-code.
        while (j < text.length && text[j] != '\n') {
            mask[j] = false
            j++
        }
        return j
    }
}
