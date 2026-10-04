# CodeC Phase 89.4 — The refreshed device round

> **Status:** ✅ **IMPLEMENTED (2026-10-04) on `arena/01a102bd-codec`** — [`DEVICE_ROUND.md`](DEVICE_ROUND.md) is refreshed (21 legacy rows + the 60-run matrix + the 8 visual checks + Q1/Q2); **every row is ⏳ and nothing is pre-ticked** — the round itself is owner-only and has **not** been run · **Cost:** `[client-only]` · **Effort:** M
> **Owner authorization (2026-10-04):** one phase, with the owner's device round as a checklist inside it.
> **Only the owner can run it** — his phone, his keys, and every Send is his tap (**D4**).
> **Parent brief:** [Phase 89 README](README.md)

## First move: evidence, not code

The round being refreshed is `docs/phases/03-editor/chat-phase82/DEVICE_ROUND.md`. Read 2026-10-04:

- **Header:** *"Status: ⏸ POSTPONED by owner (2026-10-02), until after agent optimization. NOT PASSED."* It names
  its build (`7b2ecac`, run `36995145462`, release 7 117 440 B) — three optimized levels old.
- **21 rows**, every one `⏳`: **R1–R5** (rate limit, Stop, retry, key deletion) · **B1–B3** (long answer,
  Continue, Stop a continuation) · **P1–P3, P3b–P8** (provider selection, terms, test connection, pending/empty,
  switching, **P5** the fixed task set, key deletion, **P7**, **P8** support-report scrub) · **S1–S4**
  (selection, edits, runs, refusals).
- **The 5-task P5 comparison**: same five tasks run once on Gemini and once on NVIDIA, recorded by observation —
  not a marketing score.
- **P8 is still unticked** and untestable by Level 10's own record: Level 10 stores no secrets (eight ints, enums
  and booleans in the existing non-secret bag). It stays an owner check.
- **What Phase 88 handed forward** (`PART_88_5_ACTIVITY_ROWS_AND_MEASUREMENT.md:96-103`): the **8 visual checks** —
  long answer renders in clear sections / hostile answer inert / `https` tap → dialog → browser, `http` and
  `javascript` inert / code Copy exact / progress line names each stage / rows open and close truthfully /
  disclosure one tap away / code and links legible in dark theme.
- **Two open questions** also handed forward: whether the task-memory caps (**5 files, 160 KiB cache, 256 KiB
  store** — `AiTaskMemory.kt:15,17,28`) are enough in practice, and whether very long answers need a lazy list
  (deferred in Phase 88 to be measured on the device first).

## Design — the refreshed checklist

Phase 89 ships its **own** `docs/phases/03-editor/chat-phase89/DEVICE_ROUND.md` (it does not rewrite Phase 82's
file; history stays intact, and the 82 file keeps pointing at its own build). It contains:

1. **Header:** the exact Phase 89 build — head sha, Build APK run id, release and debug byte counts, the two
   model ids, and the standing law in one line: *CI green is not device acceptance*.
2. **The 21 legacy rows, re-pointed at the new build.** Same numbering (R/B/P/S), same expectations, so a row
   number means the same thing across rounds. P5's five tasks live in the same place.
3. **The 10-task × 2-model × 3-run matrix** from part 89.3, with the **3/3** bar, the recording format and the
   NOT EXERCISED / incomplete rules. This is the bulk of the owner's typing; it is numbered so he can stop and
   resume between sittings.
4. **The 8 visual checks from 88.5**, verbatim in intent, each with a ✅/❌ box.
5. **P8 — the support-report scrub check**, still unticked, with how to build a support report locally using fake
   `nvapi-`-shaped text (never a real key, never in a screenshot).
6. **The memory-cap question:** after a real multi-file task, is 5 files / 160 KiB cache / 256 KiB store enough —
   or does the agent hit the cap on an ordinary project? Recorded as an observation with the cap numbers, not as
   a pass/fail invented by the agent.
7. **The long-answer question:** an answer long enough to scroll a screen or more — does it jank, or is a lazy
   list needed? That decides whether Phase 88's deferred item is picked up.
8. **A results table the owner can tick**, with a final *Device result* line left **blank** until he reports. The
   agent never fills it in.

## The Android edge

None. This part writes a checklist; the app under test is whatever Phase 89's implementation head builds.

## Exit condition

- [x] `chat-phase89/DEVICE_ROUND.md` exists with all eight sections above and the 21 legacy rows. (Part 1 =
      R1–R5, B1–B3, P1–P8, S1–S4, re-pointed at this build; parts 2–5 = the 60-run matrix, the P5 five-task
      comparison, V1–V8 and Q1/Q2.)
- [x] Every row has an expectation and an empty result cell; nothing is pre-ticked. (`AiLevel12MatrixTest` pins
      the P8 row as unticked and the two questions as asked.)
- [x] The build line names the real sha and run id, and the byte counts come from check-run annotations, not memory.
      (head `58039cd`, Build APK [`37185580349`](https://github.com/pabi277/CodeC/actions/runs/37185580349) green,
      release 7 179 540 B · debug 27 062 592 B · R8 mapping 71 172 428 B.)
- [x] The file states plainly: **only the owner runs it**, and formal acceptance is his report.

## Tests (plan)

Documentation only; no test file. The checklist's completeness is pinned by `AiLevel12MatrixTest` (part 89.3)
so a future refactor cannot quietly drop a required row.

## Sources (record)

1. `docs/phases/03-editor/chat-phase82/DEVICE_ROUND.md` (the 21 rows, P5's five tasks, the quota wording) — read
   2026-10-04.
2. `docs/phases/03-editor/chat-phase88/PART_88_5_ACTIVITY_ROWS_AND_MEASUREMENT.md:96-103` (the 8 visual checks).
3. `AiTaskMemory.kt:15,17,28` (the caps: `MAX_FILES` 5, `MAX_TOTAL_FILE_BYTES` 160 KiB, `MAX_STORE_BYTES` 256 KiB).
4. Phase 87 record — settings-export / feedback scrubbing was not re-verified on a device; P8 is where it lands.

## Deferred / rejected with reasons

- **Sending the owner a single mega-checklist with everything mandatory in one sitting.** Rejected: 60 real runs
  plus 21 rows is not one sitting on a phone; rows are numbered and resumable, and incomplete is allowed.
- **Re-ticking Phase 82's file in place.** Rejected: that file documents a specific postponed round on a specific
  old build; overwriting it would fake a history the repo does not have.
- **Automating the checks (Robolectric/UI tests standing in for the phone).** Rejected: this is exactly the
  mistake the repo already made once — CI green mistaken for device acceptance.

## Implementation (2026-10-04)

[`DEVICE_ROUND.md`](DEVICE_ROUND.md) is written on `arena/01a102bd-codec`. Its header is ⏳ **OWED, owner-only**;
every one of its rows is ⏳ with an empty result cell, and the completion bars are empty. Nothing in it was
filled from a run, because none has been run. The build line names the branch head — `58039cd` — and takes its byte counts
from the `build` check-run annotations of the green run [`37185580349`](https://github.com/pabi277/CodeC/actions/runs/37185580349)
(release 7 179 540 B · debug 27 062 592 B · R8 mapping 71 172 428 B), never from memory.
