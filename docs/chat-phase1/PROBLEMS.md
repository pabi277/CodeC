# Problems (sorted)

Order is the order they actually blocked the user. Closed items must not be “fixed again” unless the same symptom returns on **1.3.13+**.

---

## P1 — `./a.out: can't execute: Permission denied`

**Symptom:** `cc main.c -o a.out` succeeds; `./a.out` fails. Editor RUN still works.

**Cause:**

1. CWD was emulated storage (`/storage/emulated/0/Android/data/.../CodeC/projects`) — **`noexec`**.
2. `cc` used `exec tcc`, so it never `chmod 755` the ELF.

**Status:** Fixed (1.3.11). Projects live under `filesDir/CodeC/projects`. `cc` chmods after link.

---

## P2 — Keyboard takes ~a minute to pop up

**Symptom:** Opening Term blocks IME for tens of seconds.

**Cause:** Hidden 1.dp Compose `BasicTextField` is not a text-editor `View`. Gboard stalls. Termux uses `onCreateInputConnection`.

**Status:** Fixed (1.3.11). `TerminalKeyView` (Termux IME flags: `VISIBLE_PASSWORD | NO_SUGGESTIONS`).

---

## P3 — `[process exited with 137]` on open / refresh

**Symptom:** SIGKILL banner on a live shell.

**Cause:** Two `start()` calls (ViewModel init + `LaunchedEffect`) killed the first PTY. Old reader painted 137 onto the new emulator.

**Status:** Fixed (1.3.11). Mutex around start; superseded reader does not print.

---

## P4 — `libtcc1.a: unrecognized file type`

**Symptom:** Second compile (`cc array_sorting.c -o a.out`) fails after `main.c` worked.

**Cause:** `cc` put `-o outfile` in the **middle** of the link line. Editor RUN puts `-o` **last**. TCC could treat `libtcc1.a` as the wrong kind of file / corrupt the bundle.

**Status:** Fixed (1.3.12). `cc` matches `EmbeddedCompiler.buildCompileCommand`. Bundle re-extracted if archives lose `!<arch>\n` magic.

---

## P5 — `scanf` prompt appears *after* the user types

**Symptom:**

```
./input.out
7
Enter a number: 7 is odd
```

Textbook C (no `fflush`) is correct. Do **not** tell the user to change their source.

**Cause:** Static musl does not flush stdout on `scanf` the way glibc does. Line-buffered TTY still holds `printf("Enter a number: ")` until a later newline.

**Status:** Fixed (1.3.13). Every TCC binary links `codec_stdio.o` (`setvbuf(stdout, NULL, _IONBF, 0)`). ISO C allows unbuffered stdout on an interactive device.

---

## P6 — Editor RUN + `scanf` → exit 124

**Symptom:** `Program exceeded time limit (possible infinite loop)` after 10s.

**Cause:** RUN is `ProcessBuilder` with no keyboard. `scanf` waits until `EXECUTE_TIMEOUT_SECONDS`.

**Status:** **Open (later).** Not an infinite loop. Use **Term** for input programs. Optional future: input box on RUN. Not Phase 2.

---

## P7 — `input.out: inaccessible or not found`

**Symptom:** `cc input.c -o input.out` then `input.out` (no `./`).

**Cause:** POSIX — cwd is not on `$PATH`. Same as Termux.

**Status:** **Won’t change** (Termux model). User must run `./input.out`. Do not add `.` to `PATH` unless the user asks.

---

## P8 — `tcc: error: file '\' not found`

**Cause:** Kotlin raw string `\\` in `cc` became a literal `\` argument (line continuation).

**Status:** Fixed earlier this branch (1.3.10). `cc` is one-line; tests `assertFalse(script.contains("\\"))`.

---

## P9 — `undefined symbol 'memmove'` / `__extenddftf2` / `__addtf3` …

**Cause:** TCC does not rescan archives. Need `-nostdlib` + `libtcc1.a libc.a` **twice**.

**Status:** Fixed earlier this branch. Keep that link order.

---

## P10 — Commands sent twice (`main` defined twice, `./a.out./a.out`)

**Cause:** IME TextField reset to `""` every key + Enter handled twice.

**Status:** Fixed earlier. `TerminalKeyView` must not double-`commitText` on composing.

---

## P11 — Prompt wrap ate the first letter of `cd` / `cc`

**Cause:** Android `/system/bin/sh` is **mksh** — no bash `\w` in `PS1`. Long `$PWD` wrapped.

**Status:** Fixed. `PS1='codec $ '`.

---

## P12 — `ls` empty / “no .c files”

**Cause:** Terminal cwd was not the folder that already had `.c` files.

**Status:** Fixed. Canonical dir is executable `filesDir/CodeC/projects`; `.c` files migrate from shared storage.
