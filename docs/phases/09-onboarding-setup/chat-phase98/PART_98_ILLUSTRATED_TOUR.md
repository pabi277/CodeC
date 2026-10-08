# Phase 98 — the illustrated tour

**Owner brief (2026-10-08):** *"Design your welcome interface as a multi-step
onboarding tutorial sequence that utilizes clean, 3D illustrations centered on
each screen, accompanied by a bold, descriptive heading and a subtext detailing
specific features. The workflow should visually represent coding offline, the
edit-run-check loop, toolkit support, and sample projects with an active
progress indicator at the bottom. Following the tutorial sequence, integrate a
workspace setup screen that allows users to pick their first project and make
the editor their own through text size and theme customization choices.
Conclude the onboarding with a clear call-to-action button to start coding or a
direct path to skip the setup and explore a sample game."*

Four reference screenshots were attached (a notes app's tour). They are
**reference only** — the layout grammar was taken (art card → chip → bold
heading → subtext → bottom progress), not the content, colour, or product.

## 1. What changed

| File | Change |
|---|---|
| `app/src/main/res/drawable-nodpi/intro_01_offline.webp` … `intro_05_privacy.webp` | **new** — five 3D renders, 900×900, ~25 KB each (125 KB total) |
| `ui/screens/FirstRunIntroScreen.kt` | the four story pages + the agreement page rebuilt as one illustrated tour: `IntroStory` data class, `INTRO_STORIES` deck, `IntroArtwork`, `IntroHeading` (chip + bold heading + centred subtext), `IntroDots` |
| `ui/theme/CodecPalette.kt` | `INTRO_ART_BACKDROP` — the render's own `#F1F7F2` |
| `ui/setup/SetupFlowCopy.kt` | seven beat chips (`WELCOME_EYEBROW` … `READY_EYEBROW`), added to `ALL_COPY` |
| `ui/setup/SetupFlowScreen.kt` | `StepEyebrow` — the same chip as the tour's, above every beat's heading |
| `IntroStoriesTest.kt` | **new** — the deck, the art files, the order, the voice, the indicator |

The tour is **five pages, unchanged in count**: four illustrated story pages
(coding offline · the edit-run-check loop · the toolkit · the sample projects)
plus the privacy agreement, which stays the gate. Nothing was added to or
removed from the sequence the owner approved in Phase 97.

**Why the art is not tinted per accent.** The renders carry their own pale
backdrop and a green lead. They are drawn on a card of exactly that backdrop
colour and shipped as they were drawn; tinting photographic 3D art per accent
would look like a sticker with a colour cast. The chip, headings, dots and
buttons around them are all theme roles, so the *interface* still follows the
user's accent.

**Why the images ship as WebP.** The release APK is a measured quantity in this
project (Phase 42.2), and the five cards together add 0.125 MB to a 6.9 MB
release APK — under 2 %. PNG at the same size would have been ~10× that for no
visible gain on a phone.

## 2. The design language (and where it came from)

| Element | Decision | Source |
|---|---|---|
| One illustration per page, centred, square, capped at 336 dp | art is the page's anchor, not a decorative band | owner's reference; NN/g product-tour analysis |
| Eyebrow chip above the heading | the page's subject in two words, before the sentence | owner's reference |
| Bold heading, then centred subtext | one idea per page; the subtext carries the specific feature list | owner's reference + the Phase 96 audit's "signs, not features" finding |
| Progress dots **at the bottom**, current one a wide pill that fills | the indicator measures the real ten-second reading window, never a timer of its own | owner's brief ("active progress indicator at the bottom"); progress-accuracy research in `ONBOARDING_PERSONALISATION_DESIGN_20261008.md` |
| Timer and its Pause control kept, below the dots | the tour was already honest about auto-advance; hiding it would take a choice away | Phase 90's behaviour, kept |
| The privacy agreement stays page 5 | a gate the owner approved; it moves nothing | Phase 90/97 |

**Not built, on purpose:** the reference's "swipe to continue" chevrons (the
existing footer sentence already says swipe *and* auto-advance, and three
chevrons are decoration), and any illustration that shows text the render
cannot actually spell (every "sample" in the art is an abstract bar).

## 3. Laws, and where they are pinned

| Law | Pinned by |
|---|---|
| Five pages, and the screen counts five | `IntroStoriesTest` |
| The four promises in the brief's order, each page naming its own theme | `IntroStoriesTest` |
| Every page has a bundled illustration, the files exist, no page reuses another's | `IntroStoriesTest` |
| Every illustration is described for TalkBack | `IntroStoriesTest` |
| No page's heading is a paragraph (≤ 8 words, ≤ 44 characters) | `IntroStoriesTest` |
| ASCII + the app's three marks only; no store words, no ability labels | `IntroStoriesTest` |
| The indicator is at the bottom, one dot per page, filled by the real timer, announced as progress | `IntroStoriesTest` |
| The bundled art is the only illustration path (the five vector composables are gone) | `IntroStoriesTest` |
| Swipe, back gesture, skip, the timer + pause, the agreement gate | `FirstOpenGameArenaTest`, `MotionWiringTest`, `BackHandlerWiringTest` |
| The setup half's chips are in the reviewed copy surface | `SetupFlowCopyTest` (`ALL_COPY`, word budget, ASCII, banned words) |
| The setup still ends in *Start coding* or *Skip setup — start with the sample game* | `SetupFlowPolicyTest`, `SetupFlowWiringTest` |

## 4. Provenance and licence

The five renders were **generated for this project** (image model, prompted to
CodeC's own palette); no third-party asset, icon pack, or stock illustration is
bundled, so there is no new attribution obligation. The reference screenshots
the owner attached informed the layout grammar only. The prompt intent for each
page is recorded in the image files' page order in `INTRO_STORIES`.

## 5. What still bites (honest list)

- **No device round.** The art was reviewed as files in this environment, never
  on a phone; the card's cap (336 dp), the heading wrap, and the dots' width
  are reasoned, not measured.
- **The renders' colour is baked.** They will not follow a light theme's
  background; they sit on their own card, which is what makes that safe.
- **Not compiled locally** (no JDK/Android SDK here): CI is the compiler, as in
  Phase 97.
- **Five pages is still five pages.** The brief did not ask to shorten the
  tour; the Phase 96 research is clear that a longer upfront tour costs
  completion. The illustrations make the existing pages better, not the
  sequence shorter.
- **Alt text is author-written**, one sentence per page; it has not been read
  by a screen-reader user.
