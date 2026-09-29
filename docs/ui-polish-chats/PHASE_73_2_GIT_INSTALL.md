# Phase 73.2 — Git connection: auto-install, clear errors, new-user guidance

**Status: 🚧 IMPLEMENTED (2026-09-29), CI pending; session branch
`arena/01a0eb2d-codec`. Owner device pass owed. Not one of the numbered
UI-polish discussion drafts (65.1/74.1 remain untouched) — this is a
rule.md §4 bug/improvement lifecycle item the owner asked for directly, in
the same Git area 73.1 just touched.**

## The owner's report (verbatim, 2026-09-29)

> "The git connection in the editor part make it properly working: 1. If
> git is not installed auto install 1st. 2. Clear error massage that can be
> read by normal users. 3. What to do add for new users."

## Evidence read before any code (source only, no device)

- `GitContext.gitBinary()` resolves the packaged `git` at `$PREFIX/bin/git`;
  `GitControlViewModel` sets `state.gitInstalled = false` when it is absent,
  and the sheet showed one static sentence
  (`R.string.git_not_installed_message`) pointing the user at Modules or a
  terminal command — no in-place action.
- `GitErrors.classify()` already maps ~12 raw git failure shapes (offline,
  missing token, rejected push, merge conflict, detached HEAD, etc.) to
  plain-English `GitErrorKind`s with a suggested next step each; only the
  generic fallback shows a short redacted raw-stderr snippet. This is
  already mostly what the owner's point 2 asks for — no changes made to it
  this part; recorded as a finding, not silently skipped.
- `git_not_a_repo_message` (the new-user guidance for an untracked folder)
  assumed the reader already knows what "a Git repository" is; it named the
  fix but never said what Git/Source Control is *for*.
- The Packages tab (Phase 71.1) already has a real one-tap install
  mechanism: `terminalViewModel.sendCommand(item.installCommand)` runs the
  actual `pkg install -y <pkg>` in the shared, activity-scoped terminal
  session, and a `LaunchedEffect` polls the pure, already host-tested
  `PkgResult`/`InstallOutcomes` (`WAITING` / `INSTALLED` / `FAILED` /
  `ENDED_WITHOUT_INSTALL`) to know when it's done. `git` is already a
  `PackageItem` in `PackageCatalog.ALL_PACKAGES` (`installCommand =
  "pkg install -y git"`) — the single source of truth for the command, not
  duplicated.
- `SetupGatePolicy.can(SetupAction.INSTALL_PACKAGE, setupFacts)` already
  tells the Packages tab whether the Linux userland itself is ready enough
  to run `pkg install` at all. The bigger, separate ~40 MB userland
  bootstrap (`UserlandInstaller`) is only ever triggered from Terminal's own
  lifecycle, confirming "userland not set up yet" is a real, if rare, edge
  case (a fresh install that never opened Terminal).

## Owner's decisions (asked via `ask_user`, answered verbatim before coding)

1. **Auto-install = a one-tap button, not silent.** Show "Install Git"
   inline where the message is today; tapping runs the real install in the
   background with a visible progress line and no confirmation dialog
   (small package), then continues automatically. No install happens
   without a tap.
2. **If the Linux userland itself isn't ready yet** (rare): keep today's
   behaviour exactly — point the user at Terminal to set it up, then come
   back. Do **not** trigger the big userland bootstrap from the Git sheet.
3. **New-user guidance = better wording only.** Rewrite the not-a-repo
   message in plain words a total beginner can follow — no new action
   buttons (no "Initialize repository here").

## What shipped

- `GitControlSheet` now gets its own `TerminalViewModel` handle via the
  existing `activityTerminalViewModel()` helper (Activity-scoped singleton,
  same package as `TerminalScreen.kt` — no navigation, no new ViewModel).
  `TerminalViewModel.sendCommand()` self-queues and starts a shell session
  if none exists yet, so this works even if the user has never opened
  Terminal.
- The not-installed branch now checks
  `SetupGatePolicy.can(SetupAction.INSTALL_PACKAGE, setupFacts).allowed`
  (the exact same gate the Packages tab uses):
  - **Allowed** (the common case): a new `GitInstallGuidance` composable —
    one explainer sentence, then an `INSTALL GIT` button. Tapping it sends
    `PackageCatalog.ALL_PACKAGES.first { it.id == "git" }.installCommand`
    ("pkg install -y git") to the shared terminal, shows a short toast, and
    starts a `LaunchedEffect` polling loop (1.5 s cadence, `PkgResult.read`
    + `GitContext(...).gitBinary() != null` on `Dispatchers.IO`, decided by
    the pure `InstallOutcomes.decide(...)`) — identical shape to the
    Packages row's own loop, minus the navigate-to-Terminal step: on
    `INSTALLED` the sheet calls `viewModel.refresh(...)` and stays open; on
    `FAILED` it shows a one-line failure message plus RETRY (reusing
    `R.string.install_label_retry`, the same string the Packages tab
    retry button already uses); on `ENDED_WITHOUT_INSTALL` it silently
    resets, matching the Packages convention (no accusatory error for an
    ambiguous outcome).
  - **Refused** (userland not ready — owner decision #2): today's message
    (`R.string.git_not_installed_message`) is shown **unchanged**, byte for
    byte — no new button, no auto-trigger of the userland bootstrap.
- New strings: `git_install_explainer`, `git_install_action`
  ("INSTALL GIT"), `git_installing` ("Installing git…", reused as both the
  inline progress line and the toast text), `git_install_failed_message`.
  `git_not_installed_message` itself is untouched (owner decision #2).
- `git_not_a_repo_message` rewritten in plain words: states what Git does
  (keeps a history of your code, can back it up to GitHub) before naming
  the same two existing recovery paths (Clone from GitHub, or `git init`
  in Terminal) — no new action, per owner decision #3.
- `GitErrors.notInstalled()`/`GitErrors.classify()`: reviewed, found
  already good for the owner's point 2 (12 friendly error kinds with a
  suggested next step each); **no changes made** — recorded as a finding
  rather than silently skipped.
- Explicitly **out of scope** this part: the Clone-from-GitHub dialog
  (`FileManagerViewModel`) has its own, older "git not installed" path from
  Phase 40.1 — the owner's request named "the editor part" (the Source
  Control sheet), so the Clone dialog is untouched unless asked later.

## Tests

`app/src/test/java/com/codeci/ide/GitInstallWiringTest.kt` — source-scan
style (no Robolectric Compose render exists for this sheet, matching
`GitDiscardWiringTest`'s own precedent): pins that the install button only
renders when the gate allows it and the refused branch keeps today's
message unchanged; that the tap handler sends the exact catalog install
command with no confirmation dialog and never navigates to Terminal; that
the polling loop calls the real `PkgResult`/`InstallOutcomes` API (not a
bespoke duplicate) and reacts to `INSTALLED`/`FAILED`; that the not-a-repo
branch stays text-only; and that the new strings exist and the rewritten
message still names both recovery paths.

Pre-validated with a local kotlinc 2.4.20 + JRE 25 syntax check on the
changed file (`GitControlView.kt`): brace/paren counts balanced, and every
compiler error was an `unresolved reference` against Android/Compose
classes missing from that ad-hoc classpath (expected — this is not a real
compile), with zero syntax-level errors (`expecting`/`unexpected tokens`).
This is a syntax reading, not CI.

## Evidence to be added once available

- CI run id + tip sha once `Build APK` finishes on this branch.
- Owner device pass (not claimed here).
