package com.codeci.ide.ui.projects

import java.io.File

/**
 * Phase 39.2 — one ignore table, enforced at the choke point.
 *
 * Pattern names are the intersection of what CodeC can produce/run on a
 * phone with the `github/gitignore` templates for those languages
 * (CC0-1.0 — public domain; the pattern *names* are what we take, not
 * the template files; see `assets/licenses/GITHUB_GITIGNORE_CC0.txt`).
 *
 * Laws (from PART_39_2 / the phase README):
 *  1. Never edit the user's `.gitignore`. Rules go to `.git/info/exclude`.
 *  2. A pattern the user already has (in either file) is not duplicated —
 *     the user's own file always wins, including a `!a.out` negation.
 *  3. Enforcement lives inside [GitManager.stageAll], not at a caller's
 *     good intentions: ensure → untrack tracked violations → `git add -A`.
 *  4. Deletion from git (`git rm --cached`) leaves the file on disk.
 *
 * [BuildArtifactIgnore] and [PythonCacheIgnore] are thin delegates that
 * forward here so the three pre-39 call sites keep compiling; new code
 * should call [RepoHygiene] directly.
 */
object RepoHygiene {

    data class Pattern(
        val pattern: String,
        val reason: String,
        val langs: Set<String>,
    )

    data class Explanation(
        val path: String,
        val matched: Boolean,
        val pattern: String?,
        val reason: String?,
        val source: String?, // ".git/info/exclude" | ".gitignore" | "CodeC table" | null
    )

    data class HygieneResult(
        /** Patterns newly written to `.git/info/exclude` this call. */
        val addedPatterns: List<String>,
        /** Tracked paths that were `git rm --cached`'d. */
        val untracked: List<String>,
    ) {
        val changed: Boolean get() = addedPatterns.isNotEmpty() || untracked.isNotEmpty()

        /** One-line sheet message when [untracked] is non-empty. */
        fun userMessage(): String? {
            if (untracked.isEmpty()) return null
            val n = untracked.size
            return if (n == 1) {
                "Removed 1 build output from the repo (it stays on your phone and in .git/info/exclude)"
            } else {
                "Removed $n build outputs from the repo (they stay on your phone and in .git/info/exclude)"
            }
        }
    }

    data class CommitPreview(
        val staged: List<StagedEntry>,
        val truncated: Boolean,
        val total: Int,
        val hygieneNote: String? = null,
    ) {
        data class StagedEntry(val status: String, val path: String)
    }

    /** A human-readable note naming why the lines exist (not parsed). */
    const val NOTE = "# CodeC: build/run outputs and IDE metadata stay out of git (Phase 39)"

    /**
     * ~60 patterns in named groups. Trailing `/` = directories only (git
     * semantics). The golden host test pins this list so a pattern cannot
     * be dropped silently.
     */
    val EXCLUDE_LINES: List<Pattern> = listOf(
        // ---- C / C++ ----
        Pattern("*.out", "C/C++ link output (a.out and friends)", setOf("c", "cpp")),
        Pattern("*.o", "C/C++ object file", setOf("c", "cpp")),
        Pattern("*.obj", "C/C++ object file (MSVC-style)", setOf("c", "cpp")),
        Pattern("*.exe", "Windows executable", setOf("c", "cpp")),
        Pattern("*.so", "shared library", setOf("c", "cpp")),
        Pattern("*.a", "static library archive", setOf("c", "cpp")),
        Pattern("*.d", "compiler dependency file", setOf("c", "cpp")),
        Pattern("*.dylib", "macOS shared library", setOf("c", "cpp")),
        Pattern("bin/", "CodeC/project binary output dir", setOf("c", "cpp")),
        Pattern("CMakeFiles/", "CMake build tree", setOf("c", "cpp")),
        Pattern("CMakeCache.txt", "CMake cache", setOf("c", "cpp")),
        Pattern("cmake-build-*/", "CLion/CMake build dir", setOf("c", "cpp")),
        Pattern("compile_commands.json", "clangd compilation database (regenerable)", setOf("c", "cpp")),
        // ---- Python ----
        Pattern("__pycache__/", "Python 3 bytecode cache", setOf("python")),
        Pattern("*.pyc", "Python bytecode", setOf("python")),
        Pattern("*.pyo", "Python optimized bytecode", setOf("python")),
        Pattern("*.pyd", "Python extension module", setOf("python")),
        Pattern(".venv/", "Python virtualenv (dot form)", setOf("python")),
        Pattern("venv/", "Python virtualenv", setOf("python")),
        Pattern(".pytest_cache/", "pytest cache", setOf("python")),
        Pattern(".mypy_cache/", "mypy cache", setOf("python")),
        Pattern(".ruff_cache/", "ruff cache", setOf("python")),
        Pattern(".coverage", "coverage.py data file", setOf("python")),
        Pattern("htmlcov/", "coverage.py HTML report", setOf("python")),
        Pattern("*.egg-info/", "setuptools metadata", setOf("python")),
        Pattern(".tox/", "tox env root", setOf("python")),
        // ---- Node / JS / TS ----
        Pattern("node_modules/", "npm/yarn/pnpm packages", setOf("node", "js", "ts")),
        Pattern("npm-debug.log*", "npm debug log", setOf("node", "js")),
        Pattern("yarn-error.log*", "yarn error log", setOf("node", "js")),
        Pattern("dist/", "JS/TS build output", setOf("node", "js", "ts")),
        Pattern(".next/", "Next.js build", setOf("node", "js", "ts")),
        Pattern(".nuxt/", "Nuxt build", setOf("node", "js", "ts")),
        Pattern(".output/", "Nitro/Nuxt output", setOf("node", "js", "ts")),
        Pattern(".parcel-cache/", "Parcel cache", setOf("node", "js")),
        Pattern(".svelte-kit/", "SvelteKit build", setOf("node", "js", "ts")),
        Pattern(".turbo/", "Turborepo cache", setOf("node", "js", "ts")),
        Pattern("*.tsbuildinfo", "TypeScript incremental build info", setOf("ts")),
        // ---- Java / Kotlin ----
        Pattern("*.class", "JVM class file", setOf("java", "kotlin")),
        Pattern("build/", "Gradle/Maven/generic build dir", setOf("java", "kotlin", "c", "cpp")),
        Pattern("target/", "Maven/Cargo build dir", setOf("java", "kotlin", "rust")),
        Pattern(".gradle/", "Gradle cache", setOf("java", "kotlin")),
        Pattern("out/", "IntelliJ/generic output dir", setOf("java", "kotlin")),
        // ---- Go / Rust (trivial; CodeC may run them via packages) ----
        Pattern("*.exe~", "Go build leftover", setOf("go")),
        // ---- Lua ----
        Pattern("*.luac", "Lua bytecode", setOf("lua")),
        // ---- Docs / tests / coverage (generic) ----
        Pattern(".cache/", "generic tool cache", setOf("docs")),
        // ---- OS / editor junk ----
        Pattern(".DS_Store", "macOS folder metadata", setOf("os")),
        Pattern("Thumbs.db", "Windows thumbnail cache", setOf("os")),
        Pattern("Desktop.ini", "Windows folder config", setOf("os")),
        Pattern("*.swp", "Vim swap file", setOf("os")),
        Pattern("*.swo", "Vim swap file", setOf("os")),
        Pattern("*~", "editor backup file", setOf("os")),
        Pattern(".*.swp", "hidden Vim swap", setOf("os")),
        // ---- CodeC's own files ----
        Pattern(".codec/", "CodeC project metadata (run config, launch default)", setOf("codec")),
        Pattern(".codec.json", "CodeC per-project run override", setOf("codec")),
        Pattern(".codec-tmp/", "CodeC scratch directory inside a project", setOf("codec")),
        Pattern("codec-*.tmp", "CodeC scratch file inside a project", setOf("codec")),
    )

    /** Just the pattern strings, for callers that want the flat list. */
    val PATTERNS: List<String> = EXCLUDE_LINES.map { it.pattern }

    /** Trimmed, non-empty, non-comment lines — the patterns git would see. */
    fun splitLines(content: String?): List<String> =
        content?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            .orEmpty()

    /** True when [lines] already contain the exact pattern (user's file wins). */
    private fun covered(lines: List<String>, pattern: String): Boolean =
        lines.any { it == pattern }

    /**
     * True when the user's rules contain a negation that re-includes
     * [pattern] (e.g. `!a.out` for pattern `*.out` / a literal `a.out`).
     * When present, CodeC must NOT add the positive pattern to exclude —
     * the user wants that path tracked.
     */
    fun userWantsTracked(gitignoreLines: List<String>, pattern: String): Boolean {
        val bare = pattern.removePrefix("!")
        return gitignoreLines.any { line ->
            if (!line.startsWith("!")) return@any false
            val neg = line.removePrefix("!").trim()
            neg == bare || neg == pattern ||
                (pattern.startsWith("*.") && neg.endsWith(pattern.removePrefix("*"))) ||
                (pattern.endsWith("/") && (neg == pattern || neg == pattern.dropLast(1)))
        }
    }

    /**
     * Patterns still uncovered by both the exclude file and the project's
     * `.gitignore`. A `.gitignore` with `!a.out` (wanting it tracked) keeps
     * the matching positive pattern OUT of the missing list.
     */
    fun missingLines(existingExclude: String?, gitignore: String?): List<Pattern> {
        val exclude = splitLines(existingExclude)
        val ignore = splitLines(gitignore)
        return EXCLUDE_LINES.filter { p ->
            !covered(exclude, p.pattern) &&
                !covered(ignore, p.pattern) &&
                !userWantsTracked(ignore, p.pattern)
        }
    }

    /** New exclude-file content: existing bytes + note + the missing lines. */
    fun appendTo(existingExclude: String?, lines: List<Pattern>): String {
        if (lines.isEmpty()) return existingExclude.orEmpty()
        val base = existingExclude.orEmpty()
        val sep = if (base.isEmpty() || base.endsWith("\n")) "" else "\n"
        return buildString {
            append(base)
            append(sep)
            append(NOTE).append('\n')
            lines.forEach { append(it.pattern).append('\n') }
        }
    }

    /**
     * Best-effort, safe from any thread: when the repo does not cover the
     * patterns yet, write the private exclude file. Non-repos, read-only
     * sandboxes and every other surprise are swallowed — git operations
     * must never fail because of this helper.
     *
     * @return the patterns newly written (empty when nothing changed).
     */
    fun ensure(projectRoot: File): List<String> {
        return runCatching {
            val gitDir = resolveGitDir(projectRoot)
            if (gitDir == null) return@runCatching emptyList<String>()
            val exclude = File(gitDir, "info/exclude")
            val existing = runCatching { if (exclude.isFile) exclude.readText() else null }.getOrNull()
            val gitignore = runCatching {
                File(projectRoot, ".gitignore").takeIf { it.isFile }?.readText()
            }.getOrNull()
            val missing = missingLines(existing, gitignore)
            if (missing.isEmpty()) return@runCatching emptyList<String>()
            exclude.parentFile?.mkdirs()
            exclude.writeText(appendTo(existing, missing))
            missing.map { it.pattern }
        }.getOrDefault(emptyList())
    }

    /**
     * True when [relativePath] matches any of the table patterns, using a
     * git-ish approximation (trailing `/` = directory prefix; `*.ext` =
     * suffix; `foo*` simple glob; exact / basename match otherwise).
     * Precision when it matters comes from `git check-ignore -v` via
     * [explain]; this is the pre-filter used by untrack and the golden tests.
     */
    fun matches(relativePath: String): Boolean =
        matchingPattern(relativePath) != null

    fun matchingPattern(relativePath: String): Pattern? {
        val normalized = normalize(relativePath)
        if (normalized.isEmpty()) return null
        return EXCLUDE_LINES.firstOrNull { patternMatches(normalized, it.pattern) }
    }

    /**
     * Why isn't [relativePath] going into the next commit? Prefers the
     * CodeC table; when [gitignoreText] already covers it, says so.
     * Callers that have a live git can refine with `git check-ignore -v`.
     */
    fun explain(relativePath: String, gitignoreText: String? = null, excludeText: String? = null): Explanation {
        val normalized = normalize(relativePath)
        val ignoreLines = splitLines(gitignoreText)
        val excludeLines = splitLines(excludeText)
        // User negation wins: they WANT it tracked.
        val matchedIgnore = ignoreLines.firstOrNull { line ->
            !line.startsWith("!") && patternMatches(normalized, line)
        }
        if (matchedIgnore != null) {
            return Explanation(normalized, true, matchedIgnore, "user .gitignore", ".gitignore")
        }
        val matchedExclude = excludeLines.firstOrNull { patternMatches(normalized, it) }
        if (matchedExclude != null) {
            val reason = EXCLUDE_LINES.firstOrNull { it.pattern == matchedExclude }?.reason
                ?: "listed in .git/info/exclude"
            return Explanation(normalized, true, matchedExclude, reason, ".git/info/exclude")
        }
        val table = matchingPattern(normalized)
        if (table != null) {
            return Explanation(normalized, true, table.pattern, table.reason, "CodeC table")
        }
        return Explanation(normalized, false, null, null, null)
    }

    /** Tracked paths that match the table and should be `git rm --cached`. */
    fun trackedViolations(tracked: List<String>): List<String> =
        tracked.filter { matches(it) }

    /**
     * Untrack files that made it into the index before the patterns existed
     * (e.g. an `a.out` committed and pushed in an earlier round).
     * `git rm --cached` leaves the file on disk; the next commit records the
     * removal. Best-effort — any failure is swallowed when [swallow] is true.
     */
    fun untrackTracked(projectRoot: File, git: GitManager, swallow: Boolean = true): List<String> {
        fun once(): List<String> {
            val tracked = git.trackedFiles(projectRoot) ?: return emptyList()
            val doomed = trackedViolations(tracked)
            if (doomed.isEmpty()) return emptyList()
            git.rmCached(projectRoot, doomed)
            return doomed
        }
        return if (swallow) runCatching { once() }.getOrDefault(emptyList()) else once()
    }

    /**
     * Full ensure + untrack used by [GitManager.stageAll]. Returns a
     * [HygieneResult] so the sheet can show the one-line "Removed N…" note.
     *
     * When [strict] is true, a `git rm --cached` failure aborts (throws) —
     * a half-staged commit is worse than no commit. The default path used
     * from refresh stays best-effort.
     */
    fun prepareForStage(projectRoot: File, git: GitManager, strict: Boolean = true): HygieneResult {
        val added = ensure(projectRoot)
        val untracked = if (strict) {
            untrackTracked(projectRoot, git, swallow = false)
        } else {
            untrackTracked(projectRoot, git, swallow = true)
        }
        return HygieneResult(addedPatterns = added, untracked = untracked)
    }

    /**
     * "What will be committed" projection from a captured
     * `git status --porcelain=v1` (or `-b`) transcript. First [limit] names;
     * renames surface as `old → new`.
     */
    fun commitPreview(porcelainLines: List<String>, limit: Int = 15, hygieneNote: String? = null): CommitPreview {
        val status = GitStatusParser.parse(porcelainLines)
        // Staged = index column (x) is not space and not '?'.
        val staged = status.files.filter { it.x != ' ' && it.x != '?' }
        val entries = staged.map { change ->
            val path = if (change.oldPath != null) {
                "${change.oldPath} → ${change.path}"
            } else {
                change.path
            }
            CommitPreview.StagedEntry(status = change.x.toString(), path = path)
        }
        val shown = entries.take(limit)
        return CommitPreview(
            staged = shown,
            truncated = entries.size > limit,
            total = entries.size,
            hygieneNote = hygieneNote,
        )
    }

    /** `.git` folder, or the `gitdir:` pointer file (linked/worktree repos). */
    fun resolveGitDir(root: File): File? {
        val dot = File(root, ".git")
        return when {
            dot.isDirectory -> dot
            dot.isFile -> {
                val pointer = runCatching { dot.readText().trim() }.getOrDefault("")
                val dir = pointer.removePrefix("gitdir:").trim()
                when {
                    dir.isEmpty() -> null
                    File(dir).isAbsolute -> File(dir)
                    else -> File(root, dir)
                }
            }
            else -> null
        }
    }

    private fun normalize(path: String): String =
        path.replace('\\', '/').removePrefix("./").trimStart('/')

    /**
     * git-ish path match against one pattern. Directory patterns require a
     * trailing `/` on the pattern and match a prefix segment; `*.ext` is a
     * suffix on the final component (so `build.gradle.kts` does NOT match
     * `build/`); `foo*` is a simple prefix glob; everything else is exact
     * or "basename equals".
     */
    internal fun patternMatches(normalizedPath: String, pattern: String): Boolean {
        val path = normalize(normalizedPath)
        val pat = pattern.trim()
        if (pat.isEmpty() || path.isEmpty()) return false
        return when {
            pat.endsWith("/") -> {
                val dir = pat
                path == pat.dropLast(1) ||
                    path.startsWith(dir) ||
                    path.contains("/$dir") ||
                    path.endsWith("/${pat.dropLast(1)}")
            }
            pat.startsWith("*.") -> {
                val suffix = pat.removePrefix("*") // e.g. ".out"
                // Match on the final path segment only, so a folder named
                // `foo.out/bar` does not count as `*.out`.
                val leaf = path.substringAfterLast('/')
                leaf.endsWith(suffix)
            }
            pat.endsWith("*") && !pat.startsWith("*") -> {
                val prefix = pat.dropLast(1)
                val leaf = path.substringAfterLast('/')
                leaf.startsWith(prefix) || path.startsWith(prefix)
            }
            pat.startsWith("*") && pat.endsWith("*") && pat.length > 2 -> {
                val mid = pat.drop(1).dropLast(1)
                path.contains(mid)
            }
            pat.contains("*") -> {
                // Simple glob: only one '*' supported, matched against full path or leaf.
                val parts = pat.split("*", limit = 2)
                if (parts.size != 2) path == pat || path.endsWith("/$pat")
                else {
                    val leaf = path.substringAfterLast('/')
                    (leaf.startsWith(parts[0]) && leaf.endsWith(parts[1])) ||
                        (path.startsWith(parts[0]) && path.endsWith(parts[1]))
                }
            }
            else -> path == pat || path.endsWith("/$pat")
        }
    }
}

/**
 * Thin delegate kept so the three pre-39 call sites
 * (EditorViewModel / FileManagerViewModel / GitControlViewModel) compile
 * without a flag day. New code should call [RepoHygiene] directly.
 * Behaviour is the Phase-39 table, not the old 12-line list.
 */
@Deprecated("Use RepoHygiene", ReplaceWith("RepoHygiene"))
object BuildArtifactIgnore {
    val EXCLUDE_LINES: List<String> get() = RepoHygiene.PATTERNS
    const val NOTE = RepoHygiene.NOTE
    fun splitLines(content: String?): List<String> = RepoHygiene.splitLines(content)
    fun missingLines(existingExclude: String?, gitignore: String?): List<String> =
        RepoHygiene.missingLines(existingExclude, gitignore).map { it.pattern }
    fun appendTo(existingExclude: String?, lines: List<String>): String {
        val patterns = lines.map { RepoHygiene.Pattern(it, "legacy", setOf("legacy")) }
        return RepoHygiene.appendTo(existingExclude, patterns)
    }
    fun ensure(projectRoot: File) { RepoHygiene.ensure(projectRoot) }
    fun matchesPatterns(relativePath: String): Boolean = RepoHygiene.matches(relativePath)
    fun untrackTracked(projectRoot: File, git: GitManager) {
        RepoHygiene.untrackTracked(projectRoot, git, swallow = true)
    }
}

/**
 * Thin delegate. Python patterns now live in [RepoHygiene]; [ensure] no
 * longer waits for a cache to appear on disk — the table is applied
 * unconditionally for any repo (same as BuildArtifactIgnore).
 */
@Deprecated("Use RepoHygiene", ReplaceWith("RepoHygiene"))
object PythonCacheIgnore {
    val EXCLUDE_LINES: List<String> = listOf("__pycache__/", "*.pyc", "*.pyo")
    const val NOTE = "# CodeC: Python bytecode caches stay out of git"
    fun splitLines(content: String?): List<String> = RepoHygiene.splitLines(content)
    fun covers(lines: List<String>): Boolean =
        lines.any {
            it.contains("pycache", ignoreCase = true) ||
                it == "*.pyc" || it == "*.pyo" || it == "*.pyd"
        }
    fun shouldAppend(existingExclude: String?, gitignore: String?): Boolean =
        !covers(RepoHygiene.splitLines(existingExclude)) &&
            !covers(RepoHygiene.splitLines(gitignore))
    fun appendTo(existingExclude: String?): String {
        val missing = EXCLUDE_LINES.map { RepoHygiene.Pattern(it, "Python bytecode", setOf("python")) }
        // Only append ones not already present.
        val have = RepoHygiene.splitLines(existingExclude).toSet()
        return RepoHygiene.appendTo(existingExclude, missing.filter { it.pattern !in have })
    }
    fun hasCacheIn(projectRoot: File): Boolean {
        if (!projectRoot.isDirectory) return false
        if (File(projectRoot, "__pycache__").isDirectory) return true
        val children = projectRoot.listFiles() ?: return false
        return children.any { it.isDirectory && File(it, "__pycache__").isDirectory }
    }
    fun ensure(projectRoot: File) {
        // Always apply the full RepoHygiene table (idempotent); keeps the
        // python-only call sites honest without a second code path.
        RepoHygiene.ensure(projectRoot)
    }
}
