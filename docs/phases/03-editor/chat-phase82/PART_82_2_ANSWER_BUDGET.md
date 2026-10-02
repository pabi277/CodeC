# CodeC Phase 82.2 — a larger answer, still bounded across Continue

> **Status:** 🚧 IMPLEMENTED · pushed; CI fixture fix / rerun pending; device NOT RUN · **Cost:** `[client-only / possible additional BYOK tokens]` · **Effort:** S
> **Owner row (verbatim):** "Api rate limit"; dossier separates quota rejection from local answer truncation.

## First move: evidence, not code

Baseline `e089880`, read 2026-10-02: AiPolicy.kt:32,40 cap a reply at 24,000 characters and 8,192 output tokens; GeminiRequest.body uses that token cap. Test connection has a separate 256-token budget (`GeminiRequest.kt:60`). AiContinuation.kt:43,70 bounds the combined answer at 64,000 and computes min(reply cap, remaining).

## Design

Owner selected **32,768 output tokens / 48,000 reply characters on every request**. Keep **64,000 total characters**, **8 continuations**, 1,400-char tail and ≥600 remaining chars to offer Continue. Known model ceilings constrain a request rather than being silently exceeded; unknown capability rows disclose that they are unknown.

| After a full chunk | Combined answer | Next maximum |
|---|---:|---:|
| First reply | 48,000 | 16,000 |
| Continuation | 64,000 | No Continue; fixed limit note |

The accumulator still names both finishReason=MAX_TOKENS and a local cap as cutShort. NVIDIA's finish_reason=length maps to the same contract. A retry shares the **same** remaining budget; it does not append duplicate text or spend a second Continue tap.

## The Android edge

No new Continue network path or autonomous continuation. Tapping Continue still builds the exact two-string preview, then Send appends under the earlier text. Agent tasks/edit proposals retain Phase 81's explanation instead of Continue. Disclosure states the effective token/reply caps and that longer answers may consume more quota/time. The content-free connection test uses the requested cap too (owner every_request), while its prompt still asks for one word only.

## Exit condition

Tests pin exact cap numbers, ceiling clamp, accumulator boundary, 48,000 + 16,000 total arithmetic, no offer after 64,000, and D4's same two strings for both providers. No input context/tool/run/wall-clock caps changed.

## Tests (implemented)

AiAnswerBudgetTest **6**, NvidiaRequestTest **7**, plus provider fixtures; AiContinuationTest/AiAnswerTest/GeminiRequestTest/AiContinueWiringTest re-run **unchanged**. Exact output/remaining caps pass in the **462/462** host suite; real CI and long-answer phone checks pending.

## Sources (record)

1. Repo Phase 81 README/part/device round, read this chat 2026-10-02.
2. NVIDIA_API_RESEARCH_20261002.md §1: Gemini model ceiling 65,536 (prior-session model-card research, not newly fetched here).
3. [NVIDIA Nemotron 3 Super request reference](https://docs.api.nvidia.com/nim/reference/nvidia-nemotron-3-super-120b-a12b-infer), fetched this chat 2026-10-02: max_tokens 1–32,768.

## Deferred / rejected with reasons

96,000 total rejected by the owner's chosen option (64,000 retained). Agent Continue, dynamic context expansion and unlimited output remain out of scope. A larger cap is not a claim of larger free-tier allowance or better model quality.

## Delivery gate — 2026-10-02

Owner reconnected GitHub; push of **7921c01** on `arena/01a0fbc2-codec` succeeded. [Build APK round 1](https://github.com/pabi277/CodeC/actions/runs/36994479128) failed at `:app:compileDebugUnitTestKotlin`: `AiHttpStreamTest.kt:180` used JUnit `fail()` in a connection-opener lambda, but real JUnit returns **Unit**, not HttpURLConnection. The fixture now explicitly throws AssertionError (same strict no-network check, no assertion relaxed); the local JUnit shim also now declares Unit. Corrected host **462/462** + privacy/backup **13/13**, compiler exit 0 and transcript has no `error:`. Rerun pending after this code/docs push. No APK/device acceptance yet; no PR or merge. [Live ledger](README.md#ci--owner-handoff).
