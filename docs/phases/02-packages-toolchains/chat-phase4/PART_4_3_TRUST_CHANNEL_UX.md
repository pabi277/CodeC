# Phase 4 Part 4.3 — Trust & Channel Indicator UX

**Status: COMPLETE and verified (2026-08-24).**

---

## 1. Goal & Architecture

Provide user-facing trust and channel indicators in both the graphical Settings UI and the terminal CLI (`pkg status`), making repository cryptographic authenticity (OpenPGP signing subkey, active keyring status, fail-closed verification) and channel configuration transparent and checkable at a glance.

### Key Features:

1. **Settings UI — Package Repository & Trust Card:**
   - Visual Trust Badge: Displays `CodeC Official Signed Channel` with `Icons.Default.CheckCircle` (green/primary).
   - Metadata Breakdown:
     - **Channel:** `Development (stable/main)`
     - **Repository:** `https://pabi277.github.io/CodeC/dev`
     - **Trust Model:** `OpenPGP gpgv (Fail-Closed, signed-by=)`
     - **Keyring:** `codec-archive-keyring-v1.gpg` (with installed byte size)
     - **Signing Subkey:** `32850086...F8135015` (and primary `3185B4D219C5EF30B263F5E50A458891ED0FB8D3`)
     - **Userland Tier:** `Phase 3 (Installed, <arch>)`
   - Connectivity Probe: `CHECK REPOSITORY` action button verifies online connectivity to `https://pabi277.github.io/CodeC/dev/dists/stable/InRelease`.

2. **Terminal CLI — `pkg status` Subcommand:**
   - Terminal command: `pkg status` (with `pkg trust` and `pkg channel` aliases).
   - Formatted output:
     ```text
     CodeC Package Repository & Trust Status:
       Channel:             stable/main (Development)
       Repository URL:      https://pabi277.github.io/CodeC/dev
       Keyring File:        $PREFIX/etc/apt/keyrings/codec-archive-keyring-v1.gpg
       Keyring Status:      Installed & Active (2213 bytes)
       Trust Model:         OpenPGP gpgv (Fail-Closed, signed-by=)
       Signing Subkey:      328500868CE9B0F74B62CEFC1D7D52F6F8135015
       Primary Fingerprint: 3185B4D219C5EF30B263F5E50A458891ED0FB8D3
       Architecture:        aarch64
       Prefix:              /data/data/com.codeci.ide/files/usr
       Cached Index:        Origin: CodeC, Suite: stable
     ```

3. **Friendly Unindexed Guidance:**
   - If a package is not found (e.g. before `pkg update` is run on a clean install), `friendly_apt` now provides actionable advice:
     `pkg: package not found; run 'pkg update' first to refresh the package catalog.`

4. **In-App Script Auto-Distribution:**
   - Bumped `ShellEnvironment.BOOTSTRAP_VERSION` to `"21"`.

---

## 2. Test Verification

| Test Suite | Coverage | Status |
|---|---|---|
| `test_pkg_confirmation.py` | `pkg status`, `pkg trust`, `pkg channel` CLI output & key verification | ✅ PASS |
| `test_pkg_confirmation.py` | `friendly_apt` unindexed package lookup hint (`run 'pkg update' first`) | ✅ PASS |
| `ShellEnvironmentTest.kt` | `getRepositoryTrustInfo` Kotlin metadata inspection | ✅ PASS |
| `ShellEnvironmentTest.kt` | `pkg status` process execution test | ✅ PASS |
| Host test suite | 73/73 tests green across `codec-packages/tests` | ✅ PASS |

---

## 3. Invariants Maintained
- No `.` on `$PATH`.
- Real ELF `bash` and embedded musl `cc` compiler untouched.
- Musl TCC link order (`crt1.o ... crtn.o -o`) preserved.
- Package repository signing (`signed-by=`, `gpgv`) and script security preflight preserved.
