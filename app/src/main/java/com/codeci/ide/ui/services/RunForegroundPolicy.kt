package com.codeci.ide.ui.services

/**
 * Phase 24.2 — the 5-second foreground-promotion threshold as a pure value so
 * the rule (short runs stay silent) is host-unit-testable and does not live in
 * Android `Service` code.
 */
object RunForegroundPolicy {
    const val THRESHOLD_MS = 5_000L

    /** True once a run has been alive for at least [THRESHOLD_MS]. */
    fun shouldPromote(elapsedMs: Long): Boolean = elapsedMs >= THRESHOLD_MS
}
