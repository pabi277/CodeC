# Phase 7.1 — Multi-terminal Sessions
Status: planned · [client-only] · depends 6 · no build
Context: TerminalViewModel holds ONE TerminalSession; PtySession wraps one PTY; nonce is recomposition hack not session ID. Need native multi-session like Termux.
D1: TerminalSessionManager (map of sessions); each with own PtySession + emulator; session drawer/switcher; "+" creates new; bridge answers per session (session ID from request file path or session index).
Sources: TerminalViewModel, TerminalSession, PtyNative, TerminalScreen (routing), CodeCApi bridge (session routing), docs/chat-phase6/ (terminal UX must be stable first).
Exit device recipe: open terminal 1 → `bash -c 'while true; do sleep 1; done'`; open + new session 2 → `ls`; switch; clipboard from 2; PASS.
Evidence: §5.1 host (session manager state), §5.2 CI (compile), §5.3 device (transcript with 2 sessions).
What is NOT in 7: folder tree (8), editor undo (9), pkg GUI (10), output panel (11), Python (12), GitHub (13).
Steps when confirmed: 1) read sources; 2) design session manager; 3) implement; 4) host-test; 5) CI; 6) device verify; 7) wait for PR command.
