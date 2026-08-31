package com.codeci.ide.ui.viewmodels

import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeci.ide.R
import com.codeci.ide.ui.editor.BracketMatcher
import com.codeci.ide.ui.editor.ClangFormatBridge
import com.codeci.ide.ui.editor.CodeFormatter
import com.codeci.ide.ui.editor.CompilerDiagnostics
import com.codeci.ide.ui.editor.DiagnosticSeverity
import com.codeci.ide.ui.editor.EditorDiagnostic
import com.codeci.ide.ui.editor.EditorTab
import com.codeci.ide.ui.editor.EditorUndoManager
import com.codeci.ide.ui.editor.FileTreeCollapse
import com.codeci.ide.ui.editor.FindOptions
import com.codeci.ide.ui.editor.FindOutcome
import com.codeci.ide.ui.editor.FindReplaceEngine
import com.codeci.ide.ui.editor.LineEndings
import com.codeci.ide.ui.editor.OutputDiagnostic
import com.codeci.ide.ui.editor.OutputLineParser
import com.codeci.ide.ui.projects.AutoRunPlan
import com.codeci.ide.ui.projects.FileNode
import com.codeci.ide.ui.projects.FileTreeRepository
import com.codeci.ide.ui.projects.GitContext
import com.codeci.ide.ui.projects.ProjectConfig
import com.codeci.ide.ui.projects.ProjectInfo
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.projects.ProjectRunDetector
import com.codeci.ide.ui.projects.ProjectsHub
import com.codeci.ide.ui.services.CompilerSettings
import com.codeci.ide.ui.services.ExecutionRunner
import com.codeci.ide.ui.services.InteractiveRunSession
import com.codeci.ide.ui.services.RunEvent
import com.codeci.ide.ui.services.RunPhase
import com.codeci.ide.ui.services.RunSpec
import com.codeci.ide.ui.services.ServerEvent
import com.codeci.ide.ui.services.ServerRunner
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.stats.StatsManager
import com.codeci.ide.ui.terminal.ShellBootstrap
import com.codeci.ide.ui.terminal.TerminalHandoff
import com.codeci.ide.ui.utils.FileManager
import com.codeci.ide.ui.utils.FileNameUtils
import com.codeci.ide.ui.utils.LanguageType
import com.codeci.ide.ui.utils.WebFileSupport
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Row of the editor's file drawer (Phase 9.1). [projectName] is null for
 * scratch files stored directly under `CodeC/projects/`.
 */
data class EditorFileEntry(
    val projectName: String?,
    val relativePath: String,
    val name: String,
    val depth: Int,
    val isDirectory: Boolean
)

enum class OutputPhase { IDLE, BUILDING, RUNNING, DONE, CANCELLED, FAILED }

enum class OutputLineKind { COMMAND, BUILD, OUTPUT, ERROR, STATS, SYSTEM }

/** One rendered line of the Phase 11 Output Panel. */
data class OutputLine(
    val text: String,
    val kind: OutputLineKind,
    /** True for an unterminated PTY fragment (a prompt without a newline). */
    val partial: Boolean = false
)

/**
 * Phase 11 — the whole Output Panel state: the streaming lines, the current
 * phase, build/run timing and exit codes, the header summary, and the last
 * full terminal command (for the "Open in Terminal" escape hatch).
 */
data class OutputRunState(
    val phase: OutputPhase = OutputPhase.IDLE,
    val lines: List<OutputLine> = emptyList(),
    val buildExitCode: Int? = null,
    val runExitCode: Int? = null,
    val buildDurationMs: Long? = null,
    val runDurationMs: Long? = null,
    val busy: Boolean = false,
    val summary: String? = null,
    val lastTerminalCommand: String? = null,
    /** Phase 14 — the live loopback URL of a running server project (Open Preview). */
    val serverUrl: String? = null,
    /** Phase 14 — true while the panel is attached to a long-lived server, not a batch run. */
    val serverRun: Boolean = false
)

/** Phase 9 — cursor readout for the editor status bar. */
data class EditorCursorPos(val line: Int, val column: Int, val selectionLength: Int)

/** Phase 9 — the whole find/replace bar state. */
data class FindUiState(
    val visible: Boolean = false,
    val query: String = "",
    val replacement: String = "",
    val options: FindOptions = FindOptions(),
    val matches: List<IntRange> = emptyList(),
    val activeIndex: Int = -1,
    val error: String? = null
)

class EditorViewModel : ViewModel() {

    companion object {
        const val MAX_OPEN_TABS = 12
        const val MAX_TAB_FILE_BYTES = 256_000L
        private const val SCRATCH_KEY = "\u0000scratch"
        private val INITIAL_CODE = """
            #include <stdio.h>

            int main() {
                printf("Hello, World!\n");
                return 0;
            }
        """.trimIndent()
    }

    private val _codeText = MutableStateFlow(TextFieldValue(INITIAL_CODE))
    val codeText: StateFlow<TextFieldValue> = _codeText.asStateFlow()

    private val _fileName = MutableStateFlow("main.c")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _projectName = MutableStateFlow<String?>(null)
    val projectName: StateFlow<String?> = _projectName.asStateFlow()

    private val _outputState = MutableStateFlow(OutputRunState())
    val outputState: StateFlow<OutputRunState> = _outputState.asStateFlow()

    private val _outputExpanded = MutableStateFlow(false)
    val outputExpanded: StateFlow<Boolean> = _outputExpanded.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _isRenaming = MutableStateFlow(false)
    val isRenaming: StateFlow<Boolean> = _isRenaming.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // ---- Phase 9: multi-file tabs --------------------------------------

    private val _openTabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val openTabs: StateFlow<List<EditorTab>> = _openTabs.asStateFlow()

    private val _activeTabPath = MutableStateFlow<String?>(null)
    val activeTabPath: StateFlow<String?> = _activeTabPath.asStateFlow()

    // ---- Phase 16: drawer collapse, line endings, git meta, launch default ----

    private val _activeLineEnding = MutableStateFlow(LineEndings.LF)
    val activeLineEnding: StateFlow<String> = _activeLineEnding.asStateFlow()

    private val _collapsedDirs = MutableStateFlow<Set<String>>(emptySet())
    val collapsedDirs: StateFlow<Set<String>> = _collapsedDirs.asStateFlow()

    private val _gitBranch = MutableStateFlow<String?>(null)
    val gitBranch: StateFlow<String?> = _gitBranch.asStateFlow()

    private val _gitBadges = MutableStateFlow<Map<String, String>>(emptyMap())
    val gitBadges: StateFlow<Map<String, String>> = _gitBadges.asStateFlow()

    private val _gitChangeCount = MutableStateFlow(0)
    val gitChangeCount: StateFlow<Int> = _gitChangeCount.asStateFlow()

    private val _launchDefault = MutableStateFlow<String?>(null)
    val launchDefault: StateFlow<String?> = _launchDefault.asStateFlow()

    // ---- Phase 9: undo/redo --------------------------------------------

    private val undoManagers = HashMap<String, EditorUndoManager>()
    private var scratchSavedText: String = INITIAL_CODE

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // ---- Phase 9: find/replace, format, decorations --------------------

    private val _find = MutableStateFlow(FindUiState())
    val find: StateFlow<FindUiState> = _find.asStateFlow()

    private val _formatting = MutableStateFlow(false)
    val formatting: StateFlow<Boolean> = _formatting.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<EditorDiagnostic>>(emptyList())
    val diagnostics: StateFlow<List<EditorDiagnostic>> = _diagnostics.asStateFlow()

    private val _currentLineRange = MutableStateFlow<IntRange?>(null)
    val currentLineRange: StateFlow<IntRange?> = _currentLineRange.asStateFlow()

    private val _bracketRanges = MutableStateFlow<List<IntRange>>(emptyList())
    val bracketRanges: StateFlow<List<IntRange>> = _bracketRanges.asStateFlow()

    private val _cursorPos = MutableStateFlow(EditorCursorPos(1, 1, 0))
    val cursorPos: StateFlow<EditorCursorPos> = _cursorPos.asStateFlow()

    private var decorationJob: Job? = null

    // ---- Phase 11: Output Panel run pipeline -----------------------------

    private var runJob: Job? = null
    private var activeRunner: ExecutionRunner? = null
    private var interactiveRun: InteractiveRunSession? = null
    private var buildOutputBuffer = StringBuilder()

    // ---- Phase 14: background server pipeline -----------------------------

    private var serverRunJob: Job? = null
    private var activeServer: ServerRunner? = null
    private var serverReadyHandler: ((String) -> Unit)? = null
    private var webPreviewHandler: ((String) -> Unit)? = null

    /**
     * Phase 14 — the Editor wires this to navigation: when a server project's
     * RUN ▶ detects its port line, the handler receives the loopback URL and
     * MainActivity opens Web Preview on it.
     */
    fun setServerReadyHandler(handler: (String) -> Unit) {
        serverReadyHandler = handler
    }

    /**
     * Phase 14 — Auto projects detected as static web (index.html) have no
     * server; the Editor wires this to the same preview navigation used by
     * `web` projects so RUN ▶ just opens the preview.
     */
    fun setWebPreviewHandler(handler: (String) -> Unit) {
        webPreviewHandler = handler
    }

    override fun onCleared() {
        activeServer?.stop()
        activeServer = null
        super.onCleared()
    }

    /**
     * Phase 11 — forward a typed line to the running program's stdin. The
     * interactive PTY session (preferred) gets the line with CR line
     * discipline; the piped fallback gets LF.
     */
    fun sendInputToRun(text: String) {
        val interactive = interactiveRun
        if (interactive != null) {
            interactive.sendLine(text)
        } else {
            activeRunner?.sendInput(text)
        }
    }

    fun consumeMessage() {
        _userMessage.value = null
    }

    // ---------------------------------------------------------------------
    // Text editing + undo recording
    // ---------------------------------------------------------------------

    fun updateCode(newValue: TextFieldValue, autoIndent: Boolean = false, tabSize: Int = 4) {
        val old = _codeText.value
        var next = newValue
        if (autoIndent && isSingleNewlineInsert(old, newValue)) {
            next = applyAutoIndent(old, newValue, tabSize)
        }
        if (next.text != old.text) {
            val manager = undoManager()
            manager.recordChange(old, next, System.currentTimeMillis())
            syncUndoFlags(manager)
            _codeText.value = next
            _isDirty.value = computeDirty(next.text)
            stashActiveTabBuffer(next)
        } else if (next.selection != old.selection) {
            _codeText.value = next
        } else {
            return
        }
        scheduleDecorationRefresh()
    }

    fun undo() {
        val manager = undoManager()
        val target = manager.undo(_codeText.value) ?: return
        _codeText.value = target
        _isDirty.value = computeDirty(target.text)
        stashActiveTabBuffer(target)
        syncUndoFlags(manager)
        refreshDecorationsNow()
    }

    fun redo() {
        val manager = undoManager()
        val target = manager.redo(_codeText.value) ?: return
        _codeText.value = target
        _isDirty.value = computeDirty(target.text)
        stashActiveTabBuffer(target)
        syncUndoFlags(manager)
        refreshDecorationsNow()
    }

    private fun applyBufferEdit(before: TextFieldValue, after: TextFieldValue) {
        if (after.text == before.text) {
            _codeText.value = after
            scheduleDecorationRefresh()
            return
        }
        val manager = undoManager()
        manager.recordChange(before, after, System.currentTimeMillis())
        syncUndoFlags(manager)
        _codeText.value = after
        _isDirty.value = computeDirty(after.text)
        stashActiveTabBuffer(after)
        refreshDecorationsNow()
    }

    private fun isSingleNewlineInsert(old: TextFieldValue, newValue: TextFieldValue): Boolean {
        if (newValue.text.length != old.text.length + 1) return false
        val pos = newValue.selection.min - 1
        return pos >= 0 && newValue.text.getOrNull(pos) == '\n'
    }

    private fun applyAutoIndent(old: TextFieldValue, newValue: TextFieldValue, tabSize: Int): TextFieldValue {
        val insertAt = newValue.selection.min
        val before = newValue.text.substring(0, insertAt)
        val lastLineStart = before.lastIndexOf('\n', startIndex = (insertAt - 2).coerceAtLeast(0))
        val previousLine = if (lastLineStart >= 0) {
            before.substring(lastLineStart + 1, insertAt - 1)
        } else {
            before.dropLast(1)
        }
        val indent = previousLine.takeWhile { it == ' ' || it == '\t' }
        val extra = if (previousLine.trimEnd().endsWith("{")) " ".repeat(tabSize.coerceIn(2, 8)) else ""
        val addition = indent + extra
        if (addition.isEmpty()) return newValue
        val text = newValue.text.substring(0, insertAt) + addition + newValue.text.substring(insertAt)
        val cursor = insertAt + addition.length
        return TextFieldValue(text, TextRange(cursor))
    }

    // ---------------------------------------------------------------------
    // Tabs (project mode)
    // ---------------------------------------------------------------------

    fun openFile(context: Context, projectName: String?, fileName: String?) {
        if (fileName == null) return
        if (projectName != null) {
            openProjectFile(context, projectName, fileName)
        } else {
            openScratchFile(context, fileName)
        }
    }

    private fun openProjectFile(context: Context, projectName: String, relativePath: String) {
        val safe = ProjectPathUtils.sanitizeRelativePath(relativePath) ?: return
        val info = ProjectManager(context).project(projectName) ?: return
        if (_activeTabPath.value == safe && _projectName.value == info.name) return
        val existing = _openTabs.value.firstOrNull { it.relativePath == safe }
        if (existing != null) {
            activateTab(existing)
            return
        }
        val file = ProjectPathUtils.resolveInside(info.root, safe) ?: return
        if (!file.isFile || !file.canRead()) return
        val content = runCatching { file.readText() }.getOrNull() ?: return
        // Phase 16: the buffer always lives in LF; the file's native ending is
        // remembered on the tab and re-expanded on save (Spck-style, no reflow).
        val ending = LineEndings.detect(content)
        val normalized = LineEndings.normalizeToLf(content)
        stashActiveTabBuffer(_codeText.value)
        val tab = EditorTab(safe, TextFieldValue(normalized), normalized, ending)
        _openTabs.value = trimTabs(_openTabs.value.filterNot { it.relativePath == safe } + tab)
        _activeTabPath.value = safe
        _projectName.value = info.name
        _fileName.value = safe
        _codeText.value = tab.buffer
        _activeLineEnding.value = ending
        _isDirty.value = false
        bootstrapRemainingTabs(context, info.root, _openTabs.value)
        resetDecorationsForNewBuffer()
        syncUndoFlags(undoManager())
    }

    private fun openScratchFile(context: Context, name: String) {
        val safe = FileNameUtils.sanitizeFileName(name) ?: return
        if (_activeTabPath.value == null && _fileName.value == safe && _projectName.value == null) return
        val fm = FileManager(context)
        val content = fm.loadFile(safe) ?: return
        stashActiveTabBuffer(_codeText.value)
        _projectName.value = null
        _activeTabPath.value = null
        _fileName.value = safe
        // Phase 16: scratch files display normalized too, but they always
        // save LF (the single-files folder has no config to carry an ending).
        val normalized = LineEndings.normalizeToLf(content)
        _codeText.value = TextFieldValue(normalized)
        scratchSavedText = normalized
        _activeLineEnding.value = LineEndings.LF
        _isDirty.value = false
        resetDecorationsForNewBuffer()
        syncUndoFlags(undoManager())
    }

    /**
     * After the first project file is opened, lazily pre-open the other small
     * text files of the project so the tab bar shows the multi-file workspace.
     */
    private fun bootstrapRemainingTabs(context: Context, root: File, currentTabs: List<EditorTab>) {
        if (currentTabs.size >= MAX_OPEN_TABS) return
        val known = currentTabs.mapTo(mutableSetOf()) { it.relativePath }
        val candidates = runCatching {
            FileTreeRepository.flattenVisible(FileTreeRepository.buildTree(root))
                .filterIsInstance<FileNode.FileLeaf>()
        }.getOrDefault(emptyList())
            .filter { it.sizeBytes in 1..MAX_TAB_FILE_BYTES }
            .filter { isTextLikeFile(it.file.name) && it.relativePath !in known }
            .sortedBy { it.relativePath }
            .take(MAX_OPEN_TABS - currentTabs.size)
        if (candidates.isEmpty()) return
        val loaded = candidates.mapNotNull { leaf ->
            val content = runCatching { leaf.file.readText() }.getOrNull() ?: return@mapNotNull null
            val ending = LineEndings.detect(content)
            val normalized = LineEndings.normalizeToLf(content)
            EditorTab(leaf.relativePath, TextFieldValue(normalized), normalized, ending)
        }
        if (loaded.isEmpty()) return
        _openTabs.value = trimTabs(currentTabs + loaded)
    }

    private fun isTextLikeFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf(
            "c", "h", "cpp", "hpp", "cc", "cxx", "hh", "py", "js", "ts", "css", "html", "htm",
            "json", "md", "txt", "sh", "yaml", "yml", "toml", "xml", "ini", "cfg", "conf",
            "csv", "sql", "kt", "java", "go", "rs", "rb", "php", "lua", "cmake", "mk", "make",
            "gradle", "properties"
        )
    }

    private fun trimTabs(tabs: List<EditorTab>): List<EditorTab> {
        if (tabs.size <= MAX_OPEN_TABS) return tabs
        val active = _activeTabPath.value
        val victim = tabs.firstOrNull { it.relativePath != active && it.buffer.text == it.savedText }
            ?: tabs.firstOrNull { it.relativePath != active }
            ?: return tabs.takeLast(MAX_OPEN_TABS)
        return tabs.filterNot { it === victim }.also { undoManagers.remove(victim.relativePath) }
    }

    fun selectTab(path: String) {
        val tab = _openTabs.value.firstOrNull { it.relativePath == path } ?: return
        activateTab(tab)
    }

    private fun activateTab(tab: EditorTab) {
        if (_activeTabPath.value == tab.relativePath) return
        stashActiveTabBuffer(_codeText.value)
        _activeTabPath.value = tab.relativePath
        _fileName.value = tab.relativePath
        _codeText.value = tab.buffer
        _activeLineEnding.value = tab.lineEnding
        _isDirty.value = tab.buffer.text != tab.savedText
        resetDecorationsForNewBuffer()
        syncUndoFlags(undoManager())
    }

    /**
     * Close [path]. With [saveFirst] the buffer is written to disk first and a
     * failing save aborts the close. The last remaining tab cannot be closed.
     */
    fun closeTab(context: Context, path: String, saveFirst: Boolean) {
        val tabs = _openTabs.value
        if (tabs.size <= 1) return
        val target = tabs.firstOrNull { it.relativePath == path } ?: return
        val text = if (path == _activeTabPath.value) _codeText.value.text else target.buffer.text
        val dirty = text != target.savedText
        if (dirty && saveFirst) {
            val project = _projectName.value
            if (project == null || !writeProjectFile(context, project, path, text)) {
                _userMessage.value = context.getString(R.string.file_save_failed)
                return
            }
        }
        val index = tabs.indexOf(target)
        val remaining = tabs.filterNot { it.relativePath == path }
        _openTabs.value = remaining
        undoManagers.remove(path)
        if (_activeTabPath.value == path) {
            val next = remaining[index.coerceAtMost(remaining.size - 1)]
            _activeTabPath.value = next.relativePath
            _fileName.value = next.relativePath
            _codeText.value = next.buffer
            _activeLineEnding.value = next.lineEnding
            _isDirty.value = next.buffer.text != next.savedText
            resetDecorationsForNewBuffer()
            syncUndoFlags(undoManager())
        }
    }

    /**
     * Phase 16 — tab long-press "Close others": every other tab goes through
     * the regular close (dirty buffers are saved first; a failed save keeps
     * that tab open with a message — we never drop edits silently).
     */
    fun closeOtherTabs(context: Context, keepPath: String) {
        val appContext = context.applicationContext
        _openTabs.value.map { it.relativePath }.filter { it != keepPath }.forEach {
            closeTab(appContext, it, saveFirst = true)
        }
        selectTab(keepPath)
    }

    /**
     * Phase 16 — "Close all": save everything first, then close every tab but
     * the last one — the editor always keeps a buffer alive (its "no last tab
     * to close" invariant), so closing all means closing all-but-active.
     */
    fun closeAllTabs(context: Context) {
        val appContext = context.applicationContext
        stashActiveTabBuffer(_codeText.value)
        saveAllTabs(appContext)
        _openTabs.value.map { it.relativePath }.filter { it != _activeTabPath.value }.forEach {
            closeTab(appContext, it, saveFirst = true)
        }
    }

    private fun stashActiveTabBuffer(buffer: TextFieldValue) {
        val path = _activeTabPath.value ?: return
        _openTabs.value = _openTabs.value.map {
            if (it.relativePath == path) it.copy(buffer = buffer) else it
        }
    }

    private fun updateTab(path: String, transform: (EditorTab) -> EditorTab) {
        _openTabs.value = _openTabs.value.map {
            if (it.relativePath == path) transform(it) else it
        }
    }

    private fun computeDirty(text: String): Boolean {
        val path = _activeTabPath.value ?: return text != scratchSavedText
        val tab = _openTabs.value.firstOrNull { it.relativePath == path } ?: return text != scratchSavedText
        return text != tab.savedText
    }

    private fun undoManager(): EditorUndoManager =
        undoManagers.getOrPut(_activeTabPath.value ?: SCRATCH_KEY) { EditorUndoManager() }

    private fun syncUndoFlags(manager: EditorUndoManager) {
        _canUndo.value = manager.canUndo
        _canRedo.value = manager.canRedo
    }

    // ---------------------------------------------------------------------
    // Rename
    // ---------------------------------------------------------------------

    fun updateFileName(
        context: Context,
        newName: String,
        onRenamed: (String) -> Unit
    ) {
        val sanitized = ProjectPathUtils.sanitizeSegment(newName)
        if (sanitized == null) {
            _userMessage.value = context.getString(R.string.invalid_file_name)
            return
        }
        val project = _projectName.value
        val name = if (project != null) sanitized else WebFileSupport.normalizeFileName(sanitized)
        val oldName = _fileName.value
        val parent = oldName.substringBeforeLast('/', "")
        val newPath = if (parent.isEmpty()) name else "$parent/$name"
        if (newPath == oldName) return

        viewModelScope.launch {
            _isRenaming.value = true
            val success: Boolean
            val existingOnDisk: Boolean
            if (project != null) {
                val info = withContext(Dispatchers.IO) { ProjectManager(context).project(project) }
                val oldFile = info?.let { ProjectPathUtils.resolveInside(it.root, oldName) }
                existingOnDisk = oldFile?.isFile == true
                success = if (info != null && existingOnDisk) {
                    withContext(Dispatchers.IO) {
                        FileTreeRepository.rename(info.root, oldName, name).isSuccess
                    }
                } else {
                    info != null
                }
            } else {
                val fm = FileManager(context)
                existingOnDisk = withContext(Dispatchers.IO) { fm.loadFile(oldName) != null }
                success = if (existingOnDisk) {
                    withContext(Dispatchers.IO) { fm.renameFile(oldName, name) }
                } else true
            }
            if (success) {
                _fileName.value = newPath
                if (!existingOnDisk) _isDirty.value = true
                if (_activeTabPath.value == oldName) {
                    updateTab(oldName) { it.copy(relativePath = newPath) }
                    _activeTabPath.value = newPath
                    undoManagers[newPath] = undoManagers.remove(oldName) ?: EditorUndoManager()
                }
                SettingsManager(context).replaceRecentFile(oldName, newPath)
                _userMessage.value = context.getString(R.string.rename_success)
                onRenamed(newPath)
            } else {
                _userMessage.value = context.getString(R.string.rename_failed)
            }
            _isRenaming.value = false
        }
    }

    // ---------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------

    /**
     * Phase 9.1: save the active buffer into the root of [projectName] and
     * switch the editor's context to that project. Scratch-mode Save writes
     * into `CodeC/projects/` itself (outside every project folder), so this
     * is the escape hatch: after "Save to project…" the terminal's
     * `cd <project> && cc main.c` and the tree's Run action find the file.
     */
    fun saveToProject(context: Context, projectName: String) {
        val appContext = context.applicationContext
        val text = _codeText.value.text
        val base = FileNameUtils.sanitizeFileName(_fileName.value.substringAfterLast('/'))
            ?: "untitled.c"
        if (!writeProjectFile(appContext, projectName, base, text)) {
            _userMessage.value = "Could not save into project '$projectName'"
            return
        }
        val oldPath = _activeTabPath.value
        val tabs = ArrayList(_openTabs.value)
        if (oldPath != null) tabs.removeAll { it.relativePath == oldPath }
        tabs.removeAll { it.relativePath == base }
        tabs.add(0, EditorTab(base, TextFieldValue(text), text))
        _openTabs.value = trimTabs(tabs)
        _activeTabPath.value = base
        _fileName.value = base
        _projectName.value = projectName
        _isDirty.value = false
        if (oldPath == null) scratchSavedText = text
        if (oldPath != base) undoManagers.remove(oldPath ?: SCRATCH_KEY)
        syncUndoFlags(undoManager())
        val root = runCatching { ProjectManager(appContext).project(projectName)?.root }.getOrNull()
        if (root != null) bootstrapRemainingTabs(appContext, root, _openTabs.value)
        _userMessage.value = "Saved to project '$projectName' as $base"
    }

    // ---- Phase 9.1: in-editor file drawer --------------------------------

    private val _fileEntries = MutableStateFlow<List<EditorFileEntry>>(emptyList())
    val fileEntries: StateFlow<List<EditorFileEntry>> = _fileEntries.asStateFlow()

    /**
     * Load the drawer contents: the whole tree of the open project, or the
     * scratch files (CodeC/projects root) when no project context is set.
     */
    fun refreshFileEntries(context: Context) {
        val appContext = context.applicationContext
        val project = _projectName.value
        val entries = if (project != null) {
            val info = runCatching { ProjectManager(appContext).project(project) }.getOrNull()
            // Phase 16 — the drawer header shows this config's launch default
            // marker, so refresh it in the same single pass as the tree.
            _launchDefault.value = info?.config?.launchDefault
            val root = info?.root
            if (root == null) {
                emptyList()
            } else {
                runCatching { drawerEntries(FileTreeRepository.buildTree(root).children) }
                    .getOrDefault(emptyList())
                    .map { it.copy(projectName = project) }
            }
        } else {
            _launchDefault.value = null
            runCatching {
                FileManager(appContext).getProjectDir()
                    .listFiles()
                    ?.filter { it.isFile && !it.name.startsWith(".") }
                    ?.sortedBy { it.name.lowercase() }
                    ?.map { EditorFileEntry(null, it.name, it.name, 0, false) }
                    ?: emptyList()
            }.getOrDefault(emptyList())
        }
        _fileEntries.value = entries
    }

    private fun drawerEntries(nodes: List<FileNode>): List<EditorFileEntry> {
        val out = ArrayList<EditorFileEntry>()
        fun walk(list: List<FileNode>) {
            for (node in list) {
                if (node.file.name.startsWith(".") ||
                    node.relativePath == "bin" || node.relativePath.startsWith("bin/")
                ) continue
                when (node) {
                    is FileNode.DirectoryNode -> {
                        out += EditorFileEntry(null, node.relativePath, node.file.name, node.depth, true)
                        walk(node.children)
                    }
                    is FileNode.FileLeaf ->
                        out += EditorFileEntry(null, node.relativePath, node.file.name, node.depth, false)
                }
            }
        }
        walk(nodes)
        return out
    }

    // ---- Phase 9.2: contexts, single files, drawer actions ---------------

    /**
     * Switch the editor's context between the open project folders and the
     * single-files folder ([projectName] == null). Buffers are saved first so
     * nothing is lost; then the entry file of the new folder opens. This is
     * the "open a project from the editor" gesture the device feedback asked
     * for — no trip through the Projects screen.
     */
    fun switchContext(context: Context, projectName: String?) {
        val appContext = context.applicationContext
        stashActiveTabBuffer(_codeText.value)
        saveAllTabs(appContext)
        _openTabs.value = emptyList()
        undoManagers.clear()
        _activeTabPath.value = null
        // Phase 16 — the drawer belongs to the folder we are leaving.
        _collapsedDirs.value = emptySet()
        _gitBranch.value = null
        _gitBadges.value = emptyMap()
        _gitChangeCount.value = 0
        _launchDefault.value = null
        _activeLineEnding.value = LineEndings.LF
        if (projectName != null) {
            val info = runCatching { ProjectManager(appContext).project(projectName) }.getOrNull()
            if (info == null) {
                _userMessage.value = "Project '$projectName' is gone"
                refreshFileEntries(appContext)
                return
            }
            _projectName.value = info.name
            val entry = runCatching {
                drawerEntries(FileTreeRepository.buildTree(info.root).children)
                    .firstOrNull { !it.isDirectory && isTextLikeFile(it.name) }
            }.getOrNull()
            if (entry != null) {
                openFile(appContext, info.name, entry.relativePath)
            } else {
                // Empty project: the buffer stays, and Save creates main.c in it.
                _fileName.value = "main.c"
                _isDirty.value = true
                resetDecorationsForNewBuffer()
                syncUndoFlags(undoManager())
                refreshFileEntries(appContext)
            }
            return
        }
        _projectName.value = null
        val first = runCatching {
            FileManager(appContext).getProjectDir().listFiles()
                ?.filter { it.isFile && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?.firstOrNull()?.name
        }.getOrNull()
        if (first != null) {
            openFile(appContext, null, first)
        } else {
            _fileName.value = "untitled.c"
            scratchSavedText = _codeText.value.text
            _isDirty.value = false
            resetDecorationsForNewBuffer()
            syncUndoFlags(undoManager())
        }
        refreshFileEntries(appContext)
    }

    /**
     * Create [rawName] in the current context — the project root, or the
     * single-files folder when no project is open — and open it as a tab.
     * This is the single-file path: a file, no project required.
     */
    fun createAndOpenFile(context: Context, rawName: String, parent: String? = null) {
        val appContext = context.applicationContext
        val base = FileNameUtils.sanitizeFileName(rawName.trim())
        if (base == null) {
            _userMessage.value = "Invalid file name"
            return
        }
        val project = _projectName.value
        if (project != null) {
            val info = ProjectManager(appContext).project(project)
            if (info == null) {
                _userMessage.value = "Project '$project' is gone"
                return
            }
            // Phase 16 — "New file here" from a tree row creates inside that
            // folder; the toolbar keeps the root behaviour (parent == null).
            val target = if (parent.isNullOrBlank()) base else
                ProjectPathUtils.sanitizeRelativePath("$parent/$base")
            if (target == null) {
                _userMessage.value = "Invalid folder path"
                return
            }
            val exists = runCatching {
                ProjectPathUtils.resolveInside(info.root, target)?.isFile == true
            }.getOrDefault(false)
            if (!exists && !writeProjectFile(appContext, project, target, "")) {
                _userMessage.value = "Could not create $base"
                return
            }
            if (!parent.isNullOrBlank()) expandAncestors(parent)
            openFile(appContext, project, target)
        } else {
            val fm = FileManager(appContext)
            val exists = fm.loadFile(base) != null
            if (!exists && !fm.saveFile(base, "")) {
                _userMessage.value = "Could not create $base"
                return
            }
            openFile(appContext, null, base)
        }
        refreshFileEntries(appContext)
        _userMessage.value = "Opened $base"
    }

    /** Delete a drawer entry from disk and drop its tab if it was open. */
    fun deleteFileEntry(context: Context, entry: EditorFileEntry) {
        val appContext = context.applicationContext
        val ok = if (entry.projectName != null) {
            runCatching {
                val info = ProjectManager(appContext).project(entry.projectName)
                info != null && FileTreeRepository.delete(info.root, entry.relativePath)
            }.getOrDefault(false)
        } else {
            runCatching { FileManager(appContext).deleteFile(entry.relativePath) }.getOrDefault(false)
        }
        if (!ok) {
            _userMessage.value = "Could not delete ${entry.name}"
            return
        }
        val tab = _openTabs.value.firstOrNull { it.relativePath == entry.relativePath }
        if (tab != null && _openTabs.value.size > 1) {
            closeTab(appContext, tab.relativePath, saveFirst = false)
        }
        refreshFileEntries(appContext)
        _userMessage.value = "Deleted ${entry.name}"
    }

    // ---- Phase 16: drawer collapse + row actions -------------------------

    /** Chevron toggle on a folder row. */
    fun toggleDirectory(path: String) {
        _collapsedDirs.value = _collapsedDirs.value.toMutableSet().apply {
            if (!add(path)) remove(path)
        }
    }

    /** Drawer toolbar "Collapse All": hides everything below the top level. */
    fun collapseAllDirectories() {
        _collapsedDirs.value = FileTreeCollapse.allDirs(_fileEntries.value)
    }

    fun expandAllDirectories() {
        _collapsedDirs.value = emptySet()
    }

    private fun expandAncestors(parentRelative: String) {
        if (parentRelative.isBlank()) return
        var current = parentRelative
        val set = _collapsedDirs.value.toMutableSet()
        while (current.isNotEmpty()) {
            set.remove(current)
            current = current.substringBeforeLast('/', "")
        }
        _collapsedDirs.value = set
    }

    /** Toolbar / "New folder here" row action. */
    fun createFolderEntry(context: Context, rawName: String, parent: String? = null) {
        val appContext = context.applicationContext
        val base = ProjectPathUtils.sanitizeSegment(rawName.trim())
        if (base == null) {
            _userMessage.value = "Invalid folder name"
            return
        }
        val project = _projectName.value
        if (project != null) {
            val info = ProjectManager(appContext).project(project) ?: return
            val result = FileTreeRepository.createDirectory(info.root, parent.orEmpty(), base)
            if (result.isSuccess) {
                if (!parent.isNullOrBlank()) expandAncestors(parent)
                _userMessage.value = "Created folder $base"
            } else {
                _userMessage.value = "Could not create folder"
            }
        } else {
            val root = runCatching { FileManager(appContext).getProjectDir() }.getOrNull() ?: return
            val ok = runCatching { File(root, base).mkdirs() }.getOrDefault(false)
            _userMessage.value = if (ok) "Created folder $base" else "Could not create folder"
        }
        refreshFileEntries(appContext)
    }

    /**
     * Rename a drawer row (file OR folder). Open tabs keep following their
     * file — a folder rename re-prefixes every tab underneath it, undo
     * history included — so the buffer never points at a dead path.
     */
    fun renameFileEntry(context: Context, entry: EditorFileEntry, rawNewName: String) {
        val appContext = context.applicationContext
        val newName = ProjectPathUtils.sanitizeSegment(rawNewName.trim())
        if (newName == null) {
            _userMessage.value = appContext.getString(R.string.invalid_file_name)
            return
        }
        if (newName == entry.name) return
        val oldPath = entry.relativePath
        val project = entry.projectName
        if (project == null) {
            if (entry.isDirectory) {
                _userMessage.value = "Folders live inside projects"
                return
            }
            val ok = runCatching {
                FileManager(appContext).renameFile(oldPath, newName)
            }.getOrDefault(false)
            if (!ok) {
                _userMessage.value = appContext.getString(R.string.rename_failed)
                return
            }
            if (_activeTabPath.value == null && _fileName.value == oldPath) _fileName.value = newName
            refreshFileEntries(appContext)
            _userMessage.value = appContext.getString(R.string.rename_success)
            return
        }
        val info = ProjectManager(appContext).project(project) ?: return
        val newPath = FileTreeRepository.rename(info.root, oldPath, newName).getOrNull()
        if (newPath == null) {
            _userMessage.value = appContext.getString(R.string.rename_failed)
            return
        }
        fun remap(path: String): String? = when {
            path == oldPath -> newPath
            path.startsWith("$oldPath/") -> newPath + path.removePrefix(oldPath)
            else -> null
        }
        _openTabs.value = _openTabs.value.mapNotNull { tab ->
            remap(tab.relativePath)?.let { tab.copy(relativePath = it) } ?: tab
        }
        val remappedUndo = HashMap(undoManagers)
        undoManagers.clear()
        remappedUndo.forEach { (key, value) ->
            undoManagers[remap(key) ?: key] = value
        }
        _activeTabPath.value?.let { _activeTabPath.value = remap(it) ?: it }
        _fileName.value = remap(_fileName.value) ?: _fileName.value
        _launchDefault.value?.let { _launchDefault.value = remap(it) ?: it }
        runCatching { SettingsManager(appContext).replaceRecentFile(oldPath, newPath) }
        refreshFileEntries(appContext)
        _userMessage.value = appContext.getString(R.string.rename_success)
    }

    // ---- Phase 16: line endings + launch default --------------------------

    /**
     * LF ⇄ CRLF for the active project file: the in-memory buffer stays LF,
     * the tab's saved copy is rewritten on disk immediately (what "changing
     * line endings" means in every desktop editor), and the status-bar chip
     * reflects the new ending.
     */
    fun toggleLineEnding(context: Context) {
        val appContext = context.applicationContext
        val next = LineEndings.toggle(_activeLineEnding.value)
        _activeLineEnding.value = next
        val path = _activeTabPath.value
        val project = _projectName.value
        if (path == null || project == null) return
        updateTab(path) { it.copy(lineEnding = next) }
        val tab = _openTabs.value.firstOrNull { it.relativePath == path } ?: return
        if (!writeProjectFile(appContext, project, path, tab.savedText, next)) {
            _userMessage.value = appContext.getString(R.string.file_save_failed)
        }
    }

    /**
     * Phase 16 — Spck's "Launch default": persist the preview/Launch target in
     * the project config. Null clears it (the field is then omitted from the
     * JSON, keeping pre-Phase-16 config files byte-identical).
     */
    fun setLaunchDefault(context: Context, relativePath: String?) {
        val appContext = context.applicationContext
        val project = _projectName.value
        if (project == null) {
            _userMessage.value = "Launch default works inside a project — save this file into one first."
            return
        }
        val info = ProjectManager(appContext).project(project) ?: return
        val safe = if (relativePath == null) null else ProjectPathUtils.sanitizeRelativePath(relativePath)
        if (relativePath != null && safe == null) {
            _userMessage.value = "Invalid path"
            return
        }
        val result = runCatching { ProjectManager(appContext).writeConfig(info.root, info.config.copy(launchDefault = safe)) }
        if (result.isFailure) {
            _userMessage.value = "Could not update the project config"
            return
        }
        _launchDefault.value = safe
        _userMessage.value = if (safe == null) "Launch default cleared" else "Launch default: $safe"
        refreshFileEntries(appContext)
    }

    // ---- Phase 16: drawer git metadata ------------------------------------

    /**
     * Branch chip + M/A/D/? tree letters + change count for the drawer, read
     * best-effort off the main thread: the branch comes straight from
     * `.git/HEAD` (no process), the porcelain status only runs while the
     * packaged git is available — the same guardrail as the Projects hub.
     */
    fun refreshGitMeta(context: Context) {
        val appContext = context.applicationContext
        val project = _projectName.value
        if (project == null) {
            _gitBranch.value = null
            _gitBadges.value = emptyMap()
            _gitChangeCount.value = 0
            return
        }
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) {
                runCatching {
                    val root = ProjectManager(appContext).project(project)?.root ?: return@runCatching null
                    val gitDir = File(root, ".git")
                    if (!gitDir.exists()) return@runCatching null
                    val branch = runCatching {
                        ProjectsHub.branchFromHeadFile(
                            File(gitDir, "HEAD").takeIf { it.isFile }?.readText()
                        )
                    }.getOrNull()
                    val files = runCatching { GitContext(appContext).manager()?.status(root)?.files }.getOrNull()
                    Triple(branch, files?.let(ProjectsHub::fileBadges), files?.size)
                }.getOrNull()
            }
            if (_projectName.value != project) return@launch
            _gitBranch.value = meta?.first
            _gitBadges.value = meta?.second ?: emptyMap()
            _gitChangeCount.value = meta?.third ?: 0
        }
    }

    private fun writeProjectFile(
        context: Context,
        project: String,
        relativePath: String,
        text: String,
        lineEnding: String = LineEndings.LF
    ): Boolean {
        val info = ProjectManager(context).project(project) ?: return false
        val safe = ProjectPathUtils.sanitizeRelativePath(relativePath) ?: return false
        val file = ProjectPathUtils.resolveInside(info.root, safe) ?: return false
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(LineEndings.toNative(text, lineEnding))
        }.isSuccess
    }

    fun saveFile(context: Context): Boolean {
        val text = _codeText.value.text
        val project = _projectName.value
        if (project != null) {
            val safe = ProjectPathUtils.sanitizeRelativePath(_fileName.value) ?: return false
            if (!writeProjectFile(context, project, safe, text, _activeLineEnding.value)) return false
            _fileName.value = safe
            if (_activeTabPath.value == safe) {
                updateTab(safe) { it.copy(savedText = text, lineEnding = _activeLineEnding.value) }
            }
            _isDirty.value = false
            return true
        }
        // Phase 12: a never-named scratch buffer (the app's default
        // main.c/untitled.c, still holding the untouched starter) whose
        // content is clearly Python is saved as <name>.py so RUN ▶ routes it
        // through python3 instead of cc. Only fires for a buffer the user
        // has not explicitly named and never saved; anything else keeps the
        // existing naming exactly.
        val currentName = _fileName.value
        val untouchedDefault = (currentName == "main.c" || currentName == "untitled.c") &&
            scratchSavedText == INITIAL_CODE
        val nameToSave = if (untouchedDefault && WebFileSupport.looksLikePython(text)) {
            currentName.substringBeforeLast('.') + ".py"
        } else {
            currentName
        }
        val safe = FileNameUtils.sanitizeFileName(nameToSave) ?: return false
        val fm = FileManager(context)
        val success = fm.saveFile(safe, text)
        if (success) {
            _fileName.value = WebFileSupport.normalizeFileName(safe)
            scratchSavedText = text
            _isDirty.value = false
        }
        return success
    }

    fun saveAllTabs(context: Context) {
        if (_activeTabPath.value == null) {
            saveFile(context)
            return
        }
        stashActiveTabBuffer(_codeText.value)
        val project = _projectName.value
        if (project == null) {
            saveFile(context)
            return
        }
        var failures = 0
        _openTabs.value = _openTabs.value.map { tab ->
            when {
                tab.buffer.text == tab.savedText -> tab
                writeProjectFile(context, project, tab.relativePath, tab.buffer.text, tab.lineEnding) ->
                    tab.copy(savedText = tab.buffer.text)
                else -> { failures++; tab }
            }
        }
        _isDirty.value = computeDirty(_codeText.value.text)
        _userMessage.value = context.getString(
            if (failures == 0) R.string.file_saved else R.string.file_save_failed
        )
    }

    fun reloadActiveTab(context: Context) {
        val project = _projectName.value
        if (project != null) {
            val path = _activeTabPath.value ?: return
            val info = ProjectManager(context).project(project) ?: return
            val file = ProjectPathUtils.resolveInside(info.root, path) ?: return
            if (!file.isFile || !file.canRead()) return
            val content = runCatching { file.readText() }.getOrNull() ?: return
            val tab = EditorTab(path, TextFieldValue(content), content)
            updateTab(path) { tab }
            _fileName.value = path
            _codeText.value = tab.buffer
            _isDirty.value = false
        } else {
            val fm = FileManager(context)
            val content = fm.loadFile(_fileName.value) ?: return
            scratchSavedText = content
            _codeText.value = TextFieldValue(content)
            _isDirty.value = false
        }
        undoManager().reset()
        syncUndoFlags(undoManager())
        _diagnostics.value = emptyList()
        resetDecorationsForNewBuffer()
        _userMessage.value = context.getString(R.string.reloaded_from_disk)
    }

    // ---------------------------------------------------------------------
    // Format
    // ---------------------------------------------------------------------

    fun formatCode(context: Context, tabSize: Int) {
        if (_formatting.value) return
        _formatting.value = true
        viewModelScope.launch {
            try {
                val before = _codeText.value
                val clangText = ClangFormatBridge.format(context, before.text)
                val usedClangFormat = clangText != null
                val formatted = clangText ?: CodeFormatter.format(before.text, tabSize)
                if (formatted == before.text) {
                    _userMessage.value = context.getString(R.string.already_formatted)
                } else {
                    val cursor = CodeFormatter.mapCursor(before.text, formatted, before.selection.min)
                    applyBufferEdit(
                        before,
                        TextFieldValue(formatted, TextRange(cursor.coerceIn(0, formatted.length)))
                    )
                    _userMessage.value = context.getString(
                        if (usedClangFormat) R.string.formatted_clang_format else R.string.formatted_builtin
                    )
                }
            } finally {
                _formatting.value = false
            }
        }
    }

    // ---------------------------------------------------------------------
    // Find & replace
    // ---------------------------------------------------------------------

    fun showFind() {
        val current = _codeText.value
        val selection = current.selection
        val seed = if (!selection.collapsed) {
            current.text.substring(selection.min, selection.max).lineSequence().firstOrNull()?.take(120).orEmpty()
        } else {
            ""
        }
        _find.value = _find.value.copy(visible = true, query = seed)
        runFind()
    }

    fun hideFind() {
        _find.value = _find.value.copy(visible = false, matches = emptyList(), activeIndex = -1, error = null)
    }

    fun setFindQuery(query: String) {
        _find.value = _find.value.copy(query = query)
        runFind()
    }

    fun setFindReplacement(replacement: String) {
        _find.value = _find.value.copy(replacement = replacement)
    }

    fun toggleFindMatchCase() {
        _find.value.let { state ->
            _find.value = state.copy(options = state.options.copy(matchCase = !state.options.matchCase))
        }
        runFind()
    }

    fun toggleFindWholeWord() {
        _find.value.let { state ->
            _find.value = state.copy(options = state.options.copy(wholeWord = !state.options.wholeWord))
        }
        runFind()
    }

    fun toggleFindRegex() {
        _find.value.let { state ->
            _find.value = state.copy(options = state.options.copy(regex = !state.options.regex))
        }
        runFind()
    }

    private fun runFind() {
        val state = _find.value
        if (!state.visible || state.query.isEmpty()) {
            _find.value = state.copy(matches = emptyList(), activeIndex = -1, error = null)
            return
        }
        when (val outcome = FindReplaceEngine.search(_codeText.value.text, state.query, state.options)) {
            is FindOutcome.Success -> {
                val cursor = _codeText.value.selection.min
                val active = if (outcome.matches.isEmpty()) {
                    -1
                } else {
                    FindReplaceEngine.indexForCursor(outcome.matches, cursor)
                }
                _find.value = state.copy(matches = outcome.matches, activeIndex = active, error = null)
            }
            is FindOutcome.InvalidPattern -> {
                _find.value = state.copy(matches = emptyList(), activeIndex = -1, error = outcome.message)
            }
        }
    }

    fun findNext() {
        val state = _find.value
        jumpToMatch(FindReplaceEngine.nextIndex(state.activeIndex, state.matches.size))
    }

    fun findPrev() {
        val state = _find.value
        jumpToMatch(FindReplaceEngine.prevIndex(state.activeIndex, state.matches.size))
    }

    private fun jumpToMatch(index: Int) {
        val state = _find.value
        if (index < 0 || index >= state.matches.size) return
        _find.value = state.copy(activeIndex = index)
        val match = state.matches[index]
        selectRegion(match.first, match.last + 1)
    }

    private fun selectRegion(start: Int, end: Int) {
        val text = _codeText.value.text
        val clampedStart = start.coerceIn(0, text.length)
        val clampedEnd = end.coerceIn(clampedStart, text.length)
        _codeText.value = TextFieldValue(text, TextRange(clampedStart, clampedEnd))
        refreshDecorationsNow()
    }

    fun replaceCurrent() {
        val state = _find.value
        val match = state.matches.getOrNull(state.activeIndex) ?: return
        val old = _codeText.value
        if (state.options.regex) {
            val result = FindReplaceEngine.replaceFirstRegexFrom(
                old.text, match.first, state.query, state.replacement, state.options.matchCase
            ) ?: return
            applyBufferEdit(old, TextFieldValue(result.first, TextRange(result.second)))
        } else {
            val newText = FindReplaceEngine.replaceOne(old.text, match, state.replacement)
            applyBufferEdit(old, TextFieldValue(newText, TextRange(match.first + state.replacement.length)))
        }
        runFind()
    }

    fun replaceAll(context: Context) {
        val state = _find.value
        if (state.matches.isEmpty()) return
        val old = _codeText.value
        val count = state.matches.size
        val newText = if (state.options.regex) {
            FindReplaceEngine.replaceAllRegex(old.text, state.query, state.replacement, state.options.matchCase)
                ?: return
        } else {
            FindReplaceEngine.replaceAllLiteral(old.text, state.matches, state.replacement)
        }
        applyBufferEdit(old, TextFieldValue(newText, TextRange(old.selection.min.coerceAtMost(newText.length))))
        runFind()
        _userMessage.value = context.getString(R.string.replace_all_done, count)
    }

    // ---------------------------------------------------------------------
    // Diagnostics
    // ---------------------------------------------------------------------

    fun clearDiagnostics() {
        _diagnostics.value = emptyList()
    }

    fun jumpToDiagnostic(diagnostic: EditorDiagnostic) {
        val text = _codeText.value.text
        val lineStart = CodeFormatter.lineStartOffset(text, diagnostic.line)
        val offset = (lineStart + (diagnostic.column - 1)).coerceIn(0, text.length)
        selectRegion(offset, offset)
    }

    /** Phase 16 — "Go to line" from the overflow menu: moves the caret to the
     * start of [line] (clamped to the buffer), same select-then-highlight
     * path the diagnostics jump uses. */
    fun jumpToLine(line: Int) {
        val text = _codeText.value.text
        val target = line.coerceIn(1, text.count { it == '\n' } + 1)
        val offset = CodeFormatter.lineStartOffset(text, target)
        selectRegion(offset, offset)
    }

    /** Returns true when the buffer was modified by the fix. */
    fun applyQuickFix(diagnostic: EditorDiagnostic): Boolean {
        if (CompilerDiagnostics.semicolonFixLabel(diagnostic) == null) return false
        // Direct fix on the reported line (the usual clang case).
        val applied = applySemicolonFixToLine(diagnostic.line) ||
            // Device evidence 2026-08-30: TCC reports `';' expected (got "}")`
            // at the closing-brace line, but the missing ';' belongs to the
            // line above. Fix that line when the reported one is a brace.
            applySemicolonFixToLine(diagnostic.line - 1)
        if (applied) {
            _diagnostics.value = _diagnostics.value.filterNot { it.line == diagnostic.line }
        }
        return applied
    }

    private fun applySemicolonFixToLine(line: Int): Boolean {
        if (line < 1) return false
        val bounds = CodeFormatter.lineBounds(_codeText.value.text, line) ?: return false
        if (bounds.isEmpty()) return false
        val text = _codeText.value.text
        val lineText = text.substring(bounds.first, bounds.last + 1)
        // Only the direct-line fix may proceed when the reported line itself
        // is a closing brace — a `}` can never take the ';'.
        if (lineText.trimEnd().endsWith("}")) return false
        val fixed = CompilerDiagnostics.applySemicolonFix(lineText) ?: return false
        val newText = text.substring(0, bounds.first) + fixed + text.substring(bounds.last + 1)
        applyBufferEdit(_codeText.value, TextFieldValue(newText, TextRange(bounds.first + fixed.length)))
        return true
    }

    // ---------------------------------------------------------------------
    // Phase 11: Output Panel & Integrated Run
    // ---------------------------------------------------------------------

    fun toggleOutput() {
        _outputExpanded.value = !_outputExpanded.value
    }

    /** Clears the panel. A still-running pipeline is cancelled first. */
    fun clearOutput() {
        runJob?.cancel()
        runJob = null
        activeRunner = null
        interactiveRun?.stop()
        interactiveRun = null
        serverRunJob?.cancel()
        serverRunJob = null
        activeServer?.stop()
        activeServer = null
        _outputState.value = OutputRunState(phase = OutputPhase.IDLE)
    }

    /** Stops the running build/run pipeline and kills the live process. */
    fun stopRun() {
        runJob?.cancel()
        runJob = null
        activeRunner = null
        interactiveRun?.stop()
        interactiveRun = null
        serverRunJob?.cancel()
        serverRunJob = null
        activeServer?.stop()
        activeServer = null
        val current = _outputState.value
        if (current.busy) {
            _outputState.value = current.copy(
                phase = OutputPhase.CANCELLED,
                busy = false,
                summary = "Stopped",
                lines = current.lines + OutputLine("Stopped by user", OutputLineKind.SYSTEM)
            )
        }
    }

    /**
     * Phase 11 — the RUN entry point. Saves the active buffer, then builds and
     * executes through the app's real toolchain (`cc` frontend over the
     * embedded TCC, exactly like the terminal) streaming into the Output
     * Panel. Project contexts run their project.json build/run configuration;
     * single files compile in place (`cc <file> -o a.out && ./a.out`).
     */
    fun runActiveFile(context: Context) {
        if (_outputState.value.busy) return
        val appContext = context.applicationContext
        if (!saveFile(appContext)) {
            _userMessage.value = appContext.getString(R.string.file_save_failed)
            return
        }
        _diagnostics.value = emptyList()

        val project = _projectName.value
        val workDir: File
        val buildCommand: String?
        val runCommand: String?
        val terminalCommand: String
        if (project != null) {
            val info = ProjectManager(appContext).project(project)
            if (info == null) {
                _userMessage.value = "Project '$project' is gone"
                return
            }
            // Web projects are handled by the preview flow, not the panel.
            if (info.config.type.equals("web", ignoreCase = true)) return
            // Phase 14 — server presets: build once, then run as a long-lived
            // background server and auto-open Web Preview on the detected URL.
            if (info.config.isServerType()) {
                startServerRun(appContext, info)
                return
            }
            // Phase 14 — Auto projects: no type selection at creation; RUN ▶
            // infers the type from the files (active file first). Server and
            // web plans are terminal; c/python fall through to the normal
            // active-file run path with the preset as the project fallback.
            var config = info.config
            if (config.type.equals("auto", ignoreCase = true)) {
                val activeRelForDetect = ProjectPathUtils.sanitizeRelativePath(_fileName.value)
                when (val plan = ProjectRunDetector.detect(info.root, activeRelForDetect)) {
                    is AutoRunPlan.Server -> {
                        config = ProjectConfig.defaultFor(info.name, plan.type)
                        startServerRun(appContext, info.copy(config = config))
                        return
                    }
                    is AutoRunPlan.Web -> {
                        webPreviewHandler?.invoke(plan.entry)
                        return
                    }
                    is AutoRunPlan.Project -> config = ProjectConfig.defaultFor(info.name, plan.type)
                    is AutoRunPlan.None -> {
                        _userMessage.value = plan.message
                        return
                    }
                }
            }
            workDir = info.root
            // Phase 12 (device-found): RUN ▶ executes the ACTIVE file, not
            // the project's configured main. A .py active file runs with
            // python3; a .c/.cpp active file compiles with cc into bin/ and
            // runs — exactly like the tree's per-file "Run in terminal".
            // The project.json build/run still drives everything else
            // (headers, text, custom multi-file builds).
            val activeRel = ProjectPathUtils.sanitizeRelativePath(_fileName.value)
            val activeLang = activeRel?.let { LanguageType.fromFileName(it) }
            val activeFile = activeRel?.let { ProjectPathUtils.resolveInside(info.root, it) }
            if (activeLang == LanguageType.PYTHON && activeFile != null) {
                val (build, run) = TerminalHandoff.interpretedParts(activeFile.absolutePath)
                buildCommand = build
                runCommand = run
                terminalCommand = TerminalHandoff.interpretedRunCommand(activeFile.absolutePath)
            } else if ((activeLang == LanguageType.C || activeLang == LanguageType.CPP) && activeRel != null) {
                val (build, run, terminal) = TerminalHandoff.projectFileParts(info.root, activeRel)
                buildCommand = build
                runCommand = run
                terminalCommand = terminal
            } else {
                val (build, run) = TerminalHandoff.projectRunParts(workDir.absolutePath, config)
                buildCommand = build
                runCommand = run
                terminalCommand = TerminalHandoff.projectRunCommand(workDir.absolutePath, config)
            }
        } else {
            val path = saveAndAbsolutePath(appContext) ?: return
            val source = File(path)
            workDir = source.parentFile ?: File(appContext.filesDir, "CodeC/projects")
            // Phase 12: script files (.py) run directly with python3 — there
            // is no compile step, so the panel reports RUNNING immediately.
            val isPython = LanguageType.fromFileName(source.name) == LanguageType.PYTHON
            if (isPython) {
                val (build, run) = TerminalHandoff.interpretedParts(path)
                buildCommand = build
                runCommand = run
                terminalCommand = TerminalHandoff.interpretedRunCommand(path)
            } else {
                val (build, run) = TerminalHandoff.compileParts(path)
                buildCommand = build
                runCommand = run
                terminalCommand = TerminalHandoff.compileAndRunCommand(path)
            }
        }
        if (buildCommand.isNullOrBlank() && runCommand.isNullOrBlank()) {
            _userMessage.value = appContext.getString(R.string.output_no_command)
            return
        }

        _outputExpanded.value = true
        buildOutputBuffer = StringBuilder()
        val startLines = buildList {
            if (!buildCommand.isNullOrBlank()) {
                add(OutputLine("$ ${buildCommand.trim()}", OutputLineKind.COMMAND))
            } else if (!runCommand.isNullOrBlank()) {
                add(OutputLine("$ ${runCommand.trim()}", OutputLineKind.COMMAND))
            }
        }
        _outputState.value = OutputRunState(
            phase = if (buildCommand.isNullOrBlank()) OutputPhase.RUNNING else OutputPhase.BUILDING,
            busy = true,
            lines = startLines,
            summary = appContext.getString(
                if (buildCommand.isNullOrBlank()) R.string.output_running else R.string.output_compiling
            ),
            lastTerminalCommand = terminalCommand
        )
        runJob = viewModelScope.launch {
            StatsManager(appContext).incrementRuns()
            val settings = compilerSettingsFrom(SettingsManager(appContext))
            val prepared = withContext(Dispatchers.IO) {
                ShellBootstrap(appContext).prepare(settings)
            }
            val runner = ExecutionRunner(prepared.shell, prepared.env)
            val runFinished = CompletableDeferred<Int>()
            try {
                // 1) Build phase — batch, piped (a failing build stops here).
                if (!buildCommand.isNullOrBlank()) {
                    runner.run(RunSpec(workDir, buildCommand, null)).collect { event ->
                        when (event) {
                            is RunEvent.PhaseChanged -> {
                                _outputState.value = _outputState.value.copy(
                                    phase = OutputPhase.BUILDING,
                                    summary = appContext.getString(R.string.output_compiling)
                                )
                            }
                            is RunEvent.Output -> {
                                buildOutputBuffer.append(event.line).append('\n')
                                appendOutputLine(OutputLine(event.line, OutputLineKind.BUILD))
                            }
                            is RunEvent.BuildFinished -> {
                                _outputState.value = _outputState.value.copy(
                                    buildExitCode = event.exitCode,
                                    buildDurationMs = event.durationMs
                                )
                                if (event.exitCode != 0) {
                                    finishFailedBuild(
                                        appContext, event.exitCode, event.durationMs, event.timedOut
                                    )
                                } else {
                                    appendOutputLine(
                                        OutputLine(
                                            appContext.getString(
                                                R.string.output_build_ok_time, event.durationMs
                                            ),
                                            OutputLineKind.STATS
                                        )
                                    )
                                }
                            }
                            is RunEvent.RunFinished -> {
                                // Never emitted: the build spec has no run command.
                            }
                            is RunEvent.Failed -> {
                                failRun(appContext, event.message)
                            }
                        }
                    }
                    if (_outputState.value.buildExitCode != 0) return@launch
                }

                // 2) Run phase — interactive PTY when available (per-prompt
                //    input, line-buffered prompts), piped fallback otherwise.
                if (runCommand.isNullOrBlank()) {
                    _outputState.value = _outputState.value.copy(
                        phase = OutputPhase.DONE,
                        busy = false,
                        summary = appContext.getString(
                            R.string.output_build_ok_time, _outputState.value.buildDurationMs ?: 0L
                        )
                    )
                    return@launch
                }
                _outputState.value = _outputState.value.copy(
                    phase = OutputPhase.RUNNING,
                    summary = appContext.getString(R.string.output_running)
                )
                val runStart = System.currentTimeMillis()
                val interactive = InteractiveRunSession.start(
                    command = runCommand,
                    workDir = workDir,
                    env = prepared.env,
                    shellFile = prepared.shell,
                    onOutput = { text, partial ->
                        appendOutputLine(OutputLine(text, OutputLineKind.OUTPUT, partial))
                    },
                    onExit = { exitCode -> runFinished.complete(exitCode) }
                )
                if (interactive != null) {
                    interactiveRun = interactive
                    val exitCode = runFinished.await()
                    interactiveRun = null
                    finishRun(appContext, exitCode, System.currentTimeMillis() - runStart, timedOut = false)
                } else {
                    // PTY unavailable — piped fallback with the run timeout.
                    activeRunner = runner
                    runner.run(RunSpec(workDir, null, runCommand)).collect { event ->
                        when (event) {
                            is RunEvent.PhaseChanged -> {
                                _outputState.value = _outputState.value.copy(
                                    phase = OutputPhase.RUNNING,
                                    summary = appContext.getString(R.string.output_running)
                                )
                            }
                            is RunEvent.Output -> {
                                appendOutputLine(OutputLine(event.line, OutputLineKind.OUTPUT))
                            }
                            is RunEvent.RunFinished -> {
                                val exitCode = if (event.timedOut) {
                                    ExecutionRunner.TIMED_OUT_EXIT_CODE
                                } else {
                                    event.exitCode
                                }
                                finishRun(appContext, exitCode, event.durationMs, event.timedOut)
                            }
                            is RunEvent.BuildFinished -> {
                                // Never emitted: the run spec has no build command.
                            }
                            is RunEvent.Failed -> {
                                failRun(appContext, event.message)
                            }
                        }
                    }
                    activeRunner = null
                }
            } catch (e: CancellationException) {
                // Stop pressed: stopRun() already updated the state; kill the
                // interactive PTY session if one is alive.
                interactiveRun?.stop()
                interactiveRun = null
                throw e
            } catch (e: Exception) {
                failRun(appContext, e.message ?: "Run failed")
            }
        }
    }

    /**
     * Phase 14 — RUN ▶ for server-type projects. Builds when a build step is
     * configured (c-microservice), then starts the run command as a long-lived
     * background process via [ServerRunner] and streams its output into the
     * Output Panel. When the server prints its bind line the panel summary
     * updates and the [serverReadyHandler] opens Web Preview on that URL.
     * Stop kills the server; the Open-in-Terminal escape hatch stays.
     */
    private fun startServerRun(context: Context, info: ProjectInfo) {
        val config = info.config
        val buildCommand = config.build.trim().takeIf { it.isNotEmpty() }
        val runCommand = config.run.trim().takeIf { it.isNotEmpty() }
        if (buildCommand == null && runCommand == null) {
            _userMessage.value = context.getString(R.string.output_no_command)
            return
        }
        _outputExpanded.value = true
        buildOutputBuffer = StringBuilder()
        val startLines = buildList {
            buildCommand?.let { add(OutputLine("$ $it", OutputLineKind.COMMAND)) }
            runCommand?.let { add(OutputLine("$ $it", OutputLineKind.COMMAND)) }
        }
        _outputState.value = OutputRunState(
            phase = if (buildCommand != null) OutputPhase.BUILDING else OutputPhase.RUNNING,
            busy = true,
            serverRun = true,
            lines = startLines,
            summary = context.getString(
                if (buildCommand != null) R.string.output_compiling else R.string.output_starting_server
            ),
            lastTerminalCommand = TerminalHandoff.projectRunCommand(info.root.absolutePath, config)
        )
        serverRunJob = viewModelScope.launch {
            StatsManager(context).incrementRuns()
            val settings = compilerSettingsFrom(SettingsManager(context))
            val prepared = withContext(Dispatchers.IO) {
                ShellBootstrap(context).prepare(settings)
            }
            try {
                // 1) Optional build phase — identical to the normal pipeline.
                if (buildCommand != null) {
                    val runner = ExecutionRunner(prepared.shell, prepared.env)
                    var failed = false
                    runner.run(RunSpec(info.root, buildCommand, null)).collect { event ->
                        when (event) {
                            is RunEvent.PhaseChanged -> {
                                _outputState.value = _outputState.value.copy(
                                    phase = OutputPhase.BUILDING,
                                    summary = context.getString(R.string.output_compiling)
                                )
                            }
                            is RunEvent.Output -> {
                                buildOutputBuffer.append(event.line).append('\n')
                                appendOutputLine(OutputLine(event.line, OutputLineKind.BUILD))
                            }
                            is RunEvent.BuildFinished -> {
                                _outputState.value = _outputState.value.copy(
                                    buildExitCode = event.exitCode,
                                    buildDurationMs = event.durationMs
                                )
                                if (event.exitCode != 0) {
                                    failed = true
                                    finishFailedBuild(context, event.exitCode, event.durationMs, event.timedOut)
                                } else {
                                    appendOutputLine(
                                        OutputLine(
                                            context.getString(R.string.output_build_ok_time, event.durationMs),
                                            OutputLineKind.STATS
                                        )
                                    )
                                }
                            }
                            is RunEvent.Failed -> {
                                failed = true
                                failRun(context, event.message)
                            }
                            else -> Unit
                        }
                    }
                    if (failed || _outputState.value.buildExitCode != 0) return@launch
                }

                // 2) Server phase — long-lived; the flow completes only when
                //    the child exits (or the user cancels the collection).
                _outputState.value = _outputState.value.copy(
                    phase = OutputPhase.RUNNING,
                    summary = context.getString(R.string.output_starting_server)
                )
                val server = ServerRunner(
                    shell = prepared.shell,
                    environment = prepared.env,
                    command = runCommand.orEmpty(),
                    workDir = info.root
                )
                activeServer = server
                server.start().collect { event ->
                    when (event) {
                        is ServerEvent.Output -> appendOutputLine(
                            OutputLine(event.line, OutputLineKind.OUTPUT)
                        )
                        is ServerEvent.Ready -> {
                            _outputState.value = _outputState.value.copy(
                                serverUrl = event.url,
                                summary = context.getString(R.string.output_server_running_at, event.url)
                            )
                            serverReadyHandler?.invoke(event.url)
                        }
                        is ServerEvent.ReadyTimeout -> {
                            _outputState.value = _outputState.value.copy(
                                serverUrl = _outputState.value.serverUrl ?: config.serverPreviewUrl(),
                                summary = context.getString(
                                    R.string.output_server_no_url,
                                    event.message
                                )
                            )
                        }
                        is ServerEvent.Exited -> finishServerExit(context, event.exitCode)
                        is ServerEvent.Failed -> failRun(context, event.message)
                    }
                }
            } catch (e: CancellationException) {
                // Stop pressed: stopRun() already updated the state; kill the
                // server process, then propagate.
                activeServer?.stop()
                activeServer = null
                throw e
            } catch (e: Exception) {
                failRun(context, e.message ?: "Server run failed")
            } finally {
                activeServer = null
                serverRunJob = null
            }
        }
    }

    /** Phase 14 — a background server ended on its own. Honest summary per exit code. */
    private fun finishServerExit(context: Context, exitCode: Int) {
        val ok = exitCode == 0
        _outputState.value = _outputState.value.copy(
            phase = if (ok) OutputPhase.DONE else OutputPhase.FAILED,
            busy = false,
            serverUrl = _outputState.value.serverUrl,
            summary = context.getString(R.string.output_server_exited, exitCode),
            lines = _outputState.value.lines + OutputLine(
                "Server exited with code $exitCode",
                if (ok) OutputLineKind.STATS else OutputLineKind.ERROR
            )
        )
    }

    /** Appends a line, replacing a trailing partial prompt when it updates. */
    private fun appendOutputLine(line: OutputLine) {
        val current = _outputState.value
        val lines = current.lines
        val next = if (line.partial && lines.lastOrNull()?.partial == true) {
            lines.dropLast(1) + line
        } else {
            lines + line
        }
        _outputState.value = current.copy(lines = next)
    }

    private fun failRun(context: Context, message: String) {
        _outputState.value = _outputState.value.copy(
            phase = OutputPhase.FAILED,
            busy = false,
            summary = context.getString(R.string.output_failed, message),
            lines = _outputState.value.lines + OutputLine(message, OutputLineKind.ERROR)
        )
    }

    /** Finalizes a finished run phase (interactive exit or piped fallback). */
    private fun finishRun(context: Context, exitCode: Int, durationMs: Long, timedOut: Boolean) {
        val summary = if (timedOut) {
            context.getString(R.string.output_timed_out)
        } else {
            context.getString(R.string.output_exit_code, exitCode)
        }
        // Device evidence 2026-08-30: a piped `scanf` program blocks at its
        // prompt until the run timeout. Say so honestly and point at the
        // interactive path (PTY runs never time out — Stop is the escape).
        val finalLines = if (timedOut) {
            listOf(
                OutputLine(summary, OutputLineKind.ERROR),
                OutputLine(
                    context.getString(R.string.output_timed_out_input_hint),
                    OutputLineKind.SYSTEM
                )
            )
        } else {
            listOf(
                OutputLine(
                    "Process finished with exit code $exitCode (${durationMs}ms)",
                    OutputLineKind.STATS
                )
            )
        }
        _outputState.value = _outputState.value.copy(
            phase = OutputPhase.DONE,
            busy = false,
            runExitCode = exitCode,
            runDurationMs = durationMs,
            summary = summary,
            lines = _outputState.value.lines + finalLines
        )
    }

    /**
     * A failing build ends the run: re-color diagnostic lines red, summarize,
     * and feed the squiggle layer with the diagnostics for the active file.
     */
    private fun finishFailedBuild(context: Context, exitCode: Int, durationMs: Long, timedOut: Boolean) {
        val current = _outputState.value
        val summary = if (timedOut) {
            context.getString(R.string.output_compile_timed_out)
        } else {
            context.getString(R.string.output_build_failed, exitCode)
        }
        val reColored = current.lines.map { line ->
            if (line.kind == OutputLineKind.BUILD && OutputLineParser.parseLine(line.text) != null) {
                line.copy(kind = OutputLineKind.ERROR)
            } else {
                line
            }
        }
        _outputState.value = current.copy(
            phase = OutputPhase.DONE,
            busy = false,
            summary = summary,
            lines = reColored + OutputLine(summary, OutputLineKind.ERROR)
        )
        _diagnostics.value = CompilerDiagnostics.parse(
            buildOutputBuffer.toString(),
            _fileName.value.substringAfterLast('/')
        )
    }

    /**
     * Tap on a clickable diagnostic line in the Output Panel: open the file
     * (when it is not already the active tab) and move the editor cursor to
     * the reported line/column. Paths are confined to the current project
     * root (or the single-files folder) — a diagnostic naming a file outside
     * the active context is ignored.
     */
    fun jumpToOutputDiagnostic(context: Context, diagnostic: OutputDiagnostic) {
        if (!openOutputDiagnosticFile(context, diagnostic)) return
        jumpToDiagnostic(toEditorDiagnostic(diagnostic))
    }

    /**
     * Phase 11 — "Write a code to apply": one tap applies the automatic quick
     * fix for the tapped diagnostic (currently the missing-`;` fix) in the
     * file it belongs to. Opens/jumps to the file first, then applies the fix
     * to the active buffer via the Phase 9 machinery.
     */
    fun applyFixForOutputDiagnostic(context: Context, diagnostic: OutputDiagnostic) {
        if (!openOutputDiagnosticFile(context, diagnostic)) return
        val editorDiagnostic = toEditorDiagnostic(diagnostic)
        jumpToDiagnostic(editorDiagnostic)
        if (CompilerDiagnostics.semicolonFixLabel(editorDiagnostic) == null) {
            _userMessage.value = "No automatic fix for this error"
            return
        }
        if (applyQuickFix(editorDiagnostic)) {
            _userMessage.value = "Added missing ';' — tap RUN to rebuild"
        } else {
            _userMessage.value = "No automatic fix for this error"
        }
    }

    /** Opens the file a diagnostic names (confined to the active folder). */
    private fun openOutputDiagnosticFile(context: Context, diagnostic: OutputDiagnostic): Boolean {
        val appContext = context.applicationContext
        val project = _projectName.value
        val root = if (project != null) {
            runCatching { ProjectManager(appContext).project(project)?.root }.getOrNull()
        } else {
            runCatching { FileManager(appContext).getProjectDir() }.getOrNull()
        }
        val file = root?.let { resolveDiagnosticFile(it, diagnostic.file) } ?: return false
        val relative = runCatching { root.toRelativeString(file) }.getOrNull() ?: return false
        if (relative.startsWith("..")) return false

        return if (project != null) {
            openFile(appContext, project, relative)
            _fileName.value == relative
        } else {
            if (file.name != _fileName.value) openFile(appContext, null, file.name)
            _fileName.value == file.name
        }
    }

    private fun toEditorDiagnostic(diagnostic: OutputDiagnostic): EditorDiagnostic =
        EditorDiagnostic(
            line = diagnostic.line,
            column = diagnostic.column.coerceAtLeast(1),
            message = diagnostic.message,
            severity = if (diagnostic.isError) {
                DiagnosticSeverity.ERROR
            } else {
                DiagnosticSeverity.WARNING
            }
        )

    /**
     * Resolves the file named by a diagnostic against [root]. Absolute paths
     * must live under the root; relative paths are resolved inside it.
     */
    private fun resolveDiagnosticFile(root: File, raw: String): File? {
        val candidate = if (raw.startsWith('/')) {
            File(raw)
        } else {
            File(root, raw)
        }
        if (!candidate.isFile) return null
        val rootPath = root.absolutePath
        val candidatePath = candidate.absolutePath
        if (candidatePath != rootPath && !candidatePath.startsWith(rootPath + File.separator)) return null
        return candidate
    }

    private suspend fun compilerSettingsFrom(settingsManager: SettingsManager): CompilerSettings {
        val standard = settingsManager.cStandardFlow.first()
        val warningLevel = settingsManager.warningLevelFlow.first()
        val optimization = settingsManager.optimizationLevelFlow.first()
        return CompilerSettings(
            cStandard = standard.lowercase().removePrefix("c").let { "c$it" },
            warnings = !warningLevel.equals("None", ignoreCase = true),
            optimization = optimization.filter { it.isDigit() }.toIntOrNull() ?: 0
        )
    }

    /**
     * Persists the buffer and returns the absolute path so the terminal
     * handoff can `cc` the file. Null when the name is illegal or save fails.
     */
    fun saveAndAbsolutePath(context: Context): String? {
        if (!saveFile(context)) return null
        val project = _projectName.value
        if (project != null) {
            val info = ProjectManager(context).project(project) ?: return null
            val safe = ProjectPathUtils.sanitizeRelativePath(_fileName.value) ?: return null
            return ProjectPathUtils.resolveInside(info.root, safe)?.absolutePath
        }
        val safe = FileNameUtils.sanitizeFileName(_fileName.value) ?: return null
        return java.io.File(FileManager(context).getProjectDir(), safe).absolutePath
    }

    // ---------------------------------------------------------------------
    // Decorations (current line, bracket pairs, find highlights)
    // ---------------------------------------------------------------------

    private fun scheduleDecorationRefresh() {
        decorationJob?.cancel()
        decorationJob = viewModelScope.launch {
            delay(20)
            refreshDecorationsNow()
        }
    }

    private fun refreshDecorationsNow() {
        val current = _codeText.value
        val cursor = current.selection.min.coerceIn(0, current.text.length)
        val line = current.text.take(cursor).count { it == '\n' } + 1
        val lineStart = CodeFormatter.lineStartOffset(current.text, line)
        _cursorPos.value = EditorCursorPos(line, cursor - lineStart + 1, current.selection.length)
        _currentLineRange.value = CodeFormatter.lineBounds(current.text, line)?.takeIf { !it.isEmpty() }
        _bracketRanges.value = if (
            current.text.length <= BracketMatcher.MAX_SCAN_LENGTH &&
            (cursor in current.text.indices || cursor - 1 in current.text.indices)
        ) {
            runCatching { BracketMatcher.findPair(current.text, cursor) }.getOrNull()
                ?.let { (open, close) -> listOf(open..open, close..close) }
                ?: emptyList()
        } else {
            emptyList()
        }
        if (_find.value.visible && _find.value.query.isNotEmpty()) {
            runFind()
        }
    }

    private fun resetDecorationsForNewBuffer() {
        _bracketRanges.value = emptyList()
        _diagnostics.value = emptyList()
        decorationJob?.cancel()
        refreshDecorationsNow()
    }
}
