# PART 55.2 — Navigation and Recent

> **Status:** 🚧 IMPLEMENTED (2026-09-22) · **Effort:** M · **Cost:** `[client-only]`
> **Files:** `ui/editor/SidePanelPlan.kt` (card + `RecentProjects`), `ui/components/EditorSidePanel.kt`
> (the card, the rows), `ui/screens/EditorScreen.kt` + `MainActivity.kt` (the doors)

---

## 1. The card — 3 × 2, and the owner's bottom row

The shot (`Screenshot_20260922_124049`) shows **one card, three columns by two rows**, with the
**Editor** cell raised and highlit. Its cells are `Projects · Editor · Settings` on top and
`Discover · My Labs · Change Log` below. The bottom row is SPCK's own discovery surface; the
roadmap refuses it, and the owner answered the roadmap's question with
**“Terminal · Packages · Guide”** (2026-09-22).

```kotlin
val CARD = listOf(
    listOf(NavCell.PROJECTS, NavCell.EDITOR, NavCell.SETTINGS),   // the shot's top row
    listOf(NavCell.TERMINAL, NavCell.PACKAGES, NavCell.GUIDE)     // the owner's bottom row
)
val SELECTED_CELL = NavCell.EDITOR
```

`REFUSED_LABELS = [Discover, My Labs, Change Log, Upgrade, Account, Credits]` is *data*, so
`SidePanelPlanTest` can fail loudly if a label like that ever enters the card — a test, not a
comment.

## 2. Every cell is a real door (no dead tile)

| Cell | Action | Where |
|---|---|---|
| Projects | the Projects screen, **no sheet** — `Screen.FileManager.createRoute()` | new `onOpenProjectsHub` (`MainActivity`) |
| Editor | this screen is the editor: the cell puts the panel away | `closeDrawer(DrawerCloseReason.CLOSE_BUTTON)` |
| Settings | `onOpenSettings()` (Phase 16's existing door) | unchanged |
| Terminal | `onOpenInTerminal(null)` — the terminal tab, no command | unchanged |
| Packages | the Packages tab | new `onOpenPackages` (`MainActivity`) |
| Guide | `onOpenGuide()` — the Phase 45 tour again | unchanged |

**Projects is the load-bearing one.** The roadmap's Phase 56 does not start until a tap on
Projects here opens that screen: `SidePanelWiringTest` fails if `NavCell.PROJECTS` stops calling
`onOpenProjectsHub()`, or if `MainActivity` stops routing it to the plain Projects route.

The two new doors navigate the way a **bottom-bar tap** does (save state, single top, restore
state) because a cell is a room, not an instruction with a sheet — unlike `onOpenProjects`
(the drawer's `+ New project…` row), which keeps opening the hub's `+` sheet.

## 3. Recent — and the honest External badge

The shot lists eight rows of **name · relative age · `External`** with one row highlighted, and
every row carries the badge. CodeC's rule is stricter, per the roadmap: *“External only when the
project really came from outside.”*

```kotlin
RecentProjects.build(entries, nowMillis, privateRoot, externalRoot) =
    sort by lastOpenedMillis desc, name asc → take(8) → Row(name, ageLabel, external)
```

- **Age** is written the shot's way: `just now`, `1 minute ago`, `18 minutes ago`, `1 hour ago`,
  `yesterday`, `3 days ago`, `1 week ago`, `2 weeks ago`, `1 month ago`, `1 year ago`.
- **An unknown time sorts last**, never first: “first” is what the shot's highlight means.
- **External** is answered from the real filesystem: the project's root is under the app's
  **external** files dir (`getExternalFilesDir(null)/CodeC/projects` — the `ProjectTransfer`
  import/clone home) and not under the private projects root. Unknown → not external. A badge
  that is always on is decoration, and the roadmap refuses it.
- The rows are read **only while the Navigation slot is on screen and the drawer is open**
  (`remember(sidePanel, panelOpen, currentProject)`), so the IO never rides a recomposition.

**Tapping a recent row** switches the editor's project context — exactly what the drawer's own
PROJECTS list does (`viewModel.switchContext` + `onProjectSelected`) — and closes the panel
because a recent row means “take me there”, not “drop the list down”. Deviation from the drawer's
stay-open rule, written down here because it is a deliberate difference (55.2, decision).

## 4. Verification

- `SidePanelPlanTest`: the card is 3 × 2 and rectangular; the top row is the shot's; the bottom
  row is the owner's; no refused label; `Projects` resolves; the selected cell is Editor; recent
  ordering, the cap, the unknown-time rule, the External rule (six cases) and nine age labels.
- `SidePanelWiringTest`: both new doors exist in `MainActivity`; the Projects cell calls the hub door.
- **Device pass required:** [P5-P7](DEVICE_ROUND.md).
