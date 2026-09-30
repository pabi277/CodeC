# Level 0 — Decision record

**Status: Level 0 STARTED 2026-09-30 (owner: *"Start level 0"*). Six product decisions answered; three items still OPEN (§4).**
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
- **Model IDs change quickly.** The docs list many current models (for example `gemini-3.8-flash`, `gemini-2.5-flash`). Do not hard-code a single ID as permanent. The default model is an OPEN item for the Level 1 brief (§4).
- Custom safety settings are not supported in the Interactions API (same overview page). This is acceptable for code explanation and is recorded only so nobody plans around it.
- The `metadata.json` capability `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` and the commented-out `GEMINI_API_KEY` in `.env.example` are unrelated AI Studio packaging. **CodeC must never ship a developer key.** The key is always the user's.

### D3 — Keystore encryption
- No encryption code exists in the repo today. The GitHub token sits in the plain settings DataStore, protected only by the app sandbox and backup exclusion (`ui/projects/GitCredentialsStore.kt`; `res/xml/backup_rules.xml`).
- `androidx.security:security-crypto` (`EncryptedSharedPreferences`) is deprecated upstream. Recommended design: a non-exportable **AES-GCM key in Android Keystore** (`KeyGenParameterSpec`, API 23+, compatible with minSdk 24), with only the ciphertext and IV stored in app-private storage. This adds **no new dependency**.
- Store it outside `files/CodeC/projects/`. Backup includes only `CodeC/projects` (`backup_rules.xml`, `data_extraction_rules.xml`, pinned by `BackupRulesTest`), so the ciphertext is never backed up. That is intended: a restore or reinstall asks for the key again.
- Keystore failure (key invalidated, OEM Keystore bug) must show "Re-enter your API key", never crash, and never fall back to plaintext.
- The key never enters the prompt, the preview, logs, `crash-log.txt` (`MainActivity.kt:151-159`), the feedback draft, the clipboard, project files, or exports.
- `FeedbackDraft.redact` today covers GitHub token shapes, `Authorization` headers, `key=value` pairs and the stored Git token only (`ui/support/FeedbackDraft.kt:236-260`). **Level 1 must extend it to scrub the stored Gemini key and Google-key shapes**, with a host test.
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

1. Gemini only, one model, user's key, with `store=false` or a stateless `generateContent` call.
2. Keystore AES-GCM key storage outside the backed-up path. Keystore errors lead to re-entering the key; there is never a plaintext fallback.
3. Per-request preview with the Gemini free-tier disclosure. Nothing sent without **Send**.
4. Project tabs only; disabled in single-file mode.
5. In-memory chat; nothing persisted.
6. Redaction extended to the Gemini key; key-leak tests cover the crash log, feedback draft and error text.
7. No new Android permission: `INTERNET` and `ACCESS_NETWORK_STATE` already exist (`AndroidManifest.xml:5-6`), so `ManifestPermissionsTest` should not change.
8. `docs/guides/DATA_AND_PRIVACY.md` is updated **in the same change that ships the feature**, not before.

## 4. OPEN — still needs owner decisions before Level 1 can be briefed

| # | Open item | Why it matters | Agent recommendation (not decided) |
|---|---|---|---|
| O1 | **Gemini terms: age and "not for consumer use".** The [Additional Terms](https://ai.google.dev/gemini-api/terms) say "You must be 18 years of age or older to use the APIs", forbid API clients "likely to be accessed by individuals under the age of 18", and describe the API as "for developers … for professional or business purposes, not for consumer use". EEA/Switzerland/UK: "only Paid Services" for API clients made available to users there. | CodeC has beginner onboarding and CodeC Arcade. With BYOK the user holds the key and accepts Google's terms, but CodeC is the client. This is a product and legal question, not a technical one; the agent cannot give legal advice. | Owner decides. Options: (a) Level 1 setup shows Google's terms link plus an 18+ and "my own key, my own agreement with Google" confirmation; (b) reconsider the first provider; (c) get advice before shipping. |
| O2 | **HTTP client** | OkHttp was removed in Phase 42.2 (`app/build.gradle.kts:269`). Existing network code uses `HttpURLConnection` (`GitHubPublishApi.kt`, `DownloadManager.kt`, …). | Use `HttpURLConnection` with a small, host-tested streaming parser, and add no dependency. The official `com.google.genai:google-genai` Java SDK would be a new dependency and needs its own review. |
| O3 | **Default model and model picker** | IDs and prices change; free-tier availability differs by model. | One text field pre-filled with a current Flash model at implementation time, plus a "Test connection" button. No live catalog in Level 1. |

## 5. Deferred to later levels (explicitly not decided now)

- **Rollback scope** (Level 3). The recommendation remains file edits only; command, package and Git effects are separately approved and never called "undoable". Evidence it needs design: `EditorUndoManager` is in-memory, capped at 100 steps, and cleared on project or mode switch (`EditorUndoManager.kt`; `EditorViewModel.kt:470,1579,1667`), and no external-change detection was found.
- **Custom OpenAI-compatible endpoints** (Level 5). Plain HTTP is allowed only to `127.0.0.1` and `localhost` (`res/xml/network_security_config.xml`).
- **On-device targets** (Level 6). Relevant facts: minSdk 24, targetSdk 28, ABIs `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`, NDK 27.2 (`app/build.gradle.kts:18-59`).
- **Agent run loop** (Level 4). `ExecutionRunner` defaults to a 30 s build and 10 s run timeout with a single live process (`ui/services/ExecutionRunner.kt:44-45`).

## 6. Level 0 exit condition

Level 0 is **complete** when O1–O3 have owner answers recorded in §4. After that, Level 1 can be briefed as a numbered phase under `docs/phases/<category>/chat-phaseNN/`, following [`HOW_TO_CREATE_A_PHASE.md`](../../getting-started/HOW_TO_CREATE_A_PHASE.md). **That still requires the owner's explicit start command.** Nothing in this record authorizes code, and the `rule.md` §3 merge gate applies.
