# CodeC Phase 48 — Nothing hides behind the keyboard

> **Status:** 📋 PLANNED (researched + specced, no code) · **Cost:**
> `[client-only]` · **Effort:** S/M · **Owner row (verbatim):** *"If the code is
> very big it's last line go under the keyboard, when i use a suggestion it go
> down and hide behind the keyboard"*

```text
  48.1  The caret is always above the keyboard, on every chrome change
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [48.1](PART_48_1_CARET_ABOVE_KEYBOARD.md) | `CaretVisibilityPolicy` + one sora call | S/M | 📋 PLANNED |

---

## What exists today (evidence, read 2026-09-12 against `main` @ `f3a6e32`)

The layout is **correct**; the scroll is **not being asked for**.

1. `MainActivity` opts into edge-to-edge (`enableEdgeToEdge()`,
   `MainActivity.kt:231`), the manifest keeps
   `android:windowSoftInputMode="adjustResize"` (`AndroidManifest.xml:70`).
2. The editor's outer column applies `.imePadding()`
   (`EditorScreen.kt:1016`), with the comment explaining exactly this contract
   (`:1004-1015`). So when the IME appears the column shrinks and the sora
   `AndroidView` inside the `weight(1f)` `Box` (`:1381-1384`) is **laid out
   smaller**.
3. **Nothing tells sora to bring the caret back into the new viewport.** sora
   scrolls on *selection* change — its own `ScrollEvent` documents
   `CAUSE_MAKE_POSITION_VISIBLE` as *"Caused by calling
   `CodeEditor#ensurePositionVisible(int, int)`. This can happen when this
   method is manually called or either the user edits the text…"*
   (`ScrollEvent.java:51`). A viewport resize is neither.
4. **CodeC never calls it.** `grep -rn "ensurePositionVisible\|ensureSelectionVisible"
   app/src/main` → **0 hits**. The public API exists and is used by other sora
   consumers (`SagerNet/sing-box-for-android`'s `ProfileCodeEditor.kt:238` calls
   `editor.ensurePositionVisible(position.line, position.column, true)`);
   signatures confirmed at `CodeEditor.java:2245/2256`.
5. Why *"when I use a suggestion it goes down"*: accepting a ghost or a chip is
   a VM-driven buffer edit replayed into sora as one batch
   (`SoraEditorHost`'s VM→sora path). The caret moves to a column/line that may
   be below the now-shorter viewport, and the strip/keys rows re-render in the
   same frame — so the caret ends up under the keyboard and stays there until
   the user scrolls by hand.

The five chrome changes that shrink the editor box, all of them real and all of
them un-handled today:

| Trigger | Where |
|---|---|
| IME appears / disappears | `imePadding()` (`:1016`), `imeVisible` (`:323`) |
| CodeC Keys appears / disappears | `codecKeysUp` (`:400`) |
| The keys/strip row toggles | `keysRowVisible` (`:356`) + the two `BottomStrip` sites (`:1572`, `:1683`) |
| The output panel expands / collapses | `outputExpanded` + `outputPanelHeight` (`:1622-1677`) |
| The status bar yields / returns | `caretPlaced && !imeVisible && !codecKeysUp` (`:1594`) |

## The fix, in one sentence

**Ask sora to keep the caret visible whenever the editor box's height changes** —
with a pure policy deciding *when*, and one Android call doing it *after* the
new layout pass.

```kotlin
// ui/editor/CaretVisibilityPolicy.kt   (pure, host-tested)
data class EditorViewport(val heightPx: Int, val imeVisible: Boolean, val keysVisible: Boolean,
                          val stripVisible: Boolean, val outputExpanded: Boolean,
                          val statusVisible: Boolean, val fontSizeSp: Float)
object CaretVisibilityPolicy {
    /** One-line-of-air rule: the caret must not sit on the last visible row. */
    const val KEEP_LINES_BELOW = 1
    fun owesRescroll(previous: EditorViewport?, current: EditorViewport): Boolean
    fun debounceMs(previous: EditorViewport?, current: EditorViewport): Long
}
```

Android edge (in `SoraEditorHost`, next to the existing `AndroidView`):

```kotlin
Modifier.onSizeChanged { size ->
    val vp = currentViewport(size)
    if (CaretVisibilityPolicy.owesRescroll(lastVp, vp)) {
        lastVp = vp
        editor.post { editor.ensurePositionVisible(line, column, /*noAnimation=*/true) }
    }
}
```

`editor.post { … }` (one layout pass later) is deliberate: calling it during
`onSizeChanged` runs against the *old* layout metrics and scrolls to the wrong
place — the same reason the flutter-quill workaround for the identical bug waits
for the keyboard to settle before re-triggering caret positioning.

## Why not the alternatives (each one rejected on evidence)

| Alternative | Why not |
|---|---|
| `adjustPan` in the manifest | Pans the **whole window**: the top app bar and RUN ▶ leave the screen. The platform itself calls pan *"generally less desirable… users may need to close the soft keyboard to get at obscured parts"*. |
| Extra bottom padding on the column | The column is already inset correctly; more padding would double-count the IME and push the editor off the top. |
| Hand-rolled scroll with `getCharOffsetY` + `scrollBy` | Re-implements sora's scroller, including word-wrap layout math. `getCharOffsetY` is already used for the ghost pill (`:1521-1533`) and is not a scrolling API. |
| Padding inside sora's text area | Changes selection geometry and the ghost-hint Y math. |
| Waiting for a sora release to fix it | The API to fix it is already public and already used by other apps. |

## Risks to watch

- **Scroll fights.** If the user has scrolled away from the caret to read
  something, an IME toggle should not yank them back. `owesRescroll` therefore
  fires on **height change only**, never on a timer, and never on a plain
  selection change (sora already handles those).
- **Very short viewports.** Editor + IME + CodeC Keys + expanded output panel on
  a 5" screen can leave a handful of rows. `KEEP_LINES_BELOW` must degrade
  gracefully: if the viewport cannot show the caret plus one line, the caret
  alone wins. That case is in the device matrix (Phase 50), not guessed here.
- **Word wrap on.** With `wordWrap` the visual row ≠ the logical line; sora's
  own `ensurePositionVisible` handles that (it is layout-aware), which is the
  second reason not to hand-roll it.
- **Animation jank.** `noAnimation = true` for chrome-driven rescrolls: an
  animated scroll on every keyboard toggle is seasickness. Animated scrolling
  stays for user-driven jumps (find-next, go-to-line, diagnostic jump).

## Exit condition

```text
1. Open a 300-line file, scroll to the LAST line, tap into it, open the
   keyboard: the caret line is fully visible above the keyboard.
2. Same file, accept a ghost suggestion near the bottom: the caret stays visible.
3. Toggle CodeC Keys ON/OFF with the caret near the bottom: it stays visible
   both ways.
4. Expand the Output Panel with the caret near the bottom: the caret stays
   visible (or the panel is the only thing on screen, which is the user's
   choice).
5. Hide/show the keys row from the ⋮ menu: caret stays visible.
6. Scrolled 100 lines away from the caret, open the keyboard: the view does NOT
   jump to the caret.
7. Word wrap ON, long line, caret at the end: visible after the keyboard opens.
8. Repeat 1-5 with the system keyboard (47.2 default) and with CodeC Keys.
PASS = all eight, on a small screen and a large one.
```

## Tests (plan)

- `CaretVisibilityPolicyTest` (host): `owesRescroll` true for each of the five
  triggers in both directions; false when only unrelated state changed; false
  for a null `previous` (first composition — sora places the caret itself on
  open); `KEEP_LINES_BELOW` behaviour when the viewport is smaller than the
  margin; debounce values (IME animation gets one coalesced call, not 30).
- `ViewportSnapshotTest` (host): the five inputs map to the right viewport
  struct for each combination that `EditorScreen` can actually produce (an
  impossible combination — e.g. `keysVisible && imeVisible` with CodeC Keys
  owning input — is normalised, not represented twice).
- Source-scan test: `SoraEditorHost.kt` contains exactly one
  `ensurePositionVisible(` call site, inside a `post { }` — the guard against a
  second, unsynchronised caller.
- **Not unit-tested, on purpose:** real pixel positions after a real IME
  animation. That is Phase 50's device matrix, and the doc says so instead of
  faking a layout.

## Sources

- CodeC 2026-09-12: `EditorScreen.kt:323,356,400,1004-1016,1381-1384,1521-1533,
  1572,1594,1622-1683`, `AndroidManifest.xml:70`, `MainActivity.kt:231`,
  `ui/editor/sora/SoraEditorHost.kt` (the VM↔sora bridge).
- `Rosemoe/sora-editor` (upstream `main`, via the grep.app code index,
  **re-verified 2026-09-12**): `CodeEditor.java:2245` —
  `public void ensurePositionVisible(int line, int column)`; `:2256` —
  `public void ensurePositionVisible(int line, int column, boolean noAnimation)`;
  `ScrollEvent.java:51` — `public final static int CAUSE_MAKE_POSITION_VISIBLE = 3;`,
  whose only dispatch site is `CodeEditor.java:2313-2314` *inside
  `ensurePositionVisible`*. **These line numbers are upstream `main`, not the
  0.24.6 pin CodeC ships — match on method names, not line numbers.**
- A real consumer of the same API:
  `SagerNet/sing-box-for-android` `ProfileCodeEditor.kt:238`.
- The identical bug and its "wait for the keyboard to settle, then re-trigger
  caret positioning" workaround in another editor:
  [github.com/singerdmx/flutter-quill/issues/2137](https://github.com/singerdmx/flutter-quill/issues/2137).
- `docs/PHASE44_50_UX_RESEARCH.md` §5.
