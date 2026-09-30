# Phase 76 — AI Level 1: the read-only Gemini helper

> Owner row (2026-09-30): ***"Start lavel 1"*** — after Level 0 closed with
> every product boundary decided
> ([`00_LEVEL0_DECISION_RECORD.md`](../../../roadmaps/ai-integration/00_LEVEL0_DECISION_RECORD.md)).
> Spec: [`01_READ_ONLY_API_HELPER.md`](../../../roadmaps/ai-integration/01_READ_ONLY_API_HELPER.md).
> Research: [`AI_INTEGRATION_RESEARCH_20260930.md`](../../../research/AI_INTEGRATION_RESEARCH_20260930.md)
> (Addenda A–B).

**Status: ✅ DEVICE-PASSED (round 1, 2026-10-01) on `arena/01a0f34c-codec` —
default model changed to `gemini-3-flash-preview` per the owner's report;
**merged to `main` via [PR #102](https://github.com/pabi277/CodeC/pull/102) on the owner's command** (2026-10-01: *"update all docs file
and create ui file for next chat than you can merge everything into main"*).
Next: [Phase 77](../chat-phase77/README.md) — the phone AI UI (floating
button + bottom chat sheet), briefed for the next chat.** Levels 2+ are not
authorized.

## What the user gets

The fifth side-panel rail slot — reserved for AI by the owner since Phase 55
(*"Reseserve it i have plan for ai i can use that"*) — now opens **AI helper ·
Gemini**:

1. **Key setup (once):** the user's own Gemini API key, Google's terms
   linked, the free-tier and EEA/CH/UK notes, and the required checkbox
   *"I am 18 or older and I accept Google's Gemini API terms for my key."*
   Model pre-filled `gemini-3-flash-preview` (device-proven), editable.
2. **Explain selection** / **Explain last error**, with an optional question.
3. **Preview every time:** model, character count, free-tier note and the
   *exact* text (instruction + user text) that will leave the phone. Nothing
   is sent until **Send**.
4. **Streaming answer** with **Stop**; then **Copy answer** or **New question**.
   No apply/insert/run control exists.
5. **AI settings (⚙):** change model, **Test connection** (sends a fixed
   one-line prompt, no code), **Delete key** (also clears the acceptance).

## Level 0 decisions → where they live in code

| Decision | Enforced by | Pinned by |
|---|---|---|
| D1 read-only | `AiPanel` has no apply/write/run control; `ui/ai` calls no file/run API | `AiHelperWiringTest` |
| D2 Gemini BYOK + free-tier note | `AiCopy.FREE_TIER_NOTE` at setup and in every preview | `AiPolicyTest` |
| D3 Keystore AES-GCM, no plaintext | `AiKeyStore` (`noBackupFilesDir/ai`, `AndroidKeyStore`, decrypt failure ⇒ delete) | `AiHelperWiringTest`, `AiAnswerTest` (blob format) |
| D4 preview every request | `AiViewModel.send()` only from `PREVIEW`; "Try again" returns to PREVIEW | `AiHelperWiringTest` |
| D5 projects only | `AiGate.availability(EditorOpenMode, …)` — only `PROJECT` | `AiPolicyTest`, `AiHelperWiringTest` |
| D6 nothing saved, `store:false` | `AiUiState` in memory; cleared on project/mode change; every body ends `"store":false` | `GeminiRequestTest`, `AiHelperWiringTest` |
| O1 18+/terms gate | `AiKeySetup.canSave`, `TERMS_VERSION`, acceptance deleted with the key | `AiPolicyTest` |
| O2 wire design | `GeminiRequest` (key in `x-goog-api-key` only), `GeminiClient` (`HttpURLConnection`, cancel = disconnect), fixed error texts, `AIza…` redaction | `GeminiRequestTest`, `AiAnswerTest`, `FeedbackDraftTest` |
| O3 Flash default + Test | `AiModel.DEFAULT`, `GeminiRequest.testBody()` | `AiPolicyTest`, `GeminiRequestTest` |

## Parts

| Part | Doc | Scope |
|---|---|---|
| 76.1 | [PART_76_1_PURE_CORE.md](PART_76_1_PURE_CORE.md) | gate, context builder, request/SSE/response, answer, errors, copy |
| 76.2 | [PART_76_2_KEY_STORAGE_AND_REDACTION.md](PART_76_2_KEY_STORAGE_AND_REDACTION.md) | Keystore key store, acceptance, feedback redaction |
| 76.3 | [PART_76_3_PANEL_AND_RAIL.md](PART_76_3_PANEL_AND_RAIL.md) | client, view model, panel, rail slot, editor wiring |

Device checks: [DEVICE_ROUND.md](DEVICE_ROUND.md).

## Evidence (current `main` @ `1785b92`, before the change)

- `ui/editor/SidePanelPlan.kt` — `RailPanel.RESERVED` was drawn, dimmed, not tappable; `WIRED` excluded it.
- `ui/components/EditorSidePanel.kt` — `RailPanel.RESERVED -> Unit`; icon `AutoFixHigh` (kept).
- `ui/screens/EditorScreen.kt` — `openMode` (`EditorOpenMode`) decides chrome; SINGLE_FILE keeps `projectName` set as its save/run root, so the AI gate must key on the mode, not the name.
- `ui/viewmodels/EditorViewModel.kt` — `codeText: StateFlow<TextFieldValue>` (selection), `isDirty`, `outputState` (`phase`, `lines`, `buildExitCode`, `runExitCode`), `diagnostics`.
- `ui/projects/GitHubPublishApi.kt` / `ui/services/ReleaseFetch.kt` — house network style: `HttpURLConnection`, explicit timeouts, `org.json`.
- `ui/support/FeedbackDraft.kt` — token-shape redaction table (no Google key shape before this phase).
- `app/build.gradle.kts` — minSdk 24 (Keystore AES-GCM needs 23); no OkHttp; INTERNET already declared.

## Risks

| Risk | Mitigation |
|---|---|
| `alt=sse` framing differs from the docs | Splitter follows the SSE spec (multi-line data, comments, final event without blank line); device round row checks a real stream |
| Keystore broken on some OEMs | Any failure ⇒ key deleted, "enter it again" — never plaintext |
| A context containing ``` breaks the fence | Cosmetic for the model; the preview shows it verbatim (Deferred) |
| Model id changes upstream | Editable model + Test connection; 404 ⇒ fixed "model not found" message |
| Thinking models spend output budget | `maxOutputTokens` 8192; `thought` parts skipped; MAX_TOKENS ⇒ "cut short" note |

## Implementation (2026-09-30)

- Code commit `424e327`: `ui/ai/*` (12 files), rail slot, editor wiring, `AIza` shape redaction, 6 new test files + side-panel pins moved.
- **CI round 1 — `36755116931` 🔴 red for-cause:** everything compiled; 2599 host tests ran, **1 failed** — `TypeAdoptionTest › hardcoded platform monospace survives only in the font-setting maps`. The preview box used `FontFamily.Monospace`; the house law (Phase 50 type system) is that code text uses the bundled JetBrains Mono via `CodecType.codeFamily`. Fixed in `AiPanel.SentText`. Lesson: grep `TypeAdoptionTest`/`TokenAdoptionTest` scopes before styling new text.
- Same follow-up commit: the stored-key **literal** scrub (`FeedbackInput.extraSecrets`, loaded in `FeedbackSectionCard`) — the Level 0 record §4.2 requires both shape and literal; an earlier draft of this phase had dropped the literal half, caught against the record before the docs commit.
- **CI round 2 — `36755705695` ✅ GREEN** on `813dfd1` (assemble + unit tests + lint, 8m 23s). Artifacts: `CodeC-IDE-release` 6,372,115 B (+79,588 B vs the Phase 75.3 build's 6,292,527 B), `CodeC-IDE-debug` 25,628,418 B.
- Device round: [DEVICE_ROUND.md](DEVICE_ROUND.md).

## Device round 1 (2026-10-01) — ✅ passed, one change

Owner, on the run `36755705695` build: ***"Every test passed just i have to use gemini-3-flash-preview this model"***.
All rows A1–D5 passed. The pre-filled `gemini-3.8-flash` (taken from Google's docs at briefing time) did not work for his key; `gemini-3-flash-preview` did. Fix: `AiModel.DEFAULT = "gemini-3-flash-preview"`, pinned in `AiPolicyTest` and `GeminiRequestTest`; the `MODEL_INVALID` example text follows it. A model already saved on a phone is kept (the default only pre-fills a fresh setup). Lesson: a model id is a device fact, not a docs fact — O3's "editable model + Test connection" is what made this a one-line fix.
