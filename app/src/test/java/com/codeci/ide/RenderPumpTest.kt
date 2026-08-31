package com.codeci.ide

import com.codeci.ide.ui.terminal.RenderPump
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 19.3 — the frame-paced emitter must (a) coalesce a burst of chunks
 * into one publish per frame, (b) guarantee intermediate frames are published
 * so `\r` progress bars animate, (c) publish nothing while idle, and (d)
 * publish the latest state after the frame window.
 */
class RenderPumpTest {

    @Test
    fun `a burst within one frame window publishes exactly once`() = runTest {
        var publishes = 0
        val pump = RenderPump(frameIntervalMs = 16) { publishes++ }
        pump.start(backgroundScope)

        repeat(50) { pump.markDirty() }
        advanceTimeBy(16)

        assertEquals(1, publishes)
    }

    @Test
    fun `bursts across separate frames publish one frame each`() = runTest {
        var publishes = 0
        val pump = RenderPump(frameIntervalMs = 16) { publishes++ }
        pump.start(backgroundScope)

        repeat(20) { pump.markDirty() }
        advanceTimeBy(16)
        repeat(20) { pump.markDirty() }
        advanceTimeBy(16)
        repeat(20) { pump.markDirty() }
        advanceTimeBy(16)

        assertEquals(3, publishes)
    }

    @Test
    fun `intermediate states are published - progress animates`() = runTest {
        val seen = mutableListOf<Int>()
        var state = 0
        val pump = RenderPump(frameIntervalMs = 16) { seen.add(state) }
        pump.start(backgroundScope)

        state = 10
        pump.markDirty()
        advanceTimeBy(16)
        state = 50
        pump.markDirty()
        advanceTimeBy(16)
        state = 100
        pump.markDirty()
        // The buffered signal resumes the parked receiver without needing
        // virtual time to advance, so the task is queued for "now".
        runCurrent()

        // Every frame carries the state as it was at that moment — the
        // pre-fix behavior collapsed this to a single 100.
        assertEquals(listOf(10, 50, 100), seen)
    }

    @Test
    fun `idle pump publishes nothing`() = runTest {
        var publishes = 0
        val pump = RenderPump(frameIntervalMs = 16) { publishes++ }
        pump.start(backgroundScope)

        advanceTimeBy(5_000)

        assertEquals(0, publishes)
    }

    @Test
    fun `latest state wins after a busy frame window`() = runTest {
        val seen = mutableListOf<Int>()
        var state = 0
        val pump = RenderPump(frameIntervalMs = 16) { seen.add(state) }
        pump.start(backgroundScope)

        state = 1
        pump.markDirty()
        advanceTimeBy(16)
        // All of these land inside the *next* frame window: one publish,
        // carrying the newest state.
        state = 2
        pump.markDirty()
        state = 3
        pump.markDirty()
        runCurrent()

        assertEquals(listOf(1, 3), seen)
    }

    @Test
    fun `dirty before start publishes once started`() = runTest {
        var publishes = 0
        val pump = RenderPump(frameIntervalMs = 16) { publishes++ }
        pump.markDirty()
        advanceTimeBy(16)
        assertEquals(0, publishes)

        pump.start(backgroundScope)
        runCurrent()

        assertEquals(1, publishes)
    }
}
