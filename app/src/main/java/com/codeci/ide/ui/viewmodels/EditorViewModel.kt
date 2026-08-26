package com.codeci.ide.ui.viewmodels

import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TerminalSegmentType { COMPILATION, ERROR, OUTPUT, STATS }
data class TerminalSegment(val text: String, val type: TerminalSegmentType)

class EditorViewModel : ViewModel() {

    private val _codeText = MutableStateFlow(
        TextFieldValue(
            """
            #include <stdio.h>

            int main() {
                printf("Hello, World!\n");
                return 0;
            }
            """.trimIndent()
        )
    )
    val codeText: StateFlow<TextFieldValue> = _codeText.asStateFlow()

    private val _fileName = MutableStateFlow("main.c")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

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

    fun consumeMessage() {
        _userMessage.value = null
    }

    fun updateCode(newValue: TextFieldValue, autoIndent: Boolean = false, tabSize: Int = 4) {
        val old = _codeText.value
        var next = newValue
        if (autoIndent && isSingleNewlineInsert(old, newValue)) {
            next = applyAutoIndent(old, newValue, tabSize)
        }
        _codeText.value = next
        _isDirty.value = true
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

    fun updateFileName(
        context: Context,
        newName: String,
        onRenamed: (String) -> Unit
    ) {
        val sanitized = FileNameUtils.sanitizeFileName(newName)
        if (sanitized == null) {
            _userMessage.value = context.getString(com.codeci.ide.R.string.invalid_file_name)
            return
        }
        val name = WebFileSupport.normalizeFileName(sanitized)
        val oldName = _fileName.value
        if (name == oldName) return

        viewModelScope.launch {
            _isRenaming.value = true
            val fm = FileManager(context)
            val existingOnDisk = withContext(Dispatchers.IO) { fm.loadFile(oldName) != null }
            val success = if (existingOnDisk) {
                withContext(Dispatchers.IO) { fm.renameFile(oldName, name) }
            } else {
                true
            }
            if (success) {
                _fileName.value = name
                if (!existingOnDisk) _isDirty.value = true
                SettingsManager(context).replaceRecentFile(oldName, name)
                _userMessage.value = context.getString(com.codeci.ide.R.string.rename_success)
                onRenamed(name)
            } else {
                _fileName.value = oldName
                _userMessage.value = context.getString(com.codeci.ide.R.string.rename_failed)
            }
            _isRenaming.value = false
        }
    }

    fun toggleTerminal() {
        _isTerminalExpanded.value = !_isTerminalExpanded.value
    }

    fun runCode(context: Context) {
        _isTerminalExpanded.value = true
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

    fun clearTerminal() {
        _terminalSegments.value = emptyList()
    }

    fun loadFile(context: Context, name: String) {
        val safe = FileNameUtils.sanitizeFileName(name) ?: return
        val fm = FileManager(context)
        val content = fm.loadFile(safe) ?: return
        _fileName.value = safe
        _codeText.value = TextFieldValue(content)
        _isDirty.value = false
    }

    fun saveFile(context: Context): Boolean {
        val safe = FileNameUtils.sanitizeFileName(_fileName.value) ?: return false
        val fm = FileManager(context)
        val success = fm.saveFile(safe, _codeText.value.text)
        if (success) {
            _fileName.value = WebFileSupport.normalizeFileName(safe)
            _isDirty.value = false
        }
        return success
    }

    /**
     * Persists the buffer and returns the absolute path so the terminal
     * handoff can `cc` the file. Null when the name is illegal or save fails.
     */
    fun saveAndAbsolutePath(context: Context): String? {
        if (!saveFile(context)) return null
        val safe = FileNameUtils.sanitizeFileName(_fileName.value) ?: return null
        val file = java.io.File(FileManager(context).getProjectDir(), safe)
        return file.absolutePath
    }
}
