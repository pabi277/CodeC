package com.codeci.ide.ui.utils

import android.content.Context
import android.os.Environment
import java.io.File

data class FileInfo(
    val name: String,
    val size: Long,
    val lastModified: Long,
    val absolutePath: String
)

class FileManager(private val context: Context) {

    /**
     * Canonical project folder: app-private `filesDir/CodeC/projects`.
     *
     * Emulated storage (`/storage/emulated/0/…`, including getExternalFilesDir)
     * is mounted `noexec`, so `./a.out` fails with "Permission denied" even
     * after a successful `cc`. Termux keeps `$HOME` on `/data/data/<pkg>/files`
     * for the same reason. Existing `.c` files on shared storage are copied in.
     */
    fun getProjectDir(): File {
        val internal = File(context.filesDir, "CodeC/projects")
        try {
            if (!internal.exists()) internal.mkdirs()
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Could not create ${internal.absolutePath}", e)
        }
        try {
            migrateCSources(projectDirCandidates(), internal)
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Could not migrate sources into ${internal.absolutePath}", e)
        }
        if (internal.exists() && internal.canWrite()) return internal
        for (dir in projectDirCandidates()) {
            try {
                if (!dir.exists()) dir.mkdirs()
            } catch (e: Exception) {
                AppLogger.e("FileManager", "Could not create ${dir.absolutePath}", e)
            }
            if (dir.exists() && dir.canWrite()) return dir
        }
        return internal
    }

    /** Shared-storage copies first (migration sources), then the executable dir. */
    fun projectDirCandidates(): List<File> = listOfNotNull(
        File(Environment.getExternalStorageDirectory(), "CodeC/projects"),
        context.getExternalFilesDir(null)?.let { File(it, "CodeC/projects") },
        File(context.filesDir, "CodeC/projects")
    )

    fun createDirectory(): Boolean {
        val dir = getProjectDir()
        return dir.exists() && dir.canWrite()
    }

    fun saveFile(fileName: String, content: String): Boolean {
        return try {
            val file = FileNameUtils.resolveSafeFile(getProjectDir(), fileName) ?: return false
            file.writeText(content)
            AppLogger.d("FileManager", "Saved file: ${file.name} (${content.length} chars)")
            true
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Failed to save file: $fileName", e)
            false
        }
    }

    fun loadFile(fileName: String): String? {
        return try {
            val file = FileNameUtils.resolveSafeFile(getProjectDir(), fileName) ?: return null
            if (file.exists() && file.canRead()) {
                AppLogger.d("FileManager", "Loaded file: ${file.name}")
                file.readText()
            } else {
                AppLogger.e("FileManager", "Cannot read file: $fileName")
                null
            }
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Failed to load file: $fileName", e)
            null
        }
    }

    fun deleteFile(fileName: String): Boolean {
        return try {
            val file = FileNameUtils.resolveSafeFile(getProjectDir(), fileName) ?: return false
            file.exists() && file.delete()
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Failed to delete file: $fileName", e)
            false
        }
    }

    fun renameFile(oldName: String, newName: String): Boolean {
        return try {
            val dir = getProjectDir()
            val oldFile = FileNameUtils.resolveSafeFile(dir, oldName) ?: return false
            val newFile = FileNameUtils.resolveSafeFile(dir, newName) ?: return false
            if (oldFile.exists() && !newFile.exists()) {
                oldFile.renameTo(newFile)
            } else {
                false
            }
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Failed to rename $oldName to $newName", e)
            false
        }
    }

    fun listFiles(): List<FileInfo> {
        return try {
            val dir = getProjectDir()
            val files = dir.listFiles() ?: return emptyList()
            files.filter { it.isFile && it.name.endsWith(".c") }
                .map { FileInfo(it.name, it.length(), it.lastModified(), it.absolutePath) }
                .sortedByDescending { it.lastModified }
        } catch (e: Exception) {
            AppLogger.e("FileManager", "Failed to list files", e)
            emptyList()
        }
    }
}

/**
 * Copies `.c` files from [fromDirs] into [into], overwriting when the source
 * is newer. Skips [into] itself. Returns how many files were copied.
 */
fun migrateCSources(fromDirs: List<File>, into: File): Int {
    if (!into.exists()) into.mkdirs()
    val destPath = try {
        into.canonicalFile
    } catch (_: Exception) {
        into.absoluteFile
    }
    var copied = 0
    for (dir in fromDirs) {
        val srcDir = try {
            dir.canonicalFile
        } catch (_: Exception) {
            dir.absoluteFile
        }
        if (srcDir == destPath || !srcDir.isDirectory) continue
        val files = srcDir.listFiles() ?: continue
        for (src in files) {
            if (!src.isFile || !src.name.endsWith(".c")) continue
            val dest = File(into, src.name)
            if (!dest.exists() || src.lastModified() > dest.lastModified()) {
                try {
                    src.copyTo(dest, overwrite = true)
                    copied++
                } catch (_: Exception) {
                }
            }
        }
    }
    return copied
}
