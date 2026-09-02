# CodeC Website Phase W6.5 — Chapter 17: Troubleshooting

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W3.3 (the /faq content — same sources, deeper form)
· **Target file:** `website/ch-17.html`

> Source: `README.md` §Troubleshooting + `docs/TROUBLESHOOTING.md` —
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
2. Answers diffed against `/faq` + `docs/TROUBLESHOOTING.md` (drift = repo
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
