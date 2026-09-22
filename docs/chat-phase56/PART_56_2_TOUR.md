# PART 56.2 — The tour still teaches where projects are

> **Status:** 🚧 IMPLEMENTED (2026-09-22) · **Effort:** S · **Cost:** `[docs + one string]`
> **Files:** `ui/guide/CoachMarkPlan.kt` (one body line)

---

## The roadmap's premise, and what the research found

The roadmap (written before the shots were read for 55) said:

> *“The coach-mark beat that spotlights the Projects tab moves to the Navigation card's Projects
> cell. A mark must not point at a tab that is gone.”*

Phase 54 read the plan first, as the law requires. **There is no Projects beat.** The eleven beats
of the Phase 45 tour are:

```text
  1 editor_drawer      2 drawer_project     3 drawer_demo_pick   4 drawer_file
  5 editor_run         6 preview_close      7 nav_handle         8 nav_tab_packages
  9 packages_card     10 nav_tab_terminal  11 terminal_chip
```

and `CoachMarkPlan.tabAnchorFor(route)` maps **only** `modules → nav_tab_packages` and
`terminal → nav_tab_terminal` (`ui/guide/CoachMarkPlan.kt:475-484`). Nothing anchors to the
Projects tab, so nothing had to be moved to the panel's card, and no beat is orphaned by 56.1 —
the bar still exists and still publishes `NAV_HANDLE`, `NAV_TAB_PACKAGES`, `NAV_TAB_TERMINAL`.

The correct action for this part is therefore **verification**, exactly as the roadmap's own
“do not re-open the shots in order to delete the bar” note implies: prove the tour is untouched,
and fix the only thing that *was* stale.

## What was actually changed: the count in the copy

Beat 7 (`nav_handle`) teaches the bar the tabs live in. Its body counted the options:

```diff
- body = "Five tabs, one tap away. They hide while you type — tap this handle to bring them back.",
+ body = "Four tabs, one tap away. They hide while you type — tap this handle to bring them back.",
```

The lesson, the gesture, the anchor id, the ordering and the stall guard are all identical. No
test pinned that sentence (`grep -rn "Five tabs" app/src/test` = nothing), and the wiring test
that matters — `GuideWiringTest`'s publisher pins — reads the anchor plumbing, not the prose.

Two code comments that claimed five tabs were corrected in the same commit
(`MainActivity.kt`’s bar header, `SetupState.kt`’s route table note), so the repository does not
tell two stories about one bar.

## Exit, checked

| Roadmap exit | Result |
|---|---|
| “The guide still runs from first beat to last” | ✅ nothing in the anchors, the plan, the order or the guard changed; the bar and its two anchors are still composed |
| “Projects is not on the bar” | ✅ [56.1](PART_56_1_PROJECTS_LEAVES.md) |
| “Projects opens from the side panel” | ✅ Phase 55 (pinned by `SidePanelWiringTest`) |
| “The guide's Projects beat moves to the card” | ✅ **N/A — no such beat exists**; the research pass is the record, and the copy that counted the tabs was corrected |

## The one thing a later phase must not do

Do **not** invent a “Projects cell” coach-mark beat to satisfy the old sentence. The tour's shape
is the owner's own walk-through (☰ → project → app.py → RUN ▶ → preview → the tabs), it is
device-passed on that order, and a twelfth beat would change a verified sequence to satisfy a
stale line in a plan doc.

**Device pass required:** [Q5-Q6](DEVICE_ROUND.md).
