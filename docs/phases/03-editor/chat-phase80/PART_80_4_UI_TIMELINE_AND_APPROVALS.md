# Part 80.4 — the UI: one task preview, an activity timeline, two approval cards, Stop

Owner's decision: **`task_preview`** — one preview per task (question + map +
tool allowlist + caps). After Send, read-only in-project steps proceed one at a
time, **no extra taps**, with a visible timeline and Stop; **every run and every
file apply still needs its own explicit tap**; each turn's exact text is shown.
Both the *Ask about the project* and *Propose edits* chips use this path
(`both_flows`); Explain selection / Explain last error are unchanged.

## What changed in the sheet (`ui/ai/AiChatSheet.kt`)

- `AiChatSheet` and `Conversation` take four new parameters: `onApproveRun`,
  `onSkipRun`, `lastRunCommand` (`outputState.lastTerminalCommand`), `runBusy`
  (`outputState.busy`).
- **`AgentActivityCard(state)`** renders above the phase body whenever there are
  agent steps: the heading `AiCopy.AGENT_ACTIVITY` ("What the AI did"), the
  counter line (`agentUsageLine`: `N of 12 steps · M of 24 reads · K of 2 runs`),
  and one row per `AiAgentStep` — title (error-coloured when `ok == false`) and
  the detail the model actually received (tool result, denial reason, digest,
  stop sentence). Long detail is ellipsised rather than dumped.
- **`AgentRunCard(...)`** renders under STREAMING while `state.agentRun != null`:
  the title, `AiRunDigest.approvalQuestion(target, lastRunCommand)` (names the ▶
  action and says nothing has run yet), then **Run** / **Skip** — Run disabled
  while `runBusy`; while `agentRunRunning`, the card says the run is in the
  Output panel instead of showing buttons.
- **Preview** (the D4 gate) for an agent task shows a truthful header
  (`agentPreviewHeader`), the **map's own sentence** (`AiProjectSummary.mapLine`,
  e.g. how many files it names), the left-out line when the walk cut something,
  and `agentPreviewNote()` — the disclosure that after Send the AI may
  list/search/read up to 24 times, may ask to run up to 2 times, that **nothing
  runs until Run**, that nothing is changed without the diff review, and that
  Stop is always there. The full two-string rendering (system instruction + user
  text) is unchanged underneath.
- The Level 2/3 preview is untouched: when the prompt is not an agent prompt, the
  file-list preview renders exactly as before.
- Stop is the sheet's existing button and now also appears while an agent task
  streams (`agentWorkingLine` shows how many steps are done).

## What changed in the editor (`ui/screens/EditorScreen.kt`)

- `aiAskProject` → `aiViewModel.agentAsk(...)`, `aiProposeEdits` →
  `aiViewModel.agentPropose(...)` (both keep passing the live buffer:
  `openText = buffer.text`, `openDirty = viewModel.isDirty.value`).
- `aiApproveRun` / `aiSkipRun` as described in
  [Part 80.3](PART_80_3_APPROVED_RUN_LOOP.md); the sheet's call site passes
  `lastRunCommand` and `runBusy`.
- The digest `LaunchedEffect` is the only new effect; nothing else in the screen
  moved.

## Level 3 stays exactly as it was

The agent proposes edits as ordinary text; `finishAgent` hands the answer to the
same `AiEditProposalParser.parse` path, so an agent edit lands in the **same**
"Proposed file changes" card with per-file checkboxes, **Apply**, baseline
conflict checks, and the 1-task **Undo AI changes** card. `AiLevel4WiringTest`
pins that the agent edit still needs the Level 3 review, and `ui/ai/` still has
no direct writes — the agent's only way to change a file is the user's Apply tap.

## Copy (`ui/ai/AiCopy.kt`)

Every sentence the agent surface shows lives in the Phase 80 block of `AiCopy`:
`agentPreviewNote()`, `agentPreviewHeader(project, chars)`, `AGENT_ACTIVITY`,
`agentUsageLine(...)`, `agentWorkingLine(steps)`, `AGENT_RUN_TITLE`,
`AGENT_RUN_APPROVE/SKIP/RUNNING/SKIPPED_NOTE/NOT_STARTED`,
`AGENT_STEP_ANSWER/MALFORMED`, `agentRunApproved(target)`,
`AGENT_RUN_SKIPPED`, `AGENT_RUN_SKIPPED_MODEL`,
`AGENT_RUN_NOT_STARTED_TITLE`, `AGENT_RUN_FINISHED`,
`agentStepDenied(title, reason)`, `agentStopped(reason)`. No pinned Phase 76/77
string was edited (`AiPromptText.SYSTEM_INSTRUCTION` and `AiCopy.READ_ONLY` are
byte-for-byte what they were; the agent has its own instruction constants).

## Tests

- `AiLevel4WiringTest` (14) pins the sheet: the timeline card, the approval card
  with Run/Skip, Stop still reachable, and that the timeline is in memory (no new
  store key).
- Host-compilable checks live in the other new test classes; the Compose files
  themselves are exercised by CI's compile plus the device round rows A–F in
  [`DEVICE_ROUND.md`](DEVICE_ROUND.md).
