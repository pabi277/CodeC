# CodeC Phase 103.1 — Selection: handles, floating toolbar, joined copy

> **Status:** 🚧 IMPLEMENTED (pure layer, commit 1 of 2) · **Cost:** `[client-only]` · **Effort:** M
> The Compose wiring (handles, toolbar, Copy-law edit) ships in the 103.2 commit — see §Implementation.
> **Owner row (verbatim):** "partial text copy not working" → selection style pick:
> "Termux-style — long-press → drag handles → floating Copy/Select-all toolbar"

## First move: evidence, not code

- `TerminalEmulatorView.kt:361` — long-press resolves a single word selection;
  `:364` sets `menu = true` in the same gesture, so the popup opens before any drag
  can extend the selection. Grep confirms **zero** handle/edge-scroll code in `app/`.
- `TerminalEmulatorView.kt:783` `selectedText()` — the extractor is correct and
  covered by `TerminalUxTest`-family cases; it is unreachable in the failing flow.
- `TerminalScreen.kt:334` — toolbar Copy falls back to the whole transcript when no
  selection text is produced (the "copied everything" symptom).
- Behavioural spec (clean-room, read 2026-10-14): Termux
  `TextSelectionCursorController` — two draggable handles, selection re-anchored as
  output scrolls, floating action mode (Copy/Paste/More), and
  `getSelectedText(joinBackLines, joinFullLines)` rejoining soft-wrapped lines.

## Design

Pure, Android-free policy in a new `ui/terminal/SelectionModel.kt` (exact name at
implementation): a state value (start/end cell in *buffer-absolute* coordinates,
anchor word, active handle) and pure transitions — `begin(word)`, `drag(handle, to)`,
`growByScroll(lines)` — plus `extract(buffer, selection): String` implementing the
join rules: soft-wrapped lines join with **no** separator, hard lines keep `\n`,
trailing spaces trimmed per hard line only. The Composable layer draws two handles
and a floating toolbar (Copy / Select all / Paste), translates drag deltas through
the *current* scroll offset + sub-pixel remainder (the :783-class row-math bug —
dragging must account for `scrollSubPx` — is pinned by a policy case), auto-scrolls
when a handle is dragged into the top/bottom edge band, and re-anchors the selection
when new output arrives. **Copy law:** Copy writes the selection or is disabled —
the transcript fallback at `TerminalScreen.kt:334` is deleted, not kept as a net.

## The Android edge

Handles/toolbar are Compose overlays inside `TerminalEmulatorView` (no new
dependency); touch targets ≥ 48 dp; colours from existing theme roles only (Phase
40.5 law). Long-press keeps haptic + word-grow on double-tap-drag if the gesture
recogniser allows without conflict — recorded either way in the implementation note.

## Exit condition

Host tests green + device rows (phase README): partial copy of a long wrapped URL
yields one unbroken string; Select-all then Copy yields the full buffer; Paste into
a running `cat` prompt works from the same toolbar.

## Tests (plan)

- `SelectionModelTest` (new): word-begin, drag both handles (incl. reverse drag),
  buffer-absolute anchoring across scrollback growth, edge cases (empty buffer,
  selection in alternate screen refused).
- `SelectionExtractTest` (new): soft-wrap join, hard-line preservation, trim rules,
  wrapped-URL golden case.
- `SelectionWiringTest` (new, source pin): the view constructs the model; the
  transcript-fallback string is gone from `TerminalScreen.kt`.
- Existing terminal suites (`TerminalUxTest`, `ScrollMathTest`, buffer/reflow)
  must stay green untouched.

## Sources (record)

1. termux-app `TextSelectionCursorController.java`, `TerminalView.java`
   (`getSelectedText`), `HandleView.java` — behaviour read from local clone
   @ `8629e63`, 2026-10-14 (audit session). GPL-3.0: behaviour only.
2. Owner device reports and picks, this session (2026-10-14).

## Deferred / rejected with reasons

- **Keep the word-popup and add "extend" buttons** — rejected: the owner picked the
  Termux handle model explicitly; a hybrid keeps the gesture conflict at :364.
- **Accessibility/IME text services for the canvas** — real gap (canvas is
  `IMPORTANT_FOR_ACCESSIBILITY_NO`), but a separate phase; recorded in the series
  research §3 as P2, not smuggled into this part.

## Implementation

**Owner split decision (2026-10-14):** the owner chose to split the pure layer
from the Compose wiring rather than hand-split the interleaved gesture code.
Consequence: **commit 1 (this part) is not user-visible.** `TerminalEmulatorView.kt`
and `TerminalScreen.kt` ship whole in commit 2 (103.2), which therefore also
carries the handles, the floating Copy/Select-all/Paste toolbar, and the Copy-law
edit. The Compose layer is host-uncompilable here; CI `Build APK` is its only
compile check.

**What commit 1 contains (pure, Android-free):**

- `ui/terminal/SelectionModel.kt` (new) — `TermSelection` (buffer-absolute
  `SelCell` start/end + active handle), `begin` (word), `drag` (handle move with
  reverse-drag normalising), `selectAll`, `clamp`, `spanOnRow` (inclusive column
  span per buffer row), `extract` (joins soft-wrapped rows with no separator,
  keeps `\n` on hard ends, trims trailing spaces per hard line only, skips
  wide-glyph continuations, expands clusters), `cellAt` (view px → cell, honours
  the sub-row remainder), `screenRowOf`, `handleAnchorX`, `edgeScrollPx`.
- `ui/terminal/TerminalBuffer.kt` — `TerminalLine.wrapped` (soft-wrap flag,
  set from the row); `TerminalSnapshot.historyBase` (count of history lines ever
  dropped, so `absRowOf(extY) = historyBase + scrollbackCount + extY` stays
  stable while output scrolls); `TerminalSnapshot.altScreen`; every history drop
  goes through `dropOldestHistory`/`dropAllHistory` so `historyBase` is exact.
  A reflow drops all history (rows re-laid), and the view drops the selection on
  a width change.
- Tests: `SelectionModelTest` (begin, drag both handles incl. reverse, absolute
  anchoring across scrollback growth, clamp, edge band, empty buffer, alt screen
  refusal), `SelectionExtractTest` (soft-wrap join; partial wrapped span `mple.c`;
  hard newlines; mid-line spaces; wide glyphs; empty buffer; a legacy
  `selectedText` agreement case that runs against `main`'s extractor in this
  commit and against the routed extractor in commit 2).

**Deviations recorded:**

- The planned `SelectionWiringTest` (source pin) moved with the view into commit
  2 as `TerminalGestureWiringTest`, together with `ScrollRestTest`.
- Legacy `selectedText(GridSelection)` kept its signature. Its row semantics
  (inclusive `x2`) match `SelectionModel.extract`, checked case by case
  (`(6,0,10,0)` → `World`; `(0,0,11,1)` → `Hello World!\nSecond Line.`;
  `(0,0,1,0)` → `AB`).

**Host verification (sandbox harness, commit 1 set):** 106 passed, 0 failed
(`SelectionModelTest`, `SelectionExtractTest` minus the legacy case that needs
the Android `components` package, plus `ScrollMathTest`, `ReflowTest`,
`TerminalUnicodeTest`, `AnsiParserTest`, `CharWidthTest`, `CellMetricsTest`,
`MouseEncodingTest`, `GlyphSpansTest`, `TerminalChromeTest`).
Not host-runnable: `TerminalUxTest`, `TerminalBufferTest` (need `components`).

**CI:** commit `f5f3748` — `Build APK` run `38506463779`: **red** at
`:app:compileDebugUnitTestKotlin` (`SelectionExtractTest.kt:94,98` unresolved
`selectedText`; the legacy extension needs an import). Fix-up `7c4b9f1` adds the import;
the harness generator now drops it together with the legacy case, so the harness can no
longer hide this. The 103.1 tip was pushed together with 103.2 by mistake and never built
alone; the full-tree build is recorded in the phase README.

**Device pass required:** yes — the device rows for the whole phase run after
commit 2 (copy of a partial wrapped URL, Select-all then Copy, paste into a
running `cat`).

