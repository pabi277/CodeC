# CodeC — remaining work, broken into clear parts

**Last updated:** 2026-08-22 · **Branch:** `arena/01a028e2-codec`

The narrative is in [`docs/JOURNEY.md`](JOURNEY.md). This file is the
**task list**: everything still open, split into self-contained, ordered parts
so each can be picked up independently. Parts A–D finish Phase 3; Parts E–F
are Phase 4 polish.

Each part states its goal, its exit condition, and the exact steps. "Done"
means the exit condition is verified, not just the code written.

---

## Part A — Republish a clean bootstrap (unblocks fresh devices)

**Why.** The published `userland-v2-dev` bootstrap predates the `dpkg-perl`
clang fix, so a *fresh* device still hits `E: dpkg-perl : Depends: clang but it
is not installable` until `pkg` self-heals it (which only happens after the
user runs a `pkg` command that calls `require_backend`). The self-heal makes
this non-blocking, but a clean bootstrap is the correct permanent state.

**Exit condition.** A fresh install of the APK → "Install userland" →
`pkg install nano` works with **zero manual fixes**, because the bootstrap's
status DB no longer references `clang`.

**Steps.**
1. Confirm the current branch is merged to `main` (the recipe fix lives in
   `apply-recipe-overrides.sh`, already committed here).
2. From Termux: `gh workflow run "CodeC package repository" --ref main`.
3. `gh run watch <RUN_ID>` (~1h14m).
4. `gh workflow run "Publish CodeC bootstrap release" --ref main
   -f source_run_id=<RUN_ID> -f release_tag=userland-v2-dev`.
5. On a clean device: install APK → Install userland → `pkg install nano`.

---

## Part B — Fix bootstrap correctness (seed the right thing)

**Why.** The current bootstrap has three content defects, all visible on device:

1. **Build-dependency pollution.** `assemble-bootstrap.sh` seeds *every* built
   `.deb` — including build-only packages (`doxygen`, `swig`, `tcl`,
   `fontconfig`, `tor`, …) — into `var/lib/dpkg/status`. That is how `clang`
   (a build tool) leaked in as a runtime dependency in the first place. The
   bootstrap should record **only** the transitive `Depends` closure of the
   four roots (`busybox bash apt dpkg`).
2. **Seeded packages never run their postinst.** Their alternatives are never
   registered → `pager: command not found`, `editor` only appears after a real
   `pkg install`. The seeded `coreutils`/`less`/`nano`-style roots should have
   their alternatives wired at assembly time (or via a post-install script).
3. **No `md5sums`.** Every seeded package fails `dpkg --audit` with "missing
   the md5sums control file".

**Exit condition.** On a fresh bootstrap: `pager -V` reports `less`,
`dpkg --audit` is clean for seeded packages, and `dpkg -l` lists only the
runtime closure (no `doxygen`/`swig`/`tcl`/`tor`/…).

**Steps.**
1. In `assemble-bootstrap.sh`, replace the "seed every built `.deb`" loop with
   a closure walk from the four roots: read each root's `Depends`, resolve
   against the built set, and seed only those.
2. Generate `md5sums` control files for the seeded packages (or suppress the
   audit noise by seeding an empty-but-valid `md5sums`).
3. For the seeded `coreutils`/`less` roots, emit the alternatives (either run
   their postinst in a chroot-free way at assembly, or ship the
   `update-alternatives` links directly).
4. Rebuild + republish (Part A steps), then re-verify.

---

## Part C — Clean-device acceptance (the M2 gate)

**Why.** [`docs/PHASE3_DEVICE_ACCEPTANCE.md`](PHASE3_DEVICE_ACCEPTANCE.md)
still says **NOT PASSED**, and it is the explicit exit condition for M2. The
work done so far was on a *patched* device, not a clean one.

**Exit condition.** Every unchecked item in `PHASE3_DEVICE_ACCEPTANCE.md`
passes on a clean arm64 device (and x86_64 if available), including the
negative checks and the recovery tests.

**Steps (in order, on a clean device).**
1. Uninstall CodeC fully; install a fresh APK (≥ 1.3.15) + Install userland.
2. Runtime smoke (section 2 of the checklist): `$PREFIX`, `which bash`,
   `$BASH_VERSION`, `busybox`, `which apt-get dpkg`, `dpkg --print-architecture`,
   `dpkg -l`.
3. Package ops (section 3): `pkg update / search nano / install nano /
   nano --version / uninstall nano / upgrade`, then the `coreutils`/`less`/`nano`
   alternatives closure (`which pager editor`, `pager -V`).
4. Negative checks: `sources.list` is CodeC-only; `pkg uninstall bash` refused;
   no `com.termux` in `dpkg -l`.
5. Compiler smoke before **and** after package ops (section 4).
6. Airplane-mode restart (section 5).
7. Interrupted-install recovery (section 6): kill mid-download, retry,
   `pkg repair`.
8. v1 → Phase 3 upgrade path (section 7) on a second device.

---

## Part D — M3: sign the repository and verify on device

**Why.** The dev channel is HTTPS + SHA-256 only and is explicitly **not** a
trusted production channel. Signing closes the integrity-vs-tampering gap
(`[trusted=yes]` currently disables apt's own signature checks).

**Exit condition.** A device with only the CodeC trust file installed accepts
the repository *because* its `Release`/`InRelease` signature verifies — and
rejects a tampered one — without `trusted=yes`.

**Steps.**
1. Generate a CodeC signing key; sign `Release` → `Release.gpg` / `InRelease`.
2. Publish the key in a CodeC-owned, versioned trust file, and install it
   into `$PREFIX/etc/apt/trusted.gpg.d/` from the bootstrap.
3. Remove `trusted=yes` from the `pkg` sources line; rely on apt signature
   verification (`Verify-Peer` + signature).
4. Add negative tests: a corrupted `Packages` or a missing signature must fail
   before any package installs.
5. Document key rotation and rollback.

---

## Part E — Phase 4 polish: storage access

**Goal.** A `termux-setup-storage`-equivalent so users can read/write shared
storage (`~/storage/downloads` etc.) from the terminal.

**Exit condition.** `cp hello.c ~/storage/downloads/` works after the user
grants storage permission, using Android's scoped-storage APIs (no `noexec`
landmine, no path hard-coding).

---

## Part F — Phase 4 polish: confirmation + signing UX + themes

**Goal.** The remaining "trust" and UX items from [`TERMINAL_PLAN.md`](TERMINAL_PLAN.md) §11–12:

1. **Security confirmation prompt** — surface the "install a package" intent
   and show the resolved package set before dpkg runs (mirrors the `pkg`
   preflight, but user-visible).
2. **Signing UX** — surface whether the channel is dev (unsigned) vs
   production (signed) in the terminal/Settings.
3. **Themes / env / settings parity** — terminal theme, font, and environment
   options already in Settings; close any gaps vs. the editor experience.

**Exit condition.** A user can see *what* is being installed before it happens,
and can tell at a glance whether they are on the trusted channel.

---

## Ordering summary

| Part | Depends on | Effort |
|---|---|---|
| A — republish clean bootstrap | nothing (recipe fix committed) | ~1 rebuild run |
| B — bootstrap correctness | A | medium (build script) |
| C — clean-device acceptance | A (B ideally) | device time |
| D — M3 signing | A/B | medium |
| E — storage access | none (parallel) | medium |
| F — confirmation/signing UX | D | small–medium |

**Shortest path to "Phase 3 complete":** A → C → D. (B improves correctness
and should land with A, but is not strictly required to pass C.)
