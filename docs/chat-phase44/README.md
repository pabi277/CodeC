# CodeC Phase 44 — Setup you can see, and cannot half-finish

> **Status:** 📋 PLANNED (researched + specced, no code) · **Cost:**
> `[client-only]` · **Effort:** M · **Owner row (verbatim):** *"Userland is
> installing but the test user don't know it's installing so they close app
> before it complete than letter when they try to install any other pkg got
> errors"* → **owner's own solution:** *"If it opens the terminal 1st and show a
> warning don't close the terminal while userland is installi[ng]"*

```text
  44.1  The install is visible everywhere (gate + banner + notification + progress flow)
  44.2  The install cannot half-finish (ledger + safe swap + orphan cleanup + refusal at the point of use)
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [44.1](PART_44_1_VISIBLE_SETUP.md) | The install is visible everywhere | M | 📋 PLANNED |
| [44.2](PART_44_2_ATOMIC_SETUP.md) | The install cannot half-finish | M | 📋 PLANNED |

---

## What exists today (evidence, read 2026-09-12 against `main` @ `f3a6e32`)

1. **The install starts before the user can see anything.**
   `MainApp` builds the activity-scoped `TerminalViewModel` at
   `MainActivity.kt:629`; its `init` block launches
   `viewModelScope.launch(Dispatchers.IO) { startInternal() }` at
   `TerminalViewModel.kt:205`; `startItem` (`:323`) calls
   `installUserlandInternal(item.session, force = false)` at `:337` **before**
   `item.session.start(prepared)`. So on a fresh install the bootstrap download
   begins the moment the app opens — while the user is looking at the Projects
   hub or the editor.
2. **Its only output is text inside the terminal grid.**
   `installUserlandInternal` (`:436-451`) passes `{ msg -> target.notice(msg) }`
   as `onProgress`, and `TerminalSession.notice` (`TerminalSession.kt:260-263`)
   does `emulator.feed("\r\n$text\r\n")`. Lines like
   `userland: downloading bootstrap-phase3-aarch64.tar.gz…` and
   `userland: download 41 % (…)` are therefore **invisible from every tab except
   Terminal**, and are lost entirely if the user never opens that tab.
3. **The terminal's own "starting" chip does not say "downloading".**
   `TerminalScreen.kt:300-304` maps `TerminalLifecycle.STARTING` to the fixed
   string `"starting shell…"`. During a 200 MB download that is a lie by
   omission — and it is the only status a user who *does* open the tab sees.
4. **The foreground service starts too late to protect the download.**
   `TerminalViewModel.kt:185-193` starts `TerminalForegroundService` from
   `manager.anyAlive`, and `anyAlive` is recomputed from each session's `alive`
   flow (`TerminalSessionManager.kt:200-202`), which is set `true` only inside
   `TerminalSession.start()` (`TerminalSession.kt:117`) — i.e. **after** the
   install. During the download the app has a wake lock? **No:** the same block
   acquires the partial wake lock, so during the install there is neither
   foreground protection nor a wake lock. Android can kill the process.
5. **The kill is destructive in one specific window.**
   `UserlandInstaller.swapPrefix` (`:377-395`): `prefix.renameTo(old)` →
   `staged.renameTo(prefix)` → `old.deleteRecursively()`. A kill between the
   first and second rename leaves **no `usr` directory at all** and an orphan
   `usr.old-<timestamp>`. Nothing repairs it: `grep -rn "\.old-" app/src/main`
   returns only that line, and `STAGING_DIR` (`:538`) likewise appears only at
   its creation site (`:295`) — so neither orphan is ever cleaned, and nothing
   renames `usr.old-*` back.
6. **What the user then sees is exactly the owner's report.** Next launch:
   `hasRunnableUserland(prefix)` false → `installIfNeeded` tries a fresh install
   → offline it returns `SkippedOffline` with
   `"userland: offline — using built-in cc (TCC)"` (`:141`). C still compiles
   (TCC is in the APK), so the app *looks* fine — until the user taps **Install**
   in Packages, which does `terminalViewModel.sendCommand(item.installCommand)`
   (`ModulesScreen.kt:301-302`) into a shell whose prefix has no `pkg`. The
   result is a bare shell error: *"pkg: not found"*.
7. **Nothing refuses the action.** There is no "the userland is not ready" check
   anywhere on the Packages path — `sendCommand` queues into
   `OrderedReadinessQueue` and is flushed on the shell-ready marker
   (`TerminalViewModel.kt:265-275`), which fires for a *shell*, not for a
   *working prefix*.

## The two rules this phase must not break

1. **C must keep working with no setup.** TCC is in the APK and `.c` files have
   no install gate (Phase 21, permanent). A setup gate may therefore never block
   writing or running C — it must say *"C works right now; Python needs a
   one-time download."*
2. **No user-facing action may be able to kill the app** (the law Phase 43.1 was
   going to introduce; it survives the cancellation and lands here instead,
   because the install is the one action that runs before the user knows the app
   exists).

## Design in one picture

```text
        ┌── 44.1: the truth is visible ────────────────────────────────┐
        │  SetupState (StateFlow) ── InstallProgress(percent, stage)   │
        │      ├─ first-run gate card on the hub/editor (C works now)  │
        │      ├─ Terminal opened on first launch + "don't close" bar  │
        │      ├─ status chip: "downloading userland 62 %" (not        │
        │      │   "starting shell…")                                  │
        │      ├─ TerminalForegroundService started BEFORE the download│
        │      └─ Packages tab: install row shows progress, not "run"  │
        └──────────────────────────────────────────────────────────────┘
        ┌── 44.2: a kill cannot break it ──────────────────────────────┐
        │  InstallLedger {state, release, startedAt}  (commit(), not   │
        │      apply()) — durable before any risky step                │
        │  swapPrefix: no "no prefix" window  (or rename-back on boot) │
        │  boot sweep: orphan usr.old-* / .userland-staging-* removed  │
        │  SetupGatePolicy.refuse(action, state) → one honest sentence │
        └──────────────────────────────────────────────────────────────┘
```

## Risks to watch (multi-device round)

- **Slow/throttled mobile data.** The download must show a percentage that
  moves; a stalled progress line is indistinguishable from a hang, and a tester
  will kill the app at exactly the wrong moment (that is the bug this phase
  fixes).
- **Android 13+ notification permission.** `POST_NOTIFICATIONS` is requested
  only for `codec-notify` today (`MainActivity.kt:429-441`). The install
  notification must degrade gracefully when notifications are denied: the
  foreground service still runs, the in-app gate is the primary surface, and no
  crash occurs.
- **OEM "battery optimisation" killers** (Xiaomi/Samsung/Oppo) can still kill a
  foreground service. 44.2's restartability is the answer, not a promise that
  the process survives.
- **x86_64 emulators** have no bootstrap for some ABIs
  (`UserlandManifest.archNameForAbis` returns null) → `SkippedNoRelease`. The
  gate must render *"C works offline; extra languages are not available on this
  device"* rather than a spinner that never ends.

## Exit condition

```text
44.1
1. Fresh install, online, first launch: a visible setup surface appears WITHOUT
   the user opening the Terminal tab, and it shows a moving percentage.
2. The Terminal tab, opened during the install, shows "downloading userland 62 %"
   (not "starting shell…") and a "Don't close CodeC — finishing setup" bar.
3. A status-bar notification exists for the whole download and disappears when
   it ends (and when notifications are denied, nothing crashes and the in-app
   gate still works).
4. Writing and RUNNING a .c file works during the install (TCC gate unchanged).
5. Packages tab during the install: the install rows say what is happening and
   do not fire `pkg` into a prefix that has none.
44.2
6. Kill the app mid-DOWNLOAD (force-stop), relaunch: the download resumes
   (HTTP Range) or restarts cleanly; no half prefix; the gate reports truth.
7. Kill the app mid-EXTRACT, relaunch: no orphan staging dir remains after the
   boot sweep; the install completes.
8. Kill the app mid-SWAP (the two-rename window), relaunch: `usr` exists again
   (renamed back or re-installed); `pkg --version` works in the terminal.
9. After 6-8, `pkg install python` succeeds — the owner's exact failure.
PASS = all nine, on the owner's device.
```

## Tests (plan, per part)

- `SetupGatePolicyTest` (host): the refusal matrix (install / run C / run Python
  / open terminal / open editor × setup states), the message text per state, and
  "C is never gated".
- `InstallLedgerTest` (host): state transitions, an interrupted
  `EXTRACTING`/`SWAPPING` record detected on boot, and idempotent completion.
- `SwapRecoveryTest` (host, real temp dirs): a simulated kill between the two
  renames → `recover()` restores a runnable prefix; orphan
  `usr.old-*`/`.userland-staging-*` are swept; a *valid* `usr` is never touched.
- Robolectric `SetupStateVmTest`: `InstallProgress` is published for every
  `onProgress` line, `_isBusy`-style flags always clear (the "spinner forever"
  bug class), and a `Throwable` from the installer becomes a message + a log.
- Existing `UserlandInstallerTest` must keep passing untouched (its loopback
  keep-alive flake is documented in `TROUBLESHOOTING.md` §31 — do not "fix" it
  here).

## Deferred, recorded on purpose

- **Moving the bootstrap into the APK** — size (42.2 fought hard for 6.6 MB) and
  the ABI matrix make this a non-starter.
- **`WorkManager`** — deferred with reasons in the dossier §2.3.
- **Downloading packages in the background without a terminal** — out of scope;
  44 only makes the *existing* path visible and safe.
- **A retry UI ("Setup failed — try again")** — the installer already retries 30
  times with backoff (`MAX_DOWNLOAD_ATTEMPTS`, `UserlandInstaller.kt:552`); the
  explicit button already exists in the terminal toolbar
  (`TerminalScreen.kt:280`). 44.1 only surfaces it better.

## Sources

- CodeC code, 2026-09-12 — every file:line above.
- [developer.android.com/develop/background-work/services/fgs](https://developer.android.com/develop/background-work/services/fgs)
  — the foreground-service contract (fetched 2026-09-12).
- `docs/PHASE44_50_UX_RESEARCH.md` §2 (setup UX, Termux/Pydroid/Acode behavior,
  the WorkManager rejection).
- `docs/TROUBLESHOOTING.md` §27 (the Termux-clang fallback wording) and §31 (the
  installer test flake) — reused, not rewritten.
