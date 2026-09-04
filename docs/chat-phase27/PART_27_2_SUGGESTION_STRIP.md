# CodeC Phase 27.2 — Suggestion Strip

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
· **Depends on:** Phase 22.2 strip exists; 27.1 logic for shared ranking
· **Target files:** `ui/components/EditorKeysRow.kt` (strip context), new
   `ui/components/SuggestionStrip.kt`, host tests for chip pipeline

Mock: [`docs/images/editor-research/editor-anatomy-mockup.png`](../images/editor-research/editor-anatomy-mockup.png)

---

## 1. Design

When completions exist, the bar directly above the IME **stops being key-caps
and becomes completion chips** — the Spck-style bar / iPad shortcut-bar
completion pattern, and the direct fix for "suggests and can't do anything":
targets are 44+ dp, always thumb-reachable, and they **can't occlude the code**
because they live where keys already live.

| # | Rule | Why (research anchor) |
|---|---|---|
| S1 | Strip shows while `completionItems` non-empty and ghost mode alone doesn't suffice (multi-candidate). Chips = top-N ranked, horizontally scrollable. First chip = ghost's item (coherent with 27.1). | mobile chip-tap is the accept norm |
| S2 | Chip tap = full accept at caret (same code path as today's `insertCompletion`). Chip LONG-PRESS = show kind/doc tooltip popup. | touch vocab, no hidden meanings |
| S3 | **Return-to-keys**: strip's LEFT-END pinned cap "⌨" (and any unmatched keystroke) restores the normal key set instantly. Suggestions never imprison the keys. | "no way to disable" anti-pattern |
| S4 | **Dismiss**: swipe DOWN on the strip dismisses for the current identifier; next identifier re-arms. (Better than per-tap dismissal churn.) | mobile gesture vocabulary |
| S5 | **"⌄ more"** pinned right-end cap opens the classic floating panel as *browse mode*: `LazyColumn` with **lazy chunked render** (first page only, chunks < 16 ms — JupyterLab completer lesson [PR #13663](https://github.com/jupyterlab/jupyterlab/pull/13663)); panel auto-anchor still avoids the IME. | keep today's investment as the deep list |
| S6 | While an interactive run waits for input the strip is RunKeySet's — completions never fight run input (23.2 law). | context law |
| S7 | Chip sizing: min 44 dp height, text ≤ 18 chars + ellipsis, kind glyph (ƒ/≠/λ) on the left; selected chip = filled accent. | tap-target discipline |
| S8 | Strip height constant: suggestion mode reuses the SAME row height (no layout jump → no IME flicker). | IME stability lessons, Phase 22.2 |

Pipeline (pure, host-tested): `buildStripModel(items, prefix, maxChips) →
List<Chip>` with ranking = `CodeCompletionEngine` order + prefix match boost
+ recency of use (in-memory only); selected-index state machine shared with
the panel for future HW-arrow browsing.

Performance: strip model computed inside the existing debounced
`produceState`; chip list diffed by identity — recomposition touches ≤ 1 row;
25.1's "≤ 2 frames after keystroke" budget applies.

## 2. Implementation steps

1. `SuggestionStrip` composable + `buildStripModel` tests.
2. Strip context state (Keys | Suggestions | Run) — one sealed
   `StripContext`, replacing today's ad-hoc flag juggling; RunKeySet swap
   re-expressed as `StripContext.Run` (23.2 semantics preserved by tests).
3. Accept/dismiss wiring incl. 22.x TAB/ENTER HW parity (arrows browse chips
   when strip active; arrows fall through to caret when not).
4. "⌄ more" → refurbished floating panel with lazy chunking.
5. Contrast/size audit across `EditorThemes`; accessibility labels per chip
   (TalkBack: "accept printf").
6. Device recipes below; THEN the Phase 22.6 idle-beat cadence is revisited
   (strip instant vs ghost beat deserves one tuning pass).

## 3. Exit condition

```text
(Device)
1. Type `pri` in main.c → chips fill the strip (printf, println-style…); tap
   first chip → `printf` inserted, caret after; chips gone, keys back.
2. Horizontal scroll reveals more chips; "⌄ more" opens the panel, browse +
   tap works, panel never hides under the IME.
3. Swipe down on strip → chips dismissed for `pri`; typing `x` after shows no
   chips for `prix` prefix unless matches exist.
4. Mid `scanf` run: strip = RunKeySet only — zero completion chrome.
5. One unmatched keystroke (e.g. `z` when nothing matches) → keys return
   immediately and `z` is typed (no swallowed chars — pure-logic test pins it).
PASS = all five + 22.2/23.2 recipes green.
```
