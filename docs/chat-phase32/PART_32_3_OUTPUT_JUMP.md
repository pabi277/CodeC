# CodeC Phase 32.3 — Output peek and jump

**Status:** ✅ IMPLEMENTED & DEVICE-PASSED (owner: "All test passed on device", 2026-09-09) · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** Phase 11 output panel, `CompilerDiagnostics`

---

## 1. Design

First RUN on a session **expands** the output peek (Pydroid yellow-▶
feel), then may collapse. Tap `file:line` in output / squiggle → caret
on that line in the **user** filename, not `source_<stamp>.c`.

## 2. Exit condition

```text
(Device)
1. RUN hello.c — output visible without opening Terminal tab.
2. A compile error tap jumps to the right line in the open tab.
PASS = both.
```

## 3. Implementation

- **First-RUN expansion** is already Phase 11 behaviour — `runActiveFile`
  expands the Output Panel on every run; the collapsed 64 dp peek / clear /
  splitter are unchanged. No code needed (and no `firstRunEver` flag added:
  always-expanding on RUN is the intended "run ⇒ see output" contract).
- **`ui/editor/OutputDiagnosticTarget.kt` (new, pure)** — `resolve(root,
  raw)` (confinement: absolute paths must live under the root; must be a real
  file), `targetOrActive(root, raw, activeFile)` (a resolvable file → its
  root-relative path; otherwise the ACTIVE file), `isTempSource(name)`
  (`source_*`, `/tmp/…`). Host-testable, `java.io` only.
- **`EditorViewModel.openOutputDiagnosticFile`** — previously a diagnostic
  naming a temp / non-resolvable file returned `false` and the tap was
  silently ignored. Now it resolves through `OutputDiagnosticTarget` and
  falls back to `_fileName.value` (the user's file — the run was launched
  from it). `jumpToOutputDiagnostic` / `applyFixForOutputDiagnostic` both
  route through it, so a tap always lands in the open tab. The private
  `resolveDiagnosticFile` helper was removed (superseded by the pure file).
- `source_<stamp>.c` exists only in `CompilerService.kt`'s temp compile path
  (Settings' "test compiler"); the RUN ▶ path compiles the real file via the
  `cc` frontend, so a fallback is belt-and-braces — the tap must never dead-end.

## 4. Tests & validation

- `OutputDiagnosticTargetTest` (6 host cases): relative + absolute-in-root
  resolve; root-escape refused; temp name (`source_12345.c`, `/tmp/source_9.c`)
  falls back to the active file; a missing file falls back too; `isTempSource`
  classification.
- Local pre-validation (Temurin 25 + kotlinc 2.4.10): the pure file passes a
  main-harness, which caught the `File.toRelativeString` direction bug
  (`root.toRelativeString(resolved)` = `"../.."`; the correct call is
  `resolved.toRelativeString(root)`); JUnit source type-checks against a shim.
- **Device gate:** `TROUBLESHOOTING.md` §16 step 5.
