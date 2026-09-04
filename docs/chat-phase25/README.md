# CodeC Phase 25 — Mobile-first Editor Core

> **Status:** 🚧 **25.1 IMPLEMENTED — bench spike built, CI-gated, DEVICE
> ROUND REQUIRED (owner).** The three candidates and the harness live in the
> throwaway `:bench` module (separate APK, artifact `CodeC-Bench`); the
> decision table in [`docs/EDITOR_MOBILE_RESEARCH.md`](../EDITOR_MOBILE_RESEARCH.md)
> §3.1 is EMPTY until the owner's device numbers land. 25.2/25.3 remain
> PLANNED behind that gate. Implementation record:
> [`PART_25_1_SPIKE_BENCH.md`](PART_25_1_SPIKE_BENCH.md) §4.
> Research basis: [`docs/EDITOR_MOBILE_RESEARCH.md`](../EDITOR_MOBILE_RESEARCH.md)
> (GitHub-verified 2026-09-04). **Owner started this phase with "Start Phase 25";
> no PR/merge without the owner's explicit command** (`rule.md` §3).

Owner brief (2026-09-04): *"the main problem is the editor … find the best
optimized phone editor and also good typing experience … make it best."*

Phase 25 activates the item Phase 22 explicitly deferred: *"the
`TextFieldValue` → `TextFieldState` / `bigtext`-style rewrite — the only way
past the `BasicTextField` layout ceiling, and its own phase"*
(`docs/NEXT_STEPS.md`) — but widened per the research: before rewriting
anything, **benchmark the three candidate cores on the owner's device** and let
numbers, not taste, decide.

## The decision being made

```
                 ┌───────────────────────────────┐
   Phase 25.1 ──▶│  Bench spike: 3 candidate     │
   (this phase    │  cores, owner device,        │
    always runs)  │  release build               │
                 └──────────────┬────────────────┘
                                │
                budget table    ▼
        ┌─────────────────┴──────────────────┐
        ▼                                    ▼
  Phase 25.2 — Sora path              Phase 25.3 — Compose rewrite
  (adopt library, LGPL-2.1 gate)      (fallback: bigtext-style,
        │                              only if Sora misses budgets)
        └──────────────┬───────────────┘
                       ▼
        Phase 25.4 — caret & selection layer
        (magnifier, handles, word-grab) on the chosen core
```

| Part | Title | Cost | Effort |
|---|---|---|---|
| [25.1](PART_25_1_SPIKE_BENCH.md) | Candidate spike + device benchmark + decision gate | client-only | M |
| [25.2](PART_25_2_SORA_PATH.md) | Sora Editor integration (chosen path per research) | client-only | L |
| [25.3](PART_25_3_COMPOSE_FALLBACK.md) | Compose `TextFieldState`/bigtext-style rewrite (contingency) | client-only | L |
| [25.4](PART_25_4_CURSOR_SELECTION.md) | Caret, selection & magnifier layer | client-only | M |

## What "best" means here (exit budgets — all device-measured)

Measured on the owner's phone, **release APK**, worst file in the corpus
(517-line HTML sample + a 5 000-line generated C file):

| Budget | Target |
|---|---|
| Keystroke → committed char rendered | ≤ 1 missed frame at p95 (systrace/FrameMetrics) |
| Scroll fling | holds 60 fps |
| Caret drag across a 500-line region | no visible hitch / magnifier follows finger |
| Completion strip visible refresh | ≤ 2 frames after last keystroke (drives Phase 27) |
| Cold open of the 5k-line file | ≤ 800 ms to first interactive frame |

If the **current** Compose core meets these after the spike's cheap fixes, the
phase STOPS at 25.1 and the dossier records why — the point of the spike is to
not migrate for fashion.
