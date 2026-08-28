package com.codeci.ide.ui.projects

/** The versioned, user-editable run configuration for one project. */
data class ProjectConfig(
    val version: Int = CURRENT_VERSION,
    val name: String,
    val type: String = "c",
    val entry: String = "main.c",
    val build: String = "mkdir -p bin && cc main.c -o bin/app",
    val run: String = "./bin/app",
    val clean: String = "rm -rf bin/app"
) {
    /**
     * Avoid Android's nullable/stubbed JSONObject string methods in JVM tests;
     * this schema is small enough to serialize explicitly and safely.
     */
    fun toJsonString(): String = buildString {
        append('{')
        append("\"version\":").append(version)
        append(",\"name\":").append(jsonString(name))
        append(",\"type\":").append(jsonString(type))
        append(",\"entry\":").append(jsonString(entry))
        append(",\"build\":").append(jsonString(build))
        append(",\"run\":").append(jsonString(run))
        append(",\"clean\":").append(jsonString(clean))
        append('}')
    }

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in '\u0000'..'\u001F' -> append("\\u")
                    .append(character.code.toString(16).padStart(4, '0'))
                else -> append(character)
            }
        }
        append('"')
    }

    companion object {
        const val CURRENT_VERSION = 1

        fun defaultFor(name: String, type: String = "c"): ProjectConfig {
            val projectType = type.trim().ifEmpty { "c" }.lowercase()
            return when (projectType) {
                "web", "static-web" -> ProjectConfig(
                    name = name,
                    type = "web",
                    entry = "index.html",
                    build = "",
                    run = "",
                    clean = ""
                )
                "python" -> ProjectConfig(
                    name = name,
                    type = "python",
                    entry = "main.py",
                    build = "",
                    run = "python3 main.py",
                    clean = ""
                )
                else -> ProjectConfig(name = name)
            }
        }

        fun fromJson(text: String, fallbackName: String): ProjectConfig {
            val fields = parseJsonObject(text)
            val name = fields["name"]
                ?.takeIf { ProjectPathUtils.sanitizeProjectName(it) != null }
                ?: fallbackName
            return ProjectConfig(
                version = fields["version"]?.toIntOrNull() ?: CURRENT_VERSION,
                name = name,
                type = fields["type"].orEmpty().ifBlank { "c" },
                entry = fields["entry"] ?: "main.c",
                build = fields["build"] ?: "mkdir -p bin && cc main.c -o bin/app",
                run = fields["run"] ?: "./bin/app",
                clean = fields["clean"] ?: "rm -rf bin/app"
            )
        }

        /** Parse the small object emitted by [toJsonString], accepting field reordering. */
        private fun parseJsonObject(text: String): Map<String, String> {
            var index = 0
            val fields = linkedMapOf<String, String>()

            fun skipWhitespace() {
                while (index < text.length && text[index].isWhitespace()) index++
            }

            fun expect(expected: Char) {
                skipWhitespace()
                require(index < text.length && text[index] == expected) {
                    "Invalid project configuration JSON"
                }
                index++
            }

            fun parseString(): String {
                expect('"')
                return buildString {
                    while (index < text.length) {
                        when (val character = text[index++]) {
                            '"' -> return@buildString
                            '\\' -> {
                                require(index < text.length) { "Invalid project configuration escape" }
                                when (val escaped = text[index++]) {
                                    '"', '\\', '/' -> append(escaped)
                                    'b' -> append('\b')
                                    'f' -> append('\u000C')
                                    'n' -> append('\n')
                                    'r' -> append('\r')
                                    't' -> append('\t')
                                    'u' -> {
                                        require(index + 4 <= text.length) {
                                            "Invalid project configuration unicode escape"
                                        }
                                        val hex = text.substring(index, index + 4)
                                        require(hex.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) {
                                            "Invalid project configuration unicode escape"
                                        }
                                        append(hex.toInt(16).toChar())
                                        index += 4
                                    }
                                    else -> error("Invalid project configuration escape")
                                }
                            }
                            else -> {
                                require(character >= ' ') { "Invalid project configuration string" }
                                append(character)
                            }
                        }
                    }
                    error("Unterminated project configuration string")
                }
            }

            skipWhitespace()
            expect('{')
            skipWhitespace()
            if (index < text.length && text[index] == '}') {
                index++
                skipWhitespace()
                require(index == text.length) { "Invalid project configuration JSON" }
                return fields
            }

            while (true) {
                val key = parseString()
                expect(':')
                skipWhitespace()
                val value = if (index < text.length && text[index] == '"') {
                    parseString()
                } else {
                    val start = index
                    while (index < text.length && text[index] != ',' && text[index] != '}') index++
                    text.substring(start, index).trim().also {
                        require(it.isNotEmpty() && it != "null") {
                            "Invalid project configuration value"
                        }
                    }
                }
                fields[key] = value
                skipWhitespace()
                when {
                    index < text.length && text[index] == ',' -> {
                        index++
                        skipWhitespace()
                    }
                    index < text.length && text[index] == '}' -> {
                        index++
                        skipWhitespace()
                        require(index == text.length) { "Invalid project configuration JSON" }
                        return fields
                    }
                    else -> error("Invalid project configuration JSON")
                }
            }
        }
    }
}
