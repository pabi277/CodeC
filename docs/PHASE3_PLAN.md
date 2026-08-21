# CodeC IDE Phase 3 package-management plan

**Status:** design accepted for the first implementation milestone
**Date:** 2026-08-20
**Scope:** CodeC's private Android userland only

This plan follows the Phase 2 handoff and is intentionally narrower than a full
Termux distribution. It does not change the existing `userland-v1` release until
a Phase 3 bootstrap has been built, device-tested, and published.

## 1. Non-negotiable invariants

- Android application ID remains `com.codeci.ide`.
- The runtime prefix remains `/data/data/com.codeci.ide/files/usr` (the
  `/data/user/0/com.codeci.ide/files/usr` Android alias is equivalent).
- Packages are rebuilt from official Termux recipes and Android patches through
  `codec-packages`; official `com.termux` `.deb` files are never installed.
- `build-package.sh -I` is never used for a CodeC build. Every dependency needed
  by a CodeC package is built for the CodeC prefix.
- Package state, apt lists/cache, dpkg state, locks, and temporary installation
  data remain below the private prefix.
- The Phase 1 `cc` launcher, TCC assets, executable projects directory, and real
  Phase 2 ELF Bash are preserved.
- No package archive is bundled in the APK. Bootstrap and repository assets are
  hosted separately and downloaded only after integrity checks.
- No `.` is added to `PATH`.

## 2. Termux research findings

The current official `termux-packages` project still separates package recipes,
Android/NDK patches, the package builder, bootstrap generation, and repository
publication. The current maintainer documentation describes `build-package.sh`
with dependency resolution from source; `-I` is only a convenience for downloading
prebuilt dependencies and is unsuitable for CodeC's custom prefix.

The official apt repository shape is the conventional:

```text
dists/<suite>/<component>/binary-<architecture>/Packages
                                                     Packages.gz
                                                     *.deb
                                    Release
```

The `termux-apt-repo` project generates this layout and can optionally sign the
repository. Termux's bootstrap generator consumes the published apt metadata and
records package state under the prefix. Current upstream documentation also
supports a package-manager choice in newer bootstrap tooling, but CodeC Phase 3
selects the established Debian-compatible format because the requested commands,
dependency metadata, and rollback/state model map directly to apt/dpkg.

References consulted on 2026-08-20:

- <https://github.com/termux/termux-packages>
- <https://github.com/termux/termux-packages/wiki/Building-packages>
- <https://github.com/termux/termux-packages/wiki/For-maintainers>
- <https://github.com/termux/termux-packages/wiki/Package-Management>
- <https://github.com/termux/termux-apt-repo>
- `scripts/build-bootstraps.sh` and `scripts/generate-bootstraps.sh` in the
  official `termux-packages` repository

Important upstream behavior for CodeC:

1. Package payloads contain the prefix expected by the recipe. They must be
   rebuilt after the CodeC prefix overlay is applied.
2. A bootstrap is not the repository. It is a seed userland; apt/dpkg metadata
   and a package repository are needed for later installation.
3. APT metadata includes package dependency and SHA-256 information; the
   repository's `Release` metadata covers the package indexes. CodeC will add an
   explicit repository manifest and checksum sidecars for CI/release validation.
4. Repository signatures are the production goal. The first development channel
   is HTTPS plus complete SHA-256 verification and is not promoted as a trusted
   release until signing-key distribution and verification have been tested on a
   clean device.

## 3. Architecture decision

### Selected: apt/dpkg-compatible `.deb` repository with a CodeC `pkg` frontend

The user-facing command is a CodeC-owned `pkg` script. It is deliberately small:

- `pkg update` invokes the CodeC-configured apt backend only;
- `pkg search` queries the CodeC package indexes;
- `pkg install` resolves dependencies, downloads to apt's cache, validates every
  candidate, then installs atomically through dpkg;
- `pkg upgrade` upgrades only CodeC repository packages;
- `pkg uninstall` removes packages through dpkg while preserving required base
  packages.

The `pkg` wrapper is not allowed to fall back to an ambient apt configuration,
Termux paths, or an official Termux mirror. It supplies the CodeC sources list and
keeps the apt/dpkg state below `$PREFIX`.

The first package-manager bootstrap must contain CodeC-built `apt` and `dpkg` and
all of their CodeC-built dependencies. Until that bootstrap is released and
installed, the existing Phase 2 userland remains valid and the placeholder command
must fail clearly with `package manager is not present in this userland` rather than
silently using another repository.

### Why not a CodeC-specific format/backend?

A custom manager would avoid shipping apt/dpkg, but it would also require a new
solver, version comparison rules, package database, upgrade semantics, and
compatibility tooling. It would make official Termux recipes harder to consume and
would not provide a tested path for the dependency-rich starter set. A custom
manager remains a possible future optimization after package behavior is proven;
it is not the Phase 3 foundation.

### Why not use the official Termux repository?

The repository is not interchangeable with CodeC packages. Official packages may
contain `/data/data/com.termux/files/usr` paths and are built for a different app
identity/prefix. Mixing them would violate the Phase 2 contract and can corrupt the
userland. CodeC will publish a separate repository and build all package closure
from source with the overlay.

## 4. Repository and package policy

The development repository is published separately from the APK and bootstrap.
The generated tree is suitable for GitHub Pages or another static HTTPS host:

```text
packages/<channel>/
  dists/stable/Release
  dists/stable/Release.sha256
  dists/stable/main/binary-aarch64/Packages
  dists/stable/main/binary-aarch64/Packages.gz
  dists/stable/main/binary-x86_64/Packages
  dists/stable/main/binary-x86_64/Packages.gz
  dists/stable/main/binary-all/Packages
  repository.json
  repository.json.sha256
```

The package builder will:

1. clone a pinned official `termux-packages` revision;
2. apply the CodeC package/prefix overlay;
3. build each requested root package and its full dependency closure without `-I`;
4. reject `com.termux` contamination and wrong prefix paths;
5. generate deterministic package indexes and a machine-readable manifest;
6. validate package architecture, payload paths, dependencies, checksums, and
   metadata before publishing.

The first repository channel targets these requested roots:

```text
nano less coreutils grep sed gawk gzip tar make libmagic
```

Only packages present in the generated manifest and passing CI are promised. The
actual published set includes the exact dependency closure and is reported by CI;
no package name is advertised merely because an upstream recipe exists.

### Maintainer-script policy

The first installable channel rejects maintainer scripts by default. The only
exceptions are the pinned official `coreutils`, `less`, and `nano` packages'
generated `postinst` and `prerm` files from their `.alternatives` definitions.
This exception is an explicit security design:

- only `coreutils`, `less`, and `nano` may contain scripts;
- only `postinst` and `prerm` are accepted;
- the shebang must point to the CodeC prefix;
- the generated alternative boilerplate must be present;
- package-specific expected alternative names, paths, priorities, and slaves are
  checked;
- only the CodeC `update-alternatives` command, its `--install`, `--slave`, and
  `--remove` arguments are allowed;
- command substitution, shell chaining, external commands, Termux paths,
  absolute paths outside the prefix, and traversal are rejected;
- the same allowlist is enforced by the host repository validator and the Android
  client preflight before apt/dpkg is allowed to install the package.

Every other package containing `preinst`, `postinst`, `prerm`, or `postrm` is
rejected. A later milestone may add other reviewed, signed package-specific
policies; it must not silently widen this one.

### Payload policy

Before installation, every package is checked for:

- `Architecture: all` or the device's exact dpkg architecture;
- data members under the CodeC prefix only;
- no absolute paths, `..` components, or symlink targets escaping the prefix;
- package name/version/dependency metadata that parses successfully;
- SHA-256 matching the repository index and downloaded file size.

Downloads use temporary `.partial` files and are promoted only after size and
checksum verification. Installation has a private lock, a preflight phase, and a
recoverable state marker. Failed preflight never invokes dpkg. Failed installation
is reported as incomplete and leaves the previous package database untouched where
the dpkg operation supports it; the next invocation repairs/configures the pending
state before continuing.

## 5. Implementation milestones

### M1 — repository foundation (this change)

- document this architecture and risks;
- add deterministic repository metadata generation and validation;
- add CI that builds the curated roots for each supported architecture without
  downloading prebuilt dependencies;
- replace the Phase 1 `pkg` placeholder with a guarded CodeC-only frontend that
  exposes useful errors and becomes functional when the Phase 3 bootstrap is
  installed;
- add unit/host tests for metadata, prefix/ABI validation, path traversal,
  maintainer-script rejection, checksum failures, and recoverable downloads;
- do not change the `userland-v1` asset or claim Android package installation has
  passed before a Phase 3 bootstrap is released and tested.

### M2 — package-manager bootstrap

- build and inspect CodeC-prefixed `apt` and `dpkg` plus dependencies from official
  recipes;
- assemble a new bootstrap without overwriting the real Bash or `cc` launcher;
- publish a development bootstrap and repository pair;
- clean-device test install, update/search/install/remove, compiler smoke tests,
  and airplane-mode restart.

### M3 — signed development channel and promotion

- publish a repository signature and distribute the verification key in a
  CodeC-owned, versioned trust file;
- verify Release/index/package signatures or checksums before apt is invoked;
- promote only after clean-device tests on arm64 and x86_64 where supported;
- document rollback to the previous repository manifest and bootstrap.

## 6. Risks and unresolved decisions

| Risk/decision | Current treatment | Exit condition |
|---|---|---|
| apt/dpkg may pull Android-specific dependencies | Build full closure from source; inspect output; isolate Docker container per checkout so Phase 2 and Phase 3 mounts cannot mix | M2 clean-prefix install and shell test |
| apt/dpkg may be too large for the bootstrap | Keep package manager in the externally hosted bootstrap, not APK; measure CI artifact | M2 size report and device disk-space test |
| official recipes may add maintainer scripts | Reject in M1; review candidates | Reviewed script design in M3 |
| HTTPS-only development integrity is weaker than signatures | SHA-256 checksums plus HTTPS and explicit dev label | Signed Release verification on device |
| package payloads may contain baked `com.termux` paths | scan control/data files and ELF strings in CI | zero contamination in every artifact |
| Android ABI names differ from Debian names | use Termux architectures (`aarch64`, `x86_64`, `all`) from dpkg metadata | `dpkg --print-architecture` matches on device |
| partial dpkg transactions | private lock, preflight, status marker, repair command; never delete the old prefix | interrupted install recovery test |
| disk full during download/extract | `StatFs`/available-space preflight and actionable error | clean-device low-space test |
| offline startup after install | no network in shell/bootstrap startup; cached package state is local | airplane-mode device test |
| repository hosting is not yet provisioned | default URL is configurable and CI emits the complete static tree | owner publishes dev channel before M2 |
| Phase 2 acceptance is still pending | preserve `userland-v1`; do not retag or replace it in M1 | clean phone acceptance checklist passes |

## 7. Release and rollback

A release is promoted only when CI has uploaded the repository tree, manifest,
checksums, package test report, and (for M2+) the matching bootstrap checksum. The
host must serve files over HTTPS with stable paths. The publishing workflow can
promote a previously successful immutable build artifact by run ID, avoiding a
second expensive source rebuild during publication.

Rollback means first stopping publication of the bad manifest, restoring the prior
manifest and package indexes atomically, and keeping the previous bootstrap asset
available. The client must never delete the installed prefix merely because an
update is offline or a repository is temporarily unavailable. Package removal is
explicit and uses the package database, not a filesystem-wide delete.
