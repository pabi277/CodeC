# CodeC Phase 4 roadmap

**Status (2026-08-25):** Parts 4.1–4.5 ✅ done (device-verified or CI-verified).
Part 4.5 (expanded catalog, round 2) completed in CI workflow run
[`32845127723`](https://github.com/pabi277/CodeC/actions/runs/32845127723) (1h 53m 36s, 25 roots, both arches green).
Part 4.6 (publish & device acceptance) is in progress / ready for pickup. Phase 3 (package management: `pkg`,
apt/dpkg bootstrap, signed repository) is complete and device-verified — see [`JOURNEY.md`](JOURNEY.md)
and [`chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`](chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md).
This document is the entry point for Phase 4: polish and expansion on top of that foundation.

This is deliberately a **roadmap**, not an execution plan like
[`chat-phase3/PHASE3_PLAN.md`](chat-phase3/PHASE3_PLAN.md)/[`NEXT_STEPS.md`](NEXT_STEPS.md). It names
parts, their rough scope, and their dependencies. It does **not** pin down
exact technical decisions, UI designs, package lists, or exit-condition
wording — those get decided (and written down, updated in this file) when a
part is actually picked up, using the same verify-first / evidence-before-
hypothesis discipline used throughout Phase 3. Do not treat anything below as
a spec; treat it as a starting scope.

## Why parts are sized the way they are

Each part below is scoped so a single fresh chat session can plausibly take it
from "not started" to "exit condition met and verified" in roughly
**50–70 agent replies** — enough for the usual cycle of reading context,
proposing an approach, writing code, running host tests, and iterating on real
device/CI feedback, without running so long that context or focus degrades.

This is a budget, not a target to hit exactly. Two consequences:

- If a part turns out bigger once its real scope is understood, **split it**
  into sequential sub-parts at that time (e.g. `K1`, `K2`) rather than letting
  one chat run indefinitely long or cutting corners to fit.
- If a part turns out smaller, that is fine — "done" is still governed by its
  exit condition, not by using the full budget.

Some items below are pre-split into multiple parts already, because their
Phase 3 analogue (build → publish → device-verify) is known from this project's
history to need more than one part's worth of work.

## How to use this file

1. Read [`JOURNEY.md`](JOURNEY.md) and [`NEXT_STEPS.md`](NEXT_STEPS.md) first
   to confirm Phase 3's state has not regressed.
2. Pick **one** part below. Confirm with the project owner which one before
   starting, and confirm current repo/PR/CI state — do not assume anything in
   this file is still accurate; it will drift as work happens elsewhere.
3. At the start of that part's work, write down the specific technical
   decisions this file left open (in the part's own PR/docs, not necessarily
   back in this file), then build, test, and verify exactly like Phase 3:
   evidence before hypothesis, no unverified "done" claims, respect the
   existing invariants (`chat-phase3/PHASE3_PLAN.md` §1, `chat-phase1/SOLUTIONS.md`,
   `chat-phase2/SOLUTIONS.md`).
4. Update this roadmap's status line for that part when it is actually
   finished and verified — not before.

## Parts

### Part 4.1 — Shared-storage access — ✅ DONE (device-verified)

**Status: COMPLETE and device-verified (2026-08-24).**
- Built-in `$PREFIX/bin/codec-setup-storage` (and `$PREFIX/bin/termux-setup-storage` compatibility alias) creates symlinks under `$HOME/storage/` (`shared`, `downloads`, `documents`, `dcim`, `pictures`, `music`, `movies`, `external-*`).
- Integrated Android 11+ All Files Access (`MANAGE_EXTERNAL_STORAGE`) support with in-band OSC 1337 terminal escape and UI trigger (`FolderOpen` icon in Terminal TopAppBar and Settings $\rightarrow$ Storage $\rightarrow$ `SETUP STORAGE`).
- Verified on a real Android device: symlink creation, write probing, in-terminal write (`echo > ~/storage/downloads/...`) and removal (`rm`), and embedded compiler coexistence all passed.

**Basic goal.** Give terminal programs a way to read/write user-designated
shared storage (e.g. `~/storage/downloads`-style access to Downloads/
Pictures/etc.), analogous to Termux's `termux-setup-storage`, using Android's
current scoped-storage model and explicit user consent.

**Complexity:** small. **Est. ~50–70 replies.** Single, fairly self-contained
feature; no dependency on other Phase 4 parts.

**Left open on purpose:** whether this is a SAF-backed bridge, a
`MediaStore`-based helper, or something else; exactly which shared
directories are exposed and how they appear in `$HOME`; how/when the
permission is requested and re-requested if revoked.

---

### Part 4.2 — Package-install confirmation UX — ✅ DONE (verified)

**Status: COMPLETE and verified (2026-08-24).**
- In-terminal transaction summary for `pkg install`, `pkg upgrade`, and `pkg uninstall` detailing operation, package names, versions, archive download sizes, estimated installed disk footprint, and preflight security verification status (`Preflight: PASSED`).
- Interactive `[Y/n]` prompt (defaulting to yes on `<Enter>` or `y`/`yes`) requiring explicit user confirmation before any package extraction or dpkg database mutation occurs.
- Clean cancellation on `n`/`no`: cleans cached archives, removes pending transaction markers, leaves dpkg status database untouched, and exits cleanly with 0.
- Positional flag parsing for `-y`, `--yes`, `--assume-yes` across `install`, `upgrade`, and `uninstall` to support scripted and unattended usage. Non-interactive invocations without `-y` fail closed if stdin is not a terminal.
- Unit and host test suites in Kotlin and Python all passing (71/71 tests green).
- Full record in [`docs/chat-phase4/PART_4_2_INSTALL_CONFIRMATION.md`](chat-phase4/PART_4_2_INSTALL_CONFIRMATION.md).

**Basic goal.** Before `pkg install`/`dpkg` actually mutates the system, show
the user what is about to happen (package set, versions, download size) and
require explicit confirmation, making the existing internal `pkg` preflight
checks user-visible instead of silent.

**Complexity:** small. **Est. ~50–70 replies.**

---

### Part 4.3 — Trust/channel indicator UX — ✅ DONE (verified)

**Status: COMPLETE and verified (2026-08-24).**
- "Package Repository & Trust" section in Settings with verified trust badge, development channel (`stable/main`), repository URL (`https://pabi277.github.io/CodeC/dev`), keyring metadata (`codec-archive-keyring-v1.gpg`), signing subkey fingerprint (`328500868CE9B0F74B62CEFC1D7D52F6F8135015`), and "CHECK REPOSITORY" connectivity probe.
- Terminal CLI `pkg status` (aliases `pkg trust`, `pkg channel`) displaying full repository, channel, keyring, and signing subkey status.
- Friendly `friendly_apt` hint on unindexed package searches (`pkg: package not found; run 'pkg update' first to refresh the package catalog.`).
- Full record in [`docs/chat-phase4/PART_4_3_TRUST_CHANNEL_UX.md`](chat-phase4/PART_4_3_TRUST_CHANNEL_UX.md).

**Basic goal.** Let a user tell at a glance, in Settings and/or the terminal,
whether they are talking to the signed CodeC repository and which channel/
release they are on, instead of that information being visible only via raw
`pkg`/`apt` output.

**Complexity:** small. **Est. ~50–70 replies.**

---

### Part 4.4 — Terminal/editor settings parity — ✅ DONE (device-verified)

**Status: COMPLETE and device-verified (2026-08-24).**
- Terminal theme palette parity across all 4 CodeC themes (`Dracula`, `Monokai`, `GitHub Dark`, `Classic Dark`), syncing canvas background, foreground text, cursor, selection, and container colors.
- Custom Terminal font family selection (`Monospace`, `Courier`, `Sans Serif`, `Serif`) with dynamic canvas metrics recomputation (cell width, font spacing, ascent).
- Unified `SettingsScreen` controls under Terminal Settings and Appearance with a live `TerminalThemePreview` card reflecting active font family, font size, and terminal theme colors.
- Reactive DataStore preference flows (`terminalThemeFlow`, `terminalFontFamilyFlow`, `terminalFontSizeFlow`) updating emulator and settings in real time.
- Unit test suite (`TerminalThemeTest.kt`) and device verification all passing.
- Full record in [`docs/chat-phase4/PART_4_4_SETTINGS_PARITY.md`](chat-phase4/PART_4_4_SETTINGS_PARITY.md).

**Basic goal.** Close gaps between the terminal's theme/font/environment
options and the editor's existing `SettingsScreen`/`ThemeManager` so the two
feel like one coherent product rather than two separately configured
surfaces.

**Complexity:** small–medium. **Est. ~50–70 replies.**

---

### Part 4.5 / 4.6 — Expand the curated package catalog (round 2)

**Status: Part 4.5 COMPLETE (CI verified 2026-08-25); Part 4.6 IN PROGRESS.**
Decisions recorded in [`chat-phase4/PART_4_5_CATALOG_EXPANSION.md`](chat-phase4/PART_4_5_CATALOG_EXPANSION.md):
round 2 roots = `git wget bat ripgrep fd htop tmux tree patch diffutils zstd
m4 autoconf automake libtool` (vim/openssh/python3 deferred to round 3 with
reasons); repository-only scope (bootstrap byte-identical, verified by
digest); two fail-loud recipe overrides (bash `termux-tools` removal shared
by both builds; git gitk/git-gui/git-svn subpackage exclusion +
`--with-tcltk=no`); reviewed maintainer-script allowlist extended by
`bat`/`util-linux` (both below `less`'s pager priority, so the default pager
cannot change). CI workflow run [`32845127723`](https://github.com/pabi277/CodeC/actions/runs/32845127723)
(1h 53m 36s) completed with 100% success for both `aarch64` and `x86_64`.
Part 4.6 publishes run `32845127723` and completes clean-device acceptance.

**Basic goal.** Grow the Phase 3 package set beyond the original curated roots
(`nano less coreutils grep sed gawk gzip tar make libmagic`, per
[`chat-phase3/PHASE3_PLAN.md`](chat-phase3/PHASE3_PLAN.md) §4 and [`codec-packages/README.md`](../codec-packages/README.md))
with more commonly requested tools, using the same source-build /
dependency-closure / signed-repository / device-acceptance discipline already
proven in Phase 3 — not a new mechanism.

**Complexity:** medium–large, **pre-split into two parts** because Phase 3's
own history shows the build side and the device-acceptance side are each a
full part's worth of work on their own:

- **Part 4.5 — build and validate the expanded closure in CI.** ✅ **DONE (CI verified 2026-08-25).**
  Candidate package list, recipe overrides, host-test coverage, and a green CI build (`32845127723`).
- **Part 4.6 — publish and device-verify the expanded repository/bootstrap.** 🚧 **IN PROGRESS.**
  Mirrors Part D's publish + clean/upgrade-device acceptance pattern, publishing run `32845127723` via `source_run_id`.

**Left open on purpose:** exactly which packages, and whether new packages
ship inside the bootstrap or are only ever installed later via `pkg` from the
repository (most should probably be the latter, to keep the bootstrap small —
confirm this when the part starts).

---

### Part 4.7 — Termux:API-style Android integration (foundation slice)

**Basic goal.** A first, narrow slice of Android-native capability exposed to
terminal programs (for example, a single low-risk capability such as
clipboard access or a vibrate/notify call — the exact first capability is
chosen when this part starts), establishing a reusable pattern (permission
handling, the bridge/IPC mechanism, and a CLI command shape) that later
capabilities can follow without redesigning the plumbing each time.

**Complexity:** large epic. **This entry only scopes a first foundation
slice at ~50–70 replies.** [`TERMINAL_PLAN.md`](TERMINAL_PLAN.md) §12 lists
this area as explicitly out of scope for Phase 3, and it is the least-defined
item in this roadmap — treat it as lower priority than Parts 4.1–4.6, and
expect it to spawn further numbered parts (`4.8`, `4.9`, ...) once the
foundation slice establishes the pattern and a real backlog of specific
capabilities exists. Do not attempt the whole surface area in one part.

---

## Not a Phase 4 part, but still open

- **Optional x86_64 repeat of the Part D clean-device test.** Phase 3's exit
  condition was met on aarch64; an x86_64 repeat was never run because no
  x86_64 device was available (see [`chat-phase3/PHASE3_PLAN.md`](chat-phase3/PHASE3_PLAN.md) §5, M3).
  This can be done opportunistically whenever an x86_64 device is available.
  It does not block any Phase 4 part and is not itself renumbered as one.

## Ordering / dependency summary

| Part | Depends on | Complexity | Est. replies |
|---|---|---|---|
| 4.1 — storage access | none | small | ✅ **DONE** (device-verified) |
| 4.2 — install confirmation UX | none | small | ✅ **DONE** (verified) |
| 4.3 — trust/channel indicator UX | none (reads existing Part D metadata) | small | ✅ **DONE** (verified) |
| 4.4 — settings/theme parity | none | small–medium | ✅ **DONE** (device-verified) |
| 4.5 — expanded package build (CI) | none | medium | ✅ **DONE** (run `32845127723` green) |
| 4.6 — expanded package publish + device accept | 4.5 | medium | 🚧 **IN PROGRESS** (~50–70) |
| 4.7 — Android integration foundation slice | none | large (epic seed) | ~50–70 (first slice only) |

None of these parts block each other except 4.6 on 4.5. Pick whichever the
project owner wants next; nothing here is a fixed sequence.
