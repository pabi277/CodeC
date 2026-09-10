package com.codeci.ide.ui.projects

/**
 * Phase 40.2 — pure, Android-free [PushOutcome] parsed from git's own bytes.
 *
 * Every possible git push result shape, all of them already `GitRedactor`-cleaned.
 * Unknown text never becomes a guess: `Failed` with the first redacted line as `detail`,
 * and "we could not read git's mind" is a correct outcome to show — better than a green tick
 * that lied.
 *
 * The [GitPushParser] is the only place that understands git's actual output formats.
 * All parsing rules are pinned to what git actually prints, documented in the Phase 40.2 spec.
 */
sealed interface PushOutcome {

    /** Push succeeded — names the branch, remote, and short SHA. */
    data class Pushed(
        val branch: String,
        val remoteUrl: String?,
        val from: String?,
        val to: String,
        val newBranch: Boolean
    ) : PushOutcome

    /** "Everything up-to-date" — nothing was pushed because the remote is already at the same state. */
    data object UpToDate : PushOutcome

    /** Push was rejected — non-fast-forward, stale-info, shallow, or protected-branch. */
    data class Rejected(
        val why: RejectReason,
        val hint: String
    ) : PushOutcome {

        /** Human‑readable reason for the rejection. */
        enum class RejectReason {
            NON_FAST_FORWARD,
            STALE_INFO,
            SHALLOW_UPDATE,
            PROTECTED_BRANCH
        }
    }

    /** No remote configured — push cannot succeed without a remote. */
    data class NoRemote(val message: String) : PushOutcome

    /** Auth failed — token was rejected by GitHub. */
    data class Auth(
        val kind: GitErrorKind,
        val message: String
    ) : PushOutcome

    /** Git command failed for some other reason. */
    data class Failed(
        val message: String,
        val detail: String?
    ) : PushOutcome
}

/**
 * Parses `git push` (and allied) output into a [PushOutcome].
 *
 * All input lines are already [GitRedactor]-cleaned before they reach this parser.
 * The parser is purely functional — no side effects, no process management,
 * completely unit-testable on the host JVM.
 *
 * Parsing rules are pinned to git's actual output, listed in the Phase 40.2 spec.
 * Order matters: the first matching rule wins; unknown text falls through to `Failed`.
 */
object GitPushParser {

    /** Parse push output from git.
     *
     * @param stdout   lines from git's stdout (already redacted)
     * @param stderr   lines from git's stderr (already redacted)
     * @param exitCode git process exit code (0 = success, 1 = general failure, 124 = timeout)
     * @param branch   the branch that was pushed (from [GitStatusParser])
     * @param remote   the remote that was pushed to (from [GitManager.firstRemote])
     * @return a [PushOutcome] representing what actually happened
     */
    fun parse(
        stdout: List<String>,
        stderr: List<String>,
        exitCode: Int,
        branch: String?,
        remote: String?
    ): PushOutcome {

        val allOutput = (stdout + stderr).map { it.trim() }.filter { it.isNotEmpty() }
        val text = allOutput.joinToString("\n")

        // 1. Exit 0 + "Everything up-to-date" — nothing was pushed because remote is already current
        if (exitCode == 0 && text.contains("Everything up-to-date")) return PushOutcome.UpToDate

        // 2. `[new branch]` — a new branch was created on the remote
        if (text.contains("[new branch]")) {
            val branchName = extractNewBranchName(text)
            return PushOutcome.Pushed(
                branch = branchName ?: branch ?: "unknown",
                remoteUrl = remote?.takeIf { it.isNotBlank() },
                from = null,
                to = branchName ?: "unknown",
                newBranch = true
            )
        }

        // 3. `old..new branch -> branch` format (old..new)
        val oldNew = extractOldNewBranch(text)
        if (oldNew != null) {
            val (old, newBranchName) = oldNew
            return PushOutcome.Pushed(
                branch = newBranchName,
                remoteUrl = remote?.takeIf { it.isNotBlank() },
                from = old,
                to = newBranchName,
                newBranch = false
            )
        }

        // 4. `! [rejected] … (non-fast-forward)` — exitCode 1 typically
        if (text.contains("non-fast-forward") || text.contains("Non-fast-forward")) {
            return PushOutcome.Rejected(
                why = PushOutcome.Rejected.RejectReason.NON_FAST_FORWARD,
                hint = extractRejectionHint(text, "non-fast-forward")
            )
        }

        // 5. `remote: error: GH006: Protected branch update failed`
        if (text.contains("GH006") || text.contains("protected branch")) {
            return PushOutcome.Rejected(
                why = PushOutcome.Rejected.RejectReason.PROTECTED_BRANCH,
                hint = "Protected branch update failed. Force-push is not allowed — create a new branch or remove the protection."
            )
        }

        // 6. `shallow update not allowed`
        if (text.contains("shallow update not allowed")) {
            return PushOutcome.Rejected(
                why = PushOutcome.Rejected.RejectReason.SHALLOW_UPDATE,
                hint = "Shallow update not allowed — fetch more history and retry."
            )
        }

        // 7. `fatal: The current branch X has no upstream branch` — no remote configured
        if (text.contains("no upstream branch") || text.contains("No upstream branch")) {
            return PushOutcome.NoRemote(
                message = "This branch has no remote branch yet. Tap Publish to create one, or add a remote first."
            )
        }

        // 8. Auth-related messages (GitHub 401/403 echoed by git)
        if (text.contains("authentication failed") || text.contains("Auth failed") ||
            text.contains("invalid username") || text.contains("invalid password")) {
            val authKind = when {
                text.contains("401") -> GitErrorKind.TOKEN_PERMISSION
                text.contains("403") -> GitErrorKind.TOKEN_PERMISSION
                else -> GitErrorKind.AUTH_FAILED
            }
            return PushOutcome.Auth(
                kind = authKind,
                message = "GitHub rejected the push — your token may not allow writing to this repository. Use a token with Contents → Read and write, then retry."
            )
        }

        // 9. Generic failure — anything we didn't explicitly match
        //    Use the first redacted line as detail (never the token itself — already redacted)
        val detail = allOutput.firstOrNull { it.isNotBlank() }?.take(200) ?: null
        return PushOutcome.Failed(
            message = "The push did not succeed.",
            detail = detail
        )
    }

    /** Extract the branch name from `[new branch] <name>` or `[new branch]`. */
    private fun extractNewBranchName(text: String): String? {
        val marker = "[new branch] "
        val idx = text.indexOf(marker)
        if (idx < 0) return null
        val after = text.substringAfter(marker).trim()
        // The name is the first token before a newline or parenthesis
        return after.takeWhile { it != '(' && it != '\n' && it != '\r' }.trim()
    }

    /** Extract `old..new` branch format. Returns (oldName, newName) or null. */
    private fun extractOldNewBranch(text: String): Pair<String, String>? {
        // Look for "old..new" pattern
        val marker = ".."
        val idx = text.indexOf(marker)
        if (idx < 0) return null
        val before = text.substringBefore(marker).trim()
        val after = text.substringAfter(marker).trim()
        // After the .. is typically the branch name, possibly followed by -> and more
        val branchName = after.takeWhile { it != ' ' && it != '\n' && it != '\r' }.trim()
        if (branchName.isNotEmpty()) {
            return before.toLong() to branchName  // simplified: we only need the names
        }
        return null
    }

    /** Extract a hint string for rejection reasons. */
    private fun extractRejectionHint(text: String, keyword: String): String {
        // Try to extract a meaningful hint from the git output
        val lines = text.lines()
        for (line in lines) {
            val lowered = line.lowercase()
            if (lowered.contains(keyword)) {
                // Return the line minus the keyword prefix, trimmed
                val without = line.lowercase().replace(keyword, "").trim()
                return if (without.isNotEmpty()) without else "The push was rejected."
            }
        }
        return "The push was rejected."
    }
}