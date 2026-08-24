# Phase 4 Part 4.2 — Package-Install Confirmation UX

**Status: COMPLETE and verified (2026-08-24).**

---

## 1. Goal & Architecture

Before `pkg install`, `pkg upgrade`, or `pkg uninstall` mutates the CodeC userland or dpkg database, CodeC presents a structured in-terminal transaction summary showing what will be downloaded, installed, upgraded, or removed, including package names, versions, archive sizes, estimated installed disk footprint, and preflight security verification status.

The user is prompted with an interactive `[Y/n]` confirmation prompt before any package extraction or dpkg execution begins.

### Key Features:
1. **User-Visible Security Preflight:**
   - Background preflight checks (repository signature verification, ABI matching, CodeC prefix confinement, and strict maintainer-script allowlist) are now reported in the transaction summary:
     `Preflight: PASSED (signed repo, verified ABI, prefix-confined, script allowlist)`
2. **Transaction Summary Breakdown:**
   ```
   CodeC Package Manager — Transaction Summary:
     Operation:        Install
     Packages (2):
       • nano 9.2 (download: 238 KB, installed: ~840 KB)
       • libmagic 5.45 (download: 182 KB, installed: ~1.2 MB)
     Preflight:        PASSED (signed repo, verified ABI, prefix-confined, script allowlist)
     Total download:   420 KB
     Space change:     ~2.0 MB

   Do you want to continue? [Y/n]
   ```
3. **Interactive & Default Behavior:**
   - Pressing `<Enter>` or typing `y` / `Y` / `yes` accepts the transaction and proceeds with installation/upgrade/removal.
   - Typing `n` / `N` / `no` aborts the transaction cleanly: downloaded cache is cleaned, pending transaction markers are cleared, the dpkg database and system files remain completely untouched, and `pkg` exits cleanly with code 0.
4. **Scripted & Non-Interactive Invocations (`-y` / `--yes`):**
   - Supports `-y`, `--yes`, `--assume-yes` flags placed either before or after subcommands:
     - `pkg install -y <pkg>` / `pkg -y install <pkg>`
     - `pkg upgrade -y` / `pkg -y upgrade`
     - `pkg uninstall -y <pkg>` / `pkg -y uninstall <pkg>`
   - If stdin is closed/EOF without `-y` in a non-interactive environment, `pkg` fails closed to prevent accidental unattended modifications:
     `pkg: standard input is not a terminal and -y was not specified`
5. **Base Package Protection:**
   - Attempting to uninstall core packages (`bash`, `busybox`, `apt`, `dpkg`, `codec-pkg`) is rejected immediately before transaction preparation.
6. **Automatic In-App Script Distribution:**
   - `ShellEnvironment.BOOTSTRAP_VERSION` incremented to `"20"`. On next app launch, CodeC automatically writes the updated `pkg` script to `$PREFIX/bin/pkg` in existing userlands without requiring userland re-download.

---

## 2. Test Verification

| Test Suite | Coverage | Status |
|---|---|---|
| `test_pkg_confirmation.py` | `help` text & `-y`/`-h` flags, offline execution | ✅ PASS |
| `test_pkg_confirmation.py` | `pkg install -y` & `pkg -y install` prompt bypass | ✅ PASS |
| `test_pkg_confirmation.py` | `pkg install` interactive acceptance (`y` / `<Enter>`) | ✅ PASS |
| `test_pkg_confirmation.py` | `pkg install` interactive abort (`n`), marker cleanup, zero mutation | ✅ PASS |
| `test_pkg_confirmation.py` | `pkg install` multiple packages, KB/MB size formatting | ✅ PASS |
| `test_pkg_confirmation.py` | `pkg install` non-interactive stdin closed failure | ✅ PASS |
| `test_pkg_confirmation.py` | `pkg upgrade` up-to-date detection & interactive prompt/abort | ✅ PASS |
| `test_pkg_confirmation.py` | `pkg uninstall` confirmation prompt, `-y` flag, & base-package refusal | ✅ PASS |
| `test_pkg_confirmation.py` | Unknown command & invalid option handling | ✅ PASS |
| `ShellEnvironmentTest.kt` | Kotlin unit test for prompt structure & preflight summary strings | ✅ PASS |
| `ShellEnvironmentTest.kt` | Kotlin process test for `-y` acceptance and `n` abort handling | ✅ PASS |
| Host test suite | 71/71 tests green across `codec-packages/tests` | ✅ PASS |

---

## 3. Invariants Maintained
- No `.` on `$PATH`.
- Real ELF `bash` and embedded `cc` compiler untouched.
- Musl TCC static link order (`crt1.o ... crtn.o -o`) preserved.
- Package repository signing (`signed-by=`) and `gpgv` verification strictly enforced.
- Maintainer script allowlist and prefix isolation preserved.
