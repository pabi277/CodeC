# Phase 101 — black, white, and only the gradient in colour

**Owner brief (2026-10-08):** *"a deep black background and pure white,
high-contrast typography for all text; each step featuring sleek, custom 3D
icons; smooth, modern gradients for buttons and status indicators; options
formatted in clear, well-defined cards; and ending with a prominent,
gradient-accented 'Start coding' call-to-action button."*

That is Phase 100's brief taken to its end. Phase 100 introduced a *deep dark*
stage (near-black `#0A0E0C`) with a muted grey as the second ink and a "glass"
card a shade above it. This phase asks for the two extremes instead — **pure
black and pure white** — and lets the accent be the only colour on the screen.
The five tour pages are still five, every string is still the same string, and
no beat moved.

## 1. What changed

| File | Change |
|---|---|
| `ui/theme/OnboardingStage.kt` | **rewritten** — the stage is `#000000 → #060A08`, one ink (`ON_STAGE = #FFFFFF`), the card and chip darkened, and the hairline, the dot lane and the icon halo **derived** from the ink and the accent instead of invented (`STAGE_STROKE`, `STAGE_LANE`, `STAGE_GLOW`). `ON_STAGE_MUTED` and `ON_STAGE_CHIP` are gone: on a pure-white stage a second grey ink is a contrast bug waiting to happen |
| `ui/screens/FirstRunIntroScreen.kt` | one ink everywhere; the poster panel becomes a **gradient-ringed icon tile with an accent halo**; the **logo opening moves onto the stage** (it was drawn by `MaterialTheme`, so a light-mode phone flashed white before the black tour); the **privacy dialog is staged** for the same reason; the agreement box's ring is white |
| `ui/setup/SetupFlowScreen.kt` | the **resting option card gets a real edge** — `outline`, not `outlineVariant`, which in M3's dark baseline *is* `surfaceVariant` (the card's own fill), so a resting card had no border at all |
| `ui/theme/CodecMotion.kt` | `Duration.STORY` **10 000 → 8 000 ms** — the tour's auto-advance is the fallback pace, not the reading pace, and the brief asks it to move faster (§4) |
| `CodecMotionTest.kt` | the same constant, re-pinned at 8 000 ms |
| `app/src/main/res/drawable-nodpi/intro_0{1..5}_*.webp` | **replaced** — the five 900² scene posters give way to five custom 3D **icons** (one centred glossy object each: the phone, the terminal, the toolbox, the gamepad, the shield). Same file names, same `R.drawable` ids, so no code moved; **128.3 KB → 75.0 KB** |
| `OnboardingContrastTest.kt` | **rewritten** — eight tests, now measuring the black/white stage, the derived tokens, and the themes the setup still has to work in |
| `OnboardingStyleTest.kt` | **rewritten** — eleven tests: the stage is theme-free, one ink, the icon tile, the gradients, the CTA, the option cards, and the quieter-but-still-real clock |

The five screens, the five strings per page, and `SetupFlowCopy.ALL_COPY = 101`
are all unchanged. The tour's timer got shorter and its pause control got
quieter; the *five pages*, their order and their words did not move.

## 2. The measurements

Every number below is produced by `Contrast.ratio` / `Contrast.composite` in the
host tests — the same maths the app uses, not a screenshot taken on one device.

### The stage (the tour)

| Pair | Ratio | Floor |
|---|---|---|
| `ON_STAGE` (#FFFFFF) on `STAGE_TOP` (#000000) | **21.00:1** | AA 4.5 · AAA 7 |
| `ON_STAGE` on `STAGE_BOTTOM` (#060A08) | **19.91:1** | " |
| `ON_STAGE` on `STAGE_CARD` (#0C100E) | **19.16:1** | " |
| `ON_STAGE` on `STAGE_CHIP` (#122019) | **16.85:1** | " |
| `ON_ACCENT` (#06251A) on `ACCENT_FROM` (#3DDC84) | **9.14:1** | " |
| `ON_ACCENT` on `ACCENT_TO` (#2BC4B0) | **7.48:1** | " |

21.00:1 is the maximum ratio WCAG defines — white on black. There is no
readability left to buy on this device, which is the point of the brief's two
hardest clauses.

`STAGE_STROKE` and `STAGE_LANE` are not chosen, they are **computed**: white at
20 % and 12 % over `STAGE_CARD`, and the test asserts the composite equality, so
the card's edge can never drift away from the card.

### The themed half (the setup)

The setup is *not* frozen: S4 re-themes the app in front of the user, so its
surfaces stay `MaterialTheme` roles and are measured against both base schemes.

| Pair | Dark | Light | Floor |
|---|---|---|---|
| resting option card's edge (`outline`) on `surface` | **5.41:1** | **4.44:1** | 3:1 (§1.4.11) |
| chosen card's tick (`primary`) on the 12 % tint | ≥ 3:1, all 6 accents | idem | 3:1 |
| chosen card's title (`onSurface`) on the tint | 10.48–14.37:1 | idem | AA 4.5 |
| chosen card's note (`onSurfaceVariant`) on the tint | 7.26–9.16:1 | idem | AA 4.5 |
| the build bar's fill (`primary`, `tertiary`) on `surface` | ≥ 3:1, all 6 accents | idem | 3:1 |
| the gradient CTA's ink (`onPrimary`) on `primary` and `tertiary` | ≥ 4.5:1, all 6 accents | idem | AA 4.5 |

Two of those are **defects this phase fixed**, both found by measuring rather
than by looking:

1. **The option card had no edge when it was not chosen.** M3's dark baseline
   sets `outlineVariant` and `surfaceVariant` to the same value (`#49454F`), and
   the resting card's *fill* is `surfaceVariant` — so the 1 dp border was the
   same colour as the card. `outline` restores it, and it clears the non-text
   floor on the page the card sits on in both themes.
2. **The tour was not always black.** `IntroLogoOpening` and the privacy dialog
   were drawn with `MaterialTheme.colorScheme`. On a phone in light mode, the
   first screen of the five was a *white* screen with a grey tagline, followed
   by a black tour — and the dialog's "More details…" note was
   `onSurfaceVariant` on a themed sheet. Both are now on the stage; the style
   test asserts the file contains no `MaterialTheme.colorScheme` at all.

## 3. The icons

The brief asks each step to *feature a sleek, custom 3D icon*. Phase 100 had
answered that with the existing set and recorded a new icon set as out of scope;
this phase reverses that ruling, because the same brief is now explicit.

The five new renders are 3D objects on a flat pale-mint ground, colour-matched to
the app's mark (`#F1F7F2`, the same value as `CodecPalette.ART_CARD_BACKDROP`,
which is the card the render sits in). They are shipped as **900×900 WebP q86**,
`method 6`, under the *same five file names* — so the tour's `R.drawable.intro_0*`
references, `IntroStoriesTest`'s order check and `SetupFlowArtTest`'s "each art
is used once" pin all keep working without an edit:

| File | Was | Now | Subject |
|---|---|---|---|
| `intro_01_offline.webp` | 25.8 KB | **13.8 KB** | charcoal phone, green rim, code pills, crossed-cloud badge |
| `intro_02_loop.webp` | 30.9 KB | **15.3 KB** | dark terminal window with a green play button |
| `intro_03_tools.webp` | 20.6 KB | **14.6 KB** | green wrench, teal gear, package box |
| `intro_04_sample.webp` | 24.4 KB | **15.1 KB** | mint-and-charcoal gamepad on a tile |
| `intro_05_privacy.webp` | 26.6 KB | **16.2 KB** | mint shield with a charcoal padlock |
| **total** | **128.3 KB** | **75.0 KB** | −41 % |

The replaced posters (and the source masters of the new icons) are kept outside
the APK: `uploads/intro/posters/` and `uploads/intro/icons/`.

### Why the tile, and why the ground stays light

The render is framed by `IntroIconTile` — a 168 dp square (the same size as the
setup's six illustration cards, so the two halves of onboarding frame their art
identically), a **2 dp accent-gradient ring**, and a **22 % accent halo** that
fades in with the page's own reveal:

```
Box(.size(168 * 1.3))            // room for the halo, nothing more
  Box(.fillMaxSize().alpha(reveal).background(iconGlow()))   // radial accent → transparent
  Box(.size(168).clip(XL).border(2.dp, accent()))            // the ring
    StepArt(art, description, reveal)                        // the one shared card
```

`StepArt` is still the *only* illustration path (Phase 99's rule): the render,
its required TalkBack description and its reduced-motion reveal all still come
from the shared card. The tile is chrome around it, not a second path.

**The render keeps its own pale ground on purpose.** The alternative — baking a
near-black ground into the icons so they sit directly on the black page — would
have turned every step into a dark rectangle, because the *card* behind the tile
is what the eye reads as the artwork's frame. A pale, slightly luminous tile on
a black field is exactly the "lit object" the brief asks for; the halo and the
ring are what tie it to the accent. This is the round's one open question,
recorded here rather than hidden.

## 4. What was deliberately **not** touched

- **The five pages, their order, their words.** `INTRO_STORIES` is byte-identical.
- **The setup's seven beats** and their copy. The only edit to
  `SetupFlowScreen.kt` this phase is the resting card's edge colour and a
  comment.
- **The shape of the clock.** `FIRST_RUN` still advances on
  `CodecMotion.storyTimer`, the pill still measures the *real* window, and the
  control that stops it is still a 48 dp target: Phase 98's argument stands —
  a progress pill that measures nothing, or a clock nobody can stop, is worse
  than either alone. What moved is the numbers: **10 s → 8 s**, and the pause
  control's *label* (see §4).
- **`READY`/`BUILD`/`SKIP`/`Start coding`.** Untouched — only the gradient
  around the last one was already there from Phase 100.
- **`ART_CARD_BACKDROP`.** It is the render's own ground and it is shared with
  the setup's six cards; re-tinting it per screen would give the setup a colour
  cast. It was darkened once, in Phase 100, to the value the renders were
  produced at.
- **`START_CODING` / `BUTTON_FINISH`.** `SetupFlowCopy.BUTTON_FINISH` has read
  `"Start coding"` since Phase 97; this phase *pins* it, and pins that the
  gradient CTA is what presses it (`OnboardingStyleTest`).

## 4. The quiet half: a faster clock and a smaller word

Two clauses of the brief are about the *bottom of the screen* — *"reduce the
Pause timer affordance"* and let the tour advance faster — and neither is a
contrast question, so they are argued here instead of measured.

**8 seconds, not 10.** The tour's auto-advance is a fallback: the reader can
swipe, and the dots show how long the page has been up. Each page is a heading,
two lines and a caption — one glance plus one re-read. Ten seconds was a slide
deck's pace for a screen nobody is trapped in; it made the tour four seconds
longer than it needed to be *per page*, and eight seconds still leaves room to
read the longest page twice. `OnboardingStyleTest` pins the band (5–8 s) rather
than the number, so a future round can tune it without re-arguing the brief;
`CodecMotionTest` pins the value. The hint under the page now reads its number
off the constant:

```kotlin
"Swipe left or right · auto-advances in ${CodecMotion.Duration.STORY / 1000} seconds"
```

**One word instead of two.** The pause control is not decoration — a reader who
needs longer has to be able to stop the clock — so it keeps its 48 dp target and
its full announcement; it loses the second and third word on screen:

| | Before | Now |
|---|---|---|
| Visible | `Pause timer` / `Resume timer` | `Pause` / `Resume` |
| Announced | same | `Pause timer` / `Resume timer` |
| Target | 48 dp | 48 dp |

A two-word button next to a one-line status reads as a pair of actions on a
screen where the real action is the CTA. One word reads as what it is: the state
control for the row's own sentence.

## 5. Test surface

| Test | Count | What it pins |
|---|---|---|
| `OnboardingContrastTest` | 8 | every stage pair, the derived tokens, the halo's alpha, both themes' option cards, the bar and the CTA for all six accents |
| `OnboardingStyleTest` | 11 | no theme colour in the tour; one ink; the icon tile; the gradients; one CTA; the build bar; the option card; the quiet, fast clock |

Both are rewritten rather than extended: the old assertions were correct for
Phase 100's palette (`#0A0E0C`, a muted `#AFBDB5` second ink, 7.29:1 floors) and
would now fail for the right reason — the palette they described no longer
exists. The floors went *up*, not down: the stage's text pairs are all above
16.8:1 and the CTA's ink is measured on both gradient ends.

The 101 strings, the 5 pages, the 7 beats and the 6 setup illustrations are
unchanged, so `SetupFlowCopyTest`, `IntroStoriesTest`, `SetupFlowArtTest`,
`SetupFlowWiringTest` and `SetupGateWiringTest` needed no edit. The two tests
that *did* need a co-change are named in §1: `CodecMotionTest` (the constant it
asserts) and this phase's pair, which were rewritten because the palette they
described no longer exists.
