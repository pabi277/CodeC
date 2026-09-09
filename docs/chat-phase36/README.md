# CodeC Phase 36 — Terminal speed & feel

> **Status:** ✅ DEVICE-PASSED on `arena/01a086a0-codec`; merge authorized by
> the owner · The original acceptance and the three follow-up regressions
> (streaming output, background survival, session switching) all passed on the
> owner's device validation after fixes `da126cf` and `11fe8d7`.
> **Cost:** `[client-only]` · **Effort:** M · **Owner row:** *"Terminal is
> good but Termux is very fast and my Terminal sometimes stays exited for a
> while then start working and also sometimes behavior not user friendly"*

```text
  36.1  "stays exited for a while then start working" → cold-start latency
  36.2  "behavior not user friendly"                   → session UX polish
```

| Part | Title | Cost | Effort | Status |
|---|---|---|---|---|
| [36.1](PART_36_1_START_LATENCY.md) | Cold-start latency | client-only | M | ✅ implemented; device-passed |
| [36.2](PART_36_2_UX_BEHAVIOR.md) | Session UX behavior | client-only | S | ✅ implemented; device-passed |

**Implementation record (2026-09-09):** startup boundaries are captured in
`TerminalStartMeasurement` and logged through `AppLogger`; the `PreparedShell`
cache key is compiler settings plus the marked userland generation, with an
explicit `cc` frontend rewrite on cache hits. `TerminalLifecycle` drives the
visible starting/running/exited state, the shell emits a private OSC first-
prompt marker through `PS1`, and each session owns an `OrderedReadinessQueue`.
The userland warm-open path trusts its marker instead of probing/re-extracting;
forced reinstall and compiler-setting changes invalidate preparation. Pure
queue/cache-key/timing tests and readiness protocol tests are in CI run
`34370970512` (GREEN). The original owner device acceptance passed, then
reported three follow-ups: `friendly_apt()` buffered package output, terminal
commands stopped after backgrounding, and session switching redrew duplicate
prompts. The current fixes stream apt directly through the PTY, promote active
sessions to `TerminalForegroundService` without the ten-minute wake-lock
cutoff, and propagate/seed terminal geometry to avoid switch-only SIGWINCH
redraws. The owner then reported the follow-up device validation passed.
The phase is device-passed and is being merged on the owner's command.

**Open-source-first reference** (`docs/PHASE34_37_OSS_RESEARCH.md` §3):
**jackpal Android-Terminal-Emulator (Apache-2.0, archived)** is the canonical
open Android VT-100 terminal — native PTY + separate emulator/view + multi-window —
and confirms CodeC's `libcodec-pty.so` + `TerminalEmulator` + `TerminalEmulatorView`
split is the right architecture; any emulator/scrollback detail is cross-checked
there. **Termux is GPL-3.0 → behavior reference only** (the "fast" feel = one-time
bootstrap, then `exec` on every open — exactly 36.1's cache). No library swap.

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
