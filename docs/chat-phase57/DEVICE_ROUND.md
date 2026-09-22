# Phase 57 — device round (written, **not run**)

> **Status:** ✍️ written 2026-09-22 · **never run.** Nothing below may be quoted as
> “verified on device”. A host test cannot see a layout, so the roadmap's
> **“Device pass required: Yes”** stands for the whole phase.

Phone: the owner's own device, one hand, `docs/spck-ui/Screenshot_20260922_122157…` and
`…_124105…` open beside it for comparison.

| # | Question | How to check | Pass looks like | If it fails, the fix lives in |
|---|---|---|---|---|
| R1 | Does the top row match 122157? | Open an HTML file (or any single file). Keyboard down. | `☰` · the file's own mark · the file's name · `🔍` · a green ▶. **No** word RUN, **no** ⋮. | `EditorScreen.kt` title/actions |
| R2 | Is the tab row its own row, under the name? | Open two files. | Row 2 spans the width: the tab cells, then the trailing glyph cell at the right edge. The name stays visible above it. | `EditorScreen.kt:1614` |
| R3 | Do the retired controls still exist? | Tap the trailing cell. | The full list (Undo, Redo, keys row, Save, Rename, Format, Jump to line, Diagnostics, line endings, Run config, Close file…) opens. | the cell's `DropdownMenu` |
| R4 | Does ▶ still run, and does a locked ▶ still explain itself? | Tap ▶ on a runnable file; then reproduce the Phase 44 lock and tap again. | The job starts; the locked tap shows the lock's own sentence, not a dead button. | `RunButtonStyle` + `onRunTap` |
| R5 | Does the ▶ say it is working? | Run a long job. | The glyph becomes the small green spinner, then returns to ▶. TalkBack announces “Run” / running state. | `RunButtonStyle.showsRunning` branch |
| R6 | The blue drop | Tap into the code. | A blue drop appears under the caret and **drags to move the caret** (sora's own handle). It is not doubled, not offset, not a second caret. | `SoraEditorHost.kt:121,277` · `CodecPalette.CARET_HANDLE` |
| R7 | Does the drop survive a theme switch? | Settings → switch the editor theme, tap back into the code. | The drop is still the reference's blue. | the theme effect |
| R8 | The rows, keyboard down then up (the exit criterion) | Keyboard down: status line, then the touch row, then the bottom bar. Keyboard up: **no** status line, the chip row, the touch row, then the system keyboard. | As written, in that order, in both states. | `EditorScreen.kt:2229/2254/2371` |
| R9 | The chips only with a keyboard | With the keyboard down, type nothing and look at the row; then focus a file with many completions. | Down: key caps, **no** chips. Up (or CodeC Keys): chips dock as usual. | `StripContext.typingSurfaceUp` |

## What this round cannot answer

* 57.3 (the pill) is not built, so nothing here tests it.
* The tour's beats 2-4 still run inside the drawer; R1-R3 change the panel's *outside*, not
  the tour. A tour replay is still worth doing after this round (`docs/chat-phase55` P-round
  covers it).
* The bottom bar's look is Phase 56's, already on the bar itself; R8 only confirms the order
  above it.
