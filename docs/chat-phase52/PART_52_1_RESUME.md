# CodeC Phase 52.1 — "Continue where you left off", visibly

> **Status:** 🚧 IMPLEMENTED (host-verified; handset round pending) · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"not attractive to user to use multiple time"*.
> Parent: [`README.md`](README.md) ·
> [`PHASE50_52_ROADMAP.md`](../PHASE50_52_ROADMAP.md).

## First move: evidence, not code

```text
ui/projects/EditorLaunchState.kt:11-45
  object EditorLaunchState {
      fun save(context, projectName, fileName)          // written on every open
      fun load(context): State?  {
          … ProjectManager.project(project) ?: return null      // stale → null
          … ProjectPathUtils.resolveInside(info.root, file) ?: return null
          return if (target.isFile) State(info.name, file) else null
      }
  }

MainActivity.kt:807
  if (com.codeci.ide.ui.crash.SafeMode.active) null else EditorLaunchState.load(activity)
```

The resume machinery is **correct** — it validates against the disk, handles a
deleted project, and is skipped in safe mode. What it is not is *visible*: the
start destination is computed, the user is dropped into a file, and nothing on
screen says *why they are here* or offers the other door.

That is the whole bug behind half of "not attractive to use multiple time": the
**second** visit is the one that decides D7 (dossier §3.6 — the diagnostic shape
*"came back once, but did not build a habit"*), and today the second visit
begins with an unexplained jump.

There is also a **conflict to respect, not to fix**:

| Existing behaviour | Owner | Must survive |
|---|---|---|
| `SetupGatePolicy.startOnTerminal(...)` decides the launch tab from the disk (Phase 44, after a round-1 device bug) | setup must be watchable | ✅ unchanged |
| `EditorLaunchState` decides the start destination (2026-08-31) | resume | ✅ unchanged in *what* it decides |
| First-run welcome (`WelcomeScreen`, Phase 33.1) | three tiles | ✅ wins over both |
| Safe mode / crash overlay | safety | ✅ wins over everything |

52.1 adds a **door**, not a new destination: the same resume, now announced and
decline-able.

## Design

```kotlin
data class ResumeFacts(
    val lastProject: String?, val lastFile: String?,
    val stillExists: Boolean,             // EditorLaunchState.load != null
    val tabCount: Int,
    val minutesSinceLastOpen: Long?,      // null = unknown / first run
    val crashedLastTime: Boolean,
    val welcomePending: Boolean,
    val setupNeedsWatching: Boolean,      // Phase 44's divert
    val safeMode: Boolean,
)
enum class ResumeOffer { CONTINUE_IN_PLACE, OFFER_CARD, HUB, NONE }
object ResumePolicy {
    fun offerFor(f: ResumeFacts): ResumeOffer = when {
        f.safeMode || f.welcomePending        -> ResumeOffer.NONE
        f.setupNeedsWatching                  -> ResumeOffer.NONE   // 44 wins
        !f.stillExists                        -> ResumeOffer.HUB
        f.crashedLastTime                     -> ResumeOffer.OFFER_CARD
        (f.minutesSinceLastOpen ?: Long.MAX_VALUE) <= RESUME_WINDOW_MIN ->
                                                 ResumeOffer.CONTINUE_IN_PLACE
        else                                  -> ResumeOffer.OFFER_CARD
    }
    const val RESUME_WINDOW_MIN = 5L
}
```

- **`CONTINUE_IN_PLACE`** = today's behaviour, kept for the case where it is
  obviously right: the user was here a minute ago (a tab switch, a phone call,
  an app kill) and would be annoyed by a question.
- **`OFFER_CARD`** = the hub's first card: the file's real path (`~proj/…`, the
  Phase 46.2 spelling), two actions (**Continue** / **✕**), and a one-line
  "where you were". It renders **above** the project list, inside the hub the
  user already knows.
- **`HUB`** = nothing resumable: exactly today's stale-entry fallback.
- **`NONE`** = safe mode, first run, or setup is being watched — the existing
  decisions, untouched.

Declining (✕) sets a **session-only** flag (a `mutableStateOf` in the activity,
**no** stored preference, **no** new DataStore key). It is never asked twice in
one session — the no-nag law (Phases 41/42/45).

**Nothing about navigation changes:** no new route, no new screen, no change to
back (Phase 49), no change to the launch divert (Phase 44). The card lives in
`FileManagerScreen`, whose hub layout 51.3 is already converting to tokens.

## The Android edge

- `minutesSinceLastOpen` needs a timestamp the app does not store today.
  **Cheapest honest option:** derive it from the launcher state write —
  `EditorLaunchState.save` already runs on every open; adding a `lastOpenedAt`
  to the **same** `SharedPreferences` file is one `putLong` in one place (the
  file already exists; no new store, no migration). If the value is missing
  (an older install), `minutesSinceLastOpen == null` → `OFFER_CARD`, which is
  the safe, visible default.
- `crashedLastTime` comes from the existing `StartupLedger` /
  `CrashLog` (`ui/crash/`) — if neither reports a crash, it is false. No new
  crash plumbing.
- The card uses 51's tokens and 50.4's motion for its appear/disappear.

## Exit condition

`ResumePolicy.offerFor` is pinned for every branch (including the two `NONE`
cases and the missing-timestamp default); the card renders on the hub with the
real path and two actions; **✕** is session-only and not asked again; the
first-run welcome and Phase 44's setup divert still win; and `DEVICE_ROUND.md`
R1-R4 has been run by the owner.

## Implementation record (Phase 52 start)

- `ResumePolicy` is a pure host-testable policy with all four offers, the
  missing-timestamp default and the five-minute boundary.
- `EditorLaunchState` stores `last_opened_at` beside the existing project/file
  pair; older installs deliberately receive the visible card rather than a
  silent jump.
- `MainActivity` keeps setup/welcome/safe mode precedence, and the hub card is
  session-only: Continue and ✕ both consume it without writing a preference.
- The owner handset rows R1-R4 are **not run in this sandbox**; they require the
  phase APK and the owner's device.

## Tests (plan)

- `ResumePolicyTest` (~14): in-place inside the window; card outside it; card
  after a crash; hub when the project is gone; `NONE` in safe mode, on first
  run, and while setup needs watching; missing timestamp → card; the window
  boundary (5 min, 5 min + 1 s).
- `ResumeWiringTest` (~7, source scan): the hub renders the card from
  `ResumePolicy`; the ✕ writes **no** stored preference (grep the file for
  `edit(`/`DataStore` writes); `EditorLaunchState` remains the single source of
  *what* is resumable; `MainActivity` still applies Phase 44's divert first.
- `ResumeCardCopyTest` (~5, source scan + copy pins): the card shows the
  project-relative path; it has exactly two actions; the strings live in
  `strings.xml`.

≈26 cases, host-JVM.

## Sources (record)

- `PHASE50_52_UX_RESEARCH.md` §2.5 (the finding), §3.6 (D7 and the "no habit"
  diagnostic shape).
- `ui/projects/EditorLaunchState.kt`, `MainActivity.kt:807`,
  `ui/crash/StartupLedger.kt` reads (2026-09-13, `main` @ `62cfe7b`).
- Phases 33.1 (welcome), 44 (`SetupGatePolicy.startOnTerminal`), 46.2 (the
  `~proj/…` path spelling), 49 (back), 41/42/45 (no-nag).

## Deferred / rejected with reasons

- **A "recent files" list** — a new data store and a new decision about what
  counts as recent; the single last-file card answers 90% of it for 10% of the
  risk. A later row if the owner asks.
- **App shortcuts (long-press the icon → last file)** — tempting and
  dependency-free, but it is a *launcher* surface this repo has never tested;
  keep it out of a phase whose exit condition is already device-heavy.
- **Making the resume window a setting** — one more switch for something with a
  correct default; rejected (the same reasoning as 51.4's single switch).
- **Restoring the caret position** — sora + `EditorViewModel` own that; today
  the file opens at the top and no owner row asks for more.
