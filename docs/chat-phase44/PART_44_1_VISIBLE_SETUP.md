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

---

## Phase 45 rounds 4-5 (2026-09-12) — the chrome lock: *"the user can not access any other option"*

### The request, verbatim

> When the userland is installing and unpacking the user can not access any other other
> option and it will show a sweet massage of why can't access any other option

It arrived as a Phase 45 note (the owner had just run the guided tour), but it is a
44.1 question — *"what may the user do right now"* — so it is specified here and
implemented beside the gate: one new pure object, `SetupLockPolicy`, in `SetupState.kt`,
with the same shape as everything else in this part (a stage in, one honest sentence
out, host-tested).

### What the gate already answered, and what was missing

`SetupGatePolicy.can(action, facts)` answers **capability**: may this *act* run — a
`pkg` install, a language run, a `.c` compile. It has always refused with a sentence,
and it has always allowed `RUN_C`, `EDIT_FILE` and `OPEN_TERMINAL`.

What nothing answered was **chrome**: while the one-time setup is moving, every tab is
still tappable, and the taps that "work" are the ones that walk the user away from the
only place the install can be watched. That is the option the owner wants closed, and
closed *with an explanation* — a refused tap that says nothing reads as a broken
button.

### The lock

`SetupLockPolicy.lock(progress, facts, packageInstallRunning, reducedStart)` →
`ChromeLock(reason, message)`. Two triggers, one shape:

| Reason | When | Paused | Never paused |
|---|---|---|---|
| `USERLAND_STARTING` (round 5) | **no usable prefix and setup not given up on** — from the first frame, before any byte moves: the disk probe, the reach for the network, the server's first answer | Projects, Editor, Packages, Settings | **Terminal** — `watchOption` |
| `USERLAND_DOWNLOAD` / `_VERIFY` / `_UNPACK` | the bootstrap is moving **and** `!facts.usable` (a first install or a repair) | Projects, Editor, Packages, Settings | **Terminal** — `watchOption`, where the download, the percentage, the "don't close" line and the ⬇ retry live |
| `PACKAGE_INSTALL` | an install the user asked for is streaming into the editor's Output Panel (`OutputRunState.installing && busy`) | Projects, Packages, Settings, **Terminal**, and inside the editor ☰ + RUN ▶ + the drawer's edge swipe | **Editor** — `watchOption`, where the Output Panel is |

**The law (pinned):** *the surface that shows the install is never paused.* A pause
with no way to watch the thing you are waiting for is how an app looks bricked, and
"it looks bricked" is the failure this whole part exists to remove.

Three things deliberately pause **nothing** (round 4 exempted `CHECKING` too; round 5
took that exemption away — see below):

- **A usable prefix**, whatever the stage says. `facts.usable` short-circuits the whole
  userland branch, so a launch on an installed phone pauses nothing — including an
  **in-flight upgrade** (`facts.usable == true` while the stage moves), whose own 44.1
  sentence is *"everything still works"*. Taking the app away from a user who can use it
  would be a lie in the other direction. This is also why locking the probe cannot flash
  a pause on every launch: the facts are read **synchronously from the disk** in
  `TerminalViewModel`'s constructor, so frame one of a working phone already knows.
- `FAILED` / `UNSUPPORTED` — settled, and stopped. Each has its own sentence at the point
  of use (`refusal`) and its own retry (⬇ in the terminal toolbar); pausing the app for
  a setup that has already given up would strand the user in an app that could still
  compile their file (**C works offline right now** is a 44.1 promise).
- **A reduced start** (Phase 42.3's safe mode, `reducedStart`). Its whole purpose is to
  do *less* at startup and to reach **export all projects** and **report a crash**, both
  in Settings; a phone that has crashed three times is the last phone that may be
  funnelled to one tab by a startup-shaped lock. An install the user **asked for** still
  pauses the chrome in safe mode — one runner, one job.

And two 44.1 guarantees are untouched, because a lock is an answer about **chrome**,
not about capability: `RUN_C` and `EDIT_FILE` are still allowed in every stage, and
while the *userland* is being built the editor keeps both (`editorChromeLocked` is
false — typing and `cc` never wait for a download). A package install is the one pause
that reaches inside the editor, and it is not the userland's fault: one runner, one job
at a time. RUN ▶ during a busy panel was already a silent `return`
(`EditorViewModel.confirmInstall` / `runActiveFile`), so the choice was between a tap
that does nothing and a tap that explains itself.

### The sentence

One per reason, from `sweetMessage(reason, percent)`, and the same sentence on every
paused option — three explanations for one install is how an app starts talking to
itself. It answers *what is happening*, *why everything else is paused*, and *where to
watch*:

| Reason | Sentence |
|---|---|
| starting (round 5) | *Hang tight — CodeC is getting ready to set up its Linux tools. Other options are paused for a moment so this one-time setup finishes cleanly. The Terminal tab shows every step.* |
| download (with %) | *Hang tight — CodeC is downloading its Linux tools (62 %). Other options are paused for a moment so this one-time setup finishes cleanly. The Terminal tab shows every step.* |
| download (no %) | same, without the percentage — **never invented** (44.1's rule) |
| verify | *Hang tight — CodeC is checking the download it just made. …* |
| unpack | *Hang tight — CodeC is unpacking its Linux tools — the last step. …* |
| package install | *Hang tight — CodeC is installing what you asked for. Other options are paused for a moment so this one install finishes cleanly. The Output panel shows every step.* |

It arrives **twice**: once when the pause begins (`LaunchedEffect(chromeLock.locked)`,
keyed on `locked` so the three stages of one download do not stack three snackbars —
the moving percentage is the setup bar's job), and again on every refused tap.

### Where it is wired

- `MainActivity` computes `chromeLock` from the two live signals (`setupProgress` +
  `setupFacts` from `TerminalViewModel`, `EditorChromeState.installRunning` from the
  editor) and hands the bar a per-tab `verdictFor`; `FlatBottomBar` dims a paused tab
  (0.45 alpha), draws a small `Icons.Default.Lock` on it **before** it is tapped, and
  routes the tap to the sentence instead of to `navigate`. The scaffold grew a
  `snackbarHost` — a line of text, not a wall, and it blocks nothing.
- `EditorChromeState` grew a third fact, `installRunning`, because the tabs live in
  `MainActivity` and the install lives in the editor; it is cleared on dispose, since a
  stale "installing" would leave the whole app paused behind an install that finished
  with the screen.
- `OutputRunState` grew `installing` (default false, set on the ONE install path,
  cleared by both of its exits). `busy` alone is not enough: a run — or the Flask
  server the guided tour itself starts — keeps `busy` true for minutes, and pausing the
  app for the user's own program would be a prison, not a courtesy.
- `EditorScreen` uses the same policy for ☰ (`onDrawerTap`), RUN ▶ (`onRunTap`) and
  `ModalNavigationDrawer.gesturesEnabled` — a lock you can edge-swipe around is not a
  lock — and shows the same sentence through its own snackbar host.
- The guided tour pauses with the chrome (`blockedByForeground … || chromeLock.locked`,
  [`../chat-phase45/PART_45_2_COACH_MARKS.md`](../chat-phase45/PART_45_2_COACH_MARKS.md)
  §Round 4, deviation 20): a box on a paused control could only be spent by a tap that
  merely shows the sentence.

### Round 5 (2026-09-12, later) — *"Make it instantly after 1st open"*

The owner ran round 4 on his phone and accepted the lock, with one correction:

> The lock option is good but still it late user can switch before the start of userland
> download because is takes a little time to connect and user can switch task between
> them
>
> Make it instantly after 1st open and others are ok

He is right, and the gap was round 4's own doing: `reasonFor` waited for a stage that
means *work is moving*, so the whole `CHECKING` window was open — the ledger read, the
probe of `bin/pkg` and `bin/bash`, the reach for the network, the server's first answer.
On a cold phone with a cold radio that is seconds, and Phase 44.1's launch divert only
decides the *starting* tab: one tap in that window walked the user to Packages, where
every language reads "not installed" about tools that are already on their way.

**The rule changed from stage-keyed to prefix-keyed.** With no usable prefix, every
stage except the two that mean "setup stopped" is paused:

```kotlin
if (packageInstallRunning) return PACKAGE_INSTALL   // what the user just asked for
if (facts.usable)          return NONE              // a working phone, incl. upgrades
if (reducedStart)          return NONE              // safe mode must reach Settings
return when (progress.stage) {
    DOWNLOADING -> USERLAND_DOWNLOAD
    VERIFYING   -> USERLAND_VERIFY
    EXTRACTING  -> USERLAND_UNPACK
    FAILED, UNSUPPORTED -> NONE                     // stopped: own sentence, own retry
    else        -> USERLAND_STARTING                // CHECKING, and a READY the disk denies
}
```

Three consequences worth stating, because each is a decision and not an accident:

1. **The defaults are the paused case.** `lock()` with no arguments *is* the first frame
   of a fresh install (`CHECKING`, empty facts) and it answers PAUSED. A caller that has
   not heard anything yet must not default to "everything is open" — that default is how
   the window existed at all.
2. **A boot-time repair is the same case.** An interrupted swap leaves `swapping` true,
   which makes `usable` false, so the restore that 44.2 runs on boot now pauses the
   chrome while it puts the prefix back — with the Terminal open, where the repair is
   logged.
3. **A `READY` the disk contradicts is not "done".** That state is transient (this
   part's own C4 correction turns it into `FAILED` with *"Setup didn't finish"*), but
   while it lasts the tools are not there and nothing has been given up on, so it reads
   as `USERLAND_STARTING` rather than as an open app.

What round 5 does **not** change: the watch surface is still never paused, the sentence
is still one per reason and still never invents a percentage (the starting sentence has
no `%` in it at all), the editor still keeps typing and `cc` while the *userland* is
being built (`editorChromeLocked` is a package-install-only answer), and
`SetupGatePolicy.can` is still the only answer about capability — the lock beside it
never weakens it.

### Not locked, on purpose (the owner may overrule this list)

Read literally, *"any other option"* would also close the editor's file tree, the
keyboard, saving, `cc`, the Terminal during a bootstrap install, and the setup bar's
VIEW action. This round locks **chrome that leads away from, or collides with, the
install** and keeps everything the 44.1 laws guarantee. If the owner wants the harder
version — one full-screen "setting up" surface with nothing tappable but the progress —
that is a small change to `SetupLockPolicy.option` plus a veil composable, and it is
worth saying out loud that it would make a first launch (which starts the download
automatically, and which 44.1 already diverts to the Terminal tab) a waiting room.

### Tests added

`SetupGatePolicyTest` 25 → **33** (30 in round 4, +3 in round 5): the three userland stages pause the chrome and
explain themselves (sentence, percentage, watch surface, every other option refused
with the SAME sentence) · a probe, three settled stages and an upgrade of a working
prefix pause nothing · a package install keeps the Editor and pauses the rest,
including the editor's own chrome · routes → options, and a non-tab route → none ·
**the lock never weakens the gate beside it** (`RUN_C` / `EDIT_FILE` / `OPEN_TERMINAL`
still allowed while the chrome is paused). Round 5 added: **the first frame of a fresh
install is paused** (`lock()` with no arguments, the probe window, an interrupted swap
being repaired, a `READY` the disk denies — each with the watch surface open, the same
sentence everywhere, and no invented percentage); **a usable prefix pauses nothing at
any stage**, including an upgrade moving through all three stages; and **a reduced start
pauses nothing** while a package install still outranks it. `SetupGateWiringTest` 20 →
**24**: the bar
asks the policy and a paused tab does not navigate · a paused tab looks paused · the
sentence arrives when the pause begins · the editor reports the install only it can see
and clears it on dispose · `installing` has exactly one writer and two clears · the
editor's ☰ / RUN ▶ / edge swipe answer the same policy; round 5 pins that
`FAILED, UNSUPPORTED` are the ONLY stages answering `NONE`, that `facts.usable`
short-circuits **before** the stage table, that `MainActivity` passes
`reducedStart = SafeMode.active`, and that the facts really are built in the
ViewModel's constructor from the disk. **121 Phase 44 host cases green locally** (197
with Phase 45's 76), CI ✅ GREEN on the round-4 commit (`34711827176`, tip `e7759f1`,
release APK 6,675,258 B), round 5's run pending. Device rows:
[`../chat-phase45/DEVICE_ROUND.md`](../chat-phase45/DEVICE_ROUND.md) **G34-G39**.
Owner-facing explanation: [`../TROUBLESHOOTING.md`](../TROUBLESHOOTING.md) §38 (round 4)
and §39 (round 5).
