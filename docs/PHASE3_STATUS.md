# CodeC IDE Phase 3 status and handoff

**Date:** 2026-08-21  
**Branch:** `arena/01a0248f-codec`

This is the short handoff for the next Phase 3 chat. The detailed architecture,
security policy, trade-offs, and rollback plan remain in
[`PHASE3_PLAN.md`](PHASE3_PLAN.md). Clean-device acceptance steps are in
[`PHASE3_DEVICE_ACCEPTANCE.md`](PHASE3_DEVICE_ACCEPTANCE.md).

## Completed

### Repository and CI

- Official Termux recipes are rebuilt through the CodeC overlay.
- CodeC package identity and prefix remain:

  ```text
  com.codeci.ide
  /data/data/com.codeci.ide/files/usr
  ```

- No official `com.termux` package or repository is used.
- No `build-package.sh -I` dependency shortcut is used.
- The curated repository and complete source-built dependency closure build for:

  ```text
  aarch64
  x86_64
  ```

- Successful package/bootstrap CI run: `32469769089`.
- Development APT repository publication succeeded in run `32484160427`.
- Current published repository URL:

  ```text
  https://pabi277.github.io/CodeC/dev
  ```

- Publication can reuse an earlier successful artifact run through the
  `source_run_id` workflow input, avoiding another full package rebuild.

### Package/security foundation

- Repository metadata, `Packages`, `Packages.gz`, `Release`, manifest, and
  checksum generation are implemented.
- Debian epoch colons are sanitized only in published filenames so GitHub
  artifacts work; Debian `Version` fields remain unchanged.
- Package payloads are checked for ABI, prefix, path traversal, symlink escape,
  `com.termux` contamination, size, and SHA-256.
- Maintainer scripts are rejected except for the explicitly reviewed generated
  alternatives scripts from the official `coreutils`, `less`, and `nano` recipes.
- The same alternatives allowlist is enforced by host validation and the Android
  `pkg` preflight.
- Host repository tests cover the security/metadata cases; the new
  `validate-bootstrap.py` adds pre-release validation of the bootstrap archives
  (layout, ELF binaries, dpkg status entries, termux-exec + libandroid-support
  presence, sidecar SHA-256, traversal/symlink safety, contamination).

### Bootstrap completion (this session)

- **`termux-exec` added to the Phase 3 bootstrap closure.** The previous
  bootstrap (run `32469769089`) lacks it, and without its
  `libtermux-exec-ld-preload.so` (exported as `LD_PRELOAD`) the Android kernel
  cannot execute shebang scripts under the CodeC prefix. dpkg runs maintainer
  scripts by executing the script file, so `pkg install nano` (and the
  coreutils/less alternatives) cannot configure packages without it. The
  official Termux bootstrap ships the same package for this reason.
- **The apt recipe's generated `etc/apt/sources.list` now points at the CodeC
  development channel** instead of the official Termux repository URLs, so even
  a bare `apt-get update` stays CodeC-only (the `pkg` frontend always supplies
  its own sources list regardless).
- The Android shell environment exports `LD_PRELOAD` to the termux-exec library
  **only when the file exists** (PTY environment, login profile, and defensively
  inside the `pkg` backend check), so Phase 2 userlands are unaffected.

### Android/runtime foundation

- A guarded terminal `pkg` frontend exists for `update`, `search`, `install`,
  `upgrade`, `uninstall`, and `repair`.
- State, locks, apt lists/cache, and transaction markers are private to the CodeC
  prefix.
- The package frontend uses the published development URL:

  ```text
  https://pabi277.github.io/CodeC/dev
  ```

- A runtime shell-launch check detects broken ELF dependencies such as a
  missing `libandroid-support.so` and falls back to BusyBox or Android
  `/system/bin/sh` instead of killing the PTY; the installer now reports the
  missing library name explicitly.
- `UserlandInstaller` selects the Phase 3 bootstrap release
  (`userland-v2-dev`, assets `bootstrap-phase3-<arch>.tar.gz`) when published
  and automatically falls back to `userland-v1` when it is absent or unusable.
  Downloads go to `.partial` files, SHA-256 is verified before extraction,
  extraction happens in a staging directory and the live prefix is replaced by
  an atomic same-filesystem rename with rollback, and an existing runnable
  userland is upgraded in place (v1 → Phase 3) or left alone when the target
  release matches. `cc`, TCC, and a real ELF Bash are never overwritten with a
  shim.
- Phase 1 TCC `cc`, executable projects, and offline startup remain intact.

## Remaining work

1. **Publish the rebuilt Phase 3 bootstrap as the `userland-v2-dev` release.**
   The publishing workflow (`.github/workflows/publish-bootstrap-release.yml`)
   is written but **could not be pushed**: Arena's GitHub App lacks the
   `workflows` permission. Either grant that permission and re-push, or push
   the file manually (see [`ci-pending/`](ci-pending/README.md)). Then dispatch
   it with the successful rebuilt run's ID as `source_run_id`.
2. **Rebuild the Phase 3 bootstrap** with the completed closure
   (`busybox`, `bash`, `apt`, `dpkg`, `termux-exec`) — dispatch the existing
   `CodeC package repository` workflow on this branch. The previous artifacts
   (run `32469769089`) are proven incomplete for acceptance: no termux-exec and
   an official-Termux-repository `sources.list` (both fixed in the build
   scripts, which is why a rebuild is required).
3. **Clean Android device tests** — see
   [`PHASE3_DEVICE_ACCEPTANCE.md`](PHASE3_DEVICE_ACCEPTANCE.md).
4. Production repository signing / key distribution (M3).

## Known current issue

The published Phase 2 `userland-v1` archive can contain an ELF Bash whose
dynamic library `libandroid-support.so` is absent. The app-side launch check
and fallback prevent this from killing the terminal and the installer now
re-reports the missing library; installing the rebuilt Phase 3 bootstrap (which
includes `libandroid-support.so`) is the durable fix. Do not claim Phase 2 or
Phase 3 clean-device acceptance until the corrected bootstrap is installed and
the Bash/BusyBox/runtime-library smoke tests pass.

## Important release facts

- The app now targets the Phase 3 release tag `userland-v2-dev` with assets
  `bootstrap-phase3-aarch64.tar.gz` / `bootstrap-phase3-x86_64.tar.gz` (+
  `.sha256`), falling back to `userland-v1` / `bootstrap-aarch64.tar.gz`.
- Until that release is published, devices keep installing `userland-v1`
  (unchanged behaviour).
- The current published Pages tree is `/CodeC/dev`, not `/CodeC/packages/dev`.
- Keep all work on `arena/01a0248f-codec`.
- Do not add `.` to `PATH`, use official Termux repositories, overwrite `cc`,
  or replace real ELF Bash with a shim.
