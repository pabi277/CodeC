# prompt.md — paste this into the next chat

> Copy **everything between the two `---` lines below** as the first message of
> a new chat. It forces the next agent to verify before acting, to trust the
> repo over its own assumptions, and to continue CodeC without redoing or
> breaking anything.

---

Read `docs/JOURNEY.md`, `docs/NEXT_STEPS.md`, and **`rule.md`** first, before
doing anything else, then report what you found and the current git/PR/CI
state before making any change.

You are continuing **CodeC** (an Android C / multi-language IDE with its own
Termux-style terminal + signed package repo). Each chat session gets its own
`arena/*` session branch — verify with `git status`; commit and push to the
SESSION branch only, never `main` or any other branch. **`rule.md` is the
operating manual for all work after Phase 18** (branching, lifecycle, merge
gate, invariants, docs policy) — follow it.

**PHASE 45 IS 🚧 IMPLEMENTED, CI ✅ GREEN ROUND 1 (`34698914219`, tip `3c597b2`), DEVICE ROUND NOT RUN (2026-09-12, `arena/01a0955a-codec`; owner: "Start Phase 45").** The owner's row 2 — *"It has 0 guide features to give the user a real knowledge how to use the app, user don't know where should they change the project or file and the tap to the open down side of the keyboard"* → *"Set a step by step user guide after opening the app 1st time with a open view again[ing]"*, clarified as **"both layers"** — is now code, in the house shape (pure plan + thin Android edge, host-tested, no new dependency/permission/route). **45.1 (slides):** `ui/guide/GuidePlan.kt` — five `GuideSlide`s in the order the user meets the features (☰/files → RUN ▶ → the one-time download → the Terminal tab → Projects vs single files), caps title 34 / body 22 words / 130 chars, `at/canSkip/next/isLast/isDone/resume/progress/wordCount`, and **`GuideVocabulary`**: `candidateTerms` extracts what a slide *names* (control symbols ☰ ▶ ⋮, ALL-CAPS words, capitalised words that are not their sentence's first word, `backticked` spans) and every extracted term needs a `GuideTermProof(term, path, needle)` that `GuidePlanTest` checks against the REAL repo tree (`RepoFiles`), with three `commandProofs` (`pkg install`, `git`, `cc`) pinned in both directions — so the guide cannot promise a control that moved. `ui/guide/GuideScreen.kt` renders it (plain `Column`, SKIP on *every* slide, a fifth-per-slide progress bar, GOT IT → START CODING, back = SKIP, index surviving rotation/process death via `rememberSaveable` + `resume`) as the **second** first-launch gate — tiles → guide → shell — decided **before** Phase 44's `setupLaunchDivert` and the `NavHost`, so it can never share a screen with the setup bar and slide 3 explains the download the terminal is about to show. `guide_completed` (default **false**, so an upgrader sees it exactly once) is written in ONE place (the guide's `onFinished`, which SKIP/START CODING/back all call) and read once at startup, so Settings → **Reset tips** affects the *next* launch instead of yanking the user out of Settings. The owner's *"open view again[ing]"* is three doors on one local flag: Settings → About → **Help & guide** (audit row 53), Projects hub ⋮ → **Guide** (both hub states), editor ☰ drawer footer → **Guide**; it is **not** a nav route (a route would put the guide in the back stack and hand it to Phase 49's `BackRouter`), and the `NavController` is remembered above the gate so closing it lands back on the same tab. Safe mode skips the gate **and does not write the flag**. **45.2 (coach marks):** `ui/guide/CoachMarkPlan.kt` — five `CoachStep`s over `GuideSurface {EDITOR, TERMINAL, PACKAGES}` keyed by `GuideAnchors` (editor ☰, RUN ▶, the "Show tabs" handle, the Packages first card, the terminal status chip), `ChromeState.of(visibleAnchors, blockedByForeground)`, `stepsFor/nextUnseen/canShow/markSeen/parseSeen/serializeSeen/surfaceForRoute/stepForArrival`, `MAX_PER_ARRIVAL = 2`, plus `TooltipPlacement.place` over pure `GuideRect`/`GuideSize`. `ui/guide/CoachMarks.kt` is the edge: `GuideAnchorRegistry` (process-wide snapshot-state bridge, the codebase's `SetupNoticeBridge`/`EditorChromeState` idiom), `GuideAnchor.modifier(id)` publishing `boundsInWindow()` **and withdrawing the id in `DisposableEffect.onDispose`**, `GuideCoachMarks` (chrome → plan → per-arrival counter, reset on destination change), `CoachMarkOverlay` (scrim with a hole drawn as **four rects + a rounded stroke** — no `BlendMode.Clear`, so no offscreen layer — and a card placed by the pure function with an *estimated* 150 dp height). **The law that makes it safe:** `nextUnseen` only returns a step whose anchor the caller reports visible, and an all-hidden surface yields no step *and* no `seen` entry — so the "Show tabs" mark (the owner's *"the tap to the open down side of the keyboard"*) can never point at a handle that is not there, and it lands on the first arrival where the keyboard really hid the bar. **Never a trap:** the `Canvas` takes no pointer input (the control under the hole keeps working), and one `awaitFirstDown(requireUnconsumed = false)` consumes only OUTSIDE the hole, so tapping the highlighted control performs its own action *and* closes the mark; GOT IT and `BackHandler` are the other exits. The overlay is composed in a root `Box` **above** the `Scaffold` because the handle lives in `bottomBar`. Marks are blocked by the exit survey, safe mode, and a Phase 44 stage that is *actually moving* (DOWNLOADING/VERIFYING/EXTRACTING — **not** CHECKING, the tracker's startup value, or no mark would ever appear on a fresh phone); a screen's own dialogs/sheets are separate windows that cover the overlay and do not consume the pending step. `coach_marks_seen_csv` persists ids (garbage/deleted ids drop out in `parseSeen`); **Reset tips** (audit row 54) clears it *and* the guide flag in one atomic `dataStore.edit` touching no third key. **The vocabulary pin caught the plan's own copy:** slide 5 was specced *"⋮ → Open in editor"* but the hub's real label is `hub_open_action` = **"Open"**, so the shipped slide says *"Tap a card, or its ⋮ → Open, for the whole project."* **50 new host cases** (`GuidePlanTest` 15 · `CoachMarkPlanTest` 12 · `TooltipPlacementTest` 10 · `GuideWiringTest` 13 source pins) = **159 green locally** on the `rule.md` §9 kotlinc harness (one harness finding: a fixed 400-char window around `resetGuideTips` counted the *next* setter's key, so the pin slices to the function's own closing brace); the Compose edges cannot compile in the sandbox at all, so **CI's `Build APK` is the executor of record** and the exit condition is a **device** condition — [`docs/chat-phase45/DEVICE_ROUND.md`](docs/chat-phase45/DEVICE_ROUND.md) G1-G14 written and **NOT run; never describe Phase 45 as tested**. `SettingsAuditTest` stays green by construction (rows 53-54 in `docs/chat-phase38/SETTINGS_AUDIT.md` in the same commit: About 20 controls, 62 total, all `keep`) and `SettingsKeysHaveReadersTest` has real out-of-store readers. Eight deviations recorded in the part docs, not hidden. Records: [`docs/chat-phase45/`](docs/chat-phase45/README.md) (implementation record + both parts' `## Implementation`), JOURNEY §61, root README "The guide (Phase 45)". **CI round 1 ✅ GREEN: `Build APK` `34698914219` on tip `3c597b2` — `conclusion: success`, job `build` 9m55s, zero error annotations; per `rule.md` §5 that is `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, so the 50 new cases ran on real Gradle/JUnit/Robolectric and lint is clean. Artifacts `CodeC-IDE-release` 6,664,474 B (**+12,792 B / +0.19%** over Phase 44 round 4's 6,651,682 B — the whole guide's cost) / `CodeC-IDE-debug` 25,661,856 B (v1.3.17); the only annotations are the repo-wide Node 20 → 24 runner deprecations. Next: the owner's Phase 45 round (G1-G14 on <https://github.com/pabi277/CodeC/actions/runs/34698914219>, artifact `CodeC-IDE-debug`, G1 needs a FRESH install) AND Phase 44's round 2 (R1-R8, then D1-D12) on the same phone, then "Start Phase 46" (Projects, not folders).**

**PHASE 44 IS 🚧 IMPLEMENTED, CI ✅ GREEN (round 4 `34695797493`, tip `4bf3c4c`), DEVICE ROUND 1 🔴 FAILED AND FIXED — ROUND 2 NOT RUN (2026-09-12, `arena/01a0955a-codec`; owner: "Start Phase 44").** The owner's test-phase row 1 — *"Userland is installing but the test user don't know it's installing so they close app before it complete than letter when they try to install any other pkg got errors"* + his own fix *"If it opens the terminal 1st and show a warning don't close the terminal while userland is installi[ng]"* — is now code. **44.1 (visible):** pure `ui/terminal/SetupState.kt` (`SetupProgressParser` over the installer's *existing* progress lines → `SetupGatePolicy.can/refusal/barText/dontCloseText/notificationText/actionForCommand/userlandUsable/diskFacts`, `SetupTracker`, `SetupAnnouncer` = 5 % / 2 s throttle); `ui/components/SetupBar.kt` drawn in `MainActivity` **between `SafeModeBanner` and `NavHost`** so every tab shows `Setting up CodeC's Linux tools — 42 % · C works right now`; `startDestination` = **Terminal on a fresh, unsettled install** with the starter C file auto-opened once setup settles; the owner's `Don't close CodeC — it is finishing a one-time setup (42 %)` row in `TerminalScreen`; the chip via `TerminalStatusLabel.label` (`downloading userland 62 %`, not `starting shell…`); the notification via `TerminalForegroundService.start(context, status, percent)` (**one** channel `codec_terminal`, icon still `ic_stat_codec`, FGS-start failure caught + `AppLogger`'d); the wake lock + FGS are taken **before the first byte** and handed to the session path when a shell starts; `ModulesScreen` refuses a `pkg` transaction with the honest sentence + **VIEW SETUP** (all **four** `sendCommand` sites gated; an already-installed package keeps **RUN**), and `EditorViewModel.confirmInstall` refuses through the same policy via `SetupStateBridge.factsOrDisk`. **C is never gated** — `RUN_C`/`EDIT_FILE` are `Allowed` in every stage. **44.2 (cannot half-finish):** `SetupLedger` (its own SharedPreferences file, **`commit()` never `apply()`**, corrupt/throwing store → `IDLE`, `startedAt` stamped only on the first in-progress write) + pure `resumePlan()` (`RestoreOld/Reextract/RetryDownload/SweepOnly/FreshInstall/Nothing`) + `resumeMessage()`; `SetupRecovery.recover()` on a daemon thread in `MainActivity.onCreate` **next to `TempGc`** (restore the newest `usr.old-*` **only** when `usr` is missing; sweep **only** `usr.old-<digits>` / `.userland-staging-<digits>` directories directly inside `filesDir`, never `projects/`, never a symlink, never anything newer than the repair; always `SetupRecoveryGate.finished()`); `SetupRecoveryGate.awaitFinished()` (5 s) so the installer waits for the boot repair; the post-install marker moved **after** the swap; `userlandUsable` checks the **real** `$PREFIX/bin/pkg` + a shell (exists, `canExecute`, non-zero length) instead of the marker; the offline message names the network (`CodeC needs the network once to finish setting up its Linux tools. C works offline right now.`), never "using built-in cc". **109 host cases / 7 classes** (`SetupGatePolicyTest` 25, `SetupProgressParseTest` 22, `SetupLedgerTest` 13, `SwapRecoveryTest` 16 with real `TemporaryFolder` kill points, `UserlandUsableTest` 9, `SetupGateWiringTest` 20 source pins, `TerminalStatusLabelTest` 4) were green locally on the `rule.md` §9 kotlinc harness — the Compose/JNI edges **cannot compile in-sandbox at all** (no `android.jar`), so **CI `Build APK` is the executor of record** and the exit condition is a **device** condition: `docs/chat-phase44/DEVICE_ROUND.md` (12 rows incl. the three kill points) is written and **NOT run — never describe Phase 44 as tested**. Deviations are recorded in the part docs, not hidden: `OPEN_TERMINAL` is never refused (the Terminal tab is where setup is watched); the planned Robolectric `SetupStateVmTest` was **replaced** by pure `SetupTracker` tests + wiring pins (a Robolectric VM would really spawn a PTY and really install a userland — the §31 flake class); the bar/notice are dismissible **only** when settled *and* usable; `swapPrefix` keeps byte-identical names so `UserlandInstallerTest` stays untouched; the repair is one entry point, not two. No new dependency, DataStore key, Settings control, permission or telemetry; `MANAGE_EXTERNAL_STORAGE` uninvolved. **Two CI rounds taught two lessons, both now runbooks:** round 1 (`34692621773`) — seven `Unresolved reference` errors in `TerminalViewModel` from ONE missing parameter (`ledger` was added to `UserlandInstaller`'s primary constructor but not to the secondary `(Context)` one the ViewModel uses, so the receiver's type was unknown and every member on it looked missing) → *sort a red run's errors by line number and fix the first one; a constructor-signature change goes on EVERY constructor, with every call site grepped by hand* (§33, pinned by a source-scan case); round 2 (`34692963464`) — past the compile and the unit tests, dead in `:app:lintDebug` on two `NewApi` errors in the *pure* `SetupRecovery.kt` (`java.nio.file.Files.isSymbolicLink` is API 26, `minSdk` is 24 — **a file with no Android imports is still linted as Android code**) → replaced by the canonical-name symlink check plus `File.delete()` **before** `deleteRecursively()` (unlink does not follow a link, the recursive walk does), pinned by two `SwapRecoveryTest` cases that create **real symlinks** → **109 host cases** (§34 has the `java.nio.file`/`java.time` replacement table and the pre-push grep; the local harness cannot see `minSdk`, which is exactly why lint caught it). Records: `docs/chat-phase44/` (README implementation record + both CI rounds, both parts' `## Implementation` sections), JOURNEY §60, TROUBLESHOOTING §32/§33/§34, README's "The one-time setup you can see (Phase 44)". **DEVICE ROUND 1 WAS RUN BY THE OWNER (2026-09-12, on the CI-✅ round-3 artifact `ba51382` installed OVER an existing install) AND 🔴 FAILED FOUR ROWS** — *"I couldn't not open the terminal it's opening the editor"* · *"The top a massage 'C works right now. The linux tool need one install- open terminal and tap download'"* · *"But it's not closing or opening terminal"* · *"Every package saying view setup but terminal not opening editor opening"*. Three root causes, all fixed, all now pinned by tests: **(1) A MARKER IS NOT A PREFIX** — `UserlandInstaller.installIfNeeded(force = false)` answers `AlreadyInstalled` from the release **marker** alone (the fast warm-open path), and a *pre-44* build wrote that marker **before** the two-rename swap, so his phone had a valid marker over a prefix with no working `bin/pkg`: stage `READY`, facts *not usable*, the bar's "still need one install" sentence forever and every Packages row refusing. The `AlreadyInstalled` branch now asks the DISK (`SetupGatePolicy.userlandUsable` → `FAILED(BROKEN_USERLAND)`, wording *"Setup didn't finish — the Linux tools aren't working…"*, because "can't start on this device" reads like an unsupported phone), and `refreshSetupFromDiskWhenIdle()` re-reads stage+facts once `SetupRecoveryGate.awaitFinished()` returns so a stale ledger cannot pin the bar. **(2) THE BAR WAS A WALL IN EXACTLY THAT STATE** — its action button rendered only `if (inFlight || stage == FAILED)` and its ✕ called the *note* dismiss, which cleared a note that was not there. Now: the whole bar is one tap to the Terminal tab, **VIEW SETUP** always renders, and the ✕ is real (`SetupGatePolicy.barDismissAllowed(progress)` = `progress.settled`; MainActivity remembers the dismissed TEXT, so a changed state brings the bar back). **(3) `restoreState = true` RESTORES A WHOLE SAVED SUB-STACK, NOT A TAB** — `navigate(terminal) { popUpTo(start) { saveState = true }; restoreState = true }` put back a stack whose top was an editor the user had opened above the terminal, so "go to the terminal" arrived at the editor. Every navigation that means *show me the terminal* (setup bar, Packages' VIEW SETUP, the editor's terminal hand-off, the launch divert, the bottom bar's Terminal tab) is now `restoreState = false`; the other four tabs keep pre-44 behaviour and **Phase 49 remains the systematic nav pass**. The launch-tab gap was its own fault: the divert was keyed on the first-run welcome handing over, so an **updated** install never got "terminal first" — it is now `SetupGatePolicy.startOnTerminal(usable, abiSupported)` decided **synchronously from the disk** (`userlandUsable(prefix, ledgerPhase)` + `UserlandManifest.archName() != null`) and applied with a `navigate()` after the first composition, never by putting an argument-carrying route into `startDestination` (graph-construction risk for no gain). **Deliberately NOT done:** an automatic re-download for the marker-only state (~40 MB on possibly mobile data; this phase's law is *no surprises*) — it is one call (`installUserland(force = true)`) away if the owner asks. **109 host cases** green locally (`SetupGatePolicyTest` 25 — incl. the launch rule, the dismiss rule, the exact sentence the owner quoted; `SetupGateWiringTest` 20 — incl. "the setup bar is always actionable and its close button really closes it", "every go-to-the-terminal navigation arrives at the terminal", "an install marker alone is never accepted as ready", "a cold start is diverted to the terminal from the disk, not from a flag"). Owner-facing: TROUBLESHOOTING §35; re-test rows **R1-R8** then D1-D12 in `docs/chat-phase44/DEVICE_ROUND.md`. **CI ROUND 4 (`34695797493`, tip `4bf3c4c`) is ✅ GREEN** — `conclusion: success`, job `build` 10m53s, zero error annotations, artifacts `CodeC-IDE-release` 6,651,682 B / `CodeC-IDE-debug` 25,608,360 B (v1.3.17). Per `rule.md` §5 the green run means `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` all passed, so the **109 host cases ran on real Gradle/JUnit/Robolectric**, not only on the local kotlinc harness. (The sandbox could not download the run log — `results-receiver.actions.githubusercontent.com` answered EOF twice — so the evidence is the conclusion plus the empty error-annotation list.) **Next: the owner's round 2 (R1-R8 on <https://github.com/pabi277/CodeC/actions/runs/34695797493>, artifact `CodeC-IDE-debug`, installed OVER the current install — no data wipe), then D1-D12 on a fresh install, then "Start Phase 45" (the guide).**

**PHASE 40 IS DONE, DEVICE-PASSED AND MERGED (2026-09-10) — READ ITS CAUTIONARY TALE ANYWAY.** The owner's earlier Phase 40 session (`arena/01a08b68-codec`) pushed **16 red `Build APK` runs in two hours**, every one a plain Kotlin syntax/type error in the new files — no local compile, and the CI annotations were never read, so the same errors repeated. The evidence table, root causes and repair rules are in
[`docs/chat-phase40/PART_40_4_CI_LOOP_DIAGNOSIS.md`](docs/chat-phase40/PART_40_4_CI_LOOP_DIAGNOSIS.md); read them before any GitHub work. Two helpers exist for exactly that failure mode: `scripts/ci_annotations.py` (read a red run's annotations from the sandbox) and the jdk4py + kotlinc pre-validation loop (`rule.md` §9). **This session's branch never pushed a red run**: 40.1–40.3 (readiness + errors-in-the-window, push truth from git's own bytes, Publish to GitHub) were pre-validated on a host JVM (36/36 cases, four real bugs caught before CI), and the device round passed all 8 checks (owner: *"All passed in device"*, runbook `docs/chat-phase40/DEVICE_TEST_PLAN.md`).

**PHASE 40.5 — THE COLOUR LAW (owner: *"research throughly on the color of the app's inside texts … Now it is violet 💜 but not very good to read. Also correct other colors"*, then *"Make the green as default"*).** Root cause of the reported violet: the accent was pushed **raw into `colorScheme.primary` for both themes** — 7.44:1 in light, **2.25:1 as dark-theme text** where WCAG 2.2 AA wants 4.5:1 (M3 solves this with tone roles: primary = tone 40 light / tone 80 dark). Standing rules that came out of it, all pinned by tests (`AppContrastTest` 16 + `ChromeContrastTest` 7 — the chrome test reads the alphas **out of the UI sources**, so raising one fails the build; both run in CI via the `gradle-bootstrap` bridge):

1. **The accent is a role, not a hex.** Use `AccentPalette.rolesFor(seed, dark, surface)` — hue kept, lightness moved only until it clears AA, `onPrimary`/`onContainer` **measured**. `CodecPalette.DEFAULT_ACCENT` is **CodeC green `#3DDC84`** and `AccentPalette.DEFAULT_STORAGE_HEX` is the single spelling of it; a **stored** accent is never rewritten.
2. **A colour drawn on a translucent surface must be derived from that surface**, not from the base: composite the layers (`Contrast.composite`) and correct against the result (`Contrast.ensureReadable`). The editor status bar and the key caps do this; nine other places used to assume a dark background.
3. **No new `Color(0x…)` literal for text or icons** — take the token from `CodecPalette` (each carries its measured ratio in KDoc) or derive it, and if you add one, add its case to `ChromeContrastTest`/`AppContrastTest`.
4. Values that already pass are **not** restyled, disabled controls and decorations stay dim (WCAG §1.4.3 exemptions), and no colour is ever "corrected" for the sake of it in a theme where it already passes.

Full dossier: [`docs/chat-phase40/PART_40_5_COLOUR_REPAIR.md`](docs/chat-phase40/PART_40_5_COLOUR_REPAIR.md); owner-facing version: TROUBLESHOOTING §29.

**PHASES 38-43 ARE PLANNED, NOT STARTED (2026-09-10, docs-only).** The owner's
six "before I share the app" ideas (git errors invisible + push that stays local;
"open a folder" crashing + no open-project-from-folder; output/temp files
landing in commits + `.codec` files; a feedback page reachable on WhatsApp; drop
the Termux bridge row from Settings; set an app icon — plus *"what should I add
more before sharing the app"*) became six phases, and on the owner's follow-up
(**"Rename the phases so i can continue 38 to 43"**) the numbering was set so
that **the numbers are the order of work**: **38** Identity (original adaptive
launcher mark + real `monochrome` layer + notification icon, and the Termux
Engine card out of Settings with `TermuxCompiler`'s fallback kept), **39**
Outputs are temporary (run artifacts under `CodeC/temp` + `TempGc`, and
`RepoHygiene`'s ~60 patterns incl. `.codec/` enforced inside `stageAll`, the
user's own `.gitignore` always winning), **40** GitHub that tells the truth
(readiness gate with the error *inside* the dialog, push/branch truth as a
result card, publish via `POST /user/repos`), **41** Feedback that reaches you
(`FeedbackDraft` → `wa.me`, number kept as a Settings value, no telemetry),
**42** Share-readiness (release signing + `app-v*` tags + a truthful updater,
measured weight, real backup rules, crash-loop guard, export-all, permission
list that matches reality) and **43** File system strength (bounded
`Throwable`-safe folder walk, then open-folder-as-project with a *persisted* SAF
grant). Work straight down the list — the one dependency inside it is **39.1
before 43.2**. Old→new map for anything quoting the earlier numbers: 42→38,
40→39, 38→40, 41→41, 43→42, 39→43. Read `docs/PHASE38_43_ROADMAP.md` and
`docs/PHASE38_43_OSS_RESEARCH.md` (what was rejected with reasons: JGit,
`MANAGE_EXTERNAL_STORAGE` as a dependency, shipping gitignore *files*, Firebase
Crashlytics, a Telegram bot token in the APK, Play while `targetSdk = 28`).
**No app code exists for any of them. Both the plan docs and Phase 37 are now on
`main`** — the owner's "Merge everything" (2026-09-10) closed the gate: **PR #62 →
`main` at `e89dc49`**, branch tip `c054270` with CI ✅ (`Build APK` `34435610648`).
So the next action is `Start Phase 38`, not another merge. Start one only on "Start Phase N";
every part doc has its own device exit condition for the owner. JOURNEY §52
records the round, including the ten things reading the tree corrected (there is
**no** userland tarball in the APK — it is downloaded and SHA-verified by
`UserlandInstaller`; `libtcc.so` covers **2 of the 4** declared ABIs, so armv7
has no built-in C compiler by design; `GitRedactor` only scrubs the literal token
it is handed; the backup XMLs are untouched Android Studio samples, so
`files/usr` **and** the `git_token` DataStore are backup-eligible).

**WHERE THINGS STAND (2026-09-10, Phase 37 device-as-server LAN ✅ DEVICE-PASSED on `arena/01a0872e-codec` (owner: "Start Phase 37" → the two-device round answered "All pass"; that round's 🌐 open-in-browser follow-up is implemented on the same branch) — 37.1 opt-in LAN bind + `http://<lan-ip>:<port>` + copy + ZXing `core` (Apache-2.0) QR in the Output Panel and the preview bar; 37.2 `ServerRegistry` + `ServerHost` as the one owners of port and process truth, keep-alive on the SAME `RunForegroundService` with `Serving <project> on <ip>:<port>` + Stop; loopback stays the default and keeps dying with the screen; no port < 1024; 62 new host cases, local pre-validation 96/96 over the real production files. **`Build APK` CI is ✅ GREEN on tip `6d36a83` (run `34393543928`: assemble + unit tests + lint; APK +355 660 B).** **The two-device round is DONE: owner "All pass" on all eight exit checks (2026-09-10)**, recorded as the owner's report; its one follow-up is now shipped — **🌐 opens each share row in the phone's default browser** (`On this device` → loopback URL, `Other devices` → LAN URL; pure `ui/services/ShareActions.kt` + the app's only `ACTION_VIEW` launcher `ui/services/OpenInBrowser.kt`, which the terminal's link handler now reuses; no button without a real `http(s)://` URL; if no browser can serve it the link is copied and said so; copy + 👁 unchanged), `ShareActionsTest` (6) → **68 host cases in ten classes**, service-set re-run 86/86. **CI on the follow-up commit is ✅ GREEN (`Build APK` `34398031696` on tip `98cb2b4`, APK 24 847 792 B). ✅ MERGED to `main` via PR #62 at `e89dc49` on the owner's "Merge everything" (2026-09-10); the branch tip `c054270` was ✅ GREEN (`34435610648`, APK 24 847 836 B = +1.47 % vs the `main` it replaced).** Do not redo Phase 37; extend it from the device report. Before that: Phase 35 editor typing feel ✅ DEVICE-PASSED by owner report on `arena/01a086a0-codec`; implementation `317b89a`, docs `88839cd`, Build APK CI `34367008019`/`34367770583` ✅ GREEN; exact device evidence was not supplied. Phase 36 terminal speed & UX ✅ DEVICE-PASSED by owner report on this branch; progressive output, background survival, and session-switch follow-ups passed after `da126cf`/`11fe8d7`, with green CI `34374983032`/`34375710614`; ✅ MERGED to `main` via PR #60 at `373a51e8f027bcf08fc948b8dc2058c3bda8c566`. Phase 34 file icons ✅ DEVICE-PASSED on `arena/01a08653-codec` + owner "Device test pass" + MERGED to `main` via PR on owner's command. Before that: Phase 33.1–33.3 first-hour UX ✅ DEVICE-PASSED on `arena/01a085e0-codec` — CI `34346424311` ✅ GREEN first try (tip `5db9e33`) + owner "Device test pass"; **merge HELD** on the owner's command while the UX/UI phases 36–37 are queued; 33.1 = first-run welcome with three starter tiles (C / Python / HTML, DataStore `first_launch_complete` flag, pure `ui/projects/WelcomeStarters.kt` + `ui/screens/WelcomeScreen.kt`); 33.2 = Packages hub opens on "Languages & IntelliSense" with "Unix tools" collapsed (pure `PackageSection` + `PackageCatalog.sectionOf`); 33.3 = identity copy (README / About / empty Projects). **The owner's four new UX/UI row ideas became phases 34–37 (researched + specced; 34 is merged, 35 is device-passed on this session branch, and 36 is device-passed with merge authorized while 37 is queued; `docs/PHASE34_37_ROADMAP.md` + `docs/chat-phase34/…37/`):** 34 = official file icons (VS Code Seti, MIT-vendored, now ✅ DEVICE-PASSED and MERGED); 35 = editor typing feel (35.1 always-keyboard-stay-open · 35.2 smooth typing · 35.3 non-bouncy cursor · 35.4 no caret on open until first tap); 36 = terminal speed & UX (36.1 cold-start latency · 36.2 behavior polish); 37 = device-as-server on the LAN (37.1 bind 0.0.0.0 + LAN URL/QR · 37.2 foreground keep-alive + port lifecycle). They start one at a time on **"Start Phase N"**, then the owner runs the cross-device round. **Open-source-first research done (owner: "research everything on open source 1st for reference and update if anything useful found", 2026-09-09): `docs/PHASE34_37_OSS_RESEARCH.md`** — Seti (MIT) + Compose `PathParser` for 34's icons; sora public API + the in-tree typing path for 35 (no new dependency); jackpal (Apache-2.0) as the terminal reference / Termux (GPL-3.0) behavior-only for 36; NanoHTTPD (BSD-3) reference + ZXing `core` (Apache-2.0) QR + framework NSD for 37. Before that, Phase 32 (phone canvas) ✅ DEVICE-PASSED + Phase 33.0 RUN ▶ chooser ✅ DEVICE-PASSED, both on `arena/01a083fc-codec`, MERGED to `main` via PR #57 on the owner's command ("Then merge", 2026-09-09); owner started 32 with "Start phase 32"): 32.1 auto-hides the 5-tab bar while the editor is focused AND Keys/IME is up, with a thin tap-or-swipe handle to reveal it (pure `ui/editor/NavBarPolicy.kt` + `ui/editor/EditorChromeState.kt` bridge; `MainActivity` Scaffold `bottomBar` is now a `when`; `EditorScreen` reports `codecKeysUp`); 32.2 needed NO code — 28.2 (`suppressKeysVariant`) + 28.3 (chips as row 0) already give the one-meaning-row and 32.1 removes the nav; 32.3 makes a diagnostic tap land in the USER's file — `ui/editor/OutputDiagnosticTarget.kt` falls back to the active file for `source_<stamp>.c` / non-resolvable names (`EditorViewModel.openOutputDiagnosticFile`). Tests `NavBarPolicyTest` + `OutputDiagnosticTargetTest`; CI `34305070875` ✅ GREEN first try (tip `b5feb55`); **device round `TROUBLESHOOTING.md` §16 ✅ PASSED** (owner: "All test passed on device"). **Phase 33 first item (owner, final shape — "make the default run option as a user task if user set any default file than it will open with a option default or current file", with the `Code-with-C` example):** the default run file is the USER-set launch default (`ProjectConfig.launchDefault`), NOT the project entry. RUN ▶ runs the open file by its own type (`EditorScreen.runOpenFile`: HTML → preview; runnable source → run in the panel even inside a `web` project; else web entry → preview); when a launch default is set and differs from the open file, RUN asks "Run default / Run open" (pure `ui/projects/ProjectRunTarget.chooserDefault`/`isRunTarget`/`isRunnableSource`). The ⋮ "Set as launch default" item now accepts any run target (was HTML-only); `webDefaultEntryOrNull` only uses the launch default when it is HTML. `EditorViewModel.runFile(context, target)` runs the chosen file without switching tabs (target-aware registry decision, dirty background-tab flush, diagnostics attributed to the target's basename, install gate resumes the same target). **Owner bug (2026-09-09, "still index.html opening not the file i am in"): cloned/imported projects are type `auto` and `ProjectRunDetector` let a root `index.html` shadow an open `.c` file — `detect` now returns `AutoRunPlan.Project(c|python)` for any runnable active source (`ProjectRunTarget.isRunnableSource`), so it falls through to the registry run.** Tests `ProjectRunTargetTest` (12), `ProjectRunDetectorTest` (+2). CI `34307172630`/`34309463999` ✅ GREEN (tips `788ba46`/`9a11382`); auto-project fix CI `34317268507` ✅ GREEN first try (tip `270c70d`). **Owner bug (2026-09-09, `tcc: error: file '…/Code-with-C/C' not found`): a source path with a space was word-split by the unquoted `$converted` inside the `cc` frontend — `ShellEnvironment.ccScript()` now rebuilds its args via `set -- "$@" "$arg"` and passes `"$@"` to TCC (each arg its own word); outer `shellEscape` (planFor/TerminalHandoff) was already correct; `ShellBootstrap.prepare` rewrites `cc` every RUN.** CI `34321154191` ✅ GREEN first try (tip `fa34750`). **Owner follow-up (2026-09-09, "Every file have a main or a index not necessary if a file even if a file is self contains with something another name"): practice `.c` files are self-contained with an entry named anything, not `main` — pure `ui/services/CEntryWrapper.kt` (`hasMain`, `singleEntry`, `wrapperFor`, `write`) now compiles a single C file with no `main` and exactly one function through a generated `#include` wrapper + `int main(){ <entry>(); }` written under the app cache, so `C Programming/01_…_conversion.c` runs `program01()` directly (CI `34330372322` ✅ GREEN first try, tip `36e4fda`).** **Device round (2026-09-09): ✅ PASSED — owner "Ok working as i wanted"; owner then commanded the merge ("Then merge") → PR #57.** `TROUBLESHOOTING.md` §17–23, `PART_33_0_RUN_CHOOSER.md`. Before that: Phase 31 ✅ MERGED to `main` (2026-09-08) on owner "merge it"; CI `34210108408` GREEN tip `278099e`; device recipe §15 not run; sora `editor-lsp` AAR gated. Before that: `main` tip = PR #55 (Phase 30 merged); PHASE 30 (Offline completeness — MIT snippet packs + clean-room Emmet + strip capacity) ✅ COMPLETE, DEVICE-PASSED & MERGED to `main` via PR #55 (owner: "Start phase 30" → "If all done then merge it", 2026-09-07); all three parts in one build; 91 new host tests + 6 new cases, plus 3 more from the device round (`CodeCompletionTest` 19, `SmartTypingTest` 13) — **124 green locally** over the real production files; **CI: `34034889209` GREEN first try → one for-cause round (`34040754444`, a hand-counted caret literal in a new test) → `34041185149` GREEN → the device round's `34077539890` RED on the known sora/Robolectric flake (`EditorLaunchMeasureReproTest` → `IllegalThreadStateException` in sora's unsynchronized `AsyncIncrementalAnalyzeManager.rerun`) → `c2b392e` tolerates exactly that one signature → `34078739941` GREEN (tip `c2b392e`, 8m33s); APK artifact `CodeC-IDE` 24 375 211 B = +117 409 B (+0.11 MiB) vs `main`**; **device round 1 (`docs/TROUBLESHOOTING.md` §13, build `ca8ec57`) reported TWO bugs — accepting a suggestion kept the typed prefix (`#in` + tap → `##include <stdio.h>`) and CodeC Keys closed no brackets — both fixed in `d63a645`, both re-measured on a host JVM, both device-confirmed PASSED via §14 (owner: "Yes working", 2026-09-07)**; records in `docs/chat-phase30/` + JOURNEY §41 + `docs/TROUBLESHOOTING.md` §13/§14. Before that: Phase 28.2 MERGED via PR #52; PHASE 29 (VS Code colour / TextMate) 🚧 IMPLEMENTED on the session branch — owner: "Start phase 29"; CI green; device round 1 hit two crashes, BOTH FIXED — crash 1 CME in sora theme dispatch (`3fb404f`); crash 2 `IllegalStateException: LayoutNode should be attached to an owner` = Compose 1.7.1 detached-node-during-nav-transition family, fixed by `composeBom 2024.12.01` (1.7.6) — and the CI NavHost-transition repro test then caught the deeper VM→sora replay bug (incremental delete-all into sora's async-rebuilt layout), fixed by an atomic `setText` replay (`db56824`); crash-log now reports header-first (COPY ALL = complete record); BOTH crashes device-confirmed fixed; **device round 1 PASSED 2026-09-06 — checklist ALL PASS + APK-size deviation (+2.22 MB) ACCEPTED by the owner; MERGED to main via PR #54 (owner: "Merge it", 2026-09-06)**; records in `docs/chat-phase29/`):**

- **Phase 30 (all three parts in one build) — WHAT is offered, not how it is
  accepted.** 30.1: the four hand-written snippet tables (7 C / 9 Python /
  8 HTML / 3 CSS) are now **29 vendored MIT friendly-snippets packs** in
  `assets/snippets/` (277 KB raw ≈ 54 KB in the APK, pinned to upstream
  `6cd7280`, notice in `assets/licenses/FRIENDLY_SNIPPETS_MIT.txt` + About
  line, re-vendorable via `scripts/vendor_snippets.py`) resolved by five new
  pure files in `ui/editor/snippets/` (`SnippetJson` strict reader ·
  `SnippetSyntax` VS Code resolver: tabstops/mirrors/choices/`TM_*` vars/
  regex transforms + case ops + `:+ :- :?` conditionals/bounds ·
  `SnippetPacks` entry→items, ≤3 prefixes, first-wins dedupe, ≤64-char detail ·
  `SnippetAssets` LanguageType→packs, JSON/TEXT/XML/YAML none ·
  `SnippetLibrary` attach-by-AssetManager-identity + two cache layers +
  warm-up + degradation) = **84 C / 76 Py / 126 HTML / 156 CSS / 367 JS /
  140 TS / 62 MD / 16 sh** items; the built-in tables ride
  along as a **deduped TAIL** after the pack (and are the whole list when no
  pack loads) — they carry the 22.6 DOCTYPE skeleton, the app-private shebang
  and the descriptive labels a prefix-only pack cannot reach (`head` →
  `# Heading`, `pr` → `print(...)`); matching stays case-insensitive (22.6
  law); identifiers still rank below snippets; the empty-prefix trigger path
  excludes an item whose INSERT TEXT is the trigger (not whose label is — pack
  labels ARE trigger words).
  30.2: **clean-room `ui/editor/Emmet.kt`** (859 LOC, no dependency — rule.md
  §6) at **rank 0** of the same pipeline: markup (`!`/`html:5`, `> + ^ *n
  ( )`, `.class` `#id` `[attr]` `{text}`, `$`/`$$` numbering with counter
  propagation, implicit tags, 16 void elements, `/` + JSX self-close) and CSS
  (82 abbreviations longest-match, 14 unit suffixes, `-` separators vs
  negatives, `!important`, `+` chains ≤6, keyword tables, caret after `": "`),
  with guards that REFUSE rather than guess (structural signal required; no
  firing inside a tag/attribute/comment/string/selector; `.jsx`/`.tsx` only
  for JSX; plain `.js` and **C never fire**), bounds (120 chars / 100 repeats /
  400 nodes / depth 24) and `finish()` re-indenting CONTINUATION lines onto
  the caret's base indent. 30.3: `MAX_ITEMS` 8 → **50** (snippets ≤40,
  identifiers ≤6, keywords ≤6, tier order unchanged), `MAX_CHIPS` stays **8**
  (⌄ more = the rest, 27.2), ghost still rank 0; `CompletionItem` gained
  nullable `replaceLength`/`caretOffset` (null = the 27.x shape) honoured by
  the VM accept + ghost FULL, ignored by WORD/LINE. **`CompletionPolicy.kt` is
  NOT in the diff** — Enter sacred, master switch, no auto-commit — plus ONE
  narrow test-pinned S1 exception (a lone Emmet candidate chips, because a
  ghost cannot cover an expansion). Tests: `SnippetSyntaxTest` 21 ·
  `SnippetPacksTest` 12 · `EmmetTest` 24 · `SnippetLibraryTest` 15
  (Robolectric, real assets) · `CompletionCapacityTest` 19 (host mirror of all
  three exit conditions; the plan's named test = prefix `i` in C → 10
  candidates vs 7) · +2 `StripContextTest` · +4 `GhostCompletionTest` ·
  `CodeCompletionTest` pinned to the fallback world. Two bugs found pre-CI:
  `TM_DIRECTORY`'s chained `substringBeforeLast` ("" for `proj/main.c`) and a
  refused bare `*` in `ul>*`. **CI: `Build APK` run
  `34034889209` GREEN first try (tip `641f6e8`, 4m34s; artifact +0.11 MiB
  vs `main`). Gate: owner device round per `docs/TROUBLESHOOTING.md` §13.**
- **Phase 29 (all three parts in one build) — TextMate is the editor's
  analyzer.** Sora `language-textmate` from the SAME 0.24.6 BOM (binary
  dep only, LGPL-2.1 notice in `assets/licenses/`); 24 MIT grammar JSONs +
  4 theme JSONs as assets (~234 KB gzipped; vscode / TypeScript-TmLanguage
  / LuaLS, MIT notices shipped); default editor theme **VS Code Dark+**
  (flattened `dark_vs`+`dark_plus`; Monokai/Dracula/GitHub-Dark remain);
  `LanguageType` split (TYPESCRIPT, HTML, CSS, GO, RUST, PHP, RUBY, LUA,
  XML, YAML) so EVERY `LanguageRegistry` extension has a grammar; the regex
  highlighter is off the hot path (fallback + SmartTyping probe + templates
  preview only; `CodeCScheme` deleted). Lazy per-language grammar loading +
  background warm-up (PHP/Ruby sets deferred to first open). Host tests:
  `TextMateGrammarsTest` (pure) + `TextMateSupportTest` (Robolectric, real
  assets, asserts the analyzer swap for all colourable languages).
  **Gate: `Build APK` green + owner device round per
  `docs/TROUBLESHOOTING.md` §12** (Dark+ look vs desktop, every language
  coloured, `.txt` plain, typing still ≤ 16.7 ms p95, theme switching,
  APK delta ≤ +1.5 MiB — measured +2.10 MiB, owner verdict pending).
  Crash 1 (CME) fixed `3fb404f`; crash 2 fixed `288b760` (Compose
  BOM 2024.12.01) + `db56824` (atomic replay) — **device round 1
  PASSED 2026-09-06 (checklist ALL PASS; APK +2.22 MB ACCEPTED by the
  owner over the +1.5 MiB plan budget)**. **MERGED to main via PR #54
  (owner: "Merge it").**
- **Phase 28 remainder:** 28.2 (CodeC Keys layout engine) MERGED via PR #52
  after owner rounds 1–3 (default ON; standing regression card
  `TROUBLESHOOTING.md` §11); 28.3 (chips as row 0) + 28.4 (accessibility)
  remain planned — they do not block 29 and 29 does not depend on them.

- **Phase 24 is ✅ MERGED to `main` via PR #47** (2026-09-04; device round 1:
  E.1/E.2/E.4/E.6/E.7/E.8/E.9 passed; E.3 hardware shortcuts NOT
  device-verified — needs a Bluetooth keyboard; E.5 tablet two-pane DEFERRED
  by design). **PR #48** added the mobile-editor research dossier and the
  Phase 25–28 plan docs. `main` tip = `5ebbc6e`.
- **Phase 25.1 (bench spike) is ✅ COMPLETE & DEVICE-GATED — C-SORA WINS
  (2026-09-04).** Owner: "Start Phase 25" → bench built on this branch (CI
  green `33849153135`, artifacts `CodeC-IDE` + `CodeC-Bench`) → owner ran the
  device round and exported the full sheet (cold open + 4 scenarios ×3 reps ×
  3 candidates × bench.c/bench.html). Results:
  - **C-now (today's core) misses every bench.c budget**: keystroke p95
    **404 ms** (100 % jank — ≈24 missed frames per key), fling ~89 ms frames,
    caret drag ~150–230 ms, completion ~490 ms; even windowed bench.html runs
    ~90 ms keystrokes. The owner's complaint is now measured evidence.
  - **C-sora passes EVERY budget on BOTH corpora**: keystroke p95 14.5–16.6 ms,
    fling ≤3.1 % jank with 0 bad frames, caret-drag p95 ≤17.9 ms (auto-scroll
    traversed 15 lines), completion p95 18–22.5 ms, cold open 35–56 ms;
    `Typed=62` on a 60-key burst = SymbolPairMatch pairing `(`/`{` live.
  - **C-compose2** locked at ~36 ms frames (100 % jank, whole-window
    recomposition storm), drag traversal 0 — dead.
  - **VERDICT (in writing): 25.2 (Sora Editor integration) is the chosen
    path — starts ONLY on the owner's "Start Phase 25.2". 25.3 is ❌
    CANCELLED** (note at the top of `PART_25_3_COMPOSE_FALLBACK.md`).
    Decision table: `docs/EDITOR_MOBILE_RESEARCH.md` §3.1; raw numbers:
    `docs/chat-phase25/PART_25_1_SPIKE_BENCH.md` §4.5–§4.6; JOURNEY §34.
- **Phase 25.2 (sora-editor 0.24.6 as the edit core) is ✅ IMPLEMENTED &
  DEVICE-ACCEPTED (2026-09-04, owner: "All passed" after 4 device rounds).**
  Widget-only swap on `arena/01a06b20-codec` (9 CI rounds, green at
  `33866749797`/`c54228d`): `SoraEditorHost` bridge keeps the VM canonical
  (tabs/dirty/autosave/undo/find/strip/shortcuts), sora-native pinch,
  magnifier, symbol pairs, find-highlight — and (round 3, owner request)
  sora's NATIVE at-caret completion panel fed by the same engine (the old
  app popup is retired). Device-round 1 crash → root-caused from the
  owner's IN-APP crash report (`CrashReportOverlay`, no root needed):
  `EditorColorScheme` constructor calls `applyDefault()` before Kotlin
  assigns subclass state → NPE; fixed post-construction via
  `CodeCScheme.of()`. Drawer edge-swipe disabled over the editor (scroll
  conflict). Record: `docs/chat-phase25/PART_25_2_SORA_PATH.md` §4.
  **All gates satisfied: owner "Yes" (LGPL-2.1) + "Merge" → PR #49
  (squash).** APK delta +0.55 MiB (budget ≤ +2 MB). The 25.1 bench CI
  wrapper + `CodeC-Bench` artifact are removed with the merge (Phase 25
  closed); `bench/` stays in-tree for future re-benchmarks.
- **Phase 26 (Typing Experience 2.0) is ✅ MERGED to `main` via PR #50**
  (2026-09-04; tip `c1b4321`, post-merge CI `33941444393` green) — key strip
  2.0 popups/swipes/hold-repeat + JSON sets, smart typing, IME guide.
  JOURNEY §36.
- **Phase 27 (Phone-native Autocomplete) is ✅ MERGED via PR #51**
  (2026-09-05) — ghost text + chip strip + native panel demoted to ⌄-more
  browse, the law in pure `CompletionPolicy`/`StripContext`, settings master
  switch. 5 CI rounds incl. device-round-1 fixes (composing-gate removal +
  line-tail alignment on word boundaries); `docs/chat-phase27/` §4 records.
  E.3-style per-part device recipes remain owner-side optional.
- **Phase 28 (CodeC Keys) is 🚧 STARTED — 28.1 IME-free spike BUILT
  (2026-09-05, owner: "Start phase 28"), :bench-only, never shipped**: K1
  (Compose core) / K2 (sora core) each fed ONLY by an IME-free 3-row code
  grid through the production `EditorKeySet` model (mirrored verbatim), with
  DOWN→commit latency ledger, strict-subsequence tap audit, IME-inset
  flicker probe (+ self-check toggle), HW-keys + stdin-route scenarios
  (spike Q1/Q2) and a 5-min human session. **Device round 1 RECORDED
  (2026-09-05, `PART_28_1_SPIKE.md` §6): K2/sora passes EVERY budget**
  (DOWN→commit p95 1.25–3.4 ms, tap audits 64/64 & 40/40 exact on all reps,
  run-route OK, IME inset 0 px in ~2 900 samples — never opened); K1's frame
  reds are the Compose core's known ~260 ms cost, the keyboard itself was
  sub-1 ms there too; **28.2 input path of record = S2**. **VERDICT: GO — recorded
  2026-09-05 (owner: "Go", twice; the four human confirmations waive as
  blockers and ride 28.2's device round). `docs/EDITOR_MOBILE_RESEARCH.md`
  §9.1 carries the verdict. **28.2 (layout engine) BUILT the same day** —
  `:app/ui/keyboard/*`: JSON layout over the 26.1 cap schema, pure router,
  Compose grid, Settings (default OFF), S2 wiring (`updateCode` + sora
  `setSoftKeyboardEnabled` handoff), `EditorKey.Delete` = DEL's model home,
  20 host tests; CI green `33964504903`. **28.2 device rounds 1–2 ran and
  landed same day** (cap corner previews + hold swap, `->` row deleted, arrow
  nav = flicks; then LIVE-buffer commits `applyEditorKey` fixing fast arrows,
  Samsung space trackpad, and the master flipped **DEFAULT ON** per owner —
  off restores the 22.x IME world exactly; round 3 made EVERY key on the
  symbols layer a single character — no more pair/macro caps, language-free
  letters layer). Retest card:
  `docs/TROUBLESHOOTING.md` §11; PASS opens 28.3.

**PHASE STATUS (updated 2026-09-05):**
**Phase 25 CLOSED (25.2 device-accepted & merged via PR #49; 25.3
CANCELLED); Phase 26 MERGED via PR #50; Phase 27 MERGED via PR #51;
Phase 28 STARTED — 28.1 spike built, device round pending**
(`docs/chat-phase25/` … `docs/chat-phase28/`).
Phases 20.1, 21, 22, 23, 24, 26, 27 are COMPLETE and merged (27 via PR #51;
26 via PR #50; 24 via
PR #47; 22/23 via PRs #45/#46; 21 via #44; 20.1 via #43). Phase 28 is
STARTED: 28.1 (IME-free input-path spike in `:bench`) is BUILT on this
session branch — CI + owner device round are its gates; 28.2–28.4 wait for a
recorded GO (design law 3: feel is the gate).
- **Phase 45** (the guide: five slides on first run + five coach marks on first arrival) — 🚧 IMPLEMENTED on `arena/01a0955a-codec` (both parts, 50 new host cases = **159 green locally**, CI ✅ GREEN round 1 `34698914219` on tip `3c597b2`, release APK +0.19%); **device round NOT run** — `docs/chat-phase45/DEVICE_ROUND.md` (G1-G14) — `docs/chat-phase45/`. The copy is vocabulary-pinned to the real tree (`GuideVocabulary` + `GuideTermProof`: a word that is not a real button label, file name or language fails the build), the anchors withdraw on dispose so a mark can never point at a control that is not on screen, and the two new Settings rows are audit rows **53-54**.
- **Phase 44** (setup you can see, and cannot half-finish) — 🚧 IMPLEMENTED on `arena/01a0955a-codec` (both parts, 109 host cases green locally, CI ✅ GREEN round 4 `34695797493`; device round 1 🔴 FAILED → three root causes fixed, round 2 NOT run) — `docs/chat-phase44/DEVICE_ROUND.md` — `docs/chat-phase44/`
- **Phase 37** (device as server / LAN) — ✅ IMPLEMENTED, CI GREEN and **DEVICE-PASSED** (owner: "All pass", 2026-09-10), with the round's 🌐 open-in-browser follow-up shipped; merge held — `docs/chat-phase37/`
- **Phase 36** (terminal speed & UX) — ✅ DEVICE-PASSED and MERGED to `main` via PR #60 at `373a51e8f027bcf08fc948b8dc2058c3bda8c566`; progressive output, background survival, and session-switch follow-ups passed after `da126cf`/`11fe8d7`, with green CI `34374983032`/`34375710614` — `docs/chat-phase36/`
- **Phase 35** (editor typing feel) — ✅ DEVICE-PASSED by owner report on `arena/01a086a0-codec`; implementation `317b89a`, docs `88839cd`, Build APK CI `34367008019`/`34367770583` ✅ GREEN; exact device evidence was not supplied — `docs/chat-phase35/`
- **Phase 34** (official file icons) — ✅ DEVICE-PASSED + MERGED via PR #59 — `docs/chat-phase34/`
- **Phase 33** (first-hour UX) — ✅ 33.0 (RUN ▶ chooser) DEVICE-PASSED + MERGED via PR #57; ✅ 33.1–33.3 IMPLEMENTED on `arena/01a085e0-codec` + CI `34346424311` ✅ GREEN + **DEVICE-PASSED** (owner: "Device test pass") — **merge HELD** — `docs/chat-phase33/`
- **Phase 32** (phone canvas) — ✅ DEVICE-PASSED + MERGED via PR #57 — `docs/chat-phase32/`
- **Phase 31** (IntelliSense as Packages) — ✅ MERGED via PR #56 — `docs/chat-phase31/`
- **Phase 30** (snippet packs + Emmet + strip capacity) — ✅ MERGED via PR #55 — `docs/chat-phase30/`
- **Phase 29** (VS Code colour / TextMate) — ✅ MERGED via PR #54 — `docs/chat-phase29/`
- **Phase 27** (phone-native autocomplete) — ✅ MERGED via PR #51 — `docs/chat-phase27/`
- **Phase 26** (typing experience 2.0) — ✅ MERGED via PR #50 — `docs/chat-phase26/`
- **Phase 25** (mobile-first editor core) — ✅ CLOSED (25.2 merged via PR #49; 25.3 ❌ cancelled) — `docs/chat-phase25/`
- **Phase 28** (CodeC Keys) — 🚧 28.1 SPIKE BUILT, device round + go/no-go pending — `docs/chat-phase28/`
- **Phase 24** (polish batch E.1–E.9) — ✅ MERGED via PR #47 (E.3 device pass pending BT keyboard; E.5 deferred) — `docs/chat-phase24/`
- **Phase 23** (inline PTY input + run keys) — ✅ MERGED via PR #46 — `docs/chat-phase23/`
- **Phase 22** (editor smoothness + IME-anchored keys) — ✅ MERGED via PR #45 — `docs/chat-phase22/`
- **Phase 21** (LanguageRunProfile; TCC KEPT as default C compiler) — ✅ MERGED via PR #44 — `docs/chat-phase21/`
- **Phase 20** (toolchains in package repo) — ✅ 20.1 COMPLETE & merged via PR #43 — `docs/chat-phase20/`
**Owner starts the next step by saying so in chat (e.g. "Start Phase 25.2" or pasting the bench results).**

**FUTURE-UPDATE MODE (owner, 2026-09-01):** Phases A–E are planned but not
started. The agent **waits for the owner to say "Start Phase X"** — it does
not begin implementation on its own. For bugs between phases, the agent
**listens carefully, finds the underlying code problem, and solves it**.
Every fix or phase follows `rule.md`: verify → evidence → research →
host-testable implementation + tests → docs → commit/push → CI green →
report → STOP at the merge gate. The owner merges to `main` themselves
(or hands the merge command).

**STANDING RULES (law — read before acting):**

- **No PR/merge without the owner's literal command in chat** (owner
  2026-08-26; reinforced by `rule.md` §3). Committing to and pushing the
  session branch is fine; PR creation and any merge wait. Only an explicit
  phrase like "auto-merge when CI is green" changes this — don't infer it.
- **The owner is browser-first.** Every GitHub action has a github.com click
  path (merge a PR, re-run CI, run a workflow, download the APK) — `rule.md`
  §10 is the cheat sheet. The `gh` commands are only the agent's spelling of
  the same buttons.
- **CLEAN-ROOM LAW (2026-08-31):** replicate FEATURES, never COPY code —
  closed-source (Spck): match visible behavior from mockups/public docs only,
  never decompile; GPL/copyleft (Termux): read public specs, re-implement,
  never paste GPL source.
- **RESEARCH WHEN NEEDED (2026-08-31):** phase docs are a starting point;
  research open questions, record "Research notes" with linked sources.
- **Invariants (law):** no `.` on `PATH`; never `build-package.sh -I`; never
  overwrite `cc` or real ELF `bash` with a shim (`cc` is CodeC's own TCC
  frontend — Phase 20.1 strips `bin/cc` from the clang deb to protect it);
  TCC link order with `-o` last (**PERMANENT** — D.4 cancelled 2026-09-03,
  TCC is the DEFAULT C compiler); never official `com.termux` packages/repos; never bundle the
  bootstrap in the APK; repository metadata stays signed (`signed-by=`, no
  `trusted=yes`); clean-room. Full list: `docs/TERMINAL_PLAN.md` §B/§J,
  `docs/chat-phase1/SOLUTIONS.md`, `docs/chat-phase3/REPOSITORY_SIGNING.md`.
- **CI is the only test executor** — the sandbox has no Java runtime;
  `Build APK` runs `:app:assembleDebug` + `:app:testDebugUnitTest` +
  `:app:lintDebug` (via the `gradle-bootstrap` shim — see `rule.md` §5/§10).
  Write host-unit-testable, Android-free logic (pattern:
  `TerminalBuffer`, `AnsiParser`, `GitManager`, `WebPreviewServer`,
  `DeviceApiOps` …).
- **Sandbox limits:** reach `api.github.com` only — no CI logs/releases/
  artifact downloads, no on-device testing; use `gh` (check-runs annotations)
  for CI state. **Never `reset --hard`** (realign with fetch +
  `reset --mixed FETCH_HEAD`). **Never trigger expensive actions** (package
  repo build ~60–100 min, release, destructive device test, force-push)
  without explicit confirmation; check `gh run list` first.
- **Do not redo/re-debug anything marked COMPLETE / ✅** unless the identical
  symptom reappears with regression evidence.

**FACTS THAT MUST NOT REGRESS:**

- **Vector APIs (Compose `ui-graphics` resolved is far newer than the BOM
  suggests):** old string-path `addPath(pathData: String, color=…)` is GONE.
  Use `addPath(pathData: List<PathNode>, …, fill: Brush?, …,
  stroke: Brush?, strokeLineWidth, strokeLineCap, strokeLineJoin, …)`;
  `PathNode` in `androidx.compose.ui.graphics.vector`; `Color` is NOT a
  `Brush` (wrap in `SolidColor`); `DrawScope.drawLine` endpoint is `end`;
  use `navigationBarsPadding()`.
- **Web Preview:** serves the project folder over loopback
  `http://127.0.0.1:<ephemeral>/` (`WebPreviewServer`); `file://` only as
  fallback; preview navigation must carry the **authoritative project**
  (VM `currentProject` / drawer `entry.projectName`), never the Nav route
  argument — the route argument goes stale after in-editor folder switches.
- **Completion sources (Phase 30):** snippet packs are MIT DATA in
  `assets/snippets/` — never hand-edit a pack (re-run
  `scripts/vendor_snippets.py` and keep `SnippetAssets.packsFor` + its `PACKS`
  table in sync; `SnippetLibraryTest` pins every path). Labels are pack
  PREFIXES; matching is case-insensitive (22.6 law); JSON/TEXT/XML/YAML ship
  no pack; the built-in Kotlin tables are a deduped TAIL after the pack and
  the FALLBACK when none loads, so never delete them (four 22.6-accepted
  typings exist only there). A device card must be MEASURED against the real
  engine + real assets before it is handed over — five Phase 30 card strings
  were wrong until they were (JOURNEY §41). `Emmet.kt` is clean-room — do not paste emmetio code.
  Caps: `MAX_ITEMS` 50 / snippets 40 / identifiers 6 / keywords 6, and
  `SuggestionStripModel.MAX_CHIPS` 8 (the thumb law — the strip is not a list).
  A lone candidate stays in KEY mode unless it is an Emmet expansion
  (`detail == Emmet.DETAIL` and no ghost) — 27.2 S1 + its one exception.
  **Accept span (device round, 2026-09-07):** accepting an item replaces
  `CodeCompletionEngine.replaceSpanLength(text, cursor, insert)` = the
  identifier word-run ∪ the longest tail of the caret's line that the insert
  text continues — case-INSENSITIVE for accept (22.6's matching law) and never
  crossing a newline. The ghost must KEEP literal-case alignment
  (`alignedTailLength(..., ignoreCase = false)`): it paints the suffix into the
  buffer, so what it claims has to be byte-true. BOTH accept surfaces use it
  (the VM's chip accept and `CodeCAnalyzer`'s ⌄ panel); an item's own
  `replaceLength` (Emmet) still wins. **Auto-pair:** `SmartTyping.transform`'s
  flag is `suppressAutoPair` — pass `true` ONLY for the editor key strip (it has
  its own `()` cap) and for programmatic caret moves; every TYPING surface
  (system IME, CodeC Keys) pairs, and `{` + Enter splits the pair with the caret
  indented. **Name a flag for the behaviour it gates, never for the surface it
  was invented on** — `isStrip` silently mis-gated the 28.2 keyboard for two
  phases and no test could see it, because the IME path paired correctly.
- **CodeCApi:** ops `battery.status` / `sensor.read` / `tts.speak` /
  `camera.capture` / `intent.send`; marker `NEED_PERMISSION:` and
  `CAPTURING:`; camera output names `^[A-Za-z0-9][A-Za-z0-9._-]*\.(jpg|jpeg|png)$`;
  `BOOTSTRAP_VERSION` is **"27"**.

**ORDER OF WORK:**

1. Verify state (`gh pr list`, `git status`, `gh run list`) before acting —
   including the real `main` tip (locally the clone is shallow; cross-check
   with `api.github.com/repos/pabi277/CodeC/branches/main`).
2. Phases 20.1–27 are MERGED; 28.2 is MERGED (28.3/28.4 remain planned);
   **Phase 29 is MERGED via PR #54; Phase 30 (snippet packs + Emmet + strip
   capacity) is MERGED via PR #55 — device-passed 2026-09-07, so do not
   re-implement or "improve" it without evidence.**
   **Phase 31 is MERGED via PR #56; Phase 32 (phone canvas) is DEVICE-PASSED
   on `arena/01a083fc-codec`** (owner: "Start phase 32" → "All test passed on
   device") — see the head line; **Phase 33 (first-hour UX): 33.0 (RUN ▶
   chooser) is MERGED via PR #57, and 33.1–33.3 are IMPLEMENTED + CI
   `34346424311` ✅ GREEN + DEVICE-PASSED on `arena/01a085e0-codec`**
   (owner: "Start phase 33" → "Device test pass") — **merge HELD**. The owner
   then resumed phased **UX/UI** work: Phase 34 is merged, Phase 35 is
   device-passed by owner report, Phase 36 is MERGED via PR #60, and
   **Phase 37 (device-as-server LAN) is IMPLEMENTED, CI GREEN and
   ✅ DEVICE-PASSED on `arena/01a0872e-codec`** (owner: "All pass" on all eight
   exit checks, 2026-09-10), **with that round's 🌐 open-in-browser follow-up
   shipped on the same branch** — and Phase 37 + the 38-43 plan are now
   **MERGED via PR #62** (`main` at `e89dc49`).
   **The 38-43 series ("before I share the app") runs 38 → 39 → 40 → 41 →
   42 → 43 — the numbers ARE the order (39.1 before 43.2 is the one
   ordering rule). Phase 38 (Identity: app icon + Settings trim) is ✅
   COMPLETE, DEVICE-PASSED & MERGED to `main` via PR #64 → `main` at `dcd65b4bc3e65d268bcc354da9d413d74eb25038` (merge commit, history preserved)** (owner:
   "Start Phase 38" → "All device test pass … then marge it", 2026-09-10): the original
   `>_` mark with a real monochrome layer, committed generated rasters +
   a notification silhouette (38.1), and the Termux Engine card out of
   Settings with the mechanism kept + the audit that deleted two
   reader-less store keys (38.2) — 45 new host cases, CI GREEN, device
   round passed by owner report. **Phase 39 (outputs are temporary,
   never in your repo) is 🔧 IMPLEMENTED on `arena/01a08a0a-codec`
   (host-tested; device round pending)** — `RunArtifacts` + `TempGc` +
   `RepoHygiene` (~56 patterns incl. `.codec/`) enforced inside
   `stageAll`; no self-initiated phase starts otherwise. **Next: device
   round, then Phase 40.**
3. A part is complete only when its exit condition is met and verified (owner
   device transcript for device gates — never claim acceptance without one).
4. Keep `prompt.md`, `docs/JOURNEY.md`, `docs/NEXT_STEPS.md`,
   `docs/TROUBLESHOOTING.md` and `rule.md` updated as gates close — the next
   chat trusts only what is written there and verified in git/CI.

**Before each change, state:** what you are changing, which existing feature it
serves, which invariant (if any) it could affect.
