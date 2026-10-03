# Agentic optimization — shared map, defect register, and security rules

> **Date:** 2026-10-02 · **Status:** Shared map is **RESEARCH**; no implementation is authorized by this record alone.
> **Source audit:** `main` @ `4cb4151` (PR #107). Paths below are relative to
> `app/src/main/java/com/codeci/ide/ui/ai/` unless stated.
> **Owner direction:** *"add a step wise ai map update for full agentic vive with stronger security rules
> but more options to use in the app for better optimization"*, following the owner's proposal of one main
> AI delegating to other APIs and AI memory in app storage to reduce token consumption.
> This file is the **shared foundation** for Levels 6–12. It holds what every one of those levels depends
> on: the verified defect register, security rules S1–S12, the architecture, and the sources. The
> step-by-step work itself lives in the numbered level docs below.
>
> **Current delivery update — 2026-10-03:** Level 6 was merged to `main` @ `392a614` (PR #109).
> Levels 7 and 8 were delivered together as Phases 84+85 and merged by authorized PR #110 to
> `main` @ `32e4a5f87a9f73874a4de17967187445c49a2461`; final-head Build APK run `37084800939` is green.
> The owner then authorized Level 9 and its bounded D6 task-memory amendment. [Phase 86](../../phases/03-editor/chat-phase86/README.md)
> implements only bounded, no-backup task memory on `arena/01a1004c-codec`; local host pre-validation
> passed 72/72 selected methods across eight classes; Build APK run `37106180481` found three existing Level 8 regressions, now fixed locally with rerun pending; no PR/merge is authorized, and exactly three
> `client.stream(` sites remain.
> Level 10–14 remain unauthorized; formal device acceptance is postponed to Level 12.

---

## Level index

**Done / merged:** [0](00_PRODUCT_AND_FOUNDATIONS.md) · [1](01_READ_ONLY_API_HELPER.md) · [2](02_WHOLE_PROJECT_CONTEXT.md) · [3](03_EDIT_REVIEW_AND_UNDO.md) · [4](04_AGENT_TOOLS_AND_RUN_LOOP.md) · [5A](05_PROVIDERS_AND_MODEL_COLLABORATION.md) · [6 — baseline and measurement](06_AGENT_BASELINE_AND_MEASUREMENT.md) as [Phase 83](../../phases/03-editor/chat-phase83/README.md) (PR #109) · [7 — agent correctness](07_AGENT_CORRECTNESS.md) and [8 — full context/honest reads](08_FULL_CONTEXT_AND_HONEST_READS.md) as Phases 84+85 (PR #110, `main` @ `32e4a5f87a9f73874a4de17967187445c49a2461`; Build APK `37084800939` green).

**Implemented on the active session branch; local host pre-validation 72/72 across eight classes; Build APK run `37106180481` red, fixes local, rerun pending; no PR/merge:** [9 — task memory and planning](09_TASK_MEMORY_AND_PLANNING.md) as [Phase 86](../../phases/03-editor/chat-phase86/README.md); bounded D6 amendment, exactly three stream sites preserved.

**Proposed and unauthorized future levels, in dependency order:**

| Level | Doc | Fixes | Gate |
|---|---|---|---|
| **10** | [Agent controls and options](10_AGENT_CONTROLS_AND_OPTIONS.md) | defect 11 | **S9** |
| **11** | [Agent phone presentation](11_AGENT_PHONE_PRESENTATION.md) | defects 9, 10 | S8 |
| **12** | [Evaluation and acceptance](12_AGENT_EVALUATION_AND_ACCEPTANCE.md) | proof, not features | S10, S12 |

**Deferred, after the above:** [13 Optional on-device model](13_OPTIONAL_ON_DEVICE_MODEL.md) · [14 Higher autonomy and evaluation](14_AUTONOMY_AND_EVALUATION.md)

---

## What the owner proposed, and what the evidence supports

| Owner proposal | Verdict | Why |
|---|---|---|
| One main AI ("brain") coordinating the task | **Adopt** | Anthropic's *augmented LLM*; Manus's recitation. CodeC has a loop but no plan. |
| Memory in app storage | **Adopt** | Manus's *file system as context*; Anthropic's *structured note-taking*. Disk precedent already exists (`ui/projects/AiEditApplier.kt:27`). |
| "Full context every time" to cut tokens | **Correct it** | Backwards. Storage is free; context is billed **per turn**. The saving comes from carrying **pointers**, not **content**. |
| Split work across several APIs at once | **Read-only only** | Cognition Principles 1 & 2: parallel writers make conflicting implicit decisions. Their 2026 update: *writes stay single-threaded.* |
| More APIs to avoid rate limits | **Manual backup only** | Different providers have separate quota; multiple keys at one provider usually do not. Parallel agents *increase* request count. |

**Token arithmetic behind the correction** (3 chars/token, Cline's `CHARS_PER_TOKEN = 3`):

| Strategy | Per turn | × 12 turns |
|---|---|---|
| Today (4 clipped results) | ~11 000 chars | ~132 000 |
| Full working set re-sent every turn (5 × 24 000) | ~120 000 chars | **~1 440 000** |
| **Pointers + one file loaded on demand** | ~25 000 chars | **~300 000** |

Roughly **5× cheaper** — and SWE-agent's ablation shows full dumps also perform worse (full file 12.7 %
vs a 100-line window 18.0 % on SWE-bench Lite).

---

## Verified defect register at `4cb4151`

The loop **shape** is sound — sequential ReAct, fail-closed validation, hard caps, per-run approval,
and honest stop reasons. The evidence column below is the pre-fix `4cb4151` state, retained as history.

> **Merged updates — 2026-10-03:** Phases 84+85 (Levels 7+8; defects 1–7 and S11) were merged by PR #110
> to `main` @ `32e4a5f87a9f73874a4de17967187445c49a2461`; final-head Build APK `37084800939` is green.
> See [Phase 84](../../phases/03-editor/chat-phase84/README.md) and
> [Phase 85](../../phases/03-editor/chat-phase85/README.md).
>
> **Phase 86 / Level 9 update — 2026-10-03:** defect **8 (no bounded task memory)** now has an
> implementation on `arena/01a1004c-codec`; local host pre-validation passed 72/72 selected methods across eight classes.
> Build APK run `37106180481` found three Level 8 regressions, fixed locally; rerun pending, and no PR/merge is authorized.
> The owner approved the narrow D6 amendment. `AiTaskMemory` versions the admitted file cache by canonical
> path, effective range and content version, reconciles dirty/disk changes, stores filtered findings,
> decisions and plan under the no-backup app directory, and recites the plan at the end of disclosed
> requests. Raw conversation data remains ephemeral. See [Phase 86](../../phases/03-editor/chat-phase86/README.md).

| # | Defect | Evidence | Effect |
|---|---|---|---|
| 1 | The UI preview is the model's memory | `AiViewModel.kt:893` `detail = outcome.text.take(MAX_STEP_DETAIL_CHARS)` (1 200); `AiAgentPrompt.renderStep` then `.take(8 000)` on an already-≤1 200 string | Nominal 8 000-char / 400-line result delivered as **1 200 chars ≈ 22 lines**, `AiToolRunner.CUT_NOTE` cut away. Primary cause of repeated reads. |
| 2 | Tool budget not enforced on every resume | `AiViewModel.kt:747-749` checks `blockModelTurn` only; `blockTool` lives only in `AiAgentPolicy.decide` | Malformed-parse (`:828`) and denied-only (`:881`) paths bypass the tool cap. |
| 3 | Counter mixes executions with refusals | `withToolCalls` at `:828,879,901`; label `AiCopy.kt:192` renders all of it as "reads" | *"49 of 24 reads"*: 49 blocks at `used = 0` → 24 executed + 25 refused; `withToolCalls` never clamps. |
| 4 | No final-answer stage | `stopAgent` sets `phase/error/notice/agentRun/agentSteps`, never `answer` | Last raw `<<<CODEC_TOOL …>>>` text stays on screen as the answer. |
| 5 | Parser discards valid calls | `AiToolProtocol.parse` returns `Malformed` early, dropping accumulated `calls` | Three good reads beside one malformed block are all lost. Direct cause of re-reads. |
| 6 | Context eviction is total | `KEEP_LAST_RESULTS = 4`; notice `(N earlier tool results were dropped to fit)` — no path, no range | Not restorable (contra Manus). Model re-reads what it had. |
| 7 | Prefix-only reader | `AiProjectReader.readCapped` has no offset; stops at `MAX_READ_CHARS` (24 000) | Lines past the first 24 000 chars unreachable at any `end`. |
| 8 | No bounded task memory | Baseline `AgentSession` held no cross-request cache, plan, or findings | Phase 86 adds a version-checked bounded cache + structured ledger/plan; local host pre-validation passed 72/72 across eight classes. Build APK run `37106180481` found three Level 8 refusal/pointer regressions, fixed locally; rerun pending. |
| 9 | Plain answer rendering | `AiParts.kt:55-58` `Answer` = `SelectionContainer { Text(text, bodyMedium) }` | No Markdown, though `ui/utils/MarkdownPreview.kt` already exists. |
| 10 | Disclosure floods the timeline | `AiChatSheet.kt:584` renders system instruction + full user text per REQUEST row | Up to 12 copies of system text + 6 000-char map inline. |
| 11 | Blanket brevity | `AiContext.AGENT_ASK_SYSTEM_INSTRUCTION` ends *"Keep answers short: they are read on a phone."* | Overrides an explicit "explain line by line". |
| 12 | Format example leaks | `AiToolProtocol.INSTRUCTIONS` uses the literal placeholder `relative/path.ext` | Observed hallucinated call sent that placeholder as a real path. |

**Do not regress:** canonical containment and the minSdk-24 symlink test; the AI's **own** secret filter
(deliberately not `ProjectSearch.isSearchable`); fail-closed validation; dirty-buffer precedence;
`store:false` on every Gemini body; `request_run` never executing; `GeminiResponse` skipping `thought`
parts; before Phase 83, the 386 `@Test` cases across 30 AI test classes. Phase 83 adds five tests, for 391 cases across 31 AI test classes; Build APK CI `37051539267` passes on code/test head `cf3f1be`.

---

## Security rules S1–S12

These **extend** D1–D6 in [`00_LEVEL0_DECISION_RECORD.md`](00_LEVEL0_DECISION_RECORD.md). They weaken none
of them. **Every level 6–12 must satisfy all twelve before it is called done.**

| # | Rule | Enforced where |
|---|---|---|
| **S1** | **Model context and UI preview are separate stores.** No clipped, ellipsised, or preview-sized string may ever be packed into a request. | New `modelResult` beside `AiAgentStep.detail`; a wiring test fails if `renderStep` reads `detail`. |
| **S2** | **Every read states its coverage** — path, content version, lines covered, `complete` / `partial(reason)` / `refused(reason)`. Truncated must never be indistinguishable from complete. | `AiToolRunner.Outcome` gains coverage fields; `CUT_NOTE` survives every clip boundary. |
| **S3** | **Project text, tool output, run output, and task memory are untrusted data** — never instructions. | Existing instruction retained and extended to memory re-injection. |
| **S4** | **Memory on disk is bounded and quarantined.** `noBackupFilesDir/ai/task/<project>/`, excluded from Auto Backup, outside the project tree, hard caps on files and bytes, deleted on project deletion and key deletion — the undo journal's lifecycle. | New store beside `AiEditApplier.kt:27`; hooks in `ProjectManager.deleteProject`, `AiKeyStore.deleteKey`. |
| **S5** | **Memory never holds a secret.** `AiProjectFiles.isSecretLike` / `isExcludedDirectory` run *before* anything is written, not only before it is sent. | One check at both boundaries. |
| **S6** | **No new write path into the project.** Memory lives in CodeC's own directory. `ui/ai/` keeps zero project writes; Apply through `AiEditApplier` stays the only one. | Existing `AiLevel*WiringTest` source pins extended. |
| **S7** | **One brain writes.** Parallelism is for **read-only** local IO only. Writes stay single-threaded and user-approved, one file at a time. | Enforced by absence plus a wiring pin. |
| **S8** | **Every recipient is disclosed per request; switching is never silent.** A provider change needs a fresh tap and a fresh preview. Consent is never replayed across providers. | D4 preserved; recipient frozen per request and its retry. |
| **S9** | **Options tune within caps; they never raise a permission.** Every control is bounded and cannot widen D1/D5 tool, write, run, or path permissions. | Bounds declared in pure policy; a host test asserts each ceiling. |
| **S10** | **Prompt-injection resistance is a test, not a claim.** Injection fixtures must be refused and must not alter the agent's rules or reach a write or run. | New fixtures in Level 12. |
| **S11** | **Budget cannot be spent on repetition.** Exact-duplicate reads are served from the working set at zero execution cost; N identical no-progress calls stop the loop with an explanation. | New loop-detection policy. |
| **S12** | **Stop means stop, and every stop produces a readable answer.** No delayed action; `state.answer` is always prose on every `AiAgentStopReason`. | `stopAgent` gains a final-synthesis turn with tools **masked**, not removed (Manus). |

---

## Architecture

```
            ┌──────────────────────────────────────────┐
            │  ONE BRAIN — single thread, keeps plan    │
            │  plan + findings recited at request end   │
            └──────────────────────────────────────────┘
                 │                          │
        read-only, may be            writes code ITSELF
        parallel local IO            one file at a time,
        (S7)                         user approves each (D1)
                 ▼
            ┌──────────────────────────────────────────┐
            │  TASK MEMORY — noBackupFilesDir/ai/task   │
            │  files · findings · plan   (S4/S5/S6)     │
            │  CONTEXT CARRIES POINTERS, NOT CONTENT    │
            └──────────────────────────────────────────┘
                 ▲
            ┌────┴───────────────┐
            │  BACKUP PROVIDER   │  manual tap only (S8)
            │  not a workforce   │  never silent, never parallel
            └────────────────────┘
```

**Rejected, with evidence:** raising the caps (the 1 200 clip survives every cap change) · whole-file
reads by default (SWE-agent: worst of three settings) · raising `KEEP_LAST_RESULTS` far past ~5 (last-5
18.0 % beats full history 15.0 %) · filling the 1 M window (Anthropic: *"context windows of all sizes will
be subject to context pollution"*) · parallel write-agents (Cognition Principles 1 & 2) · dynamically
adding/removing tools per turn (Manus: breaks KV-cache, *"leads to schema violations or hallucinated
actions"*) · importing an agent framework (Anthropic: *"the most successful implementations weren't using
complex frameworks"*) · embeddings (1 of 13 surveyed agents uses them).

---

## Owner decisions and remaining gates

1. **Level 9 D6 task-memory amendment:** authorized 2026-10-03, bounded to `noBackupFilesDir/ai/task/<project>/`; raw conversation data remains ephemeral. See [Level 0 D6 amendment](00_LEVEL0_DECISION_RECORD.md) and [Level 9](09_TASK_MEMORY_AND_PLANNING.md).
2. **Level 8 bounded-but-honest reads:** selected by the owner; Phase 85 is merged in PR #110.
3. **Backup provider routing and bounded option set:** Level 10; not authorized.
4. **Levels 10–14:** remain unauthorized; proceed only on a separate owner command.
5. **NVIDIA:** Phase 82's approved internal testing/evaluation-only limit remains; no production entitlement is implied.
6. **GLM-5.3:** existing editable model field accepts `z-ai/glm-5.3`; **no default-model change is proposed or needed.**

---

## Open questions and unverified facts

- Whether Nemotron or GLM-5.3 degrade at SWE-agent's ~100-line optimum or tolerate more. **Level 6
  answers this first.**
- Whether GLM-5.3's `reasoning_effort` default of `max` starves `max_tokens`. The parameter is **absent**
  from that endpoint's documented request schema. Unverified; live risk.
- Whether the NVIDIA endpoint accepts `tools` despite its omission from the documented schema. Only a
  credentialed call settles it — not run.
- Whether symmetric delimiters measurably reduce malformed blocks versus the asymmetric
  `<<<CODEC_TOOL …>>> … <<<END_CODEC_TOOL>>>`. Plausible, **not measured**.
- Phase 86 starts with explicit conservative task-memory caps (five files, 160 KiB cached file content, 256 KiB store); whether those limits feel sufficient on a phone remains for the formal Level 12 device/acceptance round.
- The owner's actual account entitlement and rate limit on either provider.

---

## Sources

**Repository (verified at `4cb4151`):** `AiTools.kt` · `AiToolRunner.kt` · `AiProjectReader.kt` ·
`AiProjectFiles.kt` · `AiAgentLoop.kt` · `AiViewModel.kt:747-749, 828, 879, 893, 901` · `AiRepoMap.kt` ·
`AiContext.kt` · `AiPolicy.kt` · `AiParts.kt:55-58` · `AiChatSheet.kt:584` · `AiCopy.kt:192` ·
`AiProviders.kt` · `GeminiRequest.kt` · `GeminiResponse.kt` · `NvidiaRequest.kt` · `NvidiaResponse.kt` ·
`ui/utils/MarkdownPreview.kt` · `ui/projects/AiEditApplier.kt:27` · `AiKeyStore.kt:15,23` ·
`app/build.gradle.kts` · `rule.md` §3/§4/§7 · `00_LEVEL0_DECISION_RECORD.md` · `AI_INTEGRATION_ROADMAP.md:17`.

**External (checked 2026-10-02):**
- Anthropic, *Building Effective Agents* — https://www.anthropic.com/research/building-effective-agents
- Anthropic, *Effective Context Engineering for AI Agents* — https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
- Cognition, *Don't Build Multi-Agents* — https://cognition.com/blog/dont-build-multi-agents
- Cognition, *Multi-Agents: What's Actually Working* — https://x.com/walden_yan/article/2047054401341370639
- Manus, *Context Engineering for AI Agents* — https://medium.com/@peakji/context-engineering-for-ai-agents-lessons-from-building-manus-71883f0a67f2
- SWE-agent, NeurIPS 2024, ablation Table 3 — https://arxiv.org/abs/2405.15793
- *Inside the Scaffold: A Source-Code Taxonomy of Coding Agent Architectures* — https://arxiv.org/html/2604.03515v1
- Cline context-management constants — https://deepwiki.com/cline/cline/3.5-context-management
- NVIDIA GLM-5.3 model card — https://docs.api.nvidia.com/nim/reference/z-ai-glm-5-3
- NVIDIA GLM-5.3 request reference — https://docs.api.nvidia.com/nim/reference/z-ai-glm-5-3-infer
- Arena Agent Mode / leaderboard (signal set) — https://arena.ai/agent · https://arena.ai/leaderboard

---

**Historical status as of 2026-10-02 (before the owner command):** this was a research record only; it authorized no production/test source, dependency, permission, runtime, SDK, endpoint, DataStore key, or phase. At that point the next free phase number was 83.

**Current status — 2026-10-03:** Level 6 (Phase 83) was merged as PR #109. Levels 7+8 (Phases 84+85) were merged by authorized PR #110 to `main` @ `32e4a5f87a9f73874a4de17967187445c49a2461`; Build APK `37084800939` is green. The owner authorized Level 9; Phase 86 implements the bounded D6 exception on `arena/01a1004c-codec`, with local host pre-validation 72/72 across eight selected classes; Build APK run `37106180481` exposed three existing Level 8 regressions, now fixed locally; rerun pending, and no PR/merge authorization. Level 10–14 remain proposed/unauthorized; device acceptance remains postponed to Level 12.
