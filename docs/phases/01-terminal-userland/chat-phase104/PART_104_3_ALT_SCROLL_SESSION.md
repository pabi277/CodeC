# CodeC Phase 104.3 — Alt-scroll and session facts

> **Status:** 🚧 IMPLEMENTING · **Cost:** `[client-only]` · **Effort:** M

## First move: evidence, not code

- `TerminalEmulator.kt`: `1007 -> buffer.mouseMode |= MouseModes.ALT_SCROLL`; no reader.
- `TerminalEmulatorView` `onScroll`: capture modes send mouse wheel; otherwise the local
  buffer scrolls, which on the alternate screen has no history.
- `TerminalSession`: exit sets `_alive`/`_exitCode`; no text is written; input is dropped.
- `ShellBootstrap.prepare`: `cwd = projects` always.

## Design

- `AltScrollPolicy.keysFor(rows: Int, applicationCursor: Boolean): String` — one `ESC O A/B`
  (application cursor) or `ESC [ A/B` per row, up for newer, down for older.
- `SessionFacts.dead(code: Int?): String` → `"Process completed (code N) — press Enter"`;
  Enter after death starts a new session.
- `SessionFacts.inheritCwd(pid: Int?, fallback: File): File` reads `/proc/<pid>/cwd` of
  the previous tab's shell when it still exists.

## The Android edge

Alt-scroll wiring in `TerminalEmulatorView.onScroll`: when the alternate screen is
showing, ALT_SCROLL is set and no mouse capture is on, scroll deltas become key input.
Keep-screen-on: a per-session flag applied with `View.keepScreenOn` (no permission, no
DataStore). Kill-process: the terminal menu calls `PtyNative.kill(pid, SIGKILL)`.

## Exit condition

- Pure tests: `AltScrollPolicyTest`, `SessionFactsTest`.
- Wiring pins: `AltScrollWiringTest`, `SessionWiringTest`.
- Device rows: `less` scrolls with the wheel; the dead-session line appears and Enter
  restarts; a new tab opens in the previous tab's directory; Keep-screen-on and Kill work.

## Deferred / rejected with reasons

- Fling on the alternate screen: not planned in this part; the fling stays on the local
  buffer only.
