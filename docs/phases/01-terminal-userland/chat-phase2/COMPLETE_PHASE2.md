# Complete Phase 2 — final acceptance guide

The build/release work is complete as of 2026-08-20. This guide contains only
the remaining acceptance steps and reproducibility facts.

## Verified build and release

- Bootstrap workflow run `32376313030`: **success**
- APK workflow run `32378034011`: **success**
- Public release: <https://github.com/pabi277/CodeC/releases/tag/userland-v1>
- `bootstrap-aarch64.tar.gz`: 7,647,859 bytes
- SHA-256:
  `641c18d3a9daed41480f0d18e9fdbc807e393273ce492e600d60862e260d73f7`
- Archive root: `bin/`, `lib/`, `etc/`, `var/`, etc.
- Required files: `bin/bash`, `bin/busybox`
- Forbidden archive prefix: `data/data/`
- Forbidden runtime prefix: `/data/data/com.termux/files/usr`

The app downloads these exact URLs:

```text
https://github.com/pabi277/CodeC/releases/download/userland-v1/bootstrap-aarch64.tar.gz
https://github.com/pabi277/CodeC/releases/download/userland-v1/bootstrap-aarch64.tar.gz.sha256
```

## 1. Merge source changes

The post-PR fixes must be merged from `arena/01a01e7c-codec` into `main`. Do not
leave the successful workflow and archive assembly only on the old branch.

Open a PR, wait for Build APK checks, and merge it. Keep the release tag: it
already points to the successful source commit.

## 2. Clean phone installation

1. Download the `CodeC-IDE` artifact from successful APK run `32378034011` (or a
   later green run containing the same fixes).
2. Uninstall the old CodeC app so stale shims/userland cannot mask defects.
3. Install the APK and open CodeC → Term.
4. Tap Install userland if automatic installation does not begin.
5. Wait for download, SHA-256 verification, extraction, and `userland: ready`.
6. Tap refresh so the PTY restarts with real Bash.

## 3. Online smoke test

Run one command at a time:

```sh
uname -a
echo $PREFIX
which bash
echo $BASH_VERSION
busybox
ls
cc main.c -o a.out
./a.out
```

Pass criteria:

| Check | Required result |
|---|---|
| `$PREFIX` | `/data/data/com.codeci.ide/files/usr` or Android's equivalent `/data/user/0/com.codeci.ide/files/usr` |
| `which bash` | CodeC `$PREFIX/bin/bash` |
| `$BASH_VERSION` | a real Bash version |
| `busybox` | applet/help output |
| `cc` and `./a.out` | Phase 1 compile/run still works |

The minimal Phase 2 archive does not promise nano, coreutils, make, apt, or pkg.
Do not fail Phase 2 because an out-of-scope package is absent.

## 4. Offline smoke test

After one successful installation:

1. Close CodeC.
2. Enable airplane mode.
3. Reopen CodeC → Term.
4. Repeat `which bash`, `busybox`, `cc main.c -o a.out`, and `./a.out`.

No network access should be required after installation.

## Completion checklist

- [x] Workflow exists and dispatches
- [x] Official Termux recipes rebuild for the CodeC prefix
- [x] aarch64 workflow is green
- [x] SHA-256 matches
- [x] Archive layout is root-level and app-compatible
- [x] Public `userland-v1` release has both assets
- [x] Latest APK CI is green
- [ ] Post-PR fixes are merged to `main`
- [ ] Clean-device install reaches `userland: ready`
- [ ] Real Bash and BusyBox run in CodeC Term
- [ ] Phase 1 `cc` and execution still work
- [ ] Offline restart and compile/run pass

**Phase 2 is technically implemented and released, but final acceptance remains
pending until every unchecked item above is confirmed.**

## Do not do in Phase 2

- Do not install official Termux `.deb` files.
- Do not use `build-package.sh -I` with the custom prefix.
- Do not restore the direct-NDK BusyBox/Bash experiment.
- Do not add `.` to `PATH`.
- Do not bake the archive into the APK.
- Do not begin apt/dpkg/`pkg install`; that is Phase 3.
