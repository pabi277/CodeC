# CodeC Phase 45 — The guide (slides on first run + coach marks on first arrival)

> **Status:** 📋 PLANNED (researched + specced, no code) · **Cost:**
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
| [45.1](PART_45_1_GUIDE_SLIDES.md) | The first-run guide + "View guide again" | M | 📋 PLANNED |
| [45.2](PART_45_2_COACH_MARKS.md) | Coach marks on first arrival | S/M | 📋 PLANNED |

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
