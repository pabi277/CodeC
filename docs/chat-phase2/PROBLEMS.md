# Phase 2 problems encountered

This is the failure record for the CodeC aarch64 userland bootstrap completed on
2026-08-20. See [SOLUTIONS.md](SOLUTIONS.md) for the durable fixes.

## Workflow and GitHub

1. **Workflow was not dispatchable.** Two lines under `run: |` lost YAML
   indentation, so GitHub could not parse `workflow_dispatch` and produced failed
   runs with no jobs.
2. **Arena could not push workflow changes.** The GitHub App lacked the
   `workflows` permission, so workflow edits had to be pushed from Termux.
3. **Release-triggered APK run was red although the APK built.** Only the
   release-attachment step failed with `Resource not accessible by integration`.

## Abandoned direct-NDK approach

4. BusyBox `olddefconfig` does not exist in BusyBox 1.36.1.
5. `yes "" | make oldconfig` caused an expected SIGPIPE under `pipefail`.
6. Linux BusyBox `defconfig` enabled Android-incompatible applets:
   - `loadfont`: missing `sys/kd.h`
   - `hostid`: missing `gethostid()`
   - `logname`: missing `getlogin_r()`
   - username completion: missing `setpwent()` / `getpwent()`
7. A hand-written NDK Bash build ignored Termux's Android patches and native
   dependencies.
8. `BASH_VERSION` was used as a workflow variable. Bash reserves that variable,
   replacing `5.2` with the runner value such as `5.2.21(1)-release`; source URLs
   and Git tags therefore became invalid.
9. GNU FTP and an attempted mirror/archive URL returned download failures.

The direct-NDK workflow was discarded. Keeping it would have recreated years of
Termux compatibility work one error at a time.

## Official Termux builder integration

10. A repository-wide validation scan falsely treated the literal old prefix in
    `apply-prefix.sh` as contamination. That literal is required as the search
    side of the replacement.
11. Using `build-package.sh -I` was unsafe for a custom prefix because it may
    install official packages built for `com.termux`.
12. Upstream Bash depends on `termux-tools`, which pulled `termux-am`. Its Gradle
    build tried to install Android SDK Platform 33 and Build-Tools 30.0.3 into a
    non-writable SDK directory inside the package-builder image.
13. The first successful tarball had the wrong archive root:
    `data/data/com.codeci.ide/files/usr/...`. Extracting that into `$PREFIX`
    would have nested the complete Android path under `$PREFIX`.
14. `tar -t | grep -q` is unsafe with this script's `pipefail`; an early grep exit
    can give tar SIGPIPE and falsely fail validation.
15. The Actions artifact preserved repository paths, so downloaded release files
    were under `codec-packages/dist/`, not the artifact directory root.
16. An empty expected hash and empty actual hash were once compared as equal;
    existence and non-empty checks are required before checksum comparison.

## Non-problems seen in logs

The following were configure probes or optional-feature warnings, not the final
cause of failure: missing optional `libacl`, `libcap`, `wolfssl`, `explicit_bzero`,
`memset_s`, and similar checks. Always inspect the final failing task and its
surrounding log rather than treating every `not found` line as fatal.
