# Phase 103 — Terminal selection + scroll rebuild

**Status:** 🚧 IMPLEMENTED on `arena/3f8a6afe-codec` (2026-11-22) — host harness 85/85 and
**Build APK ✅ GREEN on `df3b285`** (run [`44999216380`](https://github.com/pabi277/CodeC/actions/runs/44999216380):
host unit + screenshot tests, debug + release assembly, APK set check, lint). **The owner's
device round is still owed** (see `## Implementation` at the end). **Owner rows:** *"partial text copy not working"* and
*"first open scroll up, empty space"* (device reports, this session) → *"yes"* to the
Termux-parity program → first-quick-win pick: **selection + scroll**. Series roadmap:
[`PHASE103_108_ROADMAP.md`](../../../roadmaps/PHASE103_108_ROADMAP.md); research:
[`TERMINAL_PYTHON_GUI_PACKAGES_RESEARCH_20261014.md`](../../../research/TERMINAL_PYTHON_GUI_PACKAGES_RESEARCH_20261014.md) §2–3.

## Why this phase exists (evidence, read this session @ `5cbc1c0`)

- **Selection cannot extend.** `TerminalEmulatorView.kt` long-press selects exactly one
  word (:361) and immediately opens the popup menu (`menu = true`, :364), so a drag has
  nothing to extend; there are no drag handles and no edge auto-scroll anywhere in the
  app. The owner's "select some text, copy" flow is therefore structurally impossible,
  not merely buggy.
- **Copy lies by fallback.** The toolbar Copy (`TerminalScreen.kt:334`) falls back to
  the *whole transcript* when the selection path yields nothing — the exact behaviour
  behind "it copied everything". (The underlying extractor, `selectedText()` at
  `TerminalEmulatorView.kt:783`, is correct and already tested; the gesture layer above
  it is what fails.)
- **The blank band.** The sub-pixel scroll remainder (`scrollSubPx`) is never reset on
  finger-lift, so a fresh terminal can rest shifted ~1 row with an empty band at the
  top — the owner's first-open report. Termux discards its scroll remainder on
  finger-up; CodeC keeps Termux's bug-shaped opposite.
- The Termux reference behaviour (draggable handles + floating Copy/Paste toolbar +
  selection anchored through scrolling output + soft-wrap-joined extraction,
  `TextSelectionCursorController`/`TerminalView.getSelectedText` in termux-app) was
  read source-level in the 2026-10-14 audit and is the behavioural spec — re-implemented
  clean-room in Compose, never copied (GPL, `rule.md` §6).

## Parts

| Part | Title | One-line shape |
|---|---|---|
| [103.1](PART_103_1_SELECTION.md) | Selection: handles, floating toolbar, joined copy | Pure selection model + Compose handles/toolbar; copy = selection or nothing |
| [103.2](PART_103_2_SCROLL_PHYSICS.md) | Scroll physics: rest position, fling, scrollbar | Pure scroll-math policy (remainder/fling/anchor) + view wiring |

103.1 and 103.2 touch the same view file; they land as two commits in this order
(selection first — it is the owner's visible bug), each independently CI-green.

## Standing laws honoured

Pure Android-free policy + thin Compose wiring, with wiring-pin tests (house §6);
host tests are the record, CI (`Build APK`) is the executor; **device pass required**
— both bugs are touch behaviours only a handset can accept. No new dependency,
permission, DataStore key or Settings control. Phase 40.5 colour law: handles/toolbar
use existing roles/tokens only.

## Exit condition (the phase, not the parts)

On the owner's device: (1) long-press a wrapped line, drag the end handle, Copy —
the clipboard holds exactly the dragged span with soft wraps joined; (2) open the app
fresh — no blank band; fling scrolls with inertia and stops at the true top/bottom;
(3) selection made mid-`cat` stays on its text while output scrolls. Then this README
gains its `## Implementation` record and the series moves to Phase 104.

## Implementation (2026-11-22, `arena/3f8a6afe-codec`, branch base `e06851a`)

**State: implemented on the branch, host-verified and CI-green (`df3b285`); the device
round is NOT run.** Nothing here is an acceptance claim: every exit condition on the phone
is the owner's to report.

### What landed

| File | Change |
|---|---|
| `ui/terminal/TerminalSelection.kt` *(new, pure)* | `SelectionPoint`/`SelectionRange` (normalized, inclusive), `TerminalSelection` with **logical ends** (`anchorEnd`/`cursorEnd`) so a handle grabbed at a cell stays at the finger through an inversion, `SelectionModel` (`wordAt`, `drag`, `growByScroll`, `selectAll`, `remembers`, `extract`/`extractLine`), `TrimMath`, `SelectionAutoScroll`, `SelectionHandles`. Imports neither `android` nor `androidx`. |
| `ui/terminal/TerminalScroll.kt` *(new, pure)* | `rest`, `RowThumb` mapping, `Scrollbar` geometry, `flingPx`, `ScrollMotionPolicy.flingSteps(animatorScale)`. |
| `ui/terminal/TerminalBuffer.kt` | `TerminalLine.softWrapped`; `TerminalSnapshot.lineAt(extY)` (one row lookup for the whole app); `TerminalSnapshot.scrollbackGeneration`; `TerminalLine.columnIsContinuation`. |
| `ui/components/TerminalEmulatorView.kt` | **one** `detectTerminalGestures` `awaitEachGesture` loop replacing the two racing detectors; handles drawn *and* hit-tested from `SelectionHandles`; floating `SelectionToolbar` (Copy / Select all / Paste / ⋮); fling on the frame pump's interval; fading scrollbar; `animatorScale` parameter. |
| `ui/screens/TerminalScreen.kt` | Copy is `copyText(context, activeSelection.orEmpty())` — **the transcript fallback is deleted, not kept as a net**; `animatorScale` read once from `ValueAnimator.getDurationScale()`. |
| `res/values/strings.xml` | `terminal_select_all`, `terminal_more`, `terminal_copy_all`, `terminal_copy_empty` (the screen's "nothing to copy" toast). |

### The three defects the phase's own tests caught (they are the reason those tests exist)

1. **The selection axis was modelled backwards.** `extY = index − scrollbackCount`, so
   a scrollback **trim** moves no surviving cell (both terms drop by one) while a row
   **pushed off the live screen** moves every cell down by one. The first model shifted
   by the trim count and would have dragged every selection off its text mid-`cat`.
   The single derivation now lives in `TrimMath.contentShift`; the view calls it with
   both counters and applies `.shifted(contentShift)`.
2. **A long-press became a one-cell caret.** The gesture loop entered `SELECTING` on the
   long-press frame and emitted a move on the *next* one, clamping the freshly grabbed
   word to the finger. Extension now waits for real travel (`> TOUCH_SLOP_PX` from the
   press point); likewise a handle grab no longer snaps the handle to the finger.
3. **The scrollbar's `-rows.roundToLong()…coerceIn(...)` negated *after* the clamp** — the
   positive row count clamped to 0 and then negated, so every track position mapped to
   "live". Fixed with `(-rows).coerceIn(...)`, kept as a comment so it is not
   re-introduced.

**A refused change worth recording: the long-press haptic.** The rewrite added
`performHapticFeedback(LONG_PRESS)` on long-press; it was removed because the terminal is
one of the app's two animation-free zones (Phase 36/48) and `HapticWiringTest` pins it
haptic-free (Phase 51.4). `HapticMoment.DRAG_STARTED` was considered and also refused — it
would make the view name a `HapticMoment`, which the same pin forbids. The long-press
answers by growing a word under the finger instead.

Also fixed while re-reading the rewritten view: `SelectionToolbar` used
`Color(0xFF2B2B2B)` for its surface (**Phase 40.5**) — now `theme.topBarBackground` with
`ButtonDefaults.textButtonColors(theme.foreground/…, disabled = 38 %)`, and the view's
four unused colour literals were deleted; the handle hit test/draw used the normalized
`start`/`end` instead of the logical ends, so grabbing the handle under the finger could
move the other one.

### Verification actually run

* Host harness (jdk4py + kotlinc 2.4.20, real production sources): **85 cases, 85
  passed / 0 failed** — `SelectionModelTest` 31, `SelectionExtractTest` 11,
  `ScrollRestTest` 21, `ScrollMathTest` 6, `SelectionScrollWiringTest` 16.
* The Compose view is **not** compiled by that harness (no Compose on the host). Its
  correctness is carried by (a) a symbol/unused-import/brace/locals-before-use audit of
  the rewritten file and (b) the 16 source pins, and its real proof is CI + the device
  round.
* The **pre-existing** suites that read this view were run too, since a rewrite can break
  a neighbour: `TerminalChromeTest` 7, `HapticWiringTest` 12, `MotionWiringTest` 7,
  `TokenAdoptionTest` 6, `TouchTargetTest` 2 — **34 passed / 0 failed**, plus the host
  suites above for **119 green cases** in total. (`HapticWiringTest` is what rejected the
  long-press haptic below.)
* **CI (`Build APK`, run [`44999216380`](https://github.com/pabi277/CodeC/actions/runs/44999216380)
  on `df3b285`): ✅ GREEN** — host unit + screenshot tests, `assembleDebug`,
  both release assemblies, the APK-set check and `lintDebug` all pass.
* **Not run, not claimed:** every device row in the exit condition.

### The two CI rounds before the green one (both were real, both recorded)

1. **`44997918882` — `:app:compileDebugKotlin` FAILED, two errors**, both introduced by
   the rewrite and both invisible to the host harness because it cannot compile Compose:
   `ButtonDefaults` was imported **twice** (Kotlin reports it as an ambiguous name), and
   `hitHandle` referenced `rows`, which it never had as a parameter. Fixed in `d81a22e`.
2. **`44998194404` — compile and unit tests passed, `:app:lintDebug` FAILED:** `NewApi`,
   `ValueAnimator.getDurationScale()` at `TerminalScreen.kt:487` is **API 33** and
   `minSdk` is 24. Replaced with `Settings.Global.ANIMATOR_DURATION_SCALE` — the platform
   switch the remove-animations accessibility setting zeroes on every version, and the
   exact value `MotionPolicy.inputFor` already reads, so the terminal's fling now degrades
   on the same signal as the rest of the app. The wiring pin asserts both halves. Fixed in
   `df3b285`.

### Open items for the device round

1. Long-press a wrapped URL, drag the end handle, **Copy** → the clipboard holds the
   dragged span with the soft wrap joined (no `\n` at the fold).
2. Fresh open: no blank band above the first row; a fling decelerates and stops at the
   true top/bottom (`TerminalScroll.rest` on lift and at the end of every fling).
3. A selection made while `cat` streams stays on its own text as output scrolls, and is
   **dropped, not repainted**, if its rows are overwritten.
4. Handles are reachable at 48 dp on a phone and do not sit under the keyboard.
