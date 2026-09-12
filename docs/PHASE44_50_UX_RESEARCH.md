# CodeC — Phases 44–50 · test-phase research dossier

> **Owner (2026-09-12, verbatim):** *"I am now in the test phase and i have some
> bugs and improvements. You have to do deep research about every plan and
> solution and create new phases. Number the phases like i go in a row."*
>
> This is the **research half** of that request. The plan half is
> [`PHASE44_50_ROADMAP.md`](PHASE44_50_ROADMAP.md); the per-phase evidence and
> specs live in `docs/chat-phase44/` … `docs/chat-phase50/`.
>
> **Evidence law followed here:** every claim about *this app* is a file:line
> read on 2026-09-12 against `main` @ `f3a6e32`. Every external claim names its
> source and whether it was fetched this session. Nothing below is code yet.

---

## 1. What the five owner rows actually are, in code

| # | Owner's words (shortened) | Where it lives today | Root cause found in code |
|---|---|---|---|
| 1 | *"Userland is installing but the test user don't know it's installing so they close app before it complete than letter when they try to install any other pkg got errors"* | `TerminalViewModel.kt:205` (`init` → `startInternal()`), `:323` `startItem`, `:337` → `installUserlandInternal`; progress text goes to `TerminalSession.notice()` (`TerminalSession.kt:260`) | The install **starts at app launch**, not when the Terminal tab opens — `MainApp` builds `TerminalViewModel` at `MainActivity.kt:629` for the whole activity — but its only output is text fed into the **terminal emulator's screen**, invisible from every other tab. There is **no foreground service during the download** (`TerminalViewModel.kt:185-193` starts it only once a session is `alive`, which happens at `TerminalSession.kt:117`, i.e. **after** the install at `:337`). So the process is unprotected exactly while it is downloading. |
| 1b | *"…than letter when they try to install any other pkg got errors"* | `UserlandInstaller.swapPrefix` (`UserlandInstaller.kt:377-395`) | `swapPrefix` moves the live `usr` aside (`prefix.renameTo(old)`) and **then** renames the staged tree in. A kill between those two renames leaves **no `usr` at all** plus an orphan `usr.old-<ts>`. Nothing cleans either up (`STAGING_DIR` at `:538` and the `.old-` name at `:384` have **no other reference in `app/src/main`** — verified by grep). Next launch: `hasRunnableUserland` false → a fresh install is attempted → offline it returns `SkippedOffline` (`:141`) → **`pkg` exists nowhere**, and Packages/`pkg install` fails with a shell error the user cannot read. |
| 2 | *"It has 0 guide features … Set a step by step user guide after opening the app 1st time with a open view again"* | `WelcomeScreen.kt` (3 starter tiles), `MainActivity.kt:669-700` (`firstLaunchComplete`), `EditorScreen.kt:726-750` (the one "Typing tips" `AlertDialog`, `ime_guide_dismissed`) | There is exactly **one** guide surface in the app: a four-bullet typing-tips dialog that appears in the **editor** and can only be dismissed (`setImeGuideDismissed(true)`) — no "see it again" path, no `SettingsManager` reset. Nothing explains the tabs, RUN ▶, the drawer, Packages, or the terminal. |
| 3 | *"The project have a feature open a folder (phase 43, incomplete) i want to remove it completely and make the project section more optimization features like file single click to open in a editor screen with real path and same file edit but not full project to editor. To open a full project in editor the 3 dot will have the option to open in editor"* | `FileManagerScreen.kt:180-188` (launcher), `:1077-1080` (sheet row), `:1426-1484` (`ProjectsHubAddSheet`), `FileManagerViewModel.kt:357-382` (`importFolder`), `ProjectTransfer.kt:20` + `:313` (`copyDocumentTree` / `copyDocumentChildren`) | "Open Folder" is one sheet row → one launcher → one VM function → one unbounded recursion. **No test in `app/src/test` references `copyDocumentTree`** (grep, 2026-09-12), so deleting the whole path is test-safe. Meanwhile the hub's file tap (`FileManagerScreen.kt:520-523`) already navigates to the **full editor** (`Screen.Editor.createRoute(path, projectName)`), and the card overflow enum (`:1114`) has **no OPEN_IN_EDITOR** member. |
| 4 | *"the file ber can't close without opening any file" / "I can switch project but can't directly open folder" / "System keyboard make default user can change to app keyboard if they want" / back should close the drawer* | `EditorScreen.kt:900-1010` (`ModalNavigationDrawer` + `EditorProjectDrawer`), `EditorProjectDrawer.kt:96-135` (header row = *switch project*), `SettingsManager.kt:126` (`CODEC_KEYS_ENABLED ?: true`), `SettingsScreen.kt:239-270` (CodeC Keys section) | (i) The drawer has **no close affordance**: the header row switches project, the ☰ that opened it is behind the scrim, and `gesturesEnabled` is `false` whenever a file is open (`EditorScreen.kt:906-907`), so on a phone the only reliable exit is tapping a file. (ii) The drawer offers `onSwitchProject` (a picker dialog) but no project list and no way out to the hub. (iii) **CodeC Keys is ON by default** — the system IME is switched *off* at `EditorScreen.kt:401`. |
| 5A | *"If the code is very big it's last line go under the keyboard, when i use a suggestion it go down and hide behind the keyboard"* | `EditorScreen.kt:1016` (`.imePadding()`), `:1381-1384` (editor `Box` with `weight(1f)`), `:1572`/`:1683` (the two `BottomStrip` placements), `AndroidManifest.xml:70` (`adjustResize`) | The **column** is correctly inset, so the sora `AndroidView` is resized — but nothing ever asks sora to re-scroll the caret into the **new, smaller** viewport. sora scrolls on *selection change* (`ScrollEvent` cause `CAUSE_MAKE_POSITION_VISIBLE`), not on resize; the public remedy is `CodeEditor.ensurePositionVisible(line, column[, noAnimation])` / `ensureSelectionVisible()` (confirmed present in `CodeEditor.java` lines 2245/2256, re-verified 2026-09-12 — see §5). CodeC calls **neither** (grep: zero hits in `app/src/main`). |
| 5B | *"My phone showing the option when try to close not now option but in most phone no option like not now or exit"* | `MainActivity.kt:768-812` (`exitPromptEnabled`, `ExitFeedbackDialog`, the root `BackHandler`) | The prompt is gated on `!navController.popBackStack()`. Whether that is `false` depends on the **back stack**, which depends on where the app started and which tab the user is on: tab taps push a second entry (`popUpTo(start) { saveState = true }` — the standard snippet), so the first back pops to the start tab *silently* and only the **second** back reaches the prompt. Add gesture-nav users (home swipe never sends a back event) and the prompt is reached on some devices and not others. Owner's decision (2026-09-12): **keep it, make it consistent.** |
| 5C | *"The back botton all screen behavior please recheck and refine"* | Only **two** `BackHandler` call sites exist in the whole app: `MainActivity.kt:801` and `EditorScreen.kt:646` (`enabled = isDirty`) | Every other surface (hub project tree, Web Preview, Logs, Feedback, git sheets, branch sheet, hub add-sheet, output panel, find bar, drawer) relies on Material3/NavHost defaults. That is why "back" feels different on every screen — there is no single policy to be consistent *with*. |

---

## 2. Setup that the user can see and cannot half-finish

### 2.1 What the platform gives you

- **Foreground service = the notification *is* the UX.** Android's own rule:
  *"Foreground services let you asynchronously perform operations that are
  noticeable to the user… show a status bar notification, to make users aware
  that your app is performing a task"* and *"Only use a foreground service when
  your app needs to perform a task that is noticeable by the user, even when
  they're not directly interacting with the app"*
  ([developer.android.com/develop/background-work/services/fgs](https://developer.android.com/develop/background-work/services/fgs), fetched 2026-09-12). A
  multi-hundred-MB bootstrap download is exactly that case.
- **CodeC already has the whole mechanism**: `TerminalForegroundService`
  (`ui/services/TerminalForegroundService.kt`, channel `codec_terminal`,
  `IMPORTANCE_LOW`, `ic_stat_codec`, `setOngoing(true)`), the
  `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` permissions and the
  `dataSync` service type (`AndroidManifest.xml:16-17,113-118`). It is simply
  started at the wrong moment (§1, row 1).
- **targetSdk 28** means the Android 14 foreground-service-type enforcement
  does not apply to CodeC — no new type is needed, only an earlier `start()`.
- **Process death is not an error, it is a fact.** The Android contract is that
  any process can be killed at any time; correctness therefore comes from
  *restartability*, not from asking the user nicely. A "don't close the app"
  banner (the owner's own idea, row 1) is **necessary but not sufficient** — it
  must be paired with a crash/kill-safe install (§2.3).

### 2.2 What the comparable apps do (behavior only — clean-room, `rule.md` §6)

| App | Setup story | What CodeC should take |
|---|---|---|
| Termux | First launch shows a **full-screen bootstrap progress view** in the terminal itself; nothing else is usable until it finishes; killing it re-runs the bootstrap from a clean state. | A **gate**, not a background task. The user cannot wander into Packages while the prefix is invalid. |
| Pydroid 3 | Nothing to install for Python (bundled). No setup narrative at all. | CodeC's C story is already this (TCC in the APK) — the gate must say so: *"C works right now; Python needs a 1-time download."* |
| Acode / Spck | No userland; LSP/servers are per-package installs with per-item progress in the UI. | Progress belongs **next to the action that caused it**, not only in a terminal the user may never open. |

### 2.3 The install must be restartable, not just polite

Three mechanisms, all host-testable:

1. **A ledger** (`SharedPreferences` or a marker file) recording
   `{state: IDLE|DOWNLOADING|EXTRACTING|SWAPPING|DONE, release, startedAt}` —
   the same shape `StartupLedger` (`ui/crash/StartupLedger.kt`) already uses for
   the crash-loop guard, including its `commit()`-not-`apply()` discipline.
2. **A swap that has no "no prefix" window.** Two options, both standard:
   - (a) *copy-then-delete*: rename staged → `usr.new`, copy contents into the
     existing `usr`, delete `usr.new`; or
   - (b) *rollback-on-boot*: keep the `.old-` aside and, on the next launch,
     if `usr` is missing but `usr.old-*` exists, **rename it back** before
     anything else runs.
   (b) is ~15 lines, needs no extra disk, and turns the failure into a
   self-heal. The staged/old directories are then garbage-collected on boot by
   the same routine (`TempGc` already proves the shape).
3. **A capability check at the point of use.** `pkg` commands (Packages tab →
   `terminalViewModel.sendCommand(item.installCommand)`,
   `ModulesScreen.kt:301`) must be refused with an actionable line
   (*"CodeC is still setting up its Linux tools — 62 %. Don't close the app."*)
   instead of being typed into a shell that has no `pkg`.

**Rejected:** `WorkManager` for the bootstrap. It is the right tool for
deferrable, retryable, constraint-driven work; a one-shot interactive
first-run install that must finish *now*, in the foreground, with the user
watching, is a foreground service. Using WorkManager here would add a
retry-scheduling model to a path that already has its own 30-attempt ranged
retry (`UserlandInstaller.kt:552 MAX_DOWNLOAD_ATTEMPTS`).

---

## 3. A guide that teaches the loop, and can be re-opened

### 3.1 Open-source survey (licenses checked, 2026-09-12)

| Library | License | Verdict for CodeC |
|---|---|---|
| `com.canopas.intro-showcase-view` (compose) | Apache-2.0 ([repo](https://github.com/canopas/compose-intro-showcase)) | Usable license, but it is a **third dependency** for ~150 lines of overlay Compose, and its spotlight model assumes stable anchors in one screen. CodeC needs anchors in *three* screens + a persisted plan. **Rejected: dependency for a policy.** |
| `com.github.amlcurran.ShowcaseView` | Apache-2.0 | View-system only; CodeC's editor is Compose + one `AndroidView`. **Rejected.** |
| `AppIntro` (onboarding slides) | Apache-2.0 | Fragment/ViewPager based — a second navigation model inside a single-activity Compose app. **Rejected.** |
| **Nothing** (hand-rolled) | — | **Chosen.** CodeC already has the two primitives needed: `HorizontalPager`-free `Column` slides are ~80 lines, and a spotlight overlay is a `Box` + `Canvas` + `onGloballyPositioned` anchor rects. Both are pure-policy-able (the house pattern: `NavBarPolicy`, `KeysStayPolicy`, `CompletionPolicy`). |

**The house rule that decides this:** every CodeC UX decision that can be
expressed as a pure function *is* (`CompletionPolicy.surfaceFor`,
`NavBarPolicy.hideNavBar`, `KeysStayPolicy.isVisible`, `ExitSurvey.stars`), and
CI pins it. A guide is a **sequence of steps gated on state** — exactly a pure
plan object. No dependency earns its place for that.

### 3.2 What the guide must teach (from the product analysis, not from taste)

`docs/PHONE_UX_ANALYSIS.md` §6 measured the first session against Pydroid and
Spck and named the gap: *the easy path is "know that this used to be a C IDE,
open the right tab, survive mediocre colour and empty suggestions."* The guide's
job is to kill the "know that" part. Five slides, one idea each, in the order
the user meets them:

1. **Write** — the editor: ☰ opens your files, tabs on top.
2. **Run** — RUN ▶ compiles and runs; output appears at the bottom. C works
   offline.
3. **Install** — the Packages tab adds Python/Node/etc. **one time, with a
   download; don't close the app** (this is row 1's teaching moment — the guide
   is where the mental model is planted, and Phase 44 is where it is enforced).
4. **Terminal** — a real Linux shell; `pkg`, `git`, `cc` all live there.
5. **Projects** — the Projects tab: tap a file to edit it, ⋮ → *Open in
   editor* for the whole project (row 3's new model).

Then **coach marks** on first arrival at each surface (owner's choice
2026-09-12: *both layers*): editor (☰ / RUN ▶), terminal (the status chip +
extra keys), packages (one install card). Three or four, never more — a coach
mark per screen is a tour, and tours get skipped.

### 3.3 The two rules a guide must not break

- **The no-nag law** (`ExitSurvey`'s own words): every guide surface is
  one-time, skippable with one tap, and re-openable from Settings. Nothing
  re-appears after dismissal unless the user asks.
- **No new DataStore key without a reader.** `SettingsKeysHaveReadersTest`
  walks key → flow → out-of-store reader for every key in the three stores; a
  guide flag (`guide_completed`, `coach_marks_seen_csv`) must have a real
  reader in production code, and any Settings row added must be added to
  `docs/chat-phase38/SETTINGS_AUDIT.md` in the same commit or
  `SettingsAuditTest` fails the build (both verified: the test files read the
  audit doc's table and count `Settings*` call sites in `SettingsScreen.kt`).

---

## 4. Projects vs files on a phone (row 3)

### 4.1 What the market does

| App | Model | Behaviour on a file tap |
|---|---|---|
| Spck | Project-centric | File tap opens the editor **with** the project (drawer = project tree). |
| Acode | Folder-centric | Opens a folder; files open in tabs; there is no "project" concept. |
| Pydroid 3 | File-centric | A single file is the unit; folders are secondary. |
| Squircle CE | File/folder | Opens files or a folder tree; no run integration. |

**CodeC's honest position:** it is a *project* app (git, `.codec.json`, run
targets, web preview all key off a project root) whose *first* interaction is
usually "just fix this one file". Both are true, so the answer is not to pick a
side but to make the cheap path cheap: **file tap = one file; explicit action =
whole project.** That is exactly the owner's row.

### 4.2 The technical shape (verified in code)

- `EditorViewModel.openFile(context, projectName, fileName)`
  (`:1229-1237`) already has both halves: `openProjectFile` (`:1239`, sets
  `_projectName`, loads tabs, saves launch state) and `openScratchFile`
  (`:1276`, `_projectName.value = null`, single file under `FileManager`'s
  single-files folder).
- What is missing is a **third mode**: *a real project file opened without a
  project context*. It is `openProjectFile`'s read/save path (same root, same
  real path, same line-ending handling) with the project chrome suppressed:
  no drawer tree, no git meta, no RUN-target detection beyond the file's own
  language, no `EditorLaunchState` write (a peek must not become the launch
  point). The pure part is a small `EditorOpenMode { PROJECT, SINGLE_FILE,
  SCRATCH }` decision — CI-pinnable, and it is the same lever 46.2 needs for
  the status bar's path line (*"real path"*, the owner's words).
- `HubCardAction` (`FileManagerScreen.kt:1114`) gains `OPEN_IN_EDITOR`; the card
  ⋮ gains the row; `onCardAction` routes it to
  `onProjectFileSelected(project.name, lastEditedOrEntryFile)`. **Decision
  point for the owner:** which file opens — the project's launch default
  (`launchDefault` already exists in the editor), else the most recently
  modified source file, else the alphabetically first source file. Recorded in
  `PART_46_2`; the default chosen is *launch default → newest source → first
  source*, because that is what "open this project" means to a person.

### 4.3 Why deleting "Open Folder" is safe *and* honest

`importFolder` is a **copy** (`ProjectTransfer.copyDocumentTree`), not a link —
after it, the user's folder and CodeC's copy are unrelated, which is what Phase
43's own README admitted (*"one-way copy into a new project"*). Deleting it
removes: the crash surface Phase 43.1 was written to fix (unbounded
`copyDocumentChildren` recursion at `ProjectTransfer.kt:313-339`, `catch
(Exception)` that a `StackOverflowError` escapes), an entire SAF permission
story with zero persisted grants (`takePersistableUriPermission` has **no call
site** in `app/src/main`), and a row in the `+` sheet that promises something
CodeC cannot keep. **What must survive the deletion:** `Import ZIP` and
`Import file` (both stay), `Open with CodeC` (Phase 24.7's intent filters stay),
and the export paths. Recorded as a tombstone in
[`chat-phase43/README.md`](chat-phase43/README.md) — the feature is gone, the
reason is not.

---

## 5. Nothing hides behind the keyboard (row 5A)

### 5.1 The mechanism, precisely

- `MainActivity` opts into edge-to-edge (`enableEdgeToEdge()`), the manifest
  keeps `adjustResize` (`AndroidManifest.xml:70`), and the editor's outer column
  applies `.imePadding()` (`EditorScreen.kt:1016`). So when the IME appears, the
  column shrinks and the sora `AndroidView` inside the `weight(1f)` `Box`
  (`:1381-1384`) is **laid out smaller**. That part is right.
- sora's scroll position, however, is only recomputed when the **selection**
  changes (`ScrollEvent`'s `CAUSE_MAKE_POSITION_VISIBLE` is documented as
  *"caused by calling `CodeEditor#ensurePositionVisible(int, int)`. This can
  happen when this method is manually called or either the user edits the
  text…"* — `ScrollEvent.java:51`). A viewport resize is not a selection
  change. Result: the caret line (and, on a long file, the last line) stays
  where it was — under the keyboard. This is the owner's 5A exactly, and it is
  also why accepting a suggestion makes it worse: the strip/keys row re-renders
  and the caret moves to a line that is now below the fold.
- **Verified public API** (grep.app code index of `Rosemoe/sora-editor`
  `main`, re-checked 2026-09-12): `CodeEditor.java:2245 public void
  ensurePositionVisible(int line, int column)`, `:2256 public void
  ensurePositionVisible(int line, int column, boolean noAnimation)`, and
  `ScrollEvent.java:51 public final static int CAUSE_MAKE_POSITION_VISIBLE = 3;`
  — whose only dispatch site is `CodeEditor.java:2313-2314`, inside
  `ensurePositionVisible` itself. That is the proof of the root cause: **a
  viewport resize never reaches that dispatch**, so nothing scrolls.
  (`CodeEditor` also exposes `ensureSelectionVisible()`; its line number is
  not re-verified here and 48 does not use it — see 48.1's rejected list.
  Line numbers are upstream `main` and may drift from the 0.24.6 pin; the
  method *names* are what the implementation must match.)
- Earlier research note, kept for continuity (line number NOT re-verified
  2026-09-12): a *"Cancel the next animation for `ensurePositionVisible`"*
  javadoc sits near `CodeEditor.java:512`. A real consumer uses it exactly this way
  (`SagerNet/sing-box-for-android` `ProfileCodeEditor.kt:238`:
  `editor.ensurePositionVisible(position.line, position.column, true)`).
- **CodeC calls none of them** (grep `ensurePositionVisible|ensureSelectionVisible`
  over `app/src/main`: zero hits).

### 5.2 The fix shape (and the honest caveat)

A pure `CaretVisibilityPolicy` decides **when** a re-scroll is owed (IME
visibility changed, CodeC Keys appeared/disappeared, the keys row toggled, the
output panel expanded/collapsed, the status bar yielded, font size changed) and
with what margin (`keepLinesBelow`, so the caret is never on the very last
visible row — the "one line of air" rule VS Code calls
`editor.cursorSurroundingLines`). The Android edge calls
`editor.ensurePositionVisible(line, column, /*noAnimation=*/true)` **after** the
new layout pass (`Modifier.onSizeChanged` + one `post{}`/`withFrameNanos`, never
during composition).

**Caveat, stated because it is real:** `ensurePositionVisible` scrolls the caret
into the *view's* bounds; it does not know about the keys/strip rows that sit
*inside the same inset-paid column*. Because those rows are siblings **below**
the `weight(1f)` editor box, the editor's own bounds already exclude them — so
the call is sufficient. The device round (Phase 50) must still prove it on a
small screen with the output panel expanded, because that is the one case where
the editor box can become very short.

### 5.3 Rejected

- `adjustPan` instead of `adjustResize` — pans the whole window, hides the top
  app bar, and fights the editor's own scrolling. The platform itself calls
  resize *"generally less desirable… users may need to close the soft keyboard
  to get at obscured parts"* for pan.
- Scrolling by hand with `getCharOffsetY` + `scrollBy` — that is re-implementing
  sora's scroller, including its word-wrap layout math.
- Adding bottom padding inside sora's text — changes selection geometry and
  the ghost-hint Y math (`EditorScreen.kt:1521-1533` reads
  `getCharOffsetY` for the "Tab ▸" pill).

---

## 6. Back does the obvious thing, everywhere (rows 4.iv, 5B, 5C)

### 6.1 The dispatch model (external, fetched 2026-09-12)

- Callbacks are a **chain of responsibility**: *"the callbacks are invoked in
  the reverse order from the order you add them — the callback added last is the
  first given a chance"* and *"Each callback in the chain is invoked only if the
  preceding callback was not enabled"*
  ([stackoverflow.com/q/79247909](https://stackoverflow.com/questions/79247909),
  quoting the platform docs).
- *"The same stack behavior applies in Compose: the innermost
  `PredictiveBackHandler` or `BackHandler` takes precedence"*
  ([developer.android.com/guide/navigation/custom-back/predictive-back-gesture](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture),
  fetched via search snippet 2026-09-12).
- *"OnBackPressedCallback is always called regardless of the value of
  `android:enableOnBackInvokedCallback`"* — same page. So Material3 1.3.1's
  `ModalNavigationDrawer` (Compose BOM `2024.12.01`) *does* register a back
  consumer when the drawer is open, even with predictive back off
  (CodeC's manifest has no `enableOnBackInvokedCallback`, `targetSdk = 28`).
  **This is a hypothesis to confirm on device in Phase 49, not a conclusion** —
  the owner's report says back exits the app with the drawer open, and the
  owner's report outranks a source reading (see 6.3).
- Material3 predictive-back support for `ModalNavigationDrawer` requires
  material3 `1.3.0-alpha01`+ and the manifest flag
  ([developer.android.com/codelabs/predictive-back](https://developer.android.com/codelabs/predictive-back)).

### 6.2 CodeC's actual back stack (verified in code)

| Surface | Back today | Source |
|---|---|---|
| Root tab (start destination) | exit prompt (if enabled) → `activity.finish()` | `MainActivity.kt:801-809` |
| Non-start tab | `popBackStack()` returns **true** → silently lands on the start tab, no prompt | `MainActivity.kt:824-831` (`popUpTo(start) { saveState = true }`) |
| Editor with unsaved changes | unsaved dialog | `EditorScreen.kt:646` |
| Editor drawer open | *Material3 default* — reported broken by the owner | `EditorScreen.kt:900` |
| Hub with a project open (file tree) | **exits the app** — `activeProject` is ViewModel state, not a nav entry | `FileManagerViewModel` `activeProject`; `FileManagerScreen.kt:298-306` |
| Web Preview / Logs / Feedback | `onNavigateBack` = `popBackStack()`; fine | `MainActivity.kt:1013,1101,1136` |
| Hub `+` sheet / git sheet / branch sheet / ⋮ menus | Material3 sheet defaults | `FileManagerScreen.kt:1062,1044,1053` |

Two structural bugs fall out of that table, and both are worth more than any
single tweak:

1. **The hub's project-open state is invisible to the back stack.** Opening a
   project in the Projects tab does not push a navigation entry, so back cannot
   close it — it exits the app. Fix: a `BackHandler(enabled = activeProject !=
   null)` in `FileManagerScreen` (one line, no nav change), which is also the
   cheapest demonstration of the single-policy idea.
2. **There is no policy, only two handlers.** Phase 49 introduces
   `BackRouter` — a pure function
   `decide(state: BackState): BackAction` with
   `BackAction ∈ { CloseDrawer, CloseProjectTree, ShowUnsaved, ShowExitPrompt,
   PopRoute, ExitApp, None }` and a **precedence table** pinned by CI. Every
   screen's `BackHandler` then becomes one call, and the owner's "recheck all
   screens" is answered by one test matrix instead of ten ad-hoc patches.

### 6.3 The exit prompt (owner's decision: keep it, make it consistent)

Why it is inconsistent today, in one sentence: **it is gated on a back-stack
query, not on "am I at a root"**, so it depends on start destination, current
tab, and whether the user uses gesture navigation at all. The fix is to decide
it from *state* (`isRootDestination && nothingInAppIsOpen`) and to make the
prompt itself idempotent and re-entrant. What must **not** change: nothing is
uploaded, `NOT NOW` stays in the app, `EXIT` closes, outside taps do nothing,
the switch stays in Settings, and safe mode never shows it
(`MainActivity.kt:804-808`).

### 6.4 Rejected

- **Enabling predictive back** (`android:enableOnBackInvokedCallback="true"`):
  a real improvement, but it is a whole-app migration (every `activity.finish()`
  must become a callback, NavHost needs the 2.7+ predictive path) and CodeC
  targets SDK 28 for the W^X exec reason. Recorded as **deferred**, not
  rejected-on-merit; the `BackRouter` design deliberately does not depend on it.
- **`onBackPressedDispatcher.addCallback` in `MainActivity` with a `when`
  chain**: same logic, untestable (needs a Robolectric activity), and it is the
  exact shape the codebase has been migrating away from since Phase 30.

---

## 7. Decision table for the whole series

| Question | Decision | Why |
|---|---|---|
| New dependencies for 44–50? | **None** | Every mechanism exists in-repo (FGS, DataStore, Compose, sora APIs). License surface stays exactly as Phase 42 audited it. |
| Pure-policy-per-phase? | **Yes** | `SetupGatePolicy` (44), `GuidePlan`/`CoachMarkPlan` (45), `EditorOpenMode` (46), `DrawerPolicy` (47), `CaretVisibilityPolicy` (48), `BackRouter` (49). CI pins each; the device round proves the plumbing. |
| Settings changes? | Minimal, audit-pinned | 47.2 flips `codec_keys_enabled`'s default (no new key); 45 adds a "View the guide" row + guide flags. `docs/chat-phase38/SETTINGS_AUDIT.md` must be updated in the same commit (`SettingsAuditTest`). |
| Does any phase touch `MANAGE_EXTERNAL_STORAGE`? | **No** | Row 3 removes the only SAF-tree feature; nothing new asks for storage. Phase 38–43's standing decision holds. |
| Ordering rule inside the series | 44 first, then 45, then the rest in number order | 44 fixes the failure that breaks every other test round (a tester whose `pkg` is broken reports everything else as broken). 45's slide 3 teaches what 44 enforces. 46 must land before 47 (the drawer's project switch and the hub's "open in editor" are the same model). |
| Where does Phase 43 go? | **CANCELLED with a tombstone** | Owner: *"i want to remove it completely."* The two part docs are deleted; `chat-phase43/README.md` records what was removed and why, so the next chat does not resurrect it. |

---

## 8. Source list

**Fetched / verified this session (2026-09-12):**

- CodeC `main` @ `f3a6e32` — every file:line in §1, §2, §4, §5, §6 (read, not
  remembered).
- [developer.android.com/develop/background-work/services/fgs](https://developer.android.com/develop/background-work/services/fgs)
  — foreground-service purpose + the "noticeable to the user" test. **Fetched.**
- [developer.android.com/guide/navigation/custom-back/predictive-back-gesture](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
  — callback chain-of-responsibility; `OnBackPressedCallback` runs regardless of
  the manifest flag. **Read via search excerpt** (page not fully fetched).
- [developer.android.com/codelabs/predictive-back](https://developer.android.com/codelabs/predictive-back)
  — Material3 components' predictive-back requirement (material3 1.3.0-alpha01+,
  manifest flag). **Read via search excerpt.**
- [stackoverflow.com/q/79247909](https://stackoverflow.com/questions/79247909)
  — Compose back-handler ordering; *"your back handler ends up on the bottom of
  the stack… only the top active back handler is called"*. **Read via search
  excerpt.**
- grep.app code index of `Rosemoe/sora-editor` — `CodeEditor.ensurePositionVisible`
  / `ensureSelectionVisible` signatures and line numbers; `ScrollEvent`'s
  `CAUSE_MAKE_POSITION_VISIBLE`; a real consumer in
  `SagerNet/sing-box-for-android`. **Fetched.**
- [github.com/canopas/compose-intro-showcase](https://github.com/canopas/compose-intro-showcase)
  (Apache-2.0), `amlcurran/ShowcaseView`, `AppIntro` — license check for §3.1.
  **Read via search excerpt.**

**Not verified / marked as such:**

- Material3 1.3.1's `ModalDrawer` back registration — inferred from the
  platform docs + the M3 1.3.0-alpha01 predictive-back note. **Phase 49's first
  step is to observe it on device before changing anything** (`rule.md` §4.2:
  evidence before hypothesis).
- OEM back-gesture differences (Samsung/Xiaomi edge gestures, tablet 3-button) —
  cannot be tested in the sandbox; Phase 50's device matrix owns them.
