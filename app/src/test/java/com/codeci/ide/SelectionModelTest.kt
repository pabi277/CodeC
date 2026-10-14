package com.codeci.ide

import com.codeci.ide.ui.terminal.SelCell
import com.codeci.ide.ui.terminal.SelHandle
import com.codeci.ide.ui.terminal.SelectionModel
import com.codeci.ide.ui.terminal.TermSelection
import com.codeci.ide.ui.terminal.TerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 103.1 — the selection model is pure: handles, buffer-absolute anchoring,
 * and the row/col math that the view's touch mapping relies on.
 */
class SelectionModelTest {

    private fun cell(row: Long, col: Int) = SelCell(row, col)

    @Test
    fun `a long-press word selection starts with no handle held`() {
        val sel = SelectionModel.begin(row = 7L, wordStart = 3, wordEnd = 9, altScreen = false)
        assertNotNull(sel)
        assertEquals(cell(7L, 3), sel!!.start)
        assertEquals(cell(7L, 9), sel.end)
        assertNull(sel.active)
    }

    @Test
    fun `the alternate screen refuses a selection`() {
        assertNull(SelectionModel.begin(row = 7L, wordStart = 0, wordEnd = 4, altScreen = true))
    }

    @Test
    fun `dragging the end handle back past the start swaps roles and keeps the finger`() {
        val sel = TermSelection(cell(5L, 2), cell(5L, 4))
        val moved = SelectionModel.drag(sel, SelHandle.END, cell(5L, 0))
        assertEquals(cell(5L, 0), moved.start)
        assertEquals(cell(5L, 2), moved.end)
        // The finger is now on the START handle (it dragged left of the other end).
        assertEquals(SelHandle.START, moved.active)
    }

    @Test
    fun `dragging the start handle past the end swaps roles the other way`() {
        val sel = TermSelection(cell(5L, 2), cell(5L, 4))
        val moved = SelectionModel.drag(sel, SelHandle.START, cell(5L, 9))
        assertEquals(cell(5L, 4), moved.start)
        assertEquals(cell(5L, 9), moved.end)
        assertEquals(SelHandle.END, moved.active)
    }

    @Test
    fun `a reverse drag across rows keeps reading order`() {
        val sel = TermSelection(cell(3L, 5), cell(3L, 8))
        val moved = SelectionModel.drag(sel, SelHandle.END, cell(1L, 0))
        assertEquals(cell(1L, 0), moved.start)
        assertEquals(cell(3L, 5), moved.end)
        assertEquals(SelHandle.START, moved.active)
    }

    @Test
    fun `a handle dragged onto the other handle collapses to one cell and keeps its identity`() {
        val sel = TermSelection(cell(2L, 1), cell(2L, 6))
        val moved = SelectionModel.drag(sel, SelHandle.START, cell(2L, 6))
        assertEquals(cell(2L, 6), moved.start)
        assertEquals(cell(2L, 6), moved.end)
        assertEquals(SelHandle.START, moved.active)
    }

    @Test
    fun `select all covers history and the live screen`() {
        val emu = TerminalEmulator(cols = 10, rows = 3, scrollbackLimit = 50)
        emu.feed("aaa\r\nbbb\r\nccc\r\nddd\r\neee")
        val snap = emu.snapshot()
        assertEquals(2, snap.scrollbackCount)
        val all = SelectionModel.selectAll(snap)
        assertEquals(cell(0L, 0), all.start)
        assertEquals(cell(4L, 9), all.end)
    }

    @Test
    fun `a selection keeps its text while output scrolls the screen`() {
        val emu = TerminalEmulator(cols = 10, rows = 3, scrollbackLimit = 50)
        emu.feed("aaa\r\nbbb\r\nccc")
        val before = emu.snapshot()
        // "bbb" is live row 1; its buffer-absolute row is historyBase + 1 = 1.
        val row = before.absRowOf(1)
        val sel = SelectionModel.begin(row, 0, 2, altScreen = false)!!
        assertEquals("bbb", SelectionModel.extract(sel, before))

        // Five more lines scroll "bbb" deep into history.
        emu.feed("\r\nddd\r\neee\r\nfff\r\nggg\r\nhhh")
        val after = emu.snapshot()
        assertTrue(after.scrollbackCount > before.scrollbackCount)
        assertEquals("bbb", SelectionModel.extract(sel, after))
    }

    @Test
    fun `absolute rows survive the history cap dropping lines from the front`() {
        val emu = TerminalEmulator(cols = 10, rows = 2, scrollbackLimit = 3)
        emu.feed("one\r\ntwo\r\nthree")
        val first = emu.snapshot()
        // "two" is the top live row (one is in history).
        val sel = SelectionModel.begin(first.absRowOf(0), 0, 2, altScreen = false)!!
        assertEquals("two", SelectionModel.extract(sel, first))

        // Push far past the cap: history is capped at 3, so "two" is dropped
        // long before this writes more. Its selection must be gone, not shifted.
        emu.feed("\r\nfour\r\nfive\r\nsix\r\nseven\r\neight")
        val later = emu.snapshot()
        assertEquals(3, later.scrollbackCount)
        assertTrue(later.historyBase > 0L)
        assertNull(SelectionModel.clamp(sel, later.historyBase, later.absRowOf(later.rows - 1), later.cols))
    }

    @Test
    fun `clamp keeps the part of a selection that still exists`() {
        val sel = TermSelection(cell(4L, 3), cell(9L, 7))
        val clamped = SelectionModel.clamp(sel, oldest = 6L, newest = 20L, cols = 10)
        assertNotNull(clamped)
        assertEquals(cell(6L, 0), clamped!!.start)
        assertEquals(cell(9L, 7), clamped.end)
    }

    @Test
    fun `clamp drops a selection entirely outside the buffer`() {
        val sel = TermSelection(cell(1L, 0), cell(2L, 0))
        assertNull(SelectionModel.clamp(sel, oldest = 5L, newest = 9L, cols = 10))
    }

    @Test
    fun `interior rows are full width and end rows stop at their own column`() {
        val sel = TermSelection(cell(10L, 4), cell(12L, 2))
        assertEquals(4..9, SelectionModel.spanOnRow(sel, 10L, cols = 10))
        assertEquals(0..9, SelectionModel.spanOnRow(sel, 11L, cols = 10))
        assertEquals(0..2, SelectionModel.spanOnRow(sel, 12L, cols = 10))
        assertNull(SelectionModel.spanOnRow(sel, 13L, cols = 10))
    }

    @Test
    fun `a touch maps through the sub-row offset and clamps to the grid`() {
        // cellW 10, cellH 20. Content is translated up by 5px, so y=0 is content 5px:
        // screen row 0. y=20 is content 25px: screen row 1.
        val a = SelectionModel.cellAt(
            x = 25f, y = 0f, cellW = 10, cellH = 20, scrollSubPx = 5f,
            cols = 80, rows = 24, topRow = -3, historyBase = 100L, scrollbackCount = 40
        )
        assertEquals(SelCell(100L + 40 - 3 + 0, 2), a)
        val b = SelectionModel.cellAt(
            x = 25f, y = 20f, cellW = 10, cellH = 20, scrollSubPx = 5f,
            cols = 80, rows = 24, topRow = -3, historyBase = 100L, scrollbackCount = 40
        )
        assertEquals(SelCell(100L + 40 - 3 + 1, 2), b)
        // Far outside the view clamps to the last column / row.
        val c = SelectionModel.cellAt(
            x = 9999f, y = 9999f, cellW = 10, cellH = 20, scrollSubPx = 0f,
            cols = 80, rows = 24, topRow = 0, historyBase = 0L, scrollbackCount = 0
        )
        assertEquals(SelCell(23L, 79), c)
    }

    @Test
    fun `screen row math maps a buffer row back to its screen row`() {
        val snap = TerminalEmulator(cols = 10, rows = 3, scrollbackLimit = 50).also {
            it.feed("a\r\nb\r\nc\r\nd\r\ne")
        }.snapshot()
        // Two history rows (a, b); live rows are c, d, e.
        assertEquals(2, snap.scrollbackCount)
        assertEquals(0, SelectionModel.screenRowOf(snap.absRowOf(0), snap.historyBase, snap.scrollbackCount, topRow = 0))
        assertEquals(2, SelectionModel.screenRowOf(snap.absRowOf(2), snap.historyBase, snap.scrollbackCount, topRow = 0))
        // Scrolled two rows up into history: the first history row is now on screen.
        assertEquals(0, SelectionModel.screenRowOf(snap.absRowOf(-2), snap.historyBase, snap.scrollbackCount, topRow = -2))
        assertEquals(-1, SelectionModel.screenRowOf(snap.absRowOf(-3), snap.historyBase, snap.scrollbackCount, topRow = -2))
    }

    @Test
    fun `the edge band auto-scrolls only near the top and bottom`() {
        val h = 600f
        val cellH = 20
        assertEquals(0f, SelectionModel.edgeScrollPx(300f, h, cellH), 1e-4f)
        assertTrue(SelectionModel.edgeScrollPx(0f, h, cellH) < 0f)     // top: reveal older rows
        assertTrue(SelectionModel.edgeScrollPx(h, h, cellH) > 0f)      // bottom: reveal newer rows
        // Faster the closer to the edge.
        assertTrue(
            Math.abs(SelectionModel.edgeScrollPx(0f, h, cellH)) >
                Math.abs(SelectionModel.edgeScrollPx(30f, h, cellH))
        )
    }

    @Test
    fun `a handle anchor sits inside the cell it marks`() {
        val cell = cell(0L, 4)
        val start = SelectionModel.handleAnchorX(SelHandle.START, cell, cellW = 10)
        val end = SelectionModel.handleAnchorX(SelHandle.END, cell, cellW = 10)
        assertEquals(4, (start / 10f).toInt())
        assertEquals(4, (end / 10f).toInt())
        assertTrue(start > 40f && start < 50f)
        assertTrue(end > 40f && end < 50f)
    }
}
