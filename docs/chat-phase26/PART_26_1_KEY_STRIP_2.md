# CodeC Phase 26.1 — Key Strip 2.0

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
· **Depends on:** Phase 22.2 strip (exists); editor-core choice affects only
   the insert plumbing
· **Target files:** `ui/editor/EditorKeySet.kt` (data model extend),
   `ui/components/EditorKeysRow.kt`, Settings (strip config), host tests

---

## 1. Design

The strip gains three interaction dimensions without gaining a row:

1. **Long-press popup keys (Termux pattern).** New field
   `EditorKeyDef.popup: EditorKey?` — e.g. `{label:"↑", key:Caret(UP),
   popup:Insert("\n\n" + caret-up-2 + indent)}`… more practically:
   `←→` popups = HOME/END; `↑↓` popups = PGUP/PGDN; `;` popup = `:`;
   `""` popup = backtick; `= ` popup = `==`. Long-press (≥ 300 ms) raises a
   small popup cap directly over the key; slide-release selects. Caps under
   48 dp so thumbs don't travel.
2. **Swipe layers (Unexpected/FlorisBoard pattern, gentle version).** Flick-up
   on pair caps `()` `{}` `[]` inserts **only the opener**; flick-down inserts
   **only the closer** (tab stays the pair, caret inside — Phase 22.5
   semantics untouched). This removes the last reason to open the IME symbol
   pane mid-identifier.
3. **Hold-repeat** on arrow caps (150 ms initial delay, 40 ms repeat) — same
   as 25.4's arrow item; landed here or there, never twice.
4. **User-editable key sets** (the Phase 22 stretch, activated): per-language
   ordered list persisted as JSON; Settings → Editor → Key strip editor
   (add/remove/reorder/popup+swipe assignment; reset-to-default). Model stays
   pure/host-tested; UI is a simple reorderable list.

Data model delta (all pure, host-tested):

```
EditorKeyDef(label, key, wide = false,
             popup: EditorKey? = null,
             swipeUp: EditorKeyKey? = null, swipeDown: EditorKey? = null)
```

Defaults per language stay exactly today's sets (C/C++/Python/HTML/CSS/MD +
RunKeySet override during interactive runs — 23.2 must win conflicts: while a
run waits for input the strip is `RunKeySet`'s, popups included).

## 2. Implementation steps

1. Extend `EditorKeyDef` + defaults table; migrate `EditorKeysRow` rendering
   to read popup/swipe spec (visual affordance: tiny corner tick on caps that
   carry extras).
2. Gesture recognizer on caps: tap / long-press→popup / flick-up / flick-down;
   unit-test the recognizer as a pure state machine; UI tests optional.
3. Hold-repeat timer for arrows (VM-free, composable-side; testable via fake
   clock).
4. Settings editor + persistence (DataStore JSON); validate on load, fall back
   to defaults on corruption.
5. Regression sweep: phase 22.2 + 23.2 device recipes re-run verbatim.

## 3. Exit condition

```text
(Device, soft keyboard open)
1. Long-press on → shows END popup; slide-release sends caret to line end.
2. Flick-up on {} inserts "{" only; tap inserts "{}"+caret-inside (today's
   behavior intact).
3. Holding ← repeats; release stops instantly; no key EVER fires both tap and
   popup/flick actions.
4. Reorder + add a custom key in Settings; survives restart; invalid JSON in
   storage → defaults silently restored (log line only).
5. During an interactive scanf run, strip is RunKeySet (↵/Ctrl+C/Tab/↑/↓) with
   its own popups; editor strip returns exactly on run end.
PASS = all five + Phase 22.2/23.2 recipes still green.
```
