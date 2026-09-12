# WEB_JOURNEY.md — narrative timeline of the CodeC website

Numbered entries, newest last. Append-only — never rewrite an entry, add a
correction entry instead. This is the website's `docs/JOURNEY.md`.

---

## W0 — Planning (2026-09-02)

**1. Owner asks for a website (2026-09-02).** The owner wants a website for
CodeC "like Termux have" (the termux.dev model: landing + install +
getting-started + FAQ). With a **strict rule: no code of any kind** — the
project must start the same way the app did, with its history & planning
structure first: a `web_docs/` folder (the website's `docs/`) and
`web_prompt.md` (the website's `prompt.md`, the self-distrust handoff file).

**2. Research (2026-09-02).** The agent read `README.md` (features, engines,
packages, install paths, troubleshooting), `rule.md` (operating manual:
branching, merge gate, invariants, docs policy), `prompt.md` (handoff format
to mirror), the `docs/` structure (living docs + `chat-phaseN/` session
records), and the public structure of termux.dev (landing page with install
CTAs + feature callouts; docs-style subpages).

**3. Plan written (2026-09-02).** `web_docs/WEBSITE_PLAN.md` created: 7-page
structure (Home, Install, Start, Engines, Packages, FAQ, About), per-page
content outlines with repo sources, design direction (mobile-first, dark,
terminal accents), locked static stack (HTML/CSS, GitHub Pages, no
framework/build/backend), `website/` folder layout, deployment approach,
implementation phases W1–W5 with exit conditions, acceptance criteria, and
open owner questions O1–O5.

**4. Living docs + handoff created (2026-09-02).** `web_docs/README.md`
(index & file map), `web_docs/DECISIONS.md` (D1–D9 + open O1–O5),
`web_docs/NEXT_STEPS.md` (head state + owner command table),
`web_docs/WEB_JOURNEY.md` (this file), `web_docs/chat-web1/SUMMARY.md`
(session record), and root `web_prompt.md` (paste-this-into-the-next-chat
handoff).

**5. W0 closed (2026-09-02).** Planning complete. **No code written** —
owner's strict rule honoured: the only new files are markdown. The website
now waits for the owner's implementation command ("Build the website" /
"start W1").

## W0.1 — Learning wing + self-dependent requirement (2026-09-02)

**6. Owner expands the scope (2026-09-02).** Same session, same strict no-code
rule. Two new owner commands:
1. **"I want it to be learning page also"** — the site must include a
   learning course, with the owner's own Termux-Mastery site
   (`pabitra27706-oss.github.io/Termux-Mastery`) as the structural
   reference: "Master Termux from zero to advanced", 15 numbered chapters,
   read-like-a-book, hands-on projects, course structure table, disclaimer,
   MIT license.
2. **"Make it fully proper self dependent"** — the site must stand alone:
   no external dependencies of any kind.

**7. Reference study (2026-09-02).** The agent fetched and studied the
public structure of Termux-Mastery: course home (about / why learn / how to
use / 15-chapter table / getting started / contributions / roadmap /
disclaimer / license) + numbered chapter folders. Since it is the owner's
own project, mirroring its structure is clean-room-safe; its content will
not be reused (the chapters are written fresh for CodeC from CodeC's own
docs).

**8. Plan v2 written (2026-09-02).** `WEBSITE_PLAN.md` rewritten to v2:
- **Two wings:** product (7 pages, unchanged in intent) + learning wing —
  `/learn` course home + **17 proposed chapters** (`ch-01` Getting Started
  → `ch-17` Troubleshooting), each on a fixed self-contained template
  (goals → steps → try-it → common mistakes → prev/next). CodeC-specific
  twists: chapter 04 (4 compiler engines), chapter 13 (CodeCApi = the
  CodeC answer to Termux API), chapter 14 (web projects + live preview).
  Chapters teach only what CodeC ships today; final set locked at W4.
- **Self-dependent = law (plan §5):** zero fetched external resources (no
  CDN/fonts/JS/images from outside, system font stack), outbound links
  fine, offline-complete rendering, course completable without the repo;
  W6 verifies via grep sweep + offline render + link sweep.
- **Phases restructured W1→W6** (D12): product wing first (W1–W3), course
  second (W4–W6 content), deploy + verification last (W6).
- Decision log: **D10** (learning wing), **D11** (self-dependent), **D12**
  (phases); open items **O6** (chapter set) and **O7** (course license)
  added.
- `web_prompt.md` updated (facts + phase list), `NEXT_STEPS.md` head state
  v2 + new phase queue.

**9. W0.1 closed (2026-09-02).** Planning v2 complete. Still **zero code** —
the strict rule stands. Waiting for the owner's implementation command.

## W0.2 — All phases fully spec'd (2026-09-02)

**10. Owner asks for the phases (2026-09-02).** Same session, same strict
no-code rule: *"Can you create phases"* — the website's implementation
phases should exist as fully spec'd docs, the way the app project's
Phases 20–24 exist as `docs/chat-phase20/` … `docs/chat-phase24/` before a
single line of code.

**11. Reference format studied (2026-09-02).** The agent read
`docs/chat-phase24/` (README: status/cost/depends/blocks, parts table,
ground rules; PART docs: design, implementation steps, numbered exit
conditions) and `docs/chat-phase20/README.md` (cost tags like
`[client-only]`/`[repo-build]`, owner blockquote, "hard constraint" callouts)
and mirrored the format for the website.

**12. Six phase folders created (2026-09-02).** `web_docs/web-phase1/` …
`web_docs/web-phase6/` — **35 docs** (6 phase READMEs + 29 PART docs), each
part with design, implementation steps and a numbered exit condition:
- **W1** (scaffold + Home): the shared-chrome pattern is documented once
  and copied byte-identical by all later phases; first self-dependent
  sweep runs in W1.1.
- **W2** (install + start), **W3** (engines, packages, FAQ, about): product
  wing complete; W3.2 pulls the **authoritative package list from the
  `codec-packages/` build config** (sha recorded) instead of trusting
  memory.
- **W4** (verification gate → course home → ch 01–06): **W4.2 runs first**
  in the phase — it re-verifies the README + package config into a
  committed Verified Facts Table, then **locks the 17-chapter set (closes
  O6)**; the canonical chapter template (crumb / goal box / steps /
  try-it / mistakes / prev-next) is defined in the phase README and every
  chapter part builds on it.
- **W5** (ch 07–12): ch-08 C Basics carries the **TCC-safe law** (ANSI C
  only, no C11 constructs) + a snippet-review checklist + a **device pass**
  (owner transcript); ch-12 has variant A/B decided by W4.2's tool list.
- **W6** (ch 13–17 + polish + deploy): ch-15 may mention planned app phases
  only as one "coming" line; W6.7 **verifies before live** (self-dependent
  sweep + offline render + full link sweep, all recorded) and is the only
  part allowed to touch outside `website/` (the Pages workflow + the
  one-line README link, D7).

**13. Living docs updated (2026-09-02).** Plan → v2.1 (phase table now
links the spec folders), `DECISIONS.md` D13, `NEXT_STEPS.md` head state +
phase queue + new "Start W2…W6" command row, `web_docs/README.md` file map
(naming split: `chat-webN` = session records, `web-phaseN` = planned
phases), `web_prompt.md` (state + commands).

**14. W0.2 closed (2026-09-02).** The website is now **fully spec'd end to
end**: 25 pages, 6 phases, 35 spec docs, zero code — the strict rule held
through every step. The owner's only remaining move before anything is
built: **"Start W1"** (or "Build the website").

**15. PR #41 opened (2026-09-02).** The owner commanded: "Please open a
pull request for the changes on this branch." Agent opened
[PR #41](https://github.com/pabi277/CodeC/pull/41)
(`arena/01a06270-codec` → `main`, tip `626719a` at open) with the full W0
summary. **Not merged** — the merge gate applies; the owner merges (or
hands the command).

## W0.3 — Sync with app Phases 21–43 (2026-09-12)

**16. Owner asks to sync website docs with app updates (2026-09-12).** Command: "Check the new updates in the project from the docs folder then update the web-docs and web-prompmt". The app has moved from Phase 19 (last known to website v2.1 on 2026-09-02) through **Phases 20–43** — all researched, implemented, device-passed and merged except Phase 43 planned. Website specs still at v2.1 still said "4 engines with picker", "three APK paths via Actions artifact", "no icon", "no file icons", "no LAN server", "no outputs temporary", "no GitHub truth", "no feedback", "no share-readiness", "no safe folder walk". Drift = website would state false facts.

**17. App delta researched (2026-09-12).** Agent read `docs/JOURNEY.md` tail (Phases 34–42), `docs/NEXT_STEPS.md` head (Phase 41 complete merged PR #70, Phase 42 complete merged PR #71 app-v1.3.17 universal 6.6 MB, Phase 43 planned), `docs/PHASE38_43_ROADMAP.md` + `PHASE34_37_ROADMAP.md`, `docs/chat-phase38/` … `chat-phase43/`, `README.md` (new install channel: universal APK only, signed non-debuggable, SHA256 lines, updater version guard + checksum + refusal, debug artifacts, export-all, backup include-list, crash-loop guard, targetSdk 28 deliberate, TCC default Auto only), `docs/BETA.md` (B-1…B-8 known issues), `docs/RELEASE_NOTES.md` template ({{SHA256_LINES}} asserted by check_release_notes.sh), `docs/PHASE5_ROADMAP.md`, `docs/TERMINAL_PLAN.md`, `codec-packages/README.md`.

Key facts extracted for website:

- **Phase 21:** Compiler picker deleted, Auto only — built-in TCC first, Clang module second, Termux fallback automatic, four setup steps appear in Output Panel via CompilerRemediation when Permission denied. `.c` no gate, `.cpp` installs clang.
- **Phase 22–24:** Smoothness (BasicTextField span windowing ±3000 chars, blank-line safe anchor, derivedStateOf no-op corrected), IME keys (EditorKey.Pair single-cap (){}[]<>""'', HTML/CSS/MD snippet sets, case-insensitive), insets (imePadding kept, adjustResize kept), inline PTY input (InlineInputRow last LazyColumn item when waitingForInput, sendLine), context-aware run keys (↵ Enter/Ctrl+C/Tab/↑↓ while waiting, no JNI change), desktop-class editor quality (formatter clang-format bridge + C-indenter fallback, foreground notification, HW shortcuts, ZIP share, test runner, open-with, adaptive theme, .codec.json).
- **Phase 25–33:** sora 0.24.6, bench wrapper removed, ghost text (GhostCompletion FULL/WORD/LINE, GhostHintRenderer, TAB ▸ / →▸ word / caret-row pill / tap-ghost / HW Tab & Ctrl+→), suggestion strip (StripContext Hidden|Run|Keys|Suggestions, Run always wins, ≥2 candidates → ƒ/λ/≠ chips + ⌨ + ⌄ more, tap-accept, long-press tooltip, swipe-down dismiss-per-identifier, CodeCCompletionComponent gates sora panel to ⌄ more browse only), CompletionPolicy law (Enter sacred, TAB tells truth, dismissal per-identifier, master off = zero chrome/computation), typing experience 2.0 (key strip 2.0 popups/swipes/hold-repeat + JSON user sets, smart typing type-over/wrap/empty-pair/auto-indent/delete-word string-aware, IME guide), TextMate core (language-textmate, MIT grammars C/C++/Python/JS/TS/HTML/CSS/JSON/Shell/MD/Go/Rust/PHP/Ruby/Lua/XML/YAML, VS Code Dark+ dark_plus token colors, lazy per language off UI thread), snippets+Emmet (29 friendly-snippets MIT packs ~54KB deflated pinned 6cd7280, 84C/76Python/126HTML/156CSS/367JS/140TS/62MD/16shell, Emmet clean-room rank 0 markup ! > + ^ *n () .class #id [attr] {text} $ + CSS 82 abbrevs, MAX_ITEMS 8→50 snippets≤40/identifiers≤6/keywords≤6 MAX_CHIPS 8 + ⌄ more, replaceLength/caretOffset), LSP as Packages (hand-rolled LSP stdio JSON-RPC sora editor-lsp AAR gated minSdk26 vs 24), phone canvas (NavBarPolicy auto-hides 5-tab bar while Keys/IME up, thin handle, chips-as-row-0, OutputDiagnosticTarget), RUN chooser (default run option as user task, chooserDefault "Run default / Run open", EditorViewModel.runFile, space-in-path fix ccScript set -- "$@" "$arg", missing main hint CompilerDiagnostics.looksLikeMissingMain, CEntryWrapper single file no main one function via wrapper), first-hour tiles (WelcomeStarters 3 starter tiles C/Python/HTML DataStore first_launch_complete, Packages hub Languages & IntelliSense open Unix tools collapsed search flattens).
- **Phase 34–37:** 34 official file icons (extension→icon map, Seti MIT + Compose PathParser, applied Files tab tree + editor drawer tree + editor tab bar + hub/drawer file glyph); 35 editor typing feel (always keyboard stay open Settings toggle + no gesture dismisses CodeC Keys while editing, smooth typing measure p95, solid non-blinky caret while typing, no caret on open until tap); 36 terminal speed & feel (cold-start tap→prompt, PreparedShell cache, "starting shell…" state, session restore, clear restart/new-session affordances, honest exit notices, progressive apt streaming, background command survival, session-switch redraws, TerminalSessionManager N concurrent PTY 8-cap anyAlive wake lock, TerminalViewModel delegation one CodeCApi collector per session, TerminalScreen session switcher dropdown + rename/close, TerminalEmulatorView.resizeKey fixes cursor drift); 37 device as server LAN (opt-in LAN switch LanSharePolicy OFF by default not persisted, WebPreviewServer binds 0.0.0.0 pool 8100–8199 never <1024, flask/fastapi/C templates honour CODEC_SERVER_HOST, LanAddress pure + LanAddressProvider Android, ServerEndpoints.of(port,bind,lanAddress,lanShared) single place two URLs refuses to advertise unreachable LAN URL panel states why, ServerSharePanel both URLs with copy + ZXing core 3.5.4 QR Apache-2.0 assets/licenses/ZXING_APACHE2.txt About line, ServerRegistry one owner port truth + ServerHost one owner socket/process leaving editor keeps LAN server serving while loopback preview dies with screen, same RunForegroundService no second type Serving <project> on <ip>:<port> + Stop ticker suppressed, bugs fixed error responses Content-Length without body, Stop orphaning server, stop racing blocked accept, ShareActions + OpenInBrowser single ACTION_VIEW launcher terminal link handler delegates to it launch that no browser can serve copies link, 68 host cases 10 classes, device round All pass 8/8 phone+second device same Wi-Fi + 🌐 open-in-browser follow-up).
- **Phase 38:** Identity — original >_ mark docs/icon/codec-mark.svg 108-viewport inside 66dp safe box + 33-unit safe circle farthest ~26.9, flat #3DDC84 glyph + flat #101418 background, new ic_launcher_monochrome.xml white same two paths, both mipmap-anydpi-v26 adaptive XMLs rewired, ic_stat_codec.xml bolder 24-grid silhouette, app_mark.xml rounded tile + glyph About header + future in-app identity, 10 PNG rasters 48/72/96/144/192 square/round + docs/icon/codec-512.png generated by scripts/render_icon.mjs node+sharp 0.35.4 BSD-3 pinned scripts/package.json committed outputs re-run twice byte-identical md5 guarded by scripts/check_icon_assets.sh CI step + IconAssetSetTest, template webp deleted, RunForegroundService + TerminalForegroundService + CodecApiBridge switched to ic_stat_codec no ic_launcher_foreground in status slot no system ic_dialog_info, About header shows mark+name+tagline, icon/roundIcon names versionCode app label untouched, NotificationChannel has no icon field per-notification which is what 38.1 fixes. Settings 13→11: Compiler Settings + Built-in Compiler + Termux Engine merged into one Compiler (three real settings, Engine prose one-sentence terminal-app promise, TCC status line), whole Termux card deleted (status row OPEN TERMUX CHECK BRIDGE How to enable) together with TermuxUiState/loadTermuxState/buildTermuxStatusText/formatProbe + TermuxCompiler import, mechanism untouched TermuxCompiler fallback + RUN_COMMAND permission + <queries> stay, four setup steps moved to ui/editor/CompilerRemediation.kt pure CompilerDiagnostics-style failed build whose output carries exec Permission denied signature gets SYSTEM remedy line in Output Panel EditorViewModel.finishFailedBuild next to Phase-33 no-main hint wording source TROUBLESHOOTING.md §27 CompilerRemediationTest pins both directions, audit SETTINGS_AUDIT.md 43 control rows deleted three more dead things duplicate Appearance Terminal Theme dropdown exact duplicate Terminal section's, bare Licenses duplicate Open-source licenses, two store keys recent_files_csv write-only since writers existed EditorScreen+EditorViewModel called it on open/rename no reader anywhere and smart_typing_delete_word no reader no writer ⌫ flick-up delete-word gesture always-on by design deleted with four call sites, SettingsKeysHaveReadersTest walks real chain key→flow/setter→out-of-store reader 4 hops stores enumerated SettingsManager+ThemeManager+GitCredentialsStore so next dead key fails build, SettingsAuditTest pins audit table to screen section order via machine-checked manifest line per-section+total control counts the Termux card must not come back, 45 new host cases pre-validated 45/45 local JVM Temurin via jdk4py+kotlinc JUnit shimmed pre-validation caught 5 real bugs, CI green Build APK 34442522565 tip ce1a38c 5m57s every step green including new Check icon assets step first run gradle-bootstrap bridge ran assembleDebug+testDebugUnitTest+lintDebug inside assemble so 45 new cases executed, artifacts CodeC-IDE 24 815 485 B -32 351 B -0.13% vs main deleted template webps outweigh new PNG set, docs follow-up 34443027257 green, device round PASSED owner report All device test pass no device details supplied none invented pass recorded as owner's report same as Phases 33/35/36, merge authorized owner in same message updated all md files and if phase 38 complete then marge it Phase 38 MERGED to main via PR #64 → main at dcd65b4 merge commit history preserved PR checks green 34445023100.
- **Phase 39:** Outputs temporary never in repo — RunArtifacts routes every language's build output to filesDir/CodeC/temp/runs/<stamp>/ + TempGc prunes age/capacity/newest-N on start and after Stop, RepoHygiene ~60 patterns incl .codec/ derived from CC0 github/gitignore templates plus CodeC own .codec/ .codec.json applied at single choke point stageAll instead of one call site in GitControlViewModel.refresh() with git rm --cached for anything already tracked and what will be committed list before commit button, user's own .gitignore always wins law kept and tested.
- **Phase 40:** GitHub truth — GitReadiness/GitBlocker pre-flight answer (is git installed · is token stored · is this a repo · does it have a remote · does branch have upstream · is there anything to push) computed pure data rendered before user taps one-tap Install Git routed to existing Modules installer, every git failure gets visible home clone dialog shows own inline error (bug today dialog stays open on failure and snackbar fires behind it FileManagerScreen closes showCloneDialog only in success callback), commit/push ends in result card built from git's own stdout PushOutcome what was pushed to which remote/branch Everything up-to-date * [new branch] or still local because X and project with no remote gets Publish to GitHub action creates repository through POST /user/repos or when token cannot says precisely which permission missing GitHub returns it in X-Accepted-GitHub-Permissions and offers browser fallback, private by default ls-remote-verified adopt-existing, GitReadiness isAvailable() finally called.
- **Phase 41:** Feedback that reaches you — FeedbackDraft → https://wa.me/<digits>?text= with owner's number as setting email/copy/GitHub-issue fallbacks GitRedactor plus its own tested pattern table no telemetry three honest disclosure lines, then follow-up: owner's real contacts ship in APK FeedbackStore.DEFAULT_WHATSAPP_NUMBER 916296746606 / DEFAULT_CONTACT_EMAIL PART_41_2-recorded Phase 42 decision point decided early stored value always wins clearing explicit off, feedback became own screen ui/screens/FeedbackScreen.kt Settings keeps one OPEN row audit control 46 and exit-prompt switch lives next to what it controls, exit survey back-at-root Enjoying CodeC? 💚 star row / SHARE EXPERIENCE / GIVE A REVIEW / NOT NOW / EXIT tap back again to exit via dialog's own back press outside taps disabled rating rides report info line · Rating: 4/5 and NOTHING uploaded by itself GIVE A REVIEW opens public repo whole prompt off-able feedback_exit_prompt_enabled default ON no nag law amended by owner's request switch keeping it honest Phase 42 owns long-term fate, then round 2 owner I want to sit as developer not some other guy So i want my number hard coded Any feedback comes to me no need for user to set number user know me or don't know me does not matter a bit So remove the boxes and set it in the code REPLACED reply-to fields SAVE FeedbackStore.kt and FeedbackContacts.kt DELETED both contact DataStore keys gone ExitSurveyTest pins they never come back and constant is valid E.164 wrong constant would silently hide CHAT row so pin matters exit-prompt switch moved to SettingsManager feedback_exit_prompt_enabled now ONLY feedback key in any store covered by audit reader-chain, unchanged ephemeral-checkboxes privacy law three-line disclosure line 3 now says the developer WhatsApp-less fallback nothing sends by itself, Test deltas FeedbackNumberSettingTest deleted with feature ExitSurveyTest grew round-2 pins persistence+key-reader tests updated 65/65 host-pre-validated, round-2 runbook D1/D8 updated no fields to check chat must open to hardcoded number, CI first run 34532387788 red on unrelated UserlandInstallerTest Connection reset loopback keep-alive race between installer's ranged HttpURLConnection GET and test's in-process HttpServer diagnosed TROUBLESHOOTING §31 with for-cause fix recorded if ever recurs and retrigger run 34533033047 tip 0d8317c GREEN artifact 24 998 614 B, round-2 device round pending merge HELD, then MERGED 2026-09-11 owner Merge it PR #70 arena/01a08cc6-codec into main 7 commits base 8fdbe6a → docs b326d49 → follow-up 4cfa1ce → docs 61f136b → round2 23a8c44 → flake diagnosis 0d8317c → docs aae92eb → merge record every push CI-checked green apart from one diagnosed UserlandInstallerTest loopback keep-alive flake 34532387788 → green retrigger 34533033047 TROUBLESHOOTING §31, device round1 8/8 owner 1-8 pass round-2 device pass D1–D8 not separately reported owner merged on own judgment runbook stays in repo, exit state feedback first-class surface own screen + exit survey + crash-prefilled report every channel points at HARDCODED developer DeveloperContact checkboxes ephemeral nothing ever sent by app itself, next Start Phase 42.
- **Phase 42:** Share-readiness signing size updates not eating data — 42.1 release channel publish lane app-v* tags only version guard notes template with SHA256 lines idempotent attach UpdatePolicy name-grammar law first publish app-v1.3.17 run 34597577303 GREEN shipped CodeC-IDE-1.3.17-universal.apk three attempts recorded attempt1 signed-assemble failed opaquely error-surfacer itself crashed → lane now annotates own filtered tail and surfacer crash-proof attempts2-3 real error Failed to read key upload null turned out mobile-clipboard whitespace inside GitHub secret → owner re-entered published, 42.2 weight honest measurement arc R8 release-only + shrinkResources + crunch proguard-rules.pro rewritten as law file every keep needs named proven failure first proven jdt.annotation dontwarn run 34577896124 okhttp pair removed on grep proof per-ABI splits built MEASURED and REVERTED by owner 0.94–1.77% savings vs 15% law assets/tcc/<abi> rides every split x86 kept measure-only release lane throwaway key uploads nothing made R8 numbers possible before secrets existed final record 25 553 564 → 6 630 554 B -74% release manifest machine-proven non-debuggable every run mapping.txt 54.9 MB CI-only, 42.3 launch safety backup XMLs re-architected include-list-only after FullBackupContent lint law bit exclude-under-include hard error runs 34561139415/34567771307 tuition crash-loop guard → safe mode on 3rd failed launch with export + report hand-off exportAllZip over both project roots 11 privacy rows audit-pinned raw-source telemetry scan CI taught along way kept as pattern check-run annotations as failure surface when log blobs unreachable, owner device round on release 4/4 pass fresh install editor across language matrix About fingerprint offline C compile updater up to date API 24/26 install + 42.3 runbook executions carried to BETA.md tester round per Phase 41 merge precedent never dressed as tested, merged via PR #71 merge commit 3c983c6 main CI 34602562874 Phase 42 closes one universal 6.6 MB signed release updater that can be trusted backups that cannot take your data beta checklist docs/BETA.md for strangers.
- **Phase 43 planned:** File system strength — bounded Throwable-safe folder walk crash is StackOverflowError escaping unbounded copyDocumentChildren caught only as Exception then open-folder-as-project via ProjectLink + persisted SAF grant, 43.1 crash-proof folder import + cancel/progress TreeWalkPolicy budgets progress+cancel per-provider failure as message Throwable at boundary grant persisted takePersistableUriPermission never called today, 43.2 ProjectLinkPolicy.decide take/refuse-with-reason/re-pick when grant gone honest limitation emulated storage mounted noexec so linked project runs from internal mirror sync in on open/save push-set on save-back excludes CodeC own outputs 39.1 must land first, all-files-access open in place variant considered and rejected as default four reasons and one per-project flag may still earn place.

**18. Website specs updated to v2.2 (2026-09-12).** `WEBSITE_PLAN.md` v2.1 → v2.2: install facts now universal APK 6.6 MB signed SHA256 + updater version guard + debug vs release + export-all + backup include-list + crash-loop guard; engines Auto only no picker no Termux card fallback automatic four steps appear in Output Panel only when needed; Home cards include file icons, LAN server, safe features; FAQ includes BETA B-1…B-8 + debug/release + signature change + backup + crash-loop + export-all + huge folder + 32-bit TCC null; About includes >_ mark + Settings trim + feedback hardcoded + LAN + file icons + outputs temporary + GitHub truth; chapters 01–17 enriched (ch-04 Auto, ch-06 safe walk + ProjectLink + export-all, ch-07 editor with Seti icons + typing feel + ghost + strip + snippets 29 packs + Emmet + TextMate + 50 items, ch-08 TCC-safe law, ch-11 GitReadiness + push truth + publish, ch-12 LAN server + QR + open-in-browser, ch-13 8 scripts, ch-15 export-all + backup + crash-loop + feedback, ch-17 BETA + crash-log + feedback). `DECISIONS.md` D14–D22 added, O8 added (feedback contact display on website?). `NEXT_STEPS.md` head state v2.2 + phase queue updated with new facts + O8. `web_prompt.md` updated: distribution facts (universal APK 6.6 MB signed SHA256 + updater), repo facts (icon >_ mark + Settings trim + file icons + LAN + outputs temporary + GitHub truth + feedback hardcoded + export-all + backup include-list + crash-loop guard + safe walk planned), engine facts (Auto only no picker Termux card deleted but mechanism stays). `WEB_JOURNEY.md` this entry. `web-phase2/PART_2_1_INSTALL.md` and `web-phase3/PART_3_1_ENGINES.md` etc need v2.2 refresh in W1 implementation (noted as required diff). The website still awaits "Start W1" — zero code written, strict rule held.

---

*Next entry: W1 (scaffold + Home) — after the owner's implementation command, now with v2.2 facts.*

