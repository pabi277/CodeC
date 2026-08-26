# Phase 5 Part 5.1 — Client fixes KI-1 & KI-2

**Status: 🚧 IN PROGRESS (code + host tests written, awaiting CI + device
verification).** These are the two known, non-blocking client issues recorded
at the end of the Phase 4.5/4.6 post-implementation review
([`../chat-phase4/PART_4_5_4_6_POST_IMPLEMENTATION_REVIEW.md`](../chat-phase4/PART_4_5_4_6_POST_IMPLEMENTATION_REVIEW.md)
§6.2 "Known issues recorded for a future client PR"). Both are **client-side
only** — no repository rebuild, no re-publish, no bootstrap archive change —
so this part is inexpensive and does not trigger the ~60–100 minute package
workflow.

---

## 1. Decision D1 — which candidate area, and the two fix directions

The owner chose the **known client fixes (KI-1, KI-2)** candidate from
[`../PHASE5_ROADMAP.md`](../PHASE5_ROADMAP.md). Scope is exactly these two
issues; no new capability, no catalog/GUI/root work in this part.

| Fix | Symptom (from the 4.5/4.6 review) | Decision |
|---|---|---|
| **KI-1** | `pkg install` of an already-installed package reports **failure**: apt exits 0 with `make is already the newest version (4.4.1-1)`, but the wrapper treats "0 newly installed / no packages downloaded" as a fetch failure (`pkg: apt downloaded no packages; run pkg update and retry`) and exits nonzero. Hostile to scripts and idempotent re-runs. | In `pkg`'s `install_specs()`, detect the **zero-`.deb`-downloaded** case right after the `--download-only` step and `return 0` with a friendly message — the exact idiom `upgrade_packages()` already uses for "all packages are up to date". |
| **KI-2** | On-device `$PREFIX` is `/data/user/0/com.codeci.ide/files/usr` (from `Context.getFilesDir()`), while every deb, the dpkg DB, and the alternatives admin files record `/data/data/com.codeci.ide/files/usr`. A manual `update-alternatives --install` mixing the two spellings fails with `mv: ... are the same file` / `Cross-device link` (exit 2); greps over `--display` mismatch; and `readlink -f` does **not** collapse `/data/user/0` on the Samsung test device (not a symlink), so runtime canonicalization-by-symlink-resolution is unreliable. | Canonicalize **only the `$PREFIX` value exported into the shell** (`buildEnv` + `profileScript`) to the dpkg-recorded `/data/data/…` spelling. `prefixDir()` — used by the bootstrap installer for real filesystem operations — is left untouched. Termux hardcodes its `/data/data` prefix the same way. |

### Why KI-2 is done in Kotlin (not only the `pkg` script)

The `pkg` script already computes a `CANON_PREFIX` for its *own* byte checks,
so `pkg` was never broken. The KI-2 surface is the **shell environment**: the
`$PREFIX` a user references in a manual `update-alternatives --install
"$PREFIX/bin/editor" …` must be the same `/data/data/…` spelling the dpkg DB
and alternatives admin files record, or dpkg attempts a link-rename through
the emulation and fails with `Cross-device link`.

### Regression 1 found and reverted (2026-08-26) — repeated bootstrap downloads

The first attempt applied the canonicalization at `prefixDir()`, the single
choke point the **bootstrap installer** (`UserlandInstaller.installIfNeeded` /
`installRelease` / `hasRunnableUserland`) also uses for real filesystem
operations (download, extract, `swapPrefix` rename, the ELF launch probe).
The owner reported that this build downloaded the bootstrap **repeatedly,
automatically** — consistent with `hasRunnableUserland(prefix)` no longer
seeing a runnable userland at the rewritten `/data/data/…` path and thus
re-downloading on every start. Fix: canonicalize **only the exported
`$PREFIX`** (`buildEnv` + `profileScript`); `prefixDir()` and every
installer path are back to the exact Phase-4 form. A regression test now
asserts `prefixDir()` returns the raw `/data/user/0/…` spelling so this
cannot recur silently. Device-verified fixed: the owner saw a single
`userland: already installed (userland-v2-dev)` and no download loop.

### Regression 2 found and fixed (2026-08-26) — CodeCApi bridge confinement rejected every request

With the exported `$PREFIX` now `/data/data/…`, the clipboard/notify CLI
scripts emit `/data/data/…/tmp/codec-api/…` request/response paths, while
the app still computes the bridge's `apiDir` from the raw
`Context.getFilesDir()` spelling (`/data/user/0/…`). `isConfinedDirectChild`
compared the two after `File.canonicalFile()` — but on devices where
`/data/data` is a **bind mount** (not a symlink), realpath does not collapse
the alias, so every request was rejected and the owner saw
`codec-clipboard: no response from CodeC`. Fix: `CodecApiProtocol` now
normalizes the `/data/user/0/` alias to `/data/data/`
(`canonicalUserPrefix`) **before** resolving, so the confinement check is
spelling-invariant; `ShellEnvironment.canonicalPrefix` delegates to the same
helper (single source of truth).

## 2. Implementation map

- `ShellEnvironment.kt`
  - New `canonicalPrefix(path: String)`: delegates to
    `CodecApiProtocol.canonicalUserPrefix` (single source of truth).
  - `buildEnv()`: canonicalizes the `prefix` File used to build the exported
    `PREFIX`, `PATH`, `TMPDIR`, and `ENV` entries (so the whole exported
    environment is spelled `/data/data/…`).
  - `profileScript()`: canonicalizes the `export PREFIX='…'` line (everything
    else derives from `$PREFIX` at runtime).
  - `prefixDir()`: **unchanged** (returns `File(filesDir, "usr")`).
  - `pkgScript()` → `install_specs()`: after the `--download-only` step
    succeeds, scan `$CACHE/*.deb`; if zero were downloaded, `rm -f` the
    pending marker, print `pkg: <names> already installed (already the newest
    version).`, and `return 0`.
  - `BOOTSTRAP_VERSION` 24 → **25** (informational only — `prepare()` rewrites
    all `$PREFIX/bin` scripts unconditionally; nothing gates on the marker,
    same as M2 from the 4.5/4.6 review).
- `CodecApiProtocol.kt`
  - New `canonicalUserPrefix(path)` (android-free): maps `/data/user/0/…` →
    `/data/data/…`, no-op otherwise.
  - `isConfinedDirectChild()`: normalizes both `apiDir` and the candidate
    path with `canonicalUserPrefix` **before** `canonicalFile`, so the bridge
    stays correct whether or not realpath collapses the bind-mounted alias.
- `ShellEnvironmentTest.kt` — new host tests:
  - `pkg install of an already-newest package succeeds (KI-1)` — full
    execution test with mocked apt-get/dpkg/gpgv/curl where `--download-only`
    succeeds but writes no `.deb`; asserts exit 0, the success message, the
    absence of "apt downloaded no packages", and no lingering transaction
    marker.
  - `canonicalPrefix only rewrites the user 0 emulation alias`.
  - `KI-2 exports the canonical prefix without changing the installer path`
    — asserts `prefixDir()`/`codecApiDir()` stay raw (regression guard),
    while `buildEnv["PREFIX"]`/`PATH`/`TMPDIR` and the sourced profile export
    `/data/data/…`.

## 3. Invariants (none weakened — checked)

- No `.` on `PATH`; TCC `-o` order untouched; no `com.termux` repos/binaries;
  repository metadata stays signed (`signed-by=`, no `trusted=yes`).
- The `pkg` maintainer-script allowlist and prefix-confined `.deb` preflight
  are unchanged (KI-1 only changes the *zero-download* success path, before
  `preflight_cache` runs — a package that actually downloads a `.deb` still
  goes through the full preflight).
- No bootstrap bundled in the APK; no repository rebuild triggered.

## 4. Exit condition

On a real device with the new APK:

0. **No bootstrap re-download (regression guard):** on a device that already
   has `userland-v2-dev` installed, opening the app (and switching to the
   Terminal tab) reports `userland: already installed (userland-v2-dev)` and
   does **not** download the bootstrap again — the repeated-download
   regression is gone.
1. **KI-1:** `pkg install <already-newest-package>` (e.g. a package already
   installed at its newest version) prints the "already installed (already
   the newest version)" message and **exits 0**; `echo $?` is `0`; a
   scripted/idempotent re-run does not fail.
2. **KI-2:** `echo $PREFIX` in a fresh CodeC terminal reports
   `/data/data/com.codeci.ide/files/usr`; a manual
   `update-alternatives --install` using `$PREFIX` no longer hits
   `Cross-device link`; and `codec-clipboard` / `codec-notify` still work
   end to end (the bridge confinement still matches the CLI's `$PREFIX`).

### Device verification recipe (for the owner — exact copy-paste)

```sh
# 0) no re-download: with userland already installed, open app → Terminal
#    expect a single "userland: already installed (userland-v2-dev)", and NO
#    repeated "userland: downloading … %" lines.

# 1) KI-1 — run pkg update first (fresh devices have empty apt lists), then
#    install an ALREADY-INSTALLED seeded package so apt downloads nothing:
pkg update
pkg install -y less; echo "exit=$?"
#    expect: "pkg: less already installed (already the newest version)."
#            exit=0   (was: failure + nonzero before this fix)
#    (a second `pkg install -y <pkg>` right after installing it also works.)

# 2) KI-2 — PREFIX spelling is the dpkg-recorded /data/data/ form
echo "$PREFIX"
#    expect: /data/data/com.codeci.ide/files/usr   (was: /data/user/0/...)

# 3) KI-2 — manual update-alternatives no longer cross-device-fails
#    (use a seeded binary that exists, e.g. less; nano may not be installed)
update-alternatives --install "$PREFIX/bin/editor" editor "$PREFIX/bin/less" 50
update-alternatives --remove editor "$PREFIX/bin/less"
#    expect: clean, no "Cross-device link" / "are the same file"

# 4) regression — the CodeCApi bridge still works with the new PREFIX
codec-clipboard set "hello" && codec-clipboard get   # expect: hello
codec-notify status                                    # expect: status lines
```

## 5. Evidence

### 5.1 Host (this session)

Code and host tests written; **not yet executed** — this agent sandbox has no
JDK/Android SDK/Gradle (documented sandbox limit), so unit tests can only be
run by CI. The new tests were written to compile and run in the existing
`ShellEnvironmentTest` suite (JUnit4, `sh` + mocked backend binaries on a temp
prefix, matching the existing `pkg install`/`heal`/`confirm` execution tests).

### 5.2 CI (run [`32932276532`](https://github.com/pabi277/CodeC/actions/runs/32932276532), green — 2026-08-26)

Pushing to the session branch triggered the legacy "Build APK" workflow,
which the `gradle-bootstrap` bridge expands to
`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. **Green in
2m40s** — the three new host tests (`canonicalPrefix`, `KI-2 prefix
canonicalizes…`, `pkg install of an already-newest package succeeds (KI-1)`)
compiled and passed, and lint was clean. The only annotations are unrelated
runner deprecation notices (Node.js 20 / setup-java v4).

### 5.3 Device (pending)

The §4 recipe above; to be filled in with the owner's transcript.

## 6. Not done / out of scope this part

- No `BOOTSTRAP_VERSION`-gated reinstall (the marker is informational).
- No repository rebuild, re-publish, or bootstrap archive change.
- The deferred GUI/catalog/root and further `CodeCApi` capabilities stay
  untouched.
