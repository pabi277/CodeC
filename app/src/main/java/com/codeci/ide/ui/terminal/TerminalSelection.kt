package com.codeci.ide.ui.terminal

/**
 * Phase 103.1 — the terminal's selection model, pure Kotlin, no Android.
 *
 * **Why this exists (the owner's bug, 2026-10-14):** *"partial text copy not
 * working"*. Reading the view before writing anything: `TerminalEmulatorView`
 * long-pressed *one word* and opened the popup in the same gesture, so a drag
 * had nothing to extend; there were no handles, no edge scroll, and the
 * toolbar Copy fell back to the whole transcript when no selection produced
 * text. The gesture layer — not the extractor — was the bug.
 *
 * Termux's behaviour was the behavioural spec (read source-level in the
 * 2026-10-14 audit, GPL-3.0 — behaviour only, never code): two draggable
 * handles, selection re-anchored while output scrolls, and a copy that
 * rejoins soft-wrapped rows. This file is the state machine behind that:
 *
 * - **Buffer-absolute cells.** A [SelectionPoint] is `(row, col)` on the
 *   same extended-y axis [TerminalSnapshot.lineAt] uses (0 = the first live
 *   row, negative = history), so the model never needs the view's scroll
 *   offset to be correct.
 * - **Anchoring.** [SelectionRange.shift] translates a selection by the
 *   amount [TrimMath.contentShift] derives from two snapshots, so the same
 *   text keeps its cells while output scrolls under it. The axis makes two
 *   buffer events look alike and behave oppositely (a trim moves nothing, a
 *   pushed screen row moves everything) — the derivation is written out in
 *   [TrimMath.contentShift]. A selection whose rows are all gone answers
 *   `lineAt` → null and the caller drops it rather than letting it paint over
 *   other text.
 * - **Pins.** [SelectionModel.remembers] (and the pure [remembers]) decides
 *   whether the range still covers the cells it did — the "selection survives
 *   scrolling output" law, and the guard that stops a selection from silently
 *   sliding onto new output.
 * - **Join rules.** [extract] is the copy contract: a row that ended by soft
 *   auto-wrap joins with **no** separator, a hard row keeps `\n`, trailing
 *   blanks are trimmed on hard rows only (so a wrapped URL copies unbroken and
 *   a plain row does not carry its padding).
 *
 * Deliberately NOT here: the edge auto-scroll loop and the handles' geometry
 * live in the view (they are the Android edge); the *math* they need
 * ([SelectionAutoScroll], [SelectionHandles]) is here and tested on the host.
 */
data class SelectionPoint(
    val row: Int,
    val col: Int
) : Comparable<SelectionPoint> {
    /** This cell [rows] further down the same column. */
    fun shifted(rows: Int): SelectionPoint = SelectionPoint(row + rows, col)

    override fun compareTo(other: SelectionPoint): Int {
        val byRow = row.compareTo(other.row)
        return if (byRow != 0) byRow else col.compareTo(other.col)
    }
}

/**
 * Which end of a selection a drag moves. In a non-inverted range the anchor
 * handle is the start cell and the cursor handle is the end cell; a drag that
 * crosses the other end inverts the range (see [SelectionRange]).
 */
enum class SelectionEnd { ANCHOR, CURSOR }

/**
 * An ordered, inclusive cell range (buffer-absolute rows, inclusive columns).
 *
 * Always normalized at construction: `start <= end`. A selection made
 * right-to-left or bottom-up, or dragged through itself by its own handle, is
 * the same range — "start" and "end" are geometry, not gesture direction.
 */
data class SelectionRange(val start: SelectionPoint, val end: SelectionPoint) {

    init {
        require(start <= end) { "SelectionRange must be normalized: $start > $end" }
    }

    /** A single-cell range — what a long-press puts on screen before word growth. */
    fun isCaret(): Boolean = start == end

    /**
     * The same range, [rows] further down the buffer's axis (negative = up).
     *
     * This is a plain translation and never fails. On this axis a cell's row is
     * `index − scrollbackCount`, which means the two buffer events move it in
     * OPPOSITE directions — [TrimMath.contentShift] derives the number, and the
     * reasoning (a trim moves nothing, a pushed screen row moves everything) is
     * recorded there. "Did my text survive?" is answered against the buffer
     * ([TerminalSnapshot.lineAt] → null), not guessed from a counter, so a
     * selection that outlived its rows is dropped by the caller rather than
     * silently clipped.
     */
    fun shift(rows: Int): SelectionRange =
        if (rows == 0) this
        else SelectionRange(start.shifted(rows), end.shifted(rows))
}

/**
 * A selection plus where it came from, so it can be dragged and checked.
 *
 * The word [anchor] is the long-pressed word's first cell and [baseCols] the
 * columns it covered when the selection was made. [remembers] compares the
 * text under the range against it: identical means the selection still covers
 * its own text even though rows moved under it (new output appended, scrollback
 * trimmed) — which is exactly the "selection stays on its text" law. A
 * selection the user grew past the word has nothing to compare, and is trusted
 * to its [SelectionRange.shift] bookkeeping alone.
 */
data class TerminalSelection(
    val range: SelectionRange,
    val anchor: SelectionPoint? = null,
    val baseCols: Int = 0,
    /** A drag grabbed one of the handles: a long-press or Select All clears it. */
    val handle: SelectionEnd? = null,
    /**
     * The two LOGICAL ends. They exist separately from [range] so a drag that
     * crosses the other end keeps the grabbed handle at the finger (see
     * [SelectionModel.drag]); `range` is always their normalized pair.
     */
    val anchorEnd: SelectionPoint = range.start,
    val cursorEnd: SelectionPoint = range.end,
    /**
     * The exact text the press grabbed, captured once at word-begin.
     *
     * [SelectionModel.remembers] compares the cells under [anchor] against it:
     * same text ⇒ the selection still sits on its own word even though output
     * scrolled it down the axis; different ⇒ something overwrote it and the
     * selection must be dropped. Empty means "no word was captured" (Select All,
     * or a drag the user grew) — trusted, as there is nothing to contradict.
     */
    val wordText: String = ""
) {
    val start: SelectionPoint get() = range.start
    val end: SelectionPoint get() = range.end

    /**
     * The whole selection moved [rows] down the buffer's axis — the move
     * [TrimMath.contentShift] asks for when output scrolls under it. Both
     * logical ends move with the range, so a later drag still knows which was
     * which; the word [anchor] moves too, so [SelectionModel.remembers] still
     * asks about the cells the press actually grabbed.
     */
    fun shifted(rows: Int): TerminalSelection {
        if (rows == 0) return this
        return copy(
            range = range.shift(rows),
            anchor = anchor?.shifted(rows),
            anchorEnd = anchorEnd.shifted(rows),
            cursorEnd = cursorEnd.shifted(rows)
        )
    }

    /** Bottom-left corner the view draws the start handle from. */
    val anchorHandle: SelectionPoint get() = range.start

    /** Bottom-right corner the view draws the end handle from. */
    val cursorHandle: SelectionPoint get() = range.end

    companion object {
        /**
         * The word selection a long-press begins (a caret when the cell is
         * blank). [text] is the pressed row's text: its `[a, b]` slice is the
         * word, kept verbatim so a later [SelectionModel.remembers] can tell
         * "still my word" from "something else was printed here".
         */
        fun word(row: Int, startCol: Int, endCol: Int, text: String = ""): TerminalSelection {
            val a = minOf(startCol, endCol).coerceAtLeast(0)
            val b = maxOf(startCol, endCol).coerceAtLeast(a)
            val start = SelectionPoint(row, a)
            val end = SelectionPoint(row, b)
            return TerminalSelection(
                range = SelectionRange(start, end),
                anchor = start,
                baseCols = b - a,
                handle = SelectionEnd.CURSOR,
                anchorEnd = start,
                cursorEnd = end,
                wordText = if (text.isEmpty()) "" else
                    text.substring(a.coerceAtMost(text.length), (b + 1).coerceAtMost(text.length))
            )
        }
    }
}

/**
 * What one press-and-drag frame asks the view to do. Pure data so the
 * gesture loop can be read as a table instead of an if-chain.
 */
sealed interface SelectionDrag {
    /** Move the grabbed handle to this buffer-absolute cell. */
    data class Move(val to: SelectionPoint) : SelectionDrag

    /** The finger is in an edge band: scroll by [lines] (negative = up/older). */
    data class AutoScroll(val lines: Int) : SelectionDrag

    /** Move the handle AND scroll — the drag reached the viewport edge. */
    data class MoveAndScroll(val to: SelectionPoint, val lines: Int) : SelectionDrag

    /**
     * The drag was dragged off the TOP of the buffer while already showing its
     * first row: there is nothing further to extend into, so the gesture means
     * "everything" — Termux's select-all wipe.
     */
    data object SelectAll : SelectionDrag

    /**
     * The drag was dragged off the BOTTOM of the LIVE screen: past the newest
     * line there is nothing to select, so the gesture means "never mind".
     * Deliberately NOT reachable while scrolled up, where down means "toward
     * live" and is ordinary auto-scroll.
     */
    data object Clear : SelectionDrag
}

object SelectionModel {

    /**
     * Long-press resolution: the word under [col] on [text], as an inclusive
     * `(start, end)` column pair. Blank cells make a one-cell caret so the
     * user always sees the press take effect.
     */
    /**
     * The character class the word rule splits on, identical to the one the
     * terminal has used since its first long-press (`findWordBoundaries` in
     * TerminalEmulatorView): letters, digits, `_`, `-`, `.`, `/` and `:` are
     * one word, so a path or a URL is grabbed whole (a colon only appears in
     * a URL as its scheme separator, and splitting there would copy
     * `//github.com/...`).
     */
    fun isWordChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '/' || c == ':'

    fun wordAt(text: String, col: Int): Pair<Int, Int> {
        if (text.isEmpty()) return 0 to 0
        val c = col.coerceIn(0, text.length - 1)
        if (!isWordChar(text[c])) return c to c
        var start = c
        var end = c
        while (start > 0 && isWordChar(text[start - 1])) start--
        while (end < text.length - 1 && isWordChar(text[end + 1])) end++
        return start to end
    }

    /**
     * Moves [end]'s handle to [to]. Crossing the other end is allowed: the
     * range stays normalized and the handle keeps its identity (the *cursor*
     * handle stays the cursor handle), which is how a drag through itself
     * looks exactly like Termux's.
     */
    fun drag(current: TerminalSelection, end: SelectionEnd, to: SelectionPoint): TerminalSelection {
        // Track the two logical ends SEPARATELY, then normalize for the range.
        // Collapsing both ends into min/max first is what silently disarmed a
        // handle the moment a drag crossed the other end: the grabbed end must
        // stay AT the finger, even as the pair inverts.
        val anchor = if (end == SelectionEnd.ANCHOR) to else current.anchorEnd
        val cursor = if (end == SelectionEnd.CURSOR) to else current.cursorEnd
        return current.copy(
            range = SelectionRange(minOf(anchor, cursor), maxOf(anchor, cursor)),
            anchorEnd = anchor,
            cursorEnd = cursor,
            handle = end
        )
    }

    /** One line of edge auto-scroll: the grabbed handle travels with the text. */
    fun growByScroll(current: TerminalSelection, end: SelectionEnd, lines: Int): TerminalSelection {
        if (lines == 0) return current
        val moved = when (end) {
            SelectionEnd.ANCHOR -> current.anchorEnd.shifted(lines)
            SelectionEnd.CURSOR -> current.cursorEnd.shifted(lines)
        }
        return drag(current, end, moved)
    }

    /**
     * Select All: the whole buffer including scrollback, top-left to the last
     * live cell. A one-row buffer is legal (a fresh terminal).
     */
    fun selectAll(snapshot: TerminalSnapshot): TerminalSelection {
        val start = SelectionPoint(-snapshot.scrollbackCount, 0)
        val end = SelectionPoint(snapshot.rows - 1, (snapshot.cols - 1).coerceAtLeast(0))
        return TerminalSelection(
            range = SelectionRange(start, end),
            anchorEnd = start,
            cursorEnd = end
        )
    }

    /**
     * Is the selection still on its own text? False drops it (never repaint).
     *
     * No anchor (Select All, or a selection the user grew) is trusted: those
     * have no fixed text to compare against, and their ranges are re-anchored
     * by [SelectionRange.shift] on trim.
     */
    fun remembers(selection: TerminalSelection, snapshot: TerminalSnapshot): Boolean {
        val anchor = selection.anchor ?: return true
        // Nothing captured (Select All, a grown range) means nothing to
        // contradict: trusted, exactly as the pre-103 behaviour was trusted.
        if (selection.wordText.isEmpty()) return true
        // The word may have been dragged past (the range then starts at the
        // cursor end) or over a wider span, so compare the CELLS the press
        // grabbed — [TerminalSelection.anchor] follows the text as it moves,
        // which is what makes this a plain "does the word still say the same
        // thing" test rather than a row-arithmetic puzzle.
        val line = snapshot.lineAt(anchor.row) ?: return false
        val span = extractLine(line, anchor.col, anchor.col + selection.baseCols)
        return span == selection.wordText
    }

    /**
     * The copy contract. Pure over "give me the line at this row" so it is
     * testable on the host JVM with no emulator.
     *
     * - A row whose end is a soft wrap joins the next row with **no**
     *   separator (the wrapped-URL golden case).
     * - Any other row keeps `\n`.
     * - Trailing blanks are trimmed on hard rows only; a wrapped row keeps its
     *   cells verbatim so the join is byte-true.
     * - Wide-glyph continuation columns are skipped and combining clusters are
     *   expanded (the [TerminalLine.readableText] rules, per cell range).
     */
    fun extract(source: TerminalSnapshotText, range: SelectionRange): String {
        val out = StringBuilder()
        var row = range.start.row
        while (row <= range.end.row) {
            val line = source.lineAt(row)
            if (line == null) {
                // Outside the buffer at either end: nothing to copy here, and
                // no separator either (the row does not exist).
                row++
                continue
            }
            val first = if (row == range.start.row) range.start.col else 0
            val last = if (row == range.end.row) range.end.col else line.text.length - 1
            val piece = extractLine(line, first, last)
            val wraps = line.softWrapped
            out.append(if (wraps) piece else piece.trimEnd())
            if (row < range.end.row && !wraps) out.append('\n')
            row++
        }
        return out.toString()
    }

    /** One row's cell range as human text (clusters joined, wide continuations dropped). */
    fun extractLine(line: TerminalLine, firstCol: Int, lastCol: Int): String {
        val from = firstCol.coerceIn(0, line.text.length)
        // An inclusive last column may sit one past the text's end (a caret at
        // the right margin); clamp rather than fail.
        val toExclusive = (lastCol + 1).coerceIn(from, line.text.length)
        val sb = StringBuilder(toExclusive - from)
        for (col in from until toExclusive) {
            if (line.columnIsContinuation(col)) continue
            val cluster = line.clusters?.get(col)
            if (cluster != null) sb.append(cluster) else sb.append(line.text[col])
        }
        return sb.toString()
    }
}

/**
 * Phase 103.1 — noticing that the buffer's front moved, purely.
 *
 * Two counters on `TerminalSnapshot` describe the buffer's front:
 * `scrollbackCount` (how many history rows exist) and `scrollbackGeneration`
 * (how many have been dropped off the oldest end, ever — the cap is 2 000, so a
 * busy session trims continuously). The view keeps the previous pair and calls
 * [contentShift]; nothing else moves a selection. Derived from values in the
 * snapshot — no side channel, no listener — which is why "the selection stays on
 * its text while output scrolls" is a host test rather than a device ritual.
 */
object TrimMath {

    /**
     * How far a live selection must move when the buffer's front changed
     * between two snapshots — the single rule behind "the selection stays on
     * its text while output scrolls".
     *
     * The axis is `extY = index − scrollbackCount` (`TerminalSnapshot.lineAt`),
     * so three buffer events have three different effects, and two of them
     * look alike:
     *
     * - **A trim** (the oldest history row is dropped) decrements both the
     *   survivor's index and `scrollbackCount`: net **0**. A selection in
     *   history does not move — this is the bug these tests first found, where
     *   the model shifted by the trim count and dragged every selection off its
     *   text.
     * - **A row pushed off the live screen** increments only
     *   `scrollbackCount`: net **−1 per row**. The whole screen moved up, so
     *   every cell's extY went down.
     * - **A row pulled back onto the screen** (a keyboard-close grow) is the
     *   reverse: net **+1 per row**.
     *
     * Since `ΔscrollbackCount = pushes − pulls − trims`, the shift is exactly
     * `−(ΔscrollbackCount + Δtrims)` — both counters are already on the
     * snapshot, so no extra buffer state is needed and a reflow (which rewrites
     * history wholesale) is the caller's problem, not this function's.
     */
    fun contentShift(
        prevCount: Int,
        nowCount: Int,
        prevTrims: Long,
        nowTrims: Long
    ): Int {
        val delta = (nowCount - prevCount).toLong() + (nowTrims - prevTrims)
        return -delta.coerceIn(-MAX_SHIFT, MAX_SHIFT).toInt()
    }

    /** No single frame can move a selection more than this; guards the Int cast. */
    const val MAX_SHIFT = 100_000L
}

/**
 * How the pure extractor sees the buffer: one row lookup, nothing else. The
 * snapshot adapter is a one-liner in the view; tests build a map.
 */
fun interface TerminalSnapshotText {
    fun lineAt(row: Int): TerminalLine?
}

/** The snapshot's own rows, adapted to [TerminalSnapshotText]. */
fun TerminalSnapshot.asSelectionText(): TerminalSnapshotText =
    TerminalSnapshotText { row -> lineAt(row) }

/**
 * The drag frame's decision, pure: which [SelectionDrag] a finger at
 * `(x, y)` inside a viewport of `cols × rows` cells is.
 *
 * - A handle drag that reaches the top/bottom [edgeRows] band scrolls by
 *   `linesPerFrame` while also moving the handle to the edge cell — the
 *   Termux auto-scroll, expressed as data.
 * - Everywhere else the handle simply follows the cell under the finger.
 *
 * [y] and [x] are viewport-relative px (already divided by nothing — this
 * function does the cell math), [cellH]/[cellW] the grid metrics, and
 * [xScroll] the sub-row remainder the canvas is translated by, so the cell
 * under the finger is honest mid-drag (the :783-class row-math bug).
 * [rowCap]/[rowFloor] are the current top/bottom integers, so a clamp can
 * never hand back a row the buffer does not have.
 */
object SelectionAutoScroll {

    const val EDGE_ROWS = 2
    const val LINES_PER_FRAME = 1

    fun dragFor(
        selection: TerminalSelection,
        end: SelectionEnd,
        x: Float,
        y: Float,
        cellW: Int,
        cellH: Int,
        cols: Int,
        rows: Int,
        xScroll: Float,
        rowCap: Int,
        /** The oldest reachable row (`-scrollbackCount`): the top dead end. */
        minTop: Int = Int.MIN_VALUE
    ): SelectionDrag {
        val cw = cellW.coerceAtLeast(1)
        val ch = cellH.coerceAtLeast(1).toFloat()
        val col = (x / cw).toInt().coerceIn(0, (cols - 1).coerceAtLeast(0))
        val row = ((y + xScroll) / ch).toInt().coerceIn(0, (rows - 1).coerceAtLeast(0))
        // The GRABBED end, in the buffer's own coordinates: on screen the
        // handle sits at `cell.row - rowCap`.
        val cell = when (end) {
            SelectionEnd.ANCHOR -> selection.anchorEnd
            SelectionEnd.CURSOR -> selection.cursorEnd
        }
        val target = SelectionPoint(rowCap + row, col)
        // Which edge band the finger is in decides the sign of the scroll, and
        // the buffer's own limits decide whether there is still somewhere to
        // scroll. Two dead ends become verbs instead of doing nothing:
        //   top of the oldest row     → Select all
        //   bottom of the live screen → Clear
        // A dead end that silently does nothing is what made the selection feel
        // broken: the handle simply stopped following the finger.
        val atTopEdge = row <= EDGE_ROWS - 1
        val atBottomEdge = row >= rows - EDGE_ROWS
        val oldestTop = rowCap <= minTop
        if (atTopEdge && oldestTop) return SelectionDrag.SelectAll
        if (atBottomEdge && rowCap >= 0) return SelectionDrag.Clear
        val lines = when {
            // rowCap is the TOP row, so its sign says which way "older" is:
            // in history the older line is more negative (up), on the live
            // screen it is more positive (down). A press that starts at the
            // live screen and drags to the top edge must therefore travel
            // BACK into scrollback — same gesture, opposite sign.
            atTopEdge -> if (rowCap < 0) -LINES_PER_FRAME else LINES_PER_FRAME
            atBottomEdge -> if (rowCap < 0) LINES_PER_FRAME else -LINES_PER_FRAME
            else -> 0
        }
        return if (lines != 0) SelectionDrag.MoveAndScroll(target, lines) else SelectionDrag.Move(target)
    }
}

/**
 * Where the two handles sit, in viewport px, and whether each is on screen at
 * all — pure, so the view only draws and hit-tests.
 *
 * The handle is an inverted teardrop: an OPEN circle above the row and a
 * filled tab touching the selected cell's edge, the shape Termux uses. This
 * object returns the [circle] centre and the [tab] rectangle so the drawing
 * code has no arithmetic of its own.
 */
object SelectionHandles {

    /** Handle geometry, in dp, so the Android edge converts once. */
    data class Metrics(val radius: Float, val stem: Float, val gap: Float)

    data class Handle(
        val circleX: Float,
        val circleY: Float,
        val tabX: Float,
        val tabY: Float,
        val tabW: Float,
        val tabH: Float
    )

    /**
     * @param col the handle cell's column; [end] TRUE for the cursor handle
     *   (drawn below its row) and false for the anchor handle (above it).
     * @param row the handle cell's viewport row (may be off screen).
     * @param xScroll the canvas' sub-row translation, so handles stick to
     *   their cells while the grid is displaced mid-drag.
     */
    fun forCell(
        col: Int,
        row: Int,
        cellW: Int,
        cellH: Int,
        end: Boolean,
        xScroll: Float,
        metrics: Metrics
    ): Handle {
        val cw = cellW.coerceAtLeast(1).toFloat()
        val ch = cellH.coerceAtLeast(1).toFloat()
        val left = col * cw
        val right = left + cw
        // The tab hugs the cell's vertical edge; the circle sits just past it.
        val tabY = if (end) row * ch - xScroll + ch - metrics.stem else row * ch - xScroll
        val circleY = if (end) tabY + metrics.stem + metrics.gap + metrics.radius
        else tabY - metrics.gap - metrics.radius
        return Handle(
            circleX = right,
            circleY = circleY,
            tabX = right,
            tabY = tabY,
            tabW = cw * 0.5f,
            tabH = metrics.stem
        )
    }

    /**
     * Is a touch `(x, y)` on [handle]? The target is a 48 dp box around the
     * circle: the visible dot may be 20 dp, the thing a thumb must hit may not.
     */
    fun hits(handle: Handle, x: Float, y: Float, touchSlopPx: Float): Boolean {
        val dx = x - handle.circleX
        val dy = y - handle.circleY
        return dx * dx + dy * dy <= touchSlopPx * touchSlopPx
    }
}
