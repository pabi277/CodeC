package com.codeci.ide

import com.codeci.ide.ui.terminal.SelectionHandle
import com.codeci.ide.ui.terminal.SelectionModel
import com.codeci.ide.ui.terminal.TerminalBuffer
import com.codeci.ide.ui.terminal.TextSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 103.1 — the terminal's selection MODEL, driven by a host test
 * instead of a thumb.
 *
 * The owner's report (*"partial text copy not working"*, 2026-10-14) had two
 * halves, and only the second half was ever visible:
 *
 * 1. the gesture layer never produced a range you could extend (long-press
 *    picked one word AND opened the menu in the same gesture) — that half is
 *    pinned by `SelectionWiringTest`, because it is about wiring;
 * 2. and even a range WOULD have copied wrong, because the extractor split
 *    every wrapped line with a newline — that half is pinned here.
 *
 * Everything below is the arithmetic of "which cells did the finger choose"
 * and "what text do those cells mean", with the soft-wrap join as the
 * headline: a 200-character URL must come out as ONE string.
 */
class SelectionModelTest {

    // ---- fixtures ---------------------------------------------------------

    private fun buffer(cols: Int, rows: Int): TerminalBuffer = TerminalBuffer(cols = cols, rows = rows)

    private fun TerminalBuffer.writeLine(text: String) {
        text.forEach { print(it.code) }
        carriageReturn()
        lineFeed()
    }

    /** A 26-character line in a 10-column buffer: 3 rows, the first two soft-wrapped. */
    private fun wrapped(): TerminalBuffer {
        val buf = buffer(cols = 10, rows = 4)
        "abcdefghijklmnopqrstuvwxyz".forEach { buf.print(it.code) }
        return buf
    }

    // ---- the word under the finger ---------------------------------------

    @Test
    fun `a path is one word, because that is what a terminal user grabs`() {
        val (start, end) = SelectionModel.wordBounds("see /data/data/com.codeci/files/home", 8)
        assertEquals("/data/data/com.codeci/files/home", "see /data/data/com.codeci/files/home".substring(start, end + 1))
    }

    @Test
    fun `a flag is one word`() {
        // column 13 is the 'y' of "-y" (12 is the dash).
        val (start, end) = SelectionModel.wordBounds("pkg install -y git", 13)
        assertEquals("-y", "pkg install -y git".substring(start, end + 1))
    }

    @Test
    fun `a space selects just itself, so the drag has an anchor to grow from`() {
        val (start, end) = SelectionModel.wordBounds("a b", 1)
        assertEquals(1, start)
        assertEquals(1, end)
    }

    @Test
    fun `an out-of-range column clamps into the line instead of throwing`() {
        // Column 99 lands on the last column, and the word there is the whole
        // "abc": clamping first is what keeps a long-press on the empty area
        // past the end of a line from crashing.
        val (start, end) = SelectionModel.wordBounds("abc", 99)
        assertEquals(0, start)
        assertEquals(2, end)
        assertEquals("abc", "abc".substring(start, end + 1))
    }

    @Test
    fun `long-press selects the word under the finger`() {
        val buf = buffer(cols = 20, rows = 3)
        buf.writeLine("hello world again")
        val snap = buf.snapshot()
        // column 7 is the 'o' of "world".
        val sel = SelectionModel.beginWord(snap, 7, 0)!!
        assertEquals(TextSelection(6, 0, 10, 0), sel)
        assertEquals("world", SelectionModel.extract(snap, sel))
    }

    @Test
    fun `long-press on a row that does not exist produces no selection`() {
        val buf = buffer(cols = 20, rows = 3)
        buf.writeLine("hello")
        assertNull(SelectionModel.beginWord(buf.snapshot(), 0, -4))
    }

    // ---- dragging the handles --------------------------------------------

    @Test
    fun `dragging the end handle grows the range and keeps the anchor`() {
        val buf = buffer(cols = 20, rows = 3)
        buf.writeLine("hello world again")
        val snap = buf.snapshot()
        val sel = SelectionModel.beginWord(snap, 7, 0)!!
        val moved = SelectionModel.drag(sel, SelectionHandle.END, 15, 0, 20, -snap.scrollbackCount, 2)
        assertEquals(SelectionHandle.END, moved.handle)
        assertEquals("world agai", SelectionModel.extract(snap, moved.selection))
    }

    @Test
    fun `dragging the end handle back over the anchor flips which edge the finger holds`() {
        val buf = buffer(cols = 20, rows = 3)
        buf.writeLine("hello world again")
        val snap = buf.snapshot()
        val sel = SelectionModel.beginWord(snap, 7, 0)!! // columns 6..10
        // Drag the END handle to column 1 — before the start edge.
        val moved = SelectionModel.drag(sel, SelectionHandle.END, 1, 0, 20, -snap.scrollbackCount, 2)
        assertEquals(
            "the finger keeps the edge it is dragging, now the START",
            SelectionHandle.START,
            moved.handle
        )
        assertEquals(TextSelection(1, 0, 6, 0), moved.selection)
    }

    @Test
    fun `dragging the start handle moves the start, not the end`() {
        val buf = buffer(cols = 20, rows = 3)
        buf.writeLine("hello world again")
        val snap = buf.snapshot()
        val sel = SelectionModel.beginWord(snap, 7, 0)!!
        val moved = SelectionModel.drag(sel, SelectionHandle.START, 0, 0, 20, -snap.scrollbackCount, 2)
        assertEquals(SelectionHandle.START, moved.handle)
        assertEquals("hello world", SelectionModel.extract(snap, moved.selection))
    }

    @Test
    fun `a drag is clamped to the buffer, never past the last column`() {
        val buf = buffer(cols = 20, rows = 3)
        buf.writeLine("hello world again")
        val snap = buf.snapshot()
        val sel = SelectionModel.beginWord(snap, 7, 0)!!
        val moved = SelectionModel.drag(sel, SelectionHandle.END, 999, 999, 20, -snap.scrollbackCount, 2)
        assertEquals(19, moved.selection.x2)
        assertEquals(2, moved.selection.y2)
    }

    // ---- staying on the same text while output streams --------------------

    @Test
    fun `output shifts the selection so it keeps its own text`() {
        val buf = buffer(cols = 10, rows = 3)
        buf.writeLine("one")
        buf.writeLine("two")
        buf.writeLine("three")
        buf.writeLine("four")
        val before = buf.snapshot()
        val sel = TextSelection(0, 0, 2, 0) // "thr" of "three", on the live screen
        assertEquals("thr", SelectionModel.extract(before, sel))

        buf.writeLine("five")
        buf.writeLine("six")
        val after = buf.snapshot()

        val moved = SelectionModel.shift(
            sel,
            (after.scrolledRows - before.scrolledRows).toInt(),
            -after.scrollbackCount
        )!!

        assertEquals("the shifted selection still holds its own text", "thr", SelectionModel.extract(after, moved))
        assertNotEquals(
            "without the shift the same coordinates point at different text — the bug",
            "thr",
            SelectionModel.extract(after, sel)
        )
    }

    @Test
    fun `a selection whose text has scrolled out of the buffer is dropped, not parked`() {
        // minRow -2: shifting by two rows pushes a row-0 selection out of range.
        assertNull(SelectionModel.shift(TextSelection(0, -1, 3, -1), 2, -2))
    }

    @Test
    fun `a shift of zero is the identity`() {
        val sel = TextSelection(1, -5, 3, -2)
        assertEquals(sel, SelectionModel.shift(sel, 0, -10))
    }

    @Test
    fun `the scroll counter keeps counting after the scrollback cap is reached`() {
        // This is the whole reason `scrolledRows` exists: `scrollbackCount`
        // stops moving at the cap, but the text keeps sliding, and a
        // selection anchored in extended-Y coordinates has to follow it.
        val buf = TerminalBuffer(cols = 10, rows = 2, scrollbackLimit = 2)
        repeat(6) { buf.writeLine("line$it") }
        val snap = buf.snapshot()
        assertEquals(2, snap.scrollbackCount)
        assertTrue(
            "the counter must outlive the cap (count=${snap.scrolledRows}, scrollback=${snap.scrollbackCount})",
            snap.scrolledRows > snap.scrollbackCount
        )
    }

    @Test
    fun `a reset starts a new coordinate epoch`() {
        val buf = buffer(cols = 10, rows = 2)
        repeat(3) { buf.writeLine("line$it") }
        assertTrue(buf.snapshot().scrolledRows > 0L)
        buf.reset()
        assertEquals(0L, buf.snapshot().scrolledRows)
    }

    // ---- the copy rule ----------------------------------------------------

    @Test
    fun `a wrapped line copies as ONE line, not two`() {
        // The owner's case: a long command or URL broken across rows must
        // paste back whole. This is the join rule the old extractor lacked.
        val snap = wrapped().snapshot()
        val text = SelectionModel.extract(snap, TextSelection(0, 0, 9, 2))
        assertEquals("abcdefghijklmnopqrstuvwxyz", text)
        assertFalse(text.contains('\n'))
    }

    @Test
    fun `the buffer really flags the wrapped rows the join rule depends on`() {
        val snap = wrapped().snapshot()
        assertTrue(snap.lineAt(0)!!.wrapped)
        assertTrue(snap.lineAt(1)!!.wrapped)
        assertFalse(snap.lineAt(2)!!.wrapped)
    }

    @Test
    fun `a selection that starts inside a wrapped line copies from there`() {
        val snap = wrapped().snapshot()
        assertEquals("ijklm", SelectionModel.extract(snap, TextSelection(8, 0, 2, 1)))
    }

    @Test
    fun `hard lines keep their newline`() {
        val buf = buffer(cols = 20, rows = 3)
        buf.writeLine("hello")
        buf.writeLine("world")
        val snap = buf.snapshot()
        assertEquals("hello\nworld", SelectionModel.extract(snap, TextSelection(0, 0, 4, 1)))
    }

    @Test
    fun `trailing blanks are trimmed at the end of a logical line, never inside one`() {
        val buf = buffer(cols = 6, rows = 3)
        "ab".forEach { buf.print(it.code) }
        repeat(4) { buf.print(' '.code) } // row 0 = "ab    ", then it wraps
        "cde".forEach { buf.print(it.code) }
        val snap = buf.snapshot()
        assertTrue(snap.lineAt(0)!!.wrapped)
        // The interior spaces belong to the text; only the logical line's
        // final blanks go.
        assertEquals("ab    cde", SelectionModel.extract(snap, TextSelection(0, 0, 2, 1)))
    }

    @Test
    fun `trailing blanks of an unwrapped row are trimmed`() {
        val buf = buffer(cols = 20, rows = 3)
        buf.writeLine("hello")
        val snap = buf.snapshot()
        assertEquals("hello", SelectionModel.extract(snap, TextSelection(0, 0, 19, 0)))
    }

    @Test
    fun `a selection that ends before it starts means the same thing backwards`() {
        val buf = buffer(cols = 20, rows = 3)
        buf.writeLine("hello world")
        val snap = buf.snapshot()
        val forward = SelectionModel.extract(snap, TextSelection(0, 0, 4, 0))
        val backward = SelectionModel.extract(snap, TextSelection(4, 0, 0, 0))
        assertEquals("hello", forward)
        assertEquals(forward, backward)
    }

    @Test
    fun `an empty row inside a selection is an empty line, not a swallowed one`() {
        val buf = buffer(cols = 20, rows = 4)
        buf.writeLine("first")
        buf.writeLine("")
        buf.writeLine("third")
        val snap = buf.snapshot()
        assertEquals("first\n\nthird", SelectionModel.extract(snap, TextSelection(0, 0, 4, 2)))
    }

    @Test
    fun `clamp forces a range inside the buffer`() {
        val buf = buffer(cols = 10, rows = 3)
        val snap = buf.snapshot()
        val clamped = SelectionModel.clamp(TextSelection(-5, -99, 999, 999), 10, -snap.scrollbackCount, 2)
        assertTrue(clamped.x1 >= 0)
        assertTrue(clamped.x2 <= 9)
        assertTrue(clamped.y1 >= -snap.scrollbackCount)
        assertTrue(clamped.y2 <= 2)
        assertTrue(clamped.y1 <= clamped.y2)
    }
}
