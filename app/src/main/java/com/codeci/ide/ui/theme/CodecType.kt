package com.codeci.ide.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.codeci.ide.R

/**
 * Phase 50.3 — a type scale, not the template's one line.
 *
 * The evidence: `ui/theme/Type.kt` was the Android Studio template unedited —
 * one style overridden (`bodyLarge`), every other role still the commented-out
 * template block. Every "headline", "title" and "label" in the app was
 * whatever `androidx.compose.material3` happens to default to.
 *
 * Six roles, each with size + line height + weight + letter spacing; line
 * height is always a multiple of the size ([LineHeight]), never a magic
 * number. All fifteen M3 slots are filled coherently (the app uses
 * `titleMedium`, `bodySmall` and friends directly), and `CodecTypeTest`
 * proves the six anchors are ordered, exact multiples, and none is left at
 * the Material default.
 *
 * Code surfaces (the editor status bar's path, the terminal, the diff view,
 * output lines, file paths in dialogs) use [codeFamily]: the bundled
 * JetBrains Mono (Medium + Bold, SIL OFL-1.1 — already shipped since Phase
 * 19.2 for the terminal, licence in `assets/licenses/JETBRAINS_MONO_OFL.txt`),
 * by the owner's explicit decision (2026-09-20, chat: vendor it). Zero new
 * bytes — the `.ttf` files were already in the APK; this only builds the
 * Compose face for them, the way `TerminalThemePreview` already did.
 *
 * Untouched on purpose: the editor itself (sora owns the code font, the four
 * editor themes own its colours — the owner's Phase 29.1 decision) and the
 * user's named font-setting maps ("Monospace" still means the platform face
 * there, because the terminal setting distinguishes it from "JetBrains Mono"
 * by name).
 */
object CodecType {

    /** Anchor sizes in sp. In-between M3 slots interpolate between anchors. */
    object Size {
        const val DISPLAY = 34f
        const val HEADLINE = 26f
        const val TITLE = 20f
        const val BODY = 15f
        const val LABEL = 13f
        const val CAPTION = 11f
    }

    /** Line-height multiples — the second half of every role. */
    object LineHeight {
        const val TIGHT = 1.20f
        const val NORMAL = 1.40f
        const val RELAXED = 1.55f
    }

    /**
     * The one code face: JetBrains Mono Medium as regular, Bold as bold.
     * `Font(resId, …)` holds the resource id only — loading happens at
     * composition — so this `val` is safe to reference from host tests.
     */
    val codeFamily: FontFamily = FontFamily(
        Font(R.font.jetbrainsmono_medium, FontWeight.Normal),
        Font(R.font.jetbrainsmono_bold, FontWeight.Bold),
    )

    private fun role(
        size: Float,
        lineMultiple: Float,
        weight: FontWeight,
        letterSpacing: Float,
    ): TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * lineMultiple).sp,
        letterSpacing = letterSpacing.sp,
    )

    /**
     * The full Material scale, every slot filled. Anchors: display 34,
     * headline 26, title 20, body 15, label 13, caption 11. Wired once, in
     * `MyApplicationTheme` (`typography = CodecType.scale()`).
     */
    fun scale(): Typography = Typography(
        displayLarge = role(Size.DISPLAY, LineHeight.TIGHT, FontWeight.Bold, -0.25f),
        displayMedium = role(30f, LineHeight.TIGHT, FontWeight.Bold, -0.25f),
        displaySmall = role(28f, LineHeight.TIGHT, FontWeight.SemiBold, 0f),
        headlineLarge = role(Size.HEADLINE, LineHeight.TIGHT, FontWeight.SemiBold, 0f),
        headlineMedium = role(24f, LineHeight.TIGHT, FontWeight.SemiBold, 0f),
        headlineSmall = role(22f, LineHeight.TIGHT, FontWeight.SemiBold, 0f),
        titleLarge = role(Size.TITLE, LineHeight.NORMAL, FontWeight.SemiBold, 0f),
        titleMedium = role(18f, LineHeight.NORMAL, FontWeight.Medium, 0.1f),
        titleSmall = role(16f, LineHeight.NORMAL, FontWeight.Medium, 0.1f),
        bodyLarge = role(Size.BODY, LineHeight.RELAXED, FontWeight.Normal, 0.5f),
        bodyMedium = role(14f, LineHeight.RELAXED, FontWeight.Normal, 0.25f),
        bodySmall = role(Size.LABEL, LineHeight.NORMAL, FontWeight.Normal, 0.4f),
        labelLarge = role(Size.LABEL, LineHeight.NORMAL, FontWeight.Medium, 0.1f),
        labelMedium = role(12f, LineHeight.NORMAL, FontWeight.Medium, 0.5f),
        labelSmall = role(Size.CAPTION, LineHeight.NORMAL, FontWeight.Medium, 0.5f),
    )
}
