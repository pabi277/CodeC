# CodeC AI integration — staged roadmap

**Status (2026-10-03): Levels 0–4 + Continue are merged; Level 5A shipped in Phase 82/82B (PR #107 merged; formal device acceptance POSTPONED); Level 6 was explicitly started and implemented test-only as Phase 83 (Build APK CI pending, not merged, zero production-source/behavior changes). Level 5B and Levels 7–14 remain PROPOSED and unauthorized. This roadmap grants no scope beyond the owner's explicit Level 6 start.**

> ### ⚠️ Level renumbering — 2026-10-02
>
> The owner directed that the proposed **agentic optimization** be inserted immediately after the
> completed levels, with the not-yet-started levels moved after it. Two specs therefore changed number.
> **Dated delivery notes elsewhere in `docs/` that say "Level 6", "Level 7", "6/7" or "5B/6+" were written
> before this change and mean the OLD numbering.** They are historical records and are deliberately not
> rewritten (`rule.md` §7). The mapping is:
>
> | Before | After | Spec |
> |---|---|---|
> | — | **6–12** | **New:** agentic optimization (baseline · correctness · honest reads · task memory · options · presentation · evaluation) |
> | Level 6 | **Level 13** | [Optional on-device model](ai-integration/13_OPTIONAL_ON_DEVICE_MODEL.md) |
> | Level 7 | **Level 14** | [Higher autonomy and evaluation](ai-integration/14_AUTONOMY_AND_EVALUATION.md) |
>
> Shared foundation for Levels 6–12 — defect register, security rules **S1–S12**, architecture, sources:
> [`ai-integration/00_AGENTIC_MAP_AND_SECURITY_RULES.md`](ai-integration/00_AGENTIC_MAP_AND_SECURITY_RULES.md).
> **All of 6–14 are PROPOSED. Nothing is implemented. No phase is started by this table.**
**Level 0 ✅ COMPLETE 2026-09-30:** owner decisions recorded in [`ai-integration/00_LEVEL0_DECISION_RECORD.md`](ai-integration/00_LEVEL0_DECISION_RECORD.md) — read-only first, Gemini BYOK, Keystore-encrypted key, preview every request, open project only, nothing saved. Also decided: 18+ and Google-terms confirmation at key setup, `HttpURLConnection` + stateless `streamGenerateContent` with `store:false` (no new dependency), and a pre-filled Flash model with Test connection. At that time, Level 1 still awaited the owner's explicit start; it was later shipped as Phases 76–77, device-passed and merged.
**Level 1 ✅ IMPLEMENTED & MERGED (2026-09-30 / 2026-10-01) as [Phase 76](../phases/03-editor/chat-phase76/README.md) + [Phase 77](../phases/03-editor/chat-phase77/README.md)** — read-only Gemini helper in the fifth side-panel rail slot (Phase 76, PR #102) and floating button + bottom chat sheet (Phase 77, PR #103), both device-verified.
**Level 2 ✅ IMPLEMENTED & MERGED (2026-10-01) as [Phase 78](../phases/03-editor/chat-phase78/README.md)** — whole-project context with secret/symlink/excluded-dir filtering and per-file preview (PR #104, `bd1aa06`, device-verified).
**Level 3 ✅ IMPLEMENTED & MERGED (2026-10-01 / 2026-10-02) as [Phase 79](../phases/03-editor/chat-phase79/README.md)** — reviewable multi-file edit/create/delete proposals, locally computed unified diffs with per-file checkboxes, baseline conflict checks, and a 1-task preimage undo journal in `noBackupFilesDir/ai/undo/` (owner D1/D6 amendments recorded in [`00_LEVEL0_DECISION_RECORD.md`](ai-integration/00_LEVEL0_DECISION_RECORD.md); PR #105, `df2c5d4`, device-verified).
**Level 4 ✅ IMPLEMENTED, DEVICE-PASSED & MERGED (2026-10-02) as [Phase 80](../phases/03-editor/chat-phase80/README.md)** — the model gets a bounded whole-project map and the read tools `list_files`/`search_project`/`read_file`, runs one bounded agent loop (owner's caps: ≤ 12 turns, ≤ 24 tool calls, ≤ 2 runs, ≤ 8 000 chars per result, ~5 min) with a visible timeline and Stop, and can *request* CodeC's own RUN ▶ action (each run approved, only the real exit codes/output come back) — no terminal typing, no installs, no Git (owner D4 amendment recorded in [`00_LEVEL0_DECISION_RECORD.md`](ai-integration/00_LEVEL0_DECISION_RECORD.md)); PR #106, `e089880`, post-merge CI `36972776771` green (owner: *"Mark the docs device pass and merge it"*).
**Level 1–2 UX fix ✅ IMPLEMENTED, DEVICE-PASSED & MERGED (2026-10-02) as [Phase 81](../phases/03-editor/chat-phase81/README.md)** — owner row *"Add a continue open on Gemini so i can continue after cut off"*: a cut-off answer now offers **Continue**, which builds the ordinary preview of the same request plus the last lines of the answer and a resume sentence (D4 unchanged: the user still taps Send), streams the rest under what is already on screen, and is bounded by ≤ 8 continuations and 64 000 characters per visible answer. Same PR #106, `e089880`, post-merge CI `36972776771` green. At that phase, Levels 5+ were unauthorized; the 5A-only authorization below supersedes that status.

**Rate-limit/budget fix + Level 5A 🚧 IMPLEMENTED (2026-10-02) as [Phase 82 / 82B](../phases/03-editor/chat-phase82/README.md)** — owner chose one visible cancelable automatic retry, 32,768 tokens / 48,000 reply chars globally and 64,000 Continue total; explicit manual provider/model seam with dated known/unknown capability rows and NVIDIA Build SSE BYOK. NVIDIA's independent encrypted key and Trial Terms consent are **internal evaluation/testing only, not production**; no silent fallback, shared key or async polling. Each preview/timeline names actual recipient/exact two strings; permissions unchanged. Local **462/462** in 38 classes; **Code CI 36995145462 GREEN on 7b2ecac; device acceptance POSTPONED**, no live quality claim. **5B/6+ not authorized.**

## Owner priority / scheduling update — 2026-10-02

Owner: “The device test is postponed, 1st i will make it optimized than device test … Last merge it”. **Merge the current Phase 82/82B delivery after final-head CI; do not record device acceptance.** Informal Nemotron screenshots show unresolved context/repetition/formatting issues. [Research](../research/AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md) proposes full-file/batch reads, separate full session data/UI previews, reliable counters/finalization, typed tools and readable progress. Agree/start that optimization before the formal phone round; no code is authorized by this research record.

One lead model with an approved backup and optional read-only reviewer is a future design, not today's behavior. Connecting multiple APIs does not repair truncated context, guarantee correctness or combine shared quotas. **5B/6+ remain unauthorized**; no silent provider switch, expanded tool permissions or automatic model migration.

## Product direction supplied by the owner

Build toward an agent that works across the user's selected whole CodeC project; users provide credentials and choose among API providers/models; CodeC checks device capability before recommending an optional offline model; and users can undo agent file changes. Grow from easy, useful behavior to higher autonomy rather than attempting the whole vision in one release.

## Current-main baseline

Latest baseline verified 2026-10-03: **`main` @ `741956647933562dd5056972e71473581dd21233`**; Phase 82/82B merged via PR #107 at `4cb4151`, docs follow-up PR #108 at this tip, and main Build APK CI `37047037300` is green. Formal device acceptance remains POSTPONED. Phase 83 / Level 6 is active on session branch `arena/01a0fded-codec`, test-only with zero production-source/behavior changes; its Build APK CI is pending and no PR/merge is authorized. The original pre-AI audit below is historical.

### Original pre-AI baseline

Reviewed against **`main` @ `120460f7bcccb11d739aa6b887607954b7dad428` (PR #100)** after the September 30 documentation reorganization. CodeC is a native Kotlin/Compose Android IDE with projects/files, a multi-tab editor, terminal, run/output/diagnostics and Git/source control. The editor's fifth side-rail position is deliberately reserved and not wired. There is no working LLM provider client, inference runtime, agent loop, tool-approval flow, or AI session/key management. Gemini packaging metadata and `.env.example` comments are not a live integration.

The current editor already has per-tab text undo/redo (`ui/editor/EditorUndoManager.kt`). That is valuable for ordinary edits, but it is not a durable, multi-file, task-level agent rollback. CodeC's `ExecutionRunner` and terminal are real execution surfaces; neither is an isolation boundary against actions run with CodeC's permissions.

## Read the research first

The companion [research dossier](../research/AI_INTEGRATION_RESEARCH_20260930.md) is the evidence and repository map: it states what files to inspect for UI, project roots, editor buffers, undo, run output, terminal, privacy/credentials and local model runtime; gives a procedure for researching OSS candidates and provider docs; and separates checked repository facts from proposals. Keep it current before turning any level into an implementation brief.

## Staged pages: start small, then expand

| Level | Spec | User outcome | Depends on | Complexity / risk |
|---|---|---|---|---|
| 0 | [Product boundaries and shared foundations](ai-integration/00_PRODUCT_AND_FOUNDATIONS.md) · [decision record](ai-integration/00_LEVEL0_DECISION_RECORD.md) | Decide project scope, consent, session record, key boundary and permissions before code | Owner decisions and source audit | Planning prerequisite |
| 1 | [One-provider read-only helper](ai-integration/01_READ_ONLY_API_HELPER.md) | Explain selected code or a run diagnostic via one user-triggered BYOK request | Level 0; no project crawling or write/run tools | Lowest useful slice; API/privacy validation |
| 2 | [Whole-project context](ai-integration/02_WHOLE_PROJECT_CONTEXT.md) | Answer questions across the selected project using relevant files, with visible context | Level 1; root/exclusion/dirty-buffer contract | Medium; data disclosure/context limits |
| 3 | [Reviewable edits and task undo](ai-integration/03_EDIT_REVIEW_AND_UNDO.md) | Propose multi-file diffs, apply after approval, undo the agent file change set | Level 2; conflict-safe project/editor APIs | Medium-high; preserve user work |
| 4 | [Bounded tools and verified run loop](ai-integration/04_AGENT_TOOLS_AND_RUN_LOOP.md) | Inspect → plan → approved edit → approved run → inspect output | Levels 1–3; tool policy and runner integration | High; commands and side effects |
| 5 | [Providers and model collaboration](ai-integration/05_PROVIDERS_AND_MODEL_COLLABORATION.md) | Choose among BYOK providers/models; optionally get a second, read-only review | Stable Level 1–4 contracts | High; provider compatibility, spend, extra data recipients |
| 6 | [Agent baseline and measurement](ai-integration/06_AGENT_BASELINE_AND_MEASUREMENT.md) · **IMPLEMENTED as Phase 83; CI pending** | A recorded baseline so every later change is a measured delta, not an impression | Level 4; the Level 6 fixtures | Lowest — **no behaviour change at all** |
| 7 | [Agent correctness](ai-integration/07_AGENT_CORRECTNESS.md) · **PROPOSED** | The six defects that make the agent unreliable today, fixed | Level 6 | Medium; pure policy, no wire-contract change |
| 8 | [Full context and honest reads](ai-integration/08_FULL_CONTEXT_AND_HONEST_READS.md) · **PROPOSED** | Any line reachable; batch reads; every read states its true coverage | Level 7 | Medium-high; reader offset on minSdk 24 |
| 9 | [Task memory and planning](ai-integration/09_TASK_MEMORY_AND_PLANNING.md) · **PROPOSED — needs a D6 amendment** | The missing third augmentation: working set, findings, recited plan | Level 8; **owner approval** | High; new on-disk store, S4/S5/S6 |
| 10 | [Agent controls and options](ai-integration/10_AGENT_CONTROLS_AND_OPTIONS.md) · **PROPOSED** | Nine bounded user controls; none can raise a permission (S9) | Level 9 | Medium; Settings surface |
| 11 | [Agent phone presentation](ai-integration/11_AGENT_PHONE_PRESENTATION.md) · **PROPOSED** | Formatted answers; four separate surfaces; disclosure collapsed not removed | Level 7 | Medium; Compose rendering, no new dependency |
| 12 | [Evaluation and acceptance](ai-integration/12_AGENT_EVALUATION_AND_ACCEPTANCE.md) · **PROPOSED** | Proof, then the postponed device round | Levels 7–11 | Medium; harness plus real device work |
| 13 | [Optional on-device inference](ai-integration/13_OPTIONAL_ON_DEVICE_MODEL.md) · *was Level 6* | User-opted offline model, recommended only after compatibility and device checks | **Level 12**; local runtime spike, same tool policy | High; NDK/native runtime, storage, memory, heat, reliability |
| 14 | [Higher autonomy and evaluation](ai-integration/14_AUTONOMY_AND_EVALUATION.md) · *was Level 7* | Consider bounded autonomous tasks and specialist roles only if evaluation justifies them | Guarded agent, rollback, isolation, Level 12 harness | Highest; explicitly defer |

The sequence expresses dependencies and risk—not a promise each item is easy or already scheduled. Level 1 can be evaluated without committing CodeC to later levels. Stop or narrow scope when evidence fails an acceptance check.

## Core architecture recommendation

Use one agent/session policy owned by CodeC and swap model backends behind it. Conceptually, one turn should travel through these boundaries:

**User task → CodeC session state → selected-project context builder → chosen provider/local runtime → proposed answer or typed tool request → CodeC scope/policy/approval gate → existing project/editor/run service → recorded result → next model turn or final user-facing summary.**

Keep the network/model layer separate from project operations. Expose typed, narrowly scoped tools; CodeC validates every request, project path, state and required approval. Provider/local model capability never grants permission. Start with one lead model; later allow one user-triggered, read-only reviewer. Do not start with agents editing files in parallel.

At Level 1, the context builder is just selected text/diagnostic and the only operation is a model request. At Level 2 it can retrieve project files. At Level 3 it emits a proposed patch but still cannot apply it without the user. Level 4 connects approved tools to the existing CodeC services. That expansion lets the provider and UI seams be tested before granting progressively riskier project capabilities.

For whole-project support, let the model reason over the selected project but retrieve relevant files/ranges rather than upload every file on every turn. Show provider and context before a cloud call. Treat code comments, README instructions, terminal output and downloaded files as untrusted data, not authority to change the agent's rules.

For changes, show local diffs and create a task-scoped file checkpoint. Detect dirty buffers and external edits. File rollback does not undo terminal/package/Git/network side effects; keep those actions separate and explicit. Use the existing Run pipeline for approved runs. A shell started with CodeC's app identity is not a hardened sandbox.

## Research and implementation artifacts by repository category

- Evidence and external implementation comparisons: [`docs/research/AI_INTEGRATION_RESEARCH_20260930.md`](../research/AI_INTEGRATION_RESEARCH_20260930.md).
- Approved design/sequence: this file plus the level specs in `docs/roadmaps/ai-integration/`.
- Current owner-approved work order: [`docs/getting-started/NEXT_STEPS.md`](../getting-started/NEXT_STEPS.md). Keep the AI work labeled discussion-only until the owner chooses and starts a level.
- Actual implementation records, after authorization: the appropriate topic under `docs/phases/<category>/chat-phaseNN/`, following [`docs/getting-started/HOW_TO_CREATE_A_PHASE.md`](../getting-started/HOW_TO_CREATE_A_PHASE.md).
- Master navigation: [`docs/README.md`](../README.md).

## Stop conditions

No AI endpoint is called before a user starts it. No cloud context is sent without clear provider/context disclosure. No API key enters prompts, logs, exports, crash reports, project files or unreviewed backup scope. No file change is applied without review at initial edit levels. No terminal command, package install or Git/network side effect is hidden under “undo.” No local-to-cloud fallback or model download occurs silently. Do not update privacy/product claims until implemented behavior and tests support them.

> **Phase 82 delivery update (2026-10-02):** Code CI **36995145462 / 7b2ecac** and ledger CI **36996946243 / e0665cb** green. Owner now directs documentation of [agent-core research](../research/AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md), **formal device acceptance POSTPONED until after optimization**, and **“Last merge it”** for this current delivery. No device pass or optimization/native-tool/routing implementation inferred. Current merge authorized after final-head checks; actual PR/merge/main CI/APK facts belong in the phase ledger. **5B/6+ remain unstarted.**
