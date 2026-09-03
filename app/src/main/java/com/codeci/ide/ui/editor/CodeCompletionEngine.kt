package com.codeci.ide.ui.editor

import com.codeci.ide.ui.utils.LanguageType
import com.codeci.ide.ui.utils.MultiLanguageSyntaxHighlighter

/**
 * Phase 12 — lightweight in-editor autocompletion: identifier scanning of the
 * active buffer plus per-language snippet/keyword presets. Pure functions so
 * the engine is fully unit-testable (no language server, no network).
 *
 * [completions] shows snippets and matching buffer identifiers/keywords while
 * a word prefix is being typed, and — when the prefix is empty — the snippet
 * list right after a trigger word (e.g. `def `, `import `, `#include`).
 */
enum class CompletionKind { SNIPPET, KEYWORD, IDENTIFIER }

data class CompletionItem(
    val label: String,
    val insertText: String,
    val kind: CompletionKind,
    val detail: String? = null
)

object CodeCompletionEngine {

    const val MAX_ITEMS = 8

    /**
     * How far either side of the caret the buffer-identifier scan looks.
     * Bounds the per-keystroke cost to a constant instead of the file size.
     */
    const val SCAN_WINDOW = 20_000

    /** Compiled once — it used to be rebuilt on every keystroke. */
    private val IDENTIFIER = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")

    /** Offset where the word under [cursorOffset] begins. */
    fun prefixStart(text: String, cursorOffset: Int): Int {
        val cursor = cursorOffset.coerceIn(0, text.length)
        var i = cursor
        while (i > 0 && (text[i - 1].isLetterOrDigit() || text[i - 1] == '_')) i--
        return i
    }

    /** The word fragment immediately before [cursorOffset]. */
    fun currentPrefix(text: String, cursorOffset: Int): String {
        val cursor = cursorOffset.coerceIn(0, text.length)
        return text.substring(prefixStart(text, cursor), cursor)
    }

    fun completions(text: String, cursorOffset: Int, language: LanguageType): List<CompletionItem> {
        val cursor = cursorOffset.coerceIn(0, text.length)
        if (text.isEmpty()) return emptyList()
        if (language == LanguageType.TEXT || language == LanguageType.JSON) return emptyList()
        val prefix = currentPrefix(text, cursor)
        val items = mutableListOf<CompletionItem>()
        val keywords = MultiLanguageSyntaxHighlighter.keywords(language)

        if (prefix.isNotEmpty()) {
            snippets(language)
                .filter { snippetMatches(it.label, prefix) }
                .forEach { items += it }
            identifiers(text, prefix, keywords, cursor, limit = 3)
                .forEach { items += CompletionItem(it, it, CompletionKind.IDENTIFIER, "buffer") }
            keywords
                .filter { it.startsWith(prefix) }
                .sorted()
                .take(3)
                .forEach { items += CompletionItem(it, it, CompletionKind.KEYWORD, "keyword") }
        } else {
            val trigger = lastToken(text, cursor)
            if (trigger.isNotEmpty() && trigger in snippetTriggers(language)) {
                snippets(language)
                    .filter { it.label != trigger }
                    .forEach { items += it }
            }
        }
        return items.distinctBy { it.label }.take(MAX_ITEMS)
    }

    /**
     * A snippet matches when the prefix is a prefix of the label itself or of
     * any word inside it (so typing `mai` surfaces `int main(void) {` and
     * `inc` surfaces `#include <stdio.h>`).
     */
    private fun snippetMatches(label: String, prefix: String): Boolean {
        if (label.startsWith(prefix)) return true
        return label.split(Regex("[^A-Za-z0-9_]+")).any { it.startsWith(prefix) }
    }

    /**
     * Phase 22.6 — the identifier scan is the single most expensive thing the
     * editor did per keystroke, so it is bounded twice over.
     *
     * It used to compile a `Regex` and run it across the WHOLE buffer on
     * every character (then `distinct()` + `sorted()` the result). On a long
     * file that is a full-file regex sweep per keypress, on the main thread.
     *
     * Now: the pattern is compiled once (see [IDENTIFIER]), the scan is
     * limited to a window around the caret ([SCAN_WINDOW] characters either
     * side — identifiers you are likely to reuse are near where you are
     * typing), and it stops as soon as it has enough distinct matches.
     */
    private fun identifiers(
        text: String,
        prefix: String,
        keywords: Set<String>,
        cursor: Int,
        limit: Int
    ): List<String> {
        val from = (cursor - SCAN_WINDOW).coerceAtLeast(0)
        val to = (cursor + SCAN_WINDOW).coerceAtMost(text.length)
        if (from >= to) return emptyList()
        val found = LinkedHashSet<String>()
        for (match in IDENTIFIER.findAll(text, from)) {
            if (match.range.first >= to) break
            val word = match.value
            if (word.length > prefix.length && word.startsWith(prefix) && word !in keywords) {
                found += word
                if (found.size >= limit * 4) break
            }
        }
        return found.sorted().take(limit)
    }

    private fun lastToken(text: String, cursor: Int): String {
        var i = cursor
        while (i > 0 && (text[i - 1] == ' ' || text[i - 1] == '\t' || text[i - 1] == '\n')) i--
        val end = i
        while (i > 0 && (text[i - 1].isLetterOrDigit() || text[i - 1] == '_')) i--
        return text.substring(i, end)
    }

    private fun snippet(label: String, insert: String) =
        CompletionItem(label, insert, CompletionKind.SNIPPET, "snippet")

    private fun snippets(language: LanguageType): List<CompletionItem> = when (language) {
        LanguageType.PYTHON -> listOf(
            snippet("def function():", "def function():\n    "),
            snippet("class ClassName:", "class ClassName:\n    "),
            snippet("if __name__ == '__main__':", "if __name__ == '__main__':\n    "),
            snippet("for item in iterable:", "for item in iterable:\n    "),
            snippet("try / except Exception:", "try:\n    \nexcept Exception as e:\n    "),
            snippet("import module", "import "),
            snippet("from module import name", "from "),
            snippet("print(...)", "print("),
            snippet("with open(...) as f:", "with open('file.txt', 'r') as f:\n    ")
        )
        LanguageType.C, LanguageType.CPP -> listOf(
            snippet("int main(void) {", "int main(void) {\n    \n    return 0;\n}"),
            snippet("printf(...)", "printf(\"\\n\");"),
            snippet("for (int i = 0; i < n; i++) {", "for (int i = 0; i < n; i++) {\n    \n}"),
            snippet("if (condition) {", "if (condition) {\n    \n}"),
            snippet("while (condition) {", "while (condition) {\n    \n}"),
            snippet("#include <stdio.h>", "#include <stdio.h>\n"),
            snippet("typedef struct ... Name;", "typedef struct {\n    \n} Name;\n")
        ) + if (language == LanguageType.CPP) {
            listOf(snippet("class Name { ... };", "class Name {\npublic:\n    \n};"))
        } else {
            emptyList()
        }
        LanguageType.JAVASCRIPT -> listOf(
            snippet("function name() {", "function name() {\n    \n}"),
            snippet("const name = value;", "const name = value;"),
            snippet("console.log(...)", "console.log("),
            snippet("for (let i = 0; i < n; i++) {", "for (let i = 0; i < n; i++) {\n    \n}"),
            snippet("if (condition) {", "if (condition) {\n    \n}"),
            snippet("import name from 'module';", "import name from 'module';")
        )
        LanguageType.SHELL -> listOf(
            snippet("if [ cond ]; then ... fi", "if [ condition ]; then\n    \nfi"),
            snippet("for x in list; do ... done", "for x in list; do\n    \ndone"),
            snippet("while ...; do ... done", "while condition; do\n    \ndone"),
            snippet("case \$x in ... esac", "case \$x in\n    pattern) ;;\nesac"),
            snippet("function name() {", "function name() {\n    \n}"),
            snippet("echo ...", "echo "),
            snippet("#!/data/data/com.codeci.ide/files/usr/bin/sh", "#!/data/data/com.codeci.ide/files/usr/bin/sh\n"),
            snippet("read -r var", "read -r var")
        )
        // Phase 22.6 — HTML/CSS and Markdown had NO suggestions at all; a
        // `.html` or `.md` file fell through to `emptyList()` and the popup
        // never appeared. Both are first-class in CodeC (Web Preview runs
        // HTML directly), so both get a snippet set.
        LanguageType.HTML_CSS -> listOf(
            snippet("<!DOCTYPE html> skeleton", HTML_SKELETON),
            snippet("<div class=\"\">", "<div class=\"\">\n    \n</div>"),
            snippet("<a href=\"\">", "<a href=\"\"></a>"),
            snippet("<img src=\"\" alt=\"\">", "<img src=\"\" alt=\"\">"),
            snippet("<ul><li>", "<ul>\n    <li></li>\n</ul>"),
            snippet("<script src=\"\">", "<script src=\"\"></script>"),
            snippet("<link rel=\"stylesheet\">", "<link rel=\"stylesheet\" href=\"\">"),
            snippet("<style> ... </style>", "<style>\n    \n</style>"),
            snippet("selector { }", "selector {\n    \n}"),
            snippet("@media (max-width: 600px)", "@media (max-width: 600px) {\n    \n}"),
            snippet("display: flex;", "display: flex;")
        )
        LanguageType.MARKDOWN -> listOf(
            snippet("# Heading", "# "),
            snippet("## Subheading", "## "),
            snippet("**bold**", "**bold**"),
            snippet("_italic_", "_italic_"),
            snippet("[link](url)", "[text](url)"),
            snippet("![image](path)", "![alt](path)"),
            snippet("- bullet list", "- "),
            snippet("1. numbered list", "1. "),
            snippet("> blockquote", "> "),
            snippet("``` code fence ```", "```\n\n```"),
            snippet("| table |", "| Column | Column |\n| --- | --- |\n|  |  |")
        )
        else -> emptyList()
    }

    private val HTML_SKELETON =
        "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n" +
            "    <meta charset=\"utf-8\">\n" +
            "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
            "    <title>Page</title>\n</head>\n<body>\n    \n</body>\n</html>\n"

    private fun snippetTriggers(language: LanguageType): Set<String> = when (language) {
        LanguageType.PYTHON -> setOf(
            "def", "class", "if", "elif", "else", "for", "while", "try", "except",
            "finally", "with", "async", "import", "from", "return", "lambda",
            "match", "case"
        )
        LanguageType.C, LanguageType.CPP -> setOf(
            "include", "main", "for", "if", "while", "printf", "return", "struct",
            "typedef", "do", "switch", "case"
        )
        LanguageType.JAVASCRIPT -> setOf(
            "function", "const", "let", "var", "for", "if", "while", "try",
            "catch", "import", "export", "return", "switch", "case"
        )
        LanguageType.SHELL -> setOf("if", "for", "while", "case", "function", "do", "then", "echo")
        LanguageType.HTML_CSS -> setOf(
            "html", "head", "body", "div", "span", "a", "img", "ul", "li", "p",
            "script", "link", "style", "meta", "table", "form", "input", "button"
        )
        LanguageType.MARKDOWN -> emptySet()
        else -> emptySet()
    }
}
