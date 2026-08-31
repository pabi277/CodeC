package com.codeci.ide.ui.projects

/**
 * Phase 17 — pure (Android-free, process-free) branch/stash/conflict logic for
 * the editor's git experience. Everything here is a data model or a string
 * parser, so it runs on the host JVM unit-test path exactly like
 * [GitStatusParser] and `ProjectsHub`.
 *
 * The `git` processes themselves stay in [GitManager] (Phase 13 argv rules:
 * argv LIST, no shell, private env, redacted output). This file only decides
 * WHAT to run and HOW to read it back.
 *
 * Research notes (verified 2026-08-31, public docs only):
 *  - `git status --porcelain` unmerged (conflicted) paths are exactly the
 *    seven XY pairs `DD AU UD UA DU AA UU` — see git-status(1) "Short Format"
 *    (https://manpages.debian.org/testing/git-man/git-status.1.en.html).
 *    `AA` and `DD` carry no `U`, so testing "either column is U" misses them;
 *    testing "both columns are in {A,D,U}" false-positives on `AD` (staged
 *    addition deleted in the work tree, not a conflict). The exact set is
 *    therefore hard-coded below.
 *  - `git branch -a` prints `remotes/origin/HEAD -> origin/main` for the
 *    remote's default-branch symref; that line is not a branch and must be
 *    dropped, and a detached HEAD prints `* (HEAD detached at <sha>)` /
 *    `* (no branch)` instead of a name.
 *  - Checking out a remote-tracking ref directly (`git checkout origin/x`)
 *    detaches HEAD; the correct form is
 *    `git checkout -b <local> --track <remote>/<local>`.
 *  - `git stash list` lines are `stash@{N}: WIP on <branch>: <sha> <subject>`
 *    for a default stash and `stash@{N}: On <branch>: <message>` when a
 *    message was given with `stash push -m`. Refnames may not contain `:`,
 *    so splitting on the first `: ` after the branch is unambiguous.
 *  - `git stash pop` only drops the entry when the apply succeeds — a
 *    conflicting pop leaves the entry in the stack, so nothing is lost.
 */
object GitBranchOps {

    /** The seven porcelain XY pairs git uses for unmerged (conflicted) paths. */
    private val UNMERGED_PAIRS = setOf("DD", "AU", "UD", "UA", "DU", "AA", "UU")

    /** True when a porcelain XY pair marks a merge conflict. */
    fun isConflict(x: Char, y: Char): Boolean = UNMERGED_PAIRS.contains("$x$y")

    /**
     * A branch name CodeC is allowed to hand to git as an EXISTING ref.
     *
     * Deliberately looser than [ProjectsHub.isValidBranchName] (which gates
     * user-typed NEW names): a repository may already contain names that
     * CodeC's conservative new-name charset rejects (e.g. `fix#123`). Since
     * argv is a LIST there is no shell to inject into, and the only real
     * hazard is a name git would parse as an option or a revision range, so
     * this rejects exactly that: blanks, leading `-`, whitespace/control
     * characters and `..`.
     */
    fun isSafeExistingBranch(name: String): Boolean {
        val value = name.trim()
        if (value.isEmpty() || value.length > 400) return false
        if (value.startsWith("-")) return false
        if (value.contains("..")) return false
        if (value.any { it.isWhitespace() || it.isISOControl() }) return false
        return true
    }

    /**
     * The stash marker CodeC writes so it can recognise — and automatically
     * restore — the changes it stashed on the user's behalf when they left a
     * branch (Spck's "your uncommitted changes are stored locally and brought
     * back" promise).
     */
    object StashMarker {
        const val PREFIX = "codec-switch:"

        fun message(branch: String): String = "$PREFIX ${branch.trim()}"

        /** The branch name inside a stash message, or null for a foreign stash. */
        fun branchOf(message: String?): String? {
            val value = message?.trim() ?: return null
            if (!value.startsWith(PREFIX)) return null
            return value.removePrefix(PREFIX).trim().takeIf { it.isNotEmpty() }
        }
    }
}

/** One branch row: local (`main`) or remote (`origin/main`). */
data class GitBranch(
    val name: String,
    val isRemote: Boolean,
    val isCurrent: Boolean
) {
    /** `origin/feature` → `feature`: the local name a checkout would create. */
    val localName: String get() = if (isRemote) name.substringAfter('/', name) else name
}

/** Result of `git branch --all --no-color`. */
data class GitBranchList(
    val branches: List<GitBranch> = emptyList(),
    val current: String? = null,
    val detached: Boolean = false
) {
    val local: List<GitBranch> get() = branches.filter { !it.isRemote }
    val remote: List<GitBranch> get() = branches.filter { it.isRemote }
}

/**
 * Parses `git branch --all --no-color`:
 * ```
 *   feature/x
 * * main
 *   remotes/origin/HEAD -> origin/main
 *   remotes/origin/main
 * ```
 * Detached HEAD prints `* (HEAD detached at 1a2b3c4)` (or `* (no branch)` on
 * older git) as the current row instead of a name.
 */
object GitBranchParser {

    fun parse(lines: List<String>): GitBranchList {
        val out = mutableListOf<GitBranch>()
        var current: String? = null
        var detached = false

        for (raw in lines) {
            val line = raw.trimEnd('\r', '\n').trimEnd()
            if (line.isEmpty()) continue
            // A remote's default-branch symref (`origin/HEAD -> origin/main`).
            if (line.contains(" -> ")) continue
            val isCurrent = line.startsWith("*")
            var name = if (isCurrent) line.removePrefix("*").trimStart() else line.trimStart()
            if (name.isEmpty()) continue
            if (name.startsWith("(")) {
                // `(HEAD detached at …)` / `(no branch)` — a state, not a branch.
                if (isCurrent) detached = true
                continue
            }
            val isRemote = name.startsWith("remotes/")
            if (isRemote) name = name.removePrefix("remotes/")
            if (name.isEmpty() || name == "HEAD" || name.endsWith("/HEAD")) continue
            if (isCurrent) current = name
            out += GitBranch(name = name, isRemote = isRemote, isCurrent = isCurrent)
        }
        return GitBranchList(branches = out, current = current, detached = detached)
    }
}

/** One `git stash list` entry. */
data class GitStashEntry(
    val index: Int,
    val ref: String,
    val branch: String?,
    val message: String
) {
    /** The branch CodeC stashed FROM when this entry carries our marker. */
    val codecBranch: String? get() = GitBranchOps.StashMarker.branchOf(message)
}

/**
 * Parses `git stash list`:
 * ```
 * stash@{0}: On main: codec-switch: main
 * stash@{1}: WIP on feature/x: 1a2b3c4 wip commit subject
 * ```
 */
object GitStashParser {

    private val LINE = Regex("""^stash@\{(\d+)\}:\s*(.*)$""")

    fun parse(lines: List<String>): List<GitStashEntry> =
        lines.mapNotNull { raw ->
            val match = LINE.matchEntire(raw.trim()) ?: return@mapNotNull null
            val index = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val subject = match.groupValues[2].trim()
            val (branch, message) = splitSubject(subject)
            GitStashEntry(
                index = index,
                ref = "stash@{$index}",
                branch = branch,
                message = message
            )
        }

    /** `On <branch>: <message>` / `WIP on <branch>: <sha> <subject>` → pair. */
    private fun splitSubject(subject: String): Pair<String?, String> {
        if (subject.startsWith("On ")) {
            val rest = subject.removePrefix("On ")
            val split = rest.indexOf(": ")
            if (split > 0) return rest.substring(0, split).trim() to rest.substring(split + 2).trim()
            return null to rest
        }
        if (subject.startsWith("WIP on ")) {
            val rest = subject.removePrefix("WIP on ")
            val split = rest.indexOf(": ")
            if (split > 0) return rest.substring(0, split).trim() to rest.substring(split + 2).trim()
            return null to rest
        }
        return null to subject
    }
}

/** Where a branch switch should land. */
enum class BranchTargetKind { LOCAL, REMOTE, NEW }

/** A user selection in the Switch Branch dialog. */
data class BranchTarget(
    val name: String,
    val kind: BranchTargetKind
)

/** The outcome of a [GitManager.switchBranch] run. */
data class SwitchBranchResult(
    val branch: String,
    val stashed: Boolean = false,
    val restored: Boolean = false,
    val stashPending: Boolean = false
)
