# CodeC Phase 55 — Add the side panel

> **Status:** 🚧 **IMPLEMENTED on `arena/01a0c83e-codec`** (host policies + app wiring; **device pass required**). · **Cost:** `[client-only]` · **Effort:** L ·
> **Owner row (verbatim):** *“create phase wise plan remove add what needed always say to research and take the reference seriously.”*
> **Owner decisions of 2026-09-22 that shape this phase (verbatim):**
> - *“I don't think removing the full down ber is a good choice i think only removing the project option is ok.”*
> - *“Reseserve it i have plan for ai i can use that”* (the fifth rail slot).
> - Bottom row of the Navigation card: **Terminal · Packages · Guide**.
> - The unseen glyphs: *“Do whatever is good”*.
>
> Parent: [`PHASE54_58_PHONE_UI_ROADMAP.md`](../PHASE54_58_PHONE_UI_ROADMAP.md).
> Reference: [`chat-phase54/PART_54_1_SHOTS.md`](../chat-phase54/PART_54_1_SHOTS.md) (the card written from the seven shots).

```text
  55.1  Panel shell: most of the width, rail with underline, the strip stays
  55.2  Navigation card, 3 × 2, plus Recent. Projects is a cell
  55.3  Files, Search, Repository: only what the shots show
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [55.1](PART_55_1_PANEL.md) | The panel and the rail | M | 🚧 IMPLEMENTED |
| [55.2](PART_55_2_NAVIGATION.md) | Navigation and Recent | M | 🚧 IMPLEMENTED |
| [55.3](PART_55_3_FILES_SEARCH_REPO.md) | Files, Search, Repository | M | 🚧 IMPLEMENTED |

Device round: [`DEVICE_ROUND.md`](DEVICE_ROUND.md) (P1-P10, written, **not run**).

## What this phase changed, in one screen

| File | Change |
|---|---|
| `ui/editor/SidePanelPlan.kt` | **new, pure** — the rail (5 slots, order, which are wired), the 3 × 2 card, the Recent policy (sort, age wording, the real External rule), the open-gesture threshold, the refused labels |
| `ui/editor/ProjectSearch.kt` | **new, pure** — the Search slot's engine: in-project walk, regex/Aa/whole-word, `MAX_HITS`/`MAX_FILE_BYTES` caps, binary skip, `ProjectPathGuard` |
| `ui/components/EditorSidePanel.kt` | **new, Compose** — the panel: rail with the selected underline, the card, Recent, Files slot (the tree rides in it), Search, Repository |
| `ui/screens/EditorScreen.kt` | the ☰ drawer's `drawerContent` is now `EditorSidePanel`; the tree is the panel's Files slot; panel/search state; the guide-beat → Files rule; two new callbacks (`onOpenProjectsHub`, `onOpenPackages`) |
| `ui/editor/EditorChromeState.kt` | **+`guideBeat`** — the guide host publishes the beat that is due, so the editor can ask the *pure* plan whether it is an in-drawer beat |
| `ui/guide/CoachMarks.kt` | publishes that beat (`LaunchedEffect(step?.id)`), clears it on dispose |
| `ui/guide/CoachMarkPlan.kt` | `step(id)` now takes `String?` — “no beat due” is a null, not a second null check at every caller |
| `MainActivity.kt` | the two new doors (`onOpenProjectsHub` = the plain Projects route; `onOpenPackages` = the Packages tab) |
| `res/values/strings.xml` | 12 strings: the section sentences, the placeholder, the repository sentence + button |
| `app/src/test/.../SidePanelPlanTest.kt` | **new** — 13 host cases pinning the rail, the card, the recent rules |
| `app/src/test/.../ProjectSearchTest.kt` | **new** — 15 host cases pinning the engine |
| `app/src/test/.../SidePanelWiringTest.kt` | **new** — 8 source pins: the panel is composed, the tree rides in it, Projects is a cell, the guide rule exists, **the bottom bar survives** |

## The exit conditions, checked against this build

| Roadmap exit | Where it is true |
|---|---|
| “☰ opens a panel that matches the shots' shell” | `EditorScreen.kt` `drawerContent = { EditorSidePanel(...) }`; the rail, the underline, the card and the strip are `EditorSidePanel.kt` |
| “Play works with the panel open” | **⚠️ PARTLY.** The strip stays *visible* (85 %) and is not covered by the panel, but Material3's modal scrim owns a tap on it: the first tap closes the panel, the second hits play. Written down as deviation 1 in [PART_55_1](PART_55_1_PANEL.md) §4, and put to the owner |
| “A tap on Projects in the card opens the Projects screen” | `NavCell.PROJECTS -> { close; onOpenProjectsHub() }` → `MainActivity` `Screen.FileManager.createRoute()` (pinned by `SidePanelWiringTest`) |
| “Files shows the open project's name as the root and a green triangle on the launch-default HTML file” | the tree is the **unchanged** `EditorProjectDrawer` (root = project name, launch-default ▶ = Phase 16's) — reuse, not a rewrite |
| “Search empty is empty” | `SearchSlot`: query blank → **nothing** under the RESULTS header |
| “Repository empty is one sentence and one button” | `RepositorySlot`, the shot's sentence + the blue button |
| “The bottom bar still has five tabs” | untouched: `FlatBottomBar` composed, `screens` still lists `FileManager` (pinned) |
| “A host test pins the rail order and that Projects is in the card” | `SidePanelPlanTest` + `SidePanelWiringTest` |

## Verification

- **Local pre-validation (rule.md §9):** Temurin 25.0.2 + `kotlinc` 2.4.20 in-sandbox. `SidePanelPlan.kt` + `ProjectSearch.kt` compile clean; the three test classes run through a reflection harness against a JUnit4 shim → **36/36 PASS**.
- **The Compose wiring cannot compile in the sandbox** (no Android SDK / Compose artifacts). Syntax was checked (parser + a bracket/string balancer over every touched file: **balanced**). **CI `Build APK` is the executor of record.**
- **Device pass required: yes** — [P1-P10](DEVICE_ROUND.md).
