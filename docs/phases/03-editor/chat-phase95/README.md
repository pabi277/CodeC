# Phase 95 — history, welcome + agreement, composer fix, ChatGPT-style shell (2026-10-06)

> *"Yes it's working but i want you to give the isn't. More working functions … add history option and add a welcome interface and a agreement that it's only for small work not to for havey code write and api will be user own personal wish the creator isn't responsible … and after sending a massage after end the reply it still stay in the input box … I add some chatgtp screenshot see the ui something like this create in CodeC."*
>
> — the owner, Phase 95 request. Attached two ChatGPT screenshots (menu drawer, new-chat hero).

## 1. What shipped (four things, in his words)

| # | He asked for | What landed |
|---|---|---|
| 1 | *"add history option"* | ☰ menu in the top-left opens a drawer with **Pinned** and **Recents** sections, pin/unpin per row, an outlined **+ New chat** pill, and an × close. New chat now **archives** the current conversation into the drawer instead of deleting it — that is what a history must do. A tap switches the conversation whole, so two chats never merge. Bounded at 20 per project per session, with pinned rows and the current one protected from eviction. |
| 2 | *"add a welcome interface and a agreement that it's only for small work … api will be user own personal wish the creator isn't responsible"* | A one-card welcome screen with a round sparkle, a title, a subtitle, and four checkmarked bullets that say exactly what he wrote (small work only · your key, your bill · always review before Apply, nothing runs without your tap · the creator is not responsible). One blue **"I understand — start"** button; the composer and every action card are not drawn until after he taps it. The flag (`welcome_version`) is stored in the existing non-secret `ai_settings.properties`, bumped whenever the copy changes. |
| 3 | *"after sending a massage after end the reply it still stay in the input box"* | The composer clears the instant the arrow sends (the value is display state in `EditorScreen.rememberSaveable`; nothing changes about what was sent — D4 still holds: preview bytes and sent bytes are identical). |
| 4 | *"see the ui something like this"* (ChatGPT screenshots attached) | The shell is reshaped in the same silhouette — round sparkle hero on welcome, ☰ in the header, outlined "+ New chat" pill at the top of the drawer, Pinned/Recents sections, × to close — while keeping CodeC's AutoFixHigh brand mark so the app still reads like CodeC between projects and on a fresh install. |

## 2. Where the rules run

- **D6 (session-only) does not move.** History is the same in-memory map Phase 93 introduced, widened from one entry to many: `historiesByProject: LinkedHashMap<String, AiChatHistory>` in `AiViewModel`, keyed by project name, replaced whole on switch, and never written to a file, DataStore, SharedPreferences, logs or backups. `commitFinishedTask()` now writes the settled conversation back into the per-project history before any switch can lose it.
- **D4 (preview = send) still holds.** The agreement is a UI gate, not a network gate — nothing is sent until a preview's Send, and Send is still the only road to the network. The welcome card doesn't draw the composer or any action that could touch the wire, so it cannot be scrolled past into a send.
- **D1 (no write/run without a tap) still holds.** The agreement says "always review before you apply" and "nothing runs without your tap"; those are labels, not relaxations. Write and run still need their separate approvals.
- **Content-level secret redaction (Phase 94) is untouched** (`AiToolRunner.Outcome.scrubbed()` + `AiProjectFiles.sliceFor`). More power through read/inspection tools with sensitive-data guards; write/run authority was not added.
- **Welcome agreement is a UI flag, not a permission.** It lives in `AiKeyStore` (non-secret metadata, same file as bubble position and provider terms), shown once per `AiKeyStore.WELCOME_VERSION`, and accepting it does not grant anything — it just dismisses the card. A key, a provider selection and a Send are still required before anything leaves the device.

## 3. The welcome agreement (copy, verbatim)

**Before you ask**

- Small work only. This helper is for short questions, explanations and small, reviewable edits — not for writing whole apps, large refactors, or code you have not read.
- Your API key, your bill, your rate limits. CodeC does not host a model and does not pay for requests. Calls leave your phone with the key you saved, under the provider's own terms.
- Always review before you apply. Proposed edits are shown as a diff, and Apply is your tap — never automatic. Nothing runs on your device without your tap either.
- The creator is not responsible for what the model writes, for charges you incur through your own key, or for code you run on your own device.

Button: **I understand — start** (one tap). "Not now" puts the sheet away and leaves nothing configured.

## 4. History semantics (what a tap does)

| Action | Effect |
|---|---|
| ☰ (header) | Opens the drawer from the right edge; same swipe gesture as ChatGPT's drawer. |
| Tap a Pinned/Recents row | **Saves** the conversation in front of you into the history, then **switches** to the tapped one — the session is replaced whole (no merge). Drawer closes. |
| Pin/Unpin icon | Toggles the pin. Pinned chats survive eviction and appear in their own section above Recents. |
| + **New chat** (pill, or header +) | Confirms, saves the current chat into the drawer, opens an empty composer, and shows the drawer so the owner can see where the previous conversation went. |
| × (drawer) | Closes the drawer; no change to the conversation. |
| Switch project | Saves the current conversation into this project's history, restores the other project's history whole, closes the drawer. Project B can never see A's turns. |

The title shown on a drawer row is the first question the owner asked, one line, clipped at 48 chars; no message body is drawn in the drawer.

## 5. Files

| File | What changed |
|---|---|
| `ui/ai/AiChatHistory.kt` (new) | Pure, Android/java.io/clock-free model: `AiChatEntry`, `AiChatSummary`, `AiChatHistory` (entries, currentId, nextId, `withCurrent`, `beginNew`, `switchTo`, `togglePin`, `pinned`/`recents`, `summaries`, bounded eviction, `titleOf`). Host-tested. |
| `ui/ai/AiKeyStore.kt` | `PROP_WELCOME_VERSION`/`WELCOME_VERSION`, `welcomeAccepted()`, `acceptWelcome()` — non-secret metadata. |
| `ui/ai/AiCopy.kt` | `WELCOME_*`, `HISTORY_*`, `PINNED`, `RECENTS`, `DRAWER_OPEN/CLOSE`, `HISTORY_PIN/UNPIN`, `WELCOME_AGREE/DISAGREE`, `CURRENT_CHAT`, `HISTORY_EMPTY` strings. |
| `ui/ai/AiViewModel.kt` | `AiUiState` gains `welcomeAccepted`, `historyOpen`, `history`; old single-chat `chatsByProject/ProjectChat/chatForProject` replaced by `historiesByProject/AiChatHistory/historyForProject`; `openHistory/closeHistory/switchChat/toggleChatPin/acceptWelcome` actions; `newChat()` now archives via `withCurrent().beginNew()`; `onProjectChanged` restores `history` and closes the drawer; `commitFinishedTask()` writes the settled session back into the current project's history so a switch can't drop it. Init loads `welcomeAccepted`. |
| `ui/ai/AiChatSheet.kt` | Wraps the sheet surface in a `ModalNavigationDrawer`, adds `HistoryDrawer`/`HistoryRow`/`SectionLabel`, adds `WelcomeCard` (shown when `!welcomeAccepted`, no composer drawn), adds ☰ to the header, adds the new drawer callbacks, adds the imports needed (LazyColumn/items, ModalDrawerSheet/ModalNavigationDrawer/DrawerValue/rememberDrawerState, Menu/Close/PushPin/Check icons, CircleShape, rememberCoroutineScope, launch). |
| `ui/screens/EditorScreen.kt` | Composer clears immediately on Send (`aiQuestion = ""` before calling `aiViewModel.send()`); wires the five new callbacks (`onOpenHistory`/`onCloseHistory`/`onSwitchChat`/`onToggleChatPin`/`onAcceptWelcome`). |
| Tests | `AiChatHistoryTest` 7 cases (new, pure model). `AiChatSessionWiringTest` "New chat clears" and "project switch keeps transcript" pins rewritten for the history model. `Phase93WiringTest` "one conversation per project" pin rewritten for `historiesByProject`/`historyForProject`. `AiSelfCheckWiringTest` self-check reset pin relaxed from "exact two sites" to "≥ 2 sites" (history switching adds a third reset site). |

## 6. Verification

- **Host sweep** (kotlinc 2.2.10, harness rebuilt after the sandbox reset it): `AiChatHistoryTest` 7/7/0 on the pure model; the wider host sweep is not green in this rebuilt sandbox (it needs Android stubs for Compose/`AiKeyStore`/`AiViewModel` imports), so CI is the full gate — same as earlier phases.
- **Build APK [`37434115270`](https://github.com/pabi277/CodeC/actions/runs/37434115270) green on `195d308`** (14m26s): release universal APK **7 215 648 B**, release set **6 555 178 B**, R8 mapping **4 739 715 B**, debug **26 204 013 B**, *release manifest: no `android:debuggable` flag*. All 45 steps pass, including host unit + screenshot tests. (The first push, `37432522206`, failed at 3m30s with no annotations and the logs endpoint answered EOF, same shape as the Phase 94 transient — re-triggered and green.)

## 7. Boundaries kept

- The AI still has no write/delete/exec/shell tool; edits still arrive as a PROPOSE_EDITS preview → diff → review → Apply; run still waits for the Run tap.
- D6: chat text (history + current session + timeline + self-check verdicts) lives and dies with the process; nothing is persisted except the `welcome_version` metadata flag and the existing key/model/bubble/options settings.
- The drawer shows titles only (clipped first question), never message bodies, and `AiChatHistory.titleOf` is derived, not stored — so there is no second copy of chat text to keep in step with the transcript.
- Content-level secret redaction (Phase 94) is untouched; the new welcome does not relax any tool policy.
