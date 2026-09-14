# CodeC Phase 53 — The return: it remembers you, and it never feels slow

> **Status:** 📋 **PLANNED — no app code.** · **Cost:** `[client-only]` ·
> **Effort:** L · **Owner row (verbatim):** *"not attractive to user to use
> multiple time"* — the **return** half (the *look* half is 51, the *surfaces*
> half is 52).
> Parent: [`PHASE51_53_ROADMAP.md`](../PHASE51_53_ROADMAP.md). Research dossier:
> [`PHASE51_53_UX_RESEARCH.md`](../PHASE51_53_UX_RESEARCH.md).
> **Depends on:** 51 (design language) and 52 (surfaces).

```text
  53.1  Continuity: resume you can see, and decline
  53.2  Perceived speed: first paint, jank budget, skeletons instead of blanks
  53.3  Progress without nagging: the streak the app already counts, shown once
  53.4  The proof: Roborazzi screenshot goldens + the look-and-feel device round
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [53.1](PART_53_1_RESUME.md) | "Continue where you left off", visibly | M | 📋 PLANNED |
| [53.2](PART_53_2_PERCEIVED_SPEED.md) | Jank budget + measured first paint | M | 📋 PLANNED |
| [53.3](PART_53_3_PROGRESS_WITHOUT_NAGGING.md) | The streak in About, once per day | S | 📋 PLANNED |
| [53.4](PART_53_4_PROOF.md) | Screenshot goldens + device round | M | 📋 PLANNED |

Device round: [`DEVICE_ROUND.md`](DEVICE_ROUND.md) (R1-R12, written, not run).

---

## The evidence: the app already counts the habit and shows the user nothing

Reads on **`main` @ `62cfe7b`, 2026-09-13** — this is the most surprising
finding of the whole research pass.

```text
ui/stats/StatsManager.kt:17-31
  val TOTAL_RUNS = intPreferencesKey("stats_total_runs")
  val TOTAL_FILES_CREATED = intPreferencesKey("stats_total_files_created")
  val LAST_RUN_DATE  = stringPreferencesKey("stats_last_run_date")
  val CURRENT_STREAK = intPreferencesKey("stats_current_streak")
  fun today(): String …   fun yesterday(): String …

ui/stats/StatsManager.kt:39-52   incrementRuns(): a correct streak rule
                                 (last == null → 1 · last == today → keep ·
                                  last == yesterday → streak + 1 · else → 1)
```

**Six call sites increment these counters** (`MainActivity.kt:1383`,
`EditorViewModel.kt:3149,3329`, `FileManagerViewModel.kt:276,347`) and
**nothing reads them**:

```text
$ grep -rn "totalRunsFlow\|totalFilesCreatedFlow\|currentStreakFlow\|lastRunDateFlow" \
        app/src/main/java --include=*.kt | grep -v "ui/stats/StatsManager.kt"
  (no matches)
```

The second finding is the same shape:

```text
ui/projects/EditorLaunchState.kt:11-45   save(project, file) + load(context)
                                         (validated against the disk; stale → null)
MainActivity.kt:807                       start destination = EditorLaunchState.load(activity)
```

Resume **works** — and it is completely invisible. The user is dropped straight
into a file with no breadcrumb, no "you were here", and no way to say *"not
now, take me to my projects"*. A silent jump is the kindest reading; the
unkind one is that it feels like the app opened somewhere random.

So 53 is unusually cheap: **the data is already there, the policy is already
there, and the only work is to show it — once, quietly, and never as a nag.**

### Why this is the "100×" you can actually measure

- Median retention: **D1 ≈ 25-27%, D7 ≈ 9-13%, D30 ≈ 4-6%**; users who complete
  onboarding retain **2-3× higher**; a meaningful first action **within 3
  minutes** materially lifts D7 (dossier §3.6).
- The diagnostic shapes: a **D1 cliff** = first-session failure (→ 52.1, 53.1);
  a **steep D1→D7 fall** = *"the second and third sessions were not compelling
  enough to create return behaviour"* (→ 53.1, 53.2, 53.3).
- And the measured instruments are **already in the repo**: the `bench` APK has
  `FrameStats`/`FrameCapture`; Roborazzi 1.59.0 is declared, plugin-applied and
  has **zero** tests.

---

## Design, in one page

### 53.1 — Continuity you can see (and decline)

A pure `ResumePolicy` decides, from facts the app already has, **what the first
screen offers**:

```kotlin
data class ResumeFacts(val lastProject: String?, val lastFile: String?,
                       val stillExists: Boolean, val tabCount: Int,
                       val minutesSinceLastOpen: Long?, val crashedLastTime: Boolean)
enum class ResumeOffer { CONTINUE_IN_PLACE, OFFER_CARD, HUB }
object ResumePolicy { fun offerFor(f: ResumeFacts): ResumeOffer }
```

- **CONTINUE_IN_PLACE** (today's silent jump) only when the user left seconds
  ago or the app was killed mid-session — the case where jumping back is
  obviously right.
- **OFFER_CARD** — the hub's top card: *"Pick up where you left off —
  `demo_flask/app.py`"*, with **Continue** and **✕** (decline → hub; the ✕ is
  remembered for that session only, never as a setting).
- **HUB** when nothing is resumable or the project is gone (today's stale-entry
  fallback already handles the disk half at `EditorLaunchState.kt:31-45`).

**Law:** the app never *silently* navigates somewhere the user did not ask for
when they have been away longer than a short window; and a declined offer is
never asked twice in the same session (the no-nag law).

### 53.2 — Perceived speed

- **First paint measured**, not guessed: the owner's stopwatch on a cold start
  (row R4) plus the splash-leave timestamp 52.1 already produces. The number
  goes in this part doc, with the device and OS.
- **Jank budget** with the existing `bench` APK (`FrameStats`,
  `FrameCapture`): scroll a 5,000-line file, type for 30 s, switch tabs —
  record dropped frames **before** and **after**, and publish the table. If the
  bench needs a new candidate file, that is code in `bench/`, which ships as a
  **separate APK** and adds **zero** bytes to `:app`.
- **Skeletons instead of blanks** everywhere a list takes more than one frame
  (52.3 covers the hub; this part covers the file tree, the git screens and the
  package list).

### 53.3 — Progress without nagging

The streak already exists. Show it **once per day**, in a place the user goes
to look, never as a dialog:

- Settings → About gains one line: *"3 days in a row · 41 runs · 12 files"*
  (all four numbers come from `StatsManager` — **no new DataStore key, no new
  counter, no network**).
- Optionally one quiet line on the hub when a streak *continues* (never when it
  breaks — no guilt, no "you lost your streak"). Pure
  `StreakLine.forToday(streak, runs, files, brokenYesterday): String?`.
- **Never** a notification, never a badge, never a dialog. This part is written
  specifically to be the *opposite* of a retention-nag feature.

### 53.4 — The proof

- **Roborazzi goldens** for the twelve states this series changed (welcome,
  hub empty, hub list, editor chrome with RUN ▶, editor empty, output open,
  packages row in all four states, terminal chrome, Settings appearance,
  dark + light). Declared, plugin-applied, unused until now — **no new
  dependency**. Verify mode must be wired in CI (Phase 50's README recorded
  that CI today would *record* rather than *compare*).
- **The device round** R1-R12, whose results are pasted into the owning part
  docs, plus a **final walkthrough**: fresh install → first run → write → RUN →
  close → reopen the next day — the whole journey the owner judges.

---

## What this phase must NOT do

- **No telemetry, no analytics, no network.** Everything here is on-device state
  the app already stores (`rule.md`'s standing "no new dependency / DataStore
  key / permission / telemetry").
- **No notification, badge, or streak-loss message** (no-nag law, Phases
  41/42/45).
- **No change to what resume *does*** today — only that it becomes visible and
  decline-able. Phase 49's back behaviour and the launch divert (Phase 44) stay.
- **No change to the editor's text engine** for performance; 48.1's
  `IncrementalEdit` is the current answer and is measured, not rewritten.

## Exit condition

`ResumePolicy` is pinned case by case; the resume card renders and can be
declined; first-paint and jank numbers are **recorded in this part doc** with
the device that produced them; the About line shows the four existing counters;
≥12 Roborazzi goldens run in **verify** mode on CI; and `DEVICE_ROUND.md` R1-R12
has been run by the owner.

## Tests (plan)

| File | Cases | Pins |
|---|---|---|
| `ResumePolicyTest` | ~14 | in-place vs card vs hub; stale project; long absence; crash-last-time; declined twice |
| `ResumeWiringTest` (source scan) | ~7 | the hub renders the card; decline is session-only; `EditorLaunchState` remains the single source |
| `StreakLineTest` | ~10 | the four numbers format; a broken streak is never mentioned; null when there is nothing yet |
| `AboutStatsTest` (source scan) | ~5 | the About row reads `StatsManager`; no new DataStore key is introduced |
| Roborazzi `ChromeScreenshotTest` | 12 | one golden per state, dark + light where it matters |

≈48 cases + 12 goldens.

## Sources (record)

Dossier §2.5 (the StatsManager and EditorLaunchState findings), §2.6 (bench +
Roborazzi already exist), §3.6 (retention benchmarks and the diagnostic
shapes), §4 (no new dependency), D3 (no telemetry).

## Deferred / rejected with reasons

- **Notifications / re-engagement pushes** — forbidden by the repo's
  no-telemetry and no-nag laws; explicitly rejected here.
- **A "streak" gamification screen (badges, levels)** — a nag by another name,
  and it invent incentives the owner has not asked for.
- **Cloud backup of the session state** — a server, a permission and an account;
  a different owner row entirely.
- **Rewriting the editor for speed** — 48.1 already measured and fixed the
  worst path; 53.2 measures before it tunes anything else.
