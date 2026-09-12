# CodeC Phase 45.1 — The first-run guide, and three ways back to it

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"Set a step by step user guide after opening the app
> 1st time with a open view again[ing]"*

## What ships

A `GuideScreen` (Compose, no new dependency, no new navigation model) shown
**once** between the Phase 33.1 welcome tiles and the shell, plus three
"View the guide" entry points so the owner's *"open view again"* is real.

## The five slides (content is the deliverable)

Content, not chrome, is what makes a guide worth its screen. Each slide is one
idea, ≤ 22 words of body, and names a control the user will see within a minute.
The order is the order the user meets the features — verified against the actual
navigation (`MainActivity.kt:632-641`: Projects · Editor · Terminal · Packages ·
Settings).

| # | Title | Body (≤ 22 words) | Action button |
|---|---|---|---|
| 1 | **Your files live in the ☰ menu** | Tap ☰ in the editor for the file tree. Tap the project name at the top to switch projects. | GOT IT |
| 2 | **RUN ▶ compiles and runs** | Output appears at the bottom of the same screen. **C works offline — no setup, no download.** | GOT IT |
| 3 | **One download, one time** | Python, Node and the Linux tools download once on first use. **Keep CodeC open while it finishes.** | GOT IT |
| 4 | **A real terminal** | The Terminal tab is a Linux shell: `pkg install`, `git`, `cc`. Its status chip tells you what it is doing. | GOT IT |
| 5 | **Projects vs single files** | In Projects, tap a file to edit just that file. Use the card's ⋮ → *Open in editor* for the whole project. | START CODING |

Slide 3 exists because of Phase 44; slide 5 exists because of Phase 46; slide 1
exists because the owner's row said users cannot find the project switch. **If
44/46 are not shipped yet when 45.1 lands, slides 3 and 5 keep their text** —
they describe the intended product, and both phases are in the same series.
(Recorded so a reviewer does not "fix" the copy to match a half-shipped build.)

The owner's *"the tap to the open down side of the keyboard"* (the hidden tab
bar) is a **coach mark**, not a slide: it is a control, and coach marks are where
controls get explained (45.2, Terminal/Editor step "Show tabs").

## Design

### 1. Pure plan

```kotlin
// ui/guide/GuidePlan.kt   (pure, host-tested)
data class GuideSlide(val id: String, val title: String, val body: String, val actionLabel: String)
object GuidePlan {
    val slides: List<GuideSlide>            // the five above, in order
    fun at(index: Int): GuideSlide?
    fun canSkip(index: Int): Boolean = true // SKIP on every slide, including the first
    fun next(index: Int): Int               // last -> Done
    fun isLast(index: Int): Boolean
}
```

The screen is a `Column` with the slide content, a `LinearProgressIndicator`
(5 steps), `SKIP` (top-right, always) and `NEXT`/`START CODING`. No pager
library, no images required (the mark `app_mark` from Phase 38.1 is the only
art), so it renders identically in both themes and in safe mode.

### 2. Where it is shown, and the one ordering rule

`MainApp` already gates the shell on `firstLaunchComplete`
(`MainActivity.kt:669-700`). The guide is a **second** gate after it:

```text
firstLaunchComplete == false  -> WelcomeScreen (tiles)
guide_completed == false      -> GuideScreen            <-- new
otherwise                     -> the shell (Terminal-first while setup runs, 44.1)
```

**Ordering rule:** the guide comes *before* the setup-first-terminal behaviour
from 44.1. Rationale: the guide explains the download that the terminal is about
to show, and a user who has not been told what the app is should not be dropped
into a shell watching a progress bar. Both are first-launch-only, so this costs
one launch's worth of order.

`guide_completed` is written when the user reaches `START CODING` **or** taps
`SKIP` — never on a timer, never implicitly.

### 3. The three ways back (the owner's "open view again")

| Entry point | Where in code | Notes |
|---|---|---|
| Settings → **Help & guide** | new row in the existing "About" section (`SettingsScreen.kt:914`) or a new section — see the audit note below | also carries **Reset tips** (45.2's coach marks) |
| Projects hub **⋮** | `FileManagerScreen.kt:330-345` (the hub overflow already lists New project / Refresh / Export all / Import backup) | one `DropdownMenuItem`, no dialog |
| Editor **☰ drawer footer** | `EditorProjectDrawer.kt` footer rows (next to Source Control / Switch Branch) | where a user who is *lost in the editor* will look |

Each renders the same `GuideScreen` with `guide_completed` already true, so
finishing or skipping it changes nothing.

**Audit constraint (must be handled in the same commit):** adding a
`SettingsItem`/`SettingsAction` call site changes the control count that
`SettingsAuditTest` compares against
`docs/chat-phase38/SETTINGS_AUDIT.md`, and adding a `SettingsSectionHeader`
changes the section list. Either update the audit table (preferred: one row in
the existing "About" section — no new section) or the build fails. The test
resolves `stringResource` titles from `strings.xml`, so the new strings must
exist too.

### 4. Persistence

`SettingsManager` gains `guide_completed` (Boolean, default `false`). One key,
one flow, one setter, and the readers are `MainApp` + `GuideScreen` — which
satisfies `SettingsKeysHaveReadersTest` (key → flow → out-of-store reader).

## Exit condition

```text
1. Fresh install: tiles -> guide (5 slides, progress bar, SKIP on slide 1) ->
   shell. SKIP and DONE both persist.
2. Relaunch: no guide.
3. All three entry points open the guide at slide 1; leaving it changes nothing.
4. Upgrade path (flag absent): shown exactly once, never while the 44.1 setup
   bar has an active download on screen.
5. Dark theme, light theme, safe mode, and a 5" screen: readable, no clipping,
   SKIP always reachable.
PASS = all five.
```

## Tests (plan)

- `GuidePlanTest`: five slides, unique ids, every title/body non-empty and under
  the length caps, `canSkip(0) == true`, `next()` terminates, `at(5) == null`,
  and the **vocabulary pin** (no slide may name a control/feature outside a fixed
  allow-list — the drift guard, same idea as `SettingsAuditTest`).
- `GuideFlagsTest`: `guide_completed` defaults false; the setter writes; the
  reader chain exists outside the store (duplicates the
  `SettingsKeysHaveReadersTest` intent locally so the failure message is
  actionable).
- Updated `SettingsAuditTest` fixtures + `docs/chat-phase38/SETTINGS_AUDIT.md`
  row(s) in the same commit.

## Sources (record)

- CodeC 2026-09-12: `MainActivity.kt:632-641,669-700`, `WelcomeScreen.kt`,
  `SettingsScreen.kt:914,1168`, `EditorProjectDrawer.kt` (footer rows),
  `FileManagerScreen.kt:330-345`, `SettingsManager.kt:79,156-157`,
  `docs/chat-phase38/SETTINGS_AUDIT.md`, `SettingsAuditTest.kt:20-60`,
  `SettingsKeysHaveReadersTest.kt:30-40`.
- `docs/PHONE_UX_ANALYSIS.md` §6 (what a first session must teach).
- `docs/PHASE44_50_UX_RESEARCH.md` §3.1 (dependency survey; the hand-rolled
  decision), §3.3 (the two rules).

## Deferred / rejected with reasons

- **Animated illustrations per slide** — real art is a design cost and an APK
  cost; the copy carries the model, and `app_mark` is enough identity. Revisit
  only if the device round says users skip past the text.
- **A searchable in-app help centre** — that is documentation, and
  `docs/TROUBLESHOOTING.md` already exists for the agent/owner; a phone IDE does
  not need a second copy of it in-app.
- **Video/GIF walkthroughs** — size and load time on the exact devices CodeC
  targets.
- **Showing the guide again after a crash-loop safe start** — safe mode exists to
  reduce startup work (`SafeModeBanner`, `StartupLedger`); a guide is the
  opposite of that.
