package com.codeci.ide.ui.editor

import com.codeci.ide.ui.utils.LanguageType

/**
 * Phase 30.2 — Emmet-style abbreviation expansion, CLEAN ROOM.
 *
 * The phone-web expectation (Acode / VS Code) is that `ul>li*3` expands into
 * a list. This file re-implements the *behavior* of the abbreviation subset
 * the plan named — `!`, tag names, `>`, `+`, `^`, `*n`, `.class`, `#id`,
 * `[attr=value]`, `{text}`, `$` numbering — plus a compact CSS property
 * engine (`m10-20`, `d:f`, `bg#fff`, `w100p`, `!` important). No emmetio
 * code, no Ace code, no JS interpreter: pure Kotlin, no Android, every rule a
 * case in `EmmetTest`.
 *
 * Deliberately NOT supported (plan §1: "a host-tested abbreviation subset is
 * enough for phone HTML"): `@`/`$@-` numbering modifiers, filter chains
 * (`|bem`, `|c`, `|t`), wrap-with-selection, Lorem ipsum, SCSS nested
 * properties, gradients (`lg(…)`), and any abbreviation whose parse is not
 * unambiguous — a parse failure returns null and NO item is offered, because
 * a wrong expansion is worse than none.
 *
 * Everything is bounded: an abbreviation longer than [MAX_ABBREVIATION], a
 * repetition above [MAX_REPEAT] or a tree above [MAX_NODES] is refused, so a
 * stray `div*99999` can never hang the completion thread.
 */
object Emmet {

    /** CompletionItem detail — the strip/panel show it, tests pin on it. */
    const val DETAIL = "emmet"

    const val MAX_ABBREVIATION = 120
    const val MAX_REPEAT = 100
    const val MAX_NODES = 400

    private const val MAX_DEPTH = 24
    private const val INDENT = "    "

    /** One expansion: the text to insert + where the caret parks. */
    data class Expansion(val text: String, val caretOffset: Int?)

    // ---- public API ------------------------------------------------------

    /** True when Emmet may fire at all for this file (the language gate). */
    fun enabledFor(language: LanguageType, fileName: String? = null): Boolean = when (language) {
        LanguageType.HTML, LanguageType.XML, LanguageType.CSS -> true
        // JSX-ish: the plan's "(and JSX-ish)". `.jsx`/`.tsx` only — a plain
        // `.js`/`.ts` file must never see markup expansions (exit 30.2 §2.3).
        LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT -> isJsx(fileName)
        else -> false
    }

    /**
     * The abbreviation token under [caret], or null when nothing is worth
     * offering. The walk-back stops at whitespace and at characters that can
     * never be part of an abbreviation; the token must then carry a structural
     * signal (see [hasMarkupSignal]) so prose (`Hello.World`), member access
     * (`obj.method`) and plain tag names (`div` — the snippet pack already
     * offers those) never produce a chip.
     */
    fun abbreviationAt(
        text: String,
        caret: Int,
        language: LanguageType,
        fileName: String? = null
    ): String? {
        if (!enabledFor(language, fileName)) return null
        val cursor = caret.coerceIn(0, text.length)
        val css = language == LanguageType.CSS
        val jsx = language == LanguageType.JAVASCRIPT || language == LanguageType.TYPESCRIPT
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        var start = cursor
        while (start > lineStart && isTokenChar(text[start - 1], css)) start--
        val token = text.substring(start, cursor)
        if (token.isEmpty() || token.length > MAX_ABBREVIATION) return null
        val before = text.substring(lineStart, start)
        if (css) {
            if (!cssContextOk(before)) return null
            if (!token[0].isLetter()) return null
            if (!token.any { it.isDigit() || it == '#' || it == '%' || it == ':' }) return null
            return token
        }
        if (!markupContextOk(before)) return null
        if (jsx) {
            // In a JS/TS expression `.` and `[` are member access, not markup:
            // JSX-ish files only fire on real structural operators.
            if (token == "!") return null
            if (!token.any { it == '>' || it == '*' || it == '^' || it == '+' }) return null
        } else {
            if (!hasMarkupSignal(token)) return null
        }
        return token
    }

    /** Expand [abbr] for [language]; null when it is not a valid abbreviation. */
    fun expand(
        abbr: String,
        language: LanguageType,
        baseIndent: String = "",
        fileName: String? = null
    ): Expansion? {
        if (abbr.isBlank() || abbr.length > MAX_ABBREVIATION) return null
        return if (language == LanguageType.CSS) {
            Css.expand(abbr, baseIndent)
        } else {
            Markup.expand(abbr, baseIndent, isJsx(fileName))
        }
    }

    /**
     * The completion item for the caret, or null. The engine prepends it, so
     * it is rank 0 by construction: the abbreviation is the label, the
     * expansion is the insert text, and [CompletionItem.replaceLength] tells
     * the accept paths to replace the WHOLE abbreviation — the identifier
     * prefix at the caret is only its last fragment (`ul>li*3` → prefix `3`).
     */
    fun completionItemFor(
        text: String,
        caret: Int,
        language: LanguageType,
        fileName: String? = null
    ): CompletionItem? {
        val token = abbreviationAt(text, caret, language, fileName) ?: return null
        val cursor = caret.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val baseIndent = leadingWhitespace(text.substring(lineStart, cursor))
        val expansion = expand(token, language, baseIndent, fileName) ?: return null
        if (expansion.text.isBlank()) return null
        return CompletionItem(
            label = token,
            insertText = expansion.text,
            kind = CompletionKind.SNIPPET,
            detail = DETAIL,
            replaceLength = token.length,
            caretOffset = expansion.caretOffset
        )
    }

    // ---- gates -----------------------------------------------------------

    private fun isJsx(fileName: String?): Boolean {
        val leaf = fileName?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.lowercase() ?: return false
        return leaf.endsWith(".jsx") || leaf.endsWith(".tsx")
    }

    private fun isTokenChar(c: Char, css: Boolean): Boolean =
        if (css) {
            c.isLetterOrDigit() || c == '-' || c == '_' || c == '#' || c == '.' ||
                c == '%' || c == '+' || c == ':' || c == '!' || c == '(' || c == ')' ||
                c == '/' || c == '$' || c == ','
        } else {
            c.isLetterOrDigit() || c == '-' || c == '_' || c == '#' || c == '.' ||
                c == '>' || c == '+' || c == '*' || c == '^' || c == '(' || c == ')' ||
                c == '[' || c == ']' || c == '{' || c == '}' || c == '=' || c == ':' ||
                c == '$' || c == '!' || c == '/' || c == '@' || c == ','
        }

    /** `!`, a structural operator, `[attr]`, `{text}`, `#id` or a leading `.`. */
    private fun hasMarkupSignal(token: String): Boolean {
        if (token == "!" || token == "html:5") return true
        for (c in token) {
            if (c == '>' || c == '+' || c == '*' || c == '^' || c == '[' ||
                c == '{' || c == '(' || c == '#'
            ) {
                return true
            }
        }
        // `.card` / `#main` are Emmet's implicit-div form; a `.` MID-token is
        // prose or member access and must not fire.
        return token[0] == '.'
    }

    /**
     * Markup context: refuse to fire INSIDE a tag (between `<` and its `>`),
     * inside an attribute string or inside an HTML comment — there an
     * abbreviation-looking token is really markup, not an expansion request.
     */
    private fun markupContextOk(before: String): Boolean {
        if (before.lastIndexOf('<') > before.lastIndexOf('>')) return false
        if (before.count { it == '"' } % 2 != 0) return false
        if (before.count { it == '\'' } % 2 != 0) return false
        val comment = before.lastIndexOf("<!--")
        if (comment >= 0 && before.indexOf("-->", comment) < 0) return false
        return true
    }

    /** CSS context: not inside a string, a comment, or a selector list. */
    private fun cssContextOk(before: String): Boolean {
        if (before.count { it == '"' } % 2 != 0) return false
        if (before.count { it == '\'' } % 2 != 0) return false
        val comment = before.lastIndexOf("/*")
        if (comment >= 0 && before.indexOf("*/", comment) < 0) return false
        val prev = before.trimEnd().lastOrNull() ?: return true
        return prev != ',' && prev != '>' && prev != '~' && prev != '='
    }

    private fun leadingWhitespace(s: String): String {
        var i = 0
        while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
        return s.substring(0, i)
    }

    /**
     * Renders to RELATIVE indentation and then re-indents every continuation
     * line onto the caret's own indentation. The FIRST line already sits where
     * the caret is, so prefixing it too would double the indent (`  ` + `  <ul>`).
     */
    internal fun finish(raw: String, rawCaret: Int?, baseIndent: String): Expansion {
        if (baseIndent.isEmpty() || !raw.contains('\n')) return Expansion(raw, rawCaret)
        val caretLine = if (rawCaret != null) raw.substring(0, rawCaret).count { it == '\n' } else 0
        val sb = StringBuilder(raw.length + baseIndent.length * 16)
        var index = 0
        for (line in raw.split('\n')) {
            if (index > 0) sb.append('\n').append(baseIndent)
            sb.append(line)
            index++
        }
        val caret = if (rawCaret == null) null else rawCaret + caretLine * baseIndent.length
        return Expansion(sb.toString(), caret)
    }

    // ---- markup ----------------------------------------------------------

    /** HTML/XML/JSX elements, groups, repetition, and the `!` skeleton. */
    private object Markup {

        private val VOID = setOf(
            "area", "base", "br", "col", "command", "embed", "hr", "img",
            "input", "keygen", "link", "meta", "param", "source", "track", "wbr"
        )

        /** Emmet's implicit tag names (`ul>`, `table>tr>`, `.card`, `ul>*2`). */
        private fun implicitName(parent: String?): String = when (parent) {
            "ul", "ol" -> "li"
            "table", "thead", "tbody", "tfoot" -> "tr"
            "tr" -> "td"
            "select", "optgroup", "datalist" -> "option"
            "dl" -> "dd"
            "map" -> "area"
            "object" -> "param"
            "audio", "video", "picture" -> "source"
            else -> "div"
        }

        fun expand(abbr: String, baseIndent: String, jsx: Boolean): Expansion? {
            val trimmed = abbr.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed == "!" || trimmed == "html:5") return finish(skeleton(), skeletonCaret, baseIndent)
            val nodes = Parser(trimmed).parse() ?: return null
            if (nodes.isEmpty()) return null
            val sb = StringBuilder()
            val caret = intArrayOf(-1)
            val budget = intArrayOf(MAX_NODES)
            if (!render(nodes, null, 0, sb, caret, budget, true, jsx, 1)) return null
            return finish(sb.toString(), caret[0].takeIf { it >= 0 }, baseIndent)
        }

        /** Where the caret parks in [skeleton]: the blank line inside <body>. */
        private val skeletonCaret: Int by lazy {
            skeleton().indexOf("<body>\n") + "<body>\n".length + INDENT.length + INDENT.length
        }

        private fun skeleton(): String =
            "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "    <head>\n" +
                "        <meta charset=\"utf-8\">\n" +
                "        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                "        <title>Page</title>\n" +
                "    </head>\n" +
                "    <body>\n" +
                "        \n" +
                "    </body>\n" +
                "</html>"

        // -- render -------------------------------------------------------

        /**
         * @param counter the repetition number inherited from the nearest
         *        repeated ancestor (`ul>li*3>a{Link $}` numbers the links 1..3
         *        even though each `a` repeats once).
         */
        private fun render(
            nodes: List<Node>,
            parentName: String?,
            depth: Int,
            sb: StringBuilder,
            caret: IntArray,
            budget: IntArray,
            first: Boolean,
            jsx: Boolean,
            counter: Int
        ): Boolean {
            if (depth > MAX_DEPTH) return false
            var firstLine = first
            for (node in nodes) {
                val times = node.repeat.coerceIn(1, MAX_REPEAT)
                for (k in 1..times) {
                    if (budget[0]-- <= 0) return false
                    val number = if (times > 1) k else counter
                    if (node.group) {
                        if (!render(node.children, parentName, depth, sb, caret, budget, firstLine, jsx, number)) {
                            return false
                        }
                        firstLine = false
                        continue
                    }
                    val name = node.name.ifEmpty { implicitName(parentName) }
                    val indent = INDENT.repeat(depth)
                    if (!firstLine) sb.append('\n')
                    firstLine = false
                    sb.append(indent).append('<').append(name)
                    node.id?.let { sb.append(" id=\"").append(numbered(it, number)).append('"') }
                    if (node.classes.isNotEmpty()) {
                        sb.append(" class=\"")
                        sb.append(node.classes.joinToString(" ") { numbered(it, number) })
                        sb.append('"')
                    }
                    for (attr in node.attrs) sb.append(' ').append(numbered(attr, number))
                    val text = node.text?.let { numbered(it, number) }
                    val void = name in VOID
                    if (node.children.isEmpty() && (void || node.selfClosing)) {
                        if (jsx || node.selfClosing) sb.append(" /")
                        sb.append('>')
                        if (caret[0] < 0) caret[0] = sb.length
                        continue
                    }
                    sb.append('>')
                    if (node.children.isEmpty()) {
                        // An empty element (or the start of its text) is where
                        // the caret parks — the phone equivalent of Emmet's $1.
                        if (caret[0] < 0) caret[0] = sb.length
                        if (text != null) sb.append(text)
                        sb.append("</").append(name).append('>')
                        continue
                    }
                    if (text != null) sb.append(text)
                    sb.append('\n')
                    if (!render(node.children, name, depth + 1, sb, caret, budget, true, jsx, number)) {
                        return false
                    }
                    sb.append('\n').append(indent).append("</").append(name).append('>')
                }
            }
            return true
        }

        /** `$` numbering: `$` → k, `$$` → zero-padded 2, `$$$` → 3, … */
        private fun numbered(value: String, k: Int): String {
            if (!value.contains('$')) return value
            val sb = StringBuilder()
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    sb.append(value[i + 1])
                    i += 2
                    continue
                }
                if (c != '$') {
                    sb.append(c)
                    i++
                    continue
                }
                var j = i
                while (j < value.length && value[j] == '$') j++
                sb.append(k.toString().padStart(j - i, '0'))
                i = j
            }
            return sb.toString()
        }

        // -- parse --------------------------------------------------------

        private class Node(
            var name: String = "",
            val group: Boolean = false
        ) {
            var id: String? = null
            val classes = ArrayList<String>()
            val attrs = ArrayList<String>()
            var text: String? = null
            var repeat: Int = 1
            var selfClosing: Boolean = false
            val children = ArrayList<Node>()
        }

        /**
         * Operator-stack parser: `>` nests, `+` makes siblings, `^` climbs,
         * `*n` repeats (resolved at RENDER time, so `tr*2>td*3` gives every
         * row its own cells), `(…)` groups, and a bare `*n` is an implicit
         * element (`ul>*3`). Anything ambiguous → null.
         */
        private class Parser(private val s: String) {
            private var i = 0

            fun parse(): List<Node>? {
                val roots = parseLevel(groupEnd = false) ?: return null
                return if (i == s.length) roots else null
            }

            private fun parseLevel(groupEnd: Boolean): List<Node>? {
                val roots = ArrayList<Node>()
                val parents = ArrayList<Node>()
                var last: Node? = null
                var childNext = false
                while (i < s.length) {
                    val c = s[i]
                    if (groupEnd && c == ')') return roots
                    when (c) {
                        '>' -> {
                            childNext = true
                            i++
                        }
                        '+' -> {
                            childNext = false
                            i++
                        }
                        '^' -> {
                            i++
                            var climbs = 1
                            while (i < s.length && s[i] == '^') {
                                climbs++
                                i++
                            }
                            repeat(climbs) {
                                if (parents.isNotEmpty()) parents.removeAt(parents.size - 1)
                            }
                            childNext = false
                        }
                        '(' -> {
                            i++
                            val inner = parseLevel(groupEnd = true) ?: return null
                            if (i >= s.length || s[i] != ')') return null
                            i++
                            val group = Node(group = true)
                            group.children.addAll(inner)
                            group.repeat = readRepeat()
                            if (group.repeat < 1) return null
                            if (!attach(group, roots, parents, last, childNext)) return null
                            last = group
                            childNext = false
                        }
                        else -> {
                            val node = parseElement() ?: return null
                            if (!attach(node, roots, parents, last, childNext)) return null
                            last = node
                            childNext = false
                        }
                    }
                    if (roots.size + parents.size > MAX_NODES) return null
                }
                // Falling out of the loop inside a group means "(" was never
                // closed — refuse rather than guess.
                return if (groupEnd) null else roots
            }

            private fun attach(
                node: Node,
                roots: MutableList<Node>,
                parents: MutableList<Node>,
                last: Node?,
                childNext: Boolean
            ): Boolean {
                if (childNext) {
                    // `>` needs a real element to nest into (not a group).
                    if (last == null || last.group) return false
                    parents.add(last)
                    last.children.add(node)
                } else {
                    val parent = parents.lastOrNull()
                    if (parent == null) roots.add(node) else parent.children.add(node)
                }
                return true
            }

            /**
             * `*n` — [allowBare] accepts a digit-less `*` (the implicit element
             * form `ul>*`); after a real element name a bare `*` is a typo and
             * is refused (`div*`).
             */
            private fun readRepeat(allowBare: Boolean = false): Int {
                if (i >= s.length || s[i] != '*') return 1
                i++
                var digits = 0
                var value = 0
                while (i < s.length && s[i].isDigit()) {
                    value = value * 10 + (s[i] - '0')
                    digits++
                    i++
                    if (digits > 3) return -1
                }
                if (digits == 0) return if (allowBare) 1 else -1
                return if (value in 1..MAX_REPEAT) value else -1
            }

            private fun parseElement(): Node? {
                val node = Node()
                var sawSomething = false
                var implicitStar = false
                element@ while (i < s.length) {
                    val c = s[i]
                    when {
                        isNameChar(c) -> {
                            val start = i
                            while (i < s.length && isNameChar(s[i])) i++
                            node.name = s.substring(start, i)
                            sawSomething = true
                        }
                        c == '#' -> {
                            i++
                            node.id = readWord() ?: return null
                            sawSomething = true
                        }
                        c == '.' -> {
                            i++
                            node.classes += readWord() ?: return null
                            sawSomething = true
                        }
                        c == '[' -> {
                            i++
                            if (!readAttributes(node)) return null
                            sawSomething = true
                        }
                        c == '{' -> {
                            i++
                            node.text = node.text.orEmpty() + (readText() ?: return null)
                            sawSomething = true
                        }
                        c == '/' -> {
                            i++
                            node.selfClosing = true
                            sawSomething = true
                        }
                        // "*3" with no element before it: an implicit element
                        // (`ul>*3` → three <li>). readRepeat() takes the count.
                        c == '*' && !sawSomething -> {
                            implicitStar = true
                            sawSomething = true
                            break@element
                        }
                        // An operator or the end of a group: the level parser
                        // takes it from here.
                        else -> break@element
                    }
                }
                if (!sawSomething) return null
                node.repeat = readRepeat(allowBare = implicitStar)
                if (node.repeat < 1) return null
                return node
            }

            private fun isNameChar(c: Char): Boolean =
                c.isLetterOrDigit() || c == '-' || c == '_' || c == ':' || c == '%'

            /** Reads an id/class word; null when empty. */
            private fun readWord(): String? {
                val start = i
                while (i < s.length && (isNameChar(s[i]) || s[i] == '$')) i++
                return if (i == start) null else s.substring(start, i)
            }

            /** `[href=# target=_blank class="a b"]` → rendered attribute strings. */
            private fun readAttributes(node: Node): Boolean {
                while (i < s.length) {
                    while (i < s.length && s[i] == ' ') i++
                    if (i < s.length && s[i] == ']') {
                        i++
                        return true
                    }
                    val start = i
                    while (i < s.length && s[i] != ' ' && s[i] != ']' && s[i] != '=') i++
                    if (i == start) return false
                    val name = s.substring(start, i)
                    if (!name.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == ':' }) {
                        return false
                    }
                    if (i < s.length && s[i] == '=') {
                        i++
                        val value = readAttributeValue() ?: return false
                        node.attrs += name + "=\"" + value.replace("\"", "&quot;") + "\""
                    } else {
                        node.attrs += name + "=\"\""
                    }
                }
                return false // unterminated "["
            }

            private fun readAttributeValue(): String? {
                if (i >= s.length) return null
                val quote = s[i]
                if (quote == '"' || quote == '\'') {
                    i++
                    val start = i
                    while (i < s.length && s[i] != quote) i++
                    if (i >= s.length) return null
                    val value = s.substring(start, i)
                    i++
                    return value
                }
                val start = i
                while (i < s.length && s[i] != ' ' && s[i] != ']') i++
                return if (i == start) null else s.substring(start, i)
            }

            /** `{text}` — braces nest, `\}` escapes. */
            private fun readText(): String? {
                val sb = StringBuilder()
                var depth = 1
                while (i < s.length) {
                    val c = s[i]
                    if (c == '\\' && i + 1 < s.length) {
                        sb.append(s[i + 1])
                        i += 2
                        continue
                    }
                    if (c == '{') depth++
                    if (c == '}') {
                        depth--
                        if (depth == 0) {
                            i++
                            return sb.toString()
                        }
                    }
                    sb.append(c)
                    i++
                }
                return null // unterminated "{"
            }
        }
    }

    // ---- css -------------------------------------------------------------

    /**
     * The compact CSS engine: `<abbr><values>[!]` with `-`-separated values
     * and `+`-separated declarations (`m10+p20`). Property abbreviations come
     * from [PROPERTIES] (longest match wins), single-letter value keywords
     * from [VALUE_WORDS], and bare numbers take `px` unless the property is
     * [UNITLESS] (or the number is 0, which CSS writes unit-less).
     */
    private object Css {

        fun expand(abbr: String, baseIndent: String): Expansion? {
            val parts = abbr.split('+').filter { it.isNotBlank() }
            if (parts.isEmpty() || parts.size > 6) return null
            val out = ArrayList<String>()
            for (part in parts) {
                val declaration = declaration(part.trim()) ?: return null
                out += declaration
            }
            val text = out.joinToString("\n")
            if (text.isBlank()) return null
            // The caret parks at the first declaration's value.
            val caret = text.indexOf(": ").takeIf { it >= 0 }?.let { it + 2 }
            return finish(text, caret, baseIndent)
        }

        private fun declaration(token: String): String? {
            if (token.isEmpty() || !token[0].isLetter()) return null
            val important = token.endsWith("!")
            val body = if (important) token.dropLast(1) else token
            if (body.isEmpty()) return null
            var best: String? = null
            for (abbr in PROPERTIES.keys) {
                if (body.startsWith(abbr) && (best == null || abbr.length > best.length)) best = abbr
            }
            val key = best ?: return null
            val property = PROPERTIES[key] ?: return null
            val rawValue = body.substring(key.length).removePrefix(":")
            if (rawValue.isEmpty()) return null
            if (rawValue.any { it == '>' || it == '*' || it == '^' || it == '{' || it == '}' }) {
                return null
            }
            val values = splitValues(rawValue)
            if (values.isEmpty()) return null
            val resolved = ArrayList<String>(values.size)
            for (value in values) {
                val out = resolveValue(property, key, value) ?: return null
                resolved += out
            }
            val joined = resolved.joinToString(" ")
            if (joined.isBlank()) return null
            return property + ": " + joined + if (important) " !important;" else ";"
        }

        /** `-` separates values; a LEADING `-` is a negative number. */
        private fun splitValues(value: String): List<String> {
            val out = ArrayList<String>()
            val current = StringBuilder()
            for (c in value) {
                if (c == '-' && current.isNotEmpty() && current[current.length - 1] != '-') {
                    out += current.toString()
                    current.setLength(0)
                    continue
                }
                current.append(c)
            }
            if (current.isNotEmpty()) out += current.toString()
            return out.filter { it.isNotBlank() }
        }

        private fun resolveValue(property: String, abbr: String, value: String): String? {
            if (value.isEmpty()) return null
            // Colours, url(), var(), calc() and friends pass through untouched.
            if (value[0] == '#' || value.startsWith("url(") || value.startsWith("rgb") ||
                value.startsWith("var(") || value.startsWith("calc(") || value.startsWith("hsl")
            ) {
                return value
            }
            VALUE_WORDS[property]?.get(value)?.let { return it }
            val negative = value.startsWith("-")
            val unsigned = if (negative) value.substring(1) else value
            if (unsigned.isEmpty()) return null
            if (unsigned.none { it.isDigit() }) {
                // A plain word: keyword table, common keyword, or a literal
                // CSS value the engine does not know but the user typed.
                val word = VALUE_WORDS[property]?.get(unsigned) ?: COMMON_WORDS[unsigned]
                if (word != null) return if (negative) "-$word" else word
                return if (unsigned.all { it.isLetter() }) value else null
            }
            var cut = 0
            while (cut < unsigned.length && (unsigned[cut].isDigit() || unsigned[cut] == '.')) cut++
            val number = unsigned.substring(0, cut)
            if (number.isEmpty() || number == "." || number.toDoubleOrNull() == null) return null
            val suffix = unsigned.substring(cut)
            val zero = (number.toDoubleOrNull() ?: 1.0) == 0.0
            val unit = when {
                suffix.isEmpty() -> if (zero || abbr in UNITLESS) "" else "px"
                suffix == "p" || suffix == "%" -> "%"
                suffix == "e" || suffix == "em" -> "em"
                suffix == "r" || suffix == "rem" -> "rem"
                suffix == "x" || suffix == "px" -> "px"
                suffix == "vh" || suffix == "vw" || suffix == "vmin" || suffix == "vmax" -> suffix
                suffix == "cm" || suffix == "mm" || suffix == "in" || suffix == "pt" -> suffix
                suffix == "s" || suffix == "ms" || suffix == "fr" || suffix == "deg" -> suffix
                suffix == "ch" || suffix == "ex" -> suffix
                else -> return null
            }
            return (if (negative) "-" else "") + number + unit
        }

        /** Abbreviation → property (the longest matching abbreviation wins). */
        private val PROPERTIES: Map<String, String> = linkedMapOf(
            "m" to "margin", "mt" to "margin-top", "mr" to "margin-right",
            "mb" to "margin-bottom", "ml" to "margin-left",
            "p" to "padding", "pt" to "padding-top", "pr" to "padding-right",
            "pb" to "padding-bottom", "pl" to "padding-left",
            "w" to "width", "h" to "height",
            "miw" to "min-width", "maw" to "max-width",
            "mih" to "min-height", "mah" to "max-height",
            "d" to "display", "pos" to "position",
            "t" to "top", "r" to "right", "b" to "bottom", "l" to "left",
            "z" to "z-index", "fl" to "float", "cl" to "clear",
            "ov" to "overflow", "ovx" to "overflow-x", "ovy" to "overflow-y",
            "v" to "visibility", "bg" to "background", "bgc" to "background-color",
            "bgi" to "background-image", "bgr" to "background-repeat",
            "bgp" to "background-position", "bgs" to "background-size",
            "bga" to "background-attachment",
            "c" to "color", "op" to "opacity",
            "fz" to "font-size", "fw" to "font-weight", "ff" to "font-family",
            "fst" to "font-style", "lh" to "line-height", "lsp" to "letter-spacing",
            "ta" to "text-align", "td" to "text-decoration", "tt" to "text-transform",
            "ti" to "text-indent", "tsh" to "text-shadow", "ws" to "white-space",
            "bd" to "border", "bdt" to "border-top", "bdr" to "border-right",
            "bdb" to "border-bottom", "bdl" to "border-left",
            "bdc" to "border-color", "bdw" to "border-width", "bds" to "border-style",
            "br" to "border-radius", "bs" to "box-shadow", "bxz" to "box-sizing",
            "flx" to "flex", "flw" to "flex-wrap", "fld" to "flex-direction",
            "flg" to "flex-grow", "fls" to "flex-shrink", "flb" to "flex-basis",
            "jc" to "justify-content", "ai" to "align-items", "ac" to "align-content",
            "as" to "align-self", "or" to "order", "gp" to "gap",
            "tr" to "transition", "trf" to "transform", "ani" to "animation",
            "cur" to "cursor", "pe" to "pointer-events", "us" to "user-select",
            "cnt" to "content", "lis" to "list-style", "va" to "vertical-align"
        )

        /** Properties whose bare numbers stay unit-less. */
        private val UNITLESS = setOf("z", "op", "flg", "fls", "or", "ti", "lsp")

        /** Keywords every property understands (`m-a` → `margin: auto;`). */
        private val COMMON_WORDS = mapOf(
            "a" to "auto", "n" to "none", "i" to "inherit", "ini" to "initial", "u" to "unset"
        )

        /** Per-property single-letter keyword expansions (`d:f` → `flex`). */
        private val VALUE_WORDS: Map<String, Map<String, String>> = mapOf(
            "display" to mapOf(
                "f" to "flex", "b" to "block", "i" to "inline", "ib" to "inline-block",
                "if" to "inline-flex", "g" to "grid", "ig" to "inline-grid", "n" to "none",
                "t" to "table", "tr" to "table-row", "tc" to "table-cell", "fi" to "flow-root"
            ),
            "position" to mapOf(
                "a" to "absolute", "r" to "relative", "f" to "fixed", "s" to "static",
                "st" to "sticky"
            ),
            "float" to mapOf("l" to "left", "r" to "right", "n" to "none"),
            "clear" to mapOf("l" to "left", "r" to "right", "b" to "both", "n" to "none"),
            "overflow" to mapOf("v" to "visible", "h" to "hidden", "s" to "scroll", "a" to "auto"),
            "overflow-x" to mapOf(
                "v" to "visible", "h" to "hidden", "s" to "scroll", "a" to "auto"
            ),
            "overflow-y" to mapOf(
                "v" to "visible", "h" to "hidden", "s" to "scroll", "a" to "auto"
            ),
            "visibility" to mapOf("v" to "visible", "h" to "hidden", "c" to "collapse"),
            "text-align" to mapOf("l" to "left", "r" to "right", "c" to "center", "j" to "justify"),
            "text-transform" to mapOf(
                "u" to "uppercase", "l" to "lowercase", "c" to "capitalize", "n" to "none"
            ),
            "text-decoration" to mapOf(
                "n" to "none", "u" to "underline", "o" to "overline", "l" to "line-through"
            ),
            "font-weight" to mapOf(
                "b" to "bold", "br" to "bolder", "lr" to "lighter", "n" to "normal"
            ),
            "font-style" to mapOf("i" to "italic", "o" to "oblique", "n" to "normal"),
            "border-style" to mapOf(
                "s" to "solid", "d" to "dotted", "da" to "dashed", "db" to "double",
                "n" to "none", "g" to "groove", "r" to "ridge", "i" to "inset", "o" to "outset"
            ),
            "border" to mapOf("s" to "solid", "d" to "dotted", "da" to "dashed", "n" to "none"),
            "flex-direction" to mapOf(
                "r" to "row", "rr" to "row-reverse", "c" to "column", "cr" to "column-reverse"
            ),
            "flex-wrap" to mapOf("nw" to "nowrap", "w" to "wrap", "wr" to "wrap-reverse"),
            "justify-content" to mapOf(
                "fs" to "flex-start", "fe" to "flex-end", "c" to "center",
                "sb" to "space-between", "sa" to "space-around", "se" to "space-evenly"
            ),
            "align-items" to mapOf(
                "fs" to "flex-start", "fe" to "flex-end", "c" to "center",
                "b" to "baseline", "s" to "stretch"
            ),
            "align-content" to mapOf(
                "fs" to "flex-start", "fe" to "flex-end", "c" to "center",
                "sb" to "space-between", "sa" to "space-around", "s" to "stretch"
            ),
            "align-self" to mapOf(
                "a" to "auto", "fs" to "flex-start", "fe" to "flex-end", "c" to "center",
                "b" to "baseline", "s" to "stretch"
            ),
            "box-sizing" to mapOf("bb" to "border-box", "cb" to "content-box"),
            "cursor" to mapOf(
                "p" to "pointer", "d" to "default", "n" to "none", "h" to "help",
                "m" to "move", "t" to "text", "w" to "wait", "na" to "not-allowed"
            ),
            "white-space" to mapOf(
                "n" to "normal", "p" to "pre", "nw" to "nowrap", "pw" to "pre-wrap",
                "pl" to "pre-line"
            ),
            "pointer-events" to mapOf("n" to "none", "a" to "auto"),
            "vertical-align" to mapOf("t" to "top", "m" to "middle", "b" to "bottom")
        )
    }
}
