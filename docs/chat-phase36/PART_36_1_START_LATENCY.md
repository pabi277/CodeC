# CodeC Phase 36.1 — Terminal cold-start latency

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M

## Symptom (owner)

*"my Terminal sometimes stays exited for a while then start working"* — dead
air between opening the Terminal tab and the prompt appearing.

## What exists today (evidence)

`startItem` runs three serial steps with **no visible state** in between
(see the phase README): userland `installIfNeeded`, `ShellBootstrap.prepare`,
then `PtySession.startShell`. On a cold app start (or after the process was
killed) all three re-run while the screen is blank.

## Design (measure, then cut)

1. **Measurement card** — a debug instrumentation that timestamps
   tap→userland-done, userland-done→prepare-done, prepare-done→prompt, and
   logs it (and shows it in LogsScreen). Run it on the owner's slowest device
   at two points: cold app start, and a warm re-open. Record the dominant step.
2. **Likely cuts (chosen by the card):**
   - **Cache `PreparedShell`** — `ShellBootstrap.prepare` output is a pure
     function of `(compilerSettings, userland state)`; memoize it and
     invalidate only when settings/userland actually change, so a warm re-open
     goes straight to `exec`.
   - **Show state instead of silence** — an immediate "starting shell…" row
     (reusing `TerminalSession.notice`) so the user sees the terminal is
     working, not dead.
   - **Don't re-verify userland every cold start** — short-circuit
     `installIfNeeded` on an already-valid marker (Phase 21 already has the
     concept), keeping the full verify for the explicit reinstall path.
   - **Warm the shell opportunistically** (optional, after the above): start
     the PTY on app launch in the background so the Terminal tab is already
     prompt-ready.
3. **Preserve the PTY/cc invariants** — `ShellBootstrap.prepare` rewrites the
   `cc` frontend on every RUN; the memoized value must still honor "rewrite
   `cc`" semantics and the signed-userland law (cache the *prepared shell*,
   never a stale `cc`).

## Exit condition

```text
(Device, the owner's slowest phone)
1. Measurement card shows tap→prompt latency and the per-step split; the
   dominant step is identified (not guessed).
2. Warm re-open reaches a prompt fast enough that the owner calls it
   "Termux-fast" (target: no perceptible dead air; the number is recorded).
3. Cold app start shows "starting shell…" immediately and the prompt lands
   with no silent gap.
PASS = all three.
```

## Tests

- `PreparedShell` cache invalidation (host): settings/userland change → miss;
  no change → hit. Pure if the cache key is extracted as a data class.
- Any step-timing helper is pure and host-tested; CI (executor of record)
  keeps the existing terminal tests green.
