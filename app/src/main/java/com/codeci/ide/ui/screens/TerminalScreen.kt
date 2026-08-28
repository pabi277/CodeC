package com.codeci.ide.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.MainActivity
import com.codeci.ide.R
import com.codeci.ide.ui.components.TerminalEmulatorView
import com.codeci.ide.ui.components.TerminalExtraKeys
import com.codeci.ide.ui.components.openTerminalUrl
import com.codeci.ide.ui.components.parseExtraKeysMacros
import com.codeci.ide.ui.terminal.ShellEnvironment
import com.codeci.ide.ui.theme.getTerminalTheme
import com.codeci.ide.ui.viewmodels.TerminalViewModel

@Composable
fun activityTerminalViewModel(): TerminalViewModel {
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    return viewModel(viewModelStoreOwner = activity)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier,
    initialCommand: String? = null,
    commandNonce: String? = null,
    viewModel: TerminalViewModel = activityTerminalViewModel()
) {
    val context = LocalContext.current
    val snapshot by viewModel.snapshot.collectAsState()
    val alive by viewModel.alive.collectAsState()
    val fontSize by viewModel.fontSizeSp.collectAsState()
    val fontFamily by viewModel.fontFamily.collectAsState()
    val terminalThemeType by viewModel.terminalTheme.collectAsState()
    val terminalTheme = getTerminalTheme(terminalThemeType)
    val ctrl by viewModel.ctrlLatched.collectAsState()
    val alt by viewModel.altLatched.collectAsState()
    val macrosRaw by viewModel.extraKeysMacros.collectAsState()
    val customMacros = remember(macrosRaw) { parseExtraKeysMacros(macrosRaw) }

    var activeSelection by remember { mutableStateOf<String?>(null) }
    var bellTrigger by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) { viewModel.ensureStarted() }
    LaunchedEffect(Unit) {
        viewModel.bellEvents.collect {
            bellTrigger = System.currentTimeMillis()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.storagePermissionRequests.collect {
            (context as? MainActivity)?.requestStoragePermissions()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.notificationPermissionRequests.collect { request ->
            (context as? MainActivity)?.requestNotificationPermission(request)
        }
    }
    LaunchedEffect(commandNonce, initialCommand) {
        if (!initialCommand.isNullOrBlank()) {
            viewModel.sendCommand(initialCommand)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .imePadding()
            .background(terminalTheme.background)
    ) {
        TopAppBar(
            title = {
                val suffix = if (alive) "" else " — exited"
                Text(
                    text = (snapshot.title.ifBlank { stringResource(R.string.nav_terminal) }) + suffix,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            actions = {
                IconButton(onClick = {
                    if (ShellEnvironment.hasStoragePermission(context)) {
                        val home = ShellEnvironment.homeDir(context.filesDir)
                        ShellEnvironment.setupStorageDirectory(home)
                        Toast.makeText(context, context.getString(R.string.storage_permission_granted), Toast.LENGTH_SHORT).show()
                    } else {
                        (context as? MainActivity)?.requestStoragePermissions()
                    }
                }) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = stringResource(R.string.terminal_storage_title)
                    )
                }
                IconButton(onClick = {
                    val textToCopy = activeSelection ?: viewModel.transcriptText()
                    copyText(context, textToCopy)
                }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.terminal_copy)
                    )
                }
                IconButton(onClick = { pasteFromClipboard(context, viewModel) }) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.terminal_paste)
                    )
                }
                IconButton(onClick = { viewModel.installUserland() }) {
                    Icon(
                        Icons.Default.GetApp,
                        contentDescription = stringResource(R.string.terminal_install_userland)
                    )
                }
                IconButton(onClick = { viewModel.restart() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.terminal_restart)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF1E1E1E),
                titleContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )
        TerminalEmulatorView(
            snapshot = snapshot,
            fontSizeSp = fontSize,
            fontFamily = fontFamily,
            theme = terminalTheme,
            onInput = { viewModel.send(it) },
            onResize = { cols, rows -> viewModel.resize(cols, rows) },
            onFontScale = { viewModel.setFontSize(it) },
            onPaste = { pasteFromClipboard(context, viewModel) },
            onCopyText = { copyText(context, it) },
            cursorSequence = { viewModel.cursorKey(it) },
            bellTrigger = bellTrigger,
            onSelectionChanged = { activeSelection = it },
            onUrlClick = { openTerminalUrl(context, it) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        TerminalExtraKeys(
            ctrlLatched = ctrl,
            altLatched = alt,
            onCtrl = { viewModel.toggleCtrl() },
            onAlt = { viewModel.toggleAlt() },
            onKey = { viewModel.sendKey(it) },
            cursorSequence = { viewModel.cursorKey(it) },
            customMacros = customMacros
        )
    }
}

private fun copyText(context: Context, text: String) {
    if (text.isBlank()) {
        Toast.makeText(context, context.getString(R.string.terminal_copy_empty), Toast.LENGTH_SHORT).show()
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))
    Toast.makeText(context, context.getString(R.string.terminal_copied), Toast.LENGTH_SHORT).show()
}

private fun pasteFromClipboard(context: Context, viewModel: TerminalViewModel) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    if (text.isNullOrEmpty()) {
        Toast.makeText(context, context.getString(R.string.terminal_clipboard_empty), Toast.LENGTH_SHORT).show()
        return
    }
    viewModel.send(viewModel.wrapPaste(text))
}
