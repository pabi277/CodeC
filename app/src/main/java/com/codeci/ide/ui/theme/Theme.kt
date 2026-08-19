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
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

fun parseAccentColor(hex: String): Color? {
  return try {
    val cleaned = hex.trim().removePrefix("#")
    val value = cleaned.toLong(16)
    when (cleaned.length) {
      6 -> Color(0xFF000000 or value)
      8 -> Color(value)
      else -> null
    }
  } catch (_: Exception) {
    null
  }
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  accentHex: String? = null,
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val accent = accentHex?.let { parseAccentColor(it) }
  val useDynamic = dynamicColor && accent == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val base =
    when {
      useDynamic -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }
  val colorScheme = if (accent != null) base.copy(primary = accent) else base

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
