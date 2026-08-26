# CodeC — Phase 3 task list (complete) and Phase 4 pointer

**Last updated:** 2026-08-24 · **State:** Parts A, B, C, and D ✅ ALL
device-verified. Phase 3's device-acceptance gate is complete: Part D code,
signed Pages, signed-client acceptance, the key-seeded bootstrap build/release,
and the final rebuilt-bootstrap clean-device pass are all done in PR #14.
Remaining work is Phase 4, planned separately in
[`PHASE4_ROADMAP.md`](PHASE4_ROADMAP.md) — nothing in Phase 4 has been coded.

The narrative is in [`docs/JOURNEY.md`](JOURNEY.md). This file is the
Phase 3 **task list**, kept for its history: Parts A–D, all now done, split
into self-contained parts so each could be picked up independently. Phase 4's
task list lives in [`PHASE4_ROADMAP.md`](PHASE4_ROADMAP.md) instead of here.

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
[`chat-phase3/PART_A_ARTIFACT_REPAIR.md`](chat-phase3/PART_A_ARTIFACT_REPAIR.md), which patches exactly
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

## Part B — Fix bootstrap correctness (seed the right thing) — ✅ DONE

**Status: COMPLETE and device-verified (2026-08-23).** PR #13 merged at
`35c350f338be34303296b0168933622991258142`; package dispatch #5
(`32620704350`) and publish run `32625580655` succeeded. The published
aarch64 archive is **23,926,127 bytes** with GitHub asset digest
`sha256:863f18528afa126d19481f7308a3f9b23997fda9ad9cae3bc7033d8fa60e60cd`.
A fresh-device run passed curl/TLS, no-clang/no-build-pollution/no-keyring,
alternatives, `dpkg --audit`, nano 9.2 lifecycle, and embedded-compiler checks.
The exit condition below is met. **Do not rerun the expensive build or re-test
Part B unless Part C first records a genuine new defect, and never dispatch a
build without explicit approval.** The remaining material in this section is
historical context.

**Earlier status (2026-08-22, end of day): code COMPLETE, merged to `main`
(PR #11), 49/49 host tests green.** `plan-bootstrap.py` (closure walk mirroring pinned
upstream `pull_package` semantics, fail-loud on unresolved deps) + reworked
`assemble-bootstrap.sh` (closure-only extract/seed, upstream-format
`md5sums`, assembly-time alternatives wiring incl. the dpkg admin DB,
format measured against live dpkg) + 24 new host tests.
Seed set = `busybox bash apt dpkg coreutils less` at merge time (2026-08-23:
`curl` joined the seed set and `libcurl` the build roots — see the next
status block; see `CODEC_BOOTSTRAP_SEED_PACKAGES`).

**Historical status before PR #13 (2026-08-23):** the first rebuilt bootstrap
exposed two fresh-device defects. Both were fixed on the branch that followed
PR #12 and at that time still awaited rebuild, republish, and device verification.

- **Defect 1 — no HTTPS metadata fetcher.** The Part B closure seeds none of
  `curl`/`python3`/`wget` (the in-code claim that python3 was in the closure
  was wrong), so on a fresh device `pkg update` died at
  `pkg: offline or unable to download CodeC Release metadata (HTTPS required)`.
  Also, `pkg`'s maintainer-script byte checks (`spec_in_file`) called
  `python3`, which would have broken `pkg install` preflight the same way.
  **Fix:** `libcurl` joins the build roots (the `curl` CLI is its subpackage
  at the pinned revision, auto-generating `Depends: libcurl (= …)` so the
  closure walker resolves it), `curl` joins the seed set, and
  `spec_in_file` is now pure shell (`$(cat)` + `case`). `ca-certificates`
  (`etc/tls/cert.pem`, curl's CA bundle) was already in the closure via
  `apt → libgnutls → ca-certificates`. Python stays out (much larger
  closure).
- **Defect 2 — official Termux keyring seeded.** Fresh-device
  `dpkg-query` showed `ii termux-keyring 3.13`. The pinned apt recipe lists
  `termux-keyring` (GPG keys of the *official* Termux repositories, installed
  into `etc/apt/trusted.gpg.d/`) as a runtime dependency. **Fix:**
  `apply-recipe-overrides.sh` now removes exactly `, termux-keyring` from
  apt's `TERMUX_PKG_DEPENDS` (fail-loud on pinned-recipe drift).
  `termux-licenses` deliberately stays: it ships `$PREFIX/share/LICENSES/*`,
  the target of packaged license symlinks (e.g. nano's
  `share/licenses/nano`).
- **Release gate hardened:** `validate-bootstrap.py` now requires `bin/curl`
  (ELF) in the archive and rejects any `termux-keyring` stanza in the seeded
  dpkg status. Host suite: 53/53 green (fixtures prove the fetcher is
  seeded and termux-keyring is excluded without any 100-minute build).

**Historical rebuild record.** Dispatches 1–4 led to the final fixes; dispatch
#5 and the subsequent publish/device verification completed the part. The
first three runs below predate PR #12; #4 is the successful rebuild whose
bootstrap exposed the two defects above:

| # | Run | Duration | Result |
|---|---|---|---|
| 1 | `32581293757` | ~2 min | **Our bug — fixed:** the guardrail scanner matched the repair script's own invariant *comment*; wording fixed + `tests/test_ci_guardrails.py` tripwire added. |
| 2 | `32582311088` | ~50 min | **Upstream network flake — log-proven:** `curl: (28)` fetching `util-macros` from `xorg.freedesktop.org`; fixed by the PR #12 mirror override. |
| 3 | `32585409356` | ~48 min | Same util-macros step; cause unreadable from the agent sandbox; same mirror override applied. |
| 4 | `32594910882` | 1h14m (aarch64) / 1h26m (x86_64) | ✅ **Success** → published by `32617929254` → fresh-device download/verify/extract OK → defects 1+2 found → this fix. |
| 5 | `32620704350` | ~1h20m | ✅ **Success** from PR #13 merge → published by `32625580655` → full Part B fresh-device acceptance passed. |

### Completed procedure (historical — do not rerun)

The following was the final procedure and is retained only as an audit trail.
It is **not** a current instruction.

1. **Redispatch from `main`** (the new `libcurl` root builds OpenSSL +
   libnghttp2/3 + libtcp2-family + libssh2 first):
   ```sh
   gh workflow run "CodeC package repository" --ref main
   gh run watch
   ```
   If it fails on a source download, follow the established pattern: add a
   narrow mirror override in `apply-recipe-overrides.sh` (attr/libacl and
   util-macros precedents), with a host test, then redispatch.
2. **On success, republish the bootstrap** (this re-runs
   `validate-bootstrap.py` — which now enforces `bin/curl` and the absence
   of `termux-keyring` — and swaps the release assets):
   ```sh
   gh workflow run "Publish CodeC bootstrap release" --ref main \
     -f source_run_id=<RUN_ID> -f release_tag=userland-v2-dev
   ```
3. **Device verification (Part B exit condition, ~10 min).** Full app
   uninstall → install the latest successful `Build APK` artifact → "Install
   userland", then in the CodeC terminal, one block at a time:
   ```sh
   grep -n clang $PREFIX/var/lib/dpkg/status; echo exit=$?   # expect: no output, exit=1
   command -v curl             # expect: $PREFIX/bin/curl (HTTPS metadata fetcher)
   curl -fsSI --max-time 30 https://pabi277.github.io/CodeC/dev/dists/stable/Release >/dev/null && echo tls-ok
   pager -V                    # expect: GNU less version banner
   which pager editor vi       # expect: all resolve under $PREFIX/bin
   dpkg --audit                # expect: empty output
   dpkg -l | grep -E 'doxygen|swig|tcl|tor|fontconfig|termux-keyring'; echo exit=$?   # expect: exit=1
   pkg update                  # NOTE: 'W: No Hash entry in Release file' is EXPECTED until Part D — not a bug
   pkg install nano && nano --version    # expect: GNU nano, version 9.2
   which editor                # expect: $PREFIX/bin/editor
   pkg uninstall nano
   printf '#include <stdio.h>\nint main(){printf("ok\\n");return 0;}\n' > t.c
   cc t.c -o a.out && ./a.out  # expect: ok
   ```
   Every line matched on the fresh aarch64 device; Part B's exit condition was
   met and recorded here and in [`JOURNEY.md`](JOURNEY.md).

**Original rationale (resolved).** The earlier bootstrap had three content
defects, all visible on device:

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

**Completed implementation steps.**
1. In `assemble-bootstrap.sh`, the "seed every built `.deb`" loop was replaced
   with a closure walk from the roots: read each root's `Depends`, resolve
   against the built set, and seed only those.
2. Upstream-format `md5sums` control files were generated for seeded packages.
3. Seeded-package alternatives were emitted at assembly time, including the
   dpkg admin database.
4. The bootstrap was rebuilt, republished, and device-verified.

---

## Part C — Clean-device acceptance (the M2 gate)

**Status: COMPLETE and device-verified (2026-08-23).** A clean Samsung
SM-A356E (Android 16, aarch64) passed bootstrap/runtime smoke, package
operations, alternatives, negative checks, compiler checks, airplane-mode
restart, and interrupted-install recovery. That recovery test exposed a stale
`codec-pkg/lock`; PR #14 commit `8e95a16` fixed dead-PID lock recovery and the
repeated force-stop test passed without manual state deletion.

The final second-device test exposed a wrong legacy-marker assumption:
v1.3.14 actually writes `.userland-vuserland-v1`. PR #14 commit `a4e5af6`
corrected it, CI passed, and an in-place v1 → `userland-v2-dev` update then
passed the full package/compiler/contamination block. The exit condition below
is met.

**Why.** [`docs/chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`](chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md) is
the explicit clean-device acceptance gate. It now records every item as passed.

**Exit condition.** Every unchecked item in `chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`
passes on a clean arm64 device (and x86_64 if available), including the
negative checks and the recovery tests.

**Steps (in order, on a clean device).**
1. [x] Uninstall CodeC fully; install a fresh APK (≥ 1.3.15) + Install userland.
2. [x] Runtime smoke (section 2 of the checklist): `$PREFIX`, `which bash`,
   `$BASH_VERSION`, `busybox`, `which apt-get dpkg`, `dpkg --print-architecture`,
   `dpkg -l`.
3. [x] Package ops (section 3): `pkg update / search nano / install nano /
   nano --version / uninstall nano / upgrade`, then the `coreutils`/`less`/`nano`
   alternatives closure (`which pager editor`, `pager -V`).
4. [x] Negative checks: `sources.list` is CodeC-only; `pkg uninstall bash`
   refused; no `com.termux` in `dpkg -l`.
5. [x] Compiler smoke before **and** after package ops (section 4).
6. [x] Airplane-mode restart (section 5).
7. [x] Interrupted-install recovery (section 6): force-stop mid-download,
   automatically reclaim the stale lock, retry, and confirm `pkg repair` clean.
8. [x] v1 → Phase 3 upgrade path (section 7) on a second arm64 device.

---

## Part D — M3: sign the repository and verify on device — ✅ DONE (device-verified)

**Status: COMPLETE (2026-08-24) — implementation, signed publication, client
acceptance, bootstrap build/release, and the final clean-device gate have all
passed.** PR #14 contains key-agnostic signing/validation, protected-subkey CI
support, the public-only production keyring, signed-only client/bootstrap
integration, and tamper/missing-signature tests. Corrective signed publication
run `32642631785` reused existing artifacts, skipped both expensive builds, and
fixed the APT Release-stanza defect found by the first device attempt. The
real CodeC device then passed signed update, exact-key verification, tamper
rejection, nano lifecycle, clean audit, and compiler checks. Build run
`32643383952` then built both architectures successfully; each archive passed
the validator's exact v3-keyring byte comparison and was uploaded as a
non-expired artifact. Release run `32648783080` revalidated both archives and
replaced the four `userland-v2-dev` assets.

**Final clean-device gate — passed 2026-08-24.** After a verified pre-uninstall
backup (checksum, `gzip -t`, listing, independent `cmp`), a full uninstall and
fresh reinstall against the exact published `userland-v2-dev` archive
(aarch64, 23,928,215 bytes) reached `userland: ready` automatically and passed
every remaining section-8 check on a real device: no `clang`/build-pollution/
`termux-keyring` (verified with an exact package-name match after an
unanchored `grep` gave a `sed`-description false positive), CodeC-only
`sources.list`, warning-free signed `pkg update` with an exact keyring-hash
match, independent `gpgv` acceptance of the live signature and rejection of a
tampered copy, a full nano install/uninstall cycle with working alternatives,
a silent `dpkg --audit`, and a working embedded compiler. Full commands and
output are recorded in
[`chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`](chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md) §8. **Do not
re-run this expensive/destructive test unless a genuine new defect is found;
this Part is done.**

**Why.** The development channel originally relied on HTTPS + SHA-256 only.
Signing closes the integrity-vs-tampering gap. The Part D client removes `[trusted=yes]`, verifies
`InRelease` with `gpgv` before apt, and lets apt independently verify through an
exact `signed-by=` keyring.

**Exit condition.** A device with only the CodeC versioned keyring installed
accepts the repository because its `InRelease` signature and Release/index/
package hash chain verify, rejects tampered metadata, and uses no
`trusted=yes`. The rebuilt bootstrap contains byte-for-byte the same keyring.

**Completed inexpensive implementation.**
1. `sign-repository.sh` emits `InRelease` and `Release.gpg` with an exact
   dedicated signing subkey; the protected passphrase travels through stdin,
   never argv.
2. `validate-repository.py` requires both signature forms, exact cleartext,
   exact fingerprint, and the existing index/package hash chains. Real-GPG
   tests cover valid, missing, tampered, and changed-index cases.
3. Public keyring v1 and fingerprints are committed under
   `codec-packages/keys/`; no private key material is committed. The offline
   primary and CI signing subkey fingerprints are recorded in
   [`chat-phase3/REPOSITORY_SIGNING.md`](chat-phase3/REPOSITORY_SIGNING.md).
4. The APK installs that keyring under `etc/apt/keyrings`; `pkg` requires
   `gpgv`, verifies signed Origin/Suite, and writes a CodeC-only `signed-by=`
   source. The Phase 3 bootstrap assembler seeds the same bytes and its
   validator rejects missing/different keyrings.
5. Release hash paths are now relative to `dists/stable/Release`, eliminating
   the historical APT `No Hash entry in Release file` warning.
6. The active publication workflow imports the CI-only subkey, fails closed on
   secret/fingerprint drift, signs before signed validation, and deploys only
   the public key files. Rotation, revocation, rollback, and overlap rules are
   documented.

**Exit condition met.** The final clean-device
keyring/signed-APT/package/audit/compiler test against the rebuilt
`userland-v2-dev` assets passed (see above and
[`chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`](chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md) §8). PR #14 is
ready to merge as "Phase 3 complete" pending final owner review.

---

## Phase 4 — polish and expansion

Phase 4 planning and tracking lives in [`PHASE4_ROADMAP.md`](PHASE4_ROADMAP.md) and [`docs/chat-phase4/`](chat-phase4/README.md).

- **Part 4.1 — Shared-storage access (`~/storage`)** ✅ **DONE (device-verified 2026-08-24).**
  Detailed record in [`docs/chat-phase4/PART_4_1_STORAGE.md`](chat-phase4/PART_4_1_STORAGE.md).
- **Part 4.2 — Package-install confirmation UX** ✅ **DONE (verified 2026-08-24).**
  Detailed record in [`docs/chat-phase4/PART_4_2_INSTALL_CONFIRMATION.md`](chat-phase4/PART_4_2_INSTALL_CONFIRMATION.md).
- **Part 4.3 — Trust/channel indicator UX** ✅ **DONE (verified 2026-08-24).**
  Detailed record in [`docs/chat-phase4/PART_4_3_TRUST_CHANNEL_UX.md`](chat-phase4/PART_4_3_TRUST_CHANNEL_UX.md).
- **Part 4.4 — Terminal/editor settings parity** ✅ **DONE (device-verified 2026-08-24).**
  Detailed record in [`docs/chat-phase4/PART_4_4_SETTINGS_PARITY.md`](chat-phase4/PART_4_4_SETTINGS_PARITY.md).
- **Part 4.5 — Expanded package catalog (round 2, CI build)** ✅ **DONE (CI verified 2026-08-25).**
  Detailed record in [`docs/chat-phase4/PART_4_5_CATALOG_EXPANSION.md`](chat-phase4/PART_4_5_CATALOG_EXPANSION.md).
  Workflow run [`32845127723`](https://github.com/pabi277/CodeC/actions/runs/32845127723) (1h 53m 36s)
  built both architectures (`aarch64`, `x86_64`) green with 25 curated package roots, zero maintainer script violations, and byte-identical bootstrap archives.
- **Part 4.6 — Expanded package catalog (round 2, publish & device gate)** ✅ **DONE (device-verified 2026-08-25).**
  Detailed record in [`docs/chat-phase4/PART_4_6_CATALOG_ACCEPTANCE.md`](chat-phase4/PART_4_6_CATALOG_ACCEPTANCE.md).
  Published via run [`32858460740`](https://github.com/pabi277/CodeC/actions/runs/32858460740) and verified `pkg install` + execution of all 15 new roots on real arm64 hardware.
- **Part 4.7 — Android integration slice** ✅ **DONE (device-verified 2026-08-26)** — clipboard
  over the reusable `CodeCApi` bridge; CI + all primary device checks green incl. the piped/
  redirected channel fix (`/dev/tty`); optional negatives waived; dispatch moved to activity
  scope. 4.8+ ready. See
  [`chat-phase4/PART_4_7_ANDROID_INTEGRATION.md`](chat-phase4/PART_4_7_ANDROID_INTEGRATION.md).
- **Part 4.8 — Android notifications slice** ✅ **DONE (device-verified 2026-08-26)** — `codec-notify
  send|clear|status` over the 4.7 bridge, deliberately exercising the `POST_NOTIFICATIONS`
  runtime-permission path (channel creation, `NEED_PERMISSION` marker, activity launcher,
  atomic resume). Host `sh` harness green; CI green; device-verified end to end incl. the
  permission dialog → allow → `OK` flow, no re-prompt on later sends, and owner-confirmed
  notification tap → CodeC opens. Two device-driven fixes during acceptance (hint once + 30 s
  wait; onResume recovery for the system-owned dialog). **Phase 4 complete.** See
  [`chat-phase4/PART_4_8_ANDROID_NOTIFICATIONS.md`](chat-phase4/PART_4_8_ANDROID_NOTIFICATIONS.md).
- **Post-4.5/4.6 review** ✅ **DONE (device-verified 2026-08-26).** Recipe-override
  hardening, fully artifact-neutral (no rebuild/re-publish needed) — plus the
  `pkg heal` alternatives-DB self-repair, the `plan-bootstrap.py` slave-placeholder
  fix (future bootstrap archives), and the pinned shared CI debug key
  (`debug.keystore`) that stops wipe-on-update between CI builds (proven:
  pinned-cert APK updated in place, 82 packages and the userland intact).
  Record in
  [`docs/chat-phase4/PART_4_5_4_6_POST_IMPLEMENTATION_REVIEW.md`](chat-phase4/PART_4_5_4_6_POST_IMPLEMENTATION_REVIEW.md) —
  including two non-blocking known issues for a future client PR:
  **KI-1** `pkg install` reports failure when the target is already the
  newest version (treat apt's "0 newly installed" as success), and
  **KI-2** device `$PREFIX` (`/data/user/0/...`) vs the dpkg-recorded
  `/data/data/...` spelling confuse manual `update-alternatives` calls
  (canonicalize `PREFIX` at shell setup after a full regression pass).

---

## Ordering summary

| Part | Depends on | Effort / state (2026-08-25) |
|---|---|---|
| Phase 3 Part A — republish clean bootstrap | — | ✅ **DONE** (in-place repair, no rebuild, device-verified) |
| Phase 3 Part B — bootstrap correctness | A | ✅ **DONE** — merged, rebuilt, republished, device-verified |
| Phase 3 Part C — clean-device acceptance | A ✅, B ✅ | ✅ **DONE** — every checklist item passed on real arm64 devices |
| Phase 3 Part D — M3 signing | A ✅, B ✅, C ✅ | ✅ **DONE** — implementation, signed publish, rebuild, and final clean-device gate all passed |
| Phase 4 Part 4.1 — shared-storage access | none | ✅ **DONE** — `codec-setup-storage`, OSC 1337, device-verified |
| Phase 4 Part 4.2 — install confirmation UX | none | ✅ **DONE** — transaction summary, `[Y/n]` prompt, `-y` flag |
| Phase 4 Part 4.3 — trust/channel indicator UX | none | ✅ **DONE** — Settings trust card, `pkg status`, repo probe |
| Phase 4 Part 4.4 — settings/theme parity | none | ✅ **DONE** — font family, themes, settings UI, live preview |
| Phase 4 Part 4.5 — expanded package build (CI) | none | ✅ **DONE** — run `32845127723` green (25 roots, aarch64 + x86_64) |
| Phase 4 Part 4.6 — expanded package publish + device accept | 4.5 | ✅ **DONE** — published run `32858460740` & device-verified |
| Phase 4 Part 4.7 | 4.6 on 4.5 | ✅ **DONE** (device-verified 2026-08-26) — `codec-clipboard` over `CodeCApi` OSC bridge |
| Phase 4 Part 4.8 | 4.7 | ✅ **DONE** (device-verified 2026-08-26) — `codec-notify` over the same bridge, `POST_NOTIFICATIONS` runtime path device-verified (dialog → allow → OK; tap opens CodeC) |
