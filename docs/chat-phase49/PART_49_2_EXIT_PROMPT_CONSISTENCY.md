# CodeC Phase 49.2 — The exit prompt on every device, or nowhere

> **Status:** 🚧 IMPLEMENTED (2026-09-13, `arena/01a09925-codec`; CI ✅
> GREEN `34740245825` tip `a592295`; the eight-check device round below is
> pending — at least two devices, one 3-button-nav and one gesture-nav) ·
> **Cost:** `[client-only]` · **Effort:** S/M ·
> **Owner row (verbatim):** *"B. My phone showing the option when try to close
> not now option but in most phone no option like not now or exit"* —
> clarification 2026-09-12: **keep it ON, make it consistent.**

## What the feature is (and what it never does)

The dialog is the *only* place the store's stars are collected
(`ExitFeedbackDialog.kt:44-47`), it feeds the exit-survey funnel
(`ExitSurvey.kt:1-6`), and it is wired at `MainActivity.kt:768-771`. Its laws,
already true and all of them re-pinned here:

- **no automatic uploads** — the stars ride *inside* a report the user sends;
- **`NOT NOW` stays in the app**; **`EXIT`** closes it; **outside taps do
  nothing**;
- the switch `feedback_exit_prompt_enabled` (default ON, `MainActivity.kt:768`)
  lives on the Feedback screen (`SettingsScreen.kt:1168-1185`);
- **safe mode never shows it** (`MainActivity.kt:804-808`);
- **the second press is the dialog's own** — the root handler is
  `BackHandler(enabled = !exitPromptVisible)` (`:801`) and the dialog's
  `onDismissRequest = onExit` carries *"back = the second press"*
  (`ExitFeedbackDialog.kt:39,59`). 49.2 keeps that shape and must not add a
  second exit path (see 49.1's row-8 note).

## Why it is device-dependent today

The condition is a *navigation* question, not a *state* question:

```kotlin
BackHandler { if (!navController.popBackStack()) { … show the prompt … } }   // :801-809
```

Three independent reasons a phone can therefore never see it:

| # | Cause | Who it affects | Fixable in code? |
|---|---|---|---|
| **A** | **Tab taps push a second entry.** `MainActivity.kt:824-831` does `popUpTo(startDestination) { saveState = true }` on every tab change, so on any non-start tab the first back *pops* (returning to the start tab) and the prompt never runs. The user then sees the app "not respond to back", presses again, and only now gets the prompt — or gives up. | Everyone who taps a tab — i.e. almost everyone. This is the likeliest explanation for *"in most phone no option"*. | ✅ yes |
| **B** | **The start destination differs per install — three ways.** `EditorLaunchState` present (a returning user) → start = `Screen.Editor` (`:712`); otherwise → `Screen.FileManager` (`:713`); and in **safe mode** `EditorLaunchState` is not loaded at all (`:709` — `if (SafeMode.active) null else EditorLaunchState.load(activity)`), so a crash-looped install starts somewhere else again. Same build, different first back. | Upgrades vs fresh installs vs safe mode | ✅ yes |
| **C** | **How the user closes the app.** A home swipe (gesture nav) or a Recents swipe sends **no back event** — the activity is stopped, not finished via back. No handler can show a dialog there. | Gesture-nav phones; most modern devices for the "close the app" gesture | ❌ not fixable — only designable around |
| **D** | (to be ruled out) the store value differs — e.g. a user who once turned the switch off, or a partially written preference | one device | ✅ by re-checking the store |

**Already-proven data point.** The Phase 41 owner runbook tested this dialog and
it **passed on the owner's phone** — `docs/chat-phase41/DEVICE_TEST_PLAN.md`,
checks D2 (*"From the Projects tab (or wherever you start), press BACK once →
The 'Enjoying CodeC? 💚' popup appears"*), D3 (back again → exits), D6
(`NOT NOW` → stays open) and D7 (switch off → exits directly, *"press BACK (at
a root tab)"*). So the dialog, its copy and its switch all work; what differs
between phones is **whether the press reaches the root handler at all** — which
is exactly causes A, B and C, not a broken dialog. Note that D7's own wording
already had to qualify the check with *"at a root tab"*: the runbook author hit
the same ambiguity this part removes.

Cause C is why the honest promise is *"the prompt appears on every **back** press
at the root, on every device"* — not *"the prompt appears whenever you leave the
app"*. The compensation for C is a **second door**: Settings → **Feedback &
Support** already exists (`:1168`); 49.2 adds one row there —
**"Tell us before you go"** — that opens the same dialog on demand. A tester who
never sees the prompt can still reach it, and the owner's testing does not
depend on which phone is in hand.

**Evidence gate:** cause A/B are read straight from the code; D is a hypothesis.
49.1's diagnostic log records, per device, `exitPromptEnabled`, the start route
and the back-stack depth at the moment of the press — three numbers that settle
A, B and D without guessing. If the log shows the prompt firing on a device where
the tester says it did not appear, the cause is C and the fix is the second door
plus the doc, not more code.

## Design

1. **Decide from state, not from `popBackStack()`** — row 10 of the
   `BackRouter` table (`PART_49_1_BACK_ROUTER.md`):

   ```kotlin
   val atRoot = BackRouter.isRoot(currentRoute, screens)   // pure; parameterised-route safe
   ```

   The root handler then shows the prompt whenever `atRoot` is true and nothing
   higher-precedence applies — so a tab-tapped stack no longer hides it.

2. **Make the tab-tap back behaviour explicit.** It stays as it is (back from a
   non-start tab goes to the start tab — the platform's own bottom-nav
   behaviour), but now it is a tested row rather than an accident, and the
   *second* back at the root always produces the prompt. If the 49.1 log shows
   testers confused by the first back, the follow-up (recorded, not built) is a
   one-line toast: *"Back again to exit"*.

3. **The second door** — Settings → Feedback & Support gains
   **"Tell us before you go"**, which calls the same `showExitPrompt` path. No
   new dialog, no new copy: the dialog's own subtitle already reads *"Optional.
   Nothing is sent unless you choose Send below."*

4. **The `SETTINGS_AUDIT.md` row** — the new row is added in the same commit, or
   `SettingsAuditTest` fails the build. That is the mechanism working as
   intended.

## Exit condition

```text
1. Fresh install (start = Projects): back at the root → prompt. NOT NOW → stays.
2. Upgrade (start = Editor, a file open): back at the root → prompt.
3. Tap Terminal (non-start tab) → back → lands on the start tab; back again →
   prompt. Same on every device tested.
4. Prompt up → back → the app exits.
5. Switch OFF → back exits directly, no prompt, on every device.
6. Safe mode → back exits directly, no prompt.
7. Settings → Feedback & Support → "Tell us before you go" opens the same
   dialog on any device, including one where the prompt never appeared.
8. Stars are never uploaded by the dialog alone (existing ExitSurveyTest).
PASS = all eight on at least two devices — one 3-button-nav, one gesture-nav.
```

## Tests (plan)

- `ExitPromptPolicyTest` (host): the four states (root+enabled → prompt;
  root+disabled → exit; root+safe mode → exit; non-root → pop) from the same
  inputs `BackRouter` uses, so the two cannot drift apart.
- `isRoot` for the five tab routes including parameterised editor/terminal
  routes (shared with `BackRouterRootTest`).
- `SettingsAuditTest` (existing): gains the new row — added in the same commit.
- `ExitSurveyTest` (existing): untouched and passing; the upload invariant is
  already pinned there.
- `SettingsKeysHaveReadersTest` (existing): no new key is added, so it stays
  green — asserted by running it.

## Sources (record)

- CodeC 2026-09-12: `MainActivity.kt:706-717` (start destination),
  `:768-771` (prompt wiring), `:801-809` (the `popBackStack()` condition),
  `:804-808` (safe mode), `:824-831` (tab taps + `saveState`),
  `SettingsScreen.kt:1168-1185` (the switch), `ui/support/ExitSurvey.kt`,
  `ui/support/ExitFeedbackDialog.kt:39-47,59`.
- `docs/chat-phase41/DEVICE_TEST_PLAN.md` — checks D2, D3, D6, D7: the dialog
  and its switch **PASSED on the owner's device**, which narrows 5.B to "the
  press never reaches the root handler" rather than "the dialog is broken".
- Platform: the back stack's `popUpTo { saveState }` semantics (the code itself
  is the source); gesture navigation sends no back event on a home swipe.
- `docs/PHASE44_50_UX_RESEARCH.md` §6.3 (the four causes and the "second door"
  decision).

## Deferred / rejected with reasons

- **Showing the prompt on a home swipe** — impossible: no back event is
  delivered. The second door is the honest answer.
- **Removing the prompt** (the other option the owner was offered) — rejected by
  the owner; it is the only star-collection surface and it costs one tap.
- **"Back twice to exit" toast instead of a dialog** — recorded as the follow-up
  if the 49.1 log shows the tab-tap back confusing testers; not built now.
- **Remembering the start destination across launches** — `EditorLaunchState`
  already does this; making it user-visible would add a preference nobody asked
  for.

## Implementation (2026-09-13)

**The decision moved from navigation to state — row 9/10 of the router.**
`MainActivity`'s root handler no longer asks `popBackStack()`; it asks
`BackRouter.isRoot(currentDestination?.route, screens.map { it.route })`
(parameterised-route safe, `BackRouterRootTest`), so:

- **Cause A (tab taps push a second entry)** dies: back on a non-start tab
  is `PopRoute` (the platform's own bottom-nav behaviour, now a tested row
  instead of an accident), and the back AFTER that — at the root route —
  shows the prompt on every device, because the route says "root" whether or
  not the stack happens to have a second entry under it.
- **Cause B (per-install start destination)** dies the same way: `editor`,
  `file_manager`, safe mode — all five tab patterns answer `isRoot` true, so
  "first back at the start" behaves identically everywhere.
- **Cause C (home/Recents swipe)** is not code-fixable and stays honestly
  documented — with its compensation built (below).

**The second door:** Settings → **Feedback & Support** → **"Tell us before
you go"** (`SettingsAction`, SHOW) → `onShowExitPrompt` → the host's ONE
`exitPromptVisible` flip — the same dialog, zero new copy, zero new keys. A
tester whose phone never shows the prompt can still reach it, and the
exit-survey funnel stops depending on which device is in hand. The
`SETTINGS_AUDIT.md` row (row 48) was added in the same commit, so
`SettingsAuditTest` stays green — the mechanism working as intended.

**The `tap again to exit` contract kept:** the root handler is
`BackHandler(enabled = !exitPromptVisible && rootBackAction != None)` — while
the dialog is up the handler stands down and the dialog's
`onDismissRequest = onExit` IS the second press. Exactly one exit path, as
Phase 41 pinned it; `ExitSurveyTest` (nothing uploads by itself) untouched
and passing.

**The evidence gate:** every root press now logs
`AppLogger.i("Back", "root press route=… promptEnabled=… promptVisible=… ->
…")` — the three numbers (route, switch, decision) that settle causes A, B
and D per device without guessing, readable from Settings → Developer
Options → View App Logs during the owner's round.

**Exit condition status:** the eight device checks are the owner's round on
at least two devices (one 3-button-nav, one gesture-nav — check 7 is the
gesture-nav compensation). Automated halves in CI: `ExitPromptPolicyTest`
(fresh install / upgrade / tab-tap / root-arg variants / switch off / safe
mode / the second-press shape), `BackRouterRootTest`, the second-door pins in
`BackHandlerWiringTest`, `SettingsAuditTest` (row 48 in the same commit),
`SettingsKeysHaveReadersTest` (no new key — untouched by construction),
`ExitSurveyTest` (the upload invariant).

## Test log (Phase 50 — the cross-device matrix)

> The 8 rows of the cross-device matrix that this part owns ([`../chat-phase50/DEVICE_MATRIX.md`](../chat-phase50/DEVICE_MATRIX.md)). `⏳` = not run. Paste the tester's result into `Result` (✅, or ❌ + the text the screen really showed, verbatim) and the device identity line — the first line of Settings → Feedback & Support → COPY REPORT — into `Evidence`, one line per device class that ran it. `DeviceMatrixTest` pins this table both ways: every row here must exist in the matrix with `49.2` in its `Part` column, and every matrix row owned by this part must be listed here. That is Phase 50's exit 4, enforced by CI instead of by memory.

| Row | Result | Evidence (device · OS · nav mode · what was seen) |
|---|---|---|
| I9 | ⏳ | |
| I10 | ⏳ | |
| I11 | ⏳ | |
| I12 | ⏳ | |
| I13 | ⏳ | |
| I14 | ⏳ | |
| I15 | ⏳ | |
| J6 | ⏳ | |

**Already on record:** the eight prompt checks passed on the owner's phone
at round level (2026-09-13). 5.B was *literally* a "different on other phones"
report, so I9-I12 on **both** nav modes and both start destinations are the whole
point of this phase — a one-device pass does not close 5.B. I15 is the diagnostic
the fix ships with: paste the line with any failure.

