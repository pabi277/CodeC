# CodeC Phase 39.1 — "Open folder" that cannot crash

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"open a folder crash the app"*

## Symptom & first move

Projects Hub → *Open folder* → pick a directory → the app dies (owner's
report). Before touching code, **reproduce and read the crash record** — CodeC
already keeps a header-first crash log (Phase 29) in `AppLogger` /
`LogsScreen`, and the app can also be driven from the terminal with
`adb logcat`. The hypothesis this doc is written against, visible in the code:

```
FileManagerScreen.folderImportLauncher → FileManagerViewModel.importFolder
  → ProjectTransfer.copyDocumentTree → copyDocumentChildren  (recursion)
      · no visited set            → a provider that lists the parent as its
                                    own child never terminates
      · catch (e: Exception)     → StackOverflowError (an Error) escapes
                                    → process death, no message at all
```

Secondary candidates to rule out from the same record: an
`IllegalStateException`/`NullPointerException` from a provider that returns
null `DOCUMENT_ID`s, an OOM from a huge copy, or
`SecurityException: Permission Denial: opening provider …` when the grant was
not persisted/valid. Each produces a *different* fix, so the crash text gates
the design — that is `rule.md`'s lifecycle, not theatre.

## Design

**A pure walk plan.** `ui/projects/TreeWalkPolicy.kt` — no Android types:

```kotlin
data class TreeEntry(val documentId: String, val name: String, val isDirectory: Boolean, val sizeBytes: Long?)
data class WalkBudget(val maxDepth: Int = 24, val maxFiles: Int = 5_000,
                      val maxTotalBytes: Long = 512L shl 20, val maxSeconds: Long = 120)
sealed interface WalkStep { data class Copy(val entry: TreeEntry, val relativePath: String) : WalkStep
                            data class MakeDir(val relativePath: String) : WalkStep
                            data class Skip(val relativePath: String, val reason: SkipReason) : WalkStep }
sealed interface WalkStop { object Done; data class Budget(val which: String, val value: String) : WalkStop
                            data class Cycle(val documentId: String) : WalkStop }
object TreeWalkPolicy {
    fun plan(layers: Map<String, List<TreeEntry>>, budget: WalkBudget): WalkPlan   // WalkPlan = steps + stop
    fun safeRelativePath(parent: String, name: String): String?      // rejects "", ".", "..", '/', 0x00, control chars
    fun shouldSkip(name: String): Boolean                             // .git/, node_modules/, .codec/, __pycache__, .DS_Store…
}
```

The **Android edge** (`ProjectTransfer`) becomes a thin loop: for each
`WalkStep` do the one ContentResolver call, and stop when `WalkStop` says so.
Recursion is *gone* — the walk is a queue keyed by `documentId` with a
`visited: HashSet<String>`; a document id seen twice is `WalkStop.Cycle`,
which turns "crash" into "one line in the log and a partial-import cleanup".

Why `plan()` takes a map instead of a resolver: it is what makes the whole
policy host-testable (including the cycle case) without Robolectric or a fake
`ContentResolver` — the house pattern (37's `LanAddress`/`ServerRegistry`, 30's
`Emmet`), and it means CI's unit tests prove the *rules*, while the device
proves the plumbing.

**Budgets, named in the message.** Over a budget is not an error, it is a
result: `Stopped — this folder has more than 5 000 files (CodeC imports
project-sized folders, not whole downloads)`. Numbers are constants in
`WalkBudget` so a device round can retune them without touching logic.

**Skip list.** `.git/`, `node_modules/`, `.venv/`, `__pycache__/`,
`.gradle/`, `build/`, `dist/`, `target/`, `bin/obj` for non-source noise,
dot-files-by-default (except `.gitignore`, `.editorconfig`, `.env.example`,
`.codec.json`) and a fixed extension deny list (`.zip`, `.apk`, `.mp4`,
`.iso`, `.img`, `.7z`, `.deb`, `.ttf`, `.so`, `.dll`). Skipped entries are
*counted and reported* (`"imported 312 files, skipped 4 108"`) — a silent
skip is indistinguishable from data loss, and the owner must be able to see
that CodeC did not eat anything. Reuse `BuildArtifactIgnore.matchesPatterns()`
where the semantics match, rather than a second pattern table.

**Progress + cancel + cleanup.** `importFolder` gains
`_importProgress: StateFlow<ImportProgress?>` (files done / total-if-known /
bytes, cancel flag checked every N files — a `query` and a stream copy are both
killable points). On any failure or cancel: `dest.deleteRecursively()` (the
clone path already does exactly this in `cloneFromGitHub` — copy that shape)
and the message names what was kept. No `.codec/project.json` is written for a
project that did not finish.

**The boundary catches `Throwable`.** Every SAF entry point
(`importFolder`, `importFile`, ZIP import, `fileImportLauncher`, the 39.2 link
picker) ends with `catch (t: Throwable)` → `AppLogger.e` with the class +
message + top frames, and a one-line user message. This is a *policy*, written
once into 39's law: **no user-facing action may be able to kill the app**.
(Kotlin/Compose cancellation semantics mean we do not swallow
`CancellationException` into a message — it is rethrown after cleanup; the
test pins that too.)

**Why 39.1 does not persist the grant.** The picker already uses the right
contract (`ActivityResultContracts.OpenDocumentTree()`,
`FileManagerScreen.kt:177`), so the fix is one call — but *not here*: on a
successful one-shot import we deliberately do **not** call
`takePersistableUriPermission` (a copied tree needs no lingering permission, and
a grant the user can see in system settings but cannot explain is a worse app).
39.2 persists, because it promises a lasting link. That asymmetry is deliberate
and is stated in both docs so a reviewer does not "unify" it.

## Exit condition

```text
1. The owner's crash case: reproduce once on device with the crash record
   captured, fix for cause, then re-run the same folder → imports cleanly.
2. Cyclic/self-listing provider (host test + a real Drive/Downloads-provider
   pick): terminates, reports, no crash.
3. 2 000-file tree: progress visible, Cancel works, nothing partial left.
4. Huge tree / huge single file: budget stops it with the number named.
5. Airplane mode with a cloud provider folder: per-file failures are listed as
   "N files could not be read", the rest import, no crash.
6. Re-open the app: the imported project is intact and byte-identical for the
   files it reported (spot-check two of them in the editor + one in the
   terminal with `wc -c`).
PASS = all six.
```

## Tests (plan)

- `TreeWalkPolicyTest` (host): breadth-first order and stable
  `relativePath`s; **cycle → `WalkStop.Cycle`, terminating in bounded time**
  (a self-listing root and an A→B→A pair); depth budget; file-count budget;
  byte budget with unknown sizes (`sizeBytes == null` never overflows or
  short-circuits the guard); skip rules (`.git/` skipped, `.gitignore` kept);
  `safeRelativePath` refuses `..`, `.`, absolute, empty, embedded `/`, NUL,
  and control chars, and sanitises to the same result `ProjectPathUtils`
  produces (no drift between the two path laws); a 10 000-entry synthetic
  flat layer stays linear in time (a bounded performance assertion, not a
  stopwatch flake — count `plan()` calls, not milliseconds).
- `ImportProgressTest` / `ImportCleanupTest` (Robolectric, VM): cancel mid-way
  deletes the destination and reports; failure after N copies deletes it too;
  a `Throwable` from the transfer becomes a message and an
  `AppLogger.e` line, and `_isBusy` is always cleared (the "spinner forever"
  bug class); `CancellationException` propagates after cleanup.
- Fake resolver harness: one small `FakeDocumentTree(entries: Map<String,
  List<TreeEntry>>, failOn: Set<String>)` used by both the policy tests and the
  VM tests, so the *Android* half is exercised against the same fixtures.
- Existing `ProjectTransfer` ZIP tests keep passing untouched (budgets are
  additive; the ZIP caps are already proven).

## Sources (record)

- CodeC code, 2026-09-10: `ProjectTransfer.kt` (recursion at
  `copyDocumentChildren`, the ZIP caps `MAX_ZIP_ENTRIES`/`MAX_ZIP_ENTRY_BYTES`
  and `finally { temporaryZip.delete() }`), `FileManagerViewModel.importFolder`
  (`catch (e: Exception)`), `FileManagerScreen.kt:177-185` +
  `:1013-1016` (the launcher and the hub row), `FileManagerViewModel.cloneFromGitHub`
  (the delete-on-failure shape to copy), `ProjectPathUtils.resolveInside` /
  `sanitizeArchiveSegment`, `AppLogger` (1 000-line ring), `LogsScreen`.
- [stackoverflow.com/q/34927748] — the *Downloads* picker entry is a provider
  (`content://com.android.providers.downloads.documents/tree/downloads`), not a
  filesystem directory; the accepted answer documents why tree-shaped code
  breaks on it.
- [stackoverflow.com/q/46180851], [reddit.com/r/androiddev "Fixing
  TreeDocumentFile#findFile lousy performance"] — why `DocumentFile`'s
  per-child queries are the wrong tool for bulk work (we keep
  `DocumentsContract`).
- `docs/chat-phase39/README.md` §"noexec is physics"; `rule.md` lifecycle
  (reproduce + evidence before the fix).

## Deferred / rejected with reasons

- **Silently raising the budgets until "it works"** — a phone IDE copying a
  user's whole `Download/` into app-private storage is a battery, storage and
  trust failure; a named budget with a clear message is the product answer.
- **Hard/symlink the picked tree into the project instead of copying** —
  `ln -s` across the FUSE mount is unreliable and `git status`/`cc` would
  follow links out of the sandbox; no.
- **Streaming the SAF tree directly into the editor (no copy at all)** — that
  is 39.2, with its own mirror rules; 39.1 keeps *import = copy* semantics for
  users who want a snapshot.
- **`ACTION_CREATE_DOCUMENT`-based "export back"** — exists already
  (`exportZip`/`CreateDocument`); 39.2 handles write-back for links.
