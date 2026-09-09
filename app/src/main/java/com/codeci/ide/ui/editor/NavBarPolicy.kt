package com.codeci.ide.ui.editor

/**
 * Phase 32.1 — the pure decision that drives the bottom 5-tab bar. The bar
 * hides when the editor is the active destination AND a keyboard occupies the
 * bottom of the screen (the soft IME, or CodeC Keys), because code + chips +
 * keys + a permanent nav bar leave a postage stamp of editor. Once the user
 * reveals the bar (its handle tap / swipe-up) it stays until they leave the
 * editor — a deliberate reveal is never undone by the keyboard.
 *
 * Pure Kotlin, host-testable: the composable converts this into a boolean,
 * nothing more.
 */
object NavBarPolicy {

    /** A handle swipe-up of at least this many dp counts as a deliberate reveal. */
    const val REVEAL_SWIPE_DP = 32f

    /**
     * Whether the 5-tab bar must hide. [revealed] (the sticky un-hide) always
     * wins; otherwise the bar hides while the system IME is up (any tab) or,
     * in the editor only, while CodeC Keys is up.
     */
    fun hideNavBar(
        inEditor: Boolean,
        imeVisible: Boolean,
        keysVisible: Boolean,
        revealed: Boolean = false,
    ): Boolean {
        if (revealed) return false
        return imeVisible || (inEditor && keysVisible)
    }

    /** A swipe-up (negative dy, in dp) that reaches the threshold is a reveal. */
    fun revealOnSwipe(totalDragDp: Float): Boolean = totalDragDp <= -REVEAL_SWIPE_DP
}
