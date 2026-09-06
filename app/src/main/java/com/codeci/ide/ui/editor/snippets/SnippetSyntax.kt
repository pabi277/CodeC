package com.codeci.ide.ui.editor.snippets

/**
 * Phase 30.1 (rule S3) — VS Code snippet syntax → plain insert text.
 *
 * The vendored friendly-snippets packs are VS Code snippet files: their
 * bodies are full of tabstops (`$1`), placeholders (`${1:foo}`), choices
 * (`${1|a,b|}`), mirrors, variables (`${TM_FILENAME_BASE}`) and the odd
 * transformation (`${1/(.*)/${1:/capitalize}/}`). CodeC has no snippet
 * *session* (no Tab-hopping between stops on a phone — the strip/ghost/panel
 * accept once and the caret lands), so the body is RESOLVED to ordinary text:
 *
 *  - a placeholder/choice keeps its human-readable default (`${1:i}` → `i`),
 *  - a MIRROR (`$1` after `${1:i}`) repeats that same text — dropping mirrors
 *    would produce broken code (`for (int i = 0;  < n; ++)`) — VS Code links
 *    them live, we render them filled in,
 *  - variables resolve from the file name when CodeC knows it, otherwise they
 *    vanish (there is no clipboard/selection/workspace context to read),
 *  - transformations are applied by a small regex-format engine (groups +
 *    `/upcase` `/downcase` `/capitalize` + the `:+`/`:-`/`:?` conditionals),
 *  - `\$` `\}` `\\` `\|` unescape; every OTHER backslash sequence is left
 *    alone on purpose — `printf("\n")` in a C body is source text, not syntax,
 *  - real TAB characters become [INDENT] spaces (CodeC's editor indents with
 *    spaces; `useTab()` is false in `CodeCLanguage`),
 *  - the caret parks at the FIRST stop (lowest index ≥ 1, else `$0`) —
 *    [ResolvedSnippet.caretOffset]; null means "end of the insert".
 *
 * Pure Kotlin, no Android: every rule above is a case in `SnippetSyntaxTest`.
 */
data class ResolvedSnippet(
    /** The text to insert (snippet syntax fully resolved). */
    val text: String,
    /** Where to park the caret inside [text]; null = after the insert. */
    val caretOffset: Int?
)

object SnippetSyntax {

    /** One indentation level of a resolved body (the editor inserts spaces). */
    const val INDENT = "    "

    /** Guards against a pathological self-referencing placeholder. */
    private const val MAX_DEPTH = 12

    /** Guards a `g`-transform against a zero-length-match loop. */
    private const val MAX_REPLACEMENTS = 64

    fun resolve(body: String, fileName: String? = null): ResolvedSnippet =
        Resolver(body, fileName).run()

    fun resolveLines(lines: List<String>, fileName: String? = null): ResolvedSnippet =
        resolve(lines.joinToString("\n"), fileName)

    // ---- internals -------------------------------------------------------

    /** A resolved fragment plus the stop offsets inside it (mirror merging). */
    private class Fragment(val text: String, val stops: Map<Int, Int>)

    private enum class StopKind { BARE, PLACEHOLDER, CHOICE, TRANSFORM }

    private sealed class Head {
        data class Stop(val index: Int, val kind: StopKind, val payload: String) : Head()

        data class Variable(
            val name: String,
            val transform: String?,
            val defaultPayload: String?
        ) : Head()

        object Unknown : Head()
    }

    private class Resolver(private val src: String, private val fileName: String?) {

        /** index → raw placeholder source (first definition wins). */
        private val rawPlaceholders = HashMap<Int, String>()

        /** index → first choice text of a `${n|a,b|}`. */
        private val rawChoices = HashMap<Int, String>()

        private val fragments = HashMap<Int, Fragment>()
        private val busy = HashSet<Int>()

        fun run(): ResolvedSnippet {
            collect(src, 0)
            val out = StringBuilder()
            val stops = LinkedHashMap<Int, Int>()
            emit(src, out, stops, 0)
            val text = out.toString()
            return ResolvedSnippet(text, caretFor(stops, text))
        }

        // -- pass 1: which placeholder text belongs to which index ---------

        private fun collect(s: String, depth: Int) {
            if (depth > MAX_DEPTH) return
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    i += 2
                    continue
                }
                if (c != '$' || i + 1 >= s.length || s[i + 1] != '{') {
                    i++
                    continue
                }
                val end = braceEnd(s, i + 1)
                if (end < 0) {
                    i++
                    continue
                }
                when (val head = parseHead(s.substring(i + 2, end))) {
                    is Head.Stop -> {
                        when (head.kind) {
                            StopKind.PLACEHOLDER ->
                                if (!rawPlaceholders.containsKey(head.index)) {
                                    rawPlaceholders[head.index] = head.payload
                                }
                            StopKind.CHOICE ->
                                if (!rawChoices.containsKey(head.index)) {
                                    rawChoices[head.index] = firstChoice(head.payload)
                                }
                            else -> Unit
                        }
                        collect(head.payload, depth + 1)
                    }
                    is Head.Variable -> head.defaultPayload?.let { collect(it, depth + 1) }
                    Head.Unknown -> Unit
                }
                i = end + 1
            }
        }

        // -- pass 2: emit resolved text ------------------------------------

        private fun emit(
            s: String,
            out: StringBuilder,
            stops: MutableMap<Int, Int>,
            depth: Int
        ) {
            if (depth > MAX_DEPTH) {
                out.append(s)
                return
            }
            var i = 0
            while (i < s.length) {
                when (val c = s[i]) {
                    '\\' -> {
                        val next = if (i + 1 < s.length) s[i + 1] else ' '
                        if (next == '$' || next == '}' || next == '\\' || next == '|') {
                            out.append(next)
                            i += 2
                        } else {
                            // Not a snippet escape: keep the backslash (C's "\n").
                            out.append('\\')
                            i++
                        }
                    }
                    '\t' -> {
                        out.append(INDENT)
                        i++
                    }
                    '$' -> i = emitDollar(s, i, out, stops, depth)
                    else -> {
                        out.append(c)
                        i++
                    }
                }
            }
        }

        /** Emits whatever `$…` starts at [start]; returns the next index. */
        private fun emitDollar(
            s: String,
            start: Int,
            out: StringBuilder,
            stops: MutableMap<Int, Int>,
            depth: Int
        ): Int {
            val next = start + 1
            if (next >= s.length) {
                out.append('$')
                return next
            }
            // $123 — a bare mirror.
            if (s[next].isDigit()) {
                var j = next
                while (j < s.length && s[j].isDigit()) j++
                val index = s.substring(next, j).toIntOrNull()
                if (index == null) {
                    out.append('$')
                    return next
                }
                appendStop(index, out, stops)
                return j
            }
            // ${…}
            if (s[next] == '{') {
                val end = braceEnd(s, next)
                if (end < 0) {
                    out.append('$')
                    return next
                }
                val inner = s.substring(next + 1, end)
                when (val head = parseHead(inner)) {
                    is Head.Stop -> {
                        when (head.kind) {
                            StopKind.BARE, StopKind.PLACEHOLDER ->
                                appendStop(head.index, out, stops)
                            StopKind.CHOICE -> {
                                recordStop(head.index, out, stops)
                                out.append(firstChoice(head.payload))
                            }
                            StopKind.TRANSFORM -> out.append(
                                transform(fragmentOf(head.index).text, head.payload)
                            )
                        }
                        return end + 1
                    }
                    is Head.Variable -> {
                        val value = variableValue(head.name)
                        val transformSpec = head.transform
                        if (transformSpec != null) {
                            out.append(transform(value, transformSpec))
                        } else if (value.isNotEmpty()) {
                            out.append(value)
                        } else if (head.defaultPayload != null) {
                            val child = StringBuilder()
                            val childStops = LinkedHashMap<Int, Int>()
                            emit(head.defaultPayload, child, childStops, depth + 1)
                            appendFragment(child.toString(), childStops, out, stops)
                        }
                        return end + 1
                    }
                    Head.Unknown -> {
                        out.append('$')
                        return next
                    }
                }
            }
            // $TM_FILENAME — a bare variable name.
            if (s[next].isLetter() || s[next] == '_') {
                var j = next
                while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '_')) j++
                out.append(variableValue(s.substring(next, j)))
                return j
            }
            out.append('$')
            return next
        }

        private fun recordStop(index: Int, out: StringBuilder, stops: MutableMap<Int, Int>) {
            if (!stops.containsKey(index)) stops[index] = out.length
        }

        private fun appendStop(index: Int, out: StringBuilder, stops: MutableMap<Int, Int>) {
            recordStop(index, out, stops)
            val frag = fragmentOf(index)
            appendFragment(frag.text, frag.stops, out, stops)
        }

        private fun appendFragment(
            text: String,
            nested: Map<Int, Int>,
            out: StringBuilder,
            stops: MutableMap<Int, Int>
        ) {
            val base = out.length
            out.append(text)
            for ((key, offset) in nested) {
                if (!stops.containsKey(key)) stops[key] = base + offset
            }
        }

        /** Resolved text of tabstop [index] (placeholder / choice / empty). */
        private fun fragmentOf(index: Int): Fragment {
            fragments[index]?.let { return it }
            if (!busy.add(index)) return Fragment("", emptyMap())
            val raw = rawPlaceholders[index] ?: rawChoices[index] ?: ""
            val out = StringBuilder()
            val stops = LinkedHashMap<Int, Int>()
            emit(raw, out, stops, 1)
            busy.remove(index)
            return Fragment(out.toString(), stops).also { fragments[index] = it }
        }

        // -- variables -----------------------------------------------------

        private fun variableValue(name: String): String {
            val leaf = fileName?.substringAfterLast('/')?.substringAfterLast('\\').orEmpty()
            return when (name) {
                "TM_FILENAME" -> leaf
                "TM_FILENAME_BASE" -> leaf.substringBeforeLast('.', leaf)
                "TM_FILEPATH", "RELATIVE_FILEPATH" -> fileName.orEmpty()
                // The directory of the open file ("" when it sits at the
                // project root). cpp.json's `#guard` turns it into the
                // `<dirname>` part of `INCLUDE_<dirname>_<base>_<ext>_`.
                "TM_DIRECTORY", "WORKSPACE_FOLDER", "WORKSPACE_NAME" -> directoryOf(fileName)
                // No clipboard/selection/random/date context on a phone editor:
                // these vanish rather than print "${RANDOM}" into the buffer.
                else -> ""
            }
        }

        /**
         * Everything before the last path separator, or "" when there is none.
         *
         * NOT `substringBeforeLast('/', "").substringBeforeLast('\\', "")`:
         * `substringBeforeLast` returns its missingDelimiterValue when the
         * delimiter is absent, so chaining the two separators silently
         * produced "" for a plain "proj/main.c" (found by `SnippetSyntaxTest`).
         */
        private fun directoryOf(path: String?): String {
            if (path.isNullOrEmpty()) return ""
            val cut = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
            return if (cut < 0) "" else path.substring(0, cut)
        }

        // -- transformations ----------------------------------------------

        /** `${n/regex/format/options}` — groups + case ops + conditionals. */
        private fun transform(source: String, body: String): String {
            // The regex segment stays VERBATIM (its own `\/` `\\` escapes are
            // part of the pattern — unescaping them would break e.g. the C++
            // include-guard's `[\/\\]` character class).
            val parts = SnippetText.splitTransform(body)
            if (parts.size < 2) return ""
            val pattern = parts[0]
            val format = parts[1]
            val options = if (parts.size > 2) parts[2] else ""
            return try {
                val regexOptions =
                    if (options.contains('i')) setOf(RegexOption.IGNORE_CASE) else emptySet()
                val regex = Regex(pattern, regexOptions)
                val out = StringBuilder()
                var last = 0
                var done = 0
                for (match in regex.findAll(source)) {
                    if (done >= MAX_REPLACEMENTS) break
                    if (done == 1 && !options.contains('g')) break
                    out.append(source, last, match.range.first)
                    out.append(formatReplacement(match.groupValues, format))
                    last = match.range.last + 1
                    done++
                }
                if (done == 0) return "" // VS Code: no match → empty
                out.append(source, last, source.length)
                out.toString()
            } catch (e: RuntimeException) {
                "" // an exotic pattern degrades to nothing, never to a crash
            }
        }

        private fun formatReplacement(groups: List<String>, format: String): String {
            val out = StringBuilder()
            var i = 0
            while (i < format.length) {
                val c = format[i]
                if (c == '\\' && i + 1 < format.length) {
                    out.append(format[i + 1])
                    i += 2
                    continue
                }
                if (c != '$' || i + 1 >= format.length) {
                    out.append(c)
                    i++
                    continue
                }
                if (format[i + 1].isDigit()) {
                    var j = i + 1
                    while (j < format.length && format[j].isDigit()) j++
                    out.append(group(groups, format.substring(i + 1, j)))
                    i = j
                    continue
                }
                if (format[i + 1] == '{') {
                    val end = format.indexOf('}', i + 2)
                    if (end < 0) {
                        out.append(c)
                        i++
                        continue
                    }
                    out.append(replacementVariable(groups, format.substring(i + 2, end)))
                    i = end + 1
                    continue
                }
                out.append(c)
                i++
            }
            return out.toString()
        }

        /** `${1}`, `${1:/upcase}`, `${1:+set}`, `${1:-none}`, `${1:?a:b}`. */
        private fun replacementVariable(groups: List<String>, body: String): String {
            var j = 0
            while (j < body.length && body[j].isDigit()) j++
            if (j == 0) return ""
            val value = group(groups, body.substring(0, j))
            val rest = body.substring(j)
            if (rest.isEmpty()) return value
            if (rest[0] == ':') {
                val spec = rest.substring(1)
                if (spec.startsWith("/")) return caseOp(value, spec.substring(1))
                if (spec.startsWith("+")) return if (value.isNotEmpty()) spec.substring(1) else ""
                if (spec.startsWith("-")) return if (value.isEmpty()) spec.substring(1) else ""
                if (spec.startsWith("?")) {
                    val colon = spec.indexOf(':')
                    val yes = if (colon < 0) spec.substring(1) else spec.substring(1, colon)
                    val no = if (colon < 0) "" else spec.substring(colon + 1)
                    return if (value.isNotEmpty()) yes else no
                }
                return value
            }
            return value
        }

        private fun group(groups: List<String>, digits: String): String {
            val index = digits.toIntOrNull() ?: return ""
            return if (index in groups.indices) groups[index] else ""
        }

        private fun caseOp(value: String, op: String): String = when (op) {
            "upcase" -> value.uppercase()
            "downcase" -> value.lowercase()
            "capitalize" ->
                if (value.isEmpty()) value
                else value[0].uppercaseChar() + value.substring(1).lowercase()
            "pascalcase" -> value.split(SnippetText.NON_WORD)
                .filter { it.isNotEmpty() }
                .joinToString("") { it[0].uppercaseChar() + it.substring(1).lowercase() }
            "camelcase" -> {
                val parts = value.split(SnippetText.NON_WORD).filter { it.isNotEmpty() }
                if (parts.isEmpty()) ""
                else parts.first().lowercase() + parts.drop(1).joinToString("") {
                    it[0].uppercaseChar() + it.substring(1).lowercase()
                }
            }
            else -> value
        }

        // -- shared scanning helpers ---------------------------------------

        /** Index of the `}` matching the `{` at [open]; -1 when unbalanced. */
        private fun braceEnd(s: String, open: Int): Int {
            var depth = 0
            var i = open
            while (i < s.length) {
                when (s[i]) {
                    '\\' -> i++
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
                i++
            }
            return -1
        }

        private fun parseHead(inner: String): Head {
            if (inner.isEmpty()) return Head.Unknown
            if (inner[0].isDigit()) {
                var j = 0
                while (j < inner.length && inner[j].isDigit()) j++
                val index = inner.substring(0, j).toIntOrNull() ?: return Head.Unknown
                val rest = inner.substring(j)
                return when {
                    rest.isEmpty() -> Head.Stop(index, StopKind.BARE, "")
                    rest[0] == ':' -> Head.Stop(index, StopKind.PLACEHOLDER, rest.substring(1))
                    rest[0] == '|' -> Head.Stop(
                        index,
                        StopKind.CHOICE,
                        rest.substring(1).removeSuffix("|")
                    )
                    rest[0] == '/' -> Head.Stop(index, StopKind.TRANSFORM, rest.substring(1))
                    else -> Head.Unknown
                }
            }
            var j = 0
            while (j < inner.length && (inner[j].isLetterOrDigit() || inner[j] == '_')) j++
            if (j == 0) return Head.Unknown
            val name = inner.substring(0, j)
            val rest = inner.substring(j)
            return when {
                rest.isEmpty() -> Head.Variable(name, null, null)
                rest[0] == ':' -> Head.Variable(name, null, rest.substring(1))
                rest[0] == '/' -> Head.Variable(name, rest.substring(1), null)
                else -> Head.Unknown
            }
        }

        private fun firstChoice(payload: String): String =
            SnippetText.splitUnescaped(payload, ',').firstOrNull().orEmpty()

        /** Lowest positive stop wins (VS Code starts at `$1`); then `$0`. */
        private fun caretFor(stops: Map<Int, Int>, text: String): Int? {
            val offset = stops.keys.filter { it > 0 }.minOrNull()?.let { stops[it] }
                ?: stops[0]
                ?: return null
            if (offset !in 0 until text.length) return null
            return offset
        }
    }

}

/**
 * Scanning helpers shared by the resolver. Top-level (not nested in
 * [SnippetSyntax]) so the references are unambiguous from the resolver's
 * nested class.
 */
internal object SnippetText {

    val NON_WORD = Regex("[^A-Za-z0-9]+")

    /** Splits on [sep] ignoring `\`-escaped separators (and unescapes them). */
    fun splitUnescaped(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                current.append(s[i + 1])
                i += 2
                continue
            }
            if (c == sep) {
                out += current.toString()
                current.setLength(0)
                i++
                continue
            }
            current.append(c)
            i++
        }
        out += current.toString()
        return out
    }

    /**
     * Splits a transformation body into `regex / format / options` on the
     * slashes that are neither escaped nor inside a `${…}` — the FORMAT is
     * where `${1:/upcase}` lives, and its own slash must not end the segment.
     * Segments keep their escapes verbatim (they belong to the pattern).
     */
    fun splitTransform(body: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var depth = 0
        var i = 0
        while (i < body.length && out.size < 2) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                current.append(c).append(body[i + 1])
                i += 2
                continue
            }
            if (c == '{') depth++
            if (c == '}') depth = (depth - 1).coerceAtLeast(0)
            if (c == '/' && depth == 0) {
                out += current.toString()
                current.setLength(0)
                i++
                continue
            }
            current.append(c)
            i++
        }
        current.append(body.substring(i))
        out += current.toString()
        return out
    }
}
