# CodeC Website Phase W4.5 — Chapter 03: The CodeC Terminal

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4.2 gate
· **Target file:** `website/ch-03.html`

---

## 1. Content

- **Goal box:** move around the terminal like at home — list, make, move,
  read, delete; know where programs run and where input programs must run.
- **Need:** Chapter 02 done.

### Steps

1. **Two doors into Term** — the middle tab; the terminal icon in the
   editor toolbar. Same place either way. What you're looking at: a login
   shell in your CodeC home (the `$PREFIX` path from W4.2 facts, shown
   once).
2. **The seven commands that cover 90%** — one H2-less block per command,
   each: syntax line, plain-language meaning, example output:
   `ls` (list) · `pwd` (where am I) · `cd <dir>` (move; `cd ..` back,
   `cd` home) · `mkdir <dir>` · `touch <file>` · `cat <file>` ·
   `cp`/`mv`/`rm` (copy/move/delete — `rm` warned: no trash bin here).
3. **Build a sandbox** — the guided exercise: `mkdir demo`, `cd demo`,
   `touch a.txt b.txt`, `mkdir sub`, `mv b.txt sub/`, `ls -R` (or `ls` in
   each dir), `cat a.txt`, then clean: `cd ..`, `rm -r demo` (each in its
   own one-command-per-line block).
4. **One command per line** — the CodeC terminal habit: type a command,
   press Enter, read the result; the extra-keys row above the keyboard
   (ESC/TAB/CTRL/ALT/arrows) is there when a command needs them (preview of
   chapter 15's custom macros).
5. **Where programs run** — your compiled binaries live next to your files
   (app-private storage → executable); the `./` rule from chapter 02 still
   applies.
6. **Input programs live here** — `scanf`/`getchar` programs run **in
   Term**, not via RUN: show the 4-line scanf program, compile it, run
   `./askname` in Term, type the answer at the prompt, see it echoed.
   (RUN has no keyboard into the process — state it once, plainly.)

- **Try it:** (1) build a 3-level folder tree with `mkdir` and walk it
  with `cd`, ending back home; (2) copy a file, verify with `cat`, delete
  both copies; (3) run `./askname` and answer three times differently.
- **Mistakes:** `cd /data/...` directly (app-private paths — stay in
  `$PREFIX` and your project folders); `rm -r` with a typo (type `ls`
  first); typing two commands on one line (one per line in CodeC);
  "command not found" for `./a.out` (wrong directory — `pwd` check).

## 2. Implementation steps

1. Build `ch-03.html` (crumb "Chapter 3 of 17"); sandbox exercise blocks
   verbatim-typeable.
2. Source notes in `chat-web4/`; self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-02, next → ch-04.
2. All seven commands + sandbox blocks are valid in the CodeC userland per
   W4.2 facts; the scanf-in-Term demo is complete (code + expected output).
3. 360/1440 clean; sweep PASS.
```
