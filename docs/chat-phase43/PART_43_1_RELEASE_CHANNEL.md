# CodeC Phase 43.1 — A real release channel (and an updater that can be trusted)

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` + workflow + one secret ·
> **Effort:** M

## Symptom

Everything about "sharing CodeC" currently runs through a **debug** artifact:
CI builds `:app:assembleDebug`, `README.md` points testers at the Actions
artifact, and the in-app *Install APK from GitHub* asks
`releases/latest` — which today resolves to `userland-v1`, a *bootstrap*
release with no app APK. So the app has no release, no release notes, and an
update path that cannot find anything (and, if it found something, would not
check whether it was newer).

## Design

### 1. The key, once, and never in the repo

- Generate an upload key **by the owner, off-machine** (or in the sandbox and
  then deleted from it — the doc must say which happened), `PKCS12`, alias
  `upload`, 25+ year validity — matching what the existing
  `debug.keystore` note in `build.gradle.kts` already learned (a *pinned* key
  so sideload-over-sideload keeps working; the debug store was created with
  that reasoning and this is the same problem at the release level).
- Store: GitHub Actions **secrets** `KEYSTORE_B64` + `STORE_PASSWORD` +
  `KEY_PASSWORD`; the workflow materialises the file to a temp path and exports
  `KEYSTORE_PATH` — **the build file needs no change** (it already reads
  `KEYSTORE_PATH` first). No key in git, no key in the APK, no key in logs
  (`::add-mask` for the password exports, so a failing step cannot echo them).
- The **consequence is written down in the release doc**: the upload key is
  permanent for update-over-install; losing it means every tester must
  uninstall (losing app data), and a *new* key is a new app as far as Android
  is concerned. Also: switching the existing debug-signed installs to a
  release-signed build is an uninstall for whoever has the debug build — say so
  in the first release's notes.

### 2. CI: build both, ship the release

`build-apk.yml` grows a release build **in the same job** (a docs-only or code
push must not double the queue, and there is one runner anyway):

```
- Assemble debug APK              (unchanged: the dev/QA artifact, CI run number in versionName)
- Assemble release APK            (gradle :app:assembleRelease, env KEYSTORE_*)
- Upload CodeC-IDE (debug)        (unchanged)
- Upload CodeC-IDE-release        (new)
```

`workflow_dispatch` gets an `input: publish_release` (default off) so **no push
ever publishes anything by itself**; publishing runs on a **tag**:
`on: push: tags: ["app-v*"]` → build release → `gh release create app-v…`
(softprops/action-gh-release is already used for the attachment path) with the
notes from a `RELEASE_NOTES.md` template + the `git log` since the previous
tag. The existing `Attach APK to GitHub Release` step (gated on `release`
events, skipping `userland-*`) stays for the manual case — so both routes work
and neither fires on a normal push.

**Artifact hygiene, part of the same change.** CI currently uploads two APKs —
`CodeC-IDE` (the app, debug) and `CodeC-Bench` (the Phase 28.1 spike, whose
step comment says *"REMOVE THIS BLOCK when Phase 28 closes"*) — and a stranger
reading the Actions page cannot tell which is which. 43.1 deletes the bench
block (28.1-28.2 are closed; 28.3/28.4 ride in the app), renames the app
artifact to `CodeC-IDE-debug`, and leaves one APK per build type on the page. A
beta's most common support ticket is "the app you sent me doesn't work", and
half of those are the wrong file.

Naming: `CodeC-IDE-1.3.17-universal.apk` (the one to pick when in doubt) plus
per-ABI files from 43.2. `versionCode` stays hand-authored but gains a CI
check: the tag's `app-v<X.Y.Z>` must equal `versionName`'s numeric part, and
`versionCode` must be **strictly greater** than the greatest one in any
existing `app-v*` release (one `releases` API call, unauthenticated) — the
"we shipped a release nobody could update over" mistake is a versioning
mistake, and a check is cheaper than the fallout.

### 3. The updater, made honest

`ApkUpdateManager` becomes channel-aware, and its rules live in a pure object
`ui/services/UpdatePolicy.kt` (testable, because this is the code that decides
whether to install a binary):

```kotlin
object UpdatePolicy {
    fun isAppRelease(tag: String): Boolean                    // "app-v…" only
    fun pickAsset(assets: List<Asset>, abi: String?): Asset?  // universal fallback, never "first .apk"
    fun shouldOffer(installed: Version, candidate: Version): Decision  // NEWER / SAME / OLDER / UNSAFE
    fun verify(downloaded: File, expectedSha256: String?): Verification  // NEVER_INSTALL on mismatch/absent-for-publish
}
```

Concrete changes: `GET /repos/{o}/{r}/releases` (list, `per_page=10`) instead
of `latest`, filtered by `isAppRelease` — so a `userland-*` release can never
be offered as an app update, whatever GitHub marks as "Latest"; asset choice by
ABI with `universal` as the fallback; **version compare on the parsed
`X.Y.Z`, never string equality**, so "same version" says *you are up to date*
instead of re-offering the install; an explicit refusal (with a reason) for an
older one; download into `cacheDir/updates/` with a size cap (the release's
`size` field), a SHA-256 check against a digest the release notes carry (and
`RELEASE_NOTES.md` template gets a `sha256:` line that CI fills in — no
digest, no auto-install, only "open in browser"), then
`installApk` exactly as today (FileProvider + the unknown-sources prompt), and
`finally { file.delete() }` for anything that did not launch the installer.
The whole network part stays `HttpURLConnection` + `org.json`, the house style
— **no new dependency for a version check** — and it does not start from
scratch: `ui/terminal/UserlandInstaller.kt` already implements the *same*
download problem, correctly, and its rules are what `UpdatePolicy` copies (or
shares). It has `chooseRelease(arch)` against the Releases API,
`archiveName/tarballUrl/shaUrl` (`userland-<arch>.tar.gz` + a `.sha256`
sidecar), `fetchSidecar` + `isValidSha` + `sha256(file)` verification,
`downloadFile` with a progress callback and resume, `MIN_FREE_BYTES = 48 MB` of
headroom before it writes anything, a `.userland-staging` directory + atomic
`swapPrefix`, `requireLaunchable` diagnostics when the result won't start, and
`installedRelease()` markers (`ARCHIVE`/`ARCH`/`LEGACY_MARKER`) so a partial
install is detectable. **Today's `ApkUpdateManager` has none of that**: `fetchLatestRelease()`
takes the first `assets[]` entry ending in `.apk` and `downloadApk()` writes
`cacheDir/updates/CodeC-IDE.apk` with `input.copyTo(output)`, no
`Content-Length` check, no digest, no delete. The gap is not that the code is
bad, it is that there are two downloaders and only one of them is careful — so
43.1's implementation rule is: **extract the careful one's rules into a shared
pure object (`ui/services/ReleaseFetch.kt`-style: JSON→model, no Android),
then make both callers use it**; the userland path keeps its behaviour (its
tests `UserlandInstallerTest`/`ModuleInstallerTest` stay green) and the APK path
inherits the discipline.

`Settings → About` gains one line: *Check for updates* (manual only; no
background poll — the app must not phone home on its own, which is the same
law Phase 41 writes down for feedback).

### 4. Release notes that a stranger can use

`RELEASE_NOTES.md` template, filled by CI: what's new (by phase — the JOURNEY
entries make good headings), **what still bites** (the known-issues list, from
`docs/TROUBLESHOOTING.md`'s user-visible subset), which APK to install
(universal, or the ABI line from About), how to get help (Phase 41's row), and
that the app stores everything on-device with no telemetry. This is the piece
that converts "here's an APK, good luck" into something a person will actually
install.

## Exit condition

```text
1. Tag `app-v1.3.17` → within one CI run there is a GitHub Release with the
   release-signed APK(s), the sha256 line, and the notes; no push without a tag
   ever creates a release.
2. `apkanalyzer manifest print <apk>` (or `aapt dump badging`) shows
   `debuggable=false`, the release signature, and the right versionName;
   `adb install -r` over the previous release works (same key).
3. Missing/invalid secret → the workflow fails BEFORE uploading anything, with a
   message naming the secret; no unsigned or debug-signed APK is ever published
   as a release asset.
4. On-device: *Check for updates* on the same version → "up to date"; after a
   newer tag → offers the download, verifies the digest, opens the installer;
   with the digest line removed → refuses to install and says why.
5. A `userland-*` release marked "Latest" on the repo does NOT appear as an
   app update (this is the bug being closed, so it is an explicit check).
6. versionCode regression (a tag whose `versionCode` is not greater) fails the
   publish step instead of shipping an uninstallable update.
PASS = 1-6. (2 and 4-5 need the owner's device; the rest is CI.)
```

## Tests (plan)

- `ReleaseFetchTest` (host, pure `org.json` parsing, the
  `CodecJsonParserTest` habit): a real `releases` JSON fixture (paginated list,
  prerelease flags, `draft` entries, zero assets, an asset list with only
  `.sha256` files, and one with a same-name-but-different-size entry) decodes to
  the expected model; `browser_download_url` vs `url` is the field pair a hand
  parser gets wrong, so both appear in the fixture with different values and the
  test pins which one is used.
- `UpdatePolicyTest` (host, pure) — the whole exit list that can be expressed
  as data: `isAppRelease("userland-v1") == false`, `("app-v1.3.17") == true`;
  asset pick for arm64/x86_64 with and without a universal; version compare
  (`1.3.16 (3419922)` vs `1.3.17` → NEWER, `1.3.17` vs `1.3.17` → SAME,
  `1.4` vs `1.3.17` → NEWER, `2` vs `1.9.9` → NEWER, garbage → UNSAFE);
  digest mismatch → never install; absent digest → refuse auto-install but
  allow "open in browser"; a size cap exceeded → abort before writing.
- `VersionParseTest` — the `versionName` shape with the CI run number in
  parentheses parses and compares correctly (that suffix is a Phase 29
  addition and is exactly what a naive `==` comparison breaks on).
- Workflow-level, verified by the run itself rather than a test: the
  `publish_release` input default, the `app-v*` tag trigger, and the
  secret-missing failure message (the tag run *is* the test; recorded in the
  part doc like every CI fact in this repo).
- `RELEASE_NOTES` template: `scripts/check_release_notes.sh` asserting every
  section heading exists (so the notes cannot silently shrink to "bug fixes").

## Sources (record)

- `rule.md` §"never double-dispatch expensive workflows" and §"CI is the only
  executor of record"; the `debug.keystore` rationale comment in
  `app/build.gradle.kts:62-79` (pinned key so sideload works; PKCS12 via
  OpenSSL; the uninstall-wipes-sandbox story), `signingConfigs.release` at
  `:54-60`, `buildTypes.release` at `:84-90`, `versionName`/`GITHUB_RUN_NUMBER`
  at `:25-30`, `ApkUpdateManager.kt` in full (`releases/latest`, first
  `.apk` asset, `cacheDir/updates`, `canRequestPackageInstalls`,
  `ACTION_MANAGE_UNKNOWN_APP_SOURCES`), `README.md` §"Install the APK from
  GitHub", `.github/workflows/build-apk.yml:28-31,72-79` (assemble + the
  release-attach step skipping `userland-*`), and `gh release list` output of
  2026-09-10 (`userland-v1` shown as **Latest**).
- GitHub Releases as the distribution channel for an app that cannot use Play:
  the reason Play is out (AAB-only, API-36 target by 2026-08-31, 12 closed
  testers × 14 days for new personal accounts) is in
  [`../PHASE38_43_OSS_RESEARCH.md`](../PHASE38_43_OSS_RESEARCH.md) §6 —
  recorded here so the choice reads as informed, not as "we didn't bother".
- `gh release create` / `softprops/action-gh-release@v2` (already used here)
  for the publish step.

## Deferred / rejected with reasons

- **Self-hosted update server / `update.json`** — GitHub Releases already gives
  versioning, assets, digests (in the body) and a page a human can read.
- **Signing inside the app (apksigner at runtime)** — not a thing users should
  need; the key belongs in CI secrets.
- **`applicationId` change for a Play flavour** — a second product line, not a
  beta fix.
- **Releasing on every merge to `main`** — a release per merge teaches nobody
  anything and burns the "which one do I install?" question every time; tags are
  the switch.
- **Auto-downloading updates without asking** — a phone IDE replacing its own
  binary behind the user's back is how you lose the user's `data` when the
  signing story changes.
