# CodeC Phase 27.1 — Inline Ghost-text Completion

**Status:** ✅ **CI GREEN (2026-09-05, run `33944516016` on `6da7f44`; 4 rounds — see phase README) — device gate = §3
recipe** · **Cost:** `[client-only]` · **Effort:** M
· **Depends on:** `CodeCompletionEngine` (exists, debounced off-thread)
· **Target files:** `ui/screens/EditorScreen.kt` (render), new
   `ui/editor/GhostCompletion.kt` (pure logic), host tests

Mock: [`docs/images/editor-research/ghost-text-mockup.png`](../images/editor-research/ghost-text-mockup.png)

---

## 1. Design

Single best suggestion rendered **inline at the caret** in dimmed grey —
VS Code/Copilot's ghost text, adapted to phone realities
([VS Code docs](https://code.visualstudio.com/docs/editing/ai-powered-suggestions);
react-ghost-text's accept/reject contract is the minimal behavior spec:
tab-accept, esc-reject, debounce, no auto-commit).

Rules:

| # | Rule | Why (research anchor) |
|---|---|---|
| G1 | Ghost text appears only when exactly ≥ 1 suggestion AND the top suggestion's insert starts with the word prefix at the caret; only the **suffix** is painted. | Copilot "dimmed completion of the current line" model |
| G2 | **Typing never changes**: keystroke → ghost recomputes next frame; the suggestion is never inserted until accepted. | AI-UX anti-pattern: "no auto-commit without explicit accept gesture" |
| G3 | **Accept** = (a) TAB cap in strip, (b) a small "Tab ▸" pill floating at line-end (48 dp, visible while ghost shows), (c) hardware Tab on HW keyboards, (d) → (right-arrow cap) accepts **the next WORD only** (partial accept; hold to keep going). | VS Code partial-accept word (`Ctrl+→`) norm, thumb port |
| G4 | **Reject** = keep typing anything that doesn't match, swipe-down on the pill, or ESC cap; auto-clears on caret move/scroll. | react-ghost-text reject path; AI-UX "suppress while navigating" |
| G5 | Contrast law: ghost at exactly 38 % alpha of comment color — readable but unmistakably not real text; dark/light themes audited. | AI-UX anti-pattern: ghost contrast unreadable/ungnorable |
| G6 | Multi-line suggestions degrade gracefully: ghost shows the FIRST line only; the rest rides the 27.3 "⌄ more" panel. | Phone viewport discipline |
| G7 | No ghost while: text is selected, IME composing, find dialog open, interactive run holds the strip (RunKeySet context wins), file > soft size cap (keep completion snappy — the cap equals Phase 22.1's current windowing guard). | conflict law §27.3 |

Pure core: `GhostCompletion.compute(prefix, items) → GhostState` and
`acceptSuffix(state, granularity = Full | Word | Line)` — table-driven host
tests (word boundary = identifier chars; C/Python/HTML variants).

Rendering on the current Compose core: an overlay `Text` positioned via the
existing caret rect (no layout mutation of the field — ghost must cost zero in
the document model). On the Sora core: custom span in the completion layout;
same rules.

## 2. Implementation steps

1. `GhostCompletion` pure logic + host tests (accept word/next-line pieces,
   prefix shrink, negative cases).
2. Caret-anchored overlay render; alpha-contrast table entries into
   `EditorThemes`.
3. Strip TAB cap: when ghost visible, strip's TAB key switches label to
   "TAB ▸" (accept) vs today's indent — one state bit, default accept;
   long-press TAB still = raw indent (accessibility escape hatch, documented).
4. Pill "Tab ▸" at line end (48 dp min); swipe-down dismiss.
5. Perf: ghost computation joins the existing 120 ms debounced
   `produceState` path — no new main-thread work per keystroke.
6. Settings toggle (off = feature gone entirely; also the master toggle of the
   phase).

## 3. Exit condition

```text
(Device, release APK)
1. Type `print` in main.c → ghost `f("...` shows instantly-dimmed; typing `f`
   shrinks the ghost; typing `x` clears it. No character ever entered without
   an accept gesture.
2. TAB cap accepts fully. → cap accepts one word at a time (3 presses complete
   `printf("\n")`-style suggestion).
3. On 5k-line file: keystroke budget from 25.1 still passes with ghost ON.
4. Select-all, find dialog, interactive run: no ghost appears in any.
5. Toggle OFF in Settings → zero ghost, zero strip changes, survives restart.
PASS = all five.
```

---

## 4. Implementation record (2026-09-05, on the sora core)

The spec's "current Compose core" rendering note is moot — 25.2 landed first —
so the ghost is rendered through **sora's inlay-hint lane** (the cleanest
sora-native analogue: point-anchored, zero document mutation, sora shifts the
anchor on edits for free):

- `ui/editor/GhostCompletion.kt` — pure logic, host-tested
  (`GhostCompletionTest`): `GhostState.Visible(suffix, item, prefixLength)`;
  `compute(text, caret, items)` (G1: first item whose *insertText* starts
  with the caret prefix; G6: suffix capped to the first line; empty-suffix
  items skipped); `nextWordPiece` (identifier run | symbol run | whitespace
  run; a leading newline is its own piece, pieces never span newlines —
  the WORD granularity is **documented ours** (VS Code Ctrl+→-style classes);
  `accept(value, ghost, FULL|WORD|LINE)` with the stale-ghost guard (the
  live prefix must still match the item, else null — a ghost computed before
  a fast typist's next char is rejected, never half-inserted) and the
  never-over-a-selection rule; `filterForPrefix` — the instant shrink
  (§step-5 "join the debounced path" is HALVED: full recompute stays on the
  120/240 ms debounce, but the *shrink* happens synchronously per keystroke
  from cached items, so the ghost visibly tracks typing — the exit recipe's
  "typing `f` shrinks the ghost" without an engine run).
- `ui/editor/sora/GhostHintRenderer.kt` — `GhostInlayHint` (type
  `codec.ghost`) + renderer painting plain text at FULL text size in the
  comment color @ exactly 38 % alpha (G5). The stock
  `TextInlayHintRenderer` (rounded chip, 75 % size) was deliberately NOT
  used — it reads as a badge, not as ghost text.
- `SoraEditorHost` — renderer registered once; a keyed `LaunchedEffect`
  `(ghost, caretLine, caretColumn, …)` applies/clears the container
  (`editor.setInlayHints`), gated on `hasComposingText()` (G7) and on panel
  browse (one owning surface); full-text replays clear hints first; a
  composing selection event preemptively clears.
  - G3 affordances: tap-the-ghost accepts FULL via sora's
    `InlayHintClickEvent` (+ `intercept()` so the caret doesn't jump); the
    **"Tab ▸" pill** floats at the caret row's right edge (48 dp, tap =
    FULL, swipe-down = reject — G3(b)/G4); strip caps TAB ▸ / →▸ (27.2); HW
    Tab = FULL, HW Ctrl+→ = WORD (plain HW arrows are never hijacked).
  - G4: `ScrollEvent` → `viewModel.onCompletionScroll()` hides the ghost
    until the next content change; caret moves recompute (and usually
    clear) it via the VM's instant leg.
- Requirements mapping: G1 ✓ compute · G2 ✓ nothing inserts without accept
  (undo records the accept as a normal edit) · G3 ✓ (strip/pill/tap/HW) ·
  G4 ✓ (typing mismatch, caret move, scroll, ESC, pill swipe) · G5 ✓ 38 %
  of comment color, three themes · G6 ✓ first-line suffix · G7 ✓ (selection
  suppression in the VM model; composing in the host; find dialog first
  supersedes by moving the caret — plus the strip surfaces stay key-mode;
  the 1 MiB soft cap replaces the retired-BasicTextField windowing guard as
  the file-size bound).
- **Research notes (sora 0.24.6 sources, read for interfaces only —
  clean-room law kept):** `PointAnchoredContainer` shifts inlay anchors on
  insert/delete (`CodeEditor` calls `updateOnInsertion/Deletion`
  automatically); `setInlayHints(null)` also happens on `setText`;
  `InlayHintClickEvent` is dispatched from `EditorTouchEventHandler` and is
  interceptable; renderers are picked per hint type
  (`getInlayHintRendererForType`), missing renderer = zero width — ours is
  registered once in the host's `remember(editor)` block.

### Device recipe (§3 adapted to the sora core)

```text
(Device, CI APK from this branch)
1. main.c: type `print` → dimmed `f("\n");` paints inline after the caret;
   type `f` → ghost shrinks to `("\n");`; type `x` → ghost gone. No
   character ever appears without an accept gesture.
2. Pill "Tab ▸" at the caret row: tap → full insert, caret after `;`.
   (Ghost visible again on a new word.) →▸ cap accepts ONE piece at a time;
   the TAB ▸ cap accepts fully; long-press TAB still indents.
3. Tap directly ON the ghost text → it accepts.
4. 5k-line bench.c (from bench/): burst-typing stays at 25.1 budgets with
   ghost ON (items shrink instantly; full recompute rides the debounce).
5. Select-all → no ghost. Find open → caret jumps clear it. Mid-`scanf`
   interactive input → strip is run keys, no ghost. Scroll → ghost hides
   until the next keystroke.
6. Settings → Autocompletion OFF → no ghost, no chips, no ⌄ more, no panel;
   survives restart. Ghost toggle alone hides only the ghost.
PASS = all six.
```
