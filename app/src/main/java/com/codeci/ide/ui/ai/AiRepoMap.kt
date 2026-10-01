package com.codeci.ide.ui.ai

/**
 * Phase 80 (AI Level 4) — **the whole-project map**: a compact, always-sent
 * description of everything the project contains, so the model no longer has
 * to guess from five files.
 *
 * ## Why this exists (the owner's own words, Phase 79 device round)
 *
 * *"Can't it be like have full knowledge of my code and one model will set what
 * to sent what not to / Because as it is now it Can't be agent"* — the Level 2
 * packer sends at most [AiProjectFiles.MAX_FILES] files of
 * [AiProjectFiles.MAX_FILE_CHARS] characters each, chosen by keyword overlap.
 * For a project of more than a handful of files that is a blind guess, and the
 * model cannot even ask for what is missing. This file is the "know what
 * exists" half; `AiToolProtocol`/`AiToolRunner` are the "pull what you need"
 * half.
 *
 * ## What is in the map, and in what order
 *
 * 1. **Every code/text file** the Level 2 walk admitted (so never a credential,
 *    never build output — [AiProjectFiles] decides who is allowed here too),
 *    as `path (N lines)`.
 * 2. **Definitions** found by a small per-language line scan — functions,
 *    classes, structs — appended while the budget allows.
 * 3. A truthful tail when something did not fit: how many files are missing
 *    and that `list_files` can be used to see them.
 *
 * Paths are never absolute (`02_WHOLE_PROJECT_CONTEXT.md`: no device layout),
 * and the order is deterministic (directory groups, then names) so the same
 * project always renders the same map and the preview stays checkable (D4).
 *
 * ## What this is not
 *
 * - Not a parser. [symbolsFor] is a documented heuristic: a definition is a
 *   line that *starts* a named block in that language. A missed symbol costs
 *   one `search_project` call; a false one costs a line of budget.
 * - Not an index and not persisted (D6). It is rebuilt per task, in memory,
 *   from files the reader already had to read anyway.
 * - Not Android: strings and lists only (`rule.md` §4.4), so `AiRepoMapTest`
 *   runs on the host JVM.
 */
object AiRepoMap {

    /**
     * How many characters of map the agent request carries by default. The map
     * is the *floor* of the agent's knowledge, so it gets a real budget of its
     * own rather than competing with tool results for one pool — but it is
     * still bounded, because a 3 000-file project must not build a 100 KB
     * request (D4 disclosure has to stay readable).
     */
    const val MAX_MAP_CHARS = 6_000

    /** At most this many definition lines per file — the head is the useful part. */
    const val MAX_SYMBOLS_PER_FILE = 8

    /** One symbol line is clipped at this many characters. */
    const val MAX_SYMBOL_CHARS = 100

    /** Room kept for the "+ N more files" sentence before packing anything. */
    private const val ELISION_RESERVE = 120

    /** A file bigger than this is listed but its definitions are not scanned. */
    const val MAX_SYMBOL_SCAN_CHARS = 40_000

    /** One file as the map sees it. [text] is null when the caller did not read it. */
    data class FileInfo(val path: String, val lines: Int, val text: String?)

    /**
     * The rendered map plus its own accounting, so the preview and the timeline
     * can say exactly what the model was told (`AiAgentPrompt` renders
     * [summaryLine]).
     */
    data class MapResult(
        val text: String,
        /** Files whose path made it into [text]. */
        val filesListed: Int,
        /** Files the walk offered, listed or not. */
        val filesTotal: Int,
        /** Definitions printed across all listed files. */
        val symbols: Int,
        /** True when at least one file is not named in [text]. */
        val elided: Boolean
    ) {
        /** One honest sentence for the preview: what the model can see. */
        fun summaryLine(): String =
            if (!elided) "The map names all $filesTotal files" +
                (if (symbols > 0) " and $symbols definitions." else ".")
            else "The map names $filesListed of $filesTotal files" +
                (if (symbols > 0) " and $symbols definitions" else "") +
                "; the rest can be listed with list_files."

        companion object {
            fun empty(): MapResult = MapResult("", 0, 0, 0, false)
        }
    }

    private const val HEADER = "PROJECT MAP"

    /**
     * Builds the map. `files` must already be the Level 2-filtered set (never
     * credential-shaped names, never excluded directories) — this object
     * re-checks the same rules anyway so a caller cannot hand it a secret by
     * mistake, and drops anything refused.
     */
    fun build(files: List<FileInfo>, budget: Int = MAX_MAP_CHARS): MapResult {
        val safe = files
            .filter { it.path.isNotBlank() }
            .filter { !AiProjectFiles.isSecretLike(it.path.substringAfterLast('/')) }
            .filter { it.path.split('/').none { seg -> seg.isNotEmpty() && AiProjectFiles.isExcludedDirectory(seg) } }
            .distinctBy { it.path }
            .sortedWith(compareBy({ it.path.count { c -> c == '/' } }, { it.path }))

        if (safe.isEmpty()) return MapResult.empty()

        val fileCount = safe.size
        val headerLine = "$HEADER — $fileCount code/text ${if (fileCount == 1) "file" else "files"}; " +
            "read any with read_file(path), find text with search_project(query), list more with list_files."
        // The room a possible elision sentence needs, reserved before anything
        // else is packed, so the map can never grow past the budget and then
        // claim completeness it does not have.
        val reserve = ELISION_RESERVE
        var used = headerLine.length + 1 + reserve

        val pathLines = LinkedHashMap<String, String>()
        var currentDir = "\u0000"
        for (f in safe) {
            val dir = f.path.substringBeforeLast('/', "")
            val groupCost = if (dir == currentDir) 0 else (if (dir.isEmpty()) "./" else "$dir/").length + 1
            val line = "${f.path} (${f.lines} lines)"
            if (used + groupCost + line.length + 1 > budget) break
            currentDir = dir
            pathLines[f.path] = line
            used += groupCost + line.length + 1
        }

        val symbolLines = LinkedHashMap<String, String>()
        var symbols = 0
        for (f in safe) {
            if (!pathLines.containsKey(f.path)) continue
            val text = f.text ?: continue
            if (text.length > MAX_SYMBOL_SCAN_CHARS) continue
            val names = symbolsFor(f.path, text)
            if (names.isEmpty()) continue
            val line = "    " + names.joinToString(", ")
            if (used + line.length + 1 > budget) break
            symbolLines[f.path] = line
            used += line.length + 1
            symbols += names.size
        }

        val elided = pathLines.size < fileCount
        val lines = mutableListOf(headerLine)
        currentDir = "\u0000"
        for ((path, line) in pathLines) {
            val dir = path.substringBeforeLast('/', "")
            if (dir != currentDir) {
                currentDir = dir
                lines += if (dir.isEmpty()) "./" else "$dir/"
            }
            lines += "  $line"
            symbolLines[path]?.let { lines += it }
        }
        if (elided) {
            val missing = fileCount - pathLines.size
            lines += "+ $missing more ${if (missing == 1) "file" else "files"} not listed (map budget); use list_files."
        }
        return MapResult(lines.joinToString("\n"), pathLines.size, fileCount, symbols, elided)
    }

    /**
     * Definitions in one file, in line order, clipped and capped. Recognised by
     * extension family; anything unknown (config, prose, markup) gets none, and
     * the map then simply shows that file's path.
     *
     * The rules are deliberately conservative (`starts with`, not `contains`)
     * so a call site is never reported as a definition.
     */
    fun symbolsFor(path: String, text: String): List<String> {
        val ext = path.substringAfterLast('.', "").lowercase()
        val rules = rulesFor(ext) ?: return emptyList()
        val out = mutableListOf<String>()
        for (raw in text.lineSequence()) {
            if (out.size >= MAX_SYMBOLS_PER_FILE) break
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("*")) continue
            for (r in rules) {
                val m = r.find(line)
                if (m != null) {
                    val name = m.groupValues[1].take(MAX_SYMBOL_CHARS)
                    if (out.none { it == name }) out += name
                    break
                }
            }
        }
        return out
    }

    // A definition is a line that STARTS one; the regexes are anchored with
    // `^\s*` (or `^`) so `x = area(3)` never becomes a symbol.
    private val PY_DEF = Regex("^\\s*(?:async\\s+)?def\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val PY_CLASS = Regex("^\\s*class\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val C_FUNC = Regex("^\\s*(?:static\\s+|extern\\s+|inline\\s+|const\\s+)*[A-Za-z_][A-Za-z0-9_ \\t\\*]*[\\s\\*]+([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^;]*\\)\\s*\\{?\\s*$")
    private val KOTLIN_FUN = Regex("^\\s*(?:private\\s+|internal\\s+|public\\s+|protected\\s+|override\\s+|suspend\\s+|inline\\s+|tailrec\\s+)*fun\\s+(?:<[^>]+>\\s*)?(?:[A-Za-z_][A-Za-z0-9_.<>,? ]*\\.)?([A-Za-z_][A-Za-z0-9_]*)")
    private val CLASS_DECL = Regex("^\\s*(?:public\\s+|private\\s+|internal\\s+|open\\s+|abstract\\s+|sealed\\s+|final\\s+|data\\s+|value\\s+|enum\\s+|annotation\\s+)*class\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val OBJECT_DECL = Regex("^\\s*(?:public\\s+|private\\s+|internal\\s+|companion\\s+)*object\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val STRUCT_DECL = Regex("^\\s*(?:pub\\s+)?(?:struct|enum|trait|union|interface)\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val JS_FUNCTION = Regex("^\\s*(?:export\\s+)?(?:async\\s+)?function\\s+([A-Za-z_$][A-Za-z0-9_$]*)")
    private val JS_ASSIGNED = Regex("^\\s*(?:export\\s+)?(?:const|let|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(?:async\\s*)?(?:\\(|function)")
    private val GO_FUNC = Regex("^func\\s+(?:\\([^)]*\\)\\s*)?([A-Za-z_][A-Za-z0-9_]*)")
    private val GO_TYPE = Regex("^type\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val RUST_FN = Regex("^\\s*(?:pub\\s+)?(?:async\\s+)?fn\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val RUBY_DEF = Regex("^\\s*def\\s+(?:self\\.)?([A-Za-z_][A-Za-z0-9_?!]*)")
    private val RUBY_CLASS = Regex("^\\s*(?:class|module)\\s+([A-Za-z_][A-Za-z0-9_:]*)")
    private val PHP_FUNCTION = Regex("^\\s*(?:public\\s+|private\\s+|protected\\s+|static\\s+)*function\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val SH_FUNCTION = Regex("^\\s*(?:function\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\(\\)\\s*\\{")
    private val LUA_FUNCTION = Regex("^\\s*(?:local\\s+)?function\\s+([A-Za-z_][A-Za-z0-9_.:]*)")
    private val SWIFT_FUNC = Regex("^\\s*(?:public\\s+|private\\s+|internal\\s+|open\\s+|static\\s+|class\\s+|override\\s+)*func\\s+([A-Za-z_][A-Za-z0-9_]*)")
    private val DART_DECL = Regex("^\\s*(?:[A-Za-z_][A-Za-z0-9_<>,? ]*\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^;]*\\)\\s*(?:async\\s*)?\\{?\\s*$")
    private val SQL_TABLE = Regex("^\\s*CREATE\\s+(?:TABLE|VIEW)\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Za-z_][A-Za-z0-9_]*)", RegexOption.IGNORE_CASE)

    private fun rulesFor(ext: String): List<Regex>? = when (ext) {
        "py" -> listOf(PY_DEF, PY_CLASS)
        "kt", "kts" -> listOf(KOTLIN_FUN, CLASS_DECL, OBJECT_DECL)
        "java" -> listOf(CLASS_DECL, OBJECT_DECL)
        "c", "h", "cpp", "hpp", "cc", "cxx" -> listOf(C_FUNC)
        "js", "mjs", "cjs", "ts", "tsx", "jsx" -> listOf(JS_FUNCTION, CLASS_DECL, JS_ASSIGNED)
        "go" -> listOf(GO_FUNC, GO_TYPE)
        "rs" -> listOf(RUST_FN, STRUCT_DECL)
        "rb" -> listOf(RUBY_DEF, RUBY_CLASS)
        "php" -> listOf(PHP_FUNCTION, CLASS_DECL)
        "sh", "bash", "zsh" -> listOf(SH_FUNCTION)
        "lua" -> listOf(LUA_FUNCTION)
        "sql" -> listOf(SQL_TABLE)
        "swift" -> listOf(SWIFT_FUNC, CLASS_DECL)
        "dart" -> listOf(DART_DECL, CLASS_DECL)
        else -> null
    }
}
