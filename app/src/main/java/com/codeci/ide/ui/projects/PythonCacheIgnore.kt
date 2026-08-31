package com.codeci.ide.ui.projects

import java.io.File

/**
 * Device round fix (2026-08-31) — `python3` writes `__pycache__/` next to the
 * script, and `git add -A` happily stages it, so the git panel offered the
 * cache folder for commit & push ("no work with git"). CodeC keeps bytecode
 * caches out of git WITHOUT touching the user's `.gitignore`, by appending to
 * the repo-local `.git/info/exclude` — the standard machine-private ignore
 * that never travels to clones or pushes. The policy below is pure and
 * host-tested; the filesystem half is the thin, throw-nothing [ensure].
 */
object PythonCacheIgnore {

    /** Lines appended to `.git/info/exclude` when a cache shows up. */
    val EXCLUDE_LINES: List<String> = listOf("__pycache__/", "*.pyc", "*.pyo")

    /** A human-readable note naming why the lines exist (not parsed). */
    const val NOTE = "# CodeC: Python bytecode caches stay out of git"

    /** Trimmed, non-empty, non-comment lines — the patterns git would see. */
    fun splitLines(content: String?): List<String> =
        content?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            .orEmpty()

    /** True when [lines] already cover the caches (`__pycache__` or `*.pyc`). */
    fun covers(lines: List<String>): Boolean =
        lines.any { it.contains("pycache", ignoreCase = true) || it == "*.pyc" }

    /**
     * Append only when neither the exclude file nor the project's
     * `.gitignore` covers the caches yet. The user's own file always wins —
     * we never duplicate a rule they already wrote.
     */
    fun shouldAppend(existingExclude: String?, gitignore: String?): Boolean =
        !covers(splitLines(existingExclude)) && !covers(splitLines(gitignore))

    /** New exclude-file content: existing bytes + note + the three lines. */
    fun appendTo(existingExclude: String?): String {
        val base = existingExclude.orEmpty()
        val sep = if (base.isEmpty() || base.endsWith("\n")) "" else "\n"
        return buildString {
            append(base)
            append(sep)
            append(NOTE).append('\n')
            EXCLUDE_LINES.forEach { append(it).append('\n') }
        }
    }

    /** A `__pycache__` at the project root or inside a first-level folder. */
    fun hasCacheIn(projectRoot: File): Boolean {
        if (!projectRoot.isDirectory) return false
        if (File(projectRoot, "__pycache__").isDirectory) return true
        val children = projectRoot.listFiles() ?: return false
        return children.any { it.isDirectory && File(it, "__pycache__").isDirectory }
    }

    /**
     * Best-effort, safe from any thread: when a cache exists and nothing
     * ignores it yet, write the private exclude file. Non-repos, read-only
     * sandboxes and every other surprise are swallowed — the git panel must
     * never fail because of this helper.
     */
    fun ensure(projectRoot: File) {
        runCatching {
            if (!hasCacheIn(projectRoot)) return@runCatching
            val gitDir = resolveGitDir(projectRoot) ?: return@runCatching
            val exclude = File(gitDir, "info/exclude")
            val existing = runCatching { if (exclude.isFile) exclude.readText() else null }.getOrNull()
            val gitignore = runCatching {
                File(projectRoot, ".gitignore").takeIf { it.isFile }?.readText()
            }.getOrNull()
            if (!shouldAppend(existing, gitignore)) return@runCatching
            exclude.parentFile?.mkdirs()
            exclude.writeText(appendTo(existing))
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
