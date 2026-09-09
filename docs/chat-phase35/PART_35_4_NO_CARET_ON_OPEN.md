# CodeC Phase 35.4 — No caret until the first tap

**Status:** 🚧 IMPLEMENTED · **Cost:** `[client-only]` · **Effort:** S

## Symptom (owner)

*"open a file initialization the corsure to the top of the file i want it to
be fully remove if user clicks anywhere the cursor than start working"* —
opening a file places a caret at the very top; the owner wants **no cursor at
all** on open, with the caret appearing only when (and where) the user taps.

## What exists today (evidence)

Before this phase, `EditorViewModel.openFile` set `_codeText.value =
tab.buffer`, where `EditorTab`'s buffer is `TextFieldValue(normalized)` —
`TextFieldValue` defaults `selection` to `TextRange.Zero` (0,0).
`SoraEditorHost` replayed that as a caret at line 0 column 0 (top-left), and
`refreshDecorationsNow` computed `_cursorPos` from `selection.min = 0` →
"1:1". So every opened file started focused-at-top with a visible caret.

## Design

1. **A per-open "caret not yet placed" state** (a pure placement policy plus
   a VM `StateFlow`, reset whenever a file is opened or activated).
2. While unplaced, `SoraEditorHost` renders the editor with **no insertion
   caret** and performs **no scroll-to-caret** on open (the file opens from
   the top *view* but with no cursor drawn, and no forced position).
3. The **first tap** places the caret exactly where it landed (sora already
   routes taps to caret placement) and clears the flag; from then on the
   editor behaves exactly as today. Taps that are actually scrolls / selection
   drags also place the caret at their anchor — the rule is "any user click
   starts the cursor".
4. **Hardware-keyboard and CodeC-Keys paths** — a cap press while the caret is
   unplaced first places the caret at a sensible origin (end of line 1) then
   applies the key, so typing can never land on a "nowhere" caret.
5. **Undo/dirty/decoration** must not assume a valid caret: `refreshDecorationsNow`
   skips bracket/current-line work while unplaced (no cursor → nothing to
   highlight).

## Exit condition

```text
(Device)
1. Open any file: no caret is drawn, no jump-to-top animation — the editor is
   "quiet".
2. Tap anywhere: the caret appears exactly there and typing works normally.
3. Press a CodeC Keys cap with no caret: text lands at a sensible position,
   caret appears with it.
4. Re-open the file: the quiet state returns (no remembered stale caret unless
   the user left one — i.e. this is per-open, not per-file).
PASS = all four.
```

## Tests

- Pure `CaretPlacementPolicy`: `(unplaced, action: TapAt|KeyPress|Open) →
  (placed?, caret)` — pins exit conditions 2–4 as one law.
- VM test: opening a file leaves the placement flag unset; a tap/keys event
  sets it (host-testable via the existing VM pattern).
