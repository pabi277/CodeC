# CodeC Phase 33.1 — First-run tiles

**Status:** ✅ IMPLEMENTED + CI ✅ (`34346424311`) + DEVICE-PASSED (owner: "Device test pass", 2026-09-09) — merge held · **Cost:** `[client-only]` · **Effort:** M
· **Target:** first-launch flag in DataStore, Projects empty / welcome

---

## 1. Design

On first launch (no last file): **three tiles only** —

| Tile | Opens | RUN |
|---|---|---|
| **C — works offline** | `main.c` template, caret in `main` | TCC, no dialog |
| **Python** | `main.py` | existing Phase 21 install gate if python missing |
| **HTML preview** | `index.html` | Web preview |

Returning users: last file (Phase 16) unchanged. A "show welcome once"
Settings reset is enough for testers.

## 2. Exit condition

```text
(Device, fresh install)
1. Three tiles; tap C → code + Keys; RUN prints hello with no Packages speech.
2. Second launch opens last file, no tiles.
3. Python tile: install sheet only if python missing.
PASS = all three.
```

## 3. Implementation (2026-09-09, `arena/01a085e0-codec`)

- **`ui/projects/WelcomeStarters.kt` (pure)** — the three starter tiles
  (`C` / `Python` / `HTML`) as data, each mapping to the wizard's project type
  + entry file (`ProjectScaffold`), so a tile tap is "create-or-open the
  starter project and open its entry file". `ensureProject(manager, starter)`
  is idempotent (create only if absent — a second tap reuses the project).
- **`SettingsManager`** — a DataStore flag `first_launch_complete`
  (default **false** = welcome not yet dismissed), with `firstLaunchCompleteFlow`
  + `setFirstLaunchComplete`.
- **`ui/screens/WelcomeScreen.kt`** — the first-run screen: the 33.3 identity
  copy ("Write and run C, Python, JavaScript, and HTML on your phone. C works
  offline with no setup.") + three `StarterTile`s (C orange / Python blue /
  web green leading marks, shared with the Projects empty state).
- **`MainActivity.MainApp`** — reads the flag once (nullable gate so neither
  screen flashes while DataStore loads): `false` renders `WelcomeScreen`
  instead of the whole Scaffold (no bottom bar — "three tiles only"); tapping
  a tile creates-or-opens the starter project off the main thread, saves the
  launch state **before** flipping the flag (so the shell that replaces the
  welcome opens the starter file, and the next launch opens it too — exit 2),
  then marks the welcome complete.
- **`SettingsScreen` → About** — a "Show the welcome screen again" reset
  (clears the flag) for testers.

RUN ▶ needs no new code: a C starter runs through TCC with no install dialog
(33.0 `runOpenFile` → `runActiveFile`), a Python starter hits the existing
Phase 21 `python` install gate only when python is missing, and an HTML
starter previews.

## 4. Tests

- `WelcomeStartersTest` (5 cases): three tiles with ids `c`/`python`/`web`;
  every title/subtitle/project name non-blank; project names unique; every
  entry file is what `ProjectScaffold.filesFor(type)` scaffolds; `byId`
  round-trips.

## 5. Evolution note

The plan's "first launch (no last file)" was implemented as an explicit
DataStore flag rather than reusing the launch-state heuristic: a stale/deleted
last file must not resurrect the welcome on every returning user, and the flag
is exactly what the Settings reset clears. The tile tap reuses the wizard's
own scaffold bytes, so there is no second, divergent copy of the C/Python/HTML
starters to drift out of sync.
