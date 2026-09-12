# CodeC Phase 48.1 — `CaretVisibilityPolicy` + one call to sora

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S/M ·
> **Owner row (verbatim):** *"If the code is very big it's last line go under the
> keyboard, when i use a suggestion it go down and hide behind the keyboard"*

## First move: evidence, not code

`rule.md` §4.2 applies. Before writing the policy, reproduce on a device and
record which of the five triggers actually moves the caret out of view:

1. open the IME with the caret on the last line;
2. accept a suggestion there;
3. toggle CodeC Keys;
4. expand the Output Panel;
5. toggle the keys row.

Expected from the code reading: 1, 2 and 3 reproduce; 4 and 5 probably do (same
mechanism). If one of them does **not** reproduce, that is information — it
means sora already re-scrolls for that case and the policy must not fire for it
(a rescroll that is not owed is a scroll fight).

## The pure policy

```kotlin
// app/src/main/java/com/codeci/ide/ui/editor/CaretVisibilityPolicy.kt
data class EditorViewport(
    val heightPx: Int,
    val imeVisible: Boolean,
    val codecKeysVisible: Boolean,
    val stripVisible: Boolean,
    val outputExpanded: Boolean,
    val statusVisible: Boolean,
    val fontSizeSp: Float
) {
    /** The editor's own box is everything except the rows below it. */
    val editorHeightPx: Int get() = heightPx   // caller passes the editor Box's height
}

object CaretVisibilityPolicy {
    const val KEEP_LINES_BELOW = 1
    /** IME/keys animations report many sizes; coalesce them. */
    const val RESCROLL_DEBOUNCE_MS = 90L

    fun owesRescroll(previous: EditorViewport?, current: EditorViewport): Boolean {
        if (previous == null) return false          // first layout: sora placed the caret
        if (previous.editorHeightPx == current.editorHeightPx &&
            previous.fontSizeSp == current.fontSizeSp) return false
        return true                                  // the box changed size → the caret may be under it
    }

    /** A shrinking box is urgent; a growing one can wait for the animation. */
    fun debounceMs(previous: EditorViewport?, current: EditorViewport): Long =
        if (previous != null && current.editorHeightPx < previous.editorHeightPx) 0L
        else RESCROLL_DEBOUNCE_MS
}
```

Why the policy is about **height** and not about the five booleans: the booleans
are the *cause*, the height is the *effect*, and only the effect can hide the
caret. Testing the height means a future chrome row (a find bar, a diagnostic
strip, anything) is covered without touching this file — the same reason
`NavBarPolicy` takes `imeVisible`/`keysVisible` rather than reading them itself.

## The Android edge

In `SoraEditorHost` (which already owns the `AndroidView` and the sora
instance), on the editor `Box`:

```kotlin
var lastViewport by remember { mutableStateOf<EditorViewport?>(null) }
Modifier.onSizeChanged { size ->
    val vp = EditorViewport(
        heightPx = size.height, imeVisible = imeVisible, codecKeysVisible = codecKeysUp,
        stripVisible = keysVisible, outputExpanded = outputExpanded,
        statusVisible = statusBarVisible, fontSizeSp = fontSize
    )
    if (CaretVisibilityPolicy.owesRescroll(lastViewport, vp)) {
        lastViewport = vp
        val at = editor.cursor.left()          // sora's own caret
        editor.postDelayed({
            runCatching { editor.ensurePositionVisible(at.line, at.column, true) }
        }, CaretVisibilityPolicy.debounceMs(lastViewport, vp))
    } else {
        lastViewport = vp
    }
}
```

Four details that decide whether this works:

1. **`editor.post { }` / `postDelayed`**, never a direct call — during
   `onSizeChanged` sora's layout still holds the old metrics.
2. **`noAnimation = true`** for chrome-driven rescrolls (animated scrolling
   stays for user jumps: find-next, go-to-line, diagnostic tap).
3. **`runCatching`** — the editor can be mid-`release()` when a size change
   lands (tab switch); a throw here would be a crash for a cosmetic call. The
   "no user-facing action may kill the app" law from 44 applies to a layout
   callback too.
4. **One call site.** The source-scan test pins it: exactly one
   `ensurePositionVisible(` in `app/src/main`, inside a post. A second caller
   means two owners of the scroll position, which is how scroll bugs become
   unfixable.

## Exit condition

The eight device checks in the phase README. The two that matter most:

- **#1** — 300-line file, caret on the last line, keyboard opens, caret visible.
  This is the owner's exact sentence.
- **#6** — scrolled away from the caret, keyboard opens, the view does **not**
  jump. This is the check that proves the policy is narrow enough.

## Tests (plan)

- `CaretVisibilityPolicyTest` (host, ~14 cases): null previous → false; same
  height → false; height −1 px → true; height +1 px → true; font-size change at
  the same height → true; every boolean changing with the same height → false
  (**the narrowness pin**); debounce 0 when shrinking, `RESCROLL_DEBOUNCE_MS`
  when growing; `KEEP_LINES_BELOW == 1`.
- `ViewportSnapshotTest` (host): `EditorScreen`'s reachable combinations map to
  distinct viewports; the two mutually exclusive states
  (`codecKeysVisible && imeVisible` while CodeC Keys owns input) normalise to one.
- Source-scan test (`CaretCallSiteTest`): exactly one `ensurePositionVisible(`
  in `app/src/main`, in `SoraEditorHost.kt`, inside a `post`/`postDelayed`,
  wrapped in `runCatching`.
- Existing editor tests untouched (`EditorCursorMathTest`, `ScrollMathTest`,
  `SafeZoneMathTest`) — this part adds a scroll *request*, it changes no math.

## Sources (record)

- CodeC 2026-09-12: `EditorScreen.kt:323,356,400,1004-1016,1381-1384,1572,1594,
  1622-1683`; `ui/editor/sora/SoraEditorHost.kt` (AndroidView + bridge);
  `AndroidManifest.xml:70`.
- sora API, verified via the grep.app code index 2026-09-12:
  `CodeEditor.java:2245/2256` + the dispatch at `:2313-2314`,
  `ScrollEvent.java:51` (upstream `main`; match method names, not lines);
  consumer example
  `SagerNet/sing-box-for-android` `ProfileCodeEditor.kt:238`.
- The same bug + settle-then-reposition workaround in flutter-quill
  ([issue 2137](https://github.com/singerdmx/flutter-quill/issues/2137)).
- `docs/PHASE44_50_UX_RESEARCH.md` §5.1-5.3 (mechanism, fix shape, the honest
  caveat about very short viewports, and the rejected alternatives).

## Deferred / rejected with reasons

- **`adjustPan`** — hides the app bar; platform docs call it less desirable.
- **Sticky-scroll / pinned last line** — a different feature (VS Code's
  `editor.cursorSurroundingLines` analogue); `KEEP_LINES_BELOW` is the cheap
  80 % and needs no renderer work.
- **Scrolling the *selection* instead of the caret** (`ensureSelectionVisible`) —
  wrong for a collapsed caret at the end of a long line and no better otherwise;
  `ensurePositionVisible(line, column)` is the precise one.
- **Waiting for a sora fix** — the API is public and already used elsewhere.
