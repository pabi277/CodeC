# CodeC Phase 12 — Multi-Language Support, Python & Code Intelligence

**Status:** ✅ **IMPLEMENTED, CI-VERIFIED, REPOSITORY-PUBLISHED &
DEVICE-ACCEPTED (2026-08-30, `arena/01a05221-codec`)** — repo-build
config (python + python-pip added to `CODEC_REPOSITORY_PACKAGES`, tk/X11
recipe override + maintainer-script neutralization) and client work
(multi-language highlighter, autocomplete popup, python run path) are
committed; host repo tests green (85 OK, 4 gpg skips); **`Build APK` CI
green** (`33308137225` / `33314362040` / `33323569312`); **`[repo-build]`
built & published** (build `33314588441` both arches → publish `33320104745`
on `main` via `source_run_id`; catalog verified live at
`pabi277.github.io/CodeC/dev` — `python` 3.14.6-1, `python-pip` 26.2.1,
`python-ensurepip-wheels`, `python-static`; `python-tkinter` absent).
**Device (2026-08-30): the FULL §4 recipe passed.** `pkg install -y python`
works (3.14.6-1, preflight PASSED); Python keywords highlighted; `def `
autocomplete popup appears and TAB inserts; python RUN prints `Pi is 3.1416`;
C active-file RUN works — including the two run-path bugs found on device
(`.py` saved as `.py.c`; project RUN always built `main.c`), both fixed and
CI-green (`e4c5d48`, `9bfe216`). Owner: "Now python is solved" → "Worked
properly" → "Both working"). **PR #30 MERGED to `main` at `260d8b6` (2026-08-30) — Phase 12 COMPLETE.**
**Cost:** `[repo-build]` (ONE planned CI package build ~1–2h) · **Depends on:**
Phase 8 (Projects) + Phase 9 (Editor) + Phase 10 (Package Hub)
**Target Files:** `codec-packages/properties.codec.sh`, `codec-packages/scripts/apply-recipe-overrides.sh`,
`MultiLanguageSyntaxHighlighter.kt`, `CodeCompletionEngine.kt`, `EditorScreen.kt`,
`TerminalHandoff.kt`, `EditorViewModel.kt`

---

## 1. Context & Motivation

CodeC's syntax highlighting and execution have historically been tailored to C. With the signed repository and package manager operational, Python 3 is the most requested language for mobile development (data analysis, scripts, web backends with Flask/FastAPI, automation).

Phase 12 delivers:
1. **Python 3 Package in Repository (`[repo-build]`):** The only planned CI repo build (~1–2 hours) compiles `python` (with standard libraries and pip) for `aarch64` and `x86_64` and publishes to the CodeC repository channel.
2. **Multi-Language Syntax Engine:** Extend syntax highlighting to Python, JavaScript, HTML/CSS, JSON, and Shell scripts.
3. **Buffer & Snippet Autocomplete:** Lightweight in-app autocompletion scanning identifiers in the active buffer + standard library snippet templates without heavy external language servers.
4. **Python Project & Server Presets:** 1-tap run `python3 main.py` and automatic Web Preview for `python3 -m http.server` and Flask.

## 2. Architectural Design (Decision D1)

### 2.1 Repository Build Recipe

**Mechanism (verified against the build machinery, not the plan's earlier
wording):** CodeC does not vendor recipes under `codec-packages/packages/`.
`build-package-repository.sh` clones the pinned termux-packages revision
(`1bbe66903526df2e8af51e704316bc68ede72603`), applies `apply-prefix.sh` +
`apply-recipe-overrides.sh`, and builds every root named in
`CODEC_REPOSITORY_PACKAGES` (`codec-packages/properties.codec.sh`) via
`build-package.sh -f`. **Phase 12 changes:**

- `CODEC_REPOSITORY_PACKAGES` += `python`, `python-pip` (both recipes exist at
  the pin: python 3.14.6 rev 1; python-pip 26.2.1).
  `python-ensurepip-wheels` is a subpackage of python and ships with it.
- **Recipe override** in `apply-recipe-overrides.sh` (fail-loud on
  pinned-revision drift, same pattern as the gitk/git-gui round-2 override):
  the official python recipe declares `TERMUX_PKG_BUILD_DEPENDS="tk"` and
  ships a `python-tkinter` subpackage; tk pulls the whole X11 closure
  (fontconfig, libx11, libxft, libxss, tcl) solely to build Tkinter. CodeC's
  userland has no X11 use for Tkinter → the tkinter subpackage is excluded
  (`TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64"`) and `tk` is removed from
  python's build-depends, keeping the ONE planned build inside budget.
- **Maintainer-script neutralization** (found by the first `[repo-build]`
  dispatch `33308884424`, which built python + python-pip successfully but
  aborted at repository generation — `generate-repository: ERROR:
  python-pip_26.2.1_all.deb: maintainer scripts are not allowed: postinst,
  prerm`): both the python and python-pip recipes define their own
  `termux_step_create_debscripts()` (pip-separation postinst; pip
  version-check postinst + pip.conf prerm). Because recipes are sourced
  AFTER the step scripts, those per-recipe definitions override the shared
  CodeC no-op stub, so the debs shipped with `DEBIAN/postinst`(+`prerm`) —
  which the CodeC validator rejects (maintainer scripts are forbidden for
  every package except the five reviewed update-alternatives packages).
  The override now appends a last-defined `termux_step_create_debscripts() {
  :; }` to both recipes (bash last-definition-wins), fail-loud if either
  recipe's per-recipe definition disappears. The first dispatch also
  surfaced that the pinned python recipe's `termux_step_post_massage()`
  hard-verifies `_tkinter` was built (impossible once tk is dropped) — an
  overriding post-massage validates the same modules minus `_tkinter`.
- **Repository-only:** the bootstrap seed and package-manager roots are
  unchanged, so the published `userland-v2-dev` bootstrap archives stay
  byte-identical; python is installed on demand (`pkg install -y python`).
- Preflight validation with `validate-repository.py` unchanged.

### 2.2 Multi-Language Syntax Highlighter (`MultiLanguageSyntaxHighlighter.kt`)

```kotlin
enum class LanguageType(val label: String, val extensions: List<String>) {
    C(listOf("c", "h")),
    CPP(listOf("cpp", "hpp", "cc", "cxx", "hxx", "hh")),
    PYTHON(listOf("py", "pyw")),
    JAVASCRIPT(listOf("js", "jsx", "ts", "tsx", "mjs", "cjs")),
    HTML_CSS(listOf("html", "htm", "css", "scss", "xml")),
    JSON(listOf("json")),
    SHELL(listOf("sh", "bash", "zsh")),
    MARKDOWN(listOf("md", "markdown")),
    TEXT(listOf("txt", "log"))
}
```
- Tokenizer: single-pass ordered alternation per language — comments and
  strings claim their whole range first (so content inside them is never
  re-tokenized), then numbers, keywords, decorators (`@…`), functions
  (`word(`), operators. C/C++ preprocessor directives (`#include`, `#define`)
  share the keyword color. Shell `$VAR`/`${…}`/positional params are
  operators; Markdown gets headings, fenced/inline code, links, emphasis.
- Theme-aware color token assignment (`colors.keyword`, `colors.string`,
  `colors.function`, `colors.number`, `colors.comment`) via `TokenKind.color`.
- `SyntaxVisualTransformation` (renamed from `CSyntaxVisualTransformation`)
  keeps every Phase 9 decoration layer (current line, diagnostics squiggles,
  find matches, brackets) and is identity-mapped; default `LanguageType.C`
  preserves the existing C look. Language is derived from the active file
  extension in `EditorScreen`.

### 2.3 Buffer & Snippet Autocompletion Engine (`CodeCompletionEngine.kt`)

- **Identifier Scanner:** regex over the buffer collects user symbols
  (`\b[A-Za-z_][A-Za-z0-9_]*\b`), excluding the typed prefix itself and the
  language keywords; up to 3 offered after matching snippets.
- **Snippet Presets** (word-aware matching: `mai` → `int main(void) {`,
  `inc` → `#include <stdio.h>`):
  - Python: `def function():`, `class ClassName:`, `if __name__ == '__main__':`,
    `for item in iterable:`, `try/except`, `import `, `from … import`, `print(`,
    `with open(…) as f:`.
  - C/C++: `int main(void) { … }`, `printf(...)`, `for (…i…) {`, `if (cond) {`,
    `while (cond) {`, `#include <stdio.h>`, `typedef struct …` (+ `class` for C++).
  - JS: `function name() {`, `const name = value;`, `console.log(...)`,
    `for (let i …)`, `if (cond) {`, `import name from 'module';`.
  - Shell: `if […]; then … fi`, `for x in list; do … done`, `while …; do … done`,
    `case $x in … esac`, `function name() {`, `echo `.
- **Floating Suggestion Popup** (EditorScreen): appears while typing a prefix
  (snippets → buffer identifiers → keywords, capped at 8) or right after a
  trigger word (`def `, `import `, `#include`, `printf`, `if `, …). Anchored
  near the cursor's text-layout rect (line-number gutter + scroll offsets
  folded in). **TAB/ENTER** insert the highlighted item, **↑/↓** cycle,
  **ESC** dismisses until the next edit; tapping an item inserts it. Insert
  replaces the typed prefix and puts the caret after the inserted text.

### 2.4 Python Run Path

- Single-file RUN ▶ (`.py`): no compile step — `interpretedParts` returns
  `null` build + `python3 <file>`; the Output Panel goes straight to RUNNING;
  Open-in-Terminal uses `cd <dir> && python3 <file>`.
- Project RUN ▶ runs the **active file**, not the project's configured main:
  a `.py` active file runs `python3 <file>`; a `.c`/`.cpp` active file
  compiles in place (`mkdir -p bin && cc <file> -o bin/<name>.out &&
  ./bin/<name>.out`, same as the tree's per-file "Run in terminal"). The
  project.json build/run command still drives everything else (headers,
  text, custom multi-file builds). Device-found twice: first the panel
  echoed the C command for a `.py` active file ("print the main.c"); then
  any C run printed the Hello-World of `main.c` because RUN ▶ always built
  the project's configured `main.c` regardless of the active file. **Both
  fixes device-verified 2026-08-30 (owner: "Now python is solved"; "Worked
  properly" for C active-file runs).**
- Project-tree "Run in terminal" (`.py`): `cd <project> && python3 <file>`.
- Project presets pre-existed: `ProjectConfig.defaultFor(type="python")` →
  `{"type":"python","build":"","run":"python3 main.py"}`; `ModuleCatalog`
  python entry (`pkg install -y python`); the Phase 11 `projectRunParts`
  already handles empty-build projects.
- **Naming fix (device-found):** `WebFileSupport.normalizeFileName` only kept
  `.c`/web extensions, so a `.py` file was renamed to `test.py.c` on save —
  reclassifying it as C and routing RUN ▶ through `cc` ("it auto saves the
  python as c"). It now keeps every `LanguageType` extension (py, pyw, sh,
  json, md, cpp, …); bare names still default to `.c`. A never-named scratch
  buffer (default `main.c`/`untitled.c`, never saved) whose content is clearly
  Python is saved as `.py` (`WebFileSupport.looksLikePython`) so RUN ▶ uses
  python3; `.py` files created via "+ New file" get a Python starter
  (`def main(): …`).

## 3. Implementation Steps

1. ✅ **Step 1:** Add `python`/`python-pip` to `CODEC_REPOSITORY_PACKAGES`,
   recipe override (tk/tkinter + maintainer-script neutralization + `_tkinter`
   post-massage override), host tests — committed `9b8943b`, hardened by
   `a007aa3` (post-massage) and the debscripts fix. **Dispatch of the
   `[repo-build]` is owner-run, per standing rule.** First dispatch
   `33308884424` built python+python-pip but failed at repository generation
   on python-pip's maintainer scripts (see §2.1); the fix is committed and
   host-tested, and the build is ready to re-dispatch.
2. ✅ **Step 2:** `MultiLanguageSyntaxHighlighter.kt` (LanguageType + tokenizer
   + `SyntaxVisualTransformation` rename) — committed `b876667`.
3. ✅ **Step 3:** `CodeCompletionEngine.kt` (identifier scan + snippet
   dictionary) — committed `b876667`.
4. ✅ **Step 4:** Autocomplete popup in `EditorScreen.kt` (cursor-anchored,
   TAB/ENTER/↑/↓/ESC, tap-to-insert) — committed `b876667`.
5. ✅ **Step 5:** Python run preset already existed (`ProjectConfig`); single
   file + project-tree RUN routed through `python3` (`TerminalHandoff` +
   `EditorViewModel`) — committed `12411cc`.
6. ✅ **Step 6:** Unit tests `SyntaxHighlighterTest.kt` (12) +
   `CodeCompletionTest.kt` (12) + `TerminalHandoffTest` python additions (3) —
   pure Kotlin; executed on CI (`Build APK` runs `:app:testDebugUnitTest`).

## 4. Exit Condition & Verification Recipe

A fresh APK passes the following recipe on device (owner-run, after the repo
build is published to the dev channel):

```sh
# Setup & Python Verification
# 1. Open Packages tab -> Tap INSTALL on "python" (pkg install -y python).     [✅ 2026-08-30: installed 3.14.6-1 + deps, preflight PASSED]
# 2. Open Terminal -> Run: python3 --version -> Verify Python 3.x prints.      [✅ 2026-08-30: python3 works on device]
# 3. In Files tab, create new file "script.py".                                [✅ 2026-08-30: .py keeps its extension; python RUN works]
# 4. Open "script.py" in Editor -> Observe Python keywords (def, import, print, class) highlighted.   [✅ 2026-08-30: "Both working"]
# 5. Type "def " -> Observe autocomplete popup appears with function template -> Press TAB to insert. [✅ 2026-08-30: "Both working"]
# 6. Type code:
#    import math
#    print(f"Pi is {math.pi:.4f}")
# 7. Tap "RUN ▶" in toolbar -> Observe Output Panel shows "Pi is 3.1416" with exit code 0.  [✅ 2026-08-30: python RUN works; owner "Now python is solved"]
# PASS  ✅ 2026-08-30 — ALL steps device-verified (owner: "Now python is solved" / "Worked properly" / "Both working")
```

Regression check (Phase 9/11 behavior must be unchanged): C file highlighting,
squiggle taps, find/replace, RUN ▶ for C single files and C projects, the
completion popup must not interfere with the C editor.

## 5. Invariants & Guardrails

- Python build must strictly follow CodeC's prefix `/data/data/com.codeci.ide/files/usr` (no `com.termux`).
- Signed repository metadata must be updated with `generate-repository.py` and OpenPGP subkey signing (unchanged pipeline).
- Bootstrap seed/manager roots unchanged — published bootstrap archives stay byte-identical.
- No `.` on `PATH`, no `build-package.sh -I`, no official Termux packages/repositories (unchanged).
