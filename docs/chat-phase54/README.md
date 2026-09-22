# CodeC Phase 54 — Reference lock (the phone shots, before any chrome)

> **Status:** 📋 **PLANNED → this doc set is the phase.** Phase 54 changes **no app
> file**; it is the phase that makes 55–58 citable. · **Cost:** `[docs-only]` ·
> **Effort:** S (3 parts, all research) ·
> **Owner row (verbatim):** *“create phase wise plan remove add what needed
> always say to research and take the reference seriously.”* and, on the bottom
> bar, *“I don't think removing the full down ber is a good choice i think only
> removing the project option is ok.”*
>
> Parent: [`PHASE54_58_PHONE_UI_ROADMAP.md`](../PHASE54_58_PHONE_UI_ROADMAP.md).
> Report: [`SPCK_PHONE_UI_REPORT.md`](../SPCK_PHONE_UI_REPORT.md).
> Reference of record: the seven `docs/spck-ui/Screenshot_20260922_*.jpg`
> (1080×2340), read in this session on **2026-09-22**.

```text
  54.1  Re-open the seven shots. Write the reference card a later phase must cite
  54.2  file:line of every CodeC control this series will remove or move
  54.3  The missing-shot list, and the bar decision, written so 56 cannot drift
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [54.1](PART_54_1_SHOTS.md) | The shots, not the drawings — the reference card | S | ✅ DONE (this branch) |
| [54.2](PART_54_2_TODAY.md) | What CodeC has today — `file:line` on this checkout | S | ✅ DONE (this branch) |
| [54.3](PART_54_3_OPEN.md) | What is not in the shots, what the owner already decided | S | ✅ DONE (this branch) |

## Phase 54 start record

**Step 1 — state verified first** (`rule.md` §4.1), 2026-09-22:

```text
git status        → clean, on arena/01a0c83e-codec
git log --oneline → 3f9fd6b Merge pull request #85 from pabi277/arena/01a0c7ec-codec
                    (= origin/main tip; this branch is branched from it)
gh pr list        → no PR from arena/01a0c83e-codec (the two open PRs, #83/#42,
                    are other branches — untouched)
gh run list       → newest: 35704169797 "Merge pull request #85 …" ✅ success (main)
```

**Step 2 — the seven shots were opened in this session** and read one by one:
`122157` (editor, keyboard down), `122203` (Files), `124049` (Navigation),
`124052` (Search), `124055` (Repository), `124058` (Account), `124105` (editor,
keyboard up). The findings are in [54.1](PART_54_1_SHOTS.md), with pixel
positions, because a summary written from memory is exactly what the roadmap
forbids.

**Step 3 — the CodeC side was read, not remembered** — every control this series
will touch, with `file:line` on this checkout, is in [54.2](PART_54_2_TODAY.md).

## The four laws this phase exists to protect

1. **The shots are the reference** — not `docs/spck-ui/0*.png` (drawings), not
   `image-search/` store art. A later phase that codes from a drawing has failed
   the phase.
2. **The bottom bar stays.** Five shots show an editor with no bottom bar at
   all. The owner looked at that and rejected copying it. `FlatBottomBar`,
   `EditorNavRevealHandle`, and `NavBarPolicy.hideNavBar` are **not** removal
   targets. Only the **Projects option** leaves the bar, in 56, and only after
   Projects can be opened from the side panel.
3. **Unseen controls are asked for, never invented.** The open list is
   [54.3](PART_54_3_OPEN.md) §“Missing-shot list”.
4. **Clean room.** Visible behaviour only. No SPCK code, no SPCK sample, no
   SPCK account/shop, no SPCK git engine.

## Exit condition (the roadmap's, restated)

> A reference card in `docs/chat-phase54/`. **Zero app files changed.**

- [x] 54.1 — the card, per shot, with what it settles and what it refuses to
- [x] 54.2 — the `file:line` inventory of the controls to remove/move
- [x] 54.3 — the bar decision in writing; the bottom-row and fifth-icon
      questions; the missing-shot list
- [x] `git diff --stat` on app sources is **empty** for this phase (verified in
      the commit that lands this doc set)

## One finding that changes a phase (recorded here, not discovered later in 56)

The roadmap's Phase 56 has two parts: *take Projects off the bar* and *move the
tour's Projects beat to the side panel*. **There is no Projects beat.** The
eleven beats of the Phase 45 tour are `editor_drawer`, `drawer_project`,
`drawer_demo_pick`, `drawer_file`, `editor_run`, `preview_close`, `nav_handle`,
`nav_tab_packages`, `packages_card`, `nav_tab_terminal`, `terminal_chip`
(`ui/guide/CoachMarkPlan.kt:60-112` anchors, `:193-300` steps). The only two
bottom-bar tabs with an anchor are Packages and Terminal
(`CoachMarkPlan.kt:475-484 tabAnchorFor`). So 56.2 has nothing to move and
nothing to drop: it becomes a **verification** part — after 56.1, re-run the
tour end to end and confirm no beat points at a tab that is gone. Recorded in
[54.2](PART_54_2_TODAY.md) §5.
