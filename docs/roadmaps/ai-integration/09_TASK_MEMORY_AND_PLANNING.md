# Level 9 — Task memory and planning

**Status: PROPOSED. Not implemented. Not a phase start command. Depends on Level 8. Requires an owner D6 amendment.**
**Shared foundation:** [defect register, security rules S1–S12, sources](00_AGENTIC_MAP_AND_SECURITY_RULES.md).

## User value

**This is the agentic level.** It supplies the one augmentation CodeC is missing.

Anthropic's base unit — the *augmented LLM* — is **retrieval + tools + memory**. CodeC today:
retrieval ✅ · tools ✅ · **memory ❌**. `AgentSession` holds no cache, no working set, no plan, no
findings. Nothing survives eviction, so the agent re-reads what it already saw.

## Owner decision required before this level starts

**Approve writing task memory to `noBackupFilesDir/ai/task/<project>/`.** This widens D6, which currently
reads *"Not saved. The chat is gone when closed; nothing new is persisted."*

Precedent exists: D6 was already amended once as `nobackup_one_task_journal` for the undo journal at
`noBackupFilesDir/ai/undo/<project>/task.journal` (`ui/projects/AiEditApplier.kt:27`). The same lifecycle
applies here — but it is a real amendment and must be recorded as the owner's decision, not the agent's.

## The token mechanism — pointers, not content

The owner's instinct (memory on disk to save tokens) is right; *"full context every time"* is backwards.
**Storage is free. Context is billed per turn.** Re-injecting the working set each turn costs ~1 440 000
chars over 12 turns; carrying pointers costs ~300 000.

> ❌ `js/main.js` → 24 000 characters of source
> ✅ `js/main.js — 900 lines, read 1-400, contains init() and gameLoop() — re-read on demand`

The agent loads the real file only in the turn it needs it. That is the saving.

## What changes

1. **Working set of files** — not tool results. A result is an event; a file is state. Claude Code
   continues after compaction with *"the five most recently accessed files."* Keyed by
   `(canonical admitted path, content version)`; invalidated on dirty-buffer or disk change.
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

## Security rules

**S3** · **S4** · **S5** · **S6** · **S11**. **S9** applies from Level 10, where the on/off control lands.

## Acceptance checks

- [ ] An unchanged repeat read consumes zero executions and returns the cached content.
- [ ] A dirty-buffer or disk change invalidates the cache; stale content is never served.
- [ ] Collapsing or expanding a UI row does not change model input (**S1** re-asserted).
- [ ] The recited plan appears verbatim in the disclosed request (D4).
- [ ] A `.env` and a symlinked secret are refused by the memory store, not only by the sender.
- [ ] Memory is deleted on project deletion and on key deletion.
- [ ] Memory files never appear inside the project tree.
- [ ] With memory off, behaviour falls back to today's in-memory path unchanged.
- [ ] Level 6 duplicate-read metric drops against the recorded baseline.

## Research references

- Anthropic — structured note-taking; *"the five most recently accessed files"* — https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
- Manus — file system as context; recitation via `todo.md`; restorable compression — https://medium.com/@peakji/context-engineering-for-ai-agents-lessons-from-building-manus-71883f0a67f2
- Cognition — *share context, share full agent traces* — https://cognition.com/blog/dont-build-multi-agents
- Cline — `CHARS_PER_TOKEN = 3`; compaction trigger 0.9 → target 0.7 — https://deepwiki.com/cline/cline/3.5-context-management

**Next:** [Level 10 — Agent controls and options](10_AGENT_CONTROLS_AND_OPTIONS.md)
