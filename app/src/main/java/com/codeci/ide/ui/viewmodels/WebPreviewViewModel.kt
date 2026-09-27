package com.codeci.ide.ui.viewmodels

import com.codeci.ide.ui.services.PreviewConsoleEntry
import com.codeci.ide.ui.services.PreviewRequest
import com.codeci.ide.ui.services.PreviewToolsPolicy
import kotlinx.coroutines.Job
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

    private val _console = MutableStateFlow<List<PreviewConsoleEntry>>(emptyList())
    val console: StateFlow<List<PreviewConsoleEntry>> = _console.asStateFlow()

    private val _reloadTick = MutableStateFlow(0)
    val reloadTick: StateFlow<Int> = _reloadTick.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var watchedPath: String? = null

    private val _network = MutableStateFlow<List<PreviewRequest>>(emptyList())
    val network: StateFlow<List<PreviewRequest>> = _network.asStateFlow()
    private var session = 0L
    private var watcher: Job? = null

    // WebView intercepts on a worker thread. One lock owns session validation
    // and append/clear, so late callbacks cannot bleed into a replacement view.
    @Synchronized
    fun beginSession(): Long {
        session++
        _console.value = emptyList()
        _network.value = emptyList()
        return session
    }

    @Synchronized
    fun endSession(token: Long) {
        if (token == session) session++
    }

    @Synchronized
    fun addConsole(token: Long, level: String, message: String, lineNumber: Int) {
        if (token != session) return
        _console.value = PreviewToolsPolicy.append(
            _console.value, PreviewToolsPolicy.console(level, message, lineNumber)
        )
    }

    @Synchronized
    fun addRequest(token: Long, method: String, url: String, mainFrame: Boolean) {
        if (token != session) return
        val entry = PreviewToolsPolicy.request(method, url, mainFrame) ?: return
        _network.value = PreviewToolsPolicy.append(_network.value, entry)
    }

    @Synchronized
    fun clearConsole() { _console.value = emptyList() }

    @Synchronized
    fun clearNetwork() { _network.value = emptyList() }

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
    fun watch(file: File?) {
        val path = file?.absolutePath
        if (watchedPath == path) return
        watchedPath = path
        watcher?.cancel()
        if (file == null) return
        watcher = viewModelScope.launch {
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
