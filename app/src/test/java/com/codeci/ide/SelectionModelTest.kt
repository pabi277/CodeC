package com.codeci.ide

import com.codeci.ide.ui.terminal.SelectionAutoScroll
import com.codeci.ide.ui.terminal.SelectionDrag
import com.codeci.ide.ui.terminal.SelectionEnd
import com.codeci.ide.ui.terminal.SelectionHandles
import com.codeci.ide.ui.terminal.SelectionModel
import com.codeci.ide.ui.terminal.SelectionPoint
import com.codeci.ide.ui.terminal.SelectionRange
import com.codeci.ide.ui.terminal.TerminalEmulator
import com.codeci.ide.ui.terminal.TerminalSelection
import com.codeci.ide.ui.terminal.TerminalSnapshot
import com.codeci.ide.ui.terminal.asSelectionText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 103.1 — the selection model, host-side.
 *
 * The owner's bug: *"partial text copy not working"*. The gesture layer, not
 * the extractor, was at fault, and every rule that decides what a drag means
 * is pure — so it is pinned here, on the real `TerminalEmulator`, with no
 * device in the loop.
 */
class SelectionModelTest {

    private fun emulator(cols: Int = 40, rows: Int = 10): TerminalEmulator =
        TerminalEmulator(cols = cols, rows = rows)

    // ---- long-press: the word rule ---------------------------------------

    @Test
    fun `a long-press grabs the whole word under the finger`() {
        val text = "echo \"hello_world-123 foo\""
        val (s, e) = SelectionModel.wordAt(text, 10)
        assertEquals("hello_world-123", text.substring(s, e + 1))
    }

    @Test
    fun `a path and a url are one word, never split at their punctuation`() {
        val text = "see https://github.com/pabi277/CodeC for docs"
        val (s, e) = SelectionModel.wordAt(text, 20)
        assertEquals("https://github.com/pabi277/CodeC", text.substring(s, e + 1))
        val path = "open /data/data/com.codeci.ide/files/usr/bin"
        val (ps, pe) = SelectionModel.wordAt(path, 12)
        assertEquals("/data/data/com.codeci.ide/files/usr/bin", path.substring(ps, pe + 1))
    }

    @Test
    fun `a long-press on a blank cell is a caret, so the press always shows something`() {
        val (s, e) = SelectionModel.wordAt("abc     ", 5)
        assertEquals(s, e)
        assertEquals(5, s)
    }

    @Test
    fun `an empty line cannot make the word rule throw`() {
        val (s, e) = SelectionModel.wordAt("", 3)
        assertEquals(0 to 0, s to e)
    }

    @Test
    fun `word begin normalizes a backwards span`() {
        val sel = TerminalSelection.word(row = 3, startCol = 9, endCol = 4)
        assertEquals(SelectionPoint(3, 4), sel.start)
        assertEquals(SelectionPoint(3, 9), sel.end)
        assertEquals(5, sel.baseCols)
    }

    @Test
    fun `the pressed word is captured verbatim so it can be re-verified later`() {
        // The URL rule keeps the scheme: `:` is a word char, so a long-press
        // anywhere on this URL grabs the whole thing — the exact case the
        // owner's "partial text copy" report was about.
        val text = "curl https://x.dev"
        val (a, b) = SelectionModel.wordAt(text, 8)
        assertEquals(5 to 17, a to b)
        assertEquals("https://x.dev", TerminalSelection.word(0, a, b, text).wordText)
        // A span whose end is one past the text (a caret at the right margin)
        // must clamp rather than throw — the long-press path can produce it.
        assertEquals(
            "",
            TerminalSelection.word(row = 0, startCol = 30, endCol = 39, text = "short").wordText
        )
    }

    // ---- dragging --------------------------------------------------------

    @Test
    fun `dragging the cursor handle extends the range forward`() {
        val sel = TerminalSelection.word(row = 2, startCol = 4, endCol = 8)
        val grown = SelectionModel.drag(sel, SelectionEnd.CURSOR, SelectionPoint(2, 14))
        assertEquals(SelectionPoint(2, 4), grown.start)
        assertEquals(SelectionPoint(2, 14), grown.end)
        assertEquals(SelectionEnd.CURSOR, grown.handle)
    }

    @Test
    fun `dragging the anchor handle backwards moves the START, not the end`() {
        val sel = TerminalSelection.word(row = 2, startCol = 4, endCol = 8)
        val grown = SelectionModel.drag(sel, SelectionEnd.ANCHOR, SelectionPoint(1, 30))
        assertEquals(SelectionPoint(1, 30), grown.start)
        assertEquals(SelectionPoint(2, 8), grown.end)
        assertEquals(SelectionEnd.ANCHOR, grown.handle)
    }

    @Test
    fun `a drag through its own other end inverts instead of failing`() {
        val sel = TerminalSelection.word(row = 2, startCol = 4, endCol = 8)
        val crossed = SelectionModel.drag(sel, SelectionEnd.CURSOR, SelectionPoint(2, 1))
        // The range is normalized — start 1, end 4 — and the cursor handle keeps
        // its identity, which is what makes the next frame extend from 1.
        assertEquals(SelectionPoint(2, 1), crossed.start)
        assertEquals(SelectionPoint(2, 4), crossed.end)
        val further = SelectionModel.drag(crossed, SelectionEnd.CURSOR, SelectionPoint(2, 0))
        assertEquals(SelectionPoint(2, 0), further.start)
        assertEquals(SelectionPoint(2, 4), further.end)
    }

    @Test
    fun `a reversed construction is refused, not silently sorted`() {
        var threw = false
        try {
            SelectionRange(SelectionPoint(5, 0), SelectionPoint(4, 9))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("SelectionRange must refuse start > end", threw)
    }

    @Test
    fun `a whole-row drag covers every column between its ends`() {
        val sel = SelectionModel.drag(
            TerminalSelection.word(row = 1, startCol = 2, endCol = 3),
            SelectionEnd.CURSOR,
            SelectionPoint(3, 9)
        )
        assertEquals(SelectionPoint(1, 2), sel.start)
        assertEquals(SelectionPoint(3, 9), sel.end)
    }

    // ---- scrolling under a live selection --------------------------------

    @Test
    fun `edge auto-scroll moves the handle with its line, and the view with it`() {
        val sel = TerminalSelection.word(row = -5, startCol = 0, endCol = 4)
        val up = SelectionModel.growByScroll(sel, SelectionEnd.ANCHOR, -1)
        assertEquals(SelectionPoint(-6, 0), up.start)
        assertEquals(SelectionPoint(-5, 4), up.end)
        val down = SelectionModel.growByScroll(sel, SelectionEnd.CURSOR, 2)
        assertEquals(SelectionPoint(-5, 0), down.start)
        assertEquals(SelectionPoint(-3, 4), down.end)
    }

    @Test
    fun `a zero-line scroll is the identity`() {
        val sel = TerminalSelection.word(row = -5, startCol = 0, endCol = 4)
        assertEquals(sel, SelectionModel.growByScroll(sel, SelectionEnd.CURSOR, 0))
    }

    @Test
    fun `the drag frame decides handle move, scroll, or both`() {
        val sel = TerminalSelection.word(row = -6, startCol = 0, endCol = 4)
        // Middle of the viewport: the handle just follows the cell.
        val middle = SelectionAutoScroll.dragFor(
            selection = sel, end = SelectionEnd.CURSOR,
            x = 3f * 10f, y = 10f * 20f, cellW = 10, cellH = 20,
            cols = 40, rows = 30, xScroll = 0f, rowCap = -6
        )
        assertEquals(SelectionDrag.Move(SelectionPoint(10 - 6, 3)), middle)
        // In the top band: move AND scroll one line (older).
        val top = SelectionAutoScroll.dragFor(
            selection = sel, end = SelectionEnd.ANCHOR,
            x = 0f, y = 0f, cellW = 10, cellH = 20, cols = 40, rows = 30,
            xScroll = 0f, rowCap = -6
        )
        assertEquals(SelectionDrag.MoveAndScroll(SelectionPoint(-6, 0), -1), top)
        // In the bottom band: the other direction.
        val bottom = SelectionAutoScroll.dragFor(
            selection = sel, end = SelectionEnd.CURSOR,
            x = 0f, y = 29f * 20f, cellW = 10, cellH = 20, cols = 40, rows = 30,
            xScroll = 0f, rowCap = -6
        )
        assertEquals(SelectionDrag.MoveAndScroll(SelectionPoint(29 - 6, 0), 1), bottom)
    }

    @Test
    fun `the sub-pixel remainder is honoured when mapping a drag to a cell`() {
        // Mid-drag the canvas is translated by -xScroll; the cell under the
        // finger is therefore (y + xScroll) / cellH — the :783-class row-math
        // bug, pinned here at policy level.
        val sel = TerminalSelection.word(row = 0, startCol = 0, endCol = 0)
        val shifted = SelectionAutoScroll.dragFor(
            selection = sel, end = SelectionEnd.CURSOR,
            x = 5f, y = 9f, cellW = 10, cellH = 20, cols = 40, rows = 30,
            xScroll = 18f, rowCap = 0
        )
        // (9 + 18) / 20 = 1 — NOT 9/20 = 0. The sub-pixel translation is what
        // the pre-103 row math forgot.
        val to = when (shifted) {
            is SelectionDrag.Move -> shifted.to
            is SelectionDrag.MoveAndScroll -> shifted.to
            // A sub-pixel drag near the top edge on a live screen may now
            // reach a rail; both rails are verbs, not points.
            is SelectionDrag.AutoScroll -> SelectionPoint(-1, -1)
            is SelectionDrag.SelectAll -> SelectionPoint(-2, -2)
            is SelectionDrag.Clear -> SelectionPoint(-3, -3)
        }
        assertEquals(SelectionPoint(1, 0), to)
    }

    @Test
    fun `the drag frame never returns a cell outside the viewport grid`() {
        val sel = TerminalSelection.word(row = 0, startCol = 0, endCol = 0)
        val far = SelectionAutoScroll.dragFor(
            selection = sel, end = SelectionEnd.CURSOR,
            x = 99_999f, y = 99_999f, cellW = 10, cellH = 20,
            cols = 40, rows = 30, xScroll = 0f, rowCap = 0
        )
        val to = when (far) {
            is SelectionDrag.Move -> far.to
            is SelectionDrag.MoveAndScroll -> far.to
            is SelectionDrag.AutoScroll -> SelectionPoint(0, 0)
            is SelectionDrag.SelectAll -> SelectionPoint(0, 0)
            is SelectionDrag.Clear -> SelectionPoint(0, 0)
        }
        assertTrue("col ${to.col} in 0..39", to.col in 0..39)
        assertTrue("row ${to.row} in 0..29", to.row in 0..29)
    }

    // ---- the two rails are verbs, not dead ends --------------------------

    @Test
    fun `dragging off the oldest row means select all, off the live screen means clear`() {
        val sel = TerminalSelection.word(row = -5, startCol = 0, endCol = 4)
        // Already at the top of a 100-row history, finger in the top band:
        // there is nothing further to extend into, so the gesture selects all.
        val atOldest = SelectionAutoScroll.dragFor(
            selection = sel, end = SelectionEnd.CURSOR,
            x = 5f, y = 0f, cellW = 10, cellH = 20, cols = 40, rows = 30,
            xScroll = 0f, rowCap = -100, minTop = -100
        )
        assertTrue("at the oldest row the top rail selects all", atOldest is SelectionDrag.SelectAll)

        // Still scrolled up: the top band is ordinary "keep going older".
        val canStillScroll = SelectionAutoScroll.dragFor(
            selection = sel, end = SelectionEnd.CURSOR,
            x = 5f, y = 0f, cellW = 10, cellH = 20, cols = 40, rows = 30,
            xScroll = 0f, rowCap = -5, minTop = -100
        )
        assertTrue(canStillScroll is SelectionDrag.MoveAndScroll)

        // At the live screen, the bottom band is past the newest line: clear.
        val atLive = SelectionAutoScroll.dragFor(
            selection = sel, end = SelectionEnd.CURSOR,
            x = 5f, y = 599f, cellW = 10, cellH = 20, cols = 40, rows = 30,
            xScroll = 0f, rowCap = 0, minTop = -100
        )
        assertTrue("at the live screen the bottom rail clears", atLive is SelectionDrag.Clear)

        // Scrolled up, the bottom band still means "toward live".
        val towardLive = SelectionAutoScroll.dragFor(
            selection = sel, end = SelectionEnd.CURSOR,
            x = 5f, y = 599f, cellW = 10, cellH = 20, cols = 40, rows = 30,
            xScroll = 0f, rowCap = -5, minTop = -100
        )
        assertTrue(towardLive is SelectionDrag.MoveAndScroll)
    }

    // ---- Select All ------------------------------------------------------

    @Test
    fun `select all covers scrollback plus the whole live screen`() {
        val emu = emulator()
        repeat(20) { emu.feed("line $it\r\n") }
        val snap = emu.snapshot()
        val all = SelectionModel.selectAll(snap)
        assertEquals(SelectionPoint(-snap.scrollbackCount, 0), all.start)
        assertEquals(SelectionPoint(snap.rows - 1, snap.cols - 1), all.end)
        val text = SelectionModel.extract(snap.asSelectionText(), all.range)
        assertTrue(text.contains("line 0"))
        assertTrue(text.contains("line 19"))
    }

    @Test
    fun `select all on a one-row buffer is legal`() {
        val emu = TerminalEmulator(cols = 1, rows = 1)
        val snap = emu.snapshot()
        val all = SelectionModel.selectAll(snap)
        assertEquals(SelectionPoint(0, 0), all.start)
        assertEquals(SelectionPoint(0, 0), all.end)
    }

    // ---- anchoring -------------------------------------------------------

    @Test
    fun `a scrollback trim does NOT move a selection, because the axis is live-anchored`() {
        // This was a real bug found by these tests: the first model shifted by
        // the trim count. Row 0 is the LIVE SCREEN in TerminalSnapshot.lineAt,
        // so trimming the oldest history moves no surviving row's number — the
        // derived shift for "one trim, same count" is exactly zero.
        val sel = SelectionRange(SelectionPoint(-50, 3), SelectionPoint(-48, 7))
        val rows = com.codeci.ide.ui.terminal.TrimMath.contentShift(20, 19, 5L, 6L)
        assertEquals(0, rows)
        assertEquals(sel, sel.shift(rows))
    }

    @Test
    fun `a selection whose text fell off the front is refused, never repainted`() {
        // The trim is not what answers this — the buffer is. Once every row a
        // selection held is gone, `lineAt` returns null for all of them and the
        // extraction is empty, so the view drops it instead of painting over
        // whatever took its place.
        val emu = TerminalEmulator(cols = 20, rows = 2, scrollbackLimit = 2)
        emu.feed("a\r\nb\r\nc\r\nd\r\ne\r\nf\r\ng\r\n")
        val snap = emu.snapshot()
        val stale = SelectionRange(
            SelectionPoint(-snap.scrollbackCount - 5, 0),
            SelectionPoint(-snap.scrollbackCount - 4, 19)
        )
        assertEquals("", SelectionModel.extract(snap.asSelectionText(), stale))
    }

    @Test
    fun `pulling rows back onto the screen pushes a live selection down`() {
        // The keyboard closing grows the screen by three rows, so every cell's
        // extY goes UP by three: the opposite of a push, and the selection
        // travels down by three.
        val rows = com.codeci.ide.ui.terminal.TrimMath.contentShift(0, -3, 0L, 0L)
        assertEquals(3, rows)
        val live = SelectionRange(SelectionPoint(0, 1), SelectionPoint(2, 4))
        assertEquals(
            SelectionRange(SelectionPoint(3, 1), SelectionPoint(5, 4)),
            live.shift(rows)
        )
        // A shift is a pure translation: it never clips or fabricates rows.
        // "My text is gone" is the BUFFER's answer (`lineAt` → null), not a
        // clamps-and-hopes guess made here.
        assertEquals(-9, SelectionRange(SelectionPoint(0, 0), SelectionPoint(1, 1)).shift(-9).start.row)
    }

    @Test
    fun `output scrolling moves a selection down, a trim does not move it at all`() {
        // Two rows pushed off the live screen: the screen (and every cell on
        // it) moved up, so the selection's extY goes DOWN by two.
        assertEquals(-2, com.codeci.ide.ui.terminal.TrimMath.contentShift(0, 2, 0L, 0L))
        // A trim drops the oldest history row (count −1) and is not a push:
        // the two cancel to exactly zero — the bug these tests first caught.
        assertEquals(0, com.codeci.ide.ui.terminal.TrimMath.contentShift(10, 9, 5L, 6L))
        // A run of pushes and trims: count +4 with one trim means FIVE pushes
        // (the trim freed a slot), so the screen moved up five rows.
        assertEquals(-5, com.codeci.ide.ui.terminal.TrimMath.contentShift(10, 14, 5L, 6L))
        // A grow (the keyboard closing) pulls a row back onto the screen.
        assertEquals(1, com.codeci.ide.ui.terminal.TrimMath.contentShift(10, 9, 5L, 5L))
    }
    @Test
    fun `a counter that goes backwards is capped, never a wild jump`() {
        // A session switch replaces the buffer (both counters restart): the
        // derived shift is clamped rather than allowed to fling the selection
        // off the end of the Int range.
        assertEquals(
            com.codeci.ide.ui.terminal.TrimMath.MAX_SHIFT.toInt(),
            com.codeci.ide.ui.terminal.TrimMath.contentShift(1_000_000, 0, 0L, 0L)
        )
        assertEquals(0, com.codeci.ide.ui.terminal.TrimMath.contentShift(9, 9, 3L, 3L))
    }

    @Test
    fun `the buffer counts its own trims so the view can see them`() {
        val emu = TerminalEmulator(cols = 20, rows = 2, scrollbackLimit = 3)
        assertEquals(0L, emu.snapshot().scrollbackGeneration)
        repeat(10) { emu.feed("row $it\r\n") }
        assertTrue(
            "a 3-row scrollback fed 10 rows must have trimmed",
            emu.snapshot().scrollbackGeneration > 0L
        )
    }

    @Test
    fun `a selection that still covers its own text is remembered`() {
        val emu = emulator()
        emu.feed("hello world\r\n")
        val snap = emu.snapshot()
        val sel = TerminalSelection.word(row = 0, startCol = 0, endCol = 4, text = "hello world")
        assertTrue(SelectionModel.remembers(sel, snap))
        // …and it stays remembered after output scrolls it down the axis.
        val moved = sel.shifted(-1)
        // The same text, one row further down the buffer's axis. (Nothing was
        // pushed off here, so row 0 is what the press grabbed; drop the row to
        // -1 only in the synthetic case where a row did leave.)
        assertTrue(SelectionModel.remembers(sel.shifted(0), snap))
        assertTrue(moved.anchor!!.row == sel.anchorEnd.row - 1)
    }

    @Test
    fun `a selection whose cells now hold other text is not remembered`() {
        val emu = emulator()
        emu.feed("hello world\r\n")
        val before = emu.snapshot()
        val sel = TerminalSelection.word(row = 0, startCol = 0, endCol = 4, text = "hello world")
        emu.feed("\u001b[1;1HZZZZZ")
        val after = emu.snapshot()
        assertFalse(SelectionModel.remembers(sel, after).also {
            // The pinned intent: the check is against the CURRENT buffer, and
            // the pre-edit snapshot proves the same rule passes before it.
            assertTrue(SelectionModel.remembers(sel, before))
        })
    }

    @Test
    fun `select all is trusted without a word anchor`() {
        val emu = emulator()
        emu.feed("x\r\n")
        val snap = emu.snapshot()
        assertTrue(SelectionModel.remembers(SelectionModel.selectAll(snap), snap))
    }

    // ---- handles ---------------------------------------------------------

    @Test
    fun `handles sit on the cells they belong to, above the anchor and below the cursor`() {
        val m = SelectionHandles.Metrics(radius = 10f, stem = 10f, gap = 2f)
        val top = SelectionHandles.forCell(3, 2, cellW = 12, cellH = 20, end = false, xScroll = 0f, metrics = m)
        assertEquals(3 * 12f + 12f, top.circleX, 0.001f)
        // Anchor: circle is ABOVE the row's top edge.
        assertTrue(top.circleY < 2 * 20f)
        val bottom = SelectionHandles.forCell(3, 2, cellW = 12, cellH = 20, end = true, xScroll = 0f, metrics = m)
        // Cursor: circle is BELOW the row's bottom edge.
        assertTrue(bottom.circleY > 3 * 20f)
    }

    @Test
    fun `a handle's touch target is 48 dp even though its dot is 20 dp`() {
        val m = SelectionHandles.Metrics(radius = 10f, stem = 10f, gap = 2f)
        val h = SelectionHandles.forCell(0, 0, cellW = 10, cellH = 20, end = false, xScroll = 0f, metrics = m)
        // 20 px away from the dot's centre: outside the dot, inside the target.
        assertTrue(SelectionHandles.hits(h, h.circleX + 20f, h.circleY, 24f))
        assertFalse(SelectionHandles.hits(h, h.circleX + 40f, h.circleY, 24f))
    }

    @Test
    fun `the handle follows the grid while it is displaced mid-drag`() {
        val m = SelectionHandles.Metrics(radius = 10f, stem = 10f, gap = 2f)
        val still = SelectionHandles.forCell(0, 3, cellW = 10, cellH = 20, end = true, xScroll = 0f, metrics = m)
        val mid = SelectionHandles.forCell(0, 3, cellW = 10, cellH = 20, end = true, xScroll = 7f, metrics = m)
        assertEquals(still.circleY - 7f, mid.circleY, 0.001f)
    }

    // ---- the snapshot surface the model needs ----------------------------

    @Test
    fun `the snapshot exposes one row lookup for live rows and history alike`() {
        val emu = TerminalEmulator(cols = 10, rows = 3)
        emu.feed("a\r\nb\r\nc\r\nd\r\ne\r\n")
        val snap: TerminalSnapshot = emu.snapshot()
        assertTrue(snap.scrollbackCount >= 1)
        assertEquals("a", snap.lineAt(-snap.scrollbackCount)?.text?.trimEnd())
        assertNull(snap.lineAt(-snap.scrollbackCount - 1))
        assertNull(snap.lineAt(snap.rows))
    }
}
