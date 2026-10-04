# Phase 92 — the self-check (one command instead of twenty rows)

> **Status: ✅ IMPLEMENTED (2026-10-04) on `arena/01a102bd-codec`.** The owner: *"I am tired of testing — give some
> command and I will run and share what is wrong."* This is that command: **Self-check** in the AI sheet, five checks
> the app judges itself, and a redacted report to paste back.
>
> **Authorization:** his message, the same day. **No PR, no merge, no `main` push** (`rule.md` §3). Level 12 stays
> **not accepted**; Levels 13–14 stay unauthorized.

## The command, exactly

1. Open the ✨ AI sheet and tap **Self-check** (it sits under the question box, in every face).
2. The card appears. The first line is already judged — **File access**, which needs no request.
3. For each of the next checks the sheet builds an **ordinary preview**; tap **Send** when you are ready.
   **Four Sends** in total, and the app judges each answer itself — nothing is asked of you but the tap.
4. Tap **Next check** after each verdict, and **Copy report** at any point (it is complete when the card says
   *all checks run*).
5. Paste the report in the chat.

**Nothing is sent by the self-check.** Every request is an ordinary preview the owner approves (**D4**), and the
check adds **no** fourth `client.stream(` site — pinned.

## The five checks, and who judges them

| # | Check | Judgment (mechanical, never an opinion) |
|---|---|---|
| 1 | **File access** (no request) | `MANAGE_EXTERNAL_STORAGE` on API 30+, the legacy storage permission below it — answers the owner's C8 *"no permission"* with the real state and the exact switch to flip |
| 2 | **Remember a code word** | the model answered something (`DONE`, not cut off, non-empty) — the setup the next check needs |
| 3 | **Follow-up uses it** | **the one that matters (C2/C7)**: how many characters of conversation the request carried, and whether the answer used the code word. `carried 0 chars` = **our** bug; `carried N chars, did not use it` = the model's |
| 4 | **Edit proposal parses** | a `<<<CODEC_EDIT>>>` block came back and became a reviewable diff (S2/N1), or the parser's own rejection reason |
| 5 | **Run request reaches the card** | the approval card appeared (N1), or the model never asked — a model fact, not a UI bug |

Continue (`N1`) is deliberately **not** scripted: it needs an answer that the provider cut off, which cannot be
produced on demand. The Phase 91 round keeps that row.

## What the report is — and what it is not

The report is built by a **pure** object (`AiSelfCheck.report`), from counts, labels and booleans only:

```
CodeC AI self-check — 5 checks, app 1.3.17
Android API 34 · all-files access: granted · storage permission: not granted · API key saved: yes
Recipient: NVIDIA Build · dev/test only · nvidia/nemotron-3-super-120b-a12b

[1/5] File access.................... PASS — all-files access granted (API 34)
[2/5] Remember a code word........... PASS — answered (2 chars, 2394 sent)
[3/5] Follow-up uses it.............. FAIL — carried 1842 chars of conversation; the model did not use it (answer 63 chars)
[4/5] Edit proposal parses........... FAIL — no edit block came back (answer 512 chars)
[5/5] Run request reaches the card... PASS — the run approval card appeared

Result: 3 passed, 2 failed, 0 not run.
No prompts, no answers and no keys are in this text — sizes and results only.
```

- **No prompt, no answer, no key.** The snapshot the verdicts are built from (`AiSelfCheckObserved`) has no field
  that could carry an answer; the code word is checked where the answer lives and only a boolean travels
  (**D6**). Pinned twice — in the pure tests and in the wiring pins.
- **A verdict is never a guess.** A step is `PENDING` until *its own* question is the settled task on screen, so a
  later question of the owner's can never be read as a check's result.
- **A failed check is named.** The detail line carries the numbers and, where there is one, the app's own error or
  the parser's own rejection reason.

## Why this replaced "twenty rows"

The Phase 90 round cost the owner a judged row per feature and produced a list of *opinions* mixed with facts. The
self-check inverts it: the app states the facts (**did the request carry the conversation or not**, **did the block
parse or not**, **is the permission granted or not**), and the owner's only job is to send and paste. Any row that
fails is then either a one-line app fix or a model-capability finding — never a judgement call.

## Laws that did not move

- **D4** — every check previews; the Send is his tap. The check itself never sends.
- **D6** — the run and the report live in memory; the report carries numbers, labels and booleans.
- **S6** — the report leaves through the clipboard, like every other copy in `ui/ai`: no share sheet, no file.
- **S9** — every check is an ordinary task under the ordinary caps. No ceiling moved.
- Three `client.stream(` sites and two `openUri(` in `ui/ai` — pinned.

## The C9/C11 answer, measured rather than looked at

The owner's *"no visible block"* / *"light mode visible but dark mode not visible"* had a measurable cause: the
block's surface was `surfaceVariant`, the **bubble's own colour** — exactly **1.00:1** against the bubble — with
`outlineVariant`, the faintest line in the theme, as its frame. Phase 91 moved the block to `surface` with an
`outline` frame. `AiCodeBlockContrastTest` keeps the numbers:

| Pair | Dark | Light | Requirement |
|---|---|---|---|
| frame (`outline`) vs the block's surface | **5.41:1** | **4.44:1** | ≥ 3.0 (`AA_NON_TEXT`) |
| the block's surface vs the bubble's (`surfaceVariant`) | **1.83:1** | 1.26:1 | ≥ 1.5 in the dark theme — the theme he reported |

In the light theme the surface step is small but the frame carries the block (4.44:1) — which matches his own
report (*"in light mode visible"*). A future edit that walks the block back into the bubble now fails the build.

## Files

- `app/src/main/java/com/codeci/ide/ui/ai/AiSelfCheck.kt` — the script, the verdicts, the report (pure).
- `AiViewModel.kt` — `startSelfCheck`, `selfCheckNext`, `stopSelfCheck`, `selfCheckReport`, `selfCheckLive`,
  `settleSelfCheckStep` (called from `commitFinishedTask`), `selfCheckObserved` (the only Android reads).
- `AiChatSheet.kt` — the **Self-check** entry in the idle bar and the card (both faces).
- `AiCopy.kt` — the strings. `EditorScreen.kt` — the five callbacks.

## Tests

| File | Cases | What it pins |
|---|---|---|
| `AiSelfCheckTest` | **19** | the script, the doors, both sides of every verdict, `PENDING` never guessed, the code word tolerance, and the report's counts and redaction |
| `AiSelfCheckWiringTest` | **10** | no Send in the check, three stream sites, the ordinary doors, the settle hook's place and idempotence, the snapshot's field list, the clipboard road, read-only permissions, the card in both faces, memory-only + resets, and **every `AiCopy` name the AI surface uses is declared** (the missing-constant sweep) |
| `AiCodeBlockContrastTest` | **5** | the contrast numbers above, the roles the sheet actually draws, and the old colours pinned as *not* in the code |

**75 passed / 0 failed** across the host suites in the sandbox harness — Phase 90/91's 41 (`AiChatSessionTest` 14,
`AiChatSessionWiringTest` 10, `Phase90DeviceTest` 7, `Phase91SimpleChatTest` 10) plus this phase's **34**.

> **The 34th case is a lesson.** `SELF_CHECK_TITLE` was referenced by the card and never declared; a single-file
> syntax check and every string pin passed it, and **CI** is what found it (run `37220392801`,
> `AiChatSheet.kt:1171/1173 Unresolved reference`). The sweep now walks every main source the AI surface owns,
> blanks strings and strips comments, and fails if any `AiCopy.<name>` has no declaration — the whole class of
> "constant that does not exist" is a host failure from now on, not a CI round.

## Exit conditions

- [x] The command is implemented with its tests; the host harness is green (**75 / 0**).
- [x] The report is redacted and pinned as such; the contrast numbers are pinned.
- [x] **Build APK green on the head** — run [`37220578998`](https://github.com/pabi277/CodeC/actions/runs/37220578998)
      on `090af93`: release `CodeC-IDE-1.3.17-universal.apk` = **7 191 772 B**, debug = **27 106 392 B**,
      `mapping.txt` = 71 597 427 B, release manifest with **no `android:debuggable` flag**. (The first head,
      `407cf4d`, failed this job on the missing `SELF_CHECK_TITLE` — the lesson above.)
- [ ] **The owner runs it once and pastes the report** — the whole point of the phase.
