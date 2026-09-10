package com.codeci.ide.ui.services

import java.io.File

/**
 * Phase 39.1 — garbage-collect CodeC's own run artifacts.
 *
 * Pure planner ([plan]) + thin filesystem edge ([apply], [scan], [clearIdle],
 * [measure]). The collector may walk **only** `tempRoot/runs/` — a
 * `require(path.startsWith(tempRoot))` lives in the edge, with a host test,
 * because a GC that can walk anywhere is a data-loss waiting for a symlink.
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
    val maxBytes: Long = 128L shl 20, // 128 MiB
    val keepNewest: Int = 8,
)

data class RunDir(
    val stamp: Long,
    val path: File,
    val bytes: Long,
    val lastModifiedMillis: Long,
)

enum class KeepReason {
    BUSY,
    KEEP_NEWEST,
    UNDER_BUDGET,
    NOT_A_DIR,
    OUTSIDE_ROOT,
}

sealed interface GcAction {
    data class Delete(val path: File) : GcAction
    data class Keep(val path: File, val because: KeepReason) : GcAction
}

data class GcReport(
    val actions: List<GcAction>,
    val deleted: Int,
    val kept: Int,
    val failedDeletes: Int,
    val bytesBefore: Long,
    val bytesAfter: Long,
)

data class TempMeasure(
    val files: Int,
    val bytes: Long,
    val runCount: Int,
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
        clearAllIdle: Boolean = false,
    ): List<GcAction> {
        if (runs.isEmpty()) return emptyList()
        // Newest first for keepNewest; the rest is oldest-first for eviction.
        val ordered = runs.sortedByDescending { it.stamp }
        val keepNewestStamps = ordered.take(budget.keepNewest.coerceAtLeast(0)).map { it.stamp }.toSet()
        val actions = mutableListOf<GcAction>()
        var liveBytes = runs.sumOf { it.bytes.coerceAtLeast(0L) }

        for (run in ordered) {
            when {
                run.stamp in busy ->
                    actions += GcAction.Keep(run.path, KeepReason.BUSY)
                clearAllIdle ->
                    actions += GcAction.Delete(run.path)
                run.stamp in keepNewestStamps ->
                    actions += GcAction.Keep(run.path, KeepReason.KEEP_NEWEST)
                now - run.lastModifiedMillis > budget.maxAgeMillis ->
                    actions += GcAction.Delete(run.path)
                else ->
                    actions += GcAction.Keep(run.path, KeepReason.UNDER_BUDGET)
            }
        }

        // Capacity pass: while over maxBytes, delete oldest non-busy,
        // non-keepNewest Keep that is still marked Keep/UNDER_BUDGET.
        if (!clearAllIdle && budget.maxBytes > 0L) {
            // Recompute projected bytes after age-out deletes.
            val surviving = actions.mapNotNull { a ->
                when (a) {
                    is GcAction.Delete -> null
                    is GcAction.Keep -> runs.firstOrNull { it.path == a.path }
                }
            }.sortedBy { it.stamp } // oldest first
            liveBytes = surviving.sumOf { it.bytes.coerceAtLeast(0L) }
            if (liveBytes > budget.maxBytes) {
                val byPath = actions.mapIndexed { i, a -> a to i }.toMutableList()
                for (old in surviving) {
                    if (liveBytes <= budget.maxBytes) break
                    if (old.stamp in busy) continue
                    if (old.stamp in keepNewestStamps) continue
                    val idx = byPath.indexOfFirst { (a, _) ->
                        a is GcAction.Keep && a.path == old.path
                    }
                    if (idx < 0) continue
                    byPath[idx] = GcAction.Delete(old.path) to byPath[idx].second
                    liveBytes -= old.bytes.coerceAtLeast(0L)
                }
                return byPath.sortedBy { it.second }.map { it.first }
            }
        }
        return actions
    }

    /**
     * Inventory of `tempRoot/runs/*` stamp dirs. Entries that are not
     * directories or that escape [tempRoot] are reported as Keep-able
     * (NOT_A_DIR / OUTSIDE_ROOT) and never followed.
     */
    fun scan(tempRoot: File): List<RunDir> {
        val runsRoot = File(tempRoot, RunArtifacts.RUNS_DIR)
        if (!runsRoot.isDirectory) return emptyList()
        val rootCanonical = runCatching { tempRoot.canonicalFile }.getOrNull() ?: return emptyList()
        val children = runsRoot.listFiles() ?: return emptyList()
        val out = mutableListOf<RunDir>()
        for (child in children) {
            if (!child.isDirectory) continue
            val stamp = child.name.toLongOrNull() ?: continue
            val confined = RunArtifacts.confineToTemp(rootCanonical, child, stamp)
            if (confined != child && confined.absolutePath != child.absolutePath) {
                // Escaped — skip entirely; the apply edge will also refuse.
                continue
            }
            out += RunDir(
                stamp = stamp,
                path = child,
                bytes = dirSize(child),
                lastModifiedMillis = child.lastModified(),
            )
        }
        return out
    }

    /** Apply a plan. Refuses any delete whose path is outside [tempRoot]/runs. */
    fun apply(tempRoot: File, actions: List<GcAction>): GcReport {
        val rootCanonical = runCatching { tempRoot.canonicalFile }.getOrNull()
            ?: return GcReport(actions, 0, actions.size, 0, 0, 0)
        val runsRoot = File(rootCanonical, RunArtifacts.RUNS_DIR).canonicalFile
        val runsPrefix = runsRoot.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
        var deleted = 0
        var kept = 0
        var failed = 0
        val before = scan(rootCanonical).sumOf { it.bytes.coerceAtLeast(0L) }
        for (action in actions) {
            when (action) {
                is GcAction.Keep -> kept++
                is GcAction.Delete -> {
                    val target = runCatching { action.path.canonicalFile }.getOrNull()
                    if (target == null ||
                        !(target.path == runsRoot.path || target.path.startsWith(runsPrefix))
                    ) {
                        // Refuse: outside the only root we may walk.
                        failed++
                        continue
                    }
                    val ok = runCatching {
                        if (target.exists()) target.deleteRecursively() else true
                    }.getOrDefault(false)
                    if (ok) deleted++ else failed++
                }
            }
        }
        val after = scan(rootCanonical).sumOf { it.bytes.coerceAtLeast(0L) }
        return GcReport(actions, deleted, kept, failed, before, after)
    }

    /** One-shot: plan + apply against the current inventory. */
    fun collect(
        tempRoot: File,
        now: Long = System.currentTimeMillis(),
        busy: Set<Long> = emptySet(),
        budget: GcBudget = GcBudget(),
        clearAllIdle: Boolean = false,
    ): GcReport {
        val runs = scan(tempRoot)
        val actions = plan(runs, now, busy, budget, clearAllIdle)
        return apply(tempRoot, actions)
    }

    /** Settings → Storage "Clear" — empties every idle run dir. */
    fun clearIdle(tempRoot: File, busy: Set<Long> = emptySet()): GcReport =
        collect(tempRoot, busy = busy, clearAllIdle = true)

    /** Settings → Storage size line. */
    fun measure(tempRoot: File): TempMeasure {
        val runs = scan(tempRoot)
        var files = 0
        for (run in runs) {
            files += countFiles(run.path)
        }
        return TempMeasure(
            files = files,
            bytes = runs.sumOf { it.bytes.coerceAtLeast(0L) },
            runCount = runs.size,
        )
    }

    /** Format bytes for the Settings row ("1.2 MB", "384 KB", …). */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        if (bytes < 1024L * 1024L) {
            return if (bytes % 1024L == 0L) "${bytes / 1024L} KB"
            else "%.1f KB".format(bytes / 1024.0)
        }
        val mbBytes = 1024L * 1024L
        return if (bytes % mbBytes == 0L) "${bytes / mbBytes} MB"
        else "%.1f MB".format(bytes / mbBytes.toDouble())
    }

    /** Human Settings subtitle: "N files, X MB across R runs". */
    fun formatMeasure(m: TempMeasure): String {
        val size = formatBytes(m.bytes)
        return when {
            m.runCount == 0 -> "empty"
            m.runCount == 1 -> "${m.files} file${if (m.files == 1) "" else "s"}, $size (1 run)"
            else -> "${m.files} files, $size (${m.runCount} runs)"
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return runCatching {
            var total = 0L
            dir.walkTopDown().forEach { f ->
                if (f.isFile) total += f.length().coerceAtLeast(0L)
            }
            total
        }.getOrDefault(0L)
    }

    private fun countFiles(dir: File): Int {
        if (!dir.exists()) return 0
        return runCatching {
            dir.walkTopDown().count { it.isFile }
        }.getOrDefault(0)
    }
}

/**
 * Live-stamp registry so GC never deletes a run that is still executing.
 * Process-wide; written by CompilerService / EditorViewModel / ServerHost,
 * read by TempGc call sites. Thread-safe.
 */
object LiveRunStamps {
    private val lock = Any()
    private val stamps = linkedSetOf<Long>()

    fun add(stamp: Long) {
        synchronized(lock) { stamps += stamp }
    }

    fun remove(stamp: Long) {
        synchronized(lock) { stamps -= stamp }
    }

    fun snapshot(): Set<Long> = synchronized(lock) { stamps.toSet() }

    fun clear() {
        synchronized(lock) { stamps.clear() }
    }
}
