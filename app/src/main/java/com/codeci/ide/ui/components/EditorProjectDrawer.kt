package com.codeci.ide.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeci.ide.R
import com.codeci.ide.ui.utils.WebFileSupport
import com.codeci.ide.ui.viewmodels.EditorFileEntry

/**
 * Phase 16 — the Spck-style navigation drawer for the editor: project header
 * (name / branch chip / source-control badge), the tree toolbar, the Phase 8
 * `FileTreeRepository` entries with git letters and a per-row long-press
 * menu, and the footer rows. The screen owns all actions; this is pure
 * presentation over [EditorFileEntry] + the ViewModel state it is fed.
 */
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
    onSwitchProject: () -> Unit,
    onSourceControl: () -> Unit,
    onSwitchBranch: () -> Unit,
    onOpenSettings: () -> Unit,
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 24.dp)
    ) {
        // ---- header: project + branch + source control -------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSwitchProject)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = projectName ?: stringResource(R.string.editor_scratch_mode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.editor_drawer_switch_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (branch != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "⌥ $branch",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            IconButtonTinted(
                icon = Icons.AutoMirrored.Filled.CallMerge,
                description = stringResource(R.string.editor_drawer_source_control),
                badge = changeCount,
                onClick = onSourceControl
            )
        }
        if (projectName != null) {
            TextButton(
                onClick = onSwitchBranch,
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.editor_drawer_switch_branch),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        HorizontalDivider()

        // ---- tree toolbar --------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            DrawerToolAction(
                icon = Icons.Default.Add,
                label = stringResource(R.string.new_file),
                onClick = { onNewFile(null) }
            )
            DrawerToolAction(
                icon = Icons.Default.CreateNewFolder,
                label = stringResource(R.string.new_folder),
                onClick = { onNewFolder(null) }
            )
            DrawerToolAction(
                icon = Icons.Default.Refresh,
                label = stringResource(R.string.refresh),
                onClick = onRefresh
            )
            DrawerToolAction(
                icon = if (allCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                label = stringResource(
                    if (allCollapsed) R.string.editor_drawer_expand_all else R.string.editor_drawer_collapse_all
                ),
                onClick = onToggleCollapseAll
            )
        }
        HorizontalDivider()

        // ---- the tree --------------------------------------------------------
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.editor_drawer_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                items(entries, key = { "${if (it.isDirectory) "d" else "f"}:${it.relativePath}" }) { entry ->
                    DrawerRow(
                        entry = entry,
                        expanded = !collapsedDirs.contains(entry.relativePath),
                        selected = entry.relativePath == selectedPath,
                        isLaunchDefault = entry.relativePath == launchDefault,
                        hasLaunchDefault = launchDefault != null,
                        badge = gitBadges[entry.relativePath],
                        onOpenOrToggle = { onOpenEntry(entry) },
                        onAction = { action ->
                            when (action) {
                                RowAction.Open -> onOpenEntry(entry)
                                RowAction.Rename -> onRenameEntry(entry)
                                RowAction.Delete -> onDeleteEntry(entry)
                                RowAction.Run -> onRunInTerminal(entry)
                                RowAction.Launch -> onLaunchEntry(entry)
                                RowAction.SetDefault -> onSetLaunchDefault(entry)
                                RowAction.ClearDefault -> onClearLaunchDefault()
                                RowAction.CopyPath -> onCopyPath(entry)
                                RowAction.NewFileHere -> onNewFile(entry.relativePath)
                                RowAction.NewFolderHere -> onNewFolder(entry.relativePath)
                            }
                        }
                    )
                }
            }
        }
        HorizontalDivider()

        // ---- footer ----------------------------------------------------------
        DrawerFooterRow(
            icon = Icons.AutoMirrored.Filled.CallMerge,
            label = stringResource(R.string.editor_drawer_source_control),
            badge = changeCount,
            onClick = onSourceControl
        )
        DrawerFooterRow(
            icon = Icons.Default.Settings,
            label = stringResource(R.string.editor_drawer_settings),
            onClick = onOpenSettings
        )
        Spacer(Modifier.height(16.dp))
    }
}

private enum class RowAction {
    Open, Rename, Delete, Run, Launch, SetDefault, ClearDefault, CopyPath, NewFileHere, NewFolderHere
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
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 1.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        Color.Transparent
                    }
                )
                .combinedClickable(
                    onClick = onOpenOrToggle,
                    onLongClick = { menuOpen = true }
                )
                .padding(
                    start = (8 + entry.depth * 14).dp,
                    end = 8.dp,
                    top = 7.dp,
                    bottom = 7.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (entry.isDirectory) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(Modifier.width(22.dp))
                Icon(
                    imageVector = if (WebFileSupport.isHtml(entry.name)) Icons.Default.Web else Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = when {
                        isLaunchDefault -> Color(0xFF42A5F5) // Spck marks the launch file blue
                        WebFileSupport.isHtml(entry.name) -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = entry.name,
                style = if (entry.isDirectory) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (badge) {
                        "M" -> Color(0xFFFFB347)
                        "A" -> Color(0xFF66BB6A)
                        "D" -> Color(0xFFFF5555)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            if (isLaunchDefault) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = stringResource(R.string.editor_drawer_launch_default),
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF42A5F5)
                )
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DrawerEntryMenu(
                entry = entry,
                hasLaunchDefault = hasLaunchDefault,
                onAction = { action ->
                    menuOpen = false
                    onAction(action)
                }
            )
        }
    }
}

@Composable
private fun DrawerEntryMenu(
    entry: EditorFileEntry,
    hasLaunchDefault: Boolean,
    onAction: (RowAction) -> Unit
) {
    if (!entry.isDirectory) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.open)) },
            onClick = { onAction(RowAction.Open) }
        )
    }
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(R.string.rename)) },
        onClick = { onAction(RowAction.Rename) }
    )
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(R.string.delete)) },
        onClick = { onAction(RowAction.Delete) }
    )
    if (entry.isDirectory) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.new_file)) },
            onClick = { onAction(RowAction.NewFileHere) }
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.new_folder)) },
            onClick = { onAction(RowAction.NewFolderHere) }
        )
    } else {
        if (entry.name.endsWith(".c", ignoreCase = true)) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.run_in_terminal)) },
                onClick = { onAction(RowAction.Run) }
            )
        }
        if (WebFileSupport.isHtml(entry.name)) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_drawer_launch)) },
                onClick = { onAction(RowAction.Launch) }
            )
        }
        if (entry.projectName != null) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_drawer_set_default)) },
                onClick = { onAction(RowAction.SetDefault) }
            )
            if (hasLaunchDefault) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_drawer_clear_default)) },
                    onClick = { onAction(RowAction.ClearDefault) }
                )
            }
        }
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.editor_drawer_copy_path)) },
            onClick = { onAction(RowAction.CopyPath) }
        )
    }
}

@Composable
private fun DrawerToolAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DrawerFooterRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    badge: Int = 0
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (badge > 0) {
            Badge { Text(badge.toString()) }
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IconButtonTinted(icon: ImageVector, description: String, badge: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BadgedBox(
            badge = { if (badge > 0) Badge { Text(badge.toString()) } }
        ) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
        }
    }
}
