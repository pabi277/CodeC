# CodeC Phase 49 — Back does the obvious thing, everywhere

> **Status:** 📋 PLANNED (researched + specced, no code) · **Cost:**
> `[client-only]` · **Effort:** M · **Owner rows (verbatim):**
> *"iv. After clicking 3 ber if user use back botton it will [close] the file
> view and show the editor not full app close"* · *"B. My phone showing the
> option when try to close not now option but in most phone no option like not
> now or exit"* · *"C. The back botton all screen behavior please recheck and
> refine"*
>
> **Owner clarifications (2026-09-12):** 4.iv = **both** the editor drawer and
> the Projects-hub file tree (plus an audit of every screen). 5.B = **keep the
> exit prompt ON, make it consistent** — find out why some devices never show it.

```text
  49.1  BackRouter: one pure precedence table, every screen wired to it
  49.2  The exit prompt, decided from state instead of from popBackStack()
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [49.1](PART_49_1_BACK_ROUTER.md) | The back router (policy + wiring) | M | 📋 PLANNED |
| [49.2](PART_49_2_EXIT_PROMPT_CONSISTENCY.md) | Exit prompt on every device, or nowhere | S/M | 📋 PLANNED |

---

## The audit: what back does on every screen today

Only **two** `BackHandler` call sites exist in the whole app
(`grep -rn "BackHandler" app/src/main` → `MainActivity.kt:801`,
`EditorScreen.kt:646`). Everything else is a library default.

| # | Surface | Back today | Source | Verdict |
|---|---|---|---|---|
| 1 | Start-destination tab, nothing open | `popBackStack()` false → exit prompt (if enabled) → `finish()` | `MainActivity.kt:801-809` | ✅ intended |
| 2 | A **non-start** tab | `popBackStack()` **true** → silently lands on the start tab; no prompt | `MainActivity.kt:824-831` (`popUpTo(start) { saveState = true }`) | ⚠️ standard bottom-nav behaviour, but it is why 5.B is device-dependent |
| 3 | Editor, unsaved changes | unsaved dialog | `EditorScreen.kt:646` | ✅ |
| 4 | **Editor drawer open** | *library default* — owner reports the app exits | `EditorScreen.kt:900` | ❌ owner-reported bug |
| 5 | **Hub with a project open** (file tree) | **exits the app** — `activeProject` is ViewModel state, not a navigation entry, so nothing pops it | `FileManagerViewModel.activeProject`, `FileManagerScreen.kt:298-306,516-537` | ❌ owner-reported bug (4.iv, hub half) |
| 6 | Web Preview / Logs / Feedback | `onNavigateBack` → `popBackStack()` | `MainActivity.kt:1013,1101,1136` | ✅ |
| 7 | Hub `+` sheet / git sheet / branch sheet | Material3 `ModalBottomSheet` defaults | `FileManagerScreen.kt:1062,1044,1053` | ⚠️ unverified per device |
| 8 | Editor ⋮ dropdown / card ⋮ dropdown | `DropdownMenu` default (closes) | `EditorScreen.kt:1071`, `FileManagerScreen.kt:1360` | ✅ |
| 9 | Coach mark (Phase 45.2) | must close the mark | new | ✅ by design (pinned in the router) |
| 10 | Crash overlay / safe-mode banner | overlay owns it; safe mode never shows the exit prompt | `MainActivity.kt:338` (`CrashReportOverlay`), `:857-859` (`SafeModeBanner`), `:804` (the safe-mode branch) | ✅ |

**Rows 4 and 5 are the owner's bugs. Row 2 is 5.B's mechanism.** The rest are
fine — but nobody can *say* they are fine, because there is no policy to compare
them against. That is what 49.1 adds.

## Why the drawer's back is not simply "add a BackHandler"

Material3 1.3.x's `ModalNavigationDrawer` registers its own back consumer when
the drawer is open (predictive-back support landed in material3
`1.3.0-alpha01`; CodeC is on BOM `2024.12.01` = material3 1.3.1), and the
platform guarantees *"OnBackPressedCallback is always called regardless of the
value of `android:enableOnBackInvokedCallback`"*. So **on paper the drawer
should already close on back** — and the owner says it does not.

Both statements cannot be true, so the first step of 49.1 is to find out which
is, on a device, before writing a line:

| Hypothesis | How to confirm | Fix if true |
|---|---|---|
| H1 — M3's handler is present but loses to `MainApp`'s (dispatch order / an `enabled` flag that is true when it should not be) | Log every callback's `isEnabled` at the moment of the press (a debug `OnBackPressedCallback` added first, so it sees the chain) | Make the drawer's handler explicit and innermost (the router) |
| H2 — the drawer's `isOpen` is false during the open animation, so a fast back misses it | Press back within ~200 ms of the ☰ tap | Router keys off `targetValue`, not `currentValue` |
| H3 — the press is being consumed by the sora `AndroidView` / a Compose key handler before the dispatcher | Reproduce with the editor empty vs. with a file open | Router + `onPreviewKeyEvent` audit |
| H4 — the device is a gesture-nav phone where the swipe is a *home* gesture, not back | Test the same build with 3-button navigation | Not a bug — but 49.2 must then be honest that a home swipe never shows the prompt on any device |

H4 matters beyond the drawer: it is one of the two real explanations for 5.B,
and it cannot be fixed in code — only explained, and then designed around
(the prompt is reachable from Settings → Feedback too).

## Design

### 49.1 — `BackRouter`: one precedence table, pinned by CI

```kotlin
// app/src/main/java/com/codeci/ide/ui/navigation/BackRouter.kt   (pure)
data class BackState(
    val unsavedChanges: Boolean,
    val editorDrawerOpen: Boolean,          // targetValue == Open  (H2)
    val hubProjectOpen: Boolean,            // the hub's file tree is showing
    val coachMarkVisible: Boolean,
    val sheetOrDialogOpen: Boolean,         // bottom sheet / branch sheet / git sheet
    val findBarOpen: Boolean,
    val outputPanelExpanded: Boolean,
    val canPopRoute: Boolean,               // navController.previousBackStackEntry != null
    val atRootDestination: Boolean,
    val exitPromptEnabled: Boolean,
    val exitPromptVisible: Boolean,
    val safeMode: Boolean
)
enum class BackAction {
    ShowUnsavedDialog, CloseCoachMark, CloseEditorDrawer, CloseHubProject,
    CloseFindBar, CollapseOutputPanel, PopRoute, ShowExitPrompt, ExitApp, None
}
object BackRouter { fun decide(state: BackState): BackAction }
```

The precedence, in the order the table is written (and tested):

```text
1  unsavedChanges            -> ShowUnsavedDialog
2  coachMarkVisible          -> CloseCoachMark
3  editorDrawerOpen          -> CloseEditorDrawer        (owner 4.iv, editor half)
4  hubProjectOpen            -> CloseHubProject          (owner 4.iv, hub half)
5  sheetOrDialogOpen         -> None                     (the sheet's own handler owns it)
6  findBarOpen               -> CloseFindBar
7  outputPanelExpanded       -> CollapseOutputPanel      (only when the keyboard is down)
8  exitPromptVisible         -> ExitApp                  ("tap back again to exit")
9  canPopRoute               -> PopRoute
10 atRootDestination         -> ShowExitPrompt (or ExitApp when disabled / safe mode)
11 else                      -> None
```

Rules that make it safe:

- **`None` means "let the library handle it"**, not "do nothing". Sheets and
  dropdowns keep their own handlers; the router never closes a sheet it does not
  own.
- **One handler per screen, one call.** `BackHandler(enabled =
  BackRouter.decide(state) != BackAction.None) { … }` replaces the ad-hoc
  handlers. `MainActivity`'s root handler stays the last one in the chain.
- **Safe mode never shows the prompt** (`MainActivity.kt:804-808` today) — row 10
  carries that, so the rule survives the refactor instead of living in a
  `when` branch.
- **Row 8 must not double-fire.** Verified today: the root handler is
  `BackHandler(enabled = !exitPromptVisible)` (`MainActivity.kt:801`), so while
  the prompt is up the root handler is *off* and the second press is handled by
  the dialog itself — `ExitFeedbackDialog.kt:59` is
  `onDismissRequest = onExit, // "tap again to exit": back = the second press`
  (the KDoc at `:39` states it as the law). If the router also claims
  `exitPromptVisible → ExitApp`, the app must keep exactly one of the two
  paths: either the handler stays disabled while the prompt is up (today's
  shape, recommended — the dialog owns its own exit) or the router takes it and
  the dialog's `onDismissRequest` becomes a plain dismiss. **Decide once, in
  the part file, before wiring.**
- The router is **pure and total**: every field combination yields an action.
  `BackRouterTest` walks the interesting combinations (not 2¹² — the
  precedence pairs) and pins the table. A future screen that needs a new row
  must add it to the enum, which fails every existing test until the table is
  updated. That is the point.

### 49.2 — the exit prompt, decided from state

Today: `if (!navController.popBackStack()) { … prompt … }`
(`MainActivity.kt:802`). Whether the prompt appears therefore depends on the
back stack, which depends on:

- **which destination the app started at** — `EditorLaunchState` (a returning
  user) vs `Screen.FileManager` (a fresh install), `MainActivity.kt:706-717`;
- **which tab the user is on** — a tab tap pushes a second entry
  (`popUpTo(start) { saveState = true }`), so the first back pops silently;
- **how the user closes the app** — a gesture-nav home swipe or a Recents swipe
  sends **no back event at all**, so the prompt cannot appear.

49.2 replaces the query with a state decision:

```kotlin
val atRoot = BackRouter.isRoot(currentDestination, screens)   // pure: one of the five tabs
// row 10 of the table, with no popBackStack() in the condition
```

and makes three behaviours explicit and identical on every device:

| Situation | Behaviour after 49.2 |
|---|---|
| Back at a **non-start** tab | `PopRoute` (to the start tab) — unchanged, but now *documented and tested* rather than incidental |
| Back at the **start** tab, nothing open | `ShowExitPrompt` (or `ExitApp` when the switch is off / safe mode) |
| Back at the start tab **with the prompt up** | `ExitApp` — the "tap again" contract, unchanged |
| Home swipe / Recents swipe | no prompt, on every device — and the prompt is also reachable from Settings → **Feedback & Support**, so it is not the only door |

**What must not change** (all of it already true, all of it pinned by
`ExitSurveyTest`): nothing is uploaded by itself; `NOT NOW` stays in the app;
`EXIT` closes; outside taps do nothing; the switch
(`feedback_exit_prompt_enabled`, default ON) stays on the Feedback screen; safe
mode never shows it; the stars travel only inside a report the user sends.

## Exit condition

```text
49.1
1. Editor: ☰ opens the drawer → back closes the drawer, editor still there.
   (Verified with the drawer opened <200 ms before the press — H2.)
2. Projects: open a project (file tree) → back returns to the project list, not
   the app exit.
3. Find bar open → back closes it. Output panel expanded (keyboard down) → back
   collapses it. Coach mark → back closes it.
4. Unsaved changes beat everything: back with the drawer open AND a dirty buffer
   shows the unsaved dialog first.
5. Sheets and dropdowns still close on back exactly as before (the router
   returns None for them).
6. Web Preview / Logs / Feedback: back returns to the previous screen.
49.2
7. On a 3-button-nav phone: back at the start tab shows the prompt; back again
   exits. Identical on a fresh install (start = Projects) and an upgrade
   (start = last editor file).
8. On a gesture-nav phone: the same, using the back swipe; the home swipe closes
   the app with no prompt (documented, not "fixed").
9. With the switch OFF: back exits directly, on every device.
10. In safe mode: back exits directly, no prompt.
PASS = all ten, on at least two devices (Phase 50 owns the matrix).
```

## Tests (plan)

- `BackRouterTest` (host, the centrepiece): the precedence pairs —
  unsaved × drawer, drawer × hub project, coach mark × drawer, find bar ×
  output panel, prompt-visible × can-pop, safe mode × prompt enabled, and the
  total-function property (every combination yields a non-null action).
- `BackRouterRootTest`: `isRoot` for each of the five tab routes including the
  parameterised editor/terminal routes (`editor?projectName=…`), which is where
  a `startsWith` comparison has bitten this codebase before
  (`MainActivity.kt:731-733` does the same trick for `inEditor`).
- `ExitPromptPolicyTest`: the four situations in 49.2's table, and the
  invariants list (no upload, NOT NOW stays, EXIT closes, outside taps inert).
- `ExitSurveyTest` (existing) keeps passing untouched.
- Robolectric `BackWiringTest` (if the harness allows): each screen's
  `BackHandler` is enabled exactly when the router says so — otherwise a
  source-scan test asserting every `BackHandler(` in `app/src/main` calls
  `BackRouter.decide(` (the cheap, certain version).

## Sources

- CodeC 2026-09-12: `MainActivity.kt:632-641,706-747,768-812,824-831,990-1136`,
  `EditorScreen.kt:646,900-907,1071`, `FileManagerScreen.kt:298-306,516-537,
  1044-1080,1360`, `ui/support/ExitSurvey.kt`, `ui/support/ExitFeedbackDialog.kt`,
  `ui/crash/SafeMode.kt`, `AndroidManifest.xml` (no `enableOnBackInvokedCallback`).
- [developer.android.com/guide/navigation/custom-back/predictive-back-gesture](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
  — callback chain-of-responsibility; *"the innermost PredictiveBackHandler or
  BackHandler takes precedence"*; *"OnBackPressedCallback is always called
  regardless of the value of android:enableOnBackInvokedCallback"*.
- [stackoverflow.com/q/79247909](https://stackoverflow.com/questions/79247909)
  — *"your back handler ends up on the bottom of the stack of back handlers,
  below the ones of ModalNavigationDrawer and NavHost, and only the top active
  back handler is called."*
- [stackoverflow.com/q/76564309](https://stackoverflow.com/questions/76564309)
  — the one-line `BackHandler(enabled = drawerState.isOpen)` remedy (what 49.1
  generalises instead of copying).
- [developer.android.com/codelabs/predictive-back](https://developer.android.com/codelabs/predictive-back)
  — material3 1.3.0-alpha01+ requirement for the drawer's predictive back.
- `docs/PHASE44_50_UX_RESEARCH.md` §6.

## Deferred, recorded on purpose

- **Enabling predictive back** (`android:enableOnBackInvokedCallback="true"`) —
  a real improvement and the natural follow-up, but it is a whole-app migration
  (every `activity.finish()` becomes a callback, NavHost needs its predictive
  path) and `targetSdk = 28` is load-bearing for exec-of-app-data. The router is
  deliberately written so enabling it later is additive, not a rewrite.
- **Double-back-to-exit without a dialog** — that is 49.2 with the prompt
  disabled; already available via the switch.
- **Per-tab back stacks (multiple back stacks)** — navigation-compose supports
  it, but it changes what "back" means on every tab and would invalidate the
  table above; not asked for.
- **Closing the app on a tab long-press** — invented requirement; no.
