# CodeC Phase 40.3 — Publish to GitHub: create the remote when there isn't one

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"Github integration update now Github is working
> but it's not user friendly"* — the sharpest instance of that is a local
> project with nowhere to push.

## Symptom

Today a project becomes a git repo (Phase 14/17 auto-init) and the Source
Control sheet offers COMMIT & PUSH. If the project has no remote, the push
fails with git's `fatal: 'origin' does not exist` — mapped by `GitErrors` to a
generic rejection — and the user is told nothing actionable: they must open a
browser, create a repository, copy its URL, add a remote in a terminal, and
come back. That is four apps for one intention ("put this project on GitHub"),
and it is why the owner's experience reads as "push stays local".

VS Code and GitHub Desktop both solve it with one button: **Publish to
GitHub** — create the repository, add the remote, push the current branch,
done. This part builds that, in CodeC's shape.

## Design

**One HTTPS call, no client library.** `ui/projects/GitHubPublish.kt` holds
only pure request/response logic:

```kotlin
object GitHubPublish {
    fun nameFor(projectName: String): String        // GitHub-safe: [A-Za-z0-9._-], ≤100, no leading/trailing . -
    fun body(name: String, description: String?, private: Boolean): String   // JSON, hand-rolled like CodecJsonParser
    fun parseCreateResponse(json: String): Either<Published, ApiError>       // html_url + ssh_url + default_branch + private
    fun parseError(code: Int, body: String?, acceptedPermissionsHeader: String?): ApiError
    fun remoteUrlFor(htmlUrl: String): String       // always the https form: https://github.com/o/r.git
}
```

`ApiError` kinds are the ones that actually happen and each carries a
next step, not a code: `TOKEN_MISSING`, `PERMISSION_MISSING(needs)` ← built
from GitHub's **`X-Accepted-GitHub-Permissions`** response header,
`NAME_TAKEN` (422 `name already exists on this account`), `RATE_LIMITED`
(403 + `X-RateLimit-Remaining: 0`), `OFFLINE`, `SERVER`.

The transport is one `HttpURLConnection` call
(`POST https://api.github.com/user/repos`, `Authorization: Bearer <token>`,
`Accept: application/vnd.github+json`, `X-GitHub-Api-Version`) on
`Dispatchers.IO`, in a thin Android-side object
(`GitHubPublishApi`) — the same shape `ApkUpdateManager` already uses, so no
new dependency, no OkHttp, no coroutines-test gymnastics. **The token is read
from `GitCredentialsStore` for the request and never written to disk, never
logged, and any error body passes through `GitRedactor` before it can reach a
`String`.**

**Then three git commands, all of which already exist.**
`git remote add origin <url>` → `git push --set-upstream origin <branch>` →
`git status` to refresh. No new `GitManager` surface beyond a
`addRemote(root, name, url)` and `hasRemote(root, name)` (both are one
`runGit` call, and they must go through the existing `exec`/timeout/redaction
path, not a new `ProcessBuilder`).

**Safety rules that are the actual design:**

1. **Publish never deletes and never renames.** It only *creates* and
   *attaches*. If the project already has a remote, the row becomes
   "Push `main` to `github.com/…`" instead (never silently re-point `origin`).
2. **Private by default.** A phone IDE pushing a folder named `school`,
   `taxes` or `notes` to a public repository is a data leak; the dialog's
   toggle starts **Private: on**, mirrors whatever the token's default is not,
   and the row shows which choice was made. (GitHub's own default for
   `POST /user/repos` is public — `private` must be sent explicitly.)
3. **Name collision is a conversation, not an error.** On 422 the dialog
   offers `name-2`, `name-3`, or "use the repo that exists" (which then runs
   `git ls-remote` to *verify* the user owns/can write it before attaching —
   never assume from the name).
4. **The browser fallback is a first-class path, not a consolation.** When the
   stored token cannot create repos (a fine-grained token limited to specific
   repositories — GitHub's docs require *Administration: write* for this
   endpoint), the sheet says: `Your token can't create repositories` + the
   header's own words + two buttons: `[Open github.com/new]` (via the existing
   `OpenInBrowser`) and `[I made one — paste the URL]`, which then validates
   the URL with `GitManager.isCloneableUrl`-adjacent rules, runs
   `ls-remote`, and attaches. This branch is *required* to work, so a user
   with any token type is never stuck.
5. **No `auto-init`, no force-push, no `.gitignore` writing.** CodeC does not
   decide the user's history or their ignore file here. (Publishing a repo
   with `auto_init: true` would make the first push a non-fast-forward — a
   foot-gun that must be refused in code, not documented away.)

**Where the row lives.** The Source Control sheet's empty state (repo with no
remote) and the project card's ⋮ menu, both labelled **Publish to GitHub**.
The Projects Hub "add" sheet does *not* get it (that surface is for getting
code *in*).

## Exit condition

```text
(Owner's device + a real GitHub account; no server-side changes)
1. A local-only project: Publish to GitHub, name defaulted from the project,
   Private on → tap Publish → the repo exists on github.com as a PRIVATE repo,
   `origin` is set, `main` is pushed, and the sheet shows
   `main → github.com/<owner>/<name> (short SHA)`. Opening the URL in the
   phone's browser shows the files (Phase 37's 🌐 path is the check).
2. Same flow, but the token cannot create repos: the exact permission GitHub
   asked for is on screen, and the "paste the URL" path completes the publish.
3. Name already taken: the dialog offers a free name and can adopt the
   existing repo only after `git ls-remote` proves it is reachable/writable.
4. Offline / airplane mode mid-publish: the created-repo step is
   idempotent-friendly — a retry does not create `name-2`, and the remote is
   not half-added (either both steps land or the state says what is partial
   and offers to finish it).
5. A project that already has `origin`: no Publish row; instead "Push
   <branch> to <host>" from 40.2.
PASS = all five, plus the owner's judgement that the wording told them what
was happening at every step.
```

## Tests (plan)

- `GitHubPublishTest` (host, pure): `nameFor` (spaces/emoji/`..`/length →
  GitHub-legal or null, never a silent truncation into a *different* repo
  name); JSON body (private true/false, description escaping incl. `"` and
  `\n`); `remoteUrlFor` (strips a trailing `/`, refuses `git@…` and
  `ssh://…` for the stored remote — CodeC's auth is HTTPS+askpass); 422
  `name already exists`; 401; 403-with-header → `PERMISSION_MISSING` with the
  header's contents; 403 rate-limit vs permission (distinguished by
  `X-RateLimit-Remaining`, not by message text); malformed JSON → `SERVER`
  without throwing.
- `GitHubPublishRedactionTest`: a fake token appearing in the request
  headers must never appear in `ApiError.message`/`detail`, including when the
  API echoes the Authorization in an error body.
- `PublishStateFlowTest` (Robolectric, VM level): partial state (repo created
  + remote added + push failed) reports the *push* failure and offers Retry
  push — never re-creates the repo; `hasRemote` short-circuits Publish.
- CI-only, deliberately not automated: the real repository creation (needs a
  live token and must not run from CI).

## Sources (record)

- GitHub REST docs, *Create a repository for the authenticated user*
  (`POST /user/repos`): classic-token scopes `public_repo`/`repo`;
  fine-grained PATs are supported **with "Administration" repository
  permissions (write)**; `name already exists on this account` is the 422
  body — [docs.github.com/en/rest/repos/repos].
- Same page family: GitHub's documented way to find a missing fine-grained
  permission is the **`X-Accepted-GitHub-Permissions`** response header —
  [docs.github.com/en/rest/repos/repos + the "Common errors and fixes"
  summary in getknit.dev's PAT guide (2026-08)].
- `rule.md` §6 (clean-room) and the existing in-app precedent for hand-rolled
  HTTP + JSON without a dependency: `ApkUpdateManager.kt` (HttpURLConnection,
  `org.json`) and `CodecJsonParser.kt` (a hand-written parser for
  `.codec.json`).
- CodeC code read 2026-09-10: `GitCredentialsStore` (token storage shape),
  `GitManager.firstRemote/rmCached/exec`, `GitRedactor`,
  `OpenInBrowser.kt` (Phase 37 follow-up — reused for the browser step),
  `ProjectPathUtils.sanitizeProjectName` (name rules to align with).

## Deferred / rejected with reasons

- **OAuth device flow** (GitHub App, no pasted token) — better UX in theory,
  but it needs a client id in the APK, a browser round-trip, and a token
  refresh story; the paste-a-token model already exists in Settings and a
  fine-grained token is what the owner's own workflow uses. Revisit if
  Phases 40+ show real confusion in the round.
- **GitHub App installation / org repos** — out of scope for a phone IDE.
- **Creating the repo with a template or `auto_init: true`** — rejected in
  §Design rule 5; a non-empty remote makes the first push fail.
- **Deleting/archiving a repo from the app** — never; that operation belongs
  to a desktop with a confirm-typing dialog.
- **Reusing `gh` (Modules → GitHub CLI)** — a fine tool for the user's
  terminal, but shelling out to parse `gh` output for a UI state adds a second
  dependency-free-but-unstable contract; the single REST call is testable on
  the host, which is the law here.
