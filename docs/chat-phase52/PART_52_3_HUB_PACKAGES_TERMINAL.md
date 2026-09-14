# CodeC Phase 52.3 — Hub, Packages and Terminal surfaces

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"it's not attractive to user to use multiple time"*.
> Parent: [`README.md`](README.md) ·
> [`PHASE51_53_ROADMAP.md`](../PHASE51_53_ROADMAP.md).

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
the app (51.1's evidence).

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
| Card identity | `ProjectIconView`'s existing initial+colour tile gets the token radius and card elevation from 51.1, and the **file-type icon set** from Phase 34 on the card's secondary line (the project's dominant language) |
| Row rhythm | one token gap between cards, one inside them; the ⋮ action keeps its 48 dp target (51.1) |
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
  one-line snackbar from 52.4. **No dialog, no confetti, no illustration.**
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

- `PHASE51_53_UX_RESEARCH.md` §2.3, §2.5, §3.3 (consistency across screens is
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
