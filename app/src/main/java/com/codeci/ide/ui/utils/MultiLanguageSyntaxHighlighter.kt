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
        val regex = pattern(language) ?: return emptyList()
        val spans = mutableListOf<TokenSpan>()
        for (match in regex.findAll(text)) {
            val kind = when {
                match.groups["comment"] != null -> TokenKind.COMMENT
                match.groups["string"] != null -> TokenKind.STRING
                match.groups["number"] != null -> TokenKind.NUMBER
                match.groups["keyword"] != null -> TokenKind.KEYWORD
                match.groups["decorator"] != null -> TokenKind.DECORATOR
                match.groups["variable"] != null -> TokenKind.OPERATOR
                match.groups["function"] != null -> TokenKind.FUNCTION
                match.groups["operator"] != null -> TokenKind.OPERATOR
                else -> continue
            }
            spans += TokenSpan(match.range.first, match.range.last + 1, kind)
        }
        return spans
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

    private fun pattern(language: LanguageType): Regex? {
        val kw = keywords(language)
        // C/C++ preprocessor directives (#include, #define, …) share the
        // keyword color; fold them into the same named group (Java regex does
        // not allow two groups with one name).
        val preprocessor = if (language == LanguageType.C || language == LanguageType.CPP) {
            "|#\\w+"
        } else {
            ""
        }
        val keywordGroup = if (kw.isEmpty() && preprocessor.isEmpty()) {
            ""
        } else {
            "(?<keyword>(?:\\b(?:${kw.joinToString("|") { Regex.escape(it) }})${preprocessor}))"
        }
        return when (language) {
            LanguageType.C, LanguageType.CPP -> Regex(
                """(?m)(?<comment>//[^\n]*|/\*[\s\S]*?\*/)""" +
                    """|(?<string>"(?:[^"\\]|\\.)*")""" +
                    """|(?<number>\b(?:0[xX][0-9a-fA-F]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?[fFlLuU]*)\b)""" +
                    (if (keywordGroup.isEmpty()) "" else "|$keywordGroup") +
                    """|(?<function>\b[a-zA-Z_][a-zA-Z0-9_]*\s*(?=\())""" +
                    """|(?<operator>[+\-*/%=<>!&|^~]+)"""
            )
            LanguageType.PYTHON -> Regex(
                "(?m)(?<comment>#[^\\n]*)" +
                    "|(?<string>(?<![A-Za-z0-9_])(?:[rRuUbBfF]{0,2})(?:" +
                    "\"\"\"(?:\\\\.|[^\"])*\"\"\"|'''(?:\\\\.|[^'])*'''" +
                    "|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'))" +
                    "|(?<number>\\b(?:0[xXbBoO][0-9a-fA-F_]+|\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d+)?[jJ]?)\\b)" +
                    (if (keywordGroup.isEmpty()) "" else "|$keywordGroup") +
                    "|(?<decorator>^[ \\t]*@[A-Za-z_][A-Za-z0-9_.]*)" +
                    "|(?<function>\\b[a-zA-Z_][a-zA-Z0-9_]*\\s*(?=\\())" +
                    "|(?<operator>[+\\-*/%=<>!&|^~]+)"
            )
            LanguageType.JAVASCRIPT -> Regex(
                """(?m)(?<comment>//[^\n]*|/\*[\s\S]*?\*/)""" +
                    """|(?<string>`[^`\n]*`|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')""" +
                    """|(?<number>\b(?:0[xX][0-9a-fA-F]+|\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+-]?\d+)?)\b)""" +
                    (if (keywordGroup.isEmpty()) "" else "|$keywordGroup") +
                    """|(?<function>\b[a-zA-Z_$][a-zA-Z0-9_$]*\s*(?=\())""" +
                    """|(?<operator>[+\-*/%=<>!&|^~]+)"""
            )
            LanguageType.JSON -> Regex(
                """(?<string>"(?:[^"\\]|\\.)*")""" +
                    """|(?<number>-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b)""" +
                    (if (keywordGroup.isEmpty()) "" else "|$keywordGroup")
            )
            LanguageType.HTML_CSS -> Regex(
                """(?<comment><!--[\s\S]*?-->|/\*[\s\S]*?\*/)""" +
                    """|(?<string>"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')""" +
                    """|(?<number>\b\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw|vmin|vmax|s|ms|fr|deg|ch|ex)?\b|#[0-9a-fA-F]{3,8}\b)""" +
                    """|(?<keyword></?[A-Za-z][A-Za-z0-9]*[^>]*>|!important)""" +
                    (if (keywordGroup.isEmpty()) "" else "|$keywordGroup")
            )
            LanguageType.SHELL -> Regex(
                """(?m)(?<comment>#[^\n]*)""" +
                    """|(?<string>"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')""" +
                    """|(?<variable>\$[A-Za-z_][A-Za-z0-9_]*|\$\{[^}]*\}|\$[0-9@#?$!*-])""" +
                    """|(?<number>\b\d+\b)""" +
                    (if (keywordGroup.isEmpty()) "" else "|$keywordGroup") +
                    """|(?<function>\b[a-zA-Z_][a-zA-Z0-9_]*\s*(?=\())""" +
                    """|(?<operator>[|&;<>]+)"""
            )
            LanguageType.MARKDOWN -> Regex(
                """(?m)(?<comment>^<!--[\s\S]*?-->$)""" +
                    """|(?<string>^```[\s\S]*?^```$|`[^`\n]+`)""" +
                    """|(?<keyword>^#{1,6}[^\n]*)""" +
                    """|(?<function>\[[^\]\n]*\]\([^)\n]*\))""" +
                    """|(?<operator>\*\*[^*\n]+\*\*|__[^_\n]+__|\*[^*\n]+\*|(?<![A-Za-z0-9_])_[^_\n]+_(?![A-Za-z0-9_]))"""
            )
            LanguageType.TEXT -> null
        }
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
data class EditorDecorations(
    val currentLineRange: IntRange? = null,
    val findMatches: List<IntRange> = emptyList(),
    val activeFindMatch: IntRange? = null,
    val bracketRanges: List<IntRange> = emptyList(),
    val diagnostics: List<EditorDiagnostic> = emptyList()
)

class SyntaxVisualTransformation(
    private val theme: EditorThemeType = EditorThemeType.DRACULA,
    private val decorations: EditorDecorations = EditorDecorations(),
    private val language: LanguageType = LanguageType.C
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            text = buildAnnotatedString {
                append(MultiLanguageSyntaxHighlighter.highlight(text.text, getEditorTheme(theme), language))
                addDecorations(text.text)
            },
            offsetMapping = OffsetMapping.Identity
        )
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
