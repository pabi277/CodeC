package com.codeci.ide.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeci.ide.R
import com.codeci.ide.ui.services.EmbeddedCompiler
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.stats.StatsManager
import com.codeci.ide.ui.utils.FileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToEditor: (String?) -> Unit = {},
    onNavigateToFileManager: () -> Unit = {},
    onNavigateToTemplates: () -> Unit = {},
    onNavigateToModules: () -> Unit = {},
    onNavigateToTerminal: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val statsManager = remember { StatsManager(context) }
    val fileManager = remember { FileManager(context) }
    val recentFiles by settingsManager.recentFilesOrderedFlow.collectAsState(initial = emptyList())
    val totalRuns by statsManager.totalRunsFlow.collectAsState(initial = 0)
    val runStreak by statsManager.currentStreakFlow.collectAsState(initial = 0)
    val totalFiles = fileManager.listFiles().size
    val moduleViewModel = activityModuleViewModel()
    val compilerInstalled by moduleViewModel.isCompilerInstalled.collectAsState()
    val builtInTcc by remember { mutableStateOf(EmbeddedCompiler.isAvailable(context)) }
    val compilerAvailable = compilerInstalled || builtInTcc

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_ready),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (builtInTcc && !compilerInstalled) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Built-in compiler ready",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "CodeC ships with its own offline C compiler (TCC) — " +
                                    "just write code and tap RUN. No downloads needed.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (!compilerAvailable) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.compiler_required_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = stringResource(R.string.compiler_required_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                val compiler = moduleViewModel.requiredCompiler()
                                if (compiler != null) {
                                    moduleViewModel.downloadModule(compiler.id)
                                }
                                onNavigateToModules()
                            }) {
                                Text(stringResource(R.string.download_now))
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionCard(
                            title = stringResource(R.string.action_new_file),
                            icon = Icons.Default.Add,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToEditor(null) }
                        )
                        ActionCard(
                            title = stringResource(R.string.action_open_file),
                            icon = Icons.Default.Folder,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToFileManager
                        )
                        ActionCard(
                            title = stringResource(R.string.action_templates),
                            icon = Icons.Default.AutoAwesomeMosaic,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToTemplates
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionCard(
                            title = stringResource(R.string.action_terminal),
                            icon = Icons.Default.Terminal,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToTerminal
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.recent_files),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (recentFiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_recent_files),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentFiles.forEach { file ->
                            RecentFileItem(fileName = file, onClick = { onNavigateToEditor(file) })
                        }
                    }
                }
            }

            item {
                StatsCard(
                    totalFiles = totalFiles.toString(),
                    totalRuns = totalRuns.toString(),
                    streak = if (runStreak > 0) "$runStreak \uD83D\uDD25" else "0"
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RecentFileItem(fileName: String, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = stringResource(R.string.file_icon),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun StatsCard(totalFiles: String, totalRuns: String, streak: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(label = stringResource(R.string.stat_total_files), value = totalFiles)
            StatItem(label = stringResource(R.string.stat_total_runs), value = totalRuns)
            StatItem(label = stringResource(R.string.stat_streak), value = streak)
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
