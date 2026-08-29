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
import com.codeci.ide.ui.editor.EditorDiagnostic
import com.codeci.ide.ui.editor.EditorTab
import com.codeci.ide.ui.editor.EditorUndoManager
import com.codeci.ide.ui.editor.FindOptions
import com.codeci.ide.ui.editor.FindOutcome
import com.codeci.ide.ui.editor.FindReplaceEngine
import com.codeci.ide.ui.projects.FileNode
import com.codeci.ide.ui.projects.FileTreeRepository
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.services.CompilerEngine
import com.codeci.ide.ui.services.CompilerError
import com.codeci.ide.ui.services.CompilerService
import com.codeci.ide.ui.services.CompilerSettings
import com.codeci.ide.ui.services.ErrorType
import com.codeci.ide.ui.services.ExecutionUpdate
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.stats.StatsManager
import com.codeci.ide.ui.utils.FileManager
import com.codeci.ide.ui.utils.FileNameUtils
import com.codeci.ide.ui.utils.WebFileSupport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TerminalSegmentType { COMPILATION, ERROR, OUTPUT, STATS }
data class TerminalSegment(val text: String, val type: TerminalSegmentType)

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

    private val _terminalSegments = MutableStateFlow<List<TerminalSegment>>(emptyList())
    val terminalSegments: StateFlow<List<TerminalSegment>> = _terminalSegments.asStateFlow()

    private val _isTerminalExpanded = MutableStateFlow(false)
    val isTerminalExpanded: StateFlow<Boolean> = _isTerminalExpanded.asStateFlow()

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
        stashActiveTabBuffer(_codeText.value)
        val tab = EditorTab(safe, TextFieldValue(content), content)
        _openTabs.value = trimTabs(_openTabs.value.filterNot { it.relativePath == safe } + tab)
        _activeTabPath.value = safe
        _projectName.value = info.name
        _fileName.value = safe
        _codeText.value = tab.buffer
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
        _codeText.value = TextFieldValue(content)
        scratchSavedText = content
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
            EditorTab(leaf.relativePath, TextFieldValue(content), content)
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
            _isDirty.value = next.buffer.text != next.savedText
            resetDecorationsForNewBuffer()
            syncUndoFlags(undoManager())
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

    private fun writeProjectFile(context: Context, project: String, relativePath: String, text: String): Boolean {
        val info = ProjectManager(context).project(project) ?: return false
        val safe = ProjectPathUtils.sanitizeRelativePath(relativePath) ?: return false
        val file = ProjectPathUtils.resolveInside(info.root, safe) ?: return false
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(text)
        }.isSuccess
    }

    fun saveFile(context: Context): Boolean {
        val text = _codeText.value.text
        val project = _projectName.value
        if (project != null) {
            val safe = ProjectPathUtils.sanitizeRelativePath(_fileName.value) ?: return false
            if (!writeProjectFile(context, project, safe, text)) return false
            _fileName.value = safe
            if (_activeTabPath.value == safe) {
                updateTab(safe) { it.copy(savedText = text) }
            }
            _isDirty.value = false
            return true
        }
        val safe = FileNameUtils.sanitizeFileName(_fileName.value) ?: return false
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
                writeProjectFile(context, project, tab.relativePath, tab.buffer.text) ->
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

    /** Returns true when the buffer was modified by the fix. */
    fun applyQuickFix(diagnostic: EditorDiagnostic): Boolean {
        if (CompilerDiagnostics.semicolonFixLabel(diagnostic) == null) return false
        val bounds = CodeFormatter.lineBounds(_codeText.value.text, diagnostic.line) ?: return false
        if (bounds.isEmpty()) return false
        val text = _codeText.value.text
        val lineText = text.substring(bounds.first, bounds.last + 1)
        val fixed = CompilerDiagnostics.applySemicolonFix(lineText) ?: return false
        val newText = text.substring(0, bounds.first) + fixed + text.substring(bounds.last + 1)
        applyBufferEdit(_codeText.value, TextFieldValue(newText, TextRange(bounds.first + fixed.length)))
        _diagnostics.value = _diagnostics.value.filterNot { it.line == diagnostic.line }
        return true
    }

    // ---------------------------------------------------------------------
    // Terminal output panel + run pipeline
    // ---------------------------------------------------------------------

    fun toggleTerminal() {
        _isTerminalExpanded.value = !_isTerminalExpanded.value
    }

    fun clearTerminal() {
        _terminalSegments.value = emptyList()
    }

    fun runCode(context: Context) {
        _isTerminalExpanded.value = true
        _diagnostics.value = emptyList()
        val currentSegments = mutableListOf<TerminalSegment>()
        currentSegments.add(TerminalSegment("Compiling...\n", TerminalSegmentType.COMPILATION))
        _terminalSegments.value = currentSegments.toList()

        viewModelScope.launch {
            try {
                StatsManager(context).incrementRuns()
                val settingsManager = SettingsManager(context)
                val compilerSettings = compilerSettingsFrom(settingsManager)
                val backend = settingsManager.compilerBackendFlow.first()
                val compilerService = CompilerService(context)
                val compileResult = compilerService.compile(_codeText.value.text, compilerSettings, backend)

                if (compileResult.engine == CompilerEngine.TERMUX && compileResult.engineNote != null) {
                    currentSegments.add(TerminalSegment("(Note: ${compileResult.engineNote})\n", TerminalSegmentType.COMPILATION))
                }
                compileResult.errors.forEach { error ->
                    currentSegments.add(TerminalSegment(formatError(error) + "\n", errorTypeOf(error)))
                }
                if (compileResult.output.isNotBlank() && compileResult.errors.isEmpty()) {
                    currentSegments.add(TerminalSegment(compileResult.output + "\n", TerminalSegmentType.COMPILATION))
                }
                _terminalSegments.value = currentSegments.toList()

                _diagnostics.value = CompilerDiagnostics.combine(
                    errors = compileResult.errors,
                    output = compileResult.output,
                    targetFileName = _fileName.value.substringAfterLast('/')
                )

                val hasBinary = compileResult.binaryPath != null || compileResult.termuxProgramPath != null
                if (!compileResult.success || !hasBinary) {
                    currentSegments.add(TerminalSegment("Compilation failed.\n", TerminalSegmentType.ERROR))
                    _terminalSegments.value = currentSegments.toList()
                    return@launch
                }

                currentSegments.add(TerminalSegment("Compilation successful.\nRunning...\n$ ./program\n", TerminalSegmentType.COMPILATION))
                _terminalSegments.value = currentSegments.toList()

                compilerService.execute(compileResult).collect { update ->
                    when (update) {
                        is ExecutionUpdate.OutputLine -> {
                            currentSegments.add(TerminalSegment(update.line + "\n", TerminalSegmentType.OUTPUT))
                            _terminalSegments.value = currentSegments.toList()
                        }
                        is ExecutionUpdate.Completed -> {
                            val result = update.result
                            if (result.timedOut) {
                                currentSegments.add(
                                    TerminalSegment(
                                        "Program exceeded time limit (possible infinite loop)\n",
                                        TerminalSegmentType.ERROR
                                    )
                                )
                            }
                            val stats =
                                "\nProcess finished with exit code ${result.exitCode} (Execution time: ${result.executionTime}ms)\n"
                            currentSegments.add(TerminalSegment(stats, TerminalSegmentType.STATS))
                            _terminalSegments.value = currentSegments.toList()
                        }
                    }
                }
            } catch (e: Exception) {
                currentSegments.add(TerminalSegment("Run failed: ${e.message}\n", TerminalSegmentType.ERROR))
                _terminalSegments.value = currentSegments.toList()
            }
        }
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

    private fun formatError(error: CompilerError): String {
        val kind = if (error.type == ErrorType.WARNING) "warning" else "error"
        return if (error.line > 0) {
            "source.c:${error.line}:${error.column}: $kind: ${error.message}"
        } else {
            "$kind: ${error.message}"
        }
    }

    private fun errorTypeOf(error: CompilerError): TerminalSegmentType {
        return if (error.type == ErrorType.ERROR) TerminalSegmentType.ERROR else TerminalSegmentType.COMPILATION
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
