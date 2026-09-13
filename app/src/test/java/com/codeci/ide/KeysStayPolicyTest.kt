package com.codeci.ide

import com.codeci.ide.ui.editor.KeysStayPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeysStayPolicyTest {

    @Test
    fun `default keeps keys mounted for a focused editor`() {
        assertTrue(KeysStayPolicy.isVisible(true, true, false, false))
    }

    @Test
    fun `preference off does not silently show keys`() {
        assertFalse(KeysStayPolicy.isVisible(false, true, false, false))
    }

    @Test
    fun `unfocused editor and explicit collapse both hide keys`() {
        assertFalse(KeysStayPolicy.isVisible(true, false, false, false))
        assertFalse(KeysStayPolicy.isVisible(true, true, false, true))
    }

    @Test
    fun `interactive stdin always hands the IME back`() {
        assertFalse(KeysStayPolicy.isVisible(true, true, true, false))
    }

    // ---- Phase 47.2 regression guards -------------------------------------
    // The default flip (CodeC Keys OFF by default) must not touch the strip's
    // own law: the policy's inputs are unchanged, and the screen still builds
    // `codecKeysUp` as the conjunction of the preference and this policy.

    @Test
    fun `policy signature is unchanged by the 47_2 default flip`() {
        val src = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/editor/KeysStayPolicy.kt"
        ).readText()
        org.junit.Assert.assertTrue(
            "KeysStayPolicy.isVisible keeps its four inputs",
            src.contains("fun isVisible(") &&
                src.contains("keepOpen: Boolean") &&
                src.contains("waitingForInput: Boolean") &&
                src.contains("explicitlyCollapsed: Boolean")
        )
    }

    @Test
    fun `with CodeC Keys off the strip still follows waitingForInput and the collapse`() {
        // In the screen, codecKeysUp = codecKeysOn && KeysStayPolicy.isVisible:
        // with codecKeysOn=false the conjunction is false regardless — i.e. the
        // extra-keys strip policy (22.x behaviour) governs what remains, and an
        // interactive stdin run still wins over everything.
        val editor = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
        ).readText()
        org.junit.Assert.assertTrue(
            "codecKeysUp must remain codecKeysOn && KeysStayPolicy.isVisible(...)",
            editor.contains("val codecKeysUp = codecKeysOn && keysVisible")
        )
        org.junit.Assert.assertTrue(
            "the strip's visibility must keep flowing through KeysStayPolicy",
            editor.contains("val keysVisible = KeysStayPolicy.isVisible(")
        )
    }
}
