package com.codeci.ide.ui.editor.lsp

import com.codeci.ide.ui.editor.CompletionItem
import com.codeci.ide.ui.editor.CompletionKind

/**
 * Phase 31.1 — maps LSP `CompletionItem` JSON-ish shapes (we use a thin
 * internal record to keep the manager host-testable) into CodeC's existing
 * [CompletionItem] so the rest of the completion pipeline (Phase 27
 * GhostCompletion / StripContext / CompletionPolicy / CodeCCompletionComponent)
 * does not change.
 *
 * The mapping is **deliberately conservative**:
 *  - LSP `CompletionItemKind` collapses to our 3-tag `CompletionKind`
 *    (Snippet / Keyword / Identifier) because Phase 27 has no other kind;
 *    richer chips (functions, classes, fields) ride as `Identifier` with a
 *    `detail` string the strip paints. That keeps the policy + the
 *    `MAX_ITEMS` math identical to the snippet world.
 *  - `insertText` is the LSP field when present, falling back to `label`.
 *  - `filterText` is preferred over `label` for the matching prefix ONLY
 *    when the LSP client wants it; the CodeC engine's matching is
 *    case-insensitive on the LABEL (22.6 law) so we just feed both.
 *  - No `additionalTextEdit` / `textEdit` is honoured — they mutate text
 *    outside the accept span and the Phase 30 device round proved the
 *    accept span already covers every sane case (`#in` → `#include`).
 *    Honouring LSP `textEdit` would re-introduce the `##include` bug.
 *  - `sortText` / `preselect` are dropped — CodeC re-ranks by its own
 *    pack/identifier/keyword pipeline (Phase 30). LSP items are PRE-pended
 *    so they appear BEFORE the keyword fallback (snippets still outrank
 *    them, Emmet still outranks both).
 */
object LspItemMapping {

    /**
     * The minimum LSP-CompletionItem shape this mapping reads. The real
     * `org.eclipse.lsp4j.CompletionItem` has 30+ fields; we only need five.
     * Keeping the shape local means [LspManager] can be tested with plain
     * Kotlin data and the wire adapter is one tiny file.
     */
    data class LspShape(
        val label: String,
        val kind: Int?,
        val detail: String?,
        val insertText: String?,
        val filterText: String?,
    )

    /** The [CompletionItemKind] codes we treat as our 3-tag [CompletionKind]. */
    fun mapKind(lspKind: Int?): CompletionKind = when (lspKind) {
        // LSP CompletionItemKind: 1 Text, 2 Method, 3 Function, 4 Constructor,
        // 5 Field, 6 Variable, 7 Class, 8 Interface, 9 Module, 10 Property,
        // 11 Unit, 12 Value, 13 Enum, 14 Keyword, 15 Snippet, 16 Color, …
        15 -> CompletionKind.SNIPPET
        14 -> CompletionKind.KEYWORD
        else -> CompletionKind.IDENTIFIER
    }

    /**
     * Map a single LSP item to CodeC's model. `displayLabel` is what the
     * strip/panel/ghost paint; `matchPrefix` is the [CodeCompletionEngine]'s
     * case-insensitive matcher input — we return the label (not
     * filterText) so a pack label like `int` doesn't suddenly outrank an
     * LSP `Integer.parseInt` whose filterText is `Integer.parseInt`.
     */
    fun toCompletionItem(shape: LspShape): CompletionItem {
        val insert = shape.insertText?.takeIf { it.isNotEmpty() } ?: shape.label
        val kind = mapKind(shape.kind)
        // The detail doubles as a chip's right-edge "from clangd" badge.
        // Kept short (LSP servers often return a long signature) so it
        // fits the strip; truncate defensively to keep the row from
        // wrapping.
        val detail = shape.detail?.let { truncate(it, MAX_DETAIL) }
        return CompletionItem(
            label = shape.label,
            insertText = insert,
            kind = kind,
            detail = detail,
            // LSP items use the engine's accept span (`replaceSpanLength`),
            // NOT a hand-rolled length — see the file KDoc on why we do
            // not honour LSP `textEdit`.
            replaceLength = null,
            caretOffset = null,
        )
    }

    /** A short, paintable summary for the strip/panel right edge. */
    private const val MAX_DETAIL = 32

    private fun truncate(text: String, max: Int): String {
        // First line only (LSP detail often includes a parameter list with
        // embedded newlines). Trim to fit.
        val oneline = text.substringBefore('\n').trim()
        return if (oneline.length <= max) oneline else oneline.take(max - 1) + "…"
    }
}
