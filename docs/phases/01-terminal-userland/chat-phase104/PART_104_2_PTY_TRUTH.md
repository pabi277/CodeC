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

- Pixel sizes from the view are **wired**, not deferred. The view passes the settled cell
  size (`PtyWinSize.pixelExtent`) through the ViewModel and `TerminalSession.resize` to the
  PTY. `TerminalSession.start` seeds the new PTY with the last pixel extent, so a fresh
  shell does not start at 0x0.

## Implementation (2026-10-14, `arena/4baff5b7-codec`)

- `pty.c` `configure_slave`: clears `IXON` and `IXOFF`; sets `IUTF8` under `#ifdef IUTF8`.
- `pty.c` `nativeSetWindowSize(fd, rows, cols, widthPx, heightPx)`: writes `ws_xpixel` and
  `ws_ypixel`, clamped to 0..65535 (0 = unknown).
- `ui/terminal/PtyWinSize.kt` (new, pure): `pixelExtent(cells, cellPx)`.
- `PtyNative.kt`, `PtySession.kt`, `TerminalSession.kt`, `TerminalViewModel.kt`,
  `TerminalEmulatorView.kt` (`onResize` now carries pixel extents), `TerminalScreen.kt`:
  thread the pixel extent. `TerminalSession.resize` also sends the winsize when only the
  cell size changed (same grid), and keeps the emulator resize tied to a grid change.
- Tests: `PtyWinSizeTest` (3, host), `PtyTruthWiringTest` (4 source pins, host).
- Host: 169 passed, 0 failed. `pty.c` also compiles with `gcc -Wall -Wextra` against the JDK
  headers (both before and after).
- Not host-compiled: `TerminalSession.kt`, `PtySession.kt`, the ViewModel, the view and the
  screen (Android or coroutine imports). CI is their compile. The `PtyNative.kt` change
  compiles against a host `AppLogger` stub.

**Device pass required:** Ctrl-S does not freeze `cat`; Ctrl-Q resumes; typed UTF-8 erases a
whole character on backspace.

## Fix from the device pass (line redraw after pinch zoom)

- **Symptom (device, item 1):** after pinch-zooming, typing until the line is full did not
  wrap to a new line; the text started again on the same line.
- **Cause (code reading, not reproduced on a device):** 104.2 sent the PTY a winsize update
  when only the pixel size changed. Linux raises SIGWINCH on any winsize change, and
  `PtyNative` also signals explicitly. The shell then redrew its line mid-edit at a stale
  width. Before 104.2, a pixel-only change never reached the PTY.
- **Fix:** `TerminalSession.resize` always remembers the latest pixel size, and signals the
  PTY only when rows or columns change. The pixel size rides along with the next real grid
  change. Pin: `PtyTruthWiringTest.a pixel-only resize never signals the shell`.
- **Not claimed:** a grid change during a zoom still signals the shell, as it did before
  104.2. If the device still wraps wrongly, the next suspect is the live-versus-settled size
  mismatch during the pinch in `TerminalEmulatorView`.
- **Device row to re-run:** pinch-zoom, then type until the line is full. Expect a normal
  wrap to the next line.

**Correction (same device round):** the cause of the wrap symptom was not the pixel-size
SIGWINCH described above. It is the prompt's zero-width marker, which is not wrapped in
`\[ … \]`, so readline miscounts the prompt width. The pixel fix stays (it removes a real
redraw), but see TROUBLESHOOTING §66 for the actual fix.
