# CodeC Phase 11 — Output Panel + Run Button (Spck/C4droid feel)
Status: planned · cost [client-only] · depends Phase 8 + 9
Source refs: EditorScreen.kt, TerminalViewModel.kt, run-config model (Phase 8), docs/chat-phase9/
D1: Editor-screen layout (editor top / scrollable output bottom); Run button executes run-config command in background; clickable error lines jump to editor; Terminal tab preserved for interactive.
Exit device: Run C file → output below; error at line 14 tappable → jumps; Terminal still works.
Not in scope: new compiler (use existing cc/pkg), multi-language (12), WebView server (14).
