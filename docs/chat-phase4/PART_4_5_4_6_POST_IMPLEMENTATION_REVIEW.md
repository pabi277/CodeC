# Post-implementation review — Parts 4.5 & 4.6 recipe-override hardening

**Status:** ✅ DONE (host-verified 2026-08-25). Follow-up record for the
code review of PR #20 (Parts 4.5/4.6). All fixes are **provably
artifact-neutral**: none of them changes the effective behavior of the
published build ([`32845127723`](https://github.com/pabi277/CodeC/actions/runs/32845127723))
or the published repository ([`32858460740`](https://github.com/pabi277/CodeC/actions/runs/32858460740)),
so **no rebuild, re-publish, or device re-acceptance was required**. Fixes
land as a follow-up PR on top of PR #20's merge.

---

## 1. Why a review was needed

Part 4.5/4.6 shipped working code (green CI build, green publish, green
device acceptance), but its patches to the pinned termux-packages build
system injected guard blocks whose *intended* semantics differed from their
*actual* runtime semantics. The gap was invisible to the existing host
tests because they asserted text presence rather than runtime behavior.

## 2. Findings (evidence-verified against the pinned tree)

All findings were reproduced against termux-packages @
`1bbe66903526df2e8af51e704316bc68ede72603` (the pin in
`codec-packages/properties.codec.sh`), running the real
`apply-recipe-overrides.sh` and inspecting the patched upstream scripts.

### F1 — The maintainer-script "whitelist" never evaluated the package name

`build-package.sh` sources `scripts/build/termux_step_create_debscripts.sh`
(line 402) and `scripts/build/termux_step_create_python_debscripts.sh`
(line 406) at top level, **before any recipe is parsed** — so
`TERMUX_PKG_NAME` is always unset when the injected guards ran
(`case "${TERMUX_PKG_NAME:-}" in ... *) stub ;; esac` always fell into the
`*` branch). Effect:

- `termux_step_create_python_debscripts()` became a no-op **for every
  package**, not just non-whitelisted ones (benign: no whitelisted package
  ships Python files, and the CodeC userland intentionally ships no Python
  interpreter at all).
- `termux_step_create_debscripts()` became a no-op (already a no-op
  upstream at the pin) and — via the early `return` — the helper
  `termux_step_create_debscripts__copy_from_dir` was **never defined**
  (benign: no callers at the pin; fragile under pin bumps).

The published build therefore behaved *better* than its documentation
claimed (nothing was generated for anyone), and CI/device stayed green.

### F2 — The massage-layer "purge maintainer scripts" block was dead code

The injected `termux_step_massage.sh` block guarded on
`TERMUX_PKG_MASSAGEDDIR` / `SUBPKG_MASSAGEDDIR`, **variables that do not
exist at the pin** (upstream: `TERMUX_PKG_MASSAGEDIR`; the subpackage flow
uses the local `SUB_PKG_MASSAGE_DIR`). Its relative `rm -f DEBIAN/...`
also targeted a directory that does not exist during massage (DEBIAN/ is
created later by `termux_step_create_debian_package`). The block could
never remove anything, ever.

### F3 — The `rxvt-unicode` override could never fire

The recipe is **absent from `packages/` and from `x11-packages/`** at the
pinned revision (verified by full-tree search; only an unrelated `aterm`
patch mentions rxvt). The override always took the "recipe not found"
skip path, and its host test used a synthetic fixture so it could not
notice the drift to non-existence.

### F4 — The `xcb-proto` debscripts append stubbed an already-no-op function

The unapproved xcb-proto postinst that motivated it actually came from
`termux_step_create_python_debscripts` (auto py3compile scripts), already
disabled by F1. The appended `termux_step_create_debscripts() { :; }` was
inert decoration.

### F5 — Tests asserted presence, not semantics

The original tests grepped for the guard text, so F1–F4 passed review
undetected.

### Minor

- **M1** — The client `pkg` validator (`ShellEnvironment.kt`) hardcoded
  `coreutils` in two maintainer-script error strings for every package.
- **M2** — `BOOTSTRAP_VERSION` 21→22 only stamps `.bootstrap-v22`; nothing
  reads that marker, and userland updates gate on `.userland-release`,
  so the bump is informational (no reinstall is forced — good, but the
  Part 4.6 doc implied otherwise).
- **M3** — Docs disagreed on Part 4.7's status ("Planned" in NEXT_STEPS
  vs "IN PROGRESS / READY FOR PICKUP" elsewhere).

## 3. Fixes applied in the follow-up PR

`codec-packages/scripts/apply-recipe-overrides.sh`:

1. **F1** — Replaced both source-time guards with **unconditional
   end-of-file stubs** (`termux_step_create_debscripts() { :; }` and
   `termux_step_create_python_debscripts() { :; }`). They win by bash's
   last-definition rule while the rest of each file stays sourced (helpers
   keep being defined; no `TERMUX_PKG_NAME` dependence; no `return`/`|| true`
   trickery — sourcing is clean under `set -u`). Both stubs fail loudly on
   pinned-revision drift if upstream restructures the files. This matches
   CodeC policy exactly: maintainer scripts are forbidden for *every*
   package; the only approved ones are the update-alternatives pairs from
   `termux_step_create_alternatives.sh` (a separate, unpatched step) for
   the five reviewed packages.
2. **F2** — Removed the dead massage purge block (kept the real,
   verified symlink conversion, still correctly placed **before**
   `termux_create_debian_subpackages`).
3. **F3** — Removed the rxvt-unicode override; left its flake→override
   playbook as a comment.
4. **F4** — Removed the inert xcb-proto append.
5. **F5** — Tests now assert *runtime semantics*: each patched step file
   is sourced in a `bash` subprocess mimicking `build-package.sh`
   (`set -u`, `TERMUX_PKG_NAME` unset) and `declare -f` must show the stub
   as the effective definition (helpers stay defined, the function is
   callable, no source-time `case` text exists). Added a fail-loud drift
   test. The massage test now asserts the dead purge identifiers stay
   out; the xcb-proto test asserts no per-recipe debscripts stub returns.
6. **M1** — Error strings now use `${package_name}`.
7. **M3** — NEXT_STEPS Part 4.7 status aligned.

`ShellEnvironmentTest.kt` expectations unchanged (the pkg-script content
assertion still passes; error-path wording only).

## 4. Artifact-neutrality argument (why no rebuild was needed)

- Published-build effective definitions: both debscript functions were no-op
  stubs for **all** packages (F1's guard always took the stub branch).
  Post-fix effective definitions: **identical** no-op stubs — verified by
  sourcing the re-patched pinned files and diffing the `declare -f`
  output.
- The massage purge block never executed (F2), so removing it changes no
  build output. The symlink conversion — the part that did execute — is
  byte-for-byte preserved.
- The rxvt-unicode (F3) and xcb-proto (F4) overrides never mutated any
  file at the pin, so removing them changes no build output.
- Kotlin error-string changes (M1) affect only failure-path messages in
  the `pkg` preflight validator, not accepted content.

Conclusion: a re-run of the Part 4.5 build would produce the same .deb
set; the published repository and the device-acceptance evidence in
[`PART_4_6_CATALOG_ACCEPTANCE.md`](PART_4_6_CATALOG_ACCEPTANCE.md) remain
valid.

## 5. Verified before opening the PR

- Full host suite green: `python3 -m unittest discover -s codec-packages/tests -p 'test_*.py' -v` (81 tests, 4 pre-existing skips).
- Real-tree replay: fresh clone @ `1bbe6690…`, `apply-recipe-overrides.sh`
  exits 0; all patched files pass `bash -n`; effective stub definitions
  verified via `declare -f` under `set -u` with `TERMUX_PKG_NAME` unset;
  idempotent sections confirmed single-applied.
- Known pre-existing (unchanged by this PR): the apt sources.list rewrite
  is not idempotent — a *second* run of the overrides on the same tree
  fails loudly at the apt check. CI always applies overrides once on a
  fresh clone, so this is safe; noted here for future reference.

## 6. Device-acceptance incident follow-up (2026-08-25)

During re-verification of the latest APK on a long-lived device, `bat`'s
postinst failed:

```
update-alternatives: error: .../var/lib/dpkg/alternatives/pager corrupt:
unexpected end of file while trying to read master file
```

**Definitive root cause (byte-level reproduced against live dpkg):** the
device was later observed reset to the fresh `userland-v2-dev` seed (app
reinstall/data reset between sessions), and the **freshly seeded** `pager`
admin file was again unparseable — so the defect is not an interrupted write,
it is **built into the published bootstrap archive**. `plan-bootstrap.py`'s
admin-file writer omitted the per-record slave placeholder lines dpkg
requires from every alternative record: for a member that declares no slave
in a group that has slaves (`busybox/less` in the `pager` group), dpkg's
parser consumes the file's terminator blank as the missing slave line and
then dies at EOF — reproduced byte-for-byte on the build host with live
`update-alternatives`. Every fresh device carried the poison; the first
package whose postinst touched the `pager` group (round 2's `bat`) failed
to configure.

**Fixes:**

1. `plan-bootstrap.py` now emits per-record slave placeholders (empty line
   per undeclared group slave) plus the terminator blank — output
   byte-matches live dpkg's own writer; golden test updated and a new
   live-parse test runs the generated file through real
   `update-alternatives --display` (with a graceful skip where dpkg is
   absent). This corrects **future** bootstrap archives.
2. The already-published `userland-v2-dev` archive stays byte-identical as
   required; on device the new **`pkg heal` / `heal_alternatives_db`**
   client layer mitigates the poisoned seed automatically (proven
   on-device 2026-08-25: the healer quarantined the freshly seeded file,
   the install proceeded, and the group re-registered).

**Recovery on today's already-seeded devices (proven on-device):** quarantine
or delete the unparseable admin file (healer does this), then reinstall the
affected packages (`pkg uninstall -y less && pkg install -y less`) so their
postinsts re-register the group.

**Two red herrings ruled out as defects during the same verification:**

- `readlink bin/bzcmp` → `../bin/bzdiff` (also `bzless`): these come from the
  **Phase 3 bootstrap assembler's relativizer**
  (`codec-packages/scripts/assemble-bootstrap.sh`), which rewrites absolute
  in-prefix targets as `"../"×depth + inner` (depth 1 for `bin/`). The links
  are prefix-relative and resolve correctly; the bootstrap remains the
  byte-identical, already-accepted `userland-v2-dev`.
- `termux-setup-storage` in `$PREFIX/bin` is CodeC's own compatibility shim
  written by the app bootstrap (`ShellEnvironment.prepare`), not com.termux
  contamination.

**Fix shipped in this PR (client-side only, no repository rebuild):**
`heal_alternatives_db` in the `pkg` script scans the alternatives admin
directory before every dpkg-touching operation (`install`, `upgrade`,
`repair`) and quarantines any file `update-alternatives` can no longer parse
to `<name>.corrupt-<epoch>`; the next postinst re-registers the group — the
exact recovery proven manually above. Also exposed as `pkg heal` and covered
by a Kotlin execution test that runs the real generated script against a
stubbed prefix.

**Device state note:** `nano` and `gawk` were absent on this device only —
accepted device state, not a repository regression (both remain in the
published catalog and were part of the original clean-device acceptance).

### 6.1 Why the device kept being wiped between sessions

The acceptance transcripts showed a second, independent symptom that
masqueraded as a repository/userland regression: **every newly CI-built APK
demanded an uninstall before install, wiping the whole app sandbox** — the
userland prefix, dpkg DB, and every round-1/round-2 package installed on
device (`git`, `wget`, `bat`, `ripgrep`, …) vanished between sessions, even
though `UserlandInstaller.installIfNeeded()` correctly skips reinstall when
`.userland-release` matches (no app-side wipe happened). Evidence: the dpkg
status count was the identical fresh-seed number at both session starts
(`3765 files and directories currently installed`), and only the seeded
closure (`busybox, bash, apt, dpkg, coreutils, less, curl` + `Essential`
deps) survived each time.

**Root cause (repo-proven):** `app/build.gradle.kts` only applied the
`debugConfig` signing config when `${rootDir}/debug.keystore` existed, and
`debug.keystore` was listed in `.gitignore`, so it never existed in CI. Each
CI runner therefore fell back to AGP's per-runner **ephemeral**
`~/.android/debug.keystore` → every CI-built `CodeC-IDE` artifact carried a
**different signing certificate** → the OS refused in-place updates
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE` / uninstall-prompt), and the required
uninstall deletes the app data directory.

**Fix (this PR):** a pinned, shared **debug** key is committed at the repo
root (`debug.keystore`, OpenSSL-generated PKCS12, standard Android debug DN
`C=US, O=Android, CN=Android Debug`, alias `androiddebugkey`, 30-year
validity, AES-256 PBE / SHA-256 MAC per OpenSSL 3 defaults — readable by
SunJCE PKCS12 on JDK 11+, CI uses Temurin 17), `.gitignore` gains
`!/debug.keystore`, and `debugConfig` now declares `storeType = "PKCS12"`.
Every CI and local debug build now signs with the same certificate, so
sideloading the next build installs **in place** and preserves app data.
Security note: a committed debug key means anyone can sign a *debuggable*
APK with this identity — acceptable for the dev channel; the release path
(`release` signingConfig from `KEYSTORE_PATH`/`my-upload-key.jks`, never
committed) is unchanged and unaffected.

**Expected one-time fallout:** the first APK signed with the new pinned key
still differs from the ephemeral cert of the previous build, so **one final
uninstall/wipe** is required when installing it; all subsequent CI builds
update in place.

## 7. What was verified as correct (no action)

- xorg download-host sweep + fail-loud util-macros check.
- libbz2 `termux_step_post_make_install` layering (no clobbering: the
  recipe's own fixes live in `termux_step_make_install`).
- bat/util-linux alternatives specs identical across the pinned recipes,
  the client validator, and `repository_lib.py` (priorities 10/25 keep
  `less` as default pager).
