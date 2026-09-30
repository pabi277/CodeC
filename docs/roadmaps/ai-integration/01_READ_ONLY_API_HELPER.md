# Level 1 — One-provider, read-only API helper

**Status: proposed first implementation level; no code is authorized by this plan.**
**Update 2026-09-30: authorized by the owner (*"Start lavel 1"*) and 🚧 IMPLEMENTED as [Phase 76](../../phases/03-editor/chat-phase76/README.md); device round ✅ passed 2026-10-01 (default model `gemini-3-flash-preview`), not merged.**

> **Level 0 decisions (2026-09-30) narrow this page:** provider = Google Gemini (stateless, `store=false`), key encrypted with Android Keystore, per-request preview including Gemini's free-tier data-use note, open project tabs only (not single-file mode), nothing saved. The binding constraint list is in [§3 of the decision record](00_LEVEL0_DECISION_RECORD.md#3-level-1-constraints-now-fixed-by-level-0); O1–O3 are decided too: the [18+ and terms gate at key setup](00_LEVEL0_DECISION_RECORD.md#41-key-setup-gate-o1) and the [technical design](00_LEVEL0_DECISION_RECORD.md#42-technical-design-for-level-1-o2-agent-decision) (`HttpURLConnection`, `streamGenerateContent` with `store:false`, no new dependency). A Level 1 brief still needs the owner's start command.

## User value

Answer a focused coding question using text the user explicitly selects: explain selected code, explain the current run/compiler diagnostic, or suggest what to investigate next. The AI rail slot can open this lightweight helper when an implementation is later approved.

## Scope

- One configured provider and model for the first thin slice.
- User-triggered request only; show network/provider identity and the exact selected snippet or diagnostic context.
- Read-only: no file tools, project crawling, terminal, run, package install, Git, or background agent loop.
- Streaming response, cancel, retry, and understandable provider/network/rate-limit errors.
- Clear that AI output can be wrong; no automatic edits.

## Why start here

It validates the Compose UI, API compatibility, streaming/cancellation, key entry, error handling, and privacy disclosure without granting the model file or command capabilities. A successful request is useful immediately and becomes the provider seam for later levels.

## Credential and privacy requirements

- The key belongs to the user and should be stored using a protected Android credential design, not as a normal project preference or in source/build configuration.
- Never put the key in the prompt, logs, crash records, export/backup, clipboard, or support bundle.
- CodeC calls the configured provider directly only after a user action. Make the provider and transmitted content visible.
- A BYOK arrangement means the user may be billed by that provider and is subject to that provider's terms; CodeC must not imply it is free or that CodeC controls provider retention.
- Test that a cold launch, browsing, editing, and running do not contact AI endpoints.

CodeC's source of truth is [`docs/guides/DATA_AND_PRIVACY.md`](../../guides/DATA_AND_PRIVACY.md). It documents user-triggered outbound traffic, the current GitHub token in DataStore, and backup exclusions. An AI-key design must be reviewed against those facts; do not silently expand backup or logging scope. The research dossier's [CodeC data map](../../research/AI_INTEGRATION_RESEARCH_20260930.md#current-data-and-boundaries-to-inspect) lists the source files/tests to recheck before implementation.

## Acceptance questions

- Can the user distinguish selected code sent to a provider from code kept on-device?
- Can a request be canceled without leaving a hidden active network operation?
- Do invalid keys, offline state, provider rejection, and rate limits produce useful messages without exposing secrets?
- Is the feature entirely inert until the user configures and invokes it?

## Deliberately deferred

Whole-folder context, file editing, tool calls, multiple simultaneous model calls, local models, and auto-run.
