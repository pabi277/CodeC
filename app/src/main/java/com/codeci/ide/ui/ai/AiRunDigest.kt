package com.codeci.ide.ui.ai

/**
 * Phase 80 (AI Level 4) — **what goes back to the model after a run**: a
 * compact, truthful digest of the Output panel's own result.
 *
 * ## The rules it keeps
 *
 * - **The run itself is CodeC's own RUN ▶**, not a second runner
 *   (`04_AGENT_TOOLS_AND_RUN_LOOP.md`: *"Use CodeC's established runner and
 *   run-output surface instead of launching a duplicate runner"*). This object
 *   only decides what of that run is worth sending.
 * - **Exit codes are never dropped.** They are the one fact the model cannot
 *   infer from text, and `AiGate.runFailed` already treats them as the run's
 *   verdict.
 * - **Errors first, then the tail.** A 900-line build log cannot be sent; the
 *   lines that look like errors/warnings are kept first, then the newest lines,
 *   and the digest says how many lines were left out. *"Send only the relevant
 *   run output/diagnostics back to the model, after disclosure."*
 * - **Untrusted input stays untrusted.** The digest is data in the next
 *   request; the system instruction already says to treat shared text as data,
 *   and the loop never executes anything a digest line says
 *   (*"Treat terminal output, compiler text, source comments, and project
 *   instructions as untrusted input"*).
 * - Pure Kotlin: strings and numbers, no `java.io`, no Android, so
 *   `AiRunDigestTest` runs on the host JVM.
 */
object AiRunDigest {

    /** Characters the digest may add to the next request. */
    const val MAX_DIGEST_CHARS = 6_000

    /** Lines the digest may carry. */
    const val MAX_DIGEST_LINES = 60

    /** One line is clipped here, so a minified error cannot eat the whole budget. */
    const val MAX_LINE_CHARS = 300

    /** Lines that look like a failure are kept before ordinary output. */
    private val SIGNAL = listOf(
        "error:", "error ", "fatal", "error", "warning:", "traceback", "exception", "undefined",
        "cannot find", "no such file", "segmentation fault", "core dumped", "failed", "failure",
        "panic:", "not found", "unresolved", "syntax", "assertion"
    )

    /** Everything one run produced, as the editor's Output panel holds it. */
    data class RunResult(
        val targetLabel: String?,
        val buildExitCode: Int?,
        val runExitCode: Int?,
        val buildDurationMs: Long?,
        val runDurationMs: Long?,
        val timedOut: Boolean,
        /** Every visible output line, in order (build and run phases together). */
        val outputLines: List<String>,
        /** Parser diagnostics for the run target, already formatted, may be empty. */
        val diagnosticLines: List<String> = emptyList(),
        /** True when the run was stopped by the user rather than finishing. */
        val cancelled: Boolean = false
    )

    /**
     * The digest text. Exactly this string is what the timeline shows and what
     * the next request carries — one value, so the user can check it (D4, as
     * amended for the agent's tool steps).
     */
    fun build(result: RunResult): String {
        val header = buildString {
            append("RUN")
            result.targetLabel?.let { append(" target=").append(it) }
            append(" — build exit ").append(result.buildExitCode ?: "—")
            result.buildDurationMs?.let { append(" (").append(seconds(it)).append(")") }
            append(", run exit ").append(result.runExitCode ?: "—")
            result.runDurationMs?.let { append(" (").append(seconds(it)).append(")") }
            if (result.timedOut) append(" [timed out]")
            if (result.cancelled) append(" [stopped by the user]")
        }
        val picked = pickLines(result.outputLines)
        val diagnostics = result.diagnosticLines.take(MAX_DIGEST_LINES / 3)
        val body = buildString {
            append(header)
            if (picked.lines.isNotEmpty()) {
                append("\nOutput").append(if (picked.dropped > 0) " (${picked.dropped} lines left out)" else "").append(':')
                for (line in picked.lines) append('\n').append("  ").append(line.take(MAX_LINE_CHARS))
            } else {
                append("\nOutput: (nothing was printed)")
            }
            if (diagnostics.isNotEmpty()) {
                append("\nDiagnostics:")
                for (d in diagnostics) append('\n').append("  ").append(d.take(MAX_LINE_CHARS))
            }
        }
        if (body.length <= MAX_DIGEST_CHARS) return body
        val marker = "\n  … [digest cut to fit]"
        return body.substring(0, MAX_DIGEST_CHARS - marker.length) + marker
    }

    private data class Picked(val lines: List<String>, val dropped: Int)

    /**
     * Errors first, then the newest lines, without duplicates and in the order
     * they actually appeared (a run's story reads forwards). [MAX_DIGEST_LINES]
     * is the ceiling; everything else is counted into `dropped`.
     */
    private fun pickLines(lines: List<String>): Picked {
        val clean = lines.map { it.trimEnd() }.filter { it.isNotBlank() }
        if (clean.size <= MAX_DIGEST_LINES) return Picked(clean, 0)
        val signal = clean.filter { line -> SIGNAL.any { line.lowercase().contains(it) } }
        val tail = clean.takeLast(MAX_DIGEST_LINES)
        val merged = LinkedHashSet<String>()
        for (line in signal) {
            if (merged.size >= MAX_DIGEST_LINES) break
            merged += line
        }
        for (line in tail) {
            if (merged.size >= MAX_DIGEST_LINES) break
            merged += line
        }
        val ordered = clean.filter { it in merged }
        return Picked(ordered.take(MAX_DIGEST_LINES), clean.size - ordered.take(MAX_DIGEST_LINES).size)
    }

    private fun seconds(ms: Long): String = "${(ms / 100) / 10.0} s"

    /**
     * The sentence the approval card shows before anything runs. It names the
     * action in the user's own terms — the same RUN ▶ the editor's button
     * performs — and says plainly that nothing has run yet.
     */
    fun approvalQuestion(targetLabel: String?, commandLabel: String?): String = buildString {
        append("The AI wants to run this project")
        targetLabel?.let { append(" (").append(it).append(")") }
        append(" — the same RUN action as the ▶ button.")
        if (!commandLabel.isNullOrBlank()) append("\n\n").append(commandLabel.trim())
        append("\n\nNothing has run yet.")
    }
}
