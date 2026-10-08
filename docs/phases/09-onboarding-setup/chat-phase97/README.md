# Phase 97 — the post-agreement setup flow: seven beats, your project, and the way to the lessons

> **Status: ✅ IMPLEMENTED (2026-10-07/08) on `arena/db36a12a-codec`; CI ✅ GREEN on the
> final head `7cbd7f3` (Build APK [`37685622970`](https://github.com/pabi277/CodeC/actions/runs/37685622970));
> merged to `main` via PR #118 (merge commit `fb439a9`) (2026-10-08) with Phases 98–101 riding inside it.**
>
> **Authorization:** the owner's seven Pydroid 3 screenshots, then *"something like this with a
> better approach that fit CodeC"*, and — after the first design — *"Ok implement start you can
> now change in app code"*. The screenshots are **reference only**: the composition was taken,
> the feature set was not.
>
> **The three corrections that shaped everything below:** *"i like all 7 page so i don't want to
> decrease the number of the slides"* (the seven beats stay seven); *"make it more modern
> authentic"* (not a settings grid); and the one that changed the flow's whole shape — the game
> arena is **a sample project**, so *"I want to make my own first project"* had to be literally
> true: the user picks the project and names it, and Arcade is one option among several.

## 1. What the owner asked for, and what shipped

| The ask | What shipped | Pinned by |
|---|---|---|
| A welcome before anything is asked | **S1 WELCOME** — the CodeC mark centred, *"Welcome to CodeC"*, the tagline *"Your pocket coding studio: write, run and check code on this device."*, **three numbered plan lines** with a detail line each saying what the flow will really do, the honest cost (*"About 40 seconds."*), and two exits: **Let us go** and **Skip setup — start with the sample game** | `SetupFlowWiringTest`, `SetupFlowArtTest` (the mark is `painterResource(R.drawable.app_mark)`) |
| Pick your first project | **S2 PICK** — four real starts: **CodeC Arcade** (the sample the phase-58 first open already seeds), **C**, **Python**, **Web**. Every card shows the real first line of the file it creates and what it costs | `SetupFlowPolicyTest` (18), `SetupFlowArtTest` (six `StepArt` cards, each `ART_*` used once) |
| Name it yourself | **S3 NAME** — a name field with live validation (`ProjectNameVerdict`), the app's own sanitiser, and name chips. Nothing is typed twice and nothing is refused without a reason | `SetupFlowPolicyTest`, `SetupFlowCopyTest` |
| It should look how I want | **S4 LOOKS** — **Dark / Light / Auto**, applied **live**: the screen the choice is made on is the screen that changes. Text size S/M/L too | `SetupGateWiringTest` (22), the *S4 re-themes live* seam recorded in Phase 100 |
| Set up how it helps | **S5 HELPS** — three plain-language switches (**Typing hints**, **Line numbers**, **Word wrap long lines**, each with its own detail line), the *when a build fails* preview (the plain sentence beside the real compiler output), and one honest line about the AI (*"The AI helper stays off until you ask for it."*) | `SetupFlowWiringTest`, `SetupFlowCopyTest` |
| Show me it is doing something | **S6 BUILDING** — a real progress bar over **real work**: `files written ÷ plan.files`, driven by `onFileWritten` from `SetupSeeding.apply`, with the honest download line (*"about 40 MB, once"*) and an escape hatch (*"Use C instead"*, *"Arcade, C and web pages need no download."*) | `SetupFlowPolicyTest`, `SetupFlowArtTest` |
| End on what I have | **S7 READY** — *"Your workspace is ready"*, the project's name, and chips naming the files that exist on disk, then the one action left: **Start coding** | `SetupFlowWiringTest` |
| Reach the website's lessons from the app | `LearningLinks` + one link row inside the flow (and the same doors in **Settings → About**: *Learn to code — 19 short chapters*, *Common questions*) | `SettingsSearchWiringTest`, `SettingsAuditTest` (66 rows), `SettingsSearchPolicyTest` |

## 2. The contract (what the rest of the app may rely on)

- **`ui/setup/`** — `SetupFlowScreen(initialChoice, existingProjectNames, onSkip, onThemePicked,
  onOpenLink, onBuild: suspend (SetupChoice, (String) -> Unit) -> String?, onFinish, onAbandon)`.
- **`SetupStart` = ARCADE / C / PYTHON / WEB**; `skipChoice()` is the **legacy first-open path**
  (`codec-arcade`, the phase-58 sample), unchanged — skipping setup cannot change what a
  skipping user gets.
- **`planFor.files`** always begins `.codec/project.json`; the build beat's bar is the count of
  those files actually written, never a decorative animation.
- **`progressIndex`** 0–5 with `READY = 6`; the hairline strip counts S1–S6 and READY is the pay-off
  (`SetupStep.WELCOME … BUILDING, READY`).
- **`SetupTextSize`** 14/16/20 sp; **`SetupTheme`** dark/light/auto; **`SetupFlowCopy.ALL_COPY` = 101**
  strings, every one inside `MAX_WORDS = 12`, every one ASCII (plus the app's `·`/`—`/`–`).
- **`setup_flow_complete`** is the flow's only stored fact (`SettingsManager`, with its flow).
- **≤ 6 taps, zero typing** to finish; **Back** only inside PICK…HELPS; the privacy agreement on
  the tour's last page still gates the flow that follows it (so the flow is post-agreement by
  construction, never by convention).

## 3. Where it plugs into the app

`MainActivity`'s launch effect (867–932) is the one place that decides: `setupAbandoned` →
`SetupSeeding.apply` (896) → `EditorLaunchState.save` (900) → the answers + `setSetupFlowComplete`
(922, 925) → `setFirstLaunchComplete(true)` (927). The order is the point: the files exist on disk
before the editor is told which project to open, and a user who abandons the flow at any beat
still gets the legacy sample rather than a half-built workspace.

Outbound traffic is still one path: **`ui/services/OpenInBrowser.kt`** is the app's only
`ACTION_VIEW`. `codec-arcade`'s own seeding machinery (`GameArenaSample.ensure` / `writeProject` /
`seedInto`) is reused as-is, so the sample is the same project whether it arrives silently or from
the card.

## 4. CI, honestly

Three rounds failed before the green one, and each is worth the line it takes:

| Run | Cause |
|---|---|
| `37683136302` | a compile error in the new sources |
| `37683916380` | an illegal `;` inside a backticked test name |
| `37684814990` | **2 of 3 474 tests**: `SettingsSearchWiringTest.kt:120` and `FirstOpenGameArenaTest.kt:152` — two pure-source pins that must not be "fixed" by reordering `MainActivity` |
| [`37685622970`](https://github.com/pabi277/CodeC/actions/runs/37685622970) | **green** on `7cbd7f3` — release artifact `6 577 933 B`, debug `26 316 033 B`, mapping `4 807 757 B`; release universal APK `7 482 527 B`, no `android:debuggable` |

## 5. Deliberately not done

- **No goal or level questions.** The Pydroid screenshots (the seven from this phase's brief, and
  the ones re-sent for Phase 99) were taken as reference and refused on purpose: the flow asks what
  the app needs and answers the rest by consequence.
- **No second illustration path.** Every beat draws the one shared `StepArt` card (Phase 99).
- **No new storage.** The flow persists exactly one boolean.
- **No free-form link field.** The lessons door is a URL from `LearningLinks`, not user input.
- **Nothing in `strings.xml`** — the flow's words live in `SetupFlowCopy`, where
  `SetupFlowCopyTest` can count them.

## 6. Record

- The spec: [`PART_97_SETUP_FLOW.md`](PART_97_SETUP_FLOW.md)
- The research that preceded it: [`docs/research/BEGINNER_UX_AUDIT_20261007.md`](../../../research/BEGINNER_UX_AUDIT_20261007.md)
  and [`ONBOARDING_PERSONALISATION_DESIGN_20261008.md`](../../../research/ONBOARDING_PERSONALISATION_DESIGN_20261008.md)
- What came next: [Phase 98](../chat-phase98/README.md) gave the first-run tour its five pages,
  [Phase 99](../chat-phase99/README.md) gave these beats their art, and
  [Phase 100](../chat-phase100/README.md)/[101](../chat-phase101/README.md) restyled both halves.
