# Proposed Phase 73.1 — Source control and GitHub

**Status: ✅ IMPLEMENTED (2026-09-29), CI ✅ GREEN, owner device pass ✅ 2026-09-29, merged via PR #95; session branch
`arena/01a0eb2d-codec`. Record, CI and evidence below.** One part = one future chat.
No deadline, dependency, new control or replacement engine promised.

## Copy into a new chat

> Review `docs/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/screens/GitControlView.kt:96`](../../app/src/main/java/com/codeci/ide/ui/screens/GitControlView.kt#L96), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** 124055 shows an empty repository state only. It is not evidence for the layout of populated Git controls.

## My recommendation

Improve staged/unstaged grouping, button hierarchy and actionable errors. Preserve the recently implemented discard safety exactly.

## Bounded review checklist

Uninitialised repo; status/refresh; diff; stage/unstage; commit; push/pull; publish; branch/stash; conflicts; auth/identity failures; discard confirmation and editor reconciliation.

## Your thoughts — ask before code

**Do you want Git focused on beginner actions, or a denser power-user layout with more controls visible?**

Suggested starting point, not your decision: Lead with status, diff and commit; put dangerous/advanced actions behind clearly named confirmations, not hidden semantics.

**Owner answer (2026-09-29): "Your choice."** The owner delegated all three questions
asked this chat (focus, the stage-toggle finding, and whether to check the button's
colour). Every design decision below is therefore recorded as the chat's, not the
owner's.

## Implementation and exit, after agreement

GitControlViewModel, GitManager, GitDiscardPolicy, GitDiscardEditors and BranchSwitchSheet; discard remains unstaged-only and exact-path checked.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.

## Discussion record — 2026-09-29 (owner: "73.1", i.e. Source control and GitHub)

Evidence the chat read in the checkout on `main` @ `69c4b53` (source reading only —
no device, no run; every line below is a reading, not a reproduced defect):

1. **No staged/unstaged grouping exists.** Every non-conflict file sits in one flat
   "Changes N" list (`GitControlView.kt`'s `others`), regardless of `GitFileChange.isStaged`.
2. **The per-file +/− toggle was cosmetic.** `GitChangeRow`'s trailing button drew the
   identical `SpckIcons.PlusMinus` glyph whether a file was staged or not — only its
   (sighted-users-never-see) content description changed between "Stage file" and
   "Unstage file". Worse: the toggle called `viewModel.toggleStage` (`git add`/`git reset`
   on one path), but the sheet's only commit action, `commitAndPush`, always calls
   `git.stageAll()` (add -A) before `git.commit()` — confirmed `git.commit(` has exactly
   one call site in the whole ViewModel, inside `commitAndPush`. So toggling a file's
   stage state had **zero effect** on what the next COMMIT & PUSH actually committed.
   This was consistent, not contradictory, with the existing "what will be committed"
   preview (`RepoHygiene.commitPreview`): its own comment already says it treats
   "every listed change as would be staged by add -A", i.e. the preview already assumes
   stage-everything and ignores the real git index too.
3. **The enabled COMMIT & PUSH colours already pass contrast** — measured (WCAG relative
   luminance) at 7.58:1 for `#221A3E` text on `#C3A1F5`, above both AA (4.5:1) and AAA
   (7:1); its 0.4/0.6-alpha disabled state is exempt under WCAG §1.4.3 (the rule this
   repo's Phase 40.5 colour law already carries in `rule.md` §6). Per that same law
   ("values that already pass are not restyled"), no colour change was made.
4. Uninitialised-repo state only points at the terminal or Clone-from-GitHub (no in-sheet
   "Initialize"); stash is only an implicit branch-switch checkbox, no manual stash view.
   Neither is changed this chat — out of scope for the owner's three answered questions.

Questions asked, **owner answer verbatim: "Your choice."** for all three — recorded here
as the chat's decisions, not the owner's:

- **Focus (beginner vs. power-user):** beginner-focused, kept as the brief's own starting
  recommendation — lead with status/diff/commit, advanced/dangerous actions stay behind
  clear confirmations (discard already does this; untouched).
- **The stage-toggle finding:** keep "stage everything" as the one-tap commit model
  (matches the existing, already-honest commit preview and avoids a bigger, riskier
  change to what `commitAndPush` actually commits) — and remove the now-misleading
  per-file +/− toggle from the ordinary ("others") change rows, since it had no bearing
  on the outcome. The conflict rows' "Mark Resolved" control is untouched (it calls
  `git.stageFile` directly through `markResolved`, a real, functional action).
- **The button colour:** checked (measured above), left unchanged — it already passes.

## Implementation record

| # | What | Design (choice) | Where |
|---|---|---|---|
| 1 | Remove the misleading per-file stage toggle | `GitChangeRow`'s trailing +/− control now renders only when `markResolvedMode` (a conflict row); the ordinary "others" row keeps its icon, name/path, badge and Discard (when eligible), nothing else. `onToggleStage` is now nullable, wired only for Mark Resolved. | `GitControlView.kt` |
| 2 | Remove the now-orphaned wiring | `GitControlViewModel.toggleStage()` deleted (its only caller was the removed button); `GitManager.stageFile`/`unstageFile` are untouched — `markResolved` still calls `stageFile` directly. | `GitControlViewModel.kt` |
| 3 | Button colour | Measured, not changed (see Discussion record #3). | — |

Not done (in scope for the checklist, out of scope for this chat's answered questions):
an in-sheet "Initialize repository" action for an uninitialised repo; a manual stash
view outside the branch-switch checkbox; deeper button-hierarchy or actionable-error
changes beyond the stage-toggle fix (the existing push-result/readiness/help-link
affordances were reviewed and already do this — see Discussion record and
`UI_POLISH_REVIEW_20260927.md` §3).

### Evidence, kept apart

- **Source tests** (changed, host JVM; CI executes them): `GitDiscardWiringTest`
  (`existing git affordances still delegate to the one engine` updated to drop the
  removed `viewModel.toggleStage(` pin; new case `the ordinary change row has no stage
  toggle, only Mark Resolved does`, pinning that the sheet no longer calls
  `viewModel.toggleStage(`, the ViewModel no longer declares `fun toggleStage(`, the
  ordinary-row call site passes no `onToggleStage`, and the conflict-row call site still
  wires `onToggleStage` to `viewModel.markResolved(...)` with `markResolvedMode = true`).
  The chat also re-read `GitDiscardPolicyTest`, `GitManagerTest` and `StageAllHygieneTest`
  (all untouched by this change) to confirm `isStaged`, `stageFile`/`unstageFile` and
  `stageAll` still have their existing callers and tests. Pre-validated with a local
  kotlinc 2.4.20 + JRE 25 syntax check on the two changed production files and the
  changed test file (no parse/"expecting"-class errors; the reported errors are all
  unresolved-reference cascades from the missing Android/Compose classpath, expected
  without Gradle) — a syntax reading, **not CI**, and no Robolectric/Compose execution.
- **Android tests:** none added; `GitControlView`/`GitControlViewModel` were not compiled
  locally (no Gradle here) — CI's `assembleDebug`/`testDebugUnitTest` is the first
  real compile and run.
- **Device evidence:** none. The owner has not tested this build. Device pass required
  for: a repo with modified + new + deleted files shows the same rows minus the +/−
  button; Discard still works exactly as before; Mark Resolved on a conflict still
  works; COMMIT & PUSH still commits and pushes everything shown.

### CI

✅ **GREEN** — `Build APK` [`36519004263`](https://github.com/pabi277/CodeC/actions/runs/36519004263)
on commit `9acfa34` (12 m 17 s, `arena/01a0eb2d-codec`). This is the first real Gradle
compile/run of `GitControlView.kt`/`GitControlViewModel.kt` after this change —
`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` all passed, so
`GitDiscardWiringTest`'s new/changed cases ran on real JUnit, not only the local kotlinc
syntax check. **Still no device pass** — none claimed.
