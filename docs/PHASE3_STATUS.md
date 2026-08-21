# CodeC IDE Phase 3 status and handoff

**Date:** 2026-08-21  
**Branch:** `arena/01a01fef-codec`

This is the short handoff for the next Phase 3 chat. The detailed architecture,
security policy, trade-offs, and rollback plan remain in
[`PHASE3_PLAN.md`](PHASE3_PLAN.md).

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

- Verified public metadata paths:

  ```text
  https://pabi277.github.io/CodeC/dev/dists/stable/Release
  https://pabi277.github.io/CodeC/dev/dists/stable/Release.sha256
  https://pabi277.github.io/CodeC/dev/repository.json
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
- Host repository tests currently cover six security/metadata cases.

### Android/runtime foundation

- A guarded terminal `pkg` frontend exists for `update`, `search`, `install`,
  `upgrade`, `uninstall`, and `repair`.
- State, locks, apt lists/cache, and transaction markers are private to the CodeC
  prefix.
- The package frontend uses the published development URL:

  ```text
  https://pabi277.github.io/CodeC/dev
  ```

- A runtime shell-launch check now detects broken ELF dependencies such as a
  missing `libandroid-support.so` and falls back to BusyBox or Android
  `/system/bin/sh` instead of killing the PTY.
- Phase 1 TCC `cc`, executable projects, and offline startup must remain intact.

## Remaining work

1. Publish the successful `bootstrap-phase3-aarch64.tar.gz` and
   `bootstrap-phase3-x86_64.tar.gz` as a stable development release asset. CI
   artifacts alone expire.
2. Update `UserlandManifest` and `UserlandInstaller` to download and verify the
   Phase 3 bootstrap, while retaining a safe Phase 2 fallback.
3. Make userland readiness verify the complete runtime, not only ELF magic, and
   provide a repair/reinstall path for the broken `userland-v1` archive.
4. Install the new APK on a clean Android device and run:

   ```sh
   pkg update
   pkg search nano
   pkg install nano
   pkg uninstall nano
   pkg upgrade
   ```

5. Test `cc main.c -o a.out` and `./a.out` before and after package operations,
   then repeat startup in airplane mode.
6. Add production repository signing/key distribution. The current development
   channel uses HTTPS and SHA-256 verification and is not yet a signed production
   channel.
7. Document and test bootstrap/package rollback and interrupted-install recovery.

## Known current issue

The published Phase 2 `userland-v1` archive can contain an ELF Bash whose dynamic
library `libandroid-support.so` is absent. The latest APK-side fallback prevents
this from killing the terminal, but it does not make that old Bash usable. Do not
claim Phase 2 clean-device acceptance until the corrected bootstrap is installed
and the Bash/BusyBox/runtime-library smoke tests pass.

## Important release facts

- The current app still needs a Phase 3 bootstrap release before `pkg install` can
  work on a clean device; `userland-v1` intentionally lacks apt/dpkg.
- The current published Pages tree is `/CodeC/dev`, not `/CodeC/packages/dev`.
- Keep all work on `arena/01a01fef-codec`.
- Do not add `.` to `PATH`, use official Termux repositories, overwrite `cc`, or
  replace real ELF Bash with a shim.
