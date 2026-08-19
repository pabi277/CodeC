package com.codeci.ide.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.R
import com.codeci.ide.ui.modules.InstallStatus
import com.codeci.ide.ui.modules.ModuleState
import com.codeci.ide.ui.viewmodels.ModuleViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    modifier: Modifier = Modifier,
    viewModel: ModuleViewModel = activityModuleViewModel()
) {
    val modules by viewModel.modules.collectAsState()
    val message by viewModel.catalogMessage.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshCatalog() }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (read != PackageManager.PERMISSION_GRANTED || write != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
        viewModel.refreshCatalog()
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.modules_title)) })
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!message.isNullOrBlank()) {
                item {
                    Text(message.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
            items(modules, key = { it.module.id }) { state ->
                ModuleCard(
                    state = state,
                    onDownload = { viewModel.downloadModule(state.module.id) },
                    onUninstall = { viewModel.uninstallModule(state.module.id) }
                )
            }
        }
    }
}

@Composable
fun activityModuleViewModel(): ModuleViewModel {
    val activity = LocalContext.current as ComponentActivity
    return viewModel(viewModelStoreOwner = activity)
}

@Composable
private fun ModuleCard(
    state: ModuleState,
    onDownload: () -> Unit,
    onUninstall: () -> Unit
) {
    val module = state.module
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(module.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${module.version} · ${formatSize(module.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        module.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                StatusActions(state = state, onDownload = onDownload, onUninstall = onUninstall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            when (val status = state.status) {
                is InstallStatus.Downloading -> {
                    LinearProgressIndicator(
                        progress = status.progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.downloading, status.progress))
                }
                InstallStatus.Extracting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.installing),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                is InstallStatus.Failed -> {
                    Text(
                        stringResource(R.string.module_download_failed, status.error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    if (state.updateAvailable && status is InstallStatus.Installed) {
                        Text(stringResource(R.string.update_available), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusActions(
    state: ModuleState,
    onDownload: () -> Unit,
    onUninstall: () -> Unit
) {
    when (val status = state.status) {
        InstallStatus.Installed -> {
            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.installed),
                    tint = MaterialTheme.colorScheme.primary
                )
                if (state.updateAvailable) {
                    Button(onClick = onDownload) { Text(stringResource(R.string.update_available)) }
                } else {
                    OutlinedButton(onClick = onUninstall) { Text(stringResource(R.string.uninstall)) }
                }
            }
        }
        is InstallStatus.Failed -> {
            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Button(onClick = onDownload) { Text(stringResource(R.string.retry)) }
            }
        }
        is InstallStatus.Downloading, InstallStatus.Extracting -> { }
        InstallStatus.NotInstalled -> {
            Button(onClick = onDownload) { Text(stringResource(R.string.download)) }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}