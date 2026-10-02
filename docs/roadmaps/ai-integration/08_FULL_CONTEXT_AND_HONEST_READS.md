# Level 8 — Full context and honest reads

**Status: PROPOSED. Not implemented. Not a phase start command. Depends on Level 7.**
**Shared foundation:** [defect register, security rules S1–S12, sources](00_AGENTIC_MAP_AND_SECURITY_RULES.md).

## User value

The agent can finally **reach** any line of a normal source file, reads several related files in one
action when they fit, and always tells the truth about how much of a file it actually saw.

## Owner decision required before this level starts

**"Bounded but honest" replaces "whole file".** The owner asked for full-file reads. The evidence says
otherwise, and this level is built on the corrected target.

SWE-agent's ablation, model held fixed:

| File viewer | SWE-bench Lite resolved |
|---|---|
| 30 lines | 14.3 % |
| **100 lines** | **18.0 %** ← best |
| **Full file** | **12.7 %** (↓5.3) |

*"Either too little content (30 lines) or too much (entire file) lowers performance."* The same holds for
history: **last-5 observations 18.0 %** beats **full history 15.0 %** — so `KEEP_LAST_RESULTS = 4` is
already near optimal and must **not** be raised to 20.

**The bug is that nominal ≠ delivered, not that nominal is too small.** CodeC's cap is 400 lines, already
above the optimum; its delivery is ~22 lines, below even the worst setting tested.

## What changes

1. **Offset-capable reader.** `AiProjectReader.readCapped` today has no offset and stops at
   `MAX_READ_CHARS` (24 000), so lines past the first 24 000 chars are unreachable at any `end` value.
   Preserve UTF-8 handling, cancellation, and dirty-buffer precedence. **No `java.nio.file`** — minSdk 24.
2. **Delivery matches request**, landing in the **~100–400 line** band.
3. **`read_files` batch tool** — validate each path independently, per-file status, stable ordering,
   bounded local concurrency, Stop checked throughout. **Parallel local IO, not parallel agents (S7).**
4. **Restorable eviction.** Replace `(N earlier tool results were dropped to fit)` with a pointer per
   evicted item: `path — lines a-b — status — re-read on demand`.

A batch must not become an unaccounted unlimited action: track files, bytes, and context use separately
from model turns, successes, reused results, and refusals.

## Touches

`AiProjectReader.kt` · `AiToolRunner.kt` · `AiTools.kt` · `AiAgentLoop.kt`. The tool schema grows, which
is an additive change to `AiToolProtocol.INSTRUCTIONS` and therefore visible in the disclosed prompt.

## Security rules

**S1** · **S2** (every read states path, version, lines covered, `complete`/`partial`/`refused`) ·
**S5** (a secret path inside a batch is refused while siblings succeed) · **S7** (parallelism is read-only
IO; one brain writes) · **S11** (exact-duplicate reads served from the working set at zero execution cost;
N identical no-progress calls stop the loop).

Containment, symlink refusal, and the AI's own secret filter are re-applied per path, not once per batch.

## Acceptance checks

- [ ] The last line of a >24 000-char file is reachable *(fails today)*.
- [ ] A read states `complete` or `partial(reason)` — never ambiguous.
- [ ] A secret path inside a mixed batch is refused while its siblings succeed.
- [ ] A symlink escaping the root contributes nothing, in a batch or alone.
- [ ] Every evicted result is re-acquirable by path **and** range.
- [ ] An unchanged repeat read consumes no execution budget.
- [ ] Stop during a batch ends it promptly; no delayed action.
- [ ] Level 6 context-coverage metric improves against the recorded baseline.

## Research references

- SWE-agent ablation Table 3 (file viewer 30/100/full; last-5 vs full history) — https://arxiv.org/abs/2405.15793
- Manus — *compression strategies are always designed to be restorable* — https://medium.com/@peakji/context-engineering-for-ai-agents-lessons-from-building-manus-71883f0a67f2
- Cline — `read_files` batch reads; 8 000-char tool results; 2 000-line read bound — https://deepwiki.com/cline/cline/3.5-context-management
- Anthropic — *the fix is richer observations, not more orchestration layers* — https://www.anthropic.com/research/building-effective-agents

**Next:** [Level 9 — Task memory and planning](09_TASK_MEMORY_AND_PLANNING.md)
