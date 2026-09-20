# CodeC Phase 51 — The feel: the screens you touch every day

> **Status:** 📋 **PLANNED — no app code.** · **Cost:** `[client-only]` ·
> **Effort:** L · **Owner row (verbatim):** *"it's not attractive to user to use
> multiple time so i want to boost it's ui 100× time"* — the **surfaces** half of
> that sentence (the return half is Phase 52).
> Parent: [`PHASE50_52_ROADMAP.md`](../PHASE50_52_ROADMAP.md). Research dossier:
> [`PHASE50_52_UX_RESEARCH.md`](../PHASE50_52_UX_RESEARCH.md).
> **Depends on:** Phase 50 (tokens, brand, type, motion). Start 51 only after 50
> is merged, or the six surfaces get converted twice.

```text
  51.1  The first ten seconds: a splash that is CodeC, and a first screen with a face
  51.2  The editor surface: RUN ▶ as the hero, chrome with rhythm, a real empty state
  51.3  Hub, Packages, Terminal: cards with identity, skeletons, the install moment
  51.4  Micro-feedback: press states, haptics on the eight moments, confirmations
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [51.1](PART_51_1_FIRST_TEN_SECONDS.md) | Cold start and the first screen | M | 📋 PLANNED |
| [51.2](PART_51_2_EDITOR_SURFACE.md) | The editor, with RUN ▶ as the hero | L | 📋 PLANNED |
| [51.3](PART_51_3_HUB_PACKAGES_TERMINAL.md) | Hub / Packages / Terminal surfaces | M | 📋 PLANNED |
| [51.4](PART_51_4_MICRO_FEEDBACK.md) | Haptics, press states, confirmations | S/M | 📋 PLANNED |

Device round: [`DEVICE_ROUND.md`](DEVICE_ROUND.md) (F1-F16, written, not run).

---

## The evidence: what the first session looks like today

Reads and greps on **`main` @ `62cfe7b`, 2026-09-13**.

| # | What the user meets | Evidence | Why it costs a return visit |
|---|---|---|---|
| 1 | A **black (or white) rectangle** for the first moment of every cold start | `app/src/main/res/values/themes.xml` is one line: `<style name="Theme.MyApplication" parent="android:Theme.DeviceDefault.NoActionBar" />`; `grep -rn "SplashScreen\|windowSplashScreen\|postSplashScreen"` → **0** | the first thing a new user sees is not CodeC |
| 2 | A first screen that is **functional and anonymous** | `WelcomeScreen.kt:52-100` — "CodeC" headline, one sentence, then three flat `Card`s (`RoundedCornerShape(16.dp)`, `defaultElevation = 0.dp`), no imagery, no colour beyond the accent | nothing to remember, nothing to feel |
| 3 | **The one action that matters looks like every other button** | `EditorScreen.kt` — RUN ▶ is a text/icon button among the chrome; the editor is 2,423 lines of chrome + a code view | Google's eye-tracking: a bigger, better-contained primary action is found **up to 4× faster** (dossier §3.2) |
| 4 | **Empty and loading states are one line of text** | `strings.xml:206-207, 331, 395, 442`; `FileManagerScreen.kt:1223` (`EmptyProjectsState`); the drawer's two empties (`EditorProjectDrawer.kt:255, 393`) | the moments a user is most unsure are the ones with the least design |
| 5 | **Nothing acknowledges a tap** | haptics exist only in the CodeC keyboard (`CodecKeyboard.kt:255,267`), the terminal bell (`TerminalEmulatorView.kt:126-136`) and the `codec-vibrate` API | a user's finger is never answered by the app's own chrome |
| 6 | Installs (the most anxious moment in the app) end with **terminal text** | Phase 44 owns the setup bar; Packages' rows are `ModulesScreen.kt` (783 lines) | the highest-emotion moment has the least reward |

Why this ordering inside the phase: **51.1 owns the seconds before the first
action, 51.2 owns the action itself, 51.3 makes the rest of the app feel like
the same product, 51.4 answers the finger.** That is the order the user meets
them.

---

## Design, in one page

### 51.1 — The first ten seconds

- **A splash that is CodeC.** One new dependency:
  `androidx.core:core-splashscreen` (Apache-2.0, the platform's own
  backported splash API) — a themed window background using the **existing**
  `ic_launcher_foreground` + the brand surface from 50.2, so the first frame is
  the app's mark on the app's colour instead of the system's black. The splash
  is dismissed by `SplashScreen.setKeepOnScreenCondition` keyed on a **pure**
  `LaunchReadiness` policy (never a hard delay).
- **A first screen with a face.** `WelcomeScreen` keeps its Phase 33.1
  structure (three language tiles — that is Pydroid's pattern and it works) and
  gains: the mark at a real size, the three tiles with **language identity**
  (the existing `StarterIconView` colours: C orange / Python blue / web green)
  on cards that use 51's radius/elevation, one line of "what happens next"
  under each, and a visible "C works offline — no download" reassurance, which
  is the single fact that stops a nervous first-run exit (Phase 44's whole
  problem).

### 51.2 — The editor, with RUN ▶ as the hero

- **RUN ▶ becomes the primary contained action** on the editor: larger, filled
  with the brand container role, above the chrome's visual weight — the one
  change with a measured 4× effect in Google's study.
- **Chrome rhythm:** tab bar, find bar, suggestion strip, status bar and the RUN
  row each get a declared slot in the column (50.1 tokens), so the code view's
  height stops being an accident. **Nothing** in the sora host changes.
- **A real empty state** for "no file open" (today: the tab bar collapses and
  the user is looking at an empty frame): one sentence, one action, the mark.
- **Save feedback** — Phase 46.2's single-file mode writes silently; give it a
  one-word confirmation (51.4's mechanism).

### 51.3 — Hub, Packages, Terminal

- **Project cards get identity** — the existing `ProjectIconView` (initial +
  colour) plus the file-type icon set from Phase 34, arranged with the token
  scale, so the hub reads as a shelf of *your* projects rather than a list.
- **Loading states**: the hub's project load and the file tree get a skeleton
  (`CodecMotion` shimmer), never a blank.
- **The install moment**: a package row's RUNNING → INSTALLED transition gets a
  visible, haptic-marked completion (not a terminal line); failure keeps Phase
  44's one-sentence honesty.

### 51.4 — Micro-feedback

Eight named moments get a haptic and a press state — and nothing else does,
because haptics everywhere is noise:

1. RUN ▶ started · 2. program finished (success) · 3. program failed ·
4. file saved · 5. install finished · 6. tab closed · 7. project opened ·
8. long-press / drag start.

All of it behind a **pure** `HapticPolicy.momentFor(action, settingsEnabled,
deviceSupportsVibrator)`, and behind a Settings switch (haptics on/off) that
defaults to **on** for these eight and never touches the CodeC keyboard's own
setting (`KeysStayPolicy` stays untouched, Phase 47.2).

---

## What this phase must NOT do

- **No new flow, no new screen, no changed navigation.** Phases 46/47/49 own
  that; this phase repaints.
- **No change to the sora editor host, the caret policy, the run pipeline, or
  the chrome lock.** 51.2 is chrome-only, and the editor keeps every Phase
  33/35/44/45/46/47/48 behaviour.
- **No modal nag, ever** (Phases 41/42/45). Confirmations are snackbars and
  states, never dialogs.
- **No second splash delay, no artificial minimum display time** — the splash
  leaves as soon as the app is ready (`LaunchReadiness`).
- **No change to the guide/tour** (Phase 45) — its geometry is device-tested.

## Exit condition

Cold start shows CodeC's own splash (not a black window); the welcome screen and
the editor chrome use 51's tokens throughout; RUN ▶ is measurably the largest,
most contained control on the editor (source pin + device row F6); every list
has a designed empty **and** loading state; the eight moments give a haptic and
the switch turns them off; the **one** new dependency is justified and recorded
with its APK delta; and `DEVICE_ROUND.md` F1-F16 has been run by the owner.

## Tests (plan)

| File | Cases | Pins |
|---|---|---|
| `LaunchReadinessTest` | ~9 | splash leaves on readiness, never on a timer; safe mode / crash overlay still win |
| `WelcomeLayoutTest` (source scan) | ~6 | tiles use tokens; each tile names its language; the offline-C line is present |
| `EditorChromeLayoutTest` (source scan) | ~10 | RUN ▶ is the largest contained control; chrome slots exist; **no** change inside `SoraEditorHost` |
| `EmptyStateTest` (source scan) | ~8 | every list surface has an empty + loading branch |
| `HapticPolicyTest` | ~12 | exactly the eight moments; off switch wins; keyboard setting untouched |
| `HapticWiringTest` (source scan) | ~8 | all haptic calls go through the policy; none in the sora host |

≈53 cases. The haptic *feel* is a device row, not a test; the policy is a test,
not a feeling.

## Sources (record)

Dossier §2.4 (splash evidence), §2.5, §3.2 (the 4× finding and the containment
mechanism), §3.3 (users naming "little details and haptics"), §3.6 (D1 is
decided in the first session; 3 minutes to first value), §4 (splashscreen
library licence, Lottie rejected).

## Deferred / rejected with reasons

- **Lottie / animated empty-state illustrations** — bytes for decoration; the
  research's win is containment + motion, which 50.4 and 51.2 already deliver.
- **Reordering the bottom bar / a new home tab** — a navigation change; Phase 49
  and 46 own navigation.
- **Redesigning the terminal emulator's rendering** — Phase 36 tuned it for
  speed; only its *chrome* is in scope.
- **A "recent projects" carousel on the welcome screen** — that is 52.1's
  continuity job, and putting it here would duplicate the resume decision.
