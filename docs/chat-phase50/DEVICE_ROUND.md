# CodeC Phase 50 — device round (L1-L12)

> **Status:** 📋 WRITTEN, **NOT RUN.** Per `rule.md` §5: nothing here can be
> verified in the agent sandbox (no device, no emulator, no IME). CI proves the
> pure policies; **this round proves the look**. Paste each row's result back
> into the owning part doc (`PART_50_x_*.md`) under a `## Test log (Phase 50 — the look)`
> heading — the convention Phase 53 pinned with `app/src/test/java/com/codeci/ide/DeviceMatrixTest.kt`,
> not into a scratchpad.
>
> **Concurrent work (2026-09-13):** Phase 53's cross-device matrix is 🚧
> IMPLEMENTED on `arena/01a099d8-codec` — `DeviceMatrixTest.kt` (627 lines)
> pins ten rounds A-J for **44.1-49.2 only** (a fixed `PART_FILES` map), CI ✅
> `69a70ec` — and the owner then **cancelled it outright** (2026-09-14:
> *"Remove the full device cross check phase"*). Nothing in 50-52 is scanned
> by that test today, and this round is not blocked by it;
> the format below is kept because it is a good record, not because anything
> machine-reads it, and the `## Test log (Phase 50 — the look)` heading are already the right shape.
>
> The log table is `# | Part | Run on | What to do | PASS looks like` — five columns,
> one row per result, one part doc per row.
>
> **Build to test:** the CI `Build APK` artifact of the phase-50 branch
> (`CodeC-IDE-debug`, installed **over** the current install — no data wipe).
> Record the run id and the version name (Settings → About) at the top of your
> report; a row reported from the wrong build is not evidence.


**How to record:** (Phase 53's exact format, so the rounds stay machine-checkable:) `L<n> — device / OS / theme (dark|light) / result (PASS|FAIL) /
one sentence of evidence (photo or the exact words on screen).`

| # | Row | Owning part | How to check |
|---|---|---|---|
| L1 | On a **small** phone (≤5.8"), the six core screens' gaps look even — no screen is visibly tighter than its neighbour | 50.1 | Hub → Editor → Packages → Terminal → Settings, one after another, and look only at the margins |
| L2 | On a **large** phone (≥6.5") the same, and nothing looks stretched | 50.1 | same walk; the tile/card widths should not fill the screen edge-to-edge differently between screens |
| L3 | Corners match: every card, chip and sheet on the hub and in Settings uses the same radius | 50.1 | Hub cards vs the `+` sheet vs Settings cards |
| L4 | Every tap target feels the same size: tap near the **edge** of a toolbar icon, not its centre — the action still fires | 50.1 | Editor toolbar, hub card ⋮, Settings rows |
| L5 | **Dark theme:** CodeC's own colour is visible — the app no longer looks like the wallpaper | 50.2 | Editor → RUN ▶, the tab bar, a Settings switch |
| L6 | **Light theme:** same, and no text is washed out | 50.2 | Settings → Appearance → theme = Light; read every label |
| L7 | **Android 12+ with "Match my wallpaper" OFF** (the default): brand colour, not wallpaper colour | 50.2 | Settings → Appearance |
| L8 | **Android 12+ with "Match my wallpaper" ON**: the wallpaper palette is used, and nothing becomes unreadable | 50.2 | toggle it, then read the Settings body text and the RUN ▶ button |
| L9 | The welcome screen's "CodeC" headline reads as a headline — clearly bigger and heavier than the line under it | 50.3 | first run, or Settings → About → Reset tips + clear data only if you accept a fresh install |
| L10 | A code path (editor status bar, a diff, an output line) is in a **monospace** face and does not jump when it changes | 50.3 | open a file, look at `~proj/…`; run something, look at the output |
| L11 | Switching bottom tabs **fades** instead of snapping, and is still fast enough to feel instant | 50.4 | tap Editor ↔ Terminal ↔ Hub ten times |
| L12 | Expanding/collapsing the output panel animates, and the caret does **not** jump while the keyboard is open | 50.4 | type a line, open the panel, close it, type again (Phase 48's row, re-checked) |

**Regression rows (from earlier phases — re-run because 51 touches their chrome):**

| # | Row | Since |
|---|---|---|
| L13 | The last line of a long file stays above the keyboard (48) | 48.1 |
| L14 | Back from the hub's open project tree closes the tree, not the app (49) | 49.1 |
| L15 | RUN ▶ still runs the open file and the chrome lock still says why when it refuses (44/45) | 44.1 |

**If a row fails:** report the sentence you saw and the screen, not a
conclusion. Phase 50's own history rule (from 44-49): the first fix is a test
that fails without the fix, then the fix, then a re-round on a **new** build.
