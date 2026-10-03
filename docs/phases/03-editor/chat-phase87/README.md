# Phase 87 — AI Level 10: agent controls and options

> **Status:** 📋 **PLANNED.** This is the brief. No production or test source has been
> written for this phase. No PR, no merge.
> **Owner authorization (2026-10-03):** the owner authorized Level 10 in chat by selecting
> *"Authorize Level 10 (agent controls/options)"* and then answered four design questions:
> **all nine** controls in Phase 87 · controls live in the **`AiHome` AI panel** · backup
> provider is **manual offer only** · answer detail **defaults to `normal`** (today's wording).
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

## Tests (plan)

| Kind | File | Cases |
|---|---|---|
| Pure policy | `AiOptionsPolicyTest` | 16 — clamp at every bound and outside it, decode of absent/corrupt/out-of-range values, `detailSentence` for all three levels |
| Ceiling pin | `AiLevel10CeilingTest` | 9 — one case per control asserting its declared ceiling, plus that no option touches `MAX_TURNS`/`MAX_TOOL_CALLS`/`MAX_BATCH_READS`/`MAX_READ_CHARS`/run count |
| Wiring pin | `AiLevel10WiringTest` | 8 — source pins: `AiHome` renders every row; `AiContext`'s brevity line is gone from the constants; `AiTools`/`AiTaskMemory`/`AiToolRunner` read the same window value; `AiTaskMemoryStore` is still the only task-memory writer |
| Permission pin | `AiLevel10PermissionTest` | 4 — no option path reaches a project write, a command, or a path outside the project |
| Regression | `AiLevel8BatchTest`, `AiLevel8ContextTest`, `AiLevel9WiringTest`, `AiToolRunnerTest` | existing cases must stay green unchanged |

Counts are a plan, not a result. Nothing has been run for this phase.

## Exit condition

- [ ] Every ceiling asserted by a host test at its declared bound (**S9**).
- [ ] No option alters D1/D5 tool, write, run or path permissions.
- [ ] The request-inspection row cannot be turned off.
- [ ] The backup provider never switches without a fresh tap and a fresh preview (**S8**).
- [ ] *Clear now* deletes the project's task memory and is reflected immediately (**S4/S5**).
- [ ] Answer detail visibly changes the system instruction shown in the disclosed request.
- [ ] Budget extension never increases the run count or grants an extra approval (**S12**).
- [ ] Exactly three `client.stream(` call sites remain.
- [ ] `ui/ai/` still has zero direct project writes and zero command execution (**S6**).
- [ ] No new dependency, Android permission, endpoint or default-model change.
- [ ] minSdk 24 respected: `java.io` only, `canonicalPath != absolutePath` for symlinks.
- [ ] Settings export / feedback scrubbing still redacts every credential shape.

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
