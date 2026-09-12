# CodeC Phase 49.1 — The back router

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M ·
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
- Robolectric, if the harness allows: press back in each of the four states and
  assert the resulting screen. The source scan is the fallback if it does not.

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
