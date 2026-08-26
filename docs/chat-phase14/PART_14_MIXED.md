# Phase 14.1 — Mixed-language & Long-tail
Status: planned · [repo-build] on demand · depends 12
Context: Phase 8 project model supports run-config; Phase 12 proves multi-language; Phase 10 proves catalog UI.
D1: Project type map: static web (files) → WebView direct; server (Python) → local server + WebView; generic → meta command. New languages added only when owner requests (each = one CI build, not automatic).
Sources: Phase 8 run-config, Phase 12 repo pipeline, WebView screen, local server (python3 -m http.server or Flask), codec-packages build system.
Exit device: Flask app in project folder; Run starts server; WebView shows `localhost:5000`; user requests Go → repo build dispatched; meta command `echo hello` works.
Evidence: §5.2 CI (build when requested), §5.3 device (transcript with server + WebView).
Not in 14: automatic installation of all languages; desktop-window frameworks; external cloud servers.
