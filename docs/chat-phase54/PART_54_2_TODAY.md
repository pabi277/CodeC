# PART 54.2 — What CodeC has today: every control this series removes or moves

> **Status:** ✅ DONE (2026-09-22, docs only). · **Effort:** S ·
> **Every line below is a read on THIS checkout** (`arena/01a0c83e-codec`,
> branched from `main` @ `3f9fd6b`, 2026-09-22), not a remembered line from a
> plan doc. Line numbers move; the *content* quoted beside them is what a later
> phase must re-confirm.

---

## 1. The bottom bar — **kept**; only the Projects option leaves (56)

| Thing | Today, exactly | Phase |
|---|---|---|
| The five tabs | `MainActivity.kt:783-790` — `val screens = listOf(Screen.FileManager, Screen.Editor, Screen.Terminal, Screen.Modules, Screen.Settings)`. The comment above it is the standing law: *“2026-08-31 bar: five tabs with Terminal dead-center — Projects · Editor · Terminal · Packages · Settings.”* | 56.1 removes `Screen.FileManager` from this list **only** |
| The bar itself | `MainActivity.kt:1167` `!hideNav -> FlatBottomBar(screens = screens, …)` inside `bottomBar = { … }` of the shell `Scaffold` (`:1158`) | **stays, every phase** |
| The bar's definition | `MainActivity.kt:1677` `private fun FlatBottomBar(...)` — one `Column`: a 1 dp divider, then a weighted `Row` of tabs, `navigationBarsPadding()`, and a `GuideAnchor.modifier(GuideAnchors.NAV_HANDLE)` on the whole bar (`:1700-1707`) | **stays** |
| The tab anchor rule | `ui/guide/CoachMarkPlan.kt:475-484` `tabAnchorFor(route)` → Packages / Terminal only | unchanged by 56 (see §5) |
| The reveal handle | `MainActivity.kt:1216` `inEditor && !isImeVisible -> EditorNavRevealHandle(onReveal = …)`; definition `:1804-1850`, the string **“Show tabs”** at `:1846` | **stays** |
| Hide-while-typing | `ui/editor/NavBarPolicy.kt:14-36` — `hideNavBar(inEditor, imeVisible, keysVisible, revealed)`: reveal wins, else IME up (any tab) or CodeC Keys up (editor) hides the bar; `revealOnSwipe` = `-32 dp`; `REVEAL_SWIPE_DP = 32f` | **stays** (Phase 32) |
| The tab route map | `MainActivity.kt:1189-1192` — a tab tap on `Screen.FileManager` navigates `Screen.FileManager.createRoute()` (hub, no sheet). `:1361` pushes `createRoute(openAddSheet = true)` (the *“+”* path). `:1399` is the `composable(route = Screen.FileManager.route)` itself | 56 keeps the route and the composable; only the tab entry goes |
| Route object | `ui/navigation/Screen.kt:70-77` `object FileManager` — route name is historical on purpose (*“keeps its historical name so deep links and saved state survive”*) | **not renamed, ever** |
| The Projects screen | `ui/screens/FileManagerScreen.kt:155` `fun FileManagerScreen(...)`, its own `Scaffold`/`TopAppBar` at `:351/:355`, the designed empty hub at `:1984-2006` | **stays** — this is where 55's Projects cell lands |

**The one thing 56 must not do:** delete `FlatBottomBar`, the handle, “Show tabs”,
`NavBarPolicy`, or any of the other four tabs. Five shots show no bottom bar at
all; the owner rejected copying that (see 54.3 §1).

## 2. The editor's ☰ and the file drawer — replaced in 55

| Thing | Today | Phase |
|---|---|---|
| ☰ in the top bar | `ui/screens/EditorScreen.kt:1322-1344` — `navigationIcon = { IconButton(modifier = GuideAnchor.modifier(GuideAnchors.EDITOR_DRAWER, onClick = onDrawerTap), onClick = onDrawerTap) { Icon(Icons.Default.Menu, …) } }` | 55 keeps the ☰ where it is; what it opens changes |
| What it opens | `onDrawerTap` at `EditorScreen.kt:520`: `if (drawerState.currentValue == DrawerValue.Open) drawerState.close() else drawerState.open()` | 55 — same tap, the side panel |
| The drawer host | `EditorScreen.kt:1123` `ModalNavigationDrawer(drawerState = …, gesturesEnabled = activeTabPath == null && currentFileName.isEmpty() && !editorChromeLocked, drawerContent = { EditorProjectDrawer(…) })` (`:1135`) | 55.3 keeps the tree, replaces the full-screen files-only drawer with the panel |
| The drawer's own content | `ui/components/EditorProjectDrawer.kt:77` `fun EditorProjectDrawer(...)`; rows `DrawerRow` `:488`, per-row menu `DrawerEntryMenu` `:613`, tool actions `:674`, footer `:700` | 55.3 — *“the tree itself stays”* |
| Drawer policy | `EditorScreen.kt:516` `DrawerPolicy.shouldClose(reason, …)`, `:578` `editorDrawerOpen`, `:832`/`:856` the close-on-navigate + lock wiring | 55 must not break the chrome lock (Phase 45 round 4) |
| Guide anchor | `GuideAnchors.EDITOR_DRAWER` (`CoachMarkPlan.kt:64`) — the tour's **first beat** spotlights this ☰ (`:193-206`) | 55: the ☰ keeps its anchor, the beat's copy may need to say “panel”, not “file tree” |

## 3. The editor's top row and rows — 57

| Thing | Today | Phase |
|---|---|---|
| The top bar | `EditorScreen.kt:1276` `TopAppBar(title = { … })` — title is a `Crossfade` between the filename text and `EditorTabBar` (`:1293`) | 57.1 — shots show **filename in the top row and the tab strip on its own row below**, so this crossfade splits |
| The filename | `:1285-1293` — `currentFileName.substringAfterLast('/') + if (isDirty) " *" else ""`, clickable → rename dialog | 57.1 keeps the tap-to-rename, restyles |
| **The word RUN** | `EditorScreen.kt:1725-1727` — `text = if (RunButtonStyle.showsRunning(runButtonState)) stringResource(R.string.run_running) else stringResource(R.string.run)`, `labelLarge` + `Bold`; the whole affordance block is `:1665-1740`, the green ▶ at `:1710-1720` | 57.1 **removes the label, keeps the ▶**. The run *action* does not move |
| **The ⋮** | `EditorScreen.kt:1352-1355` — `IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, …) }`; the menu itself `:1356-1450`-ish holds **undo, redo, keys-row toggle, save** (the Phase 16 relocation: *“the former second toolbar row … now lives here”*) | 57.1 removes the icon from the top row; those four actions need a home the shots do **not** show → **ask** (54.3 §4) |
| Search in the top row | `EditorScreen.kt:1346-1351` `Icon(Icons.Default.Search)` toggling `viewModel.showFind()` | stays (the shots show a magnifier there) |
| Tab strip | `ui/components/EditorTabBar.kt` — tabs with dirty dot, close, context menu (close / close others / close all / copy path), `EditorScreen.kt:1293-1320` call site; helper `EditorTabBar` also draws the `▤↓`-look? **No** — see next row | 57.1 gives it its own row + the orange active edge |
| The `▤↓` icon right of the tab row | **Does not exist.** `grep` for the glyph in `EditorTabBar.kt` finds no such control | 57.1 draws it **only** if tapping it does no guessed action → 54.3 §4 |
| Status line | `ui/components/EditorStatusBar.kt:41` `fun EditorStatusBar(line, column, selectionLength, tabSize, errorCount, warningCount, onDiagnosticsClick, languageLabel, lineEnding, onLineEndingClick, pathLabel, …)`; called at `EditorScreen.kt:2063`. Already prints `Ln n, Col n` left and `Sp: n  LANG  LF  UTF-8` right — **the shape the shots show already exists** | 57.2 — hide it while the IME is up |
| Touch row | `ui/components/EditorKeysRow.kt:58` `fun EditorKeysRow(...)`, `:104` `fun RunKeysRow(...)`; key model `ui/editor/EditorKeySet.kt` (`GENERAL` list at `:98-146`: TAB, `()`, `{}`, `[]`, `<>`, `""`, `''`, `;`, `/`, `=`, `←→↑↓` plus per-language tails) | 57.2 — the shot's eight glyphs vs. today's set is a **mapping decision**; the shot's 7th glyph is unnamed |
| Predictive row | `ui/components/SuggestionStrip.kt`, dispatched at `EditorScreen.kt:2555-2574` (`StripContext.Run` → `RunKeysRow`, `StripContext.Suggestions` → `SuggestionStrip`, else `EditorKeysRow`) | 57.2 — show only while the IME is up |
| The blue caret drop | drawn in sora — `ui/editor/sora/SoraEditorHost.kt` | 57.2 **reads sora first**; never stack a second caret |

## 4. The permanent setup strip — 58

| Thing | Today | Phase |
|---|---|---|
| The strip | `ui/components/SetupBar.kt:60` `fun SetupBar(...)` — the Phase 44.1 surface, visible from **every** tab | 58.2 removes it as a permanent strip |
| Where it is placed | `MainActivity.kt:1252-1280` — `setupBarText = setupNote ?: SetupGatePolicy.barText(setupProgress, setupFacts)`; `dismissedSetupBar` state; `goToSetup` (`:1255`) navigates to Terminal; `dismissSetupBar` (`:1262`); `if (setupBarText != null && setupBarText != dismissedSetupBar) SetupBar(...)` at `:1272` — **above the `NavHost`**, so it sits on every tab | 58.2 |
| The policy behind it | `ui/terminal/SetupState.kt:159` `object SetupGatePolicy` — `LINE_PREFIX`, `barText(...)`, `barDismissAllowed(...)`, and the stage vocabulary | 58.2 re-points it: **one warning** when a run needs the download |
| The installer | `ui/terminal/UserlandInstaller.kt` (621 lines) — `UserlandStatus` (`:16-21`: `AlreadyInstalled` / `SkippedOffline` / `SkippedNoRelease` / `Installed` / `Failed`), `UserlandManifest` (`:31`) | 58.2 — the install itself is already silent-capable; the *strip* is what is loud |
| The tests that pin it | `app/src/test/java/com/codeci/ide/SetupGatePolicyTest.kt` (867 lines), `SetupGateWiringTest.kt` (565), `UserlandInstallerTest.kt` (585) | 58.2 must move these with the behaviour, not delete them |

## 5. The tour (Phase 45) — the finding that de-scopes 56.2

`ui/guide/CoachMarkPlan.kt`:

- 11 anchor ids (`:61-112`): `editor_drawer`, `drawer_project`, `drawer_demo_pick`,
  `drawer_file`, `editor_run`, `preview_close`, `nav_handle`, `nav_tab_packages`,
  `packages_card`, `nav_tab_terminal`, `terminal_chip`.
- 11 steps in that order (`:193-300`).
- `tabAnchorFor(route)` (`:475-484`) maps **only** `modules → NAV_TAB_PACKAGES`
  and `terminal → NAV_TAB_TERMINAL`.

**There is no Projects-tab beat.** So the roadmap's Phase 56.2 (“move the beat that
spotlights the Projects tab”) describes work that does not exist. 56.2 becomes a
**verification** part:

1. re-run the whole tour after 56.1 and confirm every beat still finds its
   anchor (the bar still exists; only one option left it);
2. confirm in the wiring test that no `GuideAnchors` id references the Projects
   tab;
3. keep `nav_handle`, `nav_tab_packages`, `packages_card`, `nav_tab_terminal`,
   `terminal_chip` untouched — those tabs stay.

## 6. First open — 58.1 (two facts, both real)

| Thing | Today | Phase |
|---|---|---|
| First launch, no flag yet | `MainActivity.kt:831-853` — `if (firstLaunchComplete == false) { WelcomeScreen(onStarterChosen = …) ; return }`. `ui/screens/WelcomeScreen.kt:55` + `ui/projects/WelcomeStarters.kt:32-60`: **three tiles — C, Python, HTML** (plus their project/entry-file pairs) | 58.1 — the shots and the owner's row want the editor on a **snake** sample instead |
| Later launches, nothing to resume | `MainActivity.kt:932-938` — `startDestination = remember(launchState) { when (resumeOffer) { CONTINUE_IN_PLACE -> Editor(fileName, projectName) ?: Screen.FileManager.route; else -> Screen.FileManager.route } }` | 58.1 — the fallback must stop being the hub |
| The launch state it resumes from | `ui/projects/EditorLaunchState.kt` (save on run/open) | 58.1 reuses this to open snake |
| The "dumps you on a locked terminal" risk | `WelcomeStarters` Python tile → a language that needs the userland download; `SetupLockPolicy`/`SetupVerdict` (`ui/terminal/SetupState.kt`) refuse paused tabs | 58.1/58.2 — the snake sample is **HTML**, so it runs with zero setup (C also runs with zero setup) |

## 7. The preview chrome — 58.3

| Thing | Today | Phase |
|---|---|---|
| The preview screen | `ui/screens/WebPreviewScreen.kt:75` `fun WebPreviewScreen(...)`; `TopAppBar` at `:170` with Back (`:177-193`, anchor `PREVIEW_CLOSE`) + Refresh (`:196-203`) | 58.3 — the research decides which of the two fixes the owner's row means |
| The address row | `WebPreviewScreen.kt:197-228` — a `Row` with the live badge and the URL | 58.3 candidate (i) |
| The share panel | `:234` `ServerSharePanel(endpoints, lanShared, onToggleLan, onOpenUrl, servers, onStopAll, showSwitch)` — `ui/components/ServerSharePanel.kt` (367 lines) | 58.3 candidate (i) |
| The page's own upper links | *The page's* — `Content` / `Home` / `More` are buttons inside the **user's HTML** in the shot (`index.html` in the editor shots, lines 64-76) — not CodeC chrome | 58.3 candidate (ii). **The shots do not show the Play/preview screen at all** → 54.3 §4 |

## 8. Controls the shots want that do not exist yet (nothing to remove)

- an **outline** triangle rail glyph (CodeC has `Icons.Default.PlayArrow` and
  `ui/components/SpckIcons.kt` (443 lines) — 55 checks there first, adds nothing
  new if a match exists);
- a folder, a magnifier-with-`<>`, a branch, a person **as rail icons**;
- the **3 × 2 navigation card** with a raised selected tile;
- the **Recent** stacked list with a relative-time line and an External badge;
- Files' **Locate / new file / new folder** header controls
  (`FileManagerScreen` has its own header controls today — 55.3 maps them);
- the predictive row's `▤↓` right-hand icon and the `⌘`-like touch-row glyph
  (**names unknown** — 54.3 §4);
- the **snake sample** — `grep -ril snake` over the repo finds it only in docs
  and in `cpp.tmLanguage.json`/`SpckIcons.kt` as a *word* (“snake_case”); there is
  **no sample app**. 58.1 writes one, original.
