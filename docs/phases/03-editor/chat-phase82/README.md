# Phase 82 — CodeC AI: rate-limit resilience and a bigger answer budget

> **Status:** 🚧 IMPLEMENTED · push/CI BLOCKED (GitHub connection); device pending (2026-10-02) · **Cost:** `[client-only / BYOK]` · **Effort:** M
> **Owner row (verbatim):** "Api rate limit".
> Work only on `arena/01a0fbc2-codec`; baseline `main` @ `e089880` (PR #106).
> No PR, merge, or push to `main` without the owner's explicit command (`rule.md` §3).

## Read-first evidence (2026-10-02, baseline `e089880`)

| Symptom / contract | Evidence |
|---|---|
| 429 is a dead end | `ui/ai/GeminiClient.kt:62–68` reads responseCode and maps the capped error body, but never reads Retry-After; `AiErrors.kt:40` supplies one fixed quota sentence. There is no retry or countdown. |
| Answers stop too soon | `AiPolicy.kt:32,40`: 24,000 reply characters / 8,192 requested output tokens. These are different from provider quota failures. |
| Continue has a separate total | `AiContinuation.kt:43–71`: 64,000 total characters, ≤ 8 continuations, each bounded by min(reply cap, remaining). |
| Exactly three network entry points | `AiViewModel.kt:770,954,1147`: agentTurn, send, Test connection. AiHelper/Level2/Level3/Surface/Continue wiring pins all require three. |
| One provider and encrypted slot | `AiViewModel.kt:114`; `AiKeyStore.kt:32–36,160–168`: Gemini client and AES-GCM key slot; deleting the key also clears the one-task undo journal. |
| Payload disclosure | `AiChatSheet.kt:303–340`: exact instruction and user text on the preview; the provider/model must become immutable request data, not a setting re-read during retry. |

Research input: [`NVIDIA_API_RESEARCH_20261002.md`](../../../research/NVIDIA_API_RESEARCH_20261002.md). The previous phases' laws remain binding: D1 Apply only through AiEditApplier; D4 preview the exact two strings (Phase 80's task-preview amendment); D5 AI's own filters; D6 conversation/task state in memory only, one-task undo journal unchanged.

## Owner choices, asked before code

All four were selected through ask_user in this chat (2026-10-02):

| Choice | Owner answer |
|---|---|
| Automatic vs manual retry | **automatic_once** — one automatic resend of the identical approved request, visible cancelable countdown; Stop cancels it. |
| Answer budgets | **32768_48000_64000** — 32,768 output tokens / 48,000 reply characters; Continue total stays 64,000 (a full first reply leaves 16,000). |
| Scope of raised caps | **every_request** — includes selection/error helpers, agent/edit requests, and the content-free connection test. |
| Optional Phase 82B | **authorize_82b** — explicitly start Level **5A**, provider seam + NVIDIA BYOK, development/testing-only terms checkbox, capabilities, Test connection, no silent switching. **5B and Levels 6+ are not authorized.** |

## Parts and order

1. [82.1 Fixed error classification, retry metadata, one retry, countdown and Stop](PART_82_1_RATE_LIMIT_RESILIENCE.md).
2. [82.2 Larger per-request budgets, bounded Continue](PART_82_2_ANSWER_BUDGET.md).
3. [82B / 82.3 Level 5A provider seam and NVIDIA development/testing BYOK](PART_82_3_PROVIDER_SEAM_AND_NVIDIA_BYOK.md).
4. [Device round](DEVICE_ROUND.md) — pending, never inferred from host tests or CI.

Implementation decisions (agent, not additional owner answers): known daily exhaustion without a reset delay is not repaired by a short retry; server waits beyond a bounded automatic window are explained, never shortened. No replay after any visible partial text. Retrying a rejected agent request does not grant another tool/run budget and remains inside the task wall clock. Provider/model changes are explicit, cancel no hidden task, and require a fresh preview. The current provider is memory-only; after process death the setup defaults to Gemini and every request still names its recipient before Send.

## Carry-forward

`git cherry-pick -x 5a1dff0` completed as **`828be38`**, carrying the Phase 80/81 merge record: PR #106 merged @ `e089880`, post-merge CI `36972776771` green, release APK `7,104,312 B`. Old PRs #42 and #83 are untouched.

## Exit condition / verification ledger

- [x] State verified, required docs read, owner choices recorded before code.
- [x] Phase 80/81 post-merge record carried forward.
- [x] Pure policies and transport/provider fixtures implemented, existing safety pins unchanged.
- [x] Local kotlinc/JRE prevalidation **462/462** across **38 classes**, compile exit 0 and no `error:` in transcript (a helper's "compiled" is not proof).
- [x] Code and living docs committed together: initial implementation **9285818**, with local date-parser follow-up recorded below.
- [ ] Session branch pushed — **BLOCKED**: Git push authentication error; authenticated GitHub user request **HTTP 401**. Owner must reconnect GitHub in Arena.
- [ ] Build APK CI green (executor of record: Gradle/JUnit/Robolectric/lint/APKs).
- [ ] Device round handed to owner; no device acceptance claimed.
- [ ] Stop at merge gate. No PR opened.

## Local prevalidation — 2026-10-02

**107 new checks / 12 new test classes**, plus **355 unchanged existing checks**. Total: **462 passed / 0 failed**, 38 classes. Scratch JRE from `jdk4py` 25.0.2.1 and npm `kotlin-compiler` 2.4.20; **`-jvm-target 17`**, matching CI's target. `/tmp/codec-phase82/prevalidate.sh` compiles the actual AI core, VM/store and pure project/apply/undo dependencies; JUnit/Android/lifecycle/org.json have thin host shims, and relevant pure Git data/redactor fragments are extracted from their real source. JSON/Android behavior and Compose still require real CI; successful Keystore encryption/slot survival still require the device. Slot tests intentionally use corrupt blobs, not fake crypto success. Compile transcript inspected for **`error:`** (none), compiler exit **0**, fresh jar required before reflection runs. No local Gradle build or credentialed vendor request.

| New class | Checks | Covered |
|---|---:|---|
| AiRateLimitTest | 18 | Numeric/date/obsolete date with receiving-clock century expansion, RetryInfo rounding, maximum minimum, bad/huge delays, minute/day/unknown, error precedence/privacy |
| AiRetryTest | 11 | At most two attempts, ticks, zero delay, deadline, cancellation, initial/late partial output, same captured request |
| AiHttpStreamTest | 13 | Real adapters + fake connections: Retry-After, identical bytes/headers, SSE/DONE, remaining cap, streamed error, bounded error body, disconnect-before-countdown, blocked-read cancellation, malformed/async/redirect/IO/model failures |
| AiRateLimitWiringTest | 8 | Three original sites/private helper, Stop/reset, both countdown surfaces, deadline, no persistence, busy test/Send, no pending polling |
| AiAnswerBudgetTest | 6 | Exact global caps, 48,000 + 16,000 arithmetic, boundary accumulation, every request/test, unchanged tool/input caps |
| AiProvidersTest | 12 | Provider/model ids, validation, known/unknown capabilities, ceiling clamp, consent/copy, frozen recipient/Continue, REQUEST rows excluded from tool-result packing |
| AiProviderClientTest | 4 | Exhaustive routing, no fallback either way, same recipient/key/budget on retry |
| NvidiaRequestTest | 7 | Fixed HTTPS/Bearer header, exact two strings, selected model, escaping, stateless/test body, 32,768 cap |
| NvidiaResponseTest | 8 | Visible delta only, reasoning/native tools ignored, length/filter/error/usage/DONE, bounded CodeC text-tool fixture |
| AiProviderWiringTest | 9 | Manual/busy selection, immutable recipient, independently enforced terms, encrypted slots, D4 timeline, D6, both feedback literals, no new endpoint/tool privileges |
| AiKeyStoreSlotsTest | 8 | Actual metadata/deletion/decode-refusal code: separate consent/model slots, affected-slot-only deletion, shared undo/layout, cross-instance concurrent writers, failed atomic metadata update |
| NvidiaRedactionTest | 3 | nvapi- shape, both stored literals, log/crash attachments |

A separate supplementary compile/run also passes **13/13** unchanged privacy/backup checks: ManifestPermissionsTest **7**, BackupRulesTest **6**, compiler exit 0 and no `error:`. Both Android backup XMLs and the permission/dependency sets are unchanged. Thus local prevalidation covers **475 passing checks** across the core + supplementary batches, without treating host shims as a device pass.

Existing AI helper/Level2/Level3/Level4/Continue/surface wiring pins are **unchanged**, including count=3; apply/undo/filter/tool/run, feedback, type and motion regressions are in the 355. During prevalidation the compiler found and fixed a duplicate trailing fragment in AiAgentLoop and an inferred coroutine return type; later checks caught one busy-Send source pin and a wrong new test's invalid-model expectation. None were hidden by the helper's "compiled" print. Final credential review also found that concurrent VM/layout/support store instances could race a read-modify-write of shared acceptance properties; one shared monitor and atomic minSdk-safe metadata replacement now preserve both provider slots, verified by actual metadata/deletion fixtures.

## CI / owner handoff

**Push / Build APK: BLOCKED, NOT RUN.** Code + living docs were committed together as **9285818**. `git push origin arena/01a0fbc2-codec` then failed with “could not read Username … terminal prompts disabled”; a safe authenticated identity request returned **HTTP 401 / Bad credentials**, confirming that the Arena GitHub connection needs attention. Owner has been asked to **reconnect GitHub in Arena**; no password/token was requested, no credential was put in chat, and no other branch/push/PR/merge was attempted. Local work is preserved. After reconnect: push this session branch, obtain green Build APK (real Gradle/JUnit/Robolectric/Compose/release checks), then hand the owner the device round. No CI run or APK exists for this phase yet.

A final local date fixture found that SimpleDateFormat's default two-digit year window could ignore a valid long RFC-850 Retry-After and fall back to 12s. The follow-up expands the century against the injected receiving clock before weekday validation; **21/44/exactly-50-year** minima are refused as too long, never shortened. AiRateLimitTest is now **18**, full core **462/462**; supplementary permission/backup **13/13**. Successful cipher/real endpoint/device proof is still pending.

**Device round: NOT RUN**; no live provider availability or quality claim. HTTP 202 is a fixed pending-request failure, no polling; an empty NVIDIA test is not “NVIDIA answered”. Exactly one retry and manual selection only. **No PR opened or merge authorized.**

## Risks / deliberate limits

A larger output budget can cost more tokens, time and quota; it cannot enlarge a provider allowance. Only one retry, no background work, no generic 5xx/offline retry loop. NVIDIA catalogue/limits can change; unknown models have unknown capabilities, not invented numbers. No real vendor key is available in the sandbox: live NVIDIA connection, coding quality and tool-format correctness require the owner's BYOK device round. No custom endpoints, second-model reviewer, autonomous applies/runs, multi-agent work, or on-device inference.
