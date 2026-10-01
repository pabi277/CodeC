# Part 80.3 — the approved run loop, through CodeC's own RUN ▶ pipeline

Owner's run scope: **`run_only`** — only what the ▶ button performs (build + run
of the project / active file), through the existing Output pipeline and runner
guards, one at a time, each run approved. **No terminal typing, no package
install, no Git.** The agent may stop a run it started (the Output panel's stop
is the same one every run has).

## The AI cannot run anything

Two independent walls, both pinned by `AiLevel4WiringTest`:

1. `AiToolRunner` has no execution path at all: the `AiToolName.REQUEST_RUN`
   branch returns `Outcome(ok = false, "request_run is not executed here …")`,
   and `ui/ai/` contains no `ProcessBuilder`, no `Runtime.getRuntime`, no
   `runCode`, and no writes.
2. The only thing that starts a run is a **user tap in the sheet**, which calls
   the editor's public `viewModel.runFile(context, target)` — the same method the
   RUN ▶ button calls, with every guard CodeC already had (including its
   `outputState.busy` no-op, so a second run cannot overlap).

## The approval card

`AiAgentDecision.AskRunApproval` pauses the whole loop: no request is in flight,
no tool runs, and the sheet shows `AgentRunCard`:

- title `AiCopy.AGENT_RUN_TITLE`;
- the target and the **exact command** the ▶ action will use
  (`outputState.lastTerminalCommand`, passed into the sheet);
- `AiRunDigest.approvalQuestion(targetLabel, commandLabel)` — the sentence names
  the action and says **"Nothing has run yet."**;
- **Run** (disabled while `runBusy`) and **Skip**.

`Skip` (`skipAgentRun`) appends the row *"Run skipped by the user"* with the
model-facing sentence *"The user skipped the run. Continue without it; do not ask
to run again unless nothing else can answer the task."* and resumes the loop.

## Start, finish, and what goes back

```kotlin
// EditorScreen.kt
val aiApproveRun: () -> Unit = {
    val target = aiState.agentRun?.target
    aiViewModel.approveAgentRun()          // charges one run, shows the decision row
    viewModel.runFile(context, target)     // CodeC's own RUN, guarded
    if (!viewModel.outputState.value.busy) {
        aiViewModel.onAgentRunNotStarted(AiCopy.AGENT_RUN_NOT_STARTED)
    }
}
```

- If RUN ▶ could not start (an install prompt, no run profile for the file, a
  busy panel), the loop is told exactly that and continues — the AI never gets to
  "assume" a result.
- When the panel goes idle, `LaunchedEffect(outputState.busy)` builds
  `AiRunDigest.RunResult` from the panel's own state — `buildExitCode`,
  `runExitCode`, both durations, `timedOut`
  (`ExecutionRunner.TIMED_OUT_EXIT_CODE`), `cancelled` (`OutputPhase.CANCELLED`),
  the last output lines and `viewModel.diagnostics` — and hands it to
  `onAgentRunFinished`, which renders and appends one RUN_RESULT row and resumes
  the loop.
- `ui/ai/AiRunDigest.kt` (new, pure) is the only thing the model sees:
  `build(result)` always carries **both exit codes** and the durations in the
  header, names `[timed out]` / `[stopped by the user]`, keeps the signal/error
  lines first and then the tail, appends diagnostics, clips at
  `MAX_DIGEST_CHARS = 6_000` / `MAX_DIGEST_LINES = 60` with a cut note, and
  prints `(nothing was printed)` when the output was empty. `AiRunDigestTest`
  also pins that the digest **never adds instructions** — model-visible text is
  data, not authority.

## Honesty rules (roadmap §Run loop)

- One action at a time: a run cannot overlap the agent's own tool turn (the loop
  is paused while waiting) or another run (`outputState.busy`).
- The model is never told a change was verified because a run was *requested* —
  only the real exit codes and output ever go back, and a timeout or a user stop
  is named in the header rather than presented as a build result.
- Terminal output, compiler text, comments and READMEs are treated as untrusted
  data: they can appear inside a tool result or a digest, but never as an
  instruction the app obeys (the app decides permissions from typed arguments
  only).
- Retries are bounded by the same caps as everything else: ≤ 2 runs per task,
  and a run request beyond that is refused, not paused.

## Tests

- `AiRunDigestTest` (9): exit codes and durations always in the header; a failed
  run keeps the failing exit code and the error lines; a long log keeps the
  signal lines and the tail and counts the rest; the digest never grows past its
  cap; an empty run says so; timeout and user stop are named in the header;
  diagnostics ride along after the output; the approval sentence names the action
  and says nothing ran; the digest never adds instructions.
- `AiLevel4WiringTest`: `request_run` is an approval request and never a call
  into a runner; the approved run goes through the editor's own RUN action; only
  a digest of the run goes back to the model.
