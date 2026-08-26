# Phase 13.1 — GitHub Integration
Status: planned · [client-only] · depends 8 (folder) + 11 (output feedback)
Context: Settings can install APK from GitHub; Modules fetches from GitHub; but no clone/commit/push in app.
D1: Screen with URL input; "Clone" runs `git clone <url>` into new project folder (using Phase 8 folder model); commit button: `git commit -m "msg"`; push button: `git push`; token saved in encrypted/app-private prefs (same storage pattern as other settings); no token in logs or `~/`.
Sources: MainActivity.kt (Settings screen), FileManagerScreen, git binary in userland (from pkg), Phase 8 folder tree.
Exit device: clone pabi277/CodeC → edit README → commit "update" → push → success message; Settings removes token; PASS.
Not in 13: full GitHub API (REST/GraphQL) — only git CLI over SSH/HTTPS; PR creation via terminal available.
