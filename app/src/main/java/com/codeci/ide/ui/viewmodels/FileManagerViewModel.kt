package com.codeci.ide.ui.viewmodels

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeci.ide.R
import com.codeci.ide.ui.projects.BuildArtifactIgnore
import com.codeci.ide.ui.projects.FileNode
import com.codeci.ide.ui.projects.FileTreeRepository
import com.codeci.ide.ui.projects.GitContext
import com.codeci.ide.ui.projects.GitManager
import com.codeci.ide.ui.projects.ProjectConfig
import com.codeci.ide.ui.projects.PythonCacheIgnore
import com.codeci.ide.ui.projects.ProjectHubEntry
import com.codeci.ide.ui.projects.ProjectHubStats
import com.codeci.ide.ui.projects.ProjectInfo
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.projects.ProjectRunDetector
import com.codeci.ide.ui.projects.ProjectsHub
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

    /**
     * Phase 15 — the Projects Hub card list (Spck-style). Built off the main
     * thread with [ProjectHubStats] scans and cheap git metadata reads;
     * `hasChanges` uses `git status` only when git is installed (D3), and
     * every per-project failure degrades the card instead of the list.
     */
    private val _hubEntries = MutableStateFlow<List<ProjectHubEntry>>(emptyList())
    val hubEntries: StateFlow<List<ProjectHubEntry>> = _hubEntries.asStateFlow()

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
            _hubEntries.value = buildHubEntries(context, loaded)
            val current = _activeProject.value
            if (current != null) {
                _activeProject.value = loaded.firstOrNull { it.name == current.name }
                refreshTree()
            }
        }
    }

    /**
     * Phase 15 — assembles the hub card model for every project: kind from
     * the declared config (or the Phase 14 detector for `auto`), a bounded
     * file scan, the branch read straight from `.git/HEAD` (no git process),
     * and `hasChanges` from a porcelain status — the latter only while the
     * packaged git is available, per the "no blocking git calls" guardrail.
     */
    private suspend fun buildHubEntries(
        context: Context,
        projects: List<ProjectInfo>
    ): List<ProjectHubEntry> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val git = runCatching { GitContext(app).manager() }.getOrNull()
        projects.map { project ->
            val scan = ProjectHubStats.scan(project.root)
            val gitDir = File(project.root, ".git")
            val isGit = gitDir.exists()
            val branch = if (isGit) readBranchQuietly(project.root, gitDir) else null
            val status = if (isGit && git != null) {
                // Device round fix 2026-08-31: stray __pycache__ from a python
                // run must not light up the card badge / push offer either.
                runCatching { PythonCacheIgnore.ensure(project.root) }
                // Build outputs (a.out, bin/*.out, …) stay out of the badge too.
                runCatching { BuildArtifactIgnore.ensure(project.root) }
                runCatching { git.status(project.root) }.getOrNull()
            } else {
                null
            }
            val hasChanges = status?.files?.isNotEmpty()
            // Phase 15 device round 1 fix: run the detector for auto AND for
            // stale "c" placeholders (entry file missing — e.g. clones from
            // before the clone-defaults-to-auto change).
            val entryName = project.config.entry.ifBlank { "main.c" }
            val entryExists = File(project.root, entryName).isFile
            val autoDetected = ProjectsHub.shouldAutoDetect(project.config.type, entryExists)
            val autoPlan = if (autoDetected) {
                runCatching { ProjectRunDetector.detect(project.root, null) }.getOrNull()
            } else {
                null
            }
            ProjectHubEntry(
                name = project.name,
                kind = ProjectsHub.kindFor(project.config, autoPlan, autoDetected),
                isGit = isGit,
                branch = branch,
                fileCount = scan.fileCount,
                lastModified = scan.lastModified,
                hasChanges = hasChanges,
                // Phase 17 device fix: commits that never reached the remote.
                unpushed = status?.ahead ?: 0
            )
        }
    }

    /**
     * `.git/HEAD` for a normal repository; for a `.git` FILE (worktree/linked
     * gitdir) follows `gitdir:` one hop. Null on any read failure — the card
     * simply omits the branch chip.
     */
    private fun readBranchQuietly(projectRoot: File, gitEntry: File): String? = try {
        val headFile = when {
            gitEntry.isDirectory -> File(gitEntry, "HEAD")
            gitEntry.isFile -> {
                val pointer = gitEntry.readText().trim()
                val dir = pointer.removePrefix("gitdir:").trim()
                when {
                    dir.isEmpty() -> null
                    else -> File(File(dir).takeIf { File(dir).isAbsolute } ?: File(projectRoot, dir), "HEAD")
                }
            }
            else -> null
        }
        headFile?.takeIf { it.isFile }?.let {
            ProjectsHub.branchFromHeadFile(runCatching { it.readText() }.getOrNull())
        }
    } catch (_: Exception) {
        null
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

    /** Reload projects and reset the visible tree to a clean, collapsed state. */
    fun refresh(context: Context) {
        expandedDirectories.clear()
        loadProjects(context)
    }

    /**
     * Phase 14 — [type] selects the wizard template (C / Python / Static Web /
     * Flask / FastAPI / C microservice); default keeps the historical plain-C
     * project so existing callers are unaffected.
     */
    fun createProject(
        context: Context,
        name: String,
        type: String = "c",
        onCreated: (ProjectInfo) -> Unit = {}
    ) {
        runOperation(
            context = context,
            operation = { ProjectManager(context).createProject(name, type).getOrThrow() },
            onSuccess = { project ->
                val manager = ProjectManager(context)
                _projects.value = manager.listProjects()
                _hubEntries.value = buildHubEntries(context, _projects.value)
                _activeProject.value = project
                refreshTree()
                StatsManager(context).incrementFilesCreated()
                onCreated(project)
            }
        )
    }

    /**
     * Phase 15 — rename a project folder (hub card ⋮ → Rename). Guards mirror
     * [ProjectManager.createProject]: sanitized name, no existing target,
     * target must stay a direct child of the projects root. The config's
     * display name is rewritten after the move so metadata stays truthful.
     */
    fun renameProject(context: Context, oldName: String, newName: String, onRenamed: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val manager = ProjectManager(context)
                val result = withContext(Dispatchers.IO) {
                    val old = manager.project(oldName)
                        ?: error(context.getString(R.string.project_missing))
                    val safe = ProjectPathUtils.sanitizeProjectName(newName)
                        ?: error(context.getString(R.string.invalid_project_name))
                    if (safe == old.name) return@withContext old.name
                    val target = File(manager.projectsRoot(), safe)
                    if (target.exists()) error(context.getString(R.string.project_name_taken))
                    if (!old.root.renameTo(target)) error(context.getString(R.string.rename_failed))
                    manager.project(safe)?.let { moved ->
                        runCatching { manager.writeConfig(moved.root, moved.config.copy(name = safe)) }
                    }
                    safe
                }
                if (_activeProject.value?.name == oldName) {
                    _activeProject.value = withContext(Dispatchers.IO) { manager.project(result) }
                }
                loadProjects(context)
                onRenamed(result)
            } catch (e: Exception) {
                _userMessage.value = e.message ?: context.getString(R.string.rename_failed)
            } finally {
                _isBusy.value = false
            }
        }
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
                finishImport(context, manager, project, onImported)
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
                finishImport(context, ProjectManager(context), active, onImported)
            } catch (e: Exception) {
                _userMessage.value = "Import failed: ${e.message ?: "unknown error"}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    /** Suggests the archive filename (without `.zip`) for the import dialog. */
    fun suggestZipProjectName(context: Context, uri: Uri, onSuggested: (String) -> Unit) {
        viewModelScope.launch {
            val suggestion = withContext(Dispatchers.IO) {
                val displayName = queryDisplayName(context, uri)
                    ?: uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.let { Uri.decode(it) }
                val stem = displayName?.substringBeforeLast('.', displayName).orEmpty()
                ProjectPathUtils.sanitizeProjectName(stem) ?: "imported_project"
            }
            onSuggested(suggestion)
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
                finishImport(context, manager, project, onImported)
            } catch (e: Exception) {
                _userMessage.value = "ZIP import failed: ${e.message ?: "unknown error"}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Phase 13 — visual GitHub clone: `git clone <url>` into a NEW uniquely
     * named folder inside the projects root, then register it as a project
     * (same flow as the Phase 8 ZIP import) and open it. Partial clones are
     * cleaned up on failure. Public repositories clone without credentials;
     * a stored token (Settings → GitHub Account) is used automatically.
     *
     * Phase 15 — the Projects Hub clone dialog adds the optional [branch]
     * (`--branch`) and [shallow] (`--depth 1`) arguments; with the defaults
     * the Phase 13 behavior is bit-for-bit unchanged.
     */
    fun cloneFromGitHub(
        context: Context,
        url: String,
        requestedName: String,
        branch: String? = null,
        shallow: Boolean = false,
        onCloned: (ProjectInfo) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val git = GitContext(context.applicationContext).manager()
                    ?: error(context.getString(R.string.git_not_installed_message))
                if (!GitManager.isCloneableUrl(url.trim())) {
                    error(context.getString(R.string.clone_invalid_url))
                }
                val selectedBranch = branch?.trim()?.takeIf { it.isNotEmpty() }
                if (selectedBranch != null && !ProjectsHub.isValidBranchName(selectedBranch)) {
                    error(context.getString(R.string.clone_invalid_branch))
                }
                val manager = ProjectManager(context)
                val baseName = requestedName.trim().ifBlank {
                    GitManager.repoNameFromUrl(url.trim()) ?: "cloned_repo"
                }
                val name = withContext(Dispatchers.IO) {
                    val existing = manager.projectsRoot().listFiles()
                        ?.filter { it.isDirectory }
                        ?.map { it.name }
                        ?.toSet()
                        .orEmpty()
                    ProjectsHub.uniqueProjectName(baseName, existing)
                }
                val dest = File(manager.projectsRoot(), name)
                try {
                    withContext(Dispatchers.IO) {
                        git.clone(url.trim(), dest, shallow = shallow, branch = selectedBranch)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.IO) { dest.deleteRecursively() }
                    throw e
                }
                // Phase 15 — cloned repositories are ensured as type `auto`
                // (spec §2.3.2): RUN ▶ and the hub card then detect the real
                // family from the repo's files instead of trusting a "c"
                // placeholder that Phase 13's default carried.
                withContext(Dispatchers.IO) {
                    val config = File(dest, ".codec/project.json")
                    if (!config.isFile) {
                        manager.writeConfig(dest, ProjectConfig.defaultFor(name, "auto"))
                    }
                }
                finishImport(context, manager, manager.project(name) ?: ProjectInfo(name, dest, ProjectConfig.defaultFor(name, "auto")), onCloned)
                _userMessage.value = context.getString(R.string.clone_success, name)
            } catch (e: Exception) {
                _userMessage.value = context.getString(
                    R.string.clone_failed,
                    e.message ?: context.getString(R.string.create_failed)
                )
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Phase 15 — populate the clone dialog's branch selector:
     * `git ls-remote --heads` through the same engine (redaction, timeouts,
     * askpass). A failure is returned, not thrown: the dialog falls back to
     * free-text branch entry (offline, private repo without token, …).
     */
    fun fetchRemoteBranches(
        context: Context,
        url: String,
        onResult: (Result<List<String>>) -> Unit
    ) {
        viewModelScope.launch {
            val trimmed = url.trim()
            if (!GitManager.isCloneableUrl(trimmed)) {
                onResult(Result.failure(IllegalArgumentException(
                    context.getString(R.string.clone_invalid_url)
                )))
                return@launch
            }
            try {
                val git = GitContext(context.applicationContext).manager()
                    ?: error(context.getString(R.string.git_not_installed_message))
                val branches = withContext(Dispatchers.IO) { git.listRemoteBranches(trimmed) }
                onResult(Result.success(branches))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    /**
     * Phase 17 §2.4 — hub card ⋮ → Push. Pushes the active branch (needs an
     * upstream and, for a private remote, the stored token). Mirrors
     * [pullProject] so both menu items behave the same way.
     */
    fun pushProject(context: Context, projectName: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val manager = ProjectManager(context)
                val project = withContext(Dispatchers.IO) { manager.project(projectName) }
                    ?: error(context.getString(R.string.project_missing))
                val git = GitContext(context.applicationContext).manager()
                    ?: error(context.getString(R.string.git_not_installed_message))
                // Phase 17 device fix: a branch created in the app has no
                // upstream, so publish it instead of failing.
                withContext(Dispatchers.IO) { git.pushHandlingUpstream(project.root) }
                loadProjects(context)
                _userMessage.value = context.getString(R.string.hub_push_success, projectName)
                onDone()
            } catch (e: Exception) {
                _userMessage.value = context.getString(
                    R.string.hub_push_failed,
                    e.message ?: context.getString(R.string.create_failed)
                )
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Phase 15 — hub card ⋮ → Pull. Runs `git pull` on the project through
     * the Phase 13 engine (network timeout, redaction) and refreshes the hub
     * so the M badge and branch chip reflect the new state.
     */
    fun pullProject(context: Context, projectName: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val manager = ProjectManager(context)
                val project = withContext(Dispatchers.IO) { manager.project(projectName) }
                    ?: error(context.getString(R.string.project_missing))
                val git = GitContext(context.applicationContext).manager()
                    ?: error(context.getString(R.string.git_not_installed_message))
                withContext(Dispatchers.IO) { git.pull(project.root) }
                loadProjects(context)
                if (_activeProject.value?.name == projectName) refreshTree()
                _userMessage.value = context.getString(R.string.hub_pull_success, projectName)
                onDone()
            } catch (e: Exception) {
                _userMessage.value = context.getString(
                    R.string.hub_pull_failed,
                    e.message ?: context.getString(R.string.create_failed)
                )
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Phase 15 — hub card ⋮ → Copy remote URL. Reads `.git/config` directly
     * (no git process — works offline and before `pkg install git`); the
     * parser prefers `[remote "origin"]`.
     */
    fun remoteUrlFor(context: Context, projectName: String, onFound: (String?) -> Unit) {
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) {
                runCatching {
                    val root = ProjectManager(context).project(projectName)?.root ?: return@runCatching null
                    val config = File(root, ".git/config")
                    if (!config.isFile) return@runCatching null
                    // .git/config is a few KB at most; the 256 KiB read cap
                    // keeps a pathological file from hanging the read.
                    val text = config.bufferedReader().use { reader ->
                        val chars = CharArray(256 * 1024)
                        val n = reader.read(chars)
                        if (n <= 0) "" else String(chars, 0, n)
                    }
                    ProjectsHub.remoteUrlFromConfig(text)
                }.getOrNull()
            }
            onFound(url)
        }
    }

    /** Set an HTML/HTM file as the entry opened by a web project's Run action. */
    fun setDefaultWebRun(context: Context, relativePath: String) {
        val project = _activeProject.value ?: return
        val safePath = ProjectPathUtils.sanitizeRelativePath(relativePath)
        val target = safePath?.let { ProjectPathUtils.resolveInside(project.root, it) }
        if (safePath == null || target == null || !target.isFile || !WebFileSupport.isHtml(target.name)) {
            _userMessage.value = context.getString(R.string.select_html_for_default_run)
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val manager = ProjectManager(context)
                withContext(Dispatchers.IO) {
                    manager.writeConfig(
                        project.root,
                        project.config.copy(
                            type = "web",
                            entry = safePath,
                            build = "",
                            run = "",
                            clean = ""
                        )
                    )
                }
                _projects.value = withContext(Dispatchers.IO) { manager.listProjects() }
                _hubEntries.value = buildHubEntries(context, _projects.value)
                _activeProject.value = withContext(Dispatchers.IO) { manager.project(project.name) }
                refreshTree()
                _userMessage.value = context.getString(R.string.default_run_page_set, safePath)
            } catch (e: Exception) {
                _userMessage.value = context.getString(
                    R.string.default_run_page_failed,
                    e.message ?: "unknown error"
                )
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

    private suspend fun finishImport(
        context: Context,
        manager: ProjectManager,
        project: ProjectInfo,
        onImported: (ProjectInfo) -> Unit
    ) {
        _projects.value = withContext(Dispatchers.IO) { manager.listProjects() }
        _hubEntries.value = buildHubEntries(context, _projects.value)
        val refreshed = withContext(Dispatchers.IO) { manager.project(project.name) } ?: project
        _activeProject.value = refreshed
        expandAllDirectories(refreshed.root)
        refreshTree()
        onImported(refreshed)
    }

    /** Make every extracted directory visible immediately after an import. */
    private fun expandAllDirectories(projectRoot: File) {
        expandedDirectories.clear()
        val root = FileTreeRepository.buildTree(projectRoot)
        fun collect(directory: FileNode.DirectoryNode) {
            directory.children.forEach { child ->
                if (child is FileNode.DirectoryNode) {
                    expandedDirectories += child.relativePath
                    collect(child)
                }
            }
        }
        collect(root)
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
