# CodeC Phase 21 — Implementation record (D.1 + D.2)

**Session branch:** `arena/01a064e0-codec` · **Started:** 2026-09-03 (owner: "start phase 21")
· **Base:** `main` @ `3fa71ab` (Phase 20.1 merged)
· **Cost:** `[client-only]` — Kotlin app code, no `[repo-build]`, no bootstrap change

| Part | Status |
|---|---|
| **D.1** `LanguageRunProfile` registry + wire into `EditorViewModel` | ✅ IMPLEMENTED (this commit) |
| **D.2** Auto-install gate | ✅ IMPLEMENTED (this commit) |
| **D.3** Device acceptance (gcc compiles C/C++ end-to-end) | ⏳ **DEVICE PASS REQUIRED** — owner |
| **D.4** Remove TCC entirely (assets, `EmbeddedCompiler`, `build-tcc.sh`) | 🔒 BLOCKED on D.3 |

---

## 1. What was verified before writing any code

- `git status` / `git log`: session branch `arena/01a064e0-codec`, tip
  `3fa71ab` = `main` (Phase 20.1 merged, PR #43). `git ls-remote origin main`
  → `3fa71abe…` — the local tip and remote `main` agree.
- Read `rule.md`, `docs/NEXT_STEPS.md`, `docs/JOURNEY.md` and all five
  `docs/chat-phase21/` docs before editing.
- Read the actual call sites the spec's §8 research prompts point at:
  `EditorViewModel.runActiveFile` (the old `when` on `LanguageType`),
  `TerminalHandoff` (all six command builders), `ExecutionRunner` /
  `InteractiveRunSession`, `ShellBootstrap.prepare`, `ModuleInstaller`,
  `ModuleCatalog`, `SettingsManager`.

## 2. Research notes (answers to the spec's open questions)

The spec's §8 TODOs, resolved against the real code:

1. **Project-config override priority.** Confirmed: `runActiveFile` already
   ran the *active file*, not `project.json`'s `main`, since Phase 12 (a
   device-found fix). The registry keeps that contract — the project's
   `build`/`run` strings stay the fallback for any file the registry does not
   claim (headers, `.txt`, custom multi-file builds), and server/web project
   types still short-circuit *before* the registry.
2. **`TermuxCompiler` needs no registry entry.** It is a separate
   *compiler-engine backend* selected in Settings (`CompilerService`), not a
   per-language run path. The registry is superseded by the `gcc` profile on
   any device with the CodeC userland; the Termux bridge is untouched.
3. **`FileNameUtils.languageForExtension` does not exist.** The extension →
   language mapping lives in `LanguageType` (in
   `MultiLanguageSyntaxHighlighter.kt`) and is used for *syntax highlighting*
   — a different concern with a different extension set (`.h`, `.md`, `.json`,
   `.css` colour fine but are not runnable). They are deliberately **not**
   unified: `LanguageRegistry` answers "how do I run this?",
   `LanguageType` answers "how do I colour this?". `runActiveFile` no longer
   imports `LanguageType` at all.
4. **`ModuleInstaller` does not run `pkg install`.** It is the ZIP/checksum/
   symlink installer for the Phase 10 module archives; the Package Hub's
   `pkg install -y <id>` strings live in `ModuleCatalog` and are executed by
   piping them into the *terminal*. Since the gate must stream into the
   **Output Panel**, D.2 reuses `ExecutionRunner` (the same class the run
   pipeline uses) rather than either of those — spec §6 D3's "do not add a new
   process-launch path" is satisfied.
5. **`ModalBottomSheet` vs `AlertDialog`.** `EditorScreen` already uses
   `AlertDialog` for every other confirm/cancel decision (rename, unsaved
   changes, delete). The gate uses `AlertDialog` for consistency and because
   it needs no drag/sheet-state handling — visually identical two-button
   choice, one less API surface. (Deviation from the mock in PART_21_2 §2.3;
   content and buttons are exactly as specified.)

## 3. What was implemented

### 3.1 `ui/services/LanguageRegistry.kt` (new, pure Kotlin, zero Android imports)

- `LanguageRunProfile` — the data class from the spec, plus two additions:
  - **`probeBinary`** — the binary that proves the package is installed.
    Necessary because the package name and the binary name differ for half
    the entries (`python`→`python3`, `nodejs`→`node`, `lua54`→`lua`,
    `golang`→`go`, `rust`→`rustc`, and C++ probes `g++` while installing
    `gcc`). The spec put this mapping in a `when` block inside
    `EditorViewModel`; co-locating it with the profile keeps the "add a
    language = one list entry" promise intact.
  - **`isWebPreview`** — a named accessor for the `__WEB_PREVIEW__` sentinel
    so no caller string-compares.
- **12 profiles**: C, C++, Python, JavaScript, TypeScript, Go, Rust, PHP,
  Ruby, Lua, Shell, HTML. Extensions widened slightly against the spec to
  match what CodeC already highlights: `.pyw` (Python) and `.cjs`
  (JavaScript).
- `forExtension` / `forFile` / `expandTemplate` / `outputNameFor` /
  `planFor` / `formatterCommand` / `shellEscape`.
- **`expandTemplate` is a single left-to-right pass**, not two `String.replace`
  calls: a source path that literally contains `$OUT` must not be re-expanded
  (spec §7 `expandTemplate_no_double_substitution`).
- **`shellEscape` moved here** from `TerminalHandoff`, which now delegates to
  it. The registry must not depend on the terminal layer; both paths quote
  identically, and the existing `TerminalHandoffTest` quoting tests still
  cover it.

### 3.2 `ui/services/LanguageRunPlanner.kt` (new, pure Kotlin)

The decision layer between "RUN ▶ tapped" and "a process is launched", as a
sealed `RunDecision`: `WebPreview` | `NeedsInstall` | `Execute` |
`Unsupported`. `decide()` takes the source ref, work dir, output dir and a
`toolInstalled: (String) -> Boolean` probe lambda — so every RUN-gate rule is
host-testable with no Android, no filesystem and no process.

### 3.3 `ui/services/LanguageToolProbe.kt` (new, pure Kotlin)

`isInstalled(prefix, binary)` = does `<prefix>/bin/<binary>` exist. A
file-exists check, not a `pkg` query (spec §6 D1): RUN ▶ must not spawn a
process to decide whether it can spawn a process. `File.exists()` follows
symlinks, so a *dangling* link correctly reads as "not installed" — the honest
answer to "can I run this?". Also holds `InstallPromptState`.

### 3.4 `EditorViewModel` — generic dispatch + the install gate

- `runActiveFile` lost its `LanguageType` `when` block in **both** halves
  (project files and scratch files). Each half now calls
  `LanguageRunPlanner.decide(...)` and switches on the four `RunDecision`
  cases. `import LanguageType` is gone from the file.
- Project files build into `bin/` (`PROJECT_BUILD_DIR`) exactly as before, so
  the source tree stays clean and `BuildArtifactIgnore` keeps working;
  scratch files build next to the source.
- The Python `__pycache__` exclusion (device fix, 2026-08-31) is preserved and
  now keys off the resolved profile.
- **`preferInteractive`** — the PTY is now requested only when the profile
  says the program is likely interactive, instead of unconditionally. The
  piped fallback with the run timeout is unchanged.
- `_installPrompt: StateFlow<InstallPromptState?>` + `dismissInstall()` +
  `confirmInstall(context)`. Install streams `pkg install -y <pkg>` through
  `ExecutionRunner` into the Output Panel with a **900 s** build timeout (the
  default 30 s would kill a toolchain download), then re-enters
  `runActiveFile` on exit 0 so the user does not tap RUN twice. On any other
  exit code the panel shows the failure and stops — no retry loop (spec §5).
- `captureContext(appContext)` is now called at the top of `runActiveFile`,
  because the probe needs `filesDir` to resolve `$PREFIX`. If no context has
  been captured yet the probe returns `true` — a missing context must never
  block a run behind a phantom install prompt.

### 3.5 `TerminalHandoff` — TCC's `cc` removed from every command builder

This is the part that actually retires TCC from the run paths (D.4 will delete
the assets and `EmbeddedCompiler` once the owner's D.3 pass lands):

- `compileParts` — `cc … -o …` → `gcc`/`g++` (chosen by extension) `… -lm`.
- `compileAndRunCommand` — dispatches through the registry first, so a scratch
  `.py`/`.js`/`.rb`/`.lua` file sent to the terminal runs with **its own
  interpreter** instead of being fed to a C compiler (a real pre-existing bug:
  the editor's "Run in terminal" produced `cc script.rb -o a.out` for
  everything non-`.py`). Falls back to the C/C++ line otherwise.
- `projectFileParts` — same registry dispatch; compiled output still lands in
  `<project>/bin`.

`ProjectConfig`'s default `build` string still says `cc main.c -o bin/app`:
that is a *user-editable project template*, `cc` still exists as the app's own
frontend today, and changing it would silently rewrite the meaning of every
existing on-device `project.json`. It moves in D.4 together with the rest of
the TCC removal.

### 3.6 `EditorScreen` — the install prompt

An `AlertDialog` bound to `viewModel.installPrompt`: title
"Install <language>?", body naming the package, an optional download-size line
(only rendered when `installSizeHint != null` — spec §5), **Cancel** →
`dismissInstall()`, **Install** → `confirmInstall(context)`. Five new strings
in `strings.xml` plus four Output-Panel strings for the install phase.

### 3.7 On `useLegacyTcc` (spec §2.4) — deliberately not added

The spec asks for a `SettingsManager.useLegacyTcc` fallback flag. It was
**not** implemented, for a concrete reason: the TCC path in `runActiveFile`
was never a distinct code path — it was just `TerminalHandoff` emitting the
string `cc`, resolved at runtime by the app's `cc` frontend on `PATH`. There is
no branch to gate. The real fallback already exists and is *better*: Settings →
**Compiler Engine** (`COMPILER_BACKEND`) still selects the embedded TCC for
`CompilerService`, and `EmbeddedCompiler` plus `assets/tcc/` are untouched by
this commit — so on a device where `gcc` is unavailable the owner keeps a
working compiler and the D.4 removal stays fully reversible until they say go.
Recorded here so D.4 does not go looking for a flag that never existed.

## 4. Tests (host JUnit4 — CI is the only executor)

New: `LanguageRegistryTest` (21 tests) and `LanguageRunPlannerTest` (12 tests).

Beyond the spec's §7 table, these cover the traps found while implementing:

- `forFile_dotfile_in_a_dotted_directory_is_not_misread` — `/home/u.d/notes`
  has the dot in the *directory*; naive `substringAfterLast('.')` would resolve
  it to a bogus extension. Also `Makefile` / `README` → `null`.
- `expandTemplate_no_double_substitution` — a source path containing `$OUT`.
- `planFor_quotes_paths_with_spaces` — the project/file names real users make.
- `cpp_probes_gpp_but_installs_the_gcc_package` and
  `python_probes_python3_not_python` — the package-vs-binary asymmetry that
  would otherwise prompt "install python" forever on a working device.
- `lua_probes_lua_and_installs_lua54` — matches the name Phase 20.1 actually
  published.
- `tcc_is_not_referenced_by_any_profile` — a standing guard that no profile
  ever compiles through the retired `cc` shim.
- `every_compiled_profile_runs_the_binary_it_builds` /
  `every_profile_with_a_package_names_a_probe_binary` /
  `no_duplicate_extensions_in_registry` — invariants that make "adding a
  language is one list entry" safe for the next agent.
- `probe_against_a_missing_prefix_is_false_not_a_crash`.

Updated: `TerminalHandoffTest` — the seven assertions that pinned the `cc`
command strings now pin the gcc/g++ ones, plus two new tests
(`compile and run dispatches interpreted languages to their interpreter`,
`project file parts route every registry language`).

## 5. Invariants

- No `.` on `PATH`; no `build-package.sh -I`; no `cc`/`bash` shim overwrite;
  no official `com.termux` packages; no bootstrap in the APK; repo metadata
  still signed. None of these are touched — this is a client-only change.
- **TCC link order (`-o` last)** still applies: `EmbeddedCompiler` and
  `CompilerService` are unchanged, and `rule.md` §6 keeps the invariant until
  D.4 strikes it.
- Clean-room: the registry design is CodeC's own (`RESEARCH_NEXT_PHASES.md`
  §D.2). No code from any other IDE was read or copied.
- `LanguageRegistry.kt`, `LanguageRunPlanner.kt`, `LanguageToolProbe.kt` are
  Android-free by construction — an Android import in any of them is a bug.

## 6. Exit state

- CI (`Build APK` = assemble + unit tests + lint) — run id recorded in the
  session report.
- **D.3 device pass required** before D.4: the owner runs the
  `PART_21_3_ACCEPTANCE.md` recipe (a C file and a C++ file end-to-end through
  gcc, plus the install gate on a device without `gcc`).
- Stop at the merge gate — the owner merges to `main`.
