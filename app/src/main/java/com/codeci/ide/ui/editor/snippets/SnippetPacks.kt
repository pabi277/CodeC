package com.codeci.ide.ui.editor.snippets

import com.codeci.ide.ui.editor.CompletionItem
import com.codeci.ide.ui.editor.CompletionKind

/**
 * Phase 30.1 — one snippet-pack entry, exactly as a VS Code snippet file
 * stores it (name → prefix(es) + body + description).
 */
data class SnippetEntry(
    /** Upstream entry name ("main() template") — kept for detail fallback. */
    val name: String,
    /** Trigger prefixes; one [CompletionItem] per prefix (first wins duplicates). */
    val prefixes: List<String>,
    /** Body lines already joined with `\n` (VS Code array or scalar form). */
    val body: String,
    val description: String?
)

/**
 * Phase 30.1 — friendly-snippets JSON → [CompletionItem].
 *
 * Pure Kotlin (see [SnippetJson] for why the reader is hand-rolled): the pack
 * files are objects keyed by snippet name, whose `prefix` may be a string or
 * an array, whose `body` may be a string or an array of lines, and whose
 * `description` may be either. Bodies go through [SnippetSyntax] so tabstops
 * become plain text and the caret lands on the first stop (plan rule S3).
 *
 * The LABEL is the prefix (what you type — the Acode/nvim-cmp convention) and
 * the DETAIL is the pack's description (or the first body line), so a chip
 * reads `for` and a long-press/panel row says what it really inserts.
 */
object SnippetPacks {

    /** Entries with many prefixes stay readable on a phone strip. */
    const val MAX_PREFIXES_PER_ENTRY = 3

    /** Chip tooltips and panel rows are one line; long descriptions get cut. */
    const val DETAIL_MAX = 64

    /** Parses one pack file; malformed JSON yields no entries (never throws). */
    fun parse(json: String): List<SnippetEntry> {
        val root = SnippetJson.parse(json) as? JsonValue.Obj ?: return emptyList()
        val out = ArrayList<SnippetEntry>(root.entries.size)
        for ((name, value) in root.entries) {
            val obj = value as? JsonValue.Obj ?: continue
            val lines = SnippetJson.asStringList(obj["body"])
            if (lines.isEmpty()) continue
            val prefixes = SnippetJson.asStringList(obj["prefix"])
                .filter { it.isNotBlank() }
                .take(MAX_PREFIXES_PER_ENTRY)
            if (prefixes.isEmpty()) continue
            val description = SnippetJson.asStringList(obj["description"])
                .joinToString(" ")
                .ifBlank { null }
            out += SnippetEntry(name, prefixes, lines.joinToString("\n"), description)
        }
        return out
    }

    /**
     * Resolves [entries] into completion items. One item per distinct prefix;
     * the FIRST entry that claims a prefix wins (the library lists a language's
     * main pack before its doc/debug packs, so the common snippet beats the
     * exotic one). Blank resolutions are dropped.
     */
    fun items(entries: List<SnippetEntry>, fileName: String? = null): List<CompletionItem> {
        val out = ArrayList<CompletionItem>(entries.size)
        val seen = HashSet<String>()
        for (entry in entries) {
            val resolved = SnippetSyntax.resolve(entry.body, fileName)
            val text = resolved.text
            if (text.isBlank()) continue
            val detail = detailFor(entry, text)
            for (prefix in entry.prefixes) {
                if (!seen.add(prefix)) continue
                out += CompletionItem(
                    label = prefix,
                    insertText = text,
                    kind = CompletionKind.SNIPPET,
                    detail = detail,
                    caretOffset = resolved.caretOffset
                )
            }
        }
        return out
    }

    /** Description when the pack has one, else the first body line, else the name. */
    private fun detailFor(entry: SnippetEntry, text: String): String {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val raw = entry.description?.takeIf { it.isNotBlank() }
            ?: firstLine.takeIf { it.length >= 4 }
            ?: entry.name
        val oneLine = raw.replace('\n', ' ').replace('\t', ' ').trim()
        return if (oneLine.length <= DETAIL_MAX) oneLine
        else oneLine.substring(0, DETAIL_MAX - 1).trimEnd() + "…"
    }
}
