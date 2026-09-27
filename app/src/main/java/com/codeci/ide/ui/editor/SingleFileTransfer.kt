package com.codeci.ide.ui.editor

import java.io.File
import java.io.OutputStream
import java.util.UUID

/** Byte-preserving export snapshots. A pending save picker never follows a later project switch. */
object SingleFileTransfer {
    private const val DIRECTORY = "single-file-exports"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    fun snapshot(source: File, cache: File): File {
        require(source.isFile && source.canRead()) { "The file is no longer available" }
        val base = File(cache, DIRECTORY).apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        base.listFiles()?.filter { it.isDirectory && it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
        val folder = File(base, UUID.randomUUID().toString())
        check(folder.mkdir()) { "Could not prepare file export" }
        return try { source.copyTo(File(folder, source.name), overwrite = false) }
        catch (failure: Exception) { folder.deleteRecursively(); throw failure }
    }

    fun pending(cache: File, path: String?): File? {
        if (path == null) return null
        val base = File(cache, DIRECTORY).canonicalFile
        val file = File(path).canonicalFile
        return file.takeIf { it.isFile && it.parentFile?.parentFile == base }
    }

    fun copy(snapshot: File, output: OutputStream) {
        snapshot.inputStream().use { input -> input.copyTo(output) }
    }

    fun discard(cache: File, snapshot: File) {
        pending(cache, snapshot.path)?.parentFile?.deleteRecursively()
    }
}
