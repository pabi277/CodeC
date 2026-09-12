package com.codeci.ide

import com.codeci.ide.ui.terminal.InstallProgress
import com.codeci.ide.ui.terminal.SetupAction
import com.codeci.ide.ui.terminal.SetupFacts
import com.codeci.ide.ui.terminal.SetupGatePolicy
import com.codeci.ide.ui.terminal.SetupIssue
import com.codeci.ide.ui.terminal.SetupPhase
import com.codeci.ide.ui.terminal.SetupStage
import com.codeci.ide.ui.terminal.SetupVerdict
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 44.1 — the setup gate matrix (spec:
 * docs/chat-phase44/PART_44_1_VISIBLE_SETUP.md §1 "The gate law").
 *
 * The regression this pins: **C is never gated.** TCC lives in the APK and
 * Phase 21 made `.c` files install-gate-free permanently, so a setup surface
 * that blocked writing or running C would break the app's whole promise to
 * protect a minority case (a half-installed userland).
 */
class SetupGatePolicyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val stages = SetupStage.entries.toList()

    private fun facts(
        stage: SetupStage,
        percent: Int? = null,
        issue: SetupIssue? = null,
        runnable: Boolean = false,
        pkg: Boolean = false,
        swapping: Boolean = false
    ) = SetupFacts(
        progress = InstallProgress(stage, percent = percent, issue = issue),
        userlandRunnable = runnable,
        packageManager = pkg,
        swapping = swapping
    )

    // ---- the law: C, editing and the terminal are never gated ---------------

    @Test
    fun `RUN_C EDIT_FILE and OPEN_TERMINAL are allowed in every stage and every fact combination`() {
        for (stage in stages) {
            for (runnable in listOf(false, true)) {
                for (pkg in listOf(false, true)) {
                    for (swapping in listOf(false, true)) {
                        val f = facts(stage, runnable = runnable, pkg = pkg, swapping = swapping)
                        for (action in listOf(
                            SetupAction.RUN_C,
                            SetupAction.EDIT_FILE,
                            SetupAction.OPEN_TERMINAL
                        )) {
                            val verdict = SetupGatePolicy.can(action, f)
                            assertEquals(
                                "$action must be allowed at $stage (runnable=$runnable pkg=$pkg swapping=$swapping)",
                                SetupVerdict.Allowed,
                                verdict
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `the spec-shaped overload never gates C either`() {
        for (stage in stages) {
            for (runnable in listOf(false, true)) {
                assertTrue(
                    SetupGatePolicy.can(SetupAction.RUN_C, stage, runnable).allowed
                )
                assertTrue(
                    SetupGatePolicy.can(SetupAction.EDIT_FILE, stage, runnable).allowed
                )
            }
        }
    }

    // ---- the refusal matrix -------------------------------------------------

    @Test
    fun `install is refused for every stage while the userland is not usable`() {
        for (stage in stages) {
            val verdict = SetupGatePolicy.can(
                SetupAction.INSTALL_PACKAGE,
                facts(stage, percent = if (stage == SetupStage.DOWNLOADING) 41 else null)
            )
            assertTrue("$stage must refuse", verdict is SetupVerdict.Refused)
            assertTrue("$stage refusal needs a sentence", verdict.message!!.length > 20)
        }
    }

    @Test
    fun `the download refusal names the percentage and the do-not-close rule`() {
        val verdict = SetupGatePolicy.can(
            SetupAction.INSTALL_PACKAGE,
            facts(SetupStage.DOWNLOADING, percent = 41)
        )
        assertEquals(
            "CodeC is downloading its Linux tools (41 %). Don't close the app — this happens once.",
            (verdict as SetupVerdict.Refused).message
        )
    }

    @Test
    fun `a download with no known size never invents a percentage`() {
        val message = SetupGatePolicy.can(
            SetupAction.INSTALL_PACKAGE,
            facts(SetupStage.DOWNLOADING, percent = null)
        ).message!!
        assertFalse("no size means no number", message.contains("%"))
        assertTrue(message.contains("Don't close the app"))
    }

    @Test
    fun `unpacking refusing says unpacking`() {
        assertEquals(
            "Unpacking the Linux tools… Don't close the app.",
            SetupGatePolicy.can(
                SetupAction.INSTALL_PACKAGE,
                facts(SetupStage.EXTRACTING)
            ).message
        )
    }

    @Test
    fun `offline names the network and still offers C`() {
        val message = SetupGatePolicy.can(
            SetupAction.INSTALL_PACKAGE,
            facts(SetupStage.FAILED, issue = SetupIssue.OFFLINE)
        ).message!!
        assertTrue(message.contains("network"))
        assertTrue(message.contains("C works offline right now."))
        assertFalse("offline is not a generic failure", message.contains("disk space /"))
    }

    @Test
    fun `no disk space names storage and the retry door`() {
        val message = SetupGatePolicy.can(
            SetupAction.INSTALL_PACKAGE,
            facts(SetupStage.FAILED, issue = SetupIssue.DISK)
        ).message!!
        assertTrue(message.contains("storage"))
        assertTrue(message.contains("Terminal"))
    }

    @Test
    fun `a device with no bootstrap is told the truth, not shown a spinner`() {
        val message = SetupGatePolicy.can(
            SetupAction.INSTALL_PACKAGE,
            facts(SetupStage.UNSUPPORTED)
        ).message!!
        assertEquals(
            "Extra languages aren't available on this device. C works offline.",
            message
        )
    }

    @Test
    fun `an interrupted swap refuses even when the files look fine`() {
        val verdict = SetupGatePolicy.can(
            SetupAction.INSTALL_PACKAGE,
            facts(SetupStage.READY, runnable = true, pkg = true, swapping = true)
        )
        assertTrue(verdict is SetupVerdict.Refused)
        assertTrue(verdict.message!!.contains("repairing"))
    }

    @Test
    fun `a shell-only userland with no pkg gets its own sentence`() {
        val verdict = SetupGatePolicy.can(
            SetupAction.INSTALL_PACKAGE,
            facts(SetupStage.READY, runnable = true, pkg = false)
        )
        assertTrue(verdict is SetupVerdict.Refused)
        assertTrue(verdict.message!!.contains("no package manager"))
    }

    @Test
    fun `a usable prefix is allowed, with a note while an upgrade is in flight`() {
        assertEquals(
            SetupVerdict.Allowed,
            SetupGatePolicy.can(
                SetupAction.INSTALL_PACKAGE,
                facts(SetupStage.READY, runnable = true, pkg = true)
            )
        )
        val upgrading = SetupGatePolicy.can(
            SetupAction.INSTALL_PACKAGE,
            facts(SetupStage.DOWNLOADING, percent = 62, runnable = true, pkg = true)
        )
        assertTrue(upgrading is SetupVerdict.AllowedWithNote)
        assertTrue(upgrading.message!!.contains("62 %"))
        assertTrue(upgrading.message!!.contains("everything still works"))
    }

    @Test
    fun `every refusal names a percentage or a reason and says what to do`() {
        val cases = listOf(
            facts(SetupStage.CHECKING),
            facts(SetupStage.DOWNLOADING, percent = 12),
            facts(SetupStage.DOWNLOADING),
            facts(SetupStage.VERIFYING),
            facts(SetupStage.EXTRACTING),
            facts(SetupStage.FAILED, issue = SetupIssue.OFFLINE),
            facts(SetupStage.FAILED, issue = SetupIssue.DISK),
            facts(SetupStage.FAILED, issue = SetupIssue.BROKEN_USERLAND),
            facts(SetupStage.FAILED, issue = SetupIssue.CORRUPT),
            facts(SetupStage.FAILED),
            facts(SetupStage.UNSUPPORTED),
            facts(SetupStage.READY, runnable = true, pkg = false),
            facts(SetupStage.READY, runnable = true, pkg = true, swapping = true),
            facts(SetupStage.READY)
        )
        for (f in cases) {
            for (action in listOf(SetupAction.INSTALL_PACKAGE, SetupAction.RUN_LANGUAGE)) {
                val verdict = SetupGatePolicy.can(action, f)
                assertTrue("$action at ${f.progress.stage} must refuse", verdict is SetupVerdict.Refused)
                val message = verdict.message!!
                val namesCause = message.contains("%") ||
                    listOf(
                        "network", "storage", "device", "setup", "tools",
                        "package manager", "repairing", "download", "unpacking", "checking"
                    ).any { message.contains(it, ignoreCase = true) }
                assertTrue("'$message' must name a percentage or a reason", namesCause)
                val saysWhatToDo = message.contains("Terminal") ||
                    message.contains("⬇") ||
                    message.contains("Don't close") ||
                    message.contains("C works")
                assertTrue("'$message' must say what to do", saysWhatToDo)
                // No bare "error" without a way out.
                if (message.contains("error", ignoreCase = true)) {
                    assertTrue(saysWhatToDo)
                }
            }
        }
    }

    @Test
    fun `a language refusal always keeps the C promise in the sentence`() {
        for (stage in stages) {
            val message = SetupGatePolicy.can(SetupAction.RUN_LANGUAGE, facts(stage)).message!!
            assertTrue("$stage: '$message'", message.contains("C works"))
        }
    }

    // ---- free-text commands -------------------------------------------------

    @Test
    fun `cc, tcc and a compiled executable are never gated`() {
        val prefix = tmp.newFolder("files").let { File(it, "usr").also { p -> p.mkdirs() } }
        for (command in listOf(
            "cc hello.c -o a.out",
            "tcc -run hello.c",
            "/data/data/com.codeci.ide/files/usr/bin/cc hello.c -o hello",
            "./a.out",
            "./hello"
        )) {
            assertEquals(
                command,
                SetupAction.RUN_C,
                SetupGatePolicy.actionForCommand(command, prefix) { false }
            )
        }
    }

    @Test
    fun `a command that could actually run is not gated, one that cannot is`() {
        val files = tmp.newFolder("files2")
        val prefix = File(files, "usr")
        File(prefix, "bin").mkdirs()
        File(prefix, "bin/git").writeText("#!/bin/sh\n")
        // git is in the prefix; python3 is nowhere.
        assertEquals(
            SetupAction.RUN_C,
            SetupGatePolicy.actionForCommand("git status", prefix) { false }
        )
        assertEquals(
            SetupAction.INSTALL_PACKAGE,
            SetupGatePolicy.actionForCommand("python3 main.py", prefix) { false }
        )
        // The system shell provides ls even with no userland at all.
        assertEquals(
            SetupAction.RUN_C,
            SetupGatePolicy.actionForCommand("ls -la", prefix) { name -> name == "ls" }
        )
        assertEquals(
            SetupAction.INSTALL_PACKAGE,
            SetupGatePolicy.actionForCommand("pkg install -y python", prefix) { name -> name == "ls" }
        )
    }

    // ---- the surfaces -------------------------------------------------------

    @Test
    fun `the bar is silent only when the setup is ready and usable`() {
        assertNull(
            SetupGatePolicy.barText(
                InstallProgress(SetupStage.READY),
                facts(SetupStage.READY, runnable = true, pkg = true)
            )
        )
        for (stage in stages) {
            val f = facts(stage, percent = if (stage == SetupStage.DOWNLOADING) 62 else null)
            val text = SetupGatePolicy.barText(f.progress, f)
            if (stage == SetupStage.READY) continue
            assertNotNull("$stage must show something", text)
            assertTrue(SetupGatePolicy.barVisible(f.progress, f))
        }
        val inFlight = facts(SetupStage.DOWNLOADING, percent = 62)
        assertTrue(SetupGatePolicy.barText(inFlight.progress, inFlight)!!.contains("62 %"))
        assertTrue(SetupGatePolicy.barText(inFlight.progress, inFlight)!!.contains("C works right now"))
    }

    @Test
    fun `the do-not-close bar covers every stage except ready`() {
        for (stage in stages) {
            val text = SetupGatePolicy.dontCloseText(InstallProgress(stage, percent = 7))
            if (stage == SetupStage.READY) {
                assertNull(text)
            } else {
                assertNotNull("$stage needs the warning", text)
            }
        }
        assertEquals(
            "Don't close CodeC — it is finishing a one-time setup (62 %)",
            SetupGatePolicy.dontCloseText(InstallProgress(SetupStage.DOWNLOADING, percent = 62))
        )
    }

    @Test
    fun `the notification carries the percentage while downloading and is quiet when settled`() {
        assertEquals(
            "Downloading CodeC's Linux tools — 62 %",
            SetupGatePolicy.notificationText(InstallProgress(SetupStage.DOWNLOADING, percent = 62))
        )
        assertNull(SetupGatePolicy.notificationText(InstallProgress(SetupStage.READY)))
        assertNull(SetupGatePolicy.notificationText(InstallProgress(SetupStage.UNSUPPORTED)))
        assertNotNull(SetupGatePolicy.notificationText(InstallProgress(SetupStage.EXTRACTING)))
    }

    // ---- the disk truth (Phase 44.2 §3) -------------------------------------

    @Test
    fun `diskFacts derives the stage from the ledger and the files, never optimism`() {
        val files = tmp.newFolder("files3")
        val prefix = File(files, "usr")
        File(prefix, "bin").mkdirs()
        val pkg = File(prefix, "bin/pkg")
        pkg.writeText("#!/system/bin/sh\n")
        pkg.setExecutable(true, false)
        File(prefix, "bin/bash").writeText("ELF")

        val ready = SetupGatePolicy.diskFacts(prefix, SetupPhase.IDLE)
        assertTrue(ready.usable)
        assertEquals(SetupStage.READY, ready.progress.stage)

        val swapping = SetupGatePolicy.diskFacts(prefix, SetupPhase.SWAPPING)
        assertFalse("a pending swap is not usable", swapping.usable)
        assertTrue(swapping.swapping)

        val downloading = SetupGatePolicy.diskFacts(prefix, SetupPhase.DOWNLOADING)
        assertEquals(SetupStage.DOWNLOADING, downloading.progress.stage)

        // No prefix at all: CHECKING, not READY.
        val empty = SetupGatePolicy.diskFacts(File(files, "nothing"), SetupPhase.IDLE)
        assertFalse(empty.usable)
        assertEquals(SetupStage.CHECKING, empty.progress.stage)
        assertFalse(empty.packageManager)
        assertFalse(empty.userlandRunnable)
    }

    @Test
    fun `a shell-only userland reads as ready but not usable`() {
        val files = tmp.newFolder("files4")
        val prefix = File(files, "usr")
        File(prefix, "bin").mkdirs()
        File(prefix, "bin/bash").writeText("ELF")
        val f = SetupGatePolicy.diskFacts(prefix, SetupPhase.DONE)
        assertEquals(SetupStage.READY, f.progress.stage)
        assertTrue(f.userlandRunnable)
        assertFalse(f.packageManager)
        assertFalse("no pkg means no `pkg install`", f.usable)
    }

    // ---- device round 1 (owner report, 2026-09-12) -------------------------
    //
    // "I couldn't not open the terminal it's opening the editor" · "The top a
    // massage 'C works right now. The linux tool need one install- open terminal
    // and tap download' But it's not closing or opening terminal" · "Every
    // package saying view setup but terminal not opening editor opening".

    @Test
    fun `a cold start goes to the terminal whenever the tools are not usable`() {
        // The rule is about the STATE OF THE TOOLS, not about which launch it
        // is: the first implementation diverted only on the launch where the
        // first-run welcome handed over, so an updated install with a broken or
        // unfinished userland still opened the editor — under a bar that said
        // "open Terminal and tap ⬇".
        assertTrue(SetupGatePolicy.startOnTerminal(usable = false, abiSupported = true))
        assertFalse("a working prefix must keep the pre-44 launch tab", SetupGatePolicy.startOnTerminal(usable = true, abiSupported = true))
        // No bootstrap for this ABI (x86_64 emulators): nothing can ever
        // finish, so diverting would strand the user on a tab with no way out.
        assertFalse(SetupGatePolicy.startOnTerminal(usable = false, abiSupported = false))
        assertFalse(SetupGatePolicy.startOnTerminal(usable = true, abiSupported = false))
    }

    @Test
    fun `the bar is dismissible exactly when the setup has settled`() {
        // "it's not closing": in the settled-but-unusable state the old rule
        // (dismissible only when settled AND usable) left a ✕ that cleared a
        // note which was not there — a wall with a sentence on it.
        val inFlight = listOf(
            SetupStage.CHECKING,
            SetupStage.DOWNLOADING,
            SetupStage.VERIFYING,
            SetupStage.EXTRACTING
        )
        for (stage in inFlight) {
            assertFalse(
                "$stage is in flight: hiding it is the bug this phase removes",
                SetupGatePolicy.barDismissAllowed(InstallProgress(stage))
            )
        }
        for (stage in listOf(SetupStage.READY, SetupStage.FAILED, SetupStage.UNSUPPORTED)) {
            assertTrue(
                "$stage is settled and must be dismissible whatever the verdict",
                SetupGatePolicy.barDismissAllowed(InstallProgress(stage))
            )
        }
        // The trap state itself: READY with a prefix the disk does not confirm.
        assertTrue(SetupGatePolicy.barDismissAllowed(InstallProgress(SetupStage.READY)))
    }

    @Test
    fun `the settled-but-unusable bar text is the one the owner quoted and it names the terminal`() {
        val markerOnly = facts(SetupStage.READY, runnable = true, pkg = false)
        assertFalse("no bin/pkg means not usable, whatever the marker says", markerOnly.usable)
        val text = SetupGatePolicy.barText(InstallProgress(SetupStage.READY), markerOnly)
        assertNotNull(text)
        assertTrue(text?.contains("C works right now") == true)
        assertTrue(text?.contains("open Terminal") == true)
        assertTrue(text?.contains("⬇") == true)
        // …and the gate refuses a package transaction with the same honesty.
        val verdict = SetupGatePolicy.can(SetupAction.INSTALL_PACKAGE, markerOnly)
        assertFalse(verdict.allowed)
        assertNotNull(verdict.message)
    }

    @Test
    fun `a working prefix with a stale swapping ledger is unusable but the bar still leads out`() {
        // The other shape of the same trap: the ledger says a swap was in
        // flight, the tree is fine. Not usable (a swap must win), and the bar
        // must say what to do rather than only what is wrong.
        val stale = facts(SetupStage.READY, runnable = true, pkg = true, swapping = true)
        assertFalse(stale.usable)
        val text = SetupGatePolicy.barText(InstallProgress(SetupStage.READY), stale)
        assertNotNull(text)
        val verdict = SetupGatePolicy.can(SetupAction.INSTALL_PACKAGE, stale)
        assertFalse(verdict.allowed)
        assertTrue(verdict.message!!.contains("Terminal"))
    }
}
