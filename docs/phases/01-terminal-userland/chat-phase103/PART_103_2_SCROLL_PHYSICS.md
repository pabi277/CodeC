# CodeC Phase 103.2 — Scroll physics: rest position, fling, scrollbar

> **Status:** 🚧 IMPLEMENTED (commit 2 of 2; device rows owed) · **Cost:** `[client-only]` · **Effort:** S–M
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

## Implementation

**Commit 2 of Phase 103 (2026-10-14, owner: "Start Phase 103").** This commit carries
the Compose layer that commit 1 deferred (owner split decision, see PART_103_1
§Implementation) together with the scroll physics. Everything is host-tested or
pin-tested; the view itself is compiled only by CI `Build APK`.

**What shipped:**

- **Selection (103.1 wiring, now user-visible).** Long-press selects the word under
  the finger through `SelectionModel.begin` and opens no popup. Two handles
  (`SelectionKnob`, 48 dp touch targets) drag the ends; `applyHandleDrag` maps the
  finger's view point to a buffer cell on every move and every auto-scroll frame.
  A floating toolbar (`TerminalSelectionAction`, each 48 dp min height) offers
  Copy / Select all / Paste and a More menu (Open URL, Copy All, Share). Colours come
  from the existing `theme.selection` / `surfaceVariant` roles.
- **Copy law.** The toolbar Copy calls `selectedText?.let(onCopyText)` and is
  disabled when there is no selection. The transcript fallback at
  `TerminalScreen.kt:334` is deleted (`activeSelection ?: viewModel.transcriptText()`
  is pinned absent). Copy All and Share are explicit overflow actions, not Copy.
  Share's `selectedText ?: transcriptText()` fallback predates this phase
  (on `main`); it is recorded here and not changed in this part.
- **Selection anchored through output.** The selection is stored in buffer-absolute
  rows (`absRowOf`), so it stays on its text while `cat` scrolls. A width change
  drops it (`LaunchedEffect(cols)`), since reflow re-lays the history.
- **Rest on lift (the first-open blank band).** `onRelease` calls `ScrollMath.rest`
  before any fling is planned. The sub-row remainder is discarded and the offset snaps
  to whole rows, so a resting view is exact (`TerminalGestureWiringTest` pins that rest
  comes before the fling).
- **Fling / inertia.** `ScrollMath.planFling` turns the release velocity into an
  exponential-decay travel (`FLING_TAU_S`, bounded by `FLING_MIN/MAX_PX_PER_S`,
  stop at `FLING_STOP_PX_PER_S`). A `LaunchedEffect(fling)` loop applies one
  `ScrollMath.step` per frame and ends at the rail it is heading for (no overshoot).
  A touch cancels the fling (`onDown` sets `fling = null`). Reduced motion
  (animator scale 0) is an instant jump.
- **Proportional scrollbar.** `ScrollMath.thumb` gives the length as the visible
  fraction of history + live rows and the offset across the free track; drawn while
  scrolling and fading after 900 ms. No thumb when there is no history.
- **Edge auto-scroll** for a handle held in the top/bottom band uses the same
  `ScrollMath.step`.

**Deviations recorded:**

- The plan said fling would run on `RenderPump.kt`. It uses `withFrameNanos` in a
  `LaunchedEffect` instead: frame-clock paced, no new ticker or timer (pinned by
  `TerminalGestureWiringTest`).
- The plan's `ScrollWiringTest` shipped as `TerminalGestureWiringTest` (source pins;
  the Compose layer cannot run on host). `ScrollRestTest` covers rest, step rails,
  fling plan/offset, and the thumb.
- The scrollbar fade uses the platform default spring, not a `CodecMotion` spec. The
  view must stay off the design-system motion tokens (`TerminalChromeTest`), and spec
  constructors live in the theme motion file (`MotionWiringTest`). Reduced motion
  (animator scale 0) shows the thumb at its target at once. Found by CI run
  `38506987604`; the first attempt used `CodecMotion.fadeSpecMedium` and failed both pins.
- A touch that interrupts a fling also runs `rest`, so the sub-row remainder is
  discarded at that moment. That can move the view by less than one row; it is the
  same rest rule applied on touch-down.

**Host verification (sandbox harness, full set):** 130 passed, 0 failed for the
selection, scroll, and pure terminal suites. Plus the four source scans
(`TouchTargetTest`, `TokenAdoptionTest`, `TypeAdoptionTest`, `IconRoleTest`):
147 passed, 0 failed. Not host-runnable: `TerminalUxTest`, `TerminalBufferTest`
(they import the Android `components` package), and the Compose view and screen.

**CI history (recorded as it happened):**

- `f5f3748` (103.1) — `Build APK` run `38506463779`: red, test-compile error
  (`SelectionExtractTest` missing the legacy `selectedText` import). Fixed by the
  fix-up `7c4b9f1`.
- `8e436bc` (103.2 first tip) — run `38506987604`: compile green, 3574 tests with
  one red pin (`MotionWiringTest`: thumb fade used a spec constructor in the view).
  Fixed in the next commit (see the CI record in the phase README).
- Note: `7c4b9f1` was pushed together with `8e436bc` by mistake, so it was never
  built alone. Its test-compile fix was verified on the host instead.

**Device pass required (owner):** fresh open has no blank band; a hard fling in a long
scrollback settles at the true top/bottom without overshoot; the thumb matches
scrollback depth; a partial wrapped URL copies as one unbroken string; Select all then
Copy yields the full buffer; a selection held during a running `cat` stays on its text;
Paste from the toolbar works in a running `cat`.

