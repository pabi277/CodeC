# Phase 3 clean-device acceptance checklist

**Status: ALL SECTIONS PASSED, INCLUDING THE FINAL CLEAN-DEVICE GATE
(2026-08-24). Phase 3 acceptance is complete.**
Sections 0–6 passed on a clean Samsung SM-A356E (Android 16, aarch64); section 7
passed on a separate arm64 device through an in-place v1 upgrade; and section 8
passed live signature, tamper rejection, APT, package lifecycle, audit, and
compiler checks. Both rebuilt archives passed exact-keyring CI validation and
were published by run `32648783080`. On 2026-08-24 a full uninstall + fresh
install of that exact published bootstrap on a real aarch64 device (Android 16)
passed every remaining section-8 item — see the "Final clean-device evidence"
block at the end of section 8. No item in this checklist is open.

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

**2026-08-23 Part C evidence:** update/search/install/uninstall/upgrade all
passed; nano 9.2 ran; `sed` upgraded; `cat`, `less`, `pager`, `editor`, and `vi`
resolved under `$PREFIX`; `pager -V` reported less 704; and `dpkg --audit` was
silent. The then-published repository was unsigned; the warning observed in
this historical run is not acceptable in the Part D signed-channel test.

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
- [x] The app upgrades the userland to `userland-v2-dev` automatically
      (progress shows `upgrading userland-v1 to userland-v2-dev`), real Bash
      keeps working, and `pkg update` then works.

**2026-08-23 evidence and fix:** released v1.3.14 wrote the legacy marker
`.userland-vuserland-v1`; the upgrader and its test had assumed the nonexistent
`.userland-v-userland-v1`. PR #14 commit `a4e5af6` corrected the exact marker,
and Build APK run `32632744434` passed. Because separate CI runs use different
debug certificates, the unchanged v1.3.14 and PR #14 APK payloads were re-signed
with one local test-only key so Android could perform a genuine in-place update.
The app visibly reported `upgrading userland-v1 to userland-v2-dev`, downloaded
the 23,926,127-byte archive, verified SHA-256, extracted it, and reported ready.
The resulting device had the `userland-v2-dev` marker; Bash, apt, dpkg, and curl
worked; `dpkg --audit` was silent; update/search/nano 9.2 install passed; no
`com.termux` contamination appeared; and embedded `cc` printed `upgrade-ok`
with exit 0.

## 8. Part D signed-channel acceptance — client path passed

**Publication prerequisite passed (2026-08-23).** Initial signed workflow run
`32641097388` established both valid signature forms. The first CodeC-device
`pkg update` then exposed an APT grammar defect: blank lines terminated the
Release stanza before its hashes. Commit `0fa9823` removed those separators and
made validation reject them; corrective publication run `32642631785` reused
existing artifacts, skipped both expensive builds, and deployed successfully.
A separate Termux fetch verified the live exact signing subkey
`328500868CE9B0F74B62CEFC1D7D52F6F8135015`, committed keyring, Release
cleartext/checksum, and tamper rejection.

Install the green APK containing Part D and open Term once so its bootstrap
writer installs the public key. Then run:

```sh
set -eu
KEY="$PREFIX/etc/apt/keyrings/codec-archive-keyring-v1.gpg"
STATE="$PREFIX/var/lib/codec-pkg"
test -s "$KEY"
test "$(sha256sum "$KEY" | awk '{print $1}')" = \
  e9c36bb618d747bd303104f86869843b41239cfd1ab445dc2f9cf3e71e19a807
pkg update
grep -F "signed-by=/data/data/com.codeci.ide/files/usr/etc/apt/keyrings/codec-archive-keyring-v1.gpg" \
  "$STATE/sources.list"
! grep -F 'trusted=yes' "$STATE/sources.list"
grep -qx 'Origin: CodeC' "$STATE/Release"
grep -qx 'Suite: stable' "$STATE/Release"
```

Required result: every command succeeds; APT reports a valid CodeC repository
with no unsigned/weak-security or `No Hash entry in Release file` warning; and
no official Termux source appears.

Prove that the device's exact trust file accepts the deployed signature and
rejects changed cleartext without changing the live repository:

```sh
set -eu
D="$HOME/codec-signing-acceptance"
rm -rf "$D" && mkdir -p "$D"
curl -fsSLo "$D/InRelease" \
  https://pabi277.github.io/CodeC/dev/dists/stable/InRelease
gpgv --keyring "$PREFIX/etc/apt/keyrings/codec-archive-keyring-v1.gpg" \
  --output "$D/Release" "$D/InRelease"
grep -qx 'Origin: CodeC' "$D/Release"
grep -qx 'Suite: stable' "$D/Release"
sed 's/^Origin: CodeC$/Origin: Attacker/' \
  "$D/InRelease" > "$D/InRelease.tampered"
grep -qx 'Origin: Attacker' "$D/InRelease.tampered"
if gpgv --keyring "$PREFIX/etc/apt/keyrings/codec-archive-keyring-v1.gpg" \
  --output "$D/Release.tampered" "$D/InRelease.tampered"; then
  echo 'FAIL: tampered InRelease was accepted' >&2
  exit 1
else
  echo 'tamper-rejected'
fi
```

- [x] committed keyring hash matches on device;
- [x] `pkg update` succeeds through `signed-by=` with no unsigned/hash warning;
- [x] independent device `gpgv` verification succeeds;
- [x] modified `InRelease` is rejected;
- [x] `pkg install nano`, `nano --version`, uninstall, `dpkg --audit`, and the
      compiler smoke still pass after signing is enabled;
- [x] CI run `32643383952` built both bootstrap architectures and validated the
      embedded keyring byte-for-byte against the committed v3 public key;
- [x] publish those revalidated assets as `userland-v2-dev` (release run
      `32648783080`);
- [x] pass the rebuilt key-seeded bootstrap on a clean device.

**2026-08-23 CodeC-device evidence:** the installed keyring hash matched;
`pkg update` returned 0 with no unsigned, weak-security, or missing-hash warning;
Origin/Suite matched; device `gpgv` emitted `VALIDSIG` for the exact v3 subkey;
a changed signed Origin was rejected; nano 9.2 plus libmagic downloaded and
installed from the signed repository; the reviewed alternatives postinst/prerm
ran; removal succeeded; `dpkg --audit` was empty; and embedded `cc` printed
`partd-compiler-ok`. The dangling-editor repair and absent-mandoc messages were
non-fatal alternatives warnings, followed by a clean audit.

### Final clean-device evidence (2026-08-24) — the last open item, now closed

A verified pre-uninstall backup (checksum, `gzip -t`, archive listing, and an
independent-copy `cmp`) was confirmed before the destructive step. CodeC IDE
was then fully uninstalled (Settings → Apps → Uninstall) and reinstalled from
`app-debug-partd-test-signed.apk` (a test-only re-signed CI artifact used only
so Android would accept the sideload; it carries no repository-trust weight).
On first launch, Term opened online and the automatic installer downloaded the
already-published `userland-v2-dev` aarch64 archive end to end
(**23,928,215 bytes**, matching the digest recorded above for release run
`32648783080`), printed `userland: verifying SHA-256…`, then
`userland: extracting into /data/user/0/com.codeci.ide/files/usr`, then
`userland: ready`, reaching a real `codec $` prompt with no manual
"Install userland" tap required.

The device (`SM-A356E`-class aarch64 phone, Android 16, kernel
`5.15.189-android13-3-33470412`) then passed, in order:

1. **Runtime smoke:** `$PREFIX` = `/data/user/0/com.codeci.ide/files/usr`;
   `which bash` → real ELF `$PREFIX/bin/bash`; `$BASH_VERSION` = `5.3.15(1)-release`;
   `busybox` banner printed; `apt-get`/`dpkg` resolve under `$PREFIX`; `dpkg
   --print-architecture` → `aarch64`; `grep clang $PREFIX/var/lib/dpkg/status`
   found nothing (`exit=1`) — the Part B fix held on the rebuilt archive.
2. **No-contamination check:** an unanchored `dpkg -l | grep -E
   'doxygen|swig|tcl|tor|fontconfig|termux-keyring'` initially matched `sed`
   (substring hit on "edi**tor**"/"transf**or**ming" in its description, not a
   real package). Re-run with exact package-name matching —
   `dpkg -l | awk '$1=="ii"{print $2}' | grep -Ex
   'doxygen|swig|tcl|tor|fontconfig|termux-keyring'` — printed no lines,
   confirming zero build-dependency or Termux-keyring contamination on the
   rebuilt bootstrap. The checklist's own pattern above should be read as
   requiring an exact-name match, not a bare substring search; a future edit
   may tighten the sample command.
3. **`sources.list`:** contains only
   `deb [signed-by=.../codec-archive-keyring-v1.gpg]
   https://pabi277.github.io/CodeC/dev stable main` — no `termux.dev`.
4. **Signed `pkg update` (the specific section-8 gate):** installed keyring
   SHA-256 is `e9c36bb618d747bd303104f86869843b41239cfd1ab445dc2f9cf3e71e19a807`
   — an exact match to the value pinned above. `pkg update` printed
   `Hit:1 https://pabi277.github.io/CodeC/dev stable InRelease` with no
   unsigned/weak-security/missing-hash warning. `$STATE/sources.list` contains
   `signed-by=` and no `trusted=yes`; `$STATE/Release` contains
   `Origin: CodeC` and `Suite: stable`.
5. **Independent tamper check:** a separate `curl` fetch of the live
   `InRelease` plus `gpgv --keyring codec-archive-keyring-v1.gpg` reported
   `Good signature from "CodeC Repository Signing (2026 v3)"` using RSA key
   `328500868CE9B0F74B62CEFC1D7D52F6F8135015`; after flipping the `Origin:`
   line, the same `gpgv` call reported `BAD signature` and the script printed
   `tamper-rejected`.
6. **curl/package lifecycle:** `curl` resolved under `$PREFIX/bin` and
   `curl -fsSI` against the Release URL printed `tls-ok`; `pkg install nano`
   pulled `libmagic` + `nano` from the signed repository, ran the reviewed
   alternatives `postinst` (`update-alternatives: using .../bin/nano to
   provide .../bin/editor`), `nano --version` reported GNU nano 9.2, `editor`/
   `pager`/`vi` all resolved under `$PREFIX`, `pager -V` reported `less 704`,
   and `dpkg --audit` was silent. `pkg uninstall nano` cleanly fell the
   `editor` alternative back to busybox `vi`.
7. **Compiler smoke:** `cc t.c -o a.out && ./a.out` printed `clean-device-ok`.

Every command in this final pass succeeded with the expected result. This
closes the only remaining unchecked item in this document. Rotation,
revocation, and rollback procedures are in
[`REPOSITORY_SIGNING.md`](REPOSITORY_SIGNING.md).

## Result

Every item in this checklist — Parts A, B, C, and the Part D signed-client and
final clean-device paths — has passed on real arm64 devices; both key-seeded
archives passed CI validation and are published as `userland-v2-dev`. No
section-8 item remains open. Phase 3's device-acceptance gate is complete. The
test-only APK key used solely to align CI artifact signatures is not a
production signing key and is unrelated to repository signing.

If any item fails in the future (e.g. against a new bootstrap rebuild): keep
the failure, the device model/Android version, and the Term output; do not
claim "Phase 3 complete" without re-verifying. The safe fallback
(`userland-v1`) keeps the app functional in the meantime.
