# CodeC Phase 104.2 — PTY truth

> **Status:** 🚧 IMPLEMENTING · **Cost:** `[client-only]` (native C in the existing NDK
> target) · **Effort:** S

## First move: evidence, not code

- `app/src/main/cpp/pty.c` `configure_slave`: `tio.c_iflag |= (ICRNL | IXON)` — IXON on,
  so Ctrl-S can freeze output.
- `grep -n IUTF8 app/src/main/cpp/pty.c` = 0.
- `nativeSetWindowSize(fd, rows, cols)`; `ws_xpixel`/`ws_ypixel` are left 0.

## Design

- `configure_slave`: clear `IXON` (Ctrl-S/Ctrl-Q pass through to the program); set
  `IUTF8` when defined (`#ifdef IUTF8`), so the kernel line discipline counts UTF-8.
- Winsize carries pixel size: `nativeSetWindowSize(fd, rows, cols, xPx, yPx)`;
  `PtyNative.setWindowSize` takes optional pixel sizes (default 0 = unknown, as today).

## The Android edge

JNI signature changes in both `pty.c` and `PtyNative.kt` (`external fun`). The native
side is compiled only by CI (NDK); a mismatch surfaces as `UnsatisfiedLinkError` at
runtime, so the Kotlin declaration and the C symbol are pinned by a source test.

## Exit condition

- `PtyTruthWiringTest` (source pin): `pty.c` clears `IXON`, sets `IUTF8` under `#ifdef`,
  and `nativeSetWindowSize` takes four size args; `PtyNative.kt` declares the same.
- Device row: Ctrl-S does not freeze `cat`; Ctrl-Q resumes; typed UTF-8 counts right.

## Deferred / rejected with reasons

- Passing pixel sizes from the view on every resize: deferred to the view wiring if the
  pixel size is not already in `TerminalSession.resize`; the field exists and is 0 until
  then, which is the behaviour today.
