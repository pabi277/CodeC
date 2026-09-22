# CodeC Phase 57 — Editor chrome

> **Status:** 🚧 **IMPLEMENTED on `arena/01a0c83e-codec`, CI ✅ GREEN** (57.1-57.3, run `35710338114` on tip `7c9619b`; device round not run) · **Cost:** `[client-only]` · **Effort:** M (57.1) + L (57.2) + S (57.3)
>
> Parent: [`PHASE54_58_PHONE_UI_ROADMAP.md`](../PHASE54_58_PHONE_UI_ROADMAP.md).
> Reference of record: `docs/spck-ui/Screenshot_20260922_122157_Spck Editor.jpg`
> (keyboard down) and `…_124105_…jpg` (keyboard up). Everything below cites this
> checkout, not a memory of it.

```text
  57.1  Top row and tab row, as the shots          🚧 IMPLEMENTED
  57.2  Status line, touch row, predictive row     🚧 IMPLEMENTED
  57.3  Pills, not dialogs                         🚧 IMPLEMENTED
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [57.1](PART_57_1_TOP_ROW.md) | Filename, tab, green triangle | M | 🚧 IMPLEMENTED |
| [57.2](PART_57_2_ROWS_AND_CARET.md) | The two rows above the system keyboard | L | 🚧 IMPLEMENTED |
| [57.3](PART_57_3_PILL.md) | One pill | S | 🚧 IMPLEMENTED |

Device round: [`DEVICE_ROUND.md`](DEVICE_ROUND.md) (R1-R11, written, **not run**).

**CI round 1 was red for cause** (run `35709258747`): two pins that the 57.1 layout had invalidated —
`EditorChromeSlotTest`'s tab-row region ended on a comment this checkout no longer carries, and
`TouchTargetTest`'s exact icon census counted 16 where the six files now hold 17 (the ⋮ `IconButton`
left the bar, the bare green ▶ `IconButton` arrived, the tab row's trailing cell added one). Both were
fixed in the 57.3 commit, and both files now carry the reason in the code that must be edited next time.

## What the shots actually show (re-read 2026-09-22, before any code)

**122157, keyboard down**

- Row 1: `☰` · the file's own HTML mark · **bold `index.html`** · … · `🔍` · **green ▶**. No
  word RUN. No ⋮. No ✕.
- Row 2 (below row 1, full width): the tab cell `[mark] index.html` with a **trailing
  bordered cell** carrying a lines-and-down-arrow glyph.
- Bottom: `Ln 81, Col 28 · Sp: 4 · HTML · LF · UTF-8`, then the **touch row**
  (`>| ∧ ∨ < > ⧉ ⌘ >>`), then the system navigation bar. No predictive row.

**124105, keyboard up**

- Rows 1 and 2 identical (the file's name and the tab row are BOTH on screen).
- **No status line**, no caret drop-less caret: the **blue drop** hangs under the caret on
  line 83.
- Above the system keyboard: the **predictive row** (`<tag> div class = ""` + a right-edge
  glyph), then the **touch row**, then the keyboard.

## What the roadmap asked for, and what this phase did

| Roadmap sentence | Result |
|---|---|
| “Remove from the top row. The word RUN. The ⋮.” | Done. The word lives on only as `contentDescription`; the ⋮ left the bar. |
| “…their new home is **not in the shots**. 57.1 asks, or leaves them on the existing overflow **off** the top row” | **Asked.** Owner answered *“Tab row's trailing cell (the shot's glyph)”*. The same `showMoreMenu` list now opens from that cell — no feature was made unreachable, no control was invented. |
| “Add. The tab row under the filename.” | Done — the tab row is now a row of its own below the bar (it used to share the title slot with the name). |
| “Hide that line while the IME is up.” | Already true on this checkout; **pinned** by a test rather than changed. |
| “The touch row from the shot, docked even when the keyboard is closed.” | Already true; **pinned**. |
| “The predictive row only while the IME is up.” | Done — `StripContext` now takes `typingSurfaceUp`; the chips dock only with a keyboard. |
| “For the blue drop … If sora already draws a handle, style that.” | **sora 0.24.6 does draw one.** Verified in the library source: `EditorRenderer` calls `getHandleStyle().draw(…, HANDLE_TYPE_INSERT, …)`, and `CodeEditor`'s default is `HandleStyleSideDrop`. So 57.2 **styles sora's own handle** (`HandleStyleDrop`) and sets its colour role; no second caret, no overlay. |
| “A pill for ‘Error opening file.’ and ‘Refreshed Files’” | Done — one pill (57.3), above the snackbar's slot, dismissed by its own timer or a tap. It also closed a real hole: **eight** silent `?: return`s on the open paths made a tap on an unreadable file do nothing at all; each now reports. Details: [PART_57_3](PART_57_3_PILL.md). |
| “A glyph may be drawn to match the shot only if tapping it does not perform a guessed action.” | One new glyph (`SpckIcons.EditorMenu`) is drawn; the cell it sits in opens the **existing** list. `>>`, the `⧉` cap and `⌘` are untouched. |

## One honest reversal, recorded

Phase 51.2 made RUN a **contained, labelled** button on the strength of Google's Material
eye-tracking study, whose own counter-example says *“removing text labels … resulted in
decreased usability”*. The owner's accepted reference has a bare green ▶ and no word.
**The reference wins** — that is the standing law of this series (*“They accept only this
UI”*), and the study is now recorded as overruled rather than silently dropped. What the
study protects is kept where it still applies: the tap keeps Phase 44's “says why” lock,
the glyph keeps a TalkBack label, and the running state still answers *“did my tap work?”*.

## Risk that came with the change (written down, not discovered later)

Moving the tab row out of the app bar makes the code view **one row shorter while tabs are
open**. That is the measurement Phase 48's caret policy keys on — and it keys on the
*measured* box (`CaretVisibilityPolicy` takes `heightPx`), so it re-measures instead of
using a stale constant. The declared chrome slots (`EditorChrome`) were updated in the same
commit, and `EditorChromeSlotTest` fails a future edit that moves a piece without saying so
— which is exactly what it did here, by design.
