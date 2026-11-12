# CodeC Phase 104.3 — Alt-scroll and session facts

> **Status:** ✅ CI GREEN `b85fa99` (run `43193599612`) · device pass required · **Cost:** `[client-only]` · **Effort:** M

## First move: evidence, not code

- `TerminalEmulator.kt`: `1007 -> buffer.mouseMode |= MouseModes.ALT_SCROLL`; nothing read it.
- `TerminalEmulatorView` `onScroll`: capture modes send mouse wheel; otherwise the local
  buffer scrolls, which on the alternate screen has no history, so `less` did not move.
- `TerminalSession`: exit set `_alive`/`_exitCode` and wrote `[process exited with N]`; Enter
  was dropped (no restart path).
- `ShellBootstrap.prepare` / `PreparedShell.cwd` = projects dir for every new tab.

## What shipped

- **Alt-scroll (`AltScroll`, pure).** `active(altScreen, mouseMode)` is true when the
  alternate screen is showing, mode 1007 is set, and no mouse tracking is active. `steps`
  turns a pixel delta into whole rows with a carried remainder (truncates toward zero, so
  up and down are symmetric). In `TerminalEmulatorView`, a swipe in that state emits
  `cursorSequence('B')` (down) or `'A'` (up) per row, so application-cursor mode is honoured.
  The fling loop uses the same branch and emits keys instead of moving the local scrollback.
- **Session facts (`SessionFacts`, pure).**
  - `exitNotice(code)` → `Process completed (code N) — press Enter`, or
    `Process completed — press Enter` when the code is unknown.
  - `restartsOnInput(dead, input)`: Enter (`\r` or `\n`) restarts a dead session. `dead` is
    `TerminalSession.isDead()`: not alive and lifecycle EXITED or FAILED, never while STARTING.
  - `inheritedDirectory(path)`: the previous tab's directory when it is an absolute path.
- **Cwd inheritance.** `TerminalViewModel.newSession` reads `/proc/<pid>/cwd` of the active
  shell (`PtySession.currentDirectory`) before creating the tab, and `startItem(cwd = …)`
  copies `PreparedShell` with that directory when it still exists. Otherwise: projects dir.
- **Terminal menu.** A ⋮ `IconButton` in the terminal top bar holds **Keep screen on**
  (checkmark when on) and **Kill process**.
  - Keep-screen-on sets `FLAG_KEEP_SCREEN_ON` on the host activity window. It is in memory
    (`TerminalViewModel.keepScreenOn`), so no DataStore key, permission, or Settings control.
    The flag is cleared when the screen leaves.
  - Kill process sends SIGKILL to the shell pid (`PtySession.killProcess`). The session then
    shows the dead-session line, and Enter restarts it.

## Decisions to report

- **Menu location.** The design said "the terminal menu". The only existing one is the More
  overflow inside the selection toolbar, which appears only with a selection. The new items
  go in an always-visible top-bar ⋮ instead. The existing selection More menu is unchanged.
- **Fling on the alternate screen is included.** An earlier draft deferred it. The roadmap
  says "wheel and fling emit ↑/↓", so the fling loop emits keys in alt-scroll.
- **Kill process kills the shell pid, not the process group.** Jobs started from the shell
  may survive until the hangup reaches them. Revisit with a device observation if needed.
- **Menu labels are literal English**, matching the sibling Copy All / Share / Reset items.
- **TouchTargetTest count 21 → 22**, with the reason written into that test's comment as its
  own rule requires.

## Verification

- Host: `AltScrollTest` (5), `SessionFactsTest` (3), `SessionTruthWiringTest` (5 source pins).
  Total 182 passed, 0 failed.
- Not host-compiled: `TerminalEmulatorView`, `TerminalScreen`, `TerminalViewModel`,
  `TerminalSession`, `PtySession` (Android or coroutine imports). CI is their compile.

## Exit condition

- Device row: `less` (or `man`) scrolls with the wheel and fling in the alternate screen.
- Device row: a shell that exits shows `Process completed (code N) — press Enter`; Enter restarts it.
- Device row: a new tab opens in the previous tab's directory (if `/proc/<pid>/cwd` is readable
  on the device; otherwise it opens in the projects dir, which is the fallback).
- Device row: Keep screen on keeps the display awake; Kill process ends the shell.
