# CodeC Phase 87.8 — Read-only reviewer

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** L
> **Owner row (verbatim):** *"more options to use in the app for better optimization."*
> **Parent brief:** [Phase 87 README](README.md)

## First move: evidence, not code

Read 2026-10-03 against `main` @ `6838ea6`.

- The Level 10 spec's row is: *Read-only reviewer · off / on · default **off** · Read-only,
  separately triggered.*
- The shared map's architecture box already places a reviewer outside the write path, and its
  "Rejected" list includes **parallel write-agents**, on Cognition's Principles 1 & 2.
- **S7:** *"One brain writes. Parallelism is for read-only local IO only. Writes stay
  single-threaded and user-approved, one file at a time."*
- **D1** as amended: the agent proposes edits only through `<<<CODEC_EDIT …>>>` blocks; the only
  write path is a user tap on Apply through `ui/projects/AiEditApplier.kt`.
- **S3:** project text, tool output, run output and task memory are **untrusted data**. A
  reviewer's answer is model output about untrusted data, so it inherits that status.
- **There are exactly three `client.stream(` call sites** — `AiViewModel.kt:905`, `:1281`,
  `:1508`.

This is the largest part of Phase 87 and the one most at risk of drifting into a second agent.
The bounds below are what keep it a reviewer rather than a workforce.

## Design

```kotlin
enum class AiReviewer { OFF, ON }

object AiReviewerPolicy {
    /** A review is a separate, user-triggered request. It is never chained from the loop. */
    fun mayTrigger(reviewer: AiReviewer, userTapped: Boolean, phase: AiPhase): Boolean

    /** The reviewer gets no tools. Its request omits the tool protocol entirely. */
    fun instruction(): String

    /** Its answer can never become an edit, a run, or a tool call. */
    fun parse(text: String): AiReviewVerdict
}
```

Four hard bounds, each pinned by a test:

1. **No tools.** The reviewer's system instruction omits `AiToolProtocol.INSTRUCTIONS` and
   `AiTaskMemoryProtocol.INSTRUCTIONS`, so it cannot emit a tool block that anything would run.
2. **Separately triggered.** `mayTrigger` requires an explicit user tap. The agent loop never
   calls it, so the reviewer cannot spend the main task's budget.
3. **Read-only.** Its verdict type has no edit, run or apply case. Parsed `<<<CODEC_EDIT …>>>`
   text in a review is rendered as text, never handed to `AiEditApplier`.
4. **Untrusted output.** Its answer is labelled as a second opinion in the UI and is treated as
   data under S3 — it cannot alter the agent's rules, permissions or plan.

### The stream-site law

The reviewer sends through the **existing** helper path, which already uses one of the three
`client.stream(` sites (`AiViewModel.kt:1508`). The part must not add a fourth. A source pin
counts the sites and fails at four — the same pin 87.7 relies on.

### Budget

A review is one request with its own disclosed preview, outside the agent's turn/tool caps
because it is not part of the agent task. It does count against the user's provider quota, and the
row says so, because that is the real cost the owner was trying to optimise.

## The Android edge

One row in `AiHome` (off / on) and a *Get a second opinion* action available on a completed
answer. The review renders in its own labelled block, visually distinct from the agent's answer,
so the user can tell which model said what — **S8** requires the recipient to be named per
request, and the preview does that.

## Exit condition

- Off by default; with `OFF`, no review action exists anywhere.
- A review is never triggered without an explicit user tap.
- The reviewer's request contains no tool protocol and grants no tool.
- Edit-looking text in a review is displayed, never applied.
- The review names its provider and model in its own preview before Send.
- The agent's caps, run count and approval requirements are unchanged by a review.
- Exactly three `client.stream(` call sites remain.

## Tests (plan)

- `AiReviewerPolicyTest` — **8**: `mayTrigger` for `OFF`, for `ON` without a tap, and for a tap
  in a non-idle phase; `instruction()` contains neither tool protocol; `parse` returns no
  edit/run case for edit-shaped text; a review does not touch the agent budget.
- `AiLevel10WiringTest` — **3 new**: source pins that the `client.stream(` count is exactly
  three; that no reviewer path reaches `AiEditApplier`; that the agent loop contains no call into
  the reviewer.
- `AiLevel10PermissionTest` — **1 new**: no option path, reviewer included, reaches a project
  write or a command execution (**S6**).

## Sources (record)

1. Repository: `AiViewModel.kt:905,1281,1508`, `AiToolProtocol.INSTRUCTIONS`,
   `AiTaskMemoryProtocol.INSTRUCTIONS`, `ui/projects/AiEditApplier.kt` — read 2026-10-03 against
   `main` @ `6838ea6`.
2. Cognition, *Don't Build Multi-Agents* — https://cognition.com/blog/dont-build-multi-agents,
   cited from the shared map (Principles 1 & 2; writes stay single-threaded). Not re-fetched this
   session.
3. **S3**, **S7**, **S8** in
   [`00_AGENTIC_MAP_AND_SECURITY_RULES.md`](../../../roadmaps/ai-integration/00_AGENTIC_MAP_AND_SECURITY_RULES.md).

## Deferred / rejected with reasons

- **An automatic review of every agent answer** — rejected; it would double every request's cost
  and spend the budget this level exists to optimise.
- **Letting the reviewer apply edits or run tools** — rejected; that makes it a second writer and
  breaks S7 and D1.
- **A reviewer with the main task's context and tools, running in parallel** — rejected;
  Cognition's Principles 1 & 2, and the shared map's explicit rejection of parallel write-agents.
- **Three or more reviewers** — deferred; one second opinion is what the spec authorizes, and
  more would be a spend multiplier with no measured benefit.
