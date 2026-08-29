package com.codeci.ide.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DrawStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.codeci.ide.ui.editor.DiagnosticSeverity
import com.codeci.ide.ui.editor.EditorDiagnostic
import com.codeci.ide.ui.theme.EditorThemeColors
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.getEditorTheme

/**
 * Phase 9 — decoration layers combined with C syntax highlighting.
 * All ranges are offsets into the (untransformed) buffer text; the
 * transformation is identity-mapped so indices match directly.
 */
data class EditorDecorations(
    val currentLineRange: IntRange? = null,
    val findMatches: List<IntRange> = emptyList(),
    val activeFindMatch: IntRange? = null,
    val bracketRanges: List<IntRange> = emptyList(),
    val diagnostics: List<EditorDiagnostic> = emptyList()
)

private val SquigglePathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)

class CSyntaxVisualTransformation(
    private val theme: EditorThemeType = EditorThemeType.DRACULA,
    private val decorations: EditorDecorations = EditorDecorations()
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            text = buildHighlightedString(text.text, getEditorTheme(theme)),
            offsetMapping = OffsetMapping.Identity
        )
    }

    private fun buildHighlightedString(text: String, colors: EditorThemeColors): AnnotatedString {
        return buildAnnotatedString {
            append(text)

            // Apply text color as base
            addStyle(SpanStyle(color = colors.text), 0, text.length)

            // Keywords
            val keywords = listOf(
                "auto", "break", "case", "char", "const", "continue", "default", "do",
                "double", "else", "enum", "extern", "float", "for", "goto", "if",
                "int", "long", "register", "return", "short", "signed", "sizeof", "static",
                "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
                "#include", "#define", "#ifndef", "#endif", "#pragma"
            )

            val keywordPattern = "\\b(${keywords.joinToString("|")})\\b|#\\w+".toRegex()
            keywordPattern.findAll(text).forEach { match ->
                addStyle(SpanStyle(color = colors.keyword), match.range.first, match.range.last + 1)
            }

            // Numbers
            val numberPattern = "\\b\\d+(\\.\\d+)?\\b".toRegex()
            numberPattern.findAll(text).forEach { match ->
                addStyle(SpanStyle(color = colors.number), match.range.first, match.range.last + 1)
            }

            // Functions
            val functionPattern = "\\b[a-zA-Z_][a-zA-Z0-9_]*\\s*(?=\\()".toRegex()
            functionPattern.findAll(text).forEach { match ->
                addStyle(SpanStyle(color = colors.function), match.range.first, match.range.last + 1)
            }

            // Operators (Simple matching)
            val operatorPattern = "[+\\-*/%=<>!&|^~]+".toRegex()
            operatorPattern.findAll(text).forEach { match ->
                addStyle(SpanStyle(color = colors.operator), match.range.first, match.range.last + 1)
            }

            // Strings
            val stringPattern = "\"([^\"\\\\]|\\\\.)*\""
            Regex(stringPattern).findAll(text).forEach { match ->
                addStyle(SpanStyle(color = colors.string), match.range.first, match.range.last + 1)
            }

            // Comments (Single line for now)
            val commentPattern = "//.*".toRegex()
            commentPattern.findAll(text).forEach { match ->
                addStyle(SpanStyle(color = colors.comment), match.range.first, match.range.last + 1)
            }

            // Multi-line Comments
            val multiCommentPattern = "/\\*[\\s\\S]*?\\*/".toRegex()
            multiCommentPattern.findAll(text).forEach { match ->
                addStyle(SpanStyle(color = colors.comment), match.range.first, match.range.last + 1)
            }

            addDecorations(text)
        }
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
            // Wavy-ish red underline from the reported column to the line end.
            val squiggleStart = (bounds.first + diagnostic.column - 1).coerceIn(bounds.first, bounds.last + 1)
            addClamped(
                SpanStyle(
                    color = if (diagnostic.severity == DiagnosticSeverity.ERROR) {
                        ErrorSquiggle
                    } else {
                        WarnSquiggle
                    },
                    textDecoration = TextDecoration.Underline,
                    drawStyle = DrawStyle(pathEffect = SquigglePathEffect)
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
        com.codeci.ide.ui.editor.CodeFormatter.lineBounds(text, line)

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
