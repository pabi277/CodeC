package com.codeci.ide.ui.components

/**
 * Phase 51.4 — eight moments, two strengths, and nothing else.
 *
 * The evidence (2026-09-21, `main` @ `0f1b650`): `grep -rn "performHapticFeedback"
 * app/src/main/java` finds the CodeC keyboard's key tick (`CodecKeyboard.kt`) and
 * nothing else; the terminal's BEL bell and the `codec-vibrate` script API are the
 * only other vibrator users. So the app itself never answers the user's finger —
 * not when a program finishes, not when it fails, not when a file is saved, not
 * when an install lands. The research is blunt about what that costs: the little
 * details (feedback on the moments that matter) are half of why people *like* an
 * app rather than tolerate it (dossier §3.3).
 *
 * The correction stays small on purpose — a vibrating IDE is worse than a silent
 * one:
 *
 * - **two strengths.** A light tick for "it happened", a firm one for the three
 *   moments that are an *end*: a failure, a close, and the finish of the app's
 *   longest wait (a userland/package install). A taxonomy of eight different
 *   buzzes is unlearnable; the platform's own vocabulary is what the finger
 *   already knows from every other app;
 * - **off means off**: the one Settings switch (default on) silences all eight;
 * - **no vibrator, no crash**: a device without one gets nothing (the terminal
 *   bell already guards this the same way);
 * - **the keyboard's own setting is untouched** (`codec_keys_haptics`, Phase 47.2)
 *   — the eight moments are app chrome, that one is typing.
 *
 * This file is **pure Kotlin** (no Compose, no Android) so `HapticPolicyTest`
 * runs on the host JVM; [CodecHaptics] is the single adapter that turns a
 * [HapticStrength] into the platform call, and `HapticWiringTest` pins that it
 * is the only one.
 */
enum class HapticMoment {
    /** RUN ▶ launched a job. */
    RUN_STARTED,

    /** A program exited with 0. */
    PROGRAM_FINISHED,

    /** A program exited non-zero. */
    PROGRAM_FAILED,

    /** A file was written because the user asked. */
    FILE_SAVED,

    /** A package/install finished landing. */
    INSTALL_FINISHED,

    /** A tab (or file) was closed. */
    TAB_CLOSED,

    /** A project was opened from the hub. */
    PROJECT_OPENED,

    /** A long press took: a row menu, a drag. */
    DRAG_STARTED,
}

/** The two strengths the app is allowed, mapped to the platform's two types. */
enum class HapticStrength {
    /** A light tick — "it happened". */
    LIGHT,

    /** A firm tick — "something ended". */
    FIRM,
}

data class HapticInput(
    val moment: HapticMoment?,
    /** The Settings switch (`haptics`, default on). */
    val enabledInSettings: Boolean = true,
    /** The device actually has a vibrator. */
    val hasVibrator: Boolean = true,
)

object HapticPolicy {

    /**
     * The three ends: a failure, a close, and the end of the longest wait in the
     * app. Everything else is a light tick, so the firm buzz keeps meaning.
     */
    val FIRM: Set<HapticMoment> = setOf(
        HapticMoment.PROGRAM_FAILED,
        HapticMoment.INSTALL_FINISHED,
        HapticMoment.TAB_CLOSED,
    )

    /** The eight, in the order the plan lists them (pinned by `HapticPolicyTest`). */
    val moments: List<HapticMoment> = HapticMoment.entries.toList()

    /**
     * The feedback to perform, or `null` to do nothing at all. The three guards
     * are checked before the mapping, so "off means off" cannot be bypassed by a
     * moment that happens to be firm.
     */
    fun performFor(i: HapticInput): HapticStrength? = when {
        i.moment == null -> null
        !i.enabledInSettings -> null
        !i.hasVibrator -> null
        i.moment in FIRM -> HapticStrength.FIRM
        else -> HapticStrength.LIGHT
    }

    fun isFirm(moment: HapticMoment?): Boolean = moment != null && moment in FIRM
}

/**
 * The run-state rule, kept pure so "no buzz on a re-render" is a test and not a
 * hope.
 *
 * A run's haptic must fire on the *transition the user caused*: RUN_STARTED when
 * a job begins, then exactly one of PROGRAM_FINISHED / PROGRAM_FAILED when it
 * ends. The first observation of a screen owes nothing — opening onto an
 * already-finished run must stay quiet, the same discipline as Phase 48's
 * `CaretVisibilityPolicy`.
 */
object RunHapticRule {

    fun momentFor(wasRunning: Boolean, running: Boolean, exitCode: Int?): HapticMoment? = when {
        !wasRunning && running -> HapticMoment.RUN_STARTED
        wasRunning && !running && exitCode != null ->
            if (exitCode == 0) HapticMoment.PROGRAM_FINISHED else HapticMoment.PROGRAM_FAILED
        else -> null
    }
}
