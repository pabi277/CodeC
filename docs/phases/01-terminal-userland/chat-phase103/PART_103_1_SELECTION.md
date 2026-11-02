# CodeC Phase 103.1 — Selection: handles, floating toolbar, joined copy

> **Status:** 🚧 IMPLEMENTED (2026-10-14, on the owner's "Start Phase 103") ·
> **Cost:** `[client-only]` · **Effort:** M · **Device pass: REQUIRED** (touch
> behaviour — see the exit condition below). CI: pending at push time.
>
> *(The brief below is kept verbatim; the record of what was actually built
> follows it as [Implementation](#implementation-record-2026-10-14).)*

> **Status (brief, as planned):** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
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

---

## Implementation record (2026-10-14)

Built on the owner's *"Start Phase 103"*. Everything below is real code in this
repository; nothing here is a proposal.

### Symptom → root cause → fix

| Symptom (owner) | Root cause (read at `5cbc1c0`) | Fix |
|---|---|---|
| *"partial text copy not working"* | `TerminalEmulatorView.kt` long-press built a one-word range **and** set `menu = true` in the same gesture, so the popup ended the gesture before any drag could extend it. There were no handles and no edge auto-scroll anywhere in `app/`. | Long-press now calls `SelectionModel.beginWord`, arms drag-extension (`selecting = true`) and shows the handles; the menu no longer opens from the gesture. Two draggable handles + a floating toolbar (Copy / Select all / Paste / More) sit above the terminal, and dragging into the top/bottom band auto-scrolls. |
| "it copied everything" | `TerminalScreen.kt:334` — `activeSelection ?: viewModel.transcriptText()`. With the gesture layer never producing a usable range, the fallback fired and pasted the whole session. | Deleted, not kept as a net: the toolbar Copy is `enabled = activeSelection != null`, and the menu's Copy/Share items are `enabled = selectedText.isNotEmpty()`. **Copy writes the selection or nothing.** |
| A wrapped line copied as two lines with a `\n` in the middle | `TerminalLine` did not carry the soft-wrap flag the internal `Row.wrapped` has had since Phase 19.1, so `selectedText()` could not tell a wrapped fragment from a hard line. | `TerminalLine.wrapped` is now published by `snapshot()`; `SelectionModel.extract` rejoins wrapped fragments with **nothing** between them and trims trailing blanks only at the end of a *logical* line. |
| A selection lost its text while output arrived | Extended-Y coordinates are measured from the live screen, so every new row of output slides older text a step deeper into history — and `scrollbackCount` stops moving once the 2,000-row cap is reached. | `TerminalBuffer.scrolledRows` (monotonic, published on the snapshot) + `SelectionModel.shift`: the view diffs it and shifts an open selection by the change. |

### Two things the plan changed on, honestly

1. **The planned "selection refused on the alternate screen" case was dropped.**
   Refusing would mean a user cannot copy out of `less`, `man` or a TUI — the
   exact content people copy. Selection is grid-wide, as it always was.
2. **The long-press haptic goes through the app's one adapter**
   (`rememberCodecHaptics()` → `HapticMoment.DRAG_STARTED`), not a direct
   platform call. Phase 51.4 pins the platform call to `CodecHaptics.kt` /
   `CodecKeyboard.kt` only (`HapticWiringTest`); a terminal that reached for the
   vibrator itself would break that pin **and** ignore the Settings switch.
   `DRAG_STARTED` maps to the light `TextHandleMove` tick — the right word for a
   text handle.

### Where the code lives

- `ui/terminal/SelectionModel.kt` (**new**, pure Kotlin, no Android): the whole
  selection model — `TextSelection`, `SelectionHandle`, `wordBounds`,
  `beginWord`, `drag` (returns the handle the finger now holds, so a drag that
  crosses the other edge flips instead of fighting itself), `shift`, `clamp`,
  and `extract` with the wrap-join rule.
- `ui/terminal/TerminalBuffer.kt`: `TerminalLine.wrapped`, `scrolledRows`,
  `lineAt` promoted from a view-file extension to a member of
  `TerminalSnapshot` (one definition of the coordinate system, next to the
  buffer that defines it).
- `ui/components/TerminalEmulatorView.kt`: handles + floating toolbar + the
  handle-drag overlay + edge auto-scroll + the scrolled-rows shift. The handle
  drag lives on **one** overlay keyed only on the cell metrics (never on
  `topRow`/`scrollSubPx`, which change every frame — restarting a detector
  mid-gesture is the Phase 19.2 round-3 bug) and consumes from the DOWN,
  because the terminal's own detector deliberately accepts already-consumed
  downs.
- `ui/screens/TerminalScreen.kt`: the copy law.
- `findWordBoundaries` and `TerminalSnapshot.selectedText(GridSelection)` now
  forward to the model instead of keeping a second copy of the rule.

### Tests (36 new host cases, all green in-sandbox before CI)

`SelectionModelTest` (25) — word bounds over paths/flags/spaces, long-press,
drag on both handles including a reverse drag that flips the held edge,
clamping, `shift` (including the "text scrolled out of the buffer → drop the
selection" case and the counter outliving the scrollback cap), and the copy
rules: wrapped URL as one line, a selection starting mid-wrap, hard lines,
trim rules, empty rows, backwards ranges.
`SelectionWiringTest` (11) — source pins: no `menu = true` inside the
long-press, the transcript fallback gone from both copy paths, the
handle-drag guard wired, the 48 dp target, the sub-row offset in the row math,
the haptics invariant, and the toolbar's shape.

> **Sandbox note (honest):** the sandbox has no Android SDK, so only the pure
> sources and the two new test classes were compiled and **run** locally this
> time (a JDK from `jdk4py` + `kotlinc` from npm, with a stub `org.junit`); that
> run is what caught two real defects — a `rowToLine` body left on the old
> parameter type, and a wrong expectation in my own word-bounds test. CI
> (`Build APK`: assemble + unit tests + lint) remains the executor of record.

### Deferred (with reasons)

- **103.2 (scroll physics)** — untouched, still 📋 PLANNED; it shares this view
  file and lands as its own commit.
- **`transcriptText()` still splits wrapped lines** — "Copy All" and Share are
  explicit whole-buffer actions, and fixing them is Phase 103.2-adjacent
  polish, not this part's bug.
- **Double-tap-to-select-word, and accessibility for the canvas** (the canvas is
  `IMPORTANT_FOR_ACCESSIBILITY_NO`) — recorded in the series research §3 as P2.
