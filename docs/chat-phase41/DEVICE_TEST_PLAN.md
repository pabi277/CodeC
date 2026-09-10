# Phase 41 — device test plan (Feedback & support)

> Install the CI artifact (`CodeC-IDE`) from the green run on
> `arena/01a08cc6-codec`. Every check names the exact on-screen text that
> means PASS. 10–15 minutes, one phone (a second phone or a WhatsApp-less
> device/emulator makes check 2 honest — if you only have the one phone,
> temporarily uninstalling WhatsApp also works, but check 1 first).

## Setup (once)

1. Settings → **Feedback & Support** → *WhatsApp number for replies* →
   type your number with country code (e.g. `+91 98765 43210`) → **SAVE**.
   - PASS: "Number saved · email saved" appears, and the section now shows
     the **CHAT ON WHATSAPP** button with "Replies go to +91…" under it.
2. (Optional) fill *Contact email for replies* → SAVE → PASS: the
   **EMAIL** button appears.

## The 8 checks

| # | What to do | PASS looks like |
|---|---|---|
| 1 | Type what happened ("test from my phone"), leave both checkboxes as they are, tap **CHAT ON WHATSAPP** | A WhatsApp chat with your own number opens, the message is ALREADY TYPED (starts "CodeC <version> · Android … · Project: …") and NOT sent — you read it, you press send |
| 2 | Tick ☑ *Include the last 120 log lines*, then tap **CHAT ON WHATSAPP** (same phone if you have no second device: skip this check there) — on a device WITHOUT WhatsApp, tap **CHAT ON WHATSAPP** | The prefilled message now contains a "--- log (last N lines, redacted) ---" section; on the WhatsApp-less device: nothing opens, but a toast says the report is copied, the number to write to is on screen, and pasting anywhere shows the full report |
| 3 | Untick the log box, untick *Include the last crash* (if prefilled), tap **COPY REPORT**, paste anywhere (e.g. the terminal) | The pasted report has NO "--- log" and NO "--- crash" section; it never contains your GitHub token (if you had one stored, paste a fake token in any compiler/terminal line first and confirm it shows `<redacted>`) |
| 4 | Developer Options → **FORCE CRASH** → reopen the app → CLEAR the crash dialog → Settings → Feedback & Support | The crash checkbox is PREFILLED (ticked) and says "prefilled — a crash from a previous session is ready to attach"; tick it, COPY REPORT, paste: a "--- crash ---" section with the exception line is present |
| 5 | In *WhatsApp number for replies*, type `12345` (do not save) | Inline message "that doesn't look like a WhatsApp number (with country code, e.g. +91 98765 43210)" — and SAVE says "Number NOT saved" |
| 6 | Leave the section (go to Editor), come back to Settings → Feedback & Support | The attachment checkboxes are back to their fresh state (log OFF; crash prefilled only if a crash is present) — your typed text is also gone: every report is a fresh choice |
| 7 | Tap **GITHUB ISSUE** | The browser opens github.com/pabi277/CodeC/issues/new with title + body prefilled (no login needed to see the form; submitting needs a GitHub account) |
| 8 | Tap **EMAIL** (if you set an address) | An email compose window opens with subject "CodeC feedback <version>" and the report in the body |

## The disclosure (look, not tap)

The three disclosure lines are ABOVE the checkboxes, so they are on screen
at the moment you tick either box — "CodeC sends nothing by itself…" /
"The report includes… never includes your files, your token, or your
code." / "If you write to a personal WhatsApp number, the owner can see
your phone number and profile…".

## Result

Report the check numbers that passed (e.g. "1-8 pass") plus the phone
model. Check 3's redaction half is already host-tested
(`FeedbackDraftTest`); the device round confirms what a human actually
sees.

---

## Round 2 — the follow-up build (separate screen + exit survey + your defaults)

> Install the NEW CI artifact from `arena/01a08cc6-codec` (the round-2
> commit). Round 1 (checks 1–8) is already PASSED and does not repeat —
> this round covers what changed. Your number and email are HARDCODED in
> the app now (round 2: no fields, nothing to set), so a fresh install (or
> clearing the app's storage first) is the honest starting point.

| # | What to do | PASS looks like |
|---|---|---|
| D1 | Fresh install → Settings → **Feedback & Support** → OPEN | The full-screen Feedback page opens (back arrow top-left). The CHAT row shows **+91 62967 46606** and the EMAIL button is there — and there are **no number/email input fields anywhere** (the developer contact is built in; round 2) |
| D2 | From the Projects tab (or wherever you start), press BACK once | The "Enjoying CodeC? 💚" popup appears: star row, SHARE EXPERIENCE, GIVE A REVIEW, NOT NOW, EXIT, "tap back again to exit" |
| D3 | With the popup open, press BACK again | The app closes (tap-again-to-exit). Reopen it |
| D4 | Popup again: tap 4 stars, then **SHARE EXPERIENCE** | The Feedback page opens with "Your exit rating: ★★★★☆ — it goes into the report below"; tap COPY REPORT and paste — the info line ends "· Rating: 4/5" |
| D5 | Popup: **GIVE A REVIEW** | The browser opens github.com/pabi277/CodeC |
| D6 | Popup: **NOT NOW** | The popup closes and the app STAYS open |
| D7 | Feedback page: turn OFF "Ask for feedback on exit" → press BACK (at a root tab) | The app closes directly — no popup. Turn it back ON if you want it during testing |
| D8 | Send yourself one CHAT message end-to-end | The WhatsApp chat that opens is YOUR +91 62967 46606 (hardcoded — you did not type it anywhere), message typed, not sent |

Report "D1–D8 pass" (plus the phone model). D8 is the one that closes the
"the number is not mine" finding from round 1.
