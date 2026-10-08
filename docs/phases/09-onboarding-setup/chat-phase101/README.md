# Phase 101 — the black canvas: one white ink, five custom 3D icons, and a quieter clock

> **Status: ✅ IMPLEMENTED (2026-10-08) on `arena/db36a12a-codec` @ `2643aa9`; Build APK
> [`37776053185`](https://github.com/pabi277/CodeC/actions/runs/37776053185) ✅ GREEN, and the
> PR's own `pull_request` run [`37781090447`](https://github.com/pabi277/CodeC/actions/runs/37781090447)
> ✅ GREEN (14m05s); **merged to `main` via PR #118**.**
>
> **Authorization:** the owner's second design brief, verbatim in substance — **custom 3D icons for
> the five tour steps**, a **deep black background** with **pure white** text so the black canvas
> makes **every card glow** (glow slightly stronger), a **clean five-card indicator pager**, a **less
> prominent Pause timer**, **faster auto-advance**, well-defined option cards, smooth gradients, and a
> prominent **"Start coding"** CTA on the final screen.
>
> **Scope law that did not move:** five screens stay five, the copy budget (`MAX_WORDS = 12`) holds,
> and every claim in this phase is a measured ratio or a repo-grep — not a screenshot.

## 1. What shipped

| Piece | What changed |
|---|---|
| **The stage** | `#000000 → #060A08` backdrop, card `#0C100E`, chip `#122019`, and **one ink**: `ON_STAGE = #FFFFFF`. `ON_STAGE_MUTED` and `ON_STAGE_CHIP` are **deleted** — a second grey ink on a pure white stage is a contrast bug waiting to happen |
| **Derived, not invented** | `STAGE_STROKE` = white @ 20 % over the card, `STAGE_LANE` = white @ 12 %, `STAGE_GLOW` = the accent @ 22 %. The tests assert the **composite equality**, so a hairline can never drift away from the card it edges |
| **Five custom 3D icons** | `intro_0{1..5}_*.webp` replaced in place (same names, same `R.drawable` ids): one centred glossy object each — the phone, the terminal, the toolbox, the gamepad, the shield. **128.3 KB → 75.0 KB** |
| **The icon tile** | a 168 dp render in a rounded tile with a 2 dp **gradient ring**, sitting on a 1.3× accent halo whose alpha rides the same `reveal` as the art |
| **Start coding** | `BUTTON_FINISH = "Start coding"` on the last screen, drawn by the gradient CTA |
| **The quieter clock** | `CodecMotion.Duration.STORY` **10 000 → 8 000 ms**; the visible word is now just **"Pause"** / **"Resume"** (the screen reader still hears *"Pause timer"* / *"Resume timer"*) |
| **The option card** | the **resting** card gets a real edge — `scheme.outline`, not `outlineVariant`, which in M3's dark baseline *is* `surfaceVariant`, i.e. the card's own fill |
| **The opening** | the logo splash and the privacy dialog are **staged**: they were drawn by `MaterialTheme`, so a light-mode phone flashed white before the black tour |

**Not touched, on purpose:** the five pages, their order, their words, the six setup beats, the
setup's live re-theming, and `SetupFlowCopy.ALL_COPY = 101`.

## 2. The measurements

Produced by `Contrast.ratio` / `Contrast.composite` in host tests — the app's own maths.

| Pair | Ratio | Floor |
|---|---|---|
| `ON_STAGE` (#FFFFFF) on `STAGE_TOP` (#000000) | **21.00:1** | AA 4.5 · AAA 7 |
| `ON_STAGE` on `STAGE_BOTTOM` (#060A08) | **19.91:1** | " |
| `ON_STAGE` on `STAGE_CARD` (#0C100E) | **19.16:1** | " |
| `ON_STAGE` on `STAGE_CHIP` (#122019) | **16.85:1** | " |
| `ON_ACCENT` (#06251A) on `ACCENT_FROM` (#3DDC84) | **9.14:1** | " |
| `ON_ACCENT` on `ACCENT_TO` (#2BC4B0) | **7.48:1** | " |

21.00:1 is the maximum ratio WCAG defines — white on black. On this half there is no readability
left to buy, which is what "pure white on a deep black canvas" means numerically. The setup half
keeps its measured accent law from Phase 100 (`onPrimary` ≥ 4.50:1 for all six accents × two
themes, worst 4.61:1; the build bar's fill ≥ 3.0:1 on its surface; the resting card edge 5.41:1
dark / 4.44:1 light).

## 3. The five icons

One centred object per promise, rendered as a **custom 3D icon** rather than a scene: the phone
(offline), the terminal (the edit-run loop), the toolbox (the Linux tools), the gamepad (the
samples), the shield (the privacy beat). They are the project's own renders — no third-party art,
no attribution burden, no network fetch — and the tile frames the same `StepArt` card Phase 99
introduced, so there is still exactly **one** illustration path.

The **card ground stays `ART_CARD_BACKDROP = #F1F7F2`**: the pale ground is baked into the
renders, and the glow reads *because* the card is light on black. Re-baking the ground dark is
recorded as an **open question**, not a silent change.

## 4. The clock, and why 8 seconds

The auto-advance is a **fallback pace, not the reading pace** — swiping is the real control, and
the hint says so. The timer's job is to keep the tour moving for a reader who never touches it;
8 s keeps every page on screen for more than the ~400 ms a person needs to register a new object
while cutting the "why is this waiting for me" tail that the brief asked about. The ellipsis runs
5–8 s by `OnboardingStyleTest`'s clock pin, and `CodecMotionTest` pins the constant itself.

Pause is deliberately quiet: one word, a 48 dp target, and the same `storyTimer` state machine.
**A pause is still a pause** — resuming re-computes `remainingMs` from the value the `Animatable`
already holds, so no elapsed time is returned.

## 5. Test surface

- **`OnboardingContrastTest` (8)** — rewritten for the black stage: white/black ≥ 20.9 and the
  accent ends < 1.2 from each other, the ink on all four stage surfaces ≥ 7.0, the stroke/lane
  composites (α 89 and 56 pinned), the glow in both its composited and raw forms, `ON_ACCENT` ≥ 7,
  the setup CTA across six accents × two themes, the build bar ≥ `AA_NON_TEXT`, and the 12 % tint's
  text pairs.
- **`OnboardingStyleTest` (11)** — the tour is theme-free and the setup is un-staged; **every**
  `color =` in the tour is a stage token and every `Text` is one ink; the tile frames the unchanged
  `StepArt` / `description` / `reveal`; one gradient definition and its users; twelve stage constants
  with ≥ 2 references each; `GradientButton`'s state law; `GradientProgress`'s semantics and track;
  `ChoiceCard`'s edge; the 5–8 s clock.
- **Unchanged, still green:** `SetupFlowWiringTest` (9), `SetupFlowCopyTest` (11), `SetupFlowArtTest`
  (6), `IntroStoriesTest` (8), `CodecMotionTest`, `TouchTargetTest`, `TokenAdoptionTest`,
  `ComposableAnnotationTest` (225 annotations / 0 missing).

## 6. CI, honestly — three rounds, two of them the same family of trap

| Run | Head | Cause |
|---|---|---|
| `37770222795` | `126f7f7` | missing `androidx.compose.foundation.border` import (`FirstRunIntroScreen.kt:299:30`) |
| `37770560796` | — | `String.count(substring)` does not exist (it takes a predicate) |
| `37770926970` | `126f7f7` | **green** (Phase 100's own round) |
| `37774952759` | `ea61522` | `Regex.findFirstMatchIn` does not exist on this JVM surface — use the member `find()` |
| `37775444618` | `ea61522` | `OnboardingContrastTest.kt:117` — `java.lang.AssertionError` (3 508 tests, 1 failed) |
| [`37776053185`](https://github.com/pabi277/CodeC/actions/runs/37776053185) | **`2643aa9`** | **green** — release artifact `6 770 120 B`, debug `26 499 088 B`, mapping `4 786 246 B` |
| [`37781090447`](https://github.com/pabi277/CodeC/actions/runs/37781090447) | `2643aa9` | **green** — the PR's own run, 14m05s |

The `:117` failure is worth one line of history: the pin compared the glow's hue against the
accent with `and 0x00FFFFFF`, but `0xFF…` is a **negative** `Int` in Kotlin, so the glow side never
equalled the accent side. The fix masks **both** sides. Only simulating the assertion host-side
caught it — a compile check cannot, and neither could a screenshot.

## 7. Deliberately not done

- **No sixth page, no seventh beat** — the count is the owner's standing instruction, and the copy
  budget is a test.
- **No re-bake of the card ground** (see §3) and **no second illustration path**.
- **No goal/level questions**, no store words, no "level" labels — `IntroStoriesTest` re-uses the
  setup's banned list verbatim.
- **No new dependency.** Everything here is Compose foundation/material3 and the project's own
  tokens.

## 8. Record

- The spec: [`PART_101_BLACK_AND_WHITE.md`](PART_101_BLACK_AND_WHITE.md)
- The four-panel spec sheet: `/home/user/onboarding-phase101.png` (912 × 1848, working tree);
  the stage palette sheet: `/home/user/onboarding-stage-spec.png`
- Icon masters: `uploads/intro/icons/` (working tree); the replaced posters: `uploads/intro/posters/`
- The rest of the branch: [Phase 97](../chat-phase97/README.md), [98](../chat-phase98/README.md),
  [99](../chat-phase99/README.md), [100](../chat-phase100/README.md) — merged together as PR #118
