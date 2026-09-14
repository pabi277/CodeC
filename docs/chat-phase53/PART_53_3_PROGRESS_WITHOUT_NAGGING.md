# CodeC Phase 53.3 — The streak the app already counts, shown once

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S ·
> **Owner row (verbatim):** *"not attractive to user to use multiple time"*.
> Parent: [`README.md`](README.md) ·
> [`PHASE51_53_ROADMAP.md`](../PHASE51_53_ROADMAP.md).
>
> **This part is deliberately the smallest thing that could work.** It adds no
> counter, no key, no network and no new screen. It takes numbers the app has
> been storing and never reading, and puts them where a curious user already
> looks.

## First move: evidence, not code

```text
$ sed -n '17,31p' app/src/main/java/com/codeci/ide/ui/stats/StatsManager.kt
  val TOTAL_RUNS         = intPreferencesKey("stats_total_runs")
  val TOTAL_FILES_CREATED= intPreferencesKey("stats_total_files_created")
  val LAST_RUN_DATE      = stringPreferencesKey("stats_last_run_date")
  val CURRENT_STREAK     = intPreferencesKey("stats_current_streak")
  fun today(): String …      fun yesterday(): String …

$ sed -n '39,52p' app/src/main/java/com/codeci/ide/ui/stats/StatsManager.kt
  incrementRuns():  last == null → 1 · last == today → keep
                    last == yesterday → streak + 1 · else → 1

$ grep -rn "totalRunsFlow\|totalFilesCreatedFlow\|currentStreakFlow\|lastRunDateFlow" \
        app/src/main/java --include=*.kt | grep -v "ui/stats/StatsManager.kt"
  (no matches)
```

Six call sites **write** the counters
(`MainActivity.kt:1383`, `EditorViewModel.kt:3149,3329`,
`FileManagerViewModel.kt:276,347`); **nothing reads them**. The app has been
quietly counting a daily streak, in a correct implementation, for the whole
life of the feature — and the user has never seen a digit of it.

That is the cheapest available "reason to come back" in the codebase: no new
data, no new storage, no new risk. It is also the one place where a retention
feature can go badly wrong, so the design below is mostly **prohibitions**.

### The laws it must obey

| Law | Source |
|---|---|
| No notification, no push, no badge, ever | `rule.md`: no telemetry / no new permission |
| No modal nag; nothing that interrupts a session | Phases 41, 42, 45 |
| No new DataStore key, no new counter, no network | `rule.md` + this series' cost table |
| Never mention a **broken** streak | decency, and the no-nag law |
| Read-only: this part may **not** change how the streak is computed | the implementation is correct; touching it would need its own tests and its own row |

## Design

### A — The About line (the whole feature)

Settings → **About** gains one row under the existing build/version block:

```text
3 days in a row · 41 runs · 12 files created
```

- All four numbers come from the existing `StatsManager` flows — **nothing new
  is stored or computed**.
- If the user has run nothing yet, the row is **absent** (not "0 days in a row"
  — a zero is a nag).
- Formatting is pluralised and lives in `strings.xml`.

```kotlin
data class StreakFacts(val streak: Int, val runs: Int, val files: Int,
                       val streakBroken: Boolean)
object StreakLine {
    /** null = show nothing. Never a nag, never a guilt trip. */
    fun forAbout(f: StreakFacts): StreakLineText?
    /** The quiet hub line; null on most days by design (see below). */
    fun forHub(f: StreakFacts): StreakLineText?
}
```

### B — The hub line (optional, and only on a *continuing* day)

One quiet sentence on the hub when the streak **continues** —
*"Day 3 with CodeC."* — and **nothing at all** when it does not. No "you lost
your streak", no flame emoji, no exclamation mark. `forHub` returns `null`
unless `streak >= 2 && !streakBroken`.

### C — What is explicitly not built

- No streak **screen**. No badges, levels, goals or rewards.
- No **reminder** of any kind — the day the user does not open CodeC, CodeC
  says nothing. (Every retention source in the dossier warns that
  over-notifying lifts short-horizon retention and drives uninstalls; this repo
  additionally forbids notifications.)
- No change to `StatsManager.incrementRuns` — the today/yesterday rule is
  correct and stays.

## The Android edge

- `StatsManager` uses `context.dataStore` (DataStore Preferences) with flows;
  the Settings screen already collects `themeManager.*Flow` the same way
  (`SettingsScreen.kt:129-131`), so the wiring is a `collectAsState` next to
  the existing ones.
- The About card is inside `SettingsScreen` (1,577 lines) — this part adds
  **one** row composable and **one** string, and it is converted to 51's tokens
  like the rest of the screen.
- No `LaunchedEffect` that writes: this part is read-only, and a source-scan
  test pins that (`grep` for `increment` inside the new composable's file must
  find only the pre-existing six call sites).

## Exit condition

`StreakLine.forAbout` / `forHub` are pinned (including every `null` case); the
About row renders the four existing numbers and is **absent** when they are all
zero; the hub line appears only on a continuing day ≥2 and never mentions a
break; a source scan proves no new DataStore key, no write, no notification and
no dialog were added; and `DEVICE_ROUND.md` R9-R10 has been run by the owner.

## Tests (plan)

- `StreakLineTest` (~10): the four numbers format with correct plurals;
  all-zero → `null`; streak 1 → the singular wording; `forHub` is `null` for
  streak 1, for a broken streak, and for streak 0; a broken streak is never
  named in either output.
- `AboutStatsTest` (~5, source scan): the About composable reads the
  `StatsManager` flows; it contains no `increment*`, no `edit(`, no
  `DataStore` key literal of its own; the strings come from `strings.xml`.
- `NoNagTest` (~4, source scan): this part adds **zero** `AlertDialog`,
  `NotificationManager`, `NotificationCompat`, `Toast` or `Snackbar`
  call sites.

≈19 cases, host-JVM. Small part, small test budget.

## Sources (record)

- `PHASE51_53_UX_RESEARCH.md` §2.5 (the finding), §3.6 (retention benchmarks
  and the explicit warning against over-notifying), D3 (no telemetry).
- `ui/stats/StatsManager.kt` and `ui/screens/SettingsScreen.kt:129-131` reads
  (2026-09-13, `main` @ `62cfe7b`).
- Phases 41/42/45 — the no-nag and no-interruption laws.

## Deferred / rejected with reasons

- **Notifications / daily reminder** — forbidden; rejected on principle and by
  rule.
- **A streak screen with badges** — inventing a reward system the owner never
  asked for, and a nag by another name.
- **Showing the numbers on the hub permanently** — a permanent counter is a
  permanent distraction; the About row is where a curious user looks.
- **Fixing/rounding the streak across time zones** — the existing rule uses
  local dates (`SimpleDateFormat("yyyy-MM-dd", Locale.US)` + `Calendar`), which
  is the right behaviour for a phone; changing it would break the stored data.
