# CodeC — the full journey

**Last updated:** 2026-09-01 · **State:** `main` = **`54ae06a`** (Phases 20–24
research/design docs via **PR #40**, merged 2026-09-01; chain: `54ae06a` ←
PR #39 `arena/01a05b6c-codec` git-branch-publishing + clear-error fixes ←
PR #38 Phase 18 ← PR #37 ← PR #36 …; verify with `git ls-remote origin main` —
the local clone is shallow). Phases 3–19 all complete/merged;
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
30. **Phase 20.1 — package toolchain round 4 (2026-09-01, `arena/01a05cb9-codec`, owner: "Phase 20 start").** CI/package-repo side of the compiler redesign — six new roots in `CODEC_REPOSITORY_PACKAGES`: **`libllvm`** (LLVM/Clang 21.1.8 — the actual compiler), `nodejs` 26.4.0-1, `npm` 11.19.0, `php` 8.5.1 (trimmed), `ruby` 3.4.1-2, `lua54` 5.4.8-10. Research against the live pinned tree invalidated two plan assumptions: there is **no `packages/gcc` or `packages/clang` recipe** at the pinned ref (clang is a `libllvm` subpackage whose include list already ships `bin/gcc`/`bin/g++`/`bin/c++`/`bin/cpp` driver symlinks — and `bin/cc`, which **CodeC strips** because `$PREFIX/bin/cc` is the app's own TCC frontend; invariant preserved, Phase 21.4 will revisit), and **npm was split out of nodejs** upstream (25.3.0-1) so it is its own root. New `apply-recipe-overrides.sh` blocks (all fail-loud, idempotent-marker style): clang `bin/cc` strip; nodejs `preinst` + npm `postinst` neutralized (last-definition-wins no-ops, python-pip precedent — maintainer scripts stay forbidden outside the five reviewed alternatives packages); **php trim** (apache/ldap/pgsql/gd configure flags + `postgresql` build-dep removed, `php-apache{,-ldap,-pgsql,-sodium}`/`php-ldap`/`php-pgsql`/`php-gd` subpackages excluded, `termux_step_post_make_install` replaced with a sodium-only twin — otherwise php would drag the apache2/openldap/postgresql/libgd source closures into the round); **lua54** `.alternatives` postinst replaced by plain relative `bin/lua`/`bin/luac` symlinks (repository validator allowlists only coreutils/less/nano/bat/util-linux). Ruby needed nothing. Tests: +10 hermetic cases in `test_recipe_overrides.py`; full repo suite **95 green**. **Updates:** dispatch `33506104710` hit the 360-min ceiling (6h01m) → D10 LLVM trim made permanent; `33544558167` aborted at ~3.5 min on a trim-shape bug (fixtures vs real recipe bytes — fixed in `49d8d81`, proven by real-byte rehearsal); trimmed `33547475854` hit the ceiling AGAIN (6h00m) → **D11: the build job fans out into base/llvm/langs parallel legs** (`CODEC_REPOSITORY_GROUP_*` single source, group-suffixed artifacts, publish-dev pattern-merge, bootstrap only in the base leg, `publish-bootstrap-release.yml` reads `-base`), tripwired end-to-end by new `test_ci_guardrails.py` cases (suite 100 green). Third dispatch `33585242675` proved the split (base legs green, langs running) but failed both llvm legs at VALIDATION: `libcompiler-rt` carries postinst/prerm (upstream subpkg-level `termux_step_create_subpkg_debscripts` — ndk-multilib interop only). D12 neutralizes it (no-op append, same precedent as python-pip); remaining closures audited clean. Fourth dispatch `33598824226`: base+llvm ALL GREEN; both langs legs red on one root — D7's php-gd *exclusion* couldn't stop arch-neutral buildorder resolving its `libgd` dep edge (→ libheif → gdk-pixbuf validator-trip / dead videolan x264 URL). D13 first deleted the seven phantom files — then dispatch `33625141182` showed buildorder validates the WHOLE tree graph (phpmyadmin's php-apache edge orphaned; all 6 legs red in 8 min). D13 revised: neuter in place (strip TERMUX_SUBPKG_DEPENDS + arch-skip + no-op debscripts, keep files). Owner then canceled the full v6 dispatch and directed salvage instead → D14: workflow learned `groups=langs` + `reuse_run_id=33598824226` (merge the 4 green legs' artifacts, rebuild only langs) + a per-arch marker gate (nano/clang/nodejs) so a partial merge can never publish. Owner dispatches: package-repository with those inputs, then bootstrap-release at source_run_id=33598824226. Bootstrap seed/manager roots untouched → published bootstrap stays byte-identical. **Gate state:** third dispatch (split legs) awaits the owner's Termux command (agent dispatch API is 403 — token has workflow push scope, not actions:write); publish auto-follows on green. C.2 ([repo-build-heavy] golang/rust) not started — its commit-message guard can't work on a dispatch-only workflow; design pivot noted in PART_20_2 §6. Record: `docs/chat-phase20/` (README + PART_20_1 §3/§6/§7 as implemented).
