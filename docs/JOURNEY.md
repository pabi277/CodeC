# CodeC — the full journey

**Last updated:** 2026-08-24 · **State:** Parts A, B, C, and D ✅
device-verified — Phase 3's device-acceptance gate is complete. PR #14 has the
signing implementation, signed Pages/client acceptance, the key-seeded
bootstrap build/release, and now the final clean-device evidence.

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

## 5. Part A shipped without a rebuild; Part B merged, one rebuild left (2026-08-22)

**Part A — DONE, and the ~104-minute build turned out to be unnecessary.**
The published `userland-v2-dev` bootstrap predated the `dpkg-perl` clang
recipe fix, so a fresh device's seeded status DB contained
`Depends: perl, clang, make, dpkg (= 1.22.6-5)`. Since the *entire* content
delta between the published artifact and a full rebuild was that single
line, the owner ran `codec-packages/scripts/repair-bootstrap-status.sh`
(Path 2 of `docs/PART_A_ARTIFACT_REPAIR.md`) in Termux on both published
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
[`REPOSITORY_SIGNING.md`](REPOSITORY_SIGNING.md).

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
[`PHASE3_DEVICE_ACCEPTANCE.md`](PHASE3_DEVICE_ACCEPTANCE.md) §8.

**This closes Part D and Phase 3's device-acceptance gate. No section in
`PHASE3_DEVICE_ACCEPTANCE.md` remains open.** PR #14 has the code, docs, and
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
5. **Phase 4 — polish** (Parts E–F: storage access,
   `termux-setup-storage`-equivalent, security confirmation prompt, themes,
   signing UX).
