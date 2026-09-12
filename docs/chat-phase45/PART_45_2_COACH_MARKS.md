# CodeC Phase 45.2 — Coach marks on first arrival (three or four, then never again)

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S/M ·
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
