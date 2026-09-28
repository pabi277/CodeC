package com.codeci.ide.ui.editor

/**
 * Phase 70.1 (2026-09-28) — how tall the Output Panel may be, from facts.
 *
 * The owner's answer, verbatim: *“A — Cap the panel lower while the keyboard
 * is up”*, with the code view keeping a floor. Before this the panel was
 * clamped to 55 % of the screen height whether or not the keyboard was on
 * screen, so opening the panel to answer a prompt left the editor a sliver.
 *
 * The rule stays deliberately dumb and host-tested: the panel takes at most
 * 55 % of the screen normally, **38 % with the keyboard up**, never less than
 * [MIN] (unless the screen itself is smaller than that), and a non-finite
 * request falls back to the default. The caller keeps owning the drag; this
 * only clamps what the drag produced.
 *
 * The floor is 160, not the old 120: Q2's status row, Q4's 48 dp Stop and the
 * command line the owner types into are each 48 dp, so a 120 dp panel could no
 * longer hold what it exists to hold.
 *
 * Phone pass, later on 2026-09-28. The default used to be a fixed 220 dp. Under
 * the three 48 dp rows (144 dp) that left ~76 dp — three or four lines of
 * output — and ~44 dp, two lines, once the error-count banner was there too.
 * The owner left the default to this chat (*“Your choice”*): it is now
 * [DEFAULT_FRACTION] of the screen — about 320 dp on a typical phone, nine
 * lines — through [defaultFor]. The caps and the floor are unchanged.
 */
object OutputPanelHeight {

    const val MIN = 160f
    const val FRACTION = 0.55f

    /** Q5: the keyboard is a bigger claim on the screen than the panel is. */
    const val IME_FRACTION = 0.38f

    /** The share of the screen the panel opens at before anyone drags it. */
    const val DEFAULT_FRACTION = 0.40f

    /** What the panel opens at on a screen of [available] dp, clamped like any request. */
    fun defaultFor(available: Float): Float =
        resolve(available.coerceAtLeast(0f) * DEFAULT_FRACTION, available, imeVisible = false)

    fun resolve(requested: Float, available: Float, imeVisible: Boolean): Float {
        val room = available.coerceAtLeast(0f)
        val max = room * if (imeVisible) IME_FRACTION else FRACTION
        val min = minOf(MIN, max)
        return (if (requested.isFinite()) requested else room * DEFAULT_FRACTION).coerceIn(min, max)
    }
}
