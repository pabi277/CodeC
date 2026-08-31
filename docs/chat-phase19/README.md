# CodeC Phase 19 — Terminal Parity (Termux-quality terminal)

**Status:** IMPLEMENTED on `arena/01a056aa-codec` (2026-08-31, owner: "Ok start phase 19 … fix the bugs mentioned and also try to find other things that Termux better than CodeC terminal and fix it") — all five parts coded and **CI-GREEN (`Build APK` `33371114549`** on `arena/01a056aa-codec`: assemble + `testDebugUnitTest` + `lintDebug`; rounds 1–2 caught the Brahmic vowel-sign width gap and test-trace bugs, now fixed**)**; device round 1 (2026-08-31): 19.2 letter gaps (ceil slack) → fixed same day (`fitSizeToGrid`, PART_19_2 §7.1); round 2 (screenshots + `stty size` 32×60 vs Termux 39×71): density & weight → default 12sp + bundled JetBrains Mono Medium/Bold (OFL) + 0.9 row-pitch factor (PART_19_2 §7.2), round 3 (owner: "feels lagging, not smooth scrolling, keyboard sometimes not popping up") fixed — run-batched drawing (~2600→~dozens of draw calls/frame), gesture detectors no longer restart every output frame, pixel-smooth sub-row scrolling, IME retry loop (PART_19_3 §9) — remaining gate = round 4 · **Cost:** `[client-only]`
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

## ⚖️ Ground rule — replicate the behavior, never copy the code

**Do NOT copy Termux's (or anyone's) source code.** Termux's terminal is GPLv3
and copying it would relicense CodeC and violate the project's standing invariant
against using official Termux code. We replicate the **same behavior and result**
(smooth live output, crisp non-overlapping glyphs, correct reflow on zoom) by
writing **original code in CodeC's own clean-room emulator**
(`TerminalEmulator`/`TerminalBuffer`/`TerminalEmulatorView`). Same *features*,
original implementation. It's fine to read public specs (VT100/xterm/ECMA-48) and
learn the *technique*; it is not fine to paste GPL code.

## 🔎 Research step — do more research when needed (per part)

Each part is root-caused from the current code, but **do additional research if a
detail is unclear**, and record it as a short "Research notes" block in that
part:

- Consult **public terminal specs** for exact behavior: the
  [xterm control sequences](https://invisible-island.net/xterm/ctlseqs/ctlseqs.html),
  ECMA-48, VT100/VT220 references, and Unicode line-breaking/width (UAX #11) for
  the reflow width question.
- Look up **Jetpack Compose Canvas / Android `Paint`** specifics before guessing
  (integer text metrics, `Paint.getFontMetricsInt`, subpixel/hinting, real bold
  typeface vs `isFakeBoldText`) — Part 19.2.
- Look up **`kotlinx-coroutines`** patterns for frame-paced emission and
  `runTest` virtual time — Part 19.3.
- Study the technique of mature clean-room/permissively-licensed emulators
  *conceptually* (how reflow rejoins soft-wrapped rows, how render cadence is
  decoupled) — read, understand, then write CodeC's own version. Do **not** copy.
- **TODO for the implementer:** if a device round reveals behavior the doc didn't
  predict, research the cause, note it, and adjust the part before calling it done.

## The five parts (three planned + two from the parity audit)

| Part | Fixes | Bug | Doc |
|---|---|---|---|
| **19.1** | **Scrollback + screen reflow on resize/zoom** | #3 (zoom-out doesn't refill) | [PART_19_1_REFLOW.md](PART_19_1_REFLOW.md) |
| **19.2** | **Integer-cell crisp rendering** (no overlap) | #2 (letters overlap) | [PART_19_2_RENDERING.md](PART_19_2_RENDERING.md) |
| **19.3** | **Live render cadence & streaming output** | #1 (prints only at end) | [PART_19_3_LIVE_OUTPUT.md](PART_19_3_LIVE_OUTPUT.md) |
| **19.4** | **Unicode column widths** — CJK/emoji double-width, Indic clusters (non-ASCII overlap + smear) | parity audit gap #1 | [PART_19_4_UNICODE_WIDTH.md](PART_19_4_UNICODE_WIDTH.md) |
| **19.5** | **Protocol & interaction parity** — DA1/DA2, OSC 52 clipboard, xterm mouse reporting with Termux-style touch mapping, Ctrl+arrows, Copy All/Share/Reset | parity audit gaps #2–5 | [PART_19_5_PROTOCOL_PARITY.md](PART_19_5_PROTOCOL_PARITY.md) |

19.4 and 19.5 were added on 2026-08-31 by the parity audit the owner
requested ("find other things Termux does better and fix it"); the audit
table lives in PART_19_4 §1. Implementation order used: **19.3 → 19.2 →
19.4 → 19.1 → 19.5** (19.1+19.4 share the `TerminalBuffer` rewrite, so
they landed as one commit). All parts are host-unit-tested; CI is the only
test executor (no JDK in the agent sandbox).

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
