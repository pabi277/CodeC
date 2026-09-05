package com.codeci.bench.keys

import com.codeci.ide.ui.editor.EditorKey

/**
 * Phase 28.1 — the ONE object a spike screen and the scripted runner share.
 * PURE (no Android imports): the live ticker feeds [addImeSample] from the
 * harness side, so everything recorded here is host-testable.
 *
 * Design law: a scripted press and a physical thumb tap must travel the exact
 * same code. So the grid composable's pointer handler and [KeyScriptRunner]
 * both call [press]; the screen installs the document-edit lambda ([commit]);
 * the ledger, the tap echo and the IME-inset samples are recorded inside that
 * single path. Nothing in here knows what editor core it feeds.
 *
 * `routeToRunRow` answers spike question Q2 mechanically: with the editor's
 * IME connection deliberately suppressed, commits re-routed to a plain
 * "stdin row" (the Phase 23 run-input analogue) must keep landing there —
 * proving the run path is orthogonal to whatever the editor does with the IME
 * connection.
 */
class SpikeSession(val ledger: KeyLatencyLedger = KeyLatencyLedger()) {

    /** Haptic tick on key-down (the "feel" half of the gate). */
    @Volatile var haptics: Boolean = true

    /** Q2 routing: commits go to [runRowText] instead of the editor. */
    @Volatile var routeToRunRow: Boolean = false

    /** Screen-installed: applies the cap to the document. */
    @Volatile var commit: ((GridKeycap) -> Unit)? = null

    /** The grid UI wires this to Compose's haptic feedback; null in tests. */
    @Volatile var hapticTick: (() -> Unit)? = null

    private val echo = ArrayList<String>()
    private val imeSamples = ArrayList<Int>()

    /** The stdin-row text (the screen renders it; the audit reads the size). */
    val runRowText = StringBuilder()

    /** Commits observed (the live line + lengthDelta math). */
    @Volatile var commitCount: Int = 0
        private set

    /** What the K1/K2 grid does when a run row owns the thumbs (Q2 scenario). */
    var onRunRowCommit: ((GridKeycap) -> Unit)? = null

    fun press(cap: GridKeycap, downNs: Long) {
        if (haptics) hapticTick?.invoke()
        if (routeToRunRow) {
            synchronized(runRowText) { RunRowEdit.apply(runRowText, cap) }
            onRunRowCommit?.invoke(cap)
        } else {
            commit?.invoke(cap)
        }
        ledger.record(System.nanoTime() - downNs)
        synchronized(echo) { echo += cap.label }
        commitCount++
    }

    fun snapshotEcho(): List<String> = synchronized(echo) { echo.toList() }

    fun addImeSample(px: Int) = synchronized(imeSamples) { imeSamples += px }

    fun snapshotIme(): IntArray = synchronized(imeSamples) { imeSamples.toIntArray() }

    /** Clears everything a scenario run counts (echo, ledger, samples). */
    fun resetRun() {
        ledger.clear()
        synchronized(echo) { echo.clear() }
        synchronized(imeSamples) { imeSamples.clear() }
        synchronized(runRowText) { runRowText.setLength(0) }
        commitCount = 0
    }
}

/**
 * The pure decision law of the run-row route: DEL edits the stdin buffer
 * instead of the document; letters/spaces append. Extracted so the host test
 * pins it without touching the session's Android-adjacent plumbing.
 */
object RunRowEdit {
    fun apply(buffer: StringBuilder, cap: GridKeycap): StringBuilder {
        if (cap.backspace) {
            if (buffer.isNotEmpty()) buffer.setLength(buffer.length - 1)
        } else {
            buffer.append(defaultTextFor(cap))
        }
        return buffer
    }

    fun defaultTextFor(cap: GridKeycap): String = when {
        cap.label == "space" -> " "
        cap.label == "⏎" -> "\n"
        cap.key is EditorKey.Tab -> "\t"
        cap.key is EditorKey.Insert -> (cap.key as EditorKey.Insert).text
        else -> cap.label
    }
}
