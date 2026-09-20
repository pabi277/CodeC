package com.codeci.ide

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.codeci.ide.ui.theme.CodecType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50.3 — the scale is ordered, exact multiples, and nothing is left
 * at the Material default. (Constructing `Typography`/`TextStyle` needs no
 * Compose runtime — pure value classes — so this runs on the host JVM.)
 */
class CodecTypeTest {

    @Test
    fun `the six anchors are ordered`() {
        val anchors = listOf(
            CodecType.Size.DISPLAY,
            CodecType.Size.HEADLINE,
            CodecType.Size.TITLE,
            CodecType.Size.BODY,
            CodecType.Size.LABEL,
            CodecType.Size.CAPTION,
        )
        assertEquals(listOf(34f, 26f, 20f, 15f, 13f, 11f), anchors)
        assertEquals(anchors.sortedDescending(), anchors)
    }

    @Test
    fun `anchor sizes land on the scale slots`() {
        val scale = CodecType.scale()
        assertEquals(34.sp, scale.displayLarge.fontSize)
        assertEquals(26.sp, scale.headlineLarge.fontSize)
        assertEquals(20.sp, scale.titleLarge.fontSize)
        assertEquals(15.sp, scale.bodyLarge.fontSize)
        assertEquals(13.sp, scale.labelLarge.fontSize)
        assertEquals(11.sp, scale.labelSmall.fontSize)
    }

    @Test
    fun `line heights are the declared multiples`() {
        val scale = CodecType.scale()
        val tight = CodecType.LineHeight.TIGHT.toDouble()
        val normal = CodecType.LineHeight.NORMAL.toDouble()
        val relaxed = CodecType.LineHeight.RELAXED.toDouble()
        val slots = listOf(
            scale.displayLarge to tight, scale.displayMedium to tight, scale.displaySmall to tight,
            scale.headlineLarge to tight, scale.headlineMedium to tight, scale.headlineSmall to tight,
            scale.titleLarge to normal, scale.titleMedium to normal, scale.titleSmall to normal,
            scale.bodyLarge to relaxed, scale.bodyMedium to relaxed, scale.bodySmall to normal,
            scale.labelLarge to normal, scale.labelMedium to normal, scale.labelSmall to normal,
        )
        for ((style, multiple) in slots) {
            val actual = style.lineHeight.value.toDouble() / style.fontSize.value.toDouble()
            assertEquals(
                "line height of ${style.fontSize} must be $multiple×",
                multiple,
                actual,
                1e-4,
            )
        }
    }

    @Test
    fun `anchor weights carry the hierarchy`() {
        val scale = CodecType.scale()
        assertEquals(FontWeight.Bold, scale.displayLarge.fontWeight)
        assertEquals(FontWeight.SemiBold, scale.headlineLarge.fontWeight)
        assertEquals(FontWeight.SemiBold, scale.titleLarge.fontWeight)
        assertEquals(FontWeight.Normal, scale.bodyLarge.fontWeight)
        assertEquals(FontWeight.Medium, scale.labelLarge.fontWeight)
        assertEquals(FontWeight.Medium, scale.labelSmall.fontWeight)
    }

    @Test
    fun `no anchor is left at the Material default`() {
        val scale = CodecType.scale()
        val material = Typography()
        fun signature(style: androidx.compose.ui.text.TextStyle): String =
            "${style.fontSize}/${style.lineHeight}/${style.fontWeight}/${style.letterSpacing}"
        val pairs = listOf(
            scale.displayLarge to material.displayLarge,
            scale.headlineLarge to material.headlineLarge,
            scale.titleLarge to material.titleLarge,
            scale.bodyLarge to material.bodyLarge,
            scale.labelLarge to material.labelLarge,
            scale.labelSmall to material.labelSmall,
        )
        for ((ours, default) in pairs) {
            assertTrue(
                "$ours was left at the Material default",
                signature(ours) != signature(default),
            )
        }
    }

    @Test
    fun `the code family is its own face`() {
        assertTrue(CodecType.codeFamily !== FontFamily.Default)
        assertTrue(CodecType.codeFamily !== FontFamily.Monospace)
        assertTrue(CodecType.codeFamily !== FontFamily.SansSerif)
        assertTrue(CodecType.codeFamily !== FontFamily.Serif)
    }

    @Test
    fun `the scale is deterministic`() {
        assertEquals(CodecType.scale().bodyLarge, CodecType.scale().bodyLarge)
        assertEquals(CodecType.scale().labelSmall, CodecType.scale().labelSmall)
    }
}
