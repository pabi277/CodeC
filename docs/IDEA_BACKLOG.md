====================================================================
CodeC — FUTURE IDEAS / IMPLEMENTATION BACKLOG
(collected 2026-08-26 · Phase 4 is DONE & merged · everything below
is OPTIONAL future work, choose any slice when you want it)
====================================================================

© SESSION RULES (must stay active in every new chat)
- No PR, no merge, no push to main WITHOUT the owner's explicit
  command in chat. Commits + pushes to the session branch are OK.
- Work on the session branch only; one PR at a time.
- No code changes unless the owner asks. Verify state first
  (git status, gh pr list, gh run list) before acting.

--------------------------------------------------------------------
A. PHASE 5 ROADMAP CANDIDATES (already in docs/PHASE5_ROADMAP.md)
--------------------------------------------------------------------
1. More Termux:API-style capabilities over the existing CodeCApi
   bridge (one wire op + one CLI script + BOOTSTRAP_VERSION bump each):
   - share sheet / open URL / vibrate / toast
   - sensors / camera / intents (later)
2. Known small client fixes from the 4.5/4.6 review:
   - KI-1: `pkg install` reports FAILURE when package is already at
     newest version -> treat apt's "0 newly installed" as success.
   - KI-2: device $PREFIX (/data/user/0/...) vs dpkg-recorded
     /data/data/... spelling confuses manual `update-alternatives`
     -> canonicalize PREFIX at shell setup.
3. X11 / GUI packages (SDL, Qt) — explicitly deferred, big, optional.
4. Full Termux catalog mirroring — big, needs scope decision.
5. Root-based acceleration — out of scope by policy.
6. Optional: repeat Phase 3 Part D clean-device test on x86_64
   (no x86 device available yet; not blocking).

--------------------------------------------------------------------
B. MULTI-LANGUAGE FULL CODE EDITOR ("all common languages")
--------------------------------------------------------------------
1. Goal: run + highlight + errors for many languages, not a rewrite.
   The core (terminal, userland, pkg, compiler runner) is already done.
2. Per-language pattern (repeats):
   - Add tool package to userland: python3, nodejs, golang, rust,
     javac+java, gcc/clang (have), php, ruby, etc.
   - Map the Run button to that tool (python3 file.py, go run,
     cargo run, javac+java, node file.js, php file.php...).
   - Syntax highlighting per language + language settings in editor.
   - Handle per-language build/run quirks.
3. Autocomplete options (all free / open source):
   - LSP servers: clangd (C/C++), Pyright (Python, MIT),
     typescript-language-server, gopls (Go), rust-analyzer.
   - Tree-sitter (used by Neovim/Zed): small, fast, good mobile fit —
     the recommended "light" route.
   - Embedding Monaco (VS Code's editor, MIT) or CodeMirror 6 (MIT)
     is possible if you want VS Code's in-editor feel.
4. VS Code-style "extension system" — LIGHTWEIGHT version (realistic):
   - An "extension" = language pack: grammar + theme + completion
     server + run command.
   - Distribute via the existing Modules/manifest pattern (catalog),
     tap INSTALL -> pack loads. Reuses the existing pkg repository.
   - TRUE VS Code marketplace compatibility = recreate the extension
     host (big project) — NOT recommended as a first step.
5. Phone reality: fast completion for C/Python/JS/Go; heavy servers
   (rust-analyzer, jdtls) are slow; build big projects is slow.
6. Recommended order: (1) coloring per language (cheap, big win),
   (2) light completion (Tree-sitter or one small LSP), (3) full
   IntelliSense per language only where worth it.

--------------------------------------------------------------------
C. CLI -> GUI (buttons for commands, like Spck / C4droid)
--------------------------------------------------------------------
1. Click-to-INSTALL for packages/tools (no typing):
   - Catalog screen listing packages/programming tools -> INSTALL
     button -> progress bar -> Done.
   - Behind the scenes runs the same `pkg install` as terminal.
   - Friendly error messages instead of raw terminal text.
   - Fits existing Modules screen pattern perfectly.
2. GitHub via buttons AND terminal:
   - "GitHub" screen: paste repo URL -> CLONE button -> files appear
     in the file manager.
   - Connect account (token saved in Settings) -> COMMIT / PUSH
     buttons — no git commands typed.
   - Terminal still works for everyone who prefers typing.
   - Existing pieces: Settings already installs APK from GitHub;
     Modules/manifest already fetches from GitHub.
3. Output panel (NOT a terminal) — the biggest Spck/C4droid feel:
   - RUN button -> app compiles/runs in background.
   - Scrollable OUTPUT panel below the editor (editor on top,
     tap to expand output) — not a terminal.
   - Errors become clickable: tap "line 12" -> jumps to that line.
   - Keep the Terminal tab for interactive things
     (REPLs, vim, nano, prompts).
   - Output panel = run-and-print programs; Terminal = interactive.
4. Recommended order:
   - Part 1: Output panel + Run button for C (smallest, biggest win —
     run plumbing already exists).
   - Part 2: Package catalog with install buttons (Modules pattern).
   - Part 3: GitHub screen (clone + push buttons, token in Settings).

--------------------------------------------------------------------
D. MIXED-LANGUAGE PROJECTS (e.g. HTML + Python)
--------------------------------------------------------------------
1. Key idea: a project is not "in a language" — it's a bundle of
   files + ONE command that starts it. Need a per-project
   "run configuration / output type".
2. Project types and how Run works:
   - Static web (HTML+CSS+JS, files only) -> open in built-in
     WebView. EASY.
   - Python + Flask/FastAPI (server) -> `python3 app.py` in
     background, open http://127.0.0.1:PORT in WebView. EASY-MEDIUM.
   - Python Django / Node+Express / React+Vite -> build step first,
     then serve. MEDIUM (needs Node, heavier).
   - Python UI libs (Tkinter, PyQt) -> cannot show desktop windows on
     Android. NOT POSSIBLE directly (Kivy/Plyer ports are a rabbit
     hole).
   - Mixed compiled languages (C + Python, C++ + Go) -> compile each,
     runner script ties them together. YES — that's what the shell is
     for.
   - Generic "meta" run -> user-defined start command. YES — the
     robust general answer.
3. What already helps: real shell + userland + pkg (install python,
   Node, sqlite, local web server), ports/network on device
   (127.0.0.1 works).
4. Honest limits: phone hardware for big builds; desktop-window
   frameworks can't show; anything needing a real external server is
   out of scope (but WebView can still show the UI if app talks to it).

--------------------------------------------------------------------
E. FILE MANAGEMENT — SPCK-STYLE IMPORT / EXPORT / PROJECTS
--------------------------------------------------------------------
1. ALREADY EXISTS:
   - Files live in app-private data (invisible to other apps).
   - File Manager screen: create file, list, delete, rename, Share
     (share sheet out).
   - ~/storage symlinks (Downloads, Documents, DCIM, Pictures...)
     from Phase 4.1 — reach your private files from file apps.
   - createDirectory helper exists; migrate-existing-.c-files path.
2. TO ADD (all standard Android features):
   - IMPORT a single file into a project: tap Import -> Android SAF
     picker (OpenDocument) -> pick from Downloads/Documents ->
     copy into private project folder. EASY.
   - EXPORT / "Save as": Android SAF CreateDocument picker -> user
     chooses destination (Downloads, Drive, any folder) -> app writes
     file there. (Existing Share is a light version.) EASY-MEDIUM.
   - CREATE FOLDER for projects: real folder tree (tap folder ->
     open -> files inside) + "New folder" button; editor must follow
     the selected folder. MEDIUM (mostly UI + path handling).
   - IMPORT/EXPORT WHOLE PROJECT as ZIP: Import zip -> unpack into new
     project folder; Export zip -> zip project + share/save. MEDIUM.
   - "Open with CodeC" from other apps (receive intent filter).
     MEDIUM.
   - Terminal shortcut: cp from ~/storage/downloads/file.py (works
     even before GUI buttons exist).
3. Privacy story stays: private by default; import = copy into private
   folder; export only on explicit tap; nothing auto-public.
4. Android 13+ SAF pickers need NO storage permissions (cleaner than
   old model).

--------------------------------------------------------------------
F. WEB PREVIEW, SPCK-STYLE (HTML + CSS + JS)
--------------------------------------------------------------------
1. FACT: Android DOES support HTML with external CSS/JS files — the
   issue is HOW the page is loaded, not a platform limit.
2. Spck's actual approach (two parts, both standard):
   - Runs a tiny LOCAL WEB SERVER on the phone serving the project
     folder (e.g. http://127.0.0.1:8080) — can already be done in
     the userland with `python3 -m http.server` or Node.
   - Shows that URL inside an embedded WebView (Chrome engine via
     Android's WebView updates) with its own toolbar
     (refresh, live reload, device-size buttons).
3. What to add in CodeC:
   - "PREVIEW" button on HTML files. EASY.
   - WebView screen with refresh + live-reload. EASY-MEDIUM.
   - External CSS/JS working: base-URL loading
     (loadDataWithBaseURL) for static sites — NO server needed —
     OR the local-server route. EASY-MEDIUM.
   - Live reload on save (watch file changes -> reload page).
     MEDIUM.
   - Console / JS errors shown in the Output panel
     (WebView onConsoleMessage). MEDIUM — nice Spck-like win.
   - Multiple pages / whole web project -> just point server at the
     project folder. EASY once server exists.
4. Honest limits:
   - WebView is Android's Chromium WebView, not desktop Chrome —
     most modern HTML/CSS/JS works, exotic desktop APIs may not.
   - Node modules / build steps (React, Vite) need Node + build step;
     small projects fine, heavy ones slow on phone.
   - Local-server + WebView on Android is PROVEN (Termux does it);
     CodeC already has the right infrastructure.
5. This is one of the EASIEST big-feeling wins: a Run/Preview button
   that opens the project in an embedded browser with working CSS/JS,
   live reload, and console output — instantly makes it a real web IDE.

--------------------------------------------------------------------
G. SUGGESTED EXECUTION ORDER (my recommendation)
--------------------------------------------------------------------
1. Web preview (F) — biggest feel for least work.
2. Click-to-install catalog (C1) — fits Modules pattern.
3. Output panel + Run button for C (C3) — Spck/C4droid feel.
4. Spck-style file import/export/ZIP/folders (E) — makes it a real
   project IDE.
5. Multi-language support (B) — one language first (Python or JS),
   then the next; don't do "all languages" at once.
6. GitHub screen clone/push (C2).
7. KI-1 + KI-2 small fixes (A2) — anytime, low risk.
8. More CodeCApi capabilities (A1) — share, URL, vibrate, toast.

====================================================================
END OF BACKLOG
====================================================================
