# CodeC Website Phase W5.4 — Chapter 10: Python in CodeC

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4 (W4.2 facts must confirm the `python` package; if the
  verified table says otherwise, this chapter is re-scoped per W4.2 gate
  outcomes)
· **Target file:** `website/ch-10.html`

---

## 1. Content

- **Goal box:** install Python from the package hub; run a script; use the
  REPL; write a small utility with arguments.
- **Need:** Chapters 05 (packages), 09 (scripts) done.

### Steps

1. **Install it** — Packages tab → `python` card → **INSTALL** (or Term:
   `pkg install python`); confirm with `python3 --version` (exact command
   form from W4.2 facts — `python` vs `python3`, whichever the verified
   table says).
2. **First script** — `hello.py`: a three-line program; run it in Term:
   `python3 hello.py`. (RUN ▶ also works for Python files — show both, one
   line each.)
3. **The REPL** — `python3` in Term drops you in; `1 + 1`; `"a" * 3`;
   `exit()`; why the REPL is the fastest way to learn syntax.
4. **Variables & types** — no declarations; `int`/`float`/`str`/`list`/
   `dict`; an f-string print card (the Python answer to chapter 08 §3).
5. **Control flow in 15 lines** — one function using `if/elif/else`, one
   `for` over a list, one `while`; a fahrenheit→celsius function (the
   chapter-08 capstone, translated — the reader watches C thinking become
   Python thinking).
6. **A small utility with arguments** — `wordcount.py`: reads the file
   named in `sys.argv[1]`, prints lines/words/chars; run it on a text file
   from chapter 06's project; the error case (no argument) handled with a
   friendly message.
   *(If W4.2 confirms `pip` availability, add one "go deeper" footnote
   about installing Python packages — otherwise a one-line honest note:
   "Python package management isn't part of this curated repo today.")*

- **Try it:** (1) write `sq.py` that prints the squares 1..10 (loop);
  (2) use the REPL to find the length of a string you type; (3) run
  `wordcount.py` on three different files and eyeball one wrong (a binary
  file) to see what happens.
- **Mistakes:** `python` vs `python3` (whichever W4.2 verified — use it
  everywhere); indentation errors (Python's tabs-vs-spaces bite — 4 spaces,
  always, on the phone); editing the script and running the old version
  (there's no compile step — the run IS the check); `sys.argv[1]` missing
  (the error case is the lesson, step 6).

## 2. Implementation steps

1. Build `ch-10.html` (crumb "Chapter 10 of 17").
2. Command forms from W4.2 facts (python package name, interpreter name,
  pip availability); source notes in `chat-web5/`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-09, next → ch-11.
2. Interpreter name + install command == W4.2 verified facts (noted).
3. All 5 code samples are Python 3.8+ basic syntax (no version-dependent
   sugar); pip note matches the verified table.
4. 360/1440 clean; sweep PASS.
```
