# CodeC Website Phase W5.2 — Chapter 08: C Programming Basics

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** M
· **Depends on:** W4 (TCC-safe law)
· **Target file:** `website/ch-08.html`

> The only chapter teaching content **beyond** the repo docs (standard C).
> Every snippet is plain ANSI C (safe on built-in TCC by construction) and
> must be **device-run** before the phase closes (exit condition §3.4).

---

## 1. Content

- **Goal box:** write, compile, and debug real C programs — from variables
  to first pointers — entirely on the phone.
- **Need:** Chapters 02–03 done (compile/run loop is reflex).

### Sections (each: short prose → code block → how to run it in CodeC →
expected output)

1. **The shape of a program** — `#include`, `main`, return; recap in
   three lines (they've already written it).
2. **Variables & types** — `int`, `double`, `char`; declaring, assigning;
   a program that declares three, prints them with formats.
3. **printf, properly** — `%d %f %c %s %p`, the `\n`, `sizeof`; a
   "print a card" exercise.
4. **scanf — and where it runs** — reading a number and a string;
   **this program runs in Term, not RUN** (chapter 03 rule, restated in
   one line); the `&` (address-of) in plain words: "scanf needs the
   variable's address".
5. **Operators & expressions** — arithmetic with integers vs doubles (the
   `5/2 == 2` trap, shown), `%` for remainders, comparisons, `&&`/`||`/`!`.
6. **if / else / while / for** — one small program per construct: a parity
   checker, a countdown loop, a sum-of-1-to-N.
7. **Functions** — declare, call, return; `void`; passing by value
   (plain words + a swap that *doesn't* work, as the teaching moment).
8. **Arrays & strings** — one-dimensional arrays, loops over them; C
   strings as char arrays + `\0`; `strlen`/`strcpy` (with `#include
   <string.h>`) — one worked program: reverse a string.
9. **First pointers** — "a pointer stores an address"; `int *p = &x`;
   `*p` reads; why the swap *does* work with pointers; one picture-free,
   table-based memory walk (address | value).
10. **Mini-capstone** — a temperature converter: menu (C→F / F→C / quit),
    functions, loops, `scanf` in Term, three source lines max per function.
    Runs the whole chapter end-to-end.

- **Try it (per section, 1 each + 3 for the capstone):** e.g. §4 "read
  your name and a year, print 'You will be N in 2030'"; §6 "print the
  multiplication table for 7"; §9 "build the working swap and prove it
  prints both changed values"; capstone: add a third unit (Kelvin).
- **Mistakes:** missing `;` (again — now by reflex); `scanf("%d", n)`
  without `&` (crash or garbage — the #1 beginner crash); `5/2` surprise;
  forgetting `scanf` programs run in Term; running the capstone via RUN
  and wondering where the input went (Term); array out of bounds (no
  bounds checking in C — state it plainly once).

## 2. Implementation steps

1. Build `ch-08.html` (crumb "Chapter 8 of 17") — the longest chapter;
   keep every section ≤ ~15 lines of prose + one code block.
2. **Snippet review pass:** every block checked for ANSI-C purity (no
   `//`-only-issues, no C99-forwards-declarations tricks, no VLAs, no
   `bool`); review checklist recorded in `chat-web5/SNIPPET_REVIEW.md`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-07, next → ch-09.
2. 10 sections + capstone present; expected outputs shown for every run.
3. SNIPPET_REVIEW.md committed: 100% of blocks marked ANSI-C-safe with the
   reviewer's note per block.
4. **DEVICE PASS REQUIRED (owner):** run the capstone + §4, §6, §9 try-its
   on a real device (built-in TCC, Auto engine); transcript recorded in
   chat-web5/ — the phase is not COMPLETE until the transcript lands.
```
