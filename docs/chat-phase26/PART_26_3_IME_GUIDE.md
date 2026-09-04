# CodeC Phase 26.3 — Code-friendly IME Guide Panel

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** none
· **Target files:** Settings screen, `strings.xml`, docs links only — no
   keyboard code

---

## 1. Design

The research surfaced a fact CodeC should *say out loud* instead of fighting:
**no editor-side strip can fully replace a code-oriented IME**, and the two
most-recommended open-source ones are [Hacker's Keyboard](https://github.com/klausw/hackerskeyboard)
(5-row PC layout, real Tab/arrows/Ctrl; Apache-2.0) and
[Unexpected-Keyboard](https://github.com/Julow/Unexpected-Keyboard) (swipe-anywhere
symbols; users recommend it for coding specifically). FlorisBoard/Gboard add
spacebar-slide caret control. This part is a **guide panel**, not an IME fork
(a fork would violate clean-room + maintenance sense).

Panel: Settings → Editor → "Keyboard recommendations" card:
- three-row list (name, why it's good for code, GPL/Apache badge, F-Droid/Play
  link — external, user chooses);
- tips list: enable spacebar-slide; disable autocorrect/capitalization for the
  CodeC editor field only via `KeyboardOptions` (autoCorrect=false,
  capitalization=NONE — *this is editor-side and lands in this part*);
- hardware-keyboard row: "connected → shortcuts on (Phase 24.3)" status line.

Editor-side IME flags (the only code): for the code field — no autocorrect, no
autocapitalize, no suggestion strip from the IME, monospace-capable class —
verified per IME since some ignore flags (that variance is documented in the
panel text, honestly).

## 2. Implementation steps

1. `KeyboardOptions` audit on the editor field (+ composing region sanity on
   candidate core).
2. Settings card with links + tips (localized strings).
3. HW-keyboard status row (existing 24.3 detection reused).

## 3. Exit condition

```text
(Device)
1. Settings → Editor shows the card; links open externally.
2. With Gboard focus in the editor: no dictionary underline, no autoshift
   storms, long-press still opens strip popups (26.1) — IME flags verified.
3. With Hacker's Keyboard: Tab inserts indent, arrows move caret, Ctrl+S saves
   (24.3 shortcut layer).
PASS = all three.
```
