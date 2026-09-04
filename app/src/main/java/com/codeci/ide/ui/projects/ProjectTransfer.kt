package com.codeci.ide.ui.projects

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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
        val rawName = queryDisplayName(resolver, documentUri)
            ?: documentUri.lastPathSegment
                ?.substringAfterLast('/')
                ?.let { Uri.decode(it) }
        val name = rawName
            ?.let { ProjectPathUtils.sanitizeArchiveSegment(it) }
            ?: ProjectPathUtils.sanitizeArchiveSegment(fallbackName)
            ?: error("The selected file has an invalid name")
        val isZip = name.endsWith(".zip", ignoreCase = true) ||
            isZipDocument(resolver, documentUri)
        if (isZip) {
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

    /**
     * Phase 24.4 — export a project ZIP into the app cache for sharing
     * (no SAF picker). Written to `cacheDir/shares/<name>.zip`, where an
     * Android share sheet can hand the [getUriForFile] FileProvider URI to
     * another app.
     */
    fun exportZipToCache(
        projectRoot: File,
        cacheDir: File,
        zipName: String = "${projectRoot.name}.zip",
    ): File {
        val safeName = zipName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dir = File(cacheDir, "shares").apply { mkdirs() }
        val target = File(dir, safeName)
        exportZip(projectRoot, target.outputStream())
        return target
    }

    /**
     * Import a SAF stream through a temporary private file, then enumerate the
     * ZIP central directory. Some Android/file-manager ZIP writers produce a
     * local stream containing only the root directory even though their
     * central directory contains all project files; ZipFile reads the complete
     * archive index and also supports ZIP64 archives.
     */
    fun importZip(input: InputStream, destination: File): Int {
        val canonicalDestination = destination.canonicalFile
        require(!canonicalDestination.exists() || canonicalDestination.isDirectory) { "Invalid project destination" }
        if (!canonicalDestination.exists() && !canonicalDestination.mkdirs()) error("Could not create imported project")

        val temporaryZip = File.createTempFile("codec-import-", ".zip", canonicalDestination.parentFile)
        return try {
            input.use { source ->
                temporaryZip.outputStream().use { output ->
                    copyLimited(
                        source,
                        output,
                        MAX_ZIP_ENTRY_BYTES * 10,
                        "ZIP archive is too large"
                    )
                }
            }
            var count = 0
            var fileCount = 0
            var totalBytes = 0L
            ZipFile(temporaryZip).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (++count > MAX_ZIP_ENTRIES) error("ZIP contains too many entries")
                    val entryName = entry.name.replace('\\', '/')
                    // ZIP directory entries are identified by their trailing
                    // `/`; this works for entries with or without external
                    // directory attributes.
                    val isDirectory = entryName.endsWith('/')
                    val rawPath = if (isDirectory) entryName.dropLast(1) else entryName
                    if (rawPath.isEmpty()) throw SecurityException("ZIP contains an unsafe path")
                    val safePath = ProjectPathUtils.sanitizeArchiveRelativePath(rawPath)
                        ?: throw SecurityException("ZIP contains an unsafe path")
                    val target = ProjectPathUtils.resolveInside(canonicalDestination, safePath)
                        ?: throw SecurityException("ZIP escapes the project directory")
                    if (isDirectory) {
                        if (target.exists() && !target.isDirectory) {
                            error("ZIP directory conflicts with a file: $safePath")
                        }
                        if (!target.exists() && !target.mkdirs()) error("Could not create ZIP directory")
                    } else {
                        fileCount++
                        target.parentFile?.let {
                            if (!it.exists() && !it.mkdirs()) error("Could not create ZIP directory")
                        }
                        if (target.exists()) error("ZIP contains duplicate file: $safePath")
                        zip.getInputStream(entry).use { source ->
                            target.outputStream().use { output ->
                                val copied = copyLimited(source, output, MAX_ZIP_ENTRY_BYTES)
                                totalBytes += copied
                                if (totalBytes > MAX_ZIP_ENTRY_BYTES * 10) error("ZIP is too large")
                            }
                        }
                    }
                }
            }
            if (count == 0) error("ZIP contains no entries")
            if (fileCount == 0) error("ZIP contains no files (directory entries: $count)")
            count
        } finally {
            temporaryZip.delete()
        }
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
                val name = ProjectPathUtils.sanitizeArchiveSegment(rawName) ?: continue
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

    private fun isZipDocument(resolver: ContentResolver, uri: Uri): Boolean = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(4)
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                if (read < 0) break
                offset += read
            }
            offset == header.size &&
                header[0] == 'P'.code.toByte() &&
                header[1] == 'K'.code.toByte() &&
                ((header[2].toInt() and 0xff) == 3 ||
                    (header[2].toInt() and 0xff) == 5 ||
                    (header[2].toInt() and 0xff) == 7) &&
                (header[3].toInt() and 0xff) == 4 +
                ((header[2].toInt() and 0xff) - 3)
        } ?: false
    }.getOrDefault(false)

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun copyLimited(
        input: InputStream,
        output: OutputStream,
        limit: Long,
        limitMessage: String = "ZIP entry is too large"
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) error(limitMessage)
            output.write(buffer, 0, read)
        }
        return total
    }
}
