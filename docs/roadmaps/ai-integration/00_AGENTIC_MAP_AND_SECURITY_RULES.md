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
> Level 9 and its bounded D6 task-memory amendment shipped as [Phase 86](../../phases/03-editor/chat-phase86/README.md),
> **merged by the owner's explicit command in [PR #111](https://github.com/pabi277/CodeC/pull/111)
> to `main` @ `6838ea6cf72937766f4d92eb5e9729b71f86b9ee`**; post-merge Build APK run `37109573383`
> is green on that commit. Exactly three `client.stream(` sites remain
> (`AiViewModel.kt:1096, 1519, 1903` after Phase 88 moved two state types out of the file; they read
> `1117, 1540, 1924` on `c3771c5`, re-verified 2026-10-03, and `905, 1281, 1508` before Level 10 moved them). The owner has now authorized **Level 10** as
> [Phase 87](../../phases/03-editor/chat-phase87/README.md) — ✅ MERGED to `main` @ `c3771c5` via
> [PR #112](https://github.com/pabi277/CodeC/pull/112); all nine controls wired; **S9 held** (no
> ceiling moved); 1 594 host cases green on Kotlin 2.2.10.
> **Level 11** is ✅ MERGED as [Phase 88](../../phases/03-editor/chat-phase88/README.md) via
> [PR #113](https://github.com/pabi277/CodeC/pull/113) on the owner's *"Ok merge to main"*, after the owner's *"Complete level 11"* (2026-10-03; CI-green, Build APK `37132310296`). The merge commit is **`0fc2bfd88d99d80ca72d61347dea9b36a4b82381`** (first parent `c3771c5`, second parent `06c1433`; merged 16:21 UTC) and post-merge Build APK run [`37136523881`](https://github.com/pabi277/CodeC/actions/runs/37136523881) is green on it (release APK 7 177 180 B, debug 27 054 824 B, R8 mapping 71 186 679 B). **S3** held:
> no HTML interpreter, no image fetch, `https`-only links behind a confirm. **S1/S8** held: rows open to
> exactly the packed text and disclosure stays one tap away. **S9** held: no ceiling moved.
> **Level 12** is ✅ **IMPLEMENTED (2026-10-04) as [Phase 89](../../phases/03-editor/chat-phase89/README.md) on
> `arena/01a102bd-codec`** — the owner briefed it and then commanded *"Complete level 12"*. **S10 now has its test**
> (`AiLevel12InjectionTest`, 11 cases; the fixture driven through the loop), the numbers readout is in
> `AiMeasurements`/`AiMeasurePolicy` plus the `ui/performance/HeapProbe.kt` boundary sample (17 + 7 cases), the
> 60-run matrix is in `DEVICE_ROUND.md` (5 contract cases) and the device round is refreshed but **not run**
> (every row ⏳, owner-only). **40 new cases; 622/622 host cases green** in the sandbox harness; **no PR, no merge.**
> Owner's Level 12 answers: in-memory numbers-only readout
> (**D6**) · 3/3 pass bar · `gemini-3-flash-preview` + `nvidia/nemotron-3-super-120b-a12b` · one phase ·
> a failed row becomes its own fix phase and Level 12 repeats. Levels 13–14 remain unauthorized; formal device
> acceptance is postponed until the owner's Level 12 round.

---

## Level index

**Done / merged:** [0](00_PRODUCT_AND_FOUNDATIONS.md) · [1](01_READ_ONLY_API_HELPER.md) · [2](02_WHOLE_PROJECT_CONTEXT.md) · [3](03_EDIT_REVIEW_AND_UNDO.md) · [4](04_AGENT_TOOLS_AND_RUN_LOOP.md) · [5A](05_PROVIDERS_AND_MODEL_COLLABORATION.md) · [6 — baseline and measurement](06_AGENT_BASELINE_AND_MEASUREMENT.md) as [Phase 83](../../phases/03-editor/chat-phase83/README.md) (PR #109) · [7 — agent correctness](07_AGENT_CORRECTNESS.md) and [8 — full context/honest reads](08_FULL_CONTEXT_AND_HONEST_READS.md) as Phases 84+85 (PR #110, `main` @ `32e4a5f87a9f73874a4de17967187445c49a2461`; Build APK `37084800939` green).

**Merged:** [9 — task memory and planning](09_TASK_MEMORY_AND_PLANNING.md) as [Phase 86](../../phases/03-editor/chat-phase86/README.md) — merged by the owner's explicit command in [PR #111](https://github.com/pabi277/CodeC/pull/111) to `main` @ `6838ea6cf72937766f4d92eb5e9729b71f86b9ee`; post-merge Build APK run `37109573383` is green on that commit. Bounded D6 amendment; exactly three stream sites preserved.

**✅ MERGED (Phase 87, 2026-10-03):** [10 — agent controls and options](10_AGENT_CONTROLS_AND_OPTIONS.md) as [Phase 87](../../phases/03-editor/chat-phase87/README.md), merged via [PR #112](https://github.com/pabi277/CodeC/pull/112) to `main` @ `c3771c5` — nine bounded controls, no ceiling moved, three `client.stream(` sites intact. Owner decisions 2026-10-03: all nine controls, in the `AiHome` AI panel, backup provider manual-offer-only, answer detail defaulting to `normal`. **S9** governs the whole phase.

**✅ MERGED (Phase 88, 2026-10-03, via [PR #113](https://github.com/pabi277/CodeC/pull/113); owner: *"Complete level 11"*, then *"Ok merge to main"*; CI-green, Build APK `37132310296`):** [11 — agent phone presentation](11_AGENT_PHONE_PRESENTATION.md) as [Phase 88](../../phases/03-editor/chat-phase88/README.md) — fixes defects 9 and 10 under gates S3, S8 and S1. Owner decisions 2026-10-03: the full level in one phase (Markdown answers, one truthful progress line, full result on tap, disclosure collapsed and never removed) · `https` links behind a confirm dialog that shows the full URL, while `javascript:`, `data:` and plain `http` stay inert · read-window default stays 400. Implemented in one commit on `arena/01a101db-codec` and merged with PR #113; see the Phase 88 README's implementation and merge records.

**Proposed future levels, in dependency order (Level 12 is implemented as Phase 89 on the session branch; the owner's device round is still owed):**

| Level | Doc | Fixes | Gate |
|---|---|---|---|
| **12** | [Evaluation and acceptance](12_AGENT_EVALUATION_AND_ACCEPTANCE.md) · ✅ **IMPLEMENTED as [Phase 89](../../phases/03-editor/chat-phase89/README.md) on `arena/01a102bd-codec` (2026-10-04) — S10 test, numbers readout, 60-run matrix, refreshed device round; not merged; the owner's round is ⏳** | proof, not features | S10, S12 |

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
> **Phase 86 / Level 9 update — 2026-10-03: ✅ merged.** Defect **8 (no bounded task memory)** is
> fixed on `main`: [PR #111](https://github.com/pabi277/CodeC/pull/111) merged Phase 86 to
> `main` @ `6838ea6cf72937766f4d92eb5e9729b71f86b9ee`, and post-merge Build APK run `37109573383`
> is green on that commit. On the branch, local host pre-validation had passed 72/72 selected
> methods across eight classes, and run `37106180481` had found three Level 8 regressions that
> were fixed before run `37106545726` went green on `ddb75d3`.
> The owner approved the narrow D6 amendment. `AiTaskMemory` versions the admitted file cache by canonical
> path, effective range and content version, reconciles dirty/disk changes, stores filtered findings,
> decisions and plan under the no-backup app directory, and recites the plan at the end of disclosed
> requests. Raw conversation data remains ephemeral. See [Phase 86](../../phases/03-editor/chat-phase86/README.md).
>
> **Phase 87 / Level 10 update — 2026-10-03: ✅ merged.** The owner authorized Level 10 in chat;
> [PR #112](https://github.com/pabi277/CodeC/pull/112) merged Phase 87 to `main` @ `c3771c5`, and
> post-merge Build APK run `37122702615` is green on that commit. Defect **11 (blanket brevity)** was
> its headline target: before Phase 87, `AiContext.kt:474` ended `AGENT_ASK_SYSTEM_INSTRUCTION` with
> *"Keep answers short: they are read on a phone."*, inherited by `AGENT_EDIT_SYSTEM_INSTRUCTION`.
> That sentence is now chosen by `AiOptionsPolicy.detailSentence(answerDetail)` — brief / normal /
> thorough, default `normal` (today's wording, by owner decision) — and disclosed per request.
> Defect **10 (disclosure floods the timeline)** got the collapsed-not-removed treatment: REQUEST
> rows show a one-line recipient-and-size summary by default and reveal the identical full text on
> tap. The four-surface split and Markdown answers (defect 9) were Level 11 scope and are now
> implemented as [Phase 88](../../phases/03-editor/chat-phase88/README.md); its README also records that defect 10's
> `AiChatSheet.kt:584` citation below predates 87.5 and no longer matches the tree. All nine
> controls live in the `AiHome` panel, with **S9** asserting every ceiling.
> See [Phase 87](../../phases/03-editor/chat-phase87/README.md).

| # | Defect | Evidence | Effect |
|---|---|---|---|
| 1 | The UI preview is the model's memory | `AiViewModel.kt:893` `detail = outcome.text.take(MAX_STEP_DETAIL_CHARS)` (1 200); `AiAgentPrompt.renderStep` then `.take(8 000)` on an already-≤1 200 string | Nominal 8 000-char / 400-line result delivered as **1 200 chars ≈ 22 lines**, `AiToolRunner.CUT_NOTE` cut away. Primary cause of repeated reads. |
| 2 | Tool budget not enforced on every resume | `AiViewModel.kt:747-749` checks `blockModelTurn` only; `blockTool` lives only in `AiAgentPolicy.decide` | Malformed-parse (`:828`) and denied-only (`:881`) paths bypass the tool cap. |
| 3 | Counter mixes executions with refusals | `withToolCalls` at `:828,879,901`; label `AiCopy.kt:192` renders all of it as "reads" | *"49 of 24 reads"*: 49 blocks at `used = 0` → 24 executed + 25 refused; `withToolCalls` never clamps. |
| 4 | No final-answer stage | `stopAgent` sets `phase/error/notice/agentRun/agentSteps`, never `answer` | Last raw `<<<CODEC_TOOL …>>>` text stays on screen as the answer. |
| 5 | Parser discards valid calls | `AiToolProtocol.parse` returns `Malformed` early, dropping accumulated `calls` | Three good reads beside one malformed block are all lost. Direct cause of re-reads. |
| 6 | Context eviction is total | `KEEP_LAST_RESULTS = 4`; notice `(N earlier tool results were dropped to fit)` — no path, no range | Not restorable (contra Manus). Model re-reads what it had. |
| 7 | Prefix-only reader | `AiProjectReader.readCapped` has no offset; stops at `MAX_READ_CHARS` (24 000) | Lines past the first 24 000 chars unreachable at any `end`. |
| 8 | No bounded task memory | Baseline `AgentSession` held no cross-request cache, plan, or findings | Phase 86 adds a version-checked bounded cache + structured ledger/plan; local host pre-validation passed 72/72 across eight classes. Build APK run `37106180481` found three Level 8 refusal/pointer regressions; fixed, with run `37106545726` green on `ddb75d3`. |
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
| **S10** | **Prompt-injection resistance is a test, not a claim.** Injection fixtures must be refused and must not alter the agent's rules or reach a write or run. | **✅ Tested since Phase 89.1** ([record](../../phases/03-editor/chat-phase89/PART_89_1_S10_INJECTION_PROOF.md#implementation-2026-10-04)): `AiLevel12InjectionTest` drives the Level 6 fixture **through the loop** — a hostile answer that obeys the injection reaches only `AskRunApproval`, the runner refuses a validated `request_run`, no write tool exists, `.env` and the escaping symlink stay refused, and the data-not-instructions sentence survives memory re-injection. |
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
3. **Backup provider routing and bounded option set:** Level 10 — **authorized 2026-10-03**, briefed as [Phase 87](../../phases/03-editor/chat-phase87/README.md). The owner chose **manual offer only**: CodeC offers, the user taps, and a fresh preview discloses the new recipient. Automatic fallback is rejected (S8/D4).
4. **Level 11:** authorized 2026-10-03 for a brief, then implemented on the owner's *"Complete level 11"* as [Phase 88](../../phases/03-editor/chat-phase88/README.md) (CI-green, Build APK `37132310296`), then merged via [PR #113](https://github.com/pabi277/CodeC/pull/113) on the owner's *"Ok merge to main"* — merge commit `0fc2bfd` on `main`, post-merge Build APK `37136523881` green. **Level 12:** authorized 2026-10-04 for a brief, briefed as [Phase 89](../../phases/03-editor/chat-phase89/README.md), then **✅ implemented the same day on the owner's *"Complete level 12"*** — S10's test, the in-memory numbers readout, the 60-run matrix and the refreshed device round, all on `arena/01a102bd-codec`; **40 new cases, 622/622 host cases green, no PR and no merge**; the owner's 60 real runs and every device row remain ⏳. **Levels 13–14:** remain unauthorized.
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

**Current status — 2026-10-04:** **Level 12 is ✅ IMPLEMENTED as [Phase 89](../../phases/03-editor/chat-phase89/README.md) on `arena/01a102bd-codec`** — the S10 test (11 cases), the in-memory numbers readout (17 + 7 cases), the 60-run matrix (5 contract cases) and the refreshed device round (every row ⏳, owner-only); **622/622 host cases green**, no PR and no merge. The one genuinely missing regression row (S10) is now covered. Level 6 (Phase 83) was merged as PR #109. Levels 7+8 (Phases 84+85) were merged by authorized PR #110 to `main` @ `32e4a5f87a9f73874a4de17967187445c49a2461`; Build APK `37084800939` is green. **Level 9 (Phase 86) was merged by the owner's explicit command in [PR #111](https://github.com/pabi277/CodeC/pull/111) to `main` @ `6838ea6cf72937766f4d92eb5e9729b71f86b9ee`; post-merge Build APK run `37109573383` is green on that commit.** The owner authorized **Level 10** and then said *"Complete level 10"*; [Phase 87](../../phases/03-editor/chat-phase87/README.md) is ✅ MERGED to `main` @ `c3771c5` by the owner's explicit command via [PR #112](https://github.com/pabi277/CodeC/pull/112) — all nine controls wired end to end, **1 594 host cases green** on Kotlin 2.2.10, **S9 held** (no ceiling moved), three `client.stream(` sites intact. Owner decisions for it: all nine controls, in the `AiHome` AI panel, manual-only backup provider, answer detail defaulting to `normal`. **Level 11** is ✅ MERGED as [Phase 88](../../phases/03-editor/chat-phase88/README.md) via [PR #113](https://github.com/pabi277/CodeC/pull/113) (owner: *"Complete level 11"*, then *"Ok merge to main"*; CI-green, Build APK `37132310296`). The merge commit is **`0fc2bfd88d99d80ca72d61347dea9b36a4b82381`** (merged 2026-10-03 16:21 UTC; first parent `c3771c5`, second parent `06c1433`) and post-merge Build APK run `37136523881` is green on it (release APK 7 177 180 B, debug 27 054 824 B, R8 mapping 71 186 679 B). Levels 12–14 remain proposed/unauthorized; device acceptance remains postponed to Level 12.
