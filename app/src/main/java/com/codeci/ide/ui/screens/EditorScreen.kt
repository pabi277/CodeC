package com.codeci.ide.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.R
import com.codeci.ide.ui.components.EditorStatusBar
import com.codeci.ide.ui.components.EditorTabBar
import com.codeci.ide.ui.components.EditorTabUi
import com.codeci.ide.ui.components.FindReplaceBar
import com.codeci.ide.ui.components.SymbolBar
import com.codeci.ide.ui.components.TerminalOutput
import com.codeci.ide.ui.editor.CompilerDiagnostics
import com.codeci.ide.ui.editor.DiagnosticSeverity
import com.codeci.ide.ui.editor.EditorDiagnostic
import com.codeci.ide.ui.projects.ProjectInfo
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.ThemeManager
import com.codeci.ide.ui.theme.getEditorTheme
import com.codeci.ide.ui.terminal.TerminalHandoff
import com.codeci.ide.ui.utils.CSyntaxVisualTransformation
import com.codeci.ide.ui.utils.EditorDecorations
import com.codeci.ide.ui.utils.WebFileSupport
import com.codeci.ide.ui.viewmodels.EditorViewModel
import kotlin.math.roundToInt

/** Tap-anchor for the inline diagnostic tooltip. */
internal data class EditorPopupAnchor(val x: Float, val y: Float, val diagnostic: EditorDiagnostic)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    projectName: String? = null,
    fileName: String? = null,
    onNavigateBack: () -> Unit = {},
    onFileRenamed: (String) -> Unit = {},
    onProjectSelected: (ProjectInfo) -> Unit = {},
    onOpenInTerminal: (String?) -> Unit = {},
    onOpenPreview: (String) -> Unit = {},
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

    LaunchedEffect(projectName, fileName) {
        if (projectName != null && fileName != null) {
            viewModel.openFile(context, projectName, fileName)
            ProjectManager(context).project(projectName)?.let(onProjectSelected)
            settingsManager.addRecentFile(fileName)
        } else if (fileName != null) {
            viewModel.openFile(context, null, fileName)
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
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val findState by viewModel.find.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val currentLineRange by viewModel.currentLineRange.collectAsState()
    val bracketRanges by viewModel.bracketRanges.collectAsState()
    val cursorPos by viewModel.cursorPos.collectAsState()
    val isFormatting by viewModel.formatting.collectAsState()
    val openTabs by viewModel.openTabs.collectAsState()
    val activeTabPath by viewModel.activeTabPath.collectAsState()

    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showFilesSheet by remember { mutableStateOf(false) }
    var showSaveToProject by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var pendingCloseTab by remember { mutableStateOf<String?>(null) }
    var popup by remember { mutableStateOf<EditorPopupAnchor?>(null) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isWebProject = remember(projectName) {
        projectName?.let { name ->
            ProjectManager(context).project(name)?.config?.type?.equals("web", ignoreCase = true)
        } == true
    }

    fun projectRunCommandOrNull(): String? {
        val project = projectName ?: return null
        if (!viewModel.saveFile(context)) return null
        val info = ProjectManager(context).project(project) ?: return null
        if (info.config.type.equals("web", ignoreCase = true)) return null
        return TerminalHandoff.projectRunCommand(info.root.absolutePath, info.config)
    }

    fun webDefaultEntryOrNull(): String? {
        val project = projectName ?: return null
        if (!viewModel.saveFile(context)) return null
        val info = ProjectManager(context).project(project) ?: return null
        if (!info.config.type.equals("web", ignoreCase = true)) return null
        val entry = ProjectPathUtils.sanitizeRelativePath(info.config.entry) ?: return null
        val target = ProjectPathUtils.resolveInside(info.root, entry) ?: return null
        return entry.takeIf { target.isFile && WebFileSupport.isHtml(target.name) }
    }

    LaunchedEffect(userMessage) {
        val message = userMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    // A stale popup points at a moved line; drop it whenever the buffer text changes.
    LaunchedEffect(codeText.text) { popup = null }

    val decorations = remember(
        currentLineRange, bracketRanges, diagnostics,
        findState.matches, findState.activeIndex
    ) {
        EditorDecorations(
            currentLineRange = currentLineRange,
            findMatches = findState.matches,
            activeFindMatch = findState.matches.getOrNull(findState.activeIndex),
            bracketRanges = bracketRanges,
            diagnostics = diagnostics
        )
    }
    val transformation = remember(currentEditorTheme, decorations) {
        CSyntaxVisualTransformation(currentEditorTheme, decorations)
    }

    val tabViews = remember(openTabs, activeTabPath, codeText, isDirty) {
        openTabs.map { tab ->
            val dirty = if (tab.relativePath == activeTabPath) {
                isDirty
            } else {
                tab.buffer.text != tab.savedText
            }
            EditorTabUi(tab.relativePath, tab.displayName, dirty)
        }
    }

    val latestDiagnostics by rememberUpdatedState(diagnostics)

    BackHandler(enabled = isDirty) {
        showUnsavedDialog = true
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(currentFileName.substringAfterLast('/')) }
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

    pendingCloseTab?.let { closingPath ->
        AlertDialog(
            onDismissRequest = { pendingCloseTab = null },
            title = { Text(stringResource(R.string.close_tab)) },
            text = {
                Text(
                    stringResource(
                        R.string.close_tab_unsaved_message,
                        closingPath.substringAfterLast('/')
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.closeTab(context, closingPath, saveFirst = true)
                    pendingCloseTab = null
                }) { Text(stringResource(R.string.save_and_close)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.closeTab(context, closingPath, saveFirst = false)
                        pendingCloseTab = null
                    }) { Text(stringResource(R.string.discard)) }
                    TextButton(onClick = { pendingCloseTab = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = { Text(stringResource(R.string.diagnostics)) },
            text = {
                if (diagnostics.isEmpty()) {
                    Text(stringResource(R.string.no_diagnostics))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        diagnostics.forEach { diagnostic ->
                            val label = if (diagnostic.severity == DiagnosticSeverity.ERROR) {
                                stringResource(R.string.diagnostic_error)
                            } else {
                                stringResource(R.string.diagnostic_warning)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.jumpToDiagnostic(diagnostic)
                                        showDiagnosticsDialog = false
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$label · L${diagnostic.line}:${diagnostic.column}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (diagnostic.severity == DiagnosticSeverity.ERROR) {
                                        Color(0xFFFF5555)
                                    } else {
                                        Color(0xFFFFB347)
                                    }
                                )
                                Text(
                                    text = diagnostic.message,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (CompilerDiagnostics.semicolonFixLabel(diagnostic) != null) {
                                    TextButton(
                                        onClick = {
                                            viewModel.applyQuickFix(diagnostic)
                                        },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 0.dp,
                                            vertical = 0.dp
                                        )
                                    ) {
                                        Text(
                                            stringResource(R.string.fix_add_semicolon),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        enabled = diagnostics.isNotEmpty(),
                        onClick = { viewModel.clearDiagnostics() }
                    ) { Text(stringResource(R.string.clear_action)) }
                    TextButton(onClick = { showDiagnosticsDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = currentFileName.substringAfterLast('/') + if (isDirty) " *" else "",
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
                    if (WebFileSupport.isHtml(currentFileName)) {
                        IconButton(onClick = {
                            if (viewModel.saveFile(context)) {
                                onOpenPreview(viewModel.fileName.value)
                            } else {
                                Toast.makeText(context, context.getString(R.string.file_save_failed), Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.preview)
                            )
                        }
                    }
                    if (!isWebProject) {
                        IconButton(onClick = {
                            val command = projectRunCommandOrNull()
                                ?: viewModel.saveAndAbsolutePath(context)?.let(TerminalHandoff::compileAndRunCommand)
                            if (command != null) {
                                onOpenInTerminal(command)
                            } else {
                                Toast.makeText(context, context.getString(R.string.file_save_failed), Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = stringResource(R.string.run_in_terminal)
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (isWebProject) {
                                val entry = webDefaultEntryOrNull()
                                if (entry != null) {
                                    onOpenPreview(entry)
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.default_run_page_missing),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else if (projectName != null) {
                                val command = projectRunCommandOrNull()
                                if (command != null) {
                                    onOpenInTerminal(command)
                                } else {
                                    Toast.makeText(context, context.getString(R.string.file_save_failed), Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                viewModel.runCode(context)
                            }
                        },
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

            if (projectName != null) {
                Text(
                    text = projectName + "  >  " + currentFileName.replace("/", "  >  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            EditorTabBar(
                tabs = tabViews,
                activePath = activeTabPath,
                onSelect = { path -> viewModel.selectTab(path) },
                onClose = { path ->
                    val dirty = if (path == activeTabPath) {
                        isDirty
                    } else {
                        openTabs.firstOrNull { it.relativePath == path }
                            ?.let { it.buffer.text != it.savedText } == true
                    }
                    if (dirty) {
                        pendingCloseTab = path
                    } else {
                        viewModel.closeTab(context, path, saveFirst = false)
                    }
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(top = 2.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.undo() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.undo),
                        tint = if (canUndo) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
                IconButton(onClick = { viewModel.redo() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.redo),
                        tint = if (canRedo) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
                IconButton(onClick = { showFilesSheet = true }) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = stringResource(R.string.project_files),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { viewModel.formatCode(context, tabSize) }) {
                    if (isFormatting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(10.dp)
                                .width(18.dp)
                                .height(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = stringResource(R.string.format),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(onClick = {
                    if (findState.visible) viewModel.hideFind() else viewModel.showFind()
                }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.find),
                        tint = if (findState.visible) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
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
                            text = { Text(stringResource(R.string.save_all)) },
                            onClick = {
                                showMoreMenu = false
                                viewModel.saveAllTabs(context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reload_from_disk)) },
                            onClick = {
                                showMoreMenu = false
                                viewModel.reloadActiveTab(context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.project_files)) },
                            onClick = {
                                showMoreMenu = false
                                showFilesSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_to_project)) },
                            onClick = {
                                showMoreMenu = false
                                showSaveToProject = true
                            }
                        )
                        DropdownMenuItem(
                            enabled = diagnostics.isNotEmpty(),
                            text = {
                                Text(
                                    stringResource(R.string.clear_diagnostics),
                                    color = if (diagnostics.isEmpty()) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                viewModel.clearDiagnostics()
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = findState.visible) {
                FindReplaceBar(
                    state = findState,
                    onQueryChange = { viewModel.setFindQuery(it) },
                    onReplacementChange = { viewModel.setFindReplacement(it) },
                    onToggleCase = { viewModel.toggleFindMatchCase() },
                    onToggleWord = { viewModel.toggleFindWholeWord() },
                    onToggleRegex = { viewModel.toggleFindRegex() },
                    onNext = { viewModel.findNext() },
                    onPrev = { viewModel.findPrev() },
                    onReplace = { viewModel.replaceCurrent() },
                    onReplaceAll = { viewModel.replaceAll(context) },
                    onClose = { viewModel.hideFind() },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                )
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
                        visualTransformation = transformation,
                        cursorBrush = SolidColor(editorColors.text),
                        onTextLayout = { result -> textLayoutResult = result },
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInputDiagnosticsTap(
                                layoutResult = { textLayoutResult },
                                diagnosticsProvider = { latestDiagnostics },
                                hasDiagnostics = diagnostics.isNotEmpty()
                            ) { position, diagnostic ->
                                popup = EditorPopupAnchor(position.x, position.y, diagnostic)
                            }
                    )
                }

                if (isFormatting) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                popup?.let { anchor ->
                    val density = LocalDensity.current
                    val config = LocalConfiguration.current
                    val (x, y) = with(density) {
                        val maxX = config.screenWidthDp.dp.toPx() - 280.dp.toPx()
                        anchor.x.coerceAtMost(maxX).coerceAtLeast(0f) to
                            (anchor.y - 12.dp.toPx()).coerceAtLeast(0f)
                    }
                    Surface(
                        tonalElevation = 4.dp,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                            .padding(8.dp)
                            .width(260.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "L${anchor.diagnostic.line}:${anchor.diagnostic.column} · ${anchor.diagnostic.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (anchor.diagnostic.severity == DiagnosticSeverity.ERROR) {
                                    Color(0xFFFF5555)
                                } else {
                                    Color(0xFFFFB347)
                                }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CompilerDiagnostics.semicolonFixLabel(anchor.diagnostic)?.let {
                                    TextButton(onClick = {
                                        viewModel.applyQuickFix(anchor.diagnostic)
                                        popup = null
                                    }) {
                                        Text(
                                            stringResource(R.string.fix_add_semicolon),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                TextButton(onClick = {
                                    viewModel.jumpToDiagnostic(anchor.diagnostic)
                                    popup = null
                                }) {
                                    Text(
                                        stringResource(R.string.jump_to_line),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                TextButton(onClick = { popup = null }) {
                                    Text(
                                        stringResource(R.string.cancel),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            EditorStatusBar(
                line = cursorPos.line,
                column = cursorPos.column,
                selectionLength = cursorPos.selectionLength,
                tabSize = tabSize,
                errorCount = diagnostics.count { it.severity == DiagnosticSeverity.ERROR },
                warningCount = diagnostics.count { it.severity == DiagnosticSeverity.WARNING },
                onDiagnosticsClick = { showDiagnosticsDialog = true }
            )

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

        // Phase 9.1: Spck-style file drawer. Lists the open project's tree (or
        // the scratch folder) and opens the tapped file as a tab — switching
        // files no longer means leaving the editor for the Projects screen.
        if (showFilesSheet) {
            val fileEntries by viewModel.fileEntries.collectAsState()
            LaunchedEffect(Unit) { viewModel.refreshFileEntries(context) }
            ModalBottomSheet(onDismissRequest = { showFilesSheet = false }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = if (projectName != null) "Files — $projectName"
                               else "Files — Scratch (not in a project)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (projectName != null) "Tap a file to open it in a tab."
                               else "Scratch files live in CodeC/projects. Use “Save to project…” " +
                                    "to move the current file into a real project folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    if (fileEntries.isEmpty()) {
                        Text(
                            "Nothing on disk yet.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    fileEntries.forEach { entry ->
                        val active = entry.projectName == projectName &&
                            entry.relativePath == activeTabPath
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !entry.isDirectory) {
                                    if (!entry.isDirectory) {
                                        viewModel.openFile(context, entry.projectName, entry.relativePath)
                                        showFilesSheet = false
                                    }
                                }
                                .padding(
                                    start = (16 + entry.depth * 18).dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 8.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (entry.isDirectory) "▸ ${entry.name}" else entry.name,
                                style = if (entry.isDirectory) {
                                    MaterialTheme.typography.labelLarge
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                color = when {
                                    entry.isDirectory || active -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (!entry.isDirectory && active && isDirty) {
                                Text("●", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Phase 9.1: save the current buffer into a real project folder —
        // scratch saves land outside every project, which is why the terminal
        // could not find main.c next to portfolio-system3.
        if (showSaveToProject) {
            val projectList = remember {
                runCatching { ProjectManager(context).listProjects() }.getOrDefault(emptyList())
            }
            AlertDialog(
                onDismissRequest = { showSaveToProject = false },
                title = { Text(stringResource(R.string.save_to_project)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "The current file is written into the chosen project's folder and the " +
                                "editor switches to that project, so the terminal and Run can find it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        if (projectList.isEmpty()) {
                            Text(
                                "No projects yet — create one in the Projects tab first.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            projectList.forEach { project ->
                                TextButton(
                                    onClick = {
                                        showSaveToProject = false
                                        viewModel.saveToProject(context, project.name)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSaveToProject = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

/**
 * Phase 9 — tap-to-inspect diagnostics. Follows the Compose custom-text-link
 * pattern: track the gesture WITHOUT consuming it (so the text field still
 * places the caret normally), and only consume when a diagnostic line was
 * actually hit. [diagnosticsProvider] is read at gesture time (backed by
 * rememberUpdatedState) so the pointer input never restarts on every keystroke.
 */
internal fun Modifier.pointerInputDiagnosticsTap(
    layoutResult: () -> TextLayoutResult?,
    diagnosticsProvider: () -> List<EditorDiagnostic>,
    hasDiagnostics: Boolean,
    onDiagnosticHit: (Offset, EditorDiagnostic) -> Unit
): Modifier = pointerInput(hasDiagnostics) {
    if (!hasDiagnostics) return@pointerInput
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
        val layout = layoutResult() ?: return@awaitEachGesture
        val text = layout.layoutInput.text.text
        val offset = runCatching { layout.getOffsetForPosition(up.position) }.getOrDefault(-1)
        if (offset < 0 || offset > text.length) return@awaitEachGesture
        val line = text.take(offset).count { it == '\n' } + 1
        val diagnostic = diagnosticsProvider().firstOrNull { it.line == line } ?: return@awaitEachGesture
        up.consume()
        onDiagnosticHit(up.position, diagnostic)
    }
}
