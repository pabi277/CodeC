# Phase 2 handoff

Read [README.md](README.md), [PROBLEMS.md](PROBLEMS.md), and
[SOLUTIONS.md](SOLUTIONS.md) before changing terminal or userland code.

## Completed infrastructure

- Official Termux recipes rebuild the aarch64 bootstrap for `com.codeci.ide`.
- Successful bootstrap run: `32376313030`.
- Public release: `userland-v1` with archive and checksum.
- Verified SHA-256:
  `641c18d3a9daed41480f0d18e9fdbc807e393273ce492e600d60862e260d73f7`.
- Archive has root-level `bin/bash` and `bin/busybox`; no nested `data/data`.
- Latest APK build passed in run `32378034011`.

## Remaining acceptance work

1. Merge the post-PR Phase 2 commits from `arena/01a01e7c-codec` into `main`.
2. On a clean Android installation, install userland and refresh Term.
3. Verify `$PREFIX`, real Bash, BusyBox, `cc`, and `./a.out`.
4. Reopen in airplane mode and repeat the essential checks.

Do not declare final acceptance until those two remaining items pass.

## Preserve these invariants

- Keep TCC link order (`-o` last, `codec_stdio.o`, archives twice).
- Keep `cc` mode 755; never `exec tcc`; do not introduce `\\` into its script.
- Never overwrite ELF `$PREFIX/bin/bash` with a shim.
- Never use `build-package.sh -I` for the custom CodeC prefix.
- Never copy official `com.termux` binaries or `.deb` files.
- Archive only `$PREFIX` contents: root-level `bin/`, `lib/`, `etc/`, etc.
- Never bake the userland tarball into the APK.
- Keep projects in executable `filesDir/CodeC/projects`.

## Phase boundary

Do not start apt, dpkg, `pkg install`, or a package repository unless Phase 3 is
explicitly requested. Do not add `.` to `PATH`, change the Phase 1 RUN stdin
behaviour, or treat exit 124 as part of this phase.
