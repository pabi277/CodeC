package com.codeci.ide.ui.editor.snippets

/**
 * Phase 30.1 — a tiny strict-JSON reader for the vendored snippet packs.
 *
 * Why hand-rolled (again): the codebase law is *pure engines + injected
 * adapters* (see `CodecJsonParser`, `KeyStripStorage`, `ProjectConfig` — the
 * same "no `org.json` on host" note). Snippet parsing must be unit-testable
 * on the JVM without Robolectric, and `org.json` only exists as a stub there.
 *
 * Scope: exactly what a VS Code snippet file needs — objects, arrays,
 * strings (with the full JSON escape set incl. `\uXXXX` surrogate pairs),
 * numbers, `true`/`false`/`null`. Object key ORDER is preserved because the
 * pack order is the completion rank tie-breaker. Malformed input returns
 * `null` (never throws): a broken asset must degrade to "no snippets", not
 * crash the editor.
 */
sealed class JsonValue {
    data class Str(val value: String) : JsonValue()

    data class Arr(val items: List<JsonValue>) : JsonValue()

    /** Insertion-ordered map (LinkedHashMap) — rank stability depends on it. */
    data class Obj(val entries: LinkedHashMap<String, JsonValue>) : JsonValue() {
        operator fun get(key: String): JsonValue? = entries[key]
    }

    data class Num(val raw: String) : JsonValue()

    data class Bool(val value: Boolean) : JsonValue()

    object Null : JsonValue() {
        override fun toString(): String = "null"
    }
}

object SnippetJson {

    /** Parses [text]; null when it is not one well-formed JSON value. */
    fun parse(text: String): JsonValue? {
        val p = Parser(text)
        p.skipWhitespace()
        val value = p.readValue() ?: return null
        p.skipWhitespace()
        // Reject trailing garbage ("{} x") — a truncated asset is a bad asset.
        return if (p.atEnd()) value else null
    }

    /** Convenience: the string value of [node], or null. */
    fun asString(node: JsonValue?): String? = (node as? JsonValue.Str)?.value

    /** Convenience: every element of [node] as a string (arrays and scalars). */
    fun asStringList(node: JsonValue?): List<String> = when (node) {
        is JsonValue.Str -> listOf(node.value)
        is JsonValue.Arr -> node.items.mapNotNull { asString(it) }
        else -> emptyList()
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd(): Boolean = i >= s.length

        fun skipWhitespace() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++
        }

        fun readValue(): JsonValue? {
            skipWhitespace()
            if (atEnd()) return null
            return when (val c = s[i]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()?.let { JsonValue.Str(it) }
                't' -> literal("true", JsonValue.Bool(true))
                'f' -> literal("false", JsonValue.Bool(false))
                'n' -> literal("null", JsonValue.Null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else null
            }
        }

        private fun literal(word: String, value: JsonValue): JsonValue? {
            if (!s.regionMatches(i, word, 0, word.length)) return null
            i += word.length
            return value
        }

        private fun readObject(): JsonValue.Obj? {
            i++ // '{'
            val out = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (!atEnd() && s[i] == '}') {
                i++
                return JsonValue.Obj(out)
            }
            while (true) {
                skipWhitespace()
                val key = readString() ?: return null
                skipWhitespace()
                if (atEnd() || s[i] != ':') return null
                i++
                val value = readValue() ?: return null
                out[key] = value
                skipWhitespace()
                if (atEnd()) return null
                when (s[i]) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return JsonValue.Obj(out)
                    }
                    else -> return null
                }
            }
        }

        private fun readArray(): JsonValue.Arr? {
            i++ // '['
            val out = ArrayList<JsonValue>()
            skipWhitespace()
            if (!atEnd() && s[i] == ']') {
                i++
                return JsonValue.Arr(out)
            }
            while (true) {
                val value = readValue() ?: return null
                out += value
                skipWhitespace()
                if (atEnd()) return null
                when (s[i]) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return JsonValue.Arr(out)
                    }
                    else -> return null
                }
            }
        }

        private fun readNumber(): JsonValue.Num? {
            val start = i
            if (!atEnd() && s[i] == '-') i++
            var digits = 0
            while (!atEnd() && s[i] in '0'..'9') {
                i++
                digits++
            }
            if (digits == 0) return null
            if (!atEnd() && s[i] == '.') {
                i++
                var frac = 0
                while (!atEnd() && s[i] in '0'..'9') {
                    i++
                    frac++
                }
                if (frac == 0) return null
            }
            if (!atEnd() && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (!atEnd() && (s[i] == '+' || s[i] == '-')) i++
                var exp = 0
                while (!atEnd() && s[i] in '0'..'9') {
                    i++
                    exp++
                }
                if (exp == 0) return null
            }
            return JsonValue.Num(s.substring(start, i))
        }

        /** Reads a quoted string; null on an unterminated string or bad escape. */
        private fun readString(): String? {
            if (atEnd() || s[i] != '"') return null
            i++
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) return null
                when (val c = s[i]) {
                    '"' -> {
                        i++
                        return sb.toString()
                    }
                    '\\' -> {
                        i++
                        if (atEnd()) return null
                        when (val esc = s[i]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 >= s.length) return null
                                val hex = s.substring(i + 1, i + 5)
                                val code = hex.toIntOrNull(16) ?: return null
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> {
                                // Strict JSON: any other escape is malformed.
                                return null
                            }
                        }
                        i++
                    }
                    else -> {
                        // Raw control characters are not legal JSON string content.
                        if (c < ' ') return null
                        sb.append(c)
                        i++
                    }
                }
            }
        }
    }
}
