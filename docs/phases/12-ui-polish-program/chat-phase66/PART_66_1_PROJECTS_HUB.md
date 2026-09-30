# Part 66.1 — the Projects hub: demo seeded once, truthful dialogs, one vocabulary

**Brief:** [`docs/phases/12-ui-polish-program/PHASE_66_1_PROJECTS.md`](../PHASE_66_1_PROJECTS.md)
· **Phase record:** [README](README.md) · **Branch:** `arena/01a0e220-codec`
(base `main` @ `249877e`, the Phase 64 merge).

**Owner answers this part implements (2026-09-27, verbatim option labels):**
**"Keep the current cards"** · **"Yes — stay deleted"** ·
**"(a) demo + (b) dialogs + (c) wording — all in one part"** ·
**"Keep today: hub file tree"** (*superseded 2026-09-28* — a card's tap and Create
both land in the editor now; the tree is ⋮ → Browse files; see
[`../chat-phase70/README.md`](../chat-phase70/README.md) §Round 5).

Look unchanged: same cards, same ＋ and ⋮ sheets, same chips, same dialogs'
shape. Everything below is behaviour and words.

## Defects reproduced by reading the source, and what changed

| # | Before (file:anchor on `249877e`) | After |
|---|---|---|
| 1 | `DemoProjects.ensure` re-created `demo_flask` whenever the directory was missing (`ProjectManager.listProjects` calls it on every list read) — *Delete* on the card looked broken and the hub's empty state was unreachable. | Seeded **once per install**: marker `.demo-flask-seeded-v1` in the projects root, written only after a complete seed. Deleted stays deleted. |
| 2 | New Project: Create with an invalid/taken name did nothing visible — the error went to the snackbar **behind** the open dialog (`FileManagerScreen.kt` ≈:688, `runOperation` ≈:964). | The field says *taken* / *invalid* before the tap (`isError` + `supportingText`), Create is enabled only for a usable name, and a failure that still happens is printed **inside** the dialog. |
| 3 | Rename Project: same class as 2 (≈:1219). | Same treatment; the project's own name is not a collision (hint *"That is the project's current name"*; Rename then just closes). |
| 4 | Import ZIP: same class as 2 (≈:868); the final name silently dropped everything after the last `.` (`uniqueProjectName` ≈:927) and de-duplicated against *valid projects only*. | The dialog says *"…will be imported as `snake_2`"* from the same policy the ViewModel uses; no `.`-stripping; de-dup against every directory in the projects root (the clone path's rule). |
| 5 | Search: tapping 🔍 showed a field and no keyboard (≈:355). | Field takes focus; IME action is *Search* (hides the keyboard; the list already filters as you type). |
| 6 | Wizard type rows were `clickable` rows with ●/○ text — no radio semantics (≈:709). | `selectableGroup()` + `selectable(role = Role.RadioButton)`; the glyphs are unchanged. |
| 7 | Tree header printed the raw config id (`python-flask`, `auto`) under the name (≈:381). | Prints the kind's word the card already uses (`hubKindLabel`: hub entry kind → wizard label → "Project"). |
| 8 | Tree root line was `name  >  ` — a separator with nothing after it (≈:1851); "No entry configured" hardcoded. | Root line is the project's name; entry line is a resource. |
| 9 | Age: cards said "18 min ago", the editor's side panel "18 minutes ago" for the same project. | `ProjectsHub.relativeAge` delegates to `RecentProjects.ageLabel` — one vocabulary. |
| 10 | Search miss and filter miss shared "No projects match this filter". | A search miss says *"No projects match your search"*. |
| 11 | Hardcoded sentences in the hub and its ViewModel ("Delete project X and all its files?", "In X", the empty-state sentence, "Import failed: …", "unknown error", …). | String resources (`delete_project_confirm`, `new_item_in`, `no_projects_hint`, `import_failed`, `unknown_error`, …); three strings with zero readers removed (`clone_from_github`, `clone_fetch_branches`, `hub_rename_project`). |
| 12 | Delete confirms' action button in the default text-button colour; the amber badge a private literal `0xFFE6B33C`. | `destructiveTextButtonColors()` (theme error role); `HubBadgeYellow = Color(CodecPalette.WARNING)`. |
| 13 | **Owner device report after 66.1:** Back on Projects showed the exit prompt instead of returning to the editor (`MainActivity.kt` `onOpenProjectsHub` / `onOpenProjects` popped the editor tab-style; `popUpTo(start)`). | Both doors push the hub over the editor like the Settings door; Back returns to the editor, the prompt is the next Back. Router unchanged. See [README § Follow-up](README.md#follow-up--back-from-projects-returns-to-the-editor-owner-device-report). |

## Files

**New**
- `app/src/main/java/com/codeci/ide/ui/projects/ProjectNameCheck.kt` — pure:
  `ProjectNameProblem { EMPTY, INVALID, TAKEN }`, `ProjectNameVerdict(safeName,
  problem)` with `ok` / `showsError`, `check(raw, existing, current, allowTaken)`,
  `importedNameFor(raw, existing)`.
- `app/src/test/java/com/codeci/ide/ProjectNameCheckTest.kt` — 9 cases, each one
  the manager really refuses or accepts (case is *not* folded: the projects
  root is a case-sensitive directory and `createProject` checks `exists()`).
- `app/src/test/java/com/codeci/ide/HubDialogWiringTest.kt` — 12 source-scan
  pins: verdict + `isError` + `supportingText` + `enabled = verdict.ok` in all
  three dialogs; `onFailed` passed and rendered; ViewModel signatures and
  `runOperation`'s `onFailure`; focus + IME in the name fields and search (and
  the `requestFocus` sits *after* its `focusRequester`); radio semantics with
  the glyphs kept and no `RadioButton(` widget; header helper; the resource
  list and the three dead strings; search-vs-filter miss; two destructive
  confirms; the palette badge; the demo seed gate; the after-Create landing.
- `docs/phases/12-ui-polish-program/chat-phase66/README.md`, this file.

**Changed**
- `ui/projects/DemoProjects.kt` — seed-once law (see README § Design decisions).
- `ui/projects/ProjectsHub.kt` — `relativeAge` → `RecentProjects.ageLabel`
  (`""` for a zero timestamp, as before); `MINUTE_MILLIS` removed.
- `ui/screens/FileManagerScreen.kt` — search focus/IME; header kind label; the
  three dialogs; delete confirms; no-match copy; breadcrumb; entry line;
  empty-state sentence; badge colour; three private helpers at the end of the
  screen function (`projectNameSupportingText`, `hubKindLabel`,
  `destructiveTextButtonColors`).
- `ui/viewmodels/FileManagerViewModel.kt` — `createProject` / `renameProject` /
  `importZip` gain `onFailed: (String) -> Unit = {}`; `runOperation` gains
  `onFailure`; `projectDirectoryNames(manager)` shared by clone and ZIP import;
  hub-flow messages read resources; the old private `uniqueProjectName` is gone.
- `res/values/strings.xml` — 20 strings added after `rename_project`
  (`project_name_invalid_hint`, `project_name_will_import_as`,
  `project_name_unchanged`, `project_entry_none`, `new_item_in`,
  `delete_item_confirm`, `delete_project_confirm`, `delete_project_failed`,
  `hub_no_search_match`, `import_file_needs_project`, `import_failed`,
  `zip_import_failed`, `export_done`, `export_failed`, `backup_failed`,
  `backup_imported`, `backup_import_failed`, `unknown_error`);
  `no_projects_hint` re-purposed for the empty state's sentence; three dead
  strings removed.
- `test/…/DemoProjectSeedTest.kt` — rewritten for the once-per-install law
  (seeds on first call and writes the marker; a deleted demo is **not**
  re-created; a marker with no demo seeds nothing; an existing demo is found,
  never overwritten, and gets the marker; a plain file of the same name still
  blocks; the scaffold is the wizard's).
- `test/…/ProjectsHubTest.kt` — age expectations in the panel's words, plus
  one agreement test walking minute-by-minute to 24 h and day-by-day to 730 d.
- `docs/phases/12-ui-polish-program/PHASE_66_1_PROJECTS.md` (status + verbatim answers),
  `docs/journal` (§5 row), `docs/getting-started`.

## Things worth knowing before the next change here

- **Where the focus request lives matters.** Each `LaunchedEffect {
  nameFocus.requestFocus() }` is placed *inside the dialog's `text` content,
  after the field*. `AlertDialog` composes its content in its own window; an
  effect declared beside the `AlertDialog(...)` call would run before the
  field's node exists and `FocusRequester.requestFocus()` throws in Compose 1.7
  when nothing is attached. The same holds for the search field (the effect is
  inside `if (searchOpen)`).
- **`projectNameSupportingText` is not `@Composable`.** It is a pure mapper
  from the verdict to an optional composable lambda, so `supportingText = …`
  can be an ordinary `when` expression.
- **Two name sets, one small gap.** The dialogs compute the verdict from the
  *project list* (`projects.map { it.name }`); the ViewModel de-duplicates ZIP
  imports against *every directory* in the projects root. A non-project
  directory with the typed name (rare — a broken import) would make the
  dialog promise `name` and the import produce `name_2`. Accepted for 66.1;
  the alternative (exposing directory names through the ViewModel) is a new
  state flow for a corner case.
- **Cancel during a running import** still closes the dialog (as before); the
  import continues and reports through the snackbar. Only the outside-tap is
  ignored while busy, so a running import cannot lose its error surface by
  accident.
- **Unchanged on purpose:** the *Export all projects* summary in the ViewModel
  (a composed diagnostic with rename/skip lists, Settings flow); the clone
  dialog; the chips' height; `Auto (detect)`; *Recent*'s definition.

## Validation

**Local (prevalidation only — temporary JUnit shim over kotlinc 2.2.10; not an
Android or Gradle run; Compose sources cannot be compiled in the sandbox):**

- Pure production set compiled: `ProjectNameCheck`, `DemoProjects`,
  `ProjectsHub`, `SidePanelPlan`, `ProjectScaffold`, `ProjectConfig`,
  `HubListPolicy`, `ProjectTypes`, `ProjectMark`, `SnakeSample`,
  `ProjectRunDetector`, `GitManager` and their pure dependencies (one local
  stub for the `LanguageType` enum, which lives in a Compose file).
- Focused set: **192 passed / 0 failed** — `DemoProjectSeedTest`,
  `ProjectNameCheckTest`, `ProjectsHubTest`, `HubDialogWiringTest`,
  `HubSurfaceTest`, `HubListPolicyTest`, `ProjectMarkWiringTest`,
  `TouchTargetTest`, `TokenAdoptionTest`, `TypeAdoptionTest`, `IconRoleTest`,
  `SkeletonCoverageTest`, `SidePanelPlanTest`, `ProjectScaffoldTest`,
  `FolderImportRemovedTest`, `DrawerWiringTest`, `BackHandlerWiringTest`,
  `MotionWiringTest`, `PillNoticeWiringTest`, `PreviewChromeWiringTest`,
  `ResumeWiringTest`, `SettingsAuditTest`, `SetupGateWiringTest`,
  `SidePanelWiringTest`, `SkeletonStabilityTest`.
- Every pure source-scan class in the module (39, including
  `ComposableAnnotationTest`): **202 passed / 0 failed**.
- Fixed from that run before commit: `ProjectsHubTest`'s subtitle case still
  expected "1 min ago"; one `"unknown error"` literal (`default_run_page_failed`).

**CI**

- Green on the first round for `a96fb25` — see [CI](#ci) below.

**Device.** The owner verified the delivered build and authorised the merge
(2026-09-27, verbatim: *"Device verified merge it to the main"*). That message
is the device evidence; it was not an itemised round, so the following are
**not individually confirmed**: TalkBack reading the type rows as radio
buttons; the supporting text's layout on a narrow phone; a real ZIP import
landing on the promised `_2` name. Listed so nobody mistakes the merge for a
row-by-row device pass.

## CI

**Follow-up head (Back from Projects) — GREEN, first round** — code
`3762b7b`, [run 36317041278](https://github.com/pabi277/CodeC/actions/runs/36317041278):
host unit/screenshot tests, debug and release APKs, APK-set checks passed.

**66.1 head — GREEN, first round** — code `a96fb25`,
[run 36311728346](https://github.com/pabi277/CodeC/actions/runs/36311728346)
(*Build APK*): icon and release-notes checks, **host unit and screenshot
tests**, debug APK, release APK (measure-only and signed), release APK-set
check and artifact uploads all passed; zero failed steps. This is the compile
and test evidence for `FileManagerScreen.kt` and `FileManagerViewModel.kt`,
which the sandbox cannot build.

APK sizes on the same `versionName` 1.3.17 (against the Phase 64 build
`4de912a`): debug **25,965,508 B** (+27,096 B), release **6,777,708 B**
(+14,972 B). No new dependency.
