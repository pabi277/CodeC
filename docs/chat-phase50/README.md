# CodeC Phase 50 — The look: one CodeC design language

> **Status:** ✅ IMPLEMENTED (2026-09-20, `arena/01a0bd6d-codec`, owner: "Start phase 50"; CI ✅ GREEN `35495174151` — rounds 1–3 red for-cause (cubicBezier() not on this Compose; two test-compile type errors; audit row 64 owed by the new switch); device round L1–L12 pending at merge, owner command) · **Cost:** `[client-only]` ·
> **Effort:** L · **Owner row (verbatim):** *"it's not attractive"* /
> *"i want to boost it's ui 100× time"*, clarified 2026-09-13 as **polished
> UI**, goal = **both** *"the first 10 seconds — it must look gorgeous on
> open"* **and** *"coming back"*.
>
> Owner commands one phase at a time: **"Start Phase 50"**. Series roadmap:
> [`PHASE50_52_ROADMAP.md`](../PHASE50_52_ROADMAP.md). Research dossier:
> [`PHASE50_52_UX_RESEARCH.md`](../PHASE50_52_UX_RESEARCH.md).

```text
  50.1  Tokens: one spacing / radius / elevation / icon scale, pinned by a test
  50.2  Identity & colour: a brand you can see on Android 12+ (Phase 40.5 law)
  50.3  Type & iconography: a real type scale, one icon size per role
  50.4  Motion: CodecMotion (springs + durations), and the first six transitions
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [50.1](PART_50_1_TOKENS.md) | One scale, not 801 numbers | M | ✅ DONE |
| [50.2](PART_50_2_IDENTITY_COLOR.md) | A brand you can see on Android 12+ | M | ✅ DONE |
| [50.3](PART_50_3_TYPE_AND_ICON.md) | A type scale and one icon set | S/M | ✅ DONE |
| [50.4](PART_50_4_MOTION.md) | Motion, for the first time | M | ✅ DONE |

Device round: [`DEVICE_ROUND.md`](DEVICE_ROUND.md) (L1-L12, written, not run).

---

## The evidence: what "no design language" looks like in this code

Every row is a read or a grep on **`main` @ `62cfe7b`, 2026-09-13**.

| # | Symptom a user sees | Evidence | Root cause |
|---|---|---|---|
| 1 | Corners and gaps look different on every screen | `grep -rho "RoundedCornerShape([0-9]*\.dp)" app/src/main/java \| sort \| uniq -c` → **10 distinct radii** (2, 4, 5, 6, 8, 9, 10, 12, 14, 16 dp) | there is no radius scale; each composable picks one |
| 2 | …and the same for spacing | `grep -rho "[0-9]\+\.dp" app/src/main/java \| wc -l` → **801** literals, 25 distinct values | no spacing scale |
| 3 | Headings, labels and body text do not form a hierarchy | `ui/theme/Type.kt:9-30` — `Typography(bodyLarge = …)`, every other style still the commented-out template block | no type scale; Material's defaults carry the whole app |
| 4 | The app has **no colour of its own** on a modern phone | `ui/theme/Theme.kt:43-70`: `dynamicColor = true` by default and `useDynamic` wins whenever no accent is stored → on Android 12+ the scheme comes from the **wallpaper** | brand colour is opt-in, not default |
| 5 | The fallback (Android 7–11, or a stored accent) still smells like the Android Studio template | `ui/theme/Color.kt:5-11` — `Purple80/PurpleGrey80/Pink80/Purple40/PurpleGrey40/Pink40`, used by `Theme.kt:15-19` | the template palette was never replaced, only overlaid by Phase 40.5's accent roles |
| 6 | Nothing ever moves | `grep -rn "AnimatedVisibility" app/src/main/java` → **1** hit (`EditorScreen.kt:1627`); `grep -rnE "\b(spring\|tween\|keyframes\|snap)\(" …` → **0**; `grep -rn "AnimationSpec\|fadeIn\|slideIn\|scaleIn\|graphicsLayer"` → **0** | motion was never introduced |

Rows 1–3 are one problem (no scale), 4–5 are one problem (no identity), 6 is one
problem (no motion). That is why 51 has four parts and not fourteen.

---

## Design, in one page

### 50.1 — `CodecTokens` (pure, no Android imports)

```kotlin
object CodecTokens {
    object Space { const val NONE = 0f; const val XXS = 2f; const val XS = 4f
                   const val S = 8f; const val M = 12f; const val L = 16f
                   const val XL = 24f; const val XXL = 32f; const val HUGE = 48f }
    object Radius { const val XS = 4f; const val S = 8f; const val M = 12f
                    const val L = 16f; const val XL = 28f }
    object Elevation { const val FLAT = 0f; const val RAISED = 1f
                       const val CARD = 3f; const val SHEET = 6f }
    object Icon { const val INLINE = 16f; const val ACTION = 20f; const val NAV = 24f }
    const val MIN_TOUCH = 48f          // M3 / WCAG 2.2 §2.5.8 target size
    fun space(v: Float): Dp            // the only sanctioned way to write a gap
    fun radius(v: Float): Dp
}
```

The scale is **4-based** (wholly divisible down to 2), matching what the
codebase already uses most (8, 16, 6, 4, 10, 12 — the six most common values are
already on or near the scale), so adoption is a rename, not a redesign.

**Blast radius, deliberately bounded.** 50.1 converts the **six core surfaces**
— `WelcomeScreen`, `EditorScreen` chrome (tab bar, status bar, find bar),
`FileManagerScreen` (hub cards + empty state), `ModulesScreen` (package cards),
`TerminalScreen` chrome, `SettingsScreen` rows — and pins them. It does **not**
touch the sora host, the terminal emulator view, the git sheets or the coach
marks; those keep their numbers until a later phase, and nothing about them
regresses.

### 50.2 — `IdentityPolicy`: a brand that is *on* by default

Pure decision, tested without Android:

```kotlin
enum class BrandMode { BRAND, DYNAMIC }
data class IdentityInput(val storedAccentHex: String?, val dynamicAvailable: Boolean,
                         val darkTheme: Boolean)
object IdentityPolicy { fun decide(i: IdentityInput): BrandMode }
```

- **BRAND** (the recommended default): build every role from
  `CodecPalette.DEFAULT_ACCENT` (CodeC green) through the **existing**
  `AccentPalette.rolesFor` (Phase 40.5) — hue kept, lightness moved until the
  pair clears WCAG AA. Secondary/tertiary are derived from the **same seed**, so
  the template's violet/pink (`Color.kt`) stops leaking into chips and log lines.
- **DYNAMIC** stays available as a Settings choice (System → *"Match my
  wallpaper"*) and keeps the platform's `dynamicLight/DarkColorScheme` path.
- **Owner decision point (must be answered in chat before 50.2 lands):** brand
  by default, or wallpaper by default? The plan's recommendation is **BRAND**,
  because a code editor that borrows the user's wallpaper palette has no
  identity, and because the owner's own complaint in Phase 40.5 was that the app
  "looked violet" — i.e. he cares what colour it is.

### 50.3 — `CodecType` + one icon size per role

A real scale (display / headline / title / body / label, each with size, line
height, weight, letter spacing) replacing the one-line template. Code surfaces
(paths, terminal, diff, code in dialogs) get `FontFamily.Monospace` — **zero
bytes**, no vendored font. The **open question is recorded, not decided**:
JetBrains Mono (SIL OFL-1.1) is *not* on `rule.md` §6's licence whitelist, so
vendoring it needs an explicit owner yes (dossier §4).

Iconography: **one size per role** (inline 16 / action 20 / nav 24), every icon
carrying a `contentDescription` that is not the icon's name (accessibility — and
M3 Expressive's own research found removing *labels* hurt usability, so icon-only
actions keep their tooltips).

### 50.4 — `CodecMotion`, and the first six transitions

```kotlin
object CodecMotion {
    val spatialSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    val effectsSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy,
                                      stiffness = Spring.StiffnessMedium)
    object Duration { const val SHORT = 150; const val MEDIUM = 300; const val LONG = 500 }
}
object MotionPolicy {
    /** Android's own "remove animations" switch, and TalkBack's timeout. */
    fun specsFor(animatorDurationScale: Float, reduceMotion: Boolean): MotionSpecs
}
```

Six transitions, and **only** six, in this part: (1) bottom-tab switch
(`AnimatedContent`), (2) the output panel expand/collapse, (3) the find bar
(already an `AnimatedVisibility` — give it the spec instead of the default),
(4) opening/closing a file in the editor (crossfade of the chrome, **not** of
the code view), (5) the RUN ▶ → output reveal, (6) the hub's empty ↔ list
transition.

**The law that keeps 48 and 49 safe:** no animation on the sora host, no layout
animation while the IME is resizing, no animated transition on a back
navigation (Phase 49 decides back; animation must not delay it), and
`MotionPolicy` returns **instant** specs when the platform's
`ANIMATOR_DURATION_SCALE` is 0.

---

## What this phase must NOT do

- No new dependency (there is none to add — everything needed is on the
  classpath).
- No change to any flow: navigation, back, the chrome lock, the coach marks and
  the caret/IME work stay exactly as Phases 44–49 left them.
- No colour that fails `AppContrastTest` / `ChromeContrastTest` (Phase 40.5 law:
  the accent is a role, not a hex; values that already pass are not restyled).
- No animation that can delay a tap's *effect* by even one frame — motion
  follows state, it never gates it (45.2's single-click law).
- No vendored asset (font, icon, illustration) without an owner decision
  recorded in the part doc.

## Exit condition

`CodecTokens`, `IdentityPolicy`/brand ramp, `CodecType` and `CodecMotion` exist,
are used by the six core surfaces, and each is pinned by a named test; the app
contains **at least one spring**; both contrast test files stay green; the
release-APK delta is recorded like every phase before it; and **`DEVICE_ROUND.md`
L1-L12 has been run by the owner and reported**.

## Tests (plan)

| File | Cases | Pins |
|---|---|---|
| `CodecTokensTest` | ~10 | the scale is complete, monotonic, 4-based, and `space/radius` round-trip |
| `TokenAdoptionTest` (source scan) | ~8 | each of the six core files imports `CodecTokens`; no raw radius outside the scale in those files |
| `IdentityPolicyTest` | ~9 | brand by default; stored accent wins; dynamic only when asked for and available; dark/light both |
| `BrandRampTest` | ~12 | every role pair clears 4.5:1 (reuses `Contrast`), secondary/tertiary come from the seed, template violet is gone |
| `CodecTypeTest` | ~6 | the scale is ordered; every code surface uses a monospace family |
| `CodecMotionTest` | ~8 | spring params, duration ladder, `MotionPolicy` returns instant specs at scale 0 |
| `MotionWiringTest` (source scan) | ~7 | the six named transitions use `CodecMotion`; the sora host contains no animation call |

≈60 cases, all JVM-host (no Robolectric), following the house pattern of a pure
policy plus a source-scan wiring pin.

## Sources (record)

Dossier §6, rows 1, 2, 3, 4, 6, 7, 8, 9 — Google's M3 Expressive research (the
4×-faster finding and the "context still matters" caveat), the aesthetic-
usability effect, the 50 ms result, the Compose Material 3 release notes (why no
alpha dependency), and the Reddit threads where users define "polished" as
consistency + motion + haptics.

## Deferred / rejected with reasons

- **Material 3 Expressive as a library** — alpha-only as of 2026-09-13 (dossier
  D1); replicate the findings with stable APIs.
- **Converting all 61k lines to tokens** — deliberately out of scope (see blast
  radius); six surfaces now, the rest when a phase touches that file anyway.
- **A dark/light "theme store" like Acode's** — a different owner row; the four
  editor themes (`EditorThemes.kt:9-17`) already cover the code surface.
- **Lottie / animated illustrations** — Apache-2.0 but adds bytes for
  decoration; the research says the win is containment + motion.
- **Vendoring JetBrains Mono (OFL-1.1)** — needs the owner's explicit yes.
