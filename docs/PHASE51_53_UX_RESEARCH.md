# CodeC — Phases 51–53 · "make it 100× more attractive" research dossier

> **Owner (2026-09-13, verbatim):** *"research online, open source, real humans
> thought on the topic of policed ui / No the main problem is it's not
> attractive to user to use multiple time so i want to boost it's ui 100× time
> understand"*
>
> Owner's clarifications in chat (2026-09-13): **"policed ui" = polished UI** ·
> the goal is **both** *"the first 10 seconds — it must look gorgeous on open"*
> **and** *"coming back — the daily-return habit"* · **three** phases · this
> session delivers **research + the phase docs** (no app code yet).
>
> **Status: 📋 RESEARCH / PLAN ONLY — NO APP CODE.**
>
> **Evidence law followed here (the house rule, `PHASE44_50_UX_RESEARCH.md`):**
> every claim about *this app* is a `file:line` read **or a grep count taken on
> 2026-09-13 against `main` @ `62cfe7b`**. Every external claim names its source
> and was fetched this session. Nothing below is code yet.

---

## 1. What the owner is actually asking for

Three different complaints are hiding in one sentence, and they need three
different answers:

| # | Owner's words | The real question | Where the answer has to come from |
|---|---|---|---|
| 1 | *"not attractive"* | the app does not look designed | visual identity: colour, type, spacing, shape, iconography |
| 2 | *"to use multiple time"* | nobody comes back | retention: time-to-first-value, resume, feedback, progress |
| 3 | *"100×"* | not literally 100 — "stop feeling like a hobby project" | consistency + motion + perceived speed, i.e. the things people read as *quality* |

**The measurement problem, stated up front.** "Attractive" cannot be proven by
a JVM test, and the repo's executor of record is CI (`rule.md` §5). So this
series is built so that **the parts that are provable are pure and testable**
(tokens, contrast, motion specs, resume decisions, screenshot goldens), and the
rest is an **owner-run device round** — the same bargain Phases 44–50 made.

---

## 2. What the app looks like today — measured, not felt

All numbers are greps on `main` @ `62cfe7b` (2026-09-13).

### 2.1 There is no design system, only Android Studio's template

| Evidence | Read | What it means |
|---|---|---|
| `app/src/main/java/com/codeci/ide/ui/theme/Color.kt:5-11` | `Purple80 / PurpleGrey80 / Pink80 / Purple40 / PurpleGrey40 / Pink40` = the literal AS-template palette | the app's fallback scheme **is the template's violet**, the exact colour the owner complained about in Phase 40.5 |
| `ui/theme/Theme.kt:15-19` | `DarkColorScheme` / `LightColorScheme` built from those six constants | those constants are still the base; 40.5 only overrides the **accent roles** on top |
| `ui/theme/Theme.kt:43-70` | `dynamicColor: Boolean = true` default, and `useDynamic` wins whenever no accent is stored | on Android 12+ (**the owner's own phones**) the app is tinted by the **wallpaper**: CodeC has **no visible brand colour at all** by default |
| `ui/theme/Type.kt:9-30` | `Typography(bodyLarge = …)` — every other style is still the commented-out template block | there is **no type scale**; "headline / title / body / label" are whatever Material's defaults happen to be |
| `grep -rho "[0-9]\+\.dp" app/src/main/java \| wc -l` | **801** raw `dp` literals | there is no spacing scale |
| `grep -rho "RoundedCornerShape([0-9]*\.dp)" … \| sort \| uniq -c` | **10 distinct radii**: 2, 4, 5, 6, 8, 9, 10, 12, 14, 16 dp (17×8, 12×12, 12×10, 10×6, 8×5 …) | corners are chosen per-composable, so no two screens look related |

### 2.2 The app is completely static

| Evidence | Read |
|---|---|
| `grep -rn "AnimatedVisibility" app/src/main/java` | **one** hit: `EditorScreen.kt:1627` (the find bar) |
| `grep -rnE "\b(spring|tween|keyframes|snap)\(" app/src/main/java` | **zero** |
| `grep -rn "AnimationSpec\|animationSpec\|fadeIn\|slideIn\|scaleIn\|graphicsLayer" app/src/main/java` | **zero** |

The only motion in CodeC is whatever Material3's own components animate
internally. That is the single biggest difference between CodeC and the apps
people call "polished" — see §3.4, where a Redditor's praise for an app is
*"not just looks beautiful but **the fluidity is unreal**"*.

### 2.3 Feedback and haptics exist — but only for the keyboard

| Evidence | Read |
|---|---|
| `grep -rn "HapticFeedback\|vibrate" app/src/main/java` | 28 hits, and **every** one is either the CodeC keyboard (`ui/keyboard/CodecKeyboard.kt:255,267`), the terminal bell (`ui/components/TerminalEmulatorView.kt:126-136`) or the `codec-vibrate` CodeCApi script (`ui/terminal/CodecApiBridge.kt:875-884`, `ShellEnvironment.kt:1321-1372`) |
| … | **the app's own chrome never gives a haptic.** No tap feedback on RUN ▶, save, tab close, install success, nothing |

### 2.4 The first ten seconds are a plain white (or black) window

| Evidence | Read |
|---|---|
| `app/src/main/res/values/themes.xml` | `<style name="Theme.MyApplication" parent="android:Theme.DeviceDefault.NoActionBar" />` and nothing else |
| `AndroidManifest.xml:55,68` | that theme is the application **and** the launcher activity theme |
| `grep -rn "SplashScreen\|windowSplashScreen\|postSplashScreen"` | **zero** |

Cold start shows the system's default window background — on a dark-mode phone
a black rectangle — until Compose draws. On a cold start that also compiles
nothing but does run `TerminalViewModel` (`MainActivity.kt:629`), this is the
*first* thing a new user sees, and it is not CodeC.

After that: `WelcomeScreen.kt:52-100` — a centred "CodeC" headline, three flat
`Card`s with `RoundedCornerShape(16.dp)` and `defaultElevation = 0.dp`, one
line of copy each. It is **functional and completely anonymous**; nothing about
it says what CodeC looks like.

### 2.5 The "come back" machinery already exists and is invisible

| Evidence | Read | What it means |
|---|---|---|
| `ui/stats/StatsManager.kt:17-60` | `TOTAL_RUNS`, `TOTAL_FILES_CREATED`, `LAST_RUN_DATE`, **and `CURRENT_STREAK`** with a correct today/yesterday rule | **the app already counts a daily streak** |
| `grep -rn "totalRunsFlow\|currentStreakFlow\|totalFilesCreatedFlow" app/src/main/java` | **zero readers outside `StatsManager` itself** — only the six `increment*` call sites (`MainActivity.kt:1383`, `EditorViewModel.kt:3149,3329`, `FileManagerViewModel.kt:276,347`) | the user is counted and told **nothing** |
| `ui/projects/EditorLaunchState.kt:11-45` | saves the last project+file, validates it against disk on load | resume exists |
| `MainActivity.kt:807` | `EditorLaunchState.load(activity)` decides the start destination | …but silently: the user is dropped into a file with **no "welcome back", no breadcrumb, and no way to say "not now"** |

### 2.6 What can already measure the work

- **`bench/`** — a separate APK (`com.codeci.bench`) with
  `bench/src/main/java/com/codeci/bench/core/FrameStats.kt` and
  `harness/FrameCapture.kt`. Frame timing is instrumented **already**; nothing
  in the plan needs a new tool.
- **Roborazzi 1.59.0** — in the version catalog
  (`gradle/libs.versions.toml:30`), wired `testImplementation`
  (`app/build.gradle.kts:272-274`), plugin applied to `:app`
  (`app/build.gradle.kts:8`) — and `grep -rln roborazzi app/src/test` returns
  **nothing** (verified 2026-09-13; Phase 50's README already recorded this).
  Screenshot goldens are available **without a new dependency**.
- **The contrast gate** — `AppContrastTest` + `ChromeContrastTest` in
  `app/src/test` already measure ratios and read the alphas out of the UI
  sources, so any colour work in this series is CI-guarded for free (Phase 40.5
  colour law).

### 2.7 The dependency facts that constrain everything

| Fact | Read |
|---|---|
| `composeBom = "2024.12.01"` | `gradle/libs.versions.toml:13` → **material3 1.3.1** |
| `minSdk = 24` | `app/build.gradle.kts:18` |
| `targetSdk = 28` | `app/build.gradle.kts:26` (deliberate, W^X; not negotiable) |
| release APK today | 6,681,306 B (v1.3.17, CI `34742868395`) — the repo measures **every** byte delta per round |

---

## 3. What real humans and the literature say

### 3.1 Attractiveness is not decoration — it changes judgement in 50 ms

- Lindgaard et al. (2006): visual appeal is assessed within **50 ms** and the
  judgement is stable over longer exposures
  ([researchgate.net/publication/220208334](https://www.researchgate.net/publication/220208334_Attention_web_designers_You_have_50_milliseconds_to_make_a_good_first_impression_Behaviour_and_Information_Technology_252_115-126),
  fetched 2026-09-13); the follow-up work reports correlations of first
  impressions with **visual appeal .864, credibility .805, perceived usability
  .644** ([pmc.ncbi.nlm.nih.gov/articles/PMC4863498](https://pmc.ncbi.nlm.nih.gov/articles/PMC4863498),
  fetched 2026-09-13).
- Nielsen Norman Group, *The Aesthetic-Usability Effect* (updated 2026-09-01):
  *"Users are more tolerant of minor usability issues when they find an
  interface visually appealing"* — and the honest limit: *"A pretty design can
  make users forgiving of minor usability problems, but not of large ones"*
  ([nngroup.com/articles/aesthetic-usability-effect](https://www.nngroup.com/articles/aesthetic-usability-effect/),
  fetched 2026-09-13).

**What that means for CodeC:** this work is not vanity, and it is **not a
substitute** for the 44–50 correctness work. It buys tolerance for small
friction; it does not fix a broken flow.

### 3.2 Google's own 2025 research: expressive, *measured*, beats plain

Google's Material team ran **46 studies with over 18,000 participants** for
Material 3 Expressive ([design.google/library/expressive-material-design-google-research](https://design.google/library/expressive-material-design-google-research),
fetched 2026-09-13):

- With eye-tracking, participants **spotted key elements up to 4× faster**
  than in plain Material 3, and *"M3 Expressive designs erased the usability
  age gap"* — older users found key actions as fast as younger ones.
- Attribute lifts: **+34% modernity, +32% subculture, +30% rebelliousness**;
  younger participants rated the designs highest for *"visual appeal"* and
  *"intention to use"*.
- The **mechanism** is what CodeC should copy, and it is not the colours:
  **bigger, shaped, better-contained primary actions** and **springs**, i.e.
  the size/placement of the one button that matters, plus motion that carries
  state changes.
- **Google's own counter-example, quoted because it is the guard-rail:** a
  playlist screen that broke the familiar list paradigm *"looked modern and
  exciting"* but *"usability scores suffered"*, and removing text labels from
  email actions reduced usability. *"When basic interaction paradigms are
  broken, expressive design can lead to poor usability or negative
  sentiment."*

**What that means for CodeC:** make **RUN ▶** the biggest, most contained
thing on the editor; give the screens spring motion; and **do not** break the
familiar patterns (tab bar stays a tab bar, list stays a list).

### 3.3 What ordinary people call "polished" — their words

- r/Android, *"What is your favourite Android UI in 2025?"*
  ([reddit.com/r/Android/comments/1lhu5mf](https://www.reddit.com/r/Android/comments/1lhu5mf/what_is_your_favourite_android_ui_in_2025/)):
  *"the new expressive Pixel UI, looks **super polished and modern**, has a
  ton of **little details and haptics** that I love too."* — the two things a
  real user names are *details* and *haptics*. CodeC has neither (§2.3).
- r/androidapps, *"Really good looking apps?"*
  ([reddit.com/r/androidapps/comments/1dtiw61](https://www.reddit.com/r/androidapps/comments/1dtiw61/really_good_looking_apps/)):
  *"Bundled Notes … not just looks beautiful but the **fluidity is unreal**"*;
  *"Smart launcher — incredibly **highly polished** UI and design"*; and the
  tell about *being current*: *"FoxyNotes implements Google **latest
  specification Material 3**, so the design is not outdated."*
- r/androidapps, *"What are the most beautiful apps on your phone?"*
  ([reddit.com/r/androidapps/comments/12pokkj](https://www.reddit.com/r/androidapps/comments/12pokkj/what_are_the_most_beautiful_apps_on_your_phone/)):
  the recurring pattern is **consistency across every screen** ("Material
  Files", "Niagara — beauty **and usability**"), not one pretty screen.

### 3.4 What real humans say about coding on a phone (the honest ceiling)

- HN, *"Ask HN: Why don't people program on their phone?"* (2018)
  ([news.ycombinator.com/item?id=18363180](https://news.ycombinator.com/item?id=18363180)):
  the top complaints are the keyboard, screen size, **"lack of cursor
  navigation"** (#1 stumbling block), and *"no good way to select text except
  fat finger selection"*.
- HN, *"Ask HN: Who productively writes code on their smartphone?"* (2016)
  ([news.ycombinator.com/item?id=11697029](https://news.ycombinator.com/item?id=11697029)):
  *"The trouble is the **lack of feedback**"* — an iPad Python user on why
  mobile coding stalls.
- HN, *"Why don't smartphones encourage programming…"* (2023)
  ([news.ycombinator.com/item?id=35579425](https://news.ycombinator.com/item?id=35579425)):
  the sceptics are structural (*"a phone is a consumer's device"*), but the
  optimists name the same lever this series uses: *"improved interfaces"*.
- What actually gets recommended in 2025
  ([reddit.com/r/androidapps/comments/1on1964](https://www.reddit.com/r/androidapps/comments/1on1964/good_app_for_coding/)):
  *"Acode with the github & terminal extension and termux"* — i.e. users
  assemble a **toolkit**, and they stay where the *editing* feels native.

### 3.5 Where the competing apps win and lose on the surface

| App | Rating / evidence | The lesson for CodeC |
|---|---|---|
| **Acode** | 3.3★ / 13,970 ratings, 1M+ installs (Play, checked Aug 2026 via [bestappsforandroid.com](https://bestappsforandroid.com/best-code-editor-apps-for-android/)); its own reviews volunteer *"a really decent amount of theme settings"* and *"User-Friendly Interface"* | **users loudly reward theme/customisation** — and the same app's low rating is driven by *"can become buggy after updates"*, i.e. **surface polish does not outrun broken behaviour** |
| **GitHub Mobile** | 4.8★ — highest-rated in the category | the winner is the app that does **one** job calmly, not the one with the most features |
| **Pydroid 3** | 4.4★ | "pick a language" first-run is the pattern WelcomeScreen already mirrors (Phase 33.1) — keep it, make it beautiful |
| **Termux** | n/a, no ads, no IAP | the power user's default; zero surface — the reason CodeC can win on look alone |

### 3.6 Retention: what "use multiple time" actually costs

- Median benchmarks: **D1 ≈ 25–27%, D7 ≈ 9–13%, D30 ≈ 4–6%**
  ([mwm.ai/glossary/retention](https://mwm.ai/glossary/retention),
  [plotline.so/blog/retention-rates-mobile-apps-by-industry](https://www.plotline.so/blog/retention-rates-mobile-apps-by-industry),
  [digia.tech/post/mobile-app-retention-rate-what-it-is-and-whats-pulling-it-down](https://www.digia.tech/post/mobile-app-retention-rate-what-it-is-and-whats-pulling-it-down/);
  all fetched 2026-09-13).
- The single biggest lever named across those sources: **users who complete
  onboarding retain 2–3× higher**, and apps that get a user to a meaningful
  first action **within 3 minutes** see materially higher D7
  ([digia.tech/post/mobile-app-onboarding-activation-retention](https://www.digia.tech/post/mobile-app-onboarding-activation-retention/)).
- The diagnostic shapes matter: a **D1 cliff** = onboarding/first-impression
  failure (→ Phase 52.1 + 53.1); a **steep D1→D7 fall** = the second and third
  sessions gave no reason to return (→ Phase 53.2/53.3).

**What that means for CodeC:** the "100×" that is measurable is
**time-to-first-value** — first run to *"my code ran on my phone"* — and the
reason to return is **continuity** (where you left off, what you built), not
notifications (which this repo must never add — no-telemetry law,
`rule.md` §6 / Phase 41).

---

## 4. Open-source options, checked in the house order

`PHASE38_43_OSS_RESEARCH.md` §0's order: **library → data set we may vendor →
app whose behaviour we copy → only then custom Kotlin.**

| Idea | Best OSS answer | Licence | Verdict |
|---|---|---|---|
| Expressive components (shape-morph buttons, `ButtonGroup`, `LoadingIndicator`, `MotionScheme`) | `androidx.compose.material3:material3:1.5.0-alphaNN` | Apache-2.0, **but alpha** | ❌ **not adopted** — see D1 |
| Same, via Compose Multiplatform | `org.jetbrains.compose.material3:material3:1.9.0-alpha04` | Apache-2.0, alpha, and CMP is not in this build | ❌ not adopted |
| Cold-start screen | `androidx.core:core-splashscreen` | Apache-2.0 | ✅ **adopt** in 52.1 (tiny, `minSdk`-safe, kills §2.4) — the only new dependency in the whole series |
| Motion | `androidx.compose.animation` (`spring`/`tween`/`AnimatedContent`/`AnimatedVisibility`) | Apache-2.0, **already on the classpath and unused** | ✅ adopt — 0 bytes (§2.2) |
| Screenshot goldens | **Roborazzi 1.59.0** | Apache-2.0, **already declared + plugin applied, zero tests** | ✅ adopt in 53.4 — 0 new bytes of dependency graph |
| Frame timing | in-repo `bench/` (`FrameStats.kt`, `FrameCapture.kt`) | own code | ✅ adopt in 53.2 |
| Haptics | `LocalHapticFeedback` (Compose) + `VibrationEffect` (platform) | platform | ✅ adopt in 52.4 |
| Animated illustrations (Lottie/Rive) | `com.airbnb.android:lottie-compose` | Apache-2.0 | ❌ **rejected** — adds a runtime dependency plus binary assets for decoration; the repo measures every KB, and M3's own research says the win comes from **containment + motion**, not illustrations |
| A monospace font for code surfaces | **JetBrains Mono** | **SIL OFL-1.1** — not on `rule.md` §6's whitelist (MIT/Apache/BSD/CC0) | ⚠ **open question, escalated to the owner** — default ship = system `FontFamily.Monospace` (0 bytes); vendoring needs an explicit owner yes, recorded here and in the part doc |
| Icons | `androidx.compose.material:material-icons-extended` | Apache-2.0 | ⚠ **already in the graph** but extended icons bloat the APK; 51.3 uses the **core** set + the existing `SpckIcons`/`FileIcon` work (Phase 34/38) |
| Behaviour to copy (clean-room, visible behaviour only) | Acode (theme picker) · Pydroid (language-first first run) · Spck (preview) · GitHub Mobile (calm) · Niagara / Bundled Notes (fluidity) | — | ✅ **reference only** — no code, no assets, no trademarks |

---

## 5. The three decisions the research forces

**D1 — Do not take Material 3 Expressive as a dependency. Adopt its *findings*.**
As of 2026-09-13 the expressive components live in the **1.5.0-alpha** track
(`developer.android.com/jetpack/androidx/releases/compose-material3`,
fetched 2026-09-13: *"All public APIs tagged with
`ExperimentalMaterial3ExpressiveApi` … have been removed, please switch to
1.5.0-alpha"*, and 1.5.0-alpha23 is still graduating APIs *within* alpha).
CodeC is on BOM `2024.12.01` (material3 1.3.1) and ships a **release** APK
every round. Taking an alpha into a shipping IDE, on a `targetSdk 28`
compatibility build, to gain what three `spring()` calls and a bigger FAB give
you, is a bad trade. **Recorded decision:** replicate the measured outcomes —
a contained, larger, shaped primary action; spring motion on state change;
consistent containment and shape — using stable APIs, and re-evaluate when
expressive reaches stable.

**D2 — Tokens before screens.**
The evidence in §2.1 (801 dp literals, 10 radii, no type scale) means a
screen-by-screen repaint would be repainting the same inconsistency three
times. Phase 51 builds the scale and the motion vocabulary; Phase 52 spends it
on the surfaces; Phase 53 spends it on the return. This is also the only order
in which the **device round is meaningful** (the owner sees one change, not
nine).

**D3 — The "return" work uses what the app already counts. No telemetry, ever.**
`StatsManager` already has a streak (§2.5) and `EditorLaunchState` already has
resume. Phase 53 surfaces them. No analytics SDK, no network, no new DataStore
keys beyond the ones that already exist (`rule.md`: *"No new dependency,
DataStore key, Settings control, permission or telemetry"* unless a part doc
justifies it).

---

## 6. Sources (record)

| # | Source | Used for | Fetched |
|---|---|---|---|
| 1 | [design.google — Expressive Design: Google's UX Research](https://design.google/library/expressive-material-design-google-research) | 46 studies / 18k participants, 4× faster element spotting, age gap erased, +34/+32/+30%, and Google's own "context still matters" caveat | 2026-09-13 |
| 2 | [Dezeen — Google ushers in age of "expressive" interfaces](https://www.dezeen.com/2025/05/28/google-ushers-in-age-of-expressive-interfaces-with-material-design-update/) | the same study reported by press; "wild and way-too-playful" preferred | 2026-09-13 |
| 3 | [NN/g — The Aesthetic-Usability Effect](https://www.nngroup.com/articles/aesthetic-usability-effect/) | attractiveness → tolerance, and its limit | 2026-09-13 |
| 4 | [Lindgaard et al. 2006 — 50 ms](https://www.researchgate.net/publication/220208334_Attention_web_designers_You_have_50_milliseconds_to_make_a_good_first_impression_Behaviour_and_Information_Technology_252_115-126) | the 50 ms window | 2026-09-13 |
| 5 | [PMC — Credibility judgments in web page design](https://pmc.ncbi.nlm.nih.gov/articles/PMC4863498) | the 50 ms result replicated; appeal/usability/credibility correlations | 2026-09-13 |
| 6 | [developer.android.com — Compose Material 3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3) | expressive APIs are 1.5.0-**alpha**; removed from 1.4.0-beta01 | 2026-09-13 |
| 7 | [r/Android — favourite Android UI in 2025](https://www.reddit.com/r/Android/comments/1lhu5mf/what_is_your_favourite_android_ui_in_2025/) | "polished and modern … little details and haptics" | 2026-09-13 |
| 8 | [r/androidapps — Really good looking apps?](https://www.reddit.com/r/androidapps/comments/1dtiw61/really_good_looking_apps/) | "fluidity is unreal"; "implements … Material 3, so the design is not outdated" | 2026-09-13 |
| 9 | [r/androidapps — most beautiful apps on your phone](https://www.reddit.com/r/androidapps/comments/12pokkj/what_are_the_most_beautiful_apps_on_your_phone/) | consistency across screens is what users praise | 2026-09-13 |
| 10 | [HN — Why don't people program on their phone?](https://news.ycombinator.com/item?id=18363180) | keyboard, screen, cursor navigation, selection | 2026-09-13 |
| 11 | [HN — Who productively writes code on a smartphone?](https://news.ycombinator.com/item?id=11697029) | "the trouble is the lack of feedback" | 2026-09-13 |
| 12 | [HN — Why don't smartphones encourage programming…](https://news.ycombinator.com/item?id=35579425) | the ceiling, and "improved interfaces" as the lever | 2026-09-13 |
| 13 | [Best code editor apps for Android (2026 check)](https://bestappsforandroid.com/best-code-editor-apps-for-android/) | Acode 3.3★, GitHub Mobile 4.8★, Pydroid 4.4★ | 2026-09-13 |
| 14 | [Acode on Google Play](https://play.google.com/store/apps/details?id=com.foxdebug.acodefree) | users volunteering "theme settings" / "user-friendly interface" praise | 2026-09-13 |
| 15 | [MWM — retention benchmarks](https://mwm.ai/glossary/retention) · [Plotline](https://www.plotline.so/blog/retention-rates-mobile-apps-by-industry) · [Digia](https://www.digia.tech/post/mobile-app-retention-rate-what-it-is-and-whats-pulling-it-down/) · [Digia onboarding](https://www.digia.tech/post/mobile-app-onboarding-activation-retention/) | D1/D7/D30 medians; onboarding 2–3×; 3-minute TTFV | 2026-09-13 |
| 16 | [r/androidapps — Good app for coding?](https://www.reddit.com/r/androidapps/comments/1on1964/good_app_for_coding/) | what real users actually install (Acode + Termux) | 2026-09-13 |

---

## 7. Deferred / rejected with reasons

- **A full redesign of any screen's information architecture.** The 44–50 work
  is behaviour the owner device-tested; this series changes *surfaces*, not
  flows. Any IA change is a new owner row.
- **Material 3 Expressive as a dependency** — D1.
- **Lottie / illustrations / empty-state animation assets** — §4.
- **Vendoring a font (any licence not on `rule.md` §6's whitelist)** — §4;
  needs the owner's explicit yes.
- **Notifications, streaks-by-nagging, re-engagement pushes** — forbidden by
  the repo's no-telemetry/no-nag law; 53.3 is explicitly designed *against*
  them.
- **Anything that touches `targetSdk`, the bootstrap, or the userland.** Out of
  scope by invariant.
- **Phase 50's cross-device matrix** stays its own phase; 53.4 **reuses** its
  record format and does not replace it. (**Status corrected 2026-09-14:** while
  this dossier was being written, Phase 50's matrix became 🚧 IMPLEMENTED on
  `arena/01a099d8-codec` — `DeviceMatrixTest.kt` (627 lines), CI ✅ GREEN
  `34753000709` tip `69a70ec` — pinning rounds A-J for **44.1-49.2**. The 51-53
  rounds adopt its `## Test log` convention; extending its fixed `PART_FILES`
  map to 51-53 is out of scope here.)
