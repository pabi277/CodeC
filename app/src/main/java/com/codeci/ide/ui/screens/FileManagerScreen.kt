package com.codeci.ide.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Radius
import com.codeci.ide.ui.theme.CodecTokens.Space
import com.codeci.ide.ui.theme.CodecMotion
import com.codeci.ide.ui.theme.rememberMotionSpecs
import com.codeci.ide.R
import com.codeci.ide.ui.components.SpckIcons
import com.codeci.ide.ui.components.StarterTile
import com.codeci.ide.ui.components.HapticMoment
import com.codeci.ide.ui.components.rememberCodecHaptics
import com.codeci.ide.ui.components.PressableSurface
import com.codeci.ide.ui.components.SkeletonHubCard
import com.codeci.ide.ui.components.SkeletonFileTreeRow
import com.codeci.ide.ui.projects.HubListBranch
import com.codeci.ide.ui.projects.HubListFacts
import com.codeci.ide.ui.projects.HubListPolicy
import com.codeci.ide.ui.projects.FileNode
import com.codeci.ide.ui.projects.GitManager
import com.codeci.ide.ui.projects.ProjectHubEntry
import com.codeci.ide.ui.projects.ProjectHubFilter
import com.codeci.ide.ui.components.FileIconView
import com.codeci.ide.ui.components.ProjectIconView
import com.codeci.ide.ui.theme.CodecPalette
import com.codeci.ide.ui.projects.ProjectInfo
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.projects.ProjectNameCheck
import com.codeci.ide.ui.projects.ProjectNameProblem
import com.codeci.ide.ui.projects.ProjectNameVerdict
import com.codeci.ide.ui.projects.ProjectTransfer
import com.codeci.ide.ui.projects.ProjectTypes
import com.codeci.ide.ui.projects.ProjectsHub
import com.codeci.ide.ui.projects.WelcomeStarter
import com.codeci.ide.ui.projects.WelcomeStarters
import com.codeci.ide.ui.viewmodels.FileManagerViewModel
import com.codeci.ide.ui.navigation.BackAction
import com.codeci.ide.ui.navigation.BackRouter
import com.codeci.ide.ui.navigation.BackState
import com.codeci.ide.ui.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Projects Hub (Phase 15) + private, hierarchical source tree (Phase 8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: FileManagerViewModel = viewModel(),
    /**
     * Phase 47.1 — the editor drawer's `+ New project…` lands on the hub with
     * the `+` sheet already up (the route's `openSheet=1`); a plain tab tap
     * leaves it false.
     */
    openAddSheet: Boolean = false,
    onFileSelected: (String) -> Unit = {},
    onProjectFileSelected: (projectName: String, relativePath: String) -> Unit = { _, path -> onFileSelected(path) },
    /**
     * Phase 46.2 — the single-file peek: tapping a file in the hub's tree
     * opens THAT file in the editor (one buffer, real path, no project
     * chrome). "Open in editor" on the card's ⋮ is the whole-project action
     * and keeps riding [onProjectFileSelected].
     */
    onProjectFilePeek: (projectName: String, relativePath: String) -> Unit = { _, path -> onFileSelected(path) },
    onProjectSelected: (ProjectInfo) -> Unit = {},
    onPreviewFile: (String) -> Unit = {},
    onProjectPreviewFile: (projectName: String, relativePath: String) -> Unit = { _, path -> onPreviewFile(path) },
    onRunProjectFile: (projectName: String, relativePath: String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    /** Phase 52.1 — the visible return door, supplied by the activity session. */
    resumeFacts: com.codeci.ide.ui.projects.ResumeFacts? = null,
    showResumeOffer: Boolean = false,
    onResumeContinue: () -> Unit = {},
    onResumeDecline: () -> Unit = {},
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val hubEntries by viewModel.hubEntries.collectAsState()
    // Phase 51.3 — the hub's third state (LOADING / EMPTY / LIST) and the
    // count its skeleton honours. Pure decision: HubListPolicy.
    val hubFacts by viewModel.hubListFacts.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val tree by viewModel.tree.collectAsState()
    val treeLoading by viewModel.treeLoading.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val cloneError by viewModel.cloneError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    // Phase 51.4 — the haptics hook (the switch is read inside it).
    val haptics = rememberCodecHaptics()

    var showCreateProject by remember { mutableStateOf(false) }
    var showCreateItem by remember { mutableStateOf(false) }
    var newItemParent by remember { mutableStateOf("") }
    var newItemFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteProjectTarget by remember { mutableStateOf<ProjectInfo?>(null) }
    var renameProjectTarget by remember { mutableStateOf<ProjectInfo?>(null) }
    var zipImportUri by remember { mutableStateOf<Uri?>(null) }
    var zipProjectName by remember { mutableStateOf("imported_project") }
    var showZipNameDialog by remember { mutableStateOf(false) }
    var exportProjectName by remember { mutableStateOf<String?>(null) }
    var showActionsMenu by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var gitSheetProject by remember { mutableStateOf<ProjectInfo?>(null) }
    // Phase 17 — Switch Branch, opened from the Projects card ⋮.
    var branchSheetProject by remember { mutableStateOf<ProjectInfo?>(null) }
    // Phase 15 — Projects Hub presentation state.
    var hubFilter by remember { mutableStateOf(ProjectHubFilter.ALL) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showHubSheet by remember { mutableStateOf(false) }
    // Phase 66.1 — the search field takes focus when the 🔍 opens it: the tap
    // used to show a field and no keyboard, so every search cost a second tap.
    // (The request is made beside the field, once the field is in the tree —
    // a FocusRequester with no node attached throws.)
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Phase 47.1 — the editor drawer's `+ New project…` hand-off (openSheet=1):
    // the sheet opens once on arrival. Route args survive state restore, so
    // the flag is consumed ONCE per arrival (rememberSaveable) — otherwise a
    // rotation would reopen a sheet the user had just dismissed.
    var sheetArgConsumed by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(false)
    }
    LaunchedEffect(openAddSheet) {
        if (openAddSheet && !sheetArgConsumed) {
            sheetArgConsumed = true
            showHubSheet = true
        }
    }

    // Phase 49.1 — the hub's file tree is ViewModel state (activeProject),
    // not a navigation entry, so back at an open project used to fall
    // through to the root handler and EXIT THE APP (owner 4.iv, hub half:
    // *"After clicking 3 ber if user use back botton it will [close] the
    // file view and show the editor not full app close"*). The router gives
    // back a row for the tree: close it — the same closeProject() the
    // breadcrumb's root runs — never the app. Sheets and dialogs own their
    // own back (row 4 → None), and the list view (activeProject == null)
    // leaves back to the root handler (pop / exit prompt).
    val hubDialogOpen = showHubSheet || showActionsMenu || showCreateProject ||
        showCreateItem || showCloneDialog || showZipNameDialog ||
        renameTarget != null || deleteTarget != null ||
        deleteProjectTarget != null || renameProjectTarget != null ||
        gitSheetProject != null || branchSheetProject != null
    val hubBackAction = BackRouter.decide(
        BackState(
            hubProjectOpen = activeProject != null,
            sheetOrDialogOpen = hubDialogOpen
        )
    )
    BackHandler(enabled = hubBackAction != BackAction.None) {
        when (hubBackAction) {
            BackAction.CloseHubProject -> {
                AppLogger.i(
                    "Back",
                    "hub press closing project tree (activeProject=${activeProject?.name})"
                )
                viewModel.closeProject()
            }
            else -> Unit
        }
    }

    // Phase 46.1 — the folder-import launcher is GONE (owner: "i want to
    // remove it completely"): the `+` sheet's Open Folder row, its SAF tree
    // picker and the ViewModel's folder walk were all deleted together.
    // Import ZIP, Import file, Export and "Open with CodeC" are different
    // features and all stay (PART_46_1 §boundary).

    val fileImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFile(context, uri) { imported ->
                onProjectSelected(imported)
            }
        }
    }
    val zipImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            zipProjectName = "imported_project"
            zipImportUri = uri
            showZipNameDialog = true
            viewModel.suggestZipProjectName(context, uri) { suggested ->
                if (zipImportUri == uri) zipProjectName = suggested
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val projectName = exportProjectName
        exportProjectName = null
        if (uri != null && projectName != null) viewModel.exportProject(context, projectName, uri)
    }

    // Phase 42.3 §3 — "everything" backup: the hub menu's export/import of
    // ALL project roots as one ZIP (the uninstall-eats-your-work answer),
    // beside the existing per-project export.
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.exportAllProjects(context, uri)
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importProjectsBackup(context, uri)
    }

    LaunchedEffect(Unit) { viewModel.loadProjects(context) }
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    fun selectProject(project: ProjectInfo) {
        viewModel.openProject(context, project.name)
        onProjectSelected(project)
    }

    // 2026-09-28, owner: *"when I click on a project it should open the editor
    // by default and not the file structure"*, and the same for the wizard's
    // Create. Phase 46.2's rule decides which file: launch default → newest
    // source → first source (ProjectEntryFile). A project with nothing to open
    // falls back to the hub's own tree, which is also still one ⋮ → Browse
    // files away, and one ☰ away inside the editor (the drawer's Files slot).
    fun openInEditor(project: ProjectInfo) {
        viewModel.entryFileForEditor(context, project.name) { entry ->
            if (entry != null) {
                onProjectSelected(project)
                onProjectFileSelected(project.name, entry)
            } else {
                selectProject(project)
            }
        }
    }

    // Phase 33.3 — the Projects empty state points at the three 33.1 starter
    // tiles: tapping one create-or-opens the starter project and opens its
    // entry file in the editor (idempotent — a second tap reuses it).
    fun startFromStarter(starter: WelcomeStarter) {
        scope.launch {
            val project = withContext(Dispatchers.IO) {
                WelcomeStarters.ensureProject(ProjectManager(context), starter)
            }
            if (project != null) {
                onProjectSelected(project)
                onProjectFileSelected(project.name, starter.entryFile)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (activeProject == null) {
                        if (searchOpen) {
                            androidx.compose.material3.TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.hub_search_hint)) },
                                singleLine = true,
                                // Phase 66.1 — the keyboard's action key is Search and
                                // it dismisses the keyboard; the list filters as you type.
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocus),
                                colors = androidx.compose.material3.TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                            LaunchedEffect(Unit) { searchFocus.requestFocus() }
                        } else {
                            // Mockup-exact: a large bold screen title, not a
                            // small app-bar caption.
                            Text(
                                stringResource(R.string.projects_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Column {
                            Text(activeProject!!.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                // Phase 66.1 — the same word the card's subtitle uses
                                // ("Python server"), not the raw config id ("python-flask",
                                // "auto"): one project, one name for its kind.
                                hubKindLabel(activeProject!!, hubEntries),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (activeProject != null) {
                        IconButton(onClick = { viewModel.closeProject() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (activeProject == null) {
                        // Phase 15 — inline project-name search (D7: names only;
                        // in-file search is recorded as deferred).
                        IconButton(onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) searchQuery = ""
                        }) {
                            Icon(
                                if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = stringResource(R.string.search)
                            )
                        }
                    }
                    IconButton(onClick = { showActionsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(
                        expanded = showActionsMenu,
                        onDismissRequest = { showActionsMenu = false }
                    ) {
                        if (activeProject == null) {
                            // The unified `+` sheet is the single entry point
                            // (Phase 15); the menu keeps just its shortcuts.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.new_project)) },
                                leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
                                onClick = {
                                    showActionsMenu = false
                                    showHubSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.refresh_projects)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showActionsMenu = false
                                    viewModel.refresh(context)
                                }
                            )
                            HorizontalDivider()
                            // Phase 42.3 §3 — "everything" backup: one ZIP
                            // over every project root (shared budgets), and
                            // its restore into a clean install. The "your
                            // data is safe" answer a test phase must ship.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_all_projects)) },
                                onClick = {
                                    showActionsMenu = false
                                    val stamp = java.text.SimpleDateFormat(
                                        "yyyyMMdd-HHmm", java.util.Locale.US
                                    ).format(java.util.Date())
                                    backupExportLauncher.launch("codec-projects-backup-$stamp.zip")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_projects_backup)) },
                                onClick = {
                                    showActionsMenu = false
                                    backupImportLauncher.launch(
                                        arrayOf("application/zip", "application/octet-stream")
                                    )
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.refresh_project_tree)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showActionsMenu = false
                                    viewModel.refresh(context)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.source_control_title)) },
                                leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                                onClick = {
                                    showActionsMenu = false
                                    gitSheetProject = activeProject
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_file)) },
                                leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                                onClick = {
                                    showActionsMenu = false
                                    fileImportLauncher.launch(arrayOf("*/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_zip)) },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showActionsMenu = false
                                    activeProject?.let {
                                        exportProjectName = it.name
                                        exportLauncher.launch("${it.name}.zip")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.new_file)) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showActionsMenu = false
                                    newItemParent = ""
                                    newItemFolder = false
                                    showCreateItem = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            if (activeProject == null) {
                // Mockup-exact `+`: a large flat purple circle, not a raised
                // M3 FAB.
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = { showHubSheet = true }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.new_project),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(CodecTokens.space(Space.XXL))
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (activeProject == null) {
                Column(Modifier.fillMaxSize()) {
                    if (showResumeOffer && resumeFacts != null) {
                        ResumeOfferCard(
                            facts = resumeFacts,
                            onContinue = onResumeContinue,
                            onDecline = onResumeDecline,
                            modifier = Modifier.padding(
                                horizontal = CodecTokens.space(Space.L),
                                vertical = CodecTokens.space(Space.S),
                            ),
                        )
                    }
                    ProjectsHubList(
                        entries = hubEntries,
                        facts = hubFacts,
                        skeletonRows = viewModel.lastKnownHubRowCount(),
                        filter = hubFilter,
                        searchQuery = searchQuery,
                        onFilterSelected = { hubFilter = it },
                        onCardAction = { entry, action ->
                        val project = projects.firstOrNull { it.name == entry.name } ?: return@ProjectsHubList
                        when (action) {
                            // ⋮ → Browse files: the hub's own tree, no longer
                            // the card's primary tap (2026-09-28).
                            HubCardAction.OPEN -> selectProject(project)
                            // Phase 51.4 — PROJECT_OPENED: the hub answered
                            // the tap with the thing the user asked for — since
                            // 2026-09-28 that is the editor on the project's
                            // entry file (Phase 46.2's rule, see openInEditor).
                            HubCardAction.OPEN_IN_EDITOR -> {
                                haptics.perform(HapticMoment.PROJECT_OPENED)
                                openInEditor(project)
                            }
                            HubCardAction.RENAME -> renameProjectTarget = project
                            HubCardAction.EXPORT -> {
                                exportProjectName = project.name
                                exportLauncher.launch("${project.name}.zip")
                            }
                            HubCardAction.SHARE_ZIP -> {
                                scope.launch {
                                    val file = runCatching {
                                        ProjectTransfer.exportZipToCache(
                                            project.root, context.cacheDir, "${project.name}.zip"
                                        )
                                    }.getOrNull()
                                    if (file != null) {
                                        val uri = FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", file
                                        )
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/zip"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching {
                                            context.startActivity(
                                                Intent.createChooser(
                                                    send, context.getString(R.string.share_as_zip)
                                                ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            )
                                        }.onFailure {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.share_failed)
                                            )
                                        }
                                    } else {
                                        snackbarHostState.showSnackbar(context.getString(R.string.share_failed))
                                    }
                                }
                            }
                            HubCardAction.DELETE -> deleteProjectTarget = project
                            HubCardAction.SOURCE_CONTROL -> gitSheetProject = project
                            HubCardAction.PULL -> viewModel.pullProject(context, project.name)
                            HubCardAction.COPY_REMOTE_URL -> viewModel.remoteUrlFor(context, project.name) { url ->
                                scope.launch {
                                    if (url != null) {
                                        clipboard.setText(AnnotatedString(url))
                                        snackbarHostState.showSnackbar(context.getString(R.string.hub_remote_url_copied))
                                    } else {
                                        snackbarHostState.showSnackbar(context.getString(R.string.hub_no_remote_url))
                                    }
                                }
                            }
                            HubCardAction.SWITCH_BRANCH -> branchSheetProject = project
                            HubCardAction.PUSH -> viewModel.pushProject(context, project.name)
                        }
                    },
                    onCreate = { showHubSheet = true },
                    onStarter = { startFromStarter(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                }
            } else if (treeLoading && tree.isEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = CodecTokens.space(Space.S)),
                ) {
                    item {
                        SkeletonFileTreeRow(
                            modifier = Modifier.padding(horizontal = CodecTokens.space(Space.L))
                        )
                    }
                    items(7) { SkeletonFileTreeRow() }
                }
            } else {
                ProjectTree(
                    project = activeProject!!,
                    nodes = tree,
                    viewModel = viewModel,
                    onLongPressHaptic = { haptics.perform(HapticMoment.DRAG_STARTED) },
                    onDirectoryClick = { viewModel.toggleDirectory(it) },
                    onFileClick = { path ->
                        // Phase 46.2 — one file is one file: the tap routes the
                        // SINGLE_FILE editor (peek), not the whole project.
                        onProjectSelected(activeProject!!)
                        onProjectFilePeek(activeProject!!.name, path)
                    },
                    onCreateIn = { parent, folder ->
                        newItemParent = parent
                        newItemFolder = folder
                        showCreateItem = true
                    },
                    onRename = { renameTarget = it },
                    onDelete = { deleteTarget = it },
                    onPreview = { path -> onProjectPreviewFile(activeProject!!.name, path) },
                    onSetDefaultRun = { path -> viewModel.setDefaultWebRun(context, path) },
                    onRunFile = { path -> onRunProjectFile(activeProject!!.name, path) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (showCreateProject) {
        var name by remember { mutableStateOf("") }
        // Phase 14 — the wizard: pick the template, then the project is
        // scaffolded with its starter files (server types get a RUN ▶ that
        // opens the live Web Preview). Default is "auto": no type choice —
        // RUN ▶ detects the type from the project's files.
        var selectedType by remember { mutableStateOf("auto") }
        // Phase 66.1 — the field says "taken" / "invalid" before the tap
        // (ProjectNameCheck: the same rules ProjectManager enforces), Create
        // is enabled only for a name that can be created, and a failure that
        // still happens (filesystem) is shown INSIDE this dialog — the snackbar
        // behind an open dialog was the owner's 40.1 clone bug, repeated here.
        val existingNames = remember(projects) { projects.map { it.name } }
        val verdict = remember(name, existingNames) { ProjectNameCheck.check(name, existingNames) }
        var createError by remember { mutableStateOf<String?>(null) }
        val nameFocus = remember { FocusRequester() }
        fun submitCreate() {
            if (!verdict.ok || isBusy) return
            createError = null
            viewModel.createProject(
                context, name, selectedType,
                onCreated = { project ->
                    showCreateProject = false
                    // 2026-09-28, owner: Create lands in the editor too (it
                    // superseded Phase 66.1's "Keep today: hub file tree").
                    openInEditor(project)
                },
                onFailed = { createError = it }
            )
        }
        AlertDialog(
            onDismissRequest = { showCreateProject = false },
            title = { Text(stringResource(R.string.new_project)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    createError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = CodecTokens.space(Space.S))
                        )
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            createError = null
                        },
                        label = { Text(stringResource(R.string.project_name)) },
                        singleLine = true,
                        isError = verdict.showsError,
                        supportingText = projectNameSupportingText(verdict),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitCreate() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocus)
                    )
                    // Inside the dialog's own composition: the field exists here.
                    LaunchedEffect(Unit) { nameFocus.requestFocus() }
                    Spacer(Modifier.height(CodecTokens.space(Space.M)))
                    Text(
                        text = stringResource(R.string.project_type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(CodecTokens.space(Space.XS)))
                    // Phase 66.1 — the rows are real radio buttons to TalkBack
                    // (selectable + Role.RadioButton in one group); the ●/○
                    // glyphs are unchanged, so the dialog looks as it did.
                    Column(Modifier.selectableGroup()) {
                        ProjectTypes.options.forEach { option ->
                            val selected = option.id == selectedType
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { selectedType = option.id }
                                    )
                                    .padding(vertical = CodecTokens.space(Space.S), horizontal = CodecTokens.space(Space.XS)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selected) "●" else "○",
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(end = CodecTokens.space(Space.S))
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { submitCreate() },
                    enabled = verdict.ok && !isBusy
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateProject = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showCreateItem) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateItem = false },
            title = { Text(if (newItemFolder) stringResource(R.string.new_folder) else stringResource(R.string.new_file)) },
            text = {
                Column {
                    if (newItemParent.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.new_item_in, newItemParent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = CodecTokens.space(Space.S))
                        )
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        if (newItemFolder) {
                            viewModel.createFolder(context, newItemParent, name)
                            showCreateItem = false
                        } else {
                            val result = viewModel.createFile(context, newItemParent, name)
                            if (result.isSuccess) {
                                showCreateItem = false
                                result.getOrNull()?.let { path ->
                                    activeProject?.let { onProjectFileSelected(it.name, path) }
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: context.getString(R.string.create_failed))
                                }
                            }
                        }
                    }
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateItem = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    renameTarget?.let { node ->
        var name by remember(node.relativePath) { mutableStateOf(node.file.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.renameNode(context, node.relativePath, name)
                    renameTarget = null
                }) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    deleteTarget?.let { node ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_item_confirm, node.relativePath)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNode(context, node.relativePath)
                        deleteTarget = null
                    },
                    colors = destructiveTextButtonColors()
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    deleteProjectTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteProjectTarget = null },
            title = { Text(stringResource(R.string.delete_project)) },
            // Phase 66.1 — the sentence says what the file dialog already said:
            // it cannot be undone (and, since 66.1, a deleted demo_flask really
            // stays deleted — DemoProjects no longer re-seeds it).
            text = { Text(stringResource(R.string.delete_project_confirm, project.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(context, project.name)
                        deleteProjectTarget = null
                    },
                    colors = destructiveTextButtonColors()
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteProjectTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showZipNameDialog) {
        // Phase 66.1 — same truthfulness as New Project: an unusable name is
        // said in the field; a taken name is not an error here (the import
        // appends _2, _3 — ProjectNameCheck.importedNameFor says which), and an
        // import failure is shown in this dialog, not behind it.
        val existingNames = remember(projects) { projects.map { it.name } }
        val verdict = remember(zipProjectName, existingNames) {
            ProjectNameCheck.check(zipProjectName, existingNames, allowTaken = true)
        }
        val importedAs = remember(zipProjectName, existingNames) {
            ProjectNameCheck.importedNameFor(zipProjectName, existingNames)
        }
        var importError by remember { mutableStateOf<String?>(null) }
        val nameFocus = remember { FocusRequester() }
        fun dismissZip() {
            showZipNameDialog = false
            zipImportUri = null
        }
        fun submitZip() {
            val uri = zipImportUri
            if (uri == null || !verdict.ok || isBusy) return
            importError = null
            viewModel.importZip(
                context, uri, zipProjectName,
                onImported = { imported ->
                    dismissZip()
                    onProjectSelected(imported)
                },
                onFailed = { importError = it }
            )
        }
        AlertDialog(
            onDismissRequest = { if (!isBusy) dismissZip() },
            title = { Text(stringResource(R.string.import_zip)) },
            text = {
                Column {
                    importError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = CodecTokens.space(Space.S))
                        )
                    }
                    OutlinedTextField(
                        value = zipProjectName,
                        onValueChange = {
                            zipProjectName = it
                            importError = null
                        },
                        label = { Text(stringResource(R.string.project_name)) },
                        singleLine = true,
                        isError = verdict.showsError,
                        supportingText = when {
                            verdict.showsError -> projectNameSupportingText(verdict)
                            importedAs != null && importedAs != verdict.safeName -> {
                                { Text(stringResource(R.string.project_name_will_import_as, importedAs)) }
                            }
                            else -> null
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitZip() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocus)
                    )
                    LaunchedEffect(Unit) { nameFocus.requestFocus() }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { submitZip() },
                    enabled = verdict.ok && zipImportUri != null && !isBusy
                ) { Text(stringResource(R.string.import_action)) }
            },
            dismissButton = {
                TextButton(onClick = { dismissZip() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showCloneDialog) {
        // Phase 15 (mockup-exact) — the Spck clone dialog: "Repository URL"
        // with a trailing QR-scan icon, auto-filled "Project name", a
        // chevron-collapsed "Advanced" section holding a Branch dropdown
        // (ls-remote fed, free-text fallback) and the shallow toggle, the
        // token hint with a Settings link, and CANCEL / CLONE.
        var cloneUrl by remember { mutableStateOf("") }
        var cloneName by remember { mutableStateOf("") }
        var nameEdited by remember { mutableStateOf(false) }
        var cloneAdvancedOpen by remember { mutableStateOf(true) }
        var cloneBranch by remember { mutableStateOf("") }
        var cloneShallow by remember { mutableStateOf(true) }
        var cloneBranches by remember { mutableStateOf<List<String>>(emptyList()) }
        var cloneBranchesLoaded by remember { mutableStateOf(false) }
        var cloneBranchNote by remember { mutableStateOf<String?>(null) }
        var fetchingBranches by remember { mutableStateOf(false) }
        var branchMenuOpen by remember { mutableStateOf(false) }

        fun fetchBranches(url: String) {
            fetchingBranches = true
            cloneBranchNote = null
            viewModel.fetchRemoteBranches(context, url) { result ->
                fetchingBranches = false
                val branches = result.getOrNull()
                cloneBranches = branches.orEmpty()
                cloneBranchesLoaded = true
                cloneBranchNote = if (branches != null && branches.isEmpty()) {
                    context.getString(R.string.clone_no_branches_found)
                } else if (result.isFailure) {
                    result.exceptionOrNull()?.message
                } else {
                    null
                }
            }
        }

        AlertDialog(
            // Phase 40.1 — a clone in flight cannot be dismissed by a stray
            // tap; and the failure is rendered INSIDE this dialog below, never
            // in a snackbar alone (the owner's "error in the background" bug).
            onDismissRequest = {
                if (!isBusy) {
                    showCloneDialog = false
                    viewModel.clearCloneError()
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = !isBusy,
                dismissOnClickOutside = !isBusy
            ),
            title = {
                Text(
                    stringResource(R.string.clone_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // Phase 40.1 — the inline error slot, in the dialog's own
                    // text column, so a failure is impossible to miss.
                    cloneError?.let { error ->
                        Text(
                            text = stringResource(R.string.clone_failed, error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = CodecTokens.space(Space.S))
                        )
                    }
                    Text(
                        stringResource(R.string.clone_url_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = CodecTokens.space(Space.S))
                    )
                    OutlinedTextField(
                        value = cloneUrl,
                        onValueChange = {
                            cloneUrl = it
                            if (!nameEdited) cloneName = GitManager.repoNameFromUrl(it) ?: ""
                        },
                        placeholder = { Text("https://github.com/user/repo.git") },
                        trailingIcon = {
                            Icon(
                                SpckIcons.QrScan,
                                contentDescription = stringResource(R.string.clone_qr),
                                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.NAV))
                            )
                        },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(CodecTokens.space(Space.M)))
                    Text(
                        stringResource(R.string.clone_name_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = CodecTokens.space(Space.S))
                    )
                    OutlinedTextField(
                        value = cloneName,
                        onValueChange = {
                            cloneName = it
                            nameEdited = true
                        },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(CodecTokens.space(Space.XS)))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cloneAdvancedOpen = !cloneAdvancedOpen }
                            .padding(vertical = CodecTokens.space(Space.S)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (cloneAdvancedOpen) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION))
                        )
                        Spacer(Modifier.width(CodecTokens.space(Space.S)))
                        Text(
                            stringResource(R.string.clone_advanced),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    if (cloneAdvancedOpen) {
                        Text(
                            stringResource(R.string.clone_branch_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = CodecTokens.space(Space.S))
                        )
                        Box {
                            OutlinedTextField(
                                value = cloneBranch,
                                onValueChange = { cloneBranch = it },
                                placeholder = { Text("main") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.ExpandMore,
                                        contentDescription = stringResource(R.string.clone_branch_label),
                                        modifier = Modifier
                                            .padding(end = CodecTokens.space(Space.XS))
                                            .clickable {
                                                val url = cloneUrl.trim()
                                                if (!GitManager.isCloneableUrl(url)) {
                                                    cloneBranchNote = context.getString(R.string.clone_invalid_url)
                                                } else {
                                                    branchMenuOpen = true
                                                    if (!cloneBranchesLoaded && !fetchingBranches) {
                                                        fetchBranches(url)
                                                    }
                                                }
                                            }
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = branchMenuOpen,
                                onDismissRequest = { branchMenuOpen = false }
                            ) {
                                when {
                                    fetchingBranches -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = CodecTokens.space(Space.L), vertical = CodecTokens.space(Space.M))
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(CodecTokens.space(Space.L)),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(CodecTokens.space(Space.M)))
                                            Text(stringResource(R.string.clone_fetching_branches))
                                        }
                                    }
                                    cloneBranches.isEmpty() -> {
                                        Text(
                                            stringResource(R.string.clone_no_branches_found),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = CodecTokens.space(Space.L), vertical = CodecTokens.space(Space.M))
                                        )
                                    }
                                    else -> {
                                        cloneBranches.forEach { branch ->
                                            DropdownMenuItem(
                                                text = { Text(branch, maxLines = 1) },
                                                onClick = {
                                                    cloneBranch = branch
                                                    branchMenuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        cloneBranchNote?.let { note ->
                            Text(
                                note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = CodecTokens.space(Space.XS))
                            )
                        }
                        Spacer(modifier = Modifier.height(CodecTokens.space(Space.S)))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { cloneShallow = !cloneShallow }
                                .padding(vertical = CodecTokens.space(Space.XS)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.clone_shallow),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(checked = cloneShallow, onCheckedChange = { cloneShallow = it })
                        }
                        Spacer(modifier = Modifier.height(CodecTokens.space(Space.S)))
                    }
                    HorizontalDivider(Modifier.padding(vertical = CodecTokens.space(Space.M)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION)),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(CodecTokens.space(Space.S)))
                        Text(
                            stringResource(R.string.clone_token_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                showCloneDialog = false
                                onOpenSettings()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val branchArg = cloneBranch.trim().takeIf { it.isNotEmpty() }
                        viewModel.cloneFromGitHub(
                            context = context,
                            url = cloneUrl.trim(),
                            requestedName = cloneName,
                            branch = branchArg,
                            shallow = cloneShallow
                        ) { cloned ->
                            showCloneDialog = false
                            onProjectSelected(cloned)
                        }
                    },
                    enabled = cloneUrl.isNotBlank() && !isBusy
                ) { Text(stringResource(R.string.clone_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showCloneDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    gitSheetProject?.let { project ->
        GitControlSheet(
            projectRoot = project.root,
            onDismiss = { gitSheetProject = null },
            // Phase 73.3 — the overflow menu's "Git Credentials" item reuses
            // this screen's existing Settings jump.
            onOpenSettings = onOpenSettings
        )
    }

    // Phase 17 — Switch Branch from the Projects card ⋮.
    branchSheetProject?.let { project ->
        BranchSwitchSheet(
            projectRoot = project.root,
            onDismiss = {
                branchSheetProject = null
                viewModel.loadProjects(context)
            }
        )
    }

    if (showHubSheet) {
        ProjectsHubAddSheet(
            onDismiss = { showHubSheet = false },
            onNewProject = {
                showHubSheet = false
                showCreateProject = true
            },
            onCloneGit = {
                showHubSheet = false
                viewModel.clearCloneError()
                showCloneDialog = true
            },
            onImportZip = {
                showHubSheet = false
                zipImportLauncher.launch(arrayOf("*/*"))
            }
        )
    }

    renameProjectTarget?.let { project ->
        var newName by remember(project.name) { mutableStateOf(project.name) }
        // Phase 66.1 — "taken" / "invalid" in the field before the tap, the
        // project's own name is not a collision, and a rename that still fails
        // is shown in this dialog rather than in a snackbar behind it.
        val existingNames = remember(projects) { projects.map { it.name } }
        val verdict = remember(newName, existingNames, project.name) {
            ProjectNameCheck.check(newName, existingNames, current = project.name)
        }
        var renameError by remember(project.name) { mutableStateOf<String?>(null) }
        val nameFocus = remember { FocusRequester() }
        fun submitRename() {
            if (!verdict.ok || isBusy) return
            if (verdict.safeName == project.name) {
                renameProjectTarget = null
                return
            }
            renameError = null
            viewModel.renameProject(
                context, project.name, newName.trim(),
                onRenamed = { renameProjectTarget = null },
                onFailed = { renameError = it }
            )
        }
        AlertDialog(
            onDismissRequest = { renameProjectTarget = null },
            title = { Text(stringResource(R.string.rename_project)) },
            text = {
                Column {
                    renameError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = CodecTokens.space(Space.S))
                        )
                    }
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                            renameError = null
                        },
                        label = { Text(stringResource(R.string.project_name)) },
                        singleLine = true,
                        isError = verdict.showsError,
                        supportingText = when {
                            verdict.showsError -> projectNameSupportingText(verdict)
                            verdict.ok && verdict.safeName == project.name -> {
                                { Text(stringResource(R.string.project_name_unchanged)) }
                            }
                            else -> null
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitRename() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocus)
                    )
                    LaunchedEffect(project.name) { nameFocus.requestFocus() }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { submitRename() },
                    enabled = verdict.ok && !isBusy
                ) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = {
                TextButton(onClick = { renameProjectTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/**
 * Phase 66.1 — the line under a project-name field, from the pure verdict:
 * *invalid* and *taken* are said in the field, in the field's own error colour
 * (M3 `supportingText` + `isError`); an empty or usable name shows nothing, so
 * the dialog's shape does not change while the user types a good name.
 */
private fun projectNameSupportingText(verdict: ProjectNameVerdict): (@Composable () -> Unit)? =
    when (verdict.problem) {
        ProjectNameProblem.INVALID -> {
            { Text(stringResource(R.string.project_name_invalid_hint)) }
        }
        ProjectNameProblem.TAKEN -> {
            { Text(stringResource(R.string.project_name_taken)) }
        }
        ProjectNameProblem.EMPTY, null -> null
    }

/**
 * Phase 66.1 — the word for a project's kind in the tree header: the hub
 * entry's kind (what the card's subtitle already says, detector included for
 * `auto` projects), falling back to the wizard's label for the declared type
 * before the entries have loaded, and to the generic kind word ("Project")
 * for anything else. Never the raw config id.
 */
private fun hubKindLabel(project: ProjectInfo, entries: List<ProjectHubEntry>): String =
    entries.firstOrNull { it.name == project.name }?.let { ProjectsHub.kindLabel(it.kind) }
        ?: ProjectTypes.optionFor(project.config.type)?.label
        ?: ProjectsHub.kindLabel(ProjectsHub.kindOfConfigType(project.config.type))

/**
 * Phase 66.1 — a destructive confirm reads as one: the text button's content
 * in the theme's error role (the Packages screen's UNINSTALL already does
 * this). Same button, same place, one colour.
 */
@Composable
private fun destructiveTextButtonColors() =
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)

/** Phase 52.1 — the hub's explicit return door. It is not a new route: the
 * activity keeps the offer session-only and the two actions hand back to the
 * existing editor or hub navigation. */
@Composable
private fun ResumeOfferCard(
    facts: com.codeci.ide.ui.projects.ResumeFacts,
    onContinue: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val path = com.codeci.ide.ui.projects.ResumePolicy.displayPath(
        facts.lastProject,
        facts.lastFile,
    ) ?: return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CodecTokens.radius(Radius.L)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(CodecTokens.space(Space.L))) {
            Text(
                text = stringResource(R.string.resume_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(CodecTokens.space(Space.XS)))
            Text(
                text = path,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = com.codeci.ide.ui.theme.CodecType.codeFamily,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.resume_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDecline) {
                    Text(stringResource(R.string.resume_decline))
                }
                Button(onClick = onContinue) {
                    Text(stringResource(R.string.resume_continue))
                }
            }
        }
    }
}

/**
 * Per-project overflow actions (spec §2.4). Phase 46.2 adds OPEN_IN_EDITOR —
 * the explicit "open the WHOLE project" action, at the top of the menu
 * (PART_46_2 §4) — and gives OPEN's old job a clearer neighbour: a file tap
 * now peeks, so the full project editor needs its own row.
 */
private enum class HubCardAction {
    OPEN, OPEN_IN_EDITOR, RENAME, EXPORT, SHARE_ZIP, DELETE, SOURCE_CONTROL, PULL, PUSH, COPY_REMOTE_URL, SWITCH_BRANCH
}

@Composable
private fun ProjectsHubList(
    entries: List<ProjectHubEntry>,
    /**
     * Phase 51.3 — the read's own facts (a read in flight / one has finished).
     * The branch is decided by the pure HubListPolicy, not by `isBusy`, which
     * is also true for clone/delete/export and would draw a skeleton over
     * perfectly loaded projects.
     */
    facts: HubListFacts = HubListFacts(entryCount = entries.size, loadedOnce = true),
    /** The last non-zero count the hub showed: skeleton stability. */
    skeletonRows: Int = 0,
    filter: ProjectHubFilter,
    searchQuery: String,
    onFilterSelected: (ProjectHubFilter) -> Unit,
    onCardAction: (ProjectHubEntry, HubCardAction) -> Unit,
    onCreate: () -> Unit,
    onStarter: (WelcomeStarter) -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = rememberMotionSpecs()
    // Phase 50.4 — transition (6): the hub's states crossfade on the shared
    // spec — creating or deleting the last project reads as one change.
    // Phase 51.3 — and "still reading" is now one of those states, so a cold
    // open shows the list's own shape instead of the empty state (which was
    // indistinguishable from "your projects are gone").
    val branch = HubListPolicy.branchFor(facts)
    Crossfade(
        targetState = branch,
        animationSpec = motion.floatOrSnap(CodecMotion.crossfadeSpec)
    ) { state ->
        when (state) {
            HubListBranch.LOADING -> Column(modifier = modifier) {
                repeat(HubListPolicy.rowCount(facts, skeletonRows)) {
                    SkeletonHubCard(
                        modifier = Modifier.padding(
                            horizontal = CodecTokens.space(Space.L),
                            vertical = CodecTokens.space(Space.XS),
                        )
                    )
                }
            }
            HubListBranch.EMPTY -> EmptyProjectsState(onCreate, onStarter)
            HubListBranch.LIST -> ProjectsHubListContent(
                entries, filter, searchQuery, onFilterSelected, onCardAction,
                onCreate = onCreate, modifier = modifier
            )
        }
    }
}

@Composable
private fun ProjectsHubListContent(
    entries: List<ProjectHubEntry>,
    filter: ProjectHubFilter,
    searchQuery: String,
    onFilterSelected: (ProjectHubFilter) -> Unit,
    onCardAction: (ProjectHubEntry, HubCardAction) -> Unit,
    /** Phase 59.1 — the row's *Create* chip opens the hub's own add sheet. */
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = ProjectsHub.filterEntries(entries, filter, searchQuery)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = CodecTokens.space(Space.L), vertical = CodecTokens.space(Space.S)),
            horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
        ) {
            // Phase 59.1 — the row is the spec's own (`All · Recent · Create`), by the owner's
            // answer of 2026-09-22. *Create* is an action, not a filter: it opens the sheet the
            // hub's ＋ already opens. The language chips left the row in the same answer — the
            // policy still filters by them (`ProjectHubFilter.GIT/C/PYTHON/WEB` and their
            // `filterEntries` tests are untouched), and the card's subtitle still names each
            // project's kind (`ProjectsHub.kindLabel`).
            HubFilterChip(ProjectHubFilter.ALL, filter, stringResource(R.string.hub_filter_all), null, onFilterSelected)
            HubFilterChip(ProjectHubFilter.RECENT, filter, stringResource(R.string.hub_filter_recent), null, onFilterSelected)
            HubActionChip(stringResource(R.string.hub_filter_create), Icons.Default.Add, onCreate)
        }
        if (visible.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = CodecTokens.space(Space.HUGE), horizontal = CodecTokens.space(Space.XXL)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(CodecTokens.space(Space.HUGE)),
                    // Phase 40.5 — an accent at 60% measured 2.45:1 (needs 3:1
                    // for a graphic); the opaque accent is 4.47:1.
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(CodecTokens.space(Space.M)))
                Text(
                    // Phase 66.1 — a search miss is named as one; "this filter"
                    // is kept for a chip that really matched nothing.
                    stringResource(
                        if (searchQuery.isNotBlank()) R.string.hub_no_search_match else R.string.hub_no_match
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = CodecTokens.space(Space.L), end = CodecTokens.space(Space.L), top = CodecTokens.space(Space.XS), bottom = CodecTokens.space(Space.HUGE) * 2f),
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
        ) {
            items(visible, key = { it.name }) { entry ->
                ProjectHubCard(entry = entry, onAction = onCardAction)
            }
        }
    }
}

/**
 * Mockup-exact filter chip: a flat pill — filled purple with white text when
 * selected, dark surface with a hairline outline otherwise; the Git chip
 * carries the branch glyph.
 */
@Composable
private fun HubFilterChip(
    value: ProjectHubFilter,
    selected: ProjectHubFilter,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onSelect: (ProjectHubFilter) -> Unit
) {
    val isSelected = value == selected
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .then(
                if (isSelected) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 1.dp,
                        // Phase 40.5 — 0.25 alpha measured 2.38:1; `outline` is
                        // Material's own boundary role and clears 3:1.
                        color = MaterialTheme.colorScheme.outline,
                        shape = shape
                    )
                }
            )
            .clickable { onSelect(value) }
            .padding(horizontal = CodecTokens.space(Space.L), vertical = CodecTokens.space(Space.S)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.INLINE)),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(Modifier.width(CodecTokens.space(Space.S)))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/**
 * Phase 59.1 — the row's action chip (*Create*): the same shell as [HubFilterChip], because it
 * shares their shape, their spacing and their tap area — but it is never “selected”, because it is
 * not a state the list can be in. It opens the same sheet the hub's ＋ opens, so there is one
 * add-project path in the app and not two.
 */
@Composable
private fun HubActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                // Phase 40.5 — the same boundary role the unselected filter chips use.
                color = MaterialTheme.colorScheme.outline,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = CodecTokens.space(Space.L), vertical = CodecTokens.space(Space.S)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.INLINE)),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(CodecTokens.space(Space.XS)))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Phase 15 — Spck-style project card: colored leading type square, name,
 * `⌥ branch · N files · age` line, an `M` pill for uncommitted work, and a
 * git-aware overflow menu. All card data arrives precomputed in [entry]
 * (ViewModel IO); this composable never touches disk.
 */
@Composable
private fun ProjectHubCard(
    entry: ProjectHubEntry,
    onAction: (ProjectHubEntry, HubCardAction) -> Unit
) {
    var menuOpen by remember(entry.name) { mutableStateOf(false) }
    Card(
        // The card's tap opens the project in the editor (2026-09-28); the
        // tree is the ⋮ menu's Browse files.
        onClick = { onAction(entry, HubCardAction.OPEN_IN_EDITOR) },
        shape = RoundedCornerShape(CodecTokens.radius(Radius.L)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = CodecTokens.elevation(CodecTokens.Elevation.FLAT))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CodecTokens.space(Space.L), vertical = CodecTokens.space(Space.M)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProjectIconView(entry)
            Spacer(Modifier.width(CodecTokens.space(Space.L)))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.isGit) {
                        Icon(
                            SpckIcons.GitBranch,
                            contentDescription = null,
                            modifier = Modifier.size(CodecTokens.space(Space.M)),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(CodecTokens.space(Space.XS)))
                    }
                    Text(
                        // Mockup-exact separator: single " · " between segments.
                        ProjectsHub.subtitleSegments(entry, System.currentTimeMillis()).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (entry.hasChanges == true) {
                // Mockup-exact `M` pill: small yellow-outlined square before ⋮.
                Box(
                    modifier = Modifier
                        .padding(end = CodecTokens.space(Space.M))
                        .clip(RoundedCornerShape(CodecTokens.radius(Radius.XS)))
                        .border(width = 1.2.dp, color = HubBadgeYellow, shape = RoundedCornerShape(CodecTokens.radius(Radius.XS)))
                        .padding(horizontal = CodecTokens.space(Space.S), vertical = CodecTokens.space(Space.XXS))
                ) {
                    Text(
                        "M",
                        color = HubBadgeYellow,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (entry.unpushed > 0) {
                // Phase 17 device fix — amber "not pushed" pill: a failed push
                // must never look like an uploaded project.
                Box(
                    modifier = Modifier
                        .padding(end = CodecTokens.space(Space.S))
                        .clip(RoundedCornerShape(CodecTokens.radius(Radius.XS)))
                        .border(width = 1.2.dp, color = HubBadgeYellow, shape = RoundedCornerShape(CodecTokens.radius(Radius.XS)))
                        .padding(horizontal = CodecTokens.space(Space.S), vertical = CodecTokens.space(Space.XXS))
                ) {
                    Text(
                        "↑${entry.unpushed}",
                        color = HubBadgeYellow,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else if (entry.unpublished) {
                // Phase 17 follow-up — the branch itself is not on the remote
                // yet (a fresh branch has no "ahead" count), so show a bare ↑
                // so a local-only branch is never mistaken for an uploaded one.
                Box(
                    modifier = Modifier
                        .padding(end = CodecTokens.space(Space.S))
                        .clip(RoundedCornerShape(CodecTokens.radius(Radius.XS)))
                        .border(width = 1.2.dp, color = HubBadgeYellow, shape = RoundedCornerShape(CodecTokens.radius(Radius.XS)))
                        .padding(horizontal = CodecTokens.space(Space.S), vertical = CodecTokens.space(Space.XXS))
                ) {
                    Text(
                        "↑",
                        color = HubBadgeYellow,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // At the top, above SOURCE CONTROL: the hub's own tree.
                    // The card's tap is the editor now (2026-09-28), so the
                    // menu's first item is the other thing a project opens as.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hub_browse_files)) },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                        onClick = { menuOpen = false; onAction(entry, HubCardAction.OPEN) }
                    )
                    if (entry.isGit) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.source_control_title)) },
                            leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                            onClick = { menuOpen = false; onAction(entry, HubCardAction.SOURCE_CONTROL) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_pull)) },
                            leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                            onClick = { menuOpen = false; onAction(entry, HubCardAction.PULL) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hub_push)) },
                            leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                            onClick = { menuOpen = false; onAction(entry, HubCardAction.PUSH) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hub_switch_branch)) },
                            leadingIcon = { Icon(Icons.Default.CallSplit, contentDescription = null) },
                            onClick = { menuOpen = false; onAction(entry, HubCardAction.SWITCH_BRANCH) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hub_copy_remote_url)) },
                            leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                            onClick = { menuOpen = false; onAction(entry, HubCardAction.COPY_REMOTE_URL) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onAction(entry, HubCardAction.RENAME) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_zip)) },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                        onClick = { menuOpen = false; onAction(entry, HubCardAction.EXPORT) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_as_zip)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { menuOpen = false; onAction(entry, HubCardAction.SHARE_ZIP) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onAction(entry, HubCardAction.DELETE) }
                    )
                }
            }
        }
    }
}


/**
 * Phase 15 — the unified `+` sheet: exactly one place to New Project /
 * Clone Git Repository / Import ZIP (Spck's add-menu, rebuilt clean-room on
 * CodeC's own flows). Phase 46.1 — the fourth row ("Open Folder") is deleted
 * with the feature; the sheet has three rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectsHubAddSheet(
    onDismiss: () -> Unit,
    onNewProject: () -> Unit,
    onCloneGit: () -> Unit,
    onImportZip: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        // Mockup-exact: "New Project" headline + four rows with large colored
        // circular icons (lavender / indigo / blue / green).
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = CodecTokens.space(Space.XL), end = CodecTokens.space(Space.XL), bottom = CodecTokens.space(Space.XXL), top = CodecTokens.space(Space.XS))
        ) {
            Text(
                stringResource(R.string.hub_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = CodecTokens.space(Space.L))
            )
            HubSheetRow(
                color = Color(CodecPalette.HUB_ROW_VIOLET),
                iconTint = Color(CodecPalette.ON_HUB_ROW_VIOLET),
                icon = SpckIcons.FilePlus,
                title = stringResource(R.string.hub_sheet_new),
                subtitle = stringResource(R.string.hub_sheet_new_subtitle),
                onClick = onNewProject
            )
            HubSheetRow(
                color = Color(CodecPalette.HUB_ROW_INDIGO),
                iconTint = Color.White,
                icon = SpckIcons.CloneRepo,
                title = stringResource(R.string.hub_sheet_clone),
                subtitle = stringResource(R.string.hub_sheet_clone_subtitle),
                onClick = onCloneGit
            )
            HubSheetRow(
                color = Color(CodecPalette.HUB_ROW_BLUE),
                iconTint = Color.White,
                icon = SpckIcons.ZipFile,
                title = stringResource(R.string.import_zip),
                subtitle = stringResource(R.string.hub_sheet_zip_subtitle),
                onClick = onImportZip
            )
            // Phase 46.1 — no fourth row. "Open Folder" (the Phase 43 SAF tree
            // import) is removed completely; see the FolderImportRemovedTest pin.
        }
    }
}

@Composable
private fun HubSheetRow(
    color: Color,
    iconTint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    // Phase 51.4 — the sheet's rows were the bare case: `clip` + `clickable`
    // with no container and no press state of their own. Containment is the
    // shared component now (token radius, the press state), and the container
    // stays transparent so the row looks exactly as it did.
    PressableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CodecTokens.radius(Radius.L)),
        containerColor = Color.Transparent,
        contentPadding = PaddingValues(vertical = CodecTokens.space(Space.M)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(CodecTokens.space(Space.XXL)))
            }
            Spacer(Modifier.width(CodecTokens.space(Space.L)))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Phase 66.1 — the palette's own amber (`CodecPalette.WARNING`, the same
// `#E6B33C` the constant used to spell out), so the badge cannot drift from it.
private val HubBadgeYellow = Color(CodecPalette.WARNING)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectTree(
    project: ProjectInfo,
    nodes: List<FileNode>,
    viewModel: FileManagerViewModel,
    /** Phase 51.4 — the hub's DRAG_STARTED moment, hoisted from the screen. */
    onLongPressHaptic: () -> Unit,
    onDirectoryClick: (String) -> Unit,
    onFileClick: (String) -> Unit,
    onCreateIn: (String, Boolean) -> Unit,
    onRename: (FileNode) -> Unit,
    onDelete: (FileNode) -> Unit,
    onPreview: (String) -> Unit,
    onSetDefaultRun: (String) -> Unit,
    onRunFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = CodecTokens.space(Space.S)),
        verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.XXS))
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = CodecTokens.space(Space.L), vertical = CodecTokens.space(Space.S))) {
                Text(
                    // Phase 66.1 — the root line is the project's name; the old
                    // `name  >  ` printed a separator with nothing after it.
                    text = project.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = project.config.entry.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.project_entry_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
        }
        if (nodes.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(CodecTokens.space(Space.XXL)), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(CodecTokens.space(Space.S)))
                    Text(stringResource(R.string.empty_project), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.empty_project_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(CodecTokens.space(Space.L)))
                    Button(onClick = { onCreateIn("", false) }) { Text(stringResource(R.string.new_file)) }
                }
            }
        } else {
            items(nodes, key = { it.relativePath }) { node ->
                TreeRow(
                    node = node,
                    onLongPressHaptic = onLongPressHaptic,
                    onClick = {
                        if (node is FileNode.DirectoryNode) onDirectoryClick(node.relativePath)
                        else onFileClick(node.relativePath)
                    },
                    onCreateIn = onCreateIn,
                    onRename = onRename,
                    onDelete = onDelete,
                    onPreview = onPreview,
                    onSetDefaultRun = onSetDefaultRun,
                    onRun = onRunFile
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(
    node: FileNode,
    /** Phase 51.4 — fires on the long press (the hub's DRAG_STARTED moment). */
    onLongPressHaptic: (() -> Unit)? = null,
    onClick: () -> Unit,
    onCreateIn: (String, Boolean) -> Unit,
    onRename: (FileNode) -> Unit,
    onDelete: (FileNode) -> Unit,
    onPreview: (String) -> Unit,
    onSetDefaultRun: (String) -> Unit,
    onRun: (String) -> Unit
) {
    var menuOpen by remember(node.relativePath) { mutableStateOf(false) }
    val isDirectory = node is FileNode.DirectoryNode
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    // Phase 51.4 — DRAG_STARTED: the long press that opens a
                    // row's menu is the gesture this moment names, and it is
                    // the one place the finger is told the hold "took".
                    onLongPressHaptic?.invoke()
                    menuOpen = true
                }
            )
            .padding(start = (Space.L + node.depth * Space.XL).dp, end = CodecTokens.space(Space.S), top = CodecTokens.space(Space.S), bottom = CodecTokens.space(Space.S)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIconView(
            name = node.file.name,
            isDirectory = isDirectory,
            modifier = Modifier.size(CodecTokens.space(Space.XXL)),
            tint = if (isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(CodecTokens.space(Space.M)))
        Column(Modifier.weight(1f)) {
            Text(node.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!isDirectory) {
                val leaf = node as FileNode.FileLeaf
                Text(
                    text = formatBytes(leaf.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (isDirectory) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.new_file)) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = { menuOpen = false; onCreateIn(node.relativePath, false) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.new_folder)) },
                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    onClick = { menuOpen = false; onCreateIn(node.relativePath, true) }
                )
            } else if (node.file.name.endsWith(".c", ignoreCase = true)) {
                // Phase 9.1: run one file straight from the folder — the
                // command lands in the terminal tab with its full output.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.run_in_terminal)) },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = { menuOpen = false; onRun(node.relativePath) }
                )
            } else if (WebFileName.isPreviewable(node.file.name)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.preview)) },
                    onClick = { menuOpen = false; onPreview(node.relativePath) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.set_default_run)) },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = { menuOpen = false; onSetDefaultRun(node.relativePath) }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { menuOpen = false; onRename(node) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { menuOpen = false; onDelete(node) }
            )
        }
    }
}

private object WebFileName {
    fun isPreviewable(name: String): Boolean =
        name.endsWith(".html", true) || name.endsWith(".htm", true)
}


private fun formatBytes(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
    else -> "%.1f MB".format(size / (1024.0 * 1024.0))
}

@Composable
private fun EmptyProjectsState(
    onCreate: () -> Unit,
    onStarter: (WelcomeStarter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CodecTokens.space(Space.XL), vertical = CodecTokens.space(Space.XXL)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(CodecTokens.space(Space.XL)))
        // Phase 51.3 — the empty hub is a designed state, not a sentence on a
        // blank page: the app's own mark (Phase 38.1's `app_mark`, the same one
        // the welcome and the splash show) above the line that says what a
        // project is, then the three starters as the tiles they already were.
        // Display art, so the raw size is the 50.1 rule's own exception.
        Image(
            painter = painterResource(R.drawable.app_mark),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(CodecTokens.space(Space.L)))
        Text(stringResource(R.string.no_projects), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(CodecTokens.space(Space.S)))
        // Phase 33.3 — the empty hub points at the three starters (33.1), not
        // a blank list.
        Text(
            text = stringResource(R.string.no_projects_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(CodecTokens.space(Space.XL)))
        WelcomeStarters.starters.forEach { starter ->
            StarterTile(
                starter = starter,
                onClick = { onStarter(starter) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(CodecTokens.space(Space.M)))
        }
        Spacer(Modifier.height(CodecTokens.space(Space.M)))
        // The full create / clone / import sheet is still one tap away.
        TextButton(onClick = onCreate) { Text(stringResource(R.string.hub_create_first)) }
    }
}

/** Kept as a compatibility entry point for older callers/tests. */
@Composable
fun EmptyStateView(onCreateClick: () -> Unit) = EmptyProjectsState(onCreateClick, {})
