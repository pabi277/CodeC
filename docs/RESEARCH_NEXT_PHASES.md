# CodeC - Next Phases Research & Design (phone-first)

> Status: **research / design draft** - no code written. Owner requested a full
> research pass and detailed, *research-open* phases for three new product
> directions, then a follow-up decision to **retire TCC and compile C/C++ with a
> userland `gcc`/`g++` toolchain (like Python) behind a generic, multi-language
> run model**, and then to **pull the feasible items from groups 3-5 (smarter
> runs, adaptive device, reach/polish) into the plan now**.
>
> Authoritative state lives in `rule.md`, `docs/JOURNEY.md`, `docs/NEXT_STEPS.md`,
> `docs/TERMINAL_PLAN.md`. This document is an **addendum** - it does not change
> those files and does not relax any invariant. Phases 0-19 are COMPLETE; these
> are *new* owner-requested directions.
>
> **Standing constraints (from `rule.md` 1/5, reinforced 2026-08-31):**
> - **CLEAN-ROOM LAW:** replicate *features*, never *copy* code. Spck
>   (`io.spck`), Coding C (`com.kvassyu.coding2.c`) and Pydroid 3
>   (`com.itsaky.pydroid3`) are **closed source** - match visible behavior from
>   mockups / public docs / this research only; never decompile. Termux is
>   **GPL** - read public specs, re-implement via the official Termux *builder*
>   under the CodeC prefix, never paste GPL source and never mix official
>   `com.termux` `.deb`s (`-I` is forbidden).
> - **phone-first** is the product law for this round.
> - No PR / merge without the owner's explicit command.

---

## 0. Architecture snapshot (what we are building on)

CodeC is a **Jetpack Compose** Android IDE (`com.codeci.ide`). Relevant facts the
research is grounded in (verified in `app/src/main/...` and `codec-packages/`):

- **Editor** - `EditorScreen.kt`: a single `BasicTextField` wrapped in
  `Row(Modifier.verticalScroll(scrollState).horizontalScroll(hScrollState))`
  with `visualTransformation = SyntaxVisualTransformation(...)`. A line-number
  gutter is rebuilt every keystroke as `(1..lineCount).joinToString("\n")`.
  `tabViews` is recomputed on `remember(openTabs, activeTabPath, codeText, isDirty)`;
  `completionItems` on `remember(codeText, language)`. Pinch-to-zoom font is
  implemented via `pointerInput`. An `EditorKeysRow` is docked **inside the
  layout** above the status bar - *not* above the soft keyboard.
- **Run / output** - `EditorViewModel.runActiveFile` -> `ExecutionRunner`
  (piped, `sendInput(line)`) or `InteractiveRunSession` (real PTY, `sendLine`).
  `OutputPanelView` renders text lines in a `LazyColumn`; an `OutputInputRow`
  provides the **separate input box** the owner wants removed. Collapsed strip +
  draggable splitter exist. A full `Term` tab (VT/ANSI + PTY) is the power-user
  escape hatch.
- **Compiler today** - built-in **TCC** (Tiny C Compiler) embedded in the APK as
  a static musl toolchain for `arm64-v8a`/`x86_64`; plus a "Bundled Clang"
  download and a Termux-Clang bridge. `EmbeddedCompiler`/`TermuxCompiler` hold
  the TCC/Clang paths; `Auto` engine tries TCC first.
- **Userland** - `codec-packages/` is a **GPL overlay on `termux-packages`**.
  Packages are rebuilt for `TERMUX_PREFIX=/data/data/com.codeci.ide/files/usr`
  with `TERMUX_APP_PACKAGE=com.codeci.ide`, re-signed with CodeC keys, served
  from `https://pabi277.github.io/CodeC/dev`. Curated roots live in
  `CODEC_REPOSITORY_PACKAGES` (`properties.codec.sh`): Round 3 already added
  `python` + `python-pip`, and deliberately **drops `python-tkinter`** (X11
  closure - no X11 use in CodeC). CI builds the bootstrap (`bootstrap-userland.yml`,
  ~360-min timeout) and the repository (`package-repository.yml`).

## 0.1 Planned architectural shift (owner decision, 2026-09-01)

> "remove tcc and use gcc like python and extend it's scope with other languages
> as per need - make the plan future proof"
>
> "take ideas 3, 4, 5 now (the feasible / low-cost ones)"

This reshapes the plan and adds **Phase D** (compiler) and **Phase E** (the
feasible picks from groups 3-5). In short:
- **TCC is retired.** C/C++ compile with a **userland `gcc`/`g++` toolchain**
  delivered like Python today (a package in the CodeC repo, installed on demand,
  offline after first install) - not an APK-embedded musl toolchain.
- **Hard reality from research (must read before building):** in the
  Termux/CodeC userland the `gcc` command is a **compatibility symlink to
  Clang/LLVM** - Termux deprecated *real* GNU GCC because GCC does not support
  Android/Bionic well (NDK dropped GCC; Bionic's linker/crtbegin/crtend and
  in-libc threading differ) [10][11]. So "use gcc" resolves to exposing
  `gcc`/`g++` commands that run the LLVM/Clang suite (today's "Bundled Clang"
  engine) as a clean userland package. A *genuine* GNU GCC is only available via
  a third-party repo and is fragile; see the **D.2.1 gate**.
- **The run model becomes language-agnostic** (`LanguageRunProfile` + registry)
  so adding languages later is configuration, not new code ("as per need").
- **Feasible polish from groups 3-5** (auto-install on import, hardware
  shortcuts, project-zip share, per-language formatters, background-run
  notification, tablet two-pane, test-runner UI) is pulled into **Phase E** now.
- **CI:** `scripts/build-tcc.sh` is dropped; the C/C++ toolchain comes from the
  package-repository build (Phase C). The "fully static musl executables" property
  is intentionally given up (programs run dynamically under `$PREFIX`, like
  Termux/Python).

---

## Phase A - Editor touch smoothness & keyboard-anchored shortcuts

### A.0 Why (owner signal)
> "the editor smoothness because it not good at touch, feels like stuck, the
> shortcuts key are not above the keyboard etc"

Two distinct problems bundled here:
1. **Jank / "stuck" feeling** while typing, scrolling, and selecting on a phone.
2. **Shortcut keys are not above the keyboard** - when the soft IME opens, there
   is no quick-key strip (Tab, `{}`, `()`, `;`, arrows, Ctrl, snippets) floating
   *above* the keyboard the way Termux's extra-keys do.

### A.1 Current-state hypothesis (where the "stuck" comes from)
Grounded in `EditorScreen.kt`:
- The whole buffer is one `BasicTextField` whose `visualTransformation` rebuilds
  a full highlighted `AnnotatedString` on every recomposition; `decorations`
  depends on `currentLineRange`/`bracketRanges`/`diagnostics`/`findState`, all of
  which change as you type -> **O(n) per keystroke** for large files.
- Gutter string `(1..lineCount).joinToString("\n")` is rebuilt per keystroke.
- `tabViews` and `completionItems` recompute on `remember(codeText,...)`.
- `BasicTextField` is wrapped in **both** `verticalScroll` and `horizontalScroll`
  modifiers; Compose's `BasicTextField` already manages its own scrolling - the
  double scroll fights, and caret-follow uses manual `scrollState` offsets
  (`getCursorRect` + manual scroll) instead of the built-in `bringIntoView`.
- **No `imePadding()` / IME-inset handling** on the editor -> the IME can cover
  the caret or cause relayout jumps; and the `EditorKeysRow` sits *in layout*, so
  it disappears under / gets pushed by the keyboard instead of riding above it.
- Compose **debug builds are notoriously slow** [3]; the "stuck" feel may partly
  be debug-APK artifact. Rule: benchmark on a **release** APK, not debug.

### A.2 Research spikes (do these before/while implementing)
- **A.2.1 Scroll model.** Spike: let `BasicTextField` own scrolling via its
  `scrollState` param (or a `TextFieldState` + `BasicTextField(TextFieldState)`
  from Compose Foundation 1.7+ [1]) instead of wrapping in `verticalScroll`/
  `horizontalScroll`. Measure caret-follow latency and frame time. *Open:* does
  word-wrap + horizontal scroll still compose cleanly with the line-number gutter?
- **A.2.2 Highlight cost.** Spike: move `SyntaxVisualTransformation` off the
  per-keystroke path - debounce (e.g. 60-120 ms) or compute on a background
  dispatcher and publish an immutable `AnnotatedString` snapshot. Compare against
  incremental/token-diff highlighting. *Open:* can we keep live bracket-match +
  current-line highlight without re-highlighting the whole file?
- **A.2.3 Recomposition scope.** Audit `remember` keys in `EditorScreen`: gutter
  (cache on `lineCount`), `tabViews` (drop `codeText`/`isDirty` from the key where
  avoidable), `completionItems` (already gated by `completionDismissed`). Use
  `derivedStateOf` for derived reads. Confirm no parent recomposition on each
  keystroke via Layout Inspector / Recomposition counts.
- **A.2.4 IME-anchored keys (the big UX fix).** Research Termux's proven pattern:
  with `android:windowSoftInputMode="adjustResize"` the terminal view + extra-keys
  are siblings; the IME pushes the whole layout up so the keys ride **directly
  above** the keyboard [4]. In Compose, two viable routes:
  - (a) Render the keys strip as a `Column` whose bottom is pinned to
    `WindowInsets.ime` (use `Modifier.imePadding()` on a bottom-pinned container;
    `WindowCompat.setDecorFitsSystemWindows(window, false)` + read
    `WindowInsets.ime.getBottom(density)` for an exact offset [4]); reuse the
    existing `TerminalExtraKeys` component (2-row configurable grid) for the
    editor, with editor-specific keys (Tab, `{}`, `()`, `;`, `<>`, `<-/-`, Undo/Redo,
    language snippets) instead of ESC/CTRL/TAB-PTY.
  - (b) Floating `Dialog`/`Popup` pinned to IME bottom. *Open:* which survives
    orientation change + predictive-text + keyboard height changes most smoothly?
    Reuse vs. fork `TerminalExtraKeys`?
- **A.2.5 Touch feel.** Research Compose input-latency best practices: baseline
  profiles (`baselineProfile` rule, reported +30% scroll smoothness [3]), avoid
  `Indication`/`rememberUpdatedInstance` (deprecated, high cost [1]), keep heavy
  work off the main thread (`withContext(Dispatchers.Default)` for highlight/parse).
- **A.2.6 Selection / caret.** Confirm long-press selection + word-boundary +
  copy/paste contextual menu feel good on small screens; evaluate a larger
  drag-handle hit area and a magnifier (Compose `SelectionContainer` / custom).

### A.3 Proposed approach (phone-first)
1. **Decouple scroll from text** (A.2.1) - biggest single win for "stuck".
2. **Debounced/off-thread syntax highlight** (A.2.2) + **narrow recomposition**
   (A.2.3) + **baseline profile** (A.2.5).
3. **Editor extra-keys strip above the IME** (A.2.4): a configurable,
   phone-tuned key grid (Tab, brackets, `;`, `<>`, arrows, Undo/Redo, snippets)
   that appears *only when the soft keyboard is up*, pinned to the IME top edge.
   Make it user-editable (mirror Termux's macro format already parsed by
   `parseExtraKeysMacros`).
4. **IME insets** so the caret/last line never hide behind the keyboard.
5. Keep the existing `⋮ -> show/hide keys row` toggle as a fallback.

### A.4 Acceptance (owner device transcript required)
- Typing in a 2-3k-line file feels as smooth as a stock Notes app (no dropped
  frames during continuous input, measured on a mid-range phone, **release APK**).
- Scroll + pinch-zoom stay at ~60 fps; caret always visible above the keyboard.
- Editor shortcut strip is visible **above** the soft keyboard and sends keys into
  the buffer; user can edit the strip's keys/snippets.
- No regression to: diagnostics tap, autocomplete popup, find/replace, split
  output, autosave.

### A.5 New ideas surfaced (see §4)
- Haptic micro-feedback on keycap press; blinking caret tuned for OLED.
- "Editing toolbar" that adapts to language (C shows `{}();`, Python shows `:`
  + indentation + `def`/`print` snippets).
- Predictive-back + gesture-nav friendly (no IME jumps).

---

## Phase D - Compiler engine redesign: drop TCC, adopt userland gcc/g++ + generic multi-language run model

### D.0 Why (owner decision, 2026-09-01)
> "remove tcc and use gcc like python and extend it's scope with other languages
> as per need - make the plan future proof"

Translation into architecture:
- TCC (APK-embedded musl toolchain) is retired. C/C++ compile with a **userland
  `gcc`/`g++` toolchain** delivered the same way Python is today (a package in the
  CodeC repo, installed on demand, offline after first install).
- The run model becomes **language-agnostic** so adding languages later is
  configuration, not new code ("as per need", future-proof).

### D.1 What changes
- **Retire TCC entirely:** delete `app/src/main/assets/tcc/*`, `scripts/build-tcc.sh`,
  the TCC-first "Auto" engine branch, and the `EmbeddedCompiler` TCC path. Keep
  "Bundled Clang"/Termux-Clang only as an optional alternative compiler if validated.
- **gcc/g++ as a userland package** (see Phase C, core toolchains). C compiles via
  `gcc $SRC -o $OUT` (Termux-style: dynamic against Bionic, run under `$PREFIX`
  like Python). First RUN of a C file with a missing toolchain auto-installs `gcc`
  (pkg) then runs - reuse the Packages-tab 1-tap install logic.
- **Generic `LanguageRunProfile`** - a pure data class describing how any language
  compiles and runs. Adding a new language becomes adding one entry to a registry,
  not new Kotlin code.

### D.2 `LanguageRunProfile` design

```kotlin
/**
 * Describes how a single language is compiled + executed.
 * All paths are $PREFIX-relative; $SRC and $OUT are template tokens.
 * Android-free; host-unit-testable.
 */
data class LanguageRunProfile(
    /** Human name shown in the UI ("C", "C++", "Python", "JavaScript", …). */
    val displayName: String,
    /** File extensions this profile owns (lower-case, no dot). */
    val extensions: List<String>,
    /**
     * Package to auto-install if missing (null = interpreter already in PATH
     * or no install needed). Maps to `pkg install -y <pkgName>`.
     */
    val requiredPackage: String?,
    /**
     * Build command template, or null for interpreted languages.
     * Tokens: $SRC (abs path to source file), $OUT (abs path to output binary).
     * Example: "gcc \$SRC -o \$OUT -lm"
     */
    val buildTemplate: String?,
    /**
     * Run command template. Tokens: $OUT (compiled binary or source for
     * interpreters), $SRC.
     * Example (C): "./\$OUT"
     * Example (Python): "python3 \$SRC"
     * Example (JS): "node \$SRC"
     */
    val runTemplate: String,
    /** True if the program is likely interactive (PTY preferred over pipe). */
    val interactive: Boolean = false,
    /** Formatter command template, or null. Token: $SRC. */
    val formatterTemplate: String? = null,
)

/** The global registry. Looked up by file extension at run time. */
object LanguageRegistry {
    val profiles: List<LanguageRunProfile> = listOf(
        LanguageRunProfile(
            displayName  = "C",
            extensions   = listOf("c"),
            requiredPackage = "gcc",
            buildTemplate   = "gcc \$SRC -o \$OUT -lm",
            runTemplate     = "./\$OUT",
            interactive     = true,
            formatterTemplate = "clang-format -i \$SRC",
        ),
        LanguageRunProfile(
            displayName  = "C++",
            extensions   = listOf("cpp", "cc", "cxx"),
            requiredPackage = "gcc",        // g++ is in the same package
            buildTemplate   = "g++ \$SRC -o \$OUT -lm",
            runTemplate     = "./\$OUT",
            interactive     = true,
            formatterTemplate = "clang-format -i \$SRC",
        ),
        LanguageRunProfile(
            displayName  = "Python",
            extensions   = listOf("py"),
            requiredPackage = "python",
            buildTemplate   = null,
            runTemplate     = "python3 \$SRC",
            interactive     = true,
            formatterTemplate = "black \$SRC",
        ),
        LanguageRunProfile(
            displayName  = "JavaScript",
            extensions   = listOf("js", "mjs"),
            requiredPackage = "nodejs",
            buildTemplate   = null,
            runTemplate     = "node \$SRC",
            interactive     = false,
        ),
        LanguageRunProfile(
            displayName  = "TypeScript",
            extensions   = listOf("ts"),
            requiredPackage = "nodejs",     // ts-node via npm
            buildTemplate   = null,
            runTemplate     = "npx ts-node \$SRC",
            interactive     = false,
        ),
        LanguageRunProfile(
            displayName  = "Go",
            extensions   = listOf("go"),
            requiredPackage = "golang",
            buildTemplate   = null,
            runTemplate     = "go run \$SRC",
            interactive     = false,
            formatterTemplate = "gofmt -w \$SRC",
        ),
        LanguageRunProfile(
            displayName  = "Rust",
            extensions   = listOf("rs"),
            requiredPackage = "rust",
            buildTemplate   = "rustc \$SRC -o \$OUT",
            runTemplate     = "./\$OUT",
            interactive     = false,
            formatterTemplate = "rustfmt \$SRC",
        ),
        LanguageRunProfile(
            displayName  = "PHP",
            extensions   = listOf("php"),
            requiredPackage = "php",
            buildTemplate   = null,
            runTemplate     = "php \$SRC",
            interactive     = false,
        ),
        LanguageRunProfile(
            displayName  = "Ruby",
            extensions   = listOf("rb"),
            requiredPackage = "ruby",
            buildTemplate   = null,
            runTemplate     = "ruby \$SRC",
            interactive     = false,
        ),
        LanguageRunProfile(
            displayName  = "Lua",
            extensions   = listOf("lua"),
            requiredPackage = "lua54",
            buildTemplate   = null,
            runTemplate     = "lua \$SRC",
            interactive     = false,
        ),
        LanguageRunProfile(
            displayName  = "Shell",
            extensions   = listOf("sh", "bash"),
            requiredPackage = null,         // bash is in the bootstrap
            buildTemplate   = null,
            runTemplate     = "bash \$SRC",
            interactive     = true,
        ),
        LanguageRunProfile(
            displayName  = "HTML",
            extensions   = listOf("html", "htm"),
            requiredPackage = null,
            buildTemplate   = null,
            runTemplate     = "__WEB_PREVIEW__",   // special token -> WebPreviewScreen
            interactive     = false,
        ),
    )

    fun forExtension(ext: String): LanguageRunProfile? =
        profiles.firstOrNull { ext.lowercase() in it.extensions }

    fun forFile(path: String): LanguageRunProfile? =
        forExtension(path.substringAfterLast('.', ""))
}
```

Key points:
- **`__WEB_PREVIEW__`** is a sentinel token that `EditorViewModel.runActiveFile`
  intercepts to open the existing `WebPreviewScreen` (no behavioral change for HTML).
- **`requiredPackage`** drives the auto-install gate: before the first build,
  `EditorViewModel` checks `$PREFIX/bin/<tool>` exists; if not, it prompts
  "Install `gcc` to run C files?" and calls the existing pkg install flow before
  continuing. This is identical to how Python installation was handled in Phase 12.
- **`formatterTemplate`** is the hook for Phase E's per-language formatter
  (currently only activated from the `⋮ → Format` menu).
- The registry is a `val` list - adding a new language is a one-line entry, no
  new classes, no new branches in `runActiveFile`.

### D.2.1 The "real GNU GCC" gate

Research finding that must be recorded here:

> **In the Termux/CodeC userland, `gcc` is NOT real GNU GCC.** Termux's
> `gcc` package is a compatibility shim: a symlink (or wrapper script) pointing
> to `clang`. This is intentional and documented - NDK/Bionic dropped GCC support
> in NDK r18 (2018), and Termux followed. A genuine GNU GCC for Android/Bionic
> exists only in third-party overlays (e.g. `tur-repo`) and is fragile because:
> - GCC upstream does not support Bionic's `pthread`/`__cxa_thread_atexit_impl`
>   extensions natively.
> - Android's linker (lld via NDK) is not gcc-ld compatible.
> - Binary stability across Android versions is poor.
>
> **Decision:** `gcc` in the CodeC package registry means "the `gcc`-compatible
> Clang wrapper", same as in Termux. Users get the same `gcc foo.c -o foo`
> command-line UX they expect; under the hood it is Clang. This is correct,
> battle-tested, and matches what every Termux user already experiences.
> Real GNU GCC is explicitly out of scope.

The package recipe to add to `properties.codec.sh / CODEC_REPOSITORY_PACKAGES`:
```
gcc           # clang-based gcc/g++ wrapper (Termux recipe name: "gcc")
clang         # the actual LLVM/Clang toolchain it wraps
```

### D.3 Migration path: TCC → gcc (zero-downtime)

The migration must not break existing users who have files open. Phased:

1. **Phase D-1:** Add `gcc` + `clang` to `CODEC_REPOSITORY_PACKAGES` and rebuild
   the package repo (CI `package-repository.yml`). No app code change yet.
2. **Phase D-2:** Implement `LanguageRegistry` + `LanguageRunProfile`; wire
   `EditorViewModel.runActiveFile` through the registry instead of the current
   TCC-first switch. The TCC path stays as a fallback under a feature flag
   (`SettingsManager.useLegacyTcc`).
3. **Phase D-3:** Device acceptance: owner runs C file → auto-install prompt →
   `gcc` compiles → `./a.out` runs. If OK, flip the default to registry; TCC
   fallback still available in Settings for one release.
4. **Phase D-4:** Remove `EmbeddedCompiler` TCC code, `assets/tcc/`, the TCC
   native lib from `jniLibs`, and the `useLegacyTcc` flag. APK shrinks by ~3 MB.

### D.4 Acceptance
- `gcc main.c -o main && ./main` in the Output Panel produces the same output as
  the old TCC path on a fresh device (auto-install prompt → tap Install → runs).
- `g++ hello.cpp -o hello && ./hello` works (Phase D proves C++ parity).
- Adding a new language to `LanguageRegistry` and pressing RUN on a file of that
  extension shows the auto-install prompt and runs correctly - no Kotlin changes
  outside the registry entry.
- APK size drops after Phase D-4 (TCC assets removed).
- No regression: Python, HTML preview, shell scripts, terminal, git.

---

## Phase C - Package toolchain expansion (gcc/g++ + language packages in CI)

> This is the **CI / package-repo side** of Phase D. It is a separate phase
> because it touches `codec-packages/` and CI only, no app Kotlin code.

### C.1 Packages to add to `CODEC_REPOSITORY_PACKAGES`

| Package name (Termux recipe) | Purpose | Notes |
|---|---|---|
| `gcc` | `gcc`/`g++` CLI wrappers → Clang | Termux recipe: symlinks to clang |
| `clang` | LLVM/Clang toolchain | Already available as "Bundled Clang"; move to repo |
| `nodejs` | JavaScript / TypeScript runtime | ~15 MB |
| `golang` | Go compiler + stdlib | ~80 MB compressed; optional/on-demand |
| `rust` | Rust compiler + Cargo | ~200 MB; on-demand only |
| `php` | PHP CLI | ~10 MB |
| `ruby` | Ruby runtime | ~20 MB |
| `lua54` | Lua 5.4 | ~2 MB |
| `black` | Python code formatter | pip-installable; no Termux recipe needed |
| `clang-format` | C/C++ formatter | Sub-package of `clang` |
| `gofmt` | Go formatter | Bundled with `golang` |
| `rustfmt` | Rust formatter | Bundled with `rust` |

> **Practical note on Rust/Go:** these are large. The package is built in CI
> and served from the repo, but it is **never** auto-installed. The user sees
> "Install rust (~200 MB)?" and must confirm. This mirrors how Termux handles
> heavy compilers.

### C.2 CI impact
- `package-repository.yml` already builds ~25+ roots. Adding `gcc`, `clang`,
  `nodejs`, `php`, `ruby`, `lua54` adds ~30-60 min to CI (these are moderate
  recipes; clang is the heavy one at ~120 min). Go and Rust are opt-in: guarded
  by a separate `[repo-build-heavy]` commit tag so they don't run on every push.
- `build-tcc.sh` is **deleted** in Phase D-4 (the TCC build is no longer needed).

### C.3 Acceptance
- `pkg install gcc` on device installs `gcc` + `clang` from the CodeC repo (no
  official `com.termux` involvement).
- `gcc --version` prints a Clang-based version string (expected; see D.2.1).
- `pkg install nodejs && node --version` works.

---

## Phase E - Feasible polish from groups 3-5

> Owner: "take ideas 3, 4, 5 now (the feasible / low-cost ones)". This phase
> pulls the **low-cost, high-value** items from the original research groups
> (smarter runs, adaptive device, reach/polish) and schedules them. Items
> requiring X11, desktop-window frameworks, or deep NDK work stay deferred.

### E.0 Item classification (feasible vs. deferred)

| Idea | Group | Feasible now? | Reason |
|---|---|---|---|
| Auto-install pkg on first RUN | 3 | ✅ | Already designed in D.2 |
| Per-language formatter (Format menu) | 3 | ✅ | `formatterTemplate` in registry |
| Background-run notification | 3 | ✅ | `POST_NOTIFICATIONS` already wired (Phase 4.8) |
| Hardware keyboard shortcuts | 3 | ✅ | `KeyEvent` dispatch in Compose |
| Project ZIP share (Export + Share intent) | 4 | ✅ | `ProjectTransfer` already exists |
| Tablet two-pane layout | 4 | ✅ | Compose `WindowSizeClass` |
| Test-runner UI (output tab for test runs) | 4 | ✅ | Reuse `OutputPanelView` |
| "Open with CodeC" intent filter | 4 | ✅ | `AndroidManifest` intent + `FileManager` import |
| Adaptive editor theme (system dark/light) | 4 | ✅ | `ThemeManager` + `isSystemInDarkTheme()` |
| X11 / SDL / Qt GUI packages | 5 | ❌ | No X11 server; explicit policy exclusion |
| Kivy / PyQt Android binding | 5 | ❌ | Requires X11 or Wayland; rabbit hole |
| Root-based acceleration | 5 | ❌ | Out of scope by policy |
| Full Termux catalog mirror | 5 | ❌ | Cardinality; not before Phase C settles |

### E.1 Auto-install on first RUN (covered by Phase D.2)

Integrated into `EditorViewModel`: before running, check `$PREFIX/bin/<tool>`.
If missing, show a bottom sheet: "This file needs **gcc** — install now? (offline
after first install)". Tapping INSTALL calls `pkg install -y <pkgName>` in a
coroutine, streams output to the Output Panel, then runs on success. Cancelling
skips the run. Reuses the Phase 10 pkg-install flow; no new infrastructure.

### E.2 Per-language formatter (Format menu item)

`formatterTemplate` in `LanguageRunProfile` (D.2). The `⋮` overflow menu in
the editor gains a **Format** item (visible only when the active language has
a `formatterTemplate`). Tapping it runs the formatter command via `ExecutionRunner`
(build-only mode, no run phase), then reloads the file into the editor buffer.
The formatter must be installed (same auto-install gate as the compiler). This
mirrors Spck's format button (public behavior, clean-room).

### E.3 Background-run notification

Long-running programs (server presets, Go/Rust builds) disappear when the user
switches to another app. A foreground-service notification ("CodeC: running
`server.py`…") with a **Stop** action keeps the run alive and lets the user
return. Implementation:
- Extend `CompilerService` (already a bound service) to post a foreground
  notification when the run duration exceeds 5 seconds.
- `POST_NOTIFICATIONS` permission is already declared + runtime-requested
  (Phase 4.8).
- The existing `codec-notify` bridge is for *terminal scripts*; this is a
  separate SDK notification from `CompilerService`.
- **Stop** action calls `ExecutionRunner.cancel()` (already exists).

### E.4 Hardware keyboard shortcuts

Many tablet + phone-with-keyboard users expect keyboard shortcuts. The editor
already handles some via `EditorKeySet`. Expand:

| Shortcut | Action |
|---|---|
| Ctrl+S | Save file (already works) |
| Ctrl+Z / Ctrl+Shift+Z | Undo / Redo |
| Ctrl+F | Open Find/Replace |
| Ctrl+R | Run active file (▶) |
| Ctrl+/ | Toggle line comment |
| Ctrl+D | Duplicate line |
| Ctrl+W | Close active tab |
| Ctrl+Tab | Next tab |
| Ctrl+Shift+Tab | Previous tab |
| Ctrl+P | Open file quick-picker (future) |
| F5 | Run (alias for Ctrl+R) |

Implementation: `EditorScreen` already receives `KeyEvent` from `BasicTextField`.
Add a `handleHardwareKey(event: KeyEvent): Boolean` function in `EditorViewModel`
and dispatch in `onKeyEvent` modifier. The extra-keys strip (Phase A) already maps
virtual key presses to editor actions - hardware shortcuts reuse the same action
functions.

### E.5 Project ZIP share

The owner can already **Export ZIP** (Phase 8). Add a **Share** button next to
Export that calls the existing `ProjectTransfer.exportZip()` then fires an Android
`ACTION_SEND` intent with the ZIP's `FileProvider` URI. This makes it trivial to
send a project to Telegram, Gmail, Drive, etc. Two-line change on top of existing
`ProjectTransfer`.

### E.6 Tablet two-pane layout

Use Compose `WindowSizeClass` (already a dependency):
- **Compact width (phone):** current single-column layout (no change).
- **Medium/Expanded width (tablet, foldable):** split horizontally - file tree
  drawer becomes a persistent left pane (280 dp); editor fills the right; Output
  Panel collapses into a bottom drawer. The Terminal tab gets its own right pane
  when split.

Implementation uses `calculateWindowSizeClass()` in `MainActivity` and passes a
`WindowSizeClass` into `EditorScreen`/`ProjectsHub` via the nav graph. No new
ViewModels needed.

### E.7 Test-runner UI

For Python (`pytest`/`unittest`) and Go (`go test`) files, a **Test** button
appears in the editor toolbar (alongside ▶ RUN) when the file matches
`*_test.py` / `*_test.go` / `test_*.py`. Tapping it runs the test command
(`pytest $FILE` or `go test ./...`) and streams output to a dedicated **Tests**
tab in `OutputPanelView`, with color-coded PASS (green) / FAIL (red) / ERROR
lines parsed by `OutputLineParser`. This reuses the entire run pipeline with a
different `LanguageRunProfile` variant (no new infra).

### E.8 "Open with CodeC" intent filter

Declare in `AndroidManifest.xml`:
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/*" />
</intent-filter>
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="application/zip" />
</intent-filter>
```
- `text/*` → open the file in the editor (copy to a temp project, same as
  current Import File flow).
- `application/zip` → trigger the existing Import ZIP flow.

`MainActivity.onCreate` already handles `ACTION_VIEW` for some MIME types;
extend the `when (intent.action)` branch.

### E.9 Adaptive theme (system dark/light auto-sync)

`ThemeManager` already stores a user theme choice. Add an **"Auto (follow system)"**
option: when selected, `ThemeManager.current` reads `isSystemInDarkTheme()` from
Compose and picks `DarkTheme` / `LightTheme` accordingly. The theme switches live
when the user pulls the system quick-settings shade. One-line change in the theme
resolution function.

### E.10 Phase E acceptance
- Auto-install prompt appears when running a C file on a device without `gcc`;
  program runs after install.
- Format menu item visible for C/C++/Python/Go; formats in-place without
  breaking the editor state.
- Background-run notification appears for runs >5 s; Stop action kills the run.
- Ctrl+R in the editor triggers RUN (confirmed on a BT keyboard / tablet).
- Share button on project overflow sends a ZIP via the system share sheet.
- On a tablet (≥600 dp width), file tree is a persistent left pane.
- "Open with CodeC" appears in the Android share sheet for `.py`, `.c`, `.html`,
  `.zip` files from Files / Downloads.
- Auto theme follows system dark/light mode live.

---

## Phase B - Interactive run UX: remove the input box, use PTY input inline

### B.0 Why (owner signal)
> "remove the input box [from the Output Panel] - when program asks for input,
> user types directly in the output area"

The current Output Panel has a separate `OutputInputRow` text field below the
output. The owner wants the "C4droid" / "Pydroid" feel: the output scrolls and
the cursor is at the bottom; user types directly there (like a terminal).

### B.1 Current state
`InteractiveRunSession` already uses a **real PTY** for interactive programs
(`interactive = true` in the run profile; `scanf`/`gets` programs). The PTY
handles echo and line discipline. The `OutputPanelView` renders PTY output lines
in a `LazyColumn`. The `OutputInputRow` is a separate `TextField` below the
column that calls `session.sendLine(text)`.

### B.2 Proposed approach
- **Remove `OutputInputRow`.** Instead, when `interactive = true` and the
  program is running, make the last line of the `LazyColumn` an **inline editable
  row** (a lightweight `BasicTextField` with no box/border, styled as plain
  terminal text). The user types at the bottom of the output; pressing Enter
  sends the line to the PTY and the inline field clears.
- The IME-anchored extra-keys strip (Phase A) shows `↵ Enter`, `Ctrl+C`, `Tab`
  above the keyboard when the Output Panel is in input mode.
- **No change for non-interactive runs** (the inline field is hidden; the panel
  is read-only).
- For PTY-mode programs the existing `InteractiveRunSession.sendLine()` call is
  reused unchanged; only the UI wrapper changes.

### B.3 Acceptance
- `scanf("Enter a number: ")` prompt appears inline in the output; user types a
  number and presses Enter; program continues and prints the result - all in
  one scrollable area.
- No separate input row visible at the bottom of the Output Panel.
- Ctrl+C (from the extra-keys strip) sends `SIGINT` to the process.

---

## 4. New ideas surfaced during research (unscheduled backlog)

These surfaced during the research pass. None are spec'd or scheduled; record them
here for the next planning cycle.

| # | Idea | Origin | Effort est. |
|---|---|---|---|
| 4.1 | **Haptic feedback on editor key presses** (light vibrate on bracket completion, error pulse) | Phase A research | XS |
| 4.2 | **Language-adaptive keycap row** (C: `{}();`, Python: `:` + `def`/`print`, Go: `:=`) | Phase A.5 | S |
| 4.3 | **OLED-tuned caret** (slightly slower blink rate, pure-black background on dark theme) | Phase A.5 | XS |
| 4.4 | **Predictive-back support** (intercept gesture-back from editor without losing unsaved edits) | Phase A | S |
| 4.5 | **Incremental syntax highlighting** (token-diff from last snapshot, O(changed lines) not O(file)) | Phase A.2.2 | M |
| 4.6 | **LSP-lite autocomplete** (Tree-sitter grammar → scope-based symbols; no language server process) | IDEA_BACKLOG B.3 | M |
| 4.7 | **`go test` / `pytest` inline annotations** (tap failed test → jump to line) | Phase E.7 | S |
| 4.8 | **Project templates for new languages** (Go module, Rust `cargo new`, Node `npm init`) | Phase D registry | S |
| 4.9 | **Offline docs viewer** (bundled `man` pages via `man-db` package; `man printf` in a panel) | Phase C | M |
| 4.10 | **REPL mode** (run Python/Node/Lua in REPL in the Terminal tab, not the Output Panel) | Phase B | S |
| 4.11 | **Run history** (last 10 runs with timestamp, exit code, duration; tap to re-run) | Phase E | S |
| 4.12 | **Spell-check in comments** (highlight misspelt words in `//` / `#` / `/* */` only) | editor | M |
| 4.13 | **Multiple cursors** (Ctrl+click adds a cursor; bulk edit) | editor | L |
| 4.14 | **Code minimap** (right-side scrollbar showing file structure) | editor | M |
| 4.15 | **Per-project `.codec.json` run config** (override the registry template for a project) | Phase D | S |

---

## 5. References

> These are research anchors used while writing this document. They are not
> exhaustive; further research will update them during implementation.

- [1] Compose Foundation 1.7 `TextFieldState` + `BasicTextField` new API —
  https://developer.android.com/jetpack/compose/text/migrate-textfield
- [2] Compose `WindowInsets` / `imePadding` —
  https://developer.android.com/develop/ui/compose/layouts/insets
- [3] Compose performance / baseline profiles —
  https://developer.android.com/topic/performance/baselineprofiles/overview
- [4] Termux extra-keys IME anchoring (public docs / GitHub wiki) —
  https://wiki.termux.com/wiki/Touch_Keyboard
- [5] Termux GCC deprecation note —
  https://wiki.termux.com/wiki/Differences_from_Linux#Shells_and_utilities
- [6] NDK GCC removal (NDK r18) —
  https://android.googlesource.com/platform/ndk/+/master/docs/Roadmap.md
- [7] Compose `WindowSizeClass` tablet layout —
  https://developer.android.com/guide/topics/large-screens/support-different-screen-sizes
- [8] Android foreground services (background execution) —
  https://developer.android.com/guide/components/foreground-services
- [9] `ACTION_SEND` / `ACTION_VIEW` intent filters —
  https://developer.android.com/training/sharing/receive
- [10] Termux packages — gcc package (clang wrapper) —
  https://github.com/termux/termux-packages/tree/master/packages/gcc
- [11] Android Bionic libc vs glibc differences —
  https://android.googlesource.com/platform/bionic/+/master/docs/status.md

---

## 6. Scheduling summary

| Phase | What | Depends on | Est. effort |
|---|---|---|---|
| **A** | Editor smoothness + IME-anchored keys | none | M (1-2 weeks) |
| **B** | Remove input box, inline PTY input | A (IME keys) | S (3-5 days) |
| **C** | Package repo: add gcc/clang/nodejs/etc | none (CI only) | S (CI + device) |
| **D** | Retire TCC, `LanguageRunProfile` registry | C (packages) | M (1-2 weeks) |
| **E** | Polish from groups 3-5 (see E.1-E.9) | D (registry) | M (1-2 weeks) |

> **Recommended order:** C (CI, no app code) → D (wire the registry, retire TCC)
> → A (editor smoothness, IME keys) → B (input UX) → E (polish batch).
> C can run in parallel with A since C touches only `codec-packages/` and CI.

---

*Document created 2026-09-01. Owner-requested research addendum; no code written.
Next action: owner confirms order of phases, then implementation begins on the
current session branch (`arena/01a05c74-codec`).*
