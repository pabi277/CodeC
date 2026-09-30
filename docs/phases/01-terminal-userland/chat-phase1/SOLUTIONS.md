# Solutions (what landed)

Current ship: **1.3.13** / `versionCode` **17**.  
`BOOTSTRAP_VERSION = "12"` · `EmbeddedCompiler.BUNDLE_VERSION = "3"`.

Install: uninstall old APK → Actions → **Build APK** → **CodeC-IDE** → Term → refresh.

---

## Version map

| Version | What the user got |
|---|---|
| 1.3.10 | `cc` one-line exec (no `\` filename) |
| 1.3.11 | Executable projects dir + `chmod 755`; Termux IME view; no 137 on restart |
| 1.3.12 | `cc` `-o` last (same as RUN); re-extract bad `libtcc1.a` |
| 1.3.13 | Unbuffered stdout in every TCC binary (scanf prompt first) |

---

## Files to know

| Area | Path |
|---|---|
| `cc` / profile / `$PREFIX` | `app/src/main/java/com/codeci/ide/ui/terminal/ShellEnvironment.kt` |
| TCC link line (RUN) | `EmbeddedCompiler.buildCompileCommand` |
| Bundle extract + archive magic | `EmbeddedCompiler.ensureExtracted`, `isUnixArchive` |
| Interactive stdio object | `app/src/main/assets/tcc/{arm64-v8a,x86_64}/codec_stdio.c` |
| Projects dir (must be exec) | `FileManager.getProjectDir` → `filesDir/CodeC/projects` |
| PTY | `app/src/main/cpp/pty.c`, `PtySession`, `TerminalSession` |
| IME | `TerminalKeyView.kt` + `TerminalEmulatorView.kt` (Canvas grid) |
| Shell start race | `TerminalViewModel` mutex; `TerminalSession` superseded reader |

---

## Invariants (do not regress)

1. **Link order** (TCC does not rescan archives):  
   `-nostdlib -static` + `crt1.o crti.o codec_stdio.o <src> libtcc1.a libc.a libtcc1.a libc.a crtn.o -o <out>`
2. **`-o` is last.** Never put `-o file` in the middle of that line.
3. **No backslash** in `ccScript()` (becomes a tcc filename).
4. **Do not `exec` tcc** — chmod after link.
5. **CWD for binaries** is `/data/data/com.codeci.ide/files/...` never `/storage/emulated/0/...`.
6. **IME** is a real Android `InputConnection` view, not a 1.dp Compose `TextField`.
7. **Do not change user C** to add `fflush`. Fix the runtime (`codec_stdio.o`).
8. **Canvas terminal** — Termux `measureText("X")` / `mTopRow`. Do not wrap the PTY in Compose `Text`.

---

## How the user runs C in Term

One command per line, Enter each time. No `&&`. Always `./` for the binary.

```
cc input.c -o input.out
```

```
./input.out
```

`cc` without `-o` writes `$CWD/a.out`.
