# PART 59.1 — the hub's *Recent* filter, and the spec's row

**Roadmap line:** *“59 — Projects hub: find, filter, identify (spec §1, its own High Priority) —
Add: a Recent filter beside the existing chips, fed by `RecentProjects` (the same rows the side
panel's card uses, so the two cannot disagree) … Ask, don't guess: whether the chip row becomes
`All · Recent · C · Python · Web · Git` (six, scrollable) or the spec's literal
`All · Recent · Create`.”*

**The owner answered (2026-09-22): the spec's literal row.**

## 1. What the research pass found (before any code)

The roadmap's premise — *“fed by `RecentProjects`, the same rows the side panel's card uses”* —
needed checking, because the two surfaces do not read the same list:

| Surface | What it lists | What it ranks by |
|---|---|---|
| the side panel's RECENT card | `ProjectManager.listProjects()` (`EditorScreen.kt:1188-1208`) | `info.root.lastModified()` — the **folder's** own time, capped at `RecentProjects.MAX_ROWS` (8), sorted by `RecentProjects.build` |
| the hub's cards | `FileManagerViewModel`'s entries (`:144` and `:173`) | `ProjectHubStats.scan(...).lastModified` — the newest thing **inside** the project (folder *or* file), by design (the card's age line) |

So the hub already had the data for “most recently touched”, but on a **different clock** from the
panel — a project whose file was edited an hour ago but whose folder was last touched yesterday
ranks first in the hub and second in the panel. The roadmap's demand (“the two cannot disagree”)
therefore could not be met by simply sorting the hub's existing field: the *reading* had to be
unified first.

## 2. One recency rule, one implementation

Three small changes make the two surfaces agree **by construction** rather than by resemblance:

1. **`ProjectHubStats.ScanResult` reports the folder's own time** — `folderModified` — beside the
   newest-thing time it already reported (`scan()` now returns
   `ScanResult(count, newest, folderModified)`; `folderModified` defaults to `lastModified` so
   existing callers and cases keep their meaning).
2. **`ProjectHubEntry` carries it** (`folderModified: Long = 0L`) with one definition of what
   *recent* means: `val recency: Long get() = if (folderModified > 0L) folderModified else lastModified`.
   `FileManagerViewModel` passes `scan.folderModified` — the same reading `EditorScreen` takes for
   the panel (`info.root.lastModified()`).
3. **`ProjectsHub.recentEntries(entries, limit = RecentProjects.MAX_ROWS)` asks the panel's own
   implementation** — `RecentProjects.build(...)` — for the ranking, then returns the hub's
   entries in that order. There is no second comparator: the hub's *Recent* and the panel's card
   are one list with two renderings, capped at the same 8, ordered by the same rule.

`ProjectHubFilter.RECENT` is deliberately **not** in `ProjectHubEntry.filters`: “recent” is a
project's position in a ranking, not a fact about the project, so `filterEntries` answers it from
`recentEntries` and every other chip keeps working exactly as before:

```kotlin
if (filter == ProjectHubFilter.RECENT) {
    val recent = recentEntries(entries).map { it.name }.toSet()
    result = result.filter { it.name in recent }
} else if (filter != ProjectHubFilter.ALL) {
    result = result.filter { it.filters.contains(filter) }
}
```

The name search still combines with it (`recentEntries` chooses the set, the query narrows it).

## 3. The row

`FileManagerScreen`'s row is now exactly the spec's three, in the spec's order:

```kotlin
HubFilterChip(ProjectHubFilter.ALL, filter, stringResource(R.string.hub_filter_all), null, onFilterSelected)
HubFilterChip(ProjectHubFilter.RECENT, filter, stringResource(R.string.hub_filter_recent), null, onFilterSelected)
HubActionChip(stringResource(R.string.hub_filter_create), Icons.Default.Add, onCreate)
```

*Create* is the row's one **action**, so it is its own composable (`HubActionChip`) sharing the
filter chips' shell — same shape, spacing, boundary colour and tap area — but never “selected”,
because it is not a state the list can be in. It calls `onCreate`, which `FileManagerScreen` has
always passed as `{ showHubSheet = true }`: **the sheet the ＋ opens**, so the app keeps one
add-project path rather than two.

## 4. What left the row, and what did not

The owner chose the literal row knowing its cost, and the cost is recorded here rather than
papered over:

* **Gone from the row:** the `Git`, `C`, `Python` and `Web` chips (`hub_filter_git/c/python/web`
  are no longer referenced by the hub row; the strings stay with the policy).
* **Kept everywhere else:** `ProjectHubFilter.GIT/C/PYTHON/WEB`, `ProjectHubEntry.filters` and the
  `filterEntries` branch that honours them are **untouched**, and so are their host cases. The
  language filters are now unreachable from the row — a row change, not a feature removal, and a
  future home for them is one chip, not new machinery.
* **Kept visible:** the **kind** each project belongs to. It used to be the card's leading square;
  that square is the project's own mark now (59.2), so the kind moved into the card's subtitle —
  `C · main · 3 files · 2 days ago` — one line down instead of lost.

## 5. Records and tests

`ProjectsHubTest` gains four cases (the ranking, the panel-rule reuse, the folder clock's
precedence, and the folder clock's separate reporting), and the recency plumbing is pinned
end-to-end by `ProjectMarkWiringTest` (`recentEntries` → `RecentProjects.build`,
`limit = RecentProjects.MAX_ROWS`, `folderModified = scan.folderModified`).

| Case | What it pins |
|---|---|
| *Recent filters to the ranking, not to a flag on the entry* | newest folder first; `RECENT` never appears in `filters`; the name search still combines |
| *the hub's Recent list is the panel's own ranking, cap and all* | twelve projects → exactly `RecentProjects.MAX_ROWS` rows, and the chip shows the same list |
| *a project's recency is the folder clock when it is known* | precedence, and the fallback for an unknown folder time |
| *the scan reports the folder's own clock apart from its newest file* | the two readings are genuinely different values off one real temp tree |
