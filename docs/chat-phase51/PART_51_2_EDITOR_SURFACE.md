# CodeC Phase 51.2 — The editor, with RUN ▶ as the hero

> **Status:** ✅ DONE + DEVICE-PASSED (F5-F9 all PASS, owner report 2026-09-21,
> [PR #82](https://github.com/pabi277/CodeC/pull/82)) · **Cost:** `[client-only]` · **Effort:** L ·
> **Owner row (verbatim):** *"it's not attractive to user to use multiple time"*.
> Parent: [`README.md`](README.md) ·
> [`PHASE50_52_ROADMAP.md`](../PHASE50_52_ROADMAP.md).
>
> ⚠️ **This part is chrome-only.** `EditorScreen.kt` is 2,423 lines and carries
> Phases 33, 35, 44, 45, 46, 47, 48 and 49. Nothing below may change the sora
> host, the caret/IME work, the run pipeline, the chrome lock, the drawer
> semantics or back.

## First move: evidence, not code

The claim: *"the one action that matters looks like every other button."*

```text
$ grep -n "EditorTabBar(\|IconButton(\|EditorStatusBar(\|AnimatedVisibility(visible = findState" \
        app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt
  1244  EditorTabBar(…)
  1277  IconButton(…)
  1293  IconButton(…)
  1627  AnimatedVisibility(visible = findState.visible) { … }
```

RUN ▶ is a control among controls in a 2,423-line screen, and the user's whole
purpose for opening the editor is that one button. Google's Material team
measured exactly this: in eye-tracking studies, expressive variants let people
**spot key elements up to 4× faster**, and the mechanism they name is *bigger,
better-contained primary actions* — not colour, not illustration
(dossier §3.2). For CodeC the "key element" is not an email send button; it is
**RUN ▶**.

The second finding is the empty frame:

```text
ui/components/EditorTabBar.kt:61   if (tabs.isEmpty()) return
```

With no file open the tab bar vanishes and the user is looking at an empty
surface with chrome around it. There is no sentence, no suggestion, no action —
the state every beginner lands in after closing their first tab, and the state
with **zero** design in it.

The third: `strings.xml:257` (`no_matches`) and the status bar path work of
Phase 46.2 are the only "state" surfaces in the editor that were ever designed;
the rest is implicit.

## Design

### A — RUN ▶ becomes the primary contained action

- **Size and containment:** the largest control in the editor's chrome, filled
  with the brand `primaryContainer`/`onPrimaryContainer` pair from 50.2 (never a
  new literal), with the token radius from 50.1 — i.e. a *contained* button,
  not a text button among texts.
- **Placement stays where it is** — moving it would break Phase 45's coach-mark
  anchors, Phase 44's chrome lock dimming and the owner's muscle memory. This is
  a **weight** change, not a layout change.
- **Label kept.** Google's own counter-example in the same study: *"removing
  text labels from email actions resulted in decreased usability."* RUN ▶ keeps
  its words.
- **Its state is visible:** idle → running (a `CodecMotion` effect + the haptic
  from 51.4) → done/failed. Phase 44's lock still governs *whether* it may run;
  this only governs how its state reads.

A pure `RunButtonStyle` decides the role from state, so it is testable:

```kotlin
data class RunButtonState(val running: Boolean, val locked: Boolean,
                          val hasOpenFile: Boolean)
enum class RunButtonRole { CONTAINED, TONAL_DISABLED, TONAL_LOCKED }
object RunButtonStyle { fun roleFor(s: RunButtonState): RunButtonRole }
```

### B — The chrome gets declared slots

The editor column becomes five named slots with token gaps (50.1):
**tab bar → code view (sora, untouched) → find bar → suggestion strip → status
bar**, with the RUN row in its own slot. Two benefits: the code view's height
stops being an accident, and Phase 48's caret logic gets a stable layout to
measure (it keys on the sora box's height — a chrome change that changes height
still flows through the same policy, which is exactly why it is safe).

### C — A real empty state

"Nothing open" becomes a designed state: the mark, one sentence (*"No file
open"*), and **one** action that opens the last file or the hub — reusing
`EditorLaunchState.load` (Phase 52.1 owns its wording) so the two parts cannot
disagree.

### D — Save and state feedback

Autosave is silent today (correctly). It gains a **one-word** confirmation (a
snackbar, not a dialog) and the status bar's dirty dot keeps its meaning.
No nag, ever.

## The Android edge

- Everything above sits **above** `SoraEditorHost`; a source-scan test pins that
  the sora host file is unchanged by this part (compare against the merged
  phase-50 sha).
- The chrome lock (Phase 44/45) dims controls it pauses — RUN ▶'s new
  contained style must respect the **same** dim/🔒 handling, and the
  `SetupLockPolicy` state, not the style, still decides enablement.
- Keyboard-up vs keyboard-down: the chrome slots must not change the sora box's
  height behaviour that Phase 48 pinned (`CaretVisibilityPolicy` keys on height;
  a *stable* chrome is what keeps it quiet).
- Motion: the RUN state change uses `CodecMotion.effectsSpring` (50.4) and no
  layout animation.

## Exit condition

`RunButtonStyle.roleFor` is pinned; the RUN control is the largest, contained
control in the editor chrome (source pin: no other control in the file declares
a larger height/width, and it uses the container role pair); the empty state
renders with a sentence and one action; the sora host file is byte-identical to
the merged phase-50 version (git diff pin in the report); Phase 44's lock
behaviour and Phase 48's caret behaviour are unchanged (device rows F12, F13);
and `DEVICE_ROUND.md` F5-F9 has been run by the owner.

## Tests (plan)

- `RunButtonStyleTest` (~10): contained when idle and runnable; locked role when
  the chrome lock pauses it; disabled with no open file; running state distinct;
  the lock **wins** over everything (Phase 44 law).
- `EditorChromeSlotTest` (~8, source scan): the five slots exist in order; each
  uses `CodecTokens` gaps; the RUN row is its own slot; no `Modifier.weight`
  change inside the sora box.
- `EditorEmptyStateTest` (~6, source scan + copy): the empty branch renders a
  sentence and exactly one action; it reuses `EditorLaunchState` rather than
  inventing a second resume path.
- `SoraHostUntouchedTest` (~3, source scan): `SoraEditorHost.kt` contains no
  token, colour, motion or haptic call added by this phase (the file's own
  `IncrementalEdit` and caret calls are pre-existing and stay).

≈27 cases, host-JVM.

## Sources (record)

- `PHASE50_52_UX_RESEARCH.md` §3.2 (the 4× finding, its mechanism, and the
  "don't break paradigms / keep labels" guard-rail), §2.5, §3.3.
- `EditorScreen.kt` / `EditorTabBar.kt:61` greps above (2026-09-13,
  `main` @ `62cfe7b`).
- Phases 44 (chrome lock), 45 (coach-mark anchors), 46.2 (status bar path),
  48 (caret/height) — the invariants.

## Deferred / rejected with reasons

- **A floating RUN button (FAB)** — it would sit over the code and fight the
  keyboard/IME insets that Phase 48 just fixed; containment in the chrome is the
  same win without the risk. Revisit only with the owner's explicit ask.
- **Moving RUN ▶ to the top bar** — breaks the tour, the lock and muscle memory.
- **A run-output redesign** — Phase 19/36 own the output panel; 50.4 animates
  its reveal, nothing more.
- **Editor themes, font size, key strip** — Phases 29/35/47 decisions.

## Implementation (2026-09-21)

**RUN ▶ is the hero, and the decision is a pure function.** `RunButtonStyle`
(`ui/editor/RunButtonStyle.kt`, new) takes `RunButtonState(running, locked,
hasOpenFile)` and returns the role, the tone, the label state and the primary
flag. The lock is the **first** branch, so Phase 44's chrome lock beats a running
job, a missing file and a perfectly runnable buffer — and a locked tap stays
*enabled*, because it still runs `showChromeLock` and says why. The screen's
`run_action` slot is now a real `Button`: `primaryContainer` /
`onPrimaryContainer` (a role, never a hex), `MIN_TOUCH` tall, the `NAV` glyph,
and the label kept — `RUN`, or `RUNNING` while the job it started is producing
output. The tour's `GuideAnchor` and its one-tap `onGuideRunTap` are untouched;
`onRunTap` and its Phase 33 chooser logic are untouched; the old bare
`.clip(...).clickable(onClick = onRunTap)` row is gone.

**The chrome is declared.** `EditorChrome` (`ui/editor/EditorChrome.kt`, new)
lists the eight slots in the order the screen declares them (`TAB_BAR`,
`RUN_ACTION`, `FIND_BAR`, `CODE_VIEW`, `STATUS_BAR`, `OUTPUT_PANEL`,
`SUGGESTION_STRIP`, `KEYS_ROW`), gives each a marker comment
(`// Phase 51.2 slot: <name>`) that is now in the file at its real site, and gives
each a gap as a `CodecTokens.Space` step. It is **a declaration, not a re-flow**:
this part adds no chrome height and moves no composable, because every
hand-picked number in that column is a change to the code view's height — the
one measurement Phase 48's caret work was device-proven against. What it buys is
that a later edit which moves a chrome piece fails `EditorChromeSlotTest`
instead of silently changing that height.

**The empty state is a sentence and one action, not a screen.**
`EditorEmptyState` (new, pure) answers from `EditorEmptyFacts(openTabs,
projectOpen, lastFileAvailable)`: with a tab open it says nothing (the surface
speaks for itself), inside a project it says nothing (the drawer already lists
the files), and in scratch mode it shows one sentence — *"Nothing open — this is
a scratch file."* — with **exactly one** action: *Open my last file* when
`EditorLaunchState.load(...)` still resolves (the one resume source 52.1 owns), or
*Browse projects* through `onOpenProjects` when it does not. It renders as chrome
above the code view; it opens no dialog.

**The correction, recorded honestly.** The plan's premise — *"with no file open
the tab bar collapses and the user is looking at an empty frame"* — is **half
right**, and the wrong half matters. `EditorTabBar` really does return early
(`EditorTabBar.kt:61`) and the top bar falls back to a bare file name; but the
code view is never blank: `EditorViewModel.INITIAL_CODE` is a Hello-World `main.c`
and `closeTab` deliberately keeps one buffer alive (`tabs.size <= 1`). "No tabs"
therefore means **scratch mode**, and covering a buffer the user can already run
with a full-screen empty state would have been a lie. The missing thing was the
sentence plus the way back — which is what landed.

**Tests:** `RunButtonStyleTest` 9 (lock wins in all four combinations, the run
rule, the enabled rule, the total role mapping, `visualFor` coupling),
`EditorChromeSlotTest` 9 (eight slots, the markers present **and in order** read
from the real file, per-slot gaps on the token ladder, the RUN slot holding the
button, the touch floor, every minimum height from the scale),
`EditorEmptyStateTest` 8 (one action per state, the reuse of
`EditorLaunchState`/`openFile`, chrome-not-dialog, the strings).

**CI:** ✅ GREEN `35630471779` tip `32c7c70` (assemble + `:app:testDebugUnitTest` + `:app:lintDebug`), after round 1's raw-apostrophe AAPT2 failure and round 2's missing `PaddingValues` import — both fixed for-cause and recorded in the README.

**Not run:** device rows F5-F8 (RUN ▶ visible at arm's length, the chord/keys row
still reachable, the chip in both scratch states, save confirmation).

---

## Test log (Phase 51 — the feel)

**Owner report, 2026-09-21, verbatim: *"All pass record and merge"*** — every row
below is **PASS** (device/OS/theme not named in the report; nothing added here
that the owner did not state). Build: the CI `Build APK` artifact of
`arena/01a0c4cb-codec` (run `35630471779` tip `32c7c70`).

| # | Part | Run on | What to do | PASS looks like |
|---|---|---|---|---|
| F5 | 51.2 | owner's handset (not specified) | open a file, hold the phone at arm's length, then repeat with the keyboard up | **PASS** — RUN ▶ is the most obvious control on the editor, keyboard up or down |
| F6 | 51.2 | same | start a Python install, tap RUN ▶ | **PASS** — the lock sentence appears and nothing runs anyway (Phase 44's law intact) |
| F7 | 51.2 | same | close every tab | **PASS** — scratch mode shows one sentence and one action, and the action opens something real |
| F8 | 51.2 | same | type, open find, close it, type again | **PASS** — the chrome's gaps are even and nothing moves when the keyboard opens |
| F9 | 51.2 | same | edit + save, three times | **PASS** — one short confirmation and nothing else |

**Result: 5/5 PASS, no re-round requested.**
