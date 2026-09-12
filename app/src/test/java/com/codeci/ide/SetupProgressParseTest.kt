package com.codeci.ide

import com.codeci.ide.ui.terminal.InstallProgress
import com.codeci.ide.ui.terminal.SetupAnnouncer
import com.codeci.ide.ui.terminal.SetupGatePolicy
import com.codeci.ide.ui.terminal.SetupIssue
import com.codeci.ide.ui.terminal.SetupStage
import com.codeci.ide.ui.terminal.SetupTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 44.1 — the installer's EXISTING progress lines, parsed.
 *
 * The installer's contract (`onProgress: (String) -> Unit`) is untouched by
 * this phase: [SetupGatePolicy.parse] is the only reader of those strings, so
 * the structured state can never drift from what the terminal prints — and
 * every line below is a literal copied out of `UserlandInstaller.kt` /
 * `TerminalViewModel.kt`, not an invented one.
 */
class SetupProgressParseTest {

    private fun parse(line: String) = SetupGatePolicy.parse(line)

    @Test
    fun `the download percentage line carries stage, percent and bytes`() {
        val p = parse("userland: download 41% (1234567 bytes)")
        assertEquals(SetupStage.DOWNLOADING, p.stage)
        assertEquals(41, p.percent)
        assertEquals(1234567L, p.bytes)
        assertTrue(p.inFlight)
    }

    @Test
    fun `zero percent is a download in progress, never ready`() {
        val p = parse("userland: download 0% (0 bytes)")
        assertEquals(SetupStage.DOWNLOADING, p.stage)
        assertEquals(0, p.percent)
        assertFalse(p.settled)
    }

    @Test
    fun `a hundred percent is still not ready until the installer says so`() {
        val p = parse("userland: download 100% (182000000 bytes)")
        assertEquals(SetupStage.DOWNLOADING, p.stage)
        assertEquals(100, p.percent)
        assertFalse(p.settled)
        assertTrue(parse("userland: ready").settled)
    }

    @Test
    fun `the download start line has a stage but no invented percentage`() {
        val p = parse("userland: downloading bootstrap-phase3-aarch64.tar.gz…")
        assertEquals(SetupStage.DOWNLOADING, p.stage)
        assertNull("no size known yet", p.percent)
    }

    @Test
    fun `verify and extract lines map to their stages`() {
        assertEquals(SetupStage.CHECKING, parse("userland: fetching SHA-256…").stage)
        assertEquals(SetupStage.VERIFYING, parse("userland: verifying SHA-256…").stage)
        assertEquals(
            SetupStage.EXTRACTING,
            parse("userland: extracting into /data/user/0/com.codeci.ide/files/usr").stage
        )
    }

    @Test
    fun `every already-installed line reads as ready`() {
        for (line in listOf(
            "userland: ready",
            "userland: marker valid — using installed userland",
            "userland: already installed (userland-v2-dev)",
            "userland: offline — using installed userland",
            "userland: release check failed — keeping installed userland"
        )) {
            assertEquals(line, SetupStage.READY, parse(line).stage)
        }
    }

    @Test
    fun `offline without a userland is a failure that names the network`() {
        val p = parse("userland: offline — using built-in cc (TCC)")
        assertEquals(SetupStage.FAILED, p.stage)
        assertEquals(SetupIssue.OFFLINE, p.issue)
        val message = SetupGatePolicy.refusal(
            com.codeci.ide.ui.terminal.SetupAction.INSTALL_PACKAGE,
            p,
            com.codeci.ide.ui.terminal.SetupFacts(progress = p)
        )
        assertTrue(message, message.contains("needs the network once"))
    }

    @Test
    fun `no bootstrap for this ABI is unsupported, not failed`() {
        assertEquals(SetupStage.UNSUPPORTED, parse("userland: no bootstrap for this ABI").stage)
        assertEquals(SetupStage.UNSUPPORTED, parse("userland: no release yet").stage)
    }

    @Test
    fun `disk space failure names storage`() {
        val p = parse(
            "userland: insufficient disk space (have 1048576 bytes, need at least 50331648) " +
                "— cc (TCC) still works offline"
        )
        assertEquals(SetupStage.FAILED, p.stage)
        assertEquals(SetupIssue.DISK, p.issue)
    }

    @Test
    fun `a checksum mismatch is corrupt, not offline, despite the trailing cc clause`() {
        val p = parse("userland: SHA-256 mismatch (got 9a8b…) — cc (TCC) still works offline")
        assertEquals(SetupStage.FAILED, p.stage)
        assertEquals(SetupIssue.CORRUPT, p.issue)
    }

    @Test
    fun `a tree that cannot start is a broken userland`() {
        val p = parse(
            "userland: installed, but no shell can start: missing library " +
                "libandroid-support.so — reinstall or check disk space"
        )
        assertEquals(SetupStage.FAILED, p.stage)
        assertEquals(SetupIssue.BROKEN_USERLAND, p.issue)
    }

    @Test
    fun `an upgrade announcement and a fallback are still in flight`() {
        assertEquals(
            SetupStage.CHECKING,
            parse("userland: upgrading userland-v1 to userland-v2-dev…").stage
        )
        assertEquals(
            SetupStage.CHECKING,
            parse(
                "userland: Phase 3 bootstrap unusable (asset missing) — " +
                    "falling back to userland-v1"
            ).stage
        )
    }

    @Test
    fun `an unknown setup line stays checking and keeps the raw text`() {
        val p = parse("userland: something nobody ever wrote")
        assertEquals(SetupStage.CHECKING, p.stage)
        assertEquals("something nobody ever wrote", p.detail)
        assertNull(p.percent)
    }

    @Test
    fun `a line with no percentage in it never produces one`() {
        for (line in listOf(
            "userland: ready",
            "userland: extracting into /data/x",
            "userland: something nobody ever wrote"
        )) {
            assertNull(line, parse(line).percent)
        }
    }

    @Test
    fun `the terminal's own notices are not setup lines`() {
        assertFalse(SetupGatePolicy.isSetupLine("[terminal] shell restarted"))
        assertFalse(SetupGatePolicy.isSetupLine("bash: pkg: not found"))
        assertTrue(SetupGatePolicy.isSetupLine("userland: ready"))
        assertTrue(SetupGatePolicy.isSetupLine("  userland: download 3% (1 bytes)"))
    }

    // ---- the state machine --------------------------------------------------

    @Test
    fun `the tracker follows a whole install and settles at ready`() {
        val tracker = SetupTracker()
        assertEquals(SetupStage.CHECKING, tracker.state.stage)
        tracker.observe("userland: fetching SHA-256…")
        tracker.observe("userland: downloading bootstrap-phase3-aarch64.tar.gz…")
        assertEquals(SetupStage.DOWNLOADING, tracker.state.stage)
        tracker.observe("userland: download 10% (1000 bytes)")
        tracker.observe("userland: download 55% (55000 bytes)")
        assertEquals(55, tracker.state.percent)
        tracker.observe("userland: verifying SHA-256…")
        assertEquals(SetupStage.VERIFYING, tracker.state.stage)
        tracker.observe("userland: extracting into /data/usr")
        assertEquals(SetupStage.EXTRACTING, tracker.state.stage)
        tracker.observe("userland: ready")
        assertEquals(SetupStage.READY, tracker.state.stage)
        assertTrue(tracker.state.settled)
    }

    @Test
    fun `a terminal notice can never knock a settled setup back to checking`() {
        val tracker = SetupTracker()
        tracker.observe("userland: ready")
        tracker.observe("[terminal] shell restarted")
        assertEquals(SetupStage.READY, tracker.state.stage)
        // And a bare re-check carries no new information either.
        tracker.note(InstallProgress(SetupStage.CHECKING))
        assertEquals(SetupStage.READY, tracker.state.stage)
    }

    @Test
    fun `no busy flag survives a failure — the spinner-forever bug class`() {
        val tracker = SetupTracker()
        tracker.observe("userland: download 62% (6200 bytes)")
        assertTrue(tracker.state.inFlight)
        val failed = tracker.fail("cannot move old userland aside")
        assertTrue(failed.settled)
        assertFalse(failed.inFlight)
        assertEquals(SetupStage.FAILED, tracker.state.stage)
        assertNull("a dead download has no percentage", tracker.state.percent)

        val unsupported = SetupTracker().unsupported("no bootstrap")
        assertTrue(unsupported.settled)
        assertEquals(SetupStage.UNSUPPORTED, unsupported.stage)

        // A throw out of the installer is a settled state too.
        val thrown = SetupTracker()
        thrown.observe("userland: extracting into /data/usr")
        thrown.fail("boom")
        assertFalse(thrown.state.inFlight)
    }

    @Test
    fun `a deliberate retry restarts from checking`() {
        val tracker = SetupTracker()
        tracker.observe("userland: ready")
        tracker.restart("reinstall requested")
        assertEquals(SetupStage.CHECKING, tracker.state.stage)
        assertTrue(tracker.state.inFlight)
    }

    // ---- the throttles ------------------------------------------------------

    @Test
    fun `the notification is rewritten at most once per five percent or two seconds`() {
        val first = InstallProgress(SetupStage.DOWNLOADING, percent = 10)
        assertTrue(SetupAnnouncer.shouldPublish(null, first, 1_000L, 0L))

        val plus2 = InstallProgress(SetupStage.DOWNLOADING, percent = 12)
        assertFalse("2 % later, 200 ms later: too soon", SetupAnnouncer.shouldPublish(first, plus2, 1_200L, 1_000L))

        val plus5 = InstallProgress(SetupStage.DOWNLOADING, percent = 15)
        assertTrue("5 % is a step", SetupAnnouncer.shouldPublish(first, plus5, 1_200L, 1_000L))

        val plus3Late = InstallProgress(SetupStage.DOWNLOADING, percent = 13)
        assertTrue(
            "over 2 s with a moved percentage",
            SetupAnnouncer.shouldPublish(first, plus3Late, 3_100L, 1_000L)
        )
        assertFalse(
            "over 2 s with the SAME percentage is still silence",
            SetupAnnouncer.shouldPublish(first, first, 3_100L, 1_000L)
        )
    }

    @Test
    fun `a stage change is always announced, and a settled stage announces once`() {
        val downloading = InstallProgress(SetupStage.DOWNLOADING, percent = 99)
        val verifying = InstallProgress(SetupStage.VERIFYING)
        assertTrue(SetupAnnouncer.shouldPublish(downloading, verifying, 10L, 5L))
        assertFalse(SetupAnnouncer.shouldPublish(verifying, verifying, 99_999L, 10L))
        val ready = InstallProgress(SetupStage.READY, detail = "userland-v2-dev")
        assertTrue(SetupAnnouncer.shouldPublish(verifying, ready, 20L, 10L))
        assertFalse(SetupAnnouncer.shouldPublish(ready, ready, 30L, 20L))
    }

    @Test
    fun `the in-app flow publishes on stage or percent change, not per chunk`() {
        val a = InstallProgress(SetupStage.DOWNLOADING, percent = 41, bytes = 1L)
        assertTrue(SetupAnnouncer.shouldPublishState(null, a))
        // Same stage, same percent, more bytes: no recomposition.
        assertFalse(SetupAnnouncer.shouldPublishState(a, a.copy(bytes = 2L)))
        assertTrue(SetupAnnouncer.shouldPublishState(a, a.copy(percent = 42)))
        assertTrue(SetupAnnouncer.shouldPublishState(a, InstallProgress(SetupStage.VERIFYING)))
    }
}
