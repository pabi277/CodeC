package com.codeci.ide.ui.viewmodels

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeci.ide.R
import com.codeci.ide.ui.projects.FileNode
import com.codeci.ide.ui.projects.FileTreeRepository
import com.codeci.ide.ui.projects.ProjectInfo
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.projects.ProjectTransfer
import com.codeci.ide.ui.stats.StatsManager
import com.codeci.ide.ui.utils.WebFileSupport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileManagerViewModel : ViewModel() {
    private val _projects = MutableStateFlow<List<ProjectInfo>>(emptyList())
    val projects: StateFlow<List<ProjectInfo>> = _projects.asStateFlow()

    private val _activeProject = MutableStateFlow<ProjectInfo?>(null)
    val activeProject: StateFlow<ProjectInfo?> = _activeProject.asStateFlow()

    private val _tree = MutableStateFlow<List<FileNode>>(emptyList())
    val tree: StateFlow<List<FileNode>> = _tree.asStateFlow()

    private val expandedDirectories = mutableSetOf<String>()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun consumeMessage() {
        _userMessage.value = null
    }

    fun loadProjects(context: Context) {
        viewModelScope.launch {
            val manager = ProjectManager(context)
            val loaded = withContext(Dispatchers.IO) { manager.listProjects() }
            _projects.value = loaded
            val current = _activeProject.value
            if (current != null) {
                _activeProject.value = loaded.firstOrNull { it.name == current.name }
                refreshTree()
            }
        }
    }

    fun openProject(context: Context, name: String) {
        viewModelScope.launch {
            val project = withContext(Dispatchers.IO) { ProjectManager(context).project(name) }
            if (project == null) {
                _userMessage.value = "Project is no longer available"
                return@launch
            }
            expandedDirectories.clear()
            _activeProject.value = project
            refreshTree()
        }
    }

    fun closeProject() {
        _activeProject.value = null
        _tree.value = emptyList()
        expandedDirectories.clear()
    }

    fun toggleDirectory(relativePath: String) {
        if (!ProjectPathUtils.sanitizeRelativePath(relativePath).let { it != null && it.isNotEmpty() }) return
        if (!expandedDirectories.add(relativePath)) expandedDirectories.remove(relativePath)
        refreshTree()
    }

    fun refresh(context: Context) {
        loadProjects(context)
    }

    fun createProject(context: Context, name: String, onCreated: (ProjectInfo) -> Unit = {}) {
        runOperation(
            context = context,
            operation = { ProjectManager(context).createProject(name).getOrThrow() },
            onSuccess = { project ->
                _projects.value = ProjectManager(context).listProjects()
                _activeProject.value = project
                refreshTree()
                StatsManager(context).incrementFilesCreated()
                onCreated(project)
            }
        )
    }

    fun createFolder(context: Context, parentPath: String, name: String) {
        val project = _activeProject.value ?: return
        runOperation(
            context = context,
            operation = { FileTreeRepository.createDirectory(project.root, parentPath, name).getOrThrow() },
            onSuccess = { refreshTree() }
        )
    }

    fun createFile(context: Context, parentPath: String, name: String): Result<String> {
        val project = _activeProject.value
            ?: return Result.failure(IllegalStateException("Open a project first"))
        val safeName = ProjectPathUtils.sanitizeSegment(name)
            ?: return Result.failure(IllegalArgumentException(context.getString(R.string.invalid_file_name)))
        // A project is a general-purpose workspace: preserve extensions such
        // as .h, .md, .json, and .py instead of applying the legacy standalone
        // editor's automatic `.c` suffix.
        val fileName = safeName
        return try {
            val result = FileTreeRepository.createFile(
                project.root,
                parentPath,
                fileName,
                WebFileSupport.starterContent(fileName)
            )
            if (result.isSuccess) {
                viewModelScope.launch {
                    StatsManager(context).incrementFilesCreated()
                    refreshTree()
                }
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun renameNode(context: Context, relativePath: String, newName: String, onRenamed: (String) -> Unit = {}) {
        val project = _activeProject.value ?: return
        runOperation(
            context = context,
            operation = { FileTreeRepository.rename(project.root, relativePath, newName).getOrThrow() },
            onSuccess = { renamed ->
                refreshTree()
                onRenamed(renamed)
            }
        )
    }

    fun deleteNode(context: Context, relativePath: String) {
        val project = _activeProject.value ?: return
        runOperation(
            context = context,
            operation = {
                if (!FileTreeRepository.delete(project.root, relativePath)) error("Could not delete item")
            },
            onSuccess = { refreshTree() }
        )
    }

    fun deleteProject(context: Context, name: String) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                if (!withContext(Dispatchers.IO) { ProjectManager(context).deleteProject(name) }) {
                    error("Could not delete project")
                }
                if (_activeProject.value?.name == name) closeProject()
                loadProjects(context)
            } catch (e: Exception) {
                _userMessage.value = e.message ?: context.getString(R.string.delete_failed)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun importFolder(context: Context, uri: Uri, onImported: (ProjectInfo) -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val manager = ProjectManager(context)
                val baseName = withContext(Dispatchers.IO) {
                    queryDisplayName(context, uri) ?: "imported_project"
                }
                val name = uniqueProjectName(manager, baseName)
                val project = withContext(Dispatchers.IO) {
                    manager.createProject(name, includeStarter = false).getOrThrow().also {
                        File(it.root, ".codec/project.json").delete()
                    }
                }
                val result = withContext(Dispatchers.IO) {
                    ProjectTransfer.copyDocumentTree(context.contentResolver, uri, project.root)
                }
                result.getOrThrow()
                ensureImportedConfig(manager, project)
                finishImport(manager, project, onImported)
            } catch (e: Exception) {
                _userMessage.value = "Import failed: ${e.message ?: "unknown error"}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun importFile(context: Context, uri: Uri, onImported: (ProjectInfo) -> Unit = {}) {
        val active = _activeProject.value
        if (active == null) {
            _userMessage.value = "Open a project before importing a file"
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                withContext(Dispatchers.IO) {
                    ProjectTransfer.copySingleDocument(
                        context.contentResolver,
                        uri,
                        active.root
                    ).getOrThrow()
                }
                finishImport(ProjectManager(context), active, onImported)
            } catch (e: Exception) {
                _userMessage.value = "Import failed: ${e.message ?: "unknown error"}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun importZip(context: Context, uri: Uri, requestedName: String, onImported: (ProjectInfo) -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val manager = ProjectManager(context)
                val name = uniqueProjectName(manager, requestedName)
                val project = withContext(Dispatchers.IO) {
                    manager.createProject(name, includeStarter = false).getOrThrow().also {
                        File(it.root, ".codec/project.json").delete()
                    }
                }
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            ProjectTransfer.importZip(input, project.root)
                        } ?: error("Could not read the selected ZIP")
                    }
                    ensureImportedConfig(manager, project)
                } catch (e: Exception) {
                    withContext(Dispatchers.IO) { manager.deleteProject(project.name) }
                    throw e
                }
                finishImport(manager, project, onImported)
            } catch (e: Exception) {
                _userMessage.value = "ZIP import failed: ${e.message ?: "unknown error"}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun exportProject(context: Context, projectName: String, uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val project = withContext(Dispatchers.IO) { ProjectManager(context).project(projectName) }
                    ?: error("Project is no longer available")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        ProjectTransfer.exportZip(project.root, output)
                    } ?: error("Could not open the export destination")
                }
                _userMessage.value = "Exported ${project.name}.zip"
            } catch (e: Exception) {
                _userMessage.value = "Export failed: ${e.message ?: "unknown error"}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        return String.format(
            Locale.getDefault(),
            "%.1f %s",
            size / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    fun formatDate(timestamp: Long): String =
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

    private fun refreshTree() {
        val project = _activeProject.value ?: run {
            _tree.value = emptyList()
            return
        }
        val root = FileTreeRepository.buildTree(project.root, expandedDirectories)
        _tree.value = FileTreeRepository.flattenVisible(root)
    }

    private fun finishImport(
        manager: ProjectManager,
        project: ProjectInfo,
        onImported: (ProjectInfo) -> Unit
    ) {
        _projects.value = manager.listProjects()
        val refreshed = manager.project(project.name) ?: project
        _activeProject.value = refreshed
        expandedDirectories.clear()
        refreshTree()
        onImported(refreshed)
    }

    private fun ensureImportedConfig(manager: ProjectManager, project: ProjectInfo) {
        val config = File(project.root, ".codec/project.json")
        if (!config.isFile) {
            manager.writeConfig(project.root, com.codeci.ide.ui.projects.ProjectConfig.defaultFor(project.name))
        }
    }

    private fun uniqueProjectName(manager: ProjectManager, rawName: String): String {
        val base = ProjectPathUtils.sanitizeProjectName(rawName.substringBeforeLast('.'))
            ?: "imported_project"
        var candidate = base
        var suffix = 2
        while (manager.project(candidate) != null) {
            candidate = "${base}_$suffix"
            suffix++
        }
        return candidate
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (DocumentsContract.isTreeUri(uri)) {
            val id = DocumentsContract.getTreeDocumentId(uri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, id)
            return queryDisplayName(context, docUri)
        }
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun <T> runOperation(
        context: Context,
        operation: suspend () -> T,
        onSuccess: suspend (T) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val result = withContext(Dispatchers.IO) { operation() }
                onSuccess(result)
            } catch (e: Exception) {
                _userMessage.value = e.message ?: context.getString(R.string.create_failed)
            } finally {
                _isBusy.value = false
            }
        }
    }
}
