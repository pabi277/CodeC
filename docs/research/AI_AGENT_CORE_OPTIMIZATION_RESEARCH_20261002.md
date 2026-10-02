# CodeC AI agent-core optimization — research and owner handoff

> **Date:** 2026-10-02 · **Status:** RESEARCH / PROPOSED, not implemented.
> **Source audit:** `arena/01a0fbc2-codec` at `e0665cb888ed20b9ed7a1393be34a9073b1b642d` (app/test code unchanged from CI-tested `7b2ecac`).
> **Owner delivery decision:** document the discussion, postpone formal device acceptance until after optimization, and merge the current Phase 82/82B work. This is **not** a device pass and **not** authorization to implement the proposals below.
> [Phase 82 verification / merge ledger](../phases/03-editor/chat-phase82/README.md) · [deferred device checklist](../phases/03-editor/chat-phase82/DEVICE_ROUND.md) · [AI roadmap](../roadmaps/AI_INTEGRATION_ROADMAP.md).

## 1. Owner direction and discussion chronology

1. **Model preference:** “I want to use this Nvidia model” — `z-ai/glm-5.3`, with NVIDIA's OpenAI-compatible sample using `https://integrate.api.nvidia.com/v1`, `temperature=0.5`, `top_p=1`, `max_tokens=1024`, `stream=False`; the key was only the `###` placeholder. No credential was requested or used.
2. **Do not change the default merely to select it:** “I stopped you because if i can use it in the current model it's enough for me”. The current editable NVIDIA model field already accepts this ID. No model/default/request-parameter change was made. CodeC keeps streaming and the approved 32,768-token / 48,000-reply-character budget, not the sample's 1,024/non-streaming settings.
3. **Discussion only, not a code command:** “No code change just discussing” — the agent does not feel usable: repeated file fragments, internal tool syntax instead of understandable answers, plain formatting, little room for useful output, and low step limits.
4. **Desired reading behavior:** “Ok research about how to remove it's full restrictions for chunks to full file at a time even multiple file at a time” and “I want a fully agent vive not just a cheap api system”. Investigate full-file/batch context and a dependable task loop; do not equate the goal with unrestricted secret access or unattended destructive actions.
5. **Multiple APIs:** “Ok i understand a little but if i connect more ai APIs at one can i solve the rate limit, wrong context, better optimization?” The discussion distinguished backup-provider availability, optional second-model review, model routing and local context optimization. Connecting every model to every task does not fix missing context.
6. **Current execution instruction (verbatim):**

   > What ever we discussed add a research note in the project
   >
   > The device test is postponed, 1st i will make it optimized than device test
   >
   > Last merge it

This instruction authorizes **documentation and the current branch's merge**, with formal device acceptance explicitly deferred. It does not say that screenshot issues are fixed, that tests passed on a phone, or that Levels 5B/6/7 have started. The next optimization needs an agreed brief/start command.

## 2. Phone evidence — observed behavior, not an acceptance result

The owner supplied these screenshots during the discussion:

- `Screenshot_20261002_165505_CodeC IDE.jpg`
- `Screenshot_20261002_165539_CodeC IDE.jpg`
- `Screenshot_20261002_165548_CodeC IDE.jpg`

The originals are user attachments, not new repository assets. Do not copy screenshots/project text into Git or support exports merely to preserve this research. The text observations below are sufficient for the handoff.

**Task shown:** “can you Explain full main.js line by line and think i don't have much code knowledge”. The project map names eight code/text files. The visible provider/model is **NVIDIA Build / `nvidia/nemotron-3-super-120b-a12b`**, not GLM-5.3; do not attribute these particular observations to GLM.

**Observed:**

- Repeated identical `read_file` blocks, first requesting `js/js/main.js` lines 1–76 and later repeatedly reading `js/main.js` lines 1–22.
- Raw `<<<CODEC_TOOL name="read_file">>> ... <<<END_CODEC_TOOL>>>` markers, full system instructions, the task/map and tool results occupy the activity card.
- A malformed-block error and a refused placeholder path `relative/path.ext` occur; the model did not consistently follow the custom text protocol.
- Numerous refusals say the 24-call budget is exhausted, yet the headline shows **“9 of 12 steps · 49 of 24 reads · 0 of 2 runs”**.
- No satisfactory beginner-friendly, complete-file explanation is demonstrated by these screenshots.

The HTML-escaped markers in the pasted chat transcript are not by themselves proof that the Android app emitted HTML entities: the screenshots show literal angle-bracket markers. Tool-protocol handling and Markdown rendering are separate problems.

**Formal device acceptance is POSTPONED**, not passed. These are informal problem reports to carry into optimization. Do not mark the R/B/P/S matrix green or infer successful Keystore encryption, live GLM availability, tool correctness or model quality from CI.

## 3. Repository findings at the audited revision

Paths below are relative to `app/src/main/java/com/codeci/ide/ui/ai/`. Line references describe this audit revision; recheck symbols before editing.

| Finding | Evidence | Consequence / confidence |
|---|---|---|
| Full file is not the default tool payload | `AiTools.kt:200–203`, `AiToolLimits.MAX_READ_LINES = 400`, `MAX_RESULT_CHARS = 8_000`; `AiToolPolicy.validateRead` rejects longer ranges | Confirmed fixed line/result bounds. A 76-line request is already within the line cap; raising 400 alone cannot repair the reported repetition. |
| Reader can only return a prefix | `AiProjectFiles.kt:58`, `MAX_READ_CHARS = 24_000`; `AiProjectReader.kt:181–196`, `readCapped` stops after that many characters; file-size guard is 512 KiB | Confirmed deeper read bottleneck. Even changing tool-result limits would not expose the rest of a longer disk file through this reader. |
| UI preview is reused as model memory | `AiViewModel.kt:893`, `detail = outcome.text.take(AiAgentLimits.MAX_STEP_DETAIL_CHARS)`; `AiAgentLoop.kt:50`, 1,200 chars; `agentTurn` packs `agentSteps` | Confirmed: the result handed to subsequent model requests is already clipped to the timeline detail. The intended 8,000-char result is not preserved independently. |
| Older context falls out rapidly | `AiAgentLoop.kt:47`, `KEEP_LAST_RESULTS = 4`; `AiAgentPrompt.pack` keeps recent TOOL/DENIED/RUN_RESULT rows within a 24,000-char request budget | Confirmed last-four packing rather than a file working set or structured conversation history. Denials can displace useful source results. This can contribute to rereading; it does not prove the model's private reasoning. |
| Duplicate reads are instructed against, not enforced | `AiAgentPrompt.pack` tail says not to repeat a call; `AiAgentPolicy.decide` validates each request; `executeToolBatch` executes each allowed call | No task-level file-version/range deduplication cache in the inspected path. Identical calls can consume budget without adding information. |
| Counter includes more than successful reads | `AiViewModel.kt:828,879,901`, malformed/denied attempts also call `withToolCalls`; `AiCopy.kt:192`, all of `toolCalls` is labelled “reads” | Confirmed misleading accounting. Oversized batches/denied additions can push the displayed value over 24. **49/24 is not evidence of 49 successful file executions.** Audit enforcement/accounting per call, not just the headline. |
| Finalization is unreliable on a cap | `resumeAgentOrStop` checks model-turn/time budget; `AiAgentPolicy.decide` can stop on tool budget; `stopAgent` does not replace `state.answer`; DONE renders it | Confirmed route by which the latest raw tool text can remain the apparent final answer. There is no dedicated tool-disabled final-synthesis stage. |
| Technical disclosure overwhelms normal chat | `AiChatSheet.kt:555–598`, activity card loops through every row and renders REQUEST strings through `SentText` | Confirmed display of full request disclosures plus result previews. Keep truthful disclosure available, but separate it from the primary answer/progress presentation. |
| Answers have no Markdown interpretation | `AiParts.kt:55–58`, `Answer` is Compose `Text(text)` | Confirmed plain-text renderer, separate from tool parsing. Formatting alone will not fix tool loops or context loss. |
| Generic brevity can conflict with the task | `AiContext.kt:459–466`, `AGENT_ASK_SYSTEM_INSTRUCTION` ends “Keep answers short: they are read on a phone.” | Confirmed instruction. A future design should respect explicit detailed/beginner requests instead of applying a blanket short-answer preference. |
| Map path grouping can be misread | `AiRepoMap.build` shows a directory heading followed by already project-relative full paths | Source confirms representation; screenshot shows `js/js/main.js`. Ambiguous presentation is a plausible contributor, not a proven sole cause. Display unequivocal root-relative paths and keep containment checks. |
| Native model tools are not used today | `NvidiaRequest.body` sends system/user messages, `max_tokens`, `stream`; no `tools` definitions. `NvidiaResponse.parse` reads `delta.content`, not `delta.tool_calls` | Confirmed custom text-tool harness. Model selection does not automatically enable a model's native function-calling capability. |

Current task caps remain **12 model turns / 24 charged tool attempts / 2 separately approved runs / about 5 minutes**. Phase 82's larger answer budget fixes neither this context-memory design nor these observed agent UX problems. No production source or test is changed by this research note.

## 4. External research — checked 2026-10-02

These are evidence/patterns, not dependencies to install or code to copy.

| Reference | Checked finding | Limit on the inference |
|---|---|---|
| Cline tool reference [1](https://docs.cline.bot/tools-reference/all-cline-tools) | Current ClineCore exposes `read_files` for batch reads and separates codebase, execution and human-approval tools. | Shows a useful batch-tool pattern; not permission to import its SDK, bash tools or auto-approval policy into CodeC. |
| OpenAI Codex prompting guide [1](https://developers.openai.com/cookbook/examples/gpt-5/codex_prompting_guide) | Recommends planning independent reads and batching/parallelizing them; discusses tool-output truncation and preserving distinctions between progress and final answers. | Codex-specific API fields/prompts are not automatically compatible with NVIDIA/Gemini. The guide still uses context/output bounds, not unlimited reads. |
| OpenAI function-calling guide (primary page fetched) | [Function calling](https://developers.openai.com/api/docs/guides/function-calling): typed tool definitions → model call → local execution → call-linked tool output → subsequent answer/calls. | Architecture pattern only. OpenAI Responses/compaction endpoints cannot be assumed available at NVIDIA's Chat Completions endpoint. |
| Cline context compaction [1](https://docs.cline.bot/features/auto-compact) | Describes monitoring context pressure and summarizing task history; behavior/support varies by model. | Do not adopt “remembers everything” as a correctness guarantee. Summaries are lossy; exact source required for explanations/edits must remain recoverable. |
| Pi read/truncation implementation (primary source inspected) | [read.ts](https://raw.githubusercontent.com/badlogic/pi-mono/main/packages/coding-agent/src/core/tools/read.ts) and [truncate.ts](https://raw.githubusercontent.com/badlogic/pi-mono/main/packages/coding-agent/src/core/tools/truncate.ts): structured read tool, 2,000-line / 50 KiB default bounds, completeness/continuation notices and separate rendering. | A mature agent also uses bounds. Learn the behavior, not its Node/TypeScript runtime or larger permissions. No source copied. |
| NVIDIA GLM-5.3 model card [1](https://docs.api.nvidia.com/nim/reference/z-ai-glm-5-3) | Documents 1,048,576-token context, reasoning and OpenAI-compatible tool calls; reasoning is separate from the answer. Hosted trial terms still govern service use. | A model card is not proof of this user's entitlement, actual endpoint limits, speed or successful native-tool behavior. Validate on the chosen hosted endpoint before implementation claims. |
| NVIDIA GLM request reference / Build sample (primary pages fetched) | [Request reference](https://docs.api.nvidia.com/nim/reference/z-ai-glm-5-3-infer) documents SSE deltas/`[DONE]`, `max_tokens >= 1`, defaults 1,024 / temperature 0.5 / top-p 1; [Build example](https://build.nvidia.com/z-ai/glm-5-3) uses the exact requested ID. | No upper output ceiling was established by this reference. Do not copy a third-party “944K output” figure or another host's 128K limit into NVIDIA capabilities. |
| Google function calling [1](https://ai.google.dev/gemini-api/docs/function-calling) | Documents parallel/compositional function calls and carrying call/results in conversation state. | Verify the exact Gemini model and CodeC's selected API route; do not silently migrate to a new stateful API or discard required protocol state. |

See also [NVIDIA trial/quota research](NVIDIA_API_RESEARCH_20261002.md). A large model context is not unlimited free quota. NVIDIA Build remains **internal development/testing/evaluation only, not production** under the existing terms gate.

## 5. Proposed full-file and batch-reader design

**Goal:** normal source files are read completely once; related files can be acquired together; full accepted results become the agent's working context, not a UI excerpt.

### 5.1 Default full-file read

- Offer a full-file mode without mandatory `start/end`. Range reads remain an explicit, useful fallback for genuinely large inputs or targeted investigation.
- Replace the agent's prefix-only loader, not merely the 400-line constant. Preserve UTF-8 handling, cancellation and dirty-buffer precedence; no API-26-only `java.nio.file` shortcuts on minSdk 24.
- Return path, source version, actual completeness, useful line metadata and full accepted content. Never label a prefix as “the full file”.
- Evaluate byte/memory and context budgets before committing a large request. For an oversized file, disclose why it cannot fit and read meaningful ranges/symbols; do not exhaust RAM or silently truncate.

### 5.2 Multi-file batch

Conceptual tool, **not an implemented schema**:

```text
read_files(paths = ["js/main.js", "js/storage.js", "js/games/snake.js"])
```

Validate every path individually. Return a separate result/completeness state per file. Independent local reads may use bounded concurrency, with Stop checked throughout and stable result ordering. This is **not parallel AI agents** and does not require multiple provider requests.

A batch must not turn a dozen file operations into an unaccounted unlimited action. Track actual files/bytes/context use separately from model turns, successes, reused results and refused attempts.

### 5.3 Task-local file working set

- Keep full source/results separate from timeline summaries; UI collapsing/truncation must not alter model input.
- Cache by canonical admitted path plus content/version identity. A repeated unchanged read reuses content; after a live-buffer or disk change, invalidate/recheck it.
- Track what each model turn actually received. A cached file that fell out of context cannot be treated as still visible to the model merely because the app retains it.
- Prefer the requested target and relevant dependencies over indiscriminate whole-project uploads. A small set of files can travel together when it fits.
- Session data stays in memory under D6. Saving task history/context across restarts or writing “memory bank” files into a project requires a separate explicit decision.

## 6. Proposed agent-core behavior

### 6.1 Model-aware context, not arbitrary micro-chunks

Available input room must account for the verified model/endpoint window, instructions/tool schemas, task history and an answer/reasoning reserve. Use a conservative token estimate or supported tokenizer/usage mechanism; characters are not exact tokens. Unknown model IDs remain unknown until researched/tested.

Do not blindly set every model to a million-token request because GLM's card advertises that window. Account quota, latency, payload limits, phone memory and user cost also matter. Compact old narrative/history while retaining exact target source needed for a current line-by-line explanation or patch baseline. Compaction may itself require a disclosed/budgeted model call.

### 6.2 Structured tools and conversation state

Evaluate native function calling where the selected model **and hosted API route** support it. Use a provider-neutral typed tool/session representation with provider-specific serialization. Aggregate streamed name/argument fragments completely, validate before execution, preserve call/result identity and keep reasoning text out of the visible answer and executable tool data.

A strict structured fallback can remain for unsupported models, but malformed/escaped text must not be “repaired” into new permissions. Do not execute arbitrary prose, HTML entities or code fences as tool actions.

**Approval/design change needed:** current D4 previews the exact system/user strings and current clients are stateless two-string requests. Native tool definitions, assistant/tool-role history or a different API route change that wire contract. Agree the new exact payload/recipient disclosure before coding; keep request inspection truthful and available even if collapsed. Maintain the three VM network entry-point pins unless the owner separately authorizes a for-cause revision.

### 6.3 Progress, budgets and a real finish

- Keep a concise task plan and progress/result ledger, not hidden chain-of-thought or fake completion claims.
- Detect exact duplicate reads and repeated denials/malformed calls with no new information. Reuse/recover context, revise the action or explain the blockage instead of spending the whole budget on repetition.
- Distinguish requested/refused/actually executed/reused operations. Never display a single mixed value as “49 of 24 reads”. Check execution permission/resources at each call.
- Revisit the old 12/24/5-minute caps through an owner-approved brief; do not interpret the research request as removing them now. A read-only extension/resume option is preferable to endless autonomous activity. Run count/approval does not increase just because file reading improves.
- Reserve a final-synthesis path that disables further tools when appropriate. If data is incomplete, explain what was read, what is missing and what can still be concluded. Do not leave raw internal tool requests as Copy answer.

### 6.4 Phone presentation

Separate **answer**, **compact progress**, **tool activity** and **exact sent-request disclosure**. Render common Markdown headings/lists/code fences safely; no executable HTML/JavaScript, hidden remote-image fetches or automatic link/network effects. Keep technical details expandable and usable with accessibility/scrolling. Preserve the current look rather than adding broad unrelated polish.

Honor explicit detailed/beginner requests. For “explain the full file line by line”, concise mobile typography should not mean silently omitting most lines. Explain a long answer in clear sections; revisit agent-final-answer continuation deliberately rather than reusing proposal fragments unsafely.

## 7. Several APIs: what they can and cannot solve

| Goal | Can multiple APIs help? | Required design / caution |
|---|---|---|
| Provider 429 / availability | Another provider may have quota left | Quotas are provider/account/model specific; keys/models at one provider may share allowances. Respect Retry-After and terms. Never describe additional keys as a way to evade a provider's limit. |
| Missing/wrong context | Not inherently | Two models given the same truncated/stale/wrong file can both be wrong. Fix file/context/session handling first. |
| Better answer/patch quality | Optional read-only review may catch errors | It adds another data recipient, latency and possible cost; agreement is not proof. Disclose the exact artifact/context and provider before sending. |
| Speed/cost optimization | Task-appropriate routing may help | Sending every task to every model adds calls; measure quality, time, cost and quota on the same tasks. Local batching/caching can remove unnecessary calls without a second model. |

**Recommended future pattern:** one lead model; an explicitly approved backup provider; an optional separately triggered read-only reviewer. Not several models independently writing the same project. A backup switch needs fresh recipient/context approval, not a silent replay of consent granted to another provider. It must not reset task tool/run budgets or duplicate prior file changes.

**Current implementation:** manual Gemini/NVIDIA selection only, exactly one visible identical-request rate retry, no automatic routing/fallback or reviewer. NVIDIA GLM-5.3 can be entered manually now; model choice neither enables native tools nor fixes clipped tool context. Level **5B** review/routing expansion and Levels **6/7** remain research/future-only, not started by this merge.

## 8. Safety boundaries to retain

The goal is to remove unnecessarily tiny context restrictions, **not** all protections:

- Selected-project containment; no path escape, symlink traversal or secrets/credential files. Keep AI's own D5 filter, never reuse `ProjectSearch.isSearchable`.
- User-reviewed Apply through `AiEditApplier`, conflict-safe undo and no direct model writes (D1).
- Each RUN action separately approved; no unattended terminal, package or Git tools added by this research.
- Frozen actual provider/model/payload for a request and its one retry; no silent provider switching (D4).
- Task/chat/file-context data in memory only; existing bounded undo journal unchanged (D6).
- Stop, honest resource/cost limits, fail-closed tool validation, encrypted BYOK and support/backup redaction.
- No promised unlimited trial quota, zero retention, live-model quality or production NVIDIA entitlement.

## 9. Recommended next work order — proposal, not a started phase

1. Agree a full-file/batch-context and agent-reliability brief using this evidence; prioritize this before second-model review/offline AI/higher autonomy.
2. Separate full tool/session data from UI previews; replace prefix-only agent reads and add complete-file/batch/version accounting.
3. Fix duplicate/progress handling, budget counters, unambiguous root-relative paths and final synthesis.
4. Evaluate typed/native tools and the required D4/client-state changes on the selected endpoints; do not assume OpenAI/Codex-specific APIs work on NVIDIA.
5. Improve the answer/progress/request-inspection presentation; preserve existing UI/type/motion and minSdk constraints.
6. Run genuine CI and the **postponed owner device round after optimization**, including the screenshot task, before claiming acceptance.
7. Only if measured evidence justifies it, separately brief optional backup routing/Level 5B review; Levels 6/7 remain future decisions.

No new dependency, permission, runtime, SDK, endpoint, DataStore setting or broad UI implementation is authorized by this record. The next free numbered phase is **83**; this note is not a Phase 83 start command or a finalized implementation brief.

## 10. Acceptance evidence for a future optimization

- A normal complete source file is visible to the model, including meaningful early/middle/end content, and can be explained line by line without repeatedly rereading its prefix.
- Multiple related files are read as a batch when they fit; every file has truthful complete/partial/refused status. Secret/outside-project entries in a mixed batch remain blocked.
- Enlarging/collapsing a UI preview does not change model context. Relevant content survives beyond four tool events or is explicitly recoverable after compaction.
- Repeated unchanged calls do not redo disk work or consume execution budget as new reads; changed dirty/disk versions cannot reuse stale content.
- Streamed native/fallback calls are validated before execution. Malformed/escaped instructions cannot trigger unauthorized operations.
- Denied attempts and actual executions are distinguishable; execution never exceeds the agreed budget. On Stop/limit/interruption, no delayed action occurs and the answer is intelligible.
- The screenshot task yields a clear beginner explanation rather than raw protocol/system prompts. Exact request inspection remains available, and no hidden reasoning text is shown as the answer.
- Apply/Undo/Run, path/secret filters, provider identity, feedback scrubbing and memory-only lifecycle regressions still pass.
- Compare actual selected model IDs on identical non-secret tasks; record refusals/format errors, quality, first-token/overall latency, repeated calls, token usage when provided and APK/device impact. No marketing score substitutes for this.

## 11. Delivery status

Phase 82/82B's code CI is already green (`36995145462` on `7b2ecac`; ledger-only head `e0665cb` also green, `36996946243`). This research/owner-decision update is documentation only. Its final-head CI, authorized PR/merge and main CI/APK facts belong in the [phase ledger](../phases/03-editor/chat-phase82/README.md), not in invented device results.

**Device acceptance: POSTPONED until after optimization. Known agent issues remain open. Research proposals are not implemented.**

## Merge / post-merge CI — PR #107

The owner explicitly commanded **“Last merge it”** while postponing formal device acceptance until after optimization. Both final-head checks passed on **268ed24e2123cd7a55aa6f535bbff594d5004407**: [push **37005368058**](https://github.com/pabi277/CodeC/actions/runs/37005368058) and [PR **37005372003**](https://github.com/pabi277/CodeC/actions/runs/37005372003). The guarded merge used that exact head; old PRs #42/#83 were not touched.

- **[PR #107](https://github.com/pabi277/CodeC/pull/107): MERGED** at **`4cb4151f1a59f9b719cc9bd64224ea9040c1a022`** on **2026-10-02**.
- **Main [Build APK 37006780729](https://github.com/pabi277/CodeC/actions/runs/37006780729): GREEN** on that merge SHA; real unit/screenshot tests, debug/lint, signed release and APK guards passed.
- **Release:** `CodeC-IDE-1.3.17-universal.apk` = **7,117,440 B**; **debug:** `CodeC-IDE-1.3.17-universal-debug.apk` = **26,869,596 B**. APK bytes are annotations, not compressed artifact ZIP sizes. Release manifest has no android:debuggable flag; no failure annotation, no release/tag published.
- Delta over Phase 81/main baseline **7,104,312 B**: **+13,128 B** (~12.8 KiB / 0.18%). Different build artifacts have their own measured sizes; earlier branch facts remain historical.
- **Formal device acceptance: POSTPONED until after optimization, NOT PASSED.** Informal Nemotron screenshot issues remain open; successful Keystore/live GLM access/model quality not inferred.
- **Research only:** full-file/batch/context/loop/native-tool/Markdown/multi-API proposals are not implemented; no default-model change, automatic provider fallback or new tool permission.
- Post-merge verification-ledger-only record is committed/pushed on **`arena/01a0fbc2-codec`**, not to main. Next: agree/start optimization, then refresh/run the postponed owner device matrix. Standing §3 still requires the next change's explicit merge command.
