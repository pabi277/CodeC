# Phase 4 Part 4.1 — Shared-Storage Access (`~/storage`)

**Status: COMPLETE and device-verified (2026-08-24).**

---

## 1. Goal & Architecture

Provide POSIX-compliant terminal programs in CodeC (`bash`, `cc`, `nano`, `cp`, `tar`, `python`, etc.) direct read and write access to Android public shared storage directories (`Downloads`, `Documents`, `DCIM`, `Pictures`, `Music`, `Movies`, and secondary SD cards), analogous to Termux's `termux-setup-storage`.

### Symlink Layout in `$HOME/storage/`:
- `~/storage/shared` $\rightarrow$ `/storage/emulated/0`
- `~/storage/downloads` $\rightarrow$ `/storage/emulated/0/Download`
- `~/storage/documents` $\rightarrow$ `/storage/emulated/0/Documents`
- `~/storage/dcim` $\rightarrow$ `/storage/emulated/0/DCIM`
- `~/storage/pictures` $\rightarrow$ `/storage/emulated/0/Pictures`
- `~/storage/music` $\rightarrow$ `/storage/emulated/0/Music`
- `~/storage/movies` $\rightarrow$ `/storage/emulated/0/Movies`
- `~/storage/external-*` $\rightarrow$ Secondary storage mount points under `/storage/` (e.g. `/storage/XXXX-XXXX`).

---

## 2. Android 11+ (API 30+) Scoped Storage & SELinux Handling

### The Problem
On Android 11+ through Android 16:
1. Running system binaries such as `/system/bin/am` or `/system/bin/cmd activity` from an app's child shell subprocess is rejected by Android SELinux policy with:
   `cmd: Failure calling service activity: Failed transaction (2147483646)`
2. Direct POSIX filesystem writes (`open(..., O_CREAT|O_WRONLY)`) to `/storage/emulated/0/...` are blocked by Android's FUSE / Scoped Storage layer with `EPERM` (`Operation not permitted`) unless the app holds **All Files Access (`MANAGE_EXTERNAL_STORAGE`)**.

### The Solution
1. **In-Band Terminal Control Sequence (OSC 1337):**
   - When `codec-setup-storage` detects that storage write access has not yet been granted by Android, it emits an in-band OSC escape sequence:
     `printf '\033]1337;CodeCRequestStorage\007'`
   - `AnsiParser` parses the OSC sequence from the PTY stream $\rightarrow$ `TerminalEmulator` $\rightarrow$ `TerminalSession` $\rightarrow$ `TerminalViewModel` dispatches `MainActivity.requestStoragePermissions()` directly on the Android UI thread.
   - The Android system "Allow access to manage all files" toggle screen opens immediately on screen.
2. **One-Tap In-App UI Setup:**
   - **Terminal Screen:** TopAppBar includes a **Folder icon** (`FolderOpen`) that triggers the All Files Access permission screen or confirms ready status.
   - **Settings Screen:** Under **Settings $\rightarrow$ Storage**, the **Terminal Storage Access (`~/storage`)** card displays permission status and provides a **`SETUP STORAGE`** action button.
3. **Automatic Refresh on Return:**
   - When the user toggles **Allow** and returns to CodeC, `MainActivity.onResume()` detects the permission grant, creates/refreshes the symlinks, and displays a confirmation toast.

---

## 3. Real-Device Verification Evidence (2026-08-24)

Target: Samsung SM-A356E (Android 16, aarch64).

| Check | Command | Observed Result | Status |
|---|---|---|---|
| CLI script existence | `which codec-setup-storage termux-setup-storage` | `$PREFIX/bin/codec-setup-storage`, `$PREFIX/bin/termux-setup-storage` | ✅ PASS |
| Symlink creation | `codec-setup-storage` | Symlinks created under `$HOME/storage/` pointing to `/storage/emulated/0/...` | ✅ PASS |
| Symlink listing | `ls -la ~/storage` | `dcim`, `documents`, `downloads`, `movies`, `music`, `pictures`, `shared` | ✅ PASS |
| Write to shared storage | `echo "hello" > ~/storage/downloads/codec_storage_test.txt` | File created successfully without error | ✅ PASS |
| Read from shared storage | `cat ~/storage/downloads/codec_storage_test.txt` | Output: `hello` | ✅ PASS |
| File cleanup | `rm ~/storage/downloads/codec_storage_test.txt` | File removed cleanly | ✅ PASS |
| C compilation in shared storage | `cc ~/storage/downloads/shared_c_test.c -o a.out && ./a.out` | `Compiling from shared storage OK` | ✅ PASS |
| Package manager integrity | `pkg update && dpkg --audit` | APT metadata verified, dpkg audit silent/clean | ✅ PASS |
| Toolchain integrity | `which cc pkg editor pager vi` | All tools intact under `$PREFIX/bin` | ✅ PASS |

---

## 4. Invariants Maintained
- No `.` on `PATH`.
- Real ELF `bash` and embedded `cc` compiler untouched.
- TCC static musl link order (`crt1.o ... crtn.o -o`) preserved.
- Package repository signing (`signed-by=`) and `gpgv` verification untouched.
