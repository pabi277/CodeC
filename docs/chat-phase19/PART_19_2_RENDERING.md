# CodeC Phase 19.2 — Integer-cell crisp rendering (no glyph overlap)

**Status:** Planned (design/spec only) · **Cost:** `[client-only]`
· **Depends on:** none (independent; pairs well with 19.1)
· **Fixes bug #2:** *"letters overlapping, not visuals good."*
· **Primary target file:** `ui/components/TerminalEmulatorView.kt`

---

## 1. Evidence — why glyphs overlap

In `TerminalEmulatorView.kt` the cell size comes straight from a float paint
measurement and glyphs are placed by fractional accumulation:

```kotlin
val cellW = remember(paint) { max(paint.measureText("X"), 1f) }   // e.g. 9.37 px
val cellH = remember(paint) { max(paint.fontSpacing, 1f) }
...
// per character:
canvas.nativeCanvas.drawText(ch, (start + i) * cellW, y * cellH - ascent, paint)
```

Three compounding problems:

1. **Fractional cell width.** `cellW` is a non-integer (`measureText` returns a
   float). Multiplying by the column index (`(start+i) * cellW`) accumulates
   rounding error across the row, and Android snaps each glyph to the pixel grid
   differently, so spacing looks uneven and dense columns visually touch.
2. **Glyph advance ≠ cell width.** The font's own advance for a given glyph is
   not exactly `measureText("X")`. Wide glyphs (`m`, `@`, CJK, box-drawing) draw
   past their cell; `drawText` uses the font advance, not the cell, unless we
   force it.
3. **Fake bold widens strokes.** `paint.isFakeBoldText = bold` synthesizes bold
   by thickening strokes, pushing pixels past the cell edge into the neighbor —
   a classic overlap source on monospace grids.

Termux avoids all three by using an **integer cell width**, drawing each glyph
clipped/aligned to its cell, and letting the monospace font's real advance match
the (integer) cell.

---

## 2. Design — pixel-snapped monospace grid

### 2.1 Integer cell metrics

- Compute `cellW` and `cellH` as **integers** (round, `>= 1`):
  `val cellWpx = ceil(paint.measureText("MMMMMMMMMM") / 10f).toInt()` (average
  over several 'M's reduces single-measure rounding), `cellHpx =
  ceil(paint.fontSpacing).toInt()`.
- Derive `cols`/`rows` **and all draw positions** from the integer cell so
  `colX = col * cellWpx` is exact and never drifts. The PTY sizing path already
  uses a *settled* paint (`settledCellW/H`) — make that integer too so PTY grid
  and render grid agree exactly.
- Keep the **font size** itself continuous for smooth pinch, but snap the
  **cell** to integers each time the settled size changes.

### 2.2 Per-cell glyph placement

- Draw each glyph **centered/left-aligned within its integer cell** at
  `x = col * cellWpx` (baseline `y*cellHpx - ascentInt`), one `drawText` per
  cell (already per-cell today — keep it, just with integer coordinates).
- **Set the paint to not add its own letter spacing / scaling**:
  `paint.letterSpacing = 0f`, `textScaleX = 1f`. Optionally, if a specific glyph
  is wider than the cell (rare non-monospace fallback), scale that single draw
  with `textScaleX = cellWpx / glyphAdvance` so it never bleeds — cheap safety
  net.
- **Clip to the cell (optional, robust):** wrap each glyph draw in
  `canvas.save(); canvas.clipRect(cellRect); …; canvas.restore()` so nothing can
  ever paint into a neighbor. Measure the perf; per-glyph clip on a full screen
  is usually fine, but a per-row clip is a cheaper compromise.

### 2.3 Bold without overflow

- **Prefer a real bold typeface** over fake bold: build a `boldPaint` from
  `Typeface.create(base, Typeface.BOLD)` (monospace bold has the correct
  advance) and use it for bold runs instead of `isFakeBoldText = true`. Real
  monospace bold keeps the same cell advance; fake bold does not.
- If a real bold face isn't available for the chosen family, keep fake bold but
  clip to the cell (2.2) so thick strokes can't overlap.

### 2.4 Background & cursor alignment

- Background rects and the cursor block already use `cellW`/`cellH`; switching to
  integer metrics makes bg fills and the cursor line up exactly with glyphs
  (today a fractional `cellW` can leave 1px seams or overlaps between adjacent
  colored backgrounds — the "not visually good" seams).
- Round the canvas origin so the whole grid starts on an integer pixel.

### 2.5 Anti-aliasing / hinting

- Keep `ANTI_ALIAS_FLAG`; add `paint.isSubpixelText = true` and
  `paint.hinting = Paint.HINTING_ON` for crisper small text. Verify on the
  device (owner) — these are cosmetic and safe.

---

## 3. Implementation steps

1. In `TerminalEmulatorView`, replace float `cellW/cellH` (both `settled*` and
   active) with rounded **integer** px values; recompute `cols/rows/ptyCols/
   ptyRows` from them.
2. Set `paint.letterSpacing = 0f`, `textScaleX = 1f`, `isSubpixelText = true`.
3. Add a `boldPaint` (real bold typeface); use it for bold runs; drop
   `isFakeBoldText` (or keep as clipped fallback).
4. Update `drawLine(...)` to use integer cell coordinates for glyphs, background
   rects, selection, and cursor; optionally clip per glyph/row.
5. Keep the per-character draw loop (it already avoids the run-level drift) — now
   with integer origins it's both correct and crisp.

## 4. Host unit tests (CI-run)

Rendering is Canvas/device-visual, but the **metrics math** is unit-testable —
factor it into a pure helper:
- `CellMetricsTest` — `cellWidthPx(measured)` and `cellHeightPx(spacing)` round
  up to `>= 1` and are integers; `cols = floor(viewW / cellW)` matches expected
  for sample sizes.
- `colToX/rowToY are exact integer multiples` (no fractional drift across 200
  columns).
The visual crispness/overlap itself stays in the owner's device round (Compose
Canvas can't be asserted headlessly here).

## 5. Exit condition & device recipe

```text
1. Terminal: run `ls -la /usr/bin | head -40` (dense filenames) and
   `printf 'ABCDEFGHIJKLMNOPQRSTUVWXYZ mmmmmmmmmm @@@@@@@@@@\n'`.
   EXPECT: every character sits in its own cell; no touching/overlap; even spacing.
2. Run something with bold (e.g. `git status`, or `printf '\e[1mBOLD\e[0m normal\n'`).
   EXPECT: bold text is bold but does NOT overlap neighbors.
3. Run `ls --color` or `htop` (colored backgrounds) → colored cells tile with no
   seams or overlaps.
4. Pinch-zoom across sizes → text stays crisp and aligned at every size.
PASS = no overlapping/smeared glyphs at any size (matches Termux legibility).
```

## 6. Invariants

Client-only; Compose/Canvas + Paint only; no native/PTY/emulator-logic changes;
no `.` on PATH. Font family/size settings (Phase 4.4/6) still honored — this only
changes how cells are measured and painted.
