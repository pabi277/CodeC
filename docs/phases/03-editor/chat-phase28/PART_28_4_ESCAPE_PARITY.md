# CodeC Phase 28.4 — Prose Escape Hatch, Accessibility & Parity

**Status:** 📋 PLANNED — gated on 28.1 go · **Cost:** `[client-only]` ·
**Effort:** M · **Depends on:** 28.2/28.3
· **Target files:** `ui/keyboard/*`, `ui/screens/EditorScreen.kt` (mount),
   Settings, seekbar/labels, host + accessibility tests

---

## 1. Design

The part that keeps CodeC Keys honest. Four laws, each with a test:

### L-ESCAPE — prose escape hatch (one tap, both directions)
- Row-0 rightmost "🔤" cap summons the **system IME** over CodeC Keys (for
  commit messages, comments, search, file names, passwords, emoji, voice
  typing). Back/gesture dismisses IME → CodeC Keys returns. State is
  ephemeral per editor focus (never persists "IME mode" across files).
- Search/find fields, rename dialogs, commit message box, terminal prompts:
  these **keep the system IME permanently** — CodeC Keys binds ONLY to the
  code editor surface. (This is what "only for code" means in practice —
  other inputs want prose keyboards.)
- Password/token entry (GitHub token fields): NEVER CodeC Keys — an app-drawn
  keyboard on credentials is a security smell worth an explicit invariant test.

### L-A11Y — accessibility is our job now
- Every cap: `contentDescription` ("tab key", "semicolon"), explore-by-touch
  ordering equals visual order, min touch target 44×44 dp effective.
- Editor content remains readable to TalkBack with IME suppressed (28.1 spike
  answered the mechanism; this part asserts it on device) and switch-access
  keyboard focus traverses CodeC Keys after the editor.
- No gesture-only actions: popup/flick targets also reachable via long-press
  menu or double-tap alternatives.

### L-HW — hardware keyboard parity is untouchable
- HW keyboard connected → CodeC Keys auto-collapses to the row-0 strip
  (suggestions/macros) leaving max editor space; 24.3 shortcuts unaffected.
  Disconnect → full keyboard returns. (Matches Squircle/Termux convention of
  not drawing keys over a物理 keyboard.)
- IME-based assistants/dictation are reachable via L-ESCAPE by definition.

### L-SET — settings & consent
- Settings → Editor → "CodeC keyboard": ON/OFF master (default ON once),
  haptics, key-height, layout preview, per-language macro rows toggle.
- First-run coach mark: where the 🔤 cap is and that code keys live now —
  shown once, dismiss persisted.
- Analytics-free; no keystroke logging, ever (privacy posture from §9).

## 2. Implementation steps

1. IME-summon bridge + scoped binding table (which surfaces own CodeC Keys vs
   system IME) as a pure config + tests (token field = system, terminal =
   system, editor = CodeC).
2. Accessibility pass: descriptions, traversal, TalkBack tests (device),
   alt-paths for gestures.
3. HW keyboard connect/disconnect listener → keyboard collapse/expand;
   regression: 24.3 shortcut suite.
4. Settings rows + coach mark + persistence.
5. Full-device week-dogfood by owner before any merge conversation.

## 3. Exit condition

```text
(Device)
1. 🔤 cap → Gboard appears; commit message typed with glide; dismiss → CodeC
   Keys back; focus lost/regained never resurrects stale mode.
2. Find / rename / commit / GitHub-token screens all show SYSTEM IME (pinned
   by table test + device check).
3. TalkBack: explore keyboard by touch reads labels; editor text readable;
   every popup/flick action has a non-gesture path.
4. BT keyboard attached → only row-0 strip drawn; Ctrl+S etc. work; detach →
   full CodeC Keys returns.
5. Settings OFF → system IME everywhere, as Phase 22.x (no residue).
PASS = all five. The phase ships ONLY with all L-laws green.
```
