# PART 60.2 — *Hide tabs*: one flag, two strips

**Spec row:** *Hide Tabs* (spec §2's tab menu). **Owner's answer (2026-09-26, asked before coding):**
**both** readings, in one menu row.

## 1. The question, and why it had to be asked

The spec's *Hide tabs* comes from SPCK, where it hides the editor's **file-tab row**. This app has
a second, older meaning of almost the same words: since Phase 32.1 the editor can hide the **bottom
bar** while a keyboard is up, and the thin handle shown in its place says, literally, **“Show
tabs”** (`MainActivity.kt`'s `EditorNavRevealHandle`, `NavBarPolicy.hideNavBar`). Two honest
readings, one row — the roadmap's rule for this series is to ask rather than guess, so it was asked,
and the owner chose **both**:

* the **file-tab row** folds into a reveal strip of its own, and
* the **bottom bar** gets the same manual door, through the idiom it already has.

## 2. One flag

`EditorChromeState` — the object the editor publishes keyboard/dialog/drawer facts through — gained
a fifth fact:

```kotlin
val tabsHidden: StateFlow<Boolean>   // set by the editor, read by the app scaffold
```

* **The editor owns it.** `EditorScreen` declares `var tabsHidden by remember { mutableStateOf(false) }`
  beside `keysRowVisible`, publishes it with `LaunchedEffect(tabsHidden) { EditorChromeState.setTabsHidden(tabsHidden) }`,
  and clears it in the existing dispose block — the same shape (and the same reason) as the
  keyboard fact: a stale "hidden" would greet the next visit with a parked row and a parked bar.
* **The scaffold reads it.** `MainActivity` collects it and hands it to the policy:
  `NavBarPolicy.hideNavBar(…, hiddenByUser = editorTabsHidden)`.
* **The handler writes it back.** The reveal handle's `onReveal` now does both halves of "bring it
  back": `navRevealed = true` (the pre-32 sticky reveal) **and** `EditorChromeState.setTabsHidden(false)`.
  There is no second flag that could disagree about whether the strips are parked.

**Session-scoped, like `keysRowVisible`** — a view choice, not a setting. Nothing was added to
DataStore, and a fresh launch can never open with the tabs missing.

## 3. Two strips

**The file-tab row** (`EditorScreen.kt`, inside the `// Phase 51.2 slot: tab_bar` block):

```kotlin
if (tabsHidden) {
    TabRowRevealStrip(onReveal = { tabsHidden = false }, modifier = Modifier.weight(1f))
} else {
    EditorTabBar(…, onHideTabs = { tabsHidden = true }, …)
}
```

`TabRowRevealStrip` (new, in `EditorTabBar.kt`) is the **same idiom Phase 32.1 gave the bottom
bar**: a 44×4 pill and one word, tappable across its whole width, deliberately thin because it is a
parked row and not a second toolbar. It is not a 48 dp target and it is not an `IconButton` — the
touch-floor census (`TouchTargetTest`) counts icon buttons, and this is the identical shape to the
handle already shipping in `MainActivity`.

**The ⋮ cell stays outside the fold.** The row's trailing cell is the editor's own action list
(undo, redo, save, save all, rename, reload, format, jump to line, find, diagnostics, line endings,
the project chrome, share, clear diagnostics). Hiding the tabs must never take the editor's actions
with it, so the `Box { IconButton(⋮) … DropdownMenu(…) }` is composed in **both** states and only
the strip area switches. `TabMenuWiringTest` pins that the cell's `showMoreMenu = true` sits after
the row's `EditorTabBar(` call, i.e. outside the choice.

**The bottom bar** hides through `NavBarPolicy` exactly as the keyboard does, and its reveal handle
is unchanged in shape — only its label moved from a hard-coded `"Show tabs"` to
`stringResource(R.string.tab_show)`, the same resource the row's strip uses, so the two reveal
affordances cannot drift apart.

## 4. The policy change, and why the bottom-bar half is editor-scoped

```kotlin
fun hideNavBar(
    inEditor: Boolean,
    imeVisible: Boolean,
    keysVisible: Boolean,
    revealed: Boolean = false,
    hiddenByUser: Boolean = false,
): Boolean {
    if (revealed) return false
    if (hiddenByUser && inEditor) return true
    return imeVisible || (inEditor && keysVisible)
}
```

Three things are deliberate:

1. **A reveal still wins.** Even with `hiddenByUser`, `revealed` returns false first — the policy
   must not depend on the app remembering to write both facts in one order.
2. **The manual hide is the editor's rule, like every other hide.** Off the editor the bar comes
   back by itself: the reveal handle that un-hides it only exists in the editor, and parking the
   only navigation the other three tabs have would strand the user on a screen with no way out.
3. **The IME still doesn't matter.** While `tabsHidden` is true in the editor, the bar is hidden
   whether or not a keyboard is up — that is the one state the auto rules would otherwise keep the
   bar in, and it is exactly what the manual door is for.

`NavBarPolicyTest` gained three cases: hidden-by-user hides in the editor and not outside it, it
hides with the keyboard **down** (the state the auto rules can't reach), and a reveal beats it.

## 5. What was deliberately not invented

* **No second reveal affordance for the bottom bar.** Its handle already existed; the phase wired
  *Hide tabs* to it rather than drawing a new one, and the guide's `NAV_HANDLE` anchor is untouched
  (one anchor id, one lesson).
* **No setting, no persistence** (§2).
* **No "Show tabs" row in the tab menu** — the menu lives in the row it hides, so the way back is
  the strip (and the bar's handle). A menu row nothing can reach is not a control.
