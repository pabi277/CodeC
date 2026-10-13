# CodeC Phase 103.2 — Scroll physics: rest position, fling, scrollbar

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S–M
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
