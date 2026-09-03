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

**WHERE THINGS STAND (2026-09-03, Phase 21 COMPLETE — awaiting owner merge):**

- **Phase 21 (retire TCC, `LanguageRunProfile` registry) is STARTED** (owner:
  "start phase 21", 2026-09-03) on `arena/01a064e0-codec`, base `main` =
  `3fa71ab`. **D.1 + D.2 are IMPLEMENTED; D.3 needs a DEVICE PASS; D.4 (delete
  `assets/tcc/` + `EmbeddedCompiler` TCC path + `scripts/build-tcc.sh`) is
  BLOCKED until the owner confirms D.3.** Four new Android-free files —
  `LanguageRegistry` (12 language profiles + `$SRC`/`$OUT` templating +
  `shellEscape`, moved here from `TerminalHandoff`), `LanguageRunPlanner`
  (sealed `RunDecision`), `LanguageToolProbe` (`$PREFIX/bin/<binary>` exists),
  `InstallPromptState`. `EditorViewModel.runActiveFile` no longer switches on
  `LanguageType`; the install gate streams `pkg install -y <pkg>` into the
  Output Panel (900 s timeout) and auto-continues the run. `TerminalHandoff`
  emits `gcc`/`g++ … -lm` and routes scratch files through the registry.
  +33 host tests. The spec's `useLegacyTcc` flag was intentionally skipped —
  see the record for why.
  **Device round 1 (2026-09-03) FAILED at the install gate and is fixed:**
  D17 — C/C++ `requiredPackage` was `gcc`, but **there is no `gcc` package**
  (apt itself replied "the following packages replace it: libllvm"); Phase
  20.1 publishes **`clang`**, whose deb ships the `gcc`/`g++` driver symlinks,
  so the registry now installs `clang` and probes `gcc`/`g++`. D18 — `golang`
  and `rust` were never published; they are flagged `inRepository = false` and
  RUN ▶ reports that honestly. D19 — the gate now runs
  `pkg update && pkg install -y <pkg>` (a stale catalog made the first attempt
  unrecoverable). Guard tests pin every installable name to what
  `codec-packages/properties.codec.sh` actually publishes.
  **Device round 2 (2026-09-03) also FAILED and is fixed:** D20 — the D.2 gate
  only covered the active-file path; SERVER-type projects (`python-flask`,
  `python-fastapi`, `c-microservice`) and custom `project.json` build/run
  pairs run their command **verbatim**, so `python3 app.py` on a device
  without python died with `command not found` / exit 127 and no prompt at
  all. New `LanguageRunPlanner.toolchainForCommands` gates raw command strings
  by the leading program of each `&&`/`;`/`|` segment; a successful install
  resumes the server via `pendingServerProject`. D21 — the `c-microservice`
  preset still emitted `cc server.c`; now `gcc`.
  **D.3 device acceptance PASSED (owner: "Pass", 2026-09-03).**
  **D.4 (Remove TCC Entirely) is CANCELLED by owner direction** — *"remove the
  option of compiler Setting and make the tcc default but if need user can
  install gcc"*. D.4 was stopped before deleting anything because `cc` is
  CodeC's own TCC frontend (protected by the cc invariant, Phase 20.1
  D5/D15), so deleting the assets would have broken `cc` in the terminal and
  every existing `project.json`. What shipped instead: **D22** — the Settings
  → Compiler Engine picker (Auto/TCC/Bundled/Termux) and the
  `COMPILER_BACKEND` preference are **deleted**, replaced by one read-only
  "Compiler" line; `BACKEND_AUTO` is now the only value any caller passes.
  **D23** — the `.c` profile compiles with the built-in **`cc`** frontend and
  has **no install gate at all** (offline, instant); `.cpp` keeps its clang
  gate because TCC cannot build C++. `-o` is last again on every C line.
  **Phase 21 is COMPLETE and ready for the owner's merge command.**
  Note for future work: CodeC has FIVE run paths — active file, project file,
  project config, server preset, terminal handoff. Record:
  `docs/chat-phase21/PART_21_IMPLEMENTATION.md` §7–§9.
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
- **Phase 21 is COMPLETE (device-accepted; awaiting the owner's merge command).** Otherwise the agent waits
  for the owner to report a bug — listen carefully, find the underlying code
  problem, solve it. No self-initiated work.

**NEW PLANNED PHASES (2026-09-01, owner direction):**
Phases 21/22/23/24 are fully spec'd — no code written yet; **Phase 20.1 is
COMPLETE and merged (20.2 heavy roots behind `[repo-build-heavy]` remains a
design pivot, not started)**.
- **Phase 22** (editor smoothness + IME-anchored keys) — `docs/chat-phase22/`
- **Phase 23** (inline PTY input, remove Output Panel input box) — `docs/chat-phase23/`
- **Phase 20** (gcc/clang/nodejs/etc. in package repo — CI only) — ✅ 20.1 COMPLETE & merged — `docs/chat-phase20/`
- **Phase 21** (`LanguageRunProfile` registry, generic multi-language run; TCC KEPT as the default C compiler) — ✅ COMPLETE (D.1/D.2/D.3 done, D.4 cancelled, D22/D23 shipped) — `docs/chat-phase21/`
- **Phase 24** (polish batch: formatter, notifications, HW shortcuts, ZIP share, tablet, test runner, Open-with, adaptive theme, per-project config) — `docs/chat-phase24/`
Recommended order: 21 (in progress) → 22 → 23 → 24 (21 and 22 can run in parallel).
**Owner starts a phase by saying "Start Phase 20" (or 21/22/23/24) in chat.**

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
2. All spec'd phases are done and merged. The agent is in **bug-wait mode**:
   do nothing until the owner reports a bug, then listen carefully, find the
   code problem, and solve it via `rule.md`'s update lifecycle. No phase
   queue; no self-initiated work.
3. A part is complete only when its exit condition is met and verified (owner
   device transcript for device gates — never claim acceptance without one).
4. Keep `prompt.md`, `docs/JOURNEY.md`, `docs/NEXT_STEPS.md`,
   `docs/TROUBLESHOOTING.md` and `rule.md` updated as gates close — the next
   chat trusts only what is written there and verified in git/CI.

**Before each change, state:** what you are changing, which existing feature it
serves, which invariant (if any) it could affect.
