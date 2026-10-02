# CodeC Phase 82.1 — one visible, cancelable rate-limit retry

> **Status:** 🚧 IMPLEMENTED · push/CI BLOCKED (GitHub connection); device pending · **Cost:** `[client-only]` · **Effort:** M
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

AiRateLimitTest **18**, AiRetryTest **11**, AiRateLimitWiringTest **8**, AiHttpStreamTest **13**. Local total **462/462** across 38 classes; same-request bytes, disconnect-before-countdown, blocked read + Stop, partial-stream refusal and deadline fixtures pass. Valid obsolete dates expand their century against the receiving clock (21/44/50-year fixture) rather than falling back to a short delay. Real CI blocked by GitHub auth (HTTP 401); UI timing/device rows not run.

## Sources (record)

1. [Google troubleshooting / retry strategy](https://ai.google.dev/gemini-api/docs/troubleshooting#retry-strategy), fetched this chat 2026-10-02: transient RESOURCE_EXHAUSTED, bounded backoff.
2. [Google.Rpc.RetryInfo](https://cloud.google.com/dotnet/docs/reference/Google.Api.CommonProtos/latest/Google.Rpc.RetryInfo), fetched this chat 2026-10-02: wait at least retry_delay before resending.
3. [MDN Retry-After](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Retry-After), fetched completely this chat 2026-10-02: HTTP-date or nonnegative integer seconds. RFC 9110 §10.2.3 is the underlying standard; only its navigation/introduction was fetched here, not the section text.
4. Repo dossier above; external allowance numbers are not promises.

## Deferred / rejected with reasons

No retry library/new dependency (one bounded policy fits the existing coroutine/HTTP seams); no unlimited exponential loop; no 5xx/offline automatic replay; no silent provider switch. Do not show provider messages to explain a quota. No daily timer persisted across process death.

## Delivery gate — 2026-10-02

Implementation is committed locally (initial code/docs **9285818**). Push failed on authentication; GitHub user API **401** confirmed the connection problem. **Reconnect GitHub in Arena**, then push this same session branch and run Build APK. No phase CI/APK, PR, merge or device acceptance exists yet. [Live ledger](README.md#ci--owner-handoff).
