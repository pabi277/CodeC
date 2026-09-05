package com.codeci.bench.keys

/**
 * Phase 28.1 — the scripted grid bursts, PURE (no Android) like 25.1's
 * InputScripts, so the device runs and the host tests press the identical
 * sequence. The scripts speak in CAP LABELS, not key codes: the runner
 * resolves each label through [CodecKeyGrid] and invokes the very press
 * handler the owner's thumb triggers, which is what makes the numbers
 * comparable between script and finger.
 */
data class GridEvent(
    /** [CodecKeyGrid] cap label — a single lowercase letter, or TAB/DEL/space/⏎. */
    val label: String,
    /** Offset from run start at which the press fires (spec: @40 ms cadence). */
    val atMs: Long
)

data class GridScript(
    val name: String,
    val durationMs: Long,
    val events: List<GridEvent>
) {
    /** The echo the editor must produce for a clean run (one entry per press). */
    fun expectedEcho(): List<String> = events.map { it.label }

    /** The exact document an empty buffer must hold after the script, typed in order. */
    fun expectedText(): String =
        CodecKeyGrid.expectedText(events.map { CodecKeyGrid.find(it.label) ?: error("unknown cap '${it.label}'") })
}

object KeysSpikeScripts {

    /** Human-fast typing cadence — the same 40 ms law as 25.1's burst. */
    const val BURST_INTERVAL_MS = 40L

    /** Settle time appended after the last press, as in 25.1. */
    const val SETTLE_MS = 400L

    /** Hold-repeat law from 26.1: 150 ms initial, 40 ms repeat. */
    const val HOLD_INITIAL_MS = 150L
    const val HOLD_REPEAT_MS = 40L

    /**
     * `type_burst64` — 64 commits of real C-ish prose typed entirely on the
     * letters grid: identifiers, spaces, newlines, TAB indents, and a typo
     * fixed with DEL. The burst therefore exercises insert AND delete on the
     * IME-free path. Expected text is derived from the SAME pure model the
     * presses run through, never hardcoded.
     */
    fun typeBurst64(): GridScript {
        val taps = mutableListOf<String>()
        // Space is the wide cap labeled "space" — not the ' ' character.
        fun type(s: String) = s.forEach { taps += if (it == ' ') "space" else it.toString() }
        type("include cstdlib")     // 15
        taps += "⏎"; taps += "⏎"    // 17
        type("int main")           // 25
        taps += "⏎"; taps += "⏎"    // 27
        taps += "TAB"              // 28
        type("return value")       // 40
        taps += "⏎"                // 41
        taps += "TAB"              // 42
        type("value value")        // 53
        type("xyz")                // 56  — stray keys
        repeat(3) { taps += "DEL" } // 59  — three backspaces
        type("value")              // 64  — retyped tail
        require(taps.size == 64) { "type_burst64 must press exactly 64 keys, is ${taps.size}" }
        var t = 100L
        val events = taps.map { label -> GridEvent(label, t.also { t += BURST_INTERVAL_MS }) }
        return GridScript("type_burst64", t - 100L + SETTLE_MS, events)
    }

    /**
     * `hold_repeat30` — exactly 30 presses whose commit count is 40 (spec:
     * "30-key burst with hold-repeat on: no dropped/swapped events"). Two
     * presses are HOLDS: DEL repeats to 6 commits and SPACE to 6 commits,
     * following 26.1's 150 ms initial + 40 ms repeat cadence. A clean run is:
     * every repeat commit present, ordered, no double-up events.
     */
    fun holdRepeat30(): GridScript {
        val events = mutableListOf<GridEvent>()
        var t = 100L
        fun tap(label: String) {
            events += GridEvent(label, t)
            t += BURST_INTERVAL_MS
        }
        fun hold(label: String, repeats: Int) {
            repeat(repeats + 1) { i ->
                val at = if (i == 0) t else t + HOLD_INITIAL_MS + (i - 1) * HOLD_REPEAT_MS
                events += GridEvent(label, at)
            }
            t += HOLD_INITIAL_MS + repeats * HOLD_REPEAT_MS + BURST_INTERVAL_MS
        }
        "int main void sum".forEach { if (it == ' ') tap("space") else tap(it.toString()) } // 17 presses
        hold("space", 5)                                                                     // +1 → 18
        "for".forEach { tap(it.toString()) }; tap("space")                                   // +4 → 22
        "xyz".forEach { tap(it.toString()) }                                                 // +3 → 25 — typo trio
        hold("DEL", 5)                                                                       // +1 → 26 — erases "xyz" + " " + "ro"
        "for ".forEach { if (it == ' ') tap("space") else tap(it.toString()) }               // +4 → 30 — retypes the word
        require(events.size == 40) { "hold_repeat30 must fire 40 commits, got ${events.size}" }
        require(events.count { it.label == "DEL" } == 6) { "DEL hold must fire 6 commits" }
        require(events.count { it.label == "space" } == 11) { "3 intro + 6 held + 1 after 'for' + 1 tail" }
        var prev = -1L
        for (e in events) {
            require(e.atMs > prev) { "events must strictly advance" }
            prev = e.atMs
        }
        return GridScript("hold_repeat30", t - 100L + SETTLE_MS, events)
    }

    /**
     * `run_row_check` — spike question Q2: 12 commits routed to the stdin row
     * ("run stdin x" then one DEL ⇒ the buffer must hold exactly
     * "run stdin "). While it runs, the editor document must not move at
     * all — proving the run-input path is orthogonal to the editor's IME
     * suppression (the shipping run strip never types through an
     * InputConnection; this models it with the same grid).
     */
    fun runRowCheck(): GridScript {
        val taps = "run stdin x".map { if (it == ' ') "space" else it.toString() } + listOf("DEL")
        require(taps.size == 12)
        var t = 100L
        val events = taps.map { label -> GridEvent(label, t.also { t += BURST_INTERVAL_MS }) }
        return GridScript("run_row_check", t - 100L + SETTLE_MS, events)
    }
}
