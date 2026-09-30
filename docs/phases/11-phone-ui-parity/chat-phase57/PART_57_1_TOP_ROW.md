# PART 57.1 — the top row and the tab row, as the shots

**Files touched**

| File | What changed |
|---|---|
| `app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt` | top row rebuilt (:1452-1485); the ⋮'s `DropdownMenu` moved to the tab row's trailing cell (:1614-1690, `SpckIcons.EditorMenu` at :1674); RUN is a green glyph `IconButton` (:1878-1920); three comments corrected |
| `app/src/main/java/com/codeci/ide/ui/components/SpckIcons.kt` | `EditorMenu` glyph (:408) — clean-room, drawn from the shot's shape |
| `app/src/main/java/com/codeci/ide/ui/editor/RunButtonStyle.kt` | re-scoped: `HERO / LOCKED / QUIET`; the container tone pair deleted |
| `app/src/main/java/com/codeci/ide/ui/editor/EditorChrome.kt` | slot order: the run action is declared before the tab row, with the reason |
| `app/src/test/java/com/codeci/ide/EditorChromeSlotTest.kt` | six new/changed pins (below) |
| `app/src/test/java/com/codeci/ide/RunButtonStyleTest.kt` | rewritten to the glyph vocabulary |

## 1. The top row

```kotlin
title = {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FileIconView(                       // the file's OWN mark, as 122157 draws it
            name = currentFileName,
            modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.NAV))
        )
        Spacer(Modifier.width(CodecTokens.space(Space.S)))
        Text(                               // the name, still tap-to-rename
            text = currentFileName.substringAfterLast('/') + if (isDirty) " *" else "",
            modifier = Modifier.clickable { showRenameDialog = true },
            style = MaterialTheme.typography.titleMedium
        )
    }
},
navigationIcon = { /* ☰ — untouched, still the tour's first anchor */ },
actions = {
    IconButton(onClick = { find toggle }) { Icon(Icons.Default.Search, …) }
    /* Test ▷ for pytest/go files — unchanged, and NOT in the shots' file type */
    /* the green ▶ — below */
},
```

* The mark is `FileIconView` — the same component the drawer's tree and the tabs use, so a
  Python file shows the Python mark and an HTML file the HTML mark, exactly as the shot.
* The old “name **or** tab strip” crossfade is gone from the bar: the shots show the name and
  the tabs **at once**, so the name is never taken away by opening a second file.

## 2. The tab row (its own row) and the ⋮'s new home

```kotlin
// Phase 57.1 — the shots' second row: the open tabs under the file's name,
// with the editor-menu cell at their right edge.
Crossfade(
    targetState = tabViews.isEmpty(),
    animationSpec = motion.floatOrSnap(CodecMotion.crossfadeSpec)
) { noTabs ->
    if (!noTabs) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Phase 51.2 slot: tab_bar
            EditorTabBar(…, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(SpckIcons.EditorMenu, contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(expanded = showMoreMenu, …) { /* every row, unchanged */ }
            }
        }
    }
}
```

* Phase 50.4's crossfade keeps its job (the row arrives and leaves with the tab set); only
  its home moved.
* The list inside the cell is **the same list**, item for item: Undo, Redo, hide/show keys
  row, Save, Save all, Rename, Reload, Format, Jump to line, Find, Diagnostics, line
  endings, set/clear launch default, run config, run in terminal, Save to project, Share,
  Close file, Clear diagnostics.
* The glyph: the shot's mark is a stack of lines shrinking toward a downward chevron. Its
  meaning in the source app is not documented by the shots, so `SpckIcons.EditorMenu` is a
  clean-room drawing of **the shape** and the cell opens CodeC's own list. No action is
  guessed from a glyph (the phase's “prefer omit-and-ask” rule).

## 3. RUN ▶

```kotlin
val runButtonState = RunButtonState(
    running = outputState.busy, locked = editorChromeLocked,
    hasOpenFile = openTabs.isNotEmpty() || currentFileName.isNotEmpty(),
)
val runRole = RunButtonStyle.roleFor(runButtonState)
IconButton(
    onClick = onRunTap,
    modifier = Modifier
        .padding(end = CodecTokens.space(Space.S))
        .defaultMinSize(minHeight = CodecTokens.space(CodecTokens.MIN_TOUCH))
        .then(GuideAnchor.modifier(GuideAnchors.EDITOR_RUN, onClick = onGuideRunTap)),
    enabled = RunButtonStyle.isEnabled(runButtonState)
) {
    if (RunButtonStyle.showsRunning(runButtonState)) {
        CircularProgressIndicator(…, color = RunGreen)      // “did my tap work?”
    } else {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.run),   // the word, for TalkBack
            tint = if (runRole == RunButtonRole.HERO) RunGreen
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

* `RunGreen` is the file's pre-existing run colour (`EditorScreen.kt:210`), the same one the
  Test ▷ row wears — one run colour in the app, not a new hex.
* The lock still wins the tap (Phase 44): a locked ▶ fires `showChromeLock()` and says why.
* The tour keeps its anchor and its one-tap click.

### `RunButtonStyle` after this part

| Kept | Removed | Why removed |
|---|---|---|
| `roleFor` (lock > nothing-open > hero), `showsRunning`, `isEnabled`, `isPrimary` | `RunButtonTone`, `RunButtonVisual`, `toneFor`, `visualFor` | They described a container the reference does not have. Dead policy is worse than none: a test would keep passing while the screen draws something else. |

## 4. The pins (`EditorChromeSlotTest`)

| Test | Pin |
|---|---|
| `the RUN control is the bare green triangle of the shots` | `IconButton`, `PlayArrow`, `roleFor`, hero-only green, running state, 48 dp floor |
| `the top row paints no RUN label` | the actions block has **no** `run_running`, **no** `Text(text = if (RunButtonStyle.showsRunning…))`, and **does** keep `stringResource(R.string.run)` for TalkBack + the anchor |
| `the top row has no overflow icon` | the actions block has no `MoreVert` and no `showMoreMenu = true`; 🔍 is its one trailing icon |
| `the tab row is its own row and owns the editor menu` | `EditorTabBar` + the menu + `SpckIcons.EditorMenu` sit in the row, and the row sits **below** `title = {` |
| `the bottom bar is still composed` | `!hideNav -> FlatBottomBar(` is still in `MainActivity.kt` (the roadmap's exit line) |
| `markers appear in the order the chrome declares them` | re-ordered declaration: run action, then tab row |

## 5. Deliberately not done

* **No ✕ in the top row.** 122157 has none; “Close file” stays in the cell's list.
* **No new toolbar.** Everything the phase removed is one tap away in the cell.
* **The Test ▷ row is untouched.** The shots show an HTML file, so the reference is silent
  about test files; removing a working control on silence is invention.
* **The drawer, the output panel, the find bar, the keys row: untouched.**
