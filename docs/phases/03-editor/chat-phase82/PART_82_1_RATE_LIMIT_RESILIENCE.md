# CodeC Phase 82.1 — one visible, cancelable rate-limit retry

> **Status:** 🚧 IMPLEMENTED · CI ✅ GREEN · device acceptance POSTPONED · MERGE AUTHORIZED · **Cost:** `[client-only]` · **Effort:** M
> **Owner row (verbatim):** "Api rate limit".

## First move: evidence, not code

Baseline `e089880`, read 2026-10-02: `GeminiClient.kt:62–68` returns Failed immediately for non-2xx; no Retry-After read. `AiErrors.kt:40,59` handles 429 but not RESOURCE_EXHAUSTED, and uses one fixed sentence. The three VM stream sites (`:770,954,1147`) must remain exactly three; no new request from a surface, Continue or project gather.

## Design

Android-free marker and retry policies. AiFailure gains only typed quota-window and numeric delay metadata; provider body/header/exception strings are never retained or displayed. Distinguish explicit per-minute vs per-day quota markers; ambiguous responses stay ambiguous. Error precedence (bad key / restricted region) is preserved.

Parse Retry-After delta-seconds and HTTP-date using minSdk-24-safe java.text/java.util, plus Google's google.rpc.RetryInfo.retryDelay. Round fractional server delays **up**. If both exist, use the later minimum; never retry earlier than the server says. Absent metadata: **12-second** conservative short backoff. The automatic wait ceiling is **120 seconds**; longer server minima are refused rather than clipped. Invalid/negative/overflow headers do not wrap or spin. Daily exhaustion with no reset hint and delays beyond the automatic window fail with fixed, actionable text, not a fake short reset.

One suspend retry executor accepts an attempt, injected suspend wait, countdown publisher, and permission/deadline predicate. At most two attempts. Only rate/quota failures; no replay after partial text; a second rejection is final. Cancellation propagates rather than becoming a displayed network failure.

## The Android edge

A **shared private VM helper** wraps the existing three stream sites (does not introduce a fourth). Request/provider/model/key/body/budget are captured once, and retry sends them unchanged. Countdown lives in plain additive AiUiState entries; its type is declared outside AiUiState (AiHelperWiringTest slice law). Show the quota type when known, “limit hit; retrying in Ns”, and the existing Stop control in chat **and** while testing a connection. Stop/project change/clear/key deletion cancel the wait and clear its UI. The network socket is disconnected on cancellation, and each failed attempt is closed before the countdown.

An agent retry is the same logical turn; it cannot execute tools twice or extend turn/tool/run caps. Check the existing wall clock before retrying. Never parse a rejected reply as a tool proposal.

## Exit condition

Fixtures prove header/marker handling, one retry and unchanged payload, no retry after partial output, second-failure termination, cancellation and agent deadline. All original stream-count and D1/D4/D5/D6 pins remain green. Phone validates countdown visibility and Stop; no daily quota intentionally exhausted for testing.

## Tests (implemented)

AiRateLimitTest **18**, AiRetryTest **11**, AiRateLimitWiringTest **8**, AiHttpStreamTest **13**. Local total **462/462** across 38 classes; same-request bytes, disconnect-before-countdown, blocked read + Stop, partial-stream refusal and deadline fixtures pass. Valid obsolete dates expand their century against the receiving clock (21/44/50-year fixture) rather than falling back to a short delay. CI round 1 exposed a JUnit fixture return-type compile error, fixed without relaxing any assertion. Round 2 **36995145462 green** on **7b2ecac**; UI timing/device rows remain NOT RUN.

## Sources (record)

1. [Google troubleshooting / retry strategy](https://ai.google.dev/gemini-api/docs/troubleshooting#retry-strategy), fetched this chat 2026-10-02: transient RESOURCE_EXHAUSTED, bounded backoff.
2. [Google.Rpc.RetryInfo](https://cloud.google.com/dotnet/docs/reference/Google.Api.CommonProtos/latest/Google.Rpc.RetryInfo), fetched this chat 2026-10-02: wait at least retry_delay before resending.
3. [MDN Retry-After](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Retry-After), fetched completely this chat 2026-10-02: HTTP-date or nonnegative integer seconds. RFC 9110 §10.2.3 is the underlying standard; only its navigation/introduction was fetched here, not the section text.
4. Repo dossier above; external allowance numbers are not promises.

## Deferred / rejected with reasons

No retry library/new dependency (one bounded policy fits the existing coroutine/HTTP seams); no unlimited exponential loop; no 5xx/offline automatic replay; no silent provider switch. Do not show provider messages to explain a quota. No daily timer persisted across process death.

## Delivery gate — 2026-10-02

Owner reconnected GitHub; session branch pushed. [Build APK round 2 **36995145462**](https://github.com/pabi277/CodeC/actions/runs/36995145462) is **✅ GREEN** on **7b2ecac**: real unit/screenshot tests, debug build + lint, measured/signed release build, non-debuggable/ABI artifact checks. Signed release **7,117,440 B**, debug **26,869,612 B**; release is +13,128 B (~12.8 KiB, +0.18%) over merged Phase 81. [Device round](DEVICE_ROUND.md) handed over, now **POSTPONED until after optimization**; real vendor availability/model quality/Keystore success are not CI claims. The initial STOP handoff was superseded by the owner follow-up below: **formal acceptance postponed; current merge authorized**. [Live ledger](README.md#ci--owner-handoff).

Round 1 **36994479128** on **7921c01** failed at AiHttpStreamTest:180 because real JUnit fail() returns Unit, not HttpURLConnection. The fake opener now throws AssertionError explicitly (same strict no-network test), and the local shim now also returns Unit. Core **462/462** + privacy/backup **13/13** revalidated, compiler exit 0 / no error:; no assertion, permission, dependency or workflow change.

## Owner follow-up — research, deferred device acceptance and merge authority

Owner (2026-10-02): **“What ever we discussed add a research note in the project”**, **“The device test is postponed, 1st i will make it optimized than device test”**, **“Last merge it”**. This supersedes the initial pending-device/STOP handoff: document the discussion and merge the current work after final-head CI, with **formal device acceptance POSTPONED, not passed**.

[Agent-core optimization research](../../../research/AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md) records the informal screenshot issues, the 1,200-character preview/context coupling, prefix-only reads, last-four-result memory, duplicate/counter/final-answer problems, full-file/batch/native-tool/Markdown proposals and multi-API tradeoffs. No app/test/default-model changes are made here; GLM-5.3 remains manually selectable. Known agent issues remain open, not fixed by this record. **Optimization implementation, Level 5B and Levels 6+ are not started.** D1/D4/D5/D6 and the current caps/three stream sites stay unchanged.

Existing code CI **36995145462** on **7b2ecac** and ledger CI **36996946243** on **e0665cb** are green. Authorized PR/merge and main CI/APK facts will be recorded after they actually happen, not predicted. This owner command changes this delivery's scheduling/merge gate, not the standing §3 rule. [Deferred device matrix](DEVICE_ROUND.md).
