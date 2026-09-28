# Phase 69.3 — the strip beside the panel closes it, and a drag never types a key

**Date:** 2026-09-28. **Branch:** `arena/01a0e49f-codec` (tip `df6c654`).
**Status:** implemented; **Android CI ✅ GREEN** (runs at the bottom).
**No PR opened, nothing merged** — that needs the owner's explicit word.

Read first, asked second, coded third. Both reports arrived in one message after
the 69.2 delivery; both were read on this checkout before anything was proposed,
and the owner's answers were taken **before** any code.

## The two reports (owner, verbatim)

1. **The panel's strip** — *"The 3 ber open the editor but it have a gap side of
   that make it if user clicks the empty space it will close the 3 ber"*.
   ("3 ber" = the ☰ three-bar button — the owner's own spelling, the same word
   as his earlier *"the file ber can't close without opening any file"* and
   *"the full down ber"*.)
2. **The quick keys** — *"The quick keys are sensitive even i want to drag for
   other keys it's types which ever i am scrolling"*.

## What the investigation actually found

### 1. The strip: the tap was trusted to Material3, and that trust is the bug

- The ☰ opens `EditorSidePanel` through `ModalNavigationDrawer`
  (`EditorScreen.kt:1326`). The panel is `fillMaxWidth(SidePanelPlan.PANEL_WIDTH_FRACTION)`
  = **85 %** — the Phase 55 shot's number — so **15 % of the width shows the live
  editor** beside it. That is the "gap side of that".
- Phase 55 wrote the strip's tap off as a deviation: *"Material3's modal scrim
  owns the strip's tap, so Run costs one tap on the strip first."* That sentence
  was read out of Material3's source, never off a phone — the Phase 55 device
  round (P1–P10) was **never run**. The owner's report is the first device
  evidence, and it says the opposite: the tap in the strip closed nothing.
- So the app now owns it. `EditorSidePanel.kt`'s own comment
  (*"play has to stay tappable through it"*) is amended in place to say the
  tappable-through intent is retired — that was the intent the shot was read as,
  and it is not what the owner wants.

### 2. The quick keys: two thresholds that disagreed by 12 dp

- Three rows scroll horizontally: the **keys row** (`EditorKeyCap`), the
  **run-keys row** (`RunKeyCap`) and the **suggestion chips**
  (`SuggestionChipCap`). All three carried their **own copy of a 20 dp**
  "this was a scroll, not a tap" threshold (`EditorKeysRow.kt:219/399`,
  `SuggestionStrip.kt`).
- The row itself starts scrolling at the platform's **touch slop**
  (`ViewConfiguration.touchSlop` — Android's `scaledTouchSlop`, **8 dp** on a
  stock phone, already in pixels). So every drag between 8 and 20 dp — which is
  the short flick a thumb actually uses to reach the caps on the right —
  **scrolled the row AND typed the cap the finger started on**. That is the
  report, exactly.
- Two smaller accomplices: the arrows' **hold-repeat** starts at 150 ms
  (`HOLD_INITIAL_DELAY_MS`), so a slow drag out of ← → moved the caret before the
  drag was long enough to cancel anything; and the loop **discarded the up
  change** (`break` before measuring it), so a flick that lifted the finger
  where no move event had been delivered was judged on the last move instead of
  on where the touch really ended.
- `KeyGestureDetector.classify` — the "pure gesture state machine" 26.1
  advertised — is **dead code**: no production caller, only its own test. The
  real decisions were inline in the three `pointerInput` blocks, which is how
  three copies of one number survived.

## Owner decisions (verbatim)

| # | Question | Owner's answer |
|---|---|---|
| Q1 | The strip beside the panel — what should it be? | **A — Tap there closes it, strip stays undimmed (recommended)** — he accepted the stated cost: while the panel is open, the green play in that strip takes two taps (one closes, one runs) |
| Q2 | How strictly must a keycap tell a tap from a drag? | **A — The phone's own touch slop (~8dp) — a drag never types (recommended)** — past the slop the key is dead for that touch: no typing, no long-press popup, no arrow repeat |

## What changed

### 1. The strip closes the panel (Q1 = A)

- `SidePanelPlan.STRIP_WIDTH_FRACTION = 1f − PANEL_WIDTH_FRACTION` — the strip
  is the panel's **complement by construction**, so the panel can never grow
  over its own close zone nor leave a gap no tap answers. (Pinned:
  `SidePanelPlanTest`.)
- A box that wide, full height, `align(Alignment.CenterEnd)` (end flips with
  RTL, so it is the side the panel is not on), composed **after** the drawer in
  the wrapper `Box` — therefore **above** the drawer and above Material3's scrim,
  so nothing below can spend the tap first — and `indication = null`, so it
  draws nothing at all: the strip keeps the shot's undimmed, live-editor look
  and only the *tap* is ours.
- `clickable`, not a raw `pointerInput`, because it consumes the tap.
- It closes through the one law: `closeDrawer(DrawerCloseReason.SCRIM)` →
  `DrawerPolicy.shouldClose`, the same callback the ✕, back and a file row use.
- It exists only while the panel is **open or opening**
  (`drawerState.targetValue == DrawerValue.Open`, the 49.1 rule), so a closing
  panel leaves no dead strip behind.

**Consequences, stated plainly.** While the panel is open, that 15 % column has
exactly one job: close the panel. It therefore also covers the sliver of the top
bar and the bottom bar the panel leaves visible (a tap on RUN there closes the
panel first), and a drag started in the strip no longer reaches the drawer's
own swipe-to-close — the panel still closes on the tap, and drag-to-close still
works from the panel itself.

### 2. A drag never types (Q2 = A)

- One pure rule, `KeyGestureDetector.isScrollDx(dxPx, dyPx, slopPx)`: past the
  slop, with the horizontal travel at least the vertical, the gesture belongs to
  the row. The caller must not consume the change, so `horizontalScroll` still
  gets it and the row still scrolls.
- One slop for all three rows: `LocalViewConfiguration.current.touchSlop` — the
  platform's own, in pixels, the same value the row's scroller compares against.
  The 20 dp copies are gone (pinned: a test fails if any row keeps a threshold
  of its own).
- The **up counts**: every loop now measures `dx`/`dy` *before* it decides the
  touch is finished, so a flick is judged where the finger really ended.
- The arrows' **hold-repeat step** is guarded by `if (!isScroll)`, so a slow
  drag out of ← → never moves the caret, not even once.
- The vertical swipe layers are untouched: a vertical drag is still a swipe
  candidate, never a scroll.

## Tests

- `KeyGestureDetectorTest` **+3**: under the slop (jitter included, and exactly
  at the slop) still types; past it, in either direction, never does; a vertical
  drag stays a swipe candidate and level travel still belongs to the row.
- `KeysScrollCancelWiringTest` (**new**, 4): no row keeps a `20.dp` threshold of
  its own and all three read the platform's slop; all three ask the one rule
  (2 call sites in the keys row, 1 in the chips); the hold-repeat guard; and the
  up is measured before the touch is called finished.
- `SidePanelPlanTest` **+1**: the strip is the panel's complement (they add to
  1) and is a real strip.
- `DrawerWiringTest` **+1**: the strip's width, side, lifetime (`targetValue`),
  the `SCRIM` reason, nothing drawn (`indication = null`), and that it is
  composed **after** the drawer so it owns the tap.

Nine new host cases in total.

## Validation

| What | Where | Result |
|---|---|---|
| Source tests (host JVM) | CI `:app:testDebugUnitTest` on the tip `df6c654` | ✅ green (run 36378830783) |
| Android tests (instrumented) | — | none exist for this part; nothing claimed |
| Device evidence | — | **not claimed**: no round was run and none is asked for |

What only the owner's handset can confirm (recorded, not requested): that the
strip's tap now closes the panel on his phone — the whole reason this part does
not trust Material3's scrim any more — and that a drag across the row scrolls
without ever typing.

### CI

Two rounds, both lessons kept:

1. **Round 1 — red, two compile errors** (run
   [36378529137](https://github.com/pabi277/CodeC/actions/runs/36378529137) on
   `02988a1`, `:app:compileDebugKotlin`):
   - `Unresolved reference 'fillMaxHeight'` — the strip's box uses it and
     `EditorScreen` had never imported it;
   - `None of the following candidates is applicable` on `.toPx()` at all three
     slop sites — `ViewConfiguration.touchSlop` is a **Float in pixels**
     ("Distance in pixels a touch can wander before we think the user is
     scrolling"), not a `Dp`. Reading it directly is also the more honest
     number: the row's scroller compares raw offsets against that same value, so
     the cap now compares raw offsets too — one number, one unit.
2. **Round 2 — green** (run
   [36378830783](https://github.com/pabi277/CodeC/actions/runs/36378830783) on
   `df6c654`, 9m29s): the full suite plus this part's nine cases, zero error
   annotations, both APKs built — debug 26,025,252 B, release 6,798,268 B.

The sandbox has no JDK (`which java javac kotlinc` → empty, `JAVA_HOME` unset,
no `/usr/lib/jvm`), so CI is the executor of record, as it was for 68.1, 69.1
and 69.2. Every literal of every new pin was re-read against the live sources
before each push, and the four new wiring cases were simulated by hand — which
is why round 1's two failures were compile errors only, and why neither left a
behavioural surprise behind.

## Boundaries

No new dependency, permission, persistent preference, telemetry, screen, row,
button or setting. The panel's look, rail, slots, tree and the Phase 55 shot
numbers are untouched; the bottom bar is untouched; the drawer's close law
(47.1), back precedence (49.1) and the tour's `drawerOpen` fact are untouched.
The two-tap cost for Run while the panel is open is the owner's own choice
(Q1 = A), now paid on purpose instead of by accident. The vertical swipe
layers, popups, flicks, hold-repeat timings and the CodeC Keys grid are
unchanged.

**Stop point:** this part only. No PR, no merge, no `main` push without the
owner's explicit instruction.
