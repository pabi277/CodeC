package com.codeci.ide.ui.keyboard

import com.codeci.ide.ui.editor.EditorKeyDef
import com.codeci.ide.ui.editor.KeyStripStorage

/**
 * Phase 28.2 — the keyboard layout file format + loader, PURE so schema
 * rules are host-tested. A layout file is:
 *
 * ```json
 * {"heightScale":1.0,"rows":[[ <cap>, <cap>, ... ], [ ... ]]}
 * ```
 *
 * and a `<cap>` is EXACTLY a 26.1 key-strip cap object (`EditorKeyDef` JSON,
 * see [KeyStripStorage]) plus two optional fields: `"w"` (width weight) and
 * `"repeat"` (hold-repeat). Reusing the strip schema is not laziness — it is
 * the plan's law that "the strip IS row 1..n of the same engine": a user
 * strip edit and a dev JSON edit speak one language, and one parser owns it.
 *
 * Failure policy (spec §2.1): a corrupt file (bad JSON anywhere at the outer
 * level) yields null → the caller falls back to the built-in default; a
 * single malformed ROW inside an otherwise valid file drops that row only,
 * so a typo in row 3 never deletes the keyboard. Missing `w`/`repeat`
 * derive from the shared model rules instead of failing.
 */
object KeyboardLayoutCodec {

    fun serialize(layout: KeyboardLayout): String = buildString {
        append("{\"heightScale\":").append(layout.heightScale).append(",\"rows\":[")
        layout.rows.forEachIndexed { ri, row ->
            if (ri > 0) append(',')
            // KeyStripStorage.serialize already renders one COMPLETE cap
            // array — one bracket level only.
            append(rowOf(KeyStripStorage.serialize(row.map { it.def }), row))
        }
        append("]}")
    }

    /** Insert the per-cap extras after each cap's closing brace. */
    private fun rowOf(capsJson: String, row: List<KeycapModel>): String {
        // serialize() emits `{...},{...}` — splice ",\"w\":N,\"repeat\":B"
        // into each object's tail; the strip parser ignores unknown fields.
        val sb = StringBuilder(capsJson.length + row.size * 24)
        var capIdx = 0
        var i = 0
        var depth = 0
        var inStr = false
        var esc = false
        while (i < capsJson.length) {
            val c = capsJson[i]
            if (esc) { esc = false; sb.append(c); i++; continue }
            if (c == '\\' && inStr) { esc = true; sb.append(c); i++; continue }
            if (c == '"') inStr = !inStr
            if (!inStr) {
                when (c) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val cap = row[capIdx++]
                            sb.append(",\"w\":").append(cap.widthWeight)
                            sb.append(",\"repeat\":").append(cap.repeat)
                        }
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /** Parse a layout file or return null (corrupt → caller's built-in). */
    fun deserialize(json: String?): KeyboardLayout? {
        if (json.isNullOrBlank()) return null
        return try {
            parseLayout(json)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLayout(text: String): KeyboardLayout? {
        val trimmed = text.trim()
        require(trimmed.startsWith('{') && trimmed.endsWith('}')) { "not a layout object" }
        val heightScale = Regex("\"heightScale\"\\s*:\\s*(-?[0-9]*\\.?[0-9]+)")
            .find(trimmed)?.groupValues?.get(1)?.toFloatOrNull()
            ?: KeyboardLayout.HEIGHT_SCALE_DEFAULT
        val rowsStart = trimmed.indexOf("\"rows\"")
        require(rowsStart >= 0) { "no rows" }
        val outer = extractArray(trimmed, trimmed.indexOf('[', rowsStart))
        val rows = mutableListOf<List<KeycapModel>>()
        forEachInnerArray(outer) { rowJson ->
            val defs = KeyStripStorage.deserialize(rowJson) ?: return@forEachInnerArray // drop bad row
            if (defs.isEmpty()) return@forEachInnerArray
            rows += rowCaps(defs, rowJson)
        }
        if (rows.isEmpty()) return null
        if (rows.size > MAX_ROWS || rows.any { it.isEmpty() || it.size > MAX_CAPS_PER_ROW }) return null
        return KeyboardLayout(rows, heightScale.coerceIn(KeyboardLayout.HEIGHT_SCALE_MIN, KeyboardLayout.HEIGHT_SCALE_MAX))
    }

    /** Attach the derived-or-file `w`/`repeat` to each parsed def. */
    private fun rowCaps(defs: List<EditorKeyDef>, rowJson: String): List<KeycapModel> {
        // Split the row into per-cap object texts (same brace balance scan).
        val capJsons = mutableListOf<String>()
        var i = 0
        var depth = 0
        var inStr = false
        var esc = false
        var start = -1
        while (i < rowJson.length) {
            val c = rowJson[i]
            if (esc) { esc = false; i++; continue }
            if (c == '\\' && inStr) { esc = true; i++; continue }
            if (c == '"') inStr = !inStr
            if (!inStr) {
                if (c == '{') { if (depth == 0) start = i; depth++ }
                else if (c == '}') {
                    depth--
                    if (depth == 0 && start >= 0) { capJsons += rowJson.substring(start, i + 1); start = -1 }
                }
            }
            i++
        }
        return defs.mapIndexed { idx, def ->
            val capText = capJsons.getOrNull(idx).orEmpty()
            val w = Regex("\"w\"\\s*:\\s*(-?[0-9]*\\.?[0-9]+)").find(capText)
                ?.groupValues?.get(1)?.toFloatOrNull()
            val repeat = Regex("\"repeat\"\\s*:\\s*(true|false)").find(capText)
                ?.groupValues?.get(1)?.toBooleanStrictOrNull()
            KeycapModel(
                def,
                w?.coerceIn(0.5f, 6f) ?: KeycapModel.weightFor(def),
                repeat ?: KeycapModel.repeatFor(def)
            )
        }
    }

    /** Extract the balanced `[ ... ]` starting at [open]. */
    private fun extractArray(text: String, open: Int): String {
        require(open in text.indices && text[open] == '[') { "expected '['" }
        var i = open
        var depth = 0
        var inStr = false
        var esc = false
        while (i < text.length) {
            val c = text[i]
            if (esc) { esc = false; i++; continue }
            if (c == '\\' && inStr) { esc = true; i++; continue }
            if (c == '"') inStr = !inStr
            if (!inStr) {
                if (c == '[') depth++
                else if (c == ']') {
                    depth--
                    if (depth == 0) return text.substring(open, i + 1)
                }
            }
            i++
        }
        error("unterminated array")
    }

    /** Visit every direct inner `[ ... ]` of an outer array's text. */
    private fun forEachInnerArray(outer: String, visit: (String) -> Unit) {
        var i = 0
        var depth = 0
        var inStr = false
        var esc = false
        var start = -1
        while (i < outer.length) {
            val c = outer[i]
            if (esc) { esc = false; i++; continue }
            if (c == '\\' && inStr) { esc = true; i++; continue }
            if (c == '"') inStr = !inStr
            if (!inStr) {
                when (c) {
                    '[' -> {
                        depth++
                        if (depth == 2) start = i
                    }
                    ']' -> {
                        if (depth == 2 && start >= 0) visit(outer.substring(start, i + 1))
                        depth--
                    }
                }
            }
            i++
        }
    }

    private const val MAX_ROWS = 8
    private const val MAX_CAPS_PER_ROW = 16
}
