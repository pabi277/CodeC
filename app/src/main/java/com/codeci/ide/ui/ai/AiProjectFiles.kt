package com.codeci.ide.ui.ai

import com.codeci.ide.ui.editor.ProjectFilesPolicy

/**
 * Phase 78 (AI Level 2) — **which project files may become AI context, and how
 * much of them.** Pure: strings, numbers and lists, no `java.io`, no Android.
 * The Android side ([AiProjectReader]) walks and reads; this object decides.
 *
 * ## Why this is NOT [com.codeci.ide.ui.editor.ProjectSearch.isSearchable]
 *
 * The editor's search filter is a *find-my-code* filter, and it deliberately
 * admits configuration the user wants to search: `ProjectFilesPolicy.configNames`
 * contains `.env` and `.npmrc` (`ProjectFilesPolicy.kt:9-12`), `usefulConfig`
 * matches anything starting `.env.` (`:13`), and `TEXT_EXTENSIONS` itself lists
 * `"env"` (`ProjectSearch.kt:172`). Reusing it for AI context would put API
 * keys and npm tokens in a request body going to a cloud provider.
 *
 * So Level 2 has its own, **narrower** filter, and secret-like names are
 * refused outright — there is no opt-in (owner Q3, answered conservatively by
 * the agent 2026-10-01: never offered, not "excluded but offered").
 *
 * ## Budgets (owner Q2, answered conservatively by the agent)
 *
 * [AiLimits.MAX_CONTEXT_CHARS] stays at 12 000 — the value the Phase 76 device
 * round validated is not touched. A whole project cannot fit in it, so the
 * answer is **relevance plus ranges, never "send everything"**: at most
 * [MAX_FILES] files, at most [MAX_FILE_CHARS] characters of each, and the
 * total packed body never exceeds [AiLimits.MAX_CONTEXT_CHARS]. When the
 * project is bigger than the budget the result says so ([Plan.truncated]) and
 * the preview shows the paths that were left out — nothing is dropped silently
 * (`02_WHOLE_PROJECT_CONTEXT.md`: *"report when the project is too large
 * rather than silently dropping important information"*).
 *
 * ## What is deliberately absent
 *
 * No embeddings, no vector store, no index, no persisted state (D6). Ranking is
 * deterministic keyword overlap plus a few file-shape rules, so the same
 * project and the same question always produce the same file list — which is
 * what makes the preview honest (D4).
 */
object AiProjectFiles {

    /** How many files one request may carry. */
    const val MAX_FILES = 5

    /** How many characters of any one file. */
    const val MAX_FILE_CHARS = 3_000

    /**
     * How many directory entries the walk may visit before it stops looking.
     * A phone project is hundreds of files; this bounds a pathological tree
     * (`node_modules` is pruned, but a user can invent their own).
     */
    const val MAX_ENTRIES = 4_000

    /** How many characters are read from a shortlisted file (before packing). */
    const val MAX_READ_CHARS = 24_000

    /** How many shortlisted files are read before the final pack. */
    const val READ_SHORTLIST = 12

    /** Why a path did not make it into the request. Every value is shown to the user. */
    enum class Exclusion {
        /** Credential-shaped. Refused outright; never offered as an option. */
        SECRET,

        /** Not a text/code extension this filter recognises. */
        NOT_TEXT,

        /** Read as binary (NUL in the head). */
        BINARY,

        /** Recognised, but the budget went to more relevant files first. */
        BUDGET
    }

    // ---- exclusion --------------------------------------------------------

    /**
     * Credential-shaped names. Matched on the **file name only**, lower-cased,
     * so it cannot be dodged by nesting (`config/prod/.env` is still `.env`).
     * The list is intentionally over-inclusive: a false positive costs the
     * user one file of context, a false negative sends a secret to Google.
     */
    private val SECRET_NAMES = setOf(
        ".env", ".npmrc", ".netrc", ".pypirc", ".git-credentials", ".htpasswd",
        "credentials", "credentials.json", "service_account.json", "secrets.json",
        "secrets.yaml", "secrets.yml", "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519",
        "debug.keystore", "release.keystore", "google-services.json"
    )

    private val SECRET_PREFIXES = listOf(".env.", "id_rsa", "id_ed25519", "service-account", "secret")

    private val SECRET_SUFFIXES = listOf(
        ".pem", ".p12", ".pfx", ".key", ".keystore", ".jks", ".asc", ".gpg", ".ppk"
    )

    /** True when the name looks like a credential store. No opt-in exists. */
    fun isSecretLike(name: String): Boolean {
        val n = name.lowercase()
        if (n in SECRET_NAMES) return true
        if (SECRET_PREFIXES.any { n.startsWith(it) }) return true
        if (SECRET_SUFFIXES.any { n.endsWith(it) }) return true
        return false
    }

    /**
     * Extensions this filter treats as code/text. Deliberately **narrower**
     * than `ProjectSearch.TEXT_EXTENSIONS`: no `env`, no `lock`, no `csv`/`tsv`
     * data files, no `properties` (Android `local.properties` carries SDK paths
     * and, in some projects, signing passwords).
     */
    private val TEXT_EXTENSIONS = setOf(
        "c", "h", "cpp", "hpp", "cc", "cxx", "py", "js", "mjs", "cjs", "ts", "tsx", "jsx",
        "html", "htm", "css", "scss", "sass", "less", "json", "jsonc", "xml", "yml", "yaml",
        "md", "markdown", "txt", "sh", "bash", "zsh", "kt", "kts", "java", "rs", "go",
        "rb", "php", "sql", "toml", "ini", "cfg", "conf", "gradle", "lua", "pl", "r",
        "swift", "dart", "vb", "s", "asm", "mk"
    )

    /**
     * Extension-less names that are still worth reading. `.gitignore` is here
     * but `.env` is **not** — [isSecretLike] runs first and wins.
     */
    private val TEXT_NAMES = setOf(
        "makefile", "dockerfile", "rakefile", "gemfile", "cmakelists.txt",
        "license", "readme", "changelog", ".gitignore", ".gitattributes", ".editorconfig"
    )

    /** A directory the walk never enters. Reuses the editor's build-output set. */
    fun isExcludedDirectory(name: String): Boolean =
        name in ProjectFilesPolicy.excludedDirectories || (name.startsWith(".") && name !in setOf(".github", ".vscode"))

    /**
     * The single exclusion question, in the order it must be asked: secret
     * first, so a credential can never be re-admitted by a later rule.
     * [binary] is null when the file was not read (path-only pass).
     */
    fun exclusionFor(name: String, binary: Boolean?, chars: Int): Exclusion? = when {
        isSecretLike(name) -> Exclusion.SECRET
        !isTextFile(name) -> Exclusion.NOT_TEXT
        binary == true -> Exclusion.BINARY
        chars <= 0 -> Exclusion.NOT_TEXT
        else -> null
    }

    /** Text/code by extension, or one of the known extension-less names. */
    fun isTextFile(name: String): Boolean {
        val n = name.lowercase()
        if (n in TEXT_NAMES) return true
        val dot = n.lastIndexOf('.')
        if (dot <= 0 || dot == n.length - 1) return false
        return n.substring(dot + 1) in TEXT_EXTENSIONS
    }

    // ---- relevance --------------------------------------------------------

    /**
     * The words a question is worth matching on. Lower-cased, letters/digits
     * only, three characters or longer, so "what does area() do in main.c"
     * becomes `does`, `area`, `main` — `what` and `in` are too short to mean
     * anything and only add noise. Deterministic: a `Set` sorted for order.
     */
    fun keywords(question: String): List<String> =
        Regex("[a-z0-9_]{3,}").findAll(question.lowercase())
            .map { it.value }
            .filter { it !in STOP_WORDS }
            .distinct()
            .sorted()
            .toList()

    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "this", "that", "what", "why", "how", "where",
        "which", "does", "did", "was", "were", "are", "you", "your", "can", "could",
        "should", "would", "about", "into", "from", "have", "has", "had", "not", "all",
        "any", "its", "they", "them", "code", "file", "files", "project", "please"
    )

    /**
     * A whole-file relevance score. Deliberately crude and deterministic:
     *
     * - the file the user is **looking at** ranks first (they asked about
     *   something they can see);
     * - question words in the **path** beat question words in the body, because
     *   a path match is about what the file *is*;
     * - a small body bonus so a 40-line helper outranks a 900-line dump;
     * - build/entry-point files get a flat nudge, since "where does this start"
     *   is the most common project-level question.
     *
     * No embeddings, no caching — recomputed per request (D6).
     */
    fun relevance(relativePath: String, text: String, words: List<String>, isOpenFile: Boolean): Int {
        val path = relativePath.lowercase()
        val body = text.lowercase()
        var score = 0
        for (w in words) {
            if (path.contains(w)) score += 60
            val hits = countOccurrences(body, w)
            if (hits > 0) score += (10 + minOf(hits, 8) * 2)
        }
        if (isOpenFile) score += 200
        if (text.length in 1..4_000) score += 15
        else if (text.length > 4_000) score += 5
        if (ENTRY_POINTS.any { path.endsWith(it) }) score += 25
        return score
    }

    /** Path suffixes that usually answer "where does this program start". */
    private val ENTRY_POINTS = listOf(
        "main.c", "main.cpp", "main.py", "main.js", "main.ts", "main.go", "main.rs",
        "main.java", "main.kt", "index.js", "index.ts", "index.html", "app.py",
        "makefile", "cmakelists.txt", "package.json", "build.gradle", "build.gradle.kts"
    )

    /**
     * The cheap pass, before anything is read from disk: rank paths by name
     * alone and keep the [limit] most promising. A phone project can hold
     * thousands of files and reading all of them to find five is the wrong
     * trade, so the body is only read for this shortlist (plus the open file).
     *
     * The open file is always in the result when present — the user is looking
     * at it, so it is never shortlisted away.
     */
    fun shortlist(paths: List<String>, question: String, openPath: String?, limit: Int): List<String> {
        val words = keywords(question)
        val normalisedOpen = openPath?.trim()?.takeIf { it.isNotEmpty() }?.replace('\\', '/')
        val open = paths.firstOrNull { p -> normalisedOpen != null && samePath(p, normalisedOpen) }
        val ranked = paths.sortedWith(
            compareByDescending<String> { p -> pathScore(p, words) }
                .thenBy { it.length }
                .thenBy { it }
        )
        val out = LinkedHashSet<String>(limit + 1)
        if (open != null) out += open
        for (p in ranked) {
            if (out.size >= limit) break
            out += p
        }
        return out.toList()
    }

    /**
     * Path-only relevance: name matches dominate, entry-point names get the
     * same nudge [relevance] gives, and shallower paths win ties (a top-level
     * `main.c` is a better guess than `vendor/old/main.c`).
     */
    fun pathScore(relativePath: String, words: List<String>): Int {
        val path = relativePath.lowercase()
        var score = 0
        for (w in words) if (path.contains(w)) score += 60
        if (ENTRY_POINTS.any { path.endsWith(it) }) score += 25
        score -= (relativePath.count { it == '/' } * 3)
        return score
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var from = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) return count
            count++
            from = at + needle.length
        }
    }

    // ---- the plan ---------------------------------------------------------

    /** One file the reader offers. [text] is already capped at [MAX_READ_CHARS]. */
    data class Candidate(
        val relativePath: String,
        val text: String,
        val lines: Int,
        /** True when this is the editor's live buffer, not what is on disk. */
        val fromBuffer: Boolean,
        /** True when the file was cut at [MAX_READ_CHARS]. */
        val readCut: Boolean
    )

    /** One file that made it into the request body, with exactly what is sent. */
    data class Included(
        val relativePath: String,
        /** The text that is in the body — never longer than [MAX_FILE_CHARS]. */
        val text: String,
        val linesSent: Int,
        val linesInFile: Int,
        val fromBuffer: Boolean,
        val cut: Boolean
    )

    /**
     * The whole decision, in a shape the preview can render line for line.
     * [leftOut] is every candidate that did not fit, with its reason — the
     * user sees what was not sent (`02_WHOLE_PROJECT_CONTEXT.md`: *"Let users
     * inspect included paths and exclude files/folders"*).
     */
    data class Plan(
        val included: List<Included>,
        val leftOut: List<Pair<String, Exclusion>>,
        val candidatesOffered: Int,
        /** True when at least one readable file was dropped for budget. */
        val truncated: Boolean
    ) {
        /** Nothing usable: the caller refuses rather than sending a bare question. */
        val isEmpty: Boolean get() = included.isEmpty()
    }

    /**
     * Picks the files and packs them. Order: the open file first, then
     * descending relevance. The budget is enforced on the **rendered body**, so
     * [AiProjectContext.body] of the result is guaranteed to be at most
     * [AiLimits.MAX_CONTEXT_CHARS] — the same ceiling `fromSelection` refuses
     * to exceed.
     */
    fun plan(candidates: List<Candidate>, question: String, openPath: String?): Plan {
        val words = keywords(question)
        val normalisedOpen = openPath?.trim()?.takeIf { it.isNotEmpty() }?.replace('\\', '/')
        val scored = candidates.map { c ->
            c to relevance(
                relativePath = c.relativePath,
                text = c.text,
                words = words,
                isOpenFile = normalisedOpen != null && samePath(c.relativePath, normalisedOpen)
            )
        }
        // Stable: relevance first, then path, so equal scores never reorder
        // between two taps on the same project.
        val ranked = scored.sortedWith(compareByDescending<Pair<Candidate, Int>> { it.second }.thenBy { it.first.relativePath })

        val included = mutableListOf<Included>()
        val leftOut = mutableListOf<Pair<String, Exclusion>>()
        var used = 0
        for ((c, _) in ranked) {
            if (included.size >= MAX_FILES) {
                leftOut += c.relativePath to Exclusion.BUDGET
                continue
            }
            val slice = sliceFor(c, budgetLeft = AiLimits.MAX_CONTEXT_CHARS - used - headerCost(c.relativePath))
            if (slice == null) {
                leftOut += c.relativePath to Exclusion.BUDGET
                continue
            }
            included += slice
            used += headerCost(c.relativePath) + slice.text.length + FOOTER_COST
        }
        return Plan(
            included = included,
            leftOut = leftOut,
            candidatesOffered = candidates.size,
            truncated = leftOut.any { it.second == Exclusion.BUDGET }
        )
    }

    /**
     * The slice of one file that still fits. A file is worth sending only if a
     * meaningful head of it fits — a 200-character fragment of a 900-line file
     * would be answered as if it were the whole file, which is the exact
     * failure `fromSelection` refuses. Below [MIN_USEFUL_CHARS] the file is
     * left out instead, and the preview says so.
     */
    const val MIN_USEFUL_CHARS = 240

    private const val FOOTER_COST = 8

    /** `--- path (N lines) ---` plus the newline before the body. */
    private fun headerCost(relativePath: String): Int = relativePath.length + 24

    /**
     * The slice of one file that still fits — and, since Phase 94, the one place a
     * packed file's **values** are checked against [AiSecretScan]. The guard runs
     * here, before the budget is measured, so the one-request ceiling still holds
     * exactly and the preview shows precisely what will be sent; the candidate
     * itself is untouched, so the edit parser's baseline keeps the raw bytes.
     */
    private fun sliceFor(c: Candidate, budgetLeft: Int): Included? {
        val room = minOf(MAX_FILE_CHARS, budgetLeft)
        if (room < MIN_USEFUL_CHARS) return null
        val text = AiSecretScan.redact(c.text).text
        if (text.length <= room) {
            return Included(c.relativePath, text, c.lines, c.lines, c.fromBuffer, c.readCut)
        }
        // Take whole lines only — never cut a line in half.
        var end = 0
        var lines = 0
        var at = 0
        while (at < text.length) {
            val nl = text.indexOf('\n', at)
            val lineEnd = if (nl < 0) text.length else nl
            if (lineEnd > room) break
            end = lineEnd
            lines++
            if (nl < 0) break
            at = nl + 1
        }
        if (end < MIN_USEFUL_CHARS) return null
        return Included(c.relativePath, text.substring(0, end), lines, c.lines, c.fromBuffer, true)
    }

    /** Project-relative paths, tolerant of a leading `./` and of separator style. */
    fun samePath(a: String, b: String): Boolean {
        fun norm(s: String) = s.replace('\\', '/').removePrefix("./").trim('/')
        return norm(a) == norm(b)
    }
}
