# CodeC Phase 53 — device round (R1-R12)

> **Status:** 📋 WRITTEN, **NOT RUN.** `rule.md` §5 applies: no device, no
> emulator, no IME in the sandbox. CI proves the policies and the goldens; **you**
> prove the return and the feel. Paste each row's result into the owning part
> doc under a `## Test log (Phase 53 — the return)` heading.
>
> **Concurrent work (2026-09-13):** Phase 50's cross-device matrix is 🚧
> IMPLEMENTED on `arena/01a099d8-codec` — `DeviceMatrixTest.kt` (627 lines)
> pins ten rounds A-J for **44.1-49.2 only** (a fixed `PART_FILES` map), CI ✅
> GREEN `34753000709` tip `69a70ec`. Nothing in 51-53 is scanned by it today;
> if the owner ever wants these rows machine-pinned the same way, the map and
> the `## Test log (Phase 53 — the return)` heading are already the right shape.
>
> The log table is `# | Part | Run on | What to do | PASS looks like` — five columns,
> one row per result, one part doc per row.
>
> **Build:** the CI `Build APK` artifact of the phase-53 branch
> (`CodeC-IDE-debug`, installed **over** the current install — no data wipe, so
> the streak and the last-file state survive). Record the run id + version name
> (Settings → About) at the top of your report.


**Record format:** (Phase 50's exact format, so the rounds stay machine-checkable:) `R<n> — device / OS / result (PASS|FAIL) / one sentence (or
a photo).`

| # | Row | Owning part | How to check |
|---|---|---|---|
| R1 | Open the app the **next day**: the hub offers *"pick up where you left off"* with the real file path | 53.1 | leave the app closed overnight; open it the next morning |
| R2 | **Continue** goes to that file; **✕** goes to the project list | 53.1 | both buttons, in one session |
| R3 | After ✕, the same session never asks again | 53.1 | close the hub, reopen it, navigate around |
| R4 | Reopen within a **minute** of leaving → the app jumps straight back, no question | 53.1 | switch apps, come back |
| R5 | **Cold start timing:** force-stop, then tap — stopwatch from the tap to a usable screen. Write the number | 53.2 | three tries, write all three. Cross-check Settings → Developer Options → View App Logs (`Launch firstFrameMs=`) |
| R6 | **Warm start** timing, same stopwatch | 53.2 | back out with the home button, re-enter |
| R7 | Scroll a **large** file (5,000+ lines) — no stutter | 53.2 | and then the same on the bench APK if you have it installed |
| R8 | Type for 30 seconds in a large file — no lag, no blink | 53.2 | with the system keyboard **and** with CodeC Keys (Phase 48's row, re-checked) |
| R9 | **Settings → About** shows *"N days in a row · N runs · N files"* | 53.3 | after at least two days of use |
| R10 | Nothing anywhere mentions a **broken** streak, and no notification ever appears | 53.3 | skip a day, then come back and look everywhere |
| R11 | The **final walkthrough** (`PART_53_4` §C, beats 1-8) on a **fresh install** | 53.4 | uninstall → install → walk it |
| R12 | Dark **and** light theme, on the smallest and largest phone you have: nothing is unreadable or clipped | 51/52 | the four combinations, hub + editor + Settings |

**Regression rows** (53 surfaces state that earlier phases own):

| # | Row | Since |
|---|---|---|
| R13 | Setup still diverts to the Terminal when the tools are not ready | 44 |
| R14 | Back still behaves (drawer, hub tree, exit prompt) | 49 |
| R15 | The tour still runs first-to-last with one tap per beat | 45 |
| R16 | Installs finish once, with one message, and the lock never lies | 44/52.3 |

**How to report a failure:** the sentence you saw, the screen you were on, and
the build id. Fix = a failing test → the change → a re-round on a **new**
build. Never re-test on the same APK you just reported a bug against.
