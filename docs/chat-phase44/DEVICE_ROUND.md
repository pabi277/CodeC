# Phase 44 — device round (setup you can see, and cannot half-finish)

> **Status:** 🔴 **ROUND 1 RUN BY THE OWNER (2026-09-12) — FAILED on 4 rows, three
> root causes found and fixed; round 2 pending.** This sandbox has no device, no
> emulator and no Gradle, so nothing below is claimed as tested by the agent. Install the CI artifact
> (`CodeC-IDE`) from the green `Build APK` run on `arena/01a0955a-codec`.
> Every row names the **exact on-screen text** that means PASS; anything else
> is a FAIL worth reporting verbatim (screenshot beats description).
>
> **The whole phase is about first run, so start from a clean slate:**
> uninstall CodeC (or Settings → Apps → CodeC → Storage → **Clear data**)
> before D1, and again before D8/D10/D12 where noted. 20–30 minutes, one
> phone, mobile data or Wi-Fi (a slower connection makes the percentages
> easier to watch — that is a feature of the round, not a problem).

## Round 1 result — 🔴 FAILED on 4 of the owner's own rows (2026-09-12)

The owner installed the round-3 artifact (`ba51382`, CI ✅ `34693462725`) on his
phone — an **updated** install, so no first-run welcome — and reported, verbatim:

> *"It have bugs · I couldn't not open the terminal it's opening the editor ·
> The top a massage "C works right now. The linux tool need one install- open
> terminal and tap download " · But it's not closing or opening terminal · Every
> package saying view setup but terminal not opening editor opening"*

Three root causes, all provable from the code, all fixed:

**1. A marker is not a prefix.** `installIfNeeded(force = false,
checkForUpgrade = false)` answers `AlreadyInstalled` from the **release marker
alone** (the fast warm-open path, `UserlandInstaller.kt:145-149`) and the
ViewModel turned that into stage `READY`. His phone carries a marker written by
a **pre-44 build** — which wrote the marker *before* the swap — on top of a
prefix whose `bin/pkg` is gone: exactly the half-finished state 44.2 exists to
repair. Stage `READY` (marker) + facts *not usable* (disk) = the sentence he
quoted, permanently, because nothing ever re-decided it.
**Fix:** the `AlreadyInstalled` branch now asks the disk
(`SetupGatePolicy.userlandUsable`) and publishes `FAILED(BROKEN_USERLAND)` when
the marker lies; the wording became *"Setup didn't finish — the Linux tools
aren't working. Open the Terminal tab and tap ⬇ to install them again."* (the old
*"can't start on this device"* reads like an unsupported phone, which has a
different fix). Plus a second look from the disk once the boot repair is
finished (`refreshSetupFromDiskWhenIdle`), so a stale ledger cannot pin the bar
either.

**2. The bar was a wall in exactly that state.** The action button rendered only
`if (inFlight || stage == FAILED)` and the ✕ called `onDismissNote` — which
cleared a note that was not there. In `READY`-but-unusable: **no button, dead
✕**. **Fix:** the whole bar is one tap to the Terminal tab, **VIEW SETUP** is
always rendered, and the ✕ is real (`SetupGatePolicy.barDismissAllowed` =
`progress.settled`; dismissing remembers the bar's *text*, so a changed state —
a new install, a repair — brings it back).

**3. `restoreState = true` restores a whole sub-stack, not a tab.**
`navigate(terminal) { popUpTo(start) { saveState = true }; restoreState = true }`
puts back the previously saved stack *for that destination*, whose top can be an
editor the user opened above the terminal earlier — so "go to the terminal"
arrived at **the editor**. **Fix:** every navigation that means *show me the
terminal* now uses `restoreState = false` — the setup bar, Packages' VIEW SETUP,
the editor's terminal hand-off, the launch divert, and the bottom bar's Terminal
tab. The other four tabs keep their pre-44 restore behaviour (Phase 49 is the
systematic navigation pass).

**And the launch-tab gap behind "it's opening the editor":** the divert was keyed
on the first-run welcome handing over (`setupDiverted`), so an *updated* install
with unfinished tools still launched into the editor — under a bar telling him to
open the terminal. **Fix:** `SetupGatePolicy.startOnTerminal(usable, abiSupported)`,
decided **synchronously from the disk** (`userlandUsable(prefix, ledgerPhase)` +
`UserlandManifest.archName() != null`) and applied with a `navigate()` after the
first composition — not by putting an argument-carrying route into
`startDestination`, which is graph-construction risk for no gain.

**Deliberately NOT done:** an automatic re-download when the marker-only state is
detected. A forced reinstall is ~40 MB on possibly mobile data and this phase's
law is *no surprises*; the one-tap path (terminal first → VIEW SETUP → ⬇) is
signposted instead. If the owner wants auto-repair on launch it is one call
(`installUserland(force = true)`) in that same branch — say the word.

**109 host cases** green locally after the fixes (`SetupGatePolicyTest` 25 — incl.
the launch rule, the dismiss rule and the exact sentence he quoted;
`SetupGateWiringTest` 20 — incl. "the bar is always actionable", "every
go-to-the-terminal navigation arrives at the terminal", "an install marker alone
is never accepted as ready").

**CI round 4 (`34695797493`, tip `4bf3c4c`) is ✅ GREEN** — `conclusion: success`, job `build` 10m53s, zero error annotations, artifacts `CodeC-IDE-release` 6,651,682 B / `CodeC-IDE-debug` 25,608,360 B (v1.3.17). Per `rule.md` §5 the green run means `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` all passed, so the **109 host cases ran on real Gradle/JUnit/Robolectric**, not only on the local kotlinc harness. (The sandbox could not download the run log — `results-receiver.actions.githubusercontent.com` answered EOF twice — so the evidence is the conclusion plus the empty error-annotation list.)

### Round 2 — re-test these eight rows first (build `4bf3c4c`)

Download the artifact from the run: <https://github.com/pabi277/CodeC/actions/runs/34695797493>
→ **Artifacts** → `CodeC-IDE-debug` (or `CodeC-IDE-release`, signed, 6.65 MB).
Install it **over** the current install — round 1's whole lesson is that the
marker-only state must be recognised by the *new* build, so do not clear app data
first.

| # | What to do | PASS looks like |
|---|---|---|
| R1 | Launch CodeC (updated install, tools not working) | The **Terminal** tab opens — not the editor — with the bar above it |
| R2 | Read the bar | `Setup didn't finish — the Linux tools aren't working. Open the Terminal tab and tap ⬇ to install them again.` (or the download percentage once it is downloading) |
| R3 | Tap **anywhere** on the bar, then tap **VIEW SETUP** | The terminal opens **and stays open** (round 1: the editor opened) |
| R4 | Tap the ✕ on the bar, then tap ⬇ to start the install | The bar goes away; it comes back with the download percentage (a changed state is never hidden) |
| R5 | Packages tab → any card → **VIEW SETUP** | The terminal opens (round 1: the editor opened) |
| R6 | Terminal tab → open a file in the editor → tap the **Terminal** tab in the bottom bar | The terminal opens (round 1: the editor stayed on top) |
| R7 | Tap **⬇** in the terminal toolbar | The download runs with a moving percentage, the notification shows it, and at the end `pkg --version` prints a version and the bar is gone |
| R8 | `pkg install python` then `python3 -V` | It installs and runs — the original row 1 failure |

Then the twelve rows below (D1-D12) on a **fresh** install, as written.

---

## The three surfaces to watch

| Surface | Where | Text while downloading |
|---|---|---|
| **Setup bar** | top of **every** tab, under the safe-mode banner slot | `Setting up CodeC's Linux tools — 42 % · C works right now` |
| **Don't-close row** | Terminal tab, above the status chip | `Don't close CodeC — it is finishing a one-time setup (42 %)` |
| **Notification** | status bar, for the whole download | `Downloading CodeC's Linux tools — 42 %` (with a progress bar) |

The terminal's status chip reads `downloading userland 42 %` →
`verifying download…` → `unpacking userland…` → `running`.

## The 12 checks

| # | What to do | PASS looks like |
|---|---|---|
| D1 | **Fresh install**, online, first launch → finish the welcome/starter choice | The **Terminal tab opens first** (not the editor) and the setup bar is already there with a **moving percentage**. You did not have to find the Terminal tab yourself |
| D2 | While it downloads, tap **Projects**, **Files**, **Editor**, **Settings**, then back to **Terminal** | The **same bar with the same percentage** is at the top of every one of them — no tab hides it, and the number never restarts from 0 |
| D3 | Pull down the status bar during the download, then wait for it to finish | A CodeC notification shows `Downloading CodeC's Linux tools — NN %` with a progress bar; when setup reaches READY the notification **disappears** (or turns into the normal terminal-running one only if a shell is alive) |
| D4 | Force-stop, then Settings → Apps → CodeC → **Notifications: off** → reopen the app mid-install (or clear data + airplane-mode-off relaunch) | **No crash.** The in-app bar, the don't-close row and the chip all still tell the truth; only the status-bar notification is missing |
| D5 | **During** the download: open the editor, write a 5-line `hello.c`, tap **RUN ▶**. Then in the terminal: `cc hello.c -o hello && ./hello` | It **compiles and runs**. C is never gated by setup, at any percentage |
| D6 | **During** the download: open the **Packages** tab, tap any package's **INSTALL** (and any Quick Action / custom `pkg` command) | No `pkg` runs and nothing is silently queued. You get one honest sentence: `CodeC is downloading its Linux tools (58 %). Don't close the app — this happens once.` plus a **VIEW SETUP** button that takes you to the Terminal tab |
| D7 | **During** the download: RUN a `.py` (or any non-C) file, or use the editor's "install language support" path | The Output Panel prints the honest setup sentence (ending `C works offline right now.`) instead of `python: not found` or a fake success |
| D8 | **Kill mid-DOWNLOAD**: at ~30–60 %, Recents → swipe CodeC away (or Settings → Apps → CodeC → **Force stop**) → relaunch | The download **resumes or restarts cleanly**, the bar shows the truth again, and setup finishes. **No** `pkg: not found` afterwards |
| D9 | **Kill mid-EXTRACT**: clear data, relaunch, and force-stop while the chip says `unpacking userland…` → relaunch | The bar comes back, setup **completes on its own**, and afterwards `pkg --version` prints a version in the terminal (no half-unpacked prefix, no stuck spinner) |
| D10 | **Kill mid-SWAP** (the ~1 s two-rename window — see the timing note below) → relaunch | The bar's notice row says **once**: `Setup was interrupted; CodeC restored your Linux tools.` and `pkg --version` works in the terminal. `usr` is back — the owner's exact bug, repaired at boot |
| D11 | After D8–D10: `pkg install python` in the terminal, then `python3 -V` | It **installs and runs**. This is the failure the owner reported, so it is the row that decides the phase |
| D12 | **Clear data → airplane mode ON → launch** | Bar: `CodeC needs the network once to finish setting up its Linux tools. C works offline right now.` Chip: `setup incomplete — tap ⬇ to retry`. A `.c` file still compiles and runs. Turn airplane mode off and tap **⬇**: setup completes |

### Timing D10 (the swap window) honestly

The window is two `rename` calls, so a swipe-away will usually miss it. Two
ways that do hit it:

1. **Re-install over a working prefix** — with the userland already installed,
   tap **⬇** in the terminal toolbar (force re-install). The swap then happens
   with a full `usr` present, and force-stopping during
   `Unpacking CodeC's Linux tools` / the moment the chip flips to `running`
   lands inside or right next to the window. Repeat up to ~5 times.
2. **Force stop** (not swipe-away) kills the process immediately, so it lands
   where you press it rather than after a graceful shutdown.

If you cannot hit it, say so — `SwapRecoveryTest` covers all three kill points
with real temp directories on the host, and D10 is the device confirmation, not
the only evidence. **A FAIL here looks like:** `pkg: not found` on the next
launch, a bar that claims READY while nothing works, or an ever-growing pile of
`usr.old-*` directories (visible only via `ls -a` in the terminal on a rooted
device, so judge by behaviour: repeated interrupted installs must still leave a
working `pkg`).

## Also worth one look each (not rows)

- The bar has a **✕** only when setup is settled *and* the tools are usable —
  during the download there is no way to hide it (that is deliberate: hiding it
  is the bug).
- The bar never shows bytes/second or an ETA, only a percentage — a throttled
  connection must not look like a hang.
- On an **x86_64 emulator** (no bootstrap for that ABI) the bar reads
  `C works offline · extra languages aren't available on this device` and the
  app starts in the **editor**, not the terminal.

## Result

Report the row numbers that passed (e.g. "D1–D12 pass"), the **phone model +
Android version**, and — if you ran D10 — whether the restore notice appeared.
Paste screenshots for any row whose text differed. Until this round is
reported, Phase 44 stays 🚧 IMPLEMENTED and is described as **device pass
required** (`rule.md` §5).
