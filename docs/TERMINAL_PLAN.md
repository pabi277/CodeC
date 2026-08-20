# CodeC Terminal — Mini-Termux Plan

> **Status:** Phase 1 **working on device** (app **1.3.13+**) · Phase 2 **released** as `userland-v1` (clean-device acceptance pending) · Phase 3 **M1 in progress** (repository foundation and guarded `pkg` frontend).
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
| **2** | In progress (1.3.14): `codec-packages` overlay + download/extract. Full docker bootstrap on workflow_dispatch. | 1–2 weeks |
| **3** | apt + dpkg + termux-exec + own repo + first `pkg install` working | 2–4 weeks |
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
   `resolveShell` prefers ELF bash. `cc` always rewritten after extract.
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
