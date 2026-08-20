# CodeC overlay for termux-packages/scripts/properties.sh
# GPL-3.0 — keep this file next to the cloned tree.

# Pinned upstream (bump deliberately).
TERMUX_PACKAGES_REPO="${TERMUX_PACKAGES_REPO:-https://github.com/termux/termux-packages.git}"
TERMUX_PACKAGES_REF="${TERMUX_PACKAGES_REF:-v0.1240}"

TERMUX_APP_PACKAGE="com.codeci.ide"
TERMUX_BASE_DIR="/data/data/${TERMUX_APP_PACKAGE}/files"
TERMUX_CACHE_DIR="/data/data/${TERMUX_APP_PACKAGE}/cache"
TERMUX_ANDROID_HOME="${TERMUX_BASE_DIR}/home"
TERMUX_APPS_DIR="${TERMUX_BASE_DIR}/apps"
TERMUX_PREFIX="${TERMUX_BASE_DIR}/usr"

# Phase 2 bootstrap package list (Termux recipe names).
CODEC_BOOTSTRAP_PACKAGES="
busybox
bash
coreutils
grep
sed
gawk
tar
gzip
nano
less
make
file
termux-exec
"
