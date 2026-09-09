package com.codeci.ide

import com.codeci.ide.ui.editor.NavBarPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 32.1 — the bar-hide decision. The composable (MainActivity) only
 * converts UI state into these booleans; the rule itself holds no matter how
 * the editor reports its keyboard.
 */
class NavBarPolicyTest {

    @Test
    fun `bar shows when nothing is focused`() {
        assertFalse(
            NavBarPolicy.hideNavBar(inEditor = false, imeVisible = false, keysVisible = false)
        )
    }

    @Test
    fun `ime up hides the bar on every tab`() {
        // The pre-32 behaviour is preserved off the editor.
        assertTrue(
            NavBarPolicy.hideNavBar(inEditor = false, imeVisible = true, keysVisible = false)
        )
        assertTrue(
            NavBarPolicy.hideNavBar(inEditor = true, imeVisible = true, keysVisible = false)
        )
    }

    @Test
    fun `codec keys hide the bar only inside the editor`() {
        assertTrue(
            NavBarPolicy.hideNavBar(inEditor = true, imeVisible = false, keysVisible = true)
        )
        // Keys cannot be up on another tab, but the policy must not hide there
        // anyway — hiding is the editor's rule, not a global keyboard rule.
        assertFalse(
            NavBarPolicy.hideNavBar(inEditor = false, imeVisible = false, keysVisible = true)
        )
    }

    @Test
    fun `editor with no keyboard keeps the bar`() {
        assertFalse(
            NavBarPolicy.hideNavBar(inEditor = true, imeVisible = false, keysVisible = false)
        )
    }

    @Test
    fun `a reveal wins over every hide condition`() {
        assertFalse(
            NavBarPolicy.hideNavBar(
                inEditor = true, imeVisible = true, keysVisible = true, revealed = true
            )
        )
    }

    @Test
    fun `swipe up past the threshold reveals, anything less does not`() {
        assertTrue(NavBarPolicy.revealOnSwipe(-40f))
        assertTrue(NavBarPolicy.revealOnSwipe(-NavBarPolicy.REVEAL_SWIPE_DP))
        assertFalse(NavBarPolicy.revealOnSwipe(-NavBarPolicy.REVEAL_SWIPE_DP + 1f))
        assertFalse(NavBarPolicy.revealOnSwipe(0f))
        // A downward drag is never a reveal.
        assertFalse(NavBarPolicy.revealOnSwipe(40f))
    }
}
