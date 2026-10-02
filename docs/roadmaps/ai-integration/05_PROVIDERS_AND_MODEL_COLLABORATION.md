# Level 5 — Multiple API providers and model collaboration

**Status 2026-10-02: 5A merged via PR #107 as [Phase 82B](../../phases/03-editor/chat-phase82/README.md), local 462/462; main CI 37006780729 green; formal device acceptance postponed until after optimization, not passed. 5B remains proposed and unauthorized.**

Current 5A slice: Gemini native + NVIDIA Build fixed OpenAI-compatible HTTPS/SSE endpoints, manual provider/editable model, dated capability rows with unknowns preserved, Test connection, separate AES-GCM key/terms slots and no silent fallback. NVIDIA Trial Terms restrict this to **internal development/testing/evaluation, not production**; required checkbox, own key only. HTTP 202 is pending/fixed failure, no hidden polling. CodeC's bounded text tools and reviewed Apply/Run permissions do not depend on capability flags. Rate retry and budgets are Phase 82 policies shared by all three original VM stream sites. No custom endpoint, second reviewer, native provider tools or live catalog in this slice. Device/model quality acceptance is [POSTPONED](../../phases/03-editor/chat-phase82/DEVICE_ROUND.md).

## User value

Users can configure their own API provider credentials, select a provider/model for a session, and optionally ask a second model to review the plan or proposed diff.

## Build in two steps

### 5A. Provider/model selection

- Keep the provider interface separate from the agent tools and session model.
- Support a small number of well-tested providers first; add custom OpenAI-compatible endpoints only with clear compatibility limitations.
- Track model capabilities explicitly: streaming, tool calling, structured output, context limits, and known provider constraints.
- Let users test the connection and select model manually. Live model catalogs can be incomplete or require provider-specific APIs; do not imply every key can list all models.
- Handle rate limits, retries, cancellation, cost/usage when available, and provider outages without switching providers/models silently.
- Protect user credentials and do not put them in prompts, project files, logs, exports, crash reports, or backups.

### 5B. Optional second-model review

Start with one lead model. After it creates a plan or diff, the user may ask another configured model to review that artifact for likely bugs, missed files, or unsafe changes. The reviewer is read-only. The lead summarizes both views and highlights disagreements; it does not hide or average them into false certainty.

Do not start with several agents writing different project files in parallel. Concurrent edits create merge conflicts and make rollback, tool permissions, and blame harder. A later multi-agent design can isolate workspaces and combine patch sets, but that is Level 7.

## Privacy, cost, and trust

Each model/provider call may send the same or additional project context to another organization. Before review, show which provider will receive which material. An optional review incurs additional latency and may incur additional API charges. Never send keys or provider credentials to another model.

Do not use a remote API proxy unless its operator, retention, credential handling, and disclosures are part of the product decision. BYOK does not mean the provider cannot process or retain request content; describe the provider boundary accurately.

## Research references

- Pi separates provider/model abstraction from agent execution and tools: [Pi](https://github.com/earendil-works/pi).
- Cline demonstrates provider/model configuration within one plan/act agent experience: [Cline](https://github.com/cline/cline).
- These repositories demonstrate patterns, not the need for a third-party orchestration framework in CodeC. A small native provider boundary may be easier to reason about than importing a desktop/Node/Python agent stack.

## Acceptance checks

- Switching models does not change tool permissions or project scope.
- Provider failures never cause a silent fallback that transmits data elsewhere.
- The review provider and context are disclosed before a second call.
- Keys remain private during configuration, requests, error handling, device backup, logs, and support export.
- Compare model quality and tool-call correctness on a fixed CodeC task set, not only marketing benchmarks.

> **Phase 82 delivery update (2026-10-02):** [PR #107](https://github.com/pabi277/CodeC/pull/107) merged at **4cb4151** after final-head push/PR CI **37005368058 / 37005372003** passed on **268ed24**. Main [Build APK **37006780729**](https://github.com/pabi277/CodeC/actions/runs/37006780729) **GREEN**, release **7,117,440 B**, debug **26,869,596 B**. Owner asked to retain [research](../../research/AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md) and postpone **formal device acceptance until after optimization — NOT PASSED**. Known screenshot issues remain open; optimization/default-model/native-tool/routing changes are not implemented. **5B/6+ remain unstarted.** Post-merge ledger is carried on the session branch, not a new authorization.
