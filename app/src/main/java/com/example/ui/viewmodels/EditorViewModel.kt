package com.example.ui.viewmodels

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.services.CompilerService
import com.example.ui.utils.FileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TerminalSegmentType { COMPILATION, ERROR, OUTPUT, STATS }
data class TerminalSegment(val text: String, val type: TerminalSegmentType)

class EditorViewModel : ViewModel() {
    private val compilerService = CompilerService()

    private val _codeText = MutableStateFlow(TextFieldValue(
        """
        #include <stdio.h>

        int main() {
            printf("Hello, World!\n");
            return 0;
        }
        """.trimIndent()
    ))
    val codeText: StateFlow<TextFieldValue> = _codeText.asStateFlow()

    private val _fileName = MutableStateFlow("main.c")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _terminalSegments = MutableStateFlow<List<TerminalSegment>>(emptyList())
    val terminalSegments: StateFlow<List<TerminalSegment>> = _terminalSegments.asStateFlow()

    private val _isTerminalExpanded = MutableStateFlow(false)
    val isTerminalExpanded: StateFlow<Boolean> = _isTerminalExpanded.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    fun updateCode(newValue: TextFieldValue) {
        _codeText.value = newValue
        _isDirty.value = true
    }

    fun updateFileName(newName: String) {
        _fileName.value = newName
        _isDirty.value = true
    }

    fun toggleTerminal() {
        _isTerminalExpanded.value = !_isTerminalExpanded.value
    }

    // Insert logic is now handled by the SymbolBar component directly

    fun runCode() {
        _isTerminalExpanded.value = true
        
        val currentSegments = mutableListOf<TerminalSegment>()
        currentSegments.add(TerminalSegment("Compiling...\n", TerminalSegmentType.COMPILATION))
        _terminalSegments.value = currentSegments.toList()
        
        viewModelScope.launch {
            val code = _codeText.value.text
            val compileResult = compilerService.compile(code)
            
            if (!compileResult.success) {
                val errorMsg = compileResult.errors.joinToString("\n") + "\n\nCompilation failed.\n"
                currentSegments.add(TerminalSegment(errorMsg, TerminalSegmentType.ERROR))
                _terminalSegments.value = currentSegments.toList()
                return@launch
            }
            
            currentSegments.add(TerminalSegment("Compilation successful.\nRunning...\n$ ./main\n", TerminalSegmentType.COMPILATION))
            _terminalSegments.value = currentSegments.toList()
            
            val execResult = compilerService.run(code)
            
            var outputText = execResult.output
            if (!outputText.endsWith("\n") && outputText.isNotEmpty()) {
                outputText += "\n"
            }
            
            if (outputText.isNotEmpty()) {
                currentSegments.add(TerminalSegment(outputText, TerminalSegmentType.OUTPUT))
            }
            
            val stats = "\nProcess finished with exit code ${execResult.exitCode} (Execution time: ${execResult.executionTime}ms)\n"
            currentSegments.add(TerminalSegment(stats, TerminalSegmentType.STATS))
            
            _terminalSegments.value = currentSegments.toList()
        }
    }
    
    fun clearTerminal() {
        _terminalSegments.value = emptyList()
    }

    fun loadFile(context: Context, name: String) {
        val fm = FileManager(context)
        val content = fm.loadFile(name) ?: return
        _fileName.value = name
        _codeText.value = TextFieldValue(content)
        _isDirty.value = false
    }

    fun saveFile(context: Context): Boolean {
        val fm = FileManager(context)
        val success = fm.saveFile(_fileName.value, _codeText.value.text)
        if (success) {
            _isDirty.value = false
        }
        return success
    }
}
