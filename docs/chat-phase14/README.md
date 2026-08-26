# CodeC Phase 14 — Mixed-language & Long-tail
Status: planned · cost [repo-build] on demand · depends Phase 12
Source refs: docs/chat-phase12/, Phase 8 project types, WebView, local server
D1: Project type "server" (Python Flask/FastAPI) → background `python3 app.py`; WebView opens `http://127.0.0.1:PORT`; add Go/Node/Rust to repo only when explicitly requested (one build each); meta run command (user-defined start command) for any tool.
Exit device: Flask project runs; WebView shows page; add Go to repo only when user asks; meta command executes.
Not in scope: full desktop-framework windows (Tkinter/PyQt impossible), external server dependencies, automatic multi-language build chains.
