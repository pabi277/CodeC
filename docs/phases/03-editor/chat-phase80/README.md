# Phase 80 — AI Level 4: whole-project map, bounded tools, and an approved run loop

> **Status: ✅ DEVICE-PASSED 2026-10-02 — merge in progress** on
> `arena/01a0f9a5-codec` (owner, in chat: *"Mark the docs device pass and merge
> it"*; the round is recorded in [`DEVICE_ROUND.md`](DEVICE_ROUND.md)). Branch CI
> `36941118847` ✅ GREEN on `c5dd30a`; owner authorized the phase with
> *"Start Phase 80 — full Level 4"*, then four explicit scope answers:
> `task_preview` / `run_only` / `both_flows` / `looser` caps.
>
> Parts:
> - [80.1 Whole-project map and the bounded tool surface](PART_80_1_REPO_MAP_AND_TOOLS.md)
> - [80.2 The agent loop: validated calls, results, caps, and Stop](PART_80_2_AGENT_LOOP.md)
> - [80.3 The approved run loop (CodeC's own RUN ▶ pipeline)](PART_80_3_APPROVED_RUN_LOOP.md)
> - [80.4 The UI: one task preview, an activity timeline, and two approval cards](PART_80_4_UI_TIMELINE_AND_APPROVALS.md)
>
> Roadmap: [`docs/roadmaps/ai-integration/04_AGENT_TOOLS_AND_RUN_LOOP.md`](../../../roadmaps/ai-integration/04_AGENT_TOOLS_AND_RUN_LOOP.md).
> Previous phase: [`chat-phase79/`](../chat-phase79/README.md) (merged `df2c5d4`, PR #105).

## Evidence on `main` @ `df2c5d4` (read 2026-10-02)

Every claim below is a read against the checked-out `main`, not a memory:

- **The blind guess this phase replaces:** `ui/ai/AiProjectFiles.kt:45`
  (`MAX_FILES = 5`), `:48` (`MAX_FILE_CHARS = 3_000`),
  `ui/ai/AiPolicy.kt:23` (`MAX_CONTEXT_CHARS = 12_000`) — five files, cut at
  3 000 chars each, packed under 12 000, sent once. The model could not ask for
  anything else.
- **The two network sites that existed:** `ui/ai/AiViewModel.kt:938` (`send`) and
  `:1083` (`testConnection`), both behind
  `if (_state.value.phase != AiPhase.PREVIEW`; the new one is `:763`, inside
  `private fun agentTurn(session: AgentSession)`.
- **The run seam:** `ui/viewmodels/EditorViewModel.kt:3501` (`runFile(context,
  target)`), `:3463` (`stopRun`), `:160-167` (`OutputRunState`, `busy`), and
  `ui/services/ExecutionRunner.kt:50` (`TIMED_OUT_EXIT_CODE = 124`). `runFile`
  already no-ops while `outputState.busy`, which is why "one run at a time" needs
  no new guard.
- **Paths to reuse, not reinvent:** `AiEditProposalParser.validateTargetPath`
  (the Phase 79 path gate, reused by `AiToolPolicy`) and
  `ui/editor/ProjectSearch.kt:159` (`isSearchable`) — which **admits**
  `.env`/`.env.*`/`.npmrc`, so the AI still never reuses it (D5); the tool
  runner searches only the walk's admitted list.
- **Reader helpers shared, not copied:** `ui/ai/AiProjectReader.kt:182`
  (`readCapped`), `:218` (`isSymlink`), `:222` (`insideRoot`), `:228`
  (`canonicalFileSafe`) went `private` → `internal` so `AiToolRunner` uses the
  same containment checks the Phase 78 walk was verified with (and stays
  host-testable).
- **Wiring pins that shape the design:** `AiHelperWiringTest.kt:55` (the
  `AiUiState(` slice — new agent fields are plain additive entries and the new
  types are declared outside), `AiLevel2WiringTest.kt:86` (`askProject` →
  `cancelGather` slice), `AiLevel3WiringTest.kt:102` (the stream-site count),
  `AiSurfaceWiringTest.kt:88` (the same count from the surface test).

## Why this phase exists

The owner's device-round note on Phase 79 (verbatim):

> *"Can't it be like have full knowledge of my code and one model will set what
> to sent what not to / Because as it is now it Can't be agent"*

Phases 76–79 were a **one-shot helper**: the app picked at most
`AiProjectFiles.MAX_FILES = 5` files, cut each at `MAX_FILE_CHARS = 3_000`, packed
them under `AiLimits.MAX_CONTEXT_CHARS = 12_000`, and sent that guess once. The
model could not see a file the guess missed, could not ask a follow-up, and could
never learn whether its change worked. Phase 80 makes the *model* choose what to
read — through CodeC-validated tools over a bounded whole-project map — and adds
an **approved run** whose real exit code and output come back, so an answer can be
checked instead of assumed.

## What shipped

### 80.1 — the map and the tools (pure + `java.io`, host-testable)

| File | What it is |
|---|---|
| `ui/ai/AiRepoMap.kt` (new, pure) | `build(files, budget)` → the always-sent **project map**: header, per-directory groups, `path (N lines)`, up to 8 definition names per file, and an elided tail that says how many files were not listed and invites `list_files`. Caps: 6 000 chars, 100 symbol chars, 40 000 scanned lines, `ELISION_RESERVE = 120`. The AI's own filter is re-applied (`isSecretLike`, `isExcludedDirectory`) — a map can never name a file the walk refused. |
| `ui/ai/AiTools.kt` (new, pure) | The wire protocol `<<<CODEC_TOOL name="…">>> … <<<END_CODEC_TOOL>>>`, `AiToolProtocol.parse`/`describe`/`describeCall`, `AiToolProjectView(existingPaths, runsRemaining)`, `AiToolVerdict`, `AiToolLimits`, and **`AiToolPolicy.validate`** — the single gate every proposed call passes. |
| `ui/ai/AiToolRunner.kt` (new, `java.io` only) | Executes an *allowed* call against the project root: `list_files`, `search_project`, `read_file` (with the live dirty buffer winning over disk). No shell, no `ProcessBuilder`, no writes. `request_run` never executes here. |

Tool surface (`AiToolLimits`): `read_file` ≤ 400 lines, any result ≤ 8 000 chars
(`MAX_RESULT_CHARS`), `list_files` ≤ 200 entries, `search_project` ≤ 40 hits
across ≤ 300 files, query 2–120 chars. Arguments are typed and exhaustive — a
call carrying **any** key outside its allowlist is refused
(`request.args.keys - allowedKeys`), an unknown tool name is refused
("unknown tool"), a path must validate through `AiEditProposalParser` and exist
in the walk's admitted set, and `..`/absolute/symlink escapes never validate.

### 80.2 — the agent loop

`ui/ai/AiAgentLoop.kt` (new, pure) holds the whole decision surface:
`AiAgentLimits`, `AiAgentStopReason`, `AiAgentStep`/`AiAgentStepKind`,
`AiAgentBudget`, `AiAgentDecision`, `AiAgentPolicy.decide`, `AiAgentPrompt.pack`.

Caps (owner chose *looser*, `PART_80_2`): **≤ 12 model turns, ≤ 24 tool calls,
≤ 2 runs, ≤ 8 000 chars per tool result, ~5 min wall clock**, enforced by CodeC
*after every proposed call* — the model is never asked and never decides.

Loop shape as wired in `AiViewModel.kt` (one task at a time, `job` serialised):

1. **PREVIEW** — the chip starts `agentAsk()`/`agentPropose()`, which walks the
   project (`AiProjectReader.scan`), builds the map (`AiRepoMap.build`), builds
   the first prompt (`AiContextBuilder.fromAgent`) and lands on the **existing**
   D4 preview gate: the exact system instruction + user text, Cancel/Send.
2. **Send** → `AiAgentBudget()` is fresh, a TASK step is seeded, and the private
   `agentTurn(session)` streams (the third and last `client.stream` site in the
   file, still behind `phase == AiPhase.PREVIEW`).
3. The answer is parsed locally. No tool block → **Finish** (prose answer; for
   PROPOSE_EDITS the Level 3 parser still turns the text into the same review
   card). Tool block → `AiAgentPolicy.decide` returns `ExecuteTools`,
   `AskRunApproval`, `Finish`, or `Stop` (budget).
4. Allowed calls run on `Dispatchers.IO` oldest-first, each becomes one timeline
   row, results are packed (`KEEP_LAST_RESULTS = 4`, `MAX_REQUEST_CHARS = 24_000`,
   newest keep priority, a disclosure line when older results are dropped) into
   the next request — **the task and the map are in every request**.
5. Malformed/unreadable tool text or a refused call costs a tool call and
   returns the reason as a tool result, so the model can correct itself instead
   of looping for free. Over-budget calls become `Denied` with the reason.
6. `stopAgent(reason)` is the single stopping point: `TURN_BUDGET`, `TOOL_BUDGET`,
   `RUN_BUDGET`, `WALL_CLOCK`, `USER_STOP`, `PROVIDER_FAILURE` — each with a plain
   sentence, and a recoverable session (state resets, nothing dangling).

### 80.3 — the approved run loop

The agent may only ask: `AiToolRunner` returns `Outcome(false, …)` for
`request_run` and executes nothing. The request pauses the whole loop and shows
the **same action the ▶ button performs** (`approvalQuestion` =
"the same RUN action as the ▶ button … Nothing has run yet."). On **Run**,
`EditorScreen` calls the existing `viewModel.runFile(context, target)` — the
public editor path, with all of CodeC's runner guards, one run at a time
(`runFile` no-ops while `outputState.busy`), and the Output panel shows it. On
the run's end, **only the digest** returns to the model: `ui/ai/AiRunDigest.kt`
(new, pure) renders target, both exit codes, durations, `[timed out]` /
`[stopped by the user]`, and the signal/error lines first then the tail —
6 000 chars / 60 lines / 300 chars per line. No terminal typing, no package
install, no Git, no new runner: the user can still stop a run from the same panel
that always could.

### 80.4 — the UI

One preview per task, then a visible timeline: `AgentActivityCard` (rows for
TASK / ANSWER / TOOL / DENIED / RUN_DECISION / RUN_RESULT / STOPPED, plus the
`turns · calls · runs` counter) above the phase body, and `AgentRunCard`
(Run / Skip, Run disabled while `runBusy`) under STREAMING. Denials and stops are
rows, not dialogs. Stop is the sheet's existing button. **Level 3 is unchanged**:
the agent's edits still come back as the same "Proposed file changes" diff card,
per-file checkboxes, Apply, and 1-task Undo (`D1` intact).

## Decisions recorded this phase

Owner answers, 2026-10-02 (asked as explicit options; all binding):

| # | Question | Owner's answer |
|---|---|---|
| 1 | Task preview shape | **`task_preview`** — one preview per task (question + whole-project map + tool allowlist + caps). After Send, read-only in-project steps proceed one at a time with a visible timeline and Stop, **no extra taps**; **every run and every file apply still needs its own explicit tap**; each turn's exact text is shown in the timeline. |
| 2 | Run scope | **`run_only`** — only what RUN ▶ does (build + run of the project/active file) through the existing Output pipeline and runner guards, one at a time, each run approved; the agent may stop a run it started. **No terminal typing, no package installs, no Git.** |
| 3 | Flow scope | **`both_flows`** — when the agent is available, both *Ask about the project* and *Propose edits* route through the agent (map + tools); the Level 2 deterministic path stays as the fallback. |
| 4 | Caps | **`looser`** — ≤ 12 turns / ≤ 24 tool calls / ≤ 2 runs / ≤ 8 000 chars per result / ~5 min; CodeC enforces all of them. |

Law amendments written into
[`00_LEVEL0_DECISION_RECORD.md`](../../../roadmaps/ai-integration/00_LEVEL0_DECISION_RECORD.md):

- **D4 amended (Phase 80)** — an agent task still sends **nothing** before Send
  on the preview, but after Send the task runs multiple read-only in-project
  steps without further taps; runs and applies each keep their own tap. Every
  turn's exact text is disclosed in the timeline, and the preview of turn 1 keeps
  the full two-string D4 rendering.
- **D4 (run)** — the command is named before it runs, and it is exactly the ▶
  action.
- **D1 (unchanged)** — the agent may propose, never write; `ui/ai/` still has zero
  direct writes and zero command execution.
- **D6 (unchanged)** — no new persisted key: the map, timeline, tool results and
  digest are in memory only.

## Acceptance checks (roadmap §Acceptance, mapped)

| Roadmap check | Where it is enforced | Test |
|---|---|---|
| Every tool call validated by CodeC; unknown tools fail closed | `AiToolPolicy.validate` runs after every parse; `AiToolProtocol.parse` falls back to unknown-name calls that `decide` refuses | `AiToolProtocolTest`, `AiAgentLoopTest`, `AiLevel4WiringTest` |
| Denial/cancel/timeout/process-death/provider failure leave a recoverable session | `AiAgentStopReason` + `stopAgent` reset every agent field; `Skip` / `Run did not start` / digest timeout all continue the loop or stop cleanly; nothing is persisted, so process death can only lose the task | `AiAgentLoopTest` (budget/stop cases), `AiLevel4WiringTest` |
| Runs stay subject to runner guards, never bypassed | `AiToolRunner` refuses to execute `request_run`; the sheet calls `viewModel.runFile(context, target)`; `EditorScreen` reports a not-started run instead of forcing one | `AiLevel4WiringTest` (digest-only feedback + `runFile` pin) |
| A malicious prompt in a README cannot authorize anything | Permissions are decided by CodeC from typed arguments, never from model text; the map/tool results are data in the user message, not instructions; a refused call's reason goes back as a result, not as a permission | `AiLevel4WiringTest` (fail-closed pins), `AiToolRunnerTest` (path escapes) |
| Timeline + rollback understandable to a non-expert | `AiAgentStep` titles are plain sentences (`AiCopy`); Level 3's Apply/Undo cards are untouched | `DEVICE_ROUND.md` rows |

Not claimed: no embeddings, no index, no terminal tool, no package/Git tool, no
new dependency, permission, or DataStore key.

## Verification

- **Host harness (kotlinc 2.4.20 + JRE via `jdk4py`, `rule.md` §9):** the four new
  pure files compile and run against the real test suite —
  `AiRepoMapTest` 12, `AiToolProtocolTest` 16, `AiToolRunnerTest` 13,
  `AiAgentLoopTest` 19, `AiRunDigestTest` 9, `AiLevel4WiringTest` 14 = **83 new
  cases**, and **241/241** across the 16 host-compilable AI/wiring test classes
  (includes the amended `AiLevel3WiringTest` 16, `AiLevel2WiringTest` 16,
  `AiHelperWiringTest` 8, `AiSurfaceWiringTest` 12).
- **Wiring-test amendment:** `AiLevel3WiringTest:102`, `AiLevel2WiringTest:90`,
  `AiHelperWiringTest:70` and `AiSurfaceWiringTest:88` previously pinned
  **two** `client.stream(` sites; the owner-authorized agent turn makes it
  **three** (`send`, `agentTurn`, `testConnection`). The amended pins require that
  the third site is the private `agentTurn` behind the PREVIEW gate, so no other
  caller can appear (Phase 79's rollback note stays true: if this pin must move
  again, refactor into a shared private helper rather than relaxing the test).
- **CI (`rule.md` §5, the executor of record):** `Build APK` run
  **`36939786754` ✅ GREEN** on `arena/01a0f9a5-codec` @ `bd3276e` (release APK
  `7,101,208 B`, debug `26,826,604 B`, 14 min). Its `:app:testDebugUnitTest`
  step ran this phase's six new test classes in the real Android/JUnit
  environment and passed, and the whole module compiled. The first push
  (`2367b9a`) failed CI on one real error — a missing
  `import kotlinx.coroutines.isActive` in `AiViewModel` (the agent's tool batch
  reads its scope's `isActive` between calls); that was the **only** error in
  the module and is fixed in `bd3276e`. Final branch CI **`36941118847` ✅ GREEN**
  on `c5dd30a` (release APK `7,101,196 B`).
- **Device round builds:** `CodeC-IDE-1.3.17-universal.apk`
  (`7,101,196 B`, artifact `CodeC-IDE-release` of run `36941118847`).

## Risks, honest limits, and what is explicitly not done

- **The Android half is CI-verified, not host-verified.** `AiViewModel`,
  `AiChatSheet`, `EditorScreen` and `AiCopy` need the Android/Compose SDK, so the
  in-sandbox kotlinc harness cannot compile them; CI's `Build APK` is the
  compiler of record here and the device round is the behaviour of record
  (`DEVICE_ROUND.md`). Everything pure (`AiRepoMap`, `AiTools`, `AiAgentLoop`,
  `AiRunDigest`) and the whole `java.io` runner are host-tested against the real
  classes.
- **The map's symbol list is a heuristic.** Definitions are found by pattern in
  the first `MAX_SYMBOL_SCAN_CHARS = 40_000` characters of a file and capped at
  8 names; a file whose definitions sit deeper is listed with fewer names. The
  model always has `read_file` for the truth, and the map never claims to be a
  parser.
- **Cost is bounded but not estimated.** The caps bound turns (12) and results
  (8 000 chars), and the D4 preview states the caps before Send, but no token or
  price estimate is shown — the roadmap lists a cost estimate as "any", and this
  phase did not add one.
- **The tool protocol is text, not native function calling.** A malformed answer
  costs a tool call and is answered with the reason; native Gemini tools were
  rejected for this phase (reasons in `AiTools.kt`) and can be revisited without
  changing the policy layer.
- **Not implemented, deliberately:** general terminal commands, package
  installation, Git operations, network tools, file deletion outside a Level 3
  delete proposal, background/parallel tasks, embeddings or an on-disk index, and
  any new persistence. Each needs its own owner decision and phase.

## Exit condition

CI green on the session branch, this brief committed with the code, and the
device round ([`DEVICE_ROUND.md`](DEVICE_ROUND.md)) run by the owner with the
resulting table pasted back. **No PR is opened and nothing is merged before the
owner types the merge command** (`rule.md` §3). Levels 5+ remain unauthorized.
