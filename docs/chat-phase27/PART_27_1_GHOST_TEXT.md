# CodeC Phase 27.1 — Inline Ghost-text Completion

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
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
