# CodeC Phase 51 — device round (F1-F16)

> **Status:** ✅ RUN — **owner report 2026-09-21: ALL PASS** (F1-F16 + the four
> regression rows F17-F20), on the CI artifact of run `35631916639`/`35630471779`
> (tip `feebab5` / `32c7c70`); merge commanded by the owner in the same message
> (*"All pass record and merge"*), [PR #82](https://github.com/pabi277/CodeC/pull/82).
> The per-row record is in each owning part doc's `## Test log (Phase 51 — the
> feel)` section, and the regression rows' record is in the phase
> [`README.md`](README.md) §Test log.
>
> Same law as every round:
> `rule.md` §5 — the sandbox has no device, no emulator, no IME. CI proves the
> policies; **you** prove the feel. Paste each row's result into the owning part
> doc under a `## Test log (Phase 51 — the feel)` heading.
>
> **Concurrent work (2026-09-13):** Phase 53's cross-device matrix is 🚧
> IMPLEMENTED on `arena/01a099d8-codec` — `DeviceMatrixTest.kt` (627 lines)
> pins ten rounds A-J for **44.1-49.2 only** (a fixed `PART_FILES` map), CI ✅
> `69a70ec` — and the owner then **cancelled it outright** (2026-09-14:
> *"Remove the full device cross check phase"*). Nothing in 50-52 is scanned
> by that test today, and this round is not blocked by it;
> the format below is kept because it is a good record, not because anything
> machine-reads it, and the `## Test log (Phase 51 — the feel)` heading are already the right shape.
>
> The log table is `# | Part | Run on | What to do | PASS looks like` — five columns,
> one row per result, one part doc per row.
>
> **Build:** the CI `Build APK` artifact of the phase-51 branch
> (`CodeC-IDE-debug`, installed **over** the current install — no data wipe).
> Write the run id + version name (Settings → About) at the top of your report.


**Record format:** (Phase 53's exact format, so the rounds stay machine-checkable:) `F<n> — device / OS / theme / result (PASS|FAIL) / one
sentence (or a photo).`

| # | Row | Owning part | How to check |
|---|---|---|---|
| F1 | **Cold start is CodeC, not black.** Force-stop the app, tap the icon: the first frame is the mark on the brand colour | 51.1 | Settings → Apps → CodeC → Force stop, then launch. Do it on a dark-mode phone *and* a light-mode one |
| F2 | The splash goes away as soon as the app is ready — no waiting, no second of logo | 51.1 | launch five times; time it with your eye (52.2's R4 times it with a stopwatch) |
| F3 | After a **crash** the crash report is not hidden behind the splash | 51.1 | (only if you can trigger one; otherwise mark N/A — do not fake it) |
| F4 | The welcome screen: the three tiles are clearly tiles, each with its language colour, and each says what happens next | 51.1 | first run, or clear data if you accept a fresh install |
| F5 | **RUN ▶ is the most obvious thing on the editor** — you find it without looking, with the keyboard up and down | 51.2 | open a file; hold the phone at arm's length; then do it with the keyboard open |
| F6 | RUN ▶ still shows the lock sentence when an install is running, and never runs anyway | 51.2 | start a Python install, tap RUN ▶ (Phase 44's row, re-checked) |
| F7 | With **no file open** the editor shows a designed empty state with one action, and that action opens something real | 51.2 | close every tab |
| F8 | The chrome (tabs / find / status bar) has even gaps and does not move when the keyboard opens | 51.2 | type, search (find bar), close it, type again |
| F9 | Saving gives one short confirmation and nothing else | 51.2 | edit + save, three times |
| F10 | The hub's **empty** state is a designed screen, not a sentence | 51.3 | delete or move every project (or a fresh install) |
| F11 | The hub's **loading** state is never a blank frame — you see the list's shape before the names arrive | 51.3 | cold open straight into the hub on a phone with many projects |
| F12 | Installing a package **finishes** visibly (state change + one line), and does it exactly once | 51.3 | install a small package; watch the row; rotate the phone mid-install and confirm it does not celebrate twice |
| F13 | The terminal's first frame says whether the tools are ready, and agrees with the setup bar | 51.3 | Terminal tab on a fresh install; compare with the bar's words |
| F14 | **Haptics:** you feel one tick when a program finishes, a firm one when it fails, and one when a file saves — and nowhere else | 51.4 | run a hello world, then a program that errors, then save |
| F15 | Settings → Appearance → **Haptics off** = total silence, except the CodeC keyboard's own setting | 51.4 | turn it off, repeat F14 |
| F16 | The CodeC keyboard's haptics are unchanged by all of the above | 51.4 | Settings → Editor → CodeC Keys → haptics (Phase 47.2's row) |

**Regression rows** (52 repaints surfaces that earlier phases device-tested):

| # | Row | Since |
|---|---|---|
| F17 | The last line of a long file stays above the keyboard; no blink while typing | 48 |
| F18 | Back behaves: drawer closes, hub tree closes, exit prompt appears at the root | 49 |
| F19 | The 11-beat tour still walks first-to-last with one tap per beat | 45 |
| F20 | Two projects never share one editor tab | 46 |

**If a row fails:** report the sentence you saw and the screen — not a
conclusion. Fix = a failing test first, then the change, then a re-round on a
**new** build.
