package com.codeci.ide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.codeci.ide.ui.components.OUTPUT_COLORS
import com.codeci.ide.ui.components.PANEL_BACKGROUND
import com.codeci.ide.ui.components.PANEL_HEADER_BACKGROUND
import com.codeci.ide.ui.theme.CodecPalette
import com.codeci.ide.ui.theme.Contrast
import com.codeci.ide.ui.viewmodels.OutputLineKind
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 70.1 — the Output Panel's palette, measured instead of eyeballed.
 *
 * The 2026-09-27 review found the panel's colours (`#55FF55`, `#66B2FF`,
 * `#FFB347`, …) were a second palette no test audited, unlike every other
 * colour in the app (`AppContrastTest`). The owner's same-day report — *“hard
 * to understand the error line from terminal”* — makes readability the point,
 * so the existing colours stay (this is a terminal surface) and get the audit
 * they were missing: every line colour and every state word clears WCAG AA
 * (4.5:1) on the surface it is drawn on.
 */
class OutputPanelContrastTest {

    @Test
    fun `every output line colour clears AA on the panel background`() {
        for ((kind, color) in OUTPUT_COLORS) {
            val ratio = Contrast.ratio(color.toArgb(), PANEL_BACKGROUND.toArgb())
            assertTrue(
                "$kind (${hex(color)}) reads at only ${"%.2f".format(ratio)}:1",
                ratio >= Contrast.AA_TEXT,
            )
        }
    }

    @Test
    fun `every line kind the panel can draw has a colour`() {
        for (kind in OutputLineKind.entries) {
            assertTrue(
                "$kind has no colour: it would fall back to white silently",
                OUTPUT_COLORS.containsKey(kind),
            )
        }
    }

    @Test
    fun `the state words clear AA on the header they sit in`() {
        val tints = mapOf(
            "failed" to 0xFFFF5555.toInt(),
            "stopped" to 0xFFFFB347.toInt(),
            "working" to 0xFF66B2FF.toInt(),
            "done" to 0xFF55FF55.toInt(),
            "idle" to CodecPalette.MUTED_TEXT,
            "summary on a failure" to 0xFFFF8A80.toInt(),
            "error banner" to 0xFFFF8A80.toInt(),
        )
        for ((name, tint) in tints) {
            val ratio = Contrast.ratio(tint, PANEL_HEADER_BACKGROUND.toArgb())
            assertTrue(
                "$name reads at only ${"%.2f".format(ratio)}:1",
                ratio >= Contrast.AA_TEXT,
            )
        }
    }

    private fun hex(color: Color): String = "#%08X".format(color.toArgb())
}
