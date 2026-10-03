package com.codeci.ide.ui.ai

/*
 * Phase 88 (Level 11, 88.1) — the pure Markdown model for AI answers.
 *
 * Model output is untrusted data (S3). This file turns an answer into a small
 * block/inline MODEL that the Compose edge (`AiMarkdownView.kt`, 88.3) draws as
 * text. Nothing here interprets HTML, fetches anything or decides whether a
 * link may open: raw HTML is just characters, images keep only their alt text,
 * and every link target is judged later by `AiLinkPolicy` (88.2).
 *
 * Linear by construction: one pass per line for blocks, one left-to-right scan
 * per paragraph for inlines, no regex at all. Open emphasis is bounded by
 * MAX_NESTING, a `[` looks ahead at most MAX_LINK_TEXT_CHARS, a link target at
 * most MAX_TARGET_CHARS, and code spans pair through a precomputed run table.
 * That keeps pathological answers (thousands of `*`, `[`, `>` or backticks)
 * from turning a parse into seconds of work.
 *
 * Deliberately NOT `ui/utils/MarkdownPreview.kt`: that renders a local file to
 * HTML with a file-preview URL allowlist; this renders model output to a model
 * with no URL allowlist at all (the policy is separate and stricter).
 */

/** One block of an AI answer. */
sealed interface AiMdBlock {
    /** `#`…`######` or a setext underline; [level] is 1..6. */
    data class Heading(val level: Int, val inlines: List<AiMdInline>) : AiMdBlock

    data class Paragraph(val inlines: List<AiMdInline>) : AiMdBlock

    /** A bullet or ordered list; [start] is the first number of an ordered list (1 for bullets). */
    data class Bullets(val ordered: Boolean, val start: Int, val items: List<AiMdItem>) : AiMdBlock

    data class Quote(val blocks: List<AiMdBlock>) : AiMdBlock

    /**
     * A fenced code block. [language] is a display-only label (the info string's
     * first word); [text] is literal and never inline-parsed. [closed] is false
     * for a fence that has not been closed yet: it runs to the end of the text
     * (the CommonMark rule), so a half-streamed block is drawn as code.
     */
    data class Code(val language: String, val text: String, val closed: Boolean) : AiMdBlock

    /** A GFM pipe table. Short rows are padded; extra cells are kept, never dropped. */
    data class Table(val header: List<AiMdCell>, val rows: List<List<AiMdCell>>) : AiMdBlock

    data object Rule : AiMdBlock
}

/** One list item. [checked] is null when the item is not a task item. */
data class AiMdItem(val checked: Boolean?, val blocks: List<AiMdBlock>)

data class AiMdCell(val inlines: List<AiMdInline>)

/** Inline spans inside a heading, paragraph or table cell. There is no HTML element type. */
sealed interface AiMdInline {
    data class Text(val text: String) : AiMdInline
    data class Code(val text: String) : AiMdInline
    data class Strong(val children: List<AiMdInline>) : AiMdInline
    data class Emphasis(val children: List<AiMdInline>) : AiMdInline
    data class Strike(val children: List<AiMdInline>) : AiMdInline

    /** A link as the model wrote it. Whether it can ever open is `AiLinkPolicy`'s call (88.2). */
    data class Link(val children: List<AiMdInline>, val target: String) : AiMdInline

    /** An image reduced to its alt text. The target is discarded: nothing can fetch it. */
    data class ImageAlt(val alt: String) : AiMdInline

    data object LineBreak : AiMdInline
}

object AiMarkdown {
    /** Lists and quotes nest at most this deep; deeper content flattens into text, never drops. */
    const val MAX_NESTING = 8

    /** A `[…](…)` whose text is longer than this stays plain text. */
    const val MAX_LINK_TEXT_CHARS = 1_000

    /** 88.3's streaming throttle: at most one re-parse per this many milliseconds. */
    const val STREAM_REPARSE_MS = 150L

    /** A link target or autolink longer than this stays plain text (the link policy caps URLs at 2 048). */
    const val MAX_TARGET_CHARS = 4_096

    fun parse(text: String): List<AiMdBlock> {
        if (text.isEmpty()) return emptyList()
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        var lines = normalized.split('\n')
        // A final line ending ends the last line; it does not open an empty one.
        if (normalized.endsWith('\n')) lines = lines.subList(0, lines.size - 1)
        return MdBlockParser(0).parse(lines)
    }

    fun inlines(text: String): List<AiMdInline> = MdInlineParser(text, 0, allowLinks = true).parse()

    /** Every character the user would see, markers removed. Test oracle: nothing is lost. */
    fun visibleText(blocks: List<AiMdBlock>): String = buildString { appendBlocks(blocks) }.trimEnd('\n')

    /** The visible text of inline spans (link text, never a link target). */
    fun inlineText(inlines: List<AiMdInline>): String = buildString { appendInlines(inlines) }

    private fun StringBuilder.appendBlocks(blocks: List<AiMdBlock>) {
        for (b in blocks) {
            when (b) {
                is AiMdBlock.Heading -> { appendInlines(b.inlines); append('\n') }
                is AiMdBlock.Paragraph -> { appendInlines(b.inlines); append('\n') }
                is AiMdBlock.Bullets -> b.items.forEach { appendBlocks(it.blocks) }
                is AiMdBlock.Quote -> appendBlocks(b.blocks)
                is AiMdBlock.Code -> { append(b.text); append('\n') }
                is AiMdBlock.Table -> {
                    (listOf(b.header) + b.rows).forEach { row ->
                        row.forEachIndexed { i, cell ->
                            if (i > 0) append('\t')
                            appendInlines(cell.inlines)
                        }
                        append('\n')
                    }
                }
                AiMdBlock.Rule -> Unit
            }
        }
    }

    private fun StringBuilder.appendInlines(inlines: List<AiMdInline>) {
        for (i in inlines) {
            when (i) {
                is AiMdInline.Text -> append(i.text)
                is AiMdInline.Code -> append(i.text)
                is AiMdInline.Strong -> appendInlines(i.children)
                is AiMdInline.Emphasis -> appendInlines(i.children)
                is AiMdInline.Strike -> appendInlines(i.children)
                is AiMdInline.Link -> appendInlines(i.children)
                is AiMdInline.ImageAlt -> append(i.alt)
                AiMdInline.LineBreak -> append('\n')
            }
        }
    }
}

// ---- block level -----------------------------------------------------------

private class MdFence(val char: Char, val length: Int, val indent: Int, val info: String)

private class MdMarker(
    val ordered: Boolean,
    val start: Int,
    val indent: Int,
    val contentColumn: Int,
    val content: String
)

/** Column of the first non-blank character (a tab advances to the next multiple of 4). */
private fun mdIndent(line: String): Int {
    var col = 0
    for (ch in line) {
        when (ch) {
            ' ' -> col++
            '\t' -> col += 4 - col % 4
            else -> return col
        }
    }
    return col
}

/** Removes up to [cols] columns of leading blanks. */
private fun mdDeindent(line: String, cols: Int): String {
    var col = 0
    var i = 0
    while (i < line.length && col < cols) {
        val ch = line[i]
        if (ch == ' ') {
            col++
            i++
        } else if (ch == '\t') {
            val w = 4 - col % 4
            if (col + w > cols) return " ".repeat(col + w - cols) + line.substring(i + 1)
            col += w
            i++
        } else {
            break
        }
    }
    return line.substring(i)
}

private fun mdFenceOpen(line: String): MdFence? {
    val indent = mdIndent(line)
    if (indent > 3) return null
    val t = line.trimStart()
    val c = t.firstOrNull() ?: return null
    if (c != '`' && c != '~') return null
    var k = 0
    while (k < t.length && t[k] == c) k++
    if (k < 3) return null
    val info = t.substring(k).trim()
    if (c == '`' && info.contains('`')) return null
    return MdFence(c, k, indent, info)
}

private fun mdIsFenceClose(line: String, fence: MdFence): Boolean {
    if (mdIndent(line) > 3) return false
    val t = line.trim()
    return t.length >= fence.length && t.all { it == fence.char }
}

/** `(level, content)` of an ATX heading, without inline parsing; null when the line is not one. */
private fun mdAtx(line: String): Pair<Int, String>? {
    if (mdIndent(line) > 3) return null
    val t = line.trimStart()
    var k = 0
    while (k < t.length && t[k] == '#') k++
    if (k == 0 || k > 6) return null
    if (k < t.length && t[k] != ' ' && t[k] != '\t') return null // `#hashtag` stays text
    var content = t.substring(k).trim()
    if (content.endsWith('#')) {
        var e = content.length
        while (e > 0 && content[e - 1] == '#') e--
        if (e == 0) content = "" else if (content[e - 1] == ' ' || content[e - 1] == '\t') content = content.substring(0, e).trimEnd()
    }
    if (content.isEmpty()) return null // an empty heading stays visible as text
    return k to content
}

/** 1 for `===`, 2 for `---` (three or more, no inner spaces), else 0. */
private fun mdSetextLevel(line: String): Int {
    if (mdIndent(line) > 3) return 0
    val t = line.trim()
    if (t.length < 3) return 0
    return when {
        t.all { it == '=' } -> 1
        t.all { it == '-' } -> 2
        else -> 0
    }
}

private fun mdIsRule(line: String): Boolean {
    if (mdIndent(line) > 3) return false
    val t = line.trim()
    val c = t.firstOrNull() ?: return false
    if (c != '-' && c != '*' && c != '_') return false
    var count = 0
    for (ch in t) {
        when (ch) {
            c -> count++
            ' ', '\t' -> Unit
            else -> return false
        }
    }
    return count >= 3
}

private fun mdIsQuote(line: String): Boolean = mdIndent(line) <= 3 && line.trimStart().startsWith('>')

private fun mdStripQuote(line: String): String {
    val t = line.trimStart()
    var rest = t.substring(1)
    if (rest.startsWith(' ')) rest = rest.substring(1) else if (rest.startsWith('\t')) rest = "  " + rest.substring(1)
    return rest
}

/** Beyond the nesting cap a quote joins the deepest level: every further `>` marker is dropped. */
private fun mdStripAllQuotes(line: String): String {
    var i = 0
    while (i < line.length && (line[i] == '>' || line[i] == ' ' || line[i] == '\t')) i++
    return line.substring(i)
}

private fun mdListMarker(line: String): MdMarker? {
    val indent = mdIndent(line)
    var p = 0
    while (p < line.length && (line[p] == ' ' || line[p] == '\t')) p++
    if (p >= line.length) return null
    val c = line[p]
    val ordered: Boolean
    var start = 1
    val markerEnd: Int
    if (c == '-' || c == '*' || c == '+') {
        ordered = false
        markerEnd = p + 1
    } else if (c in '0'..'9') {
        var k = p
        while (k < line.length && line[k] in '0'..'9') k++
        if (k - p > 9) return null
        if (k >= line.length || (line[k] != '.' && line[k] != ')')) return null
        ordered = true
        start = line.substring(p, k).toInt()
        markerEnd = k + 1
    } else {
        return null
    }
    val markerWidth = markerEnd - p
    if (markerEnd >= line.length) {
        return MdMarker(ordered, start, indent, indent + markerWidth + 1, "")
    }
    if (line[markerEnd] != ' ' && line[markerEnd] != '\t') return null
    var spaces = 0
    var q = markerEnd
    while (q < line.length && line[q] == ' ' && spaces < 5) { spaces++; q++ }
    if (q < line.length && line[q] == '\t') { spaces = 1; q++ }
    if (spaces >= 5) { spaces = 1; q = markerEnd + 1 }
    val content = line.substring(q)
    if (content.isBlank()) return MdMarker(ordered, start, indent, indent + markerWidth + 1, "")
    return MdMarker(ordered, start, indent, indent + markerWidth + spaces, content)
}

private fun mdTask(content: String): Pair<Boolean?, String> {
    if (content.length >= 3 && content[0] == '[' && content[2] == ']' &&
        (content.length == 3 || content[3] == ' ' || content[3] == '\t')
    ) {
        when (content[1]) {
            ' ' -> return false to content.substring(3).trimStart()
            'x', 'X' -> return true to content.substring(3).trimStart()
        }
    }
    return null to content
}

private fun mdHasUnescapedPipe(s: String): Boolean {
    var i = 0
    while (i < s.length) {
        when (s[i]) {
            '\\' -> i += 2
            '|' -> return true
            else -> i++
        }
    }
    return false
}

/** Cells of a pipe-table row, or null when the line has no unescaped pipe. */
private fun mdSplitRow(line: String): List<String>? {
    var t = line.trim()
    if (!mdHasUnescapedPipe(t)) return null
    if (t.startsWith('|')) t = t.substring(1)
    if (t.endsWith('|') && !t.endsWith("\\|")) t = t.substring(0, t.length - 1)
    val cells = ArrayList<String>()
    val cell = StringBuilder()
    var i = 0
    while (i < t.length) {
        val ch = t[i]
        if (ch == '\\' && i + 1 < t.length && t[i + 1] == '|') {
            cell.append('|')
            i += 2
        } else if (ch == '|') {
            cells.add(cell.toString().trim())
            cell.setLength(0)
            i++
        } else {
            cell.append(ch)
            i++
        }
    }
    cells.add(cell.toString().trim())
    return cells
}

private fun mdIsSeparatorCell(cell: String): Boolean {
    val core = cell.trim().removePrefix(":").removeSuffix(":")
    return core.isNotEmpty() && core.all { it == '-' }
}

private class MdBlockParser(private val depth: Int) {

    fun parse(lines: List<String>): List<AiMdBlock> {
        val out = ArrayList<AiMdBlock>()
        val para = ArrayList<String>()
        fun flush() {
            if (para.isNotEmpty()) {
                out.add(AiMdBlock.Paragraph(paragraph(para)))
                para.clear()
            }
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                flush()
                i++
                continue
            }
            val fence = mdFenceOpen(line)
            if (fence != null) {
                flush()
                i = readFence(lines, i, fence, out)
                continue
            }
            val atx = mdAtx(line)
            if (atx != null) {
                flush()
                out.add(AiMdBlock.Heading(atx.first, AiMarkdown.inlines(atx.second)))
                i++
                continue
            }
            val setext = if (para.isNotEmpty()) mdSetextLevel(line) else 0
            if (setext > 0) {
                // The underline heads the line directly above it; earlier lines stay a paragraph.
                val last = para.removeAt(para.size - 1)
                flush()
                out.add(AiMdBlock.Heading(setext, AiMarkdown.inlines(last.trim())))
                i++
                continue
            }
            if (mdIsRule(line)) {
                flush()
                out.add(AiMdBlock.Rule)
                i++
                continue
            }
            if (mdIsQuote(line)) {
                if (depth < AiMarkdown.MAX_NESTING) {
                    flush()
                    i = readQuote(lines, i, out)
                } else {
                    val flat = mdStripAllQuotes(line)
                    if (flat.isBlank()) flush() else para.add(flat)
                    i++
                }
                continue
            }
            if (i + 1 < lines.size) {
                val table = readTable(lines, i)
                if (table != null) {
                    flush()
                    out.add(table.first)
                    i = table.second
                    continue
                }
            }
            val marker = if (depth < AiMarkdown.MAX_NESTING) mdListMarker(line) else null
            if (marker != null && (para.isEmpty() || (marker.content.isNotEmpty() && (!marker.ordered || marker.start == 1)))) {
                flush()
                i = readList(lines, i, marker, out)
                continue
            }
            para.add(line)
            i++
        }
        flush()
        return out
    }

    /** Lines of one paragraph: every line ending is a [AiMdInline.LineBreak] (phone answers are line-shaped). */
    private fun paragraph(lines: List<String>): List<AiMdInline> {
        val sb = StringBuilder()
        lines.forEachIndexed { idx, raw ->
            var l = raw.trimStart()
            if (idx < lines.lastIndex) {
                l = l.trimEnd()
                var backslashes = 0
                while (backslashes < l.length && l[l.length - 1 - backslashes] == '\\') backslashes++
                if (backslashes % 2 == 1) l = l.substring(0, l.length - 1) // hard-break backslash
                sb.append(l).append('\n')
            } else {
                sb.append(l.trimEnd())
            }
        }
        return AiMarkdown.inlines(sb.toString())
    }

    private fun readFence(lines: List<String>, start: Int, fence: MdFence, out: MutableList<AiMdBlock>): Int {
        val content = ArrayList<String>()
        var i = start + 1
        var closed = false
        while (i < lines.size) {
            val l = lines[i]
            i++
            if (mdIsFenceClose(l, fence)) {
                closed = true
                break
            }
            content.add(mdDeindent(l, fence.indent))
        }
        val language = fence.info.split(' ', '\t').firstOrNull { it.isNotEmpty() }.orEmpty()
        out.add(AiMdBlock.Code(language, content.joinToString("\n"), closed))
        return i
    }

    private fun readQuote(lines: List<String>, start: Int, out: MutableList<AiMdBlock>): Int {
        val inner = ArrayList<String>()
        var i = start
        while (i < lines.size && mdIsQuote(lines[i])) {
            inner.add(mdStripQuote(lines[i]))
            i++
        }
        out.add(AiMdBlock.Quote(MdBlockParser(depth + 1).parse(inner)))
        return i
    }

    private fun readTable(lines: List<String>, start: Int): Pair<AiMdBlock.Table, Int>? {
        val header = mdSplitRow(lines[start]) ?: return null
        val separator = mdSplitRow(lines[start + 1]) ?: return null
        if (separator.size != header.size || !separator.all(::mdIsSeparatorCell)) return null
        val rows = ArrayList<List<AiMdCell>>()
        var j = start + 2
        while (j < lines.size) {
            val l = lines[j]
            if (l.isBlank() || mdFenceOpen(l) != null || mdAtx(l) != null || mdIsQuote(l)) break
            val cells = mdSplitRow(l) ?: break
            val padded = if (cells.size < header.size) cells + List(header.size - cells.size) { "" } else cells
            rows.add(padded.map { AiMdCell(AiMarkdown.inlines(it)) })
            j++
        }
        return AiMdBlock.Table(header.map { AiMdCell(AiMarkdown.inlines(it)) }, rows) to j
    }

    private fun startsBlock(line: String): Boolean =
        mdFenceOpen(line) != null || mdAtx(line) != null || mdIsRule(line) || mdIsQuote(line) || mdListMarker(line) != null

    /**
     * A list: siblings sit within one column of the item's marker, continuation and
     * nested content sit two or more columns in (tolerant of the 2-space nesting
     * models write under `1.`), and a plain line right after item text is a lazy
     * continuation of that item, as in CommonMark.
     */
    private fun readList(lines: List<String>, start: Int, first: MdMarker, out: MutableList<AiMdBlock>): Int {
        val items = ArrayList<AiMdItem>()
        var current = first
        var task = mdTask(first.content)
        var body = arrayListOf(task.second)
        var lastWasText = task.second.isNotBlank()
        fun finish() {
            while (body.isNotEmpty() && body.last().isBlank()) body.removeAt(body.size - 1)
            items.add(AiMdItem(task.first, MdBlockParser(depth + 1).parse(body)))
        }
        var i = start + 1
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                var j = i
                while (j < lines.size && lines[j].isBlank()) j++
                if (j >= lines.size) break
                val next = lines[j]
                val nextIndent = mdIndent(next)
                if (nextIndent >= current.indent + 2) {
                    repeat(j - i) { body.add("") }
                    i = j
                    lastWasText = false
                    continue
                }
                val m = mdListMarker(next)
                if (m != null && m.ordered == first.ordered && !mdIsRule(next)) {
                    i = j // a sibling after a blank line; handled on the next pass
                    continue
                }
                break
            }
            val indent = mdIndent(line)
            val m = mdListMarker(line)
            if (m != null && indent < current.indent + 2) {
                if (m.ordered != first.ordered || mdIsRule(line)) break
                finish()
                current = m
                task = mdTask(m.content)
                body = arrayListOf(task.second)
                lastWasText = task.second.isNotBlank()
                i++
                continue
            }
            if (indent >= current.indent + 2) {
                body.add(mdDeindent(line, minOf(indent, current.contentColumn)))
                lastWasText = true
                i++
                continue
            }
            if (lastWasText && !startsBlock(line)) {
                body.add(line.trimStart())
                i++
                continue
            }
            break
        }
        finish()
        out.add(AiMdBlock.Bullets(first.ordered, if (first.ordered) first.start else 1, items))
        return i
    }
}

// ---- inline level ----------------------------------------------------------

private fun mdIsAsciiPunct(c: Char): Boolean = c.code in 33..126 && !c.isLetterOrDigit()

private fun mdIsPunct(c: Char): Boolean {
    if (c.code < 128) return mdIsAsciiPunct(c)
    return when (Character.getType(c)) {
        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(), Character.MATH_SYMBOL.toInt(),
        Character.CURRENCY_SYMBOL.toInt(), Character.MODIFIER_SYMBOL.toInt(),
        Character.OTHER_SYMBOL.toInt() -> true
        else -> false
    }
}

private fun mdIsAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

private class MdInlineParser(private val s: String, private val depth: Int, private val allowLinks: Boolean) {
    private val n = s.length

    private class Frame(val char: Char, var count: Int, val runLength: Int, val canOpen: Boolean, val canClose: Boolean) {
        var children = ArrayList<AiMdInline>()
    }

    private val stack = arrayListOf(Frame(' ', 0, 0, canOpen = false, canClose = false))
    private val text = StringBuilder()

    // Backtick runs, so a code span finds its closer by binary search, not a rescan.
    private val runStart: IntArray
    private val runLen: IntArray
    private val runsByLength: Map<Int, IntArray>

    init {
        val starts = ArrayList<Int>()
        val lens = ArrayList<Int>()
        var i = 0
        while (i < n) {
            if (s[i] == '`') {
                val b = i
                while (i < n && s[i] == '`') i++
                starts.add(b)
                lens.add(i - b)
            } else {
                i++
            }
        }
        runStart = starts.toIntArray()
        runLen = lens.toIntArray()
        val grouped = HashMap<Int, ArrayList<Int>>()
        for (k in runStart.indices) grouped.getOrPut(runLen[k]) { ArrayList() }.add(k)
        runsByLength = grouped.mapValues { it.value.toIntArray() }
    }

    /** The first full backtick run of exactly [length] starting after [pos], or -1. */
    private fun closingRun(length: Int, pos: Int): Int {
        val arr = runsByLength[length] ?: return -1
        var lo = 0
        var hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (runStart[arr[mid]] <= pos) lo = mid + 1 else hi = mid
        }
        return if (lo < arr.size) arr[lo] else -1
    }

    private fun top(): Frame = stack[stack.size - 1]

    private fun flushText() {
        if (text.isNotEmpty()) {
            top().children.add(AiMdInline.Text(text.toString()))
            text.setLength(0)
        }
    }

    private fun add(node: AiMdInline) {
        flushText()
        top().children.add(node)
    }

    fun parse(): List<AiMdInline> {
        var i = 0
        while (i < n) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < n && mdIsAsciiPunct(s[i + 1]) -> {
                    text.append(s[i + 1])
                    i += 2
                }
                c == '\n' -> {
                    add(AiMdInline.LineBreak)
                    i++
                }
                c == '`' -> {
                    var e = i
                    while (e < n && s[e] == '`') e++
                    val length = e - i
                    val closer = closingRun(length, e - 1)
                    if (closer >= 0) {
                        add(AiMdInline.Code(codeSpan(s.substring(e, runStart[closer]))))
                        i = runStart[closer] + runLen[closer]
                    } else {
                        text.append(s, i, e)
                        i = e
                    }
                }
                c == '!' && i + 1 < n && s[i + 1] == '[' && depth < AiMarkdown.MAX_NESTING -> {
                    val image = bracketed(i + 1, image = true)
                    if (image != null) {
                        add(image.first)
                        i = image.second
                    } else {
                        text.append('!')
                        i++
                    }
                }
                c == '[' && allowLinks && depth < AiMarkdown.MAX_NESTING -> {
                    val link = bracketed(i, image = false)
                    if (link != null) {
                        add(link.first)
                        i = link.second
                    } else {
                        text.append('[')
                        i++
                    }
                }
                c == '<' -> {
                    val auto = if (allowLinks) autolink(i) else null
                    if (auto != null) {
                        add(auto.first)
                        i = auto.second
                    } else {
                        text.append('<')
                        i++
                    }
                }
                (c == 'h' || c == 'H') && allowLinks -> {
                    val bare = bareUrl(i)
                    if (bare != null) {
                        add(bare.first)
                        i = bare.second
                    } else {
                        text.append(c)
                        i++
                    }
                }
                c == '*' || c == '_' || c == '~' -> i = delimiterRun(i)
                else -> {
                    text.append(c)
                    i++
                }
            }
        }
        flushText()
        while (stack.size > 1) flattenTop()
        return merge(stack[0].children)
    }

    /** CommonMark: line endings become spaces; one surrounding space is stripped when both ends have one. */
    private fun codeSpan(raw: String): String {
        val t = raw.replace('\n', ' ')
        return if (t.length >= 2 && t.startsWith(' ') && t.endsWith(' ') && t.isNotBlank()) t.substring(1, t.length - 1) else t
    }

    private fun delimiterRun(start: Int): Int {
        val c = s[start]
        var e = start
        while (e < n && s[e] == c) e++
        val length = e - start
        if (c == '~' && length != 2) {
            text.append(s, start, e)
            return e
        }
        val prev = if (start == 0) ' ' else s[start - 1]
        val next = if (e >= n) ' ' else s[e]
        val prevWs = prev.isWhitespace()
        val nextWs = next.isWhitespace()
        val prevPunct = mdIsPunct(prev)
        val nextPunct = mdIsPunct(next)
        val left = !nextWs && (!nextPunct || prevWs || prevPunct)
        val right = !prevWs && (!prevPunct || nextWs || nextPunct)
        val canOpen: Boolean
        val canClose: Boolean
        if (c == '_') {
            // An intraword `_` is neither: snake_case_name stays text.
            canOpen = left && (!right || prevPunct)
            canClose = right && (!left || nextPunct)
        } else {
            canOpen = left
            canClose = right
        }
        flushText()
        var remaining = length
        if (canClose) {
            while (remaining > 0) {
                var k = stack.size - 1
                var found = -1
                while (k >= 1) {
                    val f = stack[k]
                    if (f.char == c && compatible(f, length, canOpen, canClose)) {
                        found = k
                        break
                    }
                    k--
                }
                if (found < 0) break
                while (stack.size - 1 > found) flattenTop()
                val f = top()
                val use = if (c == '~') 2 else if (f.count >= 2 && remaining >= 2) 2 else 1
                val children = merge(f.children)
                val node: AiMdInline = when {
                    c == '~' -> AiMdInline.Strike(children)
                    use == 2 && c == '_' && f.runLength == 2 && length == 2 && isIdentifier(children) ->
                        // `__init__`-style identifiers stay text.
                        AiMdInline.Text("__" + (children[0] as AiMdInline.Text).text + "__")
                    use == 2 -> AiMdInline.Strong(children)
                    else -> AiMdInline.Emphasis(children)
                }
                f.count -= use
                remaining -= use
                if (f.count == 0) {
                    stack.removeAt(stack.size - 1)
                    top().children.add(node)
                } else {
                    f.children = arrayListOf(node)
                }
            }
        }
        if (remaining > 0) {
            if (canOpen && stack.size - 1 < AiMarkdown.MAX_NESTING) {
                stack.add(Frame(c, remaining, length, canOpen, canClose))
            } else {
                top().children.add(AiMdInline.Text(c.toString().repeat(remaining)))
            }
        }
        return e
    }

    /** CommonMark's "rule of 3" plus GFM's exact `~~` pairing. */
    private fun compatible(opener: Frame, closerLength: Int, closerCanOpen: Boolean, closerCanClose: Boolean): Boolean {
        if (opener.char == '~') return opener.count == 2 && closerLength == 2
        if ((closerCanOpen && closerCanClose) || (opener.canOpen && opener.canClose)) {
            val sum = opener.runLength + closerLength
            if (sum % 3 == 0 && !(opener.runLength % 3 == 0 && closerLength % 3 == 0)) return false
        }
        return true
    }

    private fun isIdentifier(children: List<AiMdInline>): Boolean {
        val only = children.singleOrNull() as? AiMdInline.Text ?: return false
        return only.text.isNotEmpty() && only.text.all { it.isLetterOrDigit() || it == '_' }
    }

    /** An unmatched opener becomes literal text in front of what it collected. */
    private fun flattenTop() {
        val f = stack.removeAt(stack.size - 1)
        val parent = top()
        parent.children.add(AiMdInline.Text(f.char.toString().repeat(f.count)))
        parent.children.addAll(f.children)
    }

    private fun merge(nodes: List<AiMdInline>): List<AiMdInline> {
        val out = ArrayList<AiMdInline>(nodes.size)
        val run = StringBuilder()
        for (node in nodes) {
            if (node is AiMdInline.Text) {
                run.append(node.text)
            } else {
                if (run.isNotEmpty()) {
                    out.add(AiMdInline.Text(run.toString()))
                    run.setLength(0)
                }
                out.add(node)
            }
        }
        if (run.isNotEmpty()) out.add(AiMdInline.Text(run.toString()))
        return out
    }

    /** `[text](target)` or, with [image], `![alt](target)` whose `[` is at [open]. */
    private fun bracketed(open: Int, image: Boolean): Pair<AiMdInline, Int>? {
        val close = matchBracket(open) ?: return null
        if (close + 1 >= n || s[close + 1] != '(') return null
        val destination = destination(close + 2) ?: return null
        val label = s.substring(open + 1, close)
        val children = MdInlineParser(label, depth + 1, allowLinks = false).parse()
        if (image) return AiMdInline.ImageAlt(AiMarkdown.inlineText(children)) to destination.second
        // A link must never be invisible: empty link text shows its target.
        val shown = if (AiMarkdown.inlineText(children).isBlank()) listOf(AiMdInline.Text(destination.first)) else children
        return AiMdInline.Link(shown, destination.first) to destination.second
    }

    private fun matchBracket(open: Int): Int? {
        val limit = minOf(n, open + 1 + AiMarkdown.MAX_LINK_TEXT_CHARS)
        var level = 1
        var i = open + 1
        while (i < limit) {
            when (s[i]) {
                '\\' -> i += 2
                '`' -> {
                    var e = i
                    while (e < n && s[e] == '`') e++
                    val closer = closingRun(e - i, e - 1)
                    i = if (closer >= 0 && runStart[closer] < limit) runStart[closer] + runLen[closer] else e
                }
                '[' -> {
                    level++
                    i++
                }
                ']' -> {
                    level--
                    if (level == 0) return i
                    i++
                }
                else -> i++
            }
        }
        return null
    }

    /** `(target "title")` starting just after `(`; returns the target and the index after `)`. */
    private fun destination(from: Int): Pair<String, Int>? {
        var i = from
        val limit = minOf(n, from + AiMarkdown.MAX_TARGET_CHARS)
        while (i < limit && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n')) i++
        val target: String
        if (i < limit && s[i] == '<') {
            val b = i + 1
            var e = b
            while (e < limit && s[e] != '>' && s[e] != '\n' && s[e] != '<') e++
            if (e >= limit || s[e] != '>') return null
            target = s.substring(b, e)
            i = e + 1
        } else {
            val b = i
            var parens = 0
            while (i < limit) {
                val c = s[i]
                if (c == '\\' && i + 1 < n && mdIsAsciiPunct(s[i + 1])) {
                    i += 2
                    continue
                }
                if (c == '(') {
                    parens++
                    if (parens > 32) return null
                } else if (c == ')') {
                    if (parens == 0) break
                    parens--
                } else if (c.isWhitespace() || c.code < 0x20) {
                    break
                }
                i++
            }
            if (i >= limit) return null
            target = unescape(s.substring(b, i))
        }
        while (i < limit && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n')) i++
        if (i < limit && (s[i] == '"' || s[i] == '\'' || s[i] == '(')) {
            val closeChar = if (s[i] == '(') ')' else s[i]
            var e = i + 1
            while (e < limit && s[e] != closeChar) e += if (s[e] == '\\') 2 else 1
            if (e >= limit) return null
            i = e + 1
            while (i < limit && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n')) i++
        }
        if (i < limit && s[i] == ')') return target to i + 1
        return null
    }

    private fun unescape(raw: String): String {
        if (!raw.contains('\\')) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '\\' && i + 1 < raw.length && mdIsAsciiPunct(raw[i + 1])) {
                sb.append(raw[i + 1])
                i += 2
            } else {
                sb.append(raw[i])
                i++
            }
        }
        return sb.toString()
    }

    /** `<scheme:…>` (CommonMark autolink). `<script>` has no scheme, so it stays text. */
    private fun autolink(open: Int): Pair<AiMdInline, Int>? {
        var i = open + 1
        val b = i
        if (i >= n || !mdIsAsciiLetter(s[i])) return null
        while (i < n && i - b < 33 && (mdIsAsciiLetter(s[i]) || s[i] in '0'..'9' || s[i] == '+' || s[i] == '.' || s[i] == '-')) i++
        val schemeLength = i - b
        if (schemeLength < 2 || schemeLength > 32 || i >= n || s[i] != ':') return null
        val limit = minOf(n, open + AiMarkdown.MAX_TARGET_CHARS)
        i++
        while (i < limit) {
            val c = s[i]
            if (c == '>') {
                val url = s.substring(open + 1, i)
                return AiMdInline.Link(listOf(AiMdInline.Text(url)), url) to i + 1
            }
            if (c == '<' || c.isWhitespace() || c.code < 0x20) return null
            i++
        }
        return null
    }

    /** A bare `https://…` or `http://…` (GFM autolink extension). A bare `www.` stays text. */
    private fun bareUrl(at: Int): Pair<AiMdInline, Int>? {
        val prefix = when {
            s.regionMatches(at, "https://", 0, 8, ignoreCase = true) -> 8
            s.regionMatches(at, "http://", 0, 7, ignoreCase = true) -> 7
            else -> return null
        }
        // GFM: only after the start, whitespace, `*`, `_`, `~` or `(` — never inside a
        // word or an HTML attribute such as src="https://…", which stays text.
        if (at > 0) {
            val before = s[at - 1]
            if (!before.isWhitespace() && before != '*' && before != '_' && before != '~' && before != '(') return null
        }
        val limit = minOf(n, at + AiMarkdown.MAX_TARGET_CHARS)
        var e = at + prefix
        while (e < limit && !s[e].isWhitespace() && s[e] != '<' && s[e].code >= 0x20) e++
        if (e >= limit && e < n && !s[e].isWhitespace()) return null // too long to be a link: stays text
        var openParen = 0
        var closeParen = 0
        var openSquare = 0
        var closeSquare = 0
        for (k in at until e) {
            when (s[k]) {
                '(' -> openParen++
                ')' -> closeParen++
                '[' -> openSquare++
                ']' -> closeSquare++
            }
        }
        while (e > at + prefix) {
            val c = s[e - 1]
            when {
                c in ".,;:!?*_~'\"" -> e--
                c == ')' && closeParen > openParen -> { closeParen--; e-- }
                c == ']' && closeSquare > openSquare -> { closeSquare--; e-- }
                else -> break
            }
        }
        if (e <= at + prefix || !s[at + prefix].isLetterOrDigit()) return null
        val url = s.substring(at, e)
        return AiMdInline.Link(listOf(AiMdInline.Text(url)), url) to e
    }
}
