# CodeC Phase 43 — ❌ CANCELLED (2026-09-12) · "Open a folder" is removed from the app

> **Status:** ❌ **CANCELLED BY THE OWNER** — never implemented (it was
> 📋 PLANNED only; zero app code ever existed for it). ·
> **Owner (2026-09-12, verbatim):** *"The project have a feature open a folder
> (phase 43, incomplete) i want to remove it completely and make the project
> section more optimization features like file single click to open in a editor
> screen with real path and same file edit but not full project to editor. To
> open a full project in editor the 3 dot will have the option to open in
> editor."*
>
> The replacement work is **Phase 46 — Projects, not folders**
> ([`../chat-phase46/`](../chat-phase46/README.md)). The roadmap for the whole
> test-phase series is [`../PHASE44_50_ROADMAP.md`](../PHASE44_50_ROADMAP.md).

---

## What was cancelled, exactly

Phase 43 was two planned parts, both **spec-only**:

| Part | Title | What it promised | Fate |
|---|---|---|---|
| 43.1 | "Open folder" that cannot crash | Replace `ProjectTransfer.copyDocumentTree`'s unbounded recursion with a bounded, cancellable, `Throwable`-safe walk | ❌ **Not needed** — the feature it hardened is deleted in 46.1 |
| 43.2 | Open a folder as a project (link + sync) | A `ProjectLink` record, a persisted SAF grant, two-way sync between a user folder and a CodeC working copy | ❌ **Cancelled** — the owner does not want folder linking |

Both part docs (`PART_43_1_SAFE_FOLDER_WALK.md`,
`PART_43_2_OPEN_FOLDER_AS_PROJECT.md`) are **deleted in the same commit** as
this tombstone, on the owner's *"remove it completely"*. Their full text is in
git history (`docs/chat-phase43/` at `f3a6e32` and earlier) — nothing is lost,
and this file records the parts a future chat must not silently resurrect.

## What Phase 46 deletes from the *app* (the code that exists today)

| File:line (verified 2026-09-12, `main` @ `f3a6e32`) | What it is |
|---|---|
| `FileManagerScreen.kt:180-188` | `folderImportLauncher` (`ActivityResultContracts.OpenDocumentTree()`) |
| `FileManagerScreen.kt:1077-1080` | the `+`-sheet's `onOpenFolder` branch |
| `FileManagerScreen.kt:1474-1481` | the "Open Folder" row itself (green tile, `SpckIcons.FolderLine`) |
| `FileManagerScreen.kt:1431` | the `onOpenFolder` parameter of `ProjectsHubAddSheet` |
| `FileManagerViewModel.kt:357-382` | `importFolder()` — creates an empty project, copies the tree, `catch (e: Exception)` |
| `ProjectTransfer.kt:20-29` | `copyDocumentTree()` |
| `ProjectTransfer.kt:313-345` | `copyDocumentChildren()` — the unbounded recursion (no visited set, no depth/file/byte budget, no cancel, no progress) |
| `strings.xml:348-349` | `hub_sheet_folder` ("Open Folder") + `hub_sheet_folder_subtitle` ("Pick an existing folder") |

**Kept on purpose** (they are not part of 43 and the owner did not ask for them
to go): `Import ZIP`, `Import file` (into the open project), `Export ZIP`,
"Export all projects", and the Phase 24.7 `Open with CodeC` intent filters
(`AndroidManifest.xml` `ACTION_VIEW`/`ACTION_SEND` for text, python and zip).

**Test impact:** none. `grep -rln copyDocumentTree app/src/test/` returns
nothing — no host test covers the folder walk, which is itself part of why the
crash survived to a device.

## Why this is a good cancellation, not just an owner preference

1. **It promised less than it appeared to.** `importFolder` is a **one-way
   copy**: after it, the user's folder and CodeC's copy are unrelated — no link,
   no write-back. Phase 43's own README admitted it (*"one-way copy into a new
   project"*), and 43.2 existed only to fix that. Without 43.2 the row in the
   `+` sheet over-promises.
2. **It was the app's only unbounded recursion.** A document provider that lists
   a parent among its own children (documented for provider-backed "directories"
   such as *Downloads*) makes `copyDocumentChildren` recurse forever; the
   resulting `StackOverflowError` is an `Error`, and the VM catches only
   `Exception` (`FileManagerViewModel.kt:375`) — so it kills the app with no
   message. Deleting the caller deletes the crash class.
3. **It needed a permission story CodeC does not have.**
   `takePersistableUriPermission` has **zero call sites** in `app/src/main`
   (verified 2026-09-12), so nothing SAF-selected survived process death; the
   feature only ever worked until the next launch.
4. **The owner's replacement is a better product.** "Tap a file → edit that
   file; ⋮ → Open in editor → the whole project" (Phase 46.2) covers the real
   need — *get at my code fast* — without SAF, without a copy, and without a
   second project model to explain.

## The one thing a future chat must remember

If anyone ever asks for "open my SD-card folder as a project" again, the honest
answer is in git history: `noexec` on emulated storage means anything CodeC
**executes** must live in app-private storage, so an in-place SAF project would
need a mirror anyway, plus `takePersistableUriPermission`, plus a
`ProjectManager.project()` audit (today it refuses any root whose canonical
parent is not `projectsRoot()`). That is a large phase, not a row in a sheet.
Do not re-add the cheap version — it is the version that crashed.
