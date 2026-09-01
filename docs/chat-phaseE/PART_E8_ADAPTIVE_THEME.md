# CodeC Phase E.8 — Adaptive Theme (Auto Follow System Dark/Light)

**Status:** 📋 **PLANNED** · **Cost:** `[client-only]` · **Effort:** XS
· **Depends on:** nothing (ThemeManager already exists)
· **Target files:** `ui/settings/SettingsManager.kt`,
  `ui/theme/ThemeManager.kt`, `ui/screens/SettingsScreen.kt`

---

## 1. Design

`ThemeManager` already stores a user-chosen theme (Dark / Light / specific
named theme). Add an **"Auto (follow system)"** option: when selected,
the app reads `isSystemInDarkTheme()` from Compose and picks `DarkTheme`
or `LightTheme` accordingly. The theme switches live (e.g. when the user
pulls the system quick-settings shade to toggle dark mode).

### Implementation

```kotlin
// ThemeManager.kt — add:
enum class ThemeMode { AUTO, DARK, LIGHT }

val themeMode: StateFlow<ThemeMode> = ...  // from SettingsManager

// In the app root (MainApp or MainActivity setContent):
val themeMode by themeManager.themeMode.collectAsState()
val systemDark = isSystemInDarkTheme()

val effectiveDark = when (themeMode) {
    ThemeMode.AUTO  -> systemDark
    ThemeMode.DARK  -> true
    ThemeMode.LIGHT -> false
}

CodeCTheme(darkTheme = effectiveDark) { ... }
```

### Settings UI

In Settings → Appearance → Theme: add an **"Auto"** option at the top of
the theme list / dropdown. Selecting Auto dismisses any explicit dark/light
override.

---

## 2. Implementation steps

1. Add `ThemeMode` enum and `themeMode: StateFlow` to `ThemeManager`.
2. Wire `effectiveDark` in the app root as shown above.
3. Add **Auto** option to the Settings theme picker.
4. Write a host unit test — `ThemeMode.AUTO` + `systemDark = true` → `effectiveDark = true`.

---

## 3. Exit condition

```text
1. Settings → Theme → select "Auto".
2. Enable system dark mode (pull quick settings).
   EXPECT: CodeC switches to dark theme immediately (no app restart).
3. Disable system dark mode.
   EXPECT: CodeC switches to light theme.
4. Settings → Theme → select "Dark" explicitly.
   EXPECT: CodeC stays dark even when system is light.
PASS = steps 1–4 behave as described.
```
