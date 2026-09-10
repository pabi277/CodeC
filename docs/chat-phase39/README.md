# CodeC Phase 39 — Outputs are temporary, never in your repository

> **Status:** 📋 PLANNED (researched + specced, no code) · **Cost:**
> `[client-only]` · **Effort:** S/M · **Owner row:** *"I the output files as
> temporarily file and don't come to add in github push find all languages
> temporarily file and remove from git push also the .codec file"*

```text
  39.1  Run/build outputs live in a temp dir and are garbage-collected
  39.2  The ignore policy: per-language patterns + .codec, applied at the choke point
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [39.1](PART_39_1_OUTPUTS_ARE_TEMPORARY.md) | Outputs as temporary files | M | 📋 PLANNED |
| [39.2](PART_39_2_IGNORE_POLICY.md) | Nothing CodeC made reaches your repo | S/M | 📋 PLANNED |

## What exists today (evidence, read 2026-09-10)

- **Two ignore helpers, both writing to `.git/info/exclude`** (never the user's
  `.gitignore` — "the user's own file always wins" is the stated law in both):
  - `BuildArtifactIgnore.EXCLUDE_LINES` = exactly **12 patterns**:
    `*.out *.o *.obj *.exe *.class bin/ dist/ build/ target/ node_modules/
    .venv/ venv/`, with `missingLines()`, `appendTo()`, `matchesPatterns()`,
    `untrackTracked()` and a `resolveGitDir()` that also understands a
    `gitdir:` pointer file.
  - `PythonCacheIgnore` covers `__pycache__`-style caches separately.
- **Untracking exists but is wired to one place.** `untrackTracked(projectRoot,
  git)` (which runs `git rm -f --cached --quiet -- <paths>` for tracked files
  matching the patterns) is called from **`GitControlViewModel.refresh()`**
  only. `stageAll()` is `git add -A`, and COMMIT & PUSH paths that don't go
  through that refresh can still stage an artifact that was tracked before the
  rules existed.
- **`CodeC`'s own files are not excluded.** `.codec/project.json`
  (`ProjectManager.writeConfig`) and the root `.codec.json`
  (`CodecJsonParser`, Phase 24.9) are neither in `EXCLUDE_LINES` nor in
  `PythonCacheIgnore` — the owner's *"also the .codec file"* is a real gap, not
  a misunderstanding.
- **Compiled-run temp files already live outside the project** — and nobody
  cleans them up: `CompilerService.getTempDir()` is
  `filesDir/CodeC/temp`, where `source_<stamp>.c` and `program_<stamp>` are
  written; only one success path deletes (`line 807`:
  `File(binary.parentFile, "source_$stamp.c").delete()`). There is **no GC**:
  every RUN leaves a new pair of files forever. `ProjectTransfer.importZip`
  writes `codec-import-*.zip` into `projectsRoot()`'s own directory and deletes
  it in `finally` (correct, but a crash mid-import leaves it).
- The hub/card list is built from `projectsRoot().listFiles()` filtered to
  directories whose name passes `sanitizeProjectName` and does not start with
  `.` — so any stray folder a run creates in `projectsRoot` silently becomes a
  "project".

## Research that shaped the design

Dossier: [`../PHASE38_43_OSS_RESEARCH.md`](../PHASE38_43_OSS_RESEARCH.md) §3.
The one adoptable data set is **`github/gitignore`**, which is **CC0-1.0**
("allowing unrestricted use … without attribution requirements") and is exactly
the curated per-language pattern knowledge the owner asked to be found ("find
all languages temporarily file"). 39.2 borrows its **pattern names** — merged
into one table, not shipped as template files. Mechanism-wise, everything we
need is git's own: `.git/info/exclude` (machine-private, never travels),
`git rm --cached`, `git status --porcelain=v1`, `git check-ignore -v`.

## The rules both parts share

1. **Never edit the user's `.gitignore`.** CodeC's rules go to
   `.git/info/exclude`, and a pattern the user already has (in either file) is
   not duplicated — today's behaviour, kept.
2. **Never delete a file the user might want.** GC only ever removes files
   under `CodeC/temp/` that CodeC created and that are not in use by a running
   process; `.git/` is never touched except through git subcommands.
3. **A file's location decides its lifetime.** Inside the project = the user's;
   in `CodeC/temp` = CodeC's, and therefore collectable.
4. `git add -A` must not be able to bypass anything: the enforcement point is
   `GitManager.stageAll()`, not a caller's good intentions.

## Exit condition (whole phase)

```text
1. RUN a C file, then COMMIT & PUSH in a repo: GitHub shows the sources; no
   a.out / program_* / source_*.c / bin/* anywhere in the commit; and
   `git status --porcelain` afterwards is clean apart from real sources.
2. .codec/project.json and .codec.json never appear in a commit, in a project
   that had them committed BEFORE this phase (the `git rm --cached` path), and
   the local files still work after the untrack (the project still opens, runs,
   and keeps its run config).
3. Python: __pycache__/, *.pyc, .venv/, .pytest_cache/ stay out. Node:
   node_modules/, npm-debug.log, .next/, dist/ (when created by CodeC's run)
   stay out. C/C++ extra: *.so, *.a, *.d, CMakeFiles/, CMakeCache.txt,
   compile_commands.json stay out. (Each table entry has a host test.)
4. Run `cc src/*.c -o bin/menu` yourself in CodeC's terminal, then COMMIT &
   PUSH: bin/menu is still excluded — because the ignore table, not the file
   location, catches the user's own output.
5. After 20 RUNs and one app restart, `CodeC/temp` is bounded (≤ the GC cap in
   bytes AND in entry count) and the last run's artifacts still work — GC never
   eats what is currently in use.
6. A user's own `.gitignore` that says `!a.out` (i.e. they DO want it) wins —
   CodeC neither edits their file nor untracks the file they want tracked.
PASS = all six (3 is host-tested; 1, 2, 4, 5, 6 need the device round).
```

## Risks to watch (multi-device round)

- **Already-tracked artifacts.** `.git/info/exclude` cannot un-track; only
  `git rm --cached` + a commit can, and *that commit is a deletion in the
  user's history on GitHub*. It must be explicit and explain itself — the
  device round checks that "cleaned 3 build files out of the repository" is a
  sentence the user understands, not a mystery commit.
- **Case-insensitive filesystems** (some SD cards / OEM providers) make
  `bin/` vs `BIN/` behaviour differ — patterns are matched on the git-relative
  path exactly as git does, and `matchesPatterns()` must stay consistent with
  `git check-ignore` on the same inputs (a test asserts the two agree on the
  device).
- **Clone-from-GitHub repos that ship their own `.gitignore`** — the common
  case; the "user file wins" rule is what protects it, and 39.2 tests it.
- **`.codec/` is config, not junk.** Excluding it from *git* must not break
  anything on the next clone: the design answer is that
  `ProjectConfig.defaultFor(name, "auto")` regenerates it (the clone path in
  `FileManagerViewModel` already does exactly this), so a fresh clone on
  another phone still opens, and RUN detection (`ProjectRunDetector`) fills the
  rest. If a future `.codec.json` gains something unreproducible, that is the
  moment to revisit — recorded here so the trade-off is visible.

## Deferred, recorded on purpose

- **Writing the user's `.gitignore`** (VS Code offers this) — CodeC will not
  modify a tracked file in someone's repository from an IDE action.
- **`git clean -fdx` as a "clean" button** — deletes untracked user files;
  refused. "Clear outputs" only touches `CodeC/temp`.
- **A global `core.excludesFile`** — it lives in `$HOME`, which for CodeC's
  `$PREFIX` is userland state that may be reinstalled; `.git/info/exclude` per
  repo is stable and inspectable.
- **Committing the untrack automatically** — the deletion commit is made by the
  user's own COMMIT & PUSH, not by a background job.
