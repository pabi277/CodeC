package com.codeci.ide

import com.codeci.ide.ui.ai.AiOutputConflict
import com.codeci.ide.ui.ai.AiSheetEvent
import com.codeci.ide.ui.ai.AiSheetPolicy
import com.codeci.ide.ui.ai.AiSheetState
import com.codeci.ide.ui.editor.OutputPanelHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 77.2 — the chat sheet's state machine, heights and Output-conflict variants. */
class AiSheetPolicyTest {

    private fun next(s: AiSheetState, e: AiSheetEvent, f: Float? = null) = AiSheetPolicy.next(s, e, f)

    @Test
    fun `tapping the bubble opens HALF and does nothing once open`() {
        assertEquals(AiSheetState.HALF, next(AiSheetState.HIDDEN, AiSheetEvent.TAP_BUBBLE))
        assertEquals(AiSheetState.HALF, next(AiSheetState.HALF, AiSheetEvent.TAP_BUBBLE))
        assertEquals(AiSheetState.FULL, next(AiSheetState.FULL, AiSheetEvent.TAP_BUBBLE))
    }

    @Test
    fun `expand goes HALF to FULL only`() {
        assertEquals(AiSheetState.FULL, next(AiSheetState.HALF, AiSheetEvent.EXPAND))
        assertEquals(AiSheetState.FULL, next(AiSheetState.FULL, AiSheetEvent.EXPAND))
        assertEquals(AiSheetState.HIDDEN, next(AiSheetState.HIDDEN, AiSheetEvent.EXPAND))
    }

    @Test
    fun `collapse and Back step down one level`() {
        for (e in listOf(AiSheetEvent.COLLAPSE, AiSheetEvent.BACK)) {
            assertEquals(AiSheetState.HALF, next(AiSheetState.FULL, e))
            assertEquals(AiSheetState.HIDDEN, next(AiSheetState.HALF, e))
            assertEquals(AiSheetState.HIDDEN, next(AiSheetState.HIDDEN, e))
        }
    }

    @Test
    fun `the minimize arrow goes straight to the bubble from anywhere`() {
        for (s in AiSheetState.values()) assertEquals(AiSheetState.HIDDEN, next(s, AiSheetEvent.MINIMIZE))
    }

    @Test
    fun `drag thresholds - 0_19 hides, 0_20 is HALF, 0_75 is HALF, 0_76 is FULL`() {
        assertEquals(AiSheetState.HIDDEN, next(AiSheetState.HALF, AiSheetEvent.DRAG_END, 0.19f))
        assertEquals(AiSheetState.HALF, next(AiSheetState.HALF, AiSheetEvent.DRAG_END, 0.20f))
        assertEquals(AiSheetState.HALF, next(AiSheetState.HALF, AiSheetEvent.DRAG_END, 0.75f))
        assertEquals(AiSheetState.FULL, next(AiSheetState.HALF, AiSheetEvent.DRAG_END, 0.76f))
    }

    @Test
    fun `a drag that settles mid-screen brings FULL back to HALF`() {
        assertEquals(AiSheetState.HALF, next(AiSheetState.FULL, AiSheetEvent.DRAG_END, 0.5f))
        assertEquals(AiSheetState.FULL, next(AiSheetState.FULL, AiSheetEvent.DRAG_END, 0.95f))
    }

    @Test
    fun `a drag with no usable height changes nothing, and never opens a hidden sheet`() {
        assertEquals(AiSheetState.HALF, next(AiSheetState.HALF, AiSheetEvent.DRAG_END, null))
        assertEquals(AiSheetState.HALF, next(AiSheetState.HALF, AiSheetEvent.DRAG_END, Float.NaN))
        assertEquals(AiSheetState.HIDDEN, next(AiSheetState.HIDDEN, AiSheetEvent.DRAG_END, 0.5f))
    }

    @Test
    fun `variant A replaces the Output panel with HALF, variant B opens FULL`() {
        assertEquals(AiSheetState.HALF to true, AiSheetPolicy.openWithOutput(AiOutputConflict.REPLACE_OUTPUT, outputOpen = true))
        assertEquals(AiSheetState.FULL to false, AiSheetPolicy.openWithOutput(AiOutputConflict.OPEN_FULL, outputOpen = true))
    }

    @Test
    fun `with the Output panel closed both variants simply open HALF`() {
        for (c in AiOutputConflict.values()) {
            assertEquals(AiSheetState.HALF to false, AiSheetPolicy.openWithOutput(c, outputOpen = false))
        }
    }

    @Test
    fun `the Output panel is off screen while the sheet is up and returns when it is put away`() {
        assertTrue(AiSheetPolicy.outputVisible(outputExpanded = true, sheet = AiSheetState.HIDDEN))
        assertFalse(AiSheetPolicy.outputVisible(outputExpanded = true, sheet = AiSheetState.HALF))
        assertFalse(AiSheetPolicy.outputVisible(outputExpanded = true, sheet = AiSheetState.FULL))
        assertFalse(AiSheetPolicy.outputVisible(outputExpanded = false, sheet = AiSheetState.HIDDEN))
    }

    @Test
    fun `HALF height is exactly the Output panel rule with and without the keyboard`() {
        for (screen in listOf(360f, 640f, 800f, 900f)) {
            assertEquals(
                OutputPanelHeight.resolve(OutputPanelHeight.defaultFor(screen), screen, false),
                AiSheetPolicy.halfHeight(screen, imeVisible = false), 0.001f
            )
            assertEquals(
                OutputPanelHeight.resolve(OutputPanelHeight.defaultFor(screen), screen, true),
                AiSheetPolicy.halfHeight(screen, imeVisible = true), 0.001f
            )
        }
        // the keyboard rule really is the tighter one
        assertTrue(AiSheetPolicy.halfHeight(800f, true) < AiSheetPolicy.halfHeight(800f, false))
        assertTrue(AiSheetPolicy.halfHeight(800f, true) <= 800f * OutputPanelHeight.IME_FRACTION + 0.001f)
    }

    @Test
    fun `the drag fraction is the height after the finger moved, as a share of the room`() {
        assertEquals(0.5f, AiSheetPolicy.heightFractionAfterDrag(400f, 0f, 800f), 0.0001f)
        assertEquals(0.75f, AiSheetPolicy.heightFractionAfterDrag(400f, -200f, 800f), 0.0001f)
        assertEquals(0f, AiSheetPolicy.heightFractionAfterDrag(400f, 900f, 800f), 0.0001f)
        assertEquals(1f, AiSheetPolicy.heightFractionAfterDrag(400f, -9000f, 800f), 0.0001f)
        assertTrue(AiSheetPolicy.heightFractionAfterDrag(400f, 10f, 0f).isNaN())
    }
}
