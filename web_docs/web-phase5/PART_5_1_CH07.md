# CodeC Website Phase W5.1 — Chapter 07: The Editor

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4
· **Target file:** `website/ch-07.html`

> Source: `README.md` — "Editor foundation" + "Spck-style editor" bullets;
> W4.2 verified facts (exact UI names, autosave delay).

---

## 1. Content

- **Goal box:** edit like the editor was made for it — tabs, undo, find &
  replace, format, error squiggles, the extra-keys row.
- **Need:** Chapters 02, 06 done.

### Steps

1. **Tabs are your open files** — open three files in a project; the tabs
   in the app bar; the **dirty dot** means unsaved (autosave saves ~2 s
   after you stop typing — the dot is a *preview*, not an alarm); close
   tabs; save-all; per-tab undo/redo (undo groups your typing bursts).
2. **Find & replace** — open find; literal search with highlights; the
   regex toggle with one real example (`\bint\b` → find every `int` word);
   replace-all; close, and the editor is fast again.
3. **Format** — the Format action: `clang-format` bridge when the clang
   module is present, built-in C indenter fallback otherwise (W4.2 facts);
   format a deliberately messy C snippet and watch it straighten; one-tap
   undo reverts the whole format.
4. **The extra-keys row** — above the keyboard: ESC, TAB, CTRL, ALT,
   arrows; custom macros in Settings (preview of chapter 15); why it
   matters for C (TAB for indentation, ESC for the terminal).
5. **Errors you can see** — compiler-error squiggles under the exact line;
   tap the squiggle to inspect; the missing-`;` quick fix; the Ln/Col
   status bar so you can quote a location in a bug report.
6. **Zoom & select** — 60 fps pinch-to-zoom on long lines; long-press
   selection with word-boundary detection and the copy/paste contextual
   menu.

- **Try it:** (1) write a 10-line C file with deliberate indentation chaos,
  Format it, undo it once, re-Format; (2) use regex find to count every
  `printf(` in the file; (3) break a semicolon, read the squiggle, apply
  the quick fix, recompile.
- **Mistakes:** closing a tab with the dirty dot (autosave already covered
  it ~2 s ago — but save-all before big operations is a good habit); regex
  search with a literal `.` (escape it: `\.out`); expecting desktop
  keyboard shortcuts that aren't on the extra-keys row (they're in
  Settings as macros — chapter 15).

## 2. Implementation steps

1. Build `ch-07.html` (crumb "Chapter 7 of 17").
2. UI names from W4.2 facts; source notes in `chat-web5/`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-06, next → ch-08.
2. Every editor feature named matches README/W4.2 (spot-check noted).
3. 360/1440 clean; sweep PASS.
```
