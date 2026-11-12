# CodeC Phase 104.1 — Real login shell

> **Status:** 🚧 IMPLEMENTING · **Cost:** `[client-only]` · **Effort:** S
> **Owner row (verbatim):** "a powerful terminal directly connected with editor just like i want"

## First move: evidence, not code

- `PtySession.kt` `startShell`: `/system/bin/sh -c ". <etc/profile>; exec <shell> -i"`.
- Sandbox measurement (GNU bash 5.2.15): `bash -i` loads `~/.bashrc`; `bash -l -i` loads
  `~/.profile`, not `~/.bashrc`. The roadmap's "login so rc loads" is inverted.
- `ShellEnvironment.bashShim()`: without an ELF bash the shell is `/system/bin/sh`
  (mksh), which does not read `~/.bashrc`.
- `grep -rn "bash_profile\|bash_login" app/src/main` = 0.

## Design

Pure policy `ShellLaunchPolicy` (Android-free, host-tested):

```kotlin
data class LaunchPlan(val file: String, val argv: List<String>, val login: Boolean)
object ShellLaunchPolicy {
    fun plan(shellPath: String, shellIsElf: Boolean, profilePath: String?): LaunchPlan
}
```

- `shellIsElf == true` → `LaunchPlan(file = shellPath, argv = ["-bash", "-l"], login = true)`.
  argv[0] with a leading `-` is the login-shell convention; no `sh -c` wrapper.
- `shellIsElf == false` → the current chain, unchanged, so the shim path keeps today's
  behaviour: `file = "/system/bin/sh"`, `argv = ["sh", "-c", ". profile; exec <shell> -i"]`.
- `profile` is only sourced by the chain when present (as today).

The app-written `~/.profile` gains one guarded line after the `etc/profile` source:
`[ -f "$HOME/.bashrc" ] && . "$HOME/.bashrc"` — the rc load moves to the profile, where a
login bash reads it. Written by `ShellEnvironment` (tested).

## The Android edge

`PtySession.startShell` calls `ShellLaunchPolicy.plan(...)` with
`ShellEnvironment.isElf(prepared.shell)` and spawns `plan.file` with `plan.argv`.
The cwd and env are unchanged.

## Exit condition

- `ShellLaunchPolicyTest`: ELF → login argv, no `sh -c`; non-ELF → chain; profile
  missing → no source in the chain.
- `ShellEnvironmentTest`: `~/.profile` text sources `~/.bashrc` behind a file guard.
- `ShellLoginWiringTest` (source pin): `startShell` uses `ShellLaunchPolicy.plan`.
- Device row (owner, needs bash installed): `.bashrc` alias works on first open.

## Tests (plan)

- `ShellLaunchPolicyTest` (new, pure): 4 cases.
- `ShellLoginWiringTest` (new, source pin): 2 cases.
- `ShellEnvironmentTest`: +1 case (profile sources rc behind a guard).

## Deferred / rejected with reasons

- **Login switch as "the rc fix" on its own** — rejected: measured to remove the rc load.
- **An rc loader for the mksh shim** — deferred: bashrc syntax is bash, not mksh; without
  bash the honest answer is "install bash" (bootstrap), not a fake loader.
- **Removing `pj()`** — not needed to make the login shell work; kept.

## Implementation (2026-10-14, `arena/4baff5b7-codec`)

- `ui/terminal/ShellLaunchPolicy.kt` (new, pure): `LaunchPlan` and `ShellLaunchPolicy.plan`.
  A real ELF `bash` is spawned directly with argv `["-bash", "-l"]`. Anything else keeps the
  previous `/system/bin/sh -c ". <profile>; exec <shell> -i"` chain (busybox, script shims,
  the system shell).
- `PtySession.startShell` asks the policy; the cwd and env are unchanged.
- `ShellEnvironment.homeProfileScript(etcProfile)` (new, pure): the app-written `~/.profile`
  sources `etc/profile`, then `~/.bashrc` behind `[ -f ... ]`. Login bash reads this file.
- Tests: `ShellLaunchPolicyTest` (6 cases, host), `ShellLoginWiringTest` (2 pins, host),
  `ShellEnvironmentTest` +1 case (CI only: that file imports Android).
- Host: 162 passed, 0 failed (the 154 terminal/scan suites plus the 8 new cases).

**Device pass required:** a `.bashrc` alias works on first open where bash is installed. The
shim path (no bash) is unchanged on purpose and is recorded in TROUBLESHOOTING §65.

