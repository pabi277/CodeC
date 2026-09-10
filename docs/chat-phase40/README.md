# CodeC Phase 40 — GitHub that tells the truth

> **Status:** 🔧 IMPLEMENTED on `arena/01a08c04-codec` (host-tested, CI pending) —
> the first attempt on `arena/01a08b68-codec` failed CI 16 times and is recorded
> with its root causes in [PART_40_4](PART_40_4_CI_LOOP_DIAGNOSIS.md) · **Cost:**
> `[client-only]` · **Effort:** M/L · **Owner row:** *"Github integration update
> now Github is working but it's not user friendly if i try to clone a repo and
> didn't download the git it shows error in the background i can't see it,
> sometimes it's push stay local, new branch create mostly stays local"*

```text
  40.1  Readiness + errors that cannot be missed
  40.2  Push / branch truth (what actually reached GitHub)
  40.3  Publish to GitHub (create the remote when there isn't one)
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [40.1](PART_40_1_READINESS_AND_ERRORS.md) | Readiness gate + visible errors | S/M | 🔧 IMPLEMENTED |
| [40.2](PART_40_2_PUSH_TRUTH.md) | Push & branch outcome | M | 🔧 IMPLEMENTED |
| [40.3](PART_40_3_PUBLISH_TO_GITHUB.md) | Publish to GitHub | M | 🔧 IMPLEMENTED |
| [40.4](PART_40_4_CI_LOOP_DIAGNOSIS.md) | The 16-run failure loop: diagnosis + repair rules | S/M | ✅ DIAGNOSED |

**Implementation map** (all on `arena/01a08c04-codec`, one commit):

- 40.1 `ui/projects/GitReadiness.kt` (pure) + `GitControlViewModel.refresh()`
  composing it + the readiness row in `GitControlView` + the clone dialog's
  inline error (`FileManagerViewModel.cloneError`, `DialogProperties` instead of
  the invented `onShow`).
- 40.2 `ui/projects/GitPushOutcome.kt` (pure parser) + `GitManager.pushCapturing()`
  (one push, git's own bytes) + `PushResultCard` + `lastResult` state.
- 40.3 `ui/projects/GitHubPublish.kt` + `GitHubPublishApi.kt` + `GitManager.addRemote/`
  `hasRemote/remoteUrl` + `publishToGitHub()`/`attachRemoteToGitHub()` +
  `PublishToGitHubDialog` (private by default, browser fallback offered).
- Tests: `app/src/test/java/com/codeci/ide/GitHubPhase40Test.kt` (36 host cases,
  36/36 green on a local JVM before the push). Tooling:
  `scripts/ci_annotations.py` (read a red run's annotations from the sandbox).

## What exists today (evidence, read on 2026-09-10)

- **The engine is solid.** `ui/projects/GitManager.kt` (935 LOC) runs the
  userland `git` binary with a cleared environment, `GIT_TERMINAL_PROMPT=0`,
  an askpass helper that reads the token **from the env** (never written to
  disk), redaction of the token in every line (`GitRedactor`), local/network
  timeouts (60 s / 300 s) with a poll loop, and `GitCommandException` carrying
  exit code + redacted output. `GitErrors.kt` (220 LOC, Phase 17) already maps
  12 failure kinds — `NOT_INSTALLED`, `NO_TOKEN`, `AUTH_FAILED`, `OFFLINE`,
  `REJECTED`, `NO_UPSTREAM`, `TIMEOUT`, … — into a message plus a help URL.
- **Upstream handling exists.** `pushHandlingUpstream()` reads
  `git status --porcelain=v1 -b`, and when there is no upstream it pushes
  `--set-upstream <remote> <branch>`; `GitBranchOps.SwitchBranchResult` carries
  `published` / `publishError`, so a new branch created in the app *is*
  published, and the sheet says "· published to GitHub" or "· not on GitHub
  yet: <reason>".
- **The hub card already shows a badge**: `ProjectsHubEntry.unpublished` /
  `unpushed` render `↑` / `↑3` on the project card (`FileManagerScreen:1268`).

## …and the three holes that produce exactly what the owner reported

1. **Nothing checks readiness before acting.** `GitManager.isAvailable()`
   (binary present, executable, `git --version` OK) exists **with zero call
   sites** in the app. `GitContext.manager()` returns `null` when
   `$PREFIX/bin/git` is absent, and `FileManagerViewModel.cloneFromGitHub`
   turns that into `error(git_not_installed_message)` — correct text, wrong
   place (next hole).
2. **The failure message can be rendered where the user cannot see it.**
   `cloneFromGitHub` reports through `_userMessage`, which `FileManagerScreen`
   shows in the **SnackbarHost** — while the clone `AlertDialog` stays open:
   `showCloneDialog = false` is only in the *success* callback. A dialog owns a
   separate window above the snackbar, so a failure is literally "an error in
   the background i can't see it": the dialog just stops being busy. (Push/
   commit inside `GitControlSheet` does surface `pushError`, but only while
   that sheet stays open — after it is dismissed the state is gone.)
3. **"Push stayed local" has four different causes and one shared silence.**
   (a) no credentials → git dies with `could not read Username`, mapped to
   `NO_TOKEN` but only *after* the attempt; (b) a token without Contents:write
   → `REJECTED`/`AUTH_FAILED`; (c) **the project has no remote at all** —
   `firstRemote()` falls back to the literal `"origin"` and the push fails with
   `'origin' does not exist`, with nothing offering to create the repository;
   (d) pushed to a *different* branch name than the user expects, since
   `push()` with `setUpstream = false` pushes only the current branch's
   upstream. None of the four is *pre-announced*, and (c) has no remedy at all.

## Research that shaped the design

Full dossier: [`../PHASE38_43_OSS_RESEARCH.md`](../PHASE38_43_OSS_RESEARCH.md) §1.
Decisions: **keep the CLI engine** (JGit rejected on Java-11 BREE vs API 24,
weight, and forked semantics); **`POST /user/repos` for publish**, with the
fine-grained-token caveat recorded (it needs *Administration: write* and an
all-repositories scope, else the UI must say "create it in the browser and
paste the URL"); **`X-Accepted-GitHub-Permissions` is the answer to "which
permission am I missing"** — we surface GitHub's own words instead of
inventing a guess; VS Code / GitHub Desktop as *behaviour* reference for
"publish = an explicit state with a one-button remedy".

## Exit condition (owner's device, one GitHub account, no server changes)

```text
1. Git NOT installed (fresh app, no Modules → Git): the clone dialog and the
   Source Control sheet both say so BEFORE a tap, with an "Install Git" action
   that opens Modules with Git preselected. Nothing hangs, nothing spins.
2. Clone with a bad URL / no network / wrong token: the message appears inside
   the dialog, and the dialog closes. A failure is never only in the snackbar.
3. A project with no remote: "Publish to GitHub" creates the repo (or states
   the exact missing permission from GitHub's reply), adds the remote, pushes,
   and the sheet shows `main → github.com/<owner>/<repo>` with the short SHA.
4. Create a branch, commit, push: `git ls-remote` from the same repo shows the
   branch, the hub badge clears, and the sheet says which branch was published.
5. "Everything up-to-date" is reported as itself — not as a success that
   pushed something.
PASS = all five on the owner's device (3 needs a real repo creation).
```

## Risks to watch (multi-device round)

- **Provider-of-truth drift**: `unpushed`/`unpublished` are computed in
  `loadProjects` (hub) and in `GitControlViewModel.refresh` (sheet). One
  shared pure projection must produce both, or they will disagree — that is
  the exact bug class Phase 37 pinned with `ServerEndpoints`/`ServerRegistry`.
- **Rate limits / API errors**: unauthenticated `api.github.com` is 60 req/h
  per IP; a 403 with a rate-limit body must not look like a token problem.
- **Shallow clones** (`--depth 1`, Phase 15): pushing from a shallow clone is
  legal but git can refuse on some servers (`shallowupdate`); the outcome
  parser must recognise that message and not blame the token.
- **Private-repo cloning** with a fine-grained token scoped to other repos →
  404 from git, which reads like "repo does not exist". `GitErrors` must keep
  those distinct.
- **Old git versions** in the userland prefix: `--porcelain=v1` and
  `push --set-upstream` are safe; `git restore`/`git switch` deliberately are
  not used anywhere (Phase 15 law) — new code must not start.

## Deferred, recorded on purpose

- **SSH remotes / keys on device** — no agent, and `~/.ssh` permission games on
  Android; HTTPS + stored token stays the only supported transport.
- **GitHub OAuth device flow** — needs a client id baked in and a browser
  dance; a pasted token is smaller, revocable by the user, and already the
  model Settings teaches.
- **The `gh` CLI module** — exists in the catalog and works in the terminal;
  not used by the app, because its output is not a stable parsing target and
  two REST calls do not justify the dependency.
- **Rewriting history** (rebase/squash/amend, force-push) — CodeC keeps
  refusing; a phone is not where an irreversible git command belongs.
