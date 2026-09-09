# CodeC Phase 33.0 — RUN ▶ chooser (main/index file vs the open file)

**Status:** ✅ IMPLEMENTED (2026-09-09, `arena/01a083fc-codec`) · **Cost:** `[client-only]` · **Effort:** S
· **Owner request:** "I want it to be like when user hit run it asks index/main file or the file is currently opened."

---

## 1. Design

RUN ▶ previously ran the OPEN file (HTML → preview; web project → its entry;
else `runActiveFile`) — there was no way to run the project's main file
without first switching to it. Now, when a project has a real main/index file
(`ProjectConfig.entry` — main.c / index.html / main.py / app.py …) that is
**not** the file currently open, tapping RUN ▶ asks with a two-button dialog:

- **Run `<main>`** — the project's main/index file.
- **Run `<open>`** — the file currently open (the pre-33 behaviour).

Tapping outside cancels (runs nothing). When the open file IS the main file,
or the project has no such file, or the open file is not itself runnable, RUN
behaves exactly as before — no dialog.

**A `web` project can still run its source files (owner bug, 2026-09-09):**
an HTML project holding `main.c` / `main.py` now RUNs those files in the
panel instead of always previewing `index.html`. `ProjectRunTarget.isRunnableSource`
(a run profile that is not the web preview) gates both the editor's dispatch
(`runOpenFile`) and the ViewModel's web-project early-return.

## 2. Exit condition

```text
(Device)
1. Open utils.c in a project whose main file is main.c, tap RUN ▶ →
   dialog offers main.c and utils.c.
2. Choose main.c → main.c compiles/runs; the open tab stays utils.c.
3. Choose utils.c (or tap outside) → unchanged / nothing.
4. Open main.c itself, tap RUN ▶ → no dialog, main.c runs directly.
5. In an HTML project holding main.c: RUN ▶ on main.c runs it in the
   panel (never previews index.html); RUN ▶ on index.html previews.
PASS = all five.
```

## 3. Implementation

- **`ui/projects/ProjectRunTarget.kt` (new, pure)** — `chooserEntry(root,
  entry, openFile)` returns the entry's root-relative path when it resolves to
  a real file under `root` AND differs from the open file, else null;
  `shouldAsk(..., openRunnable)` adds the guard that the open file is itself a
  run/preview target (HTML or a `LanguageRegistry` profile), so the "current
  file" arm is never a no-op.
- **`EditorScreen`** — `runChooserEntryOrNull()` computes the entry to offer;
  the RUN ▶ `clickable` shows the `AlertDialog` when it is non-null, else
  `runCurrentFile()` (the pre-33 branch, extracted verbatim: HTML → preview,
  web → its entry, else `runActiveFile`). `runMainFile(entryRel)` previews
  when the entry is HTML or the project is web, else `viewModel.runFile(...)`.
- **`EditorViewModel.runFile(context, target)`** — the `runActiveFile`
  pipeline parameterised on the source file. `target` (root-relative) is used
  for the project branch's auto-detect / registry / `.codec.json` decisions
  and `preferInteractive`; a dirty background tab of the target is flushed to
  disk first (`writeProjectFile`) so the run matches what that tab shows; the
  open file is NOT switched. Diagnostics are attributed to the target's
  basename (`runTargetBasename`, read by `finishFailedBuild`) so a failing
  main-file build still shows squiggles and tap-to-line with another file
  open; the auto-install gate resumes the same target (`pendingRunTarget`,
  used by `confirmInstall`) after a toolchain install.
- **Strings** — `run_chooser_title` / `run_chooser_body` / `run_chooser_run`.

## 4. Tests & validation

- `ProjectRunTargetTest` (8 host cases): main vs open offered; open == main →
  no choice; missing main → null; non-runnable open → not asked; web
  index.html vs about.html; entry confinement (traversal refused); a runnable
  source inside a web project is recognised (main.c / tool.py / main.cpp /
  build.sh / app.js); web and non-source files are not panel-runnable
  (index.html / about.htm / style.css / README.md / null).
- Local pre-validation (Temurin 25 + kotlinc 2.4.10): passes over the real
  `ProjectPathUtils` + `ProjectRunTarget`; the JUnit source type-checks
  against a shim. **CI `34307172630` ✅ GREEN first try (tip `788ba46`,
  5m22s)**; the web-project fix re-ran **CI `34309463999` ✅ GREEN first try
  (tip `9a11382`, 5m58s)**. Device round pending.
EOF
