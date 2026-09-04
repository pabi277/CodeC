# CodeC Phase 25.1 — Candidate Spike & Device Benchmark

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
· **Depends on:** nothing (this is Phase 25's gate; it precedes 25.2/25.3)
· **Target files:** *throwaway spike trees only* (`spike/` ignored by repo, or a
separate bench module NOT shipped in the APK)

---

## 1. Design

Benches over bets. Three minimal editors, one phone, one release build:

| Candidate | What gets built in the spike | Question answered |
|---|---|---|
| **C-now** — current `BasicTextField` + `SyntaxVisualTransformation` | Today's `EditorScreen` opened on the bench corpus, with Phase 22.1 span windowing as-is | Is the ceiling already reached, or is cheap headroom left? |
| **C-sora** — Sora Editor `CodeEditor` | `io.github.Rosemoe.sora-editor:editor:0.23.6` ([Maven, LGPL-2.1](https://libraries.io/maven/io.github.Rosemoe.sora-editor:editor)) + TextMate C grammar OR a trivial lexer; `EditorAutoCompletion` fed by a stub provider | Does the industry phone core beat us decisively? |
| **C-compose2** — `TextFieldState`-based rewrite sketch | Render only the visible line window (bigtext-style), incremental per-line spans | Could a from-scratch Compose core plausibly match C-sora? (high-risk fallback) |

Corpus (checked into the spike, never the app): the existing 517-line HTML
owner sample **and** a generated 5 000-line `bench.c` (~200 kB, mixed
comments/strings/identifiers so tokenizers do real work).

Benchmark harness: `FrameMetricsAggregator` + a scripted input injector
(replay a 60-keystroke burst, a 500-line fling, a caret drag, completion churn)
so every candidate gets *identical* input. All runs: **release build, R8,
owner's device, battery > 30 %, cool-down between runs.** Debug numbers are
inadmissible (Phase 22 established that debug Compose is misleadingly slow).

Scoring sheet (fill during device round):

| Metric (p95) | C-now | C-sora | C-compose2 | Budget |
|---|---|---|---|---|
| Keystroke commit latency | | | | ≤ 1 missed frame |
| Fling holds 60 fps? | | | | yes |
| Caret-drag hitch | | | | none visible |
| Completion refresh after keystroke | | | | ≤ 2 frames |
| Cold open 5k file (ms) | | | | ≤ 800 |
| APK size delta (MB) | — | | | ≤ +2 |

## 2. Implementation steps

1. Create the throwaway spike harness (bench module flagged
   `testFixtures`-only or a separate Android Studio run config; APK never
   grows in `main`).
2. Candidate C-sora: wire `CodeEditor` into a bare activity; attach C
   highlighting via `language-textmate` with a public-domain C grammar; stub a
   completion provider. *(LGPL discipline: dependency only, no source copy.)*
3. Candidate C-compose2: visible-window line renderer + incremental spans.
   Timebox: **two working days**; it only needs to beat C-now convincingly to
   stay alive as a candidate.
4. Script the input injector + frame capture identical across candidates.
5. Run on device ×3 each, record medians in the decision table of
   `docs/EDITOR_MOBILE_RESEARCH.md` §3.
6. Decision gate: C-sora wins → 25.2; C-compose2 wins → 25.3; C-now wins →
   stop, polish stays-in-Compose follow-ups recorded, 25.2/25.3 cancelled and
   marked so.

## 3. Exit condition

```text
1. Decision table above fully filled from device runs (no emulator numbers).
2. JOURNEY entry records the winner + raw numbers.
3. Follow-on part (25.2 or 25.3) explicitly started or cancelled in writing.
PASS = all three. The gate is evidence; "feels faster" is not a measurement.
```
