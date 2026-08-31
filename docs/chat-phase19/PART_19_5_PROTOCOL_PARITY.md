# CodeC Phase 19.5 — protocol & interaction parity (DA, OSC 52, mouse, keys, menu)

**Status:** IMPLEMENTED (2026-08-31, `arena/01a056aa-codec`) — host tests
written, CI pending · **Cost:** `[client-only]`
· **Depends on:** 19.2 (integer cells, for hit-testing math)
· **Origin:** the owner's Phase 19 instruction to *"find other things
Termux does better than CodeC terminal and fix it"* — parity-audit gaps
#2–#5 of 2026-08-31.

---

## 1. What was missing and why it mattered

| Gap | Symptom on device | Fix |
|---|---|---|
| No **Device Attributes** answers (CSI c / CSI > c) | TUI programs probe identity before enabling advanced modes; silence makes some degrade to dumb output | DA1/DA2 responses |
| No **OSC 52** clipboard | ssh/tmux/vim `y` → clipboard flows that desktop terminals give for free | write-only OSC 52 |
| No **mouse reporting** (`?9/?1000/?1002/?1003/?1006/?1007`) | htop/vim/less on a phone: no touch interaction at all | SGR + legacy mouse, Termux-style touch mapping |
| Hardware **Ctrl+arrows** unhandled | word-jump in shell line editing did nothing | `CSI 1;5A..D` |
| Long-press menu lacked **Copy All / Share / Reset** | the three actions mature terminal apps expose | menu items |

## 2. Research notes (2026-08-31, public specs only)

* **Primary DA** — vt100.net (DEC terminal docs): a VT102-class terminal
  answers `ESC [ ? 6 c` to `CSI Ps c` (Ps = 0 or omitted). xterm's ctlseqs
  documents the same response family.
* **Secondary DA** — xterm ctlseqs: `CSI > Ps c` is answered
  `CSI > Pp ; Pv ; Pc c` where Pp is the terminal type and Pv a firmware
  version. We self-identify (`0;100;0` — CodeC's own version number, not
  a borrowed xterm/Termux identity).
* **OSC 52** — `ESC ] 52 ; Pc ; Pd BEL|ST` with base64 `Pd`; `?` is a
  read QUERY (https://gist.github.com/robin-a-meade/14a279d28abdfb0526307ccb2c9b2381
  and the sequence's xterm origin). Write-only support is the safe subset
  several terminals ship; read-back silently leaks the clipboard to any
  program and is refused here.
* **Mouse encoding** — xterm ctlseqs "Mouse Tracking": DECSET `?9` (X10),
  `?1000`, `?1002` (button-event), `?1003` (any-event), `?1006` (SGR),
  `?1007` (alternate scroll). SGR form `CSI < Cb ; Cx ; Cy M/m` with
  Cb = button(0/1/2) | 4 shift | 8 meta | 16 ctrl | 32 motion, wheel =
  buttons 64/65, release = the `m` final; free motion (1003) carries
  Cb = 3|32 = 35 (cross-checked against a third-party decoder bug report
  describing exactly that value). Legacy X10 form: `CSI M` + chars
  32+Cb/32+col/32+row, 1-based, unusable past column 223. Enabling one
  tracking mode replaces the previous one (xterm semantics).
* **Touch mapping** — Termux's documented on-device behavior is that while
  an app enables mouse reporting, touch swipes are delivered as WHEEL
  events (verified from a third-party project's Termux compatibility
  notes: "touch-swipes → wheel events in Termux"). Taps are clicks.

## 3. Design decisions

* **D1 — write-only OSC 52.** A program may SET the clipboard
  (`Pc` empty or containing `c`), never read it (`?` ignored, primary-only
  selections ignored). Decoded text capped at 100,000 chars
  (`MAX_OSC52_LENGTH`); the pure-Kotlin `Base64Codec` (RFC 4648, written
  from the spec so host tests run it without `android.util.Base64`)
  fail-closes on invalid input. The AnsiParser OSC payload cap rose
  1024 → 8192 so real clips survive (≈6 KB decoded — still bounded).
* **D2 — mouse modes are buffer state** (like `bracketedPaste`), exposed
  through `TerminalSnapshot.mouseMode` so the Compose layer reacts to
  `?1006h` etc. mid-session; RIS (`ESC c`) clears them.
* **D3 — touch mapping mirrors Termux:** tap = left press+release at the
  cell; swipe = wheel (one event per cell-height of travel, direction by
  sign); the local scrollback keeps working when no app captured the
  mouse. `onMouseEvent` routes raw bytes through the existing
  `viewModel.sendKey` path (no shell, no ctrl-latch mutation).
* **D4 — identity honesty:** DA2 advertises CodeC's own emulator rather
  than impersonating xterm/Termux version strings.
* **D5 — menu actions are pure client work:** Copy All (transcript),
  Share (ACTION_SEND chooser), Reset (RIS on the active session — same
  `resetEmulator()` as the toolbar restart path uses internally).

## 4. Implementation record (2026-08-31, commits f45dea4 + 2409f87)

* New pure files: `MouseEncoding.kt` (SGR/legacy encoders + mode bits),
  `Base64Codec.kt`.
* `TerminalEmulator`: DA1 (`CSI c`, `CSI 0 c` → `ESC[?6c`), DA2
  (`CSI > …c` → `ESC[>0;100;0c`), OSC 52 branch in `osc()`, mouse
  DECSET/DECRST handling in `handlePrivate` + `setMouseTracking`
  replace-semantics, `onClipboardWrite` callback.
* `TerminalSession` relays clipboard writes as a `SharedFlow`;
  `TerminalViewModel` collector writes the Android system clipboard
  (silent, logged on failure). `TerminalSnapshot.mouseMode` plumbed.
* `TerminalEmulatorView`: tap→click, swipe→wheel, menu items, hardware
  Ctrl+arrows. `TerminalScreen` wires `onMouseEvent = viewModel.sendKey`,
  `onReset = viewModel.resetEmulator` (new passthrough).
* **Tests (16):** `MouseEncodingTest` (8) — SGR press/release/1-based
  coords/wheel 64+65/motion bit/free-motion 35/modifiers 4-8-16, legacy
  32-offsets + release-as-3 + out-of-range null; `TerminalProtocolTest`
  (8) — DA1/DA2 responses, OSC 52 BEL & ST terminators, UTF-8 clips,
  read/invalid/primary-only refused, mouse mode set/reset/replace/RIS,
  base64 codec padding/whitespace/garbage.

## 5. Exit condition & device recipe

**Verdict: PASS — owner device rounds, final word 2026-08-31: "All ok now"** (Phase 19 accepted as a whole; see README + JOURNEY item 18).

```text
1. printf '\\e]52;c;%s\\a' "$(printf 'hello from osc52' | base64)"
   EXPECT: paste anywhere → "hello from osc52".
2. htop → tap rows / swipe up-down.
   EXPECT: tap selects a row; swipe scrolls the list (wheel events).
3. vi ~/.bashrc (or any vim) → :set mouse=a → tap moves the cursor.
4. less bigfile → swipe scrolls inside less.
5. ctrl+left / ctrl+right on a hardware keyboard in the bash prompt.
   EXPECT: word-jump works.
6. Long-press → Copy All / Share / Reset all behave.
PASS = 1–6 all behave; after closing htop/vim the normal scrollback works again.
```

## 6. Invariants

Client-only; emulator/parser changes are pure Kotlin; no PTY/JNI changes;
input still goes through the existing write path (no shell, no `.` on
PATH); clean-room — every byte sequence is implemented from the public
xterm/DEC specifications, not from any terminal's source code.
