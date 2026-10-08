# Phase 100 — the design pass: a stage for the tour, a theme for the setup, and three defects the measurement found

> **Status: ✅ IMPLEMENTED (2026-10-08) on `arena/db36a12a-codec`; CI ✅ GREEN on `126f7f7`
> (Build APK [`37770926970`](https://github.com/pabi277/CodeC/actions/runs/37770926970));
> superseded on the tour by [Phase 101](../chat-phase101/README.md); merged to `main` via PR #118.**
>
> **Authorization:** the owner's design brief, verbatim in spirit — *minimalist modern, deep dark
> backdrop, crisp high-contrast type, sleek custom icons, smooth gradient accents, well-defined
> button states, card-based layouts*. **Restyle, do not re-cut:** no beat, no copy and no asset
> changed in this phase. (The tour-only parts of the brief — custom icons, the black canvas — were
> authorised afterwards and shipped as Phase 101.)

## 1. What changed

| File | What it is |
|---|---|
| `ui/theme/OnboardingStage.kt` | **new** — the tour's fixed palette (12 consts), `backdrop()`, `accent()`, `iconGlow()`, and the shared `codecAccentGradient(primary, tertiary)` |
| `ui/components/GradientButton.kt` | **new** — a `Button` whose container is the gradient (M3 cannot take a `Brush` as a container colour), with a real disabled state, `indication = null`, and ≥ 48 dp |
| `ui/components/GradientProgress.kt` | **new** — a gradient bar M3's `LinearProgressIndicator` cannot be, with `progressSemantics` and the `surface` track |
| `ui/theme/CodecPalette.kt` | `ACCENT_CHOICES`, `ART_CARD_BACKDROP`, `DEFAULT_ACCENT = IDENTITY_GREEN` |
| `ui/setup/SetupFlowScreen.kt` | `ChoiceCard` rebuilt; the primary button and the build bar swapped for the two components above |
| `ui/screens/FirstRunIntroScreen.kt` | every hard-coded colour replaced by a stage token; **zero `MaterialTheme.colorScheme`** left in the file |

## 2. The one decision worth arguing about: a stage for the tour, a theme for the setup

The brief asked for a deep dark backdrop on both halves. On the setup it is **wrong**: the flow's
fourth beat, *LOOKS*, lets the user pick **Dark / Light / Auto** and the choice is applied **live** —
the screen the choice is made on is the screen that changes. A fixed backdrop there would either
lie to the user or be re-themed a frame later. So the two halves get different rules, and both are
pinned:

| | Tour | Setup |
|---|---|---|
| Backdrop | `OnboardingStage.backdrop()` (fixed) | `MaterialTheme.colorScheme.background` (the user's) |
| Text | `ON_STAGE` (white) + `ON_STAGE_MUTED` (fixed) | `on*` roles |
| Accent | `OnboardingStage.accent()` (fixed green → teal) | `codecAccentGradient(scheme.primary, scheme.tertiary)` |
| Gradient | the fixed accent, everywhere | the **user's** accent, so the CTA, the ring, the chip hairline and the build bar all agree with the editor they are about to open |

`OnboardingStyleTest` holds the line from both sides: the tour file may not contain
`MaterialTheme.colorScheme`, and every `color =` in it must be a stage token.

## 3. The palette, and every ratio it was chosen by

`docs/phases/09-onboarding-setup/chat-phase100/PART_100_STAGE_AND_GRADIENTS.md` §3 carries the
table; the numbers that matter are in `OnboardingContrastTest`:

- **White on the stage:** 21.00:1 (backdrop top), 19.91:1 (bottom), 19.16:1 (card), 16.85:1 (chip).
  The tour's only text colour is `ON_STAGE` — a pure-white line on a near-black ground, which is
  what "crisp high-contrast type" means numerically.
- **The CTA's ink:** `ON_ACCENT` **9.14:1** on the gradient's green end and **7.48:1** on its teal end.
- **The accent is lightness-corrected:** `onPrimary` on primary *and* tertiary is ≥ 4.50:1 for **all
  six** accents in **both** themes (worst case 4.61:1 — Blue's tertiary in the light theme) and equals
  `Contrast.onColorFor(primary)` — so the authored `onPrimary` is provably the measured one.
- **The build bar:** the fill is ≥ 3.0:1 (`AA_NON_TEXT`) against the surface it sits on in every
  combination, and the **resting** `ChoiceCard` edge (`scheme.outline`) is 5.41:1 dark / 4.44:1 light
  against its surface.

## 4. Three real defects the measurement found

The brief said "high-contrast type". Measuring the things the redesign touches turned up three
problems no screenshot would have shown:

1. **The build bar's track.** `surfaceVariant` at 6 dp under an accent gradient measured as low as
   **2.46:1** (Violet, Teal, dark) — under the 3:1 floor for a graphical object, i.e. a progress bar
   whose fill cannot be told from its track. `GradientProgress` defaults its track to the **surface**
   the bar sits on, so the fill is ≥ 4.50:1 in all twelve combinations; a 1 dp `outlineVariant` lane
   keeps the empty part visible.
2. **The chosen card's sample line.** Drawn in `primary` on the card's own `primary` tint it measured
   **3.96–4.18:1** (Phase 99's 10 % tint; 3.86–4.11:1 at this phase's 12 %) — body text under AA.
   It now stays `onSurfaceVariant` (≥ 7.26:1 on the tint) while the *state* is carried by the tick,
   the 2 dp gradient edge, and the `selected` semantics TalkBack reads.
3. **The resting card has no visible border in the dark baseline** — in M3's `darkColorScheme()`,
   `outlineVariant` and `surfaceVariant` are the *same* value (`#49454F`), so a resting card is
   defined by its fill, not by an edge. Recorded rather than "fixed": the chosen/un-chosen
   difference is a tick and a gradient edge, a stronger signal than a hairline.

## 5. Laws, and where they are pinned

- **`codecAccentGradient` is the only gradient** — `OnboardingStyleTest` allows exactly one
  definition and counts its users.
- **`GradientButton` keeps a real disabled state**: a disabled container is dimmed and inert (not a
  gradient with the tap switched off in a lambda), a press is a 0.97 scale with an 0.18 wash, and
  `indication = null` because a ripple over a gradient is a third colour. It is ≥ 48 dp, like the
  other eight interactive helpers `SetupFlowWiringTest` counts.
- **`GradientProgress` keeps `progressSemantics`** — a gradient must not cost accessibility.
- **Never assert `onPrimary` on `surfaceVariant`** (2.46–5.91:1 depending on accent); **never assert
  a hairline** — `OnboardingContrastTest` asserts text pairs and `outline`-vs-surface edges only.
- **The tour's controls are words, not bare glyphs** — four `TextButton(` sites and no `IconButton(`
  in the file, so nothing is a glyph without a label (Phase 101 later added the launcher mark).
- Every phase-100 claim is a repo-grep: `IconRoleTest`, `TokenAdoptionTest` (core-DP 0),
  `ComposableAnnotationTest` (225 annotations / 0 missing), `OnboardingStyleTest` (11).

## 6. What still bites (honest list)

- **`ART_CARD_BACKDROP = #F1F7F2` is baked into the renders.** The card is a pale ground on a dark
  stage on purpose — the 3D renders were composed against it — but "make the card dark too" would
  need a re-bake in the render → WebP step, not a colour change. Open.
- **The tour's own type is fixed, so it does not follow the user's text size.** Deliberate (the tour
  is a stage), recorded here so it is not mistaken for a bug.
- **`OnboardingStyleTest` pins are positional** — they count `Text(` / `color =` sites in a file, so
  a later edit has to re-run them, which is exactly what Phase 101 did.

## 7. Record

- The spec: [`PART_100_STAGE_AND_GRADIENTS.md`](PART_100_STAGE_AND_GRADIENTS.md)
- The measured ramp and every ratio: computed with the same formulas [`Contrast.kt`](../../../../app/src/main/java/com/codeci/ide/ui/theme/Contrast.kt) ships
- The spec sheet: `/home/user/onboarding-stage-spec.png` (working tree)
- What came next: [Phase 101](../chat-phase101/README.md) — black canvas, white type, custom icons,
  the indicator pager and the quicker auto-advance
