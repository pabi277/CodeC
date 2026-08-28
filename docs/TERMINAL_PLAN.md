# CodeC Terminal — Mini-Termux Plan

> **Status (2026-08-26):** Phase 0–5 complete and device-verified (Phase 3 bootstrapping / Phase 4 CodeCApi / Phase 5 client fixes, web preview, capabilities — all merged). **Phase 6–15 master roadmap is NEW in this file** — see §13 (updated 2026-08-26). New priorities: multi-terminal, terminal UX (cutout/insets + extra-keys + wake lock), projects/files, editor foundation, pkg-install GUI, output panel + Run, Python + language intelligence (ONE repo build), GitHub, mixed-language, CodeCApi tail. All client-only except Phase 12 (Python = one ~1–2h build). See `docs/chat-phase5/README.md` and `docs/PHASE5_ROADMAP.md` for Phase 5 evidence.
> **This-session bugs/fixes:** [docs/chat-phase1/README.md](chat-phase1/README.md).  
> **Goal:** turn CodeC into a self-contained C IDE **with its own
> Termux-style terminal and package manager** — install packages like `pkg install clang`
> inside the app, no root, no Termux dependency.
>
> This document is the full engineering plan. It contains **no code** — it is the
> blueprint for the next major feature.

---

## 1. Vision

CodeC is already a standalone C IDE (built-in TCC compiler, optional Clang module,
Termux fallback). The next step makes it a **complete mobile dev environment**:

- An **in-app terminal emulator** (VT/ANSI) with a real shell.
- A **Termux-style userland** (`bash`, `busybox`, `coreutils`, …) living in the app's
  private storage.
- A **real package manager** (`apt` + `dpkg`) pointed at **our own package repository**,
  so users can run `pkg install clang git python` — exactly like Termux, but inside
  CodeC.

The architecture is **proven**: it is literally how Termux works, and Termux is fully
open source. We are not inventing anything — we are **forking and re-targeting**.

---

## 2. Why we cannot just use Termux's packages

Termux compiles every binary with its prefix path baked in:

```
/data/data/com.termux/files/usr    ← compiled into every binary's rpath/config
```

A different app:
- cannot write into `com.termux`'s directory (Android sandbox), and
- cannot create `/data/data/com.termux/...` under its own package name.

So "point CodeC at Termux's repo and install their `.debs`" is impossible without root.
**Every package we ship must be rebuilt with our own prefix:**

```
/data/data/com.codeci.ide/files/usr
```

This is the single most important constraint in the whole plan. It is also the reason the
plan forks Termux's **build system** (termux-packages) rather than its packages.

---

## 3. How Termux works (the recipe we copy)

| Ingredient | What it is | Android trick |
|---|---|---|
| Userland | bash, busybox, coreutils built with the **Android NDK** (bionic libc) | runs as a normal app process — no root, no chroot |
| Prefix dir | everything under `/data/data/<app>/files/usr` | app-private storage |
| Package manager | `apt` + `dpkg` compiled for Android, own repos | `.deb` unpack + maintainer scripts |
| Executable storage | **targetSdk 28** compatibility mode | keeps `exec()` of downloaded binaries legal on Android 10+ |

**We already have the last row.** CodeC targets SDK 28 (committed) — the same
compatibility mode Termux uses. The remaining three rows are what this plan builds.

---

## 4. Architecture overview

```
┌──────────────────────────── CodeC IDE ────────────────────────────┐
│                                                                   │
│  Terminal UI (Compose)                                            │
│   └─ VT/ANSI escape parser ── PTY (JNI openpty)                   │
│                                   │                               │
│                                   ▼                               │
│  ┌───────────────────── $PREFIX ────────────────────────┐         │
│  │ /data/data/com.codeci.ide/files/usr                  │         │
│  │   bin/   bash busybox apt dpkg clang git …           │         │
│  │   lib/   shared libs + termux-exec.so (LD_PRELOAD)   │         │
│  │   etc/   apt/sources.list termux.properties          │         │
│  │   var/   dpkg database, apt cache                    │         │
│  └──────────────────────────────────────────────────────┘         │
│                                                                   │
│  Package repo (GitHub-hosted)  ◄── apt ──  .debs + Packages/Release│
└───────────────────────────────────────────────────────────────────┘
```

Layers:

1. **App layer (Kotlin/Compose)** — terminal screen, key handling, bootstrap lifecycle.
2. **Terminal layer** — VT parser + PTY + process management.
3. **Userland** — the forked-and-rebuilt Termux packages with our prefix.
4. **Package management** — apt/dpkg + our repository.
5. **Distribution** — bootstrap tarball served from GitHub Releases.

---

## 5. Building blocks

### 5.1 Terminal UI + PTY

- **VT parser:** two options —
  - port **libvterm** (MIT license, used by Neovim; small C library, NDK-buildable) —
    preferred, or
  - write a minimal VT100/xterm-256color parser ourselves (~2–4k lines).
  - ⚠️ Termux's `terminal-view` library is excellent but **GPLv3**; using it would make
    the whole app GPLv3. Acceptable, but we prefer MIT libvterm to keep options open.
- **PTY:** `openpty()` via a tiny JNI shim (or `android.system.Os`), then spawn `bash`
  with the PTY as stdio. Keyboard input → PTY master; PTY output → escape parser →
  terminal renderer.
- **Editor → terminal handoff:** a button that opens the current editor file in the
  terminal (`bash -c "cc file.c && ./a.out"` or `nano file.c`).

### 5.2 Bootstrap userland (the heavy lift)

- **Fork [`termux-packages`](https://github.com/termux/termux-packages)** (GPL-3.0 — we
  keep the license and reuse everything).
- Change **one variable** in the build scripts:
  `TERMUX_PREFIX=/data/data/com.termux/files/usr` →
  `TERMUX_PREFIX=/data/data/com.codeci.ide/files/usr`.
- Build on GitHub Actions (we already have a working CI pipeline):
  - **v1 package set:** `busybox`, `bash`, `coreutils`, `grep`, `sed`, `awk`, `tar`,
    `gzip`, `nano`, `less`, `make`, `file`, `termux-exec`, `apt`, `dpkg`.
  - Every package is a `build.sh` recipe Termux already maintains with Android patches —
    we inherit years of fixes for free.
- Output: a **bootstrap tarball** (~20–40 MB compressed) containing the full `$PREFIX`
  for `arm64-v8a` and `x86_64` (and optionally `armeabi-v7a`).

### 5.3 Package manager

- `apt` + `dpkg` are the most-patched pieces in Termux (no chroot, custom dirs, gpgv,
  maintainer scripts, `dpkg` database in `$PREFIX/var`). Fork their patches, rebuild with
  our prefix.
- `$PREFIX/etc/apt/sources.list` → our repo:
  `deb [trusted=yes] https://github.com/pabi277/... codec main` (signed later).
- Maintainer scripts (`postinst` etc.) run via our `termux-exec` so shebangs work.

### 5.4 termux-exec (shebang fixer)

Android's kernel refuses shebangs pointing outside `/system/bin` for scripts executed
directly. Termux ships a tiny `LD_PRELOAD` library that rewrites shebang lines at exec
time. We build the same (~200 lines of C, fork of termux-exec with our prefix).

### 5.5 Distribution

- **Do not** put the bootstrap in the APK (30–60 MB). First launch downloads it into
  `$PREFIX` — same pattern as the existing module store, just bigger.
- The APK stays small; the terminal + shell + compiler all work after one download.
- Offline users keep the embedded TCC engine (already shipped) — the terminal is
  additive.

---

## 6. Package repository strategy

| Stage | Content | How |
|---|---|---|
| **v1** | ~30–40 curated packages (bash, coreutils, clang, git, nano, python, make…) | rebuild termux-packages recipes with our prefix on CI; publish `.debs` + `Packages`/`Release` metadata to GitHub Releases/Pages via a small repo-builder script |
| **v2** | grow the set on demand (user requests) | same pipeline, more recipes enabled |
| **Never** | the full Termux catalog | we maintain what we ship; that is the honest cost of independence |

`clang` is the long-pole build (1–2 h on CI) — but we already ship TCC, so v1 can launch
with `gcc`-free tooling and add clang right after.

---

## 7. Phased plan & effort

| Phase | Deliverable | Effort (1 dev, part-time) |
|---|---|---|
| **0** ✅ done | targetSdk 28 (executable storage), embedded TCC, module store, Termux bridge | — |
| **1** ✅ done on device (1.3.13) | Terminal UI + `cc`/`./a.out` + scanf prompts. Remaining polish: RUN stdin. See [chat-phase1](chat-phase1/README.md) | 1–3 weeks |
| **2** | Bootstrap overlay and runtime download are released as `userland-v1`; clean-device/runtime-library acceptance remains pending. | done, acceptance pending |
| **3** | M1 complete + M2 build/closure complete: CodeC-only APT repository, source-built starter closure, apt/dpkg/termux-exec bootstrap artifacts with pre-release validation, guarded `pkg`, green CI, and the app installer selecting the Phase 3 release with a `userland-v1` fallback. Remaining: publish bootstrap release, device tests, signing. | ongoing |
| **4** | Polish: storage access (`termux-setup-storage`-equivalent), env vars, themes, security confirmation prompt, package signing | ongoing |
| **Total** | | **6–10 weeks part-time** (phases 1–2 already give a useful in-app terminal) |

### Phase 1 detail (terminal UI) — shipped and device-verified (1.3.13)

1. JNI shim: `openpty()`, `fork`/`exec bash`, `TIOCSWINSZ` (`app/src/main/cpp/pty.c`,
   `PtyNative` / `PtySession`).
2. VT parser: hand-rolled xterm-256color subset in Kotlin (`AnsiParser` +
   `TerminalEmulator`) — colors (SGR 16/256/RGB), cursor, scrollback, alt screen,
   bracketed paste. MIT, fully unit-tested.
3. Termux-style Canvas grid (`Paint.measureText("X")`, `mTopRow`) + `TerminalKeyView`
   IME (`onCreateInputConnection`). Extra-keys row, pinch zoom, long-press copy.
4. `cc` frontend for embedded TCC; the Phase 3 `pkg` frontend is guarded to
   CodeC's own apt/dpkg repository and fails clearly until a CodeC apt/dpkg
   bootstrap is installed (`ShellEnvironment` writes `$PREFIX/bin/{cc,pkg,bash}`).
5. Projects on **executable** `filesDir/CodeC/projects` (emulated storage is `noexec`).
6. Link line: `-nostdlib` + crt + `codec_stdio.o` + `libtcc1.a libc.a` twice + `-o` last.
7. Editor → terminal handoff: toolbar terminal button runs `cc` then `./a.out` in the PTY.

Closed regressions and open polish: [chat-phase1/PROBLEMS.md](chat-phase1/PROBLEMS.md).

### Phase 2 detail (bootstrap)

1. Overlay [codec-packages/](../codec-packages/README.md) (GPL-3.0) on a pinned
   `termux-packages` clone. Override: `TERMUX_APP_PACKAGE=com.codeci.ide`,
   `TERMUX_PREFIX=/data/data/com.codeci.ide/files/usr`.
2. CI **Bootstrap userland**: prefix check on PRs; docker aarch64 build on
   `workflow_dispatch` / `main`. Artifact `bootstrap-aarch64.tar.gz` + SHA-256.
   Host on Releases tag `userland-v1` — not in the APK.
3. App downloads, verifies SHA-256, extracts into `$PREFIX`. Offline skip.
   `resolveShell` verifies that the ELF loader can actually start Bash/BusyBox and
   falls back to Android `sh` when a shared library is missing. `cc` is always
   rewritten after extract.
4. Smoke test: [docs/chat-phase2/README.md](chat-phase2/README.md).

---

## 8. What we already have (foundation, all committed)

| Piece | Where |
|---|---|
| Executable storage at any targetSdk | `app/build.gradle.kts` — targetSdk 28 |
| Embedded offline compiler (TCC, static musl, arm64 + x86_64) | `app/src/main/jniLibs/*/libtcc.so`, `assets/tcc/*`, `EmbeddedCompiler.kt` |
| Optional Clang module engine | `CompilerService.kt` (BUNDLED) |
| Optional Termux engine (RUN_COMMAND bridge) | `TermuxCompiler.kt` |
| Reproducible TCC bundle builder | `scripts/build-tcc.sh` |
| Device diagnostics (ABI, mount flags) | `DeviceDiagnostics.kt` |
| Green CI (assembleDebug + unit tests + lint) | `.github/workflows/build-apk.yml` |

The embedded TCC means **Phase 1's terminal is useful immediately** — `cc` works from
the shell with zero downloads, even before apt exists.

---

## 9. Risks & mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| apt/dpkg on Android is where Termux spent years | High | Fork their patches verbatim; scope v1 to a curated set; treat apt as "good enough for pkg install", not a full Debian |
| License: forking termux-packages ⇒ app stays GPL-3.0 | Medium | Accept GPL-3.0 for CodeC (already using Termux's RUN_COMMAND API and TCC LGPL); pick MIT libvterm to avoid extra copyleft surface |
| Security: installing arbitrary binaries | High | Package-install confirmation prompt; repo signing (apt keys) from v2; never auto-install |
| Google Play policy | Medium | Already GitHub-distributed only (Play forbids downloaded executable code); document it |
| Android 15+ / future W^X tightening | Medium | `$PREFIX` in app-private filesDir + targetSdk 28 (same as Termux); monitor Android releases |
| proot-style overhead not needed | — | We deliberately **don't** use proot — native binaries, like Termux |

---

## 10. Alternatives considered (and why rejected for now)

- **proot + Alpine/Ubuntu rootfs (UserLAnd style):** fastest route to a real package
  manager, but ptrace-based emulation adds compile/run overhead, glibc binaries are
  "foreign" on Android, and it is a second runtime to maintain. Kept as a fallback if
  the Mini-Termux build proves too heavy.
- **Static toolkit shell (bash + busybox only):** no package manager; useful but doesn't
  meet the "install packages like Termux" goal.
- **Embed Termux's prebuilt debs:** impossible without root (prefix baked into every
  binary — see §2).

---

## 11. Success criteria

- [ ] `pkg install clang` works inside CodeC's terminal on a fresh install (arm64).
- [ ] `bash` scripts run (shebangs work via termux-exec).
- [ ] Editor file opens in terminal (`nano` / `cc` round-trip).
- [ ] Bootstrap download is verified (SHA-256) and re-downloadable.
- [ ] Works offline after bootstrap; embedded TCC remains the zero-download fallback.
- [ ] Green CI: bootstrap build reproducible from `codec-packages` fork.

---

## 12. Out of scope (future)

- Termux:API-style Android integration (sensors, camera, intents).
- X11/GUI packages (SDL/Qt) — the terminal is text-first.
- Full Termux catalog mirroring.
- Root-based acceleration.

---


====================================================================
APPENDIX — MASTER ROADMAP 2026-08-26 (Phase 6–15)
Updated after Phase 5 (5.1 KI fixes / 5.2 web preview / 5.3 capabilities)
is complete and merged (PR #23). All prior phases (0–5) are CLOSED.
No code changes made to this append-only update; only docs + prompt.md.
====================================================================

## A. NORTH STAR (one sentence, drives every phase)

CodeC grows from an offline C IDE into a privacy-first mobile dev environment
(C + web + Python + projects + terminal/GUI parity), with terminal and GUI
as equals — every new feature works both ways, nothing is GUI-only.

## B. METHODOLOGY — what protects us (applies to 6–15)

1. VERIFY FIRST (git status / gh pr list / gh run list) before each part.
2. EVIDENCE BEFORE HYPOTHESIS — device transcript required; never commit
   a fix on a guess.
3. NO REDO — Phase 3/4/5 are closed. Only revisit if identical symptom
   reappears with new evidence.
4. COST TAG ON EVERY PHASE — check at a glance:
   - [client-only] = no rebuild, quick (minutes to ~1 week)
   - [repo-build] = ONE ~60–100 min "CodeC package repository" CI run
   - [bootstrap] = rebuild of userland-v2-dev (NOT planned until needed)
5. NEVER TRIGGER EXPENSIVE ACTION (build / release / destructive device
   test / force-push) without explicit owner command.
6. INVARIANTS (law): no `.` on PATH; no `build-package.sh -I`; never
   overwrite `cc` / real ELF `bash`; TCC link `-o` last; no `com.termux`;
   `signed-by=` only; no bootstrap in APK.
7. ONE PR AT A TIME; NO PR / MERGE / PUSH TO MAIN without your literal
   "open PR" / "merge" command in chat.
8. TERMINAL / GUI PARITY LAW — every new GUI button is a 1:1 wrapper
   over an existing terminal command (`pkg install`, `cc`, `git`, `bash`).
   The terminal stays source-of-truth; GUI never invents new behavior.
9. PRIVACY BY DEFAULT — import = copy-in; export = explicit tap only;
   GitHub token = app-private Settings; nothing auto-public.

## C. DECISIONS LOCKED (delegated by owner, 2026-08-26)

| Decision | Choice | Why |
|---|---|---|
| Editor intelligence (Phase 9/12) | **Native Compose** (not WebView/Monaco) | Matches current architecture (`BasicTextField` → custom `VisualTransformation`); harder to reverse; avoids WebView overhead; multi-language highlighting can extend existing regex engine per-language. WebView can be revisited later if full IntelliSense demands it. |
| Python dependency management (Phase 12) | **`pkg`-managed first**; `pip` deferred | Keeps the signed-repo / installation-confirmation / verification flow intact. `pip` inside Android userland can clash with `PREFIX` paths and isn't verifiable by the repo. If users need `pip`, we can add it as a Phase 15+ option with its own decision. |
| Extra-keys / shortcuts (Phase 6) | **Configurable multi-row grid** anchored to IME | Matches Termux; solves the single-row horizontal-scroll and "way above screen" problems; allows macros (`pkg install`, `git status`, etc.). |

## D. GAPS FOUND IN SOURCE SWEEP (2026-08-26) — ground truth, not guesses

Files verified: `TerminalScreen.kt`, `TerminalEmulatorView.kt`, `TerminalViewModel.kt`, `TerminalSession.kt`, `TerminalExtraKeys.kt`, `EditorScreen.kt`, `MainActivity.kt`, `FileManagerScreen.kt`, `CSyntaxVisualTransformation.kt`, `PtyNative.kt` / `PtySession.kt`, manifest/theme.

- **Cutout / insets:** `enableEdgeToEdge()` in `MainActivity.kt`; terminal `Column` only `.imePadding()`; zero `displayCutout` / `safeDrawingPadding()` hits. Right-edge text clipped in landscape.
- **Extra-keys:** `TerminalExtraKeys.kt` = hardcoded `Row` of 12 keys in `.horizontalScroll`; not configurable; appears too high / off-screen.
- **Wake lock:** none — screen sleeps during `pkg install` / compile.
- **URL detection:** no tap-to-open in terminal output despite `codec-open-url` existing.
- **Selection / copy:** toolbar "copy" copies full transcript; selection only via long-press dropdown.
- **Bell:** VT BEL (`\a`) silently ignored.
- **Title:** static — doesn't reflect cwd or command.
- **Font size:** only pinch-zoom in terminal; buried in Settings.
- **Multi-terminal:** `TerminalViewModel` holds ONE `TerminalSession(); `PtySession` wraps one PTY. No manager / switcher / "+"; `nonce` in route is recomposition trick.
- **Editor dead buttons:** Undo, Redo, Format, Find = `onClick = { showComingSoon() }` (40% alpha). No undo/redo stack.
- **Editor missing:** bracket match, error squiggles, line/col indicator, cursor-line highlight.
- **Editor single field:** one `BasicTextField`; no split panes, no multi-file tabs.
- **File manager flat:** no folder tree.
- **Module screen dead:** `ModulesScreen.kt` / `ModuleCatalog` / `ModuleInstaller` / `ModuleViewModel` exist only for optional Clang module (superseded by `pkg`). Should become `pkg install` GUI.
- **CodeCApi single-session:** bridge answers to one session; multi-terminal must define session routing.

All of the above are captured in the Phase 6–9 plan.

## E. PHASE 6 — 15 TABLE (dependencies + cost + exit conditions)

| Phase | Deliverable | Cost | Depends on | Status / Exit condition |
|---|---|---|---|---|
| **6** | Terminal UX fixes: safe-area/cutout padding; configurable multi-row extra-keys (macros, editable in Settings); wake lock; URL tap-to-open; BEL flash/vibration; dynamic title (cwd / cmd); selection-based copy; cell alignment; smooth pinch-to-zoom | [client-only] | 5.3 (bridge stable) | ✅ **IMPLEMENTED (2026-08-28)** — CI run `33177852501` green. Landscape safe drawing, shortcuts bar, wake lock, URL click, BEL pulse/vibrate, word boundary copy, 60fps pinch-to-zoom. |
| **7** | Multi-terminal sessions: `TerminalSessionManager` (N sessions, each PTY + emulator); session drawer / switcher / "+" button; route `CodeCApi` per session; persist session list across screen changes (not across app restart — matches Termux) | [client-only] | 6 | ✅ **COMPLETE (device-verified 2026-08-28)** — dropdown switcher (D9), per-session CodeCApi collectors, protocol unchanged (D4); device recipe §4 + regression batch green (`docs/chat-phase7/PART_7_MULTI_TERMINAL.md` §6); CI run `33185424586`; unit tests written, CI execution of tests is a recorded follow-up. |
| **8** | Projects & files (KEYSTONE): real folder tree in FileManager; SAF import/export (OpenDocument / CreateDocument); ZIP import/export; per-project run-config (`run.json`: command + type); editor opens files from folder; terminal `cd` follows folder; project icon / list | [client-only] | 7 (multi-session lets project + terminal coexist) | Device: create folder `~/project/test`, import `.c` from Downloads (SAF), open in editor; edit; run via button (uses run-config); export ZIP to Downloads; terminal `pwd` shows project path. |
| **9** | Editor foundation: undo/redo stack; find/replace (Find dialog, regex option); format button (calls `clang-format` or `indent`); bracket match highlight; error squiggles (from compiler output parsed to line/col); line/col indicator; cursor-line highlight; multi-file tabs or split | [client-only] | 8 (folder model gives multi-file context) | Device: type code, undo, redo; find "main", replace; format button runs; bracket pairs highlight; compile error shows red squiggle; tap line number jumps; cursor line highlighted. |
| **10** | GUI package catalog (replaces Modules): `pkg install` catalog screen with 1-tap INSTALL buttons, status detection, quick actions (`pkg update`, `pkg upgrade`, `setup-storage`), custom command runner | [client-only] | 3/5 (pkg verified) | ✅ **IMPLEMENTED (2026-08-28)** — CI run `33177852501` green. 1-tap install & run into live terminal, quick actions, $PREFIX/bin status detection, copy command. |
| **11** | Output panel + Run button (Spck/C4droid feel): editor-screen layout (editor top / output bottom); "Run" button executes run-config command in background; scrollable output below; clickable errors (line reference jumps to editor); keep Terminal tab for interactive | [client-only] | 8 (run-config) + 9 (editor ready) | Device: open C file, tap Run; output appears below with `a.out` result; error at line 14 is tappable → jumps to line 14; Terminal tab still works for interactive `bash`. |
| **12** | Multi-language: Python first: `python3` into `codec-packages`/repo (§1 build ~1–2h); language detect by extension + `#!/usr/bin/env python3`; per-language highlighting engine (extend `CSyntaxVisualTransformation` to keyword/string/comment tables per lang); light autocomplete (buffer identifiers + stdlib snippet table); run-config presets (`python3`, `python3 -m flask'); error parsing per language | [repo-build] (ONE build) | 10 (catalog) + 8 (projects) + 9 (editor) | Device: create `test.py`, open editor (Python keywords highlighted); type `def ` → snippet or identifier suggest (buffer + stdlib); Run button executes `python3 test.py`; error at line 5 parsed correctly; web server (`python3 -m http.server`) opens in WebView; repo rebuilt and published; device installs from new catalog. |
| **13** | GitHub integration (client-only): clone/commit/push screen; repo URL input; token saved in Settings (app-private); clone → folder in FileManager; commit message + push button; terminal still works for `git` commands | [client-only] | 8 (folder) + 11 (output/push feedback) | Device: paste repo URL, tap Clone → files appear; edit file, commit with message, push → success message in output; Settings can update/remove token; no token leaked. |
| **14** | Mixed-language & long-tail: Flask/FastAPI local server (Project type: server); web project opens in WebView (already have `codec-open-url`); add Go / Node / Rust to repo and catalog only when needed (each = [repo-build] on demand); generic meta run (user-defined command) | [repo-build] on demand | 12 (Python proven) + 8 | Device: Python Flask project, run, open `http://127.0.0.1:5000` in WebView; add Go package to repo on request only; run-config supports custom command. |
| **15** | CodeCApi tail + deferred: sensors / camera / intents capabilities over bridge; any remaining 5.3 gaps; terminal session persistence across restart (optional, matches Termux); final polish (font settings discoverability, theme parity) | [client-only] | 7 (multi-session defines routing) + 6 | Device: `codec-sensor` reads accelerometer; `codec-camera` takes photo; `codec-intent` opens other apps; all over per-session bridge; no regression in clipboard/notify/toast/share/open/URL/vibrate. |

## F. DEPENDENCY GRAPH (compact — pick a leaf, all prerequisites known)

```
5.3 (capabilities + bridge proven)
  ├─► 6 (terminal UX)
  │     └─► 7 (multi-terminal)
  │           └─► 8 (projects / files / run-config) ◄── KEYSTONE
  │                 ├─► 9 (editor)
  │                 │     └─► 11 (output panel + Run)
  │                 │           └─► 13 (GitHub)
  │                 ├─► 10 (pkg catalog UI)
  │                 │     └─► 12 (Python + intelligence) ◄── ONE build
  │                 │           └─► 14 (mixed / long-tail, builds on demand)
  │                 └─► 15 (CodeCApi tail / polish)
```

Rule: never start a phase whose prerequisites aren't verified. Phase 8 is the key dependency; everything else feeds off it.

## G. ORDER RECOMMENDATION (matches user's priorities from 2026-08-26)

User priorities folded in:
- Multi-terminal (Phase 7) is #2 after terminal UX (Phase 6).
- Projects/files / folder tree / run-config (Phase 8) is the keystone — everything else needs it.
- Editor foundation (Phase 9) comes after projects because multi-file only makes sense with folders.
- GUI pkg install (Phase 10) replaces dead Modules screen — can go in parallel with 8/9 if staff allows.
- Python (Phase 12) is the ONLY planned [repo-build]; schedule it when you're ready for the ~1–2h CI wait + device verification.
- GitHub (13) and mixed-language (14) come after Python proves the multi-language pattern.

Recommended sequence: **6 → 7 → 8 → (9 || 10) → 11 → 12 → 13 → 14 → 15**.
Within each: agree D1, write doc in `docs/chat-phase6/` etc., host-test, CI, device verification.

## H. PRIVACY STORY PER PHASE (consistent with Phase 4.1)

- Import = SAF copy-into-private; export = explicit CreateDocument tap.
- GitHub token = Settings, app-private, never in `~` or logs.
- `pkg` only installs from signed repo (`signed-by=`); no auto-install.
- WebView loads `127.0.0.1` / local files only; no external URL loading in preview (except user's explicit `codec-open-url`).
- No telemetry, no analytics, no cloud sync.

## I. OUT-OF-SCOPE / DEFERRED (clearly labeled so they don't creep in)

- X11 / SDL / Qt (GUI packages) — deferred until real demand; terminal is text-first.
- Full Termux catalog mirroring — needs cardinality / scope decision; not before Phase 12.
- Root-based acceleration — out of policy.
- `pip` inside userland — deferred to Phase 15+; pkg-only first.
- WebView editor (Monaco/CodeMirror) — deferred; native Compose is the recorded decision; revisit only if Phase 9 proves inadequate.
- Session persistence across app restart — optional; Termux doesn't do it; `tmux` already available.

## J. WHAT HAS NOT CHANGED (preserve invariants)

- Bootstrap (`userland-v2-dev`) stays byte-stable. Nothing in 6–15 touches it.
- Repository signing (`codec-packages/keys/`) stays intact; new packages (Python, Go, etc.) added only through verified CI pipeline.
- `prompt.md` self-distrust protocol stays; this appendix is part of the evidence.
- `build-package.sh -I` never used; official `com.termux` never referenced.

====================================================================
END OF APPENDIX — Phase 6–15 master roadmap (2026-08-26 update)
====================================================================
