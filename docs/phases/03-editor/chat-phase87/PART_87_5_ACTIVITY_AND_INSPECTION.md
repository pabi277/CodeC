# CodeC Phase 87.5 — Tool activity display and non-removable request inspection

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
> **Owner row (verbatim):** *"more options to use in the app for better optimization."*
> **Parent brief:** [Phase 87 README](README.md)

## First move: evidence, not code

Read 2026-10-03 against `main` @ `6838ea6`. Defect 10 in the shared register:

> *"Disclosure floods the timeline — `AiChatSheet.kt:584` renders system instruction + full user
> text per REQUEST row. Up to 12 copies of system text + 6 000-char map inline."*

**The register's line number is stale.** It was written against `4cb4151` and the code has moved.
Verified on `6838ea6`:

| Site | What it actually is |
|---|---|
| `AiChatSheet.kt:344` | `SentText(it.systemInstruction + "\n\n" + it.userText)` — the pre-Send preview |
| `AiChatSheet.kt:587-589` | `step.sentSystemInstruction?.let { instruction -> … SentText(instruction + "\n\n" + step.sentUserText.orEmpty()) }` — the per-REQUEST timeline row, with the comment *"D4: whole strings, not the timeline's ellipsised result preview."* |
| `AiChatSheet.kt:467` | `SentText(edit.unifiedDiff)` — the edit-proposal disclosure |
| `AiChatSheet.kt:584` | `maxLines = 3` — **not** a disclosure site; the register's number is off by three lines |

The defect is real; only the citation moved. **`:587-589` is the row this part makes collapsible.**
The `:344` preview must stay whole — collapsing the preview would break D4, which requires the
exact text to be shown before Send. That asymmetry is the design constraint: the *timeline* may
collapse, the *preview* may not.

Two constraints bound the fix before any design:

- **S8** — every recipient is disclosed per request; disclosure may be **collapsed, never
  removed**.
- **D4** — request inspection is not user-removable. The Level 10 spec states it plainly:
  *"Not user-removable."*

So the collapsed state must still be a tap away from the same text, and the row that would turn
inspection off must not exist as a control.

## Design

```kotlin
// AiActivityDisplay.COLLAPSED | EXPANDED — presentation only.
fun activityRow(activity: AiActivityDisplay, stepCount: Int): AiActivityRow
```

**Collapsed** renders one line per REQUEST row — provider, model, and the sizes actually sent —
with the full text behind a tap on that row. **Expanded** renders what `AiChatSheet.kt:587-589`
renders today. The data reaching the request is identical in both states; only the rendering
differs. That is what makes this S1-safe: a UI row must never change model input.

**Request inspection** is rendered as a **non-interactive** row that states it is always on:
*"Every request is shown to you before it is sent. This cannot be turned off."* There is no
`PROP_` for it and no setter, so there is nothing for a future change to flip. The absence is the
guarantee, and the wiring test pins it.

### The Level 9 acceptance check this closes

Phase 86 left one box unticked:

> *"Collapsing or expanding a UI row does not change model input (S1 re-asserted);
> Android/Compose verification remains pending."*

This part is where that box gets its evidence. The test must assert that the packed request text
is byte-identical for both display states — not merely that the code looks separate.

## The Android edge

`AiChatSheet.kt` at `:587-589` becomes display-state driven. This is the one part of Phase 87 with
real Compose work, and the one part that a host harness cannot fully prove: the row rendering
needs a screenshot or device check. The host-testable half is the pure policy plus the
byte-identity assertion on the packed text.

## Exit condition

- Collapsed shows one line per request and a tap reveals the identical full text.
- Expanded shows today's rendering.
- The packed request text is byte-identical in both states (**S1**).
- No control anywhere can disable request inspection; the row says so.
- No disclosure is removed — only collapsed (**S8**).

## Tests (plan)

- `AiOptionsPolicyTest` — **2 new**: `activity` decodes both values and falls back to
  `COLLAPSED` on `null`/unknown.
- `AiChatSheetPolicyTest` — **3 new**: the collapsed row carries provider, model and sizes; the
  expanded row carries the full text; both are derived from the same disclosed strings.
- `AiLevel10WiringTest` — **3 new**: source pins that (a) no `PROP_` or setter exists for request
  inspection, (b) the display state is read only inside `AiChatSheet`, and (c) the packed request
  text does not depend on it — closing the Phase 86 S1 box.

## Sources (record)

1. Repository: `AiChatSheet.kt:344,467,584,587-589` (the `:584` figure is the stale one from the
   defect register, retained above to show the correction), `AiSheetPolicy.kt`,
   `AiKeyStore.kt:189-197` — read 2026-10-03 against `main` @ `6838ea6`.
2. Defect 10 and **S8** in
   [`00_AGENTIC_MAP_AND_SECURITY_RULES.md`](../../../roadmaps/ai-integration/00_AGENTIC_MAP_AND_SECURITY_RULES.md).
3. The unticked S1 acceptance row in
   [`09_TASK_MEMORY_AND_PLANNING.md`](../../../roadmaps/ai-integration/09_TASK_MEMORY_AND_PLANNING.md).

## Deferred / rejected with reasons

- **A per-row "hide this request" action** — rejected; it would remove disclosure, not collapse it.
- **Virtualising the timeline to fix the flood** — deferred to Level 11 (agent phone
  presentation), which owns rendering. This part changes what a row shows, not how the list
  scrolls.
- **Persisting the inspection row** — rejected; a stored "on" implies a stored "off".
