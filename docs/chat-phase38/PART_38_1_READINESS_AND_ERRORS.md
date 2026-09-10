# CodeC Phase 38.1 — Readiness gate + errors that cannot be missed

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S/M ·
> **Owner row (verbatim):** *"…if i try to clone a repo and didn't download the
> git it shows error in the background i can't see it"*

## Symptom

The app is *reactive* about GitHub: it attempts the operation, git fails, and a
friendly message is produced — into a surface the user may not be looking at.
Two concrete defects in the current code, both read on 2026-09-10:

1. `GitManager.isAvailable()` is **dead code** (no call sites). The only
   git-presence signal is `GitContext.manager() == null`, reached *after* the
   user tapped Clone, and the resulting string
   (`R.string.git_not_installed_message`) is routed to `_userMessage` →
   SnackbarHost.
2. `FileManagerScreen`'s clone `AlertDialog` is dismissed **only in the success
   callback** (`showCloneDialog = false` inside `onCloned`). On failure the
   dialog is still open, and it draws over the snackbar → the message exists
   and the user never sees it. This is the bug the owner described, word for
   word.

## Design

**One pure readiness model, asked by every surface.** New
`ui/projects/GitReadiness.kt`, Android-free:

```kotlin
enum class GitBlocker { GIT_NOT_INSTALLED, NO_TOKEN, NO_REPOSITORY, NO_REMOTE, NO_NETWORK, OFFLINE }
data class GitReadiness(val gitInstalled: Boolean, val hasToken: Boolean,
                        val isRepository: Boolean, val remoteUrl: String?,
                        val online: Boolean) {
    /** null == ready to act. Non-null == what to tell the user, with an action. */
    fun blocker(): GitBlocker?
    fun message(): String          // one sentence, same tone as GitErrors
    fun actionId(): String         // INSTALL_GIT | CONNECT_TOKEN | PUBLISH_REPO | RETRY | none
}
```

Ordering is deliberate: the *cheapest, most blocking* answer first
(`GIT_NOT_INSTALLED` before `NO_TOKEN` — installing git is the only fix that
makes every other question meaningful), and `NO_REMOTE` **is not** a blocker
for commit (local commits must keep working offline, which is Phase 14's law
for the whole app) — it is a blocker only for push/pull, which is why
`blocker()` takes the intended operation: `fun blocker(op: GitOp)`.

**Inputs, not queries.** The readiness object is *composed by the caller* from
state it already has (`GitContext.gitBinary()`, `GitCredentialsStore.stored()`,
`GitManager.isRepository(root)`, `firstRemote()`, and the app's existing
network signal — `ConnectivityManager.activeNetworkInfo`-style check already
used by the packages screens; if none exists, `online` is omitted rather than
guessed, and `OFFLINE` never fires on a stale value). No new I/O inside the
pure object, ever.

**Three render points, one text.**
- `GitControlSheet` header: a one-line status row — `GitHub ready` (green) /
  the blocker sentence (amber) — with the action button
  (`Install Git`, `Add token`, `Publish to GitHub`, `Retry`) on the same row.
- The **clone dialog**: an inline error slot inside the dialog's own text
  column, fed by a new `GitOpResult`/`cloneError: String?` in the VM. The
  dialog additionally becomes **non-dismissable while busy** and *closes on
  failure only when the error is rendered inside it* — never a snackbar alone.
  A rule worth pinning in review: *a failure message must live in the window
  the user is looking at.*
- The Projects Hub card: the existing `↑N` / `↑` badge gains a long-press
  tooltip with `blocker().message()` (no new layout, no new colour).

**One-tap install.** `actionId = INSTALL_GIT` navigates to
`Screen.Modules` with the git module preselected. `ModuleViewModel.install(...)`
is `private`, so the phase adds the smallest public entry point that reuses
its exact code path (`fun requestInstall(context, moduleId)`, or a
`pendingInstallModuleId` StateFlow the Modules screen consumes once) — no
parallel installer, no duplicated checksum logic. The `git` entry already
exists (`ModuleCatalog.kt:177`, `binary = "git"`).

**Nothing about this changes the engine.** `GitManager` keeps its timeouts,
redaction and env handling; `GitErrors` keeps classifying what git *said*.
Readiness is the question asked before the fact; `GitErrors` stays the answer
after it. Where both could speak, readiness wins the *first* line and the raw
detail stays behind "Show details" (already the sheet's pattern).

## Exit condition

```text
(Device, fresh install with no git module)
1. Open a project's Source Control sheet with git NOT installed: the sheet says
   "Git isn't installed…" and offers Install Git; tapping it lands on the Git
   row in Modules with the install already requested.
2. Same state, Clone dialog, tap CLONE: the error appears INSIDE the dialog;
   the dialog stays usable; no snackbar-only failure anywhere in the app.
3. Add a token in Settings → the sheet's line changes to "ready" without a
   restart (readiness is recomputed from the same StateFlows, not cached).
4. Offline (airplane mode): push/pull state "offline" and local commit still
   works; a stale `online` value never produces a false "offline".
PASS = all four.
```

## Tests (plan)

- `GitReadinessTest` (host, pure): ordering (installed → token → repo →
  remote), per-`GitOp` gating (commit allowed without a remote; push is not),
  `NO_TOKEN` only when the op needs credentials (a public-repo clone must NOT
  be blocked by a missing token — that is current correct behaviour and must
  not regress), message/action pairing for every `GitBlocker`, and `OFFLINE`
  never firing when `online == null`.
- `GitErrorsTest` extension: the readiness text and the post-failure text are
  not duplicated strings (one source per case), and `GIT_NOT_INSTALLED` still
  maps from git's raw output for users who bypass the gate (terminal).
- Compile-checked in Compose by CI (no Robolectric for the sheet): the dialog
  slot is a `Text?` parameter, and the *behaviour* under test — "failure never
  surfaces only as a snackbar" — is enforced by a structural rule in the VM:
  `cloneFromGitHub` reports through `cloneError` (state) **and** keeps
  `_userMessage` only for the success line. That inversion is itself the
  regression pin; a test asserts the VM sets `cloneError` and clears
  `isBusy` on the failure path (Robolectric, no Compose).

## Sources (record)

- CodeC's own code, read 2026-09-10: `GitManager.kt` (`isAvailable`
  uncalled), `GitContext.kt` (`manager()` null path),
  `FileManagerViewModel.cloneFromGitHub` (error → `_userMessage`),
  `FileManagerScreen.kt` (`showCloneDialog` closed only on success;
  SnackbarHost `LaunchedEffect(userMessage)`), `ModuleCatalog.kt:177`,
  `ModuleViewModel.install` (private), `strings.xml`
  (`git_not_installed_message`, `clone_failed`).
- [`../PHASE38_43_OSS_RESEARCH.md`](../PHASE38_43_OSS_RESEARCH.md) §1 —
  JGit rejected; CLI stays; VS Code/GitHub Desktop "publish is a state"
  behaviour reference.

## Deferred / rejected with reasons

- **A blocking "GitHub setup" wizard on first run** — the app must stay usable
  without GitHub; a readiness *line* costs nothing and a wall costs installs.
- **Probing `git --version` on every screen** — `isAvailable()` spawns a
  process; it is called once per readiness computation and cached per
  `ShellBootstrap.prepare()` result, never per recomposition.
- **Auto-installing the git module silently** — a network download started by
  a *button labelled Clone* would betray the user's model of the app; the
  action is explicit.
