package com.codeci.ide.ui.ai

/*
 * Phase 89 (AI Level 12) — the evaluation numbers, and only the numbers.
 *
 * The owner's 2026-10-04 decision: latency, tokens and memory are measured by
 * "a small in-memory numbers-only readout". So this file is:
 *
 *  - DISPLAY STATE ONLY, exactly like [AiAgentUsage]. Nothing here is written to
 *    disk, to a log, to DataStore, to a backup or to a provider (D6). There is
 *    no key to clear because there is no store.
 *  - PURE. No Android class, no clock of its own, no `File`, no `@Composable`:
 *    the callers pass the numbers in, so the host harness can prove every rule
 *    below (rule.md §9).
 *  - HONEST. `null` means "not reported" and is never rendered as `0`. A
 *    provider that sends no usage shows "tokens not reported"; a run that has
 *    not finished hides the total instead of guessing it. This is the Level 8
 *    coverage rule applied to numbers: unknown must not look like a value.
 *
 * S3: everything a provider reports is untrusted data. A hostile usage object
 * cannot render a negative, absurd or crashing number — [AiMeasurePolicy]
 * sanitises at the door, and an out-of-bounds value becomes "not reported"
 * rather than a claimed measurement.
 */

/** Bounds for values that arrive from a provider or a clock (S3, untrusted input). */
object AiMeasureLimits {
    /** No provider reports more than this per request; anything above is not a measurement. */
    const val MAX_REPORTED_TOKENS = 2_000_000

    /** A task cannot have been running longer than an hour and still be one task (wall clock cap is 5 min). */
    const val MAX_LATENCY_MS = 3_600_000L

    /** A JVM heap sample above this is not believable on a phone; report unknown instead. */
    const val MAX_MEMORY_BYTES = 8L * 1024L * 1024L * 1024L
}

/**
 * What one completed provider request reported about itself. Both fields are
 * nullable: a provider may report one, both, or neither (Gemini reports
 * `usageMetadata`; an NVIDIA-compatible stream may send a usage-only event).
 */
data class AiTokenUsage(val prompt: Int? = null, val output: Int? = null)

/**
 * One agent task's numbers. Display state, in memory, never persisted (D6).
 * Every field is nullable: **null means the value is unknown**, never zero.
 */
data class AiMeasurements(
    /** Task start → the first non-empty visible text. Null until text arrives. */
    val firstTokenMs: Long? = null,
    /** Task start → the task's terminal state (answer, stop or failure). */
    val totalMs: Long? = null,
    /** Sum of what the task's requests reported. Null when none reported. */
    val promptTokens: Int? = null,
    val outputTokens: Int? = null,
    /** False once any completed request reported no usage, so a sum is a floor. */
    val tokensComplete: Boolean = true,
    /** One heap sample taken when the task finished. A sample, never a peak claim. */
    val memoryBytes: Long? = null
) {
    /** Nothing to show yet. */
    val isEmpty: Boolean
        get() = firstTokenMs == null && totalMs == null &&
            promptTokens == null && outputTokens == null && memoryBytes == null
}

/**
 * Phase 89 — the arithmetic and the wording of the readout. Pure and
 * host-testable; the ViewModel only supplies `nowMs` and the memory sample, and
 * the sheet only draws what [render] returns.
 *
 * The wording lives here rather than in `AiCopy` because the format and its
 * labels are one decision: a label in a different file than the rule that picks
 * it is how a readout drifts. `AiCopy` still owns every sentence *about* the
 * readout; the wiring test pins the sheet to this policy by source.
 */
object AiMeasurePolicy {

    /** The separator the progress line already uses, so one card reads as one voice. */
    const val SEPARATOR = " · "

    // ---- acceptance (S3: sanitise at the door) -----------------------------

    /** A reported count, or null when it is missing, negative or beyond belief. */
    fun reportedTokens(value: Int?): Int? =
        value?.takeIf { it in 0..AiMeasureLimits.MAX_REPORTED_TOKENS }

    /** A measured duration, or null when it is negative or beyond the bound. */
    fun reportedMs(value: Long?): Long? =
        value?.takeIf { it in 0..AiMeasureLimits.MAX_LATENCY_MS }

    /** A heap sample, or null when it is non-positive or beyond belief. */
    fun reportedMemory(value: Long?): Long? =
        value?.takeIf { it > 0L && it <= AiMeasureLimits.MAX_MEMORY_BYTES }

    // ---- the task's life ---------------------------------------------------

    /**
     * Stamp the first non-empty visible text of the task. Idempotent: the first
     * stamp wins, every later chunk changes nothing. A task with no usable start
     * instant (<= 0) records nothing rather than a bogus offset.
     */
    fun firstToken(current: AiMeasurements, nowMs: Long, startedAtMs: Long): AiMeasurements {
        if (current.firstTokenMs != null || startedAtMs <= 0L) return current
        return current.copy(firstTokenMs = reportedMs(nowMs - startedAtMs))
    }

    /**
     * Fold one **completed** request's usage into the task totals. A null
     * [usage] is not zero: it means the provider said nothing, so the sums stay
     * what they were and [AiMeasurements.tokensComplete] becomes false — the
     * readout then says "(partial)" instead of pretending the sum is total.
     */
    fun withUsage(current: AiMeasurements, usage: AiTokenUsage?): AiMeasurements {
        if (usage == null) return current.copy(tokensComplete = false)
        val prompt = reportedTokens(usage.prompt)
        val output = reportedTokens(usage.output)
        if (prompt == null && output == null) return current.copy(tokensComplete = false)
        return current.copy(
            promptTokens = add(current.promptTokens, prompt),
            outputTokens = add(current.outputTokens, output)
        )
    }

    /** Terminal stamp: total wall time and one heap sample, both sanitised. */
    fun finish(
        current: AiMeasurements,
        nowMs: Long,
        startedAtMs: Long,
        memoryBytes: Long? = null
    ): AiMeasurements = current.copy(
        totalMs = if (startedAtMs <= 0L) current.totalMs else reportedMs(nowMs - startedAtMs),
        memoryBytes = reportedMemory(memoryBytes) ?: current.memoryBytes
    )

    /** Saturating sum: an over-bound total is clamped by the door, not rendered as a lie. */
    private fun add(acc: Int?, next: Int?): Int? {
        if (next == null) return acc
        val total = (acc ?: 0).toLong() + next.toLong()
        return total.coerceAtMost(AiMeasureLimits.MAX_REPORTED_TOKENS.toLong()).toInt()
    }

    // ---- the line ----------------------------------------------------------

    /**
     * The one numbers-only line, or null when there is nothing to show.
     *
     * `first 1.4 s · total 22.6 s · tokens 1 812 in / 640 out · memory at finish 61.4 MB`
     *
     * "memory at finish" is the whole claim: one heap sample taken when the task
     * ended, never a peak and never a running average.
     *
     * Rules the tests pin: no words beyond the labels; a missing token side is
     * `—`; a provider that reported no usage at all says "tokens not reported"
     * once the task is finished; an unfinished task omits tokens entirely rather
     * than claiming none; nothing is shown at all until something is known.
     */
    fun render(m: AiMeasurements): String? {
        if (m.isEmpty) return null
        val parts = mutableListOf<String>()
        m.firstTokenMs?.let { parts += "first " + duration(it) }
        m.totalMs?.let { parts += "total " + duration(it) }
        tokenText(m)?.let { parts += it } ?: run {
            if (m.totalMs != null) parts += TOKENS_UNREPORTED
        }
        m.memoryBytes?.let { parts += "memory at finish " + bytes(it) }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)
    }

    private const val TOKENS_UNREPORTED = "tokens not reported"

    private fun tokenText(m: AiMeasurements): String? {
        if (m.promptTokens == null && m.outputTokens == null) return null
        val partial = if (m.tokensComplete) "" else " (partial)"
        return "tokens " + count(m.promptTokens) + " in / " + count(m.outputTokens) + " out" + partial
    }

    private fun count(n: Int?): String = n?.let(::thousands) ?: "—"

    /** `640` / `1 812` / `128 405` — grouped like the APK sizes in the docs. */
    fun thousands(n: Int): String {
        val digits = n.toString()
        val out = StringBuilder(digits.length + 2)
        for ((index, c) in digits.withIndex()) {
            if (index > 0 && (digits.length - index) % 3 == 0) out.append(' ')
            out.append(c)
        }
        return out.toString()
    }

    /**
     * `820 ms` / `1.4 s` / `2 m 5 s` — one decimal below a minute, never a raw
     * float and never a locale-sensitive format call. The unit is chosen from the
     * *rounded* tenths, so a hair under a minute reads `59.9 s` and 59.95 s reads
     * `1 m 0 s` instead of a nonsensical `60.0 s`.
     */
    fun duration(ms: Long): String {
        if (ms < 1_000L) return "$ms ms"
        val tenths = (ms + 50L) / 100L
        if (tenths < 600L) return "${tenths / 10L}.${tenths % 10L} s"
        val seconds = tenths / 10L
        return "${seconds / 60L} m ${seconds % 60L} s"
    }

    /** `980 B` / `61 KB` / `61.4 MB` — binary units, one decimal at MB. */
    fun bytes(n: Long): String = when {
        n < 1024L -> "$n B"
        n < 1024L * 1024L -> "${n / 1024L} KB"
        else -> {
            val tenthsMb = n * 10L / (1024L * 1024L)
            "${tenthsMb / 10L}.${tenthsMb % 10L} MB"
        }
    }
}
