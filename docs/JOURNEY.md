# CodeC — the full journey

**Last updated:** 2026-08-22 · **Branch:** `arena/01a028e2-codec`

A single, chronological record of how CodeC got from "a C editor for Android"
to "an IDE with its own terminal, its own Termux-style userland, and its own
package manager". This is the narrative; the per-phase problem/solution records
remain in [`docs/chat-phase1/`](chat-phase1/README.md),
[`docs/chat-phase2/`](chat-phase2/README.md), and
[`docs/PHASE3_PKG_DEBUGGING.md`](PHASE3_PKG_DEBUGGING.md). Remaining work is
broken into ordered parts in [`docs/NEXT_STEPS.md`](NEXT_STEPS.md).

**Starting a new chat?** Paste [`prompt.md`](../prompt.md) as the first
message — it encodes the self-distrust protocol and the order of work, so the
next agent verifies state before acting and does not redo completed work.

---

## 0. The idea

CodeC ships a C compiler inside the APK so a phone can write and run C
offline. The long-term goal — stated in [`docs/TERMINAL_PLAN.md`](TERMINAL_PLAN.md) —
is to become a **self-contained mobile dev environment**: an in-app terminal, a
real Linux-style userland, and `pkg install clang git python` — exactly like
Termux, but built for CodeC's own identity, not Termux's.

The single hardest constraint, and the reason everything below exists:

> Termux compiles every binary with `/data/data/com.termux/files/usr` baked in.
> A different app cannot use those binaries. **Every package must be rebuilt
> with CodeC's own prefix** `/data/data/com.codeci.ide/files/usr`.

So CodeC forks Termux's **build system** (`termux-packages` recipes) rather than
its packages, and re-targets the identity to `com.codeci.ide`.

---

## 1. Phase 0 — foundation (done)

| Piece | Where | Why it matters |
|---|---|---|
| `targetSdk 28` compatibility mode | `app/build.gradle.kts` | keeps `exec()` of downloaded binaries legal on Android 10+ (W^X rule) — the same trick Termux uses |
| Embedded TCC (static musl, arm64 + x86_64) | `app/src/main/jniLibs`, `assets/tcc`, `EmbeddedCompiler.kt` | a zero-download, offline C compiler in the APK |
| Optional Clang module engine | `CompilerService.kt` | full C11/C17 via a downloaded module |
| Optional Termux bridge | `TermuxCompiler.kt` | RUN_COMMAND intent fallback |
| Reproducible TCC bundle builder | `scripts/build-tcc.sh` | CI-ready |
| Device diagnostics | `DeviceDiagnostics.kt` | ABI / mount flags for bug reports |

This phase solved the original "Permission denied" W^X problem — see
[`docs/TROUBLESHOOTING.md`](TROUBLESHOOTING.md).

---

## 2. Phase 1 — the terminal (`cc` / `./a.out`), v1.3.10 → 1.3.13

**Delivered:** a real VT/ANSI terminal inside the app, driving a PTY with a
shell, where `cc file.c -o a.out` and `./a.out` work.

Building blocks:
- **PTY** via JNI `openpty` (`app/src/main/cpp/pty.c`, `PtyNative`, `PtySession`).
- **VT parser** — hand-rolled xterm-256color subset in Kotlin (`AnsiParser`,
  `TerminalEmulator`): colors (SGR 16/256/RGB), cursor, scrollback, alt screen,
  bracketed paste.
- **Canvas grid renderer** (Termux-style `measureText("X")` / `mTopRow`) + a
  real Android `InputConnection` IME view (`TerminalKeyView`).
- **`cc` frontend** written into `$PREFIX/bin/cc` by `ShellEnvironment`, wired
  to the embedded TCC. A `pkg` placeholder and a `bash` shim come with it.
- Projects live on **executable** `filesDir/CodeC/projects` (emulated storage is
  `noexec`).

Twelve real bugs were closed in this phase (P1–P12 in
[`chat-phase1/PROBLEMS.md`](chat-phase1/PROBLEMS.md)): `./a.out` permission
denied, slow keyboard, the 137 kill-on-restart race, `-o` link order, `scanf`
prompt ordering (fixed with `codec_stdio.o`, not by changing user C), and more.
Each has a durable invariant recorded in
[`chat-phase1/SOLUTIONS.md`](chat-phase1/SOLUTIONS.md) — the TCC link order, no
backslash in `cc`, never `exec` tcc, never add `.` to `PATH`.

---

## 3. Phase 2 — the userland (`userland-v1`), v1.3.14

**Delivered:** a real ELF `bash` + `busybox` downloaded and extracted into the
app-private prefix — the first real userland, before any package manager.

Key pieces:
- **`codec-packages/`** — a GPL-3.0 overlay on a pinned `termux-packages`
  revision. `apply-prefix.sh` rewrites the identity/prefix; the official
  `run-docker.sh` / `build-package.sh` do the rest.
- **`build-bootstrap.sh`** clones upstream, applies the overlay, and builds
  `busybox` + `bash` from source (never `build-package.sh -I`, never official
  `com.termux` `.deb`s).
- **`assemble-bootstrap.sh`** extracts the `.deb`s and archives only the
  `$PREFIX` contents (root-level `bin/`, `lib/`, `etc/`, `var/` — never a
  nested `data/data/` tree).
- **`UserlandInstaller`** + **`TarGzExtractor`** download, SHA-256-verify, and
  extract; `resolveShell` prefers a runnable ELF Bash/BusyBox and falls back
  gracefully.

Sixteen problems were closed here ([`chat-phase2/PROBLEMS.md`](chat-phase2/PROBLEMS.md)),
including: abandoning a hand-rolled direct-NDK build in favour of the official
builder, rejecting `-I`, dropping `termux-tools`/`termux-am`, fixing the nested
`data/data/` archive layout, and the `pipefail`+`grep -q` SIGPIPE trap.

Published result: release **`userland-v1`** (`bootstrap-aarch64.tar.gz`, 7.6 MB,
SHA-256 `641c18d3…`). Known gap: that archive's Bash could be missing
`libandroid-support.so` — the app-side launch check handles it, but clean-device
acceptance stayed pending.

---

## 4. Phase 3 — the package manager (`pkg`, apt/dpkg, repository)

The big one. Broken into milestones in [`docs/PHASE3_PLAN.md`](PHASE3_PLAN.md).

### 4.1 M1 — repository foundation ✅

- A CodeC-owned **APT repository** layout (`generate-repository.py`,
  `repository_lib.py`, `validate-repository.py`) producing `Release`,
  `Packages`/`Packages.gz`, `repository.json`, and SHA-256 sidecars.
- A **guarded `pkg` frontend** (`ShellEnvironment.pkgScript()`) for
  `update / search / install / upgrade / uninstall / repair` that is CodeC-only
  and fails clearly before a Phase 3 bootstrap exists.
- **Security preflight** on every package: ABI, prefix confinement, path
  traversal, symlink escape, `com.termux` contamination, size, SHA-256, and a
  strict maintainer-script allowlist (only the reviewed `coreutils`/`less`/
  `nano` alternatives scripts).
- Host tests + CI (build on dispatch, publish to Pages).

Published development channel: **`https://pabi277.github.io/CodeC/dev`**.

### 4.2 M2 — the apt/dpkg bootstrap 🟡

- Bootstrap roots expanded to **`busybox bash apt dpkg`** plus the full
  source-built dependency closure (aarch64 + x86_64), with a seeded dpkg
  status database.
- **termux-exec** handled: the official recipe needs a Termux-farm-only
  prebuilt, so `termux-exec-standalone.sh` builds the LD_PRELOAD library from
  pinned public sources (best-effort).
- The apt recipe's `sources.list` is rewritten to the CodeC channel only.
- `validate-bootstrap.py` gates release archives (layout, ELF, dpkg status,
  termux-exec/libandroid-support, checksum sidecar, traversal/contamination).
- `UserlandInstaller` selects the Phase 3 release with a `userland-v1`
  fallback, `.partial` downloads + resume, staged atomic extraction/rollback,
  and disk-space preflight.
- Published the pre-release **`userland-v2-dev`**
  (`bootstrap-phase3-{aarch64,x86_64}.tar.gz` + sidecars).

### 4.3 This session — on-device debugging that actually finished it

Installing the APK on a real aarch64 phone surfaced five independent bugs. The
full trace and diagnosis are in [`docs/PHASE3_PKG_DEBUGGING.md`](PHASE3_PKG_DEBUGGING.md).
Each is fixed in `ShellEnvironment.pkgScript()` (app code — no bootstrap rebuild
needed for four of them):

1. **`dpkg-perl : Depends: clang`.** The official dpkg recipe listed `clang` as
   a *runtime* dependency of `dpkg-perl`. CodeC dropped it in
   `apply-recipe-overrides.sh`, but the published bootstrap predated that fix
   and seeded the stale line into `var/lib/dpkg/status`. `pkg` now **self-heals**
   the line on every run. *(A clean bootstrap rebuild also removes it for good.)*
2. **`/data/user/0/` vs `/data/data/` alias.** Maintainer scripts are generated
   with the canonical `/data/data/…` prefix, but the app sets
   `$PREFIX=/data/user/0/…`. The alternatives byte-check now matches the
   canonical form (`CANON_PREFIX`).
3. **Missing `bin/sh`.** CodeC drops `termux-tools` (unwanted `termux-am`
   chain), but that is what normally provides `bin/sh` — and dpkg runs every
   maintainer script through `sh`. `pkg` now symlinks `bin/sh → bash`.
4. **Missing `var/log/apt`.** apt aborts the install phase without it. `pkg`
   now creates it (plus the cosmetic `etc/apt/apt.conf.d` / `preferences.d`).
5. **Over-strict symlink preflight.** nano ships a legitimate license link
   `share/licenses/nano -> ../../LICENSES/GPL-3.0.txt`. The preflight now
   resolves relative climbs and rejects only true prefix escapes.

**Two findings that changed the plan:**

- **termux-exec was not actually required.** After fixing 1–5, the nano
  `postinst` (with its `update-alternatives` call) ran and registered `editor`
  successfully *without* `libtermux-exec-ld-preload.so`. The shebang executes
  via the short `/data/data/…` path. So the missing-LD_PRELOAD worry is a
  non-issue for the reviewed scripts.
- **Seeded packages never run their postinst.** `coreutils`/`less` are present
  (copied + status entries seeded at build time) but their alternatives were
  never registered — hence `pager: command not found` — and all seeded packages
  fail `dpkg --audit` (no `md5sums`). This is a bootstrap *content* gap, not a
  `pkg` defect.

### 4.4 Verified working on device (2026-08-22)

| Operation | Result |
|---|---|
| `pkg update` | ✅ index refreshed |
| `pkg search gawk` | ✅ finds gawk + gawk-static |
| `pkg install nano` | ✅ downloads libmagic + nano, runs postinst |
| `nano --version` | ✅ GNU nano 9.2 |
| `which editor` / `editor --version` | ✅ `$PREFIX/bin/editor` (alternatives link works) |
| `pkg uninstall nano` → `pkg install nano` | ✅ clean remove/reinstall |
| `pkg upgrade` | ✅ finds a `sed` upgrade |
| `cc t.c -o a.out && ./a.out` | ✅ prints `ok` (TCC untouched) |

**The full `pkg` pipeline — download → preflight → dpkg → postinst →
update-alternatives — is proven end-to-end on a real device.**

---

## 5. What is *not* done

`pkg` works, but **Phase 3 is not complete**. The remaining work is broken into
clear, ordered parts in [`docs/NEXT_STEPS.md`](NEXT_STEPS.md). In brief:

1. **Republish a clean bootstrap** (one rebuild with all recipe fixes) so a
   *fresh* device never sees the `clang` bug.
2. **Fix bootstrap correctness** — seed only the runtime closure, run the
   seeded packages' postinst, and include `md5sums`.
3. **Run clean-device acceptance** — airplane mode, interrupted-install
   recovery, and the v1 → Phase 3 upgrade path.
4. **M3 — sign the repository** (currently HTTPS + SHA-256 only).
5. **Phase 4 — polish** (storage access, `termux-setup-storage`-equivalent,
   security confirmation prompt, themes, signing UX).
