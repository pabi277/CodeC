package com.codeci.ide.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeci.ide.ui.projects.BuildArtifactIgnore
import com.codeci.ide.ui.projects.DiffEngine
import com.codeci.ide.ui.projects.PythonCacheIgnore
import com.codeci.ide.ui.projects.DiffLine
import com.codeci.ide.ui.projects.GitBranchList
import com.codeci.ide.ui.projects.GitContext
import com.codeci.ide.ui.projects.GitFileChange
import com.codeci.ide.ui.projects.GitManager
import com.codeci.ide.ui.projects.GitStatus
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
        val branchError: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Conflicted paths of the loaded status (empty when nothing is in conflict). */
    private fun conflictsOf(): List<GitFileChange> =
        _state.value.status?.files?.filter { it.isConflict }.orEmpty()

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    // ---- Phase 17: branches ------------------------------------------------

    /** Loads the branch list for the Switch Branch dialog (off the UI thread). */
    fun loadBranches(context: Context, projectRoot: File) {
        viewModelScope.launch {
            _state.value = _state.value.copy(branchesLoading = true, branchError = null)
            try {
                val git = gitContext(context).manager()
                if (git == null) {
                    _state.value = _state.value.copy(
                        branchesLoading = false,
                        branchError = "git is not installed"
                    )
                    return@launch
                }
                val list = withContext(Dispatchers.IO) { git.listBranches(projectRoot) }
                _state.value = _state.value.copy(branchesLoading = false, branches = list)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    branchesLoading = false,
                    branchError = e.message ?: "Could not read branches"
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
            try {
                val git = gitContext(context).manager()
                    ?: error("git is not installed")
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
                    branchError = e.message ?: "Branch switch failed"
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
                        // Device round fix 2026-08-31: a python bytecode cache
                        // that nothing ignores gets repo-locally excluded right
                        // before we list changes — the panel (and its COMMIT &
                        // PUSH staging) never offers __pycache__ files again.
                        PythonCacheIgnore.ensure(projectRoot)
                        // Same treatment for build/run outputs (a.out, bin/*):
                        // exclude new ones AND untrack any that an earlier
                        // push already committed, so artifacts stop traveling.
                        BuildArtifactIgnore.ensure(projectRoot)
                        BuildArtifactIgnore.untrackTracked(projectRoot, git)
                        git.status(projectRoot)
                    }
                } else {
                    null
                }
                _state.value = _state.value.copy(
                    loading = false,
                    gitInstalled = true,
                    isRepo = isRepo,
                    status = status,
                    message = finalMessage
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    gitInstalled = true,
                    message = e.message ?: "git status failed"
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
            // Make sure fresh build outputs (a.out, bin/…) are ignored before
            // `git add -A` sweeps the tree.
            BuildArtifactIgnore.ensure(projectRoot)
            git.stageAll(projectRoot)
            git.commit(projectRoot, trimmed)
            val pushResult = runCatching { git.push(projectRoot) }
            pushResult.fold(
                onSuccess = { "Committed & pushed ✓" },
                onFailure = { failure ->
                    "Committed ✓ — push failed: ${failure.message ?: "unknown error"}"
                }
            )
        }
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
        operation: suspend (GitManager) -> String
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = busyLabel)
            try {
                val git = gitContext(context).manager()
                    ?: error("git is not installed")
                val result = withContext(Dispatchers.IO) { operation(git) }
                _state.value = _state.value.copy(busy = false)
                refresh(context, projectRoot, finalMessage = result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    message = e.message ?: "git operation failed"
                )
            }
        }
    }

    private fun gitContext(context: Context): GitContext = GitContext(context.applicationContext)
}
