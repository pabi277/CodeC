# CodeC Phase 19 — Terminal Parity (Termux-quality terminal)

**Status:** Planned (design/spec only — no code written) · **Cost:** `[client-only]`
· **Depends on:** Phase 6 (Terminal UX), Phase 7 (Multi-terminal sessions)

> Goal: make CodeC's terminal behave like **Termux** — smooth live output,
> crisp non-overlapping glyphs, and correct reflow when you zoom/resize — by
> **re-implementing the mechanisms in CodeC's own pure-Kotlin emulator**, not by
> copying Termux code (see the licensing note below).

## Why this exists (the owner's report)

The owner asked to "copy everything from Termux terminal" because CodeC's
terminal has three concrete problems:

1. **Live output is not live.** "If I download something it prints everything
   *after* the download" — progress bars / streaming output appear only at the
   end instead of animating.
2. **Glyphs overlap.** "Letters overlapping, not visually good" — characters
   collide and the grid looks smeared.
3. **No reflow on zoom.** "If I zoom out the previous commands don't get full
   screen, they remain as they were" — old lines keep their old width and leave
   empty space after widening.

All three are traced to specific CodeC code (see each part). See the before/after
sketch: [`mockups/terminal-before-after.png`](mockups/terminal-before-after.png).

## ⚖️ Licensing note (important — read before "copying Termux")

Termux's `terminal-emulator`/`terminal-view` is **GPLv3**. Copying that source
into CodeC would force **all of CodeC** under GPLv3 and conflicts with the
project's standing invariant ("never use official `com.termux` packages or
repositories"). **These phases therefore re-implement the *behavior* (reflow,
integer-cell rendering, render cadence) in CodeC's existing clean-room
`TerminalEmulator`/`TerminalBuffer`/`TerminalEmulatorView` — no GPL code is
pasted.** The result is Termux-*quality*, license-clean, and host-unit-testable
on CI (the local sandbox has no JDK; CI is the only test executor).

## The three parts

| Part | Fixes | Bug | Doc |
|---|---|---|---|
| **19.1** | **Scrollback + screen reflow on resize/zoom** | #3 (zoom-out doesn't refill) | [PART_19_1_REFLOW.md](PART_19_1_REFLOW.md) |
| **19.2** | **Integer-cell crisp rendering** (no overlap) | #2 (letters overlap) | [PART_19_2_RENDERING.md](PART_19_2_RENDERING.md) |
| **19.3** | **Live render cadence & streaming output** | #1 (prints only at end) | [PART_19_3_LIVE_OUTPUT.md](PART_19_3_LIVE_OUTPUT.md) |

Each part is independently implementable and testable; recommended order is
**19.3 → 19.2 → 19.1** (quickest visible win first, hardest last) — but 19.1 is
the owner's clearest complaint, so it may be done first if preferred. Each doc
states its own dependencies.

## Standing rules (unchanged)

- **No PR/merge and no push to `main` without the owner's explicit command.**
  Committing/pushing the session `arena/*` branch is fine.
- **Client-only:** no `[repo-build]`, no bootstrap/`$PREFIX` changes, no native
  (`libcodec-pty.so`) changes required for any part. Pure Kotlin + Compose.
- Reuse the existing emulator; keep everything host-unit-testable. Verify state
  (`git status`, `gh run list`) before acting; a part is done only when its Exit
  condition is device-verified by the owner.
- **Honor terminal invariants** (Phase 6/7): no `.` on PATH, don't change the
  PTY/JNI contract, keep the Phase 7 multi-session routing and `resizeKey`
  behavior intact.

## Current terminal architecture (for the implementer)

```
PTY (libcodec-pty.so) ──read──▶ TerminalSession.readLoop()   [Dispatchers.IO]
                                   │  emulator.feed(bytes)     (AnsiParser → TerminalBuffer)
                                   │  publish() → _snapshot: MutableStateFlow<TerminalSnapshot>
                                   ▼
TerminalViewModel.snapshot (flatMapLatest, StateFlow, conflated)
                                   ▼
TerminalEmulatorView (Compose Canvas)  ── onResize(cols,rows) ──▶ session.resize()
```

Key files: `ui/terminal/TerminalBuffer.kt` (grid + scrollback + `resize`),
`ui/terminal/TerminalEmulator.kt`, `ui/terminal/AnsiParser.kt`,
`ui/terminal/TerminalSession.kt` (read loop + `publish()`),
`ui/viewmodels/TerminalViewModel.kt` (`snapshot` StateFlow),
`ui/components/TerminalEmulatorView.kt` (Canvas renderer, pinch-zoom).
Existing tests: `TerminalBufferTest`, `AnsiParserTest`, `TerminalUxTest`,
`TerminalThemeTest`, `XtermColorsTest`.
