package com.codeci.ide.ui.editor

import java.io.File

/**
 * Phase 55.3 — **Find across the project**, behind the side panel's Search
 * slot (`Screenshot_20260922_124052`: one field reading `Find Text`, the five
 * option glyphs, a `RESULTS` header, and — with nothing typed — nothing at
 * all).
 *
 * Written as a **pure, host-tested** engine on purpose (rule.md §4.4): the
 * panel is Compose and cannot be unit-tested in the sandbox, so the part that
 * can be wrong in a way a phone would not forgive — finding the wrong lines,
 * hanging on a huge file, walking outside the project — is here, where
 * `ProjectSearchTest` can pin it.
 *
 * Laws:
 *  - **The project root is the boundary.** [search] only ever reads files
 *    under the root it was given, and never follows a symlink out of it.
 *  - **Bounded work.** A file bigger than [MAX_FILE_BYTES] is skipped, at most
 *    [MAX_HITS] hits are returned, and a binary-looking file is not searched.
 *    A search that freezes the editor is worse than a search that stops.
 *  - **No new dependency.** Plain `java.io.File` + Kotlin regex; nothing here
 *    imports Android (`java.nio.file` is banned in pure files — Phase 44's
 *    lint lesson), so this class compiles and runs on the host JVM.
 *  - **The engine never yields null results for “empty”.** No query → no hits,
 *    which is exactly the empty `RESULTS` state the shot shows.
 */
object ProjectSearch {

    /** Files larger than this are skipped (a 20 MB log is not a source file). */
    const val MAX_FILE_BYTES: Long = 512L * 1024L

    /** The most hits the panel will ever show. */
    const val MAX_HITS: Int = 200

    /** Directories that are never searched. */
    val SKIPPED_DIRS: Set<String> = setOf(".git", "node_modules", "build", ".gradle", "bin", "dist")

    /** A hit is one line of one file. [column] is 1-based, like the status bar. */
    data class Hit(
        val relativePath: String,
        val line: Int,
        val column: Int,
        val text: String
    )

    /** The five glyphs of the shot's header, as data. */
    data class Options(
        val regex: Boolean = false,
        val caseSensitive: Boolean = false,
        val wholeWord: Boolean = false
    )

    /**
     * Search [relativePath]'s file under [root]. Returns an empty list when the
     * file is missing, too large, binary, or holds no match — the panel shows
     * the same empty `RESULTS` for all four, because “nothing found” is the
     * truth in every one of them.
     */
    fun searchFile(root: File, relativePath: String, query: String, options: Options = Options()): List<Hit> {
        if (query.isBlank()) return emptyList()
        val safe = ProjectPathGuard.childOf(root, relativePath) ?: return emptyList()
        if (!safe.isFile || safe.length() > MAX_FILE_BYTES) return emptyList()
        val text = runCatching { safe.readText() }.getOrNull() ?: return emptyList()
        if (looksBinary(text)) return emptyList()
        return matchesIn(text, query, options, relativePath)
    }

    /**
     * Search the whole project, depth-first, in a stable order (files and
     * folders sorted by name, case-insensitively) so results do not jump
     * between runs. Stops the moment [MAX_HITS] is reached.
     */
    fun search(root: File, query: String, options: Options = Options(), limit: Int = MAX_HITS): List<Hit> {
        if (query.isBlank() || !root.isDirectory) return emptyList()
        val hits = mutableListOf<Hit>()
        walk(root, root, query, options, limit, hits)
        return hits
    }

    private fun walk(
        root: File,
        dir: File,
        query: String,
        options: Options,
        limit: Int,
        into: MutableList<Hit>
    ) {
        if (into.size >= limit) return
        val children = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: return
        for (child in children) {
            if (into.size >= limit) return
            if (child.isDirectory) {
                if (child.name in SKIPPED_DIRS || child.name.startsWith(".")) continue
                if (isSymlink(child)) continue
                walk(root, child, query, options, limit, into)
            } else {
                if (!isSearchable(child.name)) continue
                val relative = child.relativeTo(root).path.replace(File.separatorChar, '/')
                val remaining = limit - into.size
                into += searchFile(root, relative, query, options).take(remaining)
            }
        }
    }

    /** Line-by-line matching for one file's text. Pure; the panel's unit. */
    fun matchesIn(
        text: String,
        query: String,
        options: Options = Options(),
        relativePath: String = "",
        limit: Int = MAX_HITS
    ): List<Hit> {
        if (query.isEmpty() || text.isEmpty()) return emptyList()
        val pattern = patternFor(query, options) ?: return emptyList()
        val hits = mutableListOf<Hit>()
        var lineNumber = 0
        var start = 0
        while (start <= text.length && hits.size < limit) {
            val newline = text.indexOf('\n', start)
            val end = if (newline < 0) text.length else newline
            val line = text.substring(start, end).trimEnd('\r')
            lineNumber++
            for (match in pattern.findAll(line)) {
                if (hits.size >= limit) break
                hits += Hit(
                    relativePath = relativePath,
                    line = lineNumber,
                    column = match.range.first + 1,
                    text = line.trim()
                )
            }
            if (newline < 0) break
            start = newline + 1
        }
        return hits
    }

    /**
     * The query as a [Regex], or null when a regex option is on and the user is
     * still typing an invalid one (the panel then shows nothing rather than a
     * crash or a red wall — a half-typed pattern is not an error message).
     */
    fun patternFor(query: String, options: Options): Regex? {
        val literal = if (options.regex) query else Regex.escape(query)
        val body = if (options.wholeWord) "\\b(?:$literal)\\b" else literal
        val flags = if (options.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return runCatching { Regex(body, flags) }.getOrNull()
    }

    /** Text files only, by extension; the panel is a code search, not a disk scan. */
    fun isSearchable(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return false
        return name.substring(dot + 1).lowercase() in TEXT_EXTENSIONS
    }

    private val TEXT_EXTENSIONS: Set<String> = setOf(
        "c", "h", "cpp", "hpp", "cc", "cxx", "py", "js", "mjs", "cjs", "ts", "tsx", "jsx",
        "html", "htm", "css", "scss", "sass", "less", "json", "jsonc", "xml", "yml", "yaml",
        "md", "markdown", "txt", "text", "sh", "bash", "zsh", "kt", "kts", "java", "rs", "go",
        "rb", "php", "sql", "toml", "ini", "cfg", "conf", "csv", "tsv", "gradle", "properties",
        "gitignore", "env", "lock", "svg", "lua", "pl", "r", "swift", "dart", "vb"
    )

    /** NUL in the first 8 KB means “not text” — the classic cheap check. */
    fun looksBinary(text: String): Boolean {
        val sample = text.take(8192)
        return sample.any { it == '\u0000' }
    }

    private fun isSymlink(file: File): Boolean =
        runCatching { file.canonicalPath != file.absolutePath }.getOrDefault(false)
}

/**
 * The panel's path guard: a hit may only be read from **inside the project**.
 * `File(root, "../…")` and an absolute path both resolve outside; both are
 * refused here rather than in the caller (one truth, host-tested).
 */
object ProjectPathGuard {

    /** [relative] resolved under [root], or null when it escapes or is absolute. */
    fun childOf(root: File, relative: String): File? {
        if (relative.isBlank()) return null
        if (File(relative).isAbsolute) return null
        if (relative.split('/', '\\').any { it == ".." }) return null
        val child = File(root, relative)
        val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return null
        val childPath = runCatching { child.canonicalPath }.getOrNull() ?: return null
        return if (childPath == rootPath || childPath.startsWith("$rootPath${File.separator}")) child else null
    }
}
