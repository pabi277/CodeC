package com.codeci.ide.ui.projects

/**
 * Phase 73.3 — pure (Android-free, process-free) models for Spck-parity
 * commit history and remotes, in the same spirit as [GitStatusParser] and
 * [GitBranchParser]: the `git` process itself stays in [GitManager]; this
 * file only decides how to read its output back.
 */

/** One row of `git log`, newest first. */
data class GitCommitEntry(
    val sha: String,
    val shortSha: String,
    val author: String,
    /** ISO-8601 (`--date=iso-strict`), shown formatted by the caller. */
    val date: String,
    val subject: String
)

/**
 * Parses `git log -n <N> --date=iso-strict
 * --pretty=format:%H%x1f%h%x1f%an%x1f%ad%x1f%s%n`:
 * ```
 * 1a2b3c4d...<40 hex>\x1f1a2b3c4\x1fOwner\x1f2026-09-29T12:00:00+05:30\x1fFix the thing
 * ```
 * One line per commit — the explicit `%n` in the format string, not git's
 * own `tformat:` newline-insertion (`format:` alone does not add one), so a
 * commit subject can never accidentally swallow the next commit's line.
 */
object GitLogParser {

    private const val UNIT_SEP = '\u001F'

    fun parse(lines: List<String>): List<GitCommitEntry> =
        lines.mapNotNull { raw ->
            val line = raw.trimEnd('\r')
            if (line.isBlank()) return@mapNotNull null
            val fields = line.split(UNIT_SEP, limit = 5)
            if (fields.size != 5) return@mapNotNull null
            val (sha, shortSha, author, date, subject) = fields
            if (sha.isBlank() || shortSha.isBlank()) return@mapNotNull null
            GitCommitEntry(
                sha = sha.trim(),
                shortSha = shortSha.trim(),
                author = author.trim(),
                date = date.trim(),
                subject = subject
            )
        }
}

/** One `git remote` entry with its URL, for the Remotes screen. */
data class GitRemoteEntry(val name: String, val url: String?)
