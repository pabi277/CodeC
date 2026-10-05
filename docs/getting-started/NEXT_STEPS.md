**2026-10-05 — Phase 93c / AI — \"why can't the agent write code?\": the write path audited, and the two real bugs fixed.** The owner asked for the permission surface to be checked properly and for the reason the agent cannot write (*\"the writing permission was previous phases working properly\"*). The audit's answer: **the agent has no write tool** — by Level 3/4 design the only route is a `<<<CODEC_EDIT …>>>` block → a local diff → **Apply** → `AiEditApplier` — so a failure to write was always a failure to *propose*. Two of those failures were the app's own. **(1) Phase 93's preflight was ordered wrong:** it read Android's storage facts before asking whether a grant was needed, so on a phone whose shared-storage grant was missing it refused **CodeC's own projects** — `filesDir/CodeC/projects`, where no permission exists to grant — and blamed it on a folder that had \"moved, deleted or renamed\". The root is now classified first and a private root is never gated. **(2) The \"bookkeeping wall\":** a `modify` can only be diffed against content the parse holds, and the parse held only the ≤12-file shortlist packed into the request — so a file the agent had just read with `read_file` was refused with *\"its current contents were not in the shared project context\"*. `finishAgent` now reads exactly the answer's modify targets (admitted paths only, dirty buffers first, the walk's capped reader, ≤ `MAX_EDIT_FILES`) and parses again; every rule still runs, the diff is still local, the user still approves. **(3)** an unknown tool refusal (`write_file`, `edit_file`, `apply_patch` — what coding models call by habit) now tells the model there is no write tool and hands it the block route, so it can correct itself in the same task; **(4)** `AiEditApplier`'s fallback sentence names the Android 11+ switch (*\"Allow management of all files\"*). Documented boundaries: an answer through the **ask** arrow is never parsed for edits (only **Propose edits** can end in a diff — a capability decision, not taken silently), and the parser still refuses marker mismatches and caps. **Tests:** `AiEditProposalTest` +2, `AiToolProtocolTest` +1, `Phase93WiringTest` +2; the tool-protocol class joined the host sweep, which is **72 classes / 578 passed / 0 failed**. Build APK run id recorded once green. **No PR, no merge, no `main` push.** Record: [`chat-phase93/PART_93C_THE_WRITE_PATH.md`](../phases/03-editor/chat-phase93/PART_93C_THE_WRITE_PATH.md).\n\n**2026-10-05 — Phase 93b / AI — the owner's round 2 on Phase 93: "Not solved", and both rows are fixed in code.** He re-ran the phone (app 1.3.17 (1434), API 36, NVIDIA `nvidia/nemotron-3-super-120b-a12b`) and reported the same two failures, so both were re-diagnosed from the app's own contracts. **(S2, Phase 89 finding F2) the edit proposal that never became reviewable:** `applyModifyBody` answered every marker problem with one sentence (*"Unclosed …"*), and the sheet's `Invalid` branch was a bare reason line with nothing to press — now the three markers are read with room for the model's spelling (`<<<SEARCH >>>`, `<<<End_Search >>>`, any case) and a **forgotten `<<<END_SEARCH>>>` is recovered**: each hunk ends at its own closer, else the next `<<<SEARCH>>>` (two hunks, one forgotten closer), else the closed block's own `<<<END_CODEC_EDIT>>>`; every hunk still matches the file **byte for byte**, the diff is still local, and the user still approves. Two `REPLACE` markers for one SEARCH, an empty SEARCH, a SEARCH with no REPLACE, stray text before the first SEARCH and a block that was never closed all stay **Invalid — each naming the marker that is missing** — and that branch now draws the Proposal card's **one-tap `Rebuild proposal`** (finding F2, and F5's cut note rides the same button). **([5/5] of the self-check) "the model never asked to run":** the scripted question asked for a *shell command*, `request_run`'s allow-list is **`target` only** (`setOf("target")`), so the app refused the only request the model could build, no card appeared, and the verdict blamed the model — the question now asks for the request the tool can actually deliver (*"call your `request_run` tool to ask me to run this project"*) and the snapshot carries **`runRefusedReason`** (from the `DENIED` timeline row), so the verdict is three-way: card appeared → PASS; **request refused → FAIL with the app's own reason**; nothing arrived → the old sentence, only when it is true. **Tests:** `AiEditProposalTest` +5 (and its no-REPLACE pin rewritten), `AiSelfCheckTest` +2, `AiSelfCheckWiringTest` +2, `AiLevel3WiringTest` +1, `AiToolProtocolTest` +1; **host sweep 71 classes / 556 passed / 0 failed**; Build APK [`37309397712`](https://github.com/pabi277/CodeC/actions/runs/37309397712) **green** on `ada48f9` (release universal 7 198 092 B, debug 27 122 048 B, R8 mapping 71 626 835 B, no `android:debuggable` flag). **No PR, no merge, no `main` push.** Level 12 stays **not accepted** — S2 now needs his phone, and **T3-Gemini** and **T4-NVIDIA** still await their own fix phases. Record: [`chat-phase93/PART_93B_ROUND_2.md`](../phases/03-editor/chat-phase93/PART_93B_ROUND_2.md).

**2026-10-05 — Phase 93 / AI — five owner fixes, and the three bugs the audit found: ✅ IMPLEMENTED on `arena/01a10b97-codec`; Build APK ✅ GREEN `37303387512` on `dc96329`; not merged — the owner: *"Ok Whatever you find fix plus i have some additional fixes."*** His five, answered in code: **(1) one ➤ arrow for the whole flow** — a single `ArrowButton` composable is the last control of every bar, the preview bar is `Cancel · hint · ➤` with no Send button, and that arrow SENDS (a selected buffer can no longer turn the tap into another ask; the phase-78 rule — selection explains, otherwise ask the project — still governs the composer and mid-stream bars); **(2) the permission failure is named before it happens, and fixed in one tap** — `READ_EXTERNAL_STORAGE` was **uncapped**, so Android 13+ answered "denied" on a phone where all-files was already held (now capped at API 32 like its write twin), the reads live in **one** adapter (`StorageAccessAndroid`) under **one** policy (`StorageAccessPolicy`), the AI's door refuses with the real cause plus the version-exact switch (`fixSteps`) and the sheet draws a **`Grant access`** row wired to `MainActivity.requestStoragePermissions()`, and `AiEditApplier` names the same fact instead of failing with a mystery; **(3) the permissions the agent has are now correct** — the self-check's `access` verdict and report line are built from the same facts (API 33+ says *not applicable (API 33+)*, and the check passes where it used to fail); **(4) the home welcomes** — *"Welcome to CodeC"* with an invitation, and a self-check preview keeps its card (state, five lines, Skip) on screen instead of being covered; **(5) one conversation per project, session-only** — `chatsByProject` saves on the way out and restores on the way back (`A → B → A` no longer loses A), New chat clears only the current project, and **D6 does not move**: nothing is written to a file, store, log or backup, and the confirm dialog says so. The audit's three bugs are fixed, not reported: the self-check's fifth check could **never pass** (its fact was `agentRun != null`, a pause that every terminal path clears — a `RUN_REQUEST` timeline row is the latch now), the transcript block could render **12 084 chars against a 12 000 cap** (`frameChars` counts the closing line), and a refusal (`SELF_CHECK_BUSY`) was **invisible** while an answer was streaming or after a failure (the bar draws it then; FAILED draws its own; exactly one sentence per phase). **22 new host cases** (`Phase93WiringTest` 14, `StorageAccessTest` 8) plus six updated suites; the host sweep is **520 passed / 0 failed** across 70 classes, KDoc and docs updated ([`docs/phases/03-editor/chat-phase93/`](../phases/03-editor/chat-phase93/README.md)). **Build facts:** [`37303387512`](https://github.com/pabi277/CodeC/actions/runs/37303387512) **green on `dc96329`** — host unit + screenshot tests, debug and release assemble, the release-APK set check; release universal **7 194 500 B**, debug universal **27 119 376 B**, R8 mapping **71 627 297 B**, *release manifest: no `android:debuggable` flag*. Two rounds failed first and are recorded honestly: `37301197147` on a mixed-type `listOf` in `AiViewModel` (`String` + `File` is not a `List<String>`), and `37302637525` on **one test of 3347** — `SettingsSearchWiringTest` read the storage row's `facts.granted` as a use of an unrelated local named `granted`, now asked as `facts.state != StorageAccessState.MISSING`. The Settings quartet runs in the host sweep since, which is **70 classes / 520 passed / 0 failed**. **No PR, no merge, no `main` push** (`rule.md` §3); Level 12 stays not accepted (S2, T3-Gemini, T4-NVIDIA still await their own fix phases) and Levels 13–14 stay unauthorized.

**2026-10-04 — Phase 92 / AI — the self-check (one command instead of twenty rows): ✅ IMPLEMENTED on `arena/01a102bd-codec` — the owner: *"I am tired of testing — give some command and I will run and share what is wrong."*** So the app now judges itself. **The command:** ✨ AI sheet → **Self-check** → tap **Send** on each of the four previews it builds → **Copy report** → paste. **That is the whole round.** Five checks, each judged mechanically: **file access** (answers C8 with the real permission state and the exact Settings switch, no request at all) · **remember a code word** (**D4** — every check previews, nothing sends itself, and no fourth `client.stream(` site) · **fresh task?** · **follow-up uses it** (the C2/C7 proof: the report says whether the request carried `0` chars of conversation — *our* bug — or carried N chars and the model ignored them — *the model's*) · **edit proposal parses** (S2/N1, with the parser's own rejection reason) · **run request reaches the card** (N1). The report is **redacted by construction**: `AiSelfCheckObserved` has no answer field and no prompt field, the code word is checked where the answer lives and only a boolean travels, and it ends with *"No prompts, no answers and no keys are in this text."* (**D6**; clipboard only, **S6**). The same commit answers the **C9/C11** complaint with numbers instead of a look: the block's surface was the **bubble's own colour** — exactly **1.00:1** — so nothing showed in the dark theme; it is `surface` with an `outline` frame now, measured at **1.83:1** (dark, block vs bubble) and **5.41 / 4.44:1** (frame, both themes) by `AiCodeBlockContrastTest`. **34 new host cases** (19 + 10 + 5) — **75 passed / 0 failed** across the host suites. **No PR, no merge, no `main` push.** Docs: [`docs/phases/03-editor/chat-phase92/`](../phases/03-editor/chat-phase92/README.md). **Phase 92.1 — the owner's first round, the same day: "Not all test run."** His screenshot showed the card stuck on *Check 3 of 5* with checks 1–2 green and **no control on the card that could move the run on** — the Next button was drawn only *after* a step had worked, and a door that refused (an answer still arriving) said nothing at all. Fixed: the button is **never hidden** (*Next check* when the step has an answer to record, **Skip check** when it is still waiting for its Send), a skipped step is recorded as **not run** — reported as `not run`, never as `passed` — the run **carries itself** from verdict to verdict (the only tap left is the four Sends, **D4** unchanged, still three `client.stream(` sites), and a refusal now prints *"The previous answer is still coming in — tap again in a moment."* **39 host cases**, **80 passed / 0 failed** in the sandbox harness. Docs: [`chat-phase92/PART_92_1_NOT_ALL_TEST_RUN.md`](../phases/03-editor/chat-phase92/PART_92_1_NOT_ALL_TEST_RUN.md).

**2026-10-04 — Phase 91 / AI — the simple chat: ✅ IMPLEMENTED on `arena/01a102bd-codec` — the owner ran the Phase 90 round on his phone and this is his message turned into work.** His results: **passed** C1, C3, C5, C10, C12, C13, N2, N3; **failed** C2 (a follow-up the model answered *"You have not asked me anything prior to this current request"*), C4 (*"not possible it's just not doing that big work"*), C7 (follow-ups on both providers), C8 (*"no permission"*), C9 (*"no visible block"*), C11 (the block invisible in the dark theme), N1 (Continue / proposal edit / run approval); **not understood**: C6, C14. What is now done: **the Simple face** (a header toggle, **default Simple**, that hides the activity card, the numbers, the reviewer, the per-turn model labels, the counters and the preview's technical rows — never a control, and the exact text stays one **What will be sent** tap away, **D4**) · **Copy on every reply** (each assistant turn has its own Copy) · **New question is gone** — the composer sits under the answer, so the next question is just typing (**Clear** remains on a failure) · **the conversation is read as conversation** — the block now says what it is and **closes with *"End of the quoted conversation — continue it by answering the request that follows."*** · **the code block is visible in both themes** (its surface was the bubble's own `surfaceVariant`; now `surface` with an `outline` frame) · C4, C8 and N1 are **recorded with their evidence** in `chat-phase91/PART_91_2_FINDINGS.md`, not silently fixed. **10 new host cases** (`Phase91SimpleChatTest`) plus the updated Level 12 pin — **41 passed / 0 failed** in the sandbox harness; `client.stream(` = 3 and `openUri(` = 2 hold; the face is display state, never persisted (**D6**) and never part of a request (**S1**). **No PR, no merge, no `main` push.** Docs: [`docs/phases/03-editor/chat-phase91/`](../phases/03-editor/chat-phase91/README.md).

**2026-10-04 — Phase 90 / AI — the conversation surface: ✅ IMPLEMENTED on `arena/01a102bd-codec` — the owner said *"Go"* to the brief he asked for.** New chat, follow-up chats and the "real assistant" look, in three code parts plus the owner's checklist. **90.1** `ui/ai/AiChatSession.kt` — a pure, clock-free, bounded transcript (8 turns / 12 000 chars / 6 000 per long answer, oldest dropped with a visible marker, never the newest; protocol markers stripped so a replayed answer can never be re-parsed into an edit or a tool call; the S10 data-not-instructions sentence opens every block; **in memory only, D6**) · **90.2** the conversation surface — the sheet draws the turns with per-turn provider/model labels (**S8**), **＋ New chat** confirms before it clears, ✕ clear keeps the conversation, a project switch drops it, and the follow-up preview discloses the exact earlier-turns text it will carry (the same string the Send button sends, **D4**) — including agent tasks, whose first packed turn carries the block · **90.3** the look — code blocks now have a **frame, a header strip and a divider** (language + Copy, whole-block copy kept, a 200-line drawn cap with an honest note), plus bubble labels · **90.4** the owner-only [`DEVICE_ROUND.md`](../phases/03-editor/chat-phase90/DEVICE_ROUND.md) — C1–C14, the V1–V8 re-run and N1–N3, **nothing pre-ticked**. **30 new host cases** (`AiChatSessionTest` 14, `AiChatSessionWiringTest` 10, `Phase90DeviceTest` 6), **30 passed / 0 failed** in the sandbox harness; **Build APK green on head `2333cde`** (run `37208366826` — release 7 184 500 B, no `android:debuggable`); the wiring pins hold the three `client.stream(` sites, the two `openUri(` in `ui/ai`, no ceiling moves and no persistence of the conversation. **No PR, no merge, no `main` push.** Level 12 stays **not accepted** (S2, T3-Gemini and T4-NVIDIA failed; their fix phases are still unopened) and Levels 13–14 stay unauthorized. Brief and parts: [`docs/phases/03-editor/chat-phase90/`](../phases/03-editor/chat-phase90/README.md).

**2026-10-04 — Phase 89 / AI Level 12: ✅ IMPLEMENTED on `arena/01a102bd-codec` — the owner commanded *"Complete level 12"* the same day he briefed it. All four parts are written: **89.1** `AiLevel12InjectionTest` drives the S10 injection fixture **through the loop** (10 cases: a hostile answer that obeys the injection reaches only an approval pause, `request_run` is refused by the runner, no write tool exists, `.env` and the escaping symlink stay refused, the data-not-instructions sentence survives memory re-injection) · **89.2** the in-memory numbers readout `AiMeasurements`/`AiMeasurePolicy` plus the `ui/performance/HeapProbe.kt` boundary sample (15 + 7 cases plus 2 Robolectric decoder cases: unknown renders as *not reported*, never `0`; the D6 scan is clean; no ceiling moved) · **89.3** the 10-task × 2-model × 3-run matrix, **3/3 to pass**, in `DEVICE_ROUND.md` (5 contract cases) · **89.4** the refreshed owner round — and then **the owner ran it the same day and reported the results in chat (2026-10-04)**: Part 1 **19 of 21 rows pass, S2 failed** (*Unclosed `<<<SEARCH>>>` / `<<<REPLACE>>>` / `<<<END_SEARCH>>>` block in 'app.py'* — the guard held, no write happened, but the row could not be completed), **B2 NOT EXERCISED** (no answer was cut short, so no Continue bar appeared), and **P8 is filled for the first time since Level 10**; the matrix — all three runs per task, **48 of 60 runs** — is **14 rows pass at 3/3, 2 rows fail (T3-Gemini: cut off three times before any diff; T4-NVIDIA: repeats itself), T7 and T10 NOT EXERCISED**; the Gemini column ran on **`gemini-3.1-flash-lite`** (the owner kept it — a recorded deviation from the brief's pinned model); the 8 visual checks and Q1/Q2 were not reported. Everything is transcribed verbatim in [`DEVICE_ROUND_OWNER_REPORT_2026-10-04.md`](../phases/03-editor/chat-phase89/DEVICE_ROUND_OWNER_REPORT_2026-10-04.md), the round's cells are filled and marked *owner-reported*, and **acceptance is not declared**. **40 new cases (38 host-runnable + 2 Robolectric-only); the host-runnable AI suite is 620 pass / 0 fail** in the sandbox harness. **Build APK [`37185580349`](https://github.com/pabi277/CodeC/actions/runs/37185580349) is green on head `58039cd`** — release APK 7 179 540 B (+2 360 B vs `0fc2bfd`), debug 27 062 592 B, R8 mapping 71 172 428 B — after **two failed rounds**, both caught by CI alone (an NVIDIA usage-only null-ness regression, then two decoder cases that had to be Robolectric because `isReturnDefaultValues = true`). S10 is no longer the missing regression row. **No PR, no merge, no `main` push.** The owner's device round is reported (above) but **not accepted**: three rows failed (**S2**, **T3-Gemini**, **T4-NVIDIA**) and, per the owner's own rule, each becomes its **own fix phase** before Level 12 repeats — **no fix phase is opened yet; that needs the owner's command**, and the same command decides Levels 13–14, which remain unauthorized. Brief: [`docs/phases/03-editor/chat-phase89/`](../phases/03-editor/chat-phase89/README.md) — README plus parts 89.1–89.4, each with an implementation record. What the brief found stays true: the Level 12 spec's regression table was stale (**13 of its 14 rows were already covered** by nine Level 7–10 classes; only **S10** was missing) and the app measured nothing. S10 now has its test, and the readout renders first-token and total latency, provider-reported tokens and one heap sample — display state only (**D6**), never persisted.

**2026-10-03 — Phase 88 / AI Level 11: ✅ MERGED. [PR #113](https://github.com/pabi277/CodeC/pull/113) merged `arena/01a101db-codec` into `main` by the owner's explicit command (*"Ok merge to main"*), after the PR's own `build` check passed on the final head.** This entry was written just before the merge so it lands on `main` with it; the next change (2026-10-03) recorded both from the repository: **merge commit `0fc2bfd88d99d80ca72d61347dea9b36a4b82381`** (first parent `c3771c5`, second parent `06c1433`; merged 2026-10-03 16:21 UTC), post-merge Build APK run [`37136523881`](https://github.com/pabi277/CodeC/actions/runs/37136523881) **green** on that exact commit — release APK **7 177 180 B**, debug **27 054 824 B**, R8 mapping **71 186 679 B**. **`main` is now `0fc2bfd`.** That merge authorization was one-time and is complete. **Next free phase number: 89.** Before the merge it was ✅ IMPLEMENTED and CI-GREEN on the owner's command (*"Complete level 11"*): Build APK `37132310296` on `88f0186` is green on the first round. All five parts landed: Markdown answers (`AiMarkdown.kt`, `AiMarkdownView.kt`), `https`-only links behind a confirm dialog (`AiLinkPolicy`), one truthful progress line (`AiProgressPolicy`), and result rows that open to exactly the packed text (`AiResultRowPolicy`). The host harness runs 1 878 tests with 0 failures. Release APK 7 177 184 B: +22 756 B against `c3771c5`, +59 740 B against Level 6 (recorded in the [Phase 88 README](../phases/03-editor/chat-phase88/README.md)). **Next:** stop and wait for the owner's explicit command before opening any further level (`rule.md` §3). Levels 12–14 remain unauthorized; formal device acceptance remains POSTPONED to Level 12. The owner asked what Level 12 would do (discussion only, not authorized); a future brief should note that 13 of the 14 rows in its regression table are already covered by Level 7–10 tests, leaving **S10** (injection) as the new test.

**2026-10-03 — Phase 88 / AI Level 11: 📋 PLANNED — brief only, no code. The owner authorized Level 11 for a brief and answered the scope questions: the full level in one phase · `https` links only, behind a confirm dialog that shows the full URL (`javascript:`, `data:` and plain `http` stay inert) · the read-window default stays 400, closing Level 10's *Open decision 1*.** Brief: [`docs/phases/03-editor/chat-phase88/`](../phases/03-editor/chat-phase88/README.md) — README plus parts 88.1 (pure Markdown model) · 88.2 (link policy) · 88.3 (answer surface: formatted and selectable, code-block Copy, link confirm dialog, throttled off-main-thread parsing) · 88.4 (one truthful progress line; today two builders disagree on what "steps" means) · 88.5 (activity rows open to the exact `modelResult`, disclosure pins, APK deltas). Reading the tree corrected the Level 11 spec four times: defect 10's `AiChatSheet.kt:584` was already fixed by 87.5 · `MarkdownPreview.safeUrl` is a file-preview allowlist and must never be the AI link filter · there are no Roborazzi goldens to update (*Open decision 1*: none added; the visual check goes to Level 12) · the APK delta needs two baselines (Level 6 and current `main`). Presentation only: no ceiling moves, exactly three `client.stream(` sites, no new dependency, permission or endpoint. **No PR, no merge; implementation needs the owner's explicit command.** Levels 12–14 remain unauthorized; formal device acceptance remains POSTPONED to Level 12.

**2026-10-03 — Phase 86 / AI Level 9: ✅ MERGED. [PR #111](https://github.com/pabi277/CodeC/pull/111) landed on `main` @ `6838ea6cf72937766f4d92eb5e9729b71f86b9ee` by the owner's explicit command; post-merge Build APK run [`37109573383`](https://github.com/pabi277/CodeC/actions/runs/37109573383) is green on that commit (host unit/screenshot tests, debug+release assembly, APK checks, artifact uploads). No APK byte count is recorded for the merge run — it was not re-measured, and the Actions log and artifact-blob endpoints were unreachable from the sandbox. That merge authorization was one-time and is complete.**

**2026-10-03 — Phase 87 / AI Level 10: ✅ MERGED. [PR #112](https://github.com/pabi277/CodeC/pull/112) landed on `main` @ `c3771c58007f2f7ad4af6a26437dc19ef3819a3f` by the owner's explicit command (*"Ok merge it to main"*); post-merge Build APK run [`37122702615`](https://github.com/pabi277/CodeC/actions/runs/37122702615) is green on that commit.** All nine agent controls are now on `main`. No APK byte count is recorded for the merge run — it was not re-measured. That merge authorization was one-time and is complete. **Next free phase number: 88.** Levels 11–14 remain **unauthorized**; formal device acceptance stays POSTPONED to Level 12. One open decision travels forward: the read-window default shipped at **400** (today's effective window, so nothing changed on upgrade), while the Level 10 spec proposes 150 — a one-constant change whenever the owner asks. **Next:** stop and wait for the owner's explicit command before opening any further level (`rule.md` §3).

**2026-10-03 — Phases 84+85 / AI Levels 7+8: IMPLEMENTED and merged by authorized PR #110 to `main` @ `32e4a5f87a9f73874a4de17967187445c49a2461`; final-head Build APK `37084800939` green.** Level 7 agent correctness and Level 8 bounded-but-honest reads, `read_files`, restorable eviction, and S11 working set are recorded in [Phase 84](../phases/03-editor/chat-phase84/README.md) and [Phase 85](../phases/03-editor/chat-phase85/README.md). Prior host pre-validation records are in those ledgers. No device pass is claimed; formal device acceptance remains POSTPONED to Level 12. Phase 86 / Level 9 is now the authorized active phase; Level 10–14 remain unauthorized.


**2026-10-03 — Phase 83 / AI Level 6: test-only baseline, merged by PR #109 to `main` @ `392a614`; Build APK `37051539267` green on `cf3f1be`.** It added generated synthetic fixtures and offline measurement only, with zero production behavior changes. Formal device acceptance remains POSTPONED. Record: [Phase 83](../phases/03-editor/chat-phase83/README.md).

**Prior status — 2026-10-02 — Phase 82 / authorized 82B (Level 5A): IMPLEMENTED / code CI GREEN; current merge explicitly authorized, final-head checks pending; formal device acceptance POSTPONED until after optimization.** Owner: “The device test is postponed, 1st i will make it optimized than device test … Last merge it”. [Phase ledger](../phases/03-editor/chat-phase82/README.md) · [postponed checklist](../phases/03-editor/chat-phase82/DEVICE_ROUND.md) · [agent-core research](../research/AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md). Existing code has one cancelable identical-request rate retry, 32,768-token / 48,000-char replies, Continue 64,000/8 and manual encrypted NVIDIA dev/test-only BYOK; 3 VM stream sites and D1/D4/D5/D6 unchanged. Code CI 36995145462 on 7b2ecac and docs head CI 36996946243 on e0665cb green; local 462/462 + 13/13. No new app/test edits. Informal screenshot issues remain open, not a device pass. GLM-5.3 uses the existing editable model field; no default change. **Next:** authorized merge + main-CI bookkeeping → agree/start a full-file/batch-context and agent-reliability brief → optimize → owner device acceptance. Next free phase number **83**, not started; optional backup/reviewer routing, **5B/6+ not authorized**. No hidden provider switch or new tool/write/run permission. Earlier pending-device/merge summaries below are historical.

**2026-10-02 — Phase 81 (AI: Continue after cut off): ✅ DEVICE-PASSED & MERGED to `main` @ `e089880` via PR #106 (post-merge CI ✅ GREEN `36972776771`, release APK `7,104,312 B`; `chat-phase81/DEVICE_ROUND.md`).** Owner: *"Mark the docs device pass and merge it"*. Owner row: *"Add a continue open on Gemini so i can continue after cut off"* — the gap left by Phases 76–80, where a cut answer (`AiLimits.MAX_REPLY_CHARS = 24 000` in `AiPolicy.kt:32`, or the model's `finishReason = MAX_TOKENS` from `MAX_OUTPUT_TOKENS = 8 192` at `:40`, both collapsed into `cutShort` by `AiAnswer.kt:37,43` and shown as one line, `AiErrors.kt:72`) offered only Copy. Shipped: pure `AiContinuation` (`tailOf` = last 1 400 chars snapped to a line break, `…`-marked; `block` = the model-facing resume sentence plus that tail; `canContinue` / `remainingChars` / `requestBudget` / `limitNote`; bounds ≤ 8 continuations, ≤ 64 000 chars per visible answer, ≥ 600 chars of room to offer), `AiPrompt.continuation` so the follow-up text lives **inside the one user string** the preview renders and the request sends (D4 literal), `GeminiClient.stream(maxChars = …)` so one visible answer stays bounded, `AiViewModel.continueAnswer()` (builds a PREVIEW — no `client.stream`, no `viewModelScope.launch`), `send()` keeping the earlier text (`answer = base + text`) with the shared budget, the sheet's **Continue** button on a cut-off prose answer with the one-line reason whenever it cannot be offered, and `EditorScreen` wiring. `AiViewModel` still has exactly three `client.stream(` sites; agent tasks and edit proposals deliberately do not offer it (each gets a sentence); nothing new is persisted (D6). Tests: `AiContinuationTest` **12**, `AiContinueWiringTest` **7** (19 new), **260/260** host-harness cases green across 19 AI test classes. Branch CI **`36946839410` ✅ GREEN** on `9f9ffac` (release APK `7,104,312 B`, debug `26,831,328 B`); the first push failed on one real error — `BottomBar` had not gained the `onContinue` parameter (`AiChatSheet.kt:776`) — fixed in `9f9ffac`. Record: [`docs/phases/03-editor/chat-phase81/`](../phases/03-editor/chat-phase81/README.md); device rows: [`DEVICE_ROUND.md`](../phases/03-editor/chat-phase81/DEVICE_ROUND.md). **Next free phase number: 82.** Levels 5+ remain unauthorized.

**2026-10-02 — Phase 80 (AI Level 4, whole-project map + bounded tools + approved run loop): ✅ DEVICE-PASSED & MERGED to `main` @ `e089880` via PR #106 (post-merge CI ✅ GREEN `36972776771`, release APK `7,104,312 B`; `chat-phase80/DEVICE_ROUND.md`).** Owner: *"Mark the docs device pass and merge it"*. Owner authorized it as *"Start Phase 80 — full Level 4"* and answered the four scope questions explicitly: (1) **D4 amended (`task_preview`)** — one preview per task (question + whole-project map + tool allowlist + caps); after Send the read-only in-project steps run one at a time with a visible timeline and Stop and **no extra taps**, while **every run and every file apply keeps its own tap**; each turn's exact text is shown in the timeline. (2) **Run scope (`run_only`)** — only what RUN ▶ does (build + run of the project/active file), through the existing Output pipeline and runner guards, one at a time, each approved; the agent may stop a run it started; **no terminal typing, no package installs, no Git**. (3) **Flow scope (`both_flows`)** — *Ask about the project* and *Propose edits* both route through the agent when it is available, with the Level 2 deterministic path as the fallback. (4) **Caps (`looser`)** — ≤ 12 model turns, ≤ 24 tool calls, ≤ 2 runs, ≤ 8 000 chars per tool result, ~5 min wall clock, enforced by CodeC after every proposed call. Shipped: pure `AiRepoMap` (always-sent bounded map: 6 000 chars, 8 definitions per file, honest elision line, the AI's own secret/excluded filters re-applied), pure `AiTools` (`<<<CODEC_TOOL …>>>` protocol + `AiToolPolicy.validate` — unknown tools fail closed, any argument outside the call's allowlist is refused, paths must pass `AiEditProposalParser` **and** exist in the walk's admitted set), `AiToolRunner` (`java.io` only — `list_files` ≤ 200, `search_project` ≤ 40 hits/300 files, `read_file` ≤ 400 lines, 8 000-char results, dirty buffer wins over disk, `request_run` returns "not executed here" and cannot run anything), pure `AiAgentLoop` (budget/decide/stop-reasons/prompt packing — task+map in every request, newest 4 results, disclosure when older ones are dropped), pure `AiRunDigest` (exit codes + durations always, `[timed out]`/`[stopped by the user]` named, signal lines then tail, 6 000 chars), the agent loop in `AiViewModel` (third and only new `client.stream` site, private `agentTurn`, still behind the PREVIEW gate), `AiChatSheet`'s activity timeline + Run/Skip approval card + Stop, and `EditorScreen`'s `viewModel.runFile(context, target)` for the approved run with the digest built from CodeC's own `outputState`. Level 3 is untouched: agent edits still land in the same diff review card with per-file checkboxes, Apply and 1-task Undo. `ui/ai/` still contains zero direct file writes and zero command execution; nothing new is persisted (D6). Tests: `AiRepoMapTest` **12**, `AiToolProtocolTest` **16**, `AiToolRunnerTest` **13**, `AiAgentLoopTest` **19**, `AiRunDigestTest` **9**, `AiLevel4WiringTest` **14** (83 new), **241/241** host-harness cases green across 16 AI test classes; the two-stream-site pins in `AiLevel2WiringTest`/`AiLevel3WiringTest`/`AiHelperWiringTest`/`AiSurfaceWiringTest` were amended to three sites with a comment naming the Phase 80 owner decision. Record: [`docs/phases/03-editor/chat-phase80/`](../phases/03-editor/chat-phase80/README.md); branch CI **`36941118847` ✅ GREEN** on `c5dd30a` (release APK `7,101,196 B`, debug `26,826,600 B`; its `:app:testDebugUnitTest` step ran the six new test classes, and the first push `2367b9a` failed on one real compile error — missing `import kotlinx.coroutines.isActive` in `AiViewModel` — fixed in `bd3276e`); device rows: [`DEVICE_ROUND.md`](../phases/03-editor/chat-phase80/DEVICE_ROUND.md). **Next free phase number: 81.** Levels 5+ remain unauthorized.

**2026-10-02 — Phase 79 (AI Level 3, proposed edits, review, and undo): ✅ DEVICE-PASSED & MERGED to `main` @ `df2c5d4` via PR #105 (post-merge CI ✅ GREEN `36934250423`, release APK `7,079,820 B`; built on `arena/01a0f848-codec` @ `607ccb7`, branch CI `36898288949`; owner: *"All device tests passed"* → *"Ok merge it"*).** Owner answered all four scope and law-amendment questions in chat before coding: (1) **D1 amended (`amend_d1_keep_git_off`)** — AI may modify, create, or delete project text files ONLY after local diff review and user approval; run, terminal, packages, and automatic Git stage/commit remain prohibited; (2) **D6 amended (`nobackup_one_task_journal`)** — a 1-task preimage undo journal in `noBackupFilesDir/ai/undo/<project>/task.journal` (≤ 5 files, ≤ 256 KB total, excluded from Android Auto Backup and outside the project tree) survives process death and is deleted on undo, replacement task, project deletion (`ProjectManager.deleteProject`), or key deletion (`AiKeyStore.deleteKey`); (3) **Level 3 scope (`full_edit_create_delete_per_file`)** — `MODIFY` (`<<<SEARCH>>>` / `<<<REPLACE>>>` or full replacement), `CREATE`, and `DELETE` of project text files, with per-file checkboxes in the diff review card, baseline conflict checks before apply, and post-apply user-edit conflict checks before undo; (4) **Phase 78 bookkeeping (`fold_and_start_79`)** — folded PR #104 merge bookkeeping into Phase 79. Shipped: pure `AiEditProposal` + `AiEditProposalParser` + `AiUnifiedDiff` (`ui/ai/AiEditProposal.kt`, zero `java.io`/`android.*` imports; local diffs via `DiffEngine.compute`; path/secret/excluded-dir/baseline validation); `AiEditApplier` (`ui/projects/AiEditApplier.kt`, `java.io.File` only, minSdk-24 symlink checks, `ProjectPathUtils.resolveInside`, `GitDiscardEditors.blocks`, CRLF preservation via `LineEndings.toNative`, write-ahead 1-task journal, mid-batch rollback, byte-for-byte undo); `EditorViewModel.dirtyProjectBuffers()` + `syncAfterAiFileChanges(...)` (keeps open tabs, dirty flags, `FileTreeRepository`, and Git badges synchronized while preserving `EditorUndoManager` per-tab history); and a fourth chip *"Propose edits"*, per-file diff review card, conflict cards, and 1-task **Undo AI changes** card in `AiChatSheet.kt`. `ui/ai` still contains zero direct file-write calls and `AiViewModel` still has exactly two `client.stream(` call sites. Tests: `AiEditProposalTest` **26**, `AiEditApplierTest` **16**, `AiLevel3WiringTest` **16** (58 new), all 244/244 passing on the local kotlinc/JRE harness. Record: [`docs/phases/03-editor/chat-phase79/`](../phases/03-editor/chat-phase79/README.md); device rows: [`DEVICE_ROUND.md`](../phases/03-editor/chat-phase79/DEVICE_ROUND.md). **Next free phase number: 80.** Levels 4+ remain unauthorized.

**2026-10-01 — Phase 78 (AI Level 2, whole-project context): ✅ DEVICE-PASSED & MERGED to `main` @ `bd1aa06` via PR #104 (post-merge CI ✅ GREEN `36884609111`, release APK `7,055,936 B`); built on `arena/01a0f5d2-codec` (`8abd49e` round 1 CI `36822370600`, `e3932fb` round 2 CI `36877351748` send-arrow follow-up; owner: *"Yes device passed and merge"*).** Owner authorized AI Level 2 as the next phase and started it with *"Complete the lavel 2"*; he did **not** answer the brief's six scope questions, so each was taken at its most conservative option and recorded as **the agent's decision, not his** (Q1 no multi-turn · Q2 `MAX_CONTEXT_CHARS` stays 12 000 · Q3 secret-like files never offered, no opt-in · Q4 `.gitignore` out of scope · Q5 a third chip in the existing chat sheet · Q6 nothing new persisted). Shipped: pure `AiProjectFiles` (the AI's **own** file filter — `ProjectSearch.isSearchable` is NOT reused, because `ProjectFilesPolicy.kt:9-16` + `ProjectSearch.kt:172` admit `.env`/`.env.*`/`.npmrc` on three routes; deterministic keyword relevance, no embeddings; a packer that enforces the 12 000 ceiling on the **rendered body** and reports what did not fit); `AiProjectReader` (`java.io` only, so host-testable — canonical root containment, the minSdk-24-safe `canonicalPath != absolutePath` symlink test copied from `ProjectSearch.kt:181-182`, a 4 000-entry cap, a 512 KB read guard, and the dirty-buffer contract); `AiSource.PROJECT` + `AiContextBuilder.fromProject` + `AiViewModel.askProject` (ends in `preview()` — still exactly two `client.stream(` call sites); a third chip *"Ask about the project"* and a preview that names every file it sends, its line counts and what was left out. The file list travels **inside** `AiPrompt.userText`, so `GeminiRequest.body(prompt)` still sends exactly the two strings the preview renders — D4 holds with **no change** to the request, key or streaming path. Nothing new persisted (D6): no index, no seventh key. No new dependency/permission/DataStore key/Settings control. Tests: `AiProjectFilesTest` **28**, `AiProjectReaderTest` **14**, `AiLevel2WiringTest` **16** (58 new). Pre-validated in-sandbox on the kotlinc/JRE harness against the real compiled classes: 102/102 policy, 30/30 walker on a real temp tree with symlinks + `.env` + a 4 005-file cap case, 139/139 source pins through the real `RepoFiles.codeOnly` — catching one real compile error, one wrong test assertion and two wrong pins before CI. Record: [`docs/phases/03-editor/chat-phase78/`](../phases/03-editor/chat-phase78/README.md); device rows: [`DEVICE_ROUND.md`](../phases/03-editor/chat-phase78/DEVICE_ROUND.md) (**C4/C5 — the credential row — must not fail**). **Round 1 follow-up (same day, owner device report):** *"if i question anything it's saying select some code in the editor"* — the sheet's ➤ arrow was hardcoded to `onExplainSelection`, so a typed question with nothing selected hit `fromSelection`'s blank-selection refusal (`AiContext.kt:122`) and dead-ended; the arrow now follows the question (`if (hasSelection) onExplainSelection(question) else onAskProject(question)`), the stale `SELECTION_HINT` names both routes, and a new `AiLevel2WiringTest` case pins it (142/142 pins locally). **Next free phase number: 79.**

**2026-10-01 — Phase 77 (AI UI for phones): ✅ DEVICE-PASSED & MERGED on the owner's command (*"Ok all good you can merge it"*); built on `arena/01a0f452-codec`, CI ✅ GREEN `36784685021` on `3661264` (round 1 `36784200759` red for cause: `MotionWiringTest` banned a `snap(` helper name → `snapToEdge`), device round passed, see `chat-phase77/DEVICE_ROUND.md`, NOT merged (owner: start Phase 77).** ✨ rail slot is now AI home only (`AiHome`: setup, model, Test connection, Delete key, *Show AI button*, *Open AI chat*); a draggable edge-snapping AI bubble (`AiBubblePolicy` + `AiFloatingButton`, position/visibility in `no_backup/ai/ai_settings.properties` — `bubble_pos`, `bubble_show`, no DataStore key); a HIDDEN/HALF/FULL chat sheet (`AiSheetPolicy` + `AiChatSheet`) reusing `OutputPanelHeight.resolve`, one question at a time, Back through `BackRouter` (new row 0); "Explain with AI" on the Output header after a failed run. **Both Output-conflict variants stay** (A = half sheet replaces Output, B = chat opens FULL): owner at the merge gate — *"Stay both user can select whatever they want"* — so it is a saved ✨ home setting (`sheet_with_output` in `ai_settings.properties`, no DataStore key). **"Ask AI" in the selection menu was SKIPPED** — Sora 0.24.6 `EditorTextActionWindow` has no add-item API (see PART_77_3). `AiViewModel`'s request logic, `AiContextBuilder`, `GeminiClient` unchanged. Tests: `AiBubblePolicyTest` 14, `AiSheetPolicyTest` 12, `AiSurfaceWiringTest` 12, `BackRouterTest` +4, `BackHandlerWiringTest` +2 pins; moved pins in `AiHelperWiringTest`, `SidePanelWiringTest`, `EditorRowsWiringTest` (strip/status gates gained `!aiSheetOpen`). Pre-validated 86/86 on the local kotlinc harness (pure policies + source-pin tests; Compose files only compile in CI). Record: [`docs/phases/03-editor/chat-phase77/`](../phases/03-editor/chat-phase77/README.md). **Next free phase number: 78.** 

**2026-10-01 — Phase 76 merged; Phase 77 📋 BRIEFED (docs only) — AI UI for phones.** Owner: *"For phone use space management is 1st priority … the 3 ber ai place only for full setup ai but chat will be a floating botton … one click to open full chat"*; answers: no follow-ups yet (*"just making the ui future pruff"*), remember the button position (*"Yes i think"*), build both Output-panel variants and pick on device. Plan: ✨ rail slot → AI home (setup/model/test/delete, Show AI button, Open AI chat); draggable edge-snapping AI bubble (only after setup, never over keys/keyboard); bottom chat sheet HALF/FULL/minimized reusing the Output panel's height rules; "Explain with AI" on a failed run. Brief: [`docs/phases/03-editor/chat-phase77/`](../phases/03-editor/chat-phase77/README.md). **Next free phase number: 78.** Start 77 only on the owner's command.

**2026-10-01 — Phase 76 (AI Level 1): ✅ DEVICE-PASSED, NOT merged.** Owner: *"Every test passed just i have to use gemini-3-flash-preview this model"*. All device rows passed; the pre-filled model is now `gemini-3-flash-preview` (was `gemini-3.8-flash`, which did not answer for his key). Next: merge only on the owner's explicit command. Levels 2+ remain unauthorized.

**2026-09-30 — Phase 76 (AI Level 1, read-only Gemini helper): 🚧 IMPLEMENTED, CI ✅ GREEN `36755705695` (round 2; round 1 `36755116931` red for-cause — one `TypeAdoptionTest` monospace pin), device round owed, NOT merged (owner: *"Start lavel 1"*).** The fifth side-panel rail slot (reserved for AI since Phase 55) now opens **AI helper · Gemini**: bring-your-own key behind the 18+/Google-terms gate, key encrypted with Android Keystore AES-256-GCM in `no_backup/ai/` (no plaintext fallback), Explain selection / Explain last error, a word-for-word preview before every request, streaming answer with Stop, Copy answer only (no apply/run), nothing saved, `store:false`, projects only (never single-file mode). No new dependency, permission, DataStore key or Settings control. Record: [`docs/phases/03-editor/chat-phase76/`](../phases/03-editor/chat-phase76/README.md). Next: the owner runs [`DEVICE_ROUND.md`](../phases/03-editor/chat-phase76/DEVICE_ROUND.md); merge only on the owner's command. Levels 2+ remain unauthorized.

**2026-09-30 (later) — AI Level 0: ✅ COMPLETE (docs only).** The owner answered the three open items. O1: show Google's Gemini terms and require an 18+ confirmation at key setup (no provider change; the owner is in India). O2: technical design delegated to the agent: `HttpURLConnection`, stateless `streamGenerateContent` with `store:false` on every request, `org.json`, Keystore AES-GCM, no new dependency. O3: a pre-filled Flash model field plus Test connection. Record: [`00_LEVEL0_DECISION_RECORD.md`](../roadmaps/ai-integration/00_LEVEL0_DECISION_RECORD.md) §4. **Next: a Level 1 brief (read-only Gemini helper) as a numbered phase, only after the owner's explicit start command.**

**2026-09-30 — AI Level 0 (product boundaries): STARTED, docs only (owner: *"Start level 0"*).** Owner answers: read-only first release; Google Gemini with the user's own key; key encrypted with Android Keystore; preview and confirm every request; open CodeC project only (no single-file mode, no shared-storage folders); conversations not saved. Record: [`docs/roadmaps/ai-integration/00_LEVEL0_DECISION_RECORD.md`](../roadmaps/ai-integration/00_LEVEL0_DECISION_RECORD.md); current-main recheck and Gemini research: [research dossier, Addenda A–B](../research/AI_INTEGRATION_RESEARCH_20260930.md#addendum-a--current-main-recheck-2026-09-30-1785b92). **Open before Level 1 can be briefed:** O1 Gemini terms (18+, "not for consumer use", EEA/UK paid-only), O2 HTTP client (recommend `HttpURLConnection`, no new dependency), O3 default model. **No app code, dependency, permission or privacy-guide change is authorized; Level 1 needs its own explicit start command.**

**2026-09-30 — AI integration proposal: DISCUSSION / RESEARCH ONLY.** The owner wants an incremental path toward a whole-project coding agent, BYOK multiple API providers/models, optional on-device inference after a device check, and undoable agent edits. Research and CodeC source map: [`docs/research/AI_INTEGRATION_RESEARCH_20260930.md`](../research/AI_INTEGRATION_RESEARCH_20260930.md). Staged proposal and separate level specs: [`docs/roadmaps/AI_INTEGRATION_ROADMAP.md`](../roadmaps/AI_INTEGRATION_ROADMAP.md). **No AI implementation is authorized or scheduled; wait for the owner to select and start a level.**

**2026-09-30 — Phase 75.1–75.3 editor typing reliability: ✅ COMPLETE, DEVICE-PASSED & MERGED via PR #98.** Owner tested the 75.3 build (`26dbf9e`, **Build APK ✅ GREEN `36701799600`**, 7m 48s, release artifact `6,292,527 B`, debug artifact `25,440,267 B`) on device and reported: ***"Ok device test passed … You can complete the docs part and merged"***. Fixed across 75.1–75.3: (1) *Clicking a suggestion writes only the suggestion word* — removed 75.2's Python-only `keywordMoment` suppression from `CodeCompletionEngine.completions` (which hid chips when `< 2` candidates remained on `StripContext` while leaving C `if`/`for` and partial prefixes inserting full multi-line skeletons) and added `CodeCompletionEngine.suggestionInsertText(item)` used by both `EditorViewModel.acceptCompletionItem` (strip chips) and `CodeCLanguage.requireAutoComplete` (`⌄ more` panel). Code snippets resolve to their suggestion word (`if`, `def`, `for`, `while`, `switch`, `try`, `class`, `printf`, `print`, `return`, `import`, `main` / `int main`, `else if`, `typedef struct`), while single-line `#include` directives (`#include <stdio.h>\n`, `#include <>`), shebangs (`#!…`), HTML tags/DOCTYPE (`<…>`), Markdown/CSS, and Emmet expansions keep their `insertText`. (2 & 3) *Live-buffer strip commits + `SoraEditorHost` selection/cursor sync + `{}` outside empty `(|)`* — `EditorKeyCap`, `RunKeyCap`, and `SuggestionStrip` now wrap their `.pointerInput` callbacks in `rememberUpdatedState`, and `EditorKeysRow` + `BottomStrip` (`EditorScreen.kt`) pass `commitEditorKey = { key -> viewModel.applyEditorKey(key, autoIndent = autoIndent, tabSize = tabSize, suppressAutoPair = true) }` so tapping quick keys applies against the live `_codeText.value` instead of a stale `textFieldValue` captured when `EditorKeysRow` first mounted (`""` erasing `#include <stdio.h>` or `"int main(|)"` inserting `{}` inside `()`); `SoraEditorHost` ignores `SelectionChangeEvent.CAUSE_TEXT_MODIFICATION` (which fired in `CodeEditor.afterInsert` before `pushToVm` and shifted `old.selection` ahead by 1 whenever any char followed the caret, breaking `SmartTyping.handleTypeOver` on `int main(|)` + `)`) and ignores `SelectionChangeEvent` when `syncedText != viewModel.codeText.value.text`, and checks `cursorDrifted` (`ed.cursor.left != start || ed.cursor.right != end`) in `AndroidView.update` so `ed.setSelection` always restores Sora's cursor after `ed.text.replace`; and both `EditorKeySet.apply(EditorKey.Pair("{", "}"), ...)` and `SmartTyping.handleAutoPair` + `handleBraceInEmptyParens` step `{` / `{}` past `)` when the caret sits inside empty `(|)` (`int main(|)` → `int main(){|}`). Record: `docs/phases/12-ui-polish-program/PHASE_75_1_EDITOR_TYPING.md`.

**2026-09-30 — Phase 75.2 editor typing device round 1 follow-up (owner 5-item report): 🚧 IMPLEMENTED, device round 2 owed, NOT merged.** Owner tested the 75.1 build on device and reported five concrete issues: (1) *"If i write def it's auto completes it def fname(): pass it's not phone friendly because i have to cut that and again write another thing"*, (2) *"Int main(){ not auto indenting"*, (3) *"None of is working Indentation"*, (4) *"I don't run other that much but {} are not indenting i also tryed java same"*, and (5) *"One more problem is user can't line up the space/indenting between lines so add some line type some thing that indicates each Indentation"*. Fixed all five from source: (1) *The Python keyword moment is quiet* — `SmartTyping.typedBlockKeyword(word)` shares `pythonBlockKeywords` with `opensPythonBlock`; when `language == LanguageType.PYTHON` and the word at the caret is an exact block keyword (`def`, `for`, `if`, `elif`, `else`, `while`, `class`, `try`, `except`, `finally`, `with`, `async`, `match`, `case`, with or without trailing space), `CodeCompletionEngine.completions` offers no snippet at all (not on the strip, ghost, or panel), while partial prefixes (`de`, `fo`) and explicit abbreviations (`deft`, `defm`, `ifmain`, `pr`) keep the full snippet pack. (2, 3, 4) *Brace opener indents by one full level on both Enter routes* — `CodeCLanguage.indentAdvanceFor` now returns `val level = indentStep.coerceIn(2, 8)` for `trimmed.endsWith('{')` as well as Python block headers (75.1 had left the brace delta at its historical `1` space as a found-not-changed note, which at 16 sp looked like no indentation at all in C, C++, Java `.java`, JS/TS, Go, Rust, and Shell). (5a) *Visible indentation marks* — `SoraEditorHost` enables Sora's native non-printable leading-whitespace painting (`CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE`) in the one-time `remember(editor)` block and sets `EditorColorScheme.NON_PRINTABLE_CHAR` to `CodecPalette.INDENT_MARK` (`0x80B0B0B0`) inside `LaunchedEffect(theme)` so every leading space (including on empty auto-indented lines) paints a subtle alignment dot across all themes without touching inner or trailing spaces. (5b) *One Tab unit aligned to tab stops* — `SmartTyping.indentRun(text, caret, step)` advances to the next multiple of `step` (`step - (width % step)`) inside leading indentation and a full `step` elsewhere, shared by `SmartTyping.handleTabAsIndent` (rewriting a naive `\t` insert) and `EditorKeySet.apply(EditorKey.Tab, ...)`. Tests updated/added across `CodeCLanguageLogicTest`, `CodeCompletionTest`, `CompletionCapacityTest`, `SmartTypingTest`, `EditorKeySetTest`, and `EditorTypingRouteWiringTest` (9 wiring pins). Pre-validated with Python mirror (`/tmp/verify75_2.py`, 68/68 checks pass). **Build APK ✅ GREEN round 1 `36692498787` on tip `e5d66a8`** (11m39s, host unit + screenshot tests, debug + release assemble, weight check, zero error annotations; release artifact 6,291,637 B = +110 B over 75.1 run `36679767045`). Record: `docs/phases/12-ui-polish-program/PHASE_75_1_EDITOR_TYPING.md` §"Phase 75.2".

**2026-09-30 — Phase 75.1 editor typing reliability (owner's "find the problems … and fix"): 🚧 IMPLEMENTED, device round owed, NOT merged.** The dedicated typing phase the owner asked for, worked from source, not from a guess. *`def` indents / `for` does not:* the two Enter routes never saw each other — `CodeCLanguage.getIndentAdvance` called `indentAdvanceFor` **without the language**, so Sora's Python delta was always 0 (`DirectAccessProps.autoIndent` is true, so the IME's newline is Sora's), and `SmartTyping`'s own rule only fires when the buffer grew by exactly one character — true for a `def` at column 0 (it copied no indent), false one level down (the copied indent made the change 5+ chars). Fix: the adapter now carries language + caret column + the indent step (kept equal to `setTabWidth` by the host), and the block test has ONE owner, `SmartTyping.opensPythonBlock` — the 14 Python block keywords, trailing `#` comment removed first (`for i in x:  # walk` indents), strings and ordinary colons rejected by the first-word rule. *Backspace ate the whole indent:* Sora's `deleteEmptyLineFast` (true by default, never overridden) answers one press on a line that holds only indentation with "the indent **plus** the line break above" (`CodeEditor.deleteText`, read from the 0.24.6 tag). Fix: the host sets `deleteEmptyLineFast = false` and pins `deleteMultiSpaces = 1`, and `SmartTyping.handleIndentBackspace` corrects any multi-space deletion inside leading indentation to exactly one character — selections, code, line joins, empty-pair `(|)` and trailing spaces are left as they arrive; CodeC Keys' ⌫ flick-up (`DeleteWord`) opts out by name, because that key means "a word". No new preference/toggle/dependency; the sora→VM replay path untouched. Tests: `SmartTypingTest` (+14 cases), `CodeCLanguageLogicTest` (the `indentAdvanceFor` pin moved with its reason; brace delta pinned *unchanged*, per the brief — the 1-vs-a-level brace unit is recorded as found-not-changed for the owner), new `EditorTypingRouteWiringTest` (6 source pins on the seam). **Build APK ✅ GREEN round 1 `36679767045` on tip `5acac43`** (base `main` @ `dba1361`): host unit + screenshot tests, debug + release assemble, the weight check, zero error annotations — one round, nothing stale to move; release APK 6,291,527 B = +1,077 B / +0.02 % over the post-#96 run `36675799563`. Before the run, every new and changed assertion was mirrored in Python (72 checks, all pass) — recorded as pre-validation, never as CI's substitute. No device claim — the owner's round is the next step and the §3 merge gate stands (no PR, no merge, until he commands it). Record: `docs/phases/12-ui-polish-program/PHASE_75_1_EDITOR_TYPING.md` §"Implementation record".

**2026-09-30 — Phase 74.1 Settings search follow-up (owner authorized): IMPLEMENTED; PR #96.** Adds safe searchable aliases for the three bespoke forms (Extra-Key shortcuts, repository trust/status, GitHub credentials); no entered values or secrets are indexed, and the audited 64 real Settings rows are unchanged. Existing defaults/fold behavior remain. Build APK round 1 `36673923025` exposed an incorrect expected count (`repository` matches both the trust form and GitHub's repository-content-permission descriptor); corrected. Round 2 ✅ GREEN `36674331533` on `f29f3f3`. Merged to `main` via PR #96 at `cdc6c93`; post-merge Build APK ✅ GREEN `36675799563`. Phase 65 behavior unchanged. Typing fixes remain reverted and Phase 75.1 remains proposal-only/unstarted.

**2026-09-29 — Phase 73.9 (manual-first no-remote flow, origin by default) ✅
IMPLEMENTED, CI ✅ GREEN `36597542971` on `e4f8d6a`.** Owner premise fix on the 73.8 build: most users
will not give their token the repo-create permission, so Publish fails for them — "focus no remote
user have to 1st create a repository and paste its link in remote and set the name at origin default.
Other things are ok." No scope questions (the flow was prescribed exactly). Shipped: NO_REMOTE message
rewritten (create first, paste the link, origin default); readiness row guides step 1 via a
github.com/new link and step 2 via an ADD REMOTE button opening the Remotes dialog (PUBLISH gone from
the row); New Remote name starts at `origin` unless taken; `GitHelpLink` gains a label param;
`actionId` → new `ACTION_ADD_REMOTE`, dead `ACTION_PUBLISH_REPO` const deleted. Publish dialog/engine
untouched for permissioned tokens (after-push card door). Tests: Phase40 remedy pin + new Panel
manual-first case. Python mirror of every assertion + CI as the compiler (green first round). Record:
`docs/phases/12-ui-polish-program/PHASE_73_9_MANUAL_FIRST_REMOTE.md`. Owner device pass ✅ 2026-09-29 (*"Everything looks good"*). **Merged** with 73.1–73.9 together via [PR #95](https://github.com/pabi277/CodeC/pull/95) (the stray `projects/.git` cleanup retired with the pass).

**2026-09-29 — Phase 73.8 (git page owns credentials, the install card, and beginner hints) ✅
IMPLEMENTED, CI ✅ GREEN `36593360018` on `aa8912d`.** Owner punch-list on the 73.7 build, verbatim gist:
push-menu trigger should be ⋮ (the share glyph reads as "share this file"); the install "is just a
status bar" — want "a card saying installing git like in package part, will show live % … and will
show live line. If failed then failed and retry. But not redirected to terminal"; the token flow must
live "in the git page"; and the git UI is "hard but ok" for newcomers. Asked five scope questions
before coding; owner locked all five: package-style install card, ⋮ trigger, repoint every stale
Settings/Modules/Terminal text, three inline hints, clone dialog opens credentials inline. Shipped:
`GitInstallGuidance` → `GitInstallCard` (userland live state while the gate refuses — Terminal tab's
own stage words, real download % only, gate's refusal sentence; git's install once allowed — elapsed,
live transcript line, in-box fail tail; no fake %, no Terminal redirect); MoreVert trigger; readiness
NO_TOKEN row grew a GIT CREDENTIALS button; clone hint stacks `GitCredentialsDialog` above its draft;
`GitReadiness`/`GitErrors`/publish/push-outcome texts all repointed; one grey hint line on UNSTAGED,
in Commit, in Push. Tests: install-wiring rewrite + guidance/clone/hint/trigger pins; `GitErrorsTest`
pins follow the rewords. No JDK/SDK in the sandbox — verified with a Python mirror of every
new/changed assertion (all pass) + CI as the compiler (3 rounds: TerminalStatusLabel import +
duplicate @Composable, then one missed pin, then green). Record:
`docs/phases/12-ui-polish-program/PHASE_73_8_GIT_PAGE_OWNS_IT.md`. Owner device pass ✅ 2026-09-29 (*"Everything looks good"*). **Merged** with 73.1–73.9 together via [PR #95](https://github.com/pabi277/CodeC/pull/95) (the stray `projects/.git` cleanup retired with the pass).

**2026-09-29 — Phase 73.7 (git moves into the editor panel: Spck-ditto dialogs, no sheets) ✅
IMPLEMENTED, CI ✅ GREEN `36583980074` on `0b57c0a`.** Owner follow-up on the 73.5/73.6 builds with 8 Spck screenshots attached
(branch menu, Git Credentials, Remotes, New Remote, detach-HEAD confirm, Commit All, empty Remotes,
Push): git must live inside the editor's side panel (the REPOSITORY tab) Spck-ditto — no bottom
sheet anywhere — with Spck's Commit All and Push dialogs, centered Remotes/Log dialogs, a count
badge on the rail's branch brand/icon, and the push menu's Provider row opening Git Credentials. Asked
four scope questions before coding; owner locked all four: full-panel, ditto-dialogs, all-dialogs,
provider-row (Publish stays the permission-asked direct-create path; manual paste-a-URL stays
primary). Shipped: `GitControlSheet` → `GitControlPanel` hosted in the drawer slot (73.6 slot UI
+ engine + sheet doors deleted); new `GitCommitDialog` (credentials row, message, store-owned
author name/email, Stage All default ON) and `GitPushDialog` (remote/branch dropdowns; engine
`pushCapturing` gained `remoteName?`); Remotes/Log/BranchSwitch converted to centered dialogs;
rail badge; hub ⋮ → editor via a new optional `panel=repository` route arg; 11 dead strings
deleted, 13 added. Tests: new `GitPanelWiringTest.kt` (8 cases) replaces
`GitDrawerInstallWiringTest.kt`; Parity/Spck/discard/hygiene/route pins migrated; Install and
Refresh suites untouched. No JVM/kotlinc in the sandbox — verified with the brace/paren scan +
a Python mirror of every new/changed assertion (all pass) + a stale-pin sweep. Record:
`docs/phases/12-ui-polish-program/PHASE_73_7_GIT_PANEL_MOVE.md`. Owner device pass ✅ 2026-09-29 (*"Everything looks good"*). **Merged** with 73.1–73.9 together via [PR #95](https://github.com/pabi277/CodeC/pull/95).

**2026-09-29 — Phase 73.6 (editor-drawer git: full-GUI Initialize + ask-first install prompt) ✅
IMPLEMENTED, CI ✅ GREEN `36566495685` on `65d34a2`.** Owner device report on the 73.5 build, verbatim: "if git is not
installed it will install automatically in the background with status bar but it's not happened
… when i click on initialize it opens terminal … So where is gui?" — followed by: install must
show an option "git is not installed do you want to install it than start install". Reading the
code first: there are TWO initialize doors and 73.1–73.5 only converted one — the sheet's button
runs the background engine, but the drawer's Repository Initialize still closed the drawer and
typed `git init` into the visible shell, which runs in the projects folder (Terminal's default
cwd), never checked git was installed, and landed the repo at `projects/.git` on `master` (the
owner's transcript: "no git command" first, then a repo in the wrong folder with default-branch
hints — the engine's `-b main` shows neither; the REPOSITORY header was never seen because the
drawer path never opens the sheet). Shipped: drawer Initialize now runs the engine's `init` in
place (`EditorScreen.runDrawerInit`: scratch-mode toast / no-manager → install prompt with the
root kept / already-a-repo → refresh + open the sheet / init → refresh + open the sheet /
failure → classified toast); a shared `GitInstallPromptDialog` ("Git is not installed — …
Do you want to install it now?", INSTALL/Cancel) shown from the drawer flow AND the sheet's
INSTALL GIT button (supersedes 73.2's "no confirmation dialog" decision by the owner's explicit
ask-first instruction); the drawer's Repository slot gained the install/progress/retry/init-busy
states reusing the exact Packages-tab mechanism (gate + `sendCommand` + `PkgResult`/
`InstallOutcomes` polling), with a gate-refused plain message (73.2 kept); installs started from
Initialize auto-continue the pending init on success. Not touched: Run-in-Terminal hand-offs (a
legitimate terminal use) and the Modules/Packages rows. The stray `projects/.git` is owned by
one Terminal command from the default prompt (`rm -rf .git`) — nothing in the app auto-deletes
it. Tests: new `GitDrawerInstallWiringTest.kt` (6 cases) + `GitInstallWiringTest.kt` rewritten to
pin the ask-first sheet button. No JVM/kotlinc in the sandbox — verified with the Kotlin
state-machine brace/paren scan (all six touched files balanced) + a Python mirror of every test
assertion (caught two edit-tool tail-garbage glitches and four silently un-applied edits
pre-commit). One CI fixup round (four missing `EditorScreen.kt` imports — `65d34a2`).
Record: `docs/phases/12-ui-polish-program/PHASE_73_6_GIT_DRAWER_GUI.md`. No device pass; none
claimed. Owner device pass ✅ 2026-09-29 (*"Everything looks good"*). **Merged** with 73.1–73.9 together via [PR #95](https://github.com/pabi277/CodeC/pull/95).

**2026-09-29 — Phase 73.5 (Git panel: Spck-exact layout, install status bar, inline credentials) ✅
IMPLEMENTED, CI ✅ GREEN `36560766234` on `d469d52`.** Owner-reported directly (not a discussion draft), same 4 Spck
screenshots 73.3 ported the menu *items* from — this phase ports the *layout*: fully
terminal-independent Git GUI. Asked four scope questions before coding; owner answered: (1)
**full Spck-style restyle** (REPOSITORY header + 3 icons, two menus exactly like the
screenshots, UNSTAGED section with count badge; commit box + changes + pull/refresh stay
below); (2) **Commit All = commit only, no push** (new path; big button unchanged); (3)
**Provider opens the same credentials dialog** as Git Credentials; (4) **all 4 credential
fields** (nothing lost). Shipped: Spck-exact header (branch menu = Branches/Remotes/Log
History/Refresh Files; push menu = BRANCH header + Commit All/Revert All/Checkout
Commit/Fetch/Pull/Push/Git Credentials/Provider), branch chip on its own row below;
UNSTAGED collapsible + count badge + header search filter (display-only); install status
bar (elapsed seconds + indeterminate track — `pkg` reports no percentage — success
refreshes in place, `ENDED_WITHOUT_INSTALL` now shows failed + RETRY instead of silently
reverting); new `GitCredentialsDialog.kt` (screenshot 2's shape: provider row with GitHub
as the honest single entry, Manage link to Settings, 4 fields, "Create a GitHub Token."
link, Cancel/Ok — same `GitCredentialsStore` Settings uses, save refreshes readiness);
new `commitOnly` (same guards + `stageAll` choke point as commit-and-push, no push —
existing unpushed section offers the follow-up PUSH). Deliberately NOT ported: screenshot
4's `+` stage-all (73.1 proved a stage control here is a no-op dressed as an action) and
screenshot 3's shortcut hint (no `Ctrl+Enter` handler exists in this sheet). Supersedes
two 73.3 decisions: the single overflow menu (split into the two screenshot menus, every
action kept) and credentials-as-Settings-jump (inline dialog now; only Manage still
jumps). Session branch `arena/01a0eca9-codec` was fast-forwarded to `arena/01a0eb2d-codec`
tip `5e242f9` first (identical content), then this phase on top. Tests: new
`GitSpckPanelWiringTest.kt` (8 cases), parity/install/discard wiring tests updated (see
record). No JVM/kotlinc in sandbox — verified with a Kotlin state-machine brace/paren
scan + a Python mirror of every test assertion before pushing (caught two edit-tool
glitches pre-commit). One CI fixup round (backtick test name with an illegal `:` —
`d469d52`). Record: `docs/phases/12-ui-polish-program/PHASE_73_5_GIT_SPCK_FULL.md`. No
device pass; none claimed. Owner device pass ✅ 2026-09-29 (*"Everything looks good"*). **Merged** with 73.1–73.9 together via [PR #95](https://github.com/pabi277/CodeC/pull/95).

**2026-09-29 — Phase 73.4 (device fix: git-refresh mis-reported "installed" after any error) ✅
IMPLEMENTED, CI ✅ GREEN `36551505966` on `b49e726`.** Owner device report, verbatim: "Auto install not
working"; follow-up, after asking where: tapped Install Git in the Git panel, then "I click the
initialize in the repo no installed git" — the Source Control sheet was offering 73.3's
"Initialize repository" button on a project where git was never actually installed, so the
owner never even saw the Install Git button. Root cause: `GitControlViewModel.refresh()` wrapped
manager-acquisition and git-status in ONE try/catch whose catch unconditionally set
`gitInstalled = true` and never touched `isRepo` (left stale at the `UiState` default, `false`,
for a fresh sheet) — so ANY exception (including one from `ShellBootstrap.prepare()` writing
profile scripts, nothing to do with git) was reported as "git is installed, something else
broke", landing exactly on `gitInstalled = true, isRepo = false` — the "Initialize repository"
state. Fixed: manager acquisition is now its own try/catch reporting `gitInstalled = false,
isRepo = false` on failure (routes to Install Git instead of a dead-end Initialize button); the
git-status catch now re-derives `isRepo` with a fresh, plain filesystem check instead of leaving
it stale. New test: `GitRefreshStateWiringTest.kt` (5 cases, source-scan style, every assertion
hand-verified against the real file with a Python mirror of Kotlin's substring semantics before
being trusted). Record: `docs/phases/12-ui-polish-program/PHASE_73_4_GIT_STATE_FIX.md`. **This does not
claim to know why the underlying exception fired on the owner's device** — if git still will not
install after this fix, the next step is a `pkg install -y git` run captured directly from
Terminal (exit code + output) to read the real failure. **CI ✅ GREEN `36551505966`.** No
device pass; none claimed. Owner device pass ✅ 2026-09-29 (*"Everything looks good"*). **Merged** with 73.1–73.9 together via [PR #95](https://github.com/pabi277/CodeC/pull/95).

**2026-09-29 — Phase 73.3 (Git menu: full GUI parity with Spck) ✅ IMPLEMENTED, CI ✅ GREEN
`36544645579` on `84b8d79`.** Owner-reported directly, attached 4 Spck Editor screenshots, verbatim:
"Now git is fully depend on terminal but want it to be gui not a cli. Mean everything will be
from buttons no need terminal for that (expect git install). Like spck i also attache some
screenshot for help. Analysis all the screenshot carefully every small details must be note."
Screenshots analysed in full: a branch-icon dropdown (Branches/Remotes/Log History/Refresh
Files — Branches already had full parity via `BranchSwitchSheet`) and a push-icon dropdown
(Commit All/Revert All/Checkout Commit/Fetch/Pull/Push/Git Credentials/Provider — Commit/
Push/Pull already existed). Missing: standalone Fetch, Log History, Checkout Commit, Revert
All, a general multi-remote Remotes screen, a Git Credentials shortcut, and `git init` (the one
remaining CLI-only escape hatch for a folder that isn't a repo yet). Asked three scope
questions before coding; owner answered: (1) **full parity** — port the whole menu, not just
git-init; (2) **Revert All needs a confirm dialog** (bulk discard of staged+unstaged tracked
changes, untracked files untouched — "this cannot be undone"); (3) **one shot**, this session,
not staged across chats. This supersedes 73.2 decision #3 for the not-a-repo case specifically:
`git init` is now a real button, not wording-only (the plain-words rewrite itself stays, minus
the now-redundant "type git init in the Terminal" sentence). Shipped:
`GitManager.init/checkoutCommit/revertAllChanges/removeRemote/remotesDetailed/log` (+ new pure
`GitLog.kt`: `GitCommitEntry`/`GitLogParser`/`GitRemoteEntry`); 10 new `GitControlViewModel`
functions, all through the existing `runGitOperation`/hand-rolled-`launch` shapes already used
by every other button in the sheet; a new header overflow menu (Fetch/Log History/Checkout
Commit/Remotes/Git Credentials/Revert All) visible once git+repo are both ready; the branch
chip is now a `when` (a detached `HEAD` from Checkout Commit used to make it vanish silently —
now shows a distinct amber chip using Phase 17's previously-unwired `git_detached_head`
string); a new `GitInitGuidance` composable (real init button, matching 73.2's
`GitInstallGuidance` shape) replacing the not-a-repo's old text-only guidance; two new sheets
(`GitLogSheet.kt`: shared Log History/Checkout Commit list, checkout confirms first in plain
words; `GitRemotesSheet.kt`: list + add + remove, remove confirms first); Revert All's own
confirm `AlertDialog` (non-dismissable, matches the per-file discard dialog's shape); "Git
Credentials" as a menu shortcut into the existing `SettingsScreen` credentials editor (reuses
both call sites' existing `onOpenSettings` — no second editor built). Out of scope, flagged not
silently skipped: Spck's Provider item (GitHub-only app, nothing suggests another provider),
the tree's search-files box (unrelated to git), a dedicated Log History diff view (the existing
per-file diff view already covers inspecting changes). New tests: `GitManagerTest.kt` extended
(fake-git-script `init`/`remote`/`log` cases; happy paths + input-validation rejections for
every new engine method), `GitLogParserTest.kt` (new, pure parser tests), `GitGuiParityWiringTest.kt`
(new, source-scan wiring pins — no Robolectric render exists for this sheet), and
`GitInstallWiringTest.kt` updated for the superseded not-a-repo test. No JVM (`java`) or
coroutines/JUnit jars were available in this fresh sandbox this session (confirmed via `find /`
and blocked apt/curl network checks) — unlike 73.1/73.2, no local kotlinc syntax check was
possible; verified instead by line-by-line manual review, production-file brace/paren-count
sanity checks (balanced), and a direct `grep -cF` confirmation of every source-scan test's
literal anchor against the real file before trusting it. Without that local check, the first
push surfaced two real mistakes: `AlertDialog`'s `dismissOnBackPress`/`dismissOnClickOutside`
needed `properties = DialogProperties(...)`, not direct named args (compile failure, run
`36538343931`, fixed `5b38694`), and a `strings.xml` wiring-test substring check needed a
`substringBefore` to stop it capturing unrelated later strings that also said "Terminal" (test
failure, run `36538858396`, fixed `84b8d79`). **CI: ✅ GREEN `36544645579`** on tip `84b8d79`.
Record: `docs/phases/12-ui-polish-program/PHASE_73_3_GIT_GUI.md`. No device pass; none claimed.
Owner device pass ✅ 2026-09-29 (*"Everything looks good"*). **Merged** with 73.1–73.9 together via [PR #95](https://github.com/pabi277/CodeC/pull/95).

**2026-09-29 — Phase 73.2 (Git connection: auto-install, clear errors, new-user guidance) ✅
IMPLEMENTED, CI ✅ GREEN `36523739665` on `2f6bf55`.** Owner-reported directly (not a discussion draft), verbatim: "The git
connection in the editor part make it properly working: 1. If git is not installed auto install
1st. 2. Clear error massage that can be read by normal users. 3. What to do add for new users."
Reading the code first: the not-installed state was one static sentence with no action; error
classification (`GitErrors.classify`) already maps ~12 raw git failures to plain-English messages
with a next step each (found already good, left unchanged); the not-a-repo message named a fix
without ever saying what Git/Source Control is for. Asked three scope questions before writing
code; owner answered each: (1) auto-install = a one-tap **Install Git** button, not silent — no
install happens without a tap, background progress, auto-continues on success; (2) if the Linux
userland itself isn't ready yet (rare), keep today's "go to Terminal" message unchanged — do not
chain into the big userland bootstrap from the Git sheet; (3) new-user guidance = better wording
only, no new buttons. Shipped: the Source Control sheet gets a `TerminalViewModel` handle via the
existing `activityTerminalViewModel()` helper and reuses the exact Packages-tab install mechanism
(`sendCommand("pkg install -y git")` + polling the pure, already host-tested
`PkgResult`/`InstallOutcomes`) gated by the same `SetupGatePolicy.can(INSTALL_PACKAGE, ...)` the
Packages tab uses — allowed → new `GitInstallGuidance` (explainer + INSTALL GIT button +
installing/failed states, stays in the sheet and refreshes in place on success, unlike a Packages
row); refused → today's message unchanged. `git_not_a_repo_message` rewritten in plain words
(states what Git does before naming the same two existing recovery paths); no new action added
there. New tests: `GitInstallWiringTest.kt` (source-scan style, matching `GitDiscardWiringTest`'s
precedent — no Robolectric Compose render exists for this sheet). Pre-validated with a local
kotlinc 2.4.20 + JRE 25 syntax check (no parse errors; unresolved-reference cascades only,
expected without the Android/Compose classpath). Record: `docs/phases/12-ui-polish-program/PHASE_73_2_GIT_INSTALL.md`.
**CI: ✅ GREEN `36523739665`** on tip `2f6bf55` (the first real Gradle compile/run of the changed
files). No device pass; none claimed. Owner device pass ✅ 2026-09-29 (*"Everything looks good"*). **Merged** with 73.1–73.9 together via [PR #95](https://github.com/pabi277/CodeC/pull/95).

**2026-09-29 — Phase 73.1 (Source control and GitHub) ✅ IMPLEMENTED, CI pending.** Verified state
first: `main` @ `69c4b5316b3f2072bed05f96e22698b4d26da9d0` (PR #94, Phase 71.1 + the pinch-zoom
follow-up), CI green, PRs #83/#42 untouched. Owner picked **73.1** from the offered drafts (65.1,
73.1, 74.1). Reading `GitControlView.kt`/`GitControlViewModel.kt` end to end found: no
staged/unstaged grouping (one flat "Changes N" list); and, more materially, the per-file +/−
stage toggle drew the identical brand/icon whether a file was staged or not and had **zero effect** on
what got committed — `commitAndPush` always `git.stageAll()`s before `git.commit()` (confirmed one
call site), matching the existing "what will be committed" preview, which already assumes
add-everything. Asked three questions (focus, the stage-toggle fix, whether to check the
COMMIT & PUSH button's hardcoded lavender colour for contrast); **owner answer verbatim: "Your
choice"** for all three — every decision below is recorded as the chat's, not the owner's. Chat's
choices: beginner-focused (matches the brief's own starting recommendation); keep "stage
everything" as the one-tap model and remove the now-misleading per-file toggle from ordinary
change rows (Mark Resolved on conflict rows is untouched — it calls `git.stageFile` directly and
is a real action); measured the button's contrast (7.58:1 enabled, passes AA/AAA; its disabled
state is WCAG §1.4.3-exempt) and left it unchanged per the Phase 40.5 colour law ("values that
already pass are not restyled"). Discard safety, push readiness/help-link, conflicts and the push
result card were reviewed and are untouched — they already do what the brief asked for. Shipped:
`GitChangeRow`'s trailing control now renders only for a conflict row (Mark Resolved);
`GitControlViewModel.toggleStage()` removed (its only caller was gone). Tests:
`GitDiscardWiringTest` amended (`existing git affordances still delegate to the one engine` drops
the removed pin) + new case `the ordinary change row has no stage toggle, only Mark Resolved does`.
Pre-validated with a local kotlinc 2.4.20 + JRE 25 syntax check (no parse errors; unresolved-
reference cascades only, expected without the Android/Compose classpath) — a syntax reading, not
CI. Record: `docs/phases/12-ui-polish-program/PHASE_73_1_GIT.md`. **CI: ✅ GREEN `36519004263` on `9acfa34`**
(12 m 17 s) — the first real Gradle compile/run of the changed files. No device pass; none
claimed. Remaining discussion drafts: 65.1, 74.1. Owner device pass ✅ 2026-09-29 (*"Everything looks good"*). **Merged** with 73.1–73.9 together via [PR #95](https://github.com/pabi277/CodeC/pull/95).

**2026-09-29 — Phase 71.1 (Packages, installation and Terminal) ✅ COMPLETE — owner device-tested and MERGED on
his command** (*"Device test passed record everything and merge with main"*; no device/OS/build named). Owner said "71.1 start"; asked four questions
(`detect_exit`, ⬇ `confirm`, `name_in_toast`, `review_more`), then reported three device problems
and gave latitude (*"Whatever you want to do it better you can do"*): a package pin 📌, extra
terminal lines after opening/closing the keyboard, and "after userland install users must run
`pkg update` but don't know". Shipped: `pkg` writes a result file the Packages row reads (a failed
install becomes RETRY, no stuck INSTALLING); ⬇ asks first; the session is named in toasts and the
top bar; pin-to-top favourites (DataStore `pinned_packages`, agent's design); `pkg` refreshes the
index itself when there are no lists; the terminal buffer no longer keeps phantom blank rows on a
shrink/grow (source-level cause only — not proven to be the device cause). Record and evidence
split: `docs/phases/12-ui-polish-program/PHASE_71_1_PACKAGES_TERMINAL.md`. **CI:** ✅ GREEN `36511809977` (an earlier run
`36511449206` was red for cause on one stale look-back window in `SetupGateWiringTest`). Follow-up in the same PR: the pinch-zoom gap (owner screenshot) — `Reflow` lost the cursor when it trimmed the blank after `codec $`, and the blank screen tail pushed the prompt into history (CI ✅ `36513501892`). Remaining drafts: 65.1, 73.1, 74.1 (68.1 merged in PR #90).

**2026-09-29 — HEAD: the 70.1 + 72.1 branch is ✅ OWNER-TESTED and MERGED to `main` on the
owner's command → [PR #93](https://github.com/pabi277/CodeC/pull/93).** Owner, verbatim: *"Yes all
test passed complete docs and merge to main"* — covering round 5's four rows (the Projects card
tap, the preview console with the keyboard up, Network on a real page, RUN ▶ output height) and the
editor row (`p`, tap `print(`, `r` → `print(r`); no device, OS or theme named, and the records say
exactly that. What went to `main` in that one PR (29 commits, 49 files): Phase 72.1 (the five-tab
preview console), Phase 70.1 (the run Output Panel), preview render rounds 1–4 (the `WRAP_CONTENT`
→ zero-layout-height cause; the page-box instrument line stays), round 5 (the phone pass; the
project card and Create open the editor, ⋮ → Browse files is the tree), and the composing-replay
fix in `SoraEditorHost` (TROUBLESHOOTING §47). Last code commit `308441e`, CI ✅ `36467347590`;
docs stamps `1b68266` (CI ✅ `36468730204`) and this completion commit (its run is recorded in the
PR). The PR records the merge commit. **Next chat:** `prompt.md` carries the handoff — the polish
series' remaining discussion drafts are **65.1 shell/navigation, 68.1 editor chrome/tabs, 71.1
packages/terminal, 73.1 git, 74.1 settings/support**; the owner names the next one (or reports a
bug); nothing is started on the agent's own. Two older PRs from other sessions were open at merge
time (#83 `arena/01a0c4cb-codec`, #42 `arena/01a062f7-codec`) and were left alone — the owner's call.

**2026-09-29 — the accepted suggestion that the next letter undid (outside the 70/72 phase;
the owner spotted it after accepting round 5).** Owner: *"in a python code I write p, it shows
print(, I click it and it is on the screen — then I write the next letter and print( is gone,
only p and the letters I typed"*. Cause, read in sora 0.24.6 and reproduced against its real
classes: the system keyboard was composing the word `p`; the accept's replay (`Content.replace`,
the §44 delta path) inserted `rint(` at the end of sora's composing range, `shiftOnInsert` grew
the range over it, and the IME's next `setComposingText("pr")` replaced the whole range. Fix in
`SoraEditorHost`: sora's own bracket for an edit while composing — `restartInput()` before the
delta and after the selection, gated on `hasComposingText()` so CodeC Keys / hardware typing pay
nothing. Tests: `ComposingReplayTest` ×4 (Robolectric, real sora), `ReplayPathWiringTest` +1.
Records: `TROUBLESHOOTING.md` §47, `phases/11-phone-ui-parity/chat-phase48/PART_48_1` follow-up. **CI: ✅ GREEN — `Build APK`
`36467347590` on `308441e` (host suite incl. the four `ComposingReplayTest` cases, 10 m 52 s;
debug APK 26 171 304 B). No device pass** at the time — the owner types `p`, taps `print(`, types
`r` on the system keyboard: the line must read `print(r`; then the same with the ghost, and a
keys-row `(` after a word. *→ ✅ passed, 2026-09-29 (*"Yes all test passed"*); merged via PR #93 — see the head entry.*

**2026-09-28 (round 5) — the phone pass for 70.1 + 72.1, and the project card that opens
the editor.** Owner: *"when I click on a project it should open the editor by default and not
the file structure"*; *"make everything from this and only this phase … phone friendly … the
console input etc. is not looking good on phone"*. Measured on his 411 × 656 dp page area: the
tools panel's Console tab spent **209 dp of its 240 dp default on its own rows** (one line of
output; none with the keyboard up), the Network table gave Name ≈ 107 dp under a ≈ 90 dp note,
Elements lost the tree after one tap, the run-output panel's 220 dp left 2–4 lines. Asked first
(four questions): card tap → editor **and Create too** (supersedes 66.1's "Keep today: hub file
tree"); **two-line rows**; the two height rules left to this chat (*"I don't know"*, *"Your
choice"*). Shipped: `openInEditor` in the hub (tap = `OPEN_IN_EDITOR` + haptic, ⋮ → **Browse
files** = the tree, `hub_open_in_editor` gone); the tools panel re-fitted (strip = drag handle,
labelMedium tabs + 48 dp ×, one-row `›` command line with Cancel · Execute inside, level-coloured
auto-following console lines, two-line Network/Resources rows via pure `nameLabel` /
`requestSummary` / `resourceSummary`, collapsing notes, an Elements details header that splits the
tab, no six-column header); heights: tools panel default **½ the page area** and, while typing,
the keyboard slides *under* a panel and page that keep their size (`imeDp` added back,
`pageReserve`, panel drawn over the page); run output opens at **40 % of the screen**
(`OutputPanelHeight.defaultFor`). Tests: `PreviewToolsLayoutTest` +2, `PreviewToolsPolicyTest` +1,
`PreviewToolsWiringTest` +1, `OutputPanelStatusTest`/`OutputPanelWiringTest`/`HubDialogWiringTest`
amended. Record: `docs/phases/12-ui-polish-program/chat-phase70/README.md` §Round 5. **CI: first run red on one Robolectric
case (the 320 × 470 dp default display, fixed with `w411dp-h820dp`), second killed by a corrupt NDK
download on the runner; third run `36453466081` on `0535c0f` ✅ GREEN. No device pass** at the time — the owner installs the debug APK of that run and looks at: the Projects card tap, the
preview console with the keyboard up, Network with a real page, RUN ▶ output height. *→ ✅ passed, 2026-09-29 (*"Yes all test passed"*); merged via PR #93 — see the head entry.*

**2026-09-28 (round 4) — the modal that collapsed: the cause was ours, and it had been
there since Phase 61.** The owner's Code-with-C page with a card open: a full card in
Samsung Browser, a **42 px strip** in CodeC (the pill and the ×, nothing below) under a
page-box line that read healthy. The card is `max-height: 88vh` in border-box sizing with
20 px vertical padding — `88vh → 0` is exactly padding + border = 42 px. `vh` was 0 because
**Chromium's WebView forces a zero layout height whenever its layout params say
wrap-content** (`AwLayoutSizer.updateLayoutSettings()` →
`setForceZeroLayoutHeight(isLayoutParamsHeightWrapContent())`, read from source this round),
and **Compose's `AndroidView` stamps `WRAP_CONTENT` on a bare view** (`AndroidViewHolder`
→ `addView(view)`) while still measuring it `EXACTLY` — so the view was truly 411×656 dp,
`innerHeight` (the *visual* viewport) said 655, and every `vh`/`height:100%` (the *layout*
viewport) resolved to 0. Fixed: `PreviewWebView` sets `MATCH_PARENT × MATCH_PARENT` in its
constructor. Instrument: the per-load line now also prints what the page's CSS gets for
`100vh` (`· 100vh 655 px ·`), and `PreviewToolsPolicy.layoutHeightCollapsed` (pure: `100vh`
shorter than half the page's own `innerHeight`) turns a collapsed answer into a console
**warning**. Rounds 1 and 2 are re-read honestly in the chat README: `html{height:100%}` → 0
and `calc(100vh − 320px)` → floor are this same quirk; the page-side changes stay, the
headless-Chrome reproductions explained the shape, not the device's cause. Tests:
`PreviewWebViewTest` +1 (Robolectric), `PreviewToolsWiringTest` +1, `PreviewToolsPolicyTest`
+1 and the page-box cases on the four-field wire format. **CI ✅ `36436647849` on `146986b`.
No device pass; no render this round (no Chromium in the sandbox, and desktop Chrome cannot enter the WebView's wrap-content
mode).** The owner re-installs: the line should read `100vh 655 px`, the card should open whole.

**2026-09-28 (round 3b) — the owner's console lines came back and settled it.**
Four loads of his page in a 411×656 dp box: **three laid out 411 CSS px at scale =
dpr (the browser's own 1:1) and one laid out 457** — 1.112× the view, drawn at 0.9×
(10 % small). That single bad load is a page laid out for a box it does not have,
because Chromium decides the page's scale before the page's `viewport` meta is in
effect. Fixed in `c954599` (**CI ✅ `36421203364`**): `PreviewToolsPolicy.boxMismatch`
(pure — device-width pages only, zoom-aware, 4 dp tolerance, never for a page that
declares its own width) and `PreviewWebView` loads the page **once more** when it
fires, with the box and the meta both known, logging the reason to the console. The
app's own loads re-arm the check; the correction never re-arms itself, so it cannot
loop. **A Phase 61 pin was reversed with its reason in the test** — `PreviewToolsWiringTest`
previously pinned `assertFalse(native.contains("reload()"))`; the red round `36420734801`
is that pin working. Tests: `PreviewToolsPolicyTest` 19→21, the amended
`PreviewToolsWiringTest`. **No device pass**; the owner re-installs to confirm.

**2026-09-28 (round 3) — the snake problem is confirmed gone; a third-party page
is next, and it is being measured before anything changes.** The owner cloned
[`priyajitpaul4-cmyk/Code-with-C`](https://github.com/priyajitpaul4-cmyk/Code-with-C)
into CodeC and sent Samsung Browser vs CodeC again: *"Snake problem is gone but
still it have problem like the screenshot see in browser it opens a wide window
but in CodeC it's very small."* Two shapes produce that and need opposite fixes —
a layout **wider than the phone** (wide-viewport fallback, squeezed) versus a
**smaller scale** (right layout, drawn below density) — and both pages involved
declare a valid viewport, so (`e436473`, **CI ✅ `36417473156`**) the per-load console
line now carries every number that separates them:
`page box 360×619 CSS px · meta width=device-width, initial-scale=1.0 · view
360×430 dp · scale 3 · dpr 3` — the page's own box, its own `viewport`
declaration, the view's box, and the scale read back from the WebView's
`getScale()` (which should equal `dpr`). Reading it: `page box` ≈ the view's dp and
`scale` ≈ `dpr` ⇒ the preview is 1:1 like the browser and only this app's chrome
remains; `scale` ≈ 1 against `dpr` ≈ 3 ⇒ the initial-scale recipe; `page box` ≈ 980
in a 360 dp box ⇒ the wide-viewport path. Exhibit (a simulation, not a phone
measurement): [`phases/12-ui-polish-program/chat-phase70/render-fix/third-party/`](../phases/12-ui-polish-program/chat-phase70/render-fix/third-party).
**Nothing was changed on a hunch** — the owner's next console paste picks the fix.

**2026-09-28 (round 2) — "better than before but still i don't think it's good
enough. Because browser have a good view and code is very smaller view."** The
owner's second screenshot showed the same snake page, un-clipped but **small**: my
round-1 page capped the board by the reported viewport height
(`calc(100vh - 320px)`), and CodeC's preview box is shorter than a browser window
once the app bar, the address row, the keys row and the nav bar have taken their
height — so the board fell to **200 px** where the browser shows **380 px**.
Reversed in `0ca08dc` (**CI ✅ green `36409241166`**): the board is width-driven again
(`min(100%, 420px)`) and a short box scrolls like a browser (measured in headless
Chrome: 360×520 → 328 px board; 412×915 → 380 px, nothing to scroll); the screen
stops reserving the keyboard's inset unless the tools panel — the only text field
on it — is open (the band under the page), and the two taps that take that field
away (Close, a tab switch) release focus so the keyboard leaves with it
(`1906b08`, **CI ✅ green `36411874083`**); and every load now logs the page's own
box to the console (`page box W×H CSS px · view W×H dp · dpr N`,
`PreviewToolsPolicy.pageBoxScript`/`parsePageBox`/`pageBoxLabel`), so this
ambiguity is measurable next time. Renders:
[`phases/12-ui-polish-program/chat-phase70/render-fix/side-by-side-round2.png`](../phases/12-ui-polish-program/chat-phase70/render-fix/side-by-side-round2.png).
Tests: `PreviewToolsPolicyTest` 17→19, `PreviewToolsWiringTest` 8→10,
`FirstOpenSampleTest`'s viewport case rewritten (it now forbids `100vh`/`100dvh`
in the page). **Headless Chrome evidence only — no device pass**; N1 still open.

**2026-09-28 (later) — the snake sample came back clipped inside the preview, and
is fixed (`ced2821`, **CI ✅ green `36404697868`**, and `36404764414` on the record commit).** After the report above, the owner sent
two screenshots of the *same* page — Samsung Browser showed it whole, CodeC's Web
Preview showed the arrow pad near the top, the hint under it and a long empty
band, with the header, the score and *Tap to start / START* off-screen — and
said: *"the better one is in browser and other is from code c preview correct it
CodeC preview sucs"*. **Reproduced in headless Chrome from the seed page itself**
(360×520 CSS px — the preview box minus CodeC's bar and keys row): the legacy page
put the header at **−53 px** and the board top at **−18 px**, with `scrollHeight`
562 over an `innerHeight` of 520 — the overflow sits *above* the box, so no scroll
can reach it. Cause: `height:100%` + `justify-content:center` on the body — a page
that fixes its own height cannot survive a box shorter than its column. Fix: the
page grows (`html{height:100%}` + `body{min-height:100%}`), its square board is
capped by `width:min(100%, 420px, calc(100vh - 320px))` with a 120 px floor, the
browser-default paragraph margins are gone and `touch-action:none` moved from the
body to the board (so a too-short page can still scroll); and `WebPreviewScreen`
now waits — **bounded at 1 s** — for a measured page box before its first load, so
no page takes its first layout against 0×0. Renders:
[`phases/12-ui-polish-program/chat-phase70/render-fix/`](../phases/12-ui-polish-program/chat-phase70/render-fix). Pins: `FirstOpenSampleTest`
+1 case, `PreviewToolsWiringTest` +1 case. **Evidence is headless Chrome on this
machine — not a phone**; a page a user writes that fixes its own height will still
clip in a box shorter than it needs, exactly as in a short browser window. N1 stays
open, and nothing here is a device pass.

**2026-09-28 — Phase 70.1 + 72.1 (one chat, preview first): the Web Preview's
five-tab console and the run Output Panel's state word, Stop, error cards and
console line — implemented on `arena/01a0e704-codec` (tip `d86b4a3`), owner
answers taken BEFORE any code.** The two sentences from the owner, verbatim:
*"Try to make output exactly same for web view"* and *"And terminal output is
good enough but is hard to understand the error line from terminal"*, with six
shots of SPCK's Web Preview in `uploads/` — a **preview** reference, not an
editor-panel one. Six questions were put before anything was written; all six
answers are recorded verbatim in
[`PHASE_72_1_PREVIEW.md`](../phases/12-ui-polish-program/PHASE_72_1_PREVIEW.md): surface =
**"Both, one after the other"**, console = **"The whole strip, Elements
included"**, viewport = **"Yes — fix the tiny rendering in the same part"**,
error line = **"The run output panel's"**, order = **"Both in this chat, preview
first"**. **72.1 (commits `090596e`, `2a707be`):** the strip is now the five tabs
the shots show, the console takes commands (`PreviewConsolePolicy`: echo → one
policy-built `eval` line → LOG/ERROR result, 4096 cap, Cancel · Execute, IME
Send), the Network table's Method/Name come from `shouldInterceptRequest` and its
Type/Size/Time from the page's own Resource Timing entries joined by redacted
address (`PreviewToolsPolicy.merge`) while **Status stays "—"** because WebView
cannot see a response, Elements is a read-only DOM walk into `window.__codecDom`
(caps 300 nodes/12 depth/80 text/40 attrs/4000 HTML, highlight + copy), Resources
and Settings (zoom, resolution, fit, viewport outcome, clear cache) are real
tabs, the bar carries **"Preview · N % Zoom"** and the console toggle, and a page
with **no** viewport meta gets a phone-sized default appended — a page that
declared one is left byte-for-byte as authored. Phase 61's "native never
evaluates" pin was reversed **with its reason in the test**: scripts are still
only the policies', `shouldInterceptRequest` still fetches nothing, and the view
still has no `openConnection` and no `reload()`. **70.1 (commits `11345c0`,
`2590429`, `3ad83c7`, `d86b4a3`):** all six review findings closed — the header
is now `OutputPanelStatus.head()` (waiting > busy/installing > serving > FAILED →
Failed(run) > CANCELLED → Stopped > DONE, where `finishFailedBuild`'s stored DONE
with a non-zero `buildExitCode` reads **Failed**), an empty expanded panel says
so, the 36 dp actions became a 48 dp status row and a 48 dp action row led by the
**labelled Stop**, the terminal palette is pinned by `OutputPanelContrastTest`
(all ten line colours and seven header tints ≥ WCAG AA), the run keys survive the
stdin yield via `KeysStayPolicy.isRunStripVisible` + `stripVisible`, and the
height law is `OutputPanelHeight.resolve` (**55 % / 38 % with the IME, floor 160,
default 220**). The error line the owner could not read is now two rows —
location + severity, then the message, with the existing *Add missing ;* fix —
under a `3 errors · 1 warning` banner from `OutputPanelStatus.counts`. **N2 is
delivered too:** the panel's one line (`submitInput(context)`) is a waiting
program's stdin, otherwise a console command run in the app's real shell
(`ShellBootstrap.prepare` + PTY-first `InteractiveRunSession.start`), workdir =
active project root else the single-file folder, 4096-char bound, and **no second
job on an occupied runner** — a line typed while busy is refused, never queued.
**CI ✅ GREEN — `Build APK` run `36399610563` on `d86b4a3`, 2347 tests, 0 failed,
debug + release APKs built**; every red round in between was fixed for cause
(`is OutputHead.Done` in four `when`s, the `motion` hook in scope, `counts()`
taking `List<String>`, a policy test comparing merged rows with unmerged ones,
and a cap assertion pressing the 55 % cap from *below* it). **Open:** **N1 — the
"above ber" is still unanswered** (no shot identifies it, nothing designed);
Elements stays read-only; Network's Status stays "—". **No device evidence** —
nothing here has been on a handset, and the five owed 69.1–69.4 rows stay owed.
No PR, no merge, no `main` push without the owner's explicit instruction.

**2026-09-28 — Phase 69.4 (the Files tree remembers its shape; the route's file
opens once per editor session): implemented on `arena/01a0e49f-codec` (tip
`e7e420e`), owner answers taken BEFORE any code.** Two owner reports, verbatim:
*"When i import a zip or repository and open in editor it will in collapse state
and remember what open by the use when leaving and again open the editor and the
project in the same position no all expend or collapse"* and *"If i run a file
but it is not in 1st of the editor and back from preview it again opens the 1st
file on the editor not the file i opened"*. What the reading found: (1)
`EditorViewModel._collapsedDirs` is session-only — no storage anywhere — and
three places reset it to `emptySet()` on leaving a project, which in this code
means "expand everything", so a fresh import opened as a wall of folders and no
shape survived leaving the editor; (2) `EditorScreen`'s open effect keyed on the
route's three arguments and so re-ran on every re-composition, and the route
names the file the editor was **entered** with (opening a file from the drawer
does not navigate), so every return from the Web Preview re-activated that first
tab — and reset its caret, and re-pointed "open where I left off" at it. Owner
answers: **A — a project opened for the first time starts with every folder
closed, only the top level listed** and **A — remembered per project, and it
survives closing the app**. So: a new `FileTreeMemory` (one `SharedPreferences`
file `codec_file_tree`, one key per project, deliberately mirroring the existing
`EditorLaunchState` store) plus the pure half in `FileTreeCollapse`
(`encode`/`decode`, `initialTree` = remembered-or-everything-closed, `prune`);
`refreshFileEntries` applies it and the five user-driven mutators (chevron,
Collapse all, Expand all, the reveal, a rename's remap) are the only writers —
the project-switch resets are left alone, and deleting a project from the hub
forgets its entry. One rule CI round 2 added for cause: a shape the user already
chose (a nested file's reveal, which lands before the drawer's first listing)
outranks the first-open default — `treeStateTouched`, cleared by
`resetTreeShapeForNewContext` at the three switches. And the route: a new pure
`EditorRouteOpen` — *a route is opened once per editor session* — with the
marker in the ViewModel, so it survives a rotation and dies with the tabs.
Sixteen new host cases (`FileTreeCollapseTest` +4, `EditorRouteOpenTest` new 4,
`FileTreeStateWiringTest` new 5, `EditorRouteWiringTest` new 3). **Android CI
green on the tip — run 36386089320, 10m12s, 2306 tests, both APKs (debug
26,027,944 B / release 6,798,904 B)**; round 1 (36383822168) was a missing
import, round 2 (36384352636) was the reveal/default conflict above plus one
wrong expectation of mine, round 3 (36385643910) was one stale pin. **No device
evidence claimed** and no round asked for; the two things only his phone can
confirm are that the tree comes back in the same position after leaving the
editor and after closing the app, and that returning from the Preview keeps the
file he opened. No new dependency, permission, screen, row, button or setting —
one small `SharedPreferences` file and no telemetry. **No PR and nothing
merged.** Brief with the verbatim answers:
[PHASE_69_4_TREE_AND_ROUTE.md](../phases/12-ui-polish-program/PHASE_69_4_TREE_AND_ROUTE.md)

---

**2026-09-28 — Phase 69.3 (the strip beside the ☰ panel closes it; a drag never
types a key): implemented on `arena/01a0e49f-codec` (tip `df6c654`), owner
answers taken BEFORE any code.** Two owner reports, verbatim: *"The 3 ber open
the editor but it have a gap side of that make it if user clicks the empty space
it will close the 3 ber"* and *"The quick keys are sensitive even i want to drag
for other keys it's types which ever i am scrolling"*. What the reading found:
(1) the ☰ panel is 85 % of the width and leaves a 15 % strip of live editor, and
Phase 55 had written that strip's tap off to Material3's modal scrim **read out
of Material3's source, never off a phone** — the Phase 55 device round was never
run, and the owner's phone says the tap never closed anything; (2) three rows
scroll (keys, run keys, suggestion chips) and all three carried their own copy
of a **20 dp** "this was a scroll" threshold while the row itself starts
scrolling at the platform's **touch slop (8 dp)**, so every drag between the two
scrolled the row AND typed the cap it started on — with two accomplices, the
arrows' 150 ms hold-repeat firing into slow drags and the loops discarding the
up change. `KeyGestureDetector.classify` (26.1's advertised "pure gesture state
machine") has no production caller at all, which is how three copies of one
number survived. Owner answers: **A — tap in the strip closes the panel, strip
stays undimmed** (he accepted that the green play there then takes two taps) and
**A — the phone's own touch slop (~8 dp); a drag never types**. So: a strip-wide
box (`SidePanelPlan.STRIP_WIDTH_FRACTION` = `1 − PANEL_WIDTH_FRACTION`) composed
**after** the drawer so it sits above it and above the scrim, `clickable` (it
consumes the tap), `indication = null` (undimmed), present only while the panel
is open or opening, closing through `closeDrawer(DrawerCloseReason.SCRIM)` →
`DrawerPolicy`; and one pure rule `KeyGestureDetector.isScrollDx` + one slop
(`LocalViewConfiguration.current.touchSlop`, in pixels) shared by all three
rows, with the up counted as part of the gesture and the arrows' hold-repeat
step guarded by `!isScroll`. Nine new host cases (`KeyGestureDetectorTest` +3,
`KeysScrollCancelWiringTest` new 4, `SidePanelPlanTest` +1, `DrawerWiringTest`
+1). **Android CI green on the tip — run 36378830783, 9m29s, both APKs (debug
26,025,252 B / release 6,798,268 B)**; round 1 (36378529137) was red with two
compile errors of this part's own making — a missing `fillMaxHeight` import and
`touchSlop` being pixels rather than `Dp` — fixed in `df6c654` with no behaviour
change. **No device evidence claimed** and no round asked for; the two things
only his phone can confirm are that the strip's tap really closes the panel and
that a drag across the row never types. No new dependency, permission,
preference, telemetry, screen, row, button or setting. **No PR and nothing
merged.** Brief with the verbatim answers:
[PHASE_69_3_STRIP_AND_KEY_DRAG.md](../phases/12-ui-polish-program/PHASE_69_3_STRIP_AND_KEY_DRAG.md)
— Phase 55's recorded "the scrim owns the strip's tap" deviation is corrected in
place in `PHASE54_58_PHONE_UI_ROADMAP.md` and `JOURNEY.md`.

---

**2026-09-28 — Phase 69.2 (the quick-key row's language + the ghost's suggestion
look): implemented on `arena/01a0e49f-codec`, owner answers taken BEFORE any
code.** The owner reported two problems right after the 69.1 delivery, verbatim:
*"What language i am using don't matter it always give me same fixed quick
keys"* and *"Sometimes the ghost suggestions text are way too real i think as i
wrote the wrong word then about a second it vanished the ghost suggestions fix
it"*. Investigation on this checkout (not from memory) found both were real and
why: the language caps were the row's **last** entries — slots 15+ of a row that
shows ~7 caps and opens at the left, so every language looked identical (and Go,
Rust, PHP, Ruby, Lua, XML, YAML, Markdown had no caps at all); the ghost was
**plain code text with no box** at 38 % of the comment colour — measured
1.82–1.98:1 against the editor background where real code is 11.25–13.94:1, so
the missing cue was a container, not brightness — and two code paths could
delete it: **any** scroll (the editor's own caret-follow scroll included) and any
recompute whose fresh engine answer no longer carried the aligned item (~1 s
after the keystroke). Owner answers: **auto-detect by file extension, the
language's own caps lead the row, the default row for a non-standard file**
(*"Can it be auto detection my file extension and set the the quick keys order as
per requirement and if it is not a standard file than a default quick key
option"*), **B — Give each language a few real caps (Spck parity)**, and **A —
Both: unmistakable look + stop it vanishing**. So the part is exactly two
changes: **(1)** new pure `LanguageQuickKeys` (real caps per language, one table
shared with `languageMacroRow`), `keysFor` order = language caps → the owner's
custom snippets → the general set, non-standard files keep the default row,
nothing that shipped before was removed, and a language change returns the row
to its head (`LaunchedEffect(language) { keysRowScroll.scrollTo(0) }`) while the
69.1 remembered position still stands within one file; **(2)** the ghost is drawn
inside a suggestion box (comment colour at 18 %, text unchanged at 38 % — both
pinned by a new contrast law) and a still-correct ghost is held across background
refreshes (`GhostCompletion.heldWhenStillValid`) while G4's clear-on-scroll is
narrowed to the user's own drag/fling (causes verified against sora 0.24.6's
`ScrollEvent`). Tests: `LanguageQuickKeysTest` (new, 7), `GhostWiringTest` (new,
4), `GhostContrastTest` (new, 3), `GhostCompletionTest` +4, `EditorKeySetTest`
(two "tail, last" pins rewritten with their reason), `EditorRowsWiringTest` +1,
and one stale 22.x pin in `RunKeySetTest` found by CI round 1 (red for cause,
run 36353374750 on `9c7a74d`; fixed in `f29fcfd` with no production change; the
same run's test-only receiver typo at the tip — `GhostCompletion.Visible` for
`GhostState.Visible` — was round 3's red, run 36353710831 on `d5420f0`, fixed in
`b43432d`). **Android CI is green on the tip `b43432d`**: run 36354523369,
11m26s, the full suite plus this part's 11 new cases and both APKs (debug
26,024,564 B / release 6,796,488 B). **No device evidence claimed** and no round
asked for. No new dependency, permission, preference, telemetry, screen, engine,
row, button or setting. **No PR and nothing merged.** Brief with the verbatim
answers:
[PHASE_69_2_LANGUAGE_KEYS_AND_GHOST.md](../phases/12-ui-polish-program/PHASE_69_2_LANGUAGE_KEYS_AND_GHOST.md).

---

**2026-09-27 — Phase 69.1 (typing, keyboard and selection): reviewed, asked,
implemented on `arena/01a0e49f-codec` — owner answers taken BEFORE any code,
all five = option A.** The owner chose **keep the system IME default and the one
coding row exactly as it is** (no second toolbar, no forced CodeC Keys), **add
the one-line-of-air rule**, **remember the coding row's horizontal position (and
nothing else)**, **keep sora's own blue drop exactly as it is**, and **keep the
keyboard-time behaviour of the bottom tabs (no bar, no handle while typing)**.
So the bounded part is exactly two changes, both in the Phase-48 caret owner's
world: **(1)** the air rule — reading sora **0.24.6's own**
`CodeEditor.ensurePositionVisible` (fetched and quoted, not guessed) showed sora
leaves a row of slack only when the caret's row is OFF screen and otherwise
returns early, so a caret on the last visible row sits flush and its drop handle
(below the row) is clipped; the pure `CaretVisibilityPolicy.revealLine` /
`revealColumn` now ask the same single, posted, coalesced, `runCatching`d call
for the position **one line below** the caret (clamped into the live buffer, so
EOF = today's behaviour, and the air is a viewport effect — no `setSelection`, no
buffer write); **(2)** the coding row's position — `rememberScrollState()` lived
inside `EditorKeysRow`, which is composed at **two** strip call sites and in
three strip branches, so every keyboard toggle and chip appearance snapped the
caps row back to its left edge; one `ScrollState` is now owned by `EditorScreen`
and threaded through both call sites. Caps, order, height and the bottom cluster
are unchanged. Tests: `CaretVisibilityPolicyTest` +5, `CaretCallSiteTest` (one
pin moved with its reason + a new "ask through the pure policy, never move the
caret" pin), `EditorRowsWiringTest` +1. **No device evidence claimed** — the two
behaviours are only provable on a handset, and no round was asked. No new
dependency, permission, preference, telemetry, engine or screen. **No PR and
nothing merged.** **CI: round 1 red for-cause on two self-inflicted pins (a KDoc
carrying the literal `ensurePositionVisible(`, and one wrong expectation); fixed
in `f98ae5c` (comment + test only) and round 2 ✅ GREEN —
[run 36350567066](https://github.com/pabi277/CodeC/actions/runs/36350567066) on
`f98ae5c`: host unit/screenshot tests (Phase 52), debug APK 26,021,972 B, release
APK 6,797,484 B, manifest validation, artifacts uploaded.** Brief with the
verbatim answers:
[PHASE_69_1_TYPING.md](../phases/12-ui-polish-program/PHASE_69_1_TYPING.md); full record:
[phases/12-ui-polish-program/chat-phase69/README.md](../phases/12-ui-polish-program/chat-phase69/README.md).

---

**2026-09-27 — Phase 68.1 completed part: the three owner-approved additions
are built and CI GREEN.** After the close-out below, the owner asked what the
phase still needed and approved all three findings: **(1)** the Markdown
preview now really renders Markdown — pure host-tested `MarkdownPreview`
(subset → HTML, escaped input, sanitised URLs, no-JS themed shell) wired into
`WebPreviewScreen` via `loadDataWithBaseURL`, fixing the raw-source preview
that contradicted Q4=C; **(2)** the JSON Keys row from Spck shot 204937 —
`:` + `,` + one null/true/false cap; **(3)** the sort door's ✓ survives
editor re-entry (`lastTabSort` now lives in the view model). **GREEN, first
round: [run 36346189970](https://github.com/pabi277/CodeC/actions/runs/36346189970)**
on code `8c862c5` — host unit/screenshot tests, debug APK 26,021,016 B,
release APK 6,798,016 B. Still no PR/merge without owner instruction —
**until the owner's handset round came back: “Everything looks good merge
it” (2026-09-27). Delivery authorised: PR from `arena/01a0e444-codec` to
`main`; the PR timeline is the final-check/merge-commit record.**
**Next after merge: the 69.1 discussion (typing, keyboard and selection) in a
new chat — review and questions first, no code before the owner answers.**
[Record](../phases/12-ui-polish-program/chat-phase68/README.md).

---

**2026-09-27 — Phase 68.1 closed out: full Android CI ✅ GREEN on
`arena/01a0e444-codec`, no return to the earlier chat needed.** The whole tree
of `arena/01a0e36f-codec` at `472579d` (Phase 68.1 compact Spck parity:
48dp breadcrumb top bar, 40dp tab bar with italic active + fixed dirty dot,
split tab-menu doors — close-only ⋮ menu + ≡↓ sort door with Queue and
checkmark, compact status bar, MD preview via Run) was mirrored byte-for-byte
onto `arena/01a0e444-codec` (`9e7280b`) and repaired: that branch's CI had
been red on a compile error (missing `clickable` import, fixed there by
`472579d`) and on one self-contradictory test — `TabMenuWiringTest > the flag
is published once and cleared on the way out`, whose exact-match literal had
been given a trailing `)` that cannot coexist with the
`modifier = Modifier.weight(1f)` argument the sibling test requires; the
literal reverted to the `)`-free form `main` uses (`7ca3880`). **GREEN, first
round on the repaired code: [run
36343503123](https://github.com/pabi277/CodeC/actions/runs/36343503123)** —
host unit/screenshot tests, debug APK 26,013,004 B, release APK 6,794,264 B,
manifest validation, artifacts uploaded. No PR opened and nothing merged —
owner instruction still required. **Next: owner handset round (compact Spck
parity: top-bar breadcrumb, 40dp tabs, the two menu doors, status bar, Run ▶
opening MD preview), then merge decision.**
[Record](../phases/12-ui-polish-program/chat-phase68/README.md).

---

**2026-09-27 — Phase 66.1 delivered: owner device-verified and merge-authorised
(verbatim: "Device verified merge it to the main") via
[PR #88](https://github.com/pabi277/CodeC/pull/88).** The PR records the
final-head checks and the merge state. **Next: the 67.1 discussion (files,
drawer, project search) in a new chat — review and questions first, no code
before the owner answers.**

**2026-09-27 — Phase 66.1: Projects hub — demo seeded once, truthful dialogs,
one vocabulary.** Discussed first (review + four option questions); owner
answers verbatim: **"Keep the current cards"**, **"Yes — stay deleted"**,
**"(a) demo + (b) dialogs + (c) wording — all in one part"**, **"Keep today:
hub file tree"**. Implemented on `arena/01a0e220-codec`: `demo_flask` is seeded
once per install (marker `.demo-flask-seeded-v1`; deleted stays deleted); New
Project / Rename / Import ZIP say *taken* / *invalid* in the field before the
tap and print a failure inside the open dialog (new pure `ProjectNameCheck`,
`onFailed` on the three ViewModel operations); search and name fields take
focus with the right IME action; type rows are radio buttons to TalkBack; the
tree header names the kind, the breadcrumb no longer dangles, card age uses the
side panel's words, hub sentences are resources, delete confirms wear the error
role, the badge reads `CodecPalette.WARNING`. Look unchanged. Local JVM
prevalidation 192/0 focused + 202/0 source-scan (temporary JUnit shim, not
Android); Compose files reviewed by hand. **Full Android CI GREEN, first
round, on code `a96fb25`:
[run 36311728346](https://github.com/pabi277/CodeC/actions/runs/36311728346)**
— host unit/screenshot tests, debug APK 25,965,508 B, release APK 6,777,708 B.
Not merged; no PR; no device round asked. **Follow-up from the owner's phone
(same day):** *"when i go to projects and press back it's showing options of
close the app but i want previous editor page"* — the two editor→Projects doors
popped the editor tab-style; they now push the hub over it like the Settings
door, so Back returns to the editor and the exit prompt is the next Back
(router unchanged; `BackHandlerWiringTest` pin; local 53/0 + 197/0). **CI
green, first round, code `3762b7b`:
[run 36317041278](https://github.com/pabi277/CodeC/actions/runs/36317041278).** Deferred, not decided: clone
QR glyph, chip touch height, `Auto` default, real last-opened history. **Next
discussion in the owner's order: 67.1 (files, drawer, project search) — ask
first.** [Record](../phases/12-ui-polish-program/chat-phase66/README.md).

---

**Owner choices recorded — 2026-09-27:** keep the current look; prioritise
**Projects and files** for the next discussion (start with proposed 66.1, then
67.1 if agreed); keep installation progress in Terminal/Output, with **no new
app-wide indicator**. This prioritises the plan, not automatic implementation.
Ask the remaining detail questions in the brief. Phase 64 final code `4de912a`: [CI green](https://github.com/pabi277/CodeC/actions/runs/36305311370), unit/screenshot tests and debug/release APKs passed.

**2026-09-27 — Phase 64: remove both installation UI locks and the guide.**
Implemented on `arena/01a0e1d9-codec`; local JVM prevalidation 177/0 (temporary
JUnit shim, not Android). Full Android CI green on `4de912a` (run 36305311370). Navigation no longer locks during
userland/package installation; slides, coach marks, typing tips and guide entry
points/preferences API removed. Internal install safety and progress remain.
[Implementation](../phases/09-onboarding-setup/chat-phase64/README.md) · [Full UI review and ten proposed
one-part-per-chat briefs](../journal/UI_POLISH_REVIEW_20260927.md). Later phases are discussion
drafts: ask for the owner's thoughts before implementation. **Owner-authorised
delivery: [PR #87](https://github.com/pabi277/CodeC/pull/87)** — “Complete docs and merge”. The PR
records final-head checks and the actual merge state; authority covers this
phase and docs only, not implementation of the proposed polish parts.

---

**2026-09-27 — Owner-approved merge and future polishing handoff.** The owner says the top-level appearance looks good, declines further device testing for this delivery, and wants to define focused phases in future chats to polish every detail. This is **not a full device-test pass or final-polish claim**. The owner explicitly authorised merging the current branch via [PR #86](https://github.com/pabi277/CodeC/pull/86), after final CI. **Next: the owner defines the first detail-focused polishing phase in a new chat.** No further feature phase is invented; existing unrun checklists are reference-only, not an outstanding owner task. [Verbatim feedback, evidence and handoff](../journal/OWNER_HANDOFF_20260927.md).

---

Earlier dated progress entries below are history; the owner decision above supersedes their pending-device-test and merge-gate status for this delivery.

**2026-09-27 — Phase 63 BUILT, CI ✅ GREEN; device acceptance pending.** Owner chose unstaged-only Discard: staged changes stay intact. Existing Git pane verified; named-file confirmation, fresh exact-path/index/worktree checks, and targeted editor-buffer reconciliation implemented. Code `c43ede6` + safety guard `bd55e70`; latest green build [`36301860116`](https://github.com/pabi277/CodeC/actions/runs/36301860116). Local 134/0; full CI host tests and debug/release builds pass. [Records](../phases/06-git-github/chat-phase63/README.md), [G1–G13 device checklist](../phases/06-git-github/chat-phase63/DEVICE_ROUND.md). **59–63 are now built on this branch. Next: device acceptance/review** (including [Phase 61 P1–P14](../phases/05-run-output-preview/chat-phase61/DEVICE_ROUND.md) still pending), then explicit owner merge decision. No PR/merge/main push authorised; no further feature phase invented.

**2026-09-27 — Phase 61 BUILT, CI ✅ GREEN; device verification pending.** Code `5707e38`, first-round run [`36300428167`](https://github.com/pabi277/CodeC/actions/runs/36300428167): host unit/screenshot tests + debug/release APKs passed. Preview now has an always-openable console, Log/Info/Warn/Error filters, bounded resize, observed-request Network tab, native Zoom and fitted Screen resolution presets. [Records](../phases/05-run-output-preview/chat-phase61/README.md), [phone checks P1–P14 + fixture](../phases/05-run-output-preview/chat-phase61/DEVICE_ROUND.md). Editor blank-screen fix remains owner-confirmed. **Next: Phase 63 — verify git + per-file Discard.** Stop at the phase gate; no PR/merge/main push without explicit owner command.

**2026-09-27 — Blank editor blocker resolved on device.** Owner: “Yes working now”. Fix `5464981` bounds the tab strip to 48 dp so it cannot consume the weighted editor viewport; CI ✅ `36295679258`, including the real Compose measurement regression. [Investigation and verdict](../journal/EDITOR_BLANK_20260927.md). Next planned phase: **61**, then **63**. The merge gate remains unchanged; no PR or merge without the owner's command.

**Last updated:** 2026-09-26 · **Head: Phase 60 — tabs and the coding row — 🚧 IMPLEMENTED on `arena/01a0c83e-codec`; 35/35 phase cases and 534/534 across the phase + broad regression set, green locally; **CI ✅ GREEN `36220293009` on `017dd2c`, first round — zero failed steps**; nothing merged.** The owner was asked the one shape question the roadmap said to ask — the spec's *Hide tabs* hides the editor's **file-tab row**, but this app already had a hide-and-reveal idiom for the **bottom bar** whose handle says “Show tabs” — and chose **both, in one menu row**. **60.1 — the menu.** `EditorTabBar`'s long-press menu (the tab's own 3-dot, which 57.1 also seats as the tab row's trailing cell) gained *Close unmodified* and the spec's three sorts, and both are policy, not clicks: a new pure `ui/editor/TabClosePolicy.kt` names the two laws of *Close unmodified* (the **active tab is never taken** — the editor keeps a buffer alive, so with everything clean exactly it stays, and the row is safe to tap twice — and **only clean tabs are taken**), while the ViewModel answers the one fact the policy cannot: cleanness. That fact is genuinely two facts in this codebase, because Phase 22.5 keeps the **active** tab's stashed buffer deliberately stale between boundaries, so the active tab's dirtiness comes from the live flag and every other tab's from its own `buffer.text == savedText`; the VM then calls the ordinary `closeTab(…, saveFirst = false)` — the same close as by hand, minus a save that has nothing to save. The three sorts are a second pure file, `ui/editor/TabSortPolicy.kt` (`TabSort.NAME` / `EXTENSION` / `PATH`, all **total** orders — lower-cased comparison first, exact spelling as the last tiebreak, so the same set always lands in the same order), and the design decision worth recording is **where the sort is applied**: not to the drawn strip (a view-only sort would have left the visible order and Ctrl+Tab disagreeing, since `nextTab`, the close-fallback tab and `trimTabs`'s eviction all read `_openTabs`), but to the **open tabs themselves** — the active tab stays active, it only moves. Two things are deliberately absent, in writing: no “back to open order” row (the spec names three sorts and no neutral one; reconstructing the open order would need a second field on `EditorTab`), and **no tick** on the sort rows (a one-shot re-order is not a mode — an indicator would lie the moment a file opens). The menu's sort rows are generated from `TabSortPolicy.MENU_ORDER` and labelled by `TabSortLabels.of`, a `when` over every enum entry, so a fourth sort without a label is a compile error rather than a blank row. **60.2 — Hide tabs, one flag, two strips.** `EditorChromeState` gained a fifth fact (`tabsHidden`), owned by the editor exactly like the keyboard fact it already publishes and cleared in the same dispose block — a stale “hidden” would greet the next visit with a parked row **and** a parked bar. The editor folds its file-tab row into a new `TabRowRevealStrip` (the same 44×4 pill + one word 32.1 gave the bottom bar, deliberately thin — it is a parked row, not a second toolbar) and the scaffold parks the bottom bar through the policy it already had: `NavBarPolicy.hideNavBar(…, hiddenByUser = editorTabsHidden)`, where a **reveal still wins**, the manual hide is the **editor's** rule like every other hide (off the editor the bar comes back, because the reveal handle that un-hides it only exists there), and the IME does not matter (this is the one state the auto rules can never reach). The reveal handle's `onReveal` writes the flag back, so one tap brings both strips home; its label moved from a hard-coded literal to `R.string.tab_show`, the same resource the row's strip uses, so the two affordances cannot drift. **The editor's ⋮ cell is deliberately outside the fold** — undo, save, format and the rest live in that cell's list, and hiding the tabs must never hide the editor's actions with them — which `TabMenuWiringTest` pins by asserting the cell's `showMoreMenu = true` sits *after* the row's `EditorTabBar(` call. **Verify, not rebuild, as the plan said:** the coding row (`EditorKeysRow`) is untouched, the top row is unchanged from 57.1 (its `EditorChromeSlotTest` pins — no overflow, bare green ▶, the tour's anchor, the touch floor — pass untouched), and the second, symbol-specific toolbar is still unbuilt because the shots show one row and the roadmap says ask first. **Local: 35 passed / 0 failed** (11 `TabSortPolicyTest` + 6 `TabClosePolicyTest` + 13 `NavBarPolicyTest`, three new + 8 `TabMenuWiringTest` pins + `ComposableAnnotationTest`) and **534 passed / 0 failed** for the phase + broad regression set (197 swept files, pruned by the greedy compile loop, with the phase's own files compiled alongside). The sandbox harness was rebuilt twice this session — `/tmp` was wiped mid-phase, and the `LanguageType` shim had to be lifted verbatim out of the sora-coupled highlighter again — and a **`.git` rollback recurred** (HEAD back at the branch base `3f9fd6b` while the tree held everything): recovered with `git fetch` + `git reset --mixed FETCH_HEAD`, working tree preserved, never `--hard`. Records: [`phases/11-phone-ui-parity/chat-phase60/`](../phases/11-phone-ui-parity/chat-phase60/README.md) + `PART_60_1` + `PART_60_2`, roadmap §60. **Next:** **61** (the preview console: level filters, a drag handle for its height, and the Network tab the owner chose), then **63** (git: verify + per-file Discard) — and the gate stands: no PR, no merge, no `main` push without the owner's command.

**Last updated:** 2026-09-22 (later still) · **Head: Phase 59 — the hub's Recent filter and the project's own mark — 🚧 IMPLEMENTED on `arena/01a0c83e-codec`; 37/37 phase cases and 970/970 across the broad pure-source set, green locally; **CI ✅ GREEN `35741546107` on `55d32f3` after two red rounds of one class — a stray `@Composable`, now pinned**; nothing merged.** The owner answered the phase's one shape question the same day it was asked: the row is the spec's **literal `All · Recent · Create`**, which also moved the `Git/C/Python/Web` chips out of the row (recorded, with what was kept, in the part doc). **59.1** makes *Recent* a real filter: `ProjectHubFilter.RECENT` is a **ranking**, not a flag on the entry, so `filterEntries` answers it from one place — `ProjectsHub.recentEntries` — which asks the **side panel's own implementation** (`RecentProjects.build`) for the order and the cap, i.e. the hub's chip and the panel's RECENT card are one list with two renderings. That needed the two surfaces' *clocks* unified first: the research pass found the panel ranks by the **folder's** own last-modified while the hub's entries carried the newest-thing-inside time, so `ProjectHubStats.scan` now reports `folderModified` beside it, `ProjectHubEntry` carries it with one definition of `recency`, and `FileManagerViewModel` passes `scan.folderModified` — the same reading `EditorScreen` takes for the panel. *Create* is an **action** chip (`HubActionChip`, the filter chips' shell, never “selected”) calling the `onCreate` the screen has always passed as `{ showHubSheet = true }` — one add-project path, not two. **59.2** replaces the kind-only glyph with the project's **own mark**: a new pure `ui/projects/ProjectMark.kt` decides initials (name split on non-letter/non-digit runs: two words → their first letters, one word → its first two, nothing → `?`; no camel-case splitting, so the rule is predictable) and a seat from **FNV-1a** written out in the policy — not `String.hashCode()` — folded into the five `CodecPalette.TILE_*` colours Phase 50.1 measured for white-on-tile contrast, with real values pinned (`snake` → BLUE) so a swap to the JVM hash cannot quietly recolour everyone's projects. The kind the square used to carry was not lost: `ProjectsHub.kindLabel` now leads the card's subtitle (`C · main · 3 files · 2 days ago`), and the `HubIconToken` table plus `brand/icon`/`iconLabel` — whose last reader the change removed — were deleted rather than left to rot. **Compiling locally caught two things worth keeping:** the first draft declared `data class ProjectMark` and `object ProjectMark` together (redeclaration; fixed to the `ProjectsHub`/`ProjectHubEntry` pattern: `ProjectMarks` decides, `ProjectMark` is the value), and three of my own test expectations were wrong rather than the policy (`SN`, `日本`, BLUE) — corrected to the documented rule with the real values then pinned. The harness also gained the ability to run `ProjectsHubTest` at all: it reaches `AutoRunPlan` → `ProjectRunTarget` → `LanguageRegistry`/`WebFileSupport` → `LanguageType`, which is trapped inside the sora-coupled `MultiLanguageSyntaxHighlighter.kt`, so the data-only enum is lifted **verbatim** into a `/tmp`-only shim and the real `LanguageRegistry`/`WebFileSupport` compile against it. **Local: 37 passed / 0 failed** (10 `ProjectMarkTest` + 20 `ProjectsHubTest` + 7 `ProjectMarkWiringTest` pins) and **970 passed / 0 failed** across the broad pure-source set, which includes the core-file pins (`TouchTargetTest`, `IconRoleTest`, `TokenAdoptionTest`, `TypeAdoptionTest`, `SettingsAuditTest`) and therefore covers this phase's `FileManagerScreen`/`FileManagerViewModel` edits. **Both CI rounds were the same class of error — a stray `@Composable` left where a scripted edit inserted a block** (round 1: the annotation that belonged to `HubFilterChip` landed on the new `HubActionChip`; round 2: one appended above `ProjectHubCard`). Round 3 went green: `35741546107` on `55d32f3`. Because the sandbox cannot compile Compose, that class is now pinned rather than remembered: **`ComposableAnnotationTest`** walks every main source and fails if a `@Composable` reaches no declaration or two stack on one. Records: [`phases/04-projects-files/chat-phase59/`](../phases/04-projects-files/chat-phase59/README.md) + `PART_59_1` + `PART_59_2`, roadmap §59 §3. **Next:** **60** (close-unmodified, hide-tabs, the three tab sorts), **61**, **63** — and the gate: no PR, no merge, no `main` push without the owner's command.

**Last updated:** 2026-09-22 (later) · **Head: Phase 62 — Settings: find and group — 🚧 IMPLEMENTED on `arena/01a0c83e-codec`; 25/25 phase cases and 881/881 across the broad pure-source set, green locally; **CI ✅ GREEN `35724187664` on `aa1fcba` after a red round 1 that was the product's own fault**, nothing merged.** The owner picked this one first out of the five phases the new spec re-scoped into, and the two things §4 actually adds are now built. **62.1 — the search.** `SettingsScreen.kt` was a 1,636-line list of **66 control rows in 12 sections** with no way to find one, so the screen now has a search bar (the app's own idiom: `OutlinedTextField`, leading lens, a ✕ that appears only when there is something to clear) over a new pure `ui/settings/SettingsSearch.kt` (292 lines): `SettingsCatalog` holds the 66 rows **generated from the screen's own `title = …` arguments in screen order** — not copied from prose — plus the screen's twelve section titles and the nine that hold rows; `SettingsSearch` matches case- and punctuation-blind, every token against the row's label, its keywords and **the section it lives in** (which is why `appearance` shows Appearance's four rows and `compiler` shows Compiler's five); and `rowVisible`/`sectionVisible` are the single questions the screen asks per row and per header. **62.2 — the fold.** The unit of hiding is the **section**, not the row, because six sections own bespoke content the catalog cannot name (the two theme previews, the terminal extra-keys editor, the GitHub card, the About and developer blocks) — so a new `SettingsSection(title) { … }` wrapper (twelve call sites, each also wrapping its leading divider) hides header + rows + previews together, and `SettingsSectionHeader` became the fold row: tappable **only** where the catalog says there is something to fold (the three form-only sections get no tap and no chevron — no dead controls), with the drawer's own chevron idiom (`ExpandMore` ⇄ `KeyboardArrowRight`), a real `contentDescription`, and a count that says how many rows are inside. **The three laws are the design:** a query narrows and never guesses; while the box has something in it **the query decides everything, including the three form-only sections** (which is exactly what makes *“No settings match”* honest — a query for `repository` shows the Package Repository card, not an empty state); and **folding is remembered, never applied over a search** — a folded section must not swallow the row the search just found. **Honesty machinery:** the catalog is pinned back against the screen in four ways (66 call sites; every label rendered, in screen order — anchored on each row's `title = …` argument, because a label may also be a section title; the screen's twelve headers = `allSectionTitles`; and the audit table's 66 rows re-counted per section), and the wiring test slices the file at each wrapper to prove **each section holds exactly its own header**. One real bug was caught in review before CI: a query naming a form-only section would have shown that section *and* the empty state. One census was re-cut with its reason: `TouchTargetTest`'s exact `IconButton` count **17 → 18** (the search field's ✕ is the first `IconButton` `SettingsScreen.kt` has ever had — 4 editor + 5 hub + 2 packages + 6 terminal + 1 settings), which is the pin's own instruction. **No dead controls, no new store keys, no row moved:** the query and the fold set are `rememberSaveable` view state, one `CompositionLocalProvider` (the codebase's first `CompositionLocal`, deliberately the only one) hands them to the 12 headers and 66 rows, and the audit's 12-section/66-row pin passes untouched. **CI round 1 was RED for cause, and it was the product, not a pin:** the section wrappers (computed as “the divider before the header, else the header”) cut the Terminal Extra-Keys section's two `remember`s away from their readers, so the compile step failed with eight unresolved-reference errors. Fixing it surfaced two more instances of one class: the boundary now swallows the contiguous declarations that belong to the section, and `devModeUnlocked` + `showFilePaths` (declared in About, read by Developer Options) are **hoisted** beside the other `collectAsState` reads. A third instance was one CI could not see — the `if (BuildConfig.DEBUG && devModeUnlocked)` guard had been left *outside* the Developer wrapper, silently emptying itself (it would have shipped Developer Options into release builds) — so the phase now ships two structural pins that catch all three: *every section slice is a balanced block* and *no local declaration is split from its uses by a section wrapper* (both validated against the pre-62 screen, where they report nothing). **Local: 27 passed / 0 failed** (19 `SettingsSearchPolicyTest` cases + 8 `SettingsSearchWiringTest` pins) and **883 passed / 0 failed** across the broad pure-source set (the pins that read `SettingsScreen.kt`, `strings.xml` and every pure policy the sandbox can compile). Records: [`phases/10-app-polish-settings/chat-phase62/`](../phases/10-app-polish-settings/chat-phase62/README.md) + `PART_62_1` + `PART_62_2`, roadmap §62. **Next:** **59** (hub *Recent* filter + name-derived marks), **60**, **61**, **63** in the owner's order — and the merge gate stands: no PR, no merge, no `main` push without his command.

**Last updated:** 2026-09-22 · **Head: a new five-section spec arrived; the research pass is written and four decisions are with the owner — no app code yet.** The owner pasted *“Project Specification: CodeC UI/UX & Feature Upgrade (SPCK Editor Parity)”*. It was re-scoped against this checkout (HEAD `50fcfa7`) in [`PHASE59_63_UI_PARITY_ROADMAP.md`](../roadmaps/PHASE59_63_UI_PARITY_ROADMAP.md), and the finding is blunt: **three of its five sections are largely built already** — the file/folder tree is the editor's own (`FileTreeRepository` + `FileTreeCollapse` + the drawer/55 side panel), the hub already has a project-name search and filter chips, the “signature” symbol strip is Phase 16's `EditorKeysRow` (with pair caps and a SYMBOLS keyboard layer), the preview screen and its console exist, snippets/tab-mode/haptic toggles exist, and **Git is not terminal-only**: `GitManager` is a full argv-list engine and `GitControlSheet` is a dedicated pane with per-file stage/unstage, diff, commit(+push), pull, conflicts and publish — reachable from the hub card **and** the editor's Repository rail slot. What is genuinely new is small and specific: name-derived project marks and a *Recent* hub filter (§1), close-unmodified / hide-tabs / three tab sorts (§2), console level-filters + resize and preview **zoom + resolution** (§3), **settings search + collapsible sections** and three mobile toggles (§4), and a per-file *Discard* in the existing git pane (§5). Two spec items are refused in writing rather than built: the top bar does **not** gain Files/Search/Git/Profile icons (the shots put those five on the left rail, and moving them undoes Phase 55), and there is no AI-assistant or predictive-keyboard toggle (the fifth rail slot is reserved for the owner's own plan; the predictive row's typing-surface gate is 57.2's recorded decision). Proposed phases **59-63**, one per spec section, each with remove/add/exit in the roadmap file. **The owner answered all four the same day** (recorded in the roadmap's §3): **start at 62**; the not-in-shots items are **approved to be built as specified**; the console gets level filters, resize **and a Network tab** (no *Elements*); Git is **verified + given a per-file Discard**, not rebuilt. Phase 62 then shipped — see the head above. **Nothing merged; the merge gate stands.**

**Last updated:** 2026-09-22 · **Head: Phase 58.3 — the preview's upper links become a hamburger — 🚧 IMPLEMENTED on `arena/01a0c83e-codec`; the whole 54-58 series is now built (54 ✅, 55/56 ✅ built, 57.1-57.3 ✅ built and CI-green, 58.1-58.3 built); nothing merged.** 58.3 is the part the roadmap forbade inventing, so it was **researched and then asked**: re-reading all seven shots proved Content / Home / More are buttons **inside the page's own `index.html`** (`124105` shows their markup, `<span>More</span>`) — a user's file, not CodeC chrome — and the one file that might have been a Play/preview screenshot (`124052`) is the editor's code area again (recorded so nobody re-opens it hoping). With no Play shot, the two candidates were put to the owner with the evidence and he chose **CodeC's preview chrome**. What shipped: a new pure **`PreviewChromePolicy`** (`PreviewLink`, `PreviewChromeFacts`, `links()`, `hasMenu()`, `panelVisible()`) + a **☰ in the preview's app bar** (`SpckIcons.EditorMenu`, the same glyph the editor's overflow uses) holding the upper links — copy page address, open in the phone's browser, open the peer/LAN address, share on LAN, *Server options…*, stop servers — while the **address row stays as the readout** and the panel becomes that menu's own item (with a new opt-in `onClose` header in `ServerSharePanel`, so the Output Panel's use is untouched). Nothing was removed and nothing re-implemented: every item calls the action the panel already called (`OpenInBrowser.openOrCopy`, `ShareActions.label`, `LanSharePolicy.shared.set`, `host.stopAll()`), the `file://`-never-to-a-browser rule and the panel's own `> 1` STOP-ALL rule both survived, and the ☰ is drawn only when it has something behind it. Local: **12 passed / 0 failed** (`PreviewChromePolicyTest` 7 + `PreviewChromeWiringTest` 4 + one case re-cut by its own run — "an unloaded preview offers nothing" was wrong: a static preview owns its socket from the first frame, so the LAN switch is real before the page resolves). **CI: the whole phase went green first try** — run `35712790369` on `22b9c2e` (58.1 + 58.2) and run `35713903141` on `ebc1296` (58.3), both with the host test step and every APK job succeeding, and the 57 tip `7c9619b` closed green in `35710338114`. Records: [`phases/09-onboarding-setup/chat-phase58/PART_58_3_PREVIEW_HAMBURGER.md`](../phases/09-onboarding-setup/chat-phase58/PART_58_3_PREVIEW_HAMBURGER.md) §1 (the evidence, both candidates) and §1.1 (the near-miss shot). **Next:** the owner's device rounds (S1-S6 + 57's P/Q/R sets), then the merge gate — no PR, no merge, no `main` push without his command.

**Last updated:** 2026-09-22 · **Head: Phases 58.1 + 58.2 — a first open that lands in the editor, on a page this app writes; and a userland that installs silently — 🚧 IMPLEMENTED on `arena/01a0c83e-codec`; 57.1-57.3 are closed ✅ GREEN (`35710338114`, tip `7c9619b`); 58.3 (the hamburger) NOT started and will not be invented; device rounds P1-P10 / Q1-Q6 / R1-R11 (+ the phase's own S1/S2) written but NOT run; nothing merged.** The phase's two removals are done: **the setup strip is deleted, not hidden** (`SetupBar.kt` gone, and with it `SetupGatePolicy.barText` / `barVisible` / `barDismissAllowed` — the three functions that existed only to describe a bar over every tab; `SetupState.kt` now carries a **“Do not re-add the strip.”** block where they were, naming the owner's row and what replaced them), and **the first-run divert to the Terminal is retired** (`setupDiverted`, its `LaunchedEffect` and the welcome's hand-over effect all deleted; the disk read survives only because `ResumePolicy.setupNeedsWatching` still means "an uninstalled phone owes no resume card"). Its two additions are done too: **58.1** writes and seeds an **original, standalone HTML snake** (`SnakeSample`: `TYPE "web"`, `ENTRY_FILE "index.html"`, a 20×20 canvas game with keys *and* a thumb pad, score/best, wall and self collision, no external URL — `FILES` is a *getter* because a stored list could not reference the page declared below it, the exact `variable 'PAGE' must be initialized` error, recorded in the file) and makes the first launch **seed it, save it as the launch state and start in the editor on it, above the resume offer** (safe mode seeds nothing; the flag flips even when the seed fails, so a refusing filesystem gives the old hub path rather than a blank frame) — and with it the **WelcomeScreen is retired**: its tile moved verbatim to `ui/components/StarterTile.kt`, the screen file was deleted, `WelcomeLayoutTest` was deleted, four pins dropped the welcome from their file lists (`sixFiles` → `coreFiles`, five files) and `StarterTilesTest` now pins that the screen must not come back; **58.2** puts the whole userland story into **one pill, once, at the point of use**: `promptInstall` asks `SetupGatePolicy.can(INSTALL_PACKAGE, factsFromDisk)` first and, when the setup cannot carry the download, posts `NoticeKind.USERLAND_NOT_READY` — *“Finish installing the Linux tools first — open Terminal.”* — instead of opening any sheet, so the pill and the refusal can never disagree. C and the HTML preview are pinned as never waiting (`WebPreview` is decided before any tool probe; C's profile keeps `requiredPackage = null`). Deliberately **not** changed: `dontCloseText` (the Terminal's own chip while its install streams), the point-of-use refusals — including the Packages path's "don't close" line — and the install trigger (it still starts when the Terminal starts; nothing auto-downloads at first launch, because that is not in the Add row). **Local host runs: 70 passed / 0 failed** (NoticePolicy + SetupGatePolicy + SetupGateWiring + PillNoticeWiring) and **50 passed / 0 failed** (the phase-57 chrome set), plus **20 passed / 0 failed** (FirstOpenSample + TokenAdoption + TypeAdoption + IconRole); two real pin failures were caught and re-cut locally *before* the push (the terminal-navigation census 4 → 3, because the divert was one of them, and a `before()` anchor that matched a `WebPreview` outside `decide()`). Records: [`phases/09-onboarding-setup/chat-phase58/`](../phases/09-onboarding-setup/chat-phase58/README.md) + `PART_58_1` + `PART_58_2`; roadmap §58. **Next:** 58.3 — the page's upper links: research **both** candidates (the page's own Content/Home/More nav, and CodeC's preview chrome in `WebPreviewScreen.kt`), use a Play shot if one has arrived, otherwise **ask the owner** (the roadmap's own instruction, and *“if need say me”*). Then the owner's device rounds, then the merge gate.

**Last updated:** 2026-09-22 · **Head: Phase 57.3 — one pill — 🚧 IMPLEMENTED on `arena/01a0c83e-codec`; 57.1-57.3 now all implemented; device rounds P1-P10 / Q1-Q6 / R1-R11 written but NOT run; nothing merged.** **57.3** built the surface the shots ask for: a pure **`NoticePolicy`** (which of the two short messages is owed, and its 2.4 s lifetime) + one Compose **`PillNotice`** on 51.4's `PressableSurface`, chosen over a snackbar (wrong shape, and it would queue behind save messages), a dialog (nothing to ask) and a toast (outside the surface and the screen's lifetime); the shot's own badge is not copied — the clean room carries the app's refresh glyph in the reference's blue, and an unopenable file keeps the app's danger red. It also closed a real hole: **eight** open paths ended in a bare `?: return`, so a tap on a file deleted behind the editor did *nothing at all* — every one now reports (*“Error opening file.”*), while a refresh the user did **not** ask for stays silent (only the tree toolbar's own Refresh confirms itself — 41/42/45's no-nag law applied to the tree, pinned as a decision). **CI round 1 for 57.1 + 57.2 was RED for cause** (run `35709258747`): `EditorChromeSlotTest`'s tab-row helper ended on a comment this checkout no longer carries, and `TouchTargetTest`'s exact brand/icon census read 16 where the six files now hold 17 (⋮ off the bar, the bare green ▶ on, the tab row's trailing cell added one) — both fixed here, and both files now say **why** in the code the next phase must edit. Local host run of the whole phase-57 set: **37 passed, 0 failed**. **57.3's own CI run `35709878911` then failed on one more exact-census pin** — `HapticWiringTest`'s `PressableSurface` call-site list, where the pill is the fourth call site (added with its reason: the component is exactly what stops the pill growing its own press state); the two 57.1 pins were green in it (2,126 tests, 1 failed). **Next command: `Start Phase 58`** (first open on the snake sample we write, silent userland, the hamburger). Queue after 58: nothing in this series — then the owner's device rounds and the merge gate. Phase 52 is a different row and is not cancelled by this plan.

**Last updated:** 2026-09-22 · **Head: Phase 57.1 + 57.2 — the editor's chrome from the shots — 🚧 IMPLEMENTED on `arena/01a0c83e-codec`; 57.3 (the pill) NOT started; device rounds P1-P10 / Q1-Q6 / R1-R9 written but NOT run; nothing merged.** Phases **54** (the reference card, zero app files), **55** (the side panel: pure `SidePanelPlan` + `ProjectSearch`, the Compose `EditorSidePanel`, the tree riding unchanged in the Files slot, the guide-beat → Files rule) and **56** (Projects off the bar, four tabs left, the router's own `rootRoutes`) are done. **57.1** rebuilt the top row from `122157`/`124105`: the file's own mark + its name, the tab row moved out into **its own row** below the bar, the retired ⋮'s list now opening from that row's trailing cell (the owner's own answer), and RUN as the shots' **bare green ▶** — which retires 51.2's contained, labelled button, with that reversal written down. **57.2** pinned the two rows that were already right (the status line yields to the IME; the touch row docks with the keyboard down and rides it when up), gated the chip row on a typing surface (`StripContext.typingSurfaceUp`), and **styled sora's own caret handle** — the library does draw one in 0.24.6 — with `CodecPalette.CARET_HANDLE` re-applied on every theme switch, so no overlay and no second caret. **Next command: `Start Phase 57.3`** (the pill for “Error opening file.” / “Refreshed Files”), then **58** (first open on the snake sample, silent userland, the hamburger). Phase 52 is a different row and is not cancelled by this plan.

**Last updated:** 2026-09-22 · **Head: Phase 56 — the bar keeps four — 🚧 IMPLEMENTED on `arena/01a0c83e-codec`; 42 host cases green locally; CI round 1 (Phase 55) was RED for cause and its two fixes ship in the Phase 56 commit; device rounds P1-P10 / Q1-Q6 written but NOT run; nothing merged.** Phases **54** (the reference lock — the card, the `file:line` inventory and the owner's three answers, zero app files) and **55** (the side panel: pure `SidePanelPlan` + `ProjectSearch`, the Compose `EditorSidePanel`, the tree riding unchanged in the Files slot, the guide-beat → Files rule) are done; **56** removed exactly one option from the bar (owner: *“only removing the project option is ok”*) and left the bar, the handle, “Show tabs”, hide-while-typing, the four remaining tabs and the Projects screen itself alone — with the bar's and the back router's lists split so a phone that starts on Projects still gets the exit prompt. **Next command: `Start Phase 57`** (editor chrome from the two editor shots), then **58** (first open on the snake sample, silent userland, the hamburger). Phase 52 is a different row and is not cancelled by this plan.

**Last updated:** 2026-09-21 · **Head: Phase 51 — the feel — ✅ COMPLETE, DEVICE-PASSED & MERGED to `main` via [PR #82](https://github.com/pabi277/CodeC/pull/82) (owner: *"All pass record and merge"*).** The owner ran the handset round on the CI artifact and reported **ALL PASS** — F1-F16 plus the four regression rows F17-F20 (`48`'s caret above the keyboard, `49`'s back behaviour, `45`'s 11-beat tour, `46`'s two-projects-never-share-a-tab rule); that message is the transcript, and the record keeps exactly what it said (no device, OS or theme was named). What shipped: **51.1** the cold start is CodeC's own (`Theme.Codec.Splash` over `androidx.core:core-splashscreen` 1.0.1 — the 50-52 series' one new dependency — released only by the pure `LaunchReadiness`/`LaunchGate` facts, never a timer; `routeKnown` needs BOTH launch-flag families or the splash flashes the welcome gate's one-frame `return`) plus the welcome screen's mark, tagline, offline-C badge and a `nextStep` per starter; **51.2** RUN ▶ as a contained `primaryContainer` Button on the 48 dp floor with `RUNNING` as its state word (`RunButtonStyle`, lock-first so Phase 44 still wins), `EditorChrome`'s eight declared slots + `// Phase 51.2 slot:` markers (a declaration, **not** a re-flow — Phase 48's caret geometry stays device-proven), and `EditorEmptyState`'s scratch-file chip with exactly one action reusing `EditorLaunchState`; **51.3** `HubListPolicy`'s real LOADING branch (`loadedOnce`, never `isBusy`) + `Skeleton.kt` on `CodecMotion.shimmer`, the hub's designed empty state, `InstallMoment` celebrating only a genuine transition into INSTALLED, and `TerminalIntroPolicy`'s first frame quoting the SAME `SetupFacts` the setup bar renders; **51.4** `Haptics.kt` (the eight moments, two strengths, three guards — pure Kotlin), `CodecHaptics.kt` as the single adapter, the Settings switch `haptics` default ON (audit row 65; the keyboard's `codec_keys_haptics` untouched) and `PressableSurface`'s containment on the two bare-clickable surfaces. **CI: three rounds, two red for-cause, then ✅ GREEN** (an unescaped apostrophe AAPT2 reports as "Invalid unicode escape sequence in string"; one missing `PaddingValues` import) and the phase's measured cost: **debug 25,809,492 B = +73,884 B / +0.29 %**, **release 6,714,740 B = +27,066 B / +0.40 %** over the merged Phase 50 build on the same `versionName` 1.3.17. **Next: `Start Phase 52` — the return** (visible + decline-able resume; measured first paint and jank budget on the existing bench APK; the streak in About, read once; twelve Roborazzi goldens in verify mode). Records: [`phases/11-phone-ui-parity/chat-phase51/README.md`](../phases/11-phone-ui-parity/chat-phase51/README.md) (§Implementation, §CI, §Test log) and its four part docs (each with its own `## Test log`), [`phases/11-phone-ui-parity/chat-phase51/DEVICE_ROUND.md`](../phases/11-phone-ui-parity/chat-phase51/DEVICE_ROUND.md), `rule.md` §9, JOURNEY §75, TROUBLESHOOTING §46, [`PHASE50_52_ROADMAP.md`](../roadmaps/PHASE50_52_ROADMAP.md) §51.

**Last updated:** 2026-09-21 · **Head: Phase 51 — the feel — 🚧 IMPLEMENTED on `arena/01a0c4cb-codec` (owner: *"Start phase 51"*), 132 new host cases green locally, **CI ✅ GREEN `35630471779` tip `32c7c70`** after two for-cause red rounds (an unescaped apostrophe AAPT2 reports as "Invalid unicode escape sequence"; one missing `PaddingValues` import), APK delta +73,884 B / +0.29 % debug and +27,066 B / +0.40 % release over the merged Phase 50 build, **device round F1-F16 NOT run, nothing merged**. Phase 50 (the look) is ✅ **COMPLETE & MERGED** ([PR #81](https://github.com/pabi277/CodeC/pull/81), `main` @ `0f1b650`).** What landed, in the phase's own order: **51.1** the cold start is CodeC's own (`Theme.Codec.Splash` over `androidx.core:core-splashscreen` 1.0.1 — the one new dependency of the 50-52 series — released only by the pure `LaunchReadiness`/`LaunchGate` facts, never a timer, with `routeKnown` needing BOTH launch-flag families or the splash flashes the welcome gate's one-frame `return`) plus the welcome screen's 96 dp mark, tagline, offline-C badge and a `nextStep` per starter; **51.2** RUN ▶ as a contained `primaryContainer` Button on the `MIN_TOUCH` floor with `RUNNING` as its state word (`RunButtonStyle`, lock-first so Phase 44 still wins), `EditorChrome`'s eight declared slots + `// Phase 51.2 slot:` markers pinning the chrome's order from the real source (a declaration, **not** a re-flow — Phase 48's caret geometry is device-proven), and `EditorEmptyState`'s scratch-file chip with exactly one action reusing `EditorLaunchState`; **51.3** `HubListPolicy`'s real LOADING branch (`loadedOnce`, never `isBusy`) + `Skeleton.kt` on `CodecMotion.shimmer`, the hub's designed empty state, `InstallMoment` celebrating only a genuine transition into INSTALLED (with the row polling the disk only while the user's own install is in flight) and `TerminalIntroPolicy`'s first frame quoting the SAME `SetupFacts` the setup bar renders; **51.4** `Haptics.kt` — the eight moments in pure Kotlin, two strengths, three guards — plus `CodecHaptics.kt` as the single adapter, the Settings switch `haptics` default ON (audit row 65; the keyboard's `codec_keys_haptics` untouched) and `PressableSurface`'s containment on the two bare-clickable surfaces. **Three plan premises were corrected by reading the code** (the editor is never an empty frame — the VM keeps one scratch buffer alive; `SetupGatePolicy.barVisible` is not "an install is running" — it is also true for a settled FAILED bar, so the terminal quotes `InstallProgress.inFlight`; and v1's `celebrateOnFinish` would have celebrated an *upgrade* of a working package), and one docs drift was fixed in the same commit (`SETTINGS_AUDIT.md`'s prose said "46 rows" while its table held 64 and the test counts the table — now 65). **Owed:** the owner's handset round — [`phases/11-phone-ui-parity/chat-phase51/DEVICE_ROUND.md`](../phases/11-phone-ui-parity/chat-phase51/DEVICE_ROUND.md) F1-F16 (+ F17-F20 regressions: cold start, the guide's first frame, RUN ▶ at arm's length, the keys row, the hub's loading frame, a real install finishing, the eight haptics, the switch off) — and the APK delta for the one new dependency from the green `Build APK` artifact. Then the gate: **no PR, no merge, no `main` push without the owner's command.** Queue after 51: **52 — the return** (visible, decline-able resume; measured first paint + jank budget; the streak in About; twelve Roborazzi goldens). Records: [`phases/11-phone-ui-parity/chat-phase51/`](../phases/11-phone-ui-parity/chat-phase51/README.md) §Implementation, `rule.md` §9, JOURNEY §75, TROUBLESHOOTING §46, [`PHASE50_52_ROADMAP.md`](../roadmaps/PHASE50_52_ROADMAP.md) §51.

**Last updated:** 2026-09-14 · **Head: Phases 50-52 📋 PLANNED — docs only, no app code (owner: *"it's not attractive to user to use multiple time so i want to boost it's ui 100× time"*, clarified as **polished UI**; goal = the first ten seconds **and** the daily return; **three** phases).** The row became [`docs/research/PHASE50_52_UX_RESEARCH.md`](../research/PHASE50_52_UX_RESEARCH.md) (the dossier — 16 sources; every claim about this app is a `file:line` or a grep on `main` @ `62cfe7b`) + [`docs/roadmaps/PHASE50_52_ROADMAP.md`](../roadmaps/PHASE50_52_ROADMAP.md) + `docs/phases/11-phone-ui-parity/chat-phase50/`, `docs/phases/11-phone-ui-parity/chat-phase51/`, `docs/phases/11-phone-ui-parity/chat-phase52/` (README + four part docs + a written device round each): **50 = the look** (`CodecTokens`, brand colour vs. dynamic colour, a type scale, `CodecMotion`), **51 = the feel** (splash + first screen, the editor with RUN ▶ as the hero, hub/packages/terminal, haptics on eight named moments), **52 = the return** (visible + decline-able resume, measured first paint and jank, the streak the app already counts, twelve Roborazzi goldens). Measured evidence behind the plan: 801 raw `dp` literals, 10 different corner radii, no type scale, one `AnimatedVisibility` and zero animation specs, no splash screen, and `StatsManager`'s streak that nothing reads. Recorded decisions: no alpha Material 3 Expressive (replicate its measured findings), tokens before screens, **no telemetry and no notifications, ever**; one new dependency in the whole series (`core-splashscreen`) and two 🚩 owner decision points (brand-vs-wallpaper default; OFL code font vs platform monospace). Also added [`docs/getting-started/HOW_TO_CREATE_A_PHASE.md`](HOW_TO_CREATE_A_PHASE.md). **Owner decision (2026-09-14) — the cross-device round is renumbered and then ❌ CANCELLED:** *"Remove the
full device cross check phase"*. Phase 50 (the matrix) moved to **53** (the numbers are the execution order,
so the UI series took **50/51/52**) and was then removed from the plan outright; `docs/phases/11-phone-ui-parity/chat-phase53/` is
history only. **What replaces it:** per-phase handset rounds (L1-L12 / F1-F16 / R1-R12) when each phase
ships, CI as the executor of record, and 52.4's Roborazzi goldens in verify mode. The still-owed rows of
44/45/46/47/48/49 stay owed and are unaffected. ⚠️ `DeviceMatrixTest.kt` on `arena/01a099d8-codec`
machine-pins the cancelled matrix from `docs/phases/11-phone-ui-parity/chat-phase50/…` — **delete or re-scope it** before that branch
lands on `main`.

**MERGE COMMANDED (owner, 2026-09-14: *"Merge it"*) → [PR #80](https://github.com/pabi277/CodeC/pull/80)** — docs-only (28 files, +3587/−129), from `arena/01a09a40-codec` (tip `1b7774b`, CI ✅ `35491592459` on `e78d36b`). ⚠️ `arena/01a099d8-codec` (the matrix, unmerged) ships `DeviceMatrixTest.kt`, whose hard-coded `docs/phases/11-phone-ui-parity/chat-phase50/…` paths stop existing on `main` the moment PR #80 lands — **delete or re-scope it before that branch merges.** No other PR until the owner commands it; next command: "Start Phase 50".

**Last updated:** 2026-09-13 · **Head: Phases 48 AND 49 + both device-round fixes ✅ ALL DEVICE-PASSED & MERGE COMMANDED via [PR #79](https://github.com/pabi277/CodeC/pull/79) (owner, verbatim: "All device passed. Now merge it." — the pass covers the 11-beat tour re-round, the CodeC Keys blink re-check, 48's eight checks and 49's ten + 49.2's eight; no per-row details supplied, none invented. Tip `3a88c44`; executor-of-record run ✅ GREEN `34742868395`, release APK 6,681,306 B).**

**Last updated:** 2026-09-13 · **Head: DEVICE ROUND 1 on the 48+49 build, continued — the guide's single-click law restored + demo_flask's own beat (tip `c5e73ac`, CI ✅ GREEN `34742868395`, release APK 6,681,306 B = +2,240 B: the eleventh beat). The owner's round-1 rows after the blink fix: (a) *"one click close the guides box and again have click the option to work make it single click"* — evidence exonerated the overlay (press-resolve → published click → advance) and convicted the PLAN: the demo_flask pick had NO box since 47.1 (an unguided tap stood between beats 2 and 3), beat 5's click could detour through the Phase 33 run chooser, and the header's guided tap was a toggle in disguise. Fix: the tour is now **11 beats** — new step 3 DEMO_PICK "Pick the demo" anchored on the demo's own in-drawer PROJECTS row (`drawerDemoPickAnchor`, the row's own tap published so dismissing the box IS the switch), beat 2 teaches only the tap, the header's guided tap is goal-directed (drop, never fold), and the tour's RUN tap runs the open file directly (`onGuideRunTap`; a normal tap keeps the chooser). Owner law restated: ONE tap on a guided control does the WHOLE thing the box teaches. Tests: CoachMarkPlanTest 11-beat pins + 2 new; GuideWiringTest publisher + copy pins. Record: TROUBLESHOOTING §45, PART_45_2 §Round 7, JOURNEY §70. Round 1's first push (`34742573726`) was red for-cause — two pins travelled stale (surfaces list; the beat-3 literal) — fixed in `c5e73ac`. STILL OWED on this build: the owner's re-check of the 11-beat tour (the two fixed rows), the blink re-check on a big file, 48's eight checks, 49's ten + 49.2's eight on two nav modes, and the older 44-47 rounds. NO PR until the owner commands it.**

**Last updated:** 2026-09-13 (device round) · **Head: DEVICE ROUND on the Phases 48+49 build — one bug, fixed (CI run `34741213510` = executor of record): *"when i use app dedicate keyboard and typing it's blinking the full code"* — with CodeC Keys (the OPT-IN app keyboard; the SYSTEM keyboard is the default since 47.2) every keystroke blinked the whole code because every programmatic edit replayed through sora's `setText` (new Content + full analyzer reset + async full layout rebuild + input restart, once per keystroke — verified against the pinned 0.24.6 tag before changing a line). Fix: pure `IncrementalEdit` (maximal common prefix/suffix delta, 2048-char budget, null = too big) — `SoraEditorHost` now applies small edits as ONE `Content.replace` (sora's own typing primitive; layout/analyzer process the delta incrementally), with the atomic `setText` kept as the FALLBACK (first replay, formatter rewrite, replace-all, any `runCatching` failure — the VM text stays the source of truth; the 2026-09-06 crash story untouched). The caret-follow rescroll was ruled out first: sora's `ensurePositionVisible` early-returns (bare `invalidate()`) when the target is within 1 px (tag `:2287`). Tests: `IncrementalEditTest` ×13 + `ReplayPathWiringTest` ×5 (one `ed.setText(` site, attempt-before-fallback). Record: TROUBLESHOOTING §44, PART_48_1 §Device round. The 48/49 device rows (eight + ten + eight) are still owed on the same build.**

**Last updated:** 2026-09-13 · **Head: Phases 48 AND 49 🚧 IMPLEMENTED on `arena/01a09925-codec` (owner: "Start phase 48 and 49" — both phases, one push, tip `b094b28`; CI round 1 (`34739938499`) red for-cause — one stale Phase-41 pin in `ExitSurveyTest`, moved with its reason — round 2 ✅ GREEN `34740245825` tip `a592295` (25 steps 10m6s, zero error annotations, release APK 6,681,018 B = +4,072 B / +0.06% over the merged 46/47 tip `34737610972`, both phases combined), device rounds pending).** **48.1 — nothing hides behind the keyboard:** pure `CaretVisibilityPolicy` + `EditorViewport` (the policy keys on the sora box's HEIGHT — the five chrome booleans are only causes; `previous == null` never owes, so the Phase 35.4 quiet-on-open law holds), and ONE rescroll owner in `SoraEditorHost` (`CaretCallSiteTest` pins the app's single `ensurePositionVisible(` site, verified against the pinned sora 0.24.6 tag: `CodeEditor.ensurePositionVisible(int, int, boolean)` + `getCursor().left()`): `onSizeChanged` → policy → `postDelayed` (never inline — old metrics), `noAnimation = true` (chrome-driven scrolls must not animate), cancel-and-replace coalescing (the IME animation reports many sizes), `runCatching` (mid-release must not crash). **The suggestion half of 5.A is covered by a recorded deviation:** the spec blamed the strip re-render (a height change), but a ghost/chip accept usually swaps row for row at constant height — so both VM→sora replay paths now follow the caret they move (`scheduleCaretRescroll(endPos, 0L)` through the same single owner); typing echoes still skip via reference equality, quiet-on-open stays scroll-free, and find-next/quick-fix/CodeC-Keys-trackpad gain "the caret comes into view". **49.1 — one back table:** pure `BackRouter.decide(BackState): BackAction` with the precedence pinned (`BackRouterTest`): unsaved → drawer → hub tree → sheet(None) → find bar → output panel (!keyboard) → prompt → pop → root(prompt/exit) → None; `isRoot(route, patterns)` compares the base segment by equality (the startsWith trap); rows 7-9 are ROOT-ONLY fields so a screen handler can never pop or exit. Wiring: root handler decided from STATE, not `popBackStack()` (causes A/B of the "no option on most phones" bug die); `FileManagerScreen` gained the app's third surface handler — back at an open project tree closes it (`closeProject()`), never the app (owner 4.iv hub half); `EditorScreen`'s two ad-hoc handlers folded into one router call (drawer keys on `targetValue` per H2, so back inside the ~200 ms open animation cancels it; `closeDrawer` asks `DrawerPolicy` with the same semantics); `GuideScreen` routes through the table (PopRoute = back = SKIP, unchanged). **Recorded deviation: the spec's coach-mark row is NOT built** — 45.2 rounds 2-3 made the tour unbreakable by owner law (*"without skip anything"*), so back navigates and the tour waits unspent; audit row 9 corrected in the part doc. **49.2 — the exit prompt from state + the second door:** every root press logs the diagnostic (`AppLogger.i("Back", …)` — route, prompt switch, visibility, decision: the 49.1 evidence gate readable from Developer Options → View App Logs), and Settings → Feedback & Support gained **"Tell us before you go"** (the same dialog on demand — cause C, the home swipe that sends no back event, is unfixable in code; the audit row 48 landed in the same commit so `SettingsAuditTest` stays green). **Tests: 7 new files** (`CaretVisibilityPolicyTest`, `ViewportSnapshotTest`, `CaretCallSiteTest`, `BackRouterTest`, `BackRouterRootTest`, `ExitPromptPolicyTest`, `BackHandlerWiringTest` — the last pins EVERY `BackHandler(` in the app is either the root or router-driven) + `DrawerWiringTest`/`GuideWiringTest` pins moved to the router shapes. **Device rounds owed: 48's eight checks (README) and 49's ten checks (README) + 49.2's eight on two nav modes — and the still-open 44 R1-R8/D1-D12, 45 G1-G41, 46/47 re-round rows on the same phone. CI ✅ GREEN `34740245825` tip `a592295`; NO PR until the owner commands it.** Queue: 50 (the cross-device matrix) is the last phase of the series. Records: [`phases/11-phone-ui-parity/chat-phase48/`](../phases/11-phone-ui-parity/chat-phase48/README.md) + [`phases/11-phone-ui-parity/chat-phase49/`](../phases/11-phone-ui-parity/chat-phase49/README.md) (implementation sections in every part doc), PHASE44_50_ROADMAP, JOURNEY §68, rule.md §9.**

**Last updated:** 2026-09-13 · **Head: Phases 46 AND 47 ✅ COMPLETE & MERGED via PR #78 (owner: "Start phase 46 and 47" → device rounds 1-2 → "All working merge it") — 46.1 "Open Folder" removed completely (the `+` sheet now has three rows: New Project / Clone / Import ZIP; the SAF tree walk, its launcher, its ViewModel action and both strings are gone, grep = 0, pinned by `FolderImportRemovedTest`); 46.2 one file = one file (a file tap in the hub opens THAT file via the editor route's new nullable `single=1` flag — one tab, real `~proj/…` path in the status bar with long-press-to-copy, edit/save/autosave identical, NO project chrome and NO `EditorLaunchState` write — and the card's ⋮ gained "Open in editor" (launch default → newest source → first source, `ProjectEntryFile.pick`); pure `EditorOpenMode`+`EditorOpenModePolicy`; `SingleFileSaveTest`'s money case: save-from-peek writes the real project path, mode flips reload from disk both ways); 47.1 the drawer gained a ✕ that ONLY closes, Back closes it (interim handler, 49 folds it in), and the in-drawer PROJECTS section (Single files + every project, current marked, ＋ New project… → the hub with its `+` sheet up) replaced the retired "Open folder" dialog — grep for the title = 0, pinned by `DrawerWiringTest` + updated `GuideWiringTest` pins; 47.2 `codec_keys_enabled` default true → false (the elvis is the whole change; stored choices win; no startup write; Settings copy "Off by default — …"; both `collectAsState(initial=false)` readers; guide slide 2 carries the sentence; `KeysStayPolicy` untouched; `ImeLeverTest` pins the two IME sites). **13 new/updated host-test files ≈ 50 cases; CI ✅ GREEN `34735450676` tip `76026b5`; then DEVICE ROUND 1 (2026-09-13) — two bugs reported and fixed (CI pending): (1) ☰ drawer ＋ New project… restored a stale tab sub-stack (its top: an editor) instead of landing the hub with the `+` sheet — the row is an instruction, now `restoreState = false`; (2) "2 projects are different so don't open together" — the hub's open-a-file navigations restored another project's saved editor session (`restoreState = false` on all of them, Templates too) AND `openProjectFile` could append across projects (a different project now resets the context: save everything, clear tabs/undo/git, start that project's session). 5 new test cases pin all three surfaces; the fix build is CI ✅ GREEN `34736668771` tip `ce4044d`. Then DEVICE ROUND 2: the guide's beat-2 pick of demo_flask CLOSED the drawer (owner: it "closes the pop up of the file selection option [but] it should drop down all the available projects") — the close-unless-the-tour-waits guard (`tourWaitsInDrawer`, `CoachMarkPlan.nextBeatIsInDrawer`, `DrawerPolicy.closesAndSwitches`) is deleted; a pick now NEVER closes (the switch happens behind the drawer, list dropped-down, new project ●-marked, tour drawer beats unbreakable by construction); `DrawerPolicy.shouldClose` = `drawerOpen && reason != PROJECT_SWITCHED`; DrawerPolicyTest + GuideWiringTest pins rewritten; fix build CI ✅ GREEN `34737610972`. Older CI rounds 1-2 red for-cause: missing assertEquals imports, then a pin joining file PATHS not contents; device re-round pending (the two reports above + the 44/45 rounds still owed) — 45 G1-G41 AND 44 R1-R8/D1-D12 still owed on the same phone, plus the 46/47 exit rows in the part docs.** Phase 48 (caret behind the keyboard) is next in the queue after the device rounds; 49-50 follow. Records: [`phases/04-projects-files/chat-phase46/`](../phases/04-projects-files/chat-phase46/README.md) + [`phases/11-phone-ui-parity/chat-phase47/`](../phases/11-phone-ui-parity/chat-phase47/README.md) (implementation sections in every part doc), TROUBLESHOOTING §41, PHASE44_50_ROADMAP, JOURNEY §67, rule.md §9.**

**Last updated:** 2026-09-13 (round 6) · **Head: Phase 45 round 6 — the owner ran round 5 and reported the pause outliving the work it protected: *"One problem even after unpacking the userland it still stay lock if i refresh it it's the open the editor check the problem."*; round 6 bounded the lock by the setup itself — **a settled stage always reopens the app**, and **a shell coming alive re-reads the disk**; 🚧 IMPLEMENTED on `arena/01a0955a-codec`, **199 host cases green locally, CI ✅ GREEN on the round-6 commit (`34719753700`, tip `8c3c10d`, job `build` 25 steps 10m48s, zero error annotations, release APK 6,675,154 B = −100 B against round 5, whole phase +23,472 B / +0.35% over Phase 44 round 4), device round G1-G41 NOT run.** Phase 44's round 2 is still pending on the same phone.** **The diagnosis is a category error round 5 made, and it generalises: a verdict and a reading are not the same kind of thing.** `progress.stage` is the installer's own **verdict** — set by the code that did the work, in the call that finished it, so it cannot be stale — while `SetupFacts` is a **reading** of the disk (three `stat` calls on `bin/pkg` and the shell, plus the ledger's swap phase) that is only as fresh as its last re-computation, which round 5 did at three moments (the ViewModel's constructor, every *published* stage change, the end of an install). Round 5's law — *"no usable prefix and not given up on ⇒ paused, whatever the stage says"* — therefore let a **stale reading hold the app shut after the verdict said the work was done**, and because the only surface left open was the Terminal and nothing in it re-reads the disk on demand, the lock had **no in-session exit**; a restart took the reading again, which is why *"if i refresh it it's the open the editor"*. **Evidence before fix — five suspects ruled out first:** the bar recomputes its verdict every composition (no `remember` around `verdictFor`); one ViewModel instance (`viewModel(viewModelStoreOwner = activity)` in both `MainActivity` and `TerminalScreen`); `SetupLedger.clear()` really runs in the `Installed` branch and `read()` re-reads, so no stuck `SWAPPING`; `chmodBin` runs **before** `return Installed`, so exec bits are not late; the package-install input is already `busy && installing`, cleared on both exits and on dispose. **Round 6's two independent guarantees:** (1) `if (progress.settled) return ChromeLockReason.NONE` — the boundary moved from a hand-listed pair to the installer's own verdict (`settled` = `!inFlight`; `inFlight` = `CHECKING | DOWNLOADING | VERIFYING | EXTRACTING`), so a `READY` whose reading says "unusable" goes back to Phase 44.1's **C4** correction (*"Setup didn't finish"* + ⬇ retry, `SetupGatePolicy.can` refusing the acts that would fail, **C still compiles offline**); (2) `TerminalViewModel`'s `anyAlive` collector calls `refreshSetupFacts()` on `Dispatchers.IO` — the fourth reading, and the one that *cannot* be early, because nothing is alive until the prefix really runs a bash. Guarantee 1 makes the lock unable to outlive the verdict, guarantee 2 makes the reading unable to outlive the shell; either alone would have released the owner's phone. **Round 5 is intact where it mattered:** `CHECKING` is in flight, so the first frame of a fresh install is still paused, `lock()`'s defaults still answer PAUSED, the probe window / the network reach / a boot-time swap repair are still paused, and the sentence is unchanged; a package install still outranks everything (a *live* signal, not a reading), the watch surface is never paused, `reducedStart` still exempts safe mode, and the lock never weakens the gate beside it. 45.2's tour and 45.1's slides are byte-identical for a sixth time — and round 6 is what gives deviation 20's pause an **end**. **199 host cases green locally** (`SetupGatePolicyTest` 34 — round 5's `READY`-is-paused assertion reversed in place, plus *round 6 - the lock dies with the setup even when the facts are stale*: all three settled stages open every option with unusable facts beside them, all four in-flight stages still pause, a package install still outranks a settled stage, safe mode still outranks everything; `SetupGateWiringTest` 25 — the settled guard pinned, round 5's hand-listed pair and its `READY → USERLAND_STARTING` mapping pinned GONE, `facts.usable` still short-circuiting before it, and *a shell that is alive re-reads the disk facts*; = 76 guide/demo + 123 Phase 44). **Two harness lessons:** a stale-state bug needs a **set-shaped** assertion (a single-stage case would have passed on round 5's code for `FAILED`/`UNSUPPORTED` and missed `READY`, the stage the device actually reached), and `watchOption` returns a **non-null** `ChromeOption` by design, so "watches nothing" is asserted through `option(...).allowed` for every option. The harness was rebuilt from scratch again (`/tmp` lost: jdk4py + kotlinc 2.4.10 + the three shims + the runner) and the local checkout had again rolled back to the branch base — recovered with the standing rule (`git fetch origin <branch> && git reset --mixed FETCH_HEAD`, never `--hard`). **Next action: the owner's device rounds — [`phases/09-onboarding-setup/chat-phase45/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase45/DEVICE_ROUND.md) G1-G41 on the round-6 `Build APK` run <https://github.com/pabi277/CodeC/actions/runs/34719753700> (commit `8c3c10d`, artifact `CodeC-IDE-debug`) **after Settings → About → Reset tips** (**G41** = the release: fresh install, let the whole setup finish, do NOT kill the app, all four tabs unlock by themselves; G34 and G38 amended to say the same; the round-5 build `34714305062` does **not** have the fix), and [`phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md) R1-R8 (then D1-D12, whose lock rows are G34-G41) on the same phone — then `Start Phase 46` (Projects, not folders).** Queue after 45: 46 → 47 → 48 → 49 → 50. Records: [`phases/09-onboarding-setup/chat-phase44/PART_44_1_VISIBLE_SETUP.md`](../phases/09-onboarding-setup/chat-phase44/PART_44_1_VISIBLE_SETUP.md) §"Round 6 — *even after unpacking the userland it still stay lock*" (the lock's specification lives there), [`phases/09-onboarding-setup/chat-phase45/`](../phases/09-onboarding-setup/chat-phase45/README.md) §Round 6 (+ PART_45_2 §Round 6, PART_45_1 §Round 6 note, DEVICE_ROUND G41 + exit block), TROUBLESHOOTING §40 (+ a pointer in §39), JOURNEY §66, root README, rule.md §9, prompt.md, PHASE44_50_ROADMAP.

**Last updated:** 2026-09-12 (late night, round 5) · **Head: Phase 45 round 5 — the owner accepted round 4's lock but corrected its timing: *"Make it instantly after 1st open"*; the chrome lock is now PREFIX-keyed instead of stage-keyed, so it is on from the first frame; 🚧 IMPLEMENTED on `arena/01a0955a-codec`, **197 host cases green locally, CI ✅ GREEN on the round-5 commit (`34714305062`, tip `6c3cfea`, job `build` 25 steps 10m47s, zero annotations, release APK 6,675,254 B = −4 B against round 4, i.e. below what R8 can measure), device round G1-G40 NOT run.** Phase 44's round 2 is still pending on the same phone.** The report on round 4's build: *"The lock option is good but still it late user can switch before the start of userland download because is takes a little time to connect and user can switch task between them / Make it instantly after 1st open and others are ok"*. **The gap was round 4's own exemption:** `SetupLockPolicy.reasonFor` was **stage-keyed** — it paused only when a stage said work was moving — and `CHECKING` was deliberately exempt (*"the startup probe, not work"*). That argument holds for a working phone and fails for a fresh one: before the first byte the app reads the ledger, probes `bin/pkg` and `bin/bash`, reaches for the network and waits for the server's first answer, and on a cold radio that is seconds — while 44.1's launch divert only picks the *starting* tab, so one tap in the window landed the user on Packages reading *"not installed"* about tools already on their way. **Round 5:** with **no usable prefix and nothing given up on, every stage is paused**. New `ChromeLockReason.USERLAND_STARTING` covers `CHECKING` and a `READY` the disk contradicts, with the sentence *"Hang tight — CodeC is getting ready to set up its Linux tools. Other options are paused for a moment so this one-time setup finishes cleanly. The Terminal tab shows every step."* — **no percentage**, because none exists yet and 44.1's rule is never to invent one. Only `FAILED, UNSUPPORTED` answer `NONE` in the stage table now, and both protect a phone that can still be used (own sentence, own ⬇ retry, and **C still compiles offline**). Three consequences recorded as decisions: `lock()`'s **defaults are now the paused case** (the no-argument call *is* the first frame of a fresh install — a caller that has heard nothing yet must not default to open); a boot-time **repair** of an interrupted swap (`swapping` ⇒ `!usable`) is paused too, with the Terminal open where 44.2 logs the restore; and `lock`/`reasonFor` grew **`reducedStart`** (`MainActivity` passes `SafeMode.active`) so Phase 42.3's safe mode stays able to reach Settings — *export all projects* and *report a crash* — while an install the user **asked for** still outranks the exemption. **What keeps it safe is the order of the checks:** `facts.usable` short-circuits **before** the stage table, so an installed phone pauses nothing at any stage (including an upgrade moving through all three) and never sees a pause flash at launch, because `TerminalViewModel` builds the facts **synchronously from the disk in its constructor** (`computeSetupFacts(setupTracker.state)` → `SetupGatePolicy.factsFor(prefixDir, phase, progress)`). Nothing else moved: the watch surface is never paused, typing and `cc` never wait for a download, `SetupGatePolicy.can` remains the only answer about capability, and 45.2's tour is byte-identical — it just pauses a few seconds earlier (round 4's deviation 20 doing its job). **197 host cases green locally** (`SetupGatePolicyTest` 33 — the first frame is paused incl. the probe window, a repair and a disk-denied `READY`, a usable prefix pauses nothing at ANY stage, a reduced start pauses nothing while a package install still outranks it; `SetupGateWiringTest` 24 — `FAILED, UNSUPPORTED` are the ONLY stages answering NONE, `facts.usable` short-circuits before the stage table, `reducedStart = SafeMode.active` is passed, the facts are built in the constructor; = 76 guide/demo + 121 Phase 44). **The harness had to be rebuilt from scratch** (the sandbox lost `/tmp`: JRE via PyPI `jdk4py`, kotlinc via npm `kotlin-compiler` 2.4.10, the JUnit/`TemporaryFolder`/`CompilerSettings` shims, the compile list and the runner), and it taught three reusable lessons: **a fresh test-class instance per test method** (reusing one made five `DemoProjectSeedTest` cases fail on a shared `TemporaryFolder` root, looking exactly like production bugs), the shim `Assert` needs **`assertEquals(Double, Double, delta)`**, and `@get:Rule` needs `AnnotationTarget.PROPERTY_GETTER`. The local checkout had also rolled back to the branch base while the working tree held every round — recovered with the standing rule (`git fetch origin <branch> && git reset --mixed FETCH_HEAD`, never `--hard`). **Next action: the owner's device rounds — [`phases/09-onboarding-setup/chat-phase45/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase45/DEVICE_ROUND.md) G1-G40 (on `Build APK` <https://github.com/pabi277/CodeC/actions/runs/34714305062>, artifact `CodeC-IDE-debug`) **after Settings → About → Reset tips** (G39 wants a fresh install with a slow or absent network, G40 an installed phone), and [`phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md) R1-R8 (then D1-D12, whose lock rows are G34-G40) on the same phone — then `Start Phase 46` (Projects, not folders).** Queue after 45: 46 → 47 → 48 → 49 → 50. Records: [`phases/09-onboarding-setup/chat-phase44/PART_44_1_VISIBLE_SETUP.md`](../phases/09-onboarding-setup/chat-phase44/PART_44_1_VISIBLE_SETUP.md) §"Round 5 — *Make it instantly after 1st open*" (the lock's specification lives there), [`phases/09-onboarding-setup/chat-phase45/`](../phases/09-onboarding-setup/chat-phase45/README.md) §Round 5 (+ PART_45_2 §Round 5, PART_45_1 §Round 5 note, DEVICE_ROUND G39-G40), TROUBLESHOOTING §39, JOURNEY §65, root README, rule.md §9, prompt.md, PHASE44_50_ROADMAP.

**Last updated:** 2026-09-12 (late night, round 4) · **Head: Phase 45 round 4 — the owner ran the tour and asked for two things: ONE TAP per beat that really works the control, and an install that pauses the other options with a sweet message; 🚧 IMPLEMENTED on `arena/01a0955a-codec`, **193 host cases green locally, CI ✅ GREEN on the round-4 commit (`34711827176`, tip `e7759f1`, job `build` 11m29s, zero annotations, release APK 6,675,258 B = +5,400 B / +0.08% over round 3), device round G1-G38 NOT run.** Phase 44's round 2 is still pending on the same phone.** The report on round 3's build: *"It's good but i have 2 request- / 1. When the userland is installing and unpacking the user can not access any other other option and it will show a sweet massage of why can't access any other option / 2. Now the steps feel like an overlay on the botton so 1st click disappear the massage and i have to click 2nd time to really work but if someone don't click 2nd time it just cut off the flow of tutorial / So do something"*. **Diagnosis first (TROUBLESHOOTING §38):** request 2 is a fault in round 3's own mechanism — the overlay advanced on the **press** and left the tap **unconsumed**, trusting Compose to deliver the rest of the gesture to the control underneath, but the advance's own recomposition (`coachSeen` → `tourWaitsInDrawer` → the next box) rebuilds that control before the **lift** and a `clickable` whose node was rebuilt is cancelled: box gone, beat spent, nothing opened, and the tour then waited in silence for a control only the dead tap would have summoned. **Round 4 replaced the mechanism instead of nudging it:** every anchor publishes its control's **own click** beside its rect (`GuideAnchor.modifier(id, onClick)`; registry `rects` + `actions` + **`owners`**, where `withdraw(id, owner)` is honoured only by the site that published — beat 6 has ONE id with two publishers that swap in the same recomposition, and a blind withdraw let the leaving site wipe the arriving one's rect *and* click), pure `GuideTapPolicy.targetFor` picks the most specific anchored click under the press (smallest box, ties on tour order — the bar-wide hole resolves to the tab under the finger; an unanchored spot inside the hole is left to the app), `isTap` refuses a drag that travels and lifts outside the hole (a scroll performs nothing and spends no beat), and the overlay **performs the click, THEN advances**, swallowing the gesture so nothing fires twice. Two anchors publish no click on purpose: the terminal's status chip (a label) and a Packages card whose language is already installed (`cardClick`: `isInstalled → null`, `setupRefusal != null → onViewSetup`, else `onInstall` — the first draft published `onInstall` unconditionally and would have turned beat 8 into a **reinstall** nobody asked for; the rule is *publish the click of the control as it is rendered right now*). Beat 6's copy now teaches **tap**, not swipe, because while a box is up a swipe is not a tap (the swipe still works with the tour over). **Request 1 is a Phase 44 surface shipped beside the gate:** `SetupGatePolicy.can` answered **capability**, nothing answered **chrome**, so new pure `SetupLockPolicy` (`ChromeOption`/`ChromeLockReason`/`ChromeLock`, `lock/reasonFor/sweetMessage/watchOption/option/editorChromeLocked/optionForRoute`) pauses the tabs an install cannot serve — the userland's own install (DOWNLOADING/VERIFYING/EXTRACTING **and** `!facts.usable`) pauses all but **Terminal**, and a package install the user asked for (`OutputRunState.installing && busy` → `EditorChromeState.installRunning`, cleared on dispose) pauses all but the **Editor** plus ☰ / RUN ▶ / the drawer's edge swipe inside it. The law: **the surface that shows the install is never paused**; `CHECKING`, settled stages and an in-flight **upgrade** of a working prefix pause nothing; the lock never weakens the gate beside it (`RUN_C`/`EDIT_FILE` allowed in every stage — typing and `cc` never wait for a download). One sentence per reason (the download's carries the real percentage, never invented), shown once when the pause begins (`LaunchedEffect(chromeLock.locked)`, keyed on `locked` so three stages do not stack three snackbars) and again on every refused tap; paused tabs dim to 0.45 with a small 🔒 **before** they are tapped, and the scaffold grew a `snackbarHost` — a line, not a wall. The tour pauses with the chrome (`blockedByForeground … || chromeLock.locked`). **Deviation 17-20 recorded** (the overlay performs and swallows; beat 6 teaches tap; a label and an installed card publish no click; the lock is the fourth "someone else owns the screen" signal), and **the scope is flagged for the owner**: read literally, *"any other option"* would also close the file tree, the keyboard, saving and `cc` — this round locks chrome that leads away from, or collides with, the install, and the harder full-screen version is one small change to `SetupLockPolicy.option` plus a veil. **193 host cases green locally** (`CoachMarkPlanTest` 25, `GuideWiringTest` 19, `SetupGatePolicyTest` 30, `SetupGateWiringTest` 23, `GuidePlanTest` 15, `TooltipPlacementTest` 10, `DemoProjectSeedTest` 7 = 76 guide/demo + 117 Phase 44) and the harness learned a third pin shape: round 3's pins on the tap handler all passed on code whose taps were dead on a phone, because they pinned the mechanism's *prose*; round 4 pins **which lambda each beat performs**, **which anchors publish none**, and the **ORDER of two calls by index** (`perform` before `onAdvance`). **Next action: the owner's device rounds — [`phases/09-onboarding-setup/chat-phase45/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase45/DEVICE_ROUND.md) G1-G38 (on `Build APK` <https://github.com/pabi277/CodeC/actions/runs/34711827176>, artifact `CodeC-IDE-debug`) **after Settings → About → Reset tips** (G29-G33 the one-tap rule, G34-G38 the chrome lock, which is also Phase 44 round 2's lock rows), and [`phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md) R1-R8 (then D1-D12) on the same phone — then `Start Phase 46` (Projects, not folders).** Queue after 45: 46 → 47 → 48 → 49 → 50. Records: [`phases/09-onboarding-setup/chat-phase45/`](../phases/09-onboarding-setup/chat-phase45/README.md) §Round 4 (+ PART_45_2 §Round 4, deviations 17-20), [`phases/09-onboarding-setup/chat-phase44/PART_44_1_VISIBLE_SETUP.md`](../phases/09-onboarding-setup/chat-phase44/PART_44_1_VISIBLE_SETUP.md) §"Phase 45 round 4 — the chrome lock", TROUBLESHOOTING §38, JOURNEY §64, root README ("While an install is running, the rest of the app pauses" + the guide's one-tap bullets), rule.md §9, prompt.md, PHASE44_50_ROADMAP.

**Last updated:** 2026-09-12 (late night, round 3) · **Head: Phase 45 round 3 — the owner ran the tour, its SKIP is gone, and the ten beats now run first to last with a close at the end; 🚧 IMPLEMENTED on `arena/01a0955a-codec`, **CI ✅ GREEN on the round-3 commit (`34707337429`, tip `0fcb3b6`, job `build` 10m51s, assemble + `testDebugUnitTest` + `lintDebug`, zero errors, release APK 6,669,858 B = +5,288 B / +0.08% over round 2 and +18,176 B / +0.27% for the whole phase), device round G1-G28 NOT run.** Phase 44's round 2 is still pending on the same phone.** The report on round 2's build: *"You add the skip option and it's not a trough guide mean it got cut / I want a full process 1st to last without skip anything in this / At the end option to close and view again."* **Diagnosis first, four causes, all in round 2's own code** (TROUBLESHOOTING §37): SKIP TOUR was the most visible thing on every card and one tap spent all ten beats; the **pass-over law** skipped any beat whose control was not laid out at that instant, so the Packages box could arrive before the Flask preview did; two beats had no target when needed (the project-name box only where a switch would teach something; the tabs box only while the keyboard hid the bar); and the project picker closes the drawer the next beat lives in. **Round 3:** `waits` deleted — **every beat waits, in order**, and a later beat never jumps the queue; a tour card has **no button at all** and `markAllSeen` is deleted, so the seen set has exactly one writer (the tap on the highlighted control); **Back navigates** instead of ending anything, and the same beat is there on return, unspent; **while a beat waits nothing is drawn**, which is the safety argument that replaces the skip (the overlay can never cover the app, so a tour with no exit is not a trap); and the **finish card** is the only one with buttons — **VIEW AGAIN** (empty seen set + navigate to the editor, where beat 1 lives) and **CLOSE** (in-memory, so an owed beat stays owed). It is **earned, not remembered** (`isFinished` + `ranThisSession`), so an install that starts complete is not greeted by it on every launch. Beat 2 is anchored on the drawer header in **every** project state, beat 6 on the **tab bar as well as the reveal handle** (one id, two publishers), and the editor **reopens the drawer after a project pick** when `nextBeatIsInDrawer` says the beat it waits for is behind it. The only thing that can still pass a beat is the **stall guard**: 20 s, and only for a control that **could be on this route and is missing** (`waitingOn` + `anchorsPossibleOn(route)`, `ChromeState.route` added for it) — never for a blocked screen, a drawer in the other state, or a beat the user must travel to, because timing those out cascades (the preview would stall during a Python install, then every beat behind it). A passed beat is **never marked seen**. **Deviation 13: the no-nag law's "skippable with a single tap" is reversed for the tour by the owner's own instruction** — the slides keep SKIP and 45.1 is untouched for a third time. **180 host cases green locally, and CI green on them too** (`CoachMarkPlanTest` 21, `GuideWiringTest` 18, `GuidePlanTest` 15, `TooltipPlacementTest` 10, `DemoProjectSeedTest` 7, Phase 44's 109 unchanged), and the pins now count rather than grep words (2 publishers of the beat-6 anchor, 1 writer of the seen set, 2 drawer gates) and slice source between markers to prove a *region* has no button. **Next action: the owner's device rounds — [`phases/09-onboarding-setup/chat-phase45/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase45/DEVICE_ROUND.md) G1-G28 **after Settings → About → Reset tips** (G28 is the optional 20-second stall-guard row), and [`phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md) R1-R8 (then D1-D12) on the same phone — then `Start Phase 46` (Projects, not folders).** Queue after 45: 46 → 47 → 48 → 49 → 50. Records: [`phases/09-onboarding-setup/chat-phase45/`](../phases/09-onboarding-setup/chat-phase45/README.md) §Round 3 (+ PART_45_2 §Round 3, deviations 13-16), TROUBLESHOOTING §37, JOURNEY §63, root README "The guide: five slides, then one tour".

**Last updated:** 2026-09-12 (late night) · **Head: Phase 45 round 2 — the owner ran the guide and 45.2 was rebuilt as ONE TEN-BEAT TOUR; 🚧 IMPLEMENTED on `arena/01a0955a-codec`, **CI ✅ GREEN on the round-2 commit (`34704379023`, tip `acadaee`, job `build` 10m41s, release APK 6,664,570 B = +96 B over round 1), device round G1-G23 NOT run.** Phase 44's round 2 is still pending on the same phone.** The owner's report on round 1's build (*"the guided box are not consistent with flow … Not showing the full box guide at one and you didn't add all … remove the next option only the guide will show click the option where showing the guide to the next … make it like demo_flask is always present"*) plus the flow he dictated (*"☰ bar → change the project folder to demo_flask → selected app.py → run → install → python → it will open the flusk web → close → tap to reveal the keyboard below option → then a small tour of package and terminal"*) are now code. **Diagnosis first, and all four causes were in round 1's own code** (TROUBLESHOOTING §36): a fresh install blocked every box during Phase 44's download; `MAX_PER_ARRIVAL = 2` + the surface filter hid most beats; the card was placed with a **150dp estimated height** so a taller card was clamped over its own hole; and a box could be cut **behind a dialog or a closed drawer**. **Round 2:** ten ordered beats (five new anchors: the drawer's project header, the drawer's `app.py` row, the preview's Back, the bottom bar's Packages + Terminal tabs) under three pure laws — order, `waits` (the four always-there controls stop the tour until they are on screen, which is also why it can only start at the editor's ☰), and pass-over-**without**-spending — plus `EditorChromeState.dialogOpen` / `drawerOpen` so nothing is cut behind a modal. **The highlighted control is the only way forward:** no NEXT/GOT IT, outside taps swallow the whole gesture and do nothing, **SKIP TOUR**/back call `markAllSeen` (one tap ends the tour; Reset tips is the door back), and every card reads `Tour · n of 10` with a **measured** height. The slides are untouched by the owner's own decision. **`demo_flask` is now ALWAYS present** (re-seeded when missing, never overwritten — a recorded reversal of Phase 14's one-time law) because beats 2-3 teach it by name. **Recorded limit:** a box cannot point into an `AlertDialog` (its own window), so the project picker and the *Install Python?* prompt are taught by the copy of the beat before them; screen-absolute anchors are the follow-up if the owner wants a hole on **Install** itself. **Delegated decision:** Phase 44.1's terminal-first divert stays (*"you do as you like"*), so a fresh install still watches the download before the tour starts. **175 host cases green locally, and CI green on them too** (`CoachMarkPlanTest` 18, `GuideWiringTest` 16, `DemoProjectSeedTest` 7 — now in the harness — `GuidePlanTest` 15, `TooltipPlacementTest` 10, Phase 44's 109 unchanged); the round caught two real faults before CI (a deleted `arrivalKey` argument still being passed, and a pin that grepped the word "GOT IT" out of a sentence promising there is none). **Next action: the owner's device rounds — [`phases/09-onboarding-setup/chat-phase45/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase45/DEVICE_ROUND.md) G1-G23 **after Settings → About → Reset tips**, and [`phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md) R1-R8 (then D1-D12) on the same phone — then `Start Phase 46` (Projects, not folders).** Queue after 45: 46 → 47 → 48 → 49 → 50. Records: [`phases/09-onboarding-setup/chat-phase45/`](../phases/09-onboarding-setup/chat-phase45/README.md) §Round 2 (+ PART_45_2 §Round 2, deviations 9-12), TROUBLESHOOTING §36, JOURNEY §62, root README "The guide: five slides, then one tour".

**Last updated:** 2026-09-12 (night) · **Head: Phase 45 (The guide) — 🚧 IMPLEMENTED on `arena/01a0955a-codec` (tip `3c597b2`); **CI ✅ GREEN round 1 (`34698914219`)**; device round NOT run. Phase 44's round 2 is still pending on the same phone.** The owner's row 2 — *"It has 0 guide features to give the user a real knowledge how to use the app, user don't know where should they change the project or file and the tap to the open down side of the keyboard"* → *"Set a step by step user guide after opening the app 1st time with a open view again[ing]"*, clarified as **"both layers"** — is now code in the house shape (**pure plan + thin Android edge**): **45.1** `ui/guide/GuidePlan.kt` + `GuideScreen.kt` = five slides (☰/files → RUN ▶ → the one-time download → Terminal → Projects vs single files) as the **second** first-launch gate (tiles → guide → shell, decided *before* Phase 44's terminal divert so the guide never shares a screen with the setup bar), SKIP on every slide, back = SKIP, `guide_completed` written only by an explicit user action and read once at startup, and **three doors back** (Settings → About → Help & guide · Projects ⋮ → Guide · editor ☰ drawer footer → Guide — not a nav route, so Phase 49's `BackRouter` does not inherit it); **45.2** `ui/guide/CoachMarkPlan.kt` + `CoachMarks.kt` = five spotlights over three surfaces, ≤ 2 per arrival, one per anchor for the app's life (`coach_marks_seen_csv`), where **`nextUnseen` only returns a step whose anchor the caller reports visible** and an all-hidden surface marks nothing seen — so the *"Show tabs"* mark (the owner's *"tap to the open down side of the keyboard"*) can never point at a handle that is not there. Anchors publish `boundsInWindow()` and **withdraw on dispose**; the overlay is a drawing-only `Canvas` (four rects + a rounded stroke, no `BlendMode.Clear` offscreen layer) above the `Scaffold`, and its tap layer consumes **only outside** the hole so tapping the highlighted control performs its own action *and* closes the mark. Marks are suppressed by the exit survey, safe mode, and a Phase 44 stage that is actually moving (not CHECKING — that is a startup transient, and blocking on it would mean no mark ever shows on a fresh phone). Settings → **Reset tips** clears exactly two keys in one atomic edit. **The copy is pinned to the product:** `GuideVocabulary` extracts what a slide names (☰ ▶ ⋮, ALL-CAPS, mid-sentence capitals, backticks) and every term needs a `GuideTermProof(term, path, needle)` checked against the real tree — it caught the plan's own slide 5 (*"⋮ → Open in editor"* → the real label is **"Open"**). **50 new host cases** (`GuidePlanTest` 15 · `CoachMarkPlanTest` 12 · `TooltipPlacementTest` 10 · `GuideWiringTest` 13) = **159 green locally**; `SettingsAuditTest` kept green by adding rows 53-54 to `docs/phases/10-app-polish-settings/chat-phase38/SETTINGS_AUDIT.md` in the same commit (62 controls, About 20). No new dependency, permission, route or key without a reader. **CI round 1 ✅ GREEN** — `Build APK` `34698914219` on tip `3c597b2`: `conclusion: success`, job `build` 9m55s, zero error annotations, and per `rule.md` §5 the legacy assemble step delegates to `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, so the 50 new cases ran on real Gradle/JUnit/Robolectric; release APK **6,664,474 B (+12,792 B / +0.19%** over Phase 44 round 4), debug 25,661,856 B, v1.3.17; the only annotations are the repo-wide Node 20 → 24 runner deprecations. **Next action: the owner's device rounds — [`phases/09-onboarding-setup/chat-phase45/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase45/DEVICE_ROUND.md) G1-G14 *and* [`phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md) R1-R8 (then D1-D12 fresh) on the same phone — then `Start Phase 46` (Projects, not folders).** Queue after 45: 46 → 47 → 48 → 49 → 50. Records: [`phases/09-onboarding-setup/chat-phase45/`](../phases/09-onboarding-setup/chat-phase45/README.md) (implementation record + 8 deviations), JOURNEY §61, root README "The guide (Phase 45)", SETTINGS_AUDIT rows 53-54.

**Last updated:** 2026-09-12 (evening) · **Head: Phase 44 device round 1 🔴 FAILED on the owner's phone — four reports, three root causes, all FIXED and CI ✅ GREEN (`34695797493`, tip `4bf3c4c`, 10m53s, zero error annotations, artifacts `CodeC-IDE-release` 6.65 MB / `CodeC-IDE-debug` 25.6 MB, v1.3.17 — so the 109 host cases ran on real Gradle/JUnit); only the owner's round 2 is left.** The owner installed the round-3 artifact (`ba51382`, CI ✅ `34693462725`) **over an existing install** and reported: *"I couldn't not open the terminal it's opening the editor"* · *"The top a massage 'C works right now. The linux tool need one install- open terminal and tap download'"* · *"But it's not closing or opening terminal"* · *"Every package saying view setup but terminal not opening editor opening"*. What was actually wrong: **(1)** `installIfNeeded(force = false)` answers `AlreadyInstalled` from the release **marker** alone, and a *pre-44* build wrote that marker **before** the swap — so his phone had a valid marker over a prefix with no working `bin/pkg`: stage `READY`, facts *not usable*, the bar's "still need one install" sentence forever, and every Packages row refusing. Fixed by making the `AlreadyInstalled` branch ask the **disk** (`SetupGatePolicy.userlandUsable` → `FAILED(BROKEN_USERLAND)`, wording now *"the Linux tools aren't working"*) plus `refreshSetupFromDiskWhenIdle()` after the boot repair; **(2)** that bar was a **wall** — the action button rendered only `if (inFlight || stage == FAILED)` and the ✕ cleared a note that was not there. Fixed: the whole bar is one tap to the terminal, **VIEW SETUP** always renders, the ✕ is real (`barDismissAllowed` = `progress.settled`, remembering the dismissed text so a changed state returns); **(3)** `restoreState = true` restores a **whole saved sub-stack**, not a tab — so "go to the terminal" landed on an editor the user had opened above it. Fixed for every navigation that means *show me the terminal* (setup bar, Packages VIEW SETUP, editor hand-off, launch divert, bottom-bar Terminal tab); the other four tabs keep pre-44 behaviour and **Phase 49 stays the systematic nav pass**. Also fixed: the launch divert was keyed on the first-run welcome, so an *updated* install never opened the terminal first — it is now `SetupGatePolicy.startOnTerminal(usable, abiSupported)` from the **disk**, applied via `navigate()` after the first composition (never an argument-carrying `startDestination`). Deliberately **not** done: an automatic re-download for the marker-only state (~40 MB on possibly mobile data; the law is *no surprises*) — one call away if the owner asks. **109 host cases** green locally (`SetupGatePolicyTest` 25, `SetupGateWiringTest` 20 incl. "an install marker alone is never accepted as ready" and "every go-to-the-terminal navigation arrives at the terminal"). **Next action: the owner's round 2 on the `4bf3c4c` build — [`phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md) rows R1-R8 first (the four failures), then D1-D12 on a fresh install; owner-facing explanation in TROUBLESHOOTING §35 — then `Start Phase 45`.** Queue after 44: 45 → 46 → 47 → 48 → 49 → 50. Records: [`phases/09-onboarding-setup/chat-phase44/`](../phases/09-onboarding-setup/chat-phase44/README.md) (device round 1 section), JOURNEY §60, TROUBLESHOOTING §33/§34/§35.

**Last updated:** 2026-09-12 · **Head: Phase 44 (Setup you can see, and cannot half-finish) — 🚧 IMPLEMENTED on `arena/01a0955a-codec`; two red CI rounds fixed for cause (round 1 `34692621773` = seven `Unresolved reference` errors from ONE missing `ledger` parameter on the installer's secondary constructor, TROUBLESHOOTING §33; round 2 `34692963464` = two lint `NewApi` errors because a *pure* file used `java.nio.file`, §34); round 3 `34693462725` on tip `ba51382` was **watched to success** (`gh run watch --exit-status` → 0, the APK-size/artifact/release steps ran, zero error annotations) but the GitHub token expired before its `conclusion` could be re-read, so it is recorded as *observed green, unconfirmed* — first action next session: `gh run view 34693462725 --json conclusion`; device round NOT run.** Both parts shipped in one round: **44.1** the install is visible everywhere — a pure `SetupProgressParser` + `SetupGatePolicy` + `SetupTracker`/`SetupAnnouncer` in `ui/terminal/SetupState.kt`, the `SetupBar` rendered between `SafeModeBanner` and `NavHost` so **every tab** shows `Setting up CodeC's Linux tools — 42 % · C works right now`, **Terminal-first on a fresh install** (`MainActivity.startDestination`) with the starter C file auto-opened when setup settles, the owner's `Don't close CodeC — it is finishing a one-time setup (42 %)` row in `TerminalScreen`, a stage-aware chip (`TerminalStatusLabel.label` → `downloading userland 62 %`), the same sentence in the status bar via `TerminalForegroundService.start(context, status, percent)` (one channel, caught FGS-start failure), and the wake lock + FGS taken **before the first byte** and handed over to the session path when a shell starts; the Packages tab refuses with one honest sentence + **VIEW SETUP** (all four `sendCommand` sites gated, an installed package keeps **RUN**) and `EditorViewModel.confirmInstall` refuses through the same policy. **C is never gated** (`RUN_C`/`EDIT_FILE` `Allowed` in every stage). **44.2** a kill is harmless — `SetupLedger` (own SharedPreferences file, `commit()` never `apply()`, corrupt → `IDLE`) + pure `resumePlan()` (`RestoreOld/Reextract/RetryDownload/SweepOnly/FreshInstall/Nothing`), `SetupRecovery.recover()` on a daemon thread in `MainActivity.onCreate` **next to `TempGc`** (restore the newest `usr.old-*` only when `usr` is missing; sweep only `usr.old-<digits>`/`.userland-staging-<digits>` inside `filesDir`, never anything newer than the repair; always `SetupRecoveryGate.finished()`), `SetupRecoveryGate.awaitFinished()` so the installer waits for the boot repair, the post-install marker moved **after** the swap, and `SetupGatePolicy.userlandUsable` checking the **real** `$PREFIX/bin/pkg` + shell instead of the marker. **109 host cases** in seven classes green locally through the `rule.md` §9 kotlinc harness (`SetupGatePolicyTest` 25 · `SetupProgressParseTest` 22 · `SetupLedgerTest` 13 · `SwapRecoveryTest` 16 · `UserlandUsableTest` 9 · `SetupGateWiringTest` 20 · `TerminalStatusLabelTest` 4) — the Compose/JNI edges cannot compile here (no `android.jar`), so **CI is the executor of record**; deviations are written down in the part docs (`OPEN_TERMINAL` never refused; the planned Robolectric `SetupStateVmTest` replaced by pure tracker tests + wiring pins; bar dismissible only when settled **and** usable; `swapPrefix` names untouched so `UserlandInstallerTest` stays green; one repair entry point). No new dependency, DataStore key, Settings control, permission or telemetry. **Next action: the owner's device round — [`phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md`](../phases/09-onboarding-setup/chat-phase44/DEVICE_ROUND.md), 12 rows including the three kill points (mid-download / mid-extract / mid-swap) and `pkg install python` afterwards; owner-facing explanation in TROUBLESHOOTING §32 — then `Start Phase 45`.** Queue after 44: 45 → 46 → 47 → 48 → 49 → 50. Records: [`phases/09-onboarding-setup/chat-phase44/`](../phases/09-onboarding-setup/chat-phase44/README.md), JOURNEY §60, [`PHASE44_50_ROADMAP.md`](../roadmaps/PHASE44_50_ROADMAP.md).

**Last updated:** 2026-09-12 · **Head: the Phases 44-50 plan ✅ COMPLETE & MERGED to `main` via [PR #75](https://github.com/pabi277/CodeC/pull/75) (owner: *"If everything done merge it"*, merge commit `8eff438`, CI ✅ GREEN `34689859665` push / `34689923817` PR / `34690414244` on `main` post-merge, 28 files +3 896/−538 with zero `app/src` files so no device round applies) — the owner's test-phase bug report became seven researched phases, numbered so the numbers are the order of work: 44 → 45 → 46 → 47 → 48 → 49 → 50. Phase 43 is ❌ CANCELLED and superseded by 46.** What each number is — **44** *setup is visible and cannot half-finish* (44.1 the in-app setup bar + `SetupVerdict`, because today the one-time userland download starts at process start (`TerminalViewModel.kt:205`) and its progress only ever reaches the emulator buffer, with no FGS and no wake lock during the download; 44.2 `SetupLedger` + an atomic, resumable, self-repairing install — `UserlandInstaller.swapPrefix:377` has a kill window that leaves no `usr` **and** an orphan `usr.old-*` that nothing ever cleans, which is exactly the owner's "closed the app mid-install → `pkg` errors later"); **45** *the guide* (45.1 five first-run slides in the order the features are actually met + three re-open doors; 45.2 coach marks on first arrival at Editor/Terminal/Packages, ≤2 per surface, 5 total, never nagging — today the only guide surface in the whole app is the dismiss-only "Typing tips" dialog at `EditorScreen.kt:726-750`); **46** *projects, not folders* (46.1 delete "open a folder" from every surface — `FileManagerScreen.kt:180-188,1077-1080,1474-1481`, `FileManagerViewModel.importFolder:357-382`, `ProjectTransfer.copyDocumentTree` — and pin the deletion with a source-scan test; 46.2 one tap on a file opens **that one file** in an editor with its real path, and "Open in editor" (the full project) moves to the project card's ⋮ menu, which today has no such row); **47** *editor chrome* (47.1 the drawer gets a close ✕ and an in-drawer PROJECTS list, and the context-switcher dialog — whose title is literally `"Open folder"` at `EditorScreen.kt:1922` while listing only projects and single files — is deleted; 47.2 the **system keyboard becomes the default** and CodeC Keys becomes opt-in, an explicit reversal of the Phase 28 "DEFAULT ON per owner round 2" decision on the owner's instruction, with stored choices still winning); **48** *the caret is always above the keyboard* (the column is already `imePadding()`'d, so sora is resized but never asked to re-scroll — `ensurePositionVisible` has **zero** call sites in `app/src/main`; the fix is a pure `CaretVisibilityPolicy` + one sora call after layout settles); **49** *back does the obvious thing everywhere* (49.1 a pure `BackRouter` precedence table replacing the app's only two `BackHandler` sites, `MainActivity.kt:801` and `EditorScreen.kt:646` — the drawer and the hub file tree both exit the app today; 49.2 the exit prompt decided from **state** instead of `popBackStack()`'s return value, kept ON per the owner, plus a Settings door for gesture-nav devices where a home swipe can never show it); **50** *the cross-device round* (`DEVICE_MATRIX.md`: 4 device classes, 49 rows, results pasted back into each part file's `## Test log`). Standing decisions for the series: no new dependencies (guide + coach marks built in-house per `rule.md` §6), every new DataStore key needs an out-of-store reader, every new Settings control needs a `SETTINGS_AUDIT.md` row in the same commit, and no modal nag may ever appear twice. Owner clarifications of record: 4.iv = **both** the editor drawer and the hub file tree (plus an audit of every screen) · 5.B = **keep the prompt ON, make it consistent** · 4.ii = **in-drawer project picker, not a SAF folder picker** · guide = **slides *and* coach marks**. **Next action: `Start Phase 44`.** Open follow-up, deliberately not smuggled into PR #75: PR #74's `web_docs` v2.2 still calls Phase 43 *planned* (`web_docs/DECISIONS.md` D21, `web_docs/getting-started/NEXT_STEPS.md:29,46`, `web_docs/WEBSITE_PLAN.md:142,230` — ch-06 "safe folder walk" + "open folder as project"), so it needs a v2.2→v2.3 sync under the website's own W1-W6 ceremony. Queue: 44 → 45 → 46 → 47 → 48 → 49 → 50 (Phase 42 — share-readiness — is ✅ COMPLETE & MERGED via [PR #71](https://github.com/pabi277/CodeC/pull/71), so 44 → 50 is the whole remaining queue). Records: [`PHASE44_50_UX_RESEARCH.md`](../research/PHASE44_50_UX_RESEARCH.md) (the dossier — every claim carries a `file:line`), [`PHASE44_50_ROADMAP.md`](../roadmaps/PHASE44_50_ROADMAP.md), [`phases/09-onboarding-setup/chat-phase44/`](../phases/09-onboarding-setup/chat-phase44/README.md) … [`phases/11-phone-ui-parity/chat-phase50/`](../phases/11-phone-ui-parity/chat-phase50/README.md), JOURNEY §59.

**Last updated:** 2026-09-11 · **Head: Phase 41 (Feedback that reaches you) — ✅ COMPLETE & MERGED to `main` via [PR #70](https://github.com/pabi277/CodeC/pull/70) on the owner's command ("Merge it")** — device round 1 passed 8/8; the follow-up (separate `FeedbackScreen` + the exit survey) and round 2 (hardcoded `DeveloperContact` = +91 62967 46606 / chakraborttypabi2772006@gmail.com, reply-to fields + contact store deleted) shipped in the same PR, all CI green (`34525080153` → `34533659266`; the one red in between was the unrelated `UserlandInstallerTest` loopback flake, TROUBLESHOOTING §31). Round-2 device pass (D1–D8) not separately reported — the owner's call; the runbook stays in `phases/08-release-support/chat-phase41/DEVICE_TEST_PLAN.md`. **Next action: `Start Phase 42` (share-readiness — signing, non-debuggable artifact, the broken in-app updater, size, backup rules, crash-loop guard, export-all; also where the exit prompt's long-term fate gets decided).** Queue: 42 → 43.


# CodeC — historical Phase 3 task list and current handoff

**Last updated:** 2026-09-10 · **Head: Phase 38 (Identity: app brand/icon + Settings trim) ✅ COMPLETE, DEVICE-PASSED & MERGED to `main` via PR #64 at `dcd65b4` (owner: "Start Phase 38" → "All device test pass … then marge it"; 4 commits, CI GREEN on every one incl. the PR check `34445023100`) — CI ✅ GREEN (`Build APK` `34442522565` on tip `ce1a38c`, docs follow-up `34443027257`; the 45 new host cases executed inside the assemble step) (5 m 57 s; the new `Check brand/icon assets (Phase 38.1)` CI step passed first try; `CodeC-IDE` 24 815 485 B = **−32 351 B / −0.13 % vs `main`** — the deleted template webps outweigh the new rasters); the device round ✅ PASSED by owner report ("All device test pass", 2026-09-10 — no device details supplied, none invented), and the merge was commanded and executed in the same message. `CodeC-IDE` 24 815 485 B = **−32 351 B / −0.13 % vs `main`** (the deleted template webps outweigh the new rasters).** What shipped: **38.1** the original `>_` mark (`docs/brand/icon/codec-mark.svg`, safe-zone-verified) as flat adaptive layers + a **real monochrome layer** (the template pointed `<monochrome>` at the full-colour foreground), 10 committed density rasters + `codec-512.png` generated by `scripts/render_icon.mjs` (sharp 0.35.4; **two runs = identical md5s**), template `.webp`s deleted in the same commit, `ic_stat_codec` notification silhouette replacing `ic_launcher_foreground` in both foreground services **and** the system `ic_dialog_info` in `CodecApiBridge`, `app_mark` in the About header, README logo; **38.2** Settings 13 → 11 sections (Termux Engine card deleted, compiler sections merged into one **Compiler**; the fallback engine + `RUN_COMMAND` permission + `<queries>` all untouched), the four Termux steps moved to the error path (`CompilerRemediation` → `finishFailedBuild` Output-Panel line; wording source `TROUBLESHOOTING.md` §27), and the audit's three extra finds — the duplicate Appearance Terminal-Theme dropdown, the duplicate "Licenses" item, and two reader-less store keys (`recent_files_csv` write-only since it existed, `smart_typing_delete_word` never read) deleted with their call sites. [`SETTINGS_AUDIT.md`](../phases/10-app-polish-settings/chat-phase38/SETTINGS_AUDIT.md) (43 rows) is pinned to the screen by `SettingsAuditTest`; `SettingsKeysHaveReadersTest` walks key → flow → reader for all 41 keys across the three stores so the next dead key fails the build. **Next action: `Start Phase 39` (outputs are temporary, never in your repo).** Remaining queue: 39 → 40 → 41 → 42 → 43 (the numbers are the order; 39.1 before 43.2 is the one ordering rule). Records: [`phases/10-app-polish-settings/chat-phase38/`](../phases/10-app-polish-settings/chat-phase38/README.md), JOURNEY §54.

**Last updated:** 2026-09-10 · **Head: ✅ MERGED — PR #62 took the whole session branch (`arena/01a0872e-codec`, 9 commits) into `main` at `e89dc49` on the owner's "Merge everything" (2026-09-10).** What landed: **Phase 37 — Device as server (LAN)** (37.1 opt-in `0.0.0.0` bind + the two-URL share panel + ZXing QR, 37.2 `ServerRegistry`/`ServerHost` port-and-process truth with keep-alive on the existing `RunForegroundService`, and the device round's 🌐 open-in-browser follow-up; 68 host cases in ten classes; the owner ran all eight exit checks on phone + second device and reported "All pass"; `CodeC-IDE` 24 847 836 B = **+359 152 B / +1.47 %** over the `main` it replaced) and **the Phases 38-43 plan** (docs only, renumbered so **the numbers are the order of work**: 38 identity (brand/icon + Settings trim), 39 outputs/ignore policy, 40 GitHub that tells the truth, 41 WhatsApp feedback, 42 share-readiness, 43 file system strength; `docs/roadmaps/PHASE38_43_ROADMAP.md` + `docs/research/PHASE38_43_OSS_RESEARCH.md` + 20 part docs, with **39.1 before 43.2** as the only ordering rule). **Next action: `Start Phase 38`.** Standing decisions for the whole series: `git` CLI stays (JGit rejected), `targetSdk = 28` stays (it is what keeps downloaded compilers executable, and why CodeC ships from GitHub Releases rather than Play), the user's own `.gitignore` always beats CodeC's `.git/info/exclude` entries, no telemetry, and no phase may make `MANAGE_EXTERNAL_STORAGE` a requirement. Device gates remain the owner's, CI is the executor of record, and a merge still needs an explicit owner command. Records: JOURNEY §52/§53, [`phases/07-platform-capabilities/chat-phase37/`](../phases/07-platform-capabilities/chat-phase37/README.md) and [`phases/10-app-polish-settings/chat-phase38/`](../phases/10-app-polish-settings/chat-phase38/README.md) … [`phases/13-archive/chat-phase43/`](../phases/13-archive/chat-phase43/README.md).

**Previous head (2026-09-10): **Head: Phases 38-43 📋 PLANNED (docs-only, no code) — the owner's six "before I share the app" ideas became six researched phases, **renumbered so the numbers are the order of work: 38 → 39 → 40 → 41 → 42 → 43**. Phase 37's merge stays HELD at the owner's command.** What each number is — **38** Identity: the original adaptive launcher mark (real `monochrome` layer + `ic_stat_codec` for both notifications, rasters generated once by a script and committed) and the Settings trim ("Termux Engine" card deleted, `TermuxCompiler` fallback + `RUN_COMMAND` kept, guidance moved into the C error path); **39** Outputs are temporary, never in your repository (`RunArtifacts` routes every language's build output to `filesDir/CodeC/temp/runs/<stamp>/` + `TempGc`, and `RepoHygiene`'s ~60 patterns incl. `.codec/` are enforced *inside* `stageAll`, with the user's own `.gitignore` always winning); **40** GitHub that tells the truth (`GitReadiness`/`GitBlocker` — the clone error shows *inside* the dialog, `isAvailable()` finally called; `GitPushOutcome`/`GitPushParser` result card separating "on the remote" from "committed here"; `GitHubPublish` via `POST /user/repos`, private by default, `ls-remote`-verified adopt-existing); **41** Feedback that reaches you (`FeedbackDraft` → `https://wa.me/<digits>?text=` with the owner's number as a **setting**, email/copy/GitHub-issue fallbacks, `GitRedactor` plus its own tested pattern table, no telemetry, three honest disclosure lines); **42** Share-readiness (release-signed non-debuggable artifact + `app-v*` tags + `UpdatePolicy` reusing `UserlandInstaller`'s sha256/resume/stage discipline because `releases/latest` today resolves to `userland-v1`; measured R8 + `splits.abi` **plus** an `assets/tcc` exclude since splits don't filter `assets/`; real backup rules — today the XMLs are the AS samples, so `files/usr` and the `git_token` DataStore are backup-eligible; a crash-loop guard added to the existing `CrashReportOverlay`; `exportAllZip` over **both** project roots); **43** File system strength (bounded `Throwable`-safe folder walk — the crash is a `StackOverflowError` escaping an unbounded `copyDocumentChildren` caught only as `Exception`; then open-folder-as-project via `ProjectLink` + a *persisted* SAF grant). **One dependency to respect inside the order: 39.1 before 43.2**, so a linked folder never receives CodeC's build outputs. Docs: [`PHASE38_43_ROADMAP.md`](../roadmaps/PHASE38_43_ROADMAP.md), [`PHASE38_43_OSS_RESEARCH.md`](../research/PHASE38_43_OSS_RESEARCH.md), [`phases/10-app-polish-settings/chat-phase38/`](../phases/10-app-polish-settings/chat-phase38/README.md) … [`phases/13-archive/chat-phase43/`](../phases/13-archive/chat-phase43/README.md), JOURNEY §52. **CI for the planning commit is ✅ GREEN — `Build APK` `34433912076` on tip `8d365c4` (6 m 18 s; `CodeC-IDE` 24 847 809 B, 97 B *under* the previous tip's build with no `app/` file changed — which is exactly why 42.2 sets a ~0.1 % noise floor before it claims any size win). Nothing here is implemented; each phase starts on the owner's "Start Phase N", and Phase 37's merge stays HELD at the owner's command.**

**Previous head (2026-09-10):** **Phase 37 Device as server (LAN) ✅ DEVICE-PASSED on `arena/01a0872e-codec` — the owner's round returned "All pass" on all eight exit checks (phone + second device, same Wi-Fi), and the one thing that round asked for is now shipped: 🌐 each share row opens in the phone's default browser, not only copies.** `On this device` opens the loopback URL (no network needed), `Other devices` opens the LAN URL — the same string the QR encodes. The policy is pure and host-tested (`ui/services/ShareActions.kt`: right URL per row, a button only for a real `http(s)://` URL, per-row labels, the failure message), the Android half is `ui/services/OpenInBrowser.kt`, now the app's **single** `ACTION_VIEW` launcher (the terminal's link handler delegates to it), and a launch that no browser can serve **copies the link** and says so, so no tap loses a URL. Copy and the in-app 👁 preview stay exactly where they were. Tests: `ShareActionsTest` (6) on top of the phase's 62 — 68 cases in ten classes; local service-set re-run **86/86 green**. **CI on that commit is ✅ GREEN** — `Build APK` `34398031696` on tip `98cb2b4` (9 m 35 s; the `gradle-bootstrap` bridge runs assemble + `:app:testDebugUnitTest` + `:app:lintDebug`, so `ShareActionsTest` executed there), artifact `CodeC-IDE` 24 847 792 B = +3 485 B over the docs tip. **Merge still HELD for the owner's command.** Records: [`phases/07-platform-capabilities/chat-phase37/`](../phases/07-platform-capabilities/chat-phase37/README.md), JOURNEY §50/§51.

**Previous head (2026-09-09):** **Phase 37 Device as server (LAN) 🚧 IMPLEMENTED on `arena/01a0872e-codec` (owner: "Start Phase 37") — ✅ CI GREEN (`Build APK` `34393543928` on tip `6d36a83`: assemble + `:app:testDebugUnitTest` + `:app:lintDebug`; APK `CodeC-IDE` 24 844 344 B = +355 660 B vs the `main` build) — the two-device pass is OUTSTANDING, so no acceptance is claimed and merge is HELD.** 37.1: opt-in LAN switch (`LanSharePolicy`, OFF by default, not persisted) → `WebPreviewServer` binds `0.0.0.0` (pool 8100–8199, never < 1024) and the flask/fastapi/C templates honour `CODEC_SERVER_HOST`; `LanAddress` (pure) + `LanAddressProvider` (Android) resolve the peer address; `ServerEndpoints.of(port, bind, lanAddress, lanShared)` is the single place the two URLs exist and refuses to advertise a LAN URL a peer could not open (the panel states why instead); `ServerSharePanel` shows both URLs with copy + a **ZXing `core` 3.5.4** QR (Apache-2.0, `assets/licenses/ZXING_APACHE2.txt`, About line) in the Output Panel and under the preview address bar. 37.2: `ServerRegistry` is the one owner of port truth and `ServerHost` the one owner of the socket/process, so leaving the editor keeps a **LAN** server serving while a loopback preview still dies with the screen; the same `RunForegroundService` (no second type) carries `Serving <project> on <ip>:<port>` + Stop, with the ticker suppressed while serving. Three real bugs were caught by the host tests and fixed: error responses sending `Content-Length` without a body, Stop orphaning the server instead of releasing the port (`ServerLaunch` exec), and `stop()` racing a blocked `accept()` so a re-run could not rebind. Tests: 9 new classes / 62 cases (+1 `ProjectScaffoldTest`, +2 `ServerScaffoldE2ETest`); local pre-validation 96/96 green over the real production files. **CI rounds: `34391044725` RED (zxing hint key `CHARACTER_SET`), `34392284734` RED on two test-only causes (`TemporaryFolder` name reuse; a guessed QR pixel offset) — both fixed for cause, no assertion loosened.** **Owner action: run the eight exit checks with a second device on the same Wi-Fi (`docs/phases/07-platform-capabilities/chat-phase37/`), then command the merge.** Records: [`phases/07-platform-capabilities/chat-phase37/`](../phases/07-platform-capabilities/chat-phase37/README.md), JOURNEY §50.

**Previous head (2026-09-09):** **Phase 36 Terminal speed & feel ✅ DEVICE-PASSED on `arena/01a086a0-codec` by owner report. The original acceptance and follow-up checks for progressive apt streaming, background command survival, and session-switch redraws passed after fixes `da126cf`/`11fe8d7`; green Build APK CI `34374983032`/`34375710614`. Merge authorized by owner; **PR #60 is MERGED to `main`** at
`373a51e8f027bcf08fc948b8dc2058c3bda8c566`.** **33.1** first-run tiles (three starter tiles — C / Python / HTML — DataStore `first_launch_complete` flag, pure `WelcomeStarters` + `WelcomeScreen`, idempotent create-or-open, launch-state saved before the flag flips); **33.2** Packages hub (Languages & IntelliSense open, Unix tools collapsed, search flattens; install/run wiring unchanged); **33.3** identity copy (README/About/empty Projects). Tests: `WelcomeStartersTest` (5), `PackageSectionTest` (5). **CI `34346424311` ✅ GREEN first try (tip `5db9e33`, 5m23s); device round `TROUBLESHOOTING.md` §24 ✅ PASSED.** Records: [`phases/09-onboarding-setup/chat-phase33/`](../phases/09-onboarding-setup/chat-phase33/README.md), JOURNEY §47. **The owner's four new UX/UI row ideas became phases 34–37 (official file icons; editor typing feel; terminal speed & UX; device-as-server at localhost) — researched + specced, `docs/roadmaps/PHASE34_37_ROADMAP.md` + `docs/phases/03-editor/chat-phase34/…37/`; open-source-first dossier `docs/research/PHASE34_37_OSS_RESEARCH.md` (Seti MIT + Compose PathParser · sora public API · jackpal Apache-2.0 / Termux GPL-3.0 ref · NanoHTTPD BSD + ZXing core Apache-2.0 + NSD). They start on the owner's "Start Phase N".** Remaining: 28.3/28.4 Keys remainder.

**Previous head (2026-09-09):** **Phase 32 (Phone canvas) ✅ DEVICE-PASSED + Phase 33 RUN ▶ chooser implemented on `arena/01a083fc-codec`** — **32.1** auto-hides the 5-tab bar in the editor while Keys/IME is up, with a thin tap/swipe handle to reveal it (pure `NavBarPolicy` + `EditorChromeState` bridge); **32.2** needed no code (28.2/28.3 already give chips-as-row-0; 32.1 removes the nav); **32.3** makes a diagnostic tap land in the user's file (pure `OutputDiagnosticTarget`). **CI `34305070875` ✅ GREEN first try (tip `b5feb55`); device round `TROUBLESHOOTING.md` §16 ✅ PASSED** (owner: "All test passed on device"). **Phase 33 first item done (owner, final shape): the default run file is a USER choice** — "make the default run option as a user task if user set any default file than it will open with a option default or current file." RUN ▶ runs the open file by its own type (a `.c`/`.py`/`.js` in a `web` project compiles/runs in the panel, never previews `index.html`); if the user set a **launch default** (⋮ → Set as launch default, now any run target) that differs from the open file, RUN asks "Run default / Run open" (pure `ProjectRunTarget.chooserDefault`). `EditorViewModel.runFile(context, target)` runs the chosen file without switching tabs. **Owner bug fixed (2026-09-09): cloned/imported projects are type `auto`, and `ProjectRunDetector` was letting the root `index.html` shadow an open `.c` file — it now runs any runnable active source (`TROUBLESHOOTING.md` §19).** Tests: `ProjectRunTargetTest` (12), `ProjectRunDetectorTest` (+2). **Owner bug fixed (2026-09-09): a source path with a space (`C Programming/…`) was word-split by the unquoted `$converted` inside the `cc` frontend — `ccScript()` now rebuilds args with `set -- "$@" "$arg"` and passes `"$@"` to TCC (`TROUBLESHOOTING.md` §20).** **CI `34307172630`/`34309463999`/`34317268507`/`34321154191` ✅ GREEN** (tips `788ba46`/`9a11382`/`270c70d`/`fa34750`). **`undefined symbol 'main'` (Code-with-C is a multi-file menu project — the numbered files are `programNN()` fragments; `main.c` has the only `main()`): not a bug; CodeC now appends a hint to the Output Panel for the "no main" linker signature (`CompilerDiagnostics.looksLikeMissingMain`, §21; CI `34323755844` ✅ GREEN first try, tip `6a6d36b`), and the whole menu runs via ⋮ → Edit run config → `cc 'C Programming/'*.c -o bin/menu`.** **Owner follow-up: practice files are self-contained with an entry named anything, not `main` — `CEntryWrapper` now compiles a single C file with no `main` and exactly one function through a generated wrapper and runs it (`TROUBLESHOOTING.md` §22; CI `34330372322` ✅ GREEN first try, tip `36e4fda`).** **Device round (2026-09-09): ✅ PASSED — owner "Ok working as i wanted".** **✅ MERGED to `main` via PR #57 on the owner's command ("Then merge").** Records: [`phases/03-editor/chat-phase32/`](../phases/03-editor/chat-phase32/README.md), [`phases/09-onboarding-setup/chat-phase33/`](../phases/09-onboarding-setup/chat-phase33/README.md), JOURNEY §45/§46. Remaining: 33.1–33.3 first-hour tiles/hub/copy; 28.3/28.4 remain the Keys remainder.

**Previous head (2026-09-08):** **Phase 31 (IntelliSense as Packages) ✅ MERGED to `main` (owner: "Ok update all md files and merge it")** — hand-rolled LSP stdio JSON-RPC (sora `editor-lsp` AAR stays gated: minSdk 26 vs CodeC 24). Chip strip + ghost merge LSP on `EditorViewModel.refreshCompletionItems`. CI `34210108408` GREEN (tip `278099e`, 9m14s). Device recipe (`TROUBLESHOOTING.md` §15) **not run**. Records: [`phases/03-editor/chat-phase31/`](../phases/03-editor/chat-phase31/README.md). Remaining: 32 phone canvas, 33 first-hour UX. 28.3/28.4 remain the Keys remainder.

**Previous head (2026-09-07):** Phase 30 (Offline completeness — snippet packs + Emmet + strip capacity) ✅ COMPLETE, DEVICE-PASSED & MERGED to `main` via PR #55 (owner: "Start phase 30" → "If all done then merge it")** — all three parts shipped in one build: **30.1** the four hand-written snippet tables became **29 vendored MIT [friendly-snippets](https://github.com/rafamadriz/friendly-snippets) packs** (`assets/snippets/`, ≈54 KB deflated, pinned to `6cd7280`, licence + About line, `scripts/vendor_snippets.py`) resolved by five new pure files in `ui/editor/snippets/` = **84 C / 76 Python / 126 HTML / 156 CSS / 367 JS / 140 TS / 62 MD / 16 shell** items, with the built-in tables kept as a deduped **tail** and fallback; **30.2** a **clean-room Emmet engine** (`ui/editor/Emmet.kt`, 859 LOC, no dependency, rule.md §6) at **rank 0** — markup (`! > + ^ *n ( ) .class #id [attr] {text} $`, implicit tags, void + JSX self-close) and CSS (82 abbreviations, units, keyword tables), guards that REFUSE rather than guess; **30.3** `MAX_ITEMS` 8 → **50** (snippets ≤40 / identifiers ≤6 / keywords ≤6) with `MAX_CHIPS` still **8** + ⌄ more, and `CompletionItem.replaceLength`/`caretOffset` honoured by the VM accept and the ghost's FULL accept. **Phase 27 law untouched — `CompletionPolicy.kt` is not in the diff.** **Device round 1 (`TROUBLESHOOTING.md` §13, build `ca8ec57`) found TWO bugs, both fixed in `d63a645` and device-confirmed PASSED on §14 (owner: "Yes working", 2026-09-07):** the accept span was the identifier word-run so `#in` + tap left `##include <stdio.h>` (now `CodeCompletionEngine.replaceSpanLength` = word-run ∪ the insert's aligned line tail, case-insensitive for accept / literal for the ghost, wired at the chip AND the ⌄ panel), and CodeC Keys closed no brackets because SmartTyping's flag was named `isStrip` and both keyboard paths still passed it (renamed `suppressAutoPair`; `{` + Enter now indents and splits the closer; the strip keeps suppression). CI: `34034889209` GREEN first try → the §3.5 amendment's one for-cause round (`34040754444`, a hand-counted caret literal) → `34041185149` GREEN → the device round's `34077539890` RED on the known sora/Robolectric flake (`EditorLaunchMeasureReproTest` → `IllegalThreadStateException` in sora's unsynchronized `AsyncIncrementalAnalyzeManager.rerun`) → `c2b392e` tolerates exactly that signature → **`34078739941` GREEN (tip `c2b392e`, 8m33s), artifact `CodeC-IDE` 24 375 211 B = +117 409 B (+0.11 MiB) vs `main`**. Tests: 91 new + 6 new cases for the phase, +3 from the device round (`CodeCompletionTest` 19, `SmartTypingTest` 13), **124 green locally** over the real production files. Records: [`phases/03-editor/chat-phase30/`](../phases/03-editor/chat-phase30/README.md) (README + §3 of each part), [`JOURNEY.md`](../journal/JOURNEY.md) §41, `TROUBLESHOOTING.md` §13/§14 (both now standing regression cards). Remaining queue: 31 LSP-as-Packages, 32 phone canvas, 33 first-hour UX (all 📋 PLANNED, docs only). 28.3/28.4 remain the Keys remainder.

**Previous head (Phase 30 as implemented, superseded by the merge):** Phase 30 (Offline completeness — snippet packs + Emmet + strip capacity) 🚧 IMPLEMENTED on `arena/01a07646-codec` (owner: "Start phase 30") — `Build APK` GREEN (first build run `34034889209`; **amended build run `34041185149`, tip `ca8ec57`, 4m51s — install THIS one**; APK artifact delta +116 886 B (+0.11 MiB) vs `main`; one for-cause round, `34040754444`, red on a hand-counted caret literal in a new test = a wrong assertion, not a wrong engine); owner device round (`TROUBLESHOOTING.md` §13, 14 measured steps) is the only open gate** — all three parts in one build: **30.1** the four hand-written snippet tables (7 C / 9 Python / 8 HTML / 3 CSS) replaced by **29 vendored MIT [friendly-snippets](https://github.com/rafamadriz/friendly-snippets) packs** as assets (277 KB raw ≈ **54 KB** in the APK, pinned to upstream `6cd7280`, licence notice + About line, re-vendorable with `scripts/vendor_snippets.py`) resolved by five new pure files under `ui/editor/snippets/` (strict JSON reader · VS Code snippet resolver with tabstops/choices/`TM_*` variables/regex transforms + case ops + conditionals · entry→item mapping with first-wins dedupe · the LanguageType→pack map · a `TextMateSupport`-shaped library with two cache layers, background warm-up and degradation) = **84 C / 76 Python / 126 HTML / 156 CSS / 367 JavaScript / 140 TypeScript / 62 Markdown / 16 shell** completion items, with the built-in tables kept as a **deduped tail** after the pack (and as the whole list when no pack loads — they carry the 22.6 DOCTYPE skeleton behind `doc`, the app-private shebang, and the descriptive labels a prefix-only pack cannot reach: `head` → `# Heading`, `pr` → `print(...)`); **30.2** a **clean-room Emmet engine** (`ui/editor/Emmet.kt`, 859 LOC, no dependency, rule.md §6) in the same `CompletionItem` pipeline at **rank 0** — markup (`!`/`html:5`, `>` `+` `^` `*n` `(…)`, `.class` `#id` `[attr]` `{text}`, `$`/`$$` numbering with counter propagation, implicit tags, void + JSX self-close) and CSS (82 abbreviations by longest match, 14 unit suffixes, `-` separators vs negatives, `!important`, `+` chains, keyword tables, caret after `": "`), with guards that REFUSE rather than guess (structural-signal test, open tag/attribute/comment/string/selector contexts, `.jsx`-only JSX, plain `.js` and C never fire) and `finish()` re-indenting continuation lines onto the caret's indent; **30.3** `MAX_ITEMS` 8 → **50** (snippets ≤40, identifiers ≤6, keywords ≤6, tier order unchanged) while `MAX_CHIPS` stays **8** and ⌄ more still shows the rest (27.2), ghost still rank 0, and `CompletionItem` gained nullable `replaceLength`/`caretOffset` honoured by the VM accept and the ghost's FULL accept (WORD/LINE deliberately ignore the tabstop). **Phase 27 law untouched — `CompletionPolicy.kt` is not in the diff** (Enter sacred, master switch zeroes chrome *and* computation, no auto-commit) plus ONE narrow test-pinned S1 exception: a lone Emmet candidate gets its chip because a ghost cannot cover an expansion. **91 new host tests + 6 new cases** (`SnippetSyntaxTest` 21 · `SnippetPacksTest` 12 · `EmmetTest` 24 · `SnippetLibraryTest` 15 Robolectric on the real assets · `CompletionCapacityTest` 19 = the host mirror of all three exit conditions, incl. the plan's named test: prefix `i` in C with packs → **13 candidates vs 7**), 159 green locally; two real bugs caught pre-CI (`TM_DIRECTORY`'s chained `substringBeforeLast` → "" for `proj/main.c`; bare `*` in `ul>*` refused) and **five caught by measuring the device card post-CI** (Markdown `head` → 0 items, Python `pr` → no `print`, shell `if ` → 16 snippets with no if-block, Python `def ` → no `def`, typed `nav>…{Link $}` → nothing — fixed by the deduped tail, the trigger path testing INSERT TEXT instead of the label, and a brace-depth-aware Emmet walk-back; +2 tests). Perf: `completions()` on a 4 000-line buffer = **1.8–4.5 ms** (budget 16.7 ms). **Gate = owner device round** ([`TROUBLESHOOTING.md` §13](../guides/TROUBLESHOOTING.md)); records in [`phases/03-editor/chat-phase30/`](../phases/03-editor/chat-phase30/README.md) (README + §3 of each part) and [`JOURNEY.md`](../journal/JOURNEY.md) §41. **No PR/merge until the owner's command.** Remaining queue: 31 LSP-as-Packages, 32 phone canvas, 33 first-hour UX (all 📋 PLANNED, docs only). 28.3/28.4 remain the Keys remainder.

**Previous head:** Phase 29 (VS Code colour / TextMate) ✅ COMPLETE & MERGED to main via PR #54 (owner: "Merge it")** (owner: "Start phase 29") — all three parts (29.1 `language-textmate` core + themes, 29.2 language parity via the `LanguageType` split, 29.3 regex retirement) built. Device round 1 hit two crashes, both root-caused from the in-app crash record and FIXED: crash 1 = CME in sora theme dispatch (registry-monitor + locked-createLanguage + main-thread switch, `3fb404f`); crash 2 = `IllegalStateException: LayoutNode should be attached to an owner` — the editor Column (imePadding, inside the nav AnimatedContent transition) measuring a detached child — a Compose **1.7.1** bug family, fixed TWO layers deep: `composeBom 2024.09.00 → 2024.12.01` (1.7.6) for the framework detached-node family (`288b760`), then the CI NavHost-transition repro test caught the VM→sora replay's incremental delete-all crashing sora's async-rebuilt layout → atomic `setText` replay (`db56824`, regression-pinned). Along the way: crash-log made header-first/frame-capped (COPY ALL now yields the complete record; dialog title = exception line; versionName carries the CI run number so stale-APK confusion is visible in Settings → About). **Device round PASSED 2026-09-06: checklist ALL PASS (Dark+ look, every language, typing, theme switch, completions/Keys/find, no crash) + APK-size deviation ACCEPTED by the owner (+2.22 MB artifact-to-artifact vs the +1.5 MiB plan budget — engine weight, not grammars).** Records in [`phases/03-editor/chat-phase29/`](../phases/03-editor/chat-phase29/README.md). **No PR/merge until the owner's command.** Remaining queue: 30 snippets/Emmet, 31 LSP Packages, 32 phone canvas, 33 first-hour (all 📋 PLANNED, docs only). 28.3/28.4 remain the Keys remainder.

**Previous head:** Phase 28 (CodeC Keys) — 28.1 spike CLOSED (GO verdict), **28.2 layout engine MERGED 2026-09-05 via PR #52** after owner rounds 1–3 (previews, LIVE-buffer arrow commits, space trackpad, DEFAULT ON, one-key-per-button SYM layer; standing regression card `TROUBLESHOOTING.md` §11). PR #53 landed the Phases 29–33 plan docs.

**Previous head:** Phase 28.1 CodeC Keys IME-free spike BUILT on `arena/01a070ae-codec` (owner: "Start phase 28") — :bench-only harness (K1 = Compose document core, K2 = shipping sora core, both fed ONLY by an IME-free 3-row code grid through the production `EditorKeySet` model); pure metrics host-tested (DOWN→commit latency ledger, strict-subsequence tap audit, ime-inset flicker probe w/ self-check toggle), scenarios for the budget table + spike Q1 (synthesized HW keys) Q2 (stdin-row route) Q3 + 5-min human session; CI history + merge gate below. **DEVICE ROUND 1 RECORDED (2026-09-05): K2/sora meets every budget (commit p95 ≤3.4 ms, audits exact, IME 0 px throughout); K1's frame red = the Compose core itself (25.1 C-now disease), the grid path was clean there too; S2 is the input path of record. OWNER VERDICT: **GO** (owner: "Go", 2026-09-05 — §6; the four human items waive as blockers and ride 28.2's device round). **28.1 CLOSED.** 28.2 (layout engine) **BUILT 2026-09-05 + CI green** (`:app/ui/keyboard/*`, Settings default-OFF, S2 wiring, 20 host tests) — owner rounds 1–2 run + fixed same day (previews, no macro row, arrow flicks; LIVE-commit arrow fix, space trackpad, **DEFAULT ON** per owner; round 3: SYM layer = one key per button, single chars, language-free letters layer) — **retest card `TROUBLESHOOTING.md` §11**) — ✅ **MERGED into main 2026-09-05 on owner command** (rounds 1–3); the standing regression card guards it, and a clean pass opens 28.3. **No PR/merge until the owner's explicit command.**


**Previous head (Phase 27, MERGED via PR #51 2026-09-05, superseded):** Phase 27 Phone-native Autocomplete merged to `main` (tip `92af7fb`, post-merge CI `33955091994` green) — All three parts, client-only: **27.1** inline ghost text on the sora core (pure `GhostCompletion` FULL/WORD/LINE accept + instant shrink; rendered as a point-anchored inlay hint via new `GhostHintRenderer`, comment@38% per G5; TAB ▸ cap / →▸ word cap / caret-row "Tab ▸" pill / tap-ghost / HW Tab & Ctrl+→; scroll+composing clear it — G4/G7); **27.2** suggestion strip (sealed `StripContext` Hidden|Run|Keys|Suggestions with Run always winning — 23.2 re-pinned; ≥2 candidates → ƒ/λ/≠ chips with pinned ⌨ and ⌄ more caps, tap-accept, long-press tooltip, swipe-down dismiss-per-identifier; `CodeCCompletionComponent` gates sora's native panel to "⌄ more" browse sessions only); **27.3** the law in pure `CompletionPolicy` (surfaceFor + decide; Enter sacred, "TAB ▸" tells the truth, dismissal per-identifier, master off = zero chrome/computation) + Settings → Editor Settings rows. New VM two-leg pipeline (`completionModel: StateFlow<CompletionModel>`): instant `startsWith` narrowing per keystroke + 120/240 ms debounced engine recompute off-main. Host tests: `GhostCompletionTest`, `StripContextTest`, `CompletionPolicyTest` (full matrix). JOURNEY §37. **No PR/merge until the owner's explicit command.**


**Previous head (Phase 26, merged via PR #50, superseded):** Phase 26 Typing Experience 2.0 MERGED to `main` (`c1b4321`, post-merge CI `33941444393` green) — key strip 2.0 popups/swipes/hold-repeat + JSON user sets, smart typing (type-over/wrap/empty-pair/auto-indent/delete-word, string-aware), IME first-run guide. Device §3 recipes still pending owner (BT keyboard for E.3-style passes). JOURNEY §36.

**Previous head (Phase 25.2, device-accepted via PR #49, superseded):** Phase 25.2 (sora-editor 0.24.6) ✅ COMPLETE & DEVICE-ACCEPTED on `arena/01a06b20-codec` (tip `c54228d`, run `33866749797` → `33860045301` green; +0.55 MiB APK delta; four device rounds — crash ping-pong → constructor NPE → drawer-gesture & sora-native completion → "All passed"); LGPL-2.1 "Yes" + "Merge" → PR #49 squash-merged; Phase 25 CLOSED; bench wrapper removed. See `docs/journal/JOURNEY.md` §35.

**Previous head (Phase 24, merged via PR #47, superseded):** Phase 24 (desktop-class editor quality) on `arena/01a06784-codec` — eight of nine parts (E.1 formatter, E.2 foreground notification, E.3 hardware shortcuts, E.4 ZIP share, E.6 test-runner, E.7 open-with, E.8 adaptive theme, E.9 `.codec.json`) implemented (see `docs/journal/JOURNEY.md` §33); **E.5 tablet two-pane deferred** pending owner confirmation (requires extracting the `EditorScreen` body from the `ModalNavigationDrawer`). **CI green `33768581748`**. **Device round 1 (2026-09-04): E.1 ✅ / E.2 ✅ / E.4 ✅ / E.6 ✅ / E.7 ✅ / E.8 ✅ / E.9 ✅ on-device; E.3 ⏳ not possible this round (needs Bluetooth keyboard/tablet); E.5 ⏸ deferred.**

**State (head):** **Phase 22 ✅ MERGED to `main` via PR #45 (2026-09-03; `main` tip `7173494`, post-merge CI `33730937920` green). Phase 23 (Interactive Run UX) ✅ COMPLETE & DEVICE-ACCEPTED, and has been merged to `main` via PR #46 (owner's merge command);** **Phase 23 (Interactive Run UX) ✅ COMPLETE & DEVICE-ACCEPTED on `arena/01a06662-codec` (`Build APK` `33735687876`; owner: "Start Phase 23" → "Phone test passed") — both §4 device recipes passed on device.** Two client-only parts: **B.1** inline PTY input (the separate `OutputInputRow` is deleted; an `InlineInputRow` renders as the last Output-Panel `LazyColumn` item whenever `OutputRunState.waitingForInput` is set, and Enter/`↵` sends the line to `InteractiveRunSession.sendLine`) and **B.2** context-aware run keys (while an interactive run waits for input the Phase 22.2 IME strip shows `↵ Enter` / `Ctrl+C` / `Tab` / `↑` / `↓` instead of the editor keys — `Ctrl+C` → `interruptRun()` → `InteractiveRunSession.sendSignal(SIGINT)` → the existing `PtyNative.kill`, so **no JNI/native change**; editor keys restore the moment the run ends). New pure code: `ui/editor/RunKeySet.kt`, `ui/editor/KeysContext.kt`, `ui/services/InteractiveInputBuffer.kt`; `EditorKeysRow` refactored to a precomputed-keys signature + shared `KeyCap`; `OutputPanelView` signature `onSendInput` → `onInputChange`/`onSubmitInput`. Host tests: `RunKeySetTest` ×8, `InteractiveInputBufferTest` ×7. One for-cause red CI round (`33735482625`, missing `kotlinx.coroutines.flow.update` import) fixed before the green run. Device recipes in `docs/phases/05-run-output-preview/chat-phase23/` (§4 of each part) — **both passed on device 2026-09-03 (owner: "Phone test passed")**. **Owner action: merge when ready.** Next after that: Phase 24. Six implementation rounds, five driven by owner device feedback. **A.1 (smoothness):** the long-file lag was ultimately a documented Compose limitation — [`compose-multiplatform#4023`](https://github.com/JetBrains/compose-multiplatform/issues/4023) → `CMP-4023`, closed WONTFIX: `BasicTextField` is **not lazy** and its layout cost scales with the **span count**, not the character count. Syntax spans are now windowed to ±3 000 chars around the caret (a 517-line HTML file: 1 753 spans → ~409), the tokenizer scans from a blank-line safe anchor instead of offset 0 (bounding the main-thread fallback), and the `filter()` memo is keyed on `(text, caret)`. Earlier rounds removed real per-keystroke O(file) work: the tab-buffer stash on every keystroke, a full-prefix copy in the caret readout, and the completion scan (now `produceState` + 120 ms debounce + `Dispatchers.Default`, windowed identifier scan). **A 22.1 claim was corrected in the docs:** the original `derivedStateOf` for completions was a no-op, not a fix. **A.2 (IME keys):** row rides flush above the keyboard; new `EditorKey.Pair` gives single-cap `()`/`{}`/`[]`/`<>`/`""`/`''`; HTML/CSS and Markdown gained full snippet sets (they previously returned **no** completions at all) and snippet matching is now case-insensitive. **A.3 (insets):** `imePadding()` on the editor root; `adjustResize` **kept**; Terminal/Settings untouched. **Deferred, needs owner go-ahead:** the `TextFieldValue` → `TextFieldState` / `bigtext`-style rewrite — the only way past the `BasicTextField` layout ceiling, and its own phase. User-editable key sets remain a stretch goal.
**Previous head (Phase 22, superseded):**
**Previous head (Phase 21):** **Phase 21 ✅ COMPLETE, DEVICE-ACCEPTED & MERGED to `main` by owner command (2026-09-03, from `arena/01a064e0-codec`).** The compiler engine redesign — but **not** the one the spec described: **TCC was NOT removed, it is the DEFAULT C compiler.** `EditorViewModel.runActiveFile` no longer switches on `LanguageType`; a new Android-free `LanguageRegistry` (12 profiles) + `LanguageRunPlanner` (sealed `RunDecision`) + `LanguageToolProbe` make adding a language one list entry. A missing toolchain prompts "Install X?" and streams `pkg update && pkg install -y <pkg>` into the Output Panel, resuming the run on success. **A `.c` file has no install gate at all** (built-in `cc`, offline, instant); `.cpp` installs `clang` because TCC cannot build C++. The Settings → Compiler Engine picker and the `COMPILER_BACKEND` preference are **deleted**. Three owner device rounds found and fixed five gate bugs (D17 there is no `gcc` package — Phase 20.1 publishes `clang`; D18 golang/rust never published; D19 stale apt catalog; D20 server presets and custom `project.json` commands bypassed the gate entirely; D21 the `c-microservice` preset). **⚠️ CodeC has FIVE run paths** — active file, project file, project config, server preset, terminal handoff — and the Phase 21 spec described only the first. `docs/phases/02-packages-toolchains/chat-phase21/PART_21_4_REMOVE_TCC.md` is ❌ CANCELLED; the TCC `-o`-last invariant is PERMANENT. CI: `33704697218`, `33706730106`, `33710216905`, `33712841947`. Record: `docs/phases/02-packages-toolchains/chat-phase21/PART_21_IMPLEMENTATION.md`. **Next: Phases 22/23/24 remain PLANNED and fully spec'd — owner starts one by saying "Start Phase 22" (or 23/24) in chat.** Recommended order: 22 → 23 → 24.
**Previous head (Phase 20.1):** round-4 toolchain roots (`libllvm`+clang/gcc symlinks, nodejs, npm, php trimmed, ruby, lua54) published to the dev channel and live: final salvage run `33669069048` green (~3h04m), device-verified 6/6; bootstrap release `userland-v2-dev` refreshed via `33669089783`. Full record: `docs/phases/02-packages-toolchains/chat-phase20/PART_20_1_TOOLCHAINS.md`.

**✅ MERGED 2026-09-01 via PR #39 (`arena/01a05b6c-codec`):** the two owner-reported git bugs, fixed & CI-green:
- **New branch / never-pushed commit** — publish NEW branches on creation, push by explicit branch name, and a Projects-card amber `↑` badge for branches not on the remote yet. Record: [`phases/03-editor/chat-phase15/PART_17_SOURCE_CONTROL.md`](../phases/03-editor/chat-phase15/PART_17_SOURCE_CONTROL.md) §6.3. CI green `33476150534`.
- **Clear error messages** — new Android-free `GitErrors` classifier turns raw git failures into actionable sentences ("git not installed", "no GitHub token", "offline", "push rejected — PULL first", …) and adds a tappable "Create a GitHub token ↗" link (GitHub's fine-grained PAT page) to the Source Control sheet and Settings → GitHub Account. Record: [`phases/03-editor/chat-phase15/PART_17_SOURCE_CONTROL.md`](../phases/03-editor/chat-phase15/PART_17_SOURCE_CONTROL.md) §6.4. CI green `33479410194`.
**Previous state line:** **Phases 15/16 (Spck Projects Hub + Editor Shell) IMPLEMENTED, mockup-exact re-skin + 3 device rounds, MERGED to `main` (2026-08-31, from `arena/01a057e0-codec` — device round 3: no-Home 5-tab bar with Terminal dead-center + Packages restored, launch-into-last-editor-file, editor autosave, build-output git exclusion incl. untracking, RUN▶=HTML preview).** **Phase 17 IMPLEMENTED and MERGED to `main` (2026-08-31, from `arena/01a05878-codec`)** — Switch Branch (stash/auto-restore + New branch), merge-conflict grouping, Mark Resolved, commit blocking (+38 host tests); **device round 1 done**: upstream publishing on first push + honest "NOT pushed" state with a PUSH retry after the owner's "no upstream branch" / "it stay updated but never go to github" report. **Phase 18 (CodeCApi Device Capabilities) ✅ COMPLETE & DEVICE-ACCEPTED (2026-09-01, `arena/01a05b12-codec` `4460306`, run `33468442063`, owner §4 recipe PASSED — battery/sensor/TTS/camera/intent CLI + bridge + runtime-CAMERA flow; see `docs/phases/07-platform-capabilities/chat-phase18/` §5.6). Phases 3–14 + 19 all complete/merged below; there is no remaining spec'd implementation work — only owner device feedback on Phases 15–17/18.**
**Previous state line:** Phases 3–7 complete; **Phase 8 complete and fully accepted** (PR #27 merged at `348eb03`); **Phase 9 + 9.1/9.2 complete and accepted — PR #28 MERGED to `main` at `961e942`** (2026-08-29). **Phase 11 (Output Panel & Integrated Run) ✅ COMPLETE & DEVICE-ACCEPTED — PR #29 MERGED to `main` at `771f58f`** (2026-08-30 — CI green `33293358085`; owner: "All of the check passed"). **Phase 12 (Multi-Language Support, Python & Code Intelligence) ✅ COMPLETE, DEVICE-ACCEPTED & MERGED — PR #30 merged to `main` at `260d8b6`** (2026-08-30; device recipe FULLY PASSED — owner: "Now python is solved" / "Worked properly" / "Both working"; `[repo-build]` published python 3.14.6-1 + python-pip 26.2.1 — see [`phases/03-editor/chat-phase12/`](../phases/03-editor/chat-phase12)). **Phase 13 (GitHub & Git Integration) ✅ COMPLETE & DEVICE-ACCEPTED & MERGED — PR #31 merged to `main` at `006515a`** (2026-08-31 — `Build APK` `33326161083` green with 37 new host tests; full §7 device recipe passed; see [`phases/06-git-github/chat-phase13/`](../phases/06-git-github/chat-phase13)). **Phase 14 (Mixed-Language, Server WebViews & Long-Tail Ecosystem) 🚧 IMPLEMENTED & CI-GREEN on `arena/01a05421-codec`** (2026-08-31 — `Build APK` `33352164172` green; host tests incl. `ServerPortDetectorTest`/`ServerRunnerTest`/`ProjectScaffoldTest`/`ProjectConfigTest`; no `[repo-build]`; device recipe pending — see [`phases/05-run-output-preview/chat-phase14/`](../phases/05-run-output-preview/chat-phase14)). Device recipes in [`phases/03-editor/chat-phase9/PART_9_IMPLEMENTATION.md`](../phases/03-editor/chat-phase9/PART_9_IMPLEMENTATION.md)
remain the regression checklist. Current Phase 8 records live in [`phases/04-projects-files/chat-phase8/`](../phases/04-projects-files/chat-phase8).

> This file is retained as the historical Phase 3 task list. For current work, use [`TERMINAL_PLAN.md`](../roadmaps/TERMINAL_PLAN.md), [`phases/04-projects-files/chat-phase8/PART_8_DESIGN_DECISIONS.md`](../phases/04-projects-files/chat-phase8/PART_8_DESIGN_DECISIONS.md), and [`phases/03-editor/chat-phase9/PART_9_EDITOR.md`](../phases/03-editor/chat-phase9/PART_9_EDITOR.md).

**Historical state:** Parts A, B, C, and D ✅ ALL
device-verified (Phase 3 complete). **Phase 4 (Parts 4.1–4.8) ✅ COMPLETE,
device-verified 2026-08-26.** Historical Phase 5 text follows; Phases 8–13 are
closed (**PR #27/#28/#29/#30/#31 merged**) — see [`phases/06-git-github/chat-phase13/`](../phases/06-git-github/chat-phase13).
Phase 14 (server WebViews) is implemented & CI-green on the session branch — see
[`phases/05-run-output-preview/chat-phase14/`](../phases/05-run-output-preview/chat-phase14).

> **🔒 STANDING RULE (owner, 2026-08-26): do NOT open a PR or merge anything
> without an explicit command from the owner in chat.** Committing to and
> pushing the session branch (`arena/*`) is fine; PR creation and any merge
> wait for the owner's explicit instruction.

The narrative is in [`docs/journal/JOURNEY.md`](../journal/JOURNEY.md). This file is the
Phase 3 **task list**, kept for its history: Parts A–D, all now done, split
into self-contained parts so each could be picked up independently. Phase 4's
task list lives in [`phases/02-packages-toolchains/chat-phase4/PHASE4_ROADMAP.md`](../phases/02-packages-toolchains/chat-phase4/PHASE4_ROADMAP.md)
instead of here — it is **complete** (Parts 4.1–4.8, all DONE). The next
phase's planning-only skeleton is [`PHASE5_ROADMAP.md`](../roadmaps/PHASE5_ROADMAP.md).

Each part states its goal, its exit condition, and the exact steps. "Done"
means the exit condition is verified, not just the code written.

---

## How to use this file (verification-first — read before acting)

1. **Start from `prompt.md`** (repo root). Paste it as the first message of a
   new chat. It encodes the self-distrust protocol and the order of work below.
2. **Verify state before acting.** Nothing in this file is true until confirmed
   against the repo: `git status`, `git ls-remote origin`, `gh pr list`,
   `gh run list`. CI state and published releases drift — check them.
3. **Evidence before hypothesis.** If something fails, capture the actual
   output (device Term, CI log, file contents) before diagnosing. Do not commit
   a fix on a guess.
4. **Do not redo completed work.** Anything marked COMPLETE / ✅ in
   [`JOURNEY.md`](../journal/JOURNEY.md) and the handoffs is done. Only revisit it if the
   same symptom reappears AND you have evidence it is a regression.

## Guardrails — do not (violating any of these is a bug, not a shortcut)

- Do **not** add `.` to `PATH`.
- Do **not** use `build-package.sh -I` (installs official `com.termux` debs).
- Do **not** use official Termux repositories or `com.termux` binaries.
- Do **not** overwrite `cc` or replace real ELF `bash` with a shim.
- Do **not** change the TCC link order or move `-o` from last place.
- Do **not** bundle the bootstrap in the APK.
- Do **not** start the ~100-minute package build without an explicit user
  confirmation — and check `gh run list` for an existing run first.
- Do **not** open a second PR in parallel; work from the current branch state.

## Definition of done (applies to every part)

A part is **done** only when:

1. its **Exit condition** is met on the right target (device/CI/repo), and
2. no **invariant** above was violated, and
3. the change is committed and pushed with a message naming the part.

---

## Part A — Republish a clean bootstrap — ✅ DONE (device-verified)

**Status: COMPLETE (2026-08-22) — and it shipped _without_ the ~104-minute
rebuild.** The published `userland-v2-dev` assets were repaired **in place**
by the owner in Termux (Path 2 of
[`phases/02-packages-toolchains/chat-phase3/PART_A_ARTIFACT_REPAIR.md`](../phases/02-packages-toolchains/chat-phase3/PART_A_ARTIFACT_REPAIR.md), which patches exactly
one line of the seeded `var/lib/dpkg/status`), then triple-verified: the
script's internal proofs, the GitHub asset-digest API (aarch64
`074806ad9066d4642d4779a28abf7aeb442c76ae9cb115b12b796eac9a9643b1`, x86_64
`9f93edd06129b04cb4652dae7353b41998aee6d0e04e3d9f59bf3a4e426835ce`), and a
clean-device test — full uninstall → fresh APK → Install userland →
`grep clang` on the status DB finds nothing, full nano install cycle clean.
The exit condition below is **met**; the steps below are kept for history
only — do not redo this part.

**Why.** The published `userland-v2-dev` bootstrap predates the `dpkg-perl`
clang fix, so a *fresh* device still hits `E: dpkg-perl : Depends: clang but it
is not installable` until `pkg` self-heals it (which only happens after the
user runs a `pkg` command that calls `require_backend`). The self-heal makes
this non-blocking, but a clean bootstrap is the correct permanent state.

**Exit condition.** A fresh install of the APK → "Install userland" →
`pkg install nano` works with **zero manual fixes**, because the bootstrap's
status DB no longer references `clang`.

**Steps.**
1. Confirm the current branch is merged to `main` (the recipe fix lives in
   `apply-recipe-overrides.sh`, already committed here).
2. From Termux: `gh workflow run "CodeC package repository" --ref main`.
3. `gh run watch <RUN_ID>` (~1h14m).
4. `gh workflow run "Publish CodeC bootstrap release" --ref main
   -f source_run_id=<RUN_ID> -f release_tag=userland-v2-dev`.
5. On a clean device: install APK → Install userland → `pkg install nano`.

---

## Part B — Fix bootstrap correctness (seed the right thing) — ✅ DONE

**Status: COMPLETE and device-verified (2026-08-23).** PR #13 merged at
`35c350f338be34303296b0168933622991258142`; package dispatch #5
(`32620704350`) and publish run `32625580655` succeeded. The published
aarch64 archive is **23,926,127 bytes** with GitHub asset digest
`sha256:863f18528afa126d19481f7308a3f9b23997fda9ad9cae3bc7033d8fa60e60cd`.
A fresh-device run passed curl/TLS, no-clang/no-build-pollution/no-keyring,
alternatives, `dpkg --audit`, nano 9.2 lifecycle, and embedded-compiler checks.
The exit condition below is met. **Do not rerun the expensive build or re-test
Part B unless Part C first records a genuine new defect, and never dispatch a
build without explicit approval.** The remaining material in this section is
historical context.

**Earlier status (2026-08-22, end of day): code COMPLETE, merged to `main`
(PR #11), 49/49 host tests green.** `plan-bootstrap.py` (closure walk mirroring pinned
upstream `pull_package` semantics, fail-loud on unresolved deps) + reworked
`assemble-bootstrap.sh` (closure-only extract/seed, upstream-format
`md5sums`, assembly-time alternatives wiring incl. the dpkg admin DB,
format measured against live dpkg) + 24 new host tests.
Seed set = `busybox bash apt dpkg coreutils less` at merge time (2026-08-23:
`curl` joined the seed set and `libcurl` the build roots — see the next
status block; see `CODEC_BOOTSTRAP_SEED_PACKAGES`).

**Historical status before PR #13 (2026-08-23):** the first rebuilt bootstrap
exposed two fresh-device defects. Both were fixed on the branch that followed
PR #12 and at that time still awaited rebuild, republish, and device verification.

- **Defect 1 — no HTTPS metadata fetcher.** The Part B closure seeds none of
  `curl`/`python3`/`wget` (the in-code claim that python3 was in the closure
  was wrong), so on a fresh device `pkg update` died at
  `pkg: offline or unable to download CodeC Release metadata (HTTPS required)`.
  Also, `pkg`'s maintainer-script byte checks (`spec_in_file`) called
  `python3`, which would have broken `pkg install` preflight the same way.
  **Fix:** `libcurl` joins the build roots (the `curl` CLI is its subpackage
  at the pinned revision, auto-generating `Depends: libcurl (= …)` so the
  closure walker resolves it), `curl` joins the seed set, and
  `spec_in_file` is now pure shell (`$(cat)` + `case`). `ca-certificates`
  (`etc/tls/cert.pem`, curl's CA bundle) was already in the closure via
  `apt → libgnutls → ca-certificates`. Python stays out (much larger
  closure).
- **Defect 2 — official Termux keyring seeded.** Fresh-device
  `dpkg-query` showed `ii termux-keyring 3.13`. The pinned apt recipe lists
  `termux-keyring` (GPG keys of the *official* Termux repositories, installed
  into `etc/apt/trusted.gpg.d/`) as a runtime dependency. **Fix:**
  `apply-recipe-overrides.sh` now removes exactly `, termux-keyring` from
  apt's `TERMUX_PKG_DEPENDS` (fail-loud on pinned-recipe drift).
  `termux-licenses` deliberately stays: it ships `$PREFIX/share/LICENSES/*`,
  the target of packaged license symlinks (e.g. nano's
  `share/licenses/nano`).
- **Release gate hardened:** `validate-bootstrap.py` now requires `bin/curl`
  (ELF) in the archive and rejects any `termux-keyring` stanza in the seeded
  dpkg status. Host suite: 53/53 green (fixtures prove the fetcher is
  seeded and termux-keyring is excluded without any 100-minute build).

**Historical rebuild record.** Dispatches 1–4 led to the final fixes; dispatch
#5 and the subsequent publish/device verification completed the part. The
first three runs below predate PR #12; #4 is the successful rebuild whose
bootstrap exposed the two defects above:

| # | Run | Duration | Result |
|---|---|---|---|
| 1 | `32581293757` | ~2 min | **Our bug — fixed:** the guardrail scanner matched the repair script's own invariant *comment*; wording fixed + `tests/test_ci_guardrails.py` tripwire added. |
| 2 | `32582311088` | ~50 min | **Upstream network flake — log-proven:** `curl: (28)` fetching `util-macros` from `xorg.freedesktop.org`; fixed by the PR #12 mirror override. |
| 3 | `32585409356` | ~48 min | Same util-macros step; cause unreadable from the agent sandbox; same mirror override applied. |
| 4 | `32594910882` | 1h14m (aarch64) / 1h26m (x86_64) | ✅ **Success** → published by `32617929254` → fresh-device download/verify/extract OK → defects 1+2 found → this fix. |
| 5 | `32620704350` | ~1h20m | ✅ **Success** from PR #13 merge → published by `32625580655` → full Part B fresh-device acceptance passed. |

### Completed procedure (historical — do not rerun)

The following was the final procedure and is retained only as an audit trail.
It is **not** a current instruction.

1. **Redispatch from `main`** (the new `libcurl` root builds OpenSSL +
   libnghttp2/3 + libtcp2-family + libssh2 first):
   ```sh
   gh workflow run "CodeC package repository" --ref main
   gh run watch
   ```
   If it fails on a source download, follow the established pattern: add a
   narrow mirror override in `apply-recipe-overrides.sh` (attr/libacl and
   util-macros precedents), with a host test, then redispatch.
2. **On success, republish the bootstrap** (this re-runs
   `validate-bootstrap.py` — which now enforces `bin/curl` and the absence
   of `termux-keyring` — and swaps the release assets):
   ```sh
   gh workflow run "Publish CodeC bootstrap release" --ref main \
     -f source_run_id=<RUN_ID> -f release_tag=userland-v2-dev
   ```
3. **Device verification (Part B exit condition, ~10 min).** Full app
   uninstall → install the latest successful `Build APK` artifact → "Install
   userland", then in the CodeC terminal, one block at a time:
   ```sh
   grep -n clang $PREFIX/var/lib/dpkg/status; echo exit=$?   # expect: no output, exit=1
   command -v curl             # expect: $PREFIX/bin/curl (HTTPS metadata fetcher)
   curl -fsSI --max-time 30 https://pabi277.github.io/CodeC/dev/dists/stable/Release >/dev/null && echo tls-ok
   pager -V                    # expect: GNU less version banner
   which pager editor vi       # expect: all resolve under $PREFIX/bin
   dpkg --audit                # expect: empty output
   dpkg -l | grep -E 'doxygen|swig|tcl|tor|fontconfig|termux-keyring'; echo exit=$?   # expect: exit=1
   pkg update                  # NOTE: 'W: No Hash entry in Release file' is EXPECTED until Part D — not a bug
   pkg install nano && nano --version    # expect: GNU nano, version 9.2
   which editor                # expect: $PREFIX/bin/editor
   pkg uninstall nano
   printf '#include <stdio.h>\nint main(){printf("ok\\n");return 0;}\n' > t.c
   cc t.c -o a.out && ./a.out  # expect: ok
   ```
   Every line matched on the fresh aarch64 device; Part B's exit condition was
   met and recorded here and in [`JOURNEY.md`](../journal/JOURNEY.md).

**Original rationale (resolved).** The earlier bootstrap had three content
defects, all visible on device:

1. **Build-dependency pollution.** `assemble-bootstrap.sh` seeds *every* built
   `.deb` — including build-only packages (`doxygen`, `swig`, `tcl`,
   `fontconfig`, `tor`, …) — into `var/lib/dpkg/status`. That is how `clang`
   (a build tool) leaked in as a runtime dependency in the first place. The
   bootstrap should record **only** the transitive `Depends` closure of the
   four roots (`busybox bash apt dpkg`).
2. **Seeded packages never run their postinst.** Their alternatives are never
   registered → `pager: command not found`, `editor` only appears after a real
   `pkg install`. The seeded `coreutils`/`less`/`nano`-style roots should have
   their alternatives wired at assembly time (or via a post-install script).
3. **No `md5sums`.** Every seeded package fails `dpkg --audit` with "missing
   the md5sums control file".

**Exit condition.** On a fresh bootstrap: `pager -V` reports `less`,
`dpkg --audit` is clean for seeded packages, and `dpkg -l` lists only the
runtime closure (no `doxygen`/`swig`/`tcl`/`tor`/…).

**Completed implementation steps.**
1. In `assemble-bootstrap.sh`, the "seed every built `.deb`" loop was replaced
   with a closure walk from the roots: read each root's `Depends`, resolve
   against the built set, and seed only those.
2. Upstream-format `md5sums` control files were generated for seeded packages.
3. Seeded-package alternatives were emitted at assembly time, including the
   dpkg admin database.
4. The bootstrap was rebuilt, republished, and device-verified.

---

## Part C — Clean-device acceptance (the M2 gate)

**Status: COMPLETE and device-verified (2026-08-23).** A clean Samsung
SM-A356E (Android 16, aarch64) passed bootstrap/runtime smoke, package
operations, alternatives, negative checks, compiler checks, airplane-mode
restart, and interrupted-install recovery. That recovery test exposed a stale
`codec-pkg/lock`; PR #14 commit `8e95a16` fixed dead-PID lock recovery and the
repeated force-stop test passed without manual state deletion.

The final second-device test exposed a wrong legacy-marker assumption:
v1.3.14 actually writes `.userland-vuserland-v1`. PR #14 commit `a4e5af6`
corrected it, CI passed, and an in-place v1 → `userland-v2-dev` update then
passed the full package/compiler/contamination block. The exit condition below
is met.

**Why.** [`docs/phases/02-packages-toolchains/chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`](../phases/02-packages-toolchains/chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md) is
the explicit clean-device acceptance gate. It now records every item as passed.

**Exit condition.** Every unchecked item in `phases/02-packages-toolchains/chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`
passes on a clean arm64 device (and x86_64 if available), including the
negative checks and the recovery tests.

**Steps (in order, on a clean device).**
1. [x] Uninstall CodeC fully; install a fresh APK (≥ 1.3.15) + Install userland.
2. [x] Runtime smoke (section 2 of the checklist): `$PREFIX`, `which bash`,
   `$BASH_VERSION`, `busybox`, `which apt-get dpkg`, `dpkg --print-architecture`,
   `dpkg -l`.
3. [x] Package ops (section 3): `pkg update / search nano / install nano /
   nano --version / uninstall nano / upgrade`, then the `coreutils`/`less`/`nano`
   alternatives closure (`which pager editor`, `pager -V`).
4. [x] Negative checks: `sources.list` is CodeC-only; `pkg uninstall bash`
   refused; no `com.termux` in `dpkg -l`.
5. [x] Compiler smoke before **and** after package ops (section 4).
6. [x] Airplane-mode restart (section 5).
7. [x] Interrupted-install recovery (section 6): force-stop mid-download,
   automatically reclaim the stale lock, retry, and confirm `pkg repair` clean.
8. [x] v1 → Phase 3 upgrade path (section 7) on a second arm64 device.

---

## Part D — M3: sign the repository and verify on device — ✅ DONE (device-verified)

**Status: COMPLETE (2026-08-24) — implementation, signed publication, client
acceptance, bootstrap build/release, and the final clean-device gate have all
passed.** PR #14 contains key-agnostic signing/validation, protected-subkey CI
support, the public-only production keyring, signed-only client/bootstrap
integration, and tamper/missing-signature tests. Corrective signed publication
run `32642631785` reused existing artifacts, skipped both expensive builds, and
fixed the APT Release-stanza defect found by the first device attempt. The
real CodeC device then passed signed update, exact-key verification, tamper
rejection, nano lifecycle, clean audit, and compiler checks. Build run
`32643383952` then built both architectures successfully; each archive passed
the validator's exact v3-keyring byte comparison and was uploaded as a
non-expired artifact. Release run `32648783080` revalidated both archives and
replaced the four `userland-v2-dev` assets.

**Final clean-device gate — passed 2026-08-24.** After a verified pre-uninstall
backup (checksum, `gzip -t`, listing, independent `cmp`), a full uninstall and
fresh reinstall against the exact published `userland-v2-dev` archive
(aarch64, 23,928,215 bytes) reached `userland: ready` automatically and passed
every remaining section-8 check on a real device: no `clang`/build-pollution/
`termux-keyring` (verified with an exact package-name match after an
unanchored `grep` gave a `sed`-description false positive), CodeC-only
`sources.list`, warning-free signed `pkg update` with an exact keyring-hash
match, independent `gpgv` acceptance of the live signature and rejection of a
tampered copy, a full nano install/uninstall cycle with working alternatives,
a silent `dpkg --audit`, and a working embedded compiler. Full commands and
output are recorded in
[`phases/02-packages-toolchains/chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`](../phases/02-packages-toolchains/chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md) §8. **Do not
re-run this expensive/destructive test unless a genuine new defect is found;
this Part is done.**

**Why.** The development channel originally relied on HTTPS + SHA-256 only.
Signing closes the integrity-vs-tampering gap. The Part D client removes `[trusted=yes]`, verifies
`InRelease` with `gpgv` before apt, and lets apt independently verify through an
exact `signed-by=` keyring.

**Exit condition.** A device with only the CodeC versioned keyring installed
accepts the repository because its `InRelease` signature and Release/index/
package hash chain verify, rejects tampered metadata, and uses no
`trusted=yes`. The rebuilt bootstrap contains byte-for-byte the same keyring.

**Completed inexpensive implementation.**
1. `sign-repository.sh` emits `InRelease` and `Release.gpg` with an exact
   dedicated signing subkey; the protected passphrase travels through stdin,
   never argv.
2. `validate-repository.py` requires both signature forms, exact cleartext,
   exact fingerprint, and the existing index/package hash chains. Real-GPG
   tests cover valid, missing, tampered, and changed-index cases.
3. Public keyring v1 and fingerprints are committed under
   `codec-packages/keys/`; no private key material is committed. The offline
   primary and CI signing subkey fingerprints are recorded in
   [`phases/02-packages-toolchains/chat-phase3/REPOSITORY_SIGNING.md`](../phases/02-packages-toolchains/chat-phase3/REPOSITORY_SIGNING.md).
4. The APK installs that keyring under `etc/apt/keyrings`; `pkg` requires
   `gpgv`, verifies signed Origin/Suite, and writes a CodeC-only `signed-by=`
   source. The Phase 3 bootstrap assembler seeds the same bytes and its
   validator rejects missing/different keyrings.
5. Release hash paths are now relative to `dists/stable/Release`, eliminating
   the historical APT `No Hash entry in Release file` warning.
6. The active publication workflow imports the CI-only subkey, fails closed on
   secret/fingerprint drift, signs before signed validation, and deploys only
   the public key files. Rotation, revocation, rollback, and overlap rules are
   documented.

**Exit condition met.** The final clean-device
keyring/signed-APT/package/audit/compiler test against the rebuilt
`userland-v2-dev` assets passed (see above and
[`phases/02-packages-toolchains/chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`](../phases/02-packages-toolchains/chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md) §8). PR #14 is
ready to merge as "Phase 3 complete" pending final owner review.

---

## Phase 4 — polish and expansion

Phase 4 planning and tracking lives in [`phases/02-packages-toolchains/chat-phase4/PHASE4_ROADMAP.md`](../phases/02-packages-toolchains/chat-phase4/PHASE4_ROADMAP.md) and [`docs/phases/02-packages-toolchains/chat-phase4/`](../phases/02-packages-toolchains/chat-phase4/README.md); **Phase 4 is complete (Parts 4.1–4.8)**.

- **Part 4.1 — Shared-storage access (`~/storage`)** ✅ **DONE (device-verified 2026-08-24).**
  Detailed record in [`docs/phases/02-packages-toolchains/chat-phase4/PART_4_1_STORAGE.md`](../phases/02-packages-toolchains/chat-phase4/PART_4_1_STORAGE.md).
- **Part 4.2 — Package-install confirmation UX** ✅ **DONE (verified 2026-08-24).**
  Detailed record in [`docs/phases/02-packages-toolchains/chat-phase4/PART_4_2_INSTALL_CONFIRMATION.md`](../phases/02-packages-toolchains/chat-phase4/PART_4_2_INSTALL_CONFIRMATION.md).
- **Part 4.3 — Trust/channel indicator UX** ✅ **DONE (verified 2026-08-24).**
  Detailed record in [`docs/phases/02-packages-toolchains/chat-phase4/PART_4_3_TRUST_CHANNEL_UX.md`](../phases/02-packages-toolchains/chat-phase4/PART_4_3_TRUST_CHANNEL_UX.md).
- **Part 4.4 — Terminal/editor settings parity** ✅ **DONE (device-verified 2026-08-24).**
  Detailed record in [`docs/phases/02-packages-toolchains/chat-phase4/PART_4_4_SETTINGS_PARITY.md`](../phases/02-packages-toolchains/chat-phase4/PART_4_4_SETTINGS_PARITY.md).
- **Part 4.5 — Expanded package catalog (round 2, CI build)** ✅ **DONE (CI verified 2026-08-25).**
  Detailed record in [`docs/phases/02-packages-toolchains/chat-phase4/PART_4_5_CATALOG_EXPANSION.md`](../phases/02-packages-toolchains/chat-phase4/PART_4_5_CATALOG_EXPANSION.md).
  Workflow run [`32845127723`](https://github.com/pabi277/CodeC/actions/runs/32845127723) (1h 53m 36s)
  built both architectures (`aarch64`, `x86_64`) green with 25 curated package roots, zero maintainer script violations, and byte-identical bootstrap archives.
- **Part 4.6 — Expanded package catalog (round 2, publish & device gate)** ✅ **DONE (device-verified 2026-08-25).**
  Detailed record in [`docs/phases/02-packages-toolchains/chat-phase4/PART_4_6_CATALOG_ACCEPTANCE.md`](../phases/02-packages-toolchains/chat-phase4/PART_4_6_CATALOG_ACCEPTANCE.md).
  Published via run [`32858460740`](https://github.com/pabi277/CodeC/actions/runs/32858460740) and verified `pkg install` + execution of all 15 new roots on real arm64 hardware.
- **Part 4.7 — Android integration slice** ✅ **DONE (device-verified 2026-08-26)** — clipboard
  over the reusable `CodeCApi` bridge; CI + all primary device checks green incl. the piped/
  redirected channel fix (`/dev/tty`); optional negatives waived; dispatch moved to activity
  scope. 4.8+ ready. See
  [`phases/02-packages-toolchains/chat-phase4/PART_4_7_ANDROID_INTEGRATION.md`](../phases/02-packages-toolchains/chat-phase4/PART_4_7_ANDROID_INTEGRATION.md).
- **Part 4.8 — Android notifications slice** ✅ **DONE (device-verified 2026-08-26)** — `codec-notify
  send|clear|status` over the 4.7 bridge, deliberately exercising the `POST_NOTIFICATIONS`
  runtime-permission path (channel creation, `NEED_PERMISSION` marker, activity launcher,
  atomic resume). Host `sh` harness green; CI green; device-verified end to end incl. the
  permission dialog → allow → `OK` flow, no re-prompt on later sends, and owner-confirmed
  notification tap → CodeC opens. Two device-driven fixes during acceptance (hint once + 30 s
  wait; onResume recovery for the system-owned dialog). **Phase 4 complete.** See
  [`phases/02-packages-toolchains/chat-phase4/PART_4_8_ANDROID_NOTIFICATIONS.md`](../phases/02-packages-toolchains/chat-phase4/PART_4_8_ANDROID_NOTIFICATIONS.md).
- **Post-4.5/4.6 review** ✅ **DONE (device-verified 2026-08-26).** Recipe-override
  hardening, fully artifact-neutral (no rebuild/re-publish needed) — plus the
  `pkg heal` alternatives-DB self-repair, the `plan-bootstrap.py` slave-placeholder
  fix (future bootstrap archives), and the pinned shared CI debug key
  (`debug.keystore`) that stops wipe-on-update between CI builds (proven:
  pinned-cert APK updated in place, 82 packages and the userland intact).
  Record in
  [`docs/phases/02-packages-toolchains/chat-phase4/PART_4_5_4_6_POST_IMPLEMENTATION_REVIEW.md`](../phases/02-packages-toolchains/chat-phase4/PART_4_5_4_6_POST_IMPLEMENTATION_REVIEW.md) —
  including two non-blocking known issues for a future client PR:
  **KI-1** `pkg install` reports failure when the target is already the
  newest version (treat apt's "0 newly installed" as success), and
  **KI-2** device `$PREFIX` (`/data/user/0/...`) vs the dpkg-recorded
  `/data/data/...` spelling confuse manual `update-alternatives` calls
  (canonicalize `PREFIX` at shell setup after a full regression pass).

---

## Ordering summary

| Part | Depends on | Effort / state (2026-08-25) |
|---|---|---|
| Phase 3 Part A — republish clean bootstrap | — | ✅ **DONE** (in-place repair, no rebuild, device-verified) |
| Phase 3 Part B — bootstrap correctness | A | ✅ **DONE** — merged, rebuilt, republished, device-verified |
| Phase 3 Part C — clean-device acceptance | A ✅, B ✅ | ✅ **DONE** — every checklist item passed on real arm64 devices |
| Phase 3 Part D — M3 signing | A ✅, B ✅, C ✅ | ✅ **DONE** — implementation, signed publish, rebuild, and final clean-device gate all passed |
| Phase 4 Part 4.1 — shared-storage access | none | ✅ **DONE** — `codec-setup-storage`, OSC 1337, device-verified |
| Phase 4 Part 4.2 — install confirmation UX | none | ✅ **DONE** — transaction summary, `[Y/n]` prompt, `-y` flag |
| Phase 4 Part 4.3 — trust/channel indicator UX | none | ✅ **DONE** — Settings trust card, `pkg status`, repo probe |
| Phase 4 Part 4.4 — settings/theme parity | none | ✅ **DONE** — font family, themes, settings UI, live preview |
| Phase 4 Part 4.5 — expanded package build (CI) | none | ✅ **DONE** — run `32845127723` green (25 roots, aarch64 + x86_64) |
| Phase 4 Part 4.6 — expanded package publish + device accept | 4.5 | ✅ **DONE** — published run `32858460740` & device-verified |
| Phase 4 Part 4.7 | 4.6 on 4.5 | ✅ **DONE** (device-verified 2026-08-26) — `codec-clipboard` over `CodeCApi` OSC bridge |
| Phase 4 Part 4.8 | 4.7 | ✅ **DONE** (device-verified 2026-08-26) — `codec-notify` over the same bridge, `POST_NOTIFICATIONS` runtime path device-verified (dialog → allow → OK; tap opens CodeC) |

---

## Phase 5 — client fixes, web preview, and capability batch

✅ **COMPLETE (2026-08-26, merged in PR #23).**
- Part 5.1: KI-1 / KI-2 client fixes.
- Part 5.2: In-app web preview via WebView / local server.
- Part 5.3: Capability batch (`codec-toast`, `codec-share`, `codec-open-url`, `codec-vibrate`).
See `docs/phases/07-platform-capabilities/chat-phase5/` and `docs/roadmaps/PHASE5_ROADMAP.md`.

---

## Phase 6 — Terminal UX

✅ **IMPLEMENTED (2026-08-28 on `arena/01a0482c-codec`).**
- Part 6.1: Safe-area / cutout padding (`safeDrawingPadding()`, `shortEdges`), configurable multi-row extra-keys grid + macros in Settings, wake lock with safety, URL tap-to-open, VT BEL visual flash + vibrate, dynamic title, selection-aware copy, character cell alignment, and smooth 60fps pinch-to-zoom.
See `docs/phases/01-terminal-userland/chat-phase6/PART_6_TERMINAL_UX.md`.

---

## Phase 7 — Multi-terminal sessions

✅ **COMPLETE (device-verified 2026-08-28 on `arena/01a048df-codec`).**
- `TerminalSessionManager` (N concurrent PTY sessions, adjacent-selection close,
  auto-recreate, 8-cap, `anyAlive` wake lock) + `TerminalViewModel` delegation with
  one CodeCApi collector per session; active-session routing preserves the public
  API (`ModulesScreen` and command routes untouched).
- Session switcher dropdown + rename/close dialogs in `TerminalScreen`;
  `TerminalEmulatorView.resizeKey` fixes latent cursor drift on switch.
- Design decisions D1–D12: `docs/phases/01-terminal-userland/chat-phase7/PART_7_DESIGN_DECISIONS.md`.
  Status + owner follow-ups (CI unit-test step one-liner — the agent token cannot
  modify workflow files — and the §4 device recipe):
  `docs/phases/01-terminal-userland/chat-phase7/README.md`.
- Evidence: CI compile-green run `33185424586`; 10 unit tests written
  (`TerminalSessionManagerTest`) but not yet executed (no JDK in the agent
  sandbox; CI runs assemble only). **Device acceptance green (§6): full §4
  recipe + regression batch on the owner's aarch64 device. Exit condition MET —
  closed.**

---

## Phases 15–17 — Spck-style Editor & Project Experience (IMPLEMENTED; 15/16 merged, 17 device-gated)

📋 **Phase 15 device round 1 closed on `83ba499` (CI green `33385105931`)** (record: [`phases/03-editor/chat-phase15/PART_15_PROJECTS_HUB.md`](../phases/03-editor/chat-phase15/PART_15_PROJECTS_HUB.md)
§6). **Phase 16 IMPLEMENTED & CI-GREEN (2026-08-31, same branch; device round 1:
python `__pycache__` git-noise fixed via `PythonCacheIgnore`, CI green
`33392053258` @ `435c5f4`)** (record: [`phases/03-editor/chat-phase15/PART_16_EDITOR_SHELL.md`](../phases/03-editor/chat-phase15/PART_16_EDITOR_SHELL.md)
§6). **Device round 2 (2026-08-31, owner: "something is off about the ui — I
want exactly same ui") — mockup-exact re-skin of the Phase 15/16 UI:** flat
5-tab bar (Home·Projects·Editor·Terminal·Settings, the mockup's exact set),
pill filter chips, 16dp cards with 56dp type squares + the Python logo mark,
mockup-color `+` sheet, clone dialog rebuilt (labels above fields, QR brand/icon,
branch dropdown, Settings link), editor top bar reduced to `☰ tabs  ⋮ ▶ RUN`
(second toolbar row → overflow), 3dp tab underline on the bar's bottom edge,
keycap keys row, dot-separated status bar, gutter divider, drawer re-skin
(branch chip, 4-column toolbar, typed file icons, purple selected row,
Source Control + Switch Branch footer), and the Source Control sheet rebuilt
to `mockups/source-control.png` incl. per-file stage toggle
(`GitManager.stageFile`/`unstageFile`, +2 host tests). Hand-drawn glyphs in
`ui/components/SpckIcons.kt` (clean-room — public mockups only, no copied
assets). **Phase 17 remainder implemented 2026-08-31 on
`arena/01a05878-codec`** (Switch Branch dialog with stash/auto-restore + New
branch, Conflicts group + Mark Resolved, commit blocking, purple `U` in the
tree, Push Changes in the card menu — record:
[`phases/03-editor/chat-phase15/PART_17_SOURCE_CONTROL.md`](../phases/03-editor/chat-phase15/PART_17_SOURCE_CONTROL.md)
§6.1; **device round 1 done — the two push bugs the owner found are fixed**, see
[`PART_17_SOURCE_CONTROL.md`](../phases/03-editor/chat-phase15/PART_17_SOURCE_CONTROL.md) §6.2;
the only step not yet exercised on device is the optional conflict recipe
(§4 step 8) — it needs a reproducible conflict). Started on the owner's instructions
("i going to start phase 15" → "Start phase 15"). Originally
added 2026-08-31 on the owner's request to clone the
[Spck Editor / Git Client](https://play.google.com/store/apps/details?id=io.spck)
project & editor experience (import git project, project list, full editor look &
feel). Client-only; reuses the Phase 8/9/11/13/14 engines — a UI/UX parity + gap
fill, not a rewrite. Spec + phone mockups in [`phases/03-editor/chat-phase15/`](../phases/03-editor/chat-phase15).

- **Phase 15 — Projects Hub & Unified Import.** Redesigned Projects screen (card
  list, filter chips, search) + one `+` sheet: **New Project / Clone Git Repo /
  Import ZIP / Open Folder**, per-project git overflow actions.
  [`PART_15_PROJECTS_HUB.md`](../phases/03-editor/chat-phase15/PART_15_PROJECTS_HUB.md).
- **Phase 16 — Spck-style Editor Shell.** Nav drawer file tree with git status,
  refined tabs, snippet/extra-keys keyboard row, readability controls,
  launch-default HTML preview, errors badge.
  [`PART_16_EDITOR_SHELL.md`](../phases/03-editor/chat-phase15/PART_16_EDITOR_SHELL.md).
- **Phase 17 — In-editor Source Control & Branching.** Source Control sheet,
  in-tree M/A/D/? status letters, tap-to-diff, Switch Branch (+stash), Pull/Push
  menu items, merge-conflict marking.
  [`PART_17_SOURCE_CONTROL.md`](../phases/03-editor/chat-phase15/PART_17_SOURCE_CONTROL.md).
- **Phase 18 — CodeCApi Device Capabilities** (was Phase 15) moved to the end:
  [`phases/07-platform-capabilities/chat-phase18/`](../phases/07-platform-capabilities/chat-phase18).

> Standing rule still applies: no PR/merge without the owner's explicit command.

---

## Phase 19 — Terminal Parity (Termux-quality terminal) — ✅ COMPLETE & DEVICE-ACCEPTED (PR on owner's word)

✅ **IMPLEMENTED (2026-08-31, `arena/01a056aa-codec`) on the owner's "Ok start
phase 19 … also find other things Termux does better and fix it"** — all FIVE
parts (19.1 reflow, 19.2 integer cells, 19.3 live cadence, **19.4 Unicode
widths**, **19.5 protocol/interaction parity** — the last two from the parity
audit). `Build APK` `33371114549` green (assemble + tests + lint; 2 red rounds
first caught the Indic vowel-sign width gap + test bugs). ~50 new host tests.
**Remaining gate: the owner's per-part device recipes**
(`phases/01-terminal-userland/chat-phase19/PART_19_*.md` §5). Original spec follows.

**Device round 1 (2026-08-31, owner transcript):** ONE regression found —
*"letters have a noticeable gap between them"*: 19.2's `ceil(advance)` cell
added up to 1px of tracking per letter. **Fixed same day** with
`CellMetrics.fitSizeToGrid` (nudge the font size <1% until the advance IS a
whole pixel, so the integer cell equals the font's advance — crisp AND tight;
postmortem in `phases/01-terminal-userland/chat-phase19/PART_19_2_RENDERING.md` §7.1). Round-1 recipes
were also unusable on-device (multi-line paste, nonexistent `/usr/bin`) — all
round-2 recipes are single-line copy-pasteable. Round-1 positives: soft-wrap
of long lines ✓, Bengali/CJK/emoji echo ran clean. **Round 2 (same day,
owner screenshots + answers + `stty size` 32×60 vs Termux 39×71): density &
weight — fixed via default 12sp + bundled JetBrains Mono Medium/Bold (OFL) +
0.9 row-pitch factor (`fitSizeToGrid` kept; PART_19_2 §7.2). Round 3 (owner: "feels lagging /
not smooth scrolling / keyboard sometimes not popping up"): 4 fixes —
run-batched drawing, gesture detectors no longer restart every output
frame, pixel-smooth sub-row scrolling, IME retry loop (PART_19_3 §9).
**Round 4 (2026-08-31): PASS — owner: "All ok now".**
Phase 19 CLOSED: CI `33377713289`, 4 device rounds, ~70 new host tests.
**PR #34 MERGED to `main` at `b869ce6` (2026-08-31) — Phase 19 is fully
closed; post-merge CI on `main` green (`33380041937`).**

Phase 19 was specced 2026-08-31 on the owner's report that the terminal
(a) prints downloads only at the end, (b) overlaps letters, and (c)
doesn't reflow on zoom-out. Re-implements Termux-quality behavior in CodeC's own
clean-room emulator (⚖️ **not** copying GPLv3 Termux code). Client-only, pure
Kotlin/Compose, host-testable. Spec + before/after mockup in
[`phases/01-terminal-userland/chat-phase19/`](../phases/01-terminal-userland/chat-phase19).

- **Phase 19.1 — Scrollback & screen reflow on resize/zoom** (fixes zoom-out not
  refilling). [`PART_19_1_REFLOW.md`](../phases/01-terminal-userland/chat-phase19/PART_19_1_REFLOW.md).
- **Phase 19.2 — Integer-cell crisp rendering** (fixes letter overlap).
  [`PART_19_2_RENDERING.md`](../phases/01-terminal-userland/chat-phase19/PART_19_2_RENDERING.md).
- **Phase 19.3 — Live render cadence & streaming output** (fixes "prints at the
  end"). [`PART_19_3_LIVE_OUTPUT.md`](../phases/01-terminal-userland/chat-phase19/PART_19_3_LIVE_OUTPUT.md).

> Independent of Phases 15–18; can be scheduled first. No PR/merge without the
> owner's explicit command.

---

## Phase 10 — Package & Command Hub (Modules Screen Upgrade)

✅ **IMPLEMENTED (2026-08-28 on `arena/01a0482c-codec`).**
- 1-tap direct package installation (`pkg install -y <pkg>`) and execution into live terminal (`\r` line discipline).
- Quick repository management actions (`pkg update`, `pkg upgrade -y`, `codec-setup-storage`, `pkg status`, `pkg heal`, `pkg repair`).
- Curated 25+ package catalog (Compilers, Editors, Languages, CLI tools, Compression).
- Live `$PREFIX/bin` installation status detection (`INSTALLED ✓` / `AVAILABLE`).
- Custom interactive command runner card.
See `docs/phases/02-packages-toolchains/chat-phase10/PART_10_PKG_GUI.md`.
