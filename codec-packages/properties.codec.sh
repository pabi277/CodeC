# CodeC overlay for termux-packages/scripts/properties.sh
# GPL-3.0 — keep this file next to the cloned tree.

# Pinned upstream (bump deliberately after reviewing recipe/patch changes).
TERMUX_PACKAGES_REPO="${TERMUX_PACKAGES_REPO:-https://github.com/termux/termux-packages.git}"
# Official master revision inspected for Phase 3 on 2026-08-20.
TERMUX_PACKAGES_REF="1bbe66903526df2e8af51e704316bc68ede72603"

TERMUX_APP_PACKAGE="com.codeci.ide"
TERMUX_BASE_DIR="/data/data/${TERMUX_APP_PACKAGE}/files"
TERMUX_CACHE_DIR="/data/data/${TERMUX_APP_PACKAGE}/cache"
TERMUX_ANDROID_HOME="${TERMUX_BASE_DIR}/home"
TERMUX_APPS_DIR="${TERMUX_BASE_DIR}/apps"
TERMUX_PREFIX="${TERMUX_BASE_DIR}/usr"

# Phase 2 bootstrap package list (Termux recipe names). Do not change the
# published userland-v1 asset without a new clean-device acceptance run.
CODEC_BOOTSTRAP_PACKAGES="
busybox
bash
"

# Phase 3 package-manager bootstrap roots. These are built from source for the
# CodeC prefix before a new bootstrap is published; they are not official
# prebuilt packages and are intentionally separate from userland-v1.
#
# termux-exec is intentionally NOT in this list: the official recipe declares
# TERMUX_PKG_BUILD_DEPENDS="termux-core-static", a prebuilt package that only
# exists on Termux's private build farm, so the recipe can never build in
# CodeC CI (and official com.termux .debs are forbidden). Instead
# build-termux-exec-preload.sh builds the LD_PRELOAD library from the pinned
# public sources (termux-core-package v0.4.0, termux-exec-package v2.5.0)
# with the same toolchain and merges it into the bootstrap archive
# best-effort. The library (lib/libtermux-exec-ld-preload.so, exported as
# LD_PRELOAD by the CodeC shell profile) is what lets dpkg execute shebang
# maintainer scripts under the CodeC prefix on real devices.
#
# libcurl is a build root because the `curl` CLI is its subpackage
# (packages/libcurl/curl.subpackage.sh at the pinned revision): building
# libcurl produces both the libcurl and the curl .debs. Nothing else in the
# manager closure pulls it (apt uses GnuTLS, not OpenSSL), so it must be
# built explicitly. Fresh-device Part B evidence (2026-08-23): the Phase 3
# closure ships none of curl/python3/wget, so `pkg update` died at
# "offline or unable to download CodeC Release metadata (HTTPS required)".
CODEC_PACKAGE_MANAGER_BOOTSTRAP_PACKAGES="
busybox
bash
apt
dpkg
libcurl
"

# Phase 3 bootstrap SEED set (Part B): only the transitive Depends closure
# of these packages is extracted into the bootstrap and recorded in the
# dpkg status DB. The first Phase 3 bootstrap extracted/seeded every built
# .deb — including build tools (doxygen, swig, tcl, tor, …) — bloating the
# archive (~174 MB) and polluting `dpkg -l`; see docs/NEXT_STEPS.md Part B.
#
# coreutils and less join the four manager roots because the terminal UX
# expects their alternatives to exist on a fresh device: `pager` must be
# the real GNU less (not only busybox's pager provider), and coreutils
# arrives via apt's dependency chain regardless. nano is NOT seeded — its
# `editor` alternative is wired by the real `pkg install nano` postinst.
#
# curl seeds the HTTPS metadata fetcher the `pkg` frontend needs to fetch
# and SHA-256-verify the repository Release/Release.sha256 before apt runs
# (ShellEnvironment.pkgScript prefers $PREFIX/bin/curl). The curl CLI is
# source-built from the pinned libcurl recipe; its CA bundle
# ($PREFIX/etc/tls/cert.pem from ca-certificates) is already in the
# closure via apt -> libgnutls -> ca-certificates. Python is deliberately
# NOT seeded: it is a far larger closure, and pkg only ever used it as a
# downloader of last resort (fresh-device evidence 2026-08-23: the
# published closure ships no python3 either, and pkg no longer depends on
# it for maintainer-script checks).
CODEC_BOOTSTRAP_SEED_PACKAGES="
busybox
bash
apt
dpkg
coreutils
less
curl
"

# Curated Phase 3 repository roots. The generated repository also contains the
# exact source-built dependency closure; only the generated manifest is a
# promise to users.
CODEC_REPOSITORY_PACKAGES="
nano
less
coreutils
grep
sed
gawk
gzip
tar
make
libmagic
"

# Development channel URL. CI/release automation may override this; the app
# never falls back to an official Termux repository.
CODEC_PACKAGE_REPOSITORY_URL="${CODEC_PACKAGE_REPOSITORY_URL:-https://pabi277.github.io/CodeC/dev}"
CODEC_PACKAGE_REPOSITORY_SUITE="stable"
CODEC_PACKAGE_REPOSITORY_COMPONENT="main"
