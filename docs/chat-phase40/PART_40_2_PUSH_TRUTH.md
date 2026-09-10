# CodeC Phase 40.2 — Push & branch truth: what actually reached GitHub

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"sometimes it's push stay local, new branch create
> mostly stays local"*

## Symptom

After COMMIT & PUSH the app returns to a idle sheet. The user is left with two
questions the UI does not answer: *did anything leave the phone?* and *which
branch did it go to?* The existing signals are a card badge (`↑3`) that is
recomputed only in `loadProjects`, and a transient `branchResult` toast-line
inside the branch sheet that vanishes with the sheet. The owner's reading —
"push stays local" — is what happens when *success-without-push* and
*failure* look identical.

Three real mechanisms already in `GitManager` are invisible today:

- `push(setUpstream = …)`: with an upstream it is a plain `git push`, which
  pushes **only the current branch to its tracked ref** — so a commit on
  `feature/x` does not update `main`, and a user looking at `main` on GitHub
  correctly concludes "it stayed local".
- `pushHandlingUpstream()` re-derives `setUpstream` from
  `status(root).upstream == null` — an extra `git status` process whose result
  is thrown away.
- `SwitchBranchResult.published/publishError` — the publish-on-create result —
  reaches `describeSwitch()` and one string, then nothing.

## Design

**A pure `PushOutcome` parsed from git's own bytes.** New
`ui/projects/GitPushOutcome.kt`:

```kotlin
sealed interface PushOutcome {
    data class Pushed(val branch: String, val remoteUrl: String?,
                      val from: String?, val to: String, val newBranch: Boolean) : PushOutcome
    data object UpToDate : PushOutcome                     // "Everything up-to-date"
    data class Rejected(val why: RejectReason, val hint: String) : PushOutcome  // non-fast-forward | stale-info | shallow | protected-branch
    data class NoRemote(val message: String) : PushOutcome
    data class Auth(val kind: GitErrorKind, val message: String) : PushOutcome
    data class Failed(val message: String, val detail: String?) : PushOutcome
}
object GitPushParser { fun parse(stdout: List<String>, stderr: List<String>, exitCode: Int,
                                branch: String?, remote: String?): PushOutcome }
```

Parsing rules are pinned to git's actual output, all of it already
`GitRedactor`-cleaned: `To https://github.com/o/r.git`/`To git@…` (remote +
which URLs may be echoed), `old..new branch -> branch`,
`[new branch]`, `Everything up-to-date`, `! [rejected] … (non-fast-forward)`,
`remote: error: GH006: Protected branch update failed`, `shallow update not
allowed`, `fatal: The current branch X has no upstream branch`. **Unknown text
never becomes a guess**: `Failed` with the first redacted line as `detail`, and
"we could not read git's mind" is a *correct* outcome to show — better than a
green tick that lied.

**Result card, not a toast.** The sheet keeps a `lastResult: PushOutcome?` in
its `UiState` (survives scroll, cleared by a dismiss ✕ and by `refresh`)
rendered as:

```text
✓ Pushed  feature/x  →  github.com/pabi277/CodeC   (a1b2c3d)
  3 commits · 12 files · new branch on GitHub
```
or
```text
↑ Still local — 2 commits on feature/x
  main on GitHub is unchanged. Push this branch?   [ PUSH feature/x ]
```

That second card is the answer to "push stays local": the state *names the
branch it refers to*. `GitStatusParser` already yields `branch`, `upstream`,
`ahead`, `behind` and `unpublished`; the card is a projection of those plus
`firstRemote()`, computed by a pure
`PendingPushSummary.of(status, remoteUrl)`, so the hub badge and the sheet
share one source (the Phase 37 `ServerEndpoints` lesson: one owner of the
truth or they disagree).

**Publish on branch creation stays, but its failure becomes state.**
`checkoutNew` + publish (Phase 17) keeps its behaviour; the `publishError`
line moves from the transient `branchResult` into the same `lastResult` card,
and the hub badge for an unpublished branch gains the reason (`NO_REMOTE` vs
`AUTH` vs `REJECTED`) in its tooltip (40.1's `blocker().message()` is reused —
one text, two surfaces).

**No new push shapes.** Force-push, `--all`, `--mirror`, tag pushing by
default, and "push to `main` from a feature branch" are all *out*: CodeC's law
is that the phone never runs an irreversible git command without the user
typing it in the terminal. One deliberate addition: when `ahead > 0`
and `behind > 0`, the card offers **Pull first** and refuses to push into a
diverged upstream (git would reject it anyway; we simply say so before the
attempt, in the same vocabulary as 40.1).

## Exit condition

```text
1. Commit 1 file on `main`, PUSH → the card names branch `main`, the remote
   URL and the short SHA; the hub badge clears; github.com shows the commit.
2. Commit on `feature/x` (created in-app), PUSH → `feature/x` appears on
   GitHub as a new branch; the card says "new branch on GitHub".
3. With `feature/x` checked out, look at the Projects hub: `main` is NOT
   claimed to be updated; the badge names the branch that has pending commits.
4. Push a diverged branch (another device pushed first) → "Pull first", the
   push is not attempted, and the sheet still shows local commits intact.
5. Kill the app while a push is in flight, relaunch, open the sheet: the
   status is re-derived from git (no ghost "✓ Pushed" from a stale state).
PASS = all five on the owner's device (a second device/clone makes 4 easy).
```

## Tests (plan)

- `GitPushParserTest` (host, pure) — one case per git output shape above,
  including: both `To` URL forms (https and ssh), `[new branch]` vs
  `old..new`, `Everything up-to-date` with exit 0, `[rejected]
  (non-fast-forward)` with exit 1, `GH006` protected branch, `shallow update
  not allowed`, an empty-output failure (→ `Failed`, no crash), and a
  token-shaped string in the output asserting the redaction happened
  **before** parsing (the same guard `GitRedactor` has today — this test is
  the regression pin that `PushOutcome` can never smuggle a secret into a UI).
- `PendingPushSummaryTest` — ahead/behind/unpublished/no-remote matrix;
  "ahead on feature/x" must never be reported as "main is behind";
  a fresh repo with **zero commits** is not "unpublished" (that nuance is
  already in `GitStatus.unpublished`'s doc and must survive the refactor).
- `GitStatusParserTest` — unchanged behaviour after any refactor (its
  `## main...origin/main [ahead 1, behind 2]` cases are the contract).
- No Robolectric for the card layout; `UiState.lastResult` is plain data and
  the VM test asserts it is set on success, set on failure, cleared by
  `refresh`, and *not* cleared by dismissing a dialog.

## Sources (record)

- CodeC code, 2026-09-10: `GitManager.push`/`pushHandlingUpstream`/
  `firstRemote`, `GitStatusParser` (doc block with the `## …[ahead, behind]`
  format), `GitBranchOps.SwitchBranchResult`, `GitControlViewModel`
  (`pushError`, `describeSwitch`), `GitControlView:405-440` (the badge block),
  `ProjectsHub.kt:67` (`unpublished` field), `FileManagerScreen:1255-1285`
  (the `↑N` / `↑` badges).
- `docs/chat-phase15/PART_17_SOURCE_CONTROL.md` (the upstream/publish device fix) and the Phase 37
  "one owner of the truth" pattern (`ServerRegistry`/`ServerEndpoints`).
- git's own output formats, as reproduced on device during Phase 17's round
  and in the sandbox (`git 2.x` from the CodeC userland); the parser test
  fixtures are those captured lines, not invented text.

## Deferred / rejected with reasons

- **A "GitHub" full-screen tab** (list of repos, PRs, actions) — `gh` exists
  in the terminal for that; the IDE's job here is *never lie about state*.
- **Auto-pull before push** — silently merging on the user's phone is the
  fastest way to create a commit they cannot read; we refuse and explain.
- **`git push --force-with-lease`** — tempting, still destructive, still no.
- **Persisting "✓ Pushed" across restarts** — it would eventually lie; the
  status is always re-derived from `git status`.
