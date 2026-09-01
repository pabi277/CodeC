# CodeC Phase 17 — In-editor Source Control & Branching (Spck git parity)

**Status:** IMPLEMENTED (2026-08-31, `arena/01a05878-codec`) — Switch Branch
(checkout/stash/auto-restore + New branch), merge-conflict grouping and Mark
Resolved all shipped; **device gate (§4 steps 5–8) pending owner run** ·
**Cost:** `[client-only]`
· **Depends on:** Phase 13 (Git engine: clone/status/diff/commit/push/pull,
credential store, redaction), Phase 15 (Projects Hub git actions), Phase 16
(editor drawer + in-tree git status letters)
· **Target files (anticipated):** `ui/screens/GitControlView.kt`,
`ui/viewmodels/GitControlViewModel.kt`, `ui/projects/GitManager.kt`,
`ui/projects/GitDiff.kt`, `ui/screens/FileManagerScreen.kt`/drawer,
`ui/screens/EditorScreen.kt`

---

## 1. Context & motivation

Phase 13 shipped the git engine and a Source Control bottom sheet
(`GitControlView`) with whole-tree staging, one-tap **Commit & Push**, inline
diff, and Pull. That covers the mechanics. What's missing for **Spck parity** is
the *experience* Spck users expect around git:

- **In-tree status colors/letters** on every changed file (Spck marks files
  yellow/blue/purple), not just a list in a sheet.
- **Switch Branch** from both the Projects list and the editor's Files menu
  (Spck's "Switch Branch" prompt), with local stashing of uncommitted changes
  when switching.
- **Pull Changes** and **Push Changes** as first-class per-project menu items
  (Spck places them in the Projects/Files menus).
- **Tap-a-changed-file → diff viewer** directly from the tree (Spck opens the
  diff when you tap a colored file).
- **Merge-conflict marking** (Spck marks conflicts purple; resolve = "Mark
  Resolved").

This phase completes the git UX so CodeC's version control *feels* like Spck's,
reusing the Phase 13 engine end to end. See
[Spck Git Features](https://spck-code-editor.readthedocs.io/en/latest/git-features/)
and [`mockups/source-control.png`](mockups/source-control.png).

---

## 2. UX / UI design (phone-first)

### 2.1 Source Control bottom sheet (refined)

```
┌──────────────────────────────────────────┐
│  Source Control          ⌥ main ▾   ↻     │
│  ┌────────────────────────────────────┐   │
│  │ Message (Ctrl+Enter to commit)     │   │  ← multiline commit message
│  └────────────────────────────────────┘   │
│  [        COMMIT & PUSH        ]           │  ← filled purple
│                                            │
│  Changes  3                                │
│   M  app.py            /demo_flask     ⟩   │  ← tap → diff
│   M  index.html        /templates      ⟩   │
│   A  utils.py          /demo_flask     ⟩   │
│                                            │
│  [ PULL ]   [ REFRESH ]                    │
└──────────────────────────────────────────┘
```
- **Branch selector** `⌥ main ▾` opens the Switch Branch flow (§2.3).
- **Changes list** — each row: status letter (colored), filename, folder path,
  chevron. Tap → **diff viewer** (Phase 13 `GitDiff`/`DiffEngine`, unified
  −/+/context, theme colors). Reuses the Phase 13 sheet; adds the branch chip and
  the per-row path + tap-to-diff affordance.
- **Commit & Push** — Phase 13 D4 honest split result ("Committed ✓ — push
  failed: …" when push fails).
- **Pull / Refresh** — Phase 13 pull; refresh re-runs `status`.

Entry points: Projects Hub card `⋮ → Source Control` (Phase 15), editor drawer
footer **Source Control** (badge = change count, Phase 16), editor `⋮`.

### 2.2 In-tree & in-list status colors (Spck signature)

- File tree nodes (editor drawer, Phase 16) and Projects cards (Phase 15) show
  **status letters**: `M` (modified, yellow), `A` (added/new, green), `D`
  (deleted, red), `?` (untracked, grey), `U`/conflict (purple). Colors adapt to
  light/dark theme (Spck uses yellow in dark, blue in light).
- Tapping a changed file **in the tree** opens its diff directly (Spck behavior),
  in addition to opening it for editing — offer both via a small diff icon on the
  row or a tap=open / diff-icon=diff split.
- Data source: one `git status --porcelain` per project, parsed by the Phase 13
  `GitStatusParser`, cached in the `GitControlViewModel` and shared with the tree
  and the card. Refresh on save, pull, commit, branch switch, and screen resume.

### 2.3 Switch Branch (Spck "Switch Branch")

- A dialog listing branches from `git branch` (local) + `git branch -r` (remote,
  offered as "checkout as new local"), current branch preselected.
- On confirm: if the working tree is dirty, **stash** (`git stash`) before
  checkout and **auto-restore** (`git stash pop`) when switching back — matching
  Spck's promise ("uncommitted changes are stored locally and brought back").
  Surface stash state honestly if pop conflicts.
- **Create branch (stretch):** Spck can't create branches; CodeC *can* via
  `git checkout -b <name>` — offer a "New branch…" row as a bonus, clearly
  optional.
- Entry points: Projects card `⋮`, editor drawer footer, Source Control branch
  chip.

### 2.4 Pull / Push as menu items

- **Pull Changes** and **Push Changes** appear in the Projects card `⋮` and the
  editor Files/drawer menu (Spck placement), in addition to the sheet buttons.
- Push requires a token (Phase 13 GitHub Account); missing/invalid token →
  Spck-style honest error (401/403 guidance) with a link to Settings.

### 2.5 Merge conflicts

- After a pull that produces conflicts, conflicted files are marked **purple**
  (`U`) in the tree and listed in Source Control under a "Conflicts" group.
- **Mark Resolved** action per file (Spck's manual resolution): stages the file
  (`git add <file>`) and clears the purple mark. Commits are blocked while any
  conflict remains (Spck rule), with a clear message.
- In-editor conflict markers (`<<<<<<< ======= >>>>>>>`) are highlighted by the
  syntax layer so the user can edit them before Mark Resolved. (Auto-resolution
  UI is out of scope — Spck itself only supports manual resolution.)

---

## 3. Architecture & implementation steps

Reuse the Phase 13 engine; add branch ops + status plumbing to the UI.

1. **Status plumbing.** Promote the per-project `GitStatus` (Phase 13) to a
   shared, cached source the tree (Phase 16), the Projects card (Phase 15), and
   the sheet all read. Invalidate on save/commit/pull/switch/resume. Off-thread.
2. **Branch ops in `GitManager`** (argv-list, Android-free, host-testable):
   `currentBranch()`, `listBranches()` (local + remote), `checkout(branch)`,
   `checkoutNew(name)`, `stash()`/`stashPop()`. All via `ProcessBuilder` argv —
   no shell, no injection (Phase 13 D1). Timeouts as Phase 13.
3. **Switch Branch dialog** + dirty-tree stash/auto-restore logic in
   `GitControlViewModel`.
4. **Source Control sheet refinements** — branch chip, per-row path +
   tap-to-diff, Conflicts group, Mark Resolved.
5. **In-tree/in-list status letters** — render the cached status as colored
   letters; wire tap-to-diff from the tree.
6. **Pull/Push menu items** in the Projects card `⋮` (Phase 15) and editor menu
   (Phase 16).
7. **Host unit tests** (CI-run):
   - `GitBranchParserTest` — parse `git branch`/`git branch -r` output → list +
     current.
   - `SwitchBranchLogicTest` — dirty→stash→checkout→(switch back)→pop ordering
     (against a fake `git` script, like Phase 13 `GitManagerTest`).
   - `ConflictParseTest` — porcelain `UU`/`AA`/`DD` → conflict set; commit
     blocked while non-empty.
   - Extend `GitStatusParserTest` for the letter→color/group mapping.

---

## 4. Exit condition & device verification recipe

Uses a scratch repo the owner can push to (Phase 13 used `pabi277/T`).

```text
# Status in tree & list
1. Clone a repo (Phase 15) → open it → editor drawer tree shows files with no
   status letters (clean).
2. Edit README.md → save → the tree shows "M" (yellow) on README.md; the
   Projects card shows the yellow "M" badge; drawer Source Control badge = 1.

# Diff from tree
3. Tap the diff icon on README.md in the tree → unified diff opens showing the
   −/+ lines. Close.

# Commit & Push
4. Drawer → Source Control → branch chip shows "main" → type "docs: mobile edit"
   → COMMIT & PUSH → "Committed & pushed ✓" (verify on github.com); tree letters
   clear.

# Switch Branch
5. Source Control branch chip (or ⋮ → Switch Branch) → dialog lists branches,
   "main" selected → pick another branch → checkout succeeds; if the tree was
   dirty it stashed and restores on switching back.
6. (Bonus) "New branch…" → create feature/x → now on the new branch.

# Pull / Push menu placement
7. Projects card ⋮ shows Pull, Switch Branch, Source Control; editor menu shows
   Pull/Push. PULL returns "Pull completed".

# Conflicts (optional if a conflict can be staged)
8. Force a conflict (edit same line remotely + locally) → PULL → the file is
   purple/U and grouped under Conflicts; COMMIT is blocked; edit to resolve →
   Mark Resolved → commit allowed.

# Security regression (Phase 13)
9. env | grep -i token in the CodeC terminal → empty; .git/config has no token;
   Settings → Logs contain no token after a wrong-token push.

PASS = steps 1–7 succeed without manual fixes; step 9 stays clean. Step 8 passes
if a conflict is reproducible on the day.
```

### 4.1 Quick device checks for the Switch Branch + conflict work (2026-08-31)

Entry points to exercise (all three must open the SAME dialog):
**Projects tab → card ⋮ → Switch Branch**, **editor → ☰ → footer Switch
Branch**, **editor → ☰ → Source Control → the `⌥ branch ▾` chip**.

| # | Do this | Expect |
|---|---|---|
| 1 | Open a git project → ☰ → Switch Branch | Dialog titled *Switch Branch* with the project name, a **Branches** list (current one tagged `current`), a **Remote** group, a **New branch…** row, and the stash checkbox |
| 2 | (Clean tree) pick another branch → SWITCH | Spinner → **"Switched to \<branch\>"** → Close; the drawer's `⌥` chip shows the new branch |
| 3 | Edit a file, wait for autosave (dirty dot clears) → Switch Branch | Checkbox reads **"N uncommitted change(s) will be saved and restored"** |
| 4 | Keep the checkbox on → switch away → switch back to the first branch | Away: *"your changes are stashed…"*; back: **"— your stashed changes were restored"** and the edit is back in the file |
| 5 | Switch Branch → **Remote** → tap `origin/<x>` | **"Switched to \<x\>"** (a local tracking branch is created — *not* a detached HEAD) |
| 6 | Switch Branch → **New branch…** → `feature/test` → SWITCH | **"Switched to feature/test"**; drawer chip follows |
| 7 | Projects tab → card ⋮ → **Push Changes** | **"Pushed \<project\>"**, or an honest failure (no upstream / offline / bad token) |
| 8 | Force a conflict (see below) → open the project | Drawer tree shows a **purple `U`** on the file; the SC sheet shows a **Conflicts N** group *above* Changes |
| 9 | In the SC sheet, try COMMIT & PUSH with a conflict open | Button is disabled and **"Resolve the conflicts below before committing."** is shown |
| 10 | Edit the file to remove `<<<<<<< ======= >>>>>>>` → tap the **✓** on its row | **"Marked resolved: \<file\>"**, purple `U` clears, COMMIT & PUSH becomes enabled |

Forcing a conflict on-device (CodeC terminal, one command per line, from the
project folder — it merges a divergent branch into itself):
```text
git checkout -b conflict-test
(echo edit A > conflict.txt) && git add -A && git commit -m a
git checkout main
(echo edit B > conflict.txt) && git add -A && git commit -m b
git merge conflict-test
```
`git merge` reports the conflict; the tree and the SC sheet should then behave
as rows 8–10. Afterwards: `git merge --abort` (or resolve + commit) and delete
the scratch branch with `git branch -D conflict-test`.

Security regression (Phase 13, unchanged engine): `env | grep -i token` in the
CodeC terminal prints nothing, `.git/config` holds no token, and Settings →
Logs shows no token after a failed push.
```

## 5. Invariants & scope guard

- **Client-only.** Reuse Phase 13 `GitManager`/`GitContext`/`GitDiff`/
  `GitCredentialsStore`; **no new credential path** — token stays in the askpass
  + per-child env, never argv/`.git/config`/terminal (Phase 13 D2), always
  redacted in logs.
- No `.` on PATH; git = `$PREFIX/bin/git` only (Phase 13 D7). No `$PREFIX/bin`
  writes, no bootstrap/`[repo-build]`.
- No git call on the UI thread; status/branch cached + async.
- Whole-tree staging remains the default (Phase 13 D3); per-hunk staging is
  **out of scope** (note as a future candidate). Auto merge-conflict resolution
  is out of scope (Spck is manual too). Branch *creation* is a bonus, not a gate.

---

## 6. Implementation record (partial — 2026-08-31)

Status: **PARTIAL.** Shipped as part of the Phase 15/16 device rounds on
`arena/01a057e0-codec` (merged to `main` 2026-08-31):
- *Device round 2:* the SC sheet is the mockup-exact re-skin (title +
  outlined `⌥ branch ▾` chip, multiline "Message (Ctrl+Enter to commit)"
  box, full-width filled COMMIT & PUSH, "Changes N" with typed file icons +
  folder path + porcelain letter) and gained the **per-file +/− stage
  toggle**: `GitManager.stageFile`/`unstageFile` (`git add -- <path>` /
  `git reset -- <path>`, argv-safe; +2 fake-git argv-proof host tests),
  `GitControlViewModel.toggleStage` deciding from the porcelain `x` column.
  In-tree M/A/D/? letters + tap-to-diff already shipped with the re-skin.
- *Device round 3:* **build outputs stop traveling at push.**
  `BuildArtifactIgnore.ensure(root)` now runs at git refresh and before
  COMMIT & PUSH's `git add -A` (plus project open and every RUN), and
  `BuildArtifactIgnore.untrackTracked(root, git)` runs `git ls-files` →
  `git rm -f --cached` on any tracked path matching the artifact patterns
  (`*.out/*.o/*.obj/*.exe/*.class`, `bin/`, `dist/`, `build/`, `target/`,
  `node_modules/`, `.venv/`, `venv/`) — files stay on disk, the next commit
  records the removal. Policy lives in `.git/info/exclude` (machine-private;
  the user's `.gitignore` is never touched — same rule as
  `PythonCacheIgnore`); patterns already covered by either file are skipped.

Still **PENDING** (per §4 recipe steps 5–8): the Switch Branch
checkout/stash dialog (drawer footer + SC chip currently toast "coming
soon" — `hub_switch_branch_soon` / `editor_drawer_branch_soon` strings),
"New branch…" bonus, and merge-conflict marking (purple/U grouping,
COMMIT blocked, Mark Resolved).

### 6.1 Switch Branch + merge conflicts — IMPLEMENTED (2026-08-31, `arena/01a05878-codec`)

**Status: IMPLEMENTED, `[client-only]`, CI-GREEN (2026-08-31, `Build APK`
`33417811422` @ `3a2846f`, assemble + unit tests + lint).**
Everything the §4 recipe needs for steps 5–8 is in place; the device gate is
the owner's run of that recipe.

**CI rounds (all three red rounds were real, for-cause, and none of them
touched product behaviour):**
1. `33416562391` — `Unresolved reference 'CloudUpload'`: the resolved
   `material-icons-extended` no longer ships that icon.
2. `33416826771` — `Unresolved reference 'Send'`: `Icons.Default.<name>` only
   resolves when the matching `androidx.compose.material.icons.filled.<name>`
   extension is imported (that's why `ModulesScreen` imports `filled.Send`);
   `Icons.Default.UploadFile` is imported in `FileManagerScreen` already.
3. `33417133821` — 6 test failures, all from my harness: the fake-git script
   was written with `printf '\\n'` (two backslashes), so `sh` printed a
   literal `\n`; every command landed on one log line and canned output
   carried a trailing `\n` (so the stash marker never matched). Aligned with
   the Phase 13 harness's single backslash. **The product code was never at
   fault** — evidence: `expected:<CMD [checkout] [main][]> but
   was:<…[main][\n]>`.
4. `33417811422` — green.

**Research notes (recorded before writing code, owner's
RESEARCH-WHEN-NEEDED rule):**

1. **Conflict codes.** git-status(1) "Short Format"
   ([manpage](https://manpages.debian.org/testing/git-man/git-status.1.en.html),
   [discussion](https://stackoverflow.com/questions/44573213/parsing-git-status))
   lists exactly seven unmerged XY pairs: `DD AU UD UA DU AA UU`. Two carry
   no `U` at all, so "either column is U" would miss them, while "both
   columns ∈ {A,D,U}" false-positives on `AD` (staged addition deleted in the
   work tree). The exact set is hard-coded in `GitBranchOps.isConflict`.
2. **Branch listing.** `git branch -a` prints a remote symref line
   (`remotes/origin/HEAD -> origin/main`) that is not a branch, and a detached
   HEAD prints `* (HEAD detached at <sha>)` / `* (no branch)` in the current
   slot. Both are handled by `GitBranchParser`.
3. **Remote checkouts.** `git checkout origin/x` detaches HEAD
   ([ref](https://stackoverflow.com/questions/74626663/how-can-i-fix-head-detached-at-origin-development-when-i-have-2-remotes));
   the correct form is `git checkout -b <local> --track <remote>/<local>`.
4. **Stash.** `git stash push -u -m <msg>` covers untracked files;
   `git stash list` prints `stash@{N}: WIP on <branch>: <sha> <subject>` or
   `stash@{N}: On <branch>: <message>` when `-m` was used
   ([ref](https://linuxcapable.com/git-stash-command/)); and **`git stash pop`
   only drops the entry when the apply succeeds**, so a conflicting pop loses
   nothing — the UI can honestly say "your changes are still saved".

**What shipped:**

| Piece | Where |
|---|---|
| Pure branch/stash/conflict logic (no Android, no process) | `ui/projects/GitBranchOps.kt` (new) |
| `listBranches` / `currentBranch` / `checkout` / `checkoutNew` / `checkoutRemote` / `stashPush` / `stashPop` / `stashList` / `switchBranch` | `ui/projects/GitManager.kt` |
| `isConflict` (AA/DD included), purple `U` badge | `GitFileChange` in `GitManager.kt` |
| Branch loading, switch flow, Mark Resolved, commit guard | `ui/viewmodels/GitControlViewModel.kt` |
| Switch Branch dialog (local + remote + New branch) | `ui/screens/BranchSwitchSheet.kt` (new) |
| Conflicts group, Mark Resolved ✓, blocked COMMIT, clickable branch chip | `ui/screens/GitControlView.kt` |
| Entry points: SC chip, editor drawer footer, Projects card ⋮ | `EditorScreen.kt`, `FileManagerScreen.kt` |
| Push Changes menu item | `FileManagerViewModel.pushProject`, hub card ⋮ |
| Purple `U` in the drawer tree | `ui/components/EditorProjectDrawer.kt` |

**Tests (host, run by CI):** `GitBranchOpsTest` ×17 (branch/stash parsing,
detached HEAD, conflict set, name safety, marker round-trip) and
`GitBranchManagerTest` ×16 (fake-git argv proofs: `branch --all --no-color`,
`checkout -b … --track …`, `stash push -u -m codec-switch: main`, the
dirty→stash→checkout ordering, pop-back on a failed checkout, auto-restore
only for CodeC-marked stashes belonging to the target branch, no-op on a
clean tree, `stashChanges = false`, and rejection of an option-shaped name
before git ever runs).

**Decisions:**

- **D1 — branch list is `git branch --all --no-color`.** One call covers
  local + remote; `--no-color` blocks a user `color.branch=always` config
  from injecting escapes. Symref (`origin/HEAD -> …`) and detached-HEAD rows
  are dropped; detached-ness is reported to the UI instead.
- **D2 — remote branches become local tracking branches.** Never
  `checkout origin/x` (detaches HEAD); always
  `git checkout -b <name> --track <remote>` — and if a local branch of that
  name already exists, the plain local checkout is used so the command cannot
  fail with "a branch named 'x' already exists".
- **D3 — stash message marker `codec-switch: <branch>`.** Auto-restore only
  ever pops entries carrying *our* marker *and* naming the branch we just
  landed on, so a user's own stashes are never touched (proved by test).
- **D4 — failure handling is honest and loss-free.** A failed checkout after
  a stash pops the stash straight back; a conflicting pop leaves the entry on
  the stack and the dialog says the changes are still saved.
- **D5 — `-u` (include untracked) by default**, because Spck's promise is
  "your uncommitted changes" — new files included. The dialog's checkbox
  defaults to on and can be turned off.
- **D6 — Mark Resolved = `git add -- <path>`** (the Phase 16 argv-safe
  `stageFile`), exactly what git needs to end the unmerged state. Commits
  stay blocked until no `U` remains (both in the UI and in the ViewModel).
- **D7 — the editor menu does NOT gain Pull/Push rows.** The drawer footer is
  mockup-exact (owner's Phase 16 device round 2 requirement) and the editor
  ⋮ is already long; Pull/Push are reachable from the drawer's Source Control
  sheet and from the Projects card ⋮, which gained **Push Changes**. Recorded
  here as a deliberate deviation from §2.4 — say the word and it moves.
- **D8 — no new credential path, no new process rules.** Everything reuses
  the Phase 13 `GitManager` argv/environment/redaction model; branch names
  and stash refs are validated before exec (`isSafeExistingBranch`,
  `ProjectsHub.isValidBranchName`), and nothing is written to `$PREFIX/bin`,
  `.git/config` or the terminal environment.

### 6.2 Device round 1 (2026-08-31) — two real push bugs, both fixed

The owner's first run of the Switch Branch work surfaced two genuine defects
(transcript-quoted, so both are evidence, not guesses):

**Bug 1 — "The current branch test has no upstream branch."**
```
Committed - push failed: git push failed: fatal: The current branch test has
no upstream branch. To push the current branch and set the remote as upstream,
use git push --set-upstream origin test
```
A branch created inside the app (the new **New branch…** row, `checkout -b` in
the terminal, or the first push of a local repo) has no tracking branch, so
the Phase 13 `git push` could never work. **Fix:** `GitManager.push(root,
setUpstream)` now runs `git push --set-upstream <remote> HEAD` (remote from
`git remote`, falling back to `origin` — `HEAD` makes git use the current
branch's own name), and `pushHandlingUpstream()` picks the right form by
reading the status branch line: `## test` (no `...origin/test`) → publish,
`## main...origin/main` → plain push. Wired into commit-and-push, the new
in-sheet PUSH retry and the Projects card **Push Changes**.

**Bug 2 — "If something upload failed it doesn't return the changes in app —
it stay updated but never go to github."**
A successful commit clears the change list, so a *failed* push looked exactly
like a successful one: clean tree, no `M` badge, no hint that the commit was
still only on the phone. **Fix — make the state honest:**
- the sheet reports **"Committed locally ✓ — NOT pushed: \<reason\>"**;
- the failure text is **sticky** (`pushError`) until a push succeeds;
- an amber **"N commit(s) not pushed yet"** row with a **PUSH** retry button
  appears whenever the branch is ahead, a push failed, **or the branch is not
  published at all** — a new branch has no `ahead` figure at all, so that case
  needed its own wording ("Branch \"x\" is not on the remote yet — the first
  push publishes it");
- the Projects card shows an amber **↑N** badge for commits that never left
  the device (`ProjectHubEntry.unpushed`, from `git status -b` ahead);
- git operations now re-read `status` **after** a failure too, so the ahead
  count on screen is real instead of stale.

**CI:** green at `33421815293` @ `1c01f84` (assemble + unit tests + lint),
with +5 fake-git argv proofs: plain `push`, `push --set-upstream origin HEAD`,
`origin` fallback when `git remote` fails, upstream detection both ways, and
`firstRemote`. The fake-git harness was also rewritten so it contains **no**
`printf '…\n'` at all — the double-escaped newline that broke CI round 3 of
§6.1 cannot recur.

**Still open:** the Devices-card ↑N badge only counts commits git knows are
ahead (`git status -b`); a branch that was never published shows no hub badge
(the in-sheet row covers it) — say the word to extend it.

---

### 6.3 Follow-up (2026-09-01) — new branches never reach GitHub; local commits looked pushed

**Owner report:** *"Known bugs — create a new branch don't add in github, locally
commit cannot be pushed."*

**Symptom.** Two faces of the same gap:
1. **Create a new branch** (Switch Branch → New branch…) created the branch
   *locally only* (`git checkout -b <name>`); nothing published it, so it never
   appeared on GitHub.
2. **A commit on a never-published branch looked uploaded** — `git status -b`
   reports no `ahead` count for a branch with no upstream, so the Projects card
   showed no amber badge and the work silently stayed on the device.

**Root cause.**
- `GitManager.switchBranch` → `checkoutTarget` → `BranchTargetKind.NEW` only ran
  `checkoutNew`; the publish step was missing entirely.
- `push(setUpstream=true)` used the refspec `HEAD` instead of the branch's own
  name (fragile when HEAD resolution is ambiguous).
- `ProjectHubEntry.unpushed = status.ahead` is `0` for an unpublished branch
  (no upstream to compare against), so the hub badge could not express
  "branch not on GitHub"; `GitStatus` had no notion of a committed-but-
  unpublished branch.

**Fix (`[client-only]`, host-testable):**
- `GitManager.push(..., branchName)` now pushes the explicit branch name
  (`git push --set-upstream <remote> <branch>`, git's own guidance); `HEAD` is
  only the fallback. `pushHandlingUpstream` passes `status.branch`.
- `GitManager.switchBranch` **publishes a NEW branch on creation** (best-effort).
  The result carries `published`/`publishError`, and the Switch Branch dialog
  says "· published to GitHub" or "· not on GitHub yet: <reason>".
- `GitStatus` gained `noCommits` (from `## No commits yet on <branch>`) and a
  computed `unpublished` (branch exists, has commits, tracks nothing). The
  Source Control sheet and the Projects card both use it.
- The Projects card now shows a bare amber **↑** pill for a branch that is not
  on the remote yet (the `↑N` pill keeps its ahead-count meaning).

**Tests:** `GitBranchManagerTest` +4 (publish-on-create argv, honest publish
failure, no publish for LOCAL/REMOTE, HEAD fallback) and `pushHandlingUpstream`
asserts the branch name; `GitStatusParserTest` +3 (`noCommits`, `unpublished`,
tracking/detached negatives).

**CI:** pending the `Build APK` run (assemble + unit tests + lint) — run id
recorded in the report. **Device pass required** for the owner's own
new-branch → GitHub round trip (no device in the agent sandbox).
