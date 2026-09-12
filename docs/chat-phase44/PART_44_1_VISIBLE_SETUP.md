# CodeC Phase 44.1 — The install is visible everywhere

> **Status:** 🚧 **IMPLEMENTED + CORRECTED** (2026-09-12, `arena/01a0955a-codec`) · CI ✅ GREEN (`34693462725`) · **device round 1 🔴 FAILED four rows → three root causes fixed (see "Device round 1 corrections" below); round 2 pending** · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"the test user don't know it's installing so they
> close app before it complete"* → **owner's solution, kept almost verbatim:**
> *"If it opens the terminal 1st and show a warning don't close the terminal
> while userland is installing"*

## Symptom & first move

A tester installs the APK, opens it, sees a normal editor, does something else,
swipes the app away — and later every `pkg` command fails. **First move is
evidence, not code** (`rule.md` §4.2): reproduce on a device with a *clean
install* and capture (a) the log lines from `AppLogger`/`LogsScreen`, and (b)
whether the failure after the kill is `pkg: not found` (missing prefix) or a
dpkg error (partial prefix). The two answers lead to 44.2's two different
repairs, and both are plausible from the code (`UserlandInstaller.kt:377-395`).

The rest of this part needs no repro: it is a *visibility* gap, and the code
proves it — progress goes to `TerminalSession.notice()`
(`TerminalSession.kt:260`), which only the Terminal tab renders.

## Design

### 1. One observable truth: `SetupState`

A new Android-free policy object plus a ViewModel-exposed flow. The installer
already reports through a single `onProgress: (String) -> Unit` callback
(`UserlandInstaller.installIfNeeded`, `:116-125`) — 44.1 turns those strings
into structure *without* changing the installer's contract:

```kotlin
// ui/terminal/SetupState.kt  (pure)
enum class SetupStage { CHECKING, DOWNLOADING, VERIFYING, EXTRACTING, READY, FAILED, UNSUPPORTED }
data class InstallProgress(
    val stage: SetupStage, val percent: Int?, val bytes: Long?, val detail: String
)
object SetupGatePolicy {
    /** The one place "what may the user do right now" is answered. */
    fun can(action: SetupAction, stage: SetupStage, userlandRunnable: Boolean): SetupVerdict
    /** Parses the installer's existing progress lines (single source, no drift). */
    fun parse(progressLine: String): InstallProgress
}
enum class SetupAction { RUN_C, RUN_LANGUAGE, INSTALL_PACKAGE, OPEN_TERMINAL, EDIT_FILE }
sealed interface SetupVerdict {
    object Allowed : SetupVerdict
    data class AllowedWithNote(val message: String) : SetupVerdict
    data class Refused(val message: String) : SetupVerdict
}
```

`SetupGatePolicy.parse` keys off the *existing* installer strings
(`"userland: download 41 % (…) bytes"`, `"userland: verifying SHA-256…"`,
`"userland: extracting into …"`, `"userland: ready"`,
`"userland: no bootstrap for this ABI"`, …) so the installer stays untouched and
the parser is host-testable against the real lines. If a line does not parse,
the stage is `CHECKING` with the raw text as `detail` — never a crash, never a
fake percentage.

**The gate law (pinned by `SetupGatePolicyTest`):** `RUN_C` and `EDIT_FILE` are
`Allowed` in **every** stage. Everything else is `Refused` while
`!userlandRunnable`, with a message that says what is happening and what to do:

| Stage | `INSTALL_PACKAGE` verdict |
|---|---|
| DOWNLOADING 41 % | `Refused` — *"CodeC is downloading its Linux tools (41 %). Don't close the app — this happens once."* |
| EXTRACTING | `Refused` — *"Unpacking the Linux tools… Don't close the app."* |
| READY | `Allowed` |
| FAILED | `Refused` — *"Setup didn't finish (disk space / network). Open the Terminal tab and tap ⬇ to try again."* |
| UNSUPPORTED (no bootstrap for this ABI) | `Refused` — *"Extra languages aren't available on this device. C works offline."* |

### 2. The first-run setup surface (all tabs, not just Terminal)

- **`MainApp`**: a slim, non-modal **setup bar** under the safe-mode banner's
  slot (the same `Column` at `MainActivity.kt:857-866`) rendered while
  `SetupStage ∈ {CHECKING, DOWNLOADING, VERIFYING, EXTRACTING}`:
  `⬇ Setting up CodeC's Linux tools — 62 % · C works right now`, with a
  **VIEW** action that navigates to the Terminal tab. It disappears on `READY`.
  It never blocks the UI (the owner's row 1 is about *knowing*, not about
  waiting), and it is exactly the surface a user sees no matter which tab they
  are on.
- **Terminal tab first on a fresh install.** The owner asked for this and it is
  cheap: `MainApp`'s `startDestination` (`MainActivity.kt:712-717`) currently
  prefers the saved editor file and falls back to the hub. Add: when
  `firstLaunchComplete == false` **and** the setup is not `READY`, the post-
  welcome destination is the **Terminal** tab, so the download is on screen
  while it happens. The welcome tiles still come first (Phase 33.1) — this
  changes only what follows them, and only until the setup finishes once.
- **The "don't close" bar.** Inside `TerminalScreen`, above the status chip,
  while setup is not `READY`:
  `⚠ Don't close CodeC — it is finishing a one-time setup (62 %)`
  in the terminal's own chrome colours. One line, no dialog, no dismiss (there
  is nothing to dismiss: closing it does not stop the download, and a dismiss
  would recreate the original bug).
- **The status chip tells the truth.** `TerminalScreen.kt:300-304`'s fixed
  `"starting shell…"` becomes stage-aware:
  `downloading userland 62 %` / `unpacking…` / `starting shell…` /
  `shell failed`. `TerminalUx` (already pure) is the right home for the
  `(lifecycle, stage) → label` function, next to `TerminalLifecycle`.

### 3. The notification (and its honest degradation)

`TerminalForegroundService` already exists and is exactly the right shape
(channel `codec_terminal`, `IMPORTANCE_LOW`, `ic_stat_codec`, `setOngoing`).
Two changes:

1. **Start it before the download**, not after: in `startItem`
   (`TerminalViewModel.kt:323`) call `TerminalForegroundService.start(...)`
   *before* `installUserlandInternal(...)` when the userland is not yet
   runnable, and stop it after if no session ends up alive. The wake lock moves
   with it (same condition), so Doze cannot stall the download.
2. **The notification text carries progress**: `setContentText("Downloading
   CodeC's Linux tools — 62 %")`, updated at most once per 5 % or 2 s (the
   existing `setOnlyAlertOnce(true)` keeps it silent), and
   `"CodeC terminal"`/`"Terminal running in the background"` restored when a
   shell is what is actually running. The service gains an optional
   `EXTRA_STATUS` extra; no new service, no new channel, no new permission.

**Degradation law:** if `POST_NOTIFICATIONS` is denied (Android 13+), the
service still runs (a foreground service does not need the notification
permission to *run*; the notification is simply not shown) and the in-app bar is
the surface. No crash, no retry loop, no "notifications required" dialog — the
app never asks for a permission to protect its own download.

### 4. The Packages tab stops lying

`ModulesScreen.kt:301-302` (`terminalViewModel.sendCommand(item.installCommand)`
+ `onNavigateToTerminal()`) is gated on `SetupGatePolicy.can(INSTALL_PACKAGE,
…)`:

- `Allowed` → unchanged.
- `Refused` → no command is sent; the card shows the refusal sentence and the
  row's button becomes **VIEW SETUP**, which navigates to the Terminal tab.
  The command is **not** queued silently: a queued `pkg install` that runs 40
  seconds later with no visible cause is the same class of surprise this phase
  exists to remove.

The same gate covers the Quick Actions row (`ModulesScreen.kt:211`) and the
editor's "Install X?" prompt path (`EditorViewModel.confirmInstall`), which
today streams `pkg update && pkg install -y <pkg>` into the Output Panel
without knowing whether a prefix exists.

## Exit condition

```text
1. Clean install, online: within 2 s of the welcome tiles a setup surface is
   visible WITHOUT opening the Terminal tab, and its percentage moves.
2. The Terminal tab shows "downloading userland NN %" in the chip and the
   "Don't close CodeC" bar above it.
3. A status-bar notification shows the same percentage and disappears at READY.
   With notifications denied: no crash, no permission dialog, in-app bar works.
4. A .c file compiles and runs during the install (TCC path untouched).
5. Packages tab during install: install rows refuse with the named sentence and
   offer VIEW SETUP; no `pkg` command reaches the shell.
6. At READY every surface clears on its own — no restart, no manual refresh.
7. Force-stop mid-download, relaunch: the surface returns with the truth
   (resuming / restarting), never a stale 62 %.
PASS = all seven.
```

## Tests (plan)

- `SetupGatePolicyTest` (host, the important one): the full
  `SetupAction × SetupStage × userlandRunnable` matrix; **`RUN_C` and
  `EDIT_FILE` allowed in every stage** (the regression pin for "C is never
  gated"); every refusal message names either a percentage or a reason; no
  message contains the word "error" without also containing what to do.
- `SetupProgressParseTest` (host): each real installer line parses to the right
  stage/percent; unknown lines → `CHECKING` + raw detail; a `null` size never
  produces a fake percent; the byte-count line at 0 % is not "ready".
- `TerminalStatusLabelTest` (host, in `TerminalUxTest`'s file): the
  `(lifecycle, stage)` label table, including `FAILED` and `UNSUPPORTED`.
- Robolectric `SetupStateVmTest`: progress lines publish `InstallProgress`;
  the FGS start/stop condition is "not runnable → start before install";
  a `Throwable` from the installer clears the busy flag and publishes `FAILED`.
- Source-scan test: `ModulesScreen.kt` contains no `sendCommand(` call for an
  install command that is not preceded by a `SetupGatePolicy.can(` check — the
  cheap guard against a future row bypassing the gate (the shape of
  `StageAllHygieneTest`).

## Implementation (2026-09-12, `arena/01a0955a-codec`)

Shipped as specified, with six recorded deviations at the end of this section.
Every policy is pure Kotlin in `app/src/main/java/com/codeci/ide/ui/terminal/`;
the Android edge only publishes and draws it.

### New files

| File | What it holds |
|---|---|
| `ui/terminal/SetupState.kt` (660 lines) | `SetupStage` (`CHECKING/DOWNLOADING/EXTRACTING/SWAPPING/READY/FAILED/OFFLINE/UNSUPPORTED`), `InstallProgress` (+ `percent`, `settled`, `copyWithStage`), `SetupCapability`, `SetupAction`, `SetupVerdict` (`Allowed/Refused/Blocked`), `SetupFacts`, `SetupProgressParser.parse:176`, `SetupGatePolicy.can:270/:289`, `.refusal:308`, `.barText:380`, `.dontCloseText:409`, `.notificationText:428`, `.actionForCommand:459`, `.userlandUsable:484`, `.diskFacts:510`, `SetupTracker:551`, `SetupAnnouncer:621` |
| `ui/terminal/SetupStateBridge.kt` | `TerminalViewModel` → any other ViewModel: the live `SetupFacts`, or `diskFacts` as fallback (`factsOrDisk`) |
| `ui/terminal/SetupNoticeBridge.kt` | `TerminalViewModel` → `MainActivity`: the setup notice row (text + action) |
| `ui/components/SetupBar.kt` (133 lines) | the bar itself — `Surface` + progress row (percent only, never bytes/speed), a dismiss ✕ when the stage is settled and usable, optional `noticeText`/`onNoticeClick` |

### Changed files (the wiring)

| Site | Change |
|---|---|
| `TerminalViewModel.kt` | `setupProgress: StateFlow<InstallProgress>` + `setupNotice`; `publishSetup` runs the real parser through `SetupTracker` and re-derives facts; `startSetupKeepAlive()` / `stopSetupKeepAliveIfIdle()` acquire the wake lock and start the FGS **before** the first byte is written; `announceSetupNotification()` (throttled by `SetupAnnouncer`) drives the FGS text; `userlandUsable()` = `SetupGatePolicy.userlandUsable(prefix, ledgerPhase)` |
| `ui/services/TerminalForegroundService.kt` | `start(context, status, percent)` + `updateStatus(...)`; the setup sentence replaces the terminal copy, percent becomes `setProgress`; **one** channel (`codec_terminal`), the small icon stays `ic_stat_codec`; a refused `startForegroundService` (Android 12+ background start, denied `POST_NOTIFICATIONS`) is caught and logged via `AppLogger` |
| `ui/screens/TerminalScreen.kt:574` | `statusLabel = TerminalStatusLabel.label(setupProgress.stage, anyAlive)` — the chip is now stage-aware; `:762` renders `SetupGatePolicy.dontCloseText(progress)` |
| `ui/terminal/TerminalUx.kt` | `TerminalStatusLabel` (stage → chip text + tone), appended to the existing `TerminalUxTest.kt` as `TerminalStatusLabelTest` |
| `MainActivity.kt:761,763,778,947` | collects `setupProgress`; `startDestination` = terminal on a fresh, incomplete setup; `LaunchedEffect` opens the starter C file once setup settles; `SetupBar` sits between `SafeModeBanner` and `NavHost` so it is visible on every tab; `onStarterChosen` sets `setupDiverted` before `firstLaunchComplete` |
| `ui/screens/ModulesScreen.kt:63,69,159-164,195` | `setupRefusal = remember { mutableStateOf<String?>(null) }` + `runGated(action, command)`; `verdict`/`gated`/`runBlocked` decide the actions row (an already-installed package keeps **RUN**, a `pkg` transaction is refused and **VIEW SETUP** replaces INSTALL); all four `terminalViewModel.sendCommand(` sites are inside the gate |
| `ui/viewmodels/EditorViewModel.kt:2178` | `confirmInstall` refuses through `SetupGatePolicy.can(INSTALL_PACKAGE, SetupStateBridge.factsOrDisk(...))` and prints the honest sentence into the Output Panel; `installUserland()` still works in every stage |

### The capability rule as implemented (`actionForCommand`)

`RUN_C` and `EDIT_FILE` are **never** gated. Everything else derives its
capability from the command's own words:

* `install`/`update`/`remove`/`uninstall`/`search`/`show`/`list`/`upgrade`/`clean`/`autoclean`/`autoremove`/`files`/`depends`/`policy`/`dpkg` → `PACKAGE_MANAGER`
* `clang`/`clang++`/`python`/`python3`/`node`/`npm`/`bash`/`sh`/`zsh`/`gdb`/`make`/`cmake`/`git` → `USERLAND_SHELL`
* anything else → `NONE` (no gate).

### Deviations from this part's spec (recorded, not silent)

1. **`OPEN_TERMINAL` is never refused.** The Terminal tab is where the setup is
   watched; refusing it would trap the user outside the only progress surface.
   Its verdict is `Allowed` in every stage and `SetupGatePolicyTest` pins that.
2. **The Robolectric `SetupStateVmTest` was not written.** Driving
   `TerminalViewModel.init` under Robolectric spawns a PTY/JNI session and would
   *really* download and install a userland — the same instability class as
   TROUBLESHOOTING §31. Replaced by (a) pure `SetupTracker` tests inside
   `SetupProgressParseTest` and (b) `SetupGateWiringTest`, a source-scan class
   that pins the eleven wirings above (every `sendCommand` gated, keep-alive
   before the download, progress lines → `publishSetup`, the boot repair next to
   `TempGc`, the bar between `SafeModeBanner` and `NavHost`, one notification
   channel, the chip using `TerminalStatusLabel.label`, …).
3. **The bar and the notice row are dismissible once the stage is settled and
   usable**, and the FGS is dismissed only when `setupKeepAlive` is false. A
   device with a READY-but-unusable prefix is never trapped behind the bar (the
   spec's "never dismissible" would have made the bar a wall).
4. **First-run diversion also opens the starter C file** when setup settles
   (`LaunchedEffect` at `MainActivity.kt:778`), so a user who never left the
   Terminal tab still lands in the editor. Phase 45.1's guide will own this
   moment properly; the hook is one place.
5. **`announceSetupNotification` runs on every progress line** (the 5 % / 2 s
   throttle lives in `SetupAnnouncer`), while facts are only republished when
   the tracker says something a gate cares about changed — a long download must
   not cost thousands of `stat` calls and `SharedPreferences` writes.
6. **The keep-alive hands over instead of stopping**: when a real session starts
   during setup, `setupKeepAlive` goes false and the FGS/wake lock stay held by
   the session path; otherwise a failed install releases them
   (`stopSetupKeepAliveIfIdle`).

## Sources (record)

- CodeC 2026-09-12: `TerminalViewModel.kt:185-205,323-345,436-451`,
  `TerminalSession.kt:117,260`, `TerminalSessionManager.kt:200-202`,
  `TerminalScreen.kt:280,300-304`, `ModulesScreen.kt:211,301-302`,
  `UserlandInstaller.kt:116-125,538-552`, `MainActivity.kt:629,712-717,857-866`,
  `TerminalForegroundService.kt` (whole file).
- [developer.android.com/develop/background-work/services/fgs](https://developer.android.com/develop/background-work/services/fgs)
  (fetched 2026-09-12) — "noticeable to the user" is the test for a FGS, and the
  notification *is* the affordance.
- `docs/PHASE44_50_UX_RESEARCH.md` §2.2 — Termux's full-screen bootstrap gate,
  Pydroid's zero-setup C-analogue, Acode's per-item progress.

## Deferred / rejected with reasons

- **A blocking full-screen setup activity** (Termux's literal model) — rejected:
  CodeC's C story works with no setup, so blocking the app would make the
  majority case worse to protect the minority one. The bar + first-launch
  terminal gets the awareness without the wall.
- **A dialog the user must dismiss** — rejected: a modal on first launch is the
  first thing a tester closes, and closing it would hide the very information
  this part exists to show.
- **Progress in a snackbar** — rejected: snackbars time out; a download outlives
  them.
- **Renaming the notification channel** — rejected: the channel exists and is
  already `IMPORTANCE_LOW`; a second channel would let the user mute one and
  not the other.

---

## Device round 1 corrections (2026-09-12, same day)

The owner installed the round-3 artifact (`ba51382`) **over an existing install**
and four of this part's own acceptance rows failed. Everything below is a
correction to code written *above*, with the new law pinned by a test.

### C1. The bar was a wall in exactly the state it was written for

The action button rendered only `if (inFlight || stage == FAILED)`, and the ✕
called `onDismissNote`, which clears a *note* — so in the state the owner was in
(the bar showing a `detail` sentence, stage `READY`), the bar was **neither
tappable nor closeable**. That is not a visibility feature; that is a wall, and
it is the opposite of the owner's own instruction (*"show a warning"*).

**Law (pinned):** the setup bar is always actionable — the whole row is one tap
to the Terminal tab, **VIEW SETUP** always renders, and the close button only
exists when `SetupGatePolicy.barDismissAllowed(progress)` (= `progress.settled`)
allows it, and it then really removes the bar for that text
(`dismissedSetupBar` remembers the dismissed *sentence*, so a changed state
brings the bar back).

### C2. "Terminal tab first" did not apply to an updated install

The launch divert was keyed on the first-run welcome handing over, so a phone
with a *pre-44* install (exactly the owner's) never got terminal-first.
**Law (pinned):** `startDestination` stays pre-44 (an argument-carrying route in
`startDestination` risks graph construction for no gain); the divert is decided
**synchronously from the disk** —
`SetupGatePolicy.startOnTerminal(userlandUsable(prefix, ledgerPhase), UserlandManifest.archName() != null)`
— and applied with a single `navigate()` after the first composition. The starter
auto-open is skipped when the divert happened, so a fresh phone gets the terminal,
not the starter dialog over an editor.

### C3. `restoreState = true` restores a whole saved sub-stack, not a tab

`navigate(Screen.Terminal.createRoute()) { popUpTo(graph.startDestinationRoute) {
saveState = true }; restoreState = true }` puts back the *saved stack* for that
destination — so if the user had opened the editor above the terminal earlier,
"go to the terminal" arrived at the **editor**. This is why *"Every package
saying view setup but terminal not opening editor opening"* was true and not a
misreport.
**Law (pinned):** every navigation whose intent is *show me the terminal* is
`restoreState = false` — the launch divert, the setup bar's `goToSetup`,
ModulesScreen's VIEW SETUP, the editor's `onOpenInTerminal`. The bottom bar uses
`restoreState = screen !is Screen.Terminal`, so the other four tabs keep their
pre-44 behaviour (Phase 49 is the systematic navigation pass).

### C4. A marker was accepted as a working prefix

This one belongs to 44.2's law but surfaced as a 44.1 symptom (the bar saying
*"the Linux tool need one install"* forever): `installIfNeeded(force = false)`
answers `AlreadyInstalled` from the release **marker** alone — the fast warm-open
path, deliberately no disk probe — and a *pre-44* build wrote that marker
**before** the two-rename swap. The ViewModel published `READY` on that answer,
so the facts said "not usable" while the stage said "done", and nothing ever
re-decided it.
**Law (pinned):** the `AlreadyInstalled` branch re-checks
`SetupGatePolicy.userlandUsable(prefixDir, phase)`; false ⇒
`setupTracker.fail("installed marker but bin/pkg does not run",
SetupIssue.BROKEN_USERLAND)` + a notice, and the refusal sentence now reads
*"Setup didn't finish — the Linux tools aren't working…"* (the old *"can't start
on this device"* reads like an unsupported phone). `init` also runs
`SetupRecoveryGate.awaitFinished(); refreshSetupFromDiskWhenIdle()` so a
post-repair disk state replaces the boot-time reading. **Not done on purpose:**
auto re-download in that branch (~40 MB on possibly mobile data — this phase's
law is *no surprises*); it is one call, `installUserland(force = true)`, away.

### Tests added by the corrections

`SetupGatePolicyTest` 21 → **25** (the `startOnTerminal` matrix, the
`barDismissAllowed` matrix, the exact bar sentence the owner quoted, stale-vs-
swapping facts). `SetupGateWiringTest` 15 → **20** (bar always actionable; no
*show-me-the-terminal* navigation may carry `restoreState = true`; the divert is
disk-based and uses the pattern route; `AlreadyInstalled` re-checks the disk;
post-repair refresh exists). **109 host cases green locally.** Re-test rows:
[`DEVICE_ROUND.md`](DEVICE_ROUND.md) **R1-R8**, then D1-D12 on a fresh install.
Owner-facing explanation: [`../TROUBLESHOOTING.md`](../TROUBLESHOOTING.md) §35.
