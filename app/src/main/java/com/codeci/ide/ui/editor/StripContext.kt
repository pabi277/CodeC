package com.codeci.ide.ui.editor

import com.codeci.ide.ui.utils.LanguageType

/**
 * Phase 27.2 — ONE state for the bar directly above the keyboard, replacing
 * the ad-hoc "editor keys vs run keys" juggling: Keys | Suggestions | Run
 * (| Hidden when the chevron toggle closed the row).
 *
 * The law, re-pinned as host tests (`StripContextTest`):
 *  - S6: an interactive run ALWAYS wins the strip (23.2 semantics preserved).
 *  - S1: with ≥ 2 candidates the strip shows chips; 1 candidate stays in
 *    key mode (the ghost covers it) with the dual-mood TAB ▸.
 *  - S3: suggestions never imprison the keys — dismissal ("⌨" cap, swipe
 *    down, ESC, unmatched keyring) is per-identifier and the NEXT identifier
 *    re-arms the strip.
 *  - No suggestions while a text selection is active, while the file is past
 *    the soft size cap, or when the Settings master/strip switch is off.
 *  - The row HEIGHT never changes between contexts (no IME flicker).
 */
sealed class StripContext {
    /** The strip is not shown at all (visibility toggle off). */
    object Hidden : StripContext()

    /** Interactive run waiting for stdin: the RunKeySet caps (23.2). */
    object Run : StripContext()

    /** The per-language editor keys, possibly with dual-mood TAB/→ caps. */
    data class Keys(
        val language: LanguageType?,
        val surface: CompletionSurface
    ) : StripContext()

    /** Multi-candidate completion: chips replace the key caps. */
    data class Suggestions(val chips: List<SuggestionChip>) : StripContext()
}

/** One rendered chip (S7: label ≤ 18 chars + glyph + kind kept for tooltips). */
data class SuggestionChip(
    val label: String,
    val detail: String?,
    val kind: CompletionKind,
    /** Original engine item committed on tap. */
    val item: CompletionItem,
    /** True for the item that ALSO backs the ghost (first chip, S1). */
    val ghostBacked: Boolean
) {
    val glyph: String
        get() = when (kind) {
            CompletionKind.SNIPPET -> "ƒ"
            CompletionKind.KEYWORD -> "λ"
            CompletionKind.IDENTIFIER -> "≠"
        }

    val displayLabel: String
        get() =
            if (label.length <= 18) label else label.substring(0, 17) + "…"
}

object SuggestionStripModel {

    const val MAX_CHIPS = 8

    /**
     * Build the chip pipeline (pure; S1/S2/S7): engine order kept as the base
     * rank, then (a) direct-prefix match boost and (b) recency-of-use boost
     * (in-memory accept counts). The ghost's item is pinned FIRST (S1).
     */
    fun buildStripModel(
        items: List<CompletionItem>,
        ghost: GhostState,
        acceptCounts: Map<String, Int> = emptyMap(),
        maxChips: Int = MAX_CHIPS
    ): List<SuggestionChip> {
        if (items.isEmpty()) return emptyList()
        val ghostLabel = (ghost as? GhostState.Visible)?.item?.label
        // Ghost item pinned first, then recency boost; the stable sort keeps
        // the engine's order for ties. The live-prefix shrink happens earlier
        // (GhostCompletion.filterForPrefix), so order stability here matters.
        val ranked = items.sortedWith(
            compareByDescending<CompletionItem> { it.label == ghostLabel }
                .thenByDescending { acceptCounts[it.label] ?: 0 }
        )
        return ranked.take(maxChips.coerceIn(1, MAX_CHIPS)).map {
            SuggestionChip(it.label, it.detail, it.kind, it, it.label == ghostLabel)
        }
    }

    /**
     * The strip context from raw state (the ONLY place surfaces and contexts
     * are reconciled — tests pin every branch).
     *
     * @param stripVisible the user's chevron toggle for the whole row.
     * @param runWaiting an interactive run is waiting for stdin (S6).
     * @param items the CURRENT (possibly prefix-shrunk) candidate list.
     * @param dismissedAnchor the per-identifier dismissal: when the caret's
     *        identifier still starts at this offset, the strip stays in key
     *        mode (S4) until the identifier boundary is crossed.
     * @param prefixAnchor offset where the current identifier starts.
     * @param hasSelection true while text is selected (G7/S-matrix row).
     * @param textLength for the G7 soft file cap.
     */
    fun stripContextFor(
        stripVisible: Boolean,
        runWaiting: Boolean,
        settings: CompletionSettings,
        items: List<CompletionItem>,
        ghost: GhostState,
        dismissedAnchor: Int?,
        prefixAnchor: Int,
        hasSelection: Boolean,
        textLength: Int,
        language: LanguageType?,
        acceptCounts: Map<String, Int> = emptyMap()
    ): StripContext {
        if (!stripVisible) return StripContext.Hidden
        if (runWaiting) return StripContext.Run
        val usable = settings.master && settings.strip &&
            !hasSelection &&
            textLength <= GhostCompletion.SOFT_FILE_CAP &&
            dismissedAnchor != prefixAnchor
        if (!usable) return StripContext.Keys(language, CompletionSurface.NONE)
        if (items.size >= 2) {
            val chips = buildStripModel(items, ghost, acceptCounts)
            if (chips.size >= 2) return StripContext.Suggestions(chips)
        }
        return StripContext.Keys(language, if (ghost is GhostState.Visible) CompletionSurface.GHOST_ONLY else CompletionSurface.NONE)
    }
}
