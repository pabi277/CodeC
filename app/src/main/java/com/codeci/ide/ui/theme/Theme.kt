package com.codeci.ide.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

fun parseAccentColor(hex: String): Color? =
  AccentPalette.parseHex(hex)?.let { Color(it) }

/**
 * Phase 40.5 — applies the user's accent through [AccentPalette.rolesFor], so
 * the accent is *readable in the theme it is used in* instead of being pasted
 * into `primary` unchanged.
 *
 * The old behaviour is the owner's reported bug: the default accent
 * (`#FF6200EE`, the Android Studio template violet) was pushed into `primary`
 * for BOTH themes, so in dark mode it became violet text on a near-black
 * surface — measured 2.25:1, where WCAG 2.2 §1.4.3 requires 4.5:1 (and
 * `onPrimary` on it was 1.72:1). Now the accent keeps its hue and is moved in
 * lightness only until it clears the threshold; an accent that already passes
 * (every accent in light mode, e.g. `#FF6200EE` at 7.44:1) is returned
 * untouched.
 */
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  accentHex: String? = null,
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val accentArgb = accentHex?.let { AccentPalette.parseHex(it) }
  val useDynamic = dynamicColor && accentArgb == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val base =
    when {
      useDynamic -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }
  val colorScheme = if (accentArgb != null) {
    val roles = AccentPalette.rolesFor(
      seed = accentArgb,
      dark = darkTheme,
      surface = base.surface.toArgb()
    )
    base.copy(
      primary = Color(roles.primary),
      onPrimary = Color(roles.onPrimary),
      primaryContainer = Color(roles.container),
      onPrimaryContainer = Color(roles.onContainer)
    )
  } else {
    base
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
