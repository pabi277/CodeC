# Phase 87 — AI Level 10: agent controls and options

> **Status:** ✅ **IMPLEMENTED, host-verified, CI-pending at the time of writing.** All nine
> controls are wired end to end (parts 87.1–87.8). **152 host tests pass** on the
> kotlinc/JRE harness described in `rule.md` §9; Build APK on CI is the executor of record.
> Committed and pushed to `arena/01a100ed-codec` only. **No PR, no merge, no push to `main`.**
> **Owner authorization (2026-10-03):** the owner authorized Level 10 in chat by selecting
> *"Authorize Level 10 (agent controls/options)"* and then answered four design questions:
> **all nine** controls in Phase 87 · controls live in the **`AiHome` AI panel** · backup
> provider is **manual offer only** · answer detail **defaults to `normal`** (today's wording).
> Implementation was authorized by the owner's follow-up, *"Complete level 10"*.
> **Baseline:** `main` @ `6838ea6cf72937766f4d92eb5e9729b71f86b9ee` — Phase 86 / AI Level 9 merged
> by authorized [PR #111](https://github.com/pabi277/CodeC/pull/111) on 2026-10-03; post-merge
> Build APK run [`37109573383`](https://github.com/pabi277/CodeC/actions/runs/37109573383) is
> **green** on that commit. Formal device acceptance remains **POSTPONED** to Level 12.
> **Level 10 spec:** [`10_AGENT_CONTROLS_AND_OPTIONS.md`](../../../roadmaps/ai-integration/10_AGENT_CONTROLS_AND_OPTIONS.md) ·
> **shared rules:** [`00_AGENTIC_MAP_AND_SECURITY_RULES.md`](../../../roadmaps/ai-integration/00_AGENTIC_MAP_AND_SECURITY_RULES.md)

## Owner row (verbatim)

> *"add a step wise ai map update for full agentic vive with stronger security rules but more
> options to use in the app for better optimization"*

and, for this level specifically, the spec's own quotation of it:

> *"more options to use in the app for better optimization."*

## The one rule that governs the whole phase — S9

**S9 — options tune within caps; they never raise a permission.**

Every range below has a hard ceiling declared in pure policy and asserted by a host test.
No control may widen D1/D5 tool, write, run or path permissions. An option that could grant
access is not an option — it is a permission change, and it does not belong in this phase.

The ceilings that **must not move** (all verified on the current tree):

| Constant | Value | Where |
|---|---|---|
| `MAX_TURNS` | 12 | `app/src/main/java/com/codeci/ide/ui/ai/AiAgentLoop.kt:29` |
| `MAX_TOOL_CALLS` | 24 | `AiAgentLoop.kt:32` |
| `MAX_IDENTICAL_REPEATS` | 3 | `AiAgentLoop.kt:63` |
| `MAX_READ_CHARS` | 24 000 | `AiProjectFiles.kt:58` |
| `MAX_RESULT_CHARS` | 8 000 | `AiTools.kt:240` |
| `MAX_BATCH_READS` | 8 | `AiTools.kt:237` |
| runs per task | 2 | `AiAgentLimits` (Level 4 owner caps) |
| `MAX_CONTEXT_CHARS` | 12 000 | `AiPolicy.kt` `AiLimits` |

Two constants **do** become user-tunable, because that is what the two controls are:
`MAX_READ_LINES` (`AiTools.kt:234`, currently 400) and `KEEP_LAST_RESULTS`
(`AiAgentLoop.kt:47`, currently 4). Their new ceilings are 400 and 8 — i.e. the tunable
range ends at today's value for reads, and today's value sits at the bottom of the depth
range. Nothing else in the table above is reachable from any control.

## First move: evidence, not code

Every claim below is a `file:line` read on 2026-10-03 against `main` @ `6838ea6`.

### Defect 11 — blanket brevity

`AiContext.kt:466` declares `AGENT_ASK_SYSTEM_INSTRUCTION`. Its final line, `AiContext.kt:474`,
is the unconditional:

```
"Keep answers short: they are read on a phone."
```

`AGENT_EDIT_SYSTEM_INSTRUCTION` (`AiContext.kt:482-483`) is `AGENT_ASK_SYSTEM_INSTRUCTION + " " + …`,
so **both** agent instructions carry it. The instruction is selected by a pure `when` at
`AiContext.kt:100-103` on `(agent, source)`. That `when` is the single place the answer-detail
option has to reach, and because the selected text is disclosed per request (D4), a change of
detail level is visible to the user before Send.

### The two tuning controls, and their coupling

`AiTools.kt:321` and `AiTools.kt:348` show that when the model omits an `end`, the read defaults
to `start + MAX_READ_LINES - 1`; `AiTools.kt:350-351` denies a request above the same constant.
So **today's effective default read window is 400 lines**, not the 150 the Level 10 spec proposes
as a default. See *Open decision 1* below.

Two places must move together with any read-window value, or the cache and the hint will
disagree with the runner:

- `AiTaskMemory.kt:150` computes the cache identity's default end as
  `call.end ?: start + AiToolLimits.MAX_READ_LINES - 1`. If the window becomes tunable and this
  line still uses the constant, the version key no longer describes what was delivered.
- `AiToolRunner.kt:416` renders the follow-up hint
  `"; read ${end + 1}-${minOf(total, end + AiToolLimits.MAX_READ_LINES)} next"`, which tells the
  model a window the runner would then refuse.

`KEEP_LAST_RESULTS` is read at `AiAgentLoop.kt:428` (`if (kept.size >= AiAgentLimits.KEEP_LAST_RESULTS) dropped += step`)
inside the request packer, and Phase 85's restorable-eviction pointers are built from what it
drops. Raising it raises packed size, so it stays bounded at 8 and the pointer budget from
Phase 85 (`MAX_EVICTION_POINTER_CHARS`) is unchanged.

### Task memory already has its internal off path

Level 9 built the switch's mechanics and deliberately left the UI out:

- `AiTaskMemoryStore.kt:18` — `private val persistentEnabled: Boolean = true`
- `AiTaskMemoryStore.kt:27-28` — when off, load returns empty and clears any retained copy
- `AiTaskMemoryStore.kt:76-78` — when off, save clears instead of writing
- `AiTaskMemoryStore.kt:118` — `clearProject(noBackupRoot, projectName)`
- `AiTaskMemoryStore.kt:127` — `clearAll(noBackupRoot)`

Part 87.4 is therefore wiring plus policy, not new storage semantics.

### Where AI options actually live (correction to the spec)

The Level 10 spec's *Touches* line names `SettingsManager.kt` and "Settings UI". **That does not
match this repository.**

- `app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt` is 1 808 lines and has
  **zero** AI rows — `grep -n "AI\|Gemini\|ApiKey\|Bubble"` returns nothing AI-related.
- `app/src/main/java/com/codeci/ide/ui/settings/SettingsManager.kt` has **zero** AI keys.
- The real precedent is a **non-secret property bag inside the encrypted key store**:
  `AiKeyStore.kt:189-197` — `PROP_MODEL`, `PROP_TERMS`, `PROP_ACCEPTED_AT`, `PROP_NVIDIA_MODEL`,
  `PROP_NVIDIA_TERMS`, `PROP_NVIDIA_ACCEPTED_AT`, `PROP_BUBBLE_POS`, `PROP_BUBBLE_SHOW`,
  `PROP_SHEET_OUTPUT`.
- Accessors: `AiKeyStore.kt:49` `setModel`, `:58` `bubble`, `:59` `setBubble`, `:64` `showBubble`,
  `:65` `setShowBubble`, `:70` `outputConflict`, `:77` `acceptedTermsVersion`.
- Each accessor decodes through a **pure policy**: `AiBubblePolicy.decode`,
  `AiSheetPolicy.decodeConflict` (`AiSheetPolicy.kt`, which also carries the
  `AiOutputConflict` two-option pattern).
- Read once on init at `AiViewModel.kt:182-183`; written at `AiViewModel.kt:223-225`.
- The UI is the ✨ rail panel: `EditorScreen.kt:1725-1740` builds `AiHome(…)` with
  `onShowBubbleChange = aiViewModel::setShowBubble`; `AiHome.kt:57` and `:169` declare the
  callback and `AiHome.kt:182` renders `Switch(checked = state.showBubble, …)`.

**Decision:** the nine controls follow that precedent — new `PROP_*` entries beside the existing
ones, one pure `AiOptionsPolicy`, and rows in `AiHome`. `SettingsScreen.kt` is not touched. This
keeps `ui/ai/` self-contained and avoids a second home for AI options.

## The option set, as authorized

| # | Control | Range | Default | Rule | Part |
|---|---|---|---|---|---|
| 1 | Read window (lines per read) | 50–400 | **400** — see *Open decision 1* | S9 | 87.2 |
| 2 | Working-set depth (files held) | 2–8 | 4 | S9 | 87.2 |
| 3 | Task memory | on / off + **Clear now** | on | S4/S5 | 87.4 |
| 4 | Answer detail | brief / normal / thorough | **normal** (owner, 2026-10-03) | S8/D4 disclosure | 87.3 |
| 5 | Tool activity | collapsed / expanded | collapsed | S8 presentation | 87.5 |
| 6 | Request inspection | always on | on — **not user-removable** | D4 | 87.5 |
| 7 | Backup provider | configured / manual switch | off | S8 — **manual offer only** (owner, 2026-10-03) | 87.7 |
| 8 | Read-only reviewer | off / on | off | S7/D1 read-only | 87.8 |
| 9 | Budget extension | offer at cap | offer | S12 | 87.6 |

## Open decision 1 — the read-window default (flagged, not silently chosen)

The spec proposes **150** lines as the default, citing SWE-agent's ~100-line optimum. But
today's behaviour at `AiTools.kt:321,348` is **400**, and the owner chose
*answer detail = normal* on the stated ground that an untouched install should not change
behaviour on upgrade.

Those two answers pull in opposite directions. **Taken as the agent's decision, not the
owner's:** Part 87.2 ships with the default at **400** (unchanged behaviour, range 50–400
available for the owner to lower on the device), and this README records that the spec's 150
is available as a one-constant change if the owner asks for it. Changing it later is not a
migration — it is a default.

## Design shape

One pure policy owns all nine; nothing else may re-decide them.

```kotlin
// ui/ai/AiOptionsPolicy.kt — Android-free, host-tested
enum class AiAnswerDetail { BRIEF, NORMAL, THOROUGH }
enum class AiActivityDisplay { COLLAPSED, EXPANDED }
enum class AiBackupMode { OFF, MANUAL }
enum class AiBudgetOffer { OFFER, NO_OFFER }
enum class AiReviewer { OFF, ON }

data class AiOptions(
    val readWindowLines: Int = AiOptionsPolicy.DEFAULT_READ_WINDOW_LINES,
    val workingSetDepth: Int = AiOptionsPolicy.DEFAULT_WORKING_SET_DEPTH,
    val taskMemory: Boolean = true,
    val answerDetail: AiAnswerDetail = AiAnswerDetail.NORMAL,
    val activity: AiActivityDisplay = AiActivityDisplay.COLLAPSED,
    val backup: AiBackupMode = AiBackupMode.OFF,
    val budgetOffer: AiBudgetOffer = AiBudgetOffer.OFFER,
    val reviewer: AiReviewer = AiReviewer.OFF,
)

object AiOptionsPolicy {
    const val MIN_READ_WINDOW_LINES = 50
    const val DEFAULT_READ_WINDOW_LINES = 400   // today's behaviour — see Open decision 1
    const val MAX_READ_WINDOW_LINES = 400       // == AiToolLimits.MAX_READ_LINES ceiling
    const val MIN_WORKING_SET_DEPTH = 2
    const val DEFAULT_WORKING_SET_DEPTH = 4     // == AiAgentLimits.KEEP_LAST_RESULTS today
    const val MAX_WORKING_SET_DEPTH = 8

    fun clampReadWindow(raw: Int): Int
    fun clampWorkingSetDepth(raw: Int): Int
    fun decode(key: String?, raw: String?): AiOptions
    fun detailSentence(detail: AiAnswerDetail): String
}
```

`decode` must be total and fail-safe: an unreadable, absent, or corrupt property yields the
default, never a crash and never a value outside the clamps. That mirrors
`AiBubblePolicy.decode` and `AiSheetPolicy.decodeConflict`.

## Parts

| Part | Title | What it lands |
|---|---|---|
| [87.1](PART_87_1_OPTION_BOUNDS.md) | Option bounds and storage | `AiOptionsPolicy`, the nine `PROP_*` entries, `AiOptions` in VM state, `AiHome` rows. **No behaviour change.** |
| [87.2](PART_87_2_READ_WINDOW_AND_DEPTH.md) | Read window and working-set depth | Controls 1–2 reach `AiTools`/`AiTaskMemory`/`AiToolRunner`/`AiAgentLoop` consistently. |
| [87.3](PART_87_3_ANSWER_DETAIL.md) | Answer detail | Control 4 replaces the hardcoded brevity line; both agent instructions; disclosed per request. |
| [87.4](PART_87_4_TASK_MEMORY_TOGGLE.md) | Task memory toggle + Clear now | Controls 3, wiring Level 9's existing `persistentEnabled`/`clearProject`. |
| [87.5](PART_87_5_ACTIVITY_AND_INSPECTION.md) | Activity display + non-removable inspection | Controls 5–6; disclosure collapsed, never removed. |
| [87.6](PART_87_6_BUDGET_EXTENSION.md) | Budget extension | Control 9; read-only extension, no extra run or approval. |
| [87.7](PART_87_7_BACKUP_PROVIDER.md) | Backup provider | Control 7; manual offer only, fresh tap + fresh preview. |
| [87.8](PART_87_8_READONLY_REVIEWER.md) | Read-only reviewer | Control 8; off by default, separately triggered, read-only. |

Order matters: **87.1 first** (bounds before behaviour), then 87.2–87.5 in any order, then
87.6–87.8, which each depend on the request-disclosure plumbing 87.3 establishes.

### What actually landed, where it differs from the plan

- **`readWindowChoices()` / `workingSetChoices()` live in `AiOptionsPolicy`, not `AiCopy`.** The
  offered value lists are a policy fact, not copy: a row can no longer present a value outside the
  declared range, and the ceiling test reads the same list the UI does.
- **87.6 needed a real cap object.** The gates read `AiAgentLimits.MAX_TURNS` directly, so
  `AiAgentCaps` was introduced and threaded through `blockModelTurn` / `blockTool` / `blockResume`
  / `toolCallsRemaining` / `withToolCalls` / `AiAgentPolicy.decide` — every parameter defaulting to
  `AiAgentCaps()`, which equals the constants exactly, so no pre-Level-10 caller changed behaviour.
  `runsRemaining()` deliberately takes **no** caps parameter: `AiAgentCaps.runs` is echoed back
  unchanged by `extend()`, so the run cap cannot be threaded even by mistake.
- **87.8 reuses the single-shot stream site rather than adding a fourth.** The reviewer is an
  ordinary `AiSource.REVIEW` prompt with its own system instruction, so `AiLevel9WiringTest`'s
  three-site pin holds untouched. Its text streams into `AiUiState.review`, never into `answer`,
  and the answer under review is snapshotted to `reviewedAnswer` because every preview clears
  `answer`.
- **87.7 freezes provider readiness at Send** (`AiKeyStore.providerReadiness()`) so the offer can
  be decided synchronously on the stop path, then **re-verifies with `store.isReady(next)`** before
  switching. Accepting sets `phase = PREVIEW`; it does not stream.
- **Options are frozen into `AgentSession` at Send**, so a mid-task settings change cannot make the
  disclosed preview describe a different request than the one performed (D4).

## Tests (results, not a plan)

Run on the host harness (`rule.md` §9: `jdk4py` JRE + `npm install kotlin-compiler`, with a
JUnit-shaped shim and an `org.json` stand-in — all in `/tmp`, never in the repository).

| Kind | File | Cases | Result |
|---|---|---|---|
| Pure policy | `AiOptionsPolicyTest` | 16 — clamp at every bound and outside it, decode of absent/corrupt/out-of-range values, `detailSentence` for all three levels | ✅ 16/16 |
| Pure policy | `AiLevel10PoliciesTest` | 18 — activity rows, backup-provider offer and consent, budget-extension offer and bounds, reviewer trigger and markup routing | ✅ 18/18 |
| Ceiling pin | `AiLevel10CeilingTest` | 12 — one case per control at its declared ceiling, **plus** three behavioural cases proving the gates really consult `AiAgentCaps` and that `runs` never moves | ✅ 12/12 |
| Wiring pin | `AiLevel10WiringTest` | 20 — source pins for 87.1–87.8: every `AiHome` row, the brevity line gone from the constants, the read window agreed at all three sites, three `client.stream(` sites, caps at every gate, the reviewer previewed and never an agent | ✅ 20/20 |
| Permission pin | `AiLevel10PermissionTest` | 4 — no Level 10 file writes, runs, streams or holds a path; `ui/ai/` re-scanned whole after ten existing files were edited | ✅ 4/4 |
| Copy | `AiLevel10CopyTest` | 6 — what the new controls *say*: the counter follows the caps, the offer states what it does **not** add, the review request frames the answer as data | ⚠️ CI-only locally |
| Regression | `AiAgentLoopTest` 19 · `AiLevel4WiringTest` 14 · `AiContextBuilderTest` 11 · `AiTaskMemoryTest` 9 · `AiToolRunnerTest` 14 · `AiLevel9WiringTest` 6 · `AiTaskMemoryStoreTest` 5 · `AiLevel8ContextTest` 4 | 82 — existing cases, unchanged | ✅ 82/82 |

**Local total: 152 passed, 0 failed** across these classes. Widened afterwards to every Android-free test class in the repository: **179 classes, 1 594 cases, 0 failures** on Kotlin 2.2.10 (see *The harness lesson this round earned* below).

`AiLevel10CopyTest` is the one file this sandbox cannot compile: `AiCopy.kt` transitively needs
`AiEditApplier → GitDiscardEditors → GitDiscardPolicy → GitManager → ProjectsHub`, and that chain
pulls in Compose-coupled sources. The test ships and runs in CI; it is marked here rather than
counted as a pass. Two existing pins needed updating because the signatures they pinned genuinely
changed, and both were strengthened rather than weakened:

- `AiLevel4WiringTest` pinned `blockModelTurn(nowMs: Long)`; it now pins
  `blockModelTurn(nowMs: Long, caps: AiAgentCaps = AiAgentCaps())`, which also pins the default.
- `AiLevel10WiringTest`'s reviewer case originally asserted the ViewModel does **not** call
  `AiReviewerPolicy.parse` — wrong from the start, since that function *is* the display router.
  It now asserts the real invariant behaviourally: the reviewer's instruction contains none of the
  three protocol markers, and its reply is classified, never executed.

### CI round 1 — red, two real compile errors, both fixed

Build APK run [`37118225659`](https://github.com/pabi277/CodeC/actions/runs/37118225659) on
`ef97bbf` **failed** at `:app:compileDebugKotlin`. Both errors were real and both were invisible to
the host harness, for one reason: **the harness ran Kotlin 2.4.20 while the project builds with
Kotlin 2.2.10** (`gradle/libs.versions.toml:14`).

| Error | Cause | Fix |
|---|---|---|
| `AiViewModel.kt:407` *Unresolved reference 'copy'* | `dropLiveTaskMemory()` called `it.copy(memory = …)` on `AgentSession`, which is a plain `private class`, not a `data class` — it has no `copy`. | Assign the mutable field: `agent?.memory = AiTaskMemory.EMPTY`. Also more correct: copying would have discarded every other `var` the loop is mid-way through using. |
| `AiOptionsPolicy.kt:145,148,150,152,154` *NewConstraintError … T == AiAnswerDetail? <!: Enum<T>* | `decodeEnum<T : Enum<T>>(raw) ?: AiAnswerDetail.NORMAL` — Kotlin 2.2.10 cannot fix `T` from the elvis right-hand side. 2.4 infers it fine. | Spell the type argument at all five call sites: `decodeEnum<AiAnswerDetail>(raw)`. |

After the fixes, the whole harness was **rebuilt on `kotlin-compiler@2.2.10`** — the project's own
version, installed via `npm install kotlin-compiler@2.2.10` — and re-run: **152/152 green on
2.2.10**. The harness version mismatch is now recorded in `rule.md` §9 and `TROUBLESHOOTING.md`;
a host harness that is not on the project's Kotlin version can pass code the app cannot build.

### CI round 2 — red on two pre-existing pins, both fixed; local coverage widened

Build APK run [`37118776411`](https://github.com/pabi277/CodeC/actions/runs/37118776411) on
`35d72b1` **compiled cleanly** and reached `:app:testDebugUnitTest` — **3 120 tests, 2 failed**, both
in the pre-existing `AiLevel7WiringTest`:

| Pin | Why it broke | Fix |
|---|---|---|
| `resumeAgentOrStop gates on the combined turn and tool budget` | asserted the literal `fun blockResume(nowMs: Long)`; 87.6 added the `caps` parameter. | Pins `fun blockResume(nowMs: Long, caps: AiAgentCaps = AiAgentCaps())` **and** adds `blockModelTurn(nowMs, caps) ?: blockTool(nowMs, caps)`, so the pin now also proves both gates are still consulted in order. |
| `refusals are counted on a separate clamped counter` | asserted `coerceIn(0, AiAgentLimits.MAX_TOOL_CALLS)`; the clamp now reads the task's cap. | Pins `coerceIn(0, caps.toolCalls)` **and** asserts `AiAgentCaps().toolCalls == MAX_TOOL_CALLS`, so an unextended task is provably still clamped at 24. |

Both were strengthened, not loosened: each now pins the defaulted signature *and* the guarantee the
original pin was really about.

### The harness lesson this round earned

Two separate harness defects let broken code look green, and both are now guarded against:

1. **Wrong compiler version** (round 1): the harness ran Kotlin 2.4.20; the project builds 2.2.10.
   The harness is now `kotlin-compiler@2.2.10`.
2. **Iterative file-dropping hides a broken file.** The harness compiles the Android-free set,
   drops whatever fails, and repeats. When the `AiLevel7WiringTest` fix referenced
   `AiAgentLimits` without qualifying it, the file was silently dropped in round 1 — it did not
   fail, it *vanished*, and the run still reported zero failures. **After any sweep, check that
   every file you edited is present in the compiled set.** That check is what caught this.

Local coverage was then widened from 13 hand-picked classes to **every Android-free test class**:
**179 classes, 1 594 cases, 0 failures** on Kotlin 2.2.10. One earlier failure in `RunArtifactsTest`
turned out to be a defect in the harness's own `TemporaryFolder` stand-in (`newFolder()` with no
arguments returned the root instead of a new subfolder); fixed in the shim, and CI had passed that
test all along. `AiLevel10CopyTest` remains CI-only for the `AiCopy.kt` dependency reason above.

### Two real defects the pins caught while being written

1. **`AiAgentPolicy.decide` was called without `caps`.** The gate inside `decide` would have kept
   using the plain constants, so an accepted budget extension would have bought nothing at all —
   the loop would still stop at 24 reads. Caught by the `caps = session.caps` pin.
2. **`AiCopy.agentUsageLine` clamped against the constants.** After an extension it would have
   rendered "24 of 24 reads" for 30 real reads — the Phase 84 defect-3 counter lie in a new
   costume. Fixed by threading `turnCap`/`readCap` through `AiAgentUsage`.

## Exit condition

- [x] Every ceiling asserted by a host test at its declared bound (**S9**).
- [x] No option alters D1/D5 tool, write, run or path permissions (`AiLevel10PermissionTest`).
- [x] The request-inspection row cannot be turned off (no `PROP_`, no setter; pinned).
- [x] The backup provider never switches without a fresh tap and a fresh preview (**S8**).
- [x] *Clear now* deletes the project's task memory and reports the store's real result.
- [x] Answer detail visibly changes the system instruction shown in the disclosed request.
- [x] Budget extension never increases the run count or grants an extra approval (**S12**).
- [x] Exactly three `client.stream(` call sites remain (asserted three times over).
- [x] `ui/ai/` still has zero direct project writes and zero command execution (**S6**).
- [x] No new dependency, Android permission, endpoint or default-model change (manifest pinned at 14).
- [x] minSdk 24 respected: `java.io` only, no `java.nio.file` in production sources.
- [ ] Settings export / feedback scrubbing still redacts every credential shape — **not re-run
      here**; Level 10 stores no secrets (the eight new keys are ints, enums and booleans in the
      existing non-secret property bag), but this was not re-verified on a device.
- [ ] **Build APK on CI green** — round 1 (`37118225659` on `ef97bbf`) **red** at `:app:compileDebugKotlin` for the two errors above; round 2 (`37118776411` on `35d72b1`) compiled cleanly and reached the tests — 3 120 tests, 2 failed, both pre-existing `AiLevel7WiringTest` pins broken by the signature changes, both fixed; round 3 pending at the time of writing.
- [ ] **Device acceptance** — POSTPONED to Level 12 by standing owner decision, not claimed here.

## Deferred / rejected with reasons

- **Placing the controls in `SettingsScreen.kt`** — rejected; the repository's AI options live in
  `AiHome` and `AiKeyStore`'s property bag, and a second home for them would fork the pattern.
- **Automatic provider fallback** — rejected by the owner (2026-10-03) and by S8/D4: a provider
  switch is a new data recipient and needs fresh consent, not a replayed one.
- **Raising any cap above the table** — rejected; S9 exists because the owner's instinct
  ("bigger is better") is what SWE-agent's ablation measured against (full-file 12.7 % vs a
  100-line window 18.0 %).
- **A new DataStore file for options** — rejected; `AiKeyStore`'s existing non-secret property
  bag is already backup-excluded and already tested.
- **Parallel review agents** — rejected; S7, one brain writes. The reviewer in 87.8 is
  read-only and separately triggered, never a second writer.

## Sources (record)

1. SWE-agent, NeurIPS 2024, ablation Table 3 — bounded beats unbounded — https://arxiv.org/abs/2405.15793 (cited from the Level 10 spec; not re-fetched this session)
2. Anthropic, *Building Effective Agents* — https://www.anthropic.com/research/building-effective-agents (as above)
3. Manus, *Context Engineering for AI Agents* — https://medium.com/@peakji/context-engineering-for-ai-agents-lessons-from-building-manus-71883f0a67f2 (as above)
4. Repository: every `file:line` above, read 2026-10-03 against `main` @ `6838ea6`.

## Gate

**No PR, no merge, no push to `main` without the owner's explicit command** (`rule.md` §3).
Level 10's authorization covers this phase; **Levels 11–14 remain unauthorized.** Formal device
acceptance remains POSTPONED to Level 12 and is not claimed here.
