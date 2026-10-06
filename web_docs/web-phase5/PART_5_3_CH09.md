# CodeC Website Phase W5.3 — Chapter 09: Shell Scripting

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

All scripts use verified CodeC bash/PREFIX after opt-in setup. Keep sandboxed
exercises, expected results and actual shebang proof at W4.2; never official
Termux package installation or overwriting real bash/cc. Chapter 9 of 19.

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
· **Depends on:** W4 (bash presence confirmed in W4.2 facts — the CodeC
  userland ships a real ELF `bash`; this is an invariant, not a guess)
· **Target file:** `website/ch-09.html`

---

## 1. Content

- **Goal box:** turn a sequence of terminal commands into a script; run it;
  pass it arguments; automate your first real repeat.
- **Need:** Chapter 03 done.

### Steps

1. **What a script is** — a text file of shell commands; the CodeC userland
   ships a real `bash`; you write the file in the editor, run it from Term.
   (One line on why this is *the* skill of the terminal.)
2. **First script** — `myscript.sh`: the shebang line
   `#!/data/data/com.codeci.ide/files/usr/bin/bash` (exact path from
   W4.2 facts — state that `bash myscript.sh` also works and is the
   beginner's default), then three commands from chapter 03 (`pwd`,
   `ls`, `date`). Run it; read the output.
3. **Variables** — `NAME="codec"`, echo `$NAME`, assignment with no spaces;
   `$1` / `$2` arguments — a script that greets whoever's passed in.
4. **Conditions** — `if [ -f file ]`, `else`, `fi`; a script that compiles
   `hello.c` **only if it exists**, and tells you why when it doesn't.
5. **Loops** — a `for` over `ls` results (rename `*.txt` → `*.bak` in a
   demo folder); a `while` reading a counter; keep both to 5 lines.
6. **Your first automation** — the capstone script: `build.sh` — check
   the source exists → compile with `cc` → run `./a.out` → print a PASS /
   FAIL line (the `&&` chain and `$?` in plain words). This is chapter 02's
   loop, now one command: `bash build.sh`.

- **Try it:** (1) write `greet.sh` that takes a name and a number and
  prints "Hi <name>, you said <n>" three times (loop); (2) make a `safe-rm`
  wrapper script that refuses to run unless you type the filename as an
  argument; (3) run `bash build.sh` from a directory without `hello.c` and
  read the failure message it prints.
- **Mistakes:** `NAME = codec` (spaces — it's a command, not an
  assignment); running `./myscript.sh` without making it executable
  (`bash myscript.sh` first, `chmod +x` is fine too — show both);
  `$1` empty (no argument was passed — `set --` demo not needed, just
  explain); Windows line endings (edited on a PC? the shebang line breaks
  — retype the first line).

## 2. Implementation steps

1. Build `ch-09.html` (crumb "Chapter 9 of 17"); every script inline,
   verbatim-typeable, expected output shown.
2. bash path from W4.2 facts; source notes in `chat-web5/`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-08, next → ch-10.
2. All 6 scripts valid in the CodeC userland per W4.2 facts (bash path
   exact); capstone's PASS/FAIL logic stated.
3. 360/1440 clean; sweep PASS.
```
