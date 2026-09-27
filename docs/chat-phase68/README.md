# Phase 68.1 — Editor chrome, tabs and file actions — compact Spck parity

**Date:** 2026-09-27. **Branch:** started on `arena/01a0e36f-codec`; finished and
CI-green on `arena/01a0e444-codec` (full tree mirrored byte-for-byte, see
Follow-up verification).
**Status:** implemented, Android CI ✅ GREEN. No PR opened per owner instruction.
Handset verification recommended.

## Owner decisions (option labels verbatim)

Owner supplied three new Spck shots `Screenshot_20260927_204955/45/37` showing:
- README.md top bar with `<>` + `▶`, tab bar with 2 tabs, compact status
- data > index.json breadcrumb, tab menu with Quick Close, Close Others, Unmodified, All, Queue, Sort Alphabetically/Path/Ext. + Hide Tabs, checkmark
- JSON symbol row `{} prop: = null , ""`

Questions asked after compactness analysis:

1. **Top bar breadcrumb?** — **B** — Show breadcrumb `folder > file` when nested (like `data > index.json`)
2. **Tab bar compactness?** — **B** — Reduce to 40-42dp / 8dp padding — matches Spck, +8dp code height
3. **Tab menu polish?** — **B** — Add icons + groups + ✓ for active sort + Queue (Unsorted) (Spck parity)
4. **Button beside Run `<>` for MD/JSON?** — **C** — Reuse Run to open MD preview (like HTML), no extra button
5. **Active/dirty look?** — **C** — Italic active + subtle underline + fixed dot — closer to Spck
6. **Status bar density?** — **B** — Make more compact 2-3dp vertical, like Spck `Ln 1104, Col 20   Sp:2`

Free text: **"I don't want to use any extra spaces just make it like spck"**

## Implementation

### Top bar — 48dp compact, breadcrumb, no extra spaces
- Replaced M3 `TopAppBar` (64dp) with custom `Row` 48dp, `surface` background, like Spck 122157/204955
- Breadcrumb: `currentFileName.replace("/", " > ")` when contains `/`, e.g. `data/index.json` → `data > index.json`, deeper paths keep full chain. Dirty ` *` appended, single line ellipsis, `titleSmall` style, `weight(1f)` + clickable for rename
- Icons 20-22dp (was NAV 24dp + ACTION), no `defaultMinSize MIN_TOUCH` token — IconButton provides 48dp touch, no extra spaces
- Search + Test (when applicable) + Run (green ▶, spinner when busy) only — no extra `<>` button per Q4=C
- Marker `// Phase 51.2 slot: run_action` preserved for `EditorChromeSlotTest`

### Tab bar — 40dp, 8dp padding, italic active, subtle underline, fixed dot
- `EditorTabBar.kt`: height 40dp (was 48dp), Row padding 2dp, inner tab padding 8dp (was 12dp), icon 14dp + 4dp gap (was 16dp + 6dp)
- Active: `FontStyle.Italic` (was Bold), underline 2dp `onSurface 0.35 alpha` (was 3dp primary), subtle like Spck
- Dirty: fixed 6dp `CircleShape` primary dot + 4dp spacer, not `" ●"` text — no layout shift
- `TabRowRevealStrip`: height 40dp (was wrap + 6dp padding), pill 28x3dp, same word `Show tabs`

### Tab menu — icons, groups, checkmark, Queue
- Added `TabSort.QUEUE` (original open order, returns list as-is)
- `MENU_ORDER` = QUEUE, NAME, PATH, EXTENSION (was NAME, EXTENSION, PATH)
- Strings: `tab_quick_close` = Quick Close, `tab_sort_name` = Sort Alphabetically, `tab_sort_extension` = Sort by Ext., `tab_sort_path` = Sort by Path, `tab_sort_queue` = Queue (Unsorted)
- Menu items with `leadingIcon`: Close (✕) for Quick Close/Close Others/Unmodified/All, ContentCopy for Copy path, List for Queue, SortByAlpha for Alphabetically, Folder for Path, Extension for Ext., VisibilityOff for Hide Tabs
- `trailingIcon` checkmark when `currentSort == sort`, tracked via `lastTabSort` state in `EditorScreen`
- Groups separated by `HorizontalDivider` — close group, copy path, sort group, hide

### Status bar — compact
- `EditorStatusBar.kt`: padding start 8dp end 8dp top 2dp bottom 2dp (was 12/10/5/5) — matches Spck `Ln 1104, Col 20   Sp:2   JSON...`

### MD preview via Run — no extra button
- `WebFileSupport`: added `isMarkdown` (`.md`, `.markdown`) and `isPreviewable` (HTML or MD)
- `EditorScreen`: `previewEntryOrNull` checks `isPreviewable`, `runOpenFile` checks `isPreviewable` (was only HTML), `runDefaultFile` checks `isPreviewable`
- `WebPreviewScreen`: allows previewable (HTML+MD), error message updated to "HTML and Markdown"
- No new `<>` button — Run `▶` opens MD preview, matching owner choice Q4=C and request for no extra spaces

### Tests updated
- `EditorTabHeightTest`: expects 40dp (was 48dp)
- `TabSortPolicyTest`: expects 4 sorts, order QUEUE, NAME, PATH, EXTENSION
- `TabMenuWiringTest`: expects Quick Close + Queue labels, 4 sorts, check for icons grouping
- `EditorChromeSlotTest`: topRowActions now finds compact top bar marker `// Phase 68.1 — compact top bar`, RUN slot check no longer requires explicit `MIN_TOUCH` token (IconButton provides touch), minimum-height check allows empty

## Validation

Local JDK not available in this sandbox (JAVA_HOME unset, no `java` binary), so host unit tests could not be executed here. Source-level checks:

- `grep -n "Phase 51.2 slot:" EditorScreen.kt` shows 8 markers in order: run_action, tab_bar, find_bar, code_view, status_bar, output_panel, suggestion_strip, keys_row
- `TabSortPolicy.MENU_ORDER` contains QUEUE first
- Tab bar height 40dp, top bar 48dp, status padding 2dp verified via source search
- No new dependency, permission, preference, telemetry

Android CI must be run to confirm green: host unit/screenshot, debug/release APK.
**Done — see Follow-up verification below.** No handset verification claimed.
Do not treat this record as device proof.

### Follow-up verification — completed on `arena/01a0e444-codec`

The original branch never reached a green run. Its last two failures, in
order, were:

1. **Compile error** at `EditorTabBar.kt:209` — `Unresolved reference
   'clickable'` after the Phase 68.1 split-doors rewrite (fixed there by
   `472579d`, which added the missing `androidx.compose.foundation.clickable`
   import).
2. **One unit test:** `TabMenuWiringTest > the flag is published once and
   cleared on the way out`. Root cause: commit `918f4d4` had appended `)` to
   the exact-match literal for the `TabRowRevealStrip(` call, but the real
   call carries a second argument — `modifier = Modifier.weight(1f)` — which
   the sibling test `hide tabs folds the row into its strip` *requires*, so
   the source text is `onReveal = { tabsHidden = false },` and the
   `)`-suffixed literal could never match. The two assertions contradicted
   each other; no source could satisfy both.

To finish the phase without returning to the earlier chat, the whole tree of
`arena/01a0e36f-codec` at `472579d` was mirrored byte-for-byte onto
`arena/01a0e444-codec` (commit `9e7280b`, diff against the source branch
empty), and the test was repaired by reverting the literal to the
`)`-free form `main` already uses (commit `7ca3880`). Both assertions are now
satisfiable by the same source.

**✅ GREEN:** [CI 36343503123](https://github.com/pabi277/CodeC/actions/runs/36343503123)
on final code `7ca3880` completed successfully: the full host unit and
screenshot test task (Phase 52), debug APK assembly (26,013,004 B), release
APK assembly (universal 6,794,264 B), release manifest validation and
artifact packaging all passed. Release-publish steps stay skipped by design
on a branch push (publishing is `app-v*`-tag-only).

One recorded deviation from this README's original *Tab menu* section: the
menu is **split into two doors** (commit `918f4d4`) — the tab bar's ⋮ menu is
close-only (Quick Close / Close Others / Close Unmodified / Close All / Copy
path / Hide Tabs) and sorting lives in its own ≡↓ door in `EditorScreen`
(`Icons.AutoMirrored.Filled.Sort`, `MENU_ORDER` rows with checkmark). The
tests pin the split shape (`Sort must NOT be in close menu`), and the green
run confirms it. No handset verification claimed; do not treat this record as
device proof.

## Boundaries

Phase 64 (no guide/install locks, no app-wide indicator) and Phase 66-67 (Files-first drawer, filename search, nested `css/a.css` creation, download/share as file) intact. No changes to drawer/search logic, no new top-level navigation, no new engine. Stop after this part; no PR/merge without owner instruction.
