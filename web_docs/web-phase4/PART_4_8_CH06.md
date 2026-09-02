# CodeC Website Phase W4.8 — Chapter 06: Files & Projects

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4.2 gate
· **Target file:** `website/ch-06.html`

---

## 1. Content

- **Goal box:** create a real project; move files between single-files and
  projects; import and export; know exactly where everything lives.
- **Need:** Chapters 02–03 done.

### Steps

1. **Two home bases** — *single files* (quick experiments, where chapter 02
   happened) vs *projects* (real work, private folders
   `files/CodeC/projects/<name>` — the exact path from W4.2 facts).
   When to graduate a file: "Save to project…" from the file sheet.
2. **The Projects hub** — the card list (type mark, branch · file count ·
   age, change badge, the amber **↑N** when commits never reached the
   remote — preview of chapter 11), filter chips + search, and the ONE `+`
   sheet with its four doors: **New Project / Clone Git Repo / Import ZIP /
   Open Folder** (one line each; clone = chapter 11's deep dive).
3. **Create a project** — `+` → New Project → name it `first-project` →
   open it; the nav-drawer file tree appears (with in-tree git status
   letters — name them, don't explain them all yet).
4. **Move a file into it** — from single files: "Save to project…" →
   `first-project`; the file now lives in the tree; tabs re-key, the
   terminal follows the project.
5. **Import & export** — Import ZIP (from a shared file), Open Folder (SAF
   picker), and export the project (SAF/ZIP) — the three ways code gets in
   and out of the app; two lines each + where the buttons are.
6. **What git sees** — build outputs (`a.out`, `bin/`, `dist/`, …) are kept
   out of git automatically by a repo-local ignore — **your `.gitignore` is
   never touched** (plain paragraph; depth link chapter 11).

- **Try it:** (1) create `second-project`, move `hello.c` into it, open it
  from the Projects hub (the app opens straight into the file you left in);
  (2) export `first-project` as a file and find it in your file manager;
  (3) compile inside a project (`cc hello.c -o a.out` from Term) and
  confirm the project's ignore keeps `a.out` out of git's view.
- **Mistakes:** editing a file while switching folders (autosave + the
  in-editor folder switch handles it — the preview/terminal follow the
  *project you're in*, not a stale route); looking for projects in public
  storage (they're app-private — that's why `./a.out` runs); ZIP import
  with nested folders (open the ZIP's top-level folder, not the ZIP, in the
  picker — follow the on-screen prompt).

## 2. Implementation steps

1. Build `ch-06.html` (crumb "Chapter 6 of 17").
2. UI names/paths from W4.2 facts; source notes in `chat-web4/`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-05, next → ch-07.
2. Project path, + sheet items, ignore behavior == W4.2 facts (noted).
3. W4 total check: /learn + ch-01…ch-06 all render, template consistent,
   O6 closed, verified-facts table committed → W4 COMPLETE; report + merge
   gate.
```
