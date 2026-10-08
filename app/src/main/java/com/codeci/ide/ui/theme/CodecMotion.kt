package com.codeci.ide.ui.theme

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize

/**
 * Phase 50.4 — motion, for the first time.
 *
 * The evidence: one `AnimatedVisibility` in the whole app
 * (`EditorScreen.kt`, the find bar, on defaults nobody chose) and zero
 * animation specs. Material 3 Expressive's `MotionScheme` lives on the
 * 1.5.0-alpha track (dossier D1), so this object replicates its vocabulary
 * — spatial + effects springs, an emphasized easing, a duration ladder —
 * with the stable APIs already on the classpath. Zero new dependencies.
 *
 * This file is the ONLY place in the app that may call `spring(`/`tween(`
 * /`snap(` (pinned by `MotionWiringTest`): call sites take
 * ready-made specs ([tabEnter], [panelEnter], [crossfadeSpec], …) and gate
 * them on [MotionSpecs.useSpring], so Android's own "remove animations"
 * switch makes decorative movement genuinely instant. The status-story timer
 * is elapsed reading time, not decorative motion, so its progress stays timed.
 *
 * The seven transitions: (1) forward navigation fades (the tab
 * switch is the most frequent one; the NavHost cannot scope narrower, so
 * every forward navigate shares the 150 ms fade — pops are always instant,
 * because Phase 49 decides back); (2) the output panel expand/collapse;
 * (3) the find bar, on the shared spec instead of defaults; (4) the editor
 * chrome crossfade on file open/close (chrome only — never the code view);
 * (5) the RUN ▶ → output reveal (the run-state summary crossfade plus the
 * panel); (6) the hub's empty ↔ list crossfade; (7) the first-run
 * introduction's one-shot reveal and page crossfade. The onboarding motion is
 * confined to that screen, uses this same reduced-motion-aware vocabulary,
 * and never delays its buttons or the splash handoff.
 *
 * Laws (Phases 44–49 stay intact): no animation inside `SoraEditorHost`,
 * the terminal emulator, or any surface the IME resizes;
 * nothing delays a back navigation; motion follows state, it never gates
 * an action (45.2's single-click law).
 */
object CodecMotion {

    /**
     * The spatial spring: position/size changes. M3 Expressive's vocabulary
     * (damping just under critical, soft stiffness) in stable APIs.
     */
    val spatialSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)

    /** The effects spring: alpha/scale changes that must not overshoot. */
    val effectsSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** [spatialSpring], sized for height animations (`expandVertically`). */
    val panelSpring = spring<IntSize>(dampingRatio = 0.8f, stiffness = 380f)

    /** The duration ladder, in ms. */
    object Duration {
        const val SHORT = 150
        const val MEDIUM = 300
        const val LONG = 500
        /**
         * A readable status-story interval; the final acknowledgement never
         * auto-dismisses.
         *
         * Phase 101 - **8 s, not 10.** Each tour page is a heading, two lines
         * and a caption: the whole page is one glance plus one re-read, and the
         * owner's brief asks the tour to move faster. Ten seconds was a slide
         * deck's pace for a screen the reader can already swipe past, and the
         * swipe is the real control — the timer only exists so that a reader
         * who does nothing is still carried forward. Eight seconds cuts 8 s of
         * dead time out of the four automatic pages and still leaves room to
         * read the longest one twice. The dots keep measuring this window
         * exactly, and `Pause` still stops the clock.
         */
        const val STORY = 8_000
    }

    /** The one easing curve. */
    object Easing {
        val EMPHASIZED: CubicBezierEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    }

    /** 150 ms emphasized fade — the workhorse alpha spec. */
    val fadeSpecShort: FiniteAnimationSpec<Float> =
        tween(Duration.SHORT, easing = Easing.EMPHASIZED)

    /** 300 ms emphasized fade — for the transitions that carry meaning. */
    val fadeSpecMedium: FiniteAnimationSpec<Float> =
        tween(Duration.MEDIUM, easing = Easing.EMPHASIZED)

    /** One-shot position reveal for the first-run illustrations. */
    val introReveal: FiniteAnimationSpec<Float> =
        tween(Duration.LONG, easing = Easing.EMPHASIZED)

    /**
     * Linear elapsed-time indicator for the swipeable first-run stories. Unlike
     * visual transitions, it must not snap when reduced motion is enabled.
     */
    fun storyTimer(durationMillis: Int): FiniteAnimationSpec<Float> =
        tween(durationMillis = durationMillis.coerceAtLeast(1), easing = LinearEasing)

    /** Genuinely instant — what every transition becomes with motion off. */
    val snapFloat: FiniteAnimationSpec<Float> = snap()

    /**
     * Phase 51.3 — the skeleton's breath: one slow alpha pass, reversed. A
     * loading placeholder must look like *content is coming* (a shape, in the
     * list's own rhythm), never like a spinner: a spinner says "wait", a
     * skeleton says "here". The platform's remove-animations switch flattens it
     * to a static shape, which is exactly what a placeholder should be then
     * (see `SkeletonBox`, the only user).
     */
    val shimmer: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(Duration.LONG, easing = Easing.EMPHASIZED),
        repeatMode = RepeatMode.Reverse,
    )

    /**
     * Crossfade spec for `Crossfade` call sites (editor chrome, run-state
     * summary, hub empty ↔ list): short enough to feel instant, long enough
     * to read as a transition.
     */
    val crossfadeSpec: FiniteAnimationSpec<Float> = fadeSpecShort

    /** (1) Forward navigation: a short cross-fade. Pops are always `None`. */
    val tabEnter: EnterTransition = fadeIn(fadeSpecShort)

    /** (1) The leaving screen of a forward navigation. */
    val tabExit: ExitTransition = fadeOut(fadeSpecShort)

    /**
     * (2) The output panel, bottom-anchored: grows upward on the spatial
     * spring with a short fade.
     */
    val panelEnter: EnterTransition =
        expandVertically(panelSpring, expandFrom = Alignment.Bottom) + fadeIn(fadeSpecShort)

    /** (2) The panel collapsing back down. */
    val panelExit: ExitTransition =
        shrinkVertically(panelSpring, shrinkTowards = Alignment.Bottom) + fadeOut(fadeSpecShort)

    /**
     * (3) The find bar, top-anchored: drops down on the shared spring.
     * The bar's visibility is tap-driven, never IME-driven, so no layout
     * animation can fight Phase 48's caret work.
     */
    val findEnter: EnterTransition =
        expandVertically(panelSpring, expandFrom = Alignment.Top) + fadeIn(fadeSpecShort)

    /** (3) The find bar going back up. */
    val findExit: ExitTransition =
        shrinkVertically(panelSpring, shrinkTowards = Alignment.Top) + fadeOut(fadeSpecShort)
}

/**
 * What the platform says about motion. [animatorDurationScale] is
 * `Settings.Global.ANIMATOR_DURATION_SCALE` (the "remove animations"
 * accessibility switch zeroes it); [reduceMotion] is the explicit
 * accessibility signal for callers that have one.
 */
data class MotionInput(
    val animatorDurationScale: Float,
    val reduceMotion: Boolean,
)

/**
 * The resolved answer for every transition call site. [enterMs]/[exitMs]
 * are the budget ([CodecMotion.Duration.MEDIUM]/[CodecMotion.Duration.SHORT]);
 * [useSpring] is the gate — when false, [orNone]/[floatOrSnap] return the
 * instant spec instead of the animated one.
 */
data class MotionSpecs(
    val enterMs: Int,
    val exitMs: Int,
    val useSpring: Boolean,
) {
    /** [motion], or nothing at all when the platform says instant. */
    fun orNone(motion: EnterTransition): EnterTransition =
        if (useSpring) motion else EnterTransition.None

    /** [motion], or nothing at all when the platform says instant. */
    fun orNone(motion: ExitTransition): ExitTransition =
        if (useSpring) motion else ExitTransition.None

    /** [motion], or [CodecMotion.snapFloat] when the platform says instant. */
    fun floatOrSnap(motion: FiniteAnimationSpec<Float>): FiniteAnimationSpec<Float> =
        if (useSpring) motion else CodecMotion.snapFloat
}

object MotionPolicy {

    /** No motion: zero-ms budget, no spring, instant specs. */
    val INSTANT = MotionSpecs(enterMs = 0, exitMs = 0, useSpring = false)

    /**
     * The policy: instant when the platform's animator scale is 0 (the
     * "remove animations" switch) or reduce-motion is on; the shared
     * budget otherwise. A partial scale (0.5×) still animates — only a
     * deliberate zero silences the app.
     */
    fun specsFor(input: MotionInput): MotionSpecs =
        if (input.reduceMotion || input.animatorDurationScale == 0f) {
            INSTANT
        } else {
            MotionSpecs(
                enterMs = CodecMotion.Duration.MEDIUM,
                exitMs = CodecMotion.Duration.SHORT,
                useSpring = true,
            )
        }

    /**
     * Thin Android adapter: reads the animator scale once. There is no
     * pre-33 reduce-motion flag to read — the platform's own
     * remove-animations switch zeroes the animator scale, which the policy
     * already honours — so [MotionInput.reduceMotion] stays false here and
     * exists for callers with an explicit signal (and for the test).
     */
    fun inputFor(contentResolver: ContentResolver): MotionInput = MotionInput(
        animatorDurationScale = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ),
        reduceMotion = false,
    )
}

/**
 * The one motion hook call sites use: resolves the platform's answer once
 * per composition. Read at composition, not at transition time, so a
 * transition never changes character mid-flight.
 */
@Composable
fun rememberMotionSpecs(): MotionSpecs {
    val contentResolver = LocalContext.current.contentResolver
    return remember { MotionPolicy.specsFor(MotionPolicy.inputFor(contentResolver)) }
}
