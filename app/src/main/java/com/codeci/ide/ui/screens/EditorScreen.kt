package com.codeci.ide.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
import com.codeci.ide.ui.components.RunKeysRow
import com.codeci.ide.ui.editor.CompilerDiagnostics
import com.codeci.ide.ui.editor.DiagnosticSeverity
import com.codeci.ide.ui.editor.EditorDiagnostic
import com.codeci.ide.ui.editor.EditorShellUi
import com.codeci.ide.ui.editor.SmartTyping
import com.codeci.ide.ui.editor.FileTreeCollapse
import com.codeci.ide.ui.editor.KeysContext
import com.codeci.ide.ui.editor.KeysForContext
import com.codeci.ide.ui.editor.RunKey
import com.codeci.ide.ui.editor.keysForContext
import com.codeci.ide.ui.editor.sora.SoraEditorHost
import io.github.rosemoe.sora.widget.CodeEditor
import com.codeci.ide.ui.projects.ProjectInfo
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.services.LanguageRegistry
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.ThemeManager
import com.codeci.ide.ui.theme.getEditorTheme
import com.codeci.ide.ui.terminal.TerminalHandoff
import com.codeci.ide.ui.utils.FileManager
import com.codeci.ide.ui.utils.LanguageType
import com.codeci.ide.ui.utils.WebFileSupport
import com.codeci.ide.ui.viewmodels.EditorFileEntry
import com.codeci.ide.ui.viewmodels.EditorViewModel
import java.io.File
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Mockup-exact RUN affordance green (Spck's run action color). */
private val RunGreen = Color(0xFF3DDC84)

/** Tap-anchor for the inline diagnostic tooltip. */

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
    // Phase 25.2 — the edit core is sora-editor's CodeEditor, chosen by the
    // 25.1 device bench (keystroke p95 14.5 ms vs the old stack's 404 ms on a
    // 5 000-line file). Declared early: both the find-searcher effect below
    // and the SoraEditorHost call need it. The ViewModel remains the source
    // of truth; SoraEditorHost bridges both ways.
    val soraEditor = remember { CodeEditor(context) }
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
    val imeGuideDismissed by settingsManager.imeGuideDismissedFlow.collectAsState(initial = true)
    // Phase 26.1 — persisted strip overrides (JSON) — when empty, defaults are used.
    val keyStripJson by settingsManager.editorKeyStripJsonFlow.collectAsState(initial = "")
    // Phase 26.2 — smart typing toggles.
    val typeOverEnabled by settingsManager.smartTypingTypeOverFlow.collectAsState(initial = true)
    val wrapEnabled by settingsManager.smartTypingWrapSelectionFlow.collectAsState(initial = true)
    val emptyPairEnabled by settingsManager.smartTypingEmptyPairFlow.collectAsState(initial = true)
    val smartAutoIndentEnabled by settingsManager.smartTypingAutoIndentFlow.collectAsState(initial = true)
    val stringAwareEnabled by settingsManager.smartTypingStringAwareFlow.collectAsState(initial = true)
    // deleteWord toggle not yet used for strip hold; kept for future.

    LaunchedEffect(typeOverEnabled, wrapEnabled, emptyPairEnabled, smartAutoIndentEnabled, stringAwareEnabled, autoIndent) {
        viewModel.setSmartTypingConfig(
            SmartTyping.Config(
                typeOver = typeOverEnabled,
                wrapSelection = wrapEnabled,
                emptyPairBackspace = emptyPairEnabled,
                autoIndent = smartAutoIndentEnabled && autoIndent,
                stringAware = stringAwareEnabled,
                deleteWord = true
            )
        )
    }

    // Phase 22.2 — "is the soft keyboard up?". WindowInsets.ime animates, so
    // the bottom inset is > 0 for the whole show/hide animation; that is
    // exactly the window during which the keys row must ride the keyboard.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val uiScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

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
    // Phase 24.9 — per-project .codec.json run-config editor.
    var showCodecConfig by remember { mutableStateOf(false) }
    var gitSheetRoot by remember { mutableStateOf<File?>(null) }
    // Phase 17 — Switch Branch, opened from the drawer footer.
    var gitBranchSheetRoot by remember { mutableStateOf<File?>(null) }
    var keysRowVisible by remember { mutableStateOf(true) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var pendingCloseTab by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Phase 12 — language-aware editing: the active file's extension selects
    // the syntax highlighter and the autocomplete suggestions. The popup
    // recomputes on every buffer/selection change, resets its selection on
    // new suggestions, and ESC dismisses it until the next edit.
    val language = remember(activeTabPath, currentFileName) {
        LanguageType.fromFileName(activeTabPath ?: currentFileName)
    }
    // Phase 23.2 — which strip to show. An interactive run waiting for stdin
    // swaps the editor keys for the run keys (Enter / Ctrl+C / Tab / arrows);
    // otherwise the editor's per-language keys are shown (as before). Idle is
    // never produced here — the existing `keysRowVisible` toggle governs
    // whether the strip appears at all.
    val keysContext = if (outputState.waitingForInput) {
        KeysContext.InteractiveRun
    } else {
        KeysContext.Editor(language)
    }
    val resolvedKeys = remember(keysContext, customSnippets, keyStripJson) {
        keysForContext(keysContext, customSnippets, keyStripJson)
    }
    // Phase 23.2 — the run keys are VM actions, not editor buffer edits.
    val handleRunKey: (RunKey) -> Unit = { action ->
        when (action) {
            RunKey.SUBMIT -> viewModel.submitInput()
            RunKey.INTERRUPT -> viewModel.interruptRun()
            RunKey.TAB -> viewModel.appendInput("\t")
            RunKey.HISTORY_UP, RunKey.HISTORY_DOWN -> Unit
            // Phase 26.1 — run popups (HOME/END/PGUP/PGDN) send cursor/escape sequences to the PTY input line where useful.
            RunKey.HOME -> viewModel.appendInput("\u001b[H")
            RunKey.END -> viewModel.appendInput("\u001b[F")
            RunKey.PAGE_UP -> viewModel.appendInput("\u001b[5~")
            RunKey.PAGE_DOWN -> viewModel.appendInput("\u001b[6~")
        }
    }
    // Phase 25.2 device-round 3 — completions are served by sora's NATIVE
    // panel (CodeCLanguage.requireAutoComplete -> CodeCompletionEngine):
    // positioned at the caret, typed prefix replaced on commit, sora-managed
    // keyboard handling. The Phase 12/22 app popup (bottom-anchored, own
    // hardware-key handling) is retired.

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

    // Phase 25.2 — the find bar's match highlighting rides sora's own
    // searcher (matches are drawn by the editor surface itself); navigation
    // stays on the VM (findNext/findPrev set the selection, which the bridge
    // mirrors into sora's caret).
    LaunchedEffect(findState.visible, findState.query) {
        val searcher = soraEditor.searcher
        if (findState.visible && findState.query.isNotEmpty()) {
            runCatching {
                val type = when {
                    findState.options.regex -> io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                    findState.options.wholeWord -> io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
                    else -> io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions.TYPE_NORMAL
                }
                searcher.search(
                    findState.query,
                    io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions(
                        type,
                        !findState.options.matchCase
                    )
                )
            }
        } else {
            runCatching { searcher.stopSearch() }
        }
    }

    // Phase 22.1 — narrowed keys: the tab strip only depends on the tab list,
    // which tab is active, and the active tab's dirty flag. Keying it on the
    // live buffer rebuilt every tab model on every keystroke.
    val tabViews = remember(openTabs, activeTabPath, isDirty) {
        openTabs.map { tab ->
            if (tab.relativePath == activeTabPath) {
                // The active tab's truth is the live buffer + VM dirtiness
                // (Phase 22.5: its `buffer` stash is deliberately stale
                // between boundaries, so it must NOT be read here).
                EditorTabUi(tab.relativePath, tab.displayName, isDirty)
            } else {
                EditorShellUi.tabModel(tab.relativePath, tab.buffer.text, tab.savedText)
            }
        }
    }


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

    // Phase 26.3 — IME guide (first-run tips for soft-keyboard users). Dismiss persists via DataStore.
    if (!imeGuideDismissed) {
        AlertDialog(
            onDismissRequest = { uiScope.launch { settingsManager.setImeGuideDismissed(true) } },
            title = { Text("Typing tips") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Long-press a key for its popup ( ; → : , \" → ` , / → comment).", style = MaterialTheme.typography.bodySmall)
                    Text("• Swipe up/down on () {} [] <> to insert just the opener or closer.", style = MaterialTheme.typography.bodySmall)
                    Text("• Hold ← → to repeat quickly.", style = MaterialTheme.typography.bodySmall)
                    Text("• Smart typing (type-over, auto-pair, empty-pair delete) respects strings/comments.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { uiScope.launch { settingsManager.setImeGuideDismissed(true) } }) {
                    Text("Got it")
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
            // Phase 25.2 device-round 3 (owner report): the edge-swipe zone
            // covers the editor's line-number gutter, so vertical scrolls
            // starting there (any slight horizontal drift) opened the file
            // drawer mid-scroll. The gesture stays available only when no
            // file is on screen; with a file open, use the folder button.
            gesturesEnabled = activeTabPath == null && currentFileName.isEmpty(),
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
                                // Phase 22.5 — the ACTIVE tab's dirtiness comes
                                // from the VM flag (its stash is intentionally
                                // not updated per keystroke); other tabs are
                                // stashed at their boundaries, so their buffer
                                // is current.
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
                            if (LanguageRegistry.forFile(currentFileName)?.formatterTemplate != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.format)) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.formatActiveFile(context, tabSize)
                                    }
                                )
                            }
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
                                // Phase 24.9 — per-project `.codec.json` override.
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_run_config)) },
                                    onClick = {
                                        showMoreMenu = false
                                        showCodecConfig = true
                                    }
                                )
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
                    // Phase 24.6 — Test ▷ for pytest/go test files, alongside RUN.
                    if (LanguageRegistry.testProfileForFile(currentFileName) != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.runTests(context) }
                                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.run_tests),
                                tint = RunGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                stringResource(R.string.run_tests),
                                color = RunGreen,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(editorColors.background)
                    .onPreviewKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                        // Phase 12 — autocomplete keys while the popup is up:
                        // TAB/ENTER insert the highlighted suggestion, arrows
                        // move the highlight, ESC dismisses until next edit.
                        if (event.type == KeyEventType.KeyDown) {
                            // Phase 24.3 — hardware-keyboard shortcuts.
                            when {
                                // Phase 25.2 — undo/redo stays on the VM's per-tab
                                // EditorUndoManager (sora's own stack is disabled in
                                // the bridge, so Ctrl+Z must NOT reach it).
                                event.isCtrlPressed && !event.isShiftPressed && event.key == Key.Z -> {
                                    viewModel.undo(); true
                                }
                                event.isCtrlPressed && event.isShiftPressed && event.key == Key.Z -> {
                                    viewModel.redo(); true
                                }
                                event.isCtrlPressed && event.key == Key.Y -> {
                                    viewModel.redo(); true
                                }
                                event.isCtrlPressed && event.key == Key.R -> {
                                    viewModel.runActiveFile(context); true
                                }
                                event.isCtrlPressed && event.key == Key.F -> {
                                    if (findState.visible) viewModel.hideFind() else viewModel.showFind(); true
                                }
                                event.isCtrlPressed && event.key == Key.Slash -> {
                                    viewModel.toggleLineComment(language); true
                                }
                                event.isCtrlPressed && event.key == Key.D -> {
                                    viewModel.duplicateLine(); true
                                }
                                event.isCtrlPressed && event.key == Key.W -> {
                                    viewModel.closeActiveTab(context); true
                                }
                                event.isCtrlPressed && !event.isShiftPressed && event.key == Key.Tab -> {
                                    viewModel.nextTab(); true
                                }
                                event.isCtrlPressed && event.isShiftPressed && event.key == Key.Tab -> {
                                    viewModel.prevTab(); true
                                }
                                event.key == Key.F5 -> {
                                    viewModel.runActiveFile(context); true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
            ) {
                // Phase 25.2 — the edit surface: sora-editor. Line numbers,
                // word wrap, current-line highlight, bracket-pair drawing,
                // pinch text-scale and the caret magnifier are sora-native;
                // the VM bridge keeps every VM-driven feature (keys strip,
                // completions, find, undo, autosave) working unchanged.
                SoraEditorHost(
                    editor = soraEditor,
                    viewModel = viewModel,
                    language = language,
                    theme = currentEditorTheme,
                    fontSizeSp = fontSize,
                    fontFamily = editorFont,
                    tabSize = tabSize,
                    wordWrap = wordWrap,
                    showLineNumbers = showLineNumbers,
                    modifier = Modifier
                        .fillMaxSize()
                )

                if (isFormatting) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // Phase 25.2 device-round 3 — completions render in sora's
                // native panel at the caret; nothing to compose here.
            }

            // Phase 16 — Spck bottom order: the snippet/keys row docks ABOVE
            // the status bar (the old SymbolBar sat below it); the toolbar
            // chevron toggles the row when it would crowd small screens.
            // Phase 22.2 — while the soft keyboard is up the row moves to the
            // very bottom of the column instead, so with `imePadding()` above
            // it lands DIRECTLY on top of the keyboard (Termux's extra-keys
            // behavior) rather than being stranded mid-screen.
            if (keysRowVisible && !imeVisible) {
                KeysStrip(
                    resolved = resolvedKeys,
                    textFieldValue = codeText,
                    onEditorValueChange = { viewModel.updateCode(it, autoIndent = autoIndent, tabSize = tabSize) },
                    tabSize = tabSize,
                    onRunKey = handleRunKey,
                    onCommentToggle = { viewModel.toggleLineComment(language) }
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
                    onInputChange = { viewModel.onInputChange(it) },
                    onSubmitInput = { viewModel.submitInput() },
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
                    onInputChange = { viewModel.onInputChange(it) },
                    onSubmitInput = { viewModel.submitInput() },
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
                KeysStrip(
                    resolved = resolvedKeys,
                    textFieldValue = codeText,
                    onEditorValueChange = { viewModel.updateCode(it, autoIndent = autoIndent, tabSize = tabSize) },
                    tabSize = tabSize,
                    onRunKey = handleRunKey,
                    onCommentToggle = { viewModel.toggleLineComment(language) },
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

        // Phase 24.9 — per-project `.codec.json` build/run override editor.
        if (showCodecConfig) {
            val existing = remember { viewModel.codecOverrideForActiveProject(context) }
            var buildText by remember(existing) { mutableStateOf(existing?.build ?: "") }
            var runText by remember(existing) { mutableStateOf(existing?.run ?: "") }
            AlertDialog(
                onDismissRequest = { showCodecConfig = false },
                title = { Text(stringResource(R.string.edit_run_config)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.codec_config_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = buildText,
                            onValueChange = { buildText = it },
                            label = { Text(stringResource(R.string.build_command)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = runText,
                            onValueChange = { runText = it },
                            label = { Text(stringResource(R.string.run_command)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val saved = viewModel.saveCodecRunConfig(context, buildText, runText)
                        showCodecConfig = false
                        Toast.makeText(
                            context,
                            context.getString(
                                if (saved) R.string.run_config_saved else R.string.run_config_failed
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = {
                    TextButton(onClick = { showCodecConfig = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}


/**
 * Phase 23.2 — one strip, two key sets. Renders whichever keys the current
 * context resolved to: the editor's per-language keys (buffer edits) or the
 * interactive-run keys (VM actions). [KeysForContext.None] renders nothing —
 * the strip's visibility is still governed by the existing chevron toggle.
 */
@Composable
private fun KeysStrip(
    resolved: KeysForContext,
    textFieldValue: TextFieldValue,
    onEditorValueChange: (TextFieldValue) -> Unit,
    tabSize: Int,
    onRunKey: (RunKey) -> Unit,
    onCommentToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    when (resolved) {
        is KeysForContext.None -> Unit
        is KeysForContext.EditorKeys -> EditorKeysRow(
            keys = resolved.defs,
            textFieldValue = textFieldValue,
            onValueChange = onEditorValueChange,
            tabSize = tabSize,
            onCommentToggle = onCommentToggle,
            modifier = modifier
        )
        is KeysForContext.RunKeys -> RunKeysRow(onKeyAction = onRunKey, modifier = modifier)
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
