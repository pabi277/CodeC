# CodeC Phase 41 — Feedback that reaches you (WhatsApp-first)

> **Status:** 🔧 **IMPLEMENTED on `arena/01a08cc6-codec` (host-tested
> 61/61, CI + device round pending)** · **Cost:** `[client-only]` ·
> **Effort:** S/M · **Owner row:** *"For testing i have to add a feedback
> page give the best way, i am willing to give my WhatsApp number"*

```text
  41.1  The report: a pure draft builder + the links that carry it
  41.2  Settings → Feedback & support, with honest disclosure
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [41.1](PART_41_1_REPORT_AND_LINKS.md) | `FeedbackDraft` + WhatsApp/mailto/GitHub links | S/M | 🔧 IMPLEMENTED |
| [41.2](PART_41_2_FEEDBACK_SCREEN.md) | The screen, the opt-ins, the number in Settings | S | 🔧 IMPLEMENTED |

**Implementation map** (all on `arena/01a08cc6-codec`):

- 41.1 `ui/support/FeedbackDraft.kt` (pure: `build`/`redact`/`whatsappUrl`/
  `normaliseNumber`/`mailto`/`gitHubIssueUrl`/`encode`) + the E.164
  country-code table; redaction = this object's token-shape table **plus**
  `GitRedactor` (the stored literal + URL credentials) — the only two
  redaction paths, as specced.
- 41.2 `ui/support/FeedbackSectionCard.kt` (the Settings section's content:
  text field, two **ephemeral** checkboxes, CHAT/COPY/EMAIL/GITHUB buttons,
  owner reply-to fields, the three-line disclosure ABOVE the checkboxes),
  `ui/support/FeedbackStore.kt` (2 DataStore keys, `GitCredentialsStore`-
  shaped; the pure decisions live in `FeedbackContacts.kt`),
  `ui/support/FeedbackSectionState.kt` (pure row/primary-action state),
  `ui/crash/CrashLog.kt` (the ONE newest-record read of
  `filesDir/crash-log.txt`, extracted from `CrashReportOverlay` so the
  overlay and the report cannot drift), and
  `OpenInBrowser.openOrCopy` (the shared open-or-copy policy — the share
  row 37 now routes through it too).
- Tests: `FeedbackDraftTest` (23), `FeedbackSectionStateTest` (10),
  `FeedbackNumberSettingTest` (7), `FeedbackCheckboxNotPersistedTest` (6),
  `CrashLogTest` (4) — **50 new host cases, pre-validated 61/61 on a local
  JVM (jdk4py Temurin 25 + kotlinc 2.4.10, the Phase 40.4 harness route
  with a JUnit shim + datastore shims; the loop caught 5 test-side bugs
  before CI — assertion arithmetic and wrong fixtures, not engine bugs).**
  `SettingsAuditTest` (12 sections now) + `SettingsKeysHaveReadersTest`
  (4 stores now) were updated in the same commit and run in the same local
  loop. Device runbook: [DEVICE_TEST_PLAN.md](DEVICE_TEST_PLAN.md).

## What exists today (evidence, read 2026-09-10)

- **A Settings section: nothing.** `SettingsScreen.kt` has no
  feedback/support row of any kind (grep for `feedback|support|whatsapp` → only
  an unrelated "comma-separated" label), and `strings.xml` has no such string.
- **But the crash path already asks for exactly this.**
  `ui/crash/CrashReportOverlay.kt` shows the newest record from
  `filesDir/crash-log.txt` **before anything else on the next launch**, with the
  exception line promoted into the dialog title (so even a screenshot carries the
  diagnosis) and **COPY ALL / Share / Clear** buttons — and its body text reads
  *"CodeC crashed previously. Please COPY ALL and paste it into the chat so the
  exact failing line can be fixed."* The mechanism therefore already exists
  (`MainActivity.installCrashLog()`: `Thread.setDefaultUncaughtExceptionHandler`,
  header-first records, `appendThrowable(frameCap = 80)`, up to 5 causes, a 60 KB
  bound). What Phase 41 adds is **the chat** — the thing that text is asking for
  — plus a structured report that does not require the user to find a chat window
  themselves, and a hook in that same overlay (42.3) so the tap replaces the
  copy-paste ritual.
- **The material for a report already exists and is good**: `AppLogger`
  (`ui/utils/AppLogger.kt`, a 1 000-line ring of `LEVEL/tag: message` lines,
  `LogsScreen` to read it), that header-first crash record,
  `ui/utils/DeviceDiagnostics.kt` (ABI summary, `isArm64`, `isLikelyEmulator`,
  `osSummary()`, mount-flag parsing for the `noexec` diagnosis),
  and a `versionName` that already carries the CI run number
  (`"1.3.16 (340xxxx)"`) precisely so a bug report can say which build it was —
  a lesson from three crash reports pasted from a stale APK. `GitRedactor`
  (`ui/projects/GitManager.kt:911-932`) is the existing redaction shape, with a
  limitation this phase must design around (below).
- `OpenInBrowser` (the app's single `ACTION_VIEW` launcher, Phase 37
  follow-up) exists and already knows how to degrade when nothing can handle a
  URL — the exact problem a WhatsApp link has on a device without WhatsApp.

## Research that shaped the design

Dossier: [`../PHASE38_43_OSS_RESEARCH.md`](../PHASE38_43_OSS_RESEARCH.md) §4.
Summary of the decision: **WhatsApp click-to-chat is a plain
`https://wa.me/<E.164 digits>?text=<url-encoded>` link** — no SDK, no key, no
server, works for personal numbers, and the message lands in the composer
**unsent** so the user always approves it. The documented failure of the whole
feature is a mis-formatted number (`+`, spaces, dashes, a leading trunk `0`),
so the number is normalised *and validated* in pure Kotlin with tests rather
than pasted. Firebase/Crashlytics, a Telegram bot token in the APK, and
Google Forms were all considered and rejected with reasons (a bot token ships
to every install; Crashlytics means Google services + a data-collection
disclosure in an offline-first app).

## The shape of it

```text
Settings → Feedback & support
  ├─ What's happening / what broke      ← one free-text field
  ├─ [ ] Attach app log (last 200 lines, redacted)
  ├─ [ ] Attach last crash              ← the header-first crash record
  ├─ [CHAT ON WHATSAPP]                 ← wa.me link, draft prefilled
  ├─ [EMAIL]  [COPY REPORT]  [GITHUB ISSUE]
  └─ "Your number for replies" (owner-set, in this same screen) +
     "What we include / what we never include" (3 lines, no legalese)
```

Nothing is sent automatically; there is no background upload, ever; the
report is *composed in front of the user*. And the number is not welded into
the APK — it is a Settings value the owner fills once (default empty → the
WhatsApp row is hidden, not broken).

## Exit condition

```text
1. Owner's phone (WhatsApp installed): tap CHAT → a chat with the owner opens,
   the draft is prefilled (version, Android/API, device, project, their text)
   and NOTHING has been sent yet — they read it and press send.
2. A device without WhatsApp: tapping does not "do nothing" — the report is
   copied, the number is shown to dial/type, and the toast says so.
3. With both checkboxes off, the report contains no log and no crash text
   (asserted by reading the copied text); with them on, it contains the tail
   and NO token/secret (a fake token planted in the log must come out redacted).
4. The GitHub issue row opens a prefilled issue in the browser and works with
   no token; if the repository is private, the row is either hidden or labelled
   "sign in to GitHub first" — never a link that dies.
5. Reply path: the owner can answer from WhatsApp with no extra tooling.
PASS = all five on the owner's device (3's redaction half is host-tested).
```

## Risks to watch (multi-device round)

- **URL length**: WhatsApp's `?text=` is a URL; some OEM browsers/pasteboards
  truncate around ~2 k chars. `FeedbackDraft` has an explicit budget and, when
  the draft would exceed it, says so and offers `[COPY FULL REPORT]` (which has
  no length limit) instead of silently chopping the log tail.
- **Newlines and `%`** in the user's text must be encoded (`%0A`, `%25`) — the
  encoding is a pure function with tests, because a raw `\n` in a URL is a
  documented no-op/`500`-class failure and a stray `%` truncates everything
  after it.
- **Non-Latin text** (Hindi/Bengali bug reports) must survive — encode as
  UTF-8 percent-encoding, and test one Devanagari + one Bengali string.
- **The log may contain paths the user considers private** (`/storage/…`,
  account names). Redaction covers tokens (existing `GitRedactor` rules) and a
  path-shortening rule (`…/CodeC/projects/` → `~proj/`), and the disclosure
  line says plainly what is in it. The checkbox is off-by-default for the crash
  record if that turns out to be too much — the device round decides.

## Deferred, recorded on purpose

- **A public issue-tracker flow** (auto-create an issue with the token from
  40.3) — the prefilled browser URL covers the need with no secret handling;
  `POST /repos/{owner}/{repo}/issues` needs *Issues: write* and would
  re-introduce the "which permission?" question in a second place.
- **Ratings prompts / in-app surveys / Discord or Telegram group join links** —
  growth mechanics, not feedback; the owner asked for a way to be told when
  something is broken.
- **Attachment upload (screenshots, video)** — WhatsApp already accepts them in
  the chat the link opens; an in-app uploader would need a place to put files.
- **Email-only or form-only** — rejected: no device context, or an inbox of
  screenshots the owner has to triage by hand.
