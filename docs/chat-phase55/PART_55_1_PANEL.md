# PART 55.1 — The panel and the rail

> **Status:** 🚧 IMPLEMENTED (2026-09-22) · **Effort:** M · **Cost:** `[client-only]`
> **Files:** `ui/components/EditorSidePanel.kt` (new), `ui/editor/SidePanelPlan.kt` (new, pure),
> `ui/screens/EditorScreen.kt` (the drawer host), `ui/editor/EditorChromeState.kt`, `ui/guide/CoachMarks.kt`

---

## 1. Research first: what the shots actually show

Read in this session from `docs/spck-ui/Screenshot_20260922_124049_Spck Editor.jpg`
(and the four other panel shots), recorded in full in
[`chat-phase54/PART_54_1_SHOTS.md`](../chat-phase54/PART_54_1_SHOTS.md):

- the panel's right edge sits at ≈ **85 % of the width** (≈ x 611 of 720 viewed px of a 1080×2340 screen);
- the strip beside it is **the live editor** — the green play triangle, the tab-row icon, five
  lines of the same coloured HTML, `UTF-8`, then `>>` — and it is offset differently in every
  shot, i.e. it scrolls with the editor behind the panel;
- the rail: five icons on one row, the **selected one underlined in white**;
- the section label sits under the rail, uppercase and letter-spaced, with the section's own
  controls right-aligned on that row (`...` for Navigation, `⌕` for Repository, a set of glyphs
  for Files and Search);
- **no bottom bar in any shot** — and that absence is **not** copied (the owner's reversal; see
  [54.3 §1](../chat-phase54/PART_54_3_OPEN.md)).

Rejected in writing: the drawings (`docs/spck-ui/0*.png`), the store art, and the reading
“56 removes the bar”.

## 2. What was built

`ui/editor/SidePanelPlan.kt` — pure Kotlin, host-tested, no Compose and no Android import:

```text
RAIL            = [NAVIGATION, FILES, SEARCH, REPOSITORY, RESERVED]   ← the shot's order
WIRED           = [NAVIGATION, FILES, SEARCH, REPOSITORY]             ← the five minus the reserved slot
PANEL_WIDTH_FRACTION = 0.85f                                          ← the shot's edge
OPEN_GESTURE_DP = 32f                                                 ← the edge-swipe, kept from the drawer
DEFAULT_PANEL   = NAVIGATION
```

`ui/components/EditorSidePanel.kt` — the shell:

```text
Surface(fillMaxHeight, fillMaxWidth(0.85f), shadowElevation = SHEET)
 ├─ Rail      : five slots, each 48 dp tall, icon = 24 dp token, selected underlined (2 dp × 24 dp)
 ├─ Divider
 └─ Box(weight 1f) → the selected slot
```

Decisions worth naming:

- **Reuse before drawing (Phase 50's icon law):** the folder is the app's own
  `SpckIcons.FolderLine`, the branch is the app's own `SpckIcons.GitBranch`, the guide cell is
  `SpckIcons.BookLine`. Only the outline triangle, the magnifier, the gear, Terminal, Download,
  Folder and Code come from `Icons.Filled`/`Icons.Outlined`, all of which the app already uses.
- **The reserved slot is drawn and cannot be tapped** (`clickable(enabled = wired)`, tint at 35 %).
  The owner reserved it for AI; a slot that opened *anything* would imply a screen CodeC does
  not have.
- **The slot content owns the height under the rail** (`Box(weight(1f))`), never a second
  `fillMaxSize` — a Column child asking for the whole screen under a 48 dp rail pushes its own
  content below the fold.
- **Tokens, per Phase 50.1 law 2:** every gap, size and radius in the new panel file goes through
  `CodecTokens.space/icon/radius/elevation`; the only raw values are two 1 dp hairlines (a
  thickness, which the same law exempts).

## 3. The host: `EditorScreen`'s ☰

The drawer host is unchanged in every way that matters:

```kotlin
ModalNavigationDrawer(
    drawerState = drawerState,                 // the same state, the same gestures
    gesturesEnabled = …,                       // Phase 25.2's rule, verbatim
    drawerContent = { EditorSidePanel(...) }   // ← was EditorProjectDrawer(...)
) { …editor… }
```

That is deliberate. `drawerState` still drives `DrawerPolicy.shouldClose`, `BackRouter`
(`BackAction.CloseEditorDrawer`), `EditorChromeState.setDrawerOpen` and the coach marks'
`drawerOpen` fact, so the tour, the chrome lock and the back precedence are untouched by this
phase.

### The drawer beats belong to the panel now

The tour's first four beats — ☰ → the project row → the demo pick → `app.py` — live **inside**
the tree. With the panel defaulting to Navigation, those anchors would not be composed and the
tour would wait on an anchor nobody opened (then pass the beat after the 20 s stall guard — a
lesson lost).

Fix, and it stays pure: the guide host publishes the beat that is due
(`EditorChromeState.setGuideBeat`, set in `GuideCoachMarks` from `CoachMarkPlan.nextStep`), and
the editor asks the plan's own flag:

```kotlin
val guideBeat by EditorChromeState.guideBeat.collectAsState()
LaunchedEffect(guideBeat) {
    if (CoachMarkPlan.step(guideBeat)?.inDrawer == true) sidePanel = RailPanel.FILES
}
```

`CoachMarkPlan.step` now takes `String?` — “no beat is due” is a null. A tap by the user always
wins afterwards: the rule only fires when the guide is asking for an in-drawer beat.

## 4. Deviations from the shots (written down, not hidden)

1. **A tap on the strip closes the panel** (Material3 modal law: the scrim owns that tap), so
   play is one tap away rather than zero. The strip stays fully visible and undimmed, and the
   panel never covers it. Making the strip *interactive* means replacing `ModalNavigationDrawer`
   with a hand-rolled overlay and re-implementing the drawer's state machine (back precedence,
   the chrome lock, the tour's `drawerOpen` fact, the edge swipe). That trade was **put to the
   owner** rather than taken silently.
2. **The `...` on the NAVIGATION label is not drawn.** In the shot it is the section's overflow;
   its contents are in no shot and no doc, and the roadmap forbids inventing a menu
   (54.3 §4 row 9).
3. **The bottom bar is still under all of this.** The shots have none; the owner kept CodeC's.
   The panel is an addition, and 55 does not touch the bar at all — 56 removes exactly one option.

## 5. Verification

- `SidePanelPlanTest` (13 cases): rail order, four wired + one reserved, the width fraction, the
  gesture threshold, the card, and the refused labels.
- `SidePanelWiringTest` (8 source pins): `EditorSidePanel(` is composed, `files = {` exists,
  `EditorProjectDrawer(` still runs inside it, the rail comes from the plan, the reserved slot
  falls through to `Unit`, and `FlatBottomBar` + `EditorNavRevealHandle` + `NavBarPolicy.hideNavBar`
  all survive.
- **Device pass required:** [P1-P4](DEVICE_ROUND.md).
