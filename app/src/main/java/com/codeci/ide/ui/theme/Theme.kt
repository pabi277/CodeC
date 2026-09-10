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
 * into `primary` unchanged, and so every role of the scheme comes from the
 * accent rather than from the template's purple/pink.
 *
 * The old behaviour is the owner's reported bug: the accent — `#FF6200EE`, the
 * Android Studio template violet, which was the default until 40.5 — was
 * pushed into `primary` for BOTH themes, so in dark mode it became violet text
 * on a near-black surface: measured 2.25:1, where WCAG 2.2 §1.4.3 requires
 * 4.5:1 (and `onPrimary` on it was 1.72:1). Now the accent keeps its hue and
 * is moved in lightness only until it clears the threshold; an accent that
 * already passes (e.g. a light-theme accent at 7.44:1) is returned untouched.
 *
 * The default accent is CodeC's own green (`CodecPalette.DEFAULT_ACCENT`); a
 * stored accent always wins, and an unparseable/missing value falls back to the
 * default rather than to the template violet.
 */
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  accentHex: String? = null,
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val requested = accentHex?.let { AccentPalette.parseHex(it) }
  val useDynamic = dynamicColor && requested == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  // Phase 40.5 — a missing/corrupt value means the app's own default (green),
  // never the template violet that used to sit behind `DarkColorScheme`.
  val accentArgb = when {
    useDynamic -> null
    requested != null -> requested
    else -> CodecPalette.DEFAULT_ACCENT
  }
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
      onPrimaryContainer = Color(roles.onContainer),
      // Same accent, less chroma / a contrasting hue: without these the scheme
      // kept the template's purple-grey and pink in secondary/tertiary (the
      // DEBUG log line, the template chips).
      secondary = Color(roles.secondary),
      onSecondary = Color(roles.onSecondary),
      secondaryContainer = Color(roles.secondaryContainer),
      onSecondaryContainer = Color(roles.onSecondaryContainer),
      tertiary = Color(roles.tertiary),
      onTertiary = Color(roles.onTertiary),
      tertiaryContainer = Color(roles.tertiaryContainer),
      onTertiaryContainer = Color(roles.onTertiaryContainer)
    )
  } else {
    base
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
