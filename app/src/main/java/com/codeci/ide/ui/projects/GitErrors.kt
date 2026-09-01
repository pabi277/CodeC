package com.codeci.ide.ui.projects

/**
 * Phase 17 follow-up (owner, 2026-09-01) — every git failure must end in a
 * clear, actionable message instead of raw git output: "git is not
 * installed", "no GitHub token connected", "offline", "push rejected" — each
 * with the next step and, where relevant, a link to create a token.
 *
 * Deliberately Android-free and pure so CI's `:app:testDebugUnitTest` can
 * prove the classification. The raw text handed in is ALREADY redacted by
 * [GitRedactor] (the token never reaches this code), and the friendly message
 * only ever echoes a short, capped snippet in the generic fallback.
 */
enum class GitErrorKind {
    NOT_INSTALLED,
    NOT_A_REPOSITORY,
    NO_TOKEN,
    TOKEN_PERMISSION,
    AUTH_FAILED,
    OFFLINE,
    REJECTED,
    NO_UPSTREAM,
    BRANCH_EXISTS,
    CONFLICT,
    TIMEOUT,
    GENERIC
}

/** A user-facing explanation of a git failure. */
data class GitFriendlyError(
    val kind: GitErrorKind,
    /** One friendly sentence: what happened + what to do next. */
    val message: String,
    /** A help link (e.g. the GitHub token page), or null. */
    val helpUrl: String? = null,
    /** The redacted raw git output, kept for troubleshooting — not user-facing. */
    val detail: String? = null
) {
    /** The full text the UI can show: message, then the help link on its own line. */
    fun display(): String = buildString {
        append(message)
        if (!helpUrl.isNullOrBlank()) {
            append('\n').append(helpUrl)
        }
    }
}

object GitErrors {

    /** GitHub's fine-grained Personal Access Token creation page. */
    const val TOKEN_HELP_URL = "https://github.com/settings/personal-access-tokens/new"

    /** One-liner used wherever git is missing (mirrors `git_not_installed_message`). */
    fun notInstalled(): GitFriendlyError = GitFriendlyError(
        kind = GitErrorKind.NOT_INSTALLED,
        message = "Git isn't installed. Install it from Modules → Git (or run " +
            "`pkg install git` in the terminal), then retry."
    )

    /** A token is required but none is stored. */
    fun tokenMissing(): GitFriendlyError = GitFriendlyError(
        kind = GitErrorKind.NO_TOKEN,
        message = "No GitHub token is connected, so the push was not authorized. " +
            "Add one in Settings → GitHub Account (a fine-grained token with " +
            "Contents → Read and write).",
        helpUrl = TOKEN_HELP_URL
    )

    /** A token is stored but GitHub rejected it. */
    fun tokenInvalid(): GitFriendlyError = GitFriendlyError(
        kind = GitErrorKind.AUTH_FAILED,
        message = "GitHub didn't accept your token. Re-check it in Settings → " +
            "GitHub Account, or create a new one.",
        helpUrl = TOKEN_HELP_URL
    )

    /**
     * Maps redacted git output + context to a friendly explanation.
     *
     * @param raw the (already redacted) error message, e.g.
     *   `git push failed: fatal: could not read Username for 'https://github.com'`.
     * @param exitCode the process exit code, when known (124 = our timeout).
     * @param hasToken whether a GitHub token is stored (changes the auth wording).
     */
    fun classify(raw: String?, exitCode: Int?, hasToken: Boolean): GitFriendlyError {
        val text = raw?.trim().orEmpty()
        val lower = text.lowercase()

        fun withDetail(error: GitFriendlyError): GitFriendlyError =
            if (text.isBlank()) error else error.copy(detail = text)

        return when {
            // Our own timeout (GitManager reports 124) or git's own wording.
            exitCode == 124 || lower.contains("timed out") ->
                GitFriendlyError(
                    GitErrorKind.TIMEOUT,
                    "The git command took too long (slow or no network?). Your " +
                        "commit stays safe on this device — retry when the " +
                        "connection is better."
                ).let(::withDetail)

            // Credential prompts / invalid login — the token cases.
            lower.contains("could not read username") ||
                lower.contains("authentication failed") ||
                lower.contains("invalid username or password") ||
                lower.contains("access token") && lower.contains("invalid") ->
                if (hasToken) tokenInvalid() else tokenMissing()

            // GitHub answered 401: stored token is no longer valid.
            containsHttpStatus(lower, 401) ->
                tokenInvalid()

            // GitHub answered 403 / permission denied: token lacks write access.
            containsHttpStatus(lower, 403) || lower.contains("permission denied") ||
                lower.contains("remote: permission to ") ->
                GitFriendlyError(
                    GitErrorKind.TOKEN_PERMISSION,
                    "GitHub rejected the push — your token may not allow writing " +
                        "to this repository. Use a token with Contents → Read and " +
                        "write, then retry.",
                    helpUrl = TOKEN_HELP_URL
                ).let(::withDetail)

            // Network / reachability problems.
            lower.contains("could not resolve host") ||
                lower.contains("connection timed out") ||
                lower.contains("network is unreachable") ||
                lower.contains("failed to connect") ||
                lower.contains("unable to access") ||
                lower.contains("couldn't connect") ||
                lower.contains("early eof") ||
                lower.contains("temporary failure") ||
                lower.contains("unable to look up") ||
                lower.contains("offline") ->
                GitFriendlyError(
                    GitErrorKind.OFFLINE,
                    "You're offline or the remote is unreachable. Your work is " +
                        "safe on this device — reconnect and tap PUSH to retry."
                ).let(::withDetail)

            // The branch has no remote counterpart yet (terminal parity).
            lower.contains("no upstream branch") ->
                GitFriendlyError(
                    GitErrorKind.NO_UPSTREAM,
                    "This branch has no remote branch yet — CodeC publishes it " +
                        "on the next push."
                ).let(::withDetail)

            // The remote moved on; a plain push can't fast-forward.
            lower.contains("non-fast-forward") || lower.contains("fetch first") ||
                lower.contains("cannot push") || lower.contains("rejected") ->
                GitFriendlyError(
                    GitErrorKind.REJECTED,
                    "The push was rejected — the remote has commits this device " +
                        "doesn't have yet. Tap PULL first, then PUSH again."
                ).let(::withDetail)

            // A name collision, typically a remote branch that already exists.
            lower.contains("already exists") ->
                GitFriendlyError(
                    GitErrorKind.BRANCH_EXISTS,
                    "A branch or remote with that name already exists — pick " +
                        "another name and retry."
                ).let(::withDetail)

            // Merge conflict / unmerged paths.
            lower.contains("unmerged") || lower.contains("merge conflict") ||
                lower.contains("conflict") ->
                GitFriendlyError(
                    GitErrorKind.CONFLICT,
                    "Git stopped at a merge conflict — edit the file to resolve " +
                        "it, mark it resolved, then retry."
                ).let(::withDetail)

            // SSH remotes aren't supported by CodeC's clone flow.
            lower.contains("publickey") || lower.contains("could not read from remote repository") ->
                GitFriendlyError(
                    GitErrorKind.GENERIC,
                    "CodeC pushes over https:// URLs. Re-add the remote as an " +
                        "https:// GitHub URL, then retry."
                ).let(::withDetail)

            // Repository missing — private without a token, or a wrong URL.
            lower.contains("repository not found") || lower.contains("not found") ->
                GitFriendlyError(
                    GitErrorKind.TOKEN_PERMISSION,
                    "Repository not found — it may be private (add a token in " +
                        "Settings → GitHub Account) or the URL is wrong.",
                    helpUrl = TOKEN_HELP_URL
                ).let(::withDetail)

            // The folder isn't a git work tree.
            lower.contains("not a git repository") ->
                GitFriendlyError(
                    GitErrorKind.NOT_A_REPOSITORY,
                    "This folder isn't a Git repository. Clone one from Files → " +
                        "⋮ → Clone from GitHub, or run `git init` in the terminal."
                ).let(::withDetail)

            // Fallback: show a short, already-redacted snippet (never the token).
            else ->
                GitFriendlyError(
                    GitErrorKind.GENERIC,
                    "Git reported an error: " +
                        (text.lineSequence().firstOrNull { it.isNotBlank() }?.take(160)
                            ?.ifBlank { null } ?: "unknown error")
                )
        }
    }

    /** True when the text contains an HTTP status line for [status] (e.g. `401`). */
    private fun containsHttpStatus(lower: String, status: Int): Boolean {
        val needle = status.toString()
        // `the requested url returned error: 403` / `error: 401` — the code
        // appears as its own token, not inside a sha or timestamp.
        return lower.contains("error: $needle") ||
            lower.contains("returned error: $needle") ||
            lower.contains("($needle)")
    }
}
