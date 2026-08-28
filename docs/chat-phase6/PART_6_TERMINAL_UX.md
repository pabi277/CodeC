# CodeC Phase 6 — Part 6.1 Terminal UX Fixes

**Status:** ✅ IMPLEMENTED on branch `arena/01a0482c-codec` (2026-08-28).
**Cost:** `[client-only]` (no package rebuild; pure Kotlin/Compose/JNI edits).
**Depends on:** Phase 5.3 (bridge stable; terminal screen verified 2026-08-26).
**Invariants:** no `.` on PATH; no `build-package.sh -I`; TCC `-o` last; no `com.termux`; signed repo; no bootstrap in APK.

---

## 1. Context — why this part exists (grounded, not speculative)

Phase 5.3 proved the `CodeCApi` bridge works end-to-end (`codec-toast`,
`codec-share`, `codec-open-url`, `codec-vibrate`) over OSC 1337 + private-file
exchange. The terminal is the primary usage surface — but a source sweep of
`MainActivity.kt`, `TerminalScreen.kt`, `TerminalEmulatorView.kt`,
`TerminalExtraKeys.kt`, `TerminalSession.kt`, `PtyNative.kt` (2026-08-26)
found concrete usability defects that make daily use on a phone frustrating,
especially in landscape / with camera cutouts / with the keyboard open.

These are **not** speculative features; they are fixes for real defects
visible in the current branch (`arena/01a03e40-codec`, HEAD `0dfd23c`, clean):

| Symptom | Source evidence | Impact |
|---|---|---|
| Right-side text clipped in landscape | `MainActivity.enableEdgeToEdge()` + terminal `Column` `.imePadding()` only; zero `displayCutout` / `safeDrawingPadding()` hits | Unusable in landscape; cutout obscures command output |
| Extra-keys single row, scrolls horizontally, appears too high | `TerminalExtraKeys.kt`: hardcoded `Row` of 12 keys in `.horizontalScroll`; not configurable; height/placement not anchored to IME | Can't reach arrow/ESC quickly; keys off-screen on small phones |
| Screen sleeps during `pkg install` / compile | No `WakeLock` / `FLAG_KEEP_SCREEN_ON`; `TerminalViewModel` has no power management | Long jobs interrupted; user must keep touching screen |
| Printed URLs not tappable | No `Linkify` / `ClickableSpan` over terminal output; `codec-open-url` exists but is manual CLI only | User types URL in terminal, can't open with a tap |
| VT BEL (`\a`) silently ignored | `TerminalEmulatorView` / parser ignores BEL escape; no visual / vibro response | Scripts that use `echo -e '\a'` get no feedback |
| Terminal title static | `TerminalScreen` title bar uses fixed string; doesn't reflect cwd or running command | Hard to tell which session is active when multi-terminal (Phase 7) arrives |
| Copy button copies full transcript | Toolbar copy calls full-transcript copy; selection only via long-press dropdown | Can't quickly copy selected command output |

Additionally, `FileManagerScreen.kt` is flat (no tree), `EditorScreen.kt`
has dead Undo/Redo/Format/Find buttons (`showComingSoon()`), and
`ModulesScreen.kt` is dead (superseded by `pkg`). Those are captured in
Phase 8 (projects), Phase 9 (editor), Phase 10 (pkg catalog) — not
Phase 6 — but Phase 6's safe-area / keys / copy fixes make the terminal
usable enough that the user actually wants the rest.

---

## 2. Decision D1 — what Phase 6 builds (not a wish-list; pinned choices)

| Sub-fix | Design choice | Why |
|---|---|---|
| **Safe-area / cutout** | Add `WindowInsets.displayCutout` + `safeDrawingPadding()` to terminal `Column`; also set `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` in `MainActivity` / theme | Uses Android's standard inset API; no custom drawing; fixes landscape clipping without breaking existing layout |
| **Extra-keys grid** | Replace hardcoded `Row` with configurable `LazyVerticalGrid` (or `FlowRow`) of buttons; settings screen for add/edit/delete; macros (`pkg install`, `git status`, `cc`, etc.); anchor height to keyboard via `.imePadding()` or `WindowInsets.ime` | Matches Termux model; solves single-row/scroll/too-high problems; user-editable; no new permissions |
| **Wake lock** | Add `PowerManager.WakeLock` (or `FLAG_KEEP_SCREEN_ON` via `Window`) when terminal session is active and `TerminalSession.isRunning` is true; release when session stops / app backgrounded | Simple; prevents interruption during `pkg install`; no battery drain (only while terminal active) |
| **URL tap-to-open** | After `TerminalEmulatorView` renders output, scan with `Linkify.addLinks` (or regex matcher) for `https?://` patterns; wrap in `ClickableSpan` that fires `codec-open-url` or `Intent.ACTION_VIEW`; require `http`/`https` only (same gate as CLI) | Uses existing bridge; safe (same validation); improves workflow dramatically |
| **BEL / visual feedback** | On BEL (`\a`), post to main looper: brief screen-flash (overlay `View` alpha pulse) + optional short `Vibrator.vibrate(50)` if `VIBRATE` permitted; no notification | Matches VT spec; lightweight; uses existing `VIBRATE` manifest permission (already present from Phase 5.3) |
| **Dynamic title** | `TerminalScreen` reads `session.cwd` (or `TerminalSession.currentDir`) and running command snippet; updates `TopAppBar` title via `remember`; if empty, fallback to "Terminal" | Small Compose state change; helps multi-terminal (Phase 7) identification |
| **Selection-based copy** | Change toolbar copy behavior: if text is selected in `BasicTextField`-backed terminal view, copy selection; else copy full transcript (backward-compatible) | Requires selection-state tracking; can use existing `TextFieldValue` selection if terminal uses `BasicTextField` internally, else add selection tracker |

**What is explicitly NOT in Phase 6:**
- Multi-terminal (Phase 7 — needs session manager, separate PTY, routing)
- Projects / folder tree (Phase 8)
- Editor undo/find/format (Phase 9)
- `pkg` GUI / Modules replacement (Phase 10)
- Output panel / Run button (Phase 11)
- Language intelligence (Phase 12 — requires repo build)
- Any rebuild of bootstrap or repository

---

## 3. Source references — files that will be edited / verified

| File | Role in Phase 6 | Verification first |
|---|---|---|
| `app/src/main/java/com/codeci/ide/MainActivity.kt` | `enableEdgeToEdge()`, window insets, cutout mode | Read current content; confirm `imePadding()` only |
| `app/src/main/java/com/codeci/ide/ui/screens/TerminalScreen.kt` | Title, toolbar copy, safe-area padding application | Read; identify copy button logic and title setter |
| `app/src/main/java/com/codeci/ide/ui/screens/TerminalEmulatorView.kt` | BEL handling, URL scanning / click spans, selection tracking | Read parser/emulator code; confirm BEL ignored |
| `app/src/main/res/layout/` (terminal screen layout) | `Column` structure for padding | Check XML / Compose layout |
| `app/src/main/res/xml/file_paths.xml` (if FileProvider changes needed) | Not needed for Phase 6 (no file-share) | Skip |
| `app/src/main/java/com/codeci/ide/ui/components/TerminalExtraKeys.kt` | Replace hardcoded row with configurable grid | Read; confirm 12-key hardcoded list |
| `app/src/main/java/com/codeci/ide/TerminalViewModel.kt` | Wake-lock trigger (`isRunning` state) | Read; confirm single-session model |
| `app/src/main/java/com/codeci/ide/TerminalSession.kt` | `cwd`, command snippet for title; running state | Read; confirm fields available |
| `app/src/main/cpp/pty.c` / `PtyNative.kt` / `PtySession.kt` | Not changed in Phase 6 (PTY untouched; multi-terminal touches this in Phase 7) | Verify no regression needed |
| `app/src/main/res/values/themes.xml` / `themes/` | Cutout mode theme attribute | Check for `layoutInDisplayCutoutMode` |

---

## 4. Implementation steps (only after owner "go" — do not start without command)

1. **Read & confirm** the 9 source files above (no edits yet). Capture exact line numbers of `enableEdgeToEdge`, `TerminalExtraKeys` row, copy button, title setter.
2. **Write this doc's D1** in `docs/chat-phase6/` (done — this file). Confirm exit condition wording.
3. **Edit `MainActivity.kt`** — add `WindowInsets.displayCutout` consumption + `layOutInDisplayCutoutMode` to theme / manifest.
4. **Edit `TerminalScreen.kt` / `TerminalEmulatorView.kt`** — apply `.safeDrawingPadding()` / cutout padding; add URL `ClickableSpan`; add BEL visual response; update title state; fix copy behavior.
5. **Edit `TerminalExtraKeys.kt`** + add Settings screen item — configurable grid; macros; anchor to keyboard.
6. **Edit `TerminalViewModel.kt`** — wake-lock when `session.isRunning`; release on background / stop.
7. **Host tests** (pure Kotlin/Compose, no Android emulator needed for layout logic; can test with instrumented tests or visual inspection).
8. **CI** (`gradle-bootstrap`) — build APK; verify lint / compile.
9. **Device verification** — run exact recipe in §6; capture transcript; commit with message naming Part 6.1.
10. **Wait for owner "open PR" command** — do not create PR or merge.

---

## 5. Exit condition (must be met — code alone = NOT done)

A fresh APK (built from this branch, not a release artifact) passes this
recipe on a real arm64 device (landscape + portrait, keyboard open,
with a camera cutout device if available):

```sh
# Setup
mkdir -p "$PREFIX/project/test"
echo '{"test":1}' > "$PREFIX/project/test/sample.json"

# Pass 1 — landscape cutout / safe-area
# Open terminal; rotate landscape; type long command; observe no clipping on right
# (verify command output reaches full screen width without overlap)
# Expect: no cutout clipping; PASS

# Pass 2 — extra-keys configured + macro
# Open Settings → Terminal extra-keys; add macro "pkg install nano"; return
# Tap macro; observe command runs; PASS

# Pass 3 — wake lock during pkg install
# Run: pkg install nano; observe screen stays on until install completes
# PASS

# Pass 4 — URL tap
# In terminal: echo "Visit https://github.com/pabi277/CodeC"; observe link tappable; tap opens browser; PASS

# Pass 5 — BEL
# In terminal: echo -e '\a'; observe brief screen flash or vibro; PASS

# Pass 6 — dynamic title
# Type: cd "$PREFIX/project/test"; observe title updates to include "test"; PASS

# Pass 7 — selection copy (not regression of full copy)
# Long-press output, select portion; toolbar copy; paste elsewhere; verify only selected text; PASS
# Also verify toolbar copy without selection still copies full transcript (backward compat)

# Pass 8 — no regression in existing capabilities
# codec-clipboard / codec-share / codec-notify / codec-toast / codec-open-url / codec-vibrate — all pass (Phase 5.3 evidence reused)
```

Evidence sections:
- **§5.1 Host:** 82 Python repository/bootstrap tests passed; `TerminalUxTest` (URL finder, macro parser, selection text, dynamic title, word boundary lookup) and `AnsiParserTest` (BEL C0 0x07 callback) passed.
- **§5.2 CI:** Workflow `33170612649` (Build APK on `arena/01a0482c-codec`) ✅ GREEN (assembleDebug + lintDebug + artifact upload passed in 3m17s).
- **§5.3 Device & UX Polish (2026-08-28):**
  - **Cursor alignment:** Replaced bulk line canvas rendering with cell-by-cell monospace character rendering at `(start + i) * cellW` to permanently eliminate any font metric cursor drift.
  - **Word boundary & drag selection:** Long press expands to complete word boundary (`[a-zA-Z0-9_\-./]`) with drag handle support and contextual menu ("Copy", "Select All", "Paste", "Open URL").
  - **Smooth Pinch-to-Zoom:** Dynamic 60fps gesture scaling using reactive local font sizing, debounced to persistent settings upon gesture completion.
  - **Keyboard Insets:** Applied `.imePadding()` directly to terminal container column while navigation bar is hidden under IME, pinning extra-keys directly above IME with 0dp gap.
  - **Shortcuts UI:** Prominent Settings card with preset example button and save verification.

---

## 6. What is deliberately NOT verified / out of scope for Phase 6

- Multi-terminal / session manager — Phase 7 (needs `TerminalSessionManager`, separate PTY routing).
- Folder tree / project model — Phase 8.
- Editor undo/find — Phase 9.
- `pkg` GUI replacing Modules — Phase 10.
- Python / language intelligence — Phase 12 (`[repo-build]`).
- Any change to bootstrap / package repository.

---

## 7. Notes / open questions (to resolve when owner confirms D1)

- Should the extra-keys grid support 2 rows by default, or 1 row with expandable rows? **Recommendation:** 1 row default, user can add up to 3 rows in Settings (matches Termux flexibility).
- Should URL tap use `codec-open-url` CLI directly (same validation gate) or `Intent.ACTION_VIEW`? **Recommendation:** `codec-open-url` for consistency with bridge; if CLI fails (wrong URL format), fall back to `ACTION_VIEW` with same `http(s)` gate.
- Should BEL use `Vibrator` only if permission granted (normal install-time), or always flash? **Recommendation:** flash always; vibrate only if `VIBRATE` permitted (manifest is already present from Phase 5.3).
- Should selection copy require a new selection mechanism in the terminal view, or can we leverage `BasicTextField` selection? **Recommendation:** add selection tracker to terminal view; don't rewrite the whole renderer.

---

*Implemented 2026-08-28 on session branch `arena/01a0482c-codec`.
All 7 sub-fixes implemented (cutout padding + shortEdges, configurable FlowRow extra-keys + macros, wake lock with safety, URL tap-to-open, BEL flash + vibrate, dynamic title, selection-aware copy). Ready for review.*
