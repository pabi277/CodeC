# CodeC Phase 89.2 — The numbers readout (latency, tokens, memory)

> **Status:** ✅ **IMPLEMENTED (2026-10-04) on `arena/01a102bd-codec`** — the readout is written: `AiMeasurements.kt` + `AiLevel12MeasureTest` (**15 cases**) + `AiLevel12WiringTest` (**7 cases**), all green · **Cost:** `[client-only]` · **Effort:** M
> **Owner authorization (2026-10-04):** asked how latency, tokens and memory should be measured, the owner chose
> **"a small in-memory numbers-only readout"** — new app code that **must respect D6**.
> **Laws that bind this part:** **D6** (never persist raw chat, prompts, answers or the timeline — and here, no
> new persisted key at all) · **S3** (provider output is untrusted data) · **S9** (no ceiling moves) ·
> **S8/D4** (disclosure untouched) · no new dependency, permission, endpoint, DataStore key or default model.
> **Parent brief:** [Phase 89 README](README.md)

## First move: evidence, not code

Read 2026-10-04 on `main` @ `0fc2bfd`.

**Nothing is measured today, and the seams to measure are already there:**

| Fact | Where | What it means |
|---|---|---|
| No timer wraps a request | no `System.nanoTime()`/elapsed timer in `ui/ai/` (the only `currentTimeMillis` uses are the wall-clock budget, rate-limit and key timestamps) | a first-token and total latency reading is genuinely new |
| A clock is already injectable at the stream edge | `AiHttpStream.kt:27` — `nowMs: () -> Long = System::currentTimeMillis` | reuse the pattern (`nowMs` sampler) rather than inventing a second clock |
| The whole answer is republished per event | `AiHttpStream.kt:87` — `onText(accumulator.text)`, consumed at `AiViewModel.kt:1113-1114` | "first token" is observable as **the first non-empty `answer`**, no new stream plumbing |
| Tokens are nowhere | `grep usageMetadata ui/ai/` = **0**; `NvidiaResponse.kt:21-22` deliberately drops the usage-only event | parsing has to be added, carefully, per provider — or reported as *unknown* |
| Counters already have a home | `AiAgentUsage` (`AiAgentState.kt:14-30`) rendered by `AiCopy.agentUsageLine` | the readout is a sibling of an existing display surface, not a new screen |
| Display state is ephemeral by law | `AiAgentUsage` is *"in memory, display only"* | the precedent the readout follows exactly |

## Design

Two small pieces, in the house pattern (pure policy + thin wiring):

### 1. `AiMeasurements` — the values (pure, host-testable)

```kotlin
/** In-memory display state only. Never written anywhere (D6). */
data class AiMeasurements(
    val firstTokenMs: Long? = null,   // null = not observed yet
    val totalMs: Long? = null,        // null = task still running or not measured
    val promptTokens: Int? = null,    // null = provider did not report
    val outputTokens: Int? = null,
    val memoryBytes: Long? = null     // sample at task boundaries only
)
```

Rules the type encodes:

- **`null` means "not reported", never `0`.** A provider that sends no usage shows *"tokens not reported"*;
  a run that has not finished shows *"—"*. This is the same honesty rule as Level 8's coverage fields.
- **Tokens are clamped and treated as untrusted (S3):** a decoded value is accepted only as a non-negative
  `Int` within a declared bound; anything else becomes `null`. A malformed usage object can never render a
  negative or absurd number.
- **Memory is a sample, not a peak claim.** The field is named for what it is; the UI says *"memory at
  finish"*, never *"peak memory"*.

### 2. `AiMeasurePolicy` — the formatting (pure, host-testable)

A single pure function pair: `AiMeasurePolicy.render(measurements): AiMeasureLine` producing the numbers-only
string (*"first 1.4 s · total 22.6 s · tokens 1 812 in / 640 out · memory at finish 61.4 MB"*), with every unknown
rendered as its own explicit word and durations rounded to a stable precision so two runs are comparable.
Bounds and rounding are asserted by tests. **Amended 2026-10-04 (deviation 3):** the wording lives in
`AiMeasurePolicy` itself — the format and its labels are one decision, and a label in a different file than the
rule that picks it is how a readout drifts. `AiCopy` still owns every sentence *about* the readout.

### 3. The samplers (wiring, thin)

- **Latency:** the task-start instant is taken where the agent budget is created
  (`AiViewModel.kt:1475`, `session.budget = AiAgentBudget(startedAtMs = System.currentTimeMillis())` already
  exists — read it, do not add a second); the first non-empty answer chunk stamps `firstTokenMs`; DONE/stop
  stamps `totalMs`. One clock, injected, so the logic is host-testable.
- **Tokens:** parse `usageMetadata` from the Gemini stream decoder and the usage event from the NVIDIA decoder
  (`NvidiaResponse.kt:21-22` currently *drops* it — the change is to read it and then still not render it as
  text), through pure decoders that fail to `null`.
- **Memory:** one sampler, called once at the task's terminal stamp. On device this is a JVM-heap number and
  the line says *"memory at finish"*, never *"peak memory"*. An Android `Debug.getPss()` sampler is
  deliberately **not** added (more invasive, and not host-testable). **Amended 2026-10-04 (deviation 4):** the
  sampler cannot live in `ui/ai` at all — the package's standing Level 3/4/7/9/10 guard forbids
  `Runtime.getRuntime` there (it is the command-execution token), so collection moved to
  `ui/performance/HeapProbe.kt` beside `FrameBudget`, and `AiViewModel` keeps only the number. The guard was
  not weakened.

### 4. Where it is shown

Beside the existing usage line in the activity card / `AiHome` panel — the surface the owner already reads.
No new screen, no new button, no chart, no export. The readout is **reset** at every task start and by `clear()`
(which is the project-switch path), and is never written to disk, a log, a DataStore key or a backup.
**Amended 2026-10-04 (see deviation 5):** a Stop **keeps** the numbers instead of erasing them — a stopped task
is measured, and the numbers still die with the next task.

## The Android edge

- `AiViewModel.kt`: three stamp sites (start / first non-empty chunk / finish), one sampler, one new field in
  the existing UI state. No new coroutine, no new request, no new stream site — the count stays **exactly
  three** `client.stream(` sites (`:1096, :1519, :1903`).
- `AiChatSheet.kt` / `AiHome.kt`: one `Text` composed from `AiMeasurePolicy.render(...)` where
  `agentUsageLine` already renders. `AiCopy` owns the wording.
- D6 deletion story: nothing to delete — there is nothing on disk or in the key store.

## Exit condition

- [x] First-token latency, total latency, provider tokens and sample memory render for a real task; each unknown
      renders as *"not reported"* / *"—"*, and `0` is never used as a stand-in. (15 cases in
      `AiLevel12MeasureTest`, incl. *an unreported provider says so once the task is finished and never claims
      zero* and *a half-reported usage keeps the missing side as a dash and is not zeroed*.)
- [x] A malformed or hostile usage object yields `null`, never a negative, absurd or crashed render (**S3**).
      (*S3 — a hostile report cannot render a negative, absurd or crashing number*, *sums saturate at the bound
      instead of wrapping*.)
- [x] Nothing is persisted: no new DataStore key, no file, no log, no export; a D6 pin asserts the state class
      is display-only and the readout is cleared on task start / project switch / Stop. (D6 pin in
      `AiLevel12WiringTest`, fed by `RepoFiles.codeOnly`; the readout resets in `startMeasuring` and in `clear()`,
      which is the project-switch path. **Amended:** a *Stop* keeps the numbers, see deviation 5 below.)
- [x] No ceiling moved (**S9** pins re-run); exactly three `client.stream(` and two `openUri(` sites.
- [x] No new dependency, permission, endpoint or default-model change. (The one new file is `HeapProbe.kt`,
      `java.*` only — deviation 4 below.)

## Tests (plan)

- `AiMeasurePolicyTest` (~8 cases): formatting for known values; each unknown rendered as its own word; a
  negative or absurd token value becomes unknown; rounding stability; duration units (ms / s / m); memory
  labelled as a sample; empty state; no `0` lie.
- `AiLevel12WiringTest` (~6 source pins): the readout is fed from the injected samplers; the task-start instant
  is the existing budget instant, not a second clock; exactly three `client.stream(` sites; two `openUri(`
  sites; no new `DataStore`/`edit()` key; no persistent write in `ui/ai/`.
- `AiLevel10CeilingTest` and the three stream pins re-run unchanged.

## Sources (record)

1. `AiHttpStream.kt:27,87` · `AiViewModel.kt:1113-1114,1475,1096,1519,1903` · `AiAgentState.kt:14-30` ·
   `NvidiaResponse.kt:21-22` · `AiCopy.kt` (usage line) — read 2026-10-04 on `0fc2bfd`.
2. `00_LEVEL0_DECISION_RECORD.md` — D6 and its Level 9 amendment (the amendment is **not** widened here: it
   covers bounded non-secret file snapshots, not measurements).
3. Level 12 spec, *Measurements* section — first-token and total latency, token and cost use where the provider
   reports it, peak memory.

## Deferred / rejected with reasons

- **Cost in currency.** Deferred: no provider reports price per request; a converted figure would be invented.
- **Peak memory via a sampling loop.** Rejected: a polling timer on the main thread to chase a number nobody
  acts on. Three boundary samples, labelled as samples.
- **Persisting a run history (a "last 10 tasks" list).** Rejected (**D6**): that is a new store and a new key,
  and it would outlive the session the owner asked to measure.
- **Android `Debug.getPss()`.** Deferred: it is the more accurate number for the phone, but it is not
  host-testable and it is not needed to answer the owner's question.
- **A separate evaluation screen or a chart.** Rejected: new surface area, no new evidence.
- **Tokens for providers that do not report them (estimating from characters).** Rejected: an estimate would be
  indistinguishable from a measurement; the spec's own token arithmetic (3 chars/token) is explicitly a rough
  budget model, not a reported figure.

## Implementation (2026-10-04)

Written on `arena/01a102bd-codec`; **24 cases** (17 measure + 7 wiring), all green in the sandbox host harness
and on CI.

| File | Change |
|---|---|
| `app/src/main/java/com/codeci/ide/ui/ai/AiMeasurements.kt` | **new** — `AiMeasureLimits`, `AiTokenUsage`, `AiMeasurements`, `AiMeasurePolicy` (pure; clock-free, Android-free) |
| `app/src/main/java/com/codeci/ide/ui/performance/HeapProbe.kt` | **new** — the boundary heap sample, outside `ui/ai` (deviation 4) |
| `app/src/main/java/com/codeci/ide/ui/ai/GeminiResponse.kt` | `GeminiChunk.usage` + `usageFrom(usageMetadata)`; an absent report stays `null` |
| `app/src/main/java/com/codeci/ide/ui/ai/NvidiaResponse.kt` | `usageFrom(usage)`; a usage-only event is now kept with its counts instead of dropped |
| `app/src/main/java/com/codeci/ide/ui/ai/AiAnswer.kt` | `AiOutcome.Answer.usage` + the `reportedUsage` accumulator (recorded before the failure/cap early exits) |
| `app/src/main/java/com/codeci/ide/ui/ai/AiViewModel.kt` | `AiUiState.measurements`, one clock field, four private helpers, wired into `clear()`, both Send paths, both `onText` callbacks, both outcome paths, `stopAgent` and `finishAgent` |
| `app/src/main/java/com/codeci/ide/ui/ai/AiChatSheet.kt` | `MeasurementsLine(state)` — one helper, drawn under the progress line in the activity card (live for an agent task) and in the terminal area whenever the card is not on screen, so every finished task reports numbers exactly once |
| `app/src/test/java/com/codeci/ide/AiLevel12MeasureTest.kt` · `AiLevel12WiringTest.kt` | the 24 cases above |

Deviations from the plan above (all intent-preserving; 3–5 are the ones a reviewer should read):

1. **`render` returns `String?`, not an `AiMeasureLine` wrapper.** The sheet already renders strings through
   `Muted`, so a wrapper type would add a hop and no honesty. `null` means "nothing measured yet".
2. **The clock is not a new injected sampler at the stream edge.** The *policy* takes `nowMs` (clock-free, so
   the host harness can drive it); the ViewModel reads `System.currentTimeMillis()` at the three boundaries —
   the same clock the agent budget already uses, captured **once** per request and shared with it.
3. **The wording lives in the policy, not in `AiCopy`** (see the amended paragraph above).
4. **The heap sample lives in `ui/performance/HeapProbe.kt`.** `ui/ai` has a standing guard against
   `Runtime.getRuntime` (Levels 3, 4, 7, 9 and 10 all re-assert it). Rather than weaken a security guard for a
   display number, collection moved next to `FrameBudget` and the AI core keeps the number. `AiLevel12WiringTest`
   now pins both halves: **no** `ui/ai/*.kt` may contain `Runtime.getRuntime`, and `HeapProbe.kt` itself reads
   no file, writes nothing, logs nothing.
5. **Stop keeps the numbers** (the plan said "cleared on Stop"). A stopped task is a terminal state with a
   partial answer on screen, and the Level 12 matrix has a **Stop** row that needs its own readout, so erasing
   it would delete the evidence the row exists to record. The numbers are still display state and still die
   with the next Send / `clear()`.
6. **`memory` → `memory at finish`** in the rendered line, and the duration formatter picks its unit from the
   rounded tenths so a hair under a minute reads `59.9 s` and 59.95 s reads `1 m 0 s` (never `60.0 s`).
7. **The reset-on-project-switch claim is `clear()`.** Reading the ViewModel showed project switching goes
   through `onProjectChanged(...)` → `clear()`, so there is no second path to pin.
9. **The NVIDIA usage-only event keeps its null-ness contract exactly.** The first cut returned
   `null` from such an event when its `usage` object named no usable `prompt_tokens` / `completion_tokens`
   pair (e.g. a `total_tokens`-only report). CI round 1 caught it (`NvidiaResponseTest` — *DONE and
   usage-only events carry no answer text*, `NullPointerException` at `NvidiaResponseTest.kt:56`): an event
   **with** a `usage` object must stay a parseable chunk with empty text, whatever fields it carries. The
   decision is therefore made on the presence of the `usage` object, not on the pair, and an unnameable pair
   simply renders *"not reported"* — the readout never invents a number from a total. Two host cases were
   added so this contract cannot regress silently again (the test that caught it is Robolectric-only and not
   host-runnable).
8. **The readout is drawn for every finished task, not only for agent tasks.** The plan's "beside the usage
   line in the activity card" would have hidden the numbers for a single-shot ask (the card is drawn only when
   a tool step exists). One helper now renders the line in the card *and*, guarded by
   `state.agentSteps.isEmpty()`, in the `DONE`/`FAILED` area — exactly one line on screen per task, and the
   wiring test pins both the helper and the two guards. `preview()` also clears the numbers with the rest of
   the previous task's state.

## CI round (the evidence of record)

**Round 1 — Build APK `37182525547` on head `e7a8677` — ❌ failed, and it caught a real regression.**
`3 242 tests completed, 1 failed`: `NvidiaResponseTest > DONE and usage-only events carry no answer text
FAILED` (`java.lang.NullPointerException at NvidiaResponseTest.kt:56`). Cause: the first cut of the NVIDIA
usage read returned `null` for a usage-bearing event whose `usage` object named no `prompt_tokens` /
`completion_tokens` pair, which broke the Level 5 contract (an event with a `usage` object is a chunk with
empty text). **No production contract was rewritten to make a test pass**: the presence of the `usage` object
again decides whether the event is a chunk, and an unnameable pair stays *"not reported"*. Two host cases
were added in the same change to pin the contract, because the test that caught it is Robolectric-only and
could not have caught it in the sandbox harness.

**Round 2** — on the fix head, recorded with its run id and byte counts in the
[Phase 89 README](README.md#implementation-record-2026-10-04) and in
[`DEVICE_ROUND.md`](DEVICE_ROUND.md).
