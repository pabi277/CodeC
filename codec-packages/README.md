# codec-packages

GPL-3.0 overlay on [termux-packages](https://github.com/termux/termux-packages).

We **do not** vendor Termux `.deb` binaries. Every package is rebuilt with:

```
TERMUX_APP_PACKAGE=com.codeci.ide
TERMUX_PREFIX=/data/data/com.codeci.ide/files/usr
```

See [properties.codec.sh](properties.codec.sh) and
[docs/TERMINAL_PLAN.md](../docs/TERMINAL_PLAN.md) §2.

## Why an overlay, not a 1:1 git clone in this repo

`termux-packages` is large. CI clones a **pinned** commit (see
`TERMUX_PACKAGES_REF` in `properties.codec.sh`) and applies this overlay.

A standalone GitHub fork named `codec-packages` can be created later; until
then this directory **is** the fork surface.

## v1 package set (Phase 2 — no apt/dpkg)

`busybox` `bash` `coreutils` `grep` `sed` `gawk` `tar` `gzip` `nano` `less`
`make` `file` `termux-exec`

`apt` / `dpkg` / clang = Phase 3.

## Local / CI build

```
./scripts/build-bootstrap.sh aarch64
```

Needs Docker (`ghcr.io/termux/package-builder`) or a Termux build host.

Output:

- `dist/bootstrap-aarch64.tar.gz`
- `dist/bootstrap-aarch64.tar.gz.sha256`

Host the pair on GitHub Releases (`userland-v1`). Do not bake them into the APK.
