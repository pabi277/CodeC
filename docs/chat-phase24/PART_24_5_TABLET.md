# CodeC Phase 24.5 — Tablet Two-Pane Layout (WindowSizeClass)

**Status:** ⏸ **DEFERRED** (structural EditorScreen refactor; awaiting owner tablet confirmation) · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** nothing
· **Target files:** `MainActivity.kt`, `ui/screens/EditorScreen.kt`,
  `ui/screens/FileManagerScreen.kt` (or `ProjectsHub.kt`)

---

## 1. Design

On phones (compact width): current single-column layout — no change.
On tablets / foldables (medium/expanded width ≥ 600 dp): file tree drawer
becomes a **persistent left pane** (280 dp); the editor fills the right.

```
┌─────────────┬──────────────────────────────────┐
│  File Tree  │  Editor                          │
│  (280 dp)   │  tabs + BasicTextField + status  │
│             │                                  │
│  project/   │  #include <stdio.h>              │
│  ├ main.c   │  int main() {                    │
│  └ utils.c  │      printf("Hello\n");          │
│             │      return 0;                   │
│             │  }                               │
│─────────────│──────────────────────────────────│
│             │  Output Panel (collapsed strip)  │
└─────────────┴──────────────────────────────────┘
```

### Implementation

```kotlin
// In MainActivity (or the nav graph root):
val windowSizeClass = calculateWindowSizeClass(this)

// Pass to EditorScreen via the nav graph or a CompositionLocal:
CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
    MainApp()
}

// In EditorScreen:
val windowSizeClass = LocalWindowSizeClass.current
val isExpandedWidth = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

if (isExpandedWidth) {
    Row {
        // Persistent file tree (the existing NavDrawer content extracted as a Column)
        EditorFileTree(
            modifier = Modifier.width(280.dp).fillMaxHeight(),
            ...
        )
        // Editor body
        EditorBody(modifier = Modifier.weight(1f), ...)
    }
} else {
    // Phone layout: existing ModalNavigationDrawer + editor body
    ModalNavigationDrawer(...) { EditorBody(...) }
}
```

The **file tree content** (already implemented in the nav drawer for Phase 16)
is extracted into a shared `EditorFileTree` composable and used in both paths.

---

## 2. Implementation steps

1. Add `calculateWindowSizeClass(activity)` in `MainActivity` and pass down.
2. Extract the nav drawer file tree content into `EditorFileTree`.
3. In `EditorScreen`, branch on `isExpandedWidth` per §1.
4. Phone layout: unchanged (ModalNavigationDrawer wraps EditorFileTree).
5. Tablet layout: `Row { EditorFileTree(280.dp) + EditorBody(weight(1f)) }`.
6. Test on emulator at 600 dp width (tablet preset).

---

## 3. Exit condition

```text
1. On a tablet (or emulator at ≥ 600 dp width):
   EXPECT: file tree is always visible on the left; editor on the right.
   EXPECT: no hamburger menu / drawer needed to see files.
2. On a phone (compact width):
   EXPECT: existing layout unchanged (hamburger → drawer).
3. Rotate tablet to landscape: EXPECT: two-pane stays; no crash.
PASS = steps 1–3 behave as described.
```
