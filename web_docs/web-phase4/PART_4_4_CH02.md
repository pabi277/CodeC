# CodeC Website Phase W4.4 — Chapter 02: Your First C Program

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4.2 gate
· **Target file:** `website/ch-02.html`

---

## 1. Content

- **Goal box:** write `hello.c`; compile it two ways (RUN button and the
  `cc` command); run it; understand the `./` rule.
- **Need:** Chapter 01 done (app installed, tabs known).

### Steps

1. **Create the file** — in the editor (single files or a new project —
   show both in two lines), name it `hello.c`. Code block:
   the canonical `#include <stdio.h>` / `int main(void)` /
   `printf("Hello from CodeC!\n");` / `return 0;`
2. **The fast way: RUN** — tap **RUN** ▶; read the output under the editor;
   one paragraph on what happened (built-in TCC compiled it to a fully
   static executable — offline, instant, no download).
3. **The honest way: the terminal** — open Term; the two commands, one per
   line, each in its own block:
   `cc hello.c -o a.out` → `./a.out` → expected output.
4. **Why `./`?** — the current directory is not on `PATH`; `./` names the
   file in *this* directory. Without it, the shell looks only in system
   paths. (Short, plain, no shell theory dump.)
5. **Where did `a.out` go?** — next to your file, in app-private storage;
   that's exactly why it can be executed there (one paragraph; depth on
   `/start` and chapter 06).
6. **Change it, recompile, rerun** — edit the `printf` string, save,
   `cc hello.c -o a.out` again, `./a.out` again; the loop is now *theirs*.

- **Try it:** (1) print three different lines; (2) rename the program with
  `-o myhello` and run `./myhello`; (3) delete `a.out` (`rm a.out`),
  confirm with `ls`, rebuild.
- **Mistakes:** missing `;` → red squiggle + the tap-to-see error + the
  quick fix (name the UI); forgetting `./` → "command not found" (point
  back to §4); running a `scanf` program via RUN (preview of the chapter
  02→03 rule: input programs run in Term); editing but running the *old*
  binary (recompile after every edit).

## 2. Implementation steps

1. Build `ch-02.html` (crumb "Chapter 2 of 17").
2. Every code block = a command a learner can type verbatim; expected
   outputs shown; source notes in `chat-web4/`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-01.html, next → ch-03.html.
2. The hello.c snippet is plain ANSI C (TCC-safe by construction — no C99+
   syntax needed here).
3. ./ rule + input-in-Term preview both stated; 360/1440 clean; sweep PASS.
```
