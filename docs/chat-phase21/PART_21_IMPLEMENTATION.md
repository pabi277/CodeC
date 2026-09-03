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

---

## 7. Device round 1 (2026-09-03) — install gate FAILED, two defects fixed

**Owner transcript (RUN ▶ on a `.c` file, install gate accepted):**

```
$ pkg install -y gcc
E: Unable to locate package gcc
pkg: package not found; run 'pkg update' first to refresh the package catalog.
Could not install C
```

then, after the owner ran `pkg update` manually:

```
$ pkg install -y gcc
Package gcc is not available, but is referred to by another package.
However the following packages replace it:
  libllvm
E: Package 'gcc' has no installation candidate
```

Owner's read — *"i think gcc is wrong i am using clang"* — is correct.

### D17 — `requiredPackage` for C/C++ was `gcc`; there is no such package

**Root cause.** I took `requiredPackage = "gcc"` from the Phase 21 spec
(`PART_21_1_REGISTRY.md` §2.2), which was written *before* Phase 20.1 landed.
Phase 20.1's own record says the opposite in three places —
`PART_20_1_TOOLCHAINS.md` §2 ("**⇒ Users run `pkg install clang`**"), the §1
package table, and `prompt.md` ("no `gcc`/`clang` recipe exists at the pinned
ref"). At the pinned upstream ref there is no `packages/gcc` recipe at all:
the compile root is **`libllvm`**, and its **`clang` subpackage** creates the
`gcc`/`g++`/`c++`/`cpp` driver symlinks in `termux_step_post_make_install`.
So `gcc` is a *binary shipped by the clang deb*, never an installable name.
This is precisely the failure `rule.md` §4.1 exists to prevent (trust the
repo, not a doc) — the ground truth was two files away and I did not check it.

**Fix.** C and C++ profiles: `requiredPackage` `gcc` → **`clang`**.
`probeBinary` stays `gcc`/`g++` — those *are* the right binaries to test for,
because the clang deb provides them. Verified against
`codec-packages/properties.codec.sh` `CODEC_REPOSITORY_PACKAGES` and the live
dev-index names recorded in `PART_20_1_TOOLCHAINS.md` build-log entry 8.

New standing guards so this cannot drift again:
- `no_profile_ever_tries_to_install_a_package_named_gcc`
- `every_installable_package_is_one_phase_20_1_actually_published` — every
  installable `requiredPackage` must be in the set actually published
  (`clang`, `lld`, `llvm`, `llvm-tools`, `libllvm`, `libcompiler-rt`,
  `nodejs`, `npm`, `php`, `ruby`, `lua54`, `python`, `python-pip`).
- `c_and_cpp_install_clang_and_probe_the_gcc_symlinks`

### D18 — Go and Rust were guaranteed-to-fail installs

The same audit caught two more landmines: `golang` and `rust` are in the
registry but were **never published** by Phase 20.1 (the langs group is
`nodejs npm php ruby lua54`). Tapping RUN ▶ on a `.go` file would have
reproduced the identical dead end.

**Fix.** New `LanguageRunProfile.inRepository` flag (default `true`), `false`
for Go and Rust, and a new `RunDecision.Unavailable` case. RUN ▶ now says
"Go is not in the CodeC package repository yet" instead of firing an install
that cannot succeed. The profiles stay in the registry so the day those roots
are published it is a one-word change. Test:
`go_and_rust_report_unavailable_instead_of_a_doomed_install`.

### D19 — the gate must refresh the catalog first

The owner's *first* error was a different bug from the second: a device whose
apt lists predated the Phase 20.1 publish cannot install anything new, and the
gate offered no way out — the user had to know to run `pkg update` by hand.

**Fix.** `installCommand` is now `pkg update && pkg install -y <pkg>`.
Idempotent, and negligible next to the ~90 MB download that follows. Test:
`install_command_refreshes_the_catalog_before_installing`.

### Status

D.3 device acceptance is **still open** — this round found gate bugs before
any C code was compiled. The next device pass should re-run the same recipe.

---

## 8. Device round 2 (2026-09-03) — server presets bypassed the gate entirely

**Owner transcript (RUN ▶ on a Flask/`app.py` server project):**

```
$ python3 app.py
/data/user/0/com.codeci.ide/files/usr/bin/bash: line 1: python3: command not found
Server exited with code 127
```

The tell is **"Server exited with code 127"** — this is `startServerRun`, not
`runActiveFile`. A completely different code path from the one D.2 gated.

### D20 — the install gate only covered the active-file run path

**Root cause.** `runActiveFile` short-circuits to `startServerRun` for any
`SERVER_TYPES` project (`python-flask`, `python-fastapi`, `c-microservice`)
*before* the registry is ever consulted, and `startServerRun` executes
`config.build`/`config.run` **verbatim**. The same hole existed in the
`else ->` fallback of my own registry dispatch, which runs a custom
`project.json` build/run pair verbatim too. So D.2's gate only ever protected
single files and registry-claimed project files — exactly the narrow case I
tested against, and I never asked what *else* reaches a shell.

**Fix.** New `LanguageRunPlanner.toolchainForCommands(commands, probe)`:
splits each command on `&&`/`;`/`|`, takes the **leading token of each
segment** (the program actually being invoked), maps it back through
`profileProviding()` to the profile whose `probeBinary` it is, and returns a
`NeedsInstall` when it is missing. Wired into both uncovered paths.

Only the leading token is inspected, deliberately — `./bin/server`,
`mkdir -p bin` and `echo node ruby` must never trigger a prompt.

Because the gate can now fire for a server project, a successful install has
to resume the *server*, not the active file: `pendingServerProject` records
which project asked, `confirmInstall` restarts `startServerRun` for it, and
`dismissInstall` clears it.

### D21 — the `c-microservice` preset still compiled with `cc`

Found while auditing the server presets. `ProjectConfig.defaultFor(
"c-microservice")` emitted `mkdir -p bin && cc server.c -o bin/server`, and
the scaffold's header comment still said "compile with the embedded TCC".
Both now use `gcc` (clang's driver symlink), consistent with §3.5 and with the
new gate, which can then recognise the compiler and offer `clang`.

`ProjectConfig`'s generic default (`cc main.c -o bin/app`) is still deliberately
untouched — see §3.5 for why it moves in D.4 rather than now.

### Tests

Seven new cases in `LanguageRunPlannerTest`: server run line → install python;
silent when present; `c-microservice` build line → clang; `./bin/server` and
`cd x && ls` never prompt; `echo node ruby php` never prompts (argument, not
program); an unpublished toolchain (`go run`) never produces a doomed prompt;
first missing toolchain in a pipeline wins. Plus `ProjectConfigTest` /
`ProjectScaffoldTest` updated to the `gcc` preset.

### Status

D.3 still open. Two device rounds have now found gate bugs *before* any user
code compiled — worth noting for D.4: the run paths are more numerous than the
Phase 21 spec assumed (active file, project file, project config, server
preset, terminal handoff), and TCC removal must account for all five.
