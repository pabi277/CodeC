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
}
