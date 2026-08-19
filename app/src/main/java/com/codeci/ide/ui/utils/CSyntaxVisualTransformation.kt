package com.codeci.ide.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import com.codeci.ide.ui.theme.EditorThemeColors
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.getEditorTheme

class CSyntaxVisualTransformation(private val theme: EditorThemeType = EditorThemeType.DRACULA) : VisualTransformation {

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
            val stringPattern = "\"([^\"\\\\]|\\\\.)*\"".toRegex()
            stringPattern.findAll(text).forEach { match ->
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
        }
    }
}
