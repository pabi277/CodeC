# Phase 59 — Projects hub: find, filter, identify

**Owner row of record:** spec §1 — the projects hub's own *High Priority* row: an **All / Recent /
Create** row, and an **auto-generated distinct logo per project**.

**Owner's answer on the one open shape question (2026-09-22):** the row is the **spec's literal**
`All · Recent · Create`. That answer also decided the language chips' fate — they left the visible
row (the option he chose said so in as many words: *“the C/Python/Web/Git filters disappear from the
visible row”*). What that costs, and what was kept, is written down in PART_59_1 §4 rather than
quietly dropped.

| Part | Title | Status |
|---|---|---|
| 59.1 | The hub's *Recent* filter, and the spec's row | 🚧 IMPLEMENTED — [PART_59_1](PART_59_1_HUB_RECENT.md) |
| 59.2 | The project's own mark | 🚧 IMPLEMENTED — [PART_59_2](PART_59_2_PROJECT_MARKS.md) |

## What §1 asked for, and where it stands

* **All / Recent / Create** → **built** (59.1). *Recent* is a new `ProjectHubFilter.RECENT` fed by
  the **side panel's own recent-list implementation** (`RecentProjects.build`, its cap and its
  order), so the hub's chip and the panel's RECENT card cannot disagree. *Create* is an action
  chip that opens the hub's existing add sheet — the same sheet the ＋ opens, not a second path.
* **Search at the top** → **already built** (verified, not rebuilt: `searchOpen`/`searchQuery`,
  the pure `filterEntries` name match — and it still combines with *Recent*).
* **Hierarchy on tap** → **already built**: the tree is the editor's own
  (`FileTreeRepository` + `FileTreeCollapse`, the drawer and the 55 side panel's Files slot).
* **Distinct logo per project** → **built** (59.2). The card's leading square is the project's own
  name-derived mark (initials + one of the five `CodecPalette.TILE_*` seats). It replaces the
  kind-only glyph — so two C projects are told apart at a glance — and the kind it used to draw
  moved into the card's subtitle (`ProjectsHub.kindLabel`) instead of disappearing.

## Exit criteria, checked against this checkout

| Exit line (§59) | Where it stands |
|---|---|
| A name filter and a recent filter both narrow the list | ✅ `filterEntries` combines them; `ProjectsHubTest` pins `Recent` + query together |
| Two projects of the same kind carry visibly different marks | ✅ `ProjectMarkTest`: three Python projects → three marks, and `data-tools`/`dev-test` (same initials) differ by seat |
| Nothing that worked before the phase is gone | ✅ except what the owner explicitly chose to move: the language **chips** left the row, and the kind they filtered by is now on every card's subtitle. The language filter *policy* is untouched (`filters`, `filterEntries`, their tests) — the removal was a row change, not a feature removal |
| No dead control is drawn | ✅ *Create* opens a real sheet; the mark is decided by a pure policy; the kind→glyph table that lost its last reader was deleted rather than left to rot |

## Records

* [`PART_59_1_HUB_RECENT.md`](PART_59_1_HUB_RECENT.md) — the row, the one recency rule, the folder
  clock, and the accepted loss.
* [`PART_59_2_PROJECT_MARKS.md`](PART_59_2_PROJECT_MARKS.md) — the mark policy, the initials rules,
  the FNV-1a seat, and where the kind went.
* `docs/PHASE59_63_UI_PARITY_ROADMAP.md` §59 (now shipped) and §3 (both answers).

## Test log (Phase 59 — host JVM)

| Run | Result |
|---|---|
| `ProjectMarkTest` (10) + `ProjectsHubTest` (20, four new cases and one re-cut) + `ProjectMarkWiringTest` (7 pins) | **37 passed / 0 failed** |
| the broad pure-source regression set (every Android-free main source and host test this checkout can compile, including the core-file pins — `TouchTargetTest`, `IconRoleTest`, `TokenAdoptionTest`, `TypeAdoptionTest`, `SettingsAuditTest`) | **970 passed / 0 failed** |

**CI:** pending on this phase's commit.

**Next:** 60 — tabs and the coding row (close-unmodified, hide-tabs, the three sorts), then 61, 63 —
and the merge gate stands: no PR, no merge, no `main` push without the owner's command.
