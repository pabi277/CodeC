package com.codeci.ide.ui.modules

/**
 * Phase 51.3 — the install moment.
 *
 * The evidence (2026-09-21, `main` @ `0f1b650`): a package install is the most
 * anxious wait in the app, and it *ends in the terminal*. `ModulesScreen`'s row
 * fires `pkg install -y <id>` at `:385-388`, hands the user to the terminal
 * (`onNavigateToTerminal()`), and the row's state is a `remember(item.id) {
 * checkIsInstalled(...) }` snapshot (`:360`) that cannot change while the screen
 * lives. So a finished install is announced by a line scrolling past in another
 * tab, if it is seen at all.
 *
 * [InstallMoment] is the pure half of the fix: it says what the row *means* at
 * each transition, and — the part that matters — **when a finish is worth
 * marking**. It is deliberately as strict as Phase 44's `SetupLockPolicy`:
 *
 * - a celebration happens only on a real transition **into** `INSTALLED`
 *   ([celebrateOnFinish]): a re-render of an already-installed row is not news,
 *   and neither is upgrading a package that already works;
 * - `INSTALLING` needs the user's own command in flight, not merely "not
 *   installed yet";
 * - failure keeps Phase 44's law — one sentence saying what happened and where
 *   to look, with a retry, never a silent red badge.
 *
 * Pure Kotlin; `InstallMomentTest` pins every branch. The screen owns the
 * haptic ([com.codeci.ide.ui.components.HapticMoment.INSTALL_FINISHED]) and the
 * one-line completion toast.
 */
enum class PkgState { NOT_INSTALLED, INSTALLING, INSTALLED, UPGRADABLE }

data class InstallFacts(
    val state: PkgState = PkgState.NOT_INSTALLED,
    /** The user's own install command is in flight. */
    val running: Boolean = false,
    /** 0-100 when the installer reports it; null when it does not. */
    val progress: Int? = null,
    /** The last attempt failed (the terminal says why). */
    val failed: Boolean = false,
)

/** The label role the row's primary button wears (copy lives in strings.xml). */
enum class InstallLabel { INSTALL, INSTALLING, OPEN, UPDATE, RETRY }

object InstallMoment {

    fun labelFor(f: InstallFacts): InstallLabel = when {
        f.failed && f.state != PkgState.INSTALLED -> InstallLabel.RETRY
        f.state == PkgState.INSTALLED -> InstallLabel.OPEN
        f.state == PkgState.INSTALLING || f.running -> InstallLabel.INSTALLING
        f.state == PkgState.UPGRADABLE -> InstallLabel.UPDATE
        else -> InstallLabel.INSTALL
    }

    /** The progress to show, clamped: a package manager's 0-100 or nothing. */
    fun percentOrNull(f: InstallFacts): Int? = f.progress?.coerceIn(0, 100)

    /**
     * True **only** on a genuine transition into `INSTALLED` — the one moment
     * worth a haptic and a line. A re-render of an installed row is not news
     * (`INSTALLED → INSTALLED`), and neither is upgrading a package that
     * already works (`UPGRADABLE → INSTALLED`: the user had a working tool
     * before and after). Only "there was nothing, now there is something"
     * earns the celebration.
     */
    fun celebrateOnFinish(previous: PkgState, now: PkgState): Boolean =
        now == PkgState.INSTALLED &&
            (previous == PkgState.NOT_INSTALLED || previous == PkgState.INSTALLING)

    /**
     * The failure sentence's *kind* (the screen resolves the copy): the row
     * must say where to look, which is the terminal, exactly as Phase 44 does
     * for the userland. Null = nothing failed.
     */
    fun failureKind(f: InstallFacts): InstallFailure? =
        if (f.failed && f.state != PkgState.INSTALLED) InstallFailure.RETRY_IN_TERMINAL else null
}

enum class InstallFailure { RETRY_IN_TERMINAL }
