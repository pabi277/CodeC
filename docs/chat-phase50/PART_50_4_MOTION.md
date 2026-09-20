# CodeC Phase 50.4 — Motion, for the first time

> **Status:** 🚧 IMPLEMENTED (2026-09-20, `arena/01a0bd6d-codec`, owner: "Start phase 50"; CI pending — Build APK is executor of record) · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"it's not attractive"* / *"boost it's ui 100×"*.
> Parent: [`README.md`](README.md) ·
> [`PHASE50_52_ROADMAP.md`](../PHASE50_52_ROADMAP.md).

## First move: evidence, not code

```text
$ grep -rn  "AnimatedVisibility"  app/src/main/java --include=*.kt
  EditorScreen.kt:1627      AnimatedVisibility(visible = findState.visible) { … }
$ grep -rnE "\b(spring|tween|keyframes|snap)\("  app/src/main/java --include=*.kt
  (no matches)
$ grep -rn  "AnimationSpec|animationSpec|fadeIn|slideIn|scaleIn|graphicsLayer"  app/src/main/java --include=*.kt
  (no matches)
```

CodeC has **exactly one** animated composable and **zero** animation specs. The
whole app moves only where material3 animates internally (ripples, the drawer,
sheets). That single number is the largest measurable gap between CodeC and
every app people describe as polished — one Redditor's praise, quoted in the
dossier, is not about colour at all: *"not just looks beautiful but the
**fluidity is unreal**"* (dossier §3.3).

Why it matters beyond taste (dossier §3.2): in Google's eye-tracking studies the
expressive variants got users to key actions **up to 4× faster**, and the
mechanism they name is **motion that carries state change** plus **bigger,
better-contained primary actions**. Motion is not decoration; it is how the eye
is told where the screen went.

And the guard-rail, also from Google: *"When basic interaction paradigms are
broken, expressive design can lead to poor usability."* So this part animates
**transitions between states that already exist**, and invents no new gesture.

## Design

### `CodecMotion` (pure values) + `MotionPolicy` (the accessibility switch)

```kotlin
object CodecMotion {
    // springs: M3 Expressive's own vocabulary, expressed with stable APIs
    val spatialSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    val effectsSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy,
                                      stiffness = Spring.StiffnessMedium)
    object Duration { const val SHORT = 150; const val MEDIUM = 300; const val LONG = 500 }
    object Easing { val EMPHASIZED = cubicBezier(0.2f, 0f, 0f, 1f) }
}

data class MotionInput(val animatorDurationScale: Float,   // Settings.Global
                       val reduceMotion: Boolean)          // Accessibility setting
data class MotionSpecs(val enterMs: Int, val exitMs: Int, val useSpring: Boolean)
object MotionPolicy {
    val INSTANT = MotionSpecs(0, 0, false)
    fun specsFor(i: MotionInput): MotionSpecs =
        if (i.reduceMotion || i.animatorDurationScale == 0f) INSTANT
        else MotionSpecs(CodecMotion.Duration.MEDIUM, CodecMotion.Duration.SHORT, true)
}
```

`spring`, `cubicBezier` and `Spring` are `androidx.compose.animation` — already
on the classpath, unused. **Zero new bytes of dependency.**

### The six transitions (and only six)

| # | Transition | Mechanism | Why this one |
|---|---|---|---|
| 1 | Bottom-tab switch | `AnimatedContent` + a short cross-fade | the most frequent navigation in the app; today it is an instant swap |
| 2 | Output panel expand / collapse | `AnimatedVisibility` + `spatialSpring` on height | it is the "did my program work?" moment |
| 3 | Find bar | give the **existing** `AnimatedVisibility` (`EditorScreen.kt:1627`) the shared spec | the one animation in the app should not use defaults nobody chose |
| 4 | Open / close a file in the editor | cross-fade of the **chrome** (tab bar, status bar, path) only | the code view is sora's; swapping it is 48's territory |
| 5 | RUN ▶ → output reveal | `AnimatedContent` on the run state chip + the panel | see 51.2: RUN is the hero |
| 6 | Hub empty ↔ list | `AnimatedContent` cross-fade | the first-run → "I have projects" moment |

### The laws that keep Phases 44–49 intact

1. **No animation inside `SoraEditorHost`, the terminal emulator view, or any
   surface the IME resizes.** Phase 48's caret work depends on a stable,
   un-animated layout pass; an animated height while the keyboard opens would
   re-break "the last line goes under the keyboard".
2. **Back is instant.** Phase 49's router decides; nothing in this part may
   delay or decorate a back navigation.
3. **Motion never gates an action.** A tap's effect happens on the press, as
   today (45.2's single-click law); the animation merely *shows* it.
4. **The coach marks are untouched** (Phase 45's tour has its own geometry and
   its own stall guard; animating a box would break anchored taps).
5. **`MotionPolicy.INSTANT` when the platform says so** — Android's "remove
   animations" (animator duration scale 0) must produce a genuinely instant app.

### What is deliberately NOT animated in this part

The drawer, the bottom sheets and the ripple: material3 already animates those
and replacing them is a rewrite with a regression surface. Ripples get their
colour from 50.2's roles automatically.

## The Android edge

- `Settings.Global.ANIMATOR_DURATION_SCALE` is read once (a `Context` call in a
  thin adapter); the **policy** takes it as a `Float`, so it is testable without
  Android.
- `AnimatedContent` needs a `contentKey`/`transitionSpec`; both come from
  `CodecMotion`.
- Compose's `LocalAccessibilityManager` gives the reduce-motion hint on newer
  versions; on `minSdk 24` the platform setting above is the source of truth.
- **Frame budget:** the six transitions are 150-300 ms on a small subtree. The
  bench APK's `FrameStats`/`FrameCapture` (52.2) is the check, not a guess.

## Exit condition

`CodecMotion` and `MotionPolicy` exist and are pinned; all six transitions use
them (source scan); `spring(` appears in the main source **≥1** time and
**only** through `CodecMotion` (source scan); with `animatorDurationScale = 0`
the app is instant (`MotionPolicyTest`); the sora host and the coach marks
contain **no** animation call (source scan — the Phase 48/45 guard); and the
device round's L11-L12 rows (tab switch, output panel) are run by the owner.

## Tests (plan)

- `CodecMotionTest` (~8): spring parameters are the documented ones; the
  duration ladder is ordered; `Easing` is a valid bezier (control points in
  range).
- `MotionPolicyTest` (~7): instant at scale 0; instant when reduce-motion is
  on; spring specs otherwise; a partial scale (0.5f) still animates; the
  INSTANT singleton is returned by identity.
- `MotionWiringTest` (~7, source scan): the six named transitions reference
  `CodecMotion`; every `spring(`/`tween(` in the app sits inside
  `CodecMotion.kt`; **`SoraEditorHost.kt` and `ui/guide/` contain no animation
  call**; `BackRouter`-driven navigation has no transition spec.

≈22 cases, host-JVM.

## Sources (record)

- `PHASE50_52_UX_RESEARCH.md` §2.2 (the greps), §3.2 (46 studies / 18,000
  participants, 4× faster, the age-gap finding, and the "context still matters"
  caveat), §3.3 (users naming fluidity), §4/D1 (why not the alpha library).
- `developer.android.com/jetpack/androidx/releases/compose-material3`
  (fetched 2026-09-13) — the expressive `MotionScheme` lives in the 1.5.0-alpha
  track; the springs above replicate its vocabulary with stable APIs.
- Phase 48 (`CaretCallSiteTest`, `SoraEditorHost`) and Phase 45 (coach marks) —
  the invariants this part must not break.

## Deferred / rejected with reasons

- **`androidx.compose.material3:material3:1.5.0-alpha` (`MotionScheme`,
  shape-morphing `ButtonGroup`, `LoadingIndicator`)** — alpha, on a shipping
  `targetSdk 28` build; dossier D1. Re-evaluate when stable.
- **Lottie / Rive illustrations** — bytes for decoration.
- **Shared-element (hero) transitions between hub → editor** — beautiful, but
  they need a shared `LookaheadScope` across a NavHost and would touch every
  open-file navigation Phases 46/47 just fixed. A later row.
- **Animating the terminal's text output** — Phase 36 tuned that path for speed;
  leave it.

## Implementation (2026-09-20)

**The vocabulary** (`ui/theme/CodecMotion.kt`, zero new dependencies):
spatial + effects springs, the 150/300/500 ms ladder, the one emphasized
easing — with `MotionPolicy` resolving Android's remove-animations switch
(animator scale 0) or a reduce-motion signal to genuinely instant specs.
`CodecMotionTest` + `MotionPolicyTest` pin the values and the gate;
`MotionWiringTest` pins the call sites and the animation-free zones.

**The six transitions, and only six** (every one gated on
`rememberMotionSpecs()`, so motion-off is instant):

1. Forward navigation fades (`MainActivity` NavHost, `tabEnter`/`tabExit`);
   pops are pinned `None` — Phase 49 decides back, `BackRouter` holds zero
   transition references.
2. The output panel grows upward on the panel spec (`EditorScreen` host
   wraps splitter + panel; the collapsed strip still swaps instantly).
3. The find bar drops down on the shared spec instead of the defaults.
4. The editor title chrome crossfades when the tab strip appears/disappears
   (file open/close) — chrome only, never the code view.
5. Each new run-state summary crossfades in (the RUN ▶ reveal).
6. The hub's empty ↔ list crossfades (`ProjectsHubListContent` extraction —
   creating or deleting the last project reads as one change).

Laws kept: nothing inside `SoraEditorHost`, the terminal, IME-resized
surfaces, or the coach marks (all pinned by scan); nothing delays back
navigation; motion follows state, never gates an action. Two spec-draft
audits did not survive contact with the tree and were corrected rather than
implemented: back navigation is centralised (`onNavigateBack` → NavHost pops,
already transition (1)'d) and the status bar holds no animation.

Device rows: L11–L12 in `DEVICE_ROUND.md` (owner-run, pending).
