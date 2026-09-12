# CodeC Phase 45 — The guide (slides on first run + coach marks on first arrival)

> **Status:** 🚧 **IMPLEMENTED THROUGH ROUND 5** (2026-09-12, `arena/01a0955a-codec`;
> owner: *"Start Phase 45"* → four device reports → four rebuilds) · CI ✅ GREEN on
> **all five rounds** (`34698914219` tip `3c597b2`; `34704379023` tip `acadaee`;
> `34707337429` tip `0fcb3b6`; `34711827176` tip `e7759f1`; `34714305062` tip
> `6c3cfea`, release APK 6,675,254 B) · device round required (**G1-G40**, NOT run)
> ([`DEVICE_ROUND.md`](DEVICE_ROUND.md)) · **Cost:**
> `[client-only]` · **Effort:** M · **Owner row (verbatim):** *"It has 0 guide
> features to give the user a real knowledge how to use the app, user don't know
> where should they change the project or file and the tap to the open down side
> of the keyboard"* → **owner's solution:** *"Set a step by step user guide after
> opening the app 1st time with a open view again[ing]"*
>
> **Owner clarification (2026-09-12):** *"Both layers"* — slides on first run
> **and** coach marks the first time the user reaches Editor / Terminal /
> Packages.

```text
  45.1  The five-slide first-run guide, skippable, re-openable from three places
  45.2  Coach marks: three or four, one per surface, first arrival only
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [45.1](PART_45_1_GUIDE_SLIDES.md) | The first-run guide + "View guide again" | M | 🚧 IMPLEMENTED |
| [45.2](PART_45_2_COACH_MARKS.md) | Coach marks on first arrival | S/M | 🚧 IMPLEMENTED |

---

## What exists today (evidence, read 2026-09-12 against `main` @ `f3a6e32`)

- **The app has exactly one guide surface.** `EditorScreen.kt:726-750` renders a
  "Typing tips" `AlertDialog` with four bullets about the code keyboard, gated on
  `imeGuideDismissed` (`SettingsManager.kt:52,121-122`,
  `ime_guide_dismissed`). It appears only in the **editor**, and its only two
  buttons both call `setImeGuideDismissed(true)`. **There is no way to see it
  again** — no Settings row, no reset, no "show welcome again" equivalent for it.
- **First launch is three tiles and nothing else.** `WelcomeScreen.kt` renders
  the CodeC wordmark, two lines of copy and the three starter tiles
  (`WelcomeStarters`); `MainApp` swaps it for the shell once
  `setFirstLaunchComplete(true)` lands (`MainActivity.kt:669-700`,
  `SettingsManager.kt:79,156-157`). Nothing explains the five tabs, RUN ▶, the
  drawer, Packages, the terminal, or the one-time userland download.
- **The owner's specific confusions are real, and both are chrome problems, not
  feature gaps:**
  - *"user don't know where should they change the project or file"* — the
    project switcher is the **project name in the drawer header**
    (`EditorProjectDrawer.kt:96-106`, `clickable(onClick = onSwitchProject)`),
    behind a ☰ that a new user has no reason to open.
  - *"the tap to the open down side of the keyboard"* — the tab bar **hides
    while typing** by design (Phase 32.1, `NavBarPolicy.hideNavBar`,
    `MainActivity.kt:735-747`) and comes back through a 44 dp "Show tabs" handle
    (`EditorNavRevealHandle`, `MainActivity.kt:1148-1191`). That handle is
    undiscoverable: it is the *correct* behaviour with *no* affordance teaching
    it.
- **Nothing in Settings teaches anything.** The audit doc
  (`docs/chat-phase38/SETTINGS_AUDIT.md`, 45 control rows, machine-pinned by
  `SettingsAuditTest`) has no guide row; the "About" section (`SettingsScreen.kt:914`)
  carries version/licences/update, not help.

## Why a guide is the right fix (research, dossier §3)

`docs/PHONE_UX_ANALYSIS.md` §6 measured CodeC's first session against Pydroid
and Spck and named the gap in one line: *the easy path is "know that this used
to be a C IDE, open the right tab, survive mediocre colour and empty
suggestions."* Phases 29-33 fixed colour, completions and the first-run tiles.
**"Know that" is the last unfixed item**, and it is not fixable with more
features — only with telling the user.

The market answer is two layers, and that is what the owner chose:

| Layer | What it is for | Precedent (behaviour only) |
|---|---|---|
| Slides on first run | The mental model: what this app is, what RUN does, what the one-time download is | Pydroid/Coding C ship no setup story at all; Spck's first run lands you in a working project. Slides are the cheap way to carry the *setup* story CodeC uniquely has. |
| Coach marks on arrival | "This button, right here" at the moment of need | Acode/Spck highlight the first important control per screen; VS Code's "Get Started" walks the real UI. |

**No new dependency** (dossier §3.1): the Compose onboarding libraries
(`compose-intro-showcase`, `ShowcaseView`, `AppIntro`) are all Apache-2.0 but
each drags a second navigation/anchor model for ~150 lines of overlay that
CodeC can express as a pure plan + a `Box` with a `Canvas` hole. The house rule
decides it: if it can be a pure function, it is one.

## The two rules this phase must not break

1. **The no-nag law** (`ExitSurvey.kt:11-18` states it): every guide surface is
   one-time, skippable with a single tap, and re-openable by the user. Nothing
   returns after dismissal unless asked.
   **Rounds 3 and 4 amend one clause, on the owner's own instruction.** The *slides* keep
   SKIP (unchanged, and still single-tap). The *tour* no longer has a mid-tour exit —
   *"You add the skip option and it's not a trough guide mean it got cut. I want a
   full process 1st to last without skip anything in this"* — so its exit moved to the
   end (**CLOSE** / **VIEW AGAIN**), and Back navigates instead of ending it. The law's
   *purpose* (never trap the user, never return unasked) is kept by a different means:
   **a waiting beat draws nothing at all**, so the overlay can never cover the app, and
   the tour still shows once and only comes back when asked (VIEW AGAIN, or Settings →
   About → Reset tips). Recorded as deviation 13.
2. **No dead key, no unaudited row.** A new DataStore key needs an out-of-store
   reader (`SettingsKeysHaveReadersTest`); a new Settings control needs a row in
   `docs/chat-phase38/SETTINGS_AUDIT.md` in the same commit
   (`SettingsAuditTest` counts `Settings*` call sites and section headers).

## Design in one picture

```text
  first launch
  ┌──────────────┐   ┌──────────────────────────────────────────────┐
  │ Welcome tiles│──▶│ 45.1 GuideScreen: 5 slides, SKIP / NEXT / DONE│
  │ (Phase 33.1) │   │  guide_completed = true                      │
  └──────────────┘   └──────────────────────────────────────────────┘
                              │ then, per surface, once:
                              ▼
  ┌────────────────────────────────────────────────────────────────┐
  │ 45.2 CoachMarkPlan(surface) → [anchor, title, body] (≤ 2 steps) │
  │   Editor: ☰ (your files / switch project) · RUN ▶ (compile+run) │
  │   Terminal: the status chip · the extra-keys row                │
  │   Packages: one install card (+ "one-time download")            │
  │   coach_marks_seen_csv += surface                               │
  └────────────────────────────────────────────────────────────────┘
  re-openable from: Settings → Help & guide · Projects ⋮ · Editor ☰ footer
```

## Risks to watch

- **The guide competing with the setup bar (Phase 44.1).** On a first launch
  both want attention. Rule: the guide is a *screen* (the user is already
  looking at it); the setup bar is *chrome*. They do not conflict, but slide 3
  must be the one that explains the download — otherwise the bar looks like an
  error.
- **Coach marks over a moving target.** The editor's ☰ and RUN ▶ are stable, but
  the tab bar is hidden while typing. A coach mark must never point at a control
  that is currently hidden — `CoachMarkPlan` takes the *visible* chrome state as
  input and returns `None` when the anchor is not on screen (pure, testable).
- **Tablets / landscape / split screen.** Anchors come from
  `onGloballyPositioned` rects, so the overlay follows layout; the plan must not
  hard-code positions.
- **A returning user upgrading into the guide.** `guide_completed` defaults
  `false`, so every existing user would see it once after the update. That is
  deliberate (they have never seen it) but it must be **one** screen with a
  visible SKIP, and it must not appear while the setup bar is doing work.

## Exit condition

```text
45.1
1. Fresh install → language tile → the guide appears (5 slides), SKIP works from
   slide 1, NEXT/DONE advance, and the app opens normally afterwards.
2. The guide never appears again on any later launch.
3. Three re-open paths work: Settings → Help & guide, Projects ⋮ → Guide,
   Editor ☰ drawer footer → Guide. Re-opening shows slide 1 and leaves
   `guide_completed` true.
4. A user who upgrades (flag absent) sees it exactly once, and it does not
   appear while the setup bar is active.
45.2
5. First arrival at the Editor shows at most two coach marks (☰, RUN ▶); each
   dismisses with one tap or by tapping the highlighted control.
6. First arrival at Terminal and at Packages each shows theirs, once.
7. No coach mark ever points at a control that is not currently visible (tab bar
   hidden while typing → no mark for it).
8. Settings → Help & guide has "Reset tips" → the coach marks return, the slides
   return; nothing else is reset (no data touched).
PASS = all eight.
```

## Tests (plan)

- `GuidePlanTest` (host): the slide sequence and its invariants — every slide
  has a title + body + optional action; SKIP is available from index 0; the
  plan is `Done` after the last slide; a plan read back from a persisted index
  resumes, never restarts; **no slide mentions a feature that does not exist**
  (asserted against a fixed vocabulary list, the way `SettingsAuditTest` pins
  the audit doc).
- `CoachMarkPlanTest` (host): per-surface step lists (≤ 2 each); `None` when the
  anchor is not visible; the `seen` set suppresses a surface permanently;
  "Reset tips" clears the set and nothing else; a surface with no visible anchor
  is skipped without marking itself seen (so it can show later).
- `GuidePersistenceTest`: `guide_completed` and `coach_marks_seen_csv` have
  out-of-store readers (satisfies `SettingsKeysHaveReadersTest`) and default to
  the *show-it* side.
- `SettingsAuditTest` updated in the same commit for the new row(s).

## Sources

- CodeC 2026-09-12: `WelcomeScreen.kt`, `MainActivity.kt:669-700,735-747,1148-1191`,
  `EditorScreen.kt:726-750`, `SettingsManager.kt:52,79,121-122,156-157`,
  `EditorProjectDrawer.kt:96-106`, `SettingsScreen.kt:914,1168`,
  `docs/chat-phase38/SETTINGS_AUDIT.md`.
- `docs/PHONE_UX_ANALYSIS.md` §1, §6 (the measured first-session gap).
- `docs/PHASE44_50_UX_RESEARCH.md` §3 (OSS survey + licences, the two rules).
- Behaviour-only precedent (clean-room, `rule.md` §6): Acode's per-screen
  highlights, VS Code's Get Started, Pydroid's zero-setup first run.

---

## Implementation (2026-09-12, `arena/01a0955a-codec`)

Both parts shipped in one pass, in the house shape: **pure plan + thin Android
edge**, host-tested, no new dependency, no new permission, no new navigation
route.

### What landed

| Layer | File | What it is |
|---|---|---|
| pure | `ui/guide/GuidePlan.kt` | `GuideSlide` ×5, the caps, `at/canSkip/next/isLast/isDone/resume/progress/wordCount`, and **`GuideVocabulary`** — the pin that the copy may only name controls, tabs and commands that really exist |
| pure | `ui/guide/CoachMarkPlan.kt` | `GuideSurface`, `GuideAnchors`, `CoachStep` ×5, `ChromeState` (+ `ChromeState.of(visibleAnchors, blocked)`), `stepsFor/nextUnseen/canShow/markSeen/parseSeen/serializeSeen/surfaceForRoute/stepForArrival`, and `TooltipPlacement.place` over pure `GuideRect`/`GuideSize` |
| Android | `ui/guide/GuideScreen.kt` | the five slides: `SKIP` on every slide, `LinearProgressIndicator`, `GOT IT` → `START CODING`, back = SKIP, index survives rotation/process death via `rememberSaveable` + `GuidePlan.resume` |
| Android | `ui/guide/CoachMarks.kt` | `GuideAnchorRegistry` (window rects, snapshot-state), `GuideAnchor.modifier(id)` (publish on layout, **withdraw on dispose**), `GuideCoachMarks` (the host: chrome → plan → counter), `CoachMarkOverlay` (scrim with a hole + a placed card) |
| Android | `SettingsManager` | `guide_completed` (default **false**) + `coach_marks_seen_csv` (default **empty**) + `resetGuideTips()` (one atomic edit, exactly those two keys) |
| Android | `MainActivity` | the guide as the **second** first-launch gate (tiles → guide → shell, before the Phase 44 divert and the NavHost); the overlay host in a root `Box` **above** the `Scaffold`; the `NAV_HANDLE` anchor on the reveal handle; the three `onOpenGuide` doors |
| Android | screens | `EditorScreen` (☰ + RUN anchors, `onOpenGuide`), `EditorProjectDrawer` (footer **Guide** row), `FileManagerScreen` (hub ⋮ → **Guide**), `SettingsScreen` (**Help & guide** + **Reset tips**), `TerminalScreen` (chip anchor), `ModulesScreen` (first card anchor) |

### Tests: 50 new host cases in four classes

`GuidePlanTest` 15 · `CoachMarkPlanTest` 12 · `TooltipPlacementTest` 10 ·
`GuideWiringTest` 13 (source pins). The local `rule.md` §9 kotlinc harness runs
**159 cases / 0 failures** (Phase 44's 109 + these 50); CI's `Build APK`
(`assembleDebug` + `testDebugUnitTest` + `lintDebug`) is the executor of record
for the whole suite, including the Compose edges the sandbox cannot compile at
all.

### CI round 1 ✅ GREEN (2026-09-12, run `34698914219`, tip `3c597b2`)

`Build APK` on the implementation commit: **`conclusion: success`**, job `build`
9m55s (14:18:47Z → 14:28:42Z), **zero error annotations**. Per `rule.md` §5 the
legacy `gradle :app:assembleDebug` step delegates through the `gradle-bootstrap`
shim to `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, so
a green run means the **50 new cases ran on real Gradle/JUnit** (not only on the
local kotlinc harness), the Compose edges compile in both variants, and
`lintDebug` is clean. Artifacts: `CodeC-IDE-1.3.17-universal.apk`
**6,664,474 B** (Phase 44 round 4 was 6,651,682 B → **+12,792 B, +0.19%** for
the whole guide: two pure files, two Compose files, five anchor sites and three
doors), `CodeC-IDE-1.3.17-universal-debug.apk` 25,661,856 B, `mapping.txt`
55,913,362 B, release manifest with **no `android:debuggable`**. The only
annotations are the repo-wide runner deprecations (Node 20 → 24 for
`actions/checkout@v4`, `setup-java@v4`, `upload-artifact@v4`,
`gradle/actions/setup-gradle@v4`), which predate this phase and are CI
infrastructure, not app code.

`SettingsAuditTest` stays green by construction: the two new Settings controls
(**Help & guide**, **Reset tips**) got rows 53-54 in
`docs/chat-phase38/SETTINGS_AUDIT.md` in the same commit, and
`SettingsKeysHaveReadersTest` is satisfied by real out-of-store readers
(`MainActivity` reads both flows; `SettingsScreen` calls `resetGuideTips`).

### Deviations from the plan, recorded not hidden

1. **Slide 5's copy names the real label.** The plan said *"the card's ⋮ → Open
   in editor"*; the hub's overflow item is `hub_open_action` = **"Open"**
   (`FileManagerScreen.kt`, `HubCardAction.OPEN` → `selectProject`). The copy now
   says *"Tap a card, or its ⋮ → Open, for the whole project"* — and this is
   exactly what the vocabulary pin is for: `GuideVocabulary` proves every named
   noun against a real file, so "Open in editor" would have failed the build.
2. **The spotlight hole is four rectangles, not `BlendMode.Clear`.** A clear-blend
   hole needs `graphicsLayer(compositingStrategy = Offscreen)` on the canvas; four
   rects plus a rounded stroke need nothing exotic and look the same. Recorded
   because it is the kind of simplification a reviewer should see, not discover.
3. **Anchors live in a process-wide bridge, not a CompositionLocal.**
   `GuideAnchorRegistry` follows the codebase's existing idiom
   (`SetupNoticeBridge`, `EditorChromeState`, `IncomingImportBridge`) instead of
   threading a registry parameter through five composables — and it is snapshot
   state, so publishing a rect recomposes the overlay.
4. **The card's height is an estimate (150 dp), not a measurement.** Measuring the
   card and then placing it is a layout feedback loop; the gap reads the same
   either way. `TooltipPlacement` is pure and pinned for the top / middle / bottom
   / landscape / clamped cases.
5. **Two marks per *arrival*, and the editor has three candidates.** The plan's
   "cap: two per surface, five total" is honoured as: five marks in total, at most
   two shown per arrival at a surface (`MAX_PER_ARRIVAL`, the counter resets when
   the destination changes). So the first editor arrival teaches ☰ and RUN ▶, and
   the **Show tabs** mark lands on a later arrival — the one where the handle is
   actually on screen, which is the only moment it is useful (45.2 exit 2).
6. **"Blocked" covers the surfaces `MainActivity` owns.** The exit survey, safe
   mode, and a Phase 44 download that is *actually moving* (DOWNLOADING /
   VERIFYING / EXTRACTING — CHECKING is a startup transient, not work, or no mark
   would ever appear on a fresh phone). A screen's own dialogs and sheets are
   separate windows: they cover the overlay, and the pending step is not consumed
   while they are up, which is what exit condition 6 asks for.
7. **Safe mode never shows the guide.** The plan rejected "the guide after a
   crash-loop safe start"; the implementation goes one better — in safe mode the
   gate is skipped *and the flag is not written*, so a normal launch still shows
   it exactly once.
8. **Re-opening the guide swaps the shell out** (the same `GuideScreen`, the same
   `return` before the `NavHost`) instead of adding a navigation destination: a
   route would put the guide in the back stack and hand it to Phase 49's
   `BackRouter`. The `NavController` is remembered above the gate, so closing the
   guide lands the user back on the tab they were on.

### What is still open

CI round 1 is ✅ GREEN, so what is left is **only** the device condition: the
eight rows of 45.1/45.2 plus the fresh-install rows are written as
[`DEVICE_ROUND.md`](DEVICE_ROUND.md) and have **not** been run — and note that **round 2
rewrote those rows as G1-G23 and round 3 rewrote them again as G1-G28** (the tour replaced the five
spotlights, then lost its skip), so use the current file, not this round-1 list. Until the owner reports them, Phase 45 is 🚧
IMPLEMENTED, not ✅ COMPLETE, and nothing here may be described as tested on
hardware. Phase 44's round 2 is still pending too, and the two compose on one
phone: the guide shows **before** the terminal-first divert, and slide 3 is the
one that explains the download the divert is about to show.

---

## Round 2 (2026-09-12) — the owner ran it: 45.2 rebuilt as ONE TOUR

Round 1 shipped five spotlights, two per screen per visit, each with a **GOT IT**
button. The owner installed it and reported:

> *"Working but some problem the guided box are not consistent with flow like / Not
> showing the full box guide at one and you didn't add all / Ok listen remove the
> next option only the guide will show click the option where showing the guide to
> the next / Ok now make it like demo_flask is always present so whatever user chose
> to start guide the user to start the demo project from the editor only"*

Four causes were found in round 1's own code before anything was changed (the full
diagnosis is in [`PART_45_2_COACH_MARKS.md`](PART_45_2_COACH_MARKS.md) §Round 2):
a fresh install blocks every box while Phase 44's download runs; `MAX_PER_ARRIVAL =
2` plus the per-surface filter meant most beats could not appear in one sit; the
card height was a **150dp estimate**, so a taller card was clamped over its own
hole; and a box could be cut **behind a dialog or a closed drawer** (the scrim draws
in the activity window, an `AlertDialog` is its own window, and M3 keeps a closed
drawer's rows laid out).

### What changed

| Round 1 | Round 2 |
|---|---|
| 5 marks, ≤2 per screen per visit, surface-filtered | **10 beats in one ordered tour**, no cap, no surface filter (`MAX_PER_ARRIVAL` and `surfaceForRoute` deleted) |
| Card with **GOT IT**; tap outside closes | **No forward button.** Tap the highlighted control = its own action + next beat. Tap outside = **nothing** (the whole gesture is swallowed). **SKIP TOUR** / back end the tour |
| Card height estimated at 150dp | **Measured** (`onSizeChanged`); the constant only seeds the first frame |
| Boxes could appear behind a dialog / closed drawer | `EditorChromeState.dialogOpen` blocks everything; `drawerOpen` decides drawer beats vs the rest |
| Marks numbered by nothing | `Tour · n of 10` on every card (the slides' `Guide · n of 5` shape) |
| `demo_flask` seeded once per install | **Always present** — re-seeded when missing, never overwritten |

The tour is the flow the owner dictated: ☰ → change the project to `demo_flask` →
`app.py` → RUN ▶ → (Install Python) → the Flask preview → close → the reveal-tabs
handle → a small tour of Packages → Terminal. Five new anchors were added for it
(the drawer's project header, the drawer's `app.py` row, the preview's Back, and the
bottom bar's Packages and Terminal tabs); the five round-1 ids are unchanged, so an
upgraded install keeps the beats it already saw and gains the new ones.

**One limit, recorded not hidden:** a box cannot point into an `AlertDialog` (its own
window), so the two beats that live in dialogs — the "Open folder" project picker and
the *Install Python?* prompt — are taught by the copy of the beat before them
(*"choose demo_flask"*, *"tap **Install**"*). Making every anchor screen-absolute is
the follow-up if the owner wants a hole on **Install** itself.

**One decision the owner delegated** (*"Don't understand you do as you like"*): Phase
44.1's terminal-first divert **stays**. A fresh install still watches the download
first, because beat 4 runs `app.py` and needs Python, and the visible download is the
owner's own Phase 44 requirement. The tour starts when the editor opens.

### Files touched in round 2

`ui/guide/CoachMarkPlan.kt` (the tour: beats, `waits`, `inDrawer`, `nextStep`,
`markAllSeen`, `tabAnchorFor`, the two drawer helpers, `ChromeState` simplified to
one anchor set) · `ui/guide/CoachMarks.kt` (card without a forward button, measured
height, inert outside taps, `Tour · n of 10`) · `ui/editor/EditorChromeState.kt`
(`dialogOpen`, `drawerOpen`) · `ui/screens/EditorScreen.kt` (reports both, clears
them on dispose) · `ui/components/EditorProjectDrawer.kt` (two anchors) ·
`ui/screens/WebPreviewScreen.kt` (Back anchor) · `MainActivity.kt` (two tab anchors,
the new host call) · `ui/projects/DemoProjects.kt` (always present, `ENTRY_FILE`) ·
tests: `CoachMarkPlanTest` 18, `GuideWiringTest` 16, `DemoProjectSeedTest` 7 (now in
the local harness too) → **175 host cases green locally**, and on CI: run
**`34704379023`** ✅ `success` (job `build` 10m41s, assemble + `testDebugUnitTest` +
`lintDebug`, release APK 6,664,570 B = **+96 B over round 1** — the tour is mostly
deletions, so the redesign cost the APK nothing). 45.1 is untouched by
decision ([`PART_45_1_GUIDE_SLIDES.md`](PART_45_1_GUIDE_SLIDES.md) §Round 2 note).

### What is still open

CI on the round-2 commit is ✅ GREEN (`34704379023`, tip `acadaee`). **Round 3 then
superseded this section's behaviour** (the owner ran that build: the skip cut the tour)
— see §Round 3 below, and use the current rows **G1-G28** in
[`DEVICE_ROUND.md`](DEVICE_ROUND.md), not the G1-G23 list this round was written
against. Phase 44's round 2 is still pending on the same phone.

---

## Round 3 (2026-09-12, later) — no skip: the tour runs first beat to last

The owner installed round 2 (`34704379023`), walked the tour, and reported:

> *"You add the skip option and it's not a trough guide mean it got cut / I want a
> full process 1st to last without skip anything in this / 3 ber->change the project
> folder to demo_flask->selected app.py->run->install->python->it will open the flusk
> web->close->tap to reveal the keyboard below option->then a small tour of package and
> terminal / At the end option to close and view again"*

The ten beats were already his flow; what round 2 did to them was let them be left.
Four causes, all in round 2's own code (the full diagnosis is in
[`PART_45_2_COACH_MARKS.md`](PART_45_2_COACH_MARKS.md) §Round 3): **SKIP TOUR was the
most visible thing on every card** and one tap ended all ten beats; the **pass-over
law** skipped any beat whose control was not laid out at that instant, so the Packages
box could arrive before the Flask preview did; **two beats had no target when they were
needed** (beat 2 only when a project switch would teach something, beat 6 only while
the keyboard hid the bar); and **the project picker closes the drawer**, so after
choosing `demo_flask` the tour went silent until the user found ☰ again.

### What changed

| Round 2 | Round 3 |
|---|---|
| SKIP TOUR on every card; Back ended all ten beats | **No exit mid-tour.** A tour card has no button at all; Back navigates and the tour resumes on the same beat, unspent. `markAllSeen` is **deleted** — nothing can spend a beat the user never saw |
| Beats split into "wait" (4) and "pass over" (6) | **Every beat waits**, in order; a later beat never jumps the queue. While a beat waits, **nothing is drawn**, so the app is never covered |
| The tenth box just stopped | **The finish card**: `Tour · 10 of 10`, *That is the whole tour*, **VIEW AGAIN** (all ten from the first, and the app navigates to the editor where beat 1 lives) and **CLOSE** |
| Beat 2 anchored only where a switch taught something | Anchored on the drawer header in **every** state (other project, `demo_flask`, scratch) — it always opens the picker |
| Beat 6 anchored only to the reveal handle | Anchored to the handle **and to the visible bar** — one id, two publishers, a target on every screen |
| The picker closed the drawer and the tour went silent | The host asks the pure plan `nextBeatIsInDrawer` and the editor **reopens the drawer** after a project is chosen |
| — | **The stall guard**: a beat whose control *could be on this route* and is missing for 20 s is passed for the session, **never marked seen**. A beat the user has to travel to (or wait an install out for) is never timed out — that is what stops a cascade |

### Files touched in round 3

`ui/guide/CoachMarkPlan.kt` (`waits` deleted; `stalled` + `waitingOn` +
`anchorsPossibleOn` + `isFinished` + `replay` + `nextBeatIsInDrawer` +
`STALL_GUARD_MS`; `markAllSeen` and `drawerProjectAnchor` deleted; `ChromeState.route`)
· `ui/guide/CoachMarks.kt` (no SKIP, no BackHandler, no buttons mid-tour, the stall
timer, `TourFinishedCard`) · `MainActivity.kt` (`route`, `onReplay` + the editor
navigation, `tourWaitsInDrawer`, the bar's NAV_HANDLE anchor) ·
`ui/screens/EditorScreen.kt` (`tourWaitsInDrawer`, the drawer reopen after a pick) ·
`ui/components/EditorProjectDrawer.kt` (beat 2's unconditional anchor) · tests:
`CoachMarkPlanTest` **21**, `GuideWiringTest` **18** → **180 host cases green
locally**. 45.1 is still untouched
([`PART_45_1_GUIDE_SLIDES.md`](PART_45_1_GUIDE_SLIDES.md)).

### What is still open

CI on the round-3 commit is ✅ GREEN (`34707337429`, tip `0fcb3b6`), and round 3 is
superseded by round 4 below — the device round to run is **G1-G38**.

---

## Round 4 (2026-09-12, later still) — one tap does both halves, and an install pauses the app

The owner installed round 3 (`34707337429`), walked the tour, and came back with two
requests:

> *"1. When the userland is installing and unpacking the user can not access any other
> other option and it will show a sweet massage of why can't access any other option /
> 2. Now the steps feel like an overlay on the botton so 1st click disappear the massage
> and i have to click 2nd time to really work but if someone don't click 2nd time it
> just cut off the flow of tutorial / So do something"*

Request 2 is a bug in round 3's central mechanism, and the diagnosis is in
[`PART_45_2_COACH_MARKS.md`](PART_45_2_COACH_MARKS.md) §Round 4: the overlay advanced
the tour on the **press** and left the tap **unconsumed**, trusting Compose to deliver
the rest of the gesture to the real control. The advance's own recomposition
(`coachSeen` → `tourWaitsInDrawer` → the next box) could rebuild that control before
the **lift**, and a `clickable` whose node was rebuilt is cancelled: the box went away,
the beat was spent, nothing opened. And because the beat was spent, the tour then
waited in silence (deviation 14) — *"it just cut off the flow of tutorial"*.

### What changed

| Round 3 | Round 4 |
|---|---|
| A tap inside the hole was left unconsumed; the control *should* have performed its own action | **An anchor publishes its own click beside its rect**, and the overlay performs it and swallows the gesture: one tap = one action + one beat, and nothing can fire twice |
| The tour advanced on the **press** | The tour advances on the **lift**, after the click — the advance's recomposition can no longer cancel the click it rides on |
| A drag inside the hole was a tap | **A drag that travels and lifts outside the hole is not a tap**: nothing is performed, no beat is spent, the box stays |
| Beat 6's bar-wide hole left the tap to Compose | The tap resolves to the **most specific anchored click under the finger** (the Packages tab inside the bar); a tap on a tab the tour does not use is still left to that tab |
| Beat 6's copy said *"swipe up to bring them back"* | *"tap this handle to bring them back"* — while a box is up a swipe is not a tap, so the copy must teach the gesture the tour accepts (the swipe still works with the tour over) |
| One id, two publishers, a blind `withdraw(id)` | The registry records **which composition site owns an id**, so a bar↔handle swap cannot wipe the arriving publisher's rect *and* click |
| An install was invisible to everything but the setup bar and the gate | **The chrome lock**: while an install moves, the options that cannot work are paused, dimmed with a small 🔒, and answer a tap with one sweet sentence that says what is happening, why, and where to watch it. The tour pauses with them |

The lock is a Phase 44 surface as much as a Phase 45 one, so it is specified in
[`../chat-phase44/PART_44_1_VISIBLE_SETUP.md`](../chat-phase44/PART_44_1_VISIBLE_SETUP.md)
§"Phase 45 round 4 — the chrome lock". Its law: **the surface that shows the install is
never paused** (Terminal for the Linux tools, Editor for a package install), a settled
setup pauses nothing, an in-flight **upgrade** of a working prefix pauses nothing, and
44.1's guarantees stand — typing and `cc` never wait for a download.

### Files touched in round 4

`ui/guide/CoachMarkPlan.kt` (`GuideTapTarget`, `GuideTapPolicy.targetFor` /
`isTap`, `GuideRect.contains` / `area`, beat 6's copy) · `ui/guide/CoachMarks.kt`
(the registry's `actions` + `owners`, `GuideAnchor.modifier(id, onClick)`, the
gesture rewritten) · the eight anchor sites that publish a click (`EditorScreen` ☰ +
RUN ▶, `EditorProjectDrawer` header + row, `WebPreviewScreen` Back, `MainActivity`
tabs + reveal handle, `ModulesScreen` card) · `ui/terminal/SetupState.kt`
(`ChromeOption`, `ChromeLockReason`, `ChromeLock`, `SetupLockPolicy`) ·
`MainActivity.kt` (the lock, the bar's paused tabs, the scaffold's snackbar host,
`blockedByForeground … || chromeLock.locked`) · `ui/screens/EditorScreen.kt` (☰ / RUN ▶
/ edge swipe answer the lock, and report the install) · `ui/editor/EditorChromeState.kt`
(`installRunning`) · `ui/viewmodels/EditorViewModel.kt` (`OutputRunState.installing`) ·
tests: `CoachMarkPlanTest` **25**, `GuideWiringTest` **19**, `SetupGatePolicyTest`
**30**, `SetupGateWiringTest` **23** → **193 host cases green locally**. 45.1 is still
untouched ([`PART_45_1_GUIDE_SLIDES.md`](PART_45_1_GUIDE_SLIDES.md)).

### What is still open

CI on the round-4 commit is ✅ GREEN (`34711827176`, tip `e7759f1`, job `build`
11m29s, zero annotations, release APK 6,675,258 B = **+5,400 B / +0.08%** over round 3),
and round 4 is superseded by round 5 below — the device round to run is **G1-G40**.

---

## Round 5 (2026-09-12, later still) — the lock is on from the first frame

The owner installed round 4 (`34711827176`), accepted both halves of it, and corrected
one thing:

> *"The lock option is good but still it late user can switch before the start of
> userland download because is takes a little time to connect and user can switch task
> between them / Make it instantly after 1st open and others are ok"*

Round 4's `SetupLockPolicy.reasonFor` was **stage-keyed**: it paused only once a stage
said work was moving (`DOWNLOADING` / `VERIFYING` / `EXTRACTING`). Everything before the
first byte — the ledger read, the probe of `bin/pkg` and `bin/bash`, the reach for the
network, the server's first answer — reported `CHECKING`, which round 4 had deliberately
exempted (*"the startup probe, not work"*). The exemption was right about a working
phone and wrong about a fresh one: on a cold radio the window is seconds, and Phase
44.1's launch divert only picks the *starting* tab, so one tap in that window landed the
user on Packages reading *"not installed"* about tools already on their way.

### What changed

| Round 4 | Round 5 |
|---|---|
| Stage-keyed: `DOWNLOADING`/`VERIFYING`/`EXTRACTING` **and** `!facts.usable` | **Prefix-keyed**: `!facts.usable` and not given up on ⇒ paused, whatever the stage says. New reason `USERLAND_STARTING` covers `CHECKING` and a `READY` the disk contradicts |
| `CHECKING` exempted, so the first seconds were switchable | The first frame is paused; the sentence is *"Hang tight — CodeC is getting ready to set up its Linux tools. … The Terminal tab shows every step."* (no `%` — none exists yet, and none is invented) |
| `lock()`'s defaults meant "nothing is happening" | The defaults **are** the first frame of a fresh install (`CHECKING`, empty facts) and they answer **PAUSED** — a caller that has heard nothing yet must not default to open |
| `lock(progress, facts, packageInstallRunning)` | `lock(progress, facts, packageInstallRunning, reducedStart)` — **safe mode is exempt** from the userland branch: a crash-loop phone must still reach Settings (export all projects, report a crash). A package install still outranks it |
| — | A boot-time **repair** of an interrupted swap (`swapping` ⇒ `!usable`) is now paused too, with the Terminal open where 44.2 logs the restore |

Nothing else moved: the watch surface is still never paused, `FAILED`/`UNSUPPORTED`
still pause nothing (a stopped setup has its own sentence, its own ⬇ retry, and **C
still compiles offline**), a usable prefix still short-circuits before the stage table —
which is why an installed phone never sees a pause flash at launch (`TerminalViewModel`
builds the facts **synchronously from the disk** in its constructor) — and
`SetupGatePolicy.can` is still the only answer about capability.

The tour is unaffected except that it now pauses with the lock a few seconds earlier
(`blockedByForeground … || chromeLock.locked`), and 45.1's slides are untouched for a
fifth time.

### Files touched in round 5

`ui/terminal/SetupState.kt` (`ChromeLockReason.USERLAND_STARTING`, the prefix-keyed
`reasonFor`, the `reducedStart` parameter, the starting sentence, the law comment
rewritten) · `MainActivity.kt` (`reducedStart = SafeMode.active`) · tests:
`SetupGatePolicyTest` **33**, `SetupGateWiringTest` **24** → **197 host cases green
locally** (76 guide/demo + 121 Phase 44).

### What is still open

CI on the round-5 commit is ✅ GREEN (`34714305062`, tip `6c3cfea`, job `build` 25
steps 10m47s, zero annotations, release APK 6,675,254 B — **−4 B** against round 4: one
enum constant, a reordered `when` and one extra parameter are below what R8 can
measure), so only the device round is open: rows **G1-G40** in
[`DEVICE_ROUND.md`](DEVICE_ROUND.md) (G1-G8 the slides, G9-G28 the tour, G29-G38 round
4, **G39-G40 round 5** — G39 wants a fresh install and a slow or absent network, G40
wants an installed phone). Test the tour after **Settings → About → Reset tips**. Phase
44's round 2 is still pending on the same phone, and its lock rows are G34-G40 here.
Specification: [`../chat-phase44/PART_44_1_VISIBLE_SETUP.md`](../chat-phase44/PART_44_1_VISIBLE_SETUP.md)
§"Round 5 — *Make it instantly after 1st open*"; owner-facing:
[`../TROUBLESHOOTING.md`](../TROUBLESHOOTING.md) §39.
