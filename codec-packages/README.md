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
promised by this minimal Phase 2 archive. Package management is Phase 3.

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
