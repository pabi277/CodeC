# CodeC Phase 15 — Projects Hub & Unified Import (Spck-style project list)

**Status:** IMPLEMENTED 2026-08-31 (see §6; CI + device pending) · **Cost:** `[client-only]`
· **Depends on:** Phase 8 (Projects & folder tree), Phase 13 (Git clone),
Phase 14 (project types / auto-detect)
· **Target files (anticipated):** `ui/screens/FileManagerScreen.kt` (or a new
`ProjectsHubScreen.kt`), `ui/viewmodels/FileManagerViewModel.kt`,
`ui/projects/ProjectInfo.kt`/`ProjectManager.kt`, `ui/navigation/Screen.kt`,
`MainActivity.kt`

---

## 1. Context & motivation

Today CodeC opens projects from the **Files tab** (`FileManagerScreen`), which
mixes the project list, the file tree, and a `+` FAB that creates a project or a
file depending on context. Cloning from GitHub lives behind `⋮ → Clone from
GitHub` (Phase 13), ZIP import behind another menu item (Phase 8). It works, but
it is not the **Spck experience** the owner wants: in Spck the **Projects tab**
is a first-class, scannable list of every project, and a single **add (+)**
control fans out to *New Project*, *Mount/Clone Git Repo*, *Import ZIP*, and
*Add Git Token*. See Spck's docs:
[Create a New Project / Import a Github Repo](https://spck-code-editor.readthedocs.io/en/latest/getting-started/).

This phase turns CodeC's project entry point into that Spck-style **Projects
Hub**: one destination that lists projects beautifully and offers one obvious
way to create or import — including **import a Git project**, the headline
request.

**Design mockups:**
- Projects list → [`mockups/projects-list.png`](mockups/projects-list.png)
- `+` create/import sheet → [`mockups/create-import-sheet.png`](mockups/create-import-sheet.png)
- Clone Git dialog → [`mockups/clone-git-dialog.png`](mockups/clone-git-dialog.png)

---

## 2. UX / UI design (phone-first)

### 2.1 Projects Hub screen — layout

```
┌──────────────────────────────────────────┐
│  Projects                     🔍   ⋮       │  ← TopAppBar: title, search, overflow
├──────────────────────────────────────────┤
│ [ All ] ( Git ) ( C ) ( Python ) ( Web )  │  ← filter chips (single-select)
├──────────────────────────────────────────┤
│ ┌──────────────────────────────────────┐ │
│ │ [JS] demo_flask                    ⋮ │ │  ← project card
│ │      ⌥ main · 3 files · 2 days ago    │ │
│ └──────────────────────────────────────┘ │
│ ┌──────────────────────────────────────┐ │
│ │ [C]  hello-c              [M]      ⋮ │ │  ← [M] = has uncommitted changes
│ │      ⌥ main · 4 files · 3 days ago    │ │
│ └──────────────────────────────────────┘ │
│                          … more cards …    │
│                                            │
│                                      ( + ) │  ← FAB → create/import sheet
├──────────────────────────────────────────┤
│  Home   Projects   Editor  Terminal  ⚙︎    │  ← bottom nav (Projects active)
└──────────────────────────────────────────┘
```

**Project card anatomy** (each row):
- **Leading language/type icon** — a rounded colored square: purple `JS`/web-app,
  blue Python, orange `C`, green globe (static web), grey generic. Derived from
  `ProjectConfig.type` (Phase 14) or `ProjectRunDetector` for `auto` projects.
- **Title** — project folder name, white, bold, ellipsized.
- **Subtitle** — `⌥ <branch> · <n> files · <relative mtime>`. The branch chip and
  icon appear only when the project is a git repo (`.git/` present). Non-git
  projects show `· <n> files · <mtime>` only.
- **Status badge** — a small yellow `M` pill when `git status --porcelain` is
  non-empty (uncommitted changes). Cheap to compute lazily/off the main thread;
  cached per project. (Reuses Phase 13 `GitStatusParser`.)
- **Overflow `⋮`** — per-project menu (see §2.4).

**Empty state:** when no projects exist, a centered illustration + "No projects
yet" + a primary "Create your first project" button that opens the same sheet as
the FAB. (The Phase 14 bundled `demo_flask` normally prevents a truly empty
list on first run.)

### 2.2 Filter chips + search

- **Filter chips** (single-select, horizontally scrollable): `All`, `Git`, `C`,
  `Python`, `Web`. `Git` = repos with `.git/`; the language chips filter by
  detected/declared type. Pure client-side filter over the already-loaded list.
- **Search** (🔍 in the app bar): expands an inline search field filtering
  projects by name (case-insensitive substring). Optional stretch: also match
  file names within projects (defer if it costs a tree scan — keep it to project
  names for Phase 15).

### 2.3 The unified `+` create/import sheet (the headline)

Tapping the FAB (or the empty-state button, or app-bar `⋮ → New`) opens a
**Material 3 bottom sheet** titled **"New Project"** with four large tappable
rows (mirrors Spck's add-dropdown, adapted to CodeC's capabilities):

| Row | Icon | Subtitle | Action |
|---|---|---|---|
| **New Project** | file-plus | "Start from a template" | → template picker (§2.3.1) |
| **Clone Git Repository** | git-branch/download | "Import from GitHub, GitLab, Bitbucket" | → Clone dialog (§2.3.2) |
| **Import ZIP** | archive | "From device storage" | → SAF file picker (Phase 8 flow) |
| **Open Folder** | folder | "Pick an existing folder" | → SAF folder picker → register as project |

> This single sheet is the whole point of the phase: **one obvious place** to
> create or *import a git project*, exactly like Spck's add menu.

#### 2.3.1 New Project → template picker

Reuses Phase 14's `ProjectTypes` / `ProjectScaffold`. The picker is a compact
grid/list of templates, each with an icon + name + one-line description:
- **Auto (detect)** — default; no starter files, RUN ▶ infers type (Phase 14 D10).
- **C Program** — `main.c` starter.
- **Python Script** — `main.py`.
- **Flask Web Server** — `app.py` + `index.html` (Phase 14 preset, port 5000).
- **FastAPI Server** — Phase 14 preset, port 8000.
- **C Microservice** — Phase 14 preset, port 8080.
- **Static Web** — `index.html`.

After choosing a template → a small **name** field → **Create**. (Spck's flow:
pick framework → name → Create.) On create, the project appears at the top of
the list and opens.

#### 2.3.2 Clone Git Repository dialog (import a git project)

The centerpiece for "import git project like Spck". A dialog
([`mockups/clone-git-dialog.png`](mockups/clone-git-dialog.png)) with:
- **Repository URL** field, placeholder `https://github.com/user/repo.git`, with a
  trailing **QR-scan icon** (stretch — Spck added a QR scanner for remote repo
  URLs; behind camera permission, defer if it complicates the phase).
- **Project name** field, auto-filled from the URL's last path segment (`repo`),
  de-duplicated (`repo_2`, `repo_3`, …) exactly like Phase 8/13.
- **Advanced (collapsible):** a **Branch** dropdown (defaults to `main`; populated
  via `git ls-remote --heads` when reachable, else free-text). Optional
  **shallow** toggle (`--depth 1`, on by default — Spck fetches only the latest
  commit tree; matches CodeC's mobile-bandwidth goal).
- **Hint:** "Private repos need a token — add it in Settings" linking to the
  Phase 13 GitHub Account card.
- Buttons: **CANCEL** / **CLONE** (filled purple).

**Engine:** reuse Phase 13 `GitManager.clone(...)` + `GitContext` (askpass token
transport, redaction, 300 s network timeout). Progress is shown inline (spinner +
"Cloning…") and the partial clone is deleted on failure (Phase 13 D5). On
success the repo lands in the projects root, `.codec/project.json` is ensured
(type `auto`), and the project opens. **No new git engine code** — this is a UI
that calls the existing one.

### 2.4 Per-project overflow (`⋮`) menu

Tapping a card's `⋮` opens a menu whose items depend on whether the project is a
git repo:

Always: **Open**, **Rename**, **Duplicate** (stretch), **Export ZIP**
(Phase 8), **Delete** (confirm dialog).

Git repos additionally: **Source Control** (→ Phase 17 sheet), **Pull**,
**Switch Branch** (→ Phase 17), **Copy remote URL**. Non-git projects show
**Initialize Git** (stretch — `git init`) instead.

> Pull / Switch Branch / Source Control items are *wired* here but *implemented*
> in Phase 17; in Phase 15 they can route to the existing Phase 13 clone/commit
> plumbing or be disabled with a "coming in Source Control" affordance. Keep the
> menu structure Spck-shaped from the start.

### 2.5 Visual system

- Material 3, dark-first, honoring the existing `ThemeManager`/`EditorThemes`
  and the accent purple already in `Color.kt` (`Purple80 #D0BCFF` / brand
  `#B794F6`). No new theme system.
- Cards: 12dp radius, `surfaceVariant` background, 8dp gaps, 1-line title +
  1-line subtitle, 56dp leading icon.
- Respect safe-area/cutout insets (Phase 6 pattern) and bottom-nav height.

---

## 3. Architecture & implementation steps

The engines already exist; this phase is **presentation + wiring**.

1. **Project metadata model.** Extend the in-memory `ProjectInfo` the ViewModel
   emits with: `type` (from `ProjectConfig`/detector), `isGit` (`.git/` exists),
   `branch` (cheap `git rev-parse --abbrev-ref HEAD` or read `.git/HEAD`),
   `fileCount`, `lastModified`, and `hasChanges` (lazy `git status --porcelain`
   count). Compute off the main thread; cache; refresh on pull-to-refresh and on
   return to the screen. **No blocking git calls on the UI thread.**
2. **Projects Hub UI.** Build the card list + filter chips + search + FAB
   (`ProjectsHubScreen` or refactor `FileManagerScreen`'s project section). The
   file *tree* stays where it is (opened when a project is tapped / in the editor
   drawer, Phase 16).
3. **Create/Import bottom sheet.** One `ModalBottomSheet` with the four rows;
   route each to its existing flow (template scaffold, clone dialog, SAF ZIP,
   SAF folder).
4. **Clone dialog polish.** Rebuild the Phase 13 clone dialog to the §2.3.2 spec
   (URL + auto name + Advanced branch/shallow + token hint). Same `GitManager`.
5. **Open Folder (SAF).** Let the user pick an existing device folder and
   register it as a CodeC project (copy-in or reference per the Phase 8 storage
   model — match whatever Phase 8 does for ZIP import to stay consistent).
6. **Per-project `⋮` menu** with the git-aware items (§2.4).
7. **Bottom navigation.** Ensure "Projects" is a first-class destination in the
   nav (`Screen.kt` + `MainActivity` nav graph) so the hub is one tap away, like
   Spck's Projects tab.
8. **Host unit tests** (no JDK locally — CI runs them):
   - `ProjectInfoMapperTest` — type→icon mapping, branch/file-count/mtime
     formatting, `hasChanges` from a fake porcelain string.
   - `ProjectFilterTest` — chip filters (All/Git/C/Python/Web) + name search.
   - `CloneUrlParseTest` — URL → suggested project name + de-dup (extends the
     Phase 13 parser tests).

---

## 4. Exit condition & device verification recipe

A fresh APK (latest green `Build APK`) passes on the owner's aarch64 device:

```text
# Projects Hub
1. Open the app → bottom nav "Projects" → the Projects Hub lists demo_flask
   (bundled) as a card with a type icon, "main" branch chip, file count, mtime.
2. Filter chips: tap "Python" → only python/flask projects show; "All" → all.
3. 🔍 search "demo" → list narrows to demo_flask; clear → full list returns.

# Unified create/import sheet
4. Tap the + FAB → bottom sheet shows exactly: New Project, Clone Git Repository,
   Import ZIP, Open Folder.

# New Project
5. New Project → pick "C Program" → name "hello-c" → Create → project appears at
   top and opens; main.c present.

# IMPORT A GIT PROJECT (headline)
6. + → Clone Git Repository → paste https://github.com/octocat/Spoon-Knife
   → name auto-fills "Spoon-Knife" → CLONE → "Cloning…" → the repo appears in the
   list with a "main" branch chip and correct file count, and opens in the tree.
7. Per-project ⋮ on the cloned repo shows git items (Source Control, Pull,
   Switch Branch, Copy remote URL).

# Import ZIP / Open Folder
8. + → Import ZIP → pick a .zip → imports as a new project (Phase 8 behavior).
9. + → Open Folder → pick a device folder → registers as a project.

# Regression
10. Delete a project via ⋮ → confirm → it disappears; demo_flask still runnable
    (Phase 14 RUN ▶ still works from the editor).

PASS = steps 1–9 succeed without manual fixes; step 10 leaves the app healthy.
```

---

## 5. Invariants & scope guard

- **Client-only.** No `[repo-build]`, no bootstrap/`$PREFIX` changes, no
  `$PREFIX/bin` writes. Reuse `GitManager`/`GitContext`, `ProjectManager`,
  `ProjectScaffold`, `ProjectTransfer`.
- No `.` on PATH; git resolves to `$PREFIX/bin/git` only (Phase 13 D7); tokens
  never touch argv, `.git/config`, or the terminal env (Phase 13 D2).
- Cloning without git installed → the Phase 13 actionable guidance ("Modules →
  Git or `pkg install -y git`"), not a crash.
- No git call on the UI thread; `hasChanges`/branch are computed async + cached.
- **Out of scope (later/stretch):** QR-scan URL entry, `git init` for non-git
  projects, in-file search across projects, project duplicate. Note them; don't
  block the phase on them.

---

## 6. Implementation record (2026-08-31, `arena/01a05743-codec`)

**Status: IMPLEMENTED — CI pending.** Started on the owner's "Start phase 15".
`[client-only]`, no `[repo-build]`; all engines reused (Phases 8/9/13/14).

**Research notes** (RESEARCH-WHEN-NEEDED rule):
- Spck's public docs describe its "Projects" list as an Open File/Folder +
  "New Folder" + GitHub import entry flow ([docs.spck.io](https://docs.spck.io)).
  Per the CLEAN-ROOM LAW only the visible behavior was cloned (mockups +
  public docs); no Spck code/assets were consulted or copied.
- `.git/HEAD` (plain text) is the standard cheap branch read — Termux-adjacent
  tools document the same trick; avoids a `git rev-parse` process per card.
  Worktree-style `.git` **files** (`gitdir:` pointers) are followed one hop.
- `git ls-remote --heads <url>` output (`<sha>\trefs/heads/<name>`) is
  documented in the public git man page; used verbatim for the branch picker.
- M3 `ModalBottomSheet`/`FilterChip`/`Switch` are stable at Compose BOM
  2024.09.00 (`@OptIn(ExperimentalMaterial3Api::class)` only for the sheet).

**Files touched:**
- **NEW** `ui/projects/ProjectsHub.kt` — pure, host-tested engine:
  `ProjectHubEntry` (kind/icon/filters), `ProjectsHub` (kind mapping from
  `ProjectConfig.type` or the Phase 14 detector for `auto`, chip+search
  filtering, subtitle/relative-age formatting, `.git/HEAD` parser,
  `ls-remote` parser, `.git/config` remote-URL parser, unique-name dedup,
  branch-name gate, porcelain-changes check) and `ProjectHubStats`
  (bounded tree scan: file count + newest mtime, skipping `.git`/`.codec`).
- `ui/projects/GitManager.kt` — `clone(url, dest, shallow, branch)`
  (defaults = Phase 13 argv verbatim) + `listRemoteBranches(url)`
  (`ls-remote --heads`, redacted, network timeout). New argv +
  reject-before-exec tests in `GitManagerTest` (5 added).
- `ui/viewmodels/FileManagerViewModel.kt` — `hubEntries` StateFlow built on
  `Dispatchers.IO` (scan + `.git/HEAD` + lazy `git status` only when git is
  installed); `renameProject`, `pullProject`, `remoteUrlFor`,
  `fetchRemoteBranches`; `cloneFromGitHub` gains `branch`/`shallow` params.
- `ui/screens/FileManagerScreen.kt` — the list half became the Projects Hub:
  filter chips (All/Git/C/Python/Web), inline search, Spck-style cards
  (type square, `⌥ branch · N files · age`, yellow `M` badge), per-project
  git-aware `⋮` menu, FAB → unified 4-row bottom sheet, new Clone dialog
  (URL → auto name, Advanced: branch + Fetch-branches chips + shallow,
  token hint → Settings). File-tree mode untouched (Phase 8 intact).
- `ui/navigation/Screen.kt` + `MainActivity.kt` — bottom nav is the mockup's
  five tabs **Home · Projects · Editor · Terminal · Settings** (Packages
  stays one tap away on Home); `FileManager.title` is "Projects" (route
  `file_manager` preserved). Clone dialog `onOpenSettings` wired.
- `res/values/strings.xml` — 34 new hub/dialog strings.
- **Tests** (host JVM, CI-executed): `ProjectsHubTest` (13 new — mapping,
  filters/search, subtitles, age buckets, HEAD/ls-remote/config parsers,
  dedup, branch gate, porcelain, scan) + `GitManagerTest` (5 new clone-flags
  / ls-remote tests; fake-git updated to be POSIX-sh safe).

**Design decisions:**
- **D1** — Card kind is hub-only decoration derived from `ProjectConfig.type`
  / `ProjectRunDetector` (auto), so hub, wizard and RUN ▶ can never disagree.
- **D2** — Hub list order: newest-touched first, name as tiebreak
  (deterministic for CI + matches "appears at the top" in the recipe).
- **D3** — `hasChanges` is fetched during refresh only (no auto-polling),
  only when the packaged `git` exists; failure → badge omitted (graceful).
  Branch chips never spawn git (pure `.git/HEAD` read).
- **D4** — Branch names are argv-safety-gated (`ProjectsHub.isValidBranchName`:
  no flag lead, no spaces/control chars, no `..`, no `refs/` prefix,
  ≤200 chars) — belt-and-braces over ProcessBuilder's shell-free exec.
- **D5** — `GitManager.clone` extended with defaulted params instead of a new
  method: Phase 13 call sites keep bit-identical argv (regression safety).
- **D6** — The old Files-menu import entries (Import Folder/ZIP, Clone) were
  folded into the unified `+` sheet; the app-bar `⋮` keeps only "New Project"
  (opens the sheet) and "Refresh". Nothing is lost: same launchers, same VM
  flows. Tree-mode menu untouched.
- **D7** — Search matches project names only (spec §2.2); in-file search
  stays deferred. QR URL entry, Duplicate, and Initialize-Git remain the
  recorded stretch items — deliberately not blocking the phase.
- **D8** — Switch Branch is a present-but-informed menu entry (snackbar
  pointing at Phase 17), keeping the Spck-shaped menu structure per spec.
- **D9** — Shallow (`--depth 1`) defaults ON in the new dialog (Spck parity,
  mobile bandwidth); terminal `git clone` behavior unchanged.

**CI:** first run pending on this branch (see the next chat's `gh run list`).
**Device:** §4 recipe unchanged — run it against the artifact `CodeC-IDE` of
the latest green `Build APK` run for `arena/01a05743-codec`.
