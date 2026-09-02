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

**WHERE THINGS STAND (2026-09-01, Phase 20.1 in progress):**

- **`main` = `54ae06a` — Phases 20–24 design docs via PR #40** (merged
  2026-09-01); the chain is `54ae06a` ← **PR #39** (git branch-publishing +
  clear-error fixes, `arena/01a05b6c-codec`) ← `dc68eee` = PR #38 (Phase 18),
  `f868e10` = PR #37 (Phase 17), `a0e7dc3` = PR #36 (Phases 15/16),
  `b869ce6` = PR #34 (Phase 19) — verify the tip with
  `git ls-remote origin main` / the GitHub API (the local clone is shallow).
- **Phase 20.1 (package toolchain round 4) is 🚧 IMPLEMENTED on
  `arena/01a05cb9-codec`** (owner's "Phase 20 start", 2026-09-01): six new
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
  names verified live; lldb/mlir/libpolly absent). Next: bootstrap-release
  at source_run_id=33598824226 (owner), §4 device recipe, owner merge.
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
- **ALL PHASES COMPLETE.** No spec'd implementation remains. The agent waits
  for the owner to report a bug — listen carefully, find the underlying code
  problem, solve it. No self-initiated work.

**NEW PLANNED PHASES (2026-09-01, owner direction):**
Phases 21/22/23/24 are fully spec'd — no code written yet; **Phase 20 started
(20.1 implemented, awaiting `[repo-build]` dispatch)**.
- **Phase 22** (editor smoothness + IME-anchored keys) — `docs/chat-phase22/`
- **Phase 23** (inline PTY input, remove Output Panel input box) — `docs/chat-phase23/`
- **Phase 20** (gcc/clang/nodejs/etc. in package repo — CI only) — 🚧 20.1 in progress — `docs/chat-phase20/`
- **Phase 21** (retire TCC, `LanguageRunProfile` registry, generic multi-language run) — `docs/chat-phase21/`
- **Phase 24** (polish batch: formatter, notifications, HW shortcuts, ZIP share, tablet, test runner, Open-with, adaptive theme, per-project config) — `docs/chat-phase24/`
Recommended order: 20 → 21 → 22 → 23 → 24 (C and A can run in parallel).
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
  overwrite `cc` or real ELF `bash` with a shim; TCC link order with `-o`
  last; never official `com.termux` packages/repos; never bundle the
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
