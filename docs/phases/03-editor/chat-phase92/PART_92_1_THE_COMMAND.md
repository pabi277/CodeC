# Phase 92 — part 1: the command, the script, and the report

> **Status: ✅ IMPLEMENTED (2026-10-04).** The owner: *"I am tired of testing — give some command and I will run and
> share what is wrong."* Everything here is what runs when he taps **Self-check**.

## 92.1 — the four taps

| Tap | What happens | What the owner does |
|---|---|---|
| **Self-check** (under the question box) | The card appears; check 1 (**File access**) is already judged, and the first preview is built | tap **Send** |
| **Send** ×4 | Each check is an ordinary task through an ordinary door, and the app judges the answer the moment it settles | tap **Next check** between them |
| **Copy report** (any time) | The clipboard holds the redacted report | paste it in the chat |
| **Stop check** | The card goes away; whatever already ran stays in the conversation | — |

The check **never sends**: `AiSelfCheckWiringTest` pins that the self-check block contains no `send()`, no
`client.stream(` and no run approval, and that `client.stream(` is still exactly three sites.

## 92.2 — the script, verbatim

The questions are constants in `AiSelfCheck.STEPS`, so they cannot drift from what the preview shows:

1. **File access** — *no request.*
2. **Remember a code word**
   → `Remember this code word for my next message: KIWI-42. Reply with just: OK`
3. **Follow-up uses it**
   → `What was the code word I told you in my previous message? Reply with just the word.`
4. **Edit proposal parses** (the *Propose edits* door)
   → `Propose one small edit using the CODEC_EDIT block format: create a file called codec-check.txt whose only
   line is OK.`
5. **Run request reaches the card** (the asking door)
   → `To check the run tool: use your run-request tool to ask me to run the shell command \`echo codec-check\`. Do
   not answer in prose.`

Check 3 is the C2/C7 proof, and the *detail line* is the diagnosis:

| Detail the app writes | What it means |
|---|---|
| `carried 0 chars of conversation — the app, not the model` | the packing lost the transcript: **our** bug, in `AiChatSession`/`AiPrompt` |
| `carried 1842 chars of conversation; the model did not use it (answer 63 chars)` | the app did its job; the model ignored quoted history → the next step is real role-based `contents`/`messages` (documented in Phase 91, not assumed) |
| `carried 1842 chars of conversation; the model used it` | the follow-up feature works, and the remaining complaint is answer quality, not plumbing |

## 92.3 — how a verdict is decided

| Check | PASS | FAIL |
|---|---|---|
| File access | `MANAGE_EXTERNAL_STORAGE` granted on API 30+, or the legacy storage permission below it | not granted — the detail names the exact Settings switch |
| Remember | `DONE`, not cut off, non-empty answer | empty answer, a cut-off answer, or a failed request (`FAIL — the request failed: <the app's own line>`) |
| Follow-up | `transcriptChars > 0` **and** the answer used the code word (any casing or spacing: `KIWI-42`, `kiwi 42`, `K I W I - 4 2`) | no conversation carried, or carried and ignored |
| Proposal | at least one file parsed into a reviewable diff | the parser's own rejection reason (this is where Phase 89's S2 `Unclosed <<<SEARCH>>> block` shows up), or no block at all |
| Run | the approval card appeared | the model never asked to run |

Three honesty rules are pinned in `AiSelfCheckTest`:

- **`PENDING` is never a guess.** A step is only judged when *its own question* is the settled task on screen —
  a later question of the owner's can never be read as a check's result.
- **A failed request fails the check**, with the app's own error line (the error text is already scrubbed of
  provider bodies, keys and URLs by `AiErrors`).
- **Checks that were never reached are counted as `not run`** — never as a pass.

## 92.4 — the report is redacted by construction

`AiSelfCheckObserved` is the only input a verdict ever sees, and it holds **numbers, labels and booleans**:

```
question            (the step's own question, for matching — never printed)
settled, answerChars, usedCodeWord, cutShort, errorLine
proposalFiles, proposalInvalidReason, runRequested
sentChars, transcriptChars, provider, model, keySaved
sdkInt, allFilesAccess, storageGranted
```

There is no `answer` field and no `prompt` field, and the pin says so. The code word is checked where the answer
lives (`AiSelfCheck.usedCodeWord(s.answer)` in the view model) and only a boolean travels. The report's own
footer says it in one line: *"No prompts, no answers and no keys are in this text — sizes and results only."*
(**D6**.)

## 92.5 — why a shell command could not do this

A terminal script was the other candidate for *"give some command"*, and it was rejected for a concrete reason:
the API key is encrypted with the Android Keystore, so a shell cannot make a provider call, and the three checks
that matter (does the follow-up carry the conversation, does the edit block parse, does the run card appear) only
exist inside the app's own pipeline. A shell script could only have reported permissions and file names — while the
screenshots that started all this are about what the **models** do with what the app sends.

*Postscript:* the one thing a shell script could have done better — reading the app's permission state — is check 1
of this card now, so nothing was lost.
