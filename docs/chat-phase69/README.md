# Phase 69.1 — Typing, keyboard and selection

**Date:** 2026-09-27. **Branch:** `arena/01a0e49f-codec`, based on `main` @
`b297765` (the merge of Phase 68.1 / PR #90).
**Status:** implemented; Android CI run recorded in §Validation below.
**No PR opened, nothing merged** — the owner's instruction is required for that.

Reviewed first, then asked, then coded: the current implementation and the real
reference shots were re-read before a single question was put, and the owner's
five answers were taken **before** any code was written (`ask_user`, 2026-09-27).
They are recorded verbatim in the brief
([`docs/ui-polish-chats/PHASE_69_1_TYPING.md`](../ui-polish-chats/PHASE_69_1_TYPING.md))
and repeated here.

## Owner decisions (option labels verbatim)

| # | Question | Owner's answer |
|---|---|---|
| Q1 | Keep the system IME as default / change the symbol row? | **A — Keep the system IME default and keep the one coding row exactly as it is** |
| Q2 | Make "one line of air" real behaviour? | **A — Yes, add the one-line-of-air rule** |
| Q3 | What to change about the key row's reach? | **A — Remember the row's horizontal position (and nothing else)** |
| Q4 | Selection/caret handles? | **A — Keep sora's own blue drop exactly as it is** |
| Q5 | Bottom tabs while the keyboard is up? | **A — Keep it: no bar and no handle while typing** |

Standing decisions carried in: Phase 64's no-guide/no-installation-UI-lock; the
Phase 68.1 Spck-parity rule (*"I don't want to use any extra spaces just make it
like spck"*); no new dependency, permission, persistent preference or telemetry;
pure host-tested policies first.

## What was found working (and is therefore NOT changed)

Read on this checkout, not from memory:

- **System IME is the default; CodeC Keys is opt-in.** `SettingsManager.kt:141`
  (`codecKeysEnabledFlow … ?: false`) and `EditorScreen.kt:391`
  (`initial = false`), pinned by `KeyboardDefaultTest`, `ImeLeverTest`,
  `KeysStayPolicyTest`. Q1 = A changes nothing here.
- **The caret never hides behind the keyboard** — Phase 48, device-passed via
  PR #79: a pure height-keyed policy + ONE `ensurePositionVisible` owner, posted
  after layout, coalesced, `runCatching`d, unanimated, with the narrowness pin
  (same height ⇒ no scroll) so the view never fights the user's hand.
  Q2 = A **extends** this owner; it does not replace it.
- **The selection/caret handle is sora's own drop**, styled and theme-aware
  (`SoraEditorHost.kt:121,277`, `CodecPalette.CARET_HANDLE`), draggable through
  sora's own touch path, with "no second caret stacked on the editor" pinned by
  `EditorRowsWiringTest` and a device row in `chat-phase57/DEVICE_ROUND.md` (R6).
  Q4 = A leaves it exactly as it is.
- **Typing itself**: VM is the source of truth, small programmatic edits go
  through one incremental `Content.replace` (`IncrementalEdit`, the 2026-09-13
  blink fix), smart typing, snippets/Emmet/ghost/chips/panel, hardware
  shortcuts, and `LspStatus`'s "never block typing" law. Untouched by this part.

## What was changed — two things, both chosen by the owner

### 1. The caret's one line of air (Q2 = A)

**The defect, from sora 0.24.6's own source** (`CodeEditor.ensurePositionVisible`,
read 2026-09-27; the file was fetched and quoted, not guessed):

```java
float yOffset = layoutOffset[0];               // the BOTTOM of the caret's row
if (yOffset - getRowHeight() * topLines < currFinalY) { … }   // top invisible
if (yOffset > getHeight() + currFinalY) {                     // bottom invisible
    targetY = yOffset - getHeight() + getRowHeight() * 1f;    // ← one row of air
}
…
if (withinDelta(targetX, getOffsetX(), 1f) && withinDelta(targetY, getOffsetY(), 1f)) {
    invalidate(); return;                       // already visible → NOTHING happens
}
```

So sora leaves a row of slack **only when it has to reveal a row that is off
screen**. When the caret's row is still on screen its early return does nothing —
including the worst case, the caret's row being the **last visible row**, where
the caret sits flush on the keyboard's edge and its drop handle (sora draws it
*below* the row) is clipped off the visible box. Phase 48 declared
`KEEP_LINES_BELOW = 1` and recorded that as an honest debt; this part pays it
without a margin parameter:

- **Pure policy** (`ui/editor/CaretVisibilityPolicy.kt`): `revealLine(caretLine,
  lineCount)` = the caret's line + `KEEP_LINES_BELOW`, clamped into the buffer;
  `revealColumn(caretColumn, targetLineColumnCount)` = the caret's own column,
  clamped to the neighbour line (so a horizontally scrolled long line stays put —
  sora's x branch is the same for both rows). Both are total: a stale schedule
  (tab switch, undo) can never index outside the buffer.
- **The one owner** (`ui/editor/sora/SoraEditorHost.kt`): `scheduleCaretRescroll`
  now computes those two values inside the same posted, coalesced, `runCatching`d
  task and calls `ensurePositionVisible(targetLine, targetColumn, true)` — the
  same single call site, still unanimated. **The call sites did not move**: both
  VM→sora replay paths still schedule `(endPos.line, endPos.column, 0L)`, so the
  `CaretCallSiteTest` count pin (exactly 2) still holds.
- **Exactly when it can fire:** the next line is off screen — i.e. the caret's
  row is the last visible one. Mid-screen carets, the quiet-on-open state, and
  the Phase 48 narrowness pin are all unaffected; at the last line of the buffer
  the clamp returns the caret's own line, which is the pre-69.1 call (no new
  scroll at EOF). The air comes from the **viewport**: nothing moves the caret
  and nothing writes the buffer (both pinned).
- **Honest boundary:** sora's *own* edit-driven scroll (plain typing) is
  untouched — only a re-scroll the owner already owes (chrome resize, or a
  VM-driven caret move such as a suggestion accept, find-next, keys-row insert)
  carries the air.

### 2. The coding row remembers its position (Q3 = A)

The caps a phone thumb reaches for most (`;`, `/`, `=`, then the arrows) live in
the row's right half, so the row gets scrolled — but its `rememberScrollState()`
lived **inside** `EditorKeysRow`, and that row is composed at **two** call sites
(keyboard down at `:2290`, keyboard up at `:2432`) and in three branches of the
one strip (`BottomStrip`: Keys | Suggestions | Run). Every keyboard toggle and
every chip appearance therefore composed a fresh state and snapped the row back
to its left edge.

- One `ScrollState` is now owned by `EditorScreen` (`keysRowScroll`), passed
  through `BottomStrip(keyRowScroll = …)` at **both** call sites into
  `EditorKeysRow(scrollState = …)`, which scrolls through it
  (`horizontalScroll(scrollState)`).
- Caps, order, height and the bottom cluster's order are **unchanged** (owner
  answered A, not B/C: no reorder, no move). The run keys and the suggestion
  chips keep their own row positions on purpose — different content, and 57.2's
  geometry is theirs (pinned).

## Tests

Local execution is impossible in this sandbox (no JDK, no network — the same
position as Phase 68.1), so the checks below are written to be executed by CI,
and every literal was re-read against the real source before pushing.

- **`CaretVisibilityPolicyTest`** (+5 cases, the air rule): the target is the
  caret plus one line; at EOF it is the caret's own line; the target is total for
  empty/stale buffers and negative lines; the column keeps the caret's own column;
  the column clamps against a shorter neighbour. The Phase 48 cases (null
  previous, the five triggers, the narrowness pin, the debounce split,
  `KEEP_LINES_BELOW == 1`) are untouched and still pass.
- **`CaretCallSiteTest`** — one pin MOVED with its reason (recorded in the test's
  own comment): the guard assertion pinned the literal
  `runCatching { editor.ensurePositionVisible(line, column, true) }`, which the
  new target reintroduces for a reason, so it now asserts `runCatching {` plus
  `editor.ensurePositionVisible(targetLine, targetColumn, true)` (still
  unanimated). **New**: the owner must ask through the pure policy
  (`revealLine(line, editor.text.lineCount)`, `revealColumn(…)` with
  `getColumnCount(targetLine)`) and must never move the caret (`setSelection(`)
  or write the buffer (`text.replace(` / `.setText(`) — the air is a viewport
  effect. Unchanged: exactly ONE `ensurePositionVisible(` in `app/src/main`,
  posted, coalesced, and the two `scheduleCaretRescroll(endPos.line, …)` replays.
- **`EditorRowsWiringTest`** (+1 test): the screen owns
  `val keysRowScroll = rememberScrollState()`; both strip call sites pass that
  same state (count pinned at 2); the strip hands it to the row;
  `EditorKeysRow` declares `scrollState: ScrollState = rememberScrollState()`,
  scrolls `horizontalScroll(scrollState)`, and its caps row must **not** own a
  private state again — while `RunKeysRow` deliberately keeps its own.

## Validation

Recorded precisely, in the phase system's three separate columns:

- **Source tests (host/CI):** the three files above; the rest of the suite is
  unchanged by design. CI is the executor of record (see the run below).
- **Android/Compose compile + host test task:** `:app:testDebugUnitTest` on CI,
  plus `assembleDebug` and the measure-only release build — all recorded in the
  run below. Nothing here claims a device pass.
- **Real device evidence:** **none in this chat.** The air rule and the row's
  memory are visible behaviours a still image cannot prove; the owner's handset
  round is the only honest evidence, and it has not been run (the owner declined
  device rounds for the previous delivery, so nothing is asked for by default).
  What the shots DO establish is the boundary the part respects: 122157
  (keyboard down) and the drawing `08-bottom-comparison.png` show the status line
  then the touch row, and 124105 (keyboard up) shows the chip row + touch row
  above the system keyboard with the caret's blue drop hanging under the caret —
  none of which this part moves.

## Boundaries

Phase 64 (no guide, no installation UI lock, no new app-wide indicator), Phase
66–67 (Files-first drawer, filename search, nested creation, download/share) and
Phase 68.1 (compact top bar/breadcrumb, 40 dp tabs with the two menu doors,
compact status bar, MD preview via Run, JSON keys tail, the sort door's ✓ across
re-entry) are intact. No new dependency, permission, DataStore key, Settings
control or telemetry. No engine replaced, no guide rebuilt, no second toolbar.
The list of things the owner answered "leave it" on (Q1, Q4, Q5) is verified
above rather than re-implemented.

**Stop point:** this part only. No PR, no merge, no `main` push without the
owner's explicit instruction; the next chat starts from the next brief.
