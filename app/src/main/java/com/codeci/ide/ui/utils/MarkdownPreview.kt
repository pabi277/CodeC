package com.codeci.ide.ui.utils

/**
 * Phase 68.1 (completed part) — Run ▶ on a Markdown file renders Markdown.
 *
 * 68.1 opened the preview door for `.md` (`WebFileSupport.isPreviewable`),
 * but the WebView then loaded the RAW file — source text, not a preview,
 * which contradicted the owner's Q4=C decision ("Reuse Run to open MD
 * preview, like HTML"). This is the missing rendering half.
 *
 * Pure Markdown-subset → HTML so the converter is host-tested like every
 * other policy in this codebase: no new dependency, no WebView, no Android
 * import. The subset covers what READMEs and notes actually use: headings,
 * emphasis, code spans and fences, links, images, lists (nested), quotes,
 * rules, tables and paragraphs. Input is HTML-escaped FIRST, so the output
 * can never inject markup from the file; URLs are sanitized so a hostile
 * document cannot reach `javascript:` or friends. The page shell carries no
 * JavaScript at all — the preview is inert by design.
 */
object MarkdownPreview {

    /** A marker that cannot occur in real text, for lifting code spans out. */
    private const val CODE_MARKER = "\u0000CODE"

    /**
     * Render [markdown] to an HTML fragment (`<h1>`…`<p>`…`<pre>`…) with no
     * `<html>`/`<body>` wrapper — see [page] for the full document.
     */
    fun toHtml(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = StringBuilder()
        var paragraph = mutableListOf<String>()
        var i = 0

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            val sb = StringBuilder()
            for (idx in paragraph.indices) {
                if (idx > 0) {
                    // GFM soft break: two trailing spaces or a trailing
                    // backslash force a <br>; otherwise lines flow together.
                    // (The raw line is tested — trimEnd would eat the spaces.)
                    val prevRaw = paragraph[idx - 1]
                    val hardBreak = prevRaw.endsWith("  ") || prevRaw.trimEnd().endsWith("\\")
                    sb.append(if (hardBreak) "<br>" else " ")
                }
                sb.append(inline(paragraph[idx].trimEnd().removeSuffix("\\")))
            }
            out.append("<p>").append(sb).append("</p>\n")
            paragraph = mutableListOf()
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()

            when {
                line.isBlank() -> {
                    flushParagraph()
                    i++
                }

                // Fenced code block: ``` or ~~~, optional language tag.
                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    flushParagraph()
                    val fence = trimmed.take(3)
                    val lang = trimmed.removePrefix(fence).trim()
                    val body = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith(fence)) {
                        body.append(escape(lines[i])).append('\n')
                        i++
                    }
                    if (i < lines.size) i++ // consume the closing fence
                    val cls = if (lang.isEmpty()) "" else " class=\"language-${escape(lang)}\""
                    out.append("<pre><code").append(cls).append('>').append(body).append("</code></pre>\n")
                }

                // Heading: one to six hashes, then a space (or end of line).
                trimmed.startsWith("#") -> {
                    flushParagraph()
                    val level = trimmed.takeWhile { it == '#' }.length
                    if (level in 1..6 && (trimmed.length == level || trimmed[level] == ' ')) {
                        val text = trimmed.drop(level).trim()
                        out.append("<h").append(level).append('>').append(inline(text))
                            .append("</h").append(level).append(">\n")
                    } else {
                        // Kept RAW: trailing spaces carry the hard-break signal.
                        paragraph.add(line)
                    }
                    i++
                }

                // Horizontal rule: three or more of one of - * _ (spaces ok).
                isRule(trimmed) -> {
                    flushParagraph()
                    out.append("<hr>\n")
                    i++
                }

                // Blockquote: consecutive `>` lines, one nesting level.
                trimmed.startsWith(">") -> {
                    flushParagraph()
                    val quote = StringBuilder()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        val inner = lines[i].trimStart().removePrefix(">").removePrefix(" ").trimEnd()
                        if (quote.isNotEmpty()) quote.append("<br>")
                        quote.append(inline(inner))
                        i++
                    }
                    out.append("<blockquote>").append(quote).append("</blockquote>\n")
                }

                // Table: a `|` row followed by a |-|-| separator row.
                trimmed.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1]) -> {
                    flushParagraph()
                    val header = splitRow(trimmed)
                    i += 2
                    val rows = StringBuilder()
                    while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
                        val cells = splitRow(lines[i].trim())
                        rows.append("<tr>")
                            .append(cells.joinToString("") { "<td>${inline(it)}</td>" })
                            .append("</tr>\n")
                        i++
                    }
                    out.append("<table>\n<thead><tr>")
                        .append(header.joinToString("") { "<th>${inline(it)}</th>" })
                        .append("</tr></thead>\n<tbody>\n").append(rows).append("</tbody>\n</table>\n")
                }

                // Lists: unordered (- * +) or ordered (1. / 1)).
                isListItem(trimmed) -> {
                    flushParagraph()
                    val block = mutableListOf<String>()
                    while (i < lines.size && isListItem(lines[i].trimStart())) {
                        block.add(lines[i])
                        i++
                    }
                    out.append(renderList(block, 0)).append('\n')
                }

                else -> {
                    // Kept RAW: trailing spaces carry the hard-break signal.
                    paragraph.add(line)
                    i++
                }
            }
        }
        flushParagraph()
        return out.toString()
    }

    /**
     * Wrap a [toHtml] fragment in a full standalone page. [dark] picks the
     * palette so the preview matches the app's theme; [title] names the page.
     */
    fun page(bodyHtml: String, dark: Boolean, title: String): String {
        val bg = if (dark) "#16181d" else "#ffffff"
        val fg = if (dark) "#d6d9de" else "#1f2328"
        val muted = if (dark) "#8b919a" else "#656d76"
        val codeBg = if (dark) "#21242b" else "#f3f4f6"
        val border = if (dark) "#333842" else "#d8dee4"
        val accent = "#4f8cff"
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>${escape(title)}</title>
            <style>
            html { color-scheme: ${if (dark) "dark" else "light"}; }
            body { margin: 0; background: $bg; color: $fg;
                   font: 16px/1.6 -apple-system, system-ui, sans-serif; }
            article { max-width: 72ch; margin: 0 auto; padding: 16px; overflow-wrap: break-word; }
            h1, h2 { border-bottom: 1px solid $border; padding-bottom: .25em; }
            h1, h2, h3, h4, h5, h6 { font-weight: 600; line-height: 1.3; }
            a { color: $accent; }
            code { background: $codeBg; padding: .15em .35em; border-radius: 4px;
                   font-family: monospace; font-size: .9em; }
            pre { background: $codeBg; padding: 12px; border-radius: 6px;
                  overflow-x: auto; }
            pre code { background: none; padding: 0; }
            blockquote { margin: 0; padding: .1em 1em; border-left: 3px solid $border; color: $muted; }
            table { border-collapse: collapse; display: block; overflow-x: auto; max-width: 100%; }
            th, td { border: 1px solid $border; padding: 4px 10px; }
            img { max-width: 100%; }
            hr { border: none; border-top: 1px solid $border; }
            </style>
            </head>
            <body>
            <article>
            $bodyHtml
            </article>
            </body>
            </html>
        """.trimIndent()
    }

    // ---- internals ---------------------------------------------------------

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** Inline formatting on already-escaped text. */
    private fun inline(text: String): String {
        var s = escape(text)

        // 1. Lift code spans out so no other rule formats their content.
        val codes = mutableListOf<String>()
        s = Regex("`([^`]+)`").replace(s) { m ->
            codes.add("<code>${m.groupValues[1]}</code>")
            "$CODE_MARKER${codes.size - 1}$CODE_MARKER"
        }

        // 2. Images before links (same tail syntax, leading `!`).
        s = Regex("""!\[([^\]]*)\]\(([^)\s]+)\)""").replace(s) { m ->
            "<img src=\"${safeUrl(m.groupValues[2])}\" alt=\"${m.groupValues[1]}\">"
        }

        // 3. Links.
        s = Regex("""\[([^\]]+)\]\(([^)\s]+)\)""").replace(s) { m ->
            "<a href=\"${safeUrl(m.groupValues[2])}\">${m.groupValues[1]}</a>"
        }

        // 4. Bold, strikethrough, italic — order matters.
        s = Regex("""\*\*([^*]+)\*\*""").replace(s, "<strong>$1</strong>")
        s = Regex("""__([^_]+)__""").replace(s, "<strong>$1</strong>")
        s = Regex("""~~([^~]+)~~""").replace(s, "<del>$1</del>")
        s = Regex("""\*([^*\n]+)\*""").replace(s, "<em>$1</em>")
        s = Regex("""(^|[^\w])_([^_\n]+)_(?=[^\w]|$)""").replace(s, "$1<em>$2</em>")

        // 5. Restore the code spans.
        s = Regex("$CODE_MARKER(\\d+)$CODE_MARKER").replace(s) { m ->
            codes[m.groupValues[1].toInt()]
        }
        return s
    }

    /**
     * Only let URLs through that a preview has any business loading. The
     * input is already HTML-escaped, so attribute break-out is impossible;
     * this blocks `javascript:`, `vbscript:` and non-image `data:` URLs.
     */
    private fun safeUrl(url: String): String {
        val u = url.trim()
        val decoded = u.replace("&amp;", "&")
        val lower = decoded.lowercase()
        val allowed = lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("mailto:") ||
            lower.startsWith("file:") ||
            lower.startsWith("data:image/") ||
            lower.startsWith("#") ||
            // No URL scheme at all → a relative path against the baseUrl.
            !SCHEME_REGEX.containsMatchIn(decoded)
        return if (allowed) u else "#"
    }

    private val SCHEME_REGEX = Regex("^[a-z][a-z0-9+.-]*:")

    private fun isRule(trimmed: String): Boolean {
        if (trimmed.length < 3) return false
        val c = trimmed.first()
        if (c !in "-*_") return false
        return trimmed.all { it == c || it == ' ' } && trimmed.count { it == c } >= 3
    }

    private fun isListItem(trimmed: String): Boolean =
        UNORDERED_ITEM.matches(trimmed) || ORDERED_ITEM.matches(trimmed)

    private val UNORDERED_ITEM = Regex("""^[-*+]\s+\S.*""")
    private val ORDERED_ITEM = Regex("""^\d{1,9}[.)]\s+\S.*""")

    private fun isTableSeparator(line: String): Boolean {
        val t = line.trim()
        if (!t.contains('|') || !t.contains('-')) return false
        return t.all { it == '|' || it == '-' || it == ':' || it == ' ' }
    }

    /** Split one `|` row into cells; `\|` is a literal pipe, not a split. */
    private fun splitRow(row: String): List<String> {
        var r = row.trim()
        if (r.startsWith("|")) r = r.drop(1)
        if (r.endsWith("|")) r = r.dropLast(1)
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < r.length) {
            when {
                r[i] == '\\' && i + 1 < r.length && r[i + 1] == '|' -> {
                    current.append('|'); i += 2
                }
                r[i] == '|' -> {
                    cells.add(current.toString().trim()); current.clear(); i++
                }
                else -> { current.append(r[i]); i++ }
            }
        }
        cells.add(current.toString().trim())
        return cells
    }

    /**
     * Render a collected list block. Items indented deeper than [baseIndent]
     * nest inside the previous item — the common README shape, without
     * promising a full CommonMark parser.
     */
    private fun renderList(block: List<String>, baseIndent: Int): String {
        if (block.isEmpty()) return ""
        val ordered = ORDERED_ITEM.matches(block.first().trimStart())
        val tag = if (ordered) "ol" else "ul"
        val out = StringBuilder("<$tag>\n")
        var i = 0
        while (i < block.size) {
            val line = block[i]
            val indent = line.length - line.trimStart().length
            if (indent >= baseIndent + 2) {
                // A deeper item belongs to a sublist of the PREVIOUS item.
                val sub = mutableListOf<String>()
                while (i < block.size && block[i].length - block[i].trimStart().length >= baseIndent + 2) {
                    sub.add(block[i]); i++
                }
                val nested = renderList(sub, baseIndent + 2)
                val open = out.lastIndexOf("</li>")
                if (open >= 0) {
                    out.insert(open, nested)
                } else {
                    out.append("<li>").append(nested).append("</li>\n")
                }
                continue
            }
            val marker = if (ordered) Regex("""^\d{1,9}[.)]\s+""") else Regex("""^[-*+]\s+""")
            val text = line.trimStart().replaceFirst(marker, "")
            out.append("<li>").append(inline(text)).append("</li>\n")
            i++
        }
        out.append("</$tag>")
        return out.toString()
    }
}
