package com.codeci.ide.ui.projects

import java.io.File

/**
 * Device round (2026-08-31) — compiling/running C drops `a.out` next to the
 * source and `bin/<name>.out` for project-file runs, and `git add -A` (the
 * one-tap COMMIT & PUSH) used to stage them, so build outputs "came at
 * push". CodeC keeps build/run outputs out of git WITHOUT touching the
 * user's `.gitignore`, by appending the patterns below to the repo-local
 * `.git/info/exclude` — the standard machine-private ignore that never
 * travels to clones or pushes. Same policy shape as [PythonCacheIgnore].
 *
 * Patterns already covered by either `.git/info/exclude` or the project's
 * `.gitignore` are skipped — the user's own file always wins.
 */
object BuildArtifactIgnore {

    /** Lines appended to `.git/info/exclude` when nothing covers them yet. */
    val EXCLUDE_LINES: List<String> = listOf(
        "*.out",
        "*.o",
        "*.obj",
        "*.exe",
        "*.class",
        "bin/",
        "dist/",
        "build/",
        "target/",
        "node_modules/",
        ".venv/",
        "venv/"
    )

    /** A human-readable note naming why the lines exist (not parsed). */
    const val NOTE = "# CodeC: build/run outputs stay out of git"

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
     * The patterns that are still uncovered by both the exclude file and the
     * project's `.gitignore`.
     */
    fun missingLines(existingExclude: String?, gitignore: String?): List<String> =
        EXCLUDE_LINES.filter {
            !covered(splitLines(existingExclude), it) && !covered(splitLines(gitignore), it)
        }

    /** New exclude-file content: existing bytes + note + the missing lines. */
    fun appendTo(existingExclude: String?, lines: List<String>): String {
        val base = existingExclude.orEmpty()
        val sep = if (base.isEmpty() || base.endsWith("\n")) "" else "\n"
        return buildString {
            append(base)
            append(sep)
            append(NOTE).append('\n')
            lines.forEach { append(it).append('\n') }
        }
    }

    /**
     * Best-effort, safe from any thread: when the repo does not cover the
     * patterns yet, write the private exclude file. Non-repos, read-only
     * sandboxes and every other surprise are swallowed — git operations must
     * never fail because of this helper.
     */
    fun ensure(projectRoot: File) {
        runCatching {
            val gitDir = resolveGitDir(projectRoot) ?: return@runCatching
            val exclude = File(gitDir, "info/exclude")
            val existing = runCatching { if (exclude.isFile) exclude.readText() else null }.getOrNull()
            val gitignore = runCatching {
                File(projectRoot, ".gitignore").takeIf { it.isFile }?.readText()
            }.getOrNull()
            val missing = missingLines(existing, gitignore)
            if (missing.isEmpty()) return@runCatching
            exclude.parentFile?.mkdirs()
            exclude.writeText(appendTo(existing, missing))
        }
    }

    /** True when [relativePath] matches any of [EXCLUDE_LINES]. */
    fun matchesPatterns(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/').removePrefix("./")
        return EXCLUDE_LINES.any { pattern ->
            when {
                pattern.endsWith("/") -> normalized.startsWith(pattern)
                pattern.startsWith("*.") -> normalized.endsWith(pattern.removePrefix("*"))
                else -> normalized == pattern || normalized.endsWith("/$pattern")
            }
        }
    }

    /**
     * Untrack files that made it into the index before the patterns existed
     * (e.g. an `a.out` committed and pushed in an earlier round).
     * `git rm --cached` leaves the file on disk; the next commit records the
     * removal, so the artifact stops traveling to the remote. Best-effort —
     * any failure is swallowed, git operations must never break for this.
     */
    fun untrackTracked(projectRoot: File, git: GitManager) {
        runCatching {
            val tracked = git.trackedFiles(projectRoot) ?: return@runCatching
            val doomed = tracked.filter { matchesPatterns(it) }
            if (doomed.isEmpty()) return@runCatching
            git.rmCached(projectRoot, doomed)
        }
    }

    /** `.git` folder, or the `gitdir:` pointer file (linked/worktree repos). */
    private fun resolveGitDir(root: File): File? {
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
}
