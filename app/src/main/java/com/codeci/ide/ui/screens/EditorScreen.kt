package com.codeci.ide.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.R
import com.codeci.ide.ui.components.SymbolBar
import com.codeci.ide.ui.components.TerminalOutput
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.ThemeManager
import com.codeci.ide.ui.theme.getEditorTheme
import com.codeci.ide.ui.utils.CSyntaxVisualTransformation
import com.codeci.ide.ui.viewmodels.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    fileName: String? = null,
    onNavigateBack: () -> Unit = {},
    onFileRenamed: (String) -> Unit = {},
    viewModel: EditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager(context) }
    val settingsManager = remember { SettingsManager(context) }
    val currentEditorTheme by themeManager.editorThemeFlow.collectAsState(initial = EditorThemeType.DRACULA)
    val editorColors = getEditorTheme(currentEditorTheme)

    val fontSize by settingsManager.fontSizeFlow.collectAsState(initial = 14f)
    val fontFamilyName by settingsManager.fontFamilyFlow.collectAsState(initial = "Monospace")
    val tabSize by settingsManager.tabSizeFlow.collectAsState(initial = 4)
    val showLineNumbers by settingsManager.lineNumbersFlow.collectAsState(initial = true)
    val wordWrap by settingsManager.wordWrapFlow.collectAsState(initial = false)
    val autoIndent by settingsManager.autoIndentFlow.collectAsState(initial = true)

    val editorFont = when (fontFamilyName) {
        "Courier" -> FontFamily.Monospace
        "Sans Serif" -> FontFamily.SansSerif
        "Serif" -> FontFamily.Serif
        else -> FontFamily.Monospace
    }

    LaunchedEffect(fileName) {
        if (fileName != null) {
            viewModel.loadFile(context, fileName)
            settingsManager.addRecentFile(fileName)
        }
    }

    val codeText by viewModel.codeText.collectAsState()
    val currentFileName by viewModel.fileName.collectAsState()
    val terminalSegments by viewModel.terminalSegments.collectAsState()
    val isTerminalExpanded by viewModel.isTerminalExpanded.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val isRenaming by viewModel.isRenaming.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.coming_soon)
    fun showComingSoon() {
        scope.launch { snackbarHostState.showSnackbar(comingSoon) }
    }

    LaunchedEffect(userMessage) {
        val message = userMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    BackHandler(enabled = isDirty) {
        showUnsavedDialog = true
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(currentFileName) }
        AlertDialog(
            onDismissRequest = { if (!isRenaming) showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_file)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.file_name)) },
                    singleLine = true,
                    enabled = !isRenaming
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isRenaming,
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.updateFileName(context, newName) { renamed ->
                                onFileRenamed(renamed)
                                showRenameDialog = false
                            }
                        }
                    }
                ) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = {
                TextButton(enabled = !isRenaming, onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveFile(context)
                    showUnsavedDialog = false
                    onNavigateBack()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onNavigateBack()
                }) { Text(stringResource(R.string.discard)) }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = currentFileName + if (isDirty) " *" else "",
                        modifier = Modifier.clickable { showRenameDialog = true },
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDirty) showUnsavedDialog = true else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.runCode(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.run), modifier = Modifier.padding(end = 4.dp))
                        Text(stringResource(R.string.run))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showComingSoon() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.undo),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(onClick = { showComingSoon() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.redo),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(onClick = { showComingSoon() }) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = stringResource(R.string.format),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(onClick = { showComingSoon() }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.find),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(onClick = {
                    if (viewModel.saveFile(context)) {
                        Toast.makeText(context, context.getString(R.string.file_saved), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.file_save_failed), Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save)) }
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.coming_soon), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) },
                            onClick = {
                                showMoreMenu = false
                                showComingSoon()
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(editorColors.background)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .then(if (!wordWrap) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                        .padding(vertical = 8.dp)
                ) {
                    if (showLineNumbers) {
                        val lineCount = codeText.text.count { it == '\n' } + 1
                        val lineNumbers = (1..lineCount).joinToString("\n")
                        Text(
                            text = lineNumbers,
                            style = TextStyle(
                                fontFamily = editorFont,
                                fontSize = fontSize.sp,
                                color = Color(0xFF858585),
                                textAlign = TextAlign.End
                            ),
                            modifier = Modifier
                                .width(40.dp)
                                .padding(end = 8.dp)
                        )
                    }
                    BasicTextField(
                        value = codeText,
                        onValueChange = { viewModel.updateCode(it, autoIndent = autoIndent, tabSize = tabSize) },
                        textStyle = TextStyle(
                            fontFamily = editorFont,
                            fontSize = fontSize.sp,
                            color = editorColors.text
                        ),
                        visualTransformation = CSyntaxVisualTransformation(currentEditorTheme),
                        cursorBrush = SolidColor(editorColors.text),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SymbolBar(
                textFieldValue = codeText,
                onValueChange = { viewModel.updateCode(it, autoIndent = autoIndent, tabSize = tabSize) },
                tabSize = tabSize,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            )

            AnimatedVisibility(visible = isTerminalExpanded) {
                TerminalOutput(
                    segments = terminalSegments,
                    onClear = { viewModel.clearTerminal() },
                    onToggleExpand = { viewModel.toggleTerminal() },
                    isExpanded = isTerminalExpanded,
                    modifier = Modifier.height(200.dp)
                )
            }
            if (!isTerminalExpanded) {
                TerminalOutput(
                    segments = terminalSegments.takeLast(1),
                    onClear = { viewModel.clearTerminal() },
                    onToggleExpand = { viewModel.toggleTerminal() },
                    isExpanded = isTerminalExpanded,
                    modifier = Modifier.height(64.dp)
                )
            }
        }

        if (isRenaming) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    LaunchedEffect(Unit) {
        // keep comingSoon referenced for snackbar from disabled taps via clickable overlay if needed
        comingSoon
    }
}
