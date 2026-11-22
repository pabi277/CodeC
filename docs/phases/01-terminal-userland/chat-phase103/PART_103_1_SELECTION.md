# CodeC Phase 103.1 — Selection: handles, floating toolbar, joined copy

> **Status:** 🚧 IMPLEMENTED on `arena/3f8a6afe-codec` (2026-11-22), Build APK ✅ GREEN
> on `df3b285` (run `44999216380`); device round owed · **Cost:** `[client-only]` · **Effort:** M
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

## Implementation record (103.1)

Built as `ui/terminal/TerminalSelection.kt` (pure: no `android`, no `androidx` — pinned
by a test) plus the Compose edge in `TerminalEmulatorView.kt`; `TerminalScreen.kt`'s copy
path lost its transcript fallback.

- **The model holds logical ends.** `TerminalSelection(range, anchor, baseCols, handle,
  anchorEnd, cursorEnd, wordText)`. `range` is always the normalized pair; the two
  logical ends are what a drag moves, so the handle you grabbed stays at your finger even
  when the drag crosses the other end and the pair inverts. Without them, dragging the
  cursor handle past the anchor silently swapped which end was being dragged.
- **Anchoring is derived, not guessed.** `TrimMath.contentShift(prevCount, nowCount,
  prevTrims, nowTrims) = −((Δcount) + (Δtrims))`. On the `extY = index − scrollbackCount`
  axis a trim is a no-op and a pushed screen row is −1; the first implementation shifted by
  the trim count and moved every selection off its text. "My text is gone" is answered by
  the buffer (`lineAt` → null) and `remembers`, never clipped silently.
- **`remembers` compares the captured word.** `TerminalSelection.word(…, text)` stores the
  pressed span verbatim (`wordText`); `remembers` re-extracts the same columns and compares.
  Different text ⇒ the selection is dropped. Empty `wordText` (Select All, a grown drag) is
  trusted — there is nothing to contradict.
- **Word rule** (unchanged from the app's first long-press): letters, digits, `_ - . / :`
  are one word — `:` was added so a long-press anywhere on `https://…` grabs the whole URL,
  which is the owner's reported case.
- **Copy law.** `copySelectionText` returns `null` for a blank/absent selection and the
  toolbar's Copy is `enabled = hasSelection`; `TerminalScreen` calls
  `copyText(context, activeSelection.orEmpty())` so a no-selection tap says *Nothing to
  copy* instead of dumping the transcript. Pinned twice (view + screen).
- **Gesture.** One `awaitEachGesture` loop decides: down-on-handle ⇒ drag that handle
  (edge auto-scroll, sign derived from `rowCap`); down elsewhere ⇒ tap (clear + keyboard, or
  a mouse event under `CAPTURE_MASK`), long-press ⇒ word selection, drag before the long-press
  ⇒ scroll. The long-press no longer emits a move until the finger travels past the slop
  (it used to collapse the new word to one cell), and a handle grab no longer snaps.
- **No haptic on the terminal surface — a rejected change, recorded.** The rewrite tried
  `performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)` on the long-press. It was
  **removed**: the terminal is one of the app's two animation-free zones (Phase 36/48) and
  `HapticWiringTest` pins it haptic-free as well (Phase 51.4). The long-press already
  answers by growing a word under the finger, and this surface is itself a BEL source — a
  buzz here is noise, not feedback. The `HapticMoment.DRAG_STARTED` route was considered and
  also refused: it would have meant the view naming a `HapticMoment`, which the same pin
  forbids.
- **Phase 40.5.** The toolbar surface is `theme.topBarBackground` (was a `0xFF2B2B2B`
  literal), its buttons use `theme.foreground` via `ButtonDefaults.textButtonColors`, and
  the view's four unused colour literals were deleted. Handles draw in `theme.cursor`.

Tests: `SelectionModelTest` 31, `SelectionExtractTest` 11, `SelectionScrollWiringTest` 16 —
host harness 85/85 (with `ScrollRestTest` 21 and `ScrollMathTest` 6). CI (`Build APK`
`44999216380` on `df3b285`) is green: compile, the full unit suite, debug + release
assembly and lint. The device round is still owed.
