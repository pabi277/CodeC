# CodeC Phase 82.2 — a larger answer, still bounded across Continue

> **Status:** MERGED via PR #107 · main CI ✅ GREEN · device acceptance POSTPONED · **Cost:** `[client-only / possible additional BYOK tokens]` · **Effort:** S
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

AiAnswerBudgetTest **6**, NvidiaRequestTest **7**, plus provider fixtures; AiContinuationTest/AiAnswerTest/GeminiRequestTest/AiContinueWiringTest re-run **unchanged**. Exact output/remaining caps pass in the **462/462** host suite; real CI **36995145462 green**; long-answer phone checks still pending.

## Sources (record)

1. Repo Phase 81 README/part/device round, read this chat 2026-10-02.
2. NVIDIA_API_RESEARCH_20261002.md §1: Gemini model ceiling 65,536 (prior-session model-card research, not newly fetched here).
3. [NVIDIA Nemotron 3 Super request reference](https://docs.api.nvidia.com/nim/reference/nvidia-nemotron-3-super-120b-a12b-infer), fetched this chat 2026-10-02: max_tokens 1–32,768.

## Deferred / rejected with reasons

96,000 total rejected by the owner's chosen option (64,000 retained). Agent Continue, dynamic context expansion and unlimited output remain out of scope. A larger cap is not a claim of larger free-tier allowance or better model quality.

## Delivery gate — 2026-10-02

Owner reconnected GitHub; session branch pushed. [Build APK round 2 **36995145462**](https://github.com/pabi277/CodeC/actions/runs/36995145462) is **✅ GREEN** on **7b2ecac**: real unit/screenshot tests, debug build + lint, measured/signed release build, non-debuggable/ABI artifact checks. Signed release **7,117,440 B**, debug **26,869,612 B**; release is +13,128 B (~12.8 KiB, +0.18%) over merged Phase 81. [Device round](DEVICE_ROUND.md) handed over, now **POSTPONED until after optimization**; real vendor availability/model quality/Keystore success are not CI claims. The initial STOP handoff was superseded by the owner follow-up below: **formal acceptance postponed; current merge authorized**. [Live ledger](README.md#ci--owner-handoff).

Round 1 **36994479128** on **7921c01** failed at AiHttpStreamTest:180 because real JUnit fail() returns Unit, not HttpURLConnection. The fake opener now throws AssertionError explicitly (same strict no-network test), and the local shim now also returns Unit. Core **462/462** + privacy/backup **13/13** revalidated, compiler exit 0 / no error:; no assertion, permission, dependency or workflow change.

## Owner follow-up — research, deferred device acceptance and merge authority

Owner (2026-10-02): **“What ever we discussed add a research note in the project”**, **“The device test is postponed, 1st i will make it optimized than device test”**, **“Last merge it”**. This supersedes the initial pending-device/STOP handoff: document the discussion and merge the current work after final-head CI, with **formal device acceptance POSTPONED, not passed**.

[Agent-core optimization research](../../../research/AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md) records the informal screenshot issues, the 1,200-character preview/context coupling, prefix-only reads, last-four-result memory, duplicate/counter/final-answer problems, full-file/batch/native-tool/Markdown proposals and multi-API tradeoffs. No app/test/default-model changes are made here; GLM-5.3 remains manually selectable. Known agent issues remain open, not fixed by this record. **Optimization implementation, Level 5B and Levels 6+ are not started.** D1/D4/D5/D6 and the current caps/three stream sites stay unchanged.

Existing code CI **36995145462** on **7b2ecac** and ledger CI **36996946243** on **e0665cb** are green. Authorized PR/merge and main CI/APK facts will be recorded after they actually happen, not predicted. This owner command changes this delivery's scheduling/merge gate, not the standing §3 rule. [Deferred device matrix](DEVICE_ROUND.md).

## Merge / post-merge CI — PR #107

The owner explicitly commanded **“Last merge it”** while postponing formal device acceptance until after optimization. Both final-head checks passed on **268ed24e2123cd7a55aa6f535bbff594d5004407**: [push **37005368058**](https://github.com/pabi277/CodeC/actions/runs/37005368058) and [PR **37005372003**](https://github.com/pabi277/CodeC/actions/runs/37005372003). The guarded merge used that exact head; old PRs #42/#83 were not touched.

- **[PR #107](https://github.com/pabi277/CodeC/pull/107): MERGED** at **`4cb4151f1a59f9b719cc9bd64224ea9040c1a022`** on **2026-10-02**.
- **Main [Build APK 37006780729](https://github.com/pabi277/CodeC/actions/runs/37006780729): GREEN** on that merge SHA; real unit/screenshot tests, debug/lint, signed release and APK guards passed.
- **Release:** `CodeC-IDE-1.3.17-universal.apk` = **7,117,440 B**; **debug:** `CodeC-IDE-1.3.17-universal-debug.apk` = **26,869,596 B**. APK bytes are annotations, not compressed artifact ZIP sizes. Release manifest has no android:debuggable flag; no failure annotation, no release/tag published.
- Delta over Phase 81/main baseline **7,104,312 B**: **+13,128 B** (~12.8 KiB / 0.18%). Different build artifacts have their own measured sizes; earlier branch facts remain historical.
- **Formal device acceptance: POSTPONED until after optimization, NOT PASSED.** Informal Nemotron screenshot issues remain open; successful Keystore/live GLM access/model quality not inferred.
- **Research only:** full-file/batch/context/loop/native-tool/Markdown/multi-API proposals are not implemented; no default-model change, automatic provider fallback or new tool permission.
- Post-merge verification-ledger-only record is committed/pushed on **`arena/01a0fbc2-codec`**, not to main. Next: agree/start optimization, then refresh/run the postponed owner device matrix. Standing §3 still requires the next change's explicit merge command.
