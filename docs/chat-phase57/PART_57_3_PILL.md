# PART 57.3 — one pill, for the short messages the shots show

**Files touched**

| File | What changed |
|---|---|
| `app/src/main/java/com/codeci/ide/ui/editor/NoticePolicy.kt` | **new** — the pure decisions: `NoticeKind`, `AUTO_DISMISS_MS` (:44), `refreshNotice` (:51), `openFailureNotice` (:59) |
| `app/src/main/java/com/codeci/ide/ui/components/PillNotice.kt` | **new** — the pill itself (:47), one line + one glyph on Phase 51.4's `PressableSurface` (:64) |
| `app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt` | observes the notice and owns its timer (:613-617); the tree's own Refresh goes through the user-asked path (:1399); the pill renders above the snackbar slot (:2457-2471) |
| `app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt` | the notice flow + `refreshFilesFromUser` (:326-352); **eight silent `?: return`s on the open paths now report** (cites in §2) |
| `app/src/main/res/values/strings.xml` | `notice_files_refreshed` (:365), `notice_file_open_failed` (:366) |
| `app/src/test/java/com/codeci/ide/NoticePolicyTest.kt` | **new** — the policy's five laws (which message, when, and how long) |
| `app/src/test/java/com/codeci/ide/PillNoticeWiringTest.kt` | **new** — the five wiring pins below |

## 1. What the shot shows

`docs/spck-ui/Screenshot_20260922_122157_Spck Editor.jpg`, reread before this part was written:
one **pill** floats above the editor's bottom edge, above the status line and the touch row —
a rounded dark surface, one small blue badge, one bold line: **“Refreshed Files”**. It is not a
dialog (it asks nothing), not a dump (it scrolls nothing), and not a system toast (it belongs
to this surface, in this spot).

Two decisions taken from the pixels, both recorded because neither is in a sentence anywhere:

* **The badge is the store's own mark, so the clean room does not copy it.** The pill carries
  the app's glyph for the same idea — the refresh arrow — tinted the reference's blue
  (`CodecPalette.INFO`, `PillNotice.kt:61`). An unopenable file keeps the app's established
  danger red (`CodecPalette.DANGER`), so the two pills can never be mistaken for each other at
  a glance.
* **It sits above the snackbar's slot, not in it** (`EditorScreen.kt:2457-2471`). CodeC already
  has a short-message surface — `userMessage` → `SnackbarHost` (:801, the save/rename/error
  messages). The pill is a *second* surface on purpose: the shot's container is a pill inset
  from the bottom, the M3 snackbar is a 4 dp-cornered bar at the screen edge, and a save the
  user did not ask for must not queue behind a refresh the user did ask for. The two can never
  overlap, because the pill is laid out a full touch row above the snackbar's padding.

## 2. “Error opening file.” — the dead taps this part closes

The roadmap asked for a pill for two messages. One of them was already possible; the other was
not, because **the failure never spoke at all**. On this checkout, every open path returned in
silence:

```kotlin
// EditorViewModel.kt — before this part (the same shape eight times)
val safe = ProjectPathUtils.sanitizeRelativePath(relativePath) ?: return
val info = ProjectManager(context).project(projectName) ?: return
val file = ProjectPathUtils.resolveInside(info.root, safe) ?: return
if (!file.isFile || !file.canRead()) return
val content = runCatching { file.readText() }.getOrNull() ?: return
```

A tap on a file that had been deleted behind the editor, or on a name that could not be
resolved, did **nothing** — no pill, no dialog, no dump. All eight now go through one handler
(`failOpen` → `NoticePolicy.openFailureNotice`), at `EditorViewModel.kt:1324, 1325, 1364, 1365,
1366` (project open), `:1399, 1400, 1401, 1415` (the single-file peek), and `:1445, 1452`
(scratch files). Twelve call sites and one declaration: thirteen `failOpen(` matches, pinned by
`PillNoticeWiringTest` so a future edit cannot quietly reintroduce `?: return`.

Also unchanged on purpose: the early return when there is **no file name at all**
(`openFile`, :1317) still says nothing — the chrome never speaks about nothing, and
`openFailureNotice(null)` answers `null` for the same reason.

## 3. “Refreshed Files” — and the no-nag law that comes with it

The tree is re-read on a dozen occasions that the user did not ask for: opening the drawer,
saving, creating, deleting, renaming, the git meta pass. One occasion is a request: the tree
toolbar's **own Refresh** (`EditorProjectDrawer.kt:374-376`, `Icons.Default.Refresh`,
`R.string.refresh`). That one — and only that one — now confirms itself.

```kotlin
// EditorViewModel.kt:349
fun refreshFilesFromUser(context: Context) {
    refreshFileEntries(context)   // the tree + the drawer header's launch default
    refreshGitMeta(context)       // the git line rides the same pass, as its call sites always did
    noticeFor(NoticePolicy.refreshNotice(userAsked = true))
}
```

That is the rule Phase 41/42/45 already set for saving — *a save the user did not ask for does
not deserve an interruption* — applied to the tree. It is a decision, not a comment, so
`NoticePolicy.refreshNotice(userAsked = false)` is pinned to `null`.

`NoticePolicy.AUTO_DISMISS_MS` is 2.4 s (bounds pinned: ≥ 1.5 s so the pill is readable,
≤ 4 s so it does not become chrome), and the screen's timer is **keyed on the message**
(`LaunchedEffect(notice)`, `EditorScreen.kt:614`), so two refreshes in a row each get their
full window instead of the second inheriting the first's remainder. A tap dismisses it early
(`PillNotice`, `onDismiss = { viewModel.clearNotice() }`) and performs nothing else — the
gesture the shot's pill invites, and no guessed action.

## 4. Why the pill is a new composable (and what it deliberately is not)

| Candidate | Verdict |
|---|---|
| M3 `Snackbar` (the app's existing short-message surface) | **Rejected** — 4 dp corners at the very bottom edge is not the shot's pill, and it queues behind save messages. |
| `AlertDialog` | **Rejected** — a dialog asks; these two messages have nothing to ask. |
| `Toast` | **Rejected** — outside the surface, outside the theme, outside the screen's lifetime. |
| A new ad-hoc surface | **Rejected** — the pill renders through **`PressableSurface`** (51.4), so it keeps the app's one shape/container/press vocabulary, the token radius, and the 48 dp floor. |

## 5. Pins, and what ran

`NoticePolicyTest` (5) — an automatic refresh stays silent; the user's own refresh confirms
itself; a file that cannot open is never a dead tap; nothing is said about nothing; the
dismissal window is neither instant nor permanent.

`PillNoticeWiringTest` (5) — the editor renders the pill, reads the VM's one notice flow, and
clears it on `NoticePolicy.AUTO_DISMISS_MS` or on a tap; the pill is not a dialog and not a
toast, and renders nothing literal itself (the wording lives in `strings.xml` — the test scans
the composable with `RepoFiles.codeOnly`, so its own kdoc arguing *against* a dialog cannot
satisfy the pin); the tree's Refresh is the user-asked path; every open failure reports and the
message is the policy's; both strings are worded as this part wrote them.

**Ran in the sandbox** (no Gradle here, `kotlinc` + the host JUnit shims):

```text
RunnerKt com.codeci.ide.NoticePolicyTest com.codeci.ide.PillNoticeWiringTest
RESULT: 10 passed, 0 failed
```

Compiled to `/tmp/p573/out` from `app/`. `PillNotice.kt` itself is Compose and cannot compile
outside the Android toolchain, so the pill's own *rendering* is proven by CI (`Run host unit and
screenshot tests`) and its *existence and wiring* by the scan pins above. The look is a device
question: see `DEVICE_ROUND.md` R10-R11.

## 6. Not in this part

* The pill is not a general notification system. Only these two messages use it; the snackbar
  keeps saving, renaming and deleting, and the dialogs keep asking their questions.
* No new glyph beyond the two icon roles above; the shot's badge is not copied (§1).
* 57's exit criterion is unchanged and still a device question: keyboard down matches `122157`,
  keyboard up matches `124105`, the four-tab bar still under both.
