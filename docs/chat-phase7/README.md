# CodeC Phase 7 Documentation — Multi-Terminal Sessions

Phase 7 brings native **Multi-Terminal Sessions** (`[client-only]`) to CodeC, allowing developers to run multiple independent shells concurrently (e.g. running builds/servers in background while editing or running git commands in foreground).

## Contents & References

- **[Part 7.1 — Multi-Terminal Session Manager & Switcher](PART_7_MULTI_TERMINAL.md)** — Architectural design, session manager model, drawer UI, and verification recipes.

## Key Invariants
- Each terminal session owns its independent PTY master/slave pair and VT ANSI buffer.
- Activity-scoped lifecycle ensures background session processes keep running when switching tabs.
- `CodeCApi` bridge requests route cleanly per session.
- Client-only implementation (no package repo rebuild required).
