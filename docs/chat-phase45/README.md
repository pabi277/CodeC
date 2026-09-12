# CodeC Phase 45 — The guide (slides on first run + coach marks on first arrival)

> **Status:** 🚧 **IMPLEMENTED** (2026-09-12, `arena/01a0955a-codec`; owner:
> *"Start Phase 45"*) · CI ✅ GREEN round 1 (`34698914219`, tip `3c597b2`) ·
> device round required (G1-G14, NOT run)
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
rewrote those rows as G1-G23** (the tour replaced the five spotlights), so use the current file, not this
round-1 list. Until the owner reports them, Phase 45 is 🚧
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
tests: `CoachMarkPlanTest` 17, `GuideWiringTest` 15, `DemoProjectSeedTest` 7 (now in
the local harness too) → **175 host cases green locally**. 45.1 is untouched by
decision ([`PART_45_1_GUIDE_SLIDES.md`](PART_45_1_GUIDE_SLIDES.md) §Round 2 note).

### What is still open

CI on the round-2 commit, then the device round: rows **G1-G23** in
[`DEVICE_ROUND.md`](DEVICE_ROUND.md) (G1-G8 the slides, G9-G23 the tour). Test it
after **Settings → About → Reset tips**, or the beats round 1 already marked seen
will not come back. Phase 44's round 2 is still pending on the same phone.
