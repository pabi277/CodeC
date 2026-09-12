package com.codeci.ide.ui.terminal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 44.1 — the process-wide hand-off of the setup truth to surfaces that
 * do not own the terminal ViewModel (the editor's "Install X?" prompt is the
 * one that mattered: it streamed `pkg install -y <pkg>` into the Output Panel
 * with no idea whether a `bin/pkg` existed, and the user got a bare shell
 * error — the owner's exact report, in a second surface).
 *
 * Same shape as [SetupNoticeBridge] / `CrashHandOffBridge`: a tiny StateFlow
 * the [TerminalViewModel] publishes and anyone may read synchronously. `null`
 * means "nothing has published yet" — readers then fall back to
 * [SetupGatePolicy.diskFacts] instead of assuming the optimistic answer.
 */
object SetupStateBridge {

    private val _facts = MutableStateFlow<SetupFacts?>(null)

    val facts: StateFlow<SetupFacts?> = _facts

    fun publish(facts: SetupFacts) {
        _facts.value = facts
    }

    /** The live facts, or the honest disk-derived fallback. */
    fun factsOrDisk(prefixDir: java.io.File, ledgerPhase: SetupPhase): SetupFacts =
        _facts.value ?: SetupGatePolicy.diskFacts(prefixDir, ledgerPhase)

    fun clear() {
        _facts.value = null
    }
}
