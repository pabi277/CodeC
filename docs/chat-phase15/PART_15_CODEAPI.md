# Phase 15.1 — CodeCApi Tail + Final Polish
Status: planned · [client-only] · depends 7 (multi-session routing) + 6 (terminal UX complete)
Context: Phase 5.3 delivered toast/share/open-url/vibrate; roadmap names sensors/camera/intents as later; Phase 7 defines per-session bridge routing.
D1: Add wire ops `sensor.read`, `camera.capture`, `intent.open`; each = one CLI script + bridge op; permissions: sensors/camera need runtime/dialog; intents use existing Activity launcher; polish: font settings more discoverable, theme parity complete.
Sources: Phase 4.7/4.8 bridge docs (docs/chat-phase4/), Phase 5.3 script templates (docs/chat-phase5/), MainActivity (permissions/dialog patterns from 4.8), TerminalViewModel (session routing from 7).
Exit device: each new capability passes on device with per-session bridge; clipboard/notify still work in other session; theme/font settings accessible; PASS.
Not in 15: new package builds; bootstrap changes; PR/merge without command.
