package com.codeci.ide.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
 * Phase 16 (mockup-exact) — the Spck-style navigation drawer: project name
 * with a purple source-control glyph, an outlined `⌥ branch ▾` chip, the
 * four-column tree toolbar (New File / New Folder / Refresh / Collapse All),
 * the Phase 8 `FileTreeRepository` entries with typed file icons, git
 * M/A/D/? letters and the purple selected-row highlight, and the two footer
 * rows (Source Control with change badge, Switch Branch).
 *
 * Pure presentation over [EditorFileEntry] + the state its screen feeds in;
 * all actions and IO live in the screen / ViewModel.
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
    onSourceControl: () -> Unit,
    onSwitchBranch: () -> Unit,
    onSwitchProject: () -> Unit,
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
        // ---- header: project name + source-control glyph ------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSwitchProject)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = projectName ?: stringResource(R.string.editor_scratch_mode),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSourceControl),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.BadgedBox(
                    badge = { if (changeCount > 0) Badge { Text(changeCount.toString()) } }
                ) {
                    // Mockup-exact: the same purple branch glyph as the chip.
                    Icon(
                        SpckIcons.GitBranch,
                        contentDescription = stringResource(R.string.editor_drawer_source_control),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ---- branch chip ----------------------------------------------------
        if (branch != null) {
            Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(50)
                        )
                        .clickable(onClick = onSwitchBranch)
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
        }
        HorizontalDivider()

        // ---- tree toolbar (four equal columns) ------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawerToolAction(
                icon = Icons.Default.NoteAdd,
                label = stringResource(R.string.new_file),
                onClick = { onNewFile(null) },
                modifier = Modifier.weight(1f)
            )
            DrawerToolAction(
                icon = Icons.Default.CreateNewFolder,
                label = stringResource(R.string.new_folder),
                onClick = { onNewFolder(null) },
                modifier = Modifier.weight(1f)
            )
            DrawerToolAction(
                icon = Icons.Default.Refresh,
                label = stringResource(R.string.refresh),
                onClick = onRefresh,
                modifier = Modifier.weight(1f)
            )
            DrawerToolAction(
                icon = SpckIcons.CollapseAll,
                label = stringResource(
                    if (allCollapsed) R.string.editor_drawer_expand_all else R.string.editor_drawer_collapse_all
                ),
                onClick = onToggleCollapseAll,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider()

        // ---- the tree ---------------------------------------------------------
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.editor_drawer_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 6.dp,
                    vertical = 4.dp
                )
            ) {
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

        // ---- footer -----------------------------------------------------------
        DrawerFooterRow(
            icon = SpckIcons.GitBranch,
            label = stringResource(R.string.editor_drawer_source_control),
            badge = changeCount,
            onClick = onSourceControl
        )
        DrawerFooterRow(
            icon = SpckIcons.GitBranch,
            label = stringResource(R.string.editor_drawer_switch_branch),
            badge = 0,
            onClick = onSwitchBranch
        )
        Spacer(Modifier.height(10.dp))
    }
}

private enum class RowAction {
    Open, Rename, Delete, Run, Launch, SetDefault, ClearDefault, CopyPath, NewFileHere, NewFolderHere
}

/** Extension → the drawer's typed file icon (Spck shows a mark per language). */
@Composable
private fun FileTypeIcon(name: String, isLaunchDefault: Boolean, tint: Color) {
    when {
        name.endsWith(".py", ignoreCase = true) ->
            Icon(SpckIcons.PythonLogo, contentDescription = null, modifier = Modifier.size(18.dp))
        WebFileSupport.isHtml(name) ->
            Icon(SpckIcons.HtmlShield, contentDescription = null, modifier = Modifier.size(18.dp))
        name.endsWith(".md", ignoreCase = true) || name.endsWith(".txt", ignoreCase = true) ->
            // Mockup: the book mark is white, not grey.
            Icon(
                SpckIcons.BookLine,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        else ->
            Icon(SpckIcons.FileLine, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
    }
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
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 1.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) {
                        // Mockup: a clearly visible mid-purple row highlight.
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    } else {
                        Color.Transparent
                    }
                )
                .combinedClickable(
                    onClick = onOpenOrToggle,
                    onLongClick = { menuOpen = true }
                )
                .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (entry.isDirectory) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = muted
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = SpckIcons.FolderLine,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Spacer(Modifier.width(22.dp))
                FileTypeIcon(
                    name = entry.name,
                    isLaunchDefault = isLaunchDefault,
                    tint = muted
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isLaunchDefault) {
                // Spck marks the launch-default file with a blue mark.
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(8.dp)
                        .background(LaunchDefaultBlue, CircleShape)
                )
            }
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (badge) {
                        "M" -> Color(0xFFE6B33C)
                        "A" -> Color(0xFF66BB6A)
                        "D" -> Color(0xFFFF5555)
                        // Phase 17 — Spck marks merge conflicts purple.
                        "U" -> Color(0xFFBA68C8)
                        else -> muted.copy(alpha = 0.8f)
                    },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
        DropdownMenu(
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

private val LaunchDefaultBlue = Color(0xFF42A5F5)

@Composable
private fun DrawerEntryMenu(
    entry: EditorFileEntry,
    hasLaunchDefault: Boolean,
    onAction: (RowAction) -> Unit
) {
    if (!entry.isDirectory) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.open)) },
            onClick = { onAction(RowAction.Open) }
        )
    }
    if (entry.isDirectory) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.new_file)) },
            onClick = { onAction(RowAction.NewFileHere) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.new_folder)) },
            onClick = { onAction(RowAction.NewFolderHere) }
        )
    } else {
        if (entry.name.endsWith(".c", ignoreCase = true)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.run_in_terminal)) },
                onClick = { onAction(RowAction.Run) }
            )
        }
        if (WebFileSupport.isHtml(entry.name)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_drawer_launch)) },
                onClick = { onAction(RowAction.Launch) }
            )
        }
        if (entry.projectName != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_drawer_set_default)) },
                onClick = { onAction(RowAction.SetDefault) }
            )
            if (hasLaunchDefault) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_drawer_clear_default)) },
                    onClick = { onAction(RowAction.ClearDefault) }
                )
            }
        }
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.rename)) },
        onClick = { onAction(RowAction.Rename) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.delete)) },
        onClick = { onAction(RowAction.Delete) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.editor_drawer_copy_path)) },
        onClick = { onAction(RowAction.CopyPath) }
    )
}

@Composable
private fun DrawerToolAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DrawerFooterRow(
    icon: ImageVector,
    label: String,
    badge: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (badge > 0) {
            Badge { Text(badge.toString()) }
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
