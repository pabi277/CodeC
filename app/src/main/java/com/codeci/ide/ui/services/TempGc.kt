package com.codeci.ide.ui.services

import java.io.File
import java.util.Locale

/**
 * Phase 39.1 — garbage-collect CodeC's own run artifacts.
 *
 * Pure planner ([plan]) + thin filesystem edge ([apply], [scan], [clearIdle],
 * [measure]). The collector may walk **only** `tempRoot/runs/` — a
 * prefix check lives in the edge, with a host test, because a GC that can
 * walk anywhere is a data-loss waiting for a symlink.
 *
 * Rules (from PART_39_1):
 *  1. Never touch a stamp that is currently running ([busy]).
 *  2. Always keep the newest [GcBudget.keepNewest] runs regardless of age.
 *  3. Age-out the rest past [GcBudget.maxAgeMillis].
 *  4. Enforce [GcBudget.maxBytes] oldest-first.
 *  5. Delete failures are counted and logged, never thrown.
 */
data class GcBudget(
    val maxAgeMillis: Long = 24L * 3600_000L,
    val maxBytes: Long = 128L * 1024L * 1024L, // 128 MiB
    val keepNewest: Int = 8
)

data class RunDir(
    val stamp: Long,
    val path: File,
    val bytes: Long,
    val lastModifiedMillis: Long
)

enum class KeepReason {
    BUSY,
    KEEP_NEWEST,
    UNDER_BUDGET,
    NOT_A_DIR,
    OUTSIDE_ROOT
}

sealed class GcAction {
    data class Delete(val path: File) : GcAction()
    data class Keep(val path: File, val because: KeepReason) : GcAction()
}

data class GcReport(
    val actions: List<GcAction>,
    val deleted: Int,
    val kept: Int,
    val failedDeletes: Int,
    val bytesBefore: Long,
    val bytesAfter: Long
)

data class TempMeasure(
    val files: Int,
    val bytes: Long,
    val runCount: Int
)

object TempGc {

    /**
     * Decide what to delete. Pure: no IO. [runs] is the inventory;
     * [busy] are live stamps that must survive; [now] is the clock.
     */
    fun plan(
        runs: List<RunDir>,
        now: Long,
        busy: Set<Long>,
        budget: GcBudget = GcBudget(),
        clearAllIdle: Boolean = false
    ): List<GcAction> {
        if (runs.isEmpty()) return emptyList()
        val ordered = runs.sortedByDescending { it.stamp }
        val keepCount = if (budget.keepNewest < 0) 0 else budget.keepNewest
        val keepNewestStamps = ordered.take(keepCount).map { it.stamp }.toSet()
        val actions = ArrayList<GcAction>(ordered.size)

        for (run in ordered) {
            val action: GcAction = when {
                run.stamp in busy ->
                    GcAction.Keep(run.path, KeepReason.BUSY)
                clearAllIdle ->
                    GcAction.Delete(run.path)
                run.stamp in keepNewestStamps ->
                    GcAction.Keep(run.path, KeepReason.KEEP_NEWEST)
                now - run.lastModifiedMillis > budget.maxAgeMillis ->
                    GcAction.Delete(run.path)
                else ->
                    GcAction.Keep(run.path, KeepReason.UNDER_BUDGET)
            }
            actions.add(action)
        }

        if (clearAllIdle || budget.maxBytes <= 0L) {
            return actions
        }

        // Capacity pass: while over maxBytes, delete oldest non-busy,
        // non-keepNewest Keep entries.
        var liveBytes = 0L
        val surviving = ArrayList<RunDir>()
        for (a in actions) {
            if (a is GcAction.Keep) {
                for (r in runs) {
                    if (r.path == a.path) {
                        surviving.add(r)
                        liveBytes += if (r.bytes < 0L) 0L else r.bytes
                        break
                    }
                }
            }
        }
        surviving.sortBy { it.stamp } // oldest first

        if (liveBytes <= budget.maxBytes) {
            return actions
        }

        val result = ArrayList<GcAction>(actions)
        for (old in surviving) {
            if (liveBytes <= budget.maxBytes) break
            if (old.stamp in busy) continue
            if (old.stamp in keepNewestStamps) continue
            var idx = -1
            for (i in result.indices) {
                val a = result[i]
                if (a is GcAction.Keep && a.path == old.path) {
                    idx = i
                    break
                }
            }
            if (idx < 0) continue
            result[idx] = GcAction.Delete(old.path)
            liveBytes -= if (old.bytes < 0L) 0L else old.bytes
        }
        return result
    }

    /**
     * Inventory of `tempRoot/runs/<stamp>` stamp dirs. Entries that are not
     * directories or that escape [tempRoot] are skipped and never followed.
     */
    fun scan(tempRoot: File): List<RunDir> {
        val runsRoot = File(tempRoot, RunArtifacts.RUNS_DIR)
        if (!runsRoot.isDirectory) return emptyList()
        val rootCanonical = try {
            tempRoot.canonicalFile
        } catch (_: Exception) {
            return emptyList()
        }
        val children = runsRoot.listFiles() ?: return emptyList()
        val out = ArrayList<RunDir>()
        for (child in children) {
            if (!child.isDirectory) continue
            val stamp = child.name.toLongOrNull() ?: continue
            val confined = RunArtifacts.confineToTemp(rootCanonical, child, stamp)
            if (confined.absolutePath != child.absolutePath) {
                continue
            }
            out.add(
                RunDir(
                    stamp = stamp,
                    path = child,
                    bytes = dirSize(child),
                    lastModifiedMillis = child.lastModified()
                )
            )
        }
        return out
    }

    /** Apply a plan. Refuses any delete whose path is outside `tempRoot/runs`. */
    fun apply(tempRoot: File, actions: List<GcAction>): GcReport {
        val rootCanonical = try {
            tempRoot.canonicalFile
        } catch (_: Exception) {
            return GcReport(actions, 0, actions.size, 0, 0L, 0L)
        }
        val runsRoot = File(rootCanonical, RunArtifacts.RUNS_DIR).canonicalFile
        val runsPrefix = if (runsRoot.path.endsWith(File.separator)) {
            runsRoot.path
        } else {
            runsRoot.path + File.separator
        }
        var deleted = 0
        var kept = 0
        var failed = 0
        var before = 0L
        for (r in scan(rootCanonical)) {
            before += if (r.bytes < 0L) 0L else r.bytes
        }
        for (action in actions) {
            when (action) {
                is GcAction.Keep -> kept++
                is GcAction.Delete -> {
                    val target = try {
                        action.path.canonicalFile
                    } catch (_: Exception) {
                        null
                    }
                    if (target == null ||
                        !(target.path == runsRoot.path || target.path.startsWith(runsPrefix))
                    ) {
                        failed++
                        continue
                    }
                    val ok = try {
                        if (target.exists()) target.deleteRecursively() else true
                    } catch (_: Exception) {
                        false
                    }
                    if (ok) deleted++ else failed++
                }
            }
        }
        var after = 0L
        for (r in scan(rootCanonical)) {
            after += if (r.bytes < 0L) 0L else r.bytes
        }
        return GcReport(actions, deleted, kept, failed, before, after)
    }

    /** One-shot: plan + apply against the current inventory. */
    fun sweep(
        tempRoot: File,
        now: Long = System.currentTimeMillis(),
        busy: Set<Long> = emptySet(),
        budget: GcBudget = GcBudget(),
        clearAllIdle: Boolean = false
    ): GcReport {
        val runs = scan(tempRoot)
        val actions = plan(runs, now, busy, budget, clearAllIdle)
        return apply(tempRoot, actions)
    }

    /** Settings → Storage "Clear" — empties every idle run dir. */
    fun clearIdle(tempRoot: File, busy: Set<Long> = emptySet()): GcReport {
        return sweep(tempRoot, busy = busy, clearAllIdle = true)
    }

    /** Settings → Storage size line. */
    fun measure(tempRoot: File): TempMeasure {
        val runs = scan(tempRoot)
        var files = 0
        var bytes = 0L
        for (run in runs) {
            files += countFiles(run.path)
            bytes += if (run.bytes < 0L) 0L else run.bytes
        }
        return TempMeasure(files = files, bytes = bytes, runCount = runs.size)
    }

    /** Format bytes for the Settings row ("1.2 MB", "384 KB", ...). */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return bytes.toString() + " B"
        if (bytes < 1024L * 1024L) {
            return if (bytes % 1024L == 0L) {
                (bytes / 1024L).toString() + " KB"
            } else {
                String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            }
        }
        val mbBytes = 1024L * 1024L
        return if (bytes % mbBytes == 0L) {
            (bytes / mbBytes).toString() + " MB"
        } else {
            String.format(Locale.US, "%.1f MB", bytes.toDouble() / mbBytes.toDouble())
        }
    }

    /** Human Settings subtitle: "N files, X MB across R runs". */
    fun formatMeasure(m: TempMeasure): String {
        val size = formatBytes(m.bytes)
        return when {
            m.runCount == 0 -> "empty"
            m.runCount == 1 -> {
                val unit = if (m.files == 1) "file" else "files"
                m.files.toString() + " " + unit + ", " + size + " (1 run)"
            }
            else -> m.files.toString() + " files, " + size + " (" + m.runCount + " runs)"
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return try {
            var total = 0L
            val walk = dir.walkTopDown()
            for (f in walk) {
                if (f.isFile) {
                    val len = f.length()
                    total += if (len < 0L) 0L else len
                }
            }
            total
        } catch (_: Exception) {
            0L
        }
    }

    private fun countFiles(dir: File): Int {
        if (!dir.exists()) return 0
        return try {
            var n = 0
            for (f in dir.walkTopDown()) {
                if (f.isFile) n++
            }
            n
        } catch (_: Exception) {
            0
        }
    }
}
