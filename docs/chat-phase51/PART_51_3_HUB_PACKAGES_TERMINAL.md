# CodeC Phase 51.3 — Hub, Packages and Terminal surfaces

> **Status:** 🚧 IMPLEMENTED (2026-09-21, `arena/01a0c4cb-codec`) — device round
> F9-F13 NOT run · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"it's not attractive to user to use multiple time"*.
> Parent: [`README.md`](README.md) ·
> [`PHASE50_52_ROADMAP.md`](../PHASE50_52_ROADMAP.md).

## First move: evidence, not code

```text
$ grep -n "EmptyProjectsState\|ProjectHubCard(" app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt
  1223  if (entries.isEmpty()) { EmptyProjectsState(onCreate, onStarter) }
  1270  ProjectHubCard(entry = entry, onAction = onCardAction)
  1351  private fun ProjectHubCard(…

$ grep -rn "empty_project\|empty_project_hint\|branch_switch_empty\|git_diff_empty" app/src/main/res/values/strings.xml
  206  This project is empty
  207  Add a file or folder, or import files from storage.
  331  No textual differences (binary file, mode change, or empty diff).
  442  This repository has no branches yet.
```

Two things stand out. **First:** empty states in this app are **one sentence of
text**, with no mark, no action emphasis, no hierarchy. That is the moment a
user is most uncertain — and it is the moment with the least design.
**Second:** the hub's project card (`ProjectHubCard`, 1,830-line file) shows the
project's icon view (`ProjectIconView`) and its ⋮ menu, but the *shelf* itself
has no rhythm: rows are built from the same 801 raw dp literals as the rest of
the app (50.1's evidence).

The third finding is the one that costs return visits:

- **Packages' highest-emotion moment ends in terminal text.** Installing
  Python or clang is minutes of waiting; the reward is a line in a terminal the
  user may not be looking at. Phase 44 fixed the *honesty* of that wait (a bar,
  a lock, one sentence); it deliberately did not add a *finish*.
- **The hub loads with a blank frame** while the project list is read — no
  skeleton, no shimmer, nothing that says "it is coming".

## Design

### A — The hub: a shelf of *your* projects

| Change | Detail |
|---|---|
| Card identity | `ProjectIconView`'s existing initial+colour tile gets the token radius and card elevation from 50.1, and the **file-type icon set** from Phase 34 on the card's secondary line (the project's dominant language) |
| Row rhythm | one token gap between cards, one inside them; the ⋮ action keeps its 48 dp target (50.1) |
| Empty state | `EmptyProjectsState` gains the mark, the two starter shortcuts it already offers (Phase 33.3) **as tiles**, not as text links, and one line that says what a project is |
| Loading state | a skeleton card (`CodecMotion` shimmer) while the list is being read — **never** a blank frame |

The card's *content and actions are unchanged* — Phases 46/47's
`HubCardAction` set (Open / Open in editor / Rename / Export / Share ZIP /
Delete / Source control / Pull / Copy URL / Switch branch / Push) stays exactly
as the owner device-tested it. This part changes how it looks, not what it does.

### B — Packages: the install moment

A pure `InstallMoment` policy decides what the row says and does at each
transition, so the *finish* is testable without a package manager:

```kotlin
data class InstallFacts(val state: PkgState, val running: Boolean,
                        val progress: Int?, val failed: Boolean)
enum class PkgState { NOT_INSTALLED, INSTALLING, INSTALLED, UPGRADABLE }
object InstallMoment {
    fun labelFor(f: InstallFacts): InstallLabel      // enum: INSTALL/INSTALLING(%)/OPEN/UPDATE
    fun celebrateOnFinish(prev: PkgState, now: PkgState): Boolean
}
```

- `celebrateOnFinish` is true **only** on a real transition into `INSTALLED`
  (never on a re-render, never on an upgrade of an already-working package) —
  that is the same discipline as Phase 44's `SetupLockPolicy`, which pauses
  nothing for a settled stage.
- The celebration is: a `CodecMotion` state change on the row + the haptic and
  one-line snackbar from 51.4. **No dialog, no confetti, no illustration.**
- Failure keeps Phase 44's law: one sentence saying what happened and where to
  look (the terminal), with a retry.

### C — Terminal: chrome only

The terminal's **emulator view is out of bounds** (Phase 36 tuned its rendering
for speed, and `TerminalEmulatorView.kt` is 952 lines of measured work). What
changes:

- the chrome around it — session tab/chip, the status chip (Phase 44's), the
  extra-keys row — get the token scale and the brand roles from 51;
- the **first-run terminal frame** gets a designed state: one line saying what
  this is, and the answer to the question every new user has (*"is it ready?"*)
  taken from the **same** `SetupGatePolicy.factsFor` Phase 44 already computes —
  so the terminal, the setup bar and Packages can never disagree about the
  truth.

## The Android edge

- The hub's list is a `LazyColumn` (9 files use one); skeletons must not change
  the item count mid-scroll (a shimmer that changes list length breaks scroll
  position — the Phase 36 class of bug).
- `ModulesScreen.kt` (783 lines) reads package state from the existing VM; the
  new policy only *labels* it.
- No new DataStore key, no new permission, no network call: everything above is
  derived from state the app already has.

## Exit condition

`InstallMoment` is pinned (including "no celebration on re-render" and "no
celebration on upgrade"); the hub's empty and loading states render a designed
frame (source pins + copy pins); the terminal's chrome uses tokens and its
first-run state quotes `SetupGatePolicy` rather than its own guess; no hub card
action changed meaning; and `DEVICE_ROUND.md` F10-F13 has been run by the owner.

## Tests (plan)

- `InstallMomentTest` (~12): label per state; celebrate only on a genuine
  transition into INSTALLED; never on upgrade; never when already installed and
  the screen re-composes; failure label carries the retry.
- `HubSurfaceTest` (~9, source scan): the empty state has a mark + two starter
  tiles; a loading branch exists and renders a skeleton; cards use
  `CodecTokens`; all eleven `HubCardAction`s still exist.
- `TerminalChromeTest` (~7, source scan): the terminal chrome uses tokens; the
  first-run state reads `SetupGatePolicy.factsFor`; **no** change inside
  `TerminalEmulatorView.kt`.
- `SkeletonStabilityTest` (~4, pure): the skeleton and the loaded list declare
  the same item count for the same input (scroll-position safety).

≈32 cases, host-JVM.

## Sources (record)

- `PHASE50_52_UX_RESEARCH.md` §2.3, §2.5, §3.3 (consistency across screens is
  what users praise), §3.6.
- `FileManagerScreen.kt:1223,1270,1351` and `strings.xml:206,207,331,442`
  greps (2026-09-13, `main` @ `62cfe7b`).
- Phase 33.3 (hub empty state = the three starters), Phase 34 (file icons),
  Phase 36 (terminal rendering = do not touch), Phase 44 (`SetupGatePolicy` is
  the single source of setup truth).

## Deferred / rejected with reasons

- **A project "cover" / generated artwork per project** — cute, but it invents
  content; the icon + language is real information.
- **Reordering / drag-reorder of hub cards** — behaviour change, and
  `LazyColumn` reorder is a dependency (or a lot of code).
- **Animated install progress inside the row** — Phase 44's setup bar already
  owns progress; a second progress affordance competes.
- **Terminal emulator restyle (font, colours beyond the 4 themes, padding)** —
  Phase 36's performance work; a later, measured row.

## Implementation (2026-09-21)

**The hub has three states, and the third one is the fix.** `HubListPolicy`
(`ui/projects/HubListPolicy.kt`, new, pure): `LIST` when entries are in hand,
`EMPTY` once a read has finished, `LOADING` otherwise; `rowCount` answers for the
skeleton and for the list from the same function (floor `SKELETON_ROWS = 3`, and a
previous count is honoured so the shape does not jump when the names arrive — the
Phase 36 scroll-position bug class); `isPlaceholder` marks the branch that must
not be interactive. `FileManagerViewModel` gained `hubListFacts` and
`lastKnownHubRowCount()`, written by the existing `loadProjects`, and
`FileManagerScreen` crossfades three branches: skeleton cards, the designed empty
state, the list. `Skeleton.kt` (new) is `SkeletonBox` on `CodecMotion.shimmer` —
the shared vocabulary's only infinite spec, so a device with animations off shows
a static shape — plus `SkeletonHubCard`, which copies the real card's internal
rhythm. The empty hub now carries the `app_mark` above its three starter tiles and
the create door.

**The install moment stops ending in another tab.** `InstallMoment`
(`ui/modules/InstallMoment.kt`, new, pure) owns the words and the celebration;
`ModulesScreen`'s row now holds observable state (`installedNow`,
`installRequested`, `celebrated`) and re-reads the disk **only** while the user's
own install is in flight (1.5 s, and never as a background poll for a screenful
of rows). A genuine finish fires the `INSTALL_FINISHED` haptic (51.4) and the
one-line toast, the badge crossfades on
`motion.floatOrSnap(CodecMotion.crossfadeSpec)`, and the primary button wears the
label and is disabled while in flight. The row still never guesses a failure —
Phase 44's law stands: the terminal reports it, and `refuse()` already names the
place to look.

**The terminal's first frame quotes the shared truth.** `TerminalIntroPolicy`
(`ui/terminal/TerminalIntro.kt`, new, pure) answers from `TerminalIntroFacts(setup
= SetupFacts, hasOutput)`: output → `NONE` (silent — the shell speaks for
itself), `InstallProgress.inFlight` → `INSTALLING`, `SetupFacts.usable` →
`READY`, else `NEEDS_SETUP`. `TerminalScreen` renders it as one strip above the
emulator, with the copy from `strings.xml`. `TerminalEmulatorView` is untouched.

**Three corrections, all found by checking the plan against the code:**

1. the planned `reading` flag was redundant — `(entryCount, loadedOnce)` already
   answers the branch, and `isBusy` (the alternative) would have drawn skeletons
   during clone/delete/export (the pin is `HubListPolicyTest`);
2. `SetupGatePolicy.barVisible` is **not** "an install is running" — it is also
   true for a *settled* bar (READY-without-pkg, FAILED, UNSUPPORTED), so quoting
   it would have announced a failed setup as "installing"; the policy uses
   `InstallProgress.inFlight`, the same predicate the bar refuses dismissal on;
3. `celebrateOnFinish` v1 (`previous != INSTALLED && now == INSTALLED`) would
   have celebrated **upgrading a package the user already had working**. The
   predicate is now `now == INSTALLED && (previous == NOT_INSTALLED || previous ==
   INSTALLING)` — "there was nothing, now there is something" — and a matrix test
   counts exactly two true cells out of sixteen.

**Tests:** `HubListPolicyTest` 9, `SkeletonStabilityTest` 4, `HubSurfaceTest` 9
(the three branches render, the skeleton template, the empty state's design, and
every `HubCardAction` the owner device-tested still exists), `InstallMomentTest`
14 (the label matrix, the celebration matrix, clamped progress, the failure
pointing at the terminal), `TerminalIntroTest` 9, `TerminalChromeTest` 7 (the
shared facts, the copy, the tokens, and that the emulator view and the sora host
gained none of this phase's vocabulary).

**Not run:** device rows F9-F13 (the hub's loading frame, a real install's finish,
the terminal's first frame in all three states, the package row's words).
