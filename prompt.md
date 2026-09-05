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

**WHERE THINGS STAND (2026-09-05, Phases 25 ✅ CLOSED, 26 ✅ MERGED via PR #50, 27 ✅ MERGED via PR #51 — `main` tip `92af7fb`; PHASE 28 STARTED (owner: "Start phase 28") — 28.1 spike BUILT on `arena/01a070ae-codec`, device round pending; CI history in `docs/chat-phase28/PART_28_1_SPIKE.md` §4):**

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
  (spike Q1/Q2) and a 5-min human session. **The gate is the owner's device
  round** (runbook `docs/TROUBLESHOOTING.md` §10; recipe `PART_28_1_SPIKE.md`
  §5); go/no-go lands in `docs/EDITOR_MOBILE_RESEARCH.md` §9.1. **28.2–28.4
  must NOT start before a recorded GO.**

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
- **CodeCApi:** ops `battery.status` / `sensor.read` / `tts.speak` /
  `camera.capture` / `intent.send`; marker `NEED_PERMISSION:` and
  `CAPTURING:`; camera output names `^[A-Za-z0-9][A-Za-z0-9._-]*\.(jpg|jpeg|png)$`;
  `BOOTSTRAP_VERSION` is **"27"**.

**ORDER OF WORK:**

1. Verify state (`gh pr list`, `git status`, `gh run list`) before acting —
   including the real `main` tip (locally the clone is shallow; cross-check
   with `api.github.com/repos/pabi277/CodeC/branches/main`).
2. Phases 20.1–24 are MERGED. **Phase 25.1 is COMPLETE and its gate verdict
   is written: C-sora wins → 25.2 chosen, 25.3 cancelled.** 25.2 starts only
   when the owner says "Start Phase 25.2" (it is an L-effort editor-core
   swap: AndroidView host, highlighter adapter, completion provider adapter,
   find/replace via Sora searcher, LGPL checklist — spec:
   `docs/chat-phase25/PART_25_2_SORA_PATH.md`). Phases 26–28 and any other
   phase also start only on the owner's word. Otherwise the agent is in
   **bug-wait mode**: do nothing until the owner reports a bug or names the
   next phase. No self-initiated work.
3. A part is complete only when its exit condition is met and verified (owner
   device transcript for device gates — never claim acceptance without one).
4. Keep `prompt.md`, `docs/JOURNEY.md`, `docs/NEXT_STEPS.md`,
   `docs/TROUBLESHOOTING.md` and `rule.md` updated as gates close — the next
   chat trusts only what is written there and verified in git/CI.

**Before each change, state:** what you are changing, which existing feature it
serves, which invariant (if any) it could affect.
