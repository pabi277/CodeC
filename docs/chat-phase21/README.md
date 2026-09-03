# CodeC Phase 21 — Compiler Engine Redesign: Drop TCC, LanguageRunProfile Registry

**Status:** ✅ **COMPLETE (2026-09-03, `arena/01a064e0-codec`)** — D.1 + D.2 implemented, **D.3 device PASSED**, **D.4 CANCELLED by owner** (TCC stays as the default C compiler; the Settings engine picker was removed instead — D22/D23). Implementation record: [`PART_21_IMPLEMENTATION.md`](PART_21_IMPLEMENTATION.md).
· **Cost:** `[client-only]` — app Kotlin code only; no new `[repo-build]`
· **Depends on:** Phase 20.1 (gcc/clang in the CodeC repo so `pkg install gcc` works on-device)
· **Blocks:** Phase 24 (E.2 formatter uses `formatterTemplate` from the registry)

> **Owner decision (2026-09-01):** "remove tcc and use gcc like python and extend it's
> scope with other languages as per need - make the plan future proof."
>
> Full research & design rationale: [`docs/RESEARCH_NEXT_PHASES.md`](../RESEARCH_NEXT_PHASES.md)
> §Phase 21. The `LanguageRunProfile` Kotlin design (with registry code) is in that doc §D.2.

---

## Why this exists

TCC (Tiny C Compiler) is embedded in the APK as a static musl toolchain. It is
fast to set up but has real limits: ANSI C / partial C99 only, no C11, no C++,
no multi-file projects, no system headers, and it produces fully static musl
binaries that cannot link against Android/Bionic shared libs. The owner's
decision is to replace it with the same model Python already uses: a `gcc`
package in the CodeC repo, installed on demand, running under `$PREFIX` like
every other userland tool.

Alongside the compiler swap, the run model is refactored into a **generic
`LanguageRunProfile` registry** so adding a new language later is one entry in
a list — not new Kotlin code, not a new branch in `runActiveFile`.

---

## The four parts

| Part | Title | What it delivers | Doc |
|---|---|---|---|
| **D.1** ✅ | `LanguageRunProfile` registry + wire into `EditorViewModel` | The data model + registry; `runActiveFile` dispatches through it; TCC path stays as a feature-flag fallback | [PART_21_1_REGISTRY.md](PART_21_1_REGISTRY.md) |
| **D.2** ✅ | Auto-install gate | Before first RUN of a file whose `requiredPackage` is missing, show "Install X?" prompt and install; same flow as Python Phase 12 | [PART_21_2_AUTOINSTALL.md](PART_21_2_AUTOINSTALL.md) |
| **D.3** ✅ | Device acceptance: gcc compiles C/C++ end-to-end | Owner runs a C file and a C++ file through the new path on device; TCC fallback still in Settings | [PART_21_3_ACCEPTANCE.md](PART_21_3_ACCEPTANCE.md) |
| **D.4** ❌ | ~~Remove TCC entirely~~ **CANCELLED** | Delete `assets/tcc/`, `EmbeddedCompiler` TCC path, `scripts/build-tcc.sh`, `useLegacyTcc` flag; APK shrinks | [PART_21_4_REMOVE_TCC.md](PART_21_4_REMOVE_TCC.md) |

---

## ⚖️ Ground rules

- **Clean-room law:** the `LanguageRunProfile` design is CodeC's own (documented
  in `RESEARCH_NEXT_PHASES.md` §D.2). No code is copied from Coding C, Pydroid 3,
  or any other closed-source app.
- **No PR/merge and no push to `main` without the owner's explicit command.**
- **Client-only (D.1–D.4):** no `[repo-build]`, no bootstrap changes. The packages
  come from Phase 20; D only changes the Kotlin app code.
- All new logic must be **Android-free and host-unit-testable** (pattern:
  `LanguageRegistry` is a pure Kotlin object; `EditorViewModel` injects the
  shell path and environment from `ShellEnvironment`).
- **Phase 21.4 (TCC removal) is irreversible** — do not run it until D.3 device
  acceptance is confirmed by the owner.

## 🔎 Research prompts

Before implementing each part:
- Re-read `EditorViewModel.runActiveFile` (the current TCC-first switch) and
  `ExecutionRunner`/`InteractiveRunSession` to confirm the exact call sites
  that must be re-routed through the registry.
- Re-read `EmbeddedCompiler.kt` and `TermuxCompiler.kt` to understand what the
  TCC path does today (especially the `bundleDir`, archive-extraction, and
  link-order logic) before deleting it in D.4.
- Re-read `ShellEnvironment.kt` / `ShellBootstrap.prepare()` for the env-map
  and shell binary path that `ExecutionRunner` receives — the registry profiles
  must use the same env.
- For the auto-install gate (D.2), re-read Phase 12's Python auto-install flow
  in `EditorViewModel` / `ModuleInstaller` to reuse it, not reinvent it.

## Standing rules (unchanged)

- **No PR/merge and no push to `main` without the owner's explicit command.**
- CI (`Build APK`) = assemble + unit tests + lint — the only test executor.
- Never `build-package.sh -I`; never touch `cc` shim or `bash` shim; TCC link
  order (`-o` last) invariant is dropped when TCC is removed in D.4 (it no longer
  applies); all other invariants stay.
