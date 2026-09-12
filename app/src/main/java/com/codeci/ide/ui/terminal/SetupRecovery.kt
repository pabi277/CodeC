package com.codeci.ide.ui.terminal

import java.io.File

/**
 * Phase 44.2 — boot-time repair + orphan sweep for the userland install
 * (spec: docs/chat-phase44/PART_44_2_ATOMIC_SETUP.md §2).
 *
 * Two mechanisms, because a two-directory rename cannot be made truly atomic on
 * every filesystem:
 *
 *  1. **The dangerous window is recorded** — [SetupLedger] is set to
 *     [SetupPhase.SWAPPING] before the first rename and to [SetupPhase.DONE]
 *     after the second, so a kill inside the window is *described* by the disk
 *     state instead of being silent.
 *  2. **Boot repair** — [recover] reads that record, and when the prefix is
 *     missing it renames the newest `usr.old-<ts>` back to `usr`. The restored
 *     tree is the PREVIOUS release, which may be older; that is correct and
 *     honest (a working older userland beats a missing one) and the next
 *     upgrade pass replaces it when online.
 *
 * Sweep law (the same shape as `ui/services/TempGc`):
 *  - only DIRECT children of `filesDir`;
 *  - only names matching `usr.old-<digits>` or `.userland-staging-<digits>`
 *    (a strict digit suffix, so a user file called `usr.old-but-mine` inside a
 *    project is never a candidate — and `projects/` is never walked at all);
 *  - never the prefix itself, never recursion, never symlinks;
 *  - never a stamp created after the sweep started (an install may be racing);
 *  - failures counted and logged, never thrown.
 */
object SetupRecovery {

    /** `UserlandInstaller`'s staging directory prefix (kept identical). */
    const val STAGING_PREFIX = ".userland-staging"

    /** `UserlandInstaller`'s old-prefix infix (kept identical). */
    const val OLD_INFIX = ".old-"

    /** The prefix directory's own name (`ShellEnvironment.PREFIX_NAME`). */
    const val PREFIX_NAME = "usr"

    // ---- pure name logic ----------------------------------------------------

    fun stagingName(timestamp: Long, prefix: String = STAGING_PREFIX): String = "$prefix-$timestamp"

    fun oldPrefixName(prefixName: String = PREFIX_NAME, timestamp: Long): String =
        "$prefixName$OLD_INFIX$timestamp"

    fun isStagingName(name: String): Boolean = stampOf(name, STAGING_PREFIX + "-") != null

    fun isOldPrefixName(name: String, prefixName: String = PREFIX_NAME): Boolean =
        stampOf(name, prefixName + OLD_INFIX) != null

    /** True for exactly the two orphan shapes, with a strict digit stamp. */
    fun isOrphanName(name: String, prefixName: String = PREFIX_NAME): Boolean =
        isStagingName(name) || isOldPrefixName(name, prefixName)

    /** The trailing `<digits>` of an orphan name, or null when it is not one. */
    fun stampOf(name: String, prefix: String): Long? {
        if (!name.startsWith(prefix)) return null
        val tail = name.substring(prefix.length)
        if (tail.isEmpty() || !tail.all { it.isDigit() }) return null
        return tail.toLongOrNull()
    }

    /** Every orphan name in [names], oldest stamp first. */
    fun orphansIn(names: List<String>, prefixName: String = PREFIX_NAME): List<String> =
        names.filter { isOrphanName(it, prefixName) }
            .sortedBy { stampOf(it, STAGING_PREFIX + "-") ?: stampOf(it, prefixName + OLD_INFIX) ?: 0L }

    /** The newest `usr.old-*` — the one a mid-swap kill left behind. */
    fun newestOldPrefix(names: List<String>, prefixName: String = PREFIX_NAME): String? =
        names.filter { isOldPrefixName(it, prefixName) }
            .maxByOrNull { stampOf(it, prefixName + OLD_INFIX) ?: 0L }

    // ---- the boot repair (thin filesystem edge) -----------------------------

    data class RecoveryReport(
        val resume: SetupResume,
        val restored: String? = null,
        val swept: List<String> = emptyList(),
        val failed: List<String> = emptyList(),
        val message: String? = null
    ) {
        val didSomething: Boolean get() = restored != null || swept.isNotEmpty()
    }

    /**
     * Runs once per cold start, on a daemon thread, next to the `TempGc` sweep.
     * Returns a report; throws nothing.
     */
    fun recover(
        filesDir: File,
        prefixDir: File,
        ledger: SetupLedger,
        log: (String) -> Unit = {}
    ): RecoveryReport {
        val startedAt = System.currentTimeMillis()
        return try {
            val record = ledger.read()
            val names = scan(filesDir, prefixDir.name).map { it.name }
            val oldDirs = names.filter { isOldPrefixName(it, prefixDir.name) }
            val resume = ledger.resumePlan(record, prefixDir.isDirectory, oldDirs)
            var restored: String? = null
            var restoreFailed: String? = null
            val messages = ArrayList<String>()

            if (resume is SetupResume.RestoreOld) {
                restored = restoreOldPrefix(filesDir, prefixDir, resume.dir, log)
                if (restored != null) {
                    ledger.clear()
                    messages.add(
                        "setup interrupted during swap — previous userland restored ($restored)"
                    )
                } else {
                    restoreFailed = resume.dir
                    messages.add(
                        "setup interrupted during swap — could not restore ${resume.dir}; reinstalling"
                    )
                }
            } else if (record.phase == SetupPhase.SWAPPING && prefixDir.isDirectory) {
                // The swap landed before the kill: only the record is stale.
                ledger.clear()
            }

            val sweep = sweep(filesDir, prefixDir.name, newerThan = startedAt, log = log)
            if (sweep.deleted.isNotEmpty()) {
                messages.add(
                    "removed ${sweep.deleted.size} leftover setup director" +
                        if (sweep.deleted.size == 1) "y" else "ies"
                )
            }
            messages.forEach { log(it) }
            val userMessage = when {
                restored != null -> ledger.resumeMessage(SetupResume.RestoreOld(restored))
                resume is SetupResume.RetryDownload -> ledger.resumeMessage(resume)
                resume is SetupResume.Reextract -> ledger.resumeMessage(resume)
                else -> null
            }
            RecoveryReport(
                resume = resume,
                restored = restored,
                swept = sweep.deleted,
                failed = sweep.failed + listOfNotNull(restoreFailed),
                message = userMessage
            )
        } catch (t: Throwable) {
            // A repair must never be the reason the app does not start.
            log("setup recovery failed: ${t.message ?: t.javaClass.simpleName}")
            RecoveryReport(resume = SetupResume.Nothing, failed = listOf("recovery-error"))
        } finally {
            SetupRecoveryGate.finished()
        }
    }

    /**
     * Renames [orphanName] (a `usr.old-<ts>` sibling of the prefix) back onto
     * the prefix. Refuses when the prefix already exists — a valid `usr` is
     * never renamed, moved or deleted.
     */
    fun restoreOldPrefix(
        filesDir: File,
        prefixDir: File,
        orphanName: String,
        log: (String) -> Unit = {}
    ): String? {
        if (!isOldPrefixName(orphanName, prefixDir.name)) return null
        val orphan = File(filesDir, orphanName)
        if (!orphan.isDirectory || isSymlink(orphan)) return null
        if (!sameParent(orphan, prefixDir)) return null
        if (prefixDir.exists()) return null
        return try {
            if (orphan.renameTo(prefixDir)) orphanName else null
        } catch (t: Throwable) {
            log("restore rename failed: ${t.message ?: t.javaClass.simpleName}")
            null
        }
    }

    data class SweepResult(val deleted: List<String>, val failed: List<String>, val kept: List<String>)

    /**
     * Deletes orphaned `usr.old-*` / `.userland-staging-*` directories, keeping
     * anything whose stamp is not older than [newerThan] (a live install may be
     * creating one right now).
     */
    fun sweep(
        filesDir: File,
        prefixName: String = PREFIX_NAME,
        newerThan: Long = Long.MAX_VALUE,
        log: (String) -> Unit = {}
    ): SweepResult {
        val deleted = ArrayList<String>()
        val failed = ArrayList<String>()
        val kept = ArrayList<String>()
        for (candidate in scan(filesDir, prefixName)) {
            val stamp = stampOf(candidate.name, STAGING_PREFIX + "-")
                ?: stampOf(candidate.name, prefixName + OLD_INFIX)
                ?: 0L
            if (stamp >= newerThan) {
                kept.add(candidate.name)
                continue
            }
            if (deleteOrphan(candidate, log)) {
                deleted.add(candidate.name)
            } else {
                failed.add(candidate.name)
            }
        }
        return SweepResult(deleted, failed, kept)
    }

    /**
     * Direct children of [filesDir] whose name is one of the two orphan shapes.
     * Symlinks are skipped and never followed; the prefix itself is excluded by
     * name.
     */
    fun scan(filesDir: File, prefixName: String = PREFIX_NAME): List<File> {
        if (!filesDir.isDirectory) return emptyList()
        val children = try {
            filesDir.listFiles() ?: return emptyList()
        } catch (t: Throwable) {
            return emptyList()
        }
        val rootCanonical = try {
            filesDir.canonicalFile
        } catch (t: Throwable) {
            return emptyList()
        }
        val out = ArrayList<File>()
        for (child in children) {
            val name = child.name
            if (name == prefixName) continue
            if (!isOrphanName(name, prefixName)) continue
            if (isSymlink(child)) continue
            if (!child.isDirectory) continue
            if (child.parentFile?.canonicalFile?.path != rootCanonical.path) continue
            out.add(child)
        }
        return out.sortedBy {
            stampOf(it.name, STAGING_PREFIX + "-") ?: stampOf(it.name, prefixName + OLD_INFIX) ?: 0L
        }
    }

    /**
     * **minSdk 24 — `java.nio.file` is API 26**, the rule `TarGzExtractor`
     * already follows (and `ShellEnvironment` only reaches `Files` through
     * reflection). So the check is by canonical name: a link's canonical file is
     * its TARGET, so a `usr.old-<ts>` whose canonical name is not
     * `usr.old-<ts>` is not the directory it claims to be. Anything that cannot
     * be resolved counts as a link, i.e. is never deleted.
     *
     * Belt and braces: [deleteOrphan] also tries a plain [File.delete] first,
     * which `unlink`s a symlink WITHOUT following it — so even a link pointing
     * at a same-named directory (creatable only by root) cannot make the sweep
     * walk into a target tree.
     */
    internal fun isSymlink(file: File): Boolean = try {
        file.canonicalFile.name != file.name
    } catch (t: Throwable) {
        true
    }

    /**
     * Removes one orphan. The plain [File.delete] goes first on purpose: it
     * unlinks a symlink without following it and removes an empty directory, so
     * `deleteRecursively` — which DOES follow a link to a directory — only ever
     * runs on a real, non-empty tree.
     */
    private fun deleteOrphan(candidate: File, log: (String) -> Unit): Boolean = try {
        candidate.delete() || candidate.deleteRecursively()
    } catch (t: Throwable) {
        log("sweep failed for ${candidate.name}: ${t.message ?: t.javaClass.simpleName}")
        false
    }

    private fun sameParent(a: File, b: File): Boolean = try {
        a.parentFile?.canonicalFile?.path == b.parentFile?.canonicalFile?.path
    } catch (t: Throwable) {
        false
    }
}

/**
 * The installer must not race the boot repair: a staging directory created by a
 * live install is indistinguishable by name from an orphan, and a rename back
 * onto `usr` while `swapPrefix` is running would be a second writer. The
 * installer waits here (bounded) before it touches the disk.
 */
object SetupRecoveryGate {

    private const val DEFAULT_TIMEOUT_MS = 5_000L

    /** One latch per process life; [reset] (tests only) swaps in a fresh one. */
    private val latch = java.util.concurrent.atomic.AtomicReference(
        java.util.concurrent.CountDownLatch(1)
    )

    val isFinished: Boolean get() = latch.get().count == 0L

    fun finished() {
        latch.get().countDown()
    }

    /** Blocks until [finished] or the timeout expires. Never throws. */
    fun awaitFinished(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean = try {
        latch.get().await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    } catch (t: Throwable) {
        isFinished
    }

    /** Test/process-restart helper: a new process starts un-finished. */
    fun reset() {
        latch.set(java.util.concurrent.CountDownLatch(1))
    }
}
