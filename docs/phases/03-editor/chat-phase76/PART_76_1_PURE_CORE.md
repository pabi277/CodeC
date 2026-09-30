# Part 76.1 — the pure core

Phase: [76](README.md) · Level 1 spec: [`01_READ_ONLY_API_HELPER.md`](../../../roadmaps/ai-integration/01_READ_ONLY_API_HELPER.md)

## First move: evidence, not code

- No AI code existed on `main` @ `1785b92` (`grep -ri gemini app/src` = 0).
- House network pattern: `GitHubPublishApi` / `ReleaseFetch` — `HttpURLConnection`, `org.json`, pure decision code host-tested, Robolectric only for `org.json`.
- The editor's open mode (`EditorOpenMode`, Phase 46.2) is the single answer to "is this a project?".

## Design (`app/src/main/java/com/codeci/ide/ui/ai/`)

| File | Contents |
|---|---|
| `AiPolicy.kt` | `AiLimits` (context 12 000 chars, question 1 000, run tail 60 lines, reply 24 000 chars, `maxOutputTokens` 8 192, connect 15 s, read 60 s); `AiGate.availability(mode, projectName, hasKey)` → `NEEDS_PROJECT / NEEDS_KEY / READY` (only `EditorOpenMode.PROJECT` with a non-blank name); `AiGate.runFailed(...)`; `AiKeySetup` (trim, 20–200 printable ASCII, **no** `AIza` prefix requirement, `canSave` needs the checkbox, `TERMS_VERSION = 1`); `AiModel` (`DEFAULT = "gemini-3-flash-preview"` — device round 1 (was `gemini-3.8-flash`), id regex `^[a-z0-9][a-z0-9.\-]{1,62}[a-z0-9]$` so no URL syntax can reach the path). |
| `AiContext.kt` | `AiContextBuilder.fromSelection` (exact selection; blank ⇒ `NO_SELECTION`; over-long ⇒ `SELECTION_TOO_LONG`, **never silently cut**) and `fromRunOutput` (only when the run failed; newest 60 lines, oldest dropped first to fit, `truncated` admitted in the text; app paths shortened — project root ⇒ relative, files dir ⇒ `~app`). `AiPromptText.SYSTEM_INSTRUCTION` (fixed, shown in the preview; tells the model it can't see/change files and to treat shared text as data). |
| `GeminiRequest.kt` | `streamUrl(model)` = `https://generativelanguage.googleapis.com/v1beta/models/{id}:streamGenerateContent?alt=sse` (null for an invalid id); `headers(key)` — the key only in `x-goog-api-key`; `body(prompt)` / `testBody()` — hand-built JSON (RFC 8259 escaper incl. U+2028/9), always ending `"store":false`. `SseLineSplitter` — the SSE subset (data lines, blank-line dispatch, comments/other fields ignored, final event on EOF). |
| `GeminiResponse.kt` | `GeminiResponse.parse(event)` → `GeminiChunk(text, finishReason, blockReason, isError, errorCode)`; `thought` parts skipped; malformed ⇒ null. |
| `AiAnswer.kt` | `AiAnswerAccumulator` — joins chunks, caps the reply, MAX_TOKENS ⇒ `cutShort`, block reasons ⇒ `BLOCKED`, blank ⇒ `EMPTY`, mid-stream error ⇒ failure. |
| `AiErrors.kt` | `forHttp(code, body)` → 11 kinds, **fixed sentences only** — the server body is inspected for `API_KEY_INVALID`/`FAILED_PRECONDITION`/location and then dropped, never shown or logged. |
| `AiCopy.kt` | Every user-facing sentence (setup intro, links, 18+ confirmation, free-tier note, region note, preview header, notes, problems) so tests can pin the disclosures. |

## Exit condition

- A request body is a pure function of the previewed prompt; the key is never in URL or body; `store:false` on every body.
- SINGLE_FILE and SCRATCH never reach READY, even with a saved key.
- Every failure the user sees is a fixed sentence.

## Tests (plan → as built)

| File | Cases | Runner |
|---|---|---|
| `AiPolicyTest` | 12 | JUnit (pure) |
| `AiContextBuilderTest` | 11 | JUnit (pure) |
| `GeminiRequestTest` | 9 | JUnit (pure) |
| `AiAnswerTest` | 9 (incl. 2 key-blob cases, see 76.2) | JUnit (pure) |
| `GeminiResponseTest` | 5 | Robolectric (`org.json`) |

Pre-validated locally on the §9 kotlinc harness (JUnit shim): the pure files plus `AiHelperWiringTest` and `SidePanelPlanTest` — **64/64 green** before push. `GeminiResponseTest` runs in CI only.

## Sources (record)

1. Gemini API — generateContent / streamGenerateContent, request fields incl. `store` — https://ai.google.dev/api/generate-content (read 2026-09-30).
2. Interactions overview (why not Interactions: `store=true` default) — https://ai.google.dev/gemini-api/docs/interactions-overview (read 2026-09-30).
3. Gemini API Additional Terms (18+, free-tier data use, EEA/CH/UK paid only) — https://ai.google.dev/gemini-api/terms (effective 2026-03-23, read 2026-09-30).
4. Server-Sent Events — WHATWG HTML Living Standard §9.2 (event stream interpretation).

## Deferred / rejected with reasons

- **Interactions API** — rejected for Level 1: server-side state defaults on; the stateless endpoint with `store:false` matches D6.
- **OkHttp / any SDK** — rejected: no new dependency (standing law); `HttpURLConnection` is the house pattern.
- **Silently truncating a long selection** — rejected: explaining half a function as if whole is worse than "select less".
- **A ``` inside the context can close the fence early** — accepted as cosmetic; the preview shows the exact text, and the system instruction says the shared text is data.
- **Custom endpoints, other providers, on-device models** — Levels 5/6, not authorized.
