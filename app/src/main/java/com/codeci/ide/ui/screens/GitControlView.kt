package com.codeci.ide.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.ui.theme.CodecType
import com.codeci.ide.R
import com.codeci.ide.ui.components.SpckIcons
import com.codeci.ide.ui.components.FileIconView
import com.codeci.ide.ui.components.SkeletonGitRow
import com.codeci.ide.ui.modules.InstallOutcome
import com.codeci.ide.ui.modules.InstallOutcomes
import com.codeci.ide.ui.modules.PackageCatalog
import com.codeci.ide.ui.modules.PkgResult
import com.codeci.ide.ui.projects.DiffLine
import com.codeci.ide.ui.projects.DiffOp
import com.codeci.ide.ui.projects.GitBlocker
import com.codeci.ide.ui.projects.GitContext
import com.codeci.ide.ui.projects.GitErrors
import com.codeci.ide.ui.projects.GitDiscardPolicy
import com.codeci.ide.ui.projects.GitFileChange
import com.codeci.ide.ui.projects.GitFileState
import com.codeci.ide.ui.projects.GitOp
import com.codeci.ide.ui.projects.GitHubPublish
import com.codeci.ide.ui.projects.PushOutcome
import com.codeci.ide.ui.terminal.SetupAction
import com.codeci.ide.ui.terminal.SetupFacts
import com.codeci.ide.ui.terminal.SetupGatePolicy
import com.codeci.ide.ui.terminal.SetupStage
import com.codeci.ide.ui.terminal.ShellEnvironment
import com.codeci.ide.ui.terminal.TerminalLifecycle
import com.codeci.ide.ui.terminal.TerminalStatusLabel
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Radius
import com.codeci.ide.ui.theme.CodecTokens.Space
import com.codeci.ide.ui.utils.WebFileSupport
import com.codeci.ide.ui.viewmodels.GitControlViewModel
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Phase 13 Source Control, re-skinned mockup-exact (design:
 * mockups/source-control.png, Phase 17 spec §2.1), then Spck-exact in
 * Phase 73.5 (screenshots 1–4): a REPOSITORY header with search /
 * branch-menu / push-menu icons, an UNSTAGED collapsible section with a
 * count badge, an install status bar, and an inline Git Credentials
 * dialog. Engine and diff viewer are the unchanged Phase 13
 * `GitManager`/`DiffEngine`.
 *
 * Phase 73.7 — this is a *panel*, not a sheet: the owner's screenshots
 * show git living inside the editor's side panel (the REPOSITORY tab),
 * so the bottom-sheet wrapper is gone and the drawer hosts this
 * content directly. Commit All and Push moved into Spck's dialogs with
 * it (the old inline commit box + COMMIT & PUSH button are deleted).
 */
@Composable
fun GitControlPanel(
    projectRoot: File,
    viewModel: GitControlViewModel = viewModel(),
    /** Phase 39 device follow-up — see [BranchSwitchDialog.onBeforeSwitch]. */
    onBeforeBranchSwitch: (() -> Unit)? = null,
    /** Phase 39 device follow-up — see [BranchSwitchDialog.onAfterSwitch]. */
    onAfterBranchSwitch: (() -> Unit)? = null,
    /**
     * Phase 73.5 — the Git Credentials dialog's "Manage" link reuses the
     * drawer footer's existing Settings jump (Settings keeps its own
     * editor; both write the same store). The dialog itself lives in this
     * panel — credentials no longer require leaving it.
     */
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var pendingDiscard by remember(projectRoot) { mutableStateOf<GitFileChange?>(null) }
    // Phase 17 — the branch chip opens the Switch Branch dialog.
    var showBranchDialog by remember { mutableStateOf(false) }
    // Phase 40.3 — the Publish-to-GitHub dialog (create the remote there isn't one).
    var showPublishDialog by remember { mutableStateOf(false) }
    // Phase 73.5 — Spck-exact header: the 73.3 overflow menu is split into
    // the two screenshot menus (branch menu + push menu). Every action the
    // overflow had is still here, reached from the same ViewModel calls.
    var showBranchMenu by remember { mutableStateOf(false) }
    var showPushMenu by remember { mutableStateOf(false) }
    var showCredentialsDialog by remember { mutableStateOf(false) }
    // Phase 73.7 — Commit All and Push open Spck's dialogs (the inline
    // commit box + COMMIT & PUSH button are deleted with the old sheet).
    var showCommitDialog by remember { mutableStateOf(false) }
    var showPushDialog by remember { mutableStateOf(false) }
    var logDialogMode by remember(projectRoot) { mutableStateOf<GitLogDialogMode?>(null) }
    var showRemotesDialog by remember { mutableStateOf(false) }
    var pendingRevertAll by remember { mutableStateOf(false) }
    // Phase 73.5 — the header search icon filters the UNSTAGED list by
    // path; the section itself collapses like Spck's.
    var searchingGit by remember { mutableStateOf(false) }
    var gitSearchQuery by remember { mutableStateOf("") }
    var unstagedExpanded by remember { mutableStateOf(true) }

    // Phase 73.2 — install git in place instead of sending the user to
    // Packages/Terminal and back. Reuses the exact mechanism Phase 71.1 built
    // for the Packages tab: the real command goes into the shared terminal
    // session (`TerminalViewModel.sendCommand`, activity-scoped, so it runs
    // even if this panel is closed afterwards), and `PkgResult`/`InstallOutcomes`
    // — pure and already host-tested — decide WAITING/INSTALLED/FAILED from the
    // exit-status file `pkg` itself writes, exactly like a Packages row. The
    // one difference from a Packages row: on success this stays in the panel
    // (`viewModel.refresh`) instead of navigating to Terminal — the owner's
    // choice, since the user came here to work on a repo, not to watch a shell.
    val terminalViewModel: TerminalViewModel = activityTerminalViewModel()
    val setupFacts by terminalViewModel.setupFacts.collectAsState()
    // The Linux tools themselves (the ~40 MB userland) are a separate,
    // bigger install than the `git` package — SetupGatePolicy already knows
    // whether they're ready. Phase 73.8 supersedes 73.2's "plain message"
    // decision: when they are not ready, the install card below shows the
    // userland's live state (stage + the installer's real download %)
    // instead of a dead message pointing at Terminal.
    val installGitVerdict = remember(setupFacts) {
        SetupGatePolicy.can(SetupAction.INSTALL_PACKAGE, setupFacts)
    }
    val gitPackage = remember { PackageCatalog.ALL_PACKAGES.first { it.id == "git" } }
    val gitInstallTargets = remember(gitPackage) { PkgResult.installTargets(gitPackage.installCommand) }
    val userlandPrefix = remember(context) { ShellEnvironment.prefixDir(context.applicationContext.filesDir) }
    var installingGit by remember(projectRoot) { mutableStateOf(false) }
    var gitInstallStartedAtSec by remember(projectRoot) { mutableStateOf(0L) }
    var gitInstallFailed by remember(projectRoot) { mutableStateOf(false) }
    // Phase 73.6 — the install button asks first (shared prompt); the
    // 73.2 "no confirmation dialog" decision is superseded by the owner's
    // explicit ask-first instruction.
    var showInstallPrompt by remember(projectRoot) { mutableStateOf(false) }
    // Phase 73.5 — elapsed seconds for the install status bar below.
    var gitInstallElapsedSec by remember(projectRoot) { mutableStateOf(0) }
    // Phase 73.8 — the honest progress signals `pkg` never gives: the
    // installer's live last line (read from the shared session's
    // transcript, only what appeared after this install started) and,
    // on failure, the last lines kept in the box (no Terminal redirect).
    var gitInstallLogLine by remember(projectRoot) { mutableStateOf<String?>(null) }
    var gitInstallFailTail by remember(projectRoot) { mutableStateOf<List<String>>(emptyList()) }
    var gitInstallLogBaseline by remember(projectRoot) { mutableStateOf(-1) }

    LaunchedEffect(installingGit) {
        if (!installingGit) return@LaunchedEffect
        while (installingGit) {
            delay(1_500)
            val (result, onDisk, transcript) = withContext(Dispatchers.IO) {
                val read = PkgResult.read(userlandPrefix)
                val installed = GitContext(context.applicationContext).gitBinary() != null
                Triple(read, installed, terminalViewModel.transcriptText())
            }
            if (gitInstallLogBaseline < 0) gitInstallLogBaseline = transcript.length
            val fresh = transcript.drop(gitInstallLogBaseline.coerceAtLeast(0))
            gitInstallLogLine = fresh.lineSequence()
                .map { it.trim() }
                .lastOrNull { it.isNotEmpty() }
                ?.take(140)
            fun failTail(): List<String> {
                val source = fresh.ifBlank { transcript }
                return source.lineSequence()
                    .map { it.trim().take(140) }
                    .filter { it.isNotEmpty() }
                    .toList()
                    .takeLast(6)
            }
            when (InstallOutcomes.decide(gitInstallTargets, gitInstallStartedAtSec, onDisk, result)) {
                InstallOutcome.INSTALLED -> {
                    installingGit = false
                    viewModel.refresh(context, projectRoot)
                }
                InstallOutcome.FAILED -> {
                    installingGit = false
                    gitInstallFailTail = failTail()
                    gitInstallFailed = true
                }
                // Phase 73.5 — `pkg` ended but git still is not on disk
                // (declined halfway, a partial write). Until now this
                // silently reverted to the INSTALL button with no
                // explanation; it now shows the same failed + RETRY state
                // as a non-zero exit, since the user-visible truth ("git
                // is still missing after an install ran") is identical.
                InstallOutcome.ENDED_WITHOUT_INSTALL -> {
                    installingGit = false
                    gitInstallFailTail = failTail()
                    gitInstallFailed = true
                }
                InstallOutcome.WAITING -> Unit
            }
        }
    }
    // Phase 73.5 — ticks the "Installing git… · Ns" label on the status
    // bar. `pkg` reports no percentage, so elapsed time + the live
    // installer line are the honest progress signals (see GitInstallCard).
    LaunchedEffect(installingGit) {
        while (installingGit) {
            delay(1_000)
            gitInstallElapsedSec =
                ((System.currentTimeMillis() / 1000) - gitInstallStartedAtSec)
                    .coerceAtLeast(0).coerceAtMost(5999).toInt()
        }
    }
    val onInstallGit: () -> Unit = {
        gitInstallFailed = false
        gitInstallStartedAtSec = System.currentTimeMillis() / 1000
        gitInstallElapsedSec = 0
        gitInstallLogLine = null
        gitInstallFailTail = emptyList()
        gitInstallLogBaseline = -1
        installingGit = true
        Toast.makeText(context, context.getString(R.string.git_installing), Toast.LENGTH_SHORT).show()
        terminalViewModel.sendCommand(gitPackage.installCommand)
    }

    LaunchedEffect(projectRoot) {
        viewModel.refresh(context, projectRoot)
    }

    // Phase 73.7 — no bottom-sheet wrapper: the drawer hosts this Column
    // directly (the owner's screenshots show git inside the side panel).
    // The whole panel scrolls on short screens; the UNSTAGED list keeps
    // its own capped height inside it.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // ---- header: REPOSITORY + search / branch / push -------------
        // Phase 73.5 — Spck screenshot 4: the title with three icon
        // buttons on the right (search filters the UNSTAGED list; the
        // branch button opens screenshot 1's menu; the push button
        // opens screenshot 3's). The branch chip keeps its own row
        // below so the current branch (or a detached HEAD) stays
        // visible without opening a menu.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.git_repository_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (state.gitInstalled && state.isRepo) {
                IconButton(onClick = { searchingGit = !searchingGit }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.git_search_changes)
                    )
                }
                Box {
                    IconButton(onClick = { showBranchMenu = true }) {
                        Icon(
                            SpckIcons.GitBranch,
                            contentDescription = stringResource(R.string.git_branch_menu_description)
                        )
                    }
                    DropdownMenu(
                        expanded = showBranchMenu,
                        onDismissRequest = { showBranchMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_branches_action)) },
                            leadingIcon = {
                                Icon(SpckIcons.GitBranch, contentDescription = null)
                            },
                            onClick = {
                                showBranchMenu = false
                                showBranchDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_remotes_action)) },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null)
                            },
                            onClick = {
                                showBranchMenu = false
                                viewModel.loadRemotes(context, projectRoot)
                                showRemotesDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_log_history_action)) },
                            leadingIcon = {
                                Icon(Icons.Default.History, contentDescription = null)
                            },
                            onClick = {
                                showBranchMenu = false
                                viewModel.loadCommits(context, projectRoot)
                                logDialogMode = GitLogDialogMode.VIEW
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_refresh_files_action)) },
                            leadingIcon = {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                            },
                            onClick = {
                                showBranchMenu = false
                                viewModel.refresh(context, projectRoot)
                            }
                        )
                    }
                }
                Box {
                    // Phase 73.8 — the owner's call: the share glyph read as
                    // "share this file", but the menu holds Commit All,
                    // Fetch, Pull, Push, Credentials… — a ⋮, honestly.
                    IconButton(onClick = { showPushMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.git_push_menu_description)
                        )
                    }
                    DropdownMenu(
                        expanded = showPushMenu,
                        onDismissRequest = { showPushMenu = false }
                    ) {
                        val pushMenuBranch = state.status?.branch
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (!pushMenuBranch.isNullOrBlank()) {
                                        stringResource(R.string.git_branch_menu_header, pushMenuBranch)
                                    } else {
                                        stringResource(
                                            R.string.git_branch_menu_header,
                                            stringResource(R.string.git_detached_head)
                                        )
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            enabled = false,
                            onClick = {}
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_commit_all_action)) },
                            leadingIcon = {
                                Icon(Icons.Default.DoneAll, contentDescription = null)
                            },
                            // Phase 73.7 — Spck's Commit All dialog asks
                            // for the message itself, so this only gates
                            // on "something to commit, no conflict open".
                            enabled = !state.busy && !state.loading &&
                                state.isRepo && !state.status?.files.isNullOrEmpty() &&
                                state.status?.files.orEmpty().none { it.isConflict },
                            onClick = {
                                showPushMenu = false
                                showCommitDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.git_revert_all_action),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                            },
                            enabled = state.status?.files?.isNotEmpty() == true &&
                                state.status?.noCommits != true,
                            onClick = {
                                showPushMenu = false
                                pendingRevertAll = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_checkout_commit_action)) },
                            leadingIcon = {
                                Icon(Icons.Default.Restore, contentDescription = null)
                            },
                            onClick = {
                                showPushMenu = false
                                viewModel.loadCommits(context, projectRoot)
                                logDialogMode = GitLogDialogMode.CHECKOUT
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_fetch_action)) },
                            leadingIcon = {
                                Icon(Icons.Default.Download, contentDescription = null)
                            },
                            onClick = {
                                showPushMenu = false
                                viewModel.fetch(context, projectRoot)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_pull)) },
                            leadingIcon = {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                            },
                            onClick = {
                                showPushMenu = false
                                viewModel.pull(context, projectRoot)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_push_action)) },
                            leadingIcon = {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                            },
                            // Phase 73.7 — Spck's Push dialog names the
                            // remote + branch; its lists load here so
                            // the dropdowns arrive filled.
                            enabled = !state.busy && !state.loading,
                            onClick = {
                                showPushMenu = false
                                viewModel.loadRemotes(context, projectRoot)
                                viewModel.loadBranches(context, projectRoot)
                                showPushDialog = true
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_credentials_action)) },
                            leadingIcon = {
                                Icon(Icons.Default.VpnKey, contentDescription = null)
                            },
                            onClick = {
                                showPushMenu = false
                                showCredentialsDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_provider_action)) },
                            leadingIcon = {
                                Icon(Icons.Default.Hub, contentDescription = null)
                            },
                            onClick = {
                                showPushMenu = false
                                showCredentialsDialog = true
                            }
                        )
                    }
                }
            }
        }
        // ---- branch chip (its own row since 73.5) ----------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Phase 73.3 — a checked-out COMMIT (not a branch) used to
            // make this chip vanish silently; a beginner who tapped
            // Checkout Commit would see no branch name anywhere. Show a
            // distinct, still-tappable chip (opens Switch Branch, the
            // existing way back) instead of nothing.
            when {
                state.status?.branch != null -> {
                    val branch = state.status?.branch!!
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showBranchDialog = true }
                            .border(
                                width = 1.dp,
                                // Phase 40.5 — 0.55 alpha measured 2.25:1 (needs
                                // 3:1 for a control boundary); opaque is 4.56:1.
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                SpckIcons.GitBranch,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = branch,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = stringResource(R.string.editor_drawer_switch_branch),
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                state.status?.detached == true -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showBranchDialog = true }
                            .border(
                                width = 1.dp,
                                color = UnpushedAmber,
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            // Phase 17 added this string, never wired to
                            // anything (no path produced detached HEAD
                            // until 73.3's Checkout Commit); reused as-is.
                            text = stringResource(R.string.git_detached_head),
                            style = MaterialTheme.typography.labelLarge,
                            color = UnpushedAmber,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Phase 73.5 — the header search field filters the UNSTAGED
        // list by path (Spck screenshot 4's search icon). The query
        // only narrows what is shown; commit/discard still act on the
        // real change set, never on the filtered view.
        if (searchingGit && state.gitInstalled && state.isRepo) {
            OutlinedTextField(
                value = gitSearchQuery,
                onValueChange = { gitSearchQuery = it },
                placeholder = { Text(stringResource(R.string.git_search_changes)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (gitSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { gitSearchQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        when {
            state.loading || state.busy -> {
                // Phase 52.2 — the source-control panel keeps a stable shape
                // while git reads; a spinner-only blank made a slow status
                // call look broken.
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    repeat(4) { SkeletonGitRow() }
                }
            }
            !state.gitInstalled -> {
                // Phase 73.8 — one install card for both states: the
                // userland's live state while the Linux tools are still
                // arriving (73.2's plain message is retired), git's own
                // install once the gate allows it.
                GitInstallCard(
                    setupFacts = setupFacts,
                    installAllowed = installGitVerdict.allowed,
                    installing = installingGit,
                    failed = gitInstallFailed,
                    elapsedSec = gitInstallElapsedSec,
                    liveLine = gitInstallLogLine,
                    failTail = gitInstallFailTail,
                    installCommand = gitPackage.installCommand,
                    onInstall = { showInstallPrompt = true }
                )
            }
            !state.isRepo -> {
                // Phase 73.3 — the owner's follow-up superseded 73.2's
                // "wording only" decision for this one screen: `git init`
                // was the last normal action still requiring the
                // terminal, so it is now a real button, not just clearer
                // text. Clone (a working GUI flow already, in Files) is
                // still named, not duplicated here.
                GitInitGuidance(
                    busy = state.busy,
                    onInit = { viewModel.initRepo(context, projectRoot) }
                )
            }
            else -> {
                // ---- Phase 40.1: readiness before the attempt ----------
                // The owner's symptom was an error rendered where he was
                // not looking ("it shows error in the background i can't
                // see it"). Readiness answers *before* the tap: what is
                // missing, in one sentence, with the remedy on the same row.
                val pushBlocker = state.readiness?.blocker(GitOp.PUSH)
                val pushReadiness = state.readiness?.message(GitOp.PUSH)
                when {
                    pushBlocker != null && pushReadiness != null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Text(
                            text = "!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = UnpushedAmber
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pushReadiness,
                                style = MaterialTheme.typography.bodySmall,
                                color = UnpushedAmber
                            )
                            if (pushBlocker == GitBlocker.NO_TOKEN) {
                                GitHelpLink(GitErrors.TOKEN_HELP_URL)
                            }
                            // Phase 73.9 — step 1 of the manual-first
                            // no-remote flow: create the repository on
                            // GitHub (most tokens lack the create
                            // permission, so Publish fails for them).
                            if (pushBlocker == GitBlocker.NO_REMOTE) {
                                GitHelpLink(
                                    GitHubPublish.NEW_REPO_URL,
                                    label = "Create the repository on GitHub ↗"
                                )
                            }
                        }
                        // Phase 73.9 — step 2 sits on the row itself:
                        // ADD REMOTE opens the Remotes dialog (lists
                        // pre-loaded, like the menu door); the PUBLISH
                        // button it replaces stays reachable through the
                        // after-push card for permissioned tokens.
                        if (pushBlocker == GitBlocker.NO_REMOTE) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.loadRemotes(context, projectRoot)
                                    showRemotesDialog = true
                                },
                                enabled = !state.busy,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text("ADD REMOTE", letterSpacing = 0.8.sp)
                            }
                        }
                        // Phase 73.8 — the token remedy sits on the row
                        // itself now (the owner's "now present in the git
                        // page"): one tap opens the Git Credentials
                        // dialog, no Settings detour.
                        if (pushBlocker == GitBlocker.NO_TOKEN) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { showCredentialsDialog = true },
                                enabled = !state.busy,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    stringResource(R.string.git_credentials_action),
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                    state.readiness?.isReady(GitOp.PUSH) == true -> Text(
                        text = "✓ GitHub ready",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
                // Phase 17 §2.5 — conflicts get their own group and block
                // the commit; everything else stays in "Changes".
                val files = state.status?.files.orEmpty()
                val conflicts = files.filter { it.isConflict }
                val others = files.filterNot { it.isConflict }

                // Phase 39.2 — "what will be committed" list + hygiene note.
                // Makes the ignore policy verifiable by a human instead of
                // by faith, and answers "why didn't my file push?".
                // (Phase 73.7 — the inline commit box + COMMIT & PUSH
                // button that used to sit here moved into Spck's Commit
                // All dialog; this projection stays, since Commit All
                // with Stage All on commits exactly what it lists.)
                state.hygieneNote?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
                state.commitPreview?.let { preview ->
                    if (preview.total > 0) {
                        Text(
                            text = stringResource(R.string.git_commit_preview_header, preview.total),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        preview.staged.forEach { entry ->
                            Text(
                                text = "  ${entry.status}  ${entry.path}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = CodecType.codeFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (preview.truncated) {
                            Text(
                                text = stringResource(
                                    R.string.git_commit_preview_more,
                                    (preview.total - preview.staged.size).coerceAtLeast(0)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 14.dp))

                // ---- conflicts (Phase 17 §2.5) -------------------------
                if (conflicts.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.git_conflicts_header),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ConflictPurple
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(ConflictPurple.copy(alpha = 0.18f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                conflicts.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = ConflictPurple
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.git_conflict_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        conflicts.forEachIndexed { index, change ->
                            GitChangeRow(
                                change = change,
                                projectFolderName = projectRoot.name,
                                onOpenDiff = {
                                    viewModel.openDiff(context, projectRoot, change.path)
                                },
                                onToggleStage = {
                                    viewModel.markResolved(context, projectRoot, change)
                                },
                                markResolvedMode = true
                            )
                            if (index < conflicts.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }

                // ---- UNSTAGED (Spck screenshot 4) ------------------------
                // Phase 73.5 — the changes list is the UNSTAGED section:
                // collapsible, with the count badge Spck shows. The one
                // deliberate omission is Spck's "+" stage-all button:
                // Phase 73.1 removed staging controls because COMMIT &
                // PUSH always runs `add -A` first, so a stage button here
                // would change the index without ever changing what gets
                // committed — a no-op dressed as an action. The header
                // search only narrows this list by path; commit/discard
                // still act on the real change set, never the filtered view.
                val visibleOthers = if (gitSearchQuery.isBlank()) {
                    others
                } else {
                    others.filter {
                        it.path.contains(gitSearchQuery.trim(), ignoreCase = true)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { unstagedExpanded = !unstagedExpanded }
                        .padding(vertical = 10.dp)
                ) {
                    Icon(
                        if (unstagedExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = stringResource(
                            if (unstagedExpanded) {
                                R.string.git_collapse_section
                            } else {
                                R.string.git_expand_section
                            }
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.git_unstaged_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(others.size.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Phase 73.8 — one grey line for newcomers (the owner's
                // "hard but ok"): what lands here, and what Commit All
                // does with it. No popup, no coach mark.
                Text(
                    text = stringResource(R.string.git_hint_unstaged),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                if (unstagedExpanded && files.isEmpty()) {
                    Text(
                        text = stringResource(R.string.git_working_tree_clean),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                    )
                } else if (unstagedExpanded && others.isEmpty()) {
                    Text(
                        text = stringResource(R.string.git_no_other_changes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                    )
                } else if (unstagedExpanded) {
                    if (visibleOthers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.git_no_matching_changes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                        )
                    } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .padding(vertical = 2.dp)
                    ) {
                        itemsIndexed(visibleOthers, key = { _, change -> change.path }) { index, change ->
                            GitChangeRow(
                                change = change,
                                projectFolderName = projectRoot.name,
                                onOpenDiff = {
                                    viewModel.openDiff(context, projectRoot, change.path)
                                },
                                onDiscard = if (GitDiscardPolicy.canDiscard(change)) {
                                    { pendingDiscard = change }
                                } else null
                            )
                            // Mockup: a hairline between every change row.
                            if (index < visibleOthers.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                    }
                }

                HorizontalDivider()

                // ---- PULL / REFRESH -------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.pull(context, projectRoot) },
                        enabled = !state.busy && !state.loading,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        // Mockup: the pull mark is the download arrow (↓ over a line).
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.git_pull), letterSpacing = 0.8.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.refresh(context, projectRoot) },
                        enabled = !state.busy && !state.loading,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.refresh), letterSpacing = 0.8.sp)
                    }
                }

                // ---- honest push state (Phase 17 device fix) -----------
                // A commit clears the change list, so a FAILED push used
                // to look exactly like a successful one. Whenever the
                // branch is ahead of its remote (or a push failed), say so
                // and offer a retry.
                val ahead = state.status?.ahead ?: 0
                // A branch that tracks nothing has no "ahead" figure at
                // all — it simply is not published yet, which is exactly
                // the case the owner hit with a freshly created branch.
                val unpublished = state.status?.unpublished == true
                // Phase 40.2 — a push result is *state*, not a toast: it
                // stays until it is dismissed or the user refreshes.
                val pushOk = state.lastResult?.ok == true
                if (ahead > 0 || state.pushError != null || unpublished ||
                    state.lastResult != null
                ) {
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = if (pushOk) "✓" else "↑",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (pushOk) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                UnpushedAmber
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (!pushOk) {
                                Text(
                                    text = when {
                                        ahead > 0 -> {
                                            val b = state.status?.branch
                                            if (!b.isNullOrBlank()) {
                                                stringResource(
                                                    R.string.git_unpushed_count_branch,
                                                    ahead,
                                                    b
                                                )
                                            } else {
                                                stringResource(R.string.git_unpushed_count, ahead)
                                            }
                                        }
                                        state.pushError != null ->
                                            stringResource(R.string.git_unpushed_unknown)
                                        else -> stringResource(
                                            R.string.git_unpushed_new_branch,
                                            state.status?.branch ?: ""
                                        )
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UnpushedAmber
                                )
                            }
                            // Phase 40.2 — what actually happened to the last
                            // push, named branch and all.
                            state.lastResult?.let { result ->
                                PushResultCard(
                                    result = result,
                                    onDismiss = { viewModel.dismissPushResult() },
                                    onPublish = { showPublishDialog = true }
                                )
                            }
                            state.pushError?.let { error ->
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            // Phase 17 follow-up — a tappable help link
                            // (the GitHub token page) when the failure has
                            // one, so "no token" is one tap from the fix.
                            state.pushHelpUrl?.let { url ->
                                GitHelpLink(url)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(
                            // Phase 73.7 — the retry also names its
                            // target first (same dialog as the menu).
                            onClick = {
                                viewModel.loadRemotes(context, projectRoot)
                                viewModel.loadBranches(context, projectRoot)
                                showPushDialog = true
                            },
                            enabled = !state.busy && !state.loading,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            val pushBranch = state.status?.branch
                            Text(
                                text = if (!pushBranch.isNullOrBlank()) {
                                    stringResource(R.string.git_push_to, pushBranch)
                                } else {
                                    stringResource(R.string.git_push_action)
                                },
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (state.diffPath != null) {
        GitDiffDialog(
            path = state.diffPath!!,
            loading = state.diffLoading,
            lines = state.diffLines,
            onClose = { viewModel.closeDiff() }
        )
    }

    // Phase 17 — Switch Branch, opened from the branch chip.
    if (showBranchDialog) {
        BranchSwitchDialog(
            projectRoot = projectRoot,
            onDismiss = { showBranchDialog = false },
            onBeforeSwitch = onBeforeBranchSwitch,
            onAfterSwitch = onAfterBranchSwitch
        )
    }

    // Phase 40.3 — Publish to GitHub (create the remote when there isn't one).
    if (showPublishDialog) {
        PublishToGitHubDialog(
            defaultName = projectRoot.name,
            busy = state.publishBusy,
            error = state.publishError,
            note = state.publishNote,
            needsPermission = state.publishNeedsPermission,
            onDismiss = {
                showPublishDialog = false
                viewModel.dismissPublish()
            },
            onPublish = { name, description, isPrivate ->
                viewModel.publishToGitHub(
                    context = context,
                    projectRoot = projectRoot,
                    repoName = name,
                    description = description.takeIf { it.isNotBlank() },
                    isPrivate = isPrivate
                )
            },
            onAttach = { url -> viewModel.attachRemoteToGitHub(context, projectRoot, url) }
        )
    }

    // Phase 73.3 — Log History / Checkout Commit share one commit list.
    logDialogMode?.let { mode ->
        GitLogDialog(
            mode = mode,
            commits = state.commits,
            loading = state.commitsLoading,
            error = state.commitsError,
            onDismiss = {
                logDialogMode = null
                viewModel.clearCommits()
            },
            onCheckout = { entry ->
                viewModel.checkoutCommit(context, projectRoot, entry)
                logDialogMode = null
                viewModel.clearCommits()
            }
        )
    }

    // Phase 73.3 — the general Remotes screen (view/add/remove).
    if (showRemotesDialog) {
        GitRemotesDialog(
            remotes = state.remotes,
            loading = state.remotesLoading,
            busy = state.remotesBusy,
            error = state.remotesError,
            onDismiss = {
                showRemotesDialog = false
                viewModel.clearRemotes()
            },
            onAdd = { name, url -> viewModel.addRemote(context, projectRoot, name, url) },
            onRemove = { name -> viewModel.removeRemote(context, projectRoot, name) }
        )
    }

    // Phase 73.7 — Spck's Commit All dialog (message + identity + Stage
    // All); Ok saves the identity when changed, then stages (when on)
    // and commits. The credentials row stacks the Git Credentials
    // dialog ABOVE this one (the draft underneath is kept).
    if (showCommitDialog) {
        GitCommitDialog(
            busy = state.busy,
            canCommit = !state.status?.files.isNullOrEmpty() &&
                state.status?.files.orEmpty().none { it.isConflict },
            onDismiss = { showCommitDialog = false },
            onOpenCredentials = { showCredentialsDialog = true },
            onConfirm = { message, stageAll ->
                viewModel.commitOnly(context, projectRoot, message, stageAll)
                showCommitDialog = false
            }
        )
    }

    // Phase 73.7 — Spck's Push dialog (remote + branch + credentials);
    // Ok pushes to the chosen pair, then closes. The no-remote path
    // trades this dialog for the Remotes screen (sequential, not
    // stacked); the credentials row stacks like the Commit dialog's.
    if (showPushDialog) {
        GitPushDialog(
            remotes = state.remotes.map { it.name },
            // Push targets a local branch (Spck pushes the checked-out
            // one); remote-tracking names would confuse the pair.
            branches = state.branches?.local?.map { it.name }.orEmpty(),
            currentBranch = state.status?.branch,
            busy = state.busy,
            onDismiss = {
                showPushDialog = false
                viewModel.clearRemotes()
            },
            onAddRemote = {
                showPushDialog = false
                viewModel.loadRemotes(context, projectRoot)
                showRemotesDialog = true
            },
            onOpenCredentials = { showCredentialsDialog = true },
            onConfirm = { remote, branch ->
                viewModel.push(context, projectRoot, remote, branch)
                showPushDialog = false
            }
        )
    }

    // Phase 73.6 — the install button asks first (the shared prompt);
    // confirming starts the background install under the status bar.
    if (showInstallPrompt) {
        GitInstallPromptDialog(
            onConfirm = {
                showInstallPrompt = false
                onInstallGit()
            },
            onDismiss = { showInstallPrompt = false }
        )
    }

    // Phase 73.5 — the inline Git Credentials dialog (Spck screenshot
    // 2). "Manage" leaves the panel for Settings' own editor (a
    // full-screen route, not a dialog); saving re-reads readiness so a
    // fresh token can clear the NO_TOKEN blocker without a manual
    // refresh.
    if (showCredentialsDialog) {
        GitCredentialsDialog(
            onDismiss = { showCredentialsDialog = false },
            onManage = {
                showCredentialsDialog = false
                onOpenSettings()
            },
            onSaved = { viewModel.refresh(context, projectRoot) }
        )
    }

    // Phase 73.3 — Revert All is the one destructive action here that is
    // not limited to a single file, so it confirms first (matching the
    // owner's answer, unlike the per-file discard which is already narrow
    // enough not to need one).
    if (pendingRevertAll) {
        AlertDialog(
            onDismissRequest = { pendingRevertAll = false },
            title = { Text(stringResource(R.string.git_revert_all_title)) },
            text = { Text(stringResource(R.string.git_revert_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRevertAll = false
                        viewModel.revertAll(context, projectRoot)
                    }
                ) {
                    Text(
                        stringResource(R.string.git_revert_all_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevertAll = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    }

    pendingDiscard?.let { change ->
        AlertDialog(
            onDismissRequest = { pendingDiscard = null },
            title = { Text(stringResource(R.string.git_discard_title)) },
            text = { Text(stringResource(R.string.git_discard_confirm, change.path)) },
            confirmButton = {
                TextButton(
                    enabled = !state.busy && !state.loading && !state.branchBusy && !state.publishBusy,
                    onClick = {
                        pendingDiscard = null
                        viewModel.discardUnstaged(context, projectRoot, change)
                    }
                ) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscard = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (state.discardBusy) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.git_discard_progress)) },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )
    }

}

/**
 * Phase 40.2 — the result card. Deliberately a projection of git's own bytes
 * (never a green tick we invented): the branch it refers to is always named,
 * because "push stayed local" was exactly the case where the UI did not say
 * which branch it was talking about.
 */
@Composable
private fun PushResultCard(
    result: PushOutcome,
    onDismiss: () -> Unit,
    onPublish: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        when (result) {
            is PushOutcome.Pushed -> {
                Text(
                    text = "✓ Pushed ${result.branch} → ${shortRemote(result.remoteUrl)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                val detail = when {
                    result.newBranch -> "new branch on GitHub"
                    result.from != null -> "${result.from}..${result.to}"
                    else -> null
                }
                if (detail != null) {
                    Text(
                        text = "  $detail",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is PushOutcome.UpToDate -> Text(
                text = "↑ Everything up-to-date — nothing was pushed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is PushOutcome.Rejected -> Text(
                text = "✗ Push rejected — ${result.hint}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            is PushOutcome.NoRemote -> {
                Text(
                    text = "↑ Still local — ${result.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = UnpushedAmber
                )
                TextButton(onClick = onPublish) { Text("PUBLISH TO GITHUB") }
            }
            is PushOutcome.Auth -> Text(
                text = "✗ ${result.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            is PushOutcome.Failed -> {
                Text(
                    text = "✗ ${result.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                result.detail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text(
            text = "Dismiss",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onDismiss() }
                .padding(vertical = 2.dp, horizontal = 2.dp)
        )
    }
}

/**
 * Phase 40.3 — the Publish dialog.
 *
 * Two hard rules from the spec are visible here: **private by default** (a
 * phone IDE pushing a folder called `taxes` to a public repository is a data
 * leak — GitHub's own default is public, so `private` is sent explicitly), and
 * **the failure message lives in the window the user is looking at** (the
 * Phase 40.1 lesson: never a snackbar alone).
 */
@Composable
private fun PublishToGitHubDialog(
    defaultName: String,
    busy: Boolean,
    error: String?,
    note: String?,
    needsPermission: String?,
    onDismiss: () -> Unit,
    onPublish: (name: String, description: String, isPrivate: Boolean) -> Unit,
    onAttach: (url: String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(GitHubPublish.nameFor(defaultName)) }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }
    var existingUrl by remember { mutableStateOf("") }

    // Success closes the dialog — the panel keeps the result card and note.
    LaunchedEffect(note) {
        if (!note.isNullOrBlank()) onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy
        ),
        title = {
            Text(
                text = "Publish to GitHub",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Creates the repository on GitHub, adds it as origin, and " +
                        "pushes this branch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    enabled = !busy,
                    label = { Text("Repository name") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    singleLine = true,
                    enabled = !busy,
                    label = { Text("Description (optional)") }
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        enabled = !busy
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Private repository", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = "GitHub's own default is public — this stays private unless " +
                        "you turn it off.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                needsPermission?.let { needs ->
                    Text(
                        text = "GitHub asked for: $needs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (error != null) {
                    Text(
                        text = "Open github.com/new ↗",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(GitHubPublish.NEW_REPO_URL))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                            .padding(vertical = 4.dp)
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text(
                    text = "Already created it in the browser?",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = existingUrl,
                    onValueChange = { existingUrl = it },
                    singleLine = true,
                    enabled = !busy,
                    placeholder = { Text("https://github.com/user/repo.git") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(name, description, isPrivate) },
                enabled = !busy && name.isNotBlank()
            ) {
                Text(if (busy) "PUBLISHING…" else "PUBLISH")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onAttach(existingUrl.trim()) },
                    enabled = !busy && existingUrl.isNotBlank()
                ) {
                    Text("ATTACH")
                }
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

/** `https://github.com/u/r.git` → `github.com/u/r` for one-line result cards. */
private fun shortRemote(url: String?): String {
    if (url.isNullOrBlank()) return "GitHub"
    return url
        .removePrefix("https://")
        .removePrefix("http://")
        .removeSuffix(".git")
        .trimEnd('/')
}

@Composable
private fun SheetGuidance(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
    )
}

/**
 * Phase 73.2 — shown instead of [SheetGuidance] when git is missing but the
 * Linux tools it needs are already ready, so a real one-tap install is
 * actually possible from here.
 *
 * Phase 73.5 — the installing state is a status bar, not a spinner row:
 * the command still runs in the shared terminal session underneath, but
 * the user is never redirected there — this bar (elapsed seconds + an
 * indeterminate track, since `pkg` reports no percentage) is the whole
 * progress surface, and it finishes right here in the panel.
 *
 * Phase 73.8 — the install card (the owner's "like in package part"):
 * `PackageItemCard`'s shape — a Card with a header row, the command, and
 * a status badge — with git's two install states inside. While the
 * Linux-tools gate refuses, the card shows the USERLAND's live state
 * (the Terminal tab's own stage words via [TerminalStatusLabel], with the
 * installer's real download % whenever it knows one — nothing here is
 * fabricated); once the gate allows, it shows git's own install
 * (elapsed + the installer's live last line while running, the last
 * lines in the box on failure). Nothing ever redirects to Terminal.
 */
@Composable
private fun GitInstallCard(
    setupFacts: SetupFacts,
    installAllowed: Boolean,
    installing: Boolean,
    failed: Boolean,
    elapsedSec: Int,
    liveLine: String?,
    failTail: List<String>,
    installCommand: String,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        shape = RoundedCornerShape(CodecTokens.radius(Radius.M)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = CodecTokens.elevation(CodecTokens.Elevation.RAISED))
    ) {
        Column(modifier = Modifier.padding(CodecTokens.space(Space.L))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.git_install_action),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = installCommand,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = CodecType.codeFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (installing || failed) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(CodecTokens.radius(Radius.S)))
                            .background(
                                if (failed) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                }
                            )
                            .padding(
                                horizontal = CodecTokens.space(Space.S),
                                vertical = CodecTokens.space(Space.XS)
                            )
                    ) {
                        Text(
                            text = if (failed) "FAILED" else "INSTALLING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (failed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
            if (!installAllowed) {
                UserlandInstallSection(setupFacts = setupFacts)
            } else {
                Text(
                    text = stringResource(R.string.git_install_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                when {
                    installing -> Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        Text(
                            text = stringResource(R.string.git_install_progress, elapsedSec),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        liveLine?.let { line ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.git_install_live_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = CodecType.codeFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.git_install_background_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    failed -> Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text(
                            text = stringResource(R.string.git_install_failed_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (failTail.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.git_install_failed_tail_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            failTail.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = CodecType.codeFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onInstall,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(top = 8.dp).height(42.dp)
                        ) {
                            Text(stringResource(R.string.install_label_retry), letterSpacing = 0.8.sp)
                        }
                    }
                    else -> OutlinedButton(
                        onClick = onInstall,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(top = 12.dp).height(46.dp)
                    ) {
                        Text(stringResource(R.string.git_install_action), letterSpacing = 0.8.sp)
                    }
                }
            }
        }
    }
}

/**
 * Phase 73.8 — the card's Linux-tools section, shown while the userland
 * gate refuses the git install. The stage words are the Terminal tab's
 * own ([TerminalStatusLabel.label] — one wording, two screens); the bar is
 * determinate only while the installer reports a real download %,
 * indeterminate otherwise (an unknown is shown as an unknown).
 */
@Composable
private fun UserlandInstallSection(setupFacts: SetupFacts) {
    val progress = setupFacts.progress
    val stageLabel = when (progress.stage) {
        // The shared label says "starting shell…" here (its subject is
        // the shell); the card's subject is the tools, so it says so.
        SetupStage.CHECKING -> stringResource(R.string.git_userland_checking)
        // The shared label's FAILED wording points at the Terminal tab's
        // own retry button; this card has no such button, so it keeps
        // the stage word and lets the refusal sentence below carry the
        // guidance instead of quoting a control that isn't here.
        SetupStage.FAILED -> stringResource(R.string.git_userland_failed)
        else -> TerminalStatusLabel.label(
            TerminalLifecycle.STARTING,
            progress.stage,
            progress.percent
        ).text
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.git_userland_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stageLabel,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        val percent = progress.percent
        if (progress.stage == SetupStage.DOWNLOADING && percent != null) {
            LinearProgressIndicator(
                progress = { percent.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = SetupGatePolicy.refusal(SetupAction.INSTALL_PACKAGE, progress, setupFacts),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.git_userland_git_wait),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Phase 73.3 — shown instead of [SheetGuidance] for "this folder isn't a Git
 * repository yet": a real `git init` button, since that was the last normal
 * Source Control action still requiring the terminal. Cloning an existing
 * repository (a working GUI flow already, Files → ⋮ → Clone from GitHub) is
 * named, not duplicated here as a second button.
 */
@Composable
private fun GitInitGuidance(
    busy: Boolean,
    onInit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.git_not_a_repo_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.git_init_busy),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            OutlinedButton(
                onClick = onInit,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(top = 12.dp).height(46.dp)
            ) {
                Text(stringResource(R.string.git_init_action), letterSpacing = 0.8.sp)
            }
        }
    }
}

/**
 * A tappable "Create a GitHub token ↗" link, opened in the browser via an
 * ACTION_VIEW intent (the app has no in-app browser; this is the same
 * approach the Settings screen uses for its GitHub links).
 */
@Composable
private fun GitHelpLink(url: String, label: String = "Create a GitHub token ↗") {
    val context = LocalContext.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            .padding(vertical = 2.dp)
    )
}

/** Typed icon per extension — Spck marks python/html files distinctly. */
@Composable
private fun GitFileIcon(name: String) {
    FileIconView(
        name = name,
        isDirectory = false,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * One change row. For a conflicted file ([markResolvedMode]) the trailing
 * control is Spck's ✓ "Mark Resolved". An ordinary change row has no stage
 * toggle: Phase 73.1 removed it — Commit All always stages everything
 * (`stageAll` before `commit`, matching the "what will be committed" preview,
 * which already projects every listed change as if `add -A` had run), so the
 * old +/− button changed the git index without ever changing what the one
 * commit action in this panel would commit. Kept for its actual use — marking
 * a conflict resolved — with the same [onToggleStage] callback, now only
 * wired when [markResolvedMode] is true.
 */
@Composable
private fun GitChangeRow(
    change: GitFileChange,
    projectFolderName: String,
    onOpenDiff: () -> Unit,
    onToggleStage: (() -> Unit)? = null,
    markResolvedMode: Boolean = false,
    onDiscard: (() -> Unit)? = null,
) {
    val accent = badgeColor(change.state)
    val fileName = change.path.substringAfterLast('/')
    val parent = change.path.substringBeforeLast('/', "")
    val folderPath = if (parent.isEmpty()) "/$projectFolderName" else "/$projectFolderName/$parent"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDiff)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .padding(end = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            GitFileIcon(fileName)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = folderPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            change.oldPath?.let { old ->
                Text(
                    text = stringResource(R.string.git_renamed_from, old),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = change.badge,
            color = accent,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 16.sp,
            fontFamily = CodecType.codeFamily,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 12.dp)
        )
        if (onDiscard != null) {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) }
        }
        // Phase 17 ✓ "Mark Resolved" for a conflicted path — the ordinary
        // (non-conflict) row has no trailing control here any more.
        if (markResolvedMode && onToggleStage != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        // Phase 40.5 — 0.6 alpha measured 2.55:1; opaque conflict
                        // purple is 4.81:1 (an inactive control is exempt, but the
                        // boundary still identifies the button, so it uses outline).
                        color = ConflictPurple,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable(onClick = onToggleStage),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.git_mark_resolved),
                    modifier = Modifier.size(20.dp),
                    tint = ConflictPurple
                )
            }
        }
    }
}

@Composable
private fun GitDiffDialog(
    path: String,
    loading: Boolean,
    lines: List<DiffLine>,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.git_diff_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = path,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = CodecType.codeFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                }
                HorizontalDivider()
                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .padding(vertical = 8.dp)
                    ) {
                        items(lines) { line ->
                            val color = when (line.op) {
                                DiffOp.ADD -> DiffAddColor
                                DiffOp.REMOVE -> DiffRemoveColor
                                DiffOp.CONTEXT -> MaterialTheme.colorScheme.onSurface
                            }
                            val marker = when (line.op) {
                                DiffOp.ADD -> "+"
                                DiffOp.REMOVE -> "-"
                                DiffOp.CONTEXT -> " "
                            }
                            Text(
                                text = "$marker${line.text}",
                                color = color,
                                fontFamily = CodecType.codeFamily,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (lines.isEmpty()) {
                        Text(
                            text = stringResource(R.string.git_diff_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

private val DiffAddColor = Color(0xFF66BB6A)
private val DiffRemoveColor = Color(0xFFEF5350)

/** Spck marks merge conflicts purple (Phase 17 §2.5). */
private val ConflictPurple = Color(0xFFBA68C8)

/** "Not pushed yet" — amber, so it reads as a warning, not an error. */
private val UnpushedAmber = Color(0xFFE6B33C)

private fun badgeColor(state: GitFileState): Color = when (state) {
    GitFileState.MODIFIED -> Color(0xFFE6B33C)
    GitFileState.ADDED -> DiffAddColor
    GitFileState.DELETED -> DiffRemoveColor
    GitFileState.UNTRACKED -> Color(0xFF9E9E9E)
    GitFileState.RENAMED -> Color(0xFF64B5F6)
    GitFileState.UNMERGED -> Color(0xFFBA68C8)
}
