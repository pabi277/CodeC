package com.codeci.ide.ui.editor

/**
 * Phase 9 — pure find/replace engine for the editor find bar.
 *
 * Host-testable: no Android or Compose types. Literal search supports match
 * case and whole word; regex search additionally supports group references
 * (`$1`) in the replacement for Replace All. Match ranges are
 * `first..(endExclusive - 1)` index ranges over the current buffer text.
 */
data class FindOptions(
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
    val regex: Boolean = false
)

sealed class FindOutcome {
    data class Success(val matches: List<IntRange>) : FindOutcome()
    data class InvalidPattern(val message: String) : FindOutcome()
}

object FindReplaceEngine {

    fun search(text: String, query: String, options: FindOptions): FindOutcome {
        if (query.isEmpty()) return FindOutcome.Success(emptyList())
        if (options.regex) {
            val body = if (options.wholeWord) "\\b(?:$query)\\b" else query
            val pattern = if (options.matchCase) body else "(?i)$body"
            val regex = runCatching { Regex(pattern) }.getOrElse {
                return FindOutcome.InvalidPattern(it.message ?: "Invalid regular expression")
            }
            val matches = regex.findAll(text)
                .map { it.range }
                .filter { it.first <= it.last } // skip zero-width matches
                .toList()
            return FindOutcome.Success(matches)
        }
        return FindOutcome.Success(findLiteral(text, query, options))
    }

    private fun findLiteral(text: String, query: String, options: FindOptions): List<IntRange> {
        val result = ArrayList<IntRange>()
        var from = 0
        while (from <= text.length) {
            val idx = text.indexOf(query, from, ignoreCase = !options.matchCase)
            if (idx < 0) break
            val end = idx + query.length
            if (!options.wholeWord || isWordBounded(text, idx, end)) {
                result += idx until end
            }
            from = end
        }
        return result
    }

    private fun isWordBounded(text: String, start: Int, end: Int): Boolean {
        val before = text.getOrNull(start - 1)
        if (before != null && before.isWordPart()) return false
        val after = text.getOrNull(end)
        if (after != null && after.isWordPart()) return false
        return true
    }

    private fun Char.isWordPart(): Boolean = isLetterOrDigit() || this == '_'

    /** Index of the first match starting at/after [cursor], else the first match. */
    fun indexForCursor(matches: List<IntRange>, cursor: Int): Int {
        if (matches.isEmpty()) return -1
        val forward = matches.indexOfFirst { it.first >= cursor }
        return if (forward >= 0) forward else 0
    }

    fun nextIndex(active: Int, size: Int): Int =
        if (size <= 0) -1 else ((if (active < 0) -1 else active) + 1) % size

    fun prevIndex(active: Int, size: Int): Int =
        if (size <= 0) -1 else (((if (active < 0) 0 else active) - 1 + size) % size)

    fun replaceOne(text: String, match: IntRange, replacement: String): String =
        text.substring(0, match.first) + replacement + text.substring(match.last + 1)

    fun replaceAllLiteral(text: String, matches: List<IntRange>, replacement: String): String {
        if (matches.isEmpty()) return text
        val sb = StringBuilder(text.length)
        var last = 0
        matches.forEach { m ->
            sb.append(text, last, m.first)
            sb.append(replacement)
            last = m.last + 1
        }
        sb.append(text, last, text.length)
        return sb.toString()
    }

    /**
     * Regex Replace All with group-reference expansion in [replacement].
     * Returns null when [query] is an invalid pattern (caller keeps the text).
     */
    fun replaceAllRegex(text: String, query: String, replacement: String, matchCase: Boolean): String? {
        val regex = runCatching {
            Regex(if (matchCase) query else "(?i)$query")
        }.getOrNull() ?: return null
        return regex.replace(text, replacement)
    }

    /**
     * Single regex replacement honoring group references, applied to the match
     * at or after [from]. Returns the new text plus the caret offset after the
     * replacement, or null when the pattern is invalid / no match exists.
     */
    fun replaceFirstRegexFrom(text: String, from: Int, query: String, replacement: String, matchCase: Boolean): Pair<String, Int>? {
        val regex = runCatching {
            Regex(if (matchCase) query else "(?i)$query")
        }.getOrNull() ?: return null
        val match = regex.findAll(text).firstOrNull { it.range.first >= from } ?: return null
        val expanded = expandGroups(match, replacement)
        val out = text.substring(0, match.range.first) + expanded +
            text.substring(match.range.last + 1)
        return out to (match.range.first + expanded.length)
    }

    /** Expands `$0`..`$9`, `${n}`, `\n`, and `\t` references for a single match. */
    private fun expandGroups(match: MatchResult, replacement: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < replacement.length) {
            val c = replacement[i]
            if (c == '\\' && i + 1 < replacement.length) {
                val n = replacement[i + 1]
                when (n) {
                    'n' -> { sb.append('\n'); i += 2; continue }
                    't' -> { sb.append('\t'); i += 2; continue }
                    else -> { sb.append(c); i += 1; continue }
                }
            }
            if (c == '$' && i + 1 < replacement.length) {
                val next = replacement[i + 1]
                if (next == '{') {
                    val close = replacement.indexOf('}', i + 2)
                    if (close > 0) {
                        val idx = replacement.substring(i + 2, close).toIntOrNull()
                        if (idx != null) {
                            sb.append(match.groupValues.getOrElse(idx) { "" })
                            i = close + 1
                            continue
                        }
                    }
                } else if (next.isDigit()) {
                    sb.append(match.groupValues.getOrElse(next - '0') { "" })
                    i += 2
                    continue
                }
            }
            sb.append(c)
            i += 1
        }
        return sb.toString()
    }
}
