package com.codeci.ide.ui.theme

/**
 * Phase 50.2 — a brand that is *on* by default.
 *
 * The plan's uncomfortable finding, re-checked against the Phase 50 tree:
 * `Theme.kt` asked for dynamic colour by default (`dynamicColor = true`),
 * so on paper the whole scheme came from the wallpaper on Android 12+.
 * What the tree actually does is subtler — `SettingsManager.accentColorFlow`
 * never emits null (it defaults to CodeC green via
 * `AccentPalette.effectiveStoredAccent`), so `requested` was never null at
 * the `MainActivity` call site and the dynamic branch was already dead code.
 * The app *is* brand-green today; it just cannot say so, cannot offer the
 * wallpaper back, and still leaks the Android Studio template violet through
 * `surfaceTint` (see `Theme.kt`).
 *
 * This policy makes the behaviour honest and testable without Android:
 * a stored accent always wins, Android 7–11 is always brand (there is no
 * dynamic scheme to take), and on Android 12+ the owner's decision
 * (2026-09-20, chat: **brand green by default**) holds unless the user
 * turns on Settings → Appearance → "Match my wallpaper".
 */
enum class BrandMode {
    BRAND,
    DYNAMIC,
}

/**
 * Everything [IdentityPolicy.decide] needs. [storedAccentHex] is the RAW
 * stored value (`SettingsManager.storedAccentFlow`) — null means "never
 * chose", which the defaulting [SettingsManager.accentColorFlow] cannot say.
 * [darkTheme] is carried so the policy — and its test — can state that the
 * mode never depends on the theme.
 */
data class IdentityInput(
    val storedAccentHex: String?,
    val dynamicAvailable: Boolean,
    val darkTheme: Boolean,
)

object IdentityPolicy {

    /**
     * Which colour source the theme uses. A stored choice beats everything
     * (even a stale preference for the wallpaper); without one, the
     * wallpaper wins only when it exists (API 31+) *and* the user asked for
     * it. Everything else is the brand.
     */
    fun decide(input: IdentityInput, prefersWallpaper: Boolean): BrandMode = when {
        input.storedAccentHex != null -> BrandMode.BRAND
        !input.dynamicAvailable -> BrandMode.BRAND
        prefersWallpaper -> BrandMode.DYNAMIC
        else -> BrandMode.BRAND
    }
}
