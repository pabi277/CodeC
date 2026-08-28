package com.codeci.ide.ui.projects

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** SAF and ZIP transfer helpers. Imports always copy into private storage. */
object ProjectTransfer {
    private const val BUFFER_SIZE = 16 * 1024
    private const val MAX_ZIP_ENTRIES = 10_000
    private const val MAX_ZIP_ENTRY_BYTES = 128L * 1024L * 1024L

    fun copyDocumentTree(
        resolver: ContentResolver,
        treeUri: Uri,
        destination: File
    ): Result<Int> = runCatching {
        require(DocumentsContract.isTreeUri(treeUri)) { "The selected location is not a folder" }
        if (!destination.exists() && !destination.mkdirs()) error("Could not create import project")
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        copyDocumentChildren(resolver, treeUri, rootId, destination)
    }

    fun copySingleDocument(
        resolver: ContentResolver,
        documentUri: Uri,
        destination: File,
        fallbackName: String = "imported_file"
    ): Result<String> = runCatching {
        if (!destination.exists() && !destination.mkdirs()) error("Could not create import project")
        val name = queryDisplayName(resolver, documentUri)
            ?.let { ProjectPathUtils.sanitizeSegment(it) }
            ?: ProjectPathUtils.sanitizeSegment(fallbackName)
            ?: error("The selected file has an invalid name")
        if (name.endsWith(".zip", ignoreCase = true)) {
            // The in-project Import File action also accepts ZIPs. Treating a
            // ZIP as an ordinary document leaves one opaque archive in the
            // tree, so expand it into the active private project instead.
            resolver.openInputStream(documentUri)?.use { input ->
                importZip(input, destination)
            } ?: error("Could not read the selected ZIP")
            return@runCatching name
        }
        val target = File(destination, name)
        if (target.exists()) error("A file with that name already exists")
        resolver.openInputStream(documentUri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
        } ?: error("Could not read the selected file")
        name
    }

    fun exportZip(projectRoot: File, output: OutputStream) {
        val canonicalRoot = projectRoot.canonicalFile
        require(canonicalRoot.isDirectory) { "Project does not exist" }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            canonicalRoot.walkTopDown().forEach { file ->
                if (file == canonicalRoot || file.absoluteFile.path != file.canonicalFile.path) return@forEach
                val relative = ProjectPathUtils.relativePath(canonicalRoot, file) ?: return@forEach
                if (file.isDirectory) {
                    zip.putNextEntry(ZipEntry("$relative/"))
                    zip.closeEntry()
                } else if (file.isFile) {
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().use { it.copyTo(zip, BUFFER_SIZE) }
                    zip.closeEntry()
                }
            }
            zip.finish()
        }
    }

    fun importZip(input: InputStream, destination: File): Int {
        val canonicalDestination = destination.canonicalFile
        require(!canonicalDestination.exists() || canonicalDestination.isDirectory) { "Invalid project destination" }
        if (!canonicalDestination.exists() && !canonicalDestination.mkdirs()) error("Could not create imported project")
        var count = 0
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > MAX_ZIP_ENTRIES) error("ZIP contains too many entries")
                val entryName = entry.name.replace('\\', '/')
                // Strip only the one separator used by a normal directory
                // entry. Repeated separators and a root-only entry must be
                // rejected instead of being normalised into a safe path.
                val rawPath = if (entryName.endsWith('/')) entryName.dropLast(1) else entryName
                if (rawPath.isEmpty()) throw SecurityException("ZIP contains an unsafe path")
                val safePath = ProjectPathUtils.sanitizeRelativePath(rawPath)
                    ?: throw SecurityException("ZIP contains an unsafe path")
                val target = ProjectPathUtils.resolveInside(canonicalDestination, safePath)
                    ?: throw SecurityException("ZIP escapes the project directory")
                if (entry.isDirectory) {
                    if (!target.exists() && !target.mkdirs()) error("Could not create ZIP directory")
                } else {
                    target.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("Could not create ZIP directory") }
                    if (target.exists()) error("ZIP contains duplicate file: $safePath")
                    target.outputStream().use { output ->
                        val copied = copyLimited(zip, output, MAX_ZIP_ENTRY_BYTES)
                        totalBytes += copied
                        if (totalBytes > MAX_ZIP_ENTRY_BYTES * 10) error("ZIP is too large")
                    }
                }
                zip.closeEntry()
            }
        }
        return count
    }

    private fun copyDocumentChildren(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocumentId: String,
        destination: File
    ): Int {
        var copied = 0
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumn)
                val rawName = cursor.getString(nameColumn) ?: "untitled"
                val name = ProjectPathUtils.sanitizeSegment(rawName) ?: continue
                val mime = cursor.getString(mimeColumn)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                val target = File(destination, name)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!target.exists() && !target.mkdirs()) error("Could not create imported folder")
                    copied += copyDocumentChildren(resolver, treeUri, id, target)
                } else {
                    if (target.exists()) continue
                    resolver.openInputStream(childUri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
                    } ?: continue
                    copied++
                }
            }
        } ?: error("Could not read the selected folder")
        return copied
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) error("ZIP entry is too large")
            output.write(buffer, 0, read)
        }
        return total
    }
}
