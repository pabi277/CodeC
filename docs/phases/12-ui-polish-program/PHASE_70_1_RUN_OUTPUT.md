# Proposed Phase 70.1 — Run, output and error recovery

**Status: discussion draft, not approved for implementation.** One part = one
future chat. No deadline, dependency, new control or replacement engine promised.

## Copy into a new chat

> Review `docs/journal` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/components/OutputPanelView.kt:99`](../../../app/src/main/java/com/codeci/ide/ui/components/OutputPanelView.kt#L99), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** No reference screenshot shows output. Agree the desired behaviour before designing a new panel.

## My recommendation

Keep one runner owner. Make running, installing, waiting for input, failed and completed states distinguishable without locking unrelated navigation.

## Bounded review checklist

Offline C; language missing; explicit install confirmation; default/open-file chooser; interactive stdin; output collapse; stop; test runner; compile-error jump; server lifecycle; no-file state.

## Your thoughts — ask before code

**After a run, should output open automatically every time, or primarily on errors?**

Suggested starting point, not your decision: Keep current auto-opening behaviour initially; separate idle success from failure visually before changing routing.

**Owner answer:** pending. Record it verbatim here when given; do not present the
recommendation as approval.

## Implementation and exit, after agreement

OutputRunState, LanguageRunPlanner, InteractiveRunSession, RunButtonStyle and CompilerRemediation; no second job on an occupied runner.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.

---

# Review of the current implementation — 2026-09-28 (this chat)

**Status: read and reviewed; six questions with the owner; no code written.** This
section is the review of `main` @ `fbb3056` (the Phase 69 / PR #91 merge), read on
the session branch `arena/01a0e704-codec`. Line numbers are this checkout's.

**Reference boundary, restated.** No shot shows output. Nothing here redesigns the
panel; the recorded boundary stays (`SPCK_PHONE_UI_REPORT.md` §5: *no permanent
output strip … output appears only when a run has something to say*;
`PHASE54_58_PHONE_UI_ROADMAP.md` line 112: *a permanent output dock — no shot shows
a run*).

**Not device-confirmed.** None of 69.1–69.4 has a device round, and this review does
not record or imply one. Nothing in this section is device evidence.

## What already works — read on this checkout, not remembered

1. **One runner owner.** `runFile` returns early while `busy` (`EditorViewModel.kt:3262`);
   every run / install / test / format / server job replaces `_outputState` wholesale;
   `stopRun` (3224) cancels the job, stops the foreground notification, kills the
   interactive PTY and stops servers, and writes CANCELLED only if the state was still
   busy — a stale tap cannot overwrite a finished run.
2. **No permanent dock.** `hasContent()` (210) plus the collapsed gate
   (`EditorScreen.kt:2443`) hide the strip before the first run, while typing and after
   Clear; `OutputPanelVisibilityTest` (5 cases) pins the rule.
3. **Every run opens the panel** — five sites (`3501` run, `3681` server, `988` install,
   `2935` tests, `2825` format) — and it opens **before** success or failure is known.
   That is today's answer to the brief's first question.
4. **Back closes it** through the one table: `BackRouter` row 6
   (`outputPanelExpanded && !keyboardVisible → CollapseOutputPanel`), pinned by
   `BackRouterTest` and `BackHandlerWiringTest`.
5. **The facts are already carried.** `OutputRunState` (160) carries phase (6 values),
   per-line kind (10), build/run exit codes and durations, `busy`, `summary`,
   `lastTerminalCommand`, `waitingForInput` + `inputBuffer`, `testRun`, `installing`,
   `serverUrl` / `serverRun` / `serverEndpoints` / `lanShared` / `servers`. What is
   missing is that most of them are never *drawn*.
6. **Interactive stdin is real.** PTY-first through `InteractiveRunSession.start`, with
   the piped `ExecutionRunner` as the fallback; `PtyLineBuffer` emits partial prompts;
   the inline field is the last list item while `waitingForInput`, with `ImeAction.Send`
   and a send icon; the VM API is `onInputChange` (1152), `submitInput` (1158),
   `appendInput` (1165), `interruptRun` (1175, `PtyNative.SIGINT`).
7. **Compile errors already do the right thing.** `finishFailedBuild` (3948) re-colours
   diagnostic lines, appends the build-failed summary, the no-`main` hint (Phase 33) and
   the Termux remedy (Phase 38.2), and feeds `_diagnostics`, which is the same parse the
   squiggles and the status-bar count read; a tap opens the file at that line (Phase 32.3)
   and a missing `;` gets one-tap Apply. `LanguageRunPlanner` and `CompilerRemediation`
   are pure and host-tested.
8. **The install gate is honest.** `promptInstall` (923) asks `SetupGatePolicy` first and
   the pill (Phase 58.2) says to finish the Linux tools; the sheet names the package and
   the download size; after a successful install the run resumes only if the same
   project and file are still selected (Phase 64), otherwise the panel says "tap Run when
   ready". `installing = true` is progress metadata; navigation is never locked.
9. **The default-vs-open chooser** is pure (`ProjectRunTarget.chooserDefault`) and
   deliberately absent in `SINGLE_FILE` mode (Phase 46.2).

## What the reading found — candidates, all inside one bounded part

1. **The panel cannot be read at a glance.** The header draws one `summary` string whose
   colour is `if (state.busy) Color(0xFF66B2FF) else MUTED_TEXT` (`OutputPanelView.kt:174`),
   so *done*, *failed*, *stopped* and a *finished install* are the same grey text.
   `OutputPhase.FAILED` is only `failRun`'s state: a failed **build** sets `DONE`
   (3948). And two fields are write-only — `installing` and `serverRun` have no UI reader
   (grep).
2. **`clearOutput()` leaves an empty expanded panel.** `hasContent()` gates only the
   collapsed strip; the expanded body has no empty state, so Clear with the panel open
   leaves a bare dark box with a header.
3. **The header's actions are 36 dp** — under the app's own 48 dp floor — and
   `TouchTargetTest`'s own comment defers them to *"51.2's editor-surface pass"*, which
   was never run: stop, preview, terminal, copy, clear and collapse are six 36 dp taps in
   a row.
4. **The panel's palette is a second, unaudited set.** `#55FF55` (STATS/TEST_PASS) vs
   `SUCCESS #66BB6A`; `#66B2FF` (SYSTEM) vs `INFO #64B5F6`; `#FFB347` (TEST_ERROR) vs
   `WARNING #E6B33C`; `Color.LightGray` icons. `AppContrastTest` audits `CodecPalette`,
   not this file, so none of these pairs is measured.
5. **The Phase 23.2 run-keys row is unreachable.** `KeysStayPolicy.isVisible` returns
   false while `waitingForInput`; `stripContextFor(stripVisible = false, …)` returns
   `Hidden` (StripContext.kt:126); and both strip call sites are gated by `keysVisible`
   (`EditorScreen.kt:2330/2474`). So `StripContext.Run` → `RunKeysRow` can never be
   composed, and the inline field's 32 dp send icon is the only touch door while a
   program waits on stdin.
6. **The keyboard's competition is uncapped.** `outputPanelHeight` (220 dp default,
   55 % of screen height, `EditorScreen.kt:2407`) does not change when the IME is up, and
   the editor's box is the `weight(1f)` sibling, so the code view can be squeezed to a
   sliver (the Phase 48/69.1 caret rule keeps the caret visible inside whatever is left).

## Questions put to the owner — 2026-09-28

**Owner answers:** pending. Record them verbatim here when given; do not present any
recommendation above as approval.

| # | Question | Owner's answer (verbatim) |
|---|---|---|
| Q1 | After a run, should the panel open automatically every time, or primarily on errors? | **A — Every run, exactly as today (recommended)** (answered on the second ask; the first answer was a new set of points instead — quoted below) |
| Q2 | How should running / waiting / failed / completed be distinguishable? | **A — State word + icon, and the exit code on done/failed (recommended)** |
| Q3 | What should happen when a program waits for input and the panel is not on screen? | **A — The strip says “Waiting for input — tap to answer”, and the ↵ key opens/focuses the field (recommended)** |
| Q4 | What should the way out be while a run or an install is live? | **A — One labelled Stop at the leading edge, 48 dp; while an install streams it asks once (recommended)** |
| Q5 | With the keyboard up, what should give way? | **A — Cap the panel lower while the keyboard is up (recommended)** |
| Q6 | What should a tap on a compiler-error line do to the panel? | **A — Collapse the panel to its strip on the jump (recommended)** |

## The two new items the owner added — 2026-09-28, verbatim

The first answer to Q1 was not an option at all but three new points:

> *"Check the full output panel code and make the above ber less like the editor
> compact, the console is not good i can't run any console command, the html
> preview is very bad only the index.html load good others are very small screen
> very bad experience i will upload some screenshot after this ok"*

They were put back to him as three questions. His answers:

| # | Question | Owner's answer (verbatim) |
|---|---|---|
| N1 | Which bar is “the above ber”, and how should it change? | **No option picked:** *"I will give you example screenshot"* — the shape is **not yet agreed**, and nothing may be designed for it until that shot arrives. |
| N2 | “The console is not good i can't run any console command” — which surface, and what should it do? | **A — The Output panel itself should take commands** — the option's own words: *“I type ls, python main.py or pkg install … into the output area at any time and it runs there, like a terminal.”* |
| N3 | The HTML preview (“only the index.html load good others are very small screen”) — recorded here, answered in the Preview chat. | **Belongs to Phase 72.1**, not this part; recorded so it is not lost. |

**Scope decision (verbatim):** **B — Wait for my screenshots before any code. Then
do the panel work in one go, look and behaviour together.**

### What N2 means for the part (honest first reading, nothing designed yet)

The Output Panel is a **run log**, not a shell: it renders `OutputRunState` and only
accepts typing while a program is `waitingForInput` (the inline field). Making it
*take commands at any time* means a second job would have to enter the panel — and
this codebase's own law (`runFile`: `if (_outputState.value.busy) return`) is that an
occupied runner runs nothing else. Two live routes already exist and both are the
Terminal's: `PtySession`/`ShellBootstrap` (the interactive session) and
`ExecutionRunner` (piped batch). Any design therefore has to answer, before code:
which owner a panel-typed command belongs to, what happens when a run is already
busy, whether the Terminal's session is reused or a panel-owned one is started, and
how the panel says which of the two it is showing. **No new dependency, permission,
preference or telemetry is on the table** (standing rule), and the Terminal engine
is not replaced (Phase 19/28 boundaries).

### Where this chat stands — 2026-09-28

- **No code, no commit, nothing merged.** The whole of this chat is reading, six
  questions and their answers, and this record.
- **Held on purpose:** the owner's screenshots (the “above ber” example) and the
  design of the panel-as-command-surface (N2). Scope decision B: when the shots
  arrive, the panel work happens **in one go — look and behaviour together**.
- **Settled and ready:** Q1 = A, Q2 = A, Q3 = A, Q4 = A, Q5 = A, Q6 = A, plus the
  six findings above (the state projection, the empty expanded panel after Clear, the
  36 dp header actions, the unaudited panel palette, the unreachable run keys, the
  uncapped panel with the IME up).
- **Nothing here is device evidence**, and none of 69.1–69.4 has a device round; no
  acceptance is recorded or implied.
- **Stop point:** this part only. No PR, no merge, no `main` push without the
  owner's explicit instruction.

## 2026-09-28, later — the screenshots arrived, and they are the *preview*, not this panel

Six more shots arrived (saved to `uploads/`: `…134154`, `…134206`, `…134212`,
`…134218`, `…134226`, `…134232`), with the owner's message, verbatim:

> *"Try to make output exactly same for web view"* and *"And terminal output is
> good enough but is hard to understand the error line from terminal"*

**What the six shots are:** SPCK Editor's **Web Preview** (its label is in the
filename; the page shown is the owner's own site). They are a *preview* reference,
not an editor-panel reference:

1. the preview of `index.html` — top bar with the page title, a **“Preview · 100 %
   Zoom”** subtitle, a console-toggle icon, refresh, ⋮; the page rendering full
   width, phone-sized;
2. the same on other pages of the site (so *other pages do load and render at
   phone size in SPCK* — the opposite of CodeC today, which is the owner's earlier
   *“only the index.html load good others are very small screen”*);
3. the **console** open under the page: a five-tab strip **Console · Elements ·
   Network · Resources · Settings**, a filter row **All · Info · Warning · Error**
   with filter and copy icons, an empty body with a caret;
4. the same console with the keyboard up and **Cancel · Execute** above it — i.e.
   **SPCK's console takes commands** (*“i can't run any console command”* was about
   this console);
5. the **Network** tab: a table with **Name · Method · Status · Type · Size ·
   Time**;
6. the preview's ⋮ menu: **Open in Browser… · Back · Forward · Zooming · Screen
   Resolution · Clear Cache · Exit**.

**So the “console” item (N2) and the “above ber” item (N1) are about the Web
Preview, not this editor panel** — they belong to
[PHASE_72_1_PREVIEW.md](PHASE_72_1_PREVIEW.md), where the owner's input is now
recorded. This part (70.1) keeps its own five settled answers (Q1–Q6 = A) and its
six findings, still **not implemented**: the owner's scope decision was “wait for
my screenshots before any code”, and the screenshots turned out to describe the
preview. The owner is asked which comes first.


## Implementation (2026-09-28) — the panel part, after the preview

Q1–Q6 are all **A** (the table at the top of this file), and the panel they
describe is now built. Branch `arena/01a0e704-codec` (base `main` @ `fbb3056`),
commits `11345c0`, `2590429`, `3ad83c7`, `d86b4a3`; **CI: `Build APK` run
[36399610563](https://github.com/pabi277/CodeC/actions/runs/36399610563) GREEN on
tip `d86b4a3`** (2347 tests, 0 failed).

### The six findings, and what each became

1. **The header said “Output” for every state** → `OutputPanelStatus.head()` is
   the one state projection, in a fixed order: waiting for input wins, then busy
   (with *Installing* as its own word), then a live server (*Serving*), then the
   terminal phases — where **a build that failed reads as Failed** even though
   `finishFailedBuild` stores `DONE` (`buildExitCode != 0`), a non-zero run exit
   is Failed, and a zero one is Done with its exit code. The row shows icon +
   state word + `exit N` (Q2) on a 48 dp bar.
2. **Clear left an expanded empty panel** → the empty state now says
   `output_empty_hint`, and the collapsed strip says `output_strip_hint` until
   something runs.
3. **36 dp header actions** → a 48 dp status row and a 48 dp action row — the
   labelled **Stop** leads it (Q4), the rest scroll horizontally so nothing
   squeezes on a narrow phone. `TouchTargetTest`'s note that deferred the panel's
   under-size buttons to “51.2's editor-surface pass” is discharged for the
   panel.
4. **The palette no one audited** → `OUTPUT_COLORS`, `PANEL_BACKGROUND` and
   `PANEL_HEADER_BACKGROUND` are named and pinned by `OutputPanelContrastTest`:
   all ten line colours clear WCAG AA on `#121212`, and the seven state/header
   tints clear AA on `#252526`. The terminal colours themselves are unchanged.
5. **The run keys were unreachable while a program waited** → `KeysStayPolicy.isRunStripVisible(keepOpen, waitingForInput, explicitlyCollapsed)`
   keeps ↵ / Ctrl+C / Tab alive for exactly the stdin case `isVisible` yields to,
   and the screen gates both strip positions on
   `stripVisible = keysVisible || runStripVisible`. The collapsed strip says
   *“Waiting for input — tap to answer”* (Q3), and ↵ with the panel collapsed
   opens the field first.
6. **The panel ignored the keyboard** → `OutputPanelHeight.resolve(requested,
   available, imeVisible)`: 55 % of the screen normally, **38 % with the IME up**,
   floor 160 (room for the status row, the actions and the line), default 220;
   the splitter drag and the panel height both go through it (Q5).

**The error line — his own words, *“hard to understand the error line from
terminal”*** — is now two rows: the location and severity (`main.c:12:5 - error`)
and then the message, with the existing *Add missing ;* fix beside it when
`CompilerDiagnostics` can offer one; and a banner above the list counts the
parseable diagnostics (`3 errors · 1 warning`) so the user does not have to
count red lines. `OutputPanelStatus.counts` counts only parseable diagnostics —
a location-less line is not an error, exactly as `OutputLineParserTest` pins.

**N2, answered in the same chat, is delivered as well.** The owner's words:
*“I type ls, python main.py or pkg install … into the output area at any time and
it runs there, like a terminal.”* The panel's one line now has two meanings
(`submitInput(context)`): while a program waits it is that program's stdin,
otherwise it is a console command. Before code, the four questions this file
listed were settled as: **the owner is the panel's own runner** (the panel is the
output surface, not a second terminal); **no second job on an occupied runner** —
a line typed while something is busy is refused with a line saying so, never
queued or interleaved; **the shell is the app's real environment**
(`ShellBootstrap.prepare` + the PTY-first `InteractiveRunSession.start`, the same
pair RUN ▶ uses, so `ls`, `python main.py` and `pkg install` behave); and **the
place is the active project's root**, else the folder the editor keeps single
files in. The line is bounded at 4096 characters, and an exit finishes the panel
with the command's own words (`output_console_exit`) and DONE/FAILED.

### Tests

`OutputPanelStatusTest` 9 cases (state precedence, the failed-build case, the
counts, the height law), `OutputPanelWiringTest` 5 pins, `OutputPanelContrastTest`
3 cases, and `EditorRowsWiringTest` updated to the new strip gate. Together with
the preview's tests the branch's CI run executed **2347 tests, 0 failed**.

### Red rounds, for the record

Each was fixed for cause, none papered over: `11345c0` failed to compile
(`OutputHead.Done` is a data class — `is` in four `when`s — and the Crossfade
needed the `motion` hook in scope); `36398809324` failed the test compile
(`OutputPanelStatus.counts` takes `List<String>`); `36399166831` failed one test
(a policy test compared the merged rows with the unmerged ones); the last red
round was the height test asserting the 55 % cap with a request *below* it.

### Still open

**N1 — the “above ber” — is still unanswered**, and nothing was designed for it:
none of the six shots identifies that bar, so the shape has not been agreed.
Q1's behaviour (the panel opens on every run) is unchanged and already true. And
**this is not device evidence**: nothing in 70.1 or 72.1 has been on a handset,
and the five owed handset rows for 69.1–69.4 stay owed. No PR, no merge, no
`main` push without the owner's explicit instruction.

### Phone pass (2026-09-28, later) — the default height

The owner asked for this phase's surfaces to be phone-friendly. Measured: the 220 dp default
held the status row, the Stop row and the command line (144 dp) and left ~76 dp — three or four
lines — or ~44 dp, two lines, once the error-count banner was there. Asked (keep 220 / ≈ 40 % /
≈ 50 %): **"Your choice"**. This chat's choice: **40 % of the screen**
(`OutputPanelHeight.DEFAULT_FRACTION`, `defaultFor(screen)`; about 320 dp, nine lines). Q2–Q6's
rows and the 55 % / 38 % caps and the 160 floor the owner chose are untouched; only what the panel
opens at changed. `OutputPanelStatusTest` and `OutputPanelWiringTest` pin it. Record:
[`../chat-phase70/README.md`](chat-phase70/README.md) §Round 5. **Device pass ✅ — owner, 2026-09-29: *"Yes all test passed"*** (no device, OS or theme named).
**Merged to `main` on the owner's command via [PR #93](https://github.com/pabi277/CodeC/pull/93)**;
the whole phase (70.1 + 72.1, the render rounds, round 5) went in that one PR.

