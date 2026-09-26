# Phase 59-63 — the UI/feature-upgrade spec, re-scoped against this checkout

> **What this file is.** On 2026-09-22 the owner sent a five-section specification headed
> *“Project Specification: CodeC UI/UX & Feature Upgrade (SPCK Editor Parity)”*. This is the
> research pass that spec's own culture requires — every bullet checked against the code on
> this checkout (HEAD `50fcfa7`), then grouped into phases with a remove/add/exit each.
>
> It is **not** a promise to build five sections as written. Three of the spec's five
> premises describe behaviour this app already has, and one asks for chrome no shot shows.

> **The laws still in force**
> - **Clean room.** *Visible behaviour only; no SPCK code, sample, git engine, or shop.* Parity
>   means we implement the **behaviour ourselves**. A copied file is a failed phase, whatever
>   the spec's title says.
> - **The shots are the reference** for the phone UI (*“They accept only this UI”*), except the
>   recorded bottom-bar reversal. Anything a shot shows, the shot wins; anything **no** shot
>   shows is *“if need say me”* — ask, do not invent.
> - **One phase at a time**, researched, with part docs, a wiring pin, CI green, and **no PR,
>   no merge, no `main` push** without the owner's word.

---

## 0. The verdict, in one table

| Spec bullet | State on `50fcfa7` | Evidence |
|---|---|---|
| §1 clicking a project lacks a folder structure | **already built** | the tree is the editor's, exactly as the shots have it: `FileTreeRepository.buildTree` (`ui/projects/FileTreeRepository.kt:28`), expansion state (`ui/editor/FileTreeCollapse.kt`), rendered by the drawer (`ui/components/EditorProjectDrawer.kt:68`) and the Phase 55 side panel's Files slot (`ui/components/EditorSidePanel.kt:437`) |
| §1 All / Recent / Create tabs | **partial** | chips exist: `ProjectHubFilter {ALL, GIT, C, PYTHON, WEB}` (`ui/projects/ProjectsHub.kt:21`, drawn `FileManagerScreen.kt:1398-1402`). *Recent* exists as the side panel's RECENT card (shot `124049`; `ui/editor/SidePanelPlan.kt:150`), not as a hub filter. *Create* is the hub's own sheet |
| §1 search project name at the top | **already built** | `searchOpen` / `searchQuery` (`FileManagerScreen.kt:224-225`), the top-bar field (`:360`), the pure filter (`ProjectsHub.kt:171`), and a no-match state (`hub_no_match`) |
| §1 auto-generated distinct logo per project | **partial** | `ProjectIconView` exists (`ui/components/ProjectIconView.kt:22`) but is **kind**-based (C / Python / web / git), not name-distinct |
| §2 tab menu: Quick Close, Close Others, Close Unmodified, Hide Tabs, sort | **partial** | the tab row's trailing cell owns a `DropdownMenu` (`ui/components/EditorTabBar.kt:121`): Close tab / Close others / Close all / Copy path (`:125-147`, strings `:446-448`). Missing: close-unmodified, hide-tabs, sort |
| §2 bottom coding toolbar with brackets/semicolons/operators | **already built** | `EditorKeysRow` — “Spck's signature row, mockup-exact”, horizontally scrollable keycaps above the keys (`ui/components/EditorKeysRow.kt:41-48`), data-driven by `keysForContext` with pair caps (`ui/editor/KeysContext.kt:34`, `:106`), plus the CodeC Keys keyboard's dense SYMBOLS layer (`ui/keyboard/KeyboardLayout.kt:90`) |
| §2 top bar: Files, Search, Git, Profile | **built elsewhere, on purpose** | the shots put those five on the **left rail**: `RailPanel {NAVIGATION, FILES, SEARCH, REPOSITORY, RESERVED}` (`ui/editor/SidePanelPlan.kt:112-124`), REPOSITORY opening the git pane (`EditorSidePanel.kt:178-182`). The top row is shot `122157`/`124105`'s (☰, 🔍, Test▷, ▶; `EditorScreen.kt:1496,1503,1609`) — adding four more icons there contradicts the reference |
| §3 dedicated web preview screen | **already built** | `ui/screens/WebPreviewScreen.kt` (in-app server, live reload, LAN share) |
| §3 blended console | **partial** | a console strip exists, fed by `onConsoleMessage` with levels (`WebPreviewScreen.kt:407-417`, `:506-523`). No level filters, fixed 120 dp, no DOM/Network/Resources |
| §3 preview controls: Refresh, Open in Browser, Zoom, resolution | **partial** | Refresh and Open-in-browser are there (58.3's ☰); **zoom and resolution do not exist** (`grep zoom WebPreviewScreen.kt` = empty) |
| §4 settings search | **new** | no search anywhere in `ui/screens/SettingsScreen.kt` (1,636 lines) |
| §4 collapsible categories | **partial** | sections exist as headers (`SettingsSectionHeader`, `:1387`, used at `:188,271,331,372,415,478,566,739,837,968,1268,1306`); none collapse |
| §4 custom snippets | **already built** | `customSnippets` is an argument of `keysForContext` (`ui/editor/KeysContext.kt:34`) and the strip renders them |
| §4 AI-assistant toggles | **not ours to add** | the fifth rail slot is *reserved* for the owner's planned AI (*“Reseserve it i have plan for ai”*) — an AI toggle is a product decision, not parity |
| §4 tab mode / pin-on-edit | **already built** | `tabMode` (`ui/editor/EditorShellUi.kt`, `EditorScreen.kt`) |
| §4 haptic keypress | **already built** | `codec_keys_haptics` + `CodecHaptics.kt` / `Haptics.kt` |
| §4 tablet mode, predictive keyboard toggle, touch action bar | **new** | none exist. ⚠️ a *predictive* toggle would reverse 57.2's decision (the chip row is gated on a typing surface, `StripContext.typingSurfaceUp`) — that needs the owner's word, not a switch |
| §5 “Git is handled via terminal or basic token input” | **refuted** | `GitManager.kt` is a full argv-list engine (status `:137`, stageAll `:182`, stageFile `:193`, unstage `:204`, commit `:241`, push `:274`, pull `:489`, checkout `:757`, stash `:822`, clone `:504`), and `GitControlSheet` (`ui/screens/GitControlView.kt:95`) is the dedicated pane: per-file rows with a stage/unstage control (`:978-1078`), diff dialog, conflicts + “Mark resolved”, commit (+push), pull, refresh, PUBLISH TO GITHUB. It opens from the hub card (`FileManagerScreen.kt:1199`) **and** the editor's Repository rail slot (`EditorSidePanel.kt:181`) |
| §5 stage visually / Commit All / Push / Pull / Revert | **already built** except per-file revert | staging toggle ✓, commit ✓, push ✓, pull ✓, refresh ✓; a per-file *discard* action is **not** in the sheet (the manager has `checkout`/`stash`; `discard` is only a generic string, `strings.xml:149`) |

**Read that table as the deliverable it is:** three of the five sections are largely built, and
the honest work left is a much smaller than the spec implies — mostly *find, filter, show,
organise*, plus two genuinely new instruments (name-derived project marks; preview zoom).

---

## 1. What is proposed

Five phases, in the spec's own priority order, each small enough to ship with its own pins.
Only the *new* rows are numbered; already-built behaviour is verified, not rebuilt.

### 59 — Projects hub: find, filter, identify  *(spec §1, its own High Priority)* — ✅ **BUILT (2026-09-22)**
- **Built:** the row is the spec's own **`All · Recent · Create`** (the owner's answer to the §3
  question below), where *Create* is an action chip opening the same add sheet the hub's ＋ opens;
  and the card's leading square is the project's **own name-derived mark** (initials + one of the
  five `CodecPalette.TILE_*` seats, hashed from the name by FNV-1a), replacing the kind-only glyph.
  Records: [`chat-phase59/`](chat-phase59/README.md) + `PART_59_1` + `PART_59_2`.
- **One recency rule:** *Recent* is `ProjectHubFilter.RECENT`, a **ranking** (not a flag on the
  entry), fed by the side panel's own `RecentProjects.build` with the panel's cap — and the clock
  it ranks by is now read off disk once (`ScanResult.folderModified` →
  `ProjectHubEntry.folderModified`/`recency`, passed by `FileManagerViewModel`), so the hub's chip
  and the panel's RECENT card cannot disagree.
- **Kept, verified not rebuilt:** the top search field (still combines with *Recent*), the tree, the
  hub→tree path.
- **The owner's answer moved the language chips out of the row** (his chosen option said so): the
  policy is untouched (`ProjectHubFilter.GIT/C/PYTHON/WEB`, `filters`, `filterEntries` and their
  cases), the kind is now on every card's subtitle (`kindLabel`), and a future home for those chips
  is one chip, not new machinery.
- **Exit, checked:** a name filter and a recent filter both narrow the list ✅; three same-kind
  projects carry three marks ✅; nothing else that worked is gone — the kind the mark used to show
  moved to the subtitle ✅. Local: **37 passed / 0 failed** for the phase, **970 passed / 0 failed**
  across the broad pure-source set (which includes the core-file pins). **CI: green on the third
  round** — `35741546107` on `55d32f3`; rounds 1 and 2 were red for one class (a stray `@Composable`
  left by the edit, invisible to the sandbox's non-Compose host JVM), which is now pinned by
  `ComposableAnnotationTest`.

### 60 — Tabs and the coding row  *(spec §2)* — ✅ **BUILT (2026-09-26)**
- **Built:** *Close unmodified* (a new pure `TabClosePolicy` + `EditorViewModel.closeUnmodifiedTabs`,
  reusing the ordinary `closeTab`), *Hide tabs* (the tab row folds into a reveal strip, and the
  bottom bar is parked through the existing `NavBarPolicy`/reveal-handle idiom), and the three
  sorts (`TabSortPolicy`: name / extension / path, applied to the **open tabs** so the strip,
  Ctrl+Tab, the close fallback and the eviction pick read one order). Records:
  [`chat-phase60/`](chat-phase60/README.md) + `PART_60_1` + `PART_60_2`.
- **The owner's answer (2026-09-26) — *Hide tabs* = both readings in one row:** the editor's
  file-tab row (the spec's own meaning) **and** the bottom bar's manual door, with the editor's ⋮
  cell deliberately left outside the fold so undo/save/format stay reachable. The bar's half stays
  editor-scoped, because the reveal handle that un-hides it only exists in the editor.
- **Verify, not rebuild (done):** the symbol strip is `EditorKeysRow`, untouched; the shots show one
  row, so the second, symbol-specific toolbar is still unbuilt and still needs the owner's word.
- **Exit, checked:** the menu lists close/others/all/unmodified, hide-tabs and three sorts ✅;
  sorting is a pure function with host tests ✅ (11 `TabSortPolicyTest` cases); the top row is
  unchanged from 57.1 ✅ (`EditorChromeSlotTest`'s top-row pins pass untouched). Local: **35 passed
  / 0 failed** for the phase, **534 passed / 0 failed** for the phase + broad regression set.
  **CI ✅ GREEN on the first round** — `36220293009` on `017dd2c` (zero failed steps).
- **Deliberately absent, recorded:** no "back to open order" row (the spec names three sorts and no
  neutral one; reconstructing the open order would need a second field on `EditorTab`), and no tick
  on the sort rows (a one-shot re-order is not a mode — an indicator would lie the moment a file
  opens).

### 60 — (the plan, as it stood before the build)
- **Add:** to the tab menu that already exists — *Close unmodified*, *Hide tabs*
  (the existing hide bar is `NavBarPolicy.hideNavBar` + the reveal handle; this is the menu's
  door to it), and the three sorts (alphabetical / by extension / by path) as **pure ordering
  policy** over `EditorTab` (`ui/editor/EditorTab.kt`) with pins.
- **Verify, not rebuild:** the symbol strip (it is the app's oldest “Spck signature row”).
- **Ask, don't guess:** a *second*, symbol-specific toolbar when the strip already carries
  pairs (`{}`, `()`, `""`) and the CodeC Keys keyboard has a SYMBOLS layer — the shots show one
  row (`124105`), so a second needs the owner's word.
- **Exit:** the tab menu lists close/others/all/unmodified, hide-tabs and three sorts; sorting
  is a pure function with host tests; the top row is unchanged from 57.1.

### 61 — Preview: console and controls  *(spec §3)*
- **Add:** the console strip becomes a real surface — level filters (log/info/warn/error) over
  the lines already captured, a drag handle for its height, and it no longer hides itself when
  empty (a console that appears only when it has content is a console you cannot open).
- **Add:** *Zoom* and *Screen resolution* behind 58.3's existing ☰, implemented with WebView's
  own `setSupportZoom` / `setInitialScale` / `setLoadWithOverviewMode` — no new engine.
- **Ask, don't guess:** *Elements* and *Network* tabs. Network is reachable honestly
  (`WebViewClient.shouldInterceptRequest`), Elements needs JS injection; neither appears in any
  shot, and both are instruments, not chrome — so they wait for a yes.
- **Exit:** the console opens, filters, and resizes; zoom and resolution work; Refresh and
  Open-in-browser still behave as 58.3 left them.

### 62 — Settings: find and group  *(spec §4)* — ✅ **BUILT (2026-09-22), the owner's chosen start**
- **Built:** a search field at the top that filters rows by their own labels — a pure matcher over
  the audit's **66** rows (the doc's own count; this line said 65 and the table held 66 — see
  `docs/chat-phase38/SETTINGS_AUDIT.md`) — and collapsible sections over the twelve headers that
  already exist. Records: [`chat-phase62/`](chat-phase62/README.md) + `PART_62_1` + `PART_62_2`.
- **Built, in the sections' own terms:** the sidebar-free `SettingsCatalog` (generated from the
  screen's own `title = …` arguments, pinned back against the screen *and* the audit table),
  `SettingsSearch`'s three laws, and `SettingsSection` wrappers so a folded or filtered section
  takes its bespoke preview blocks (and its leading divider) with it.
- **Add only where a real behaviour exists:** *Haptic* and *touch action bar* are already built
  (51.4/28.2, the 57.2 row); **tablet mode is refused in writing** until the owner names what it
  should change — nothing in this checkout behaves differently when it flips, and this app does
  not ship dead controls.
- **Refuse, in writing:** an AI-assistant toggle (the fifth slot is reserved for the owner's
  own plan) and a predictive-keyboard switch that would silently reverse 57.2.
- **Exit, checked:** typing “haptic” finds the haptics row ✅; the nine row-bearing sections fold
  (the three form-only ones are not tappable, because folding them would hide nothing) ✅; the
  audit still matches the screen ✅ (`SettingsAuditTest` untouched). Local: **25 passed / 0
  failed** for the phase, **881 passed / 0 failed** across the broad pure-source set, with the
  phase's one census re-cut (the `IconButton` census 17 → 18, reason recorded in
  `TouchTargetTest`). **CI round 1 was red for cause and round 2 went green:** the section wrappers
  cut two declarations from their readers (CI's compile), a third boundary bug was one no compiler
  could see (the `BuildConfig.DEBUG` guard ended up outside the Developer Options fold), and the
  phase now ships two structural pins that catch all three — *every section slice is a balanced
  block*, *no local declaration is split from its uses* (both validated against the pre-62 screen).
  CI ✅ **GREEN** `35724187664` on `aa1fcba`.

### 63 — Git pane: verify and fill  *(spec §5)*
- **Verify:** the pane, the staging toggle, diff, commit(+push), pull, conflicts — all exist.
- **Fill (only after the ask):** a per-file **Discard** (the `Revert` of the spec's wording)
  and, if wanted, a first-class *Source control* entry point in the editor's rail (it is
  already one tap: Repository → *Source control*).
- **Exit:** nothing in §5 is a rebuild; the gap list is closed or explicitly refused.

---

## 2. Rejections, in writing (the spec's culture demands them)

1. **Copied SPCK assets, code, or its git engine.** The spec is titled “parity”; parity here
   means our own implementation of the behaviour, never a transplant.
2. **Files / Search / Git / Profile forced into the top app bar.** The shots' top rows are
   `122157` and `124105`; the four rail icons 55 built are those five positions, with the
   fifth reserved. Moving them into the bar undoes 55 and contradicts the reference.
3. **A second coding toolbar.** One strip already exists and the shots show one row.
4. **An AI-assistant toggle** (reserved slot) and **a predictive-keyboard switch** (reverses
   57.2's typing-surface gate) — both are owner decisions.
5. **“Basic file switching” and “Git via terminal”** as descriptions of this app — refuted
   above, with file:line, so no phase spends effort fixing what is already right.

---

## 3. Decisions — ANSWERED by the owner (2026-09-22)

Written here so the answers are on the record; asked in chat in the same breath.

0. **The hub's chip row** (asked when 59 started, 2026-09-22): the spec's **literal**
   `All · Recent · Create` — not the six-chip variant that kept the language filters beside
   *Recent*. The language chips therefore leave the visible row; the filter policy stays, and the
   kind each card belongs to moved into the card's subtitle so it is still readable.

1. **Start at 62** — Settings search + collapsible groups (spec §4). *Every other phase keeps
   its place; 59, 60, 61 and 63 follow.*
2. **Build the not-in-shots items as specified** — name-derived project marks, the hub *Recent*
   filter, preview zoom + resolution tools, and console resizing are all **approved**. The
   shots stay the reference for everything they *do* show; this answer covers the four items
   named in the question.
3. **Console + Network** — level filters, resize, **and** a Network tab fed by
   `WebViewClient.shouldInterceptRequest`. *Elements* (JS injection) was **not** chosen.
4. **Git: verify + per-file Discard** — the existing pane (stage/unstage, diff, commit+push,
   pull, conflicts, publish) is verified, not rebuilt; the one gap to close is a per-file
   discard/revert action.

**Still open — do not build:** an AI-assistant toggle (the fifth rail slot is reserved for the
owner's own plan) and a predictive-keyboard switch that would change 57.2's typing-surface
gate. A *Tablet Mode* switch is unproven too: nothing in the code today changes when it flips,
and this app does not draw dead controls — it needs a behaviour the owner names, or a refusal
in writing.
