# Phase 83 — AI Level 6: baseline and measurement

> **Status:** IMPLEMENTED test-only · temporary host-shim smoke **5/5** · Build APK CI ✅ GREEN on code/test head `cf3f1be` ([run 37051539267](https://github.com/pabi277/CodeC/actions/runs/37051539267)) · **zero production-source/behavior changes** · **not merged**.
> **Owner command:** “Start level 6” (2026-10-03).
> **Work branch:** `arena/01a0fded-codec`; baseline `main` @ `741956647933562dd5056972e71473581dd21233` (PR #108).
> **Device acceptance:** not part of this measurement-only phase. The separate formal device round remains **POSTPONED until after optimization**, not passed.

## Scope locked by Level 6

This phase establishes a reproducible pre-optimization baseline. It does not alter app behavior, request routing, provider settings, permissions, storage, tool policy, or UI. The four projects are generated under a host-test temporary directory; they contain synthetic, explicitly non-secret data and are never sent to a provider.

The measurement harness is test-only. The legacy phone screenshots remain the only live-model observation in the baseline; no key, project, screenshot, or request is replayed to a service.

## First-move evidence (rechecked against `main` @ `7419566`, 2026-10-03)

The merge from PR #108 is documentation-only; the app source audited at `4cb4151` is unchanged at this baseline. The current implementation confirms the reported context loss:

| Finding | Current source evidence | Baseline consequence |
|---|---|---|
| Tool results become timeline-sized model memory | `app/src/main/java/com/codeci/ide/ui/ai/AiViewModel.kt:886–898`: `detail = outcome.text.take(AiAgentLimits.MAX_STEP_DETAIL_CHARS)`; `AiAgentLoop.kt:50`: cap **1,200**; `AiAgentLoop.kt:285–289`: the next request packs `step.detail` | An 8,000-character result is reduced to 1,200 characters before the next request; the cut marker is lost. |
| Tool-result caps | `AiTools.kt:198–218`: `MAX_READ_LINES = 400`, `MAX_RESULT_CHARS = 8,000`, and list/search caps | A higher result cap alone cannot bypass the separate 1,200-character detail clip. |
| Large-file reads are prefix-only | `AiProjectFiles.kt:58`: `MAX_READ_CHARS = 24,000`; `AiProjectReader.kt:181–196`: `readCapped(file)` has no offset | A later requested range can only address the already-read prefix. |
| Mixed counter can exceed its cap | `AiViewModel.kt:896–903`, `AiCopy.kt:191–194`; budget display has no clamp | Executions and refusals can produce “49 of 24 reads.” |
| Existing protections to retain | `AiProjectReader.kt:217–225` canonical containment and `canonicalPath != absolutePath`; `AiProjectFiles.kt:80–133` AI-specific secret/excluded filters | Fixture coverage must not weaken path containment or reuse `ProjectSearch.isSearchable`. |

The phone evidence itself is recorded in [`AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md`](../../../research/AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md#2-phone-evidence--observed-behavior-not-an-acceptance-result). The originals remain owner attachments and are not copied into Git.

## Part

- [83.1 — synthetic fixtures, offline replay, and baseline ledger](PART_83_1_FIXTURES_AND_REPLAY.md)

## Baseline result

[`BASELINE.md`](BASELINE.md) separates observed phone evidence from the deterministic, provider-free fixture replay. It records per-format lines actually delivered, request-body bytes produced by the existing NVIDIA serializer (not sent), the 49-call accounting reproduction, the unreachable large-file tail, and exact APK artifact sizes from Build APK check annotations (main run 37047037300 vs code/test run 37051539267). Live/provider latency, model token usage/cost, and phone memory remain explicitly unmeasured rather than inferred.

Pre-Level-6 inventory on this checkout: **310 Kotlin test files / 2,968 `@Test` annotations**; **30 `Ai*.kt` test classes / 386 AI tests**. Level 6 adds five test-only baseline cases; the post-change inventory is **391 AI tests across 31 classes**, and the real Build APK workflow passes on code/test head `cf3f1be`.

## Guardrails

- `git diff` contains **zero production-source changes**.
- No dependency, permission, endpoint, API call, DataStore key, persisted memory, telemetry, Settings control, or provider-default change.
- No fixture contains real credentials or user project content; the `.env` value is explicitly fake.
- No app behavior or answer quality is improved/claimed by this phase.
- Levels 7–14 remain separate, proposed work; each needs its own explicit start. Level 8 still needs the owner’s “bounded but honest” decision; Level 9 still needs a D6 amendment.

## Exit / verification ledger

- [x] State verified: branch `arena/01a0fded-codec`, clean start, `main` @ `7419566`; PR #108 merged; main Build APK CI `37047037300` green.
- [x] Re-read exact Level 6 scope and source pins; no stale test inventory carried forward.
- [x] Four deterministic synthetic project shapes generated from test-only code; no checked-in bulk data.
- [x] Baseline records owner-observed failures separately from replay-derived values and unknown live metrics.
- [x] Five test-only baseline cases added; all **386** pre-existing AI tests remain unchanged in source.
- [x] Local prevalidation: **5/5** new tests against the real agent/read/map classes with temporary host shims (smoke only; not a substitute for CI).
- [x] Build APK CI **37051539267** green on code/test head `cf3f1be` (real Gradle/JUnit/Robolectric/screenshot tests, debug/lint, signed release/APK checks and artifacts).
- [x] Production source delta: **0 files**.
- [x] No device pass requested or claimed; postponed formal device acceptance stays with Level 12.
- [ ] PR/merge: not authorized or opened. Stop at `rule.md` §3.
