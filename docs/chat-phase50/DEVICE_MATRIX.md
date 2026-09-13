# Phase 50 — the cross-device matrix (owner runbook)

> **What this is:** one runbook that walks **44 · 45 · 46 · 47 · 48 · 49**'s exit
> conditions on a matrix of hardware, so "it works on my phone" stops being the
> evidence. **90 rows in ten rounds**, each row naming the part it tests, the
> device class it must run on, and the **exact on-screen text** that means PASS.
>
> **Status (2026-09-13):** 🚧 **THE INSTRUMENT IS SHIPPED; THE MATRIX IS NOT YET
> FILLED.** Nothing in the sandbox has a handset, an IME or an OEM battery
> manager, so every row below belongs to the owner's devices. The deliverable of
> this phase is a **filled matrix**, and filling it is a two-minute paste per
> device (§4) — the results live in the owning part file's `## Test log`, and
> `DeviceMatrixTest` fails the build if this file and those logs ever disagree.
>
> **The build to install:** the latest green `Build APK` on **`main`**
> (`34744496605` at tip `62cfe7b` at the time of writing). One build now carries
> the whole series — 44+45 merged in PR #77, 46+47 in #78, 48+49 in #79 — so
> **there is no reason to test an `arena/*` branch build**, and no row below may
> be run on anything older: rows C/D reference the eleven-beat tour and rows
> A9/B3 the round-6 lock, neither of which exists in earlier APKs.
>
> **Same law as Phases 40, 41, 44 and 45**
> (`../chat-phase40/DEVICE_TEST_PLAN.md`, `../chat-phase44/DEVICE_ROUND.md`,
> `../chat-phase45/DEVICE_ROUND.md`): **if the screen says something different,
> copy the text verbatim and say which row it was — that report IS the bug
> report.** A row you cannot run because the hardware is missing is `n/a`, not
> `✅`.

---

## 0. Before you start (3 minutes per device)

1. **Get the APK.** GitHub → **Actions** → latest green `Build APK` on `main` →
   **Artifacts** → **CodeC-IDE-debug** (or `CodeC-IDE-release`, signed, ~6.7 MB)
   → unzip → install (allow *Install unknown apps*).
2. **Identify the device instead of guessing at it.** Settings → **Feedback &
   Support** → **COPY REPORT** → paste anywhere → **line 1** is
   `CodeC <version> · Android <release> (API n) · <model> · <abis>`. Paste that
   line as the header of your results — it is the only device identity this
   matrix trusts, and it is what makes a second device's row comparable to a
   first one's.
3. **Note the navigation mode** — Settings → System → Gestures: *3-button* or
   *Gesture*. Round I means something different on each, and the owner's own bug
   (*"My phone showing the option when try to close not now option but in most
   phone no option like not now or exit"*) is a nav-mode + back-stack bug, so a
   round-I pass on one mode proves nothing about the other.
4. **How to get the states the rows ask for:**
   | Need | How |
   |---|---|
   | a fresh install | uninstall, or Settings → Apps → CodeC → Storage → **Clear data** |
   | the five slides again | **Clear data**, or Settings → About → **Show the welcome screen again** + kill + relaunch |
   | the eleven-beat tour again | Settings → About → **Reset tips** + kill + relaunch (the beats you already saw stay spent otherwise) |
   | a stuck half-install | Terminal tab → **⬇** re-runs the setup over what is on disk |
   | no network at all | airplane mode **before** launching (round B, row B5) |
   | the log of a back press | Settings → Logs, or the crash dialog's **COPY REPORT** (round I, row I14) |
5. **Rows marked `★` are the ones whose answer differs by device class** — run
   those on every device you have before spending time on the rest. The
   shortlist for a second phone is §3's **20-minute pass**.

## 1. The four device classes

**Class 1 and class 2 are both mandatory** (they are the two nav modes); 3 and 4
run if a device is at hand. Every row's `Run on` column names its class.

| Class | Shape | Why it is in the matrix |
|---|---|---|
| **D1** | Small gesture-nav phone (≤ 6.1", ≤ 4 GB RAM, largest system font if you can stand it) | the caret/keyboard rows (H) and the tour's card geometry (C, D, E) are worst here; low RAM + an aggressive OEM skin is where an interrupted setup (A, B) happens |
| **D2** | Large phone, **3-button navigation** | the back rows (I) behave differently under 3-button nav, and the exit prompt is the owner's own 5.B |
| **D3** | Android **13+** | `POST_NOTIFICATIONS` is a runtime permission there and the foreground-service rules are strictest — the setup bar (A) and the notification (A3, A4) must still be true even though CodeC targets SDK 28 |
| **D4** | Tablet / foldable / low-end | wide and split layouts: the drawer (G), the tour's holes and card (D, E), the single-file status bar (F), the setup bar's one-line rule (A) |

## 2. The rounds

`Part` = the spec the row tests (its file's `## Test log` is where the result
goes, §4). `Run on` = the classes that must run it. **★** marks the rows to run
*first* on a new device — the ones where hardware, not policy, decides the
answer, or where a device has disagreed before. A span in `back-ticks` is
**verbatim on-screen text** (CI proves it exists); `NN` and `…` inside one are
the app's own number and its own variable part.

### Round A — the setup you can see (44.1)

| # | Part | Run on | What to do | PASS looks like |
|---|---|---|---|---|
| A1 ★ | 44.1 | ALL | Clear data, launch, finish the starter tile, **stay on the Terminal tab** | The app opened on **Terminal**, not the editor, and the bar already reads `Setting up CodeC · C works right now`, then `Setting up CodeC's Linux tools — NN % · C works right now` with the number moving |
| A2 ★ | 44.1 | ALL | While it downloads, tap Projects, Editor, Packages, Settings | The **same bar with the same number** is at the top of every one of them; the number never restarts from 0 |
| A3 | 44.1 | ALL | Pull down the status bar mid-download, then let it finish | `Downloading CodeC's Linux tools — NN %` with a progress bar; when setup settles the notification **goes away** (or becomes the plain terminal one while a shell lives) |
| A4 ★ | 44.1 | D3 | Deny the notification permission when the system asks, mid-install | No crash. The in-app bar, the terminal's don't-close row and the chip all keep telling the truth; only the status-bar notification is missing |
| A5 ★ | 44.1 | ALL | Mid-download: write a 5-line `hello.c`, tap RUN ▶; then in the terminal `cc hello.c -o hello && ./hello` | It compiles and runs at ANY percentage. C is never gated by setup — TCC lives in the APK |
| A6 ★ | 44.1 | ALL | Mid-download: Packages tab → any card's INSTALL (and a Quick Action / custom `pkg` command) | Nothing is typed into a dead shell and nothing queues. One sentence — `CodeC is downloading its Linux tools (NN %). Don't close the app — this happens once.` — plus **VIEW SETUP**, which lands on the **Terminal** tab |
| A7 | 44.1 | ALL | Mid-download: RUN a `.py` (or take the editor's "install language support" path) | The Output Panel prints the honest sentence, ending `C works offline right now.` — not a python: not found line and not a fake success |
| A8 | 44.1 | ALL | Watch the terminal's status chip from the first second to READY | `downloading userland NN %` → `verifying download…` → `unpacking userland…` → `running`; above the chip, `Don't close CodeC — it is finishing a one-time setup (NN %)` and it disappears at READY |
| A9 ★ | 44.1 | ALL | After the bar settles, tap the ✕; then start a repair with ⬇ | In flight there is **no** ✕ to tap (deliberate). Once settled the ✕ really puts the bar away, and it returns by itself when the state changes |
| A10 | 44.1 | D1+D4 | On the small screen at the largest font, and on the tablet, read the bar | One line, an ellipsis if it must clip, and **VIEW SETUP** fully tappable — never a clipped button, never two lines pushing the tab bar off |
| A11 ★ | 44.1 | ALL | Put the app in the background for 10 minutes mid-download (do not open it), then return | It finished, or it is honestly still running with a number. It never shows a settled `READY` over tools that do not work |
| A12 | 44.1 | ALL | At the end: in the terminal type `pkg --version`, then `pkg install python` and `python3 -V` | A version prints, Python installs and runs, and the bar is gone |

### Round B — the setup that cannot half-finish (44.2)

| # | Part | Run on | What to do | PASS looks like |
|---|---|---|---|---|
| B1 ★ | 44.2 | ALL | **Kill mid-DOWNLOAD**: at ~30–60 % force-stop (Settings → Apps → CodeC → **Force stop**, or swipe it away) → relaunch | The download resumes or restarts cleanly, the bar tells the truth again, setup finishes, and afterwards `pkg --version` works. No half-written prefix |
| B2 ★ | 44.2 | ALL | Clear data, relaunch, and **kill mid-EXTRACT**: force-stop while the chip says `unpacking userland…`, then relaunch | The bar comes back, setup **completes on its own**, `pkg --version` works — no stuck spinner, no orphan pile |
| B3 ★ | 44.2 | ALL | Hit the ~1 s swap window: with the tools already installed tap **⬇** in the terminal toolbar (a re-install) and force-stop as the chip flips to `running`. Repeat up to 5× | One-time notice row on the bar: `Setup was interrupted; CodeC restored your Linux tools.` — and `pkg --version` works on the next launch. The owner's original data-loss case, repaired at boot |
| B4 | 44.2 | ALL | Repeat B3 three times in a row | Nothing accumulates and disk usage does not grow; `pkg` still works after the third |
| B5 ★ | 44.2 | ALL | Airplane mode **on**, Clear data, launch, wait for the failure; then airplane off + **⬇** | Bar: `CodeC needs the network once to finish setting up its Linux tools. C works offline right now.` Chip: `setup incomplete — tap ⬇ to retry`. A `.c` file still compiles. With the network back, ⬇ completes the setup |
| B6 | 44.2 | D4 | On hardware with no bootstrap for its ABI (an x86_64 emulator qualifies) | Bar: `C works offline · extra languages aren't available on this device`. The app opens in the **editor**, not the Terminal, and nothing retries or churns |
| B7 | 44.2 | ALL | Kill during an **upgrade** of a working userland (⬇ with the tools already usable), then relaunch | The tools that worked still work — `pkg --version` prints, and the bar says `Updating CodeC's Linux tools (NN %) — everything still works.` or is gone |

### Round C — the five slides (45.1)

| # | Part | Run on | What to do | PASS looks like |
|---|---|---|---|---|
| C1 ★ | 45.1 | ALL | Clear data → launch → pick a starter tile | The guide is the next thing you see: `Guide · NN of 5`, a fifth-filled bar, **SKIP** top right — on slide 1 as well as later ones |
| C2 | 45.1 | ALL | Tap **GOT IT** four times, reading the titles | In order: `Your files live in the ☰ menu` → `RUN ▶ compiles and runs` → `One download, one time` → `A real terminal` → `Projects vs single files`, whose button reads **START CODING** and lands in the app |
| C3 | 45.1 | ALL | Read slide 3 | `Python, Node and the Linux tools download once on first use. Keep CodeC open while it finishes.` — this is the slide that makes round A's bar make sense |
| C4 ★ | 45.1 | ALL | Press the **back button** on slide 2, then relaunch | Back leaves the guide exactly like SKIP does (it never traps you), and the guide does not come back |
| C5 | 45.1 | ALL | Re-open the guide from all three doors: Settings → About; the Projects hub ⋮; the editor ☰ drawer footer | Labels are `Help & guide` in Settings, `Guide` in the hub menu, `Guide` in the drawer footer. Each opens the same guide at slide 1, and re-opening never marks anything seen |
| C6 | 45.1 | D1+D4 | On a small screen at the largest font (and on the tablet), walk all five slides | The whole card is on screen, the button is reachable, and the copy column scrolls if it must |

### Round D — the eleven-beat tour (45.2)

| # | Part | Run on | What to do | PASS looks like |
|---|---|---|---|---|
| D1 ★ | 45.2 | ALL | After the slides, arrive at the editor | `Tour · NN of 11`, a scrim, a hole exactly around **☰**, card titled `Your files`. The card has **no button at all** — no NEXT, no SKIP, no GOT IT |
| D2 ★ | 45.2 | ALL | Tap the highlighted ☰ **once** | The drawer opens **and** the box is already `2 of 11` on the project name, in the same gesture. Round 3 needed a second tap — that is the bug this row exists to remove |
| D3 ★ | 45.2 | ALL | Continue with ONE tap per beat: the project name → `demo_flask` → `app.py` → RUN ▶ | `3 of 11` `Pick the demo` lands on the demo's own row; the switch happens **behind the open drawer** (the list stays dropped down); `4 of 11` `Open app.py` is on the file; the list drops, the tree refreshes, the file opens, RUN runs — and **nothing fires twice** (one run, one toast, no double-open) |
| D4 | 45.2 | ALL | On `5 of 11` (RUN ▶), read the card, then tap it | `Runs the open file. If Python is missing, tap Install — one download, one time.` and it runs the OPEN file — no "Run main.c / Run utils.c" chooser dialog on this beat |
| D5 ★ | 45.2 | ALL | If the Install Python? prompt appears, tap Install; watch while it streams | No box over the dialog, the install streams into the Output Panel, and **no box is drawn while the setup moves** — the tour waits on the preview beat instead of walking past it |
| D6 | 45.2 | ALL | Let the Flask preview open, then continue to the end | `6 of 11` `Your app is running` on the preview's Back arrow; `7 of 11` `The tabs are here` on the whole tab bar (or on the reveal handle if the keyboard hides it); `8 of 11` `Packages`; `9 of 11` `One-time download`; `10 of 11` `Terminal`; `11 of 11` `What it is doing` |
| D7 ★ | 45.2 | ALL | Tap the eleventh hole | The finish card: `Tour · NN of 11`, `That is the whole tour`, and the tour's only two buttons — **VIEW AGAIN** and **CLOSE** |
| D8 | 45.2 | ALL | **VIEW AGAIN**, then walk one beat and kill the app mid-tour; relaunch | VIEW AGAIN navigates to the editor and restarts at `1 of 11`, all eleven beats. After the kill, the tour resumes at the beat you had not spent |
| D9 ★ | 45.2 | ALL | On any box: tap **outside** the hole; then press **back** | The outside tap does nothing at all — the box stays, the UI under the scrim is untouched. Back navigates away and **no box follows**; come back and the same beat, same number, is waiting. Back pauses a tour, it cannot cut one |
| D10 | 45.2 | D1+D4 | With a box up, rotate the device (or enter split screen / drag the foldable's divider) | The hole follows its control or the box disappears — it never floats over the wrong control, and the card stays fully on screen |
| D11 | 45.2 | ALL | Projects hub → ⋮ on `demo_flask` → Delete; return to the tab | It is back on the next list refresh. A demo you deleted is never left as a hole the tour points into |

### Round E — the chrome lock while an install moves (44.1 §"the chrome lock")

| # | Part | Run on | What to do | PASS looks like |
|---|---|---|---|---|
| E1 ★ | 44.1 | ALL | Clear data, launch, and in the **first second** — before any percentage, and again with the network off — tap Projects, Editor, Packages, Settings | All four are already dimmed with a small 🔒, and each answers `Hang tight — CodeC is getting ready to set up its Linux tools. Other options are paused for a moment so this one-time setup finishes cleanly. The Terminal tab shows every step.` There is no window in which a tab can be switched |
| E2 ★ | 44.1 | ALL | During that pause, stay on Terminal | The **Terminal** tab is never locked (that is where the install is visible), the bar keeps its percentage, and no tour box is drawn while the lock is on |
| E3 | 44.1 | ALL | Mid-download, read a locked tab's sentence as the number moves | The same sentence with the download's own version, including `(NN %)`, so the number appears twice in one screen |
| E4 ★ | 44.1 | ALL | In the editor, RUN a `.py` → Install, and watch the other tabs | Projects / Packages / Settings / Terminal are paused with `Hang tight — CodeC is installing what you asked for. Other options are paused for a moment so this one install finishes cleanly. The Output panel shows every step.` while the **Editor** stays open. ☰ and RUN ▶ answer the same sentence instead of acting, and the drawer's edge swipe is dead |
| E5 | 44.1 | ALL | Run your own program (a C build, or a Flask server) and leave it running | **Nothing is paused.** A run or a server is not an install: tabs, ☰ and RUN ▶ all work while the Output Panel streams |
| E6 ★ | 44.1 | ALL | Let a one-time setup finish **without killing the app** and watch the tabs; then re-run a finished setup with Terminal → ⬇ | The 🔒 and the dimming **release by themselves in the same session** — no restart, no refresh — and every tab navigates again. A setup that stopped (FAILED / offline) reopens the app too: it must never keep the app paused |
| E7 | 44.1 | ALL | On a phone with the tools already installed: kill, relaunch, watch the first frame | **No pause flashes at launch.** A working prefix reads from disk before the first frame, so a usable phone never sees a lock |

### Round F — projects, not folders (46)

| # | Part | Run on | What to do | PASS looks like |
|---|---|---|---|---|
| F1 | 46.1 | ALL | Projects tab → the big `+` | Sheet titled `New Project` with exactly three rows — `New Project` (`Start from a template`), `Clone Git Repository` (`Import from GitHub, GitLab, Bitbucket`), `Import ZIP` (`From device storage`) — and **no folder row** |
| F2 | 46.1 | ALL | Look for a folder picker anywhere: the hub menus, the editor drawer, the drawer's context switcher | There is none, and no dialog is titled Open folder (grep = 0 is CI-pinned by `FolderImportRemovedTest`; this row is the device half) |
| F3 ★ | 46.2 | ALL | Projects → open a project → tap ONE file in its tree | The editor opens with **that file only**: one tab, and the status bar shows its real path as `~proj/demo_flask/app.py`. No ☰ tree, no git badge, no branch chip |
| F4 ★ | 46.2 | ALL | Edit it, save (and let autosave run), go back to the hub, open it again | The edit is there — the file on disk changed. No whole project loaded behind it |
| F5 | 46.2 | ALL | Card ⋮ → `Open in editor` | The full project editor: tree + tabs bar + git, opened on the project's entry file (launch default → newest source → first source) |
| F6 | 46.2 | ALL | Long-press the path in the status bar; then kill and relaunch after single-file peeks only | The absolute path lands on the clipboard (paste it anywhere to check), and a relaunch opens where the last **project** session left off — a single-file peek never wrote the launch state |
| F7 | 46.2 | D1+D2 | Open a file from project A, then switch context to project B from the drawer's PROJECTS list | B opens its own editor; A's tabs are not shared or reused (the owner's law: two projects are different, so don't open together) |

### Round G — editor chrome that behaves (47)

| # | Part | Run on | What to do | PASS looks like |
|---|---|---|---|---|
| G1 ★ | 47.1 | ALL | Editor → ☰, then tap the ✕ in the header | The drawer closes and **nothing else** happens — no dialog, no navigation, no lost caret. The ✕'s accessibility name is `Close drawer` |
| G2 | 47.1 | ALL | Tap the scrim to close; then open a file from the drawer | Scrim tap closes it; a file tap opens the file and closes the drawer; with the keyboard up, neither loses the buffer or the caret |
| G3 | 47.1 | ALL | Drawer → tap the PROJECTS row | It expands to `Single files` plus every project, the current one marked with a ● — and no navigation happened: the editor is still behind the drawer |
| G4 ★ | 47.1 | ALL | In that list, tap another project | The switch happens **with the list still dropped down** (a pick never closes the drawer — round-2 owner law), and the tree + git badges are the new project's |
| G5 | 47.1 | ALL | Drawer → `＋ New project…` | You land on the **Projects tab with its `+` sheet open** — not on a stale restored tab (device round 1's bug) |
| G6 | 47.1 | D4 | In single-file mode, open the drawer | Its whole surface is the PROJECTS list plus `Guide`: the hint reads `This file is open on its own — one tab, no project tree, no git. Use PROJECTS above to open a whole project, or Guide below for help.` |
| G7 ★ | 47.2 | ALL | Fresh install (Clear data) → open a file and tap into it | The **system** keyboard appears with the extra-keys strip above it; ☰, RUN ▶ and the tabs are all reachable |
| G8 | 47.2 | ALL | Settings → CodeC Keys: read the item, then flip the switch | The item is titled `Dedicated in-app code keyboard` and its subtitle begins `Off by default — CodeC uses your phone's keyboard.` Flipping the switch shows the code keyboard immediately, with ghost-accept, popups, haptics and the space-bar caret drag all working; off again returns the system keyboard with no restart |
| G9 | 47.2 | ALL | Turn CodeC Keys **on**, then install the new build over it (no Clear data) | Still on. A stored choice always beats the new default — and an interactive run (scanf, a REPL) still gets the system keyboard for stdin |

### Round H — nothing hides behind the keyboard (48)

| # | Part | Run on | What to do | PASS looks like |
|---|---|---|---|---|
| H1 ★ | 48.1 | ALL | Open a ~300-line file, scroll to the LAST line, tap into it, open the keyboard | The caret line is fully visible above the keyboard. This is the owner's sentence, verbatim, inverted |
| H2 ★ | 48.1 | ALL | On that line, accept a suggestion (ghost, or a chip) | The caret stays visible; it does not drop behind the keyboard. The second half of the owner's sentence |
| H3 | 48.1 | ALL | Toggle CodeC Keys on/off with the caret near the bottom | Visible both ways, and no jump-scroll to the top of the file |
| H4 | 48.1 | ALL | Expand the Output Panel with the caret near the bottom | The caret stays visible — or the panel is the only thing on screen (both are acceptable; a caret that vanishes is not) |
| H5 | 48.1 | ALL | ⋮ menu → hide, then show, the keys row | The caret stays visible through both changes |
| H6 ★ | 48.1 | ALL | Scroll 100 lines away from the caret, then open the keyboard | The view does **not** jump to the caret. This row proves the policy is narrow enough — a pass on H1–H5 with a failure here is still a failure |
| H7 | 48.1 | ALL | Word wrap ON, one very long line, caret at the end, keyboard opens | Visible after the resize |
| H8 ★ | 48.1 | D1+D2 | Repeat H1–H5 with the **system** keyboard, then with **CodeC Keys** | Same results both ways, on the small screen and the large one |
| H9 ★ | 48.1 | ALL | With CodeC Keys ON, type continuously into a ~300-line file | **No blink.** The code does not flash white/re-layout once per keystroke (device round 1's bug: every replay went through `setText`); colours stay put, the caret keeps its own blink, and the text matches the buffer exactly after 50 keystrokes |

### Round I — back does the obvious thing (49)

| # | Part | Run on | What to do | PASS looks like |
|---|---|---|---|---|
| I1 ★ | 49.1 | ALL | Editor → ☰ → **back** | The drawer closes, the editor is still there, the app does **not** exit |
| I2 ★ | 49.1 | ALL | Same, pressing back within ~200 ms of tapping ☰ | Same — the opening animation must not swallow the press (hypothesis H2, the router keys on the drawer's target value) |
| I3 ★ | 49.1 | ALL | Projects → open a project's file tree → **back** | Back to the **project list**. The app never exits from here (the owner's 4.iv, hub half) |
| I4 | 49.1 | ALL | Edit a file without saving, open the drawer, press back | The **unsaved-changes** dialog appears first — dirty beats every other open surface |
| I5 | 49.1 | ALL | Find bar open → back | The find bar closes, the editor stays |
| I6 | 49.1 | ALL | Output Panel expanded, keyboard down → back | The panel collapses |
| I7 | 49.1 | ALL | Web Preview, Logs, Feedback → back | Each returns to the previous screen |
| I8 | 49.1 | ALL | A bottom sheet or a ⋮ dropdown open → back | It closes, exactly as before the router existed — the router returns `None` for surfaces it does not own |
| I9 ★ | 49.2 | D1+D2 | At the **start** tab, nothing open → back | `Enjoying CodeC? 💚` with `You're closing the app. During testing, every word helps — tell us what you saw, or report a bug.`, the line `tap back again to exit`, and four buttons: `TELL US / REPORT A BUG` (or `SHARE EXPERIENCE` once you tap a star), `GIVE A REVIEW 💚`, `NOT NOW`, `EXIT` |
| I10 ★ | 49.2 | D1+D2 | **NOT NOW**, then back again; separately: back twice quickly | `NOT NOW` stays in the app and outside taps do nothing; the second back exits. Identical on a **fresh install** (start = Projects) and an **upgrade** (start = the last editor file) — that pair was the whole 5.B bug |
| I11 ★ | 49.2 | D1+D2 | Tap Terminal (a non-start tab) → back → back | First back lands on the start tab; **second back shows the prompt**, on every device, 3-button *and* gesture |
| I12 | 49.2 | ALL | Settings → Feedback & Support → turn the exit prompt **off** → back at the root | The app exits directly, no prompt, on every device |
| I13 | 49.2 | ALL | In safe mode (crash-loop start) → back at the root | Exits directly, no prompt. Same when the prompt switch is on: safe mode outranks it |
| I14 | 49.2 | ALL | Settings → Feedback & Support → `Tell us before you go` | The same dialog, on demand — the door that works even on a device where I9/I11 can never fire (a gesture-nav **home** swipe sends no back event at all, so no code can answer it) |
| I15 | 49.2 | ALL | Press back at the root, then read the app log (Settings → Logs) | One `Back` line per press naming the route, the prompt switch, its visibility and the decision — e.g. `root press route=… promptEnabled=… promptVisible=…`. This is the evidence that settles a device report without guessing |

### Round J — the hostile environment (the two destructive cases, plus the OEM ones)

| # | Part | Run on | What to do | PASS looks like |
|---|---|---|---|---|
| J1 ★ | 46.1 | ALL | System settings → Apps → CodeC → Permissions → **revoke** file access (and, if offered, "All files access"), then open a project, edit, save, run, install a package | Nothing changes in Projects / Editor / Packages / Terminal — they are app-private. Only the external-folder affordance is gone. No crash, no permission loop, and `Setup didn't finish` never appears because of it. `MANAGE_EXTERNAL_STORAGE` must never be load-bearing |
| J2 | 44.1 | ALL | Mid-install, on a phone with an aggressive OEM skin (Xiaomi / Oppo / Vivo / Samsung battery saver), leave the app alone for 10 minutes with the screen off | Either the setup finishes or the bar says what is happening. A kill by the battery manager is recovered by round B's boot path — the app never continues with a half prefix and never claims `running` over a dead shell |
| J3 | 44.2 | ALL | Free the device down to ~1 GB, Clear data, launch, let it try | `Setup didn't finish — not enough storage for the Linux tools. Free some space, then open the Terminal tab and tap ⬇.` — a named reason and a next step, never a silent retry loop |
| J4 | 47.2 | ALL | Install the new build **over** an existing one (no Clear data) | Everything you chose is kept: CodeC Keys, theme, projects on disk, the spent guide and tour (nothing re-shows), and the launch default |
| J5 | 45.2 | D4 | With a tour box up, fold/unfold the device or drag the split-screen divider mid-beat | The box follows its anchor or steps aside; the hole is never left over dead space, and the beat is not spent by the resize |
| J6 | 49.2 | ALL | Trigger safe mode (crash twice on start → `TRY WITHOUT MY SETTINGS`), then walk rounds C, D and I | No slides, no tour box, no finish card, and back at the root exits with no prompt — but the app is fully usable, and a normal launch afterwards still shows what it owes |
| J7 | 45.1 | D1 | Set the system font to largest and the theme to LIGHT, then walk the slides, the tour and the setup bar | Every card is fully on screen and legible in both themes; the bar keeps one line; nothing's button is cut off |

## 3. The record

### The per-device sheet (copy this block once per device)

```text
Device:   CodeC <version> · Android <release> (API n) · <model> · <abis>   ← COPY REPORT line 1
Class:    D1 | D2 | D3 | D4        Nav mode: 3-button | gesture     RAM: __ GB     Date: 2026-__-__
Build:    CI run 34744496605  /  commit 62cfe7b   (or the run you actually installed)
Pass:     FULL | 20-minute

A1 ✅  A2 ❌ "the bar's number went back to 0 after I came back to Terminal"   …
I9 ❌  "no dialog at all, the app just closed"   → owner: 49.2, log line pasted below
```

Then, **for every ❌**: which part owns it (the row's `Part` column), and either
the fix's commit or a dated deferral with a reason. One log line or one
screenshot name per failure is worth ten adjectives.

### The 20-minute pass (run this on a second device, then stop)

For every device **after** the first, these 24 rows are what cross-device
testing means; the rest of the matrix is the primary device's full pass.

```text
A1 A5 A6 A9 A11 · B1 B5 · C1 C5 · D2 D3 D7 D9 · E1 E6 · F3 F4 · G1 G4 G7 ·
H1 H2 H6 H9 · I1 I3 I9 I11 · J1
```

### The results, as they arrive (this table is the phase's deliverable)

| Device | Class | Nav | Build | Rows run | ✅ | ❌ | Report |
|---|---|---|---|---|---|---|---|
| *not run yet* | — | — | — | — | — | — | — |

**PASS for Phase 50 = the table above has ≥ 2 devices, including one gesture-nav
and one 3-button-nav, with every ❌ either fixed or explicitly deferred — and
each of the eleven part files from 44–49 carrying its own `## Test log` filled in
the same commit as the fix it proves.** The `## Test log` sections exist and are
row-for-row pinned by `DeviceMatrixTest` (see §4); what they lack is results.

## 4. Why the runbook cannot rot (the headless half)

A stale runbook is not a small problem: **Phase 44's device round 1 was run
against four rows that quoted sentences the app had never had**, and the whole
round was spent discovering that. So the runbook is now a CI-checked artifact.

`app/src/test/java/com/codeci/ide/DeviceMatrixTest.kt` (plain JVM, reads the real
repo tree through `RepoFiles`, the same mechanism `SettingsAuditTest` uses for the
Settings audit) pins:

| Pin | What it means for this file |
|---|---|
| row shape | every row id is `<round letter><number>`, contiguous from 1, unique, and its `Part` is one of the eleven real part files |
| **verbatim truth** | every back-ticked span in a `PASS looks like` cell **exists as an app string** — resolved against `strings.xml` values and the production Kotlin literals (concatenations joined, comments stripped, `${…}` and `%d` treated as holes, `NN` / `…` as the doc's own wildcards). A span that is not on-screen text is exempt by shape — a path, a shell command the tester types, a file name, a test class, a source identifier — and that exemption list is the whole file's only escape hatch, so it is visible in review. Re-word a sentence in the app without updating its row, or write a row from memory, and CI goes red |
| coverage floor | each of the **eleven** part files owns ≥ 3 rows, and ≥ 30 rows carry a corpus-verified sentence — so the runbook stays mostly verbatim rather than mostly prose |
| classes | the `Run on` column uses only real class names; D1–D4 each appear ≥ 3×; the exit-prompt rows name both nav classes |
| paste-back law | every row appears in **exactly one** owning part file's `## Test log`, and every row listed there appears back here — so "we'll write the results up later" cannot survive a push |
| honest limits | the destructive pair the roadmap demanded is present (a kill mid-install and a revoked file access), and no row is marked ✅ in this file's results table without a device line above it |

That is the whole headless half, and it is deliberately the only half. **The two
other options in the plan were measured and rejected here, not deferred
silently:**

- **Roborazzi screenshot tests for the static chrome** (the setup bar in each
  verdict state, the tour overlay's geometry, the single-file status bar, the
  drawer's PROJECTS section). The dependency is already declared and applied —
  but a golden recorded in a headless JVM window measures **Robolectric's**
  layout, not a phone's, and the four surfaces it would cover are *already*
  pinned by the strings and behaviour tests above (`SetupGatePolicyTest`,
  `TooltipPlacementTest`/`ViewportSnapshotTest`, `DrawerProjectListTest`). What
  those cannot reach is exactly what a Robolectric window also cannot reach: a
  real IME resize, a real hole over a real control. **Not built — say the word
  and it is one small follow-up.**
- **A Robolectric back-stack test.** Not needed: 49 shipped the stronger version
  — `BackRouterTest` (the table), `BackRouterRootTest` (`isRoot` over every real
  route shape) and `BackHandlerWiringTest` (every `BackHandler(` in the app is the
  root or router-driven), which is the property a UI test would re-derive.

## 5. What this matrix cannot prove (kept in the file so nobody over-reads a pass)

- **Nothing about a home swipe.** A gesture-nav home swipe and a Recents swipe
  send no back event at all, so no code can show a prompt there — 49.2's cause C.
  I9–I15 test the **back** path and the **second door**. That is the whole claim.
- **No emulator, no device, no Gradle in the agent sandbox.** Rounds A, B, E, H
  and I are physically unrunnable here; `rule.md` §5 calls that a **device pass
  required**, and `n/a` is an honest answer where a device class is missing.
- **Battery-manager kills are not reproducible on demand.** J2 is the closest
  proxy; a real answer needs an aggressive OEM skin and ten idle minutes.
- **Host tests never excuse a device row.** The 44–49 suites (200+ cases, green
  on CI) check *pure policy*: they prove `barText` returns that sentence, not
  that a finger saw it above a keyboard.
- **The `★` rows are the ones where the answer is expected to differ between
  devices.** A one-device matrix is a Phase 49 bug report waiting to happen.

## Sources

- The phases' own records, which this file re-reads rather than re-invents:
  [`../chat-phase44/DEVICE_ROUND.md`](../chat-phase44/DEVICE_ROUND.md) (R1-R8,
  D1-D12), [`../chat-phase45/DEVICE_ROUND.md`](../chat-phase45/DEVICE_ROUND.md)
  (G1-G41), [`../chat-phase46/README.md`](../chat-phase46/README.md),
  [`../chat-phase47/README.md`](../chat-phase47/README.md),
  [`../chat-phase48/README.md`](../chat-phase48/README.md) (its eight checks) and
  [`../chat-phase49/README.md`](../chat-phase49/README.md) (its ten, plus 49.2's
  eight). Every row above is a **selection plus a correction** of those lists: the
  corrections are the quoted strings (re-read from the shipped sources on
  2026-09-13) and the rows the plans predated (B3/B4, E1–E7, D2–D5, F7, G4, G9,
  H9, J1–J7).
- `ui/utils/DeviceDiagnostics.kt` + `ui/support/FeedbackDraft.kt` — the identity
  line §0 asks for instead of a guess about the model.
- `docs/PHASE44_50_ROADMAP.md` §"Phase 50" (the class list and the two destructive
  cases this file must contain), `docs/BETA.md` (the record standard a beta round
  is held to), `rule.md` §3 (CI as the executor of record), §5 (device pass
  required), §8 (verification law), §4.2 (evidence before change).
- `app/src/main/java/com/codeci/ide/ui/terminal/SetupState.kt`
  (`SetupGatePolicy.barText` / `refusal` / `dontCloseText` / `notificationText`,
  `SetupLockPolicy.sweetMessage`), `ui/terminal/TerminalUx.kt` (the chip),
  `ui/guide/GuidePlan.kt` + `ui/guide/CoachMarkPlan.kt` (slides and eleven beats),
  `ui/screens/FileManagerScreen.kt` (the `+` sheet, the hub menu, the hub's back
  handler), `ui/components/EditorProjectDrawer.kt` + `ui/editor/DrawerPolicy.kt`
  (✕, PROJECTS, the pick), `ui/screens/SettingsScreen.kt` (CodeC Keys copy, the
  guide doors, `Tell us before you go`) and `ui/support/ExitFeedbackDialog.kt`
  (the prompt) — the corpus every quoted span in this file was checked against,
  and the one `DeviceMatrixTest` checks it against on every push.
