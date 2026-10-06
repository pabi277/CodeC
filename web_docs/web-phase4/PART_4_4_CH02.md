# CodeC Website Phase W4.4 — Chapter 02: Your First C Program

> **Current batch status — 2026-10-07, D33: IMPLEMENTED FOR REVIEW.**
> Owner authorized W3–W6 content together, then one commit/watch; separate start
> commands/intermediate commits below are superseded. W4.2 verification ran FIRST.
> [Batch record](../chat-web6/BATCH_SUMMARY.md) · [checks](../chat-web6/CHECKS.md) ·
> [device gates](../chat-web6/DEVICE_TESTS.md) · [current full ZIP](../chat-web6/review-zips/CodeC-website-W6-review.zip).
> D31 download law retained. No device pass, PR, merge or public deployment implied.

> **Binding v2.3 amendment — 2026-10-07 (scope supplied 2026-10-06).**
> Apply [WEBSITE_PLAN.md](../WEBSITE_PLAN.md) and the current instructions below
> before the retained prior design. They supersede conflicting old facts and exits:
> **29 pages / 9 product / 19 chapters**; app-v1.3.18 **7 217 532 B, versionCode 22**;
> AI in header, Privacy every footer; contacts in-app only; README then AI/privacy
> guides; Phases 1–96 shipped except cancelled proposals, 97+ roadmap. Never pair
> current APK size with historical -74%. No picker, Open Folder, ProjectLink,
> first-hour tiles, mandatory tour or automatic userland setup in visitor teaching.
> Earlier session-folder references are historical: preserve existing records;
> W1 uses `chat-web4/W1_*`. No next phase, PR, merge or deploy without command.

**Current status:** IMPLEMENTED FOR REVIEW; owner-authorized combined batch (D33).

## Current v2.3 implementation requirements

Keep ANSI C hello.c, RUN Auto, terminal cc hello.c -o a.out followed by ./a.out,
expected output, ./ rule and input-in-Term note. Name optional userland prerequisite
for shell route; built-in C itself needs no Linux download on supported ABIs.
Crumb Chapter 2 of 19; prev ch-01, next ch-03.

## Current acceptance additions (binding)

1. Follow the current requirements above and the master plan, not superseded UI/copy.
2. Preserve each non-conflicting numbered exit below, including device gates.
3. Record source file/line/sha, responsive checks and self-dependent sweep; use 19-chapter crumbs where applicable.
4. No app/workflow changes or phase expansion; update web living docs and stop at merge gate.

---

## Prior design detail — retained v2.2 record

**Historical reference, not authority for superseded facts or exit counts.**

> **v2.2 update (2026-09-12):** synced with app Phases 21–43 — universal APK 6.6 MB signed SHA256, Auto engine only picker deleted Termux card deleted fallback automatic error-path Output Panel, >_ mark icon, official file icons Seti MIT, typing feel, terminal multi-session + LAN server opt-in 0.0.0.0 two URLs QR ZXing open-in-browser keep-alive, outputs temporary RunArtifacts+RepoHygiene ~60 patterns .codec/ user .gitignore wins, GitHub truth GitReadiness+PushOutcome+Publish POST /user/repos, feedback hardcoded +91 62967 46606 / email + FeedbackScreen + exit survey + crash-log header-first + OpenInBrowser no telemetry, backup include-list-only FullBackupContent law crash-loop guard safe mode 3rd launch export-all over both roots 11 privacy rows RELEASE_NOTES template {{SHA256_LINES}}, safe walk Throwable-safe TreeWalkPolicy ProjectLink persisted SAF grant noexec mirror, BETA B-1…B-8, TCC null on armeabi-v7a/x86.

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
