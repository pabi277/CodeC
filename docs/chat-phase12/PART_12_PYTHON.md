# Phase 12.1 — Python + Editor Intelligence (ONE build)
Status: planned · [repo-build] · depends 10 (catalog UI) + 8 (projects) + 9 (editor)
Context: Only C is highlighted (hardcoded regex in CSyntaxVisualTransformation). Need a real multi-language step.
D1: Add python3 recipe to codec-packages; build on CI (~1–2h); publish archive; device installs via pkg. Editor: extend VisualTransformation with per-language tables (C, Python, JS, Go...) — start with Python. Autocomplete: scan current buffer for identifiers + small stdlib snippet table (no external server). Run-config presets for common commands.
Sources: codec-packages/build system, CI workflows, CSyntaxVisualTransformation, repo signing keys (unchanged), device bootstrap (userland-v2-dev unchanged).
Exit device recipe: create test.py; open in editor; observe Python syntax colors; type "def " → snippet or identifier suggest; Run button executes `python3 test.py`; compile-style error at line 3 parsed to red squiggle; repo build passes (check CI run); device installs python3 from new catalog.
Evidence: §5.1 host (highlight + autocomplete logic), §5.2 CI (repo build run + publish), §5.3 device (full transcript + package install).
Not in 12: other languages (add only when needed — 14); pip (deferred); LSP servers (heavy, deferred).
Steps when confirmed: 1) write python3 build override; 2) CI dispatch; 3) verify archive; 4) publish; 5) implement highlight + autocomplete; 6) device verify; 7) wait for PR.
