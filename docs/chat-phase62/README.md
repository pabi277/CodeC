# Phase 62 — Settings: find and group

**Owner row of record:** spec §4 — a Settings **search bar** that lets a user find a specific
configuration without scrolling a 1,600-line list, and **collapsible categories** over the
sections the screen already draws.

**Owner's answers (2026-09-22, `PHASE59_63_UI_PARITY_ROADMAP.md` §3):** *start at **62*** (of the
five phases the spec re-scoped into), and the items **no shot shows are approved to be built as
specified** — the shots stay the reference for everything they do show.

| Part | Title | Status |
|---|---|---|
| 62.1 | Settings, findable | 🚧 IMPLEMENTED — [PART_62_1](PART_62_1_SETTINGS_SEARCH.md) |
| 62.2 | Folded sections | 🚧 IMPLEMENTED — [PART_62_2](PART_62_2_COLLAPSIBLE_SECTIONS.md) |

## What §62 asked for, and where it stands

* **A search bar at the top** → **built** (62.1). It filters the screen's own **66 control rows in
  12 sections**, matched on the row's label, its keywords and the section it lives in — case- and
  punctuation-blind, so the row whose label opens with a quote mark is found by typing `more`.
* **Collapsible categories** → **built** (62.2). Every section that holds rows folds; its header
  says how many rows it is holding, and folding is remembered for the visit, not written to disk.
* **Custom snippets, Show hints, Pin on edit** → **already built** (the roadmap's §0 table:
  `customSnippets` in `keysForContext`, `tabMode` in `EditorShellUi`/`EditorScreen`) — nothing was
  re-implemented for them.
* **Tablet mode, touch action bar, haptic, predictive** → **read this way:** *Haptic* is
  `codec_keys_haptics` and the eight `Haptics` moments (already built, 51.4/28.2). *Touch action
  bar* is the row 57.2 docked above the keys (`EditorKeysRow`) — a toggle for it would need a
  behaviour the owner names, and the row itself is shipped. *Tablet mode* has **no behaviour to
  gate** in this checkout: nothing in the app changes when it flips, and this app does not draw
  dead controls — so it is **refused in writing** until the owner names what it should do (the
  same standing as the roadmap's §2 refusals).
* **Refused, unchanged from the roadmap:** an AI-assistant toggle (the fifth rail slot is reserved
  for the owner's own plan) and a predictive-keyboard switch (it would silently reverse 57.2's
  typing-surface gate).

## Exit criteria, checked against this checkout

| Exit line (§62) | Where it stands |
|---|---|
| Typing “haptic” finds the haptics row | ✅ `SettingsSearch.matchesRow("haptic", "Haptics")` — pinned; “Haptics” is a row under Appearance, and a word may match a row's label, its keywords or its section |
| Every section collapses | ✅ the nine that hold rows fold (62.2); the three whose content the catalog cannot index are **not tappable**, because folding them would hide nothing |
| The audit still matches the screen | ✅ `SettingsAuditTest` (12 sections, 66 rows, table ↔ screen) passes untouched, and `SettingsSearchPolicyTest` re-checks the same rows per section against `docs/chat-phase38/SETTINGS_AUDIT.md` |
| No dead control is shipped | ✅ the count badge and the chevron are the only furniture added to a header, and the chevron only appears where a tap does something |

## Records

* [`PART_62_1_SETTINGS_SEARCH.md`](PART_62_1_SETTINGS_SEARCH.md) — the catalog, the matcher, the
  three laws, and why the catalog is generated from the screen.
* [`PART_62_2_COLLAPSIBLE_SECTIONS.md`](PART_62_2_COLLAPSIBLE_SECTIONS.md) — the fold, the count,
  the search-overrides-fold rule, and the one census the phase re-cut.
* `docs/PHASE59_63_UI_PARITY_ROADMAP.md` §62 (the phase's own line, now shipped) and §3 (the
  owner's four answers).

## Test log (Phase 62 — host JVM)

| Run | Result |
|---|---|
| `SettingsSearchPolicyTest` (19 cases) + `SettingsSearchWiringTest` (8 pins) | **27 passed / 0 failed** |
| the broad pure-source regression set (the pins that read `SettingsScreen.kt`, `strings.xml` and every pure policy this checkout can compile on a host JVM) | **883 passed / 0 failed** |
| CI round 1 | **RED, for cause** — the wrapper boundaries cut two declarations from their readers (`PART_62_2 §5`); fixed, plus a third boundary bug CI could not see (the DEBUG guard around Developer Options was left outside its fold), plus two new structural pins that catch all three |
| CI round 2 | ✅ **GREEN** — run `35724187664` on `aa1fcba`, job `build`, zero failed steps (the host unit + screenshot step that round 1 died in) |

**Next:** the owner's device rounds (this phase has none of its own beyond S1-S6 + 57's P/Q/R, none
run yet), then 59 → 60 → 61 → 63 in the owner's order — and the merge gate stands: no PR, no merge,
no `main` push without his command.
