# Phase 69.1 — Typing, keyboard and selection

**Status: implemented and Android CI ✅ GREEN on `arena/01a0e49f-codec`
([run 36350567066](https://github.com/pabi277/CodeC/actions/runs/36350567066),
tested code `f98ae5c`)** — owner answers taken before any code; see the answer
block below. Round 1 was red for-cause on two self-inflicted pins, fixed in
`f98ae5c` (a comment and one test expectation, no production behaviour). Based on
`main` @ `b297765` (the merge of Phase 68.1 / PR #90). One part = one chat. No
deadline, dependency, new control or replacement engine promised; no device-round
evidence claimed, and no PR/merge without the owner's instruction.
Implementation record: [`chat-phase69/README.md`](chat-phase69/README.md).

## Copy into a new chat

> Review `docs/journal/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## Reference boundary (re-checked this session)

Winning references, in order of authority:

1. `docs/reference/spck-ui/Screenshot_20260922_122157_Spck Editor.jpg` — **keyboard down**.
   Bottom cluster, top to bottom: **status line** (`Ln 81, Col 28   Sp: 4  HTML
   LF  UTF-8`) and only then the **touch row** (8 icon caps), then the gesture
   pill.
2. `docs/reference/spck-ui/Screenshot_20260922_124105_Spck Editor.jpg` — **keyboard up**.
   No status line at all; code, then **two** rows (a per-language symbol row
   `<tag> div class = "" ✏` and the extra-key row `→| ^ v < > 💬 ⌘ >>`), then
   Spck's own docked toolbar, then the system keyboard. The caret's blue drop
   hangs under the caret at `</html>`.
3. `docs/reference/spck-ui/08-bottom-comparison.png` (spec drawing) — same order as
   (1): status line, then the touch row.

Runtime IME behaviour cannot be proved from a still image. **No claim in this
brief is device evidence for CodeC.**

## What already works (read on this checkout, not from memory)

| Area | State | Evidence |
|---|---|---|
| System IME is the default | **Works.** CodeC Keys is opt-in; the store default is `false` and the screen's first frame agrees (`initial = false`) | `SettingsManager.kt:141`, `EditorScreen.kt:391` |
| One IME lever, one owner | **Works.** sora's soft input follows CodeC Keys and is restored on dispose; pinned so no future feature can switch the IME from somewhere new | `EditorScreen.kt:677-678`, `ImeLeverTest` |
| The keys row stays mounted | **Works.** Only an explicit collapse or an interactive stdin run takes it away; a transient IME change does not | `KeysStayPolicy`, `KeysStayPolicyTest` |
| Caret never hides behind the keyboard | **Works (Phase 48, device-passed).** Height-keyed pure policy + ONE `ensurePositionVisible` owner, posted after layout, coalesced, `runCatching`, `noAnimation`; the narrowness pin (same height = no scroll, so the view never fights the user's hand); both VM→sora replay paths follow the caret | `CaretVisibilityPolicy.kt`, `SoraEditorHost.kt:430,434,557,572`, `CaretCallSiteTest`, `CaretVisibilityPolicyTest` |
| Caret blink | **Works.** Solid during a typing burst, sora's blink re-armed after 500 ms, no animated caret travel | `CaretBlinkPolicy`, `SoraEditorHost` |
| Quiet on open | **Works.** Opening a file does not force a caret or a scroll; the first tap owns its exact offset | `CaretPlacementPolicy`, `SoraEditorHost` |
| Selection / caret handle | **Works and needs nothing.** It is sora's own handle, styled (`HandleStyleDrop`), coloured from one palette role, re-applied on every theme switch; dragging is sora's own touch path; no second caret may be stacked (pinned) | `SoraEditorHost.kt:121,277`, `EditorRowsWiringTest`, `PART_57_2` §3 |
| The coding row rides the IME | **Works.** The editor column is `imePadding()`'d and the row is its last child while the keyboard is up, flush on the keyboard; the status line yields its row; the chip row is gated on a typing surface | `EditorScreen.kt:2290,2316,2432`, `EditorRowsWiringTest`, `StripContext` |
| Typing itself | **Works.** VM stays the source of truth; small programmatic edits go through one incremental `Content.replace` (no full-code blink); smart typing (type-over, wrap, empty-pair, auto-indent, string-aware); snippets/Emmet/ghost/chips/panel; hardware shortcuts (Ctrl+Z/Y/R/F//, D, W, Tab, F5) | `SoraEditorHost`, `IncrementalEdit`, `SmartTyping`, `CodeCompletionEngine`, `Emmet`, `EditorScreen`'s `onPreviewKeyEvent` |
| LSP | **Works as designed.** Optional package; `LspStatus`'s law is "never block typing"; no prompt, no modal | `lsp/LspStatus.kt` |

## What I recommend changing — three details, one part

All three come from comparing the real shots and the drawing against the current
source. None is a redesign, none adds a dependency, permission, preference or
telemetry, and none adds a second toolbar.

1. **The bottom cluster's order is inverted against every reference.**
   Keyboard down, CodeC draws **`[coding row][status line]`**
   (`EditorScreen.kt:2290` then `:2316`); 122157 and the drawing both show
   **`[status line][coding row]`**. The row's own comment claims "Spck bottom
   order", so the code and the references disagree. Consequences: the row you
   reach for most sits one row *higher* than in the reference — further from the
   thumb — and the status line takes the bottom slot. With the keyboard up
   nothing changes (the status line already yields).
2. **The coding row forgets where you left it.** The row is horizontally
   scrollable and the caps you reach for most (`;`, `/`, `=`, then `← → ↑ ↓`)
   sit in its right half, so it gets scrolled — but its scroll state is
   `rememberScrollState()` **inside** `EditorKeysRow` (`EditorKeysRow.kt:74`),
   and the row is composed at **two** different call sites (`:2290`, `:2432`)
   plus three different branches inside `BottomStrip` (Keys / Suggestions /
   Run). Composing a different branch means a fresh `remember`: the row snaps
   back to its left edge every time the keyboard opens or closes, and every time
   chips appear or disappear.
3. **"One line of air" is declared but not enforced.** `KEEP_LINES_BELOW = 1`
   (`CaretVisibilityPolicy.kt:63`) records that the caret must not sit on the
   last visible row, and the file says honestly that sora's
   `ensurePositionVisible(line, column)` has no margin argument — so today the
   guarantee is only "the caret's row is visible", i.e. the caret may sit flush
   on the bottom edge with the keyboard under it. Sora already has the lever for
   the margin: ask it for the position **one line below** the caret. Sora clamps
   a line past the end, so the last line of a file degrades to exactly today's
   behaviour.

Deliberately **not** recommended:

- Growing the caps. CodeC's cap is 40 dp tall with 6 dp of row padding
  (`EditorKeysRow.kt:190`); the reference's touch-row band measures ~118 px in
  the 1080-wide shot, i.e. the same range. There is nothing to gain by adding
  height, and the owner's rule is "no extra spaces".
- A second symbol toolbar, or forcing CodeC Keys. The shots show Spck using two
  or three bands above its keyboard; CodeC matches on one. The brief's starting
  point stands.
- Restyling the handle. It is already sora's own drop in the reference's blue.

## Questions asked before code — 2026-09-27

The owner's answers are recorded verbatim below (option labels as chosen, plus
any free text), and they are the owner's, not the reviewer's.

**Q1 (the brief's own question). Should the system keyboard remain the default,
and do you want any change to the symbol row?**

**Q2. Caret visibility — should "one line of air" become real behaviour?**

**Q3. Key-row reach — should the row keep its horizontal position, and should
its order (or position, see detail 1) change?**

**Q4. Selection and caret handles — keep sora's own, or restyle?**

**Q5. Keyboard overlap — should the four bottom tabs stay reachable while the
keyboard is up?**

**Owner answers — 2026-09-27 (option labels verbatim):**

1. System keyboard / symbol row — **A — Keep the system IME default and keep the
   one coding row exactly as it is.** (No second toolbar, no forced CodeC Keys,
   no new cap; nothing to change in this area.)
2. Caret visibility — **A — Yes, add the one-line-of-air rule.** (When a
   re-scroll is already owed, ask sora for the position one line BELOW the caret
   so it never rests on the last visible row; at the end of a file the clamp
   makes it today's behaviour.)
3. Key-row reach — **A — Remember the row's horizontal position (and nothing
   else).** (Caps, order and height unchanged; no move of the bottom cluster.)
4. Selection and caret handles — **A — Keep sora's own blue drop exactly as it
   is.**
5. Keyboard overlap — **A — Keep it: no bar and no handle while typing.**

So the bounded part is exactly two changes: **(1)** the caret's one line of air
inside the existing Phase-48 owner, and **(2)** the coding row's remembered
horizontal position. Items 1, 4 and 5 are recorded as **verified, not changed** —
the record states what proves them.

## Implementation and exit, after agreement

SoraEditorHost, EditorKeysRow, CaretVisibilityPolicy and the editor's keyboard
layout; protect undo, selection and composition correctness. **The agreed part is
two changes across four production files** — the air rule (pure policy + the one
Phase-48 owner) and the row's remembered position (one hoisted `ScrollState`
threaded to the row) — with the pins that make them stick; see the record for the
exact pins, the one that moved, and its reason.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.
