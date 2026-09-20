# CodeC Phase 50.2 — A brand you can see on Android 12+

> **Status:** 🚧 IMPLEMENTED (2026-09-20, `arena/01a0bd6d-codec`, owner: "Start phase 50"; CI pending — Build APK is executor of record) · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"it's not attractive"*; the older colour row
> (2026-09-05) was *"research throughly on the color of the app's inside texts …
> Now it is violet 💜 but not very good to read … Make the green as default"* —
> which Phase 40.5 answered for **contrast**. This part answers the half 40.5
> could not: **identity**.
> Parent: [`README.md`](README.md) ·
> [`PHASE50_52_ROADMAP.md`](../PHASE50_52_ROADMAP.md).

## First move: evidence, not code

```text
ui/theme/Color.kt:5-11
  val Purple80 = Color(0xFFD0BCFF)   val PurpleGrey80 = Color(0xFFCCC2DC)
  val Pink80   = Color(0xFFEFB8C8)   val Purple40 = Color(0xFF6650a4)
  val PurpleGrey40 = Color(0xFF625b71) val Pink40 = Color(0xFF7D5260)

ui/theme/Theme.kt:15-19
  private val DarkColorScheme  = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)
  private val LightColorScheme = lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

ui/theme/Theme.kt:43-70
  fun MyApplicationTheme(darkTheme…, accentHex: String? = null, dynamicColor: Boolean = true, …)
    val useDynamic = dynamicColor && requested == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val accentArgb = when { useDynamic -> null; requested != null -> requested
                            else -> CodecPalette.DEFAULT_ACCENT }
```

Two findings, and the first one is the uncomfortable one:

1. **On the owner's own phones the app has no brand colour at all.**
   `dynamicColor` defaults to `true` and `requested == null` on a fresh install,
   so on Android 12+ the whole scheme is generated from the **wallpaper**. CodeC
   is the only thing on the screen that is not CodeC-coloured. (On Android 7–11
   — `minSdk` is 24 — it falls through to the template: finding 2.)
2. **The fallback is literally the Android Studio template palette** —
   `Purple80`/`Pink80`, the violet the owner complained about. Phase 40.5
   overlaid *accent roles* on top of it for `primary`/`container`, but
   `secondary`/`tertiary` still carry the template's purple-grey and pink into
   chips, log lines and template-coloured decorations.

Both are visible on a device, and neither is provable by a unit test — which is
why the **exit condition** below is a measured contrast table plus a source pin,
and the *look* is the device round's job (L5-L8).

## Design

### The decision, and why it is the owner's to make

```kotlin
enum class BrandMode { BRAND, DYNAMIC }
data class IdentityInput(
    val storedAccentHex: String?,     // a user choice always wins
    val dynamicAvailable: Boolean,    // API 31+ and the platform produced a scheme
    val darkTheme: Boolean,
)
object IdentityPolicy {
    fun decide(i: IdentityInput, prefersWallpaper: Boolean): BrandMode = when {
        i.storedAccentHex != null            -> BrandMode.BRAND   // choice beats everything
        !i.dynamicAvailable                  -> BrandMode.BRAND   // Android 7–11
        prefersWallpaper                     -> BrandMode.DYNAMIC
        else                                 -> BrandMode.BRAND   // ← the recommendation
    }
}
```

**Recommendation: BRAND by default, wallpaper available in Settings.**
A code editor is a place the user works for an hour; it should look like itself.
Acode's Play reviews show users *volunteer* praise for theme identity, and no
app people call "polished" (dossier §3.3-§3.5) borrows its accent from the
wallpaper by default.

> 🚩 **Owner decision point — must be answered in chat before this part
> lands.** *Brand-green by default, or wallpaper by default?* Whatever is
> answered is written into `IdentityPolicyTest` and into the Settings copy, and
> the other branch is kept behind the Settings switch.

### The brand ramp (built on Phase 40.5, not beside it)

`AccentPalette.rolesFor(seed, dark, surface)` already does the hard part: it
keeps the hue and moves lightness until the pair clears WCAG AA, and it returns
`primary / onPrimary / container / onContainer`.

```kotlin
object BrandRamp {                       // pure; no Android imports
    data class Roles(
        val primary: Int, val onPrimary: Int, val container: Int, val onContainer: Int,
        val secondary: Int, val onSecondary: Int,       // derived from the SAME seed
        val tertiary: Int,  val onTertiary: Int,        //   (a hue rotation, not the template)
        val surfaceTint: Int,
    )
    fun rolesFor(seed: Int, dark: Boolean): Roles
    fun contrastReport(r: Roles): Map<String, Double>   // every pair, measured
}
```

- **secondary/tertiary come from the seed** (hue rotation + chroma reduction),
  so nothing in the app is the template's violet or pink any more.
- Every pair in `contrastReport` must clear **4.5:1** for text-sized roles and
  **3:1** for large/decorative roles (WCAG 2.2 §1.4.3), measured with the
  **existing** `Contrast` util — the same tool `AppContrastTest` uses, so the
  gate is not new.
- The template constants in `Color.kt` are **deleted** (not deprecated):
  `TemplatePaletteRemovedTest` greps `Theme.kt` for them so they cannot come
  back. `Color.kt` keeps only `CodecPalette`-derived values, if any.

### The Settings row

Settings → Appearance gains one switch: **"Match my wallpaper"** (off by
default), visible only on API 31+. Off = BRAND. On = DYNAMIC. Stored choice
wins over both, exactly as today (`ThemeManager.appThemeFlow` already persists
appearance choices in DataStore — **no new key is invented if the existing
accent key can carry it**; if it cannot, the part doc records why before a key
is added).

## The Android edge

- `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` stay
  for the DYNAMIC branch; only the *default* changes.
- `MyApplicationTheme`'s single call site is `MainActivity.kt:373`; the change
  is one new parameter (`brandMode`) plus the ramp application.
- The **editor** and **terminal** colour schemes (`EditorThemes.kt:9-17`,
  `TerminalThemeType`) are untouched — the four editor themes are the owner's
  own Phase 29.1 decision ("looks like VS Code Dark+").
- Phase 40.5's laws are unchanged and still enforced by `AppContrastTest` and
  `ChromeContrastTest`: the accent is a role, not a hex; no new `Color(0x…)`
  literal for text or icons; values that already pass are not restyled.

## Exit condition

`IdentityPolicy.decide` is pinned case by case; `BrandRamp.rolesFor` produces a
measured report in which **every** text pair clears 4.5:1 in **both** themes;
`grep -n "Purple80\|PurpleGrey80\|Pink80\|Purple40\|PurpleGrey40\|Pink40"
app/src/main/java` returns **zero** in `Theme.kt`; both contrast test files stay
green; and the device round's L5-L8 rows (brand visible in dark, in light, on
Android 12+, and with the wallpaper switch on) are run by the owner.

## Tests (plan)

- `IdentityPolicyTest` (~9): stored accent beats everything; Android 7–11 is
  BRAND even when the user prefers wallpaper; API 31+ with the switch off is
  BRAND; on is DYNAMIC; dark/light do not change the *mode*.
- `BrandRampTest` (~12): every role pair clears its threshold in both themes;
  the ramp is deterministic for a seed; a seed that already passes is returned
  with its hue unchanged; secondary/tertiary are derived (not the template
  constants).
- `TemplatePaletteRemovedTest` (~4, source scan): `Theme.kt` references no
  template colour; `Color.kt` defines none; `MaterialTheme.colorScheme` is not
  constructed from literals anywhere else.
- `AppContrastTest` / `ChromeContrastTest`: **no new cases required** — they
  must simply stay green, and a regression here fails the build.

≈25 cases + the two existing contrast files.

## Sources (record)

- `PHASE50_52_UX_RESEARCH.md` §2.1, §3.3, §3.5, §4, D1.
- Phase 40.5 colour law (`docs/JOURNEY.md`, `prompt.md`) — the standing rule
  this part extends rather than replaces.
- WCAG 2.2 §1.4.3 (contrast minimum) as already applied by
  `ui/theme/Contrast.kt`.

## Deferred / rejected with reasons

- **A theme store / downloadable themes (Acode-style)** — a different owner row;
  it would also mean shipping colour assets, which this repo does not do.
- **Restyling the four editor themes** — owner's Phase 29.1 decision stands.
- **Per-screen accent colours** — the opposite of consistency; rejected.
- **Taking `MaterialTheme` out of the equation with a fully custom theme** —
  the app is 61k lines of material3 components; a custom theme would be a
  rewrite, not a polish.

## Implementation (2026-09-20)

**The decision** (`ui/theme/IdentityPolicy.kt`, host-tested by
`IdentityPolicyTest` over all 16 input combinations): a stored accent always
wins; Android 7–11 is always brand; on 12+ the wallpaper wins only when the
user turns on Settings → Appearance → **"Match my wallpaper"** (off by
default, switch visible on API 31+ only). `MainActivity` resolves
`storedAccentFlow` + `matchWallpaperFlow` into a `BrandMode` and passes it to
`MyApplicationTheme(brandMode = …)`; the theme re-checks the SDK so DYNAMIC
can never reach the dynamic scheme on 7–11 even if a caller mis-decides.

**Why a new DataStore key** (the spec asked for the reason before a key is
added): the existing `accent_color` key holds a colour hex or null, and
"match wallpaper" is orthogonal to it — a user can hold a stored accent
*with* the switch on (stored wins), or hold nothing with the switch on *or*
off (two different modes from the same null). A hex cannot encode that, so
`match_wallpaper` (boolean, default false) is its own key, with
`matchWallpaperFlow` / `setMatchWallpaper` and a reader in both `MainActivity`
and `SettingsScreen` (`SettingsKeysHaveReadersTest` stays green).

**The ramp** (`ui/theme/BrandRamp.kt`): the eight brand roles plus the surface
tint from the one seed, fronting Phase 40.5's twelve-role engine — never
beside it. `BrandRampTest` proves all seven text pairs clear 4.5:1 for all six
picker choices in both themes, and pins the facade equal to the engine on
every shared role.

**The template is gone**: `Color.kt` (six violet constants) and `Type.kt`
deleted; the theme builds its base from plain `darkColorScheme()` /
`lightColorScheme()`; `surfaceTint` is the brand primary (the violet wash on
every card and sheet is fixed). `TemplatePaletteRemovedTest` pins all of it.
`dynamicColor` is gone from the theme signature — its one call site passes
`brandMode` instead.

Device rows: L5–L8 in `DEVICE_ROUND.md` (owner-run, pending).
