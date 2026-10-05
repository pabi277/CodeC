# Part 93c — the write path, audited: why the agent "can't write code"

> **Status: ✅ IMPLEMENTED (2026-10-05) on `arena/01a10b97-codec`; host sweep 72 classes, 578 passed / 0 failed.
> Build APK recorded below once green.** **No PR, no merge, no `main` push** (`rule.md` §3).
>
> **The owner's message (2026-10-05):** *"Check the permission section properly. Read the code base thoroughly research,
> throw away, open source etc and find. Why can't the agent write code in my codec? And the writing permission was
> previous phases working properly like 3 to 4 phases before I don't recall the current number."*

## 1. The answer, in one paragraph

**The agent has no write tool, and it never did.** By explicit decision (AI Level 3/4, D1, restated by Level 12's
security law) the only way a byte changes on the phone is: the model answers with a `<<<CODEC_EDIT …>>>` block →
CodeC parses it into a **local diff** → the user reviews it and taps **Apply** → `AiEditApplier` writes, journal-first.
The agent's tool surface is read-only (`list_files`, `search_project`, `read_file`, `read_files`) plus
`request_run`, and the system instruction tells the model *"You cannot change files"*. So when the owner says *"the
agent can't write code"*, the truthful decomposition is: **the proposal never became a diff** — the write itself was
never the failing step. The audit found four places that stop a proposed write, and **two of them were the app's own
bugs**, both introduced or left behind by Phase 93:

1. **Phase 93's storage preflight refused CodeC's own projects** (the real regression). It asked Android's storage
   facts *first* and only then whether a grant was needed — so on any phone whose shared-storage grant was missing,
   every AI ask *and* every Apply on a project inside `filesDir/CodeC/projects` was refused, with a sentence about the
   folder having "moved, deleted or renamed". **A project inside CodeC's own storage needs no permission at all** —
   that is exactly why `StorageAccessPolicy.needsExternalAccess` exists.
2. **The parser could not diff a file the model had just read** — the "bookkeeping wall". A `modify` must be compared
   against the file's current content, and the only content captured was the **≤12-file shortlist packed into the
   request** (`AiProjectFiles.READ_SHORTLIST`). The agent, though, reads any file with `read_file`. The result was
   *"Cannot modify 'X' because its current contents were not in the shared project context."* — a sentence about the
   app's map, not about the file — on files the model had literally just read.

…plus two honest boundaries, unchanged by this part:

3. **An answer that arrives through the *ask* arrow is never parsed for edits.** Only the **Propose edits** door (and
   the self-check's proposal step) sets `AiSource.PROPOSE_EDITS`, and only that source runs the parser. Typing "change
   the README tree" into the composer and tapping ➤ starts an *ask*: the model reads, explains, and may even print a
   block — and CodeC draws it as text, with no Apply. This is a design boundary (the agent cannot write on the ask
   door) and it is the likeliest first-contact reason for the complaint; a decision to parse blocks there would widen
   the agent's capability and is **not** taken here.
4. **A model that calls `write_file`/`edit_file`/`apply_patch`** — which coding models do by habit — is refused
   (correctly: there is no such tool), but the old refusal stopped at *"unknown tool"* plus a list. The model then
   answered in prose and nothing was ever proposed.

## 2. The complete write path, gate by gate

| # | Gate | Where | What it does | Verdict after this part |
|---|---|---|---|---|
| 1 | Door | `AiChatSheet` IDLE: `Propose edits`, the `Invalid` branch's `Rebuild proposal`, the self-check's step 4 | Only a `PROPOSE_EDITS` task parses edits (`AiViewModel.finishAgent`) | unchanged (design) |
| 2 | Preview + Send | `AiChatSheet` PREVIEW / `AiViewModel.send` | D4: nothing leaves the phone until the user taps ➤ | unchanged |
| 3 | Storage preflight | `AiViewModel.storageProblemFor` | Refuses before the request when the *shared-storage grant* is missing | **fixed: private roots are never gated, and the grant question is asked only for roots outside CodeC** |
| 4 | Model output | agent loop | The model must stop calling tools and answer with blocks | unchanged |
| 5 | Marker parse | `AiEditProposalParser.parse` / `applyModifyBody` | Marker spelling, hunks, mismatch, caps | Phase 93b (tolerant spelling, hunk recovery) |
| 6 | **Baseline** | `parse`'s `MODIFY` branch | `modify` needs the file's current content in `pendingBaselines` | **fixed: the answer's own modify targets are read from the session root and the parse is re-run** |
| 7 | Review card | `AiChatSheet.ProposalReviewCard` | Per-file checkboxes + **Apply** | unchanged |
| 8 | Apply preflight | `AiViewModel.applyProposal` → `storageProblemFor(root, PROPOSE_EDITS)` | Same gate as #3, on the write side | **fixed with #3** |
| 9 | Applier | `AiEditApplier.apply` | canonical root, symlink/containment, freshness re-check, 256 KB journal, atomic writes, rollback | unchanged; its fallback sentence now names the all-files switch |
| 10 | Editor refresh | `EditorViewModel.syncAfterAiFileChanges` | Open tabs, tree, Git badges | unchanged |

**What "no permission" can and cannot mean in this path.** For a CodeC-created project the folder is
`filesDir/CodeC/projects/<name>` — app-private — so **Android grants nothing here and none is needed**: a permission
failure on such a project was impossible before Phase 93 and is impossible now (that is the bug fixed in #3). The
shared-storage grant matters only for projects *outside* CodeC's own storage; then the refusal now names the exact
switch (`StorageAccessPolicy.fixSteps`: *Settings → Apps → CodeC → Permissions → Files and media → Allow management
of all files*) and the sheet draws the one-tap **Grant access**.

## 3. What changed, in code

| File | Change |
|---|---|
| `ui/ai/AiViewModel.kt` | `storageProblemFor` now classifies the root **before** it reads any permission, and returns for a private root without asking. New `widenedBaselines(session, missing)` + the second parse in `finishAgent`: when the first parse refused a `modify` only because the file's content was not captured, the app reads **exactly the answer's modify targets** — admitted paths only, dirty buffers first, the walk's capped reader, at most `MAX_EDIT_FILES` — and parses again. The state only changes if that same answer is still on screen; a re-parse that is still `Invalid` replaces the reason with the **true** one. |
| `ui/ai/AiEditProposal.kt` | New pure helpers: `proposedEdits(answer)` (the `path`/`op` of each header — headers only, never body text) and `missingBaselinePaths(answer, baselines)` (the `modify` targets with no captured content). |
| `ui/ai/AiTools.kt` | The unknown-tool refusal now says the agent has **no write tool and no command tool**, lists the real ones, and names the route that *can* write (`<<<CODEC_EDIT …>>>` — *a diff the user reviews and applies*). This text is what the model reads back as the tool result, so it can correct itself inside the same task. |
| `ui/projects/AiEditApplier.kt` | `STORAGE_PERMISSION_MESSAGE` now names the Android 11+ switch (*"Allow management of all files"*) and says it is the same switch the Grant access button opens. |

## 4. Tests

* `AiEditProposalTest` **+2** — the shortlist wall (the refusal, the exact `missingBaselinePaths` list, and the very
  same reply becoming a reviewable proposal once the content is captured) and `proposedEdits` (headers only, the
  `update`/`edit` op aliases, a `path="…"` string inside a SEARCH body is content, `create` needs no baseline).
* `AiToolProtocolTest` **+1** — `write_file`, `edit_file` and `apply_patch` are each refused **and** taught the block
  route.
* `Phase93WiringTest` **+2** — the preflight's order (folder classified before any permission read; `if (!needs)
  return null`) and the widen's wiring (source, bounds, dirty buffers, capped reader, `s.answer == answer`).
* **Host sweep: 72 classes, 578 passed / 0 failed** — the tool-protocol class (18 cases incl. the new one) is now
  part of the sweep, so the write path is host-covered end to end.

## 5. Open, for the owner's word (not changed here)

1. **Should the *ask* arrow parse edit blocks too?** Today only **Propose edits** can end in a diff. If the owner
   wants "change X" typed into the arrow to become a reviewable proposal, that is a *capability* change to the ask
   door (still no write: the diff would need his Apply) and belongs in a decision, not a silent fix.
2. **T3-Gemini / T4-NVIDIA** (Level 12) remain open as before.
