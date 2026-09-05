# CodeC Phase 27.2 — Suggestion Strip

**Status:** ✅ **CI GREEN (2026-09-05, run `33944516016` on `6da7f44`; 4 rounds — see phase README) — device gate = §3
recipe** · **Cost:** `[client-only]` · **Effort:** M
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

---

## 4. Implementation record (2026-09-05)

- `ui/editor/StripContext.kt` — the sealed `StripContext` (Hidden | Run |
  Keys | Suggestions) + pure `SuggestionStripModel`:
  - `stripContextFor(...)` — single resolution law: chevron toggle off ⇒
    Hidden; interactive run waiting for stdin ⇒ **Run** (S6 — 23.2 semantics
    re-pinned in tests); master/strip switch off, selection active, past the
    1 MiB soft cap, or dismissed-anchor equality ⇒ Keys; ≥ 2 candidates ⇒
    Suggestions (S1's "ghost mode alone suffices" single-candidate case stays
    in key mode with the dual-mood TAB ▸); otherwise Keys with the
    `GHOST_ONLY` surface bit while the ghost shows.
  - `buildStripModel(items, ghost, acceptCounts)` — engine order is the
    stable base; the ghost's item pinned FIRST (S1); in-memory
    recency-of-use boost (Session HashMap in the VM). Chips: ≤ 8
    (MAX_CHIPS), `displayLabel` ellipsized ≤ 18 chars, kind glyph ƒ/λ/≠
    (S7), `ghostBacked` flag.
- `ui/components/SuggestionStrip.kt` — the renderer: pinned left **⌨** cap
  (S3 return-to-keys), the chips row (horizontal scroll, same 40 dp caps &
  padding as the keys row — S8 height constancy ⇒ no IME flicker), pinned
  right **⌄ more** cap (S5, hidden when the panel setting is off). Chip
  gestures reuse the proven 26.1 cap pattern rewritten for chips: tap =
  accept (same VM path), long-press = kind+detail tooltip while held (S2),
  horizontal drag = scroll (no accidental accepts). **Swipe DOWN anywhere on
  the strip** dismisses for the current identifier (S4). TalkBack labels:
  "accept printf" per chip, "Hide suggestions, show keys", "Open full
  completion panel" (step 5's accessibility pass).
- `ui/editor/sora/CodeCCompletionComponent.kt` — S5's browse mode ON SORA'S
  NATIVE PANEL (the 25.2 investment kept): a subclass installed via
  `editor.replaceComponent(EditorAutoCompletion::class.java, …)` whose
  `requireCompletion()` is **gated** — sora's typing/caret auto-triggers
  fall through as no-ops (the auto popup is gone); `browseNow()` opens an
  explicit session in which the panel updates per keystroke and keeps its
  own hardware Tab/Enter/arrows handling (matrix PANEL column); every hide
  path (`hide()` override — empty results, tap-away, ESC, fling) ends the
  session so the panel never resurrects by itself; an
  `onBrowseVisibility` callback mirrors REAL attach/detach (post-verified
  `isShowing`, not just "show requested") into Compose state.
- `EditorScreen` — the old `KeysStrip` is replaced by `BottomStrip`, a dumb
  renderer over `StripContext` (kept in BOTH positions: docked and
  IME-anchored). `EditorKeysRow` gained `onInterceptKey` (first-refusal
  hook for the dual-mood caps; `EditorKeySet.apply` is a deliberate no-op
  for `GhostAccept`/`GhostAcceptWord` so a missed wiring never inserts a
  silent tab under the "TAB ▸" label). `KeysContext`/`keysForContext`
  remain for the 23.2 host tests (`RunKeySetTest` pins them).
- Ranking note: "prefix match boost" from the spec pipeline is handled
  upstream — the instant `filterForPrefix` shrink already narrows by the
  live prefix, so strip-build ranking = ghost-pin + recency + engine order
  (recorded so the next reader doesn't add a second prefix pass).
- **Research notes:** `EditorAutoCompletion`'s auto-trigger lives in the
  PROTECTED `onContentChange`/`onSelectionChange`/`requireCompletion`
  handlers (overridable; `requireCompletion` and `show()`/`hide()`
  early-return when disabled); `replaceComponent` disables the old instance
  and carries its enabled state; `EditorPopupWindow.show()/dismiss()` are
  public; `cancelCompletionNs` throttles immediate re-requests (single-tap
  "⌄ more" unaffected).

### Device recipe (§3 unchanged in spirit, sora-core wording)

```text
(Device)
1. main.c: type `pri` → chips fill the strip (printf… first chip = ghost's
   item, filled); tap it → `printf("\n");` inserted, caret after; chips
   collapse back to keys (unless more identifiers match).
2. Scroll the strip horizontally → more chips; "⌄ more" → the sora panel
   opens ABOVE the caret; keep typing → panel updates; tap editor or ESC →
   panel closes and STAYS closed for this identifier until re-armed.
3. Swipe DOWN on the strip → chips dismissed for `pri`; type `x` (no
   matches) → keys return and `x` is typed (never swallowed — also pinned
   by CompletionPolicyTest).
4. During `scanf` input: strip = run keys only; zero completion chrome.
5. Settings: strip off → keys row never flips to chips (ghost + panel stay
   as configured).
PASS = all five + 22.2 strip position + 23.2 run-swap recipes green.
```
