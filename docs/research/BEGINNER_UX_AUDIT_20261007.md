# Beginner-friendliness audit — every inch of CodeC's UI, and what the evidence says to do about it

> **Status:** 📋 **RESEARCH ONLY — NO APP CODE CHANGED** (2026-10-07).
> Owner's brief: *"Analysis the current ui and research thoroughly online, open source anything that can be helpful find every inch to make it beginners friendly … No code changes just research."*
> Reviewed revision: `ccae8d46` (`main` @ merge PR #117), branch `arena/db36a12a-codec`.
> Companion artifact: [interactive wireframe appendix](beginner-ux-mockups/index.html) (HTML, no app code).
>
> **Evidence limits, stated up front.** (1) No device round was run and no build was installed in this session — every claim about the app comes from the source at this revision plus the repository's own reference images (`docs/reference/spck-ui/`), not from a handset. (2) The owner's attached screenshots did not arrive with the message; the analysis stands on the repo, and the screenshots can be folded in later. (3) External research is cited with links and a confidence note; nothing here authorises implementation.
>
> **A rule this document obeys.** Phase 64 removed the guide system (slides, coach marks, typing tips) on the owner's instruction, and its exit condition says: *"No new help screen, replacement onboarding, or reset switch"* — *"Never reintroduce a tour or installation navigation lock from an older phase document."* Nothing below is a tour, a coach mark, an overlay, or a lock. Everything here is either **wording**, **ordering**, **a door to something that already exists**, **an inline explanation inside the surface where the confusion happens**, or **accessibility**.

---

## 0. Executive summary — the twelve inches that cost a beginner the most

Ranked by *beginner pain per unit of risk*. Each one is expanded in §3 and §7.

| # | The inch | What's wrong today (one line) | Evidence |
|---|---|---|---|
| 1 | **The Templates library has no door** | A 5-template, difficulty-rated, concepts-explaining screen is fully built and composed — and no code path in the app navigates to it. | `Screen.kt:87`, `MainActivity.kt:1456`, zero callers |
| 2 | **The 19-chapter beginner course is web-only** | `website/learn/` is a complete CC BY 4.0 course (first chapter to Git to "Ask about your code"); the app links to GitHub and nothing else. | `website/learn/index.html`, `website/learn/licenses/SCOPE.txt`; no matching URL constant in `app/src/main` |
| 3 | **Jargon before grammar** | "userland", "Linux tools", "packages", "toolchain", "repository", "detached HEAD", "PTY/session" meet a first-time user inside their first five minutes. | `strings.xml` `terminal_intro_*`, `git_*`, `modules_title` |
| 4 | **One concept, two names** | "Modules" vs "Packages"; "Source Control" vs "Repository" vs "Git"; "Files" vs "Projects" vs "Project files". | `strings.xml`, `Screen.kt:87/95`, `EditorScreen.kt:1954` |
| 5 | **A tap on a file is an undocumented mode change** | Tapping a file in the hub opens a *single-file peek* (no project chrome, no tree, no Git) while ⋮ → *Open in editor* opens the project. Nothing says so on screen. | `MainActivity.kt:1373-1385`, Phase 46.2 comments |
| 6 | **Raw compiler text is the only teacher of errors** | Diagnostics are parsed, counted and jumpable — but the sentence a beginner must act on is still `cc: error: …`. | `CompilerDiagnostics.kt`, `OutputPanelStatus.kt`, `strings.xml` `fix_add_semicolon` |
| 7 | **Hidden gestures carry real weight** | Long-press (drawer project rows, tab rows, file rows), drag (panel, keys, AI bubble, space-bar trackpad), edge-swipe (drawer). | `EditorProjectDrawer.kt`, `EditorScreen.kt:2092+`, `AiBubblePolicy.kt` |
| 8 | **The primary action lives in the "ow" zone** | RUN ▶ is top-right — the hardest reach on a 6.7" phone — while the thumb-zone strip below the code carries cursor keys. | `EditorScreen.kt:1942-2010`; Hoober §4 |
| 9 | **Onboarding asks for reading before doing** | 2 s logo → 5 auto-timed stories (~10 s each) → privacy sheet → "Agree & start coding" → Arcade. Nothing is tappable code until then. | `FirstRunIntroScreen.kt:110, 436`, `CodecMotion.Duration.STORY` |
| 10 | **Settings points at files the user cannot open** | "docs/guides/DATA_AND_PRIVACY.md in the repository — …" is shown as a setting subtitle. There is no door; it is a path string. | `SettingsScreen.kt:1179` |
| 11 | **Accessibility stops at touch targets** | 48 dp is enforced in six core files; headings, live regions, state descriptions, and drag alternatives are largely absent, and the editor font is fixed. | `TouchTargetTest.kt`, only one `heading()` in the whole app (`AiMarkdownView.kt:177`) |
| 12 | **No answer to "what should I make?"** | Three starter tiles exist in one empty state; the Arcade sample exists; the five C templates exist. Nothing connects "I'm new" → "here is the next thing to try". | `WelcomeStarters.kt`, `GameArenaSample.kt`, `Template.kt` |

**The one-sentence thesis.** CodeC is no longer short of features, it is short of **signs**: the beginner-friendly material is already in the repository (templates, a course, plain-words copy in patches, honest error states), and the highest-value work left is *making existing things findable, nameable, and explainable in place* — not building a second product for beginners.

---

## 1. What a beginner actually meets today (walkthrough, from source)

Timings are structural, not measured on a device; routes and strings are quoted from this revision.

| Beat | What happens | Where it lives |
|---|---|---|
| Install → first frame | Splash is the app mark (`installSplashScreen`, Phase 51.1); released by the pure `LaunchReadiness` policy, never a timer. | `MainActivity.kt:271-283`, `ui/crash/LaunchReadiness.kt` |
| Logo opening | 2 000 ms of the CodeC mark + "YOUR POCKET CODING STUDIO", then the stories. | `FirstRunIntroScreen.kt:110, 117-152` |
| 5 timed stories | Auto-advance on a 10 s story timer; swipe or "Skip" jumps to the last page ("Skip to agreement"). Story 4 hands over to *CodeC Arcade*; story 5 is the privacy summary. | `FirstRunIntroScreen.kt:180-195, 251-262, 384-400, 436` |
| Privacy | A required checkbox + "Agree & start coding"; details sheet opens in-place. Device permissions are **not** requested here (good). | `FirstRunIntroScreen.kt:1019-1096`, §7 |
| First destination | `firstOpenSample` → the **Arcade** sample project opens directly in the editor on `index.html`-family entry. | `MainActivity.kt:933-946`, `GameArenaSample.kt` |
| Editor | 48 dp top bar: ☰ (cd "Project files") · file mark · breadcrumb (tap = **rename** dialog) · 🔍 · (optional Test ✓) · RUN ▶. Then the open-tab row, the code view, the coding strip (language caps first, then Tab/arrows/symbols), the status bar (Ln/Col, Spaces, language, encoding), and the Output strip. | `EditorScreen.kt:1942-2010, 2026-2120, 2738-2790` |
| Run | RUN expands the Output panel by itself (`_outputExpanded = true` on the run path) and the header tells the truth: state word, exit code, `3 errors · 1 warning`, plus Stop / Open in Terminal / Copy / Clear. Panel default height is 40 % of the screen, ≤55 % normally, ≤38 % with the keyboard. | `EditorViewModel.kt:320, 3920`, `OutputPanelHeight.kt:31-40`, `OutputPanelStatus.kt` |
| Projects hub | Reached from the side panel's Navigation card or the drawer ("+ New project…"); not a tab since Phase 56. Empty state = the three `WelcomeStarters` tiles + "Create your first project". | `MainActivity.kt:783-789, 1290-1345`, `FileManagerScreen.kt:2280-2310` |
| Packages | Bottom tab "Packages" → screen titled from `modules_title` = **"Module Store"**, grouped by `PackageCategory` (Compilers / Editors / Languages / CLI tools) with per-card install, sizes, pins, retry. | `Screen.kt:95`, `strings.xml:283`, `ModuleCatalog.kt:35-240` |
| Terminal | Intro card states the three truths (ready / installing / needs setup) in plain words, and C-works-offline is said out loud. | `TerminalScreen.kt:426-455`, `strings.xml` `terminal_intro_*` |
| Git | The empty state already explains what Git *is* before what to do; INSTALL GIT is gated on Linux tools being ready. | `strings.xml` `git_install_explainer`, `git_not_a_repo_message` (Phase 73.2/73.3) |
| AI | Floating bubble (draggable, edge-snapping), HALF/FULL chat sheet, first-use agreement card, "Simple by default", explain-selection and explain-last-error chips. | `ui/ai/AiFloatingButton.kt`, `AiHome.kt`, `AiChatSheet.kt`, `AiSheetPolicy.kt` |
| Settings | Searchable, foldable catalogue (64 rows at Phase 64), with Editor / Compiler / Appearance / Storage / About groups; Developer Options behind a tap-count. | `SettingsScreen.kt`, `ui/settings/SettingsSearch.kt` |

**Read this table as a designer:** the *loop* (open → type → RUN → see output) is intact and thick with honest states. The **ramps** into that loop — "which file?", "what is this word?", "what do I make?", "what did this error mean?" — are thin.

---

## 2. What is already right (do not "fix" these)

These are the assets a beginner-friendly pass should build *on*, and the tests that will fail if it breaks them.

| Asset | Why it matters for beginners | Evidence |
|---|---|---|
| **One token scale** for space / radius / elevation / icon, with a 48 dp floor | Consistent rhythm is what makes a phone UI scannable; the floor is the difference between "small" and "unusable". | `CodecTokens.kt`, `TokenAdoptionTest`, `TouchTargetTest` |
| **A real type scale** (6 anchors, 15 filled M3 slots) + JetBrains Mono for code surfaces | Text hierarchy is how a beginner finds the one important thing; a code face for paths/output is what makes them look like "code" rather than prose. | `CodecType.kt`, `CodecTypeTest`, `TypeAdoptionTest` |
| **Accent role computation with measured contrast** ([AccentPalette.rolesFor]) and a pinned contrast table | A learner's first impression is readability; the repo already measures rather than eyeballs. | `Theme.kt:17-40`, `CodecPalette.kt:1-20`, `AppContrastTest`, `ChromeContrastTest`, `OutputPanelContrastTest`, `GhostContrastTest` |
| **Motion policy with a reduced-motion gate** | Motion sensitivity is real; and the intro timer is deliberately *not* motion, so reading time survives the accessibility switch. | `CodecMotion.kt`, `CodecMotionTest`, `MotionPolicyTest` |
| **Honest output states** (Building / Installing / Running / Waiting for input / Done / Failed / Stopped / Serving) with error counts | The single most beginner-hostile thing an IDE can do is *lie* about what is happening; CodeC does not. | `OutputPanelStatus.kt`, `strings.xml` `output_state_*` |
| **Plain-words copy where it was already rewritten** | `no_projects_hint`, `notice_userland_not_ready`, `output_no_main_hint`, `output_timed_out_input_hint`, `git_install_explainer`, `install_prompt_body` — this is the house voice; the problem is coverage, not quality. | `strings.xml` |
| **Tappable diagnostics** (`OutputDiagnosticTarget`) and one-tap remedies (`fix_add_semicolon`) | Turning an error line into a *place in the file* is the single highest-value debugging affordance on a phone. | `ui/editor/OutputDiagnosticTarget.kt`, `strings.xml` |
| **Empty state that teaches** (three starter tiles, editor scratch chip, hub resume card) | NN/g and COGA both name empty states as teaching moments; CodeC already treats them that way in two places. | `FileManagerScreen.kt:2280-2310`, `EditorEmptyState.kt`, `ResumeOffer` |
| **A test culture for UI invariants** (349 test files; token/contrast/touch/nav/back-router pins) | Any recommendation here can be *pinned*, not hoped for. That is unusual and it is the reason a beginner-friendliness pass can be safe. | `app/src/test/java/com/codeci/ide/` |

---

## 3. The friction inventory — "every inch"

Severity: **A** = a beginner stops or mis-learns; **B** = a beginner hesitates, backtracks, or asks "why"; **C** = polish that a careful reader notices.

### 3.1 Discovery — features with no door

| Item | Detail | Evidence | Sev |
|---|---|---|---|
| Templates screen unreachable | `Screen.Templates` has a route, a composable, strings, 5 templates with `difficulty` and `concepts` — and **no navigator** anywhere in `app/src`. A grep for `Templates` outside its own files returns only the import and the `composable(...)` block. | `Screen.kt:87`, `MainActivity.kt:91, 1456`, `Template.kt` | **A** |

> **Correction (2026-10-08, verified by list of `TemplateProvider.templates`):** the library ships **5** templates — Hello World (L1), Calculator (L1), Array Sorting (L2), File I/O (L2), Linked List (L3) — not 6 as first written. Everything else in this row stands.
| Course unreachable | `website/learn/` is a 19-chapter course under CC BY 4.0 with MIT-licensed examples, published to GitHub Pages. The only user-visible URLs in the app are GitHub, the AI key/terms pages, and the token page. | `website/learn/`, `AiCopy.kt:53-57`, `GitErrors.kt:51` | **A** |
| Docs pointers are path strings | "docs/guides/DATA_AND_PRIVACY.md in the repository — every permission…" is *displayed text* with no action; likewise `TROUBLESHOOTING.md` is quoted by `CompilerRemediation` but not reachable from the app. | `SettingsScreen.kt:1179`, `CompilerRemediation.kt:22-25` | **B** |
| "Explain with AI" is conditional and quiet | The AI affordance that turns a failure into understanding appears on the Output header **only after a failed run**; the selection path needs a selection and a known gesture. | `EditorScreen.kt:1135` (Phase 77.3 comment), `AiChatSheet.kt` | **B** |
| Learning state is buried in About | "Your CodeC progress" and the streak line live inside Settings → About; the hub's `StreakLine.forHub` exists. | `SettingsScreen.kt:1062-1076`, `StreakLine.kt:29` | **C** |
| Templates' "Save to project…" writes into a project literally named `default` | If the Templates screen were reachable today, `onUseTemplate` would create-or-use a project called **"default"** and drop the file in it — a beginner-hostile surprise ("why is my file in *default*?"). Fix before exposing the door. | `MainActivity.kt:1458-1485` | **A** (latent) |

### 3.2 Vocabulary and mental model

| Item | Detail | Evidence | Sev |
|---|---|---|---|
| "Modules" vs "Packages" | Tab/title says *Packages*; the screen header says **"Module Store"**; the string family is `modules_*`. | `Screen.kt:95`, `strings.xml:283` | **B** |
| "Source Control" / "Repository" / "Git" | Three names for one thing across the panel slot, the dialog and the strings. | `Screen.kt:36-37`, `strings.xml` `source_control_title`, `git_*` | **B** |
| "Files" / "Projects" / "Project files" | The route is `file_manager`, the title is *Projects*, the ☰ in the editor is described as *Project files*, and an old string says *Files*. | `Screen.kt:83-88`, `EditorScreen.kt:1954`, `strings.xml:196` | **B** |
| "Userland" / "Linux tools" | Both are used for the same download, sometimes in the same flow. | `strings.xml` `terminal_intro_*`, `notice_userland_not_ready`, `terminal_userland_confirm_*` | **A** |
| Compiler vocabulary on first run | "TCC (Tiny C Compiler)", "Clang / LLVM", "GNU Binutils", "C standard", "warning level", "optimization level" are all first-contact words for someone who has never compiled. | `ModuleCatalog.kt:53-104`, `strings.xml` `compiler_settings`, `c_standard`, `optimization_level` | **B** |
| Git vocabulary | "detached HEAD" (even translated as a lowercase label), "working tree clean", "PUSH TO main", "remote". The *explanations* are good; the *labels* are not. | `strings.xml` `git_detached_head`, `git_working_tree_clean`, `git_push_to` | **B** |
| Casing and verb forms drift | `RUN` / "Run" / "Run in Terminal"; `INSTALL` / "Install"; `SETUP STORAGE` / "Set up"; `CLEAR` / "Clear". | `strings.xml` | **C** |
| English-only | `values/` only (no locale folders); the owner is in India and the course is English. Adding locales is a product decision, not a bug — but it caps the beginner audience. | `app/src/main/res/values/` | **C** |

### 3.3 First-run and orientation

| Item | Detail | Evidence | Sev |
|---|---|---|---|
| ~50 s of reading before any code | 2 s logo + 5 × 10 s stories, all auto-advancing, then a required acknowledgement. Skip exists but is not the primary affordance, and the first *doable* action appears only after the agreement. | `FirstRunIntroScreen.kt:110, 180-195, 251-262, 436` | **B** |
| Nothing is personalised | No "have you coded before?" question; the same 5 stories for a 12-year-old and a senior dev. The starters that *do* branch by language live one screen later, in the hub. | `FirstRunIntroScreen.kt`, `WelcomeStarters.kt` | **B** |
| Story 4 promises Arcade; the user cannot act on it | "Tap RUN to open your game arena" is text in a timed story, not a live control. | `FirstRunIntroScreen.kt` story 4 | **C** |
| Privacy summary is required reading *before* the product is understood | Required is defensible (honesty); ordering it *last* is right; making it a checkbox is the cost. | `FirstRunIntroScreen.kt:1054+` | **C** |

### 3.4 The editor (the most-used surface)

| Item | Detail | Evidence | Sev |
|---|---|---|---|
| Breadcrumb tap = rename | The file name at the top is the *rename* door. For a beginner, tapping a title usually means "go up" or "switch file"; a rename dialog is a surprise with a destructive-feeling field. | `EditorScreen.kt:1985-1993` | **B** |
| Single-file peek vs project open | Hub tap = peek (`single=1`), ⋮ → *Open in editor* = project. In a peek the drawer has no tree and no Git ("The peek's drawer has no tree and no git") — a beginner sees a toolbox that vanished. | `MainActivity.kt:1373-1385`, `EditorScreen.kt:1047, 1806` | **A** |
| Tab accepts a suggestion, but nothing says so | Ghost text and the `Tab ▸` pill are beautiful and silent; README states the law, the UI does not. | `EditorScreen.kt:2625`, README §"everyday details" | **B** |
| Drag-and-drop keys, key-drag reorder | Keyboard-strip customisation is drag-only. | `PHASE_69_3_STRIP_AND_KEY_DRAG.md` | **C** |
| Status bar speaks expert | `Ln 12, Col 3 · Spaces: 4 · HTML · LF · UTF-8` — three of those five facts mean nothing to a beginner in week one, and the bar hides entirely when the keyboard is up. | `strings.xml` `status_*`, `EditorScreen.kt:2731-2759` | **C** |
| Hidden long-press menus | Drawer rows, tab rows, file rows: the ⋮/long-press duality is not signposted. | `EditorScreen.kt:2092-2290`, `FileManagerScreen.kt` | **B** |
| The ☰ label is a noun phrase | TalkBack reads "Project files" for a button that opens a *drawer* containing projects, the file tree, a navigation card and Settings. | `EditorScreen.kt:1954`, `R.string.project_files` | **C** |

### 3.5 Running, output and errors

| Item | Detail | Evidence | Sev |
|---|---|---|---|
| Raw compiler sentences are the payload | Errors are counted, colour-coded and jumpable — but the sentence to act on is the compiler's. Only semicolon gets a friendly remedy chip today. | `CompilerDiagnostics.kt`, `strings.xml` `fix_add_semicolon` | **A** |
| Explain-with-AI appears after failure only | The moment a beginner most needs "explain this" is exactly the moment it exists — but it *appears* (it was never visible before), and an appearing control is discoverable only by luck. | `EditorScreen.kt:1135` | **B** |
| Wait-for-input is explained in the panel, not at the code | `output_timed_out_input_hint` says "tap the terminal icon to run it interactively" — good words, printed after the frustration. | `strings.xml` | **C** |
| No "what does this program do?" affordance | The Arcade sample is deliberately designed "play first, then explore one piece at a time" — but there is no in-editor way to ask about *this file* (outline, "what is this?"), only AI free-text. | `ui/ai/AiOutline.kt`, `GameArenaSample.kt` | **B** |
| Panel minimum is 160 dp but header rows are 48 dp | With state row + command row + stdin row, the *code* shown inside the panel can be ~2 lines on a small phone. Already reasoned in Phase 70.1; still a beginner-visible cliff. | `OutputPanelHeight.kt:18-27` | **C** |

### 3.6 Hub, Projects and files

| Item | Detail | Evidence | Sev |
|---|---|---|---|
| No "what next" after the first run | The hub lists projects; the starters appear only when the list is *empty*. After one project exists, a beginner has no path to "another small thing to try". | `FileManagerScreen.kt:2290-2310` | **B** |
| Project cards say *what*, not *where to start* | `hubKindLabel` gives C/Python/Web/Git; nothing marks the entry file or "this is runnable". | `FileManagerScreen.kt:1488-1491` | **C** |
| Import/export vocabulary | "Import ZIP", "Export all projects (backup ZIP)", "Share as ZIP", "Import projects backup (ZIP)" — four phrasings of two ideas. | `strings.xml` | **C** |
| Delete/destructive confirmations are good | They name the item and warn "cannot be undone" — keep. | `strings.xml` `delete_file_confirm` | ✅ |

### 3.7 Packages, Terminal, Preview, Git, AI, Settings

| Item | Detail | Evidence | Sev |
|---|---|---|---|
| The Packages list is a distro front page | `nano`, `vim`, `tmux`, `ripgrep`, `bat`, `fd`, `htop`, `tree` sit in the same visual weight class as `Python 3`. A beginner needs *"add a language"* separated from *"Unix tools for people who know Unix"*. (PHONE_UX_ANALYSIS §13 said the same in 2026-09; it is still true.) | `ModuleCatalog.kt:110-240` | **A** |
| Install size/time is per card, honest, but the *why* is a sentence | `install_prompt_body` is good copy; the card list itself does not answer "do I need this?". | `strings.xml` | **C** |
| Terminal empty state is good | `terminal_intro_ready`: "type a command, or run a file with RUN" — keep. | `strings.xml` | ✅ |
| Preview has a hamburger menu with four items | LAN sharing, server options, stop servers, copy address — the beginner-visible part (does my page work?) is fine; the chrome is expert-shaped but hidden behind one control. | `strings.xml` `preview_menu_*` | **C** |
| Git's "not a repo yet" state explains itself | Best-in-repo example of the voice this audit asks for. | `strings.xml` `git_not_a_repo_message` | ✅ |
| AI first-run agreement + Simple default | Deliberate friction on a paid/keyed feature is right; the "Simple" default is right for beginners. | `AiChatSheet.kt`, Phase 91/96 records | ✅ |
| AI can propose file edits and apply them | Phase 79+ grants apply-on-tap. For a *beginner*, the risk named in the literature is accepting without reading. The gate (user taps Apply) is the mitigation; the design question is whether a first-run user should be *told* that the AI's code is a proposal to read, not an answer to paste. | `ui/ai/AiEditProposal.kt`, `AiEditApplier.kt`; §4.4 | **B** |
| Settings search exists; Settings prose is long | Search is a genuine beginner asset. But several rows carry a paragraph (`safe_mode_banner`, temporary files, storage) rather than a sentence. | `SettingsScreen.kt`, `strings.xml` | **C** |
| Developer Options "N steps away" hint | The old `dev_steps_away` string is friendly and hidden by design. Keep. | `strings.xml` | ✅ |

### 3.8 Accessibility, reachability, and ergonomics (cross-cutting)

| Item | Detail | Evidence | Sev |
|---|---|---|---|
| Touch targets | Enforced ≥48 dp in the six core files; **declared debt**: output-panel 36 dp header buttons (the test's own comment defers them to "51.2's editor-surface pass"), menu-row glyphs (20-22 dp, inside taller rows), Templates screen (not in the core list). | `TouchTargetTest.kt` header comment | **B** |
| Headings for TalkBack | Exactly one `heading()` in the app (`AiMarkdownView.kt:177`). Screen titles, section headers and Settings group labels are plain text — a TalkBack user cannot jump between sections. | grep `heading()` | **B** |
| Live regions | None. The Output panel's state word changes silently for a screen-reader user (Done → Failed is exactly the kind of change that deserves a polite live region). | grep `liveRegion` | **B** |
| State descriptions | Used in the drawer (Expanded/Collapsed) and the side panel rail — good pattern, not applied to toggles/switches elsewhere. | `EditorProjectDrawer.kt:223, 319`, `EditorSidePanel.kt:596` | **C** |
| Drag-only interactions | Panel resize, key reorder, AI bubble move, space-bar trackpad, drawer edge-swipe. WCAG 2.2 **2.5.7 Dragging Movements** asks for a single-pointer alternative for anything that can be dragged. | see §4.5 | **B** |
| Font scaling | Type is in `sp`, so system scaling works — and `EditorKeysRow`/`CodecKeyboard` already size keys *from* `fontScale` (good precedent). But fixed-height chrome (48 dp top bar, 36 dp panel headers, 24 dp status bar) will clip at 200 % non-linear scaling (Android 14+). No screenshot test at 1.3×/2.0× exists, even though Roborazzi already runs in verify mode in CI (Phase 52.4) — the cheapest possible guard is one more variant. | `EditorKeysRow.kt`, `CodecKeyboard.kt`; `app/build.gradle.kts:297-301` | **B** |
| Colour-only signals | Error/warning counts, git badges, RUN green, install state — check each for a redundant shape/word. The state word already exists for output (good precedent). | `OutputPanelStatus.kt` (✅), `strings.xml` | **C** |
| Editor font choice | Editor theme (4 themes) and font size exist; there is no legibility-first font option (see §6.5 for OFL candidates). | `EditorThemes.kt`, `FontSizeZoom.kt` | **C** |
| Colour-blind safety of syntax themes | The four editor themes are VS Code-derived; strings/comments/errors are hue-only distinctions in places. No CVD audit exists. | `EditorThemes.kt`, `TextMateGrammars` | **C** |
| Reachability | RUN ▶ top-right; the drawer ☰ also top-left; hub ⋮ top-right. The thumb zone holds the coding strip and the nav bar. | `EditorScreen.kt:1942-2010`; §4.3 | **B** |
| Landscape / tablet | No `WindowSizeClass` usage anywhere; the phone is the product by decision. On a tablet CodeC will stretch a phone layout. | grep `WindowSizeClass` → 0 hits | **C** |

---

## 4. What the evidence actually says (external research)

Confidence tags: **[strong]** = replicated or large-N; **[mixed]** = contradictory results; **[practitioner]** = widely agreed, not experimentally settled. These tags exist so the recommendations in §7 do not over-claim.

### 4.1 Learning science — how beginners actually learn to program

- **Worked examples and completion problems beat blank-page writing for novices** (cognitive load theory) **[strong]** — the worked-example effect originates in Sweller's studies and is the backbone of modern CS1 design; a review of CLT in computing education maps it to Parsons problems, subgoal labelling and syntax exercises. → *Implication:* a beginner should meet **working code and change it**, not an empty buffer. CodeC already ships the Arcade sample and three starters; the gap is making "modify this small thing" explicit. ([CLT in CER review](https://dl.acm.org/doi/full/10.1145/3483843))
- **Parsons problems** (arrange shuffled lines) reduce cognitive load, take less time than writing or fixing code, produce equal learning gains, and help low-self-efficacy learners most **[strong]**. Distractors add load — use them late, or not at all. → *Implication:* line-ordering is a *cheap, testable* beginner feature that fits a phone (drag/tap, no typing). ([Parsons & learning theories, 2025](https://dl.acm.org/doi/10.1145/3769994.3770032) · [Parsons as scaffolding, 2023](https://arxiv.org/html/2311.18115v1) · [Adaptive Parsons, 2022](https://dl.acm.org/doi/fullHtml/10.1145/3501385.3543977))
- **PRIMM (Predict–Run–Investigate–Modify–Make)** is the classroom-shaped version of the same finding: read and *predict* before writing; teachers report novices gaining confidence faster, and the model is explicitly built on read-before-write, Use-Modify-Create and language-rich discussion **[strong for adoption, practitioner for effect sizes]**. ([PRIMM teachers' study](https://primmportal.com/wp-content/uploads/2019/01/pre_print_teachers__experiences_of_using_primm_to_teach_programming_in_school.pdf))
- **Syntax highlighting** — Sarkar's eye-tracking study found faster comprehension for novices (median 8.4 s advantage, effect *stronger for novices*) **[mixed]**; a 390-student Java experiment found **no** improvement in comprehension correctness from highlighting alone, and warned it "squanders a feedback channel" **[strong, negative]**. → *Implication:* keep TextMate colour (it is a quality signal and CodeC fought for it in Phase 29), but **never** treat colour as the teaching channel. Use colour *plus* structure (labels, outlines, error counts). ([Sarkar 2015](https://ppig.org/files/2015-PPIG-26th-Sarkar1.pdf))

### 4.2 Error messages — the honest state of the evidence

- Message *enhancement* has a mixed record on objective outcomes: Becker et al. (2016) found reductions in repeated errors **[positive]**; other controlled studies found no significant effect **[negative]**; the 2026 "Beyond the Traceback" study (N=103) found LLM rewrites (Pragmatic/Contingent) rated *more readable and less cognitively demanding* — with **no statistically significant improvement in fix rate or time-to-fix** **[mixed, recent]**. ([Becker et al. via ERIC](https://eric.ed.gov/?id=EJ1117113) · [Beyond the Traceback, arXiv 2608.20896](https://arxiv.org/html/2608.20896v1))
- What *is* consistent across sources: **structure and placement matter more than length**; messages should name the problem plainly, point at the line, and offer **one next action**. Nielsen's heuristic 9 formalises it: plain language, no codes, precise problem, constructive solution; NN/g's guidelines add "put it next to the thing that caused it" and "never blame the user". ([NN/g error-message guidelines](https://www.nngroup.com/articles/error-message-guidelines/) · [Heuristic 9 card, NN/g PDF](https://media.nngroup.com/media/articles/attachments/Heuristic_9_A4-compressed.pdf))
- The strongest beginner-oriented *compiler* precedent is **DCC** (UNSW's Debugging C Compiler): it catches common novice C errors and explains them, including run-time value dumps. It is **GPL-3.0** — its *ideas* are usable, its code is not, in an app that ships as a closed-source APK. ([COMP1511UNSW/dcc](https://github.com/COMP1511UNSW/dcc) · [dcc --help paper](https://arxiv.org/pdf/2308.11873))
- **What to promise internally:** count comprehension and recovery (did the learner get to a compiling program, how many attempts, did they read the line), **not** "fewer bugs".

### 4.3 Reachability and one-handed use

- Hoober's 2013 grip study (and the replications that follow it) puts ~49 % of use in one hand, with the bottom-centre as the "sweet spot" and top corners as the hardest reach; NN/g's hidden-navigation study found burger menus discovered ~21 % of the time versus ~48 % for visible navigation **[strong for grip, practitioner for placement]**. ([Hoober, UXmatters](https://www.uxmatters.com/mt/archives/2013/09/how-do-users-hold-mobile-devices-part-1-a-closer-look.php) · [UX Movement summary](https://uxmovement.com/mobile/why-mobile-menus-belong-at-the-bottom-of-the-screen/))
- Android's own floor is **48 dp** targets and 7-10 mm physical size. ([Android accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults))
- → *Implication for CodeC:* RUN ▶ at the top-right is the reference-locked Spck position (Phase 54 locked the shots), so the recommendation is not "move RUN" — it is **make sure a bottom-half run affordance exists** (the strip already carries run keys via `RunKeySet`) and **make it legible**.

### 4.4 AI and novices — use it as a tutor, not an author

- Kazemitabaar et al. (CHI '23, 69 novices aged 10-17): access to an AI generator **increased completion rate 1.15× and score 1.8× without hurting retention**; the follow-up Koli Calling study then documented **over-reliance**: "AI Single Prompt" (paste task → submit output) predicted the worst independent performance, and students often accepted unverified code **[mixed — benefit with guardrails]**. A 2026 systematic review frames it as a **comprehension–completion trade-off**: "students complete more while understanding less, unless critical engagement with the output is required" **[strong]**; guardrail designs it cites (CodeHelp refusing full solutions, CodeAid's scaffolding templates) are the pattern to copy. ([CHI '23 PDF](https://www.terpconnect.umd.edu/~weintrop/papers/Kazemitabaar_et_al_CHI_2023.pdf) · [Koli Calling '23 PDF](https://austinhenley.com/pubs/Kazemitabaar2023Koli_LLMsCS1.pdf) · [2026 systematic review](https://www.sciencedirect.com/science/article/pii/S2666920X26001396))
- → *Implication:* CodeC's AI surface is already unusually well behaved (preview before send, no auto-apply, apply-on-tap, "Simple" default). The beginner-facing additions that the research supports are: **explain-first framing**, **ask me a question before the solution**, and **"what changed?" summaries after an applied edit** — not more autonomy.

### 4.5 Accessibility standards worth adopting verbatim

- **WCAG 2.2**, new criteria CodeC should treat as design requirements: **2.5.7 Dragging Movements** (single-pointer alternative for every drag) · **2.5.8 Target Size (Minimum)** 24×24 CSS px, AA — CodeC's 48 dp already exceeds it, but the *out-of-scope* glyphs (20-22 dp menu rows) are the ones to re-check · **3.2.6 Consistent Help** (help appears in a consistent relative position) · **3.3.7 Redundant Entry** (never ask again for something the user already gave — e.g. Git credentials, project names) · **2.4.11 Focus Not Obscured**. ([WCAG 2.2 new criteria summary](https://www.hemispheredm.com/wcag-2-2-explained))
- **COGA "Making Content Usable"** (W3C) is the plain-language bible for exactly this audience: use common words (~1,500 highest-frequency terms), avoid or explain jargon **in place** (bracket definition or pop-up), simple tense and active voice, no double negatives, do not rely on memory, and keep help visible rather than behind an icon. ([W3C COGA](https://www.w3.org/TR/coga-usable/))
- **Android specifics**: 48 dp targets; `sp` for all text (200 % non-linear scaling on Android 14+); `heading()` for section structure; `liveRegion` for status changes; an accessibility action for anything that is swipe/drag-only. ([Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) · [Accessibility testing codelab](https://developer.android.com/codelabs/basic-android-kotlin-compose-test-accessibility))
- **Colour-vision deficiency**: ~8 % of men / 0.5 % of women have some CVD; red/green coding collapses under deuteranopia. The established fixes are palettes built from Tol/Wong sets (blue/orange/yellow), and themes with explicit deuteranopia/tritanopia variants (e.g. Modus). ([Wong, *Nature Methods* 2011 — "Points of view: Color blindness"] · [Modus themes CVD variants](https://protesilaos.com/emacs/modus-themes))

### 4.6 Onboarding, progressive disclosure and gamification

- **Tutorial carousels complete below ~30 %** and users skip them; **contextual, in-place hints** beat front-loaded tours; **more than two disclosure levels** confuses; hiding something frequently needed is the classic failure mode of progressive disclosure **[practitioner, consistent]**; empty states and error states are named as teaching moments, and required guidance must not be hidden behind an icon. ([NN/g progressive disclosure](https://www.nngroup.com/articles/progressive-disclosure/) · [NN/g instructional overlays](https://www.nngroup.com/articles/instructional-overlays/))
- **Time-to-first-value** is the metric that predicts retention; onboarding should be under a minute and the "aha" moment should be reached with minimal steps **[practitioner]**.
- **Gamification**: streaks and loss-aversion mechanics do move retention (Duolingo's own numbers), but the same literature documents "gamification misuse" — chasing points over learning, and the overjustification effect on intrinsic motivation **[mixed]**. → *Implication:* CodeC's existing counters (files created, runs, streak, shown in About) are fine as **honest presence indicators**; do not add XP, leaderboards, or rewards for opening the app. ([Gamification misuse, Mogavi et al. 2022 summary](https://www.taalhammer.com/gamification-in-language-learning-apps/))

---

## 5. What the other apps do (and which half to copy)

Closed-source apps are judged from their **visible behaviour, store listings and reviews**; open-source ones from their repositories.

| App | What it does for beginners | Copy this | Don't copy this |
|---|---|---|---|
| **Pydroid 3** | One language, bundled runtime, empty editor with a template stub, yellow ▶, output directly under the code, a pip GUI, an examples folder. Reviews consistently name "works offline, no setup" as the reason beginners stay. | The *first-30-seconds* shape: template in the buffer, one obvious run control, output in the same view. | Single-language identity — CodeC is deliberately multi-language. |
| **Coding C / C4droid** | TCC in the APK, symbol row, compile-and-run, errors reported with line and column. Store copy: *"does not need to download additional plugins"*. | "Nothing to download" as a *stated promise*, not just a fact (`welcome_offline_c` already does this once). | Sparse chrome and no project model. |
| **Spck Editor** | The chrome CodeC cloned (Phases 15-17, 54): flat editor bar, file drawer, side panel, bottom strip, preview loop. | Tab/label clarity and the preview-in-one-tap loop. | It previews; it does not compile. Also its depth is in Git/Node flows that assume competence. |
| **Acode** | MIT, plugin store; learnability arrives *through the store* (install "Acode LSP", theme packs). Its docs pattern is "tap to install the thing you need, when you need it". | Card-as-capability framing for Packages; per-capability install moment. | Plugin sprawl and a store as the *first* experience. |
| **Squircle CE** | Sora-editor based, Apache-2.0, no run step at all — pure editing comfort: themes, sticky scroll, pair skipping. | Editor-feel details (sticky scroll, pair skipping) as comfort for beginners too. | No run loop. |
| **Termux** | A real Linux environment; power, zero beginners' rails. Even its fans describe the learning cliff. | The honesty about what is installed. | The vocabulary: "userland", `pkg`, sessions-as-concepts. CodeC should keep the power *behind* the GUI. |
| **AndroidIDE** | Gradle on device; archived; heavy. | — | Desktop model on a phone. |
| **Replit mobile / Mimo / Sololearn** | Replit: mobile-first editor with a *custom keyboard* and instant run; Mimo/Sololearn: bite-sized lessons plus an on-device IDE, and they sell "learn by building" with very short loops; Mimo's own store copy leads with *"beginner-friendly interactive lessons"*. | The short-loop lesson shape (predict/run/change), the custom key row, and **progress you can feel within a session**. | Streak-pressure, ads, and cloud dependence. |
| **Microsoft CodeTour** (MIT) | The "guided tour of a codebase" pattern — steps anchored to file+line with Markdown narration, recorded in-repo as `.tour` files. | The *idea* of a tour **as content in a file**, not as an overlay the app forces: a beginner can open "Tour: how Snake works" like a document. This is the sanctioned shape if anything tour-like is ever wanted. | Auto-start overlays (Phase 64 already banned them). |
| **tldr-pages** | The community "simplified man page" project: 3-5 examples per command, exactly what a beginner needs at a terminal prompt. Pages: CC BY 4.0; client scripts: MIT. | A per-command cheat sheet inside CodeC's terminal surfaces. | Shipping the whole 5 000-page corpus in the APK (size) — pick 30-60 commands. |
| **DCC** (ideas only, GPL-3) | Novice-specific C error explanations + runtime value dumps. | The *taxonomy* of first-week C errors to explain (missing `;`, missing `#include <stdio.h>`, `main` signature, `%d` with a float, uninitialized read, array off-by-one). | The code (GPL-3 is incompatible with CodeC's shipped APK licensing). |

---

## 6. The open-source shelf (with licences, so nothing is a surprise)

**Licence discipline for this repo.** CodeC ships a closed-source APK (`docs/guides/RELEASE_NOTES.md`, `LICENSE` terms) with vendored third-party material under `assets/licenses/` (MIT, Apache-2.0, OFL, LGPL-binary, CC0). Anything **GPL/AGPL** cannot be linked or bundled; anything **CC BY-SA** can be *content* only if the derivative content keeps the same licence (fine in a separate content folder with attribution, but it cannot be relicensed into CodeC's own terms); **CC BY 4.0** (CodeC's own course) and **OFL** are already precedents in this repo.

### 6.1 Content a beginner can read — free to reuse

| Asset | Licence | Why it helps | How CodeC could use it |
|---|---|---|---|
| **CodeC's own course** (`website/learn/`, 19 chapters, CC BY 4.0 prose + MIT examples) | own + CC BY 4.0 | Already written in the house voice, already covers terminal, editor, C basics, Git, AI with approvals — and already licence-clean for reuse *by CodeC itself*. | The single biggest win: expose it from the app, and later vendor 3-5 chapters into `assets/` for offline reading. |
| **tldr-pages** | pages CC BY 4.0, scripts MIT | Beginner-shaped command help (examples, not flags) | A "Command help" sheet in the Terminal/Packages surfaces, 30-60 curated commands, attributed. |
| **freeCodeCamp curriculum** | software BSD-3, `/curriculum` CC BY-SA 4.0 | Huge, tested exercise bank | Reference only unless CodeC accepts share-alike content in its own folder; extracting exercises = derivative content (share-alike). |
| **Exercism** tracks | platform AGPL-3.0; per-exercise licences vary — **verify per track** | High-quality, language-specific practice with tests | Design inspiration; do not copy exercise text without checking each licence. |
| **TheAlgorithms/\*** | **differs per repository** (C repo has been GPL-3.0; C++/Python published MIT) | Ready-made example programs with explanations | Verify per repo before reuse; treat as reference. |
| **Selected algorithm/example sets** CodeC writes itself | MIT (its own) | Full control, no licence debt | Preferable to importing: the five templates already exist and are CodeC-authored. |

### 6.2 Error-message knowledge (ideas, not code)

| Asset | Licence | Use |
|---|---|---|
| **DCC** (COMP1511UNSW) | GPL-3.0 | Copy the *taxonomy* of novice C errors and the "print the value at the point of failure" idea into a CodeC-written explainer table. |
| **`dcc --help` / "Beyond the Traceback"** | papers | Evidence for *how* to phrase explanations, and the warning not to over-promise fix-rate gains. |
| **clang / gcc diagnostics + fix-its** | compiler licences (already in use via packages) | `<compiler> -fcaret-diagnostics`, `-fdiagnostics-parseable-fixits` — machine-readable fix-its already exist for C; a small parser could surface "did you mean `printf`?" as CodeC UI. |
| **Python's `friendly-traceback`** (MIT) | MIT | Not needed for the C path; the *pattern* (explain + suggest) is what transfers. |

### 6.3 Learning-interaction patterns worth re-implementing (all small, all host-testable)

| Pattern | Source | Cost in CodeC |
|---|---|---|
| **Predict-then-run** | PRIMM | A tiny "What will this print?" card that appears once per session above the Output strip for the Arcade entry file — pure policy + one composable. |
| **Line-ordering (Parsons)** | Ericson et al. | A card in the side panel/templates with the shuffled lines of a template; tap-to-place, no typing. Needs a content file, not an engine. |
| **Tour-as-file** | CodeTour (MIT) | If the owner ever wants guided reading again, the sanctioned shape: a `.tour`-style JSON in the project + a "Play tour" row, user-initiated, never automatic. |
| **Subgoal labels** | CLT research | Comments in the starter templates that name *what this block does* (the templates already carry a `concepts` list — surface it). |
| **Guardrailed AI help** | CodeHelp / CodeAid papers | Prompt presets inside the existing AI sheet: "Explain this line", "Give me a hint, not the answer", "What changed and why". |

### 6.4 Accessibility tooling to adopt (all free)

| Tool | Licence | Use |
|---|---|---|
| **Accessibility Scanner** (Google) | free app | Manual device pass over the six core screens. |
| **Espresso Accessibility Test Framework** | Apache-2.0 | Could join CI as a Robolectric/ATF check for content descriptions and targets; CodeC already runs Roborazzi screenshot tests. |
| **Roborazzi** (already a dependency) | Apache-2.0 | Add font-scale screenshot variants (1.0×, 1.3×, 2.0×) for the six core screens — this is the cheapest possible guard against clipping. |
| **CVD simulators** (Material/Chrome DevTools-style filters) | free | Screenshot the four editor themes under deuteranopia/protanopia. |

### 6.5 Type and icons

| Asset | Licence | Why |
|---|---|---|
| **Atkinson Hyperlegible** | OFL-1.1 | Character-disambiguation first (0/O, I/l/1) — the right option for a "readable UI" font toggle and for labels/numbers in the hub and status bar. OFL is already a precedent in this repo (JetBrains Mono). |
| **Lexend** | OFL-1.1 | Reading-speed optimised; a good candidate for long-form in-app lesson reading. |
| **Material Symbols** | Apache-2.0 | Consistent icon language already adjacent to Material Icons used today. |
| **Lucide / Tabler** | ISC / MIT | Thin, friendly line icons if a "beginner mode" ever needs softer iconography; **do not** mix icon families inside one surface. |

---

## 7. Recommendations, ranked (with the test that would hold each one)

Every item is expressible inside the existing structure: no new tabs, no overlays, no tours, no locks. Effort: S ≈ a day-ish of chat-scoped work, M ≈ a part, L ≈ a phase with content work.

### P0 — Do these first: doors, words, and one honest explanation

**P0-1 · Open the Templates door (and fix what it does first). — S/M**
- *Problem:* a beginner-shaped screen exists and is unreachable (§3.1).
- *Change:* add exactly **one** entry point that costs no new chrome: a **"Learn from a template"** row in the Projects hub ⋮ menu **and** a fourth tile row under the three starters ("More starters…"), plus a row in the `+` sheet. Keep it a page over the editor (Phase 66.1 law: pages, not tabs).
- *Fix first:* `onUseTemplate` currently drops the file into a project named `default` (`MainActivity.kt:1458-1485`). Before exposing the door, make the flow ask **"Save to which project?"** (existing project picker) with "New project: *Template name*" as the default.
- *Pin:* a parity test in the style of `UnrestrictedUiWiringTest` (route reachable from ≥1 real caller) + a regression test that no template write targets an implicit `default` project.
- *Copy to use:* "Templates" for the screen; the concept list is already written per template.

**P0-2 · Give the course a door. — S (link) / L (offline subset)**
- *Problem:* the best beginner artifact in the project is on a website the app never mentions (§3.1).
- *Change (S):* one row in **Settings → About** ("Learn CodeC — 19 short chapters") and one line in the **hub empty state** ("Prefer to read first? Open the beginner course."). Opening it: reuse the existing `Preview` route if it can host the GitHub Pages URL, else an `ACTION_VIEW` intent — **this is an owner decision** because it is the first outbound link from the app (the privacy story must be updated in the same breath: "opening the course leaves the app; nothing about you is sent").
- *Change (L, later):* vendor chapters 01, 02, 06, 07 into `assets/learn/` for **offline** reading, rendered by the existing Markdown stack (`AiMarkdown`), with the CC BY 4.0 attribution block the licence already requires (`website/learn/licenses/SCOPE.txt` contains ready-made attribution text).
- *Pin:* a string test that every shipped URL has a matching `DATA_AND_PRIVACY.md` sentence.

**P0-3 · One name per thing (copy pass). — M**
- *Change:* a mechanical pass over `strings.xml` with the glossary in Appendix C: Packages (not Modules/Module Store), Git (not Source Control/Repository), Projects (not Files/File Manager), Linux tools (not userland, everywhere), one casing rule for actions (Title case labels, CAPS only for the primary contained button), and one verb per action.
- *Evidence for it:* COGA §4.4 (consistent terminology, common words) + NN/g error/consistency guidance.
- *Pin:* `StringsConsistencyTest` — a host test asserting banned terms (`userland`, `Module Store`, `File Manager`) never reappear in user-facing strings; extend the existing string audits rather than invent a new mechanism.

**P0-4 · "What is this?" — explain in place, never in an overlay. — M**
- *Change:* a single reusable **ExplanationRow** pattern (a small ⓘ/`?` inline control with a one-sentence plain-language body + "More in the course →" where P0-2 exists), used at the six places jargon first appears — Packages header, Git empty state, compiler settings, terminal intro, AI key setup, Storage. Rules: never a pop-up dialog over content; the body opens inline (expanding row) so the user never loses their place.
- *Evidence:* COGA "provide a pop-up definition **or** a bracket definition", "keep required help visible"; NN/g "contextual hints beat upfront tours"; Phase 64's ban is respected (this is in-surface help, not a tour).
- *Pin:* `ExplanationCopyTest` — every explanation ≤ 25 words, present tense, contains the word it explains.

**P0-5 · Make the error line teachable in one tap, always. — M**
- *Change:* (a) keep the raw compiler output verbatim; (b) add a **pure** `BeginnerDiagnostics` table mapping the ~20 first-week C errors (and the common Python/JS ones CodeC can see) to `{plain sentence, one action, course chapter}`; (c) render it as one extra line *under* the error count in the Output header — never inside the log; (d) make "Explain with AI" **present whenever diagnostics exist** (not only after a failure), keeping the existing pre-send preview.
- *Honest promise:* the research says this improves *comprehension and confidence*, not necessarily fix-rate (§4.2). Write the phase doc that way.
- *Pin:* `BeginnerDiagnosticsTest` (pure table: every entry has one sentence, one action, no jargon terms) + a wiring test that the header line disappears when diagnostics are empty.

### P1 — Structure and ergonomics

**P1-1 · Single-file peek should announce itself. — S**
- *Change:* in `single=1` mode, the editor's existing empty/scratch chip area says one line: **"Single-file view — open the project to see every file."** with the existing "Browse projects" action. No new mode switch; the mode already exists (`EditorEmptyState`), it just has nothing to say in peek mode.
- *Pin:* extend `EditorEmptyStateTest` with the peek facts.

**P1-2 · Say what the gesture does, where the gesture lives. — S/M**
- *Change:* text-only affordance labels, not overlays: give the tab row's ⋮ an existing-string label; ensure every long-press action has a visible twin (the tab/file ⋮ menus already exist for some rows — extend, don't invent); give the drawer's edge-swipe a visible ☰ (already there) — and add `stateDescription` to the panel resize handle and key strip so TalkBack can report state.
- *Evidence:* WCAG 2.5.7 (dragging alternatives), Android 48 dp + state announcements.

**P1-3 · Thumb-zone run, without touching the reference-locked ▶. — S/M**
- *Change:* the coding strip already includes `RunKeySet.KEYS`; make the run key in the strip the **first** key with the same green and a text label ("RUN") at ≥48 dp, and ensure it is present in the peek mode too. Owner taste call: the Phase 54 reference lock governs the *top* bar; the strip is CodeC's own.
- *Evidence:* Hoober/NN-g reachability.

**P1-4 · Font scale and screen-reader hardening. — M**
- *Change:* (a) `heading()` on screen titles/section headers; (b) `liveRegion = Polite` on the Output state word and install progress; (c) audit fixed heights (`48 dp` top bar, `36 dp` panel headers, status bar) at 200 % font scale — allow growth (`heightIn(min=)`) where clipping is found; (d) Roborazzi variants at 1.0/1.3/2.0×.
- *Evidence:* WCAG/Android/COGA; consistent with the repo's test-pinned culture.

**P1-5 · Packages, split by intent. — M**
- *Change:* two sections in the existing list: **"Add a language or compiler"** (Languages + Compilers, with the size/time promise and the C-offline line) and **"Unix tools (for the terminal)"**, collapsed by default with one sentence. Reuse `PackageCategory` (no data change), only ordering/grouping and copy.
- *Evidence:* PHONE_UX_ANALYSIS §13; NN/g "hide rarely-needed, never hide frequently-needed".

**P1-6 · Beginner-safe AI framing. — S**
- *Change:* three preset chips in the existing AI sheet (`Explain this line` · `Give me a hint, not the answer` · `What changed?`), and one sentence in the first-run agreement: *"The AI suggests; you decide. Nothing changes your files until you tap Apply."* No new capability; this is copy + three buttons mapping to existing prompt paths.
- *Evidence:* §4.4; keeps Phase 79-96's approval model intact.

### P2 — The learning ramp (owner decision required)

**P2-1 · One practice loop, from content CodeC already has. — L**
- *Change:* "Try it" cards attached to the Arcade sample and the five templates: **Predict** (a one-line question about output), **Run** (existing), **Change** (one named edit, e.g. "make the snake faster"), **Share** (existing ZIP/share). Content-only at first: a JSON file next to the template, one composable to render it. No XP, no scores, no streak pressure.
- *Evidence:* §4.1 (worked examples, PRIMM, Parsons), §4.6 (gamification caution).

**P2-2 · Make progress honest and quiet. — S**
- *Change:* move the existing counters (`StatsManager`, `StreakLine`) into the hub as one line ("12 files · 40 runs · 3 days"), text-only, no badges; keep About as the detail.
- *Evidence:* §4.6 — presence, not pressure.

**P2-3 · Tablet/foldable courtesy. — M/L**
- *Change:* `WindowSizeClass` + `NavigationSuiteScaffold` (bottom bar → rail) and two-pane hub/editor on expanded widths. Explicitly a product decision; the phone remains the design target.

**P2-4 · Offline course subset. — L**
- *Change:* the P0-2 (L) item: 3-5 chapters in `assets/`, rendered by the existing Markdown view, with the CC BY 4.0 notice. This is what makes "learn" usable without network — which matters most for the audience that has no laptop.

### Explicitly rejected (with the reason)

| Idea | Why not |
|---|---|
| Reintroducing coach marks / spotlight tours | Phase 64 owner instruction; NN/g also finds upfront tours skipped and coach marks misread as controls. |
| A new "Learn" bottom tab | Phase 56 law: the bar has four destinations; Projects left it on purpose. Doors live in pages and the side panel. |
| An in-app marketplace of lessons (download-more-content) | Contradicts the offline-first, privacy-forward story and adds a network surface. |
| Rewriting compiler output to hide the real message | Becker/Beyond-the-Traceback: enhancement ≠ replacement; the raw text is the ground truth an IDE must preserve. |
| Gamified XP/leaderboards | §4.6 overjustification + misuse evidence; also off-brand for the app's tone. |
| Adding a second visual language for "beginner mode" | `TokenAdoptionTest` exists to prevent exactly this. Beginner-friendliness is *words and order*, not a skin. |

---

## 8. How to know it worked (measurement, phone-local)

The app already logs `firstFrameMs` per launch (`MainActivity.kt:436-441`). The beginner metrics below can be *counters on the device*, printed in Logs — no analytics, consistent with the privacy story.

| Metric | Definition | Target direction |
|---|---|---|
| **Time to first green run** | first launch → first run with exit 0 | shorten |
| **Onboarding drop-off** | intro page reached at exit (1-5) | fewer exits before page 5 |
| **Taps to code** | first frame → first keystroke | shorten |
| **Peek confusion** | sessions that enter `single=1`, then open the drawer, then leave the editor | shorten |
| **Error recovery** | failed run → next run with exit 0, within the same session | increase |
| **Explain usage** | diagnostics shown vs. explain tapped | increase (means the door is visible) |
| **A11y pass** | Accessibility Scanner issues per core screen at 1.0× and 2.0× font scale | reach zero for content/labels |

**A 12-step beginner script for the next device round** (owner-run, ~10 minutes, no new tooling):
1. Fresh install, stopwatch running: install → first keystroke. 2. Did you know you had skipped the introduction? 3. Tap RUN on the Arcade sample. 4. Change one line; run again. 5. Find the file list; open a second file. 6. Open the same file from the Projects hub — *what changed?* 7. Break the code deliberately (`printf` without `;`); what does the app say? Fix it. 8. Find where you would add Python. 9. Find out what a "package" is without leaving the screen. 10. Find something to learn next. 11. Open the Git panel; explain what it does in one sentence. 12. Turn the system font up; is anything clipped?

---

## 9. Proposed phase sequence (research proposal — nothing starts without the owner's word)

Following the repo's one-part-per-chat grammar; next free number is **97**.

| Phase | Title | Scope | Depends on |
|---|---|---|---|
| **97** | Beginners A — **Doors** | Templates entry (+ the `default`-project fix), course link, docs pointers become buttons | owner's call on the outbound-link policy |
| **98** | Beginners B — **Words** | `strings.xml` consistency pass, glossary in place (ExplanationRow), casing rules, `StringsConsistencyTest` | — |
| **99** | Beginners C — **Errors that teach** | `BeginnerDiagnostics` pure table, header line, always-available "Explain with AI" | — |
| **100** | Beginners D — **Accessible by default** | headings/live regions/state descriptions, drag alternatives, font-scale audit + Roborazzi scale variants | — |
| **101** | Beginners E — **One practice loop** | Predict/Change cards for Arcade + templates; no scores | content approval, owner decision |
| **102** | Beginners F — **Offline course subset** | 3-5 vendored chapters + attribution | 97 |

Each phase keeps the house shape: pure policy + host tests + a device-round script + honest "what still bites" in RELEASE_NOTES.

---

## 10. Open questions for the owner

1. **Outbound links:** may the app open the website course (and the docs on GitHub) in a browser/Custom Tab? If yes, does `DATA_AND_PRIVACY.md` need a sentence first? If no, do we vendor offline chapters instead (Phase 102 earlier)?
2. **Templates:** where should its door live — hub ⋮ only, the `+` sheet, both — and is "Templates" the right name, or "Starters" (matching `WelcomeStarters`)?
3. **Naming law:** do you accept the glossary in Appendix C as the house vocabulary (Packages, Git, Projects, Linux tools), even where it changes strings you have seen for months?
4. **Errors:** is a one-line plain-language summary *above* the raw compiler log acceptable ("compiler output stays untouched below it"), or do you want the raw log first and the summary as a tap?
5. **AI framing:** may the AI sheet gain three beginner presets (`Explain this line` · `Hint, not the answer` · `What changed?`), given "Simple" stays the default?
6. **Practice loop:** is a no-scores "Predict then Run" card on the Arcade sample welcome, or does anything lesson-shaped feel like the guide system you removed in Phase 64?
7. **Font scale:** should CodeC ship an OFL legibility font option (Atkinson Hyperlegible) for the editor/UI, or keep the current fonts and only fix clipping?
8. **Tablets:** is large-screen adaptation (rail + two-pane) in scope this year, or explicitly out?

---

## Appendix A — Evidence table (this revision)

| Claim | Evidence (path / symbol) |
|---|---|
| Templates screen is composed but never navigated to | `ui/navigation/Screen.kt:87`; `MainActivity.kt:91, 1456`; grep across `app/src/main` returns no other reference |
| Template "Use Template" writes to an implicit `default` project | `MainActivity.kt:1458-1485` |
| Course exists, licensed, web-only | `website/learn/index.html`, `website/learn/chapters/*` (19), `website/learn/licenses/SCOPE.txt` |
| App's only user-visible URLs | `ui/ai/AiCopy.kt:53-57`, `ui/projects/GitErrors.kt:51`, `GitHubPublish.kt:53`, Settings About (GitHub) |
| Intro: 2 s logo, 5 timed stories, skip, agreement | `ui/screens/FirstRunIntroScreen.kt:110, 180-195, 251-262, 384-400, 436` |
| First destination is the Arcade sample | `MainActivity.kt:933-946`; `ui/projects/GameArenaSample.kt` |
| Editor top bar composition & 48 dp | `ui/screens/EditorScreen.kt:1942-2010` |
| Breadcrumb tap opens rename | `ui/screens/EditorScreen.kt:1985-1993` |
| Output panel: 40 % default, 55 %/38 % caps, 160 dp floor | `ui/editor/OutputPanelHeight.kt:18-40` |
| RUN expands the panel | `ui/viewmodels/EditorViewModel.kt:320, 3920` |
| Output header truthfulness & error counts | `ui/editor/OutputPanelStatus.kt` |
| Hub empty state = starters; peek vs project open | `ui/screens/FileManagerScreen.kt:2280-2310`; `MainActivity.kt:1373-1385`; `EditorScreen.kt:1047, 1806` |
| Packages categories & jargon neighbours | `ui/modules/ModuleCatalog.kt:35-240`; `strings.xml:283` |
| Git plain-words copy already exists | `strings.xml` `git_install_explainer`, `git_not_a_repo_message` |
| Plain-language output hints already exist | `strings.xml` `output_no_main_hint`, `output_timed_out_input_hint`, `install_prompt_body` |
| Tokens / type / contrast / motion systems | `ui/theme/CodecTokens.kt`, `CodecType.kt`, `CodecPalette.kt`, `Theme.kt`, `CodecMotion.kt`; tests `CodecTokensTest`, `CodecTypeTest`, `AppContrastTest`, `ChromeContrastTest`, `OutputPanelContrastTest`, `GhostContrastTest`, `MotionPolicyTest` |
| Touch-target enforcement scope | `test/.../TouchTargetTest.kt:12-30` |
| Semantics coverage today | `heading()` once (`ui/ai/AiMarkdownView.kt:177`); `stateDescription` in `EditorProjectDrawer.kt:223, 319` and `EditorSidePanel.kt:596`; no `liveRegion` |
| No large-screen adaptation | zero `WindowSizeClass` references in `app/src/main` |
| Stats exist, surface is About/hub line | `ui/stats/StatsManager.kt`, `StreakLine.kt:29`, `SettingsScreen.kt:1062-1076` |
| Phase 64 bans tours/help screens | `docs/phases/09-onboarding-setup/chat-phase64/PART_64_2_REMOVE_GUIDE.md`, `README.md` |

## Appendix B — Sources (annotated)

**Learning science**
- Sweller's cognitive load theory in CS education — review: https://dl.acm.org/doi/full/10.1145/3483843
- Parsons problems & learning theories (2025): https://dl.acm.org/doi/10.1145/3769994.3770032
- Parsons as scaffolding for write-code, self-efficacy (2023): https://arxiv.org/html/2311.18115v1
- Adaptive Parsons problems (2022): https://dl.acm.org/doi/fullHtml/10.1145/3501385.3543977
- PRIMM — teachers' experiences (pre-print): https://primmportal.com/wp-content/uploads/2019/01/pre_print_teachers__experiences_of_using_primm_to_teach_programming_in_school.pdf

**Errors**
- Becker et al., *Effective Compiler Error Message Enhancement* (2016): https://eric.ed.gov/?id=EJ1117113
- *Beyond the Traceback: Using LLMs for Adaptive Explanations of Programming Errors* (2026): https://arxiv.org/html/2608.20896v1
- *dcc --help*: LLM-generated novice explanations in a C compiler: https://arxiv.org/pdf/2308.11873
- DCC repository (GPL-3.0 — ideas only): https://github.com/COMP1511UNSW/dcc

**AI and novices**
- Kazemitabaar et al., CHI '23 (69 novices; gains without retention loss): https://www.terpconnect.umd.edu/~weintrop/papers/Kazemitabaar_et_al_CHI_2023.pdf
- Kazemitabaar et al., Koli Calling '23 (over-reliance patterns): https://austinhenley.com/pubs/Kazemitabaar2023Koli_LLMsCS1.pdf
- Systematic review, responsible GenAI integration (2026, "comprehension–completion trade-off"): https://www.sciencedirect.com/science/article/pii/S2666920X26001396

**Onboarding / UX**
- NN/g — Progressive disclosure: https://www.nngroup.com/articles/progressive-disclosure/
- NN/g — Instructional overlays / coach marks: https://www.nngroup.com/articles/instructional-overlays/
- NN/g — Error-message guidelines: https://www.nngroup.com/articles/error-message-guidelines/
- NN/g — Heuristic 9 card (PDF): https://media.nngroup.com/media/articles/attachments/Heuristic_9_A4-compressed.pdf
- Hoober, how users hold mobile devices: https://www.uxmatters.com/mt/archives/2013/09/how-do-users-hold-mobile-devices-part-1-a-closer-look.php

**Accessibility**
- WCAG 2.2 new criteria (summary with 2.5.7/2.5.8/3.2.6/3.3.7/2.4.11): https://www.hemispheredm.com/wcag-2-2-explained
- W3C COGA — *Making Content Usable*: https://www.w3.org/TR/coga-usable/
- Android — Compose accessibility API defaults (48 dp): https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- Android — accessibility testing codelab: https://developer.android.com/codelabs/basic-android-kotlin-compose-test-accessibility
- Modus themes (CVD variants; deuteranopia/tritanopia design precedent): https://protesilaos.com/emacs/modus-themes

**Syntax highlighting (mixed)**
- Sarkar, *The impact of syntax colouring on program comprehension* (2015): https://ppig.org/files/2015-PPIG-26th-Sarkar1.pdf
- Hannebauer et al., *Does syntax highlighting help programming novices?* (2018) — no effect on correctness (summary): https://www.researchgate.net/publication/323452417_Does_syntax_highlighting_help_programming_novices

**Open-source assets**
- CodeC course licence scope: `website/learn/licenses/SCOPE.txt`
- tldr-pages licence: https://github.com/tldr-pages/tldr/blob/main/LICENSE.md
- Microsoft CodeTour (MIT): https://github.com/microsoft/codetour
- TheAlgorithms (licence differs per repository): https://github.com/TheAlgorithms
- freeCodeCamp (software BSD-3; curriculum CC BY-SA 4.0): https://github.com/freeCodeCamp/freeCodeCamp
- Atkinson Hyperlegible (OFL): https://en.wikipedia.org/wiki/Atkinson_Hyperlegible
- Lexend (OFL): https://fonts.google.com/specimen/Lexend

> Sources surfaced through search but **not** re-verified line-by-line at this revision are marked in-text; treat any licence or statistic as needing a fresh check at the moment of adoption. Nothing in this document authorises adoption.

## Appendix C — Proposed glossary (one word per concept)

| Today (variants) | Proposed single term | Everywhere |
|---|---|---|
| Modules / Module Store / Packages | **Packages** | tab, screen title, cards, strings |
| userland / Linux tools / toolchain / setup | **Linux tools** (first mention: "the extra tools CodeC downloads once") | terminal intro, notices, dialogs, Settings |
| Source Control / Repository / Git | **Git** (screen/panel), "Repository" only inside Git's own panel title | hub ⋮, panel slot, dialogs |
| File Manager / Files / Projects / Project files | **Projects** (screen), "Files" only for the tree *inside* a project | drawer, hub, strings |
| Templates / starters | **Templates** for the library; **"starter project"** for the three tiles | hub, tiles, sheets |
| RUN / Run / Run in Terminal | **Run** (button label), `Run in Terminal` as one action name | editor, output, menus |
| Import/Export ZIP variants | **Import ZIP** · **Export ZIP** · **Back up all projects** · **Restore from backup** | hub ⋮ sheet, Settings |
| Install / Setup / Download | **Install** (a thing) · **Set up** (an action someone takes) | packages, storage, terminal |
| Ln/Col, "Spaces: 4" in the status bar | keep, but move behind the existing "Show file paths"-style developer grouping for the first days | status bar |

*End of dossier. Companion wireframes: [`beginner-ux-mockups/index.html`](beginner-ux-mockups/index.html).*
