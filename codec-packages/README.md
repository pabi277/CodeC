# codec-packages

GPL-3.0 overlay on [termux-packages](https://github.com/termux/termux-packages).
CodeC does not vendor or install official Termux `.deb` binaries. Packages and
native dependencies are rebuilt with:

```text
TERMUX_APP_PACKAGE=com.codeci.ide
TERMUX_PREFIX=/data/data/com.codeci.ide/files/usr
```

## Why an overlay

`termux-packages` is large, so CI clones upstream and applies the small CodeC
prefix overlay. Official `scripts/run-docker.sh` and package recipes retain the
Android/Bionic patches that direct NDK builds lack.

Do not pass `-I` to `build-package.sh`: downloaded official packages target
`com.termux` and cannot be mixed with CodeC's prefix.

## Phase 2 package set

The minimal published `userland-v1` explicitly builds:

- `busybox`
- `bash`

The official dependency resolver also rebuilds required native libraries for the
CodeC prefix. The bootstrap intentionally removes Bash's `termux-tools`
dependency because CodeC supplies its own terminal environment and does not need
the `termux-am` Android wrapper chain.

Coreutils, nano, make, apt, dpkg, `pkg install`, and a package repository are not
provided by the minimal Phase 2 archive. Phase 3 builds them in a separate
CodeC-only channel; the existing `userland-v1` asset is not replaced until a new
apt/dpkg bootstrap passes device acceptance.

## Phase 3 / Phase 4 package repository

The selected format is an apt/dpkg-compatible repository with a guarded CodeC
`pkg` frontend. The expanded curated roots are:

- **Round 1 (Phase 3)**: `nano`, `less`, `coreutils`, `grep`, `sed`, `gawk`, `gzip`, `tar`, `make`, and `libmagic`.
- **Round 2 (Phase 4 Part 4.5)**: `git`, `wget`, `bat`, `ripgrep`, `fd`, `htop`, `tmux`, `tree`, `patch`, `diffutils`, `zstd`, `m4`, `autoconf`, `automake`, and `libtool`.

The published manifest is the only package promise; it includes the full source-built dependency closure.

Phase 3 bootstrap roots are `busybox`, `bash`, `apt`, `dpkg`, and
`termux-exec` (see `properties.codec.sh`). `termux-exec` is required at
runtime: dpkg executes maintainer scripts by executing the script file, and
Android only executes shebang scripts under the app prefix through the
termux-exec `LD_PRELOAD` library (the official Termux bootstrap ships the same
package). The overlay also rewrites the apt recipe's generated
`etc/apt/sources.list` from the official Termux repository URLs to the CodeC
development channel, so a bare `apt-get update` never points at Termux.

Generate and validate a repository from `.deb` files:

```sh
./scripts/generate-repository.sh /path/to/debs /path/to/repository aarch64 x86_64
python3 scripts/validate-repository.py /path/to/repository --architectures aarch64 x86_64
```

Build the curated closure with the official builder (Docker is the supported CI
path):

```sh
./scripts/build-package-repository.sh aarch64
./scripts/build-package-repository.sh x86_64
./scripts/build-package-manager-bootstrap.sh aarch64
./scripts/validate-bootstrap.py dist/bootstrap-phase3-aarch64.tar.gz
```

`validate-bootstrap.py` is the pre-release gate for bootstrap archives: it
checks the SHA-256 sidecar, the root-level prefix layout, real ELF
`bin/bash`/`bin/busybox`/`bin/apt-get`/`bin/dpkg`, a seeded dpkg status
database (apt/dpkg/bash/busybox/termux-exec installed), the termux-exec
`LD_PRELOAD` library and `libandroid-support.so`, and rejects path traversal,
unsafe symlinks, and `com.termux`/`termux-am`/official-Termux-repository
contamination. Host tests live in `tests/test_bootstrap.py`.

The `publish-bootstrap-release.yml` workflow promotes the validated artifacts
of a previously successful `CodeC package repository` run to the stable
development release `userland-v2-dev` (pre-release, `userland-*` tag, no APK)
without rebuilding: dispatch it with `source_run_id` set to that run.

The separate Phase 3 bootstrap seeds CodeC-built `apt`/`dpkg`, a dpkg status
database, and `termux-exec` (its `LD_PRELOAD` library is what lets dpkg run the
reviewed maintainer scripts on Android). It is a release artifact, not part of
the APK and not a replacement for `userland-v1` until the clean-device gate
passes.

These scripts never use `build-package.sh -I`. They apply only narrow source
transport overrides for the official `attr` and `libacl` recipes when Savannah's
HTTP endpoint returns 502; upstream versions and SHA-256 values remain unchanged.
They reject wrong architectures, wrong CodeC prefix paths, `com.termux`
contamination, unsafe symlinks, and unreviewed maintainer scripts. The only
allowed script exceptions are the explicitly validated alternatives postinst/prerm pairs
for `coreutils`, `less`, `nano`, `bat`, and `util-linux`. The generated
static tree contains
`Release`, `Packages`,
`Packages.gz`, package SHA-256 values, `repository.json`, and checksum sidecars.
Package filenames are sanitized for GitHub artifact/static-host compatibility;
Debian Version fields, including epoch versions such as `1:3.6.3`, are unchanged.

The development publishing workflow is `.github/workflows/package-repository.yml`.
It runs host security tests on pushes and performs the expensive source builds only
when manually dispatched. The repository build and Phase 3 bootstrap use distinct
Docker container names so `run-docker.sh` cannot reuse a container mounted with a
different Termux checkout. Publishing is an explicit GitHub Pages development channel; it must not be
confused with the official Termux repository. A validated build can be published
without rebuilding by passing its successful workflow run ID as `source_run_id`.

The Android `pkg` command is now a guarded frontend, but the current Phase 2
`userland-v1` bootstrap intentionally lacks apt/dpkg. On that release it reports
an actionable "package manager is not present" error rather than falling back to
Termux. A Phase 3 bootstrap containing CodeC-built apt/dpkg is required before
claiming on-device install success.

See [`docs/chat-phase3/PHASE3_PLAN.md`](../docs/chat-phase3/PHASE3_PLAN.md) for
trade-offs, security policy, device acceptance, signing, release, and rollback
requirements.

## Build

```sh
./scripts/build-bootstrap.sh aarch64
```

Docker and the official Termux package-builder are required. Output:

- `dist/bootstrap-aarch64.tar.gz`
- `dist/bootstrap-aarch64.tar.gz.sha256`

`assemble-bootstrap.sh` extracts package payloads into a neutral root, then
archives only `/data/data/com.codeci.ide/files/usr/` contents. Therefore archive
entries begin with `bin/`, `lib/`, `etc/`, and similar directories—not
`data/data/`.

## Published result

- Workflow run: `32376313030`
- Release: `userland-v1`
- SHA-256: `641c18d3a9daed41480f0d18e9fdbc807e393273ce492e600d60862e260d73f7`

Host the archive/checksum pair on GitHub Releases. Do not bundle them in the APK.
