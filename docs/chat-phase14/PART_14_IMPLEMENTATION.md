# Phase 14 — Implementation Record (Part 14.1: Server Runner, Port Monitor & Web Preview Integration)

**Status:** IMPLEMENTED (client-only, `arena/01a05421-codec`) — CI round + device recipe pending.
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
app/src/main/res/values/strings.xml                                 (server strings)
app/src/test/java/com/codeci/ide/ServerPortDetectorTest.kt          (NEW)
app/src/test/java/com/codeci/ide/ServerRunnerTest.kt                (NEW)
app/src/test/java/com/codeci/ide/ProjectScaffoldTest.kt             (NEW)
app/src/test/java/com/codeci/ide/ProjectConfigTest.kt               (extended)
```

## 5. Device recipe (owner, aarch64, latest green `Build APK` APK)

```sh
# 0. Install the APK (fresh or in-place), open Files → “+ New Project”
# 1. Name it e.g. `demo_flask`, choose “Flask Web Server”, create.
#    → .codec/project.json has type python-flask, port 5000, previewUrl
#    → files: app.py + index.html
# 2. Open app.py (observe the dual-path Flask/fallback code).
# 3. Ensure python3 is installed:  pkg install -y python   (Phase 12 package)
# 4. Tap RUN ▶ in the editor toolbar.
#    EXPECTED (Output Panel):
#      $ python3 app.py
#      * Running on http://127.0.0.1:5000/ (CodeC stdlib fallback; …)
#      summary: “Server running at http://127.0.0.1:5000”
#    then the app navigates to Web Preview automatically,
#    address bar shows “● live http://127.0.0.1:5000”.
# 5. Web Preview shows “Welcome to CodeC Flask App!”.
# 6. Edit index.html (e.g. change h1 text) in the editor → Save.
#    → Web Preview auto-reloads (or tap the Refresh icon) → updated text.
# 7. Tap the Output Panel’s green preview icon (from the editor) → preview reopens.
# 8. Optional: pkg install -y python-pip && pip install flask →
#    app.py logs “(CodeC Flask)”; /api/hello returns JSON.
# 9. Tap Stop — the server dies; Run again starts it fresh.
# PASS = steps 1–7.
```

FastAPI (`python-fastapi`, port 8000; page = index.html; Swagger at `/docs` with
fastapi installed) and C microservice (`c-microservice`, port 8080,
`printf("CodeC server listening on http://127.0.0.1:8080")`) are checked the same
way (step 4–6 only; C needs a RUN again after editing `server.c`).

## 6. CI

`Build APK` run — see §“CI” of this phase's README / JOURNEY once posted.
