package com.codeci.ide.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeci.ide.R
import com.codeci.ide.ui.editor.DrawerProjectList
import com.codeci.ide.ui.utils.WebFileSupport
import com.codeci.ide.ui.viewmodels.EditorFileEntry

/** Phase 67.1: reference-style hierarchy and menus over the existing file engine. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorProjectDrawer(
    projectName: String?,
    branch: String?,
    changeCount: Int,
    entries: List<EditorFileEntry>,
    collapsedDirs: Set<String>,
    selectedPath: String?,
    launchDefault: String?,
    gitBadges: Map<String, String>,
    /**
     * Phase 46.2 — false in the editor's SINGLE_FILE mode: a peek carries no
     * project tree, no git rows and no create/rename toolbar. What remains is
     * the header, the PROJECTS list (47.1) only.
     */
    showProjectTree: Boolean = true,
    onSourceControl: () -> Unit,
    onSwitchBranch: () -> Unit,
    /** The Files overflow opens the in-panel project switcher. */
    onSwitchProject: () -> Unit,
    /** Explicit Close files panel action: no save or navigation side effects. */
    onClose: () -> Unit = {},
    /** Phase 47.1 — the in-drawer project list (built by [DrawerProjectList]). */
    projects: List<DrawerProjectList.Row> = emptyList(),
    projectsExpanded: Boolean = false,
    onToggleProjects: () -> Unit = {},
    /** null picks the Single files context — the old dialog's exact behaviour. */
    onSelectProject: (String?) -> Unit = {},
    /** Phase 47.1 — jumps to the Projects screen with the `+` sheet open. */
    onNewProject: () -> Unit = {},
    onNewFile: (String?) -> Unit,
    onNewFolder: (String?) -> Unit,
    onRefresh: () -> Unit,
    onToggleCollapseAll: () -> Unit,
    allCollapsed: Boolean,
    onOpenEntry: (EditorFileEntry) -> Unit,
    onRenameEntry: (EditorFileEntry) -> Unit,
    onDeleteEntry: (EditorFileEntry) -> Unit,
    onRunInTerminal: (EditorFileEntry) -> Unit,
    onLaunchEntry: (EditorFileEntry) -> Unit,
    onSetLaunchDefault: (EditorFileEntry) -> Unit,
    onClearLaunchDefault: () -> Unit,
    onCopyPath: (EditorFileEntry) -> Unit,
    onShareFile: (EditorFileEntry) -> Unit = {},
    onDownloadFile: (EditorFileEntry) -> Unit = {},
    fileSearchOpen: Boolean = false,
    fileSearchQuery: String = "",
    onFileSearchQuery: (String) -> Unit = {},
    onCloseFileSearch: () -> Unit = {},
    onSearch: () -> Unit = {},
    onLocate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var overflow by remember { mutableStateOf(false) }
    var rootMenu by remember { mutableStateOf(false) }
    var rootExpanded by remember(projectName) { mutableStateOf(true) }
    var locateRequest by remember(projectName) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val fileSearchFocus = remember { FocusRequester() }
    LaunchedEffect(fileSearchOpen) {
        if (fileSearchOpen && showProjectTree) {
            rootExpanded = true
            fileSearchFocus.requestFocus()
        }
    }
    LaunchedEffect(locateRequest, entries, selectedPath) {
        if (locateRequest) {
            val index = entries.indexOfFirst { it.relativePath == selectedPath }
            if (index >= 0) { listState.animateScrollToItem(index); locateRequest = false }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth().fillMaxHeight()) {
        val compactToolbar = maxWidth < 300.dp
        Column(Modifier.fillMaxWidth().fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
            // Reference-style toolbar; every glyph has a real, named action.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("FILES", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showProjectTree) {
                    DrawerIcon(Icons.Default.Search, "Find file", onSearch)
                    if (!compactToolbar) DrawerIcon(Icons.Default.MyLocation, "Locate active file") {
                        rootExpanded = true
                        onLocate()
                        locateRequest = true
                    }
                    DrawerIcon(Icons.Default.NoteAdd, "New file") { onNewFile(null) }
                    DrawerIcon(Icons.Default.CreateNewFolder, "New folder") { onNewFolder(null) }
                }
                Box {
                    DrawerIcon(Icons.Default.MoreHoriz, "Files actions") { overflow = true }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(text = { Text("Switch project") }, onClick = {
                            overflow = false; onSwitchProject()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_drawer_new_project)) },
                            onClick = { overflow = false; onNewProject() })
                        if (showProjectTree) {
                            if (compactToolbar) DropdownMenuItem(text = { Text("Locate active file") },
                                leadingIcon = { Icon(Icons.Default.MyLocation, null) },
                                onClick = { overflow = false; rootExpanded = true; onLocate(); locateRequest = true })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Refresh files") },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = { overflow = false; onRefresh() })
                            DropdownMenuItem(text = { Text(if (allCollapsed) "Expand all" else "Collapse all") },
                                onClick = { overflow = false; rootExpanded = true; onToggleCollapseAll() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Source control" + if (changeCount > 0) " ($changeCount)" else "") },
                                onClick = { overflow = false; onSourceControl() })
                            DropdownMenuItem(text = { Text("Switch branch" + (branch?.let { " · $it" } ?: "")) },
                                onClick = { overflow = false; onSwitchBranch() })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Close files panel") },
                            leadingIcon = { Icon(Icons.Default.Close, null) },
                            onClick = { overflow = false; onClose() })
                    }
                }
            }
            if (fileSearchOpen && showProjectTree) {
                OutlinedTextField(value = fileSearchQuery, onValueChange = onFileSearchQuery,
                    label = { Text("Find file by name or path") }, singleLine = true,
                    trailingIcon = { DrawerIcon(Icons.Default.Close, "Close file search", onCloseFileSearch) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).focusRequester(fileSearchFocus))
            }
            if (projectsExpanded) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Projects", Modifier.weight(1f).padding(start = 12.dp))
                    DrawerIcon(Icons.Default.Close, "Close project list", onToggleProjects)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 192.dp)) {
                    if (projects.none { it.contextName != null }) item {
                        Text(DrawerProjectList.EMPTY_PROJECTS_COPY, Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    items(projects, key = { it.contextName ?: "__single_files__" }) { row ->
                        TextButton(onClick = { onSelectProject(row.contextName) }, modifier = Modifier
                            .fillMaxWidth().semantics { selected = row.isCurrent }) {
                            Text(if (row.isCurrent) "●  ${row.label}" else row.label,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                HorizontalDivider()
            }
            if (showProjectTree) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { stateDescription = if (rootExpanded) "Expanded" else "Collapsed" }
                    .clickable { rootExpanded = !rootExpanded }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (rootExpanded) Icons.Default.ExpandMore else Icons.Default.KeyboardArrowRight, null)
                    Text(projectName ?: stringResource(R.string.editor_scratch_mode),
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box {
                        DrawerIcon(Icons.Default.MoreHoriz, "Project folder actions") { rootMenu = true }
                        DropdownMenu(expanded = rootMenu, onDismissRequest = { rootMenu = false }) {
                            DropdownMenuItem(text = { Text(projectName ?: "Single files", maxLines = 3) },
                                enabled = false, onClick = {})
                            DropdownMenuItem(text = { Text("New file") },
                                leadingIcon = { Icon(Icons.Default.NoteAdd, null) },
                                onClick = { rootMenu = false; onNewFile(null) })
                            DropdownMenuItem(text = { Text("New folder") },
                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                onClick = { rootMenu = false; onNewFolder(null) })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Switch project") },
                                onClick = { rootMenu = false; onSwitchProject() })
                        }
                    }
                }
                if (rootExpanded) {
                    if (entries.isEmpty()) Text(if (fileSearchQuery.isNotBlank()) "No matching files or folders"
                        else stringResource(R.string.editor_drawer_empty), Modifier.padding(16.dp))
                    else LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(entries, key = { "${if (it.isDirectory) "d" else "f"}:${it.relativePath}" }) { entry ->
                            DrawerRow(entry, !collapsedDirs.contains(entry.relativePath),
                                entry.relativePath == selectedPath, entry.relativePath == launchDefault,
                                launchDefault != null, gitBadges[entry.relativePath],
                                onOpenOrToggle = { onOpenEntry(entry) }, onAction = { action ->
                                    when (action) {
                                        RowAction.Open -> onOpenEntry(entry)
                                        RowAction.Rename -> onRenameEntry(entry)
                                        RowAction.Delete -> onDeleteEntry(entry)
                                        RowAction.Run -> onRunInTerminal(entry)
                                        RowAction.Launch -> onLaunchEntry(entry)
                                        RowAction.SetDefault -> onSetLaunchDefault(entry)
                                        RowAction.ClearDefault -> onClearLaunchDefault()
                                        RowAction.CopyPath -> onCopyPath(entry)
                                        RowAction.Share -> onShareFile(entry)
                                        RowAction.Download -> onDownloadFile(entry)
                                        RowAction.NewFileHere -> onNewFile(entry.relativePath)
                                        RowAction.NewFolderHere -> onNewFolder(entry.relativePath)
                                    }
                                })
                        }
                    }
                }
            } else Text(stringResource(R.string.editor_single_file_drawer_hint), Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DrawerIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

private enum class RowAction {
    Open, Rename, Delete, Run, Launch, SetDefault, ClearDefault, CopyPath, NewFileHere, NewFolderHere, Share, Download
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(
    entry: EditorFileEntry,
    expanded: Boolean,
    selected: Boolean,
    isLaunchDefault: Boolean,
    hasLaunchDefault: Boolean,
    badge: String?,
    onOpenOrToggle: () -> Unit,
    onAction: (RowAction) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val guide = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val depth = entry.depth.coerceIn(0, 6)
    Box {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .drawBehind {
                for (level in 0..depth) {
                    val x = (16 + level * 16).dp.toPx()
                    drawLine(guide, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                }
            }
            .semantics {
                this.selected = selected
                if (entry.isDirectory) stateDescription = if (expanded) "Expanded" else "Collapsed"
            }, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).heightIn(min = 48.dp)
                .combinedClickable(onClick = onOpenOrToggle, onLongClick = { menuOpen = true })
                .padding(start = (20 + depth * 16).dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (entry.isDirectory) Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.KeyboardArrowRight,
                    null, Modifier.size(20.dp), tint = muted)
                else if (isLaunchDefault) Icon(Icons.Default.PlayArrow, "Launch default",
                    Modifier.size(20.dp), tint = Color(0xFF27AE80))
                else Spacer(Modifier.width(20.dp))
                if (!entry.isDirectory) FileIconView(entry.name, false, Modifier.size(18.dp), tint = muted)
                Text(entry.name, modifier = Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (badge != null) Text(badge, color = muted, style = MaterialTheme.typography.labelMedium)
            }
            Box {
                DrawerIcon(Icons.Default.MoreHoriz, "Actions for ${entry.name}") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DrawerEntryMenu(entry, hasLaunchDefault) { action -> menuOpen = false; onAction(action) }
                }
            }
        }
    }
}

@Composable
private fun DrawerEntryMenu(
    entry: EditorFileEntry,
    hasLaunchDefault: Boolean,
    onAction: (RowAction) -> Unit
) {
    Text(entry.relativePath, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    HorizontalDivider()
    if (!entry.isDirectory) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.open)) },
            leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
            onClick = { onAction(RowAction.Open) }
        )
    }
    if (entry.isDirectory) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.new_file)) },
            leadingIcon = { Icon(Icons.Default.NoteAdd, null) },
            onClick = { onAction(RowAction.NewFileHere) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.new_folder)) },
            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
            onClick = { onAction(RowAction.NewFolderHere) }
        )
    } else {
        if (entry.name.endsWith(".c", ignoreCase = true)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.run_in_terminal)) },
            leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                onClick = { onAction(RowAction.Run) }
            )
        }
        if (WebFileSupport.isHtml(entry.name)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_drawer_launch)) },
            leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                onClick = { onAction(RowAction.Launch) }
            )
        }
        if (entry.projectName != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_drawer_set_default)) },
            leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                onClick = { onAction(RowAction.SetDefault) }
            )
            if (hasLaunchDefault) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_drawer_clear_default)) },
            leadingIcon = { Icon(Icons.Default.Close, null) },
                    onClick = { onAction(RowAction.ClearDefault) }
                )
            }
        }
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(stringResource(R.string.rename)) },
            leadingIcon = { Icon(Icons.Default.Edit, null) },
        onClick = { onAction(RowAction.Rename) }
    )

    DropdownMenuItem(
        text = { Text(stringResource(R.string.editor_drawer_copy_path)) },
            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
        onClick = { onAction(RowAction.CopyPath) }
    )
    if (!entry.isDirectory) {
        HorizontalDivider()
        DropdownMenuItem(text = { Text("Share as file") },
            leadingIcon = { Icon(Icons.Default.Share, null) },
            onClick = { onAction(RowAction.Share) })
        DropdownMenuItem(text = { Text("Download") },
            leadingIcon = { Icon(Icons.Default.Download, null) },
            onClick = { onAction(RowAction.Download) })
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        onClick = { onAction(RowAction.Delete) }
    )
}
