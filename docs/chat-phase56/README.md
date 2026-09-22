# CodeC Phase 56 — Remove only the Projects option

> **Status:** 🚧 **IMPLEMENTED on `arena/01a0c83e-codec`** · **Cost:** `[client-only]` · **Effort:** S ·
> **Owner row (verbatim):** *“I don't think removing the full down ber is a good choice i think only removing the project option is ok.”*
>
> Parent: [`PHASE54_58_PHONE_UI_ROADMAP.md`](../PHASE54_58_PHONE_UI_ROADMAP.md).
> Depends on **55** (Projects must open from the side panel first — it does; see
> [`chat-phase55/README.md`](../chat-phase55/README.md)).

```text
  56.1  Take Projects off the bottom bar. Leave the other four tabs
  56.2  The tour still teaches where projects are (a verification part)
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [56.1](PART_56_1_PROJECTS_LEAVES.md) | Projects leaves the bar | S | 🚧 IMPLEMENTED |
| [56.2](PART_56_2_TOUR.md) | The tour still teaches where projects are | S | 🚧 IMPLEMENTED (the roadmap's premise corrected) |

Device round: [`DEVICE_ROUND.md`](DEVICE_ROUND.md) (Q1-Q6, written, **not run**).

## The change, in one diff

```diff
-    val screens = listOf(
-        Screen.FileManager,     // ← “Projects”, and the ONLY option this phase removes
-        Screen.Editor,
-        Screen.Terminal,
-        Screen.Modules,
-        Screen.Settings
-    )
+    val screens = listOf(
+        Screen.Editor,
+        Screen.Terminal,
+        Screen.Modules,
+        Screen.Settings
+    )
+    /** …the BACK ROUTER's list, which is not the bar's any more. */
+    val rootRoutes = screens.map { it.route } + Screen.FileManager.route
```

and one line at the router call site (`screens.map { it.route }` → `rootRoutes`).

## The premise the research corrected (56.2)

The roadmap said *“the coach-mark beat that spotlights the Projects tab moves to the Navigation
card's Projects cell”*. **There is no such beat.** Phase 54 read the plan: eleven beats
(`editor_drawer`, `drawer_project`, `drawer_demo_pick`, `drawer_file`, `editor_run`,
`preview_close`, `nav_handle`, `nav_tab_packages`, `packages_card`, `nav_tab_terminal`,
`terminal_chip`) and `tabAnchorFor` maps only **Packages** and **Terminal**
(`ui/guide/CoachMarkPlan.kt:475-484`). So 56.2 is what the roadmap's own two rows really are —
a **verification** part:

- the bar still exists, so every bar beat still finds its anchor;
- the `nav_handle` beat (“The tabs are here — five tabs, one tap away”) **needs a copy fix**: it
  says *five*. After this phase the bar has four. That is the one real change 56.2 makes.
- nothing references a Projects tab.

## What did NOT change

`FlatBottomBar` (the divider, the weighted row, `navigationBarsPadding`, the `NAV_HANDLE` anchor),
`EditorNavRevealHandle` + the “Show tabs” string, `NavBarPolicy.hideNavBar` (Phase 32), the
**four** remaining tabs, the Projects **screen** (`FileManagerScreen`), its **route**
(`Screen.FileManager`, never renamed — deep links and saved state survive), the hub, the guide's
other beats, and the install lock's route table (`SetupLockPolicy.optionForRoute("file_manager")`
still answers PROJECTS, so a paused install can still refuse a programmatic arrival).

## Verification

- `SidePanelWiringTest` (now 10 pins): the tab list holds exactly **four** `Screen.*` entries,
  includes Editor/Terminal/Packages/Settings, **excludes** `Screen.FileManager`, and no filler tab
  replaced the fifth slot; `FlatBottomBar`, the handle and `NavBarPolicy` all still present;
  `rootRoutes` exists and the router reads it.
- `BackRouterRootTest`: `file_manager` and `file_manager?openSheet=1` still answer **root** —
  a phone that starts on Projects gets the exit prompt, not a silent exit.
- Local pre-validation: **42/42** host cases (Temurin 25 + kotlinc harness), which includes the
  two phase-55 classes.
- **Device pass required:** [Q1-Q6](DEVICE_ROUND.md).
