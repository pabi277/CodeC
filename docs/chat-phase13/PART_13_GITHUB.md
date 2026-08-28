# CodeC Phase 13 — GitHub & Git Version Control Integration

**Status:** Planned · **Cost:** `[client-only]` · **Depends on:** Phase 8 (Projects & Folder Tree) + Phase 11 (Output Feedback)  
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
