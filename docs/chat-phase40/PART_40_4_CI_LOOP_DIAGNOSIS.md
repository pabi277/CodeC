# CodeC Phase 40.4 — Why Phase 40 failed CI 16 times in a row (and the fix)

> **Status:** ✅ DIAGNOSED + FIXED on `arena/01a08c04-codec` (implementation of
> 40.1/40.2/40.3 in the same commit). **Cost:** `[client-only]` · **Effort:** S
> (diagnosis) / M (re-implementation) · **Owner row (verbatim):** *"It unusually
> failed the workflow several time please diagonise these and give it proper
> solution"*
>
> **Scope note.** This is a *repair* of the Phase 40 work that was attempted on
> `arena/01a08b68-codec`. That branch is 16 commits of broken Kotlin (see the
> evidence table) and was abandoned in place; the fix was implemented from the
> Phase 40 specs on a fresh session branch so the working history stays linear.

## Symptom (evidence, read 2026-09-10)

Sixteen consecutive `Build APK` runs on `arena/01a08b68-codec`, every one red
at the same step, for two hours:

| # | run id | commit | what the annotation said (abridged) |
|---|---|---|---|
| 1 | `34484089568` | `9c39252` initial | `GitHubPublish.kt`: `Unresolved reference 'redactUrls'`, `Cannot import 'redactAll'`, `@JvmStatic` on a member function, `it` unresolved in a `filter`, `Only safe (?.) … on a nullable receiver` |
| 2 | `34484541251` | `48f9af1` | same file, `Conflicting declarations`, `Missing return statement` |
| 3 | `34484849246` | `bb8ab94` | `Cannot extend an object`, `JvmField cannot be applied to a property that overrides…`, `Unresolved reference 'ApiError'` |
| 4 | `34485148848` | `1d361cc` | **an existing file broke**: `FileTreeRepository.kt` — `Unresolved reference 'failure'`, `No type arguments expected for class Result` (a hand-rolled `class Result` in the new file shadowed `kotlin.Result`) |
| 5 | `34485450327` | `b617e3e` | `Unresolved reference 'Either'` (arrow-kt is not a dependency), `Cannot create an instance of an abstract class` (`HttpURLConnection`) |
| 6 | `34485751415` | `3c3bc91` | `GitReadiness.kt`: `Cannot import 'isRepository'`, `Unresolved reference 'isPushPull'`, plus `GitPushOutcome.kt:194 Pair<Long, String>` |
| 7 | `34486047763` | `206b25f` | `Data class must have at least one primary constructor parameter`, `Unresolved reference 'CommitOrPush'` |
| 8 | `34486359978` | `d3d9ea2` | `Data class must have at least one primary constructor parameter` (again), `Expecting a top level declaration` |
| 9 | `34486662678` | `2650a37` | `FileManagerScreen.kt`: `Unresolved reference 'setCancelable'`, `'setCanceledOnTouchOutside'`; `GitReadiness.kt`: `This type is final, so it cannot be extended` |
| 10 | `34486974518` | `b389c9a` | `GitReadiness.kt:33` — 5 syntax errors, `Data class must have at least one…` |
| 11 | `34487285126` | `c9eb303` | `GitReadiness.kt:41` — 5 syntax errors |
| 12 | `34487596441` | `abf6879` | `FileManagerScreen.kt` — `setCancelable` / `setCanceledOnTouchOutside` (the same two errors as #9) |
| 13 | `34487937214` | `75ade9a` | identical `FileManagerScreen.kt` errors |
| 14 | `34488257392` | `8c09802` | identical `FileManagerScreen.kt` errors + a third |
| 15 | `34488919472` | `05ed9e8` | `GitReadiness.kt:14` — 6 syntax errors |
| 16 | `34489229134` | `a3a8b46` (tip) | `GitReadiness.kt:22` — 8 syntax errors; `GitPushOutcome.kt:194 Pair<Long, String>` |

Every run compiled for 1 m 46 s – 2 m 29 s (green runs on this repo take
6–7 min), i.e. it died in the Kotlin front end before anything was tested.

## Root causes, in the order they mattered

### 1. No compile feedback loop — the whole failure was 100 % avoidable
Every single error is a **Kotlin syntax or type error in two brand-new files**.
The loop never compiled anything locally, although `rule.md` §9 (session-tooling
note, 2026-09-06) records that a JRE + kotlinc *are* downloadable in-sandbox for
exactly this. It pushed, waited ~2 minutes, got a red X, then rewrote the same
file by guess.

### 2. The CI failure itself was never read
The errors were **available and unchanged across runs**:

- runs 12–14 print the *same two* `FileManagerScreen.kt` errors three times —
  proof that no run's output was consulted;
- runs 6 and 16 both print `GitPushOutcome.kt:194 … Pair<Long, String>`, ten
  commits apart;
- every run carries ~10 readable annotations (`Gradle failure 1…10`), which the
  sandbox can read — new tool `scripts/ci_annotations.py` does exactly that, and
  the run page is the documented fallback (`rule.md` §10 will point at it).

A red run whose output is not read is not feedback: it is a coin flip with an
audit trail.

### 3. Whole-file rewrites instead of delta fixes
11 of the 15 follow-up commits are *"Rewrite X"* / *"Simplify X"*. Each rewrite
replaced code whose *shape* was fine with new invented syntax, so the compile
error surface **moved instead of shrinking** (`GitHubPublish.kt` → `GitReadiness.kt`
→ `FileManagerScreen.kt` → `GitReadiness.kt` again). Compiler output is a
precise address; rewriting the file a tenth time discards it.

### 4. APIs were invented rather than looked up
`import …redactAll`, `redactUrls` as a top-level function, `Either`, a local
`class Result`, `object X : ApiError()`, `@JvmStatic`/`@JvmField` on functions
and overrides, Compose `AlertDialog(onShow = { dialog -> dialog.setCancelable(…) })`
— none of these exist. The real pieces were already in the repo:
`GitRedactor(secret).redact/redactAll`, `HttpURLConnection` via
`URL(url).openConnection()`, `DialogProperties(dismissOnBackPress = …, …)`, and
`GitErrorKind`/`GitErrors`.

### 5. A broken localisation of the loop, not the intent
The phase spec itself is good and was not the problem: 40.1 (readiness +
errors-in-the-window), 40.2 (push truth) and 40.3 (publish) are sound designs
(see the part docs). What failed is *engineering discipline*, and one tooling
gap helped it: the workflow's annotation-surfacing step after the app build
only reads the **bench** log, so the app's real errors are visible only as
check-run annotations (GitHub UI / run-page HTML) — never in the sandbox's
"logs" path. That gap is now closed by `scripts/ci_annotations.py`.

## The fix (this branch)

1. **A local pre-validation loop for the Android-free half of the app.**
   `pip install --break-system-packages --user jdk4py` (Temurin 25) +
   `npm install kotlin-compiler@2.4.10`, then `kotlinc` over the real
   `ui/projects/*.kt` production files plus two tiny shims
   (`WebFileSupport.isHtml`, `LanguageRegistry.forFile`). Empty output ==
   compiles.
2. **The real tests run on the host JVM.** `org.junit.*` shimmed (annotation +
   `Assert`) and the repo's real `@Test` methods invoked reflectively — the
   §9 route. Result: **36/36 pass**, and the loop caught four real bugs
   *before* CI:
   - `GitPushParser` truncated `feature/x` to `x` (a `substringAfterLast('/')`
     on a refspec destination that is already a ref name);
   - `GitHubPublish.nameFor("café note")` produced `caf--note` (double dash);
   - a fixture used `e4f5g6h` — not valid hex, so it could never match a real
     abbreviated sha (and force-push `...` refspecs were unparsed);
   - `GitManager.isSafeRemoteUrl("https://github.com/u/r.git; rm -rf /")` was
     `true` — a substring `find`-style check; now full-string anchored with a
     rejected-character set.
3. **Re-implementation against verified APIs**, from the specs, on clean files:
   - `ui/projects/GitReadiness.kt` — `GitOp`/`GitBlocker` + `blocker/isReady/`
     `message/actionId`, ordering git → offline → repo → token → remote,
     `online = null` never becomes `OFFLINE`;
   - `ui/projects/GitPushOutcome.kt` — `PushOutcome` + `GitPushParser` pinned to
     git's real output (`[new branch]`, `old..new`, `old...new`, `[rejected]`
     reasons, `GH006`, shallow, no-upstream, no-remote, auth, unknown→`Failed`);
   - `ui/projects/GitHubPublish.kt` + `GitHubPublishApi.kt` — `POST /user/repos`
     with private-by-default, `X-Accepted-GitHub-Permissions` surfaced, error
     bodies read from `errorStream` and passed through `GitRedactor`;
   - `GitManager`: `pushCapturing()` (returns `GitPushAttempt(exitCode, stdout,
     stderr)` instead of throwing — the spec's "parse git's own bytes"),
     `hasRemote`/`remoteNames`/`remoteUrl`/`addRemote` (never re-points),
     `isSafeRemoteUrl`;
   - `GitControlViewModel`: readiness composed in `refresh()`, ONE push per
     action parsed into `lastResult`, `publishToGitHub()` +
     `attachRemoteToGitHub()`, and the result card survives the refresh that
     follows the operation (cleared by an explicit REFRESH or the ✕);
   - `GitControlView`: the readiness row (with `PUBLISH` / token link), the
     `PushResultCard`, and `PublishToGitHubDialog`;
   - the clone dialog (40.1's original symptom): inline error inside the
     dialog's own text column, non-dismissable while busy, via
     `DialogProperties` — not the invented `onShow`.
4. **Tests:** `app/src/test/java/com/codeci/ide/GitHubPhase40Test.kt` — 36 host
   cases (readiness truth table, push-parser fixtures incl. force-push and
   slashed branches, publish/JSON/URL-safety).

## Rules this record exists to enforce

1. **Compile before you push.** Pure Kotlin: the local harness. Everything
   else: one push, then *read the annotations before writing the next line*.
2. **Read the failure you already have.** `python3 scripts/ci_annotations.py`
   (or the run page) before touching a file — never a second red run with the
   same errors.
3. **Fix in place.** A compiler error names a line; editing that file beats
   rewriting it.
4. **Look up every API you call.** Open the file that owns it; if it is not
   there, it does not exist.
5. **A red run is not an iteration.** If two consecutive runs fail with the
   same signature, the strategy is wrong — stop and re-derive from the spec.

## CI record

- Fix branch: `arena/01a08c04-codec` — see the tip sha + `Build APK` run id in
  the session report / `docs/NEXT_STEPS.md` head line.
- The abandoned branch `arena/01a08b68-codec` (`a3a8b46`) is left untouched for
  the record; its content is superseded by this one.
