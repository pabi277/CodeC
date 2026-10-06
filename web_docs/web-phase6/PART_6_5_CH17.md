# CodeC Website Phase W6.5 — Chapter 17: Troubleshooting

> **Current batch status — 2026-10-07, D33: CONTENT IMPLEMENTED; DEVICE ACCEPTANCE OPEN.**
> Owner authorized W3–W6 content together, then one commit/watch; separate start
> commands/intermediate commits below are superseded. W4.2 verification ran FIRST.
> [Batch record](../chat-web6/BATCH_SUMMARY.md) · [checks](../chat-web6/CHECKS.md) ·
> [device gates](../chat-web6/DEVICE_TESTS.md) · [current full ZIP](../chat-web6/review-zips/CodeC-website-W6-review.zip).
> D31 download law retained. No device pass, PR, merge or public deployment implied.

> **Binding v2.3 amendment — 2026-10-07 (scope supplied 2026-10-06).**
> Apply [WEBSITE_PLAN.md](../WEBSITE_PLAN.md) and the current instructions below
> before the retained prior design. They supersede conflicting old facts and exits:
> **29 pages / 9 product / 19 chapters**; app-v1.3.18 **7 217 532 B, versionCode 22**;
> AI in header, Privacy every footer; contacts in-app only; README then AI/privacy
> guides; Phases 1–96 shipped except cancelled proposals, 97+ roadmap. Never pair
> current APK size with historical -74%. No picker, Open Folder, ProjectLink,
> first-hour tiles, mandatory tour or automatic userland setup in visitor teaching.
> Earlier session-folder references are historical: preserve existing records;
> W1 uses `chat-web4/W1_*`. No next phase, PR, merge or deploy without command.

**Current status:** CONTENT IMPLEMENTED; DEVICE ACCEPTANCE OPEN; owner-authorized combined batch (D33).

## Current v2.3 implementation requirements

This is NOT the last chapter. Chapter 17 of 19, prev ch-16, NEXT ch-18; remove
course-complete/Back-to-home ending. Keep BETA B-1…B-8 and diagnostic ladder,
crash-log header-first COPY ALL, feedback/Issues. Correct updater path to Settings
→ About → Check for updates; never tell user to switch engine or tap Open Folder.
Offer Explain last error with preview/redaction and AI prerequisites, not a silent
upload. Course completion is reserved for W6.9/ch-19.

## Current acceptance additions (binding)

1. Follow the current requirements above and the master plan, not superseded UI/copy.
2. Preserve each non-conflicting numbered exit below, including device gates.
3. Record source file/line/sha, responsive checks and self-dependent sweep; use 19-chapter crumbs where applicable.
4. No app/workflow changes or phase expansion; update web living docs and stop at merge gate.

---

## Prior design detail — retained v2.2 record

**Historical reference, not authority for superseded facts or exit counts.**

> **v2.2 update (2026-09-12):** synced with app Phases 21–43 — universal APK 6.6 MB signed SHA256, Auto engine only picker deleted Termux card deleted fallback automatic error-path Output Panel, >_ mark icon, official file icons Seti MIT, typing feel, terminal multi-session + LAN server opt-in 0.0.0.0 two URLs QR ZXing open-in-browser keep-alive, outputs temporary RunArtifacts+RepoHygiene ~60 patterns .codec/ user .gitignore wins, GitHub truth GitReadiness+PushOutcome+Publish POST /user/repos, feedback hardcoded +91 62967 46606 / email + FeedbackScreen + exit survey + crash-log header-first + OpenInBrowser no telemetry, backup include-list-only FullBackupContent law crash-loop guard safe mode 3rd launch export-all over both roots 11 privacy rows RELEASE_NOTES template {{SHA256_LINES}}, safe walk Throwable-safe TreeWalkPolicy ProjectLink persisted SAF grant noexec mirror, BETA B-1…B-8, TCC null on armeabi-v7a/x86.

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W3.3 (the /faq content — same sources, deeper form)
· **Target file:** `website/ch-17.html`

> Source: `README.md` §Troubleshooting + `docs/guides/TROUBLESHOOTING.md` —
> same answers as `/faq`, in course depth, in the chapter template.

---

## 1. Content

- **Goal box:** when something breaks, you know the five suspects, how to
  read the logs, and how to file a bug that gets fixed.
- **Need:** the whole course (this is the survival chapter).

### Steps

1. **The five suspects** — the five `/faq` entries as a diagnostic ladder,
   each: *symptom → the real cause(s) → the fix in order*:
   compiler could not start · "Permission denied" (W^X / noexec) ·
   "Exec format error" (CPU) · "Runtime libraries missing" ·
   hangs (the 30 s / 10 s caps). Each gets a "what just happened" line
   (the plain-English mechanism) beyond the FAQ's length.
2. **Read the logs** — Settings → Developer Options → **Logs**; the
   **"Device: …"** line (ABI + app storage mount flags); when to include
   it (always, in a bug report); how to find the mount flags' meaning in
   one line (noexec = nothing can execute there — no app's fault).
3. **The fix ladder, in order** — update the app (Settings → Install APK
   from GitHub) → reinstall once (Android's sandbox labeling) → switch
   engine (Auto → Termux) → check the device's class (real phone vs
   noexec cloud phone) → open Issues. (Same order as `/faq`, taught as a
   decision path: "which line are you on?")
4. **When it's your code, not the app** — the three tells: the squiggle
   (compile error — chapter 07), the crash with no app error (your
   program — chapter 08's mistakes box), the output that's *wrong but
   present* (logic — reread the last loop). Two lines each, kindly.
5. **File a bug that gets fixed** — the minimum report: the exact error
   text, the Device: line, the steps, what you expected; where (GitHub
   Issues — link); what makes a report "good enough" (three bullets).

- **Try it:** (1) open Logs on your own phone and copy your Device: line
  (you now know what every field means); (2) trigger a harmless compile
  error (missing `;`) and screenshot-free describe the squiggle → quick
  fix → clean compile; (3) draft a bug report on paper for a made-up
  symptom using the step-5 template — check it against the three
  "good enough" bullets.
- **Mistakes:** reinstalling five times before reading the error text
  (read first — the text names the suspect); skipping the Device: line
  (the fix often depends on it); reporting "doesn't work" (the template in
  step 5 takes 60 seconds).

## 2. Implementation steps

1. Build `ch-17.html` (crumb "Chapter 17 of 17" — the last; **next →
   learn.html** ("Back to the course home") and prev → ch-16; note the
   course-complete one-liner in the closing box).
2. Answers diffed against `/faq` + `docs/guides/TROUBLESHOOTING.md` (drift = repo
   wins, both pages fixed in the same commit if needed); source notes in
   `chat-web6/`.
3. Self-dependent sweep.

## 3. Exit condition (W6.5)

```text
1. Template complete with the course-completing footer; prev → ch-16,
   next → learn.html.
2. Five suspects + fix ladder == /faq == docs (diff recorded).
3. 360/1440 clean; sweep PASS. → Course content complete (17/17).
```
