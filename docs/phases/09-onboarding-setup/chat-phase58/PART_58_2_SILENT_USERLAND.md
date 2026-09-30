# PART 58.2 — the userland installs, and says one sentence

**Roadmap line:** *“58.2 Userland installs with no strip. One warning when a run needs it.”*
**Owner's standing row, verbatim:** *“Userland installs silently; one warning when a run needs
a download before userland is ready.”*

## 1. Research, before any code

Re-read on this checkout, as the roadmap asks:

* `ui/terminal/UserlandInstaller.kt` — unchanged by this part. The install still runs from
  `TerminalViewModel`'s session start (`startItem` → `installUserlandInternal`, `:416`; the
  Terminal's own ⬇ calls it with `force = true`), and the boot repair still posts its note
  through `SetupNoticeBridge.post`.
* `ui/terminal/SetupState.kt` — `SetupGatePolicy` is the one vocabulary for every refusal
  (`can(action, facts)`, `refusal(...)`, `SetupLockPolicy`). It also held the **strip's** three
  functions: `barText`, `barVisible`, `barDismissAllowed`.
* `ui/components/SetupBar.kt` — the strip itself: a bar under the safe-mode banner, on every
  tab, from first launch until the tools were ready.
* `MainActivity.kt` — the strip's six supporting values, the **first-run divert** to the
  Terminal, and the welcome's hand-over effect.

## 2. What was removed, and why

| Removed | Why |
|---|---|
| `SetupBar.kt` (deleted) | the roadmap's **Remove** row: “the setup bar as a permanent strip”. A bar over every tab is chrome the user did not ask for, and the shots show none. |
| `SetupGatePolicy.barText` / `barVisible` / `barDismissAllowed` (deleted) | they existed *only* to describe the strip (its sentences, its visibility, its ✕). Leaving them would leave a later agent a ready-made bar to rebuild. `SetupState.kt` now carries a **“Do not re-add the strip.”** block where they were: the owner's row, what replaced them, and the device-round-1 reports they used to answer — answered now at the point of use. |
| the first-run **divert** (`LaunchedEffect(setupLaunchDivert)` → `navigate(Screen.Terminal…)`) | §58's exit says the first open is the editor and **“not a locked terminal”**. Nothing marches the user to the Terminal behind their back any more. |
| the welcome's hand-over effect (`LaunchedEffect(setupDiverted, …)`) | the welcome is retired by 58.1; the sample is opened by the launch itself, so there is nothing left to hand over. |

**Kept, deliberately:**

* `setupLaunchDivert` — the same disk read (`startOnTerminal(userlandUsable(prefix, phase),
  archName != null)`), but it no longer diverts. It survives because it still *means*
  something to the resume decision: `ResumePolicy.setupNeedsWatching` → an uninstalled phone
  owes no resume card.
* `SetupNoticeBridge`, `setupProgress`, `setupFacts` — the Terminal's own truth (its intro
  card and chip quote them) and the installers' repair note. Retiring the strip is not
  retiring the state.
* `SetupGatePolicy.dontCloseText` — the Terminal's chip while an install it can see is in
  flight. The roadmap's “no *hang tight*, no *don't close*” is about what the app volunteers
  over the work, which 58.2 removed; the Terminal is a place the user chose to visit, and its
  chip describes what is running there.
* `SetupGatePolicy.refusal(...)` — including its “don't close the app” line for a `pkg`
  transaction requested while one is already streaming (the Packages tab's own refusal). That
  is a point-of-use answer to an action the user just took, not a standing nag; changing that
  copy is a Packages-surface decision (55/56), not this part's, and the phase's Remove row
  names the strip.

## 3. What replaced it — one sentence, at the point of use

`RunDecision.NeedsInstall` is reached only when a run needs a package. The gate that already
existed for that moment — `EditorViewModel.promptInstall`, the 'Install <tool>?' sheet — now
first asks the same question `confirmInstall` obeys:

```kotlin
val allowed = SetupGatePolicy.can(
    SetupAction.INSTALL_PACKAGE,
    SetupStateBridge.factsOrDisk(prefixDir, ledger.phase)   // the disk, not a flag
).allowed
NoticePolicy.userlandWarning(allowed)?.let { noticeFor(it); return }   // one pill, one sentence
```

* `NoticePolicy.userlandWarning(packageInstallAllowed)` — the pure decision (null when the
  setup can take the download).
* `NoticeKind.USERLAND_NOT_READY` — the third and last pill, rendered by the editor's existing
  `PillNotice`: amber warning triangle, the app's own tone (the file is fine; the tool is not
  installed yet). One line, from `strings.xml`:
  **“Finish installing the Linux tools first — open Terminal.”**
* No dialog, no dump, nothing to watch: the pill takes itself away on the existing 2.4 s timer,
  and the run stops there. The user has the whole editor, the whole app, and a sentence telling
  them the one thing that unblocks them.
* **Ordering matters:** because the pill is the run gate's answer, the editor can never show
  the install sheet for a download the setup cannot carry — the pill and the refusal can never
  disagree.

`C` and the HTML preview never wait, and that is now pinned rather than asserted:
`LanguageRunPlanner.decide` returns `WebPreview` **before** any tool probe, and the C profile
keeps `requiredPackage = null` (TCC ships in the APK).

## 4. Pins re-cut (the product changed; the pins say why)

| Test | Before | After |
|---|---|---|
| `SetupGateWiringTest` “the setup bar is always actionable…” | the strip's ✕/VIEW rules | **the removal**: the file is gone, the three policy functions are gone, `SetupState.kt` says “Do not re-add the strip”, no `SetupBar`/`setupBarText`/`dismissedSetupBar` in the shell's code |
| `SetupGateWiringTest` “the setup bar is mounted above the NavHost…” | ordering of bar vs NavHost | the shell keeps the truth (`setupProgress`/`setupFacts`/`SetupNoticeBridge.post`, the facts reach the screens) and mounts **no** setup surface between the banner and the NavHost |
| `SetupGateWiringTest` “a fresh install opens the terminal first…” | the divert flag and its navigate | the **reversal**: no `setupDiverted`, no “wait until settled”, the disk read kept for `ResumePolicy`, and the first open on the sample |
| `SetupGateWiringTest` “every go-to-the-terminal navigation…” | at least **four** navigations | at least **three** — the divert was one of the four; the rest are the bottom bar, a project's open-in-terminal and the Packages door |
| `SetupGateWiringTest` “a cold start is diverted…” | `remember(launchState)`, the `stuck` guard | `remember(launchState, firstOpenSample)`, no divert, `setupNeedsWatching` kept |
| `SetupGateWiringTest` **new** “the one warning a run owes is the editor's pill…” | — | the gate lives in `promptInstall`, posts a notice (not a dialog), orders `userlandWarning` before the sheet, the pill renders the kind, the string exists, no “hang tight”/“don't close” copy, `WebPreview` before the probe, C never gated |
| `GuideWiringTest` “phase 44's setup surfaces are untouched by the guide” | ordered `GuideScreen(` before the bar mount | the disk read + `GuideScreen(` stay, and the shell mounts no bar |
| `SetupGatePolicyTest` “the bar is silent only when the setup is ready and usable” | `barText`/`barVisible` | the same fact at its point of use: `can(INSTALL_PACKAGE, …)` allows only a ready, usable setup, refuses every other stage **with a sentence**, and still allows `RUN_C` |
| `SetupGatePolicyTest` “the settled-but-unusable bar text is the one the owner quoted…” | the bar's sentence | the refusal names the Terminal for the same state |
| `SetupGatePolicyTest` “a working prefix with a stale swapping ledger…” | the bar's sentence | the refusal names the Terminal |
| `SetupGatePolicyTest` “the bar is dismissible exactly when the setup has settled” | **deleted** | its subject no longer exists; a comment where it was explains that `InstallProgress.settled` still keeps the Terminal chip honest, and that the device-round-1 report is answered by the gates |
| `SetupGatePolicyTest` “the do-not-close bar covers every stage…” | renamed | “the terminal's do-not-close line…” — the sentence outlived the bar on the Terminal's chip |
| `NoticePolicyTest` **new** “a run that needs a download says so only when the tools cannot take it” | — | `userlandWarning(false) == USERLAND_NOT_READY`, `userlandWarning(true) == null` |
| `PillNoticeWiringTest` “both messages exist…” | two strings | **three** kinds, including `notice_userland_not_ready`, and the pill renders the third |

## 5. Test log

| Run | Result |
|---|---|
| `NoticePolicyTest` + `SetupGatePolicyTest` + `SetupGateWiringTest` + `PillNoticeWiringTest` (host harness) | **70 passed, 0 failed** |
| the phase-57 set (`EditorChromeSlot`, `EditorRows`, `Haptic`, `RunButtonStyle`, `TouchTarget`, `PillNoticeWiring`, `NoticePolicy`) | **50 passed, 0 failed** |

Two real pin failures were caught locally **before** the push and fixed with their reasons:
the terminal-navigation census (4 → 3, above) and a `before()` anchor that matched a
`WebPreview` occurrence outside `decide()` — the pin now slices `decide()` first.

## 6. What this part does **not** decide

* The install still starts when the Terminal starts (unchanged), and nothing new starts it in
  the background at first launch. A fresh install therefore reaches the pill's case honestly:
  run something that needs a package before visiting the Terminal, and the sentence says where
  to go. Inventing a background auto-install at first launch is not in the roadmap's Add row.
* No new surface was added for the setup: the sentence rides the pill the shots already show.
