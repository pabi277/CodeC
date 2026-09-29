# Phase 73.3 — Git menu: full GUI parity with Spck (no terminal, except install)

**Status: 🚧 IMPLEMENTED (2026-09-29), CI ✅ GREEN `36544645579` on tip `84b8d79`; session
branch `arena/01a0eb2d-codec`. Owner device pass owed, not merged (rule.md §3). Not one of the
numbered UI-polish discussion drafts (65.1/74.1 remain untouched) — this is a rule.md §4
bug/improvement lifecycle item, in the same Git area 73.1/73.2 just touched, superseding one
73.2 decision (see below).**

## The owner's report (verbatim, 2026-09-29)

> "Now git is fully depend on terminal but want it to be gui not a cli. Mean
> everything will be from buttons no need terminal for that (expect git
> install). Like spck i also attache some screenshot for help. Analysis all
> the screenshot carefully every small details must be note."

Four Spck Editor screenshots were attached. Read in full before any code:

- A search-files box above the tree.
- A branch-icon dropdown: **Branches / Remotes / Log History / Refresh
  Files**.
- A push-icon dropdown: **Commit All / Revert All / Checkout Commit / Fetch
  / Pull / Push / Git Credentials / Provider**.

## Evidence read before any code (source only, no device)

- `GitControlView.kt`/`GitControlViewModel.kt`/`GitManager.kt` already cover
  Commit, Push, Pull, per-file stage/discard, Publish-to-GitHub, and Switch
  Branch (`BranchSwitchSheet.kt` — full parity for Spck's "Branches" item
  already, list + switch + create local/remote-tracking, nothing new needed
  there).
- Missing entirely: a standalone **Fetch** action (it only ran implicitly
  inside Switch Branch's remote-branch discovery), **Log History**,
  **Checkout Commit**, **Revert All**, a general (multi-remote) **Remotes**
  screen, a **Git Credentials** shortcut, and — the one CLI-only escape
  hatch — **Initialize repository** (`git init`) for a folder that isn't a
  repo yet.
- `state.status?.branch?.let { branch -> ... }` made the header's branch
  chip vanish silently whenever `HEAD` was detached — which never happened
  before this phase (nothing produced a detached `HEAD`), but Checkout
  Commit's whole purpose is to detach it. A beginner who tapped Checkout
  Commit would see the branch name disappear with zero explanation.
  `R.string.git_detached_head` ("detached HEAD") already existed in
  `strings.xml` from Phase 17 but was never wired to anything — reused
  as-is for the new chip instead of adding a near-duplicate string.
- `GitCredentialsStore`'s editor already lives inline in `SettingsScreen.kt`
  — "Git Credentials" needed only a menu shortcut into Settings (both
  `EditorScreen` and `FileManagerScreen` already carry an `onOpenSettings`
  callback for their drawer footers; reused, not duplicated).
- `GitManager.push` already resolves the remote name dynamically
  (`firstRemote(root) ?: "origin"`), so adding a general multi-remote screen
  does not change push/pull behaviour for existing single-remote projects.

## Owner's decisions (asked via `ask_user`, answered verbatim before coding)

1. **Scope = full parity.** Port Spck's whole git menu — Fetch, Log
   History, Checkout Commit, Revert All, and a general (multi-remote)
   Remotes screen — not just the minimal git-init fix.
2. **Revert All = yes, with a confirmation dialog.** Add a bulk "discard
   all changes" action (staged + unstaged tracked-file changes reset to
   `HEAD`; untracked files left alone), gated behind a confirm ("this
   cannot be undone") since it is more destructive than the existing
   per-file, unstaged-only discard, which needs no such confirm.
3. **Pacing = one shot.** Implement the whole scope now, this session.

This explicitly **supersedes** one part of Phase 73.2's decision #3 ("new-
user guidance = wording only, no new buttons"): that restriction applied
only to `git init` specifically, and the owner's follow-up here lifts it —
`git init` was the last normal Source Control action still requiring the
terminal, so it is now a real button. The 73.2 wording rewrite stays as
prose, minus the "type git init in the Terminal" sentence (redundant now
that a button does exactly that); Clone is still named as the other
recovery path.

## What shipped

### Engine (`GitManager.kt`)

- `init(root)` — `git init -b main` (matches GitHub's own default branch
  name; explicit, not dependent on the packaged git's own
  `init.defaultBranch`, which CodeC never sets). `-b`/`--initial-branch`
  needs git 2.28+; on failure, falls back to plain `init` + best-effort
  `symbolic-ref HEAD refs/heads/main` (safe pre-first-commit). Refuses to
  run (`require`) if the folder is already a repository.
- `checkoutCommit(root, sha)` — `git checkout <sha>` (detaches `HEAD`).
  `sha` is always one of `log()`'s own full ids in practice, but is still
  checked against a private `FULL_SHA = Regex("^[0-9a-f]{40}$")` before
  reaching argv (defence in depth, matching `isSafeExistingBranch`
  elsewhere in this file).
- `revertAllChanges(root)` — `git reset --hard HEAD`. Deliberately narrower
  than `git clean`: untracked (new) files are never touched, matching what
  most git GUIs mean by "discard all changes". The one destructive action
  here not limited to a single file — the UI confirms before calling it.
- `removeRemote(root, name)` — `git remote remove <name>`, reusing the
  existing `SAFE_REMOTE_NAME` validation and `hasRemote` existence check
  `addRemote` already uses (refuses a name that isn't currently configured).
- `remotesDetailed(root): List<GitRemoteEntry>` — every configured remote
  with its URL (`remoteNames` + one `remote get-url` per name); a remote
  whose URL can't be read shows a null URL rather than dropping the row.
- `log(root, limit=50): List<GitCommitEntry>` — `git log -n <limit>
  --date=iso-strict --pretty=format:%H%x1f%h%x1f%an%x1f%ad%x1f%s%n`. The
  unit-separator (`%x1f`) field delimiter, not a comma or pipe, so an
  arbitrary commit subject can never be mistaken for a field boundary; a
  brand-new repository with no commits yet returns `emptyList()` (no
  history is a normal first-run state, not a fault — never thrown).
- `isSafeRemoteName(name)` — the same check `addRemote`/`removeRemote`
  enforce, exposed for the new Remotes screen's live "is this valid" UI
  feedback (no regex duplicated in the UI layer).
- New pure file `GitLog.kt` (Android-free, process-free, same spirit as
  `GitBranchOps.kt`): `GitCommitEntry`, `GitLogParser` (splits each line on
  `\u001F`, drops blank/malformed lines), `GitRemoteEntry`.

### ViewModel (`GitControlViewModel.kt`)

- New `UiState` fields: `commits`/`commitsLoading`/`commitsError` (loaded
  on demand when the Log/Checkout sheet opens — not on every `refresh()`, a
  repo can have thousands of commits and nothing else in the pane needs
  them), `remotes`/`remotesLoading`/`remotesBusy`/`remotesError`.
- `initRepo`, `fetch`, `revertAll`, `checkoutCommit` — all go through the
  existing `runGitOperation` helper (same busy/message/refresh shape as
  every other button in the sheet; `runGitOperation` already tolerates no
  repository existing yet, which `initRepo` relies on).
  `checkoutCommit`'s result message spells out the consequence in plain
  words: *"Now viewing commit `<sha>` — not on a branch. Switch branches
  anytime to return to your work."* — a beginner who has never heard
  "detached HEAD" still gets told what happened and how to undo it.
- `loadCommits`/`clearCommits`, `loadRemotes`/`addRemote`/`removeRemote`/
  `clearRemotes` — hand-rolled `viewModelScope.launch` (matches `refresh()`'s
  own manual pattern, not `runGitOperation`, since these manage their own
  loading/error fields instead of the shared busy/message ones).
  `addRemote`/`removeRemote` reload the remotes list, then call
  `refresh(context, projectRoot)` since a remote change can affect PUSH
  readiness (`NO_REMOTE` clears once one exists).

### UI (`GitControlView.kt` + two new files)

- Header row: a new overflow `IconButton` (⋮, `MoreVert`) next to the
  branch chip, visible only once `state.gitInstalled && state.isRepo` —
  opens a `DropdownMenu` with **Fetch, Log History, Checkout Commit,
  Remotes, Git Credentials**, a divider, then **Revert All** (styled with
  `MaterialTheme.colorScheme.error`, disabled when there is nothing to
  discard or before the first commit — `state.status?.noCommits`). The
  existing PULL/REFRESH button row is untouched.
- The branch chip is now a `when`, not a single `?.let`: a checked-out
  branch renders exactly as before; a detached `HEAD` (new, from Checkout
  Commit) renders a distinct amber chip using the pre-existing, previously
  unused `git_detached_head` string — still tappable, opening the same
  Switch Branch sheet as the way back. Neither state silently disappears.
- Not-a-repo branch: new private `GitInitGuidance` composable (same shape
  as 73.2's `GitInstallGuidance`) — the (trimmed) explainer text, then a
  real **"START TRACKING THIS FOLDER WITH GIT"** button wired to
  `viewModel.initRepo`, with an inline "Setting up Git…" progress row while
  `state.busy`.
- New `GitLogSheet.kt` (`GitLogSheetMode.VIEW` / `.CHECKOUT`, one shared
  commit list — both are `git log`, only whether a row offers a checkout
  action differs): loading/error/empty states, a two-line row per commit
  (subject; short id · author · date), and — in checkout mode — a per-row
  Checkout button that opens a plain-words `AlertDialog` ("this opens
  commit `<id>`, you will not be on a branch until you switch back to one")
  before calling `viewModel.checkoutCommit`. Closes and clears its list on
  dismiss or after a successful checkout.
- New `GitRemotesSheet.kt`: the remote list (name, URL or "(no URL)"), a
  per-row remove (✕) button gated behind a confirm `AlertDialog` (removing
  a remote by the wrong name can silently break the next Push — the one
  Remotes action that confirms first), and an inline add-a-remote form
  (name + URL fields, live-validated against `GitManager.isSafeRemoteName`/
  `isSafeRemoteUrl` before the button enables).
- Revert All's own confirm `AlertDialog` lives in `GitControlView.kt`
  itself (matching the existing per-file discard dialog's shape):
  non-dismissable by back/outside-tap, spells out "this cannot be undone"
  and that untracked files are left alone.
- "Git Credentials" is a menu shortcut only — no second credentials editor
  was built. `GitControlSheet` gained an `onOpenSettings: () -> Unit = {}`
  parameter; both call sites (`EditorScreen`, `FileManagerScreen`) already
  had their own `onOpenSettings` (the drawer footer's existing Settings
  jump) and now pass it straight through. Tapping the item closes the git
  sheet (`onDismiss()`) then navigates to Settings, where the existing
  `GitCredentialsStore` editor already lives.
- New strings (all plain-English, matching 73.2's mandate): `git_init_action`,
  `git_init_busy`, `git_more_actions`, `git_fetch_action`,
  `git_log_history_action`, `git_checkout_commit_action` (+`_row_action`,
  `_explainer`, `_title`, `_confirm`), `git_log_empty`, `git_remotes_action`
  (+`_empty`, `_no_url`, `_remove`, `_add_title`, `_name_label`,
  `_url_label`, `_add_action`, `_remove_title`, `_remove_confirm`,
  `_remove_action`), `git_credentials_action`, `git_revert_all_action`
  (+`_title`, `_confirm`). `git_not_a_repo_message` reworded (terminal
  instruction dropped, Clone still named).

## Explicitly out of scope this part

- Spck's Provider item (switching git hosting provider) — CodeC only
  targets GitHub today; nothing else in the app suggests another provider
  is coming. Not built; flagged as a finding if raised later.
- The search-files box above Spck's tree — a file-tree feature, not a git
  one; unrelated to "git...GUI not a CLI".
- A dedicated Log History diff view (tapping a commit to see what changed)
  — Spck's own screenshots showed Log History as a list-with-checkout, not
  a diff browser; the existing per-file diff view (`openDiff`) still covers
  inspecting changes against `HEAD`.

## Tests

- `app/src/test/java/com/codeci/ide/GitManagerTest.kt` — extended the
  existing fake-`git`-shell-script harness with `init` (dispatches on `-b`
  so the git-2.28+ path and the plain-`init`+`symbolic-ref` fallback are
  both independently exercisable), `remote` (`get-url` / `add` / `remove` /
  bare list), and `log` cases (`FAKE_GITLOG_OUT`/`_EXIT` — deliberately not
  `FAKE_LOG_*`, since `FAKE_LOG` is the pre-existing calls-log **file
  path**, not git-log output). New tests: `init` happy path, fallback path,
  and refusal against an existing repo; `checkoutCommit` happy path and
  input-validation rejections (short id, flag-looking id, wrong-length hex,
  empty); `revertAllChanges`'s exact argv; `addRemote`/`removeRemote` happy
  paths, `removeRemote`'s refusal of an unconfigured name and of an invalid
  name (both before touching git); `remotesDetailed` with two remotes and
  with a failing URL lookup; `log`'s parsing of two commits (with the exact
  argv checked) and its empty-list-not-throw behaviour on failure.
- `app/src/test/java/com/codeci/ide/GitLogParserTest.kt` (new) — pure
  parser tests: newest-first ordering untouched, a subject containing a
  comma/pipe is never split, blank/malformed lines are dropped rather than
  thrown, and a regression guard for a subject that itself contains stray
  separator-adjacent bytes.
- `app/src/test/java/com/codeci/ide/GitGuiParityWiringTest.kt` (new) —
  source-scan wiring pins (no Robolectric Compose render exists for this
  sheet, same precedent as `GitInstallWiringTest`/`GitDiscardWiringTest`):
  the overflow menu's visibility gate and its five/six actions; Revert
  All's enabled condition and its non-dismissable confirm dialog; Checkout
  Commit's confirm-then-clear flow; the detached-HEAD chip replacing the
  old silent-`?.let`; the not-a-repo screen's real init button (and that it
  is scoped inside the right `when` branch); the Remotes add/remove wiring
  and its confirm; every new ViewModel action routing through
  `runGitOperation`; the plain-words detached-HEAD result message;
  `revertAllChanges` never touching `git clean`; the `checkoutCommit`/
  `removeRemote` input-validation guards; and that every new string exists.
- `app/src/test/java/com/codeci/ide/GitInstallWiringTest.kt` — updated the
  one test this phase's owner decision superseded (`not-a-repo guidance
  stays text-only, no new action button` → `... grew a real init button in
  Phase 73.3`) and the string test that used to check for the now-removed
  "git init"/Terminal wording.
- No Robolectric Compose render was added or attempted for the two new
  sheets (`GitLogSheet`, `GitRemotesSheet`) — same constraint as the rest
  of this sheet's UI, no existing render harness to extend safely within
  this session.

No coroutines/JUnit jars or a JVM (`java`) are available in this sandbox
this session (confirmed via `find /`, and via apt/curl network checks that
came back blocked for non-allowlisted hosts) — full local JVM test
execution and even a syntax-only `kotlinc-jvm` pass (which the 73.1/73.2
docs record having done, using a JRE that no longer exists in this fresh
sandbox) were not possible. All new/changed Kotlin was instead verified by:
line-by-line manual review, brace/paren-count sanity checks on the
production files (balanced), and — for every source-scan test's literal
anchor string — a direct `grep -cF` confirmation against the real file
content before trusting the assertion. CI (`Build APK`) remains the actual
test executor of record for this change, as for every prior phase.

## CI round trip (no local JVM this session — see Tests)

Without a local `kotlinc`/JVM to pre-validate against (see Tests), the first
push surfaced two real mistakes CI caught that a syntax-only read missed:

1. **Run `36538343931` — compile failure.** `AlertDialog`'s
   `dismissOnBackPress`/`dismissOnClickOutside` were passed as direct named
   arguments in the three new confirm dialogs (Revert All, Checkout Commit,
   Remove Remote) — this Compose Material3 version only accepts them via a
   `properties = DialogProperties(...)` argument (the exact shape the
   pre-existing Publish-to-GitHub and discard-progress dialogs in the same
   file already use, which a closer read before writing would have caught).
   Fixed in `5b38694`.
2. **Run `36538858396` — compiled, one test failure.**
   `GitInstallWiringTest`'s rewritten "new user-facing strings" test used
   `strings.substringAfter("name=\"git_not_a_repo_message\"")` with no
   matching `substringBefore`, so it captured everything to the end of
   `strings.xml` — including two unrelated, untouched strings
   (`notice_userland_not_ready`, `install_failed_retry`) that legitimately
   say "Terminal", tripping the new `assertFalse(...contains("Terminal"))`
   check. Scoped with `.substringBefore("</string>")`; the identical latent
   bug shape in `GitGuiParityWiringTest`'s `git_revert_all_confirm` check
   (not yet failing, but wrong for the same reason) was fixed the same way
   pre-emptively. Fixed in `84b8d79`.
3. **Run `36544645579` — CI ✅ GREEN.** Both APKs assembled
   (`CodeC-IDE-1.3.17-universal.apk` / `-debug.apk`), all host unit tests
   passed.

## Evidence

- **CI: ✅ GREEN.** `Build APK` run `36544645579` on tip `84b8d79` — the
  first real Gradle compile/run of the changed files, after two fixup
  commits (`5b38694`, `84b8d79`) resolving the failures above.
- **Device:** not run this session; none claimed.
