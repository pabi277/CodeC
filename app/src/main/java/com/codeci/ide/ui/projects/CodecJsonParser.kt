package com.codeci.ide.ui.projects

import java.io.File

/**
 * Phase 24.9 — per-project `.codec.json` run-config override.
 *
 * A project root may contain a `.codec.json` file that overrides the
 * [LanguageRegistry] defaults (and the existing `.codec/project.json`
 * build/run pair) for that project:
 *
 * ```json
 * {
 *   "build": "gcc main.c utils.c -o app -lm",
 *   "run": "./app",
 *   "formatter": "clang-format -i main.c utils.c"
 * }
 * ```
 *
 * Missing fields are simply absent: the caller falls back to the project
 * config, then the registry. The parser is deliberately the same hand-rolled
 * style as [ProjectConfig.toJsonString] so it stays Android-free and
 * host-unit-testable; malformed JSON returns null instead of throwing so a
 * bad file can never break RUN ▶.
 */
data class CodecOverride(
    val build: String?,
    val run: String?,
    val formatter: String?,
) {
    companion object {
        val EMPTY = CodecOverride(null, null, null)
    }
}

object CodecJsonParser {

    /**
     * Parses a `.codec.json` file. Returns null when the file is absent,
     * unreadable, malformed, or contains no recognized field.
     */
    fun parse(file: File): CodecOverride? {
        if (!file.isFile || !file.canRead()) return null
        return parse(file.readText())
    }

    /** Parses raw JSON text; null on malformed input or no recognized field. */
    fun parse(text: String): CodecOverride? {
        if (text.isBlank()) return null
        val fields = parseObject(text) ?: return null
        val build = fields["build"]?.takeIf { it.isNotBlank() }
        val run = fields["run"]?.takeIf { it.isNotBlank() }
        val formatter = fields["formatter"]?.takeIf { it.isNotBlank() }
        return if (build == null && run == null && formatter == null) null
        else CodecOverride(build, run, formatter)
    }

    /** Serializes an override to the canonical `.codec.json` bytes. */
    fun toJson(override: CodecOverride): String = buildString {
        append('{')
        var first = true
        fun field(name: String, value: String?) {
            if (value == null) return
            if (!first) append(',')
            first = false
            append('"').append(name).append("\":").append(jsonString(value))
        }
        field("build", override.build)
        field("run", override.run)
        field("formatter", override.formatter)
        append('}')
    }

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in '\u0000'..'\u001F' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> append(c)
            }
        }
        append('"')
    }

    /** Parses a flat JSON object of string values; null on malformed input. */
    private fun parseObject(text: String): Map<String, String>? =
        try {
            parseObjectOrThrow(text)
        } catch (_: Exception) {
            null
        }

    private fun parseObjectOrThrow(text: String): Map<String, String> {
        var index = 0
        val fields = linkedMapOf<String, String>()

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun expect(expected: Char) {
            skipWhitespace()
            if (index >= text.length || text[index] != expected) error("Invalid .codec.json")
            index++
        }

        fun parseString(): String {
            expect('"')
            return buildString {
                while (index < text.length) {
                    when (val c = text[index++]) {
                        '"' -> return@buildString
                        '\\' -> {
                            if (index >= text.length) error("Invalid .codec.json escape")
                            when (val e = text[index++]) {
                                '"', '\\', '/' -> append(e)
                                'b' -> append('\b')
                                'f' -> append('\u000C')
                                'n' -> append('\n')
                                'r' -> append('\r')
                                't' -> append('\t')
                                'u' -> {
                                    if (index + 4 > text.length) error("Invalid .codec.json unicode")
                                    val hex = text.substring(index, index + 4)
                                    if (hex.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) {
                                        error("Invalid .codec.json unicode")
                                    }
                                    append(hex.toInt(16).toChar())
                                    index += 4
                                }
                                else -> error("Invalid .codec.json escape")
                            }
                        }
                        else -> append(c)
                    }
                }
                error("Unterminated .codec.json string")
            }
        }

        skipWhitespace()
        expect('{')
        skipWhitespace()
        if (index < text.length && text[index] == '}') {
            index++
            skipWhitespace()
            if (index != text.length) error("Invalid .codec.json")
            return fields
        }
        while (true) {
            if (index >= text.length || text[index] != '"') error("Invalid .codec.json key")
            val key = parseString()
            expect(':')
            skipWhitespace()
            if (index < text.length && text[index] == '"') {
                fields[key] = parseString()
            } else {
                // Non-string scalar (the schema only uses strings; a number/
                // boolean is read as its raw text so the field is preserved).
                val start = index
                while (index < text.length && text[index] != ',' && text[index] != '}') index++
                val raw = text.substring(start, index).trim()
                if (raw.isEmpty() || raw == "null") error("Invalid .codec.json value")
                fields[key] = raw
            }
            skipWhitespace()
            when {
                index < text.length && text[index] == ',' -> {
                    index++
                    skipWhitespace()
                }
                index < text.length && text[index] == '}' -> {
                    index++
                    skipWhitespace()
                    if (index != text.length) error("Invalid .codec.json")
                    return fields
                }
                else -> error("Invalid .codec.json")
            }
        }
        @Suppress("UNREACHABLE_CODE")
        return fields
    }
}
