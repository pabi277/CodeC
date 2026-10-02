# Level 7 — Agent correctness

**Status: PROPOSED. Not implemented. Not a phase start command. Depends on Level 6.**
**Shared foundation:** [defect register, security rules S1–S12, sources](00_AGENTIC_MAP_AND_SECURITY_RULES.md).

## User value

Fixes the six defects that make the agent unreliable **today**. None of them is a new feature; all of them
are the reason the owner saw raw `<<<CODEC_TOOL>>>` blocks, a "49 of 24 reads" counter, and the same 22
lines of `main.js` over and over.

This is the highest-value-per-line level in the series. **Nothing after it matters until this is done.**

## What changes

| # | Fix | Defect | Rule |
|---|---|---|---|
| 1 | Separate `modelResult` (full fidelity, for the model) from `detail` (short, for the timeline) | 1 | **S1** |
| 2 | Enforce `blockTool` in `resumeAgentOrStop`, not only inside `AiAgentPolicy.decide` | 2 | — |
| 3 | Clamp and split counters into **requested / executed / refused / reused**; never render one mixed value | 3 | — |
| 4 | Reserved final-synthesis turn on every stop reason, tools **masked** not removed | 4 | **S12** |
| 5 | Preserve the truncation marker at every clip boundary | 1 | **S2** |
| 6 | `AiToolProtocol.parse` returns successfully parsed calls **alongside** the format error | 5 | — |

### Why fix 1 is the whole ballgame

`AiViewModel.kt:893` clips each tool result to `MAX_STEP_DETAIL_CHARS` (1 200) for the on-screen preview,
and `AiAgentPrompt.renderStep` then applies `.take(8 000)` to that already-≤1 200 string — dead code. The
model is told `FILE js/main.js — lines 1-400 of 900` and receives ~22 lines with `CUT_NOTE` cut away.

Arithmetic: a 75-char header leaves ~1 124 chars of body; at ~50 chars per numbered line that is **~22
lines** — matching the owner's screenshot exactly.

### Why fix 6 was easy to miss

`AiToolProtocol.parse` accumulates valid calls into a local list, but every `Malformed` branch does an
early `return`, discarding everything already parsed. Three good `read_file` blocks beside one malformed
block are all thrown away, and the model restarts from scratch.

## Touches

`AiAgentLoop.kt` · `AiViewModel.kt` · `AiCopy.kt` · `AiTools.kt`. No new file, no new dependency, no
permission change, no wire-contract change — **D4 untouched.**

## Security rules

**S1** (separate stores) · **S2** (honest coverage) · **S12** (readable answer on every stop). All other
rules unchanged and re-asserted by the wiring pins.

## Acceptance checks

- [ ] A full tool result reaches the next request unclipped *(fails today)*.
- [ ] The cut marker survives every clip boundary *(fails today)*.
- [ ] An exhausted tool budget blocks **every** resume path, including malformed and denied-only *(fails today)*.
- [ ] Counters never exceed their caps after a 49-block answer *(fails today)*.
- [ ] `parse` returns valid calls alongside a malformed block *(fails today)*.
- [ ] Every `AiAgentStopReason` yields prose, never `<<<CODEC_TOOL` *(fails today)*.
- [ ] Denied / executed / reused are counted separately.
- [ ] Level 6 baseline re-run shows duplicate reads reduced against the recorded number.

## Research references

- Anthropic, *Effective Context Engineering* — tool-result clearing is safe **with** a summary, lossy without — https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
- Manus, *mask, don't remove* — constrain the action space without invalidating tool definitions — https://medium.com/@peakji/context-engineering-for-ai-agents-lessons-from-building-manus-71883f0a67f2
- SWE-agent — guardrails hasten recovery; malformed generations get an error response and a retry — https://arxiv.org/abs/2405.15793

**Next:** [Level 8 — Full context and honest reads](08_FULL_CONTEXT_AND_HONEST_READS.md)
