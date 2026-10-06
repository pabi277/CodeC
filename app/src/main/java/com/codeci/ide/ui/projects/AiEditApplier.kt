package com.codeci.ide.ui.projects

import com.codeci.ide.ui.ai.AiCurrentFileState
import com.codeci.ide.ui.ai.AiEditOp
import com.codeci.ide.ui.ai.AiEditProposal
import com.codeci.ide.ui.ai.AiEditProposalParser
import com.codeci.ide.ui.ai.AiProjectFiles
import com.codeci.ide.ui.ai.AiProposedFileEdit
import com.codeci.ide.ui.editor.LineEndings
import java.io.File

/**
 * Phase 79 (AI Level 3) — the diff-approved project file writer and 1-task
 * rollback journal (`03_EDIT_REVIEW_AND_UNDO.md`).
 *
 * Lives in `com.codeci.ide.ui.projects` alongside [ProjectManager],
 * [ProjectPathUtils], and [FileTreeRepository], so `ui/ai` remains pure of
 * direct file writes (`AiHelperWiringTest`). Uses `java.io.File` only (minSdk
 * 24 safe, host-tested on a plain JVM by `AiEditApplierTest`).
 *
 * ## Owner decisions enforced here (2026-10-01)
 *
 *  - **D1 amendment:** writes happen only after the user approves the local
 *    diff; never stages or commits to Git, never runs commands or installs
 *    packages.
 *  - **D6 amendment:** stores a 1-task preimage journal in
 *    `noBackupFilesDir/ai/undo/<project>/task.journal` (outside
 *    `files/CodeC/projects/`, so it is never backed up and never appears in
 *    the project tree or `git status`). Capped at [MAX_JOURNAL_FILES] files
 *    and [MAX_JOURNAL_BYTES] total preimage bytes. Survives process death;
 *    deleted on undo, replaced on the next applied AI task, deleted when the
 *    project is deleted ([clearProjectJournal]), and cleared when the API key
 *    is deleted ([clearAllJournals]).
 */
data class AiUndoFileSummary(
    val path: String,
    val op: AiEditOp
)

data class AiUndoSummary(
    val projectName: String,
    val timestampMs: Long,
    val files: List<AiUndoFileSummary>
)

sealed class AiApplyOutcome {
    data class Applied(
        val appliedPaths: List<String>,
        val deletedPaths: List<String>,
        val undoSummary: AiUndoSummary
    ) : AiApplyOutcome()

    /** One or more target files changed in the editor or on disk since the proposal baseline. */
    data class BaselineConflict(val stalePaths: List<String>) : AiApplyOutcome()

    /** Validation or IO failure; [rolledBack] indicates whether partial writes were restored. */
    data class Failed(val message: String, val rolledBack: Boolean = true) : AiApplyOutcome()
}

sealed class AiUndoOutcome {
    data class Restored(
        val restoredPaths: List<String>,
        val deletedCreatedPaths: List<String>
    ) : AiUndoOutcome()

    /** The user edited one or more task files after the AI change was applied. */
    data class UserEditedConflict(val modifiedPaths: List<String>) : AiUndoOutcome()

    object NothingToUndo : AiUndoOutcome()

    data class Failed(val message: String) : AiUndoOutcome()
}

object AiEditApplier {

    const val MAX_JOURNAL_FILES = 5
    const val MAX_JOURNAL_BYTES = 256 * 1024

    private const val JOURNAL_HEADER = "CODEC_AI_UNDO_V1"
    private const val JOURNAL_FILE_NAME = "task.journal"

    internal data class JournalEntry(
        val path: String,
        val op: AiEditOp,
        val existedBefore: Boolean,
        val preimageBytes: ByteArray,
        val appliedBytes: ByteArray
    )

    internal data class TaskJournal(
        val projectName: String,
        val timestampMs: Long,
        val entries: List<JournalEntry>
    ) {
        fun toSummary(): AiUndoSummary = AiUndoSummary(
            projectName = projectName,
            timestampMs = timestampMs,
            files = entries.map { AiUndoFileSummary(it.path, it.op) }
        )
    }

    /**
     * Reads the current state of [paths] from [projectRoot], substituting
     * [dirtyBuffers] (LF-normalized text of unsaved open editor tabs) when a
     * tab has unsaved edits.
     */
    fun captureCurrentStates(
        projectRoot: File,
        paths: Collection<String>,
        dirtyBuffers: Map<String, String> = emptyMap()
    ): Map<String, AiCurrentFileState> {
        val root = projectRoot.canonicalFileSafe() ?: return emptyMap()
        val out = LinkedHashMap<String, AiCurrentFileState>()
        for (raw in paths) {
            val safe = AiEditProposalParser.validateTargetPath(raw) ?: continue
            val dirtyText = dirtyBuffers.entries.firstOrNull {
                AiProjectFiles.samePath(it.key, safe)
            }?.value
            if (dirtyText != null) {
                out[safe] = AiCurrentFileState(
                    path = safe,
                    exists = true,
                    content = LineEndings.normalizeToLf(dirtyText)
                )
                continue
            }
            val file = resolveSafeTarget(root, safe)
            if (file == null || !file.exists() || !file.isFile) {
                out[safe] = AiCurrentFileState(path = safe, exists = false, content = "")
            } else {
                val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: ""
                out[safe] = AiCurrentFileState(
                    path = safe,
                    exists = true,
                    content = LineEndings.normalizeToLf(text)
                )
            }
        }
        return out
    }

    /**
     * Validates and applies the selected files of [proposal] to [projectRoot],
     * writing a write-ahead 1-task preimage journal under [noBackupRoot] first.
     */
    fun apply(
        projectRoot: File,
        noBackupRoot: File,
        projectName: String,
        proposal: AiEditProposal,
        dirtyBuffers: Map<String, String> = emptyMap(),
        nowMs: Long = System.currentTimeMillis()
    ): AiApplyOutcome {
        val safeProject = ProjectPathUtils.sanitizeProjectName(projectName)
            ?: return AiApplyOutcome.Failed("Invalid project name.")
        val root = projectRoot.canonicalFileSafe()
            ?: return AiApplyOutcome.Failed("Project folder is not accessible.")
        if (!root.isDirectory || isSymlink(projectRoot)) {
            return AiApplyOutcome.Failed("Project root is not a valid directory.")
        }
        // Phase 93 — the owner's *"it's showing error no permission"*: a project
        // folder CodeC cannot write to is a **permission** fact, not a mystery, and
        // this is where it is named with the switch to flip. (Projects CodeC
        // created live in its own storage and never reach this branch.)
        if (!root.canRead() || !root.canWrite()) {
            return AiApplyOutcome.Failed(STORAGE_PERMISSION_MESSAGE)
        }

        val selected = proposal.selectedFiles
        if (selected.isEmpty()) {
            return AiApplyOutcome.Failed("Select at least one file to apply.")
        }
        if (selected.size > MAX_JOURNAL_FILES) {
            return AiApplyOutcome.Failed("Cannot apply more than $MAX_JOURNAL_FILES files in one task.")
        }

        // 1. Validate every selected path, symlink chain, and discard lock before reading/writing.
        val resolvedTargets = LinkedHashMap<String, File>()
        for (edit in selected) {
            val (safePath, reason) = AiEditProposalParser.validateTargetPathWithReason(edit.path)
            if (safePath == null || safePath != edit.path) {
                return AiApplyOutcome.Failed(reason ?: "Rejected unsafe file path: ${edit.path}")
            }
            if (GitDiscardEditors.blocks(root, safePath)) {
                return AiApplyOutcome.Failed("'$safePath' is currently being restored by Git. Try again in a moment.")
            }
            val target = resolveSafeTarget(root, safePath)
                ?: return AiApplyOutcome.Failed("Rejected path escaping project root or crossing a symlink: $safePath")
            resolvedTargets[safePath] = target
        }

        // 2. Verify baseline freshness against live dirty buffers + disk.
        val currentStates = captureCurrentStates(root, selected.map { it.path }, dirtyBuffers)
        val stale = proposal.checkStale(currentStates)
        if (stale.isNotEmpty()) {
            return AiApplyOutcome.BaselineConflict(stale)
        }

        // 3. Build preimage & post-image bytes and verify journal byte cap.
        val entries = mutableListOf<JournalEntry>()
        var totalPreimageBytes = 0
        for (edit in selected) {
            val target = resolvedTargets.getValue(edit.path)
            val existed = target.exists()
            if (edit.op == AiEditOp.CREATE && existed) {
                return AiApplyOutcome.BaselineConflict(listOf(edit.path))
            }
            if ((edit.op == AiEditOp.MODIFY || edit.op == AiEditOp.DELETE) && (!existed || !target.isFile)) {
                return AiApplyOutcome.BaselineConflict(listOf(edit.path))
            }
            val preBytes = if (existed) {
                runCatching { target.readBytes() }.getOrElse {
                    return AiApplyOutcome.Failed("Could not read existing file '${edit.path}' before applying.")
                }
            } else {
                ByteArray(0)
            }
            totalPreimageBytes += preBytes.size
            if (totalPreimageBytes > MAX_JOURNAL_BYTES) {
                return AiApplyOutcome.Failed(
                    "The files to back up exceed the ${MAX_JOURNAL_BYTES / 1024} KB undo journal limit."
                )
            }

            val appliedBytes = when (edit.op) {
                AiEditOp.DELETE -> ByteArray(0)
                AiEditOp.CREATE -> LineEndings.toNative(edit.newContent, LineEndings.LF).toByteArray(Charsets.UTF_8)
                AiEditOp.MODIFY -> {
                    val oldRaw = String(preBytes, Charsets.UTF_8)
                    val ending = LineEndings.detect(oldRaw)
                    LineEndings.toNative(edit.newContent, ending).toByteArray(Charsets.UTF_8)
                }
            }
            entries += JournalEntry(
                path = edit.path,
                op = edit.op,
                existedBefore = existed,
                preimageBytes = preBytes,
                appliedBytes = appliedBytes
            )
        }

        // 4. Write the 1-task preimage journal before touching any project file.
        val journal = TaskJournal(
            projectName = safeProject,
            timestampMs = nowMs,
            entries = entries
        )
        if (!writeJournal(noBackupRoot, safeProject, journal)) {
            return AiApplyOutcome.Failed("Could not save the undo journal before applying changes.")
        }

        // 5. Apply each file in order; if any step fails, roll back already-touched files.
        val completed = mutableListOf<JournalEntry>()
        val appliedPaths = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()

        for (entry in entries) {
            val target = resolvedTargets.getValue(entry.path)
            val ok = runCatching {
                when (entry.op) {
                    AiEditOp.CREATE, AiEditOp.MODIFY -> {
                        val parent = target.parentFile
                        if (parent != null && !parent.exists() && !parent.mkdirs()) {
                            throw IllegalStateException("Could not create folder for ${entry.path}")
                        }
                        writeBytesAtomically(target, entry.appliedBytes)
                    }
                    AiEditOp.DELETE -> {
                        if (target.exists() && !target.delete()) {
                            throw IllegalStateException("Could not delete ${entry.path}")
                        }
                    }
                }
            }.isSuccess

            if (!ok) {
                val rolledBack = rollbackEntries(root, completed)
                clearProjectJournal(noBackupRoot, safeProject)
                return AiApplyOutcome.Failed(
                    message = if (rolledBack) {
                        "Could not apply changes to '${entry.path}'. Earlier files in this batch were restored."
                    } else {
                        "Could not apply changes to '${entry.path}', and automatic rollback was only partial. Check your project files."
                    },
                    rolledBack = rolledBack
                )
            }

            completed += entry
            if (entry.op == AiEditOp.DELETE) {
                deletedPaths += entry.path
            } else {
                appliedPaths += entry.path
            }
        }

        return AiApplyOutcome.Applied(
            appliedPaths = appliedPaths,
            deletedPaths = deletedPaths,
            undoSummary = journal.toSummary()
        )
    }

    /**
     * Restores the exact pre-apply bytes for the last applied AI task of
     * [projectName]. If the user modified any affected file after the AI task
     * was applied (either on disk or in [dirtyPaths]) and [force] is false,
     * returns [AiUndoOutcome.UserEditedConflict] without overwriting.
     */
    fun undo(
        projectRoot: File,
        noBackupRoot: File,
        projectName: String,
        dirtyPaths: Set<String> = emptySet(),
        force: Boolean = false
    ): AiUndoOutcome {
        val safeProject = ProjectPathUtils.sanitizeProjectName(projectName)
            ?: return AiUndoOutcome.NothingToUndo
        val root = projectRoot.canonicalFileSafe()
            ?: return AiUndoOutcome.Failed("Project folder is not accessible.")
        // Phase 93 — undo writes too, so it names the same permission fact apply does.
        if (!root.canRead() || !root.canWrite()) {
            return AiUndoOutcome.Failed(STORAGE_PERMISSION_MESSAGE)
        }
        val journal = readJournal(noBackupRoot, safeProject)
            ?: return AiUndoOutcome.NothingToUndo

        val resolvedTargets = LinkedHashMap<String, File>()
        for (entry in journal.entries) {
            if (GitDiscardEditors.blocks(root, entry.path)) {
                return AiUndoOutcome.Failed("'${entry.path}' is currently being restored by Git. Try again in a moment.")
            }
            val target = resolveSafeTarget(root, entry.path)
                ?: return AiUndoOutcome.Failed("Rejected undo path escaping project root or crossing a symlink: ${entry.path}")
            resolvedTargets[entry.path] = target
        }

        if (!force) {
            val conflicts = mutableListOf<String>()
            for (entry in journal.entries) {
                val isDirtyInEditor = dirtyPaths.any { AiProjectFiles.samePath(it, entry.path) }
                if (isDirtyInEditor) {
                    conflicts += entry.path
                    continue
                }
                val target = resolvedTargets.getValue(entry.path)
                when (entry.op) {
                    AiEditOp.CREATE, AiEditOp.MODIFY -> {
                        if (!target.exists() || !target.isFile) {
                            conflicts += entry.path
                        } else {
                            val currentBytes = runCatching { target.readBytes() }.getOrNull()
                            if (currentBytes == null || !currentBytes.contentEquals(entry.appliedBytes)) {
                                conflicts += entry.path
                            }
                        }
                    }
                    AiEditOp.DELETE -> {
                        if (target.exists()) {
                            conflicts += entry.path
                        }
                    }
                }
            }
            if (conflicts.isNotEmpty()) {
                return AiUndoOutcome.UserEditedConflict(conflicts)
            }
        }

        val restoredPaths = mutableListOf<String>()
        val deletedCreatedPaths = mutableListOf<String>()
        val ok = rollbackEntries(root, journal.entries, restoredPaths, deletedCreatedPaths)
        if (!ok) {
            return AiUndoOutcome.Failed("Could not restore all files from the undo journal. Check your project files.")
        }

        clearProjectJournal(noBackupRoot, safeProject)
        return AiUndoOutcome.Restored(
            restoredPaths = restoredPaths,
            deletedCreatedPaths = deletedCreatedPaths
        )
    }

    /** Returns the summary of the persisted 1-task journal for [projectName], if any. */
    fun readUndoSummary(noBackupRoot: File, projectName: String): AiUndoSummary? {
        val safeProject = ProjectPathUtils.sanitizeProjectName(projectName) ?: return null
        return readJournal(noBackupRoot, safeProject)?.toSummary()
    }

    /** Deletes the undo journal for [projectName] (called on undo or project deletion). */
    fun clearProjectJournal(noBackupRoot: File, projectName: String) {
        val safeProject = ProjectPathUtils.sanitizeProjectName(projectName) ?: return
        val dir = File(File(noBackupRoot, "ai/undo"), safeProject)
        if (dir.exists()) runCatching { dir.deleteRecursively() }
    }

    /** Deletes all AI undo journals across projects (called when the API key is deleted). */
    fun clearAllJournals(noBackupRoot: File) {
        val undoDir = File(noBackupRoot, "ai/undo")
        if (undoDir.exists()) runCatching { undoDir.deleteRecursively() }
    }

    // ---- internal rollback & safe path helpers ----------------------------

    private fun rollbackEntries(
        canonicalRoot: File,
        entries: List<JournalEntry>,
        restoredOut: MutableList<String>? = null,
        deletedOut: MutableList<String>? = null
    ): Boolean {
        var allOk = true
        for (entry in entries.asReversed()) {
            val target = resolveSafeTarget(canonicalRoot, entry.path)
            if (target == null) {
                allOk = false
                continue
            }
            val stepOk = runCatching {
                if (entry.existedBefore) {
                    val parent = target.parentFile
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw IllegalStateException("Could not recreate parent folder")
                    }
                    writeBytesAtomically(target, entry.preimageBytes)
                    restoredOut?.add(entry.path)
                } else {
                    if (target.exists() && !target.delete()) {
                        throw IllegalStateException("Could not delete created file")
                    }
                    pruneEmptyParents(canonicalRoot, target.parentFile)
                    deletedOut?.add(entry.path)
                }
            }.isSuccess
            if (!stepOk) allOk = false
        }
        return allOk
    }

    private fun pruneEmptyParents(canonicalRoot: File, startDir: File?) {
        var dir = startDir
        while (dir != null) {
            val canon = dir.canonicalFileSafe() ?: break
            if (canon.path == canonicalRoot.path || !canon.path.startsWith(canonicalRoot.path + File.separator)) {
                break
            }
            val children = dir.listFiles()
            if (children != null && children.isEmpty()) {
                val parent = dir.parentFile
                dir.delete()
                dir = parent
            } else {
                break
            }
        }
    }

    private fun writeBytesAtomically(target: File, bytes: ByteArray) {
        val parent = target.parentFile ?: throw IllegalStateException("Missing parent directory")
        val tmp = File(parent, ".${target.name}.codec_ai_tmp")
        try {
            tmp.writeBytes(bytes)
            if (target.exists() && !target.delete()) {
                // Fallback to direct overwrite if delete before rename is blocked.
                target.writeBytes(bytes)
                tmp.delete()
                return
            }
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            throw e
        }
    }

    /**
     * Resolves [relativePath] inside [canonicalRoot], verifying both
     * [ProjectPathUtils.resolveInside] and that no existing path segment from
     * [canonicalRoot] to the target is a symlink (`canonicalPath != absolutePath`,
     * minSdk 24 safe).
     */
    private fun resolveSafeTarget(canonicalRoot: File, relativePath: String): File? {
        val safe = AiEditProposalParser.validateTargetPath(relativePath) ?: return null
        val resolved = ProjectPathUtils.resolveInside(canonicalRoot, safe) ?: return null
        if (resolved.path == canonicalRoot.path) return null

        var current = canonicalRoot
        for (seg in safe.split('/')) {
            val next = File(current, seg)
            if (next.exists() && isSymlink(next)) {
                return null
            }
            val nextCanon = next.canonicalFileSafe() ?: return null
            if (nextCanon.path != canonicalRoot.path &&
                !nextCanon.path.startsWith(canonicalRoot.path + File.separator)
            ) {
                return null
            }
            current = next
        }
        return current
    }

    /** The minSdk-24-safe symlink check (`ProjectSearch.kt:181-182`). */
    private fun isSymlink(file: File): Boolean =
        runCatching { file.canonicalPath != file.absolutePath }.getOrDefault(false)

    private fun File.canonicalFileSafe(): File? = runCatching { canonicalFile }.getOrNull()

    /**
     * Phase 93 — one sentence for a project folder the app cannot reach, pointing
     * at the one screen that fixes it. Shared by apply and undo so the two doors
     * cannot describe the same failure differently; the version-exact switch is
     * named where the phone's Android version is known (`StorageAccessPolicy.fixSteps`
     * in the AI preflight, the storage row and the self-check report).
     */
    val STORAGE_PERMISSION_MESSAGE: String =
        "CodeC cannot read or write this project folder. If it lives outside CodeC's own storage, " +
            "grant storage access to CodeC in Android Settings (Settings → Apps → CodeC → Permissions) — " +
            "allow access to files and media, and on Android 11 and later choose " +
            "\"Allow management of all files\" (the same switch the Grant access button opens)."

    // ---- pure hex-framed journal serialization (no org.json / Robolectric) -

    private fun journalFile(noBackupRoot: File, safeProject: String): File =
        File(File(noBackupRoot, "ai/undo/$safeProject"), JOURNAL_FILE_NAME)

    internal fun encodeJournal(journal: TaskJournal): String = buildString {
        append(JOURNAL_HEADER).append('\n')
        append(encodeHex(journal.projectName.toByteArray(Charsets.UTF_8))).append('\n')
        append(journal.timestampMs).append('\n')
        append(journal.entries.size).append('\n')
        for (e in journal.entries) {
            append(encodeHex(e.path.toByteArray(Charsets.UTF_8))).append('|')
            append(e.op.name).append('|')
            append(if (e.existedBefore) '1' else '0').append('|')
            append(encodeHex(e.preimageBytes)).append('|')
            append(encodeHex(e.appliedBytes)).append('\n')
        }
    }

    internal fun decodeJournal(raw: String): TaskJournal? = runCatching {
        val lines = raw.lines().filter { it.isNotEmpty() }
        if (lines.size < 4 || lines[0] != JOURNAL_HEADER) return@runCatching null
        val projectName = String(decodeHex(lines[1]) ?: return@runCatching null, Charsets.UTF_8)
        val timestampMs = lines[2].toLongOrNull() ?: return@runCatching null
        val count = lines[3].toIntOrNull() ?: return@runCatching null
        if (count !in 1..MAX_JOURNAL_FILES || lines.size < 4 + count) return@runCatching null
        val entries = mutableListOf<JournalEntry>()
        var totalPre = 0
        for (i in 0 until count) {
            val parts = lines[4 + i].split('|')
            if (parts.size != 5) return@runCatching null
            val pathBytes = decodeHex(parts[0]) ?: return@runCatching null
            val path = AiEditProposalParser.validateTargetPath(String(pathBytes, Charsets.UTF_8))
                ?: return@runCatching null
            val op = runCatching { AiEditOp.valueOf(parts[1]) }.getOrNull() ?: return@runCatching null
            val existed = when (parts[2]) {
                "1" -> true
                "0" -> false
                else -> return@runCatching null
            }
            val pre = decodeHex(parts[3]) ?: return@runCatching null
            val app = decodeHex(parts[4]) ?: return@runCatching null
            totalPre += pre.size
            if (totalPre > MAX_JOURNAL_BYTES) return@runCatching null
            entries += JournalEntry(
                path = path,
                op = op,
                existedBefore = existed,
                preimageBytes = pre,
                appliedBytes = app
            )
        }
        TaskJournal(projectName = projectName, timestampMs = timestampMs, entries = entries)
    }.getOrNull()

    private fun writeJournal(noBackupRoot: File, safeProject: String, journal: TaskJournal): Boolean = runCatching {
        val target = journalFile(noBackupRoot, safeProject)
        val dir = target.parentFile ?: return@runCatching false
        if (!dir.exists() && !dir.mkdirs()) return@runCatching false
        val encoded = encodeJournal(journal).toByteArray(Charsets.UTF_8)
        writeBytesAtomically(target, encoded)
        true
    }.getOrDefault(false)

    private fun readJournal(noBackupRoot: File, safeProject: String): TaskJournal? = runCatching {
        val target = journalFile(noBackupRoot, safeProject)
        if (!target.isFile) return@runCatching null
        decodeJournal(target.readText(Charsets.UTF_8))
    }.getOrNull()

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    private fun encodeHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX_DIGITS[v ushr 4]
            out[i * 2 + 1] = HEX_DIGITS[v and 0x0F]
        }
        return String(out)
    }

    private fun decodeHex(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
