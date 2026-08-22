# Phase 3 — debugging `pkg` on device

Hands-on triage for the CodeC `pkg` frontend. Run these in the **CodeC Term**
tab; the "cross-check" section uses **Termux**.

## Verified state (real aarch64 device, 2026-08-22)

The Phase 3 bootstrap installs and boots correctly:

- `userland: already installed (userland-v2-dev)`
- real Bash `5.3.15(1)-release`, BusyBox `1.38.0`
- `apt-get`, `dpkg`, `python3` all present under `$PREFIX/bin`
- `pkg update` **works** (fetches `Release` + `Release.sha256` via `python3
  urllib`, checksum matches, `apt-get update` succeeds)

`pkg install` fails for two reasons that both trace to the **published
bootstrap being stale**, not to the `pkg` script or the repository.

## Root cause 1 — stale bootstrap still records `dpkg-perl: Depends: clang`

```
E: Unmet dependencies.
 dpkg-perl : Depends: clang but it is not installable
 nano : Depends: libmagic but it is not going to be installed
```

The official `dpkg` recipe's `dpkg-perl` subpackage listed `clang` as a
*runtime* dependency. CodeC dropped that bogus dependency in
`apply-recipe-overrides.sh` (commit `41c8df6`), but the published
`userland-v2-dev` bootstrap was built **before** that fix (run `32546404876`),
so its seeded `var/lib/dpkg/status` still records `Depends: clang`. `clang` is
a build tool and is intentionally absent from the runtime repository, so apt
treats `dpkg-perl` as broken and refuses every install.

`libmagic` **is** published and installable (verified in the aarch64
`Packages` index); the `nano : Depends: libmagic` line is collateral from the
broken resolver, not a real missing dependency.

Fix: rebuild + republish the bootstrap from the current branch (see below).
No further code change is needed for this one.

## Root cause 2 — termux-exec LD_PRELOAD library missing

```
ls: cannot access '.../usr/lib/libtermux-exec-ld-preload.so': No such file or directory
echo "$LD_PRELOAD"   # -> empty
```

The bootstrap build script builds the termux-exec `LD_PRELOAD` library
*best-effort* (`build-termux-exec-preload.sh`); when that standalone build
fails it publishes the bootstrap without the library and only prints a
warning. `validate-bootstrap.py` also treats the library as warning-only, so
the release went out without it. The release body still claims it is included
— it is not.

Without termux-exec, `dpkg` cannot execute shebang maintainer scripts under
`/data/data/.../usr`, so the reviewed `coreutils`/`less`/`nano` `postinst`/
`prerm` alternatives scripts will fail once dependencies resolve.

Fix: get the standalone termux-exec build to succeed (needs the CI log of run
`32546404876` to see why it failed), then rebuild.

## Secondary issues (non-blocking, but clean these up)

1. **dpkg status polluted with build-dependencies.** `assemble-bootstrap.sh`
   seeds **every** built `.deb` (build deps included — `doxygen`, `swig`,
   `tcl`, `fontconfig`, …) as "installed" instead of only the runtime closure
   of `busybox bash apt dpkg`. This is what put `dpkg-perl`'s clang dependency
   into the status DB in the first place and risks more unsatisfiable
   build-only packages. Correct fix: seed only the transitive `Depends`
   closure from the roots.
2. **Missing `md5sums`** → `dpkg --audit` reports every seeded package as
   "missing the md5sums control file" (cosmetic, but noisy).
3. **Stale `transaction.pending`.** A clean `apt` failure returned before
   removing the marker. Fixed in `ShellEnvironment.pkgScript()` (remove the
   marker before returning on a clean failure).
4. **`apt.conf.d` / `preferences.d` warnings.** The apt recipe removes these
   dirs and the bootstrap does not recreate them. Fixed in `require_backend`
   (create them).

## Tier 0 — is the Phase 3 bootstrap installed?

```sh
echo "$PREFIX"
which apt-get dpkg bash busybox python3 curl wget 2>&1
dpkg --print-architecture          # aarch64 | x86_64
dpkg -l | grep -E 'apt|dpkg|bash|busybox'
```

## Tier 1 — runtime health (termux-exec check is the important one)

```sh
echo "$BASH_VERSION"                 # real version (empty = shim)
busybox                              # applet help
echo "$LD_PRELOAD"                   # MUST show libtermux-exec-ld-preload.so
ls -l "$PREFIX/lib/libtermux-exec-ld-preload.so" "$PREFIX/lib/libandroid-support.so"
```

## Tier 2 — reproduce the dependency failure

```sh
sh -x "$PREFIX/bin/pkg" install nano 2>&1 | tee "$PREFIX/tmp/pkg-install.log"
# look for: dpkg-perl : Depends: clang ...
apt-cache policy dpkg-perl clang libmagic
grep -A5 '^Package: dpkg-perl' "$PREFIX/var/lib/dpkg/status" | grep -i depends
```

`grep ... depends` should show `Depends: clang` on the stale bootstrap (and
`Depends: perl, make` after the rebuild).

## Tier 3 — apt/dpkg directly (bypass the wrapper)

```sh
apt-get update
apt-cache search nano
dpkg --audit                        # lists seeded pkgs missing md5sums
```

## Cross-check the repository from Termux

```sh
curl -fsSL https://pabi277.github.io/CodeC/dev/dists/stable/Release -o Release
curl -fsSL https://pabi277.github.io/CodeC/dev/dists/stable/Release.sha256 -o Release.sha256
cat Release.sha256 && sha256sum Release    # tokens must match
```

## Rebuild + republish the bootstrap (the actual unblock)

The `clang` fix is already in `main`-candidate code; the published bootstrap
just predates it. From Termux (with `gh` authenticated):

```sh
# 1) Rebuild packages + Phase 3 bootstrap (includes the dpkg-perl clang fix)
gh workflow run "CodeC package repository" --ref arena/01a028e2-codec

# 2) Watch for the run ID, then republish the dev release from that run
gh run watch <RUN_ID>
gh workflow run "Publish CodeC bootstrap release" \
  --ref arena/01a028e2-codec \
  -f source_run_id=<RUN_ID> -f release_tag=userland-v2-dev
```

Then in CodeC: **Settings → Install userland** (force reinstall) so the new
bootstrap replaces the stale one, and re-run the acceptance checklist in
`docs/PHASE3_DEVICE_ACCEPTANCE.md`.
