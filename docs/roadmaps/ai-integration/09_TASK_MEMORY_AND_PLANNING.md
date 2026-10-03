# Level 9 — Task memory and planning

**Status: IMPLEMENTED on `arena/01a1004c-codec`; latest local host pre-validation passed 72/72 selected methods across eight classes (Temurin 25.0.2 + kotlinc 2.4.20). Build APK round 1 (`37106180481`) exposed three existing Level 8 regressions, fixed locally; rerun pending. Not merged; no PR. Depends on merged Level 8.**
**Owner authorization (2026-10-03): Level 9 is started with the bounded D6 amendment recorded below. Level 10's persistent-memory UI remains out of scope.**
**Shared foundation:** [defect register, security rules S1–S12, sources](00_AGENTIC_MAP_AND_SECURITY_RULES.md).

## User value

**This is the agentic level.** It supplies the one augmentation CodeC is missing.

Anthropic's base unit — the *augmented LLM* — is **retrieval + tools + memory**. CodeC today:
retrieval ✅ · tools ✅ · **memory ❌**. `AgentSession` holds no cache, no working set, no plan, no
findings. Nothing survives eviction, so the agent re-reads what it already saw.

## Approved D6 amendment — 2026-10-03

The owner authorized only **bounded task memory** in
`noBackupFilesDir/ai/task/<project>/`, excluded from Android Auto Backup and
outside the project tree. It may contain only admitted non-secret file-cache
entries and structured findings, decisions, and plan. It may not contain raw
chat transcript, prompts, answers, maps, tool results, or timeline. Filter secrets
before writes; use hard caps; delete memory on project deletion and key deletion.
All other D6 limits remain in force. The existing
`noBackupFilesDir/ai/undo/<project>/task.journal` amendment is unchanged.

The owner also directed that the on/off UI belongs to Level 10. Level 9 therefore
provides an internal persistent-off path without adding a new Settings control.
See the Level 9 amendment in [`00_LEVEL0_DECISION_RECORD.md`](00_LEVEL0_DECISION_RECORD.md).

## The token mechanism — pointers, not content

The owner's instinct (memory on disk to save tokens) is right; *"full context every time"* is backwards.
**Storage is free. Context is billed per turn.** Re-injecting the working set each turn costs ~1 440 000
chars over 12 turns; carrying pointers costs ~300 000.

> ❌ `js/main.js` → 24 000 characters of source
> ✅ `js/main.js — 900 lines, read 1-400, contains init() and gameLoop() — re-read on demand`

The agent loads the real file only in the turn it needs it. That is the saving.

## What changes

1. **Working set of files** — not tool results. A result is an event; a file is state. Claude Code
   continues after compaction with *"the five most recently accessed files."* The reuse key is
   `(canonical admitted path, effective read range, content version)`; dirty-buffer or disk changes
   invalidate it before any cached result can be served.
2. **Findings and decisions ledger** — what was learned, what is still open, what was refused and why.
3. **Recited plan at the end of every request**, replacing the generic tail in `AiAgentPrompt.pack`:
   *"Continue the task. Emit one or more tool blocks if you need more of the project…"*
   Manus: recitation *"pushes the global plan into the model's recent attention span"*, fighting
   lost-in-the-middle. **The slot already exists and is already last** — highest leverage per line changed.
4. **Duplicate-read cache** and the no-progress guard.
5. **Storage** under **S4/S5/S6**.

## What memory must not become

- Not a project file. It lives in CodeC's own directory, never in the project tree (**S6**).
- Not a channel for secrets. The AI's own filter runs **before** write, not only before send (**S5**).
- Not trusted input. Re-injected memory is **untrusted data**, like tool output (**S3**).
- Not permanent. Deleted on project deletion and key deletion (**S4**).
- Not a guarantee of visibility. **A file retained in app memory is not necessarily still visible to the
  model.** Each turn records what actually reached the request.

## Touches

New pure `AiTaskMemory.kt` (host-testable, no Android import) + a thin Android store · `AiViewModel.kt` ·
`AiAgentLoop.kt`.

## Phase 86 implementation bounds

The implementation uses one versioned binary file at
`noBackupFilesDir/ai/task/<project>/task-memory.bin`; the parent is Android's
no-backup directory, so the file is excluded from Auto Backup. Hard caps are
5 file entries, 32 KiB per cache entry, 160 KiB total file content, 512 KiB as
the largest source snapshot considered, 256 KiB total store bytes, 6 findings,
6 decisions (512 characters each), and 8 plan items (256 characters each). The
bounded prompt context and plan recitation are separate; raw request/response
and timeline fields do not exist in the persisted model.

Dirty buffers contribute to the in-memory version key but are never stored as
file snapshots. Before reads and each later model request, cached content and source-bound notes
are reconciled against current admitted paths, canonical containment, symlink
state, content version, and a stable dirty-buffer snapshot. If editing remains
continuous across three checks, file-backed memory is omitted for that request
rather than sending stale state; a later stable read can rebuild the cache. The
effective delivered line range participates in the cache key. Disk, dirty-buffer,
and cached reads preserve the same out-of-range refusal behavior.

Persistent storage can be disabled internally: the current task keeps using its
in-memory session, while loading returns empty persistent state and any retained
project copy is cleared. There is no Level 9 UI switch; Level 10 owns it. D4 is
preserved by packing the first preview and first Send from the same
`AiPrompt.userText`, revalidating before Send, and showing a changed-memory notice
that requires another Send. Every later request's recitation is its exact final
user-text suffix.

## Security rules

**S3** · **S4** · **S5** · **S6** · **S11**. **S9** applies from Level 10, where the on/off control lands.

## Acceptance checks

Implementation and local host pre-validation are complete: **72/72 selected test
methods passed across eight classes** against the Android-free production
core/store, two Level 8 regression classes, and source-pin suite. Build APK run
`37106180481` was red on three existing Level 8 refusal/pointer assertions; the
root causes were fixed and the rerun is pending. This is not the full Gradle suite or Android/Compose compilation; the
Build APK workflow remains pending and is the executor of record. See the
[Phase 86 ledger](../../phases/03-editor/chat-phase86/README.md).

- [x] Host tests: unchanged repeat reads return the cached result/content; a 24-read replay measured one execution and 23 reuse hits.
- [x] Host tests: dirty-buffer/disk changes invalidate cache identity and source-bound findings; stale persisted snapshots are deleted on load.
- [ ] Collapsing or expanding a UI row does not change model input (**S1** re-asserted); Android/Compose verification remains pending.
- [x] Host tests and source pins: the recited plan is a verbatim suffix in the preview/first Send and final disclosed request (D4).
- [x] Host test: `.env` and a manually admitted symlink to an out-of-project secret are refused before memory caching.
- [x] Host test and source pins: project deletion and key deletion clear task memory.
- [x] Host test: memory files remain outside the project tree under the no-backup root.
- [x] Host test: persistent-off mode clears retained storage while task-local memory remains usable.
- [x] Host replay against Level 6's 23-duplicate baseline: one execution, 23 cache hits (not a full task-loop benchmark).

## Research references

- Anthropic — structured note-taking; *"the five most recently accessed files"* — https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
- Manus — file system as context; recitation via `todo.md`; restorable compression — https://medium.com/@peakji/context-engineering-for-ai-agents-lessons-from-building-manus-71883f0a67f2
- Cognition — *share context, share full agent traces* — https://cognition.com/blog/dont-build-multi-agents
- Cline — `CHARS_PER_TOKEN = 3`; compaction trigger 0.9 → target 0.7 — https://deepwiki.com/cline/cline/3.5-context-management

**Next:** [Level 10 — Agent controls and options](10_AGENT_CONTROLS_AND_OPTIONS.md)
