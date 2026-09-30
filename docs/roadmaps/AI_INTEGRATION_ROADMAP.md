# CodeC AI integration — staged roadmap

**Status: discussion and planning only. No app implementation is authorized by this document.**

## Product direction supplied by the owner

Build toward an agent that works across the user's selected whole CodeC project; users provide credentials and choose among API providers/models; CodeC checks device capability before recommending an optional offline model; and users can undo agent file changes. Grow from easy, useful behavior to higher autonomy rather than attempting the whole vision in one release.

## Current-main baseline

Reviewed against **`main` @ `120460f7bcccb11d739aa6b887607954b7dad428` (PR #100)** after the September 30 documentation reorganization. CodeC is a native Kotlin/Compose Android IDE with projects/files, a multi-tab editor, terminal, run/output/diagnostics and Git/source control. The editor's fifth side-rail position is deliberately reserved and not wired. There is no working LLM provider client, inference runtime, agent loop, tool-approval flow, or AI session/key management. Gemini packaging metadata and `.env.example` comments are not a live integration.

The current editor already has per-tab text undo/redo (`ui/editor/EditorUndoManager.kt`). That is valuable for ordinary edits, but it is not a durable, multi-file, task-level agent rollback. CodeC's `ExecutionRunner` and terminal are real execution surfaces; neither is an isolation boundary against actions run with CodeC's permissions.

## Read the research first

The companion [research dossier](../research/AI_INTEGRATION_RESEARCH_20260930.md) is the evidence and repository map: it states what files to inspect for UI, project roots, editor buffers, undo, run output, terminal, privacy/credentials and local model runtime; gives a procedure for researching OSS candidates and provider docs; and separates checked repository facts from proposals. Keep it current before turning any level into an implementation brief.

## Staged pages: start small, then expand

| Level | Spec | User outcome | Depends on | Complexity / risk |
|---|---|---|---|---|
| 0 | [Product boundaries and shared foundations](ai-integration/00_PRODUCT_AND_FOUNDATIONS.md) | Decide project scope, consent, session record, key boundary and permissions before code | Owner decisions and source audit | Planning prerequisite |
| 1 | [One-provider read-only helper](ai-integration/01_READ_ONLY_API_HELPER.md) | Explain selected code or a run diagnostic via one user-triggered BYOK request | Level 0; no project crawling or write/run tools | Lowest useful slice; API/privacy validation |
| 2 | [Whole-project context](ai-integration/02_WHOLE_PROJECT_CONTEXT.md) | Answer questions across the selected project using relevant files, with visible context | Level 1; root/exclusion/dirty-buffer contract | Medium; data disclosure/context limits |
| 3 | [Reviewable edits and task undo](ai-integration/03_EDIT_REVIEW_AND_UNDO.md) | Propose multi-file diffs, apply after approval, undo the agent file change set | Level 2; conflict-safe project/editor APIs | Medium-high; preserve user work |
| 4 | [Bounded tools and verified run loop](ai-integration/04_AGENT_TOOLS_AND_RUN_LOOP.md) | Inspect → plan → approved edit → approved run → inspect output | Levels 1–3; tool policy and runner integration | High; commands and side effects |
| 5 | [Providers and model collaboration](ai-integration/05_PROVIDERS_AND_MODEL_COLLABORATION.md) | Choose among BYOK providers/models; optionally get a second, read-only review | Stable Level 1–4 contracts | High; provider compatibility, spend, extra data recipients |
| 6 | [Optional on-device inference](ai-integration/06_OPTIONAL_ON_DEVICE_MODEL.md) | User-opted offline model, recommended only after compatibility and device checks | Local runtime spike, model/device evaluation, same tool policy | High; NDK/native runtime, storage, memory, heat, reliability |
| 7 | [Higher autonomy and evaluation](ai-integration/07_AUTONOMY_AND_EVALUATION.md) | Consider bounded autonomous tasks and specialist roles only if evaluation justifies them | Guarded agent, rollback, isolation, task benchmark | Highest; explicitly defer |

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
