# CodeC Phase 39.2 — The ignore policy: nothing CodeC made reaches your repository

> **Status:** ✅ COMPLETE, DEVICE-PASSED & MERGED (2026-09-10) · **Cost:** `[client-only]` · **Effort:** S/M ·
> **Owner row (verbatim):** *"don't come to add in github push find all
> languages temporarily file and remove from git push also the .codec file"*

## Symptom

`git add -A` (COMMIT & PUSH's staging step) takes whatever is in the folder.
Today the safety net is `BuildArtifactIgnore`'s **12 patterns** in
`.git/info/exclude`, plus `untrackTracked()` — but the untrack runs only from
`GitControlViewModel.refresh()`, and three whole classes of file are not
covered at all: **CodeC's own config** (`.codec/project.json`, `.codec.json`),
**most languages' junk** (`*.pyc`, `*.so`, `*.a`, `*.d`, `CMakeFiles/`,
`compile_commands.json`, `.gradle/`, `out/`, `.pytest_cache/`, `.ruff_cache/`,
`.mypy_cache/`, `.next/`, `.parcel-cache/`, `.svelte-kit/`, `.turbo/`,
`*.tsbuildinfo`, `.DS_Store`, `*.swp`, `*~`, `Thumbs.db`, `.venv` variants,
`htmlcov/`, `.coverage`, `venv/` ✓ already, `__pycache__/` (in the *other*
helper), and any language the owner tries that we never thought of.

Result: users see build artifacts in their public repositories, and CodeC's
`.codec/` folder travelling along — which the owner noticed and wants gone.

## Design

**1. One table, per language, derived from CC0 templates.**
`ui/projects/RepoHygiene.kt` (pure):

```kotlin
object RepoHygiene {
    /** Grouped so the UI can say WHY a line exists, and so the table is reviewable. */
    val EXCLUDE_LINES: List<Pattern>      // Pattern(pattern: String, reason: String, langs: Set<String>)
    fun missingLines(existingExclude: String?, gitignore: String?): List<Pattern>
    fun matches(relativePath: String): Boolean
    fun explain(relativePath: String, gitignoreText: String?): Explanation   // for "why isn't this in my commit?"
}
data class Pattern(val pattern: String, val reason: String, val langs: Set<String>)
```

`EXCLUDE_LINES` becomes **~55-65 patterns** in named groups — `C/C++`,
`Python`, `Node/JS/TS`, `Java/Kotlin`, `Lua`, `Go`, `Rust`, `Docs/tests`,
`OS junk`, `CodeC`. Content is the intersection of *what CodeC can actually
produce or run on a phone* with the `github/gitignore` templates for those
languages (CC0 — [github/gitignore], "all templates are released under
CC0-1.0" per its README/LICENSE; **the pattern names are what we take, not the
template files**, so no vendored asset and no ShareAlike clause; the CC0 note
still goes in `assets/licenses/` next to the zxing one, because recording where
a table came from is this repo's habit).

`BuildArtifactIgnore` and `PythonCacheIgnore` **fold into `RepoHygiene`** as
one object with two entry points (write-to-exclude, untrack-tracked), keeping
their public function names as `@Deprecated`-free thin delegates for the three
existing call sites — or, better, the call sites are updated in the same commit
and the two files are deleted. One truth, one owner (the Phase 37 lesson).

**2. CodeC's own files, explicitly.**

```
.codec/          # CodeC project metadata (run config, launch default)
.codec.json      # per-project run override
codec-*.tmp / .codec-tmp/  # any scratch CodeC writes into a project today
```

with the consequence stated in the doc and in the *untrack* message: the file
stays on disk, only the repository stops carrying it; a fresh clone
regenerates it (`ProjectConfig.defaultFor(name, "auto")` +
`ProjectRunDetector`), which is already what the clone path does in
`FileManagerViewModel.cloneFromGitHub`.

**3. Enforcement at the choke point, not at a caller.**
`GitManager.stageAll()` becomes:

```kotlin
fun stageAll(root: File) {
    RepoHygiene.ensure(root)                    // idempotent append to .git/info/exclude
    val doomed = RepoHygiene.trackedViolations(root, trackedFiles(root) ?: emptyList())
    if (doomed.isNotEmpty()) rmCached(root, doomed)   // then add -A
    exec(root, listOf("add", "-A"), localTimeoutSeconds, "git add failed")
}
```

so *every* path that stages (sheet, quick-commit, future 40.3 publish) is
covered, and `GitControlViewModel.refresh()`'s separate call disappears. The
order matters and is tested: untrack-then-add means the *same* commit records
both the removal and the clean tree, instead of a follow-up "remove junk"
commit that clutters history.

**4. Tell the user, in one line, when it happens.** If `doomed` is non-empty,
the sheet's commit area shows:
`Removed 3 build outputs from the repo (they stay on your phone and in .git/info/exclude)`
— an unexplained `git rm --cached` in the user's history is the kind of thing
that destroys trust in an app that touches their repository.

**5. A "what will be committed" list, before the button.** From the staged set
(`git diff --cached --name-status`, or `git status --porcelain=v1`'s XY codes
that `GitStatusParser` already reads): count + first ~15 names, with the
`explain()` reason for anything *not* included that the user might expect
(a file they can see in the tree). This is the piece that makes the whole
policy verifiable by a human instead of by faith, and it doubles as the answer
to "why didn't my file push?" — the owner's other complaint, in the same
screen.

**6. `git check-ignore -v` as the tiebreaker.** `explain()` may ask git itself
(`check-ignore -v -- <path>`) through the existing `exec` path when a user
disagrees with our table; the source line git prints (which file, which
pattern) is shown verbatim. It's also how the device test asserts our matcher
and git's matcher agree, rather than asserting our *opinion*.

## Exit condition

```text
(Device, a repo pushed to GitHub; the same checks visible on github.com)
1. A project with a.out + bin/menu + __pycache__/x.pyc + .codec/project.json +
   .codec.json: COMMIT & PUSH sends only sources; the commit list shown before
   the tap matches what lands on GitHub.
2. Repo that ALREADY had a.out committed in an earlier round: the next commit
   removes it from the repository (visible on GitHub) and the file still exists
   on the phone (open it in the Files app or `ls` it in the terminal).
3. A user's `.gitignore` containing `a.out` is not duplicated into the exclude
   file, and a user's `.gitignore` with `!a.out` (wanting it tracked) results in
   a.out being committed — CodeC does not override the user's own file.
4. "Why isn't notes.txt in my commit?" → the explain line names the pattern and
   the file it came from (`.git/info/exclude` or `.gitignore`).
5. After publish/clone on a second device: the project still opens, still runs,
   and regenerated metadata means nothing was lost but run-config overrides
   (`.codec.json`) — and the UI says so on a clone that has none.
PASS = all five on the owner's device; 1-4 are host-testable too (fixtures).
```

## Tests (plan)

- `RepoHygieneTest` (host): the table (a golden list test so a pattern cannot
  be dropped silently), per-group membership, `missingLines` against
  (a) an empty exclude, (b) a partial one, (c) a `.gitignore` that already
  covers everything (→ zero lines written, file not created!), (d) a
  `.gitignore` with `!a.out` (→ `a.out` NOT added to exclude: user wins);
  `matches` for every language row — `__pycache__/x.pyc`, `build/app/intermediates/y`,
  `.venv/lib/python3/site-packages/z.py`, `dist/bundle.js`, `a.out`,
  `bin/menu`, `CMakeFiles/3.22/CMakeCCompiler.cmake`, `.next/BUILD_ID`,
  `.DS_Store`, `.codec/project.json`, `.codec.json`, `x.o`, `x.so`, `x.d` — and
  **false** for the files a user must never lose: `out/main.c` (a folder called
  `out` containing sources? see below), `about.html`, `src/index.ts`,
  `build.gradle.kts`, `build.py`, `target.c`, `node.js`.
  That second list is the interesting half: `build/` and `out/` are
  directory patterns with a trailing slash, so `build.gradle.kts` is safe —
  the test proves the table was written with git's semantics, not with
  substring matching (today's `matchesPatterns()` is a *prefix/suffix*
  approximation and must be aligned with git, or `check-ignore` is the oracle).
- `StageAllHygieneTest` (Robolectric, fake `GitManager` subclass or an injected
  runner): order (untrack before add), idempotence (second `stageAll` performs
  no `rmCached`), a repo with nothing to untrack issues no extra process, and a
  `git rm --cached` failure does not lose the user's `git add` (the error is
  reported and staging still happens or the whole op aborts — decided in the
  doc: **aborts**, because a half-staged commit is worse).
- `CommitPreviewTest`: the "what will be committed" projection from a captured
  `git status --porcelain=v1` fixture, incl. renames (`R  old -> new`) and the
  15-line truncation.
- Device-only, deliberately not automated: `git check-ignore -v` agreement
  (checked on the real git in the userland, since the JVM has none) — recorded
  as a manual step in the exit condition instead of a fake test.

## Sources (record)

- **`github/gitignore`** — the canonical templates, **CC0-1.0** licence:
  "All templates in the github/gitignore repository are released under the
  CC0-1.0 license, allowing unrestricted use in your projects without
  attribution requirements"; three tiers (root = languages, `Global/` =
  editors/OS, `community/` = frameworks), ~150+ templates
  [github.com/github/gitignore README + LICENSE, summarised at
  deepwiki.com/github/gitignore and instagit.com's integration guide, which
  also documents merging-with-existing-rules rather than overwriting].
- git's own semantics for the mechanism we rely on: patterns with a trailing
  `/` match directories only; `!` negation; `.git/info/exclude` is repository
  local and never cloned or pushed (the reason CodeC uses it instead of editing
  the user's file) — Pro Git / `gitignore(5)`.
- `X-Accepted-GitHub-Permissions`-style "ask the tool, don't guess" pattern
  reused here as `git check-ignore -v` for explanations.
- CodeC code, 2026-09-10: `BuildArtifactIgnore` (12 patterns,
  `matchesPatterns`, `untrackTracked`, `resolveGitDir`), `PythonCacheIgnore`,
  `GitManager.stageAll/stageFile/unstageFile/trackedFiles/rmCached`,
  `GitControlViewModel:228-233` (today's single untrack site),
  `ProjectManager.writeConfig` (`.codec/project.json`), `CodecJsonParser`
  (root `.codec.json`), `FileManagerViewModel.cloneFromGitHub`
  (config-regeneration on clone, which is what makes excluding `.codec/`
  safe), `GitStatusParser` (XY codes for the preview).
- [`../PHASE38_43_OSS_RESEARCH.md`](../PHASE38_43_OSS_RESEARCH.md) §3.

## Deferred / rejected with reasons

- **Generating `.gitignore` in the user's repo** (GitHub's own default when you
  create a repo with a template) — CodeC's rules would then be *committed* into
  someone's project and argued with later. `.git/info/exclude` keeps the
  opinion on the device.
- **A matcher library** (`gitignore`-style npm/Java ports) — a second,
  slightly-wrong implementation of git's rules is worse than asking git with
  `check-ignore` when precision matters; `matchesPatterns()` stays a
  *pre-filter* and the golden test keeps it honest.
- **Excluding `docs/`, `build/` for every project unconditionally** — the
  table's `langs:` field is why each pattern carries its reason: a `web`
  project that *commits* `dist/` on purpose (a GitHub Pages repo!) must not
  have CodeC decide otherwise; the `!` rule in a user's `.gitignore` wins, and
  exit condition 3 pins it.
- **Deleting artifacts from disk as part of commit** — never; exclusion from
  git and deletion from the phone are different verbs.
