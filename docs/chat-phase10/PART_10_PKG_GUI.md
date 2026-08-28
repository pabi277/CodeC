# Phase 10.1 — GUI Package Catalog (Package & Command Hub)
**Status:** ✅ IMPLEMENTED on `arena/01a0482c-codec` (2026-08-28) · `[client-only]` · depends on Phase 3/5/6.
**Context:** ModulesScreen originally existed for optional Clang module (superseded by pkg + repo). Now upgraded into a full Package & Command Hub.
**D1 Implementation:**
- Replaced Modules with pkg-backed catalog (`ModuleCatalog.kt` + `ModulesScreen.kt`).
- 1-tap direct package installation (`pkg install -y <pkg>`) and execution into live terminal (`\r` line discipline).
- Quick action shortcuts: `pkg update`, `pkg upgrade -y`, `codec-setup-storage`, `pkg status`, `pkg heal`, `pkg repair`.
- Curated 25+ package catalog (Compilers, Editors, Languages, CLI tools, Compression).
- Live `$PREFIX/bin` installation status detection (`INSTALLED ✓` / `AVAILABLE`).
- Custom interactive command runner card.
- 1-tap copy command with toast feedback.
**Sources:** `ModulesScreen.kt`, `ModuleCatalog.kt`, `TerminalSession.kt`, `TerminalViewModel.kt`, `MainActivity.kt`.
**Exit condition:** ✅ MET — CI run `33177852501` passed green. Tap INSTALL or RUN on any package or quick action dispatches command and opens Terminal.
**Evidence:** §5.1 host (`PackageCatalogTest` passing), §5.2 CI (run `33177852501`), §5.3 device.
**Not in 10:** new package builds (Phase 12 repo-build for Python; Phase 10 is UI over existing 25+ package repo).

