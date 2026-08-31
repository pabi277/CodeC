package com.codeci.ide.ui.terminal

/**
 * Phase 19.4 — terminal column width of a Unicode code point.
 *
 * Written from the public specifications (no third-party code):
 *  - UAX #11 "East Asian Width" (https://unicode.org/reports/tr11/):
 *    code points with property Wide (W) or Fullwidth (F) occupy TWO
 *    terminal columns (CJK ideographs, Hangul, Kana, fullwidth forms and
 *    the wide emoji);
 *  - the Unicode general categories Mn (nonspacing mark), Me (enclosing
 *    mark), Mc (spacing *combining* mark — the Indic vowel signs that form
 *    one visual cluster with their base consonant) and the zero-width
 *    format characters (Cf: ZWJ/ZWNJ, bidi marks, variation selectors,
 *    conjoining Hangul jamo medials/finals) occupy ZERO columns — they
 *    combine with the preceding base character.
 *
 * Before this class existed every code point took exactly one cell, so CJK
 * text and emoji overlapped their neighbours and Bengali/Devanagari vowel
 * signs smeared across separate cells. The table below is a curated subset
 * of the properties above, covering the scripts and symbols that actually
 * appear in a terminal; anything unknown is treated as width 1.
 */
object CharWidth {

    /** Code point combines with the previous cell (Mn/Me/Mc/Cf subsets). */
    private val ZERO_WIDTH = intArrayOf(
        0x0300, 0x036F,       // Combining Diacritical Marks
        0x0483, 0x0489,       // Cyrillic combining
        0x0591, 0x05BD,       // Hebrew marks
        0x05BF, 0x05BF,
        0x05C1, 0x05C2,
        0x05C4, 0x05C5,
        0x05C7, 0x05C7,
        0x0610, 0x061A,       // Arabic marks
        0x064B, 0x065F,
        0x0670, 0x0670,
        0x06D6, 0x06DC,
        0x06DF, 0x06E4,
        0x06E7, 0x06E8,
        0x06EA, 0x06ED,
        0x0711, 0x0711,       // Syriac
        0x0730, 0x074A,
        0x07A6, 0x07B0,       // Thaana
        0x07EB, 0x07F3,       // NKo
        0x0816, 0x0819,       // Samaritan
        0x081B, 0x0823,
        0x0825, 0x0827,
        0x0829, 0x082D,
        0x0859, 0x085B,       // Mandaic
        0x08E3, 0x0903,       // Arabic ext + Devanagari 0900-0903
        0x093A, 0x093C,       // Devanagari vowel signs (Mc/Mn)
        0x093E, 0x0940,       // vowel signs AA/I/II (Mc/Mc/Mn)
        0x0941, 0x0948,       // Mn vowel signs U..AI
        0x0949, 0x094C,       // Mc vowel signs
        0x094D, 0x094D,       // virama (Mn)
        0x094E, 0x094F,
        0x0951, 0x0957,       // Mn stress signs
        0x0962, 0x0963,
        0x0981, 0x0983,       // Bengali candrabindu/anusvara/visarga
        0x09BC, 0x09BC,       // nukta
        0x09BE, 0x09C0,       // vowel signs AA/I/II (Mc/Mc/Mn)
        0x09C1, 0x09C4,       // Mn vowel signs U..VOCAL RR
        0x09C7, 0x09C8,       // Mc vowel signs E/AI
        0x09CB, 0x09CC,       // Mc vowel signs O/AU
        0x09CD, 0x09CD,       // virama
        0x09D7, 0x09D7,       // vowel sign AU (Mc)
        0x09E2, 0x09E3,
        0x0A01, 0x0A03,       // Gurmukhi
        0x0A3C, 0x0A3C,
        0x0A41, 0x0A42,
        0x0A47, 0x0A51,       // incl. 0A4B-0A4D vowel signs
        0x0A70, 0x0A71,
        0x0A75, 0x0A75,
        0x0A81, 0x0A82,       // Gujarati
        0x0ABC, 0x0ABC,
        0x0ABE, 0x0AC0,       // vowel signs AA/I/II (Mc)
        0x0AC1, 0x0ACD,
        0x0AE2, 0x0AE3,
        0x0B01, 0x0B03,       // Oriya
        0x0B3C, 0x0B3C,
        0x0B3F, 0x0B44,       // vowel sign I (Mc) + Mn signs
        0x0B4D, 0x0B56,       // virama + Mc signs
        0x0B62, 0x0B63,
        0x0B82, 0x0B82,       // Tamil
        0x0BBE, 0x0BC2,       // vowel signs (Mc/Mn)
        0x0BCD, 0x0BCD,       // virama
        0x0BD7, 0x0BD7,
        0x0C00, 0x0C04,       // Telugu
        0x0C3E, 0x0C40,
        0x0C46, 0x0C56,
        0x0C62, 0x0C63,
        0x0C81, 0x0C81,       // Kannada
        0x0CBC, 0x0CBC,
        0x0CBF, 0x0CC4,       // vowel signs (Mc/Mn)
        0x0CC6, 0x0CC6,
        0x0CCC, 0x0CCD,
        0x0CE2, 0x0CE3,
        0x0D01, 0x0D03,       // Malayalam
        0x0D3E, 0x0D40,       // vowel signs (Mc/Mc/Mn)
        0x0D41, 0x0D44,
        0x0D4D, 0x0D4D,
        0x0D57, 0x0D57,
        0x0D62, 0x0D63,
        0x0DCA, 0x0DDF,       // Sinhala virama + vowel signs
        0x1160, 0x11FF,       // Hangul Jamo medial/final (conjoining)
        0x1AB0, 0x1AFF,       // Combining Diacritical Marks Extended
        0x1DC0, 0x1DFF,       // Combining Diacritical Marks Supplement
        0x200B, 0x200F,       // ZWSP..RLM (zero-width + bidi)
        0x2060, 0x2064,       // word joiner..invisible plus
        0x20D0, 0x20F0,       // Combining Marks for Symbols
        0xD7B0, 0xD7C6,       // Hangul Jamo Extended-B (vowel)
        0xD7CB, 0xD7FB,       // Hangul Jamo Extended-B (final)
        0xFE00, 0xFE0F,       // Variation Selectors 1–16
        0xFE20, 0xFE2F,       // Combining Half Marks
        0xFEFF, 0xFEFF,       // zero-width no-break space (BOM)
        0xE0100, 0xE01EF      // Variation Selectors Supplement
    )

    /** East Asian Width W/F subsets that take two columns. */
    private val WIDE = intArrayOf(
        0x1100, 0x115F,       // Hangul Jamo leading
        0x231A, 0x231B,       // watch, hourglass (emoji)
        0x2329, 0x232A,
        0x23E9, 0x23EC,
        0x23F0, 0x23F0,
        0x23F3, 0x23F3,
        0x25FD, 0x25FE,
        0x2614, 0x2615,
        0x2648, 0x2653,
        0x267F, 0x267F,
        0x2693, 0x2693,
        0x26A1, 0x26A1,
        0x26AA, 0x26AB,
        0x26BD, 0x26BE,
        0x26C4, 0x26C5,
        0x26CE, 0x26CE,
        0x26D4, 0x26D4,
        0x26EA, 0x26EA,
        0x26F2, 0x26F3,
        0x26F5, 0x26F5,
        0x26FA, 0x26FA,
        0x26FD, 0x26FD,
        0x2705, 0x2705,
        0x270A, 0x270B,
        0x2728, 0x2728,
        0x274C, 0x274C,
        0x274E, 0x274E,
        0x2753, 0x2755,
        0x2757, 0x2757,
        0x2795, 0x2797,
        0x27B0, 0x27B0,
        0x27BF, 0x27BF,
        0x2B1B, 0x2B1C,
        0x2B50, 0x2B50,
        0x2B55, 0x2B55,
        0x2E80, 0x2E99,       // CJK Radicals Supplement
        0x2E9B, 0x2EF3,
        0x2F00, 0x2FD5,       // Kangxi Radicals
        0x2FF0, 0x2FFB,       // Ideographic Description
        0x3000, 0x303E,       // CJK Symbols (incl. ideographic space)
        0x3041, 0x3096,       // Hiragana
        0x3099, 0x30FF,       // Kana combining + Katakana
        0x3105, 0x312F,       // Bopomofo
        0x3131, 0x318E,       // Hangul Compatibility Jamo
        0x3190, 0x31E3,       // Kanbun + CJK Strokes
        0x31F0, 0x321E,       // Katakana Phonetic Extensions + Enclosed CJK
        0x3220, 0x3247,
        0x3250, 0x4DBF,       // Enclosed + CJK Ext A
        0x4E00, 0xA48C,       // CJK Unified Ideographs + Ext A tail
        0xA490, 0xA4C6,       // Yijing
        0xA960, 0xA97C,       // Hangul Jamo Extended-A
        0xAC00, 0xD7A3,       // Hangul Syllables
        0xF900, 0xFAFF,       // CJK Compatibility Ideographs
        0xFE10, 0xFE19,       // Vertical forms
        0xFE30, 0xFE52,       // CJK Compatibility Forms
        0xFE54, 0xFE66,
        0xFE68, 0xFE6B,
        0xFF00, 0xFF60,       // Fullwidth Forms
        0xFFE0, 0xFFE6,
        0x16FE0, 0x16FE4,
        0x16FF0, 0x16FF1,
        0x17000, 0x187F7,     // Tangut
        0x18800, 0x18CD5,
        0x18D00, 0x18D08,
        0x1B000, 0x1B11E,     // Kana Supplements
        0x1B150, 0x1B152,
        0x1B164, 0x1B167,
        0x1B170, 0x1B2FB,
        0x1F004, 0x1F004,     // Emoji (UAX #11 Wide subset)
        0x1F0CF, 0x1F0CF,
        0x1F18E, 0x1F18E,
        0x1F191, 0x1F19A,
        0x1F200, 0x1F320,
        0x1F32D, 0x1F335,
        0x1F337, 0x1F37C,
        0x1F37E, 0x1F393,
        0x1F3A0, 0x1F3CA,
        0x1F3CF, 0x1F3D3,
        0x1F3E0, 0x1F3F0,
        0x1F3F4, 0x1F3F4,
        0x1F3F8, 0x1F43E,
        0x1F440, 0x1F440,
        0x1F442, 0x1F4FC,
        0x1F4FF, 0x1F53D,
        0x1F54B, 0x1F54E,
        0x1F550, 0x1F567,
        0x1F57A, 0x1F57A,
        0x1F595, 0x1F596,
        0x1F5A4, 0x1F5A4,
        0x1F5FB, 0x1F64F,
        0x1F680, 0x1F6C5,
        0x1F6CC, 0x1F6CC,
        0x1F6D0, 0x1F6D2,
        0x1F6D5, 0x1F6D7,
        0x1F6EB, 0x1F6EC,
        0x1F6F4, 0x1F6FC,
        0x1F7E0, 0x1F7EB,
        0x1F90C, 0x1F93A,
        0x1F93C, 0x1F945,
        0x1F947, 0x1F978,
        0x1F97A, 0x1F9CB,
        0x1F9CD, 0x1F9FF,
        0x1FA70, 0x1FA74,
        0x1FA78, 0x1FA7A,
        0x1FA80, 0x1FA86,
        0x1FA90, 0x1FAA8,
        0x1FAB0, 0x1FAB6,
        0x1FAC0, 0x1FAC2,
        0x1FAD0, 0x1FAD6,
        0x20000, 0x2FFFD,     // CJK Ext B–F
        0x30000, 0x3FFFD      // CJK Ext G+
    )

    /** Terminal column count of [codePoint]: 0 (combining), 1, or 2 (wide). */
    fun width(codePoint: Int): Int = when {
        codePoint < 0x300 -> 1
        inRanges(ZERO_WIDTH, codePoint) -> 0
        inRanges(WIDE, codePoint) -> 2
        else -> 1
    }

    private fun inRanges(table: IntArray, cp: Int): Boolean {
        var lo = 0
        var hi = table.size / 2 - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val start = table[mid * 2]
            val end = table[mid * 2 + 1]
            if (cp < start) hi = mid - 1
            else if (cp > end) lo = mid + 1
            else return true
        }
        return false
    }
}
