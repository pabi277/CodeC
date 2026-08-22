# CodeC IDE Phase 3 status and handoff

**Date:** 2026-08-22  
**Branch:** `arena/01a028e2-codec`

This is the short handoff for the next Phase 3 chat. The detailed architecture,
security policy, trade-offs, and rollback plan remain in
[`PHASE3_PLAN.md`](PHASE3_PLAN.md). Clean-device acceptance steps are in
[`PHASE3_DEVICE_ACCEPTANCE.md`](PHASE3_DEVICE_ACCEPTANCE.md). The full
chronology is in [`JOURNEY.md`](JOURNEY.md); the remaining task list is in
[`NEXT_STEPS.md`](NEXT_STEPS.md).

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
- App CI is green: run `32497218472` (`e1ff4ef`) compiles the app and passes
  all unit tests, including the 20 new `UserlandInstallerTest` and 6 new
  `ShellEnvironmentTest` tests (the `assembleDebug` CI task also runs
  `:app:testDebugUnitTest` via `gradle-bootstrap`).
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

### Phase 3 bootstrap rebuilt and published (2026-08-22)

- The approved rebuild ran green on `arena/01a0248f-codec`
  (run `32546404876`, commit `9d7be9d`): four bootstrap roots
  (`busybox`, `bash`, `apt`, `dpkg`) plus the full source-built closure for
  `aarch64` and `x86_64`, CodeC-only `sources.list`, seeded dpkg status
  database, and in-prefix symlinks relativized at assembly time.
- The artifacts were promoted to the pre-release
  **`userland-v2-dev`** (release run `32550349154`) with
  `bootstrap-phase3-{aarch64,x86_64}.tar.gz` and their two-field
  SHA-256 sidecars; `userland-v1` remains "Latest", so the Phase 2
  fallback is untouched.
- The termux-exec LD_PRELOAD library is best-effort: the official recipe
  needs a Termux-farm-only prebuilt, so this release may carry the archive
  without `lib/libtermux-exec-ld-preload.so` (the validator warns instead
  of failing). The device acceptance run decides whether it is required;
  dpkg maintainer scripts that need it would be the symptom.

## This session — on-device debugging (2026-08-22)

Installing on a real aarch64 phone and driving `pkg` end-to-end surfaced five
bugs and two plan-changing findings. Full trace/diagnosis in
[`PHASE3_PKG_DEBUGGING.md`](PHASE3_PKG_DEBUGGING.md). Four are fixed in app
code (`ShellEnvironment.pkgScript()`) — no bootstrap rebuild needed:

1. **`dpkg-perl : Depends: clang`** — stale bootstrap seeded the pre-fix
   dependency; `pkg` now self-heals the status line.
2. **`/data/user/0/` vs `/data/data/` alias** — alternatives byte-check now
   matches the canonical prefix.
3. **Missing `bin/sh`** — `pkg` symlinks `bin/sh → bash` (dpkg needs `sh`).
4. **Missing `var/log/apt`** — `pkg` creates it (plus apt.conf.d/preferences.d).
5. **Over-strict symlink preflight** — in-prefix `../` (nano license link) is
   now allowed; only true escapes are rejected.

Findings:
- **termux-exec was NOT required** — the nano postinst ran (and registered
  `editor`) without `libtermux-exec-ld-preload.so`.
- **Seeded packages never ran postinst** — so `pager` is missing and seeded
  packages fail `dpkg --audit` (no `md5sums`). Bootstrap content gap, not a
  `pkg` defect.

Verified working on device: `pkg update / search / install / uninstall /
upgrade`, `nano --version` (GNU nano 9.2), `editor` alternative, and `cc`
compile/run.

## Remaining work

Broken into ordered parts in [`NEXT_STEPS.md`](NEXT_STEPS.md):

1. **Part A — republish a clean bootstrap** (the `clang` recipe fix predates
   the published `userland-v2-dev`; a fresh device still hits it until `pkg`
   self-heals).
2. **Part B — bootstrap correctness** (seed only the runtime closure, run
   seeded postinst, add `md5sums`).
3. **Part C — clean-device acceptance** ([`PHASE3_DEVICE_ACCEPTANCE.md`](PHASE3_DEVICE_ACCEPTANCE.md)
   — still NOT PASSED).
4. **Part D — M3: repository signing / key distribution.**

Phase 3 is **functionally working but not complete** — do not declare it done
until Parts A–D pass.

## Known current issue

The published Phase 2 `userland-v1` archive can contain an ELF Bash whose
dynamic library `libandroid-support.so` is absent. The app-side launch check
and fallback prevent this from killing the terminal and the installer now
re-reports the missing library; installing the rebuilt Phase 3 bootstrap (which
includes `libandroid-support.so`) is the durable fix. Do not claim Phase 2 or
Phase 3 clean-device acceptance until the corrected bootstrap is installed and
the Bash/BusyBox/runtime-library smoke tests pass.

### The official `termux-exec` recipe cannot build in CodeC CI (run 32501170464)

The pinned `termux-exec` recipe declares
`TERMUX_PKG_BUILD_DEPENDS="termux-core-static"`. `termux-core-static` does not
exist anywhere in the pinned `termux-packages` tree (checked `packages/`,
`root-packages/`, `disabled-packages/`) — it is a prebuilt package that only
exists on Termux's private build farm, and CodeC must not consume official
`com.termux` prebuilts (the build system also ignores the official dependency
repo because `TERMUX_APP_PACKAGE` is `com.codeci.ide`). The 2026-08-21
rebuild therefore failed in the "Build Phase 3 package-manager bootstrap"
step on both arches (the 14-minute package closure built fine).

**Fix (committed):** `build-termux-exec-preload.sh` /
`termux-exec-standalone.sh` build the `direct` LD_PRELOAD variant from the
pinned public sources — `termux-core-package` v0.4.0
(SHA-256 `af6299f3…`) for `libtermux-core_nos_c_tre.a` + headers, then
`termux-exec-package` v2.5.0 (SHA-256 `5c5eeb15…`) — with the same
standalone NDK toolchain and the CodeC identity constants the recipe build
would bake in. It runs best-effort inside the builder container after the
four bootstrap packages; the archive includes the library when the build
succeeds (the validator warns instead of failing when it is absent), and the
app exports `LD_PRELOAD` only when the library is present, so a bootstrap
without it still installs and runs.

### Workflow regex bug failed the bootstrap step after a full build (run 32509911413)

Run `32509911413` (2026-08-21, both arches) failed at the end of the
"Build Phase 3 package-manager bootstrap" step — *after* all four packages,
the full closure, the prefix assembly, the `bootstrap-phase3-*.tar.gz`
archive, and its SHA-256 had been produced. Root cause: the step's
verification greps used a double backslash in the YAML `|` block scalar:

```
grep -qE '^\\./?bin/(apt-get|dpkg)$' bootstrap-phase3-contents.txt
```

YAML block scalars do not process escapes, so the shell received
`'^\\./?bin/…'`; in ERE, `\\` matches a *literal backslash*, which no tar
listing line contains. The grep therefore always failed silently under
`set -e`, wasting the ~100-minute build. (The earlier green run
`32469769089` was on branch `arena/01a01fef-codec`, whose workflow did not
contain this step, so the bug was never exercised there.)

**Fix (committed):** single-backslash patterns, each check wrapped in an
explicit `if ! grep …` with an `ERROR:` message and a `head -25` dump of the
candidate entries, so any future miss is visible in the log. Verified
locally against a realistic listing (pass) and a truncated one
(detects the missing entry).

## Important release facts

- The app now targets the Phase 3 release tag `userland-v2-dev` with assets
  `bootstrap-phase3-aarch64.tar.gz` / `bootstrap-phase3-x86_64.tar.gz` (+
  `.sha256`), falling back to `userland-v1` / `bootstrap-aarch64.tar.gz`.
- Until that release is published, devices keep installing `userland-v1`
  (unchanged behaviour).
- The current published Pages tree is `/CodeC/dev`, not `/CodeC/packages/dev`.
- Keep all work on `arena/01a028e2-codec`.
- Do not add `.` to `PATH`, use official Termux repositories, overwrite `cc`,
  or replace real ELF Bash with a shim.
