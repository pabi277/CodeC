# CodeC Phase 35.2 — Smooth typing

**Status:** 🚧 IMPLEMENTED; device measurement pending · **Cost:** `[client-only]` · **Effort:** M

## Symptom (owner)

*"the typing is not smoth like normal keyboard"* — CodeC Keys feels less
smooth than the system keyboard.

## What exists today (evidence — the per-keystroke path)

One cap press runs, on the **main thread**: `EditorKeySet.apply` (new
`TextFieldValue`) → `SmartTyping.transform` → `undo.recordChange` +
`syncUndoFlags` → dirty compute → `scheduleAutoSave()` →
`scheduleDecorationRefresh()` (an 80 ms-coalesced snapshot; line/column scan
and `BracketMatcher` now run on `Dispatchers.Default`, while find matching is
keyed to query/visibility/options instead of rerunning for every key) → `_codeText`
emit → `SoraEditorHost` `ed.setText` **or** `ed.setSelection` →
`ContentListener` echo → `updateCode` again. The Phase 28.1 bench certified
14.5 ms p95 on a 5 000-line file, but that was a **bench device**, and the
per-keystroke decoration + undo + autosave work still scales with file size.

**Law (from 28.1/30 device rounds):** no fix on a guess — this part starts
with a measurement card and only then removes the specific cost it finds.

## Design (measure first, then cut)

1. **Measurement card (Phase 28.1 bench pattern).** Re-run a keystroke-p95
   harness (the `:bench` module's real-wrapper path, or a debug-logging
   instrumentation in `applyEditorKey`/`updateCode`/`SoraEditorHost.update`)
   on a **slow** device, at three file sizes (small / 2 000 lines / 10 000
   lines). Record p50/p95/p99 per phase: key-apply, undo-record, dirty,
   autosave-schedule, decoration-refresh, sora-replay, listener-echo.
2. **Likely cuts (chosen by the measurement, not all at once):**
   - Make `scheduleDecorationRefresh` not recompute **find highlights** every
     keystroke (only when the find query/visibility changed).
   - Move `BracketMatcher` and the line/column scan off the typed path's hot
     tail (keep the 20 ms debounce, but let it coalesce and run after the
     frame).
   - Autosave is already debounced; verify it is not being rescheduled per
     keystroke in a way that wakes I/O on the main thread.
   - Ensure the sora replay only **replaces text once** and never re-attaches
     the `ContentListener` on a plain text change (it already re-attaches only
     after `setText`; confirm no redundant work per key).
3. **Guard against regression** — the Phase 28.1/30 laws: undo must survive
   tab switch, `()` pairing must keep working on the CodeC Keys path, and
   completions/ghost must not jitter.

## Exit condition

```text
(Device, the owner's slowest phone)
1. Measurement card shows typed-char p95 cut (target: ≤ the bench's 14.5 ms
   p95) at a 10 000-line file, with the per-phase breakdown recorded.
2. Typing a long file feels "like a normal keyboard" — no visible lag on a
   fast burst (owner's word).
3. Undo/pairing/completion regression card passes.
PASS = all three.
```

## Tests

- Any pure logic extracted (e.g. a `DecorationDirtyPolicy`) gets a host test.
- The bench module gains (or re-runs) the keystroke-p95 harness so CI keeps
  the number honest — CI is the only executor of record for the bench.
