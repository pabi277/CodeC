# CodeC Phase 4 Documentation

Phase 4 covers polish, expansion, and UX improvements on top of the Phase 3 package manager foundation.

## Parts & Status

- **[Part 4.1 — Shared-storage access (`~/storage`)](PART_4_1_STORAGE.md):** ✅ **DONE (device-verified 2026-08-24).**
  Provides `$PREFIX/bin/codec-setup-storage` and `termux-setup-storage` symlinks to Android shared storage (`Downloads`, `Documents`, `DCIM`, `Pictures`, `Music`, `Movies`), Android 11+ All Files Access integration via OSC 1337 terminal escape sequences, and UI triggers in Terminal TopAppBar and Settings.
- **[Part 4.2 — Package-install confirmation UX](PART_4_2_INSTALL_CONFIRMATION.md):** ✅ **DONE (verified 2026-08-24).**
  Provides structured in-terminal transaction summaries (operation, packages, versions, download size, installed disk footprint, security preflight status) with `[Y/n]` confirmation before userland mutation, `-y`/`--yes` bypass, clean abort on `n`, and core package protection.
- **[Part 4.3 — Trust/channel indicator UX](PART_4_3_TRUST_CHANNEL_UX.md):** ✅ **DONE (verified 2026-08-24).**
  Provides transparent trust and channel indicators in Settings ("Package Repository & Trust" card, keyring status, signing subkey fingerprint, "CHECK REPOSITORY" probe) and in terminal CLI (`pkg status`, `pkg trust`, `pkg channel`).
- **[Part 4.4 — Terminal/editor settings parity](PART_4_4_SETTINGS_PARITY.md):** ✅ **DONE (verified 2026-08-24).**
  Closes gaps between terminal and editor settings with custom terminal font family, synchronized terminal theme palettes (Dracula, Monokai, GitHub Dark, Classic Dark), SettingsScreen integration with live TerminalThemePreview card, and reactive DataStore flows.
- **Part 4.5 — Expand curated package catalog (Round 2 — CI Build):** 🚧 **IN PROGRESS (2026-08-24).**
  Decisions and evidence in [`PART_4_5_CATALOG_EXPANSION.md`](PART_4_5_CATALOG_EXPANSION.md):
  15 new roots (`git wget bat ripgrep fd htop tmux tree patch diffutils zstd m4 autoconf automake libtool`),
  repository-only scope (bootstrap unchanged), fail-loud git/bash recipe overrides,
  reviewed `bat`/`util-linux` alternatives entries. CI build dispatch pending explicit confirmation.
- **Part 4.6 — Expand curated package catalog (Round 2 — Publish & Device Gate):** Planning.
- **Part 4.7 — Android-integration foundation slice:** Planning.
