# PART 58.1 — a first open that lands in the editor, on a page we wrote

**Roadmap line:** *“58.1 First open is the editor, on a snake sample we write.”*
**Files touched** (all in one commit with 58.2 — see the README):

| File | What changed |
|---|---|
| `app/src/main/java/com/codeci/ide/ui/projects/SnakeSample.kt` | **new** — the sample: `NAME "snake"`, `TYPE "web"`, `ENTRY_FILE "index.html"`, `FILES` (a getter), `ensure(root)` |
| `app/src/main/java/com/codeci/ide/MainActivity.kt` | the first launch seeds the sample, saves the launch state, and starts on it (`firstOpenSample`) |
| `app/src/main/java/com/codeci/ide/ui/components/StarterTile.kt` | **new** — the hub's starter tile, moved out of the retired screen verbatim |
| `app/src/main/java/com/codeci/ide/ui/screens/WelcomeScreen.kt` | **deleted** — the first-run screen is retired (58.1) |
| `app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt` | imports the tile from its new home (`:120`); the loop is unchanged (`:2008`) |
| `app/src/test/.../StarterTilesTest.kt` | **new** — the tiles, and the pin that the retired screen stays retired |
| `app/src/test/.../FirstOpenSampleTest.kt` | **new** — the seed's five laws |
| `app/src/test/.../WelcomeLayoutTest.kt` | **deleted** — it measured a screen that no longer exists |
| `app/src/test/.../{TokenAdoption,TypeAdoption,IconRole,TouchTarget}Test.kt` | the `sixFiles` list becomes `coreFiles` (five files), welcome entries dropped |

## 1. Research, before any code

* **Is there a snake sample in this checkout?** No. `grep -ril "snake" app/src docs` on this
  checkout hits exactly three things that predate the phase: the Python mark's comment in
  `SpckIcons.kt:238` (“Simplified two-snake Python mark”), search fixtures in
  `ProjectSearchTest.kt:58-81` (`"Snake\nsnake"`), and the roadmap's own sentences. The
  roadmap's finding (“there is no snake sample in the repo, search on 2026-09-22”) is
  confirmed, so the sample has to be written — and the roadmap says how: *“Write a small
  original HTML snake. Do not transcribe SPCK's.”*
* **What does the reference show?** The seven shots show the editor with a file already open
  (`122157`, `124105`): a name in the top row, code below, no first-run screen anywhere. None
  of them shows the sample's page, so the page is not a copy target — it is the thing the
  first-open state needs in order to look like the shots at all.
* **Why HTML.** The roadmap's own constraint decides it: *“C and HTML preview must run without
  userland.”* A `.html` entry never reaches a tool probe — `LanguageRunPlanner.decide` returns
  `RunDecision.WebPreview(profile)` from the profile's own `isWebPreview` before any binary is
  looked for — so RUN ▶ on the sample opens the in-process preview with nothing to download.
  A Python sample would have made the very first tap a download (`NeedsInstall`), which is the
  opposite of the phase's exit.

## 2. What the sample is

`SnakeSample.ensure(root)` seeds one project (`snake/`) exactly the way the wizard would:
`ProjectConfig.defaultFor` metadata into `.codec/project.json`, the files of `FILES`, and a
README — the same shape `DemoProjects` already ships, so the hub, the run detection and the
preview see an ordinary project of type `"web"`.

* **Idempotent, never destructive.** A second `ensure` returns `null` and touches nothing: if
  the directory exists, the user's edits are theirs. A failed write deletes what it started
  and returns `null` (`FirstOpenSampleTest` pins both, with the wizard's own file set as the
  expected shape).
* **The page is standalone.** One `index.html`: a 20×20 canvas, arrow keys/WASD **and** a
  thumb pad (`class="pad"`, `touchstart`/`touchend`), a RESTART control, score + best, wall and
  self collision. No external URL, no `<script src>`, no `<link>`, no `@import`, no template
  placeholder — pinned literally, because a page that needs the network is a page that cannot
  be the first impression on a phone that is offline.
* **`FILES` is a getter, not a stored list.** The first version stored it and the compiler
  refused: `variable 'PAGE' must be initialized` — an object's stored properties initialize in
  declaration order and the page is declared below them. The getter is the fix and the file
  says so.

## 3. The first open

In `MainActivity`, on the launch where `firstLaunchComplete == false`:

1. seed (`SnakeSample.ensure`) on `Dispatchers.IO` — **skipped entirely under
   `SafeMode.active`** (Phase 42.3: safe mode does less at startup, on purpose);
2. `EditorLaunchState.save(activity, SnakeSample.NAME, SnakeSample.ENTRY_FILE)` — **before**
   the flag flips, so the shell that replaces the frame opens the sample and the *next* launch
   resumes it (33.1's exit 2, kept);
3. `firstOpenSample = true`, then `setFirstLaunchComplete(true)` — **even when the seed
   failed**, because a filesystem that refuses must not leave the user on a blank first frame;
   the shell then opens the way it always did (the hub), which is the honest fallback;
4. `startDestination` puts the sample branch **above** the resume offer, so no first-run state
   (a crash log, a missing file list, whatever the offer would have said) can outrank it.

## 4. The welcome screen is retired

The first-run screen was the only place the tiles lived. Rather than keep a dead screen for
one composable, the tile moved **verbatim** into `ui/components/StarterTile.kt` (kdoc rewritten
to record the retirement) and `WelcomeScreen.kt` was deleted. That single deletion invalidated
five pins at once, so it was treated as one conscious re-cut rather than five loose edits:

* `WelcomeLayoutTest.kt` deleted (it measured the retired screen);
* `TokenAdoptionTest` / `TypeAdoptionTest` / `IconRoleTest` / `TouchTargetTest`: `sixFiles` →
  `coreFiles`, the welcome path and the welcome-only entries dropped, with the reason written
  where the list is declared;
* `StarterTilesTest.kt` added: three tiles and they are languages; every tile names its
  language and promises the next step; the empty hub draws them in one loop; **the first-run
  screen must not come back** (the screen file must not exist, and `MainActivity` must not
  call it).

## 5. Test log

| Run | Result |
|---|---|
| `FirstOpenSampleTest` + `TokenAdoptionTest` + `TypeAdoptionTest` + `IconRoleTest` (host harness) | **20 passed, 0 failed** |
| `FirstOpenSampleTest` alone (seed, no-overwrite, standalone page, playable, launch ordering) | 5 passed, 0 failed |
| `StarterTilesTest` (4 cases) | **not runnable in the host harness**: it links `WelcomeStarters`, which links `ProjectManager` → `android.content.Context`. Compiles and runs under `:app:testDebugUnitTest` only; CI is its executor of record |

**Owed:** the device rows (P1-P10 / Q1-Q6 / R1-R11 in `docs/chat-phase57/DEVICE_ROUND.md` plus
a first-open row: install fresh, open, and confirm the editor shows `index.html` with the snake
on screen and no hub in between).
