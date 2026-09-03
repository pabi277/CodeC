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

**WHERE THINGS STAND (2026-09-03, Phase 22 MERGED — Phase 23 IMPLEMENTED, CI pending):**

- **Phase 23 (Interactive Run UX) is 🟡 IMPLEMENTED on `arena/01a06662-codec`
  (owner: "Start Phase 23") — CI pending.** Two client-only parts, both done:
  - **B.1 — inline PTY input:** the Output Panel's separate input row
    (`OutputInputRow`) is **deleted**; an `InlineInputRow` renders as the last
    `LazyColumn` item of the panel whenever `OutputRunState.waitingForInput`
    is set (only while a PTY interactive run is live). `singleLine` +
    `ImeAction.Send` + `KeyboardActions(onSend)` (the same shape the old row
    used) submits via `InteractiveRunSession.sendLine`; the `↵` icon stays.
    `waitingForInput` is set when `InteractiveRunSession.start` returns
    non-null and cleared in every terminal transition (`finishRun`/`failRun`/
    `stopRun`/`finishFailedBuild`/`finishServerExit`); each run starts with a
    fresh `inputBuffer`. Auto-scroll now keys on `(lines.size,
    waitingForInput)` and pins the field while waiting.
  - **B.2 — run keys in the IME strip:** while an interactive run waits for
    input, the Phase 22.2 strip shows `↵ Enter` / `Ctrl+C` / `Tab` / `↑` /
    `↓` instead of the editor keys (editor keys restore when the run ends).
    Strip choice derives from `outputState.waitingForInput` alone
    (`KeysContext.InteractiveRun` vs `KeysContext.Editor(language)`).
    `↵ Enter` → `submitInput()`; `Ctrl+C` → `interruptRun()` →
    `InteractiveRunSession.sendSignal(SIGINT)` → the **existing**
    `PtyNative.kill` (child process group) — **no JNI/native change**; `Tab`
    → `appendInput("\t")`; `↑`/`↓` are placeholders (REPL history later).
  - **New pure code:** `ui/editor/RunKeySet.kt`, `ui/editor/KeysContext.kt`,
    `ui/services/InteractiveInputBuffer.kt`; `EditorKeysRow` refactored to a
    precomputed-keys signature + shared `KeyCap` (`RunKeysRow` added);
    `OutputPanelView` signature `onSendInput` → `onInputChange`/
    `onSubmitInput`. Host tests: `RunKeySetTest` ×8,
    `InteractiveInputBufferTest` ×7.
  - **NEXT ACTION: CI must go green, then the owner runs the two §4 device
    recipes in `docs/chat-phase23/`** (B.1 scanf → "Hello, Alice!"; B.2 run
    keys above the keyboard + Ctrl+C → "Killed"/130 + editor keys restore),
    then merges. Do **not** start Phase 24 before 23 is accepted.

- **Phase 22 (editor smoothness + IME-anchored keys) is ✅ MERGED to `main`
  via PR #45 (2026-09-03; `main` tip `7173494`, post-merge CI
  `33730937920` green).** It was implemented on `arena/01a065a0-codec`
  (head `a39a5d6`, run `33728936911`), went through **five device-feedback
  rounds**, and the owner closed with *"Ok i will no check any more leave as
  it is now"*, then commanded the merge.
  - **⚠️ READ FIRST — the long-file lag was a documented Compose limitation,
    not a CodeC bug.** JetBrains `compose-multiplatform#4023` → `CMP-4023`,
    closed **not planned**: `BasicTextField` is **not lazy** and its layout
    cost is dominated by the **SPAN COUNT**, not the character count. Four
    rounds of plausible-looking fixes (off-thread tokenizing, memoization,
    debouncing) failed because the tokenizer was never the expensive part.
    **If editor lag is ever reported again: measure the span count first.**
    HTML is the worst case (~1 753 spans for 517 lines — every tag name,
    attribute string and number is a token). Full record with sources:
    `docs/chat-phase22/PART_22_1_SMOOTHNESS.md` §12–§13.
  - **Do NOT raise `HighlightedCode.WINDOW` (3 000).** It was 20 000, which
    was *larger than the owner's ~25 000-char file*, so the windowing never
    engaged. A unit test fails if it goes above 5 000.
  - **A 22.1 claim in the older text below is WRONG and has been corrected in
    the docs:** `completionItems` becoming a `derivedStateOf` was a **no-op**,
    not a fix (it reads `codeText` and is read in the same frame, so it
    recomputed every keystroke anyway). It is now `produceState` + 120 ms
    debounce + `Dispatchers.Default`.
  - **A.1 (partial):** new pure `HighlightedCode` (in
    `MultiLanguageSyntaxHighlighter.kt`) + `EditorViewModel.highlighted`
    (`combine` → `distinctUntilChanged` → `debounce(80 ms)` →
    `Dispatchers.Default`); `SyntaxVisualTransformation` gained a fourth
    `cached:` parameter and **falls back to inline tokenizing when the
    snapshot is stale**, so the cache is a pure optimization with no
    correctness role. Plus `EditorDecorations.isEmpty()` fast path,
    `tabViews` lost its `codeText` key, `completionItems` → `derivedStateOf`,
    gutter string `remember(lineCount)`d.
  - **⚠️ A.1's scroll-model rewrite (spec §2.3) is DEFERRED, on purpose:** at
    Compose BOM **2024.09.00** the `scrollState` parameter exists **only on
    the `TextFieldState` overload** of `BasicTextField`; CodeC's
    `TextFieldValue` overload has none. Migrating would rewrite the undo
    manager, auto-indent, tab buffers, find/replace and quick-fixes (all
    speak `TextFieldValue`). Do not "fix" this casually. Baseline profile
    (§2.5) also not done — needs an on-device Macrobenchmark run.
  - **A.2:** the keys row's *content* was already correct; only its
    *position* was wrong. It now renders as the last child of the
    `imePadding()`'d column while the IME is open (flush on the keyboard),
    and in the docked Phase 16 position when it is closed. No
    `AnimatedVisibility` — the inset already animates.
  - **A.3:** `imePadding()` on `EditorScreen`'s root column **only**.
    `enableEdgeToEdge()` and `adjustResize` were already in place;
    `adjustResize` was deliberately left alone.
  - **Tests:** `EditorHighlightCacheTest` ×8.
  - **NEXT ACTION: the owner runs the three §4 device recipes in
    `docs/chat-phase22/`** (typing/fling/pinch smoothness; keys-above-keyboard
    steps 1–9; caret visibility + the **Terminal no-white-strip regression
    check**), then merges. Phase 23 must not start first — 22 and 23 both
    touch the editor/terminal.

**PHASE 21 (COMPLETE & MERGED to `main`):**

- **Phase 21 (`LanguageRunProfile` registry + generic multi-language run) is
  ✅ COMPLETE, DEVICE-ACCEPTED and MERGED to `main`** by owner command
  (2026-09-03, from `arena/01a064e0-codec`; base was `3fa71ab`). Verify the
  new tip with `git ls-remote origin main` — the local clone is shallow.
  **⚠️ The headline is the opposite of what the Phase 21 spec says: TCC was
  NOT removed. It is the DEFAULT C compiler.** Read
  `docs/chat-phase21/PART_21_IMPLEMENTATION.md` §9 before touching anything
  compiler-related; `PART_21_4_REMOVE_TCC.md` is marked ❌ CANCELLED — do not
  execute its checklist.
  - **What shipped (D.1/D.2):** four Android-free, host-testable files —
    `LanguageRegistry` (12 profiles: C, C++, Python, JS, TS, Go, Rust, PHP,
    Ruby, Lua, Shell, HTML; `$SRC`/`$OUT` templating; `shellEscape` moved here
    from `TerminalHandoff`, which now delegates), `LanguageRunPlanner`
    (sealed `RunDecision`: WebPreview | NeedsInstall | Unavailable | Execute |
    Unsupported), `LanguageToolProbe` (`$PREFIX/bin/<binary>` exists — no
    `pkg` query per RUN tap), `InstallPromptState`.
    `EditorViewModel.runActiveFile` no longer switches on `LanguageType`;
    **adding a language is one entry in `LanguageRegistry.profiles`.**
  - **Auto-install gate (D.2):** a missing toolchain prompts
    "Install <language>?" (size hint only for heavy roots), streams
    `pkg update && pkg install -y <pkg>` into the Output Panel via
    `ExecutionRunner` (900 s timeout), then resumes the run automatically on
    exit 0. Server projects resume via `pendingServerProject`.
  - **TCC is the default (D22/D23, owner direction):** the Settings →
    Compiler Engine picker (Auto/TCC/Bundled/Termux) and the
    `COMPILER_BACKEND` preference are **deleted** — one read-only "Compiler"
    line replaces them; `CompilerService` keeps `BACKEND_*` as its internal
    fallback chain with `BACKEND_AUTO` the only value any caller passes.
    A `.c` file compiles with the built-in **`cc`** frontend and has **no
    install gate at all** (offline, instant, no download). `.cpp` keeps its
    `clang` gate because TCC cannot build C++.
  - **Three device rounds** (all owner-found, all fixed): D17 — there is **no
    `gcc` package**; Phase 20.1 publishes **`clang`**, whose deb ships the
    `gcc`/`g++` driver symlinks (apt itself said "the following packages
    replace it: libllvm"). D18 — `golang`/`rust` were never published →
    `inRepository = false` → an honest "not in the repository yet" instead of
    a doomed install. D19 — the gate must `pkg update` first. D20 — the gate
    only covered the active-file path; **server presets and custom
    `project.json` build/run pairs execute verbatim** → new
    `toolchainForCommands` gates raw command strings by the leading program of
    each `&&`/`;`/`|` segment. D21 — the `c-microservice` preset's compiler.
  - **⚠️ CodeC has FIVE run paths**, not one — active file, project file,
    project config, server preset, terminal handoff. The Phase 21 spec
    described only the first, and that assumption caused both gate bugs. Check
    all five for any future run-related change.
  - CI green: `33704697218`, `33706730106`, `33710216905`, `33712841947`.
    Record: `docs/chat-phase21/PART_21_IMPLEMENTATION.md` §1–§9.
- **Post-merge device sanity check the owner may still want:** a `.c` file
  should run with **no** install prompt, and Settings should show a single
  read-only "Compiler" line (no engine dropdown).
- **Phase 20.1 background (COMPLETE & merged):**

- **`main` includes Phase 20.1** — the toolchain round merged from
  `arena/01a05cb9-codec` by owner command (2026-09-03; before that `main` =
  `9b3669e` Website W0 docs via PR #41, `54ae06a` Phases 20–24 design docs via
  PR #40, PR #39 git fixes, PR #38 Phase 18 — verify the tip with
  `git ls-remote origin main` / the GitHub API (the local clone is shallow).
- **Phase 20.1 (package toolchain round 4) is ✅ COMPLETE, DEVICE-VERIFIED
  6/6 and MERGED to `main` (2026-09-03, `arena/01a05cb9-codec`)** (owner's
  "Phase 20 start", 2026-09-01): six new
  `CODEC_REPOSITORY_PACKAGES` roots — `libllvm` (clang 21.1.8 + the
  `gcc`/`g++` driver symlinks; no `gcc`/`clang` recipe exists at the pinned
  ref), `nodejs`, `npm` (split from nodejs upstream), `php` (trimmed of
  apache/ldap/pgsql/gd), `ruby`, `lua54` (plain symlinks instead of the
  allowlist-blocked alternatives postinst). Five new fail-loud
  `apply-recipe-overrides.sh` blocks incl. the **`bin/cc` strip (cc
  invariant)**; +8 hermetic tests, repo suite 93 green. Two dispatches died at the
  360-min job ceiling (`33506104710`, `33547475854`) → D10 backend trim +
  **D11 split: the build job now fans out into base/llvm/langs parallel
  legs**; third dispatch `33585242675` proved the split (base green) but
  failed both llvm legs at validation: libcompiler-rt's upstream
  subpkg-level postinst/prerm (ndk-multilib interop) → D12 no-op append.
  Fourth dispatch `33598824226`: base+llvm all green; langs legs red —
  php-gd's excluded-subpackage deps still entered the arch-neutral
  buildorder closure (gdk-pixbuf validator-trip / dead x264 URL) → D13:
  neuter phantom subpackages IN PLACE (strip dep edges + arch-skip + no-op
  scripts — outright deletion orphaned phpmyadmin's graph edge in
  `33625141182`). Owner directed SALVAGE (D14): rebuild only the langs legs
  and merge the 4 green legs of `33598824226` via new workflow inputs
  `groups=langs` + `reuse_run_id=33598824226` (per-arch nano/clang/nodejs
  marker gate before signing). Salvage dispatch `33639310638`: langs green,
  publish blocked by the github-pages env branch allowlist → owner added
  `arena/01a05cb9-codec` → rerun-failed → **DEV REPO PUBLISHED** (all 14
  names verified live; lldb/mlir/libpolly absent). §4 device run: 5/6 OK,
  two content bugs found — cc clobbered (unclaimed symlink swept into
  main libllvm deb → D15 drops cc from the loop) + no `lua` (post_massage
  wrote to staging, not MASSAGEDIR → D16); salvage now downloads
  complement legs only. **End state: salvage round 2 `33669069048` GREEN
  (~3h04m, complement-only reuse proven), live repo carries the fixes
  (libllvm `abe38f14…`, lua54 `01cf611c…`); bootstrap release `33669089783`
  refreshed `userland-v2-dev` (aarch64 `33b2718b…`, x86_64 `bd669950…`);
  device re-verify 6/6 — `lua -v` → Lua 5.4.8 (5.4 has no `--version`
  flag), `cc` gone from the libllvm deb → after one app restart
  `command -v cc` → tcc 0.9.27, `gcc $HOME/t.c` → `Hello gcc`.**
  Record: `docs/chat-phase20/`.
  Phases 3–14, 19 (PR #34 @ `b869ce6`), Phases 15/16 (PR #36 @ `a0e7dc3`),
  Phase 17 (PR #37 @ `f868e10`) and **Phase 18 (PR #38)** are all
  **COMPLETE, DEVICE-ACCEPTED (where gated) and MERGED**.
- **Phase 18 (CodeCApi Device Capabilities) is COMPLETE, DEVICE-ACCEPTED
  (2026-09-01) and MERGED via PR #38.** Session branch
  `arena/01a05b12-codec` @ `ffca133`: feature `012deea`, lint fix
  `4460306` (CAMERA `uses-feature required=false`), docs `6c67202`, the
  2026-09-01 Web Preview fix `d49ac47` (see below), then `ffca133`
  (rule.md + living docs). CI green:
  `33468442063` (feature), `33468793012` (docs), `33471103959` (fix),
  `33472175072` (docs).
  Five CLI scripts + wire ops on the OSC 1337 CodeCApi bridge:
  `codec-battery` (sticky `ACTION_BATTERY_CHANGED` → JSON), `codec-sensor`
  (accelerometer/gyroscope/light), `codec-tts` (app-lifetime TextToSpeech,
  QUEUE_FLUSH, 32 KiB cap), `codec-camera` (runtime CAMERA park/resume +
  `TakePicture` via FileProvider → `$PREFIX/tmp/codec-api/camera/`,
  `OK:<path>`/`ERR`), `codec-intent` (implicit view/dial/send only +
  URI-scheme allow-list). `BOOTSTRAP_VERSION` 26 → 27; manifest CAMERA +
  TTS/IMAGE_CAPTURE queries. Pure core behind android-free `DeviceApiOps`:
  `CodecApiBridgeFullTest` ×22 + additions. Record:
  `docs/chat-phase18/PART_18_CODEAPI.md` §5 (D1–D9) + §5.6 (device
  transcript — battery/sensor/TTS/camera/intent all PASSED).
  **Phase 18 is CLOSED; do not revisit unless the identical symptom reappears
  with regression evidence.**
- **2026-09-01 Web Preview fix (`d49ac47`):** imported HTML opened in the
  editor showed `File not found: <name>` after the in-editor folder switch
  (Phase 9.2) or Save to project… — the preview resolved files with the Nav
  route's stale project. Fixed by threading the authoritative project
  (`currentProject` / `entry.projectName`) through `onOpenPreview`,
  `onOpenPreviewUrl` and the server/auto-web handlers. Record:
  `docs/chat-phase9/PART_9_IMPLEMENTATION.md` (Phase 9.2 follow-up). Owner
  may want to re-verify the imported-HTML flow on device.
- **Phase 18 was merged to `main` via PR #38 (2026-09-01)** — the standing
  rule still applies to everything new: the agent stops at CI green + docs and
  the owner merges to `main` (or hands the merge command).
- **Phase 22 is MERGED to `main` (PR #45).** **Phase 23 is IMPLEMENTED on
  `arena/01a06662-codec` (CI pending; device recipes not yet run). Phase 24
  remains PLANNED and fully spec'd — the agent does NOT start it until the
  owner says "Start Phase 24", and 23 must be accepted first because both
  touch the editor/terminal.** Between phases the agent waits for the owner to
  report a bug — listen carefully, find the underlying code problem, solve it.
  No self-initiated work.

**PHASE STATUS (updated 2026-09-03):**
Phase 22 is ✅ MERGED to `main` (PR #45, tip `7173494`). **Phase 23 is 🟡
IMPLEMENTED on `arena/01a06662-codec` (owner: "Start Phase 23") — CI pending;
device recipes not yet run.** Phase 24 is fully spec'd, no code written yet. **Phase 20.1 and
Phase 21 are COMPLETE and merged** (20.2 heavy roots behind
`[repo-build-heavy]` remains a design pivot, not started).
- **Phase 22** (editor smoothness + IME-anchored keys) — ✅ MERGED via PR #45 (2026-09-03) — `docs/chat-phase22/`
- **Phase 23** (inline PTY input + run keys; remove Output Panel input box) — 🟡 IMPLEMENTED, CI pending — `docs/chat-phase23/`
- **Phase 20** (gcc/clang/nodejs/etc. in package repo — CI only) — ✅ 20.1 COMPLETE & merged — `docs/chat-phase20/`
- **Phase 21** (`LanguageRunProfile` registry, generic multi-language run; TCC KEPT as the default C compiler) — ✅ COMPLETE (D.1/D.2/D.3 done, D.4 cancelled, D22/D23 shipped) — `docs/chat-phase21/`
- **Phase 24** (polish batch: formatter, notifications, HW shortcuts, ZIP share, tablet, test runner, Open-with, adaptive theme, per-project config) — `docs/chat-phase24/`
Recommended order for what remains: **23 → 24** (both touch the editor/terminal — do not run them in parallel).
**Owner starts a phase by saying "Start Phase 24" in chat.**

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
- **CodeCApi:** ops `battery.status` / `sensor.read` / `tts.speak` /
  `camera.capture` / `intent.send`; marker `NEED_PERMISSION:` and
  `CAPTURING:`; camera output names `^[A-Za-z0-9][A-Za-z0-9._-]*\.(jpg|jpeg|png)$`;
  `BOOTSTRAP_VERSION` is **"27"**.

**ORDER OF WORK:**

1. Verify state (`gh pr list`, `git status`, `gh run list`) before acting —
   including the real `main` tip (locally the clone is shallow; cross-check
   with `api.github.com/repos/pabi277/CodeC/branches/main`).
2. Phase 22 is **merged to `main` (PR #45)**. Phase 23 is implemented on
   `arena/01a06662-codec` with **CI pending** — push it, wait for green, then
   hand the owner the two `docs/chat-phase23/` §4 device recipes (do not
   claim device acceptance without the owner's transcript). Phase 24 must not
   start until 23 is accepted. Otherwise the agent is in **bug-wait mode**: do
   nothing until the owner reports a bug or says "Start Phase 24". No
   self-initiated work.
3. A part is complete only when its exit condition is met and verified (owner
   device transcript for device gates — never claim acceptance without one).
4. Keep `prompt.md`, `docs/JOURNEY.md`, `docs/NEXT_STEPS.md`,
   `docs/TROUBLESHOOTING.md` and `rule.md` updated as gates close — the next
   chat trusts only what is written there and verified in git/CI.

**Before each change, state:** what you are changing, which existing feature it
serves, which invariant (if any) it could affect.
