# Phase 2 — bootstrap userland

App **1.3.14** (`versionCode` 18). Phase 1 offline `cc` and `./a.out` behaviour
must remain unchanged.

## Current status (2026-08-20)

| Item | Status |
|---|---|
| Download, SHA-256 verification, extraction | ✅ implemented |
| Prefer real ELF Bash without overwriting it | ✅ implemented |
| Official Termux-recipe aarch64 build | ✅ run `32376313030` |
| Correct root-level archive layout | ✅ verified |
| Public `userland-v1` release and checksum | ✅ published |
| Latest APK CI | ✅ run `32378034011` |
| Merge post-PR fixes into `main` | ⏳ required |
| Clean-device and airplane-mode smoke test | ⏳ required on an Android phone |

**Conclusion:** the Phase 2 implementation and release pipeline are complete,
but Phase 2 acceptance is not fully closed until the final branch is merged and
the phone smoke test passes.

## Final architecture

| Piece | Location |
|---|---|
| Custom Termux overlay | `codec-packages/` |
| Prefix | `/data/data/com.codeci.ide/files/usr` |
| Build workflow | `.github/workflows/bootstrap-userland.yml` |
| Official recipe build | `codec-packages/scripts/build-bootstrap.sh` |
| Prefix-only archive assembly | `codec-packages/scripts/assemble-bootstrap.sh` |
| App installer | `UserlandInstaller`, `TarGzExtractor` |
| Shell selection | `ShellEnvironment.resolveShell` |
| Public assets | GitHub release `userland-v1` |

See [PROBLEMS.md](PROBLEMS.md) for the failure history and
[SOLUTIONS.md](SOLUTIONS.md) for durable fixes and design decisions.

## Published aarch64 userland

- Release: <https://github.com/pabi277/CodeC/releases/tag/userland-v1>
- Archive: `bootstrap-aarch64.tar.gz`
- Checksum: `bootstrap-aarch64.tar.gz.sha256`
- SHA-256: `641c18d3a9daed41480f0d18e9fdbc807e393273ce492e600d60862e260d73f7`

The archive root contains `bin/bash`, `bin/busybox`, libraries and configuration
files. It must never contain a leading `data/data/...` tree because the app
extracts it directly into `$PREFIX`.

## Final phone test

After a clean APK install, open Term, install userland, wait for `userland:
ready`, and refresh. Run each line separately:

```sh
echo $PREFIX
which bash
echo $BASH_VERSION
busybox
cc main.c -o a.out
./a.out
```

Then close the app, enable airplane mode, reopen Term, and repeat `which bash`,
`cc`, and `./a.out`.

## Scope boundary

`apt`, `dpkg`, `pkg install`, and a CodeC package repository are **Phase 3**.
Do not install official Termux binaries, add `.` to `PATH`, or bundle the
bootstrap in the APK.
