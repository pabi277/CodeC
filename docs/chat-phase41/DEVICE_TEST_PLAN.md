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
