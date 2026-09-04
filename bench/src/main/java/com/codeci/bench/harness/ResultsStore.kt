package com.codeci.bench.harness

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import com.codeci.bench.core.FrameStats
import com.codeci.bench.core.FrameSummary

/** One repetition of one scenario on one candidate × corpus. */
data class RepResult(
    val rep: Int,
    val summary: FrameSummary,
    /** Cold-open time in ms (only for the cold_open scenario). */
    val coldOpenMs: Double? = null,
    /** Characters that landed (only for typing scenarios; -1 = n/a). */
    val typed: Int = -1,
    /** Lines traversed during the caret drag (only for caret_drag; -1 = n/a). */
    val linesTraversed: Int = -1
)

/** A full run (all reps) of one scenario. */
data class RunRecord(
    val candidate: String,
    val corpus: String,
    val scenario: String,
    val mode: InputMode,
    val notes: String,
    val reps: List<RepResult>
) {
    fun medianLine(): String {
        val median = FrameStats.medianReps(reps.map { it.summary })
        return median?.line() ?: "no frames"
    }
}

/**
 * Phase 25.1 — observable run history + the markdown export the owner pastes
 * back into chat. Persisted to filesDir so an app restart keeps the numbers.
 */
object ResultsStore {

    val runs = mutableStateListOf<RunRecord>()

    fun add(record: RunRecord) {
        runs += record
    }

    fun clear() = runs.clear()

    fun toMarkdown(apkSizeMb: Double? = null): String = buildString {
        appendLine("# CodeC Phase 25.1 bench results")
        appendLine()
        appendLine("Device-measured, release APK (R8), identical scripted input per scenario.")
        apkSizeMb?.let { appendLine("Bench APK size: %.1f MB".format(it)) }
        appendLine()
        appendLine("| Candidate | Corpus | Scenario | Mode | Median (p50/p95/p99 ms) | Jank | Bad | Typed | Lines | Notes |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|")
        for (run in runs) {
            val median = FrameStats.medianReps(run.reps.map { it.summary })
            appendLine(
                "| %s | %s | %s | %s | %s | %d | %d | %s | %s | %s |".format(
                    run.candidate,
                    run.corpus,
                    run.scenario,
                    run.mode.label,
                    median?.let { "%.1f / %.1f / %.1f".format(it.p50Ms, it.p95Ms, it.p99Ms) } ?: "—",
                    median?.jankyFrames ?: 0,
                    median?.badFrames ?: 0,
                    run.reps.lastOrNull()?.typed?.takeIf { it >= 0 }?.toString() ?: "—",
                    run.reps.lastOrNull()?.linesTraversed?.takeIf { it >= 0 }?.toString() ?: "—",
                    run.notes
                )
            )
        }
        appendLine()
        appendLine("## Per-rep detail")
        for (run in runs) {
            appendLine()
            appendLine("### ${run.candidate} · ${run.corpus} · ${run.scenario} (${run.mode.label})")
            for (rep in run.reps) {
                val cold = rep.coldOpenMs?.let { " cold=%.0fms".format(it) } ?: ""
                val typed = if (rep.typed >= 0) " typed=${rep.typed}" else ""
                val lines = if (rep.linesTraversed >= 0) " lines=${rep.linesTraversed}" else ""
                appendLine("- rep ${rep.rep}: ${rep.summary.line()}$cold$typed$lines")
            }
        }
    }

    fun copyToClipboard(context: Context): Boolean {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        manager.setPrimaryClip(ClipData.newPlainText("CodeC bench results", toMarkdown()))
        return true
    }

    fun share(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, toMarkdown())
            putExtra(Intent.EXTRA_SUBJECT, "CodeC Phase 25.1 bench results")
        }
        context.startActivity(Intent.createChooser(intent, "Share bench results"))
    }

    fun persist(context: Context) {
        runCatching {
            context.getFileStreamPath("bench-results.md").writeText(toMarkdown())
        }
    }

    fun persistedPath(context: Context) = context.getFileStreamPath("bench-results.md")
}
