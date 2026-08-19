package com.codeci.ide.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeci.ide.ui.modules.InstallStatus
import com.codeci.ide.ui.modules.InstalledModulesStore
import com.codeci.ide.ui.modules.ManifestRepository
import com.codeci.ide.ui.modules.Module
import com.codeci.ide.ui.modules.ModuleInstaller
import com.codeci.ide.ui.modules.ModuleState
import com.codeci.ide.ui.services.CompilerService
import com.codeci.ide.ui.services.DownloadManager
import com.codeci.ide.ui.services.DownloadState
import com.codeci.ide.ui.utils.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModuleViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MANIFEST_URL = ManifestRepository.MANIFEST_URL
        const val CLANG_ID = "clang-compiler"
    }

    private val store = InstalledModulesStore(application)
    private val downloader = DownloadManager(application)
    private val manifestRepository = ManifestRepository(application)

    private val _modules = MutableStateFlow<List<ModuleState>>(emptyList())
    val modules: StateFlow<List<ModuleState>> = _modules.asStateFlow()

    private val _catalogMessage = MutableStateFlow<String?>(null)
    val catalogMessage: StateFlow<String?> = _catalogMessage.asStateFlow()

    private val _isCompilerInstalled = MutableStateFlow(false)
    val isCompilerInstalled: StateFlow<Boolean> = _isCompilerInstalled.asStateFlow()

    init {
        detectCompilerOnStartup()
        refreshCatalog()
    }

    fun isCompilerReady(): Boolean = _isCompilerInstalled.value || store.isCompilerInstalled()

    fun requiredCompiler(): Module? =
        _modules.value.firstOrNull { it.module.required || it.module.id == CLANG_ID }?.module

    fun refreshCatalog() {
        viewModelScope.launch {
            val result = manifestRepository.fetchManifest(store.getModulesRoot())
            _catalogMessage.value = result.error
            _modules.value = result.modules.map { module ->
                ModuleState(module = module, status = detectStatus(module), updateAvailable = isUpdateAvailable(module))
            }
            _isCompilerInstalled.value = store.isCompilerInstalled()
        }
    }

    fun downloadModule(moduleId: String) {
        val current = _modules.value.firstOrNull { it.module.id == moduleId } ?: return
        viewModelScope.launch {
            install(current.module, retryChecksum = true)
        }
    }

    fun uninstallModule(moduleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _modules.value.firstOrNull { it.module.id == moduleId } ?: return@launch
            try {
                val dir = store.installDir(state.module)
                if (dir.exists()) dir.deleteRecursively()
                store.removeInstalled(moduleId)
                updateStatus(moduleId, InstallStatus.NotInstalled, updateAvailable = false)
                _isCompilerInstalled.value = store.isCompilerInstalled()
            } catch (e: Exception) {
                AppLogger.e("Modules", "Uninstall failed", e)
                updateStatus(moduleId, InstallStatus.Failed("Couldn't remove the module. Try again."))
            }
        }
    }

    private suspend fun install(module: Module, retryChecksum: Boolean) {
        val tempDir = store.getTempDir()
        val needed = module.size.takeIf { it > 0 } ?: (100L * 1024L * 1024L)
        val free = ModuleInstaller.availableBytes(store.getModulesRoot())
        if (free < needed + 8L * 1024L * 1024L) {
            val needMb = needed / (1024 * 1024)
            updateStatus(
                module.id,
                InstallStatus.Failed("Not enough storage. Free at least ${needMb}MB and try again.")
            )
            return
        }

        updateStatus(module.id, InstallStatus.Downloading(0))
        var zipPath: String? = null
        try {
            downloader.downloadModule(module.downloadUrl, module.id).collect { state ->
                when (state) {
                    is DownloadState.Downloading ->
                        updateStatus(module.id, InstallStatus.Downloading(state.progress))
                    is DownloadState.Paused ->
                        updateStatus(module.id, InstallStatus.Downloading(0))
                    is DownloadState.Failed ->
                        updateStatus(module.id, InstallStatus.Failed(friendlyDownloadError(state.error)))
                    is DownloadState.Completed -> zipPath = state.filePath
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Modules", "Download error", e)
            updateStatus(module.id, InstallStatus.Failed(friendlyDownloadError(e.message)))
            return
        }

        val zipFile = zipPath?.let { File(it) }
        if (zipFile == null || !zipFile.exists()) {
            if (_modules.value.firstOrNull { it.module.id == module.id }?.status is InstallStatus.Failed) {
                return
            }
            updateStatus(module.id, InstallStatus.Failed("Download failed. Check your connection and tap Retry."))
            return
        }

        val checksumOk = withContext(Dispatchers.IO) {
            ModuleInstaller.checksumMatches(zipFile, module.checksum)
        }
        if (!checksumOk) {
            zipFile.delete()
            if (retryChecksum) {
                AppLogger.i("Modules", "Checksum mismatch, retrying once")
                install(module, retryChecksum = false)
                return
            }
            updateStatus(module.id, InstallStatus.Failed("Corrupted download. The file didn't match its checksum."))
            return
        }

        updateStatus(module.id, InstallStatus.Extracting)
        try {
            val executable = withContext(Dispatchers.IO) {
                val dest = store.installDir(module)
                ModuleInstaller.extractZip(zipFile, dest)
                // Termux-style toolchains use symlinks; ZIP extraction flattens them
                // into text placeholders, so restore them and force exec bits.
                ModuleInstaller.materializeFlattenedSymlinks(dest)
                ModuleInstaller.markBinariesExecutable(dest)
                store.markInstalled(module, dest.absolutePath)
                zipFile.delete()
                tempDir.listFiles()?.forEach { if (it.name.startsWith(module.id)) it.delete() }
                val binary = ModuleInstaller.compilerBinary(store.getModulesRoot(), module)
                binary == null || binary.canExecute() || ModuleInstaller.chmodExecutable(binary)
            }
            if (executable) {
                updateStatus(module.id, InstallStatus.Installed, updateAvailable = false)
            } else {
                updateStatus(module.id, InstallStatus.Failed(CompilerService.DEVICE_EXEC_BLOCKED))
            }
            _isCompilerInstalled.value = store.isCompilerInstalled()
        } catch (e: Exception) {
            AppLogger.e("Modules", "Extraction failed", e)
            updateStatus(
                module.id,
                InstallStatus.Failed("Couldn't install the compiler files. ${e.message ?: "Try downloading again."}")
            )
        }
    }

    private fun detectCompilerOnStartup() {
        val root = store.getModulesRoot()
        val clang = File(root, "clang/bin/clang")
        val installed = clang.exists() || store.isCompilerInstalled()
        _isCompilerInstalled.value = installed
        AppLogger.i("Modules", "Compiler present on startup: $installed")
    }

    private fun detectStatus(module: Module): InstallStatus {
        val root = store.getModulesRoot()
        val installDir = store.installDir(module)
        val binary = ModuleInstaller.compilerBinary(root, module)
        val present = binary != null || File(installDir, ".installed").exists()
        return if (present) InstallStatus.Installed else InstallStatus.NotInstalled
    }

    private fun isUpdateAvailable(module: Module): Boolean {
        val installed = store.readInstalled().firstOrNull { it.id == module.id }
        val diskVersion = ModuleInstaller.installedVersion(store.installDir(module))
        val current = installed?.version ?: diskVersion ?: return false
        return current.isNotBlank() && current != module.version
    }

    private fun updateStatus(id: String, status: InstallStatus, updateAvailable: Boolean? = null) {
        _modules.value = _modules.value.map { state ->
            if (state.module.id == id) {
                state.copy(
                    status = status,
                    updateAvailable = updateAvailable ?: state.updateAvailable
                )
            } else state
        }
    }

    private fun friendlyDownloadError(raw: String?): String {
        val message = raw.orEmpty()
        return when {
            message.contains("Unable to resolve", ignoreCase = true) ||
                message.contains("UnknownHost", ignoreCase = true) ->
                "No internet connection. Connect and tap Retry."
            message.contains("HTTP") ->
                "Download failed ($message). Tap Retry."
            message.contains("Permission", ignoreCase = true) ->
                "Storage permission denied. Allow file access and try again."
            message.isBlank() -> "Download failed. Check your connection and tap Retry."
            else -> message
        }
    }
}
