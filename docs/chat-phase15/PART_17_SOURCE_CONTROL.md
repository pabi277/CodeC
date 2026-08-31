# CodeC Phase 17 — In-editor Source Control & Branching (Spck git parity)

**Status:** Planned (design/spec only) · **Cost:** `[client-only]`
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

---

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
