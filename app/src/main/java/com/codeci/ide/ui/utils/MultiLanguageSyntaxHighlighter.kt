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
 */
enum class LanguageType(val label: String, val extensions: List<String>) {
    C("C", listOf("c", "h")),
    CPP("C++", listOf("cpp", "hpp", "cc", "cxx", "hxx", "hh")),
    PYTHON("Python", listOf("py", "pyw")),
    JAVASCRIPT("JavaScript", listOf("js", "jsx", "ts", "tsx", "mjs", "cjs")),
    HTML_CSS("HTML/CSS", listOf("html", "htm", "css", "scss", "xml")),
    JSON("JSON", listOf("json")),
    SHELL("Shell", listOf("sh", "bash", "zsh")),
    MARKDOWN("Markdown", listOf("md", "markdown")),
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
        LanguageType.JSON -> jsonKeywords
        LanguageType.HTML_CSS -> cssKeywords
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
    fun tokenize(text: String, language: LanguageType): List<TokenSpan> {
        if (text.isEmpty() || language == LanguageType.TEXT) return emptyList()
        val compiled = pattern(language) ?: return emptyList()
        // Named group lookup via MatchGroupCollection.get(String) calls
        // Matcher.start(String), an API 26 call (minSdk is 24), so resolve
        // group names to 1-based capturing indices from the construction
        // order recorded in CompiledPattern and access by index instead.
        // groupKinds mirrors the same alternation priority order.
        val indexByName = compiled.groupNames.mapIndexed { i, name -> name to (i + 1) }.toMap()
        val groupKinds = tokenGroupKinds(language)
        val spans = mutableListOf<TokenSpan>()
        for (match in compiled.regex.findAll(text)) {
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
        LanguageType.C, LanguageType.CPP, LanguageType.JAVASCRIPT -> listOf(
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
        LanguageType.HTML_CSS -> listOf(
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
    }

    /** Build the styled [AnnotatedString] the editor transformation shows. */
    fun highlight(text: String, colors: EditorThemeColors, language: LanguageType): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            if (text.isEmpty()) return@buildAnnotatedString
            addStyle(SpanStyle(color = colors.text), 0, text.length)
            for (span in tokenize(text, language)) {
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
            LanguageType.JAVASCRIPT -> {
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
            LanguageType.HTML_CSS -> {
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
        }
        val needsMultiline = language != LanguageType.JSON && language != LanguageType.HTML_CSS
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
    val annotated: AnnotatedString
) {
    /** True when this snapshot is still the truth for the given inputs. */
    fun matches(text: String, theme: EditorThemeType, language: LanguageType): Boolean =
        this.theme == theme && this.language == language && this.text == text

    companion object {
        /** Tokenize [text] once (call off the main thread). */
        fun of(text: String, theme: EditorThemeType, language: LanguageType): HighlightedCode =
            HighlightedCode(
                text = text,
                theme = theme,
                language = language,
                annotated = MultiLanguageSyntaxHighlighter.highlight(
                    text,
                    getEditorTheme(theme),
                    language
                )
            )
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
    private val cached: HighlightedCode? = null
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
    private var memoValue: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        memoValue?.let { if (memoKey == text.text) return it }

        val base = cached
            ?.takeIf { it.matches(text.text, theme, language) }
            ?.annotated
            ?: MultiLanguageSyntaxHighlighter.highlight(text.text, getEditorTheme(theme), language)
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
