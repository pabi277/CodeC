# CodeC — the full journey

**2026-09-03 — Phase 21 COMPLETE, device-accepted and MERGED to `main`**
(owner: "start phase 21" → "Pass" → "marge it"; `arena/01a064e0-codec`, base
`3fa71ab`). The compiler engine redesign — though **not** the one the spec
described. The hard-coded `when (LanguageType)` in
`EditorViewModel.runActiveFile` is replaced by a generic
`LanguageRunProfile` registry, so adding a language is now one entry in a
list. But **TCC was not retired**: the owner's mid-phase direction made it the
*default* C compiler (see the end of this entry), so `.c` still compiles with
the built-in `cc` and only C++/other languages reach for the userland. Four new Android-free files carry the whole design:
`LanguageRegistry` (12 profiles: C, C++, Python, JS, TS, Go, Rust, PHP, Ruby,
Lua, Shell, HTML + `$SRC`/`$OUT` templating and POSIX quoting),
`LanguageRunPlanner` (a sealed `RunDecision`: WebPreview | NeedsInstall |
Execute | Unsupported), `LanguageToolProbe` (`$PREFIX/bin/<binary>` exists —
no `pkg` query per RUN tap) and `InstallPromptState`. D.2's gate asks
"Install <language>?" before the first RUN of a file whose toolchain is
missing, streams `pkg install -y <pkg>` into the Output Panel with a 900 s
timeout, and re-enters the run automatically on exit 0. `TerminalHandoff` now
emits `gcc`/`g++ … -lm` and — new — dispatches scratch files through the
registry, fixing a real pre-existing bug where "Run in terminal" fed a `.rb`
or `.lua` file to a C compiler. +33 host tests (`LanguageRegistryTest`,
`LanguageRunPlannerTest`) plus the updated `TerminalHandoffTest`. **D.3 device
pass required** (C and C++ end-to-end through gcc, plus the install gate)
**Device round 1 (2026-09-03) failed at the gate and was fixed the same day:**
`pkg install -y gcc` → *"E: Unable to locate package"*, then after a manual
`pkg update` → *"the following packages replace it: **libllvm**"*. The owner
called it — *"i think gcc is wrong i am using clang"*. Root cause: I lifted
`requiredPackage = "gcc"` from the Phase 21 spec, which predates Phase 20.1;
at the pinned ref there is no `packages/gcc` recipe at all — `libllvm`'s
`clang` subpackage creates the `gcc`/`g++`/`c++`/`cpp` driver symlinks, so
`gcc` is a binary, never an installable name. Fixed to install `clang` while
still probing `gcc`/`g++`, plus two neighbours the audit caught: Go and Rust
were never published (now `inRepository = false` → an honest "not in the
repository yet" instead of a doomed install), and the gate now runs
`pkg update &&` first so a stale catalog is self-healing. Guard tests pin every
installable name to `codec-packages/properties.codec.sh`. **Device round 2 the same day** exposed a deeper miss: *"python3: command not
found / Server exited with code 127"*. The D.2 gate only ever covered the
active-file path — server-type projects and custom `project.json` build/run
pairs execute their configured command verbatim and never consult the
registry. Fixed with `toolchainForCommands`, which gates any raw command
string by the leading program of each `&&`/`;`/`|` segment (so `./bin/server`
and `echo node` never prompt), plus `pendingServerProject` so a successful
install resumes the server rather than the active file; the `c-microservice`
preset's leftover `cc server.c` became `gcc`. The lesson for D.4: CodeC has
**five** run paths (active file, project file, project config, server preset,
terminal handoff) and the Phase 21 spec described only one. **D.3 then PASSED (owner: "Pass") — and D.4 was CANCELLED.** Beginning the
TCC deletion surfaced what the spec never modelled: `cc` is CodeC's *own TCC
frontend*, and Phase 20.1 deliberately strips `bin/cc` from the clang deb to
protect it, so deleting the assets would have broken `cc` in the terminal and
every `project.json` already on disk. Raised rather than executed — and the
owner redirected: *"remove the option of compiler Setting and make the tcc
default but if need user can install gcc"*. So TCC is not legacy, it is the
**default C compiler**: the Settings → Compiler Engine picker and the
`COMPILER_BACKEND` preference are gone (D22), a `.c` file compiles with the
built-in `cc` and is **never** gated behind a download (D23), `.cpp` still
installs clang because TCC cannot build C++, and the `-o`-last link-order
invariant is now PERMANENT. Note: the spec's `useLegacyTcc`
flag was deliberately *not* added — there was never a distinct TCC branch to
gate, only the `cc` string. Record:
[`chat-phase21/PART_21_IMPLEMENTATION.md`](chat-phase21/PART_21_IMPLEMENTATION.md).

**Last updated:** 2026-09-03 · **State:** **Phase 22 implemented & CI-green on
`arena/01a065a0-codec` (head `a39a5d6`) — six rounds, five from owner device
feedback; owner has stopped testing and accepted the current state. **Phase 22 was
MERGED to `main` via PR #45 on 2026-09-03 (`main` tip `7173494`, post-merge CI
`33730937920` green). Phase 23 is ✅ COMPLETE & DEVICE-ACCEPTED on
`arena/01a06662-codec` (`Build APK` `33735687876`)
(owner: "Start Phase 23" → "Phone test passed"): B.1 inline PTY input (remove
`OutputInputRow`) + B.2 context-aware run keys (`↵ Enter`/`Ctrl+C`/`Tab`/history);
both §4 device recipes passed on device (2026-09-03).**
Phase 21 MERGED to `main` by owner command** (2026-09-03, from `arena/01a064e0-codec`; base `3fa71ab` = Phase
20.1 via PR #43). Verify the tip with `git ls-remote origin main` — the local
clone is shallow. Phases 3–19 all complete/merged;
**Phase 20.1 (package toolchain round 4: libllvm/clang + nodejs/npm + php +
ruby + lua54) 🚧 IMPLEMENTED on `arena/01a05cb9-codec` (owner: "Phase 20
start")** — host suite 95 green (10 new override tests — incl. the D10 LLVM build-time trim, now PERMANENT); five new
`apply-recipe-overrides.sh` blocks (clang `bin/cc` strip protecting the
invariant, nodejs/npm debscripts no-ops, php heavy-extension trim, lua54
alternatives→symlinks); **`[repo-build]` CI dispatch awaits the owner's
explicit confirmation** (dispatch-only since round 1; pushes never trigger).
Research corrections recorded in `docs/chat-phase20/PART_20_1_TOOLCHAINS.md`
§7 — at the pinned ref there is no `gcc`/`clang` recipe: `libllvm` is the
root and its clang subpackage ships the `gcc`/`g++` driver symlinks; `npm`
is a separate recipe since nodejs 25.3.0-1. **No remaining spec'd implementation — the owner's
future-update mode is defined in [`rule.md`](../rule.md): all phases are
complete, so the agent waits for the owner to report a bug, listens carefully,
finds the underlying code problem, and solves it (owner merges to `main`).**
**First bug report in the new mode (2026-09-01):** *"create a new branch don't
add in github, locally commit cannot be pushed"* — new branches were never
published (now published on creation) and an unpublished branch showed no
"not pushed" badge (now an amber `↑` pill). CI green `33476150534`; owner
device pass pending. Record:
[`chat-phase15/PART_17_SOURCE_CONTROL.md`](chat-phase15/PART_17_SOURCE_CONTROL.md)
§6.3.

**Second bug report in the new mode (2026-09-01):** *"add clear error messages
like git is not installed, no token available, a guide to get a new token with
proper link, and other things that will be user friendly."* — every git failure
now ends in an actionable sentence instead of raw git text, via a new
Android-free `GitErrors` classifier (+ `GitErrorsTest` 23 host tests); token
failures carry GitHub's fine-grained PAT page as a tappable
"Create a GitHub token ↗" link in the Source Control sheet and Settings →
GitHub Account. CI green `33479410194`; owner device pass pending. Record:
[`chat-phase15/PART_17_SOURCE_CONTROL.md`](chat-phase15/PART_17_SOURCE_CONTROL.md)
§6.4.
**Phase 18 (CodeCApi Device Capabilities) ✅ COMPLETE & DEVICE-ACCEPTED
(2026-09-01, `arena/01a05b12-codec`, `4460306`, CI `33468442063`, owner §4
recipe PASSED) — the last spec'd work; Phase 18 is CLOSED.** Five CLI scripts +
wire ops over the existing OSC 1337 CodeCApi bridge: `codec-battery`
(sticky `ACTION_BATTERY_CHANGED` → JSON), `codec-sensor`
(accelerometer/gyroscope/light one-sample), `codec-tts` (app-lifetime
TextToSpeech, QUEUE_FLUSH, 32 KiB cap), `codec-camera` (runtime CAMERA
park/resume — same Phase 4.8 pattern — + `TakePicture` via FileProvider,
sanitized name under `$PREFIX/tmp/codec-api/camera/`, `OK:<path>`/`ERR`),
`codec-intent` (implicit view/dial/send only + URI-scheme allow-list; never
an explicit component). `BOOTSTRAP_VERSION` 26 → 27; manifest: `CAMERA` +
`uses-feature required=false` (lint) + TTS/IMAGE_CAPTURE queries. Pure
host-testable core via android-free `DeviceApiOps` — `CodecApiBridgeFullTest`
×22 + protocol/script additions; the one red CI round was the lint ERROR,
fixed same commit set. Record: `docs/chat-phase18/PART_18_CODEAPI.md` §5
(design D1–D9, research notes with sources, files, exit status) + §5.6
(device acceptance transcript).
**Phase 19 (Terminal Parity + Unicode/protocol parity) is COMPLETE,
DEVICE-ACCEPTED and MERGED — PR #34 merged to `main` at `b869ce6`
(2026-08-31T09:55:36Z)**, so `main` = `b869ce6` now (the previously cited
`8dd961a2` = PR #33 is its ancestor). CI on `main` green after the merge
(`Build APK` `33380041937`).
**Phase 15 (Spck clone — Projects Hub & Unified Import): device round 1 done
(2026-08-31) — both owner issues fixed (clone kind re-detect; Packages tab
restored), CI green `33385105931` @ `83ba499`.**
**Device round 2 (2026-08-31, owner: "something is off about the ui — I want
exactly same ui"): mockup-exact re-skin of the whole Phase 15/16 UI** (flat
5-tab bar, pill filter chips, 16dp cards + 56dp type squares w/ Python logo,
mockup-color `+` sheet, clone dialog rebuilt, editor top bar → `☰ tabs ⋮ ▶ RUN`,
3dp tab underline, keycap keys row, dot status bar, gutter divider, drawer
re-skin, Source Control sheet rebuilt to `mockups/source-control.png` with
per-file stage toggle `GitManager.stageFile`/`unstageFile` +2 host tests).
Clean-room hand-drawn glyphs in `SpckIcons.kt`. — see items 19–20 and
[PART_15 §6 / PART_16 §6](chat-phase15/) (device round 2).
**Phase 14 (Mixed-Language, Server WebViews & Long-Tail Ecosystem) was merged in PR #32** — client-only, no `[repo-build]`; `Build APK`
`33352164172` green (assemble + unit tests + lint); **device recipe pending
(owner)**. See item 17 below and [`chat-phase14/`](chat-phase14/).
**Phase 4 (Parts 4.1–4.8) ✅ complete** — 4.7 and 4.8 both device-verified
2026-08-26; 4.8 verified the runtime-permission path
(`codec-notify` over the `CodeCApi` bridge: dialog → allow → OK,
owner-confirmed notification tap opens CodeC). The Phase 4 roadmap now
lives in [`chat-phase4/PHASE4_ROADMAP.md`](chat-phase4/PHASE4_ROADMAP.md).
**Phases 5–7 are complete. Phase 8 is complete and fully accepted**: implementation
merged in PR #27 (`348eb03`), core workflows and the final export →
re-import-as-a-new-project round trip owner-confirmed on device 2026-08-29. The
current design and verification record lives in [`chat-phase8/`](chat-phase8/).
**Phases 9–9.2 (Editor Foundation + device rounds) are ✅ COMPLETE and ACCEPTED
(2026-08-29): the owner's device rounds passed ("Good working" → three fixes in
9.1 → "Yes working" → 9.2 UI/folders/single-files), CI green throughout
(`33239651690`, `33241237168`, `33243620762`), and the owner closed the phase by
directing doc finalization + PR creation — **PR #28** (merged to `main` at `961e942`).**
**Phase 11 (Output Panel & Integrated Run) is ✅ COMPLETE & DEVICE-ACCEPTED
(2026-08-30, `arena/01a0508b-codec`) — implemented on the owner's instruction,
CI green through the D9 round (`33293358085`), and every device round passed;
owner's final word: "All of the check passed". Highlights: split-screen Output
Panel with draggable splitter; RUN ▶ builds/executes via the real `cc`
toolchain; clickable error lines → editor jump + Phase 9 squiggles; one-tap
**Apply fix** ("write a code to apply"); interactive programs run on a real
PTY (per-prompt output, one input per scanf, echo, no timeout — D9, after the
owner's "It takes all input at once" round); honest timeout wording +
always-visible Open-in-Terminal escape hatch (D7); input row + terminal escape
kept (D8). **PR #29 MERGED to `main` at `771f58f` (2026-08-30).** See
[`chat-phase11/`](chat-phase11/).**

> **🔒 STANDING RULE (owner, 2026-08-26): do NOT open a PR or merge anything
> without an explicit command from the owner in chat.** Committing to and
> pushing the session branch (`arena/*`) is fine; PR creation and any merge
> wait for the owner's explicit instruction.

A single, chronological record of how CodeC got from "a C editor for Android"
to "an IDE with its own terminal, its own Termux-style userland, and its own
package manager". This is the narrative; the per-phase problem/solution
records remain in [`docs/chat-phase1/`](chat-phase1/README.md),
[`docs/chat-phase2/`](chat-phase2/README.md), and
[`docs/chat-phase3/`](chat-phase3/PHASE3_PKG_DEBUGGING.md) (Phase 3's plan,
device-acceptance checklist, signing operations, and debugging records).
Remaining work is broken into ordered parts in
[`docs/NEXT_STEPS.md`](NEXT_STEPS.md).

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

The big one. Broken into milestones in [`docs/chat-phase3/PHASE3_PLAN.md`](chat-phase3/PHASE3_PLAN.md).

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

### 4.2 M2 — the apt/dpkg bootstrap ✅

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
full trace and diagnosis are in [`docs/chat-phase3/PHASE3_PKG_DEBUGGING.md`](chat-phase3/PHASE3_PKG_DEBUGGING.md).
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

## 5. Part A shipped without a rebuild; Part B merged, one rebuild left (2026-08-22)

**Part A — DONE, and the ~104-minute build turned out to be unnecessary.**
The published `userland-v2-dev` bootstrap predated the `dpkg-perl` clang
recipe fix, so a fresh device's seeded status DB contained
`Depends: perl, clang, make, dpkg (= 1.22.6-5)`. Since the *entire* content
delta between the published artifact and a full rebuild was that single
line, the owner ran `codec-packages/scripts/repair-bootstrap-status.sh`
(Path 2 of `docs/chat-phase3/PART_A_ARTIFACT_REPAIR.md`) in Termux on both published
tarballs and re-uploaded them. Triple-verified: the script's own
before/after tree proofs (only `./var/lib/dpkg/status` changed inside the
archive), the GitHub asset-digest API (aarch64 `074806ad…`, x86_64
`9f93edd0…`), and a **clean-device test** — full uninstall, fresh install,
no `clang` anywhere in the status DB (`grep … ; echo exit=$?` → `exit=1`),
and a complete `pkg update / install / uninstall / reinstall nano` cycle on
nano 9.2 with `editor` resolving to `$PREFIX/bin/editor`. Release notes
updated remotely.

**Part B — all code and tests merged (PR #11); the rebuild is the only step
left.** `plan-bootstrap.py` seeds exactly the runtime `Depends` closure of
the four roots (measured seed set
`CODEC_BOOTSTRAP_SEED_PACKAGES="busybox bash apt dpkg coreutils less"`) and
fails loudly on any unresolved dependency; the reworked
`assemble-bootstrap.sh` additionally seeds upstream-format `md5sums`, wires
every seeded package's update-alternatives **including the dpkg admin
database** (prepend order; slave block from the last registration; format
measured against a live dpkg 1.21), and relativizes paths last. A
full-corpus preflight of all 40 upstream `.alternatives` files (74 groups)
found zero problems. Host suite: **49/49 green**.

The ~104-minute rebuild has consumed **3 dispatches, no artifact yet**:
#1 (`32581293757`) died in 90 s on our own guardrail scanner matching a
*comment* (fixed, plus a tripwire test so it cannot recur silently); #2
(`32582311088`) died at ~50 min on `curl: (28)` downloading
`util-macros-1.20.2.tar.xz` from `xorg.freedesktop.org` — an upstream
network flake, log-proven, our assembly code never ran; #3 (`32585409356`)
died ~33 min into the same step on both arches — cause **unknown**, because
CI logs cannot be downloaded from the agent sandbox. Reading that log
(`gh run view --job 97060936792 --log | tail -120` in Termux) is the
mandatory first step of the next chat, before any new dispatch. The full
decision table and commands are in
[`docs/NEXT_STEPS.md`](NEXT_STEPS.md) → Part B → "Continue here".

---

## 5b. The rebuild succeeded — and the fresh device found two more Part B
defects (2026-08-23)

PR #12's util-macros mirror fallback ended the flake era: dispatch #4
(`32594910882`) built both arches green (aarch64 1h14m, x86_64 1h26m),
`32617929254` republished `userland-v2-dev`, and a truly fresh device
(cleared CodeC storage) downloaded the new aarch64 archive
(22,181,256 bytes), verified its SHA-256, and extracted it. That is when
the real new-bootstrap evidence landed — and it convicted two assumptions:

1. **No HTTPS fetcher in the closure.** `pkg update` died with
   `pkg: offline or unable to download CodeC Release metadata (HTTPS
   required)`: `$PREFIX/bin/{curl,python3,wget}` are all absent. The pkg
   code's comment claiming "python3 + OpenSSL are in the closure" was a
   disproven guess (dpkg's `dpkg-perl` needs *perl*, not python). Worse,
   `pkg`'s maintainer-script byte checks called `python3` directly, so
   `pkg install` would have failed the same way. **Fix:** build `libcurl`
   (the `curl` CLI is its subpackage — upstream auto-generates
   `Depends: libcurl (= …)` for it), seed `curl`, and make the byte check
   pure shell (`$(cat)` + `case`). `ca-certificates` was already in the
   closure via `apt → libgnutls → ca-certificates`, so curl's CA bundle
   (`etc/tls/cert.pem`) comes for free. Python stays out of the bootstrap
   on purpose.
2. **`ii termux-keyring 3.13` in the seeded dpkg status.** The pinned apt
   recipe lists the official Termux repositories' GPG keyring as a runtime
   dependency, and the sources.list override never touched it — so the
   bootstrap shipped Termux's repo signing keys inside
   `etc/apt/trusted.gpg.d/`. **Fix:** a narrow fail-loud override removes
   exactly `, termux-keyring` from apt's `TERMUX_PKG_DEPENDS`;
   `termux-licenses` stays (it provides `share/LICENSES/*`, the target of
   packaged license symlinks such as nano's).

`validate-bootstrap.py` now enforces both invariants at publish time
(`bin/curl` must exist as ELF; no `termux-keyring` stanza in status), and
the host suite grew to **53/53 green** with fixture-level proofs.

### 5c. Part B completed and device-verified (2026-08-23)

PR #13 merged to `main` at `35c350f338be34303296b0168933622991258142`.
Dispatch #5 (`32620704350`) then rebuilt both architectures successfully, and
publish run `32625580655` replaced the `userland-v2-dev` assets. The published
aarch64 archive is **23,926,127 bytes** with GitHub asset digest
`sha256:863f18528afa126d19481f7308a3f9b23997fda9ad9cae3bc7033d8fa60e60cd`.

A full uninstall and fresh install on a real aarch64 device passed the complete
Part B acceptance block: the source-built curl completed the HTTPS/TLS check;
there was no `clang`, build-only package pollution, or `termux-keyring`;
`pager`, `editor`, and `vi` resolved under `$PREFIX`; `dpkg --audit` was
silent; the nano 9.2 install/uninstall cycle was clean; and the embedded `cc`
compiler still printed `ok`. **Part B's exit condition is met. Do not rebuild,
republish, or re-verify it unless Part C records evidence of a genuine new
defect; even then, an expensive workflow requires explicit approval.**

### 5d. Part C completed; two evidence-found defects fixed (2026-08-23)

A clean Samsung SM-A356E (Android 16, aarch64) passed the Phase 3 bootstrap and
runtime smoke, package update/search/install/uninstall/upgrade, alternatives,
negative repository/base-package checks, compiler checks before and after
package operations, and airplane-mode restart. The published bootstrap's
best-effort termux-exec library was absent, but nano's postinst and
`update-alternatives` ran successfully, confirming that the checklist's old
hard requirement was stale.

The interrupted-download test produced one genuine new defect: force-stop left
a `codec-pkg/lock` owned by dead PID `18339`, causing both retry and
`pkg repair` to reject the transaction. PR #14 commit `8e95a16` makes `pkg`
reclaim only a lock whose recorded owner PID is dead and bumps the app bootstrap
marker so the repaired script is installed on APK update. Build APK CI passed.
Repeating the test left dead PID `6549`; the new script reclaimed it, completed
the partial download and install, left `dpkg --audit` silent, and cleared the
pending marker without manual deletion.

The second-device upgrade test then exposed the other genuine defect: released
v1.3.14 writes `.userland-vuserland-v1`, while the upgrader's legacy constant
and unit test had an extra hyphen. PR #14 commit `a4e5af6` corrected the marker.
After green CI, a separate arm64 device performed a genuine in-place update
(the two CI APK payloads were re-signed with one local test-only key solely to
satisfy Android's update-signature rule). CodeC visibly reported the v1 → v2
upgrade, downloaded all **23,926,127 bytes**, verified SHA-256, extracted, and
reported ready. The v2 marker, Bash/apt/dpkg/curl, clean audit, CodeC package
operations, no-contamination check, nano 9.2, and embedded compiler all passed.
**Part C's exit condition is met.**

### 5e. Part D trust implementation staged (2026-08-23)

PR #14 now has a fail-closed signing chain. Repository generation uses APT's
required Release-relative index paths. A key-agnostic signer produces both
`InRelease` and `Release.gpg`; validation requires both, extracts and compares
the exact cleartext, checks the exact signing-subkey fingerprint, and retains
the Release/index/package SHA-256 chain. Real-GPG tests cover protected signing,
missing/tampered metadata, and changed indexes.

The production design keeps primary fingerprint
`3185B4D219C5EF30B263F5E50A458891ED0FB8D3` offline and gives CI only protected
signing subkey `328500868CE9B0F74B62CEFC1D7D52F6F8135015`. Git contains only the
versioned public keyring/armor/fingerprint files. The APK installs that exact
keyring; `pkg` requires `gpgv`, verifies the signed CodeC Origin/Suite before
APT, and uses a keyring-scoped `signed-by=` source. APT verifies independently.
The Phase 3 bootstrap assembler seeds the same public bytes and its validator
rejects a missing or different keyring. An earlier candidate key was replaced
before first signed publication or released-client use when its protected CI
export proved unusable; the operations record preserves those retired
fingerprints.

The first signed publication established valid OpenPGP metadata, then the real
CodeC device exposed a Debian-control grammar defect: blank lines ended the
Release stanza before its hash fields, so APT correctly rejected weak metadata.
Commit `0fa9823` removed those separators and added a fail-closed regression
test. Corrective run `32642631785` reused existing artifacts, skipped both
expensive builds, signed/validated, and deployed successfully. The device then
passed warning-free `pkg update`, exact `VALIDSIG`, tamper rejection, nano 9.2
install/postinst/removal, clean `dpkg --audit`, and compiler smoke. Approved
build run `32643383952` then completed both architectures; each archive passed
assembly plus the validator's exact v3-keyring byte comparison and was uploaded.
Release run `32648783080` downloaded and revalidated both immutable artifacts,
then replaced the four `userland-v2-dev` assets. The new archive digests are
`49cef1ccf82831e870d2d94537c5b9091cc71fa17c4eb0c27dc913d4e79248bf`
(aarch64, 23,928,215 bytes) and
`8e9fd6a973a4c56a957d952aa0ecc1d01ac4788f9cf61bd9162fa6d93e873b4a`
(x86_64, 23,824,737 bytes), matching the live sidecars. Operational details and
rotation/revocation/rollback rules are in
[`chat-phase3/REPOSITORY_SIGNING.md`](chat-phase3/REPOSITORY_SIGNING.md).

### 5f. Part D's final clean-device gate passed — Phase 3 acceptance complete (2026-08-24)

The last open item — a full uninstall/reinstall clean-device pass against the
published, key-seeded `userland-v2-dev` bootstrap (run `32648783080`) — was
run and passed. The pre-uninstall backup was verified first (checksum,
`gzip -t`, archive listing, independent-copy `cmp`); the app was fully
uninstalled, reinstalled from a test-only re-signed sideload APK, and opened
online. The automatic installer downloaded the aarch64 archive end to end
(23,928,215 bytes, matching the digest above), verified its SHA-256,
extracted it, and reached a real `codec $` prompt without a manual
"Install userland" tap.

On that clean device: `$PREFIX`, real ELF Bash (`5.3.15(1)-release`), busybox,
and `dpkg --print-architecture aarch64` all matched expectations; the seeded
dpkg status contained no `clang` and no build-only/`termux-keyring` package by
exact name (an earlier unanchored `grep` had matched `sed`'s description text,
not a real package — the exact-name recheck was clean); `sources.list`
referenced only the CodeC channel; `pkg update` succeeded with no
unsigned/weak-security/hash warning and the installed keyring's SHA-256
(`e9c36bb6…e19a807`) matched the pinned value exactly; an independent `gpgv`
run confirmed `Good signature` from the exact v3 subkey
(`328500868CE9B0F74B62CEFC1D7D52F6F8135015`) and rejected a tampered
`InRelease`; `pkg install nano` (+`libmagic`) ran its reviewed alternatives
postinst, `nano --version` reported 9.2, `editor`/`pager`/`vi` resolved,
`dpkg --audit` was silent, and uninstall cleanly fell back to busybox `vi`;
and embedded `cc` compiled and ran a test program successfully. Full evidence
and commands are recorded in
[`chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`](chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md) §8.

**This closes Part D and Phase 3's device-acceptance gate. No section in
`chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md` remains open.** PR #14 has the code, docs, and
now the recorded evidence for all of Parts B, C, and D and is ready to merge.

---

## 6. What is *not* done

`pkg` works, and as of 2026-08-24 **Phase 3's device-acceptance gate is
complete**. The remaining work is Phase 4 polish, broken into ordered parts in
[`docs/NEXT_STEPS.md`](NEXT_STEPS.md). In brief:

1. ~~Republish a clean bootstrap~~ ✅ **DONE (Part A above).**
2. ~~Fix bootstrap correctness~~ ✅ **DONE (Part B above; device-verified).**
3. ~~Run clean-device acceptance~~ ✅ **DONE (Part C above; all sections passed).**
4. ~~M3 final gate — accept the released key-seeded bootstrap~~ ✅ **DONE**
   (signed Pages, signed-client device path, CI builds, release publication,
   and the backup-first clean-device proof all passed — see §5f above).
5. **Phase 4 — polish and expansion** ✅ **COMPLETE (device-verified 2026-08-26).**
   Planned in [`chat-phase4/PHASE4_ROADMAP.md`](chat-phase4/PHASE4_ROADMAP.md);
   every part below is DONE. **Phase 5** (not started) has its planning
   skeleton at [`PHASE5_ROADMAP.md`](PHASE5_ROADMAP.md):
   - **Part 4.1 — Shared-storage access** ✅ **DONE (device-verified 2026-08-24).**
     `codec-setup-storage` / `termux-setup-storage` configure `~/storage`
     symlinks (`shared`, `downloads`, `documents`, `dcim`, `pictures`,
     `music`, `movies`, `external-*`). Android 11+ All Files Access
     (`MANAGE_EXTERNAL_STORAGE`) supported with OSC 1337 terminal escape
     dispatch and UI setup button.
   - **Part 4.2 — Package-install confirmation UX** ✅ **DONE (verified 2026-08-24).**
     In-terminal transaction summaries (operation, packages, versions, download size,
     installed space change, preflight security status) with interactive `[Y/n]` confirmation,
     `-y`/`--yes` bypass, clean abort on `n` with zero mutation, and core package protection.
   - **Part 4.3 — Trust/channel indicator UX** ✅ **DONE (verified 2026-08-24).**
     Settings "Package Repository & Trust" card (verified trust badge, channel, keyring metadata,
     signing subkey fingerprint, "CHECK REPOSITORY" probe) and terminal `pkg status` / `pkg trust` CLI.
   - **Part 4.4 — Terminal/editor settings parity** ✅ **DONE (device-verified 2026-08-24).**
     Unified color palettes (`Dracula`, `Monokai`, `GitHub Dark`, `Classic Dark`) for terminal canvas,
     custom monospace/proportional font families (`Monospace`, `Courier`, `Sans Serif`, `Serif`),
     Settings screen controls with live `TerminalThemePreview` card, and reactive DataStore flows.
   - **Part 4.5 — Expanded package catalog (Round 2 CI build)** ✅ **DONE (CI verified 2026-08-25).**
     Expanded from 10 to 25 curated package roots (`git`, `wget`, `bat`, `ripgrep`, `fd`, `htop`,
     `tmux`, `tree`, `patch`, `diffutils`, `zstd`, `m4`, `autoconf`, `automake`, `libtool`).
     Workflow run [`32845127723`](https://github.com/pabi277/CodeC/actions/runs/32845127723) (1h 53m 36s)
     compiled all 25 roots and dependencies for both architectures with 0 maintainer script violations and
     byte-identical bootstrap archives.
   - **Part 4.6 — Expanded package catalog (Round 2 publish & device gate)** ✅ **DONE (device-verified 2026-08-25).**
     Published via run [`32858460740`](https://github.com/pabi277/CodeC/actions/runs/32858460740) (reusing CI run `32845127723`).
     Verified `pkg update`, `pkg install`, and execution of all 15 new package roots (`git`, `wget`, `bat`, `ripgrep`, `fd`,
     `htop`, `tmux`, `tree`, `patch`, `diffutils`, `zstd`, `m4`, `autoconf`, `automake`, `libtool`) on a real arm64 device.
   - **Post-implementation review (4.5/4.6)** ✅ **DONE (2026-08-25).** Latent recipe-override bugs found and fixed
     (unreachable whitelist guards, dead override code) with runtime-semantics regression tests; provably artifact-neutral,
     so the published repository stood unchanged. The device-acceptance follow-up additionally root-caused two
     device-side symptoms: the seeded dpkg alternatives admin DB omitted per-record slave placeholders (poisoned
     `pager` group on every fresh bootstrap — fixed for future archives in `plan-bootstrap.py`, mitigated on device
     by the new `pkg heal` self-repair), and CI debug APKs were signed with per-runner ephemeral keys, forcing an
     uninstall-and-wipe on every new build — fixed by pinning a shared repo-level debug key (`debug.keystore`).
     Device acceptance (2026-08-26): 46/46 checks green after one final pinned-key reinstall, and a pinned-cert →
     pinned-cert in-place update proven non-destructive (82 packages, userland, and alternatives DB all intact);
     two non-blocking client known-issues recorded (KI-1 already-installed `pkg install` reports failure,
     KI-2 `$PREFIX` vs dpkg-recorded prefix spelling).
     See [`chat-phase4/PART_4_5_4_6_POST_IMPLEMENTATION_REVIEW.md`](chat-phase4/PART_4_5_4_6_POST_IMPLEMENTATION_REVIEW.md).
   - **Part 4.7 — Android-integration foundation slice** ✅ **DONE (device-verified 2026-08-26).**
     First capability = clipboard (`codec-clipboard get|set|clear|status`) over the reusable
     `CodeCApi` OSC 1337 bridge (file-based request/response under
     `$PREFIX/tmp/codec-api`, path-confined). CI + every primary device check green, incl. the
     piped/redirected channel fix (`/dev/tty` + stdout fallback, device-confirmed); two optional
     negatives waived by owner; post-acceptance review moved request dispatch to activity scope.
     See [`chat-phase4/PART_4_7_ANDROID_INTEGRATION.md`](chat-phase4/PART_4_7_ANDROID_INTEGRATION.md).
   - **Part 4.8 — Android notifications slice** ✅ **DONE (device-verified 2026-08-26).**
     Chosen capability = notifications (`codec-notify send|clear|status`) — the
     runtime-permission path deferred by 4.7: `POST_NOTIFICATIONS` channel creation,
     `NEED_PERMISSION` marker, activity launcher, atomic resume after the dialog.
     Protocol/bridge/CLI implemented, `BOOTSTRAP_VERSION` 24; host `sh` harness green; CI
     green (assemble + unit tests + lint, incl. one fixed test-compile issue). First device
     run uncovered two real issues (hint spam + system-owned dialog not completing the
     parked request) → F1/F2 fixes in `7a321ad`; retest passed: one hint → dialog → allow
     → `OK`, status enabled/ready, `clear` OK, second send with no re-prompt, and the
     owner-confirmed notification tap opens CodeC. **Phase 4 complete.**
     See [`chat-phase4/PART_4_8_ANDROID_NOTIFICATIONS.md`](chat-phase4/PART_4_8_ANDROID_NOTIFICATIONS.md).
6. **Phase 5 — Client fixes, web preview, and CodeCApi capability batch** ✅ **COMPLETE, merged PR #23 (2026-08-26).**
   - 5.1 KI fixes (KI-1 already-installed package error message, KI-2 prefix casing).
   - 5.2 Web preview in app via local server / WebView.
   - 5.3 CodeCApi batch: `codec-toast`, `codec-share`, `codec-open-url`, `codec-vibrate`.
   See `docs/chat-phase5/` and `docs/PHASE5_ROADMAP.md`.
7. **Phase 6 — Terminal UX fixes** ✅ **IMPLEMENTED (2026-08-28).**
   - Part 6.1: safe-area / display-cutout padding (`safeDrawingPadding()`, `shortEdges`), configurable FlowRow extra-keys grid + custom macros in Settings, safe wake-lock on active session, URL tap-to-open detection, VT BEL visual flash + vibration, dynamic title, selection-based toolbar copy + word boundary lookup, monospace cell-by-cell rendering (no cursor drift), and smooth 60fps pinch-to-zoom (PTY resize decoupled from continuous in-flight touch gestures).
   See `docs/chat-phase6/PART_6_TERMINAL_UX.md`.
8. **Phase 10 — Package & Command Hub (Modules Screen Upgrade)** ✅ **IMPLEMENTED (2026-08-28).**
   - Transformed legacy Modules screen into a full-featured Package Catalog & Command Hub:
   - 1-tap direct package installation (`pkg install -y <pkg>`) and execution into the live terminal with carriage return line discipline (`\r`).
   - Quick action shortcuts for repository management: `pkg update`, `pkg upgrade -y`, `codec-setup-storage`, `pkg status`, `pkg heal`, `pkg repair`.
   - Curated catalog covering Compilers, Editors, Languages, CLI utilities, and Compression tools.
   - Real-time `$PREFIX/bin` installation status detection (`INSTALLED ✓` / `AVAILABLE`).
   - Custom interactive command runner card.
   See `docs/chat-phase10/PART_10_PKG_GUI.md`.
9. **Phase 7 — Multi-terminal sessions** ✅ **COMPLETE (device-verified 2026-08-28, `arena/01a048df-codec`).**
   - `TerminalSessionManager` (pure Kotlin, host-tested design): N concurrent PTY
     sessions, monotonic numbering, adjacent-selection close, auto-recreate on last
     close, 8-session cap, `anyAlive` wake-lock source.
   - `TerminalViewModel` delegates session state to the manager; one CodeCApi
     collector per session (protocol unchanged — responses are per-invocation
     `mktemp` files, so no cross-talk); `send`/`sendCommand`/`resize` route to the
     active session (public API preserved); `installUserland(force)` resets to one
     fresh session.
   - UI: session-number badge + dropdown switcher (status dot, rename, close-confirm,
     "+ New session"); `TerminalEmulatorView.resizeKey` re-applies grid dims on
     switch (kills the 80×24 cursor-drift latent bug).
   - Evidence: decisions D1–D12 in `docs/chat-phase7/PART_7_DESIGN_DECISIONS.md`;
     10 unit tests in `TerminalSessionManagerTest` (written, **not yet executed by
     CI** — `build-apk.yml` runs assemble only and the agent token cannot change
     workflow files; owner one-liner recorded in `docs/chat-phase7/README.md`);
     CI compile-green run `33185424586`; **device acceptance §6 green** (sanity
     `cc -v`, background ticker + switch, `stty size` `27 63` both sessions,
     `codec-toast`/`codec-clipboard` from session 2, close/exit transitions,
     active-session routing for Modules/Editor/toolbar, session cap).
   See `docs/chat-phase7/`.
10. **Phase 8 — Projects & File Tree** ✅ **COMPLETE and device-accepted (2026-08-29; PR #27 merged at `348eb03`).**
    Added private project directories, hierarchical folders, breadcrumbs, project
    metadata/run configuration, SAF folder/file import, complete
    extension-agnostic ZIP import/export, central-directory ZIP recovery,
    terminal project listing, editor project routes, Projects overflow actions,
    refresh/collapse-all, and HTML/HTM default web Run entry. Owner confirmed
    ZIP extraction with HTML, CSS, JS, C, and Python files, terminal project
    listing behavior, refresh/collapse, and HTML default Run. On 2026-08-29 the
    owner confirmed on device that the final export → re-import-as-a-different-project
    round trip succeeded, closing the last acceptance gate.
    see [`chat-phase8/PART_8_DESIGN_DECISIONS.md`](chat-phase8/PART_8_DESIGN_DECISIONS.md).
11. **Phase 9 — Editor Foundation** ✅ **COMPLETE (2026-08-29, `arena/01a04c1c-codec`); CI green; device rounds passed; closed by the owner's finalization instruction → PR #28.**
    Undo/redo (per-file snapshot history with typing-burst coalescing), find/replace
    (literal + regex, match case/whole word, wrap-around, group references, live
    highlights), Format (`clang-format` bridge first, built-in line-preserving C
    indenter fallback), bracket pair matching (string/comment aware), compiler
    diagnostics (parsed `file:line:col` output + structured errors → line squiggles,
    tap-to-inspect tooltip, missing-`;` quick fix), Ln/Col status bar, current-line
    highlight, and multi-file project tabs (per-tab undo history + dirty state,
    close-confirm, save-all, reload). 55 new host unit tests, executed green by CI
    (which also revealed and fixed two real API-compat issues: Compose 1.7 has no
    `SpanStyle.drawStyle`; `ProcessBuilder` file redirects need API 26). Exit
    condition: device recipe §4 of `PART_9_EDITOR.md` — run on device 2026-08-29
    ("Yes working" + three problems; resolved by items 12–13; owner closed Phase 9 on
    2026-08-29 by directing finalization + PR #28). See
    [`chat-phase9/`](chat-phase9/) and
    [`chat-phase9/PART_9_IMPLEMENTATION.md`](chat-phase9/PART_9_IMPLEMENTATION.md).

12. **Phase 9.1 — device follow-ups to Phase 9** ✅ **COMPLETE (2026-08-29, `arena/01a04c1c-codec` run `33241237168`) — owner's device round passed ("Yes working"; its three new asks shipped as item 13).**
    Three problems from the owner's device pass: (1) no Spck-like file switching in the
    editor → folder-icon bottom-sheet drawer listing the open project's tree (or scratch
    files), tap to open as tab; (2) a project's `.c` file could not be run from the folder
    → per-file **Run in terminal** in the Projects tree (`cd proj && mkdir -p bin && cc
    main.c -o bin/main.out && ./bin/main.out`) plus editor **Save to project…** fixing the
    root cause (scratch Save wrote `CodeC/projects/main.c`, outside every project folder,
    so `cc` in `portfolio-system3` found nothing); (3) HTML preview loaded via `file://`
    so only inline-referenced css/js worked → `WebPreviewServer`, an in-app HTTP server on
    `127.0.0.1` (ephemeral port, loopback-only cleartext via `network_security_config`,
    traversal-safe resolution, index fallback, MIME map) serving the whole project folder
    so `fetch("data.json")`, XHR, ES modules and relative assets work; falls back to
    `file://` if binding fails. New host tests for `WebPreviewServer` path rules and the
    handoff command. See `chat-phase9/PART_9_IMPLEMENTATION.md` §Phase 9.1.

13. **Phase 9.2 — simpler editor UI + folders & single files from the editor** ✅ **COMPLETE (2026-08-29, run `33243620762`) — owner closed Phase 9 with the finalization + PR #28 instruction.**
    Owner: "still not a friendly editor, make ui less complex", "open a project folder from
    the editor is not possible", "everything need a project — i want an option for single
    file also". Editor toolbar trimmed to undo/redo/save/⋮ (Format, Find, Run-in-terminal
    moved into the menu); the folder button + breadcrumb open a Files & Projects sheet with
    a **Change** folder picker (Single files ⇄ any project — buffers saved first, tabs
    re-keyed, terminal cwd follows) and a **+ New file** action; single files are a
    first-class context (create/run/delete straight from the sheet, `cc` via the terminal
    handoff); long-press on any listed file gives Run in terminal / Delete. VM API:
    `switchContext`, `createAndOpenFile`, `deleteFileEntry`. See
    `chat-phase9/PART_9_IMPLEMENTATION.md` §Phase 9.2.
14. **Phase 11 — Output Panel & Integrated Run** ✅ **COMPLETE & DEVICE-ACCEPTED (2026-08-30, `arena/01a0508b-codec`)** — implemented on the owner's instruction ("Ok start phase 11"); CI green (incl. `33293358085`); **every device round passed; owner's final word: "All of the check passed"**; **PR #29 MERGED to `main` at `771f58f` (2026-08-30).**
    Split-screen **Output Panel** under the editor with a draggable splitter; **RUN ▶** now builds & executes through the app's real toolchain (`cc` frontend → embedded TCC, the exact commands the terminal handoff produces) for both project contexts (project.json build/run) and single files (`cc <file> -o a.out && ./a.out`); real-time streamed output with per-phase status (Compiling… → Build OK (Nms) → program output → exit code + duration), Stop (kills the live process), Copy, Clear, collapse/expand, auto-scroll, and an **Open in Terminal** escape hatch for interactive programs. Compiler diagnostics in the panel are clickable (`file:line:col: error:` — Clang and TCC forms) and jump the editor to the position; failed builds also light up the Phase 9 squiggles. **Interactive runs happen on a real PTY** (reusing PtyNative/PtySession: per-prompt output, one input per scanf, echo, no timeout — D9; piped fallback retained); one-tap **Add missing ;** Apply fix under fixable errors (D6); honest timeout wording + always-visible Open-in-Terminal (D7); panel input row + terminal escape kept (D8). New: `ExecutionRunner`, `OutputLineParser`, `OutputPanelView`, `InteractiveRunSession` (+`PtyLineBuffer`, `decodeExitStatus`); `TerminalHandoff.compileParts`/`projectRunParts`; ~30 new/updated host unit tests (CI executes them). Design decisions D1–D9 and the device recipe: `docs/chat-phase11/PART_11_OUTPUT.md` §6. Legacy `runCode`/`CompilerService` in-editor pipeline removed (D1: editor RUN now matches the terminal's `cc`; the Settings "Compiler Engine" picker's editor effect is superseded — flagged as a follow-up).
    See [`chat-phase11/`](chat-phase11/).

15. **Phase 12 — Multi-Language Support, Python & Code Intelligence** ✅ **COMPLETE, DEVICE-ACCEPTED & MERGED — PR #30 merged to `main` at `260d8b6` (2026-08-30 17:21 UTC; owner's explicit command).** Originally IMPLEMENTED, CI-VERIFIED & REPOSITORY-PUBLISHED on `arena/01a05221-codec` — repo-build config + all client work committed; host repo tests green (85 OK, 4 gpg skips); **`Build APK` CI green** (`33308137225`/`33314362040`); **`[repo-build]` DONE — build `33314588441` (aarch64+x86_64) → publish `33320104745` (main, `source_run_id`); catalog verified live (`python` 3.14.6-1, `python-pip` 26.2.1, `python-tkinter` absent)**. **Device 2026-08-30 — FULL §4 recipe PASSED:** `pkg install -y python` works; Python keywords highlighted + `def ` autocomplete popup TAB-insert work ("Both working"); python RUN works ("Now python is solved"); C active-file RUN works ("Worked properly") — device-found run bugs fixed (`e4c5d48` `.py`→`.py.c` naming, `9bfe216` project RUN always built `main.c`).
    - **Repo build:** `python` + `python-pip` added to `CODEC_REPOSITORY_PACKAGES` (`properties.codec.sh`; python 3.14.6 rev 1 + python-pip 26.2.1 exist at the pinned termux-packages ref). Narrow fail-loud recipe override (same pattern as gitk/git-gui): `python-tkinter` subpackage excluded for CodeC arches and `tk` removed from python's build-depends — tk pulls the whole X11 closure (fontconfig/libx11/libxft/libxss/tcl) solely for Tkinter. Bootstrap seed/manager roots unchanged (published bootstrap archives stay byte-identical); python installs on demand via `pkg install -y python`.
    - **Highlighter:** `MultiLanguageSyntaxHighlighter.kt` — `LanguageType` from the file extension (C/C++, Python, JS/TS, HTML/CSS, JSON, Shell, Markdown, Text), single-pass ordered-alternation tokenizer (comments/strings swallow their content before numbers/keywords/functions/operators; C/C++ `#directives` share the keyword color), theme-aware colors. `CSyntaxVisualTransformation` → `SyntaxVisualTransformation` (decoration layers unchanged, now language-aware; default C keeps the old look). EditorScreen + TemplatesScreen updated.
    - **Autocomplete:** `CodeCompletionEngine.kt` — buffer identifier scanning + per-language snippet presets, word-aware matching (`mai` → `int main(void) {`, `inc` → `#include`), capped at 8. Floating popup anchored at the cursor rect in `EditorScreen`: TAB/ENTER insert, ↑/↓ cycle, ESC dismisses until next edit, tap to insert.
    - **Python run path:** single-file RUN ▶ for `.py` has no compile step (`python3 <file>` straight to RUNNING; `interpretedParts`/`interpretedRunCommand`); project-tree "Run in terminal" runs `.py` with python3. Project preset pre-existed (`ProjectConfig` `{"type":"python","run":"python3 main.py","build":""}`; ModuleCatalog python entry).
    - **Tests:** `SyntaxHighlighterTest.kt` (12), `CodeCompletionTest.kt` (12), `TerminalHandoffTest` python additions (3) — pure Kotlin, run by CI.
    See [`chat-phase12/`](chat-phase12/).

16. **Phase 13 — GitHub & Git Version Control Integration** ✅ **COMPLETE & DEVICE-ACCEPTED (2026-08-31, `arena/01a053b3-codec`, on the owner's "Start phase 13").** `Build APK` `33326161083` green incl. 37 new host tests (first round caught two real bugs — diff new-side numbering + fake-git `--no-pager` dispatch — fixed `501b6f2`). Device §7 recipe FULLY PASSED: clone from URL, M/?? badges, inline diff, "Committed & pushed ✓" to a scratch repo, PULL round trip (HEAD == origin/main), and all security spot-checks clean (no token in terminal env, `.git/config`, or Logs — the redaction path was exercised by an owner-induced 403 with a write-less token). Acceptance record: [`chat-phase13/PART_13_GITHUB.md` §8](chat-phase13/PART_13_GITHUB.md). **Merged:** PR #31 @ `006515a` (2026-08-30).**
    Visual GitHub/Git integration, client-only: `GitManager.kt` (Android-free engine over the packaged `$PREFIX/bin/git` — argv-list ProcessBuilder, no shell; `git status --porcelain=v1 -b` parser; 60 s/300 s timeouts), secret-safe auth (`GIT_ASKPASS` script over a per-child `CODEC_GIT_TOKEN` env — never argv/.git-config/terminal env; `GitRedactor` scrubs every output line; token stored app-private in DataStore), `GitDiff.kt` (Kotlin LCS line diff), **Source Control bottom sheet** (`GitControlView.kt` + `GitControlViewModel`: branch + ahead/behind, M/A/D/R/?? badges, tap-to-diff dialog, PULL, one-tap COMMIT & PUSH with honest per-step results), **Files → ⋮ → Clone from GitHub** (unique project name, Phase-8 import flow, partial-clone cleanup, https-only URLs), **Settings → GitHub Account card** (masked PAT + username + commit identity, SAVE/DISCONNECT). 37 new host tests (`GitStatusParserTest`, `DiffEngineTest`, `GitManagerTest` — the last runs a fake `git` script through real processes to prove argv/env/redaction). Design decisions D1–D7 + device recipe: [`chat-phase13/PART_13_GITHUB.md`](chat-phase13/PART_13_GITHUB.md) §6–§7.
    See [`chat-phase13/`](chat-phase13/).

18. **Phase 19 — Terminal Parity (Termux-quality terminal) + Unicode/protocol parity** 🚧 **IMPLEMENTED & CI-GREEN (2026-08-31, `arena/01a056aa-codec`, owner: "Ok start phase 19 … also try to find other things that Termux better than CodeC terminal and fix it").** `Build APK` `33371114549` green (assemble + `testDebugUnitTest` + `lintDebug`); **device recipes pending (owner)** — per-part recipes in `chat-phase19/PART_19_*.md` §5. Two red CI rounds caught real defects before any device run: the curated zero-width table missed key Indic spacing vowel signs (Bengali ি/ী, Devanagari ा/ि, Tamil ி, Gurmukhi ਾ, Kannada ಾ …) — expanded from the Unicode Mn/Mc categories (`39bd3e2`) — plus 9 test-trace bugs (`ee1c054`).
    - **19.3 live output** — `RenderPump` (frame-paced emitter, ~60 fps, conflated dirty-signal channel; parks idle, coalesces bursts, guarantees intermediate frames): `TerminalSession`'s reader marks dirty instead of per-chunk `StateFlow` publishes (the conflation that made downloads "print everything at the end"); immediate publishes kept for resize/notice/reset/exit. 6 `runTest` tests. View hot paths stopped concatenating 2000-row lists per frame (`lineAt` index helper).
    - **19.2 crisp rendering** — `CellMetrics`: INTEGER cell width/height for both settled (PTY) and active (pinch) grids, later upgraded from ceil to fitSizeToGrid (see device round 1 below); every glyph/background/selection/cursor origin is an exact integer multiple; real-bold `boldPaint` replaces `isFakeBoldText`; per-glyph squeeze-to-slot guard for fallback-font advances; `letterSpacing=0`/`textScaleX=1`/subpixel. 4 tests.
    - **19.4 Unicode widths (parity gap #1)** — `CharWidth` from UAX #11 + combining categories: CJK/emoji = lead+continuation cells (2 columns, never split at wrap or reflow boundaries), Bengali/Devanagari/Tamil… vowel signs (Mn/**Mc**) combine into their base cell and render as one shaped cluster; astral glyphs keep `TerminalLine.text` one-char-per-column via the `clusters` map; copy/selection joins pairs + expands clusters (`readableText`/`selectedText`). 11 tests (`CharWidthTest`, `TerminalUnicodeTest`).
    - **19.1 reflow** — `Row(cells, wrapped)` storage; `Reflow` (pure) rejoins soft-wrapped scrollback+screen into logical lines and re-splits at the new width (wide pairs never split, trailing default blanks trimmed), maps the cursor through; rows-only resize restores from / overflows into scrollback with the cursor following its content; alt screen stays a rectangular copy. 14 tests (`ReflowTest`).
    - **19.5 protocol & interaction parity (gaps #2–5)** — DA1 (`CSI c`→`ESC[?6c`, VT102 class) + DA2 (`CSI > c`→`ESC[>0;100;0c`, CodeC self-identity); **OSC 52 clipboard WRITE only** (pure-Kotlin `Base64Codec`, read queries refused, 100k cap, OSC payload cap 1024→8192) wired emulator→session→ViewModel→Android clipboard; **xterm mouse reporting** (9/1000/1002/1003/1006/1007, SGR + legacy encoders, `MouseEncoding`) with Termux-style touch mapping (tap=click, swipe=wheel) so htop/vim/less are touch-drivable; hardware Ctrl+arrows (`CSI 1;5A..D`); long-press menu gains Copy All / Share / Reset. 16 tests (`MouseEncodingTest`, `TerminalProtocolTest`).
    - **Clean-room:** all sequences re-implemented from public specs (xterm ctlseqs, vt100.net, UAX #11, RFC 4648) — no Termux/other terminal source. Invariants kept: client-only, no PTY/JNI changes, Phase 7 routing intact, nothing in `$PREFIX/bin`, no `[repo-build]`.
    - **Device round 1 (2026-08-31, owner transcript):** ONE regression — *"letters have a noticeable gap between them"* — 19.2's `ceil(advance)` cell added up to 1px of tracking per letter. **Fixed same day:** `CellMetrics.fitSizeToGrid()` nudges the text size (<1% in practice, 8% guard) until the monospace advance IS a whole pixel, so the integer cell equals the font's own advance (crisp AND tight; per-column placement caps error at 0.05px/glyph so the overlap bug cannot return); view fits settled+active paints via `fitGridPaint()`, bold copies the fitted paint; +6 host tests (10 total). Postmortem: `chat-phase19/PART_19_2_RENDERING.md` §7.1. Also fixed the recipes (round-1's were unusable on-device: multi-line `python3 -c` paste → the owner typed a literal `…` → `SyntaxError`; `/usr/bin` doesn't exist in the CodeC userland → `$PREFIX/bin`) — all round-2 recipes are single-line copy-pasteable. Round-1 positives from the transcript: soft-wrap of long lines ✓ (error message + long command wrapped), Bengali/CJK/emoji echo ran without crash (visual quality pending round 2 after the gap fix). Still unverified on device: 19.3 live cadence (recipe never ran), 19.1 pinch reflow, 19.4 cluster rendering, 19.5 OSC 52 paste-back + htop touch.
    - **Device round 2 (2026-08-31, owner screenshots + answers):** still gaps/thin/airy/bigger vs Termux — objective `stty size`: **CodeC 32×60, Termux 39×71** (~44% more text in Termux). Root causes: default 14sp too big for a terminal; stock Droid Sans Mono light+wide-clearance; no row-pitch control. **Fixed:** default **12sp** (60×14/12 = 70 cols), **bundled JetBrains Mono** Medium (normal) + Bold (ANSI bold) under SIL OFL (notice in assets/licenses; ~544 KB), and `CellMetrics.TERMINAL_LINE_FACTOR = 0.9` (JBM ships a roomy 1.32em line — verified by parsing the TTF hhea — tightened to ~1.19em ≈ 2.0× the 0.6em advance). Predicted ~70 cols × 36–38 rows. +2 `CellMetricsTest` cases. Postmortem: `chat-phase19/PART_19_2_RENDERING.md` §7.2. Round 3 = objective `stty size` check + side-by-side.
    - **Device round 4 (2026-08-31): PASS — owner's final word: "All ok now".** Phase 19 is DEVICE-ACCEPTED end-to-end (CI `33378705305` on `f2a60b1`). **PR #34 was merged to `main` @ `b869ce6` (2026-08-31 09:55Z).**
    - **Device round 3 (2026-08-31, owner: "terminal feels lagging, not smooth scrolling, something keyboard not pop up") — 4 root causes fixed:** (1) per-glyph `measureText`+`drawText` for ~2600 cells/frame → **run-batched drawing** (snapped advance == cellW makes one `drawText` per plain span; cluster/wide/non-ASCII keep the individual path; `GlyphSpans` + 6 tests); (2) tap detector keyed on `snapshot.generation` restarted every output frame → taps eaten mid-stream (the missing keyboard!); scroll detector keyed on `scrollbackCount` restarted when history grew mid-drag → both now keyed on geometry only, reading `latestSnapshot` at event time; (3) a per-update `topRow=0` effect rubber-banded the scrollback during output (removed — typed input still jumps to live) + whole-row drag jumps → `ScrollMath` sub-row remainder with canvas `translate(0, -scrollSubPx)` (+6 tests); (4) `showIme()` one-shot → retry loop + `onWindowFocusChanged` re-show, `restartInput` removed. Postmortem: `chat-phase19/PART_19_3_LIVE_OUTPUT.md` §9.
    See [`chat-phase19/`](chat-phase19/) (README + 5 part docs with research notes, D-decisions, and device recipes).

17. **Phase 14 — Mixed-Language, Server WebViews & Long-Tail Ecosystem** 🚧 **IMPLEMENTED & CI-GREEN (2026-08-31, `arena/01a05421-codec`, on the owner's \"You have to work on phase 14\").** Client-only (`Build APK` `33352164172` — assemble + `testDebugUnitTest` + `lintDebug`; four CI-caught bugs fixed: `const val` interpolation, `${Q}` identifier parse, missing `assertTrue` import, and three test-logic bugs — see the record). **Device recipe pending at the time; MERGED via PR #32 @ `0b591e2` (2026-08-31) — the §5 device round was never given a dedicated pass.** [`chat-phase14/PART_14_IMPLEMENTATION.md`](chat-phase14/PART_14_IMPLEMENTATION.md) §5.
    - **Server pipeline:** `ServerRunner.kt` — long-lived background process (reuses `ShellBootstrap` env, merged output streaming, no timeout, Stop; `awaitClose` kills the child); `ServerPortDetector` — bind-line patterns only (Flask `* Running on http://…`, Uvicorn, `Serving HTTP on … port …`, `CodeC server listening on http://…`, generic `listening on`; `0.0.0.0` rewritten to `127.0.0.1`; URLs inside rendered content never match), 20 s readiness warning.
    - **Config & presets:** `ProjectConfig` v1 + optional `port`/`previewUrl` (back-compat; `previewUrl` falls back to `http://127.0.0.1:<port>`); presets `python-flask` (5000, app.py), `python-fastapi` (8000, main.py), `c-microservice` (8080, server.c → `cc server.c -o bin/server`, TCC only); `SERVER_TYPES`.
    - **Scaffolds (`ProjectScaffold`):** Flask/FastAPI run the real framework when installed (`pip install flask` / `fastapi uvicorn`), else a stdlib `http.server` fallback serves the identical pages — works out of the box with Phase-12 `python`; the page is `index.html` read per request (edit → Reload, no restart). C microservice: single-file socket server (no deps). Static web `index.html`, python `main.py`, C starter byte-identical.
    - **Files wizard + bundled demo + Auto:** New Project dialog now has a template picker (C / Python / Static Web / Flask / FastAPI / C microservice) — `ProjectTypes` + `FileManagerViewModel.createProject(type)`; the app also **ships a ready `demo_flask` project** in the Files tab (owner request 2026-08-31 — open it and RUN ▶ straight away; `DemoProjects` one-time seed, never overwrites, `DemoProjectSeedTest` ×4; `ProjectScaffold.writeFiles` is the single scaffold writer). **Auto (detect)** is the wizard's default (owner request — "no selection … just created and run any type"): no type choice at creation; RUN ▶ infers app.py→Flask, server.c→C microservice, main.py→FastAPI (iff imports) / Python, *.html→static Web, main.c→C (`ProjectRunDetector`, pure; `ProjectRunDetectorTest` ×13 + E2E auto→Flask; each RUN re-detects).
    - **RUN ▶ & Web Preview:** server projects build (if any) then run in the background; on the bind line the Output Panel summary shows the URL, **auto-opens Web Preview** on it (`Preview` route gains `url`), Output Panel gets an **Open Preview** action, stdin row hidden for servers; Web Preview live mode shows a **● live address bar** and watches the project's `index.html` (auto-reload); static preview unchanged.
    - **Tests:** `ServerPortDetectorTest` (10), `ServerRunnerTest` (7, real `/bin/sh` processes: ready/stream/exit/failure/timeout-warning/stop), `ProjectScaffoldTest` (7), `ProjectConfigTest` (+7: presets, round-trip, legacy JSON, URL fallback), and `ServerScaffoldE2ETest` (3 — green on CI `33355693242`): builds/runs each preset via the exact `ProjectConfig` commands through `ServerRunner`, fetches the page over loopback HTTP, edits `index.html` and re-fetches (hot-read, no restart), stops cleanly.
    - **Invariants:** no `.` on PATH; TCC `-o` last; `cc`/bash untouched; nothing in `$PREFIX/bin`; no official Termux packages; **no `[repo-build]`** (Flask/FastAPI are pip packages; C server = embedded TCC). Design D1–D8 + follow-ups: `PART_14_IMPLEMENTATION.md` §2.

19. **Phase 15 — Projects Hub & Unified Import (Spck clone, 15 of 15–17)** 🚧 **IMPLEMENTED & awaiting CI/device (2026-08-31, `arena/01a05743-codec`, on the owner's "Start phase 15").** The Files tab became Spck's **Projects Hub**: card list (type square from `ProjectConfig`/auto-detect, `⌥ branch · N files · relative age`, yellow `M` badge for uncommitted work), single-select filter chips (All/Git/C/Python/Web), inline name search, and ONE `+` bottom sheet — **New Project / Clone Git Repository / Import ZIP / Open Folder** — the unified import entry the owner asked for. Per-project `⋮`: Open, Source Control / Pull / Switch Branch / Copy remote URL (git repos), Rename, Export ZIP, Delete. New Clone dialog: URL → auto project name → Advanced (branch free-text or `ls-remote` chips, shallow `--depth 1` default on) + token hint → Settings. Bottom nav is now Home · **Projects** · Editor · Terminal · Settings (Packages stays on Home). New pure engine `ui/projects/ProjectsHub.kt` (+`ProjectHubStats` scan) and `FileManagerViewModel.hubEntries` (IO-built; branch via `.git/HEAD`, no git process; `git status` only when git installed — D3); `GitManager.clone` extended (defaulted `shallow`/`branch`, Phase 13 argv unchanged — D5) + `listRemoteBranches`. `[client-only]`, clean-room (Spck behavior mirrored, zero code/assets copied). Tests: `ProjectsHubTest` ×13 + `GitManagerTest` ×5 new (fake-git argv proofs incl. reject-before-exec). CI round 1 caught a real chip-membership bug (first-arm `when`) — fixed, round 2 green (`33383946165` @ `3ba2d58`). Decisions D1–D9 + research notes: `docs/chat-phase15/PART_15_PROJECTS_HUB.md` §6. **Next: owner runs the §4 device recipe on the artifact `CodeC-IDE`.**
20. **Phase 16 — Spck-style Editor Shell (Spck clone, 16 of 15–17)** 🚧 **IMPLEMENTED & CI-GREEN (2026-08-31, `arena/01a05743-codec`, run `33388547817`; device recipe pending) — on the owner's "Start phase 16".** The editor got the Spck shell, native-Compose-only: **top bar** = ☰ + tab strip IN THE TITLE (bold+underline active, ● dirty, ✕ when >1, long-press Close others/all/Copy path) + 🔍 + ⋮ overflow (Save/Save all/Rename/Reload/Format/Go to line/Find/Diagnostics/LF⇄CRLF/launch-default set-clear/Run in terminal/Save to project/Share/Close file/Clear diagnostics) + unchanged green ▶ RUN; **`ModalNavigationDrawer`** replaces the files sheet — project header (name, `⌥ branch` chip, source-control badge → `GitControlSheet`), tree toolbar (New File/New Folder/Refresh/Collapse⇄Expand All), the Phase 8 tree with git M/A/D/? letters + selected highlight + blue ⚡ launch-default marker, per-row menu (Open/Rename/Delete/New file-folder here/Run in terminal/Launch/Copy path), footer Source Control · Switch Branch (Phase 17 note) · Editor Settings; **snippet keys row** (`EditorKeysRow` + pure `EditorKeySet`: spec's `TAB { } ( ) ; < > / = " '` + arrows, per-language tails, custom-snippet data model via `editor_custom_snippets` — editing UI recorded follow-up) docked ABOVE the status bar with a chevron hide-toggle; **readability** = two-finger pinch → `FontSizeZoom` → the SAME `setFontSize` store as Settings (stepper/family/wrap already existed — verified, no new state); **Launch Default** = `ProjectConfig.launchDefault` (omitted-when-null, Phase-14 `port` contract; `LaunchDefaultTest`), preview targeting active-html → default → web entry; **status bar** gained language + tappable LF/CRLF chips, errors badge taps to the first error (pure `EditorShellUi`). Line endings: LF buffers, majority-rule detect, re-expand on save, immediate rewrite on toggle (`LineEndingsTest`). New engines host-tested: `EditorKeySetTest` ×13, `LineEndingsTest` ×5, `LaunchDefaultTest` ×5, `FileTreeCollapseTest` ×5, `ProjectsHubTest` +1 (badge map). Decisions D1–D11 + research notes: `docs/chat-phase15/PART_16_EDITOR_SHELL.md` §6. `[client-only]`, clean-room, no engine rewrites (Phase 9/11/12/14 pipelines untouched; undo/redo/format/find/Output all preserved). CI rounds: 3 red-for-cause (see doc §6) →
green on `a1f73fa`. **Device round 1 (2026-08-31):** owner reported python
`__pycache__` being offered to git — fixed with the repo-local
`.git/info/exclude` auto-append (`PythonCacheIgnore`, 10 new host tests;
doc §6). **Next: owner re-installs the fresh artifact and continues the §4
recipe (steps 1–8).**
21. **Phases 15/16 — Device round 2: mockup-exact re-skin (2026-08-31, `arena/01a057e0-codec`).** Owner: "read the phase 15 and 16 and the makeup images for the ui. I want exactly same ui." The `docs/chat-phase15/mockups/*.png` became the fidelity bar and every screen was re-skinned against them: Projects Hub cards/chips, the `+` import sheet, the clone dialog (labels above fields, QR trailing icon, Advanced chevron, branch dropdown, filled CLONE), the editor (top bar exactly `☰ + tabs + 🔍 + ⋮ + ▶ RUN` — second toolbar row gone, undo/redo + keys-row toggle in overflow; 3dp tab underline; gutter hairline divider; ghost-green RUN), the keys row keycaps (40dp/10dp radius/hairline), the status bar (`Ln x, Col y · UTF-8 · <lang> · Spaces: n`), the drawer (branch glyph + chip, 4-column tree toolbar, typed file icons, purple selection, footer Source Control · Switch Branch), and the mockup's flat **five-tab bar (Home · Projects · Editor · Terminal · Settings)** — the owner's round-2 word overriding round-1 "keep six tabs" (Packages then moved to a Home-screen button; "Term" renamed "Terminal"). The Phase 17 SC sheet was re-skinned too (outlined branch chip, multiline message, full-width COMMIT & PUSH, per-file **+/− stage toggle** — new `GitManager.stageFile`/`unstageFile`, `add -- <path>` / `reset -- <path>`, +2 argv-proof tests). Hand-drawn clean-room glyphs: `ui/components/SpckIcons.kt` (git-branch, QR, zip, clone, file-plus, folder, Python/HTML two-tone marks, book, file, collapse-all, +−, globe — zero copied assets). Records: `docs/chat-phase15/PART_15_PROJECTS_HUB.md` (device round 2 section), `PART_16_EDITOR_SHELL.md` (device round 2 section).
22. **Phases 15/16 — Vector-API compile saga resolved & branch CI-green (2026-08-31, `253201e`).** The re-skin's new glyphs broke the build: the resolved `ui-graphics` (the BOM's version number misleads — dependency resolution lands on a far newer 2026-era Compose) dropped the old string-path `ImageVector.Builder.addPath(pathData: String, color=…, strokeWidth=…)` API entirely. With no local Java/SDK and CI-log blobs unreachable from the sandbox, the API was pinned **using CI itself as the compiler oracle**: three probe rounds (`ApiProbe.kt`, deleted after) established via annotations that `addPath` takes `pathData: List<PathNode>` with `fill: Brush?`/`stroke: Brush?`/`strokeLineWidth`/`strokeLineCap`/`strokeLineJoin`; `PathNode` lives in `androidx.compose.ui.graphics.vector` (sealed `MoveTo/LineTo/HorizontalTo/VerticalTo/CurveTo/QuadTo/ArcTo/Close`); `Color` is NOT a `Brush` (wrap in `SolidColor`); `androidx.compose.ui.graphics.drawscope.Stroke` exists but is for `DrawScope` styles, not vectors; `DrawScope.drawLine`'s endpoint parameter is `end` (not `stop`); and `padding(WindowInsets)` is gone (use `navigationBarsPadding()`). The API was then cross-checked against the `androidx/androidx` GitHub mirror (the real compose source). All 13 `SpckIcons` glyphs were rewritten as PathNode lists (with `circle`/`rect`/`strokePath`/`fillPath` helpers); the editor gutter `drawLine` and the bottom-bar inset fixed the same way. `Build APK` green at `253201e` (run `33402899023`) and `4fb4a21` (run `33403600667`). The verified API facts are recorded in the `SpckIcons.kt` header — do not reintroduce string-path `fillColor`/`Stroke(width=…)` calls.
23. **Phases 15/16 — Device round 3: bar, launch-restore, autosave, git hygiene, RUN=preview (2026-08-31, `4db8c72`, merged to `main`).** Owner: "Remove the home botton and undo the packages install option and the terminal will be in the middle and when user open app 1st it will open where the use left in editor and set editor as auto save. And the output files like .out for c and other files that are not need to upload in git also come at push. And make the run botton even for html no extra preview botton." Delivered in one commit:
    - **Bottom bar:** Home tab + `HomeScreen` deleted; the Packages tab restored to the bar (undoing round 2's demotion to a Home-screen button — "undo the packages install option" read as undoing that move, which also makes Terminal land dead-center); order is **Projects · Editor · Terminal · Packages · Settings** with Terminal exactly in the middle.
    - **Open where I left off:** `ui/projects/EditorLaunchState.kt` persists the last opened project file on every open/tab-switch/close (app-private prefs; stale project/file falls back); `MainApp`'s start destination is that file's editor route (first launch / stale entry → Projects hub).
    - **Editor autosave:** 2 s debounced `saveFile` after any buffer mutation (typing, undo/redo, code actions) + an immediate `flushAutoSave()` when the editor composable disposes; silent on success, dirty-dot self-clears.
    - **Build outputs stay out of git:** `ui/projects/BuildArtifactIgnore.kt` (same repo-local `.git/info/exclude` policy as `PythonCacheIgnore`, user's `.gitignore` untouched) covers `*.out/*.o/*.obj/*.exe/*.class`, `bin/`, `dist/`, `build/`, `target/`, `node_modules/`, `.venv/`, `venv/` — applied at git refresh, before COMMIT & PUSH's `git add -A`, at project open (hub scan + drawer meta) and every RUN; **already-tracked artifacts are untracked** (`GitManager.trackedFiles` → `git rm -f --cached` for matching paths, leaving them on disk) so a previously pushed `a.out` stops traveling at the next push.
    - **RUN ▶ is the HTML preview:** an open `.html` file makes RUN ▶ save the buffer and open Web Preview (web projects still open their launch-default page; C/Python/server paths unchanged); the separate "Preview" overflow item is deleted.
    `Build APK` green at `4db8c72` (run `33406221777`). **Owner then commanded: "Create a pr than Merge in main" — the session branch was PR'd and merged to `main` the same day** (verify the merge sha with `git log`).
24. **Phase 17 remainder — Switch Branch + merge conflicts (2026-08-31, `arena/01a05878-codec`).** On the owner's "Phase 17 remainder — Switch Branch + merge-conflict UI". Closes the two gaps the re-skin left (the drawer footer and the SC branch chip both toasted "coming soon"):
    - **Engine (pure, host-tested):** new `ui/projects/GitBranchOps.kt` — conflict detection from the seven documented porcelain unmerged pairs (`DD AU UD UA DU AA UU`; `AA`/`DD` carry no `U`, and `AD` is *not* a conflict), `git branch --all --no-color` parsing (remote `origin/HEAD -> …` symrefs and detached-HEAD rows dropped), `git stash list` parsing (`WIP on <b>: …` vs `On <b>: …`), and the `codec-switch: <branch>` marker that lets CodeC recognise — and auto-restore — the stash it made on the user's behalf.
    - **`GitManager`:** `listBranches`, `currentBranch`, `checkout`, `checkoutNew`, `checkoutRemote` (`-b <name> --track <remote>`, never detaching HEAD), `stashPush`/`stashPop`/`stashList`, and the `switchBranch` orchestration: dirty → `stash push -u -m codec-switch: <from>` → checkout; **pop straight back if the checkout fails**; then auto-restore only a CodeC-marked stash belonging to the branch we landed on. All argv-only through the Phase 13 private env. Conflict test note: `GitFileChange.isConflict` now makes `AA`/`DD` purple `U` too.
    - **UI:** new `BranchSwitchSheet` dialog (local + remote branches, bonus **New branch…**, and the "stash my uncommitted changes — restored when you come back" checkbox) reachable from the SC branch chip, the editor drawer footer and the Projects card ⋮ (both "coming soon" toasts and their strings are gone); the SC sheet gained a **Conflicts** group above Changes with per-file **Mark Resolved** (`git add -- <path>`), and **COMMIT & PUSH is blocked with an explanation** while any conflict is open (Spck's rule); the drawer tree shows the purple `U`; the Projects card ⋮ gained **Push Changes**.
    - **Tests:** `GitBranchOpsTest` ×17 + `GitBranchManagerTest` ×16 (fake-git argv proofs of the stash→checkout→restore ordering, pop-back on failure, "foreign stash untouched", clean-tree no-op, and rejection of an option-shaped branch name before git runs).
    Decisions D1–D8 + research notes with sources: `docs/chat-phase15/PART_17_SOURCE_CONTROL.md` §6.1. **CI green (`Build APK` `33417811422` @ `3a2846f`) after three for-cause red rounds** — (1) `Icons.Default.CloudUpload` no longer exists in the resolved icon set, (2) `Icons.Default.<name>` needs the matching `filled.<name>` extension import, (3) a double-escaped `\n` in the new fake-git harness (my test bug, never the product). **Remaining gate: the owner's §4 device recipe steps 5–8** (clean-room throughout; client-only).
25. **Phase 17 device round 1 — two real push bugs (2026-08-31, `arena/01a05878-codec`).** Owner's first run of the Switch Branch work: (a) **"The current branch test has no upstream branch"** — a branch created in the app has no tracking branch, so commit-and-push could never work. Fixed by `GitManager.push(root, setUpstream)` running `git push --set-upstream <remote> HEAD` (remote from `git remote`, `origin` fallback) and `pushHandlingUpstream()` choosing the form from the status branch line (`## test` → publish, `## main...origin/main` → plain push). (b) **"If something upload failed it doesn't return the changes in app — it stay updated but never go to github"** — a successful commit clears the change list, so a FAILED push looked identical to a successful one. Fixed by making the state honest: *"Committed locally ✓ — NOT pushed: \<reason\>"*, a sticky failure text, an amber **"N commit(s) not pushed yet"** row with a **PUSH** retry (also shown for a never-published branch, which has no `ahead` figure at all), an amber **↑N** badge on the Projects card, and a status re-read after failures so the ahead count is real. CI green `33421815293` @ `1c01f84` with +5 fake-git argv proofs. Record: `docs/chat-phase15/PART_17_SOURCE_CONTROL.md` §6.2.
26. **Phase 18 — CodeCApi Device Capabilities (2026-09-01, `arena/01a05b12-codec`).** On the owner's "Start 18": five device bridges on the existing OSC 1337 CodeCApi pipe — `codec-battery` (sticky `ACTION_BATTERY_CHANGED` → JSON), `codec-sensor` (accelerometer/gyroscope/light one-sample), `codec-tts` (app-lifetime TextToSpeech), `codec-camera` (runtime CAMERA park/resume — the Phase 4.8 pattern — + `TakePicture` via FileProvider; sanitized names under `$PREFIX/tmp/codec-api/camera/`), `codec-intent` (implicit view/dial/send + URI-scheme allow-list; never an explicit component). `BOOTSTRAP_VERSION` 26 → 27; manifest `CAMERA` + `uses-feature required=false` (one lint-red round fixed) + TTS/IMAGE_CAPTURE queries. Android-free `DeviceApiOps` keeps the core host-testable (`CodecApiBridgeFullTest` ×22 + protocol/script additions). **Owner's §4 device recipe PASSED 2026-09-01 — battery JSON, accelerometer sample, TTS audio, maps intent, and the full camera dialog → `CAPTURING:` → photo → `OK:<path>` chain.** Record: `docs/chat-phase18/PART_18_CODEAPI.md` §5 (D1–D9) + §5.6 (transcript). **Merged to `main` via PR #38 (2026-09-01, owner's "Create pr and marge").**
27. **Web Preview "File not found" after an in-editor folder switch (2026-09-01, `d49ac47`, CI `33471103959`).** Owner: "The HTML is not loading showing file not found". Root cause: the editor's preview navigation used the **Nav route's `projectName` argument**, which goes stale after Phase 9.2's in-editor *Open folder* picker or Phase 9.1's *Save to project…* — so an imported HTML in `CodeC/projects/<imported>/` was looked up in the projects root (or the previously open project) and the preview reported `File not found: <name>`. Fix: thread the **authoritative project** (VM `currentProject` / drawer `entry.projectName`, and `info.name` for server/auto-web plans) through `onOpenPreview`/`onOpenPreviewUrl`, the `EditorViewModel` server/web handlers, and the `isWebProject`/`webDefaultEntryOrNull`/`projectRunCommandOrNull` helpers. Record + repro: `docs/chat-phase9/PART_9_IMPLEMENTATION.md` (Phase 9.2 follow-up).
28. **Future-update mode (2026-09-01).** Owner: "i will not do anything with phase maybe and merge with main". The phase ceremony is retired; `rule.md` (repo root) is now the operating manual for all post-Phase-18 work — verify → evidence → host-testable fix + tests → docs → CI green → report → **owner merges to `main`** (agent never opens/merges a PR without the literal command, per the standing rule; `rule.md` §3 records the exact phrase that would change that).
29. **New phases A/B/C/D/E spec'd (2026-09-01, `arena/01a05c74-codec`).** Owner: "remove tcc and use gcc like python and extend it's scope with other languages as per need — make the plan future proof" + "take ideas 3, 4, 5 now (the feasible / low-cost ones)". Full research document written (`docs/RESEARCH_NEXT_PHASES.md`); per-phase structured docs created in `docs/chat-phase20/` through `docs/chat-phase24/` following the exact same pattern as completed phases (README + PART_*.md files with context, architectural design, implementation steps, exit condition, device recipe, design decisions, research notes). **No code written yet — this is the planning/design commit.** Phase summaries:
    - **Phase 22** (editor smoothness + IME-anchored keys): A.1 debounced off-thread highlight + scroll decoupling; A.2 IME-pinned language-adaptive key strip; A.3 `imePadding()` + caret visibility. `docs/chat-phase22/`.
    - **Phase 23** (inline PTY input): B.1 remove `OutputInputRow`, add inline `BasicTextField` at the bottom of `OutputPanelView`; B.2 context-aware `KeysContext` (editor vs. interactive-run keys in the strip). `docs/chat-phase23/`.
    - **Phase 20** (package toolchain): C.1 add `gcc`/`clang`/`nodejs`/`php`/`ruby`/`lua54` to `CODEC_REPOSITORY_PACKAGES` (CI `[repo-build]`); C.2 optional Go/Rust behind `[repo-build-heavy]` guard. `docs/chat-phase20/`.
    - **Phase 21** (retire TCC, generic run model): D.1 `LanguageRunProfile` + `LanguageRegistry` (12 languages, host-testable); D.2 auto-install gate (prompt + `pkg install` before first RUN); D.3 device acceptance; D.4 delete `assets/tcc/`, `EmbeddedCompiler`, `build-tcc.sh` — APK shrinks. `docs/chat-phase21/`.
    - **Phase 24** (polish batch): E.1 formatter menu; E.2 background-run notification; E.3 hardware shortcuts; E.4 ZIP share; E.5 tablet two-pane; E.6 test-runner UI; E.7 "Open with CodeC" intent; E.8 adaptive theme; E.9 per-project `.codec.json` override. `docs/chat-phase24/`.
    Updated: `rule.md` §6 (TCC invariant retirement note), `rule.md` §9 (new phases), `prompt.md` (new phases block), `docs/NEXT_STEPS.md` (head state line). Commit `37096a1` (research doc) + this commit on `arena/01a05c74-codec`. **Merged to `main` via PR #40 (2026-09-01) → `main` = `54ae06a`** (PR #39 — the git branch-publishing + clear-error fix from `arena/01a05b6c-codec` — landed just before it, closing the two "bug-wait mode" fixes recorded in items at the top of this file and in NEXT_STEPS).
30. **Phase 20.1 — package toolchain round 4 (2026-09-01, `arena/01a05cb9-codec`, owner: "Phase 20 start").** CI/package-repo side of the compiler redesign — six new roots in `CODEC_REPOSITORY_PACKAGES`: **`libllvm`** (LLVM/Clang 21.1.8 — the actual compiler), `nodejs` 26.4.0-1, `npm` 11.19.0, `php` 8.5.1 (trimmed), `ruby` 3.4.1-2, `lua54` 5.4.8-10. Research against the live pinned tree invalidated two plan assumptions: there is **no `packages/gcc` or `packages/clang` recipe** at the pinned ref (clang is a `libllvm` subpackage whose include list already ships `bin/gcc`/`bin/g++`/`bin/c++`/`bin/cpp` driver symlinks — and `bin/cc`, which **CodeC strips** because `$PREFIX/bin/cc` is the app's own TCC frontend; invariant preserved, Phase 21.4 will revisit), and **npm was split out of nodejs** upstream (25.3.0-1) so it is its own root. New `apply-recipe-overrides.sh` blocks (all fail-loud, idempotent-marker style): clang `bin/cc` strip; nodejs `preinst` + npm `postinst` neutralized (last-definition-wins no-ops, python-pip precedent — maintainer scripts stay forbidden outside the five reviewed alternatives packages); **php trim** (apache/ldap/pgsql/gd configure flags + `postgresql` build-dep removed, `php-apache{,-ldap,-pgsql,-sodium}`/`php-ldap`/`php-pgsql`/`php-gd` subpackages excluded, `termux_step_post_make_install` replaced with a sodium-only twin — otherwise php would drag the apache2/openldap/postgresql/libgd source closures into the round); **lua54** `.alternatives` postinst replaced by plain relative `bin/lua`/`bin/luac` symlinks (repository validator allowlists only coreutils/less/nano/bat/util-linux). Ruby needed nothing. Tests: +10 hermetic cases in `test_recipe_overrides.py`; full repo suite **95 green**. **Updates:** dispatch `33506104710` hit the 360-min ceiling (6h01m) → D10 LLVM trim made permanent; `33544558167` aborted at ~3.5 min on a trim-shape bug (fixtures vs real recipe bytes — fixed in `49d8d81`, proven by real-byte rehearsal); trimmed `33547475854` hit the ceiling AGAIN (6h00m) → **D11: the build job fans out into base/llvm/langs parallel legs** (`CODEC_REPOSITORY_GROUP_*` single source, group-suffixed artifacts, publish-dev pattern-merge, bootstrap only in the base leg, `publish-bootstrap-release.yml` reads `-base`), tripwired end-to-end by new `test_ci_guardrails.py` cases (suite 100 green). Third dispatch `33585242675` proved the split (base legs green, langs running) but failed both llvm legs at VALIDATION: `libcompiler-rt` carries postinst/prerm (upstream subpkg-level `termux_step_create_subpkg_debscripts` — ndk-multilib interop only). D12 neutralizes it (no-op append, same precedent as python-pip); remaining closures audited clean. Fourth dispatch `33598824226`: base+llvm ALL GREEN; both langs legs red on one root — D7's php-gd *exclusion* couldn't stop arch-neutral buildorder resolving its `libgd` dep edge (→ libheif → gdk-pixbuf validator-trip / dead videolan x264 URL). D13 first deleted the seven phantom files — then dispatch `33625141182` showed buildorder validates the WHOLE tree graph (phpmyadmin's php-apache edge orphaned; all 6 legs red in 8 min). D13 revised: neuter in place (strip TERMUX_SUBPKG_DEPENDS + arch-skip + no-op debscripts, keep files). Owner then canceled the full v6 dispatch and directed salvage instead → D14: workflow learned `groups=langs` + `reuse_run_id=33598824226` (merge the 4 green legs' artifacts, rebuild only langs) + a per-arch marker gate (nano/clang/nodejs) so a partial merge can never publish. Dispatch `33639310638`: plan+langs green, publish-dev blocked by the github-pages environment branch allowlist (never hit before — first run to survive to deploy); owner added `arena/01a05cb9-codec` (past sessions' branches already listed) → rerun-failed → **DEV REPO PUBLISHED**. Verified live Packages (aarch64): all 14 names present (LLVM 21.1.8-3 family, nodejs 26.4.0-1, npm 11.19.0, php 8.5.1 trio, ruby 3.4.1-2, lua54 5.4.8-10), lldb/mlir/libpolly absent. Device recipe caught two content bugs CI can't see: bin/cc = clang symlink (unclaimed file swept into MAIN libllvm deb — D15 removes cc from the loop outright) and lua54 shipped no `lua` (D8 wrote to staging, not $TERMUX_PKG_MASSAGEDIR — D16). Reuse hardened to complement-only downloads. Salvage round 2 — run `33669069048` on `fac3ac5`, `groups=llvm,langs` + complement-only reuse of `33598824226`'s base legs — went **GREEN end-to-end in ~3h04m** (all four legs + publish-dev; the complement-only rule held: the reused run's stale llvm/langs artifacts were not merged). Live index re-verified with new digests (libllvm SHA256 `abe38f14…`, lua54 `01cf611c…`). Bootstrap release run `33669089783` green (~68s, mandatory `--ref arena/01a05cb9-codec`): **`userland-v2-dev` refreshed** (aarch64 sha256 `33b2718b…`, x86_64 `bd669950…`) from the untouched base legs. Device re-verify **PASSED 6/6 (2026-09-03)**: same-version `pkg reinstall libllvm lua54` pulled the rebuilt bytes; `lua -v` → Lua 5.4.8 (Lua 5.4 has no `--version` flag — `-v`); the new libllvm deb no longer carries `bin/cc` → after one full app restart `command -v cc` → **tcc 0.9.27**, the app's own frontend restored (D15 verified end-to-end); `gcc $HOME/t.c` → `Hello gcc` (D16 verified). **Phase 20.1 COMPLETE and MERGED to `main` by owner command (2026-09-03).** Bootstrap seed/manager roots untouched → published bootstrap stayed byte-identical in content, refreshed only from the same base artifacts. C.2 ([repo-build-heavy] golang/rust) not started — its commit-message guard can't work on a dispatch-only workflow; design pivot noted in PART_20_2 §6. Record: `docs/chat-phase20/` (README + PART_20_1 §3/§6/§7 as implemented).
31. **Phase 22 — editor smoothness + IME-anchored keys row (2026-09-03, `arena/01a065a0-codec`, owner: "start phase 22").** Owner's signal from the spec: *"the editor smoothness because it not good at touch, feels like stuck, the shortcuts key are not above the keyboard"*. All three parts implemented client-only, CI green `33717680783` @ `f2cee13`.
    - **A.1 (partial) — per-keystroke work removed.** New pure `HighlightedCode` (in `MultiLanguageSyntaxHighlighter.kt`): a tokenized `AnnotatedString` tagged with the exact `(text, theme, language)` it came from, plus `matches(...)`. `EditorViewModel` runs one collector — `combine(codeText, highlightContext)` → `distinctUntilChanged()` → `debounce(80 ms)` → `withContext(Dispatchers.Default) { HighlightedCode.of(...) }` — and publishes `highlighted: StateFlow<HighlightedCode?>`; a result the buffer already moved past is discarded. Instead of the spec's `StaticTransformation`, `SyntaxVisualTransformation` took a fourth `cached:` parameter and **falls back to inline tokenizing when the snapshot is stale** — strictly safer than a static wrapper, since during the 80 ms window text keeps its correct colors and the cache never carries correctness. Plus `EditorDecorations.isEmpty()` (skip `buildAnnotatedString` entirely when there is no current-line tint / bracket / find / diagnostic), `tabViews` lost its `codeText` key, `completionItems` became a `derivedStateOf`, and the gutter string is `remember(lineCount)`d.
    - **A.1 deferred — the scroll model.** Research killed §2.3: at Compose BOM **2024.09.00** (Foundation 1.7) the `scrollState` parameter exists **only on the `TextFieldState` overload**; the `TextFieldValue`/`onValueChange` overload CodeC uses has none. Migrating would rewrite the whole editing pipeline (undo manager, auto-indent, tab buffers, find/replace, quick-fixes all speak `TextFieldValue`) and touch every Phase 9/11/12 invariant. The `verticalScroll` + conditional `horizontalScroll` wrapper is **kept** and recorded as a known limitation; the baseline profile (§2.5) also stays undone (needs an on-device Macrobenchmark run).
    - **A.2 — the keys row now rides the keyboard.** The row's *content* was already right (`EditorKeysRow` was already parameterized per D1, and `EditorKeySet.languageTail()` already adapts to C/C++/Python/JS/Shell/HTML) — only its *position* was wrong. It now renders in one of two places: docked above `EditorStatusBar` when the keyboard is closed (Phase 16 behavior, unchanged), or as the **last child of the `imePadding()`'d root column** when `WindowInsets.ime.getBottom(density) > 0`, i.e. flush on top of the keyboard. `AnimatedVisibility` was deliberately **not** used (D2 overruled): the IME inset already animates, so a second slide animation just made the row lag the keyboard. The `⋮ → show/hide keys row` override still gates both positions.
    - **A.3 — insets.** Steps 1 and 4 were no-ops: `MainActivity.onCreate` already calls `enableEdgeToEdge()` and the manifest already sets `adjustResize` on `MainActivity`. `adjustResize` was **left in place** (contrary to D2 — with edge-to-edge it is inert for inset delivery, and removing it changes behavior on every other screen for no gain here). Shipped: `Modifier.imePadding()` on `EditorScreen`'s root column **only** (D1 respected — Terminal's `safeDrawingPadding()` and Settings/dialog insets untouched). `imeNestedScroll()` not added: still experimental at 1.7 and meaningless until the field owns its scroll.
    - **Tests:** `EditorHighlightCacheTest` ×8 — snapshot match/stale across text, theme and language; cached render equals inline render span-for-span with and without decorations; a stale cache never leaks wrong colors; offsets stay identity-mapped; `isEmpty()` across all five decoration layers.
    - **⚠️ Device pass REQUIRED and not yet run** — the three §4 recipes (`PART_22_1` steps 1–6 typing/fling/pinch smoothness, `PART_22_2` steps 1–9 keys-above-keyboard, `PART_22_3` steps 1–6 caret visibility **including the Terminal no-white-strip regression check**). Record: `docs/chat-phase22/` (README round-1 table + §7/§8 of each part doc).
    - **Rounds 2–6 (owner device feedback, 2026-09-03).** The round-1 entry above is preserved as written; these rounds corrected and superseded parts of it. Full record: `docs/chat-phase22/PART_22_1_SMOOTHNESS.md` §8–§13.
        - **The long-file lag was a documented Compose limitation, not a CodeC bug.** JetBrains [`compose-multiplatform#4023`](https://github.com/JetBrains/compose-multiplatform/issues/4023) → `CMP-4023`, closed **not planned**: `BasicTextField` is **not lazy**, and its layout cost is dominated by the **span count**, not the character count. The reporter confirmed the field is fine with the same text once the `VisualTransformation` is removed. Measured on CodeC's own tokenizer: a 500-line C file ≈ 4 500 spans, a 517-line HTML file ≈ 1 753 — all re-laid-out on every layout pass. **HTML is the worst case for span density** (every tag name, attribute string and number is a token), so "big file" here means *many spans*, not many lines.
        - **Fix (22.7 + 22.8):** `highlight()`/`tokenize()` take a `from`/`to` window and colour only ±`WINDOW` chars around the caret. `WINDOW` was first set to 20 000 — **larger than the owner's ~25 000-char file, so it never engaged**; corrected to **3 000** (1 753 spans → ~409; a unit test now fails if it is raised above 5 000). Scanning starts at a **blank-line safe anchor** 4 000 chars back rather than offset 0, which bounds the *main-thread* inline fallback (it ran on every keystroke whenever the debounced snapshot was stale). `HighlightedCode` reports itself stale when the caret leaves its window, and `filter()`'s memo is keyed on `(text, caret)` — keyed on text alone it could serve colouring for a scrolled-away window.
        - **Real per-keystroke O(file) work removed along the way (22.5/22.6):** `updateCode` stashed the active buffer into `_openTabs` on **every character**, rebuilding the tab list and publishing a new `StateFlow` identity (now only at real boundaries, as `stashActiveTabBuffer`'s own KDoc always described); `refreshDecorationsNow` built the caret line with `text.take(cursor).count { … }`, copying the whole prefix per keystroke (now one in-place pass); and `CodeCompletionEngine.completions()` ran **synchronously on the main thread every keystroke**, compiling a fresh `Regex` and sweeping the entire buffer (now `produceState` + 120 ms debounce + `Dispatchers.Default`, `Regex` hoisted, identifier scan windowed).
        - **⚠️ Correction to the round-1 entry above:** it credits `completionItems` becoming a `derivedStateOf` as a fix. **It was not.** `derivedStateOf` only helps when the derived *value* changes less often than the state it reads; this one reads `codeText` and is read by the popup in the same frame, so it recomputed every keystroke regardless. It looked like a fix in the diff and did nothing at runtime. The gutter's `derivedStateOf` *does* help, but by skipping *recomposition*, not the count — its comment was corrected to say so.
        - **Keys & suggestions (owner requests):** new `EditorKey.Pair` makes `()`, `{}`, `[]`, `<>`, `""`, `''` and JS backticks single caps (caret between, or surround the selection) — six pair caps replaced ten single caps. HTML/CSS and Markdown had **no completions at all** (both fell through to `else -> emptyList()`, despite Web Preview running HTML directly); both gained full snippet sets, and CI caught that snippet matching was case-sensitive, so lowercase `doc` never matched `<!DOCTYPE html>` — now case-insensitive.
        - **Tests added across rounds:** `EditorCursorMathTest` ×6 (incl. a 500-line oracle comparison), `OutputPanelVisibilityTest` ×5, 7 in `CodeCompletionTest`, 11 more in `EditorHighlightCacheTest`, 4 in `EditorKeySetTest`.
        - **Ceiling (unchanged, needs owner go-ahead):** windowing bounds the *steady-state* cost but cannot make `BasicTextField` lazy — per JetBrains it never will be. Going further means replacing the field (`bigtext`-style, or `TextFieldState` + viewport-driven windowing), which is its own phase.
32. **Phase 23 — Interactive Run UX (2026-09-03, `arena/01a06662-codec`, owner: "Start Phase 23").** Two client-only parts, both implemented in one pass; **CI green `33735687876`** (one for-cause red round `33735482625` first — a missing `kotlinx.coroutines.flow.update` import in `EditorViewModel` — fixed in the same commit set). **Device-accepted 2026-09-03 — owner: "Phone test passed"** (both §4 recipes: inline scanf input with no separate row → "Hello, Alice!"; run keys above the keyboard with Ctrl+C → "Killed"/130 and editor keys restored afterwards).
    - **B.1 — inline PTY input (remove `OutputInputRow`).** The Output Panel's separate input row is gone. Its replacement is an editable `InlineInputRow` rendered as the **last `LazyColumn` item** of the panel (key `"inline-input"`), shown **only while a PTY interactive run is live** (`OutputRunState.waitingForInput`). It reuses the device-proven `singleLine` + `ImeAction.Send` + `KeyboardActions(onSend)` wiring (the same shape the old row used), so both the soft keyboard's send action and the `↵` icon submit the line; `submitInput()` sends it to `InteractiveRunSession.sendLine` and clears the buffer, and an empty line sends nothing. `waitingForInput` is set the moment `InteractiveRunSession.start` returns non-null and cleared in every terminal transition (`finishRun`/`failRun`/`stopRun`/`finishFailedBuild`/`finishServerExit`), so the inline cursor shows for a `scanf` prompt with no newline and disappears the instant the program stops; each run starts with a fresh `inputBuffer` so an unsubmitted line never leaks across runs. The auto-scroll `LaunchedEffect` now keys on `(lines.size, waitingForInput)` and scrolls to index `lines.size` (the field, kept pinned) while waiting, else `lines.size - 1` as before. New pure `ui/services/InteractiveInputBuffer.kt` (Android-free; `current`/`onChange`/`submit`) is host-tested, keeping the ViewModel thin.
    - **B.2 — run keys in the IME strip.** While an interactive run waits for input, the Phase 22.2 keys strip shows `↵ Enter` (wide), `Ctrl+C` (wide), `Tab`, `↑`, `↓` instead of the editor keys, and the editor keys return as soon as the run ends. The strip choice is derived in `EditorScreen` from `outputState.waitingForInput` alone (`KeysContext.InteractiveRun` vs `KeysContext.Editor(language)`) — no focus plumbing. `↵ Enter` → `submitInput()`; `Ctrl+C` → `interruptRun()` → new `InteractiveRunSession.sendSignal(SIGINT)` → `PtyNative.kill(pid, SIGINT)` (the existing JNI `nativeKill` already signals the child's **process group** — **no native change was needed**, the spec's "add `kill()` if missing" was a non-issue); `Tab` → `appendInput("\t")`; `↑`/`↓` are placeholders (REPL history is a future phase). Editor-key handling is untouched: `EditorKey` remains a buffer edit, while the run keys are VM *actions*, so they live as a separate `RunKey` type rather than being forced into the editor key set.
    - **New pure code:** `ui/editor/RunKeySet.kt`, `ui/editor/KeysContext.kt` (`keysForContext` → `None | EditorKeys | RunKeys`), `ui/services/InteractiveInputBuffer.kt`. `EditorKeysRow` was refactored to take a precomputed `keys: List<EditorKeyDef>` (a new `RunKeysRow` and both rows draw through one shared `KeyCap`); `OutputPanelView`'s signature changed from `onSendInput` to `onInputChange` + `onSubmitInput`. Host tests: `RunKeySetTest` ×8, `InteractiveInputBufferTest` ×7 (editor keys stay covered by `EditorKeySetTest`). Device recipes: `docs/chat-phase23/PART_23_1_INLINE_INPUT.md` §4 (scanf → "Hello, Alice!"), `PART_23_2_RUN_KEYS.md` §4 (run keys above keyboard, Ctrl+C → "Killed"/130, editor keys restore).
33. **Phase 24 — desktop-class editor quality (2026-09-03, `arena/01a06784-codec`, owner: "Start Phase 24").** Nine spec parts (E.1–E.9) in `docs/chat-phase24/`; implementation in one branch. **CI green `33768581748`** (`Build APK`; four for-cause red rounds fixed). Device pass REQUIRED (owner's recipes §3 of each part); no PR/merge without owner's command.
    - **E.1 — per-language formatter.** `EditorViewModel.formatActiveFile(context, tabSize)` looks up `LanguageRegistry.formatterCommand`, saves the buffer first, runs the formatter through the real userland path (`ExecutionRunner`, build-only, 60 s), then reloads the rewritten disk file into the buffer as ONE undo step. `.c/.cpp` keep the instant offline built-in `formatCode` fallback when clang-format is not installed, so formatting never prompts for a 90 MB install. The `⋮ → Format` item is now conditional on `profile.formatterTemplate != null` (a `.lua` file has no Format item).
    - **E.2 — background-run notification.** New `ui/services/RunForegroundService.kt` + `ui/services/RunForegroundPolicy.kt` (pure 5-second threshold, host-tested). A run/server that is still busy after 5 s promotes the app to a foreground service ("Running: `<file>` · X seconds", tap-to-return, Stop action routed back through `EditorViewModel.stopRun`). Short runs stay silent. Manifest declares `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` and the `<service>`; cancellation on `finishRun`/`failRun`/`stopRun`/`clearOutput`.
    - **E.3 — hardware shortcuts.** New pure `ui/editor/EditorLineOps.kt` (`toggleLineComment`, `duplicateLine`, `commentPrefixFor` — C/Go/JS `//`, Python/Shell `#`, HTML `<!--`); VM methods `toggleLineComment`/`duplicateLine` route through the undo-recording `applyBufferEdit`. `EditorScreen` `onPreviewKeyEvent` added for Ctrl+R / Ctrl+F / Ctrl+/ / Ctrl+D / Ctrl+W / Ctrl+Tab / Ctrl+Shift+Tab / F5; `nextTab`/`prevTab`/`closeActiveTab` (save-first) exist on the VM.
    - **E.4 — Share as ZIP.** `ProjectTransfer.exportZipToCache(projectRoot, cacheDir, name)` writes `cacheDir/shares/<name>.zip`; `file_paths.xml` gained `<cache-path name="shared_zips" path="shares/">`; Projects card `⋮ → Share as ZIP` fires `ACTION_SEND` with the `FileProvider` URI and `FLAG_GRANT_READ_URI_PERMISSION` — no SAF picker.
    - **E.5 — tablet two-pane.** **DEFERRED** (see below).
    - **E.6 — Test ▷.** `LanguageRegistry.isTestFile`/`testProfileForFile` (pytest `test_*.py`/`*_test.py`, Go `*_test.go`; keeps the parent language gate). `OutputLineParser` gained `TestLineKind`/`TestOutputParser` (FAIL-before-pass so `ok 1 failed` is a fail); `OutputLineKind` gained `TEST_PASS/FAIL/ERROR/SUMMARY` and the panel colors them; a `Test ▷` button appears when the active file is a test file and `runTests(context)` streams through the same runner with a 120 s test timeout and a `Tests` panel header.
    - **E.7 — Open with CodeC.** Manifest `VIEW` (`text/*`, `application/x-python`, `application/zip`) + `SEND` (`application/zip`) filters; `MainActivity.handleIncomingIntent` copies a shared file/ZIP into `CodeC/projects` (new `ui/projects/IncomingImportBridge.kt`) and `MainApp` receives the import via a `StateFlow` and navigates to the editor.
    - **E.8 — adaptive theme.** `ThemeManager.effectiveDark(mode, systemDark)` is pure/companion (AUTO follows `isSystemInDarkTheme`); the app root uses it; Settings lists `Auto (follow system)` first, then Light/Dark. Host test `ThemeManagerTest`.
    - **E.9 — per-project `.codec.json`.** Pure `ui/projects/CodecJsonParser.kt` + `CodecOverride`; `EditorViewModel.runActiveFile` applies the project-root override ABOVE the registry/project config, gates the raw commands through the same toolchain gate, and the editor `⋮ → Edit run config` dialog writes `.codec.json`.
    - **Host tests added:** `CodecJsonParserTest`, `EditorLineOpsTest`, `RunForegroundPolicyTest`, `ThemeManagerTest`, plus `LanguageRegistryTest` (test files/profiles/formatter commands), `OutputLineParserTest` (test-line classification), `ProjectTransferTest` (`exportZipToCache`).
    - **Device round 1 (2026-09-04, owner on-device):**
        - **E.1 Format — ✅ PASS.** Includes the **Python formatter**: `⋮ → Format` on an `if/else` python file corrected the indentation (owner: "Formater pass"). Managed manually in chat with the black-style snippet (`def add(a,b):`, `x=1+2`, `name='Alice'`, long `y=[...]` list) to confirm black output beyond indentation.
        - **E.2 Background-run notification — ✅ PASS** (owner: "I tested the notification part it's working fine").
        - **E.4 Share as ZIP — ✅ PASS.**
        - **E.6 Test runner — ✅ PASS.**
        - **E.7 Open with CodeC — ✅ PASS.**
        - **E.8 Adaptive theme — ✅ PASS.**
        - **E.9 Per-project `.codec.json` — ✅ PASS.**
        - **E.3 Hardware shortcuts — ⏳ NOT device-verified this round** (owner: "E.3 is not possible in this time" — needs a Bluetooth keyboard/tablet). Code is CI-green with host tests (`EditorLineOpsTest`, `RunForegroundPolicyTest`, etc.) but the §3 recipes (Ctrl+R, Ctrl+/, Ctrl+D, Ctrl+W, Ctrl+Tab, F5) have **not** run on hardware.
        - **E.5 Tablet two-pane — ⏸ DEFERRED.** It needs the `EditorScreen` body extracted from the `ModalNavigationDrawer` so a wide layout can render a permanent `EditorProjectDrawer` pane (or a `windowSizeClass` dependency) — a structural refactor better done as its own commit once the owner confirms tablet support is wanted (the rest of the phase is phone-first and device-verifiable now).
34. **Phase 25.1 — candidate spike & device benchmark (2026-09-04, `arena/01a06b20-codec`, owner: "Start Phase 25").** The editor-core decision gate from `docs/chat-phase25/` is now BUILT: a throwaway `:bench` application module (separate APK `com.codeci.bench`, never shipped in `:app`) with the three candidates and an identical-input measurement harness. **Device pass REQUIRED** — the decision table (`docs/EDITOR_MOBILE_RESEARCH.md` §3.1) is empty until the owner runs the bench and pastes the Copy-all export; 25.2/25.3 stay PLANNED behind that gate. Implementation record: `docs/chat-phase25/PART_25_1_SPIKE_BENCH.md` §4.
    - **C-now** — the current core, measured: verbatim copies of CodeC's own `MultiLanguageSyntaxHighlighter` (+ theme, `CodeFormatter`, `BracketMatcher`, `EditorUndoManager`, `CodeCompletionEngine`, decoration types) and a faithful mirror of the `EditorScreen`/`EditorViewModel` typing pipeline (±3 000-char windowed spans, 80 ms debounced off-thread highlight, 20 ms decorations, 120 ms completion scan, `remember(lineCount)` gutter).
    - **C-sora** — sora-editor **0.24.6** (`io.github.rosemoe:editor` + `language-java` — the project moved groups; newest stable verified on Maven Central 2026-09-04; the spec's `0.23.6` pin is a research note) behind `AndroidView`, **binary dependency only** (LGPL-2.1; no source vendored). `JavaLanguage` = the ready-made trivial lexer with identifier completion.
    - **C-compose2** — the Phase 22-deferred rewrite sketch: pure `DocumentBuffer` (line array + maintained offset index, binary-search `locate` — the CMP-#4021 avoidance), `VisibleWindow` (visible lines + 8 overscan only), `LineSpanCache` (per-line re-tokenization, bounded LRU); caret-line single-line field editing at spike scope (no cross-line IME composing — the documented debt).
    - **Harness** — platform `Window.addOnFrameMetricsAvailableListener` + `FrameMetrics.TOTAL_DURATION` (androidx `FrameMetricsAggregator` was REMOVED from metrics-performance — spec deviation recorded); pure host-tested input scripts (`burst60`, `completionChurn`, `fling500`, `caretDrag` with a bottom-edge auto-scroll probe); `ScriptRunner` lowers them via `KeyCharacterMap.VIRTUAL_KEYBOARD` key events (all halves dispatched so shifted punctuation lands) and bounds-resolved MotionEvents; per-run **input mode** (`keys` vs `direct`) recorded on the sheet; cold-open = read + compose + 2 frames, same protocol per candidate; Copy-all markdown export persisted to `files/bench-results.md`.
    - **Corpus** — committed to `bench/` assets from a seeded generator (`bench/tools/generate_corpus.py`): `bench.c` 4 993 lines/175 kB, `bench.html` exactly 517 lines/31 kB (generated stand-in for the owner's 517-line sample — substitution recorded).
    - **CI** — `build-apk.yml` gained a wrapper step `:bench:assembleRelease :bench:testDebugUnitTest` + the `CodeC-Bench` artifact upload (remove when Phase 25 closes); `settings.gradle.kts` includes `:bench` only for real (9.3.1) wrapper builds so the legacy 9.0.0 shim path is untouched. **CI green `33849153135` (tip `9dd7922`) after four for-cause red rounds** (unreadable first failure → `::error` emitter added to the step; 9 kotlin errors; a missing `assertTrue` import; `lintVitalRelease` tripping the targetSdk-28 policy check → release lint off on the harness) — record `docs/chat-phase25/PART_25_1_SPIKE_BENCH.md` §4.4. Host tests: `FrameStatsTest` ×8, `InputScriptsTest` ×5, `DocumentBufferTest` ×9 (10 000-op seeded fuzz vs `StringBuilder` oracle), `VisibleWindowAndSpansTest` ×6.
    - **DEVICE GATE — DECIDED 2026-09-04 (owner's full export: cold open + 4 scenarios ×3 reps ×3 candidates ×2 corpora).** **C-SORA WINS.** C-now on bench.c: ~400 ms/keystroke p95 (100 % jank — ≈24 missed frames per key), ~90 ms fling frames, ~150–230 ms drag, ~490 ms completion refresh; even Phase-22-windowed bench.html runs ~90 ms keystrokes — the phase's premise is now measured evidence. C-sora passes EVERY budget on both corpora: keystroke p95 14.5–16.6 ms, fling ≤3.1 % jank with 0 bad frames (holds 60 fps), caret-drag p95 ≤17.9 ms (15 lines auto-scrolled during the bottom-edge wiggle), completion p95 18–22.5 ms, cold open 35–56 ms; `Typed=62` on a 60-key burst = Sora's SymbolPairMatch pairing `(`/`{` live during the run. C-compose2: rep-1 frames partly fine then locked at ~36 ms/frame at 100 % jank (whole-window recomposition storm), drag traversal 0 — dead as a candidate. ⚠️ C-now bench.c cold open (1155/1215 ms) may include process startup; immaterial to the verdict. **Verdict in writing: 25.2 CHOSEN (starts on the owner's "Start Phase 25.2"); 25.3 ❌ CANCELLED** (note at the top of `PART_25_3_COMPOSE_FALLBACK.md`). Decision table `docs/EDITOR_MOBILE_RESEARCH.md` §3.1; analysis `PART_25_1_SPIKE_BENCH.md` §4.5–§4.6.

35. **Phase 25.2 — sora-editor 0.24.6 is the new edit core (2026-09-04, `arena/01a06b20-codec`, owner: "Start Phase 25.2").** The 25.1 winner integrated as a widget-only swap; the `EditorViewModel` stays THE source of truth. New `ui/editor/sora/`: `SoraEditorHost` (two-way bridge — sora→VM via `ContentListener`+`SelectionChangeEvent` into the SAME `viewModel.updateCode` the old `BasicTextField` fed, so undo recording/dirty/autosave are byte-identical; VM→sora replays foreign changes — tab switch, undo/redo, find/replace, formatter, keys strip, completions — as ONE `batchEdit`; selection-only VM moves replay as caret/region changes; typing echoes short-circuit on reference equality; sora's own undo stack DISABLED, VM `EditorUndoManager` canonical); `CodeCAnalyzer` (v1 = full re-tokenize with the existing `MultiLanguageSyntaxHighlighter` on `SimpleAnalyzeManager`'s background thread — incremental lexing deferred); `CodeCLanguage` (no-op completions — the app popup stays VM-driven, bottom-anchored; pure `indentAdvanceFor`/`symbolPairsFor`; no-op formatter); `CodeCScheme`+`CodeCThemeMap` (pure, host-tested theme map, 26 slots; fresh scheme per editor per the single-ownership rule). `EditorScreen`: BasicTextField+gutter+scroll Rows → `SoraEditorHost`; Phase 16 pinch deleted (sora-native, on by default); diagnostics tap-popup REMOVED (recorded regression — it lived on the old surface); Phase 22.1 highlight pipeline deleted from screen AND VM (Phase 23 decoration engine kept). Find rides `EditorSearcher` (`SearchOptions(TYPE_* from wholeWord/regex, !matchCase)`); Ctrl+Z/Shift+Z/Y → VM undo/redo. `app` consumes sora 0.24.6 as a BINARY Gradle dep at Java 17 (mirrors `:bench`); About gains the sora LGPL-2.1 attribution and `assets/licenses/SORA_EDITOR_LGPL.txt` ships the license text. Host tests: `CodeCThemeMapTest` ×5, `CodeCLanguageLogicTest` ×6. Implementation record + honest deviations: `docs/chat-phase25/PART_25_2_SORA_PATH.md` §4. **CI: three rounds — `33855565141` ❌ (`setTextSize` is Sp-native, no `setTextSizeUnit`; `FormatResultReceiver` nested in `Formatter` — the rest of the 1 360-line change compiled first try), `33856448309` ❌ (Pair compareTo in a test), `33857318159` ✅ GREEN on `f78864a` (release included; 11 new host tests pass), then the owner hit a **crash on editor tap (device round 1)** → §4.1: the `SelectionChangeEvent` receiver missed the `pushing` guard (stale-text push mid-replay → two-way replay ping-pong), fixed with single-apply editor config and an on-device crash log (`Android/data/com.codeci.ide/files/crash-log.txt`); rounds `33859414468` ❌ (SAM label `@EventReceiver`) → `33860045301` ✅ GREEN on `08fb542` (CodeC-IDE 20.84 MiB, +0.55 MiB vs 25.1). **Round 2 (owner screenshot + "not a root user"):** Android/data is unreachable without root → crash handler moved to internal `filesDir` + `CrashReportOverlay` shows the last report IN-APP (COPY ALL/SHARE/CLEAR) — and it worked: the owner pasted the full stack. **§4.3 ROOT CAUSE:** sora's `EditorColorScheme` constructor calls `applyDefault()`; the Kotlin override read the subclass `type` val before assignment → NPE the instant the editor composed (leaked `this` in super-construction; invisible to CI). Fix: `CodeCScheme.of(theme)` applies colors post-construction, override deleted (`fe7ae11` ✅ `33863407938`). **Round 3 = §3 recipe PASS** with two owner reports, both fixed in `c54228d` (✅ `33866749797`): (1) the file drawer's edge-swipe covered the line-number gutter and opened during scrolls → `gesturesEnabled` off whenever a file is open; (2) owner: "sora is better than that" → `CodeCLanguage.requireAutoComplete` now feeds the same `CodeCompletionEngine` into sora's NATIVE at-caret panel (`SimpleCompletionItem` + kind icons, sora keyboard handling); the Phase 12/22 app popup and its produceState scan are RETIRED — this pulls Phase 27's renderer goal forward. Highlighting stays the 7-kind tokenizer by design; TextMate/tree-sitter grammars = separate increment, not started. **Round 4 (build `c54228d`): owner "All passed" — PHASE 25.2 COMPLETE, DEVICE-ACCEPTED.** Merge gates open: owner's explicit LGPL-2.1 acceptance + owner's merge command (APK delta +0.55 MiB ≤ +2 MB). No PR/merge without the owner's command.**

36. **Phase 26 — Typing Experience 2.0 (2026-09-04, `arena/01a06c70-codec`, owner: "Start Phase 26").** Three client-only parts on top of sora; all pure engines host-tested so CI carries confidence.
    - **26.1 — Key strip 2.0.** `EditorKeySet` gains `EditorKeyDef.popup/swipeUp/swipeDown` (data model only — no new row). `EditorKeysRow` draws popup caps as a small `Box`+`offset` over the key (≥300 ms long-press) plus `KeyGestureDetector` swipe Up/Down (✓ via `KeyGestureDetectorTest`) and hold-repeat `Job`/`delay` for arrows; `hasPopup` tick on caps with extras. Per-language defaults wired; `RunKeySet` similarly extended (HOME/END/PAGE_UP/PAGE_DOWN popups). User-editable sets persisted as JSON via new pure `KeyStripStorage` (manual escape/unescape, encodeKey, `deserialize` returns `null` on invalid JSON with `Log.w`, host-tested roundtrip+null+empty); `SettingsManager` adds `KEY_STRIP_JSON` + `Flow` + `setKeyStripJson`; `KeysContext.keysForContext(context, prefs, storedJson?)` prefers stored JSON then `EditorKeySet.keysFor(LanguageType)`.
    - **26.2 — Smart typing.** New pure `SmartTyping` (no Android) with `Config(typeOver/wrapSelection/emptyPairBackspace/autoIndent/stringAware)` and `transform(old, newValue, lang, tabSize, config)`: type-over `"')]}`, wrap-selection for `({["'`, empty-pair Backspace, auto-indent (copy leading indent + extra 4 after `{/:[` — `:` gate is Python-only), `deletePrevWord` (stop chars whitespace/`. / ' " \` `), and string-aware suppression via `MultiLanguageSyntaxHighlighter.tokenize`+`TokenKind.STRING`. `SettingsManager` adds `SMART_TYPING_*` booleans + Flows + setters; `EditorViewModel` collects them into `smartTypingConfig` and routes every `updateCode` through `SmartTyping.transform`. Host tests: `SmartTypingTest` (typeOver/wrap/emptyPair/deletePrevWord/autoIndent).
    - **26.3 — Code-friendly IME guide.** `SettingsManager.IME_GUIDE_DISMISSED` + `EditorScreen` first-run tip (dismiss persists via DataStore) plus `KeyboardOptions` hardening (autoCorrect=false, capitalization=NONE) — the only editor-side IME flag change.
    - **CI history on `arena/01a06c70-codec`:** first run `33874648603` ❌ 10 compile errors (SmartTyping char-literal escaping, KeyStripStorage forward-ref `parseObject` before `parseNestedObject`, EditorKeysRow `awaitPointerEventScope`/`awaitPointerEvent`/`LocalDensity` imports); second run `33875023595` ❌ 1 test failure (`SmartTypingTest.autoIndent copies previous line indent` — double-newline fixture fed empty `previousLine`); third run `33875546000` ✅ **GREEN (4m28s)** on `b56a152` after the 3-file fix + single-newline test correction. PR #50 remains open (no merge without owner's command). Record: `docs/chat-phase26/` (3 parts) + research `docs/EDITOR_MOBILE_RESEARCH.md` §2/§5. **Device recipes pending owner** — §3 of each part (popups/swipes/hold-repeat + type-over/wrap/indent/delete-word + guide).


37. **Phase 27 — phone-native autocomplete (2026-09-05, `arena/01a06f9e-codec`, owner: "Start phase 27").** The owner's diagnosis from the research round: *"the suggestions are good but also problematic for phone because it suggests and can't do anything"* — a floating popup asking for mouse-era input while a soft keyboard is up. Fix = replace the popup-first UX with the spec's pipeline: **ghost text** (top-1 painted inline at the caret) + a **chip strip** (top-N as thumb-sized caps where the keys already live) + the sora native panel demoted to an explicit **"⌄ more" browse mode**. Three client-only parts, all law in pure, host-tested code:
    - **27.1 — Ghost text.** New pure `GhostCompletion` (GhostState.Visible {suffix, item, prefixLength}; `compute` = top item whose *insert text* starts with the caret prefix, suffix capped at the first line — G1/G6; `nextWordPiece` = identifier-run | symbol-run | whitespace-run, never crossing a newline — G3(d); `accept` FULL/WORD/LINE with stale-ghost and selection rejection — G2). Rendered on the sora core via its **inlay-hint lane**: new `GhostInlayHint` (type `codec.ghost`, point-anchored so sora auto-shifts it on edits) + `GhostHintRenderer` (plain dimmed text at FULL size — no rounded chip — the stock `TextInlayHintRenderer` look), colored comment@38% exactly per G5. Affordances: strip **TAB ▸** cap / **→▸** (next word) / the small **"Tab ▸" pill** anchored at the caret row's right edge (tap = accept, swipe-down = reject) / **tap the ghost text itself** (`InlayHintClickEvent` + `intercept()`) / HW Tab & Ctrl+→. Ghost clears on scroll (`ScrollEvent` → VM `onCompletionScroll`) and while IME-composing (`hasComposingText` gate at apply time + a clear on composing selection events) — G4/G7. Instant shrink: grown prefixes narrow the CACHED items per keystroke without an engine re-run (`filterForPrefix`), so the ghost shrinks character-by-character.
    - **27.2 — Suggestion strip.** New sealed `StripContext` (Hidden | Keys | Run | Suggestions) resolved by the pure `stripContextFor` — Run ALWAYS wins (S6/23.2 re-pinned), ≥2 candidates flip the bar into chips (S1; a single candidate stays in key mode — the ghost covers it), dismissal is per-identifier (`dismissedAnchor`; swiped down / "⌨" cap / ESC / selector boundary — the next identifier re-arms), selection/1 MiB cap/settings-off suppress. New `SuggestionStrip` composable: pinned left "⌨" cap (S3), horizontally scrolling chips with ƒ/λ/≠ kind glyphs + ≤18-char labels + ghost-backed chip filled accent (S7), long-press chip tooltip (S2), pinned right "⌄ more" cap (S5) → sora's panel via `CodeCCompletionComponent.browseNow()`, swipe-down-to-dismiss for the current identifier (S4). Row geometry identical to the keys row (S8: no IME flicker). Ranking = engine order + ghost-pin + in-memory recency boost.
    - **27.3 — the policy owns the law.** New pure `CompletionPolicy`: `surfaceFor` (NONE | GHOST_ONLY | STRIP | PANEL — PANEL only while sora's popup is *actually attached*, mirrored via the component callback so the look always tells the truth) + `decide(surface, input)` — every matrix cell a host test: Enter always NEWLINE on soft keyboards (invariant 1, never stolen); TAB tap accepts only while its cap reads "TAB ▸" (labels from `tabCapLabel`; long-press is ALWAYS raw indent — accessibility escape hatch); →▸ accepts the next word; ↑↓ never target the strip (PANEL browses); ESC rejects/dismisses/closes; an unmatched char is never swallowed. `CompletionSettings` (master + ghost + strip + panel + debounce 120/240) backed by DataStore — **master off ⇒ zero chrome, zero computation** (invariant 4: the sora component is also `setEnabled(false)`). New Settings → "Editor Settings" rows with the behavior explainer.
    - **The gated panel.** New `CodeCCompletionComponent : EditorAutoCompletion` (installed via `replaceComponent` at editor setup) — `requireCompletion()` is gated so sora's typing/caret auto-triggers become no-ops; `browseNow()` opens a session where the panel updates/behaves exactly as the 25.2 device-accepted native panel did (hardware Tab/Enter/arrows inside the panel stay sora's); every hide path clears the session so a dismissed panel never resurrects mid-identifier.
    - **VM pipeline (one model, no disagreement).** `EditorViewModel` gains the two-leg completion pipeline: the instant leg re-narrows cached items per keystroke (main-thread-cheap `startsWith` class filtering only) and the debounced leg (120/240 ms, `Dispatchers.Default`) re-runs the same `CodeCompletionEngine`; `completionModel: StateFlow<CompletionModel>` is the single projection ghost+strip+panel read from. Host tests: `GhostCompletionTest`, `StripContextTest`, `CompletionPolicyTest` (the full matrix). Sora stays a BINARY Gradle dep (LGPL checklist untouched — subclassing its public component is ordinary usage, no source copied; upstream 0.24.6 sources read for interfaces only).
    - Record per part + deviations: `docs/chat-phase27/` §4 sections (incl. research notes on sora's inlay-hint lane, `InlayHintClickEvent` dispatch, `replaceComponent`). CI history below; device recipes in the part docs' §3 blocks.
    - **CI history on `arena/01a06f9e-codec`:** first run `33943917743` ❌ (1m39s, compileDebugKotlin) — `EditorKey.GhostAccept`/`GhostAcceptWord` broke the two exhaustive `when`s in 26.1's `KeyStripStorage` JSON (de)serializer; fixed in `51680e5` by persisting the ghost caps as their PHYSICAL keys (transient caps never live in saved JSON by construction, the branches only satisfy exhaustiveness). Second run `33944038267` ❌ (4m30s, compileDebugUnitTestKotlin) — two backtick test names contained `;` (AGP test-name rule); renamed in `c25c5b8`. Third run `33944280599` ❌ (~3.5m, testDebugUnitTest) — one assertion: `G6 multi-line insert ghosts the FIRST line only` — the fixture typed `"int ma"` with caret 6, but `currentPrefix` = the identifier run `"ma"` only, and the skeleton's insert text doesn't *start* with `"ma"` → Hidden (impl correct per G1; fixture wrong). Fixed in `6da7f44`: typed `"int"`, ghost ` main(void) {` (first line only, no `\n`). Fourth run `33944516016` ✅ **GREEN (4m51s)** on `6da7f44` (assemble + all host tests + lint). **Device recipes remain the only open gate — §3 of each part doc; Phase 27 NOT merged, no PR without the owner's explicit command.**
    - **Device round 1 (2026-09-05): owner report "ghost not showing" — root-caused & fixed in code, CI pending re-run.** Two stacked defects: (a) the G7 composing suppression keyed on `hasComposingText()`, which is true during almost all soft-keyboard typing (Gboard word composition) → gates deleted, inlay hints are point-anchored so composition can't corrupt them (deviation recorded in `PART_27_1` §4.1); (b) `GhostCompletion.compute` aligned inserts against the bare identifier prefix while the strip matches chips by fuzzy label → the ghost was Hidden exactly when a snippet surfaced (typed `int mai` ⇒ insert starts `int ` ⇒ no match). Replaced with longest-line-tail alignment (accept replaces exactly the matched range — sound), fixtures restored/extended in `GhostCompletionTest`. CI for the round: `33952400479` ❌ (my own negative fixture caught a real edge — bare `mai` aligned its trailing `i` mid-word against `int main…`; alignment now requires a word-boundary start, `7e5b4f2`) → `33952662950` ✅ GREEN. Owner-reported `;` cap emitting `';`: cap/gesture/apply/SmartTyping paths audited clean; reopened forensics pending the owner's repro log. Sandbox note: the GitHub reconnect reset local git to the pre-27 base (working tree preserved); recovered via `git reset --mixed FETCH_HEAD` per the standing rule. Remote branch untouched throughout.


38. **Phase 28 — CodeC Keys, started; 28.1 IME-free input-path spike BUILT (2026-09-05, `arena/01a070ae-codec`, owner: "Start phase 28").** Phase 27 merged via PR #51 in the same window (`main` = `92af7fb`, post-merge CI `33955091994`). Per the phase's law 3 ("feel is the gate"), ONLY the spike was built — entirely in `:bench`, `:app` shipped nothing: **K1** (Compose document core — the 25.1 `NowState` mirror: pure VM-shape doc + undo + debounced highlight) and **K2** (the shipping sora `CodeEditor`) each fed **only** by an IME-free 3-row code grid (letters + TAB/DEL/⏎/space) whose every press routes through the production model — the four pure editor files (`EditorKeySet`, `SmartTyping`, `CompletionPolicy`, `KeyStripStorage`) mirrored VERBATIM into bench; DEL is the one spike-local op (the app model has no backspace key while the IME owns deletion).
    - **Measured, not asserted:** `SpikeSession.press` is the single path for script AND thumb — DOWN→commit latency ledger (ring, 25.1 percentile law), `TapAuditor` strict-subsequence audit (dropped/dup/swapped — catches a double-fired DEL hold and a reordered burst), `ImeFlicker` probe over window-ime-inset samples with a self-check toggle ("IME: allowed" must make `ime=` go > 0, proving the detector), live p95 line at 150 ms. Suppression trio: window `SOFT_INPUT_STATE_ALWAYS_HIDDEN` while the K-screen is open + 60 ms `hideSoftInputFromWindow` poll + hide in the Initial pointer pass on every down (recorded deviation: Compose 1.7's `PointerInputScope` has no `view` — `LocalView.current` captured instead).
    - **Spike questions answered in scenarios:** Q1 = `hw_path_check` (20 synthesized KeyEvents into the IME-less focused view — the BT-keyboard mechanism; owner confirms with a real keyboard); Q2 = `run_row_check` (grid commits re-routed to a stdin-row buffer, document length must stay untouched — the 23.2 run path never used an InputConnection, this proves the keyboard can't break it); Q3 = TalkBack pass, owner-side recipe (`PART_28_1_SPIKE.md` §5). Budgets = 25.1's law: keystroke p95 ≤ 16.7 ms, exact 64/64 + 40/40 taps, `ime max=0px`, plus the owner's "feels instant" on the 5-min human session.
    - **Harness reuse:** FrameCapture/FrameStats cold-open protocol + the ResultsStore markdown export ride from 25.1 — `RepResult` gained optional `latencyLine/auditLine/imeLine` and the export a `## Owner notes` section (answers typed into a Home-screen box BEFORE export, so one paste = the whole round).
    - **CI history:** `33956591999` ❌ (bench step red, raw log unreadable in the sandbox → the workflow's bench step now `set -o pipefail`+`tee` and a follow-up re-emits error lines as check-run annotations — the shim's trick generalized to bench; two host-test expectation bugs self-caught: fold DEL count, swapped-law drop count — `5bf70bd`); `33956854196` ❌ (annotations pinpointed `Unresolved reference 'view'` ×4 → LocalView fix `3ea58d1`); `33957016839` ✅ **GREEN (4m01s) on `3ea58d1`** — app+bench both gates, `CodeC-Bench` artifact live. Host tests added: `CodecKeyGridTest`, `KeysMetricsTest`, `KeysSpikeScriptsTest`, `SpikeSessionTest`.
    - **Gate:** owner device round per `docs/chat-phase28/PART_28_1_SPIKE.md` §5 (runbook `docs/TROUBLESHOOTING.md` §10). GO → 28.2 starts on the owner's word; NO-GO → phase stops, L0 strip (26/27) stays the product answer — the verdict lands `docs/EDITOR_MOBILE_RESEARCH.md` §9.1. Merge gate untouched.
    - **Device round 1 (2026-09-05): K2 meets EVERY budget** — DOWN→commit p95 1.25–3.4 ms (max 4.4, `over1f=0` everywhere incl. the 5-min human session), tap audits `64/64`+`40/40` exact on all 6 reps/core, Q1 hw-synth 20/20 (K1) + 21/20 (K2 — the +1 is sora's live SymbolPairMatch auto-closing `(`, 25.1's `Typed=62` signature), Q2 run-route `OK` both cores, **IME inset 0 px across every sample (~2 900 incl. human sessions)**. K1's red frame rows (~260–280 ms) are the Compose core 25.1 already condemned — the keyboard itself committed sub-1 ms there too; **input path of record for 28.2 = S2 (sora `Content` edits)**. Verdict **GO pending four owner confirmations** (detector self-check >0 toggle, real BT-keyboard Q1, TalkBack Q3, "feels instant" yes/no) — recorded `PART_28_1_SPIKE.md` §6.
    - **GO recorded (2026-09-05): owner answered "Go" (twice)** to the four open confirmations — waiving them as blockers, folded into 28.2's device round (self-check flip, real BT keyboard, feel line; TalkBack rides 28.4). **28.2 STARTED same day** on the S2 path — no new mechanism needed: production `:app` already routes every programmatic edit through `EditorViewModel.codeText` → `SoraEditorHost` `batchEdit` (the strip rides it); the keyboard joins that path. CI history for the phase so far: 5/5 green.
    - **28.2 BUILT the same day (engine in `:app`/ui/keyboard: JSON-over-26.1-schema layout, router, Compose grid, Settings master OFF, S2 wiring, sora soft-IME handoff, `EditorKey.Delete` = DEL's home).** CI green `33964504903` after 3 fix rounds (test-package imports, specials-are-noop long-press law, the `;` popup expectation moved to the flick set); 20 host tests pin the laws. **Device round = the exit gate** (`TROUBLESHOOTING.md` §11) — five checks plus the four waived 28.1 items.
    - **Device round 1 (2026-09-05): "the keyboard is really good"** + three asks, all landed same day — ① hold previews: the overflow bubble is GONE (only outside-bounds draw = prime suspect for the round's headerless trace); every cap now PRINTS its release in the corner (`q¹`, `;:`) and the big label SWAPS to it while held; ② the lone `->` macro row deleted — language tail caps ride the utility row (5 rows for every language, owner's space back); ③ port-side discovery: arrow popups are dead under the 150 ms repeat law (latent strip flaw) → Home/End/PgUp/PgDn moved to arrow FLICKS, pinned by test `arrowNavigationTravelsAsFlicksNotPopups`.
    - **Device round 2 (2026-09-05): "Everything working fine" + three asks, all landed same day** — ① arrows "not working well": root cause = `updateCode`'s equality short-circuit collapsing same-frame snapshot taps; the keyboard now commits through `EditorViewModel.applyEditorKey` on the LIVE buffer (every tap/repeat tick counts once); ② **DEFAULT FLIPPED ON** per owner ("make the keyboard default user can off it") — Settings-off still restores strip+IME exactly; ③ **space-bar trackpad** (Samsung law): hold 260 ms → "⇄ caret" → slide → release places the caret, slide types nothing — pure `SpaceTrack` (12 dp/col, 28 dp/line, origin-quantized) + `moveCaretBy` on the selection branch. A long-lost PART_28_2 §0 build record was rebuilt in the same pass (several doc anchor writes across rounds had silently missed — lesson: verify the ANCHOR FILE, not the script).
    - **Device round 3 (2026-09-05): "in the sym many keys in one touch … make one key per button"** — the symbols layer rebuilt as three 10-wide rows of SINGLE characters (+ specials row): pair caps `()` `{}` `<>` `""`, and multi-char macros `->` `::` `==` `<=` `&&` all removed; brackets/quotes are their own keys, `->` = two taps (the 22.5 pair law lives on in the strip + the dev JSON schema, which can still express pairs). The language-tail caps left the utility row too — the letters layer is language-independent now (five rows, everyone). CI after 3 fix rounds (stale test bodies a half-applied script had left behind): ✅ `33972271855`; law pinned by `oneKeyPerButtonOnEveryShippedLayer`. **✅ MERGED into main 2026-09-05 on owner command** ("Merge it") — 28.2 rounds 1–3 are the shipped keyboard; 28.3/28.4 stay planned.
