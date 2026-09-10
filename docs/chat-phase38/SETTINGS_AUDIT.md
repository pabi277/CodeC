# Phase 38.2 — Settings audit: one row, one effect

> **Law (PART_38_2 §Design 3):** every control in Settings must change
> something observable, and the stored value behind it must have a
> reader. A dead switch is worse than no switch, because the user
> believes it did something. `SettingsAuditTest` pins this table to the
> code (section order + control count), and `SettingsKeysHaveReadersTest`
> walks the real chain (key → flow/setter → out-of-store reader) for
> every DataStore key in the three stores, so the NEXT dead row fails
> the build instead of rotting back.

Audit date: 2026-09-10, against `SettingsScreen.kt` as edited by Phase
38.2 (11 sections, 45 `Settings*` control rows). Phase 41 added a 12th
section — **Feedback & Support** (one OPEN row since the follow-up round;
the content lives on `FeedbackScreen`, see "Other surfaces").

## Deleted by this audit (with evidence)

| Was | Kind | Evidence it was dead | Disposition |
|---|---|---|---|
| "Termux Engine" section (status row, OPEN TERMUX, CHECK BRIDGE, "How to enable") | card + buttons | `BACKEND_AUTO` is the only backend since Phase 21 removed the picker — nothing to choose; the four steps moved to the error path (`CompilerRemediation`, Output Panel) | **deleted**; fallback engine + `RUN_COMMAND` permission + `<queries>` kept |
| "Built-in Compiler" section header | header | its only row (TCC status) is informational; three compiler sections for one automatic policy was the confusion the owner reported | **merged** into the single "Compiler" section |
| "Compiler Settings" header (name) | header | same merge | **renamed** to "Compiler" |
| "Terminal Theme" dropdown in Appearance | dropdown | exact duplicate of the Terminal section's dropdown (both call `themeManager.setTerminalTheme`) — two rows, one effect | **deleted** (Terminal section owns it, next to its preview) |
| "Licenses" item in About | item | duplicated "Open-source licenses" directly above it; controlled nothing | **deleted** |
| `recent_files_csv` key + `recentFilesOrderedFlow` + `addRecentFile`/`replaceRecentFile` | DataStore key (no UI row) | write-only: EditorScreen/EditorViewModel wrote it on open/rename; **no reader anywhere** (the Files tab lists from disk) | **deleted** with its 4 call sites |
| `smart_typing_delete_word` key + flow + setter | DataStore key (no UI row) | no reader, no writer; the ⌫ flick-up delete-word gesture is hardcoded on (`SmartTyping.Config(deleteWord = true)`), with a comment admitting the toggle was "kept for future" | **deleted**; gesture stays, always on |

## Kept sections, stated with their readers (so the next audit does not re-open them)

- **Package Repository & Trust** — drives the Part D trust material:
  `ShellEnvironment.getRepositoryTrustInfo` reads the installed keyring;
  the CHECK button verifies InRelease reachability.
- **GitHub Account** — writes `GitCredentialsStore` (`git_token`,
  `git_username`, `git_author_name`, `git_author_email`), read by
  `GitManager` for every push/pull and by the commit-identity path.
- **Terminal Extra-Keys & Shortcuts** — writes `terminal_extra_keys_macros`,
  read by `terminalExtraKeysMacrosFlow` → the terminal key bar.

## Control rows (one row per Settings* control, in screen order)

Screen order (machine-checked): Editor Settings | CodeC Keys | Compiler | Terminal | Terminal Extra-Keys & Shortcuts | Package Repository & Trust | GitHub Account | Appearance | Storage | About | Feedback & Support | Developer Options

This table is what `SettingsAuditTest` counts: 12 sections, 46 rows
(three of the sections — Terminal Extra-Keys & Shortcuts, Package
Repository & Trust, GitHub Account — are custom cards with no
`Settings*` rows; they are covered under "Other surfaces" below and in
the kept-sections list above). "Info" rows are deliberate prose (they
change nothing and say so).

| # | Section | Control | Kind | What it changes | Reader (who observes it) | Verdict |
|---|---|---|---|---|---|---|
| 1 | Editor Settings | Font Size | slider | `font_size` | `fontSizeFlow` → EditorScreen text size | keep |
| 2 | Editor Settings | Font Family | dropdown | `font_family` | `fontFamilyFlow` → EditorScreen font | keep |
| 3 | Editor Settings | Tab Size | dropdown | `tab_size` | `tabSizeFlow` → TAB indent + formatter | keep |
| 4 | Editor Settings | Line Numbers | switch | `line_numbers` | `lineNumbersFlow` → editor gutter | keep |
| 5 | Editor Settings | Auto Indent | switch | `auto_indent` | `autoIndentFlow` → SmartTyping indent | keep |
| 6 | Editor Settings | Word Wrap | switch | `word_wrap` | `wordWrapFlow` → editor layout | keep |
| 7 | Editor Settings | Autocompletion | switch | `completion_master` | `completionMasterFlow` → ALL completion surfaces (master off = gone) | keep |
| 8 | Editor Settings | How suggestions appear | item | — (info) | — | keep |
| 9 | Editor Settings | Inline ghost text | switch | `completion_ghost` | `completionGhostFlow` → ghost surface | keep |
| 10 | Editor Settings | Suggestion chips in the keys row | switch | `completion_strip` | `completionStripFlow` → chip strip | keep |
| 11 | Editor Settings | "⌄ more" opens the full completion panel | switch | `completion_panel` | `completionPanelFlow` → ⌄-more panel | keep |
| 12 | Editor Settings | Suggestion delay | dropdown | `completion_debounce_ms` | `completionDebounceMsFlow` → VM debounce | keep |
| 13 | CodeC Keys | Dedicated in-app code keyboard | item | — (info) | — | keep |
| 14 | CodeC Keys | CodeC Keys | switch | `codec_keys_enabled` | `codecKeysEnabledFlow` → Keys vs L0 strip + IME | keep |
| 15 | CodeC Keys | Keep the code keyboard open while editing | switch | `editor.keep_keys_open` | `editorKeepKeysOpenFlow` → Keys stay policy | keep |
| 16 | CodeC Keys | Haptic tick per key | switch | `codec_keys_haptics` | `codecKeysHapticsFlow` → Keys haptics | keep |
| 17 | CodeC Keys | Key row height | slider | `codec_keys_height` | `codecKeysHeightFlow` → row height scale | keep |
| 18 | Compiler | C Standard | dropdown | `c_standard` | `cStandardFlow` → `compilerSettingsFrom` → `cc -std=` | keep |
| 19 | Compiler | Warning Level | dropdown | `warning_level` | `warningLevelFlow` → `-Wall -Wextra` flags | keep |
| 20 | Compiler | Optimization Level | dropdown | `optimization_level` | `optimizationLevelFlow` → `-O` flag | keep |
| 21 | Compiler | Engine | item | — (info; carries the one-sentence terminal-app promise since 38.2) | — | keep |
| 22 | Compiler | Built-in TCC status | item | — (info; reads EmbeddedCompiler state) | — | keep |
| 23 | Terminal | Terminal Font Size | slider | `terminal_font_size` | `terminalFontSizeFlow` → TerminalScreen | keep |
| 24 | Terminal | Terminal Font Family | dropdown | `terminal_font_family` | `terminalFontFamilyFlow` → TerminalScreen | keep |
| 25 | Terminal | Terminal Theme | dropdown | `terminal_theme` (ThemeManager) | `terminalThemeFlow` → terminal colours (Appearance duplicate deleted) | keep |
| 26 | Terminal | Terminal | item | — (info) | — | keep |
| 27 | Appearance | Editor Theme | dropdown | `editor_theme` (ThemeManager) | `editorThemeFlow` → editor colours | keep |
| 28 | Appearance | Accent Color | dropdown | `accent_color` | `accentColorFlow` → app accent | keep |
| 29 | Storage | Terminal Storage Access (~/storage) | item + button | storage permission + `~/storage` setup | `ShellEnvironment.hasStoragePermission` / setup | keep |
| 30 | Storage | Projects Location | item | — (info; displays `getExternalFilesDir`) | — | keep |
| 31 | Storage | Temporary files | item | — (info; `TempGc.measure` of `CodeC/temp/runs`) | size shown; Phase 39.1 | keep |
| 32 | Storage | Clear temporary files | action | `TempGc.clearIdle` (idle run dirs only) | space freed; live stamps kept; Phase 39.1 | keep |
| 33 | Storage | Clear Cache | action | deletes `cacheDir` | space freed; toast confirms | keep |
| 34 | About | Show the welcome screen again | action | `first_launch_complete=false` | `firstLaunchCompleteFlow` → MainActivity welcome | keep |
| 35 | About | App Version | item | — (info; 7 taps in DEBUG → `dev_mode`) | `devModeUnlockedFlow` → Developer Options | keep |
| 36 | About | GitHub | item | — (info) | — | keep |
| 37 | About | Open-source licenses | item | — (info; LGPL/MIT obligations) | — | keep |
| 38 | About | Install APK from GitHub | action | downloads latest release APK | `ApkUpdateManager` | keep |
| 39 | Feedback & Support | Send feedback, rate, or report a bug | action | navigates to `Screen.Feedback` | `FeedbackScreen` (Phase 41 follow-up moved the card to its own screen; the exit-prompt switch lives there) | keep |
| 41 | Developer Options | Show File Paths | switch | `show_file_paths` | `showFilePathsFlow` → file tree labels | keep |
| 41 | Developer Options | Export App Logs | action | ACTION_SHARE with `AppLogger` logs | share sheet | keep |
| 42 | Developer Options | View App Logs | action | navigates to log screen | `onNavigateToLogs` | keep |
| 43 | Developer Options | Clear ALL Data | action | deletes single-file storage | `FileManager` | keep |
| 44 | Developer Options | Test Compiler Service | action | probe compile of `int main(){return 0;}` | toast result | keep (dev-only) |
| 45 | Developer Options | Simulate Module Download | action | fake 2s download | logs + toast | keep (dev-only) |
| 46 | Developer Options | Force Crash | action | throws RuntimeException | CrashReportOverlay | keep (dev-only) |

## Other surfaces (not Settings* rows — listed for completeness, not counted)

| Surface | Section | Effect | Verdict |
|---|---|---|---|
| App Theme radio group (3 options) | Appearance | `app_theme` (ThemeManager) → `appThemeFlow` → app dark/light | keep (one row per option would be noise; one effect) |
| Editor theme preview box | Appearance | renders current editor theme | keep |
| Terminal theme preview box | Terminal | renders current terminal theme | keep |
| CodeC Keys live preview | CodeC Keys | renders the keyboard at current height | keep |
| Terminal Extra-Keys card (text field + buttons) | Terminal Extra-Keys & Shortcuts | writes `terminal_extra_keys_macros` | keep |
| Package Repository & Trust card | Package Repository & Trust | shows trust info; CHECK verifies repo | keep |
| GitHub Account card (4 fields, link, disconnect/save) | GitHub Account | writes `GitCredentialsStore` | keep |
| About header (app mark + name + tagline) | About | identity only (Phase 38.1) | keep |
| Feedback screen (`FeedbackScreen`, Phase 41 follow-up: the card moved out of Settings to its own screen) — text field, 2 ephemeral checkboxes, CHAT/COPY/EMAIL/GITHUB buttons, 2 owner reply-to fields + save, exit-prompt switch | Feedback & Support | writes `FeedbackStore` (`feedback_whatsapp_number`, `feedback_contact_email`, `feedback_exit_prompt_enabled`); builds the `FeedbackDraft` report; the checkboxes are deliberately NOT stored (fresh choice per report, pinned by `FeedbackCheckboxNotPersistedTest`) | keep |

## Store keys NOT surfaced as Settings rows (config surface, read-only)

These keys are read by the app but have no editing UI yet — they are
not dead (readers exist), they are unfinished API. Listed so the next
audit sees them deliberately:

| Key | Reader |
|---|---|
| `editor_custom_snippets` | `editorCustomSnippetsFlow` → editor custom-snippet row |
| `editor_key_strip_json` | `editorKeyStripJsonFlow` → editor key strip layout |
| `codec_keys_layout_json` | `codecKeysLayoutJsonFlow` → Keys layout override (dev builds) |
| `smart_typing_type_over` / `wrap_selection` / `empty_pair` / `auto_indent` / `string_aware` | the five `smartTyping*Flow`s → EditorScreen `SmartTyping.Config` |
| `ime_guide_dismissed` | `imeGuideDismissedFlow` / `setImeGuideDismissed` → IME guide one-time banner |
| `dev_mode` | `devModeUnlockedFlow` → Developer Options + About 7-tap |
| `git_token` / `git_username` / `git_author_name` / `git_author_email` | `GitCredentialsStore.stored()` → `GitManager`, commit identity |
