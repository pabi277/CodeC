package com.codeci.ide.ui.terminal

/**
 * Phase 44.1 — the one observable truth about CodeC's one-time userland setup
 * (spec: docs/chat-phase44/PART_44_1_VISIBLE_SETUP.md §1).
 *
 * Symptom being closed: the bootstrap download starts the moment the app opens
 * (`TerminalViewModel.init` → `startInternal` → `installUserlandInternal`) and
 * its ONLY output was text painted into the terminal emulator's grid
 * (`TerminalSession.notice`). A tester on the Projects/Editor/Packages tab
 * therefore never learned that a 200 MB one-time download was running, swiped
 * the app away, and every later `pkg install` failed with `pkg: not found`.
 *
 * Design — deliberately Android-free:
 *  - [SetupStage] + [InstallProgress] are the structured form of the installer's
 *    EXISTING progress lines. [SetupGatePolicy.parse] is the only reader of
 *    those strings, so the installer's contract (`onProgress: (String) -> Unit`)
 *    is untouched and the parser is host-testable against the real lines.
 *  - [SetupGatePolicy.can] is the single place "what may the user do right now"
 *    is answered; every surface (setup bar, terminal bar, Packages tab, editor
 *    install prompt) renders the SAME sentence from the SAME function.
 *  - The gate law (pinned by SetupGatePolicyTest): **C is never gated.**
 *    `RUN_C` and `EDIT_FILE` are allowed in every stage — TCC lives in the APK
 *    and Phase 21 made `.c` install-gate-free permanently. `OPEN_TERMINAL` is
 *    allowed in every stage too: the terminal is where the user watches (and
 *    retries) the setup, so refusing it would trap them.
 *  - [SetupTracker] owns the state machine so no busy flag can survive a
 *    terminal event (the "spinner forever" bug class), and [SetupAnnouncer]
 *    bounds how often the notification may be rewritten.
 */

/** The user-visible stage of the one-time setup. */
enum class SetupStage {
    /** Probing markers / release sidecars; nothing is on screen to measure yet. */
    CHECKING,

    /** The bootstrap archive is downloading (percentage known when the server answered a length). */
    DOWNLOADING,

    /** The download finished; the SHA-256 sidecar is being checked. */
    VERIFYING,

    /** The verified archive is being unpacked into the staging directory. */
    EXTRACTING,

    /** A usable Linux userland is in place (or was already in place). */
    READY,

    /** The setup stopped short: offline, out of disk, broken bootstrap, … */
    FAILED,

    /** This device has no bootstrap at all (ABI without a published release). */
    UNSUPPORTED
}

/** Why a [SetupStage.FAILED] happened — the message names it, never a bare "error". */
enum class SetupIssue {
    OFFLINE,
    DISK,
    BROKEN_USERLAND,

    /** The SHA-256 gate rejected the download (corrupt or truncated). */
    CORRUPT,
    UNKNOWN
}

/**
 * One structured setup observation. [percent] is null whenever the size is
 * unknown — a guessed percentage is worse than none, so nothing fabricates one.
 */
data class InstallProgress(
    val stage: SetupStage,
    val percent: Int? = null,
    val bytes: Long? = null,
    val detail: String = "",
    val issue: SetupIssue? = null
) {
    /** True while the setup is doing something the user must not interrupt. */
    val inFlight: Boolean
        get() = stage == SetupStage.CHECKING ||
            stage == SetupStage.DOWNLOADING ||
            stage == SetupStage.VERIFYING ||
            stage == SetupStage.EXTRACTING

    /** True when the setup is over, whichever way it ended. */
    val settled: Boolean get() = !inFlight

    fun withStage(next: SetupStage): InstallProgress =
        copy(stage = next, issue = if (next == SetupStage.FAILED) issue else null)
}

/** What the user is trying to do when the gate is consulted. */
enum class SetupAction {
    /** Compile/run a `.c` file with the APK's built-in TCC — never gated. */
    RUN_C,

    /** Run a language that lives in the downloaded userland (Python, Node, …). */
    RUN_LANGUAGE,

    /** `pkg install …` from the Packages tab, a Quick Action, or a custom command. */
    INSTALL_PACKAGE,

    /** Open the Terminal tab — never gated (it is where setup is watched/retried). */
    OPEN_TERMINAL,

    /** Type in / save a file — never gated. */
    EDIT_FILE
}

/** The gate's answer. Every non-`Allowed` verdict carries ONE honest sentence. */
sealed interface SetupVerdict {

    /** The sentence to show, or null when there is nothing to explain. */
    val message: String?

    /** True for [Allowed] and [AllowedWithNote]; false only for [Refused]. */
    val allowed: Boolean

    data object Allowed : SetupVerdict {
        override val message: String? = null
        override val allowed: Boolean = true
    }

    /** Usable, but the setup is moving underneath it (an upgrade in flight). */
    data class AllowedWithNote(override val message: String) : SetupVerdict {
        override val allowed: Boolean = true
    }

    /** Not usable: the sentence says what is happening and what to do. */
    data class Refused(override val message: String) : SetupVerdict {
        override val allowed: Boolean = false
    }
}

/**
 * What the filesystem says, independent of the stage. File-based on purpose:
 * the release marker is written AFTER the swap (`UserlandInstaller.writeMarkers`)
 * and a marker-only test would pass on a half tree, while an exec probe
 * (`ShellEnvironment.launchDiagnostic`) spawns a process — too heavy for a
 * per-frame gate.
 */
data class SetupFacts(
    val progress: InstallProgress = InstallProgress(SetupStage.CHECKING),
    /** `bin/bash` or `bin/busybox` exists and is executable. */
    val userlandRunnable: Boolean = false,
    /** `bin/pkg` exists, is executable and is not empty. */
    val packageManager: Boolean = false,
    /** The setup ledger says a swap was interrupted and not yet repaired. */
    val swapping: Boolean = false
) {
    /** The package manager is what every `pkg` row actually needs. */
    val usable: Boolean get() = packageManager && userlandRunnable && !swapping
}

/**
 * The one place "what may the user do right now" is answered, plus the only
 * reader of the installer's progress strings.
 */
object SetupGatePolicy {

    /** The installer's own line prefix (`onProgress("userland: …")`). */
    const val LINE_PREFIX = "userland:"

    /** "… — cc (TCC) still works offline", appended to every failure line. */
    private val TRAILING_CC_CLAUSE =
        Regex("""\s*[—–-]\s*cc \(TCC\) still works offline\s*$""", RegexOption.IGNORE_CASE)

    private val DOWNLOAD_PERCENT = Regex("""download\s+(\d{1,3})\s*%""", RegexOption.IGNORE_CASE)
    private val DOWNLOAD_BYTES = Regex("""\((\d+)\s*bytes?\)""", RegexOption.IGNORE_CASE)

    /**
     * Turns one EXISTING installer progress line into structure. Never throws,
     * never invents a percentage: an unrecognised line stays [SetupStage.CHECKING]
     * with the raw text as [InstallProgress.detail].
     */
    fun parse(progressLine: String): InstallProgress {
        val raw = progressLine.trim()
        val body = if (raw.startsWith(LINE_PREFIX, ignoreCase = true)) {
            raw.substring(LINE_PREFIX.length).trim()
        } else {
            raw
        }
        // The installer appends "— cc (TCC) still works offline" to every
        // failure line. Stripped before classification: it contains the word
        // "offline", which would otherwise report a SHA-256 mismatch as a
        // network problem.
        val trimmed = TRAILING_CC_CLAUSE.replace(body, "").trim()
        val text = trimmed.lowercase()

        // A working userland already in place — these lines all mean READY, and
        // they must be matched BEFORE the offline wording below because two of
        // them contain "offline" as well ("offline — using installed userland").
        if (text.startsWith("ready") ||
            text.contains("marker valid") ||
            text.contains("using installed userland") ||
            text.contains("keeping installed userland") ||
            text.startsWith("already installed")
        ) {
            return InstallProgress(SetupStage.READY, detail = body)
        }

        // "installed, but no shell can start: …" — a tree that is there and dead.
        if (text.contains("no shell can start") || text.contains("cannot start")) {
            return InstallProgress(
                SetupStage.FAILED,
                detail = body,
                issue = SetupIssue.BROKEN_USERLAND
            )
        }

        if (text.contains("no bootstrap for this abi") || text.contains("no release yet")) {
            return InstallProgress(SetupStage.UNSUPPORTED, detail = body)
        }

        // Download progress: the installer emits one line per 16 KiB chunk.
        DOWNLOAD_PERCENT.find(text)?.let { match ->
            val percent = match.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
            val bytes = DOWNLOAD_BYTES.find(text)?.groupValues?.get(1)?.toLongOrNull()
            return InstallProgress(
                stage = SetupStage.DOWNLOADING,
                percent = percent,
                bytes = bytes,
                detail = body
            )
        }
        if (text.startsWith("downloading")) {
            return InstallProgress(SetupStage.DOWNLOADING, detail = body)
        }
        if (text.contains("verifying sha-256") || text.contains("verifying")) {
            return InstallProgress(SetupStage.VERIFYING, detail = body)
        }
        if (text.contains("extracting") || text.contains("unpacking")) {
            return InstallProgress(SetupStage.EXTRACTING, detail = body)
        }

        if (text.contains("insufficient disk space") || text.contains("not enough") ||
            text.contains("no space left")
        ) {
            return InstallProgress(SetupStage.FAILED, detail = body, issue = SetupIssue.DISK)
        }
        if (text.contains("sha-256 mismatch") || text.contains("checksum") ||
            text.contains("unexpected eof")
        ) {
            return InstallProgress(SetupStage.FAILED, detail = body, issue = SetupIssue.CORRUPT)
        }
        if (text.contains("offline") || text.contains("network") || text.contains("unreachable")) {
            return InstallProgress(SetupStage.FAILED, detail = body, issue = SetupIssue.OFFLINE)
        }
        if (text.startsWith("failed") || text.contains("cannot ") || text.contains("http ")
        ) {
            return InstallProgress(SetupStage.FAILED, detail = body, issue = SetupIssue.UNKNOWN)
        }

        // Checking markers, probing the release sidecar, announcing an upgrade,
        // falling back to the older bootstrap — all "still working, no number".
        return InstallProgress(SetupStage.CHECKING, detail = body)
    }

    /**
     * True when [line] is one of the installer's setup lines at all. The
     * terminal paints other notices (`[terminal] shell restarted`); those must
     * never knock a settled setup back to CHECKING.
     */
    fun isSetupLine(line: String): Boolean =
        line.trim().startsWith(LINE_PREFIX, ignoreCase = true)

    // ---- the gate -----------------------------------------------------------

    /** Spec-shaped convenience: stage + the one filesystem fact that matters. */
    fun can(
        action: SetupAction,
        stage: SetupStage,
        userlandRunnable: Boolean
    ): SetupVerdict = can(
        action,
        SetupFacts(
            progress = InstallProgress(stage),
            userlandRunnable = userlandRunnable,
            packageManager = userlandRunnable
        )
    )

    /**
     * The refusal matrix. `RUN_C`, `EDIT_FILE` and `OPEN_TERMINAL` are allowed
     * in EVERY stage; everything else is refused while the userland is not
     * usable, with a sentence that names a percentage or a reason AND what to
     * do next.
     */
    fun can(action: SetupAction, facts: SetupFacts): SetupVerdict {
        if (action == SetupAction.RUN_C ||
            action == SetupAction.EDIT_FILE ||
            action == SetupAction.OPEN_TERMINAL
        ) {
            return SetupVerdict.Allowed
        }
        val progress = facts.progress
        if (facts.usable) {
            return if (progress.inFlight && progress.stage != SetupStage.CHECKING) {
                SetupVerdict.AllowedWithNote(inFlightNote(progress))
            } else {
                SetupVerdict.Allowed
            }
        }
        return SetupVerdict.Refused(refusal(action, progress, facts))
    }

    /** The one honest sentence for "you cannot do that yet". */
    fun refusal(action: SetupAction, progress: InstallProgress, facts: SetupFacts): String {
        val base = when {
            facts.swapping ->
                "CodeC is repairing its Linux tools after an interrupted setup. " +
                    "Open the Terminal tab and tap ⬇ to finish."
            progress.stage == SetupStage.UNSUPPORTED ->
                "Extra languages aren't available on this device. C works offline."
            progress.stage == SetupStage.DOWNLOADING ->
                if (progress.percent != null) {
                    "CodeC is downloading its Linux tools (${progress.percent} %). " +
                        "Don't close the app — this happens once."
                } else {
                    "CodeC is downloading its Linux tools. Don't close the app — this happens once."
                }
            progress.stage == SetupStage.VERIFYING ->
                "CodeC is checking the download. Don't close the app — this happens once."
            progress.stage == SetupStage.EXTRACTING ->
                "Unpacking the Linux tools… Don't close the app."
            progress.stage == SetupStage.FAILED -> when (progress.issue) {
                SetupIssue.OFFLINE ->
                    "CodeC needs the network once to finish setting up its Linux tools. " +
                        "C works offline right now."
                SetupIssue.DISK ->
                    "Setup didn't finish — not enough storage for the Linux tools. " +
                        "Free some space, then open the Terminal tab and tap ⬇."
                SetupIssue.BROKEN_USERLAND ->
                    "Setup didn't finish — the Linux tools can't start on this device. " +
                        "Open the Terminal tab and tap ⬇ to install them again."
                SetupIssue.CORRUPT ->
                    "The download didn't verify (corrupt or truncated), so CodeC discarded it. " +
                        "Open the Terminal tab and tap ⬇ to fetch it again."
                else ->
                    "Setup didn't finish (disk space / network). " +
                        "Open the Terminal tab and tap ⬇ to try again."
            }
            // READY or CHECKING without a package manager: either the shell-only
            // fallback userland is installed (no `pkg` at all) or the very first
            // probe has not answered yet.
            progress.stage == SetupStage.READY && facts.userlandRunnable && !facts.packageManager ->
                "These CodeC tools have no package manager yet. " +
                    "Open the Terminal tab and tap ⬇ to install the full set."
            progress.stage == SetupStage.CHECKING ->
                "CodeC is checking its Linux tools. Don't close the app — this happens once."
            else ->
                "The Linux tools aren't ready yet. " +
                    "Open the Terminal tab and tap ⬇ to install them."
        }
        if (action == SetupAction.RUN_LANGUAGE && !base.contains("C works")) {
            return "$base C works offline right now."
        }
        return base
    }

    /** A working prefix that is being upgraded: allowed, but say so. */
    fun inFlightNote(progress: InstallProgress): String = when (progress.stage) {
        SetupStage.DOWNLOADING ->
            if (progress.percent != null) {
                "Updating CodeC's Linux tools (${progress.percent} %) — everything still works."
            } else {
                "Updating CodeC's Linux tools — everything still works."
            }
        SetupStage.VERIFYING -> "Checking the CodeC tools update — everything still works."
        SetupStage.EXTRACTING -> "Unpacking the CodeC tools update — everything still works."
        else -> "Setting up CodeC's Linux tools — everything still works."
    }

    // ---- the surfaces (same vocabulary everywhere) --------------------------

    /**
     * The slim bar under the safe-mode banner, visible from EVERY tab.
     * `null` when there is nothing to say (READY and usable).
     */
    fun barText(progress: InstallProgress, facts: SetupFacts): String? = when {
        progress.stage == SetupStage.READY && facts.usable -> null
        progress.stage == SetupStage.READY ->
            "C works right now · the Linux tools still need one install — open Terminal and tap ⬇"
        progress.stage == SetupStage.UNSUPPORTED ->
            "C works offline · extra languages aren't available on this device"
        progress.stage == SetupStage.FAILED -> refusal(SetupAction.INSTALL_PACKAGE, progress, facts)
        progress.stage == SetupStage.DOWNLOADING ->
            if (progress.percent != null) {
                "Setting up CodeC's Linux tools — ${progress.percent} % · C works right now"
            } else {
                "Setting up CodeC's Linux tools · C works right now"
            }
        progress.stage == SetupStage.VERIFYING ->
            "Checking the downloaded Linux tools · C works right now"
        progress.stage == SetupStage.EXTRACTING ->
            "Unpacking CodeC's Linux tools · C works right now"
        else -> "Setting up CodeC · C works right now"
    }

    /** True while the bar should be on screen (the caller may add a dismiss ✕). */
    fun barVisible(progress: InstallProgress, facts: SetupFacts): Boolean =
        barText(progress, facts) != null

    /**
     * The terminal's own "don't close" warning — the owner's idea, kept almost
     * verbatim. No dismiss: closing it would not stop the download and hiding
     * the information is the bug this phase removes.
     */
    fun dontCloseText(progress: InstallProgress): String? = when (progress.stage) {
        SetupStage.DOWNLOADING ->
            if (progress.percent != null) {
                "Don't close CodeC — it is finishing a one-time setup (${progress.percent} %)"
            } else {
                "Don't close CodeC — it is finishing a one-time setup"
            }
        SetupStage.VERIFYING -> "Don't close CodeC — it is checking the one-time download"
        SetupStage.EXTRACTING -> "Don't close CodeC — it is unpacking the Linux tools"
        SetupStage.CHECKING -> "Don't close CodeC — it is finishing a one-time setup"
        SetupStage.FAILED -> "Setup didn't finish — tap ⬇ in the toolbar to try again"
        SetupStage.UNSUPPORTED -> "No Linux tools for this device — C still works offline"
        SetupStage.READY -> null
    }

    /**
     * The foreground-service notification text while the setup owns the
     * service; `null` restores the plain "terminal running" copy.
     */
    fun notificationText(progress: InstallProgress): String? = when (progress.stage) {
        SetupStage.DOWNLOADING ->
            if (progress.percent != null) {
                "Downloading CodeC's Linux tools — ${progress.percent} %"
            } else {
                "Downloading CodeC's Linux tools"
            }
        SetupStage.VERIFYING -> "Checking CodeC's downloaded tools"
        SetupStage.EXTRACTING -> "Unpacking CodeC's Linux tools"
        SetupStage.CHECKING -> "Setting up CodeC's Linux tools"
        SetupStage.FAILED -> "CodeC setup didn't finish — open the app to retry"
        SetupStage.UNSUPPORTED -> null
        SetupStage.READY -> null
    }

    // ---- free-text commands -------------------------------------------------

    /** The APK's own C frontend: it needs no userland at all (Phase 21, permanent). */
    private val C_FRONTENDS = setOf("cc", "tcc", "codec-cc")

    /**
     * Which gate a free-text terminal command needs (the Packages tab's custom
     * runner and its Quick Actions).
     *
     * The rule is *capability, not optimism* in both directions: CodeC's own
     * `cc` (TCC lives in the APK), a path to a compiled executable, and any
     * binary that actually exists — in the userland's `bin/` or in Android's
     * own `/system/bin` — are never gated, because refusing a command that
     * would have worked is its own lie. Everything else needs `bin/pkg`, so it
     * gets the setup sentence instead of `something: not found`.
     */
    fun actionForCommand(
        command: String,
        prefixDir: java.io.File,
        systemBinaryPresent: (String) -> Boolean = { name ->
            java.io.File("/system/bin", name).canExecute()
        }
    ): SetupAction {
        val token = command.trim().substringBefore(' ').substringBefore('\n')
        if (token.isEmpty()) return SetupAction.RUN_C
        if (token.startsWith("./") || token.startsWith("/")) return SetupAction.RUN_C
        val base = token.substringAfterLast('/')
        if (base.lowercase() in C_FRONTENDS) return SetupAction.RUN_C
        val inPrefix = java.io.File(java.io.File(prefixDir, "bin"), base).isFile
        if (inPrefix || systemBinaryPresent(base)) return SetupAction.RUN_C
        return SetupAction.INSTALL_PACKAGE
    }

    // ---- the filesystem truth (Phase 44.2 §3) -------------------------------

    /**
     * Capability, not optimism: the ACTUAL `bin/pkg` decides, because the
     * release marker is written after the swap and would pass on a half tree.
     * A ledger stuck in `SWAPPING` means the prefix may be mid-rename, so it is
     * not usable until the boot repair has answered.
     */
    fun userlandUsable(prefixDir: java.io.File, ledgerPhase: SetupPhase): Boolean =
        ledgerPhase != SetupPhase.SWAPPING &&
            packageManagerPresent(prefixDir) &&
            shellPresent(prefixDir)

    /** `bin/pkg` exists, is a non-empty file, and is executable. */
    fun packageManagerPresent(prefixDir: java.io.File): Boolean {
        val pkg = java.io.File(java.io.File(prefixDir, "bin"), "pkg")
        return pkg.isFile && pkg.length() > 0L && pkg.canExecute()
    }

    /** A shell binary is present (no exec probe: this runs on the UI path). */
    fun shellPresent(prefixDir: java.io.File): Boolean {
        val bin = java.io.File(prefixDir, "bin")
        val bash = java.io.File(bin, "bash")
        if (bash.isFile && bash.length() > 0L) return true
        val busybox = java.io.File(bin, "busybox")
        return busybox.isFile && busybox.length() > 0L
    }

    /**
     * Facts for a surface with NO live progress feed (the editor's install
     * prompt): the stage is derived from the ledger and the files. Never
     * optimistic — an unrecorded, unusable prefix reads as CHECKING, which
     * refuses with "don't close the app", not as READY.
     */
    fun diskFacts(prefixDir: java.io.File, ledgerPhase: SetupPhase): SetupFacts {
        val runnable = shellPresent(prefixDir)
        val pkg = packageManagerPresent(prefixDir)
        val stage = when {
            ledgerPhase == SetupPhase.DOWNLOADING -> SetupStage.DOWNLOADING
            ledgerPhase == SetupPhase.EXTRACTING -> SetupStage.EXTRACTING
            ledgerPhase == SetupPhase.SWAPPING -> SetupStage.CHECKING
            runnable -> SetupStage.READY
            else -> SetupStage.CHECKING
        }
        return SetupFacts(
            progress = InstallProgress(stage),
            userlandRunnable = runnable,
            packageManager = pkg,
            swapping = ledgerPhase == SetupPhase.SWAPPING
        )
    }

    /** The cheap facts a ViewModel can refresh without spawning a process. */
    fun factsFor(prefixDir: java.io.File, ledgerPhase: SetupPhase, stage: SetupStage): SetupFacts {
        val progress = InstallProgress(stage)
        return factsFor(prefixDir, ledgerPhase, progress)
    }

    fun factsFor(
        prefixDir: java.io.File,
        ledgerPhase: SetupPhase,
        progress: InstallProgress
    ): SetupFacts = SetupFacts(
        progress = progress,
        userlandRunnable = shellPresent(prefixDir),
        packageManager = packageManagerPresent(prefixDir),
        swapping = ledgerPhase == SetupPhase.SWAPPING
    )
}

/**
 * The setup state machine. One instance per process, fed by the installer's
 * progress lines; the terminal event always wins, so no busy flag can survive
 * (the "spinner forever" bug class the spec pins).
 */
class SetupTracker(initial: InstallProgress = InstallProgress(SetupStage.CHECKING)) {

    @Volatile
    private var current: InstallProgress = initial

    val state: InstallProgress get() = current

    /** Feeds one installer line; returns the new state (unchanged for non-setup lines). */
    fun observe(line: String): InstallProgress {
        if (!SetupGatePolicy.isSetupLine(line)) return current
        return note(SetupGatePolicy.parse(line))
    }

    /** Feeds structured progress. A settled stage is never overwritten by CHECKING. */
    fun note(progress: InstallProgress): InstallProgress {
        val previous = current
        val next = if (previous.settled &&
            progress.stage == SetupStage.CHECKING &&
            progress.detail.isEmpty()
        ) {
            // A bare re-check after a settled result carries no new information.
            previous
        } else {
            progress
        }
        current = next
        return next
    }

    /** The installer threw: the setup is over and failed, whatever was on screen. */
    fun fail(message: String, issue: SetupIssue = SetupIssue.UNKNOWN): InstallProgress {
        val next = InstallProgress(
            stage = SetupStage.FAILED,
            percent = null,
            bytes = null,
            detail = message,
            issue = issue
        )
        current = next
        return next
    }

    /** The installer returned a usable prefix (or it was already installed). */
    fun ready(detail: String = ""): InstallProgress {
        val next = InstallProgress(SetupStage.READY, detail = detail)
        current = next
        return next
    }

    /** No bootstrap exists for this device: settled, and not a failure. */
    fun unsupported(detail: String = ""): InstallProgress {
        val next = InstallProgress(SetupStage.UNSUPPORTED, detail = detail)
        current = next
        return next
    }

    /** A deliberate new attempt (toolbar ⬇ / a repair pass) starts from CHECKING. */
    fun restart(detail: String = ""): InstallProgress {
        val next = InstallProgress(SetupStage.CHECKING, detail = detail)
        current = next
        return next
    }
}

/**
 * Bounds how often a surface may be rewritten. The installer emits one line per
 * 16 KiB; the notification is allowed to change at most once per
 * [MIN_PERCENT_STEP] % or [MIN_INTERVAL_MS] ms (the spec's "5 % or 2 s"), and
 * every stage change is announced immediately.
 */
object SetupAnnouncer {

    const val MIN_PERCENT_STEP = 5
    const val MIN_INTERVAL_MS = 2_000L

    /**
     * @param published the last announced progress (null = nothing announced yet)
     * @param candidate the newest observed progress
     * @param nowMs wall clock
     * @param lastPublishedMs when [published] was announced
     */
    fun shouldPublish(
        published: InstallProgress?,
        candidate: InstallProgress,
        nowMs: Long,
        lastPublishedMs: Long
    ): Boolean {
        if (published == null) return true
        if (published.stage != candidate.stage) return true
        // Terminal states are announced once and then stop chattering.
        if (candidate.settled) return published.detail != candidate.detail
        val lastPercent = published.percent ?: -1
        val nextPercent = candidate.percent ?: -1
        if (nextPercent - lastPercent >= MIN_PERCENT_STEP) return true
        if (nextPercent > 0 && nextPercent == 100) return true
        return nowMs - lastPublishedMs >= MIN_INTERVAL_MS && nextPercent != lastPercent
    }

    /**
     * The in-app StateFlow throttle: publish when the STAGE or the integer
     * PERCENT changed (≤ ~100 recompositions for a whole download), never per
     * 16 KiB chunk.
     */
    fun shouldPublishState(published: InstallProgress?, candidate: InstallProgress): Boolean {
        if (published == null) return true
        if (published.stage != candidate.stage) return true
        if (published.percent != candidate.percent) return true
        return published.settled && published.detail != candidate.detail
    }
}
