package com.codeci.ide.ui.projects

import android.content.Context
import com.codeci.ide.ui.utils.WebFileSupport
import java.io.File

/** Metadata and lifecycle operations for app-private CodeC projects. */
data class ProjectInfo(
    val name: String,
    val root: File,
    val config: ProjectConfig
)

class ProjectManager(context: Context) {
    private val appContext = context.applicationContext

    fun projectsRoot(): File = File(appContext.filesDir, "CodeC/projects").also {
        if (!it.exists()) it.mkdirs()
    }

    fun listProjects(): List<ProjectInfo> {
        migrateLegacyFiles()
        return projectsRoot().listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && ProjectPathUtils.sanitizeProjectName(it.name) != null }
            ?.map { project -> projectInfo(project) }
            ?.sortedBy { it.name.lowercase() }
            ?.toList()
            ?: emptyList()
    }

    fun project(name: String): ProjectInfo? {
        migrateLegacyFiles()
        val safe = ProjectPathUtils.sanitizeProjectName(name) ?: return null
        val root = File(projectsRoot(), safe)
        val canonicalProjectsRoot = projectsRoot().canonicalFile
        if (!root.isDirectory || root.canonicalFile.parentFile?.path != canonicalProjectsRoot.path) return null
        return projectInfo(root)
    }

    fun createProject(
        name: String,
        type: String = "c",
        includeStarter: Boolean = true
    ): Result<ProjectInfo> {
        val safe = ProjectPathUtils.sanitizeProjectName(name)
            ?: return Result.failure(IllegalArgumentException("Invalid project name"))
        val root = File(projectsRoot(), safe)
        if (root.exists()) return Result.failure(IllegalStateException("A project with that name already exists"))
        return try {
            if (!root.mkdirs()) return Result.failure(IllegalStateException("Could not create project"))
            val config = ProjectConfig.defaultFor(safe, type)
            writeConfig(root, config)
            if (includeStarter) {
                ProjectScaffold.filesFor(config.type).forEach { file ->
                    FileTreeRepository.createFile(
                        root,
                        file.relativePath.substringBeforeLast('/', ""),
                        file.relativePath.substringAfterLast('/'),
                        file.content
                    ).getOrThrow()
                }
            }
            Result.success(ProjectInfo(safe, root, config))
        } catch (e: Exception) {
            root.deleteRecursively()
            Result.failure(e)
        }
    }

    fun deleteProject(name: String): Boolean {
        val info = project(name) ?: return false
        return info.root.deleteRecursively()
    }

    fun readConfig(name: String): ProjectConfig? = project(name)?.config

    fun writeConfig(projectRoot: File, config: ProjectConfig) {
        val canonicalRoot = projectRoot.canonicalFile
        val root = projectsRoot().canonicalFile
        require(canonicalRoot.parentFile?.path == root.path) { "Project metadata must stay inside the projects directory" }
        val metadata = File(canonicalRoot, ".codec")
        if (!metadata.exists() && !metadata.mkdirs()) throw IllegalStateException("Could not create project metadata")
        val target = File(metadata, "project.json")
        val temp = File(metadata, "project.json.tmp")
        temp.writeText(config.toJsonString())
        if (target.exists() && !target.delete()) throw IllegalStateException("Could not replace project configuration")
        if (!temp.renameTo(target)) throw IllegalStateException("Could not save project configuration")
    }

    fun resolveFile(projectName: String, relativePath: String): File? =
        project(projectName)?.let { ProjectPathUtils.resolveInside(it.root, relativePath) }

    fun projectInfo(root: File): ProjectInfo {
        val fallback = root.name
        val configFile = File(root, ".codec/project.json")
        val config = if (configFile.isFile) {
            runCatching { ProjectConfig.fromJson(configFile.readText(), fallback) }
                .getOrElse { ProjectConfig.defaultFor(fallback) }
        } else {
            ProjectConfig.defaultFor(fallback)
        }
        return ProjectInfo(fallback, root, config)
    }

    /**
     * One-time compatibility migration for the old flat workspace. We copy
     * source/web files rather than deleting them, so an interrupted upgrade
     * cannot lose a user's work. The new tree intentionally ignores root-level
     * legacy files after they are copied into the `default` project.
     */
    private fun migrateLegacyFiles() {
        val root = projectsRoot()
        val legacy = root.listFiles()
            ?.filter {
                it.isFile &&
                    !it.name.startsWith(".") &&
                    ProjectPathUtils.sanitizeSegment(it.name) != null &&
                    (it.name.endsWith(".c", true) ||
                        it.name.endsWith(".h", true) ||
                        WebFileSupport.isWeb(it.name) ||
                        it.name.endsWith(".md", true) ||
                        it.name.endsWith(".txt", true))
            }
            .orEmpty()
        if (legacy.isEmpty()) return
        val destination = File(root, "default")
        if (!destination.exists()) destination.mkdirs()
        for (source in legacy) {
            val target = File(destination, source.name)
            if (!target.exists() || source.lastModified() > target.lastModified()) {
                runCatching { source.copyTo(target, overwrite = true) }
            }
        }
        if (!File(destination, ".codec/project.json").exists()) {
            runCatching { writeConfig(destination, ProjectConfig.defaultFor("default")) }
        }
    }
}
