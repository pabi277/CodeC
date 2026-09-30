# CodeC Phase 41.2 — Settings → Feedback & support, with honest disclosure

> **Status:** 🔧 IMPLEMENTED (`ui/support/FeedbackSectionCard.kt` +
> `FeedbackStore.kt` + `FeedbackContacts.kt` + `FeedbackSectionState.kt` +
> `ui/crash/CrashLog.kt`; `FeedbackSectionStateTest` 10,
> `FeedbackNumberSettingTest` 7, `FeedbackCheckboxNotPersistedTest` 6,
> `CrashLogTest` 4 — host-pre-validated) · **Cost:** `[client-only]` ·
> **Effort:** S

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

---

## Implementation record (2026-09-10, `arena/01a08cc6-codec`)

The section shipped as **About → "Feedback & Support"** (12th section,
between About and the debug-only Developer Options), with the card content
in its own file so `SettingsScreen.kt` stays navigable and the pinned
audit (`SettingsAuditTest`, now 12 sections) keeps its meaning. The
disclosure sits ABOVE the checkboxes (exit 4); the checkbox labels state
their own redaction policy; the CHAT row shows the number it replies to.

**Decisions made during implementation, recorded for the device round:**

1. **The crash box is prefilled when a crash is present** (exit 5
   "prefilled in"; the README's risk line "off-by-default if that turns
   out to be too much" stays the device round's call — flip
   `FeedbackSectionState`'s `includeCrash` default to `false` if so). The
   LOG box is off by default, and NEITHER is ever persisted: a fresh
   `FeedbackSectionState` is what "leave and return" gives, pinned by
   `FeedbackCheckboxNotPersistedTest` (fresh-default pins + a structural
   source scan: no feedback boolean key exists in any store, and the card
   never writes to a DataStore).
2. **The crash read is the overlay's read, extracted, not duplicated** —
   `ui/crash/CrashLog.kt` owns `newestRecord(filesDir)`
   (header-first, 9 000-char display cap); `CrashReportOverlay` now calls
   it too. One sink, one reader list. `CrashLogTest` pins the semantics
   (newest-from-header, absent/empty/blank → null).
3. **EMAIL is a second owner-set field, hidden when unset** — the same law
   as the WhatsApp number (empty → hidden, not broken; the store's
   `DEFAULT_CONTACT_EMAIL` is the one-line change point). The planned
   `mailto:` route is unchanged; an owner who never fills it simply never
   sees the button, and COPY + GITHUB ISSUE are the always-on fallbacks.
4. **The WhatsApp row degrades exactly as exit 2 demands, via an explicit
   presence probe** — `com.whatsapp` or `com.whatsapp.w4b` (the API-33
   `PackageInfoFlags` overload with the legacy path guarded). If neither
   is installed the wa.me link is never launched; instead the FULL report
   is copied and the toast names the number to write to (the number is
   also permanently visible under the CHAT button). If WhatsApp IS
   installed but the launch fails, `OpenInBrowser.openOrCopy`'s policy
   applies — content copied, never lost.
5. **`FeedbackSectionState` (pure) owns the row decisions** — chat
   availability (valid stored number only), `chatReady` (non-blank text,
   per the design table's "non-empty to enable CHAT"), `primaryAction`
   (CHAT vs COPY, exit 1), email availability — so the Compose layer is a
   render, not a policy.
6. **Test-plan deviation, for cause:** the planned Robolectric DataStore
   round-trip was replaced by pure tests over `FeedbackContacts` (the
   storage decisions: blank = clear, invalid = refuse, normalised =
   stored) + a source scan of `FeedbackStore` + the store's addition to
   `SettingsKeysHaveReadersTest`'s store list (the generic key → flow →
   out-of-store-reader chain now covers `feedback_whatsapp_number` and
   `feedback_contact_email`). Reason: the sandbox cannot run Robolectric,
   and Phase 40.4's law is to never push an unverifiable test when a pure
   equivalent carries the same guarantee. The DataStore plumbing itself
   (2 keys, 2 flows, 2 savers) mirrors `GitCredentialsStore`, compiles
   against the datastore shims in the local loop, and CI is the executor
   of record for it.
7. **`OpenInBrowser.openOrCopy` is the shared helper** (the spec's
   "decided in implementation" point): open via the single launcher; on
   failure copy `copyInstead` (the URL for the share rows, the full
   report for the feedback channels) and say so. ServerSharePanel's
   open-button now routes through it — same behavior, one policy.

**The number's default is empty BY DESIGN** (the phase's law: not welded
into the APK — the owner fills it once in Settings → Feedback & Support).
`FeedbackStore.DEFAULT_WHATSAPP_NUMBER` is the one-line change point when
Phase 42 (share-readiness) decides whether a tester's fresh install
should carry the number — flagged to the owner in the phase report.

---

## Follow-up round (2026-09-10, owner: device round 1 "1-8 pass" + three requests)

**Round 1 record:** 8/8 checks PASSED on the owner's phone (owner report;
the tested WhatsApp number was not the owner's own — which prompted
request 1 below). The owner then asked for three changes, all shipped on
`arena/01a08cc6-codec`:

1. **"Add number +91 62967 46606 · Email- chakraborttypabi2772006@gmail.com"**
   — the PART_41_2-recorded Phase 42 decision point (`DEFAULT_WHATSAPP_NUMBER`,
   "empty by design"), decided a phase early by the owner: both defaults now
   ship in the APK (`916296746606` / the address, pinned by
   `ExitSurveyTest`). A stored value always wins over a default, and
   clearing a field is an EXPLICIT off (a stored `""` hides the row; it
   does not fall back to the shipped default — the field's supporting text
   says so).
2. **"Can it be a separate page?"** — yes: the card moved to
   `ui/screens/FeedbackScreen.kt` (Settings keeps one OPEN row → audit
   control 46), reachable from Settings AND from the exit survey. The
   reply-to fields stay on that screen ("where feedback from this app is
   delivered" — visible to testers on purpose: it is the honest answer to
   "who reads this?").
3. **The exit survey** (owner: *"for the testing phase it when user want to
   close the app it show a sweet request pop up for rate,experience,
   bugs,problems etc and tap again to exit and a option to give review"*).
   This AMENDS the part's "nothing else in the app may nag" non-goal —
   amended by the owner, not abandoned: the prompt ships with its off
   switch on the Feedback screen (default ON, key
   `feedback_exit_prompt_enabled`), it appears only on a back press that
   would close the app (never mid-work), it sends nothing by itself, and
   an accidental outside tap never closes the app
   (`dismissOnClickOutside = false`; the dialog's back press IS the exit —
   the classic "tap again to exit"). The star row rides the report's info
   line ("· Rating: 4/5") and nothing else; GIVE A REVIEW opens the public
   repo (CodeC ships from GitHub, so the repository page is the review
   surface). Wiring: a single `BackHandler` in `MainApp`, registered
   BEFORE the Scaffold so every in-app back consumer (NavHost pops,
   dialogs, sheets) keeps priority; at the root it decides prompt vs
   direct exit. `ExitSurveyTest` pins the defaults, the rating rules
   (0/null/out-of-range never render), and the wiring by source scan.

**Deferred:** an in-app "app store" style review card (screenshots gallery
etc.) — GIVE A REVIEW already opens the repo; Phase 42 (share-readiness)
owns anything more. The exit prompt's long-term fate (keep / soften to
once-a-week / remove) is explicitly Phase 42's call — the switch and the
`ExitSurvey` KDoc record that.

---

## Round 2 (2026-09-10, owner: *"I want to sit as developer not some other guy"*)

The owner ended the "the number is a Settings value" design (this part's
original table row *Your number for replies* and the README's "number is a
setting" law) in one sentence: *"So i want my number hard coded. Any
feedback comes to me no need for the user to set number the user know me
or don't know me does not matter a bit. So remove the boxes and set it in
the code."*

What changed, and what deliberately did not:

- **`ui/support/DeveloperContact.kt` is the single source** —
  `WHATSAPP_E164 = "916296746606"` (+91 62967 46606), `WHATSAPP_DISPLAY`,
  `EMAIL = chakraborttypabi2772006@gmail.com`. Hardcoded constants; every
  channel (CHAT, EMAIL, GITHUB ISSUE's repo) reads them. `ExitSurveyTest`
  pins that the constant is valid E.164 (a wrong constant would make the
  CHAT row vanish — `whatsappUrl` returns null) and that it matches what
  the owner typed.
- **The reply-to fields, the SAVE button, `FeedbackStore.kt` and
  `FeedbackContacts.kt` are DELETED.** No store key for the contacts
  exists anymore (`ExitSurveyTest` pins the absence — the round-1 keys
  must not come back). The exit-prompt switch moved to `SettingsManager`
  (`feedback_exit_prompt_enabled`), the only feedback key left, covered by
  the audit's reader-chain test like every other key.
- **Unchanged on purpose:** the ephemeral-checkboxes privacy law (fresh
  choice per report, `FeedbackCheckboxNotPersistedTest` still bans
  attachment-shaped keys), the three-line disclosure (line 3 now says
  "the developer" — still true, and now the ONLY identity involved), the
  WhatsApp-less fallback (copy + the number, shown as
  `WHATSAPP_DISPLAY`), and the honest "nothing sends by itself" rule.
- **Test deltas:** `FeedbackNumberSettingTest` deleted with the feature it
  pinned; `ExitSurveyTest` grew the round-2 pins (validity,
  not-configurable, wiring via `SettingsManager`); the persistence and
  key-reader tests updated. 65/65 host-pre-validated.

**Why this is the right shape for the testing phase:** one developer, one
identity, zero configuration — and when the owner someday wants a second
maintainer or a support alias, it is one constant in one file (or a list),
not a settings surface that every tester had to understand.
