package com.codeci.ide.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
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
import com.codeci.ide.ui.components.EditorKeysRow
import com.codeci.ide.ui.components.EditorProjectDrawer
import com.codeci.ide.ui.components.OutputPanelView
import com.codeci.ide.ui.editor.CodeCompletionEngine
import com.codeci.ide.ui.editor.CompletionItem
import com.codeci.ide.ui.editor.CompilerDiagnostics
import com.codeci.ide.ui.editor.DiagnosticSeverity
import com.codeci.ide.ui.editor.EditorDiagnostic
import com.codeci.ide.ui.editor.EditorShellUi
import com.codeci.ide.ui.editor.FileTreeCollapse
import com.codeci.ide.ui.editor.FontSizeZoom
import com.codeci.ide.ui.projects.ProjectInfo
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.ThemeManager
import com.codeci.ide.ui.theme.getEditorTheme
import com.codeci.ide.ui.terminal.TerminalHandoff
import com.codeci.ide.ui.utils.EditorDecorations
import com.codeci.ide.ui.utils.FileManager
import com.codeci.ide.ui.utils.LanguageType
import com.codeci.ide.ui.utils.SyntaxVisualTransformation
import com.codeci.ide.ui.utils.WebFileSupport
import com.codeci.ide.ui.viewmodels.EditorFileEntry
import com.codeci.ide.ui.viewmodels.EditorViewModel
import java.io.File
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Mockup-exact RUN affordance green (Spck's run action color). */
private val RunGreen = Color(0xFF3DDC84)

/** Tap-anchor for the inline diagnostic tooltip. */
internal data class EditorPopupAnchor(val x: Float, val y: Float, val diagnostic: EditorDiagnostic)

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    projectName: String? = null,
    fileName: String? = null,
    onNavigateBack: () -> Unit = {},
    onFileRenamed: (String) -> Unit = {},
    onProjectSelected: (ProjectInfo) -> Unit = {},
    onOpenInTerminal: (String?) -> Unit = {},
    /**
     * Opens the static Web Preview. The project travels with the file because
     * the Nav route argument can be stale after an in-editor folder switch
     * (Phase 9.2) — the caller supplies the file's real project instead.
     */
    onOpenPreview: (projectName: String?, fileName: String) -> Unit = { _, _ -> },
    /** Phase 14 — open Web Preview on a live server URL detected by RUN ▶. */
    onOpenPreviewUrl: (projectName: String?, url: String) -> Unit = { _, _ -> },
    /** Phase 16 — the drawer footer jumps to the app Settings screen. */
    onOpenSettings: () -> Unit = {},
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
    val outputState by viewModel.outputState.collectAsState()
    val outputExpanded by viewModel.outputExpanded.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val isRenaming by viewModel.isRenaming.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    // Phase 21.2 — toolchain auto-install gate.
    val installPrompt by viewModel.installPrompt.collectAsState()
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
    val currentProject by viewModel.projectName.collectAsState()
    // Phase 16 — drawer + shell state.
    val collapsedDirs by viewModel.collapsedDirs.collectAsState()
    val gitBranch by viewModel.gitBranch.collectAsState()
    val gitBadges by viewModel.gitBadges.collectAsState()
    val gitChangeCount by viewModel.gitChangeCount.collectAsState()
    val launchDefault by viewModel.launchDefault.collectAsState()
    val activeLineEnding by viewModel.activeLineEnding.collectAsState()
    val fileEntries by viewModel.fileEntries.collectAsState()
    val customSnippets by settingsManager.editorCustomSnippetsFlow.collectAsState(initial = "")

    // Phase 22.2 — "is the soft keyboard up?". WindowInsets.ime animates, so
    // the bottom inset is > 0 for the whole show/hide animation; that is
    // exactly the window during which the keys row must ride the keyboard.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val uiScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val fontSizeState = rememberUpdatedState(fontSize)

    val visibleEntries = remember(fileEntries, collapsedDirs) {
        FileTreeCollapse.visible(fileEntries, collapsedDirs)
    }
    val allCollapsed = remember(fileEntries, collapsedDirs) {
        val dirs = FileTreeCollapse.allDirs(fileEntries)
        dirs.isNotEmpty() && collapsedDirs.containsAll(dirs)
    }

    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSaveToProject by remember { mutableStateOf(false) }
    var showContextPicker by remember { mutableStateOf(false) }
    // Phase 16 — drawer dialogs: create entry (parent, isFolder), per-row rename,
    // delete confirm, go-to-line and the git sheet for the footer row.
    var pendingCreate by remember { mutableStateOf<Pair<String?, Boolean>?>(null) }
    var entryName by remember { mutableStateOf("") }
    var pendingRenameEntry by remember { mutableStateOf<EditorFileEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<EditorFileEntry?>(null) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var goToLineText by remember { mutableStateOf("") }
    var gitSheetRoot by remember { mutableStateOf<File?>(null) }
    // Phase 17 — Switch Branch, opened from the drawer footer.
    var gitBranchSheetRoot by remember { mutableStateOf<File?>(null) }
    var keysRowVisible by remember { mutableStateOf(true) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var pendingCloseTab by remember { mutableStateOf<String?>(null) }
    var popup by remember { mutableStateOf<EditorPopupAnchor?>(null) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Phase 12 — language-aware editing: the active file's extension selects
    // the syntax highlighter and the autocomplete suggestions. The popup
    // recomputes on every buffer/selection change, resets its selection on
    // new suggestions, and ESC dismisses it until the next edit.
    val language = remember(activeTabPath, currentFileName) {
        LanguageType.fromFileName(activeTabPath ?: currentFileName)
    }
    // Phase 22.1 — the completion list is derived, not remembered per
    // keystroke: derivedStateOf recomputes only when the values it actually
    // reads change, and only when something is reading `completionItems`.
    val completionItems by remember(language) {
        derivedStateOf {
            val sel = codeText.selection
            CodeCompletionEngine.completions(
                codeText.text,
                sel.end.coerceAtLeast(sel.start),
                language
            )
        }
    }
    var completionDismissed by remember(codeText.text) { mutableStateOf(false) }
    var completionIndex by remember(completionItems) { mutableStateOf(0) }
    val showCompletion = completionItems.isNotEmpty() && !completionDismissed

    fun insertCompletion(item: CompletionItem) {
        val sel = codeText.selection
        val cursor = sel.end.coerceAtLeast(sel.start)
        val start = CodeCompletionEngine.prefixStart(codeText.text, cursor)
        val newText = codeText.text.substring(0, start) + item.insertText +
            codeText.text.substring(cursor)
        val caret = start + item.insertText.length
        viewModel.updateCode(TextFieldValue(newText, selection = TextRange(caret)))
    }

    val isWebProject = remember(currentProject) {
        currentProject?.let { name ->
            ProjectManager(context).project(name)?.config?.type?.equals("web", ignoreCase = true)
        } == true
    }

    fun projectRunCommandOrNull(): String? {
        val project = currentProject ?: return null
        if (!viewModel.saveFile(context)) return null
        val info = ProjectManager(context).project(project) ?: return null
        if (info.config.type.equals("web", ignoreCase = true)) return null
        return TerminalHandoff.projectRunCommand(info.root.absolutePath, info.config)
    }

    // Phase 16 — the preview/Launch target: Spck's "Launch default" wins over
    // the config entry; a `web` project still falls back to its entry, and RUN
    // ▶ keeps its Phase 11/14 behavior (this only refines WHICH page opens).
    fun webDefaultEntryOrNull(): String? {
        val project = currentProject ?: return null
        if (!viewModel.saveFile(context)) return null
        val info = ProjectManager(context).project(project) ?: return null
        val isWeb = info.config.type.equals("web", ignoreCase = true)
        val candidate = launchDefault ?: info.config.entry.takeIf { isWeb } ?: return null
        val entry = ProjectPathUtils.sanitizeRelativePath(candidate) ?: return null
        val target = ProjectPathUtils.resolveInside(info.root, entry) ?: return null
        return entry.takeIf { target.isFile && WebFileSupport.isHtml(target.name) }
    }

    /** The file the 👁 button previews: the active HTML file, else the project default. */
    fun previewEntryOrNull(): String? {
        val name = viewModel.fileName.value
        return if (WebFileSupport.isHtml(name)) {
            if (viewModel.saveFile(context)) name else null
        } else {
            webDefaultEntryOrNull()
        }
    }
    LaunchedEffect(userMessage) {
        val message = userMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    // Auto-save: leaving the editor (tab switch, deep link, back) saves any
    // unsaved buffer immediately instead of waiting for the debounce.
    DisposableEffect(Unit) {
        onDispose { viewModel.flushAutoSave() }
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
    // Phase 22.1 — the O(n) tokenizer runs debounced on Dispatchers.Default in
    // the VM; the transformation reuses that snapshot and only layers the
    // cheap decoration spans (current line, brackets, find, diagnostics) on
    // the main thread. A stale snapshot just means one inline highlight pass,
    // never wrong colors.
    val highlighted by viewModel.highlighted.collectAsState()
    LaunchedEffect(currentEditorTheme, language) {
        viewModel.setHighlightContext(currentEditorTheme, language)
    }
    val transformation = remember(currentEditorTheme, decorations, language, highlighted) {
        SyntaxVisualTransformation(currentEditorTheme, decorations, language, highlighted)
    }

    // Phase 22.1 — narrowed keys: the tab strip only depends on the tab list,
    // which tab is active, and the active tab's dirty flag. Keying it on the
    // live buffer rebuilt every tab model on every keystroke.
    val tabViews = remember(openTabs, activeTabPath, isDirty) {
        openTabs.map { tab ->
            if (tab.relativePath == activeTabPath) {
                // The active tab's truth is the live buffer + VM dirtiness.
                EditorTabUi(tab.relativePath, tab.displayName, isDirty)
            } else {
                EditorShellUi.tabModel(tab.relativePath, tab.buffer.text, tab.savedText)
            }
        }
    }

    val latestDiagnostics by rememberUpdatedState(diagnostics)

    // Phase 14 — when RUN ▶ detects a server's bind line, open the Web Preview
    // on that URL. rememberUpdatedState keeps the callback fresh without
    // restarting the effect, and the handler survives navigation. Auto
    // projects detected as static web (index.html) use the same preview
    // navigation as `web` projects.
    val openPreviewUrlState = rememberUpdatedState(onOpenPreviewUrl)
    val openPreviewState = rememberUpdatedState(onOpenPreview)
    LaunchedEffect(Unit) {
        viewModel.setServerReadyHandler { project, url -> openPreviewUrlState.value(project, url) }
        viewModel.setWebPreviewHandler { project, entry -> openPreviewState.value(project, entry) }
    }

    // Phase 16 — the drawer tree + git meta refresh on open (and the tree once
    // per folder switch so the launch-default marker is live even when closed).
    LaunchedEffect(currentProject) {
        viewModel.refreshFileEntries(context)
    }
    LaunchedEffect(drawerState.currentValue, currentProject) {
        if (drawerState.currentValue == DrawerValue.Open) {
            viewModel.refreshFileEntries(context)
            viewModel.refreshGitMeta(context)
        }
    }

    BackHandler(enabled = isDirty) {
        showUnsavedDialog = true
    }

    // Phase 21.2 — RUN ▶ on a file whose toolchain package is missing asks
    // before downloading anything; Install streams `pkg install -y <pkg>` into
    // the Output Panel and then continues the run automatically.
    installPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissInstall() },
            title = { Text(stringResource(R.string.install_prompt_title, prompt.displayName)) },
            text = {
                Column {
                    Text(stringResource(R.string.install_prompt_body, prompt.packageName))
                    prompt.sizeHint?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.install_prompt_size, it),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmInstall(context) }) {
                    Text(stringResource(R.string.install_prompt_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissInstall() }) {
                    Text(stringResource(R.string.install_prompt_cancel))
                }
            }
        )
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
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                EditorProjectDrawer(
                    projectName = currentProject,
                    branch = gitBranch,
                    changeCount = gitChangeCount,
                    entries = visibleEntries,
                    collapsedDirs = collapsedDirs,
                    selectedPath = activeTabPath ?: currentFileName,
                    launchDefault = launchDefault,
                    gitBadges = gitBadges,
                    allCollapsed = allCollapsed,
                    onSwitchProject = {
                        uiScope.launch { drawerState.close() }
                        showContextPicker = true
                    },
                    onSourceControl = {
                        val root = currentProject?.let {
                            runCatching { ProjectManager(context).project(it)?.root }.getOrNull()
                        }
                        if (root != null) gitSheetRoot = root else Toast.makeText(
                            context,
                            context.getString(R.string.editor_scratch_mode),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onSwitchBranch = {
                        val root = currentProject?.let {
                            runCatching { ProjectManager(context).project(it)?.root }.getOrNull()
                        }
                        if (root != null) gitBranchSheetRoot = root else Toast.makeText(
                            context,
                            context.getString(R.string.editor_scratch_mode),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onNewFile = { parent ->
                        entryName = "main.c"
                        pendingCreate = parent to false
                    },
                    onNewFolder = { parent ->
                        entryName = ""
                        pendingCreate = parent to true
                    },
                    onRefresh = {
                        viewModel.refreshFileEntries(context)
                        viewModel.refreshGitMeta(context)
                    },
                    onToggleCollapseAll = {
                        if (allCollapsed) viewModel.expandAllDirectories() else viewModel.collapseAllDirectories()
                    },
                    onOpenEntry = { entry ->
                        if (entry.isDirectory) {
                            viewModel.toggleDirectory(entry.relativePath)
                        } else {
                            viewModel.openFile(context, entry.projectName, entry.relativePath)
                            uiScope.launch { drawerState.close() }
                        }
                    },
                    onRenameEntry = { pendingRenameEntry = it },
                    onDeleteEntry = { pendingDelete = it },
                    onRunInTerminal = { entry ->
                        val rootDir = if (entry.projectName != null) {
                            runCatching { ProjectManager(context).project(entry.projectName)?.root }.getOrNull()
                        } else {
                            runCatching { FileManager(context).getProjectDir() }.getOrNull()
                        }
                        val command = if (rootDir == null) null else if (entry.projectName != null) {
                            TerminalHandoff.projectFileRunCommand(rootDir, entry.relativePath)
                        } else {
                            TerminalHandoff.compileAndRunCommand(
                                File(rootDir, entry.relativePath).absolutePath
                            )
                        }
                        if (command != null) {
                            uiScope.launch { drawerState.close() }
                            onOpenInTerminal(command)
                        }
                    },
                    onLaunchEntry = { entry ->
                        uiScope.launch { drawerState.close() }
                        onOpenPreview(entry.projectName, entry.relativePath)
                    },
                    onSetLaunchDefault = { viewModel.setLaunchDefault(context, it.relativePath) },
                    onClearLaunchDefault = { viewModel.setLaunchDefault(context, null) },
                    onCopyPath = { entry ->
                        val path = runCatching {
                            val root = entry.projectName?.let {
                                runCatching { ProjectManager(context).project(it)?.root }.getOrNull()
                            } ?: runCatching { FileManager(context).getProjectDir() }.getOrNull()
                            if (root != null) File(root, entry.relativePath).absolutePath else entry.relativePath
                        }.getOrDefault(entry.relativePath)
                        clipboard.setText(AnnotatedString(path))
                        Toast.makeText(context, R.string.path_copied, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        ) {
        // Phase 22.3 — the editor owns its IME inset. MainActivity already
        // opted into edge-to-edge (`enableEdgeToEdge()` =
        // `setDecorFitsSystemWindows(false)`), so the keyboard no longer
        // resizes the window by itself; `imePadding()` reserves exactly the
        // keyboard's height at the bottom of the editor column. That both
        // keeps the caret visible and lets the keys row (Phase 22.2) ride
        // directly on top of the keyboard. Only EditorScreen is padded —
        // Terminal/Settings keep their own inset handling.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            TopAppBar(
                title = {
                    if (tabViews.isEmpty()) {
                        Text(
                            text = currentFileName.substringAfterLast('/') + if (isDirty) " *" else "",
                            modifier = Modifier.clickable { showRenameDialog = true },
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
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
                            onCloseOthers = { path -> viewModel.closeOtherTabs(context, path) },
                            onCloseAll = { viewModel.closeAllTabs(context) },
                            onCopyPath = { path ->
                                clipboard.setText(AnnotatedString(path))
                                Toast.makeText(context, R.string.path_copied, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        uiScope.launch {
                            if (drawerState.currentValue == DrawerValue.Open) drawerState.close() else drawerState.open()
                        }
                    }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.project_files))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (findState.visible) viewModel.hideFind() else viewModel.showFind()
                    }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.find))
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            // Phase 16 mockup-exact: the former second toolbar
                            // row (undo/redo/save/format + keys toggle) now
                            // lives here; the top bar stays ☰ tabs 🔍  RUN.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.undo)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null) },
                                enabled = canUndo,
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.undo()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.redo)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null) },
                                enabled = canRedo,
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.redo()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (keysRowVisible) R.string.editor_hide_keys_row else R.string.editor_show_keys_row
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (keysRowVisible) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    keysRowVisible = !keysRowVisible
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save)) },
                                onClick = {
                                    showMoreMenu = false
                                    if (viewModel.saveFile(context)) {
                                        Toast.makeText(context, context.getString(R.string.file_saved), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.file_save_failed), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_all)) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.saveAllTabs(context)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename_file)) },
                                onClick = {
                                    showMoreMenu = false
                                    showRenameDialog = true
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
                                text = { Text(stringResource(R.string.format)) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.formatCode(context, tabSize)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.jump_to_line)) },
                                onClick = {
                                    showMoreMenu = false
                                    goToLineText = cursorPos.line.toString()
                                    showGoToLineDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.find)) },
                                onClick = {
                                    showMoreMenu = false
                                    if (findState.visible) viewModel.hideFind() else viewModel.showFind()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.diagnostics)) },
                                onClick = {
                                    showMoreMenu = false
                                    showDiagnosticsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.line_endings, activeLineEnding)) },
                                enabled = currentProject != null && activeTabPath != null,
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.toggleLineEnding(context)
                                }
                            )
                            if (currentProject != null) {
                                if (WebFileSupport.isHtml(currentFileName) && launchDefault != currentFileName) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.editor_drawer_set_default)) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.setLaunchDefault(context, viewModel.fileName.value)
                                        }
                                    )
                                }
                                if (launchDefault != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.editor_drawer_clear_default)) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.setLaunchDefault(context, null)
                                        }
                                    )
                                }
                            }
                            if (!isWebProject) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.run_in_terminal)) },
                                    onClick = {
                                        showMoreMenu = false
                                        val command = projectRunCommandOrNull()
                                            ?: viewModel.saveAndAbsolutePath(context)?.let(TerminalHandoff::compileAndRunCommand)
                                        if (command != null) {
                                            onOpenInTerminal(command)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.file_save_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_to_project)) },
                                onClick = {
                                    showMoreMenu = false
                                    showSaveToProject = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_file)) },
                                onClick = {
                                    showMoreMenu = false
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, currentFileName.substringAfterLast('/'))
                                        putExtra(Intent.EXTRA_TEXT, codeText.text)
                                    }
                                    runCatching {
                                        context.startActivity(
                                            Intent.createChooser(send, context.getString(R.string.share_file))
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.close_file)) },
                                onClick = {
                                    showMoreMenu = false
                                    val path = activeTabPath
                                    if (path != null) {
                                        val dirty = isDirty
                                        if (dirty) pendingCloseTab = path
                                        else viewModel.closeTab(context, path, saveFirst = false)
                                    }
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
                    // Mockup-exact RUN: green ▶ + green "RUN" text, no filled
                    // button chrome (Spck's run affordance).
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (WebFileSupport.isHtml(currentFileName)) {
                                    // 2026-08-31 — RUN ▶ IS the preview for
                                    // HTML files: save the buffer and open it.
                                    // No separate preview affordance.
                                    val entry = previewEntryOrNull()
                                    if (entry != null) {
                                        // The VM project is authoritative: the
                                        // Nav route's projectName can be stale
                                        // after an in-editor folder switch.
                                        onOpenPreview(currentProject, entry)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.file_save_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else if (isWebProject) {
                                    val entry = webDefaultEntryOrNull()
                                    if (entry != null) {
                                        onOpenPreview(currentProject, entry)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.default_run_page_missing),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    viewModel.runActiveFile(context)
                                }
                            }
                            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.run),
                            tint = RunGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            stringResource(R.string.run),
                            color = RunGreen,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Phase 16 mockup-exact: no second toolbar row — the top bar is
            // exactly ☰ + tabs + 🔍 +  + ▶ RUN. Undo/Redo and the keys-row
            // toggle moved into the ⋮ overflow (below).

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
            val hScrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(editorColors.background)
                    .pointerInput(Unit) {
                        // Phase 16 — pinch to zoom the editor font (Spck §2.3,
                        // the terminal's reactive pinch rebuilt on Compose
                        // pointer input). Two-finger only: taps keep placing
                        // the caret and the scroll views keep single-finger
                        // drags; once the pinch is real both pointers consume.
                        awaitEachGesture {
                            val first = awaitFirstDown(requireUnconsumed = false)
                            var secondId: PointerId? = null
                            while (true) {
                                val e = awaitPointerEvent()
                                val other = e.changes.firstOrNull { it.id != first.id && it.pressed }
                                if (other != null) {
                                    secondId = other.id
                                    break
                                }
                                val cur = e.changes.firstOrNull { it.id == first.id }
                                if (cur == null || !cur.pressed || cur.isConsumed) return@awaitEachGesture
                            }
                            val id2 = secondId ?: return@awaitEachGesture
                            var prevSpan = 0f
                            while (true) {
                                val e = awaitPointerEvent()
                                val a = e.changes.firstOrNull { it.id == first.id } ?: break
                                val b = e.changes.firstOrNull { it.id == id2 } ?: break
                                if (!a.pressed || !b.pressed || a.isConsumed || b.isConsumed) break
                                val span = (a.position - b.position).getDistance()
                                if (prevSpan > 0f && span > 0f) {
                                    val next = FontSizeZoom.applyZoom(fontSizeState.value, span / prevSpan)
                                    if (next != fontSizeState.value) {
                                        uiScope.launch { settingsManager.setFontSize(next) }
                                    }
                                }
                                prevSpan = span
                                a.consume()
                                b.consume()
                            }
                        }
                    }
                    .onPreviewKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                        // Phase 12 — autocomplete keys while the popup is up:
                        // TAB/ENTER insert the highlighted suggestion, arrows
                        // move the highlight, ESC dismisses until next edit.
                        if (event.type == KeyEventType.KeyDown && showCompletion) {
                            val size = completionItems.size
                            when (event.key) {
                                Key.Tab, Key.Enter, Key.NumPadEnter -> {
                                    insertCompletion(completionItems[completionIndex % size])
                                    true
                                }
                                Key.DirectionDown -> {
                                    completionIndex = (completionIndex + 1) % size
                                    true
                                }
                                Key.DirectionUp -> {
                                    completionIndex = (completionIndex - 1 + size) % size
                                    true
                                }
                                Key.Escape -> {
                                    completionDismissed = true
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .then(if (!wordWrap) Modifier.horizontalScroll(hScrollState) else Modifier)
                        .padding(vertical = 8.dp)
                ) {
                    if (showLineNumbers) {
                        // Phase 22.1 / 22.4 — the gutter is driven by a
                        // derived line COUNT, not by the buffer. Reading
                        // `codeText` here made every keystroke recompose the
                        // gutter; `derivedStateOf` re-runs the cheap newline
                        // count but only invalidates this scope when the
                        // count actually changes, so typing inside a line is
                        // free and the string is rebuilt only on line add or
                        // remove.
                        val lineCount by remember {
                            derivedStateOf { codeText.text.count { it == '\n' } + 1 }
                        }
                        val lineNumbers = remember(lineCount) {
                            (1..lineCount).joinToString("\n")
                        }
                        // Mockup-exact gutter: right-aligned muted numbers with
                        // a hairline vertical divider at the gutter edge.
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .drawBehind {
                                    drawLine(
                                        color = editorColors.text.copy(alpha = 0.14f),
                                        start = Offset(size.width - 0.5f, 0f),
                                        end = Offset(size.width - 0.5f, size.height),
                                        strokeWidth = 1f
                                    )
                                }
                        ) {
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

                // Phase 12 — floating autocomplete popup, anchored near the
                // cursor's text-layout rectangle (line-number gutter + padding
                // and the current scroll offsets are folded in).
                if (showCompletion) {
                    val density = LocalDensity.current
                    val cursorRect = textLayoutResult?.let { result ->
                        runCatching {
                            result.getCursorRect(
                                codeText.selection.end.coerceAtLeast(codeText.selection.start)
                            )
                        }.getOrNull()
                    }
                    val lineNumberWidthPx = with(density) { (40.dp + 8.dp).toPx() }
                    val topPaddingPx = with(density) { 8.dp.toPx() }
                    val anchorX = (lineNumberWidthPx + (cursorRect?.left ?: 0f) - hScrollState.value)
                        .coerceAtLeast(0f)
                    val anchorY = (topPaddingPx + (cursorRect?.top ?: 0f) - scrollState.value)
                        .coerceAtLeast(0f)
                    Surface(
                        tonalElevation = 6.dp,
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(anchorX.roundToInt(), anchorY.roundToInt()) }
                            .width(280.dp)
                            .heightIn(max = 220.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            itemsIndexed(completionItems) { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { insertCompletion(item) }
                                        .background(
                                            if (index == completionIndex % completionItems.size) {
                                                Color(0x33FFFFFF)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    item.detail?.let { detail ->
                                        Text(
                                            text = detail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0x99FFFFFF),
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Phase 16 — Spck bottom order: the snippet/keys row docks ABOVE
            // the status bar (the old SymbolBar sat below it); the toolbar
            // chevron toggles the row when it would crowd small screens.
            // Phase 22.2 — while the soft keyboard is up the row moves to the
            // very bottom of the column instead, so with `imePadding()` above
            // it lands DIRECTLY on top of the keyboard (Termux's extra-keys
            // behavior) rather than being stranded mid-screen.
            if (keysRowVisible && !imeVisible) {
                EditorKeysRow(
                    textFieldValue = codeText,
                    onValueChange = { viewModel.updateCode(it, autoIndent = autoIndent, tabSize = tabSize) },
                    tabSize = tabSize,
                    language = language,
                    customSnippets = customSnippets
                )
            }

            // Phase 22.4 — while the keyboard is up the status bar yields its
            // row to the editor. Ln/Col is a glance-value readout, not
            // something you consult mid-keystroke, and on a phone this is a
            // whole extra line of code kept visible above the keyboard. It
            // returns the moment the keyboard closes.
            if (!imeVisible) {
                EditorStatusBar(
                    line = cursorPos.line,
                    column = cursorPos.column,
                    selectionLength = cursorPos.selectionLength,
                    tabSize = tabSize,
                    errorCount = EditorShellUi.errorCount(diagnostics),
                    warningCount = EditorShellUi.warningCount(diagnostics),
                    onDiagnosticsClick = {
                        // Phase 16 — the errors badge taps to the first error
                        // (Spck's jump); warnings-only still opens the review.
                        val target = EditorShellUi.firstError(diagnostics)
                        if (target != null) viewModel.jumpToDiagnostic(target) else showDiagnosticsDialog = true
                    },
                    languageLabel = language.label,
                    lineEnding = activeLineEnding,
                    onLineEndingClick = if (currentProject != null && activeTabPath != null) {
                        { viewModel.toggleLineEnding(context) }
                    } else {
                        null
                    }
                )
            }

            // Phase 11: split-screen Output Panel. Expanded = draggable
            // splitter + panel; collapsed = one-line strip (tap to expand).
            val maxPanelHeight = LocalConfiguration.current.screenHeightDp * 0.55f
            var outputPanelHeight by remember { mutableStateOf(220f) }
            if (outputExpanded) {
                OutputPanelSplitter(
                    onDragDelta = { dragAmount ->
                        outputPanelHeight = (outputPanelHeight - dragAmount)
                            .coerceIn(120f, maxPanelHeight)
                    }
                )
                OutputPanelView(
                    state = outputState,
                    isExpanded = true,
                    onStop = { viewModel.stopRun() },
                    onClear = { viewModel.clearOutput() },
                    onToggleExpand = { viewModel.toggleOutput() },
                    onOpenInTerminal = { outputState.lastTerminalCommand?.let(onOpenInTerminal) },
                    onDiagnosticTap = { viewModel.jumpToOutputDiagnostic(context, it) },
                    onApplyFix = { viewModel.applyFixForOutputDiagnostic(context, it) },
                    onSendInput = { viewModel.sendInputToRun(it) },
                    // The Output Panel's "open URL" button carries the same
                    // authoritative project as the RUN ▶ preview path.
                    onOpenPreviewUrl = { url -> onOpenPreviewUrl(currentProject, url) },
                    modifier = Modifier.height(outputPanelHeight.dp)
                )
            } else if (outputState.hasContent() && !imeVisible) {
                // Phase 22.4 — the collapsed strip only exists once there IS
                // output, and never while you are typing. Before the first
                // RUN it was 64dp of permanently reserved height showing
                // nothing, which on a phone is a real chunk of the editor.
                // `clearOutput()` hides it again. The EXPANDED panel is left
                // alone even with the keyboard up — if you deliberately
                // opened it (e.g. to answer a prompt) it must stay.
                OutputPanelView(
                    state = outputState,
                    isExpanded = false,
                    onStop = { viewModel.stopRun() },
                    onClear = { viewModel.clearOutput() },
                    onToggleExpand = { viewModel.toggleOutput() },
                    onOpenInTerminal = { outputState.lastTerminalCommand?.let(onOpenInTerminal) },
                    onDiagnosticTap = { viewModel.jumpToOutputDiagnostic(context, it) },
                    onApplyFix = { viewModel.applyFixForOutputDiagnostic(context, it) },
                    onSendInput = { viewModel.sendInputToRun(it) },
                    onOpenPreviewUrl = { url -> onOpenPreviewUrl(currentProject, url) },
                    modifier = Modifier.height(64.dp)
                )
            }

            // Phase 22.2 — the IME-anchored position. This is the last child
            // of the imePadding()'d column, so it sits flush on top of the
            // soft keyboard. Same composable, same key set, same actions as
            // the docked row above — only the position changes, so nothing
            // about find/replace, autocomplete or the status bar is affected.
            if (keysRowVisible && imeVisible) {
                EditorKeysRow(
                    textFieldValue = codeText,
                    onValueChange = { viewModel.updateCode(it, autoIndent = autoIndent, tabSize = tabSize) },
                    tabSize = tabSize,
                    language = language,
                    customSnippets = customSnippets,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                )
            }
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

        // Phase 16 — the drawer replaced the files bottom-sheet; its dialogs
        // (create entry, per-row rename, delete confirm) live here now.
        pendingCreate?.let { (parent, isFolder) ->
            AlertDialog(
                onDismissRequest = { pendingCreate = null },
                title = { Text(stringResource(if (isFolder) R.string.new_folder else R.string.new_file)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = entryName,
                            onValueChange = { entryName = it },
                            label = { Text(stringResource(if (isFolder) R.string.new_folder else R.string.file_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Created in: " + (parent ?: (currentProject ?: stringResource(R.string.editor_scratch_mode))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingCreate = null
                        val name = entryName
                        entryName = ""
                        if (isFolder) viewModel.createFolderEntry(context, name, parent)
                        else viewModel.createAndOpenFile(context, name, parent)
                    }) { Text(stringResource(R.string.create)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCreate = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        pendingRenameEntry?.let { target ->
            var renameValue by remember(target) { mutableStateOf(target.name) }
            AlertDialog(
                onDismissRequest = { pendingRenameEntry = null },
                title = { Text(stringResource(R.string.rename_file)) },
                text = {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        label = { Text(stringResource(R.string.file_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (renameValue.isNotBlank()) {
                            viewModel.renameFileEntry(context, target, renameValue)
                        }
                        pendingRenameEntry = null
                    }) { Text(stringResource(R.string.rename)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRenameEntry = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (pendingDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete ${pendingDelete?.name ?: ""}?") },
                text = { Text("The entry is removed from disk. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDelete?.let { viewModel.deleteFileEntry(context, it) }
                        pendingDelete = null
                    }) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showGoToLineDialog) {
            AlertDialog(
                onDismissRequest = { showGoToLineDialog = false },
                title = { Text(stringResource(R.string.jump_to_line)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.go_to_line_prompt),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = goToLineText,
                            onValueChange = { raw -> goToLineText = raw.filter { it.isDigit() }.take(7) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = goToLineText.toIntOrNull() != null,
                        onClick = {
                            showGoToLineDialog = false
                            goToLineText.toIntOrNull()?.let { viewModel.jumpToLine(it) }
                        }
                    ) { Text(stringResource(R.string.jump_to_line)) }
                },
                dismissButton = {
                    TextButton(onClick = { showGoToLineDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        gitSheetRoot?.let { root ->
            GitControlSheet(projectRoot = root, onDismiss = { gitSheetRoot = null })
        }

        // Phase 17 — Switch Branch from the drawer footer: closing refreshes
        // the drawer's branch chip and status letters.
        gitBranchSheetRoot?.let { root ->
            BranchSwitchSheet(
                projectRoot = root,
                onDismiss = {
                    gitBranchSheetRoot = null
                    viewModel.refreshGitMeta(context)
                }
            )
        }

        // Phase 9.2: open a project folder (or back to single files) without
        // leaving the editor.
        if (showContextPicker) {
            val pickerProjects = remember {
                runCatching { ProjectManager(context).listProjects() }.getOrDefault(emptyList())
            }
            AlertDialog(
                onDismissRequest = { showContextPicker = false },
                title = { Text("Open folder") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "The editor works inside one folder at a time. Everything you open " +
                                "from it becomes a tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                showContextPicker = false
                                viewModel.switchContext(context, null)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (currentProject == null) "●  Single files" else "Single files")
                        }
                        pickerProjects.forEach { project ->
                            TextButton(
                                onClick = {
                                    showContextPicker = false
                                    ProjectManager(context).project(project.name)?.let(onProjectSelected)
                                    viewModel.switchContext(context, project.name)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (currentProject == project.name) "●  ${project.name}" else project.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (pickerProjects.isEmpty()) {
                            Text(
                                "No projects yet — create one in the Projects tab, or keep working " +
                                    "with single files here.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showContextPicker = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
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

/**
 * Phase 11 — the draggable splitter between the editor pane and the Output
 * Panel. Dragging up grows the panel; dragging down shrinks it. The caller
 * clamps the resulting height.
 */
@Composable
private fun OutputPanelSplitter(onDragDelta: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(3.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(2.dp)
                )
        )
    }
}
