package com.codeci.ide

import com.codeci.ide.ui.editor.EditorChrome
import com.codeci.ide.ui.editor.EditorChromeSlot
import com.codeci.ide.ui.theme.CodecTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.2 — the editor chrome is declared, not ad hoc.
 *
 * The slots, their order and their token gaps are the pure [EditorChrome]
 * declaration; this test pins that the real screen carries the matching marker
 * per slot, in the declared order — so a future edit that moves a chrome piece
 * fails here instead of quietly changing the code view's height (the one
 * measurement Phase 48's caret policy keys on).
 */
class EditorChromeSlotTest {

    private val editor = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
    ).readText()

    @Test
    fun `the chrome declares eight named slots`() {
        assertEquals(8, EditorChromeSlot.entries.size)
        assertEquals(EditorChromeSlot.entries.toList(), EditorChrome.order)
    }

    @Test
    fun `every slot carries its marker in the screen`() {
        for (slot in EditorChromeSlot.entries) {
            assertTrue(
                "$slot has no marker in EditorScreen.kt",
                editor.contains("// " + EditorChrome.markerFor(slot)),
            )
        }
    }

    @Test
    fun `the markers appear in the order the chrome declares them`() {
        var cursor = 0
        for (slot in EditorChrome.order) {
            val marker = "// " + EditorChrome.markerFor(slot)
            val index = editor.indexOf(marker)
            assertTrue("missing marker $marker", index > 0)
            assertTrue(
                "the chrome's order changed: $slot (${slot.marker}) appears before the previous slot",
                index > cursor,
            )
            cursor = index
        }
    }

    @Test
    fun `the RUN action is its own slot`() {
        val runIndex = editor.indexOf("// " + EditorChrome.markerFor(EditorChromeSlot.RUN_ACTION))
        assertTrue(runIndex > 0)
        val region = editor.substring(runIndex, minOf(editor.length, runIndex + 2_000))
        assertTrue("the RUN slot must hold the RUN control", region.contains("onRunTap"))
    }

    @Test
    fun `every slot gap is a step of the one scale`() {
        val ladder = setOf(
            CodecTokens.Space.NONE,
            CodecTokens.Space.XXS,
            CodecTokens.Space.XS,
            CodecTokens.Space.S,
            CodecTokens.Space.M,
            CodecTokens.Space.L,
            CodecTokens.Space.XL,
            CodecTokens.Space.XXL,
            CodecTokens.Space.HUGE,
        )
        for (slot in EditorChromeSlot.entries) {
            assertTrue(
                "$slot has an off-scale gap ${EditorChrome.gapFor(slot)}",
                EditorChrome.gapFor(slot) in ladder,
            )
        }
    }

    @Test
    fun `the RUN control is contained, brand-toned and keeps its label`() {
        val runIndex = editor.indexOf("// " + EditorChrome.markerFor(EditorChromeSlot.RUN_ACTION))
        val region = editor.substring(runIndex, minOf(editor.length, runIndex + 4_000))
        assertTrue("RUN must be a real Button", region.contains("Button("))
        assertTrue(
            "RUN must wear the brand's container role, not a hex and not green text",
            region.contains("MaterialTheme.colorScheme.primaryContainer"),
        )
        assertTrue(
            "the button's colour decision comes from RunButtonStyle",
            region.contains("RunButtonStyle.roleFor(runButtonState)"),
        )
        assertTrue(
            "the label is kept (the study's own counter-example)",
            region.contains("RunButtonStyle.showsRunning(runButtonState)"),
        )
        assertTrue(
            "RUN sits on the touch floor",
            region.contains("defaultMinSize(minHeight = CodecTokens.space(CodecTokens.MIN_TOUCH))"),
        )
    }

    @Test
    fun `the RUN control is no longer a bare clickable row`() {
        assertFalse(
            "the old text-row RUN must be gone: the hero is a contained button",
            editor.contains(".clickable(onClick = onRunTap)"),
        )
        assertTrue(
            "the tour's anchor and its one-tap click must survive the restyle",
            editor.contains("GuideAnchors.EDITOR_RUN") &&
                editor.contains("onClick = onGuideRunTap"),
        )
    }

    @Test
    fun `every minimum height in the file comes from the one scale`() {
        val minimums = Regex("""defaultMinSize\(([^\n]*)\)""")
            .findAll(editor)
            .map { it.groupValues[1].trim() }
            .toList()
        assertTrue("RUN's own minimum must exist", minimums.isNotEmpty())
        for (value in minimums) {
            assertTrue(
                "an off-token minimum height appeared: $value",
                value.contains("CodecTokens.space(CodecTokens.MIN_TOUCH)"),
            )
        }
    }

    @Test
    fun `the RUN slot itself sits on the touch floor`() {
        val start = editor.indexOf("// " + EditorChrome.markerFor(EditorChromeSlot.RUN_ACTION))
        val end = editor.indexOf("// " + EditorChrome.markerFor(EditorChromeSlot.FIND_BAR))
        val region = editor.substring(start, end)
        assertEquals(
            "the hero must declare the touch floor exactly once",
            1,
            Regex("""defaultMinSize\(\s*minHeight\s*=\s*CodecTokens\.space\(CodecTokens\.MIN_TOUCH\)\)""")
                .findAll(region).count(),
        )
    }
}
