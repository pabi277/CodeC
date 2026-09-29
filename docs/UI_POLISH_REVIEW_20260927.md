# CodeC — full UI review and one-part-per-chat polish plan

**Date:** 2026-09-27 · **Baseline:** `main` @ `ef0e030` (PR #86 merged)
**Working branch:** `arena/01a0e1d9-codec`

## What you asked for

> Ok see the full project and docs folder
>
> Everything planed before implied now you have research on full ui and create every part a chat you add your thoughts on phases and add ask me about my thoughts as well
>
> And 1st remove the both locker system when installing something and the guide system

Interpretation: review the existing implementation and history, carry out the
explicit removal first, then prepare focused future chats with my recommendations
**and your decisions**. This is not approval to redesign every screen at once.

## 1. What is actually in this checkout

CodeC is a **native Android Compose app**, not a browser website. The checkout
contains the app, native terminal/compiler integration, package build/repository
machinery, a benchmark module, CI/release tooling, app docs and a separate website
planning workstream. There is no `website/` implementation on this branch.
A browser mockup would not test the real keyboard, native editor or WebView.

Reviewed: root README/architectural baseline; phase index/history across
`docs/chat-phase1…63`; latest handoff, phase-creation procedure, 54–58 and 59–63
roadmaps, phone/reference research; app routes/screens and relevant shared
components/policies; installer/guide callers and regression tests; build/CI
configuration; `codec-packages` and `web_docs` boundaries. This is a **repo-wide
UI inventory and focused code review**, not a claim that every line of the
compiler/package toolchain has been audited or every interaction run on Android.

### Historical docs are not the live specification

- `PROJECT_ANALYSIS.md` is an August architecture snapshot, not today's screen
  count or engine selection. Earlier phase titles can also describe rejected
  plans; e.g. the Phase 21 title must not be read as permission to remove TCC.
- Latest phase records establish the UI through 63 as built. The owner said
  top-level appearance looks good and declined further device rounds for that
  delivery. That is **not** evidence every interaction passed on a handset.
- The blank-editor height fix has its own owner-confirmed result; preserve it.
- Seven real `docs/spck-ui/Screenshot_20260922_*.jpg` images were viewed together
  this session. Earlier `0*.png` drawings are lower-priority references. The
  owner’s decision to keep four bottom tabs overrides the reference’s bar shape.
- The Account/shop screenshot does not authorise accounts, credits or AI UI.
  The reserved AI slot stays reserved for the owner's separate plan.
- **This request supersedes** Phase 45’s guide and chrome-lock requirements.
  Keep old phase records as history; never rebuild their tour from an old rule.

## 2. First change — Phase 64

[Implementation record and tests](chat-phase64/README.md).

**Removed:** both installation UI locks; first-launch guide slides; coach-mark
spotlight tour; typing tips; anchor/state plumbing; Guide entry points; guide
preference APIs; Help & guide and Reset tips in Settings/search.

**Preserved:** background setup, progress/retry, operation-readiness checks,
package transaction lock, verification, atomic recovery, single-runner busy
protection, source files/samples, resume, safe mode and normal Back handling.
An installation must not take away navigation, but an unavailable tool still
cannot honestly run and two transactions must not corrupt the same prefix.

After an editor-driven install, automatic Run resumes only if the originally
selected project/file is still selected; otherwise successful installation asks
the user to tap Run when ready. Switching files must never run the wrong file.

No replacement onboarding or new help screen. Existing stored guide flags are
ignored without resetting user data. The navigation card now has five real
choices, not a dead sixth tile. Settings/search inventory is now 64 controls.

### Phase 64 verification

Code `4de912a`: [Android CI green — run 36305311370](https://github.com/pabi277/CodeC/actions/runs/36305311370); host
unit/screenshot task and debug/release APK builds passed. Local prevalidation:
177 passed / 0 failed with a temporary JUnit shim. No handset verification
claimed. The owner subsequently instructed **“Complete docs and merge”**.
Delivery: [PR #87](https://github.com/pabi277/CodeC/pull/87); its checks and timeline record the
final-head validation and actual merge state/commit. Plan choices recorded
below are documentation updates after the tested code revision, not additional
app changes. The merge instruction does not approve implementing later phases.

## 3. My assessment of the full UI

The foundation is feature-rich enough. The next gain is **consistency, clarity
and reliable interaction**, not another collection of features or a new editor.

| Surface | Already implemented / owning code | What I would review next — not a proven defect |
|---|---|---|
| Startup and shell | MainActivity, SnakeSample, ResumePolicy, safe mode, four bottom tabs | First-frame clarity; resume/failure paths; navigation remains available during installs |
| Side panel | EditorSidePanel, SidePanelPlan; navigation/files/search/repository plus reserved slot | Selected destination, width, close behaviour, long labels; leave removed Guide space non-interactive |
| Projects | FileManagerScreen, ProjectsHub, ProjectMark | Name/path clarity, cards versus density, empty/search/recent states; import/clone/export feedback |
| File tree / single files | EditorProjectDrawer, DrawerPolicy, EditorFileTree | Project versus file selection, tree expansion, rename/delete feedback, narrow-screen actions |
| Project search | ProjectSearch and EditorScreen side-panel slot | Search scope/options, result jump, no-results/loading states |
| Editor chrome | EditorScreen, EditorShellUi, EditorChrome, tab policies | Top-bar hierarchy, dirty/active tabs, overflow, menu discoverability; preserve bounded 48dp tab row |
| Typing | Sora editor host, EditorKeysRow, optional CodeC Keys, completions/LSP | Caret/selection and IME stability; one useful coding row rather than another toolbar |
| Run/output | OutputPanelView, EditorViewModel, LanguageRunPlanner | Distinguish run/install/stdin/error/completed states; stop/retry and error jump without global locks |
| Packages | ModulesScreen, InstallMoment, SetupGatePolicy | State transitions, status freshness, unavailable tools, retry; no fake progress or unsafe parallel install |
| Terminal | TerminalScreen, TerminalViewModel, session manager/emulator | Session identity, selection/paste/extra keys, restart and setup progress; do not replace the terminal engine |
| Preview / LAN | WebPreviewScreen, PreviewToolsPanel, PreviewToolsPolicy, ServerRegistry | Viewport versus tools space, menu priority, loading/error states, console/Network semantics, sharing |
| Source control | GitControlView, GitManager, GitDiscardEditors | Readable staged/unstaged state and truthful auth/push errors; preserve exact-path unstaged-only Discard |
| Settings | SettingsScreen, SettingsSearch | Search/fold consistency, 64 live rows, honest labels and action results; no new toggles without behaviour |
| Support and secondary screens | FeedbackScreen, ExitFeedbackDialog, CrashReportOverlay, TemplatesScreen, LogsScreen | Exit-prompt preference, attachment clarity, empty states, destructive confirmation and safe-mode escape |
| Shared appearance | CodecTokens, CodecPalette, CodecType, CodecMotion, PressableSurface | Touch targets, dark/light contrast, long text, TalkBack, reduced motion, keyboard overlap and large font |

### Concrete follow-up questions found in code

1. Settings still says **“Show the welcome screen again”**, while first launch
   now seeds a sample rather than showing the old welcome screen. This label is
   a candidate for correction; I did not silently change its behaviour here.
2. `DemoProjects.ensure` still re-creates a missing Flask demo, a policy earlier
   justified by the guide. Removing the guide does not require deleting demo
   files. Ask whether deleting a bundled demo should now be permanent.
3. Exit feedback is separate from the guide. Ask whether the owner still wants
   the prompt; do not treat “remove guide” as permission to remove feedback.
4. A minimal install status elsewhere might help after unlocking navigation,
   but could also reintroduce clutter. Ask before adding any global indicator.

## 4. External research checked this session

These are platform references, not proof of CodeC device performance:

1. [Android Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
   — fetched 2026-09-27. Recommends at least 48dp interactive targets and
   appropriate built-in semantics. **Application:** preserve compact-looking
   rows without tiny or overlapping tap targets; check custom controls too.
2. [Android progress indicators](https://developer.android.com/develop/ui/compose/components/progress)
   — fetched 2026-09-27. Distinguishes determinate progress from indeterminate
   processing. **Application:** display observed progress, not invented percent;
   keep progress feedback while removing app-wide installation restrictions.
3. [Android adaptive navigation](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation)
   — fetched 2026-09-27. Describes navigation bars for compact windows and rails
   for expanded windows. **Application:** test size changes; it does not justify
   replacing the owner-approved phone navigation during this removal task.

**Library decision:** removal needs no library. For later polish, existing
Compose/Material APIs and CodeC's policy/test structure come first. No new
third-party source/assets copied, no dependency or permission added. A future
adaptive-layout library would need its own compatibility/licence/budget review.

**Rejected:** wholesale redesign; replacing Sora or the terminal to solve
spacing; treating reference store art as the real UI; adding AI/account/shop;
removing internal install safety locks; another forced tour; claiming Android
behaviour from a browser preview or source scan.

## 5. Proposed future phases — one part, one chat

**Discussion drafts, not approved work.** The numbers continue after 64 but can
be reordered/re-scoped with you before execution. Each linked brief contains:
current source anchor, my recommendation, review scope, a question for you,
acceptance checks, and a ready-to-paste chat starter. A future agent must ask
and record your answer before implementing the part.

| Proposed phase / chat | Area | My starting recommendation |
|---|---|---|
| [65.1](ui-polish-chats/PHASE_65_1_SHELL.md) | Shell and navigation | Keep navigation; refine behaviour. |
| [66.1](ui-polish-chats/PHASE_66_1_PROJECTS.md) | Projects hub and creation | Keep hub; improve clarity. **Discussed and implemented 2026-09-27** on `arena/01a0e220-codec` (owner: keep cards; deleted demo stays deleted; (a)+(b)+(c) in one part; after Create keep the hub tree) — [record](chat-phase66/README.md). Owner device-verified; delivered via [PR #88](https://github.com/pabi277/CodeC/pull/88). |
| [67.1](ui-polish-chats/PHASE_67_1_FILES_SEARCH.md) | Files, drawer and project search | Screenshot-style tree, safe file actions, file finding and single-file download/share merged to `main`; [record and CI](chat-phase67/README.md). |
| [68.1](ui-polish-chats/PHASE_68_1_EDITOR_TABS.md) | Editor chrome, tabs and file actions | Protect code space and tab geometry. |
| [69.1](ui-polish-chats/PHASE_69_1_TYPING.md) | Typing, keyboard and selection | Caret and input correctness first. **Delivered 2026-09-28** as 69.1–69.4 (typing; language keys + ghost; strip + key drag; tree + route) via [PR #91](https://github.com/pabi277/CodeC/pull/91) — [record](chat-phase69/README.md). Then, 2026-09-29, the accepted suggestion the next letter undid: [TROUBLESHOOTING §47](TROUBLESHOOTING.md), owner-tested, [PR #93](https://github.com/pabi277/CodeC/pull/93). |
| [70.1](ui-polish-chats/PHASE_70_1_RUN_OUTPUT.md) | Run, output and error recovery | Clear run states; no navigation lock. **Delivered 2026-09-28** (state row, labelled Stop, error rows + fix, waiting strip, the panel's console; phone pass 2026-09-28) — owner-tested 2026-09-29, [PR #93](https://github.com/pabi277/CodeC/pull/93); [record](chat-phase70/README.md). |
| [71.1](ui-polish-chats/PHASE_71_1_PACKAGES_TERMINAL.md) | Packages, installation and Terminal | Visible progress, safe transactions. |
| [72.1](ui-polish-chats/PHASE_72_1_PREVIEW.md) | Web preview, console and viewport tools | Polish existing tools. **Delivered 2026-09-28** (five-tab console, four render rounds incl. the `WRAP_CONTENT` → zero-layout-height cause, the phone pass; the project card opens the editor) — owner-tested 2026-09-29, [PR #93](https://github.com/pabi277/CodeC/pull/93); [record](chat-phase70/README.md). |
| [73.1](ui-polish-chats/PHASE_73_1_GIT.md) | Source control and GitHub | Simplify hierarchy; keep discard safeguards. **Implemented 2026-09-29** (the per-file stage toggle removed — it never changed what COMMIT & PUSH committed; discard/Mark Resolved/push-readiness untouched) — CI ✅ GREEN `36519004263`, owner device pass owed, not merged; [record](ui-polish-chats/PHASE_73_1_GIT.md). |
| [73.2](ui-polish-chats/PHASE_73_2_GIT_INSTALL.md) | Git connection: auto-install, clear errors, new-user guidance | Owner-reported (not a discussion draft): auto-install git, readable errors, beginner guidance. **Implemented 2026-09-29** (one-tap Install Git reusing the Packages-tab mechanism; not-a-repo message rewritten in plain words; error classification reviewed, already good) — CI ✅ GREEN `36523739665`, owner device pass owed, not merged; [record](ui-polish-chats/PHASE_73_2_GIT_INSTALL.md). |
| [73.3](ui-polish-chats/PHASE_73_3_GIT_GUI.md) | Git menu: full GUI parity with Spck | Owner-reported (not a discussion draft), 4 Spck screenshots attached: everything but git install should be a button, not the terminal. **Implemented 2026-09-29** (Fetch, Log History, Checkout Commit, Revert All with confirm, a general Remotes screen, a real `git init` button, Git Credentials shortcut) — CI ✅ GREEN `36544645579`, owner device pass owed, not merged; [record](ui-polish-chats/PHASE_73_3_GIT_GUI.md). |
| [74.1](ui-polish-chats/PHASE_74_1_SETTINGS_SUPPORT.md) | Settings, support and final consistency | Truthful controls and cross-screen consistency. |
**Owner-selected starting area (2026-09-27): Projects and files.** Start the
next discussion with 66.1 (hub/creation), then 67.1 (files/search), unless the
owner names a particular file detail first. *State on 2026-09-29:* 64, 66.1,
67.1, 69.1–69.4, 70.1, 71.1 and 72.1 are delivered and merged; **73.1 is
implemented this session (CI ✅ GREEN `36519004263`, owner device pass owed,
not merged); 73.2 (owner-reported Git connection fixes — auto-install,
clear errors, new-user guidance — not a discussion draft) is also
implemented this session (CI ✅ GREEN `36523739665`, owner device pass owed,
not merged); 73.3 (owner-reported, not a discussion draft: the whole git
menu ported to buttons, Spck-parity, per 4 attached screenshots) is also
implemented this session (CI ✅ GREEN `36544645579`, owner device pass
owed, not merged); 65.1 and 74.1
remain discussion drafts** (68.1 merged in PR #90) — the owner picks the
next discussion draft. The part numbers are stable IDs, not a requirement
to execute 65 first. My original suggestion was editor/typing; the owner's
priority overrides that. Polish one agreed part per chat.

## 6. Gate for every chat

1. Recheck current code and the relevant real shots; separate working behaviour
   from a suggested improvement or a reproduced defect.
2. Ask the brief's question; record **your** preference, not mine as your answer.
3. Agree one bounded part and its acceptance criteria; no surprise new setting,
   permission, dependency, engine rewrite or telemetry.
4. Implement with focused behaviour and wiring tests, including empty/loading/
   failure, Back, narrow layouts, long text and keyboard states as relevant.
5. Run Android CI; report what is actually verified and what is not. Do not
   convert declined handset rounds into a pass or a new compulsory owner task.
6. Update docs and stop. No auto-start of the next part; no PR/merge without an
   explicit instruction. Website plans remain a separate workstream.

## Your thoughts

- Keep the current look and polish details, or change the visual direction?
  **My vote: preserve the current look.**
- Which area should the next chat tackle first: editor/typing, projects/files,
  navigation, or another area?
  **My vote: editor/typing if you already like the top-level appearance.**
- With both installation locks gone, should progress stay only in
  Terminal/Output, or also have a small non-blocking indicator elsewhere?
  **My vote: retain existing progress locations until you want more visibility.**

### Your answers — recorded 2026-09-27

- Visual direction: **Keep the current look** — polish, not a redesign.
- First future chat: **Projects and files** — hub, creation, file drawer and search.
- Installation progress: **Keep existing progress locations** — Terminal and
  Output only; **no new app-wide status indicator**.

These choices prioritise the plan; they do not authorise automatic implementation
of a future part. Detailed card-density/demo-deletion preferences remain open.
Only Phase 64 was implemented here.
