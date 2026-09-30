# CodeC Phase 41.1 — The report: pure draft builder + the links that carry it

> **Status:** 🔧 IMPLEMENTED (`ui/support/FeedbackDraft.kt`,
> `FeedbackDraftTest` 23 cases, host-pre-validated) · **Cost:**
> `[client-only]` · **Effort:** S/M

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

---

## Implementation record (2026-09-10, `arena/01a08cc6-codec`)

Shipped as `ui/support/FeedbackDraft.kt`, exactly the specced shape
(`FeedbackInput` gained two optional fields: `screen` — the report's
project line reads "Project: x · Screen: y" — and `secretToScrub`, the
stored git token, which is only ever passed to `GitRedactor` and never
rendered). Exit conditions 1–5 are all pinned by `FeedbackDraftTest`:

- **1** golden layout, byte-for-byte; sections in fixed order; user text
  verbatim; blank text → the `(no text)` line.
- **2** `ghp_secret123` + `CODEC_GIT_TOKEN=…` fixtures → `<redacted>`;
  the full token-shape table (classic/fine-grained PAT, `gh[soru]_`,
  `x-access-token:`, `Authorization: Bearer`, `KEY=VALUE` secrets) plus
  `GitRedactor`'s literal + `user:pass@` URL rules; `~proj/`, `~home/`,
  `~app/` path shortening in BOTH `/data/user/0/` and `/data/data/`
  spellings (they are the same directory; a log line may carry either).
- **3** the number table verbatim, plus edges (trunk-0, 7/16 digits,
  words-after-number, Italian `+39 06…` landline — rejected on purpose,
  see below).
- **4** `whatsappUrl("919876543210", "hi\nthere 100%")` →
  `…?text=hi%0Athere%20100%25` exactly; Devanagari + Bengali + emoji
  fixtures with hand-computed UTF-8 percent sequences; `mailto` and
  `gitHubIssueUrl` encoding.
- **5** budget: log trimmed first (oldest lines go, notice present), crash
  second (from the bottom — the header + exception line are the diagnosis),
  user text NEVER cut (an over-budget report with a long text is the
  correct output); `maxChars = Int.MAX_VALUE` = no budget, no notices.

**Decisions made during implementation, recorded for the device round:**

1. **`ghp_` shape loosened from the spec's `{36}` to `{8,}`** — real classic
   PATs are 36–40 chars, but a TRUNCATED paste is still a secret, and the
   exit fixture `ghp_secret123` (11 chars) must come out redacted.
   Over-redaction is the safe direction; the prefix `ghp_` is distinctive
   enough that false positives are not a realistic log shape.
2. **`normaliseNumber` is stricter than plain digit rules** — after
   stripping separators/`+`/`00` and the 8–15 bound, the first 1–3 digits
   must be an ASSIGNED E.164 country code (the ITU list as data, ~220
   entries) and the subscriber part must be 7–12 digits not starting with
   `0`. That is what makes the doc's `8-800-555-3535` case null (any
   country-code reading leaves a trunk-`0` subscriber) while
   `0044-7700-900123` passes. **Known cost:** Italian-style landlines that
   legitimately keep a `0` (`+39 06 …`) are rejected — accepted because
   the number stored here is the owner's single support number, and
   strictness beats completeness for a wrong-number failure mode.
3. **The crash section's 14-line cap (header + exception + 12 frames) is
   the design, not a budget cut** — the crash-trim notice appears only
   when the BUDGET cut crash lines; the full record stays in
   CrashReportOverlay's COPY ALL (one sink, one reader list).
4. **`mailto` gained an optional `subject`** (default "CodeC feedback") —
   an owner triaging an inbox needs the subject line; the exit conditions
   pin the body encoding, which is unchanged.
5. **The GitHub issue row targets `pabi277/CodeC`, checked public on
   2026-09-10** (`gh repo view --json isPrivate` → `false`, issues
   enabled) — so the row is shown unconditionally, no private-repo caveat
   needed. If the repo ever goes private, the link still works for a
   signed-in browser session; the part doc's original wording stands.

**Local pre-validation (Phase 40.4's law):** jdk4py Temurin 25 + kotlinc
2.4.10 over the REAL pure sources (the Android-free half of `ui/projects`
for `GitRedactor` + the Phase 41 files) with a JUnit shim and a
datastore-shim set mirroring the real API shapes; the repo's own `@Test`
methods ran reflectively — **61/61** (50 new + the audit/key-reader
suites). The loop caught 5 test-side bugs before CI (budget arithmetic
that forgot the notice line's own length, and wrong number fixtures like
`(+1) 555-123-4567` — a `+` after a separator is a paste error the engine
correctly rejects); the engine itself needed no change.
