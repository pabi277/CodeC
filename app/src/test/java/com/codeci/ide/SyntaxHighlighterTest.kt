package com.codeci.ide

import com.codeci.ide.ui.theme.DraculaTheme
import com.codeci.ide.ui.utils.LanguageType
import com.codeci.ide.ui.utils.MultiLanguageSyntaxHighlighter
import com.codeci.ide.ui.utils.TokenKind
import com.codeci.ide.ui.utils.TokenSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 12 — multi-language syntax highlighter unit tests. Pure Kotlin
 * (tokenize returns plain data), so no Android/Robolectric runtime is needed.
 */
class SyntaxHighlighterTest {

    private fun assertSpan(source: String, spans: List<TokenSpan>, token: String, kind: TokenKind) {
        val found = spans.any {
            it.kind == kind && source.substring(it.start, it.end).contains(token)
        }
        assertTrue("no $kind span covering '$token' in $spans", found)
    }

    private fun assertNoSpan(source: String, spans: List<TokenSpan>, token: String, kind: TokenKind) {
        val found = spans.any {
            it.kind == kind && source.substring(it.start, it.end).contains(token)
        }
        assertTrue("unexpected $kind span covering '$token' in $spans", !found)
    }

    @Test
    fun `language resolves from file extension`() {
        assertEquals(LanguageType.C, LanguageType.fromFileName("main.c"))
        assertEquals(LanguageType.C, LanguageType.fromFileName("src/header.h"))
        assertEquals(LanguageType.CPP, LanguageType.fromFileName("app.cpp"))
        assertEquals(LanguageType.PYTHON, LanguageType.fromFileName("script.py"))
        assertEquals(LanguageType.PYTHON, LanguageType.fromFileName("code/run.PY"))
        assertEquals(LanguageType.JAVASCRIPT, LanguageType.fromFileName("app.js"))
        assertEquals(LanguageType.JAVASCRIPT, LanguageType.fromFileName("component.tsx"))
        assertEquals(LanguageType.HTML_CSS, LanguageType.fromFileName("index.html"))
        assertEquals(LanguageType.HTML_CSS, LanguageType.fromFileName("style.css"))
        assertEquals(LanguageType.JSON, LanguageType.fromFileName("data.json"))
        assertEquals(LanguageType.SHELL, LanguageType.fromFileName("setup.sh"))
        assertEquals(LanguageType.MARKDOWN, LanguageType.fromFileName("README.md"))
        assertEquals(LanguageType.TEXT, LanguageType.fromFileName("notes.txt"))
        assertEquals(LanguageType.TEXT, LanguageType.fromFileName("Makefile"))
        assertEquals(LanguageType.TEXT, LanguageType.fromFileName(""))
    }

    @Test
    fun `c keywords strings comments numbers and functions are tokenized`() {
        val source = "int main() { printf(\"hi\\n\"); // note\nreturn 0; }"
        val spans = MultiLanguageSyntaxHighlighter.tokenize(source, LanguageType.C)
        assertSpan(source, spans, "int", TokenKind.KEYWORD)
        assertSpan(source, spans, "return", TokenKind.KEYWORD)
        assertSpan(source, spans, "main", TokenKind.FUNCTION)
        assertSpan(source, spans, "\"hi\\n\"", TokenKind.STRING)
        assertSpan(source, spans, "// note", TokenKind.COMMENT)
        assertSpan(source, spans, "0", TokenKind.NUMBER)
    }

    @Test
    fun `c preprocessor directives are keywords`() {
        val source = "#include <stdio.h>\n#define MAX 10"
        val spans = MultiLanguageSyntaxHighlighter.tokenize(source, LanguageType.C)
        assertSpan(source, spans, "#include", TokenKind.KEYWORD)
        assertSpan(source, spans, "#define", TokenKind.KEYWORD)
        assertSpan(source, spans, "10", TokenKind.NUMBER)
    }

    @Test
    fun `python keywords decorators docstrings and comments are tokenized`() {
        val source = "# comment\n" +
            "def hello(name):\n" +
            "    \"\"\"docstring\"\"\"\n" +
            "    print(f\"hi {name}\")  # inline\n" +
            "@app.route\n" +
            "class Greeter:\n" +
            "    pass\n"
        val spans = MultiLanguageSyntaxHighlighter.tokenize(source, LanguageType.PYTHON)
        assertSpan(source, spans, "# comment", TokenKind.COMMENT)
        assertSpan(source, spans, "def", TokenKind.KEYWORD)
        assertSpan(source, spans, "hello", TokenKind.FUNCTION)
        assertSpan(source, spans, "\"\"\"docstring\"\"\"", TokenKind.STRING)
        assertSpan(source, spans, "print", TokenKind.FUNCTION)
        assertSpan(source, spans, "f\"hi {name}\"", TokenKind.STRING)
        assertSpan(source, spans, "@app.route", TokenKind.DECORATOR)
        assertSpan(source, spans, "class", TokenKind.KEYWORD)
        assertSpan(source, spans, "pass", TokenKind.KEYWORD)
        assertSpan(source, spans, "# inline", TokenKind.COMMENT)
        // The identifier 'name' is not a keyword span.
        assertNoSpan(source, spans, "name", TokenKind.KEYWORD)
    }

    @Test
    fun `javascript template literals and block comments are tokenized`() {
        val source = "const x = 1; // one\n/* block */\nfunction f() { return `hi`; }"
        val spans = MultiLanguageSyntaxHighlighter.tokenize(source, LanguageType.JAVASCRIPT)
        assertSpan(source, spans, "const", TokenKind.KEYWORD)
        assertSpan(source, spans, "function", TokenKind.KEYWORD)
        assertSpan(source, spans, "return", TokenKind.KEYWORD)
        assertSpan(source, spans, "1", TokenKind.NUMBER)
        assertSpan(source, spans, "// one", TokenKind.COMMENT)
        assertSpan(source, spans, "/* block */", TokenKind.COMMENT)
        assertSpan(source, spans, "`hi`", TokenKind.STRING)
        assertSpan(source, spans, "f", TokenKind.FUNCTION)
    }

    @Test
    fun `shell variables comments and keywords are tokenized`() {
        val source = "#!/bin/bash\nif [ -f \"\$HOME/x\" ]; then\n    echo \$HOME\nfi"
        val spans = MultiLanguageSyntaxHighlighter.tokenize(source, LanguageType.SHELL)
        assertSpan(source, spans, "#!/bin/bash", TokenKind.COMMENT)
        assertSpan(source, spans, "if", TokenKind.KEYWORD)
        assertSpan(source, spans, "then", TokenKind.KEYWORD)
        assertSpan(source, spans, "fi", TokenKind.KEYWORD)
        assertSpan(source, spans, "echo", TokenKind.KEYWORD)
        assertSpan(source, spans, "\"\$HOME/x\"", TokenKind.STRING)
        assertSpan(source, spans, "\$HOME", TokenKind.OPERATOR)
    }

    @Test
    fun `json strings numbers and literals are tokenized`() {
        val source = "{\"ok\": true, \"count\": 3, \"pi\": 3.14}"
        val spans = MultiLanguageSyntaxHighlighter.tokenize(source, LanguageType.JSON)
        assertSpan(source, spans, "\"ok\"", TokenKind.STRING)
        assertSpan(source, spans, "true", TokenKind.KEYWORD)
        assertSpan(source, spans, "3", TokenKind.NUMBER)
        assertSpan(source, spans, "3.14", TokenKind.NUMBER)
    }

    @Test
    fun `markdown headings code and links are tokenized`() {
        val source = "# Title\n\nSome `inline` code.\n\n[link](https://example.com)\n"
        val spans = MultiLanguageSyntaxHighlighter.tokenize(source, LanguageType.MARKDOWN)
        assertSpan(source, spans, "# Title", TokenKind.KEYWORD)
        assertSpan(source, spans, "`inline`", TokenKind.STRING)
        assertSpan(source, spans, "[link](https://example.com)", TokenKind.FUNCTION)
    }

    @Test
    fun `html tags comments and css numbers are tokenized`() {
        val source = "<!-- hi -->\n<div class=\"x\">text</div>\nbody { color: #fff; margin: 8px; }"
        val spans = MultiLanguageSyntaxHighlighter.tokenize(source, LanguageType.HTML_CSS)
        assertSpan(source, spans, "<!-- hi -->", TokenKind.COMMENT)
        assertSpan(source, spans, "<div", TokenKind.KEYWORD)
        assertSpan(source, spans, "</div", TokenKind.KEYWORD)
        assertSpan(source, spans, "\"x\"", TokenKind.STRING)
        assertSpan(source, spans, "#fff", TokenKind.NUMBER)
        assertSpan(source, spans, "8px", TokenKind.NUMBER)
        assertSpan(source, spans, "color", TokenKind.KEYWORD)
    }

    @Test
    fun `text language has no tokens`() {
        val spans = MultiLanguageSyntaxHighlighter.tokenize("just some words 123", LanguageType.TEXT)
        assertTrue(spans.isEmpty())
    }

    @Test
    fun `highlight applies theme colors to token kinds`() {
        val annotated = MultiLanguageSyntaxHighlighter.highlight(
            "def f():\n    return 1",
            DraculaTheme,
            LanguageType.PYTHON
        )
        assertEquals(annotated.text, "def f():\n    return 1")
        val kinds = annotated.spanStyles.map { it.item.color }
        assertTrue("no keyword-colored span", kinds.contains(DraculaTheme.keyword))
        assertTrue("no number-colored span", kinds.contains(DraculaTheme.number))
    }
}
