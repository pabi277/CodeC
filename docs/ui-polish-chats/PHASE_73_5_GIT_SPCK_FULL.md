# Phase 73.5 — Git panel: Spck-exact layout, install status bar, inline credentials

**Status: 🚧 IMPLEMENTED (2026-09-29), CI ✅ GREEN `36560766234` on tip
`d469d52`; session branch `arena/01a0eca9-codec` (copied whole from
`arena/01a0eb2d-codec`, tip `5e242f9`, then this phase on top). Owner device
pass owed, not merged (rule.md §3). Not one of the numbered UI-polish
discussion drafts (65.1/74.1 remain untouched) — this is a rule.md §4
bug/improvement lifecycle item the owner asked for directly, in the same Git
area 73.1–73.4 just touched, superseding two 73.3 decisions (see below).**

## The owner's report (verbatim, 2026-09-29)

> "Ok i want to convert the git part of the editor fully terminal
> independent gui … I attached some screenshot analysis them and convert
> into real think in app And the 1st git install will be in terminal but
> not be redirected to terminal instead a status ber for the installation
> completion Settings have a git config place merge that also here so here
> also i can set like the provide screenshot"

Four Spck Editor screenshots attached (the same set 73.3 ported the *menu
items* from — this phase ports the *layout*):

1. The branch dropdown: **Branches / Remotes / Log History / Refresh
   Files**, each with an icon.
2. The Git Credentials dialog: **PROVIDER** row (`GitHub ▾` + `Manage`
   link), username / email / token fields, a *"Create a GitHub Token."*
   link, **Cancel / Ok**.
3. The push dropdown: a **BRANCH: MAIN** header, then **Commit All**
   (shortcut hint) / **Revert All** / **Checkout Commit**, a divider,
   **Fetch / Pull / Push**, a divider, **Git Credentials / Provider** (cat
   icon).
4. The panel itself: a **REPOSITORY** header with search / branch-menu /
   push-menu icons on the right, and an **UNSTAGED** collapsible section
   with a `+` button and a `0` count badge.

## Evidence read before any code (source only, no device)

- 73.3's overflow menu already owned every screenshot action *except* the
  layout: Fetch/Log History/Checkout Commit/Remotes/Revert All plus
  Commit/Push/Pull as buttons. Missing for screenshot parity: the two-menu
  split, the BRANCH header, Commit All as a menu item, Refresh Files as a
  menu item, Provider, the search icon, the UNSTAGED section shape.
- 73.2's install already runs in the shared terminal session *without*
  redirecting (`sendCommand` only queues into the session; the sheet stays
  put) — but the only progress surface was a spinner row plus a toast.
  `pkg` reports no percentage (`PkgResult` only records the end state), so
  elapsed time is the honest progress signal.
- Settings' GitHub Account card already edits all four stored values
  (token, username, commit name, commit email) through
  `GitCredentialsStore` — the merge needs a second *editor*, not a second
  store.
- `sendCommand` never navigates (`TerminalViewModel.kt`) — confirmed the
  "no redirect" half of the install ask is already true and only the
  status bar is new.

## Owner's decisions (asked via `ask_user`, answered before coding)

1. **Full Spck-style restyle** — REPOSITORY header with the 3 icons, the
   two dropdown menus exactly like screenshots 1+3, UNSTAGED section with
   count badge. Commit box + changes + pull/refresh stay below, behaviour
   unchanged.
2. **Commit All = commit only, no push** — a new engine path (stage +
   commit locally); the big button keeps doing commit-and-push.
3. **Provider opens the same credentials dialog** as Git Credentials (both
   menu items, one dialog; GitHub is the only provider).
4. **All 4 credential fields** — token + username + commit name + commit
   email (nothing lost), Spck-style rows, plus the provider row and the
   "Create a GitHub Token" link. Settings keeps working as today.

## What shipped

- **Spck-exact header** (`GitControlView.kt`): `REPOSITORY` title with
  search / branch-menu / push-menu icons, gated on the same
  `gitInstalled && isRepo` readiness 73.3 used. The branch menu is
  screenshot 1 item-for-item (Branches opens the existing
  `BranchSwitchSheet`; Refresh Files is `refresh()`); the push menu is
  screenshot 3 item-for-item (BRANCH header, Commit All, Revert All,
  Checkout Commit, Fetch, Pull, Push, Git Credentials, Provider). The
  branch chip keeps its own row below the header so the current branch —
  or 73.3's detached-HEAD chip — stays visible without opening a menu.
- **UNSTAGED section**: the changes list renamed, collapsible, with the
  count badge; the header search icon toggles a field that filters the
  list by path (display only — commit/discard still act on the real
  change set). Screenshot 4's `+` stage-all button is deliberately NOT
  ported: 73.1 removed staging controls because commit always runs
  `add -A`, so a stage button would be a no-op dressed as an action.
  Likewise screenshot 3's shortcut hint is not shown next to Commit All:
  no `Ctrl+Enter` handler exists in this sheet today, and the menu must
  not advertise a key that does nothing.
- **Install status bar** (`GitInstallGuidance`): elapsed-seconds label +
  indeterminate `LinearProgressIndicator` + "stay here" note. The command
  still runs in the shared session underneath; the bar finishes in-panel
  (success refreshes in place, as 73.2 did). `ENDED_WITHOUT_INSTALL`
  (`pkg` ended but git still not on disk) now shows the same failed +
  RETRY state as a non-zero exit instead of silently reverting to the
  INSTALL button.
- **Inline Git Credentials dialog** (new `GitCredentialsDialog.kt`):
  screenshot 2's shape — title + close, PROVIDER row (GitHub is honestly
  the only entry; the app's push/publish/discovery are all GitHub-shaped),
  `Manage` link (jumps to Settings' own editor), the four fields (token
  with SHOW/HIDE), the "Create a GitHub Token." link (same
  `TOKEN_HELP_URL` Settings uses), Cancel / Ok. Saves through the shared
  `GitCredentialsStore` and refreshes the sheet, so a fresh token can
  clear the NO_TOKEN blocker without a manual refresh.
- **Commit All** (`GitControlViewModel.commitOnly`): same guards as
  COMMIT & PUSH (message required, conflicts block), same `stageAll`
  choke point with the hygiene note, then commit — no push. The existing
  "N commit(s) not pushed yet" section offers the follow-up PUSH, so
  Commit All → PUSH reads as one honest two-step flow.

Supersedes from 73.3: (1) the single overflow menu is split into the two
screenshot menus (every 73.3 action is still present, same ViewModel
calls); (2) Git Credentials is now an inline dialog, not a jump to
Settings (only the dialog's Manage link still jumps there).

## Tests

- `GitSpckPanelWiringTest.kt` (new, 8 cases): header/menus shape, search
  filter, UNSTAGED collapse, `commitOnly` (stages + commits, guards,
  never `pushCapturing`), the dialog's four fields/shared store/Spck
  shape, save-refresh + Manage wiring, all 29 new strings.
- `GitGuiParityWiringTest.kt` updated for the two-menu split (73.3's
  overflow anchors replaced; every behaviour pin kept) and `commitOnly`
  added to the `runGitOperation` list.
- `GitInstallWiringTest.kt` extended: status-bar shape (progress bar +
  elapsed + stay-here note, no spinner) and `ENDED_WITHOUT_INSTALL` →
  failed + retry.
- `GitDiscardWiringTest.kt` anchor updated for the search-filtered list
  (`visibleOthers`; same call site, same row contract).
- No JVM/kotlinc in this sandbox (same as 73.3) — verified instead by a
  proper Kotlin state-machine brace/paren scan of every touched file
  (balanced, no transient negatives) and a Python mirror of Kotlin's
  `substringAfter`/`substringBefore`/`contains` semantics run against
  every new or changed test assertion before pushing (all passed). That
  scan caught two real edit-tool glitches before commit: a duplicated
  file tail in `GitControlView.kt` (removed) and a silently un-applied
  `GitInstallGuidance` rewrite (re-applied and re-verified).

## Evidence

- **CI: ✅ GREEN.** `Build APK` run `36560766234` on tip `d469d52` — both
  APKs assembled, all host unit tests passed (including the new
  `GitSpckPanelWiringTest.kt`'s 8 cases). One fixup round: the first push's
  run `36559721476` failed `compileDebugUnitTestKotlin` (a backtick test
  name contained a `:`, illegal in a JVM method name — fixed `d469d52`).
- **Device:** not run this session; none claimed.
