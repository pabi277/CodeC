package com.codeci.ide.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Phase 5.2 web preview: holds the JS console lines and a reload signal.
 *
 * The WebView itself lives in [com.codeci.ide.ui.screens.WebPreviewScreen];
 * this ViewModel is the small, survive-configuration-changes sidecar that the
 * screen feeds console messages into and reads the reload tick from.
 */
class WebPreviewViewModel : ViewModel() {

    private val _console = MutableStateFlow<List<String>>(emptyList())
    val console: StateFlow<List<String>> = _console.asStateFlow()

    private val _reloadTick = MutableStateFlow(0)
    val reloadTick: StateFlow<Int> = _reloadTick.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var watchedPath: String? = null

    fun addConsole(level: String, message: String, lineNumber: Int) {
        val prefix = if (lineNumber > 0) "$level [line $lineNumber]" else level
        _console.update { (it + "$prefix: $message").takeLast(200) }
    }

    fun clearConsole() {
        _console.value = emptyList()
    }

    fun requestReload() {
        _reloadTick.update { it + 1 }
    }

    fun reportError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Live reload: poll [file]'s mtime and bump the reload tick when it
     * changes, so an editor Save (or a terminal rewrite) is reflected without
     * re-navigating. Cheap (~700 ms poll); a `FileObserver` slice is future
     * work.
     */
    fun watch(file: File) {
        val path = file.absolutePath
        if (watchedPath == path) return
        watchedPath = path
        viewModelScope.launch {
            var last = runCatching { file.lastModified() }.getOrDefault(0L)
            while (isActive) {
                delay(700)
                val current = runCatching { file.lastModified() }.getOrDefault(0L)
                if (current != last) {
                    last = current
                    _reloadTick.update { it + 1 }
                }
            }
        }
    }
}
