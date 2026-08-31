package com.codeci.ide.ui.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Phase 19.3 — frame-paced render emitter.
 *
 * The PTY reader used to call `publish()` after every 4 KB chunk into a
 * conflating [kotlinx.coroutines.flow.MutableStateFlow]. During a burst (a
 * download progress bar rewritten with `\r`) the reader overwrote the state
 * far faster than Compose recomposes, so the UI collected one value — the
 * final one — and "everything appeared after the download".
 *
 * This class re-implements the *mechanism* mature terminals use (decouple
 * reading from rendering): the reader only marks state dirty
 * ([markDirty]); a single coroutine waits for that signal, publishes one
 * snapshot, then sleeps one frame interval. Signals arriving during the
 * sleep coalesce into the next publish, so:
 *  - a burst publishes at most one snapshot per frame (~60/s) — cheap;
 *  - intermediate states are guaranteed to be published once per frame, so
 *    `\r` progress bars animate;
 *  - when the terminal is idle the loop is suspended on [receive] and
 *    costs nothing.
 */
class RenderPump(
    private val frameIntervalMs: Long = DEFAULT_FRAME_INTERVAL_MS,
    private val publish: () -> Unit
) {
    private val signal = Channel<Unit>(Channel.CONFLATED)

    /** Reader-side: state changed; publish at frame cadence. Never blocks. */
    fun markDirty() {
        signal.trySend(Unit)
    }

    /**
     * Starts the paced emitter in [scope]. Cancelling the returned job parks
     * it; the caller keeps responsibility for any final direct publish.
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            // Parks until output has arrived since the last publish.
            signal.receive()
            publish()
            // Everything fed during this window coalesces into the next
            // iteration, which publishes the *latest* state.
            delay(frameIntervalMs)
        }
    }

    companion object {
        /** ~60 fps; exposed as a constant for tests and tuning. */
        const val DEFAULT_FRAME_INTERVAL_MS = 16L
    }
}
