# PART 55.3 — Files, Search, Repository

> **Status:** 🚧 IMPLEMENTED (2026-09-22) · **Effort:** M · **Cost:** `[client-only]`
> **Files:** `ui/editor/ProjectSearch.kt` (new, pure), `ui/components/EditorSidePanel.kt` (the three slots),
> `ui/screens/EditorScreen.kt` (the Files slot wiring + the search effect)

---

## 1. Files — the tree stays, it just moved

The shot (`Screenshot_20260922_122203`) shows `FILES` with four header controls (`⌕` search,
`⌖` locate, `▤+` new file, `▭+` new folder) and `...`; the **project name as the tree root** with
a chevron; folders with `›`; every row with `...`; the **selected row as a full-width bar** with
the **green ▶ left of the file icon** on `index.html`; and an error pill at the bottom.

What shipped:

- the tree is the **unchanged** `EditorProjectDrawer` (732 lines: the header with the project
  switch, the PROJECTS list, the four tool actions, the typed file icons, the git letters, the
  selected-row highlight, the launch-default ▶, the footer rows) — the roadmap's own words:
  *“The tree itself stays”*;
- it rides in the panel's Files slot (`files = { EditorProjectDrawer(...) }`), so every one of its
  actions, dialogs and anchors are the same code as before;
- the tour's beats 2-4 keep working through that same tree (see [55.1 §3](PART_55_1_PANEL.md)).

**Not built, because the shots do not show it** (54.3 §4 rows 2 and 9): the per-row `...` menu's
contents, the `...` on the section label, and the screen after *Initialize Repository*. The
per-row `...` already exists in the tree (the Phase 16 `DrawerEntryMenu`: open, new file, new
folder, run in terminal, launch, set/clear launch default, rename, delete, copy path) — that is
CodeC's own menu, and this phase did not touch it.

## 2. Search — the engine is real, the empty state is the shot's

The shot (`124052`) shows one field (`Find Text`), five header glyphs, a `RESULTS` row with three
controls, and **nothing else**. A field that did nothing would be a dead control, so the engine
behind it is real — and it is **pure**, which is the only way it can be tested here:

```text
  ProjectSearch.search(root, query, options)   depth-first, name-sorted, in-project only
  ProjectSearch.searchFile(root, rel, query)   one file: size cap, binary skip, guard
  ProjectSearch.matchesIn(text, query, …)      line + 1-based column per hit
  ProjectSearch.Options(regex, caseSensitive, wholeWord)
  caps:      MAX_HITS = 200        MAX_FILE_BYTES = 512 KiB
  skips:     .git, node_modules, build, .gradle, bin, dist, any dot-dir, non-text extensions
  guard:     ProjectPathGuard.childOf(root, rel) — `..`, absolute paths and symlink escapes are refused
```

The panel's header glyphs are wired to the two options the shot names as toggles and a clear:

| Glyph | Meaning | Wired |
|---|---|---|
| `.*` | regex | yes (`Options.regex`) |
| `Aa` | case-sensitive | yes (`Options.caseSensitive`) |
| `Ab|` | whole word | yes (`Options.wholeWord`) |
| `⌫` | clear the field and the hits | yes |

The shot's fifth glyph (a filter mark) is **not drawn**: neither a shot nor a freshly fetched doc
says what it filters. *“Do whatever is good”* (the owner's answer about the unseen glyphs) is
read the way the roadmap reads it — **prefer omit-and-ask over a guessed action** — with one
exception where a real CodeC action existed (the clear).

**Empty means empty:** with nothing typed there is **nothing** under `RESULTS`, exactly as the
shot shows. Two states the shot cannot show are handled honestly and are deviations worth naming:
a query with **zero hits** prints one muted line (`No matches`) — silence there would look like a
bug — and a **half-typed regex** is silence, not an error wall (`patternFor` returns null).

**Results** are rows of `path:line` + the line's text; a tap opens the file and jumps to the line
(`viewModel.openFile` + `viewModel.jumpToLine`) and closes the panel. The result list is capped by
the engine, and the run happens on `Dispatchers.IO` under a `LaunchedEffect(query, options, project)`,
so the previous walk is cancelled when the query changes.

## 3. Repository — the shot's empty state, CodeC's engine

The shot (`124055`): `REPOSITORY`, a `⌕`, the sentence *“No Git Repository initialized. Initialize
one to version your work.”* **left-aligned**, and a centred blue **Initialize Repository** button.

```kotlin
if (!hasRepository) { the shot's sentence; the centred button }
else { the branch; “Changes: N”; a Source Control button }
```

- `hasRepository` is derived from the editor's own `gitBranch` (`null` = no repo), so the panel
  does not invent a second git state machine.
- The **button runs CodeC's git**, never SPCK's: it hands `git init` to the Terminal — the same
  path the app's own readiness sentence already points users at (`GitReadiness`,
  `GitErrors`), and the same hand-off the editor's “Run in terminal” uses. There is no invented
  screen after the button (the shots do not show one).
- The **non-empty** state is CodeC's own and is a deviation from the shot (which only shows the
  empty one): branch + change count + a button to the existing Source Control sheet. The two
  doors share one lambda (`openSourceControl`) with the drawer footer's row, so there is one
  truth about where the sheet comes from.
- **Not replaced:** `GitManager`, the signer, the clean-room rule. The roadmap says so explicitly.

## 4. Verification

- `ProjectSearchTest` (15 host cases): empty query, line/column, the three options, half-typed
  regex, the hit cap, the stable in-project order, skipped dirs, non-text extensions, the
  `MAX_FILE_BYTES` cap (built from the constant, not a hardcoded size), a binary file, a missing
  file, and the path guard (`..`, absolute, outside-the-root).
- `SidePanelWiringTest`: the panel calls `ProjectSearch.search(...)` on `Dispatchers.IO`.
- **Device pass required:** [P8-P10](DEVICE_ROUND.md).
