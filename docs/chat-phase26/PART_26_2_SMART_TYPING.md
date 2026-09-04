# CodeC Phase 26.2 — Smart Typing Semantics

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
· **Depends on:** 22.5 pairs; core choice from 25 affects plumbing only
· **Target files:** `ui/editor/EditorKeySet.kt` (apply-logic), new
   `ui/editor/SmartTyping.kt`, `ui/editor/EditorLineOps.kt` (extend),
   host tests

---

## 1. Design

Phone typing is won by deleting *friction events*, not by adding keys. Grounded
behaviors (Squircle CE changelog & behavior; Sora `SymbolPairMatch`;
VS Code rules — all behavior-level):

| # | Rule | Behavior spec (test oracle) |
|---|---|---|
| 1 | **Type-over** | Typing `)` `]` `}` `"` `'` when the very next char is that same closer → caret moves over it, no insert. Kills `))` doubles. |
| 2 | **Wrap-selection** | Pair key (or opener typed) with a selection → surround the selection, keep it selected. (22.5 does this for strip caps; extend to IME-typed openers.) |
| 3 | **Empty-pair backspace** | Backspace inside `()` with nothing between → deletes both chars. (The single biggest phone annoyance: deleting an auto-pair char-by-char.) |
| 4 | **Auto-indent** | Enter copies leading indent; after `{` adds one level and (if the closer line exists) splits `}` onto its own line; after `:` in Python adds one level; exiting rules mirrored (dedent when the line's sole content is a closer). |
| 5 | **Comment toggle from strip** | Long-press on `/` popup = toggle line comment (C `//`, Py `#`…) — reuses `EditorLineOps.toggleLineComment`; also bindable to HW Ctrl+/ (24.3 exists). |
| 6 | **Delete-word swipe** | Backspace long-press or slide on the strip's DEL-equivalent: delete previous identifier path-segment (`foo.ba|z` → `foo.|z`)? — NO: phone-friendly = previous **word** (identifier + whites). Stop chars: whitespace, `.`, `/`, quote. |
| 7 | **String-aware negatives** | Inside a string literal, rules 1–3 don't fire for the quote char that opens the string; inside comments, none fire. Enum of lex context reuses the Phase 22.1 tokenizer's per-line state (or Sora's scope info on that path). |
| 8 | **Undo integrity** | Every smart edit is a single undo unit (pair insert = one undo, type-over = zero-cost no-op). `EditorUndoManager`/Sora undo both enforce. |

New pure engine `SmartTyping` (`nextEdit(text, caret, sel, incoming, lang) →
EditOp`) — every rule above becomes a host table-test; the editor only applies
`EditOp` (this is the same pure-function discipline `EditorKeySet` already
follows, so CI carries nearly all confidence).

All rules individually toggleable (Settings → Editor → Smart typing), ON by
default except 4's Python `:` rule (first-run hint dialog instead).

## 2. Implementation steps

1. `SmartTyping` + per-rule unit tables (each rule ≥ 6 cases incl. string/comment
   negatives, rule 4 indent math vs `tabSize`).
2. Wire into the input path (before commit; composing-region safe).
3. Strip `/` popup + Ctrl+/ parity check.
4. Delete-word recognizer (26.1's DEL cap or IME backspace aide).
5. Settings toggles + persistence + first-run hint.
6. Undo-unit audits (host tests around `EditorUndoManager`).

## 3. Exit condition

```text
(Device)
1. `(|)` + ")" → `(x)|` never `())`.
2. Type "(" with `name` selected → `(name)` and selection intact.
3. Backspace in `(|)` → line has neither char.
4. Enter after `{` → newline + indent+4, `}` drops to its own line; Python
   `def f():` + Enter → indented next line.
5. `foo.bar(|)` + DEL long-press → `foo.|`; `- ` whitespace pairs trimmed sane.
6. Inside `"..."` typing `"` closes/behaves per lex context — no pair storms.
7. Each rule can be turned OFF individually; off survives restart.
PASS = all seven + existing 22.5/24.3 recipes green.
```
