# Level 4 — Bounded agent tools and verified run loop

**Status: ✅ MERGED 2026-10-02 as [Phase 80](../../phases/03-editor/chat-phase80/README.md)** on `arena/01a0f9a5-codec` (owner: *"Start Phase 80 — full Level 4"*; the four scope answers are recorded in the Phase 80 README and in [`00_LEVEL0_DECISION_RECORD.md`](00_LEVEL0_DECISION_RECORD.md)). Merged to `main` @ `e089880` via PR #106 (post-merge CI `36972776771` green) on the owner's instruction (*"Mark the docs device pass and merge it"*). Advanced and security-sensitive; depends on Levels 1–3.

## User value

The agent can perform a bounded inspect → plan → edit proposal → approved apply → approved run → inspect result loop, using CodeC's existing editor, project, output/diagnostics, runner, and terminal surfaces.

## Tool surface: narrow first

Prefer explicit CodeC-owned operations over exposing the entire shell at once:

- list files inside the selected project;
- read a file/range and search project text;
- propose a patch (Level 3 review required);
- inspect the latest run output and compiler diagnostics;
- request a supported build/run action through the existing run pipeline;
- stop a run that CodeC started.

A general terminal command is a later and higher-risk tool. If added, display the exact command, working directory, and expected scope; require approval; support cancel/timeout/output limits; and never let command output grant new permissions. Package install, Git push, file deletion, network access, and commands outside the project need distinct explicit consent.

## Why not call the terminal a sandbox

CodeC's terminal is powerful because it can run installed Linux tools on-device. It executes under CodeC's app identity and can reach CodeC-accessible data. Android app isolation protects other apps from CodeC, not CodeC's own projects from an agent command. A true stronger boundary may require staging the project into an isolated disposable workspace and carefully controlling imports, exports, networking, and process limits. Do not claim this is solved by merely launching a subprocess or using the in-app terminal.

## Tool approval policy

At this level, approval is required for each write/apply and each run/command. Read-only in-project operations can be combined under the active project consent, with a visible activity timeline and a stop button. Keep Git changes and external network/package operations outside the initial allowlist.

The model never decides whether it is authorized. The app enforces permissions after every proposed tool call. Every call has validated typed arguments, normalized paths, bounded output/time, a result state, and an audit entry excluding secrets.

## Run loop

- Use CodeC's established runner and run-output surface instead of launching a duplicate runner.
- Start one action at a time initially; do not overlap agent writes with a running build or active editor save.
- Send only the relevant run output/diagnostics back to the model, after disclosure.
- Treat terminal output, compiler text, source comments, and project instructions as untrusted input.
- Limit retries/turn count, model calls, wall time, output bytes, and any provider cost estimate; expose stop/cancel.
- After a run, present what was tested and its actual exit/result. Never label an unrun change as verified.

## Acceptance checks

- Every tool call is validated by CodeC; unknown tools fail closed.
- User denial, cancellation, timeout, process death, and provider failure leave a recoverable session.
- Run actions remain subject to CodeC's runner guards and never silently bypass them.
- A malicious prompt embedded in a README cannot authorize a command or access another folder.
- Full event timeline and file rollback evidence are understandable to a non-expert.

## Research references

Cline's approval/plan workflow is a relevant UI reference. OpenHands is useful for studying runtime/workspace boundaries, but its heavier server/container architecture is not a mobile drop-in. DroidAgentKit illustrates structured, allowlisted tools over raw shell for Android development; its host-side scope differs from CodeC's in-app runner.
