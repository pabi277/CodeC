package com.codeci.ide

import com.codeci.ide.ui.terminal.CharWidth
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 19.4 — terminal column widths follow UAX #11 (W/F = 2) and the
 * combining categories (Mn/Me/Mc/Cf = 0). Written from the public Unicode
 * property definitions.
 */
class CharWidthTest {

    @Test
    fun `ascii is single width`() {
        for (cp in 0x20..0x7E) assertEquals(1, CharWidth.width(cp))
    }

    @Test
    fun `combining marks take no column`() {
        assertEquals(0, CharWidth.width(0x0301))   // combining acute
        assertEquals(0, CharWidth.width(0x09CD))   // Bengali virama (Mn)
        assertEquals(0, CharWidth.width(0x09BE))   // Bengali vowel sign AA (Mc)
        assertEquals(0, CharWidth.width(0x09BF))   // Bengali vowel sign I (Mc)
        assertEquals(0, CharWidth.width(0x093E))   // Devanagari vowel sign AA (Mc)
        assertEquals(0, CharWidth.width(0xFE0F))   // variation selector-16
        assertEquals(0, CharWidth.width(0x200D))   // zero-width joiner
    }

    @Test
    fun `cjk hangul kana and fullwidth forms are double width`() {
        assertEquals(2, CharWidth.width(0x6F22))   // 漢 CJK ideograph
        assertEquals(2, CharWidth.width(0xD55C))   // 한 Hangul syllable
        assertEquals(2, CharWidth.width(0x3072))   // ひ hiragana
        assertEquals(2, CharWidth.width(0xFF01))   // ！ fullwidth exclamation
        assertEquals(2, CharWidth.width(0x3000))   // ideographic space
    }

    @Test
    fun `emoji are double width`() {
        assertEquals(2, CharWidth.width(0x1F600))  // 😀 grinning face
        assertEquals(2, CharWidth.width(0x231A))   // ⌚ watch
        assertEquals(2, CharWidth.width(0x1F44D))  // 👍 thumbs up
    }

    @Test
    fun `ambiguous narrow symbols stay single width`() {
        assertEquals(1, CharWidth.width(0x2500))   // box drawing ─
        assertEquals(1, CharWidth.width(0x03B1))   // Greek α
        assertEquals(1, CharWidth.width(0x044F))   // Cyrillic я
        assertEquals(1, CharWidth.width(0x00E9))   // é (precomposed)
        assertEquals(1, CharWidth.width(0xFF71))   // ｱ halfwidth katakana
        assertEquals(1, CharWidth.width(0x2764))   // ❤ is East-Asian Ambiguous → 1
    }
}
