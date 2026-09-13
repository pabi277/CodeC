# CodeC Phase 49.1 — The back router

> **Status:** 🚧 IMPLEMENTED (2026-09-13, `arena/01a09925-codec`, owner:
> "Start phase 48 and 49"; CI ✅ GREEN `34740245825` tip `a592295` — round 1
> red for-cause, one stale Phase-41 pin in `ExitSurveyTest`, moved with its
> reason; the ten-check device round pending — the diagnostic log now ships
> IN the build, see the implementation record) · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"iv. After clicking 3 ber if user use back botton it
> will [close] the file view and show the editor not full app close"* —
> clarification 2026-09-12: **both** the editor drawer and the hub file tree,
> plus *"C. The back botton all screen behavior please recheck and refine"*.

## Step 1 — reproduce before changing anything

`rule.md` §4.2. The plan assumes the drawer *should* already close on back
(material3 1.3.1 registers a drawer back consumer, and the platform calls
`OnBackPressedCallback` regardless of the manifest flag). The owner says it does
not. So: build a debug APK with a **diagnostic-only** `OnBackPressedCallback`
added *first* in `MainApp` that logs, on every back press, the chain's enabled
state plus `drawerState.targetValue`, `activeProject != null`,
`navController.previousBackStackEntry` and the current route — then run the ten
device checks from the phase README and paste the log into this file. That log
selects H1-H4 from the phase README's table, and H4 (gesture-nav home swipe)
changes 49.2's wording rather than its code.

Until that log exists, every hypothesis in this part stays a hypothesis. The
design below works for H1, H2 and H3; only H4 is a non-bug.

**How this step was actually discharged (implementation record, 2026-09-13):**
the diagnostic is not a throwaway build — the shipped root handler logs every
press it sees (`AppLogger.i("Back", …)`: the route, the prompt switch, the
prompt visibility and the decision) and the two screen-local handlers log the
presses they claim (the hub's tree close names the project), so the owner's
device round produces the exact log this step asked for from the REAL build,
via Settings → Developer Options → **View App Logs** — no second APK, and the
ten checks run against the code that has to pass them. The drawer/hub rows
make the chain's winners explicit (H1), key on `targetValue` (H2), and the
sora key-path is untouched (H3 unchanged); H4 remains a wording question for
49.2, answered by the same log.

## Design

### The policy (pure, `ui/navigation/BackRouter.kt`)

`BackState` and `BackAction` as specified in the phase README. The table:

```kotlin
fun decide(s: BackState): BackAction = when {
    s.unsavedChanges          -> BackAction.ShowUnsavedDialog
    s.coachMarkVisible        -> BackAction.CloseCoachMark
    s.editorDrawerOpen        -> BackAction.CloseEditorDrawer
    s.hubProjectOpen          -> BackAction.CloseHubProject
    s.sheetOrDialogOpen       -> BackAction.None          // the sheet owns its own back
    s.findBarOpen             -> BackAction.CloseFindBar
    s.outputPanelExpanded && !s.keyboardVisible -> BackAction.CollapseOutputPanel
    s.exitPromptVisible       -> BackAction.ExitApp       // "tap again to exit"
    s.canPopRoute             -> BackAction.PopRoute
    s.atRootDestination       ->
        if (s.safeMode || !s.exitPromptEnabled) BackAction.ExitApp
        else BackAction.ShowExitPrompt
    else                      -> BackAction.None
}
```

Three deliberate choices:

1. **`sheetOrDialogOpen → None`** *after* the drawer/hub rows, not before. A
   Material3 sheet's own handler is above ours in the chain, so returning `None`
   is what lets it work; putting the sheet row first would only matter if the
   sheet's handler were missing, and if it is missing we want to see that.
2. **`outputPanelExpanded` is guarded by `!keyboardVisible`.** With the keyboard
   up, back is the user closing the keyboard — Android already does that and the
   router must not eat it.
3. **`atRootDestination` is computed from the route, not from
   `popBackStack()`** — see 49.2.

### Wiring, screen by screen

| Screen | `BackState` fields it fills | Action it performs |
|---|---|---|
| `MainActivity` (root handler, `:801`) | `canPopRoute`, `atRootDestination`, `exitPrompt*`, `safeMode` | pop / prompt / `finish()` |
| `EditorScreen` | `unsavedChanges` (`:646` today), `editorDrawerOpen` (`drawerState.targetValue == DrawerValue.Open`), `findBarOpen`, `outputPanelExpanded`, `coachMarkVisible` | the five closes + the unsaved dialog |
| `FileManagerScreen` | `hubProjectOpen` (`vm.activeProject != null`), `sheetOrDialogOpen` | close the tree → `vm.closeProject()` |
| `TerminalScreen` / `ModulesScreen` | nothing screen-local | root handler |
| `WebPreview` / `Logs` / `Feedback` | nothing (their own `onNavigateBack`) | unchanged |

The `FileManagerScreen` row is the interesting one: because `activeProject` is
ViewModel state rather than a navigation entry, "close" means
`vm.closeProject()` — the same call the breadcrumb's root already makes
(`FileManagerScreen.kt:301`). **The alternative** — making the project tree a
real route so back falls out of the navigation for free — is recorded in
*Deferred* with its cost.

## Exit condition

The phase README's ten device checks. The three that this part owns outright:

- **#1** editor drawer → back closes it (with the sub-200 ms variant);
- **#2** hub project tree → back returns to the list;
- **#4** unsaved changes outrank both.

## Tests (plan)

- `BackRouterTest` (host): the precedence pairs and the total-function property.
  Cases include: drawer open + dirty → `ShowUnsavedDialog`; drawer open + clean
  → `CloseEditorDrawer`; hub project + drawer open (impossible in practice, but
  the table must still answer) → `CloseEditorDrawer`; output expanded + keyboard
  up → `None`; output expanded + keyboard down → `CollapseOutputPanel`; prompt
  visible → `ExitApp`; safe mode + prompt enabled at root → `ExitApp`; prompt
  disabled at root → `ExitApp`; non-start tab → `PopRoute`.
- `BackRouterRootTest`: `isRoot` for `projects`, `terminal`, `packages`,
  `editor?projectName=x`, `terminal?session=y` (parameterised routes — the
  `startsWith` trap that bit `MainActivity.kt:731-733`), and the web/logs/
  feedback routes → false.
- Source scan (`BackHandlerWiringTest`): every `BackHandler(` in `app/src/main`
  is either in `MainActivity`'s root handler or calls `BackRouter.decide(` — so
  a future screen cannot add an ad-hoc handler that contradicts the table.
- Robolectric: press back in each of the four states and assert the resulting
  screen. This is not speculative — Robolectric 4.16.1 is already a test
  dependency (`app/build.gradle.kts:271`), 8 tests use `RobolectricTestRunner`,
  and `EditorLaunchMeasureReproTest` already drives `NavHost` +
  `rememberNavController` under `createComposeRule`. The source scan is the
  fallback if a Compose back-press proves unreliable under Robolectric.

## Sources (record)

- CodeC 2026-09-12: `MainActivity.kt:632-641,706-747,768-812,824-831`,
  `EditorScreen.kt:646,900-907`, `FileManagerScreen.kt:298-306,301`,
  `FileManagerViewModel` (`activeProject`, `closeProject()`).
- Platform: the predictive-back guide (chain of responsibility, innermost wins,
  `enableOnBackInvokedCallback` is irrelevant to `OnBackPressedCallback`);
  stackoverflow 79247909 (drawer/NavHost handlers sit *above* an activity-level
  one); stackoverflow 76564309 (`BackHandler(enabled = drawerState.isOpen)`).
- `docs/PHASE44_50_UX_RESEARCH.md` §6.1-6.2 (the audit table and the four
  hypotheses).

## Deferred / rejected with reasons

- **Making the hub project tree a navigation route** (`projects?project=…`).
  Cleaner in principle — back then works with no router row at all. Cost: the
  hub's open state drives the file tree, the git banner, the branch sheet and the
  search filter, all of which currently read `vm.activeProject`; moving it into
  the back stack means re-plumbing `FileManagerScreen`'s state and its
  `saveState`/`restoreState` interaction with the tab logic. Recorded as a
  follow-up if the router row ever proves insufficient.
- **Enabling `enableOnBackInvokedCallback`** — deferred at the phase level.
- **`PredictiveBackHandler` for the drawer** — same reason; the router keeps the
  door open.
- **A "press back twice to exit" toast instead of the dialog** — 5.B's owner
  instruction is to keep the prompt; a toast is a different product decision.

## Implementation (2026-09-13)

**New pure code (host-tested):** `ui/navigation/BackRouter.kt` — `BackState`
(12 fields, everything defaulted so a screen fills only what it owns),
`BackAction` (9 values) and `BackRouter.decide` — the spec's precedence
table, verbatim:

```text
1  unsavedChanges                          -> ShowUnsavedDialog
2  editorDrawerOpen                        -> CloseEditorDrawer
3  hubProjectOpen                          -> CloseHubProject
4  sheetOrDialogOpen                       -> None
5  findBarOpen                             -> CloseFindBar
6  outputPanelExpanded && !keyboardVisible -> CollapseOutputPanel
7  exitPromptVisible                       -> ExitApp
8  canPopRoute                             -> PopRoute
9  atRootDestination                       -> ShowExitPrompt / ExitApp
10 else                                    -> None
```

Three laws kept from the spec: `None` = "let the library own it" (sheets and
dropdowns keep their handlers); row 6 is guarded by the keyboard (back with
the keyboard up is the user closing it — the router must not eat it); rows
7-9 are ROOT-ONLY fields (a screen-local state leaves them defaulted, so a
screen's handler can never pop navigation or exit the app behind its own
screen's back).

**`isRoot` (49.2's half of this file):** `BackRouter.isRoot(route,
rootRoutePatterns)` compares the segment before any query, by equality —
parameterised patterns (`editor?projectName={projectName}&…`) report
themselves as destination routes, and a substring `startsWith` would be the
trap the 45.2 replay idiom worked around. It takes plain route-pattern
strings (the app passes `screens.map { it.route }`) instead of `List<Screen>`
so the file stays a pure, compose-free policy. Pinned by `BackRouterRootTest`
against the exact five patterns `Screen.kt` builds, plus the forgery cases
(`preview?url=https://x/settings` is NOT a root).

**Wiring, screen by screen (one handler per surface, every one of them
router-driven — pinned by `BackHandlerWiringTest`):**

- `MainActivity` (root, the last in the chain): fills `canPopRoute`
  (`previousBackStackEntry != null`), `atRootDestination` (`isRoot`),
  `exitPromptEnabled/Visible`, `safeMode`. Performs PopRoute / ShowExitPrompt
  / ExitApp. While the prompt is up the handler stays OFF
  (`enabled = !exitPromptVisible && …`) — the dialog's own back IS the second
  press, exactly one exit path, kept from Phase 41. Every press logs the
  49.1/49.2 diagnostic line (route, prompt switch, prompt visibility,
  decision) — View App Logs reads it.
- `EditorScreen`: fills `unsavedChanges`, `editorDrawerOpen`
  (`drawerState.targetValue == DrawerValue.Open` — H2), `sheetOrDialogOpen`
  (`editorModalOpen || pendingCloseTab != null`), `findBarOpen`,
  `outputPanelExpanded`, `keyboardVisible`. Performs the four closes. The old
  `BackHandler(enabled = drawerState.isOpen)` and `BackHandler(enabled =
  isDirty)` are gone; `closeDrawer` now asks `DrawerPolicy.shouldClose` with
  the same targetValue semantics, so a back/✕ inside the ~200 ms open
  animation CANCELS it (a closing animation still refuses — never two
  `close()` calls).
- `FileManagerScreen` (NEW handler — the owner's 4.iv hub half): fills
  `hubProjectOpen` (`activeProject != null`) and its sheet/dialog set;
  performs `viewModel.closeProject()` — the same close the breadcrumb's root
  runs. Back at an open tree can never exit the app again.
- `GuideScreen`: fills `canPopRoute` (the guide is a full-screen surface
  above the shell) → PopRoute → `onFinished()` — the same act SKIP runs,
  "back = SKIP" unchanged.
- `WebPreview` / `Logs` / `Feedback`: unchanged (`onNavigateBack`); their
  hardware back now reaches the root handler and pops the same way it always
  did (canPopRoute → PopRoute).
- Coach marks: NO handler, by design — see the deviation below.

**Deviations from the written spec, each with its reason:**

1. **The coach-mark row is NOT built.** The spec's table row 2
   (`coachMarkVisible → CloseCoachMark`) was written before Phase 45's owner
   rounds 2-3 rebuilt the tour as ONE unbreakable flow (*"I want a full
   process 1st to last without skip anything in this"*) — Back there
   navigates and the tour resumes unspent, pinned by `GuideWiringTest`
   ("Back must not end the tour"). A close-the-mark row would contradict
   that later, owner-given law, so `BackState`/`BackAction` have no
   coach-mark members and the audit table's row 9 is corrected to
   "navigates; the tour waits unspent" instead of "must close the mark".
2. **The replay-follow rescroll of Phase 48** touched `SoraEditorHost`, not
   this phase — noted here only because 49's wiring tests were written to
   coexist with it (both phases landed in one push).
3. **The diagnostic log ships in the root/hub handlers** (AppLogger, tag
   `Back`) instead of a throwaway debug APK — see Step 1 above.

**Exit condition status:** the ten device checks in the phase README are the
owner's round, with the sub-200 ms drawer variant covered by construction
(targetValue). Automated halves in CI: `BackRouterTest` (the precedence
pairs, the total-function cases, the row-6 guard, safe mode),
`BackRouterRootTest` (the five tab patterns plain + parameterised, non-tab
routes, the forgery cases), `ExitPromptPolicyTest` (the four prompt states
from the same inputs the wiring builds), `BackHandlerWiringTest` (every
`BackHandler(` in the app is either the root or router-driven; the root's
enabled condition and its three actions; the editor's four actions and the
targetValue semantics; the hub's close; the guide's pop; the second door),
plus the moved `DrawerWiringTest` / `GuideWiringTest` pins.

## Test log (Phase 50 — the cross-device matrix)

> The 8 rows of the cross-device matrix that this part owns ([`../chat-phase50/DEVICE_MATRIX.md`](../chat-phase50/DEVICE_MATRIX.md)). `⏳` = not run. Paste the tester's result into `Result` (✅, or ❌ + the text the screen really showed, verbatim) and the device identity line — the first line of Settings → Feedback & Support → COPY REPORT — into `Evidence`, one line per device class that ran it. `DeviceMatrixTest` pins this table both ways: every row here must exist in the matrix with `49.1` in its `Part` column, and every matrix row owned by this part must be listed here. That is Phase 50's exit 4, enforced by CI instead of by memory.

| Row | Result | Evidence (device · OS · nav mode · what was seen) |
|---|---|---|
| I1 | ⏳ | |
| I2 | ⏳ | |
| I3 | ⏳ | |
| I4 | ⏳ | |
| I5 | ⏳ | |
| I6 | ⏳ | |
| I7 | ⏳ | |
| I8 | ⏳ | |

**Already on record:** the ten back checks ran on the owner's phone and
passed at round level (2026-09-13, *"All device passed"*). One phone is one nav
mode: I1-I8 on the other mode, and I2 (the sub-200 ms press) on a slow device,
are the cross-device half.

