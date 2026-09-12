# CodeC Phase 45.2 — Coach marks on first arrival (three or four, then never again)

> **Status:** 🚧 **IMPLEMENTED THROUGH ROUND 3** (2026-09-12, `arena/01a0955a-codec`) · CI ✅ GREEN rounds 1-3 (`34698914219` tip `3c597b2`; `34704379023` tip `acadaee`; `34707337429` tip `0fcb3b6`) · device round required (NOT run — **G1-G28**) · **Cost:** `[client-only]` · **Effort:** S/M ·
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

---

## Round 2 (2026-09-12) — the owner ran round 1, and 45.2 became ONE TOUR

### The report, verbatim

> *"Working but some problem the guided box are not consistent with flow like /
> Not showing the full box guide at one and you didn't add all / Ok listen remove
> the next option only the guide will show click the option where showing the
> guide to the next / Ok now make it like demo_flask is always present so whatever
> user chose to start guide the user to start the demo project from the editor
> only"*

and, asked what the flow should be:

> *"☰ bar → change the project folder to demo_flask → selected app.py → run →
> install → python → it will open the flusk web → close → tap to reveal the
> keyboard below option → then a small tour of package and terminal"*

### Diagnosis first: four causes, all in round 1's own code

1. **A fresh install blocked every box.** `blockedByForeground` counts Phase 44's
   DOWNLOADING/VERIFYING/EXTRACTING as "someone else owns the screen" — right in
   itself, but on a first run that is most of the session, so almost nothing could
   appear while the download ran.
2. **`MAX_PER_ARRIVAL = 2`** capped the editor at ☰ + RUN ▶, the handle beat needs
   the keyboard up, and Packages/Terminal need the user to wander there. *"you
   didn't add all"* was the cap **and** the per-surface filter, not missing anchors.
3. **The card height was a 150dp estimate** inside the placement maths, so a taller
   card fell into the "neither above nor below fits" clamp and was pushed over its
   own hole — *"Not showing the full box guide at one"*.
4. **A box could be cut behind a dialog or a closed drawer.** The scrim draws in the
   activity window; an `AlertDialog` is a window of its own, and M3 keeps a closed
   drawer's rows laid out (so they still publish rects). Both give a hole the user
   cannot see — *"not consistent with flow"*.

### The owner's four decisions (asked, answered, recorded)

| Question | Answer |
|---|---|
| How far does "remove the next option" go? | *"Keep the 5 slides, boxes lose their button … tap the highlighted control to advance, **even tap outside will not end that box**"* → 45.1 untouched; the card's GOT IT is gone and outside taps are inert |
| What should one run cover? | The dictated ten-beat flow above → the per-arrival cap and the surface filter are **deleted** |
| demo_flask as the start | Option C: the tiles keep their own starter project, **the tour runs on demo_flask** (beats 2-3 walk the user there) — and demo_flask is **always present** |
| Editor-only vs Phase 44's terminal-first divert | *"Don't understand you do as you like"* → **agent's call: keep 44.1's divert.** A fresh install still sees the download first, because beat 4 (RUN ▶ on `app.py`) needs Python and the bar is the owner's own Phase 44 requirement. The tour simply starts when the editor opens |

### The tour as built (ten beats, `CoachMarkPlan.steps`)

*(Round 3 deleted the `waits` column: **every** beat waits now — the split below is
kept as the record of what round 2 shipped.)*

| # | Anchor | Where | Beat | waits | inDrawer |
|---|---|---|---|---|---|
| 1 | `editor_drawer` | Editor ☰ | *Your files* | ✅ | – |
| 2 | `drawer_project` | Drawer header (project name) | *Change project* → names `demo_flask` in copy | – | ✅ |
| 3 | `drawer_file` | Drawer row, **only** `demo_flask/app.py` | *Open app.py* | – | ✅ |
| 4 | `editor_run` | Editor RUN ▶ | *Run it* → names **Install** in copy | ✅ | – |
| 5 | `preview_close` | Web Preview's Back arrow | *Your app is running* | – | – |
| 6 | `nav_handle` | Phase 32.1's reveal handle | *The tabs are here* | – | – |
| 7 | `nav_tab_packages` | Bottom bar, Packages tab | *Packages* | ✅ | – |
| 8 | `packages_card` | Packages, first install card | *One-time download* | – | – |
| 9 | `nav_tab_terminal` | Bottom bar, Terminal tab | *Terminal* | – | – |
| 10 | `terminal_chip` | Terminal status chip | *What it is doing* | – | – |

Three pure laws replace the cap:

- **order** — `nextStep` returns the first unseen step whose anchor is on screen, so
  the beats teach in the owner's sequence wherever the screen allows;
- **`waits`** — a step flagged `waits` (the four controls that are always there on
  their own screen: ☰, RUN ▶, the two tabs) **stops the tour** while its anchor is
  absent. This is also the start gate: beat 1 waits, so nothing at all is shown on
  the Terminal tab or the hub — the tour cannot begin out of order;
- **pass-over without spending** *(superseded by round 3: this is the law that
  punched holes in the owner's flow — a beat whose control was not laid out at that
  instant was skipped and a later beat taught instead, so the Packages box could
  arrive before the Flask preview did)* — a step that does not wait is skipped when
  its control is absent and is **not** marked seen, so the tour can never stall on a
  control that only sometimes exists (a drawer row, the preview's Back, the reveal
  handle, the install card) and the lesson is still there later.

Two "someone else owns the screen" inputs sit above the anchor check:
`blockedByForeground` (exit survey, safe mode, a moving Phase 44 stage, **and now
any editor dialog** via `EditorChromeState.dialogOpen`) and `drawerOpen`
(`EditorChromeState.drawerOpen`, `currentValue == Open || isAnimationRunning`): a
drawer beat shows only while the drawer is open, every other beat only while it is
shut.

### The card: one exit, no way on but the control

- **No NEXT/GOT IT.** Tapping the hole leaves the tap unconsumed (the control does
  its own job) and that same tap advances the tour.
- **Outside taps are swallowed whole** — the down *and* the rest of the gesture are
  consumed, so nothing reaches the UI under the scrim and the box does not close.
- ~~**SKIP TOUR** (one tap) and **Back** end the *whole* tour: `markAllSeen` writes
  every beat id, so nothing returns until Settings → About → **Reset tips**.~~
  **Superseded by round 3 — the owner ran this build and the skip is exactly what
  cut the tour: both exits are deleted, `markAllSeen` no longer exists, and the
  only card with buttons is the one at the end.**
- **`Tour · n of 10`** is on every card, in the same shape as the slides'
  `Guide · 1 of 5`: the round-1 boxes read as ten unrelated popups.
- **The card height is measured** (`onSizeChanged`) and only *seeded* by the 150dp
  constant, so `TooltipPlacement` places the real box: fully on screen, never over
  its own hole. The card's size does not depend on its offset, so there is no
  layout feedback loop.

### Deviations added in round 2 (kept with round 1's eight)

9. **No box can point into an `AlertDialog`.** Compose dialogs are their own window,
   so `boundsInWindow()` inside one is dialog-relative and an activity-window scrim
   would cut its hole in the wrong place. Two of the owner's beats live in dialogs —
   the "Open folder" project picker and the *Install Python?* prompt — so they are
   taught by the copy of the beat before them (beat 2 names `demo_flask`, beat 4
   names **Install**, the real `install_prompt_confirm` label). The alternative
   (publishing every anchor in absolute screen coordinates and converting in the
   overlay) is a coordinate-system change to all ten anchors and should not ride
   along with a flow redesign; it is the recorded follow-up if the owner wants a hole
   on **Install** itself.
10. **The Output Panel gets no beat.** The owner's "install → python" moment is
    covered by beat 4's copy and beat 8 (*One-time download*) instead of an eleventh
    anchor on a panel that is often collapsed.
11. **`demo_flask` is now ALWAYS present** — a reversal of Phase 14's documented
    one-time law (`.demo-flask-seeded-v1` meant "never seed again"). The tour teaches
    this project by name, so a deleted demo would make beat 2/3 point at nothing.
    `DemoProjects.ensure` re-seeds when the directory is missing, still never touches
    an existing one (the user's edits are theirs), still refuses to replace a plain
    *file* of that name, and now rolls back a seed that throws so a half-written demo
    can never be mistaken for a project. The marker survives as a record, not a gate.
12. **`ChromeState` lost its five per-anchor booleans** for one `visibleAnchors` set
    (+ `blockedByForeground`, `drawerOpen`): ten anchors would have meant ten
    hand-maintained flags and a mapping that could drift from `GuideAnchors`.

### Tests (round 2): 175 host cases green locally

`CoachMarkPlanTest` **18** (was 12): the tour is the owner's ten beats in the
owner's order · each beat's surface · `waits` is set on exactly the four
always-there controls · every box fits the card · the tour cannot start anywhere but
the editor's ☰ · a box only when its anchor is on screen · a passed-over beat is
never marked seen · a waiting beat holds the tour · the small tour of Packages and
Terminal walks tab → card → tab → chip · blocked suppresses without consuming ·
drawer beats need the drawer open and covered beats need it shut · SKIP/Back end the
whole tour · `markSeen` monotonic and ignores unknown ids · the CSV round-trips in
tour order and drops garbage (including round 1's ids, which are all still valid, so
an upgraded install keeps what it saw and gains the five new beats) ·
`ChromeState.of` · routes → the two tabs the tour teaches · the drawer helpers.
`GuideWiringTest` **16** (was 13): all ten anchors have publishers (four by pure
helper) · no box behind a dialog or a closed drawer (the editor's modal list names
the picker, the Install? prompt and the RUN ▶ chooser; both signals clear on
dispose) · the card has no `Text("GOT IT")`/`Text("NEXT")`, exactly one
`TextButton`, a measured height · the tour's copy names only real labels
(`demo_flask`, `app.py` = the scaffold's real Flask entry file, **Install** =
`install_prompt_confirm`, **Packages**/**Terminal** = the tab titles in `Screen.kt`).
`DemoProjectSeedTest` **7** (was 4, and now runs in the local harness too): first
seed · idempotent, never overwrites · an existing project is respected · **a deleted
demo comes back** · the marker is a record, not a gate · a plain file named
`demo_flask` blocks the seed · `ENTRY_FILE` agrees with `ProjectScaffold.filesFor`
and with the config's `entry` (so RUN ▶ runs the file the tour told the user to
open).

### CI round 2: ✅ GREEN — `34704379023` (tip `acadaee`, 2026-09-12)

`conclusion: success`, job `build`, 25 steps, 16:09:42 → 16:20:23Z (**10m41s**), zero
error annotations (one notice: `setup-java@v4` is deprecated). Per rule §5 that is
`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, so all 66 guide/demo cases
ran on real Gradle/JUnit/Robolectric and lint is clean. Artifacts: release
`CodeC-IDE-1.3.17-universal.apk` **6,664,570 B**, debug **25,665,688 B**,
`mapping.txt` 55,977,938 B, release manifest has no `android:debuggable`.

The interesting number is the delta: **+96 B (+0.001%) over round 1.** Ten beats
replaced five, five new anchors appeared, and the card gained a measured height — and
the APK did not move, because round 2 mostly *deleted* (the per-arrival cap, the
surface filter, the card's forward button, the estimated-height branch). A redesign
that makes the guide a tour instead of a set of one-offs cost nothing to ship.

---

## Round 3 (2026-09-12, later) — no skip: first beat to last, and a close at the end

### The report, verbatim

The owner installed round 2 (`34704379023`), walked the tour, and wrote:

> *"You add the skip option and it's not a trough guide mean it got cut*
>
> *I want a full process 1st to last without skip anything in this*
>
> *3 ber->change the project folder to demo_flask->selected app.py->run->install->python->it
> will open the flusk web->close->tap to reveal the keyboard below option->then a small tour of
> package and terminal*
>
> *At the end option to close and view again"*

("3 ber" = the ☰ three-bar menu; the flow is the same ten beats round 2 already had.)

### Diagnosis first: four causes, again all in the shipped code

1. **SKIP TOUR was the most visible thing on the card.** Every beat carried a button
   whose only job was to end the tour, so the guide read as something to dismiss
   rather than something to walk — and one tap on it *did* dismiss all ten beats
   (`markAllSeen`), which is precisely *"it got cut"*.
2. **The pass-over law punched holes in the flow.** Round 2 split the beats into
   "wait" (four controls that are always there) and "pass over" (everything else).
   Passing over is instant and silent: tap RUN ▶, the Flask server takes three
   seconds to start, and the tour had already walked on to the reveal handle and the
   Packages tab. The boxes arrived **out of the order the app was actually doing
   things in** — *"not a full process 1st to last"*.
3. **Two beats had no target exactly when the user needed them.** Beat 2 published
   its anchor only when a project switch would teach something, so a phone already
   in `demo_flask` (or in scratch mode) never saw the box at all; beat 6 was anchored
   to Phase 32.1's *reveal handle*, which exists only while the bar is hidden, so the
   beat existed only while the keyboard happened to be up.
4. **The project picker closes the drawer.** It always has (the dialog needs the
   room), and beat 3 is a row *inside* that drawer — so after choosing `demo_flask`
   the tour went silent and waited for the user to work out which button brings the
   files back.

### The four laws now

| Law | Round 2 | Round 3 |
|---|---|---|
| Order | first unseen step whose anchor is on screen | **the first unseen step, full stop** — a later beat never jumps the queue |
| Missing control | `waits` beats hold; the rest pass over instantly | **every beat holds**, and holds *silently*: nothing is drawn while a beat waits, so the app is never covered |
| Exit | SKIP TOUR + Back, both ending all ten beats | **no exit mid-tour.** The highlighted control is the only way on; Back navigates as it always does and the tour resumes, unspent, on the same beat |
| The end | the tenth box just stopped | **the finish card**: `CLOSE` and `VIEW AGAIN` — *"At the end option to close and view again"* |

`markAllSeen` is **deleted**: nothing in the product can now spend a beat the user
never saw. The only writer of the seen set is the tap on the highlighted control
(`GuideWiringTest` counts the writers: exactly one).

### The stall guard, and why "every beat waits" needs one

A beat that waits forever is not a brick — it draws nothing, so the app stays fully
usable — but it *is* a dead end: park on a control that never appears and every beat
behind it goes untaught. So the host times a wait, in memory, and may pass a beat for
**this session** (`CoachMarkPlan.STALL_GUARD_MS = 20_000L`). Three rules make that
safe, and all three are pure and host-tested:

- **A passed beat is never marked seen.** It is still owed, and the next pass (next
  launch, or VIEW AGAIN) walks it. `remaining()` still lists it; `isComplete` is
  still false; only `isFinished` — "this session's tour has run to its end" — is true.
- **The plan names what the host times** (`waitingOn`), and it names nothing in three
  cases: another surface owns the screen (a dialog, the exit survey, safe mode, a
  moving Phase 44 download — a Python install takes minutes and must not cost the
  beat after it); the drawer is simply in the other state (the user's own next tap
  ends it); and **the beat's control cannot exist on this route**.
- **That last one is the anti-cascade rule** (`anchorsPossibleOn(route)`, and
  `ChromeState` grew a `route` for it). Timing out a beat the user has to *travel* to
  would cascade: the Flask preview stalls during the install, then the reveal handle,
  then the Packages tab, then everything — and the tour would "finish" on a screen the
  owner's flow has not reached. So the guard fires only for a control that **could be
  here and is not**: the Packages card behind a collapsed section, the `app.py` row
  when the user picked some other project. The bar and its tabs are possible on every
  route; the preview's Back only on `preview`; the card only on `modules`; the chip
  only on `terminal`; the editor's four only on `editor`.

### Two beats that needed a real target

- **Beat 2 is now published in every state of the drawer header** — another project,
  `demo_flask` already open, scratch mode. The header always opens the picker, so the
  beat is always reachable, and `drawerProjectAnchor` (the pure "is a switch worth
  teaching?" question) is deleted. A tour that is "1st to last without skip anything"
  must not depend on which project happens to be open.
- **Beat 6 has two publishers and one id**: the thin reveal handle while the bar is
  hidden, and **the bar itself** while it is visible. Same lesson (*"Five tabs, one
  tap away. They hide while you type — swipe up to bring them back."*), and now a
  target on every screen the tour walks. A tap inside that hole lands on a tab, which
  is where beat 7 wanted the user anyway.

### The drawer the picker closes

`CoachMarkPlan.nextBeatIsInDrawer(seen)` is a pure question — *is the beat the tour is
waiting for a row inside the ☰ drawer?* — and the host that owns the seen set passes
the answer to `EditorScreen(tourWaitsInDrawer = …)`. When it is true, the project
picker's "a project was chosen" branch reopens the drawer after the switch, so
beat 3's box is already there. When the tour is over, or has not started, the flag is
false and the picker behaves exactly as it always did. The drawer still closes for the
picker itself; that is not the tour's to change.

### The finish card

`TourFinishedCard` — the only card with buttons, and not a dialog (a dialog is a window
of its own; this belongs to the same layer as the tour). Dim backdrop, taps swallowed
(the tour's own rule), `Tour · 10 of 10`, one line of summary, then **VIEW AGAIN** and
**CLOSE**.

- It is **earned**, not remembered: `isFinished(seen, stalled)` *and* a
  `ranThisSession` flag that only becomes true when this composition watched the tour
  being incomplete. An install that starts with all ten beats taught — the owner's
  phone after round 2 — is not greeted by "that is the whole tour" on every launch.
- **CLOSE** is in-memory. It does not write the seen set, so a beat the guard passed
  is still owed and comes back on the next pass.
- **VIEW AGAIN** clears the seen set (`CoachMarkPlan.replay()`, one key, one meaning),
  clears the guard's set, and the host navigates to the editor — where beat 1 lives —
  with the bar's own idiom (`createRoute(null)`, `restoreState = true`,
  `navRevealed = false`), so a replay arrives the way a tab tap would.
- Outside the tour, **Settings → About → Reset tips** is the same door (it resets the
  slides too). No new Settings control, so no new audit row; no new DataStore key, so
  `SettingsKeysHaveReadersTest` is untouched.

### Deviations added in round 3 (13-16, kept with round 1-2's twelve)

13. **The no-nag law's "skippable with a single tap" is reversed for the tour, by the
    owner.** The slides keep SKIP (his round-2 decision, unchanged). The tour's exit
    moved to the end. The safety argument that replaces the skip: *a waiting beat
    draws nothing*, so the overlay can never cover the app — the user is never trapped,
    only ever accompanied. Recorded in `chat-phase45/README.md` §"two rules" and in
    TROUBLESHOOTING §37.
14. **A parked tour is silent, and the beats behind a park wait too.** If the Flask
    preview never opens (Python missing and the install declined), beat 5 waits and
    beats 6-10 are not taught until it does — or until Reset tips. That is the direct
    cost of "without skip anything"; round 2's answer (teach what is available) is the
    thing the owner called *cut*.
15. **The stall guard can still pass a beat** (possible on this route, missing for 20s).
    It is invisible, rare, and never spends the lesson. It exists so deviation 14
    cannot become a dead end on the screen the user is actually looking at.
16. **Beat 6 spotlights the whole bottom bar when the bar is visible** — a much bigger
    hole than the thin handle. Same lesson, and the tap inside it lands on a tab.

### Tests (round 3): 180 host cases green locally

`CoachMarkPlanTest` **21** (was 18): every beat waits and no later beat jumps the
queue (all ten positions) · a stalled beat is passed without being spent · the drawer
law (both `nextStep` and `waitingOn` gate on it) · the guard is armed only by a control
that is not laid out, and never by a blocked screen or a drawer-state wait · **the
guard never times out a beat the user has to travel to** (the cascade case) · **the
route map** route by route, plus "every beat is possible somewhere" ·
`nextBeatIsInDrawer` in all five tour states · nothing ends the tour early and VIEW
AGAIN restarts all ten · the seen CSV round-trip. `GuideWiringTest` **18** (was 16):
beat 2's unconditional anchor · beat 6's **two** publishers · the host passes `route` ·
a tour card has **no button at all** and the only buttons are `VIEW AGAIN`/`CLOSE` on
the finish card · no `markAllSeen`, no `BackHandler`, exactly one writer of the seen
set · the guard's wiring (`waitingOn` → `delay` → in-memory set) · the finish card is
earned, not remembered · the drawer reopens after a pick and still closes for the
picker. `GuidePlanTest` 15, `TooltipPlacementTest` 10, `DemoProjectSeedTest` 7
unchanged; Phase 44's 109 unchanged. **180 = 71 guide/demo + 109.**

### CI round 3: ✅ GREEN — `34707337429` (tip `0fcb3b6`, 2026-09-12)

`conclusion: success`, job `build`, 25 steps, 17:08:44 → 17:19:35Z (**10m51s**), zero
error annotations (two deprecation notices: Node 20 on the runner, `setup-java@v4`).
Per rule §5 that is `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, so all
71 guide/demo cases ran on real Gradle/JUnit/Robolectric and lint is clean. Artifacts:
release `CodeC-IDE-1.3.17-universal.apk` **6,669,858 B**, debug **25,676,356 B**,
`mapping.txt` 56,101,847 B, release manifest has no `android:debuggable`.

**+5,288 B (+0.08%) over round 2**, and this round was not mostly deletion: the tour
grew a finish card, a stall guard with a route map, a second publisher for beat 6, and
a drawer reopen. Five kilobytes is the measured price of a guide that cannot be
skipped. Whole-phase cost, for the record: **+18,176 B (+0.27%) over Phase 44 round 4**
(6,664,474 − 12,792 = 6,651,682 B), for the slides and all three rounds of the tour.

---

## Round 4 (2026-09-12, later still) — one tap does both halves, and an install pauses the app

### The report, verbatim

> It's good but i have 2 request-
>
> 1. When the userland is installing and unpacking the user can not access any other
>    other option and it will show a sweet massage of why can't access any other option
>
> 2. Now the steps feel like an overlay on the botton so 1st click disappear the massage
>    and i have to click 2nd time to really work but if someone don't click 2nd time it
>    just cut off the flow of tutorial
>
> So do something

Request 2 is a bug report against round 3's central mechanism, and it is right.
Request 1 is a new behaviour, and it belongs to Phase 44's setup as much as to the
tour — it is specified in
[`../chat-phase44/PART_44_1_VISIBLE_SETUP.md`](../chat-phase44/PART_44_1_VISIBLE_SETUP.md)
§"Phase 45 round 4 — the chrome lock" and summarised here because it changes what the
tour may do.

### Diagnosis first (request 2): the pass-through WAS the design, and the design was wrong

Round 3's overlay, on a tap inside the hole:

```kotlin
val down = awaitFirstDown(requireUnconsumed = false)
if (hole.contains(down.position)) { onAdvance() }   // left unconsumed: "the control gets the rest"
```

Three consequences, and the first alone reproduces the report:

1. **The advance ran before the click.** `onAdvance()` writes the seen set on the
   DOWN event — a state write in the middle of a gesture. The host recomposes from it
   (`coachSeen` → `tourWaitsInDrawer` → the editor's drawer plumbing → the next beat's
   box), while a `clickable` fires on the LIFT, 80-150 ms later, and only if the node
   that took the press is still the node that takes the lift. When that recomposition
   rebuilds it, Compose cancels the gesture: **the box went away, the lesson was
   spent, and nothing opened.**
2. **Pass-through is a hope, not a contract.** It depends on hit-test order, on the
   control not being covered by the next beat's scrim, and on the drawer/dialog
   plumbing underneath it. Round 3 already carries four deviations about controls that
   are laid out but not tappable; the tap path had the same class of problem, and no
   host test can see it (a JVM has no pointer dispatch).
3. ***"if someone don't click 2nd time it just cut off the flow"*** — and worse than
   it sounds. The first tap marked the beat SEEN, so the second tap was not the same
   lesson again: the tour had moved to a beat whose control was not on screen and then
   (deviation 14) waited in silence. **One dead tap = one lost lesson + a parked
   tour.** That is the exact failure mode round 3 was written to remove, arriving
   through the tap handler instead of through a skip button.

### The fix: an anchor publishes its own click, and the overlay performs it

`GuideAnchor.modifier(id, onClick)` now publishes **two** things per anchor: the window
rect (as before) and the control's **own click**. The registry stores them side by side
(`rects`, `actions`) and offers them to the overlay as pure boxes
(`tapTargets(): List<GuideTapTarget>`) plus one verb (`perform(id)`).

The gesture, rewritten:

- **press inside the hole** → the PURE policy ([`GuideTapPolicy.targetFor`]) picks the
  control under the finger: the **most specific** anchored click whose box contains the
  press (smallest area; ties break on tour order, so the answer never depends on map
  iteration), and only inside the hole. The gesture is then **consumed**, so the
  control cannot also fire it — one tap, one action, never two.
- **lift** → if [`GuideTapPolicy.isTap`] says the gesture was a tap (it barely moved,
  or it lifted inside the hole), the overlay performs that control's click **and then**
  advances the tour. Action first, beat second: the click runs against the state the
  user is looking at, and the advance's recomposition comes after it, never through it.
- **a drag that travels and lifts outside the hole** → not a tap. Nothing is performed,
  **no beat is spent**, the box stays. Answering a scroll with an install — or with an
  advance and no action — is the same bug wearing a different hat.
- **press inside the hole where no anchored click lives** → the gesture is left ALONE
  (Compose delivers it to the real control) and the tour advances **on the lift**. This
  is beat 6's bar-wide hole over a tab the tour does not use (Projects, Editor,
  Settings): that tap still opens that tab.
- **press outside the hole** → swallowed, unchanged (owner, round 1: *"even tap outside
  will not end that box"*).

The slop comes from `LocalViewConfiguration.current.touchSlop` — the platform's, passed
into the pure policy, never a number invented in the guide.

#### What each beat performs

| # | Anchor | Published click | One tap now does |
|---|---|---|---|
| 1 | `editor_drawer` | `onDrawerTap` (☰'s own toggle, lock-aware) | opens the drawer **+** beat 2 |
| 2 | `drawer_project` | `onSwitchProject` | opens the project picker **+** beat 3 waits for the pick |
| 3 | `drawer_file` | `onOpenOrToggle` (the row's own click; long-press stays the row's) | opens `app.py` **+** beat 4 |
| 4 | `editor_run` | `onRunTap` (RUN ▶'s own click, lock-aware) | runs the file / opens the chooser **+** beat 5 waits for the preview |
| 5 | `preview_close` | `onNavigateBack` | closes the Flask preview **+** beat 6 |
| 6 | `nav_handle` (handle) | `onReveal` | reveals the bar **+** beat 7 |
| 6 | `nav_handle` (the visible bar) | **none** — the bar is not a control | resolves to the tab under the finger, or passes through |
| 7 | `nav_tab_packages` | `onTabTap` (the tab's own click, lock-aware) | opens Packages **+** beat 8 |
| 8 | `packages_card` | the button the card is really showing: `onInstall`, or `onViewSetup` while the userland is unusable, or **none** when the package is installed | starts the one-time download **+** beat 9 |
| 9 | `nav_tab_terminal` | `onTabTap` | opens Terminal **+** beat 10 |
| 10 | `terminal_chip` | **none** — a label, not a control | advances (nothing to perform) |

Two of those rows are honesty rules rather than conveniences:

- **Beat 8 publishes the click of the button the card is SHOWING.** An installed card's
  buttons are RUN / UNINSTALL / REINSTALL; publishing `onInstall` there would turn a tap
  on a highlighted card into a reinstall nobody asked for, so it publishes nothing and
  the tap is left to the card.
- **Beat 10 publishes nothing** because the status chip is a label. A fake click on it
  would be a lie the user can tap.

#### One id, two publishers: the registry learned about owners

Beat 6 has two publishers that are never on screen together (the bar while it is
visible, the thin handle while it is hidden), and a swap between them disposes one site
and composes the other **in the same recomposition**. A blind `withdraw(id)` from the
leaving site could therefore wipe the arriving site's rect *and* click, and the beat
would go dark on exactly the keyboard transition it exists to teach. The registry now
records which composition site owns an id (`owners`) and honours a withdrawal only from
that site. Round 3 shipped without this and round 4 could not ship with it: round 3's
withdrawal lost a rect for a frame, round 4's would lose the click as well.

### Request 1: the chrome lock (summary — the spec is in PART_44_1)

While an install is moving, the options that cannot work are **paused**, and a paused
option **says why** instead of doing nothing:

- the one-time **userland** setup (downloading / checking / unpacking, with no working
  prefix yet) pauses the four tabs that are not the Terminal;
- a **package install** the user asked for (RUN ▶ → Install, streaming into the Output
  Panel) pauses the four tabs that are not the Editor, plus ☰ and RUN ▶ inside it;
- the sentence is one per reason, from the pure `SetupLockPolicy.sweetMessage`, and it
  arrives **once when the pause begins** and again on every refused tap;
- the surface the install can be watched from is **never** paused
  (`SetupLockPolicy.watchOption`), an in-flight **upgrade** of a working prefix pauses
  nothing, `CHECKING` (the startup probe) pauses nothing, and Phase 44.1's guarantees
  are untouched: typing and `cc` never wait for a download.

**And the tour pauses with the chrome** (`blockedByForeground … || chromeLock.locked`):
a box on a paused control could only be *spent* by a tap that merely shows the
sentence, and after request 2 a spent beat is precisely what this round exists to
prevent. Nothing is drawn during an install (already true for the userland stages in
round 3); now it is also true for a package install, which no setup stage reports.

### Deviations added in round 4 (17-20, kept with rounds 1-3's sixteen)

17. **The overlay performs the control's click instead of letting Compose deliver the
    tap.** Round 1-3's law was "a tap inside the hole is left unconsumed so the real
    control performs its own action". Round 4 keeps the *law* (the highlighted control
    is the only way on, and it really acts) and replaces the *mechanism*: the anchor
    publishes its click, the overlay performs it and swallows the gesture. Cost: an
    anchor whose click is not published can only dismiss its box — which is why the
    wiring test now names the lambda each beat performs, and why two anchors
    deliberately publish none.
18. **Beat 6's copy says TAP, not "swipe up".** While a box is up the overlay owns the
    gesture, so a swipe that leaves the hole is not a tap and does nothing: a box that
    told the user to swipe would be a box that never moves. New body: *"Five tabs, one
    tap away. They hide while you type — tap this handle to bring them back."* The
    swipe itself still works the moment the tour is over (`NavBarPolicy.revealOnSwipe`
    is untouched), and the owner's own words for this beat were *"the tap to the open
    down side of the keyboard"*.
19. **A card that is already installed, and a label, publish no click.** See the two
    honesty rules above. Consequence: on those two beats the tap is passed through
    (card) or does nothing but advance (chip).
20. **The chrome lock pauses the tour.** An install is now a fourth "someone else owns
    the screen" signal, beside the exit survey, safe mode, a moving Phase 44 download
    and any editor dialog — and it is the only one that can arrive from inside the
    editor's own Output Panel.

### Tests (round 4): 193 host cases green locally

`CoachMarkPlanTest` **25** (was 21): the tap resolves to the most specific anchored
click (bar-wide hole → the tab under the finger; card → its own button) · a tap on an
unanchored part of the hole resolves to nothing · equal areas break on tour order ·
nothing outside the hole is performed · a label performs nothing · **a drag that leaves
the hole is not a tap and spends no beat** · the slop and the lift-inside rule · beat
6's copy teaches a gesture the tour accepts (and no box teaches "swipe").
`GuideWiringTest` **19** (was 18): every clickable anchor publishes **the lambda named
here** (`onDrawerTap`, `onSwitchProject`, `onOpenOrToggle`, `onRunTap`,
`onNavigateBack`, `onTabTap`, `onReveal`, `cardClick`) · the bar and the chip publish
**no** click · the overlay resolves at the press, performs **before** it advances
(order pinned by index, not by prose) · a drag is refused · the slop is the platform's ·
withdrawal is ownership-checked and clears rect, click **and** owner · the lock pauses
the tour. `SetupGatePolicyTest` **30** (was 25): the userland stages pause the chrome
with one sentence that names the work, the percentage when there is one, and where to
watch · Terminal is never paused · a probe, a settled setup and an upgrade of a working
prefix pause nothing · a package install pauses the chrome and keeps the **Editor** ·
routes map to options and a non-tab route maps to none · **the lock never weakens the
gate beside it** (`RUN_C`, `EDIT_FILE`, `OPEN_TERMINAL` still allowed while the chrome
is paused). `SetupGateWiringTest` **23** (was 20): the bar asks the policy, a paused tab
does not navigate, it looks paused, the scaffold grew a snackbar host, the sentence
arrives when the pause begins, the editor reports the install only it can see and
clears it on the way out, `installing` is set on exactly one path and cleared by both
of its exits. `GuidePlanTest` 15, `TooltipPlacementTest` 10, `DemoProjectSeedTest` 7
unchanged. **193 = 76 guide/demo + 117 Phase 44** (180 + 13 new: 4 tap-policy, 1 wiring,
5 lock-policy, 3 lock-wiring).

### CI round 4: ✅ GREEN

`Build APK` **`34711827176`** on tip `e7759f1` — `conclusion: success`, job `build`
25 steps, 11m29s (18:38:49Z → 18:50:18Z), **zero annotations**, assemble +
`testDebugUnitTest` + `lintDebug` per `rule.md` §5, so the 193 host cases ran on real
Gradle/JUnit/Robolectric and lint is clean. Artifacts: **release APK 6,675,258 B**
(**+5,400 B / +0.08%** over round 3's 6,669,858 B — this round added an anchor-click
registry with ownership, a pure tap policy, a chrome-lock policy and its wiring across
five screens, so five and a half kilobytes is the measured price of one tap doing both
halves; whole phase **+23,576 B / +0.35%** over Phase 44 round 4's 6,651,682 B),
debug 25,692,976 B, mapping.txt 56,258,512 B, v1.3.17.

Pushed with this section; the run and its APK numbers are recorded here when it lands.
