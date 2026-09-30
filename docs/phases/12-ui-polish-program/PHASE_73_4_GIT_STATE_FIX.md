# Phase 73.4 — device fix: git-refresh mis-reported "installed" after any error

**Status: ✅ IMPLEMENTED (2026-09-29), CI ✅ GREEN `36551505966` on tip `b49e726`; session
branch `arena/01a0eb2d-codec`. Owner device pass ✅ 2026-09-29, merged via PR #95. A real bug fix on
top of 73.1/73.2/73.3, found from the owner's own device report — not a new discussion draft.**

## The owner's report (verbatim, 2026-09-29)

> "Auto install not working"

Follow-up, after being asked where exactly this happened:

> selected: tapped Install Git in the Git panel, never checked Terminal separately — the
> bug report is about what the Git panel itself showed after tapping Install.
>
> "I click the initialize in the repo no installed git"

Read together: the owner opened the Source Control sheet on a project git had never touched,
and instead of the "Install Git" guidance, the sheet offered **"Initialize repository"** — the
73.3 button that should only ever appear once git is confirmed installed. Tapping it did not
produce a working repository, because git genuinely was not usable on the device.

## Root cause (source-read, no device access this session)

`GitControlViewModel.refresh()` wrapped its entire body — from acquiring a `GitManager` all
the way through running `git status` — in **one** `try`/`catch`. The `catch` unconditionally
set `gitInstalled = true` and never touched `isRepo`:

```kotlin
} catch (e: Exception) {
    _state.value = _state.value.copy(
        loading = false,
        gitInstalled = true,               // ← always, regardless of what threw
        message = friendly(e, hasToken = false).display()
    )
}
```

`GitContext.manager()` only reaches shell/credential setup (`ShellBootstrap.prepare()` —
writing `cc`/`pkg`/the profile script, extracting the TCC bundle; `GitCredentialsStore.stored()`
— reading stored credentials) **after** it has already confirmed a `git` binary exists on disk.
None of that has anything to do with whether git itself is installed. But if any of it threw —
a profile-script write failing, a credential read failing, anything — the exception landed in
the same `catch`, which then declared `gitInstalled = true` anyway. Meanwhile `isRepo` was
never assigned in that `catch`, so it stayed at whatever `_state.value` already held — the
`UiState` data class default, `false`, for a sheet that had never successfully refreshed before.

Result: `gitInstalled = true, isRepo = false` — precisely the state that renders "Initialize
repository" (`!state.gitInstalled` false, `!state.isRepo` true; see the `when` block in
`GitControlView.kt`) instead of "Install Git" — for a device where git was never actually
confirmed working. The owner never saw the auto-install button at all; that is why "auto
install" looked broken, and why "Initialize" was the only thing on screen to tap.

## The fix

`refresh()` now wraps `gitContext(context).manager()` in its **own** `try`/`catch`, separate
from the git-invoking work below it:

- **Acquiring the manager throws** → report `gitInstalled = false, isRepo = false` (the same
  shape as the existing "git is null" branch) instead of guessing it succeeded. A device that
  cannot even get git into a usable state is told "not installed", which routes it to the
  Install Git button — the worst case is now an extra tap on Install, not a dead-end button
  that goes nowhere.
- **The manager was acquired, but running git later throws** (`git.isRepository`/`git.status`
  etc.) → `gitInstalled = true` is legitimate here (a `GitManager` exists), but `isRepo` is
  now **re-derived** with a fresh, plain filesystem check
  (`git.isRepository(projectRoot)` — the same one-liner `File(root, ".git").isDirectory`
  `GitManager.isRepository()` already is, so it cannot fail the same way the exception above
  just did) instead of being left stale.

Both catches still classify the exception through the same `friendly()` helper 73.2 already
uses, so the message text is unaffected.

## Tests

`GitRefreshStateWiringTest.kt` (new, 5 cases) — a source-scan wiring test in this repo's
established style (`RepoFiles.mainSource`, no Robolectric/coroutine harness exists for this
ViewModel): pins that manager acquisition is its own try/catch reporting not-installed on
failure, that the null-manager branch is untouched, that the git-status catch re-derives
`isRepo` via a fresh filesystem check instead of leaving it stale, and that the old one-liner
bug (`gitInstalled = true` with no `isRepo` touch) is gone. Every assertion was hand-verified
against the real file with a small Python script mirroring Kotlin's `substringAfter`/
`substringBefore`/`substringAfterLast` semantics before being trusted (this session's own
lesson from the 73.3 CI round — see `docs/getting-started`).

## What this does **not** claim to fix

This fix corrects the **state-reporting bug** that let the wrong button show. It cannot, from
source alone, confirm or rule out *why* `gitContext(context).manager()` (or the git status call)
threw on the owner's specific device — that requires the owner's own device logs (Logcat around
the moment "Install Git" was tapped, or the moment the sheet was opened) to diagnose further if
git truly still fails to install after this fix. If the owner re-tests and still cannot get a
working git after tapping Install (not just "the wrong button showed"), the next step is a
`pkg install -y git` run captured from Terminal directly (exit code + full output) so the real
failure (network, a broken dependency, storage) can be read instead of guessed at.

## Evidence

- **CI: ✅ GREEN.** `Build APK` run `36551505966` on tip `b49e726` — both APKs assembled
  (`CodeC-IDE-1.3.17-universal.apk` / `-debug.apk`), all host unit tests passed, including the
  new `GitRefreshStateWiringTest.kt`'s 5 cases whose exact assertions were hand-verified against
  the real file (a Python mirror of Kotlin's substring semantics) before this push — this is the
  first CI round for this fix, and it went green on the first try.
- **Device:** not run this session; none claimed.
