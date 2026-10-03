package com.codeci.ide.ui.projects

import com.codeci.ide.ui.ai.AiProjectReader
import com.codeci.ide.ui.ai.AiTaskMemory
import com.codeci.ide.ui.ai.AiTaskMemoryCodec
import com.codeci.ide.ui.ai.AiTaskMemoryLimits
import java.io.File
import java.io.FileOutputStream

/**
 * Level 9's only durable task-memory writer. Data lives under the Android
 * no-backup root at `ai/task/<project>/`, never in the project tree.
 */
class AiTaskMemoryStore(
    private val noBackupRoot: File,
    projectName: String,
    /** Level 10 supplies the UI switch; false keeps working memory task-local and clears retained copies. */
    private val persistentEnabled: Boolean = true
) {
    private val safeProjectName = ProjectPathUtils.sanitizeProjectName(projectName)

    fun load(
        projectRoot: File,
        admittedPaths: Collection<String>,
        dirtyBuffers: Map<String, String> = emptyMap()
    ): AiTaskMemory = synchronized(STORE_LOCK) {
        if (!persistentEnabled) {
            safeProjectName?.let { clearProject(noBackupRoot, it) }
            return@synchronized AiTaskMemory.EMPTY
        }
        val directory = projectDirectory(noBackupRoot, safeProjectName, create = false)
            ?: return@synchronized AiTaskMemory.EMPTY
        // A process may have stopped while replacing the bounded snapshot. The
        // temporary file is never a source of truth; remove it before loading.
        val temp = File(directory, TEMP_FILE)
        if (temp.exists()) runCatching { temp.delete() }
        val file = File(directory, MEMORY_FILE)
        if (!file.exists()) return@synchronized AiTaskMemory.EMPTY
        if (AiProjectReader.isSymlink(file) || !file.isFile || file.length() > AiTaskMemoryLimits.MAX_STORE_BYTES) {
            runCatching { file.delete() }
            return@synchronized AiTaskMemory.EMPTY
        }
        val bytes = runCatching { file.readBytes() }.getOrNull()
            ?.takeIf { it.size <= AiTaskMemoryLimits.MAX_STORE_BYTES }
            ?: run {
                runCatching { file.delete() }
                return@synchronized AiTaskMemory.EMPTY
            }
        val decoded = AiTaskMemoryCodec.decode(bytes)
        if (decoded == null) {
            runCatching { file.delete() }
            return@synchronized AiTaskMemory.EMPTY
        }
        val reconciled = decoded.reconcile(projectRoot, admittedPaths, dirtyBuffers)
        if (reconciled != decoded && !save(reconciled, admittedPaths)) {
            // Fail closed: never leave an invalidated source snapshot on disk
            // merely because the replacement could not be written.
            runCatching { file.delete() }
        }
        reconciled
    }

    /** Revalidate before each agent request; only current versions may be reused. */
    fun reconcile(
        memory: AiTaskMemory,
        projectRoot: File,
        admittedPaths: Collection<String>,
        dirtyBuffers: Map<String, String>
    ): AiTaskMemory = memory.reconcile(projectRoot, admittedPaths, dirtyBuffers)

    /**
     * The last write filter is applied here as well as at model-update time.
     * The single bounded binary file is replaced through a synced temporary file.
     */
    fun save(memory: AiTaskMemory, admittedPaths: Collection<String>): Boolean = synchronized(STORE_LOCK) {
        if (!persistentEnabled) {
            val safeName = safeProjectName ?: return@synchronized false
            return@synchronized clearProject(noBackupRoot, safeName)
        }
        val directory = projectDirectory(noBackupRoot, safeProjectName, create = !memory.isEmpty())
            ?: return@synchronized false
        val file = File(directory, MEMORY_FILE)
        if (memory.isEmpty()) {
            if (AiProjectReader.isSymlink(file)) return@synchronized false
            return@synchronized (!file.exists() || file.delete())
        }
        val clean = memory.sanitizedForStore(admittedPaths)
        if (clean.isEmpty()) {
            if (AiProjectReader.isSymlink(file)) return@synchronized false
            return@synchronized (!file.exists() || file.delete())
        }
        val bytes = AiTaskMemoryCodec.encode(clean) ?: return@synchronized false
        if (bytes.size > AiTaskMemoryLimits.MAX_STORE_BYTES || AiProjectReader.isSymlink(file)) return@synchronized false
        val tmp = File(directory, TEMP_FILE)
        if (AiProjectReader.isSymlink(tmp)) return@synchronized false
        try {
            FileOutputStream(tmp).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                tmp.delete()
                return@synchronized false
            }
            true
        } catch (_: Exception) {
            runCatching { tmp.delete() }
            false
        }
    }

    companion object {
        private const val MEMORY_FILE = "task-memory.bin"
        private const val TEMP_FILE = "task-memory.tmp"
        private val STORE_LOCK = Any()

        /** Remove this project's task memory after its project directory is deleted. */
        fun clearProject(noBackupRoot: File, projectName: String): Boolean = synchronized(STORE_LOCK) {
            val safeName = ProjectPathUtils.sanitizeProjectName(projectName) ?: return@synchronized false
            val directory = projectDirectory(noBackupRoot, safeName, create = false)
                ?: return@synchronized false
            if (AiProjectReader.isSymlink(directory)) return@synchronized false
            !directory.exists() || directory.deleteRecursively()
        }

        /** Key deletion clears task memory for every project, independently of chat state. */
        fun clearAll(noBackupRoot: File): Boolean = synchronized(STORE_LOCK) {
            val taskDirectory = taskDirectory(noBackupRoot, create = false) ?: return@synchronized false
            if (AiProjectReader.isSymlink(taskDirectory)) return@synchronized false
            !taskDirectory.exists() || taskDirectory.deleteRecursively()
        }

        private fun projectDirectory(noBackupRoot: File, projectName: String?, create: Boolean): File? {
            val safeName = projectName ?: return null
            val task = taskDirectory(noBackupRoot, create) ?: return null
            if (create && !task.exists() && !task.mkdirs()) return null
            val taskCanonical = task.canonicalFile
            val directory = File(taskCanonical, safeName)
            if (directory.exists() && AiProjectReader.isSymlink(directory)) return null
            val expected = File(taskCanonical, safeName).absolutePath
            val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return null
            if (canonical.path != expected) return null
            if (create && !directory.exists() && !directory.mkdirs()) return null
            return canonical
        }

        private fun taskDirectory(noBackupRoot: File, create: Boolean): File? {
            if (AiProjectReader.isSymlink(noBackupRoot)) return null
            val root = runCatching { noBackupRoot.canonicalFile }.getOrNull() ?: return null
            val ai = File(root, "ai")
            if (ai.exists() && AiProjectReader.isSymlink(ai)) return null
            val task = File(ai, "task")
            if (task.exists() && AiProjectReader.isSymlink(task)) return null
            val expected = File(root, "ai/task").absolutePath
            val canonical = runCatching { task.canonicalFile }.getOrNull() ?: return null
            if (canonical.path != expected) return null
            if (create) {
                if (!ai.exists() && !ai.mkdir()) return null
                if (!task.exists() && !task.mkdir()) return null
            }
            return canonical
        }
    }
}
