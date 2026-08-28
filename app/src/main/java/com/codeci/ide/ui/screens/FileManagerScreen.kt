package com.codeci.ide.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.R
import com.codeci.ide.ui.projects.FileNode
import com.codeci.ide.ui.projects.ProjectInfo
import com.codeci.ide.ui.viewmodels.FileManagerViewModel
import kotlinx.coroutines.launch

/** Projects and private, hierarchical source tree (Phase 8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: FileManagerViewModel = viewModel(),
    onFileSelected: (String) -> Unit = {},
    onProjectFileSelected: (projectName: String, relativePath: String) -> Unit = { _, path -> onFileSelected(path) },
    onProjectSelected: (ProjectInfo) -> Unit = {},
    onPreviewFile: (String) -> Unit = {},
    onProjectPreviewFile: (projectName: String, relativePath: String) -> Unit = { _, path -> onPreviewFile(path) }
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val tree by viewModel.tree.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreateProject by remember { mutableStateOf(false) }
    var showCreateItem by remember { mutableStateOf(false) }
    var newItemParent by remember { mutableStateOf("") }
    var newItemFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteProjectTarget by remember { mutableStateOf<ProjectInfo?>(null) }
    var zipImportUri by remember { mutableStateOf<Uri?>(null) }
    var showZipNameDialog by remember { mutableStateOf(false) }
    var exportProjectName by remember { mutableStateOf<String?>(null) }

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
            zipImportUri = uri
            showZipNameDialog = true
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
                        Text(stringResource(R.string.projects_title))
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
                        IconButton(onClick = { folderImportLauncher.launch(null) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.import_folder))
                        }
                        IconButton(onClick = { zipImportLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                            Icon(Icons.Default.Archive, contentDescription = stringResource(R.string.import_zip))
                        }
                        IconButton(onClick = { viewModel.refresh(context) }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_projects))
                        }
                    } else {
                        IconButton(onClick = {
                            activeProject?.let {
                                exportProjectName = it.name
                                exportLauncher.launch("${it.name}.zip")
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.export_zip))
                        }
                        IconButton(onClick = { fileImportLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.UploadFile, contentDescription = stringResource(R.string.import_file))
                        }
                        IconButton(onClick = {
                            newItemParent = ""
                            newItemFolder = false
                            showCreateItem = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_file))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            if (activeProject == null) {
                FloatingActionButton(onClick = { showCreateProject = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_project))
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (activeProject == null) {
                ProjectList(
                    projects = projects,
                    onOpen = ::selectProject,
                    onDelete = { deleteProjectTarget = it },
                    onCreate = { showCreateProject = true },
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
        AlertDialog(
            onDismissRequest = { showCreateProject = false },
            title = { Text(stringResource(R.string.new_project)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.project_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.createProject(context, name) { project ->
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
        var name by remember { mutableStateOf("imported_project") }
        AlertDialog(
            onDismissRequest = {
                showZipNameDialog = false
                zipImportUri = null
            },
            title = { Text(stringResource(R.string.import_zip)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.project_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = zipImportUri
                    if (uri != null && name.isNotBlank()) {
                        viewModel.importZip(context, uri, name) { imported ->
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
}

@Composable
private fun ProjectList(
    projects: List<ProjectInfo>,
    onOpen: (ProjectInfo) -> Unit,
    onDelete: (ProjectInfo) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (projects.isEmpty()) {
        EmptyProjectsState(onCreate)
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "${projects.size} project${if (projects.size == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(projects, key = { it.name }) { project ->
            ProjectCard(project, onOpen, onDelete)
        }
        item {
            OutlinedActionButton(
                text = stringResource(R.string.new_project),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onCreate
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectInfo,
    onOpen: (ProjectInfo) -> Unit,
    onDelete: (ProjectInfo) -> Unit
) {
    Card(
        onClick = { onOpen(project) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${project.config.type} project · ${project.root.listFiles()?.size ?: 0} top-level items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onDelete(project) }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_project))
            }
        }
    }
}

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
                    onPreview = onPreview
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
    onPreview: (String) -> Unit
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
            } else if (WebFileName.isPreviewable(node.file.name)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.preview)) },
                    onClick = { menuOpen = false; onPreview(node.relativePath) }
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
        Button(onClick = onCreate) { Text(stringResource(R.string.new_project)) }
    }
}

@Composable
private fun OutlinedActionButton(text: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

/** Kept as a compatibility entry point for older callers/tests. */
@Composable
fun EmptyStateView(onCreateClick: () -> Unit) = EmptyProjectsState(onCreateClick)
