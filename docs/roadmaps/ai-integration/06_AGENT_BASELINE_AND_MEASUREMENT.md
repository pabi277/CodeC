# Level 6 — Agent baseline and measurement

**Status (2026-10-03): IMPLEMENTED as [Phase 83](../../phases/03-editor/chat-phase83/README.md); test-only baseline, no production-source or behavior delta. Build APK CI ✅ GREEN on code/test head `cf3f1be` (run `37051539267`); not merged. The owner explicitly started Level 6. Depends on nothing; baseline gates Levels 7–12.**
**Shared foundation:** [defect register, security rules S1–S12, sources](00_AGENTIC_MAP_AND_SECURITY_RULES.md).

**Measurement boundary:** every metric is either backed by the owner screenshots or deterministic offline replay, or explicitly marked **not captured** with its reason in the Phase 83 ledger. No provider request or device profiler was authorized/performed; host-JVM timing is not substituted for phone/provider latency.

## User value

Every later claim becomes a **measured delta** instead of an impression. Without this level, "it feels
better" is the only evidence available, and the owner has already been through one round where CI green
was mistaken for a working agent.

This is the only level in the series that ships **no behaviour change at all**.

## Why it comes first

The current pipeline cannot be improved honestly until the failure is quantified. The owner's phone
evidence — *"9 of 12 steps · 49 of 24 reads · 0 of 2 runs"*, repeated reads of `js/main.js` lines 1–22 —
is a screenshot, not a measurement. It cannot be compared against a later build.

## What this level adds

1. **Four fixture projects** (C, Python, JavaScript, HTML), each containing: a ~300-line normal file
   exercising beginning/middle/end · a large generated file · a minified single-line file · a binary file ·
   a `.env` · a symlink escaping the root · a file carrying prompt-injection text.
2. **Instrumentation, not new behaviour** — record per task: lines requested vs lines delivered, duplicate
   calls, refusals, malformed blocks, turns used, wall clock, request bytes.
3. **A recorded baseline** for the current build, checked into the phase ledger, so Level 7's diff is real.

## The metric set

Adopt Arena's Agent Arena signals as the scorecard — **task success · steerability · retries · tool
hallucinations** — plus CodeC-specific: **context coverage** (lines delivered ÷ lines requested),
duplicate/no-progress calls, first-token and total latency, token use, peak memory, APK delta.

CodeC currently fails **retries** and **tool hallucinations**. Those two are the acceptance headline for
the whole series.

## Security rules

No new permissions and no new surface. **S10** fixtures are *created* here but *enforced* from Level 7
onward. Nothing is sent to a provider during this level except the owner's own manual runs.

## Acceptance checks

- [x] Four synthetic fixture projects are generated offline; no user project or credential data is used.
- [x] The ledger records each metric as an observation/replay value or explicitly **not captured** with a reason; no provider/device value is inferred.
- [x] The baseline reproduces the reported symptom: repeated prefix reads of the same file.
- [x] `git diff` shows **zero** production-source changes.
- [x] The pre-Level-6 AI suite (386 tests / 30 classes) is unchanged; Build APK CI **37051539267** is green on `cf3f1be` with the five new cases (391 tests / 31 classes).

## Research references

- Arena Agent Arena signal set — https://arena.ai/agent
- SWE-agent ablation methodology (hold the model fixed, vary the interface) — https://arxiv.org/abs/2405.15793
- Anthropic, *Building Effective Agents* — "think like your agent"; log what is actually in context — https://www.anthropic.com/research/building-effective-agents

**Dependency next, not authorized or started:** [Level 7 — Agent correctness](07_AGENT_CORRECTNESS.md). It requires its own explicit owner start command.
