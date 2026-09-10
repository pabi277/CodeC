# CodeC Phase 40.1 — Outputs are temporary files (and get collected)

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"I the output files as temporarily file"* — read as:
> treat what CodeC produces as temporary, not as project content.

## Symptom

Every `RUN` of a C file writes `source_<stamp>.c` and `program_<stamp>` into
`filesDir/CodeC/temp` (`CompilerService.getTempDir()`), and nothing ever
removes them: the only delete in the file is one success path
(`File(binary.parentFile, "source_$stamp.c").delete()`). A phone that has been
used for a term has hundreds of stray binaries and copies of the user's source
sitting in app storage — invisible to the user, so it looks like nothing is
wrong until storage is tight. Meanwhile project-side outputs
(`a.out`, `bin/menu`, `__pycache__`) are *inside* the repository, which is the
half that annoys the user (40.2).

Two things are therefore wrong and they are different: **location** (some
outputs belong to the project, e.g. the user's own `-o bin/menu`) and
**lifetime** (CodeC's own outputs never die).

## Design

**1. One policy for every generated path.** `ui/services/RunArtifacts.kt`
(pure):

```kotlin
object RunArtifacts {
    /** Where a run of `file` in `project` puts each artifact kind. */
    fun plan(lang: LanguageType, project: File, tempRoot: File, stamp: Long,
             userSuppliedOutput: String?): ArtifactPlan
    data class ArtifactPlan(val sourceCopy: File?, val binary: File, val cwd: File,
                            val ownedByCodeC: Boolean)   // owned ⇒ GC-eligible
    fun isCodeCArtifact(relativePath: String): Boolean   // for 40.2 + 39.2's push set
}
```

The rule is a sentence: **an artifact CodeC invented goes under
`CodeC/temp/runs/<stamp>/`; an artifact the user asked for by name stays where
they asked for it** (`cc … -o bin/menu` still lands in `bin/menu` — we are not
allowed to silently relocate a path the user typed in a run config, and
`ProjectConfig`/`.codec.json` commands depend on it).

`ownedByCodeC` is the boolean that decides GC eligibility and git exclusion, so
both later parts read the same object instead of re-deriving "looks like a
build output" from name patterns in three places.

**2. Per-language placements, explicit in the plan** (so the device round can
check each row):

| Run | Today | After 40.1 |
|---|---|---|
| C single file (TCC/Clang) | `temp/source_*.c`, `temp/program_*` | `temp/runs/<stamp>/{source.c,program}` |
| C project (`bin/<name>.out`, `-o bin/menu`) | project `bin/` | unchanged (user's path), excluded by 40.2, deleted by *Clear outputs* |
| Python | runs in place (`__pycache__/` appears in the project) | unchanged location; `PYTHONPYCACHEPREFIX` set to `temp/runs/<stamp>/pycache` when the interpreter supports it (3.8+), so the cache is *ours*, and 40.2 covers the case where it isn't |
| Node / Lua / HTML preview | no output files | `stdout` capture files (if any) move under the run dir |
| Server (`ServerHost`) | log tail in memory | session logs under `temp/runs/<stamp>/server.log`, GC'd |

**3. A collector.** `ui/services/TempGc.kt` (pure) + one call site
(`MainApp`/first `Application`-level coroutine, plus after every Stop and after
`Clear outputs`):

```kotlin
data class GcBudget(val maxAgeMillis: Long = 24L*3600_000, val maxBytes: Long = 128L shl 20,
                    val keepNewest: Int = 8)
sealed interface GcAction { data class Delete(val path: File) : GcAction; data class Keep(val path: File, val because: KeepReason) : GcAction }
object TempGc {
    fun plan(runs: List<RunDir>, now: Long, busy: Set<Long> /*live stamps*/, budget: GcBudget): List<GcAction>
}
```

Rules: never touch a stamp that is currently running (`busy`), always keep the
newest `keepNewest` runs regardless of age (so re-running yesterday's program
still works), age-out the rest, and enforce `maxBytes` oldest-first. Delete
failures are counted and logged, never thrown. `runs/` is the *only* root the
collector may walk — a `require(path.startsWith(tempRoot))` inside the
Android edge, with a test for it, because a GC that can walk anywhere is a data
loss waiting for a symlink.

**4. Visible, not magical.** *Settings → Storage* already exists-ish
(paths are shown); this part adds one row: **Temporary files — N files, X MB —
[Clear]**, which calls the same `TempGc` with a "clear all idle" flag. The
number is why the user tolerates the policy; an invisible cache that never
shows its size is what people resent about other apps.

**5. Cleanup on the failure paths that already leak.** `importZip`'s
`codec-import-*.zip` (in `projectsRoot()`'s dir) is deleted in `finally`
✓ — but a process kill skips that; so the temp zips move to
`temp/runs/…`/`temp/import-*.zip` and inherit the GC. Same for
`cacheDir/updates/*.apk` (`ApkUpdateManager`): installed or stale → collected
(43 touches the same dir; the rule is stated once here so both phases share
it).

## Exit condition

```text
1. RUN a C file 5×: `CodeC/temp/runs/` holds 5 stamp dirs; the project has no
   source_*.c / program_* anywhere; the Output Panel and re-runs work.
2. `cc src/*.c -o bin/menu` in the terminal + RUN with that config: bin/menu
   still exists where the user asked, and is excluded from git (40.2).
3. Restart the app after 30 RUNs: only the newest 8 (or the byte cap) remain;
   Settings → Storage reports the size, and [Clear] empties idle runs.
4. While a server/run is live, its stamp dir survives GC (kill -0 equivalent:
   re-run while GC ran — no "file disappeared" error).
5. Python run creates no `__pycache__` in the project when
   `PYTHONPYCACHEPREFIX` is honoured; when the interpreter is older, the
   fallback is exactly today's behaviour + 40.2's exclusion (no failure).
6. `Clear outputs` never deletes a file that is in the user's `.gitignore`
   sense "theirs" — i.e. the only deletions are under CodeC's temp root.
PASS = 1, 3, 4, 6 on device; 2 and 5 are the device round's spot checks.
```

## Tests (plan)

- `RunArtifactsTest` (host): placement per language; `userSuppliedOutput`
  honoured verbatim (`-o bin/menu` is never redirected, never quoted twice);
  `isCodeCArtifact` true for `source_*.c`/`program_*`/`import-*.zip` and false
  for the user's own names; a path that would escape the temp root is refused
  (returns the safe default) — the same discipline as `resolveInside`.
- `TempGcTest` (host): age + capacity + keepNewest interplay; `busy` stamps
  never deleted; empty dir; a dir that vanished between plan and delete
  (idempotent); a file (not a dir) at a stamp path → kept + logged, never
  followed; **the walk is confined to `runs/`** (a planted `runs/../important`
  is untouched); byte accounting with a directory whose size query fails.
- `TempGcAndRunInteropTest` (Robolectric, thin): the collector is invoked on
  app start and after Stop, and *not* while a run is active.

## Sources (record)

- CodeC code, 2026-09-10: `CompilerService.getTempDir()` + the two
  `source_$stamp.c` / `program_$stamp` sites (lines ~268-271, ~393-396) and the
  single delete at ~807; `ProjectTransfer.importZip`'s `codec-import-*.zip`
  lifecycle; `ApkUpdateManager.downloadApk` (`cacheDir/updates/CodeC-IDE.apk`,
  never cleaned); `LanguageType`, `ProjectConfig`/`CodecJsonParser` run
  commands; `BuildArtifactIgnore` (the pattern owner to reuse, not duplicate).
- CPython's `PYTHONPYCACHEPREFIX` env var (3.8+) as the documented way to move
  `__pycache__` out of a tree — used when present, with today's behaviour as
  the fallback; recorded in the doc so a reviewer does not assume it is
  universal.
- [`../PHASE38_43_OSS_RESEARCH.md`](../PHASE38_43_OSS_RESEARCH.md) §3 — no
  library does this; the design follows how mature toolchains separate *build
  output* from *source* and how they collect it (Gradle `build/`, Cargo
  `target/`), with CodeC's constraint that a phone must bound its own cache.

## Deferred / rejected with reasons

- **Android's `cacheDir` for the run artifacts** — the OS may evict cache at
  any moment; a run in progress would vanish mid-compile. `filesDir/CodeC/temp`
  with our own GC keeps control (and stays inside the app's backup exclusions,
  see 43.3).
- **`useLegacyPackaging`/`noexec` interactions** — untouched: temp files are
  data + a binary executed from app-private storage, exactly like today.
- **Deleting the user's `bin/menu`** — no. It is theirs; we only exclude it
  from git and offer *Clear outputs* that names it and asks.
- **A per-run "keep this output" pin** — nice; belongs with a real output
  browser, not here.
