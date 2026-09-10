# CodeC Phase 41.2 — Settings → Feedback & support, with honest disclosure

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S

## Design

**A section, not a screen.** `SettingsScreen` gains a **Feedback & support**
section (same `SettingsSectionHeader` + row composition the rest of the screen
uses — no new layout system), placed right after *About / diagnostics* so that
"what build is this" and "who do I tell" sit together. Rows:

| Row | Behaviour |
|---|---|
| *Tell the owner what happened* | `OutlinedTextField`, 1-6 lines, no validation beyond "non-empty to enable CHAT" |
| ☐ *Include the last 120 log lines* | off by default; the label states "redacted: no tokens, paths shortened" |
| ☐ *Include the last crash* | off by default; disabled + explanatory when `filesDir/crash-log.txt` is absent or empty (the same file `CrashReportOverlay` reads — one sink, one reader list, no second crash store) |
| **[CHAT ON WHATSAPP]** | primary button → `OpenInBrowser.openOrCopy(waUrl)`; on failure: copy + a row showing the number |
| **[COPY REPORT]** / **[EMAIL]** / **[GITHUB ISSUE]** | the three fallbacks, always enabled (copy works offline) |
| *Your number for replies* (owner setting) | a `GitCredentialsStore`-shaped DataStore string (`feedback_whatsapp_number`), shown only in this section, with `normaliseNumber` validation inline ("that doesn't look like a WhatsApp number") and a `whatsappUrl == null` → hide the CHAT row |
| Disclosure, 3 lines | see below |

**The disclosure, in plain words** (three lines, no legalese, and each line
true):

```text
CodeC sends nothing by itself. Tapping CHAT opens WhatsApp with a message
already typed — you read it, then press send.
The report includes: app version, Android version, device model, and whatever
you ticked below. It never includes your files, your token, or your code.
If you write to a personal WhatsApp number, the owner can see your phone
number and profile — that's how the reply gets back to you.
```

The third line is the one an app normally hides, and it is the honest
consequence of the owner's choice to publish a personal number: the design
keeps it visible in-app rather than buried, and it is why the number is a
*setting* (an owner who later moves to a Business number or a support alias
changes one field, not one APK).

**Two rules about the existing crash machinery.** (1) *No second crash sink*:
the report reads the file `MainActivity.installCrashLog()` writes
(`filesDir/crash-log.txt`) using the same "newest record from its header" read
`CrashReportOverlay` uses — not a byte tail, and not a new capture path, because a
second sink is how a crash gets reported from the wrong build. (2) *No modal
hijack*: `CrashReportOverlay` already opens before anything else on the next
launch with COPY ALL / Share / Clear and the sentence *"please COPY ALL and
paste it into the chat"*. 41 contributes one button to it (42.3 wires
`[Send a report]` into this screen); the three existing buttons keep working
untouched, because that dialog is how the owner has debugged a dozen device
rounds.

**Nothing else in the app may nag.** Explicit non-goals for this part: no
first-run feedback dialog, no "rate us", no "share on WhatsApp" growth prompt,
no periodic nudge. Feedback is asked for where the user goes looking for it
(Settings) and after a crash (42.3 hands the crash record to this screen) —
that's it.

## Exit condition

```text
1. Fresh install, number unset: no CHAT row, and the section still works —
   COPY REPORT yields a valid report. The row appears after Settings → paste a
   number, with no restart.
2. Tapping CHAT on a phone with WhatsApp opens the chat with the draft typed
   and NOT sent; the checkboxes visibly change what the draft contains.
3. A bad number ("12345") shows the inline validation message and no CHAT row.
4. The disclosure text is on screen at the moment a checkbox is first ticked
   (no scrolling to find it), and the log checkbox's state is not persisted
   (each report is a fresh choice) — pinned by test.
5. After a crash (or a simulated one from 42.3's safe mode), the crash row is
   prefilled in and the section is reachable in one tap.
PASS = 1-5 on the owner's device.
```

## Tests (plan)

- `FeedbackNumberSettingTest` (Robolectric, `SettingsManager`-style store):
  save/load round-trip, invalid string rejected, empty = feature hidden.
- `FeedbackSectionStateTest` (pure, the section's `when` inputs): with no
  WhatsApp number, `primaryAction == COPY`; with checkboxes off, the draft
  contains neither "log" nor "crash" section headers; with a crash present but
  the box unticked, still absent.
- `FeedbackCheckboxNotPersistedTest` (Robolectric): toggling, leaving the
  screen and returning resets both checkboxes to off (this is the "no silent
  always-on attachment" guarantee; it is a privacy behaviour, so it is tested,
  not commented).
- Compose layout itself: compile + lint via CI, checked by the owner on device.

## Sources (record)

- 41.1 for every rule about content, encoding and validation; this doc owns
  only *where it lives* and *what is disclosed*.
- CodeC code, 2026-09-10: `SettingsScreen.kt` structure (`SettingsSectionHeader`,
  the About section with the licenses line added in Phase 37, the paths row that
  reads `context.getExternalFilesDir(null)`), `SettingsManager` (DataStore
  patterns for a new string key), `GitCredentialsStore` (the store-a-secret/
  setting-in-Settings shape), `LogsScreen` (where a tester is sent for the raw
  log), and the Phase 37 law this part follows for link handling — *the
  fallback must never lose the content*.

## Deferred / rejected with reasons

- **A dedicated full-screen "Support" tab** — the app has 5 nav tabs and one
  more for a text field + four buttons is a worse UI, not a better one.
- **In-app chat (WhatsApp Business Platform API)** — needs a business account,
  a server, template approval and per-message fees: the wrong tool for
  "message me when something is broken".
- **Screenshot picker** — Android's `MediaProjection` permission dialog to
  attach a screenshot is 3 taps and a privacy prompt, versus the user taking a
  screenshot themselves and pasting it into the chat the link already opened.
- **Collecting emails for a "beta list"** — different product, and it would
  make this screen a marketing surface, which is how trust dies.
