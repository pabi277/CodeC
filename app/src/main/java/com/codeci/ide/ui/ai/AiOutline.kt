package com.codeci.ide.ui.ai

/**
 * Phase 94 — **the readable structure of one file**: the definitions a model
 * needs before it reads 900 lines, with their line numbers.
 *
 * The owner asked for more tools *"with readable structure"*, and this is the
 * one a coding agent misses most on a phone: `read_file` returns a window, so
 * the model either reads the whole head and burns its budget, or guesses a
 * range. [outline] answers the cheap question first — *what is in this file and
 * where* — in a fixed, tab-free format the model can act on:
 *
 * ```text
 * STRUCTURE src/main.c — 214 lines, 7 definitions
 * 1: #include <stdio.h>
 * 12: int main(void) {
 * 40: static int parse_args(const char *line) {
 * ```
 *
 * ## The rules
 *
 *  - **Pure and dumb on purpose.** No parser, no compiler, no Android: an
 *    indentation-aware line scan per language family (C/C++, Python, shell,
 *    JVM/JS/TS, Markdown, HTML). It never claims to be a symbol table — a row
 *    is a line that *looks like* a definition, which is all the model needs to
 *    choose where to read.
 *  - **Indentation is kept** (two spaces per source level up to [MAX_INDENT]),
 *    so a method inside a class reads as a child of it.
 *  - **Bounded**: at most [MAX_ROWS] rows, and comments are skipped (a
 *    commented-out function must not look real).
 *  - **Closed blocks only**: a row is emitted only when its line opens a block
 *    (ends with `{`, is a `def`/`class`, a heading, a shell function …), never
 *    for a bare call.
 *
 * Host-tested by `AiOutlineTest`.
 */
object AiOutline {

    /** Hard cap on rows, whatever the file's size. */
    const val MAX_ROWS = 120

    /** Indentation is clamped here (2 spaces per level in the output). */
    const val MAX_LEVELS = 6

    data class Row(val line: Int, val text: String)

    /** The definitions of [text], in line order, capped at [maxRows]. */
    fun outline(path: String, text: String, maxRows: Int = MAX_ROWS): List<Row> {
        val language = Language.of(path)
        val rows = mutableListOf<Row>()
        var inBlockComment = false
        var inMarkdownFence = false
        val lines = text.split('\n')
        for ((index, raw) in lines.withIndex()) {
            val lineNo = index + 1
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            // Block comments: /* … */ (C-family) and """ … """ (Kotlin).
            if (inBlockComment) {
                if (trimmed.contains("*/") || trimmed.contains("\"\"\"")) inBlockComment = false
                continue
            }
            when {
                trimmed.startsWith("/*") -> {
                    if (!trimmed.contains("*/")) inBlockComment = true
                    continue
                }
                trimmed.startsWith("\"\"\"") -> {
                    if (trimmed.count { it == '"' } < 6 && !trimmed.drop(3).contains("\"\"\"")) inBlockComment = true
                    continue
                }
            }
            // Comment lines are never definitions.
            if (trimmed.startsWith("//")) continue
            if (trimmed.startsWith("#") &&
                language != Language.PYTHON && language != Language.SHELL &&
                language != Language.MARKDOWN &&
                !trimmed.startsWith("#include") && !trimmed.startsWith("#define")
            ) continue
            if (language == Language.MARKDOWN) {
                if (trimmed.startsWith("```")) {
                    inMarkdownFence = !inMarkdownFence
                    continue
                }
                if (inMarkdownFence) continue
                if (HEADING.containsMatchIn(trimmed)) {
                    rows += Row(lineNo, indentLevel(raw, 0) + trimmed)
                    if (rows.size >= maxRows) break
                }
                continue
            }
            val row = rowFor(language, raw, trimmed) ?: continue
            rows += Row(lineNo, row)
            if (rows.size >= maxRows) break
        }
        return rows
    }

    /** One formatted line: `2 spaces × level` + the source text, comment-stripped. */
    private fun rowFor(language: Language, raw: String, trimmed: String): String? {
        val definition = when (language) {
            Language.PYTHON -> PY_DEF.containsMatchIn(trimmed)
            Language.SHELL -> SHELL_FN.containsMatchIn(trimmed)
            Language.HTML -> HTML_HEADING.containsMatchIn(trimmed)
            Language.C -> C_DEF.containsMatchIn(trimmed) && !CONTROL_FLOW.containsMatchIn(trimmed)
            Language.JVM ->
                (JVM_DEF.containsMatchIn(trimmed) || C_DEF.containsMatchIn(trimmed)) &&
                    !CONTROL_FLOW.containsMatchIn(trimmed)
            Language.MARKDOWN -> false
        }
        if (!definition) return null
        return indentLevel(raw, levelOf(raw)) + trimmed.removeSuffix("{").trimEnd()
    }

    /** Two spaces per source level, clamped. */
    private fun indentLevel(raw: String, level: Int): String = "  ".repeat(level.coerceIn(0, MAX_LEVELS))

    private fun levelOf(raw: String): Int {
        var spaces = 0
        for (c in raw) {
            when (c) {
                ' ' -> spaces += 1
                '\t' -> spaces += 4
                else -> return spaces / 4
            }
        }
        return 0
    }

    private val HEADING = Regex("^#{1,6}\\s+\\S")

    // A heading that carries <h1>-style text, or a bare tag.
    private val HTML_HEADING = Regex("""(?i)<h[1-6][ >]""")

    private val PY_DEF = Regex("""^(async\s+def|def|class)\s+[A-Za-z_]""")
    private val SHELL_FN = Regex("""^[A-Za-z_][A-Za-z0-9_]*\s*\(\)\s*\{""")
    private val JVM_DEF = Regex(
        """^(?:@\w+\s+)*(?:(?:public|private|protected|internal|open|final|abstract|sealed|data|inline|""" +
            """suspend|override|operator|external|lateinit|const|static|export|default|async|declare|""" +
            """companion|tailrec|infix|value|fun|class|interface|object|enum|record|struct|trait|type|""" +
            """namespace|module|typedef)\s+)*(?:fun|class|interface|object|enum|record|struct|trait|type|""" +
            """namespace|module|typedef|const|val|var)\s+[A-Za-z_@"'\[<]"""
    )
    private val C_DEF = Regex(
        """^(?:[A-Za-z_][\w:<>,\*&\s]*?\s+)?[A-Za-z_]\w*\s*\([^;{}]*\)\s*(?:\{.*)?$""" +
            """|^(?:typedef\s+)?(?:struct|union|enum|class)\s+[A-Za-z_]\w*"""
    )
    private val CONTROL_FLOW = Regex("""^(if|for|while|switch|else|do|catch|try|return|case|when|unless|elif)\b""")

    /** Which scan a file gets. Unknown extensions use the JVM/C-ish general set. */
    private enum class Language {
        C, PYTHON, SHELL, JVM, MARKDOWN, HTML;

        companion object {
            fun of(path: String): Language {
                val name = path.substringAfterLast('/').lowercase()
                val ext = name.substringAfterLast('.', "")
                return when {
                    name.endsWith(".md") || name.endsWith(".markdown") -> MARKDOWN
                    name.startsWith("makefile") || name.endsWith(".mk") -> C
                    ext in setOf("sh", "bash") || name.endsWith(".zsh") -> SHELL
                    ext in setOf("py", "pyw") -> PYTHON
                    ext in setOf("html", "htm") -> HTML
                    ext in setOf("c", "h", "cc", "cpp", "cxx", "hpp", "m", "mm") -> C
                    else -> JVM
                }
            }
        }
    }
}
