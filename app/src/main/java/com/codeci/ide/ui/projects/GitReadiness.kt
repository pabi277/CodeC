package com.codeci.ide.ui.projects

import com.codeci.ide.ui.projects.GitManager.isRepository

/**
 * Phase 40.1 — pure, Android-free readiness model.
 *
 * Asked by every surface before any git action. Composed from state the caller
 * already has: git binary, stored credentials, repository detection, first
 * remote, and the app's existing network signal. No new I/O inside this object,
 * ever.
 *
 * Ordering is deliberate: the cheapest, most blocking answer first
 * (GIT_NOT_INSTALLED before NO_TOKEN — installing git is the only fix that
 * makes every other question meaningful). NO_REMOTE is not a blocker for
 * commit (local commits must keep working offline, Phase 14's law), but IS a
 * blocker for push/pull, which is why blocker() takes the intended operation.
 */
enum class GitBlocker {
    GIT_NOT_INSTALLED,
    NO_TOKEN,
    NO_REPOSITORY,
    NO_REMOTE,
    OFFLINE
}

/**
 * Holds the answers. null blocker == ready to act.
 * Non-null blocker == what to tell the user, with an action id.
 */
data class GitReadiness(
    val gitInstalled: Boolean,
    val hasToken: Boolean,
    val isRepository: Boolean,
    val remoteUrl: String?,
    val online: Boolean?
) {
    /** Null when ready. Non-null blocker, with message and action id. */
    fun blocker(op: GitOp): GitBlocker? {
        // 1. Git not installed — most blocking; fixes every other question
        if (!gitInstalled) return GitBlocker.GIT_NOT_INSTALLED

        // 2. No token — only block ops that need credentials (push, pull, publish)
        //    A public-repo clone does NOT require a token — that is current correct
        //    behaviour and must not regress.
        if (!hasToken && op.needsToken) return GitBlocker.NO_TOKEN

        // 3. Not a git repository
        if (!isRepository) return GitBlocker.NO_REPOSITORY

        // 4. No remote — blocker only for push/pull, not for commit
        if (op.isPushPull && remoteUrl == null) return GitBlocker.NO_REMOTE

        // 5. Offline / no network signal
        if (online != true) {
            // OFFLINE never fires on a stale value; if online is null we guess
            // nothing and simply omit this blocker so the operation can proceed.
            return GitBlocker.OFFLINE
        }

        null // ready
    }

    /** One sentence, same tone as GitErrors. */
    fun message(blocker: GitBlocker): String {
        return when (blocker) {
            GitBlocker.GIT_NOT_INSTALLED -> "Git isn't installed. Install it from Modules → Git (or run `pkg install git` in the terminal), then retry."
            GitBlocker.NO_TOKEN -> "No GitHub token is connected, so this operation needs authorization. Add one in Settings → GitHub Account (a fine-grained token with Contents → Read and write), then retry."
            GitBlocker.NO_REPOSITORY -> "This folder isn't a Git repository. Clone one from Files → ⋮ → Clone from GitHub, or run `git init` in the terminal."
            GitBlocker.NO_REMOTE -> "There is no remote configured for this project. Pull and local commits still work; push needs a remote. Add one or tap Publish to create one."
            GitBlocker.OFFLINE -> "You're offline or the remote is unreachable. Your work is safe on this device — reconnect and retry."
            else -> ""
        }
    }

    /** Action id that surfaces a one-tap remedy. */
    fun actionId(blocker: GitBlocker): String {
        return when (blocker) {
            GitBlocker.GIT_NOT_INSTALLED -> "INSTALL_GIT"
            GitBlocker.NO_TOKEN -> "CONNECT_TOKEN"
            GitBlocker.NO_REMOTE -> "PUBLISH_REPO"
            GitBlocker.OFFLINE -> "RETRY"
            else -> "none"
        }
    }
}

/** Operations that carry a token-needs flag. */
sealed class GitOp {
    val needsToken: Boolean
        get() = this is GitOp.CommitOrPush || this is GitOp.Publish
        private init
}

data class GitOp.Clone(override val needsToken: Boolean = false) : GitOp()
data class GitOp.CommitOrPush(override val needsToken: Boolean = true) : GitOp()
data class GitOp.Publish(override val needsToken: Boolean = true) : GitOp()
