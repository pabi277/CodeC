# CodeC Phase 15 — CodeCApi Tail + Polish
Status: planned · cost [client-only] · depends Phase 7 + 6
Source refs: docs/chat-phase5/ (4.7/4.8 bridge), Phase 6 (terminal UX stable), MainActivity.kt (permissions/theme)
D1: Four new capabilities over bridge: sensors / camera / intents + any remaining Phase 5.3 gaps; optional session persistence across restart; final polish (font discoverability, theme parity, title/cwd static fixes if any remain).
Exit device: `codec-sensor` reads accelerometer; `codec-camera` takes photo; `codec-intent` opens other apps; all over per-session bridge (Phase 7); no regression in clipboard/notify/toast/share/open/URL/vibrate.
Not in scope: root acceleration, full catalog mirroring, GUI packages (SDL/Qt), X11.
