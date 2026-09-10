# CodeC Phase 43.2 — Open a folder as a project (link + sync), in place where it is legal

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** L ·
> **Owner row (verbatim):** *"i can't open a project in the editor from project
> folder"*

## Symptom

Everything the user already has — a folder from a course, a USB-drive dump, a
`Download/school/` tree, a project another app created — cannot be opened. The
only entry points are *New project*, *Clone*, *Import ZIP* and *Open folder*,
and "Open folder" (43.1) is a **one-way copy into a new project**: after it,
the user's folder and CodeC's copy are unrelated. Nothing can be added to the
hub from an existing folder, nothing can be saved back, and nothing can be
opened from any of the `FileManager.projectDirCandidates()` roots other than the
single primary one (`ProjectManager.project()` refuses a whose canonical parent
is not `projectsRoot()`).

## The honest constraint (state it before designing)

**A project's files must stay reachable as real `File`s for everything else in
the app.** The editor's buffer host, `ProjectScaffold`, `GitManager` (which
shells out to `git` with `workingDir = root`), `CompilerService`,
`WebPreviewServer` and `ServerHost` all take `java.io.File`; sora's `FileModel`
memory-maps a path; `noexec` on emulated storage means *anything executed*
must come from app-private storage (see `FileManager.getProjectDir()`'s comment
and this phase's README, "noexec is physics"). A pure "edit the SAF tree in
place" project type would mean replacing `File` with `ContentResolver` in
every one of those, plus a keystroke-latency problem (a provider query per
read) — a rewrite with a worse result. So:

> **Working copy in, source of truth linked, sync in both directions.**
> The project still lives under `projectsRoot()` (so nothing else in the app
> changes), and the linked tree becomes a first-class *remote* that CodeC keeps
> in step — the same shape `git` gives you (local clone + `origin`), which is
> also what the user already understands.

## Design

**1. A link record.** `ui/projects/ProjectLink.kt` (pure) + persistence in the
existing DataStore-backed settings store (no new file format invented):

```kotlin
data class ProjectLink(val projectName: String, val treeUri: String, val displayName: String,
                       val addedAt: Long, val lastSyncAt: Long?, val writable: Boolean)
object ProjectLinkPolicy {
    sealed interface Action { object InSync; data class Push(val paths: List<String>) : Action   // project → folder
                              data class Pull(val paths: List<String>) : Action                   // folder → project
                              data class Conflict(val paths: List<String>) : Action              // both changed
                              data class Lost(val reason: LostReason) : Action }                   // revoked/unmounted
    fun decide(project: Map<String, Stamp>, folder: Map<String, Stamp>): Action   // Stamp = size + mtime + exists
}
```

`decide` is the only place "who wins" is answered, and it never deletes:
`Conflict` lists files and the UI offers *Keep mine / Take theirs / Show diff*
(CodeC already has `GitDiff` for the third option).

**2. Take the grant, and be honest about it.** The picker becomes
`ActivityResultContracts.OpenDocumentTree` **with write when the user chose
"keep in sync"** (read-only link = import + pull, no push), and CodeC calls
`takePersistableUriPermission(uri, READ [| WRITE])` once, stores the URI, and
lists it via `persistedUriPermissions` to *verify* the grant before promising
anything. A `Lost` action (revoked in system settings, SD card unmounted,
provider gone) renders as a row on the project card — `Link lost — re-pick
<sname>` — and never as a crash and never as a silent no-op. This is the whole
reason the research insists on the persistable grant: without it, a
`content://` URI that worked in this process is worthless after a reboot.

**3. Sync points, not a watcher.** Sync runs: after a save that dirties a
project file (the `EditorViewModel` save choke point), on STOP/close of the
project, on pull-to-refresh in the hub, and from an explicit **Sync now** in
the project's ⋮ menu. No `FileObserver`/`ContentObserver` background watcher
(this phase's README defers it with reasons): observers are OEM-unreliable and
a surprise write into the user's folder while they are editing it elsewhere is
worse than a visible action. Every sync is bounded by 43.1's
`WalkBudget` and reports counts, so a linked folder with 40 000 files says so
instead of hanging.

**4. Skip rules are shared, and that is why 39.1 comes first.** Push uses
`TreeWalkPolicy.shouldSkip` **plus** `BuildArtifactIgnore.matchesPatterns`:
CodeC must never write `a.out`, `bin/menu`, `__pycache__` or `.codec/` back
into the user's real folder — the same rule that keeps them out of git
(Phase 39). If 39.1 ships first, CodeC's own outputs live in
`filesDir/CodeC/temp/` and are not in the project at all, so push has nothing
to leak; the ignore list remains as the belt. **Ordering dependency recorded:
39.1 → 43.2.** (43.1 has no such dependency and may be pulled forward.)

**5. "Open a folder as project" is the entry point.** The hub sheet's
*Open folder* row becomes a chooser with three outcomes, all from one SAF
pick: (a) **Copy into CodeC** (43.1, unchanged semantics), (b) **Link and
sync** (new), (c) when the picked folder is *already* a CodeC project root
candidate, **Add to hub** (no copy at all — register the root and use the
`File` path; this is the small piece that makes "open a project from the
projects folder" true for every candidate root, not just the primary one).
(c) is cheap and it is literally the owner's sentence, so it ships with (b).

**6. Nothing in the app learns about URIs.** The link is an *extra* concern:
`ProjectInfo.root` stays a `File`, and the sync engine is a separate module
(`ProjectSync`) called at the four sync points. If `ProjectSync` is deleted,
the app must still work — that is the review test for this part.

## Exit condition

```text
(Device; a folder on shared storage with ~20 files, one of them a .c, one an index.html)
1. Link and sync: files appear in the hub as a project; edit + save in CodeC
   → `cat` the same file in the terminal (or the device Files app) shows the
   NEW content — i.e. push really happened.
2. Edit the file outside CodeC (Files app / another editor), Sync now → the new
   content is in the editor buffer after a re-open, and the file that changed
   on BOTH sides produces a Conflict choice, not a silent overwrite.
3. RUN on the linked project: works exactly as any project (compiled binary and
   temp files are in app-private storage, and step 1's `cat` still shows ONLY
   source changes — no a.out, no bin/, no .codec/ appeared in the user folder).
4. Force-stop the app, relaunch, Sync now: still works (persisted grant).
5. Revoke the permission in system settings → the card shows "Link lost",
   Sync now says why, editing the local copy keeps working, and nothing crashes.
6. Unlink: the user's folder keeps every file CodeC wrote (nothing is deleted on
   unlink) and the project stays in CodeC as a plain local copy.
PASS = all six on the owner's device; 1-3 are the core promise.
```

## The alternative the owner should see before implementation

CodeC can *already* hold `/sdcard` paths directly when the user granted all-files
access (`Environment.isExternalStorageManager()`), so a third design is available
for 43.2: **open in place** — no copy, no link ledger, edits land in the user's
own folder, and a huge project costs nothing. It is not chosen as the default,
for four reasons, and the device round may still prefer it per-project:

1. it only works for a user who granted "All files access" — the beta must not
   require the scariest permission in the app to open a folder, and
   `projectDirCandidates()`/`FileUtils` show the app already degrades without it;
2. it breaks the one rule that keeps project operations safe:
   `ProjectManager.project()` demands `canonicalFile.parentFile == projectsRoot`,
   so an external root means every project operation (rename, delete, duplicate,
   export, `.codec/project.json` writes, and Phase 39.1's artifact deletion) has
   to be re-audited for "this could now be the user's Downloads folder";
3. on API 30+ **without** the grant there is no path at all — SAF cannot write
   back into an arbitrary folder's tree efficiently, so an in-place project on
   such a device would be read-only, which is a worse story than a copy;
4. it makes `app/src/main/java/.../utils/FileManager.kt`'s `noexec` finding
   *worse*, not better: files in `/sdcard` cannot be exec'd, so a C project
   opened in place needs a copy of its sources into app storage anyway to build —
   i.e. the copy returns, just hidden inside the compiler path.

**Decision recorded:** ship the **link + sync-in / copy-out** design as the
default (works with the picker and with a granted folder alike), and treat
in-place as a per-project flag the *user* turns on in the folder's long-press
menu, only visible when `isExternalStorageManager()` is true — with the danger
the owner named (CodeC must not delete the user's `build/` without asking)
handled by 39.1's ignore engine never deleting outside `projectsRoot`, ever. That
last clause is a law, not a nicety: **no CodeC cleanup path may write or delete
outside its own project root without an explicit user confirmation in the same
dialog.** If implementing it turns out to need more than that flag plus a
`ProjectManager` audit, the flag is dropped and the doc says so.

## Tests (plan)

- `ProjectLinkPolicyTest` (host): `decide` matrix — identical, newer-in-project,
  newer-in-folder, both-newer (→ `Conflict`), missing-one-side (→ `Push`/`Pull`),
  zero-byte files (a size-only comparison would wrongly call them equal → mtime
  breaks the tie), deleted-on-one-side never becomes a delete-on-the-other.
- `ProjectLinkTest`: serialisation round-trip (DataStore string + `Lost`
  detection inputs), `writable=false` link never yields a `Push` action.
- `SyncSetTest`: the push set excludes `TreeWalkPolicy.shouldSkip` and
  `BuildArtifactIgnore.matchesPatterns` outputs — **the exact regression pin for
  "CodeC must not write build junk into my folder"**, with `a.out`,
  `bin/menu`, `__pycache__/x.pyc`, `.codec/project.json`, `.codec.json` asserted
  absent from the push set while `main.c`/`index.html`/`README.md` are present.
- Robolectric `ProjectSyncVmTest`: the four sync points each call
  `ProjectSync.request()` exactly once per qualifying event (a debounced
  coalescing queue, so 20 rapid saves are one sync — assert the coalescing,
  not the timing); a thrown `Throwable` from the resolver becomes a message +
  a `w`-level log and `_isSyncing` clears (the same boundary rule as 43.1).
- Deliberately **not** unit-tested: real DocumentsUI behaviour, SD-card
  removal, OEM provider quirks — device round, and the doc says so instead of
  faking a `ContentResolver`.

## Sources (record)

- `rule.md` §lifecycle (no blind fix — 43.1's crash record gates 43.2's
  picker change too).
- [stackoverflow.com/q/37157765] + [stackoverflow.com/q/57260955-family
  answers / stackoverflow.com/q/57747643 (CommonsWare)] — `ACTION_OPEN_DOCUMENT_TREE`
  grants are per-session **unless** `takePersistableUriPermission` is called;
  intent flags alone are not the grant; `persistedUriPermissions` is how you
  verify. (Also in the dossier §2.)
- [stackoverflow.com/q/34927748] — a provider tree (Downloads) is not a
  directory: the link picker must refuse such picks for a *sync* link (copy
  only), because bidirectional sync against a provider that invents IDs on
  each query would delete the user's files in a `Pull`.
- `androidx.documentfile` (`DocumentFile.fromTreeUri`) API shape for
  children/`createFile` — Apache-2.0, no dependency added; CodeC keeps
  `DocumentsContract` calls as it does today.
- Android 11+ scoped-storage behaviour of `Android/data/<pkg>/` (other apps,
  including the system Files app, cannot read it) — the reason (c) "Add to
  hub" covers CodeC's own candidate roots only on devices/paths where the user
  can actually reach them, and why the *link* is the answer for everything else.
- CodeC code, 2026-09-10: `ProjectManager` (`projectsRoot`, `project()`
  parent check, `migrateLegacyFiles`), `FileManager.projectDirCandidates`,
  `EditorViewModel` save choke point, `GitDiff` (reused for Show diff),
  `SettingsManager`/DataStore for the link record, `ProjectTransfer` (43.1's
  walker), `BuildArtifactIgnore` (shared skip rules).

## Deferred / rejected with reasons

- **True in-place editing of a SAF tree (no working copy)** — rejected in
  §"The honest constraint"; would replace `File` across editor/git/compiler/
  server and pay a provider query per keystroke.
- **Using `MANAGE_EXTERNAL_STORAGE` as *the* way to open a folder** — the app
  already declares and offers it (see this phase's README: three call sites of
  `Environment.isExternalStorageManager()`), so this is not "avoid a permission",
  it is "make the scariest grant load-bearing for a headline feature". It would
  (a) hide the feature from everyone who declines, (b) still be `noexec` for
  anything we compile (`FileManager`'s own comment), and (c) put an in-place
  write path outside `ProjectManager`'s `projectsRoot` guard. The *optional*
  in-place flag above is the accepted middle ground, and only because the grant
  already exists for other reasons. Never **require** it.
- **Two-way sync on a timer** — surprise writes into a folder the user is
  editing elsewhere; explicit + save-triggered sync only.
- **Syncing `git` state** (`.git/` in the user folder) — the repository stays
  in the working copy; if the user wants it on disk they can Export. A
  half-synced `.git` in the user's folder is worse than none.
- **Conflict auto-resolution by mtime** — conflicts must be *chosen*, not
  guessed, because the loser's bytes are unrecoverable.
