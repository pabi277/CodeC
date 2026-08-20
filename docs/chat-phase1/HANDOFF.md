# Handoff for the next chat

Read [README.md](README.md) then [PROBLEMS.md](PROBLEMS.md) before writing code.

## Do

- Stay on branch `arena/01a01d83-codec` unless the user names another.
- If Term is broken, paste-match against **PROBLEMS.md** before inventing a new theory.
- Keep user C textbook (`printf` + `scanf` with no `fflush`).
- Copy Termux (`TerminalView` grid, PTY, `$PREFIX` on `/data/data/<pkg>/files`).
- After APK fixes: uninstall old APK, install Actions artifact, Term → refresh, **recompile**.

## Do not

- Do **not** start Phase 2 (bootstrap tarball) or Phase 3 (`pkg` / apt) unless the user says so. New chat for that.
- Do **not** open a new PR unless asked. PR #5 already tracks this branch.
- Do **not** add `.` to `PATH` (Termux does not).
- Do **not** put `-o` in the middle of the TCC line.
- Do **not** `exec` tcc in `cc`.
- Do **not** save projects on `/storage/emulated/0` (noexec).
- Do **not** go back to a hidden Compose `TextField` for IME.
- Do **not** treat RUN `scanf` timeout (exit 124) as an infinite loop.

## Still open (Phase 1 polish, not Mini-Termux Phase 2)

| Item | Notes |
|---|---|
| Editor RUN has no stdin | `scanf` waits 10s → 124. Use Term. Optional later: input box or hand off to Term. |
| `input.out` without `./` | POSIX. Teach `./input.out`. |
| Long paths wrapping in the grid | Cosmetic. Short `PS1` already. |

## Phase 2 (only in a new chat, only if asked)

Fork termux-packages, `TERMUX_PREFIX=/data/data/com.codeci.ide/files/usr`, bootstrap tarball. See [docs/TERMINAL_PLAN.md](../TERMINAL_PLAN.md) §7. Keep embedded TCC as zero-download fallback.
