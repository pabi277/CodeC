# Phase 73.6 — Editor drawer git goes full GUI (Initialize + install prompt)

**Status: 🚧 IMPLEMENTED (2026-09-29), CI ✅ GREEN `36566495685` on
`65d34a2`; session branch `arena/01a0eca9-codec`. Owner device pass owed,
not merged (rule.md §3).
Not one of the numbered UI-polish discussion drafts (65.1/74.1 remain
untouched) — this is a rule.md §4 bug/improvement lifecycle item from the
owner's own device report on the 73.5 build, superseding one 73.2 decision
(see below).**

## The owner's report (verbatim, 2026-09-29, on run #1261 — the 73.5 build)

> "Tell me 1st what is happening I said if git is not installed it will
> install automatically in the background with status bar but it's not
> happened Then i installed manually but then i said it will be full gui
> bassed but when i click on initialize it opens terminal … So where is
> gui?"

Follow-ups: installed from the 73.5 CI run; never saw a REPOSITORY/Source
Control header; Terminal switched automatically (the app did it, not the
user). Wanted: *"like editor if git is not installed it shows a option
that will say git is not installed do you want to install it than start
install"*.

## Root cause (source-read, then confirmed against the transcript)

There are **two** "initialize" doors, and 73.1–73.5 only converted one:

- The Git panel's button (`GitControlSheet` → `GitManager.init`) runs
  `git init -b main` as a hidden background process — no Terminal, no
  navigation code anywhere near it (verified: zero navigate-to-Terminal
  references in the sheet).
- The **editor drawer's** Repository panel (`EditorSidePanel.RepositorySlot`
  → `EditorScreen.onInitializeRepository`) still ran the old path: close
  the drawer, `onOpenInTerminal("git init")` — Terminal opens and the
  command is typed into the visible shell.

The owner's transcript proves door #2 beyond doubt: the shell ran in
`…/files/CodeC/projects/` (Terminal's default cwd — `TerminalScreen`
runs `initialCommand` with no `cd`), so the repo landed in `projects/.git`
— the projects folder, not the project — with `master` + the default-branch
hints (the engine's `-b main` shows neither). "No git command" first
because git genuinely wasn't installed; the drawer never checked. And no
REPOSITORY/Source Control header was ever seen because that header lives in
the sheet — a screen the drawer path never opened (its empty state doesn't
even offer it).

## What shipped

- **Drawer Initialize is the engine now** (`EditorScreen.runDrawerInit`):
  resolves the project root (scratch mode toasts, same as
  `openSourceControl`), acquires the manager off the main thread, and —
  no manager → asks to install (pending root kept); already a repo →
  refreshes meta and opens the Source Control sheet; else `init` → on
  success refreshes meta and opens the sheet (the GUI answers "where is
  gui" by appearing), on failure toasts the classified friendly message.
  Terminal is never opened and nothing is typed anywhere.
- **The shared install prompt** (new `GitInstallPromptDialog.kt`): "Git
  is not installed — … Do you want to install it now?" with
  INSTALL/Cancel — the owner's words. Shown from the drawer flow (Initialize
  with no git; install option + retry in the panel) AND from the sheet's
  INSTALL GIT button (consistency — supersedes 73.2's "no confirmation
  dialog" decision, which the owner's ask-first instruction overrules).
- **Drawer install states** (`RepositoryPanelState` + `RepositorySlot`):
  not-installed sentence + INSTALL GIT option; the same background
  install + status bar as the sheet (elapsed seconds, stay-here note);
  failed + RETRY; init-busy row; gate-refused plain message with no
  button (the 73.2 decision, kept). The install reuses the exact
  Packages-tab mechanism (`sendCommand` + `PkgResult`/`InstallOutcomes`
  polling, `SetupGatePolicy` gate) — third door, same engine.
- **Auto-continue**: an install that started from an Initialize tap runs
  the pending init on success, which opens the sheet — one confirmation
  carries the user from "no git" to the working GUI panel.

Not touched: the Run-in-Terminal hand-offs (running code is a legitimate
terminal use) and the Modules/Packages install rows (their own confirmed
flow).

## The stray `projects/.git`

The owner's manual run created `…/files/CodeC/projects/.git`. The GUI is
immune to it (`isRepository` checks `root/.git` only, and status only runs
inside real repos), but it should still go — cleanup is one Terminal
command from the default prompt (cwd is already `projects/`):
`rm -rf .git`. Nothing in the app auto-deletes it (no silent deletes).

## Tests

- `GitDrawerInstallWiringTest.kt` (new, 6 cases): the terminal-typing path
  is gone (`onOpenInTerminal("git init")` absent file-wide; the tap block
  calls `runDrawerInit` with no terminal/drawer-close calls); init
  branches (prompt + pending root / already-repo / init / sheet /
  classified failure); install-mechanism reuse + consume-once
  auto-continue; the prompt dialog's shape + both hosts; the drawer
  panel's install/progress/retry/busy/gate states; the 5 new strings.
- `GitInstallWiringTest.kt` updated: the sheet button now opens the
  shared prompt first (73.2's no-dialog pin superseded, with the note).
- No JVM/kotlinc in this sandbox (same as 73.3/73.5) — verified instead
  with the Kotlin state-machine brace/paren scan (all six touched files
  balanced, no transient negatives) and a Python mirror of every new or
  changed assertion (all passed). That scan caught real edit-tool damage
  before commit again this phase: tail garbage in `EditorSidePanel.kt`
  and `GitControlView.kt` (truncated), plus four silently un-applied
  edits (re-applied and individually re-verified).

## Evidence

- **CI:** ✅ GREEN `36566495685` on `65d34a2`. One fixup round: run
  `36566151760` was red for cause on four missing `EditorScreen.kt`
  imports (`SetupGatePolicy`/`SetupAction`/`ShellEnvironment`/`delay` —
  the other two errors were cascades), fixed in `65d34a2` with no
  behaviour change.
- **Device:** not run this session; none claimed.
