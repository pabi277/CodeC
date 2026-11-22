# CodeC Phase 103.2 — Scroll physics: rest position, fling, scrollbar

> **Status:** 🚧 IMPLEMENTED on `arena/3f8a6afe-codec` (2026-11-22) · **Cost:**
> `[client-only]` · **Effort:** S–M
> **Owner row (verbatim):** "after open the app first time then when i scroll up
> then i saw empty space in the terminal"

## First move: evidence, not code

- `TerminalEmulatorView.kt` — the sub-pixel scroll remainder (`scrollSubPx`)
  accumulates during drags and is **never reset on finger-lift**; a fresh terminal
  therefore rests displaced by up to a row, rendering the blank band the owner
  photographed in words. Grep: no fling/velocity handling and no scrollbar thumb in
  the terminal view.
- Reference behaviour (clean-room): Termux discards `mScrollRemainder` when the
  finger leaves the glass and runs a standard scroller fling; its scrollbar thumb is
  proportional to scrollback depth (read in the 2026-10-14 audit clone).

## Design

Extend the existing pure scroll policy (the `ScrollMath` family) rather than
inventing a second one: (1) `rest(remainder): Int` — on pointer-up the fractional
remainder is discarded and the offset snaps to whole rows, so rest positions are
always exact; (2) a fling model (initial velocity → distance with the platform's
friction curve) clamped to `[0, scrollback]`, emitting whole-row steps; (3)
scrollbar geometry (thumb offset/length from offset, rows, total) as a pure
function. The view wires pointer-up → `rest`, velocity tracker → fling, and draws
the thumb while scrolling (fading after). Selection anchoring under scroll is
103.1's `growByScroll`; this part must not move a resting selection.

## The Android edge

Velocity from the Compose gesture layer; fling runs on the existing 16 ms
frame-paced render pump (`RenderPump.kt`) — no new ticker. Reduced-motion: if the
platform animator scale is 0, fling degrades to an instant jump (recorded, tested
at policy level).

## Exit condition

Device rows: fresh open has **zero** blank band; a hard fling in a long scrollback
travels and settles without overshoot past either end; the thumb's size/position
match scrollback depth; a resting selection (103.1) does not drift.

## Tests (plan)

- `ScrollRestTest` (new or `ScrollMathTest` extension): remainder discarded at
  rest for ±fractions; rest is identity for whole rows; fling clamps at both ends;
  thumb geometry at top/middle/bottom and with empty scrollback.
- `ScrollWiringTest` (source pin): pointer-up path calls the rest policy; the old
  un-reset accumulation cannot return.
- Existing `ScrollMathTest` / `TerminalScrollTest` suites stay green.

## Sources (record)

1. termux-app `TerminalView.java` (`mScrollRemainder` lifecycle, fling, scrollbar)
   — behaviour read from local clone @ `8629e63`, 2026-10-14. GPL-3.0: behaviour only.
2. Owner device report, this session (2026-10-14).

## Deferred / rejected with reasons

- **Alternate-screen wheel→arrows** — belongs with the mode-1007 view work in
  Phase 104.3 (it needs the PTY/session facts settled there); not half-done here.
- **Scrollback-size setting** — the 2,000-line hard cap (`TerminalBuffer.kt:179`)
  becomes a setting in a later polish phase; this part only makes scrolling honest
  at whatever size the buffer is.

## Implementation record (103.2)

Built as `ui/terminal/TerminalScroll.kt` (pure) with the wiring in `TerminalEmulatorView.kt`.

- **`rest()` discards the fraction and never changes the row.** A lift is
  `Rest(topRow.coerceIn(minTop,0), row − topRow)`; because `ScrollMath` keeps
  `|subRowPx| < cellH` by construction the second field is 0 for any legal state. The
  earlier "round the remainder to the nearest row" model fabricated row changes out of a
  sub-row offset and *doubled* every rest — that is the blank-band class of bug, not its
  fix. The view has exactly **two** `rest(` call sites (finger lift, fling end), both of
  which set `scrollSubPx = 0f` in the same breath, and a pin enforces both facts.
- **The lift is unconditional on every path** — plain scroll and a selection drag alike —
  which is what makes a resting terminal draw `translate(0f)` and no blank strip.
- **Fling** is a list of frame deltas (`ScrollMotionPolicy.flingSteps`) applied through the
  same `ScrollMath.step` a drag uses, at `FRAME_MS` on the frame pump, cancelled by the next
  touch; velocity is capped (`MAX_VELOCITY`), the decay floor (`MIN_VELOCITY_PX_PER_MS`)
  ends it, `MAX_FRAMES` bounds it, and `animatorScale <= 0` (reduced motion) turns it into a
  single instant step. It always ends with the same `rest()` — no rail overshoot survives.
- **Scrollbar** geometry is proportional (`MIN_THUMB_PX` 24 px floor, `MIN_FRACTION` 6 %),
  0 = oldest and 1 = live; the thumb is drawn only `if (bar.hasTravel)` and fades
  (`SCROLLBAR_LINGER_MS` 700 ms) keyed on the offset, so a resting terminal draws none.
  Dragging it maps a track position to a row with `rowForThumb`.
- **Defect the tests caught:** `-rows.roundToLong().toInt().coerceIn(-history, 0)` parses
  as `-(…coerceIn…)`, so the positive count clamped to 0 and was then negated — every
  track position mapped to live. Now `(-rows).coerceIn(-history, 0)`.

Tests: `ScrollRestTest` 21 (rest invariants, decay/monotonicity/rails, reduced motion,
thumb geometry, `rowForThumb`) + `ScrollMathTest` 6 untouched. Not run: Gradle, CI, device.
