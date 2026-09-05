package com.codeci.ide.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.codeci.ide.ui.editor.CodeFormatter
import com.codeci.ide.ui.editor.DiagnosticSeverity
import com.codeci.ide.ui.editor.EditorDiagnostic
import com.codeci.ide.ui.theme.EditorThemeColors
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.getEditorTheme

/**
 * Phase 12 — multi-language syntax highlighting.
 *
 * [LanguageType] is derived from the active file's extension; the tokenizer
 * produces ordered, non-overlapping [TokenSpan]s per language and
 * [MultiLanguageSyntaxHighlighter.highlight] maps them onto theme colors.
 * All spans are offsets into the (untransformed) buffer text; the visual
 * transformation stays identity-mapped so indices match directly.
 *
 * **Phase 29 (2026-09-05): this regex engine is OFF the live editor hot
 * path.** The editor's analyzer is sora `language-textmate` (VS Code
 * grammars, see `ui/editor/sora/TextMateSupport.kt`); what remains here is
 * (a) `LanguageType` + `fromFileName` (still THE file→language mapping),
 * (b) the keyword sets for [CodeCompletionEngine], (c) the regex tokenizer
 * as the SmartTyping string/comment probe, the templates preview and the
 * TextMate-load fallback. [LanguageType] buckets were split in 29.2 so
 * every `LanguageRegistry` run-profile extension maps to a real grammar.
 */
enum class LanguageType(val label: String, val extensions: List<String>) {
    C("C", listOf("c", "h")),
    CPP("C++", listOf("cpp", "hpp", "cc", "cxx", "hxx", "hh")),
    PYTHON("Python", listOf("py", "pyw")),
    JAVASCRIPT("JavaScript", listOf("js", "jsx", "mjs", "cjs")),
    // Phase 29.2 — TypeScript is its own bucket (its own TextMate grammar,
    // source.ts / source.tsx). Before 29 it rode the JavaScript regex.
    TYPESCRIPT("TypeScript", listOf("ts", "tsx")),
    // Phase 29.2 — HTML and CSS split (distinct grammars: text.html.basic /
    // source.css). Before 29 one regex mashed them together.
    HTML("HTML", listOf("html", "htm")),
    CSS("CSS", listOf("css", "scss")),
    JSON("JSON", listOf("json")),
    SHELL("Shell", listOf("sh", "bash", "zsh")),
    MARKDOWN("Markdown", listOf("md", "markdown")),
    // Phase 29.2 — the remaining run-profile languages used to colour as
    // TEXT; each now maps to its TextMate grammar.
    GO("Go", listOf("go")),
    RUST("Rust", listOf("rs")),
    PHP("PHP", listOf("php")),
    RUBY("Ruby", listOf("rb")),
    LUA("Lua", listOf("lua")),
    XML("XML", listOf("xml")),
    YAML("YAML", listOf("yaml", "yml")),
    TEXT("Text", listOf("txt", "log"));

    companion object {
        /** Resolve the language of a file path/name by its final extension. */
        fun fromFileName(name: String): LanguageType {
            val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
            val ext = cleaned.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return TEXT
            return entries.firstOrNull { ext in it.extensions } ?: TEXT
        }
    }
}

enum class TokenKind { KEYWORD, STRING, NUMBER, COMMENT, FUNCTION, OPERATOR, DECORATOR }

/** One non-overlapping colored range inside the buffer text. */
data class TokenSpan(val start: Int, val end: Int, val kind: TokenKind)

object MultiLanguageSyntaxHighlighter {

    fun keywords(language: LanguageType): Set<String> = when (language) {
        LanguageType.C -> cKeywords
        LanguageType.CPP -> cKeywords + cppKeywords
        LanguageType.PYTHON -> pythonKeywords
        LanguageType.JAVASCRIPT -> jsKeywords
        // Phase 29.2 — TS completions are the JS keyword set plus TS-only
        // words (kept for CodeCompletionEngine; colour itself is TextMate).
        LanguageType.TYPESCRIPT -> jsKeywords + tsKeywords
        LanguageType.JSON -> jsonKeywords
        LanguageType.HTML, LanguageType.CSS -> cssKeywords
        LanguageType.SHELL -> shellKeywords
        else -> emptySet()
    }

    /**
     * Tokenize [text] for [language]. A single ordered alternation is used so
     * the first matching alternative at each position wins and swallows its
     * whole range: comments and strings claim their content before numbers,
     * keywords, functions, and operators (the classic single-pass approach —
     * content inside a comment or string is never re-tokenized).
     */
    /**
     * Tokenize [text], keeping only spans that intersect `[from, to)`.
     *
     * **Phase 22.8 — scanning no longer starts at offset 0.** It used to, so
     * that multi-line constructs kept their context; but that left an O(file)
     * regex sweep on the inline-fallback path, which runs on the MAIN THREAD
     * on every keystroke (the debounced snapshot is by definition stale the
     * instant you type). On a 25 000-char HTML file that sweep was the
     * remaining per-keystroke cost.
     *
     * Instead we start at a *safe anchor*: [LOOKBEHIND] characters before the
     * window, snapped back to a blank line where possible. A blank line
     * cannot occur inside an HTML/CSS/C block comment or a single-line
     * string, so it is a reliable resynchronisation point for the constructs
     * these grammars actually have. Cost is now bounded by
     * `LOOKBEHIND + 2*WINDOW` instead of by the file.
     */
    /**
     * How far before the window to start scanning, so a construct that opened
     * just above the viewport (a block comment, a multi-line CSS rule) is
     * still classified correctly.
     */
    const val LOOKBEHIND = 4_000

    /**
     * The offset to begin regex scanning at when the coloured window starts at
     * [from]: [LOOKBEHIND] characters earlier, snapped forward to just after a
     * blank line if one exists in that span.
     *
     * A blank line is a safe resynchronisation point: none of the grammars
     * here allow a blank line inside a string, and while a block comment *can*
     * contain one, starting mid-comment only risks mis-colouring text that is
     * [LOOKBEHIND] characters above the viewport — and the debounced
     * full-context pass from the ViewModel corrects it. Returning 0 (scan
     * everything) whenever we are near the top keeps small files exact.
     */
    internal fun safeAnchor(text: String, from: Int): Int {
        val start = from.coerceIn(0, text.length)
        if (start <= LOOKBEHIND) return 0
        val floor = start - LOOKBEHIND
        val blank = text.lastIndexOf("\n\n", start)
        return if (blank >= floor) blank + 2 else floor
    }

    @JvmOverloads
    fun tokenize(
        text: String,
        language: LanguageType,
        from: Int = 0,
        to: Int = text.length
    ): List<TokenSpan> {
        if (text.isEmpty() || language == LanguageType.TEXT) return emptyList()
        val compiled = pattern(language) ?: return emptyList()
        val scanFrom = safeAnchor(text, from)
        // Named group lookup via MatchGroupCollection.get(String) calls
        // Matcher.start(String), an API 26 call (minSdk is 24), so resolve
        // group names to 1-based capturing indices from the construction
        // order recorded in CompiledPattern and access by index instead.
        // groupKinds mirrors the same alternation priority order.
        val indexByName = compiled.groupNames.mapIndexed { i, name -> name to (i + 1) }.toMap()
        val groupKinds = tokenGroupKinds(language)
        val spans = mutableListOf<TokenSpan>()
        for (match in compiled.regex.findAll(text, scanFrom)) {
            // Past the window: nothing later can intersect it.
            if (match.range.first >= to) break
            // Before the window: keep scanning for context, emit nothing.
            if (match.range.last + 1 <= from) continue
            val kind = groupKinds.firstNotNullOfOrNull { (name, tokenKind) ->
                val index = indexByName[name] ?: return@firstNotNullOfOrNull null
                if (match.groups[index] != null) tokenKind else null
            } ?: continue
            spans += TokenSpan(match.range.first, match.range.last + 1, kind)
        }
        return spans
    }

    /** (group name, kind) pairs in alternation priority order, per language. */
    private fun tokenGroupKinds(language: LanguageType): List<Pair<String, TokenKind>> = when (language) {
        LanguageType.C, LanguageType.CPP, LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT -> listOf(
            "comment" to TokenKind.COMMENT,
            "string" to TokenKind.STRING,
            "number" to TokenKind.NUMBER,
            "keyword" to TokenKind.KEYWORD,
            "function" to TokenKind.FUNCTION,
            "operator" to TokenKind.OPERATOR
        )
        LanguageType.PYTHON -> listOf(
            "comment" to TokenKind.COMMENT,
            "string" to TokenKind.STRING,
            "number" to TokenKind.NUMBER,
            "keyword" to TokenKind.KEYWORD,
            "decorator" to TokenKind.DECORATOR,
            "function" to TokenKind.FUNCTION,
            "operator" to TokenKind.OPERATOR
        )
        LanguageType.SHELL -> listOf(
            "comment" to TokenKind.COMMENT,
            "string" to TokenKind.STRING,
            "variable" to TokenKind.OPERATOR,
            "number" to TokenKind.NUMBER,
            "keyword" to TokenKind.KEYWORD,
            "function" to TokenKind.FUNCTION,
            "operator" to TokenKind.OPERATOR
        )
        LanguageType.HTML, LanguageType.CSS -> listOf(
            "comment" to TokenKind.COMMENT,
            "string" to TokenKind.STRING,
            "number" to TokenKind.NUMBER,
            "keyword" to TokenKind.KEYWORD
        )
        LanguageType.JSON -> listOf(
            "string" to TokenKind.STRING,
            "number" to TokenKind.NUMBER,
            "keyword" to TokenKind.KEYWORD
        )
        LanguageType.MARKDOWN -> listOf(
            "comment" to TokenKind.COMMENT,
            "string" to TokenKind.STRING,
            "keyword" to TokenKind.KEYWORD,
            "function" to TokenKind.FUNCTION,
            "operator" to TokenKind.OPERATOR
        )
        LanguageType.TEXT -> emptyList()
        // Phase 29.2 — the new buckets colour through TextMate only; the
        // regex fallback has no rules for them (tokenize returns early
        // because pattern() is null for these languages).
        else -> emptyList()
    }

    /** Build the styled [AnnotatedString] the editor transformation shows. */
    /**
     * Build the styled [AnnotatedString] the editor shows.
     *
     * **Phase 22.7 — [from]/[to] bound the number of SPAN STYLES, and that is
     * the whole point.** `BasicTextField` is not lazy: it measures and lays
     * out the entire text, and its cost is dominated by the span count, not
     * the character count. JetBrains confirmed this as a known, WONTFIX
     * limitation of `TextField` (compose-multiplatform#4023 → CMP-4023): a
     * `VisualTransformation` returning tens of thousands of spans freezes the
     * UI, and the maintainer's answer was "TextField was not meant to be used
     * with such a large amount of styled text".
     *
     * A 500-line C file produces roughly 4 500 token spans. Those were all
     * handed to the field on every layout pass — which is why every previous
     * optimization (moving tokenizing off-thread, memoizing, debouncing) left
     * the typing lag untouched: the expensive work was never the tokenizer,
     * it was the field laying out thousands of spans.
     *
     * So we colour only the window the user can actually see. The text is
     * always complete and offsets stay identity-mapped — only the decoration
     * is windowed, which is invisible to the user and to every caller.
     */
    fun highlight(
        text: String,
        colors: EditorThemeColors,
        language: LanguageType,
        from: Int = 0,
        to: Int = text.length
    ): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            if (text.isEmpty()) return@buildAnnotatedString
            addStyle(SpanStyle(color = colors.text), 0, text.length)
            val start = from.coerceIn(0, text.length)
            val end = to.coerceIn(start, text.length)
            for (span in tokenize(text, language, start, end)) {
                addStyle(SpanStyle(color = span.kind.color(colors)), span.start, span.end)
            }
        }
    }

    fun TokenKind.color(colors: EditorThemeColors): Color = when (this) {
        TokenKind.KEYWORD -> colors.keyword
        TokenKind.STRING -> colors.string
        TokenKind.NUMBER -> colors.number
        TokenKind.COMMENT -> colors.comment
        TokenKind.FUNCTION, TokenKind.DECORATOR -> colors.function
        TokenKind.OPERATOR -> colors.operator
    }

    /** A compiled alternation plus its named groups in construction order. */
    private class CompiledPattern(val regex: Regex, val groupNames: List<String>)

    private fun pattern(language: LanguageType): CompiledPattern? {
        val kw = keywords(language)
        // (group name, regex body) pairs in alternation priority order. Each
        // named group's 1-based capturing index equals its position in this
        // list + 1, because every inner group is non-capturing (?:…), a
        // lookahead, or a lookbehind — so the group order can never drift
        // from the priority order (no pattern-string scanning needed).
        val groups = mutableListOf<Pair<String, String>>()
        fun add(name: String, body: String) {
            groups += name to body
        }

        // C/C++ preprocessor directives (#include, #define, …) share the
        // keyword color; fold them into the same named group (Java regex does
        // not allow two groups with one name).
        val preprocessor = if (language == LanguageType.C || language == LanguageType.CPP) {
            "|#\\w+"
        } else {
            ""
        }
        val keywordBody = if (kw.isEmpty() && preprocessor.isEmpty()) {
            null
        } else {
            "(?:\\b(?:${kw.joinToString("|") { Regex.escape(it) }})$preprocessor)"
        }

        when (language) {
            LanguageType.C, LanguageType.CPP -> {
                add("comment", """//[^\n]*|/\*[\s\S]*?\*/""")
                add("string", """"(?:[^"\\]|\\.)*"""")
                add("number", """\b(?:0[xX][0-9a-fA-F]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?[fFlLuU]*)\b""")
                keywordBody?.let { add("keyword", it) }
                add("function", """\b[a-zA-Z_][a-zA-Z0-9_]*\s*(?=\()""")
                add("operator", """[+\-*/%=<>!&|^~]+""")
            }
            LanguageType.PYTHON -> {
                add("comment", "#[^\n]*")
                add(
                    "string",
                    "(?<![A-Za-z0-9_])(?:[rRuUbBfF]{0,2})(?:" +
                        "\"\"\"(?:\\\\.|[^\"])*\"\"\"|'''(?:\\\\.|[^'])*'''" +
                        "|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')"
                )
                add("number", """\b(?:0[xXbBoO][0-9a-fA-F_]+|\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+-]?\d+)?[jJ]?)\b""")
                keywordBody?.let { add("keyword", it) }
                add("decorator", "^[ \t]*@[A-Za-z_][A-Za-z0-9_.]*")
                add("function", """\b[a-zA-Z_][a-zA-Z0-9_]*\s*(?=\()""")
                add("operator", """[+\-*/%=<>!&|^~]+""")
            }
            LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT -> {
                add("comment", """//[^\n]*|/\*[\s\S]*?\*/""")
                add("string", """`[^`\n]*`|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'""")
                add("number", """\b(?:0[xX][0-9a-fA-F]+|\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+-]?\d+)?)\b""")
                keywordBody?.let { add("keyword", it) }
                add("function", """\b[a-zA-Z_$][a-zA-Z0-9_$]*\s*(?=\()""")
                add("operator", """[+\-*/%=<>!&|^~]+""")
            }
            LanguageType.JSON -> {
                add("string", """"(?:[^"\\]|\\.)*"""")
                add("number", """-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b""")
                keywordBody?.let { add("keyword", it) }
            }
            LanguageType.HTML, LanguageType.CSS -> {
                add("comment", """<!--[\s\S]*?-->|/\*[\s\S]*?\*/""")
                add("string", """"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'""")
                add("number", """\b\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw|vmin|vmax|s|ms|fr|deg|ch|ex)?\b|#[0-9a-fA-F]{3,8}\b""")
                // One keyword group only: CSS property names, HTML tag NAMES
                // (not the whole tag, so attribute strings still get string
                // color), and !important (Java forbids duplicate group names).
                add(
                    "keyword",
                    "(?:\\b(?:${kw.joinToString("|") { Regex.escape(it) }})|" +
                        "</?[A-Za-z][A-Za-z0-9]*\\b|!important)"
                )
            }
            LanguageType.SHELL -> {
                add("comment", "#[^\n]*")
                add("string", """"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'""")
                add("variable", """\$[A-Za-z_][A-Za-z0-9_]*|\$\{[^}]*\}|\$[0-9@#?$!*-]""")
                add("number", """\b\d+\b""")
                keywordBody?.let { add("keyword", it) }
                add("function", """\b[a-zA-Z_][a-zA-Z0-9_]*\s*(?=\()""")
                add("operator", """[|&;<>]+""")
            }
            LanguageType.MARKDOWN -> {
                add("comment", """^<!--[\s\S]*?-->$""")
                add("string", """^```[\s\S]*?^```$|`[^`\n]+`""")
                add("keyword", """^#{1,6}[^\n]*""")
                add("function", """\[[^\]\n]*\]\([^)\n]*\)""")
                add("operator", """\*\*[^*\n]+\*\*|__[^_\n]+__|\*[^*\n]+\*|(?<![A-Za-z0-9_])_[^_\n]+_(?![A-Za-z0-9_])""")
            }
            LanguageType.TEXT -> return null
            // Phase 29.2 — the new buckets (Go, Rust, PHP, Ruby, Lua, XML,
            // YAML) colour through TextMate only; no regex fallback rules.
            else -> return null
        }
        val needsMultiline = language != LanguageType.JSON && language != LanguageType.HTML && language != LanguageType.CSS
        val body = groups.joinToString("|") { "(?<${it.first}>${it.second})" }
        return CompiledPattern(
            Regex(if (needsMultiline) "(?m)$body" else body),
            groups.map { it.first }
        )
    }

    private val cKeywords = setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do",
        "double", "else", "enum", "extern", "float", "for", "goto", "if",
        "int", "long", "register", "return", "short", "signed", "sizeof",
        "static", "struct", "switch", "typedef", "union", "unsigned", "void",
        "volatile", "while"
    )

    private val cppKeywords = setOf(
        "bool", "catch", "class", "const_cast", "delete", "dynamic_cast",
        "explicit", "false", "friend", "inline", "namespace", "new",
        "nullptr", "operator", "override", "private", "protected", "public",
        "reinterpret_cast", "static_cast", "template", "this", "throw",
        "true", "try", "typename", "using", "virtual"
    )

    private val pythonKeywords = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "case", "class", "continue", "def", "del", "elif", "else",
        "except", "finally", "for", "from", "global", "if", "import", "in",
        "is", "lambda", "match", "nonlocal", "not", "or", "pass", "raise",
        "return", "try", "while", "with", "yield"
    )

    private val jsKeywords = setOf(
        "async", "await", "break", "case", "catch", "class", "const",
        "continue", "debugger", "default", "delete", "do", "else", "export",
        "extends", "false", "finally", "for", "from", "function", "get",
        "if", "import", "in", "instanceof", "let", "new", "null", "of",
        "return", "set", "static", "super", "switch", "this", "throw",
        "true", "try", "typeof", "undefined", "var", "void", "while", "yield"
    )

    /** Phase 29.2 — TypeScript-only words (completions; colour is TextMate). */
    private val tsKeywords = setOf(
        "abstract", "any", "as", "asserts", "bigint", "boolean", "declare",
        "enum", "implements", "infer", "interface", "internal", "is",
        "keyof", "module", "namespace", "never", "number", "object",
        "override", "private", "protected", "public", "readonly",
        "satisfies", "string", "symbol", "type", "unknown"
    )

    private val jsonKeywords = setOf("true", "false", "null")

    private val cssKeywords = setOf(
        "align", "align-content", "align-items", "background", "background-color",
        "background-image", "border", "border-radius", "bottom", "box-shadow",
        "color", "content", "cursor", "display", "flex", "float", "font",
        "font-size", "font-weight", "grid", "height", "justify", "justify-content",
        "left", "letter-spacing", "line-height", "margin", "max-height",
        "max-width", "min-height", "min-width", "opacity", "overflow", "padding",
        "position", "right", "text-align", "text-decoration", "text-overflow",
        "top", "transform", "transition", "visibility", "width", "z-index"
    )

    private val shellKeywords = setOf(
        "case", "do", "done", "echo", "elif", "else", "esac", "exit", "export",
        "fi", "for", "function", "if", "in", "local", "readonly", "return",
        "source", "test", "then", "trap", "true", "false", "until", "unset",
        "while"
    )
}

/**
 * Phase 9/12 — decoration layers combined with multi-language syntax
 * highlighting. All ranges are offsets into the (untransformed) buffer text;
 * the transformation is identity-mapped so indices match directly.
 */
/**
 * Phase 22.1 — a pre-built, off-thread syntax highlight for one exact buffer
 * snapshot. `EditorViewModel` debounces keystrokes, builds this on
 * `Dispatchers.Default` and publishes it; `SyntaxVisualTransformation` reuses
 * it verbatim when [matches] is true, so the O(n) tokenizer never runs on the
 * main thread during typing.
 */
data class HighlightedCode(
    val text: String,
    val theme: EditorThemeType,
    val language: LanguageType,
    val annotated: AnnotatedString,
    /** Phase 22.7 — the character window whose tokens are actually coloured. */
    val from: Int = 0,
    val to: Int = text.length
) {
    /**
     * True when this snapshot is still the truth for the given inputs.
     *
     * Phase 22.7 — the caret window must also still be covered. A snapshot
     * built for a window the user has since scrolled away from is stale even
     * though its text is unchanged.
     */
    fun matches(
        text: String,
        theme: EditorThemeType,
        language: LanguageType,
        from: Int = this.from,
        to: Int = this.to
    ): Boolean =
        this.theme == theme &&
            this.language == language &&
            this.text == text &&
            from >= this.from &&
            to <= this.to

    companion object {
        /**
         * Phase 22.8 — how many characters either side of the caret get
         * coloured.
         *
         * **This was `20_000` in Phase 22.7 and that was too large to help
         * the case the owner actually reported.** A 517-line HTML file is
         * only ~25 000 characters, so a +/-20 000 window covered the ENTIRE
         * file and the windowing never engaged — the field still received
         * every span. Measured on a representative 517-line HTML file:
         *
         * ```
         * window +/-20000 -> 1753 spans   (i.e. the whole file)
         * window +/- 3000 ->  ~400 spans
         * ```
         *
         * A phone shows roughly 40 lines (~2 000 characters), so +/-3 000 is
         * still well over a screenful in both directions — the user cannot
         * scroll to an uncoloured edge before the debounced re-highlight
         * catches up — while the span count the field must lay out drops by
         * ~4x. HTML is the worst case for span density because every tag
         * name, attribute string and numeric literal is its own token.
         */
        const val WINDOW = 3_000

        /** Tokenize [text] once, colouring only around [caret]. Call off the main thread. */
        fun of(
            text: String,
            theme: EditorThemeType,
            language: LanguageType,
            caret: Int = 0
        ): HighlightedCode {
            val from = (caret - WINDOW).coerceIn(0, text.length.coerceAtLeast(0))
            val to = (caret + WINDOW).coerceIn(from, text.length)
            return HighlightedCode(
                text = text,
                theme = theme,
                language = language,
                annotated = MultiLanguageSyntaxHighlighter.highlight(
                    text,
                    getEditorTheme(theme),
                    language,
                    from,
                    to
                ),
                from = from,
                to = to
            )
        }
    }
}

data class EditorDecorations(
    val currentLineRange: IntRange? = null,
    val findMatches: List<IntRange> = emptyList(),
    val activeFindMatch: IntRange? = null,
    val bracketRanges: List<IntRange> = emptyList(),
    val diagnostics: List<EditorDiagnostic> = emptyList()
) {
    /** True when there is nothing to layer on top of the syntax colors. */
    fun isEmpty(): Boolean =
        currentLineRange == null &&
            findMatches.isEmpty() &&
            activeFindMatch == null &&
            bracketRanges.isEmpty() &&
            diagnostics.isEmpty()
}

class SyntaxVisualTransformation(
    private val theme: EditorThemeType = EditorThemeType.DRACULA,
    private val decorations: EditorDecorations = EditorDecorations(),
    private val language: LanguageType = LanguageType.C,
    /**
     * Phase 22.1 — the debounced, off-thread highlight for the buffer the
     * editor is about to draw. When it matches the incoming text the O(n)
     * tokenizer is skipped entirely (only the cheap decoration spans are
     * layered on); when it is stale (the first frames after a keystroke) the
     * transformation falls back to highlighting inline so text is never
     * mis-colored.
     */
    private val cached: HighlightedCode? = null,
    /**
     * Phase 22.7 — where the user is, so the inline fallback colours the
     * right window. Defaults to the top of the file.
     */
    private val caret: Int = 0
) : VisualTransformation {

    /**
     * Single-entry memo of the last [filter] result.
     *
     * `filter` is called on every *layout* pass, not just on every edit — and
     * the soft keyboard's open/close animation relayouts the field on every
     * frame of that animation. Without this memo each of those frames rebuilt
     * the decorated `AnnotatedString` from scratch (an O(n) copy of the whole
     * file), which is what made opening the keyboard stutter on a big file.
     * The instance itself is `remember`ed on (theme, decorations, language,
     * cached highlight), so a stale memo can only ever mean "same inputs,
     * same text" — the entry is still keyed on the text to be safe.
     */
    private var memoKey: String? = null
    private var memoCaretKey: Int = -1
    private var memoValue: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        // Phase 22.8 — the memo must include the caret window. Keyed on the
        // text alone it would keep serving a snapshot coloured for a window
        // the user has scrolled away from, leaving visible uncoloured text.
        memoValue?.let { if (memoKey == text.text && memoCaretKey == caret) return it }

        // Phase 22.7 — the inline fallback is windowed too. It used to colour
        // the WHOLE buffer, so on the frames before the debounced snapshot
        // arrived it handed the field thousands of spans — exactly the cost
        // the windowing exists to avoid.
        val base = cached
            ?.takeIf { it.matches(text.text, theme, language) }
            ?.annotated
            ?: HighlightedCode.of(text.text, theme, language, caret).annotated
        // Phase 22.8 — when there are no decorations, `base` is handed
        // through untouched; `buildAnnotatedString { append(base) }` would
        // copy the entire text plus every span for nothing.
        val result = TransformedText(
            text = if (decorations.isEmpty()) {
                base
            } else {
                buildAnnotatedString {
                    append(base)
                    addDecorations(text.text)
                }
            },
            offsetMapping = OffsetMapping.Identity
        )
        memoKey = text.text
        memoCaretKey = caret
        memoValue = result
        return result
    }

    /**
     * Layer order (last wins visually): current-line tint → diagnostic line
     * tint → diagnostic squiggle → find matches → active match → brackets.
     */
    private fun AnnotatedString.Builder.addDecorations(text: String) {
        if (text.isEmpty()) return

        decorations.currentLineRange?.let { range ->
            addClamped(
                SpanStyle(background = CurrentLineBackground),
                range,
                text
            )
        }

        decorations.diagnostics.forEach { diagnostic ->
            val bounds = lineBoundsOf(text, diagnostic.line) ?: return@forEach
            if (bounds.isEmpty()) {
                // Empty flagged line: underline the line break cell itself.
                addClamped(
                    SpanStyle(
                        background = if (diagnostic.severity == DiagnosticSeverity.ERROR) {
                            ErrorLineBackground
                        } else {
                            WarnLineBackground
                        }
                    ),
                    bounds.first until (bounds.first + 1),
                    text
                )
                return@forEach
            }
            addClamped(
                SpanStyle(
                    background = if (diagnostic.severity == DiagnosticSeverity.ERROR) {
                        ErrorLineBackground
                    } else {
                        WarnLineBackground
                    }
                ),
                bounds,
                text
            )
            // Diagnostic marker: red underline from the reported column to the
            // line end, over a soft tinted line background. (Compose BOM
            // 2024.09.00 SpanStyle has no drawStyle/pathEffect yet - a true
            // squiggle needs ui-graphics 1.8+; see PART_9_IMPLEMENTATION.md.)
            val squiggleStart = (bounds.first + diagnostic.column - 1).coerceIn(bounds.first, bounds.last + 1)
            addClamped(
                SpanStyle(
                    color = if (diagnostic.severity == DiagnosticSeverity.ERROR) {
                        ErrorSquiggle
                    } else {
                        WarnSquiggle
                    },
                    textDecoration = TextDecoration.Underline
                ),
                squiggleStart until (bounds.last + 1).coerceAtLeast(squiggleStart + 1),
                text
            )
        }

        decorations.findMatches.forEach { match ->
            addClamped(SpanStyle(background = FindMatchBackground), match, text)
        }
        decorations.activeFindMatch?.let { match ->
            addClamped(SpanStyle(background = ActiveMatchBackground), match, text)
        }

        decorations.bracketRanges.forEach { bracket ->
            addClamped(
                SpanStyle(
                    background = BracketHighlightBackground,
                    fontWeight = FontWeight.Bold
                ),
                bracket,
                text
            )
        }
    }

    private fun AnnotatedString.Builder.addClamped(style: SpanStyle, range: IntRange, text: String) {
        if (range.isEmpty()) return
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        if (end > start) addStyle(style, start, end)
    }

    private fun lineBoundsOf(text: String, line: Int): IntRange? =
        CodeFormatter.lineBounds(text, line)

    internal companion object {
        // Deliberate theme-independent accents (visible on every editor theme).
        val CurrentLineBackground = Color(0x14FFFFFF)
        val ErrorLineBackground = Color(0x26FF5555)
        val WarnLineBackground = Color(0x26FFB347)
        val ErrorSquiggle = Color(0xFFFF5555)
        val WarnSquiggle = Color(0xFFFFB347)
        val FindMatchBackground = Color(0x66FFEB3B)
        val ActiveMatchBackground = Color(0xB3FF9800)
        val BracketHighlightBackground = Color(0x5900BCD4)
    }
}
