# Phase 8.1 — Projects & Files (Keystone)
Status: planned · [client-only] · depends 7 · 8 is dependency for 9/11/12/13
Context: FileManager is flat; no project concept; terminal cwd is disconnected from file manager selection.
D1: Folder tree (tap folder → list inside); "New folder"; import (SAF OpenDocument → copy into private project); export (SAF CreateDocument); ZIP pack/unpack; run-config file in project root (JSON: {command, type}) used by Phase 11 Run button.
Sources: FileManagerScreen.kt (flat list), Android SAF APIs (Storage Access Framework, no permissions needed on 13+), EditorScreen.kt (file path tracking), TerminalSession/TerminalViewModel (cwd sync).
Exit device recipe: mkdir $PREFIX/project/demo; echo 'int main(){}' > $PREFIX/project/demo/main.c; open in editor; tap Run (uses run-config); observe output; export ZIP to Downloads; terminal cd $PREFIX/project/demo; PASS.
Evidence: §5.1 host (folder tree UI, SAF flow), §5.2 CI (build), §5.3 device (full cycle transcript).
Not in 8: GitHub clone (13), Python (12 — needs repo build), editor undo/find (9 — can start after folder model exists).
Steps when confirmed: propose D1 (folder + run-config format), verify SAF APIs, implement, test import/export, device verify.
