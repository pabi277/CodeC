package com.codeci.ide.ui.editor

import com.codeci.ide.ui.editor.snippets.SnippetLibrary
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
 *
 * **Phase 30 (2026-09-06):** the snippet SOURCE and the CAPACITY changed, the
 * accept law did not (Phase 27 `CompletionPolicy` is untouched — Enter stays
 * sacred, the master switch still removes every surface, nothing auto-commits):
 *  - **30.1** snippets come from the vendored MIT friendly-snippets packs
 *    (`assets/snippets/`, see `ui/editor/snippets/`), ~1 400 entries instead of
 *    the 1–9 hand-written Kotlin tables per language; those tables survive ONLY
 *    as the no-asset fallback plus two CodeC extras the packs cannot express.
 *  - **30.2** an Emmet-style abbreviation at the caret (`ul>li*3`, `!`, `m10`)
 *    becomes rank 0 — see [Emmet], clean room.
 *  - **30.3** the engine returns a longer RANKED list ([MAX_ITEMS] 8 → 50):
 *    the strip still shows thumb-reachable chips (`SuggestionStripModel`), the
 *    ghost is still rank 0, and "⌄ more" browses the rest.
 */
enum class CompletionKind { SNIPPET, KEYWORD, IDENTIFIER }

data class CompletionItem(
    val label: String,
    val insertText: String,
    val kind: CompletionKind,
    val detail: String? = null,
    /**
     * Phase 30 — how many characters BEFORE THE CARET this item replaces when
     * accepted. Null = the identifier prefix (the 27.x rule, still right for
     * snippets/keywords/identifiers). An Emmet expansion carries its whole
     * abbreviation: at `ul>li*3|` the identifier prefix is only `3`.
     */
    val replaceLength: Int? = null,
    /**
     * Phase 30 — where to park the caret INSIDE [insertText] after accepting
     * (a snippet's first tabstop, an Emmet expansion's first empty element).
     * Null = after the insert, the 27.x behaviour.
     */
    val caretOffset: Int? = null
)

object CodeCompletionEngine {

    /**
     * Phase 30.3 — the engine's ranked list is no longer 8 deep: 8 was why the
     * phone list "feels empty" (the strip's thumb budget is a RENDER limit,
     * `SuggestionStripModel.MAX_CHIPS`, not an engine limit). 50 stays as the
     * safety cap the plan asks for — it bounds the per-keystroke work now and
     * is the ceiling a future LSP (Phase 31) will also hand us.
     */
    const val MAX_ITEMS = 50

    /** Snippet share of [MAX_ITEMS] (identifiers/keywords get the rest). */
    const val MAX_SNIPPET_ITEMS = 40

    /** Buffer identifiers offered (was 3 pre-30.3 — the cap hid the rest). */
    const val MAX_IDENTIFIER_ITEMS = 6

    /** Keywords offered (was 3 pre-30.3). */
    const val MAX_KEYWORD_ITEMS = 6

    /**
     * How far either side of the caret the buffer-identifier scan looks.
     * Bounds the per-keystroke cost to a constant instead of the file size.
     */
    const val SCAN_WINDOW = 20_000

    /** Compiled once — it used to be rebuilt on every keystroke. */
    private val IDENTIFIER = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")

    /** Compiled once; splits a snippet label into its searchable words. */
    private val WORD_SEPARATOR = Regex("[^A-Za-z0-9_]+")

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

    /**
     * The ranked candidate list for [cursorOffset].
     *
     * @param fileName the open file (path or leaf). Phase 30: it feeds Emmet's
     *        JSX-ish gate (`.jsx`/`.tsx` only) and the snippet packs'
     *        `${TM_FILENAME_BASE}` resolution; null keeps both working with
     *        the language alone.
     */
    fun completions(
        text: String,
        cursorOffset: Int,
        language: LanguageType,
        fileName: String? = null
    ): List<CompletionItem> {
        val cursor = cursorOffset.coerceIn(0, text.length)
        if (text.isEmpty()) return emptyList()
        if (language == LanguageType.TEXT || language == LanguageType.JSON) return emptyList()
        val prefix = currentPrefix(text, cursor)
        val items = mutableListOf<CompletionItem>()

        // Phase 30.2 — Emmet first: when the token under the caret is an
        // abbreviation it is the most specific thing on offer, so it is rank 0
        // (the strip's first chip, the panel's first row). The ghost usually
        // cannot paint it — an expansion does not start with what was typed —
        // which is why StripContext shows a lone Emmet candidate as a chip.
        Emmet.completionItemFor(text, cursor, language, fileName)?.let { items += it }

        val keywords = MultiLanguageSyntaxHighlighter.keywords(language)

        if (prefix.isNotEmpty()) {
            val matches = rankSnippets(snippetItems(language, fileName), prefix)
            matches.take(MAX_SNIPPET_ITEMS).forEach { items += it }
            identifiers(text, prefix, keywords, cursor, limit = MAX_IDENTIFIER_ITEMS)
                .forEach { items += CompletionItem(it, it, CompletionKind.IDENTIFIER, "buffer") }
            keywords
                .filter { it.startsWith(prefix) }
                .sorted()
                .take(MAX_KEYWORD_ITEMS)
                .forEach { items += CompletionItem(it, it, CompletionKind.KEYWORD, "keyword") }
        } else {
            val trigger = lastToken(text, cursor)
            if (trigger.isNotEmpty() && trigger in snippetTriggers(language)) {
                val pack = snippetItems(language, fileName)
                // Phase 30.1 — a 1 400-entry pack must not dump 50 unrelated
                // snippets after a trigger word: offer the ones the trigger
                // actually names, and only fall back to the whole pack when
                // nothing matches (the pre-30 behaviour, capped).
                // Phase 30.1 — the "don't offer the word back" test moved from
                // the LABEL to the INSERT TEXT, because pack labels ARE trigger
                // words. Measured on the real shell pack: `if ` matched exactly
                // ONE item — the pack's own `if` block — and the label test
                // threw it away, so `relevant` came up empty and the fallback
                // dumped all 16 shell snippets (`echo`, `read`, `elseif`, …)
                // with no if-block among them. Same shape for Python `def `
                // (`deft`/`defs`/`defst`, no `def`). An item whose body really
                // is just the trigger word is still excluded (`import ` must
                // not offer an item that inserts `import `).
                val relevant = pack.filter { triggerMatches(it, trigger) && !it.retypes(trigger) }
                items += (relevant.ifEmpty { pack.filter { !it.retypes(trigger) } })
                    .take(MAX_SNIPPET_ITEMS)
            }
        }
        return items.distinctBy { it.label }.take(MAX_ITEMS)
    }

    /**
     * A snippet matches when the prefix is a prefix of the label itself or of
     * any word inside it (so typing `mai` surfaces `int main(void) {` and
     * `inc` surfaces `#include <stdio.h>`).
     */
    /**
     * Phase 22.6 — matching is CASE-INSENSITIVE. It used to be case-sensitive,
     * so typing `doc` never surfaced `<!DOCTYPE html>` and `head` never
     * surfaced `# Heading` — you had to guess the snippet's capitalization,
     * which defeats the point of a prefix search. Lowercase typing is the
     * common case on a phone keyboard.
     */
    private fun snippetMatches(label: String, prefix: String): Boolean {
        if (label.startsWith(prefix, ignoreCase = true)) return true
        return label.split(WORD_SEPARATOR).any { it.startsWith(prefix, ignoreCase = true) }
    }

    /**
     * Phase 27.1 — public view of the matcher used by [completions], so the
     * instant (non-debounced) shrink path narrows cached items by the grown
     * prefix with the SAME predicate (ghost/strip never re-rank by a
     * different rule).
     */
    fun labelMatches(label: String, prefix: String): Boolean = snippetMatches(label, prefix)

    /**
     * Phase 30.1 — rank a whole pack's matches. With 100+ candidates for a
     * short prefix the ORDER is the feature: a label the prefix directly
     * starts beats a word inside a label, and shorter labels beat longer ones
     * (they are the everyday snippet; the long ones are the doc/debug packs).
     * Stable within a tier, so a pack's own order still breaks exact ties.
     */
    private fun rankSnippets(pack: List<CompletionItem>, prefix: String): List<CompletionItem> {
        if (pack.isEmpty()) return pack
        val direct = ArrayList<CompletionItem>()
        val fuzzy = ArrayList<CompletionItem>()
        for (item in pack) {
            if (item.label.startsWith(prefix, ignoreCase = true)) direct += item
            else if (snippetMatches(item.label, prefix)) fuzzy += item
        }
        val byShape = compareBy<CompletionItem> { it.label.length }.thenBy { it.label }
        direct.sortWith(byShape)
        fuzzy.sortWith(byShape)
        return direct + fuzzy
    }

    /**
     * Phase 30.1 — the trigger-word relevance test: the trigger names the
     * snippet's label (either direction, so `#include ` finds `#inc`) or its
     * first body line (`main ` finds `int main(…)`).
     */
    private fun triggerMatches(item: CompletionItem, trigger: String): Boolean {
        if (item.label.startsWith(trigger, ignoreCase = true)) return true
        val words = item.label.split(WORD_SEPARATOR).filter { it.isNotEmpty() }
        if (words.any { trigger.startsWith(it, ignoreCase = true) }) return true
        val firstLine = item.insertText.lineSequence().firstOrNull { it.isNotBlank() }
            ?.trim().orEmpty()
        return firstLine.startsWith(trigger, ignoreCase = true)
    }

    /**
     * Phase 30.1 — the snippet source: the vendored MIT packs when they are
     * installed (device: always; host tests: injected) with CodeC's own tables
     * appended as a TAIL; when no pack loads at all the tables are the whole
     * list, so a broken asset degrades to the 22.x behaviour instead of
     * leaving the phone with nothing.
     *
     * The tail is load-bearing. Pack labels are short *prefixes* (`bg`, `for`,
     * `deft`), so a word from the OLD descriptive labels no longer matches
     * anything. Measured with packs alone (JVM harness on the real assets):
     * Markdown `head` returned ZERO items — `# Heading` was a 22.6 device-
     * accepted chip and no pack prefix is reachable by typing `head`; Python
     * `pr` returned only `property`, so `print(...)` was gone. The tail brings
     * both back, and the pack still ranks FIRST (`rankSnippets` sorts a tier by
     * label length, and prefix labels are shorter than body-first-lines), so
     * the tail only ever adds what the pack cannot express. It also carries the
     * two CodeC-only snippets: the phone-optimised HTML skeleton (what `doc`
     * pinned in 22.6) and the Termux-resolved shebang (29.7 — that interpreter
     * path lives in CodeC's private files dir, which no upstream pack knows).
     */
    private fun snippetItems(language: LanguageType, fileName: String?): List<CompletionItem> {
        val pack = runCatching { SnippetLibrary.snippetsFor(language, fileName) }
            .getOrDefault(emptyList())
        if (pack.isEmpty()) return builtinSnippets(language)
        // `seen.add` dedupes by label: an entry both sources claim keeps its
        // pack copy (that is what `doc` → DOCTYPE skeleton relied on before).
        val seen = HashSet<String>(pack.size + 16)
        pack.forEach { seen.add(it.label) }
        return pack + builtinSnippets(language).filter { seen.add(it.label) }
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

    /** True when accepting this item would only re-type [word] (no expansion). */
    private fun CompletionItem.retypes(word: String): Boolean = insertText.trim() == word

    /**
     * Phase 30.1 — CodeC's own 22.x/29.x snippet tables. Two roles since the
     * packs landed: the WHOLE list while no pack is installed (host unit tests
     * without assets, or every pack asset failed to read), and a deduped TAIL
     * after the pack on a device — see [snippetItems] for why that tail is
     * load-bearing.
     */
    private fun builtinSnippets(language: LanguageType): List<CompletionItem> = when (language) {
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
        // Phase 29.2 — TypeScript shares the JS snippets (plus its own set
        // below); colour is TextMate but completions stay CodeC's engine.
        LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT -> listOf(
            snippet("function name() {", "function name() {\n    \n}"),
            snippet("const name = value;", "const name = value;"),
            snippet("console.log(...)", "console.log("),
            snippet("for (let i = 0; i < n; i++) {", "for (let i = 0; i < n; i++) {\n    \n}"),
            snippet("if (condition) {", "if (condition) {\n    \n}"),
            snippet("import name from 'module';", "import name from 'module';")
        ) + if (language == LanguageType.TYPESCRIPT) {
            listOf(
                snippet("interface Name { ... }", "interface Name {\n    \n}"),
                snippet("type Alias = ...;", "type Alias = ;"),
                snippet("enum Name { ... }", "enum Name {\n    \n}")
            )
        } else {
            emptyList()
        }
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
        // Phase 29.2 — the bucket split: HTML keeps the HTML snippets, CSS
        // keeps the CSS ones (each colours with its own grammar now).
        LanguageType.HTML -> listOf(
            snippet("<!DOCTYPE html> skeleton", HTML_SKELETON),
            snippet("<div class=\"\">", "<div class=\"\">\n    \n</div>"),
            snippet("<a href=\"\">", "<a href=\"\"></a>"),
            snippet("<img src=\"\" alt=\"\">", "<img src=\"\" alt=\"\">"),
            snippet("<ul><li>", "<ul>\n    <li></li>\n</ul>"),
            snippet("<script src=\"\">", "<script src=\"\"></script>"),
            snippet("<link rel=\"stylesheet\">", "<link rel=\"stylesheet\" href=\"\">"),
            snippet("<style> ... </style>", "<style>\n    \n</style>")
        )
        LanguageType.CSS -> listOf(
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
        LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT -> setOf(
            "function", "const", "let", "var", "for", "if", "while", "try",
            "catch", "import", "export", "return", "switch", "case"
        )
        LanguageType.SHELL -> setOf("if", "for", "while", "case", "function", "do", "then", "echo")
        // Phase 29.2 — the split: HTML tag triggers, CSS property triggers.
        LanguageType.HTML -> setOf(
            "html", "head", "body", "div", "span", "a", "img", "ul", "li", "p",
            "script", "link", "style", "meta", "table", "form", "input", "button"
        )
        LanguageType.CSS -> setOf(
            "display", "position", "margin", "padding", "border", "background",
            "color", "font", "flex", "grid", "width", "height", "media"
        )
        LanguageType.MARKDOWN -> emptySet()
        else -> emptySet()
    }
}
