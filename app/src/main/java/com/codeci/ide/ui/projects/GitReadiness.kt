package com.codeci.ide.ui.projects

/**
 * Phase 40.1 — pure, Android-free readiness model.
 *
 * The app used to be *reactive* about GitHub: it attempted the operation, git
 * failed, and a friendly message was produced — into a surface the user might
 * not be looking at (the owner, 2026-09-10: *"if i try to clone a repo and
 * didn't download the git it shows error in the background i can't see it"*).
 *
 * This object is the question asked *before* the fact; [GitErrors] stays the
 * answer *after* it. It is composed by the caller from state it already has —
 * no I/O happens here, ever, so it is trivially unit-testable on the host JVM.
 *
 * Ordering of the checks is deliberate: the cheapest, most blocking answer
 * wins (`GIT_NOT_INSTALLED` before `NO_TOKEN` — installing git is the only fix
 * that makes every other question meaningful).
 */
enum class GitOp {
    /** Stage + commit locally. Works offline and without a remote (Phase 14 law). */
    COMMIT,

    /** Publish local commits on the remote. Needs repo + token + remote. */
    PUSH,

    /** Fetch + merge from the remote. Needs repo + remote (a token only for private repos). */
    PULL,

    /** `git clone` a URL into a project. Needs git (+ network); no repo and no remote yet. */
    CLONE,

    /** Phase 40.3 — create the GitHub repository itself. Needs a token, not a remote. */
    PUBLISH,

    /** Local status / diff / branch reads. Needs a repository and nothing else. */
    REFRESH
}

/** What is missing before [GitOp] can work. `null` (no blocker) == ready. */
enum class GitBlocker {
    GIT_NOT_INSTALLED,
    NO_TOKEN,
    NO_REPOSITORY,
    NO_REMOTE,
    OFFLINE
}

/**
 * Who is ready for what, right now.
 *
 * [online] is intentionally nullable: `null` means "not known", and an unknown
 * network state never becomes an `OFFLINE` blocker — the app attempts the
 * operation and lets git's own error (or success) speak. A stale connectivity
 * value must not stop a push that would have worked.
 */
data class GitReadiness(
    val gitInstalled: Boolean,
    val hasToken: Boolean,
    val isRepository: Boolean,
    /** The first configured remote's name (`origin`), or null when there is none. */
    val remoteName: String? = null,
    /** The first configured remote's URL, for display only (`github.com/u/r`). */
    val remoteUrl: String? = null,
    val online: Boolean? = null
) {

    /** True when a remote is configured (name or URL known). */
    fun hasRemote(): Boolean = !remoteName.isNullOrBlank() || !remoteUrl.isNullOrBlank()

    /** The single blocker for [op], or null when the operation can proceed. */
    fun blocker(op: GitOp): GitBlocker? {
        if (!gitInstalled) return GitBlocker.GIT_NOT_INSTALLED
        if (isNetworkOp(op) && online == false) return GitBlocker.OFFLINE
        // CLONE creates the repository; PUBLISH creates the remote. Neither
        // requires either to exist yet.
        if (needsExistingRepository(op) && !isRepository) return GitBlocker.NO_REPOSITORY
        if (needsToken(op) && !hasToken) return GitBlocker.NO_TOKEN
        // A missing remote blocks push/pull — never commit (commits are local).
        if (needsRemote(op) && !hasRemote()) return GitBlocker.NO_REMOTE
        return null
    }

    /** True when [op] can proceed with what is known. */
    fun isReady(op: GitOp): Boolean = blocker(op) == null

    /** One sentence for the user, or null when [op] is ready. */
    fun message(op: GitOp): String? = when (blocker(op)) {
        null -> null
        GitBlocker.GIT_NOT_INSTALLED ->
            "Git isn't installed. Install it from Modules → Git (or run " +
                "`pkg install git` in the terminal), then retry."
        GitBlocker.NO_TOKEN ->
            "No GitHub token is connected. Add one in Settings → GitHub Account " +
                "(a token with Contents → Read and write), then retry."
        GitBlocker.NO_REPOSITORY ->
            "This folder isn't a Git repository yet. Run `git init` in the terminal " +
                "or clone a repository instead."
        GitBlocker.NO_REMOTE ->
            "This project has no GitHub remote yet, so there is nowhere to push. " +
                "Tap Publish to create the repository."
        GitBlocker.OFFLINE ->
            "You're offline or GitHub is unreachable. Your work is safe on this " +
                "device — reconnect and retry."
    }

    /** Which one-tap remedy the UI should offer, or null when [op] is ready. */
    fun actionId(op: GitOp): String? = when (blocker(op)) {
        null -> null
        GitBlocker.GIT_NOT_INSTALLED -> ACTION_INSTALL_GIT
        GitBlocker.NO_TOKEN -> ACTION_CONNECT_TOKEN
        GitBlocker.NO_REPOSITORY -> ACTION_INIT_REPO
        GitBlocker.NO_REMOTE -> ACTION_PUBLISH_REPO
        GitBlocker.OFFLINE -> ACTION_RETRY
    }

    private fun isNetworkOp(op: GitOp): Boolean =
        op == GitOp.PUSH || op == GitOp.PULL || op == GitOp.CLONE || op == GitOp.PUBLISH

    private fun needsExistingRepository(op: GitOp): Boolean =
        op != GitOp.CLONE && op != GitOp.PUBLISH

    private fun needsToken(op: GitOp): Boolean = op == GitOp.PUSH || op == GitOp.PUBLISH

    private fun needsRemote(op: GitOp): Boolean = op == GitOp.PUSH || op == GitOp.PULL

    companion object {
        const val ACTION_INSTALL_GIT = "INSTALL_GIT"
        const val ACTION_CONNECT_TOKEN = "CONNECT_TOKEN"
        const val ACTION_INIT_REPO = "INIT_REPO"
        const val ACTION_PUBLISH_REPO = "PUBLISH_REPO"
        const val ACTION_RETRY = "RETRY"

        /** Readiness for a project the app has open (git resolved, repo known). */
        fun forProject(
            gitInstalled: Boolean,
            hasToken: Boolean,
            isRepository: Boolean,
            remoteName: String? = null,
            remoteUrl: String? = null,
            online: Boolean? = null
        ): GitReadiness = GitReadiness(
            gitInstalled = gitInstalled,
            hasToken = hasToken,
            isRepository = isRepository,
            remoteName = remoteName,
            remoteUrl = remoteUrl,
            online = online
        )
    }
}
