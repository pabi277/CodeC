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

## Phase 3 repository foundation

The selected format is an apt/dpkg-compatible repository with a guarded CodeC
`pkg` frontend. The first curated roots are `nano`, `less`, `coreutils`, `grep`,
`sed`, `gawk`, `gzip`, `tar`, `make`, and `libmagic` (the official recipe for
file-type identification). The published manifest is the only package promise;
it includes the full source-built dependency closure.

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
```

The separate Phase 3 bootstrap seeds CodeC-built `apt`/`dpkg` and a dpkg status
database. It is a release artifact, not part of the APK and not a replacement for
`userland-v1` until the clean-device gate passes.

These scripts never use `build-package.sh -I`. They apply only narrow source
transport overrides for the official `attr` and `libacl` recipes when Savannah's
HTTP endpoint returns 502; upstream versions and SHA-256 values remain unchanged.
They reject wrong architectures, wrong CodeC prefix paths, `com.termux`
contamination, unsafe symlinks, and unreviewed maintainer scripts. The only
allowed script exception is the explicitly validated official coreutils
`cat.alternatives` postinst/prerm pair. The generated static tree contains
`Release`, `Packages`,
`Packages.gz`, package SHA-256 values, `repository.json`, and checksum sidecars.

The development publishing workflow is `.github/workflows/package-repository.yml`.
It runs host security tests on pushes and performs the expensive source builds only
when manually dispatched. Publishing is an explicit GitHub Pages development
channel; it must not be confused with the official Termux repository.

The Android `pkg` command is now a guarded frontend, but the current Phase 2
`userland-v1` bootstrap intentionally lacks apt/dpkg. On that release it reports
an actionable "package manager is not present" error rather than falling back to
Termux. A Phase 3 bootstrap containing CodeC-built apt/dpkg is required before
claiming on-device install success.

See [`docs/PHASE3_PLAN.md`](../docs/PHASE3_PLAN.md) for trade-offs, security
policy, device acceptance, signing, release, and rollback requirements.

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
