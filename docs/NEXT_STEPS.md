# CodeC — historical Phase 3 task list and current handoff

**Last updated:** 2026-09-05 · **Current head: Phase 28.1 CodeC Keys IME-free spike BUILT on `arena/01a070ae-codec` (owner: "Start phase 28") — :bench-only harness (K1 = Compose document core, K2 = shipping sora core, both fed ONLY by an IME-free 3-row code grid through the production `EditorKeySet` model); pure metrics host-tested (DOWN→commit latency ledger, strict-subsequence tap audit, ime-inset flicker probe w/ self-check toggle), scenarios for the budget table + spike Q1 (synthesized HW keys) Q2 (stdin-row route) Q3 + 5-min human session; CI history + merge gate below. **DEVICE ROUND 1 RECORDED (2026-09-05): K2/sora meets every budget (commit p95 ≤3.4 ms, audits exact, IME 0 px throughout); K1's frame red = the Compose core itself (25.1 C-now disease), the grid path was clean there too; S2 is the input path of record. OWNER VERDICT: **GO** (owner: "Go", 2026-09-05 — §6; the four human items waive as blockers and ride 28.2's device round). **28.1 CLOSED.** 28.2 (layout engine) **BUILT 2026-09-05 + CI green** (`:app/ui/keyboard/*`, Settings default-OFF, S2 wiring, 20 host tests) — owner rounds 1–2 run + fixed same day (previews, no macro row, arrow flicks; LIVE-commit arrow fix, space trackpad, **DEFAULT ON** per owner) — **retest card `TROUBLESHOOTING.md` §11**; a PASS opens 28.3. **No PR/merge until the owner's explicit command.**


**Previous head (Phase 27, MERGED via PR #51 2026-09-05, superseded):** Phase 27 Phone-native Autocomplete merged to `main` (tip `92af7fb`, post-merge CI `33955091994` green) — All three parts, client-only: **27.1** inline ghost text on the sora core (pure `GhostCompletion` FULL/WORD/LINE accept + instant shrink; rendered as a point-anchored inlay hint via new `GhostHintRenderer`, comment@38% per G5; TAB ▸ cap / →▸ word cap / caret-row "Tab ▸" pill / tap-ghost / HW Tab & Ctrl+→; scroll+composing clear it — G4/G7); **27.2** suggestion strip (sealed `StripContext` Hidden|Run|Keys|Suggestions with Run always winning — 23.2 re-pinned; ≥2 candidates → ƒ/λ/≠ chips with pinned ⌨ and ⌄ more caps, tap-accept, long-press tooltip, swipe-down dismiss-per-identifier; `CodeCCompletionComponent` gates sora's native panel to "⌄ more" browse sessions only); **27.3** the law in pure `CompletionPolicy` (surfaceFor + decide; Enter sacred, "TAB ▸" tells the truth, dismissal per-identifier, master off = zero chrome/computation) + Settings → Editor Settings rows. New VM two-leg pipeline (`completionModel: StateFlow<CompletionModel>`): instant `startsWith` narrowing per keystroke + 120/240 ms debounced engine recompute off-main. Host tests: `GhostCompletionTest`, `StripContextTest`, `CompletionPolicyTest` (full matrix). JOURNEY §37. **No PR/merge until the owner's explicit command.**


**Previous head (Phase 26, merged via PR #50, superseded):** Phase 26 Typing Experience 2.0 MERGED to `main` (`c1b4321`, post-merge CI `33941444393` green) — key strip 2.0 popups/swipes/hold-repeat + JSON user sets, smart typing (type-over/wrap/empty-pair/auto-indent/delete-word, string-aware), IME first-run guide. Device §3 recipes still pending owner (BT keyboard for E.3-style passes). JOURNEY §36.

**Previous head (Phase 25.2, device-accepted via PR #49, superseded):** Phase 25.2 (sora-editor 0.24.6) ✅ COMPLETE & DEVICE-ACCEPTED on `arena/01a06b20-codec` (tip `c54228d`, run `33866749797` → `33860045301` green; +0.55 MiB APK delta; four device rounds — crash ping-pong → constructor NPE → drawer-gesture & sora-native completion → "All passed"); LGPL-2.1 "Yes" + "Merge" → PR #49 squash-merged; Phase 25 CLOSED; bench wrapper removed. See `docs/JOURNEY.md` §35.

**Previous head (Phase 24, merged via PR #47, superseded):** Phase 24 (desktop-class editor quality) on `arena/01a06784-codec` — eight of nine parts (E.1 formatter, E.2 foreground notification, E.3 hardware shortcuts, E.4 ZIP share, E.6 test-runner, E.7 open-with, E.8 adaptive theme, E.9 `.codec.json`) implemented (see `docs/JOURNEY.md` §33); **E.5 tablet two-pane deferred** pending owner confirmation (requires extracting the `EditorScreen` body from the `ModalNavigationDrawer`). **CI green `33768581748`**. **Device round 1 (2026-09-04): E.1 ✅ / E.2 ✅ / E.4 ✅ / E.6 ✅ / E.7 ✅ / E.8 ✅ / E.9 ✅ on-device; E.3 ⏳ not possible this round (needs Bluetooth keyboard/tablet); E.5 ⏸ deferred.**

**State (head):** **Phase 22 ✅ MERGED to `main` via PR #45 (2026-09-03; `main` tip `7173494`, post-merge CI `33730937920` green). Phase 23 (Interactive Run UX) ✅ COMPLETE & DEVICE-ACCEPTED, and has been merged to `main` via PR #46 (owner's merge command);** **Phase 23 (Interactive Run UX) ✅ COMPLETE & DEVICE-ACCEPTED on `arena/01a06662-codec` (`Build APK` `33735687876`; owner: "Start Phase 23" → "Phone test passed") — both §4 device recipes passed on device.** Two client-only parts: **B.1** inline PTY input (the separate `OutputInputRow` is deleted; an `InlineInputRow` renders as the last Output-Panel `LazyColumn` item whenever `OutputRunState.waitingForInput` is set, and Enter/`↵` sends the line to `InteractiveRunSession.sendLine`) and **B.2** context-aware run keys (while an interactive run waits for input the Phase 22.2 IME strip shows `↵ Enter` / `Ctrl+C` / `Tab` / `↑` / `↓` instead of the editor keys — `Ctrl+C` → `interruptRun()` → `InteractiveRunSession.sendSignal(SIGINT)` → the existing `PtyNative.kill`, so **no JNI/native change**; editor keys restore the moment the run ends). New pure code: `ui/editor/RunKeySet.kt`, `ui/editor/KeysContext.kt`, `ui/services/InteractiveInputBuffer.kt`; `EditorKeysRow` refactored to a precomputed-keys signature + shared `KeyCap`; `OutputPanelView` signature `onSendInput` → `onInputChange`/`onSubmitInput`. Host tests: `RunKeySetTest` ×8, `InteractiveInputBufferTest` ×7. One for-cause red CI round (`33735482625`, missing `kotlinx.coroutines.flow.update` import) fixed before the green run. Device recipes in `docs/chat-phase23/` (§4 of each part) — **both passed on device 2026-09-03 (owner: "Phone test passed")**. **Owner action: merge when ready.** Next after that: Phase 24. Six implementation rounds, five driven by owner device feedback. **A.1 (smoothness):** the long-file lag was ultimately a documented Compose limitation — [`compose-multiplatform#4023`](https://github.com/JetBrains/compose-multiplatform/issues/4023) → `CMP-4023`, closed WONTFIX: `BasicTextField` is **not lazy** and its layout cost scales with the **span count**, not the character count. Syntax spans are now windowed to ±3 000 chars around the caret (a 517-line HTML file: 1 753 spans → ~409), the tokenizer scans from a blank-line safe anchor instead of offset 0 (bounding the main-thread fallback), and the `filter()` memo is keyed on `(text, caret)`. Earlier rounds removed real per-keystroke O(file) work: the tab-buffer stash on every keystroke, a full-prefix copy in the caret readout, and the completion scan (now `produceState` + 120 ms debounce + `Dispatchers.Default`, windowed identifier scan). **A 22.1 claim was corrected in the docs:** the original `derivedStateOf` for completions was a no-op, not a fix. **A.2 (IME keys):** row rides flush above the keyboard; new `EditorKey.Pair` gives single-cap `()`/`{}`/`[]`/`<>`/`""`/`''`; HTML/CSS and Markdown gained full snippet sets (they previously returned **no** completions at all) and snippet matching is now case-insensitive. **A.3 (insets):** `imePadding()` on the editor root; `adjustResize` **kept**; Terminal/Settings untouched. **Deferred, needs owner go-ahead:** the `TextFieldValue` → `TextFieldState` / `bigtext`-style rewrite — the only way past the `BasicTextField` layout ceiling, and its own phase. User-editable key sets remain a stretch goal.
**Previous head (Phase 22, superseded):**
**Previous head (Phase 21):** **Phase 21 ✅ COMPLETE, DEVICE-ACCEPTED & MERGED to `main` by owner command (2026-09-03, from `arena/01a064e0-codec`).** The compiler engine redesign — but **not** the one the spec described: **TCC was NOT removed, it is the DEFAULT C compiler.** `EditorViewModel.runActiveFile` no longer switches on `LanguageType`; a new Android-free `LanguageRegistry` (12 profiles) + `LanguageRunPlanner` (sealed `RunDecision`) + `LanguageToolProbe` make adding a language one list entry. A missing toolchain prompts "Install X?" and streams `pkg update && pkg install -y <pkg>` into the Output Panel, resuming the run on success. **A `.c` file has no install gate at all** (built-in `cc`, offline, instant); `.cpp` installs `clang` because TCC cannot build C++. The Settings → Compiler Engine picker and the `COMPILER_BACKEND` preference are **deleted**. Three owner device rounds found and fixed five gate bugs (D17 there is no `gcc` package — Phase 20.1 publishes `clang`; D18 golang/rust never published; D19 stale apt catalog; D20 server presets and custom `project.json` commands bypassed the gate entirely; D21 the `c-microservice` preset). **⚠️ CodeC has FIVE run paths** — active file, project file, project config, server preset, terminal handoff — and the Phase 21 spec described only the first. `docs/chat-phase21/PART_21_4_REMOVE_TCC.md` is ❌ CANCELLED; the TCC `-o`-last invariant is PERMANENT. CI: `33704697218`, `33706730106`, `33710216905`, `33712841947`. Record: `docs/chat-phase21/PART_21_IMPLEMENTATION.md`. **Next: Phases 22/23/24 remain PLANNED and fully spec'd — owner starts one by saying "Start Phase 22" (or 23/24) in chat.** Recommended order: 22 → 23 → 24.
**Previous head (Phase 20.1):** round-4 toolchain roots (`libllvm`+clang/gcc symlinks, nodejs, npm, php trimmed, ruby, lua54) published to the dev channel and live: final salvage run `33669069048` green (~3h04m), device-verified 6/6; bootstrap release `userland-v2-dev` refreshed via `33669089783`. Full record: `docs/chat-phase20/PART_20_1_TOOLCHAINS.md`.

**✅ MERGED 2026-09-01 via PR #39 (`arena/01a05b6c-codec`):** the two owner-reported git bugs, fixed & CI-green:
- **New branch / never-pushed commit** — publish NEW branches on creation, push by explicit branch name, and a Projects-card amber `↑` badge for branches not on the remote yet. Record: [`chat-phase15/PART_17_SOURCE_CONTROL.md`](chat-phase15/PART_17_SOURCE_CONTROL.md) §6.3. CI green `33476150534`.
- **Clear error messages** — new Android-free `GitErrors` classifier turns raw git failures into actionable sentences ("git not installed", "no GitHub token", "offline", "push rejected — PULL first", …) and adds a tappable "Create a GitHub token ↗" link (GitHub's fine-grained PAT page) to the Source Control sheet and Settings → GitHub Account. Record: [`chat-phase15/PART_17_SOURCE_CONTROL.md`](chat-phase15/PART_17_SOURCE_CONTROL.md) §6.4. CI green `33479410194`.
**Previous state line:** **Phases 15/16 (Spck Projects Hub + Editor Shell) IMPLEMENTED, mockup-exact re-skin + 3 device rounds, MERGED to `main` (2026-08-31, from `arena/01a057e0-codec` — device round 3: no-Home 5-tab bar with Terminal dead-center + Packages restored, launch-into-last-editor-file, editor autosave, build-output git exclusion incl. untracking, RUN▶=HTML preview).** **Phase 17 IMPLEMENTED and MERGED to `main` (2026-08-31, from `arena/01a05878-codec`)** — Switch Branch (stash/auto-restore + New branch), merge-conflict grouping, Mark Resolved, commit blocking (+38 host tests); **device round 1 done**: upstream publishing on first push + honest "NOT pushed" state with a PUSH retry after the owner's "no upstream branch" / "it stay updated but never go to github" report. **Phase 18 (CodeCApi Device Capabilities) ✅ COMPLETE & DEVICE-ACCEPTED (2026-09-01, `arena/01a05b12-codec` `4460306`, run `33468442063`, owner §4 recipe PASSED — battery/sensor/TTS/camera/intent CLI + bridge + runtime-CAMERA flow; see `docs/chat-phase18/` §5.6). Phases 3–14 + 19 all complete/merged below; there is no remaining spec'd implementation work — only owner device feedback on Phases 15–17/18.**
**Previous state line:** Phases 3–7 complete; **Phase 8 complete and fully accepted** (PR #27 merged at `348eb03`); **Phase 9 + 9.1/9.2 complete and accepted — PR #28 MERGED to `main` at `961e942`** (2026-08-29). **Phase 11 (Output Panel & Integrated Run) ✅ COMPLETE & DEVICE-ACCEPTED — PR #29 MERGED to `main` at `771f58f`** (2026-08-30 — CI green `33293358085`; owner: "All of the check passed"). **Phase 12 (Multi-Language Support, Python & Code Intelligence) ✅ COMPLETE, DEVICE-ACCEPTED & MERGED — PR #30 merged to `main` at `260d8b6`** (2026-08-30; device recipe FULLY PASSED — owner: "Now python is solved" / "Worked properly" / "Both working"; `[repo-build]` published python 3.14.6-1 + python-pip 26.2.1 — see [`chat-phase12/`](chat-phase12/)). **Phase 13 (GitHub & Git Integration) ✅ COMPLETE & DEVICE-ACCEPTED & MERGED — PR #31 merged to `main` at `006515a`** (2026-08-31 — `Build APK` `33326161083` green with 37 new host tests; full §7 device recipe passed; see [`chat-phase13/`](chat-phase13/)). **Phase 14 (Mixed-Language, Server WebViews & Long-Tail Ecosystem) 🚧 IMPLEMENTED & CI-GREEN on `arena/01a05421-codec`** (2026-08-31 — `Build APK` `33352164172` green; host tests incl. `ServerPortDetectorTest`/`ServerRunnerTest`/`ProjectScaffoldTest`/`ProjectConfigTest`; no `[repo-build]`; device recipe pending — see [`chat-phase14/`](chat-phase14/)). Device recipes in [`chat-phase9/PART_9_IMPLEMENTATION.md`](chat-phase9/PART_9_IMPLEMENTATION.md)
remain the regression checklist. Current Phase 8 records live in [`chat-phase8/`](chat-phase8/).

> This file is retained as the historical Phase 3 task list. For current work, use [`TERMINAL_PLAN.md`](TERMINAL_PLAN.md), [`chat-phase8/PART_8_DESIGN_DECISIONS.md`](chat-phase8/PART_8_DESIGN_DECISIONS.md), and [`chat-phase9/PART_9_EDITOR.md`](chat-phase9/PART_9_EDITOR.md).

**Historical state:** Parts A, B, C, and D ✅ ALL
device-verified (Phase 3 complete). **Phase 4 (Parts 4.1–4.8) ✅ COMPLETE,
device-verified 2026-08-26.** Historical Phase 5 text follows; Phases 8–13 are
closed (**PR #27/#28/#29/#30/#31 merged**) — see [`chat-phase13/`](chat-phase13/).
Phase 14 (server WebViews) is implemented & CI-green on the session branch — see
[`chat-phase14/`](chat-phase14/).

> **🔒 STANDING RULE (owner, 2026-08-26): do NOT open a PR or merge anything
> without an explicit command from the owner in chat.** Committing to and
> pushing the session branch (`arena/*`) is fine; PR creation and any merge
> wait for the owner's explicit instruction.

The narrative is in [`docs/JOURNEY.md`](JOURNEY.md). This file is the
Phase 3 **task list**, kept for its history: Parts A–D, all now done, split
into self-contained parts so each could be picked up independently. Phase 4's
task list lives in [`chat-phase4/PHASE4_ROADMAP.md`](chat-phase4/PHASE4_ROADMAP.md)
instead of here — it is **complete** (Parts 4.1–4.8, all DONE). The next
phase's planning-only skeleton is [`PHASE5_ROADMAP.md`](PHASE5_ROADMAP.md).

Each part states its goal, its exit condition, and the exact steps. "Done"
means the exit condition is verified, not just the code written.

---

## How to use this file (verification-first — read before acting)

1. **Start from `prompt.md`** (repo root). Paste it as the first message of a
   new chat. It encodes the self-distrust protocol and the order of work below.
2. **Verify state before acting.** Nothing in this file is true until confirmed
   against the repo: `git status`, `git ls-remote origin`, `gh pr list`,
   `gh run list`. CI state and published releases drift — check them.
3. **Evidence before hypothesis.** If something fails, capture the actual
   output (device Term, CI log, file contents) before diagnosing. Do not commit
   a fix on a guess.
4. **Do not redo completed work.** Anything marked COMPLETE / ✅ in
   [`JOURNEY.md`](JOURNEY.md) and the handoffs is done. Only revisit it if the
   same symptom reappears AND you have evidence it is a regression.

## Guardrails — do not (violating any of these is a bug, not a shortcut)

- Do **not** add `.` to `PATH`.
- Do **not** use `build-package.sh -I` (installs official `com.termux` debs).
- Do **not** use official Termux repositories or `com.termux` binaries.
- Do **not** overwrite `cc` or replace real ELF `bash` with a shim.
- Do **not** change the TCC link order or move `-o` from last place.
- Do **not** bundle the bootstrap in the APK.
- Do **not** start the ~100-minute package build without an explicit user
  confirmation — and check `gh run list` for an existing run first.
- Do **not** open a second PR in parallel; work from the current branch state.

## Definition of done (applies to every part)

A part is **done** only when:

1. its **Exit condition** is met on the right target (device/CI/repo), and
2. no **invariant** above was violated, and
3. the change is committed and pushed with a message naming the part.

---

## Part A — Republish a clean bootstrap — ✅ DONE (device-verified)

**Status: COMPLETE (2026-08-22) — and it shipped _without_ the ~104-minute
rebuild.** The published `userland-v2-dev` assets were repaired **in place**
by the owner in Termux (Path 2 of
[`chat-phase3/PART_A_ARTIFACT_REPAIR.md`](chat-phase3/PART_A_ARTIFACT_REPAIR.md), which patches exactly
one line of the seeded `var/lib/dpkg/status`), then triple-verified: the
script's internal proofs, the GitHub asset-digest API (aarch64
`074806ad9066d4642d4779a28abf7aeb442c76ae9cb115b12b796eac9a9643b1`, x86_64
`9f93edd06129b04cb4652dae7353b41998aee6d0e04e3d9f59bf3a4e426835ce`), and a
clean-device test — full uninstall → fresh APK → Install userland →
`grep clang` on the status DB finds nothing, full nano install cycle clean.
The exit condition below is **met**; the steps below are kept for history
only — do not redo this part.

**Why.** The published `userland-v2-dev` bootstrap predates the `dpkg-perl`
clang fix, so a *fresh* device still hits `E: dpkg-perl : Depends: clang but it
is not installable` until `pkg` self-heals it (which only happens after the
user runs a `pkg` command that calls `require_backend`). The self-heal makes
this non-blocking, but a clean bootstrap is the correct permanent state.

**Exit condition.** A fresh install of the APK → "Install userland" →
`pkg install nano` works with **zero manual fixes**, because the bootstrap's
status DB no longer references `clang`.

**Steps.**
1. Confirm the current branch is merged to `main` (the recipe fix lives in
   `apply-recipe-overrides.sh`, already committed here).
2. From Termux: `gh workflow run "CodeC package repository" --ref main`.
3. `gh run watch <RUN_ID>` (~1h14m).
4. `gh workflow run "Publish CodeC bootstrap release" --ref main
   -f source_run_id=<RUN_ID> -f release_tag=userland-v2-dev`.
5. On a clean device: install APK → Install userland → `pkg install nano`.

---

## Part B — Fix bootstrap correctness (seed the right thing) — ✅ DONE

**Status: COMPLETE and device-verified (2026-08-23).** PR #13 merged at
`35c350f338be34303296b0168933622991258142`; package dispatch #5
(`32620704350`) and publish run `32625580655` succeeded. The published
aarch64 archive is **23,926,127 bytes** with GitHub asset digest
`sha256:863f18528afa126d19481f7308a3f9b23997fda9ad9cae3bc7033d8fa60e60cd`.
A fresh-device run passed curl/TLS, no-clang/no-build-pollution/no-keyring,
alternatives, `dpkg --audit`, nano 9.2 lifecycle, and embedded-compiler checks.
The exit condition below is met. **Do not rerun the expensive build or re-test
Part B unless Part C first records a genuine new defect, and never dispatch a
build without explicit approval.** The remaining material in this section is
historical context.

**Earlier status (2026-08-22, end of day): code COMPLETE, merged to `main`
(PR #11), 49/49 host tests green.** `plan-bootstrap.py` (closure walk mirroring pinned
upstream `pull_package` semantics, fail-loud on unresolved deps) + reworked
`assemble-bootstrap.sh` (closure-only extract/seed, upstream-format
`md5sums`, assembly-time alternatives wiring incl. the dpkg admin DB,
format measured against live dpkg) + 24 new host tests.
Seed set = `busybox bash apt dpkg coreutils less` at merge time (2026-08-23:
`curl` joined the seed set and `libcurl` the build roots — see the next
status block; see `CODEC_BOOTSTRAP_SEED_PACKAGES`).

**Historical status before PR #13 (2026-08-23):** the first rebuilt bootstrap
exposed two fresh-device defects. Both were fixed on the branch that followed
PR #12 and at that time still awaited rebuild, republish, and device verification.

- **Defect 1 — no HTTPS metadata fetcher.** The Part B closure seeds none of
  `curl`/`python3`/`wget` (the in-code claim that python3 was in the closure
  was wrong), so on a fresh device `pkg update` died at
  `pkg: offline or unable to download CodeC Release metadata (HTTPS required)`.
  Also, `pkg`'s maintainer-script byte checks (`spec_in_file`) called
  `python3`, which would have broken `pkg install` preflight the same way.
  **Fix:** `libcurl` joins the build roots (the `curl` CLI is its subpackage
  at the pinned revision, auto-generating `Depends: libcurl (= …)` so the
  closure walker resolves it), `curl` joins the seed set, and
  `spec_in_file` is now pure shell (`$(cat)` + `case`). `ca-certificates`
  (`etc/tls/cert.pem`, curl's CA bundle) was already in the closure via
  `apt → libgnutls → ca-certificates`. Python stays out (much larger
  closure).
- **Defect 2 — official Termux keyring seeded.** Fresh-device
  `dpkg-query` showed `ii termux-keyring 3.13`. The pinned apt recipe lists
  `termux-keyring` (GPG keys of the *official* Termux repositories, installed
  into `etc/apt/trusted.gpg.d/`) as a runtime dependency. **Fix:**
  `apply-recipe-overrides.sh` now removes exactly `, termux-keyring` from
  apt's `TERMUX_PKG_DEPENDS` (fail-loud on pinned-recipe drift).
  `termux-licenses` deliberately stays: it ships `$PREFIX/share/LICENSES/*`,
  the target of packaged license symlinks (e.g. nano's
  `share/licenses/nano`).
- **Release gate hardened:** `validate-bootstrap.py` now requires `bin/curl`
  (ELF) in the archive and rejects any `termux-keyring` stanza in the seeded
  dpkg status. Host suite: 53/53 green (fixtures prove the fetcher is
  seeded and termux-keyring is excluded without any 100-minute build).

**Historical rebuild record.** Dispatches 1–4 led to the final fixes; dispatch
#5 and the subsequent publish/device verification completed the part. The
first three runs below predate PR #12; #4 is the successful rebuild whose
bootstrap exposed the two defects above:

| # | Run | Duration | Result |
|---|---|---|---|
| 1 | `32581293757` | ~2 min | **Our bug — fixed:** the guardrail scanner matched the repair script's own invariant *comment*; wording fixed + `tests/test_ci_guardrails.py` tripwire added. |
| 2 | `32582311088` | ~50 min | **Upstream network flake — log-proven:** `curl: (28)` fetching `util-macros` from `xorg.freedesktop.org`; fixed by the PR #12 mirror override. |
| 3 | `32585409356` | ~48 min | Same util-macros step; cause unreadable from the agent sandbox; same mirror override applied. |
| 4 | `32594910882` | 1h14m (aarch64) / 1h26m (x86_64) | ✅ **Success** → published by `32617929254` → fresh-device download/verify/extract OK → defects 1+2 found → this fix. |
| 5 | `32620704350` | ~1h20m | ✅ **Success** from PR #13 merge → published by `32625580655` → full Part B fresh-device acceptance passed. |

### Completed procedure (historical — do not rerun)

The following was the final procedure and is retained only as an audit trail.
It is **not** a current instruction.

1. **Redispatch from `main`** (the new `libcurl` root builds OpenSSL +
   libnghttp2/3 + libtcp2-family + libssh2 first):
   ```sh
   gh workflow run "CodeC package repository" --ref main
   gh run watch
   ```
   If it fails on a source download, follow the established pattern: add a
   narrow mirror override in `apply-recipe-overrides.sh` (attr/libacl and
   util-macros precedents), with a host test, then redispatch.
2. **On success, republish the bootstrap** (this re-runs
   `validate-bootstrap.py` — which now enforces `bin/curl` and the absence
   of `termux-keyring` — and swaps the release assets):
   ```sh
   gh workflow run "Publish CodeC bootstrap release" --ref main \
     -f source_run_id=<RUN_ID> -f release_tag=userland-v2-dev
   ```
3. **Device verification (Part B exit condition, ~10 min).** Full app
   uninstall → install the latest successful `Build APK` artifact → "Install
   userland", then in the CodeC terminal, one block at a time:
   ```sh
   grep -n clang $PREFIX/var/lib/dpkg/status; echo exit=$?   # expect: no output, exit=1
   command -v curl             # expect: $PREFIX/bin/curl (HTTPS metadata fetcher)
   curl -fsSI --max-time 30 https://pabi277.github.io/CodeC/dev/dists/stable/Release >/dev/null && echo tls-ok
   pager -V                    # expect: GNU less version banner
   which pager editor vi       # expect: all resolve under $PREFIX/bin
   dpkg --audit                # expect: empty output
   dpkg -l | grep -E 'doxygen|swig|tcl|tor|fontconfig|termux-keyring'; echo exit=$?   # expect: exit=1
   pkg update                  # NOTE: 'W: No Hash entry in Release file' is EXPECTED until Part D — not a bug
   pkg install nano && nano --version    # expect: GNU nano, version 9.2
   which editor                # expect: $PREFIX/bin/editor
   pkg uninstall nano
   printf '#include <stdio.h>\nint main(){printf("ok\\n");return 0;}\n' > t.c
   cc t.c -o a.out && ./a.out  # expect: ok
   ```
   Every line matched on the fresh aarch64 device; Part B's exit condition was
   met and recorded here and in [`JOURNEY.md`](JOURNEY.md).

**Original rationale (resolved).** The earlier bootstrap had three content
defects, all visible on device:

1. **Build-dependency pollution.** `assemble-bootstrap.sh` seeds *every* built
   `.deb` — including build-only packages (`doxygen`, `swig`, `tcl`,
   `fontconfig`, `tor`, …) — into `var/lib/dpkg/status`. That is how `clang`
   (a build tool) leaked in as a runtime dependency in the first place. The
   bootstrap should record **only** the transitive `Depends` closure of the
   four roots (`busybox bash apt dpkg`).
2. **Seeded packages never run their postinst.** Their alternatives are never
   registered → `pager: command not found`, `editor` only appears after a real
   `pkg install`. The seeded `coreutils`/`less`/`nano`-style roots should have
   their alternatives wired at assembly time (or via a post-install script).
3. **No `md5sums`.** Every seeded package fails `dpkg --audit` with "missing
   the md5sums control file".

**Exit condition.** On a fresh bootstrap: `pager -V` reports `less`,
`dpkg --audit` is clean for seeded packages, and `dpkg -l` lists only the
runtime closure (no `doxygen`/`swig`/`tcl`/`tor`/…).

**Completed implementation steps.**
1. In `assemble-bootstrap.sh`, the "seed every built `.deb`" loop was replaced
   with a closure walk from the roots: read each root's `Depends`, resolve
   against the built set, and seed only those.
2. Upstream-format `md5sums` control files were generated for seeded packages.
3. Seeded-package alternatives were emitted at assembly time, including the
   dpkg admin database.
4. The bootstrap was rebuilt, republished, and device-verified.

---

## Part C — Clean-device acceptance (the M2 gate)

**Status: COMPLETE and device-verified (2026-08-23).** A clean Samsung
SM-A356E (Android 16, aarch64) passed bootstrap/runtime smoke, package
operations, alternatives, negative checks, compiler checks, airplane-mode
restart, and interrupted-install recovery. That recovery test exposed a stale
`codec-pkg/lock`; PR #14 commit `8e95a16` fixed dead-PID lock recovery and the
repeated force-stop test passed without manual state deletion.

The final second-device test exposed a wrong legacy-marker assumption:
v1.3.14 actually writes `.userland-vuserland-v1`. PR #14 commit `a4e5af6`
corrected it, CI passed, and an in-place v1 → `userland-v2-dev` update then
passed the full package/compiler/contamination block. The exit condition below
is met.

**Why.** [`docs/chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`](chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md) is
the explicit clean-device acceptance gate. It now records every item as passed.

**Exit condition.** Every unchecked item in `chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`
passes on a clean arm64 device (and x86_64 if available), including the
negative checks and the recovery tests.

**Steps (in order, on a clean device).**
1. [x] Uninstall CodeC fully; install a fresh APK (≥ 1.3.15) + Install userland.
2. [x] Runtime smoke (section 2 of the checklist): `$PREFIX`, `which bash`,
   `$BASH_VERSION`, `busybox`, `which apt-get dpkg`, `dpkg --print-architecture`,
   `dpkg -l`.
3. [x] Package ops (section 3): `pkg update / search nano / install nano /
   nano --version / uninstall nano / upgrade`, then the `coreutils`/`less`/`nano`
   alternatives closure (`which pager editor`, `pager -V`).
4. [x] Negative checks: `sources.list` is CodeC-only; `pkg uninstall bash`
   refused; no `com.termux` in `dpkg -l`.
5. [x] Compiler smoke before **and** after package ops (section 4).
6. [x] Airplane-mode restart (section 5).
7. [x] Interrupted-install recovery (section 6): force-stop mid-download,
   automatically reclaim the stale lock, retry, and confirm `pkg repair` clean.
8. [x] v1 → Phase 3 upgrade path (section 7) on a second arm64 device.

---

## Part D — M3: sign the repository and verify on device — ✅ DONE (device-verified)

**Status: COMPLETE (2026-08-24) — implementation, signed publication, client
acceptance, bootstrap build/release, and the final clean-device gate have all
passed.** PR #14 contains key-agnostic signing/validation, protected-subkey CI
support, the public-only production keyring, signed-only client/bootstrap
integration, and tamper/missing-signature tests. Corrective signed publication
run `32642631785` reused existing artifacts, skipped both expensive builds, and
fixed the APT Release-stanza defect found by the first device attempt. The
real CodeC device then passed signed update, exact-key verification, tamper
rejection, nano lifecycle, clean audit, and compiler checks. Build run
`32643383952` then built both architectures successfully; each archive passed
the validator's exact v3-keyring byte comparison and was uploaded as a
non-expired artifact. Release run `32648783080` revalidated both archives and
replaced the four `userland-v2-dev` assets.

**Final clean-device gate — passed 2026-08-24.** After a verified pre-uninstall
backup (checksum, `gzip -t`, listing, independent `cmp`), a full uninstall and
fresh reinstall against the exact published `userland-v2-dev` archive
(aarch64, 23,928,215 bytes) reached `userland: ready` automatically and passed
every remaining section-8 check on a real device: no `clang`/build-pollution/
`termux-keyring` (verified with an exact package-name match after an
unanchored `grep` gave a `sed`-description false positive), CodeC-only
`sources.list`, warning-free signed `pkg update` with an exact keyring-hash
match, independent `gpgv` acceptance of the live signature and rejection of a
tampered copy, a full nano install/uninstall cycle with working alternatives,
a silent `dpkg --audit`, and a working embedded compiler. Full commands and
output are recorded in
[`chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`](chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md) §8. **Do not
re-run this expensive/destructive test unless a genuine new defect is found;
this Part is done.**

**Why.** The development channel originally relied on HTTPS + SHA-256 only.
Signing closes the integrity-vs-tampering gap. The Part D client removes `[trusted=yes]`, verifies
`InRelease` with `gpgv` before apt, and lets apt independently verify through an
exact `signed-by=` keyring.

**Exit condition.** A device with only the CodeC versioned keyring installed
accepts the repository because its `InRelease` signature and Release/index/
package hash chain verify, rejects tampered metadata, and uses no
`trusted=yes`. The rebuilt bootstrap contains byte-for-byte the same keyring.

**Completed inexpensive implementation.**
1. `sign-repository.sh` emits `InRelease` and `Release.gpg` with an exact
   dedicated signing subkey; the protected passphrase travels through stdin,
   never argv.
2. `validate-repository.py` requires both signature forms, exact cleartext,
   exact fingerprint, and the existing index/package hash chains. Real-GPG
   tests cover valid, missing, tampered, and changed-index cases.
3. Public keyring v1 and fingerprints are committed under
   `codec-packages/keys/`; no private key material is committed. The offline
   primary and CI signing subkey fingerprints are recorded in
   [`chat-phase3/REPOSITORY_SIGNING.md`](chat-phase3/REPOSITORY_SIGNING.md).
4. The APK installs that keyring under `etc/apt/keyrings`; `pkg` requires
   `gpgv`, verifies signed Origin/Suite, and writes a CodeC-only `signed-by=`
   source. The Phase 3 bootstrap assembler seeds the same bytes and its
   validator rejects missing/different keyrings.
5. Release hash paths are now relative to `dists/stable/Release`, eliminating
   the historical APT `No Hash entry in Release file` warning.
6. The active publication workflow imports the CI-only subkey, fails closed on
   secret/fingerprint drift, signs before signed validation, and deploys only
   the public key files. Rotation, revocation, rollback, and overlap rules are
   documented.

**Exit condition met.** The final clean-device
keyring/signed-APT/package/audit/compiler test against the rebuilt
`userland-v2-dev` assets passed (see above and
[`chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md`](chat-phase3/PHASE3_DEVICE_ACCEPTANCE.md) §8). PR #14 is
ready to merge as "Phase 3 complete" pending final owner review.

---

## Phase 4 — polish and expansion

Phase 4 planning and tracking lives in [`chat-phase4/PHASE4_ROADMAP.md`](chat-phase4/PHASE4_ROADMAP.md) and [`docs/chat-phase4/`](chat-phase4/README.md); **Phase 4 is complete (Parts 4.1–4.8)**.

- **Part 4.1 — Shared-storage access (`~/storage`)** ✅ **DONE (device-verified 2026-08-24).**
  Detailed record in [`docs/chat-phase4/PART_4_1_STORAGE.md`](chat-phase4/PART_4_1_STORAGE.md).
- **Part 4.2 — Package-install confirmation UX** ✅ **DONE (verified 2026-08-24).**
  Detailed record in [`docs/chat-phase4/PART_4_2_INSTALL_CONFIRMATION.md`](chat-phase4/PART_4_2_INSTALL_CONFIRMATION.md).
- **Part 4.3 — Trust/channel indicator UX** ✅ **DONE (verified 2026-08-24).**
  Detailed record in [`docs/chat-phase4/PART_4_3_TRUST_CHANNEL_UX.md`](chat-phase4/PART_4_3_TRUST_CHANNEL_UX.md).
- **Part 4.4 — Terminal/editor settings parity** ✅ **DONE (device-verified 2026-08-24).**
  Detailed record in [`docs/chat-phase4/PART_4_4_SETTINGS_PARITY.md`](chat-phase4/PART_4_4_SETTINGS_PARITY.md).
- **Part 4.5 — Expanded package catalog (round 2, CI build)** ✅ **DONE (CI verified 2026-08-25).**
  Detailed record in [`docs/chat-phase4/PART_4_5_CATALOG_EXPANSION.md`](chat-phase4/PART_4_5_CATALOG_EXPANSION.md).
  Workflow run [`32845127723`](https://github.com/pabi277/CodeC/actions/runs/32845127723) (1h 53m 36s)
  built both architectures (`aarch64`, `x86_64`) green with 25 curated package roots, zero maintainer script violations, and byte-identical bootstrap archives.
- **Part 4.6 — Expanded package catalog (round 2, publish & device gate)** ✅ **DONE (device-verified 2026-08-25).**
  Detailed record in [`docs/chat-phase4/PART_4_6_CATALOG_ACCEPTANCE.md`](chat-phase4/PART_4_6_CATALOG_ACCEPTANCE.md).
  Published via run [`32858460740`](https://github.com/pabi277/CodeC/actions/runs/32858460740) and verified `pkg install` + execution of all 15 new roots on real arm64 hardware.
- **Part 4.7 — Android integration slice** ✅ **DONE (device-verified 2026-08-26)** — clipboard
  over the reusable `CodeCApi` bridge; CI + all primary device checks green incl. the piped/
  redirected channel fix (`/dev/tty`); optional negatives waived; dispatch moved to activity
  scope. 4.8+ ready. See
  [`chat-phase4/PART_4_7_ANDROID_INTEGRATION.md`](chat-phase4/PART_4_7_ANDROID_INTEGRATION.md).
- **Part 4.8 — Android notifications slice** ✅ **DONE (device-verified 2026-08-26)** — `codec-notify
  send|clear|status` over the 4.7 bridge, deliberately exercising the `POST_NOTIFICATIONS`
  runtime-permission path (channel creation, `NEED_PERMISSION` marker, activity launcher,
  atomic resume). Host `sh` harness green; CI green; device-verified end to end incl. the
  permission dialog → allow → `OK` flow, no re-prompt on later sends, and owner-confirmed
  notification tap → CodeC opens. Two device-driven fixes during acceptance (hint once + 30 s
  wait; onResume recovery for the system-owned dialog). **Phase 4 complete.** See
  [`chat-phase4/PART_4_8_ANDROID_NOTIFICATIONS.md`](chat-phase4/PART_4_8_ANDROID_NOTIFICATIONS.md).
- **Post-4.5/4.6 review** ✅ **DONE (device-verified 2026-08-26).** Recipe-override
  hardening, fully artifact-neutral (no rebuild/re-publish needed) — plus the
  `pkg heal` alternatives-DB self-repair, the `plan-bootstrap.py` slave-placeholder
  fix (future bootstrap archives), and the pinned shared CI debug key
  (`debug.keystore`) that stops wipe-on-update between CI builds (proven:
  pinned-cert APK updated in place, 82 packages and the userland intact).
  Record in
  [`docs/chat-phase4/PART_4_5_4_6_POST_IMPLEMENTATION_REVIEW.md`](chat-phase4/PART_4_5_4_6_POST_IMPLEMENTATION_REVIEW.md) —
  including two non-blocking known issues for a future client PR:
  **KI-1** `pkg install` reports failure when the target is already the
  newest version (treat apt's "0 newly installed" as success), and
  **KI-2** device `$PREFIX` (`/data/user/0/...`) vs the dpkg-recorded
  `/data/data/...` spelling confuse manual `update-alternatives` calls
  (canonicalize `PREFIX` at shell setup after a full regression pass).

---

## Ordering summary

| Part | Depends on | Effort / state (2026-08-25) |
|---|---|---|
| Phase 3 Part A — republish clean bootstrap | — | ✅ **DONE** (in-place repair, no rebuild, device-verified) |
| Phase 3 Part B — bootstrap correctness | A | ✅ **DONE** — merged, rebuilt, republished, device-verified |
| Phase 3 Part C — clean-device acceptance | A ✅, B ✅ | ✅ **DONE** — every checklist item passed on real arm64 devices |
| Phase 3 Part D — M3 signing | A ✅, B ✅, C ✅ | ✅ **DONE** — implementation, signed publish, rebuild, and final clean-device gate all passed |
| Phase 4 Part 4.1 — shared-storage access | none | ✅ **DONE** — `codec-setup-storage`, OSC 1337, device-verified |
| Phase 4 Part 4.2 — install confirmation UX | none | ✅ **DONE** — transaction summary, `[Y/n]` prompt, `-y` flag |
| Phase 4 Part 4.3 — trust/channel indicator UX | none | ✅ **DONE** — Settings trust card, `pkg status`, repo probe |
| Phase 4 Part 4.4 — settings/theme parity | none | ✅ **DONE** — font family, themes, settings UI, live preview |
| Phase 4 Part 4.5 — expanded package build (CI) | none | ✅ **DONE** — run `32845127723` green (25 roots, aarch64 + x86_64) |
| Phase 4 Part 4.6 — expanded package publish + device accept | 4.5 | ✅ **DONE** — published run `32858460740` & device-verified |
| Phase 4 Part 4.7 | 4.6 on 4.5 | ✅ **DONE** (device-verified 2026-08-26) — `codec-clipboard` over `CodeCApi` OSC bridge |
| Phase 4 Part 4.8 | 4.7 | ✅ **DONE** (device-verified 2026-08-26) — `codec-notify` over the same bridge, `POST_NOTIFICATIONS` runtime path device-verified (dialog → allow → OK; tap opens CodeC) |

---

## Phase 5 — client fixes, web preview, and capability batch

✅ **COMPLETE (2026-08-26, merged in PR #23).**
- Part 5.1: KI-1 / KI-2 client fixes.
- Part 5.2: In-app web preview via WebView / local server.
- Part 5.3: Capability batch (`codec-toast`, `codec-share`, `codec-open-url`, `codec-vibrate`).
See `docs/chat-phase5/` and `docs/PHASE5_ROADMAP.md`.

---

## Phase 6 — Terminal UX

✅ **IMPLEMENTED (2026-08-28 on `arena/01a0482c-codec`).**
- Part 6.1: Safe-area / cutout padding (`safeDrawingPadding()`, `shortEdges`), configurable multi-row extra-keys grid + macros in Settings, wake lock with safety, URL tap-to-open, VT BEL visual flash + vibrate, dynamic title, selection-aware copy, character cell alignment, and smooth 60fps pinch-to-zoom.
See `docs/chat-phase6/PART_6_TERMINAL_UX.md`.

---

## Phase 7 — Multi-terminal sessions

✅ **COMPLETE (device-verified 2026-08-28 on `arena/01a048df-codec`).**
- `TerminalSessionManager` (N concurrent PTY sessions, adjacent-selection close,
  auto-recreate, 8-cap, `anyAlive` wake lock) + `TerminalViewModel` delegation with
  one CodeCApi collector per session; active-session routing preserves the public
  API (`ModulesScreen` and command routes untouched).
- Session switcher dropdown + rename/close dialogs in `TerminalScreen`;
  `TerminalEmulatorView.resizeKey` fixes latent cursor drift on switch.
- Design decisions D1–D12: `docs/chat-phase7/PART_7_DESIGN_DECISIONS.md`.
  Status + owner follow-ups (CI unit-test step one-liner — the agent token cannot
  modify workflow files — and the §4 device recipe):
  `docs/chat-phase7/README.md`.
- Evidence: CI compile-green run `33185424586`; 10 unit tests written
  (`TerminalSessionManagerTest`) but not yet executed (no JDK in the agent
  sandbox; CI runs assemble only). **Device acceptance green (§6): full §4
  recipe + regression batch on the owner's aarch64 device. Exit condition MET —
  closed.**

---

## Phases 15–17 — Spck-style Editor & Project Experience (IMPLEMENTED; 15/16 merged, 17 device-gated)

📋 **Phase 15 device round 1 closed on `83ba499` (CI green `33385105931`)** (record: [`chat-phase15/PART_15_PROJECTS_HUB.md`](chat-phase15/PART_15_PROJECTS_HUB.md)
§6). **Phase 16 IMPLEMENTED & CI-GREEN (2026-08-31, same branch; device round 1:
python `__pycache__` git-noise fixed via `PythonCacheIgnore`, CI green
`33392053258` @ `435c5f4`)** (record: [`chat-phase15/PART_16_EDITOR_SHELL.md`](chat-phase15/PART_16_EDITOR_SHELL.md)
§6). **Device round 2 (2026-08-31, owner: "something is off about the ui — I
want exactly same ui") — mockup-exact re-skin of the Phase 15/16 UI:** flat
5-tab bar (Home·Projects·Editor·Terminal·Settings, the mockup's exact set),
pill filter chips, 16dp cards with 56dp type squares + the Python logo mark,
mockup-color `+` sheet, clone dialog rebuilt (labels above fields, QR icon,
branch dropdown, Settings link), editor top bar reduced to `☰ tabs  ⋮ ▶ RUN`
(second toolbar row → overflow), 3dp tab underline on the bar's bottom edge,
keycap keys row, dot-separated status bar, gutter divider, drawer re-skin
(branch chip, 4-column toolbar, typed file icons, purple selected row,
Source Control + Switch Branch footer), and the Source Control sheet rebuilt
to `mockups/source-control.png` incl. per-file stage toggle
(`GitManager.stageFile`/`unstageFile`, +2 host tests). Hand-drawn glyphs in
`ui/components/SpckIcons.kt` (clean-room — public mockups only, no copied
assets). **Phase 17 remainder implemented 2026-08-31 on
`arena/01a05878-codec`** (Switch Branch dialog with stash/auto-restore + New
branch, Conflicts group + Mark Resolved, commit blocking, purple `U` in the
tree, Push Changes in the card menu — record:
[`chat-phase15/PART_17_SOURCE_CONTROL.md`](chat-phase15/PART_17_SOURCE_CONTROL.md)
§6.1; **device round 1 done — the two push bugs the owner found are fixed**, see
[`PART_17_SOURCE_CONTROL.md`](chat-phase15/PART_17_SOURCE_CONTROL.md) §6.2;
the only step not yet exercised on device is the optional conflict recipe
(§4 step 8) — it needs a reproducible conflict). Started on the owner's instructions
("i going to start phase 15" → "Start phase 15"). Originally
added 2026-08-31 on the owner's request to clone the
[Spck Editor / Git Client](https://play.google.com/store/apps/details?id=io.spck)
project & editor experience (import git project, project list, full editor look &
feel). Client-only; reuses the Phase 8/9/11/13/14 engines — a UI/UX parity + gap
fill, not a rewrite. Spec + phone mockups in [`chat-phase15/`](chat-phase15/).

- **Phase 15 — Projects Hub & Unified Import.** Redesigned Projects screen (card
  list, filter chips, search) + one `+` sheet: **New Project / Clone Git Repo /
  Import ZIP / Open Folder**, per-project git overflow actions.
  [`PART_15_PROJECTS_HUB.md`](chat-phase15/PART_15_PROJECTS_HUB.md).
- **Phase 16 — Spck-style Editor Shell.** Nav drawer file tree with git status,
  refined tabs, snippet/extra-keys keyboard row, readability controls,
  launch-default HTML preview, errors badge.
  [`PART_16_EDITOR_SHELL.md`](chat-phase15/PART_16_EDITOR_SHELL.md).
- **Phase 17 — In-editor Source Control & Branching.** Source Control sheet,
  in-tree M/A/D/? status letters, tap-to-diff, Switch Branch (+stash), Pull/Push
  menu items, merge-conflict marking.
  [`PART_17_SOURCE_CONTROL.md`](chat-phase15/PART_17_SOURCE_CONTROL.md).
- **Phase 18 — CodeCApi Device Capabilities** (was Phase 15) moved to the end:
  [`chat-phase18/`](chat-phase18/).

> Standing rule still applies: no PR/merge without the owner's explicit command.

---

## Phase 19 — Terminal Parity (Termux-quality terminal) — ✅ COMPLETE & DEVICE-ACCEPTED (PR on owner's word)

✅ **IMPLEMENTED (2026-08-31, `arena/01a056aa-codec`) on the owner's "Ok start
phase 19 … also find other things Termux does better and fix it"** — all FIVE
parts (19.1 reflow, 19.2 integer cells, 19.3 live cadence, **19.4 Unicode
widths**, **19.5 protocol/interaction parity** — the last two from the parity
audit). `Build APK` `33371114549` green (assemble + tests + lint; 2 red rounds
first caught the Indic vowel-sign width gap + test bugs). ~50 new host tests.
**Remaining gate: the owner's per-part device recipes**
(`chat-phase19/PART_19_*.md` §5). Original spec follows.

**Device round 1 (2026-08-31, owner transcript):** ONE regression found —
*"letters have a noticeable gap between them"*: 19.2's `ceil(advance)` cell
added up to 1px of tracking per letter. **Fixed same day** with
`CellMetrics.fitSizeToGrid` (nudge the font size <1% until the advance IS a
whole pixel, so the integer cell equals the font's advance — crisp AND tight;
postmortem in `chat-phase19/PART_19_2_RENDERING.md` §7.1). Round-1 recipes
were also unusable on-device (multi-line paste, nonexistent `/usr/bin`) — all
round-2 recipes are single-line copy-pasteable. Round-1 positives: soft-wrap
of long lines ✓, Bengali/CJK/emoji echo ran clean. **Round 2 (same day,
owner screenshots + answers + `stty size` 32×60 vs Termux 39×71): density &
weight — fixed via default 12sp + bundled JetBrains Mono Medium/Bold (OFL) +
0.9 row-pitch factor (`fitSizeToGrid` kept; PART_19_2 §7.2). Round 3 (owner: "feels lagging /
not smooth scrolling / keyboard sometimes not popping up"): 4 fixes —
run-batched drawing, gesture detectors no longer restart every output
frame, pixel-smooth sub-row scrolling, IME retry loop (PART_19_3 §9).
**Round 4 (2026-08-31): PASS — owner: "All ok now".**
Phase 19 CLOSED: CI `33377713289`, 4 device rounds, ~70 new host tests.
**PR #34 MERGED to `main` at `b869ce6` (2026-08-31) — Phase 19 is fully
closed; post-merge CI on `main` green (`33380041937`).**

Phase 19 was specced 2026-08-31 on the owner's report that the terminal
(a) prints downloads only at the end, (b) overlaps letters, and (c)
doesn't reflow on zoom-out. Re-implements Termux-quality behavior in CodeC's own
clean-room emulator (⚖️ **not** copying GPLv3 Termux code). Client-only, pure
Kotlin/Compose, host-testable. Spec + before/after mockup in
[`chat-phase19/`](chat-phase19/).

- **Phase 19.1 — Scrollback & screen reflow on resize/zoom** (fixes zoom-out not
  refilling). [`PART_19_1_REFLOW.md`](chat-phase19/PART_19_1_REFLOW.md).
- **Phase 19.2 — Integer-cell crisp rendering** (fixes letter overlap).
  [`PART_19_2_RENDERING.md`](chat-phase19/PART_19_2_RENDERING.md).
- **Phase 19.3 — Live render cadence & streaming output** (fixes "prints at the
  end"). [`PART_19_3_LIVE_OUTPUT.md`](chat-phase19/PART_19_3_LIVE_OUTPUT.md).

> Independent of Phases 15–18; can be scheduled first. No PR/merge without the
> owner's explicit command.

---

## Phase 10 — Package & Command Hub (Modules Screen Upgrade)

✅ **IMPLEMENTED (2026-08-28 on `arena/01a0482c-codec`).**
- 1-tap direct package installation (`pkg install -y <pkg>`) and execution into live terminal (`\r` line discipline).
- Quick repository management actions (`pkg update`, `pkg upgrade -y`, `codec-setup-storage`, `pkg status`, `pkg heal`, `pkg repair`).
- Curated 25+ package catalog (Compilers, Editors, Languages, CLI tools, Compression).
- Live `$PREFIX/bin` installation status detection (`INSTALLED ✓` / `AVAILABLE`).
- Custom interactive command runner card.
See `docs/chat-phase10/PART_10_PKG_GUI.md`.
