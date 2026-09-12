package com.codeci.ide.ui.terminal

import com.codeci.ide.ui.services.CompilerSettings

/**
 * The user-visible lifecycle of one terminal session. A PTY can be alive
 * before the first prompt is usable, so STARTING is deliberately distinct
 * from RUNNING.
 */
enum class TerminalLifecycle {
    STARTING,
    RUNNING,
    EXITED,
    FAILED
}

/**
 * A small ordered command queue used while a shell is bootstrapping. It is
 * intentionally independent from Android/coroutines so the ordering and
 * readiness contract can be tested on the host JVM.
 */
class OrderedReadinessQueue {
    private val pending = ArrayDeque<String>()
    private var ready = false

    val isReady: Boolean get() = ready
    val size: Int get() = pending.size

    /** Adds a command, preserving FIFO order until the shell is ready. */
    fun enqueue(command: String) {
        if (command.isNotBlank()) pending.addLast(command)
    }

    /** Marks the queue ready and returns all commands in their original order. */
    fun markReady(): List<String> {
        ready = true
        return drain()
    }

    /** Returns queued commands without changing readiness. */
    fun drain(): List<String> = buildList {
        while (pending.isNotEmpty()) add(pending.removeFirst())
    }

    /** A failed/restarted shell must wait for a new readiness signal. */
    fun reset() {
        ready = false
    }
}

/**
 * The stable inputs that make a PreparedShell reusable. The userland stamp is
 * supplied by the installer/marker rather than by a shell probe: a valid
 * marker is enough to avoid revalidating and re-extracting on every open.
 */
data class PreparedShellCacheKey(
    val compilerSettings: CompilerSettings,
    val userlandStamp: String
)

/**
 * Monotonic startup timestamps. Null means that stage has not completed yet;
 * elapsed values are therefore safe to display/log for partial failures too.
 */
data class TerminalStartMeasurement(
    val tapAtMs: Long,
    val userlandDoneAtMs: Long? = null,
    val prepareDoneAtMs: Long? = null,
    val promptAtMs: Long? = null
) {
    val tapToUserlandMs: Long? get() = elapsed(tapAtMs, userlandDoneAtMs)
    val userlandToPrepareMs: Long? get() = elapsed(userlandDoneAtMs, prepareDoneAtMs)
    val prepareToPromptMs: Long? get() = elapsed(prepareDoneAtMs, promptAtMs)
    val tapToPromptMs: Long? get() = elapsed(tapAtMs, promptAtMs)

    fun userlandDone(atMs: Long): TerminalStartMeasurement = copy(userlandDoneAtMs = atMs)
    fun prepareDone(atMs: Long): TerminalStartMeasurement = copy(prepareDoneAtMs = atMs)
    fun prompt(atMs: Long): TerminalStartMeasurement = copy(promptAtMs = atMs)

    private fun elapsed(from: Long?, to: Long?): Long? =
        if (from != null && to != null) (to - from).coerceAtLeast(0L) else null
}

/**
 * Phase 44.1 — the terminal status chip tells the truth.
 *
 * Before this the chip mapped `TerminalLifecycle.STARTING` to the fixed string
 * `"starting shell…"`, which during a 200 MB one-time bootstrap download is a
 * lie by omission — and it was the only status a user who DID open the Terminal
 * tab could see. The mapping is pure so CI pins the whole
 * `(lifecycle, stage) → label` table, including FAILED and UNSUPPORTED.
 */
object TerminalStatusLabel {

    /** Which colour family the screen should use (no Compose in this file). */
    enum class Kind { SETUP, STARTING, RUNNING, FAILED, EXITED }

    data class Label(val text: String, val kind: Kind)

    fun label(
        lifecycle: TerminalLifecycle,
        stage: SetupStage,
        percent: Int? = null,
        exitCode: Int? = null
    ): Label = when (lifecycle) {
        TerminalLifecycle.EXITED -> Label(
            text = "exited" + (exitCode?.let { " ($it)" } ?: ""),
            kind = Kind.EXITED
        )
        TerminalLifecycle.FAILED -> Label("shell failed", Kind.FAILED)
        TerminalLifecycle.RUNNING -> Label("running", Kind.RUNNING)
        TerminalLifecycle.STARTING -> when (stage) {
            SetupStage.DOWNLOADING ->
                if (percent != null) {
                    Label("downloading userland $percent %", Kind.SETUP)
                } else {
                    Label("downloading userland…", Kind.SETUP)
                }
            SetupStage.VERIFYING -> Label("verifying download…", Kind.SETUP)
            SetupStage.EXTRACTING -> Label("unpacking userland…", Kind.SETUP)
            SetupStage.FAILED -> Label("setup incomplete — tap ⬇ to retry", Kind.FAILED)
            SetupStage.UNSUPPORTED -> Label("no Linux tools for this device", Kind.SETUP)
            SetupStage.CHECKING, SetupStage.READY -> Label("starting shell…", Kind.STARTING)
        }
    }

    /**
     * The "don't close the app" warning above the chip — the owner's own
     * solution to the invisible download, kept almost verbatim. Driven by the
     * setup stage alone, so it also covers an upgrade that starts while a shell
     * is already running.
     */
    fun showsDontCloseBar(stage: SetupStage): Boolean = stage != SetupStage.READY
}
