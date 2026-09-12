# CodeC Phase 45.2 — Coach marks on first arrival (three or four, then never again)

> **Status:** 🚧 **IMPLEMENTED** (2026-09-12, `arena/01a0955a-codec`) · CI pending · device round required · **Cost:** `[client-only]` · **Effort:** S/M ·
> **Owner row:** the second half of *"It has 0 guide features to give the user a
> real knowledge how to use the app, user don't know where should they change the
> project or file and the tap to the open down side of the keyboard"* — plus the
> owner's 2026-09-12 choice: **both layers**.

## What ships

A spotlight overlay that highlights **one real control at a time** on the screen
the user is already on, at most twice per surface, exactly once in the app's
life. No library: a `Box` over the screen, a `Canvas` that punches a rounded
hole at the anchor rect, a tooltip card beside it, and one tap to advance.

## The steps (content is the deliverable)

| Surface | Anchor | Title / body | Why this one |
|---|---|---|---|
| Editor | the ☰ button (`EditorScreen.kt:1055-1064`) | **Your files** — "Tap here for the file tree. The project name at the top switches projects." | The owner's exact words: users cannot find where to change project or file |
| Editor | RUN ▶ (the toolbar's run action) | **Run your code** — "Compiles and runs. The result opens at the bottom." | The 30-second loop (`PHONE_UX_ANALYSIS.md` §4) |
| Editor | the "Show tabs" handle (`MainActivity.kt:1148-1191`) | **The tabs are here** — "They hide while you type. Tap or swipe up to bring them back." | The owner's exact words: *"the tap to the open down side of the keyboard"* |
| Packages | the first install card (`ModulesScreen.kt:290-315`) | **One-time download** — "Adding a language downloads once. Keep CodeC open while it finishes." | Phase 44's teaching moment, at the point of action |
| Terminal | the status chip (`TerminalScreen.kt:299-310`) | **What it is doing** — "starting / downloading / running. If it says downloading, don't close the app." | Makes 44.1's chip legible |

**Cap: two per surface, five total.** A third mark on one screen is a tour, and
tours get skipped — that is the whole finding behind coach marks.

## Design

### 1. Pure plan (the house pattern)

```kotlin
// ui/guide/CoachMarkPlan.kt  (pure, host-tested)
enum class GuideSurface { EDITOR, TERMINAL, PACKAGES }
data class CoachStep(val id: String, val anchorId: String, val title: String, val body: String)
data class ChromeState(val navHandleVisible: Boolean, val runButtonVisible: Boolean,
                       val drawerButtonVisible: Boolean, val installCardVisible: Boolean,
                       val statusChipVisible: Boolean)
object CoachMarkPlan {
    fun stepsFor(surface: GuideSurface, chrome: ChromeState): List<CoachStep>
    fun nextUnseen(surface: GuideSurface, seen: Set<String>, chrome: ChromeState): CoachStep?
    fun markSeen(seen: Set<String>, stepId: String): Set<String>
    /** A surface whose anchors are all hidden returns empty and is NOT marked seen. */
    fun canShow(surface: GuideSurface, chrome: ChromeState): Boolean
}
```

The rule that makes it safe: **`nextUnseen` only ever returns a step whose
anchor the caller says is visible.** A hidden tab bar therefore produces no mark
*and* no "seen" entry, so the mark appears the next time the handle is actually
on screen. That single rule kills the worst coach-mark failure mode (pointing at
nothing).

### 2. The Android edge

- **Anchors:** each highlightable control publishes its bounds via
  `Modifier.onGloballyPositioned { … }` into a small `AnchorRegistry`
  (a `mutableStateMapOf<String, Rect>` remembered at the screen level). No
  reflection, no `findViewsWithText`, no accessibility-node walking.
- **Overlay:** a `Box(Modifier.fillMaxSize())` composed *after* the screen
  content, drawing a 60 %-alpha scrim with a rounded-rect hole
  (`BlendMode.Clear` on a `Canvas` layer), plus a `Card` positioned above or
  below the hole depending on free space (a two-line pure function
  `tooltipPlacement(anchor, screen)`, host-testable).
- **Dismissal:** tap the hole → the control's own action runs *and* the mark
  closes (so the user's first ☰ tap is the lesson). Tap anywhere else → next
  step or close. Back → close (and see 49's `BackRouter`: a coach mark is an
  in-app open surface, so back closes it before anything else — that precedence
  is pinned in `BackRouterTest`, not left to chance).
- **Persistence:** `coach_marks_seen_csv` in `SettingsManager` (a CSV of step
  ids, the same shape the codebase already uses for list-ish prefs). Readers:
  the three screens. **Reset tips** in Settings clears it (45.1 §3).

### 3. What it must never do

- Never show while a modal sheet, dialog, the crash overlay, the safe-mode
  banner, or the 44.1 setup gate is the foreground surface — a coach mark over a
  dialog is unreadable and rude. `canShow` takes that as an input too.
- Never appear twice for the same step id, even across process death.
- Never block the control it is teaching (the hole is tappable, the scrim is not
  a full-screen click trap).
- Never ship copy that names a control that moved: the vocabulary pin in
  `GuidePlanTest` covers the slides; `CoachMarkPlanTest` pins the `anchorId` set
  against the same allow-list.

## Exit condition

```text
1. First arrival at the Editor: at most two marks (☰, RUN ▶); tapping the hole
   performs the action and closes the mark.
2. The "Show tabs" mark appears only when the handle is actually visible (i.e.
   with the keyboard up in the editor), and not before.
3. First arrival at Terminal and at Packages: their marks, once each.
4. Second and later arrivals: nothing. Kill the app mid-mark, relaunch: it may
   re-show the same step once, and never a step already marked seen.
5. Settings → Help & guide → Reset tips: all marks return; slides return; no
   project/file/setting data is touched.
6. With any dialog or sheet open: no mark appears, and the pending step is not
   consumed.
PASS = all six.
```

## Tests (plan)

- `CoachMarkPlanTest`: `stepsFor` per surface ≤ 2; `nextUnseen` returns only
  visible-anchor steps; an all-hidden surface returns empty **and** is not
  recorded as seen; `markSeen` is monotonic; an unknown step id is ignored;
  the anchor-id vocabulary pin.
- `TooltipPlacementTest`: above/below choice for an anchor at the top, middle
  and bottom of a 640×360 dp box, and for a landscape box.
- `CoachMarksPersistenceTest`: the CSV round-trips, defaults empty, and has an
  out-of-store reader.
- `BackRouterTest` (Phase 49) gains the case *coach mark open → `CloseCoachMark`
  before anything else* — added here, executed there.

## Sources (record)

- CodeC 2026-09-12: `EditorScreen.kt:1055-1064` (☰), `MainActivity.kt:1148-1191`
  (the reveal handle), `ModulesScreen.kt:290-315` (the install card),
  `TerminalScreen.kt:299-310` (the status chip), `SettingsManager.kt` (key +
  reader pattern), `SettingsKeysHaveReadersTest.kt`.
- `docs/PHASE44_50_UX_RESEARCH.md` §3.1-3.2 (dependency survey; why the plan is
  pure and hand-rolled).
- Behaviour-only precedent: Acode/VS Code first-run highlights (clean-room,
  `rule.md` §6).

## Deferred / rejected with reasons

- **A spotlight library** (`compose-intro-showcase`, Apache-2.0) — rejected: a
  dependency for ~120 lines, and its anchor model assumes one screen.
- **More than two marks per screen** — a tour; measured to be skipped.
- **Contextual tips tied to state** (e.g. "you have unsaved changes") — that is
  the status bar's job, and it re-nags by definition.
- **Per-feature "what's new" marks after each release** — a support channel, not
  a guide; `docs/RELEASE_NOTES.md` already covers it.

---

## Implementation (2026-09-12)

Five marks, one per anchor, no library: a `Canvas` that draws a scrim with a hole,
a `Card` placed by pure maths, and one tap to finish.

### The five marks as built

| Surface | Anchor id | Title | Body | Published by |
|---|---|---|---|---|
| EDITOR | `editor_drawer` | Your files | Tap here for the file tree. The project name at the top switches projects. | `EditorScreen`'s ☰ `IconButton` |
| EDITOR | `editor_run` | Run your code | Compiles and runs. The result opens at the bottom. | `EditorScreen`'s RUN ▶ `Row` |
| EDITOR | `nav_handle` | The tabs are here | They hide while you type. Tap or swipe up to bring them back. | `MainActivity`'s `EditorNavRevealHandle` |
| PACKAGES | `packages_card` | One-time download | Adding a language downloads once. Keep CodeC open while it finishes. | `ModulesScreen`'s first card of the first section |
| TERMINAL | `terminal_chip` | What it is doing | Starting, downloading or running. If it says downloading, do not close CodeC. | `TerminalScreen`'s status-chip `Row` |

Caps pinned: title ≤ 24 chars, body ≤ 90, and the coach copy goes through the same
`GuideVocabulary` pin as the slides (a mark that names a renamed control fails the
build).

### The visibility law, implemented

`GuideAnchor.modifier(id)` publishes `boundsInWindow()` from
`onGloballyPositioned` and — the half that matters — **withdraws the id in
`DisposableEffect.onDispose`**, so a control that leaves composition is not
"visible". `GuideAnchorRegistry.visibleIds()` feeds
`ChromeState.of(visibleAnchors, blockedByForeground)`, and
`CoachMarkPlan.nextUnseen` returns only steps whose anchor that state reports
visible. A hidden tab bar therefore yields no step *and* no `seen` entry, and the
**Show tabs** mark arrives on the first arrival where the handle is really on
screen (45.2 exit 2). Empty rects are never published.

### Never a trap, never a wall

- The scrim is a `Canvas`: drawing only, so it takes no pointer input and the
  control under the hole keeps working.
- One `pointerInput` layer above it uses `awaitFirstDown(requireUnconsumed =
  false)`: a tap **inside** the hole is left unconsumed (the real control performs
  its own action) and the mark closes; a tap **outside** is consumed and closes it
  too. `GOT IT` on the card and `BackHandler` are the other two exits.
- The card never blocks the control it teaches, and the overlay is composed in a
  root `Box` **above** the `Scaffold` — the handle it spotlights lives in the
  scaffold's `bottomBar`, so an overlay inside the content column could never
  reach it.
- Two marks per arrival (`MAX_PER_ARRIVAL`, the counter resets on destination
  change); a step is marked seen only when it was actually shown, so a blocked or
  all-hidden arrival consumes nothing.

### Deviations

1. **Four rectangles, not `BlendMode.Clear`.** A clear-blend hole needs
   `graphicsLayer(compositingStrategy = Offscreen)`; four scrim rects plus a
   rounded white stroke need nothing exotic, cannot fail on a driver that ignores
   the offscreen layer, and read the same on a phone.
2. **A process-wide bridge, not a CompositionLocal.** `GuideAnchorRegistry`
   follows the codebase's idiom (`SetupNoticeBridge`, `EditorChromeState`,
   `IncomingImportBridge`) — six screens publish without a parameter threaded
   through five signatures. It is snapshot state, so publishing recomposes the
   overlay.
3. **The card's height is an estimate (150 dp).** Measuring the card and then
   placing it is a layout feedback loop; `TooltipPlacement` is pure and pinned for
   anchor-at-top / middle / bottom, both horizontal clamps, a landscape box, a card
   wider than the screen, and a degenerate zero-size anchor.
4. **"Blocked" is what `MainActivity` can see:** the exit survey, safe mode, and a
   Phase 44 stage that is *actually moving* (DOWNLOADING / VERIFYING / EXTRACTING).
   CHECKING deliberately does not block — it is the tracker's startup value, and
   blocking on it would mean no mark ever appears on a fresh phone. A screen's own
   dialogs and sheets are separate windows: they cover the overlay, and the pending
   step is not consumed while they are up (exit 6).
5. **`BackRouter` precedence is a fact, not yet a law.** Back closes the mark
   before the exit survey because the overlay's `BackHandler` is composed later;
   Phase 49 turns that into `BackRouterTest`'s `CloseCoachMark` case as planned.

### Tests (22 cases + 10 + the wiring half)

`CoachMarkPlanTest` 12: the plan's shape and order · the cap · copy caps + the
vocabulary pin · only visible anchors are returned · an all-hidden surface marks
nothing seen · the Show-tabs mark waits for the handle · a blocked screen
suppresses without consuming · the seen set suppresses a surface permanently ·
`markSeen` monotonic and ignores unknown ids · the CSV round-trip (order, blanks,
garbage, null) · `ChromeState.of` · routes → surfaces.
`TooltipPlacementTest` 10: below / above / clamped / both horizontal clamps /
landscape / card wider than the screen / degenerate anchor / purity.
`GuideWiringTest` (shared with 45.1) pins the Android half: every anchor has a
publisher on the control it names and no id is left unpublished · withdrawal on
dispose · the overlay is above the scaffold and asks the plan · the tap layer
consumes only outside the hole · the blocked rule names the exit survey, safe mode
and the three working stages · no foreign import in any `ui/guide` file · no
`showcase`/`intro`/`onboarding`/`tooltip` entry in `libs.versions.toml` · the
guide is not a navigation route · Phase 44's setup surfaces are untouched.
