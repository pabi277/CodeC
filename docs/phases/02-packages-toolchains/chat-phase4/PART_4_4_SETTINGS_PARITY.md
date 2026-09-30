# Part 4.4 — Terminal & Editor Settings Parity

## Overview

Part 4.4 harmonizes configuration and visual appearance between the CodeC Editor and Terminal so the application functions as a unified, coherent development environment.

Previously:
- The Editor had customizable font family (`Monospace`, `Courier`, `Sans Serif`, `Serif`), font size (12–32 sp), and theme selection (`Dracula`, `Monokai`, `GitHub Dark`, `Classic Dark`).
- The Terminal only had an isolated font size setting (14 sp default) hardcoded to a fixed dark background (`#121212`) and fixed monospace typeface.

With Part 4.4:
- **Terminal Theme Support**: The Terminal supports all 4 standard CodeC themes (`Dracula`, `Monokai`, `GitHub Dark`, `Classic Dark`), syncing background, foreground text, cursor, selection, and container colors.
- **Terminal Font Family Support**: The Terminal supports custom monospace and proportional font families (`Monospace`, `Courier`, `Sans Serif`, `Serif`) with dynamic canvas metrics recalculation.
- **Settings Screen Integration**: Terminal Font Size, Font Family, Theme selector, and a live syntax-styled Terminal Preview Card are integrated directly into the `SettingsScreen` (under both Terminal Settings and Appearance).
- **Reactive State Flow**: Theme and font changes update immediately across the Terminal screen, Canvas emulator renderer, and Settings preview via reactive DataStore flows (`terminalThemeFlow`, `terminalFontFamilyFlow`, `terminalFontSizeFlow`).

---

## Architectural Changes

### 1. Theme Model & Palette Extension (`EditorThemes.kt`)
- Added `TerminalThemeType` enum (`DRACULA`, `MONOKAI`, `GITHUB_DARK`, `CLASSIC_DARK`).
- Defined `TerminalThemeColors` data class with:
  - `background: Color` & `backgroundRgb: Int`
  - `foreground: Color` & `foregroundRgb: Int`
  - `cursor: Color`
  - `selection: Color`
  - `topBarBackground: Color`
- Added palette definitions matching editor counterparts:
  - `DraculaTerminalTheme`: Background `#282A36`, Foreground `#F8F8F2`, Cursor `#50FA7B`, Selection `0x6644475A`.
  - `MonokaiTerminalTheme`: Background `#272822`, Foreground `#F8F8F2`, Cursor `#A6E22E`, Selection `0x6649483E`.
  - `GitHubDarkTerminalTheme`: Background `#24292E`, Foreground `#E1E4E8`, Cursor `#79B8FF`, Selection `0x663B4048`.
  - `ClassicDarkTerminalTheme`: Background `#121212`, Foreground `#E5E5E5`, Cursor `#55FF55`, Selection `0x6680CBC4`.
- Added helper function `getTerminalTheme(type: TerminalThemeType): TerminalThemeColors`.

### 2. Preference Storage & Management (`ThemeManager.kt` & `SettingsManager.kt`)
- `ThemeManager`:
  - `TERMINAL_THEME_KEY`: string preference key `terminal_theme`.
  - `terminalThemeFlow: Flow<TerminalThemeType>`
  - `suspend fun setTerminalTheme(theme: TerminalThemeType)`
- `SettingsManager`:
  - `TERMINAL_FONT_FAMILY`: string preference key `terminal_font_family`.
  - `terminalFontFamilyFlow: Flow<String>` (default `"Monospace"`)
  - `suspend fun setTerminalFontFamily(family: String)`
  - `terminalFontSizeFlow: Flow<Float>` (range 8–32 sp)

### 3. Reactive Terminal View Model (`TerminalViewModel.kt`)
- Added `terminalTheme: StateFlow<TerminalThemeType>` collected eagerly from `ThemeManager`.
- Added `fontFamily: StateFlow<String>` collected eagerly from `SettingsManager`.
- Added helper actions `setFontFamily(family: String)` and `setTheme(theme: TerminalThemeType)`.

### 4. Canvas Terminal Renderer (`TerminalEmulatorView.kt`)
- Dynamically derives Android `Typeface` from `fontFamily` parameter:
  - `"Courier"` → `Typeface.MONOSPACE`
  - `"Sans Serif"` → `Typeface.SANS_SERIF`
  - `"Serif"` → `Typeface.SERIF`
  - Default → `Typeface.MONOSPACE`
- Recomputes cell width `paint.measureText("X")`, height `paint.fontSpacing`, and baseline ascent whenever font family or font size changes.
- Uses `TerminalThemeColors` for:
  - Surface background fill
  - Text runs with ANSI/RGB fallbacks mapped to theme default foreground and background RGB
  - Selection highlight box
  - Cursor rectangle and alpha blending

### 5. Settings Screen UI (`SettingsScreen.kt`)
- Added Terminal Font Family selector (`Monospace`, `Courier`, `Sans Serif`, `Serif`).
- Added Terminal Theme selector (`Dracula`, `Monokai`, `GitHub Dark`, `Classic Dark`) in both Terminal Settings and Appearance sections.
- Added live `TerminalThemePreview` component displaying a stylized terminal prompt (`codec@user:~$ cc -o hello hello.c`) reflecting the current font family, font size, and terminal theme colors.

---

## Unit Testing & Verification

- `TerminalThemeTest.kt`:
  - `all terminal theme types map to non-null color palettes`
  - `terminal theme mappings resolve correctly`
  - `terminal themes provide distinct rgb values`
- Invariant verification:
  - No `.` on `$PATH`
  - Musl TCC link order preserved
  - Repository metadata signing untouched
