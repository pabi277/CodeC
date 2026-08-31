# CodeC Phase 14 — Mixed-Language, Server WebViews & Long-Tail Ecosystem

**Status:** IMPLEMENTED (client-only, 2026-08-31, `arena/01a05421-codec`) — server
runner, port monitor, Web Preview live mode, presets + wizard are in; CI + device
round pending. See [PART_14_IMPLEMENTATION.md](PART_14_IMPLEMENTATION.md).
**Cost:** `[repo-build]` on-demand (only when adding new compiled toolchains like Go/Rust) / `[client-only]` for WebViews
**Depends on:** Phase 8 (Projects) + Phase 12 (Python & Multi-Language)

---

## 1. Context & Motivation

Modern web and full-stack development involves combining frontend web technologies (HTML, CSS, JavaScript) with local backend services (Python Flask/FastAPI, C CGI/microservers, or Node.js). 

Phase 14 delivers:
1. **Server-to-WebView Pipeline:** Running a local server (Flask, FastAPI, `http.server`, micro-httpd) automatically attaches to the in-app Web Preview screen (`http://127.0.0.1:<PORT>`).
2. **Project Type Presets:**
   - `web`: Static frontend (HTML/CSS/JS) with live hot reload.
   - `python-flask` / `python-fastapi`: Local REST API with Swagger UI in WebView.
   - `c-microservice`: Fast native C HTTP server.
3. **On-Demand Long-Tail Toolchains:** Recipes for Node.js, Lua, Go, or Rust built and published only when specifically requested by the project owner.

---

## 2. Architectural Design (Decision D1)

### 2.1 Project Type Runner
In `.codec/project.json` (implemented types: `web`, `python`, `python-flask`,
`python-fastapi`, `c-microservice`; `port`/`previewUrl` are optional and
back-compatible — `previewUrl` defaults to `http://127.0.0.1:<port>`):
```json
{
  "name": "flask_app",
  "type": "python-flask",
  "port": 5000,
  "entry": "app.py",
  "run": "python3 app.py",
  "previewUrl": "http://127.0.0.1:5000"
}
```

### 2.2 Server Port Monitor & WebView Integration
- When tapping "RUN" on a server project:
  1. Spawns background process runner.
  2. Monitors stdout/stderr for port binding (e.g. `Running on http://127.0.0.1:5000/` or `Uvicorn running on ...`).
  3. Automatically switches to or opens the **Web Preview** tab with the target URL.
  4. Web Preview includes reload, console log viewer, and responsive mobile/desktop viewports.

---

## 3. Implementation Steps

1. **Step 1:** Implement Server Process Monitor detecting port bindings in `ServerRunner.kt`.
2. **Step 2:** Update `WebPreviewScreen.kt` with live console inspection and port address bar.
3. **Step 3:** Add Project Wizard templates for Static Web, Flask Server, and FastAPI.
4. **Step 4:** Write unit tests in `ServerRunnerTest.kt` for port regex detection and URL construction.

---

## 4. Exit Condition & Verification Recipe

```sh
# Setup & Server WebView Test
# 1. In Files tab, create new project from template "Flask Web Server".
# 2. Open "app.py", observe Flask code.
# 3. Tap "RUN ▶" in toolbar.
# 4. Observe server starts in background: "Running on http://127.0.0.1:5000".
# 5. Observe Web Preview tab opens showing "Welcome to CodeC Flask App!".
# 6. Edit the HTML response in "index.html" (the page file app.py serves
#    from disk per request) -> Tap Reload in Web Preview -> Verify updated content.
# PASS
```
