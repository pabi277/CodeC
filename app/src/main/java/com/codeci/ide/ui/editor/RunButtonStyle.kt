package com.codeci.ide.ui.editor

/**
 * Phase 51.2 — RUN ▶ stops looking like every other button.
 *
 * The evidence (2026-09-21, `main` @ `0f1b650`): the editor's primary action is
 * a bare `Row` with `.clip(...).clickable(onClick = onRunTap)` and a green glyph
 * + label (`EditorScreen.kt:1563-1631`), one control among ☰, 🔍, ⋮ and Test ▷.
 * Google's Material eye-tracking study (dossier §3.2) measured the mechanism
 * that fixes exactly this: a bigger, better-contained primary action is found
 * **up to 4× faster** — and its own counter-example says the *label* must stay
 * ("removing text labels … resulted in decreased usability"), which is why RUN
 * keeps its word.
 *
 * The policy is pure: a state in, a role + a visual out. The Android half is
 * two branches in `EditorScreen.kt`, so the decision is provable without a
 * device and the device only has to prove the *look* (row F5).
 *
 * **Phase 44's law is preserved by the first branch**: when the chrome lock is
 * up, the lock wins over everything else — a running job, a missing file, even
 * a perfectly runnable buffer. The lock's own sentence stays the snackbar's job
 * (`SetupLockPolicy.message`), never this file's.
 */
data class RunButtonState(
    val running: Boolean = false,
    val locked: Boolean = false,
    val hasOpenFile: Boolean = true,
)

/**
 * Which visual role the RUN control wears.
 *
 * - [CONTAINED] — fillable, the hero: idle and runnable.
 * - [TONAL_LOCKED] — the Phase 44/45 chrome lock is up: still visible (it says
 *   why when tapped), never the primary weight.
 * - [TONAL_DISABLED] — nothing to run: tonally quiet, not hidden.
 */
enum class RunButtonRole { CONTAINED, TONAL_LOCKED, TONAL_DISABLED }

/** The colour pair the control paints with — brand roles, never a hex. */
enum class RunButtonTone { PRIMARY_CONTAINER, SURFACE_VARIANT }

/** The whole visual decision, in one value the screen can paint directly. */
data class RunButtonVisual(
    val role: RunButtonRole,
    val tone: RunButtonTone,
    /** The label's state: RUN → RUNNING while a job this button started runs. */
    val showsRunning: Boolean,
    /** True when the control is the surface's primary action. */
    val primary: Boolean,
)

object RunButtonStyle {

    /** The role, with the lock winning over every other fact. */
    fun roleFor(s: RunButtonState): RunButtonRole = when {
        s.locked -> RunButtonRole.TONAL_LOCKED
        !s.hasOpenFile -> RunButtonRole.TONAL_DISABLED
        else -> RunButtonRole.CONTAINED
    }

    /**
     * True while a job the button started is still producing output. The button
     * stays contained (it is still the thing that owns the run) but its label
     * becomes the state — "RUN" → "RUNNING" — so the surface answers "did my tap
     * work?" without the user watching the panel.
     */
    fun showsRunning(s: RunButtonState): Boolean = s.running && !s.locked

    /** True when the control is the surface's primary action. */
    fun isPrimary(s: RunButtonState): Boolean = roleFor(s) == RunButtonRole.CONTAINED

    /**
     * True when a tap does something (or says why it cannot): the lock's own
     * `showChromeLock` makes a locked tap meaningful, so only the
     * nothing-open case is inert.
     */
    fun isEnabled(s: RunButtonState): Boolean = s.locked || s.hasOpenFile

    /**
     * The tone: the brand's container pair while the control is the hero, the
     * quiet surface variant otherwise. Phase 40.5's law — the accent is a role
     * (`primaryContainer` / `onPrimaryContainer`), so the RUN affordance keeps
     * its identity without a single new colour literal.
     */
    fun toneFor(s: RunButtonState): RunButtonTone =
        if (roleFor(s) == RunButtonRole.CONTAINED) {
            RunButtonTone.PRIMARY_CONTAINER
        } else {
            RunButtonTone.SURFACE_VARIANT
        }

    /** The one call the screen makes. */
    fun visualFor(s: RunButtonState): RunButtonVisual {
        val role = roleFor(s)
        return RunButtonVisual(
            role = role,
            tone = toneFor(s),
            showsRunning = showsRunning(s),
            primary = role == RunButtonRole.CONTAINED,
        )
    }
}
