# CodeC Phase 12 — Multi-language: Python + Editor Intelligence
Status: planned · cost [repo-build] (ONE ~1–2h CI run) · depends 10 + 8 + 9
Source refs: codec-packages/recipes, CSyntaxVisualTransformation.kt, TerminalPlan §C, docs/chat-phase10/ + 9/
D1: Native Compose editor (not WebView); python3 added to repo/CI; language detect by extension + shebang; per-language highlighting engine (keyword/string/comment tables); light autocomplete (buffer identifiers + stdlib snippet); run-config presets (python3, python3 -m flask); error parsing per language.
Exit device: .py opens with Python colors; autocomplete suggests; Run executes python3; error parsed; repo build passes; device installs from new catalog.
Not in scope: full IntelliSense / LSP (deferred to future if needed), pip inside userland (pkg-first), WebView editor (deferred).
