# Part 93b — the owner's round 2: *"Not solved"*

> **Status: ✅ IMPLEMENTED (2026-10-05) on `arena/01a10b97-codec`; host sweep green (71 classes, 556 passed / 0
> failed); Build APK ⏳ running at commit time.** **No PR, no merge, no `main` push** (`rule.md` §3).
>
> **What happened:** Phase 93 was pushed and CI-green, but the owner ran his phone again and reported the same two
> rows as broken. His message is the scope: the edit-proposal dead end (Level 12 **S2**, Phase 89 finding **F2**) and
> the self-check's **[5/5]** (*"the model never asked to run (answer 0 chars)"*). Whatever the audit found was to be
> **fixed in code**, so both were re-diagnosed from the app's own contracts rather than re-explained.

## 1. S2 — the edit proposal that never became reviewable

**His evidence:** the README tree-restructure ask on NVIDIA ended in *"Unclosed `<<<SEARCH>>>` / `<<<REPLACE>>>` /
`<<<END_SEARCH>>>` block in 'README.md'."* — one sentence, no diff, and **nothing on screen to press**. The old
message was built in `applyModifyBody` for any of three different problems, so it told the owner less than the app
knew, and the sheet drew a bare `ErrorLine` on that branch.

**Two fixes, no loosened rule:**

| Fix | Detail |
|---|---|
| The markers are read with room for the model's spelling | `MARKER = Regex("<<<\\s*(SEARCH\|REPLACE\|END_SEARCH)\\s*>>>", IGNORE_CASE)` now answers *presence* (`hasSearch`, the `create`-body rejection, the hunk walk). `<<<SEARCH >>>`, `<<<End_Search >>>`, any case. The canonical form is still what the app writes and teaches; this widens the **spelling**, never the permission. |
| A forgotten `<<<END_SEARCH>>>` is recovered, not fatal | A hunk ends at its own `<<<END_SEARCH>>>`; **without one, at the next `<<<SEARCH>>>`** (two hunks, one forgotten closer); a trailing hunk with no closer ends at the block's own closed `<<<END_CODEC_EDIT>>>`. Every hunk's SEARCH text must still match the file **byte for byte**, the diff is still computed **locally**, and the user still approves before a byte changes. |

**What is still refused, with the true sentence now:** two `<<<REPLACE>>>` markers for one SEARCH (*merging two
replacements would be a guess, not a recovery*), an empty SEARCH block, a SEARCH with no REPLACE at all (*"The
`<<<SEARCH>>>` block in '…' has no `<<<REPLACE>>>`."* — the marker that is missing is named), stray text before the
first SEARCH, a mismatch (*"… did not match the current file content"*, plus *", and the quoted lines themselves
contain an edit marker."* when that is why), and a block that was never closed at all (`cut off`).

**The dead end itself:** the `Invalid` branch of `AiChatSheet` now draws the **same one-tap `Rebuild proposal`** the
Proposal card offers — the same question goes back with the reason still on screen (`AiProposalResult.Invalid` →
`onProposeEdits(question)`), gated only by *gathering/applying* so a second request cannot be launched on top of a
running one. This is Phase 89 **F2**'s direction and **F5**'s: a cut proposal's note is reachable through the same
button. Nothing is written on either path.

## 2. [5/5] — *"the model never asked to run"* was the app's own refusal

**The audit, in order:**

1. `AiSelfCheck` step 5 asked the model to *"ask me to run the shell command `echo codec-check`"*.
2. `request_run`'s allow-list is **`target` only** (`AiTools.kt`: `setOf("target")`) — a project file, or nothing at
   all. Any other key is refused before validation: *"request_run does not take command"*.
3. So the only request the model could build was **refused by the app**, no card appeared, and the verdict — whose
   failure sentence was hard-wired to *"the model never asked to run"* — blamed the model for the app's rule.

**Fixes:**

* **The question now matches the tool that exists:** *"To check the run tool: call your `request_run` tool to ask me
  to run this project. Do not answer in prose."* `AiToolProtocolTest` pins the literal against
  `AiToolName.REQUEST_RUN.wire`.
* **A refusal is a fact the app reports:** `AiSelfCheckObserved.runRefusedReason` carries the timeline's own refusal
  text (found by `AiViewModel.runRefusedReason()` from the `DENIED` row whose title begins with the copy's own
  `agentStepDeniedPrefix` + the wire name — one builder, so the row and the lookup cannot drift). The verdict is now
  three-way: **card appeared → PASS**; **request refused → FAIL** with *"a run request came back but the app refused
  it: request_run does not take command"*; **nothing arrived → FAIL** with the old sentence, which is now only said
  when it is true.

The check still reports; it never asks for a permission, never sends, and never approves a run (**D4**).

## 3. Files

| File | Change |
|---|---|
| `ui/ai/AiEditProposal.kt` | The tolerant marker reader (`MARKER`/`markerList`), presence and `create`-body checks switched to it, the recovering `applyModifyBody` (hunk ends: own closer → next SEARCH → closed block end), `trimNewlines()`, the marker-in-quoted-lines note. |
| `ui/ai/AiChatSheet.kt` | `Invalid` → the same **Rebuild proposal** row as the Proposal card. |
| `ui/ai/AiSelfCheck.kt` | The run step's prompt; `runRefusedReason` on the snapshot; the three-way run verdict. |
| `ui/ai/AiCopy.kt` | `agentStepDeniedPrefix()` — the refusal prefix, extracted so the timeline row and the check's lookup share one builder. |
| `ui/ai/AiViewModel.kt` | `runRefusedReason()` + the field in `selfCheckObserved()`. |

## 4. Tests

* **Updated:** `AiEditProposalTest` — the pin that asserted *"Unclosed"* for a SEARCH with no REPLACE now asserts the
  named missing marker.
* **New (host):** `AiEditProposalTest` +5 — a forgotten `END_SEARCH` splits at the next SEARCH; a trailing hunk ends
  at the closed block; `<<<search>>>` / `<<<REPLACE >>>` / `<<< end_search >>>` are read; two `REPLACE` markers stay
  malformed; text before the first SEARCH stays rejected. `AiSelfCheckTest` +2 — a refused run reports the refusal,
  not silence; the run question names the tool and asks for what it takes. `AiSelfCheckWiringTest` +2 — the prompt
  pin and the refusal wiring. `AiLevel3WiringTest` +1 — the `Invalid` branch offers the same rebuild and no Apply.
  `AiToolProtocolTest` +1 — the step's literal names the real wire name.
* **Host sweep (this round, from the repo root):** **71 classes, 556 passed / 0 failed** — the same sweep command as
  before (*"a step asked for it"*), now including the parser class in the count.
* **CI (the gate the sandbox cannot be):** the Build APK run for this commit is the record; the run id is in
  `NEXT_STEPS.md` once green.

## 5. What is *not* claimed

Level 12 stays **not accepted**: S2's fix is host-tested but the owner's own round is what closes it, and
**T3-Gemini** (cut off before any diff) and **T4-NVIDIA** (repetition) still await their own fix phases. Levels 13–14
remain unauthorized. **D6 stands** — nothing new is written to a file, a store, a log or a backup.
