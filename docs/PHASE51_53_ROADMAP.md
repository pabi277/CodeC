# CodeC — Phases 51–53 · the "100× more attractive" roadmap

> **Owner (2026-09-13, verbatim):** *"research online, open source, real humans
> thought on the topic of policed ui / No the main problem is it's not
> attractive to user to use multiple time so i want to boost it's ui 100× time
> understand"*
>
> **Clarified in chat the same day:** *policed* = **polished** · the goal is
> **both** halves — *"the first 10 seconds — it must look gorgeous on open"*
> **and** *"coming back — the daily-return habit"* · **three** phases, numbered
> in a row after Phase 50.
>
> **Status: 📋 PLANNED — docs only, no app code.** Research dossier (every
> external claim carries its source, every claim about this app carries a
> `file:line` or a grep, read on `main` @ `62cfe7b`):
> [`PHASE51_53_UX_RESEARCH.md`](PHASE51_53_UX_RESEARCH.md). Per-phase specs:
> `docs/chat-phase51/`, `docs/chat-phase52/`, `docs/chat-phase53/`.
>
> **The phase recipe this series follows is now written down:**
> [`docs/HOW_TO_CREATE_A_PHASE.md`](HOW_TO_CREATE_A_PHASE.md).

---

## The owner's one row → three phases

| Owner's words | Phase | Title | Effort |
|---|---|---|---|
| *"it's not attractive"* | **51** | **The look — one CodeC design language** | L |
| *"not attractive to user to use multiple time"* (the surfaces you touch every session) | **52** | **The feel — the screens you touch every day** | L |
| *"to use multiple time"* (the return) | **53** | **The return — it remembers you, and it never feels slow** | L |

One command per phase, in a row: **"Start Phase 51"** → 52 → 53. The numbers
*are* the order, and the order is forced by the evidence, not by taste:

1. **51 first** — you cannot repaint six screens against a scale that does not
   exist. Today the app has **801 raw `dp` literals, 10 different corner radii
   and no type scale** (dossier §2.1); a screen-by-screen repaint would ship the
   same inconsistency three times.
2. **52 second** — spend the tokens on the four surfaces a user touches in every
   session (first run / editor / hub / packages+terminal), and give the app
   motion and feedback, of which it currently has **one** `AnimatedVisibility`
   and **zero** animation specs (dossier §2.2).
3. **53 last** — the return habit, which is the *only* part of "100×" that is
   measurable in the field (D1/D7), plus the proof: screenshot goldens and the
   device round.

---

## 51 — The look: one CodeC design language

```text
  51.1  Tokens: one spacing / radius / elevation / icon scale, pinned by a test
  51.2  Identity & colour: a brand that survives dynamic colour (Phase 40.5 law)
  51.3  Type & iconography: a real type scale, one icon size per role
  51.4  Motion: CodecMotion (springs + durations) and the first six transitions
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [51.1](chat-phase51/PART_51_1_TOKENS.md) | One scale, not 801 numbers | M | 📋 PLANNED |
| [51.2](chat-phase51/PART_51_2_IDENTITY_COLOR.md) | A brand you can see on Android 12+ | M | 📋 PLANNED |
| [51.3](chat-phase51/PART_51_3_TYPE_AND_ICON.md) | A type scale and one icon set | S/M | 📋 PLANNED |
| [51.4](chat-phase51/PART_51_4_MOTION.md) | Motion, for the first time | M | 📋 PLANNED |

**Why these four.** They are the four things every "polished" app review in the
dossier names, and CodeC is missing all four: no spacing scale, no type scale,
no brand colour by default (dynamic colour wins on the owner's own Android 12+
phones — `Theme.kt:43-70`), and no motion at all.

**Exit condition.** `CodecTokens` + `CodecMotion` exist, are used by the six
core surfaces, and are pinned by source-scan tests; the app has **≥1 spring**;
contrast gates (`AppContrastTest`, `ChromeContrastTest`) stay green; **zero**
new dependencies.

**Biggest risk.** A token refactor touches every screen. 51.1 therefore ships
the *scale* and converts the **six core surfaces** (welcome, editor chrome, hub,
packages, terminal chrome, settings), not all 61,000 lines — and the test pins
that a changed file uses the token, not that the whole app is converted.

---

## 52 — The feel: the screens you touch every day

```text
  52.1  The first ten seconds: a splash that is CodeC, and a first screen with a face
  52.2  The editor surface: RUN ▶ as the hero, chrome with rhythm, a real empty state
  52.3  Hub, Packages, Terminal: cards with identity, skeletons, the install moment
  52.4  Micro-feedback: press states, haptics on the eight moments, confirmations
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [52.1](chat-phase52/PART_52_1_FIRST_TEN_SECONDS.md) | Cold start and the first screen | M | 📋 PLANNED |
| [52.2](chat-phase52/PART_52_2_EDITOR_SURFACE.md) | The editor, with RUN ▶ as the hero | L | 📋 PLANNED |
| [52.3](chat-phase52/PART_52_3_HUB_PACKAGES_TERMINAL.md) | Hub / Packages / Terminal surfaces | M | 📋 PLANNED |
| [52.4](chat-phase52/PART_52_4_MICRO_FEEDBACK.md) | Haptics, press states, confirmations | S/M | 📋 PLANNED |

**Why these four.** D1 retention is decided in the first session
(dossier §3.6: onboarding completers retain 2–3×; a meaningful first action
within 3 minutes). For CodeC that first action is **write a line → RUN ▶ → see
output**, so 52.1 owns the seconds before it and 52.2 owns the button itself —
Google's own eye-tracking says a bigger, better-contained primary action is
found **up to 4× faster** (dossier §3.2). 52.3 and 52.4 are what make the *rest*
of the app feel like the same product.

**Exit condition.** A cold start shows CodeC's own splash, not a black window
(`themes.xml` today is one line); the editor's primary action is measurably the
largest, most contained control on the surface (pinned by a test that reads the
sources); every list has a designed empty/loading state; eight named moments
give a haptic. **One** new dependency: `androidx.core:core-splashscreen`
(Apache-2.0).

**Biggest risk.** Touching `EditorScreen.kt` (2,423 lines) — the most
device-tested file in the app (Phases 33, 35, 44, 45, 46, 47, 48, 49 all landed
there). 52.2 is therefore **chrome-only**: no change to the sora host, the caret
policy, the run pipeline or the chrome lock.

---

## 53 — The return: it remembers you, and it never feels slow

```text
  53.1  Continuity: resume you can see, and decline
  53.2  Perceived speed: first paint, jank budget, skeletons instead of blanks
  53.3  Progress without nagging: the streak the app already counts, shown once
  53.4  The proof: Roborazzi goldens + the look-and-feel device round
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [53.1](chat-phase53/PART_53_1_RESUME.md) | "Continue where you left off", visibly | M | 📋 PLANNED |
| [53.2](chat-phase53/PART_53_2_PERCEIVED_SPEED.md) | Jank budget + measured first paint | M | 📋 PLANNED |
| [53.3](chat-phase53/PART_53_3_PROGRESS_WITHOUT_NAGGING.md) | The streak in About, once | S | 📋 PLANNED |
| [53.4](chat-phase53/PART_53_4_PROOF.md) | Screenshot goldens + device round | M | 📋 PLANNED |

**Why these four.** The owner's *"to use multiple time"* is D7, and the
diagnostic for a D1→D7 collapse (dossier §3.6) is *"the second and third
sessions were not compelling enough to create return behaviour"*. Two facts make
this phase cheap and honest:

- **The machinery already exists and is invisible.** `StatsManager` already
  computes `CURRENT_STREAK` with a correct today/yesterday rule
  (`ui/stats/StatsManager.kt:17-60`) and **nothing reads it**;
  `EditorLaunchState` already restores the last file and is applied silently at
  `MainActivity.kt:807`.
- **The measuring instruments already exist.** `bench/` has `FrameStats` +
  `FrameCapture`; Roborazzi 1.59.0 is declared, plugin-applied and **unused**.

**Exit condition.** Resume is visible and decline-able (a pure
`ResumePolicy`), the frame budget is measured on the bench APK and recorded,
the streak is displayed exactly once per day in a non-modal place, and ≥12
screenshot goldens run in CI.

**Laws this phase must not break:** no new telemetry, no notifications, no
modal nag (Phases 41/42/45), and Back must still do exactly what Phase 49
decides.

---

## Cost, dependencies and the byte budget

| Item | Cost |
|---|---|
| New runtime dependency | **one** — `androidx.core:core-splashscreen` (Apache-2.0), 52.1 |
| New test-only dependency | **none** — Roborazzi is already declared and applied |
| New permission / DataStore key / Settings control / telemetry | **none** (a Settings row is only added if the owner asks for the font question in 51.3) |
| Fonts | 0 bytes by default (system `FontFamily.Monospace`); JetBrains Mono is OFL-1.1 and needs an explicit owner decision |
| APK budget | the repo measures every round (release APK is 6,681,306 B today, v1.3.17). Every part doc records its own delta the way 44–49 did. |

---

## Device rounds (the gate)

Each phase ends with owner-run rows; the format is Phase 50's
(`docs/chat-phase50/DEVICE_MATRIX.md`), and each row's result is pasted back
into the **owning part doc**, not a scratchpad.

> **Concurrent work (recorded 2026-09-14):** while this plan was written,
> Phase 50's cross-device matrix became 🚧 IMPLEMENTED on `arena/01a099d8-codec`
> — `app/src/test/java/com/codeci/ide/DeviceMatrixTest.kt` pins ten rounds A-J
> for phases **44.1-49.2**, CI ✅ GREEN `34753000709` tip `69a70ec`. 51-53 are
> unaffected (its `PART_FILES` map is fixed and covers 44.1-49.2 only), and the
> three rounds below deliberately adopt its `## Test log (Phase NN — …)`
> convention so the two systems compose instead of competing.

| Phase | Round | Rows |
|---|---|---|
| 51 | `docs/chat-phase51/DEVICE_ROUND.md` | L1-L12 — one screen per row, dark + light, small + large phone: spacing looks even, corners match, the accent is visible, nothing regressed in contrast |
| 52 | `docs/chat-phase52/DEVICE_ROUND.md` | F1-F16 — cold start, first run, RUN ▶ visibility, keyboard up vs down, hub scroll, package install moment, haptics on/off |
| 53 | `docs/chat-phase53/DEVICE_ROUND.md` | R1-R12 — resume card, decline, cold-open timing (stopwatch), jank on a big file, the streak row |

**Not verifiable in this sandbox and never claimed as verified:** IME insets,
real haptics, perceived smoothness, OEM behaviour. CI proves the pure policies
and the goldens; the owner's handsets prove the feel.

---

## Definition of done (per phase, `rule.md` §8)

1. Evidence first — `file:line` reads, not memory.
2. Pure policy + wiring pin + host tests, pre-validated locally.
3. No invariant broken; no new dependency beyond the one named here.
4. Docs updated in the same commit: part doc → phase README → `JOURNEY.md` →
   `NEXT_STEPS.md` → `rule.md` §9.
5. `Build APK` green; run id recorded; APK delta recorded.
6. Report: what changed, tip sha, run id, **device pass required**.
7. Stop at the merge gate — the owner merges.
