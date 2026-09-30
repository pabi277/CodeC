# Phase 69.2 — the quick-key row's language, and the ghost's suggestion look

**Date:** 2026-09-28. **Branch:** `arena/01a0e49f-codec`, based on `main` @
`b297765` (Phase 69.1 was still unpushed to `main`).
**Status:** implemented; **Android CI ✅ GREEN on the tip** (runs below).
**No PR opened, nothing merged** — that needs the owner's explicit word.

Reviewed first, asked second, coded third. The two problem reports came in one
message right after the 69.1 delivery; both areas were read on this checkout
before anything was proposed, and the owner's answers were taken **before** any
code (`ask_user`, 2026-09-28).

## The two reports (owner, verbatim)

1. **The quick keys** — *"What language i am using don't matter it always give
   me same fixed quick keys"*.
2. **The ghost** — *"Sometimes the ghost suggestions text are way too real i
   think as i wrote the wrong word then about a second it vanished the ghost
   suggestions fix it"*.

## What the investigation actually found

### The quick keys: the row *was* language-aware, but invisibly

Read, not guessed (`EditorKeySet.kt`, `LanguageType.kt`, `EditorKeysRow.kt`):

- The row was `GENERAL` — `TAB () {} [] <> "" '' ; / = ← → ↑ ↓`, **14 caps** —
  with the file language's caps **appended after them**, in slots 15+.
- A cap is ≥ 44 dp (56 dp when wide) plus padding, so about **six or seven
  caps fit a phone's width**, and the row opens at offset 0. The language caps
  were therefore **off-screen in every language** — the owner's report was
  literally correct.
- The old per-language tails were tiny and uneven: C `->`; C++ `->` `::`;
  Python `:` `_(self)`; HTML `</>`; CSS `:` `;`; JS/TS `` `` `` `=>`; shell `$`;
  JSON `:` `,` `null`; **Go, Rust, PHP, Ruby, Lua, XML, YAML, Markdown, Text: no
  caps at all**.
- The language is the **file's extension** (`LanguageType.fromFileName`,
  `EditorScreen.kt:666`); there is no language picker anywhere in the app.
- `EditorKeySet.languageMacroRow` — the per-language hook for the app's own
  CodeC Keys — exists but is **never called** (28.2 round 3 made the CodeC Keys
  letters/symbol layers language-independent by decision). It now shares the one
  new table so the two can never drift.

### The ghost: it is plain text with no container, and one rule can delete it

Read, not guessed (`GhostHintRenderer.kt`, `EditorScreen.kt`,
`SoraEditorHost.kt`, `GhostCompletion.kt`, `EditorViewModel.kt`,
`CompletionPolicy.kt`, `lsp/LspManager.kt`):

- The ghost was painted as **plain code text**, same font and full size, right
  after the caret, with **no box** — 27.1's deliberate choice ("plain text reads
  as *not real text*").
- Its colour is the theme comment colour at **38 % alpha**. Measured with the
  app's own `Contrast` maths on the four shipped editor themes, that composite
  is **1.82:1 / 1.88:1 / 1.98:1 / 1.85:1** against the editor background, where
  **real code text is 11.25:1 / 13.94:1 / 13.36:1 / 11.50:1**. The ghost was
  already very faint — so "too real" was never a brightness problem; the missing
  cue was that nothing said *"this is a suggestion"*. (Dimming it further would
  have pushed a 1.8:1 hint towards unreadable, the anti-pattern G5 warned about.)
- It can be taken away by two code paths:
  1. **Any** `ScrollEvent` hid it until the next content change (G4) — and the
     editor itself scrolls to keep the caret on screen: sora's own reveal while
     typing, plus the Phase-48/69 air rule's rescroll when the keyboard or the
     chrome settles. Verified against sora 0.24.6's `event/ScrollEvent.java`
     causes: `CAUSE_USER_DRAG = 1`, `CAUSE_USER_FLING = 2`,
     `CAUSE_MAKE_POSITION_VISIBLE = 3`, `CAUSE_TEXT_SELECTING = 4`,
     `CAUSE_SCALE_TEXT = 5`.
  2. The model is **recomputed from scratch** on every event, and the debounced
     engine pass lands ~120 ms–1 s after the keystroke; if that fresh answer no
     longer carried the aligned item, a visible, still-correct ghost blinked out
     with nothing the user had done.
- Nothing in Phase 69.1 touched the ghost's look or its rules.

## Owner decisions (option labels / answer text verbatim)

| # | Question | Owner's answer |
|---|---|---|
| Q1 | Where should the language's caps sit? | *"Can it be auto detection my file extension and set the the quick keys order as per requirement and if it is not a standard file than a default quick key option"* |
| Q2 | Keep today's caps, or Spck-like real caps? | **B — Give each language a few real caps (recommended, Spck parity)** |
| Q3 | What should change about the ghost? | **A — Both: unmistakable look + stop it vanishing (recommended)** |

Standing decisions carried in: the 68.1 Spck-parity rule (*"I don't want to use
any extra spaces just make it like spck"*); 69.1's Q3 = A (*"Remember the row's
horizontal position (and nothing else)"*); no new dependency, permission,
persistent preference or telemetry; pure host-tested policies first; the system
keyboard stays the default and CodeC Keys stays opt-in.

## What changed — two changes, both chosen by the owner

### 1. The quick keys answer to the file (Q1 + Q2)

- **New pure table `LanguageQuickKeys`** (`ui/editor/LanguageQuickKeys.kt`): the
  file language's caps. Detection is the extension through the one existing
  mapper — no second language concept, no picker.
- **The row order is now: the language's caps → the user's own custom snippets →
  the general set.** `EditorKeySet.keysFor` assembles it; `languageTail` is
  deleted; `languageMacroRow` delegates to the same table.
- **Content (Spck parity).** A handful of real caps per language, every one a
  plain `Insert`/`Pair` cap (a keycap inserts literally, so no `${1:}` snippet
  syntax can ride a cap):
  C `#include` `printf` `int` `->` · C++ `#include` `cout` `std::` `->` `::` ·
  Python `def` `print` `:` `_(self)` · JS/TS `log` `=>` `` `` `` `function` ·
  HTML `<tag>` `div` `class` `</>` · CSS `color:` `px` `:` `;` · JSON `:` `,`
  `null` · Shell `echo` `|` `#!` `$` · Go `:=` `func` `fmt` `err` ·
  Rust `println!` `fn` `let` `->` · PHP `<?php` `$` `echo` `->` ·
  Ruby `def` `end` `puts` `#{}` · Lua `local` `function` `then` `end` ·
  XML `<?xml` `<tag>` `/>` `</>` · YAML `:` `-` `#` · Markdown `#` `-` `**` `` ` ``.
  **Nothing that shipped before this part was removed** — the old tails are all
  in the new lists, popups and flicks included (JSON's `: ` popup and the
  true/false flicks are regression-pinned).
- **A non-standard file keeps the default row** (owner: *"a default quick key
  option"*): TEXT / no extension / unknown extension → no language caps, exactly
  today's `TAB () {} [] …` row.
- **A language change returns the row to its head** (`EditorScreen`:
  `LaunchedEffect(language) { keysRowScroll.scrollTo(0) }`), because the
  language's caps lead it now; within one file the 69.1 "remember the position"
  answer still stands untouched.

### 2. The ghost reads as a suggestion and stops vanishing (Q3 = A)

- **Look.** The hint is painted inside a **suggestion box** — the same rounded
  background family sora's own inlay hints use
  (`graphics/inlayHint/TextInlayHintRenderer.kt`, read at 0.24.6) — filled with
  the theme comment colour at **18 %** over the editor background, while the text
  keeps G5 (comment colour at **38 %**). The box starts exactly at the caret, so
  it never covers what was typed before it, and the measured width covers the
  box so the ghost stays tappable.
- **The vanish, part 1 — a still-correct ghost is held.** New pure
  `GhostCompletion.heldWhenStillValid(previous, text, caret)`: the painted item
  is re-aligned against the live text by the same rule `compute` uses, so the
  ghost survives a refresh that no longer proposes it, shrinks as the user keeps
  typing matching characters, keeps its first-line shape (G6), and dies the
  moment the text before the caret stops matching. Nothing is kept that `accept`
  could not still accept — `accept` re-checks the live buffer before writing.
  The VM consults it **only** when the fresh pass found nothing; selection,
  per-identifier dismissal, a real scroll and the master switch all clear as
  before.
- **The vanish, part 2 — G4 is narrowed to the user's own scrolls.** Only
  `CAUSE_USER_DRAG` / `CAUSE_USER_FLING` hide the ghost now; the editor's own
  caret-follow scroll (`CAUSE_MAKE_POSITION_VISIBLE`, which the air rule
  triggers when the keyboard or the chrome settles) can no longer take it away.
  A pinch (`CAUSE_SCALE_TEXT`) and selection-scrolling (`CAUSE_TEXT_SELECTING`)
  do not either — selection already suppresses upstream.

## Tests

- `LanguageQuickKeysTest` (**new**, 7 cases): extension detection through the one
  mapper; the row leads with the detected language's caps; the non-standard file
  falls back to the default row; **every** non-TEXT language has ≥ 3 caps and no
  duplicate labels (the law this part exists for); caps are plain inserts/pairs
  that insert what they say and never carry `${1:}` syntax; the pre-69.2 popup
  and flick layers survive; pinned examples per language family.
- `EditorKeySetTest`: the two old "tail, last" pins were **rewritten with their
  reason** into four order/fallback/ownership cases (language caps first, per
  language; default row for an unknown file; custom snippets right after the
  language caps; the general set unchanged behind them).
- `RunKeySetTest`: the one stale 22.x pin (`keysForContext … defs.last() == "->"`)
  moved with its reason — CI round 1 found it (see below).
- `EditorRowsWiringTest` +1: the row returns to its head when the language
  changes, and the row's language is still the file's own extension.
- `GhostCompletionTest` +4: the held ghost; the shrink re-measure; the mismatch
  and empty-remainder clears; G6's first-line-only hold.
- `GhostWiringTest` (**new**, 4 cases): the box is really drawn and the text
  keeps its colour; both colours really come from the active editor theme and
  ride a theme switch; exactly one hide-on-scroll call site and it is guarded by
  the two user causes only; exactly one hold call site, reached only when the
  fresh pass found nothing.
- `GhostContrastTest` (**new**, 3 cases): the two alphas the law was audited
  against (0.38 / 0.18), re-derived from `EditorThemes.kt` for all four themes —
  the ghost stays ≥ 1.4:1 (visible) and ≤ 2.6:1 and under 30 % of real text
  contrast (can never be mistaken for code again); the box is visible (≥ 1.15:1)
  and never reaches the 3:1 of a real UI component; and the ghost stays legible
  inside its own box (≥ 1.15:1 against it).

## Validation

| What | Where | Result |
|---|---|---|
| Source tests (host JVM) | CI `:app:testDebugUnitTest` on the tip `b43432d` (run 36354523369) | ✅ green |
| Android tests (instrumented) | — | none exist for this part; nothing claimed |
| Device evidence | — | **not claimed**: no round was run and none is asked for |

What only a handset can confirm (recorded, not requested): that the language
caps are the ones a thumb reaches for in a real file, and that the box reads as
a suggestion on the owner's screen rather than as a selection.

### CI

Four rounds, each one's lesson kept:

1. **Round 1 — red for cause** (`9c7a74d`, run
   [36353374750](https://github.com/pabi277/CodeC/actions/runs/36353374750)):
   *"2270 tests completed, 1 failed"* — `RunKeySetTest > keysForContext keeps
   the per-language tail for C` still asserted the language cap was the row's
   **last** entry, the exact thing this part changes. The fix is test-only, the
   pin moved with its reason (`f29fcfd`): it now takes the row's first four keys
   (`#include` `printf` `int` `->`) and separately asserts the arrow is still a
   cap. No production code changed.
2. **Round 2 — the part-1 head green**
   (`f29fcfd`, run [36353691179](https://github.com/pabi277/CodeC/actions/runs/36353691179),
   11m51s): the keys half — table, order, row reset, tests — has been green on
   this branch since here, and rounds 3–4 did not touch it.
3. **Round 3 — red for cause** (the tip `d5420f0`, run
   [36353710831](https://github.com/pabi277/CodeC/actions/runs/36353710831)):
   the only failure was `:app:compileDebugUnitTestKotlin` with two `Unresolved
   reference 'Visible'` errors, both in this part's own new test cases — they
   built the state as `GhostCompletion.Visible(...)` although `Visible` is
   nested in `GhostState`. `:app:compileDebugKotlin` passed in that same run,
   which is why the error list named a test file only: the production change
   (renderer box, host wiring, VM hold, scroll filter) compiled. Test-only fix,
   `GhostCompletion.Visible(` → `GhostState.Visible(`, committed as `b43432d`.
4. **Round 4 — the tip green** (`b43432d`, run
   [36354523369](https://github.com/pabi277/CodeC/actions/runs/36354523369),
   11m26s): the whole suite plus this part's 11 new cases (4 ghost-hold, 4
   ghost-wiring, 3 ghost-contrast), and both APKs built — debug
   26,024,564 B, release 6,796,488 B.

Both reds were found by CI, not by the compiler here: the sandbox has no JDK
(`which java javac kotlinc` → empty, `JAVA_HOME` unset, no `/usr/lib/jvm`), so
CI remains the executor of record — the same position 68.1 and 69.1 were in.
Every literal of every new pin was re-read against the live sources before each
push, and the two new contrast/wiring suites were additionally reproduced by
hand (the four themes' ratios, the 15 wiring literals) rather than trusted.

## Boundaries

No new dependency, permission, persistent preference, telemetry, screen, engine,
row, button or setting; the one row stays one row (68.1); nothing was removed
from the row; the CodeC Keys layers stay language-independent by their own
recorded 28.2 decision; `keyStripJson` still controls only the general base.
**No PR and nothing merged.**
