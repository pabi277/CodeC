package com.codeci.ide.ui.projects

/**
 * Phase 40.1 — pure, Android-free readiness model.
 *
 * Asked by every surface before any git action. Composed from state the caller
 * already has: git binary stored flag, repo detected flag, remote URL, online flag.
 * No new I/O inside this object, ever.
 */
object GitReadiness {

    /** Git blocker status, in priority order (most blocking first). */
    sealed class GitBlocker {
        GIT_NOT_INSTALLED, NO_TOKEN, NO_REPOSITORY, NO_REMOTE, OFFLINE
    }

    /** One sentence explanation per blocker. */
    fun blockerMessage(blocker: GitBlocker): String = blocker when
        GitBlocker.GIT_NOT_INSTALLED -> "Git isn't installed. Install it from Modules → Git (or run `pkg install git` in the terminal), then retry."
        GitBlocker.NO_TOKEN -> "No GitHub token is connected, so this operation needs authorization. Add one in Settings → GitHub Account (a fine-grained token with Contents → Read and write), then retry."
        GitBlocker.NO_REPOSITORY -> "This folder isn't a Git repository. Clone one from Files → ⋮ → Clone from GitHub, or run `git init` in the terminal."
        GitBlocker.NO_REMOTE -> "There is no remote configured for this project. Pull and local commits still work; push needs a remote. Add one or tap Publish to create one."
        GitBlocker.OFFLINE -> "You're offline or the remote is unreachable. Your work is safe on this device — reconnect and retry."
        else -> ""
    }

    /** Action id per blocker. */
    fun blockerActionId(blocker: GitBlocker): String = blocker when
        GitBlocker.GIT_NOT_INSTALLED -> "INSTALL_GIT"
        GitBlocker.NO_TOKEN -> "CONNECT_TOKEN"
        GitBlocker.NO_REMOTE -> "PUBLISH_REPO"
        GitBlocker.OFFLINE -> "RETRY"
        else -> "none"
    }
}
