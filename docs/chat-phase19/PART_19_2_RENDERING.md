# CodeC Phase 19.2 — Integer-cell crisp rendering (no glyph overlap)

**Status:** IMPLEMENTED (2026-08-31, `arena/01a056aa-codec`) — **CI GREEN `33371114549`** (assemble + unit tests + lint; two earlier rounds caught the Brahmic vowel-sign width gap + 9 test-trace bugs, fixed `ee1c054`/`39bd3e2`) · **Cost:** `[client-only]`
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

**Device round 1 (2026-08-31) verdict: FAIL → fixed.** The owner reported
"letters have a noticeable gap between them" — root cause found and fixed the
same day (see §7.1). Recipes below are the **round-2** versions: single-line,
copy-pasteable, no `/usr/bin` (it does not exist in the CodeC userland — use
`$PREFIX/bin`).

```text
1. GAP CHECK (the round-1 failure): run
     python3 -c 'print("MWMWMMMWMW"*4)'
   EXPECT: a dense, even WALL of letters — same visual density as this doc's
   monospace text. No airy tracking, no touching glyphs.
2. DENSE GRID: run  ls -la $PREFIX/bin | head -40
   and  printf 'ABCDEFGHIJKLMNOPQRSTUVWXYZ mmmmmmmmmm @@@@@@@@@@\n'
   EXPECT: every character sits in its own cell; no touching/overlap; even spacing.
3. BOLD: printf '\e[1mBOLD\e[0m normal\n'
   EXPECT: bold text is bold but does NOT overlap neighbors.
4. COLORS: ls --color  (or htop) → colored cells tile with no seams or overlaps.
5. Pinch-zoom across sizes → text stays crisp, tight and aligned at every size.
PASS = dense-but-not-touching glyphs at any size (matches Termux legibility).
```

## 6. Invariants

Client-only; Compose/Canvas + Paint only; no native/PTY/emulator-logic changes;
no `.` on PATH. Font family/size settings (Phase 4.4/6) still honored — this only
changes how cells are measured and painted.


---

## 7. Research notes (2026-08-31)

* Android `Paint.measureText` returns a FLOAT advance; per-glyph origins
  computed as `col * cellW` with a fractional `cellW` accumulate error, and
  each `drawText` snaps to the pixel grid independently — the overlap the
  owner photographed. Rounding UP (ceil) guarantees cell ≥ font advance, so
  glyph-wide collisions become impossible at the cost of ≤1 px letter
  spacing.
* `Paint.isFakeBoldText` widens strokes beyond the advance (Android docs:
  it applies a "fake bold" effect); `Typeface.create(base, BOLD)` prefers a
  real bold face, and where none exists the per-glyph squeeze-to-slot guard
  (below) keeps even synthesized bold inside its cell.
* `Paint.letterSpacing`/`textScaleX` must be pinned (0 / 1) or the font's
  own advance silently diverges from the cell; `isSubpixelText` positions
  glyphs with subpixel precision without changing advances.

## 8. Implementation record (2026-08-31, commit 3b1986d)

* **New `CellMetrics.kt`** (pure): `cellWidthPx` (ceil of the
  average-of-ten-'M' advance, ≥1), `cellHeightPx` (ceil of
  `fontSpacing`), `columnsForWidth`/`rowsForHeight` (floor, ≥1), and
  `columnX`/`rowY` exact integer origins. Both the **settled** (PTY sizing)
  and **active** (pinch-visual) paints now derive INTEGER cells from it, so
  the render grid and the PTY grid agree exactly.
* **`TerminalEmulatorView`**: `cellW`/`cellH` are `Int`; glyph x =
  `columnX(col, cellW)`, baseline = `rowY(y, cellH) - ascent`; background,
  selection and cursor rects all use the same integer metrics (kills the
  fractional seams). New `boldPaint` (`Typeface.create(typeface, BOLD)`)
  replaces `isFakeBoldText`. Per-glyph guard: if the measured glyph
  (fallback font, cluster, anything) exceeds its slot, `textScaleX` is
  scaled for that single draw and restored — nothing can bleed into the
  neighbour. `configureGridPaint()` pins `letterSpacing = 0`,
  `textScaleX = 1`, `isSubpixelText = true` on every grid paint.
* **Tests** — `CellMetricsTest` (4): ceil/never-below-1 semantics, floor
  column counts, exact integer multiples across 200 columns.

**Device gate (owner):** §5 recipe unchanged. PASS = dense text (`ls -la
$PREFIX/bin`), bold (`printf '\e[1mBOLD\e[0m'`), colored bg (`ls --color`) and every pinch
size render with zero touching glyphs.

---

## 7.1 Device round 1 postmortem — the letter-gap regression (FIXED 2026-08-31)

**Report:** owner device test: "The letters have a noticeable gap between them
not looking good."

**Root cause — my ceil-slack:** `CellMetrics.cellWidthPx` = `ceil(advance)`.
On the owner's device the monospace advance measured ~22.05 px, so every cell
was 23 px: **+0.95 px of man-made tracking on every letter (~4%)**. Glyphs are
drawn one per cell at `col * cellW`, so the slack appears uniformly across the
whole row — exactly "noticeable gap". Ceil was chosen (a) to prevent overlap
and (b) to keep integer origins; it succeeded at both but paid with spacing.

**Fix — fit the font to the grid, not the grid to the font:** new
`CellMetrics.fitSizeToGrid(requestedSizePx, measure)` nudges the text size
(≤ 8 % guard, in practice < 1 %) until the monospace advance lands on a whole
pixel; the integer cell then EQUALS the font's own advance — still drift-free
(every origin stays an integer multiple of the cell), now with zero added
tracking. Example: 22.05 px → size × 22/22.05 → advance 22.00 px → cell 22.
Rounding down is safe because glyphs are placed per-column (error is capped at
0.05 px/glyph and never accumulates — the original overlap bug cannot return).
Degenerate metrics or a > 8 % bend fall back to the old ceil cell, size
untouched.

**Code:** `CellMetrics.fitSizeToGrid` + `FontFit` (`ui/terminal/CellMetrics.kt`);
view fits both paints via `fitGridPaint(paint)` (`TerminalEmulatorView.kt`) —
the settled (PTY) and active (pinch) paints each snap independently, and
`boldPaint` is now COPIED from the fitted paint so bold shares the snapped
size. **Tests:** 6 new `CellMetricsTest` cases (snap-to-advance, keep-exact
size, round-down safety, drift refusal, quantized-advance invariant sweep,
degenerate fallback) — 10 total in the file.

**Lesson (for later phases):** integer snapping must happen on the FONT SIZE
first; snapping only the cell converts sub-pixel error into uniform letter
spacing. The general rule from public terminal-rendering practice: fit the
font to the grid, never pad the grid away from the font.

---

## 7.2 Device round 2 postmortem — density & weight (the `stty size` data) — FIXED 2026-08-31

**Owner's side-by-side screenshots (CodeC vs Termux) + answers:**
letters still too far apart (a), **thinner/lighter** than Termux (a), rows
**airier** (a), letters **much bigger** — "very obvious; much more text fits
on the Termux screen" (a); both sharp (b). Plus the objective numbers:

| | CodeC | Termux |
|---|---|---|
| `stty size` | **32 rows × 60 cols** | **39 rows × 71 cols** |

Termux fit ~44% more text on the same screen. Root causes (both mine):

1. **Default terminal font size was 14sp** — a UI-editor default, not a
   terminal default. Columns scale inversely with size, so 60 cols ×
   (14/12) = **70** and 32 rows scale by the same factor → **~37**: a 12sp
   default lands on Termux-class density. Changed in `SettingsManager`
   (+ the `stateIn`/`collectAsState` initials in VM & SettingsScreen).
2. **The font itself**: stock Android `Typeface.MONOSPACE` (Droid Sans Mono)
   is light-stroked with wide sidebearings — the "thin / gaps / stretched"
   look. The app now BUNDLES **JetBrains Mono** (SIL OFL 1.1, notice at
   `app/src/main/assets/licenses/JETBRAINS_MONO_OFL.txt`): **Medium** as the
   normal face and **Bold** for ANSI-bold runs (real faces, distinct from
   each other; fallback to MONOSPACE if loading fails). New default family
   "JetBrains Mono"; Monospace/Courier/Sans/Serif remain selectable.
3. **Row pitch**: parsed the TTF (hhea) — JBM ships a roomy **1.32 em** line
   (ascent 1020/descent 300 per 1000 em, advance 0.600 em). Drawn as-is that
   is only ~33 rows. New `CellMetrics.TERMINAL_LINE_FACTOR = 0.9` tightens
   the row to ~1.19 em ≈ 2.0 × the 0.6 em advance — the classic terminal
   ratio (Termux similarly tightens its font's metrics; clean-room: general
   font-metrics practice, no Termux assets/code). **Predicted result at 12sp:
   ~70 cols × ~36–38 rows** vs Termux's 71 × 39.

**Code:** `SettingsManager` (12sp + "JetBrains Mono" defaults),
`TerminalViewModel`/`SettingsScreen` initials + options list + preview,
`TerminalEmulatorView` (`ResourcesCompat.getFont(R.font.jetbrainsmono_medium/bold)`,
bold runs use the real Bold face), `CellMetrics.cellHeightPx(spacing,
lineFactor)` + `TERMINAL_LINE_FACTOR` (clamped [0.5, 2], never < 1px;
1-arg overload unchanged). **Tests:** +2 `CellMetricsTest` (tightened pitch
math, clamp). Assets: `res/font/jetbrainsmono_{medium,bold}.ttf` (~544 KB).

**Round-3 recipe (objective):** after install, `stty size` should report
≈ **70 cols / 36–38 rows** (was 32/60; Termux reference 39/71), and the
MWMW wall should look dense and dark next to Termux.

**Final verdict (2026-08-31): PASS — owner: "All ok now"** after rounds 3–4 on top of §7.1–7.2 (density pack + perf work).
