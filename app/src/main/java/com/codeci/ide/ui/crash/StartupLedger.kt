package com.codeci.ide.ui.crash

/**
 * Phase 42.3 — the crash-loop guard's ledger (spec:
 * docs/chat-phase42/PART_42_3_LAUNCH_SAFETY.md §2).
 *
 * Symptom being closed: NOTHING used to guard a crash *during startup*
 * (a corrupt settings value, a half-written project file, a module the new
 * device can't load) repeated on every launch — the crash overlay appears,
 * the user dismisses it, the app dies again, and the only ways out were
 * "clear data" or uninstall, both of which delete every project.
 *
 * Design — deliberately small: two counters' worth of truth
 * (`starting`, `consecutiveCount`) in a synchronous store consulted before
 * `MainActivity.onCreate` does any heavier work. No daemon, no service, no
 * background process; when the main screen has been drawn the marker is
 * cleared. The launcher thresholds are pinned by [StartupLedgerTest] —
 * first launch NORMAL, ONE interrupted start NORMAL, two in a row
 * LOOP_SUSPECTED, a finished start clears everything.
 *
 * Persistence is an injected [Store] (MainActivity backs it with a
 * SharedPreferences — NOT DataStore, which needs a coroutine and cannot be
 * read synchronously in the first instructions of a process). The decision
 * layer therefore stays pure and host-testable, and "safe mode writes
 * nothing back" is mechanically checkable: [noteLaunch] is the ONLY write
 * path, and it only ever writes the marker itself.
 *
 * Corrupt/unreadable values are treated as *no crash*: a counter that
 * cannot be read must never trap the user in safe mode because the file
 * was unreadable.
 */
class StartupLedger(private val store: Store) {

    /** The synchronous two-cell storage (one read+write per launch). */
    interface Store {
        /** True when the previous start set the marker and never cleared it. */
        fun readStarting(): Boolean?

        /** Consecutive interrupted starts recorded so far. */
        fun readCount(): Int?

        /** The one write path: persists the marker atomically. */
        fun write(starting: Boolean, count: Int)
    }

    /** What the guard decided about THIS launch. */
    enum class Plan {
        NORMAL,

        /** Two interrupted starts in a row: show the loop sentence + safe-mode door. */
        LOOP_SUSPECTED
    }

    /**
     * Call ONCE, synchronously, as the first work of the process (before
     * any init whose crash should count as a startup crash). Sets the
     * marker for this start and answers how the launcher should behave.
     */
    fun noteLaunch(): Plan {
        val wasStarting = runCatching { store.readStarting() }.getOrNull() ?: false
        val previous = (runCatching { store.readCount() }.getOrNull() ?: 0).coerceIn(0, 1_000)
        val count = if (wasStarting) (previous + 1).coerceAtMost(1_000) else 0
        // The ONLY write of the whole mechanism — the marker itself.
        runCatching { store.write(starting = true, count = count) }
        return if (count >= LOOP_THRESHOLD) Plan.LOOP_SUSPECTED else Plan.NORMAL
    }

    /**
     * Call once the main screen has been drawn (the same point where the
     * crash overlay is mounted): the start succeeded, so the counter and
     * the marker clear. Also called when the user accepts the reduced
     * (safe-mode) start — the accepted reduced start IS the repair, and a
     * crash AFTER it counts from zero like any other.
     */
    fun noteStartupFinished() {
        runCatching { store.write(starting = false, count = 0) }
    }

    companion object {
        /** Two interrupted starts => the third launch is offered safe mode. */
        const val LOOP_THRESHOLD = 2
    }
}
