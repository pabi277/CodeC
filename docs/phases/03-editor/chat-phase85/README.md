# Phase 85 — AI Level 8: full context and honest reads

> **Status:** IMPLEMENTED on `arena/01a0fe36-codec` · host-shim pre-validation **360/360** · merged with Phase 84 via authorized PR #110 to `main` @ `32e4a5f`; final-head Build APK **37084800939 ✅ GREEN**. Formal device acceptance remains POSTPONED (Level 12).
> **Owner command (verbatim):** “Start Level 8” → chose **bounded but honest** and **Full Level 8 (all four items)** (2026-10-03).
> **Baseline:** `main` @ `392a614573137a56db112764a57409773b9fe962` (PR #109 merge), branch head before this phase `a1af105` (Phase 84). Level 8 spec: [`08_FULL_CONTEXT_AND_HONEST_READS.md`](../../../roadmaps/ai-integration/08_FULL_CONTEXT_AND_HONEST_READS.md); shared foundation: [`00_AGENTIC_MAP_AND_SECURITY_RULES.md`](../../../roadmaps/ai-integration/00_AGENTIC_MAP_AND_SECURITY_RULES.md).
> **Device acceptance:** not part of this phase. The formal device round remains **POSTPONED** (Level 12), not passed.

## What Level 8 is

The agent could not see enough of a file, and it could not tell when it had not.
A `read_file` capped at the first 24 000 characters, so the tail of a large file
was unreachable; a delivered fragment was labelled with the range it *asked* for,
not the range it *got*; reading several files cost several round trips; a result
that did not fit the request was reduced to a bare count; and a repeated read
re-executed and re-counted. Level 8 makes the reads **bounded but honest** and
adds the batch, the restorable eviction and the loop-local working set.

The owner accepted **bounded but honest** over whole-file delivery: the SWE-agent
viewer ablation put a 100-line window at **18.0%** versus full-file **12.7%**, so
the caps stay bounded and `KEEP_LAST_RESULTS` stays **4** (near-optimal in the
last-5 ablation). Honesty — not a bigger window — is the fix.

| # | Item | Acceptance | Rule | Part |
|---|---|---|---|---|
| 1 | Offset-capable reader: any line of a >24 000-char file is reachable | last line reachable | — | [85.1](PART_85_1_OFFSET_READER.md) |
| 2 | Delivery matches request: every read states `complete` / `partial(reason)` / `refused` | coverage always stated | **S2** | [85.1](PART_85_1_OFFSET_READER.md) |
| 3 | `read_files` batch: per-path validation, per-file status, stable order | secret refused beside siblings | **S5 · S7** | [85.2](PART_85_2_READ_FILES_BATCH.md) |
| 4 | Restorable eviction: a per-item pointer, not a count | every eviction re-acquirable | — | [85.3](PART_85_3_RESTORABLE_EVICTION.md) |
| S11 | Loop-local working set: duplicate reads cost zero; N no-progress calls stop | reused counter populated | **S11** | [85.4](PART_85_4_WORKING_SET_S11.md) |

## First-move evidence (rechecked at branch head `a1af105`, 2026-10-03)

| Item | Evidence before the fix |
|---|---|
| 1 — no offset | `AiProjectReader.readCapped(file)` read the first `AiProjectFiles.MAX_READ_CHARS` (24 000) chars only; `AiToolRunner.read` sliced *that prefix*, so lines past ~304 of a 2 000-line file were unreachable. `AiLevel6BaselineTest` pinned the defect (`the prefix-only reader cannot reach a generated file tail range`). |
| 2 — dishonest header | the header reported the *requested* `start-end` while `clip()` silently truncated the body to 8 000 chars; a fragment could read as the whole file. |
| 3 — no batch | only `read_file` (one path) existed; `AiToolName` had four entries. |
| 4 — bare count | `AiAgentPrompt.pack` emitted `(N earlier tool results were dropped to fit)` with no way to name or re-read them. |
| S11 — counter pinned at 0 | `AiAgentBudget.toolCallsReused` doc read *“Always 0 until Level 9's working set exists”*; `executeToolBatch` called `withToolCalls(1)` for every call, duplicate or not. |

## Guardrails (unchanged and re-asserted)

- Exactly **three** `client.stream(` call sites preserved (`AiViewModel.kt`
  841 / 1136 / 1363 after this phase); S11 adds no network road.
- `ui/ai/` keeps **zero** project writes and **zero** command execution.
  `read_files` is parallel **read-only IO**, never parallel agents (**S7**).
- Per-path security (canonical containment, the minSdk-24
  `canonicalPath != absolutePath` symlink test, the AI's **own** secret filter) is
  re-applied to **every** file in a batch, not once (**S5**).
- The reader is `java.io` `BufferedReader.readLine` only — **no `java.nio.file`**
  (minSdk 24).
- Default model unchanged; D1–D6 and all amendments intact; no new dependency,
  permission, DataStore key, endpoint or Settings control.

## Parts

- [85.1 — the offset-capable honest reader (items 1, 2)](PART_85_1_OFFSET_READER.md)
- [85.2 — the `read_files` batch tool (item 3)](PART_85_2_READ_FILES_BATCH.md)
- [85.3 — restorable eviction (item 4)](PART_85_3_RESTORABLE_EVICTION.md)
- [85.4 — the loop-local working set (S11)](PART_85_4_WORKING_SET_S11.md)

## Tests

- **New:** `AiLevel8BatchTest` **12** (batch: secret refused beside siblings,
  escaping symlink contributes nothing, stable order, missing/non-admitted
  refusals, per-file range, prompt stop, caps, parse + additive disclosure) ·
  `AiLevel8ContextTest` **4** (restorable eviction) · `AiLevel8ReuseTest` **7**
  (working set + counters).
- **Updated for the corrected contract:** `AiLevel6BaselineTest` (the tail test
  now asserts the last line **is** reachable; the Phase 84 packing case now
  asserts the honest delivered range) · `AiToolRunnerTest` (small-binary refusal)
  · `AiAgentLoopTest` (a dropped path is now a re-read pointer, not invisible).
- **Inventory:** 317 Kotlin test files / 3 023 `@Test`; **36 AI classes / 441 AI
  tests** (pre-Level-8: 314 / 3 000; 33 / 418).
- **Pre-validation:** **360/360** on the jdk4py + kotlinc 2.4.20 host harness
  against the real production sources (JUnit/org.json/coroutines shimmed;
  `internal` visible via `-Xfriend-paths`). CI (`:app:testDebugUnitTest`) remains
  the executor of record. The `AiViewModel` coroutine path (S11 wiring) is verified
  by compilation and the static wiring pins, not by runtime execution on this
  harness; its pure decision logic is unit-tested in `AiLevel8ReuseTest`.

## Exit / verification ledger

- [x] State verified: branch `arena/01a0fe36-codec`, head before phase `a1af105`.
- [x] Evidence: all five sites re-read at `a1af105` before editing.
- [x] Items 1–4 + S11 implemented; three stream sites preserved; no `java.nio.file`.
- [x] 23 new tests; 4 pinned cases updated to the corrected contract; 360/360 host pre-validation.
- [x] Level 8 acceptance: last line of a >24 000-char file reachable; every read states coverage; secret refused beside siblings; escaping symlink contributes nothing; every eviction re-acquirable by path and range; duplicate read costs zero execution.
- [x] Final-head Build APK CI for the combined Phase 84+85 PR: **37084800939 ✅ GREEN**.
- [x] PR #110 merged to `main` at `32e4a5f87a9f73874a4de17967187445c49a2461` on the owner's authorization.
- [ ] Device pass: **not requested** — the formal round stays with Level 12.
