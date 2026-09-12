# CodeC Phase 44 — Setup you can see, and cannot half-finish

> **Status:** 🚧 **IMPLEMENTED** (2026-09-12, `arena/01a0955a-codec`) — code
> + tests written, CI (`Build APK`) pending, device round required before it is
> called tested · **Cost:** `[client-only]` · **Effort:** M · **Owner row (verbatim):** *"Userland is
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
| [44.1](PART_44_1_VISIBLE_SETUP.md) | The install is visible everywhere | M | 🚧 IMPLEMENTED (CI pending) |
| [44.2](PART_44_2_ATOMIC_SETUP.md) | The install cannot half-finish | M | 🚧 IMPLEMENTED (CI pending) |

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

## Implementation record (2026-09-12, `arena/01a0955a-codec`)

Both parts are written. Code lives in the repo, not in this doc; the part docs
carry the file-by-file record
([44.1](PART_44_1_VISIBLE_SETUP.md#implementation-2026-09-12-arena01a0955a-codec),
[44.2](PART_44_2_ATOMIC_SETUP.md#implementation-2026-09-12-arena01a0955a-codec))
and their **deviations are listed there**, not hidden. Summary:

```text
new   ui/terminal/SetupState.kt        660   parser + gate policy + tracker + announcer (pure)
new   ui/terminal/SetupLedger.kt       155   durable attempt record + resumePlan (pure)
new   ui/terminal/SetupRecovery.kt     292   boot repair: restore / sweep / gate  (pure + File)
new   ui/terminal/SetupLedgerPrefs.kt   48   the SharedPreferences store (commit, never apply)
new   ui/terminal/SetupStateBridge.kt   35   VM → VM facts
new   ui/terminal/SetupNoticeBridge.kt  30   VM → activity notice
new   ui/components/SetupBar.kt        133   the bar (all tabs, percentage only)
new   6 test classes + 1 appended      ~1700  109 host cases
mod   TerminalViewModel · UserlandInstaller · TerminalForegroundService · MainActivity
mod   TerminalScreen · TerminalUx · ModulesScreen · EditorViewModel
```

Nothing new was added to the dependency list, no DataStore key was created (the
ledger is its own `SharedPreferences` file, which `SettingsKeysHaveReadersTest`
does not scan), no Settings control was added (so `SETTINGS_AUDIT.md` is
unchanged), no permission was added, and the notification small icon is still
`ic_stat_codec`. `MANAGE_EXTERNAL_STORAGE` is not involved.

**Host pre-validation (`rule.md` §9, optional):** 109 cases green locally through
`kotlinc` + `jdk4py` + shimmed `org.junit`/`flow`
(`SetupGatePolicyTest` 25 · `SetupProgressParseTest` 22 · `SetupLedgerTest` 13 ·
`SwapRecoveryTest` 16 · `UserlandUsableTest` 9 · `SetupGateWiringTest` 20 ·
`TerminalStatusLabelTest` 4). The Compose/JNI edges cannot compile in this
sandbox at all (no `android.jar`), so **CI's `Build APK` is the executor of
record** and this round is a pre-check only — never a claim of passing tests.

**Test-name deviation:** the plan's `InstallLedgerTest` shipped as
`SetupLedgerTest`; the plan's Robolectric `SetupStateVmTest` was replaced by pure
`SetupTracker` tests + `SetupGateWiringTest` source pins (reason recorded in
PART_44_1's deviation 2 — a Robolectric `TerminalViewModel` would really spawn a
PTY and really install a userland).

### CI round 1 — 🔴 `34692621773`, one signature, seven errors

`Build APK` on tip `e3d1e64` failed with **seven** `Unresolved reference`
errors, all in `TerminalViewModel.kt` (`installIfNeeded`, `installedRelease`,
`releaseTag`, `message` ×2, "cannot infer type", "none of the following
candidates is applicable"). Read together they are **one** fault: `userland` was
built as `UserlandInstaller(application, ledger = setupLedger)`, but the
installer's *secondary* `(Context)` constructor did not declare a `ledger`
parameter, so the constructor call did not resolve, so `userland`'s type was
unknown, so every member access on it reported "unresolved". Fix: the secondary
constructor takes `ledger: SetupLedger? = null` and forwards it
(`UserlandInstaller.kt:107-121`) — every pre-existing caller, including
`UserlandInstallerTest`, keeps compiling unchanged.

Pinned so it cannot regress: `SetupGateWiringTest`'s new
`the installer's Context constructor accepts and forwards the ledger`
(**109 host cases**, green locally before the re-push). Lesson recorded as
TROUBLESHOOTING §33 — *a cascade of "Unresolved reference" errors on one
receiver is a single constructor/signature fault; fix the first error and the
rest disappear.*

### CI round 2 — 🔴 `34692963464`, lint `NewApi` in a *pure* file

The Kotlin compile and the unit tests passed; `:app:lintDebug` aborted the build
with **two** errors, both on one line of the pure recovery policy:

```text
LINT ERROR [NewApi] ui/terminal/SetupRecovery.kt:248:
  Call requires API level 26 (current min is 24): java.nio.file.Files#isSymbolicLink
  Call requires API level 26 (current min is 24): java.io.File#toPath
```

`isSymlink` used `java.nio.file` to honour the sweep law's "never a symlink"
clause — legal on the host JVM the local harness runs on, illegal on
`minSdk 24`. **A file being "pure Kotlin" does not put it outside Android lint.**
Fixed the way `TarGzExtractor` already does (`/** minSdk 24 — avoid
java.nio.file (API 26). */`), with two API-1 mechanisms that are together
*stronger* than the nio call:

1. `isSymlink` compares the **canonical name** with the name — a link's
   canonical file is its TARGET, so `usr.old-900` → `target-k` is caught; and
   anything that cannot be resolved now counts as a link (never deleted) instead
   of the old "assume it is fine".
2. `deleteOrphan` tries a plain `File.delete()` **before** `deleteRecursively()`.
   `delete()` `unlink`s a symlink without following it, and `deleteRecursively()`
   DOES follow a link to a directory — so the recursive walk now only ever runs
   on a real, non-empty tree. That covers the case the name check cannot see: a
   link whose target carries the *same* orphan-shaped name (creatable only by
   root).

Both are pinned by two new `SwapRecoveryTest` cases with **real symlinks** on a
real filesystem (`a symlink shaped like an orphan is never scanned`,
`a same-named symlink target survives the sweep because delete() unlinks first`)
→ **109 host cases**, green locally before the re-push. Runbook:
TROUBLESHOOTING §34.

### CI round 3 — `34693462725` (tip `ba51382`): **observed green, not re-read**

`gh run watch 34693462725 --exit-status` returned **0** (success) and the run's
final steps were listed — `Report APK sizes (Phase 42.1)`, `Upload debug APK
artifact`, `Upload release APK artifact`, `Verify tag against the binary`,
`Publish app release (Phase 42.1)` — with **no error annotations at all**. Those
steps only run after assemble + `:app:testDebugUnitTest` + `:app:lintDebug`
succeed, so the two rounds of faults above are fixed.

**Honest caveat:** the GitHub token expired immediately afterwards
(`HTTP 401: Bad credentials`, and `git ls-remote` lost its credential too), so
the run's `conclusion` field, duration and APK-size delta could **not** be
re-read. Until the next session can run
`gh run view 34693462725 --json conclusion,updatedAt` and paste the answer here,
this line reads *observed green, unconfirmed* — not "CI ✅ GREEN". Nothing was
merged, no PR was opened, and the branch was not pushed again after `ba51382`.

### Device round 1 — 🔴 the owner ran it (2026-09-12) and four rows failed

The owner installed the round-3 artifact on his phone (an **updated** install, so
no first-run welcome) and reported: *"I couldn't not open the terminal it's
opening the editor"* · *"The top a massage 'C works right now. The linux tool
need one install- open terminal and tap download'"* · *"But it's not closing or
opening terminal"* · *"Every package saying view setup but terminal not opening
editor opening"*. Full record with the eight re-test rows:
[`DEVICE_ROUND.md`](DEVICE_ROUND.md#round-1-result--failed-on-4-of-the-owners-own-rows-2026-09-12).

Three root causes, all provable from the code, all fixed in this round:

1. **A marker is not a prefix.** `installIfNeeded(force = false)` answers
   `AlreadyInstalled` from the release **marker** alone, and the VM turned that
   into stage `READY`. His phone has a marker written by a *pre-44* build (which
   wrote it **before** the swap) over a prefix with no working `bin/pkg` — the
   exact half-finished state 44.2 exists to repair. `READY` (marker) + not usable
   (disk) = the sentence he quoted, permanently. Now the `AlreadyInstalled`
   branch asks the disk and publishes `FAILED(BROKEN_USERLAND)` when the marker
   lies, the wording says *"the Linux tools aren't working"* (not *"can't start on
   this device"*, which reads like an unsupported phone), and
   `refreshSetupFromDiskWhenIdle()` takes a second look once the boot repair is
   finished so a stale ledger cannot pin the bar either.
2. **The bar was a wall in exactly that state.** The action rendered only
   `if (inFlight || stage == FAILED)` and the ✕ cleared a *note* that was not
   there. Now the whole bar is one tap to the terminal, **VIEW SETUP** is always
   rendered, and the ✕ is real (`SetupGatePolicy.barDismissAllowed` =
   `progress.settled`; the dismissal remembers the bar's text, so a changed state
   brings it back).
3. **`restoreState = true` restores a whole sub-stack, not a tab.** Navigating to
   the terminal with `popUpTo(start) { saveState = true }` + `restoreState = true`
   put back a saved stack whose top was an editor the user had opened above the
   terminal — so "go to the terminal" arrived at the editor. Every navigation
   that means *show me the terminal* (setup bar, Packages' VIEW SETUP, the
   editor's terminal hand-off, the launch divert, the bottom bar's Terminal tab)
   now uses `restoreState = false`; the other four tabs keep their pre-44
   behaviour and Phase 49 remains the systematic navigation pass.

Plus the launch-tab gap behind *"it's opening the editor"*: the divert was keyed
on the welcome hand-over, so an updated install never got "terminal first". It is
now `SetupGatePolicy.startOnTerminal(usable, abiSupported)` decided
**synchronously from the disk** and applied with a `navigate()` after the first
composition — not by putting an argument-carrying route into `startDestination`.

**Deliberately not done:** an automatic re-download when the marker-only state is
detected (~40 MB on possibly mobile data, and this phase's law is *no surprises*).
The one-tap path is signposted instead; if the owner wants auto-repair on launch
it is one call (`installUserland(force = true)`) in that branch.

**What is still open:** the exit condition is a **device** condition. Round 1 was
run by the owner and **failed four rows** (above); the fixes are implemented,
**109 host cases** are green locally **and CI round 4 (`34695797493`, tip
`4bf3c4c`) is ✅ GREEN** (job `build` 10m53s, zero error annotations, artifacts
`CodeC-IDE-release` 6,651,682 B / `CodeC-IDE-debug` 25,608,360 B, v1.3.17 — per
`rule.md` §5 that green run means `assembleDebug` + `testDebugUnitTest` +
`lintDebug` all passed, so the host suite ran on real Gradle/JUnit/Robolectric).
**Round 2 (R1-R8, then D1-D12 on a fresh install) has NOT been run.** Until the
owner reports it, Phase 44 is 🚧 IMPLEMENTED, not ✅ COMPLETE, and nothing here
may be described as tested on hardware.

## Sources

- CodeC code, 2026-09-12 — every file:line above.
- [developer.android.com/develop/background-work/services/fgs](https://developer.android.com/develop/background-work/services/fgs)
  — the foreground-service contract (fetched 2026-09-12).
- `docs/PHASE44_50_UX_RESEARCH.md` §2 (setup UX, Termux/Pydroid/Acode behavior,
  the WorkManager rejection).
- `docs/TROUBLESHOOTING.md` §27 (the Termux-clang fallback wording) and §31 (the
  installer test flake) — reused, not rewritten.
