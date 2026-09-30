# CodeC AI integration — staged product and implementation plan

**Status: discussion / planning only. No AI implementation is approved by this document.**

This plan records the owner's requested direction: a project-wide, agentic coding assistant; user-supplied API credentials and multiple provider/model choices; an optional offline model recommended only after checking the device; and a way to undo agent edits. It deliberately starts with a small, useful slice and adds autonomy only after the previous level is dependable.

## The intended destination

A user selects a CodeC project and asks an agent to inspect, explain, change, and verify code across that project. The agent can use CodeC's editor, project files, run/output and terminal through narrowly defined CodeC tools. It can use a selected cloud provider/model or, optionally, a supported on-device model. Users can review and undo file changes. Commands and other side effects remain separately controlled.

"Whole project" means the agent is allowed to reason about the selected project, not that every file must be uploaded on every request. CodeC should discover relevant context, respect exclusions, and disclose what leaves the device before a cloud request.

## Repository baseline checked for this plan

This plan was written after rechecking the current checkout (HEAD `69c4b53`, merge PR #94; working tree clean at review). CodeC is a native Kotlin/Jetpack Compose Android IDE with project/file-tree management, an editor, terminal, run/output and diagnostics, plus Git features. `EditorSidePanel.kt` has a reserved, currently unwired rail slot and `strings.xml` calls it a future AI feature. There is no provider client, LLM runtime, agent tool loop, agent approval flow, or AI-key management in the app today. Gemini references in packaging metadata and `.env.example` are not a working integration.

Relevant existing contracts and constraints:

- [`DATA_AND_PRIVACY.md`](../DATA_AND_PRIVACY.md): no telemetry; outbound traffic is user-triggered; project data is private by default; it documents the existing file-access permissions and backup scope.
- [`README.md`](../../README.md): describes the current project, terminal, run and install behavior.
- [`rule.md`](../../rule.md): implementation/CI and owner approval rules. These AI pages do not authorize implementation, a phase, a PR, or a merge.
- The editor has both on-disk files and live editor state. Before agent reads/writes, unsaved buffers and external file changes need an explicit conflict policy.
- CodeC's Android app sandbox is not an agent sandbox. A process running with CodeC's access can still affect CodeC-accessible projects and app data.

## Staged pages

| Level | Page | Goal | Relative complexity |
|---|---|---|---|
| 0 | [Product boundaries and shared foundations](00_PRODUCT_AND_FOUNDATIONS.md) | Agree on permissions, session model, and the stable provider/tool boundaries before implementation | Planning prerequisite |
| 1 | [One-provider, read-only helper](01_READ_ONLY_API_HELPER.md) | BYOK API request for selected code or a run diagnostic; no file edits or shell tools | Lowest useful AI slice |
| 2 | [Whole-project context](02_WHOLE_PROJECT_CONTEXT.md) | Give the agent useful project-wide understanding without sending the entire tree by default | Medium |
| 3 | [Proposed edits and task undo](03_EDIT_REVIEW_AND_UNDO.md) | Stage file changes, review diffs, apply/reject, and roll back agent edits | Medium-high |
| 4 | [Agent tools and verified run loop](04_AGENT_TOOLS_AND_RUN_LOOP.md) | Add bounded file/search/run tools and explicit command approvals | High; security-sensitive |
| 5 | [Multiple API providers and model collaboration](05_PROVIDERS_AND_MODEL_COLLABORATION.md) | Add provider/model choice, then optional second-model review | High; cost and privacy-sensitive |
| 6 | [Optional on-device model](06_OPTIONAL_ON_DEVICE_MODEL.md) | Check device suitability, acquire a model only on request, and support offline inference | High; native/runtime and device matrix work |
| 7 | [Higher autonomy and evaluation](07_AUTONOMY_AND_EVALUATION.md) | Only after the guarded loop works: bounded task autonomy, specialist roles, measurable quality | Highest; defer until evidence supports it |

The levels are an order of risk and dependency, not a promise that implementation is easy. Each page says what the user gets, what it depends on, what can go wrong, and what should remain out of scope at that level.

## Research references

These are implementation references, not endorsements to copy code or a decision to adopt their dependencies. Check the actual repository license, maintenance, Android compatibility, model/provider terms, and dependency footprint before reuse.

- [AndCode](https://github.com/yuga-hashimoto/and-code) — a close Android UX/runtime comparison: project/workspace UI, agent sessions, approvals, diffs, terminal and local/remote agent runtime options. Its approach wraps existing CLI agents in a Linux environment or connects remotely; that is not the same as a native Kotlin agent integrated with CodeC's editor and runners.
- [Cline](https://github.com/cline/cline) — plan/act workflow, tool approval, provider configuration, checkpoints/undo, and agent-core boundaries. Useful for interaction and state-machine study; its main ecosystem is not Android/Kotlin.
- [Aider](https://github.com/Aider-AI/aider) — repository map/context selection and reviewable edits; useful for whole-project context without naively attaching every file. GitHub currently shows its latest listed commit as May 22, 2026, so inspect maintenance before adopting.
- [OpenHands](https://github.com/OpenHands/OpenHands) — broad software-agent runtime and workspace/sandbox concepts. Its operational footprint is much larger than a mobile IDE feature; study boundaries and event flows rather than attempting to embed the platform wholesale.
- [Pi agent toolkit](https://github.com/earendil-works/pi) — separates multi-provider model API, agent loop/tool state, and coding-agent product. Useful architecture reference, but it is TypeScript and not a drop-in Kotlin library.
- [llama.cpp](https://github.com/ggml-org/llama.cpp) — native inference runtime and GGUF model ecosystem for flexible local-model experimentation.
- [Google LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) — Android/Kotlin-oriented edge inference option with stated tool-use and accelerator support; verify model/runtime/device support for the exact intended release before selecting it.
- [DroidAgentKit](https://github.com/iVamsi/droid-agent-kit) — example of exposing structured, permissioned Android-development operations instead of handing an agent an unrestricted shell. It targets host-side Android development tools, not CodeC's in-app project sandbox.

## Recommendation

Approve the product boundaries first, then evaluate Level 1 as the smallest real user value. Do not begin by adding an autonomous shell agent or bundling a local model. Keep the UI entry point and architecture extensible so the later levels do not require replacing the first one.
