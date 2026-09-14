# CodeC Phase 52 — device round (F1-F16)

> **Status:** 📋 WRITTEN, **NOT RUN.** Same law as every round:
> `rule.md` §5 — the sandbox has no device, no emulator, no IME. CI proves the
> policies; **you** prove the feel. Paste each row's result into the owning part
> doc under a `## Device round` heading.
>
> **Build:** the CI `Build APK` artifact of the phase-52 branch
> (`CodeC-IDE-debug`, installed **over** the current install — no data wipe).
> Write the run id + version name (Settings → About) at the top of your report.

**Record format:** `F<n> — device / OS / theme / result (PASS|FAIL) / one
sentence (or a photo).`

| # | Row | Owning part | How to check |
|---|---|---|---|
| F1 | **Cold start is CodeC, not black.** Force-stop the app, tap the icon: the first frame is the mark on the brand colour | 52.1 | Settings → Apps → CodeC → Force stop, then launch. Do it on a dark-mode phone *and* a light-mode one |
| F2 | The splash goes away as soon as the app is ready — no waiting, no second of logo | 52.1 | launch five times; time it with your eye (53.2's R4 times it with a stopwatch) |
| F3 | After a **crash** the crash report is not hidden behind the splash | 52.1 | (only if you can trigger one; otherwise mark N/A — do not fake it) |
| F4 | The welcome screen: the three tiles are clearly tiles, each with its language colour, and each says what happens next | 52.1 | first run, or clear data if you accept a fresh install |
| F5 | **RUN ▶ is the most obvious thing on the editor** — you find it without looking, with the keyboard up and down | 52.2 | open a file; hold the phone at arm's length; then do it with the keyboard open |
| F6 | RUN ▶ still shows the lock sentence when an install is running, and never runs anyway | 52.2 | start a Python install, tap RUN ▶ (Phase 44's row, re-checked) |
| F7 | With **no file open** the editor shows a designed empty state with one action, and that action opens something real | 52.2 | close every tab |
| F8 | The chrome (tabs / find / status bar) has even gaps and does not move when the keyboard opens | 52.2 | type, search (find bar), close it, type again |
| F9 | Saving gives one short confirmation and nothing else | 52.2 | edit + save, three times |
| F10 | The hub's **empty** state is a designed screen, not a sentence | 52.3 | delete or move every project (or a fresh install) |
| F11 | The hub's **loading** state is never a blank frame — you see the list's shape before the names arrive | 52.3 | cold open straight into the hub on a phone with many projects |
| F12 | Installing a package **finishes** visibly (state change + one line), and does it exactly once | 52.3 | install a small package; watch the row; rotate the phone mid-install and confirm it does not celebrate twice |
| F13 | The terminal's first frame says whether the tools are ready, and agrees with the setup bar | 52.3 | Terminal tab on a fresh install; compare with the bar's words |
| F14 | **Haptics:** you feel one tick when a program finishes, a firm one when it fails, and one when a file saves — and nowhere else | 52.4 | run a hello world, then a program that errors, then save |
| F15 | Settings → Appearance → **Haptics off** = total silence, except the CodeC keyboard's own setting | 52.4 | turn it off, repeat F14 |
| F16 | The CodeC keyboard's haptics are unchanged by all of the above | 52.4 | Settings → Editor → CodeC Keys → haptics (Phase 47.2's row) |

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
