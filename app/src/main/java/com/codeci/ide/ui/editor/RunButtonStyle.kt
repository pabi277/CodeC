package com.codeci.ide.ui.editor

/**
 * Phase 51.2 — RUN ▶ stops looking like every other button.
 *
 * The evidence (2026-09-21, `main` @ `0f1b650`): the editor's primary action is
 * a bare `Row` with `.clip(...).clickable(onClick = onRunTap)` and a green glyph
 * + label (`EditorScreen.kt:1563-1631`), one control among ☰, 🔍, ⋮ and Test ▷.
 * Google's Material eye-tracking study (dossier §3.2) measured the mechanism
 * that fixes exactly this: a bigger, better-contained primary action is found
 * **up to 4× faster** — and its own counter-example said the *label* must stay
 * ("removing text labels … resulted in decreased usability").
 *
 * **Phase 57.1 re-scopes the look, and the reference wins.** The owner's seven
 * phone shots (`docs/spck-ui` 122157 / 124105) are the accepted UI, and their
 * top row carries a bare green ▶: no container, no word. So the container pair
 * and its tone enum are gone from this file — a policy that describes a
 * control the app no longer draws is worse than no policy. What survives is
 * everything the eye-tracking study could not decide and the device must
 * still honour:
 *
 *  - **Phase 44's lock wins over every other fact.** A locked tap stays
 *    meaningful (it says why, `SetupLockPolicy`); nothing open stays inert.
 *  - **The state has an owner.** While a job this control started is running,
 *    the glyph itself answers "did my tap work?" — the one state change the
 *    shots cannot show, because nothing was running in them.
 *  - **The word is not lost, it moves.** `contentDescription` keeps "Run" /
 *    "Running" for TalkBack; the pixels drop it because the reference does.
 *
 * The policy is pure: a state in, a role + a tone out. The Android half is the
 * one `IconButton` in `EditorScreen.kt`, so the decision is provable without a
 * device and the device only has to prove the *look* (PART_57_1 row F5).
 */
data class RunButtonState(
    val running: Boolean = false,
    val locked: Boolean = false,
    val hasOpenFile: Boolean = true,
)

/**
 * Which visual role the RUN control wears.
 *
 * - [HERO] — idle and runnable: the reference's bare green ▶, the surface's
 *   primary action.
 * - [LOCKED] — the Phase 44/45 chrome lock is up: still visible, still
 *   tappable (it says why), never the green weight.
 * - [QUIET] — nothing to run: on-surface, inert, not hidden.
 */
enum class RunButtonRole { HERO, LOCKED, QUIET }

object RunButtonStyle {

    /** The role, with the lock winning over every other fact. */
    fun roleFor(s: RunButtonState): RunButtonRole = when {
        s.locked -> RunButtonRole.LOCKED
        !s.hasOpenFile -> RunButtonRole.QUIET
        else -> RunButtonRole.HERO
    }

    /**
     * True while a job the control started is still producing output. The
     * screen paints the state where the word used to be (a small progress
     * glyph in the same green) so the surface answers "did my tap work?"
     * without the user watching the panel.
     */
    fun showsRunning(s: RunButtonState): Boolean = s.running && !s.locked

    /**
     * True when a tap does something (or says why it cannot): the lock's own
     * `showChromeLock` makes a locked tap meaningful, so only the
     * nothing-open case is inert.
     */
    fun isEnabled(s: RunButtonState): Boolean = s.locked || s.hasOpenFile

    /**
     * True when the glyph is the surface's hero — the one case that paints the
     * reference's run green. Phase 40.5's law survives in spirit (the accent is
     * a *role*: `RunGreen` is the app's established run colour, not a literal
     * typed at a call site), and a locked or nothing-to-run control falls back
     * to the theme's `onSurfaceVariant`.
     */
    fun isPrimary(s: RunButtonState): Boolean = roleFor(s) == RunButtonRole.HERO
}
