# CodeC Phase 88.5 — Activity rows with the full result on tap, disclosure pins, measurement

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
> **Owner decisions (2026-10-03):** activity rows that show the full result on tap · request disclosure
> stays collapsed, never removed.
> **Level 11 spec:** *"Tool activity — expandable rows, collapsed by default, full result on tap."*
> **Parent brief:** [Phase 88 README](README.md)

## First move: evidence, not code

Read 2026-10-03 on `arena/01a101db-codec` @ `8062f0c`.

**The row today** (`AiChatSheet.kt:625-679`): a title (`maxLines = 3`), then, for REQUEST rows, the
87.5 disclosure, then `step.detail` in monospace with `maxLines = 12` and an ellipsis (`:670-678`).
With 24 reads that is up to 288 lines of previews between the question and the answer, because the card
renders above the answer (`:294-296` vs `:373`/`:412`). The previews are also cut: `detail` comes
from `AiAgentLimits.timelineDetail` (`AiViewModel.kt:1336`, `AiAgentLoop.kt:78`) or
`.take(MAX_STEP_DETAIL_CHARS)` = 1 200 (`AiViewModel.kt:1164`, `AiAgentLoop.kt:66`).

**The full text exists.** `modelResult` is set once per step (`AiViewModel.kt:970, 985, 1005, 1165,
1179, 1261, 1337`) and never mutated afterwards. It is read only by `renderStep`, which packs
`modelResult.take(MAX_RESULT_CHARS)` (`AiAgentLoop.kt:537`), and by `evictionPointer`, which reads
its first line (`:516`).

| Carries a full result | Carries none (unchanged) |
|---|---|
| TOOL (`:1331-1338`), ANSWER (`:1161-1166`), DENIED (`:1175`, `:1257`), RUN_RESULT (`:966`, `:981`, `:1003`) | REQUEST (has the request disclosure), TASK, RUN_DECISION, STOPPED |

**Eviction changes what "full" may claim.** Level 8's restorable eviction (`AiAgentLoop.kt:509-530`)
can send an older result as a one-line pointer in *later* requests. The tap view therefore says the
result is shown **as it first entered a request**. The REQUEST rows stay the per-turn record of exactly
what was sent.

**The pattern to mirror is already in the card.** `openRequests = remember { mutableStateListOf<Int>() }`
(`:624`) is per-row UI state that never reaches the ViewModel. The 87.5 option
`AiActivityDisplay { COLLAPSED, EXPANDED }` (`AiOptionsPolicy.kt:33`, default `COLLAPSED` at `:53`)
already governs REQUEST rows through `AiActivityPolicy.row` (`AiLevel10Policies.kt:33-41`).

**Existing pins this part must keep green:** `AiLevel10WiringTest` *"request inspection has no stored
property and no setter"* (`:98`) and *"the collapsed timeline keeps the disclosure and never changes
model input"* (`:156`); `AiLevel7WiringTest:26-28`, where `renderStep` packs `step.modelResult` and
never `step.detail`.

## Design

```kotlin
// In AiLevel11Policies.kt — pure, stateless, display-only.
object AiResultRowPolicy {
    fun hasFullResult(step: AiAgentStep): Boolean = step.modelResult.isNotEmpty()
    /** The same expression renderStep packs: the row shows exactly the model's input for this result. */
    fun fullResult(step: AiAgentStep): String = step.modelResult.take(AiAgentLimits.MAX_RESULT_CHARS)
    /** One line for the collapsed row. TOOL previews start with their honest FILE header. */
    fun teaser(step: AiAgentStep): String = step.detail.lineSequence().firstOrNull().orEmpty()
    fun startsOpen(display: AiActivityDisplay): Boolean = display == AiActivityDisplay.EXPANDED
}
```

**Rows with a full result:**
- **Collapsed:** the title, then one line: the teaser (`maxLines = 1`, ellipsis) and *"tap for the full
  result"*.
- **Open:** `SentText(AiResultRowPolicy.fullResult(step))`, the same bounded, self-scrolling,
  selectable monospace box as the request disclosure. Under it, a caption, *"as it first entered a
  request · N characters"*, and **collapse**.
- `EXPANDED` starts rows open; `COLLAPSED` (the default) starts them closed. Either way, a tap toggles.
- Per-row state is `openResults`, `remember`-local beside `openRequests`.

**Rows without a full result** keep today's drawing. They are short by nature.

**Never Markdown.** Results are data (**S3**): file contents, refusals, run digests. They are drawn raw,
exactly as they entered the request, never through 88.3's renderer.

**Why S1 holds.** Opening a row reads `modelResult`; it writes nothing. `openResults` never leaves the
composable, and `renderStep`, the packer and the ViewModel never learn whether a row is open. **D6
holds** because `modelResult` already lives in memory only; drawing it persists nothing.

### Request disclosure: kept, and pinned

Unchanged from 87.5: collapsed to the `AiActivityPolicy.row` summary, with one tap to the identical
`SentText(instruction + "\n\n" + user)`. The pre-Send preview (`:365`) stays whole. Level 11 adds pins
so a later presentation change cannot remove either.

### Measurement (recorded at implementation, from Build APK)

| Baseline | Release APK | Debug APK | Source |
|---|---:|---:|---|
| Level 6 (`main` @ `7419566`) | 7 117 444 B | 26 869 612 B | run `37047037300`, Phase 83 `BASELINE.md` §7 |
| Current `main` (`c3771c5`) | 7 154 428 B | 26 988 060 B | run `37122702615`, *APK size* annotations |
| Phase 88 implementation head | *to record* | *to record* | its Build APK run |

Report both deltas. Only the second isolates Level 11; the first includes about 37 KB of release growth
from Levels 7–10. Also record the test inventory delta against the README's baseline (326 files /
3 120 `@Test`; 538 AI `@Test`).

### Handed to the Level 12 device round (not run here)

1. A long *"explain this file line by line"* answer renders in clear sections, scrolls smoothly and is
   not truncated.
2. A hostile answer stays inert.
3. An `https` link goes tap → dialog → browser; `http`/`javascript` do nothing.
4. Code **Copy** pastes exactly.
5. The progress line during a multi-read task names each stage.
6. Rows open and close; the result matches the request's content.
7. Request disclosure is one tap away.
8. Code blocks and links are legible in the dark theme.

## The Android edge

`AiChatSheet.kt` only, inside the existing step loop (`:625-679`): the `:670-678` block becomes the
collapsed/open result row for steps where `hasFullResult` is true, and is unchanged otherwise.
`AiResultRowPolicy` is the only new type.

## Exit condition

- [ ] Rows with a full result collapse to one line and open to exactly `AiResultRowPolicy.fullResult(step)`.
- [ ] `EXPANDED` starts them open; `COLLAPSED` (the default) starts them closed.
- [ ] `openResults` and `AiResultRowPolicy` appear in neither `AiViewModel.kt` nor `AiAgentLoop.kt` (**S1**).
- [ ] `renderStep` is unchanged and still reads `modelResult` (existing pin).
- [ ] Request disclosure is still one tap away and identical; the pre-Send preview is whole; inspection still cannot be turned off.
- [ ] Results are never passed to the Markdown renderer.
- [ ] Both APK deltas and the inventory delta are recorded in the README.

## Tests (plan)

- `AiResultRowPolicyTest`, about 6 cases: each kind's `hasFullResult`; for every kind with a result,
  `AiAgentLoop.renderStep(step)` **contains** `fullResult(step)`; a result over 8 000 characters is cut
  exactly where `renderStep` cuts it; `startsOpen` for both options; `teaser` keeps a TOOL row's
  `FILE … lines a-b of n [status]` header.
- Pins in `AiLevel11WiringTest`: the open row draws `AiResultRowPolicy.fullResult(`; no `openResults`
  or `AiResultRowPolicy` in `AiViewModel.kt` or `AiAgentLoop.kt`; `openRequests`, `AiActivityPolicy.row(`
  and `SentText(instruction` are still present; the PREVIEW branch still draws
  `SentText(it.systemInstruction`; the result row never calls `Answer(`.
- Regression: `AiLevel10WiringTest` (`:98`, `:156`) and `AiLevel7WiringTest` (`:26-28`) unchanged and
  green.

Counts are a plan, not a result.

## Sources (record)

1. The Level 11 spec — four surfaces; *"full result on tap"*; S1/S8 —
   [`11_AGENT_PHONE_PRESENTATION.md`](../../../roadmaps/ai-integration/11_AGENT_PHONE_PRESENTATION.md).
2. Phase 83 [`BASELINE.md`](../chat-phase83/BASELINE.md) §7 — the Level 6 APK bytes.
3. Repository lines above, read 2026-10-03 on `8062f0c`. Current-`main` APK bytes come from run
   `37122702615`'s *APK size* annotations.

## Deferred / rejected with reasons

- **Showing what the model "currently sees" for each row** — rejected: after eviction it may be a
  pointer, so the honest claim is "as it first entered a request". The REQUEST rows show each turn's
  exact input.
- **Folding the whole activity card** — rejected: request disclosure would be two taps away.
- **Moving the card below the answer** — rejected for now: a layout jump at DONE. Collapsed rows remove
  the burying without it.
- **Searching or filtering rows** — deferred: not required by the acceptance checks.
