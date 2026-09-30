# CodeC AI coding-agent integration — research dossier

**Date:** 2026-09-30
**Repository baseline:** `main` at `120460f7bcccb11d739aa6b887607954b7dad428` (PR #100; docs tree reorganized)
**Status:** research and product planning only; no AI app implementation authorized.
**Companion plan:** [`docs/roadmaps/AI_INTEGRATION_ROADMAP.md`](../roadmaps/AI_INTEGRATION_ROADMAP.md)

## Executive finding

CodeC can practically grow into a project-scoped coding agent because it already owns the editor, project tree, file persistence, compile/run pipeline, output/diagnostics, terminal, and a deliberately reserved editor-side-panel slot. The missing product is the AI layer: provider/model access, context selection, an agent loop, constrained tools, approvals, change review, and agent-task rollback. There is no functioning AI provider or local inference runtime in the app today.

The feasible route is incremental: first validate one user-triggered, read-only API request; then add project context; then diff-reviewed edits and task rollback; only then allow CodeC tools and approved run/terminal actions. Multiple providers/models and optional offline inference should plug into that same policy boundary later. A model's capability never determines its permission.

## 1. Repository recheck: facts to build on

These are current-main observations, not assumptions from the older `PROJECT_ANALYSIS.md` snapshot. The snapshot itself is dated 2026-08-19 and should not be treated as an authoritative inventory of the September 30 app.

| Question | Current evidence in the repository | Why it matters |
|---|---|---|
| Is there a place in the UI? | `app/src/main/java/com/codeci/ide/ui/editor/SidePanelPlan.kt` reserves the fifth rail position; `EditorSidePanel.kt` renders it disabled/unwired. The source comment records the owner's intent. | Natural future entry point; preserve the current rail placement and do not imply it already works. |
| Is AI already integrated? | Searches of current-main app source find no LLM client, provider adapter, inference engine, agent orchestrator, tool approval system, or AI session store. `metadata.json` lists a server-side Gemini capability and `.env.example` mentions `GEMINI_API_KEY`, but these are packaging/example references, not app functionality. | Treat current Gemini metadata as stale/unimplemented until proven otherwise; do not design around a phantom integration. |
| Does CodeC understand projects? | `ui/projects/ProjectManager.kt`, `FileTreeRepository.kt`, `EditorViewModel.kt`, project routes and the project/file screens already manage project roots, nested files, open tabs and save flows. | Project-wide scope is a realistic product direction, but AI context collection still needs to be designed. |
| Is there a live editor buffer? | `EditorViewModel.kt` maintains active `_codeText`, per-tab buffers and save/reload behavior; saving is explicit in the view model and autosave also exists in the editor flow. | AI reads must use the visible buffer or a verified saved revision; AI writes must detect concurrent user edits. |
| Is editor undo available? | `ui/editor/EditorUndoManager.kt` maintains per-tab undo/redo history for text edits. The current README documents per-tab undo/redo. | Reuse the editor's ordinary undo for one-file edits where appropriate, but it is not a durable multi-file task checkpoint. |
| Can it run code? | `ui/services/ExecutionRunner.kt` owns the non-interactive build/run process and timeout handling. `EditorViewModel.kt` connects it to the editor's output/diagnostics. | Prefer the existing runner for approved run requests; do not build a competing runner. |
| Is there a terminal? | `ui/viewmodels/TerminalViewModel.kt`, terminal session/PTY classes and the terminal tab provide an interactive Linux shell. `sendCommand` exists. | Powerful, but an agent command is not isolated merely because it runs in CodeC's Android process or terminal. |
| What does the app promise about privacy? | `docs/guides/DATA_AND_PRIVACY.md` documents no telemetry and user-triggered outbound connections, private project storage, external-folder consent, current credentials and backup scope. | A new cloud AI request changes the disclosure surface and must be added to the source-of-truth privacy guide if implemented. |
| What changed in the latest user experience? | Current `README.md` describes the first-run introduction/privacy acknowledgement and CodeC Arcade. PR #99 changed onboarding/starter behavior; PR #100 reorganized documentation. | Research and integration planning must reference the current app and new documentation paths, not older navigation or onboarding descriptions. |

### Current data and boundaries to inspect

- **Private project files:** `files/CodeC/projects/` is the normal CodeC project root (documented in `docs/guides/DATA_AND_PRIVACY.md`). Projects can also be selected/imported from shared storage. The agent's default workspace should be only the currently selected project, not all of app storage or all shared storage.
- **Editor state:** active buffer, tabs, dirty/save state and project/file route live through `EditorViewModel`; see methods around `openFile`, `saveFile`, `saveAllTabs`, project-file opening, and current run target. Source locations move; verify them against the exact branch before implementation.
- **Run evidence:** latest output, diagnostics and run lifecycle are exposed by the editor view-model/output path and `ExecutionRunner`; the Terminal's PTY is a different interactive surface.
- **Credentials:** current privacy docs say the GitHub token lives in DataStore and is excluded from backup. API-provider keys must not be mixed into prompts, project files, ordinary logs, crash reports, exported diagnostics or backup scope. A future key-storage decision needs a threat-model review; do not assume the existing GitHub-token storage is automatically an acceptable pattern for all providers.
- **External files:** `MANAGE_EXTERNAL_STORAGE` exists for user-approved whole-tree work/builds, with picker alternatives. A project agent should not silently widen the scope beyond the root the user chose.
- **Network:** INTERNET is already declared for existing features. That does not remove the requirement for explicit AI request consent, provider disclosure, context preview, and updated privacy wording.

## 2. CodeC-specific integration map: where each concern belongs

Use these paths as the initial research map; inspect current definitions and call sites again before any implementation.

| Concern | First places to read | Research/output to produce before coding |
|---|---|---|
| Entry point and panel geometry | `ui/editor/SidePanelPlan.kt`, `ui/components/EditorSidePanel.kt`, `ui/screens/EditorScreen.kt` | A panel behavior note preserving the reserved slot, existing side panel interaction, phone geometry, accessibility labels, and no-new-global-banner rule unless owner decides otherwise. |
| Project root, tree, exclusion | `ui/projects/ProjectManager.kt`, `FileTreeRepository.kt`, project tree/hub screens, `ui/projects/RepoHygiene.kt` and related ignore policy | A project-scope and file-exclusion table: selected root, hidden/generated files, `.gitignore`, symlinks, binary/large files, secret-like files, and how exclusions are previewed/overridden. |
| Active editor state and edit correctness | `ui/viewmodels/EditorViewModel.kt`, `ui/editor/EditorUndoManager.kt`, `ui/editor/sora/SoraEditorHost.kt`, editor tabs/drawer | A concurrency contract: dirty buffer handling, file revision checking, external edits, new/deleted files, selection/caret preservation, and undo behavior. |
| Run and compiler results | `ui/services/ExecutionRunner.kt`, `ui/viewmodels/EditorViewModel.kt`, `ui/components/OutputPanelView.kt`, output diagnostic parsers/policies | A run tool contract naming the existing runner, supported run configurations, stop/cancel, timeouts, returned diagnostics, and how the model sees bounded output. |
| Interactive terminal | `ui/viewmodels/TerminalViewModel.kt`, terminal session manager/PTY, `ShellEnvironment.kt`, terminal command entry paths | A separate terminal-risk decision. Document why arbitrary command execution is later/higher risk than Run and what approval, working-directory, timeout, output cap, and stop behavior would be required. |
| API credentials and privacy | `docs/guides/DATA_AND_PRIVACY.md`, `ui/settings/SettingsManager.kt`, credential/GitHub settings flows, `AndroidManifest.xml`, `ManifestPermissionsTest.kt`, backup rules | A data-flow diagram: device → provider, exact payload, key location, provider response/storage policy, local session history, logs, backup, export, and deletion. |
| Provider/model availability | New provider research, official provider API/tool-calling docs, network/error code patterns, app build/dependency policy | A provider capability matrix; do not assume a provider's chat endpoint supports tool calls, structured output, streaming, or model discovery in the same way. |
| Local inference | `app/build.gradle.kts`, `app/src/main/cpp/CMakeLists.txt`, current ABI filters, APK-weight checks, minSdk/NDK setup, model download/install conventions | A runtime spike report with exact model format, ABI support, app-size delta, download bytes, peak memory, latency, heat/battery, tool-use reliability and devices tested. |
| Evidence and historical decisions | `docs/phases/03-editor/`, `04-projects-files/`, `05-run-output-preview/`, `06-git-github/`, `12-ui-polish-program/`; `docs/journal/JOURNEY.md`; `rule.md`; `prompt.md` | A claim ledger with source paths and dates; mark shipped/current, historical, proposed, and owner decision separately. |

## 3. Open-source implementation research

Research checked on 2026-09-30. These are reference projects; an architecture pattern can be reused without importing its code. Before embedding code or dependencies, inspect the actual license file, current release/commit cadence, Android support, transitive/native dependency impact, model licenses, and provider terms.

| Project | What is useful for CodeC | Important difference / caution | Source |
|---|---|---|---|
| AndCode | Closest Android product comparison: project workspace, chat/session UX, streamed tool activity, diffs, approvals, local and remote runtime choices. | It presents existing CLI agents through a Linux/remote-agent integration. That is not equivalent to a Kotlin-native agent using CodeC's editor/run APIs. Its own docs distinguish its license from third-party CLI/runtime terms. | [GitHub: yuga-hashimoto/and-code](https://github.com/yuga-hashimoto/and-code) |
| Cline | Plan/act flow, per-tool approvals, provider selection, checkpoint/undo concepts, event timeline, sub-agent extension points. | Desktop/Node-oriented architecture; study interaction and agent states, not as a drop-in Android library. Its project evolves quickly; pin claims to the reviewed revision. | [GitHub: cline/cline](https://github.com/cline/cline) |
| Aider | Repository map, context selection, patch-based multi-file changes, Git-aware review practices. | Terminal-first pair-programming product; not CodeC's mobile UI/runtime. GitHub's current page showed last listed commit May 22, 2026; maintenance status should be checked again before adoption. | [GitHub: Aider-AI/aider](https://github.com/Aider-AI/aider) |
| OpenHands | Full software-agent loop, event/tool history, runtime/workspace isolation concepts, autonomous verification. | Much heavier server/container platform than an in-app Android feature. A local process with CodeC permissions is not equivalent to its isolated runtime. | [GitHub: OpenHands/OpenHands](https://github.com/OpenHands/OpenHands) |
| Pi | Clear separation between multi-provider model API, agent loop/tool state, and coding-agent shell. | TypeScript ecosystem; useful architecture reference, not a Kotlin dependency without an explicit port/bridge decision. | [GitHub: earendil-works/pi](https://github.com/earendil-works/pi) |
| DroidAgentKit | Structured, permissioned Android-development tools rather than giving a model raw shell access; useful tool allowlist/read-only/destructive hints concept. | Host-side Android build/device tooling, not an in-app IDE agent or a sandbox for CodeC projects. | [GitHub: iVamsi/droid-agent-kit](https://github.com/iVamsi/droid-agent-kit) |

### What to extract from these projects during a deeper code review

Do not stop at the README. For each candidate, inspect the actual files implementing:

- provider interface, provider/model capability descriptions, authentication storage, streaming parser and cancellation;
- agent state machine, tool-call schemas, approval/pause/resume behavior, iteration limits, event journal and recovery after process death;
- project-map/context collection, ignore handling, path normalization, diff generation, patch application and rollback/checkpoint handling;
- command execution boundary, cwd validation, environment variables/secrets, timeout, stdout/stderr limits, process cancellation and sandbox assumptions;
- tests for path traversal, malformed model output, concurrent edits, tool denial, network errors and interrupted work;
- dependency licenses and model/provider terms, separately from the main repository license.

A popularity count or curated list is discovery input, not evidence that a project is maintained, safe, Android-compatible, or licensed for the intended use.

## 4. Optional local-model runtime research

Two candidates merit an Android-focused evaluation; selecting one is a later decision, not a current commitment.

- **llama.cpp** ([repository](https://github.com/ggml-org/llama.cpp)): native C/C++ inference and broad GGUF quantized-model support. It offers flexibility but requires evaluating JNI/NDK integration, ABI coverage, model download/storage, runtime behavior, build time, APK packaging, and model-specific tool-call quality.
- **Google LiteRT-LM** ([repository](https://github.com/google-ai-edge/LiteRT-LM)): Kotlin-facing edge-inference project that advertises Android support, acceleration, and tool-use capabilities. Verify the exact stable API, eligible models, formats, Android/API level support, delegated hardware support, and whether those models can reliably follow CodeC's structured tool contract.

Do not infer that a phone can run a coding agent from its RAM label or NPU alone. Evaluate model weights plus context/KV-cache and runtime buffers, available memory while the IDE/terminal is open, generation speed, device thermal throttling, battery impact, and tool-call reliability. A small local model may be useful for explanation/read-only tasks even if it is not dependable for multi-file edits or shell use.

The capability check should give conservative outcomes (ready, possibly slow, insufficient storage, unsupported, or needs test), then allow an opt-in model download and a short real-device load/benchmark. No silent fallback from local to cloud is acceptable.

## 5. Recommended research method before an implementation phase

1. **Freeze the product scope in owner words.** Capture whole-project default, BYOK provider expectations, collaboration mode, approval requirements, offline optionality, and rollback promise. Mark open decisions rather than inventing them.
2. **Read current CodeC source and docs.** Start from the integration map above. For each app claim, note an exact path/symbol (and line number at the reviewed commit) so the claim can be rechecked. Use `docs/guides/DATA_AND_PRIVACY.md` and source tests as authorities over older summaries.
3. **Inspect source projects at pinned revisions.** Record repo URL, commit/tag, date, license file, Android fit, architecture files examined, dependency footprint, and at least one observed limitation. Don’t paste/copy implementation code without a license and clean-room review.
4. **Read primary platform/provider documentation.** For each API provider, research official API docs for auth, tool calls, stream format, context/token limits, rate limits, data-retention controls, pricing/billing, regional availability, and BYOK terms. For Android local inference, use official runtime docs/releases and actual target-device measurements.
5. **Build a requirements-to-evidence table.** Columns: CodeC requirement, candidate approach, evidence/source, known limitation, unresolved decision, acceptance test. Distinguish provider claims from test results.
6. **Prototype only the smallest uncertain technical risk.** For example, confirm a provider's streaming/tool-call shape with a harmless read-only request, or load one candidate local model on representative phones. No project writes or terminal use during a provider spike.
7. **Define tests and device matrix before expanding permissions.** Include API 24 baseline compatibility if retained, current ABI filters, low/mid/high RAM phones, poor/no network, interrupted tasks, dirty editor buffers, ignored/secret files, and tool-denial cases.
8. **Update the right documentation category.** Research evidence goes here under `docs/research/`; approved staged scope goes in `docs/roadmaps/`; only a started/approved implementation phase belongs under the appropriate `docs/phases/<category>/chat-phaseNN/`. Add links to `docs/README.md` and the current `docs/getting-started/NEXT_STEPS.md` without presenting a proposal as authorized work.

## 6. Product and security conclusions

- Keep the first AI request read-only and user-triggered; use it to validate provider/key/network/privacy handling.
- Whole-project scope should be a selected project root with selective file/range retrieval, visible context preview, ignore/exclusion policy, symlink/path boundary, and no background upload.
- A lead model plus an optional read-only second-model review is a practical later collaboration mode. Several models editing in parallel is not a first release requirement.
- Use CodeC-owned tools with validated arguments. The model must not decide its own permissions.
- Prefer the existing Run pipeline for builds/runs. Treat arbitrary terminal as a distinct elevated capability with explicit command preview, approval, timeout, cancel, bounded output and truthful result reporting.
- Existing per-tab undo is not multi-file agent rollback. A task checkpoint needs baseline capture, conflict detection, interruption recovery and a clear scope; terminal/package/Git/network side effects cannot be promised reversible.
- “On-device” improves data locality but does not make poor tool-use quality safe. Add local agent tools only after a local model passes the same action-safety and task evaluation as API models.
- Preserve CodeC's user-triggered network promise: no automatic AI requests, analytics, telemetry, provider fallback, background indexing, or model downloads without an explicit owner-approved privacy design.

## External sources reviewed

- [AndCode repository](https://github.com/yuga-hashimoto/and-code)
- [Cline repository](https://github.com/cline/cline)
- [Aider repository](https://github.com/Aider-AI/aider)
- [OpenHands repository](https://github.com/OpenHands/OpenHands)
- [Pi agent toolkit](https://github.com/earendil-works/pi)
- [llama.cpp repository](https://github.com/ggml-org/llama.cpp)
- [Google LiteRT-LM repository](https://github.com/google-ai-edge/LiteRT-LM)
- [DroidAgentKit repository](https://github.com/iVamsi/droid-agent-kit)

See the roadmap for the level-by-level implementation sequence and stop conditions. All external project claims should be rechecked at the start of any later implementation work.

## Addendum A — current-main recheck (2026-09-30, `1785b92`)

Rechecked against `main` @ `1785b92` (PR #101) when Level 0 started. **App source is unchanged since `120460f`**; the diff between them is only the AI planning docs. Every path in §1–§2 exists. The facts below were checked in code and were missing from, or understated in, the original dossier.

| # | Fact | Evidence | Consequence |
|---|---|---|---|
| A1 | Editor undo is in-memory only: 100 steps, 600 ms typing coalescing, `reset()` on open/reload, all stacks cleared on project switch and mode switch, dropped when a tab closes. Sora's own undo is disabled. | `ui/editor/EditorUndoManager.kt`; `ui/viewmodels/EditorViewModel.kt:470,1579,1667,1751,1937`; `ui/editor/sora/SoraEditorHost.kt:129` | Lost on process death or project switch; cannot back agent-task rollback (Level 3). |
| A2 | Autosave runs a short delay after every buffer change and flushes before Run. No file watcher or timestamp-based external-change detection was found. | `EditorViewModel.kt:378-392` | Agent writes would race autosave; conflict detection must be built (Level 3). |
| A3 | `ProjectSearch` is a pure, host-tested engine: stays inside the root, skips symlinks, 512 KB file cap, 200-hit cap, binary check. | `ui/editor/ProjectSearch.kt` | Reusable for Level 2 retrieval. |
| A4 | …but `isSearchable` deliberately includes `.env`, `.env.*` and `.npmrc` (`ProjectFilesPolicy.usefulConfig`). | `ui/editor/ProjectSearch.kt:159-172`; `ui/editor/ProjectFilesPolicy.kt` | Level 2 needs a separate, stricter AI deny list. |
| A5 | The GitHub token is stored in the plain settings DataStore; no Keystore or security-crypto code exists in the repo. | `ui/projects/GitCredentialsStore.kt` | No encrypted-storage pattern to reuse; see Level 0 decision D3. |
| A6 | Backup and device transfer include only `CodeC/projects` (pinned by `BackupRulesTest`). | `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | Anything AI-related inside a project (e.g. `.codec/`) would be backed up; anything outside is not. |
| A7 | Uncaught exceptions are written to `filesDir/crash-log.txt`. Feedback redaction covers GitHub token shapes, `Authorization` headers, `key=value` pairs and the stored Git token only. | `MainActivity.kt:151-159`; `ui/support/FeedbackDraft.kt:236-260` | A bare provider key in an error message would not be redacted. |
| A8 | OkHttp was removed in Phase 42.2; network code uses `HttpURLConnection` (the version catalog still lists `okhttp`). | `app/build.gradle.kts:269`; `gradle/libs.versions.toml` | An HTTP-client dependency is an owner decision. |
| A9 | Plain-HTTP traffic is allowed only to `127.0.0.1` and `localhost`. | `res/xml/network_security_config.xml` | Plain-HTTP LAN or custom endpoints would be blocked (Level 5). |
| A10 | `ExecutionRunner` defaults to a 30 s build and 10 s run timeout, with one live process. | `ui/services/ExecutionRunner.kt:44-45,89-110` | Constrains the Level 4 run loop. |
| A11 | minSdk 24, targetSdk 28, ABIs `arm64-v8a`/`armeabi-v7a`/`x86_64`/`x86`, NDK 27.2. | `app/build.gradle.kts:18-59` | Constrains the Level 6 runtime choice. |
| A12 | `GitDiscardEditors.reloadDiscardedFile` already reloads open editors after a file changes on disk. | `ui/projects/GitDiscardEditors.kt`; `EditorViewModel.kt:2737` | Existing pattern for refreshing tabs after agent apply/undo. |

Not verified: how folders opened through Android's picker flow into the editor (`MANAGE_EXTERNAL_STORAGE` appears only in `SettingsScreen.kt` and `ShellEnvironment.kt`). Moot for now, because Level 0 decision D5 limits scope to CodeC projects.

## Addendum B — Gemini provider research (2026-09-30)

Primary sources only; recheck before the Level 1 brief.

- **API surface.** The Interactions API (`POST /v1beta/interactions`, header `x-goog-api-key`) is GA as of June 2026 and recommended for new projects. `generateContent` / `streamGenerateContent` are "legacy" but "remain fully supported". Source: [Interactions API overview](https://ai.google.dev/gemini-api/docs/interactions-overview); [generateContent reference](https://ai.google.dev/api/generate-content).
- **Server-side storage.** The Interactions API defaults to `store=true`: retained 55 days on the paid tier and 1 day on the free tier. `store=false` opts out but disables `previous_interaction_id` and background execution. Source: Interactions overview, "Data storage and retention".
- **Data use** ([Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms), effective March 23, 2026). On unpaid quota, content and responses are used to improve Google products, and human reviewers may read them; the terms say not to submit sensitive or confidential information. On paid services, prompts are not used for product improvement, but they are logged for a limited period for abuse detection.
- **Use restrictions** (same terms). Users must be 18+; API clients must not be likely to be accessed by under-18s; the API is for developers "for professional or business purposes, not for consumer use". EEA/Switzerland/UK: only paid services for API clients made available to users there. **This needs an owner decision** (Level 0 record, O1).
- **Other.** Custom safety settings are not supported in the Interactions API. Model IDs listed on 2026-09-30 include `gemini-3.8-flash`, `gemini-3.5-flash-lite` and `gemini-2.5-flash`; the list changes often.
- **Key storage.** `androidx.security:security-crypto` (`EncryptedSharedPreferences`) is deprecated upstream (1.1.0). A Keystore AES-GCM key with ciphertext in app-private storage needs no new dependency.

Decisions derived from this research: [`docs/roadmaps/ai-integration/00_LEVEL0_DECISION_RECORD.md`](../roadmaps/ai-integration/00_LEVEL0_DECISION_RECORD.md).
