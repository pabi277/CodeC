# CodeC Phase 36 — Terminal speed & feel

> **Status:** 📋 PLANNED (researched + specced, not implemented) ·
> **Cost:** `[client-only]` · **Effort:** M · **Owner row:** *"Terminal is
> good but Termux is very fast and my Terminal sometimes stays exited for a
> while then start working and also sometimes behavior not user friendly"*

```text
  36.1  "stays exited for a while then start working" → cold-start latency
  36.2  "behavior not user friendly"                   → session UX polish
```

| Part | Title | Cost | Effort | Status |
|---|---|---|---|---|
| [36.1](PART_36_1_START_LATENCY.md) | Cold-start latency | client-only | M | 📋 planned |
| [36.2](PART_36_2_UX_BEHAVIOR.md) | Session UX behavior | client-only | S | 📋 planned |

## The start path today (evidence — read from source)

Tap Terminal → `TerminalViewModel.ensureStarted()` →
`startInternal()` → `startItem(item)` runs, **on IO but serially**:
1. `installUserlandInternal` → `UserlandInstaller.installIfNeeded()` —
   re-validates/possibly re-extracts the userland each cold start;
2. `prepareShell()` → `ShellBootstrap.prepare(compilerSettings)` — rebuilds
   the env, rewrites the `cc` frontend, resolves the shell, probes the
   toolchain;
3. `item.session.start(prepared)` → `PtySession.startShell` fork/exec + the
   reader coroutine + the `RenderPump` (~60 fps emitter).

While that runs the screen is **blank** — no "starting shell…" state — so a
slow step reads as "stayed exited for a while". Termux feels fast because its
bootstrap is one-time and every subsequent open is just `exec login`.

**Law: no fix on a guess** — 36.1 ships a measurement card (tap→prompt
latency, split per step) before anything is changed, then removes the step
the card blames.

## Cross-device risks to watch

- Low-end storage: userland re-validation/extract is the slowest step on
  cheap eMMC — the measurement must run there, not on a flagship.
- PTY/JNI `libcodec-pty.so` ABI across the device matrix (the terminal's
  fork/exec path is already device-proven; do not disturb it casually).
- Foreground-service / Doze differences across OEMs affect a long-lived shell.
