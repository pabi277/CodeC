package com.codeci.ide.ui.editor

/**
 * Phase 26.1 — persistence for user-editable key sets.
 * Stored as JSON `[{"label":"TAB","type":"tab","wide":true, ...}]` in DataStore.
 * Manual JSON building/parsing (no org.json on host) mirroring ProjectConfig's approach.
 * Invalid JSON falls back to defaults silently (single log line).
 */
object KeyStripStorage {

    fun serialize(defs: List<EditorKeyDef>): String = buildString {
        append('[')
        defs.forEachIndexed { idx, def ->
            if (idx > 0) append(',')
            append('{')
            append("\"label\":").append(escape(def.label))
            append(",\"wide\":").append(def.wide)
            when (val k = def.key) {
                is EditorKey.Insert -> {
                    append(",\"type\":\"insert\"")
                    append(",\"text\":").append(escape(k.text))
                }
                is EditorKey.Pair -> {
                    append(",\"type\":\"pair\"")
                    append(",\"open\":").append(escape(k.open))
                    append(",\"close\":").append(escape(k.close))
                }
                EditorKey.Tab -> append(",\"type\":\"tab\"")
                EditorKey.Delete -> append(",\"type\":\"delete\"")
                EditorKey.DeleteWord -> append(",\"type\":\"deleteWord\"")
                EditorKey.CommentToggle -> append(",\"type\":\"commentToggle\"")
                // Phase 27.1 — the dual-mood caps are transient (ghost-driven);
                // persisted JSON always stores the PHYSICAL key they stand for.
                EditorKey.GhostAccept -> append(",\"type\":\"tab\"")
                EditorKey.GhostAcceptWord -> {
                    append(",\"type\":\"caret\"")
                    append(",\"move\":").append(escape(EditorKey.Caret.Move.RIGHT.name))
                }
                is EditorKey.Caret -> {
                    append(",\"type\":\"caret\"")
                    append(",\"move\":").append(escape(k.move.name))
                }
            }
            def.popup?.let { p ->
                append(",\"popup\":").append(encodeKey(p))
            }
            def.swipeUp?.let { s ->
                append(",\"swipeUp\":").append(encodeKey(s))
            }
            def.swipeDown?.let { s ->
                append(",\"swipeDown\":").append(encodeKey(s))
            }
            append('}')
        }
        append(']')
    }

    private fun encodeKey(key: EditorKey): String = buildString {
        append('{')
        when (key) {
            is EditorKey.Insert -> {
                append("\"type\":\"insert\"")
                append(",\"text\":").append(escape(key.text))
            }
            is EditorKey.Pair -> {
                append("\"type\":\"pair\"")
                append(",\"open\":").append(escape(key.open))
                append(",\"close\":").append(escape(key.close))
            }
            EditorKey.Tab -> append("\"type\":\"tab\"")
            EditorKey.Delete -> append("\"type\":\"delete\"")
            EditorKey.DeleteWord -> append("\"type\":\"deleteWord\"")
            EditorKey.CommentToggle -> append("\"type\":\"commentToggle\"")
            // Phase 27.1 — transient ghost caps persist as their physical key.
            EditorKey.GhostAccept -> append("\"type\":\"tab\"")
            EditorKey.GhostAcceptWord -> {
                append("\"type\":\"caret\"")
                append(",\"move\":").append(escape(EditorKey.Caret.Move.RIGHT.name))
            }
            is EditorKey.Caret -> {
                append("\"type\":\"caret\"")
                append(",\"move\":").append(escape(key.move.name))
            }
        }
        append('}')
    }

    /**
     * Deserializes JSON or returns null on any error (invalid => fallback to defaults).
     * Host-testable without Android.
     */
    fun deserialize(json: String): List<EditorKeyDef>? {
        return try {
            parseArray(json.trim())
        } catch (e: Exception) {
            // Single log line as per spec.
            try {
                android.util.Log.w("KeyStripStorage", "invalid key strip JSON, restoring defaults: ${e.message}")
            } catch (_: Throwable) {
                // Host test without android.util.Log
                println("KeyStripStorage: invalid JSON, restoring defaults: ${e.message}")
            }
            null
        }
    }

    private fun parseArray(text: String): List<EditorKeyDef> {
        var i = 0
        fun skipWs() { while (i < text.length && text[i].isWhitespace()) i++ }
        fun expect(c: Char) {
            skipWs()
            require(i < text.length && text[i] == c) { "expected '$c' at $i" }
            i++
        }
        fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (i < text.length) {
                when (val ch = text[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(i < text.length) { "invalid escape" }
                        when (val e = text[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                require(i + 4 <= text.length) { "invalid unicode escape" }
                                val hex = text.substring(i, i + 4)
                                sb.append(hex.toInt(16).toChar())
                                i += 4
                            }
                            else -> error("invalid escape \\$e")
                        }
                    }
                    else -> {
                        require(ch >= ' ') { "invalid string char" }
                        sb.append(ch)
                    }
                }
            }
            error("unterminated string")
        }
        fun parseNestedObject(): Map<String, Any> {
            expect('{')
            skipWs()
            val map = mutableMapOf<String, Any>()
            if (i < text.length && text[i] == '}') { i++; return map }
            while (true) {
                val key = parseString()
                expect(':')
                skipWs()
                val value: Any = when {
                    i < text.length && text[i] == '"' -> parseString()
                    else -> {
                        val start = i
                        while (i < text.length && text[i] != ',' && text[i] != '}') i++
                        text.substring(start, i).trim()
                    }
                }
                map[key] = value
                skipWs()
                when {
                    i < text.length && text[i] == ',' -> { i++; skipWs() }
                    i < text.length && text[i] == '}' -> { i++; break }
                    else -> error("expected ',' or '}' in nested object")
                }
            }
            return map
        }
        fun parseObject(): MutableMap<String, String> {
            // We parse as map of raw values for our limited schema: values are string/boolean/object
            // For simplicity, we parse fully with recursion.
            expect('{')
            skipWs()
            val map = mutableMapOf<String, Any>()
            if (i < text.length && text[i] == '}') { i++; return mutableMapOf() }
            while (true) {
                val key = parseString()
                expect(':')
                skipWs()
                val value: Any = when {
                    i < text.length && text[i] == '"' -> parseString()
                    i < text.length && text[i] == '{' -> parseNestedObject()
                    i < text.length && text[i] == '[' -> error("nested arrays not supported")
                    else -> {
                        val start = i
                        while (i < text.length && text[i] != ',' && text[i] != '}') i++
                        text.substring(start, i).trim().also {
                            require(it.isNotEmpty()) { "invalid value" }
                        }
                    }
                }
                map[key] = value
                skipWs()
                when {
                    i < text.length && text[i] == ',' -> { i++; skipWs() }
                    i < text.length && text[i] == '}' -> { i++; break }
                    else -> error("expected ',' or '}'")
                }
            }
            // Convert to string map for outer? But we need typed.
            // We'll keep as Any map and convert later via helper.
            @Suppress("UNCHECKED_CAST")
            return map as MutableMap<String, String>
        }

        // Top-level array parse using above but simpler: hand-parse defs
        skipWs()
        expect('[')
        skipWs()
        val result = mutableListOf<EditorKeyDef>()
        if (i < text.length && text[i] == ']') { i++; skipWs(); require(i == text.length) { "trailing chars" }; return result }
        while (true) {
            skipWs()
            // Parse one def object manually with raw parsing to handle nested popup objects.
            // We'll extract substring for one object by balancing braces.
            require(i < text.length && text[i] == '{') { "expected '{' for def" }
            val start = i
            var depth = 0
            var inString = false
            var escape = false
            while (i < text.length) {
                val c = text[i]
                if (escape) { escape = false; i++; continue }
                if (c == '\\' && inString) { escape = true; i++; continue }
                if (c == '"') inString = !inString
                if (!inString) {
                    if (c == '{') depth++
                    else if (c == '}') { depth--; if (depth == 0) { i++; break } }
                }
                i++
            }
            val objText = text.substring(start, i)
            val def = parseDef(objText)
            result.add(def)
            skipWs()
            when {
                i < text.length && text[i] == ',' -> { i++; continue }
                i < text.length && text[i] == ']' -> { i++; skipWs(); require(i == text.length) { "trailing after array" }; break }
                else -> error("expected ',' or ']'")
            }
        }
        return result
    }

    private fun parseDef(objText: String): EditorKeyDef {
        // Simple regex-free parsing for our known keys: extract via helper
        fun extractString(key: String): String? {
            val regex = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
            val m = regex.find(objText) ?: return null
            return unescape(m.groupValues[1])
        }
        fun extractRaw(key: String): String? {
            val regex = Regex("\"$key\"\\s*:\\s*([^,\\}]+)")
            val m = regex.find(objText) ?: return null
            return m.groupValues[1].trim().removeSurrounding("\"")
        }
        fun extractBool(key: String): Boolean? {
            val raw = extractRaw(key) ?: return null
            return raw == "true"
        }
        fun extractObject(key: String): String? {
            val pattern = "\"$key\"\\s*:\\s*\\{"
            val regex = Regex(pattern)
            val m = regex.find(objText) ?: return null
            var start = m.range.last // at {
            var depth = 0
            var inStr = false
            var esc = false
            var end = -1
            for (idx in start until objText.length) {
                val c = objText[idx]
                if (esc) { esc = false; continue }
                if (c == '\\' && inStr) { esc = true; continue }
                if (c == '"') inStr = !inStr
                if (!inStr) {
                    if (c == '{') depth++
                    else if (c == '}') { depth--; if (depth == 0) { end = idx; break } }
                }
            }
            require(end >= 0) { "unterminated object for $key" }
            return objText.substring(start, end + 1)
        }
        val label = extractString("label") ?: error("missing label")
        val wide = extractBool("wide") ?: false
        val type = extractString("type") ?: extractRaw("type") ?: error("missing type")
        val key: EditorKey = when (type) {
            "insert" -> {
                val txt = extractString("text") ?: ""
                EditorKey.Insert(txt)
            }
            "pair" -> {
                val open = extractString("open") ?: ""
                val close = extractString("close") ?: ""
                EditorKey.Pair(open, close)
            }
            "tab" -> EditorKey.Tab
            "delete" -> EditorKey.Delete
            "deleteWord" -> EditorKey.DeleteWord
            "commentToggle" -> EditorKey.CommentToggle
            "caret" -> {
                val moveName = extractString("move") ?: extractRaw("move") ?: "LEFT"
                val move = try { EditorKey.Caret.Move.valueOf(moveName) } catch (_: Exception) { EditorKey.Caret.Move.LEFT }
                EditorKey.Caret(move)
            }
            else -> error("unknown type $type")
        }
        fun parseKeyObj(jsonObj: String): EditorKey {
            val t = Regex("\"type\"\\s*:\\s*\"([^\"]+)\"").find(jsonObj)?.groupValues?.get(1) ?: error("popup missing type")
            return when (t) {
                "insert" -> {
                    val txt = Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").find(jsonObj)?.groupValues?.get(1)?.let { unescape(it) } ?: ""
                    EditorKey.Insert(txt)
                }
                "pair" -> {
                    val open = Regex("\"open\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").find(jsonObj)?.groupValues?.get(1)?.let { unescape(it) } ?: ""
                    val close = Regex("\"close\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").find(jsonObj)?.groupValues?.get(1)?.let { unescape(it) } ?: ""
                    EditorKey.Pair(open, close)
                }
                "tab" -> EditorKey.Tab
                "delete" -> EditorKey.Delete
                "deleteWord" -> EditorKey.DeleteWord
                "commentToggle" -> EditorKey.CommentToggle
                "caret" -> {
                    val mv = Regex("\"move\"\\s*:\\s*\"([^\"]+)\"").find(jsonObj)?.groupValues?.get(1) ?: "LEFT"
                    EditorKey.Caret(try { EditorKey.Caret.Move.valueOf(mv) } catch (_: Exception) { EditorKey.Caret.Move.LEFT })
                }
                else -> error("unknown popup type $t")
            }
        }
        val popup = extractObject("popup")?.let { parseKeyObj(it) }
        val swipeUp = extractObject("swipeUp")?.let { parseKeyObj(it) }
        val swipeDown = extractObject("swipeDown")?.let { parseKeyObj(it) }
        return EditorKeyDef(label, key, wide, popup, swipeUp, swipeDown)
    }

    private fun escape(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in '\u0000'..'\u001F' -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val ch = value[i++]
            if (ch == '\\' && i < value.length) {
                when (val e = value[i++]) {
                    '"', '\\', '/' -> append(e)
                    'b' -> append('\b')
                    'f' -> append('\u000C')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    'u' -> {
                        require(i + 4 <= value.length) { "invalid unicode escape" }
                        val hex = value.substring(i, i + 4)
                        append(hex.toInt(16).toChar())
                        i += 4
                    }
                    else -> append(e)
                }
            } else {
                append(ch)
            }
        }
    }
}
