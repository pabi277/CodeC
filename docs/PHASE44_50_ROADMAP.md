# CodeC — Phases 44–50 · test-phase roadmap

> **Owner (2026-09-12, verbatim):** *"I am now in the test phase and i have some
> bugs and improvements. You have to do deep research about every plan and
> solution and create new phases. Number the phases like i go in a row."*
>
> Five rows came in; they became **seven phases (44–50)**, numbered in a row so
> one command per phase — **"Start Phase 44"**, then 45, … 50 — is all the
> planning you have to remember. Research dossier with licences and sources:
> [`PHASE44_50_UX_RESEARCH.md`](PHASE44_50_UX_RESEARCH.md). Each phase has a
> `docs/chat-phaseNN/` directory with a README (evidence + risks + exit
> condition) and one part doc per part, in the house format
> (symptom → root cause → design → exit condition → tests → deferred →
> sources).
>
> **Status (2026-09-12):** Phase **44 is 🚧 IMPLEMENTED** on
> `arena/01a0955a-codec`; CI is ✅ GREEN (`34693462725`) and the owner **ran
> device round 1** on that artifact — it 🔴 **failed four rows** (a marker-only
> prefix read as READY, a setup bar with no working action, and
> `restoreState = true` sending "go to the terminal" to the editor). All three
> root causes are fixed and pinned by tests, and CI round 4 (`34695797493`, tip
> `4bf3c4c`) is ✅ GREEN; **round 2
> ([`chat-phase44/DEVICE_ROUND.md`](chat-phase44/DEVICE_ROUND.md) R1-R8, then
> D1-D12) is the gate.** Phase **45 is 🚧 IMPLEMENTED** as well (both layers the
> owner chose: the five-slide first-run guide with three doors back to it, and
> coach marks that may only point at an anchor the layout says is really on screen;
> **CI round 1 ✅ GREEN** — `34698914219` on tip `3c597b2`: assemble +
> `testDebugUnitTest` + `lintDebug`, release APK +12,792 B / +0.19%). **The owner
> then ran round 1 on his phone and 45.2 was rebuilt as ONE TEN-BEAT TOUR** (his
> flow: ☰ → change project to `demo_flask` → `app.py` → RUN ▶ → Install → the Flask
> preview → close → the tab bar / reveal handle → a small tour of Packages and
> Terminal; no next button — the highlighted control is the only way on, outside taps
> are inert, `demo_flask` is always present). **He then ran round 2 and the tour lost
> its skip** (*"You add the skip option and it's not a trough guide mean it got cut …
> I want a full process 1st to last without skip anything in this … At the end option
> to close and view again"*): every beat now waits in order, a tour card has **no
> button at all**, Back pauses without spending a beat, and the finish card offers
> **VIEW AGAIN** + **CLOSE**. **He then ran round 3 and asked for two things**
> (*"1st click disappear the massage and i have to click 2nd time to really work but if
> someone don't click 2nd time it just cut off the flow of tutorial"* and *"when the
> userland is installing and unpacking the user can not access any other other option
> and it will show a sweet massage of why"*): every anchor now publishes its
> control's **own click** and the overlay performs it before advancing — one tap, both
> halves, a drag spends nothing — and a new pure **`SetupLockPolicy`** (shipped beside
> the 44 gate, specified in
> [`chat-phase44/PART_44_1_VISIBLE_SETUP.md`](chat-phase44/PART_44_1_VISIBLE_SETUP.md))
> pauses the options an install cannot serve, dimmed with a 🔒, each answering a tap
> with one sentence that says what is happening, why, and where to watch it — the
> surface that shows the install is never paused. **He then ran round 4 and accepted
> it with one correction** (*"The lock option is good but still it late user can switch
> before the start of userland download because is takes a little time to connect … Make
> it instantly after 1st open and others are ok"*): round 4's lock waited for a stage
> that meant *work is moving*, so the seconds before the first byte — the disk probe,
> the reach for the network — were still switchable. Round 5 keys the pause on **whether
> the Linux tools work** instead of on the stage, so it is on from the first frame
> (`USERLAND_STARTING` also covers a boot-time repair of an interrupted swap), while a
> usable prefix, a stopped setup (`FAILED`/`UNSUPPORTED`) and Phase 42.3's **safe mode**
> still pause nothing. Round 5 is implemented with **197 host cases green locally**
> (76 guide/demo + 121 Phase 44); CI is ✅ **GREEN** on rounds 1-4 (`34698914219`;
> `34704379023`; `34707337429`; `34711827176` on tip `e7759f1`, release APK
> 6,675,258 B = +0.08% over round 3) with round 5's run pending, and device round
> [`chat-phase45/DEVICE_ROUND.md`](chat-phase45/DEVICE_ROUND.md) **G1-G40** is NOT
> run (start it from Settings → About → Reset tips; G29-G33 the one-tap rule, G34-G38
> the lock, G39-G40 its first-frame timing).
> 46-50 are still plan-only. Phase 43 is
> **❌ CANCELLED** by the same instruction (row 3): its feature is deleted, its
> reason is kept as a tombstone in
> [`chat-phase43/README.md`](chat-phase43/README.md).

---

## The owner's five rows → phases

| # | Owner's words (verbatim) | Phase | Title | Effort |
|---|---|---|---|---|
| 1 | *"Userland is installing but the test user don't know it's installing so they close app before it complete than letter when they try to install any other pkg got errors"* → *"If it opens the terminal 1st and show a warning don't close the terminal while userland is installi[ng]"* | **44** | Setup you can see, and cannot half-finish | M |
| 2 | *"It has 0 guide features to give the user a real knowledge how to use the app…"* → *"Set a step by step user guide after opening the app 1st time with a open view again"* | **45** | The guide (slides + coach marks) | M |
| 3 | *"The project have a feature open a folder (phase 43, incomplete) i want to remove it completely and make the project section more optimization features like file single click to open in a editor screen with real path and same file edit but not full project to editor. To open a full project in editor the 3 dot will have the option to open in editor"* | **46** | Projects, not folders | M |
| 4.i / 4.ii / 4.iii | *"the file ber can't close without opening any file"* · *"I can switch project but can't directly open folder"* · *"System keyboard make default user can change to app keyboard if they want"* | **47** | Editor chrome that behaves | M |
| 5.A | *"If the code is very big it's last line go under the keyboard, when i use a suggestion it go down and hide behind the keyboard"* | **48** | Nothing hides behind the keyboard | S/M |
| 4.iv / 5.B / 5.C | *"After clicking 3 ber if user use back botton it will [close] the file view and show the editor not full app close"* · *"My phone showing the option when try to close not now option but in most phone no option like not now or exit"* · *"The back botton all screen behavior please recheck and refine"* | **49** | Back does the obvious thing, everywhere | M |
| — | *"I am now in the test phase"* (the round that proves 44–49) | **50** | Cross-device test round | S (docs + gates) |

Owner's clarifications, 2026-09-12 (they decide the ambiguous readings):

| Point | Owner chose |
|---|---|
| 4.iv *"3 bar + back"* | **Both** — the editor ☰ drawer **and** the Projects-hub file tree, plus an audit of every screen (that is 49's job anyway) |
| 5.B the exit prompt | **Keep it ON, make it consistent** — find out why some devices never show it and fix that |
| 4.ii *"can't directly open folder"* | **In-drawer project picker** — the project list opens right inside the drawer, no navigation |
| 2 the guide | **Both layers** — slides on first run **and** coach marks on first arrival at Editor / Terminal / Packages |

---

## Execution order: **44 → 45 → 46 → 47 → 48 → 49 → 50**

The numbers **are** the order. Why this order:

1. **44 — setup you can see, and cannot half-finish.** 🚧 **IMPLEMENTED
   2026-09-12** (`arena/01a0955a-codec`; both parts, **121 host cases** after
   Phase 45's rounds 4-5 added the chrome lock here, CI ✅ GREEN
   round 4 `34695797493`; device round 1 🔴 FAILED four rows → three root causes
   fixed; **round 2 not run**, and its lock rows live in
   [`chat-phase45/DEVICE_ROUND.md`](chat-phase45/DEVICE_ROUND.md) **G34-G40**). It goes first because it
   breaks every other test round: a tester whose userland is half-installed
   reports *everything* as broken (`pkg` missing, Python "not installed", git
   missing). It is also the only phase in the series with a data-loss-shaped
   bug (a kill between the two renames in `swapPrefix` leaves **no `usr` at
   all**, `UserlandInstaller.kt:377-395`).
2. **45 — the guide.** 🚧 **IMPLEMENTED 2026-09-12, THROUGH ROUND 4**
   (`arena/01a0955a-codec`; both parts, **197 host cases green locally**, CI ✅
   GREEN on rounds 1-4
   `34698914219`/`34704379023`/`34707337429`/`34711827176` with round 5's run
   pending, device round
   [`chat-phase45/DEVICE_ROUND.md`](chat-phase45/DEVICE_ROUND.md) **G1-G40** not
   run). Round 4 came from the owner's own two requests after he ran round 3:
   *one tap on a highlighted control must really work it* (it now performs the
   control's own published click and then advances, in one gesture), and *"when
   the userland is installing and unpacking the user can not access any other
   option"* — which is a **44 surface** shipped beside the gate as pure
   `SetupLockPolicy` (`chat-phase44/PART_44_1_VISIBLE_SETUP.md` §"Phase 45 round
   4 — the chrome lock"). First impression for every tester you have not met yet,
   and its slide 3 is where the *"one-time download, don't close the app"*
   mental model is planted that 44 enforces — which is why the guide gate runs
   *before* 44.1's terminal-first divert and never shares a screen with the setup
   bar.
3. **46 — projects, not folders.** Deletes the incomplete Phase 43 feature and
   re-shapes the hub around the new file/project split. It goes before 47
   because the drawer's in-drawer project picker and the hub's *Open in editor*
   are two faces of one model.
4. **47 — editor chrome that behaves.** The drawer you can always close, the
   project list inside it, and the system keyboard as the default.
5. **48 — nothing hides behind the keyboard.** Small, surgical, and it needs
   47.2 settled first (the default keyboard changes the repro).
6. **49 — back does the obvious thing, everywhere.** The systematic answer to
   4.iv + 5.B + 5.C: one pure `BackRouter`, one precedence table, every screen
   wired to it. It goes last of the feature phases because 46/47 change which
   surfaces exist.
7. **50 — cross-device test round.** One runbook over 44–49, on at least three
   devices including one gesture-nav and one 3-button-nav phone, because 5.B is
   literally a *"why is it different on other phones"* bug.

**Ordering rules inside the series** (the only two):

- **44.2 before any Packages-tab testing.** Until the prefix can survive a kill,
  package tests on a shared device are uninterpretable.
- **46.1 before 46.2.** The "Open Folder" row and the single-file editor both
  touch the hub's tap routing; deleting first keeps the diff honest.

---

## What each phase is, in one paragraph

### Phase 44 — Setup you can see, and cannot half-finish

**44.1 The install is visible.** The bootstrap already runs at app launch
(`TerminalViewModel.kt:205` builds it from `MainActivity.kt:629`), and its only
output is text painted into the terminal emulator's grid
(`TerminalSession.notice`, `TerminalSession.kt:260`) — invisible from the
Projects/Editor/Packages tabs. Ship: a first-run **setup gate** ("Setting up
CodeC… 62 % — you can write C right now"), the terminal opened on first launch
with a **"don't close the app"** banner (the owner's own idea, kept), an
`onProgress` StateFlow that any tab can render, the existing
`TerminalForegroundService` started **before** the download instead of after,
and a gate on the Packages tab so `pkg install` is refused with a real sentence
while the userland is not valid.
**44.2 The install cannot half-finish.** An install ledger, a swap with no
"no-prefix" window (or a rename-back on boot), orphan `usr.old-*` /
`.userland-staging-*` cleanup (neither is referenced anywhere else in
`app/src/main` — verified), and a `SetupState` that every consumer reads.
Specs: [`chat-phase44/`](chat-phase44/README.md).

### Phase 45 — The guide

**45.1 Slides on first run** (after the three language tiles, skippable,
persisted, five slides in the order the user meets the features) with **three**
"see it again" entry points: a Settings row, the Projects-hub ⋮ menu, and the
editor ☰ drawer footer. **45.2 Coach marks** on first arrival at the editor
(☰ + RUN ▶), the terminal (status chip + extra keys) and Packages (one install
card) — three or four, one per surface, never a tour. Both layers are pure plans
(`GuidePlan`, `CoachMarkPlan`) so CI pins the sequence, the once-only rule and
the re-open paths. The no-nag law holds: one tap skips, nothing returns unless
asked. Specs: [`chat-phase45/`](chat-phase45/README.md).

### Phase 46 — Projects, not folders

**46.1 Remove "Open Folder" completely** — the `+`-sheet row
(`FileManagerScreen.kt:1474-1481`), the launcher (`:180-188`), `importFolder`
(`FileManagerViewModel.kt:357`), `copyDocumentTree`/`copyDocumentChildren`
(`ProjectTransfer.kt:20`, `:313`), the two strings, and the Phase 43 docs —
with a tombstone recording *why* (a one-way copy that crashes on provider
trees, and no persisted grant anywhere in the app). **46.2 The new split:**
a single tap on a file in the hub opens **that one file** (real path in the
status bar, edit + save, no project chrome), and the project card's **⋮ → Open
in editor** loads the whole project. Pure part: `EditorOpenMode`.
Specs: [`chat-phase46/`](chat-phase46/README.md).

### Phase 47 — Editor chrome that behaves

**47.1 The drawer:** a visible close affordance (✕ in the header + scrim tap
kept), `BackHandler(enabled = drawerState.isOpen)` so back never exits, and the
**in-drawer project picker** the owner asked for (the list opens inside the
drawer; tapping a project switches the editor's context without navigating).
**47.2 The keyboard:** `codec_keys_enabled` defaults to **false** (system
keyboard), CodeC Keys becomes an opt-in with an honest one-line description, and
the first-run guide mentions it. Existing users keep whatever they chose (a
stored value always wins over a new default).
Specs: [`chat-phase47/`](chat-phase47/README.md).

### Phase 48 — Nothing hides behind the keyboard

The editor column is correctly `imePadding()`'d (`EditorScreen.kt:1016`), so
sora is resized — but nothing ever asks sora to bring the caret back into the
new, smaller viewport, and sora only auto-scrolls on *selection* change
(`ScrollEvent` cause `CAUSE_MAKE_POSITION_VISIBLE`). Fix: a pure
`CaretVisibilityPolicy` for *when* a re-scroll is owed, and the Android edge
calling the public `CodeEditor.ensurePositionVisible(line, column,
noAnimation = true)` after the layout settles. Also covers the "accept a
suggestion and the caret disappears" half of 5.A.
Spec: [`chat-phase48/`](chat-phase48/README.md).

### Phase 49 — Back does the obvious thing, everywhere

Two `BackHandler` call sites exist in the whole app today
(`MainActivity.kt:801`, `EditorScreen.kt:646`). Replace the ad-hoc set with one
pure **`BackRouter.decide(state): BackAction`** and a precedence table pinned by
CI: unsaved-changes → open drawer → open hub project tree → open sheet/dialog →
pop route → exit prompt → exit. Then **5.B's real answer**: the exit prompt is
decided from *state* ("am I at a root with nothing open"), not from
`popBackStack()`'s return value, so it behaves the same on every device; the
silent "first back hops to the start tab" behaviour gets a decision of its own.
Specs: [`chat-phase49/`](chat-phase49/README.md).

### Phase 50 — Cross-device test round

No new features. One runbook (`chat-phase50/DEVICE_MATRIX.md`) that walks
44–49's exit conditions on a matrix: a gesture-nav phone, a 3-button-nav phone,
a small screen (≤ 5.5"), a tablet/foldable if available, Android 11 / 13 / 15 if
available, plus the two destructive cases that matter (kill during install,
revoke storage). The deliverable is a **filled matrix**, not a promise — the
same standard `docs/BETA.md` and Phase 41's device rounds set.
Specs: [`chat-phase50/`](chat-phase50/README.md).

---

## Standing rules this series must not break

Carried from `rule.md` §6 and the 38–43 decisions, re-stated because they are
easy to break by accident in a UX series:

1. **No new dependencies.** Everything in 44–50 is Compose, DataStore, the
   existing foreground services and sora's public API (dossier §3.1, §7).
2. **No new DataStore key without a reader** — `SettingsKeysHaveReadersTest`
   walks key → flow → out-of-store reader for all three stores.
3. **No new Settings control without an audit row** — `SettingsAuditTest` counts
   `Settings*` call sites and section headers against
   `docs/chat-phase38/SETTINGS_AUDIT.md`; update the doc in the same commit.
4. **`MANAGE_EXTERNAL_STORAGE` never becomes load-bearing** — 46 removes the
   only SAF-tree feature and adds no storage ask.
5. **TCC stays the default C compiler; `cc` stays CodeC's own frontend;** the
   `-o`-last link order stands. 44 touches the *userland* install, never the
   bundled compiler.
6. **No telemetry, ever.** The guide records only "seen" booleans locally; the
   exit prompt still uploads nothing by itself.
7. **Pure policy + thin Android edge**, host-tested, for every decision in the
   series (the codebase's pattern since Phase 30).
8. **Evidence before fix** (`rule.md` §4.2): 44's crash/kill repro and 49's
   drawer-back observation come from a device or a host test **before** code.

---

## What is explicitly *not* in this series

| Idea | Why not |
|---|---|
| Predictive back (`enableOnBackInvokedCallback`) | Whole-app migration; `targetSdk 28` is load-bearing for exec-of-app-data. Deferred, recorded in dossier §6.4. |
| Opening a SAF folder as a project (old 43.2) | Cancelled by the owner's row 3. If it ever returns it needs the persisted-grant design in the cancelled docs — which the tombstone points at. |
| A new file manager, external-storage editing, `FileObserver` sync | Same cancellation; nothing in 44–50 depends on them. |
| New languages, LSP servers, themes, git features | Out of scope for a test-phase series; `docs/IDEA_BACKLOG.md` still owns them. |
| Redesigning the 5-tab bar | Phase 32.1's hide-while-typing already handles the canvas problem; 48 handles the keyboard problem. |
