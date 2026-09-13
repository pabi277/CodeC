package com.codeci.ide

import com.codeci.ide.ui.editor.EditorViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 48 — what the viewport struct may represent (PART_48_1 §Tests). The
 * combinations here are the ones `EditorScreen` can actually produce; the
 * one impossible pairing is normalised, not represented twice.
 */
class ViewportSnapshotTest {

    private fun vp(
        heightPx: Int,
        ime: Boolean = false,
        keys: Boolean = false,
        strip: Boolean = true,
        output: Boolean = false,
        status: Boolean = true,
        font: Float = 14f
    ) = EditorViewport(
        heightPx = heightPx, imeVisible = ime, codecKeysVisible = keys,
        stripVisible = strip, outputExpanded = output, statusVisible = status,
        fontSizeSp = font
    )

    @Test
    fun `distinct chrome states with distinct heights are distinct viewports`() {
        // The five trigger states EditorScreen really produces (S/M screen
        // heights illustrative; the struct does not care about the values).
        val settled = vp(1400)
        val imeUp = vp(860, ime = true)
        val keysUp = vp(920, keys = true)
        val panelUp = vp(760, output = true)
        val statusYielded = vp(1378, status = false)
        val states = listOf(settled, imeUp, keysUp, panelUp, statusYielded)
        for (i in states.indices) {
            for (j in i + 1 until states.size) {
                assertNotEquals("states $i and $j collapsed", states[i], states[j])
            }
        }
    }

    @Test
    fun `codec keys and the ime are exclusive by construction - normalised`() {
        // Phase 28.2's IME lever: while CodeC Keys is up, sora's soft input
        // is switched OFF, so the IME cannot be visible beside it. A struct
        // that claims both would represent one on-screen state twice.
        val honest = vp(900, ime = true, keys = true).normalised()
        assertEquals(vp(900, keys = true), honest)
        assertFalse(honest.imeVisible)
        assertTrue(honest.codecKeysVisible)
    }

    @Test
    fun `normalised is the identity when nothing is exclusive`() {
        val plain = vp(1000, ime = true)
        assertEquals(plain, plain.normalised())
        val keysOnly = vp(1000, keys = true)
        assertEquals(keysOnly, keysOnly.normalised())
    }

    @Test
    fun `normalised keeps every other field`() {
        val v = vp(1234, ime = true, keys = true, strip = false, output = true, status = false, font = 18f)
        val n = v.normalised()
        assertEquals(false, n.imeVisible)
        assertEquals(1234, n.heightPx)
        assertEquals(false, n.stripVisible)
        assertEquals(true, n.outputExpanded)
        assertEquals(false, n.statusVisible)
        assertEquals(18f, n.fontSizeSp)
    }

    @Test
    fun `equal fields are equal - the struct is a value`() {
        assertEquals(vp(500, ime = true), vp(500, ime = true))
        assertNotEquals(vp(500), vp(501))
    }
}
