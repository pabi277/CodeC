# Phase 103 — Terminal selection + scroll rebuild

**Status:** 🚧 IMPLEMENTED — device pass owed (owner: "Start Phase 103", 2026-10-14).
Commit 1 = 103.1 pure selection layer; commit 2 = 103.2 view wiring (handles, toolbar,
Copy law) + scroll physics. The series moves to Phase 104; see `## Implementation`.

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

## Implementation

**Shipped 2026-10-14 on `arena/4baff5b7-codec` (owner: "Start Phase 103"), in two
commits in order.** Commit 1 = `f5f3748` (+ fix-up `7c4b9f1`) = 103.1 pure selection policy (owner split
decision: the Compose layer was not hand-split out of the interleaved gesture code).
Commit 2 = 103.1 wiring + 103.2 scroll physics, plus the Copy-law edit. Part records:
[`PART_103_1_SELECTION.md`](PART_103_1_SELECTION.md) §Implementation,
[`PART_103_2_SCROLL_PHYSICS.md`](PART_103_2_SCROLL_PHYSICS.md) §Implementation.

**Built:** `SelectionModel` (pure: begin/drag/selectAll/clamp/spanOnRow/extract/cellAt/
screenRowOf/handleAnchorX/edgeScrollPx); the buffer-absolute selection anchor
(`TerminalSnapshot.historyBase`, `absRowOf`) and the soft-wrap join
(`TerminalLine.wrapped`); two 48 dp handles and a floating Copy / Select all / Paste
toolbar; Copy writes the selection or is disabled, and the transcript fallback is
deleted; rest-on-lift (`ScrollMath.rest`) that fixes the first-open blank band;
fling/inertia (`ScrollMath.planFling`/`flingOffset`, reduced-motion instant jump);
the proportional scrollbar (`ScrollMath.thumb`).

**Host:** 130 / 0 on the terminal suites; 154 / 0 with the source scans (`TouchTargetTest`,
`TokenAdoptionTest`, `TypeAdoptionTest`, `IconRoleTest`, `MotionWiringTest`). Compose view
and screen: CI `Build APK` only. CI history: `38506463779` red on `f5f3748` (test-compile
import) → fixed by `7c4b9f1`; `38506987604` red on the first 103.2 tip (one motion pin) →
fixed by `d3b6f08`; **`Build APK` ✅ GREEN `38507532428` on tip `d3b6f08`** (the full 103.1 + 103.2 tree, build=success). Device pass owed.

**Deviations (all recorded in the part docs):** fling on `withFrameNanos` rather than
`RenderPump.kt` (frame-paced, no new ticker); `ScrollWiringTest`/`SelectionWiringTest`
built as `TerminalGestureWiringTest`; Share's transcript fallback predates the phase and
is not changed here.

**Device pass required (owner):** the exit condition in this README, plus the
PART_103_2 device list. Phase 103 is not closed until the owner's device pass is
recorded.

