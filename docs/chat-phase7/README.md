# CodeC Phase 7 — Multi-terminal Sessions
Status: planned · cost [client-only] · depends Phase 6
Source references: TerminalViewModel.kt, TerminalSession.kt, PtyNative.kt, TerminalScreen.kt, docs/chat-phase6/
D1: Session manager (N sessions + drawer/switcher/"+"), per-session PTY + emulator, CodeCApi routed per session; session list persists across screen changes (not app restart — matches Termux).
Exit (device): open 2+ terminals with different commands, switch via drawer, clipboard from second tab.
Not in scope: session persistence across restart (optional), multi-pane split, project folder tree (Phase 8).
