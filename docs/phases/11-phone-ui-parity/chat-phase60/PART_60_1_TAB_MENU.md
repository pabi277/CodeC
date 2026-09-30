# PART 60.1 — the tab menu's new rows, and the sorts

**Spec rows:** *Close Unmodified*, and the three sorts (*alphabetical / by extension / by path*).
**Home:** `ui/components/EditorTabBar.kt`, the long-press menu that already carried Close tab /
Close others / Close all / Copy path.

## 1. Close unmodified

**Where the law lives.** `ui/editor/TabClosePolicy.kt` — a new pure object with one question:

```kotlin
fun unmodifiedTargets(cleanPaths: List<String>, activePath: String?): List<String> =
    cleanPaths.filterNot { it == activePath }
```

Two laws, and they are the whole policy: **the active tab is never taken** (the editor keeps a
buffer alive, so when the whole strip is clean exactly the active tab stays — and the row is
therefore safe to tap twice), and **only clean tabs are taken**.

**Why the policy cannot answer the whole question.** "Is this tab clean?" is *not* one question in
this codebase. Phase 22.5 keeps the **active** tab's stashed buffer deliberately stale between
boundaries (the live buffer is in the ViewModel and the stash is only refreshed on switch/close),
so the active tab's dirtiness must come from the live flag while every other tab's comes from its
own `buffer.text == savedText`. That is a fact only the ViewModel holds, which is exactly why the
decision below it is pure and the VM is reduced to reading two facts:

```kotlin
val clean = _openTabs.value.filter { tab ->
    if (tab.relativePath == active) !_isDirty.value else tab.buffer.text == tab.savedText
}.map { it.relativePath }
TabClosePolicy.unmodifiedTargets(clean, active).forEach { path ->
    closeTab(appContext, path, saveFirst = false)
}
```

`saveFirst = false` because there is nothing to save — the tabs entering this list are clean by
construction — and the ordinary `closeTab` does the rest (the fallback tab, the undo-manager
cleanup, the launch-point memory). Closing a tab from this row is the same operation as closing it
by hand, minus the save.

**Not invented:** no confirmation dialog (nothing unsaved can be lost — that is the row's whole
promise), no per-tab haptics (the existing bulk rows, Close others and Close all, perform none
either; one tick per closed tab would be a small machine gun, not feedback).

## 2. The three sorts

**Where the order lives.** `ui/editor/TabSortPolicy.kt`:

```kotlin
enum class TabSort { NAME, EXTENSION, PATH }
val MENU_ORDER = listOf(TabSort.NAME, TabSort.EXTENSION, TabSort.PATH)
fun sort(tabs: List<EditorTab>, sort: TabSort): List<EditorTab>
```

* **NAME** — `displayName` lower-cased → full path lower-cased → exact path.
* **EXTENSION** — the extension lower-cased (extensionless files first: their key is `""`) →
  `displayName` → path.
* **PATH** — the whole project-relative path lower-cased → exact path.

Every comparison is **total**, not merely stable: the lower-cased comparison comes first and the
exact spelling is the last tiebreak, so the same set of tabs always lands in the same order on
every device (`TabSortPolicyTest` proves it by sorting the list forwards and reversed). An
extension is the text after the **last** dot, and a **leading** dot names a hidden file
(`.gitignore`), not an extension — the same reading a file manager's "type" column uses.

### The design decision: the sort is applied to the open tabs, not to the drawing

The tempting shape was a *view* sort — sort `tabViews` for rendering and leave the ViewModel's list
alone, which would have made the change trivially reversible. It was rejected because this app has
more than one reader of the tab order: `nextTab()` (Ctrl+Tab), the tab a close falls back to, and
`trimTabs`'s eviction pick all read `_openTabs`. A view-only sort would have left the visible order
and the Ctrl+Tab order disagreeing — a tab strip that contradicts the keyboard.

So `EditorViewModel.sortTabs(sort)` replaces the open-tab list with the policy's answer, and
**nothing else changes**: the active tab stays active (it only moves), its live buffer is untouched
(the reorder moves records, not the buffer), and a sort that changes nothing returns early without
emitting a new list.

**What that costs, recorded rather than hidden:** there is no "back to open order". The spec names
three sorts and no neutral row, and the information needed to reconstruct the open order is not
kept anywhere (that would mean a second field on `EditorTab` and a rule about what happens when the
two disagree). Adding an "Open order" row therefore needs the owner's word, exactly like the
second symbol toolbar in §60's plan.

### No tick in the menu

The sorts are **one-shot actions**, not a mode: there is no "current sort" to mark, because the
order was computed once and a file opened afterwards appends at the end. A check mark would claim a
state that stops being true the moment anything opens — so the menu shows three plain rows, the
same shape as Close others.

## 3. The menu, and how the pieces are held together

`EditorTabBar` gained three parameters (`onCloseUnmodified`, `onHideTabs`, `onSort`) with the
existing defaults, and its menu gained, after Copy path:

* a divider, then **Hide tabs** (60.2), then a divider, then the three sort rows — so "what the row
  looks like" and "how it is ordered" read as their own group, and the close family stays the top
  block.

The sort rows are **generated from the policy**, never hand-typed:

```kotlin
TabSortPolicy.MENU_ORDER.forEach { sort ->
    DropdownMenuItem(text = { Text(stringResource(TabSortLabels.of(sort))) }, onClick = { … onSort(sort) })
}
```

and `TabSortLabels.of` (in the same file) is a `when` over every enum entry, so a fourth sort
without a label is a **compile error**, not a blank row. `TabMenuWiringTest` pins the other half:
the menu really walks `MENU_ORDER`, every entry really has a label, the label map holds exactly
three, and both callbacks are wired to the ViewModel's own operations.

## 4. What was deliberately not touched

* **`EditorKeysRow`** (the coding row): verified, not rebuilt. The shots show one row; the strip's
  keys, popup, swipe layers and hold-repeat are Phase 16/26.1/57.2's and stay as they are.
* **The top row**: §60's exit line asks it to be unchanged from 57.1, and it is —
  `EditorChromeSlotTest`'s top-row pins (no overflow icon, bare green ▶, the tour's anchor) pass
  untouched.
* **The tab's own ✕**: still none, as the shots have it. Closing is the menu, as before.
