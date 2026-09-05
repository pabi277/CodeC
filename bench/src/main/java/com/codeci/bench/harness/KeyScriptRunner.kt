package com.codeci.bench.harness

import android.os.SystemClock
import com.codeci.bench.keys.CodecKeyGrid
import com.codeci.bench.keys.GridScript
import com.codeci.bench.keys.ImeFlicker
import com.codeci.bench.keys.ImeProbeResult
import com.codeci.bench.keys.LatencySummary
import com.codeci.bench.keys.SpikeSession
import com.codeci.bench.keys.TapAudit
import com.codeci.bench.keys.TapAuditor
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Phase 28.1 — lowers a [GridScript] onto a [SpikeSession] on the MAIN
 * dispatcher (the editor path is main-thread-only). Every event fires at
 * `runStart + atMs` and calls `session.press(...)` — the very handler the
 * owner's thumb hits — so scripted numbers and human numbers are the same
 * quantity. The audit compares the editor's echo against the script; a drop,
 * a double-fire or a swap is visible in [KeyRunOutcome.audit] rather than
 * being silently absorbed.
 */
object KeyScriptRunner {

    data class KeyRunOutcome(
        val audit: TapAudit,
        val latency: LatencySummary,
        val lengthDelta: Int,
        val ime: ImeProbeResult
    )

    suspend fun run(script: GridScript, session: SpikeSession, target: TypingTarget): KeyRunOutcome {
        val before = target.length()
        session.resetRun()
        val t0 = SystemClock.uptimeMillis()
        for (event in script.events) {
            val cap = CodecKeyGrid.find(event.label)
                ?: error("script label '${event.label}' is not a grid cap")
            val at = t0 + event.atMs
            val wait = at - SystemClock.uptimeMillis()
            if (wait > 0) delay(wait)
            // downNs taken immediately before press() — same anchor the
            // pointer handler uses (ACTION_DOWN time).
            session.press(cap, System.nanoTime())
        }
        // Settle window after the last press, as in 25.1.
        val tail = script.durationMs - (script.events.lastOrNull()?.atMs ?: 0L)
        if (tail > 0) delay(tail)
        val after = target.length()
        return KeyRunOutcome(
            audit = TapAuditor.verify(script.expectedEcho(), session.snapshotEcho()),
            latency = session.ledger.snapshot(),
            lengthDelta = abs(after - before),
            ime = ImeFlicker.analyze(session.snapshotIme())
        )
    }
}
