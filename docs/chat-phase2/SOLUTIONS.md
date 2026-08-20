# Phase 2 solutions and durable decisions

## Final build architecture

CodeC uses the official `termux/termux-packages` build system rather than
hand-building shells with NDK Clang:

1. `codec-packages/scripts/build-bootstrap.sh` clones upstream.
2. `apply-prefix.sh` rewrites the package identity and prefix to:
   - package: `com.codeci.ide`
   - prefix: `/data/data/com.codeci.ide/files/usr`
3. Official `scripts/run-docker.sh` and `build-package.sh` rebuild packages and
   native dependencies for that prefix.
4. No official prebuilt `com.termux` package is installed (`-I` is forbidden).
5. The bootstrap-only Bash recipe omits `termux-tools`. CodeC supplies its own
   terminal environment and does not need `termux-am`; native Bash dependencies
   and official Android patches are retained.
6. `assemble-bootstrap.sh` extracts `.deb` files into a neutral root and archives
   only `data/data/com.codeci.ide/files/usr/` contents.

## Archive invariants

A valid release archive:

- starts with root-level `bin/`, `lib/`, `etc/`, `var/`, and related directories;
- contains `bin/bash` and `bin/busybox` as ELF executables;
- does not contain an archive entry beginning with `data/data/`;
- does not embed `/data/data/com.termux/files/usr` in runtime files;
- is accompanied by a plain-text SHA-256 file whose first token is 64 hex digits.

Archive validation writes `tar -tzf` output to a file before using `grep -q`,
avoiding SIGPIPE false failures under `pipefail`.

## Published result

- Successful workflow: `32376313030`
- Successful commit: `48ca4c83e2f94992148ccc7f1fa36db5a59008c9`
- Release: <https://github.com/pabi277/CodeC/releases/tag/userland-v1>
- Asset: `bootstrap-aarch64.tar.gz` (7,647,859 bytes)
- SHA-256: `641c18d3a9daed41480f0d18e9fdbc807e393273ce492e600d60862e260d73f7`
- Checksum asset: `bootstrap-aarch64.tar.gz.sha256`

The checksum was independently compared after download, and the corrected
archive showed root-level `bin/bash` and `bin/busybox` with no nested
`data/data` entries.

## Workflow policy

- `Bootstrap userland` is manual (`workflow_dispatch`) because rebuilding the
  custom userland is expensive and unnecessary for every app edit.
- `Build APK` continues on pushes and PRs.
- APK attachment is skipped for tags beginning with `userland-`; those releases
  contain userland assets only.
- Workflow-file changes may require a GitHub credential with workflow permission.

## Runtime installation

`UserlandInstaller` downloads the release pair, verifies SHA-256, and extracts
into the app-private `$PREFIX`. `ShellEnvironment` prefers real ELF Bash and must
not overwrite it with a shell shim. The app rewrites/preserves the Phase 1 `cc`
launcher after installation.

## Commands that define acceptance

Run each separately in CodeC Term:

```sh
echo $PREFIX
which bash
echo $BASH_VERSION
busybox
cc main.c -o a.out
./a.out
```

Required results:

- `$PREFIX` resolves to CodeC's private prefix (the `/data/user/0/...` alias is
  equivalent on Android);
- `which bash` points to `$PREFIX/bin/bash`;
- BusyBox prints its applet help;
- Phase 1 C compilation and execution still work;
- after one successful installation, the same tests work in airplane mode.

## Boundaries

Phase 2 does not provide apt, dpkg, `pkg install`, a package repository, or a new
Clang toolchain. Those belong to Phase 3. Never copy Termux prebuilt `.deb` files,
add `.` to `PATH`, or bake the bootstrap tarball into the APK.
