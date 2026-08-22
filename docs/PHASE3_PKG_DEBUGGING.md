# Phase 3 — debugging `pkg` on device

Hands-on triage for the CodeC `pkg` frontend. Run these in the **CodeC Term**
tab; the "cross-check" section uses **Termux**.

## Resolution (verified on a real aarch64 device, 2026-08-22)

`pkg install nano` failed for four independent reasons, all now fixed. The
terminal accepted a manual `apt-get install nano` once each was addressed, and
`nano --version` → `GNU nano, version 9.2` with the `editor` alternative
registered by `update-alternatives`. **termux-exec was NOT required** for the
reviewed alternatives postinst — the shebang executes via the short
`/data/data/...` path without the LD_PRELOAD shim.

1. **`dpkg-perl : Depends: clang` (stale bootstrap).** The official dpkg
   recipe listed `clang` as a runtime dep of `dpkg-perl`; CodeC dropped it in
   `apply-recipe-overrides.sh`, but the published `userland-v2-dev` bootstrap
   predated that fix and seeded the stale `Depends: clang` into
   `var/lib/dpkg/status`. Fixed in the recipe; on-device workaround is a
   `sed` of the status file. The bootstrap rebuild + republish is what makes
   it permanent.
2. **`/data/user/0/` vs `/data/data/` alias.** Maintainer scripts are
   generated with the canonical `/data/data/...` prefix, but the app sets
   `$PREFIX=/data/user/0/...`. The `pkg` alternatives byte-check matched the
   wrong form. Fixed in `pkgScript()` (CANON_PREFIX).
3. **Missing `bin/sh`.** CodeC drops `termux-tools` (unwanted `termux-am`
   chain), but `termux-tools` is what normally provides `bin/sh`; dpkg runs
   every maintainer script through `sh` and refused with
   `'sh' not found in PATH`. Fixed in `pkgScript()` (symlink `bin/sh -> bash`).
4. **Missing `var/log/apt`.** apt aborted the non-download install phase with
   `E: Directory '.../var/log/apt/' missing`. Fixed in `pkgScript()`
   (`mkdir -p`), alongside the cosmetic `etc/apt/apt.conf.d` /
   `etc/apt/preferences.d` warnings.

## What was NOT the cause

- `python3`/`curl` absence — they ARE present in the bootstrap, and
  `pkg update` worked all along.
- `libmagic` — it is published and installable; the `nano : Depends: libmagic`
  line was collateral from the broken `clang` resolver.

## Tier 0 — is the Phase 3 bootstrap installed?

```sh
echo "$PREFIX"
which apt-get dpkg bash busybox python3 2>&1
dpkg --print-architecture          # aarch64 | x86_64
dpkg -l | grep -E 'apt|dpkg|bash|busybox'
```

## Tier 1 — runtime health

```sh
echo "$BASH_VERSION"                 # real version (empty = shim)
busybox                              # applet help
ls -l "$PREFIX/bin/sh"               # -> bash (dpkg needs this)
ls -l "$PREFIX/lib/libtermux-exec-ld-preload.so" 2>&1
```

## Tier 2 — reproduce a `pkg` failure

```sh
sh -x "$PREFIX/bin/pkg" install nano 2>&1 | tee "$PREFIX/tmp/pkg-install.log"
```

## Tier 3 — apt/dpkg directly (bypass the wrapper)

```sh
apt-get update
apt-cache search nano
dpkg --audit
```

## Cross-check the repository from Termux

```sh
curl -fsSL https://pabi277.github.io/CodeC/dev/dists/stable/Release -o Release
curl -fsSL https://pabi277.github.io/CodeC/dev/dists/stable/Release.sha256 -o Release.sha256
cat Release.sha256 && sha256sum Release    # tokens must match
```
