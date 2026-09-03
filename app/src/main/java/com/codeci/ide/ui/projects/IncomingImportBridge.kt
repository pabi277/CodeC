package com.codeci.ide.ui.projects

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.codeci.ide.ui.utils.FileNameUtils
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 24.7 — a shared file/ZIP arriving through the "Open with CodeC"
 * intent filters lands as an editable single file (or a project) and then the
 * editor navigates to it. MainActivity copies the content; MainApp observes
 * [state] and takes the user to the editor.
 *
 * Data holder only: no Compose, no Android lifecycle — host-testable.
 */
object IncomingImportBridge {

    private val _state = MutableStateFlow<IncomingImport?>(null)
    val state = _state.asStateFlow()

    data class IncomingImport(
        val projectName: String?,
        val fileName: String,
        val mimeType: String?,
    )

    fun offer(import: IncomingImport) {
        _state.value = import
    }

    fun consume(): IncomingImport? = _state.value

    fun clear() {
        _state.value = null
    }

    /**
     * Copies the shared content into the app-private single-files folder and
     * returns the resulting [IncomingImport]. Returns null when the source
     * cannot be read or the target already exists with a different content.
     */
    fun importFile(context: Context, uri: Uri, mimeType: String?): IncomingImport? {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.let { Uri.decode(it) }
            ?: "imported_file"
        val safeName = FileNameUtils.sanitizeFileName(displayName) ?: "imported_file"
        val base = ProjectPathUtils.sanitizeProjectName(
            safeName.substringBeforeLast('.', "imported").ifBlank { "imported" }
        ) ?: "imported"
        val root = File(context.filesDir, "CodeC/projects")
        root.mkdirs()
        val projectName = ProjectsHub.uniqueProjectName(
            base,
            root.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet().orEmpty()
        )
        val projectRoot = File(root, projectName).apply { mkdirs() }
        val target = File(projectRoot, safeName)
        val input = resolver.openInputStream(uri) ?: return null
        input.use { source ->
            source.copyTo(target.outputStream())
            target.parentFile?.mkdirs()
        }
        return IncomingImport(projectName = projectName, fileName = safeName, mimeType = mimeType)
    }

    /**
     * Extracts a shared ZIP into a fresh project under `CodeC/projects` and
     * returns the first text file so the editor opens the import directly.
     * Uses the existing SAF-free [ProjectTransfer.importZip] expansion.
     */
    fun importZip(context: Context, uri: Uri): IncomingImport? {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.let { Uri.decode(it) }
            ?: "imported.zip"
        val base = ProjectPathUtils.sanitizeProjectName(
            displayName.substringBeforeLast('.', "imported").trim().ifBlank { "imported" }
        ) ?: "imported"
        val root = File(context.filesDir, "CodeC/projects")
        root.mkdirs()
        val projectName = ProjectsHub.uniqueProjectName(
            base,
            root.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet().orEmpty()
        )
        val projectRoot = File(root, projectName)
        val input = resolver.openInputStream(uri) ?: return null
        input.use { source -> ProjectTransfer.importZip(source, projectRoot) }
        val first = findFirstTextFile(projectRoot)
        return IncomingImport(projectName = projectName, fileName = first, mimeType = "application/zip")
    }

    private fun findFirstTextFile(root: File): String {
        val textExt = setOf(
            "c", "h", "cpp", "hpp", "py", "js", "ts", "css", "html", "htm",
            "json", "md", "txt", "sh", "go", "rs", "rb", "php", "lua", "java", "kt", "xml"
        )
        return root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in textExt }
            .sortedBy { it.path }
            .firstOrNull()
            ?.let { root.toRelativeString(it) }
            ?: ""
    }

    private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}
