# PART 56.1 — Projects leaves the bar

> **Status:** 🚧 IMPLEMENTED (2026-09-22) · **Effort:** S · **Cost:** `[client-only]`
> **Files:** `MainActivity.kt` (the tab list + the router's list), `CoackMarkPlan`less copy in
> `ui/guide/CoachMarkPlan.kt` (the count), `ui/terminal/SetupState.kt` (a comment)

---

## 1. Research first (the roadmap's own checklist)

**Re-read the tab list.** `MainActivity.kt:783-790`, before this phase:

```kotlin
val screens = listOf(
    Screen.FileManager,   // “Projects”
    Screen.Editor,
    Screen.Terminal,
    Screen.Modules,       // “Packages”
    Screen.Settings
)
```

with the standing comment *“five tabs with Terminal dead-center”*.

**Re-read `NavBarPolicy.kt:14-36`.** `hideNavBar(inEditor, imeVisible, keysVisible, revealed)` —
`revealed` wins; otherwise the bar hides while the IME is up (any tab) or while CodeC Keys is up
(inside the editor). Nothing in this phase touches it.

**Re-read the Phase 45 coach-mark plan.** Eleven beats, and `tabAnchorFor` maps **only**
`modules → NAV_TAB_PACKAGES` and `terminal → NAV_TAB_TERMINAL`. **There is no Projects beat** —
so no beat had to move ([56.2](PART_56_2_TOUR.md) records what that turns the part into).

**Re-open the Navigation shot** (`Screenshot_20260922_124049`): Projects is a cell in the 3 × 2
card, and Phase 55 wired that cell to `onOpenProjectsHub` — the plain Projects route, no sheet.
**That is this phase's precondition, and it is met** (`SidePanelWiringTest` fails if it ever stops
being true).

**And do not re-open the shots to delete the bar.** The owner already decided that. Five shots
show no bottom bar; not one pixel of that absence is copied.

## 2. What changed

```diff
     val screens = listOf(
-        Screen.FileManager,
         Screen.Editor,
         Screen.Terminal,
         Screen.Modules,
         Screen.Settings
     )
+    val rootRoutes = screens.map { it.route } + Screen.FileManager.route
```

and, at the back router's call site, `screens.map { it.route }` → `rootRoutes`.

### Why the router gets its own list

`BackRouter.isRoot(route, patterns)` decides *“am I at a room?”*, which is what makes a back press
at a room ask the exit question instead of silently leaving. Until this phase the bar and the
router read the same list — but they answer two different questions, and Projects is the proof:

- reached **from the panel** (the normal way now): the Projects destination sits on top of the
  editor, so `BackState.canPopRoute` is true and back pops to the editor — unchanged by any of
  this, and correct;
- reached as the **start destination** (a deep link today; the first-launch hub until Phase 58):
  nothing to pop, so the router asks `atRootDestination`. With Projects out of that list the app
  would **exit silently** from a room — a regression a user would feel. `rootRoutes` keeps it a
  room, and `BackRouterRootTest` pins it.

### Comments that counted

Three comments and one **user-facing sentence** counted five tabs and were updated in the same
commit — the sentence most of all, because it is the tour's own copy:

```diff
- body = "Five tabs, one tap away. They hide while you type — tap this handle to bring them back.",
+ body = "Four tabs, one tap away. They hide while you type — tap this handle to bring them back.",
```

(`AppContext`-free, host-tested: no test pinned that string, so the change is safe — and the
lesson, the gesture and the anchor are identical.)

## 3. What was deliberately NOT touched

| Kept | Why |
|---|---|
| `FlatBottomBar` (all of it: divider, weighted row, `navigationBarsPadding`, `NAV_HANDLE` anchor) | the owner's row is about *one option*, not the bar |
| `EditorNavRevealHandle` + “Show tabs” | the roadmap's explicit “not removed” list |
| `NavBarPolicy.hideNavBar` (Phase 32) | idem |
| `Screen.FileManager` (route object, never renamed) | `Screen.kt:70-77`: *“keeps its historical name so deep links and saved state survive”* |
| `FileManagerScreen`, the hub, the `+` sheet | only the **tab** left; the screen is now the panel's Projects cell target |
| The four remaining tabs | four, not five — and **no filler tab** was invented to keep the row full |
| `SetupLockPolicy.optionForRoute("file_manager")` | the lock still answers PROJECTS for that route, so a paused install cannot be walked around by a programmatic arrival |

## 4. The dead `when` branch (a deliberate non-change)

The tab-tap route map still carries `is Screen.FileManager -> Screen.FileManager.createRoute()`.
It is now unreachable from the bar (the bar's own list is the `forEach`), and it is **kept on
purpose**: it is the documented mapping “a tap on Projects means the hub, not the `+` sheet”, and
it costs one branch. Removing it would make a future Phase-56-style reversal silently fall into
`else -> screen.route` (which would drop the parameterised route).

## 5. Verification

- `SidePanelWiringTest`: the tab list block holds **exactly four** `Screen.*` names, contains
  Editor/Terminal/Packages/Settings, does **not** contain `Screen.FileManager`, and
  `FlatBottomBar` / `EditorNavRevealHandle` / `NavBarPolicy.hideNavBar` are all still present and
  composed; `rootRoutes` exists and the router reads it.
- `BackRouterRootTest`: `file_manager` and `file_manager?openSheet=1` are still roots.
- **Device pass required:** [Q1-Q4](DEVICE_ROUND.md).
