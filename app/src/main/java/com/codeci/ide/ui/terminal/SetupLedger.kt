package com.codeci.ide.ui.terminal

/**
 * Phase 44.2 — the install ledger (spec:
 * docs/chat-phase44/PART_44_2_ATOMIC_SETUP.md §1).
 *
 * Symptom being closed: `UserlandInstaller.swapPrefix` performs two renames
 * (`usr` → `usr.old-<ts>`, then `.userland-staging-<ts>` → `usr`). A process
 * kill BETWEEN them leaves **no `usr` at all** plus an orphan `usr.old-*`, and
 * nothing in the app ever repaired or swept either directory: the next launch
 * found no runnable userland, tried a fresh install, and offline answered
 * `"userland: offline — using built-in cc (TCC)"` — which reads like success.
 * C still worked (TCC is in the APK), so the app looked fine until the user
 * tapped INSTALL in Packages and got `pkg: not found`.
 *
 * The ledger makes that window **recorded** instead of silent, which is what
 * lets [SetupRecovery] repair it on boot. Shape copied from
 * `ui/crash/StartupLedger.kt`: a pure decision layer over an injected
 * synchronous [Store] (SharedPreferences, NOT DataStore — the marker must be
 * readable in the first instructions of a process and durable BEFORE the risky
 * step, so the Android edge writes with `commit()`, not `apply()`).
 *
 * Corrupt/unreadable values are treated as [SetupPhase.IDLE]: a ledger that
 * cannot be read must never trap the user in a repair loop.
 */

/** Where the installer was when the process died. */
enum class SetupPhase {
    IDLE,
    DOWNLOADING,
    EXTRACTING,
    SWAPPING,
    DONE
}

/** One durable ledger record. */
data class SetupRecord(
    val phase: SetupPhase,
    val release: String,
    val startedAt: Long
) {
    val inFlight: Boolean
        get() = phase == SetupPhase.DOWNLOADING ||
            phase == SetupPhase.EXTRACTING ||
            phase == SetupPhase.SWAPPING
}

/** What a leftover record MEANS on boot. Pure, so CI can pin the kill matrix. */
sealed interface SetupResume {

    /** Nothing to repair (IDLE / DONE with no orphans). */
    data object Nothing : SetupResume

    /** Killed mid-download: the Range-resume path picks it up. */
    data object RetryDownload : SetupResume

    /** Killed mid-extract: the staging tree is junk, the live prefix is fine. */
    data object Reextract : SetupResume

    /** Killed mid-swap with no `usr`: put the newest `usr.old-*` back. */
    data class RestoreOld(val dir: String) : SetupResume

    /** The prefix is fine; only orphaned directories need sweeping. */
    data class SweepOnly(val orphans: List<String>) : SetupResume
}

class SetupLedger(private val store: Store) {

    /** The synchronous three-cell storage (one read + one write per change). */
    interface Store {
        fun readPhase(): String?
        fun readRelease(): String?
        fun readStartedAt(): Long?

        /** The one write path. Implementations MUST be durable (commit()). */
        fun write(phase: String, release: String, startedAt: Long)

        fun clear()
    }

    /**
     * Records a phase BEFORE the work it names. `commit()` semantics are the
     * whole point: a marker written after the risky step cannot describe a kill
     * that happened during it.
     */
    fun note(phase: SetupPhase, release: String = "", nowMs: Long = System.currentTimeMillis()) {
        val previous = read()
        val startedAt = if (previous.phase == phase && previous.startedAt > 0L) {
            previous.startedAt
        } else {
            nowMs
        }
        runCatching { store.write(phase.name, release, startedAt) }
    }

    /** The current record; unreadable values degrade to IDLE, never to a crash. */
    fun read(): SetupRecord {
        val phaseName = runCatching { store.readPhase() }.getOrNull()
        val phase = phaseName?.let { name ->
            SetupPhase.entries.firstOrNull { it.name == name }
        } ?: SetupPhase.IDLE
        val release = runCatching { store.readRelease() }.getOrNull()?.trim().orEmpty()
        val startedAt = (runCatching { store.readStartedAt() }.getOrNull() ?: 0L)
            .coerceAtLeast(0L)
        return SetupRecord(phase, release, startedAt)
    }

    /** The install finished (or the repair did): the ledger goes quiet. */
    fun clear() {
        runCatching { store.clear() }
    }

    /**
     * Boot-time verdict. Pure: [prefixExists] and [oldDirs] are the caller's
     * observations, so the kill matrix is a table CI pins instead of a field
     * bug report.
     *
     * @param oldDirs names of `usr.old-*` directories found next to the prefix.
     */
    fun resumePlan(
        record: SetupRecord = read(),
        prefixExists: Boolean,
        oldDirs: List<String> = emptyList()
    ): SetupResume {
        val newestOld = SetupRecovery.newestOldPrefix(oldDirs)
        return when (record.phase) {
            SetupPhase.SWAPPING -> when {
                // The owner's exact bug: no `usr`, a `usr.old-*` beside it.
                !prefixExists && newestOld != null -> SetupResume.RestoreOld(newestOld)
                // Nothing to restore from: the download path has to redo it.
                !prefixExists -> SetupResume.RetryDownload
                // The swap actually landed; only the old tree is left over.
                oldDirs.isNotEmpty() -> SetupResume.SweepOnly(oldDirs)
                else -> SetupResume.Nothing
            }
            SetupPhase.EXTRACTING -> SetupResume.Reextract
            SetupPhase.DOWNLOADING -> SetupResume.RetryDownload
            SetupPhase.DONE, SetupPhase.IDLE ->
                if (oldDirs.isNotEmpty()) SetupResume.SweepOnly(oldDirs) else SetupResume.Nothing
        }
    }

    /** The user-facing sentence for a boot repair (null = nothing to say). */
    fun resumeMessage(resume: SetupResume): String? = when (resume) {
        is SetupResume.RestoreOld ->
            "Setup was interrupted; CodeC restored your Linux tools."
        is SetupResume.Reextract ->
            "Finishing setup — unpacking the Linux tools."
        is SetupResume.RetryDownload ->
            "CodeC needs the network once to finish setting up its Linux tools. " +
                "C works offline right now."
        is SetupResume.SweepOnly -> null
        is SetupResume.Nothing -> null
    }
}
