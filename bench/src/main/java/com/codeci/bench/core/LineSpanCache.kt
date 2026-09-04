package com.codeci.bench.core

import androidx.compose.ui.text.AnnotatedString
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.utils.LanguageType
import com.codeci.ide.ui.utils.MultiLanguageSyntaxHighlighter

/**
 * Phase 25.1 candidate C-compose2 — per-line span cache.
 *
 * The whole point of the candidate: an edit re-tokenizes ONE line (O(line)),
 * not the document. The tokenizer is the SAME regex engine C-now uses
 * (`MultiLanguageSyntaxHighlighter`), applied per line, with a bounded LRU so
 * a 5 000-line file cannot grow the cache without limit.
 *
 * Honesty note (also in the results sheet): per-line lexing has no
 * cross-line state, so a multi-line comment/string is colored per line —
 * a cosmetic simplification that does not change the perf question.
 */
class LineSpanCache(
    private val language: LanguageType,
    private val theme: EditorThemeType,
    private val capacity: Int = DEFAULT_CAPACITY
) {

    private val cache = object : LinkedHashMap<Int, AnnotatedString>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, AnnotatedString>): Boolean =
            size > capacity
    }

    /** Colored [AnnotatedString] for [line] of [buffer], computed on miss. */
    fun get(buffer: DocumentBuffer, line: Int): AnnotatedString {
        synchronized(cache) {
            cache[line]?.let { return it }
        }
        val text = buffer.lineAt(line)
        val annotated = MultiLanguageSyntaxHighlighter.highlight(
            text, com.codeci.ide.ui.theme.getEditorTheme(theme), language, 0, text.length
        )
        synchronized(cache) { cache[line] = annotated }
        return annotated
    }

    /** Invalidate `[from, until)` (the [until] index is EXCLUSIVE, as returned by DocumentBuffer edits). */
    fun invalidateLines(from: Int, until: Int) {
        synchronized(cache) {
            for (line in from until until.coerceAtMost(Int.MAX_VALUE)) {
                cache.remove(line)
            }
        }
    }

    fun invalidateAll() = synchronized(cache) { cache.clear() }

    val size: Int get() = synchronized(cache) { cache.size }

    companion object {
        const val DEFAULT_CAPACITY = 1_200
    }
}
