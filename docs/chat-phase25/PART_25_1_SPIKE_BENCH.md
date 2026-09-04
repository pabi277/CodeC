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

---

## 4. Implementation record (2026-09-04, `arena/01a06b20-codec` — owner: "Start Phase 25")

The spike is **BUILT**; the device round is the owner's pass. Status: 🚧
implemented + CI-gated, **device pass required** — the decision table in
`docs/EDITOR_MOBILE_RESEARCH.md` §3.1 stays EMPTY until real device numbers
land, and 25.2/25.3 remain PLANNED behind that gate.

### 4.1 What was built

A throwaway Gradle module **`:bench`** (directory `bench/`), a SEPARATE
Android application:

- **`applicationId com.codeci.bench`** — installs alongside the IDE; the app
  APK is untouched (`:app` has zero new dependencies or sources).
- **Release build with R8** (`isMinifyEnabled = true`), signed with the
  repo-pinned shared debug key so the owner can sideload the CI artifact and
  update it in place — no signing secrets needed. (The spec's "release build"
  is about R8/optimization, not the signing key; Phase 22 proved debug Compose
  numbers are inadmissible.)
- Kept out of the legacy CI invocation: `settings.gradle.kts` includes
  `:bench` only when `gradle.gradleVersion != "9.0.0"` (the 9.0.0 → shim path
  cannot configure AGP 9.1.1 modules at all). `build-apk.yml` gained a step
  that runs the REAL wrapper: `:bench:assembleRelease :bench:testDebugUnitTest`
  and uploads the **`CodeC-Bench`** artifact (remove this step when Phase 25
  closes).

**Candidates** (all three behind one `BenchMainActivity`, chooser + results
sheet; identical scripted input per scenario):

| Candidate | Implementation |
|---|---|
| **C-now** | Faithful mirror of the production widget stack — copied VERBATIM from `:app` (our own code): `MultiLanguageSyntaxHighlighter.kt`, `EditorThemes.kt`, `CodeFormatter.kt`, `BracketMatcher.kt`, `EditorUndoManager.kt`, `CodeCompletionEngine.kt`, plus the two decoration types (`BenchEditorDiagnostics.kt`, trimmed copy). `NowCandidate` reproduces the `EditorScreen`/`EditorViewModel` typing pipeline: `BasicTextField(TextFieldValue)` + `SyntaxVisualTransformation` with the ±3 000-char windowed snapshot (80 ms debounce, `Dispatchers.Default`), 20 ms decorations (current line + bracket pair), 120 ms debounced completion scan + minimal preview list, gutter `remember(lineCount)`. |
| **C-sora** | `io.github.rosemoe:editor:0.24.6` + `io.github.rosemoe:language-java:0.24.6` in an `AndroidView` — **binary dependency only, no source vendored** (LGPL-2.1; the same discipline PART_25_2 will gate on). `JavaLanguage` is the ready-made "trivial lexer" (incremental spans + identifier completion). |
| **C-compose2** | The Phase 22-deferred rewrite sketch: pure `DocumentBuffer` (line-partitioned + maintained offset index — binary-search `locate`, the CMP-#4021 avoidance), `VisibleWindow` (only visible lines + 8 overscan in a LazyColumn), `LineSpanCache` (per-line re-tokenization via the same production tokenizer, bounded LRU). Editing at spike scope: the caret line is a focused single-line `BasicTextField`; no cross-line IME composing (the accepted debt §25.3 names). |

**Harness** (`bench/src/main/java/com/codeci/bench/harness/`):

- **Frame capture** — the PLATFORM `Window.addOnFrameMetricsAvailableListener`
  + `FrameMetrics.TOTAL_DURATION` (API 24+, exactly our minSdk). *Spec
  deviation:* the spec named androidx `FrameMetricsAggregator`, which was
  removed from `metrics-performance` in favor of JankStats; the platform
  listener is the same mechanism with zero dependency risk.
- **Input scripts** — pure (`core/InputScripts.kt`, host-tested):
  `burst60` (60 keys @ 40 ms), `completionChurn` (16 keys @ 220 ms — past the
  120 ms completion debounce), `fling500` (accelerating drag + lift, settle
  window captured), `caretDrag` (600 ms long-press → slow drag → 1.5 s
  bottom-edge wiggle, the auto-scroll probe).
- **ScriptRunner** — main-thread lowering: synthesized KeyEvents via
  `KeyCharacterMap.VIRTUAL_KEYBOARD.getEvents` (ALL halves dispatched, so
  shifted punctuation works), MotionEvents resolved against live view bounds
  (resolution-independent). Per-candidate **TypingTarget** exposes the
  content-API insert + probes; each run records which **input mode** produced
  it (`keys` vs `direct`) — if a core ignores dispatched keys, the owner
  flips one toggle and re-runs rather than the numbers being silently wrong.
- **Results** — per-rep `FrameSummary` (p50/p90/p95/p99/max, jank = >16.667 ms,
  bad = >33.333 ms), median-of-3-reps, cold-open (read + compose + 2 frames,
  same protocol per candidate), chars-typed and lines-traversed probes, a
  Copy-as-markdown / Share export for the owner to paste into chat, persisted
  to `files/bench-results.md`.
- **Corpus** (checked into `bench/src/main/assets/bench/`, never the app):
  deterministic generator `bench/tools/generate_corpus.py` produced
  `bench.c` (4 993 lines / 175 kB — spec said ~5 000 / ~200 kB) and
  `bench.html` (exactly 517 lines / 31 kB — the Phase 22 span-density worst
  case; the owner's original sample file is not in the repo, so this is a
  generated equivalent and the substitution is recorded here).
- **Host tests (CI):** `FrameStatsTest` ×8, `InputScriptsTest` ×5,
  `DocumentBufferTest` ×9 (incl. a 10 000-op seeded random-edit differential
  vs a `StringBuilder` oracle), `VisibleWindowAndSpansTest` ×6.

### 4.2 Research notes (2026-09-04)

1. **Sora coordinates moved.** The spec pinned
   `io.github.Rosemoe.sora-editor:editor:0.23.6` (old group). The project now
   publishes under `io.github.rosemoe`; newest stable verified on Maven
   Central on 2026-09-04 is **0.24.6** (both `editor` and `language-java`,
   repo1.maven.org listings). We pin 0.24.6 so the decision reflects the core
   a Phase 25.2 integration would actually adopt.
2. **`FrameMetricsAggregator` is gone** from androidx metrics (removed in
   1.0.0-beta01 in favor of JankStats); see the platform-listener deviation
   above.
3. **LGPL for the spike:** the bench APK redistributes sora-editor as a CI
   artifact to the owner. Dependency-only use + the repo link + license name
   in the results/home screen satisfy the practical bar for a throwaway
   harness; the FULL obligation checklist (About screen, license text,
   substitutability build) binds 25.2 and is not triggered by the spike.

### 4.3 Device round — the owner's runbook

1. **Actions** tab → latest green `Build APK` run → **Artifacts** →
   download **`CodeC-Bench`** → unzip → install the APK ("CodeC Bench").
2. Battery > 30 %, phone cool, nothing else in the foreground.
3. Home → toggle **Input mode** (start with `keys`; flip to `direct` only if
   a candidate's "typed" count comes back 0).
4. For each of **C-now / C-sora / C-compose2 × bench.c / bench.html**:
   open it, wait for "ready", then run the four scenario buttons one at a
   time (each does 3 reps with cool-downs). Cold-open is recorded
   automatically on open.
5. Back on Home → **Copy all** → paste the markdown into chat.
6. The agent fills the decision table (`docs/EDITOR_MOBILE_RESEARCH.md` §3.1),
   states the gate verdict in writing (25.2 / 25.3 / stay-on-C-now), and the
   follow-on part starts only after that.

### 4.4 CI record (2026-09-04)

| Round | Run | Outcome |
|---|---|---|
| 1 | `33846401954` | 🔴 app gates GREEN; bench step failed with no readable log → this round motivated the `::error` annotation emitter in the workflow step |
| 2 | `33847120745` | 🔴 9 kotlin errors via annotations: `ScriptEvent.atMs` not on the sealed interface, Sora `Content.insert` overload, `Compose2State` private-set writes from the composable, `LazyListState.scrollBy` (doesn't exist → `scroll { scrollBy }`), `ScenarioKind` visibility |
| 3 | `33847885679` | 🔴 main sources GREEN; test file missing `assertTrue` import |
| 4 | `33848545357` | 🔴 `lintVitalRelease` (fatal release lint inside `assembleRelease`) trips the same targetSdk-28 Play-policy check `:app`'s lint block disables → `lint { checkReleaseBuilds = false }` on the throwaway harness |
| **5** | **`33849153135`** | ✅ **GREEN — app assemble + tests + lint, bench assembleRelease + bench unit tests (incl. the 10 000-op `DocumentBuffer` fuzz), both artifacts uploaded** |

### 4.5 Device round 1 — INTERIM (2026-09-04, owner's first export)

The owner's first Copy-all export contains the six **cold_open** rows only;
the four scenario families (burst/fling/drag/churn) are still pending. What
the cold-open rows say:

| Candidate | bench.c | bench.html |
|---|---|---|
| C-now | **1155 ms** ⚠️ | 158 ms |
| C-sora | 112 ms | 51 ms |
| C-compose2 | 59 ms | 42 ms |

**⚠️ Measurement caveat, recorded before anyone over-reads this:** the owner's
screens were opened in home-screen order, so **C-now · bench.c was the FIRST
open in a cold process** — its 1155 ms includes one-time app startup + Compose
initialization, not just the file open. Every other number (including
C-now · bench.html at 158 ms) is a warm-process measurement. A fair C-now
bench.c number requires re-opening that screen once the process is warm;
requested from the owner alongside the scenario runs. `frames=0` on these rows
is by design (frame capture runs during scenario reps, not cold open).

### 4.6 Exit condition status

1. ⏳ Decision table filled from device runs — **waiting on the owner**.
2. ⏳ JOURNEY entry records the winner + raw numbers (entry created; numbers
   to be appended after the device round).
3. ⏳ Follow-on part explicitly started or cancelled in writing — blocked by 1.

PASS = all three. The gate is evidence; "feels faster" is not a measurement.
