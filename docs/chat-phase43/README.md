# CodeC Phase 43 — File system strength (open any folder, never crash)

> **Status:** 📋 PLANNED (researched + specced, no code) · **Cost:**
> `[client-only]` · **Effort:** L · **Owner row:** *"Now the file system is good
> but i want it more stronger, i can't open a project in the editor from project
> folder, open a folder crash the app"*

```text
  43.1  "Open folder" that cannot crash (safe SAF walk)
  43.2  Open a folder as a project (ProjectLink + link/sync, in place where legal)
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [43.1](PART_43_1_SAFE_FOLDER_WALK.md) | Crash-proof folder import + cancel/progress | M | 📋 PLANNED |
| [43.2](PART_43_2_OPEN_FOLDER_AS_PROJECT.md) | Open a folder as a project, in place | L | 📋 PLANNED |

## What exists today (evidence, read 2026-09-10)

- **Projects live in app-private storage, enforced in code.**
  `ProjectManager.projectsRoot()` is `filesDir/CodeC/projects`, and
  `project(name)` refuses anything whose `canonicalFile.parentFile` is not
  exactly that root — so **by construction, no folder anywhere else on the
  device can be a project**. `FileManager.getProjectDir()` documents *why* the
  fallback candidates are a last resort: emulated storage (including
  `getExternalFilesDir`) is mounted **`noexec`**, so `./a.out` dies with
  "Permission denied" even after a successful `cc` — the same reason Termux
  keeps `$HOME` under `/data/data`.
- **The one way in is a copy, and it is fragile.** Hub → *Open folder*
  (`FileManagerScreen:1013-1016`, `ActivityResultContracts.OpenDocumentTree`) →
  `FileManagerViewModel.importFolder`, which creates an empty project and runs
  `ProjectTransfer.copyDocumentTree`. That walker
  (`ProjectTransfer.copyDocumentChildren`) is a **plain recursion per
  directory** with: no visited set, no depth limit, no file-count/byte budget,
  no cancellation, no progress, and it opens every file's stream serially.
  `importFolder` catches `Exception` only. A file provider that lists the
  parent among its own children (documented for provider-backed "directories"
  like *Downloads*) therefore produces a `StackOverflowError` — an `Error`, not
  an `Exception` — which sails past the `catch` and **kills the app**. That is
  the owner's second sentence, and it is a plausible, code-visible cause; 43.1
  must still confirm it on device with the crash record before fixing (no
  blind patch).
- **Compare the ZIP path, which is already guarded**: `importZip` has
  `MAX_ZIP_ENTRIES = 10_000`, `MAX_ZIP_ENTRY_BYTES = 128 MB`, a total-bytes
  cap, a path-escape check per entry, and `finally { temporaryZip.delete() }`.
  The tree walk deserves the same maturity and nothing more exotic.
- **The picker is half-right: the contract is correct, the persistence is
  missing.** `FileManagerScreen.kt:177-185` uses
  `ActivityResultContracts.OpenDocumentTree()` — the *only* action whose grant
  can be persisted — and hands the URI to `viewModel.importFolder(context, uri)`
  which copies and forgets. `takePersistableUriPermission` has **zero call
  sites** in the app (verified 2026-09-10), so nothing SAF-selected survives
  process death — which is why "link a folder" is impossible today and why
  import-by-copy is the only story. 43.2 adds the call; 43.1 does not (one-shot
  copy needs no lingering permission).
- **Storage is already two-track, and 39 must not pretend otherwise.**
  `MainActivity.kt:354-360`, `SettingsScreen.kt:846-852` and
  `ShellEnvironment.kt:1771-1775` each check `Environment.isExternalStorageManager()`
  and route to `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` — i.e. CodeC
  **already declares and already offers** `MANAGE_EXTERNAL_STORAGE`
  (`AndroidManifest.xml:12`, with `READ_EXTERNAL_STORAGE` and
  `WRITE_EXTERNAL_STORAGE maxSdkVersion="32"` as the legacy pair). So plain
  `File` access to `/sdcard` is a supported path here, not a fallback: 43's
  walker therefore takes a `File` **or** a `Uri` and never proposes changing that
  permission story (42.3 audits the list instead, honestly).
- **The in-app file tree has no such action at all**: a directory row's ⋮ menu
  offers New file / New folder / Rename / Delete — there is no "use as
  project", "open here", or "link".

## Research that shaped the design

Dossier: [`../PHASE38_43_OSS_RESEARCH.md`](../PHASE38_43_OSS_RESEARCH.md) §2.
The short version: `androidx.documentfile` (Apache-2.0) is the platform's own
helper and its `TreeDocumentFile` is exactly the shape CodeC already avoids for
performance (one provider query per child; `findFile()` is O(children) queries
and sub-tree roots lose write permission) — CodeC keeps its
`DocumentsContract`-based walk and *fixes* it. `MANAGE_EXTERNAL_STORAGE`, Rclone
and third-party pickers were rejected. Persisting the grant
(`takePersistableUriPermission`) is mandatory for anything that must survive a
reboot; the flags on the *intent* are not the grant.

## The two rules this phase must not break

1. **`noexec` is physics.** A project's *sources* may live outside
   `filesDir`; anything that must be **executed** (a compiled binary, a
   `python3` child run against it) runs from app-private storage. 43.2's
   mirror exists only for that, and the phase states it as such instead of
   pretending the app edits the SD card in place.
2. **A crash is a failure of the boundary, not of the provider.** Every
   SAF-facing entry point ends in `catch (t: Throwable)` → a message + a
   cleanup, and *no unbounded recursion, anywhere*. If a host test can feed a
   cyclic tree and the code does not terminate, the phase is not done.

## Exit condition (device matrix matters here)

```text
1. A normal folder (~2 000 files, nested) imports with a progress line and a
   final count; tapping Cancel leaves no half-copied project behind.
2. Pick *Downloads* (a provider, not a directory) → a clear message, no crash.
3. Pick a huge tree (a folder with a node_modules or a 1 GB video) → stopped
   by a budget with the reason named; the app stays alive; nothing partial.
4. Force-stop the app mid-import, relaunch → no ghost project in the hub.
5. (43.2) Open a folder that already has code from `Download/` (or an SD card)
   as a project: it appears in the hub, edits, and RUN works — python/HTML
   directly, C through the mirror — and saving syncs back to the same folder.
6. (43.2) Kill the app and reopen: the linked folder still works (persisted
   grant). Revoke the permission in system settings: CodeC says "the link to
   <folder> is gone" and offers to re-pick — it must not crash and must not
   silently edit a stale mirror.
PASS = 1-4 for 43.1; 5-6 additionally for 43.2.
```

## Risks to watch (multi-device round)

- Provider behaviour differs by OEM (AOSP DocumentsUI vs Samsung *My Files* vs
  Xiaomi's file manager vs Total Commander): the budgets must be enforced by
  CodeC, never trusted from the provider.
- Cloud providers (Google Drive, Nextcloud) return URIs whose reads fail
  mid-stream (`FileNotFoundException`, `SocketTimeoutException`): per-file
  failure must skip-and-report, never abort the whole import.
- Android 11+ scoped storage means the app can *see* nothing outside its dirs
  without SAF: no code path may assume `/storage/emulated/0/…` is readable
  just because `ShellEnvironment` can `ls` it in the terminal (different
  mechanism, and users will notice the inconsistency).
- Symlink-ish documents (`application/vnd.document.android.document` oddities)
  and `OTAs`: never follow a child URI built from an *un*sanitised name —
  `ProjectPathUtils.resolveInside` stays the only way to make a target path.

## Deferred, recorded on purpose

- **Editing an SD-card project fully in place** (no mirror) — needs every
  editor/git/process path to speak `ContentResolver`, gives up local
  `exec`/`cc`, and makes `git status` on a slow provider unusable. 43.2's
  mirror-and-sync is the honest 80 %.
- **"Storage access" via `Environment.isExternalStorageManager()`** —
  over-broad and Play-sensitive; never.
- **A custom full-featured file manager** replacing `FileManagerScreen` —
  out of scope; SAF is the file manager.
- **Watching the linked folder for external changes** (`FileObserver` /
  `ContentObserver` on the tree) — deferred; 43.2 ships an explicit
  "Re-check folder" action instead of a background observer that will miss
  events on some OEMs.
