# prompt.md — paste this into the next chat

> Copy **everything between the two `---` lines below** as the first message of
> a new chat. It forces the next agent to verify before acting, to trust the
> repo over its own assumptions, and to continue CodeC without redoing or
> breaking anything.

---

Read `docs/JOURNEY.md`, `docs/NEXT_STEPS.md`, and **`rule.md`** first, before
doing anything else, then report what you found and the current git/PR/CI
state before making any change.

You are continuing **CodeC** (an Android C / multi-language IDE with its own
Termux-style terminal + signed package repo). Each chat session gets its own
`arena/*` session branch — verify with `git status`; commit and push to the
SESSION branch only, never `main` or any other branch. **`rule.md` is the
operating manual for all work after Phase 18** (branching, lifecycle, merge
gate, invariants, docs policy) — follow it.

**WHERE THINGS STAND (2026-09-09, Phase 32 (phone canvas) ✅ DEVICE-PASSED + Phase 33 RUN ▶ chooser implemented, both on `arena/01a083fc-codec` — NOT merged, the owner merges (§3); owner started 32 with "Start phase 32"): 32.1 auto-hides the 5-tab bar while the editor is focused AND Keys/IME is up, with a thin tap-or-swipe handle to reveal it (pure `ui/editor/NavBarPolicy.kt` + `ui/editor/EditorChromeState.kt` bridge; `MainActivity` Scaffold `bottomBar` is now a `when`; `EditorScreen` reports `codecKeysUp`); 32.2 needed NO code — 28.2 (`suppressKeysVariant`) + 28.3 (chips as row 0) already give the one-meaning-row and 32.1 removes the nav; 32.3 makes a diagnostic tap land in the USER's file — `ui/editor/OutputDiagnosticTarget.kt` falls back to the active file for `source_<stamp>.c` / non-resolvable names (`EditorViewModel.openOutputDiagnosticFile`). Tests `NavBarPolicyTest` + `OutputDiagnosticTargetTest`; CI `34305070875` ✅ GREEN first try (tip `b5feb55`); **device round `TROUBLESHOOTING.md` §16 ✅ PASSED** (owner: "All test passed on device"). **Phase 33 first item (owner: "when user hit run it asks index/main file or the file currently opened"):** RUN ▶ now shows a two-button chooser when a project has a main/index file (`ProjectConfig.entry`) that differs from the open file — pure `ui/projects/ProjectRunTarget.kt` (`chooserEntry`/`shouldAsk`), `EditorScreen` `runMainFile`/`runCurrentFile`, and `EditorViewModel.runFile(context, target)` (target-aware run: registry decision on the target, dirty background-tab flush, diagnostics attributed to the target's basename, install gate resumes the same target). Test `ProjectRunTargetTest` (6); CI `34307172630` ✅ GREEN first try (tip `788ba46`). Before that: Phase 31 ✅ MERGED to `main` (2026-09-08) on owner "merge it"; CI `34210108408` GREEN tip `278099e`; device recipe §15 not run; sora `editor-lsp` AAR gated. Before that: `main` tip = PR #55 (Phase 30 merged); PHASE 30 (Offline completeness — MIT snippet packs + clean-room Emmet + strip capacity) ✅ COMPLETE, DEVICE-PASSED & MERGED to `main` via PR #55 (owner: "Start phase 30" → "If all done then merge it", 2026-09-07); all three parts in one build; 91 new host tests + 6 new cases, plus 3 more from the device round (`CodeCompletionTest` 19, `SmartTypingTest` 13) — **124 green locally** over the real production files; **CI: `34034889209` GREEN first try → one for-cause round (`34040754444`, a hand-counted caret literal in a new test) → `34041185149` GREEN → the device round's `34077539890` RED on the known sora/Robolectric flake (`EditorLaunchMeasureReproTest` → `IllegalThreadStateException` in sora's unsynchronized `AsyncIncrementalAnalyzeManager.rerun`) → `c2b392e` tolerates exactly that one signature → `34078739941` GREEN (tip `c2b392e`, 8m33s); APK artifact `CodeC-IDE` 24 375 211 B = +117 409 B (+0.11 MiB) vs `main`**; **device round 1 (`docs/TROUBLESHOOTING.md` §13, build `ca8ec57`) reported TWO bugs — accepting a suggestion kept the typed prefix (`#in` + tap → `##include <stdio.h>`) and CodeC Keys closed no brackets — both fixed in `d63a645`, both re-measured on a host JVM, both device-confirmed PASSED via §14 (owner: "Yes working", 2026-09-07)**; records in `docs/chat-phase30/` + JOURNEY §41 + `docs/TROUBLESHOOTING.md` §13/§14. Before that: Phase 28.2 MERGED via PR #52; PHASE 29 (VS Code colour / TextMate) 🚧 IMPLEMENTED on the session branch — owner: "Start phase 29"; CI green; device round 1 hit two crashes, BOTH FIXED — crash 1 CME in sora theme dispatch (`3fb404f`); crash 2 `IllegalStateException: LayoutNode should be attached to an owner` = Compose 1.7.1 detached-node-during-nav-transition family, fixed by `composeBom 2024.12.01` (1.7.6) — and the CI NavHost-transition repro test then caught the deeper VM→sora replay bug (incremental delete-all into sora's async-rebuilt layout), fixed by an atomic `setText` replay (`db56824`); crash-log now reports header-first (COPY ALL = complete record); BOTH crashes device-confirmed fixed; **device round 1 PASSED 2026-09-06 — checklist ALL PASS + APK-size deviation (+2.22 MB) ACCEPTED by the owner; MERGED to main via PR #54 (owner: "Merge it", 2026-09-06)**; records in `docs/chat-phase29/`):**

- **Phase 30 (all three parts in one build) — WHAT is offered, not how it is
  accepted.** 30.1: the four hand-written snippet tables (7 C / 9 Python /
  8 HTML / 3 CSS) are now **29 vendored MIT friendly-snippets packs** in
  `assets/snippets/` (277 KB raw ≈ 54 KB in the APK, pinned to upstream
  `6cd7280`, notice in `assets/licenses/FRIENDLY_SNIPPETS_MIT.txt` + About
  line, re-vendorable via `scripts/vendor_snippets.py`) resolved by five new
  pure files in `ui/editor/snippets/` (`SnippetJson` strict reader ·
  `SnippetSyntax` VS Code resolver: tabstops/mirrors/choices/`TM_*` vars/
  regex transforms + case ops + `:+ :- :?` conditionals/bounds ·
  `SnippetPacks` entry→items, ≤3 prefixes, first-wins dedupe, ≤64-char detail ·
  `SnippetAssets` LanguageType→packs, JSON/TEXT/XML/YAML none ·
  `SnippetLibrary` attach-by-AssetManager-identity + two cache layers +
  warm-up + degradation) = **84 C / 76 Py / 126 HTML / 156 CSS / 367 JS /
  140 TS / 62 MD / 16 sh** items; the built-in tables ride
  along as a **deduped TAIL** after the pack (and are the whole list when no
  pack loads) — they carry the 22.6 DOCTYPE skeleton, the app-private shebang
  and the descriptive labels a prefix-only pack cannot reach (`head` →
  `# Heading`, `pr` → `print(...)`); matching stays case-insensitive (22.6
  law); identifiers still rank below snippets; the empty-prefix trigger path
  excludes an item whose INSERT TEXT is the trigger (not whose label is — pack
  labels ARE trigger words).
  30.2: **clean-room `ui/editor/Emmet.kt`** (859 LOC, no dependency — rule.md
  §6) at **rank 0** of the same pipeline: markup (`!`/`html:5`, `> + ^ *n
  ( )`, `.class` `#id` `[attr]` `{text}`, `$`/`$$` numbering with counter
  propagation, implicit tags, 16 void elements, `/` + JSX self-close) and CSS
  (82 abbreviations longest-match, 14 unit suffixes, `-` separators vs
  negatives, `!important`, `+` chains ≤6, keyword tables, caret after `": "`),
  with guards that REFUSE rather than guess (structural signal required; no
  firing inside a tag/attribute/comment/string/selector; `.jsx`/`.tsx` only
  for JSX; plain `.js` and **C never fire**), bounds (120 chars / 100 repeats /
  400 nodes / depth 24) and `finish()` re-indenting CONTINUATION lines onto
  the caret's base indent. 30.3: `MAX_ITEMS` 8 → **50** (snippets ≤40,
  identifiers ≤6, keywords ≤6, tier order unchanged), `MAX_CHIPS` stays **8**
  (⌄ more = the rest, 27.2), ghost still rank 0; `CompletionItem` gained
  nullable `replaceLength`/`caretOffset` (null = the 27.x shape) honoured by
  the VM accept + ghost FULL, ignored by WORD/LINE. **`CompletionPolicy.kt` is
  NOT in the diff** — Enter sacred, master switch, no auto-commit — plus ONE
  narrow test-pinned S1 exception (a lone Emmet candidate chips, because a
  ghost cannot cover an expansion). Tests: `SnippetSyntaxTest` 21 ·
  `SnippetPacksTest` 12 · `EmmetTest` 24 · `SnippetLibraryTest` 15
  (Robolectric, real assets) · `CompletionCapacityTest` 19 (host mirror of all
  three exit conditions; the plan's named test = prefix `i` in C → 10
  candidates vs 7) · +2 `StripContextTest` · +4 `GhostCompletionTest` ·
  `CodeCompletionTest` pinned to the fallback world. Two bugs found pre-CI:
  `TM_DIRECTORY`'s chained `substringBeforeLast` ("" for `proj/main.c`) and a
  refused bare `*` in `ul>*`. **CI: `Build APK` run
  `34034889209` GREEN first try (tip `641f6e8`, 4m34s; artifact +0.11 MiB
  vs `main`). Gate: owner device round per `docs/TROUBLESHOOTING.md` §13.**
- **Phase 29 (all three parts in one build) — TextMate is the editor's
  analyzer.** Sora `language-textmate` from the SAME 0.24.6 BOM (binary
  dep only, LGPL-2.1 notice in `assets/licenses/`); 24 MIT grammar JSONs +
  4 theme JSONs as assets (~234 KB gzipped; vscode / TypeScript-TmLanguage
  / LuaLS, MIT notices shipped); default editor theme **VS Code Dark+**
  (flattened `dark_vs`+`dark_plus`; Monokai/Dracula/GitHub-Dark remain);
  `LanguageType` split (TYPESCRIPT, HTML, CSS, GO, RUST, PHP, RUBY, LUA,
  XML, YAML) so EVERY `LanguageRegistry` extension has a grammar; the regex
  highlighter is off the hot path (fallback + SmartTyping probe + templates
  preview only; `CodeCScheme` deleted). Lazy per-language grammar loading +
  background warm-up (PHP/Ruby sets deferred to first open). Host tests:
  `TextMateGrammarsTest` (pure) + `TextMateSupportTest` (Robolectric, real
  assets, asserts the analyzer swap for all colourable languages).
  **Gate: `Build APK` green + owner device round per
  `docs/TROUBLESHOOTING.md` §12** (Dark+ look vs desktop, every language
  coloured, `.txt` plain, typing still ≤ 16.7 ms p95, theme switching,
  APK delta ≤ +1.5 MiB — measured +2.10 MiB, owner verdict pending).
  Crash 1 (CME) fixed `3fb404f`; crash 2 fixed `288b760` (Compose
  BOM 2024.12.01) + `db56824` (atomic replay) — **device round 1
  PASSED 2026-09-06 (checklist ALL PASS; APK +2.22 MB ACCEPTED by the
  owner over the +1.5 MiB plan budget)**. **MERGED to main via PR #54
  (owner: "Merge it").**
- **Phase 28 remainder:** 28.2 (CodeC Keys layout engine) MERGED via PR #52
  after owner rounds 1–3 (default ON; standing regression card
  `TROUBLESHOOTING.md` §11); 28.3 (chips as row 0) + 28.4 (accessibility)
  remain planned — they do not block 29 and 29 does not depend on them.

- **Phase 24 is ✅ MERGED to `main` via PR #47** (2026-09-04; device round 1:
  E.1/E.2/E.4/E.6/E.7/E.8/E.9 passed; E.3 hardware shortcuts NOT
  device-verified — needs a Bluetooth keyboard; E.5 tablet two-pane DEFERRED
  by design). **PR #48** added the mobile-editor research dossier and the
  Phase 25–28 plan docs. `main` tip = `5ebbc6e`.
- **Phase 25.1 (bench spike) is ✅ COMPLETE & DEVICE-GATED — C-SORA WINS
  (2026-09-04).** Owner: "Start Phase 25" → bench built on this branch (CI
  green `33849153135`, artifacts `CodeC-IDE` + `CodeC-Bench`) → owner ran the
  device round and exported the full sheet (cold open + 4 scenarios ×3 reps ×
  3 candidates × bench.c/bench.html). Results:
  - **C-now (today's core) misses every bench.c budget**: keystroke p95
    **404 ms** (100 % jank — ≈24 missed frames per key), fling ~89 ms frames,
    caret drag ~150–230 ms, completion ~490 ms; even windowed bench.html runs
    ~90 ms keystrokes. The owner's complaint is now measured evidence.
  - **C-sora passes EVERY budget on BOTH corpora**: keystroke p95 14.5–16.6 ms,
    fling ≤3.1 % jank with 0 bad frames, caret-drag p95 ≤17.9 ms (auto-scroll
    traversed 15 lines), completion p95 18–22.5 ms, cold open 35–56 ms;
    `Typed=62` on a 60-key burst = SymbolPairMatch pairing `(`/`{` live.
  - **C-compose2** locked at ~36 ms frames (100 % jank, whole-window
    recomposition storm), drag traversal 0 — dead.
  - **VERDICT (in writing): 25.2 (Sora Editor integration) is the chosen
    path — starts ONLY on the owner's "Start Phase 25.2". 25.3 is ❌
    CANCELLED** (note at the top of `PART_25_3_COMPOSE_FALLBACK.md`).
    Decision table: `docs/EDITOR_MOBILE_RESEARCH.md` §3.1; raw numbers:
    `docs/chat-phase25/PART_25_1_SPIKE_BENCH.md` §4.5–§4.6; JOURNEY §34.
- **Phase 25.2 (sora-editor 0.24.6 as the edit core) is ✅ IMPLEMENTED &
  DEVICE-ACCEPTED (2026-09-04, owner: "All passed" after 4 device rounds).**
  Widget-only swap on `arena/01a06b20-codec` (9 CI rounds, green at
  `33866749797`/`c54228d`): `SoraEditorHost` bridge keeps the VM canonical
  (tabs/dirty/autosave/undo/find/strip/shortcuts), sora-native pinch,
  magnifier, symbol pairs, find-highlight — and (round 3, owner request)
  sora's NATIVE at-caret completion panel fed by the same engine (the old
  app popup is retired). Device-round 1 crash → root-caused from the
  owner's IN-APP crash report (`CrashReportOverlay`, no root needed):
  `EditorColorScheme` constructor calls `applyDefault()` before Kotlin
  assigns subclass state → NPE; fixed post-construction via
  `CodeCScheme.of()`. Drawer edge-swipe disabled over the editor (scroll
  conflict). Record: `docs/chat-phase25/PART_25_2_SORA_PATH.md` §4.
  **All gates satisfied: owner "Yes" (LGPL-2.1) + "Merge" → PR #49
  (squash).** APK delta +0.55 MiB (budget ≤ +2 MB). The 25.1 bench CI
  wrapper + `CodeC-Bench` artifact are removed with the merge (Phase 25
  closed); `bench/` stays in-tree for future re-benchmarks.
- **Phase 26 (Typing Experience 2.0) is ✅ MERGED to `main` via PR #50**
  (2026-09-04; tip `c1b4321`, post-merge CI `33941444393` green) — key strip
  2.0 popups/swipes/hold-repeat + JSON sets, smart typing, IME guide.
  JOURNEY §36.
- **Phase 27 (Phone-native Autocomplete) is ✅ MERGED via PR #51**
  (2026-09-05) — ghost text + chip strip + native panel demoted to ⌄-more
  browse, the law in pure `CompletionPolicy`/`StripContext`, settings master
  switch. 5 CI rounds incl. device-round-1 fixes (composing-gate removal +
  line-tail alignment on word boundaries); `docs/chat-phase27/` §4 records.
  E.3-style per-part device recipes remain owner-side optional.
- **Phase 28 (CodeC Keys) is 🚧 STARTED — 28.1 IME-free spike BUILT
  (2026-09-05, owner: "Start phase 28"), :bench-only, never shipped**: K1
  (Compose core) / K2 (sora core) each fed ONLY by an IME-free 3-row code
  grid through the production `EditorKeySet` model (mirrored verbatim), with
  DOWN→commit latency ledger, strict-subsequence tap audit, IME-inset
  flicker probe (+ self-check toggle), HW-keys + stdin-route scenarios
  (spike Q1/Q2) and a 5-min human session. **Device round 1 RECORDED
  (2026-09-05, `PART_28_1_SPIKE.md` §6): K2/sora passes EVERY budget**
  (DOWN→commit p95 1.25–3.4 ms, tap audits 64/64 & 40/40 exact on all reps,
  run-route OK, IME inset 0 px in ~2 900 samples — never opened); K1's frame
  reds are the Compose core's known ~260 ms cost, the keyboard itself was
  sub-1 ms there too; **28.2 input path of record = S2**. **VERDICT: GO — recorded
  2026-09-05 (owner: "Go", twice; the four human confirmations waive as
  blockers and ride 28.2's device round). `docs/EDITOR_MOBILE_RESEARCH.md`
  §9.1 carries the verdict. **28.2 (layout engine) BUILT the same day** —
  `:app/ui/keyboard/*`: JSON layout over the 26.1 cap schema, pure router,
  Compose grid, Settings (default OFF), S2 wiring (`updateCode` + sora
  `setSoftKeyboardEnabled` handoff), `EditorKey.Delete` = DEL's model home,
  20 host tests; CI green `33964504903`. **28.2 device rounds 1–2 ran and
  landed same day** (cap corner previews + hold swap, `->` row deleted, arrow
  nav = flicks; then LIVE-buffer commits `applyEditorKey` fixing fast arrows,
  Samsung space trackpad, and the master flipped **DEFAULT ON** per owner —
  off restores the 22.x IME world exactly; round 3 made EVERY key on the
  symbols layer a single character — no more pair/macro caps, language-free
  letters layer). Retest card:
  `docs/TROUBLESHOOTING.md` §11; PASS opens 28.3.

**PHASE STATUS (updated 2026-09-05):**
**Phase 25 CLOSED (25.2 device-accepted & merged via PR #49; 25.3
CANCELLED); Phase 26 MERGED via PR #50; Phase 27 MERGED via PR #51;
Phase 28 STARTED — 28.1 spike built, device round pending**
(`docs/chat-phase25/` … `docs/chat-phase28/`).
Phases 20.1, 21, 22, 23, 24, 26, 27 are COMPLETE and merged (27 via PR #51;
26 via PR #50; 24 via
PR #47; 22/23 via PRs #45/#46; 21 via #44; 20.1 via #43). Phase 28 is
STARTED: 28.1 (IME-free input-path spike in `:bench`) is BUILT on this
session branch — CI + owner device round are its gates; 28.2–28.4 wait for a
recorded GO (design law 3: feel is the gate).
- **Phase 33** (first-hour UX) — 🚧 first item done (RUN ▶ chooser) on `arena/01a083fc-codec`; 33.1–33.3 pending — `docs/chat-phase33/`
- **Phase 32** (phone canvas) — ✅ DEVICE-PASSED on `arena/01a083fc-codec`; not merged — `docs/chat-phase32/`
- **Phase 31** (IntelliSense as Packages) — ✅ MERGED via PR #56 — `docs/chat-phase31/`
- **Phase 30** (snippet packs + Emmet + strip capacity) — ✅ MERGED via PR #55 — `docs/chat-phase30/`
- **Phase 29** (VS Code colour / TextMate) — ✅ MERGED via PR #54 — `docs/chat-phase29/`
- **Phase 27** (phone-native autocomplete) — ✅ MERGED via PR #51 — `docs/chat-phase27/`
- **Phase 26** (typing experience 2.0) — ✅ MERGED via PR #50 — `docs/chat-phase26/`
- **Phase 25** (mobile-first editor core) — ✅ CLOSED (25.2 merged via PR #49; 25.3 ❌ cancelled) — `docs/chat-phase25/`
- **Phase 28** (CodeC Keys) — 🚧 28.1 SPIKE BUILT, device round + go/no-go pending — `docs/chat-phase28/`
- **Phase 24** (polish batch E.1–E.9) — ✅ MERGED via PR #47 (E.3 device pass pending BT keyboard; E.5 deferred) — `docs/chat-phase24/`
- **Phase 23** (inline PTY input + run keys) — ✅ MERGED via PR #46 — `docs/chat-phase23/`
- **Phase 22** (editor smoothness + IME-anchored keys) — ✅ MERGED via PR #45 — `docs/chat-phase22/`
- **Phase 21** (LanguageRunProfile; TCC KEPT as default C compiler) — ✅ MERGED via PR #44 — `docs/chat-phase21/`
- **Phase 20** (toolchains in package repo) — ✅ 20.1 COMPLETE & merged via PR #43 — `docs/chat-phase20/`
**Owner starts the next step by saying so in chat (e.g. "Start Phase 25.2" or pasting the bench results).**

**FUTURE-UPDATE MODE (owner, 2026-09-01):** Phases A–E are planned but not
started. The agent **waits for the owner to say "Start Phase X"** — it does
not begin implementation on its own. For bugs between phases, the agent
**listens carefully, finds the underlying code problem, and solves it**.
Every fix or phase follows `rule.md`: verify → evidence → research →
host-testable implementation + tests → docs → commit/push → CI green →
report → STOP at the merge gate. The owner merges to `main` themselves
(or hands the merge command).

**STANDING RULES (law — read before acting):**

- **No PR/merge without the owner's literal command in chat** (owner
  2026-08-26; reinforced by `rule.md` §3). Committing to and pushing the
  session branch is fine; PR creation and any merge wait. Only an explicit
  phrase like "auto-merge when CI is green" changes this — don't infer it.
- **The owner is browser-first.** Every GitHub action has a github.com click
  path (merge a PR, re-run CI, run a workflow, download the APK) — `rule.md`
  §10 is the cheat sheet. The `gh` commands are only the agent's spelling of
  the same buttons.
- **CLEAN-ROOM LAW (2026-08-31):** replicate FEATURES, never COPY code —
  closed-source (Spck): match visible behavior from mockups/public docs only,
  never decompile; GPL/copyleft (Termux): read public specs, re-implement,
  never paste GPL source.
- **RESEARCH WHEN NEEDED (2026-08-31):** phase docs are a starting point;
  research open questions, record "Research notes" with linked sources.
- **Invariants (law):** no `.` on `PATH`; never `build-package.sh -I`; never
  overwrite `cc` or real ELF `bash` with a shim (`cc` is CodeC's own TCC
  frontend — Phase 20.1 strips `bin/cc` from the clang deb to protect it);
  TCC link order with `-o` last (**PERMANENT** — D.4 cancelled 2026-09-03,
  TCC is the DEFAULT C compiler); never official `com.termux` packages/repos; never bundle the
  bootstrap in the APK; repository metadata stays signed (`signed-by=`, no
  `trusted=yes`); clean-room. Full list: `docs/TERMINAL_PLAN.md` §B/§J,
  `docs/chat-phase1/SOLUTIONS.md`, `docs/chat-phase3/REPOSITORY_SIGNING.md`.
- **CI is the only test executor** — the sandbox has no Java runtime;
  `Build APK` runs `:app:assembleDebug` + `:app:testDebugUnitTest` +
  `:app:lintDebug` (via the `gradle-bootstrap` shim — see `rule.md` §5/§10).
  Write host-unit-testable, Android-free logic (pattern:
  `TerminalBuffer`, `AnsiParser`, `GitManager`, `WebPreviewServer`,
  `DeviceApiOps` …).
- **Sandbox limits:** reach `api.github.com` only — no CI logs/releases/
  artifact downloads, no on-device testing; use `gh` (check-runs annotations)
  for CI state. **Never `reset --hard`** (realign with fetch +
  `reset --mixed FETCH_HEAD`). **Never trigger expensive actions** (package
  repo build ~60–100 min, release, destructive device test, force-push)
  without explicit confirmation; check `gh run list` first.
- **Do not redo/re-debug anything marked COMPLETE / ✅** unless the identical
  symptom reappears with regression evidence.

**FACTS THAT MUST NOT REGRESS:**

- **Vector APIs (Compose `ui-graphics` resolved is far newer than the BOM
  suggests):** old string-path `addPath(pathData: String, color=…)` is GONE.
  Use `addPath(pathData: List<PathNode>, …, fill: Brush?, …,
  stroke: Brush?, strokeLineWidth, strokeLineCap, strokeLineJoin, …)`;
  `PathNode` in `androidx.compose.ui.graphics.vector`; `Color` is NOT a
  `Brush` (wrap in `SolidColor`); `DrawScope.drawLine` endpoint is `end`;
  use `navigationBarsPadding()`.
- **Web Preview:** serves the project folder over loopback
  `http://127.0.0.1:<ephemeral>/` (`WebPreviewServer`); `file://` only as
  fallback; preview navigation must carry the **authoritative project**
  (VM `currentProject` / drawer `entry.projectName`), never the Nav route
  argument — the route argument goes stale after in-editor folder switches.
- **Completion sources (Phase 30):** snippet packs are MIT DATA in
  `assets/snippets/` — never hand-edit a pack (re-run
  `scripts/vendor_snippets.py` and keep `SnippetAssets.packsFor` + its `PACKS`
  table in sync; `SnippetLibraryTest` pins every path). Labels are pack
  PREFIXES; matching is case-insensitive (22.6 law); JSON/TEXT/XML/YAML ship
  no pack; the built-in Kotlin tables are a deduped TAIL after the pack and
  the FALLBACK when none loads, so never delete them (four 22.6-accepted
  typings exist only there). A device card must be MEASURED against the real
  engine + real assets before it is handed over — five Phase 30 card strings
  were wrong until they were (JOURNEY §41). `Emmet.kt` is clean-room — do not paste emmetio code.
  Caps: `MAX_ITEMS` 50 / snippets 40 / identifiers 6 / keywords 6, and
  `SuggestionStripModel.MAX_CHIPS` 8 (the thumb law — the strip is not a list).
  A lone candidate stays in KEY mode unless it is an Emmet expansion
  (`detail == Emmet.DETAIL` and no ghost) — 27.2 S1 + its one exception.
  **Accept span (device round, 2026-09-07):** accepting an item replaces
  `CodeCompletionEngine.replaceSpanLength(text, cursor, insert)` = the
  identifier word-run ∪ the longest tail of the caret's line that the insert
  text continues — case-INSENSITIVE for accept (22.6's matching law) and never
  crossing a newline. The ghost must KEEP literal-case alignment
  (`alignedTailLength(..., ignoreCase = false)`): it paints the suffix into the
  buffer, so what it claims has to be byte-true. BOTH accept surfaces use it
  (the VM's chip accept and `CodeCAnalyzer`'s ⌄ panel); an item's own
  `replaceLength` (Emmet) still wins. **Auto-pair:** `SmartTyping.transform`'s
  flag is `suppressAutoPair` — pass `true` ONLY for the editor key strip (it has
  its own `()` cap) and for programmatic caret moves; every TYPING surface
  (system IME, CodeC Keys) pairs, and `{` + Enter splits the pair with the caret
  indented. **Name a flag for the behaviour it gates, never for the surface it
  was invented on** — `isStrip` silently mis-gated the 28.2 keyboard for two
  phases and no test could see it, because the IME path paired correctly.
- **CodeCApi:** ops `battery.status` / `sensor.read` / `tts.speak` /
  `camera.capture` / `intent.send`; marker `NEED_PERMISSION:` and
  `CAPTURING:`; camera output names `^[A-Za-z0-9][A-Za-z0-9._-]*\.(jpg|jpeg|png)$`;
  `BOOTSTRAP_VERSION` is **"27"**.

**ORDER OF WORK:**

1. Verify state (`gh pr list`, `git status`, `gh run list`) before acting —
   including the real `main` tip (locally the clone is shallow; cross-check
   with `api.github.com/repos/pabi277/CodeC/branches/main`).
2. Phases 20.1–27 are MERGED; 28.2 is MERGED (28.3/28.4 remain planned);
   **Phase 29 is MERGED via PR #54; Phase 30 (snippet packs + Emmet + strip
   capacity) is MERGED via PR #55 — device-passed 2026-09-07, so do not
   re-implement or "improve" it without evidence.**
   **Phase 31 is MERGED via PR #56; Phase 32 (phone canvas) is DEVICE-PASSED
   on `arena/01a083fc-codec`** (owner: "Start phase 32" → "All test passed on
   device") — see the head line; **Phase 33 (first-hour UX) has its first item
   done** (the RUN ▶ chooser, owner-requested) — 33.1–33.3 remain PLANNED
   (`docs/chat-phase33/`) and start on `"Start Phase 33"`. Otherwise
   **bug-wait mode**. No self-initiated work.
3. A part is complete only when its exit condition is met and verified (owner
   device transcript for device gates — never claim acceptance without one).
4. Keep `prompt.md`, `docs/JOURNEY.md`, `docs/NEXT_STEPS.md`,
   `docs/TROUBLESHOOTING.md` and `rule.md` updated as gates close — the next
   chat trusts only what is written there and verified in git/CI.

**Before each change, state:** what you are changing, which existing feature it
serves, which invariant (if any) it could affect.
