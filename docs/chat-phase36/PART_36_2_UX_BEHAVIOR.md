# CodeC Phase 36.2 — Terminal session UX behavior

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S

## Symptom (owner)

*"sometimes behavior not user friendly"* — the owner finds the terminal's
behavior surprising in places (dead air, unclear restart, unclear "still
running", sessions that seem to reset on their own).

## What exists today (evidence)

- `TerminalSessionManager` already has: multi-session (max 8), custom titles,
  a "new session" `+`, close→adjacent-select, and `closeAll` (used on
  forced userland re-install — every shell dies **silently** when that runs).
- `TerminalSession` paints `[process exited with N]` / `[process exited]` and
  `_alive` flows drive the switcher's status badge; `restart()` stops +
  `resetEmulator()` + restarts in place.
- A command queued while starting fires after a hard-coded `delay(350)` —
  a magic number, and the queue holds exactly one command.

The "not user friendly" gaps are: no state row while starting (36.1), a
silent mass-exit on userland re-install, the 350 ms queued-command race, and
no obvious "this shell restarted because…" explanation.

## Design (polish, not rewrite)

1. **State row** — a persistent status line (bottom or via `notice`) that says
   `starting shell…` / `running` / `exited (code)` so the terminal never looks
   dead (complements 36.1's measurement work).
2. **Explain a restart** — when a session is replaced or `closeAll` runs (e.g.
   forced re-install), the new shell opens with a one-line reason
   ("userland was updated — session restarted") instead of a silent blank.
3. **Queued-command fix** — replace the `delay(350)` + single-command queue
   with an ordered queue that flushes when the shell actually prints its first
   prompt (or a readiness marker), so `pkg install …` dispatched from the
   Packages tab never races the shell and never drops.
4. **Clear affordances** — the toolbar already has restart/new-session; add a
   confirm only where destructive (close a session with a long-running child
   already warns implicitly via exit notice — keep it non-modal), and make the
   active-session switcher show the run state unambiguously.

## Exit condition

```text
(Device)
1. Open Terminal cold: a status row shows "starting shell…", then "running".
2. Kill a command (Ctrl+C) → "exited (…)" appears; the shell stays usable.
3. From Packages, INSTALL python while a fresh session is still starting:
   the command runs once the prompt is ready (no lost keystrokes, no 350 ms
   race).
4. Force a userland re-install: the replacement shell states why it restarted.
PASS = all four.
```

## Tests

- `CommandQueueTest` (host): the queue is ordered and flushes on the
  readiness signal; a second command while starting is appended, not dropped.
- The `delay(350)` literal is removed — CI greps stay green (lint/tests pin
  the readiness-signal path).
