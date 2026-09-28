# Phase 69.1 — Typing, keyboard and selection

**Date:** 2026-09-27. **Branch:** `arena/01a0e49f-codec`, based on `main` @
`b297765` (the merge of Phase 68.1 / PR #90).
**Status:** implemented; **Android CI ✅ GREEN on `f98ae5c`
([run 36350567066](https://github.com/pabi277/CodeC/actions/runs/36350567066))**.
**No PR opened, nothing merged** — the owner's instruction is required for that.

**Phase 69.3** (2026-09-28) followed in the next chat — two more owner reports:
*"The 3 ber open the editor but it have a gap side of that make it if user clicks
the empty space it will close the 3 ber"* and *"The quick keys are sensitive even
i want to drag for other keys it's types which ever i am scrolling"*. Full
section at the bottom of this file; the brief is
[`PHASE_69_3_STRIP_AND_KEY_DRAG.md`](../ui-polish-chats/PHASE_69_3_STRIP_AND_KEY_DRAG.md).

**Phase 69.2** (2026-09-28) arrived in the same chat, right after this delivery:
the owner reported two problems — *"What language i am using don't matter it
always give me same fixed quick keys"* and *"Sometimes the ghost suggestions
text are way too real i think as i wrote the wrong word then about a second it
vanished the ghost suggestions fix it"*. Both were investigated on this checkout,
answered before any code, and implemented as one part. **The full section is at
the bottom of this file**; the brief is
[`PHASE_69_2_LANGUAGE_KEYS_AND_GHOST.md`](../ui-polish-chats/PHASE_69_2_LANGUAGE_KEYS_AND_GHOST.md).

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
  unchanged by design.
- **Android/Compose compile + host test task:** `:app:testDebugUnitTest` on CI,
  plus `assembleDebug` and the measure-only release build — all recorded below.
  Nothing here claims a device pass.
- **Real device evidence:** **none in this chat.** The air rule and the row's
  memory are visible behaviours a still image cannot prove; the owner's handset
  round is the only honest evidence, and it has not been run (the owner declined
  device rounds for the previous delivery, so nothing is asked for by default).
  What the shots DO establish is the boundary the part respects: 122157
  (keyboard down) and the drawing `08-bottom-comparison.png` show the status line
  then the touch row, and 124105 (keyboard up) shows the chip row + touch row
  above the system keyboard with the caret's blue drop hanging under the caret —
  none of which this part moves.

### CI

Two rounds, and the first one earned its keep:

**Round 1 🔴 for-cause — [run 36350232681](https://github.com/pabi277/CodeC/actions/runs/36350232681), code
`e7a11d9`.** The Phase 52 host step reported **2260 tests completed, 2 failed** —
both failures were self-inflicted and both were mine, not the platform's:

1. `CaretCallSiteTest > exactly one ensurePositionVisible call site exists in the
   app` — that test pins the *raw text* of `app/src/main` and found TWO files
   with the literal. The second hit was this phase's own policy KDoc, which
   spelled the call with its parentheses while documenting why a margin argument
   does not exist. The doc now names the call and its `(line, column)` pair
   without the literal; the *meaning* is unchanged and the pin still enforces
   one owner.
2. `CaretVisibilityPolicyTest > the reveal target is total …` — my new
   expectation said a caret past the end of a *shorter* buffer reveals line 0;
   the policy (correctly) clamps to the **last** line. Test expectation
   corrected, with the intent written next to it.

Fix commit `f98ae5c` changed **no production behaviour**: one comment and one
test expectation.

**Round 2 ✅ GREEN — [run 36350567066](https://github.com/pabi277/CodeC/actions/runs/36350567066), code `f98ae5c`** (the
tested head; this documentation finalisation does not change tested code).
Every step passed: icon assets, release-notes template, signing resolution, the
**host unit and screenshot step (Phase 52)**, `assembleDebug`, the measure-only
release build, the release APK set check and the artifact uploads. Reported APK
facts: **debug `CodeC-IDE-1.3.17-universal-debug.apk` = 26 021 972 B**, **release
`CodeC-IDE-1.3.17-universal.apk` = 6 797 484 B** (release manifest: no
`android:debuggable` flag). Release-publish steps stay skipped by design on a
branch push (publishing is `app-v*`-tag-only).

What the green run does **not** establish: any handset behaviour. The host suite
proves the pure policy's arithmetic, the pin set and that the Compose half
compiles and the screens still measure; it cannot prove that the row stays where
you scrolled it or that the caret gains air above a real keyboard.

### Local prevalidation

None possible: no JDK/`kotlinc` in the sandbox (`which java javac kotlinc` →
empty, `JAVA_HOME` unset, no `/usr/lib/jvm`) and no network for a toolchain, so
CI is the executor of record — the same position Phase 68.1 was in. Every pinned
literal was re-read against the real source before the push, which is exactly how
round 1's two reds were found by CI rather than by the compiler here.

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

---

# Phase 69.2 — the quick-key row's language, and the ghost's suggestion look

**Date:** 2026-09-28. **Status:** implemented on `arena/01a0e49f-codec`.
**No PR, nothing merged.** Owner reports, verbatim: *"What language i am using
don't matter it always give me same fixed quick keys"* and *"Sometimes the ghost
suggestions text are way too real i think as i wrote the wrong word then about a
second it vanished the ghost suggestions fix it"*.

## Owner answers (verbatim, taken before any code)

| # | Question | Answer |
|---|---|---|
| Q1 | Where should the language's caps sit? | *"Can it be auto detection my file extension and set the the quick keys order as per requirement and if it is not a standard file than a default quick key option"* |
| Q2 | Keep today's caps or Spck-like real caps? | **B — Give each language a few real caps (recommended, Spck parity)** |
| Q3 | What should change about the ghost? | **A — Both: unmistakable look + stop it vanishing (recommended)** |

## What was found (read on this checkout, not from memory)

- The quick-key row was `GENERAL` — 14 caps (`TAB () {} [] <> "" '' ; / = ← → ↑ ↓`)
  — with the file language's caps **appended after them**, in slots 15+. About
  six or seven caps fit a phone width and the row opens at offset 0, so the
  language caps were **off-screen in every language**: the report was right.
  Old tails: C `->`; C++ `->` `::`; Python `:` `_(self)`; HTML `</>`; CSS `:` `;`;
  JS/TS `` `` `` `=>`; shell `$`; JSON `:` `,` `null`; **no caps at all for Go,
  Rust, PHP, Ruby, Lua, XML, YAML, Markdown, Text**.
- The language is the file's **extension** only (`LanguageType.fromFileName`);
  there is no picker. `EditorKeySet.languageMacroRow` existed but was never
  called (28.2 round 3 made CodeC Keys language-independent by decision).
- The ghost was **plain code text**, full size, no box, theme comment colour at
  38 % alpha: measured 1.82–1.98:1 against the editor background on the four
  shipped themes, where real code text is 11.25–13.94:1. So "too real" was a
  missing **container**, never brightness.
- Two code paths could take the ghost away: **any** `ScrollEvent` (G4) — and the
  editor scrolls itself to follow the caret, including the 48/69 air rule's
  rescroll when the keyboard settles — and **any** recompute, because the
  debounced engine pass (~120 ms–1 s after the keystroke) landing without the
  aligned item dropped a still-correct ghost with nothing the user had done.
  Phase 69.1 had touched neither the look nor the rules.

## What changed

1. **The quick keys answer to the file** (`9c7a74d`). New pure
   `ui/editor/LanguageQuickKeys.kt` (the file language's caps, Spck-parity
   content: C `#include` `printf` `int` `->` · Python `def` `print` `:` `_(self)` ·
   HTML `<tag>` `div` `class` `</>` · JS/TS `log` `=>` `` `` `` `function` ·
   CSS `color:` `px` `:` `;` · JSON `:` `,` `null` · Shell `echo` `|` `#!` `$` ·
   Go/Rust/PHP/Ruby/Lua/XML/YAML/Markdown each with their own three to five).
   `keysFor` order is now language caps → the user's custom snippets → the
   general set; `languageTail` deleted; `languageMacroRow` shares the table; a
   non-standard file keeps the default row (TEXT / unknown extension); a language
   change returns the row to its head (within one file the 69.1 remembered
   position stands). **Nothing that shipped before was removed** — every old cap
   is in the new lists, JSON's `: ` popup and the true/false flicks
   regression-pinned.
2. **The ghost reads as a suggestion and stops vanishing** (`d5420f0`). The hint
   is drawn inside a suggestion box (sora's own inlay-hint rounded-background
   family) filled with the comment colour at **18 %**, text unchanged at **38 %**
   (G5); the box starts at the caret and the measured width covers it. A
   still-correct ghost is held across a background refresh
   (`GhostCompletion.heldWhenStillValid` = the painted item re-aligned by the
   same rule `compute` uses, i.e. exactly what `accept` would still accept), and
   G4's clear-on-scroll is narrowed to the **user's own** scrolls
   (`CAUSE_USER_DRAG` / `CAUSE_USER_FLING`; causes verified against sora 0.24.6
   `event/ScrollEvent.java`) so the editor's own caret-follow scroll can no
   longer delete the suggestion.

## Tests

`LanguageQuickKeysTest` (new, 7) · `EditorKeySetTest` (two old "tail, last" pins
rewritten with their reason into four order/fallback/ownership cases) ·
`RunKeySetTest` (one stale 22.x pin moved — found by CI round 1) ·
`EditorRowsWiringTest` +1 (the head-of-row reset) · `GhostCompletionTest` +4 (the
hold) · `GhostWiringTest` (new, 4: the box, the themed colours, the one guarded
hide-on-scroll call site, the one hold call site) · `GhostContrastTest` (new, 3:
both alphas re-derived from `EditorThemes.kt`, the ghost ≤ 2.6:1 and < 30 % of
real text contrast on all four themes, the box visible but never a filled
component, and the ghost legible inside its box).

## Validation

| What | Where | Result |
|---|---|---|
| Source tests (host JVM) | CI `:app:testDebugUnitTest` | ✅ runs below |
| Android tests (instrumented) | — | none for this part; nothing claimed |
| Device evidence | — | **not claimed** — no round run, none asked for |

What only a handset can confirm (recorded, not requested): that the language
caps are the ones a thumb wants in a real file, and that the box reads as a
suggestion on the owner's screen.

### CI

- **Round 1 — red for cause**, run
  [36353374750](https://github.com/pabi277/CodeC/actions/runs/36353374750) on
  `9c7a74d`: *"2270 tests completed, 1 failed"* — `RunKeySetTest > keysForContext
  keeps the per-language tail for C` still asserted the row's language cap was
  the **last** entry, which is exactly what this part changes. The fix
  (`f29fcfd`) moved only that pin: no production code changed.
- **Round 2** (`f29fcfd`) — green, run
  [36353691179](https://github.com/pabi277/CodeC/actions/runs/36353691179), 11m51s.
- **Round 3 — red for cause**, run
  [36353710831](https://github.com/pabi277/CodeC/actions/runs/36353710831) on
  the tip `d5420f0`: the only failure was `:app:compileDebugUnitTestKotlin` —
  two `Unresolved reference 'Visible'` in this part's own new test cases
  (`GhostCompletion.Visible` should have been `GhostState.Visible`). Main code
  compiled in the same run, so the production change was never in question; the
  fix is the two test lines only (`b43432d`).
- **Round 4 — tip green**, run
  [36354523369](https://github.com/pabi277/CodeC/actions/runs/36354523369) on
  `b43432d`, 11m26s: the full suite plus this part's 11 new cases, both APKs
  built (debug 26,024,564 B / release 6,796,488 B).

Both reds were found by CI, not by a compiler here: this sandbox has no JDK
(`which java javac kotlinc` → empty, `JAVA_HOME` unset, no `/usr/lib/jvm`), so
CI is the executor of record, as it was for 68.1 and 69.1. Every literal of the
new pins was re-read against the live sources before each push; the contrast
law's four themes and the 15 wiring literals were additionally reproduced by
hand, which is how round 3's test-only typo was isolated to the test source.

## Boundaries

No new dependency, permission, persistent preference, telemetry, screen, engine,
row, button or setting. The one coding row stays one row (68.1); nothing was
removed from it; CodeC Keys stays language-independent by its own 28.2 decision;
`keyStripJson` still controls only the general base; the system keyboard stays
the default and CodeC Keys stays opt-in.

**Stop point:** this part only. No PR, no merge, no `main` push without the
owner's explicit instruction.

---

# Phase 69.3 — the strip beside the panel closes it, and a drag never types a key

**Date:** 2026-09-28. **Status:** implemented on `arena/01a0e49f-codec`
(tip `df6c654`), **CI ✅ GREEN** (run 36378830783). **No PR, nothing merged.**
Owner reports, verbatim: *"The 3 ber open the editor but it have a gap side of
that make it if user clicks the empty space it will close the 3 ber"* and *"The
quick keys are sensitive even i want to drag for other keys it's types which ever
i am scrolling"*.

## Owner answers (verbatim)

| # | Question | Answer |
|---|---|---|
| Q1 | What should the strip beside the ☰ panel be? | **A — Tap there closes it, strip stays undimmed (recommended)** — accepted cost: the green play in that strip takes two taps while the panel is open |
| Q2 | How strictly must a cap tell a tap from a drag? | **A — The phone's own touch slop (~8dp) — a drag never types (recommended)** |

## What was found (read on this checkout)

- The ☰ panel is 85 % of the width and leaves a 15 % strip of live editor
  beside it. Phase 55 had written that strip's tap off to Material3's modal
  scrim, **read out of Material3's source and never off a phone** — its device
  round was never run. The owner's report is the first device evidence, and it
  says the tap closed nothing.
- Three rows scroll (keys, run keys, suggestion chips) and all three carried
  their own copy of a **20 dp** "this was a scroll" threshold, while the row
  itself starts scrolling at the platform's **touch slop (8 dp)**. Every drag
  between the two scrolled the row **and** typed the cap. The arrows'
  hold-repeat (150 ms) fired into slow drags, and the loops threw the up change
  away. `KeyGestureDetector.classify` — 26.1's advertised "pure state machine" —
  turns out to have no production caller at all.

## What changed

1. **The strip closes the panel.** `SidePanelPlan.STRIP_WIDTH_FRACTION` =
   `1 − PANEL_WIDTH_FRACTION` (the panel's complement by construction); a box
   that wide, full height, aligned to the end (RTL-correct), composed **after**
   the drawer so it sits above it and above the scrim, `indication = null` (the
   shot's undimmed strip), `clickable` (it consumes the tap), closing through
   `closeDrawer(DrawerCloseReason.SCRIM)` → `DrawerPolicy`. Present only while
   the panel is open or opening (`targetValue`).
2. **A drag never types.** One pure rule `KeyGestureDetector.isScrollDx`, one
   slop (`LocalViewConfiguration.current.touchSlop`, pixels — the same value the
   row's scroller uses) for all three rows; the up counts as part of the
   gesture; the arrows' hold-repeat step is guarded by `!isScroll`.

## Tests

`KeyGestureDetectorTest` +3 (the slop rule) · `KeysScrollCancelWiringTest`
(new, 4: no row keeps its own threshold, all three ask the one rule, the
hold-repeat guard, the up is measured) · `SidePanelPlanTest` +1 (the strip is
the panel's complement) · `DrawerWiringTest` +1 (the strip's width/side/
lifetime/SCRIM reason/nothing drawn/composed after the drawer). **Nine new host
cases.**

## Validation

| What | Where | Result |
|---|---|---|
| Source tests (host JVM) | CI `:app:testDebugUnitTest` on `df6c654` | ✅ run 36378830783, 9m29s |
| Android tests (instrumented) | — | none for this part; nothing claimed |
| Device evidence | — | **not claimed** — no round run, none asked for |

CI round 1 was red with two compile errors of this part's own making
(run 36378529137): `fillMaxHeight` had no import in `EditorScreen`, and
`ViewConfiguration.touchSlop` is **pixels**, not `Dp`, so `.toPx()` did not
apply. Both fixed in `df6c654` with no behaviour change; round 2 is green with
both APKs (debug 26,025,252 B / release 6,798,268 B).

## Boundaries

No new dependency, permission, preference, telemetry, screen, row, button or
setting; the panel's look, rail, slots and tree, the bottom bar, the drawer
close law (47.1), back precedence (49.1) and the tour's `drawerOpen` fact are
untouched; vertical swipe layers, popups, flicks and hold-repeat timings are
unchanged.

**Stop point:** this part only. No PR, no merge, no `main` push without the
owner's explicit instruction.
