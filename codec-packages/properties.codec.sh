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

# Curated repository roots. The generated repository also contains the exact
# source-built dependency closure; only the generated manifest is a promise
# to users.
#
# Round 1 (Phase 3): nano less coreutils grep sed gawk gzip tar make libmagic.
#
# Round 2 (Phase 4 Part 4.5, 2026-08-24): the most-requested dev-environment
# tools for a C IDE, chosen so the incremental closure stays within the CI
# build budget. Decisions (package list, repository-only scope, recipe
# overrides, deferred items) are recorded in
# docs/chat-phase4/PART_4_5_CATALOG_EXPANSION.md. New packages are
# repository-only: CODEC_PACKAGE_MANAGER_BOOTSTRAP_PACKAGES and
# CODEC_BOOTSTRAP_SEED_PACKAGES are unchanged, so the bootstrap archive stays
# byte-identical to the published userland-v2-dev assets (verified in CI by
# digest comparison).
#
# Deferred deliberately (round 3 candidates, with reasons):
#   vim      — build dependencies luajit/python/ruby/tcl add 20+ min per arch
#   openssh  — krb5/ldns/termux-auth are heavy; termux-auth needs its own review
#
# Round 3 (Phase 12, 2026-08-30): python + python-pip — the ONE planned CI
# package build (~1–2 h). The python recipe closure (gdbm, openssl, readline,
# ncurses-ui-libs, libsqlite, zstd, …) is built from source for the CodeC
# prefix. python-ensurepip-wheels is a subpackage of python and ships with it;
# python-pip is its own recipe and is added explicitly so `pkg install -y
# python` gives a pip-capable interpreter. Repository-only: the bootstrap seed
# and package-manager roots are unchanged, so the published bootstrap archives
# stay byte-identical (python is installed on demand, like every round-2/3
# package). apply-recipe-overrides.sh excludes the python-tkinter subpackage
# and drops tk from python's build-depends (tk would pull the whole X11
# closure; CodeC has no X11 use for Tkinter — same rationale as the git
# round-2 override).
#
# Round 4 (Phase 20.1, 2026-09-01): language toolchains for the Phase 21
# LanguageRunProfile registry. Research was done against the pinned
# TERMUX_PACKAGES_REF before adding anything — full record in
# docs/chat-phase20/PART_20_1_TOOLCHAINS.md §7:
#   libllvm  — root recipe for Clang 21 (there is no packages/gcc or
#              packages/clang at the pinned revision: upstream removed the
#              old gcc shim recipe, and clang is a subpackage of libllvm).
#              The clang subpackage ships bin/clang/clang++ AND the driver
#              symlinks bin/gcc, bin/g++, bin/c++, bin/cpp — so
#              `pkg install clang` gives users the `gcc foo.c -o foo` UX.
#              bin/cc is deliberately stripped from the subpackage by
#              apply-recipe-overrides.sh: $PREFIX/bin/cc is the app's own
#              TCC frontend until Phase 21.4 (invariant: never overwrite cc).
#   nodejs   — Node.js 26 runtime (deps: libc++, openssl, c-ares, libicu,
#              libsqlite, zlib, libffi — no X11).
#   npm      — split out of nodejs upstream at 25.3.0-1; added explicitly so
#              `pkg install nodejs npm` gives a package-manager-capable node.
#   php      — PHP 8.5 CLI, trimmed by apply-recipe-overrides.sh: no
#              apache/ldap/pgsql/gd closures (the upstream recipe would
#              otherwise build postgresql, openldap, apache2 and libgd just
#              for extensions a phone IDE never uses). php-fpm/php-sodium
#              subpackages are kept.
#   ruby     — Ruby 3.4 (clean closure: libffi, libgmp, libyaml, openssl…).
#   lua54    — Lua 5.4; the upstream lua54.alternatives postinst is NOT one
#              of the five reviewed allowlisted alternatives packages, so
#              apply-recipe-overrides.sh removes the .alternatives file and
#              ships plain relative bin/lua/bin/luac symlinks instead.
# Repository-only: CODEC_PACKAGE_MANAGER_BOOTSTRAP_PACKAGES and
# CODEC_BOOTSTRAP_SEED_PACKAGES are unchanged, so the published bootstrap
# archives stay byte-identical (CI verifies the digest).
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
git
wget
bat
ripgrep
fd
htop
tmux
tree
patch
diffutils
zstd
m4
autoconf
automake
libtool
python
python-pip
libllvm
nodejs
npm
php
ruby
lua54
"

# Build-job split (Phase 20.1, after the 360-minute job ceiling killed
# dispatches 33506104710 and 33547475854): one 6h-capped GitHub job cannot
# compile all roots any more, so the workflow matrix fans out into one leg
# per GROUP (arch x group). These three groups are the single source of
# truth — build-package-repository.sh resolves the matrix group name through
# them, and codec-packages/tests/test_ci_guardrails.py guards that their
# union is exactly CODEC_REPOSITORY_PACKAGES with empty pairwise overlap;
# the workflow's matrix lists the group NAMES only.
#
#   base  — the round 1–3 catalog (fits easily: round 3 measured ~2h) and
#           the only leg that also builds/validates/uploads the Phase 3
#           bootstrap archive.
#   llvm  — libllvm alone: the long pole even after the D10 backend trim.
#   langs — the round-4 language runtimes sharing one closure (libicu is
#           built once for nodejs+php here; npm is arch-independent).
CODEC_REPOSITORY_GROUP_BASE="
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
git
wget
bat
ripgrep
fd
htop
tmux
tree
patch
diffutils
zstd
m4
autoconf
automake
libtool
python
python-pip
"

CODEC_REPOSITORY_GROUP_LLVM="
libllvm
"

CODEC_REPOSITORY_GROUP_LANGS="
nodejs
npm
php
ruby
lua54
"

# Development channel URL. CI/release automation may override this; the app
# never falls back to an official Termux repository.
CODEC_PACKAGE_REPOSITORY_URL="${CODEC_PACKAGE_REPOSITORY_URL:-https://pabi277.github.io/CodeC/dev}"
CODEC_PACKAGE_REPOSITORY_SUITE="stable"
CODEC_PACKAGE_REPOSITORY_COMPONENT="main"
