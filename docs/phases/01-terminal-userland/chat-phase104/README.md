# CodeC Phase 104 — Shell & session truth

> **Status:** 🚧 IMPLEMENTING · **Owner command:** "Start Phase 104" (2026-10-14, given as
> "Ok continue to phase 104", then confirmed "Start Phase 104 now" through the
> phase-start question). **Branch:** `arena/4baff5b7-codec` (continues Phase 103).
> **Roadmap:** [`PHASE103_108_ROADMAP.md`](../../../roadmaps/PHASE103_108_ROADMAP.md) §Phase 104.

## Why this phase exists (evidence, read this session @ `e06851a`)

- **Shell launch is a `sh -c` chain.** `PtySession.startShell` runs
  `/system/bin/sh -c ". <prefix>/etc/profile; exec <shell> -i"`, so the first
  process is Android's mksh, which then execs the shell as a non-login interactive
  shell (`app/src/main/java/com/codeci/ide/ui/terminal/PtySession.kt`, `startShell`).
- **Correction to the roadmap's 104.1 premise.** The roadmap says a *login* shell is
  needed "so `~/.bashrc` loads". Measured in the sandbox with GNU bash 5.2: `bash -i`
  (the current launch) **already loads `~/.bashrc`**; `bash -l -i` loads `~/.profile`
  and does **not** load `~/.bashrc` unless the profile sources it. So the login switch
  alone would *remove* the rc load, not add it. 104.1 is therefore: login shell **plus**
  `~/.profile` sourcing `~/.bashrc`.
- **The real first-open failure is the shim.** When no ELF bash is installed,
  `ShellEnvironment.bashShim()` is used and the shell is `/system/bin/sh` (mksh), which
  never reads `~/.bashrc`. This is the "`.bashrc` alias does not work on first open"
  case. The fix needs a real bash, which comes from the bootstrap; without bash the
  rc load stays out of reach and is recorded as deferred, not claimed.
- **`pj()` is not a workaround for a missing rc.** `pj` is defined in `etc/profile`
  (`ShellEnvironment.kt`, `profileScript`). It is kept; the roadmap's "kills" claim
  is not supported by the code.
- **No `~/.bash_profile` or `~/.bash_login` is written anywhere** (grep = 0), so a login
  bash reads our `~/.profile`.
- **PTY flags:** `app/src/main/cpp/pty.c` `configure_slave` sets `IXON` (Ctrl-S can freeze
  output) and never sets `IUTF8`. Winsize is `rows`/`cols` only
  (`nativeSetWindowSize(fd, rows, cols)`); no pixel fields.
- **Alt-scroll:** mode 1007 is parsed (`TerminalEmulator.kt`, `1007 -> ALT_SCROLL`) but
  nothing reads `ALT_SCROLL`. On the alternate screen the wheel scrolls the local
  buffer, which has no history there.
- **Dead session:** `TerminalSession` sets `_alive=false` and `_exitCode` on exit, but no
  text is shown in the buffer; the next input is swallowed.
- **New tab cwd:** `ShellBootstrap.prepare` always returns `cwd = projects`
  (`ShellEnvironment.kt`); nothing inherits `/proc/<pid>/cwd`.

## Parts

| Part | Scope | Doc |
|---|---|---|
| 104.1 (host green; CI pending) | Login shell via direct spawn with argv[0] `-bash` when an ELF bash exists; `~/.profile` sources `~/.bashrc`; the shim path is unchanged and recorded | [PART_104_1_LOGIN_SHELL.md](PART_104_1_LOGIN_SHELL.md) |
| 104.2 (host green; CI pending) | PTY truth: `IXON`/`IXOFF` off, `IUTF8` on (`pty.c`), winsize pixel fields wired from the view | [PART_104_2_PTY_TRUTH.md](PART_104_2_PTY_TRUTH.md) |
| 104.3 (host green; CI pending) | Alt-scroll (1007 → arrow keys, wheel and fling), dead-session message with Enter restart, new-tab cwd inheritance, Keep-screen-on and Kill-process in the top-bar ⋮ menu | [PART_104_3_ALT_SCROLL_SESSION.md](PART_104_3_ALT_SCROLL_SESSION.md) |

## Standing laws honoured

No new dependency, permission, DataStore key or Settings control. Colours from existing
theme roles. Touch targets ≥ 48 dp. No PR, merge or `main` push (rule.md §3). Termux is
behavioural spec only (clean-room; no code copied). Invariants: no `.` on PATH, TCC link
order, no `com.termux` packages.

## Exit condition (the phase)

Host tests green and `Build APK` green on the tip. Device rows (owner): `.bashrc` alias
works on first open **where bash is installed**; Ctrl-S / Ctrl-Q harmless; `less`
scrolls with the wheel on the alternate screen; a dead session says so and Enter starts
a new one; a new tab opens in the previous tab's directory.

**Device pass required.** Not claimed by this phase's commits.
