# Phase 3 clean-device acceptance checklist

**Status: PARTIALLY PASSED (2026-08-23).** Sections 0–6 passed on a clean
Samsung SM-A356E (Android 16, aarch64) using PR #14 commit `8e95a16` for the
interrupted-install fix. Section 7 is deferred because only one device is
available; do not claim the full Part C exit condition until the v1 → Phase 3
upgrade path passes on a second device.

This checklist is the M2 gate from [`PHASE3_PLAN.md`](PHASE3_PLAN.md). Run it
after the `userland-v2-dev` release is published and a fresh APK (versionName
≥ 1.3.15) is installed.

## 0. Preconditions

- Release `userland-v2-dev` published with four assets
  (`bootstrap-phase3-aarch64.tar.gz`, `bootstrap-phase3-x86_64.tar.gz`, and
  both `.sha256` sidecars), built from a green `CodeC package repository` run.
- A fresh `CodeC-IDE` APK from a green `Build APK` run on this branch.
- No CodeC app currently installed on the device (uninstall first so stale
  shims/userland cannot mask defects).

## 1. Fresh install + bootstrap

- [x] Install the APK, open CodeC → Term.
- [x] Automatic install (or tap **Install userland**) downloads
      `bootstrap-phase3-<arch>.tar.gz`, verifies SHA-256, and reports
      `userland: ready`.
- [x] Progress shows the Phase 3 release was selected
      (`userland-v2-dev`), not `userland-v1`.

## 2. Runtime smoke (online)

Run each command separately in CodeC Term:

```sh
uname -a
echo $PREFIX
which bash
echo $BASH_VERSION
busybox
which apt-get dpkg
ls $PREFIX/lib | grep -c '\.so'
echo "$LD_PRELOAD"
test -f "$PREFIX/lib/libtermux-exec-ld-preload.so" && echo termux-exec-ok || echo termux-exec-optional-absent
dpkg --print-architecture
dpkg -l | grep -E 'apt|dpkg|termux-exec'
```

| Check | Required result |
|---|---|
| `$PREFIX` | `/data/data/com.codeci.ide/files/usr` (or `/data/user/0/...` alias) |
| `which bash` | `$PREFIX/bin/bash` (real ELF Bash, not a shim) |
| `$BASH_VERSION` | a real Bash version string |
| `busybox` | applet help output |
| `which apt-get dpkg` | both resolve under `$PREFIX/bin` |
| `echo "$LD_PRELOAD"` | termux-exec path when the best-effort library was built; empty is accepted when the maintainer-script test below passes |
| `dpkg --print-architecture` | `aarch64` (or `x86_64`) |
| `dpkg -l` | `apt`, `dpkg`, `bash`, `busybox` installed; `termux-exec` is optional |

**2026-08-23 evidence:** all required runtime checks passed on the SM-A356E.
The published bootstrap had no termux-exec library and left `LD_PRELOAD` empty,
but nano's reviewed postinst and `update-alternatives` ran successfully. This
confirms the later Part B finding that termux-exec is best-effort, not a
functional acceptance requirement.

## 3. Package operations

```sh
pkg update
pkg search nano
pkg install nano
nano --version
pkg uninstall nano
pkg upgrade
```

| Check | Required result |
|---|---|
| `pkg update` | CodeC repository index refreshed, no Termux URL mentioned |
| `pkg search nano` | finds `nano` in the CodeC index |
| `pkg install nano` | downloads, preflight passes, installs, and the **postinst alternatives script runs** (with or without the optional termux-exec preload) |
| `nano --version` | nano runs |
| `pkg uninstall nano` | removed cleanly; `bin/editor` alternative removed |
| `pkg upgrade` | succeeds (nothing to upgrade on a fresh userland is fine) |

Then the alternatives closure:

```sh
pkg install coreutils
pkg install less
pkg install nano
which cat less pager editor
pager -V
```

| Check | Required result |
|---|---|
| `which pager editor` | resolve via `update-alternatives` links |
| `pager -V` | reports less |
| `cat`, `less` | real coreutils/less binaries under `$PREFIX` |

**2026-08-23 evidence:** update/search/install/uninstall/upgrade all passed;
nano 9.2 ran; `sed` upgraded; `cat`, `less`, `pager`, `editor`, and `vi`
resolved under `$PREFIX`; `pager -V` reported less 704; and `dpkg --audit` was
silent. The unsigned `Release` hash warning remains expected until Part D.

Negative checks:

- [x] `cat $PREFIX/etc/apt/sources.list` contains only the CodeC development
      channel (`https://pabi277.github.io/CodeC/dev`) — no `termux.dev`.
- [x] `pkg install` of a package is never satisfied from an official Termux
      repository (no `com.termux` in any `dpkg -l` entry).
- [x] `pkg uninstall bash` (or `busybox`, `apt`, `dpkg`) is refused.

## 4. Compiler smoke (before and after package operations)

```sh
echo 'int main(void){return 0;}' > main.c
cc main.c -o a.out
./a.out
echo "exit=$?"
```

- [x] `cc` (embedded TCC) compiles and `./a.out` exits 0 **before** package
      operations, and again **after** `pkg install nano` / `pkg uninstall nano`.

## 5. Offline / airplane mode

1. Close CodeC.
2. Enable airplane mode.
3. Reopen CodeC → Term.

- [x] `which bash`, `busybox`, `cc main.c -o a.out`, `./a.out` all work with no
      network.
- [x] `pkg update` reports offline (installed packages remain usable) and does
      not touch any external repository.

## 6. Interrupted install recovery

1. Start `pkg install nano` and kill the app mid-download (or enable airplane
      mode mid-download).
2. Reopen CodeC → Term (online).
3. Run `pkg install nano` again.

- [x] The interrupted transaction is repaired/completed; no partial `.deb` is
      installed; `pkg repair` reports no pending transaction afterwards.

**2026-08-23 evidence and fix:** force-stop during a throttled nano download
left `transaction.pending`, a 173,002-byte partial archive, and lock PID `18339`.
The original app blocked both retry and `pkg repair` on that stale lock. PR #14
commit `8e95a16` now checks the owner PID and atomically reclaims a dead-owner
lock. Repeating the test with lock PID `6549` produced the recovery message,
resumed and installed `libmagic` + nano, left `dpkg --audit` silent, and cleared
the pending marker without manual deletion.

Also: delete `usr/var/lib/codec-pkg/transaction.pending` manually is NOT a
supported recovery path — `pkg repair` must do it.

## 7. Upgrade path (v1 → Phase 3)

1. On a second device (or after uninstall), install an APK that predates this
   branch and install `userland-v1`.
2. Install the new APK, open Term.
- [ ] The app upgrades the userland to `userland-v2-dev` automatically
      (progress shows `upgrading userland-v1 to userland-v2-dev`), real Bash
      keeps working, and `pkg update` then works.

## Result

Sections 0–6 passed on the available arm64 device. Section 6 required the
stale-lock fix in PR #14 and passed when repeated with that APK. Section 7
remains unchecked and deferred until a second device with `userland-v1` is
available; do not downgrade the only phone to simulate it.

If any item fails: keep the failure, the device model/Android version, and the
Term output; do not merge as "Phase 3 complete". The safe fallback
(`userland-v1`) keeps the app functional in the meantime.
