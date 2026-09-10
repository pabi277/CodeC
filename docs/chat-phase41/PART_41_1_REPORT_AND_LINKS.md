# CodeC Phase 41.1 — The report: pure draft builder + the links that carry it

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S/M

## Design

**One pure object produces the text and the URLs.** `ui/support/FeedbackDraft.kt`:

```kotlin
data class FeedbackInput(appVersion: String, androidRelease: String, apiLevel: Int,
                         device: String, abis: String, project: String?,
                         userText: String, includeLog: Boolean, logTail: List<String>,
                         includeCrash: Boolean, crashRecord: String?, maxChars: Int = 1800)
object FeedbackDraft {
    fun build(i: FeedbackInput): String                 // the report, plain text
    fun redact(lines: List<String>): List<String>       // secrets + private paths, BEFORE the budget
    fun whatsappUrl(numberE164: String, text: String): String?   // null = unusable number, hide the row
    fun normaliseNumber(raw: String): String?            // "+91 98765 43210" → "919876543210"
    fun mailto(address: String, text: String): String
    fun gitHubIssueUrl(owner: String, repo: String, title: String, body: String): String
    private fun encode(s: String): String                // UTF-8 percent-encoding, RFC 3986 unreserved kept
}
```

**Report layout** (fixed order, so the owner can skim 50 of them a week):

```text
CodeC 1.3.16 (3439922) · Android 13 (API 33) · Redmi Note 9 · arm64-v8a
Project: hello-c · Screen: Editor
--- what I saw ---
<user text, verbatim, no re-wrapping>
--- log (last 120 lines, redacted) ---
[09-10 01:22:11.401] E/Compiler: …
--- crash ---
<header line + up to 12 frames>
```

Rules that make it trustworthy rather than noisy:

- **Redaction before truncation.** `redact()` runs first so a budget cut can
  never *keep* a secret that a later line would have cut. Private paths: the
  app's data dir prefix (`/data/user/0/com.codeci.ide/files`) and the userland
  home → `~app/`, `~home/`; `Authorization:`/`token=` fragments → `<redacted>`.
- **Reusing `GitRedactor` is necessary but not sufficient — read this before
  implementing.** The existing redactor is `class GitRedactor(private val
  secret: String?)` inside `ui/projects/GitManager.kt:911-932`: `redact(text)`
  replaces **the literal token it was constructed with** and otherwise only
  scrubs `user:password@` URL credentials (`redactUrls`, via
  `urlCredentialsRegex`). It has **no** pattern list for `ghp_…` /
  `github_pat_…` shapes and, more importantly, it needs the secret *passed in*.
  A feedback log tail is not produced by one call site with one credential: the
  crash log and the `AppLogger` ring can contain a token echoed by any code path
  (a `gh` failure, a `curl` the user typed into the terminal, a future feature
  that logs the wrong thing). So `FeedbackDraft.redact` is
  `GitRedactor(GitCredentialsStore.token).redactAll(lines)` **plus** a
  `TokenShapes` list owned here (`ghp_[A-Za-z0-9]{36}`, `github_pat_\w+`,
  `gh[sour]_[A-Za-z0-9]+`, `x-access-token:`, `://[^/\s:]+:[^@\s]+@` — the last
  one already `redactUrls`' job, so call it rather than duplicating it), with the
  rule that the *only* redaction code paths are those two objects. The one-line
  summary of why this matters: `GitRedactor` is written for
  "scrub the secret we just injected into this child process"; a bug report
  needs "scrub any secret that could possibly be in here", which is a different
  question and needs its own tested table.
- **`maxChars` budget with an escape hatch.** The WhatsApp URL has to survive
  OEM browsers, so `build()` trims the log section first, then the crash
  section, and *never* the user's own text — the last thing to cut is what a
  human typed. When trimming happens the report says
  `[log trimmed — use COPY FULL REPORT for the whole thing]`, and 41.2 offers
  that button, which has no budget.
- **Number normalisation is strict, with a visible result.** `normaliseNumber`
  strips spaces/dashes/parens, drops a leading `+` and a leading `00`, accepts
  only 8-15 digits (E.164's own bound), and `whatsappUrl` returns `null` for
  anything else — the UI then hides the WhatsApp row and shows
  "WhatsApp number not set / looks wrong" instead of building a link that opens
  a chat with the wrong person. The `?text=` payload is percent-encoded with
  `%20`/`%0A`, spaces included (a `+` is not a space in a query under every
  handler's interpretation, so we do not rely on it).
- **Every link is optional, every link has a fallback.** WhatsApp →
  `mailto:` → copy → GitHub issue; each of the first three works with no
  account and no network. `OpenInBrowser.open()` already returns a Boolean and
  39/37 established the pattern: **if the launch fails, do not lose the
  content** — copy it and say so. (This part makes that the *shared* helper the
  feedback screen and the share row both use, rather than a second copy of the
  same three lines; a small `ui/services/LinkLauncher.kt`-style function or an
  `OpenInBrowser.openOrCopy(context, url, label)` addition — decided in
  implementation, not in stone here.)
- **`gitHubIssueUrl` builds only a URL.** `https://github.com/<owner>/<repo>/issues/new?title=…&body=…`
  (percent-encoded, `X-GitHub-Api-Version` not needed because there is no API
  call). For a **private** repo the link is still valid for a signed-in browser
  session, so the row is shown with a one-line caveat rather than hidden — but
  the *owner's* repo visibility must be checked at implementation time and the
  wording chosen accordingly (recorded as an open question, not a guess).

## Exit condition

```text
(host + device)
1. build() output for a fixed input is byte-for-byte what the test expects
   (header line, section order, user text untouched).
2. A planted `ghp_secret123` and a `CODEC_GIT_TOKEN=` line in the log tail come
   out as `<redacted>`; `~proj/` replaces the app data path.
3. normaliseNumber: "+91 98765 43210"→919876543210, "0044-7700-900123"→
   447700900123, "8-800-555-3535"→null (too short/invalid), "abc"→null.
4. whatsappUrl("919876543210", "hi\nthere 100%") ends with
   ?text=hi%0Athere%20100%25 — and the link, opened on a phone, shows the
   message exactly as typed (Devanagari + Bengali fixtures included).
5. Over the budget: the log tail shrinks, the user text does not, and the trim
   notice is present.
PASS = 1-5; 4 is the device half.
```

## Tests (plan)

`FeedbackDraftTest` (host, pure) — layout golden; section order; empty user
text (still a valid report, "no text" line, no blank-only output); redaction
(the three secret shapes + path shortening); budget/trim precedence incl. the
"never cuts user text" case; `normaliseNumber` table (already above);
`whatsappUrl` encoding (`\n`, `%`, spaces, non-ASCII, `&`, `#`, emoji);
`mailto`/`gitHubIssueUrl` encoding; a 200-line log tail input that must be
capped by section, not by accident; and a test that `build()` is pure — the
same input twice gives the same string (no `Date()` inside, so a device round
can diff two reports meaningfully). Timestamps come in from the caller.

## Sources (record)

- wa.me format + encoding: the click-to-chat guides
  [u2l.ai "WhatsApp Click-to-Chat Links: The Complete wa.me Guide",
  marketing.help.dotdigital.com click-to-chat article, support.wati.io] —
  international digits with no `+`/spaces/dashes/trunk-0, `?text=`
  percent-encoded (spaces `%20`, newlines `%0A`), the message is prefilled and
  the user still presses send, and it works for personal numbers too
  [waapp.me guide].
- E.164's 8-15 digit bound as the validation ceiling (the same range the
  guides' "gut check" describes).
- CodeC code, 2026-09-10: `GitRedactor.redact` (the token-shape rules to
  reuse), `AppLogger` ring, the crash record's header-first shape,
  `ui/utils/DeviceDiagnostics.{abiSummary,osSummary,isLikelyEmulator}`,
  `BuildConfig.VERSION_NAME` carrying `GITHUB_RUN_NUMBER`
  (`app/build.gradle.kts:25-30`), `OpenInBrowser` (`ui/services/OpenInBrowser.kt`,
  the app's single `ACTION_VIEW` launcher, Boolean-returning), the
  `AppLogger` ring + `LogsScreen`, and `ui/crash/CrashReportOverlay.kt` —
  `MainActivity.kt:266` mounts it before anything else, it reads the newest
  record from its header (`.take(9_000)`), and it already tells the user to
  *"COPY ALL and paste it into the chat"*, which is the exact workflow 41 turns
  into a button. `MainActivity.installCrashLog()`
  (`MainActivity.kt:125-175`) is the record's writer: 60 KB file bound,
  header-first, `frameCap = 80`, causes to depth 5 — so 41 reads that file and
  does **not** invent a second crash sink.
