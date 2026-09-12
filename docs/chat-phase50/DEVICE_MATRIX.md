# Phase 50 — cross-device matrix (owner runbook)

> **Branch:** `arena/01a094e5-codec` · **Functional round:** *TBD — filled in
> when the phase's implementation commits exist* · **CI ✅ GREEN:** *TBD*
> (`Build APK` run ID, per `rule.md` §3 — no runbook is worth anything on a red
> build) · **Status:** 📋 PLANNED — the rows below are the *procedure*; the
> results get pasted back into each part file's `## Test log`.
>
> Same rule as Phases 40 and 41 (`docs/chat-phase40/DEVICE_TEST_PLAN.md`,
> `docs/chat-phase41/DEVICE_TEST_PLAN.md`): **every row has a "PASS looks like"
> line with the exact on-screen text.** If the screen says something different,
> copy the text verbatim and say which row it was — that is the bug report.

## 0. Before you start (2 minutes per device)

1. **Get the APK** — GitHub → **Actions** → latest green `Build APK` on
   `arena/01a094e5-codec` → **Artifacts** → **`CodeC-IDE`** → unzip →
   `app-debug.apk` → install (allow *Install unknown apps*).
2. **Identify the device without guessing.** Settings → **Feedback &
   Support** → **COPY REPORT** → paste anywhere → **line 1** is
   `CodeC <version> · Android <release> (API n) · <model> · <abis>`
   (`FeedbackDraft.kt:145-149`, `FeedbackSectionCard.kt:132-133`). Paste that
   line as the header of your results. It is the only device identity this
   matrix trusts.
3. **Note the navigation mode** — Settings → System → Gestures: *3-button* or
   *Gesture*. Rows F1-F10 depend on it and mean different things on each.

## The four device classes

At least **class 1 and class 2** are mandatory; 3 and 4 if a device is at hand.

| Class | Shape | Why it is in the matrix |
|---|---|---|
| **D1** | Small gesture-nav phone (≤6.1", ≤4 GB RAM) | The caret/keyboard rows (E) and the coach-mark geometry (B) are worst here; low RAM is where an interrupted setup (A) happens |
| **D2** | Large phone, **3-button navigation** | The back rows (F) behave differently under 3-button nav — and the exit-prompt row is the owner's own 5.B |
| **D3** | Android **13+** device | `POST_NOTIFICATIONS` is a runtime permission there (`MainActivity.kt:429-441`), and FGS rules are strictest — the setup bar (A) must still be visible |
| **D4** | Tablet / foldable / low-end | Wide layouts: the drawer (D), the single-file status bar (C), the setup bar's one-line rule (A) |

## Round A — setup is visible and cannot half-finish (Phase 44)

| # | What to do | PASS looks like |
|---|---|---|
| A1 | Fresh install, stay on the Projects tab, do **not** open Terminal | The setup bar reads **"Preparing Python (1/3) · 0 %"** and the percentage moves |
| A2 | Let it finish without touching anything | **"Ready ✓"**; Terminal shows a prompt; `pkg --version` prints a version |
| A3 | During the download, **force-stop the app** (or reboot the phone), reopen | A **repair** notice appears, then the download **continues from where it stopped** (the % does not restart from 0 on a resumed attempt) |
| A4 | After A3, open Terminal | No `pkg: not found`. If still offline, the bar says **"Offline — setup paused · Retry"**, not "Ready" |
| A5 | Turn on airplane mode mid-download, wait, turn it off | **"Offline — setup paused"** while offline; it resumes on its own or on **Retry** |
| A6 | Packages tab **during** setup | Tapping an install card says **"Setup is running — open Terminal to watch it"** instead of silently typing into a dead shell |
| A7 | Android 13+ (class D3): deny the notification permission when asked | The **in-app bar still shows progress** (the notification is a bonus, not the only signal) |
| A8 | Any device: leave the app in the background during the download for 10 minutes | Setup either finishes or says **Paused** — it does not silently die with "Ready" never appearing |

## Round B — the guide (Phase 45)

| # | What to do | PASS looks like |
|---|---|---|
| B1 | Fresh install (clear app storage first) | The **5-slide guide** appears before anything else; **SKIP** is on every slide |
| B2 | Swipe through all 5 | Slide order: ☰/files → RUN ▶ → the one-time download → Terminal → Projects vs single file |
| B3 | Close the guide, then reopen it from **all three** doors | Settings → **Help & guide**; Projects ⋮ → **Help & guide**; Editor ☰ drawer footer → **Help & guide** |
| B4 | Open Terminal for the first time | A coach mark on **one** element only (≤2 per surface), with **NEXT**/**SKIP** |
| B5 | Reopen Terminal | **No coach mark** — coach marks never repeat |
| B6 | Rotate / resize the window with a coach mark up (class D4) | The mark follows its anchor or disappears — it never floats over the wrong control |
| B7 | Force-stop the app with a coach mark on screen, reopen | The surface is **not** marked as seen (the mark can appear again) — a crash never burns a step |

## Round C — projects, not folders (Phase 46)

| # | What to do | PASS looks like |
|---|---|---|
| C1 | Projects tab → **+** sheet | Rows: **New project**, **Import ZIP**, **Import file**, **Clone from URL** — and **no "Open folder"** row |
| C2 | Projects tab → card ⋮ menu | Contains **Open in editor** (full project), plus the existing Source control / Switch branch / Pull / Push / Copy remote URL / Export / Share ZIP / Rename / Delete |
| C3 | **Single tap a file** in an open project's tree | The **editor opens with that one file**; the status bar shows its **real path** (`~proj/…` style) |
| C4 | Edit + save in that single-file editor, then go back | The file on disk changed; no whole-project load happened (no project tabs bar) |
| C5 | Card ⋮ → **Open in editor** | The full project editor, with the file drawer and the tabs bar — unchanged from today |
| C6 | Search the whole UI for the words "open folder" | **Zero hits** (the old switcher dialog's title is gone) |

## Round D — editor chrome (Phase 47)

| # | What to do | PASS looks like |
|---|---|---|
| D1 | Editor → ☰ | The drawer opens and its header shows a **✕** |
| D2 | Tap ✕ | The drawer closes, the editor is still there, nothing else changed |
| D3 | Drawer → **PROJECTS** section → tap another project | The drawer stays open, the tree switches to that project, no navigation happened |
| D4 | Editor → context switcher (header tap) | The dialog lists **projects and single files only**, and its title is **not** "Open folder" |
| D5 | Settings → **CodeC Keys** | The subtitle says **"Off by default — CodeC uses your phone's keyboard…"** |
| D6 | Fresh install → open the editor | The **system keyboard** appears, with the extra-keys strip above it |
| D7 | Turn CodeC Keys ON in Settings | The code keyboard appears immediately; ghost-accept, popups, haptics and the space-bar caret drag all work |
| D8 | Install over a build that had CodeC Keys **ON** | It is **still ON** after the update (the stored choice wins) |

## Round E — the caret is above the keyboard (Phase 48)

| # | What to do | PASS looks like |
|---|---|---|
| E1 | Open a ~300-line file, scroll to the **last line**, tap into it | The cursor line is **fully visible above the keyboard** |
| E2 | On that last line, accept a suggestion (ghost or chip) | The caret **stays visible**; it does not drop behind the keyboard |
| E3 | Toggle CodeC Keys ON/OFF with the caret near the bottom | Visible both ways, no jump-scroll to the top |
| E4 | Expand the **Output Panel** with the caret near the bottom | The caret stays visible (or the panel is the only thing on screen — your choice) |
| E5 | ⋮ menu → hide/show the keys row | The caret stays visible |
| E6 | Scroll **100 lines away** from the caret, then open the keyboard | The view does **NOT** jump to the caret |
| E7 | Word wrap ON, a long line, caret at the end | Visible after the keyboard opens |
| E8 | Repeat E1-E5 with the **system keyboard** and with **CodeC Keys** | Same results both ways |

## Round F — back, everywhere (Phase 49)

| # | What to do | PASS looks like |
|---|---|---|
| F1 | Editor → ☰ → **BACK** | The **drawer closes**; the editor is still there; the app does **not** exit |
| F2 | Same, but press BACK within ~200 ms of tapping ☰ | Same result (the animation must not swallow the press) |
| F3 | Projects → open a project (file tree) → **BACK** | Back to the **project list** — the app does not exit |
| F4 | Editor with **unsaved changes** and the drawer open → BACK | The **unsaved-changes dialog** appears first |
| F5 | Find bar open → BACK | The find bar closes |
| F6 | Output panel expanded, keyboard down → BACK | The panel collapses |
| F7 | Web Preview / Logs / Feedback → BACK | Returns to the previous screen |
| F8 | Bottom sheet or ⋮ dropdown open → BACK | It closes, exactly as today |
| F9 | At the **start tab**, nothing open → BACK | The **"Enjoying CodeC? 💚"** prompt appears; **NOT NOW** stays in the app; **BACK again** exits |
| F10 | Tap **Terminal** (a non-start tab) → BACK → BACK | First back lands on the start tab; **second back shows the prompt** — on every device, 3-button *and* gesture nav |
| F11 | Feedback page → turn the exit switch **OFF** → BACK at the root | The app exits directly, no prompt |
| F12 | Settings → Feedback & Support → **"Tell us before you go"** | The same prompt opens on demand — the door that works even on a device where F9/F10 never fire |

## Result format (paste this back, per device)

```text
Device:   CodeC <version> · Android <release> (API n) · <model> · <abis>   ← COPY REPORT line 1
Nav mode: 3-button | gesture        RAM: ___ GB       Date: 2026-__-__
Build:    <CI run id> / <commit>

A1 ✅  A2 ✅  A3 ❌ "text on screen, verbatim"  …
F1 ✅  F2 ❌ "the app closed"  …
```

Then, for **every ❌**: which phase/part owns it (A→44, B→45, C→46, D→47,
E→48, F→49), and either the fix's commit or a dated deferral with a reason. Each
part file gets a `## Test log` section with its own rows — that is what closes
Phase 50.

## What this matrix cannot prove

- **Nothing about a home swipe.** A gesture-nav home swipe and a Recents swipe
  send no back event, so no code can show a prompt there (49.2 cause C). Rows
  F9-F12 test the **back** path and the **second door** — that is the whole
  honest claim.
- **No emulator here.** This sandbox has no device, no emulator and no Gradle
  cache, so rows A, B6, E and F are unrunnable by the agent and belong to the
  owner's handsets. **Nothing here is automated today** — the host tests that
  44-49 plan (`CaretVisibilityPolicyTest`, `BackRouterTest`, `DrawerPolicyTest`,
  `SetupGatePolicyTest`, `FolderImportRemovedTest`) **do not exist yet**; they
  land with their phases. And even once they do, they check *pure policy*, not
  what a finger sees — so no device row is ever excused by them.
- **Battery-manager kills** (Xiaomi/Oppo/Vivo/Samsung) are not reproducible on
  demand. A8 is the closest proxy; a real answer needs a device with an
  aggressive OEM skin and 10 idle minutes.
