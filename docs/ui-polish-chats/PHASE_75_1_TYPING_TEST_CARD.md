# Phase 75.1 — the typing test card (type these on the phone)

Short lines on purpose: type every character with your normal keyboard, then repeat the same
case with **CodeC Keys** (⌫ / ⏎ / TAB caps). `:` at the end of a line, one ⏎, read where the
caret lands. Count spaces, not feelings.

Legend — **MUST** = the build is wrong if this fails. **KNOWN** = today's honest behaviour,
not a regression: report it only if you want it changed.

For every case: after you finish it, press **undo twice** and **redo twice** — one press should
undo one of your edits, never a whole block.

---

## A. Python — the lines that MUST indent by one level

File `main.py`. Type the line, press ⏎, look at the caret column. Every line below must leave
the next line **4 spaces deeper than the line you typed** (all of them start at column 0 here,
so the answer is column 4). Then ⏎ again: it must stay level, not run away.

```
def f():
class Box:
for i in items:
while n > 0:
if n:
elif n:
else:
try:
except ValueError as e:
finally:
with open(p) as fh:
async def go():
match cmd:
case 1 | 2:
```

Variants that MUST also indent:

```
def sum2(items):
    total = 0
    for i in items:
        total += i
    return total
```
```
if __name__ == "__main__":
```
```
else :
```
(space before the colon — still a block)
```
for i in items:   # walk the list
```
(trailing comment must not hide the colon)
```
def f():  # noqa
```
```
for i in x:␣␣␣
```
(three spaces, THEN ⏎ — still a block)

### A2. Python — the lines that MUST NOT invent an indent

Type each, press ⏎: the next line must be at the **same** column as the line you typed
(copy the indent, add nothing).

```
# note:
    # TODO: later
x:
label:
format:
elsex:
if x: print(1)
data = {
```
then on the next line
```
    "a":
```
```
s = "abc:"
print("def:")
url = "http://x"
```
```
def f(
    a, b):
```
```
if x > (
    1 + 2):
```
```
s = """sql:
```
```
print("for:
```

### A3. Python — where the caret is, is what counts

```
for i in x:      → caret at the very end, ⏎  → MUST indent
for i in |x:     → caret in the MIDDLE, ⏎   → MUST NOT indent (only text before the caret)
def f():         → caret inside the ( ) , ⏎  → MUST NOT indent
```

### A4. Python — dedent and closers

```
def f():⏎⏎return 1
class A:⏎def g(self):⏎    ...
```
Type on an indented line, then `}` / `)` / `]` — in Python there is nothing to close, so
typing `)` must stay put. In a Python file `}` must not dedent anything.

---

## B. Backspace inside indentation — one space, every press

Set-up for all of B: in `main.py`
```
def f():
    for i in items:
        total = 0
```

| # | Where the caret is | What one ⌫ must do |
|---|---|---|
| B1 | on the **empty auto-indented line** after `for i in items:` ⏎ (4 spaces, caret after them) | remove **one** space (3 left). Four presses = one level up. A fifth joins the line above |
| B2 | in the middle of a 4-space indent (after 2 spaces) | one space, not two, not four |
| B3 | right before `total` (after 8 spaces) | 8→7→6… one at a time |
| B4 | TAB-indented line (TAB cap + ⏎) | one **tab** per press |
| B5 | `x = 1␣␣␣` — trailing spaces after code | whatever the keyboard asked stays; the code must not be eaten |
| B6 | a selection that covers the indent | the selection goes, once |
| B7 | `(|)` — caret between empty parens | BOTH go (26.2 rule 3, older than this phase) |
| B8 | `("|)` and `('|)` | both quotes go |
| B9 | ⌫ **flick-up** (word delete) anywhere in the indent | the whole run goes — that key means "a word" |
| B10 | hold ⌫ inside an 8-space indent on a 1,000-line file | smooth repeat, no blink of the whole screen (2026-09-13 law), no lost keystrokes |
| B11 | after B1: undo once, redo once | one edit back, one edit forward, caret where you left it |
| B12 | ⌫ at column 0 of an empty line | joins the line above (normal) |

---

## C. Braces — C / C++ / Java / Go / Rust

File `main.c` (or `.cpp`/`.java`/`.go`/`.rs`). Read the new line's column after ⏎.

```
int main() {
if (x) {
for (int i = 0; i < n; i++) {
switch (v) {
case 3:
default:
struct P {
namespace n {
```

- **KNOWN (this phase left it alone):** after a line ending `{`, CodeC adds **one space**, not
  four. Same for `switch`/`struct`/`namespace`. Say "fix braces too" and it becomes a real
  level (with the `}` split onto its own line at any depth).
- **MUST:** `case 3:` and `default:` must add **nothing** — the colon rule is Python-only.
- **MUST:** typing `}` alone on an indented line pulls it back one step (the 26.2 dedent).
- **MUST:** `(` `)` type-over: with `(|)` typing `)` moves over it; `"` inside a `//` comment
  must not auto-close.

---

## D. JavaScript / TypeScript

File `app.js` (and `.ts`/`.tsx`).

```
function g() {
const f = (x) => {
if (x) {
class A {
outer:
label:
const t = `a:
const s = "x:"
```

- **MUST:** the two label lines (`outer:`, `label:`) add **nothing**.
- **MUST:** the backtick and quote lines add nothing; `` ` `` still pairs and closes.
- **KNOWN:** `{` lines indent by one space (see C).

---

## E. HTML / CSS / JSON / YAML / Markdown / plain text — the rules must stay quiet

Files: `index.html`, `style.css`, `data.json`, `config.yml`, `notes.md`, `readme.txt`.

```
<div id="x">
<p>
<style>
```
```
p { color: red; }
.a {
```
```
{ "a": 1 }
[1, 2]
{"k":
```
```
server:
  host: localhost
  ports:
```
```
# Heading
def not_python:
- item:
> quote:
```

- **MUST:** **no auto-indent at all** in HTML, CSS, JSON, YAML, Markdown or TXT — including the
  Markdown line `def not_python:` (a `.md` file is not a `.py` file: the rule follows the
  extension, not the words). This is the cross-language check for language detection.
- **MUST:** pairing still works in every one of them: `(`, `[`, `{`, `"`, `'` auto-close and the
  closer types over — except inside a string or a comment.
- **MUST:** in CSS/JSON typing `:` must never move the caret or insert anything.

---

## F. Shell

File `run.sh`.

```
if [ -f a ]; then
for f in *; do
case "$x" in
foo() {
while read l; do
```
- **KNOWN:** shell uses `then`/`do`/`in`, not `:` — today nothing auto-indents here, and `{`
  gets the one-space brace delta. Report only if you want shell block keywords taught too.
- **MUST:** `"$HOME"` keeps its quotes paired; typing `"` inside `[ ... ]` must not strand a
  second quote; `# comment:` adds nothing.

---

## G. The keyboard itself (this is where phones break things)

1. **Autocorrect / composing:** type `def` as a word and let the suggestion bar underline it;
   press ⏎, then ⌫. No swallowed letters, no doubled word, no ghost of the composing span.
2. **Swipe-typing:** swipe the word `for`, then ⏎ — the block must still indent once, not twice.
3. **Smart punctuation:** let the IME commit `:` + Enter as one chunk (`for i in x:` ⏎) → one
   level, not two, not zero.
4. **A whole line in one commit:** paste/select a `for i in items:` line from the keyboard's own
   phrasebook/dictionary insert if you have one → caret lands on the indented line.
5. **Devanagari:** type `नमस्ते` then press ⌫ four times — one akshara at a time, no orphan
   matra, no broken word.
6. **Emoji + ZWJ:** `👨‍👩‍👧` then ONE ⌫ → the whole family goes, never half of it.
7. **Ghost / chip accept:** type `p`, accept `print(`, keep typing `x` → the accept must survive
   (the 2026-09-29 law), then ⌫ inside the parens behaves per B7.
8. **Word wrap + long line:** type a 300-character Python line, ⏎ at the end, ⌫ in the indent.
9. **Fast typing:** type `for i in items:` as fast as you can, ⏎, then `total = 0` fast — every
   character must be there once (28.2 "swallowed presses" law).
10. **Both keyboards:** the same snippet through the system keyboard and CodeC Keys must end with
    the same buffer. If they differ, that IS the bug class this phase was created for.
11. **Key strip:** TAB cap, `()` cap, swipe-up `(`, then ⏎ — none of it may auto-close twice.
12. **Hardware keyboard** (if you have one): Enter, ⌫, Ctrl+⌫ (word), Ctrl+Z / Ctrl+Y.

---

## H. Editor state around the edits

1. 10 characters, ⏎, three corrected ⌫ → undo 14 times: every step is one of your edits;
   nothing jumps back to the top of the file.
2. Dirty dot: type, wait for autosave, press ⌫ in the indent → dot returns; save → dot clears;
   reopen the file → the indentation on disk matches what you saw.
3. Find/replace `for` → `foreach` in the file, then ⏎ after `foreach i in items:` → **MUST** not
   indent (`foreach` is not a block keyword).
4. Undo the corrected backspace, then type a character — no duplicated or missing space.
5. Switch tabs while the caret sits mid-indent, come back, press ⌫ → one space.

---

## I. The two things I already know are weak (check them, they are not surprises)

1. **A prose line inside a docstring that begins with a block word and ends with a colon** —
   e.g. inside `"""…"""`, the line `    for each item:` **will** indent. The rule reads one line
   only, so it cannot see that you are inside a string. Say the word and I can pass the
   tokenizer's string state into the rule.
2. **Brace languages** indent by one space instead of a level (see C).

---

## How to report (so I can fix it in one round)

```
case: B1
keyboard: system (name) | CodeC Keys | hardware
file/extension: main.py
typed: |for i in items:⏎|
saw: caret column 4, then ONE ⌫ -> "   " became "" (it took all 3 spaces)
undo: one press restored everything / restored only one space / caret jumped to col 0
```

A screenshot of the file + which numbered case, in the order above, is enough. Nothing in this
card is expected to be perfect on the first pass — that is what the card is for.
