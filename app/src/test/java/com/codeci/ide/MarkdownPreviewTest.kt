package com.codeci.ide

import com.codeci.ide.ui.utils.MarkdownPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 68.1 (completed part) — the Markdown preview's rendering half.
 *
 * The converter is the piece that makes Run ▶ on a `.md` file a real preview
 * instead of raw source, so these cases pin its contract: common README
 * constructs render, raw HTML in the document can NEVER inject markup, and
 * hostile URLs never reach the page.
 */
class MarkdownPreviewTest {

    // ---- escaping / safety ---------------------------------------------------

    @Test
    fun `raw html in the document is escaped, never injected`() {
        val html = MarkdownPreview.toHtml("hello <script>alert(1)</script> world")
        assertFalse("script tag must not survive", html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `javascript urls are neutralised in links`() {
        val html = MarkdownPreview.toHtml("[tap me](javascript:alert(1))")
        assertFalse(html.contains("javascript:"))
        assertTrue(html.contains("<a href=\"#\">tap me</a>"))
    }

    @Test
    fun `javascript urls are neutralised in images`() {
        val html = MarkdownPreview.toHtml("![x](javascript:alert(1))")
        assertFalse(html.contains("javascript:"))
        assertTrue(html.contains("<img src=\"#\" alt=\"x\">"))
    }

    @Test
    fun `relative and http urls pass through`() {
        val html = MarkdownPreview.toHtml("[a](docs/a.md) [b](https://example.com) ![c](./img/logo.png)")
        assertTrue(html.contains("href=\"docs/a.md\""))
        assertTrue(html.contains("href=\"https://example.com\""))
        assertTrue(html.contains("src=\"./img/logo.png\""))
    }

    // ---- blocks ---------------------------------------------------------------

    @Test
    fun `headings render at their own level`() {
        val html = MarkdownPreview.toHtml("# One\n## Two\n###### Six")
        assertTrue(html.contains("<h1>One</h1>"))
        assertTrue(html.contains("<h2>Two</h2>"))
        assertTrue(html.contains("<h6>Six</h6>"))
    }

    @Test
    fun `fenced code keeps its content verbatim and names the language`() {
        val html = MarkdownPreview.toHtml("```c\nint main(void) { return 0; }\n```\n")
        assertTrue(html.contains("<pre><code class=\"language-c\">"))
        assertTrue(html.contains("int main(void) { return 0; }"))
        // Emphasis syntax inside a fence is NOT formatting.
        val html2 = MarkdownPreview.toHtml("```\n**not bold**\n```")
        assertFalse(html2.contains("<strong>"))
    }

    @Test
    fun `an unclosed fence swallows the rest as code`() {
        val html = MarkdownPreview.toHtml("```\nline one\nline two")
        assertTrue(html.contains("line one"))
        assertTrue(html.contains("line two"))
        assertFalse(html.contains("<p>line one"))
    }

    @Test
    fun `unordered and ordered lists render`() {
        val ul = MarkdownPreview.toHtml("- alpha\n- beta")
        assertTrue(ul.contains("<ul>"))
        assertTrue(ul.contains("<li>alpha</li>"))
        assertTrue(ul.contains("<li>beta</li>"))

        val ol = MarkdownPreview.toHtml("1. first\n2. second")
        assertTrue(ol.contains("<ol>"))
        assertTrue(ol.contains("<li>first</li>"))
    }

    @Test
    fun `indented items nest into the previous item`() {
        val html = MarkdownPreview.toHtml("- outer\n  - inner")
        val outerAt = html.indexOf("<li>outer")
        val innerListAt = html.indexOf("<ul>", outerAt)
        val outerCloseAt = html.indexOf("</li>", innerListAt)
        assertTrue("the sublist must sit INSIDE the outer item", innerListAt in outerAt until outerCloseAt)
        assertTrue(html.contains("<li>inner</li>"))
    }

    @Test
    fun `blockquotes render with breaks between lines`() {
        val html = MarkdownPreview.toHtml("> quoted line one\n> quoted line two")
        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("quoted line one<br>quoted line two"))
    }

    @Test
    fun `horizontal rules render from dashes, stars and underscores`() {
        assertTrue(MarkdownPreview.toHtml("---").contains("<hr>"))
        assertTrue(MarkdownPreview.toHtml("***").contains("<hr>"))
        assertTrue(MarkdownPreview.toHtml("___").contains("<hr>"))
    }

    @Test
    fun `tables render header and body cells`() {
        val md = "| Name | Value |\n| --- | --- |\n| a | 1 |\n| b | 2 |"
        val html = MarkdownPreview.toHtml(md)
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("<th>Name</th>"))
        assertTrue(html.contains("<td>1</td>"))
        assertTrue(html.contains("<td>b</td>"))
    }

    @Test
    fun `blank lines separate paragraphs and soft lines flow together`() {
        val html = MarkdownPreview.toHtml("line one\nline two\n\nsecond paragraph")
        assertTrue(html.contains("<p>line one line two</p>"))
        assertTrue(html.contains("<p>second paragraph</p>"))
    }

    @Test
    fun `two trailing spaces force a hard break`() {
        val html = MarkdownPreview.toHtml("first  \nsecond")
        assertTrue(html.contains("first<br>second"))
    }

    // ---- inlines ---------------------------------------------------------------

    @Test
    fun `bold italic strikethrough and code render`() {
        val html = MarkdownPreview.toHtml("**bold** *ital* ~~gone~~ `code`")
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<em>ital</em>"))
        assertTrue(html.contains("<del>gone</del>"))
        assertTrue(html.contains("<code>code</code>"))
    }

    @Test
    fun `code spans protect their content from formatting`() {
        val html = MarkdownPreview.toHtml("use `**not bold**` here")
        assertFalse(html.contains("<strong>"))
        assertTrue(html.contains("<code>**not bold**</code>"))
    }

    @Test
    fun `links and images render`() {
        val html = MarkdownPreview.toHtml("[CodeC](https://example.com) and ![logo](img/logo.png)")
        assertTrue(html.contains("<a href=\"https://example.com\">CodeC</a>"))
        assertTrue(html.contains("<img src=\"img/logo.png\" alt=\"logo\">"))
    }

    // ---- page shell --------------------------------------------------------------

    @Test
    fun `the page shell is a full inert document in the right palette`() {
        val body = MarkdownPreview.toHtml("# Hi")
        val dark = MarkdownPreview.page(body, dark = true, title = "README.md")
        val light = MarkdownPreview.page(body, dark = false, title = "README.md")
        for (page in listOf(dark, light)) {
            assertTrue(page.contains("<!DOCTYPE html>"))
            assertTrue(page.contains("name=\"viewport\""))
            assertTrue(page.contains("<h1>Hi</h1>"))
            assertTrue(page.contains("<title>README.md</title>"))
            assertFalse("the preview must stay inert", page.contains("<script"))
        }
        assertTrue(dark.contains("color-scheme: dark"))
        assertTrue(light.contains("color-scheme: light"))
        assertFalse("the palettes must differ", dark.contains("#ffffff"))
        assertTrue(light.contains("#ffffff"))
    }

    @Test
    fun `the page shell escapes its title`() {
        val page = MarkdownPreview.page("", dark = false, title = "<evil>.md")
        assertFalse(page.contains("<title><evil>"))
        assertTrue(page.contains("&lt;evil&gt;.md"))
    }

    @Test
    fun `a real readme shape renders end to end`() {
        val md = """
            # CodeC

            A **C** IDE for Android. See [the docs](docs/README.md).

            ## Features

            - tabs
            - terminal
              - with history

            ```c
            #include <stdio.h>
            ```

            > Built for phones.
        """.trimIndent()
        val html = MarkdownPreview.toHtml(md)
        assertTrue(html.contains("<h1>CodeC</h1>"))
        assertTrue(html.contains("<strong>C</strong>"))
        assertTrue(html.contains("href=\"docs/README.md\""))
        assertTrue(html.contains("<h2>Features</h2>"))
        assertTrue(html.contains("<li>tabs</li>"))
        assertTrue(html.contains("<li>with history</li>"))
        assertTrue(html.contains("#include &lt;stdio.h&gt;"))
        assertTrue(html.contains("<blockquote>Built for phones.</blockquote>"))
        assertEquals(html, MarkdownPreview.toHtml(md)) // deterministic
    }
}
