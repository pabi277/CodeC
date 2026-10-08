# Phase 100 — the stage, the gradients, and the numbers behind them

**Owner brief (2026-10-08):** *"minimalist modern aesthetic; deep dark backdrop;
crisp high-contrast type; sleek custom icons; smooth gradient accents;
well-defined button states; card-based layouts."* — with the standing rule that
the seven beats of the tour and the seven beats of the setup flow stay exactly as
they are: this phase **restyles**, it does not re-cut.

## 1. What changed

| File | Change |
|---|---|
| `ui/theme/OnboardingStage.kt` | **new** — the tour's fixed palette (12 consts), `backdrop()`, `accent()`, and the shared `codecAccentGradient(primary, tertiary)` for themed surfaces |
| `ui/components/GradientButton.kt` | **new** — the gradient call to action, with real pressed / disabled / focus states |
| `ui/components/GradientProgress.kt` | **new** — the hand-painted determinate bar that keeps Material's progress semantics |
| `ui/screens/FirstRunIntroScreen.kt` | staged dark; the page is a card; the chip, the dots, the links, the agreement box and the CTA all draw from the stage |
| `ui/setup/SetupFlowScreen.kt` | the same CTA; an accent ring on the welcome mark; the gradient as a hairline on every eyebrow chip; gradient-edged chosen cards; the accent gradient in the build bar |
| `OnboardingContrastTest.kt` | **new** — nine assertions, every colour pair measured (§4) |
| `OnboardingStyleTest.kt` | **new** — six assertions, the wiring that produces the look (§5) |

No copy changed, no art changed, no beat was added or removed, and no asset was
generated. `SetupFlowCopy.ALL_COPY` is still 101 strings; the tour is still five
pages; the setup is still seven beats with six illustrations.

## 2. The one decision worth arguing about: a stage for the tour, a theme for the setup

The tour runs **before the app has a theme** — the theme is one of the answers
the setup collects. So it cannot borrow the user's palette, and it must not look
different on two devices. It gets a fixed stage.

The setup runs **while the theme is being chosen**: S4 re-themes the app live,
because *"these change right here"* is that beat's entire point. So the setup's
accents are `primary → tertiary` from the live `MaterialTheme`, and the seam is
deliberate:

| | Tour | Setup |
|---|---|---|
| Backdrop | `OnboardingStage.backdrop()` (fixed) | `MaterialTheme.colorScheme.background` (the user's) |
| Text | `ON_STAGE` / `ON_STAGE_MUTED` (fixed) | `on*` roles |
| Accent | `OnboardingStage.accent()` (fixed green → teal) | `codecAccentGradient(scheme.primary, scheme.tertiary)` |
| Pinned by | `OnboardingStyleTest` | `OnboardingStyleTest` (including "the setup must **not** import the stage") |

A fresh install is dark, so the two halves land in the same visual family; nobody
sees the seam as a seam. But the seam is real, and pretending otherwise (freezing
the setup too) would break S4's whole reason to exist.

## 3. The palette, and every ratio it was chosen by

| Token | Value | Measured |
|---|---|---|
| `STAGE_TOP` → `STAGE_BOTTOM` | `#0A0E0C` → `#111A15` | the two ends differ by 1.09:1 — one surface, not two |
| `STAGE_CARD` | `#141C17` | the tour's reading surface |
| `STAGE_STROKE` | `#333B36` | **exactly** `ON_STAGE` at 14 % over the card — the hairline is the ink, not a second colour |
| `ON_STAGE` | `#F2F6F3` | 17.81:1 on the top, 16.28:1 on the bottom, 15.93:1 on the card |
| `ON_STAGE_MUTED` | `#AFBDB5` | 8.91:1 on the card, 9.11:1 on the bottom |
| `STAGE_CHIP` + `ON_STAGE_CHIP` | `#16301F` + `#9CF2C3` | 10.77:1 |
| `ACCENT_FROM` → `ACCENT_TO` | `#3DDC84` → `#2BC4B0` | the ends differ by 1.22:1 — a gradient, not a fill |
| `ON_ACCENT` | `#06251A` | 9.14:1 on the green end, **7.48:1 on the teal end** (the teal was chosen for this number) |
| `STAGE_CONTROL_STROKE` | `#F2F6F3` at 35 % | translucent by design — it is drawn over whatever it sits on |

Two of those are *derivations*, and the tests assert them as equalities rather
than as reviews: the card's edge **is** the ink at 14 %, and the control edge
**is** the ink at 35 %. A stage with one ink and one accent cannot drift.

**Hairlines are not asserted at 3:1.** A 1 dp edge on a deep-dark surface cannot
reach 3:1 without becoming a second, brighter colour — which is exactly what a
minimalist stage must not grow. So the *text* and the *state glyph* carry the
meaning (both measured), and the hairline is decoration. The tests say this out
loud instead of pinning a ratio the design cannot have.

## 4. Three real defects the measurement found

The brief said "high-contrast type". Measuring the things the redesign touches
turned up three problems that no screenshot would have shown:

1. **The build bar's track.** `surfaceVariant` at 6 dp under an accent gradient
   measured as low as **2.46:1** (Violet, Teal, in the dark theme) — under the
   3:1 WCAG 2.2 §1.4.11 floor for a graphical object, i.e. a progress bar whose
   fill cannot be told from its track. `GradientProgress` now defaults its track
   to the **surface** the bar sits on, which every accent is lightness-corrected
   against, so the fill is guaranteed ≥ 4.50:1 in all twelve accent × theme
   combinations; a 1 dp `outlineVariant` lane keeps the empty part visible.
2. **The chosen card's sample line.** Drawn in `primary` on the card's own
   `primary` tint, it measured **3.96:1 – 4.18:1** for most accents (Phase 99's
   10 % tint; 3.86–4.11:1 at this phase's 12 %) — body text under AA. It now
   stays `onSurfaceVariant` (≥ 7.26:1 on the tint) while the *state* is carried
   by the tick (≥ 3.86:1, a graphic), the 2 dp gradient edge, and the `selected`
   semantics TalkBack reads.
3. **The resting card has no visible border in the dark baseline** — in M3's
   `darkColorScheme()`, `outlineVariant` and `surfaceVariant` are the *same*
   value (`#49454F`), so a resting card is defined by its fill, not by an edge.
   Recorded rather than "fixed": the chosen/un-chosen difference is a tick and a
   gradient edge, which is a stronger signal than a hairline would have been.

## 5. Laws, and where they are pinned

| Law | Pinned by |
|---|---|
| The tour draws only from `OnboardingStage` — no hex literal, no `MaterialTheme.colorScheme.primary` | `OnboardingStyleTest` |
| The setup is theme-driven and must not import the stage | `OnboardingStyleTest` |
| Both halves press the same gradient CTA; no raw Material `Button` survives in either | `OnboardingStyleTest` |
| `codecAccentGradient` has one definition and one user (the themed half) | `OnboardingStyleTest` |
| The stage's 12 consts are all drawn | `OnboardingStyleTest` |
| The CTA has a pressed scale **and** wash, a disabled fill, no hover, a 48 dp floor inside the component, `role = Button`, no ripple, and reduce-motion awareness | `OnboardingStyleTest` |
| The hand-painted bar keeps `progressBarRangeInfo` and clamps; its track is the surface, not `surfaceVariant` | `OnboardingStyleTest` |
| Every stage text pair clears 7:1 | `OnboardingContrastTest` |
| The CTA's dark ink clears 4.5:1 on **both** ends, for all six accents × two themes | `OnboardingContrastTest` |
| The bar's fill clears 3:1 on its track, for all twelve combinations | `OnboardingContrastTest` |
| The chosen card's text clears 4.5:1 and its tick 3:1 on the tint | `OnboardingContrastTest` |
| The rest of the flow's behaviour (the beats, the gate, the seeding, the copy) is untouched | `SetupFlowWiringTest`, `SetupFlowPolicyTest`, `SetupFlowCopyTest`, `SetupFlowArtTest`, `IntroStoriesTest`, `MotionWiringTest`, `TokenAdoptionTest` |

## 6. What still bites (honest list)

- **No device round.** The stage's depth, the card's radius, the ring around the
  welcome mark and the press feel are reasoned, not seen. The press state (0.97
  scale + 18 % wash) is the piece most likely to want a second pass on hardware.
- **The setup and the tour do not share one palette by construction** — only by
  consequence (a fresh install is dark). If a future phase changes the dark
  theme's surface, the family resemblance narrows; `OnboardingContrastTest`
  would not catch that, because it measures each half against its own surfaces.
- **`STAGE_CHIP` is a fixed green-tinted chip** on the tour, while the setup's
  chip is `primaryContainer`. Two chips, two derivations — intentional (§2), but
  it is the one place the seam is visible side by side.
- **Nine ratios are pinned as "≥ threshold" plus two as equalities.** If the
  design moves, the thresholds move with it; the equality pins (14 % and 35 %)
  are the ones that will fail loudly, which is the intent.
- **Icons.** The brief asked for "sleek custom icons": this phase reuses the
  app's existing icon set rather than drawing a new one, so the *style* claim is
  about the surfaces around the icons, not new glyphs. Drawing a new onboarding
  icon set is a separate piece of work and was not in the owner's fix list.
- **Not compiled locally** (no JDK/Android SDK in the authoring environment): CI
  is the compiler, as in Phases 97–99.
