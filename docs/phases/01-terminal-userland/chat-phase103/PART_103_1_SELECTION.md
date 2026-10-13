# CodeC Phase 103.1 — Selection: handles, floating toolbar, joined copy

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
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
