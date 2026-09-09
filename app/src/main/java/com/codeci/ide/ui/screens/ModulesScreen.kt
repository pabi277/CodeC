package com.codeci.ide.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.ide.R
import com.codeci.ide.ui.modules.PackageCatalog
import com.codeci.ide.ui.modules.PackageItem
import com.codeci.ide.ui.modules.PackageSection
import com.codeci.ide.ui.modules.QuickAction
import com.codeci.ide.ui.services.EmbeddedCompiler
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    modifier: Modifier = Modifier,
    terminalViewModel: TerminalViewModel = activityTerminalViewModel(),
    onNavigateToTerminal: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var customCommand by remember { mutableStateOf("") }
    // Phase 33.2 — the "Unix tools" section is collapsed by default; a tap on
    // its header expands it. The language section is always open.
    var unixToolsExpanded by remember { mutableStateOf(false) }

    val filteredPackages = remember(searchQuery) {
        PackageCatalog.ALL_PACKAGES.filter { item ->
            val query = searchQuery.trim().lowercase()
            query.isEmpty() ||
                item.name.lowercase().contains(query) ||
                item.id.lowercase().contains(query) ||
                item.binary.lowercase().contains(query) ||
                item.description.lowercase().contains(query) ||
                item.installCommand.lowercase().contains(query)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text("Packages & Tools", fontWeight = FontWeight.Bold)
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // SEARCH BAR
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search packages or install commands…") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // PACKAGES — Phase 33.2: "Languages & IntelliSense" is always open
            // and "Unix tools" is collapsed by default (tap the header to
            // expand). A typed search flattens both sections so a name is
            // found regardless of which section it lives in.
            if (filteredPackages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No packages match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (searchQuery.isNotBlank()) {
                items(filteredPackages, key = { it.id }) { item ->
                    PackageCardRow(
                        item = item,
                        context = context,
                        terminalViewModel = terminalViewModel,
                        onNavigateToTerminal = onNavigateToTerminal,
                    )
                }
            } else {
                PackageSection.ordered.forEach { section ->
                    val sectionItems = filteredPackages.filter { PackageCatalog.sectionOf(it) == section }
                    if (sectionItems.isEmpty()) return@forEach
                    val expanded = section == PackageSection.LANGUAGES_INTELLISENSE || unixToolsExpanded
                    item(key = "section_${section.name}") {
                        PackageSectionHeader(
                            title = section.title,
                            expanded = expanded,
                            collapsible = section == PackageSection.UNIX_TOOLS,
                            onToggle = { unixToolsExpanded = !unixToolsExpanded },
                        )
                    }
                    if (expanded) {
                        items(sectionItems, key = { it.id }) { item ->
                            PackageCardRow(
                                item = item,
                                context = context,
                                terminalViewModel = terminalViewModel,
                                onNavigateToTerminal = onNavigateToTerminal,
                            )
                        }
                    }
                }
            }

            // QUICK SYSTEM ACTIONS
            item {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PackageCatalog.QUICK_ACTIONS.forEach { action ->
                        QuickActionChip(
                            action = action,
                            onClick = {
                                Toast.makeText(context, "Running: ${action.command}", Toast.LENGTH_SHORT).show()
                                terminalViewModel.sendCommand(action.command)
                                onNavigateToTerminal()
                            }
                        )
                    }
                }
            }

            // CUSTOM COMMAND RUNNER
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Run Custom Command",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customCommand,
                                onValueChange = { customCommand = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("e.g. pkg install -y git, cc main.c") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (customCommand.isNotBlank()) {
                                        val cmd = customCommand.trim()
                                        Toast.makeText(context, "Running in terminal: $cmd", Toast.LENGTH_SHORT).show()
                                        terminalViewModel.sendCommand(cmd)
                                        onNavigateToTerminal()
                                    }
                                },
                                enabled = customCommand.isNotBlank(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Run", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * One package card inside a hub section, with the installed check and the
 * terminal hand-off wired. Shared by the sectioned list and the search-flat
 * list so both behave identically (Phase 33.2).
 */
@Composable
private fun PackageCardRow(
    item: PackageItem,
    context: Context,
    terminalViewModel: TerminalViewModel,
    onNavigateToTerminal: () -> Unit,
) {
    val isInstalled = remember(item.id) { checkIsInstalled(context, item) }
    PackageItemCard(
        item = item,
        isInstalled = isInstalled,
        onInstall = {
            Toast.makeText(context, "Installing ${item.name}…", Toast.LENGTH_SHORT).show()
            terminalViewModel.sendCommand(item.installCommand)
            onNavigateToTerminal()
        },
        onRun = {
            Toast.makeText(context, "Launching ${item.name}…", Toast.LENGTH_SHORT).show()
            terminalViewModel.sendCommand(item.runCommand)
            onNavigateToTerminal()
        },
        onUninstall = {
            Toast.makeText(context, "Uninstalling ${item.name}…", Toast.LENGTH_SHORT).show()
            terminalViewModel.sendCommand("pkg uninstall -y ${item.id}")
            onNavigateToTerminal()
        },
        onCopyCommand = { cmd ->
            copyToClipboard(context, cmd)
            Toast.makeText(context, "Copied: $cmd", Toast.LENGTH_SHORT).show()
        },
    )
}

/**
 * Phase 33.2 — a hub section header. The language section is a plain label;
 * the "Unix tools" section carries a collapse/expand chevron.
 */
@Composable
private fun PackageSectionHeader(
    title: String,
    expanded: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (collapsible) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (collapsible) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    action: QuickAction,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .clickable { onClick() }
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = action.title,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = action.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun PackageItemCard(
    item: PackageItem,
    isInstalled: Boolean,
    onInstall: () -> Unit,
    onRun: () -> Unit,
    onUninstall: () -> Unit,
    onCopyCommand: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${item.category.title} · ${item.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // STATUS BADGE
                if (isInstalled) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Installed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (item.isBuiltIn) "BUILT-IN" else "INSTALLED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "AVAILABLE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // DESCRIPTION
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // COMMAND SNIPPET BOX
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$ ${item.installCommand}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onCopyCommand(item.installCommand) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Command",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ACTIONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isInstalled) {
                    if (!item.isBuiltIn) {
                        TextButton(onClick = onUninstall) {
                            Text("UNINSTALL", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(onClick = onInstall) {
                            Text("REINSTALL")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Button(onClick = onRun) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (item.isBuiltIn) "RUN CC" else "RUN")
                    }
                } else {
                    Button(onClick = onInstall) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("INSTALL")
                    }
                }
            }
        }
    }
}

private fun checkIsInstalled(context: Context, item: PackageItem): Boolean {
    if (item.isBuiltIn) {
        return EmbeddedCompiler.isAvailable(context)
    }
    val filesDir = context.filesDir
    val binDir = File(File(filesDir, "usr"), "bin")
    val binaryFile = File(binDir, item.binary)
    if (binaryFile.exists() && binaryFile.canExecute()) {
        return true
    }
    val dpkgStatus = File(File(filesDir, "usr"), "var/lib/dpkg/status")
    if (dpkgStatus.exists()) {
        try {
            val text = dpkgStatus.readText()
            if (text.contains("Package: ${item.id}\n") && text.contains("Status: install ok installed")) {
                return true
            }
        } catch (_: Exception) {
        }
    }
    return false
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Command", text)
    clipboard?.setPrimaryClip(clip)
}
