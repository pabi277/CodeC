package com.codeci.bench.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28.1 — the shared session law: script and thumb both go through
 * `press`, the echo records every commit exactly once, the ledger samples the
 * DOWN→commit span, and the stdin route (spike question Q2) edits ONLY the
 * run buffer. The device side can't fake these behaviors; the host pins them.
 */
class SpikeSessionTest {

    @Test fun `press routes to the screen commit lambda and echoes once`() {
        val session = SpikeSession()
        var applied = 0
        session.commit = { applied++ }
        session.press(GridKeycap.letter('a'), System.nanoTime())
        session.press(GridKeycap.SPACE, System.nanoTime())
        assertEquals(2, applied)
        assertEquals(listOf("a", "space"), session.snapshotEcho())
        assertEquals(2, session.commitCount)
    }

    @Test fun `ledger gets a sample per press`() {
        val session = SpikeSession()
        session.commit = { }
        repeat(5) { session.press(GridKeycap.letter('x'), System.nanoTime()) }
        assertEquals(5, session.ledger.snapshot().count)
    }

    @Test fun `run route edits the buffer not the document`() {
        val session = SpikeSession()
        var docEdits = 0
        session.commit = { docEdits++ }
        session.routeToRunRow = true
        for (label in listOf("r", "u", "n", "space", "x")) {
            CodecKeyGrid.find(label)?.let { session.press(it, System.nanoTime()) }
        }
        session.press(CodecKeyGrid.find("DEL")!!, System.nanoTime())
        assertEquals(0, docEdits)
        assertEquals("run ", session.runRowText.toString())
    }

    @Test fun `resetRun clears echo ledger and counters`() {
        val session = SpikeSession()
        session.commit = { }
        repeat(3) { session.press(GridKeycap.letter('a'), System.nanoTime()) }
        session.addImeSample(120)
        session.resetRun()
        assertTrue(session.snapshotEcho().isEmpty())
        assertEquals(0, session.snapshotIme().size)
        assertEquals(0, session.commitCount)
        assertEquals(0, session.ledger.snapshot().count)
    }

    @Test fun `haptic tick fires before the commit exactly once per press`() {
        val session = SpikeSession()
        var ticks = 0
        var commits = 0
        session.hapticTick = { ticks++ }
        session.commit = { commits++ }
        session.press(GridKeycap.letter('q'), System.nanoTime())
        session.haptics = false
        session.press(GridKeycap.letter('q'), System.nanoTime())
        assertEquals(1, ticks)
        assertEquals(2, commits)
    }
}
