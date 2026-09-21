package com.codeci.ide.ui.components

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.codeci.ide.ui.settings.SettingsManager

/**
 * Phase 51.4 — the ONE place the app's chrome touches the platform's feedback.
 *
 * [HapticPolicy] decides; this file performs. Every screen asks for a
 * [HapticMoment] through [rememberCodecHaptics], and `HapticWiringTest` pins
 * that: outside this file (and the pre-existing CodeC keyboard, which keeps its
 * own `codec_keys_haptics` setting), nothing in `app/src/main/java` performs
 * haptic feedback.
 *
 * The Settings switch is read here rather than being passed in from five
 * screens: one reader, one behaviour, and the switch cannot be half-wired.
 * It defaults **on** — the eight moments are quiet by nature.
 */
class CodecHaptics(
    private val feedback: HapticFeedback,
    private val enabledInSettings: Boolean,
    private val hasVibrator: Boolean,
) {
    /** Performs the moment's feedback, or nothing at all. Never throws. */
    fun perform(moment: HapticMoment?) {
        val strength = HapticPolicy.performFor(
            HapticInput(
                moment = moment,
                enabledInSettings = enabledInSettings,
                hasVibrator = hasVibrator,
            )
        ) ?: return
        val type = when (strength) {
            // The two platform vocabulary words the finger already knows: the
            // light text-handle tick, and the firm long-press tick.
            HapticStrength.LIGHT -> HapticFeedbackType.TextHandleMove
            HapticStrength.FIRM -> HapticFeedbackType.LongPress
        }
        runCatching { feedback.performHapticFeedback(type) }
    }
}

/**
 * The one hook call sites use. Reads `haptics` from DataStore (default on) and
 * the device's vibrator support once per composition.
 */
@Composable
fun rememberCodecHaptics(): CodecHaptics {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager(context) }
    val enabled by settingsManager.hapticsFlow.collectAsState(initial = true)
    val hasVibrator = remember(context) { HapticsSupport.hasVibrator(context) }
    val feedback = LocalHapticFeedback.current
    return remember(feedback, enabled, hasVibrator) {
        CodecHaptics(feedback, enabled, hasVibrator)
    }
}

/**
 * Thin platform adapter, the same shape `TerminalEmulatorView`'s bell already
 * uses: `VibratorManager` on API 31+, `Vibrator` below (`minSdk` 24).
 */
object HapticsSupport {

    fun hasVibrator(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator?.hasVibrator() ?: false
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            // `hasVibrator()` is the API-24-safe question this needs; no
            // `VibrationEffect` (API 26+) is built anywhere in the app.
            vibrator?.hasVibrator() ?: false
        }
    }.getOrDefault(false)

}
