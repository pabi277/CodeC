package com.codeci.ide.ui.projects

/**
 * Phase 40.2 — pure, Android-free result of a real `git push`, parsed from
 * git's own bytes.
 *
 * Symptom this answers (owner, 2026-09-10): *"sometimes it's push stay local,
 * new branch create mostly stays local"*. Success-without-push and failure
 * used to look identical: a commit clears the change list either way.
 *
 * The [GitPushParser] is the only place that understands git's output
 * formats. Every line handed in is already [GitRedactor]-cleaned by
 * [GitManager]; unknown text never becomes a guess — the parser says
 * [PushOutcome.Failed] with the first redacted line as `detail`, which is a
 * *correct* answer to show (better than a green tick that lied).
 */
sealed interface PushOutcome {

    /** One or more commits reached the remote. */
    data class Pushed(
        val branch: String,
        val remoteUrl: String?,
        /** Short sha the remote was at before (`old..new`), or null for a new branch. */
        val from: String?,
        /** Short sha / branch the remote is at now. */
        val to: String,
        val newBranch: Boolean
    ) : PushOutcome

    /** `Everything up-to-date` — the remote already had these commits. */
    data object UpToDate : PushOutcome

    /** The remote refused the push (non-fast-forward, stale info, shallow, protected). */
    data class Rejected(val why: RejectReason, val hint: String) : PushOutcome

    /** There is no remote (or no upstream) to push to — the 40.3 case. */
    data class NoRemote(val message: String) : PushOutcome

    /** The remote rejected our credentials/token. */
    data class Auth(
        val kind: GitErrorKind,
        val message: String,
        val helpUrl: String? = GitErrors.TOKEN_HELP_URL
    ) : PushOutcome

    /** Git failed for a reason we could not name — [detail] is the redacted first line. */
    data class Failed(val message: String, val detail: String?) : PushOutcome

    /** True when commits are on the remote because of this push (or already were). */
    val ok: Boolean get() = this is Pushed || this is UpToDate
}

/** Why a non-fast-forward / refused push was rejected. */
enum class RejectReason {
    /** The remote has commits we do not have. */
    NON_FAST_FORWARD,

    /** The remote only accepted a fetch first (`[rejected] … (fetch first)`). */
    FETCH_FIRST,

    /** `shallow update not allowed` — a shallow clone cannot be pushed from. */
    SHALLOW_UPDATE,

    /** GitHub's protected-branch rule (`GH006`) or a pre-receive hook. */
    PROTECTED_BRANCH,

    /** Any other `[remote rejected]` refusal. */
    REMOTE_REJECTED
}

/**
 * Parses `git push` output into a [PushOutcome].
 *
 * Rules are pinned to git's actual output:
 *
 * ```text
 * Everything up-to-date
 * To https://github.com/u/r.git
 *  * [new branch]      feature -> feature
 *    a1b2c3d..e4f5a6b  main -> main
 *  ! [rejected]        main -> main (non-fast-forward)
 *  ! [rejected]        main -> main (fetch first)
 *  ! [remote rejected] main -> main (protected branch hook declined)
 * remote: error: GH006: Protected branch update failed for refs/heads/main.
 * fatal: The current branch feature has no upstream branch
 * fatal: 'origin' does not exist
 * fatal: No configured push destination.
 * fatal: Authentication failed for 'https://github.com/u/r.git/'
 * fatal: could not read Username for 'https://github.com': terminal prompts disabled
 * remote: Permission to u/r.git denied to someone.
 * ```
 */
object GitPushParser {

    private val rejectedRegex = Regex("""\[(remote )?rejected\]""", RegexOption.IGNORE_CASE)
    private val newBranchRegex = Regex("""\[new branch\]\s+(\S+)\s*->\s*(\S+)""")
    private val refspecRegex = Regex("""([0-9a-fA-F]{7,40})\.{2,3}([0-9a-fA-F]{7,40})\s+(\S+)\s*->\s*(\S+)""")
    private val remoteLineRegex = Regex("""^To\s+(\S+)""")

    /**
     * @param stdout   git's stdout, already redacted
     * @param stderr   git's stderr, already redacted (git prints progress here)
     * @param exitCode git's exit code (0 = success, 124 = our timeout)
     * @param branch   the branch we asked git to push (from `git status`)
     * @param remoteUrl the configured remote URL, for the result card
     */
    fun parse(
        stdout: List<String>,
        stderr: List<String>,
        exitCode: Int,
        branch: String?,
        remoteUrl: String? = null
    ): PushOutcome {
        val lines = (stdout + stderr).map { it.trim() }.filter { it.isNotEmpty() }
        val url = remoteUrl?.takeIf { it.isNotBlank() } ?: remoteFrom(lines)

        // 1. Refusals first: a `[rejected]` line can coexist with "failed to
        //    push some refs", and naming the reason is the whole point.
        val rejection = lines.firstOrNull { rejectedRegex.containsMatchIn(it) }
        if (rejection != null) {
            val lower = rejection.lowercase()
            val why = when {
                lower.contains("shallow") -> RejectReason.SHALLOW_UPDATE
                lower.contains("protected") || lower.contains("gh006") -> RejectReason.PROTECTED_BRANCH
                lower.contains("non-fast-forward") -> RejectReason.NON_FAST_FORWARD
                lower.contains("fetch first") -> RejectReason.FETCH_FIRST
                else -> RejectReason.REMOTE_REJECTED
            }
            return PushOutcome.Rejected(why, hintFor(why, rejection))
        }

        // 2. GitHub's protected-branch answer can arrive without a [rejected]
        //    line (the `remote: error: GH006: …` block).
        val gh006 = lines.firstOrNull { it.contains("GH006", ignoreCase = true) }
        if (gh006 != null) {
            return PushOutcome.Rejected(
                RejectReason.PROTECTED_BRANCH,
                hintFor(RejectReason.PROTECTED_BRANCH, gh006)
            )
        }

        // 3. Credentials: git never prompts (GIT_TERMINAL_PROMPT=0), so a
        //    missing/insufficient token surfaces here.
        val auth = lines.firstOrNull { isAuthLine(it) }
        if (auth != null) {
            val permission = auth.contains("permission", ignoreCase = true) ||
                auth.contains("403") || auth.contains("denied", ignoreCase = true)
            return PushOutcome.Auth(
                kind = if (permission) GitErrorKind.TOKEN_PERMISSION else GitErrorKind.AUTH_FAILED,
                message = if (permission) {
                    "GitHub accepted the connection but refused the write — the token " +
                        "needs Contents → Read and write on this repository."
                } else {
                    "GitHub rejected the token. Check it in Settings → GitHub Account " +
                        "(a fine-grained token with Contents → Read and write)."
                }
            )
        }

        // 4. Nowhere to push: no remote configured, or no upstream tracking.
        val noRemote = lines.firstOrNull { isNoRemoteLine(it) }
        if (noRemote != null) {
            return PushOutcome.NoRemote(
                "This branch has no remote branch yet. Tap Publish to create the " +
                    "GitHub repository, then push again."
            )
        }

        // 5. Success shapes.
        if (lines.any { it.contains("Everything up-to-date", ignoreCase = true) }) {
            return PushOutcome.UpToDate
        }
        val newBranch = lines.firstNotNullOfOrNull { newBranchRegex.find(it) }
        if (newBranch != null) {
            val to = newBranch.groupValues[2]
            return PushOutcome.Pushed(
                branch = to.ifBlank { branch ?: "HEAD" },
                remoteUrl = url,
                from = null,
                to = to.ifBlank { branch ?: "HEAD" },
                newBranch = true
            )
        }
        val refspec = lines.firstNotNullOfOrNull { refspecRegex.find(it) }
        if (refspec != null) {
            val to = refspec.groupValues[4]
            return PushOutcome.Pushed(
                branch = to.ifBlank { branch ?: "HEAD" },
                remoteUrl = url,
                from = shortSha(refspec.groupValues[1]),
                to = shortSha(refspec.groupValues[2]),
                newBranch = false
            )
        }

        // 6. Exit 0 without a refspec line: git still says the push worked
        //    (e.g. a tag, or a branch line we do not parse). Only the exit
        //    code is our evidence; say so rather than inventing refs.
        if (exitCode == 0) {
            val name = branch?.takeIf { it.isNotBlank() } ?: "HEAD"
            return PushOutcome.Pushed(
                branch = name,
                remoteUrl = url,
                from = null,
                to = name,
                newBranch = false
            )
        }

        // 7. Unknown failure — never guess. The first redacted line is the
        //    honest detail; the UI shows it verbatim.
        return PushOutcome.Failed(
            message = "The push did not succeed.",
            detail = lines.firstOrNull()?.take(200)
        )
    }

    /** `https://user:token@host/…` never leaves this parser (already scrubbed). */
    private fun remoteFrom(lines: List<String>): String? = lines
        .firstNotNullOfOrNull { remoteLineRegex.find(it) }
        ?.groupValues?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }

    private fun shortSha(value: String): String = value.take(7)

    private fun isAuthLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("authentication failed") ||
            lower.contains("could not read username") ||
            lower.contains("could not read password") ||
            lower.contains("invalid username or password") ||
            lower.contains("bad credentials") ||
            lower.contains("terminal prompts disabled") ||
            lower.contains("password authentication was removed") ||
            (lower.contains("permission to") && lower.contains("denied")) ||
            (lower.contains("403") && lower.contains("forbidden"))
    }

    private fun isNoRemoteLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("no configured push destination") ||
            lower.contains("has no upstream branch") ||
            lower.contains("does not appear to be a git repository") ||
            (lower.contains("'") && lower.contains("' does not exist")) ||
            lower.contains("no such remote")
    }

    private fun hintFor(why: RejectReason, line: String): String = when (why) {
        RejectReason.NON_FAST_FORWARD ->
            "GitHub has commits this device does not have. Pull first, then push again."
        RejectReason.FETCH_FIRST ->
            "The remote refused a stale update. Fetch (or pull), then push again."
        RejectReason.SHALLOW_UPDATE ->
            "This is a shallow clone, so git will not push from it. Fetch the full " +
                "history (`git fetch --unshallow`), then push again."
        RejectReason.PROTECTED_BRANCH ->
            "That branch is protected on GitHub. Push a new branch and open a pull " +
                "request instead."
        RejectReason.REMOTE_REJECTED -> line.take(200)
    }
}
