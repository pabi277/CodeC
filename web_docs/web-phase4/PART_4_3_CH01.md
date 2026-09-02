# CodeC Website Phase W4.3 — Chapter 01: Getting Started

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4.2 gate (verified facts)
· **Target file:** `website/ch-01.html`

---

## 1. Content (chapter template, plan §3.2)

- **Goal box:** install CodeC on a fresh phone; open it; name all five tabs
  of the bottom bar; open the editor and the terminal once.
- **Need:** an Android phone (arm64 best), 10–20 minutes, a browser for the
  APK download.

### Steps

1. **Get the APK** — brief 3-path recap (GitHub Actions artifact / Release /
   in-app later), then: tap the file, allow *Install unknown apps*, open.
   (Details live on `/install` — linked, not duplicated beyond two lines.)
2. **First screen** — what opens (Projects hub on first launch), one
   paragraph, no fear: "Everything you'll ever need is in this bottom bar."
3. **The bottom bar map** — the five tabs, one line each: **Projects**
   (your work) · **Editor** (where files are written) · **Terminal**
   (middle — the real terminal) · **Packages** (install & run software) ·
   **Settings** (engines, keys, updates, logs).
4. **Touch the editor** — open the single-files sheet, create a file named
   `note.txt`, type one line, watch **autosave** (~2 s) do its job.
5. **Touch the terminal** — open Term (tab or the editor's terminal icon);
   read the prompt; type `ls` and `pwd`; read where you are (the `$PREFIX`
   home, per W4.2 facts).

- **Try it:** (1) open Packages tab and read one status badge aloud;
  (2) create `note.txt`, type "hello codec", switch tabs, switch back —
  it's still there; (3) in Term: `pwd` — find `files/usr` in the output.
- **Mistakes:** app won't open (reinstall once — Android's sandbox
  labeling); prompt looks different on your model (normal — what matters is
  the command, not the colors); tapping RUN before writing a file.

## 2. Implementation steps

1. Build `ch-01.html` on the canonical template; crumb "Chapter 1 of 17".
2. Facts taken from `VERIFIED_FACTS.md` (W4.2); source notes in
   `chat-web4/`.
3. Self-dependent sweep (plan §5.5).

## 3. Exit condition

```text
1. Template complete (crumb, goal box, 5 steps, 3 try-its, mistakes,
   prev/next — next → ch-02.html, prev → learn.html).
2. Every command (`ls`, `pwd`, file creation) works on a fresh install per
   verified facts.
3. 360/1440 clean; sweep PASS.
```
