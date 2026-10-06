# CodeC Website Phase W4.5 — Chapter 03: The CodeC Terminal

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

**Current status:** PLANNED; not authorized by the W1 command.

## Current v2.3 implementation requirements

Teach opt-in userland setup before shell commands, no setup navigation lock.
One command per line, no dot on PATH; sessions/rename/close, input, process stop
and recovery. Exercise files live in a named scratch directory, no destructive
commands on real projects. Crumb Chapter 3 of 19; keep prev/next.

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
