# CodeC — Phases 50–52 · the "100× more attractive" roadmap

> **Owner (2026-09-13, verbatim):** *"research online, open source, real humans
> thought on the topic of policed ui / No the main problem is it's not
> attractive to user to use multiple time so i want to boost it's ui 100× time
> understand"*
>
> **Clarified in chat the same day:** *policed* = **polished** · the goal is
> **both** halves — *"the first 10 seconds — it must look gorgeous on open"*
> **and** *"coming back — the daily-return habit"* · **three** phases, numbered
> in a row after Phase 49.
>
> **Status: 🚧 IN PROGRESS — 50 ✅ merged, 51 ✅ merged + device-passed,
> 52 📋 planned (next).** Research dossier (every
> external claim carries its source, every claim about this app carries a
> `file:line` or a grep, read on `main` @ `62cfe7b`):
> [`PHASE50_52_UX_RESEARCH.md`](PHASE50_52_UX_RESEARCH.md). Per-phase specs:
> `docs/chat-phase50/`, `docs/chat-phase51/`, `docs/chat-phase52/`.
>
> **The phase recipe this series follows is now written down:**
> [`docs/HOW_TO_CREATE_A_PHASE.md`](HOW_TO_CREATE_A_PHASE.md).
>
> **Renumbered, and the cross-device matrix is ❌ CANCELLED (owner decision,
> 2026-09-14):** the owner's law is *"number the phases like i go in a row"* —
> the numbers **are** the execution order. The cross-device round therefore
> moved from **50 to 53** … and the owner then cancelled it outright
> (*"Remove the full device cross check phase"*). This series (the look / the
> feel / the return) took the free numbers **50, 51, 52** and is the whole of
> the plan now. **What proves things on a phone instead:** one short per-phase
> round on the owner's own handsets (L1-L12, F1-F16, R1-R12), CI as the executor
> of record, and 52.4's Roborazzi screenshot goldens. The still-owed device rows
> of 44/45/46/47/48/49 are untouched — they belong to their own phases and were
> never part of 53.

---

## The owner's one row → three phases

| Owner's words | Phase | Title | Effort |
|---|---|---|---|
| *"it's not attractive"* | **50** | **The look — one CodeC design language** | L |
| *"not attractive to user to use multiple time"* (the surfaces you touch every session) | **51** | **The feel — the screens you touch every day** | L |
| *"to use multiple time"* (the return) | **52** | **The return — it remembers you, and it never feels slow** | L |

One command per phase, in a row: **"Start Phase 50"** → 51 → 52. The numbers
*are* the order, and the order is forced by the evidence, not by taste:

1. **50 first** — you cannot repaint six screens against a scale that does not
   exist. Today the app has **801 raw `dp` literals, 10 different corner radii
   and no type scale** (dossier §2.1); a screen-by-screen repaint would ship the
   same inconsistency three times.
2. **51 second** — spend the tokens on the four surfaces a user touches in every
   session (first run / editor / hub / packages+terminal), and give the app
   motion and feedback, of which it currently has **one** `AnimatedVisibility`
   and **zero** animation specs (dossier §2.2).
3. **52 last** — the return habit, which is the *only* part of "100×" that is
   measurable in the field (D1/D7), plus the proof: screenshot goldens and the
   device round.

---

## 50 — The look: one CodeC design language

```text
  50.1  Tokens: one spacing / radius / elevation / icon scale, pinned by a test
  50.2  Identity & colour: a brand that survives dynamic colour (Phase 40.5 law)
  50.3  Type & iconography: a real type scale, one icon size per role
  50.4  Motion: CodecMotion (springs + durations) and the first six transitions
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [50.1](chat-phase50/PART_50_1_TOKENS.md) | One scale, not 801 numbers | M | ✅ DONE |
| [50.2](chat-phase50/PART_50_2_IDENTITY_COLOR.md) | A brand you can see on Android 12+ | M | ✅ DONE |
| [50.3](chat-phase50/PART_50_3_TYPE_AND_ICON.md) | A type scale and one icon set | S/M | ✅ DONE |
| [50.4](chat-phase50/PART_50_4_MOTION.md) | Motion, for the first time | M | ✅ DONE |

**Why these four.** They are the four things every "polished" app review in the
dossier names, and CodeC is missing all four: no spacing scale, no type scale,
no brand colour by default (dynamic colour wins on the owner's own Android 12+
phones — `Theme.kt:43-70`), and no motion at all.

**Exit condition.** `CodecTokens` + `CodecMotion` exist, are used by the six
core surfaces, and are pinned by source-scan tests; the app has **≥1 spring**;
contrast gates (`AppContrastTest`, `ChromeContrastTest`) stay green; **zero**
new dependencies.

**Biggest risk.** A token refactor touches every screen. 50.1 therefore ships
the *scale* and converts the **six core surfaces** (welcome, editor chrome, hub,
packages, terminal chrome, settings), not all 61,000 lines — and the test pins
that a changed file uses the token, not that the whole app is converted.

---

## 51 — The feel: the screens you touch every day

```text
  51.1  The first ten seconds: a splash that is CodeC, and a first screen with a face
  51.2  The editor surface: RUN ▶ as the hero, chrome with rhythm, a real empty state
  51.3  Hub, Packages, Terminal: cards with identity, skeletons, the install moment
  51.4  Micro-feedback: press states, haptics on the eight moments, confirmations
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [51.1](chat-phase51/PART_51_1_FIRST_TEN_SECONDS.md) | Cold start and the first screen | M | ✅ DONE |
| [51.2](chat-phase51/PART_51_2_EDITOR_SURFACE.md) | The editor, with RUN ▶ as the hero | L | ✅ DONE |
| [51.3](chat-phase51/PART_51_3_HUB_PACKAGES_TERMINAL.md) | Hub / Packages / Terminal surfaces | M | ✅ DONE |
| [51.4](chat-phase51/PART_51_4_MICRO_FEEDBACK.md) | Haptics, press states, confirmations | S/M | ✅ DONE |

> **Phase 51 status (2026-09-21):** ✅ **COMPLETE, DEVICE-PASSED & MERGED** — owner:
> *"Start phase 51"*, then *"All pass record and merge"* on device rows F1-F16 +
> F17-F20 (all PASS; the record is the owner's own report, kept verbatim).
> [PR #82](https://github.com/pabi277/CodeC/pull/82); 132 new host cases; APK
> delta +73,884 B / +0.29 % debug, +27,066 B / +0.40 % release. What actually
> landed — with the three plan premises the code corrected — is recorded in
> [`chat-phase51/README.md`](chat-phase51/README.md) §Implementation.
> Phase 50 (the look) is ✅ **COMPLETE & MERGED** ([PR #81](https://github.com/pabi277/CodeC/pull/81),
> `main` @ `0f1b650`).

**Why these four.** D1 retention is decided in the first session
(dossier §3.6: onboarding completers retain 2–3×; a meaningful first action
within 3 minutes). For CodeC that first action is **write a line → RUN ▶ → see
output**, so 51.1 owns the seconds before it and 51.2 owns the button itself —
Google's own eye-tracking says a bigger, better-contained primary action is
found **up to 4× faster** (dossier §3.2). 51.3 and 51.4 are what make the *rest*
of the app feel like the same product.

**Exit condition.** A cold start shows CodeC's own splash, not a black window
(`themes.xml` today is one line); the editor's primary action is measurably the
largest, most contained control on the surface (pinned by a test that reads the
sources); every list has a designed empty/loading state; eight named moments
give a haptic. **One** new dependency: `androidx.core:core-splashscreen`
(Apache-2.0).

**Biggest risk.** Touching `EditorScreen.kt` (2,423 lines) — the most
device-tested file in the app (Phases 33, 35, 44, 45, 46, 47, 48, 49 all landed
there). 51.2 is therefore **chrome-only**: no change to the sora host, the caret
policy, the run pipeline or the chrome lock.

---

## 52 — The return: it remembers you, and it never feels slow

```text
  52.1  Continuity: resume you can see, and decline
  52.2  Perceived speed: first paint, jank budget, skeletons instead of blanks
  52.3  Progress without nagging: the streak the app already counts, shown once
  52.4  The proof: Roborazzi goldens + the look-and-feel device round
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [52.1](chat-phase52/PART_52_1_RESUME.md) | "Continue where you left off", visibly | M | 📋 PLANNED |
| [52.2](chat-phase52/PART_52_2_PERCEIVED_SPEED.md) | Jank budget + measured first paint | M | 📋 PLANNED |
| [52.3](chat-phase52/PART_52_3_PROGRESS_WITHOUT_NAGGING.md) | The streak in About, once | S | 📋 PLANNED |
| [52.4](chat-phase52/PART_52_4_PROOF.md) | Screenshot goldens + device round | M | 📋 PLANNED |

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
| New runtime dependency | **one** — `androidx.core:core-splashscreen` (Apache-2.0), 51.1 |
| New test-only dependency | **none** — Roborazzi is already declared and applied |
| New permission / DataStore key / Settings control / telemetry | one DataStore key (`match_wallpaper`, boolean, default false) + one Settings → Appearance switch (API 31+ only) — 50.2, reason recorded in the part doc; no permission, no telemetry |
| Fonts | 0 new bytes — JetBrains Mono Medium + Bold became the one code face (50.3) but both `.ttf` files already shipped since 19.2; OFL-1.1 licence already in `assets/licenses/`, owner decision 2026-09-20 |
| APK budget | the repo measures every round (release APK is 6,681,306 B today, v1.3.17). Every part doc records its own delta the way 44–49 did. |

---

## Device rounds (the gate)

Each phase ends with owner-run rows on the owner's own handsets, and each
row's result is pasted back into the **owning part doc**, not a scratchpad.
The record format (`# | Part | Run on | What to do | PASS looks like`,
under a `## Test log (Phase NN — …)` heading) comes from the **cancelled**
cross-device matrix — it survived the cancellation; the matrix did not.

> **Concurrent work (recorded 2026-09-14):** while this plan was written,
> **⚠️ Merge-order note (live):** `arena/01a099d8-codec` — CI ✅ GREEN
> `34753000709` tip `69a70ec` — ships `app/src/test/java/com/codeci/ide/
> DeviceMatrixTest.kt`, a 627-line test that machine-pins a cross-device matrix
> (hard-coded `MATRIX_PATH = "docs/chat-phase50/DEVICE_MATRIX.md"`, a required
> link in `docs/chat-phase50/README.md`, and a `PART_FILES` map demanding a
> `## Test log (Phase 50 — the cross-device matrix)` section in eleven part
> docs). **Phase 53 is cancelled, so that test must be deleted or re-scoped
> before that branch lands on `main`** — otherwise `Build APK` goes red on paths
> this branch moved. Nothing in 50-52 depends on it.

| Phase | Round | Rows |
|---|---|---|
| 50 | `docs/chat-phase50/DEVICE_ROUND.md` | L1-L12 — one screen per row, dark + light, small + large phone: spacing looks even, corners match, the accent is visible, nothing regressed in contrast |
| 51 | `docs/chat-phase51/DEVICE_ROUND.md` | F1-F16 — cold start, first run, RUN ▶ visibility, keyboard up vs down, hub scroll, package install moment, haptics on/off |
| 52 | `docs/chat-phase52/DEVICE_ROUND.md` | R1-R12 — resume card, decline, cold-open timing (stopwatch), jank on a big file, the streak row |
| ~~53~~ | `docs/chat-phase53/` | ❌ **CANCELLED** (owner, 2026-09-14: *"Remove the full device cross check phase"*) — kept as history only; nothing in it is owed |

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
