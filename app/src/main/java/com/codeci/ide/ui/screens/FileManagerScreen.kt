package com.codeci.ide.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
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
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.R
import com.codeci.ide.ui.components.SpckIcons
import com.codeci.ide.ui.projects.FileNode
import com.codeci.ide.ui.projects.GitManager
import com.codeci.ide.ui.projects.ProjectHubEntry
import com.codeci.ide.ui.projects.ProjectHubFilter
import com.codeci.ide.ui.projects.HubIconToken
import com.codeci.ide.ui.projects.ProjectInfo
import com.codeci.ide.ui.projects.ProjectTypes
import com.codeci.ide.ui.projects.ProjectsHub
import com.codeci.ide.ui.viewmodels.FileManagerViewModel
import kotlinx.coroutines.launch

/** Projects Hub (Phase 15) + private, hierarchical source tree (Phase 8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: FileManagerViewModel = viewModel(),
    onFileSelected: (String) -> Unit = {},
    onProjectFileSelected: (projectName: String, relativePath: String) -> Unit = { _, path -> onFileSelected(path) },
    onProjectSelected: (ProjectInfo) -> Unit = {},
    onPreviewFile: (String) -> Unit = {},
    onProjectPreviewFile: (projectName: String, relativePath: String) -> Unit = { _, path -> onPreviewFile(path) },
    onRunProjectFile: (projectName: String, relativePath: String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val hubEntries by viewModel.hubEntries.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val tree by viewModel.tree.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

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

    val folderImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.importFolder(context, uri) { imported ->
                onProjectSelected(imported)
            }
        }
    }
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
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
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
                                activeProject!!.config.type,
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
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (activeProject == null) {
                ProjectsHubList(
                    entries = hubEntries,
                    filter = hubFilter,
                    searchQuery = searchQuery,
                    onFilterSelected = { hubFilter = it },
                    onCardAction = { entry, action ->
                        val project = projects.firstOrNull { it.name == entry.name } ?: return@ProjectsHubList
                        when (action) {
                            HubCardAction.OPEN -> selectProject(project)
                            HubCardAction.RENAME -> renameProjectTarget = project
                            HubCardAction.EXPORT -> {
                                exportProjectName = project.name
                                exportLauncher.launch("${project.name}.zip")
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
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ProjectTree(
                    project = activeProject!!,
                    nodes = tree,
                    viewModel = viewModel,
                    onDirectoryClick = { viewModel.toggleDirectory(it) },
                    onFileClick = { path ->
                        onProjectSelected(activeProject!!)
                        onProjectFileSelected(activeProject!!.name, path)
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
        AlertDialog(
            onDismissRequest = { showCreateProject = false },
            title = { Text(stringResource(R.string.new_project)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.project_name)) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.project_type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    ProjectTypes.options.forEach { option ->
                        val selected = option.id == selectedType
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedType = option.id }
                                .padding(vertical = 7.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selected) "●" else "○",
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(end = 8.dp)
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
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.createProject(context, name, selectedType) { project ->
                            showCreateProject = false
                            onProjectSelected(project)
                        }
                    }
                }) { Text(stringResource(R.string.create)) }
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
                            text = "In ${newItemParent}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
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
            text = { Text("Delete ${node.relativePath}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNode(context, node.relativePath)
                    deleteTarget = null
                }) { Text(stringResource(R.string.delete)) }
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
            text = { Text("Delete project ${project.name} and all its files?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(context, project.name)
                    deleteProjectTarget = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteProjectTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showZipNameDialog) {
        AlertDialog(
            onDismissRequest = {
                showZipNameDialog = false
                zipImportUri = null
            },
            title = { Text(stringResource(R.string.import_zip)) },
            text = {
                OutlinedTextField(
                    value = zipProjectName,
                    onValueChange = { zipProjectName = it },
                    label = { Text(stringResource(R.string.project_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = zipImportUri
                    if (uri != null && zipProjectName.isNotBlank()) {
                        viewModel.importZip(context, uri, zipProjectName) { imported ->
                            showZipNameDialog = false
                            zipImportUri = null
                            onProjectSelected(imported)
                        }
                    }
                }) { Text(stringResource(R.string.import_action)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showZipNameDialog = false
                    zipImportUri = null
                }) { Text(stringResource(R.string.cancel)) }
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
            onDismissRequest = { showCloneDialog = false },
            title = {
                Text(
                    stringResource(R.string.clone_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.clone_url_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
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
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.clone_name_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = cloneName,
                        onValueChange = {
                            cloneName = it
                            nameEdited = true
                        },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cloneAdvancedOpen = !cloneAdvancedOpen }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (cloneAdvancedOpen) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
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
                            modifier = Modifier.padding(bottom = 6.dp)
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
                                            .padding(end = 4.dp)
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
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(stringResource(R.string.clone_fetching_branches))
                                        }
                                    }
                                    cloneBranches.isEmpty() -> {
                                        Text(
                                            stringResource(R.string.clone_no_branches_found),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
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
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { cloneShallow = !cloneShallow }
                                .padding(vertical = 4.dp),
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
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
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
            onDismiss = { gitSheetProject = null }
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
                showCloneDialog = true
            },
            onImportZip = {
                showHubSheet = false
                zipImportLauncher.launch(arrayOf("*/*"))
            },
            onOpenFolder = {
                showHubSheet = false
                folderImportLauncher.launch(null)
            }
        )
    }

    renameProjectTarget?.let { project ->
        var newName by remember(project.name) { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { renameProjectTarget = null },
            title = { Text(stringResource(R.string.rename_project)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.project_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.renameProject(context, project.name, newName.trim()) {
                            renameProjectTarget = null
                        }
                    }
                }) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = {
                TextButton(onClick = { renameProjectTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Per-project overflow actions (spec §2.4). */
private enum class HubCardAction {
    OPEN, RENAME, EXPORT, DELETE, SOURCE_CONTROL, PULL, PUSH, COPY_REMOTE_URL, SWITCH_BRANCH
}

@Composable
private fun ProjectsHubList(
    entries: List<ProjectHubEntry>,
    filter: ProjectHubFilter,
    searchQuery: String,
    onFilterSelected: (ProjectHubFilter) -> Unit,
    onCardAction: (ProjectHubEntry, HubCardAction) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) {
        EmptyProjectsState(onCreate)
        return
    }
    val visible = ProjectsHub.filterEntries(entries, filter, searchQuery)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HubFilterChip(ProjectHubFilter.ALL, filter, stringResource(R.string.hub_filter_all), null, onFilterSelected)
            HubFilterChip(ProjectHubFilter.GIT, filter, stringResource(R.string.hub_filter_git), SpckIcons.GitBranch, onFilterSelected)
            HubFilterChip(ProjectHubFilter.C, filter, stringResource(R.string.hub_filter_c), null, onFilterSelected)
            HubFilterChip(ProjectHubFilter.PYTHON, filter, stringResource(R.string.hub_filter_python), null, onFilterSelected)
            HubFilterChip(ProjectHubFilter.WEB, filter, stringResource(R.string.hub_filter_web), null, onFilterSelected)
        }
        if (visible.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.hub_no_match),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                        shape = shape
                    )
                }
            )
            .clickable { onSelect(value) }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(Modifier.width(6.dp))
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
        onClick = { onAction(entry, HubCardAction.OPEN) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HubTypeIcon(entry)
            Spacer(Modifier.width(14.dp))
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
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(5.dp))
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
                        .padding(end = 10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .border(width = 1.2.dp, color = HubBadgeYellow, shape = RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .border(width = 1.2.dp, color = HubBadgeYellow, shape = RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "↑${entry.unpushed}",
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
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hub_open_action)) },
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
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onAction(entry, HubCardAction.DELETE) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HubTypeIcon(entry: ProjectHubEntry) {
    // Mockup-exact: 56dp rounded square, 14dp radius, brand colors from the
    // design (orange C, blue Python, purple web framework, green static web).
    val background = when (entry.icon) {
        HubIconToken.C_ORANGE -> Color(0xFFF0863C)
        HubIconToken.PY_BLUE -> Color(0xFF3E7CC1)
        HubIconToken.SERVER_PURPLE -> Color(0xFF8B5CF6)
        HubIconToken.WEB_GREEN -> Color(0xFF4CAF50)
        HubIconToken.GENERIC_GRAY -> Color(0xFF6B7280)
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        when (entry.icon) {
            HubIconToken.WEB_GREEN ->
                Icon(SpckIcons.Globe, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            HubIconToken.PY_BLUE ->
                Icon(SpckIcons.PythonLogo, contentDescription = null, modifier = Modifier.size(34.dp))
            else ->
                Text(
                    entry.iconLabel.ifEmpty { entry.name.take(1).uppercase() },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
        }
    }
}

/**
 * Phase 15 — the unified `+` sheet: exactly one place to New Project /
 * Clone Git Repository / Import ZIP / Open Folder (Spck's add-menu, rebuilt
 * clean-room on CodeC's own flows).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectsHubAddSheet(
    onDismiss: () -> Unit,
    onNewProject: () -> Unit,
    onCloneGit: () -> Unit,
    onImportZip: () -> Unit,
    onOpenFolder: () -> Unit
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
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp, top = 4.dp)
        ) {
            Text(
                stringResource(R.string.hub_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 14.dp)
            )
            HubSheetRow(
                color = Color(0xFFA78BFA),
                iconTint = Color(0xFF241A4F),
                icon = SpckIcons.FilePlus,
                title = stringResource(R.string.hub_sheet_new),
                subtitle = stringResource(R.string.hub_sheet_new_subtitle),
                onClick = onNewProject
            )
            HubSheetRow(
                color = Color(0xFF6366F1),
                iconTint = Color.White,
                icon = SpckIcons.CloneRepo,
                title = stringResource(R.string.hub_sheet_clone),
                subtitle = stringResource(R.string.hub_sheet_clone_subtitle),
                onClick = onCloneGit
            )
            HubSheetRow(
                color = Color(0xFF3B82F6),
                iconTint = Color.White,
                icon = SpckIcons.ZipFile,
                title = stringResource(R.string.import_zip),
                subtitle = stringResource(R.string.hub_sheet_zip_subtitle),
                onClick = onImportZip
            )
            HubSheetRow(
                color = Color(0xFF4CAF50),
                iconTint = Color.White,
                icon = SpckIcons.FolderLine,
                title = stringResource(R.string.hub_sheet_folder),
                subtitle = stringResource(R.string.hub_sheet_folder_subtitle),
                onClick = onOpenFolder
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(18.dp))
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

private val HubBadgeYellow = Color(0xFFE6B33C)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectTree(
    project: ProjectInfo,
    nodes: List<FileNode>,
    viewModel: FileManagerViewModel,
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
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = listOf(project.name, "").joinToString("  >  "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = project.config.entry.takeIf { it.isNotBlank() } ?: "No entry configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
        }
        if (nodes.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.empty_project), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.empty_project_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { onCreateIn("", false) }) { Text(stringResource(R.string.new_file)) }
                }
            }
        } else {
            items(nodes, key = { it.relativePath }) { node ->
                TreeRow(
                    node = node,
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
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(start = (16 + node.depth * 24).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isDirectory) Icons.Default.Folder else iconForFile(node.file.name),
            contentDescription = null,
            tint = if (isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
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

private fun iconForFile(name: String) = when {
    name.endsWith(".c", true) || name.endsWith(".h", true) || name.endsWith(".cpp", true) -> Icons.Default.Code
    else -> Icons.Default.InsertDriveFile
}

private fun formatBytes(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
    else -> "%.1f MB".format(size / (1024.0 * 1024.0))
}

@Composable
private fun EmptyProjectsState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.no_projects), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.no_projects_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        // Phase 15 — the empty state funnels into the same unified sheet.
        Button(onClick = onCreate) { Text(stringResource(R.string.hub_create_first)) }
    }
}

/** Kept as a compatibility entry point for older callers/tests. */
@Composable
fun EmptyStateView(onCreateClick: () -> Unit) = EmptyProjectsState(onCreateClick)
