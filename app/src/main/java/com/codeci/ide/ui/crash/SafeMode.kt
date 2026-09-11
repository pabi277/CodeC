package com.codeci.ide.ui.crash

/**
 * Phase 42.3 — safe mode: the reduced startup the crash-loop guard offers
 * after two interrupted starts in a row (PART_42_3 §2).
 *
 * What safe mode IS (the whole mechanism): a process-lifetime flag the
 * launcher consults when applying PERSISTED inputs that a startup crash
 * could plausibly come from — the theme/accent values that feed parsers
 * (a garbage accent string reaching `AccentPalette`), and the saved
 * "open where I left off" editor session (a half-written project file is
 * exactly the kind of culprit the spec names). With [active] set those
 * inputs resolve to their DEFAULTS for the rest of this session.
 *
 * What safe mode is NOT / its laws:
 *  - it is NOT a different app: projects and files are fully readable, and
 *    the export row stays reachable — "get my code out" must work in the
 *    worst state the app can be in;
 *  - it writes NOTHING back: no setting is reset, nothing is deleted, the
 *    persisted values are only bypassed for the session (asserted — the
 *    only write in the whole guard is [StartupLedger.noteLaunch]'s marker
 *    and [StartupLedger.noteStartupFinished]'s clear). "Repaired by
 *    deleting your settings" is what these guards do when nobody checks;
 *  - it is NOT triggered by any one-off error — only two consecutive
 *    startup failures, and the user picks the reduced start on the loop
 *    overlay; a successful start afterwards clears the counter and the
 *    next launch is normal again;
 *  - the session flag never survives the process: a later normal launch
 *    starts with settings intact.
 */
object SafeMode {

    /** Process-lifetime session flag (never persisted). Tests reset it. */
    @Volatile
    var active: Boolean = false
        internal set

    /** The loop overlay's [Try starting without my settings] taps this. */
    fun activateForSession() {
        active = true
    }

    /**
     * The one decision every bypassed input flows through: persisted value
     * when normal, DEFAULT when safe — for this session only.
     */
    fun <T> resolve(defaultValue: T, persisted: T): T =
        if (active) defaultValue else persisted
}
