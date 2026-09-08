package com.codeci.ide.ui.editor.lsp

/**
 * Phase 31.5 — minimal LSP response parser.
 *
 * We don't pull in a JSON library (org.json is on the classpath via
 * Android, but the host JVM tests would need a shim; the kotlin-stdlib
 * has no JSON). The LSP wire format for completion is small enough
 * (a `result: Array<CompletionItem>`) that a hand-rolled parser
 * that reads the five fields we care about is honest.
 *
 * The parser handles:
 *  - The `Content-Length`-framed body (a single JSON object).
 *  - The `id` field (Int, may be `null` for notifications).
 *  - The `error` field (LSP error object → `message: String`).
 *  - The `result` field for `textDocument/completion`: either
 *    a `CompletionItem[]` (the LSP `CompletionList` form with
 *    `isIncomplete` and `items` is also accepted — we just take
 *    `items` and ignore the `isIncomplete` flag for now).
 *
 * It does NOT handle:
 *  - JSON streaming (we get one message at a time via
 *    `LspStdioClient.readOneMessage`).
 *  - UTF-16 escape sequences in strings (the only place a phone
 *    file would have them is in source code, not in metadata;
 *    we read body bytes as UTF-8, which is correct for all
 *    LSP-spec JSON).
 */

/**
 * The response of one framed LSP message.
 *
 * `error` is the LSP error string (e.g. "Method not found"),
 * or `null` on success. `result` is the raw JSON of the
 * `result` field, left unparsed for the caller to interpret
 * (LspResponseParser.parseCompletionList is the only known
 * interpreter in the 31.5 surface).
 *
 * Top-level (not nested in `LspResponseParser`) so the stdio
 * client can reference it as `LspResponse` without a
 * `LspResponseParser.` prefix.
 */
data class LspResponse(
    val id: Int?,
    val error: String?,
    val result: String?,
    val method: String? = null,
)

internal object LspResponseParser {

    fun parse(body: String): LspResponse {
        val id = matchIntField(body, "\"id\"")
        val method = matchStringField(body, "\"method\"")
        val errorRaw = matchField(body, "\"error\"")
        val error = when {
            errorRaw == null -> null
            errorRaw.trimStart().startsWith("{") ->
                matchStringField(errorRaw, "\"message\"") ?: errorRaw.trim()
            else -> errorRaw.trim()
        }
        val result = matchField(body, "\"result\"")
        return LspResponse(id = id, error = error, result = result, method = method)
    }

    /**
     * Parse an LSP `CompletionList` (or just an `Array<CompletionItem>`)
     * into the wire-side [LspItemMapping.LspShape] we feed to the
     * orchestrator. Reads only the five fields [LspItemMapping.LspShape]
     * cares about — `label`, `kind`, `detail`, `insertText`,
     * `filterText` — and drops everything else.
     */
    fun parseCompletionList(resultJson: String?): List<LspItemMapping.LspShape> {
        if (resultJson.isNullOrBlank()) return emptyList()
        // Strip a possible `CompletionList` wrapper: `{ "isIncomplete": false, "items": [...] }`
        val itemsStart = findItemsArray(resultJson) ?: return emptyList()
        // itemsStart is the offset of the first `[` in the items array.
        return parseCompletionItemArray(resultJson, itemsStart)
    }

    private fun findItemsArray(json: String): Int? {
        // Bare `CompletionItem[]` (no CompletionList wrapper).
        var i = 0
        while (i < json.length && json[i].isWhitespace()) i++
        if (i < json.length && json[i] == '[') return i
        // Look for the substring `"items":[` (allowing whitespace).
        val key = "\"items\""
        var from = 0
        while (true) {
            val idx = json.indexOf(key, from)
            if (idx < 0) return null
            val afterKey = idx + key.length
            // `"items" : [ ... ]` — colon is required JSON, was skipped
            // by the first parser and every CompletionList test returned 0.
            var i = afterKey
            while (i < json.length && json[i].isWhitespace()) i++
            if (i < json.length && json[i] == ':') {
                i++
                while (i < json.length && json[i].isWhitespace()) i++
            }
            if (i < json.length && json[i] == '[') return i
            from = afterKey
        }
    }

    private fun parseCompletionItemArray(json: String, arrayStart: Int): List<LspItemMapping.LspShape> {
        val out = ArrayList<LspItemMapping.LspShape>()
        var i = arrayStart + 1
        while (i < json.length) {
            // skip whitespace + commas
            while (i < json.length && (json[i].isWhitespace() || json[i] == ',')) i++
            if (i >= json.length || json[i] == ']') break
            if (json[i] != '{') {
                // Malformed — skip one char to avoid infinite loop.
                i++
                continue
            }
            val end = findMatchingBrace(json, i) ?: break
            val obj = json.substring(i, end + 1)
            out.add(parseCompletionItem(obj))
            i = end + 1
        }
        return out
    }

    private fun parseCompletionItem(obj: String): LspItemMapping.LspShape {
        val label = matchStringField(obj, "\"label\"") ?: ""
        val kind = matchIntField(obj, "\"kind\"")
        val detail = matchStringField(obj, "\"detail\"")
        val insertText = matchStringField(obj, "\"insertText\"")
        val filterText = matchStringField(obj, "\"filterText\"")
        return LspItemMapping.LspShape(
            label = label,
            kind = kind,
            detail = detail,
            insertText = insertText,
            filterText = filterText,
        )
    }

    /**
     * Find the matching `}` for the `{` at [openIdx]. Handles nested
     * braces and string-quoted braces (a `}` inside a JSON string
     * does not close the object). Returns null on malformed input.
     */
    private fun findMatchingBrace(json: String, openIdx: Int): Int? {
        var depth = 0
        var inString = false
        var escape = false
        var i = openIdx
        while (i < json.length) {
            val c = json[i]
            if (escape) { escape = false; i++; continue }
            if (c == '\\') { escape = true; i++; continue }
            if (c == '"') { inString = !inString; i++; continue }
            if (!inString) {
                when (c) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return null
    }

    // ---------- field readers ----------

    /**
     * Read the value of a top-level-ish JSON field. Returns the
     * raw value text (without surrounding whitespace). The caller
     * decides what JSON type they want.
     */
    private fun matchField(json: String, key: String): String? {
        val idx = json.indexOf(key)
        if (idx < 0) return null
        val colon = json.indexOf(':', idx + key.length)
        if (colon < 0) return null
        var i = colon + 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length) return null
        // If it's a string, read until the closing quote. Otherwise
        // read until the next `,` or `}` at the same brace depth.
        if (json[i] == '"') {
            val sb = StringBuilder()
            i++
            while (i < json.length) {
                val c = json[i]
                if (c == '\\' && i + 1 < json.length) {
                    sb.append(c).append(json[i + 1])
                    i += 2
                    continue
                }
                if (c == '"') return sb.toString()
                sb.append(c)
                i++
            }
            return sb.toString()
        }
        // Non-string: read until `,` or `}` at depth 0.
        var depth = 0
        val start = i
        while (i < json.length) {
            when (json[i]) {
                '{', '[' -> depth++
                '}', ']' -> {
                    if (depth == 0) return json.substring(start, i)
                    depth--
                }
                ',' -> if (depth == 0) return json.substring(start, i)
            }
            i++
        }
        return json.substring(start, i)
    }

    private fun matchStringField(json: String, key: String): String? = matchField(json, key)

    private fun matchIntField(json: String, key: String): Int? {
        val v = matchField(json, key) ?: return null
        // Strip leading sign and parse. null literal returns null.
        val trimmed = v.trim()
        if (trimmed == "null") return null
        return trimmed.toIntOrNull()
    }
}
