# CodeC IDE — Full Project Analysis

**Analyzed:** 2026-08-19 · **Branch:** `arena/01a01b17-codec` (from `main` @ `19cf97c`) · **CI:** ✅ passing on `main`

> **Addendum 2026-08-29:** this file is the architectural baseline snapshot, kept as
> history. Everything since — storage/install UX (Phase 4), preview & capabilities
> (Phase 5), terminal UX & Package Hub (Phases 6/10), multi-terminal (Phase 7),
> Projects & file tree (Phase 8) and the Editor Foundation with device rounds 9.1/9.2
> (tabs, undo/redo, find/replace, format, squiggles, single files, in-editor folders,
> loopback preview server) — is complete and accepted. The authoritative timeline is
> [docs/JOURNEY.md](docs/JOURNEY.md); the current handoff prompt is
> [prompt.md](prompt.md).
>
> **Addendum 2026-09-01:** through Phase 18 (CodeCApi device capabilities,
> device-accepted) all spec'd work is complete; `main` = `f868e10` and Phase 18
> awaits merge on `arena/01a05b12-codec` (`d49ac47`). Post-Phase-18 work follows
> the owner's operating manual: [`rule.md`](rule.md).

---

## 1. What the project is

**CodeC IDE** is a native Android app that turns a phone into a C development environment: a code
editor with syntax highlighting, a file manager, templates, and a **real on-device Clang compiler**
that is downloaded at runtime from a remote module catalog and executed on the device.

- **Package:** `com.codeci.ide` · **App ID:** `com.codeci.ide` · **Version:** 1.0 (beta)
- **minSdk 24** (Android 7.0) · **targetSdk 34** · **compileSdk 36**
- ~5,165 lines of Kotlin across 37 source files + 3 test files + resources
- Single-activity, Jetpack Compose UI, Navigation Compose, Material 3
- Built with AGP 9.1.1 / Gradle 9.3.1 (wrapper) / JDK 17 / Kotlin 2.2.10
- Distributions: debug APK via GitHub Actions; self-update from GitHub Releases inside the app

---

## 2. Architecture overview

```
MainActivity (single activity)
 └─ MyApplicationTheme (dark/light, accent color, dynamic color)
    └─ Scaffold + NavigationBar (Home · Editor · Modules · Settings)
       └─ NavHost — 7 destinations
          ├─ HomeScreen        — quick actions, recent files, stats, compiler banner
          ├─ EditorScreen      — code editor + symbol bar + terminal (compile & run)
          ├─ FileManagerScreen — CRUD for .c files
          ├─ TemplatesScreen   — 5 starter templates w/ detail + preview
          ├─ ModulesScreen     — module store (download/install Clang)
          ├─ SettingsScreen    — editor/compiler/appearance/storage/dev options
          └─ LogsScreen        — in-memory app log viewer

ViewModels: EditorViewModel · ModuleViewModel · FileManagerViewModel
Services:   CompilerService · DownloadManager · ApkUpdateManager
Managers:   FileManager · SettingsManager · ThemeManager · StatsManager · ManifestRepository
Stores:     InstalledModulesStore (installed.json) · DataStore preferences ("settings")
Modules:    ModuleInstaller (zip extraction, checksums, exec flags) · ModuleCatalog (legacy stub)
Utils:      AppLogger (in-memory ring buffer, 1000 lines) · FileNameUtils (sanitization)
            CSyntaxVisualTransformation (syntax highlighting) · TerminalOutput/SymbolBar
```

**Persistence:** DataStore Preferences for all settings/stats/theme/recent files; plain files on
disk for source code (`CodeC/projects`), module manifests (`manifest.cache.json`), and installed
module registry (`installed.json`).

---

## 3. Feature deep-dive

### 3.1 On-device C compilation (the core feature)
- **Module store** fetches a manifest from
  `https://raw.githubusercontent.com/pabi277/CodeC-Modules/main/manifest.json` (15s connect /
  20s read timeouts, cached to disk, offline fallback with a friendly error).
- **Download manager** streams module zips with **pause / resume (HTTP Range) / cancel** support,
  SHA-256 verification (with one automatic retry on mismatch), and free-space checks.
- **Installer** extracts zips with **zip-slip protection** (canonical-path prefix check), marks all
  binaries executable, and records `installed.json` + a `.installed` version marker.
- **CompilerService** invokes the extracted `clang` via `ProcessBuilder` with a tuned environment
  (`LD_LIBRARY_PATH`, `PREFIX`, `HOME`, `TMPDIR`, `PATH`), supports `compiler-wrapper.sh`
  launchers, applies `-std`/`-Wall`/`-O0..3` from settings, parses GCC/Clang diagnostics with a
  regex into structured errors, and enforces **30s compile / 10s execute timeouts**
  (`destroyForcibly` on API 26+, manual polling fallback on API 24–25).
- Program output streams into an auto-scrolling terminal with colored segments
  (compile / error / output / stats), copy-to-clipboard, and a collapsed peek bar.

### 3.2 Editor
- `BasicTextField` + `CSyntaxVisualTransformation`: keywords, numbers, functions, operators,
  strings, and single-/multi-line comments; 3 editor themes (Monokai, Dracula, GitHub Dark).
- **Auto-indent** on Enter (carries indentation, adds tab after `{`), configurable tab size,
  line-number gutter, font size/family, word wrap.
- **Symbol bar** for quick `{ } ( ) [ ] ; " #` insertion; save with dirty-flag `*` indicator;
  rename with on-disk rename + recent-files list update; unsaved-changes dialog on back;
  terminal clear/copy/collapse.
- ⚠️ Toolbar **Undo / Redo / Format / Find are stubs** ("Coming soon") — buttons are present but
  disabled-looking and non-functional.

### 3.3 File manager
- Lists `.c` files (newest first), create / rename / delete / share, empty-state CTA,
  size + last-modified formatting. All paths pass through `FileNameUtils.sanitizeFileName`
  (whitelist `[A-Za-z0-9._-]`, strips traversal, canonical-path containment check) — good
  hardening against path traversal.

### 3.4 Templates
- 5 built-in templates (Hello World, Calculator, Bubble Sort, Linked List, File I/O), each with
  difficulty, category, description, concepts, and a code preview dialog.

### 3.5 Home
- "Ready to code today?" hero, compiler-required banner with one-tap download (auto-starts the
  Clang module download), action cards (New File / Open File / Templates), recents (max 10, CSV in
  DataStore), and a stats card (total files, total runs, daily streak with 🔥).

### 3.6 Settings
- Editor: font size (12–32 sp), family, tab size, line numbers, auto-indent, word wrap.
- Compiler: C standard (C89/C99/C11/C17), warnings (None/Standard/-Wall -Wextra), optimization
  (O0–O3).
- Appearance: app theme (Light/Dark/System), editor theme, 5 accent colors, live preview card.
- Storage: projects location, cache clearing.
- About: version tap-to-unlock **Developer Options** (debug builds only): show paths, export/view
  logs, clear all data, compiler smoke test, simulated download, force crash.
- **APK self-update:** queries `api.github.com/repos/pabi277/CodeC/releases/latest`, downloads the
  APK, requests `REQUEST_INSTALL_PACKAGES` permission, installs via FileProvider intent, or opens
  the Releases page as fallback.

### 3.7 Build & CI
- `.github/workflows/build-apk.yml` builds `:app:assembleDebug` on every push/PR/release, uploads
  the APK artifact, and attaches it to GitHub Releases.
- Interesting quirk: the workflow pins Gradle 9.0.0, but AGP 9.1.1 needs Gradle 9.3.1, so
  `settings.gradle.kts` **redirects `:app` to a `gradle-bootstrap` shim project** whenever
  `gradle.gradleVersion == "9.0.0"`; that shim re-invokes the real wrapper (running
  assemble + unit tests + lint) and post-processes the logs into GitHub error annotations.
  Clever compatibility bridge; the checked-in wrapper is the real build path.
- Last CI run on `main`: ✅ success (4m38s). The recent history shows the project was actively
  fixed (timeout support on Android 7, Robolectric on JDK 17, lint error reporting in CI).

---

## 4. Strengths

| Area | Notes |
|---|---|
| **Security** | Zip-slip protection (canonical paths), filename sanitization + traversal guard, SHA-256 verification with retry, checksums passed via manifest |
| **Robustness** | Timeouts with API-level fallbacks, threaded stream readers that can't deadlock, storage fallback (public → app-specific) for projects and downloads, offline manifest cache, friendly user-facing errors for network/permission/checksum cases |
| **Android-correctness** | Toolchain kept in **app-private storage** (shared storage is often `noexec`), `setExecutable` before spawn, `LD_LIBRARY_PATH` wiring, FileProvider for APK install, `maxSdkVersion=32` on WRITE_EXTERNAL_STORAGE |
| **UX** | Auto-scroll terminal with colored segments, live compiler-installed banner, one-tap download, streak tracking, dev-mode easter egg |
| **Tooling** | Version catalog, configuration cache, non-transitive R classes, secrets-gradle-plugin for `.env`, Robolectric + Roborazzi test rigs |

---

## 5. Issues & risks (by severity)

### 🔴 Functional
1. **No runtime storage permission flow** — `DownloadManager` and `FileManager` first attempt
   public storage (`/storage/emulated/0/CodeC/...`). On API 24–28 that requires a runtime
   `WRITE_EXTERNAL_STORAGE` grant, which is never requested; on API 29+ scoped storage makes it
   unwritable. The code *does* fall back to app-specific dirs, so it works, but the public-storage
   path is effectively dead code and the temp-dir download path differs from the app-private
   module path (a resumed download could be lost between the two locations on some devices).
2. **Undo/Redo/Format/Find are fake** — user-facing toolbar affordances with no implementation.
   In particular, `EditorViewModel` keeps no history stack, so even accidental deletions can't be
   undone.
3. **Editor state lost on rotation** — `EditorViewModel` is created per-`composable` via
   `viewModel()` without an Activity-scoped store; configuration changes (rotation) can reset
   unsaved buffer contents. (Should use `activityViewModel` / SavedStateHandle.)

### 🟠 Maintainability / correctness
4. **`metadata.json` + `.env.example` advertise a Gemini API capability** (`GEMINI_API_KEY`,
   `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API`) but **no Gemini/AI code exists anywhere** in the
   repo. Dead configuration; misleading for AI Studio packaging.
5. **Version catalog carries ~10 unused libraries** (Coil, Accompanist, Play Services Location,
   CameraX, Credentials, GoogleID) — leftovers from a template. Harmless to the APK but confusing.
6. **Hardcoded strings in UI** — `SettingsScreen`, `TerminalOutput`, and `SymbolBar` mix
   `stringResource(...)` with inline literals ("Font Size", "Terminal", "Copy", etc.), so the app
   can't be fully localized; `strings.xml` already exists for most of these.
7. **Version drift** — `versionCode=1`/`versionName="1.0"` in Gradle vs `"1.0.0 (Beta)"` and
   `"App Version 1.0.0 (Beta)"` hardcoded in resources; also `versionName` isn't displayed
   dynamically, so APK self-update can't detect "same version" (it always offers the latest
   release).
8. **Compiler diagnostics filename mismatch** — `CompilerService` compiles `source_<stamp>.c`
   but `EditorViewModel.formatError` prints errors as `source.c:<line>:<col>`, so **tapping/linking
   error lines to editor locations isn't possible** and line numbers are only approximate (they're
   right, since the file content is identical, but the name is cosmetic).
9. **`ModuleViewModel` runs in the Home screen too** — `HomeScreen` calls
   `activityModuleViewModel()` (shared), which is good, but it means the module catalog is fetched
   on app start even if the user never opens Modules.
10. **Broad executable flags** — `markBinariesExecutable` marks *every* file under `bin/` and the
    install dir executable (not just `clang`); low risk, but a smaller surface would be better.

### 🟡 Testing
11. **Only template tests** — the 3 test files are the stock Android Studio/JetBrains examples
    (`2+2==4`, Robolectric reads app name, instrumentation checks package). **None of the core
    logic is tested**: `parseDiagnostics`, `FileNameUtils`, `ModuleInstaller.extractZip`
    (zip-slip!), `checksumMatches`, `StatsManager` streak math, `DownloadManager` resume logic,
    `EditorViewModel` auto-indent — all prime candidates and all testable without a device.
12. `ExampleRobolectricTest` and `ExampleUnitTest` are trivial; Roborazzi is configured but unused.

### 🟢 Minor
13. `gradle-bootstrap` shim is a clever but fragile CI bridge (relies on log scraping and
    `gradleVersion == "9.0.0"` string match) — consider just pinning `gradle-version: 9.3.1` in
    the workflow and deleting the shim.
14. `AppThemeMode`/`EditorThemeType` values stored as enum names in DataStore — renaming enums
    silently resets user preference; a versioned key would be safer.
15. `AppLogger` keeps a 1000-line in-memory buffer with no size cap on message length; a huge
    `Log` line (e.g. compiler dump) is fine but the state flow recomposes `LogsScreen` on every
    entry — acceptable for a dev tool.
16. `versionTaps` unlock counter resets if the user leaves Settings — minor.
17. `ManifestRepository` has no cache-busting (no ETag/If-Modified-Since) — always re-downloads
    the manifest, which is small, so fine.

---

## 6. File inventory (37 main sources, 5,165 LOC)

| Area | Files | ~LOC |
|---|---|---|
| Screens | Editor, Home, FileManager, Settings, Modules, Templates, Logs | 2,575 |
| ViewModels | Editor, Module, FileManager | 576 |
| Services | CompilerService, DownloadManager, ApkUpdateManager | 702 |
| Modules (store/install) | ModuleInstaller, InstalledModulesStore, ManifestRepository, Module, ModuleCatalog | 372 |
| Utils | FileManager, FileNameUtils, AppLogger, CSyntaxVisualTransformation | 268 |
| Theme | Theme, Color, Type, ThemeManager, EditorThemes | 215 |
| Settings / Stats | SettingsManager, StatsManager | 150 |
| Components / Nav / Models | SymbolBar, TerminalOutput, Screen, Template | 307 |

---

## 7. Recommendations (priority order)

1. **Add real unit tests** for `FileNameUtils`, `ModuleInstaller.extractZip` (zip-slip), `CompilerService.parseDiagnostics`, `StatsManager` streaks, and `EditorViewModel` auto-indent — high value, zero device needed, CI already runs them.
2. **Implement undo/redo** in `EditorViewModel` (simple text-snapshot stack) or remove/disable the toolbar buttons honestly.
3. **Fix rotation state loss** — scope `EditorViewModel` to the Activity or persist buffer via `SavedStateHandle`.
4. **Remove the Gemini/misleading config** (metadata.json capability + `.env.example` comment) or actually wire the API.
5. **Simplify CI** — pin `gradle-version: "9.3.1"` in the workflow and delete the `gradle-bootstrap` shim + the `settings.gradle.kts` conditional.
6. **Localize SettingsScreen** and the components (move literals into `strings.xml`).
7. **Unify storage paths** — drop the public-storage attempts (or add a real permission flow) so downloads and modules live in the same app-private tree.
8. **Surface `versionName` dynamically** and consider comparing versions before offering an APK update.
9. Clean the version catalog of unused libraries; wire `versionCode`/`versionName` into the Settings "About" section.

---

*Note: a local Gradle build was not possible in this sandbox (no JDK, no Android SDK, and outbound
network is restricted to GitHub). Build health is confirmed via the GitHub Actions history, where
the latest run on `main` succeeded (4m38s).*

---

## Update — compile-time "Permission denied" bug fix (2026-08-19)

A runtime bug was reported: compiling fails with

```
error: .../modules/clang-compiler/clang-compiler/bin/compiler-wrapper.sh[11]:
       .../clang-compiler/clang-compiler/bin/clang: Permission denied
```

**Root cause (two compounding issues):**

1. **Doubled install path** — the module zip wraps the toolchain in a top-level
   `clang-compiler/` folder, while the manifest's `install_path` is already
   `clang-compiler`. The app extracted it verbatim, producing
   `modules/clang-compiler/clang-compiler/bin/...`, so the manifest-declared
   `bin/compiler-wrapper.sh` / `bin/clang` paths don't resolve directly (the app
   only found the wrapper via a full-tree search).
2. **`EACCES` on `exec`** — either the device/emulator mounts app storage
   noexec, or the executable bit was lost; plus the toolchain is Termux-built,
   so its `bin/clang`/versioned-`.so` **symlinks are flattened into text
   placeholders** by `ZipInputStream`, which breaks execution once the bits are
   fixed.

**Fixes applied:**

- `ModuleInstaller.extractZip` now **flattens single-root archives**
  (`clang-compiler/` → contents moved up), so installs land at the
  manifest-expected paths.
- New `materializeFlattenedSymlinks()` **reconstructs symlinks** from
  flattened placeholders (`Os.symlink` → `ln -sfn` → copy fallback).
- `markBinariesExecutable()` now **verifies** `canExecute()` and falls back to
  `chmod 755` (API-level guarded `destroyForcibly`); returns success/failure.
- `CompilerService` pre-flights the real binary (wrapper-aware) and runs
  `repairToolchain()` on old installs **without re-download**.
- New `detectEnvironmentError()` maps the cryptic shell errors to actionable
  messages: `Permission denied` → device blocks execution (emulators/cloud
  phones), `Exec format error` → ARM64-only, missing `.so` → reinstall.
- `ModuleViewModel` install flow runs the new pipeline and reports a clear
  failure if the compiler binary can't be made executable.
- Unit tests added: `ModuleInstallerTest`, `CompilerServiceTest`.
- README gained a Troubleshooting section.
