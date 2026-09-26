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
    fun `hide tabs parks the bar, and only inside the editor`() {
        // Phase 60 — the tab menu's manual door: same hiding, same scope. Off
        // the editor the bar comes back, because the reveal handle that
        // un-hides it only exists there.
        assertTrue(
            NavBarPolicy.hideNavBar(
                inEditor = true, imeVisible = false, keysVisible = false, hiddenByUser = true
            )
        )
        assertFalse(
            NavBarPolicy.hideNavBar(
                inEditor = false, imeVisible = false, keysVisible = false, hiddenByUser = true
            )
        )
    }

    @Test
    fun `hide tabs parks the bar even while the keyboard is down`() {
        // The one state the auto rules would otherwise keep the bar in.
        assertTrue(
            NavBarPolicy.hideNavBar(
                inEditor = true, imeVisible = false, keysVisible = false, hiddenByUser = true
            )
        )
        assertFalse(
            "without the flag nothing changed",
            NavBarPolicy.hideNavBar(inEditor = true, imeVisible = false, keysVisible = false)
        )
    }

    @Test
    fun `a reveal wins over the manual hide too`() {
        // The reveal handle taps write BOTH facts in the app; the policy still
        // has to prefer the reveal, so the order cannot depend on that.
        assertFalse(
            NavBarPolicy.hideNavBar(
                inEditor = true,
                imeVisible = true,
                keysVisible = true,
                revealed = true,
                hiddenByUser = true,
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
