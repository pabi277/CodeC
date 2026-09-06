package com.codeci.ide.ui.editor

import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 30.2 — the clean-room Emmet engine: the supported abbreviation subset,
 * caret placement, base indentation, the JSX flavour, and the gates/guards that
 * keep a literal `!` in C (or a `{` in a CSS selector) from ever producing an
 * expansion.
 *
 * Everything here goes through the PUBLIC surface the engine uses
 * ([Emmet.expand], [Emmet.abbreviationAt], [Emmet.completionItemFor],
 * [Emmet.enabledFor]) — the markup and CSS parsers are private by design.
 */
class EmmetTest {

    private fun html(abbr: String, indent: String = "", file: String? = null) =
        Emmet.expand(abbr, LanguageType.HTML, indent, file)?.text

    private fun css(abbr: String, indent: String = "") =
        Emmet.expand(abbr, LanguageType.CSS, indent)?.text

    // ---- the HTML skeleton (30.2 subset: `!`) ------------------------------

    @Test
    fun `bang expands to the html skeleton with the caret inside body`() {
        val expansion = Emmet.expand("!", LanguageType.HTML)
        assertNotNull(expansion)
        val text = expansion!!.text
        assertTrue(text.startsWith("<!DOCTYPE html>\n<html lang=\"en\">"))
        assertTrue(text.contains("<head>"))
        assertTrue(text.contains("<meta charset=\"utf-8\">"))
        assertTrue(text.contains("<meta name=\"viewport\""))
        assertTrue(text.contains("<title>"))
        assertTrue(text.contains("<body>"))
        assertTrue(text.trimEnd().endsWith("</html>"))
        // The caret parks on the blank line inside <body>.
        val caret = expansion.caretOffset
        assertNotNull(caret)
        assertTrue(
            "caret at $caret of <$text>",
            text.substring(caret!!).startsWith("\n    </body>")
        )
    }

    // ---- nesting, siblings, repetition ------------------------------------

    @Test
    fun `child, sibling and repeat operators expand`() {
        assertEquals("<div>\n    <p></p>\n</div>", html("div>p"))
        assertEquals("<h1></h1>\n<p></p>", html("h1+p"))
        assertEquals(
            "<ul>\n    <li></li>\n    <li></li>\n    <li></li>\n</ul>",
            html("ul>li*3")
        )
        assertEquals(
            "<div>\n    <p></p>\n</div>\n<div>\n    <p></p>\n</div>",
            html("(div>p)*2")
        )
    }

    @Test
    fun `the climb operator returns to the parent level`() {
        assertEquals(
            "<div>\n    <p>\n        <span></span>\n    </p>\n    <h2></h2>\n</div>",
            html("div>p>span^h2")
        )
    }

    @Test
    fun `implicit tags fill in the element a child implies`() {
        // `.card` with no name is a div; `ul>*` is a list item; `tr>td` is a
        // table cell — the same implicit rules upstream Emmet uses.
        assertEquals("<div class=\"card\"></div>", html(".card"))
        assertEquals("<ul>\n    <li></li>\n</ul>", html("ul>*"))
        assertEquals(
            "<table>\n    <tr>\n        <td></td>\n    </tr>\n" +
                "    <tr>\n        <td></td>\n    </tr>\n</table>",
            html("table>tr*2>td")
        )
    }

    @Test
    fun `classes, ids, attributes and text content expand`() {
        // The id comes first, the classes join into one attribute.
        assertEquals("<div id=\"main\" class=\"card wide\"></div>", html("div#main.card.wide"))
        assertEquals("<a href=\"#\" target=\"_blank\"></a>", html("a[href=# target=_blank]"))
        assertEquals("<p>hello</p>", html("p{hello}"))
        assertEquals("<img src=\"a.png\" alt=\"hi\">", html("img[src=a.png alt=hi]"))
    }

    @Test
    fun `counter placeholders number repeated siblings`() {
        assertEquals(
            "<ul>\n    <li class=\"item1\"></li>\n    <li class=\"item2\"></li>\n" +
                "    <li class=\"item3\"></li>\n</ul>",
            html("ul>li.item\$*3")
        )
        assertEquals(
            "<ul>\n    <li class=\"item01\"></li>\n    <li class=\"item02\"></li>\n</ul>",
            html("ul>li.item\$\$*2")
        )
        assertEquals(
            "<ul>\n    <li>item 1</li>\n    <li>item 2</li>\n</ul>",
            html("ul>li{item \$}*2")
        )
        // The counter reaches into a nested repeated child, one value each.
        assertEquals(
            "<nav>\n    <ul>\n        <li>\n            <a href=\"#\">Link 1</a>\n" +
                "        </li>\n        <li>\n            <a href=\"#\">Link 2</a>\n" +
                "        </li>\n    </ul>\n</nav>",
            html("nav>ul>li*2>a[href=#]{Link \$}")
        )
    }

    @Test
    fun `void elements stay empty and a slash self closes`() {
        assertEquals("<br>", html("br"))
        assertEquals("<custom />", html("custom/"))
    }

    @Test
    fun `the jsx flavour self closes void elements`() {
        assertEquals("<br />", Emmet.expand("br", LanguageType.JAVASCRIPT, "", "App.jsx")?.text)
        assertEquals(
            "<div>\n    <p></p>\n</div>",
            Emmet.expand("div>p", LanguageType.TYPESCRIPT, "", "App.tsx")?.text
        )
    }

    // ---- caret and indentation --------------------------------------------

    @Test
    fun `the caret parks inside the first empty element`() {
        val expansion = Emmet.expand("div>p", LanguageType.HTML)!!
        assertEquals("</p>\n</div>", expansion.text.substring(expansion.caretOffset!!))
    }

    @Test
    fun `only continuation lines take the caret line indentation`() {
        // The first line is inserted where the caret already is, so prefixing
        // it would double the indent (the bug `finish()` exists to prevent).
        assertEquals(
            "<ul>\n        <li></li>\n        <li></li>\n    </ul>",
            html("ul>li*2", "    ")
        )
        // The caret lands inside the first repeated <li>, not at the end.
        val expansion = Emmet.expand("ul>li*2", LanguageType.HTML, "  ")!!
        assertTrue(
            "caret at ${expansion.caretOffset} of <${expansion.text}>",
            expansion.text.substring(expansion.caretOffset!!).startsWith("</li>")
        )
    }

    // ---- CSS ---------------------------------------------------------------

    @Test
    fun `css abbreviations expand to declarations`() {
        assertEquals("margin: 10px;", css("m10"))
        assertEquals("padding: 10px 20px;", css("p10-20"))
        assertEquals("margin: 0 auto;", css("m0-a"))
        assertEquals("margin-top: -5px;", css("mt-5"))
        assertEquals("display: flex;", css("d:f"))
        assertEquals("display: flex;", css("df"))
        assertEquals("flex-direction: column;", css("fld:c"))
        assertEquals("position: absolute;", css("pos:a"))
        assertEquals("background: #fff;", css("bg#fff"))
        assertEquals("width: 100%;", css("w100p"))
        assertEquals("font-size: 12px;", css("fz12"))
        assertEquals("font-size: 1.5em;", css("fz1.5e"))
        assertEquals("border: 1px solid;", css("bd1-s"))
        assertEquals("border-radius: 4px;", css("br4"))
        assertEquals("margin: 10px !important;", css("m10!"))
        // Unitless properties keep the bare number.
        assertEquals("z-index: 10;", css("z10"))
        assertEquals("opacity: 0;", css("op0"))
    }

    @Test
    fun `css plus chains two declarations and parks after the first colon`() {
        val expansion = Emmet.expand("m10+p20", LanguageType.CSS)!!
        assertEquals("margin: 10px;\npadding: 20px;", expansion.text)
        assertEquals("margin: ".length, expansion.caretOffset)
    }

    @Test
    fun `css refuses an unknown property or a missing value`() {
        assertNull(css("qq10"))
        assertNull(css("m"))
        // Markup operators are not CSS.
        assertNull(css("div>p"))
    }

    // ---- refusals: never guess --------------------------------------------

    @Test
    fun `malformed markup abbreviations are refused`() {
        assertNull(Emmet.expand("(div>p", LanguageType.HTML))
        assertNull(Emmet.expand(">div", LanguageType.HTML))
        assertNull(Emmet.expand("div*", LanguageType.HTML))
        assertNull(Emmet.expand("div*99999", LanguageType.HTML))
        assertNull(Emmet.expand("p{hello", LanguageType.HTML))
        assertNull(Emmet.expand("a[href=#", LanguageType.HTML))
        assertNull(Emmet.expand("", LanguageType.HTML))
        assertNull(Emmet.expand("   ", LanguageType.HTML))
    }

    @Test
    fun `an abbreviation past the length cap is refused`() {
        val long = "div>".repeat(60)
        assertTrue(long.length > Emmet.MAX_ABBREVIATION)
        assertNull(Emmet.expand(long, LanguageType.HTML))
    }

    // ---- the token under the caret, and the guards around it ---------------

    @Test
    fun `the token walk back stops at whitespace and needs a signal`() {
        val body = "<body>\n  ul>li*3"
        assertEquals("ul>li*3", Emmet.abbreviationAt(body, body.length, LanguageType.HTML))
        assertEquals("div.card>p", Emmet.abbreviationAt("div.card>p", 10, LanguageType.HTML))
        assertEquals("li*2", Emmet.abbreviationAt("ul> li*2", 8, LanguageType.HTML))
        // A bare word is the snippet pack's job, not Emmet's.
        assertNull(Emmet.abbreviationAt("div", 3, LanguageType.HTML))
        // Prose and member access carry a dot but no structural intent.
        assertNull(Emmet.abbreviationAt("<p>Hello.World</p>", 13, LanguageType.HTML))
        // `!` and a leading `.` are signals on their own.
        assertEquals("!", Emmet.abbreviationAt("!", 1, LanguageType.HTML))
        assertEquals(".card", Emmet.abbreviationAt(".card", 5, LanguageType.HTML))
    }

    @Test
    fun `a text node keeps its spaces, prose still stops at one`() {
        // `a{Link $}` and `p{Hello World}` are Emmet text/numbered nodes: the
        // space sits INSIDE the braces, so the walk-back keeps it.
        val numbered = "a{Link \$}"
        assertEquals(numbered, Emmet.abbreviationAt(numbered, numbered.length, LanguageType.HTML))
        val body = "<body>\n  p{Hello World}"
        assertEquals("p{Hello World}", Emmet.abbreviationAt(body, body.length, LanguageType.HTML))
        assertNotNull(Emmet.completionItemFor(numbered, numbered.length, LanguageType.HTML, "i.html"))
        // Two text nodes, each with a space, in one abbreviation. Carets are
        // `.length`, never a hand-counted literal: this case first shipped with
        // 16 for a 17-char string, so the walk-back stopped before the closing
        // `}` and the assertion (not the engine) was wrong — CI run
        // 34040754444, the phase's one for-cause round.
        val twoNodes = "div>p{a b}+p{c d}"
        assertEquals(twoNodes, Emmet.abbreviationAt(twoNodes, twoNodes.length, LanguageType.HTML))
        // Outside a text node a space still ends the token (`ul> li*2` → `li*2`),
        // prose never fires, and an UNBALANCED `{` ahead of the caret produces
        // no expansion — the walk-back may return `{World`, but the parser
        // refuses it (refusing beats guessing, §3.3).
        assertEquals("li*2", Emmet.abbreviationAt("ul> li*2", 8, LanguageType.HTML))
        assertNull(Emmet.completionItemFor("<p>Hello {World", 15, LanguageType.HTML, "i.html"))
        assertNull(Emmet.expand("{World", LanguageType.HTML, ""))
        assertNull(Emmet.abbreviationAt("<p>Hello World", 14, LanguageType.HTML))
        // CSS has no brace-text syntax: a space ends the token there too.
        assertNull(Emmet.abbreviationAt("a { p10 20", 10, LanguageType.CSS))
    }

    @Test
    fun `markup is refused inside a tag, a string or a comment`() {
        assertNull(Emmet.abbreviationAt("<div class=x>", 12, LanguageType.HTML))
        assertNull(Emmet.abbreviationAt("<a href=\"ul>li", 14, LanguageType.HTML))
        assertNull(Emmet.abbreviationAt("<!-- ul>li*2", 12, LanguageType.HTML))
    }

    @Test
    fun `css is refused inside a string, a comment or a selector list`() {
        assertEquals("m10", Emmet.abbreviationAt("  m10", 5, LanguageType.CSS))
        assertNull(Emmet.abbreviationAt("margin", 6, LanguageType.CSS))
        assertNull(Emmet.abbreviationAt(".card, m10", 10, LanguageType.CSS))
        assertNull(Emmet.abbreviationAt("/* m10", 6, LanguageType.CSS))
        assertNull(Emmet.abbreviationAt("content: \"m10", 13, LanguageType.CSS))
    }

    // ---- the language gate (exit condition 30.2 §2.3) ----------------------

    @Test
    fun `emmet never fires in C, Python or a plain JS file`() {
        assertFalse(Emmet.enabledFor(LanguageType.C))
        assertFalse(Emmet.enabledFor(LanguageType.PYTHON))
        assertFalse(Emmet.enabledFor(LanguageType.JAVASCRIPT, "app.js"))
        assertFalse(Emmet.enabledFor(LanguageType.JAVASCRIPT))
        assertTrue(Emmet.enabledFor(LanguageType.HTML))
        assertTrue(Emmet.enabledFor(LanguageType.CSS))
        assertTrue(Emmet.enabledFor(LanguageType.JAVASCRIPT, "app.jsx"))
        assertTrue(Emmet.enabledFor(LanguageType.TYPESCRIPT, "app.tsx"))
        // A `!` in C is a logical NOT: no token, no item.
        assertNull(Emmet.abbreviationAt("if (!x)", 6, LanguageType.C, "main.c"))
        assertNull(Emmet.completionItemFor("if (!x)", 6, LanguageType.C, "main.c"))
        assertNull(Emmet.abbreviationAt("ul>li", 5, LanguageType.PYTHON, "a.py"))
        assertNull(Emmet.abbreviationAt("ul>li*3", 7, LanguageType.JAVASCRIPT, "app.js"))
    }

    @Test
    fun `jsx files only fire on a real structural operator`() {
        assertEquals("ul>li*3", Emmet.abbreviationAt("ul>li*3", 7, LanguageType.JAVASCRIPT, "app.jsx"))
        assertEquals("div>p", Emmet.abbreviationAt("div>p", 5, LanguageType.TYPESCRIPT, "app.tsx"))
        // In a JS expression `.` and `[` are member access, not markup.
        assertNull(Emmet.abbreviationAt("obj.method", 10, LanguageType.JAVASCRIPT, "a.tsx"))
        assertNull(Emmet.abbreviationAt("!", 1, LanguageType.JAVASCRIPT, "a.tsx"))
    }

    // ---- the completion item the engine prepends ---------------------------

    @Test
    fun `the item carries the abbreviation, the emmet detail and the caret`() {
        val body = "<body>\n  ul>li*3"
        val item = Emmet.completionItemFor(body, body.length, LanguageType.HTML)
        assertNotNull(item)
        item!!
        assertEquals("ul>li*3", item.label)
        assertEquals(Emmet.DETAIL, item.detail)
        assertEquals(CompletionKind.SNIPPET, item.kind)
        // The whole abbreviation is replaced, not just the identifier prefix.
        assertEquals(7, item.replaceLength)
        // The caret parks inside the first <li> of the expansion.
        assertTrue(
            "caret at ${item.caretOffset} of <${item.insertText}>",
            item.insertText.substring(item.caretOffset!!).startsWith("</li>")
        )
        // Two spaces of caret indentation carry into the expansion.
        assertTrue(
            "insert was <${item.insertText}>",
            item.insertText.startsWith("<ul>\n      <li>")
        )
    }

    @Test
    fun `a css item expands and parks after the colon`() {
        val item = Emmet.completionItemFor("  m10", 5, LanguageType.CSS, "site.css")
        assertNotNull(item)
        assertEquals("m10", item!!.label)
        assertEquals(Emmet.DETAIL, item.detail)
        assertEquals("margin: 10px;", item.insertText)
        assertEquals(3, item.replaceLength)
        assertEquals("margin: ".length, item.caretOffset)
    }

    @Test
    fun `no item when there is nothing to expand`() {
        assertNull(Emmet.completionItemFor("<div>", 5, LanguageType.HTML))
        assertNull(Emmet.completionItemFor("plain prose", 11, LanguageType.HTML))
        assertNull(Emmet.completionItemFor("", 0, LanguageType.HTML))
        assertNull(Emmet.completionItemFor("int x = 1;", 10, LanguageType.C, "main.c"))
    }
}
