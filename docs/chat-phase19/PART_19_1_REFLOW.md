# CodeC Phase 19.1 — Scrollback & screen reflow on resize / zoom

**Status:** IMPLEMENTED (2026-08-31, `arena/01a056aa-codec`) — **CI GREEN `33371114549`** (assemble + unit tests + lint; two earlier rounds caught the Brahmic vowel-sign width gap + 9 test-trace bugs, fixed `ee1c054`/`39bd3e2`) · **Cost:** `[client-only]`
· **Depends on:** Phase 6 (Terminal UX — pinch-zoom), Phase 7 (`resizeKey`)
· **Fixes bug #3:** *"if I zoom out the terminal the previous commands don't get
full screen, they remain as they were."*
· **Primary target file:** `ui/terminal/TerminalBuffer.kt` (+ tests)

---

## 1. Evidence — why it happens

Pinch-zoom in `TerminalEmulatorView.kt` changes the font size, which changes
`cellW`/`cellH`, which changes the computed `ptyCols`/`ptyRows`, which calls
`onResize(cols, rows)` → `TerminalSession.resize()` → `TerminalBuffer.resize()`.

`TerminalBuffer.resize()` today does **only a rectangular copy**:

```kotlin
fun resize(newCols: Int, newRows: Int) {
    ...
    screen = resizeGrid(screen, c, r)      // copy old cells into a new-sized grid
    altScreen = altScreen?.let { resizeGrid(it, c, r) }
    cols = c; rows = r
    ...
}
private fun resizeGrid(src, c, r): ... {
    val copyCols = minOf(src[0].size, c)   // ← truncates or leaves blanks; never re-wraps
    ...
}
```

- The **scrollback** (`ArrayDeque<Array<Cell>>`) is **not touched at all** on
  resize — every history line keeps the width it had when it was produced.
- The visible **screen** is copied cell-for-cell, so lines that were wrapped at
  the old column count keep their old wrap points.

Result: when you zoom **out** (more columns), old content stays at the old narrow
width and the right side is empty; when you zoom **in** (fewer columns), long
lines are truncated instead of re-wrapping onto the next line. Termux fixes this
by **reflowing** the combined scrollback+screen text to the new width on every
resize.

---

## 2. Design — a reflow engine

### 2.1 Model: logical lines vs. visual rows

The core idea (Termux's approach, re-implemented): remember which physical rows
were **hard newlines** vs. **soft (auto-wrap) continuations**, so on resize we
can rejoin soft-wrapped fragments into a *logical line* and re-split them at the
new width.

1. **Track a `wrapped` flag per row.** Add a boolean to each stored line meaning
   "this row continued onto the next because of auto-wrap (not a real `\n`)."
   Set it in `TerminalBuffer.print()` at the moment `wrapPending` becomes true
   and the next print forces `carriageReturn()+lineFeed()` — the line we just
   left was soft-wrapped. Lines ended by an explicit LF/CR are **not** wrapped.
   - Storage: extend the internal row representation (a parallel
     `BooleanArray`/`ArrayDeque<Boolean>` for scrollback, or a small wrapper
     class per row). Keep `Cell[]` as-is to minimize churn.

2. **On `resize(newCols, newRows)`:**
   a. **Concatenate** scrollback rows + screen rows into a list of *logical
      lines*: walk rows top→bottom, appending each row's cells to the current
      logical line; start a new logical line whenever a row was **not** wrapped.
      Track where the **cursor** sits within its logical line (logical index +
      column) so it can be restored.
   b. **Re-wrap** each logical line to `newCols`: emit `ceil(len/newCols)` visual
      rows (at least 1), carrying cell styles; mark all but the last emitted row
      as `wrapped=true`. Trailing blanks in a logical line are trimmed before
      wrapping so a full-width line doesn't create a spurious empty row.
   c. **Repartition** the emitted visual rows into scrollback vs. the bottom
      `newRows` screen rows. Push overflow into scrollback (respect
      `scrollbackLimit`).
   d. **Restore the cursor** to the visual row/col matching its saved logical
      position; clamp into the new screen.

3. **Alt screen is NOT reflowed.** Full-screen apps (vi, htop, less) repaint
   themselves on `SIGWINCH`; reflowing the alt screen fights them. When
   `usingAlt`, keep the current rectangular `resizeGrid` behavior and just send
   the new winsize (the app repaints). Only the **primary** screen + scrollback
   reflow. (This matches Termux.)

### 2.2 Edge cases to cover (and test)

- Width **unchanged**, only rows change → no reflow needed, just add/remove
  blank rows at the bottom and adjust scrollback (cheap path; keep it).
- Zoom **out** (wider): short old lines must visually extend / rejoin; a logical
  line longer than the new width still wraps correctly.
- Zoom **in** (narrower): long logical lines split into multiple visual rows;
  total content preserved, nothing truncated.
- Double-width / combining characters: out of scope for 19.1 (CodeC is
  single-width today); reflow by code-point count. Note as a follow-up.
- Cursor at end-of-line / `wrapPending` true at resize time: cursor restored to
  the correct rewrapped position.
- Empty scrollback; scrollback at exactly `scrollbackLimit` (oldest dropped).

### 2.3 Performance

Reflow is O(total cells). With `scrollbackLimit = 2000` rows × ~80 cols this is
well within a frame budget for an occasional pinch-end resize. Reflow should run
on the existing IO/synchronized path (`TerminalSession.resize()` already holds
the emulator lock), **not** on every intermediate pinch frame — resize is only
called when `ptyCols/ptyRows` actually change (already the case), and the smooth
pinch only rescales the font visually until `onZoomEnd`. Confirm resize isn't
called per-frame; if it is, debounce to pinch-end.

---

## 3. Implementation steps

1. Add per-row `wrapped` tracking to `TerminalBuffer` (screen rows + scrollback),
   set in `print()`/`lineFeed()` where auto-wrap occurs; cleared by explicit
   CR/LF and by erase ops on that row.
2. Write a pure `reflow(oldRows, oldScrollback, newCols)` function (logical-line
   rejoin → re-wrap) returning `(newScrollback, newScreenRows, cursor)`. Keep it
   pure/static for direct unit testing.
3. Rewrite `TerminalBuffer.resize()` to: fast-path when `cols` unchanged; else
   call `reflow` for the primary screen; keep rectangular copy for the alt
   screen; restore cursor; bump `generation`.
4. Ensure `TerminalSession.resize()` still sends `pty.setWindowSize(rows, cols)`
   and `publish()`es afterward (unchanged).
5. Verify `TerminalEmulatorView` scrollback math (`topRow`, `scrollbackCount`)
   still holds after reflow (counts come from the snapshot, so it should).

## 4. Host unit tests (CI-run — no JDK locally)

Extend `TerminalBufferTest`:
- `reflow widens: two soft-wrapped rows rejoin into one when cols grows`.
- `reflow narrows: one long logical line splits into N rows; content preserved`.
- `hard newline is NOT rejoined across resize` (two real lines stay two).
- `cursor position preserved across widen and narrow`.
- `scrollback reflows and respects scrollbackLimit (oldest dropped)`.
- `alt screen is not reflowed (rectangular copy retained)`.
- `rows-only change adds/removes blank rows without reflowing text`.

## 5. Exit condition & device recipe

```text
1. Open the terminal; run: seq 1 60   (or `ls -la /usr` — many lines).
2. Pinch to ZOOM OUT (smaller font, more columns).
   EXPECT: previous output re-wraps to fill the full width edge-to-edge;
   no empty right margin; no truncation.
3. Pinch to ZOOM IN (bigger font, fewer columns).
   EXPECT: long lines wrap onto multiple rows; all text still present.
4. Run vi (or htop) → resize/zoom → the full-screen app repaints correctly
   (alt screen not corrupted).
5. Scroll into history after a resize → old lines are at the new width.
PASS = steps 2–5 behave like Termux.
```

## 6. Invariants

Client-only; pure Kotlin; no native/PTY changes; alt-screen apps still get
`SIGWINCH` via the unchanged `setWindowSize`. No `.` on PATH; Phase 7
multi-session + `resizeKey` untouched.


---

## 7. Research notes (2026-08-31)

* **Soft-wrap flag semantics** — a row's `wrapped=true` means "this visual
  row continued onto the next by auto-wrap (DECAWM), not a hard LF" — is
  the model used by mature terminals' resize ("transcript resize"): rejoin
  wrapped fragments into logical lines, re-split at the new width, push
  overflow into scrollback. The xterm/ECMA-48 auto-wrap model (the cursor
  parks at the right margin and the next print wraps) is what generates the
  flag.
* **Rows-only resize**: when only the height changes there is nothing to
  re-wrap — grow by restoring rows from scrollback (content moves DOWN, so
  the cursor follows it down), shrink by overflowing the top rows into
  scrollback (cursor follows up). This matches how full terminals behave
  when a window gains height (prompt stays with its output).
* **Alt screen**: full-screen apps repaint on SIGWINCH; a rectangular copy
  (with cursor clamp) is the correct behavior — reflowing would fight the
  app's own repaint (and the phase docs already specified this).
* **Wide glyphs** (from 19.4): UAX #11 wide characters occupy two columns;
  a re-wrap boundary must never split a lead/continuation pair — the
  fragment shortens by one (blank padding) and the pair moves down intact.

## 8. Implementation record (2026-08-31, commit 843a274)

* **Row storage** — `TerminalBuffer` internals moved from
  `Array<Array<Cell>>` to `Array<Row>` where `Row(cells, wrapped)`;
  scrollback became `ArrayDeque<Row>`. All shift/copy/clone/erase paths
  carry the flag; `clearRow`/`eraseInLine(2)` reset it; rows scrolled into
  history keep it (cloned at push — the live row is about to be
  overwritten).
* **`print()` sets the wrap flag BEFORE `lineFeed()`** — if the feed
  scrolls at the bottom margin, the row's clone must already carry
  `wrapped=true` into scrollback. (Setting it after was a caught-and-fixed
  aliasing bug: the pushed clone would have kept `false` while the flag
  landed on the wrong live row.)
* **New `Reflow.kt`** (pure): logical-line rejoin (trailing *default*
  blanks trimmed so full-width lines don't spawn phantom rows) → re-split
  at the new width (all but last fragment `wrapped=true`; wide pairs never
  split) → cursor mapped through (logical offset → fragment row/col,
  boundary ties to the earlier fragment's end, clamped). `resize()` runs it
  over scrollback+screen, repartitions (bottom rows = screen, overflow →
  scrollback, oldest beyond the limit dropped), and restores the cursor.
* **Rows-only path** (`resizeRowsOnly`): grow pulls scrollback rows back
  (cursor follows down), shrink overflows top rows (cursor follows up,
  clamped). **Alt screen**: unchanged rectangular `resizeGrid` for both
  dimensions.
* **Tests** — `ReflowTest` (14): widen rejoins / narrow re-wraps / hard LF
  never rejoined / cursor maps through widen & narrow / scrollback limit
  honored / rows-only grow+shrink / cursor-follows-content on shrink /
  alt rectangular / wrapped-into-history rejoins on later widen / wide pair
  never split at a boundary / wide pair wraps whole at the right margin.

**Device gate (owner):** §5 recipe unchanged. PASS = zoom-out refills the
width, zoom-in wraps (nothing truncated), `vi`/`htop` repaint cleanly on
zoom, scrolled history sits at the new width.
