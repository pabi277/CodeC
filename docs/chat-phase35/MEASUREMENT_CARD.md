# Phase 35 — typing measurement card

**State:** instrumentation and host coverage are implemented; the owner’s
slowest-device card is still pending. This is intentionally not a device-pass
claim.

## Baseline and target

- Existing Phase 28.1 bench reference: **14.5 ms keystroke p95** on the prior
  bench device and a 5,000-line file.
- Phase 35 target: at or below that p95 on the owner’s slowest phone with a
  10,000-line file, while undo, pairing, completion, and the IME remain
  correct.

## What changed before the new device number

The known per-key hot tail no longer performs the heavy decoration pass on the
main dispatcher. `EditorViewModel.scheduleDecorationRefresh()` coalesces the
burst, waits for the existing 80 ms highlight debounce, computes the
current-line/bracket snapshot on `Dispatchers.Default`, and discards stale
results. Find matching is keyed by query/visibility/options and is not
recompiled for every character. `SoraEditorHost` also avoids a redundant
selection replay when sora already owns that selection.

These are code-path changes, not a replacement for the device measurement.

## Owner device card to fill

Run the same burst on the owner’s slowest phone at each file size and record
milliseconds (p50 / p95 / p99):

| File | Key apply | Undo/dirty | Autosave schedule | Decoration snapshot | Sora replay | Listener echo | Total |
|---|---:|---:|---:|---:|---:|---:|---:|
| small | — | — | — | — | — | — | — |
| 2,000 lines | — | — | — | — | — | — | — |
| 10,000 lines | — | — | — | — | — | — | — |

Regression card: type a burst with the system IME and CodeC Keys; accept a
completion; insert `(` and `{` + Enter; undo/redo; then switch tabs. Record
any dropped key, caret jump, pair mismatch, stale highlight, or composing-span
failure before declaring Phase 35 device-passed.
