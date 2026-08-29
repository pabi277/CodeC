====================================================================
CodeC — FUTURE IDEAS / IMPLEMENTATION BACKLOG  [FROZEN — 2026-08-29]
====================================================================

> STATUS: FROZEN AS HISTORY. All items from this file have been
> reviewed, classified (done / deferred / folded into plan), and
> either completed (Phase 3–5) or moved into the new master roadmap
> [`TERMINAL_PLAN.md`](TERMINAL_PLAN.md) (Phases 6–15, updated 2026-08-26).
> Do NOT add new items here — add them to `TERMINAL_PLAN.md` §13 (out-of-scope / deferred) or open a Phase 6+ part.
> Session rule still applies: no PR/merge without explicit owner command.

--------------------------------------------------------------------
COMPLETED / IMPLEMENTED / MERGED (do not redo)
--------------------------------------------------------------------
- Phase 3 (A–D): bootstrap, signing, device acceptance → see `JOURNEY.md` §5, PR #15.
- Phase 4 (4.1–4.8): storage, install UX, trust, settings, catalog, CodeCApi clipboard+notify → `chat-phase4/`, PR #22.
- Phase 5 (5.1–5.3): KI fixes, web preview, capability batch → `chat-phase5/`, PR #23.
- Backlog sections A1 (share/open/URL/vibrate/toast) → COMPLETED in 5.3.
- Backlog A2 (KI-1, KI-2) → COMPLETED in 5.1.
- Backlog F (web preview) → COMPLETED in 5.2.
- Phase 8 project work (D/E/F extensions) → IMPLEMENTED in PR #27;
  core device workflows confirmed, final export/re-import round trip tracked
  in `docs/chat-phase8/PART_8_DESIGN_DECISIONS.md` before merge.

--------------------------------------------------------------------
DEFERRED (kept in TERMINAL_PLAN.md §13)
--------------------------------------------------------------------
- X11 / SDL / Qt (GUI packages) — text-first terminal; only if real demand.
- Full Termux catalog mirroring — needs cardinality decision; not before 5.3 + 6–15.
- Root-based acceleration — out of scope by policy.
- Optional x86_64 repeat of Phase 3 Part D — opportunistic, not blocking.

--------------------------------------------------------------------
FOLDED INTO TERMINAL_PLAN.md (Phases 6–15)
--------------------------------------------------------------------
- B (multi-language) → Phase 12 (Python first, light autocomplete, per-language highlighting).
- C (CLI -> GUI) → Phase 10 (pkg catalog INSTALL buttons) + Phase 11 (output panel + Run) + Phase 9 (editor buttons) + Phase 13 (GitHub buttons).
- D (mixed-language projects / run-config) → Phase 8 (project model with run-config).
- E (file import/export/ZIP/folders) → Phase 8 (folder tree, SAF import/export, ZIP).
- F (web preview / local server + WebView) → Phase 8 (project type: static web / Python server) and can be picked early if desired.
- Additional gaps found 2026-08-26 (terminal cutout/insets, extra-keys, wake lock, URL tap, selection copy, multi-terminal, editor undo/find/format) → Phase 6 (terminal UX) + Phase 7 (multi-terminal) + Phase 9 (editor).
- More CodeCApi (sensors/camera/intents) → Phase 15.

--------------------------------------------------------------------
ORIGINAL BACKLOG CONTENT (preserved for audit)
--------------------------------------------------------------------

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
2. DELIVERED IN PR #27 (core implementation; final round-trip device gate is
   recorded in the Phase 8 completion record):
   - IMPORT a single file into a project: Android SAF OpenDocument picker
     copies it into the private project.
   - EXPORT / "Save as": Android SAF CreateDocument picker writes the project
     ZIP only after an explicit user action.
   - CREATE FOLDER for projects: real hierarchical tree with open/close,
     nested creation, rename, and delete; editor follows the selected path.
   - IMPORT/EXPORT WHOLE PROJECT as ZIP: ZIP import extracts all file types and
     preserves the complete tree; export writes paths relative to the project.
   - Projects overflow actions: Import Folder, Import ZIP, Refresh Projects,
     Refresh/collapse folders, Import File, Export ZIP, and New File.
   - HTML/HTM files: Preview and Set as default run; web Run opens the stored
     project entry page.
   - Terminal project listing and project-relative build/run handoff.
3. REMAINING / DEFERRED:
   - "Open with CodeC" from other apps (receive intent filter) remains deferred.
   - Terminal shortcut: `cp` from `~/storage/downloads/file.py` remains available
     as a terminal workflow rather than a separate GUI button.
4. Privacy story stays: private by default; import = copy into private
   folder; export only on explicit tap; nothing auto-public.
5. Android 13+ SAF pickers need NO storage permissions (cleaner than
   old model).

--------------------------------------------------------------------
F. WEB PREVIEW, SPCK-STYLE (HTML + CSS + JS)
--------------------------------------------------------------------
1. FACT: Android DOES support HTML with external CSS/JS files — the
   issue is HOW the page is loaded, not a platform limit.
2. DELIVERED: Phase 5.2 provides the in-app WebView preview with sibling
   CSS/JS loading, live reload, and console output. PR #27 adds project-aware
   HTML/HTM Preview plus Set as default run; the web project Run action opens
   the configured entry page.
3. REMAINING / DEFERRED:
   - Local-server/project-type runners for Python/Node backends belong to
     Phase 12/14.
   - Device-size preview controls and other future WebView polish remain
     deferred.
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
