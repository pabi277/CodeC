# Phase 84 — AI Level 7: agent correctness

> **Status:** IMPLEMENTED on `arena/01a0fe36-codec` · host-shim pre-validation **337/337** · merged with Phase 85 via authorized PR #110 to `main` @ `32e4a5f`; final-head Build APK **37084800939 ✅ GREEN**. Formal device acceptance remains POSTPONED (Level 12).
> **Owner command (verbatim):** “Start level 7” (2026-10-03).
> **Baseline:** `main` @ `392a614573137a56db112764a57409773b9fe962` (PR #109 merge). Level 7 spec: [`07_AGENT_CORRECTNESS.md`](../../../roadmaps/ai-integration/07_AGENT_CORRECTNESS.md); shared foundation: [`00_AGENTIC_MAP_AND_SECURITY_RULES.md`](../../../roadmaps/ai-integration/00_AGENTIC_MAP_AND_SECURITY_RULES.md).
> **Device acceptance:** not part of this phase. The formal device round remains **POSTPONED** (Level 12), not passed.

## What Level 7 is

The six defects that make the agent unreliable **today**, fixed. None is a new
feature; all six are the reason the owner saw raw `<<<CODEC_TOOL>>>` blocks left
on screen as an answer, a “49 of 24 reads” counter, and the same ~22 lines of a
file delivered over and over. This is pure policy plus thin wiring — **no new
file in `ui/ai/` beyond tests, no new dependency, no permission change, no
DataStore key, no endpoint change, and no wire-contract change. D4 untouched.**

| # | Fix | Defect | Rule | Part |
|---|---|---|---|---|
| 1 | Separate `modelResult` (full, for the model) from `detail` (short, for the timeline) | 1 | **S1** | [84.1](PART_84_1_TWO_STORES_AND_CUT_MARKER.md) |
| 5 | Preserve the truncation marker at every clip boundary | 1 | **S2** | [84.1](PART_84_1_TWO_STORES_AND_CUT_MARKER.md) |
| 2 | Enforce the tool budget in `resumeAgentOrStop`, not only in `AiAgentPolicy.decide` | 2 | — | [84.2](PART_84_2_BUDGET_AND_COUNTERS.md) |
| 3 | Clamp and split counters into executed / refused / reused; never render one mixed value | 3 | — | [84.2](PART_84_2_BUDGET_AND_COUNTERS.md) |
| 4 | Reserved final-synthesis turn on a budget stop, tools **masked** not removed | 4 | **S12** | [84.3](PART_84_3_FINAL_ANSWER_AND_PARSER.md) |
| 6 | `AiToolProtocol.parse` returns parsed calls **alongside** the format error | 5 | — | [84.3](PART_84_3_FINAL_ANSWER_AND_PARSER.md) |

## First-move evidence (rechecked against `main` @ `392a614`, 2026-10-03)

Every defect was re-verified at the current head before any edit; the line
numbers moved from the `4cb4151` audit but the code is unchanged.

| Defect | Evidence at `392a614` (pre-fix) |
|---|---|
| 1 — preview is the model's memory | `AiViewModel.kt:893` `detail = outcome.text.take(MAX_STEP_DETAIL_CHARS)` (1 200); `AiAgentLoop.kt` `renderStep` then `.take(8 000)` on the already-≤1 200 string |
| 2 — tool budget not enforced on resume | `AiViewModel.kt:748` `resumeAgentOrStop` checks `blockModelTurn` only; `blockTool` lives only in `AiAgentPolicy.decide` (`AiAgentLoop.kt:195`) |
| 3 — mixed, unclamped counter | `withToolCalls(denied.size)` / `withToolCalls(1)` at `AiViewModel.kt:828,879,896,901`; `AiCopy.kt:192` renders it all as “reads” |
| 4 — no final-answer stage | `stopAgent` (`AiViewModel.kt:1004`) sets phase/error/notice/steps, never `answer` |
| 5 — cut marker lost at the clip | the 1 200-char `take` drops `AiToolRunner.CUT_NOTE`, which sits at the end of a truncated result |
| 6 — parser discards valid calls | `AiToolProtocol.parse` (`AiTools.kt:101,105,113,117`) does an early `return Malformed(...)`, dropping the accumulated `calls` |

The Phase 83 baseline (`AiLevel6BaselineTest`) already measured these: a normal
file replay delivered 19–22 lines of a 400-line read, and a 49-block answer
rendered “49 of 24 reads”. Those measurements are the recorded “before”.

## Guardrails (unchanged and re-asserted)

- Exactly **three** `client.stream(` call sites preserved — the reserved
  synthesis turn reuses `agentTurn`, it does not add a fourth
  (`AiLevel7WiringTest` pins the count).
- `ui/ai/` keeps **zero** project writes and **zero** command execution; Apply
  through `AiEditApplier` stays the only project-write path.
- Canonical containment and the minSdk-24 `canonicalPath != absolutePath`
  symlink test untouched; the AI's **own** secret filter is still not
  `ProjectSearch.isSearchable`.
- Default model unchanged; `store:false`, D1–D6 and all amendments intact.
- No new dependency, permission, DataStore key, Settings control or telemetry.

## Parts

- [84.1 — two stores and the surviving cut marker (fixes 1, 5)](PART_84_1_TWO_STORES_AND_CUT_MARKER.md)
- [84.2 — the tool budget on every path and split counters (fixes 2, 3)](PART_84_2_BUDGET_AND_COUNTERS.md)
- [84.3 — a readable final answer and a parser that keeps good calls (fixes 4, 6)](PART_84_3_FINAL_ANSWER_AND_PARSER.md)

## Tests

- **New:** `AiLevel7CorrectnessTest` **16** (pure policy, one per acceptance
  check) · `AiLevel7WiringTest` **11** (source pins through `RepoFiles.codeOnly`).
- **Updated for the corrected contract:** `AiAgentLoopTest` (2 packing cases now
  set `modelResult`) · `AiLevel6BaselineTest` (the two cases that recorded the
  defect now assert the fix — see 84.1/84.2 “baseline re-run”).
- **Inventory:** 314 Kotlin test files / 3 000 `@Test`; **33 AI classes / 418 AI
  tests** (pre-Level-7: 312 / 2 973; 31 / 391).
- **Pre-validation:** 337/337 on the jdk4py + kotlinc 2.4.20 host harness against
  the real production sources (JUnit/org.json/coroutines shimmed; `internal`
  visible via `-Xfriend-paths`). CI (`:app:testDebugUnitTest`) remains the
  executor of record.

## Exit / verification ledger

- [x] State verified: branch `arena/01a0fe36-codec`, `main` @ `392a614`.
- [x] Evidence: all six defects re-read at `392a614` before editing.
- [x] Six fixes implemented as pure policy + thin wiring; three stream sites preserved.
- [x] 27 new tests; 4 pinned cases updated to the corrected contract; 337/337 host pre-validation.
- [x] Level 6 baseline re-run shows the full result now reaches the request and counters stay within caps.
- [x] Final-head Build APK CI for the combined Phase 84+85 PR: **37084800939 ✅ GREEN**.
- [x] PR #110 merged to `main` at `32e4a5f87a9f73874a4de17967187445c49a2461` on the owner's authorization.
- [ ] Device pass: **not requested** — the formal round stays with Level 12.
