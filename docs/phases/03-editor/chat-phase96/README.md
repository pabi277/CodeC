# Phase 96 — the bug sweep after the Phase 95 device round (2026-10-06)

> *"Fix, don't just report."* — the owner's standing instruction for this sweep. The brief in full: read `main` as merged (`1c6f910`, PR #114), work the Phase 95 device list (D1–D11) and the Phase 94 list (D1–D8) against the code, fix what is genuinely broken, add only **small, reviewable read-power** improvements, document the phase with a device-round checklist, update the living docs — and open **no PR** until he has confirmed the round on his phone.

No new feature landed here. Six defects were found (none of them visible in a code review that does not trace *order*), one read-power item shipped, one drift hole was closed with a pin, and the sandbox harness was rebuilt so the whole AI model layer can be run before CI again.

## 1. The six fixes

| # | Symptom (what the owner would have seen) | Cause | The change |
|---|---|---|---|
| 1 | Switch project A → B → A: **B's drawer showed A's chats**, and A's last exchange was gone. | `onProjectChanged` set `project = name` **before** `clear()`. `clear()` settles the finished task via `commitFinishedTask()`, which files the conversation under `project` — so the commit landed in the *arriving* project's slot. | `clear()` + `closeSheet()` now run **before** the pointer moves. Pin: `Phase96WiringTest` "the settle happens while `project` still names the leaving project". |
| 2 | Tap a history row: the **previous conversation's last exchange was appended to the row you tapped** — the merge the drawer promises never to do, and it reached the stored map, not just the screen. | `switchChat` applied `withCurrent(…).switchTo(id)` **before** `clear()`, so the settle committed into the entry `currentId` had just been moved to. | `clear()` first — the same order `newChat()` already used. Pin: the ordering test plus `AiChatHistoryTest.switchTo never merges` (pure model). |
| 3 | The drawer's **"+ New chat" pill started a new chat without asking**, then **closed the drawer it had just filled** — the archived row was never visible. Only the header `+` asked. | The `AlertDialog` lived inside `Header`, so the pill (a different composable) called `onNewChat` directly; and the pill's own lambda additionally did `drawerState.close(); onCloseHistory()`. | One `AlertDialog` hoisted to the sheet; `requestNewChat` is now the single entry point for both controls, and the pill leaves the drawer open. D8 is one sentence in one place. |
| 4 | Close the drawer with a **scrim tap or a swipe** → ☰ and the pill stop opening it, until something else closes it. | `historyOpen` was written by the buttons only. `ModalNavigationDrawer` can close itself; the flag stayed `true`, and both open-paths are `if (!state.historyOpen)`, so the state said "already open". | `LaunchedEffect(drawerState.currentValue)` writes the settled value back on `Closed` — the flag now follows the drawer in both directions. |
| 5 | A drawer with several rows could not answer *"where am I?"* | `AiChatSummary.current` was computed by the model, `AiCopy.CURRENT_CHAT` was declared — and neither was ever drawn. | `HistoryRow` marks the current row with one `Check` (and keeps the other titles aligned with a spacer). No new string, no new copy. |
| 6 | **The *Explain last error* card sent the build output unredacted** while `read_run_output` redacts the same text. | `AiContextBuilder.fromRunOutput` trimmed and clipped `cleaned` but never passed it through `AiSecretScan`; the tool route got the guard, the chip did not. A run output is the app's pick, not the user's, and a build line can carry a credential (`pip install https://user:token@host/…`, a test that printed an env value, a signed URL in a stack trace). | `AiSecretScan.redact(…)` now runs in `fromRunOutput`, **before** the budget is measured — so the ceiling still holds exactly, the `[withheld: N credential-shaped value(s)]` line survives the tail-keep, and the preview is the sent bytes (D4). |

Plus one micro-fix that is not a bug but is the reason the drawer flickered on a slow phone: `AiChatHistory.titleOf` compiled `Regex("\\s+")` per row per recomposition; the pattern is now a value.

**Two laws came out of 1 and 2, and they are the same law:** *anything that settles a task must run while the state still names the thing it belongs to.* Both bugs were a `clear()` moved two lines late, and both wrote into the persisted in-memory map, so neither shows up in a screenshot — only in the next project or the next row.

## 2. Read power (the only new capability)

**`search_project(query, max?, path?, ext?)`** — the same two scoping keys `list_files` already had. A question about one folder no longer spends `MAX_SEARCH_FILES` reads on the rest of the project, and both keys are validated by exactly the same rules (`safeDirectoryPath`, the `^[a-z0-9]{1,8}$` extension, the admitted-path existence check) so a scope that names nothing is **refused with a reason** rather than answered as "not in this project". The filter is applied to the walk's own admitted list, so it can only ever narrow, never widen, what Level 2 let in. The scope is visible everywhere it matters: the timeline row reads `search_project "malloc" in src/ *.kt` and the result header repeats it (`SEARCH "malloc" in src/ *.kt — 3 matches in 7 files scanned`), so the owner reviews what was actually searched (D4) and the model is never told a scoped-empty is an empty project.

**The drift pin.** The tool list exists in three places — `AiToolPolicy`'s `allowedKeys`, the sentence the model is taught (`AiToolProtocol.INSTRUCTIONS`), and the sentence the owner is shown (`AiCopy.agentPreviewNote()`). A tool present in one and not another is invisible on a device. `Phase96WiringTest` now compares the three **at runtime**: the `Tools:` line's names are set-equal to `AiToolName.entries`' wires, and the preview note names every read tool and only the read tools (`request_run` is described in its own sentence, never as a read).

**Considered and deliberately not done:** *generating* those two strings from the policy tables (it rewrites a model-facing prompt byte-for-byte for no behaviour change — the pin closes the hole, the refactor can wait for a phase that touches the prompt anyway); a read-only `read_diagnostics` tool (new surface, not "small"); and the on-device/local model plan, which the owner **parked** — nothing for it is scaffolded or stubbed here.

## 3. Checked and left alone (they were already right)

| Looked like a bug | Why it is not |
|---|---|
| "A refused write still has no *Grant access* row" (Phase 94 D-row, re-audited) | `storageFixRow` is drawn in `BottomBar` for every phase except `DONE`, and `DONE` has its own branch; the ViewModel sets `storageProblem` from `storageProblemFor(…)` at apply, undo **and** both `Failed` branches — the fix Phase 94 round 3 made still holds, and the row is never gated behind the welcome card. |
| "The composer does not clear after a reply" | The clear is on **Send**, at the single `send()` site, for both preview chips; the answer arriving later is not the composer's job. Re-confirmed for both chips. |
| "Tapping a history row leaves the drawer open" | It closes it (`onSwitchChat` → `closeHistory`), and a project switch closes it too — Phase 95 §4 as written. |
| "Chat text is persisted somewhere" (D6 audit) | `AiKeyStore` holds one int (`welcome_version`) plus the pre-existing key/model/bubble/options keys; `Log.`, `SharedPreferences` and `writeText(` are absent from `AiViewModel` (0 hits each, pinned); history rows are derived titles, never bodies. |
| `globMatches` metacharacters, `clip(body)` at 8 000, `welcomeAccepted` loading async, the `HistoryRow` pin icon's hit target | Each was raised and closed earlier with the reason in its own phase doc. Not re-litigated here. |
| Phase 95 D6 — *"ask a second question → two Recents rows"* | **The doc was wrong, not the app.** One chat is one row; a second question appends to the same entry, and a new row appears on New chat or a project switch. The row was rewritten in `chat-phase95/DEVICE_ROUND.md` and the correct expectation is [D6 here](DEVICE_ROUND.md). |

## 4. Files

| File | Change |
|---|---|
| `ui/ai/AiViewModel.kt` | Fix 1 and fix 2 — both are statement order inside `onProjectChanged` / `switchChat`, with the reason written where it can never be "tidied" back. |
| `ui/ai/AiChatSheet.kt` | `newChatConfirming` + one `AlertDialog` hoisted to the sheet; `requestNewChat` wired to header `+` **and** drawer pill; the pill no longer closes the drawer; `LaunchedEffect(drawerState.currentValue)` syncs `historyOpen` back; `HistoryRow` draws the current-row mark. |
| `ui/ai/AiChatHistory.kt` | `titleOf`: precompiled whitespace `Regex` (and the mis-indented body aligned). |
| `ui/ai/AiContext.kt` | Fix 6 — `fromRunOutput` scrubs through `AiSecretScan` before the budget, with the reason in the comment. |
| `ui/ai/AiTools.kt` | `search_project` accepts `path`/`ext`; `validateSearch(request, view)` mirrors `validateList`'s checks; `EXT_PATTERN` shared by both; `INSTRUCTIONS` teaches the two new keys; `describeCall`/`describe` name the scope. |
| `ui/ai/AiToolRunner.kt` | `search()` filters the admitted list by `path`/`ext` **before** scanning and carries `$scope` into both the hit header and the no-match line. |
| Tests | **new** `Phase96WiringTest` (14 cases: the ordering laws, the single confirm, the drawer-flag sync, the current-row mark, the scrub site, the scoping keys, the runtime three-way tool-list pin); `AiChatHistoryTest` 7 → 9 (the switch/new-chat ordering on the pure model); `AiToolProtocolTest` 26 → 27; `AiToolRunnerTest` 29 → 30. |
| Docs | this record + `DEVICE_ROUND.md`; Phase 95's `DEVICE_ROUND.md` D6 expectation corrected in place, with the correction marked. |

## 5. Verification

- **Host sweep, rebuilt.** `jdk4py` (Temurin 25) + `kotlin-compiler` 2.2.10 in `/home/user/tools`, harness in `/home/user/harness` (outside the repo, as in earlier phases). It derives the set rather than listing it: every `ui/ai` file closed over its real references, with a **fixpoint** for host-buildability (a pure file is out if the only declaration it names lives in a Compose file), and it *generates* a stub for the four cases where that is mere co-location (`LanguageType`, `EditorKey`, `ErrorType`, `OutputPhase` — the declaration is sliced out of its host file, never retyped, and a declaration that itself touches Android is refused). JUnit/`org.json`/`TemporaryFolder` are shims; `AiHttpStream`/`AiProviderClient`/`GeminiClient` stay blocked by design because they need `kotlinx.coroutines`.
  Result: **112 production files, 53 test files, 52 classes — 641 passed / 0 failed**. `AiProgressPolicyTest` is the one file left to CI (an Android-tied dependency), and the Compose layer (`AiChatSheet`, `AiViewModel`, `AiKeyStore`, `AiFloatingButton`, `AiHome`, `AiMarkdownView`, `AiParts`) is compiled by CI only — which is why the source pins in `Phase96WiringTest` exist.
- **CI is the executor of record, and it is green.** Build APK [`37461645069`](https://github.com/pabi277/CodeC/actions/runs/37461645069) **success** on `2eaecce` (14m17s, all steps ✓, including *Run host unit and screenshot tests* — the full `:app:testDebugUnitTest`, which compiles the Compose files the sandbox sweep cannot). Release universal APK **7 217 232 B**, debug APK **27 180 520 B**; artifacts: release set **6 556 148 B**, R8 mapping **4 738 133 B**, debug **26 205 601 B**; *release manifest: no `android:debuggable` flag*. (A docs-only follow-up run carries the same code.)

## 6. Boundaries kept

- No write, delete, exec or shell tool. Edits still arrive as `PROPOSE_EDITS` → preview → diff → review → **Apply** (his tap); a run still waits for **Run**. The one new capability is a narrower read.
- **D4**: the preview and the sent bytes are identical, including the `[withheld: N]` line and the search scope.
- **D6**: nothing new is persisted — the ordering fixes touch only the in-memory map, and the history still stores titles derived from live turns.
- Content-level redaction was **widened to a route that lacked it**, never weakened; no tool now returns a credential-shaped value raw.
- No new permission, dependency, store key or Settings control. The welcome hub, the AI home and the one-arrow flow are untouched.
- The local/on-device model plan stays **parked** — nothing was scaffolded for it.
- No PR, no merge, no `main` push: the device round comes first.
