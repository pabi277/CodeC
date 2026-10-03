# CodeC Phase 87.2 — Read window and working-set depth

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
> **Owner row (verbatim):** *"more options to use in the app for better optimization."*
> **Parent brief:** [Phase 87 README](README.md)

## First move: evidence, not code

Read 2026-10-03 against `main` @ `6838ea6`. `MAX_READ_LINES` is used in **five** places, and a
read window that reaches only some of them will produce a runner that refuses what the model was
told it could ask for:

| Site | What it does with the constant |
|---|---|
| `AiTools.kt:234` | declares `MAX_READ_LINES = 400` |
| `AiTools.kt:321` | when the model omits `end`, defaults it to `MAX_READ_LINES` |
| `AiTools.kt:327` | caps an explicit `end` to the window |
| `AiTools.kt:348` | the other default-`end` path |
| `AiTools.kt:350-351` | denies a request above the window: `"read_file may read at most ${AiToolLimits.MAX_READ_LINES} lines at once"` |
| `AiTaskMemory.kt:150` | cache identity: `call.end ?: start + AiToolLimits.MAX_READ_LINES - 1` |
| `AiToolRunner.kt:416` | the follow-up hint: `"; read ${end + 1}-${minOf(total, end + AiToolLimits.MAX_READ_LINES)} next"` |
| `AiToolRunner.kt:23` | doc comment stating the limit |

`KEEP_LAST_RESULTS` (`AiAgentLoop.kt:47`) is read at `AiAgentLoop.kt:428` inside the request
packer, and Phase 85 builds one re-read pointer per result that line drops.

**The risk this part exists to prevent:** `AiTaskMemory.kt:150` derives the cache version key from
the *effective* line range. If the window becomes user-tunable and that line still uses the
constant, the key describes a range that was never delivered, and a stale cached read can be
served. Level 9's whole invariant is that the key includes the effective range.

## Design

The window becomes a parameter threaded from `AiOptions`, not a second constant.

```kotlin
// AiToolLimits keeps MAX_READ_LINES as the CEILING.
// AiToolPolicy.read(...) gains the effective window and clamps it first:
fun readWindow(requested: Int?): Int =
    AiOptionsPolicy.clampReadWindow(requested ?: AiOptionsPolicy.DEFAULT_READ_WINDOW_LINES)
        .coerceAtMost(AiToolLimits.MAX_READ_LINES)
```

Every one of the five `AiTools` sites takes the effective window as an argument. `AiTaskMemory`'s
identity computation and `AiToolRunner`'s hint take the **same** value that produced the read, so
key, hint and refusal can never disagree.

`KEEP_LAST_RESULTS` likewise becomes the packed-context depth, clamped to 2–8 and passed into the
packer at `AiAgentLoop.kt:428`. `MAX_EVICTION_POINTER_CHARS` (Phase 85) is unchanged: a deeper
working set means fewer evictions, so the pointer budget is under less pressure, never more.

**Ordering constraint:** the window is read once per request and frozen for that request, exactly
as D4 freezes the provider, model and text. A mid-task settings change must not alter an in-flight
task's window, because the disclosed preview would then describe a different read than the one
performed.

## The Android edge

`AiViewModel` passes `state.options.readWindowLines` and `state.options.workingSetDepth` into the
loop construction, frozen at Send. No new `@Composable`. No new dependency.

## Exit condition

- Lowering the window to 50 makes every read, its honest coverage header, its cache identity and
  its follow-up hint agree on 50.
- A read above the window is still refused with the existing message, naming the effective window.
- Raising the depth to 8 packs eight results and still emits a pointer for each eviction.
- An in-flight task is unaffected by a settings change made during it.

## Tests (plan)

- `AiToolRunnerTest` — **3 new**: a 50-line window delivers 50 and says so; the hint names the
  effective window; a request above the window is refused.
- `AiTaskMemoryTest` — **2 new**: the cache key differs between two windows over the same file;
  a window change invalidates a stored snapshot.
- `AiAgentLoopTest` — **2 new**: depth 8 packs eight; depth 2 packs two and emits pointers for
  the rest.
- `AiLevel10WiringTest` — **3 new**: source pins that `AiTools`, `AiTaskMemory` and
  `AiToolRunner` all take the window as a parameter and none re-reads
  `AiToolLimits.MAX_READ_LINES` as a live value.

## Sources (record)

1. Repository: `AiTools.kt:234,321,327,348,350-351`, `AiTaskMemory.kt:150`,
   `AiToolRunner.kt:23,416`, `AiAgentLoop.kt:47,428` — read 2026-10-03 against `main` @ `6838ea6`.
2. SWE-agent ablation Table 3 (bounded beats unbounded) — https://arxiv.org/abs/2405.15793,
   cited from the Level 10 spec; not re-fetched this session.

## Deferred / rejected with reasons

- **Letting the model choose the window** — rejected; S9. The model is told the format, never the
  permissions (`AiTools.kt` doc comment at `:226-231`).
- **Changing the default from 400** — deferred; see *Open decision 1* in the Phase 87 README.
- **Per-file windows** — rejected; one frozen value per request is what D4 can disclose.
