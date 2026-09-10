package com.codeci.ide.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeci.ide.ui.projects.DiffEngine
import com.codeci.ide.ui.projects.DiffLine
import com.codeci.ide.ui.projects.GitBranchList
import com.codeci.ide.ui.projects.GitContext
import com.codeci.ide.ui.projects.GitFileChange
import com.codeci.ide.ui.projects.GitErrorKind
import com.codeci.ide.ui.projects.GitFriendlyError
import com.codeci.ide.ui.projects.GitErrors
import com.codeci.ide.ui.projects.GitManager
import com.codeci.ide.ui.projects.GitStatus
import com.codeci.ide.ui.projects.RepoHygiene
import com.codeci.ide.ui.projects.SwitchBranchResult
import com.codeci.ide.ui.projects.BranchTarget
import com.codeci.ide.ui.projects.ProjectPathUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 13 — state for the Source Control pane ([GitControlSheet]): branch
 * + change list, pull, and the one-tap commit-and-push flow, plus the inline
 * diff viewer contents.
 *
 * Phase 39.2 — ignore/untrack live inside [GitManager.stageAll] (the choke
 * point). refresh() still calls [RepoHygiene.ensure] so the change list
 * never offers build outputs; the untrack-on-refresh path is gone (it now
 * happens on the same commit as the stage, so history stays one commit).
 */
class GitControlViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val busy: Boolean = false,
        val gitInstalled: Boolean = true,
        val isRepo: Boolean = false,
        val status: GitStatus? = null,
        val message: String? = null,
        val diffLoading: Boolean = false,
        val diffPath: String? = null,
        val diffLines: List<DiffLine> = emptyList(),
        // Phase 17 — Switch Branch dialog + merge conflicts.
        val branches: GitBranchList? = null,
        val branchesLoading: Boolean = false,
        val branchBusy: Boolean = false,
        val branchResult: String? = null,
        val branchError: String? = null,
        /**
         * Phase 17 device fix — the reason the last push failed, kept until a
         * push succeeds or the user dismisses it. A failed push used to look
         * exactly like a successful one (the commit clears the change list),
         * so the app silently claimed work was on GitHub when it was not.
         */
        val pushError: String? = null,
        /**
         * Phase 17 follow-up — a help link to show next to [pushError] (the
         * GitHub token page for auth failures), or null when not applicable.
         */
        val pushHelpUrl: String? = null,
        /**
         * Phase 39.2 — one-line note when stageAll untracked previously
         * committed build outputs ("Removed N build outputs from the repo…").
         * Cleared on the next successful refresh without a hygiene change.
         */
        val hygieneNote: String? = null,
        /**
         * Phase 39.2 — "what will be committed" projection (first ~15 names)
         * from the current status. Empty when nothing is staged/pending.
         */
        val commitPreview: RepoHygiene.CommitPreview? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Conflicted paths of the loaded status (empty when nothing is in conflict). */
    private fun conflictsOf(): List<GitFileChange> =
        _state.value.status?.files?.filter { it.isConflict }.orEmpty()

    /**
     * Maps a git failure to a friendly, actionable message. Only real git
     * process failures ([GitManager.GitCommandException]) are classified;
     * validation errors ("Invalid branch name", "Enter a commit message")
     * pass through unchanged.
     */
    private fun friendly(e: Throwable, hasToken: Boolean): GitFriendlyError =
        if (e is GitManager.GitCommandException) {
            GitErrors.classify(e.message, e.exitCode, hasToken)
        } else {
            GitFriendlyError(
                kind = GitErrorKind.GENERIC,
                message = e.message ?: "Git operation failed"
            )
        }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    // ---- Phase 17: branches ------------------------------------------------

    /** Loads the branch list for the Switch Branch dialog (off the UI thread). */
    fun loadBranches(context: Context, projectRoot: File) {
        viewModelScope.launch {
            _state.value = _state.value.copy(branchesLoading = true, branchError = null)
            val git = gitContext(context).manager()
            if (git == null) {
                _state.value = _state.value.copy(
                    branchesLoading = false,
                    branchError = GitErrors.notInstalled().display()
                )
                return@launch
            }
            try {
                val list = withContext(Dispatchers.IO) { git.listBranches(projectRoot) }
                _state.value = _state.value.copy(branchesLoading = false, branches = list)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    branchesLoading = false,
                    branchError = friendly(e, git.hasCredentials).display()
                )
            }
        }
    }

    /**
     * Switch Branch: stash (when the tree is dirty and [stashChanges] is on) →
     * check out → auto-restore a stash belonging to the target branch. The
     * result text is kept in `branchResult`/`branchError` so the dialog can
     * show it before the user closes it (entry points without a snackbar —
     * the editor drawer, the Projects card — still surface the outcome).
     */
    fun switchBranch(
        context: Context,
        projectRoot: File,
        target: BranchTarget,
        stashChanges: Boolean = true
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                branchBusy = true,
                branchResult = null,
                branchError = null
            )
            val git = gitContext(context).manager()
            if (git == null) {
                _state.value = _state.value.copy(
                    branchBusy = false,
                    branchError = GitErrors.notInstalled().display()
                )
                return@launch
            }
            try {
                val result = withContext(Dispatchers.IO) {
                    git.switchBranch(projectRoot, target, stashChanges)
                }
                _state.value = _state.value.copy(
                    branchBusy = false,
                    branchResult = describeSwitch(result)
                )
                refresh(context, projectRoot)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    branchBusy = false,
                    branchError = friendly(e, git.hasCredentials).display()
                )
            }
        }
    }

    /** Honest one-liner for the Switch Branch dialog (D3/D4). */
    private fun describeSwitch(result: SwitchBranchResult): String = buildString {
        append("Switched to ").append(result.branch)
        when {
            result.stashed && result.restored -> append(" — saved changes restored")
            result.stashed -> append(" — your changes are stashed and come back when you switch to ").append(
                result.branch
            )
            result.restored -> append(" — your stashed changes were restored")
            result.stashPending ->
                append(" — your stashed changes are still saved (they could not be applied cleanly)")
        }
        // Phase 17 follow-up: a NEW branch is published on creation; say
        // exactly what happened so "not on GitHub" is never silent.
        when {
            result.published -> append(" · published to GitHub")
            result.publishError != null ->
                append(" · not on GitHub yet: ").append(result.publishError)
        }
    }

    fun clearBranchResult() {
        _state.value = _state.value.copy(branchResult = null, branchError = null)
    }

    /**
     * Mark Resolved (Spck's manual conflict resolution): staging the path
     * tells git the merge for it is done and clears the purple `U` mark.
     */
    fun markResolved(context: Context, projectRoot: File, change: GitFileChange) {
        val name = change.path.substringAfterLast('/')
        runGitOperation(context, projectRoot, "Marking $name resolved…") { git ->
            git.stageFile(projectRoot, change.path)
            "Marked resolved: $name"
        }
    }

    /**
     * Loads git availability + repository status for [projectRoot]. When
     * [finalMessage] is set (an operation just finished), it is applied only
     * once the fresh status has loaded so the result is actually visible.
     */
    fun refresh(context: Context, projectRoot: File, finalMessage: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            try {
                val git = gitContext(context).manager()
                if (git == null) {
                    _state.value = _state.value.copy(
                        loading = false,
                        gitInstalled = false,
                        isRepo = false,
                        status = null,
                        message = finalMessage
                    )
                    return@launch
                }
                val isRepo = withContext(Dispatchers.IO) { git.isRepository(projectRoot) }
                val status = if (isRepo) {
                    withContext(Dispatchers.IO) {
                        // Phase 39.2 — one table covers python caches, build
                        // outputs, .codec/, OS junk. ensure() is idempotent
                        // and never edits the user's .gitignore. Untrack of
                        // already-committed artifacts now happens inside
                        // stageAll (same commit as the clean tree), not here.
                        RepoHygiene.ensure(projectRoot)
                        val local = git.status(projectRoot)
                        // Phase 39 device follow-up — the "Branch X is not on
                        // the remote yet" banner used only the local upstream
                        // config. After a successful create+push the branch
                        // IS on GitHub, but if tracking was missing/stale the
                        // banner stayed. Probe the remote and repair tracking.
                        git.resolvePublishState(projectRoot, local)
                    }
                } else {
                    null
                }
                // "What will be committed" — every pending path the sheet
                // shows, projected the same way stage+commit would take them.
                val preview = status?.let { s ->
                    // Treat every listed change as "would be staged by add -A".
                    // Map untracked/unstaged onto a synthetic staged view for
                    // the preview (stageAll is add -A).
                    val synthetic = s.files.map { f ->
                        val x = if (f.x == ' ' || f.x == '?') {
                            when {
                                f.y == '?' -> 'A' // untracked → would be added
                                f.y == 'D' -> 'D'
                                f.y == 'M' || f.y == ' ' -> 'M'
                                else -> f.y
                            }
                        } else f.x
                        "${x}  ${f.oldPath?.let { "$it -> ${f.path}" } ?: f.path}"
                    }
                    RepoHygiene.commitPreview(
                        porcelainLines = listOf("## ${s.branch ?: "HEAD"}") + synthetic,
                        limit = 15,
                        hygieneNote = _state.value.hygieneNote,
                    )
                }
                _state.value = _state.value.copy(
                    loading = false,
                    gitInstalled = true,
                    isRepo = isRepo,
                    status = status,
                    message = finalMessage,
                    commitPreview = preview,
                )
            } catch (e: Exception) {
                // `git status` never authenticates, so a token check is moot;
                // classify to turn "not a git repository" etc. into guidance.
                _state.value = _state.value.copy(
                    loading = false,
                    gitInstalled = true,
                    message = friendly(e, hasToken = false).display()
                )
            }
        }
    }

    fun pull(context: Context, projectRoot: File) {
        runGitOperation(context, projectRoot, "Pulling…") { git ->
            git.pull(projectRoot)
            "Pull completed"
        }
    }

    /**
     * One-tap COMMIT & PUSH: stage everything, commit with the stored
     * identity, then push. A push failure (offline, no token, rejected) is
     * reported without losing the fact that the commit succeeded.
     */
    fun commitAndPush(context: Context, projectRoot: File, message: String) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            _state.value = _state.value.copy(message = "Enter a commit message")
            return
        }
        // Phase 17 §2.5 — Spck's rule: no commit while a merge conflict is
        // open. The UI disables the button too; this guard keeps the path
        // honest if it is ever reached from elsewhere.
        val conflicts = conflictsOf()
        if (conflicts.isNotEmpty()) {
            _state.value = _state.value.copy(
                message = if (conflicts.size == 1) {
                    "Resolve the conflict in ${conflicts.first().path.substringAfterLast('/')} before committing"
                } else {
                    "Resolve the ${conflicts.size} conflicted files before committing"
                }
            )
            return
        }
        runGitOperation(context, projectRoot, "Committing…") { git ->
            // Phase 39.2 — stageAll is the choke point: ensure exclude +
            // untrack previously-committed artifacts + git add -A. The
            // hygiene note is surfaced on the sheet so an unexplained
            // `git rm --cached` never appears in the user's history.
            val hygiene = git.stageAll(projectRoot)
            val note = hygiene.userMessage()
            if (note != null) {
                _state.value = _state.value.copy(hygieneNote = note)
            }
            git.commit(projectRoot, trimmed)
            // Phase 17 device fix: publish a branch that has no upstream yet
            // (`git push --set-upstream <remote> <branch>`) instead of failing
            // with "has no upstream branch" — and never claim a push worked.
            val pushFailure = runCatching { git.pushHandlingUpstream(projectRoot) }
                .exceptionOrNull()
            // Phase 39 device follow-up — name the branch so success never
            // reads like a silent push to main.
            val branchLabel = runCatching { git.currentBranch(projectRoot) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            if (pushFailure == null) {
                _state.value = _state.value.copy(pushError = null, pushHelpUrl = null)
                val pushed = if (branchLabel != null) {
                    "Committed & pushed to $branchLabel ✓"
                } else {
                    "Committed & pushed ✓"
                }
                if (note != null) "$note · $pushed" else pushed
            } else {
                // Phase 17 follow-up: a friendly, actionable reason + token
                // link instead of raw git output.
                val friendly = friendly(pushFailure, git.hasCredentials)
                _state.value = _state.value.copy(
                    pushError = friendly.message,
                    pushHelpUrl = friendly.helpUrl
                )
                val prefix = if (note != null) "$note · " else ""
                val where = if (branchLabel != null) " on $branchLabel" else ""
                "${prefix}Committed locally$where ✓ — NOT pushed: ${friendly.message}"
            }
        }
    }

    /**
     * Phase 17 device fix — retry a push on its own (the Source Control sheet
     * offers this whenever the branch is ahead of its remote).
     */
    fun push(context: Context, projectRoot: File) {
        runGitOperation(
            context,
            projectRoot,
            "Pushing…",
            onError = { failure ->
                _state.value = _state.value.copy(
                    pushError = failure.message,
                    pushHelpUrl = failure.helpUrl
                )
            }
        ) { git ->
            git.pushHandlingUpstream(projectRoot)
            _state.value = _state.value.copy(pushError = null, pushHelpUrl = null)
            val branch = runCatching { git.currentBranch(projectRoot) }.getOrNull()
            if (!branch.isNullOrBlank()) "Pushed to $branch ✓" else "Pushed ✓"
        }
    }

    /** Dismisses the sticky "not pushed" explanation. */
    fun dismissPushError() {
        _state.value = _state.value.copy(pushError = null, pushHelpUrl = null)
    }

    /**
     * Phase 15/16 — per-file stage/unstage (the mockup's +/− row button):
     * staged rows unstage (`git reset -- <path>`), unstaged rows stage
     * (`git add -- <path>`). The porcelain `x` column tells us the side the
     * file is currently on.
     */
    fun toggleStage(context: Context, projectRoot: File, change: GitFileChange) {
        val staged = change.x != ' '
        val name = change.path.substringAfterLast('/')
        runGitOperation(
            context,
            projectRoot,
            if (staged) "Unstaging $name…" else "Staging $name…"
        ) { git ->
            if (staged) {
                git.unstageFile(projectRoot, change.path)
                "Unstaged $name"
            } else {
                git.stageFile(projectRoot, change.path)
                "Staged $name"
            }
        }
    }

    /** Opens the inline diff viewer for one path (HEAD blob vs working tree). */
    fun openDiff(context: Context, projectRoot: File, path: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(diffLoading = true, diffPath = path, diffLines = emptyList())
            try {
                val lines = withContext(Dispatchers.IO) {
                    val git = gitContext(context).manager()
                    when {
                        git == null -> emptyList()
                        else -> {
                            val old = git.headFileContent(projectRoot, path) ?: ""
                            val file = ProjectPathUtils.resolveInside(projectRoot, path)
                            val new = if (file?.isFile == true) file.readText() else ""
                            DiffEngine.compute(old, new)
                        }
                    }
                }
                _state.value = _state.value.copy(diffLoading = false, diffLines = lines)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    diffLoading = false,
                    diffPath = null,
                    message = e.message ?: "Could not read the diff"
                )
            }
        }
    }

    fun closeDiff() {
        _state.value = _state.value.copy(diffPath = null, diffLines = emptyList())
    }

    private fun runGitOperation(
        context: Context,
        projectRoot: File,
        busyLabel: String,
        onError: ((GitFriendlyError) -> Unit)? = null,
        operation: suspend (GitManager) -> String
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = busyLabel)
            val git = gitContext(context).manager()
            if (git == null) {
                val notInstalled = GitErrors.notInstalled()
                onError?.invoke(notInstalled)
                _state.value = _state.value.copy(
                    busy = false,
                    gitInstalled = false,
                    message = notInstalled.display()
                )
                return@launch
            }
            try {
                val result = withContext(Dispatchers.IO) { operation(git) }
                _state.value = _state.value.copy(busy = false)
                refresh(context, projectRoot, finalMessage = result)
            } catch (e: Exception) {
                val failure = friendly(e, git.hasCredentials)
                onError?.invoke(failure)
                _state.value = _state.value.copy(busy = false, message = failure.display())
                // Phase 17 device fix: re-read the repository after a failure
                // too, so the "N commit(s) ahead" figure on screen is real —
                // a failed push must not look like a clean, pushed tree.
                refresh(context, projectRoot, finalMessage = failure.display())
            }
        }
    }

    private fun gitContext(context: Context): GitContext = GitContext(context.applicationContext)
}
