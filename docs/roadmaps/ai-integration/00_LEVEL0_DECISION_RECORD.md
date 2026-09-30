# Level 0 — Decision record

**Status: Level 0 ✅ COMPLETE 2026-09-30 (owner: *"Start level 0"*, then answers to O1–O3). All nine decisions recorded; nothing OPEN.**
**This is a documentation deliverable. It does not authorize Level 1 or any app code, dependency, permission, or privacy-guide change.**

Companion pages: [Level 0 foundations](00_PRODUCT_AND_FOUNDATIONS.md) · [Level 1 spec](01_READ_ONLY_API_HELPER.md) · [roadmap](../AI_INTEGRATION_ROADMAP.md) · [research dossier and 2026-09-30 recheck](../../research/AI_INTEGRATION_RESEARCH_20260930.md#addendum-a--current-main-recheck-2026-09-30-1785b92).

Repository baseline for every claim here: `main` @ `1785b92` (PR #101). App source is identical to the dossier's `120460f` baseline; only AI planning docs changed between them.

## 1. Owner decisions (2026-09-30, answered in chat)

| # | Question | Owner answer | Recommended option? |
|---|---|---|---|
| D1 | What can the first AI release do? | **Read-only help.** It explains selected code or a compiler/run diagnostic. It never changes files and never runs anything. | Yes |
| D2 | First API provider | **Google Gemini**, with the user's own key (BYOK) | — (owner choice) |
| D3 | API key storage on the phone | **Encrypted with Android Keystore** | Yes |
| D4 | Confirmation before code is sent | **Preview and confirm every request** | Yes |
| D5 | Which files the AI may use | **Only the open CodeC project** under `files/CodeC/projects/`. No AI in single-file mode; no folders picked from shared storage. | Yes |
| D6 | Are AI conversations saved? | **Not saved.** The chat is gone when closed; nothing new is persisted. | Yes |

These answers settle questions 1, 2 (partly), 3 and 4 of [`00_PRODUCT_AND_FOUNDATIONS.md`](00_PRODUCT_AND_FOUNDATIONS.md#decisions-needed-before-implementation). Question 2's "custom OpenAI-compatible endpoints" part, question 5 (rollback scope) and question 6 (on-device targets) belong to Levels 3, 5 and 6 and are **deferred, not decided** (§5).

## 2. What each decision means in practice

Rules that follow directly from the owner answers and current-main facts. Each cites the evidence.

### D1 — Read-only
- No write, run, terminal, package, or Git capability, and no "Apply" button. Output can be copied only.
- The AI panel may occupy the reserved fifth rail slot (`ui/editor/SidePanelPlan.kt:59-73,124-128`; `ui/components/EditorSidePanel.kt:204,236,252`). Until Level 1 is authorized, the slot stays `RailPanel.RESERVED`.

### D2 — Gemini
Verified in Google's primary docs, 2026-09-30:
- **API choice.** The Interactions API (`POST https://generativelanguage.googleapis.com/v1beta/interactions`, header `x-goog-api-key`) is GA as of June 2026 and recommended for new projects. `generateContent` is "legacy" but "remains fully supported". Source: [Interactions API overview](https://ai.google.dev/gemini-api/docs/interactions-overview).
- **Server-side storage.** The Interactions API stores interactions by default (`store=true`): 55 days on the paid tier, 1 day on the free tier. **To honour D6, Level 1 must send `store=false` (or use `generateContent`) and must not use `previous_interaction_id`.** Stateless requests carry any needed context themselves. Same source.
- **Free-tier data use.** Under the [Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms) (effective March 23, 2026), on unpaid quota Google "uses the content you submit … and any generated responses to provide, improve, and develop Google products", and "human reviewers may read, annotate, and process your API input and output". The terms say "Do not submit sensitive, confidential, or personal information to the Unpaid Services". On paid services, prompts are not used to improve products, but they are logged "for a limited period" for abuse detection. **The per-request preview (D4) must state this in plain words.** CodeC cannot see which tier a key belongs to, so it must describe both.
- **Model IDs change quickly.** The docs list many current models (for example `gemini-3.8-flash`, `gemini-2.5-flash`). Do not hard-code a single ID as permanent. The default-model rule is decided in §4 (O3).
- Custom safety settings are not supported in the Interactions API (same overview page). This is acceptable for code explanation and is recorded only so nobody plans around it.
- The `metadata.json` capability `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` and the commented-out `GEMINI_API_KEY` in `.env.example` are unrelated AI Studio packaging. **CodeC must never ship a developer key.** The key is always the user's.

### D3 — Keystore encryption
- No encryption code exists in the repo today. The GitHub token sits in the plain settings DataStore, protected only by the app sandbox and backup exclusion (`ui/projects/GitCredentialsStore.kt`; `res/xml/backup_rules.xml`).
- `androidx.security:security-crypto` (`EncryptedSharedPreferences`) is deprecated upstream. Recommended design: a non-exportable **AES-GCM key in Android Keystore** (`KeyGenParameterSpec`, API 23+, compatible with minSdk 24), with only the ciphertext and IV stored in app-private storage. This adds **no new dependency**.
- Store it outside `files/CodeC/projects/`. Backup includes only `CodeC/projects` (`backup_rules.xml`, `data_extraction_rules.xml`, pinned by `BackupRulesTest`), so the ciphertext is never backed up. That is intended: a restore or reinstall asks for the key again.
- Keystore failure (key invalidated, OEM Keystore bug) must show "Re-enter your API key", never crash, and never fall back to plaintext.
- The key never enters the prompt, the preview, logs, `crash-log.txt` (`MainActivity.kt:151-159`), the feedback draft, the clipboard, project files, or exports.
- `FeedbackDraft.redact` today covers GitHub token shapes, `Authorization` headers, `key=value` pairs and the stored Git token only (`ui/support/FeedbackDraft.kt:236-260`). **Level 1 must extend it to scrub the stored Gemini key and the Google API-key shape** (see §4.2), with a host test.
- The user can delete the key in one tap. Removing it deletes both the ciphertext and the Keystore entry.

### D4 — Preview every request
- Before each request, show: the provider ("Google Gemini"), the model, the exact text that will be sent (the selection or diagnostic plus CodeC's fixed instruction text), its size, and the D2 data-use note. **Send** and **Cancel** are both one tap. No "don't ask again" option in Level 1.
- If the selection comes from an unsaved buffer, say so. The live buffer is `EditorViewModel._codeText`, and autosave is debounced (`EditorViewModel.kt:378-392`).
- No request is made at cold start, while browsing, editing or running, or when the panel opens. The network is used only after **Send**.

### D5 — Open project only
- Level 1 sends only the selection or the latest diagnostic from the active project tab, so project-wide reading is not in scope yet.
- The panel is disabled in `EditorOpenMode.SINGLE_FILE` (`ui/editor/EditorOpenMode.kt:21,45`), with a short reason.
- For Level 2 later, the context builder must add an **AI-specific deny list**. The existing `ProjectSearch.isSearchable` deliberately includes `.env`, `.env.*` and `.npmrc` through `ProjectFilesPolicy.usefulConfig` (`ui/editor/ProjectSearch.kt:159-172`, `ui/editor/ProjectFilesPolicy.kt`). Reusing the engine as it is would expose secrets.

### D6 — Nothing saved
- The chat lives in memory only (for example in a ViewModel) and is cleared when the panel closes, the project changes, or the process dies. There is no history file, no DataStore entry for messages, and nothing in `.codec/`.
- Server side: `store=false` (see D2). Google's own abuse-monitoring logs and free-tier product use still apply. The UI must not claim "nothing is kept anywhere".

## 3. Level 1 constraints now fixed by Level 0

A future Level 1 brief must satisfy all of these. They are additions to [`01_READ_ONLY_API_HELPER.md`](01_READ_ONLY_API_HELPER.md):

1. Gemini only, one model, user's key; stateless `streamGenerateContent` with `store:false` on every request (§4.2).
2. Keystore AES-GCM key storage outside the backed-up path. Keystore errors lead to re-entering the key; there is never a plaintext fallback. The key is saved only after the 18+ and terms confirmation (§4.1).
3. Per-request preview with the Gemini free-tier disclosure. Nothing sent without **Send**.
4. Project tabs only; disabled in single-file mode.
5. In-memory chat; nothing persisted.
6. Redaction extended to the Gemini key; key-leak tests cover the crash log, feedback draft and error text.
7. No new Android permission: `INTERNET` and `ACCESS_NETWORK_STATE` already exist (`AndroidManifest.xml:5-6`), so `ManifestPermissionsTest` should not change.
8. `docs/guides/DATA_AND_PRIVACY.md` is updated **in the same change that ships the feature**, not before.

## 4. Former open items — decided 2026-09-30

| # | Item | Owner answer | Resulting rule |
|---|---|---|---|
| O1 | **Gemini terms: 18+ and "not for consumer use"** ([Additional Terms](https://ai.google.dev/gemini-api/terms): users 18 or older; no API clients "likely to be accessed by individuals under the age of 18"; "professional or business purposes, not for consumer use"; EEA/Switzerland/UK "only Paid Services") | **"Show Google's terms and ask the user to confirm they are 18+ during setup" — yes.** A different first provider: no. On legal advice the owner said: *"it's ok i am an Indian"*. | See §4.1. |
| O2 | **HTTP client and API technical design** | *"You decide the technical part"*: delegated to the agent. | See §4.2. |
| O3 | **Default model and model picker** | **"Ok"**, accepting the recommendation. | One model text field pre-filled with a current Gemini Flash model ID (re-read from Google's model list when Level 1 is briefed; `gemini-3.8-flash` on 2026-09-30), plus a **Test connection** button. No live model catalog in Level 1. |

### 4.1 Key-setup gate (O1)

Shown once, when the user first saves a Gemini key, before the key is stored:

1. Plain text: *"CodeC sends requests to Google using **your own** Gemini API key. Your use is covered by your own agreement with Google."* With links to the [Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms) and the [Prohibited Use Policy](https://policies.google.com/terms/generative-ai/use-policy). Links open in the browser only after a tap.
2. The free-tier data-use note from D2 (Google may use free-tier prompts to improve products; human reviewers may read them).
3. A factual note, without legal interpretation: Google limits where the API may be used, and in the EEA, Switzerland and the UK only paid keys are permitted.
4. A required checkbox: **"I am 18 or older and I accept Google's Gemini API terms for my key."** **Save key** stays disabled until it is ticked. Cancel stores nothing.

Rules:
- The acceptance is saved as a non-secret flag with the date it was accepted, stored outside `files/CodeC/projects/` so it is not backed up.
- **Deleting the key also clears the acceptance**, so a new key means a new confirmation.
- If a future Google terms update requires it, the flag can be reset by bumping a version constant.
- The owner's note records his own situation. It does not change the fact that Google's age, consumer-use and regional terms apply to every user of their key. CodeC shows those terms and does not interpret them.

### 4.2 Technical design for Level 1 (O2, agent decision)

| Concern | Decision | Why / evidence |
|---|---|---|
| HTTP client | `java.net.HttpURLConnection` over HTTPS, with explicit connect/read timeouts, run off the main thread (`Dispatchers.IO`). **No new dependency.** The official `com.google.genai` SDK is not used. | Same pattern as `GitHubPublishApi.kt` (dependency-free by design, `connectTimeout`/`readTimeout`). OkHttp was deliberately removed in Phase 42.2 (`app/build.gradle.kts:269`). APK weight is a CI check. |
| Endpoint | `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse`, key sent in the `x-goog-api-key` header. **Never** as a `?key=` query parameter, because URLs end up in exception messages and logs. | Stateless by construction: each request carries its own context, with no server-side conversation IDs. The API is "legacy" but "remains fully supported" ([Interactions overview](https://ai.google.dev/gemini-api/docs/interactions-overview)). Move to the Interactions API only when tool calling needs it (Level 4/5), as a separate decision. |
| Server-side storage | Send `"store": false` on **every** request. A host test fails if a request body lacks it or contains any conversation or interaction ID. | `store` "configures the logging behavior for a given request … takes precedence over the project-level logging config" ([generateContent reference](https://ai.google.dev/api/generate-content)). This backs up D6. |
| Wire-format check | **Level 1's first step:** confirm the exact SSE event shape against the official reference with one harmless request before writing the parser. If streaming cannot be parsed reliably, use non-streaming `:generateContent` with a visible "waiting…" state instead of guessing. | Rule §4: evidence before hypothesis. |
| Parsing | A pure-Kotlin line splitter for the SSE stream (host JUnit), plus Android's built-in `org.json` for payloads, tested with Robolectric. **No new JSON library.** | Existing pattern: `ReleaseFetch.kt` + `ReleaseFetchTest.kt` (Robolectric because `org.json` is a framework class). |
| Cancel | Cancelling the coroutine calls `HttpURLConnection.disconnect()` and closes the stream. At most one request is in flight at a time. | Level 1 acceptance: no hidden network activity after cancel. |
| Limits | Cap the outgoing text (selection or diagnostic) at a fixed size, shown in the preview; cap the streamed reply length; limits live in constants that tests pin. | Avoids unbounded requests and cost; mirrors the `ProjectSearch` bounded-work rule. |
| Errors | Map HTTP 400/401/403 (bad or restricted key), 429 (rate or quota limit), 5xx, timeouts and offline (`ACCESS_NETWORK_STATE`) to short messages. Blocked responses (`promptFeedback`/`finishReason`) show "Gemini declined to answer". Messages never include the key, the request body, or the full URL. | D3 and A7. |
| Test connection | Sends one fixed, content-free prompt with `store:false`, only after a tap, and says so ("sends a short test message to Google; no code"). | D4: nothing leaves without a user action. |
| Key storage | Keystore AES-GCM key (non-exportable) + ciphertext and IV in app-private storage outside `CodeC/projects` (D3). Any decrypt failure deletes the ciphertext and prompts for re-entry. | No plaintext fallback. |
| Redaction | Extend `FeedbackDraft.redact` with the stored Gemini key (exact match) and the Google API-key shape (`AIza` + 35 URL-safe characters), with host tests. | A7. |
| Code placement | Provider code isolated behind a small `AiProvider`-style interface in its own package, with request building, the SSE splitter, error mapping and redaction written Android-free and host-testable (rule §4.4). | Keeps the Level 5 provider seam cheap without building it now. |

## 5. Deferred to later levels (explicitly not decided now)

- **Rollback scope** (Level 3). The recommendation remains file edits only; command, package and Git effects are separately approved and never called "undoable". Evidence it needs design: `EditorUndoManager` is in-memory, capped at 100 steps, and cleared on project or mode switch (`EditorUndoManager.kt`; `EditorViewModel.kt:470,1579,1667`), and no external-change detection was found.
- **Custom OpenAI-compatible endpoints** (Level 5). Plain HTTP is allowed only to `127.0.0.1` and `localhost` (`res/xml/network_security_config.xml`).
- **On-device targets** (Level 6). Relevant facts: minSdk 24, targetSdk 28, ABIs `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`, NDK 27.2 (`app/build.gradle.kts:18-59`).
- **Agent run loop** (Level 4). `ExecutionRunner` defaults to a 30 s build and 10 s run timeout with a single live process (`ui/services/ExecutionRunner.kt:44-45`).

## 6. Level 0 exit condition — met

O1–O3 were answered on 2026-09-30 (§4), so **Level 0 is complete.** The next step is a **Level 1 brief**: a numbered phase under `docs/phases/<category>/chat-phaseNN/`, following [`HOW_TO_CREATE_A_PHASE.md`](../../getting-started/HOW_TO_CREATE_A_PHASE.md), built from §2–§4 of this record. **Writing that brief, and any Level 1 code, still requires the owner's explicit start command.** Nothing here authorizes code, dependencies, permissions or privacy-guide changes, and the `rule.md` §3 merge gate applies.

> **Update 2026-10-01 (O3, Phase 76 device round 1):** the pre-filled model is `gemini-3-flash-preview`, the id that worked on the owner's phone (*"i have to use gemini-3-flash-preview this model"*); `gemini-3.8-flash`, named above from the docs, did not answer for his key. The decision itself (a pre-filled, editable Flash model plus Test connection) is unchanged.
