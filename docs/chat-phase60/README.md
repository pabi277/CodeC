# Phase 60 — Tabs and the coding row

**Owner row of record:** spec §2 — the tab menu: *Quick Close / Close Others / **Close Unmodified** /
**Hide Tabs*** … **the three sorts** (alphabetical / by extension / by path).

**Scope, as the roadmap re-scoped it against this checkout (not against the spec's marketing copy):**

* the tab menu already exists — long-press a tab (`EditorTabBar.kt:121`) and it offers Close tab /
  Close others / Close all / Copy path. This phase **extends** it; it does not build a second menu.
* the *coding row* half of the row's title is **verify, not rebuild**: the symbol strip is
  `EditorKeysRow` (Phase 16/26.1, re-shaped by 57.2) and the shots show **one** row
  (`docs/spck-ui/Screenshot_20260922_124105`), so a second, symbol-specific toolbar stays
  unbuilt until the owner asks for one (roadmap §60, "ask, don't guess").
* **Hide tabs** needed the owner's word, and got it (2026-09-26): the spec's *Hide tabs* is the
  editor's **file-tab row**, and this app already had a hide-and-reveal idiom for the **bottom
  bar** whose handle literally says *Show tabs*. The owner chose **both, in one menu row** — see
  PART_60_2, where the one flag that does it and the reason the bottom-bar half stays
  editor-scoped are written down.

| Part | Title | Status |
|---|---|---|
| 60.1 | The tab menu's new rows, and the sorts | 🚧 IMPLEMENTED — [PART_60_1](PART_60_1_TAB_MENU.md) |
| 60.2 | *Hide tabs*: one flag, two strips | 🚧 IMPLEMENTED — [PART_60_2](PART_60_2_HIDE_TABS.md) |

## What §2 asked for, and where it stands

* **Close unmodified** → built (60.1): a new `TabClosePolicy` names the rule, the ViewModel answers
  the one fact the policy cannot (cleanness) and reuses the ordinary `closeTab`.
* **Hide tabs** → built (60.2): the tab menu folds the file-tab row into a reveal strip and parks
  the bottom bar through the existing `NavBarPolicy` / reveal-handle idiom.
* **Three sorts** → built (60.1): `TabSortPolicy` orders `EditorTab` by name, extension or path,
  and the choice is applied to the **open tabs themselves** so the strip, Ctrl+Tab, the
  close-fallback tab and the eviction pick all read one order.
* **The tab 3-dot menu** → already built (57.1 put the editor's ⋮ in the tab row's trailing cell);
  the three new rows landed there, next to the close family they belong with.
* **The coding row / symbol strip** → verified, untouched: `EditorKeysRow` still carries the same
  keys, and no second toolbar was invented.

## Exit criteria, checked against this checkout

| Exit line (§60) | Where it stands |
|---|---|
| The tab menu lists close/others/all/unmodified, hide-tabs and three sorts | ✅ `EditorTabBar`'s menu, pinned by `TabMenuWiringTest` (the menu walks `TabSortPolicy.MENU_ORDER`; `TabSortLabels` must label every entry) |
| Sorting is a pure function with host tests | ✅ `ui/editor/TabSortPolicy.kt` + 11 `TabSortPolicyTest` cases (order, case-blindness, tiebreaks, extensionless files, determinism) |
| The top row is unchanged from 57.1 | ✅ nothing in the top row's `actions` block was touched; `EditorChromeSlotTest`'s top-row pins (no overflow, bare green ▶, the tour anchor) pass untouched |
| No dead control is drawn | ✅ every new row has a real operation behind it; no sort carries a tick (the re-order *is* the choice, so an indicator would lie the moment a file opens) |
| The editor keeps a buffer alive | ✅ `TabClosePolicy` never names the active tab, and `closeTab`'s last-tab guard is untouched |

## Test log (Phase 60 — tabs and the coding row)

Local host runs through the `/tmp` harness (rebuilt twice this phase: `/tmp` was wiped between
sessions), on this checkout:

* **35 passed / 0 failed** — the phase set: `TabSortPolicyTest` (11), `TabClosePolicyTest` (6),
  `NavBarPolicyTest` (13, three of them new), `TabMenuWiringTest` (8 pins),
  `ComposableAnnotationTest` (1).
* **534 passed / 0 failed** — the regression set: the broad pure-source sweep (197 files after
  pruning) plus the phase's own files, compiled together.
* **CI ✅ GREEN on the first round** — `36220293009` on `017dd2c` (build + `:app:testDebugUnitTest` +
  `:app:lintDebug`, zero failed steps). The phase that follows two rounds of stray-`@Composable`
  reds opened with a clean run: `ComposableAnnotationTest` is in the local set and passed before
  the push.
* **Not runnable here, by construction:** anything that needs Compose or the Android SDK — the
  screens, the tab bar, `MainActivity`. CI is their gate.
