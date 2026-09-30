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
| Startup and shell | MainActivity, OrbitSample, ResumePolicy, safe mode, four bottom tabs | First-run intro/acknowledgement and seed/replay/failure paths; returning-user resume; navigation remains available during installs |
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

1. **Resolved 2026-09-30:** Settings now says **“Replay the CodeC introduction”**.
   Fresh installs get the short, animated Orbit Shift walkthrough and required
   privacy-summary acknowledgement; the action replays it on the next launch.
   See [`FIRST_RUN_EXPERIENCE_RESEARCH_20260930.md`](FIRST_RUN_EXPERIENCE_RESEARCH_20260930.md).
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
removing internal install safety locks; a long, unskippable feature tour;
claiming Android behaviour from a browser preview or source scan. The later
short, skippable first-run walkthrough is documented separately in
[`FIRST_RUN_EXPERIENCE_RESEARCH_20260930.md`](FIRST_RUN_EXPERIENCE_RESEARCH_20260930.md).

## 5. Proposed future phases — one part, one chat

**Discussion drafts, not approved work.** The numbers continue after 64 but can
be reordered/re-scoped with you before execution. Each linked brief contains:
current source anchor, my recommendation, review scope, a question for you,
acceptance checks, and a ready-to-paste chat starter. A future agent must ask
and record your answer before implementing the part.

| Proposed phase / chat | Area | My starting recommendation |
|---|---|---|
| [65.1](ui-polish-chats/PHASE_65_1_SHELL.md) | Shell and navigation | Keep navigation; refine behaviour. **Reviewed 2026-09-29** (kept existing hide-while-typing behavior). The typing defects were delivered in [75.1–75.3](ui-polish-chats/PHASE_75_1_EDITOR_TYPING.md) via [PR #98](https://github.com/pabi277/CodeC/pull/98). |
| [66.1](ui-polish-chats/PHASE_66_1_PROJECTS.md) | Projects hub and creation | Keep hub; improve clarity. **Discussed and implemented 2026-09-27** on `arena/01a0e220-codec` (owner: keep cards; deleted demo stays deleted; (a)+(b)+(c) in one part; after Create keep the hub tree) — [record](chat-phase66/README.md). Owner device-verified; delivered via [PR #88](https://github.com/pabi277/CodeC/pull/88). |
| [67.1](ui-polish-chats/PHASE_67_1_FILES_SEARCH.md) | Files, drawer and project search | Screenshot-style tree, safe file actions, file finding and single-file download/share merged to `main`; [record and CI](chat-phase67/README.md). |
| [68.1](ui-polish-chats/PHASE_68_1_EDITOR_TABS.md) | Editor chrome, tabs and file actions | Protect code space and tab geometry. |
| [69.1](ui-polish-chats/PHASE_69_1_TYPING.md) | Typing, keyboard and selection | Caret and input correctness first. **Delivered 2026-09-28** as 69.1–69.4 (typing; language keys + ghost; strip + key drag; tree + route) via [PR #91](https://github.com/pabi277/CodeC/pull/91) — [record](chat-phase69/README.md). Then, 2026-09-29, the accepted suggestion the next letter undid: [TROUBLESHOOTING §47](TROUBLESHOOTING.md), owner-tested, [PR #93](https://github.com/pabi277/CodeC/pull/93). |
| [70.1](ui-polish-chats/PHASE_70_1_RUN_OUTPUT.md) | Run, output and error recovery | Clear run states; no navigation lock. **Delivered 2026-09-28** (state row, labelled Stop, error rows + fix, waiting strip, the panel's console; phone pass 2026-09-28) — owner-tested 2026-09-29, [PR #93](https://github.com/pabi277/CodeC/pull/93); [record](chat-phase70/README.md). |
| [71.1](ui-polish-chats/PHASE_71_1_PACKAGES_TERMINAL.md) | Packages, installation and Terminal | Visible progress, safe transactions. |
| [72.1](ui-polish-chats/PHASE_72_1_PREVIEW.md) | Web preview, console and viewport tools | Polish existing tools. **Delivered 2026-09-28** (five-tab console, four render rounds incl. the `WRAP_CONTENT` → zero-layout-height cause, the phone pass; the project card opens the editor) — owner-tested 2026-09-29, [PR #93](https://github.com/pabi277/CodeC/pull/93); [record](chat-phase70/README.md). |
| [73.1](ui-polish-chats/PHASE_73_1_GIT.md) | Source control and GitHub | Simplify hierarchy; keep discard safeguards. **Implemented 2026-09-29** (the per-file stage toggle removed — it never changed what COMMIT & PUSH committed; discard/Mark Resolved/push-readiness untouched) — CI ✅ GREEN `36519004263`, owner device pass ✅ 2026-09-29, merged via PR #95; [record](ui-polish-chats/PHASE_73_1_GIT.md). |
| [73.2](ui-polish-chats/PHASE_73_2_GIT_INSTALL.md) | Git connection: auto-install, clear errors, new-user guidance | Owner-reported (not a discussion draft): auto-install git, readable errors, beginner guidance. **Implemented 2026-09-29** (one-tap Install Git reusing the Packages-tab mechanism; not-a-repo message rewritten in plain words; error classification reviewed, already good) — CI ✅ GREEN `36523739665`, owner device pass ✅ 2026-09-29, merged via PR #95; [record](ui-polish-chats/PHASE_73_2_GIT_INSTALL.md). |
| [73.3](ui-polish-chats/PHASE_73_3_GIT_GUI.md) | Git menu: full GUI parity with Spck | Owner-reported (not a discussion draft), 4 Spck screenshots attached: everything but git install should be a button, not the terminal. **Implemented 2026-09-29** (Fetch, Log History, Checkout Commit, Revert All with confirm, a general Remotes screen, a real `git init` button, Git Credentials shortcut) — CI ✅ GREEN `36544645579`, owner device pass ✅ 2026-09-29, merged via PR #95; [record](ui-polish-chats/PHASE_73_3_GIT_GUI.md). |
| [73.4](ui-polish-chats/PHASE_73_4_GIT_STATE_FIX.md) | Device fix: git-refresh mis-reported "installed" after any error | Owner device report: "Auto install not working" / tapped Install Git, then saw and tapped "Initialize repository" on a project git was never installed on. Root cause: `refresh()`'s one catch-all hardcoded `gitInstalled = true` and left `isRepo` stale. **Implemented 2026-09-29** (manager acquisition is its own try/catch; git-status catch re-derives `isRepo`) — CI ✅ GREEN `36551505966`, owner device pass ✅ 2026-09-29, merged via PR #95; [record](ui-polish-chats/PHASE_73_4_GIT_STATE_FIX.md). |
| [73.5](ui-polish-chats/PHASE_73_5_GIT_SPCK_FULL.md) | Git panel: Spck-exact layout, install status bar, inline credentials | Owner-reported (not a discussion draft), same 4 Spck screenshots: fully terminal-independent Git GUI. **Implemented 2026-09-29** (REPOSITORY header + branch/push menus item-for-item; UNSTAGED collapsible + search; install status bar, no Terminal redirect; inline Git Credentials dialog on the shared store; Commit All = commit without push) — CI ✅ GREEN `36560766234`, owner device pass ✅ 2026-09-29, merged via PR #95; [record](ui-polish-chats/PHASE_73_5_GIT_SPCK_FULL.md). |
| [73.6](ui-polish-chats/PHASE_73_6_GIT_DRAWER_GUI.md) | Editor-drawer git: full-GUI Initialize + ask-first install prompt | Owner device report on the 73.5 build: drawer Initialize opened Terminal and init'd the wrong folder. **Implemented 2026-09-29** (Initialize = the engine's `init` in place; shared "Do you want to install it now?" prompt in drawer + sheet; drawer install/progress/retry states; auto-continue into the pending init) — CI ✅ GREEN `36566495685`, owner device pass ✅ 2026-09-29, merged via PR #95; [record](ui-polish-chats/PHASE_73_6_GIT_DRAWER_GUI.md). |
| [73.7](ui-polish-chats/PHASE_73_7_GIT_PANEL_MOVE.md) | Git moves into the editor panel: Spck-ditto dialogs, no sheets | Owner follow-up with 8 Spck screenshots, scope locked by four answers (full-panel, ditto-dialogs, all-dialogs, provider-row). **Implemented 2026-09-29** (panel hosted in the drawer slot, sheet + 73.6 slot deleted; Commit All + Push dialogs; centered Remotes/Log dialogs; rail badge; hub ⋮ routes to the panel) — CI ✅ GREEN `36583980074`, owner device pass ✅ 2026-09-29, merged via PR #95; [record](ui-polish-chats/PHASE_73_7_GIT_PANEL_MOVE.md). |
| [73.8](ui-polish-chats/PHASE_73_8_GIT_PAGE_OWNS_IT.md) | Git page owns credentials, the install card, and beginner hints | Owner punch-list on the 73.7 build, scope locked by five answers. **Implemented 2026-09-29** (⋮ push-menu trigger; package-style install card — userland live state, git elapsed + live transcript line + in-box fail tail, no fake %; token/install texts repointed at the git page; clone dialog opens credentials inline; three one-line hints) — CI ✅ GREEN `36593360018`, owner device pass ✅ 2026-09-29, merged via PR #95; [record](ui-polish-chats/PHASE_73_8_GIT_PAGE_OWNS_IT.md). |
| [73.9](ui-polish-chats/PHASE_73_9_MANUAL_FIRST_REMOTE.md) | Manual-first no-remote flow, origin by default | Owner premise fix: most tokens lack the repo-create permission, so Publish fails. **Implemented 2026-09-29** (readiness row links github.com/new + ADD REMOTE opens the Remotes dialog; New Remote name starts at `origin`; PUBLISH off the row, Publish dialog kept for permissioned tokens) — CI ✅ GREEN `36597542971`, owner device pass ✅ 2026-09-29, merged via PR #95; [record](ui-polish-chats/PHASE_73_9_MANUAL_FIRST_REMOTE.md). |
| [74.1](ui-polish-chats/PHASE_74_1_SETTINGS_SUPPORT.md) | Settings, support and final consistency | Truthful controls and cross-screen consistency. **Implemented 2026-09-29** (16sp editor default, ghost off, every section starts collapsed; exit prompt/welcome control unchanged); [record](chat-phase74/README.md). Earlier CI green; post-revert CI `36625653233` ✅ on `902c9f6` (typing reports remain open). |
| [75.1](ui-polish-chats/PHASE_75_1_EDITOR_TYPING.md) | Editor typing reliability | **Delivered 2026-09-30** as 75.1–75.3 (Python block indentation on both Enter routes, full-level brace indentation across C/C++/Java/JS/Go/Rust/Shell, one-space Backspace inside indentation, leading indentation dots + Tab alignment, suggestion word-only accept, live-buffer quick-key commits, Sora cursor/selection sync, and `{}` outside empty `(|)`) — CI ✅ GREEN (`36679767045` / `36692498787` / `36701799600`), owner device pass ✅ 2026-09-30, merged via [PR #98](https://github.com/pabi277/CodeC/pull/98); [record](ui-polish-chats/PHASE_75_1_EDITOR_TYPING.md). |
**Current owner instruction (2026-09-30):** Phase 75.1–75.3 is complete, device-passed (*"Ok device test passed … You can complete the docs part and merged"*), and merged to `main` via [PR #98](https://github.com/pabi277/CodeC/pull/98). Preserve the 74.1 Settings work (16sp default, ghost off, all sections collapsed, bespoke Settings search) and the 65.1 navigation decision. See [`docs/ui-polish-chats/PHASE_75_1_EDITOR_TYPING.md`](ui-polish-chats/PHASE_75_1_EDITOR_TYPING.md).

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
