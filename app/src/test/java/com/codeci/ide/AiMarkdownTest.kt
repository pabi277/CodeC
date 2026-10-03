package com.codeci.ide

import com.codeci.ide.ui.ai.AiMarkdown
import com.codeci.ide.ui.ai.AiMdBlock
import com.codeci.ide.ui.ai.AiMdCell
import com.codeci.ide.ui.ai.AiMdInline
import com.codeci.ide.ui.ai.AiMdItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 88.1 / Level 11 — the pure Markdown model for AI answers. */
class AiMarkdownTest {

    private fun text(s: String) = AiMdInline.Text(s)
    private fun para(vararg inlines: AiMdInline) = AiMdBlock.Paragraph(inlines.toList())
    private fun cell(s: String) = AiMdCell(if (s.isEmpty()) emptyList() else listOf(text(s)))
    private fun item(s: String, checked: Boolean? = null) = AiMdItem(checked, listOf(para(text(s))))

    /** Every inline node, depth first, so a test can ask "is there any X anywhere". */
    private fun allInlines(blocks: List<AiMdBlock>): List<AiMdInline> {
        val out = ArrayList<AiMdInline>()
        fun walkInlines(list: List<AiMdInline>) {
            for (i in list) {
                out.add(i)
                when (i) {
                    is AiMdInline.Strong -> walkInlines(i.children)
                    is AiMdInline.Emphasis -> walkInlines(i.children)
                    is AiMdInline.Strike -> walkInlines(i.children)
                    is AiMdInline.Link -> walkInlines(i.children)
                    else -> Unit
                }
            }
        }
        fun walk(list: List<AiMdBlock>) {
            for (b in list) {
                when (b) {
                    is AiMdBlock.Heading -> walkInlines(b.inlines)
                    is AiMdBlock.Paragraph -> walkInlines(b.inlines)
                    is AiMdBlock.Bullets -> b.items.forEach { walk(it.blocks) }
                    is AiMdBlock.Quote -> walk(b.blocks)
                    is AiMdBlock.Table -> (listOf(b.header) + b.rows).forEach { row -> row.forEach { walkInlines(it.inlines) } }
                    else -> Unit
                }
            }
        }
        walk(blocks)
        return out
    }

    // ---- headings ------------------------------------------------------------

    @Test
    fun `ATX headings need a space, strip closing hashes, and hashtags stay text`() {
        assertEquals(
            listOf(
                AiMdBlock.Heading(1, listOf(text("Title"))),
                AiMdBlock.Heading(3, listOf(text("Third"))),
                para(text("#hashtag stays text"))
            ),
            AiMarkdown.parse("# Title\n### Third ###\n\n#hashtag stays text")
        )
        assertEquals(listOf(para(text("####### seven is text"))), AiMarkdown.parse("####### seven is text"))
    }

    @Test
    fun `setext underlines make levels 1 and 2 and head only the line above`() {
        assertEquals(
            listOf(
                para(text("Intro line")),
                AiMdBlock.Heading(1, listOf(text("Big"))),
                AiMdBlock.Heading(2, listOf(text("Small")))
            ),
            AiMarkdown.parse("Intro line\nBig\n===\nSmall\n---")
        )
    }

    @Test
    fun `a rule alone is a rule, but under a sentence it is a setext heading`() {
        assertEquals(listOf(AiMdBlock.Rule, AiMdBlock.Rule, AiMdBlock.Rule), AiMarkdown.parse("---\n\n* * *\n\n___"))
        assertEquals(listOf(AiMdBlock.Heading(2, listOf(text("A sentence")))), AiMarkdown.parse("A sentence\n---"))
    }

    // ---- paragraphs ------------------------------------------------------------

    @Test
    fun `a single newline is a line break and a blank line starts a new paragraph`() {
        assertEquals(
            listOf(
                para(text("Step 1: open"), AiMdInline.LineBreak, text("Step 2: run")),
                para(text("Done."))
            ),
            AiMarkdown.parse("Step 1: open\nStep 2: run\n\nDone.")
        )
        // The MarkdownPreview hard-break forms also break (their markers vanish).
        assertEquals(
            listOf(para(text("a"), AiMdInline.LineBreak, text("b"), AiMdInline.LineBreak, text("c"))),
            AiMarkdown.parse("a  \nb\\\nc")
        )
        // CRLF and CR are normalised.
        assertEquals(AiMarkdown.parse("x\ny"), AiMarkdown.parse("x\r\ny"))
        assertEquals(AiMarkdown.parse("x\ny"), AiMarkdown.parse("x\ry"))
    }

    // ---- lists -----------------------------------------------------------------

    @Test
    fun `bullets, ordered lists with their start number, and task items`() {
        assertEquals(
            listOf(AiMdBlock.Bullets(false, 1, listOf(item("one"), item("two"), item("three")))),
            AiMarkdown.parse("- one\n* two\n+ three")
        )
        assertEquals(
            listOf(AiMdBlock.Bullets(true, 3, listOf(item("three"), item("four")))),
            AiMarkdown.parse("3. three\n4) four")
        )
        assertEquals(
            listOf(AiMdBlock.Bullets(false, 1, listOf(item("todo", false), item("done", true), item("[y] not a task")))),
            AiMarkdown.parse("- [ ] todo\n- [x] done\n- [y] not a task")
        )
    }

    @Test
    fun `nested lists follow indentation, including the two-space nesting under an ordered item`() {
        val expected = listOf(
            AiMdBlock.Bullets(
                true, 1,
                listOf(
                    AiMdItem(null, listOf(para(text("Install")), AiMdBlock.Bullets(false, 1, listOf(item("download"), item("unzip"))))),
                    item("Run")
                )
            )
        )
        assertEquals(expected, AiMarkdown.parse("1. Install\n   - download\n   - unzip\n2. Run"))
        assertEquals(expected, AiMarkdown.parse("1. Install\n  - download\n  - unzip\n2. Run"))
    }

    @Test
    fun `a list interrupts a paragraph, but a year-like number does not`() {
        assertEquals(
            listOf(para(text("Steps:")), AiMdBlock.Bullets(true, 1, listOf(item("a"), item("b")))),
            AiMarkdown.parse("Steps:\n1. a\n2. b")
        )
        assertEquals(
            listOf(para(text("It happened in"), AiMdInline.LineBreak, text("2019. A good year."))),
            AiMarkdown.parse("It happened in\n2019. A good year.")
        )
    }

    // ---- quotes ----------------------------------------------------------------

    @Test
    fun `quotes nest and keep their content`() {
        assertEquals(
            listOf(AiMdBlock.Quote(listOf(para(text("outer")), AiMdBlock.Quote(listOf(para(text("inner"))))))),
            AiMarkdown.parse("> outer\n>\n> > inner")
        )
    }

    @Test
    fun `nesting beyond the cap flattens into the deepest level without losing content`() {
        val deepQuote = ">".repeat(AiMarkdown.MAX_NESTING + 5) + " deepest words"
        var blocks = AiMarkdown.parse(deepQuote)
        var depth = 0
        while (blocks.singleOrNull() is AiMdBlock.Quote) {
            blocks = (blocks.single() as AiMdBlock.Quote).blocks
            depth++
        }
        assertEquals(AiMarkdown.MAX_NESTING, depth)
        assertEquals(listOf(para(text("deepest words"))), blocks)

        val deepList = (0 until AiMarkdown.MAX_NESTING + 4).joinToString("\n") { "  ".repeat(it) + "- level $it" }
        val visible = AiMarkdown.visibleText(AiMarkdown.parse(deepList))
        for (level in 0 until AiMarkdown.MAX_NESTING + 4) assertTrue("level $level kept", visible.contains("level $level"))
    }

    // ---- code ------------------------------------------------------------------

    @Test
    fun `fences with backticks or tildes, an info string, and a longer closing fence`() {
        assertEquals(
            listOf(AiMdBlock.Code("kotlin", "val x = 1\n  indented", true)),
            AiMarkdown.parse("```kotlin title=\"x\"\nval x = 1\n  indented\n```")
        )
        assertEquals(listOf(AiMdBlock.Code("", "a\n```\nb", true)), AiMarkdown.parse("~~~~\na\n```\nb\n~~~~~"))
        // Content is literal: no inline parsing inside a fence.
        assertEquals(listOf(AiMdBlock.Code("md", "**not bold** <b>x</b>", true)), AiMarkdown.parse("```md\n**not bold** <b>x</b>\n```"))
    }

    @Test
    fun `an unclosed fence is code to the end while streaming, and closing it keeps the same text`() {
        val streaming = AiMarkdown.parse("Here:\n```python\nprint(1)\nfor i in x:\n")
        assertEquals(listOf(para(text("Here:")), AiMdBlock.Code("python", "print(1)\nfor i in x:", false)), streaming)
        val closed = AiMarkdown.parse("Here:\n```python\nprint(1)\nfor i in x:\n```")
        assertEquals((streaming[1] as AiMdBlock.Code).text, (closed[1] as AiMdBlock.Code).text)
        assertTrue((closed[1] as AiMdBlock.Code).closed)
        // A fence just opened is already code, never raw backticks.
        assertEquals(listOf(AiMdBlock.Code("js", "", false)), AiMarkdown.parse("```js"))
    }

    // ---- tables ----------------------------------------------------------------

    @Test
    fun `pipe tables parse cells inline and accept alignment markers`() {
        assertEquals(
            listOf(
                AiMdBlock.Table(
                    listOf(cell("Name"), cell("Size")),
                    listOf(
                        listOf(AiMdCell(listOf(AiMdInline.Code("a.kt"))), cell("12")),
                        listOf(AiMdCell(listOf(AiMdInline.Strong(listOf(text("b"))))), cell("3"))
                    )
                )
            ),
            AiMarkdown.parse("| Name | Size |\n|:-----|-----:|\n| `a.kt` | 12 |\n| **b** | 3 |")
        )
    }

    @Test
    fun `ragged table rows are padded and extra cells are kept`() {
        assertEquals(
            listOf(
                AiMdBlock.Table(
                    listOf(cell("a"), cell("b")),
                    listOf(listOf(cell("1"), cell("")), listOf(cell("1"), cell("2"), cell("3")))
                )
            ),
            AiMarkdown.parse("a | b\n--|--\n1 |\n1 | 2 | 3")
        )
        // An escaped pipe stays inside its cell.
        assertEquals(listOf(cell("x|y")), (AiMarkdown.parse("| x\\|y |\n|---|")[0] as AiMdBlock.Table).header)
    }

    // ---- inline ----------------------------------------------------------------

    @Test
    fun `code, strong, emphasis and strike`() {
        assertEquals(
            listOf(
                AiMdInline.Code("x*y"), text(" "),
                AiMdInline.Strong(listOf(text("bold"))), text(" "),
                AiMdInline.Emphasis(listOf(text("it"))), text(" "),
                AiMdInline.Strong(listOf(text("b 2"))), text(" "),
                AiMdInline.Emphasis(listOf(text("i2"))), text(" "),
                AiMdInline.Strike(listOf(text("gone")))
            ),
            AiMarkdown.inlines("`x*y` **bold** *it* __b 2__ _i2_ ~~gone~~")
        )
        assertEquals(
            listOf(AiMdInline.Emphasis(listOf(text("a "), AiMdInline.Strong(listOf(text("b"))), text(" c")))),
            AiMarkdown.inlines("*a **b** c*")
        )
        assertEquals(listOf(AiMdInline.Code("a ` b")), AiMarkdown.inlines("`` a ` b ``"))
    }

    @Test
    fun `an intraword underscore is not emphasis, and dunder identifiers stay text`() {
        assertEquals(listOf(text("call snake_case_name now")), AiMarkdown.inlines("call snake_case_name now"))
        assertEquals(listOf(text("Python runs __init__ first")), AiMarkdown.inlines("Python runs __init__ first"))
        assertEquals(listOf(text("obj.__init__() and self.__dict__")), AiMarkdown.inlines("obj.__init__() and self.__dict__"))
        assertEquals(listOf(AiMdInline.Strong(listOf(text("two words")))), AiMarkdown.inlines("__two words__"))
    }

    @Test
    fun `links, angle autolinks and bare urls with trailing punctuation`() {
        assertEquals(
            listOf(AiMdInline.Link(listOf(text("docs")), "https://developer.android.com/x?y=1#z")),
            AiMarkdown.inlines("[docs](https://developer.android.com/x?y=1#z \"title is ignored\")")
        )
        assertEquals(
            listOf(text("see "), AiMdInline.Link(listOf(text("https://a.example/p")), "https://a.example/p"), text(".")),
            AiMarkdown.inlines("see <https://a.example/p>.")
        )
        assertEquals(
            listOf(
                text("Go to "), AiMdInline.Link(listOf(text("https://x.example/a_(b)")), "https://x.example/a_(b)"),
                text(", or ("), AiMdInline.Link(listOf(text("https://y.example")), "https://y.example"), text(")!")
            ),
            AiMarkdown.inlines("Go to https://x.example/a_(b), or (https://y.example)!")
        )
        assertEquals(listOf(text("bare www.example.com stays text")), AiMarkdown.inlines("bare www.example.com stays text"))
        // A link is never invisible: empty link text shows its target.
        assertEquals(listOf(AiMdInline.Link(listOf(text("https://z.example")), "https://z.example")), AiMarkdown.inlines("[](https://z.example)"))
    }

    @Test
    fun `images become alt text only and their target is discarded`() {
        val parsed = AiMarkdown.inlines("Look: ![a cat](https://evil.example/track.png?id=1)")
        assertEquals(listOf(text("Look: "), AiMdInline.ImageAlt("a cat")), parsed)
        assertFalse(parsed.toString().contains("evil.example"))
    }

    @Test
    fun `raw HTML is text, exactly as written`() {
        val html = "<script>alert(1)</script> <img src=x onerror=alert(2)> <iframe src=\"https://e.example\"></iframe>"
        assertEquals(listOf(para(text(html))), AiMarkdown.parse(html))
    }

    @Test
    fun `the hostile corpus parses to text and link targets only`() {
        val hostile = listOf(
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<iframe src=javascript:alert(1)></iframe>",
            "[click](javascript:alert(1))",
            "[c2](JavaScript:alert(1))",
            "[d](data:text/html;base64,PHNjcmlwdD4=)",
            "[h](http://insecure.example)",
            "<javascript:alert(3)>"
        ).joinToString("\n\n")
        val nodes = allInlines(AiMarkdown.parse(hostile))
        val links = nodes.filterIsInstance<AiMdInline.Link>()
        assertEquals(
            listOf("javascript:alert(1)", "JavaScript:alert(1)", "data:text/html;base64,PHNjcmlwdD4=", "http://insecure.example", "javascript:alert(3)"),
            links.map { it.target }
        )
        // Every other node is plain text (or a line break): no element type exists to smuggle HTML through.
        assertTrue(nodes.all { it is AiMdInline.Text || it is AiMdInline.Link || it is AiMdInline.LineBreak })
        val visible = AiMarkdown.visibleText(AiMarkdown.parse(hostile))
        assertTrue(visible.contains("<script>alert(1)</script>"))
        assertTrue(visible.contains("<img src=x onerror=alert(1)>"))
    }

    @Test
    fun `backslash escapes and unmatched delimiters are literal`() {
        assertEquals(listOf(text("*not em* and [x] and `tick`")), AiMarkdown.inlines("\\*not em\\* and \\[x\\] and \\`tick\\`"))
        // Mid-stream: the asterisks show until the closing pair arrives.
        assertEquals(listOf(text("**bold te")), AiMarkdown.inlines("**bold te"))
        assertEquals(listOf(text("2 * 3 * 4")), AiMarkdown.inlines("2 * 3 * 4"))
        assertEquals(listOf(text("[not a link] (x)")), AiMarkdown.inlines("[not a link] (x)"))
    }

    @Test
    fun `visibleText keeps every visible character and drops only markers`() {
        val source = "# Plan\n\nUse **bold**, *it*, `code` and [docs](https://d.example).\n\n" +
            "- [x] first\n- second\n\n> quoted\n\n| k | v |\n|---|---|\n| a | b |\n\n```sh\necho hi\n```"
        val visible = AiMarkdown.visibleText(AiMarkdown.parse(source))
        assertEquals(
            "Plan\nUse bold, it, code and docs.\nfirst\nsecond\nquoted\nk\tv\na\tb\necho hi",
            visible
        )
    }

    // ---- pathological inputs: linear by construction ---------------------------------

    private fun assertPrompt(label: String, input: String) {
        val start = System.nanoTime()
        val blocks = AiMarkdown.parse(input)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("$label took $ms ms", ms < 2_000)
        AiMarkdown.visibleText(blocks) // and the oracle walks it without trouble
    }

    @Test
    fun `fifty thousand asterisks parse promptly`() {
        assertPrompt("stars", "*".repeat(50_000))
        assertPrompt("stars in prose", "a" + "*a".repeat(25_000))
        assertPrompt("star runs", "x " + "**".repeat(25_000) + " y")
    }

    @Test
    fun `twenty thousand open brackets parse promptly`() {
        assertPrompt("brackets", "[".repeat(20_000))
        assertPrompt("bracket links", "[a](".repeat(5_000))
    }

    @Test
    fun `ten thousand quote markers parse promptly`() {
        assertPrompt("quotes", ">".repeat(10_000))
        assertPrompt("quote lines", (1..10_000).joinToString("\n") { "> line $it" })
    }

    @Test
    fun `ten thousand backticks parse promptly`() {
        assertPrompt("ticks", "`".repeat(10_000))
        assertPrompt("ticks in prose", "a " + "` ``".repeat(2_500))
    }

    // ---- constants and purity ----------------------------------------------------

    @Test
    fun `the streaming re-parse interval sits within its bounds`() {
        assertTrue(AiMarkdown.STREAM_REPARSE_MS in 50L..500L)
        assertEquals(8, AiMarkdown.MAX_NESTING)
        assertEquals(1_000, AiMarkdown.MAX_LINK_TEXT_CHARS)
    }

    @Test
    fun `the model has no Android or Compose import and no regex`() {
        val source = File(RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai"), "AiMarkdown.kt").readText()
        assertFalse(source.contains("import android"))
        assertFalse(source.contains("import androidx"))
        val code = RepoFiles.codeOnly(source)
        assertFalse(code.contains("Regex("))
        assertFalse(code.contains("toRegex("))
    }
}
