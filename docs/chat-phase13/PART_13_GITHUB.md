# CodeC Phase 13 — GitHub & Git Version Control Integration

**Status:** ✅ COMPLETE & DEVICE-ACCEPTED — **PR #31 MERGED to `main` at `006515a` (2026-08-31)** · **Cost:** `[client-only]` · **Depends on:** Phase 8 (Projects & Folder Tree) + Phase 11 (Output Feedback)  
**Target Files:** `GitManager.kt`, `GitControlView.kt`, `SettingsScreen.kt`, `FileManagerScreen.kt`

---

## 1. Context & Motivation

Developers coding on mobile frequently collaborate on GitHub repositories. While `git` and `gh` are available in the terminal via `pkg install git gh`, having a visual Git interface directly in the IDE streamlines the clone/edit/commit/push workflow:
- 1-tap repository cloning from GitHub URL.
- Visual file diff and staging list (modified, added, deleted files).
- 1-tap Commit & Push with descriptive commit messages.
- Secure token storage that avoids manual authentication prompts on every push.

---

## 2. Architectural Design (Decision D1)

### 2.1 Git Operations Engine (`GitManager.kt`)
- Interfaces with the native `git` binary installed in `$PREFIX/bin/git`.
- Commands executed safely via process runner with private environment credentials:
  - `git clone <url> <dest>`
  - `git status --porcelain` (parses file state)
  - `git diff <file>`
  - `git add <files>`
  - `git commit -m <message>`
  - `git push`
  - `git pull`
  - `git branch` / `git checkout`

### 2.2 Secure Credential Storage
- GitHub Personal Access Token (PAT) stored in app-private storage.
- Injected into git operations via `GIT_ASKPASS` helper script or HTTPS credential helper:
  `https://oauth2:<TOKEN>@github.com/...` (redacted from logs).
- "GitHub Account" card in Settings:
  - Connect GitHub Token.
  - Set default author name & email (`user.name`, `user.email`).
  - Disconnect / revoke stored credentials.

### 2.3 Visual Source Control Pane
- Embedded in `FileManagerScreen` or dedicated Git side panel:
  - Branch label (e.g. `main` or `feature/login`).
  - Changes list:
    - Modified (`M`), Added (`A`), Deleted (`D`), Untracked (`?`).
    - Tapping a modified file opens visual side-by-side or inline Diff Viewer.
  - Commit Message TextField + `COMMIT & PUSH` button.
  - Pull button with fetch status.

---

## 3. Implementation Steps

1. **Step 1:** Create `GitManager.kt` handling git CLI execution, status parsing, and credential injection.
2. **Step 2:** Build `GitControlView.kt` with changed files list and commit interface.
3. **Step 3:** Implement GitHub Token configuration card in `SettingsScreen.kt`.
4. **Step 4:** Build "Clone Repository" modal dialog in `FileManagerScreen.kt`.
5. **Step 5:** Write unit tests in `GitStatusParserTest.kt` for git status parsing and credential scrubbing.

---

## 4. Exit Condition & Verification Recipe

A fresh APK passes the following recipe on device:

```sh
# Setup & Git Integration Test
# 1. In Settings, configure GitHub Username and PAT Token.
# 2. In Files tab, tap "Clone from GitHub" -> enter "https://github.com/pabi277/CodeC" (or a test repo).
# 3. Observe repository clones directly into a new project folder.
# 4. Open "README.md" in Editor -> make a small edit -> save file.
# 5. Open Git Source Control pane -> Observe "README.md" listed under Modified (M).
# 6. Type commit message "docs: test mobile commit" -> Tap "COMMIT & PUSH".
# 7. Observe Git push completes successfully with green confirmation toast.
# PASS
```

---

## 5. Security & Invariants

- Personal Access Tokens must never be exported to unconfined terminal environment variables or logged in `AppLogger`.
- All Git operations run with project path confinement.

---

## 6. Implementation record (2026-08-30)

All client-side work is committed on the session branch. CI (`Build APK`)
executes the new host unit tests; device rounds below remain the exit gate.

### Files

| File | Role |
|---|---|
| `ui/projects/GitManager.kt` | Android-free git CLI engine: argv-list `ProcessBuilder` (no shell), porcelain `status -b` parser (`GitStatusParser`), change model (`GitFileChange`/`GitStatus`), secret scrubbing (`GitRedactor`), askpass script generator, timeouts (60 s local / 300 s network, exit 124) |
| `ui/projects/GitDiff.kt` | Pure-Kotlin line diff (`DiffEngine`): common prefix/suffix trim + LCS, oversized middles fall back to whole-block replace |
| `ui/projects/GitCredentialsStore.kt` | App-private DataStore: token, GitHub username, commit name/email; `clearCredentials()` disconnects while keeping the author identity |
| `ui/projects/GitContext.kt` | Context-aware factory: resolves `$PREFIX/bin/git`, the `ShellBootstrap.prepare()` environment, askpass file, stored credentials → `GitManager` (null ⇒ git not installed) |
| `ui/viewmodels/GitControlViewModel.kt` | Pane state: status/diff/commit-push/pull, per-step messages |
| `ui/screens/GitControlView.kt` | Source Control bottom sheet + inline diff dialog |
| `FileManagerScreen` / `FileManagerViewModel` | ⋮ → **Clone from GitHub** dialog (URL + name, suggested from the URL), ⋮ → **Source Control** entry for an open project |
| `SettingsScreen` | **GitHub Account** card: masked token (SHOW/HIDE), username, commit name/email, SAVE / DISCONNECT |
| Tests | `GitStatusParserTest` (17), `DiffEngineTest` (10), `GitManagerTest` (10, real processes via a fake `git` script) — 37 new host tests |

### Design decisions

- **D1 — engine stays Android-free and shell-free.** Like `ExecutionRunner`,
  `GitManager` takes the binary/env from the caller; commands go to
  `ProcessBuilder` as an argv list, so no URL/branch/message can inject shell
  words and everything is host-testable.
- **D2 — token transport = `GIT_ASKPASS` + per-child env.** The token lives
  only in the git child's environment (`CODEC_GIT_TOKEN`) read back by the
  askpass script (`#!/system/bin/sh` — always present on Android; no
  dependency on `$PREFIX`'s shell). Not in argv, not in `.git/config`, not in
  the terminal environment, not written anywhere on disk beyond the
  app-private DataStore. `GIT_TERMINAL_PROMPT=0` prevents hangs;
  `GIT_MERGE_AUTOEDIT=no` prevents editor blocks on pull. Every git output
  line and error message is scrubbed by `GitRedactor` (token literal +
  `user:password@` URL credentials) before UI/AppLogger.
- **D3 — whole-tree staging.** The pane stages with `git add -A`; no partial
  staging UI in 13.1 (follow-up candidate). Commit identity falls back to
  `CodeC <codec@localhost>` so a commit can never hard-fail on missing
  identity.
- **D4 — one-tap COMMIT & PUSH with honest results.** Commit runs first; a
  push failure (offline, no token, rejected) is reported *in addition* to the
  commit success ("Committed ✓ — push failed: …") instead of hiding it.
- **D5 — clone lands in the projects root.** Same flow as the Phase 8 ZIP
  import: uniquely named folder (suffix `_2`, `_3`, …), `.codec/project.json`
  ensured, project opened on success, partial clone deleted on failure.
  Only `http(s)://` URLs are accepted (no local paths, no scp syntax).
- **D6 — diff computed in Kotlin, not parsed from git.** Old side =
  `git --no-pager show HEAD:<path>` (empty for new files), new side = working
  tree; `DiffEngine` renders +/-/context lines. No pager risk, theme-aware
  colors, host-testable.
- **D7 — git resolution is `$PREFIX/bin/git` only** (never system git; no
  `.` on PATH — the env comes from `ShellEnvironment.buildEnv`). Missing git
  ⇒ actionable guidance ("Modules tab → Git, or `pkg install -y git`").

### Invariant check

No `.` on PATH (unchanged `buildEnv`); nothing written into `$PREFIX/bin`;
`cc`/bash/TCC link order untouched; no bootstrap/republish involved
(client-only); terminal sessions never receive the token.

---

## 7. Device verification recipe (exit gate)

1. **Install git** if needed: Modules tab → Git → Install (or terminal
   `pkg install -y git`).
2. **Settings → GitHub Account:** paste a fine-grained PAT (contents
   read/write), username, commit name/email → **SAVE** → toast + "Connected
   (user · ••••xxxx)".
3. **Files → ⋮ → Clone from GitHub:** enter `https://github.com/pabi277/CodeC`
   (or any small test repo) → name auto-fills → **CLONE** → project opens in
   the tree.
4. Open `README.md` in the editor → make a small edit → save.
5. Files → ⋮ → **Source Control**: branch shows; `README.md` listed with an
   **M** badge.
6. Tap the file → inline diff shows the `-`/`+` lines → close.
7. Type `docs: test mobile commit` → **COMMIT & PUSH** → status
   "Committed & pushed ✓" (verify on github.com).
8. Tap **PULL** → "Pull completed".
9. Regression: open a non-git project → Source Control shows the
   "not a Git repository" guidance; with git uninstalled the pane shows the
   install guidance.
10. Security spot-checks: `env | grep -i token` in the CodeC terminal shows
    nothing; `.git/config` of the cloned project contains no token;
    Settings → Logs contains no token after a failed push (use a wrong token
    once).

**PASS** = steps 2–8 succeed without manual fixes and the step-10 checks stay
clean.

---

## 8. Device acceptance record (2026-08-31) ✅ PASSED

Owner device rounds on aarch64 (scratch repo `pabi277/T` for pushes; the real
`pabi277/CodeC` clone used read-only for status/diff/pull checks):

- **Round 1 (found, not an app bug):** push to `pabi277/CodeC` returned
  `403 Permission … denied` — the token was fine-grained WITHOUT
  Contents write. The pane showed the honest D4 split result
  ("Committed ✓ — push failed: …"), tree stayed consistent, and the owner
  was guided to fix token permissions and use a scratch repo. No code change.
- **Round 2 (all green):** `pkg` git installed; Settings GitHub Account saved;
  Clone from GitHub (`CodeC`, `T`) works; `M README.md` badge + `? .codec/`
  untracked entry appear; tap-to-diff shows the −/+ edit lines;
  **"Committed & pushed ✓"** on `T`; web PULL round trip returns
  "Pull completed" with `HEAD -> main, origin/main` in sync
  (`5447bdc`); `git log` shows the owner's commits.
- **Security spot-checks clean:** `env | grep -i token` empty in the CodeC
  terminal; `T/.git/config` and `CodeC/.git/config` contain plain https URLs
  with **no token**; Settings Logs contain no token material after the 403
  failure (redaction path exercised by the failed push).
- Known follow-up candidates (not blockers): pane stages the whole tree
  (`git add -A`), so clones commit CodeC's `.codec/` metadata folder —
  candidate: skip `.codec/` or seed a `.gitignore`; no partial staging UI (D3).

Phase 13 exit condition MET. **PR #31 MERGED to `main` at `006515a`
(2026-08-31, owner's explicit command).**
