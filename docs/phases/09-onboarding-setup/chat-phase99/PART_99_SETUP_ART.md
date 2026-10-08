# Phase 99 — the setup flow's illustrations, and the welcome beat

**Owner brief (2026-10-08, seven Pydroid 3 screenshots attached as reference):**
*"Design an onboarding and configuration flow that begins with a welcome screen
featuring a centered logo and a clear tagline, leading to a sequence of
preference and goal-oriented prompts. Each configuration step should display a
consistent 3D illustration and relevant options, culminating in a brief,
animated loading state before presenting the final personalized workspace
summary."*

The screenshots are **reference only**. What was taken from them is the
*composition* — centred logo on the welcome, one illustration per configuration
step, options as full-width rows, a loading beat with a bar, and a summary that
lists what the user chose. What was deliberately **not** taken: their goal and
ability questions ("What's your goal?", "What's your Python level?") — Phase 97
already recorded why those are friction that changes nothing
(`docs/research/ONBOARDING_PERSONALISATION_DESIGN_20261008.md`), and this flow
answers them with consequences instead (the picks on S4/S5, the project on S2).

## 1. What changed

| File | Change |
|---|---|
| `ui/components/StepArt.kt` | **new** — the illustration card, extracted from the tour so both halves of onboarding draw art the same way |
| `res/drawable-nodpi/setup_01_pick.webp` … `setup_06_ready.webp` | **new** — six 3D renders, 900×900, ~19 KB each (113 KB total) |
| `ui/screens/FirstRunIntroScreen.kt` | the tour's private `IntroArtwork` is gone; it calls `StepArt` (same card, same reveal) |
| `ui/theme/CodecPalette.kt` | `INTRO_ART_BACKDROP` → `ART_CARD_BACKDROP` (it is no longer only the intro's) |
| `ui/setup/SetupFlowCopy.kt` | `WELCOME_TAGLINE`; six `ART_*` descriptions; `WELCOME_TITLE` → *Welcome to CodeC*; `READY_TITLE` → *Your workspace is ready*; `WELCOME_SUB` retired |
| `ui/setup/SetupFlowScreen.kt` | S1 rebuilt as the welcome beat (centred mark + tagline + the three answers + both exits); one illustration on every beat; the loading beat gains a real progress bar |
| `SetupFlowArtTest.kt` | **new** — six assertions, §3 below |

**No question was added and none was removed.** The flow is still seven beats,
the skip is still the sample game, and the choice still lives on disk as the
project itself.

## 2. How the brief maps onto the flow

| Brief | Where it already was | What this phase added |
|---|---|---|
| Welcome screen: centred logo + clear tagline | S1 had a `>_` glyph and a plan list | the CodeC mark centred (`88 dp`), *Welcome to CodeC*, and the tagline as a sentence |
| A sequence of preference prompts | S4 (text size, theme) and S5 (reading helps) | — (they were already the preference beats) |
| "Goal-oriented prompts" | S2 asks what to *make* — a goal whose answer becomes a real project | — (see the note above on why "What's your goal?" is not asked as a label) |
| Each step displays a consistent 3D illustration | the beats had chips and headings, no art | six renders, one per beat, one size, one card |
| Relevant options | the beats' existing controls | — |
| A brief, animated loading state | S6 listed the files as they were written | plus `LinearProgressIndicator` driven by `written.size / plan.files.size` — the same fact, as a bar |
| Final personalized workspace summary | S7 showed the project + the answers as chips | the illustration, the retitle to *Your workspace is ready*, and both exits kept |

**The art size is 168 dp, not the tour's 336 dp.** Every beat still has to fit
its own answers and its *Continue* on a phone; the setup screen scrolls, so a
poster-sized card would have pushed the primary action off-screen. One size for
all six beats is what "consistent" means here, and the test pins it.

**What is still not built, and why:** the tour's pages animate in with
`CodecMotion.introReveal`; the setup beats appear without an entrance. That is
Phase 97's deviation 4 (motion deferred to 97.1) and it stays deferred — moving
it now would mean animating six beats that this environment cannot render to
check.

## 3. Laws, and where they are pinned

| Law | Pinned by |
|---|---|
| Every configuration step draws exactly one illustration, through the one shared card | `SetupFlowArtTest` |
| The six renders are real bundled files, and none is reused | `SetupFlowArtTest` |
| One art size constant, used by all six beats | `SetupFlowArtTest` |
| Every illustration is described for a screen reader, ≥12 words, and the descriptions are in the reviewed copy surface | `SetupFlowArtTest`, `SetupFlowCopyTest` (banned words, ASCII, ≤120 characters) |
| The welcome beat is a centred mark + a tagline sentence + both exits | `SetupFlowArtTest` |
| The loading bar measures files really written, never time | `SetupFlowArtTest` |
| The summary names the project, the entry file and the answers; *Start coding* is its action | `SetupFlowArtTest`, `SetupFlowPolicyTest` |
| Skip is still the sample game; the flow still imports no `Intent` | `SetupFlowWiringTest` |
| The tour's art is the same card, and its reveal is still the reduced-motion-aware one | `IntroStoriesTest`, `MotionWiringTest` |

## 4. Provenance and licence

The six renders were **generated for this project**, prompted to CodeC's own
palette (green lead, teal/amber secondary, pale mint ground) on the same contract
as Phase 98's five. No third-party asset is bundled; there is no new attribution
obligation. All eleven cards together are **238 KB** in the release APK.

## 5. What still bites (honest list)

- **No device round.** The 168 dp card, the welcome's vertical rhythm, and the
  loading bar's placement are reasoned from the existing layout, not measured.
- **The welcome beat is taller than it was.** With a mark, a title, a tagline
  and three plan rows, S1 scrolls on shorter phones; the two exits stay at the
  bottom of the scrolling column, one flick apart.
- **Alt text is author-written** (one sentence per card, ≤120 characters) and
  has not been read by a screen-reader user.
- **The reference's goal/level questions were not adopted.** If the owner wants
  them, they must change something visible — that is the standing rule from
  Phase 97, and it is recorded there rather than re-litigated here.
- **Not compiled locally** (no JDK/Android SDK in the authoring environment):
  CI is the compiler, as in Phases 97 and 98.
