# Phase 83 / Level 6 — baseline ledger

**Code baseline:** `main` @ `741956647933562dd5056972e71473581dd21233` (2026-10-03). PR #108 changed docs only; the AI source remains the audited `4cb4151` code.
**Method:** owner-supplied screenshots + deterministic offline host replay through the existing reader/tool/loop. The original screenshots are not in Git. The replay creates only fake fixture data, builds one hypothetical request body, and makes **no network call**.
**Status:** structural baseline recorded. This is not a provider-quality benchmark, device acceptance, or claim that Level 6 fixed a defect.

## 1. Owner-observed task (not a controlled benchmark)

The screenshots show NVIDIA Build / `nvidia/nemotron-3-super-120b-a12b` and the task “can you Explain full main.js line by line and think i don't have much code knowledge”. The current source audit confirms the app clips a result to a 1,200-character timeline detail before repacking it into the next model request.

| Signal | Observation | Measurement limit |
|---|---|---|
| Task success | **0/1** satisfactory beginner-friendly, complete-file explanation demonstrated | One reported task; not a model success-rate estimate. |
| Steerability | **Not measured** | No controlled mid-task redirect or user-steering transcript is available. |
| Retries | **Not captured** | No request/retry trace is visible; no provider call was made for this baseline. |
| Turns / runs | **9/12 steps · 0/2 runs** displayed | The screenshot does not give task wall time or say that two runs were attempted. |
| Tool counter | **49/24 reads** displayed | “49” is not 49 successful file reads: current accounting mixes executed, refused, and malformed attempts and does not clamp. |
| Repeated context | The same `js/main.js` prefix (lines 1–22) is requested repeatedly; a prior visible request is lines 1–76 | Exact duplicate count and distinct-line coverage are not recoverable from the screenshots. |
| Approximate line coverage | About **22 lines** are visible to the model after the 1,200-character clip, versus the 400-line read cap: **~5.5%** of that cap | Source-derived estimate matching the screenshot, not a byte-for-byte capture of a request. |
| Tool hallucinations / protocol errors | At least one malformed-block error and one refusal for placeholder `relative/path.ext` | Screenshot does not provide a denominator or exact hallucination rate. The refusal is fail-closed behavior, not an executed path. |
| First token / total latency / wall clock | **Not captured** | No timers or provider transcript in the screenshot. |
| Request bytes / tokens / cost | **Not captured** | The UI did not show payload bytes or provider usage. |
| Peak memory | **Not captured** | No phone profiler sample. |
| APK delta | Production-source delta is **0 files** for Level 6; APK byte delta **not measured** | CI artifacts are not available to this host-shim measurement. Test sources are not packaged in the APK. |

Source of observations and caveats: [`AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md`](../../../research/AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md#2-phone-evidence--observed-behavior-not-an-acceptance-result).

## 2. Deterministic normal-file replay

Each project contains a 300-line normal source. A synthetic `read_file` request asks for lines 1–400; the current runner caps the result at 8,000 characters; the current ViewModel boundary then applies `.take(1_200)` before `AiAgentPrompt.pack`. The test counts numbered lines that actually enter the packed next request and builds, but does not send, the exact-shaped NVIDIA JSON body using `NvidiaRequest.body`.

| Fixture | File lines | Requested range | Numbered lines in next model request | Share of 400-line cap | Runner result chars | Timeline/model chars | `CUT_NOTE` after final clip? | Hypothetical JSON body bytes |
|---|---:|---|---:|---:|---:|---:|---|---:|
| C | 300 | 1–400 | **22** | 5.50% | 8,000 | 1,200 | **No** | **2,608** |
| Python | 300 | 1–400 | **21** | 5.25% | 8,000 | 1,200 | **No** | **2,615** |
| JavaScript | 300 | 1–400 | **19** | 4.75% | 8,000 | 1,200 | **No** | **2,617** |
| HTML | 300 | 1–400 | **19** | 4.75% | 8,000 | 1,200 | **No** | **2,620** |

For these 300-line files, the delivered coverage of the *existing file* is 22/300 (7.33%), 21/300 (7.00%), 19/300 (6.33%), and 19/300 (6.33%) respectively. Those percentages are about input lines only; they do not score answer correctness. The result marker survives `AiToolRunner.clip` and is absent from the next request after the UI-sized clip.

Request-body bytes include the existing system instruction, packed user text, JSON escaping, model id, and fixed output/stream fields. They exclude HTTP headers (including the key), response bytes, and any provider call. The values are reproducible from the fixture code and request builder; CI runs the same assertions against the actual app classes.

## 3. Large-file range replay

Each generated file has 2,000 source lines and a unique end marker. The test requests lines 1,900–2,000. The reader can only supply the first 24,000 characters, so the result is clamped to the end of that prefix and does not reach the requested range or final marker.

| Fixture | Actual file lines | Requested | Declared result range from the prefix | End marker reached? |
|---|---:|---|---|---|
| C | 2,000 | 1,900–2,000 | **304–304 of 304** | No |
| Python | 2,000 | 1,900–2,000 | **290–290 of 290** | No |
| JavaScript | 2,000 | 1,900–2,000 | **273–273 of 273** | No |
| HTML | 2,000 | 1,900–2,000 | **273–273 of 273** | No |

The `of 304 / 290 / 273` totals describe the decoded prefix, not the actual 2,000-line file. The current outcome has no coverage field that exposes this mismatch.

## 4. Repeated-call and budget replay

A deterministic answer contains **49 identical, syntactically valid `read_file` blocks** for one admitted JavaScript source path. The existing parser/policy/runner produce:

| Count | Result |
|---|---:|
| Requests in the scripted answer | 49 |
| Executable calls admitted by the 24-call task cap | 24 |
| Budget refusals | 25 |
| Repeated requests after the first request | 48 |
| Repeated executions after the first execution | 23 |
| Distinct returned result texts among the 24 executions | 1 |
| Mixed tool counter after adding executed + refused | **49**, shown as **49 of 24 reads** |

This replay proves the accounting/repetition shape in CodeC's current policy; it does **not** claim a model emitted 49 blocks in one turn in the phone session. The phone report's exact total duplicate count remains unknown. HTTP retry behavior is a separate Phase 82 mechanism and is not what “repeated read” means here.

## 5. Fixture and suite inventory

`AiLevel6FixtureSupport` creates C, Python, JavaScript, and HTML projects in a temporary directory. Every project contains the normal / large / minified / binary / fake `.env` / escaping-symlink / injection-text cases. The current AI project walk admits the source, large, minified, and injection-text paths, and omits the fake `.env`, binary, and symlink. The injection fixture is only read as text in a local test; it is never submitted to a model.

| Inventory | Before Level 6 | After Level 6 |
|---|---:|---:|
| Kotlin test files under `app/src/test` | 310 | 312 (2 new test-support files) |
| `@Test` annotations across app tests | 2,968 | 2,973 (+5) |
| `Ai*.kt` test classes / tests | 30 / 386 | 31 / 391 (+1 class, +5) |
| Production-source files changed | 0 | 0 |

## 6. Metrics intentionally left unmeasured

No credentialed model run or phone profiler was authorized or performed in Level 6. Therefore **first-token latency, end-to-end provider wall clock, provider token usage/cost, phone peak memory, model task-success/steerability rates, and byte-for-byte APK delta remain unmeasured**. The 9/12 and 0/2 screenshot counters are recorded as shown, not used as latency or run-attempt counts. These gaps must remain visible in later comparisons; do not fill them with host-JVM timings, model-card claims, or inferred APK sizes.
