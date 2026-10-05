# Phase 93 — five owner items, and the three bugs the audit found

> **Followed by [Phase 94](../chat-phase94/README.md)** (2026-10-05, same branch): *"more working functions with
> restrictions for sensitive information … more tool with readable structure"* — three read-only tools
> (`find_files`, `outline_file`, `read_run_output`) plus the content-level `AiSecretScan` guard. Phase 93's fixes
> are unchanged and their pins (`Phase93WiringTest`) run in the same host sweep.

> **Status: ✅ IMPLEMENTED (2026-10-05) on `arena/01a10b97-codec`; Build APK ✅ GREEN; not merged.** **No PR, no merge,
> no `main` push** (`rule.md` §3). **Authorization:** the owner's message, same day: *"Ok Whatever you find fix plus i
> have some additional fixes"*, followed by his five items. His answers to the pre-work questions are the binding
> scope: **one arrow for the whole flow**, **both** halves of the permission complaint (*name the cause* + *one-tap
> fix*), **session-only** chat memory (**D6 is not amended**), and **both** homes greet (*hub* and *AI sheet*).
>
> **Build facts (Phase 93, CI is the gate the sandbox cannot be):** `37303387512` **green** on `dc96329` —
> host unit + screenshot tests, debug assemble, release assemble, the release-APK set check; release universal
> **7 194 500 B**, debug universal **27 119 376 B**, R8 mapping **71 627 297 B**, and *release manifest: no
> `android:debuggable` flag*. **Two rounds failed first, and both are worth keeping:** `37301197147` died on
> `:app:compileDebugKotlin` — `listOf(filesDir.absolutePath, …, dataDir)` mixed `String` with `File`, so the list
> was not the `List<String>` the policy takes (fixed in `e40b887`) — and `37302637525` ran **3347 tests** with
> exactly one failure, `SettingsSearchWiringTest`'s "no local declaration is split from its uses": the storage row's
> `facts.granted` read like a use of the launcher callback's own local `granted` at line 899 (fixed in `dc96329` by
> asking the policy's enum instead: `facts.state != StorageAccessState.MISSING`). Both lessons are in the commit
> messages; the Settings quartet now runs in the host sweep, which is why the second one is reproducible offline.

## 1. What the owner asked for, and what shipped

| # | His words | What shipped | Pinned by |
|---|---|---|---|
| 1 | *"Make the arrow button the final button after the arrow. No send option"* | One `ArrowButton` composable, the last control of its row in **every** phase. The preview bar is now `Cancel · hint · ➤` (no `Send` button); the arrow is what sends, unconditionally, and a selected editor buffer can no longer turn that tap into another ask. The phase-78 rule survives everywhere else (*selection → explain it; nothing selected → ask about the project*). | `Phase93WiringTest.the preview bar sends through the arrow, not a Send button`, `…the arrow is drawn in every phase…`, `AiLevel2WiringTest.the send arrow follows the question…` |
| 2 | *"when I really say to change something in file it's showing error no permission"* — **both** (*name the cause* **and** *give the fix*) | The AI's own door now refuses **before** the request with a sentence that names shared storage, plus the version-exact switch (`StorageAccessPolicy.fixSteps`), and the sheet draws a **`Grant access`** row right under it (wired to `MainActivity.requestStoragePermissions()`, the same door the storage screen uses). A refused Apply is checked the same way, and `AiEditApplier` names the same fact if the write itself is impossible. | `…the preflight refuses before a request and offers the fix`, `…an AI edit that cannot write names the permission, not a mystery` |
| 3 | *"Read all the permissions the agent will have and make them correct, because now many permissions are not usable."* | `READ_EXTERNAL_STORAGE` was **uncapped** — Android 13+ never grants it again, so `checkSelfPermission` returned "denied" on a phone where all-files was already held. It is now capped at API 32 like its write twin; the reads live in **one** Android adapter (`StorageAccessAndroid`), the meanings in one pure policy (`StorageAccessPolicy`), and the self-check's report is built from the same facts — on API 33+ it says *not applicable (API 33+)* instead of a false "not granted", and its `access` check passes on a phone where it used to fail. | `ManifestPermissionsTest` (the cap), `StorageAccessTest` (8 route cases, API 24→36), `AiSelfCheckTest` |
| 4 | *"Make the homepage welcome"* — **both of them** | The Projects home greets: **"Welcome to CodeC"** with an invitation instead of a status report; and in the AI sheet a self-check preview now **keeps the card on screen** (the run's state, its five lines and its Skip button) instead of being covered by the ordinary preview, so the check's own home never disappears mid-run. | `Phase93WiringTest` (the two string pins), `…a self-check preview keeps its card, so Skip is always reachable` |
| 5 | *"One big change park project, one history, like any AI that must be stored history of previous chats"* | **Session-only, per project.** `chatsByProject` keeps one `AiChatSession` + task latch per project name, saved on the way out and restored on the way back (`A → B → A` no longer loses A); **New chat** clears only the project in front of you. **D6 does not move**: nothing is written to a file, store, log or backup — the map dies with the process, exactly like the transcript it holds. | `Phase93WiringTest.one conversation per project, and D6 still holds`, `AiChatSessionWiringTest` (the no-File/no-store scan) |

## 2. The three audited bugs (fixed in code, not just reported)

| Bug | Evidence it was real | Fix |
|---|---|---|
| The self-check's fifth check (*run request reaches the card*) **could never pass**. | Its verdict read `state.agentRun != null`, and `agentRun` is a **pause**, cleared by every terminal path — finish, stop, approve, skip — each of which also settles the task, and only a settled task is judged. So the fact was always gone by the time it was read. | A `RUN_REQUEST` timeline row is written **when the card appears** and nothing clears it; `runRequested()` reads the row (and the live card, for the instant between them). |
| The transcript block could render **12 084 characters against a 12 000 cap**. | `charsOf` counted the opening note and the turns but not the closing `TURNS_END` line. | `frameChars()` measures the whole frame; the budget pin now scans the 5 800 → cap boundary and finds no violation. |
| A refusal looked like a dead button. | `SELF_CHECK_BUSY` landed in `state.notice`, which the conversation body drew in **IDLE and PREVIEW only** — never while the answer it refused was **STREAMING**, and never after a **FAILED**. | The bar (pinned under the conversation, present in every phase) draws the notice while `STREAMING`; `FAILED` draws its own copy in the body; **exactly one sentence per phase**. |

## 3. Files

| File | Change |
|---|---|
| `ui/projects/StorageAccess.kt` **(new)** | The pure model: `StorageApi`, `StorageFacts` (`canRead`/`canWrite`/`granted`), `StorageAccessPolicy.facts/fixSteps/legacyLabel/reportLine/needsExternalAccess`. |
| `ui/projects/StorageAccessAndroid.kt` **(new)** | The one Android adapter: `read`, `granted`, `openAllFilesSettings` (per-app intent, global fallback). The only place `Environment.isExternalStorageManager()` is called (behind an API-level early return). |
| `AndroidManifest.xml` | `READ_EXTERNAL_STORAGE` → `android:maxSdkVersion="32"`, with the reason in the comment. |
| `MainActivity.kt` | `requestStoragePermissions()` now asks for the route the phone has: granted → nothing; API 30+ → the app's all-files page; below → the one runtime dialog. |
| `ui/terminal/ShellEnvironment.kt` | `hasStoragePermission` delegates to the adapter — the second opinion is gone. |
| `ui/screens/SettingsScreen.kt` | "Set up storage" uses the same adapter; the legacy row now says the cap and that on Android 13+ the all-files switch is the only route. |
| `ui/ai/AiViewModel.kt` | Per-project chat memory; the storage preflight (`storageProblemFor`) at ask/propose/apply/undo doors; `storageProblem` on the state; the run-request latch; one storage read for the self-check snapshot. |
| `ui/ai/AiChatSheet.kt` | The shared `ArrowButton`; the preview bar's arrow-as-Send; `storageFixRow` (the one-tap grant) drawn with the notice; the bar's streaming notice; the self-check preview that keeps its card. |
| `ui/ai/AiCopy.kt` | `SEND_ARROW_HINT`, `SEND_PREVIEW`, `GRANT_ACCESS`, `PROJECT_FOLDER_UNREACHABLE`, `storageNeeded(writing, fix)`, `AGENT_RUN_REQUESTED`, and the honest `NEW_CHAT_BODY` (per project, in memory, gone when the app closes). |
| `ui/ai/AiSelfCheck.kt` | The `access` verdict and the report line are built from `StorageAccessPolicy`; `progressLabel` says *all 5 checks* only when the run is finished. |
| `ui/ai/AiChatSession.kt` | `frameChars()` — the budget counts the closing line. |
| `ui/projects/AiEditApplier.kt` | Apply and undo name the permission (`STORAGE_PERMISSION_MESSAGE`) instead of failing with a mystery; the two doors share one sentence. |
| `ui/screens/EditorScreen.kt` | Passes `onGrantAccess` to the sheet. |
| `res/values/strings.xml` | The welcoming home copy. |
| `docs/guides/DATA_AND_PRIVACY.md` | The read row's capped truth, plus the AI's own permission surface (what it reads, when it asks, what it never does). |

## 4. Tests

* **New:** `Phase93WiringTest` (14 cases — all five items + the three bugs, source-pinned), `StorageAccessTest` (8 cases — the full route matrix API 24→36, below-30 read/write split, the fix paths per version, the report lines, CodeC-private roots exempt).
* **Updated:** `AiChatSessionTest` (+1: the 5 800 → 12 000-char budget boundary), `AiChatSessionWiringTest` (per-project memory; D6 bans), `AiHelperWiringTest` / `AiLevel2WiringTest` (the arrow's new shape; the phase-78 rule), `AiSelfCheckTest` (the `storage permission: …` report, the finished label), `AiSelfCheckWiringTest` (the check still *reports*, never asks), `ManifestPermissionsTest` (the cap's reason).
* **The host sweep:** 70 host-runnable classes, **520 passed / 0 failed** (fresh compile of the pure sources and the tests; Compose/Robolectric suites excluded, as always). The Settings quartet joined it after CI caught what the sweep could not see.
* **Still owed:** the owner's device round — CI has now covered the compile, the lint-wired tasks and the 3347-test suite. Level 12 stays **not accepted**: **S2's fix is round 2 (§6)** and needs his phone to close it; **T3-Gemini** (cut off before any diff) and **T4-NVIDIA** (repetition) still await their own fix phases, and Levels 13–14 remain unauthorized.

## 5. Deliberate non-changes

* **D6 stands.** No chat text is written anywhere — not to a file, a store, a log or a backup. The per-project map is
  process memory, and `NEW_CHAT_BODY` says so in the confirm dialog.
* The Agent's own memory ceiling, the three `client.stream(` sites, the two `openUri(` sites and every other pinned
  ceiling are untouched.
* `AiCopy.SEND` is left in the file (nothing draws it): the pin asserts the **bar**, not the constant.

## 6. Round 2 — *"Not solved"* (Phase 93b)

The owner ran the phone again on 2026-10-05, screenshotted the same two rows and said **"Not solved"**. Both were
re-diagnosed from the app's own contracts and fixed in code; the full record is
[`PART_93B_ROUND_2.md`](PART_93B_ROUND_2.md).

| Row | What was actually wrong | Fix |
|---|---|---|
| **S2** — the edit proposal never became reviewable | `applyModifyBody` reported every marker problem with one sentence (*"Unclosed …"*) and the sheet's `Invalid` branch was a bare `ErrorLine` with **nothing to press**. | The markers are read with room for the model's spelling (`<<<SEARCH >>>`, any case) and a **forgotten `<<<END_SEARCH>>>` is recovered** — hunk ends at its own closer, else the next `SEARCH`, else the closed block's end — while every hunk still has to match the file byte for byte. Each remaining refusal names the marker that is missing. **`Invalid` now gets the Proposal card's one-tap `Rebuild proposal`** (finding F2; F5's cut note rides the same button). |
| **[5/5]** — *"the model never asked to run (answer 0 chars)"* | The scripted question asked for a **shell command**; `request_run` takes **`target` only**, so the app refused the only request the model could build, no card appeared — and the verdict blamed the model. | The question now asks for the request the tool can actually deliver (*"call your `request_run` tool to ask me to run this project"*), and the snapshot carries `runRefusedReason` so a refusal fails the check **with the app's own reason** instead of being read as silence. |

**Tests:** `AiEditProposalTest` +5 (and its no-REPLACE pin rewritten to the new sentence), `AiSelfCheckTest` +2,
`AiSelfCheckWiringTest` +2, `AiLevel3WiringTest` +1, `AiToolProtocolTest` +1. **Host sweep: 71 classes, 556 passed /
0 failed.** Build APK [`37309397712`](https://github.com/pabi277/CodeC/actions/runs/37309397712) is **green** on
**`ada48f9`** — release universal **7 198 092 B**, debug universal **27 122 048 B**, R8 mapping **71 626 835 B**,
release manifest *no `android:debuggable` flag*.

## 7. Round 3 — *"why can't the agent write code?"* (Phase 93c)

The owner asked for the permission surface to be checked properly and for the real reason the agent cannot write.
The full audit — every gate from the door to the editor refresh, with the two app-side bugs it found and fixed — is
[`PART_93C_THE_WRITE_PATH.md`](PART_93C_THE_WRITE_PATH.md). In one line: **the agent's write route is the
`<<<CODEC_EDIT>>>` proposal → your **Apply**** (there is no write tool, by Level 3/4 design), and two of the things
that stopped a proposal were the app's own: Phase 93's preflight asked Android for a storage grant **before** it asked
whether one was needed — refusing CodeC's own private projects, which need no permission — and the parser could not
diff a file the model had read but which was not among the ≤12 shortlisted files. Both are fixed; the unknown-tool
refusal now teaches the model the block route, and the applier's fallback sentence names the Android 11+ switch.
Build APK [`37328263954`](https://github.com/pabi277/CodeC/actions/runs/37328263954) is **green** on **`e612db06`**
— release universal **7 197 988 B**, debug universal **27 126 752 B**, R8 mapping **71 654 841 B**, release manifest
*no `android:debuggable` flag*; host sweep **72 classes / 578 passed / 0 failed**.
