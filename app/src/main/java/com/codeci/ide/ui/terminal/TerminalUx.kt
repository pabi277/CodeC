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
