# CodeC — remaining work, broken into clear parts

**Last updated:** 2026-08-22 (end of day) · **Branch:** work merged to
`main` via PR #11 (`arena/01a02962-codec`) — **Part A ✅ device-verified;
Part B code ✅ merged, ⏳ one rebuild left**

The narrative is in [`docs/JOURNEY.md`](JOURNEY.md). This file is the
**task list**: everything still open, split into self-contained, ordered parts
so each can be picked up independently. Parts A–D finish Phase 3; Parts E–F
are Phase 4 polish.

Each part states its goal, its exit condition, and the exact steps. "Done"
means the exit condition is verified, not just the code written.

---

## How to use this file (verification-first — read before acting)

1. **Start from `prompt.md`** (repo root). Paste it as the first message of a
   new chat. It encodes the self-distrust protocol and the order of work below.
2. **Verify state before acting.** Nothing in this file is true until confirmed
   against the repo: `git status`, `git ls-remote origin`, `gh pr list`,
   `gh run list`. CI state and published releases drift — check them.
3. **Evidence before hypothesis.** If something fails, capture the actual
   output (device Term, CI log, file contents) before diagnosing. Do not commit
   a fix on a guess.
4. **Do not redo completed work.** Anything marked COMPLETE / ✅ in
   [`JOURNEY.md`](JOURNEY.md) and the handoffs is done. Only revisit it if the
   same symptom reappears AND you have evidence it is a regression.

## Guardrails — do not (violating any of these is a bug, not a shortcut)

- Do **not** add `.` to `PATH`.
- Do **not** use `build-package.sh -I` (installs official `com.termux` debs).
- Do **not** use official Termux repositories or `com.termux` binaries.
- Do **not** overwrite `cc` or replace real ELF `bash` with a shim.
- Do **not** change the TCC link order or move `-o` from last place.
- Do **not** bundle the bootstrap in the APK.
- Do **not** start the ~100-minute package build without an explicit user
  confirmation — and check `gh run list` for an existing run first.
- Do **not** open a second PR in parallel; work from the current branch state.

## Definition of done (applies to every part)

A part is **done** only when:

1. its **Exit condition** is met on the right target (device/CI/repo), and
2. no **invariant** above was violated, and
3. the change is committed and pushed with a message naming the part.

---

## Part A — Republish a clean bootstrap — ✅ DONE (device-verified)

**Status: COMPLETE (2026-08-22) — and it shipped _without_ the ~104-minute
rebuild.** The published `userland-v2-dev` assets were repaired **in place**
by the owner in Termux (Path 2 of
[`PART_A_ARTIFACT_REPAIR.md`](PART_A_ARTIFACT_REPAIR.md), which patches exactly
one line of the seeded `var/lib/dpkg/status`), then triple-verified: the
script's internal proofs, the GitHub asset-digest API (aarch64
`074806ad9066d4642d4779a28abf7aeb442c76ae9cb115b12b796eac9a9643b1`, x86_64
`9f93edd06129b04cb4652dae7353b41998aee6d0e04e3d9f59bf3a4e426835ce`), and a
clean-device test — full uninstall → fresh APK → Install userland →
`grep clang` on the status DB finds nothing, full nano install cycle clean.
The exit condition below is **met**; the steps below are kept for history
only — do not redo this part.

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

**Status (2026-08-22, end of day): code COMPLETE, merged to `main` (PR #11),
49/49 host tests green.** `plan-bootstrap.py` (closure walk mirroring pinned
upstream `pull_package` semantics, fail-loud on unresolved deps) + reworked
`assemble-bootstrap.sh` (closure-only extract/seed, upstream-format
`md5sums`, assembly-time alternatives wiring incl. the dpkg admin DB,
format measured against live dpkg) + 24 new host tests.
Seed set = `busybox bash apt dpkg coreutils less` (see
`CODEC_BOOTSTRAP_SEED_PACKAGES`).

**The ~104-minute rebuild is the ONLY remaining step** — it has consumed 3
dispatches and none has produced an artifact yet:

| # | Run | Duration | Failed at | Cause |
|---|---|---|---|---|
| 1 | `32581293757` | ~2 min | `Validate CodeC overlay` | **Our bug — fixed:** the guardrail scanner matched the repair script's own invariant *comment*; wording fixed + `tests/test_ci_guardrails.py` tripwire added so it can't recur silently. |
| 2 | `32582311088` | ~50 min | `Build Phase 3 package-manager bootstrap` | **Upstream network flake — log-proven, not our code:** `curl: (28)` downloading `util-macros-1.20.2.tar.xz` from `xorg.freedesktop.org` (3×30 s retries all timed out). The ~40-min compile step had already passed; the assembler never ran. |
| 3 | `32585409356` | ~48 min | `Build Phase 3 package-manager bootstrap` (~33 min into the step, both arch jobs within 30 s of each other) | **Unknown until the log is read.** The agent sandbox cannot download CI logs — see below. |

### Continue here (new chat) — in this order, BEFORE spending another ~104 minutes

1. **Read dispatch 3's log tail** (in Termux, where `gh` is authenticated —
   the agent sandbox has no egress to the log hosts):
   ```sh
   gh run view --job 97060936792 --log | tail -120   # aarch64 job
   # x86_64 twin, if needed: --job 97060936787
   ```
2. **Diagnose by the evidence, not by guessing:**
   - `curl: (28)` / `Failed to download <url>` → another upstream flake. Add
     a recipe-level mirror override for that host in
     `codec-packages/scripts/apply-recipe-overrides.sh` (the attr/libacl
     Savannah-mirror pattern is already in that file), push, then redispatch.
   - `assemble-bootstrap.sh:` / `plan-bootstrap.py:` / `update-alternatives`
     errors → a real bug in the Part B code: first reproduce it in a host
     fixture test under `codec-packages/tests/`, fix, push, then redispatch.
3. **Redispatch — from `main`** (the code is merged now):
   ```sh
   gh workflow run "CodeC package repository" --ref main
   gh run watch
   ```
4. **On success, republish the bootstrap** (this re-runs
   `validate-bootstrap.py` and swaps the release assets):
   ```sh
   gh workflow run "Publish CodeC bootstrap release" --ref main \
     -f source_run_id=<RUN_ID> -f release_tag=userland-v2-dev
   ```
5. **Device verification (Part B exit condition, ~10 min).** Full app
   uninstall → install the latest successful `Build APK` artifact → "Install
   userland", then in the CodeC terminal, one block at a time:
   ```sh
   grep -n clang $PREFIX/var/lib/dpkg/status; echo exit=$?   # expect: no output, exit=1
   pager -V                    # expect: GNU less version banner
   which pager editor vi       # expect: all resolve under $PREFIX/bin
   dpkg --audit                # expect: empty output
   dpkg -l | grep -E 'doxygen|swig|tcl|tor|fontconfig'; echo exit=$?   # expect: exit=1
   pkg update                  # NOTE: 'W: No Hash entry in Release file' is EXPECTED until Part D — not a bug
   pkg install nano && nano --version    # expect: GNU nano, version 9.2
   which editor                # expect: $PREFIX/bin/editor
   pkg uninstall nano
   printf '#include <stdio.h>\nint main(){printf("ok\\n");return 0;}\n' > t.c
   cc t.c -o a.out && ./a.out  # expect: ok
   ```
   When every line matches, Part B's exit condition is met — mark it ✅ here
   and in [`JOURNEY.md`](JOURNEY.md), then move to Part C.

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

| Part | Depends on | Effort / state (2026-08-22) |
|---|---|---|
| A — republish clean bootstrap | — | ✅ **DONE** (in-place repair, no rebuild, device-verified) |
| B — bootstrap correctness | A | code ✅ merged; ⏳ **one rebuild run left** (see "Continue here") |
| C — clean-device acceptance | A ✅ (B ideally) | device time |
| D — M3 signing | A ✅ / B | medium |
| E — storage access | none (parallel) | medium |
| F — confirmation/signing UX | D | small–medium |

**Shortest path to "Phase 3 complete":** B's rebuild → C → D. (A is done;
B's code is merged — only its build/republish/device-verify remains.)
