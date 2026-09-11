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

    /** What an [exportAllZip] run did, for the user's result message. */
    data class ExportAllResult(
        /** Roots (of the candidates given) that contained project directories. */
        val rootsWithProjects: Int,
        val projects: Int,
        val entriesWritten: Int,
        val bytesWritten: Long,
        /** Original directory name → flat archive name, when they differ (collisions). */
        val renames: Map<String, String>,
        /** Projects/entries left out (unsanitizable name, unreadable) — reported, never silent. */
        val skipped: List<String>
    )

    /**
     * Phase 42.3 §3 — "Export all projects": one backup ZIP over EVERY
     * project root (the data-loss trap found while reading: projects live at
     * `filesDir/CodeC/projects`, but `FileManager.projectDirCandidates()`
     * also lists `getExternalFilesDir(null)/CodeC/projects` and a legacy
     * shared-storage copy — an "export all" of only the first root silently
     * omits the rest). Today [exportZip] is per-project with NO caps; here
     * the ZIP budget is shared across ALL projects, because 12 projects
     * × 9 999 entries each is exactly what no phone should unpack:
     *
     *  - entries written, TOTAL:            ≤ [MAX_ZIP_ENTRIES]
     *  - uncompressed bytes per file:       ≤ [MAX_ZIP_ENTRY_BYTES]
     *  - uncompressed bytes in the archive: ≤ [MAX_ZIP_ENTRY_BYTES] × 10
     *
     * Entry layout is `<projectName>/<path inside project>` — exactly what
     * [importAllZip] (and the plain per-project [importZip]) reads back,
     * so the round-trip is byte-identical. Symlink-looking files (canonical
     * path mismatch) are skipped exactly like [exportZip] does; entries
     * whose names the import side would refuse are skipped and REPORTED in
     * the result (never silently dropped into a backup the user trusts).
     */
    fun exportAllZip(output: OutputStream, roots: List<File>): ExportAllResult {
        val canonicalRoots = roots
            .mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
            .distinctBy { it.path }
            .filter { it.isDirectory }
        var entries = 0
        var totalBytes = 0L
        var projects = 0
        var rootsWithProjects = 0
        val renames = linkedMapOf<String, String>()
        val skipped = mutableListOf<String>()
        val usedFlatNames = mutableSetOf<String>()

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            for (root in canonicalRoots) {
                val projectDirs = root.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedBy { it.name.lowercase() }
                    .orEmpty()
                if (projectDirs.isNotEmpty()) rootsWithProjects++
                for (projectDir in projectDirs) {
                    val flatBase = ProjectPathUtils.sanitizeArchiveSegment(projectDir.name) ?: run {
                        skipped += projectDir.name
                        continue
                    }
                    // Flattening collisions ("a/b" sanitised like "a_b", or
                    // the same name in two roots) get a deterministic suffix,
                    // recorded for the user's result message — a backup must
                    // never silently merge two projects into one folder.
                    var flat: String = flatBase
                    var suffix = 2
                    while (!usedFlatNames.add(flat)) {
                        flat = "$flatBase-$suffix"
                        suffix++
                    }
                    if (flat != projectDir.name) renames[projectDir.name] = flat
                    projects++
                    projectDir.walkTopDown().forEach { file ->
                        if (file == projectDir || file.absoluteFile.path != file.canonicalFile.path) {
                            return@forEach
                        }
                        val relative = ProjectPathUtils.relativePath(projectDir, file)
                            ?: return@forEach
                        val entryPath = "$flat/$relative"
                        if (entryPath.split('/').any { ProjectPathUtils.sanitizeArchiveSegment(it) == null }) {
                            skipped += entryPath
                            return@forEach
                        }
                        if (++entries > MAX_ZIP_ENTRIES) {
                            error("Backup has too many files ($MAX_ZIP_ENTRIES cap) — export the largest project separately")
                        }
                        if (file.isDirectory) {
                            zip.putNextEntry(ZipEntry("$entryPath/"))
                            zip.closeEntry()
                        } else if (file.isFile) {
                            zip.putNextEntry(ZipEntry(entryPath))
                            var fileBytes = 0L
                            file.inputStream().buffered().use { input ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var n: Int
                                while (input.read(buffer).also { n = it } != -1) {
                                    fileBytes += n
                                    if (fileBytes > MAX_ZIP_ENTRY_BYTES) {
                                        error("Backup failed: $relative is over the ${MAX_ZIP_ENTRY_BYTES / (1024 * 1024)} MB per-file limit")
                                    }
                                    totalBytes += n
                                    if (totalBytes > MAX_ZIP_ENTRY_BYTES * 10) {
                                        error("Backup is too large (${MAX_ZIP_ENTRY_BYTES * 10 / (1024 * 1024)} MB cap) — export in parts")
                                    }
                                    zip.write(buffer, 0, n)
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                }
            }
            zip.finish()
        }
        return ExportAllResult(
            rootsWithProjects = rootsWithProjects,
            projects = projects,
            entriesWritten = entries,
            bytesWritten = totalBytes,
            renames = renames,
            skipped = skipped
        )
    }

    /**
     * Phase 42.3 §3 — restore an [exportAllZip] backup into a projects
     * root. This is [importZip] verbatim with the project's own destination
     * being the ROOT (every entry is `<name>/…`; the import side's own
     * sanitize/resolveInside/no-duplicate/cap rules apply unchanged) — the
     * round trip the phase's exit condition measures.
     */
    fun importAllZip(input: InputStream, projectsRoot: File): Int =
        importZip(input, projectsRoot)

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

        // Phase 39.1 — the scratch zip used to land next to the project
        // (projectsRoot itself), so a crash mid-import left a
        // `codec-import-*.zip` that the hub could mistake for a project.
        // It now lives under java.io.tmpdir (app-private on Android) and
        // is still deleted in finally; TempGc's runs/ root is separate.
        val temporaryZip = File.createTempFile("codec-import-", ".zip")
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
