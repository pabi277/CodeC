package com.codeci.ide.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Phase 27.1 — inline ghost-text completion (VS Code/Copilot style, adapted
 * to a phone): the top suggestion's remainder painted dimmed at the caret.
 * Pure logic so the accept/shrink/reject math is host-tested; the sora-side
 * renderer lives in `ui/editor/sora/GhostHintRenderer.kt`.
 *
 * Design law (docs/chat-phase27/PART_27_1_GHOST_TEXT.md):
 *  - G1: the ghost shows only when the top item's insert text starts with the
 *    word prefix at the caret, and only the SUFFIX is painted.
 *  - G2: typing never commits anything; the ghost is recomputed/shrunk, never
 *    auto-inserted.
 *  - G3: accept FULL (TAB ▸), WORD (→), or LINE; partial accept never crosses
 *    a newline in one word step.
 *  - G4: reject = keep typing / caret move / scroll / ESC — no state is kept.
 *  - G6: multi-line suggestions show their FIRST line only as the ghost.
 */
sealed class GhostState {
    /** Nothing painted (also the state the renderer clears hints for). */
    object Hidden : GhostState()

    /** [suffix] is painted right after the caret (already first-line-capped). */
    data class Visible(
        val suffix: String,
        val item: CompletionItem,
        /** Length of the prefix the suffix was computed against. */
        val prefixLength: Int
    ) : GhostState()
}

object GhostCompletion {

    /** File-size guard (G7): beyond this the pipeline emits no suggestions. */
    const val SOFT_FILE_CAP = 1_000_000

    /** Word/symbol/whitespace splitting for partial accept. Newline never rides a piece. */
    private val PIECE = Regex("\\w+|[^\\w\\s]+|[ \\t]+|\\n")

    /**
     * Compute the ghost for [caret] in [text]: the first item whose insert
     * text begins with the current word prefix and has more to say. The
     * suffix is capped at the first line (G6) and must be non-empty.
     */
    fun compute(text: String, caret: Int, items: List<CompletionItem>): GhostState {
        if (items.isEmpty()) return GhostState.Hidden
        val cursor = caret.coerceIn(0, text.length)
        val prefix = CodeCompletionEngine.currentPrefix(text, cursor)
        if (prefix.isEmpty()) return GhostState.Hidden
        for (item in items) {
            if (!item.insertText.startsWith(prefix)) continue
            val rest = item.insertText.substring(prefix.length)
            if (rest.isEmpty()) continue
            val firstLine = rest.substringBefore('\n')
            if (firstLine.isEmpty()) {
                // Only a newline remains after the prefix (rare: "foo(\n…");
                // a ghost made of pure newline is invisible — skip the item.
                continue
            }
            return GhostState.Visible(firstLine, item, prefix.length)
        }
        return GhostState.Hidden
    }

    /**
     * The next word-piece of [rest] (VS Code Ctrl+→ semantics): an identifier
     * run, a symbol run, or a whitespace run — never spanning a newline; a
     * leading newline is its own single-character piece.
     */
    fun nextWordPiece(rest: String): String {
        if (rest.isEmpty()) return ""
        if (rest[0] == '\n') return "\n"
        val m = PIECE.find(rest, 0) ?: return rest.substring(0, 1)
        return m.value
    }

    /** Insert text from the caret's identifier start through the next line break. */
    private fun linePiece(rest: String): String {
        val nl = rest.indexOf('\n')
        return if (nl < 0) rest else rest.substring(0, nl + 1)
    }

    /**
     * Apply an accept against [value]: the range
     * [caret - ghost.prefixLength, caret) is replaced by the FULL insert text
     * / the next WORD piece / the rest of the current LINE. The caret lands at
     * the end of what was inserted. Returns null when there is nothing valid
     * to accept (stale ghost, selection active).
     */
    fun accept(
        value: TextFieldValue,
        ghost: GhostState,
        granularity: AcceptGranularity = AcceptGranularity.FULL
    ): TextFieldValue? {
        val state = ghost as? GhostState.Visible ?: return null
        val text = value.text
        val sel = value.selection
        if (sel.start != sel.end) return null // never accept over a selection
        val caret = sel.start.coerceIn(0, text.length)
        val prefixStart = CodeCompletionEngine.prefixStart(text, caret)
        val livePrefix = text.substring(prefixStart, caret)
        // The ghost must still match what is really before the caret; a stale
        // snapshot (user typed on before the debounce) is rejected, not half-
        // inserted.
        if (!state.item.insertText.startsWith(livePrefix)) return null
        val rest = state.item.insertText.substring(livePrefix.length)
        if (rest.isEmpty()) return null
        return when (granularity) {
            // FULL replaces the typed prefix by the WHOLE insert text.
            AcceptGranularity.FULL -> TextFieldValue(
                text.substring(0, prefixStart) + state.item.insertText + text.substring(caret),
                TextRange(prefixStart + state.item.insertText.length)
            )
            // WORD/LINE keep the typed prefix in place and append only the
            // piece after the caret.
            else -> {
                val piece = when (granularity) {
                    AcceptGranularity.WORD -> nextWordPiece(rest)
                    else -> linePiece(rest)
                }
                TextFieldValue(
                    text.substring(0, caret) + piece + text.substring(caret),
                    TextRange(caret + piece.length)
                )
            }
        }
    }

    /**
     * The instant (non-debounced) shrink filter: as the user keeps typing,
     * the cached items are narrowed by the GROWN prefix without re-running the
     * engine — a couple of `startsWith`/matcher calls per keystroke, so the
     * strip and ghost visibly track every character (27.3 perf budget).
     */
    fun filterForPrefix(items: List<CompletionItem>, prefix: String): List<CompletionItem> {
        if (items.isEmpty() || prefix.isEmpty()) return emptyList()
        return items.filter {
            CodeCompletionEngine.labelMatches(it.label, prefix) && it.insertText != prefix
        }
    }
}

/** How much of the ghost to commit (G3). */
enum class AcceptGranularity { FULL, WORD, LINE }
