# CodeC Phase 25.3 — Compose Rewrite Fallback (bigtext-style, contingency only)

**Status:** ❌ **CANCELLED 2026-09-04 — permanently dead.** The 25.1 device
gate chose C-sora: every budget passed on both corpora (decision table:
[`docs/EDITOR_MOBILE_RESEARCH.md`](../EDITOR_MOBILE_RESEARCH.md) §3.1; raw
numbers: [`PART_25_1_SPIKE_BENCH.md`](PART_25_1_SPIKE_BENCH.md) §4.5), and the
C-compose2 spike itself demonstrated the failure mode the research dossier
predicted (whole-window recomposition storm: frames locked at ~36 ms at 100 %
jank; drag auto-scroll dead). Do not execute this plan unless the owner
re-opens the decision with NEW evidence (e.g. a licensing veto on sora-editor).
The original spec text below is preserved for the record.

---

**Status (original):** 📋 PLANNED — **executes ONLY if 25.1's gate kills Sora** (e.g. LGPL
declined by owner, or budgets missed). If 25.2 proceeds, this part is
permanently CANCELLED and says so at the top.
**Cost:** `[client-only]` · **Effort:** L (and highest risk of the phase)
· **Depends on:** PART 25.1 decision = C-compose2
· **Target files:** new `ui/editor/core/*` (document model + renderer), then
`EditorScreen.kt`/`EditorViewModel.kt`

---

## 1. Design

The Phase 22-deferred rewrite, now with the spike's evidence. Core ideas —
learned *conceptually* from how Sora/Quoda-era editors and Compose's own
`LazyColumn` think (clean-room; no code import):

1. **Document model:** line-partitioned content (`rope`-lite: array of line
   strings + `CachedIndexer`-style cumulative offsets) so edits are O(line);
   expose `TextFieldState`-equivalent snapshot state to Compose.
2. **Visible-window rendering:** only visible lines (+overscan) are laid out —
   one `BasicTextField` *per visible range* variant was benchmarked in the
   spike; the winner (single field vs per-line fields) is chosen from 25.1's
   harness, not guesswork.
3. **Incremental spans:** per-line span lists shifted by the edit delta instead
   of rebuilding an `AnnotatedString` (the reason current code dies is the
   per-keystroke full-document transformation + span count, per CMP-4023).
4. **Caret math without `getLineForOffset`:** line offset table (binary search)
   maintained with the document — avoids the known linear-scan trap
   ([#4021](https://github.com/JetBrains/compose-multiplatform/issues/4021)).

Explicitly NOT in scope here (delegated to 25.4): magnifier, selection handles,
word-grab. Explicitly accepted as permanent debt vs 25.2: no LSP-on-rails, no
battle-tested IME edge-case handling, our own undo manager stays.

### Why this is the fallback, honestly
Every Compose-native editor surveyed ([compose-code-editor](https://github.com/Qawaz/compose-code-editor),
[text-editor-compose](https://github.com/kaleidot725/text-editor-compose)) ships the
same per-keystroke-parse pattern CodeC already outgrew; none demonstrates
5 000-line fluency. The decade of Android text-input edge cases (IME composing
regions, Samsung keyboards, accessibility) that Sora has absorbed would become
CodeC's own bug stream. This path exists because owner control > dependency —
only when the numbers force it.

## 2. Implementation steps

1. `DocumentBuffer` (lines + offset index + edit events) — pure Kotlin, host
   tests: random-edit differential vs `StringBuilder` oracle (10k ops).
2. `VisibleWindowEngine` — decides rendered line range from scroll state +
   viewport; unit tests on synthetic scroll traces.
3. Span cache: per-line spans, shift-on-edit; reuse Phase 22.1 tokenizer as the
   per-line backend first, upgrade to stateful line-by-line lexing after.
4. Editor surface composable rendering the window; IME connection on the
   window; `imePadding()` retained (Phase 22.3 invariant).
5. Bridge: `EditorViewModel` APIs repointed (`codeText`, autosave, tabs,
   EditorKeySet application, `EditorUndoManager` → document ops) — the VM's
   public shape must not change (shell/theme/status untested regressions
   become unlikely by construction).
6. Re-run the Phase 9/22/23 device recipes + the 25.1 budget table.

## 3. Exit condition

```text
1. 25.1 budget table passes on C-compose2 (device, release).
2. EditorCursorMathTest / HighlightCache / KeySet / LineOps / Undo suites pass
   against the new core unchanged (adapted only at the bridge).
3. Random-edit host fuzz passes.
4. Owner comment in chat accepting the LSP/edge-case debt.
PASS = all four; otherwise admit defeat and go back to 25.2's LGPL checklist.
```
