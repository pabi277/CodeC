# Phase 14 — Implementation Record (Part 14.1: Server Runner, Port Monitor & Web Preview Integration)

**Status:** IMPLEMENTED & CI-GREEN (client-only, `arena/01a05421-codec`) — **device recipe pending** (owner).
**Date:** 2026-08-31
**Branch:** `arena/01a05421-codec` (started from `main` at `006515a`, i.e. after PR #31/Phase 13)

---

## 1. What was delivered

The plan in `PART_14_MIXED.md` called for a **Server-to-WebView pipeline**: RUN ▶ on a
server-type project starts a long-lived local server, detects the port it bound,
streams its logs, and opens the in-app Web Preview on `http://127.0.0.1:<PORT>`.

Delivered (all client-only — **no `[repo-build]` dispatch**):

| Piece | File(s) |
|---|---|
| Server process runner (long-lived, streamed, stop-able) | `app/…/ui/services/ServerRunner.kt` |
| Pure port-binding detector (Flask/Uvicorn/`http.server`/CodeC C template) | `ServerPortDetector` in the same file |
| `ProjectConfig` schema v1 + `port`/`previewUrl` (backward compatible, optional) | `ui/projects/ProjectConfig.kt` |
| Server presets: `python-flask`, `python-fastapi`, `c-microservice` | `ProjectConfig.defaultFor` |
| Starter-file scaffolds (Flask/FastAPI split app + `index.html` read per request; C socket microservice; static web `index.html`; python `main.py`) | `ui/projects/ProjectScaffold.kt` |
| Files-tab New Project wizard with template picker | `ui/projects/ProjectTypes.kt`, `FileManagerScreen.kt`, `FileManagerViewModel.kt` |
| RUN ▶ server pipeline in the Output Panel (build → background server → Ready → auto-open preview) | `EditorViewModel.kt` |
| Output Panel “Open Preview” action (re-opens Web Preview while the server runs) | `OutputPanelView.kt`, `EditorScreen.kt`, `MainActivity.kt` |
| Web Preview live-server mode + port address bar + auto-reload of `index.html` | `WebPreviewScreen.kt`, `Screen.kt`, `MainActivity.kt` |
| Host tests | `ServerPortDetectorTest.kt`, `ServerRunnerTest.kt`, `ProjectScaffoldTest.kt`, `ProjectConfigTest` additions |

## 2. Design decisions

- **D1 — Servers are background processes, not batch runs.** `ServerRunner` mirrors
  `ExecutionRunner` (argv-list `shell -c`, merged stderr, daemon reader, reflective
  `destroyForcibly`, no Android imports) but never times out: the process lives until
  it exits or the user taps **Stop**. Cancelling the collection destroys the child
  (`awaitClose`); the EditorViewModel `onCleared`/`clearOutput`/`stopRun` all stop it.
- **D2 — Readiness via bind-line patterns, never “assume the port”.**
  `ServerPortDetector` accepts only lines that look like a *binding report*
  (`* Running on http://…`, `Uvicorn running on http://…`,
  `Serving HTTP on … port …`, `CodeC server listening on http://…`, generic
  `listening on http://…`), only for `127.0.0.1`/`0.0.0.0`, and rewrites `0.0.0.0`
  to `127.0.0.1`. A URL inside rendered content must NOT match (tested).
  20 s readiness window → honest “no port line detected” summary, and the
  configured `previewUrl` still powers the Open Preview action.
- **D3 — Presets are runnable out of the box.** `python-flask`/`python-fastapi`
  scaffold a dual-path server: the real framework when installed
  (`pkg install -y python-pip && pip install flask`), otherwise a stdlib
  `http.server` fallback serving the identical pages — so the device recipe works
  with only the Phase-12 `python` package, no repo build and no pip install.
  `c-microservice` is a dependency-free socket server compiled by the embedded TCC
  (`cc server.c -o bin/server`, `-o` last — invariant preserved).
- **D4 — Live editing works.** The page lives in `index.html`, read from disk on
  every request; the Web Preview watches it (like Phase 9.1) *and* the Refresh
  button re-fetches from the running server. C servers recompile on RUN (compiled
  binary holds the page) — documented honestly.
- **D5 — Backward compatibility.** `port`/`previewUrl` are optional and omitted
  from the JSON when null; old `project.json` files parse unchanged (tested);
  `createProject(name, type)` keeps its signature and historical C behaviour
  (byte-identical `main.c` starter moved into `ProjectScaffold`).
- **D6 — Config is still the source of truth.** `lastTerminalCommand` (Open in
  Terminal escape hatch) is `projectRunCommand(...)`; a server run keeps Stop /
  Copy / Clear / Open-in-Terminal exactly like Phase 11, and the stdin input row
  is hidden for server runs (no stdin for a web server).
- **D7 — Framework availability is not assumed, but also not silently done.**
  The fallback prints which framework it is using and how to install the real one.
- **D8 — Out of scope this round (follow-ups):** responsive mobile/desktop
  viewport toggle in Web Preview (plan §2.2 bullet), per-project server
  autostart on app open, and the long-tail toolchains (Node/Lua/Go/Rust) —
  the last requires the owner's explicit request and a `[repo-build]` dispatch.
- **D9 — Bundled demo project (owner request, 2026-08-31):** the app ships a
  ready `demo_flask` project (Flask preset + README.md) in the Files tab so it
  can be opened and RUN ▶ immediately — no wizard steps. Seeding is pure and
  host-tested (`DemoProjects.ensure`): runs once per app install (marker
  `.demo-flask-seeded-v1`), never overwrites a user's `demo_flask`, and
  deleting it does not make it reappear. `ProjectScaffold.writeFiles` is now
  the single write path for ProjectManager, the wizard and the demo.
- **D10 — Auto projects (owner request, 2026-08-31: "no selection … just
  created and run any type").** The New Project wizard's default is now
  **Auto (detect)**: creating a project needs no type choice and scaffolds no
  starter files; RUN ▶ infers the type from the user's own files. Detection
  is pure and host-tested (`ProjectRunDetector`): the actively open file wins,
  then a root scan — `app.py` → Flask server, `server.c` → C microservice,
  `main.py` → FastAPI server iff it imports fastapi/uvicorn else Python
  script, any `.html` → static Web, `main.c`/any `.c`/`.cpp` → C, any `.py`
  → Python, otherwise an honest "add a file" hint. Server and Web plans reuse
  the exact existing pipelines (`ProjectConfig.defaultFor(type)` +
  `startServerRun`; preview handler for static web), so an auto project
  behaves identically to the picked preset once detected; c/python fall
  through to the Phase 12 active-file run path with the preset as the
  project-level fallback. Each RUN re-detects, so files can change type
  freely (config stays `auto`).

## 3. Invariants

- No `.` on `PATH` — `ShellBootstrap.prepare` env is reused untouched.
- `cc server.c -o bin/server` — `-o` remains the last argument (TCC link order).
- No overwriting `cc`/bash; no official Termux packages; no repository metadata
  changes; no `/data/…` outside the app; nothing written into `$PREFIX/bin` by
  the new code.
- No expensive dispatch: Flask/FastAPI are `pip` packages; the C server uses the
  embedded TCC. The plan's “on-demand long-tail toolchains” (Node/Lua/Go/Rust)
  remain explicitly deferred until the owner requests one.

## 4. Files changed (client)

```
app/src/main/java/com/codeci/ide/MainActivity.kt            (navigation wiring)
app/src/main/java/com/codeci/ide/ui/components/OutputPanelView.kt   (Open Preview action)
app/src/main/java/com/codeci/ide/ui/navigation/Screen.kt            (Preview url arg)
app/src/main/java/com/codeci/ide/ui/projects/ProjectConfig.kt       (port/previewUrl, presets)
app/src/main/java/com/codeci/ide/ui/projects/ProjectManager.kt      (scaffold on create)
app/src/main/java/com/codeci/ide/ui/projects/ProjectScaffold.kt     (NEW: starter files)
app/src/main/java/com/codeci/ide/ui/projects/ProjectTypes.kt        (NEW: wizard options)
app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt         (server-ready → preview)
app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt    (wizard)
app/src/main/java/com/codeci/ide/ui/screens/WebPreviewScreen.kt     (live mode + address bar)
app/src/main/java/com/codeci/ide/ui/services/ServerRunner.kt        (NEW)
app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt   (server pipeline)
app/src/main/java/com/codeci/ide/ui/viewmodels/FileManagerViewModel.kt (createProject(type))
app/src/main/java/com/codeci/ide/ui/projects/DemoProjects.kt        (NEW — bundled demo_flask, D9)
app/src/main/java/com/codeci/ide/ui/projects/ProjectRunDetector.kt  (NEW — auto type detection, D10)
app/src/main/java/com/codeci/ide/ui/projects/ProjectTypes.kt        (auto first)
app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt   (auto plan routing + web preview handler)
app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt         (web preview handler wiring)
app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt    (wizard default = auto)
app/src/test/java/com/codeci/ide/ServerPortDetectorTest.kt          (NEW)
app/src/test/java/com/codeci/ide/ServerRunnerTest.kt                (NEW)
app/src/test/java/com/codeci/ide/ProjectScaffoldTest.kt             (NEW + writeFiles + auto)
app/src/test/java/com/codeci/ide/ProjectConfigTest.kt               (extended + auto)
app/src/test/java/com/codeci/ide/DemoProjectSeedTest.kt             (NEW — D9)
app/src/test/java/com/codeci/ide/ProjectRunDetectorTest.kt          (NEW — D10, 13 cases)
app/src/test/java/com/codeci/ide/ServerScaffoldE2ETest.kt           (+ auto → flask end to end)
```

## 5. Device recipe (owner, aarch64, latest green `Build APK` APK)

```sh
# 0. Install the APK (fresh or in-place), open the Files tab.
#    → `demo_flask` is ALREADY there (bundled, D9): app.py + index.html +
#      README.md + .codec/project.json (type python-flask, port 5000).
#    (Alternative A: “+ New Project” → name + “Flask Web Server”.)
#    (Alternative B — no type selection, D10: “+ New Project” → name only,
#     leave the default “Auto (detect)” — then inside the project add
#     app.py + index.html yourself, or copy them from demo_flask.)
# 1. Open app.py (observe the dual-path Flask/fallback code).
# 2. Ensure python3 is installed:  pkg install -y python   (Phase 12 package)
# 3. Tap RUN ▶ in the editor toolbar.
#    EXPECTED (Output Panel):
#      $ python3 app.py
#      * Running on http://127.0.0.1:5000/ (CodeC stdlib fallback; …)
#      summary: “Server running at http://127.0.0.1:5000”
#    then the app navigates to Web Preview automatically,
#    address bar shows “● live http://127.0.0.1:5000”.
#    (Auto projects detect the same way from app.py/main.py/server.c/
#     index.html/main.c — no type selection needed at creation.)
# 4. Web Preview shows “Welcome to CodeC Flask App!”.
# 5. Edit index.html (e.g. change h1 text) in the editor → Save.
#    → Web Preview auto-reloads (or tap the Refresh icon) → updated text.
# 6. Tap the Output Panel’s green preview icon (from the editor) → preview reopens.
# 7. Optional: pkg install -y python-pip && pip install flask →
#    app.py logs “(CodeC Flask)”; /api/hello returns JSON.
# 8. Tap Stop — the server dies; Run again starts it fresh.
# PASS = steps 0–6.
```

FastAPI (`python-fastapi`, port 8000; page = index.html; Swagger at `/docs` with
fastapi installed) and C microservice (`c-microservice`, port 8080,
`printf("CodeC server listening on http://127.0.0.1:8080")`) are checked the same
way (step 4–6 only; C needs a RUN again after editing `server.c`).

## 6. CI

`Build APK` **GREEN — run `33352164172`** (assemble `:app:assembleDebug` +
`:app:testDebugUnitTest` + `:app:lintDebug` via the gradle-bootstrap bridge).
Four earlier rounds failed, each caught by CI and fixed (evidence-first, as
recorded here):

| Run | Failure | Fix |
|---|---|---|
| `33351530009` | `ProjectScaffold` compile: `Const 'val' initializer must be a constant value` | Python templates are plain `val` (Kotlin `const val` cannot interpolate) |
| `33351638813` | compile: `Unresolved reference 'QCodeC'` | `$QCodeC` parses as the identifier `QCodeC` → `${Q}CodeC` |
| `33351751134` | test compile: missing `assertTrue` import | import added |
| `33351961497` | 5 unit-test failures | (a) templates printed bind URLs with `%d` — now the literal URLs (matches the device recipe); (b) `server exit` test raced the fast process exit — stay-alive sleep added; (c) disk test grepped `app.py` for the page text — page lives in `index.html` |

Host tests: `ServerPortDetectorTest` (10), `ServerRunnerTest` (7, real
processes via `/bin/sh`), `ProjectScaffoldTest` (7), `ProjectConfigTest` (+7),
plus **`ServerScaffoldE2ETest` (3)** — green on `33355693242`: writes the exact
scaffold bytes, runs the preset `build`/`run` commands from `ProjectConfig`
through `ServerRunner`, fetches `http://127.0.0.1:<port>/` over loopback HTTP
(200 + page text), **edits `index.html` and fetches again — new content without
restart**, then Stop → no live process. That automates the server half of the
recipe (§5 steps 3–7) on CI. The only acceptance step a runner cannot do is
the Compose UI itself (auto-open, ● live badge, Save → reload) — that stays
in the owner's device round on aarch64.
