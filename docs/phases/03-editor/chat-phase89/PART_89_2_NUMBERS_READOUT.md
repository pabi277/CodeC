# CodeC Phase 89.2 — The numbers readout (latency, tokens, memory)

> **Status:** 📋 PLANNED (brief only — nothing written, nothing run) · **Cost:** `[client-only]` · **Effort:** M
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
string (e.g. *"first 1.4 s · total 22.6 s · 1 812 in / 640 out · memory 61 MB"*), with every unknown rendered
as its own explicit word and durations rounded to a stable precision so two runs are comparable. Bounds and
rounding are asserted by tests; the strings live in `AiCopy` (the existing wording home) so `RepoFiles.codeOnly`
pins stay honest.

### 3. The samplers (wiring, thin)

- **Latency:** the task-start instant is taken where the agent budget is created
  (`AiViewModel.kt:1475`, `session.budget = AiAgentBudget(startedAtMs = System.currentTimeMillis())` already
  exists — read it, do not add a second); the first non-empty answer chunk stamps `firstTokenMs`; DONE/stop
  stamps `totalMs`. One clock, injected, so the logic is host-testable.
- **Tokens:** parse `usageMetadata` from the Gemini stream decoder and the usage event from the NVIDIA decoder
  (`NvidiaResponse.kt:21-22` currently *drops* it — the change is to read it and then still not render it as
  text), through pure decoders that fail to `null`.
- **Memory:** one sampler, `memoryBytes: () -> Long` (default `Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }`),
  called at the three task boundaries only. On device this is a JVM-heap number; it is labelled as such. An
  Android `Debug.getPss()` sampler is deliberately **not** added: it is more invasive and would not be
  host-testable.

### 4. Where it is shown

Beside the existing usage line in the activity card / `AiHome` panel — the surface the owner already reads.
No new screen, no new button, no chart, no export. The readout is **cleared** at every task start, on project
switch, and on Stop, and is never written to disk, a log, a DataStore key or a backup.

## The Android edge

- `AiViewModel.kt`: three stamp sites (start / first non-empty chunk / finish), one sampler, one new field in
  the existing UI state. No new coroutine, no new request, no new stream site — the count stays **exactly
  three** `client.stream(` sites (`:1096, :1519, :1903`).
- `AiChatSheet.kt` / `AiHome.kt`: one `Text` composed from `AiMeasurePolicy.render(...)` where
  `agentUsageLine` already renders. `AiCopy` owns the wording.
- D6 deletion story: nothing to delete — there is nothing on disk or in the key store.

## Exit condition

- [ ] First-token latency, total latency, provider tokens and sample memory render for a real task; each unknown
      renders as *"not reported"* / *"—"*, and `0` is never used as a stand-in.
- [ ] A malformed or hostile usage object yields `null`, never a negative, absurd or crashed render (**S3**).
- [ ] Nothing is persisted: no new DataStore key, no file, no log, no export; a D6 pin asserts the state class
      is display-only and the readout is cleared on task start / project switch / Stop.
- [ ] No ceiling moved (**S9** pins re-run); exactly three `client.stream(` and two `openUri(` sites.
- [ ] No new dependency, permission, endpoint or default-model change.

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
