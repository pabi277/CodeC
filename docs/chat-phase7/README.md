# CodeC Phase 7 Documentation — Multi-Terminal Sessions

Phase 7 brings native **Multi-Terminal Sessions** (`[client-only]`) to CodeC, allowing developers to run multiple independent shells concurrently (e.g. running builds/servers in background while editing or running git commands in foreground).

**Status (2026-08-28): IMPLEMENTED on `arena/01a048df-codec` — CI
compile-green (run `33185424586`); unit tests written but not yet executed
(see below); device verification pending.**

## Contents & References

- **[Part 7.1 — Multi-Terminal Session Manager & Switcher](PART_7_MULTI_TERMINAL.md)** — Architectural design, session manager model, drawer UI, and verification recipes.
- **[Design decisions D1–D12](PART_7_DESIGN_DECISIONS.md)** — every open
  technical question resolved against the actual code before implementation,
  including the evidence-based amendment of plan §2.4 (CodeCApi responses are
  per-invocation `mktemp` files, so no protocol change was needed).

## Implementation record (2026-08-28)

- `ui/terminal/TerminalSessionManager.kt` — pure-Kotlin manager: N sessions,
  monotonic numbering, adjacent-selection close, auto-recreate on last close,
  8-session cap, `anyAlive` wake-lock source. Injectable session factory +
  alive accessor for host tests.
- `TerminalViewModel` — delegates state to the manager; one CodeCApi collector
  per session; wake lock held while ANY session is alive; `send`/`sendCommand`/
  `resize` route to the active session (public API preserved, so `ModulesScreen`
  and the `Screen.Terminal(cmd)` route are untouched); `installUserland(force)`
  resets to one fresh session.
- `TerminalScreen` — session-number badge + dropdown switcher (status dot,
  rename dialog, close-confirm, "+ New session"); `TerminalEmulatorView` gained
  `resizeKey` so a switch re-applies grid dims (kills the 80×24 cursor-drift
  latent bug, D10).
- `TerminalSessionManagerTest` — 10 pure-JVM tests (create/switch/close/
  adjacent/auto-recreate/rename/cap/anyAlive).

### Known follow-ups

1. **Unit tests are not executed by CI yet.** Verified against
   `.github/workflows/build-apk.yml` on `main`: the workflow runs
   `:app:assembleDebug` only — no `testDebugUnitTest` (so the Phase 4.8 doc's
   "assemble + unit tests + lint" no longer matches the workflow). The agent
   sandbox has no JDK and its token **cannot push workflow-file changes**
   (push rejected: `refusing to allow a GitHub App to … without workflows
   permission`). Owner one-liner to close this (between assemble and artifact
   upload in `build-apk.yml`):
   ```yaml
   - name: Unit tests
     run: gradle :app:testDebugUnitTest --no-daemon --stacktrace
   ```
2. **Device verification recipe** (§4 below) still pending — needs the owner
   on hardware with the branch APK (CI artifact of run `33185424586`).

## Key Invariants
- Each terminal session owns its independent PTY master/slave pair and VT ANSI buffer.
- Activity-scoped lifecycle ensures background session processes keep running when switching tabs.
- `CodeCApi` bridge requests route cleanly per session.
- Client-only implementation (no package repo rebuild required).

