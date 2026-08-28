# Phase 11.1 — Output Panel + Run
Status: planned · [client-only] · depends 8 (run-config) + 9 (editor ready)
Context: No run button; user must switch to terminal and type `cc file.c && ./a.out`. Spck/C4droid feel needs one-tap run with output below editor.
D1: Layout: EditorScreen split or overlay; bottom pane shows stdout/stderr from background process (managed by TerminalViewModel or new background runner); Run button reads project run-config; on error, parse line/col and make clickable; keep Terminal tab for interactive sessions.
Sources: EditorScreen.kt (layout), TerminalViewModel (process management), Phase 8 run-config format.
Exit device: open main.c; tap Run; see output; error at line 14 → tap → editor jumps; Terminal tab opens interactive bash; PASS.
Evidence: §5.1 host (layout + click), §5.2 CI, §5.3 device.
Not in 11: Python (12 — needs repo build); multi-terminal routing (7 — but output panel can run in current session).
