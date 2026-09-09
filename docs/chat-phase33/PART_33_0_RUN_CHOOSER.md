# CodeC Phase 33.0 — RUN ▶ chooser (the user's default file vs the open file)

**Status:** ✅ IMPLEMENTED + DEVICE-PASSED + **MERGED via PR #57** (2026-09-09, `arena/01a083fc-codec`) · **Cost:** `[client-only]` · **Effort:** S
· **Owner request:** "make the default run option as a user task if user set any default file than it will open with a option default or current file."

---

## 1. Design

The **default run file is a user-set choice** — the project's launch default
(`ProjectConfig.launchDefault`, set from the editor ⋮ menu "Set as launch
default"), NOT an automatic guess from the project type.

- **No default set** → RUN ▶ runs the file that is open, by its own type.
- **Default set AND it differs from the open file** → RUN ▶ asks with a
  two-button dialog: **Run `<default>`** / **Run `<open>`**. Outside-tap
  cancels.
- **Default IS the open file** → no dialog, it just runs.

Any run target can be the default — a C/Python/JS/shell source (runs in the
Output Panel) or an HTML file (opens Web Preview). This is exactly the
**Code-with-C** shape: a web project (`index.html` + `style.css` + `data.json`)
that also holds a folder of `.c` practice files — set your default (or none),
open any `.c`, and RUN compiles/runs that file instead of opening `index.html`.

## 2. Exit condition

```text
(Device)
1. Open a .c file in a web project with NO default set → RUN ▶ compiles and
   runs that .c in the Output Panel (never opens index.html).
2. Set a default (⋮ → "Set as launch default" on any runnable file) → RUN ▶
   on a different file asks "Run <default> / Run <open>".
3. Choose the default → it runs/previews; the open tab stays put.
4. Choose the open file (or tap outside) → unchanged / nothing.
5. Open the default file itself → RUN ▶ runs it directly, no dialog.
PASS = all five.

**Result (2026-09-09): ✅ DEVICE-PASSED** — owner: "Ok working as i wanted".
```

## 3. Implementation

- **`ui/projects/ProjectRunTarget.kt` (pure)** —
  - `isRunTarget(rel)`: a file RUN can act on (a language profile — HTML
    preview or a panel-runnable source).
  - `isRunnableSource(rel)`: a panel-runnable source (a run profile that is
    NOT the web preview) — lets a C/Python file inside a `web` project be RUN
    instead of previewed.
  - `chooserEntry(root, entry, openFile)`: raw confinement (real file under
    root, differs from open, traversal refused).
  - `chooserDefault(root, default, openFile)`: the RUN decision — null unless
    a default is set, both files are run targets, and the default resolves to
    a real file differing from the open file.
- **`EditorScreen`** — `runChooserEntryOrNull()` reads the user's
  `config.launchDefault` (authoritative, no StateFlow timing) and asks only
  when `chooserDefault` is non-null; `runOpenFile()` runs the open file by its
  own type (HTML → preview; runnable source → `runActiveFile` even inside a
  web project; else web entry → preview); `runDefaultFile()` previews an HTML
  default or `runFile(...)`s a source default. The ⋮ "Set as launch default"
  item now shows for ANY run target (was HTML-only); "Clear launch default"
  unchanged. `webDefaultEntryOrNull()` only uses the launch default when it
  names an HTML file, so a C default never breaks the 👁 preview path.
- **`EditorViewModel`** — the `web` project early-return in `runFile` now
  skips only non-runnable files (`ProjectRunTarget.isRunnableSource`), so a
  runnable source falls through to the normal registry run path.
- **`ProjectRunDetector`** — for an `auto` project (clone/import writes this
  type), a runnable source open as the active file now returns
  `AutoRunPlan.Project(c|python)` instead of letting the root scan shadow it
  with `AutoRunPlan.Web("index.html")`; this is what finally stopped
  "RUN on a C file opened index.html".
- **`ShellEnvironment.ccScript()`** — the `cc` frontend flattened converted
  args into a string and expanded it unquoted, word-splitting a source path
  with a space (`C Programming/…`); it now rebuilds its args via
  `set -- "$@" "$arg"` and passes `"$@"` to TCC, so the space path survives.
- **`CompilerDiagnostics.looksLikeMissingMain`** — a failed build whose
  output is a "no main" linker error (a lone fragment compiled out of a
  multi-file menu project like Code-with-C) now gets a plain-language hint
  line in the Output Panel (`output_no_main_hint`).
- **`CEntryWrapper`** — the owner's practice files are self-contained C
  programs whose entry may be named anything, not `main`: a single C file
  with no `main` and exactly one function now compiles through a generated
  wrapper (`#include` the file + supply `main()`) and runs directly, no
  `.codec.json` needed.
- **Strings** — `run_chooser_body` now reads "Default file: …".

## 4. Tests & validation

- `ProjectRunTargetTest` (12 host cases): a set default differing from the open
  file is offered; the Code-with-C shapes (index.html default vs an open .c, a
  C default vs an open .c); open==default / no default / no open → nothing to
  choose; a missing or non-run-target default is not offered; a non-run-target
  open file is never asked; entry confinement; `isRunTarget` and
  `isRunnableSource` classification (C/Python/JS/sh run; HTML/CSS/MD do not).
- Local pre-validation (Temurin 25 + kotlinc 2.4.10): the pure file passes a
  main-harness over the real `LanguageRegistry` + `WebFileSupport` +
  `ProjectPathUtils`; the JUnit source type-checks against a shim.
- **CI:** `34314971389` ✅ GREEN (tip `a37e8c2`, 4m37s) and the auto-project
  fix `34317268507` ✅ GREEN first try (tip `270c70d`, 5m45s). **Device round
  (2026-09-09) ✅ PASSED — owner "Ok working as i wanted"; MERGED via PR #57.**

## 5. Evolution note

The first cut (same session) drove the chooser from `ProjectConfig.entry`
automatically. The owner's "Code-with-C" example — a web project whose
`index.html` is the entry but whose real content is a folder of `.c` practice
files — showed that the default must be a **user** choice, not the entry, so
the chooser now keys off `launchDefault` and RUN of a source file no longer
routes through the web-preview branch at all.
