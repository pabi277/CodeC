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
CODEC_PACKAGE_MANAGER_BOOTSTRAP_PACKAGES="
busybox
bash
apt
dpkg
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
CODEC_PACKAGE_REPOSITORY_URL="${CODEC_PACKAGE_REPOSITORY_URL:-https://pabi277.github.io/CodeC/packages/dev}"
CODEC_PACKAGE_REPOSITORY_SUITE="stable"
CODEC_PACKAGE_REPOSITORY_COMPONENT="main"
